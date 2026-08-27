# /// script
# requires-python = ">=3.9"
# dependencies = ["pyyaml==6.0.3"]
# ///
"""
generate.py — the orchestrator's writing helper. Every subcommand mutates durable
state at a fan-out join; none is read-only (use get_status.py for checks). Run with uv
from the project root: `uv run scripts/generate.py <cmd>`.

  init                bootstrap the .opentaint tree + state.yaml from the workflow flags
  tags                refresh tracking/rules/tags.yaml from the active lib rules
  partition analyze   dropped external methods -> per-root batch plans (approximations)
  partition discover  coverage.yaml's used members -> balanced discover plans
  mark-safe           discover plans' verdicts -> classification.yaml ledger (+prune plans)
  merge-skipped       batch skipped/engine_issues -> approximations/skipped.yaml (+prune plans)
  findings            results/report.sarif -> per-rule finding tracking files (idempotent)
"""
import argparse
import glob
import hashlib
import json
import math
import re
import subprocess
import sys
from pathlib import Path

import yaml

from _common import (APPROX, DATAFLOW, DROPPED,
                     FINDINGS_TR, MODEL, PASS_THROUGH, RESULTS, RULES, RULES_TR,
                     SARIF, SINKS_TR, SOURCES_TR, TAGS, TRACKING,
                     builtin_rules_root, class_of, classified_keys,
                     collect_lib_tags, dropped_entries, dump_yaml, fqn_base,
                     git_head, ledger_verdicted_keys, load_yaml, member_key,
                     package_of, strip_quotes)

ANALYZE_BUDGET = 20                       # methods per approximation batch
ANALYZE_MISC = 6                          # roots with <= this many methods pool into one misc batch
ROOT_DEPTH = 2                            # library root = first 2 dotted segments
DISCOVER_TARGET, DISCOVER_BAND = 50, 15   # project-used members per discover plan (~50, loose)

DISCOVER_PLANS = RULES_TR / "plans"
APPROX_PLANS = APPROX / "plans"


# ---- init: bootstrap the working tree + state.yaml ----

# the durable directories a run writes into; the leaves/scripts mkdir on write, but seeding
# them up front gives every stage a place to land and makes the empty tree self-describing.
INIT_DIRS = [TRACKING, APPROX, SOURCES_TR, SINKS_TR, FINDINGS_TR,
             RESULTS, RULES, PASS_THROUGH, DATAFLOW]
STATE_DERIVED = ("model_commit", "build_jdk", "max_memory")   # build/scan fill these, init preserves


def cmd_init(args):
    tags_existed = TAGS.is_file()
    for d in INIT_DIRS:
        d.mkdir(parents=True, exist_ok=True)
    state_path = TRACKING / "state.yaml"
    prior = load_yaml(state_path, {}) or {}
    resume = bool(prior)
    state = {"scan_level": args.scan_level, "triage_level": args.triage_level,
             "language": args.language or prior.get("language")}
    for k in STATE_DERIVED:                       # never clobber what build/scan already learned
        state[k] = prior.get(k)
    state_path.write_text(dump_yaml(state), encoding="utf-8")

    # history: append one run entry on a fresh init, never on resume (the derived knobs survived)
    hist_path = TRACKING / "history.yaml"
    runs = (load_yaml(hist_path, {}) or {}).get("runs") or []
    if not resume:
        runs.append({"commit": git_head(), "type": f"{args.scan_level}/{args.triage_level}"})
        hist_path.write_text(dump_yaml({"runs": runs}), encoding="utf-8")

    refresh_tags(state["language"], include_custom=tags_existed)

    mode = "resumed (derived knobs preserved)" if resume else "fresh"
    print(f"init {mode}: scan_level={state['scan_level']} triage_level={state['triage_level']} "
          f"language={state['language']}")
    print(f"seeded {len(INIT_DIRS)} directories under .opentaint/")
    print("next: uv run scripts/get_status.py --full")
    return 0


