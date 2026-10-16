# Static-domain repair and frozen requests: project measurements

Date: 16 October 2026. Status: complete measurement report; all capture phases
are terminal. Strict finding comparisons, profiles and all six required OOM
heap analyses are complete. Performance and recall targets remain failed.

The static-domain exception is fixed in the four selected projects. Factoring
removes the expanded frozen request rows and preserves the corrected control's
reported findings. It does not meet the run-1 target: Conductor and ThingsBoard
still fail in run-1, and the completed PDF and OWASP run-1 engines take longer
than 60 seconds. The observed engine times do not show a factoring speedup.

The corrected materialized-map control is `d330a335a8ac4faf8ba839f2a74bcfd2500c79bd`.
Factoring is integrated as `4acde739bdc23d48eacb3c125429c461597f75be`, from
isolated commit `bbdbe7307`. Its fixed-project matrix is terminal. Fresh main is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`.

The final phase measures HEAD `05cf9ad6246bf5071da5499318ed9a9354c1e430`.
Its only changes after the factoring commit are documentation. Core source is
bound to both integrated validation receipts. The capture phase kept that HEAD
and its core source unchanged.

Use the [selected design](../design/bidirectional-static-domain-and-frozen-requests-20261016.md).
Historical reports are not measurements of these revisions.

## Fixed workload and evidence

Evidence root, called B below:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z
```

Each phase has `launch.json`, before/after core source bindings and an actual
terminal receipt. Each project has its manifest, analyzer log, process identity,
resource samples, SARIF, profile captures and terminal summary. An exit from the
matrix harness does not establish analyzer success.

| Project | Heap | IFDS time limit | Outer time limit |
| --- | ---: | ---: | ---: |
| OWASP | 8 GiB | 1,000 s | 1,200 s |
| Conductor | 8 GiB | 1,080 s | 1,200 s |
| ThingsBoard | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | 8 GiB | 1,080 s | 1,200 s |

The phases use the existing project models and direct Gradle
`:runProjectAnalyzer`. They use the same rules, project inputs and Java 17.
The pre-scan input audit passed 80 checks covering 41 content datasets. The
post-scan audit passes all 80 checks. Its 41 content datasets and all 80 non-path
check facts match the pre-scan audit. This is endpoint equality, not continuous
mutation monitoring. `B/input-endpoint-review.json` binds both receipts. Previous raw captures were
removed before this batch; `B/prior-raw-cleanup.json` records the cleanup.

## Fresh main

All four main scans completed. Main uses the legacy engine and has no
bidirectional reached, completed or accepted run number.

| Project | Status | Command wall s | Peak process RSS bytes | Bidi run |
| --- | --- | ---: | ---: | --- |
| OWASP | OK | 186.436 | 7,951,122,432 | N/A, legacy |
| Conductor | OK | 154.322 | 10,140,729,344 | N/A, legacy |
| ThingsBoard | OK | 323.725 | 14,585,487,360 | N/A, legacy |
| Stirling-PDF | OK | 87.257 | 7,333,937,152 | N/A, legacy |

The command wall scope includes setup and report export. It is not the run-1
engine scope. RSS includes memory outside the configured Java heap.
`B/fresh-main/terminal.json` confirms stable source and HEADs for this phase.

## Corrected map control

The phase finished at 08:04:46 UTC on 16 October 2026. All analyzer processes
are gone. Its wrapper receipt confirms unchanged core source, HEADs and capture
harness. Analyzer outcomes below remain resource failures, not successful scans.

| Project | Status | Latest reached | Latest completed engine | Accepted forward report | Run-1 engine s |
| --- | --- | --- | --- | --- | ---: |
| Stirling-PDF | OOM | run-4, backward, limit 4 | run-3, forward | run-3 | 168.350, COMPLETE |
| Conductor | OOM | run-1, forward, limit 1 | None | None | 105.805, OOM |
| ThingsBoard | OOM | run-1, forward, limit 1 | None | None | 106.335, OOM |
| OWASP | TIMEOUT | run-6, backward, limit 6 | run-5, forward | run-5 | 213.730, COMPLETE |

Stirling-PDF continues beyond the old static-domain exception and accepts a
report. Its completed run-1 exceeds the 60-second engine target. Conductor also
continues beyond the old exception, but reaches OOM before completing run-1.
Its failed prefix does not establish a completed run-1 time.

| Project/run | Direction | Engine s | Status | Barrier s |
| --- | --- | ---: | --- | ---: |
| Stirling-PDF/run-1 | Forward | 168.350408405 | COMPLETE | 1.295884956 |
| Stirling-PDF/run-2 | Backward | 27.927794890 | COMPLETE | 0.176676361 |
| Stirling-PDF/run-3 | Forward | 84.521827241 | COMPLETE | 0.472466056 |
| Stirling-PDF/run-4 | Backward | 164.003324173 | OOM | No successful barrier |
| Conductor/run-1 | Forward | 105.805429394 | OOM | No successful barrier |
| ThingsBoard/run-1 | Forward | 106.334662236 | OOM | No successful barrier |
| OWASP/run-1 | Forward | 213.729949159 | COMPLETE | 2.846339002 |
| OWASP/run-2 | Backward | 184.439226323 | COMPLETE | 0.749906612 |
| OWASP/run-3 | Forward | 78.895283839 | COMPLETE | 3.734110215 |
| OWASP/run-4 | Backward | 227.970837077 | COMPLETE | 1.542513527 |
| OWASP/run-5 | Forward | 74.165562483 | COMPLETE | 3.394645965 |
| OWASP/run-6 | Backward | 121.014597360 | TIMEOUT | No successful barrier |

