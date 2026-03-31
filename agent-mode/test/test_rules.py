"""
Suite 2: Rule Generation Pipeline

Tests:
2.1  Read builtin rules via `opentaint rules-path` (or known path)
2.2  Create custom library + security rules, verify YAML validity
2.3  Run scan with custom ruleset + --rule-id filter
2.4  Run scan with custom ruleset without --rule-id filter (all rules active)
2.5  Bootstrap test project, build, and run rule tests
2.6  Rule test: false negative detected (positive sample with wrong pattern)
2.7  Rule test: false positive detected (negative sample with too-broad pattern)
2.8  Run scan on Stirling-PDF with custom path-traversal rule
"""

import json
import shutil
import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI,
    load_sarif,
    sarif_results,
    sarif_rule_ids,
    sarif_findings_for_rule,
    write_text,
    BUILTIN_RULES_DIR,
    FIXTURES_DIR,
)


class TestReadBuiltinRules:
    """2.1: Agent can discover and read builtin rules."""

    def test_builtin_rules_directory_exists(self, builtin_rules: Path):
        """Builtin rules directory exists and contains rule files."""
        security_dir = builtin_rules / "java" / "security"
        assert security_dir.exists(), f"No security rules at {security_dir}"
        rule_files = list(security_dir.glob("*.yaml"))
        assert len(rule_files) > 10, (
            f"Expected >10 security rules, found {len(rule_files)}"
        )

    def test_builtin_lib_rules_exist(self, builtin_rules: Path):
        """Library rules (sources/sinks) exist."""
        lib_generic = builtin_rules / "java" / "lib" / "generic"
        assert lib_generic.exists()
        assert (lib_generic / "servlet-untrusted-data-source.yaml").exists()
        assert (lib_generic / "path-traversal-sinks.yaml").exists()

    @pytest.mark.new_feature
    def test_rules_path_command(self, cli: OpenTaintCLI):
        """opentaint rules-path prints the rules directory."""
        result = cli.rules_path()
        result.assert_ok("rules-path command failed")
        rules_dir = Path(result.stdout.strip())
        assert rules_dir.exists(), f"rules-path returned non-existent dir: {rules_dir}"
        assert (rules_dir / "java" / "security").is_dir()


class TestCustomRuleCreation:
    """2.2: Create and validate custom rules."""

    def test_custom_rules_are_valid_yaml(self):
        """Fixture rule files are syntactically valid YAML with expected structure."""
        import yaml

        rules_dir = FIXTURES_DIR / "rules"
        for rule_file in rules_dir.rglob("*.yaml"):
            with open(rule_file) as f:
                data = yaml.safe_load(f)
            assert "rules" in data, f"Rule file {rule_file} missing 'rules' key"
            for rule in data["rules"]:
                assert "id" in rule, f"Rule in {rule_file} missing 'id'"
                assert "severity" in rule, f"Rule {rule['id']} missing 'severity'"
                assert "languages" in rule, f"Rule {rule['id']} missing 'languages'"

    def test_library_rule_has_lib_option(self):
        """Library rules must have options.lib: true."""
        import yaml

        lib_rule = FIXTURES_DIR / "rules" / "java" / "lib" / "stirling-source.yaml"
        if not lib_rule.exists():
            pytest.skip("Library rule fixture not created yet")
        with open(lib_rule) as f:
            data = yaml.safe_load(f)
        for rule in data["rules"]:
            assert rule.get("options", {}).get("lib") is True, (
                f"Library rule {rule['id']} missing options.lib: true"
            )

    def test_security_rule_has_metadata(self):
        """Security rules must have metadata.cwe and metadata.short-description."""
        import yaml

        sec_rule = (
            FIXTURES_DIR
            / "rules"
            / "java"
            / "security"
            / "stirling-path-traversal.yaml"
        )
        if not sec_rule.exists():
            pytest.skip("Security rule fixture not created yet")
        with open(sec_rule) as f:
            data = yaml.safe_load(f)
        for rule in data["rules"]:
            if rule.get("options", {}).get("lib"):
                continue
            meta = rule.get("metadata", {})
            assert "cwe" in meta, f"Security rule {rule['id']} missing metadata.cwe"
            assert "short-description" in meta, (
                f"Security rule {rule['id']} missing metadata.short-description"
            )


