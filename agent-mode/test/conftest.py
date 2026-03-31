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
    # Check environment variable first
    env_jar = os.environ.get("OPENTAINT_ANALYZER_JAR")
    if env_jar:
        p = Path(env_jar)
        if p.exists():
            return p

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
    env_jar = os.environ.get("OPENTAINT_AUTOBUILDER_JAR")
    if env_jar:
        p = Path(env_jar)
        if p.exists():
            return p

    candidates = [
        OPENTAINT_ROOT
        / "autobuilder"
        / "build"
        / "libs"
        / "opentaint-project-auto-builder.jar",
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
    command: list

    @property
    def ok(self) -> bool:
        return self.returncode == 0

    def assert_ok(self, msg: str = ""):
        assert self.ok, (
            f"Command failed (rc={self.returncode}){': ' + msg if msg else ''}\n"
            f"  cmd: {' '.join(str(c) for c in self.command)}\n"
            f"  stderr: {self.stderr[:2000]}"
        )

    def assert_failed(self, msg: str = ""):
        assert not self.ok, (
            f"Command unexpectedly succeeded{': ' + msg if msg else ''}\n"
            f"  cmd: {' '.join(str(c) for c in self.command)}\n"
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

    def run(
        self, args: list, timeout: Optional[int] = None, env: Optional[dict] = None
    ) -> CLIResult:
        """Run an arbitrary command and return the result."""
        str_args = [str(a) for a in args]
        run_env = {**os.environ, **(env or {})}
        t = timeout or self.timeout
        try:
            proc = subprocess.run(
                str_args,
                capture_output=True,
                text=True,
                timeout=t,
                env=run_env,
            )
            return CLIResult(proc.returncode, proc.stdout, proc.stderr, str_args)
        except subprocess.TimeoutExpired:
            return CLIResult(-1, "", f"Timeout after {t}s", str_args)

    def scan(
        self,
        project_path: str,
        output: str,
        rulesets: list = None,
        rule_ids: list = None,
        approximations_config: Optional[str] = None,
        dataflow_approximations: Optional[str] = None,
        external_methods: Optional[str] = None,
        severity: list = None,
        timeout: int = 900,
        max_memory: str = "8G",
        extra_flags: list = None,
    ) -> CLIResult:
        """Run opentaint scan (or direct analyzer JAR invocation)."""

        if self.has_cli:
            cmd = [self.cli_path, "scan", project_path, "-o", output]
            for rs in rulesets or ["builtin"]:
                cmd.extend(["--ruleset", rs])
            for rid in rule_ids or []:
                cmd.extend(["--rule-id", rid])
            if approximations_config:
                cmd.extend(["--approximations-config", approximations_config])
            if dataflow_approximations:
                cmd.extend(["--dataflow-approximations", dataflow_approximations])
            if external_methods:
                cmd.extend(["--external-methods", external_methods])
            for sev in severity or ["warning", "error"]:
                cmd.extend(["--severity", sev])
            cmd.extend(["--timeout", f"{timeout}s", "--max-memory", max_memory])
            cmd.extend(extra_flags or [])
            return self.run(cmd, timeout=timeout + 60)

        # Direct JAR invocation
        assert self.analyzer_jar, "No analyzer JAR found"
        output_dir = str(Path(output).parent)
        sarif_name = Path(output).name
        cmd = [
            self.java_path,
            f"-Xmx{max_memory}",
            "-Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000",
            "-Djdk.util.jar.enableMultiRelease=false",
            "-jar",
            str(self.analyzer_jar),
            "--project",
            project_path,
            "--output-dir",
            output_dir,
            "--sarif-file-name",
            sarif_name,
            f"--ifds-analysis-timeout={timeout}",
            "--verbosity=info",
        ]
        for rs in rulesets or []:
            if rs == "builtin":
                cmd.extend(["--semgrep-rule-set", str(BUILTIN_RULES_DIR)])
            else:
                cmd.extend(["--semgrep-rule-set", rs])
        for rid in rule_ids or []:
            cmd.extend(["--semgrep-rule-id", rid])
        if approximations_config:
            cmd.extend(["--config", approximations_config])
        if external_methods:
            cmd.extend(["--external-methods-output", external_methods])
        for sev in severity or ["warning", "error"]:
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
        rulesets: list,
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
            self.java_path,
            f"-Xmx{max_memory}",
            "-Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000",
            "-Djdk.util.jar.enableMultiRelease=false",
            "-jar",
            str(self.analyzer_jar),
            "--project",
            project_path,
            "--output-dir",
            output_dir,
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
            self.java_path,
            "-Xmx1G",
            "-jar",
            str(self.autobuilder_jar),
            "--project-root-dir",
            project_path,
            "--result-dir",
            output_dir,
            "--build",
            "portable",
            "--verbosity=info",
        ]
        return self.run(cmd, timeout=timeout + 60)

    def rules_path(self) -> CLIResult:
        """Run opentaint rules-path."""
        if self.has_cli:
            return self.run([self.cli_path, "rules-path"])
        # Fall back to known builtin path
        return CLIResult(
            0, str(BUILTIN_RULES_DIR), "", ["echo", str(BUILTIN_RULES_DIR)]
        )

    def init_test_project(
        self,
        output_dir: str,
        dependencies: list = None,
    ) -> CLIResult:
        """Run opentaint init-test-project."""
        if self.has_cli:
            cmd = [self.cli_path, "init-test-project", output_dir]
            for dep in dependencies or []:
                cmd.extend(["--dependency", dep])
            return self.run(cmd)
        # Fallback: not available without Go CLI
        return CLIResult(
            1, "", "init-test-project not available in direct JAR mode", []
        )


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
    assert STIRLING_PROJECT.exists(), (
        f"Stirling-PDF project not found at {STIRLING_PROJECT}"
    )
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


def sarif_results(data: dict) -> list:
    """Extract results from a SARIF report."""
    return data["runs"][0].get("results", [])


def sarif_rule_ids(data: dict) -> set:
    """Extract unique rule IDs from SARIF results."""
    return {r["ruleId"] for r in sarif_results(data)}


def sarif_findings_for_rule(data: dict, rule_id: str) -> list:
    """Get findings for a specific rule ID."""
    return [r for r in sarif_results(data) if r["ruleId"] == rule_id]


def load_external_methods(path: Path) -> dict:
    """Load and validate an external methods YAML file."""
    assert path.exists(), f"External methods file not found: {path}"
    with open(path) as f:
        data = yaml.safe_load(f)
    assert isinstance(data, dict), "External methods file must be a YAML mapping"
    assert "withoutRules" in data or "withRules" in data, (
        "Missing withoutRules/withRules sections"
    )
    return data


def count_external_methods(data: dict) -> tuple:
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