The Stirling-PDF guard snapshot has 12,063,819,900 bytes. It was captured during
**run-4, backward**, before cancellation. Its accepted report is from run-3.
The Conductor guard snapshot has 11,721,619,519 bytes and was captured during
**run-1, forward**. Both have paired snapshot START/END markers. Snapshot file
bytes are not retained heap bytes. Bind owner analysis to this actual run-4
lifetime, not peak run-1.

The ThingsBoard guard snapshot has 18,393,949,142 bytes and paired START/END
markers during **run-1, forward**. Its corrected frozen plan has 3,000,410
physical rows and 188,923,219 logical per-method mark memberships. Mark payloads
are shared, so the membership total is not an allocated mark-cell count.

The corrected plan reports 19,663 physical frozen rows for Stirling-PDF and
4,492 for Conductor. These row counts alone do not identify the source of either
OOM. The current implementation already packs concrete output marks together;
distinct original initial premises can still have separate method state.
Live owner counts and CPU profiles must determine the next proposal.

## Final factored phase

The phase finished at **09:03:30.957 UTC on 16 October 2026**. All analyzer,
harness and phase-wrapper processes are gone. The terminal receipt confirms
stable core source, HEADs and capture harness. Remote `main` still points to
the baseline revision at the post-capture check.

| Project | Status at snapshot | Latest reached | Latest completed engine | Accepted forward report | Run-1 engine s |
| --- | --- | --- | --- | --- | ---: |
| Stirling-PDF | OOM | run-4, backward, limit 4 | run-3, forward | run-3 | 189.345575384, COMPLETE |
| Conductor | OOM | run-1, forward, limit 1 | None | None | 105.231510719, OOM |
| ThingsBoard | OOM | run-1, forward, limit 1 | None | None | 109.891609201, OOM |
| OWASP | TIMEOUT | run-5, forward, limit 5 | run-4, backward | run-3 | 288.383395111, COMPLETE |

| Project/run | Direction | Engine s | Status | Barrier s |
| --- | --- | ---: | --- | ---: |
| Stirling-PDF/run-1 | Forward | 189.345575384 | COMPLETE | 2.326458308 |
| Stirling-PDF/run-2 | Backward | 39.099634079 | COMPLETE | 0.089274186 |
| Stirling-PDF/run-3 | Forward | 114.944203836 | COMPLETE | 0.864561681 |
| Stirling-PDF/run-4 | Backward | 543.623789893 | OOM | No successful barrier |
| Conductor/run-1 | Forward | 105.231510719 | OOM | No successful barrier |
| ThingsBoard/run-1 | Forward | 109.891609201 | OOM | No successful barrier |
| OWASP/run-1 | Forward | 288.383395111 | COMPLETE | 3.714652043 |
| OWASP/run-2 | Backward | 208.128967876 | COMPLETE | 1.138833996 |
| OWASP/run-3 | Forward | 110.351766695 | COMPLETE | 5.218786926 |
| OWASP/run-4 | Backward | 259.575427999 | COMPLETE | 1.735082507 |
| OWASP/run-5 | Forward | 15.196550874 | TIMEOUT | No successful barrier |

Failed engine durations retain their logged timer scope, including guard and
snapshot pauses. They are not completed-run times or pure solver CPU. A shorter
time to OOM does not establish better performance.

PDF command wall time is 967.951 seconds; peak process RSS is 10,367,856,640
bytes. Conductor command wall time is 228.561 seconds; peak RSS is
10,277,572,608 bytes. These include scopes outside the engine. PDF's completed
run-1 is 12.47% slower than the map-control observation. This is a measured
difference, not a demonstrated cost caused by factoring; profiles and workload
comparison remain required. Conductor has no completed run-1 time to compare.

The PDF guard capture completes at 08:34:01.167 UTC during run-4 and has
11,782,619,088 file bytes. The Conductor capture completes at 08:37:53.998 UTC
during run-1 and has 11,728,199,692 file bytes. Both have paired snapshot markers.
File size is not retained heap size. PDF's later capture cannot establish peak
run-1 request-plan savings after that plan has been released.

ThingsBoard's command wall time is 460.078 seconds; peak RSS is 14,721,036,288
bytes. Its run-1 guard capture completes at 08:45:33.034 UTC and has
18,375,310,302 file bytes, with paired markers. Its factored plan preserves all
15,671,695 logical per-method rows and 188,923,219 mark memberships. Expanded
physical B rows fall from 3,000,410 to zero; sparse field rows are zero. The
79,080 primitive mark cells remain unchanged. This removes an expanded relation
but does not prevent run-1 OOM. The heap analysis below identifies the remaining
owners.

| Frozen-plan measure | PDF map / factored | Conductor map / factored |
| --- | ---: | ---: |
| Physical expanded B rows | 19,663 / 0 | 4,492 / 0 |
| Factored sparse field rows | N/A / 0 | N/A / 0 |
| Logical per-method rows | 22,164 / 22,164 | 6,801 / 6,801 |
| Logical per-method mark memberships | 175,540 / 175,540 | 59,948 / 59,948 |
| Owned primitive mark cells | 12,994 / 12,994 | 3,920 / 3,920 |
| Metadata setup s | 12.520503008 / 9.750289972 | 14.100558803 / 12.384789930 |
| Catalog setup s | 0.346679881 / 0.430267886 | 0.708292491 / 1.345629780 |