def refresh_tags(language, include_custom):
    if not language:
        raise SystemExit("cannot build tags.yaml before tracking/state.yaml has a language")
    roots = [builtin_rules_root()]
    if include_custom and RULES.is_dir():
        roots.append(RULES)
    tags = collect_lib_tags(roots, language)
    TAGS.parent.mkdir(parents=True, exist_ok=True)
    TAGS.write_text(dump_yaml(tags), encoding="utf-8")
    origin = "builtin + custom" if include_custom else "builtin"
    print(f"tags.yaml ({origin}): {len(tags['sources'])} source, {len(tags['sinks'])} sink")


def cmd_tags(args):
    state = load_yaml(TRACKING / "state.yaml", {}) or {}
    refresh_tags(state.get("language"), include_custom=TAGS.is_file())
    return 0


def regen_plans(out_dir):
    # partition regenerates the whole plan set from the current unclassified state, so drop any
    # stale plans first — otherwise a re-partition leaves already-consumed plans as leftover cruft.
    out_dir.mkdir(parents=True, exist_ok=True)
    for p in out_dir.glob("*.yaml"):
        p.unlink()


# ---- partition: shared bin-packing ----

def root_of(fqn, depth=ROOT_DEPTH):
    cls = class_of(fqn)
    separator = "/" if "/" in cls else "."
    segs = cls.split(separator)
    return separator.join(segs[:depth]) if len(segs) >= depth else cls


def in_packages(cls, prefixes):
    # Boundary match for JVM packages and Go import paths. A receiver type
    # follows a Go import path with a dot.
    return any(cls == p or cls.startswith(p + ".") or cls.startswith(p + "/")
               for p in prefixes)


def package_parts(package):
    separator = "/" if "/" in package else "."
    return separator, package.split(separator)


def atomize(fqns, cap):
    # split into atomic scopes (prefix, [fqns]); each scope is a whole (sub)package (or a subtree
    # under cap) — a package is NEVER split across scopes, so it lands in exactly one bin and no two
    # agents ever share a package's per-package unit. A package over cap stays oversized.
    scopes = []

    def recurse(prefix, items):
        if len(items) <= cap:
            scopes.append((prefix, items))
            return
        separator, prefix_parts = package_parts(prefix)
        depth = len(prefix_parts)
        buckets, leaf = {}, []
        for f in items:
            pkg = package_of(f)
            pkg_separator, segs = package_parts(pkg) if pkg else (separator, [])
            if pkg == prefix or len(segs) <= depth:
                leaf.append(f)
            else:
                child = pkg_separator.join(segs[: depth + 1])
                buckets.setdefault(child, []).append(f)
        if leaf:
            scopes.append((prefix, leaf))
        for child, cf in buckets.items():
            recurse(child, cf)

    top = {}
    for f in fqns:
        pkg = package_of(f)
        _, segs = package_parts(pkg) if pkg else (".", [])
        top.setdefault(segs[0] if segs else class_of(f), []).append(f)
    for seg0, items in top.items():
        recurse(seg0, items)
    return scopes


def pack(scopes, target, cap):
    # longest-processing-time bin-packing. An atomic scope larger than cap (a package that can't
    # be split) gets its own bin instead of forcing the whole set to one-scope-per-bin.
    plans = [{p: v} for p, v in scopes if len(v) > cap]
    items = sorted((s for s in scopes if len(s[1]) <= cap), key=lambda s: len(s[1]), reverse=True)
    if items:
        total = sum(len(v) for _, v in items)
        k = max(1, math.ceil(total / cap), round(total / target))
        while True:
            loads = [0] * k
            bins = [{} for _ in range(k)]
            for prefix, v in items:
                i = min(range(k), key=lambda j: loads[j])
                bins[i][prefix] = v
                loads[i] += len(v)
            if max(loads) <= cap or k >= len(items):
                break
            k += 1
        plans += [b for b in bins if b]
    return plans


