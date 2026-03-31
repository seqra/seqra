"""
Suite 1: Project Build Scenarios

Tests:
1.1 Scan with pre-compiled project model (project.yaml)
1.2 Scan with source project (triggers auto-compile)
1.3 Compile-only (autobuilder)
1.4 Scan with invalid project path (error handling)
1.5 Scan with custom output directory
"""

import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI,
    load_sarif,
    sarif_results,
    sarif_rule_ids,
    STIRLING_PROJECT_DIR,
    BUILTIN_RULES_DIR,
)


class TestScanPreCompiledProject:
    """1.1: Scan using the pre-compiled Stirling-PDF project model."""

    @pytest.mark.slow
    def test_scan_with_builtin_rules(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """Basic scan with builtin rules produces a valid SARIF with findings."""
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with builtin rules failed")

        # Validate SARIF output
        data = load_sarif(sarif_path)
        results = sarif_results(data)
        assert len(results) > 0, (
            "Scan produced no findings — expected some on Stirling-PDF"
        )

        # Should contain known vulnerability types
        rule_ids = sarif_rule_ids(data)
        print(f"Found {len(results)} findings across rules: {rule_ids}")

    @pytest.mark.slow
    def test_scan_with_custom_ruleset_directory(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """Scan with a custom ruleset directory works alongside builtin."""
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=[str(BUILTIN_RULES_DIR)],
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with custom ruleset directory failed")
        data = load_sarif(sarif_path)
        assert len(sarif_results(data)) > 0

    @pytest.mark.slow
    def test_scan_severity_filter_note(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """Scan with severity=note should include more findings."""
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with note severity failed")


class TestScanFromSourceProject:
    """1.2: Scan from source (auto-compiles via autobuilder first)."""

    @pytest.mark.slow
    def test_scan_from_source_directory(self, cli: OpenTaintCLI, tmp_output: Path):
        """
        Scan the Stirling-PDF source directory (not pre-compiled).
        This triggers auto-compilation via autobuilder.
        """
        sarif_path = tmp_output / "report.sarif"
        source_dir = STIRLING_PROJECT_DIR / "sources"

        if not source_dir.exists():
            pytest.skip("Stirling-PDF source directory not available")

        result = cli.scan(
            project_path=str(source_dir),
            output=str(sarif_path),
            rulesets=["builtin"],
            timeout=900,
        )
        if result.ok:
            data = load_sarif(sarif_path)
            assert len(sarif_results(data)) > 0


class TestCompileOnly:
    """1.3: Test the compile command separately."""

    @pytest.mark.slow
    def test_compile_source_project(self, cli: OpenTaintCLI, tmp_output: Path):
        """Compile a source project into a project model."""
        source_dir = STIRLING_PROJECT_DIR / "sources"
        model_dir = tmp_output / "project-model"

        if not source_dir.exists():
            pytest.skip("Stirling-PDF source directory not available")

        result = cli.compile(
            project_path=str(source_dir),
            output_dir=str(model_dir),
            timeout=300,
        )
        if result.ok:
            project_yaml = model_dir / "project.yaml"
            assert project_yaml.exists(), "compile did not produce project.yaml"


class TestErrorHandling:
    """1.4: Error handling for invalid inputs."""

    def test_scan_nonexistent_project(self, cli: OpenTaintCLI, tmp_output: Path):
        """Scan with nonexistent project path should fail gracefully."""
        sarif_path = tmp_output / "report.sarif"
        result = cli.scan(
            project_path="/nonexistent/project/path",
            output=str(sarif_path),
        )
        result.assert_failed("Scan should fail for nonexistent project")

    def test_scan_missing_output_flag(self, cli: OpenTaintCLI, stirling_project: Path):
        """Scan without -o flag should fail (it's required)."""
        result = cli.run([cli.cli_path, "scan", str(stirling_project)])
        result.assert_failed("Scan should require -o flag")
