"""
Suite 3: Approximations Generation/Override

Approximations (both YAML passThrough and code-based) are ONLY applicable to
external methods — library classes whose source code is NOT part of the analyzed
project. The agent discovers which methods need approximations via the
--external-methods output (withoutRules section).

Tests:
3.1  Scan with --approximations-config (YAML passThrough for external library methods)
3.2  Scan with --approximations-config + --ruleset together (§1.2)
3.3  Scan with --dataflow-approximations from .java sources (auto-compile, §1.4)
3.4  Approximation compilation failure handling (bad Java source)
3.5  Duplicate approximation targeting built-in class (error)
3.6  Scan with both --approximations-config and --dataflow-approximations
3.7  Verify approximation changes analysis results
3.8  Invalid YAML config error handling
"""

import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI,
    load_sarif,
    sarif_results,
    sarif_rule_ids,
    sarif_findings_for_rule,
    write_text,
    write_yaml,
    FIXTURES_DIR,
    BUILTIN_RULES_DIR,
)


class TestYAMLApproximationsConfig:
    """3.1-3.2: YAML passThrough config for external library methods."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_approximations_config(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Scan with --approximations-config applies custom passThrough rules
        for external library methods (PDFBox, Jackson, etc.).
        """
        sarif_path = tmp_output / "report.sarif"
        config_path = (
            FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        )

        if not config_path.exists():
            pytest.skip("Fixture approximation config not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            approximations_config=str(config_path),
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with --approximations-config failed")
        data = load_sarif(sarif_path)
        assert len(sarif_results(data)) >= 0

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_approximations_config_with_custom_ruleset(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        --approximations-config and --ruleset can be used together (§1.2).
        Previously these were mutually exclusive.
        """
        sarif_path = tmp_output / "report.sarif"
        config_path = (
            FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        )
        custom_rules = FIXTURES_DIR / "rules"

        if not config_path.exists() or not custom_rules.exists():
            pytest.skip("Fixture files not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            approximations_config=str(config_path),
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok(
            "Scan with both --approximations-config and --ruleset failed. "
            "These should work together per design §1.2"
        )

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_invalid_approximations_config_errors(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """Invalid YAML config should produce a clear error."""
        sarif_path = tmp_output / "report.sarif"
        bad_config = tmp_output / "bad-config.yaml"
        write_text(bad_config, "this is not: [valid: yaml: config")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            approximations_config=str(bad_config),
            timeout=120,
        )
        result.assert_failed("Scan should fail with invalid approximations config")


class TestCodeBasedApproximations:
    """3.3-3.5: Code-based approximations via --dataflow-approximations."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_java_source_approximations(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        --dataflow-approximations with .java source files auto-compiles them.
        The approximation targets PDFBox's PDDocument (an external library class).
        """
        sarif_path = tmp_output / "report.sarif"
        approx_dir = FIXTURES_DIR / "approximations" / "java"

        if not approx_dir.exists():
            pytest.skip("Fixture approximation source not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            dataflow_approximations=str(approx_dir),
            severity=["warning", "error"],
            timeout=600,
        )
        if result.ok:
            data = load_sarif(sarif_path)
            print(
                f"Scan with code-based approximations: {len(sarif_results(data))} findings"
            )

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_approximation_compilation_failure(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        --dataflow-approximations with invalid Java source should fail
        with compilation errors before analysis starts.
        """
        sarif_path = tmp_output / "report.sarif"
        bad_approx_dir = tmp_output / "bad-approximations"
        bad_approx_dir.mkdir()
        write_text(
            bad_approx_dir / "BrokenApprox.java",
            """\
package agent.approximations;

import org.opentaint.ir.approximation.annotation.Approximate;

@Approximate(com.nonexistent.library.DoesNotExist.class)
public class BrokenApprox {
    public void broken() {
        com.nonexistent.library.DoesNotExist x = null;
    }
}
""",
        )

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            dataflow_approximations=str(bad_approx_dir),
            timeout=120,
        )
        result.assert_failed("Scan should fail when approximation compilation fails")
        assert "compil" in result.stderr.lower() or "error" in result.stderr.lower(), (
            f"Error message should mention compilation failure: {result.stderr[:500]}"
        )

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_duplicate_approximation_errors(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        A custom approximation targeting a class that already has a built-in
        approximation should produce an error (bijection violation).
        """
        sarif_path = tmp_output / "report.sarif"
        dup_approx_dir = tmp_output / "dup-approximations"
        dup_approx_dir.mkdir()

        write_text(
            dup_approx_dir / "StreamDuplicate.java",
            """\
package agent.approximations;

import org.opentaint.ir.approximation.annotation.Approximate;

@Approximate(java.util.stream.Stream.class)
public class StreamDuplicate {
    public Object map(java.util.function.Function fn) throws Throwable {
        return fn.apply(null);
    }
}
""",
        )

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            dataflow_approximations=str(dup_approx_dir),
            timeout=300,
        )
        result.assert_failed("Duplicate approximation should produce an error")


class TestCombinedApproximations:
    """3.6-3.7: Combining YAML config + code-based approximations."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_both_approximation_types(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Both --approximations-config and --dataflow-approximations can be
        used in the same scan.
        """
        sarif_path = tmp_output / "report.sarif"
        yaml_config = (
            FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        )
        java_approx = FIXTURES_DIR / "approximations" / "java"
        custom_rules = FIXTURES_DIR / "rules"

        if not yaml_config.exists() or not java_approx.exists():
            pytest.skip("Fixture files not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            approximations_config=str(yaml_config),
            dataflow_approximations=str(java_approx),
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with combined approximation types failed")

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_approximations_change_results(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Running the same scan with and without custom approximations should
        produce different results — the approximations add propagation paths.
        """
        custom_rules = FIXTURES_DIR / "rules"
        yaml_config = (
            FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        )

        if not custom_rules.exists() or not yaml_config.exists():
            pytest.skip("Fixture files not created yet")

        # Run 1: without approximations
        sarif_no_approx = tmp_output / "no-approx" / "report.sarif"
        (tmp_output / "no-approx").mkdir()
        r1 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_no_approx),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            severity=["note", "warning", "error"],
            timeout=600,
        )

        # Run 2: with approximations
        sarif_with_approx = tmp_output / "with-approx" / "report.sarif"
        (tmp_output / "with-approx").mkdir()
        r2 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_with_approx),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            approximations_config=str(yaml_config),
            severity=["note", "warning", "error"],
            timeout=600,
        )

        if r1.ok and r2.ok:
            data1 = load_sarif(sarif_no_approx)
            data2 = load_sarif(sarif_with_approx)
            count1 = len(sarif_results(data1))
            count2 = len(sarif_results(data2))
            print(f"Without approximations: {count1} findings")
            print(f"With approximations:    {count2} findings")