The physical expansion is absent and logical B membership is equal in these
two plans. Payload cells were already shared and stay equal. These facts do not
establish a total retained-byte saving or equal completed analysis closure.
PDF admits the same 356,375 method-local new initials and 2,517,328 fresh
authoritative dynamic request memberships in completed run-1. Attempt counts
can differ with scheduling. Conductor's failed prefixes are not matched closure.
Both completed PDF run-1 observations exceed the 60-second target. The complete
strict map-to-final finding comparison is equal, as detailed below.

OWASP command wall time is 1,075.255 seconds and peak process RSS is
10,219,601,920 bytes. It captures no OOM heap. Its completed run-1 is 34.93%
slower than the control observation and exceeds the 60-second target. It admits
the same 36,592 new initials and 299,471 authoritative dynamic request members;
all compact run-1 forward-frontier totals match. The frozen plan preserves
1,155 logical per-method rows and 9,240 mark memberships, with zero expanded
B rows and zero sparse field rows. Primitive mark cells remain 14,544.

## Strict finding comparisons

The strict comparator preserves complete rule/sink identities, raw locations and
spans, and occurrence multiplicity. The reference and candidate inputs match.
Its successful execution does not make a failed analyzer a successful scan.

| Project | Main identities | Final identities | Missing | New identities | Main/final occurrences |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 4,112 | 0 | 0 | 4,112 / 9,179 |
| Conductor | 7 | 0 | 7 | 0 | 7 / 0 |
| ThingsBoard | 14 | 0 | 14 | 0 | 14 / 0 |
| Stirling-PDF | 17 | 56 | 3 | 42 | 17 / 128 |

Conductor and ThingsBoard have valid empty exports after run-1 OOM, with no
accepted forward report and no attempted report generation for a finding. Their
21 missing identities follow the resource failure. Stirling-PDF has three strict
location losses in its accepted run-3 report.
Same-rule later returns do not establish the same path or location coverage.
The 42 new PDF identities are additional candidates, not confirmed false
positives or confirmed feasible traces. Its 128 exports and OWASP's 9,179 exports
are Simple reports with no generated concrete trace evidence.

All four map-to-final comparisons preserve exactly the same sink identities,
raw retained locations and occurrence multiplicities. Conductor and ThingsBoard
both have empty reports, which is not evidence of baseline recall. OWASP's map
report is accepted at run-5; the final report is accepted at run-3. Equal output
does not mean equal reached or completed progress.

The eight final comparisons complete with exit 0 after captures and the input
audit. Their processes are gone. No input differences, invalid location rejects
or attempted/exported gaps are reported. The original strict comparator is
unchanged. Full identities, locations, spans and all 24 missing baseline
identities are under `B/comparisons/{main-final,map-final}/`; use
`B/comparisons/final-comparison-index.json` for the combined index and
`B/comparisons/final-eight-job-20261016T090745.625198Z/` for process and resource
receipts. Historical main-to-map comparison receipts remain under
`B/comparisons/main-map/`; that earlier comparator overlapped the application
control, not a project capture.

### PDF missing-location causes

A bounded read and then the full strict final comparator confirm that all three
baseline identities are absent in the final PDF report too.
Both PDF candidates accepted run-3 before run-4 OOM.

| Missing baseline location | Current cause evidence | Limit of the conclusion |
| --- | --- | --- |
| `EMLToPDF` earlier return, instruction 103, source lines 133–134 | Method-exit sinks use a synthetic normal-exit node. Simple reports have no preceding thread-flow return, so export selects the final physical return: instruction 126, lines 148–149 | Explains why the earlier strict location cannot be exported through this path; does not prove its original taint witness survived |
| `PipelineController` normal return, instruction 50, source lines 90–91 | The same fallback selects instruction 109, source line 130, in the later catch block (`return null`) | The later catch return is a different path and is not equivalent to the baseline sink |
| `ProcessExecutor` constructor call, instruction 8, source line 183 | The isolated current-source replay observes the URL add emit exact receiver `ARGS`, then the next add remove that same value; it also observes a backward exact `ARGS` requirement removed at the later output-path add | Identifies an actual transfer defect on the baseline URL route. It does not establish a unique constructor-to-controller lineage or exclude other routes contributing to the final loss |

The return-location argument follows the actual sink-key writer in
`RunMethodLocalServices.witness`, ordinary return transfers in
`JIRStatementForms`, boundary sink lowering in `JIRBoundaryForms`, the Simple
adapter in `JIRBidiAnalysis.toVulnerabilities`, and the final-return fallback in
`JirSarifGenerator`. It is a source-and-output deduction. No comparator is
relaxed and no earlier return is replaced by a later one for acceptance.

The baseline command route passes through URL `List.add`, then two more adds
before `ProcessBuilder`. The explicit field suffix is `this.urlInput`; call
count is not AP depth. Current source lowering adds an unconditional selected
mark cleaner to carried returned results when a source condition is not
statically false. It can clear old receiver `ARGS` even when that call emits no
replacement source result. New source outputs use a separate stage. The temporary
output path is not assumed untainted.

