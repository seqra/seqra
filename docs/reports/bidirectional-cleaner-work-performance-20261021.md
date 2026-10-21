# Run-1 cleaner work: performance and findings

Date: 21 October 2026. Status: **complete; finding-quality and full-analysis
performance gates are not met**. The eight fixed runs, strict comparison,
all three OOM heap reviews and all eight JFR analyses are closed.

The selected five optimizations are implemented in
`51dbc4d66fb1bf1b56071dbf18c90c5909e3146b`. The fresh main baseline is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The main checkout has only the
reviewed MemoryManager snapshot hook. Its runtime patch SHA-256 is
`53f0abeeb0c98a16db86b8236b91899020fca14e799ff69a6739addf644b402f`.
Both versions use this hook. Reports from earlier candidate commits are
not measurement evidence for this campaign.

Artifacts are in
`/drive-testcomp/opentaint-go-rules/cleaner-work-20261021`.

## Latest iteration

These are the actual closed-run records. Source and input before/after
equality passed. Reported-location comparison and missing-identity source
classification are complete. Every captured OOM heap and all eight
profile leaves have been analyzed.

OWASP run-1 completed in 267.894522 engine seconds. Its 60-second engine
target failed. The first barrier took 5.587184 seconds; the whole first
iteration took 273.482076 seconds. Runs 2–5 also completed. Run-6 ended
with TIMEOUT. The latest accepted forward report is from run-5.

| Project | Latest reached | Latest COMPLETE | Accepted forward report | Candidate state |
| --- | --- | --- | --- | --- |
| OWASP | run-6 TIMEOUT | run-5 | run-5 | closed |
| conductor | run-2 OOM | run-1 | run-1 | closed |
| thingsboard | run-1 OOM | none | none | closed |
| Stirling-PDF | run-2 OOM | run-1 | run-1 | closed |

| Project | Run-1 engine seconds | Run-1 state | 60-second target |
| --- | ---: | --- | --- |
| OWASP | 267.894522 | COMPLETE | failed |
| conductor | 52.269827 | COMPLETE | passed |
| thingsboard | 439.474332 | OOM | failed |
| Stirling-PDF | 33.442215 | COMPLETE | passed |

The target measures engine time. It excludes prescan and the report
barrier. Thingsboard's recorded engine interval includes its heap dump.

Fresh OWASP main completed with status OK. Its command took 123.210
seconds. It reported 4112 vulnerabilities. All 4112 main locations are
present in the candidate report. Main uses the legacy analyzer, so bidirectional
iteration labels do not apply to it.

The compact run-1 frontier proves the accepted forward report. It logs
151406 demand edges in 12083 method keys and zero remaining frontier
items. Its DEMAND/CONFIRMED counters are 47599/0. These diagnostic counts
are not strict sink-location counts and do not establish finding parity.
The first barrier completed before run-2 began.

| OWASP iteration | Direction | Field limit | Engine seconds | Barrier seconds | State |
| --- | --- | ---: | ---: | ---: | --- |
| run-1 | forward | 1 | 267.894522 | 5.587184 | COMPLETE |
| run-2 | backward | 1 | 224.377761 | 0.629551 | COMPLETE |
| run-3 | forward | 2 | 64.129224 | 3.085517 | COMPLETE |
| run-4 | backward | 3 | 209.235830 | 0.685107 | COMPLETE |
| run-5 | forward | 4 | 61.672038 | 2.237837 | COMPLETE |
| run-6 | backward | 5 | 123.719682 | not entered | TIMEOUT |

The run-5 frontier logs 46 demand edges in 23 method keys. Its
DEMAND/CONFIRMED counters are 9179/0. These remain diagnostic counts,
not a physical sink-location comparison.

| Closed project pair | Main command seconds | Candidate command seconds | Candidate terminal state |
| --- | ---: | ---: | --- |
| OWASP | 123.210 | 1044.577 | TIMEOUT in run-6 |
| conductor | 114.228 | 258.593 | OOM in run-2 |
| thingsboard | 978.449 (TIMEOUT) | 685.333 | OOM in run-1 |
| Stirling-PDF | 59.125 | 521.426 | OOM in run-2 |

These are command times under the recorded diagnostic settings, not an
isolated before/after test of the five changes. The fresh main commands
completed for OWASP, conductor and Stirling-PDF. None of the four candidates
completed its full analysis sequence. All exported finding reports;
strict sink-location results are below. A shorter resource-failed
command is not a completed-analysis speedup.

