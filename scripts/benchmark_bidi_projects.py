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

try:
    from .compare_bidi_projects import ITERATION_FIELDS, log_evidence
except ImportError:
    from compare_bidi_projects import ITERATION_FIELDS, log_evidence

ASPROF = Path('/home/sobol/.local/opt/async-profiler-4.5-linux-x64/bin/asprof')


def analyzer_jvm_args(args, directory, side, memory):
    values = [f'-Xmx{memory}', '-XX:FlightRecorderOptions=stackdepth=256',
              f'-XX:StartFlightRecording=filename={directory}/continuous.jfr,settings=profile,dumponexit=true,maxsize=256m',
              f'-Xlog:gc*:file={directory}/gc.log:time,uptime,level,tags:filecount=0']
    if args.heap_snapshots:
        values += [f'-Dopentaint.memorySnapshotDir={directory}',
                   '-XX:+HeapDumpOnOutOfMemoryError', f'-XX:HeapDumpPath={directory}/jvm-oom.hprof']
    if side == 'bidi':
        values += [f'-Dopentaint.bidi.run1MarkMode={args.run1_mark_mode}',
                   '-Dopentaint.bidi.disableRun1Statics=false']
        if args.form_statistics:
            values.append('-Dorg.opentaint.bidi.formStatistics=true')
        if args.optimization_statistics:
            values.append('-Dorg.opentaint.bidi.optimizationStatistics=true')
    return values


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