An isolated observer on exact HEAD `05cf9ad62` now records actual values. In
run-1, forward, source rows 1 and 3 at URL call 55 emit receiver-root exact ARGS
737 and 738. Rows 10 and 9 at call 56 (`add("--pdf-forms")`) receive the same
complete values under the same full controller key and original zero premise.
The returned-source cleaner completes with zero outputs. In run-2, backward,
row 39 removes exact receiver ARGS 737 at call 58, the temporary-output-path
add, through the reversed stage. All three cut rows have complete codecs and
zero outputs. The observed stage/action identities match the source defect.

Run-1 also has constructor contributions and created predicate-sink seeds with
ARGS 737. These are positive observations, not a unique path from the controller
zero premise to that constructor. The observer intentionally records bounded
examples, so it cannot prove global absence or the first loss among every branch.
The transfer defect is identified; exclusive attribution of the final missing
constructor key remains unproved.

The diagnostic passes four common controls, one JVM selector control and root
classes. An earlier isolated build fails compilation before any test executes;
the corrected source-v3 gate and original failure are both retained. Main
analyzer source stays unchanged. The replay uses the exact archived PDF model,
rules, heap and limits, with one observer property. It completes and accepts
run-3, then reaches run-4, backward. Root intentionally stops its verified owned
JVM after the positive evidence. Actual analyzer exit is 143 and Gradle exit is
1. The wrapper labels an intentional prefix, not a successful full scan.
No closed observer ledger or diagnostic SARIF is produced; their absence is
unavailable evidence, not a finding absence or result-parity claim.

Source and output receipts are under
`B/comparisons/main-map/Stirling-PDF/construction-and-codeflow-review-20261016T082816.920605Z/`
and `B/preparation/final-strict-comparison-readiness/`. The current command
review and isolated-observer design are under
`B/preparation/pdf-command-current-gate-review-20261016T084907.897285Z/`.
The new actual positive certificate is under
`B/analysis-preparation/pdf-command-positive-05cf/execution-v2-20261016T095351.164017Z/jobs/replay-20261016T095450.207128Z/positive-review/positive-certificate-f1-b2/`.
Its immutable 39-row prefix, eleven exact tuple checks, terminal/source/argument
bindings and explicit limitations remain separate from the primary comparisons.

## Integrated validation

The integrated commit passed all 1,480 common tests and 408 JVM tests, with no
failures, errors or skips. Application test compilation and root analyzer classes
passed. The gate has stable before/after source bindings and archived XML at
`B/validation/integrated-common-jvm/review/`.

The fresh map-control application selection ran 158 tests: 149 pass, eight
failures, no errors and one existing skip. Independent XML review confirms the
same expected and actual rule-ID multisets as the historical eight failures:
26 extra case/rule memberships and zero missing expected IDs. This remains a
failed strict gate.
The factored gate also ran 158 tests with the same pass/fail/error/skip counts.
Its testcase identities and failed rule-ID memberships/multiplicities match
the fresh map control. Six raw assertion lists differ in order only. The raw
collector differences remain preserved; this does not turn either failed gate
into a pass. Archives are under `B/validation/{corrected-map-app,factored-app}/review/`.

## CPU capture interpretation

The closed native CPU reader validates all 12 bundles. It parses 11 actual CPU
captures; the other 13 project/window slots were not scheduled and remain
unavailable. Captures retain their full weighted sample denominator, including
native and collector stacks. The windows below are nominal requested intervals
aligned to logs; the collapsed stacks contain no per-sample timestamps.

| Project/window | Map / final weighted samples | Heap-dump overlap s, map / final | Collector-name pattern share, map / final |
| --- | ---: | ---: | ---: |
| OWASP normal, run-1 | 5,374 / 5,932 | 0 / 0 | 28.12% / 14.53% |
| OWASP near limit, map run-6 / final run-4 | 11,541 / 10,634 | 0 / 0 | 75.03% / 72.88% |
| Conductor normal, run-1 | 19,173 / 22,529 | 14.646 / 13.598 | 90.25% / 86.17% |
| ThingsBoard normal, run-1 | 23,053 / 21,706 | 14.440 / 17.451 | 84.51% / 83.87% |
| Stirling-PDF normal, run-1 | 17,269 / 15,014 | 0 / 0 | 58.48% / 61.46% |

Conductor and ThingsBoard's 30-second normal windows include snapshot work.
Do not treat their collector-heavy totals as pure analyzer CPU. Use the
continuous JFR pre-guard window for Java hotspot attribution. PDF's normal
window has no snapshot overlap, yet collector-named stacks still dominate its
recorded CPU. Its final normal window also has `MethodEdgeStore` in 12.78% of
sample weight and the cleaner family in 5.79%. These are inclusive name-pattern
shares; they are not exclusive totals or predicted removable CPU.

Final OWASP's normal run-1 window has the call-plan build family in 22.93%,
the cleaner family in 16.10%, and signed normalization in 4.08% of original
sample weight. Those families overlap. The map window is at the same elapsed
offset but a different work prefix: its cleaner family is 36.62% and its
call-plan build family has no matching recorded stack. No matched-work speedup
follows from these window percentages. Near-limit windows use different backward
iterations and cannot establish an iteration speedup either.

Main has only one scheduled native capture: OWASP normal, 11,712 weighted
samples. That nominal window overlaps trace generation by 6.812 seconds and
SARIF output by 22.779 seconds; it is not a legacy engine CPU control. The other
three main scans finish their engine before the normal capture delay. Continuous
JFR remains the source for their engine windows.