Thingsboard main ended with TIMEOUT. Its command took 978.449491 seconds
and its peak process RSS was 14,527,393,792 bytes. It exported a finding
report. The candidate entered run-1 at 16:01:00.130 UTC and ended with OOM
after 439.474332 engine seconds. Its run-1 target failed. No COMPLETE
engine or accepted forward report is recorded. The comparison finds all
13 main locations missing from that output. This main report is not a
complete reference analysis.

The configured 1080-second IFDS timeout is unchanged. The legacy
`TaintAnalyzer.fullScan` uses 80% of the time left after prescan, then
reserves time for confirmation. It also allows 30 seconds for runner
cancellation. Thingsboard main entered full scan at 15:43:58.887, logged
its runner timeout at 15:56:17.416 and finished IFDS at 15:56:48.165 UTC.
Thus equal configured timeouts do not mean equal full-engine budgets in
the two cores. This pre-existing budget policy is not changed by the five
optimizations. Source: main `TaintAnalyzer.kt:156` and
`TaintAnalysisUnitRunnerManager.kt:178`.

Conductor run-1 completed in 52.269827 engine seconds. It meets the
60-second engine target. Its barrier took 2.194768 seconds; the complete
first iteration took 54.464971 seconds. Run-2 reached OOM after
103.743137 reported engine seconds. This includes the 19.266-second
logged heap-dump interval. A simple subtraction is not a measurement of
the runtime without a dump.

The conductor heap was captured in run-2, from 15:39:52.197 to
15:40:11.463 UTC. MemoryManager logged 7,748,023,328 used heap bytes and
a threshold of 7,730,941,132 bytes. The dump contains 11,762,948,637
serialized bytes. Dump size is not retained heap size. The retained-owner
analysis is below.

The thingsboard heap snapshot belongs to run-1. Its logged interval is
16:07:48.212 to 16:08:18.940 UTC, or 30.728 seconds. MemoryManager logged
11,699,199,520 used heap bytes and a threshold of 11,596,411,699 bytes.
The dump contains 18,083,091,050 serialized bytes. The reported engine
time includes the dump interval; no runtime without the dump is measured.
The campaign is closed; the retained-owner results are below.

The live run-1 progress controls show 13,121,288 handled events and
597,189 in flight at 16:06:31.798, then 13,752,058 handled and 259,893 in
flight at 16:07:42.715. `InFlight` counts outstanding event/handler reservations,
not logical AP facts. These controls show processing progress during that
interval. They do not prove termination, a unique fact count or the
absence of redundant work.

Stirling-PDF main completed in 59.125480 command seconds. The candidate
completed run-1 in 33.442215 engine seconds, with a 1.962024-second
barrier and a 35.404539-second first iteration. Its run-1 engine target
passes. The run-1 frontier proves an accepted forward report. Run-2 ended
with OOM after 411.140361 engine seconds. The frontier's DEMAND/CONFIRMED
counts, 62345/0, are diagnostic
counts and remain separate from the reported-location comparison below.

The Stirling-PDF run-2 heap interval is 16:17:30.766 to 16:17:54.667 UTC,
or 23.901 seconds. The guard logged 7,765,930,488 used heap bytes and a
threshold of 7,730,941,132 bytes. Its serialized dump is 12,158,378,935
bytes. Its engine time includes the dump; retained-owner results are below.

The campaign controller closed at 16:18:56 UTC after its final source and
input audit passed. The eight leaf manifests and terminal receipts are
bound by `validation/study-closed.json`. No benchmark was restarted.

## Reported sink locations and missing-identity classification

The strict key retains the complete emitted rule ID, verified physical path/span
and full logical instruction coordinate. Only the model-verified URI base is
normalized. All **exported** SARIF location rows produced strict keys; this does
not include records rejected by the analyzer before export. No rule, return
site, synthetic method or severity was removed to obtain a match.

| Project | Main unique | Candidate unique | Main present | Missing | Added | Accepted report / latest reached |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| OWASP | 4,112 | 4,112 | 4,112 | 0 | 0 | run-5 / run-6 TIMEOUT |
| conductor | 7 | 1,902 | 7 | 0 | 1,895 | run-1 / run-2 OOM |
| thingsboard | 13 | 0 | 0 | 13 | 0 | none / run-1 OOM |
| Stirling-PDF | 17 | 1,656 | 15 | 2 | 1,641 | run-1 / run-2 OOM |

Candidate occurrence counts are 9,179, 12,579, 0 and 61,820 respectively;
repeated occurrences are not distinct locations. OWASP and conductor include
all reported main identities. Every candidate analysis sequence remains
incomplete, and Thingsboard main also timed out. Reported-identity inclusion
therefore does not establish full-analysis equivalence or a no-FN certificate.
Added locations remain candidates, not automatically false positives.