def write_plans(plans, out_dir, prefix_id):
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = []
    for i, scopes in enumerate(plans, 1):
        pid = f"{prefix_id}-{i:03d}"
        norm = {p.replace(".", "-").replace("/", "-"): sorted(v, key=lambda x: (x["method"], x.get("signature", ""))
                                            if isinstance(x, dict) else x)
                for p, v in sorted(scopes.items())}
        path = out_dir / f"{pid}.yaml"
        # source: null is the unprocessed sentinel — a discover agent overwrites it with the
        # list of sources it found (an empty list when it finds none). mark-safe folds only
        # plans whose sentinel was replaced, so an un-returned plan is never marked safe.
        path.write_text(dump_yaml({"id": pid, "scopes": norm, "source": None}), encoding="utf-8")
        paths.append(str(path))
    return paths


# ---- partition analyze ----

def _root_next_index(prefix):
    # additive numbering: a re-partition round must never reuse an id an existing batch already
    # owns, or the new plan's analyze agent would overwrite that batch. Continue past the highest
    # index any existing batch OR leftover plan already claims for this root.
    mx = 0
    for d in (APPROX, APPROX_PLANS):
        for p in glob.glob(str(d / f"{prefix}-*.yaml")):
            m = re.match(rf"^{re.escape(prefix)}-(\d+)\.yaml$", Path(p).name)
            if m:
                mx = max(mx, int(m.group(1)))
    return mx + 1


def cmd_analyze(args):
    regen_plans(APPROX_PLANS)
    classified = classified_keys()
    rows = [r for r in dropped_entries() if member_key(r) not in classified]
    if not rows:
        print("nothing to plan — every dropped method already classified", file=sys.stderr)
        return 0

    by_root = {}
    for r in rows:
        by_root.setdefault(root_of(r["method"]), []).append(r)
    count = lambda rs: len({fqn_base(r["method"]) for r in rs})
    misc = []
    for root in [k for k, rs in by_root.items() if count(rs) <= ANALYZE_MISC]:
        misc += by_root.pop(root)
    if misc:
        by_root["misc"] = misc

    paths = []
    for root in sorted(by_root):
        by_fqn = {}
        for r in by_root[root]:
            by_fqn.setdefault(fqn_base(r["method"]), []).append(r)
        bins = pack(atomize(sorted(by_fqn), ANALYZE_BUDGET), ANALYZE_BUDGET, ANALYZE_BUDGET)
        prefix = root.replace(".", "-").replace("/", "-")
        start = _root_next_index(prefix)
        for i, b in enumerate(bins):
            scopes = {}
            for f in {f for v in b.values() for f in v}:           # re-group the batch by class
                scopes.setdefault(class_of(f), []).extend(by_fqn[f])
            norm = {cls: sorted(v, key=lambda x: (x["method"], x.get("signature", "")))
                    for cls, v in sorted(scopes.items())}
            pid = f"{prefix}-{start + i:03d}"
            path = APPROX_PLANS / f"{pid}.yaml"
            path.write_text(dump_yaml({"scopes": norm}), encoding="utf-8")
            paths.append(str(path))
    for p in paths:
        print(p)
    return 0


# ---- partition discover ----

def yaml_modules(model_yaml):
    # each module in project.yaml as (packages, moduleClasses); only `packages` says which of a
    # classpath-mode model's moduleClasses (dependency jars included) is project code
    doc = load_yaml(model_yaml, {}) or {}
    mods = []

    def walk(node):
        if isinstance(node, dict):
            if isinstance(node.get("moduleClasses"), list):
                mods.append(([str(p) for p in (node.get("packages") or [])],
                             [str(c) for c in node["moduleClasses"]]))
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for x in node:
                walk(x)

    walk(doc)
    return mods


def is_project_class(cls, packages):
    # mirrors the engine's ProjectClasses.isModuleClass; empty packages = a project-only module
    return not packages or any(cls.startswith(p) for p in packages)


CALL_RE = re.compile(r"//\s*(?:Interface)?Method\s+(\S+?)\.(<?\w+>?):(\S+)")