def bidi_iteration_summary(log_path, side, status):
    """Read bounded control lines after exit; never retain giant frontier dumps."""
    available = log_path.is_file()
    controls = []
    if available and side != 'main':
        tail, discarded = '', False
        with log_path.open(errors='replace') as stream:
            while True:
                chunk = stream.read(65536)
                if not chunk:
                    if tail and not discarded:
                        controls.append(tail)
                    break
                pieces = chunk.split('\n')
                for piece in pieces[:-1]:
                    line = tail + piece
                    if not discarded and len(line) <= 4096 and (
                            'BIDI_RUN_START' in line or 'BIDI_RUN_END' in line or
                            'BIDI_BARRIER_END' in line or ') frontier:' in line):
                        controls.append(line)
                    tail, discarded = '', False
                last = pieces[-1]
                if not discarded:
                    tail += last
                    if len(tail) > 4096:
                        tail, discarded = '', True
    evidence = log_evidence('\n'.join(controls), {'status': status}, side=side, log_available=available)
    return {key: evidence[key] for key in ITERATION_FIELDS}


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
    form_statistics = args.form_statistics and side == 'bidi'
    optimization_statistics = args.optimization_statistics and side == 'bidi'
    jvm_args = analyzer_jvm_args(args, directory, side, memory)
    # Configure only the selected JavaExec; Gradle/compiler heaps stay separate.
    init.write_text('gradle.projectsEvaluated {\n'
                    '  if (rootProject.name != "opentaint-jvm-sast") return\n'
                    '  rootProject.tasks.named("runProjectAnalyzer", JavaExec).configure {\n'
                    f'    jvmArgs = {json.dumps(jvm_args)}\n'
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
                'optimization_statistics': optimization_statistics,
                'run1_mark_mode': args.run1_mark_mode if side == 'bidi' else None,
                'analyzer_jvm_args': jvm_args,
                'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'START {project} {side}: {memory}, {timeout}s', flush=True)
    start = time.monotonic()
    pid = None
    analysis_start = None
    full_start = None
    workload_finished = False
    peak_rss = 0
    profiles = {}
    profile_jobs = []
    hard_timeout = False
    log_offset = 0
    log_tail = ''
    latest_run = None
    latest_run_active = False
    run1_recording = None
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
                for marker in re.finditer(r'BIDI_RUN_(START|END) run=(\d+)', log_tail):
                    latest_run = int(marker[2])
                    latest_run_active = marker[1] == 'START'
                if (pid and side == 'bidi' and run1_recording is None
                        and 'BIDI_RUN_END run=1 ' in log_tail):
                    # Preserve the early recording before a later run rolls it out.
                    # This runs after the engine end marker, without a GC request.
                    try:
                        dump = subprocess.run(
                            [str(Path(os.environ['JAVA_HOME']) / 'bin/jcmd'), str(pid),
                             'JFR.dump', f'filename={directory}/run1.jfr'],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
                        run1_recording = {'returncode': dump.returncode, 'output': dump.stdout,
                                          'elapsed': now - start, 'latest_run': latest_run}
                    except (OSError, subprocess.TimeoutExpired) as error:
                        run1_recording = {'error': str(error), 'elapsed': now - start,
                                          'latest_run': latest_run}
                if analysis_start is None and 'Start IFDS analysis' in lines:
                    analysis_start = now
                if full_start is None and ('BIDI_RUN_START run=1 ' in lines or 'Start full scan phase' in lines):
                    full_start = now
                if ('Finish full scan phase' in lines or 'BIDI_ANALYSIS_END ' in lines
                        or 'Finish IFDS analysis' in lines):
                    workload_finished = True
            if pid:
                try:
                    if 'analyzer_executable' not in manifest:
                        command_line = Path(f'/proc/{pid}/cmdline').read_bytes()
                        executable = os.readlink(f'/proc/{pid}/exe')
                        (directory / 'analyzer-process.json').write_text(json.dumps({
                            'pid': pid, 'executable': executable,
                            'command': [arg.decode(errors='replace') for arg in command_line.split(b'\0') if arg],
                        }, indent=2) + '\n')
                        manifest['analyzer_executable'] = executable
                        manifest['analyzer_command_sha256'] = hashlib.sha256(command_line).hexdigest()
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
                    if anchor and not workload_finished and now >= anchor + delay and label not in profiles:
                        if label == 'normal' and side == 'bidi' and not (latest_run == 1 and latest_run_active):
                            profiles[label] = {'skipped': True, 'reason': 'Run-1 engine already ended',
                                               'latest_run': latest_run, 'elapsed': now - start}
                            continue
                        output = directory / f'{label}.collapsed'
                        profile_log = (directory / f'{label}-profiler.log').open('w')
                        job = subprocess.Popen([str(ASPROF), '-e', 'cpu', '-d', '30', '-o', 'collapsed',
                                                '-f', str(output), str(pid)], stdout=profile_log, stderr=subprocess.STDOUT)
                        profiles[label] = {'start_elapsed': now - start, 'duration_requested': 30,
                                           'requested_delay_seconds': delay,
                                           'anchor': 'run-1-engine' if side == 'bidi' and label == 'normal' else 'full-analysis' if label == 'normal' else 'ifds',
                                           'latest_run': latest_run, 'engine_active': latest_run_active}
                        profile_jobs.append((label, job, profile_log))
                for label, job, _ in profile_jobs:
                    if job.poll() is not None and 'end_observed_elapsed' not in profiles[label]:
                        profiles[label].update(end_observed_elapsed=now - start,
                                               end_latest_run=latest_run,
                                               end_engine_active=latest_run_active)
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
        if 'end_observed_elapsed' not in profiles[label]:
            profiles[label]['end_phase_unobserved'] = True
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
               'run1_recording': run1_recording,
               'sarif': (directory / 'reports/report-ifds.sarif').exists()}
    summary.update(bidi_iteration_summary(directory / 'analyzer.log', side, status))
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
    parser.add_argument('--optimization-statistics', action='store_true',
                        help='Record bounded N1/N2 counters with run, direction and limit attribution')
    parser.add_argument('--run1-mark-mode', choices=['GROUP', 'FULL'], default='GROUP',
                        help='Select run-1 mark precision in the analyzer JVM; later runs remain FULL')
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
