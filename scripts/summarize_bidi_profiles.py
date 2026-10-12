#!/usr/bin/env python3
"""Summarize captured CPU stacks, JFR samples, allocations, and GC events.

JFR execution samples cover Java stacks, not total CPU time. Inclusive sample
shares overlap. Allocation sample weights estimate bytes; they are not a heap
census. The output retains these limits and raw event/profile artifact paths.
"""
import argparse
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
import re
import subprocess


def ranking(counts, total):
    return [{'frame': name, 'weight': weight, 'percent': round(100 * weight / total, 2) if total else 0}
            for name, weight in counts.most_common(25)]


def collapsed(path):
    leaves, inclusive = Counter(), Counter()
    total = 0
    for line in path.read_text().splitlines():
        stack, _, weight = line.rpartition(' ')
        if not weight.isdigit():
            continue
        count = int(weight)
        frames = stack.split(';')
        total += count
        leaves[frames[-1]] += count
        for frame in set(frames):
            inclusive[frame] += count
    return {'samples': total, 'leaf': ranking(leaves, total), 'inclusive': ranking(inclusive, total)}


def method_name(frame):
    method = frame.get('method') or {}
    cls = (method.get('type') or {}).get('name', '?').replace('/', '.')
    return cls + '.' + method.get('name', '?')


def parse_date(value):
    # Python 3.8 accepts at most six fractional digits; JFR emits nine.
    value = re.sub(r'(\.\d{6})\d+', r'\1', value)
    return datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()


def seconds(duration):
    # JFR JSON uses an ISO-8601 duration (PT0.123S).
    match = re.fullmatch(r'PT([\d.]+)S', str(duration))
    return float(match[1]) if match else None


def phase_windows(log, day_start, recording_end):
    active, windows = {}, {}
    day = day_start
    first_day = day_start
    latest = None
    for line in log.splitlines():
        clock = re.match(r'(\d\d):(\d\d):(\d\d)\.(\d+)', line)
        if not clock:
            continue
        hour, minute, second, fraction = clock.groups()
        timestamp = day + int(hour) * 3600 + int(minute) * 60 + int(second) + float('0.' + fraction)
        if latest is None:
            # The first JFR samples can arrive just after midnight even when
            # the log starts on the preceding day.
            if timestamp - recording_end > 43200:
                day -= 86400
                first_day = day
                timestamp -= 86400
        elif latest - timestamp > 43200:
            day += 86400
            timestamp += 86400
        elif day > first_day and timestamp - latest > 43200:
            # After a rollover a delayed late-evening line belongs to the
            # previous day. It must not rewind the day for subsequent lines.
            timestamp -= 86400
        # Small out-of-order clocks remain on their day and do not move the
        # rollover reference backward. A half-day jump distinguishes them
        # from the nearly full-day clock change at midnight.
        latest = timestamp if latest is None else max(latest, timestamp)
        for name, begin, end in (
                ('ifds', 'Start IFDS analysis', 'Finish IFDS analysis'),
                ('prescan', 'Start prescan phase', 'Finish prescan phase'),
                ('legacy-full-scan', 'Start full scan phase', 'Finish full scan phase'),
                ('trace-generation', 'Start trace generation', 'Finish trace generation')):
            if begin in line:
                active[name] = timestamp
            if end in line and name in active:
                windows[name] = (active.pop(name), timestamp)
        start = re.search(r'BIDI_RUN_START run=(\d+)', line)
        end = re.search(r'BIDI_RUN_END run=(\d+)', line)
        if start:
            active['run-' + start[1]] = timestamp
        if end:
            name = 'run-' + end[1]
            if name in active:
                windows[name] = (active.pop(name), timestamp)
    windows.update({name: (start, recording_end) for name, start in active.items()})
    return windows


def sample_window(samples, start, end):
    leaves, inclusive = Counter(), Counter()
    count = 0
    for timestamp, frames in samples:
        if start <= timestamp <= end:
            count += 1
            leaves[frames[0]] += 1
            for frame in set(frames):
                inclusive[frame] += 1
    return {'samples': count, 'duration_seconds': end - start,
            'leaf': ranking(leaves, count), 'inclusive': ranking(inclusive, count)}