def extract_jvm_usages():
    # disassemble project classes, collect // Method / // InterfaceMethod call sites with their
    # JVM descriptor; returns (fqn, signature) pairs so an overloaded member stays disambiguated
    fqns = set()
    for packages, module_classes in yaml_modules(MODEL / "project.yaml"):
        for entry in module_classes:
            p = MODEL / entry
            if p.is_dir():
                classes = [str(c.relative_to(p))[:-6].replace("/", ".") for c in p.rglob("*.class")]
            elif p.is_file():
                try:
                    listing = subprocess.run(["jar", "tf", str(p)], capture_output=True,
                                             text=True, check=True).stdout
                except (OSError, subprocess.CalledProcessError):
                    continue
                if not packages:
                    print(f"warning: {entry} is a jar in a module with no declared `packages` — its "
                          f"plans will cover the library's own calls, not the project's", file=sys.stderr)
                classes = [c[:-6].replace("/", ".") for c in listing.splitlines()
                           if c.endswith(".class")]
            else:
                continue
            classes = [c for c in classes if is_project_class(c, packages)]
            cp = str(p)
            for i in range(0, len(classes), 200):       # batch to keep argv under the limit
                batch = classes[i:i + 200]
                try:
                    out = subprocess.run(["javap", "-c", "-p", "-classpath", cp, *batch],
                                         capture_output=True, text=True).stdout
                except OSError:
                    continue
                for owner, method, sig in CALL_RE.findall(out):
                    fqns.add((f"{owner.replace('/', '.')}#{method}", sig))
    return fqns


def go_project_dirs(model_yaml):
    doc = load_yaml(model_yaml, {}) or {}
    project_root = Path(doc.get("projectRoot") or ".").resolve()
    result = []
    for project in doc.get("goProjects") or []:
        if not isinstance(project, dict) or not project.get("projectDir"):
            continue
        path = Path(str(project["projectDir"]))
        result.append(path if path.is_absolute() else project_root / path)
    return result


def extract_go_usages():
    helper = Path(__file__).resolve().parent / "go-usage"
    fqns = set()
    for project_dir in go_project_dirs(MODEL / "project.yaml"):
        try:
            result = subprocess.run(
                ["go", "run", ".", "--dir", str(project_dir)],
                cwd=helper,
                capture_output=True,
                text=True,
                check=True,
            )
        except OSError as error:
            raise SystemExit(f"cannot run the Go usage extractor: {error}") from error
        except subprocess.CalledProcessError as error:
            detail = error.stderr.strip() or error.stdout.strip() or str(error)
            raise SystemExit(f"Go usage extraction failed: {detail}") from error
        for row in json.loads(result.stdout or "[]"):
            fqns.add((str(row["method"]), str(row["signature"])))
    return fqns


def extract_usages():
    state = load_yaml(TRACKING / "state.yaml", {}) or {}
    language = str(state.get("language") or "java").lower()
    if language == "go":
        return extract_go_usages()
    if language in ("java", "kotlin"):
        return extract_jvm_usages()
    raise SystemExit(f"usage extraction is not implemented for language: {language}")


def pending_packages():
    cov = load_yaml(TRACKING / "coverage.yaml", {}) or {}
    return tuple(p for p in (cov.get("packages") or []) if isinstance(p, str) and p)


def cmd_discover(args):
    regen_plans(DISCOVER_PLANS)
    packages = pending_packages()
    verdicted = ledger_verdicted_keys()               # method+signature keys
    sigs = {}                       # bare member fqn -> its pending signatures
    if packages:
        for f, sig in extract_usages():
            if in_packages(class_of(f), packages) and f + sig not in verdicted:
                sigs.setdefault(f, set()).add(sig)
    todo = sorted(sigs)
    if not todo:
        ledger = RULES_TR / "classification.yaml"
        if not ledger.is_file():
            ledger.parent.mkdir(parents=True, exist_ok=True)
            ledger.write_text(dump_yaml({"source": [], "safe": []}), encoding="utf-8")
        print("nothing to plan — every used dependency member is "
              "already verdicted", file=sys.stderr)
        return 0
    cap = DISCOVER_TARGET + DISCOVER_BAND
    plans = pack(atomize(todo, cap), DISCOVER_TARGET, cap)
    rows = {f: [{"method": f, "signature": s} for s in sorted(sigs[f])] for f in sigs}
    plans = [{pkg: [r for f in members for r in rows[f]] for pkg, members in plan.items()}
             for plan in plans]
    for p in write_plans(plans, DISCOVER_PLANS, "lib"):
        print(p)
    return 0


