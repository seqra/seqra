"""Run with: uv run --project skills-templates --with pyyaml python -m unittest discover -s skills-templates/tests."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


REPO = Path(__file__).resolve().parents[2]


class GoDiscoveryTest(unittest.TestCase):
    def test_regenerated_skills_and_go_plans(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            generated = root / "skills"
            subprocess.run([sys.executable, str(REPO / "skills-templates/build.py"),
                            "--out", str(generated)], check=True, capture_output=True)
            actual = {p.relative_to(generated): p.read_bytes()
                      for p in generated.rglob("*") if p.is_file()}
            expected = {p.relative_to(REPO / "skills"): p.read_bytes()
                        for p in (REPO / "skills").rglob("*")
                        if p.is_file() and "__pycache__" not in p.parts}
            self.assertEqual(actual, expected, "checked-in skills differ from templates")

            project = root / "project"
            files = {
                "go.mod": "module example.com/app\n\ngo 1.25.0\n",
                "lib/lib.go": "package lib\nfunc Direct(string) {}\n"
                              "func Assigned(string) {}\nfunc Unused(string) {}\n"
                              "type Worker struct{}\nfunc (*Worker) Bound(string) {}\n",
                "app.go": 'package app\nimport "example.com/app/lib"\n'
                          'func use(w *lib.Worker) {\n lib.Direct("value")\n'
                          ' get := lib.Assigned\n get("value")\n'
                          ' bound := w.Bound\n bound("value")\n}\n',
                ".opentaint/tracking/state.yaml": "language: go\n",
                ".opentaint/tracking/coverage.yaml": "packages: [example.com/app/lib]\n",
                ".opentaint/tracking/rules/classification.yaml":
                    "safe: [example.com/app/lib.Directargs:1]\n",
                ".opentaint/project/project.yaml":
                    f"projectRoot: {project}\ngoProjects:\n  - projectDir: .\n",
            }
            for name, content in files.items():
                path = project / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            for skill in ("appsec-agent", "orchestrate-stage"):
                subprocess.run([sys.executable, str(generated / skill / "scripts/generate.py"),
                                "partition", "discover"], cwd=project, check=True,
                               capture_output=True, text=True)
                plans = list((project / ".opentaint/tracking/rules/plans").glob("*.yaml"))
                self.assertEqual(len(plans), 1)
                plan = yaml.safe_load(plans[0].read_text())
                self.assertIsNone(plan["source"])
                rows = [row for scope in plan["scopes"].values() for row in scope]
                self.assertEqual(rows, [
                    {"method": "(*example.com/app/lib.Worker).Bound", "signature": "args:1"},
                    {"method": "example.com/app/lib.Assigned", "signature": "args:1"},
                ])


if __name__ == "__main__":
    unittest.main()