Raw capture provenance, name patterns, complete ranked samples, nominal
intersections and sampled host-load/CPU/fault context are under
`B/measurement-review/native-cpu-closed-analysis-1/`. Independent derived review
is under `B/measurement-review/native-cpu-peer-review-20261016T091342Z/`.
The resource observations are context, not proof of the timing difference's
cause.

## Continuous JFR

All 12 closed recordings are exported. Main uses the original `jfr print`
producer. A compact producer exports the eight map/factored recordings after
exact parity with a fresh original-producer OWASP export. Both actual canary
processes use the same Python hash seed because equal-weight inclusive ranks
otherwise depend on set iteration order. The initial rank-tie failure remains
preserved; no comparison tolerance or rank waiver is used. The final canary
matches every window, native-runtime field, event count and metric exactly.

Every bidi run-1 window has full finalized chunk-range coverage and all four
metric event types. CPU stacks have no missing or recorded truncated flags in
these windows; allocation has two to four missing stacks per window and no
recorded truncation. No run-1 ranking budget overflows. All 12 JVM records
configure stack depth 256. Observed maximum depths do not prove complete caller
coverage beyond the actual recording.

| Final run-1 | Engine status | Java CPU samples | Sampled allocation bytes | Clipped GC pause s |
| --- | --- | ---: | ---: | ---: |
| OWASP | COMPLETE | 7,701 | 341,797,695,064 | 8.846 |
| Conductor | OOM | 11,885 | 361,250,572,832 | 28.861 |
| ThingsBoard | OOM | 14,801 | 238,781,656,056 | 24.227 |
| Stirling-PDF | COMPLETE | 19,462 | 429,394,158,024 | 11.362 |

Allocation values are sampled weights, not live heap bytes. Failed engine
windows include snapshot work and do not establish completed-run performance.
GC pause durations are clipped to the window; they are not the collector's
total CPU time. Main's logged full-scan includes trace generation at least in
OWASP, so it must not be called a pure legacy engine interval.

The final run-1 inclusive top frames show project-specific work:

| Project | Named operation | Share of Java CPU samples |
| --- | --- | ---: |
| ThingsBoard | Call-plan construction | 41.70% |
| ThingsBoard | Alias acquisition | 26.08% |
| OWASP | Call-plan construction | 28.52% |
| OWASP | Alias acquisition | 13.52% |
| OWASP | Fact cleaning | 17.53% |
| Conductor | Fact limiting | 30.59% |
| Conductor | Request replay | 27.72% |
| Stirling-PDF | Fact limiting | 16.53% |
| Stirling-PDF | Call-plan construction | 14.51% |

These inclusive frames overlap. A frame outside the top 50 is not a measured
zero, and a hot build frame does not by itself prove cache eviction or duplicate
construction. The profiles support separate investigations of method-form
construction, input/request joins and AP operations. They do not support one
global percentage of removable work.

Use `B/measurement-review/compact-derived-review-1/` for all 12 per-bundle
windows, coverage, setup/engine/barrier/guard intersections and hotspot summaries.
The exact canary is `B/measurement-review/compact-fixed-seed-parity-1.json`.
Original exports and both failed and successful adapter receipts remain intact.

## Setup and form counters

Setup intervals are separate from engine time. Prescan uses logged wall intervals;
metadata and catalog use their reported monotonic timers.

| Project | Prescan s, map / final | Metadata s, map / final | Catalog s, map / final |
| --- | ---: | ---: | ---: |
| OWASP | 41.973 / 52.443 | 45.259 / 53.632 | 0.245 / 0.352 |
| Conductor | 44.118 / 41.968 | 14.101 / 12.385 | 0.708 / 1.346 |
| ThingsBoard | 178.337 / 213.999 | 58.665 / 75.438 | 3.609 / 3.859 |
| Stirling-PDF | 31.095 / 25.462 | 12.521 / 9.750 | 0.347 / 0.430 |

Main prescan takes 52.207/42.570/218.090/27.745 seconds in the same project
order. Its logged full-scan intervals are 67.331/44.684/46.289/8.812 seconds,
including trace intervals of 6.812/2.127/0.072/0.965 seconds. These legacy full
intervals are not pure engine times or bidi run numbers.

The form counters combine cold builds, rebuilds after soft-reference clearing,
uncached builds and retries. They do not label those causes. Final ThingsBoard
records 895,245 successful builds, 14,413 successful uncached builds and 307,381
call-plan shape samples in its failed run-1. These samples are not unique live
forms. OWASP and PDF terminal totals cover later iterations too and cannot be
assigned to run-1. Summed builder wall times overlap across threads and nested
builds, so they are not CPU time or engine wall time.

The existing forms cache already uses managed soft references. The AA owner
computes lazily once per method; rebuilding a form does not discard that result.
Alias acquisition also performs queries, intersections, conversions and path
materialization. Its inclusive CPU share is not repeated AA computation time.
Aggregate soft-reference cleanup logs do not identify form-specific evictions.

Equal fresh initials and request members do not mean equal intermediate work.
For completed PDF run-1, request geometry steps change from 69,925,364 to
71,619,595, while replay visits change from 133,178 to 129,112. OWASP's admitted
input deltas change from 159,464 to 159,475. Available counters do not explain
the full 12.47% PDF or 34.93% OWASP engine-time increase. No causal performance
regression is assigned to factoring from these single observations.

Exact counter scopes, source bindings, all setup intervals and completed-run
comparisons are under `B/measurement-review/forms-and-setup-review-1/`.

## ThingsBoard run-1 heap owners

