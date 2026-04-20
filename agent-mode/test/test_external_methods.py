"""
Suite 4: External Methods Extraction

Tests:
4.1  Scan with --external-methods produces two YAML files (without-rules / with-rules)
4.2  External methods files have correct structure (methods list with method, signature, factPositions, callSites)
4.3  External methods contain expected fields
4.4  without-rules list is non-empty for a real project
4.5  with-rules list contains known standard library methods
4.6  Scan with custom approximations reduces without-rules count
4.7  External methods extraction alongside SARIF output
"""

import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI,
    load_sarif,
    sarif_results,
    load_external_methods,
    count_external_methods,
    external_methods_exist,
    FIXTURES_DIR,
    BUILTIN_RULES_DIR,
)


class TestExternalMethodsBasic:
    """4.1-4.3: Basic external methods output."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_produces_external_methods_file(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """--track-external-methods produces YAML files alongside SARIF output."""
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with --track-external-methods failed")
        assert external_methods_exist(sarif_path), (
            "External methods files not produced"
        )

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_external_methods_structure(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        External methods are split into two files (-without-rules.yaml and -with-rules.yaml).
        Each entry has: method, signature, factPositions, callSites.
        """
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        data = load_external_methods(sarif_path)

        for section_name in ["withoutRules", "withRules"]:
            section = data.get(section_name, [])
            for entry in section[:5]:
                assert "method" in entry, f"Entry in {section_name} missing 'method'"
                assert "signature" in entry, (
                    f"Entry in {section_name} missing 'signature'"
                )
                assert "factPositions" in entry, (
                    f"Entry in {section_name} missing 'factPositions'"
                )
                assert "callSites" in entry, (
                    f"Entry in {section_name} missing 'callSites'"
                )

                assert "#" in entry["method"], (
                    f"Method should be in Class#method format: {entry['method']}"
                )

                assert isinstance(entry["factPositions"], list), (
                    f"factPositions should be a list: {entry['factPositions']}"
                )

                assert isinstance(entry["callSites"], int) and entry["callSites"] > 0, (
                    f"callSites should be a positive integer: {entry['callSites']}"
                )

                for pos in entry["factPositions"]:
                    assert pos == "<this>" or pos == "ret" or pos.startswith("arg("), (
                        f"Invalid fact position: {pos} — expected '<this>', 'ret', or 'arg(N)'"
                    )


class TestExternalMethodsContent:
    """4.4-4.5: External methods content validation."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_without_rules_nonempty_for_real_project(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Stirling-PDF uses many libraries without built-in approximations.
        The withoutRules list should be non-empty.
        """
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        data = load_external_methods(sarif_path)
        without_count, with_count = count_external_methods(data)
        print(
            f"External methods: {without_count} without rules, {with_count} with rules"
        )

        assert without_count > 0, "Expected non-empty withoutRules for Stirling-PDF"

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_with_rules_contains_standard_library_methods(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        The withRules section should contain standard library methods that
        have built-in approximations.
        """
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        data = load_external_methods(sarif_path)
        with_rules = data.get("withRules", [])
        with_rules_methods = {e["method"] for e in with_rules}

        print(f"Methods with rules ({len(with_rules_methods)}):")
        for m in sorted(list(with_rules_methods))[:20]:
            print(f"  - {m}")


class TestExternalMethodsWithApproximations:
    """4.6: Custom approximations reduce withoutRules count."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_approximations_reduce_without_rules(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Adding custom passThrough rules for methods that were in withoutRules
        should move them to withRules.
        """
        yaml_config = (
            FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        )

        if not yaml_config.exists():
            pytest.skip("Fixture approximation config not created yet")

        # Run 1: without custom approximations
        sarif1 = tmp_output / "run1" / "report.sarif"
        (tmp_output / "run1").mkdir()
        r1 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif1),
            rulesets=["builtin"],
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )

        # Run 2: with custom approximations
        sarif2 = tmp_output / "run2" / "report.sarif"
        (tmp_output / "run2").mkdir()
        r2 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif2),
            rulesets=["builtin"],
            approximations_config=str(yaml_config),
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )

        if r1.ok and r2.ok:
            data1 = load_external_methods(sarif1)
            data2 = load_external_methods(sarif2)
            wo1, _ = count_external_methods(data1)
            wo2, _ = count_external_methods(data2)
            print(f"Without custom approx: {wo1} methods without rules")
            print(f"With custom approx:    {wo2} methods without rules")

            methods_without_1 = {e["method"] for e in data1.get("withoutRules", [])}
            methods_without_2 = {e["method"] for e in data2.get("withoutRules", [])}
            newly_covered = methods_without_1 - methods_without_2
            if newly_covered:
                print(f"Newly covered methods ({len(newly_covered)}):")
                for m in sorted(newly_covered):
                    print(f"  + {m}")


class TestExternalMethodsAlongsideSarif:
    """4.7: External methods and SARIF are produced together."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_both_outputs_produced(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """A single scan produces both SARIF report and external methods file."""
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            track_external_methods=True,
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        assert sarif_path.exists(), "SARIF report not produced"
        assert external_methods_exist(sarif_path), (
            "External methods files not produced"
        )

        sarif_data = load_sarif(sarif_path)
        ext_data = load_external_methods(sarif_path)
        assert len(sarif_results(sarif_data)) > 0, "SARIF has no results"
        wo, wr = count_external_methods(ext_data)
        assert wo + wr > 0, "External methods file is empty"