def cmd_partition(args):
    return cmd_analyze(args) if args.kind == "analyze" else cmd_discover(args)


# ---- mark-safe (discover join) ----

def _member_source_unit_keys():
    keys = set()
    for path in SOURCES_TR.glob("*.yaml") if SOURCES_TR.is_dir() else []:
        doc = load_yaml(path, {}) or {}
        keys.update(member_key(entry) for entry in (doc.get("sources") or [])
                    if isinstance(entry, dict))
    return keys


def cmd_mark_safe(args):
    plans = sorted(glob.glob(str(DISCOVER_PLANS / "lib-*.yaml")))
    if not plans:
        print("no discover plans to reconcile", file=sys.stderr)
        return 0
    ledger = RULES_TR / "classification.yaml"
    doc = load_yaml(ledger, {}) or {}
    source = {member_key(x) for x in (doc.get("source") or [])}
    safe = {member_key(x) for x in (doc.get("safe") or [])}
    unit_sources = _member_source_unit_keys()
    processed, unprocessed = [], []
    invalid = []
    for p in plans:
        pdoc = load_yaml(p, {}) or {}
        raw = pdoc.get("source")
        if raw is None:                       # sentinel intact — no discover agent returned for it
            unprocessed.append(p)
            continue
        members = {member_key(m) for v in (pdoc.get("scopes") or {}).values() for m in v}
        srcs = {member_key(x) for x in raw}
        outside = sorted(srcs - members)
        missing_units = sorted(srcs - unit_sources)
        if outside or missing_units:
            unprocessed.append(p)
            if outside:
                invalid.append(f"{Path(p).name}: source entries outside the assigned plan: "
                               + ", ".join(outside))
            if missing_units:
                invalid.append(f"{Path(p).name}: source entries missing from member source units: "
                               + ", ".join(missing_units))
            continue
        source |= srcs
        safe |= members - srcs
        processed.append(p)
        print(f"{Path(p).name}: {len(srcs)} sources, {len(members - srcs)} safe")
    if not processed:
        print("no complete discover plan can be reconciled — fan out or repair its source units",
              file=sys.stderr)
        for issue in invalid:
            print(issue, file=sys.stderr)
        return 1 if invalid else 0
    safe -= source
    ledger.parent.mkdir(parents=True, exist_ok=True)
    ledger.write_text(dump_yaml({"source": sorted(source), "safe": sorted(safe)}), encoding="utf-8")
    print(f"classification.yaml: {len(source)} source, {len(safe)} safe total")
    if not args.keep:
        for p in processed:
            Path(p).unlink()
        print(f"pruned {len(processed)} reconciled discover plan(s)")
    if unprocessed:
        print(f"left {len(unprocessed)} incomplete plan(s) for re-dispatch: "
              + ", ".join(Path(p).name for p in unprocessed))
    for issue in invalid:
        print(issue, file=sys.stderr)
    return 1 if invalid else 0


# ---- merge-skipped (analyze join) ----

def _skip_member(item):
    # normalize a skipped/engine_issues entry to {method, signature?}, dropping the reason
    if isinstance(item, dict):
        m = strip_quotes(item.get("method", ""))
        sig = str(item.get("signature", "")).strip()
        return {"method": m, "signature": sig} if sig else {"method": m}
    return {"method": strip_quotes(item)}


def _collect(docs, bucket):
    seen = {}
    for doc in docs:
        for item in doc.get(bucket, []) or []:
            m = _skip_member(item)
            if m["method"]:
                seen[(m["method"], m.get("signature", ""))] = m
    return [seen[k] for k in sorted(seen)]