All **15 missing identities have a source-based classification**; their strict
failures remain recorded. [The detailed review lists every identity, raw
coordinate, source snippet and source hash](/drive-testcomp/opentaint-go-rules/cleaner-work-20261021/validation/fresh-main-missing-location-review.md),
with [machine-readable controls](/drive-testcomp/opentaint-go-rules/cleaner-work-20261021/validation/fresh-main-missing-location-review.json).

Thingsboard's 13 omissions follow run-1 OOM after 439.474332345 engine seconds,
with no COMPLETE engine or accepted forward report. `IterationDriver` returns
before report admission on an incomplete engine, leaving the initially empty
`ReportBuilder` unchanged. Actual export controls record zero vulnerabilities
and zero trace-generation attempts. This explains the empty output; it does
not prove that any individual missing flow would be found by a completed run.
All 13 rows retain classification `NO_ACCEPTED_RUN1_REPORT_RESOURCE_FAILURE`.

PDF completed and accepted run-1 in 33.442214573 engine seconds, then reached
run-2 OOM after 411.140361003 engine seconds. Its two omissions are different
return coordinates for emitted rule `java.security.xss#sink#1`:

| Method | Main return | Candidate same-rule/full-method return |
| --- | --- | --- |
| `ConvertEmlToPDF.convertEmlToPdf(EmlToPdfRequest)` | IR 103, `return %54`, lines 133–134, span 133:17–134:77 | IR 126, `return %89`, final catch at lines 148–149, span 148:13–149:84; 74 occurrences |
| `PipelineController.handleData(HandleDataRequest)` | IR 50, `return %104`, lines 90–91, span 90:17–91:93 | IR 109, `return null`, final catch at line 130, span 130:13–130:24; 74 occurrences |

The actual source distinguishes the returns: the main ConvertEmlToPDF branch
returns `errorMessage`, while the candidate-selected final catch returns a
constant error response. Main Pipeline returns file bytes, while the
candidate-selected final catch returns null. The full signatures and exact
paths under `app/core/src/main/java/` remain in the linked controls. A further
Pipeline coordinate, IR 14 at line 67, is retained as an extra and is not used
as a match for main IR 50.

The candidate records exit sinks at the synthetic boundary, then exports
`Simple` without the witness's return-specific support. With no relevant trace
locations, the unchanged resolver selects the method's last real return. This
explains the concrete reporting-coordinate defect. Main traces end at IR 103
and IR 50; the candidate exports no code flows. Equal emitted rule ID and full
method do **not** prove coverage of either original path or specialized binding.
Both cases retain classification
`REPORTED_LOCATION_REGRESSION_EXACT_MAIN_FLOW_COVERAGE_UNPROVED`, and both
strict misses stay failed.

This reporting mechanism predates the five selected optimizations:
`JIRBidiAnalysis`, `SarifGenerator`, `JirSarifGenerator` and `TraceMessageBuilder`
are byte-identical between pre-cleaner candidate `5d3a706cf` and current
`51dbc4d66`. Do not attribute this existing exporter limitation to the five
changes, or infer fixed-point equivalence from the failed resource-limited runs.

PDF separately attempted 62,345 Simple exports: 61,820 succeeded and **525
virtual method-exit locations were rejected before SARIF export**. Neither of
the two real methods above occurs in that rejection census. These 525 records
are a separate export gap; `generationFailed=0` counts trace generation and does
not negate location-resolution failures. They do not justify waiving the two
strict missing return locations. [The rejection controls preserve every
rejected-line identity/hash](/drive-testcomp/opentaint-go-rules/cleaner-work-20261021/validation/fresh-main-missing-location-review/Stirling-PDF-export-controls.json).

The first comparison packet stopped before parsing an oversized report at
128 MiB. The separate 256 MiB packet changed only that cap and its error text;
61 pure controls passed. Original reports, the original packet and its failed
attempt remain preserved. Nothing was truncated, and no explanatory alias was
applied to strict matching. Evidence: `validation/fresh-main-comparisons-256m/`
and the review controls linked above. The finding-quality gate remains unmet.

## Implemented change

The [implementation specification](../design/bidirectional-cleaner-intermediate-work-implementation-20261021.md)
defines all five changes. No new analysis approximation is selected.

