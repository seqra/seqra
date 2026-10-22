"""Shared helpers for the appsec-agent orchestrator scripts.

Not a runnable script — imported by the PEP723 entry points (get_status.py, generate.py),
which carry the pyyaml dependency. Every path resolves under the fixed
<project-root>/.opentaint/ tree relative to the current directory, so run the entry
scripts from the project root.
"""
import glob
import re
import subprocess
from pathlib import Path

import yaml

ROOT = Path(".opentaint")
MODEL = ROOT / "project"
TRACKING = ROOT / "tracking"
APPROX = TRACKING / "approximations"
RULES_TR = TRACKING / "rules"
SOURCES_TR = RULES_TR / "sources"
SINKS_TR = RULES_TR / "sinks"
TAGS = RULES_TR / "tags.yaml"
FINDINGS_TR = TRACKING / "findings"
RESULTS = ROOT / "results"
DROPPED = RESULTS / "dropped-external-methods.yaml"
SARIF = RESULTS / "report.sarif"
RULES = ROOT / "rules"
PASS_THROUGH = ROOT / "pass-through"
DATAFLOW = ROOT / "dataflow"


# ---- yaml io ----

def load_yaml(path, default=None):
    p = Path(path)
    if not p.is_file():
        return default
    try:
        return yaml.safe_load(p.read_text(encoding="utf-8")) or default
    except yaml.YAMLError as e:
        raise SystemExit(f"{p}: invalid YAML — {e}\n"
                         "  a JVM signature containing '[' must be quoted in flow style "
                         "(signature: \"([BLjava/lang/String;)V\")")


def dump_yaml(obj):
    return yaml.safe_dump(obj, sort_keys=False, default_flow_style=False, allow_unicode=True)


# ---- ruleset readers ----

def builtin_rules_root():
    """Resolve the installed built-in rules root reported by the CLI."""
    try:
        proc = subprocess.run(["opentaint", "--color", "never", "health", "--rules"],
                              capture_output=True, text=True, check=True)
    except OSError as e:
        raise SystemExit(f"cannot run `opentaint health --rules`: {e}")
    except subprocess.CalledProcessError as e:
        detail = (e.stderr or e.stdout or "").strip()
        raise SystemExit(f"`opentaint health --rules` failed: {detail}")

    # Current releases print the path alone. Walking the lines backwards also tolerates a
    # warning before it without guessing where OpenTaint was installed.
    for line in reversed(proc.stdout.splitlines()):
        raw = re.sub(r"\x1b\[[0-9;]*m", "", line).strip().strip('"').strip("'")
        candidate = Path(raw)
        if candidate.is_dir():
            return candidate
    raise SystemExit("`opentaint health --rules` did not report an existing rules directory")


def rule_tags(rule):
    raw = rule.get("tags") or []
    if isinstance(raw, str):
        raw = [raw]
    return {str(tag).strip() for tag in raw if str(tag).strip()}


def iter_rules(root, language):
    """Yield (ref, rule) from one ruleset root for the selected language."""
    base = Path(root) / language
    if not base.is_dir():
        return
    for path in sorted(base.rglob("*.yaml")):
        doc = load_yaml(path, {}) or {}
        for rule in doc.get("rules") or []:
            if not isinstance(rule, dict) or not rule.get("id"):
                continue
            rel = path.relative_to(root).as_posix()
            yield f"{rel}#{str(rule['id']).strip()}", rule


def lib_rules(root, language):
    for ref, rule in iter_rules(root, language):
        options = rule.get("options") or {}
        if options.get("lib") is True:
            yield ref, rule


def active_lib_rules(root, language):
    for ref, rule in lib_rules(root, language):
        if "disabled" not in (rule.get("options") or {}):
            yield ref, rule


def collect_lib_tags(roots, language):
    sources, sinks = set(), set()
    for root in roots:
        for _ref, rule in lib_rules(root, language):
            for tag in rule_tags(rule):
                if tag.endswith("-source"):
                    sources.add(tag)
                elif tag.endswith("-sink"):
                    sinks.add(tag)
    return {"sources": sorted(sources), "sinks": sorted(sinks)}


# ---- fqn / member normalization ----

def strip_quotes(s):
    return str(s).strip().strip('"').strip("'")


def fqn_base(s):
    """The method fqn without its signature/params — `a.b.C#m`."""
    s = strip_quotes(s)
    # A Go method starts with its receiver, for example
    # `(*example.com/lib.Type).Method`. The first parenthesis is part of
    # the name, not an appended signature.
    if s.startswith("("):
        return s
    i = s.find("(")
    return (s[:i] if i != -1 else s).strip()