The final factored snapshot is from **run-1, forward, limit 1**. No engine or
forward report was accepted. MAT completed its first analysis at 09:36:53 UTC:
332,060,159 reachable objects, with 11,464,640,976 shallow bytes in the complete
uncapped histogram. Its 48 queries produced 46 CSV results and two empty
selections. Independent review verifies the empty selections against the complete
class census; it preserves the intentionally capped example table and the
unavailable actor-status projection.

| Selected retained owner union | Bytes |
| --- | ---: |
| Caller input/subscription state, called N2 in the implementation | 2,371,746,016 |
| Its positional index alone | 1,621,602,496 |
| Method-local fact directories | 2,006,855,232 |
| All conclusion buckets, including non-method stores | 1,897,154,816 |
| Normal call/entry/exit sink-configuration caches | 1,092,007,080 |
| Current form contexts and caches | 574,279,648 |
| Authoritative dynamic requests D | 74,291,776 |
| Temporary method request stores P | 94,050,072 |
| Method worklists | 103,490,944 |
| Event queues and buffered channels | 43,725,384 |

These are retained unions with different, overlapping roots. Do not add them or
call each total removable. In particular, conclusion buckets and directories
share downstream objects. The leading standing owners are much larger than the
pending queues. Reducing queued request volume alone cannot remove those owners.
The selected IR-owner union is 1,619,106,928 bytes for context only. IR storage
is outside the optimization scope and is not a proposed memory saving.

The method stores allocate 41,448,103 statement-reference cells in 2,342,470
premise rows. Those arrays have 206,959,448 shallow bytes. Their occupancy is
unknown. A sparse array change cannot claim the full 2.01 GB directory union as
its saving. Output leaves already pack concrete marks, while exact original
initial marks still distinguish premises and their method state. There are
2,410,745 live actor initial memberships but only 3,936 canonical InitialAp
objects globally. This shows repeated membership across method actors, not
2.41 million different fact shapes. The global wrapper census has 8,436,731
FLOW trees and 26,377 TAINT trees. Those counts do not identify N2 input kinds
or prove that concrete mark propagation dominates the workload.

The fact grammar bounds AP shapes, not the stored analysis relation. A method
state also has a full method/context/entry key, an original premise, a statement,
and conclusion base/layer/annotations. Caller inputs additionally retain the
original call and caller reference. The same canonical initial can therefore
own many separate states across methods and statements. This product, plus its
container/index overhead, explains why a small initial vocabulary can still
consume a large heap. It is not evidence of billions of distinct canonical facts.

The complete canonical-initial table splits into 3,919 abstract-mark initials
(3,901 static, 17 arguments and one `this`) and 17 concrete-mark initials
(16 static and canonical zero). All have empty field exclusions. This is a
canonical vocabulary census, not an actor-weighted count or an execution-cost
measurement. It weakens the claim that many different concrete input marks are
the main source of work in this current run-1. Repeated abstract static state
across method actors is a better hypothesis to test next.

An address join covers all 68,822 actor/emitter footprint borrowers: 17,233 have
`allStatic=true` and 51,589 have `false`. Current root cleaner needs can make
this flag true. No untouched-field fast path can apply under such a footprint.
The false borrowers are not a count of eligible inputs: their actual fields
can still be owned. This is a gate on the proposed frame optimization, not an
estimate of its savings. The joined source/census receipt is
`B/heap-analysis/operations/remaining-five-20261016T100346.967803Z/footprint-owner-scope.json`.

The input index has 5,852,153 trie nodes, each with an eager linked posting-set
shell, for 5,082,315 postings. This is a concrete representation cost to assess
before selecting a larger change to input ownership or mark execution.
The conservative empty/singleton-node shell bound is 238,391,712 bytes. The
all-bucket census also establishes at least 243,116,256 current bytes in empty
kind-map shells. These source/census bounds do not give net replacement savings.

The frozen-base retained query from active summary stores covers only 1,045 of
the 1,453 nonempty plan descriptors. Its 100,336-byte result is not the complete
plan payload. The all-descriptor retained union is 139,488 bytes, but also
excludes payloads rooted elsewhere through profiles and footprints. Neither
number is total B storage or a saved-byte measurement. Expanded physical row
removal is established by the plan census; a total retained-byte saving is not.

Evidence is under
`B/heap-analysis/results/final-factored/thingsboard/job-20261016T092001.948790Z-61c6a55cb6/`.
The independent address joins, source checks and scope review are under
`B/measurement-review/final-factored-thingsboard-heap-peer-review-20261016T0940Z/`.

The corrected-map snapshot is also from **run-1, forward, limit 1**, with no
completed engine or accepted report. Its focused analysis completes all 20
queries with CSV results and an unchanged capture. The reachable histogram has
329,880,048 objects and 11,479,118,832 shallow bytes.

| ThingsBoard retained union | Map bytes | Final bytes |
| --- | ---: | ---: |
| Caller input/subscription state | 2,463,278,776 | 2,371,746,016 |
| Its positional index | 1,672,469,448 | 1,621,602,496 |
| Method-local fact directories | 1,860,308,008 | 2,006,855,232 |
| All conclusion buckets | 1,807,457,360 | 1,897,154,816 |
| Normal sink caches | 1,092,021,344 | 1,092,007,080 |
| Form contexts and caches | 578,040,256 | 574,279,648 |
| Authoritative dynamic requests D | 69,419,920 | 74,291,776 |
| Temporary method request stores P | 82,629,328 | 94,050,072 |

