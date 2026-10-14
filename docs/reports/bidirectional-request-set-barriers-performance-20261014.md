# Request sets and method barriers: fixed-project performance

Date: 14 October 2026. Status at 13:28:34 UTC: measurements in progress. This draft contains
completed main and control evidence, the completed optimized OWASP result and implementation
checks. The other optimized results, profile and heap reviews, and final conclusions are pending.

The tested optimization is request mark sets plus a method processing barrier. The implementation
commit is `a23ad7fcd99553190c82557cff6076503959aec6`. It preserves independent source and sink
conditions and full request origins. Position requests keep their scalar path. A nonzero Work
quantum can service a bounded request chunk when the method remains busy.

## Revisions, fixed inputs and checks

The comparison has three runtimes:

| Runtime | Revision | Purpose |
| --- | --- | --- |
| Main | `f945f92c5ae554ff4f5fc7696ee5a269210478bb` | Saved `origin/main`; legacy analyzer |
| Control | `61fd97acd792f2200b2e4a5746185d740d87712b` | Fresh preoptimization bidi runtime; core equals the user task HEAD `e1d517ced` |
| Optimized | `a23ad7fcd99553190c82557cff6076503959aec6` | Committed request sets and method barriers |

Main has the opt-in memory-guard capture hook. Its algorithm and options are unchanged. The local
`main` branch is older than the saved `origin/main` revision. The exact measured SHA above defines
this baseline. Reports from earlier measurement batches are historical records.

All projects use Gradle `runProjectAnalyzer` directly, Temurin 17, storage batch 2,000 and disabled
multi-release loading. The prepared project models, classes, dependencies, rules and extensions
are fixed across runtimes. OWASP uses the cached release-8 project with the CI analysis options.
The comparison does not include a fresh CI autobuild.

| Project | Maximum heap | IFDS budget | Outer budget |
| --- | ---: | ---: | ---: |
| OWASP | 8 GiB | 1,000 s | 1,200 s |
| Conductor | 8 GiB | 1,080 s | 1,200 s |
| ThingsBoard | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | 8 GiB | 1,080 s | 1,200 s |

The committed sources match the passing validation manifest: 1,370 common-core tests and 377 JVM
tests, with zero failures, errors or skips. Go, JVM SAST and root test sources compile. The 107
focused tests pass. The complete NNF iteration fixture reaches and completes **run-3, forward,
limit 2**, after limits `[1, 1, 2]`. It compares full witnesses and support, source hits, summaries,
native records and the final report against an independent scalar reference.

The Lean build and audit pass: 19,472 declarations and zero unexpected axioms. The new proofs
cover local request-frontier and Work completion laws. The complete engine remains outside their
proof scope. The reporting tools pass 36 tests.

Before the batch, cleanup removed 75 approved old capture, export and MAT sidecar files,
totaling 101,012,364,052 bytes. Compact historical evidence remains. The fresh input audits pass:
1,841 model-selected dependency files and 77 checks over 41 captured datasets. The final audit
will also compare all terminal run manifests and actual JVM arguments.