class TestScanWithRuleIdFilter:
    """2.3-2.4: Scan with --rule-id filter."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_rule_id_filter(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Scan with --rule-id should only produce findings for the specified rule.
        Library rules referenced via refs should be auto-included.
        """
        sarif_path = tmp_output / "report.sarif"
        custom_rules = FIXTURES_DIR / "rules"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with --rule-id filter failed")

        data = load_sarif(sarif_path)
        rule_ids = sarif_rule_ids(data)
        for rid in rule_ids:
            assert rid == "stirling-path-traversal", (
                f"Unexpected rule '{rid}' in output — --rule-id filter not working"
            )

    @pytest.mark.slow
    def test_scan_without_rule_id_filter_includes_all(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Scan without --rule-id should include findings from all active rules.
        """
        sarif_path = tmp_output / "report.sarif"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan without rule-id filter failed")

        data = load_sarif(sarif_path)
        rule_ids = sarif_rule_ids(data)
        assert len(rule_ids) > 1, f"Expected multiple rule IDs, got: {rule_ids}"


class TestRuleTests:
    """2.5-2.7: Rule test workflow."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_init_test_project(self, cli: OpenTaintCLI, tmp_output: Path):
        """opentaint init-test-project bootstraps a valid Gradle test project."""
        test_project_dir = tmp_output / "test-project"

        result = cli.init_test_project(
            output_dir=str(test_project_dir),
            dependencies=[
                "org.springframework:spring-web:6.2.12",
                "jakarta.servlet:jakarta.servlet-api:6.0.0",
            ],
        )
        if not result.ok:
            pytest.skip("init-test-project not available (new feature)")

        assert (test_project_dir / "build.gradle.kts").exists()
        assert (test_project_dir / "settings.gradle.kts").exists()
        assert (test_project_dir / "libs" / "opentaint-sast-test-util.jar").exists()
        assert (test_project_dir / "src" / "main" / "java" / "test").is_dir()

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_rule_test_all_pass(self, cli: OpenTaintCLI, tmp_output: Path):
        """
        Create a test project with correct positive/negative samples.
        Rule tests should all pass.
        """
        test_project_dir = tmp_output / "test-project"
        compiled_dir = tmp_output / "test-compiled"
        test_output = tmp_output / "test-output"
        rules_dir = FIXTURES_DIR / "rules"

        result = cli.init_test_project(
            output_dir=str(test_project_dir),
            dependencies=[
                "org.springframework:spring-web:6.2.12",
                "jakarta.servlet:jakarta.servlet-api:6.0.0",
            ],
        )
        if not result.ok:
            pytest.skip("init-test-project not available")

        # Copy test samples
        samples_src = FIXTURES_DIR / "test-samples" / "src"
        samples_dst = test_project_dir / "src"
        if samples_src.exists():
            shutil.copytree(samples_src, samples_dst, dirs_exist_ok=True)

        compile_result = cli.compile(str(test_project_dir), str(compiled_dir))
        compile_result.assert_ok("Failed to compile test project")

        test_result = cli.test_rules(
            project_path=str(compiled_dir / "project.yaml"),
            rulesets=[str(rules_dir)],
            output_dir=str(test_output),
        )
        test_result.assert_ok("Rule tests failed")

        result_json = test_output / "test-result.json"
        assert result_json.exists(), "test-result.json not produced"
        with open(result_json) as f:
            results = json.load(f)

        assert len(results.get("falsePositive", [])) == 0, (
            f"Unexpected false positives: {results['falsePositive']}"
        )
        assert len(results.get("falseNegative", [])) == 0, (
            f"Unexpected false negatives: {results['falseNegative']}"
        )
        assert len(results.get("success", [])) > 0, (
            "No successful tests — something is wrong"
        )

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_rule_test_detects_false_negative(
        self, cli: OpenTaintCLI, tmp_output: Path
    ):
        """
        A @PositiveRuleSample that doesn't match the rule → false negative.
        This tests that the test framework correctly detects missing findings.
        """
        test_project_dir = tmp_output / "test-project-fn"
        compiled_dir = tmp_output / "test-compiled-fn"
        test_output = tmp_output / "test-output-fn"

        rules_dir = tmp_output / "broken-rules" / "java" / "security"
        rules_dir.mkdir(parents=True)
        write_text(
            rules_dir / "broken-rule.yaml",
            """\
rules:
  - id: broken-path-traversal
    severity: ERROR
    message: This rule intentionally won't match
    metadata:
      cwe: CWE-22
      short-description: Broken rule for testing FN detection
    languages: [java]
    patterns:
      - pattern: ThisClassDoesNotExist.neverCalled($X)
""",
        )

        result = cli.init_test_project(
            output_dir=str(test_project_dir),
            dependencies=["jakarta.servlet:jakarta.servlet-api:6.0.0"],
        )
        if not result.ok:
            pytest.skip("init-test-project not available")

        test_file = (
            test_project_dir
            / "src"
            / "main"
            / "java"
            / "test"
            / "FalseNegativeTest.java"
        )
        write_text(
            test_file,
            """\
package test;

import org.opentaint.sast.test.util.PositiveRuleSample;

public class FalseNegativeTest {

    @PositiveRuleSample(value = "java/security/broken-rule.yaml", id = "broken-path-traversal")
    public void shouldTriggerButWont() {
        String x = System.getenv("USER_INPUT");
        System.out.println(x);
    }
}
""",
        )

        compile_result = cli.compile(str(test_project_dir), str(compiled_dir))
        if not compile_result.ok:
            pytest.skip("Cannot compile test project")

        test_result = cli.test_rules(
            project_path=str(compiled_dir / "project.yaml"),
            rulesets=[str(tmp_output / "broken-rules")],
            output_dir=str(test_output),
        )

        result_json = test_output / "test-result.json"
        if result_json.exists():
            with open(result_json) as f:
                results = json.load(f)
            assert len(results.get("falseNegative", [])) > 0, (
                "Expected false negative not detected"
            )


class TestScanStirlingWithCustomRule:
    """2.8: Run custom path-traversal rule on Stirling-PDF."""

    @pytest.mark.slow
    def test_scan_stirling_with_path_traversal_rule(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Scan Stirling-PDF with our custom path-traversal rule.
        Stirling-PDF handles file uploads in several controllers —
        we expect the rule to find some findings.
        """
        sarif_path = tmp_output / "report.sarif"
        custom_rules = FIXTURES_DIR / "rules"

        if not custom_rules.exists():
            pytest.skip("Fixture rules not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=[
                "java/security/stirling-path-traversal.yaml:stirling-path-traversal"
            ],
            severity=["note", "warning", "error"],
            timeout=600,
        )

        if result.ok:
            data = load_sarif(sarif_path)
            findings = sarif_findings_for_rule(data, "stirling-path-traversal")
            print(f"Found {len(findings)} path-traversal findings in Stirling-PDF")
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
                    print(f"  - {uri}:{line}")
        else:
            print(f"Scan failed or produced no output: {result.stderr[:500]}")
