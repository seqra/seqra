import unittest
from pathlib import Path
from types import SimpleNamespace

from benchmark_bidi_projects import analyzer_jvm_args


class AnalyzerConfigurationTest(unittest.TestCase):
    def options(self, mode):
        return SimpleNamespace(run1_mark_mode=mode, heap_snapshots=True,
                               form_statistics=True, optimization_statistics=True)

    def test_paired_modes_change_only_the_selected_property(self):
        path = Path('/tmp/paired-run')
        group = analyzer_jvm_args(self.options('GROUP'), path, 'bidi', '12g')
        full = analyzer_jvm_args(self.options('FULL'), path, 'bidi', '12g')
        self.assertEqual([x for x in group if 'run1MarkMode=' not in x],
                         [x for x in full if 'run1MarkMode=' not in x])
        self.assertIn('-Dopentaint.bidi.run1MarkMode=GROUP', group)
        self.assertIn('-Dopentaint.bidi.run1MarkMode=FULL', full)
        self.assertIn('-Dopentaint.bidi.disableRun1Statics=false', full)
        self.assertIn('-Xmx12g', full)

    def test_main_has_no_candidate_flags_and_keeps_the_same_capture_policy(self):
        path = Path('/tmp/main-run')
        values = analyzer_jvm_args(self.options('FULL'), path, 'main', '8g')
        self.assertFalse(any('bidi.' in x for x in values))
        self.assertIn('-Dopentaint.memorySnapshotDir=/tmp/main-run', values)
        self.assertTrue(any(x.startswith('-Xlog:gc*:') for x in values))


if __name__ == '__main__':
    unittest.main()