These are different failed prefixes, not equal completed closures. Some owner
unions decrease while others increase; neither direction isolates factoring's
effect. The map dense arrays have 38,817,422 allocated cells and 192,661,992
shallow bytes, versus final 41,448,103 cells and 206,959,448 bytes. Occupancy
remains unknown. Both snapshots have the same canonical vocabulary: 3,919
abstract and 17 concrete initials, including zero. That equality is not equal
actor membership, stored state or work.

Active map summary stores borrow 1,053 of the 1,454 distinct profile-B roots;
401 ArrayOwnedMap roots are absent from that selection. As in the final query,
an active-storage retained union cannot establish complete plan payload bytes.
The map review is under
`B/heap-analysis/operations/remaining-five-20261016T100346.967803Z/review-corrected-TB/`.

## Conductor run-1 heap owners

The final factored snapshot is from **run-1, forward, limit 1**. There is no
completed engine or accepted forward report. MAT completes all 42 queries with
CSV results, no query error and an unchanged capture. The reachable heap has
222,494,724 objects and 7,638,870,336 shallow bytes.

| Selected retained owner union | Bytes |
| --- | ---: |
| Caller input/subscription state | 976,651,464 |
| Its positional index alone | 631,424,200 |
| Method-local fact directories | 1,429,934,088 |
| All conclusion buckets, including other stores | 1,555,344,928 |
| Normal call/entry/exit sink caches | 320,745,432 |
| Form contexts and caches | 165,175,816 |

The unions overlap. Conductor's larger method-state owner makes bucket storage
at least as relevant as input-index storage. The 765,512 dense premise rows
contain 13,945,169 allocated cells and 69,561,168 shallow bytes, with unknown
occupancy. An array-only rewrite cannot recover the full method-directory union.
Its empty kind-map shell bound is 179,719,200 bytes; the empty/singleton input
posting-shell bound is 65,649,960 bytes. New representation costs remain unmeasured.

Actors retain 782,749 initial memberships; the global canonical vocabulary has
672 initials: 662 abstract and ten concrete, including zero. There are 1,991,663
caller input groups and 8,903 observer groups; their disjoint role join covers
all accumulators. All eight nonempty profile-B roots are borrowed by a summary
store here, but a retained B union still excludes co-owned payloads.

The exact review is under
`B/heap-analysis/operations/remaining-five-20261016T100346.967803Z/review-final-CDF/`.

The corrected map snapshot is also from **run-1, forward, limit 1**, with no
completed engine or accepted report. Its focused 20-query analysis completes
with 20 CSV results, no query error and an unchanged capture. Its reachable
heap is 7,634,433,232 bytes in 222,417,358 objects.

| Conductor retained union | Map bytes | Final bytes |
| --- | ---: | ---: |
| Caller input/subscription state | 975,584,952 | 976,651,464 |
| Its positional index | 625,863,016 | 631,424,200 |
| Method-local fact directories | 1,419,787,816 | 1,429,934,088 |
| All conclusion buckets | 1,559,728,648 | 1,555,344,928 |
| Normal sink caches | 320,745,928 | 320,745,432 |
| Form contexts and caches | 165,155,640 | 165,175,816 |

These are two failed prefixes, not equal completed closure. The similar owner
pattern identifies what factoring leaves in place; the differences are not a
net saving attributable to factoring. Map dense arrays have 67,484,912 bytes
versus final 69,561,168 bytes. Its canonical vocabulary has 671 abstract and 16
concrete initials. Actor-weighted and exact accumulator-role joins are omitted
from the focused query set and remain unavailable, not zero. All eight profile-B
roots have storage borrowers, with the same co-owned-payload qualification.
Both plans preserve 6,801 logical method rows and 59,948 mark memberships.

The map review is in the same operations directory at `review-corrected-CDF/`.
The focused version is `B/heap-owner-focus-v2/`: exact original query subset,
same runner, source/input/capture bindings, tool heap and execution gates. It
does not change or overwrite the full packet.

## Stirling-PDF run-4 heap owners

The final snapshot is from **run-4, backward, limit 4**, after completion and
acceptance of run-3. Its reachable heap contains 225,161,134 objects and
7,616,506,864 shallow bytes. The focused query set produces 19 CSV results and
one empty selection. The complete class census confirms that no Run1Abstraction
or Run1StaticProfile remains. The empty profile query does not validate field
projections that had no instance to read.

| Selected retained owner union | Final bytes |
| --- | ---: |
| Method-local fact directories | 3,445,904,336 |
| All conclusion buckets, including other stores | 3,846,395,488 |
| Caller input/subscription state | 718,628,168 |
| Its positional index | 532,638,448 |
| Proof, record and demand stores | 639,032,384 |
| Normal sink caches | 151,316,416 |
| Form contexts and caches | 216,549,048 |

These owner unions overlap. Method-state buckets dominate this later failure;
the sole PersistentRecordStore root dominates 583,248,784 bytes. Dense premise
arrays contain 15,514,719 allocated cells and 64,289,736 shallow bytes, with
unknown occupancy. Most of the directory union is therefore outside the array
shells. Replacing dense arrays alone does not address that union.

The canonical vocabulary contains 2,093 abstract and 4,114 concrete initials.
Those counts span the retained run-4 state and cannot test the run-1 mark
hypothesis. The run-1 frozen plan has been released: current empty B and absent
profile objects do not establish its peak footprint or factoring's saved bytes.
Use the setup census for logical membership and expanded-row removal.

