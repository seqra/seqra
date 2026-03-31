"""
Shared fixtures and helpers for agent-mode tests.

All tests use the Go CLI binary (`opentaint`). In development mode, the binary
is located at `cli/bin/opentaint` relative to the repo root, and hidden
`--analyzer-jar` / `--autobuilder-jar` flags are passed automatically to point
at locally-built JARs.
"""

import json
import os
import shutil
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import pytest
import yaml


# ─── Timing ──────────────────────────────────────────────────────────────────


@pytest.hookimpl(tryfirst=True)
def pytest_runtest_setup(item):
    """Record start time before each test."""
    item._start_time = time.time()


@pytest.hookimpl(trylast=True)
def pytest_runtest_teardown(item, nextitem):
    """Print elapsed time after each test."""
    start = getattr(item, "_start_time", None)
    if start is not None:
        elapsed = time.time() - start
        print(f"\n  [timing] {item.nodeid}: {elapsed:.1f}s")


# ─── Paths ───────────────────────────────────────────────────────────────────

STIRLING_PROJECT = Path("/home/sobol/data/Stirling-PDF/seqra-project/project.yaml")
STIRLING_PROJECT_DIR = STIRLING_PROJECT.parent
OPENTAINT_ROOT = Path(__file__).resolve().parent.parent.parent  # -> opentaint/
FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"
BUILTIN_RULES_DIR = OPENTAINT_ROOT / "rules" / "ruleset"


# ─── CLI Resolution ──────────────────────────────────────────────────────────


def _find_cli_binary() -> str:
    """
    Find the opentaint CLI binary. Resolution order:
    1. OPENTAINT_CLI env var
    2. Local dev build at cli/bin/opentaint
    3. opentaint on PATH
    """
    env_cli = os.environ.get("OPENTAINT_CLI")
    if env_cli:
        p = Path(env_cli)
        if p.exists():
            return str(p)

    dev_binary = OPENTAINT_ROOT / "cli" / "bin" / "opentaint"
    if dev_binary.exists():
        return str(dev_binary)

    on_path = shutil.which("opentaint")
    if on_path:
        return on_path

    pytest.exit(
        "opentaint CLI binary not found. Build it with: cd cli && go build -o ./bin/opentaint .",
        returncode=1,
    )


def _find_local_jar(env_var: str, candidates: list) -> Optional[str]:
    """Find a locally-built JAR by env var or candidate paths."""
    env_jar = os.environ.get(env_var)
    if env_jar:
        p = Path(env_jar)
        if p.exists():
            return str(p)

    for c in candidates:
        if c.exists():
            return str(c)
    return None


def _find_analyzer_jar() -> Optional[str]:
    """Find locally-built analyzer JAR for --analyzer-jar hidden flag."""
    return _find_local_jar(
        "OPENTAINT_ANALYZER_JAR",
        [
            OPENTAINT_ROOT
            / "core"
            / "build"
            / "libs"
            / "opentaint-project-analyzer.jar",
        ],
    )


def _find_autobuilder_jar() -> Optional[str]:
    """Find locally-built autobuilder JAR for --autobuilder-jar hidden flag."""
    return _find_local_jar(
        "OPENTAINT_AUTOBUILDER_JAR",
        [
            OPENTAINT_ROOT
            / "autobuilder"
            / "build"
            / "libs"
            / "opentaint-project-auto-builder.jar",
        ],
    )


# ─── CLI Abstraction ─────────────────────────────────────────────────────────


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
    Abstraction over the opentaint Go CLI binary.

    All commands go through the CLI. In dev mode, hidden --analyzer-jar and
    --autobuilder-jar flags are passed to point at locally-built JARs.
    """

    cli_path: str = ""
    analyzer_jar: Optional[str] = None
    autobuilder_jar: Optional[str] = None
    timeout: int = 600  # seconds

    def _base_cmd(self) -> list:
        """Return the base command with hidden JAR flags if set."""
        cmd = [self.cli_path]
        if self.analyzer_jar:
            cmd.extend(["--analyzer-jar", self.analyzer_jar])
        if self.autobuilder_jar:
            cmd.extend(["--autobuilder-jar", self.autobuilder_jar])
        return cmd

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
        """Run opentaint scan.

        The CLI expects a directory path (it looks for project.yaml inside).
        If project_path points to a project.yaml file, the parent directory is used.
        """
        # CLI expects directory, not project.yaml file path
        p = Path(project_path)
        if p.name == "project.yaml" and p.is_file():
            project_path = str(p.parent)
        cmd = self._base_cmd() + ["scan", project_path, "-o", output]
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

    def test_rules(
        self,
        project_path: str,
        rulesets: list,
        output_dir: str,
        timeout: int = 300,
        max_memory: str = "8G",
    ) -> CLIResult:
        """Run opentaint agent test-rules.

        The CLI expects a directory path (it looks for project.yaml inside).
        If project_path points to a project.yaml file, the parent directory is used.
        """
        p = Path(project_path)
        if p.name == "project.yaml" and p.is_file():
            project_path = str(p.parent)
        cmd = self._base_cmd() + ["agent", "test-rules", project_path]
        for rs in rulesets:
            cmd.extend(["--ruleset", rs])
        cmd.extend(["-o", output_dir])
        cmd.extend(["--timeout", f"{timeout}s", "--max-memory", max_memory])
        return self.run(cmd, timeout=timeout + 60)

    def compile(
        self,
        project_path: str,
        output_dir: str,
        timeout: int = 300,
    ) -> CLIResult:
        """Run opentaint compile."""
        cmd = self._base_cmd() + ["compile", project_path, "-o", output_dir]
        return self.run(cmd, timeout=timeout + 60)

    def rules_path(self) -> CLIResult:
        """Run opentaint agent rules-path."""
        return self.run(self._base_cmd() + ["agent", "rules-path"])

    def init_test_project(
        self,
        output_dir: str,
        dependencies: list = None,
    ) -> CLIResult:
        """Run opentaint agent init-test-project."""
        cmd = self._base_cmd() + ["agent", "init-test-project", output_dir]
        for dep in dependencies or []:
            cmd.extend(["--dependency", dep])
        return self.run(cmd)


# ─── Fixtures ─────────────────────────────────────────────────────────────────


@pytest.fixture(scope="session")
def cli() -> OpenTaintCLI:
    """Provide an OpenTaintCLI instance configured for the current environment."""
    return OpenTaintCLI(
        cli_path=_find_cli_binary(),
        analyzer_jar=_find_analyzer_jar(),
        autobuilder_jar=_find_autobuilder_jar(),
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
