#!/usr/bin/env python3
"""Compare main and bidi project bundles without treating trace changes as lost sinks.

Usage: python scripts/compare_bidi_projects.py --root /path/to/bundles
The input layout is <root>/<project>/{main,bidi}/reports/report-ifds.sarif.
Outputs retain every added/removed sink occurrence, raw locations, and run evidence.
Differences are observations, not an automatic claim that a real vulnerability is lost.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import posixpath
import re
from urllib.parse import unquote, urlsplit


PROJECTS = ("OWASP", "conductor", "thingsboard", "Stirling-PDF")
SIGNATURE = re.compile(r"^(.+?\)):(-?\d+):\((.*)\)$", re.DOTALL)
CLOCK = re.compile(r"(?<!\d)(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?")
RUN_END = re.compile(r"BIDI_RUN_END run=(\d+) status=(\w+) engineTime=(.*)")
RUN_START = re.compile(r"BIDI_RUN_START run=(\d+) direction=(\w+) limit=(\d+) timeout=(.*)")
BARRIER = re.compile(r"BIDI_BARRIER_END run=(\d+) barrierTime=(.*?) iterationTime=(.*)")
ANALYSIS_END = re.compile(r"BIDI_ANALYSIS_END run=(\d+) status=(\w+) reason=(\w+) elapsed=(.*)")


def read_json(path: Path) -> tuple[object | None, str, str | None]:
    if not path.exists():
        return None, "missing", None
    try:
        return json.loads(path.read_text()), "present", None
    except (OSError, ValueError) as error:
        return None, "invalid", str(error)


def duration_seconds(value: str) -> float | None:
    units = {"ns": 1e-9, "us": 1e-6, "µs": 1e-6, "ms": .001,
             "s": 1, "m": 60, "h": 3600, "d": 86400}
    parts = re.findall(r"([\d.]+)\s*(ns|us|µs|ms|s|m|h|d)", value)
    return sum(float(number) * units[unit] for number, unit in parts) if parts else None


def clock_seconds(line: str) -> float | None:
    match = CLOCK.search(line)
    if match is None:
        return None
    hour, minute, second, fraction = match.groups()
    return int(hour) * 3600 + int(minute) * 60 + int(second) + float("0." + (fraction or "0"))


def log_evidence(log: str, summary: dict) -> dict:
    iterations: dict[int, dict] = {}
    analysis_ends: list[dict] = []
    report_states: list[dict] = []
    trace_stats: list[dict] = []
    invalid_locations: list[str] = []
    prescan_errors: list[str] = []
    signals: list[dict] = []
    phases: dict[str, list[dict]] = defaultdict(list)
    starts: dict[str, tuple[float | None, str]] = {}
    inside_prescan = False
    first_run_seen = False
    phase_markers = {
        "prescan": ("Start prescan phase", "Finish prescan phase"),
        "ifds": ("Start IFDS analysis", "Finish IFDS analysis"),
        "full_scan": ("Start full scan phase", "Finish full scan phase"),
        "trace_generation": ("Start trace generation", "Finish trace generation"),
    }
    for line in log.splitlines():
        if "Invalid vulnerability location:" in line:
            invalid_locations.append(line)
        match = re.search(r"Bidi analysis: (\d+) CONFIRMED, (\d+) DEMAND vulnerabilities", line)
        if match:
            report_states.append({"confirmed": int(match[1]), "demand": int(match[2]), "line": line})
        match = re.search(r"TraceGenerationStats\(([^)]*)\)", line)
        if match:
            trace_stats.append({**{key: int(value) for key, value in re.findall(r"(\w+)=(\d+)", match[1])},
                                "line": line})
        if "Start prescan phase" in line:
            inside_prescan = True
        prescan_failure = "Prescan failed" in line or "Prescan setup failed" in line
        timeout = "Ifds analysis timeout" in line
        oom = "Running low on memory" in line or "OutOfMemoryError" in line
        if prescan_failure or (inside_prescan and (timeout or oom)):
            prescan_errors.append(line)
        if timeout or oom or "Detected high memory usage" in line:
            signals.append({"phase": "prescan" if inside_prescan else "analysis_or_output",
                            "kind": "TIMEOUT" if timeout else "OOM" if oom else "HIGH_MEMORY",
                            "line": line})
        if "Finish prescan phase" in line:
            inside_prescan = False
        for phase, (begin, end) in phase_markers.items():
            if begin in line:
                starts[phase] = clock_seconds(line), line
            if end in line and phase in starts:
                start_time, start_line = starts.pop(phase)
                end_time = clock_seconds(line)
                elapsed = None if start_time is None or end_time is None else (end_time - start_time) % 86400
                phases[phase].append({"seconds": elapsed, "start": start_line, "end": line})
        match = RUN_START.search(line)
        if match:
            index, direction, limit, timeout_value = match.groups()
            first_run_seen = True
            iterations.setdefault(int(index), {}).update(
                run=int(index), direction=direction, limit=int(limit), timeout=timeout_value,
                timeout_seconds=duration_seconds(timeout_value), start_line=line)
        match = RUN_END.search(line)
        if match:
            index, status, elapsed = match.groups()
            iterations.setdefault(int(index), {}).update(
                run=int(index), status=status, engine_time=elapsed,
                engine_seconds=duration_seconds(elapsed), end_line=line)
        match = BARRIER.search(line)
        if match:
            index, elapsed, total = match.groups()
            iterations.setdefault(int(index), {}).update(
                run=int(index), barrier_time=elapsed, barrier_seconds=duration_seconds(elapsed),
                iteration_time=total, iteration_seconds=duration_seconds(total), barrier_line=line)
        match = ANALYSIS_END.search(line)
        if match:
            index, status, reason, elapsed = match.groups()
            analysis_ends.append({"run": int(index), "status": status, "reason": reason,
                                  "elapsed": elapsed, "seconds": duration_seconds(elapsed), "line": line})
    status = summary.get("status", "IN_PROGRESS_OR_UNKNOWN")
    unfinished = [dict(phase=phase, start=line) for phase, (_, line) in starts.items()]
    return {"terminal_status": status, "analysis_ends": analysis_ends,
            "iterations": [iterations[k] for k in sorted(iterations)],
            "reported_states": report_states, "trace_generation_stats": trace_stats,
            "invalid_vulnerability_locations": invalid_locations,
            "phases": dict(phases), "unfinished_phases": unfinished, "signals": signals,
            "prescan_errors": prescan_errors,
            "masked_prescan_error": bool(prescan_errors) and status in ("OK", "COMPLETE"),
            "bidi_started_after_prescan_error": bool(prescan_errors) and first_run_seen,
            "runner_empty_marker_is_not_completion": "All runners are empty" in log}


def normalize_uri(uri: str | None, bases: dict, base_id: str | None) -> str | None:
    """Remove a known source root; retain package/module paths and class fallbacks."""
    if uri is None:
        return None
    parsed = urlsplit(uri)
    value = unquote(parsed.path) if parsed.scheme in ("", "file") else uri
    value = value.replace("\\", "/")
    source_base = (bases.get(base_id) or {}).get("uri") if base_id else None
    if source_base:
        prefix = unquote(urlsplit(source_base).path).replace("\\", "/").rstrip("/")
        if value.startswith(prefix + "/"):
            value = value[len(prefix) + 1:]
    return posixpath.normpath(value)


def method_parts(location: dict) -> tuple[str | None, int | None, str | None]:
    for logical in location.get("logicalLocations") or []:
        match = SIGNATURE.match(logical.get("decoratedName") or "")
        if match:
            signature, index, instruction = match.groups()
            return signature, int(index), instruction
    return None, None, None


def extract_findings(sarif: dict) -> tuple[list[dict], int, list[str]]:
    findings: list[dict] = []
    result_count = 0
    warnings: set[str] = set()
    for run_index, run in enumerate(sarif.get("runs") or []):
        bases = run.get("originalUriBaseIds") or {}
        rules = ((run.get("tool") or {}).get("driver") or {}).get("rules") or []
        for result_index, result in enumerate(run.get("results") or []):
            result_count += 1
            rule_id = result.get("ruleId") or (result.get("rule") or {}).get("id")
            rule_index = result.get("ruleIndex")
            if rule_id is None and isinstance(rule_index, int) and 0 <= rule_index < len(rules):
                rule_id = rules[rule_index].get("id")
            fingerprints = result.get("partialFingerprints") or {}
            fingerprint = fingerprints.get("vulnerabilitySinkHash/v1")
            for location_index, location in enumerate(result.get("locations") or [{}]):
                physical = location.get("physicalLocation") or {}
                artifact = physical.get("artifactLocation") or {}
                region = physical.get("region") or {}
                path = normalize_uri(artifact.get("uri"), bases, artifact.get("uriBaseId"))
                signature, instruction_index, instruction = method_parts(location)
                if rule_id and fingerprint and signature:
                    identity = ("sink_fingerprint_and_signature", rule_id, fingerprint, signature)
                elif rule_id and signature and instruction_index is not None:
                    identity = ("logical_instruction", rule_id, signature, instruction_index, instruction)
                    warnings.add("Some sinks have no sink fingerprint; logical instruction identity is used.")
                elif rule_id and path is not None and region.get("startLine") is not None:
                    identity = ("physical_location", rule_id, path, region.get("startLine"),
                                region.get("startColumn"))
                    warnings.add("Some sinks have only physical identity; source mapping differences need review.")
                else:
                    # A malformed location cannot safely collapse with another missing location.
                    identity = ("unresolved", rule_id, run_index, result_index, location_index)
                    warnings.add("Some sinks lack a usable identity; their comparisons are unresolved.")
                findings.append({
                    "identity": identity, "rule_id": rule_id, "sink_fingerprint": fingerprint,
                    "method_signature": signature, "instruction_index": instruction_index,
                    "instruction": instruction, "path": path,
                    "region": region, "raw_location": location, "partial_fingerprints": fingerprints,
                    "message": result.get("message"), "properties": result.get("properties"),
                    "code_flow_count": len(result.get("codeFlows") or []),
                    "run_index": run_index, "result_index": result_index,
                    "location_index": location_index,
                })
    return findings, result_count, sorted(warnings)


def load_bundle(directory: Path) -> dict:
    summary, summary_state, summary_error = read_json(directory / "summary.json")
    manifest, manifest_state, manifest_error = read_json(directory / "manifest.json")
    sarif_path = directory / "reports/report-ifds.sarif"
    sarif, sarif_state, sarif_error = read_json(sarif_path)
    issues = []
    if not isinstance(summary, dict):
        summary = {}
        issues.append("Summary is " + summary_state + "; terminal status is unknown.")
    if not isinstance(manifest, dict):
        manifest = {}
        issues.append("Manifest is " + manifest_state + "; fixed input equality is not established.")
    if sarif_state == "present" and not isinstance(sarif, dict):
        sarif_state, sarif_error = "invalid", "The SARIF root is not an object."
    elif sarif_state == "present" and not isinstance(sarif.get("runs"), list):
        sarif_state, sarif_error = "invalid", "SARIF has no runs array."
    elif sarif_state == "present" and not sarif["runs"]:
        sarif_state = "empty_runs"
    elif sarif_state == "present" and any(
            not isinstance(run, dict) or not isinstance(run.get("results", []), list)
            or any(not isinstance(result, dict) for result in run.get("results", []))
            for run in sarif["runs"]):
        sarif_state, sarif_error = "invalid", "A SARIF run or its results have an invalid shape."
    log_path = directory / "analyzer.log"
    log = log_path.read_text(errors="replace") if log_path.exists() else ""
    evidence = log_evidence(log, summary)
    findings, result_count, identity_warnings = extract_findings(sarif) if sarif_state == "present" else ([], 0, [])
    attempted = sum(row.get("total", 0) for row in evidence["trace_generation_stats"]) if evidence["trace_generation_stats"] else None
    export_gap = max(0, attempted - result_count) if attempted is not None and sarif_state == "present" else None
    if export_gap:
        issues.append(f"SARIF generation attempted {attempted} results but exported {result_count}; inspect location rejects and output filters.")
    if evidence["invalid_vulnerability_locations"]:
        issues.append("The SARIF generator rejected vulnerability locations; the raw errors are retained.")
    if sarif_state != "present":
        issues.append("SARIF is " + sarif_state + "; this is not a zero-finding result.")
    if evidence["masked_prescan_error"]:
        issues.append("A failed prescan is followed by terminal OK; an empty result can mask a false negative.")
    if evidence["terminal_status"] not in ("OK", "COMPLETE"):
        issues.append("The run is not complete: " + str(evidence["terminal_status"]) + ".")
    if evidence["terminal_status"] in ("OK", "COMPLETE") and any(
            row.get("status") not in (None, "COMPLETE") for row in evidence["iterations"]):
        issues.append("A non-complete bidi iteration conflicts with the terminal success status.")
    if evidence["terminal_status"] in ("OK", "COMPLETE") and any(
            row["status"] != "COMPLETE" for row in evidence["analysis_ends"]):
        issues.append("A non-complete bidi analysis end conflicts with the terminal success status.")
    issues += identity_warnings
    return {"directory": str(directory), "summary": summary, "manifest": manifest,
            "summary_state": summary_state, "summary_error": summary_error,
            "manifest_state": manifest_state, "manifest_error": manifest_error,
            "sarif_state": sarif_state, "sarif_error": sarif_error, "sarif": str(sarif_path),
            "analyzer_log": str(log_path), "status": evidence, "result_count": result_count,
            "sarif_generation_attempts": attempted, "unexported_result_count": export_gap,
            "finding_occurrences": len(findings), "unique_sinks": len({row["identity"] for row in findings}),
            "issues": issues, "findings": findings}


def groups(findings: list[dict]) -> dict[tuple, list[dict]]:
    result = defaultdict(list)
    for finding in findings:
        result[finding["identity"]].append(finding)
    return dict(result)


def sort_identity(identity: tuple) -> str:
    return json.dumps(identity, ensure_ascii=False)


def raw_location_counts(findings: list[dict]) -> list[dict]:
    counts = Counter(json.dumps(row["raw_location"], sort_keys=True) for row in findings)
    return [{"location": json.loads(location), "count": count} for location, count in sorted(counts.items())]


def grouped_artifacts(findings: list[dict]) -> list[dict]:
    by_rule_path: dict[tuple, list[dict]] = defaultdict(list)
    for finding in findings:
        by_rule_path[(finding["rule_id"], finding["path"])].append(finding)
    return [{"rule_id": rule, "path": path, "occurrences": len(rows),
             "unique_sinks": len({row["identity"] for row in rows}), "findings": rows}
            for (rule, path), rows in sorted(by_rule_path.items(), key=lambda item: sort_identity(item[0]))]


def input_differences(main: dict, bidi: dict) -> list[dict]:
    result = []
    for key in ("model_sha256", "test_revision"):
        a, b = main.get(key), bidi.get(key)
        if a is not None and b is not None and a != b:
            result.append({"field": key, "main": a, "bidi": b})
    for key in ("memory", "timeout", "outer_timeout"):
        a, b = (main.get("config") or {}).get(key), (bidi.get("config") or {}).get(key)
        if a is not None and b is not None and a != b:
            result.append({"field": "config." + key, "main": a, "bidi": b})
    return result


def compare_project(project: str, main_dir: Path, bidi_dir: Path) -> dict:
    main, bidi = load_bundle(main_dir), load_bundle(bidi_dir)
    a, b = groups(main["findings"]), groups(bidi["findings"])
    available = main["sarif_state"] == bidi["sarif_state"] == "present"
    both, removed_keys, added_keys = a.keys() & b.keys(), a.keys() - b.keys(), b.keys() - a.keys()
    if not available:
        both, removed_keys, added_keys = set(), set(), set()
    removed = [row for key in sorted(removed_keys, key=sort_identity) for row in a[key]]
    added = [row for key in sorted(added_keys, key=sort_identity) for row in b[key]]
    multiplicity_changes = []
    span_changes = []
    for key in sorted(both, key=sort_identity):
        if len(a[key]) != len(b[key]):
            multiplicity_changes.append({"identity": key, "main_count": len(a[key]), "bidi_count": len(b[key]),
                                         "note": "The sink remains present; this is not a lost sink."})
        al, bl = raw_location_counts(a[key]), raw_location_counts(b[key])
        if {json.dumps(row["location"], sort_keys=True) for row in al} != {
                json.dumps(row["location"], sort_keys=True) for row in bl}:
            span_changes.append({"identity": key, "main_locations": al, "bidi_locations": bl,
                                 "note": "Equal sink identity with different raw locations or spans."})
    # Keep changed-index matches as review evidence; do not silently weaken the primary identity.
    logical_candidates = []
    return_candidates = []
    for old in removed:
        if not old["method_signature"] or not old["instruction"]:
            continue
        candidates = [new for new in added if new["rule_id"] == old["rule_id"]
                      and new["method_signature"] == old["method_signature"]
                      and new["instruction"] == old["instruction"]]
        if candidates:
            logical_candidates.append({"main": old, "bidi_candidates": candidates,
                                       "note": "Same logical instruction with changed primary identity; inspect before classifying a miss."})
        if old["instruction"].startswith("return "):
            returns = [new for new in added if new["rule_id"] == old["rule_id"]
                       and new["method_signature"] == old["method_signature"]
                       and (new["instruction"] or "").startswith("return ")]
            if returns:
                return_candidates.append({"main": old, "bidi_candidates": returns,
                                          "note": "Same rule and full method at different return sites. A Simple method-exit location can select the last return. This is review evidence, not an automatic identity or flow match."})
    inputs = input_differences(main["manifest"], bidi["manifest"])
    complete = available and not inputs and all(
        bundle["manifest_state"] == "present"
        and
        bundle["status"]["terminal_status"] in ("OK", "COMPLETE")
        and not bundle["status"]["prescan_errors"]
        and not any(row.get("status") not in (None, "COMPLETE") for row in bundle["status"]["iterations"])
        and not any(row["status"] != "COMPLETE" for row in bundle["status"]["analysis_ends"])
        for bundle in (main, bidi))
    unresolved = any(row["identity"][0] == "unresolved" for row in main["findings"] + bidi["findings"])
    if not available:
        assessment = "OUTPUT_UNAVAILABLE"
    elif not complete:
        assessment = "INCOMPLETE_OR_INVALID_INPUT_COMPARISON"
    elif unresolved:
        assessment = "UNRESOLVED_SINK_IDENTITIES"
    elif removed_keys:
        assessment = "REMOVED_SINKS_NEED_ROOT_CAUSE"
    else:
        assessment = "ALL_MAIN_SINK_IDENTITIES_PRESENT"
    a_count, b_count = Counter(row["identity"] for row in main["findings"]), Counter(row["identity"] for row in bidi["findings"])
    occurrence_removed = [row for key, count in (a_count - b_count).items() for row in a[key][-count:]] if available else []
    occurrence_added = [row for key, count in (b_count - a_count).items() for row in b[key][-count:]] if available else []
    for bundle in (main, bidi):
        del bundle["findings"]
    return {
        "project": project, "main": main, "bidi": bidi, "input_differences": inputs,
        "outputs_available": available, "both_complete_without_prescan_error": complete,
        "assessment": assessment,
        "counts": {"main_unique": len(a) if main["sarif_state"] == "present" else None,
                   "bidi_unique": len(b) if bidi["sarif_state"] == "present" else None,
                   "unchanged_unique": len(both) if available else None,
                   "removed_unique": len(removed_keys) if available else None,
                   "added_unique": len(added_keys) if available else None,
                   "main_occurrences": sum(a_count.values()) if main["sarif_state"] == "present" else None,
                   "bidi_occurrences": sum(b_count.values()) if bidi["sarif_state"] == "present" else None,
                   "unchanged_occurrences": sum((a_count & b_count).values()) if available else None,
                   "removed_occurrences": sum((a_count - b_count).values()) if available else None,
                   "added_occurrences": sum((b_count - a_count).values()) if available else None},
        "removed": grouped_artifacts(removed), "added": grouped_artifacts(added),
        "removed_occurrence_details": grouped_artifacts(occurrence_removed),
        "added_occurrence_details": grouped_artifacts(occurrence_added),
        "multiplicity_changes": multiplicity_changes, "equal_identity_location_changes": span_changes,
        "possible_same_logical_sink": logical_candidates,
        "method_exit_location_candidates": return_candidates,
        "interpretation": "Trace counts are excluded from sink identity. Removed sinks require source and engine evidence; added sinks are not automatically false positives.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--projects", nargs="+", default=list(PROJECTS))
    args = parser.parse_args()
    output = args.output_dir or args.root / "comparison"
    output.mkdir(parents=True, exist_ok=True)
    rows = []
    for project in args.projects:
        result = compare_project(project, args.root / project / "main", args.root / project / "bidi")
        (output / (project + ".json")).write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
        for field in ("added", "removed"):
            (output / (project + "-" + field + ".json")).write_text(
                json.dumps(result[field], indent=2, ensure_ascii=False) + "\n")
        for field in ("added_occurrence_details", "removed_occurrence_details"):
            (output / (project + "-" + field + ".json")).write_text(
                json.dumps(result[field], indent=2, ensure_ascii=False) + "\n")
        rows.append({"project": project, "assessment": result["assessment"], "counts": result["counts"],
                     "main_status": result["main"]["status"]["terminal_status"],
                     "bidi_status": result["bidi"]["status"]["terminal_status"],
                     "main_sarif": result["main"]["sarif_state"], "bidi_sarif": result["bidi"]["sarif_state"],
                     "main_masked_prescan_error": result["main"]["status"]["masked_prescan_error"],
                     "bidi_masked_prescan_error": result["bidi"]["status"]["masked_prescan_error"]})
        print(project + ": " + result["assessment"] + " " + json.dumps(result["counts"]))
    (output / "summary.json").write_text(json.dumps(rows, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
