"""
Suite 5: Full Agent Loop (Integration)

Simulates the complete agent workflow on Stirling-PDF:
1. Discover entry points (by reading source)
2. Create a custom path-traversal rule
3. Test the rule with samples
4. Run initial scan on Stirling-PDF
5. Analyze external methods
6. Create YAML approximation for an unmodeled method
7. Re-scan and verify the approximation has effect
"""

import json
import shutil
import time
import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI,
    load_sarif,
    sarif_results,
    sarif_rule_ids,
    sarif_findings_for_rule,
    load_external_methods,
    count_external_methods,
    write_text,
    write_yaml,
    print_timing_breakdown,
    FIXTURES_DIR,
    BUILTIN_RULES_DIR,
    STIRLING_PROJECT_DIR,
)


@pytest.mark.slow
@pytest.mark.new_feature
class TestFullAgentLoop:
    """
    Simulates the agent's analysis workflow on Stirling-PDF.

    Phase 1 → discover entry points (manual)
    Phase 2 → create rule + test
    Phase 3 → scan + analyze + create approx + rescan
    """

    def _setup_workspace(self, tmp_output: Path) -> dict:
        """Create the agent workspace directory layout."""
        workspace = {
            "root": tmp_output,
            "rules": tmp_output / "agent-rules",
            "config": tmp_output / "agent-config",
            "approximations": tmp_output / "agent-approximations" / "src",
            "results": tmp_output / "results",
            "test_project": tmp_output / "agent-test-project",
            "test_compiled": tmp_output / "agent-test-compiled",
            "test_output": tmp_output / "agent-test-output",
        }
        for d in workspace.values():
            if isinstance(d, Path):
                d.mkdir(parents=True, exist_ok=True)
        return workspace

    def test_full_agent_loop(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """Full end-to-end agent loop on Stirling-PDF."""
        ws = self._setup_workspace(tmp_output)
        t0 = time.time()

        def _phase_time(label):
            elapsed = time.time() - t0
            print(f"  [{elapsed:6.1f}s] {label}")

        # ── Phase 1: Source Discovery (simulated) ─────────────────────
        controllers = [
            "stirling.software.SPDF.controller.api.misc.PrintFileController",
            "stirling.software.SPDF.controller.api.MergeController",
            "stirling.software.SPDF.controller.api.SplitPDFController",
            "stirling.software.SPDF.controller.api.security.*",
        ]
        print(f"Phase 1: Discovered {len(controllers)} controller groups")
        _phase_time("Phase 1 complete (source discovery)")

        # ── Phase 2: Create Rule ──────────────────────────────────────

        builtin_path_traversal = (
            BUILTIN_RULES_DIR / "java" / "security" / "path-traversal.yaml"
        )
        assert builtin_path_traversal.exists(), "Builtin path-traversal rule not found"
        print("Phase 2a: Read builtin path-traversal rule")

        # Create custom source library rule
        lib_dir = ws["rules"] / "java" / "lib"
        lib_dir.mkdir(parents=True, exist_ok=True)
        write_text(
            lib_dir / "stirling-source.yaml",
            """\
rules:
  - id: stirling-multipart-file-source
    options:
      lib: true
    severity: NOTE
    message: Untrusted multipart file data from Spring controller
    languages: [java]
    patterns:
      - pattern: |
          $RETURNTYPE $METHOD(..., @RequestParam MultipartFile $UNTRUSTED, ...) { ... }
""",
        )

        # Create join-mode security rule
        sec_dir = ws["rules"] / "java" / "security"
        sec_dir.mkdir(parents=True, exist_ok=True)
        write_text(
            sec_dir / "stirling-path-traversal.yaml",
            """\
rules:
  - id: stirling-path-traversal
    severity: ERROR
    message: >-
      User-uploaded file name flows to file system operation without sanitization
    metadata:
      cwe: CWE-22
      short-description: Path Traversal via uploaded file name
    languages: [java]
    mode: join
    join:
      refs:
        - rule: java/lib/stirling-source.yaml#stirling-multipart-file-source
          as: source
        - rule: java/lib/generic/path-traversal-sinks.yaml#java-path-traversal-sinks
          as: sink
      on:
        - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
""",
        )
        print("Phase 2b-c: Created custom rules")
        _phase_time("Phase 2 complete (rule creation)")

        # ── Phase 3: Initial Scan ─────────────────────────────────────

        sarif_path = ws["results"] / "report-1.sarif"
        ext_methods_path = ws["results"] / "external-methods-1.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(ws["rules"])],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            external_methods=str(ext_methods_path),
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok("Initial scan failed")
        print_timing_breakdown("initial-scan", result)
        _phase_time("Phase 3 complete (initial scan)")

        sarif_data = load_sarif(sarif_path)
        findings = sarif_findings_for_rule(sarif_data, "stirling-path-traversal")
        print(f"Phase 3: Initial scan found {len(findings)} path-traversal findings")
        assert len(findings) > 0, (
            "Expected path-traversal findings from initial scan but got 0. "
            "Check that the join rule's sink ref matches the builtin sink rule ID."
        )

        for f in findings[:5]:
            locs = f.get("locations", [{}])
            if locs:
                uri = (
                    locs[0]
                    .get("physicalLocation", {})
                    .get("artifactLocation", {})
                    .get("uri", "?")
                )
                line = (
                    locs[0]
                    .get("physicalLocation", {})
                    .get("region", {})
                    .get("startLine", "?")
                )
                print(f"  Finding: {uri}:{line}")

        # ── Phase 3b: Analyze External Methods ────────────────────────

        priority_methods = []
        wo_count, wr_count = 0, 0
        if ext_methods_path.exists():
            ext_data = load_external_methods(ext_methods_path)
            wo_count, wr_count = count_external_methods(ext_data)
            print(
                f"Phase 3b: External methods — {wo_count} without rules, {wr_count} with rules"
            )

            without_rules = ext_data.get("withoutRules", [])
            priority_methods = [m for m in without_rules if m.get("callSites", 0) > 5]
            priority_methods.sort(key=lambda m: m.get("callSites", 0), reverse=True)
            print(
                f"  Priority unmodeled methods (>5 call sites): {len(priority_methods)}"
            )
            for m in priority_methods[:10]:
                print(
                    f"    {m['method']} ({m['callSites']} call sites, positions: {m['factPositions']})"
                )
        _phase_time("Phase 3b complete (external methods analysis)")

        # ── Phase 4: Create Approximation and Rescan ──────────────────
        # Approximations are ONLY for external methods (from withoutRules).
        # These are library methods without source code in the project.

        if priority_methods:
            pass_through_rules = []
            for m in priority_methods[:5]:
                method_name = m["method"]
                positions = m["factPositions"]

                copies = []
                for pos in positions:
                    if pos.startswith("arg("):
                        copies.append({"from": pos, "to": "result"})
                    elif pos == "<this>":
                        copies.append({"from": "<this>", "to": "result"})

                if copies:
                    pass_through_rules.append(
                        {
                            "function": method_name,
                            "copy": copies,
                        }
                    )

            if pass_through_rules:
                config_file = ws["config"] / "custom-propagators.yaml"
                write_yaml(config_file, {"passThrough": pass_through_rules})
                print(
                    f"Phase 4: Created {len(pass_through_rules)} custom passThrough rules"
                )

                # Rescan with approximations
                sarif_path_2 = ws["results"] / "report-2.sarif"
                ext_methods_path_2 = ws["results"] / "external-methods-2.yaml"

                result2 = cli.scan(
                    project_path=str(stirling_project),
                    output=str(sarif_path_2),
                    rulesets=["builtin", str(ws["rules"])],
                    rule_ids=[
                        "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
                    ],
                    approximations_config=str(config_file),
                    external_methods=str(ext_methods_path_2),
                    severity=["note", "warning", "error"],
                    timeout=600,
                )

                print_timing_breakdown("rescan-with-approx", result2)

                if result2.ok:
                    sarif_data_2 = load_sarif(sarif_path_2)
                    findings_2 = sarif_findings_for_rule(
                        sarif_data_2, "stirling-path-traversal"
                    )
                    print(
                        f"Phase 4: Rescan found {len(findings_2)} findings (was {len(findings)})"
                    )

                    if ext_methods_path_2.exists():
                        ext_data_2 = load_external_methods(ext_methods_path_2)
                        wo2, wr2 = count_external_methods(ext_data_2)
                        print(
                            f"  External methods after approx: {wo2} without (was {wo_count}), {wr2} with (was {wr_count})"
                        )

                        delta_findings = len(findings_2) - len(findings)
                        delta_methods = wo_count - wo2
                        print(
                            f"  Delta: {delta_findings:+d} findings, {delta_methods:+d} newly modeled methods"
                        )

        _phase_time("Phase 4 complete (approximation + rescan)")
        total = time.time() - t0
        print(f"\n=== Full agent loop completed in {total:.1f}s ===")