Evidence root:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/request-set-barriers/20261014T105250Z
```

`implementation-commit.json`, `checks/full/20261014T115931.311324Z`, `input-audit`, phase launch
manifests and each project's process record bind the inputs and runtime. Builds, scans and later
heap/profile processing use the shared serial locks. The phase launcher preserves each run's
output directory and records terminal status separately from Gradle exit status.

## Main baseline

All four fresh main runs are terminal. Main uses legacy phases and has no bidi run-N, direction
or field limit. The three successful runs define complete exported baselines. ThingsBoard's
timeout gives an incomplete exported baseline of 13 unique sink identities.

| Project | Main outcome | Strict unique sinks / occurrences | Prescan | Full scan | IFDS | Overall wall |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | Complete | 4,112 / 4,112 | 42.933 s | 62.103 s | 105.038 s | 155.597 s |
| Conductor | Complete | 7 / 7 | 32.289 s | 56.199 s | 88.491 s | 137.297 s |
| ThingsBoard | Timeout | 13 / 13 | 232.529 s | 709.820 s | 942.358 s | 1,031.290 s |
| Stirling-PDF | Complete | 17 / 17 | 34.994 s | 10.999 s | 45.996 s | 125.463 s |

Trace generation is inside the full-scan and IFDS intervals. Overall wall includes Gradle and
output. Main has no OOM capture. Main ThingsBoard assigns the full scan 80% of the remaining
IFDS budget. This differs from the bidi driver's remaining budget and must be included when
comparing timeout timing.

## Iteration progress and strict findings

All four fresh controls and optimized OWASP are terminal. Optimized Conductor is active.
Run-1 starts at limit 1. Completion and report acceptance have separate evidence.

| Runtime / project | Latest reached | Last complete engine | Last accepted forward report | Status |
| --- | --- | --- | --- | --- |
| Control / OWASP | run-3, forward, limit 3 | run-3, forward, limit 3 | run-3, forward, limit 3 | Complete |
| Control / Conductor | run-2, backward, limit 2 | run-1, forward, limit 1 | run-1, forward, limit 1 | OOM |
| Control / ThingsBoard | run-1, forward, limit 1 | None | None | OOM |
| Control / Stirling-PDF | run-4, backward, limit 4 | run-3, forward, limit 3 | run-3, forward, limit 3 | Timeout |
| Optimized / OWASP | run-3, forward, limit 3 | run-3, forward, limit 3 | run-3, forward, limit 3 | Complete |
| Optimized / Conductor | run-2, backward, limit 2 | run-1, forward, limit 1 | run-1, forward, limit 1 | Engine active |
| Optimized / ThingsBoard, Stirling-PDF | Not started | None | None | Pending |

OWASP control preserves all 4,112 main strict identities and their location sets. It exports
9,179 occurrences, with 5,067 extra occurrences at existing identities. Multiplicity changes
occur at 2,420 identities. There are zero strict missing or added identities and no paired-input
differences. The actual source and sink report has 9,179 CONFIRMED and zero DEMAND entries.

Its overall wall time is 584.296 seconds. Run-1, forward, limit 1 takes 303.950 seconds in the
engine and 1.554 seconds at the barrier. Run-2, backward, limit 2 takes 60.722 and 0.142 seconds.
Run-3, forward, limit 3 takes 61.587 and 0.107 seconds. These are fresh control measurements.

Optimized OWASP completes and accepts **run-3, forward, limit 3**. It preserves all 4,112 main
strict identities and their location sets. Its 9,179 occurrences also match the fresh control,
with zero identity, location or multiplicity changes between control and optimized output.
The paired input checks report no differences. Its overall wall time is 637.508 seconds and
IFDS takes 560.651 seconds. The bidi engines and report barriers take 494.651 seconds together.

| OWASP iteration | Control engine | Optimized engine | Control report barrier | Optimized report barrier |
| --- | ---: | ---: | ---: | ---: |
| run-1, forward, limit 1 | 303.950 s | 336.977 s | 1.554 s | 1.696 s |
| run-2, backward, limit 2 | 60.722 s | 81.042 s | 0.142 s | 0.165 s |
| run-3, forward, limit 3 | 61.587 s | 74.618 s | 0.107 s | 0.148 s |

The optimized OWASP engines are slower in this fresh pair. The overall wall time increases
from 584.296 to 637.508 seconds. These are single measurements on a shared host with diagnostic
statistics enabled. Profile review is pending, so they do not yet identify the cause of the
increase. Evidence is in `optimized-review/OWASP-main-comparison.json`,
`optimized-review/OWASP-control-comparison.json` and `projects/OWASP/bidi`.

The control launcher stopped at the next project boundary because available RAM fell below its
20 GiB scheduling floor. Three proven idle owned build daemons were released. A new readiness
check passed, and the same phase resumed at Conductor. The completed OWASP result was retained;
the analyzer heap limits, timeout values and scheduling floor are unchanged. Shared-host pressure
and this gap will remain visible in the final timing assessment.

Conductor control preserves all seven main identities and their locations. It exports 300 sites,
with 293 added identities. Run-1 accepts three CONFIRMED and 298 DEMAND entries. The exporter
rejects one NATS `createDispatcher` lambda location, so 301 attempted entries yield 300 results.
This rejection removes none of the seven main identities. The added sites are unresolved because
the backward engine stops with OOM.

Its overall wall time is 531.970 seconds; IFDS takes 428.195 seconds. Run-1, forward, limit 1 takes
70.805 seconds in the engine and 1.838 seconds at the barrier. Run-2, backward, limit 2 takes
307.476 seconds, including a 45.741-second pre-cancel heap capture. The serialized guard heap is
11,763,718,066 bytes. Its root-owner review is pending; file size is not retained heap size.

Optimized Conductor has completed and accepted run-1, forward, limit 1. Its engine takes
75.939 seconds. It has reached run-2, backward, limit 2, which is active at this draft's
status time. Its terminal output and strict finding comparison are pending.

ThingsBoard control stops with OOM in run-1, forward, limit 1. It never accepts a forward report
and exports a valid empty SARIF file. All 13 identities in the incomplete main output are absent.
The first-report failure explains this output loss; it does not identify 13 separate transfer
defects. Its IFDS time is 872.502 seconds, including 249.029 seconds of prescan. The run-1 engine
takes 619.485 seconds, including a 31.904-second guard capture. Overall wall is 958.679 seconds.
The complete serialized guard heap is 17,462,279,076 bytes; its owner review is pending.

The fresh control's N1 counters record 39,413,262 mark memberships in 106,679 mark rows: a mean
of 369.457 marks per row. They also record 1,746,438 position memberships, 367,339,865 duplicate
admission attempts, 417,004,260 climb callbacks and 459,547 answer callbacks. These action totals
include both request kinds. They establish a large request workload, without assigning its share
of CPU time. Different incomplete progress and counter scopes must be retained in the comparison.

Stirling-PDF control times out in run-4, backward, limit 4 and retains its accepted run-3 report.
It exports 56 unique identities and 128 occurrences, versus 17 identities and occurrences on
main. Fourteen main identities remain; three are missing and 42 are added. The losses are the
`ProcessExecutor.runCommandWithOutputHandling(List, File)` command constructor and two XSS
return locations in `ConvertEmlToPDF.convertEmlToPdf` and `PipelineController.handleData`.
The two XSS methods have same-rule findings at other returns. These remain strict losses.

Current source inspection shows that synthetic method-exit witnesses use Simple export, which
falls back to the last physical return without trace return locations. It explains the displayed
XSS location changes, without proving equal originating flows. The command site's current source
return rewriter clears an exact receiver mark in bound callee coordinates at a later benign
`List.add`. The full main trace and current local source analysis identify a loss mechanism under
the existing source-override policy. They do not establish the earliest loss along the whole
current caller chain. Fresh source hashes and evidence limits are in `finding-source-review`.

The PDF control's overall wall is 1,119.652 seconds; IFDS is 1,080.052 seconds. Run-1 through run-4
engines take 38.894, 26.706, 65.714 and 912.064 seconds. The completed barriers take 0.356, 0.138
and 10.640 seconds. Both normal and near-limit 30-second CPU captures complete. No memory guard
snapshot is triggered. The optimized comparison and profile review remain pending.

Strict comparison uses rule ID, sink fingerprint and full method signature. Physical and logical
locations and occurrence counts are checked separately. Missing identities remain missing even
when a same-method result exists at another return. Added sites from an incomplete DEMAND report
need refinement and review before they can be called false positives.

## Profiles and memory

Control and optimized profile and heap reviews are pending. Optimized OWASP's normal 30-second
CPU capture completes, and it has no memory guard snapshot. Continuous JFR recordings and normal
and near-limit CPU windows are captured during each eligible run. Each window will state its
actual iteration, coverage and overlap with snapshot or output work.

The main OWASP normal window contains only the last part of analysis and then output. Main
Conductor and PDF finish before their eligible normal windows. Main ThingsBoard's normal CPU
capture has 3,064 samples. `Arrays.copyOf` accounts for 70.79% of self samples and the accessor
interner's `ConcurrentReadSafeObject2IntMap.getInt` for 26.24%. The captured stacks run through
side-effect requirements and initial-fact registration. Its planned IFDS-plus-1,020-second CPU
window occurs after its actual main timeout. The continuous JFR can supply an actual pre-timeout
window. These limits are saved in `main-evidence-review`.

For optimized counters, N1 action totals cover the prompt position-request path. Mark joins use
`RequestBatchStatistics`. Compare N1 admission and capacity data separately from action counts.
Answers count callbacks, not accepted new initials. Batch widths describe accepted sends. Pending
peaks are maxima in one actor, not simultaneous global totals or retained heap bytes. The service
age counter includes time spent inside the active phase. A guard heap must establish actual live
owners. Retained owner unions can overlap and must not be added as independent savings.

## Optimization outcome and next iteration

Conclusions are pending the remaining optimized results, strict loss review, per-run
profiles and OOM owner analysis. The next proposal will rank changes from those measurements,
with current behavior, proposed behavior and the reason for improvement for each candidate.
Analyzer-core work remains the scope; IR storage and implementation remain outside it.