The final review is under
`B/heap-analysis/operations/remaining-five-20261016T100346.967803Z/review-final-PDF/`.

The corrected-map snapshot is also from **run-4, backward, limit 4**, after
accepted run-3. Its focused analysis completes with 19 CSV results and one empty
profile selection, consistent with the complete class census. It contains
212,019,279 reachable objects and 7,630,668,696 shallow bytes.

| Stirling-PDF retained union | Map bytes | Final bytes |
| --- | ---: | ---: |
| Method-local fact directories | 3,620,629,784 | 3,445,904,336 |
| All conclusion buckets | 3,377,485,152 | 3,846,395,488 |
| Caller input/subscription state | 469,653,320 | 718,628,168 |
| Its positional index | 332,680,016 | 532,638,448 |
| Normal sink caches | 151,316,008 | 151,316,416 |
| Form contexts and caches | 218,859,472 | 216,549,048 |

These OOM snapshots are different work prefixes, despite sharing a run number.
Map dense arrays have 157,554,655 allocated cells and 637,502,848 shallow bytes,
versus final 15,514,719 cells and 64,289,736 bytes. Their occupancy is unknown.
Dense arrays are therefore not uniformly small across controls. Neither this
drop nor the larger final bucket union is a measured effect caused by factoring.
Both snapshots lack the run-1 profile objects. Map's 2,095 abstract and 7,802
concrete canonical initials span later state, not run-1 input propagation.

The map review is in the same operations directory at `review-corrected-PDF/`.

## Closed memory review and structural bounds

All six required OOM snapshots have completed analysis. The full and focused
packets account for 170 requested queries: 166 CSV results, four verified empty
selections and no failed query. Empty selections do not validate unread field
projections. Focused packets omit other queries; their results are unavailable,
not zero. The full ThingsBoard actor-status projection is also unavailable.
Every analysis has a terminal receipt, an unchanged capture and an archive;
all six owned MAT JVMs are gone.

The source and class census establish the following conservative bounds on
current container shells. MB in the proposal means decimal megabytes.

| Snapshot | Engine at capture | Empty kind-map shells, bytes | Empty/singleton posting shells, bytes |
| --- | --- | ---: | ---: |
| ThingsBoard map | run-1, forward | 230,886,144 | 228,244,248 |
| ThingsBoard final | run-1, forward | 243,116,256 | 238,391,712 |
| Conductor map | run-1, forward | Unavailable | 61,352,928 |
| Conductor final | run-1, forward | 179,719,200 | 65,649,960 |
| Stirling-PDF map | run-4, backward | 366,393,456 | 41,470,632 |
| Stirling-PDF final | run-4, backward | 378,101,616 | 45,725,976 |

Each nonempty kind map needs a base group. Where the complete selected census
has two maps per conclusion group, subtracting all base groups bounds empty
maps; each current map shell has 48 bytes. Corrected Conductor's selected map
census is one below twice its conclusion count, so its bound remains unavailable
under this consistency check. No replacement value is invented.

For the input index, a node with multiple postings needs at least two postings.
At least `nodes - floor(postings / 2)` nodes therefore have zero or one posting.
Their current linked-set/map shells cost 72 bytes per node. These formulas give
gross current costs, not measured cardinality distributions or net savings.
New node layout, remaining collections and other ownership paths still matter.
Do not add these bounds to overlapping retained unions.

Use `B/measurement-review/six-heap-owners-20261016T105655Z/` for the combined
CSV, source-bound derivations, query statuses and limits. Its `receipt.json`
binds the six individual reviews and the footprint borrower join. Operation
closure is `B/heap-analysis/operations/remaining-five-20261016T100346.967803Z/all-six-completion.json`.

## Conclusions and next iteration

The exception repair restores the selected project executions past the old
static-domain failure. The factored plan removes expanded B rows in every
project and preserves logical plan membership. All strict map-to-final finding
comparisons match. These are the demonstrated results of the selected changes.

The wider acceptance targets fail. Completed run-1 takes 288.383 seconds in
OWASP and 189.346 seconds in PDF. Conductor and ThingsBoard complete no run-1
engine. OWASP reaches run-5 and accepts run-3; PDF reaches run-4 and accepts
run-3; the other two reach only run-1 and accept no report. There are 24 strict
missing baseline identities, and the existing eight strict application failures
remain. Equal output against a resource-failed control does not establish recall.

The observed completed run-1 times are longer than the corrected control.
Single observations at different work prefixes do not isolate a causal cost
of factoring. The physical-layout census proves row removal, but the available
retained unions do not establish a saved-byte total. This report claims neither
a factoring speedup nor a measured net memory reduction.

The main memory issue is standing analysis relations and their containers.
Small canonical input vocabularies still produce many method/premise/statement
and caller/input memberships. Current output storage already packs concrete
marks. The current measurements do not establish many different concrete input
marks as the main run-1 cost. Request replay remains a CPU target in Conductor,
but its pending and authoritative stores do not explain the largest heap owners.

Use the [next optimization proposal](../design/bidirectional-next-iteration-05cf9ad62-20261016.md).
It ranks lazy conclusion storage and compact input postings first from supported
container costs. It also designs a narrower algorithmic frame route, bound-rule
cache retention and deferred alias construction. Their eligible shares remain
measurement gates. A footprint or cache-owner total is not a promised saving.
IR storage and unproposed reference-manager changes remain outside this iteration.