| Change | Current implementation |
| --- | --- |
| Static continuation storage | Keep one complete context/header row. Store exact image tries separately for each pending group. Keep scalar reply transfer. |
| GROUP return cleaners | Execute the first eligible base/group operator in each block and invocation suffix. Keep every raw step address and barrier. |
| Intermediate cleaner results | Remove equal completed run-1 step results before the next step. Keep distinct premises, facts and identity values. |
| Immutable mark exclusions | Reuse unchanged headers and cache whether their retained atoms include FULL marks. Keep catalog checks and normalization. |
| Empty static metadata | Keep the original summary in the local empty-to-empty fallback. Keep statement transfer and its effects. |

Field transfer, rule admission, mark projection, layers, initial shapes,
static supply, source/sink identities and later refinement retain their
existing contracts. Internal IDs, duplicate callback counts and batching
can differ. The required equivalence concerns complete fixed points;
resource-limited event prefixes can differ.

This preservation claim is scoped to `5d3a706cf` versus `51dbc4d66`.
Earlier group/static changes are already in the reference runtime.
The fresh legacy-main comparison measures the resulting analyzer as a
whole; it is not a semantic equivalence test of the five changes alone.

## Validation

| Gate | Passed | Failures | Skipped |
| --- | ---: | ---: | ---: |
| Complete common dataflow suite | 1690 | 0 | 0 |
| Complete JVM dataflow suite | 438 | 0 | 0 |
| Selected application integration | 38 | 0 | 0 |
| Query rule compiler and bidirectional examples | 38 | 0 | 3 |

There are 2204 executed passes. The three skipped query controls require
`OPENTAINT_TEST_BIDI=true`. They were not enabled in this gate.

The new controls compare packed static images with the prior scalar
transfer, check every finite cleaner suffix, preserve raw action/source
IDs, compare late static answers with scheduling enabled and disabled,
and check request sets after nonadjacent duplicate field images. They
also check later FULL execution, catalog boundaries, retained-atom
metadata, static metadata fallback and actor release.

The local Lean model and its 26-declaration axiom audit pass. The audit
has no `Classical.choice`, `sorryAx` or new local axioms. These are local
storage and transfer laws. They do not prove whole-engine equivalence or
finding parity on real projects.

A separate source review of the six production files found no concrete
new analysis approximation or valid-program semantic defect. It checked
the complete context/group relation, suffix scheduling, nullable identity,
catalog boundaries and empty-summary fallback. The scope is valid service
protocol executions and eventual observations. It does not establish
whole-engine equivalence or equal resource-limited reports. Evidence:
`validation/semantic-preservation-review.md`.

The accepted 13 files match the implementation commit. Evidence:
`tests/acceptance-before-commit.json`, archived gate logs/XML and
`implementation-receipt.json` in the artifact directory.

## Fixed workload

Use the direct Gradle `runProjectAnalyzer` task, Java 17, the configured
models/rules and the conductor extensions. Static handling is enabled:
`opentaint.bidi.disableRun1Statics=false`.

Candidate runs also enable the existing `formStatistics` and
`optimizationStatistics` diagnostics. Main has no matching new-core
counter implementation. The counter observer cost is not isolated in this
campaign; do not describe these timings as uninstrumented production
times.

| Project | Analyzer heap | IFDS timeout | Outer timeout |
| --- | ---: | ---: | ---: |
| OWASP | 8g | 1000 s | 1200 s |
| conductor | 8g | 1080 s | 1200 s |
| thingsboard | 12g | 1080 s | 1200 s |
| Stirling-PDF | 8g | 1080 s | 1200 s |

The eight main/candidate runs are serial. The harness captured continuous
JFR and requested 30-second CPU profiles at full-analysis start +60 seconds
and near the IFDS limit, while the workload is active. Profile names do
not establish which iteration they cover. The profile analysis maps each
capture to its actual phase.

The external benchmark copy increases the JFR rolling cap from 256m to
2g. This is a diagnostic change. It does not change analyzer memory/time
limits. Retained ranges and selected event counts were validated.
The MemoryManager hook captured a live heap before cancellation. A JVM
OOM can create a separate dump. Snapshot time must be distinguished from
engine work when the logs permit it.

Before the campaign, 40 completed prior-study captures and MAT indexes
were removed, freeing 73,348,974,064 bytes. Compact analyses, logs,
finding reports and capture receipts remain. Cleanup checked the shared
locks, prior closure records and readable process FD tables. System and
other-user FD tables can be inaccessible; that observation limit is
recorded in `preparation/prior-raw-cleanup-completed-2.json`.

## CPU, allocation and synchronization

All eight main/candidate profile leaves have closed. All 56 selected
JFR summary/export event-count checks match. Retained chunk-header ranges cover every
candidate engine interval in the table. These are marker-binned windows;
exact logged engine times are given above. Whole-recording main/candidate controls are below.