def cmd_merge_skipped(args):
    # collects the `skipped` and `engine_issues` buckets of every batch into skipped.yaml, keeping
    # them as two separate groups — regular skips under `methods`, engine issues under `engine_issues`.
    docs = [load_yaml(p, {}) or {} for p in
            (Path(x) for x in sorted(glob.glob(str(APPROX / "*.yaml"))))
            if p.name != "skipped.yaml"]
    out = {"methods": _collect(docs, "skipped"), "engine_issues": _collect(docs, "engine_issues")}
    (APPROX / "skipped.yaml").write_text(dump_yaml(out), encoding="utf-8")
    print(f"skipped.yaml: {len(out['methods'])} methods, {len(out['engine_issues'])} engine_issues")
    if not args.keep and APPROX_PLANS.is_dir():
        pruned = [p for p in glob.glob(str(APPROX_PLANS / "*.yaml"))]
        for p in pruned:
            Path(p).unlink()
        if pruned:
            print(f"pruned {len(pruned)} consumed approximation plan(s)")
    return 0


# ---- findings (SARIF -> per-rule tracking files) ----

ADJ = ["brave", "calm", "eager", "fuzzy", "gentle", "jolly", "keen", "lucid",
       "merry", "noble", "proud", "quiet", "rapid", "sly", "tidy", "vivid",
       "witty", "zesty", "amber", "bold"]
NOUN = ["hopper", "eagle", "otter", "falcon", "maple", "comet", "harbor",
        "willow", "pixel", "river", "ember", "cobra", "lotus", "raven",
        "quartz", "badger", "cedar", "drake", "finch", "gull"]

_FP_PREFERENCE = ("vulnerabilitySourceSinkHash", "vulnerabilityWithTraceHash")

RULE_RE = re.compile(r'^rule_id:\s*(.+?)\s*$', re.M)
HASHES_RE = re.compile(r'^sarif_hashes:\s*\[(.*)\]\s*$', re.M)
HASHES_BLOCK_RE = re.compile(r'^sarif_hashes:\s*\n((?:[ \t]+-[^\n]*\n?)+)', re.M)
VERDICT_RE = re.compile(r'^verdict:\s*(.+?)\s*$', re.M)


def docker_name(seed, taken):
    h = int(hashlib.sha1(seed.encode()).hexdigest(), 16)
    base = f"{ADJ[h % len(ADJ)]}-{NOUN[(h // len(ADJ)) % len(NOUN)]}"
    name, n = base, 2
    while name in taken:
        name, n = f"{base}-{n}", n + 1
    return name


def result_hash(res):
    fp = res.get("fingerprints") or res.get("partialFingerprints")
    if isinstance(fp, dict) and fp:
        for pref in _FP_PREFERENCE:
            for k, v in fp.items():
                if k.startswith(pref):
                    return str(v)[:16]
        return str(sorted(fp.values())[0])[:16]
    parts = [res.get("ruleId", "")]
    locs = list(res.get("locations", []))
    for cf in res.get("codeFlows", []):
        for tf in cf.get("threadFlows", []):
            locs += [st.get("location", {}) for st in tf.get("locations", [])]
    for loc in locs:
        pl = loc.get("physicalLocation", {})
        parts.append(pl.get("artifactLocation", {}).get("uri", ""))
        parts.append(json.dumps(pl.get("region", {}), sort_keys=True))
    return hashlib.sha1("|".join(parts).encode()).hexdigest()[:16]


def scan_results(sarif):
    out = {}
    for run in sarif.get("runs") or []:
        for res in run.get("results") or []:
            rid = res.get("ruleId") or "unknown"
            msg = (res.get("message", {}) or {}).get("text", "").strip()
            out.setdefault(rid, {})[result_hash(res)] = msg
    return out


def fmt_list(hashes):
    return "[" + ", ".join(hashes) + "]"


def parse_hashes(text):
    m = HASHES_RE.search(text)
    if m:
        return [h.strip() for h in m.group(1).split(",") if h.strip()]
    m = HASHES_BLOCK_RE.search(text)
    if m:
        return [ln.strip().lstrip("-").strip()
                for ln in m.group(1).splitlines() if ln.strip().lstrip("-").strip()]
    return []


def replace_hashes(text, merged):
    line = "sarif_hashes: " + fmt_list(merged)
    if HASHES_RE.search(text):
        return HASHES_RE.sub(lambda m: line, text, count=1)
    if HASHES_BLOCK_RE.search(text):
        return HASHES_BLOCK_RE.sub(line + "\n", text, count=1)
    return line + "\n" + text


