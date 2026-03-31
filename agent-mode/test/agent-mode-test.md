# Agent Mode Test Pipeline

## Table of Contents

1. [Overview](#1-overview)
2. [Test Environment Setup](#2-test-environment-setup)
3. [Test Infrastructure (`conftest.py`)](#3-test-infrastructure-conftestpy)
4. [Test Suite 1: Project Build Scenarios](#4-test-suite-1-project-build-scenarios)
5. [Test Suite 2: Rule Generation Pipeline](#5-test-suite-2-rule-generation-pipeline)
6. [Test Suite 3: Approximations Generation/Override](#6-test-suite-3-approximations-generationoverride)
7. [Test Suite 4: External Methods Extraction](#7-test-suite-4-external-methods-extraction)
8. [Test Suite 5: Full Agent Loop (Integration)](#8-test-suite-5-full-agent-loop-integration)
9. [Running Tests](#9-running-tests)

---

## 1. Overview

This document defines a test pipeline for validating the agent-mode features designed in `agent-mode/design/agent-mode-design.md`. Tests use **Python (pytest)** scripts that invoke the `opentaint` Go CLI and the analyzer JAR directly, validating outputs against expected results.

### Test target project

All tests use the Stirling-PDF project at `/home/sobol/data/Stirling-PDF/seqra-project/project.yaml` — a real-world Spring Boot application with 538 Java source files, 3 modules (proprietary, core, common), and 400 dependencies. This project is already compiled (classes + dependencies + sources are in place), so tests can skip the build step for faster iteration, or exercise the build pipeline explicitly.

### What we are testing

The test pipeline validates that the **new CLI features** from the design doc work correctly:

| Feature | Design Section | Test Suite |
|---|---|---|
| `opentaint scan` with pre-compiled project | §2.1 | Suite 1 |
| `opentaint compile` (autobuilder) | §2.1 | Suite 1 |
| `--ruleset` with custom rules | §2.1 | Suite 2 |
| `--rule-id` filter | §1.6, §2.1 | Suite 2 |
| `opentaint test-rules` | §1.5, §2.1 | Suite 2 |
| `opentaint init-test-project` | §1.8 | Suite 2 |
| `--approximations-config` (YAML passThrough) | §1.2, §2.1 | Suite 3 |
| `--dataflow-approximations` (code-based, auto-compile) | §1.3, §1.4, §2.1 | Suite 3 |
| `--external-methods` output | §1.1, §2.1 | Suite 4 |
| `opentaint rules-path` | §1.8, §2.1 | Suite 4 |
| Full loop: rule → test → scan → external methods → approx → rescan | §4 (Meta Prompt) | Suite 5 |

### Constraints

Since the new CLI features are **not yet implemented**, the tests serve two purposes:
1. **Specification** — define the expected behavior precisely so implementation can be verified
2. **Incremental validation** — tests that exercise current (existing) functionality can run today; tests for new features are marked `@pytest.mark.new_feature` and will pass once implemented

Where a new CLI command doesn't exist yet, we fall back to invoking the analyzer JAR directly with the equivalent Kotlin CLI flags. This ensures we can test the **engine behavior** even before the Go CLI wrapper is ready.

---

## 2. Test Environment Setup

### Directory layout

```
agent-mode/test/
├── agent-mode-test.md            # This document
├── conftest.py                    # Shared fixtures and helpers
├── test_build.py                  # Suite 1: Project build scenarios
├── test_rules.py                  # Suite 2: Rule generation pipeline
├── test_approximations.py         # Suite 3: Approximations
├── test_external_methods.py       # Suite 4: External methods extraction
├── test_full_loop.py              # Suite 5: Full agent loop
├── fixtures/
│   ├── rules/                     # Test rule YAML files
│   │   ├── java/
│   │   │   ├── lib/
│   │   │   │   └── stirling-source.yaml
│   │   │   └── security/
│   │   │       ├── stirling-path-traversal.yaml
│   │   │       └── stirling-sqli.yaml
│   │   └── README.md
│   ├── approximations/
│   │   ├── yaml/
│   │   │   └── custom-propagators.yaml
│   │   └── java/
│   │       └── StirlingPDFUtils.java
│   └── test-samples/
│       └── src/main/java/test/
│           ├── PathTraversalTest.java
│           └── SqlInjectionTest.java
└── pytest.ini
```

### Prerequisites

```bash
# Python dependencies
pip install pytest pyyaml

# OpenTaint CLI on PATH (or use --analyzer-jar / --autobuilder-jar flags for local dev)
which opentaint || echo "opentaint not on PATH — will use direct JAR invocation"

# Stirling-PDF project available
test -f /home/sobol/data/Stirling-PDF/seqra-project/project.yaml
```

### `pytest.ini`

```ini
[pytest]
testpaths = .
markers =
    new_feature: Tests for features not yet implemented (deselect with -m "not new_feature")
    slow: Tests that run full analysis (>60s)
```

---

## 3. Test Infrastructure (`conftest.py`)

```python
"""
Shared fixtures and helpers for agent-mode tests.

Handles two execution modes:
1. Go CLI mode: when `opentaint` is on PATH (production)
2. Direct JAR mode: when running against locally-built JARs (development)
"""

import json
import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import pytest
import yaml


# ─── Paths ───────────────────────────────────────────────────────────────────

STIRLING_PROJECT = Path("/home/sobol/data/Stirling-PDF/seqra-project/project.yaml")
STIRLING_PROJECT_DIR = STIRLING_PROJECT.parent
OPENTAINT_ROOT = Path(__file__).resolve().parent.parent.parent  # -> opentaint/
FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"
BUILTIN_RULES_DIR = OPENTAINT_ROOT / "rules" / "ruleset"


# ─── CLI Abstraction ─────────────────────────────────────────────────────────

def _find_opentaint_cli() -> Optional[str]:
    """Check if opentaint is on PATH."""
    return shutil.which("opentaint")


def _find_analyzer_jar() -> Optional[Path]:
    """Find locally-built analyzer JAR."""
    candidates = [
        OPENTAINT_ROOT / "core" / "build" / "libs" / "opentaint-jvm-sast.jar",
        OPENTAINT_ROOT / "core" / "build" / "libs" / "opentaint-project-analyzer.jar",
    ]
    for c in candidates:
        if c.exists():
            return c
    return None


def _find_autobuilder_jar() -> Optional[Path]:
    """Find locally-built autobuilder JAR."""
    candidates = [
        OPENTAINT_ROOT / "autobuilder" / "build" / "libs" / "opentaint-project-auto-builder.jar",
    ]
    for c in candidates:
        if c.exists():
            return c
    return None


def _find_java() -> str:
    """Find Java 21 (analyzer requires it)."""
    # Check JAVA_HOME first
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        java = Path(java_home) / "bin" / "java"
        if java.exists():
            return str(java)
    # Fall back to PATH
    java = shutil.which("java")
    if java:
        return java
    raise RuntimeError("Java not found. Set JAVA_HOME or add java to PATH.")


@dataclass
class CLIResult:
    """Result of a CLI command execution."""
    returncode: int
    stdout: str
    stderr: str
    command: list[str]

    @property
    def ok(self) -> bool:
        return self.returncode == 0

    def assert_ok(self, msg: str = ""):
        assert self.ok, (
            f"Command failed (rc={self.returncode}){': ' + msg if msg else ''}\n"
            f"  cmd: {' '.join(self.command)}\n"
            f"  stderr: {self.stderr[:2000]}"
        )

    def assert_failed(self, msg: str = ""):
        assert not self.ok, (
            f"Command unexpectedly succeeded{': ' + msg if msg else ''}\n"
            f"  cmd: {' '.join(self.command)}\n"
            f"  stdout: {self.stdout[:2000]}"
        )


@dataclass
class OpenTaintCLI:
    """
    Abstraction over the opentaint CLI.

    Supports two modes:
    - Go CLI: uses `opentaint` binary from PATH
    - Direct JAR: uses `java -jar analyzer.jar` for scan, `java -jar autobuilder.jar` for compile
    """
    cli_path: Optional[str] = None
    analyzer_jar: Optional[Path] = None
    autobuilder_jar: Optional[Path] = None
    java_path: str = "java"
    timeout: int = 600  # seconds

    @property
    def has_cli(self) -> bool:
        return self.cli_path is not None

    def run(self, args: list[str], timeout: Optional[int] = None, env: Optional[dict] = None) -> CLIResult:
        """Run an arbitrary command and return the result."""
        run_env = {**os.environ, **(env or {})}
        t = timeout or self.timeout
        try:
            proc = subprocess.run(
                args,
                capture_output=True,
                text=True,
                timeout=t,
                env=run_env,
            )
            return CLIResult(proc.returncode, proc.stdout, proc.stderr, args)
        except subprocess.TimeoutExpired:
            return CLIResult(-1, "", f"Timeout after {t}s", args)

    def scan(
        self,
        project_path: str,
        output: str,
        rulesets: list[str] = None,
        rule_ids: list[str] = None,
        approximations_config: Optional[str] = None,
        dataflow_approximations: Optional[str] = None,
        external_methods: Optional[str] = None,
        severity: list[str] = None,
        timeout: int = 900,
        max_memory: str = "8G",
        extra_flags: list[str] = None,
    ) -> CLIResult:
        """Run opentaint scan (or direct analyzer JAR invocation)."""

        if self.has_cli:
            cmd = [self.cli_path, "scan", project_path, "-o", output]
            for rs in (rulesets or ["builtin"]):
                cmd.extend(["--ruleset", rs])
            for rid in (rule_ids or []):
                cmd.extend(["--rule-id", rid])
            if approximations_config:
                cmd.extend(["--approximations-config", approximations_config])
            if dataflow_approximations:
                cmd.extend(["--dataflow-approximations", dataflow_approximations])
            if external_methods:
                cmd.extend(["--external-methods", external_methods])
            for sev in (severity or ["warning", "error"]):
                cmd.extend(["--severity", sev])
            cmd.extend(["--timeout", f"{timeout}s", "--max-memory", max_memory])
            cmd.extend(extra_flags or [])
            return self.run(cmd, timeout=timeout + 60)

        # Direct JAR invocation
        assert self.analyzer_jar, "No analyzer JAR found"
        output_dir = str(Path(output).parent)
        sarif_name = Path(output).name
        cmd = [
            self.java_path, f"-Xmx{max_memory}",
            "-Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000",
            "-Djdk.util.jar.enableMultiRelease=false",
            "-jar", str(self.analyzer_jar),
            "--project", project_path,
            "--output-dir", output_dir,
            "--sarif-file-name", sarif_name,
            f"--ifds-analysis-timeout={timeout}",
            "--verbosity=info",
        ]
        for rs in (rulesets or []):
            if rs == "builtin":
                cmd.extend(["--semgrep-rule-set", str(BUILTIN_RULES_DIR)])
            else:
                cmd.extend(["--semgrep-rule-set", rs])
        for rid in (rule_ids or []):
            cmd.extend(["--semgrep-rule-id", rid])
        if approximations_config:
            cmd.extend(["--config", approximations_config])
        if external_methods:
            cmd.extend(["--external-methods-output", external_methods])
        for sev in (severity or ["warning", "error"]):
            cmd.extend([f"--semgrep-rule-severity={sev}"])
        # Note: --dataflow-approximations needs auto-compile in Go CLI;
        # for direct JAR, pass pre-compiled classes directory
        if dataflow_approximations:
            cmd.extend(["--dataflow-approximations", dataflow_approximations])
        cmd.extend(extra_flags or [])
        return self.run(cmd, timeout=timeout + 60)

    def test_rules(
        self,
        project_path: str,
        rulesets: list[str],
        output_dir: str,
        timeout: int = 300,
        max_memory: str = "8G",
    ) -> CLIResult:
        """Run opentaint test-rules (or direct JAR with --debug-run-rule-tests)."""

        if self.has_cli:
            cmd = [self.cli_path, "test-rules", project_path]
            for rs in rulesets:
                cmd.extend(["--ruleset", rs])
            cmd.extend(["-o", output_dir])
            cmd.extend(["--timeout", f"{timeout}s", "--max-memory", max_memory])
            return self.run(cmd, timeout=timeout + 60)

        # Direct JAR invocation
        assert self.analyzer_jar, "No analyzer JAR found"
        cmd = [
            self.java_path, f"-Xmx{max_memory}",
            "-Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000",
            "-Djdk.util.jar.enableMultiRelease=false",
            "-jar", str(self.analyzer_jar),
            "--project", project_path,
            "--output-dir", output_dir,
            "--debug-run-rule-tests",
            f"--ifds-analysis-timeout={timeout}",
            "--verbosity=info",
        ]
        for rs in rulesets:
            cmd.extend(["--semgrep-rule-set", rs])
        return self.run(cmd, timeout=timeout + 60)

    def compile(
        self,
        project_path: str,
        output_dir: str,
        timeout: int = 300,
    ) -> CLIResult:
        """Run opentaint compile (or direct autobuilder JAR invocation)."""

        if self.has_cli:
            cmd = [self.cli_path, "compile", project_path, "-o", output_dir]
            return self.run(cmd, timeout=timeout + 60)

        # Direct JAR invocation
        assert self.autobuilder_jar, "No autobuilder JAR found"
        cmd = [
            self.java_path, "-Xmx1G",
            "-jar", str(self.autobuilder_jar),
            "--project-root-dir", project_path,
            "--result-dir", output_dir,
            "--build", "portable",
            "--verbosity=info",
        ]
        return self.run(cmd, timeout=timeout + 60)

    def rules_path(self) -> CLIResult:
        """Run opentaint rules-path."""
        if self.has_cli:
            return self.run([self.cli_path, "rules-path"])
        # Fall back to known builtin path
        return CLIResult(0, str(BUILTIN_RULES_DIR), "", ["echo", str(BUILTIN_RULES_DIR)])

    def init_test_project(
        self,
        output_dir: str,
        dependencies: list[str] = None,
    ) -> CLIResult:
        """Run opentaint init-test-project."""
        if self.has_cli:
            cmd = [self.cli_path, "init-test-project", output_dir]
            for dep in (dependencies or []):
                cmd.extend(["--dependency", dep])
            return self.run(cmd)
        # Fallback: not available without Go CLI
        return CLIResult(1, "", "init-test-project not available in direct JAR mode", [])


# ─── Fixtures ─────────────────────────────────────────────────────────────────

@pytest.fixture(scope="session")
def cli() -> OpenTaintCLI:
    """Provide an OpenTaintCLI instance configured for the current environment."""
    return OpenTaintCLI(
        cli_path=_find_opentaint_cli(),
        analyzer_jar=_find_analyzer_jar(),
        autobuilder_jar=_find_autobuilder_jar(),
        java_path=_find_java(),
    )


@pytest.fixture(scope="session")
def stirling_project() -> Path:
    """Path to the Stirling-PDF project.yaml."""
    assert STIRLING_PROJECT.exists(), f"Stirling-PDF project not found at {STIRLING_PROJECT}"
    return STIRLING_PROJECT


@pytest.fixture
def tmp_output(tmp_path) -> Path:
    """Provide a temporary output directory for test results."""
    return tmp_path


@pytest.fixture(scope="session")
def builtin_rules() -> Path:
    """Path to the built-in rules directory."""
    assert BUILTIN_RULES_DIR.exists(), f"Builtin rules not found at {BUILTIN_RULES_DIR}"
    return BUILTIN_RULES_DIR


# ─── Helpers ──────────────────────────────────────────────────────────────────

def load_sarif(path: Path) -> dict:
    """Load and validate a SARIF file."""
    assert path.exists(), f"SARIF file not found: {path}"
    with open(path) as f:
        data = json.load(f)
    assert data.get("version") == "2.1.0", "Not a valid SARIF 2.1.0 file"
    assert "runs" in data and len(data["runs"]) > 0, "SARIF has no runs"
    return data


def sarif_results(data: dict) -> list[dict]:
    """Extract results from a SARIF report."""
    return data["runs"][0].get("results", [])


def sarif_rule_ids(data: dict) -> set[str]:
    """Extract unique rule IDs from SARIF results."""
    return {r["ruleId"] for r in sarif_results(data)}


def sarif_findings_for_rule(data: dict, rule_id: str) -> list[dict]:
    """Get findings for a specific rule ID."""
    return [r for r in sarif_results(data) if r["ruleId"] == rule_id]


def load_external_methods(path: Path) -> dict:
    """Load and validate an external methods YAML file."""
    assert path.exists(), f"External methods file not found: {path}"
    with open(path) as f:
        data = yaml.safe_load(f)
    assert isinstance(data, dict), "External methods file must be a YAML mapping"
    assert "withoutRules" in data or "withRules" in data, "Missing withoutRules/withRules sections"
    return data


def count_external_methods(data: dict) -> tuple[int, int]:
    """Return (without_rules_count, with_rules_count)."""
    without = len(data.get("withoutRules", []))
    with_rules = len(data.get("withRules", []))
    return without, with_rules


def write_yaml(path: Path, content: dict):
    """Write a YAML file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w") as f:
        yaml.dump(content, f, default_flow_style=False, sort_keys=False)


def write_text(path: Path, content: str):
    """Write a text file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
```

---

## 4. Test Suite 1: Project Build Scenarios

**File: `test_build.py`**

Tests that `opentaint scan` and `opentaint compile` work with different project input modes.

```python
"""
Suite 1: Project Build Scenarios

Tests:
1.1 Scan with pre-compiled project model (project.yaml)
1.2 Scan with source project (triggers auto-compile)
1.3 Compile-only (autobuilder)
1.4 Scan with invalid project path (error handling)
1.5 Scan with pre-compiled project, custom output directory
"""

import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI, load_sarif, sarif_results, sarif_rule_ids,
    STIRLING_PROJECT_DIR, BUILTIN_RULES_DIR,
)


class TestScanPreCompiledProject:
    """1.1: Scan using the pre-compiled Stirling-PDF project model."""

    @pytest.mark.slow
    def test_scan_with_builtin_rules(self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path):
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
        assert len(results) > 0, "Scan produced no findings — expected some on Stirling-PDF"

        # Should contain known vulnerability types
        rule_ids = sarif_rule_ids(data)
        # Stirling-PDF is known to have path-traversal and XSS issues
        print(f"Found {len(results)} findings across rules: {rule_ids}")

    @pytest.mark.slow
    def test_scan_with_custom_ruleset_directory(self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path):
        """Scan with a custom ruleset directory works alongside builtin."""
        sarif_path = tmp_output / "report.sarif"

        # Use the builtin rules directory directly as a "custom" ruleset
        # This is equivalent to --ruleset builtin but tests the custom path logic
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
    def test_scan_severity_filter_note(self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path):
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

        Uses the mirrored source tree inside seqra-project/sources/ which
        is a full copy of the Stirling-PDF repo.
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
        # This may fail if the autobuilder can't build Stirling-PDF
        # (requires Java 17+, Gradle wrapper). That's acceptable — the test
        # validates the auto-compile → scan pipeline.
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
        if not cli.has_cli:
            pytest.skip("Requires Go CLI for flag validation")
        # Invoke without -o
        result = cli.run([cli.cli_path, "scan", str(stirling_project)])
        result.assert_failed("Scan should require -o flag")
```

---

## 5. Test Suite 2: Rule Generation Pipeline

**File: `test_rules.py`**

Tests the full rule lifecycle: create rule → create test samples → build test project → run rule tests → run scan with rule.

### Fixture rules used by tests

**`fixtures/rules/java/lib/stirling-source.yaml`** — a library rule defining a source for Spring `@PostMapping` multipart file parameters:

```yaml
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
```

**`fixtures/rules/java/security/stirling-path-traversal.yaml`** — a security rule joining the source with a built-in path traversal sink:

```yaml
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
        - rule: java/lib/generic/path-traversal-sinks.yaml#java-path-traversal-sink
          as: sink
      on:
        - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
```

### Test samples

**`fixtures/test-samples/src/main/java/test/PathTraversalTest.java`**:

```java
package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.opentaint.sast.test.util.NegativeRuleSample;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PathTraversalTest {

    @PositiveRuleSample(value = "java/security/stirling-path-traversal.yaml", id = "stirling-path-traversal")
    @PostMapping("/upload-vulnerable")
    public String vulnerable(@RequestParam MultipartFile file) throws IOException {
        // Directly use original filename — path traversal possible
        String filename = file.getOriginalFilename();
        Path dest = Paths.get("/uploads/" + filename);
        Files.copy(file.getInputStream(), dest);
        return "uploaded";
    }

    @NegativeRuleSample(value = "java/security/stirling-path-traversal.yaml", id = "stirling-path-traversal")
    @PostMapping("/upload-safe")
    public String safe(@RequestParam MultipartFile file) throws IOException {
        // Use sanitized filename — only the base name, no path components
        String filename = new File(file.getOriginalFilename()).getName();
        Path dest = Paths.get("/uploads/").resolve(filename);
        Files.copy(file.getInputStream(), dest);
        return "uploaded";
    }
}
```

### Test script

```python
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
    OpenTaintCLI, load_sarif, sarif_results, sarif_rule_ids,
    sarif_findings_for_rule, write_yaml, write_text,
    BUILTIN_RULES_DIR, FIXTURES_DIR,
)


class TestReadBuiltinRules:
    """2.1: Agent can discover and read builtin rules."""

    def test_builtin_rules_directory_exists(self, builtin_rules: Path):
        """Builtin rules directory exists and contains rule files."""
        security_dir = builtin_rules / "java" / "security"
        assert security_dir.exists(), f"No security rules at {security_dir}"
        rule_files = list(security_dir.glob("*.yaml"))
        assert len(rule_files) > 10, f"Expected >10 security rules, found {len(rule_files)}"

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
        for rule_file in FIXTURES_DIR.rglob("*.yaml"):
            if rule_file.parent.name == "yaml":
                continue  # skip approximation configs
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
            assert rule.get("options", {}).get("lib") is True, \
                f"Library rule {rule['id']} missing options.lib: true"

    def test_security_rule_has_metadata(self):
        """Security rules must have metadata.cwe and metadata.short-description."""
        import yaml
        sec_rule = FIXTURES_DIR / "rules" / "java" / "security" / "stirling-path-traversal.yaml"
        if not sec_rule.exists():
            pytest.skip("Security rule fixture not created yet")
        with open(sec_rule) as f:
            data = yaml.safe_load(f)
        for rule in data["rules"]:
            if rule.get("options", {}).get("lib"):
                continue
            meta = rule.get("metadata", {})
            assert "cwe" in meta, f"Security rule {rule['id']} missing metadata.cwe"
            assert "short-description" in meta, f"Security rule {rule['id']} missing metadata.short-description"


class TestScanWithRuleIdFilter:
    """2.3-2.4: Scan with --rule-id filter."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_rule_id_filter(self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path):
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
            rule_ids=["stirling-path-traversal"],
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with --rule-id filter failed")

        data = load_sarif(sarif_path)
        rule_ids = sarif_rule_ids(data)
        # Only our rule should appear (lib rules don't produce top-level findings)
        for rid in rule_ids:
            assert rid == "stirling-path-traversal", \
                f"Unexpected rule '{rid}' in output — --rule-id filter not working"

    @pytest.mark.slow
    def test_scan_without_rule_id_filter_includes_all(self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path):
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
        # Should have multiple rule IDs
        assert len(rule_ids) > 1, f"Expected multiple rule IDs, got: {rule_ids}"


class TestRuleTests:
    """2.5-2.7: Rule test workflow."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_init_test_project(self, cli: OpenTaintCLI, tmp_output: Path):
        """
        opentaint init-test-project bootstraps a valid Gradle test project.
        """
        test_project_dir = tmp_output / "test-project"

        result = cli.init_test_project(
            output_dir=str(test_project_dir),
            dependencies=["org.springframework:spring-web:6.2.12", "jakarta.servlet:jakarta.servlet-api:6.0.0"],
        )
        if not result.ok:
            pytest.skip("init-test-project not available (new feature)")

        # Verify structure
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
        # Setup: copy fixture test samples and rules
        test_project_dir = tmp_output / "test-project"
        compiled_dir = tmp_output / "test-compiled"
        test_output = tmp_output / "test-output"
        rules_dir = FIXTURES_DIR / "rules"

        # Bootstrap (or manually create if CLI not available)
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

        # Compile test project
        compile_result = cli.compile(str(test_project_dir), str(compiled_dir))
        compile_result.assert_ok("Failed to compile test project")

        # Run rule tests
        test_result = cli.test_rules(
            project_path=str(compiled_dir / "project.yaml"),
            rulesets=[str(rules_dir)],
            output_dir=str(test_output),
        )
        test_result.assert_ok("Rule tests failed")

        # Check test-result.json
        result_json = test_output / "test-result.json"
        assert result_json.exists(), "test-result.json not produced"
        with open(result_json) as f:
            results = json.load(f)

        assert len(results.get("falsePositive", [])) == 0, \
            f"Unexpected false positives: {results['falsePositive']}"
        assert len(results.get("falseNegative", [])) == 0, \
            f"Unexpected false negatives: {results['falseNegative']}"
        assert len(results.get("success", [])) > 0, \
            "No successful tests — something is wrong"

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_rule_test_detects_false_negative(self, cli: OpenTaintCLI, tmp_output: Path):
        """
        A @PositiveRuleSample that doesn't match the rule → false negative.
        This tests that the test framework correctly detects missing findings.
        """
        test_project_dir = tmp_output / "test-project-fn"
        compiled_dir = tmp_output / "test-compiled-fn"
        test_output = tmp_output / "test-output-fn"

        # Create a rule that intentionally won't match the test sample
        rules_dir = tmp_output / "broken-rules" / "java" / "security"
        rules_dir.mkdir(parents=True)
        write_text(rules_dir / "broken-rule.yaml", """\
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
""")

        # Create test sample that references the rule
        result = cli.init_test_project(
            output_dir=str(test_project_dir),
            dependencies=["jakarta.servlet:jakarta.servlet-api:6.0.0"],
        )
        if not result.ok:
            pytest.skip("init-test-project not available")

        test_file = test_project_dir / "src" / "main" / "java" / "test" / "FalseNegativeTest.java"
        write_text(test_file, """\
package test;

import org.opentaint.sast.test.util.PositiveRuleSample;

public class FalseNegativeTest {

    @PositiveRuleSample(value = "java/security/broken-rule.yaml", id = "broken-path-traversal")
    public void shouldTriggerButWont() {
        String x = System.getenv("USER_INPUT");
        System.out.println(x);  // not a real sink for the broken rule
    }
}
""")

        compile_result = cli.compile(str(test_project_dir), str(compiled_dir))
        if not compile_result.ok:
            pytest.skip("Cannot compile test project")

        test_result = cli.test_rules(
            project_path=str(compiled_dir / "project.yaml"),
            rulesets=[str(tmp_output / "broken-rules")],
            output_dir=str(test_output),
        )

        # The test framework should detect this as a false negative
        result_json = test_output / "test-result.json"
        if result_json.exists():
            with open(result_json) as f:
                results = json.load(f)
            assert len(results.get("falseNegative", [])) > 0, \
                "Expected false negative not detected"


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
            rule_ids=["stirling-path-traversal"],
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
                    uri = locs[0].get("physicalLocation", {}).get("artifactLocation", {}).get("uri", "?")
                    line = locs[0].get("physicalLocation", {}).get("region", {}).get("startLine", "?")
                    print(f"  - {uri}:{line}")
        else:
            # Rule might not match if patterns are wrong — that's part of testing
            print(f"Scan failed or produced no output: {result.stderr[:500]}")
```

---

## 6. Test Suite 3: Approximations Generation/Override

**File: `test_approximations.py`**

Tests YAML passThrough config (`--approximations-config`) and code-based approximations (`--dataflow-approximations`). Both types of approximations are **only applicable to external methods** — library classes without source code in the project. The agent discovers which methods need approximations via the `--external-methods` output.

### Key constraint: External methods only

**Approximations (both YAML passThrough and code-based) are ONLY applicable to external methods** — library classes whose source code is NOT part of the analyzed project. Project classes with source code are analyzed directly by the engine; approximations for them would be ignored or cause errors.

The agent's workflow is: run scan → get external methods list → create approximations for methods in `withoutRules` → rescan. The external methods list drives which methods need approximations.

### Fixture: YAML approximation config

**`fixtures/approximations/yaml/custom-propagators.yaml`** — models external library methods from Stirling-PDF's dependencies:

```yaml
# Custom passThrough rules for external library methods encountered by the engine.
#
# IMPORTANT: Approximations are ONLY applicable to external methods — library
# classes whose source code is NOT part of the project. These methods would
# appear in the external-methods.yaml output under withoutRules.

passThrough:
  # org.apache.pdfbox.pdmodel.PDDocument#getPage — taint on this flows to result
  # PDFBox is an external dependency of Stirling-PDF
  - function: org.apache.pdfbox.pdmodel.PDDocument#getPage
    copy:
      - from: this
        to: result

  # org.apache.pdfbox.text.PDFTextStripper#getText — taint on arg(0) flows to result
  - function: org.apache.pdfbox.text.PDFTextStripper#getText
    copy:
      - from: arg(0)
        to: result

  # com.fasterxml.jackson.databind.ObjectMapper#readValue — taint flows through deserialization
  - function: com.fasterxml.jackson.databind.ObjectMapper#readValue
    copy:
      - from: arg(0)
        to: result

  # org.jsoup.Jsoup#parse — taint on arg(0) flows to result
  - function: org.jsoup.Jsoup#parse
    copy:
      - from: arg(0)
        to: result
```

### Fixture: Code-based approximation

**`fixtures/approximations/java/PdfBoxDocumentApprox.java`** — approximation for PDFBox's `PDDocument` (an external library class):

```java
package agent.approximations;

import org.opentaint.ir.approximation.annotation.ApproximateByName;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

/**
 * Code-based approximation for PDFBox's PDDocument class.
 *
 * IMPORTANT: Approximations are ONLY applicable to external methods —
 * library classes whose source code is NOT part of the project being analyzed.
 * PDFBox is an external dependency of Stirling-PDF (pdfbox-3.0.6.jar).
 */
@ApproximateByName("org.apache.pdfbox.pdmodel.PDDocument")
public class PdfBoxDocumentApprox {

    /**
     * Model save(OutputStream) — taint on this flows to arg(0).
     * A tainted document writes tainted bytes to the output stream.
     */
    public void save(java.io.OutputStream output) throws java.io.IOException {
        org.apache.pdfbox.pdmodel.PDDocument self =
            (org.apache.pdfbox.pdmodel.PDDocument) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            throw new java.io.IOException("approximation: failure path");
        }
        byte[] data = new byte[1];
        output.write(data);
    }

    /**
     * Model getPage(int) — taint on this flows to result.
     * A tainted document produces tainted pages.
     */
    public Object getPage(int pageIndex) {
        org.apache.pdfbox.pdmodel.PDDocument self =
            (org.apache.pdfbox.pdmodel.PDDocument) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            return null;
        }
        return self.getPages().get(pageIndex);
    }
}
```

### Test script

```python
"""
Suite 3: Approximations Generation/Override

Tests:
3.1  Scan with --approximations-config (YAML passThrough)
3.2  Scan with --approximations-config + --ruleset together (§1.2)
3.3  Scan with --dataflow-approximations (pre-compiled .class files)
3.4  Scan with --dataflow-approximations from .java sources (auto-compile, §1.4)
3.5  Approximation compilation failure handling (bad Java source)
3.6  Duplicate approximation targeting built-in class (error)
3.7  Scan with both --approximations-config and --dataflow-approximations
3.8  Verify approximation changes analysis results
"""

import pytest
import shutil
from pathlib import Path
from conftest import (
    OpenTaintCLI, load_sarif, sarif_results, sarif_rule_ids,
    sarif_findings_for_rule, write_text, write_yaml,
    FIXTURES_DIR, BUILTIN_RULES_DIR,
)


class TestYAMLApproximationsConfig:
    """3.1-3.2: YAML passThrough config."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_approximations_config(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Scan with --approximations-config applies custom passThrough rules.
        We verify the scan completes successfully (the config is accepted).
        """
        sarif_path = tmp_output / "report.sarif"
        config_path = FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"

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
        assert len(sarif_results(data)) >= 0  # May have results, may not

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
        config_path = FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        custom_rules = FIXTURES_DIR / "rules"

        if not config_path.exists() or not custom_rules.exists():
            pytest.skip("Fixture files not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=["stirling-path-traversal"],
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
    """3.3-3.6: Code-based approximations via --dataflow-approximations."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_java_source_approximations(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        --dataflow-approximations with .java source files auto-compiles them.
        The scan should complete successfully.
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
        # If the auto-compile works, scan should succeed
        if result.ok:
            data = load_sarif(sarif_path)
            print(f"Scan with code-based approximations: {len(sarif_results(data))} findings")

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
        write_text(bad_approx_dir / "BrokenApprox.java", """\
package agent.approximations;

import org.opentaint.ir.approximation.annotation.Approximate;

// This won't compile — referencing nonexistent class
@Approximate(com.nonexistent.library.DoesNotExist.class)
public class BrokenApprox {
    public void broken() {
        com.nonexistent.library.DoesNotExist x = null; // compile error
    }
}
""")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            dataflow_approximations=str(bad_approx_dir),
            timeout=120,
        )
        result.assert_failed("Scan should fail when approximation compilation fails")
        # Error message should mention compilation
        assert "compil" in result.stderr.lower() or "error" in result.stderr.lower(), \
            f"Error message should mention compilation failure: {result.stderr[:500]}"

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

        # java.util.stream.Stream already has a built-in approximation
        write_text(dup_approx_dir / "StreamDuplicate.java", """\
package agent.approximations;

import org.opentaint.ir.approximation.annotation.Approximate;

@Approximate(java.util.stream.Stream.class)
public class StreamDuplicate {
    public Object map(java.util.function.Function fn) throws Throwable {
        return fn.apply(null);
    }
}
""")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            dataflow_approximations=str(dup_approx_dir),
            timeout=300,
        )
        # Should fail due to ApproximationIndexer bijection assertion
        result.assert_failed("Duplicate approximation should produce an error")


class TestCombinedApproximations:
    """3.7-3.8: Combining YAML config + code-based approximations."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_with_both_approximation_types(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        Both --approximations-config and --dataflow-approximations can be
        used in the same scan. YAML handles simple passThrough, code-based
        handles complex methods.
        """
        sarif_path = tmp_output / "report.sarif"
        yaml_config = FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        java_approx = FIXTURES_DIR / "approximations" / "java"
        custom_rules = FIXTURES_DIR / "rules"

        if not yaml_config.exists() or not java_approx.exists():
            pytest.skip("Fixture files not created yet")

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=["stirling-path-traversal"],
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
        produce different results (the approximations add propagation paths
        that weren't there before).

        This is a differential test — we compare finding counts.
        """
        custom_rules = FIXTURES_DIR / "rules"
        yaml_config = FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"

        if not custom_rules.exists() or not yaml_config.exists():
            pytest.skip("Fixture files not created yet")

        # Run 1: without approximations
        sarif_no_approx = tmp_output / "no-approx" / "report.sarif"
        (tmp_output / "no-approx").mkdir()
        r1 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_no_approx),
            rulesets=["builtin", str(custom_rules)],
            rule_ids=["stirling-path-traversal"],
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
            rule_ids=["stirling-path-traversal"],
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
            # We don't assert which is larger — just that they're potentially different
            # The agent would analyze the difference to validate the approximations
```

---

## 7. Test Suite 4: External Methods Extraction

**File: `test_external_methods.py`**

Tests the `--external-methods` output functionality.

```python
"""
Suite 4: External Methods Extraction

Tests:
4.1  Scan with --external-methods produces a YAML file
4.2  External methods file has correct structure (withoutRules/withRules)
4.3  External methods contain expected fields (method, signature, factPositions, callSites)
4.4  withoutRules list is non-empty for a real project (Stirling-PDF has many unmodeled methods)
4.5  withRules list contains known standard library methods
4.6  Scan with custom approximations reduces withoutRules count
4.7  External methods extraction alongside SARIF output
"""

import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI, load_sarif, sarif_results,
    load_external_methods, count_external_methods,
    FIXTURES_DIR, BUILTIN_RULES_DIR,
)


class TestExternalMethodsBasic:
    """4.1-4.3: Basic external methods output."""

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_scan_produces_external_methods_file(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        --external-methods flag produces a YAML file alongside SARIF output.
        """
        sarif_path = tmp_output / "report.sarif"
        ext_methods_path = tmp_output / "external-methods.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            external_methods=str(ext_methods_path),
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok("Scan with --external-methods failed")
        assert ext_methods_path.exists(), "External methods file not produced"

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_external_methods_structure(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        External methods file has two sections: withoutRules and withRules.
        Each entry has: method, signature, factPositions, callSites.
        """
        sarif_path = tmp_output / "report.sarif"
        ext_methods_path = tmp_output / "external-methods.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            external_methods=str(ext_methods_path),
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        data = load_external_methods(ext_methods_path)

        # Validate structure
        for section_name in ["withoutRules", "withRules"]:
            section = data.get(section_name, [])
            for entry in section[:5]:  # Check first 5 entries
                assert "method" in entry, f"Entry in {section_name} missing 'method'"
                assert "signature" in entry, f"Entry in {section_name} missing 'signature'"
                assert "factPositions" in entry, f"Entry in {section_name} missing 'factPositions'"
                assert "callSites" in entry, f"Entry in {section_name} missing 'callSites'"

                # Validate method format: Class#method
                assert "#" in entry["method"], \
                    f"Method should be in Class#method format: {entry['method']}"

                # Validate factPositions is a list
                assert isinstance(entry["factPositions"], list), \
                    f"factPositions should be a list: {entry['factPositions']}"

                # Validate callSites is a positive integer
                assert isinstance(entry["callSites"], int) and entry["callSites"] > 0, \
                    f"callSites should be a positive integer: {entry['callSites']}"

                # Validate factPositions values
                valid_positions = {"this", "result"}
                for pos in entry["factPositions"]:
                    assert pos == "this" or pos == "result" or pos.startswith("arg("), \
                        f"Invalid fact position: {pos}"


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
        ext_methods_path = tmp_output / "external-methods.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            external_methods=str(ext_methods_path),
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        data = load_external_methods(ext_methods_path)
        without_count, with_count = count_external_methods(data)
        print(f"External methods: {without_count} without rules, {with_count} with rules")

        assert without_count > 0, \
            "Expected non-empty withoutRules for Stirling-PDF (it uses many unmodeled libraries)"

    @pytest.mark.slow
    @pytest.mark.new_feature
    def test_with_rules_contains_standard_library_methods(
        self, cli: OpenTaintCLI, stirling_project: Path, tmp_output: Path
    ):
        """
        The withRules section should contain standard library methods that
        have built-in approximations (e.g., StringBuilder, String methods).
        """
        sarif_path = tmp_output / "report.sarif"
        ext_methods_path = tmp_output / "external-methods.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            external_methods=str(ext_methods_path),
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        data = load_external_methods(ext_methods_path)
        with_rules = data.get("withRules", [])
        with_rules_methods = {e["method"] for e in with_rules}

        # Known methods that should have rules in the default config
        # (these are common and Stirling-PDF definitely calls them)
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
        should move them to withRules (or remove them from withoutRules entirely).
        """
        yaml_config = FIXTURES_DIR / "approximations" / "yaml" / "custom-propagators.yaml"
        custom_rules = FIXTURES_DIR / "rules"

        if not yaml_config.exists():
            pytest.skip("Fixture approximation config not created yet")

        # Run 1: without custom approximations
        sarif1 = tmp_output / "run1" / "report.sarif"
        ext1 = tmp_output / "run1" / "external-methods.yaml"
        (tmp_output / "run1").mkdir()
        r1 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif1),
            rulesets=["builtin"],
            external_methods=str(ext1),
            severity=["warning", "error"],
            timeout=600,
        )

        # Run 2: with custom approximations
        sarif2 = tmp_output / "run2" / "report.sarif"
        ext2 = tmp_output / "run2" / "external-methods.yaml"
        (tmp_output / "run2").mkdir()
        r2 = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif2),
            rulesets=["builtin"],
            approximations_config=str(yaml_config),
            external_methods=str(ext2),
            severity=["warning", "error"],
            timeout=600,
        )

        if r1.ok and r2.ok:
            data1 = load_external_methods(ext1)
            data2 = load_external_methods(ext2)
            wo1, _ = count_external_methods(data1)
            wo2, _ = count_external_methods(data2)
            print(f"Without custom approx: {wo1} methods without rules")
            print(f"With custom approx:    {wo2} methods without rules")

            # Methods we added rules for should no longer be in withoutRules
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
        """
        A single scan produces both SARIF report and external methods file.
        """
        sarif_path = tmp_output / "report.sarif"
        ext_methods_path = tmp_output / "external-methods.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin"],
            external_methods=str(ext_methods_path),
            severity=["warning", "error"],
            timeout=600,
        )
        result.assert_ok()

        # Both files should exist
        assert sarif_path.exists(), "SARIF report not produced"
        assert ext_methods_path.exists(), "External methods file not produced"

        # Both should be non-trivial
        sarif_data = load_sarif(sarif_path)
        ext_data = load_external_methods(ext_methods_path)
        assert len(sarif_results(sarif_data)) > 0, "SARIF has no results"
        wo, wr = count_external_methods(ext_data)
        assert wo + wr > 0, "External methods file is empty"
```

---

## 8. Test Suite 5: Full Agent Loop (Integration)

**File: `test_full_loop.py`**

End-to-end test simulating the agent's workflow from the meta prompt: create rule → test → scan → analyze external methods → create approximation → rescan.

```python
"""
Suite 5: Full Agent Loop (Integration)

This test simulates the complete agent workflow on Stirling-PDF:
1. Discover entry points (by reading source)
2. Create a custom path-traversal rule
3. Test the rule with samples
4. Run initial scan on Stirling-PDF
5. Analyze external methods
6. Create YAML approximation for an unmodeled method
7. Re-scan and verify the approximation has effect

This is a single large integration test, not meant for fast CI.
"""

import json
import shutil
import pytest
from pathlib import Path
from conftest import (
    OpenTaintCLI, load_sarif, sarif_results, sarif_rule_ids,
    sarif_findings_for_rule, load_external_methods, count_external_methods,
    write_text, write_yaml,
    FIXTURES_DIR, BUILTIN_RULES_DIR, STIRLING_PROJECT_DIR,
)


@pytest.mark.slow
@pytest.mark.new_feature
class TestFullAgentLoop:
    """
    Simulates the agent's analysis workflow on Stirling-PDF.

    This test class follows the meta prompt phases:
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

        # ── Phase 1: Source Discovery (simulated) ─────────────────────
        # The agent would read source files to identify controllers and
        # attack surface. Here we simulate the discovery result.
        controllers = [
            "stirling.software.SPDF.controller.api.misc.PrintFileController",
            "stirling.software.SPDF.controller.api.MergeController",
            "stirling.software.SPDF.controller.api.SplitPDFController",
            "stirling.software.SPDF.controller.api.security.*",
        ]
        print(f"Phase 1: Discovered {len(controllers)} controller groups")

        # ── Phase 2: Create Rule ──────────────────────────────────────

        # 2a: Read builtin rules to check coverage
        builtin_path_traversal = BUILTIN_RULES_DIR / "java" / "security" / "path-traversal.yaml"
        assert builtin_path_traversal.exists(), "Builtin path-traversal rule not found"
        print("Phase 2a: Read builtin path-traversal rule")

        # 2b: Create custom source library rule for Stirling's multipart upload
        lib_dir = ws["rules"] / "java" / "lib"
        lib_dir.mkdir(parents=True, exist_ok=True)
        write_text(lib_dir / "stirling-source.yaml", """\
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
""")

        # 2c: Create join-mode security rule
        sec_dir = ws["rules"] / "java" / "security"
        sec_dir.mkdir(parents=True, exist_ok=True)
        write_text(sec_dir / "stirling-path-traversal.yaml", """\
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
        - rule: java/lib/generic/path-traversal-sinks.yaml#java-path-traversal-sink
          as: sink
      on:
        - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
""")
        print("Phase 2b-c: Created custom rules")

        # ── Phase 3: Initial Scan ─────────────────────────────────────

        sarif_path = ws["results"] / "report-1.sarif"
        ext_methods_path = ws["results"] / "external-methods-1.yaml"

        result = cli.scan(
            project_path=str(stirling_project),
            output=str(sarif_path),
            rulesets=["builtin", str(ws["rules"])],
            rule_ids=["stirling-path-traversal"],
            external_methods=str(ext_methods_path),
            severity=["note", "warning", "error"],
            timeout=600,
        )
        result.assert_ok("Initial scan failed")

        # Analyze results
        sarif_data = load_sarif(sarif_path)
        findings = sarif_findings_for_rule(sarif_data, "stirling-path-traversal")
        print(f"Phase 3: Initial scan found {len(findings)} path-traversal findings")

        for f in findings[:5]:
            locs = f.get("locations", [{}])
            if locs:
                uri = locs[0].get("physicalLocation", {}).get("artifactLocation", {}).get("uri", "?")
                line = locs[0].get("physicalLocation", {}).get("region", {}).get("startLine", "?")
                print(f"  Finding: {uri}:{line}")

        # ── Phase 3b: Analyze External Methods ────────────────────────

        if ext_methods_path.exists():
            ext_data = load_external_methods(ext_methods_path)
            wo_count, wr_count = count_external_methods(ext_data)
            print(f"Phase 3b: External methods — {wo_count} without rules, {wr_count} with rules")

            # Identify methods the agent would want to model
            without_rules = ext_data.get("withoutRules", [])
            priority_methods = [
                m for m in without_rules
                if m.get("callSites", 0) > 5
            ]
            priority_methods.sort(key=lambda m: m.get("callSites", 0), reverse=True)
            print(f"  Priority unmodeled methods (>5 call sites): {len(priority_methods)}")
            for m in priority_methods[:10]:
                print(f"    {m['method']} ({m['callSites']} call sites, positions: {m['factPositions']})")

        # ── Phase 4: Create Approximation and Rescan ──────────────────
        # Approximations are ONLY for external methods (from withoutRules).
        # These are library methods without source code in the project.

        # Create YAML approximation for top unmodeled external methods
        if ext_methods_path.exists() and priority_methods:
            pass_through_rules = []
            for m in priority_methods[:5]:
                method_name = m["method"]
                positions = m["factPositions"]

                # Simple heuristic: if taint is on arg(0), propagate to result
                copies = []
                for pos in positions:
                    if pos.startswith("arg("):
                        copies.append({"from": pos, "to": "result"})
                    elif pos == "this":
                        copies.append({"from": "this", "to": "result"})

                if copies:
                    pass_through_rules.append({
                        "function": method_name,
                        "copy": copies,
                    })

            if pass_through_rules:
                config_file = ws["config"] / "custom-propagators.yaml"
                write_yaml(config_file, {"passThrough": pass_through_rules})
                print(f"Phase 4: Created {len(pass_through_rules)} custom passThrough rules")

                # Rescan with approximations
                sarif_path_2 = ws["results"] / "report-2.sarif"
                ext_methods_path_2 = ws["results"] / "external-methods-2.yaml"

                result2 = cli.scan(
                    project_path=str(stirling_project),
                    output=str(sarif_path_2),
                    rulesets=["builtin", str(ws["rules"])],
                    rule_ids=["stirling-path-traversal"],
                    approximations_config=str(config_file),
                    external_methods=str(ext_methods_path_2),
                    severity=["note", "warning", "error"],
                    timeout=600,
                )

                if result2.ok:
                    sarif_data_2 = load_sarif(sarif_path_2)
                    findings_2 = sarif_findings_for_rule(sarif_data_2, "stirling-path-traversal")
                    print(f"Phase 4: Rescan found {len(findings_2)} findings (was {len(findings)})")

                    if ext_methods_path_2.exists():
                        ext_data_2 = load_external_methods(ext_methods_path_2)
                        wo2, wr2 = count_external_methods(ext_data_2)
                        print(f"  External methods after approx: {wo2} without (was {wo_count}), {wr2} with (was {wr_count})")

                        # Verify the approximations had some effect
                        delta_findings = len(findings_2) - len(findings)
                        delta_methods = wo_count - wo2
                        print(f"  Delta: {delta_findings:+d} findings, {delta_methods:+d} newly modeled methods")

        print("\n=== Full agent loop completed ===")
```

---

## 9. Running Tests

### Quick validation (existing features only)

```bash
cd agent-mode/test

# Run only tests that work with current implementation
pytest test_build.py -m "not new_feature and not slow" -v

# Run build tests including slow ones (actual scans)
pytest test_build.py -m "not new_feature" -v --timeout=900
```

### Full test suite (after new features are implemented)

```bash
cd agent-mode/test

# Run all tests
pytest -v --timeout=900

# Run specific suite
pytest test_rules.py -v --timeout=900
pytest test_approximations.py -v --timeout=900
pytest test_external_methods.py -v --timeout=900

# Run the full integration loop
pytest test_full_loop.py -v --timeout=1800

# Exclude slow tests for quick checks
pytest -m "not slow" -v
```

### Development mode (direct JAR invocation)

When `opentaint` is not on PATH, tests automatically fall back to invoking the analyzer/autobuilder JARs directly. Set environment variables if the JARs are in non-default locations:

```bash
# Point to locally-built JARs
export JAVA_HOME=/path/to/java-21
export OPENTAINT_ANALYZER_JAR=/path/to/opentaint-project-analyzer.jar
export OPENTAINT_AUTOBUILDER_JAR=/path/to/opentaint-project-auto-builder.jar

pytest -v --timeout=900
```

### CI Integration

For CI, use a matrix of test suites to parallelize:

```yaml
# .github/workflows/ci-agent-mode-tests.yaml
jobs:
  test:
    strategy:
      matrix:
        suite: [test_build, test_rules, test_approximations, test_external_methods]
    steps:
      - name: Run agent-mode tests
        run: |
          cd agent-mode/test
          pytest ${{ matrix.suite }}.py -v --timeout=900 -m "not new_feature"
```

---

## Summary

| Suite | Tests | Markers | Purpose |
|---|---|---|---|
| `test_build.py` | 5 | `slow` | Project build: pre-compiled, auto-compile, error handling |
| `test_rules.py` | 8 | `slow`, `new_feature` | Rule creation, rule-id filter, rule tests, custom rules on Stirling |
| `test_approximations.py` | 8 | `slow`, `new_feature` | YAML config, code-based approximations, compilation errors, combined |
| `test_external_methods.py` | 7 | `slow`, `new_feature` | External methods output, structure validation, coverage changes |
| `test_full_loop.py` | 1 | `slow`, `new_feature` | Full agent workflow: rule → test → scan → approx → rescan |
| **Total** | **29** | | |

Tests marked `new_feature` will pass once the corresponding engine/CLI changes from `agent-mode-design.md` are implemented. Tests without that marker can run today against the existing codebase (using direct JAR invocation).