Allocation values are estimated sampled weight in decimal GB, not object
counts, retained bytes or removable work. Java samples are not calls or
CPU seconds. GC pause union counts overlapping stop-the-world intervals
once. Collection elapsed sums include concurrent/overlapping collections
and can exceed engine time. They are not GC CPU or a stall fraction.

| Project | Engine | Java samples | Allocation weight GB | GC pause union s | Summed collection overlap s |
| --- | --- | ---: | ---: | ---: | ---: |
| OWASP | run-1 | 11686 | 568.980 | 5.141 | 102.604 |
| OWASP | run-2 | 10668 | 602.672 | 6.291 | 154.355 |
| OWASP | run-3 | 2230 | 102.273 | 2.473 | 21.734 |
| OWASP | run-4 | 7544 | 404.945 | 7.709 | 149.722 |
| OWASP | run-5 | 2088 | 100.038 | 2.742 | 14.770 |
| OWASP | run-6 | 4286 | 243.027 | 4.868 | 79.936 |
| conductor | run-1 | 9425 | 216.068 | 3.711 | 39.293 |
| conductor | run-2 | 15478 | 382.672 | 37.844 | 130.420 |
| thingsboard | run-1 | 57913 | 831.507 | 65.003 | 423.399 |
| Stirling-PDF | run-1 | 5841 | 113.828 | 1.890 | 10.797 |
| Stirling-PDF | run-2 | 102004 | 2589.339 | 101.021 | 480.439 |

**Thingsboard run-1:** static-service ancestry has 24,205/57,913 Java
samples (41.80%); answerClass has 19,551 (33.76%). Intersect has 13,712
exact-leaf samples (23.68%). These rows overlap. The selected exact-class
versus field loops cannot match under current path/tail invariants.
They are a concrete source of useless comparisons. Genuine broad/class
and read/writer matches remain; the profile does not measure the share
of each loop or make 33.76% a removable-work estimate.

The service commit stacks account for 2557.346 recorded blocked-thread
seconds, 98.68% of run-1's monitored blocked duration. This is elapsed
waiting summed across concurrent threads, not wall time, CPU or lock
hold time. Shortening work under the existing lock is the first
run-1 candidate. No lock or synchronization rewrite is proposed.

**OWASP run-1:** runtimeFact ancestry has 2683/11686 Java samples (22.96%)
and 204,601,255,600/568,979,695,536 allocation-weight bytes (35.96%).
cleanChain ancestry has 40.89% of Java samples and 51.54% of allocation
weight; these overlap projection. The source repeats GROUP projection
at several boundaries. Reuse and a narrowly proved redundant-boundary
skip are candidates. A blanket run-1 shortcut is invalid: raw source
layers, generic FULL values and receiving-manager interning still need
normalization. The profile does not identify a fast-path hit rate.

**Conductor run-2:** addLink ancestry has 4840/15478 Java samples (31.27%)
and 31.14% of allocation weight. QueuePublications has 18.69% of Java
samples; flush/publication admission has about 18.9%. These overlap.
**PDF run-2:** addLink has 41,408/102,004 samples (40.59%) and 34.34% of
allocation weight. Raw-summary entries are the exact leaf in 6444
samples (6.32%). Publication/flush ancestry is about 33.8%. These are
consistent with the source path that expands old raw answers again on
unrelated query admission, then deduplicates after actor flush.
The heaps independently show 2.35 million and 25.17 million pending
entries. No artifact separates their new-delta and replay populations
or proves a duplicate percentage.

Later FULL work differs from GROUP work. RuntimeFact has only 2 named
ancestry samples in conductor run-2 and 22 in PDF run-2. Run-1 projection
is not the explanation for those OOMs. PDF also records 165.240
blocked-thread seconds at SummaryStorage.candidates. These observations
support avoiding repeated work through existing owners, rather than
changing mark identities, cache managers or locking contracts.

The code still builds forms. OWASP raw return-rewriter lowering has
156 run-1 Java ancestry samples (1.335%) and 1.764% of allocation weight.
A FormsCache callback scope includes its caller's ordinary work; it is
not a timer for building forms. JIT inlining can hide schedule frames;
zero recorded execute-tag matches do not prove the schedule never runs.

The closed OWASP driver reports 229,561,974 form-cache accesses,
227,753,386 hits and 1,808,588 successful builds across runs 1–6. It does
not separate first builds from rebuilds. The summed 281.273 build seconds
can overlap across threads/nested calls and include the enabled shape
census. They are not exclusive CPU, run-1 time or retained object counts.
Stable action/rule/temporary identities survive payload rebuilds.
Evidence: `validation/form-cache-work-source-analysis.md`.

