import json
from pathlib import Path
import tempfile
import unittest

from compare_bidi_projects import compare_project, log_evidence, normalize_uri


def finding(fingerprint="hash", signature="pkg.Service#f(java.lang.String)", index=12,
            path="module/src/main/java/pkg/Service.java", line=20, flows=0, rule="rules/sql"):
    result = {"ruleId": rule, "locations": [{
        "logicalLocations": [{"fullyQualifiedName": signature.split("(")[0],
                              "decoratedName": f"{signature}:{index}:(sink(value))"}],
        "physicalLocation": {"artifactLocation": {"uri": path, "uriBaseId": "%SRCROOT%"},
                             "region": {"startLine": line, "endLine": line, "startColumn": 3}},
    }], "codeFlows": [{} for _ in range(flows)]}
    if fingerprint is not None:
        result["partialFingerprints"] = {"vulnerabilitySinkHash/v1": fingerprint}
    return result


class CompareProjectsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def bundle(self, side, results=None, status="OK", log="", manifest=None):
        directory = self.root / side
        directory.mkdir(exist_ok=True)
        (directory / "summary.json").write_text(json.dumps({"status": status}))
        (directory / "manifest.json").write_text(json.dumps(manifest or {
            "model_sha256": "same-model", "test_revision": "test", "config": {"memory": "8g", "timeout": 1000}}))
        (directory / "analyzer.log").write_text(log)
        if results is not None:
            (directory / "reports").mkdir(exist_ok=True)
            (directory / "reports/report-ifds.sarif").write_text(json.dumps({"version": "2.1.0", "runs": [{
                "tool": {"driver": {"name": "test"}}, "results": results,
                "originalUriBaseIds": {"%SRCROOT%": {"uri": "/checkout/sources/"}},
            }]}))
        return directory

    def compare(self):
        return compare_project("project", self.root / "main", self.root / "bidi")

    def test_trace_and_span_changes_preserve_the_same_sink(self):
        # A real prior conductor sink had this class-URI versus Java-span difference.
        self.bundle("main", [finding(path="pkg/Service", line=21, flows=4)])
        self.bundle("bidi", [finding(line=20)])
        result = self.compare()
        self.assertEqual("ALL_MAIN_SINK_IDENTITIES_PRESENT", result["assessment"])
        self.assertEqual(0, result["counts"]["removed_unique"])
        self.assertEqual(1, len(result["equal_identity_location_changes"]))
        change = result["equal_identity_location_changes"][0]
        self.assertEqual(21, change["main_locations"][0]["location"]["physicalLocation"]["region"]["startLine"])

    def test_overloads_do_not_collapse_even_with_a_colliding_sink_hash(self):
        self.bundle("main", [finding(signature="pkg.Service#f(java.lang.String)"),
                             finding(signature="pkg.Service#f(java.lang.Object)")])
        self.bundle("bidi", [finding(signature="pkg.Service#f(java.lang.String)")])
        result = self.compare()
        self.assertEqual(2, result["counts"]["main_unique"])
        self.assertEqual(1, result["counts"]["removed_unique"])
        self.assertEqual("pkg.Service#f(java.lang.Object)", result["removed"][0]["findings"][0]["method_signature"])

    def test_duplicate_count_reduction_is_not_a_lost_sink(self):
        self.bundle("main", [finding(flows=4), finding(flows=2)])
        self.bundle("bidi", [finding()])
        result = self.compare()
        self.assertEqual("ALL_MAIN_SINK_IDENTITIES_PRESENT", result["assessment"])
        self.assertEqual(0, result["counts"]["removed_unique"])
        self.assertEqual(1, result["counts"]["removed_occurrences"])
        self.assertEqual(2, result["multiplicity_changes"][0]["main_count"])
        self.assertEqual(1, result["removed_occurrence_details"][0]["occurrences"])
        self.assertEqual([], result["removed"])

    def test_full_rule_and_package_paths_are_retained_for_physical_fallbacks(self):
        a = finding(fingerprint=None, path="a/src/pkg/Same.java", rule="a/same")
        b = finding(fingerprint=None, path="b/src/pkg/Same.java", rule="b/same")
        a["locations"][0].pop("logicalLocations")
        b["locations"][0].pop("logicalLocations")
        self.bundle("main", [a])
        self.bundle("bidi", [b])
        result = self.compare()
        self.assertEqual(1, result["counts"]["removed_unique"])
        self.assertEqual("a/same", result["removed"][0]["rule_id"])
        self.assertEqual("a/src/pkg/Same.java", result["removed"][0]["path"])

    def test_missing_sarif_is_not_a_valid_zero_result(self):
        self.bundle("main", [])
        self.bundle("bidi")
        result = self.compare()
        self.assertEqual("present", result["main"]["sarif_state"])
        self.assertEqual(0, result["main"]["result_count"])
        self.assertEqual("missing", result["bidi"]["sarif_state"])
        self.assertEqual("OUTPUT_UNAVAILABLE", result["assessment"])
        self.assertFalse(result["both_complete_without_prescan_error"])
        self.assertIsNone(result["counts"]["bidi_unique"])
        self.assertIsNone(result["counts"]["removed_unique"])
        self.assertEqual([], result["removed"])

    def test_empty_runs_and_invalid_json_are_not_valid_zero_results(self):
        self.bundle("main", [])
        directory = self.bundle("bidi", [])
        file = directory / "reports/report-ifds.sarif"
        file.write_text('{"runs": []}')
        self.assertEqual("empty_runs", self.compare()["bidi"]["sarif_state"])
        file.write_text('{"runs":')
        self.assertEqual("invalid", self.compare()["bidi"]["sarif_state"])
        file.write_text('{"runs": [{"results": {}}]}')
        self.assertEqual("invalid", self.compare()["bidi"]["sarif_state"])

    def test_masked_prescan_error_blocks_success_even_with_no_removed_sink(self):
        self.bundle("main", [])
        self.bundle("bidi", [], log="07:00:00.000 Start prescan phase\n"
                    "07:00:00.500 Prescan failed java.lang.IllegalStateException\n"
                    "07:00:01.000 Finish prescan phase\n"
                    "07:00:01.010 BIDI_RUN_START run=1 direction=FORWARD limit=1 timeout=20s\n"
                    "07:00:01.050 BIDI_RUN_END run=1 status=COMPLETE engineTime=40ms\n")
        result = self.compare()
        self.assertTrue(result["bidi"]["status"]["masked_prescan_error"])
        self.assertTrue(result["bidi"]["status"]["bidi_started_after_prescan_error"])
        self.assertEqual("INCOMPLETE_OR_INVALID_INPUT_COMPARISON", result["assessment"])

    def test_partial_sarif_and_empty_runner_marker_do_not_prove_completion(self):
        self.bundle("main", [finding()], status="TIMEOUT", log="All runners are empty\nIfds analysis timeout\n")
        self.bundle("bidi", [finding()], status="OOM", log="All runners are empty\n"
                    "BIDI_RUN_END run=1 status=OOM engineTime=2m 1.25s\n")
        result = self.compare()
        self.assertEqual(0, result["counts"]["removed_unique"])
        self.assertEqual("INCOMPLETE_OR_INVALID_INPUT_COMPARISON", result["assessment"])
        self.assertEqual(121.25, result["bidi"]["status"]["iterations"][0]["engine_seconds"])

    def test_iteration_failure_conflicting_with_summary_ok_is_visible(self):
        self.bundle("main", [])
        self.bundle("bidi", [], log="BIDI_RUN_END run=3 status=TIMEOUT engineTime=2s\n")
        result = self.compare()
        self.assertFalse(result["both_complete_without_prescan_error"])
        self.assertTrue(any("conflicts" in issue for issue in result["bidi"]["issues"]))

    def test_changed_instruction_indices_are_candidates_not_silent_matches(self):
        self.bundle("main", [finding(fingerprint="old", index=12)])
        self.bundle("bidi", [finding(fingerprint="new", index=13)])
        result = self.compare()
        self.assertEqual(1, result["counts"]["removed_unique"])
        self.assertEqual(1, len(result["possible_same_logical_sink"]))

    def test_changed_model_is_not_a_controlled_comparison(self):
        self.bundle("main", [])
        self.bundle("bidi", [], manifest={"model_sha256": "other"})
        result = self.compare()
        self.assertEqual("model_sha256", result["input_differences"][0]["field"])
        self.assertFalse(result["both_complete_without_prescan_error"])

    def test_phase_timing_handles_midnight_and_prescan_oom(self):
        result = log_evidence("23:59:59.500 Start prescan phase\n"
                              "00:00:00.000 Running low on memory, stopping analysis\n"
                              "00:00:01.000 Finish prescan phase\n", {"status": "OK"})
        self.assertEqual(1.5, result["phases"]["prescan"][0]["seconds"])
        self.assertTrue(result["masked_prescan_error"])

    def test_uri_normalization_removes_only_a_known_source_root(self):
        bases = {"root": {"uri": "file:///checkout/sources/"}}
        self.assertEqual("module/src/pkg/A.java", normalize_uri("file:///checkout/sources/module/src/pkg/A.java", bases, "root"))
        self.assertEqual("pkg/A", normalize_uri("pkg/A", bases, "root"))

    def test_missing_manifest_does_not_prove_fixed_input_equality(self):
        self.bundle("main", [])
        directory = self.bundle("bidi", [])
        (directory / "manifest.json").unlink()
        self.assertFalse(self.compare()["both_complete_without_prescan_error"])

    def test_barrier_failure_is_visible_after_a_complete_engine_run(self):
        self.bundle("main", [])
        self.bundle("bidi", [], log="BIDI_RUN_END run=1 status=COMPLETE engineTime=2s\n"
                    "BIDI_ANALYSIS_END run=1 status=OOM reason=ABNORMAL elapsed=3s\n")
        result = self.compare()
        self.assertFalse(result["both_complete_without_prescan_error"])
        self.assertEqual("OOM", result["bidi"]["status"]["analysis_ends"][0]["status"])

    def test_rejected_location_explains_report_and_sarif_count_difference(self):
        self.bundle("main", [finding()])
        self.bundle("bidi", [finding()], log="Bidi analysis: 1 CONFIRMED, 1 DEMAND vulnerabilities\n"
                    "Invalid vulnerability location: virtual method sink\n"
                    "TraceGenerationStats(total=2, simple=2, generatedSuccess=0, generationFailed=0)\n")
        result = self.compare()
        self.assertEqual(1, result["bidi"]["unexported_result_count"])
        self.assertEqual(2, result["bidi"]["sarif_generation_attempts"])
        self.assertEqual(1, result["bidi"]["status"]["reported_states"][0]["demand"])
        self.assertTrue(result["bidi"]["status"]["invalid_vulnerability_locations"])

    def test_different_returns_are_explicit_candidates_and_still_strictly_removed(self):
        a = finding(fingerprint="earlier-return", index=50)
        b = finding(fingerprint="last-return", index=109)
        a["locations"][0]["logicalLocations"][0]["decoratedName"] = "pkg.Service#f(java.lang.String):50:(return %104)"
        b["locations"][0]["logicalLocations"][0]["decoratedName"] = "pkg.Service#f(java.lang.String):109:(return null)"
        self.bundle("main", [a])
        self.bundle("bidi", [b])
        result = self.compare()
        self.assertEqual(1, result["counts"]["removed_unique"])
        self.assertEqual(1, len(result["method_exit_location_candidates"]))
        self.assertEqual("return null", result["method_exit_location_candidates"][0]["bidi_candidates"][0]["instruction"])
        self.assertEqual([], result["possible_same_logical_sink"])


if __name__ == "__main__":
    unittest.main()
