import unittest
from datetime import datetime, timezone

from summarize_bidi_profiles import collapsed, parse_date, phase_windows, sample_window
from pathlib import Path
import tempfile


class ProfileSummaryTest(unittest.TestCase):
    def test_jfr_nanosecond_date_is_accepted(self):
        expected = datetime(2026, 10, 12, 7, 49, 41, 728299, tzinfo=timezone.utc).timestamp()
        self.assertAlmostEqual(parse_date('2026-10-12T07:49:41.728299064Z'), expected)

    def test_incomplete_run_window_ends_at_last_recorded_sample(self):
        windows = phase_windows('00:00:10.000 |I| BIDI_RUN_START run=1 direction=FORWARD', 0, 25)
        self.assertEqual(windows, {'run-1': (10, 25)})

    def test_midnight_rollover_keeps_phase_durations_and_samples(self):
        day_start = datetime(2026, 10, 12, tzinfo=timezone.utc).timestamp()
        next_day = day_start + 86400
        log = '\n'.join([
            '23:59:55.500 |I| Start IFDS analysis',
            '23:59:58.250 |I| BIDI_RUN_START run=1 direction=FORWARD',
            '00:00:02.750 |I| BIDI_RUN_END run=1',
            '00:00:03.000 |I| BIDI_RUN_START run=2 direction=BACKWARD',
            '00:00:04.000 |I| Finish IFDS analysis',
        ])
        windows = phase_windows(log, day_start, next_day + 5)
        self.assertEqual(windows, {
            'ifds': (next_day - 4.5, next_day + 4),
            'run-1': (next_day - 1.75, next_day + 2.75),
            'run-2': (next_day + 3, next_day + 5),
        })
        profile = sample_window([
            (next_day - 2, ['before']),
            (next_day - 1, ['run']),
            (next_day + 2, ['run']),
            (next_day + 3, ['after']),
        ], *windows['run-1'])
        self.assertEqual(profile['samples'], 2)
        self.assertEqual(profile['duration_seconds'], 4.5)

    def test_delayed_prior_day_line_does_not_advance_or_rewind_current_day(self):
        log = '\n'.join([
            '23:59:58.000 |I| BIDI_RUN_START run=1',
            '00:00:02.000 |I| Progress',
            '23:59:59.000 |I| BIDI_RUN_END run=1',
            '00:00:01.500 |I| Delayed progress',
            '00:00:03.000 |I| BIDI_RUN_START run=2',
            '00:00:04.000 |I| BIDI_RUN_END run=2',
        ])
        self.assertEqual(phase_windows(log, 0, 86405), {
            'run-1': (86398, 86399),
            'run-2': (86403, 86404),
        })

    def test_same_day_out_of_order_clocks_keep_original_timestamps(self):
        log = '\n'.join([
            '01:00:00.000 |I| Start prescan phase',
            '12:00:10.000 |I| BIDI_RUN_START run=1',
            '12:00:05.000 |I| Delayed progress',
            '12:00:12.000 |I| BIDI_RUN_END run=1',
            '20:00:00.000 |I| Finish prescan phase',
        ])
        self.assertEqual(phase_windows(log, 0, 72001), {
            'prescan': (3600, 72000),
            'run-1': (43210, 43212),
        })
        self.assertEqual(phase_windows('\n'.join([
            '01:00:00.000 |I| Start prescan phase',
            '20:00:00.000 |I| Finish prescan phase',
        ]), 0, 72001), {'prescan': (3600, 72000)})

    def test_log_can_start_before_midnight_when_first_samples_are_next_day(self):
        log = '\n'.join([
            '23:59:59.500 |I| BIDI_RUN_START run=1',
            '00:00:02.000 |I| BIDI_RUN_END run=1',
        ])
        self.assertEqual(phase_windows(log, 86400, 86403), {
            'run-1': (86399.5, 86402),
        })

    def test_recursive_frames_count_once_in_inclusive_share(self):
        data = sample_window([(11, ['leaf', 'recursive', 'recursive']), (12, ['other'])], 10, 20)
        weights = {item['frame']: item['weight'] for item in data['inclusive']}
        self.assertEqual(data['samples'], 2)
        self.assertEqual(weights['recursive'], 1)
        self.assertEqual(data['duration_seconds'], 10)

    def test_native_cpu_stacks_keep_leaf_and_inclusive_counts_separate(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'sample.collapsed'
            path.write_text('root;work;leaf 3\nroot;work 2\n')
            data = collapsed(path)
        self.assertEqual(data['samples'], 5)
        self.assertEqual(data['leaf'][0]['frame'], 'leaf')
        self.assertEqual(data['inclusive'][0]['percent'], 100)


if __name__ == '__main__':
    unittest.main()