### Native CPU windows and diagnostic tails

| Project | Window | Nominal engine overlap | All CPU sample weights | GC-native family |
| --- | --- | --- | ---: | ---: |
| OWASP | normal | run-1 | 8982 | 62.83% |
| OWASP | near-limit | run-6 | 13905 | 78.88% |
| conductor | normal | run-2 | 36097 | 41.91% |
| thingsboard | normal | run-1 | 22499 | 53.73% |
| Stirling-PDF | normal | run-2 | 37451 | 37.12% |

These counts use the saved consistent generic predicates. They supersede
ad-hoc preliminary frame-family counts. Java-only JFR and GC-inclusive
native percentages have different denominators. Inclusive families
overlap. Each nominal interval is 30 seconds, derived from rounded
manifest start plus harness elapsed time. Actual attach delay/start/end
are not measured; collapsed stacks have no per-sample timestamps.
No finite attach-delay bound or exact per-sample phase assignment is
claimed.

Only OWASP reaches the near-limit capture trigger. It belongs nominally
to run-6 and ends about 29.596 seconds before END; it is not the engine's
final-30 window. Conductor, thingsboard and PDF close before their
configured near-limit triggers, so those native windows are unavailable.
Do not substitute a normal or different-project capture.

The final-30 JFR windows overlap the heap-dump intervals by 19.266 seconds
for conductor, 29.336 for thingsboard and 23.901 for PDF. They contain
only 142, 59 and 266 Java samples respectively. These tails include
capture/GC/termination and are unsuitable as steady-state comparisons.
No counterfactual runtime is obtained by subtracting dump time.

The candidate recordings have no recorded DataLoss event, marked stack
truncation or print-depth-256 hit. Some allocation events lack frames;
they remain in the denominators. Header coverage does not prove uniform
sampling, restore inlined/omitted frames or establish complete command
capture outside retained bounds. Per-method displays are top 40, not a
complete attribution. Allocation ancestry is not retained ownership.

Evidence: `validation/profile-analysis/*-bidi/analysis.json` and the
closed `validation/OWASP-profile-review.md`,
`conductor-profile-review.md`, `thingsboard-profile-review.md` and
`Stirling-PDF-profile-review.md`, with their source-bound JSON controls.

### Closed profile controls

The serial reader sweep ended with `FIXED8_PROFILE_READERS_CLOSED`.
One summary and one depth-256 event export per recording completed;
the same streaming pass computed generic and source-focus attribution.
All recorded reader processes closed. Validated expanded event JSONs
were removed with hash/count disposition receipts; original recordings
and failed diagnostic outputs remain. No analyzer was rerun.

| Project | Side | Retained range s | Java samples | Allocation weight GB | GC pause union s |
| --- | --- | ---: | ---: | ---: | ---: |
| OWASP | bidi | 1041.106 | 44845 | 2189.425 | 31.401 |
| conductor | bidi | 254.303 | 35075 | 789.238 | 45.736 |
| thingsboard | bidi | 678.661 | 98979 | 1386.570 | 74.924 |
| Stirling-PDF | bidi | 517.725 | 116154 | 2842.160 | 105.988 |
| OWASP | main | 120.854 | 9832 | 221.376 | 2.788 |
| conductor | main | 110.296 | 12224 | 201.835 | 4.758 |
| thingsboard | main | 974.529 | 117009 | 559.482 | 9.975 |
| Stirling-PDF | main | 56.377 | 7279 | 129.234 | 2.134 |

These are whole retained recordings, including setup/prescan, engines,
barriers and reporting/termination. They have different completed work
and durations; their totals are not a per-operation or causal five-change
comparison. All actual prescan, candidate-engine, legacy-fullscan and
IFDS marker windows have observed header coverage. No selected event
stream has recorded DataLoss, marked truncation or a print-depth hit;
allocation events with missing frames remain in each denominator.

Main OWASP/conductor/PDF fullscan intervals are 47.254/33.299/5.064 seconds,
so they end before the +60-second native trigger. Main thingsboard has
a normal native capture in legacy fullscan (3091 weights). No main leaf
reaches its near-limit capture trigger. Absence is unavailable native
evidence, not zero work. Per-leaf analysis preserves every actual phase
and native overlap. Main has no bidirectional run labels.

Evidence: `validation/profile-analysis/sweep-receipt.json` and
`validation/final-profile-summary.json`. The latter binds all eight
analysis hashes, labels, coverage, event checks and missing-stack counts.

## Conductor run-2 retained heap