def summarize(directory):
    output = {'limits': __doc__, 'windows': {}}
    for profile in directory.glob('*.collapsed'):
        output['windows'][profile.stem] = collapsed(profile)
    recording = directory / 'continuous.jfr'
    if not recording.exists():
        return output
    exported = directory / 'profile-events.json'
    with exported.open('w') as stream:
        result = subprocess.run(['jfr', 'print', '--json', '--events',
                                 'jdk.ExecutionSample,jdk.ObjectAllocationSample,jdk.GarbageCollection',
                                 str(recording)], stdout=stream, stderr=subprocess.PIPE, text=True)
    if result.returncode:
        output['jfr_error'] = result.stderr
        return output
    data = json.loads(exported.read_text())
    leaves, inclusive, allocated = Counter(), Counter(), Counter()
    total = 0
    allocation_bytes = 0
    timestamps = []
    samples = []
    allocation_samples = []
    gc = []
    for event in data['recording']['events']:
        values = event['values']
        timestamp = parse_date(values['startTime'])
        if event['type'] == 'jdk.ExecutionSample':
            frames = [method_name(frame) for frame in (values.get('stackTrace') or {}).get('frames', [])]
            if not frames:
                continue
            total += 1
            timestamps.append(timestamp)
            samples.append((timestamp, frames))
            leaves[frames[0]] += 1
            for name in set(frames):
                inclusive[name] += 1
        elif event['type'] == 'jdk.ObjectAllocationSample':
            weight = values['weight']
            allocation_bytes += weight
            allocated[(values.get('objectClass') or {}).get('name', '?').replace('/', '.')] += weight
            frames = [method_name(frame) for frame in (values.get('stackTrace') or {}).get('frames', [])]
            allocation_samples.append((timestamp, weight, frames))
        elif event['type'] == 'jdk.GarbageCollection':
            gc.append({'time': values['startTime'], 'name': values.get('name'), 'cause': values.get('cause'),
                       'duration_seconds': seconds(values.get('duration')),
                       'pause_seconds': seconds(values.get('sumOfPauses'))})
    output['continuous'] = {'samples': total, 'leaf': ranking(leaves, total),
                            'inclusive': ranking(inclusive, total), 'gc': gc,
                            'gc_pause_seconds': sum(row['pause_seconds'] or 0 for row in gc),
                            'sampled_allocation_bytes': allocation_bytes,
                            'allocation_classes': ranking(allocated, allocation_bytes)}
    if timestamps:
        # The last 30 seconds cover termination even if the workload finishes
        # before its timeout. Keep this separate from the requested near-limit window.
        end = max(timestamps)
        day_start = int(min(timestamps) // 86400) * 86400
        log = (directory / 'analyzer.log').read_text(errors='replace')
        output['phases'] = {}
        for name, (start, finish) in phase_windows(log, day_start, end).items():
            phase = sample_window(samples, start, finish)
            frames_weight = Counter()
            weight = 0
            for timestamp, allocation, frames in allocation_samples:
                if start <= timestamp <= finish:
                    weight += allocation
                    for frame in set(frames):
                        frames_weight[frame] += allocation
            phase['sampled_allocation_bytes'] = weight
            phase['allocation_inclusive'] = ranking(frames_weight, weight)
            phase['last_30_seconds'] = sample_window(samples, max(start, finish - 30), finish)
            if finish > start + 60:
                phase['after_one_minute'] = sample_window(samples, start + 60, min(start + 90, finish))
                phase['after_one_minute']['full_30_seconds'] = finish >= start + 90
            output['phases'][name] = phase
        last_leaves, last_inclusive = Counter(), Counter()
        count = 0
        for timestamp, frames in samples:
            if timestamp < end - 30:
                continue
            count += 1
            last_leaves[frames[0]] += 1
            for frame in set(frames):
                last_inclusive[frame] += 1
        output['last_30_seconds'] = {'samples': count, 'leaf': ranking(last_leaves, count),
                                      'inclusive': ranking(last_inclusive, count)}
    # The recording is the authoritative artifact; this verbose export can be
    # recreated and would otherwise duplicate hundreds of MB of stack metadata.
    exported.unlink()
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    args = parser.parse_args()
    for project in ('OWASP', 'conductor', 'thingsboard', 'Stirling-PDF'):
        for side in ('main', 'bidi'):
            directory = args.root / project / side
            if not (directory / 'summary.json').exists():
                continue
            target = directory / 'profile-summary.json'
            if target.exists():
                continue
            target.write_text(json.dumps(summarize(directory), indent=2) + '\n')
            print(target, flush=True)


if __name__ == '__main__':
    main()