def new_file_text(rid, hashes, notes):
    body = "\n".join("  " + ln for ln in (notes or "(no analyzer message)").splitlines())
    return (f"sarif_hashes: {fmt_list(hashes)}\n"
            f"rule_id: {rid}\n"
            f"verdict: pending\n"
            f"notes: >\n{body}\n"
            f"poc: pending\n")


def cmd_findings(args):
    sarif = json.loads(SARIF.read_text(encoding="utf-8"))
    by_rule = scan_results(sarif)
    out = FINDINGS_TR
    out.mkdir(parents=True, exist_ok=True)

    existing = {}
    taken = set()
    for p in sorted(glob.glob(str(out / "*.yaml"))):
        text = Path(p).read_text(encoding="utf-8")
        rid = RULE_RE.search(text)
        verdict = VERDICT_RE.search(text)
        taken.add(Path(p).stem)
        if rid:
            existing.setdefault(rid.group(1).strip(), []).append(
                (Path(p), parse_hashes(text), verdict.group(1).strip() if verdict else "pending"))

    created = updated = unchanged = reconcile = 0
    for rid, hashmap in sorted(by_rule.items()):
        scanned = set(hashmap)
        files = existing.get(rid)
        if not files:
            name = docker_name(rid, taken)
            taken.add(name)
            notes = "\n".join(sorted({m for m in hashmap.values() if m}))
            (out / f"{name}.yaml").write_text(new_file_text(rid, sorted(scanned), notes),
                                              encoding="utf-8")
            created += 1
            continue
        already = set().union(*(set(h) for _, h, _ in files))
        new = sorted(scanned - already)
        if not new:
            unchanged += 1
            continue
        pending = next(((p, h) for p, h, v in files if v == "pending"), None)
        if pending:
            path, hashes = pending
            text = replace_hashes(path.read_text(encoding="utf-8"), sorted(set(hashes) | set(new)))
            text = re.sub(r'^verdict:\s*.+$', "verdict: pending", text, count=1, flags=re.M)
            path.write_text(text, encoding="utf-8")
            updated += 1
            continue
        name = docker_name(rid, taken)
        taken.add(name)
        msgs = sorted({hashmap[h] for h in new if hashmap.get(h)})
        notes = ("reconcile: new results under a rule whose findings are already triaged — "
                 "match each against this rule's triaged findings by flow before judging; if the "
                 "vulnerability is the same, merge its hashes into that finding and inherit its "
                 "verdict instead of re-triaging\n" + "\n".join(msgs))
        (out / f"{name}.yaml").write_text(new_file_text(rid, sorted(new), notes), encoding="utf-8")
        reconcile += 1

    print(f"findings: {created} created, {updated} updated, {unchanged} unchanged, "
          f"{reconcile} to reconcile ({len(by_rule)} rules in scan)")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    i = sub.add_parser("init", help="bootstrap the .opentaint tree + state.yaml from workflow flags")
    i.add_argument("--scan-level", required=True, choices=["lite", "normal", "deep"])
    i.add_argument("--triage-level", required=True, choices=["static", "dynamic"])
    i.add_argument("--language", default=None, help="target language, determined by the orchestrator")
    i.set_defaults(func=cmd_init)

    t = sub.add_parser("tags", help="refresh tags.yaml from the active lib rules")
    t.set_defaults(func=cmd_tags)

    p = sub.add_parser("partition", help="split classification work into per-agent plans")
    p.add_argument("kind", choices=["analyze", "discover"])
    p.set_defaults(func=cmd_partition)

    m = sub.add_parser("mark-safe", help="merge discover plans into classification.yaml")
    m.add_argument("--keep", action="store_true", help="keep the reconciled discover plans")
    m.set_defaults(func=cmd_mark_safe)

    s = sub.add_parser("merge-skipped", help="rebuild approximations/skipped.yaml from batches")
    s.add_argument("--keep", action="store_true", help="keep the consumed approximation plans")
    s.set_defaults(func=cmd_merge_skipped)

    f = sub.add_parser("findings", help="seed per-rule finding files from results/report.sarif")
    f.set_defaults(func=cmd_findings)

    args = ap.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