The pre-cancellation snapshot has 231,334,187 reachable objects and
7,622,277,664 shallow bytes after MAT removes unreachable objects. These
are snapshot measurements, not counts of logical facts or operations.
The latest reached iteration is run-2 OOM; run-1 is COMPLETE and its
forward report is accepted.

| Owner union | Retained bytes | Interpretation |
| --- | ---: | --- |
| MethodEdgeStore | 1,413,384,416 | Per-statement conclusion storage and its reachable values |
| Ordinary caller/publication/request owners | 614,538,288 | Shared subscription and request data; not an exclusive queue total |
| Raw summary stores | 441,747,968 | Raw premise/conclusion data |
| Current demand/query owners | 187,482,944 | Later-run admission and guidance |
| Persistent normal records | 36,330,920 | Normal records retained across iterations |
| Mark interner | 353,608 | 38 groups, 737 full-mark keys and 776 atoms |
| Retained cleaner actions/schedules | 8,656 | Current retained forms only; not cumulative construction |

These retained unions overlap. Do not add them. The largest individual
dominator is IR relationship storage at 2,254,513,952 retained bytes.
IR storage changes remain out of scope.

Within the MethodEdgeStore union, MAT finds 3,191,016 ConclusionGroup
objects, 6,382,032 HashMaps, 6,738,636 ArrayLists and 6,772,934 Object
arrays. Their respective map, list and array shallow sizes are
306,337,536, 161,727,264 and 181,826,240 bytes. This is substantial
container overhead. It supports a separate representation proposal;
it does not establish that the represented facts are redundant.

The complete actor directory has 2,348,062 pending publication entries,
along with 240 unchanged-work, 662 zero-work and 65,046 other-work
entries. Pending publications are separate from the named worklist and
channel union. That union's 17,023,848 bytes must not be described as
all queued memory. The pending entry count is not a distinct-fact count,
a duplicate count or a retained-byte total.

The source-confirmed summary replay path can offer old answers again
before global deduplication. The large pending publication population
makes it a high-priority work-avoidance candidate. An exact saving still
requires measuring duplicate replay membership. Run-1 service/point
classes have zero reachable instances in the complete histogram; this
run-2 snapshot does not retain the run-1 static-service population.

All 27 query outcomes were reviewed. Fourteen have complete CSVs; two
are top displays, two have blank fields, two are empty/unavailable and
seven have no CSV. Unavailable fields are not zero. Evidence:
`validation/heap-tools/jobs/job-conductor-20261021T162238.657975Z/`
and its `query-outcomes.json` and reviewed CSVs.

## Thingsboard run-1 retained heap

The run-1 OOM snapshot has 344,995,130 reachable objects and
11,408,431,664 shallow bytes. No iteration or forward report completed.
These owner unions overlap and must not be added:

| Owner union | Retained bytes |
| --- | ---: |
| Static service and method continuations | 2,552,172,992 |
| Ordinary caller/publication/request owners | 707,749,816 |
| MethodEdgeStore | 570,328,816 |
| Raw summary stores | 496,992,200 |
| Ordinary predicate/conjunction owners | 343,007,816 |
| Named worklist and channel owners | 108,815,928 |
| Trie interners | 24,315,512 |
| Mark interner | 459,472 |

The complete mark table has 42 groups, 934 full-mark keys and 977 atoms.
These counts do not support runaway mark-key creation. The largest
individual dominators include IR relationship storage (1,625,559,704 B),
SpringRuleProvider (1,282,528,280 B), Run1StaticService (1,183,869,872 B)
and JIRMethodEntries (722,531,792 B). Each is a dominator observation,
not an additional disjoint owner union. IR changes remain out of scope.

The service directory has 111 write regions, 88 canonical field states,
42,216 read descriptors and 995,524 FLOW writer rows. Small region and
group populations coexist with large per-context/per-operation storage:

| Snapshot population | Entries |
| --- | ---: |
| Field-state pending rows | 2,485,236 |
| Method writer continuations | 1,176,625 |
| Method writer inputs | 2,073,708 |
| Packed point rows | 600,825 |
| Packed point pending group entries | 1,147,001 |
| Call continuations | 326,440 |
| Read continuations | 79,512 |
| Conditional/join input rows, each directory | 27,544 |
| Actor pending static supplies | 2,102,252 |
| Actor pending static interests | 545,295 |
| Actor pending static writes | 210,770 |
| Actor pending static writer registrations | 178,265 |
| Actor pending publications | 169,374 |

These are separate directory/list populations. Some describe the same
logical work at different stages; do not sum them into a fact count.
The 32,721,164 admitted writer-group memberships are another relation,
not distinct writer rows. Cumulative terminal counters are not used as
snapshot populations. In particular, the terminal reply total differs
from the pre-cancellation snapshot.

