#!/usr/bin/env python3
"""Run the task.md workloads through Gradle, with fixed inputs and profiles.

Build :classes in both checkouts first. Runs are sequential. Existing result
directories are never overwritten. Paths and commands are saved for each run.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time

ASPROF = Path('/home/sobol/.local/opt/async-profiler-4.5-linux-x64/bin/asprof')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def is_run_analyzer(pid, directory):
    try:
        command = Path(f'/proc/{pid}/cmdline').read_bytes()
        return (f'filename={directory}/continuous.jfr'.encode() in command
                and b'org.opentaint.jvm.sast.runner.ProjectAnalyzerRunner' in command)
    except OSError:
        return False


def signal_run_analyzer(pid, directory, requested_signal):
    if pid is None or not is_run_analyzer(pid, directory):
        return False
    try:
        os.kill(pid, requested_signal)
        return True
    except ProcessLookupError:
        return False


def analyzer_pid(directory):
    # A reused Gradle daemon is not a child of the invoking wrapper. Match this
    # run's unique recording path rather than a global Java process name.
    for process in Path('/proc').iterdir():
        if not process.name.isdigit():
            continue
        if is_run_analyzer(process.name, directory):
            return int(process.name)
    return None


def host_memory():
    try:
        fields = {line.split(':', 1)[0]: int(line.split()[1]) * 1024
                  for line in Path('/proc/meminfo').read_text().splitlines()
                  if line.startswith(('MemAvailable:', 'SwapTotal:', 'SwapFree:'))}
        return {'mem_available_bytes': fields['MemAvailable'],
                'swap_used_bytes': fields['SwapTotal'] - fields['SwapFree'],
                'load_average_1m': os.getloadavg()[0]}
    except (OSError, KeyError, ValueError):
        return {}


def run(args, project, side, config):
    checkout = args.main if side == 'main' else args.current
    directory = args.output / project / side
    directory.mkdir(parents=True, exist_ok=False)
    model = Path(config['model'])
    memory = config['memory']
    timeout = config['timeout']
    runner_args = ['--project', str(model), '--output-dir', str(directory / 'reports'),
                   '--ifds-analysis-timeout', str(timeout),
                   '--logs-file', str(directory / 'analyzer.log'), '--verbosity', 'debug']
    runner_args += ['--semgrep-rule-set', str(checkout / 'rules/ruleset')]
    runner_args.append('--sarif-generate-fingerprint')
    if project != 'OWASP':
        runner_args.append('--sarif-semgrep-style-id')
        runner_args += ['--semgrep-rule-severity', 'warning', '--semgrep-rule-severity', 'error']
    if project == 'conductor':
        extension = args.test / 'projects/extensions/conductor'
        runner_args += ['--semgrep-rule-set', str(extension / 'rules'),
                        '--passthrough-approximations', str(extension / 'passthrough'),
                        '--java-dataflow-approximations', str(args.test / 'opentaint-test-conductor/dataflow-compiled')]
    if side == 'bidi':
        runner_args.append('--ifds-bidirectional')
    init = directory / 'benchmark.init.gradle'
    snapshot_arg = (f', "-Dopentaint.memorySnapshotDir={directory}", '
                    f'"-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath={directory}/jvm-oom.hprof"'
                    if args.heap_snapshots else '')
    form_statistics = args.form_statistics and side == 'bidi'
    statistics_arg = ', "-Dorg.opentaint.bidi.formStatistics=true"' if form_statistics else ''
    # Configure only the selected JavaExec; Gradle/compiler heaps stay separate.
    init.write_text('gradle.projectsEvaluated {\n'
                    '  if (rootProject.name != "opentaint-jvm-sast") return\n'
                    '  rootProject.tasks.named("runProjectAnalyzer", JavaExec).configure {\n'
                    f'    jvmArgs = ["-Xmx{memory}", '
                    f'"-XX:StartFlightRecording=filename={directory}/continuous.jfr,settings=profile,dumponexit=true,maxsize=256m"{snapshot_arg}{statistics_arg}]\n'
                    f'    args = {json.dumps(runner_args)}\n'
                    '  }\n}\n')
    # These four inputs are JVM projects. The Go server is never called.
    command = ['./gradlew', ':runProjectAnalyzer', '--console=plain', '-I', str(init),
               '-x', ':opentaint-ir:go:buildGoServer']
    manifest = {'project': project, 'side': side, 'revision': git(checkout, 'rev-parse', 'HEAD'),
                'patch_sha256': hashlib.sha256(git(checkout, 'diff', 'HEAD').encode()).hexdigest(),
                'config': config, 'model_sha256': sha(model), 'command': command,
                'runner_args': runner_args, 'test_revision': git(args.test, 'rev-parse', 'HEAD'),
                'guard_heap_snapshot': args.heap_snapshots,
                'form_statistics': form_statistics,
                'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'START {project} {side}: {memory}, {timeout}s', flush=True)
    start = time.monotonic()
    pid = None
    analysis_start = None
    full_start = None
    peak_rss = 0
    profiles = {}
    profile_jobs = []
    hard_timeout = False
    log_offset = 0
    log_tail = ''
    with (directory / 'console.log').open('w') as console, (directory / 'resources.jsonl').open('w') as resources:
        process = subprocess.Popen(command, cwd=checkout / 'core', stdout=console,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        (directory / 'gradle.pid').write_text(str(process.pid) + '\n')
        while process.poll() is None:
            now = time.monotonic()
            pid = pid or analyzer_pid(directory)
            analyzer_log = directory / 'analyzer.log'
            if analyzer_log.exists():
                with analyzer_log.open() as stream:
                    stream.seek(log_offset)
                    lines = stream.read()
                    log_offset = stream.tell()
                log_tail = (log_tail + lines)[-65536:]
                if analysis_start is None and 'Start IFDS analysis' in lines:
                    analysis_start = now
                if full_start is None and ('BIDI_RUN_START run=1 ' in lines or 'Start full scan phase' in lines):
                    full_start = now
            if pid:
                try:
                    stat = Path(f'/proc/{pid}/stat').read_text().split()
                    rss = int(stat[23]) * os.sysconf('SC_PAGE_SIZE')
                    peak_rss = max(peak_rss, rss)
                    resources.write(json.dumps({'elapsed': now - start, 'pid': pid, 'rss': rss,
                                                'cpu_seconds': (int(stat[13]) + int(stat[14])) / os.sysconf('SC_CLK_TCK'),
                                                'major_faults': int(stat[11]), **host_memory()}) + '\n')
                    resources.flush()
                except OSError:
                    pass
                windows = [('normal', full_start, 60), ('near-limit', analysis_start, timeout - 60)]
                for label, anchor, delay in windows:
                    if anchor and now >= anchor + delay and label not in profiles:
                        output = directory / f'{label}.collapsed'
                        profile_log = (directory / f'{label}-profiler.log').open('w')
                        job = subprocess.Popen([str(ASPROF), '-e', 'cpu', '-d', '30', '-o', 'collapsed',
                                                '-f', str(output), str(pid)], stdout=profile_log, stderr=subprocess.STDOUT)
                        profiles[label] = {'start_elapsed': now - start, 'duration_requested': 30}
                        profile_jobs.append((label, job, profile_log))
            # The project config gives a 1200s outer scan limit. Start at the
            # analyzer launch so Gradle compilation is not charged to the scan.
            if pid and 'analyzer_started_elapsed' not in manifest:
                manifest['analyzer_started_elapsed'] = now - start
            launched = manifest.get('analyzer_started_elapsed')
            if launched is not None and now - start - launched > config['outer_timeout']:
                hard_timeout = True
                # A reused Gradle daemon can launch the analyzer outside the wrapper's process group.
                signal_run_analyzer(pid, directory, signal.SIGTERM)
                os.killpg(process.pid, signal.SIGTERM)
                break
            time.sleep(1)
        try:
            return_code = process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            signal_run_analyzer(pid, directory, signal.SIGKILL)
            os.killpg(process.pid, signal.SIGKILL)
            return_code = process.wait()
    if pid is not None and is_run_analyzer(pid, directory):
        signal_run_analyzer(pid, directory, signal.SIGTERM)
        deadline = time.monotonic() + 30
        while is_run_analyzer(pid, directory) and time.monotonic() < deadline:
            time.sleep(0.2)
        if is_run_analyzer(pid, directory):
            signal_run_analyzer(pid, directory, signal.SIGKILL)
    command_wall_seconds = time.monotonic() - start
    for label, job, stream in profile_jobs:
        try:
            profiles[label]['return_code'] = job.wait(timeout=35)
        except subprocess.TimeoutExpired:
            job.kill()
            profiles[label]['return_code'] = job.wait()
        stream.close()
    console_text = (directory / 'console.log').read_text(errors='replace')
    exit_match = re.search(r'finished with non-zero exit value (\d+)', console_text)
    analyzer_exit = int(exit_match[1]) if exit_match else (0 if return_code == 0 else None)
    status = 'HARD_TIMEOUT' if hard_timeout else {0: 'OK', 253: 'OOM', 254: 'TIMEOUT', 252: 'EXCEPTION'}.get(analyzer_exit, 'BUILD_OR_RUN_ERROR')
    summary = {'status': status, 'gradle_exit': return_code, 'analyzer_exit': analyzer_exit,
               'command_wall_seconds': command_wall_seconds,
               'wall_seconds': time.monotonic() - start, 'peak_rss_bytes': peak_rss,
               'analysis_started_elapsed': analysis_start - start if analysis_start else None,
               'full_started_elapsed': full_start - start if full_start else None,
               'profiles': profiles, 'continuous_jfr': (directory / 'continuous.jfr').exists(),
               'sarif': (directory / 'reports/report-ifds.sarif').exists()}
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (directory / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(f'END {project} {side}: {status}, {summary["wall_seconds"]:.1f}s, SARIF={summary["sarif"]}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('main', 'current', 'test', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--projects', nargs='+', choices=['OWASP', 'conductor', 'thingsboard', 'Stirling-PDF'],
                        default=['OWASP', 'conductor', 'thingsboard', 'Stirling-PDF'])
    parser.add_argument('--sides', nargs='+', choices=['main', 'bidi'], default=['main', 'bidi'])
    parser.add_argument('--heap-snapshots', action='store_true',
                        help='Dump a live heap before the memory guard cancels analysis (diagnostic runs only)')
    parser.add_argument('--form-statistics', action='store_true',
                        help='Record aggregate bidi form/cache counters without per-event logging')
    args = parser.parse_args()
    for name in ('main', 'current', 'test', 'output'):
        setattr(args, name, getattr(args, name).resolve())
    parent = args.test.parent
    configs = {
        'OWASP': {'model': str(parent / 'owasp-ci-investigation/project/project.yaml'), 'memory': '8g', 'timeout': 1000, 'outer_timeout': 1200},
        'conductor': {'model': str(args.test / 'opentaint-test-conductor/opentaint-project/project.yaml'), 'memory': '8g', 'timeout': 1080, 'outer_timeout': 1200},
        'thingsboard': {'model': str(args.test / 'opentaint-test-thingsboard/opentaint-project/project.yaml'), 'memory': '12g', 'timeout': 1080, 'outer_timeout': 1200},
        'Stirling-PDF': {'model': str(parent / 'e2e-root/model-Stirling-PDF/project.yaml'), 'memory': '8g', 'timeout': 1080, 'outer_timeout': 1200},
    }
    for project in args.projects:
        for side in args.sides:
            run(args, project, side, configs[project])


if __name__ == '__main__':
    main()