def member_of(item):
    """A bucket/plan entry (dict or str) normalized to {method, signature?}."""
    if isinstance(item, dict):
        m = strip_quotes(item.get("method", ""))
        sig = str(item.get("signature", "")).strip()
        return {"method": m, "signature": sig} if sig else {"method": m}
    return {"method": strip_quotes(item)}


def member_key(item):
    """Overload-precise key: method + signature. Matches the classification ledger."""
    if isinstance(item, dict):
        return f"{strip_quotes(item.get('method', ''))}{str(item.get('signature', '')).strip()}"
    return strip_quotes(item)


def class_of(fqn):
    base = fqn_base(fqn)
    if "#" in base:
        return base.split("#", 1)[0].strip()
    if base.startswith("("):
        end = base.find(").")
        if end != -1:
            return base[1:end].lstrip("*").strip()
    # A Go package function has the form `import/path.Function`.
    if "/" in base and "." in base:
        return base.rsplit(".", 1)[0]
    return base.strip()


def package_of(fqn):
    cls = class_of(fqn)
    if fqn_base(fqn).startswith("("):
        return cls.rsplit(".", 1)[0] if "." in cls else cls
    if "/" in cls:
        return cls
    return cls.rsplit(".", 1)[0] if "." in cls else ""


# ---- git ----

def git_head():
    """HEAD commit of the project tree, or None when there's no repo."""
    try:
        out = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True,
                             text=True, check=True)
        return out.stdout.strip() or None
    except (OSError, subprocess.CalledProcessError):
        return None


# ---- approximation batch readers (shared by coverage + partition) ----

# a method counts classified once it sits in any batch classification bucket or in build.done
CLASSIFIED_BUCKETS = ("passthrough", "dataflow", "skipped", "engine_issues")
MODELED_BUCKETS = ("passthrough", "dataflow")


def batch_files():
    """Every approximation batch file (skipped.yaml is the merged view, not a batch)."""
    return [Path(p) for p in sorted(glob.glob(str(APPROX / "*.yaml")))
            if Path(p).name != "skipped.yaml"]


def dropped_entries():
    """The dropped external methods as {method, signature?} rows (order preserved)."""
    rows = []
    for e in load_yaml(DROPPED, []) or []:
        if isinstance(e, dict) and e.get("method"):
            row = {"method": strip_quotes(e["method"])}
            if e.get("signature"):
                row["signature"] = str(e["signature"]).strip()
            rows.append(row)
    return rows


def classified_keys():
    """method+signature keys of every method already classified across the batch files.
    Overload-precise (matches the ledger, build.done, unbuilt and stuck checks): a method with
    one overload classified does not mask a differently-propagating overload still dropped."""
    out = set()
    for p in batch_files():
        doc = load_yaml(p, {}) or {}
        for key in CLASSIFIED_BUCKETS:
            for item in doc.get(key, []) or []:
                if str(item).strip():
                    out.add(member_key(item))
        for item in (doc.get("build") or {}).get("done", []) or []:
            if str(item).strip():
                out.add(member_key(item))
    return out


def skipped_keys():
    """method+signature keys classified terminal — the `skipped` and `engine_issues` buckets of
    every batch. Terminal means the method will never build a working carrier, so it must not hold
    the approximations phase pending even if it is still modeled and still dropped by the scan.
    (skipped.yaml is the merged view of these same buckets, so reading the batches alone suffices.)"""
    out = set()
    for p in batch_files():
        doc = load_yaml(p, {}) or {}
        for bucket in ("skipped", "engine_issues"):
            for item in doc.get(bucket, []) or []:
                if str(item).strip():
                    out.add(member_key(item))
    return out


def modeled_entries():
    """Every passthrough/dataflow entry across batches, tagged with its batch file."""
    out = []
    for p in batch_files():
        doc = load_yaml(p, {}) or {}
        for kind in MODELED_BUCKETS:
            for item in doc.get(kind, []) or []:
                if str(item).strip():
                    out.append((p, kind, member_of(item)))
    return out


def build_done_keys():
    """Overload-precise keys of every built approximation across batches."""
    keys = set()
    for p in batch_files():
        doc = load_yaml(p, {}) or {}
        for item in (doc.get("build") or {}).get("done", []) or []:
            if str(item).strip():
                keys.add(member_key(item))
    return keys


def ledger_verdicted_keys():
    """method+signature keys already verdicted in classification.yaml (source ∪ safe)."""
    doc = load_yaml(RULES_TR / "classification.yaml", {}) or {}
    return {strip_quotes(x) for key in ("source", "safe") for x in (doc.get(key) or [])}