This snapshot establishes a large static auxiliary population. It does
not establish the number of redundant contexts or a duplicate fraction.
This is request, writer and continuation routing. It does not imply that
ordinary static CFG propagation has returned in run-1.
The existing static-image packing covers only the final points branch;
writer, writer-input, call, read and join continuations remain separate
by the selected implementation contract. The actor's temporary static
batch is also distinct from globally admitted service state. Avoiding
duplicate batch work before submission is a candidate for the next design.

All 27 query outcomes were reviewed: 20 complete CSVs, two top displays,
one sample, one blank/invalid table, two empty/unavailable results and
one absent CSV. Packed-point counts are complete scalar counts; they are
not path counts or per-group Cartesian products. Evidence:
`validation/heap-tools/jobs/job-thingsboard-20261021T163132.484815Z/`.

## Stirling-PDF run-2 retained heap

The run-2 OOM snapshot has 236,485,411 reachable objects and
7,573,258,456 shallow bytes. Run-1 is COMPLETE and its report is accepted.
The owner unions below overlap and must not be added:

| Owner union | Retained bytes |
| --- | ---: |
| MethodEdgeStore | 2,455,015,616 |
| Ordinary caller/publication/request owners | 821,915,272 |
| Raw summary stores | 713,081,952 |
| Current demand/query owners | 84,113,288 |
| Named worklist and channel owners | 35,027,592 |
| Persistent normal records | 9,357,536 |
| Mark interner | 452,536 |

The complete mark table has 42 groups, 883 full-mark keys and 926 atoms.
The actor directory has **25,171,733 pending publication entries**,
209,417 other-work entries, 482 unchanged-work entries and one zero-work
entry. These pending publications are separate from the roots used for
the named queue union. Neither that union nor a pending list length
measures the exclusive retained size of all publication payloads.

This is a much larger replay/pending population than the 2,348,062
conductor run-2 entries. The source-confirmed old-answer replay path is
therefore the leading work-avoidance candidate for both later-run OOMs.
The heap establishes the population, not which entries are duplicate or
which exact fraction the candidate will remove. Large per-statement
containers are a separate, measured representation cost.

The backlog spans 538 of 7258 actors. The largest actor has 3,710,150
entries; the largest ten hold 52.37% of the total. The full histogram has
25,172,049 physical Publication objects using 604,129,176 shallow bytes.
Physical objects and pending references are different populations;
neither count establishes logical duplicates. Principal run-1 service
and point families have explicit zero instances in the histogram.

All 27 query outcomes were reviewed: 14 complete CSVs, two top displays,
two blank/invalid tables, two empty/unavailable results and seven absent
CSVs. As in conductor, absent run-1 directories are not evidence of a
retained run-1 static leak. Evidence:
`validation/heap-tools/jobs/job-Stirling-PDF-20261021T164815.234670Z/`.

## Result and next iteration

The implementation/test gate passes. The fresh workload does not meet
the requested overall quality/performance outcome: only conductor and
PDF meet the run-1 engine target; every candidate full sequence ends
with OOM or TIMEOUT. Strict main-location inclusion fails for
thingsboard and PDF. All 15 missing identities have source-based
classifications; extra identities remain candidates, not proven FPs.

The main comparison shows worse completion/resource behavior for this
core on these fixed workloads. It does not isolate the performance
effect of the five optimizations against their same-core prepatch.
No causal speedup or regression percentage for those five is measured.

The evidence explains different costs: thingsboard run-1 spends
substantial work on static request/continuation routing; OWASP run-1
allocates heavily under GROUP projection; conductor/PDF run-2 have
large summary publication backlogs and per-statement container costs.
Small mark interner populations do not support runaway mark-key growth.
Completed run-1 engines and thingsboard's live progress controls show
processing progress, but no whole-engine termination theorem or infinite
loop diagnosis follows from these measurements.

The [ranked next-iteration proposal](../design/bidirectional-work-avoidance-next-iteration-20261021.md)
starts with source-proven impossible static class/field matches, then
GROUP projection reuse, later-run permission-transition summary catch-up,
lazy conclusion containers and diagnostic-gated supply batching. It
states current versus new behavior, examples, expected effects, limits
and proof obligations. Bulk supply packing retains an unresolved
class/exclusion effect proof and is not ready for implementation.
The actual return-location correctness fix is separate and unselected.

All proposed optimizations retain the current analysis contracts.
The current runtime remains `51dbc4d66`; no additional core, IR, cache
manager, reference policy, field, mark, layer or approximation change
was made during the investigation.
