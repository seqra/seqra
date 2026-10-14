# Request sets and method barriers: fixed-project performance

Date: 14 October 2026. Status: all twelve scans are terminal. This report contains fresh main,
control and optimized measurements, strict finding comparisons and implementation checks.
The deeper profile review, all four primary heap-owner reviews, run-1 component/root-path analysis
and both command-path diagnostic replays are complete. The next proposal gives a memory ranking
and run-1 work-avoidance designs. The second replay identifies the failed NORMAL supplier and
the source-admission gap at refinement. It does not prove a global first-loss event.

The tested optimization is request mark sets plus a method processing barrier. The implementation
commit is `a23ad7fcd99553190c82557cff6076503959aec6`. It preserves independent source and sink
conditions and full request origins. Position requests keep their scalar path. A nonzero Work
quantum can service a bounded request chunk when the method remains busy.

Summary publication already has a method barrier. `RunMethodAnalyzer.summaryDelta` buffers
fresh publications, and `work` flushes them after the method fact queue becomes empty. A Work
quantum yield does not flush summaries. `PublicationIndex` admits fresh deltas, and caller
delivery still matches and applies those deltas separately. The next proposal addresses that
remaining matching and graft work.

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
1,841 model-selected dependency files and 77 checks over 41 captured datasets. The final input audits pass, including full content and dependency membership. Evidence is in
`input-audit/result-20261014T140340.134117Z` and
`input-audit/dependencies-20261014T140720.150861Z`. Terminal manifests and actual JVM arguments
are also bound in `combinedmatrix-terminal.json`.

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

All four fresh controls and all four optimized runs are terminal.
Run-1 starts at limit 1. Completion and report acceptance have separate evidence.

The next iteration's primary target is to complete **run-1, forward, limit 1, within 60 seconds
of engine time** on each project. The user confirmed that this target excludes prescan and the
report barrier. The current optimized measurements meet this target only on Stirling-PDF:

| Project | Run-1 engine outcome | Engine time | 60-second target |
| --- | --- | ---: | --- |
| OWASP | Complete | 336.977 s | Not met |
| Conductor | Complete | 75.939 s | Not met |
| ThingsBoard | OOM before completion | 226.938 s, including capture | Not met |
| Stirling-PDF | Complete | 47.389 s | Met in this measurement |

ThingsBoard's guard starts 170.673 seconds into run-1, already beyond the target. Removing the
capture time would not make that failed run a pass. OWASP needs at least an 82.19% engine-time
reduction, and Conductor needs at least 20.99%, to reach 60 seconds from these completed runs.
The failed ThingsBoard prefix cannot give the required reduction for a complete run. These
single measurements do not establish that any runtime can meet the target on every project.

| Runtime / project | Latest reached | Last complete engine | Last accepted forward report | Status |
| --- | --- | --- | --- | --- |
| Control / OWASP | run-3, forward, limit 3 | run-3, forward, limit 3 | run-3, forward, limit 3 | Complete |
| Control / Conductor | run-2, backward, limit 2 | run-1, forward, limit 1 | run-1, forward, limit 1 | OOM |
| Control / ThingsBoard | run-1, forward, limit 1 | None | None | OOM |
| Control / Stirling-PDF | run-4, backward, limit 4 | run-3, forward, limit 3 | run-3, forward, limit 3 | Timeout |
| Optimized / OWASP | run-3, forward, limit 3 | run-3, forward, limit 3 | run-3, forward, limit 3 | Complete |
| Optimized / Conductor | run-2, backward, limit 2 | run-1, forward, limit 1 | run-1, forward, limit 1 | OOM |
| Optimized / ThingsBoard | run-1, forward, limit 1 | None | None | OOM |
| Optimized / Stirling-PDF | run-4, backward, limit 4 | run-3, forward, limit 3 | run-3, forward, limit 3 | Timeout |

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
IFDS takes 560.651 seconds. The logged interval from the first run START to the final report barrier is 494.651 seconds.
The explicit sum of engine and report-barrier times is 494.647 seconds.

| OWASP iteration | Control engine | Optimized engine | Control report barrier | Optimized report barrier |
| --- | ---: | ---: | ---: | ---: |
| run-1, forward, limit 1 | 303.950 s | 336.977 s | 1.554 s | 1.696 s |
| run-2, backward, limit 2 | 60.722 s | 81.042 s | 0.142 s | 0.165 s |
| run-3, forward, limit 3 | 61.587 s | 74.618 s | 0.107 s | 0.148 s |

The optimized OWASP engines are slower in this fresh pair. The overall wall time increases
from 584.296 to 637.508 seconds. These are single measurements on a shared host with diagnostic
statistics enabled. Profiles show substantial form and cleaner work, but this pair does not
isolate the cause of the increase. Evidence is in `optimized-review/OWASP-main-comparison.json`,
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
11,763,718,066 bytes. Its primary retained-owner census is complete below; file size is not retained heap size.

Optimized Conductor stops with OOM in **run-2, backward, limit 2**. Its last complete engine
and accepted report are **run-1, forward, limit 1**. It preserves all seven main identities
and their locations. It exports 300 identities, with no identity, location or occurrence
changes against the fresh control. The paired input checks report no differences.

Its overall wall time is 603.117 seconds. Run-1 takes 75.939 seconds in the engine and
2.585 seconds at the report barrier. Run-2 takes 342.415 seconds in the engine, including
a 29.958-second pre-cancel heap capture. The serialized guard heap is 11,547,010,744 bytes.
Both optimized engines are slower than their control engines, and both runtimes stop in
run-2. Different progress at OOM and capture overhead limit a direct timing comparison.
The guard's file size does not measure live owner memory. Its primary retained-owner census is complete below.
Evidence is in `optimized-review/conductor-main-comparison.json`,
`optimized-review/conductor-control-comparison.json` and `projects/conductor/bidi`.

ThingsBoard control stops with OOM in run-1, forward, limit 1. It never accepts a forward report
and exports a valid empty SARIF file. All 13 identities in the incomplete main output are absent.
The first-report failure explains this output loss; it does not identify 13 separate transfer
defects. Its IFDS time is 872.502 seconds, including 249.029 seconds of prescan. The run-1 engine
takes 619.485 seconds, including a 31.904-second guard capture. Overall wall is 958.679 seconds.
The complete serialized guard heap is 17,462,279,076 bytes; its retained-owner census is complete
below. Optimized run-1 component and bounded strong-root attribution are also complete.

The fresh control's N1 counters record 39,413,262 mark memberships in 106,679 mark rows: a mean
of 369.457 marks per row. They also record 1,746,438 position memberships, 367,339,865 duplicate
admission attempts, 417,004,260 climb callbacks and 459,547 answer callbacks. These action totals
include both request kinds. They establish a large request workload, without assigning its share
of CPU time. Different incomplete progress and counter scopes must be retained in the comparison.

Optimized ThingsBoard also stops with OOM in **run-1, forward, limit 1**, before any complete
engine or accepted report. It exports no findings. All 13 sites from the incomplete main report
remain absent. This is a first-report failure, as in the control. Its overall wall time is
632.027 seconds. The engine takes 226.938 seconds, including a 56.141-second pre-cancel capture.
The serialized guard heap is 17,828,607,064 bytes. The guard starts 170.673 seconds after the
run-1 engine starts. The control engine takes 619.485 seconds, including its 31.904-second capture.
The optimized run still fails to complete run-1. Different workload progress prevents a
same-work speed or memory-saving claim.

Optimized N1 records 17,577,042 mark memberships in 188,260 mark rows and 2,401,904 position
memberships. It records 56,560,503 duplicate admission attempts and 147,975,115 live primitive
cells at failed engine END. N2 records 2,992,723 accepted deltas. The batch counters record
141,590,548 member scans, 113,166,651 climb callbacks and 3,586,966 partial continuations.
These differ from the control census. Failed END accounting is not a post-teardown reachable
heap census. The owner review below separates standing stores, unprocessed state and request frontier
snapshots. Failed END counters and pre-cancel heap measurements remain different observations.

Stirling-PDF control times out in run-4, backward, limit 4 and retains its accepted run-3 report.
It exports 56 unique identities and 128 occurrences, versus 17 identities and occurrences on
main. Fourteen main identities remain; three are missing and 42 are added. The losses are the
`ProcessExecutor.runCommandWithOutputHandling(List, File)` command constructor and two XSS
return locations in `ConvertEmlToPDF.convertEmlToPdf` and `PipelineController.handleData`.
The two XSS methods have same-rule findings at other returns. These remain strict losses.

Current source inspection shows that synthetic method-exit witnesses use Simple export, which
falls back to the last physical return without trace return locations. It explains the displayed
XSS location changes, without proving equal originating flows. Source inspection also identifies
a possible command-site loss mechanism: the RETURNED rewriter removes an eligible same-mark
EXACT root tuple from bound `This` at a later benign `List.add`. The static review covers both
layers. Fresh source hashes and its limits are in `finding-source-review/control-tightening`.

The PDF control's overall wall is 1,119.652 seconds; IFDS is 1,080.052 seconds. Run-1 through run-4
engines take 38.894, 26.706, 65.714 and 912.064 seconds. The completed barriers take 0.356, 0.138
and 10.640 seconds. Both normal and near-limit 30-second CPU captures complete. No memory guard
snapshot is triggered. The optimized comparison and deeper profile review are complete.

Optimized Stirling-PDF times out in **run-4, backward, limit 4**. It retains the complete engine
and accepted report from **run-3, forward, limit 3**. Its first three engines take 47.389, 38.814
and 82.403 seconds. Their report barriers take 0.494, 0.130 and 11.173 seconds. Run-4 takes
868.784 seconds and exhausts its smaller remaining budget; that shorter failed run is not an
improvement. Overall wall is 1,139.302 seconds; IFDS is 1,080.077 seconds. Both 30-second CPU
captures complete, and no guard heap is triggered. Its 56 identities and 128 occurrences match
the fresh control exactly. The same three main identities remain absent, and 42 are added.

The isolated command-path diagnostic also times out in **run-4, backward, limit 4**, after
completing and accepting **run-3, forward, limit 3**. It uses the fixed PDF configuration and
detached implementation source with observation hooks. Its terminal output matches the primary
optimized output exactly: 56 strict identities, 128 occurrences, and no identity, location or
multiplicity changes. Its timings are separate from the primary measurements.

The diagnostic's complete report records show two DEMAND keys at the target command constructor
after run-1. Neither remains after run-3 or in the terminal output. Two complete cleaner pairs at
controller instruction 56 prove local removal of command marks 729 and 730: root membership
changes from one to zero, with no output. Both pairs have caller layer DEMAND and a zero premise.
They do not prove the earliest loss of the main NORMAL flow. Some ordinary run-3 event lanes
reach their caps; protected report, plan and cleaner records have no drops. The complete run-3 constructor census has only three NORMAL REACH and four NORMAL FLOW
inputs, with no TAINT input. Restricted concrete sink checks cannot create a witness from those
FLOW facts. Support review rejects the run-1 nonzero NORMAL witness; report replacement then
removes the target keys. This first replay identifies the failing refinement boundary. It does
not name the rejecting upstream supplier. Its evidence and strict output
comparison are in
`analysis-preparation/command-loss-diagnostic/jobs/replay-/20261014T160643.886578Z-0356d5e9/terminal-review-final`.

A second isolated replay observes the actual support decisions and completed backward hand-off.
It also reaches **run-4, backward, limit 4**, with TIMEOUT, after completing and accepting
**run-3, forward, limit 3**. Its output again matches the primary run: 56 strict identities,
128 occurrences, and no raw-location or multiplicity changes. Its times remain diagnostic
measurements. Its two run-1 constructor support queries each visit 17 candidates:
16 fail the fact-shape test, and one supplies the requested exact ARGS fact. That NORMAL supplier
is the one-argument `ProcessExecutor.runCommandWithOutputHandling(List)` at call 0. Its caller
premise is unsupported, so both queries return false. This names the failed supplier; the
constructor binding and report replacement do not cause that support rejection.

The matching concrete assumption does not establish the main website source path. The recorded
incoming exact ARGS fact comes from `ColorSpaceConversionStrategy.replace`, call 35, in DEMAND.
The website controller at call 61 supplies a DEMAND UNTRUSTED fact at `Iterable.Element`.
Its run-1 entry also has a NORMAL zero-premise command UNTRUSTED source. In the completed run-2
hand-off, the same full controller key has 11 source hits, all filtered as non-command mark
edges, and no selected command source seed. The getter has a passive NORMAL FLOW record, but its selected backward
summary is zero REACH and its outgoing demand count is zero. The exact source-seed check therefore
does not admit that controller command entry source in run-3. Conditional List.add source
predicates remain enabled; they do not use this zero-premise source-seed gate.

The support and source-admission failures explain the target's unavailable marked support after
refinement. The closed protected lanes have no dropped, truncated, unwritten or erroneous rows,
and the hand-off has no scan cutoffs. The separate local D23 erasure remains a DEMAND example.
These observations do not prove whole-chain NORMAL erasure, the first backward branch that
prevents the source hit, or that the main finding is invalid. The diagnostic patch stays in its
isolated checkout. Its 114 focused tests and root compilation pass; source and helper hashes
match before and after execution. Evidence is under
`analysis-preparation/command-support-diagnostic-2/jobs/replay-/20261014T183526.607713Z-05d9ee84`.

Across all four projects, control and optimized output match in strict identities, occurrence
counts and physical locations. Main still has 16 absent identities: 13 from ThingsBoard and
three from Stirling-PDF. The no-loss target against main is not met. The complete comparison and
all engine/report-barrier times are in `comparison-20261014T140556.870021Z`.

Strict comparison uses rule ID, sink fingerprint and full method signature. Physical and logical
locations and occurrence counts are checked separately. Missing identities remain missing even
when a same-method result exists at another return. Added sites from an incomplete DEMAND report
need refinement and review before they can be called false positives.

## Profiles and memory

All twelve first JFR exports complete with matching event totals. Their print command used the
default five-frame stack depth. Sampled totals and GC windows remain valid, but inclusive caller
rankings cover only those printed frames. The corrected export uses an explicit print depth of
256 and completes for all twelve recordings. Its event, allocation and GC totals match the first
export, with zero audit errors or rank-cap overflows. The recorded stacks reach at most 64 frames
and include explicit truncation flags. Printing more frames cannot restore missing recorded
callers. The first export remains preserved in `profile-analysis/export-1`.

The table gives sampled allocation weights for each bidi engine. GB means decimal gigabytes.
These are observed sample weights, not retained memory or exact total allocations. OOM engine
weights include the guard interval. Failed runs have different progress. Full Java sample and
clipped GC-pause tables are in `profile-analysis/exported-review-2/tables.md`.

| Project / engine | Control sampled GB | Optimized sampled GB |
| --- | ---: | ---: |
| OWASP run-1, forward, limit 1 | 313.368 | 312.479 |
| OWASP run-2, backward, limit 2 | 70.694 | 70.417 |
| OWASP run-3, forward, limit 3 | 65.822 | 65.620 |
| Conductor run-1, forward, limit 1 | 117.081 | 103.075 |
| Conductor run-2, backward, limit 2; OOM | 346.131 | 387.577 |
| ThingsBoard run-1, forward, limit 1; OOM | 1,209.202 | 359.563 |
| Stirling-PDF run-1, forward, limit 1 | 116.971 | 114.274 |
| Stirling-PDF run-2, backward, limit 2 | 99.819 | 99.463 |
| Stirling-PDF run-3, forward, limit 3 | 209.683 | 206.745 |
| Stirling-PDF run-4, backward, limit 4; timeout | 1,764.999 | 1,386.679 |

Optimized OWASP completes run-3. Its engine allocation weights remain close to the control, while
all three engines take longer. Form building appears in 75.10% and 84.58% of Java execution
samples in optimized run-2 and run-3. Run-1 cleaner-chain frames carry 164.652 GB of inclusive
sampled allocation. These paths overlap; their shares cannot be added. They identify build and
transfer work but do not establish the cause of the timing increase.

Conductor's optimized run-2 OOM profile has nested call-stage, callee-entry, fact insertion and
limit work. Stage application carries 188.998 GB of inclusive sampled allocation and limit work
carries 78.362 GB. The requested normal windows cross run-1, its barrier and run-2 in both
runtimes. The derived last 30 seconds before each guard stay in run-2 and exclude the capture.

Optimized ThingsBoard reaches only run-1. Its form-building path appears in 47.53% of that
engine's Java samples and carries 155.380 GB of inclusive sampled allocation. The snapshot
cursor has 753 self samples, or 3.59%. The control's queue comparator carries 555.910 GB of
sampled allocation, but the optimized guard occurs much earlier with different work completed.
This profile change does not establish equal-work savings or current queue retention.

Stirling-PDF reaches run-4 and retains the accepted run-3 report. Its optimized run-4 has
588.978 GB of inclusive allocation in limit handling and 344.810 GB in subtraction. These are
nested within call-stage and insertion paths. The near-limit requested window is wholly in
run-4; the separately derived final 30-second window is different. The normal requested window
crosses run-2, its barrier and run-3. Java JFR samples exclude native GC, so their shares must
not be compared directly with the native profiler percentages below. Actual window coverage,
recorded trace limits and source-linked paths are in `profile-analysis/exported-review-2`.

All four guard heaps match their completed capture records and precede failed engine END. The
first actual MAT job completes for optimized ThingsBoard, captured in **run-1, forward,
limit 1**, before cancellation. This run has no complete engine or accepted report. The analysis
tool uses 16 GiB with an 18 GiB RAM floor; analyzer settings are unchanged. Parsing finds
310,407,138 raw objects and removes 30,044 unreachable objects. The complete reachable histogram
contains **310,377,094 objects and 11,392,920,992 shallow bytes**. The complete root dominator
partition matches this byte total. All 46 requested queries export; three have empty selections.

The following rows are separate retained-set union queries. GB means decimal gigabytes. These
unions can overlap and must not be added or treated as independent potential savings.

| Selected owners | Retained-set bytes | Share of reachable heap |
| --- | ---: | ---: |
| Actors, unit runners and subscription managers | 5.688 GB | 49.93% |
| Standing N1 request stores | 2.268 GB | 19.91% |
| Cumulative N2 subscription and publication state | 1.089 GB | 9.55% |
| Raw actor added-link stores | 0.988 GB | 8.68% |
| Forms, method contexts and caches together | 0.684 GB | 6.00% |
| Request frontiers and active copy-on-write views | 0.080 GB | 0.70% |
| Temporary batching state | 0.045 GB | 0.40% |
| Event queues and buffered channels | 0.003 GB | 0.03% |

The complete physical queue census finds 562 queues with 2,564 entries. This snapshot shows
large standing state and indexes, with much smaller pending request frontiers and physical
queues. Actor-local delta worklists and their fact payloads now have separate attribution below.

The N1 union includes processed requests, membership directories and query indexes. Its captured
census has 17,573,638 mark memberships in 188,247 mark rows, and 2,401,903 position memberships in
the same number of position rows. Static positions account for 92.73% of rows; marks account for
87.98% of memberships. Request initials already have an empty field exclusion, so resetting that
field cannot reduce this population. The follow-up
[request-management investigation](../design/bidirectional-run1-request-management-20261014.md)
examines routing projection and demand sharing before accepting the current materialization as
necessary. This evidence is from ThingsBoard run-1/forward/limit 1, with no complete iteration
or accepted report.
The selected [subsequent redesign](../design/bidirectional-run1-abstraction-demands-20261014.md)
uses prescan's resolved call graph and collected static accesses to move demand handling into
abstraction. It has no runtime measurements yet; the timings and progress in this report remain
the measured baseline.

Physical queue bytes alone do not bound all unprocessed actor state; the separate census below
measures that state. The snapshot does not
support making broad queue storage changes a priority for the next iteration.
It also contains a 1.324 GB Spring rule-provider retained set. This set includes its delegated
base provider and Spring context; it does not isolate the cost of Spring rule expansion. The separate fields below identify base-provider rule caching as a large target. IR storage retains
1.622 GB and remains outside the optimization scope. The forms/context total does not isolate
soft-cache payloads.

The ThingsBoard control MAT analysis also completes for its **run-1, forward, limit 1** guard.
It has no complete engine or accepted report. Its complete reachable histogram contains
293,231,017 objects and 11,344,297,928 shallow bytes. Its root partition matches that total.

| ThingsBoard guard owner selection | Control bytes | Optimized bytes |
| --- | ---: | ---: |
| Actors, runners and subscription managers | 5,664,376,416 | 5,688,245,352 |
| Standing N1 request stores | 2,517,478,256 | 2,267,943,216 |
| N2 subscription and publication state | 664,371,064 | 1,088,543,928 |
| Raw actor added-fact stores | 474,984,976 | 988,439,488 |
| Event queues and buffered channels | 1,273,255,840 | 2,995,000 |

The control has 13,786,571 physical queue entries, compared with 2,564 in the optimized guard.
The new snapshot has much less retained queue state and larger N2/raw-store selections. The
runs reach different workload states before their guards; neither completes run-1. These are
snapshot comparisons, not equal-work savings or a measured reduction for a particular event
kind. The selected unions overlap within each snapshot.

Optimized run-1 strong root paths and component attribution are complete below. All four primary guard heaps
have complete reachable histograms and root partitions.
The actual query results and complete censuses are in
`heap-review/optimized/thingsboard/job-20261014T141629.704526Z-15e20a898a/review/evidence.json`.
The control results are in
`heap-review/control/thingsboard/job-20261014T152054.478326Z-247c8abf03/review/evidence.json`.
The completed deeper JFR export is in `profile-analysis/export-2`. Recorded stack truncation
still limits caller attribution.

Optimized Conductor's MAT analysis completes for its **run-2, backward, limit 2** guard. Its last
complete engine and accepted report remain **run-1, forward, limit 1**. The reachable histogram
contains 216,242,808 objects and 7,641,192,032 shallow bytes; the root partition matches that total.
The actor/runner/subscription union retains 2,840,595,824 bytes. N2 subscription/publication state
retains 965,935,880 bytes and raw added-fact stores retain 559,276,760 bytes. Physical queues and
channels retain 816,936 bytes, with six physical event entries. These selections overlap.

The full class census proves zero live N1 request stores, request frontiers, mark-set builders
and snapshot cursors in this backward heap. Five run-1 batching-array queries produce no result
because their receiver classes have no instances. Their original query errors remain preserved;
`review/empty-class-audit.json` proves the empty populations separately. This does not validate
those field queries on nonempty receivers. The other owner results and complete histogram are
in `heap-review/optimized/conductor/job-20261014T153946.114325Z-3ae40a0461/review/evidence.json`.
An N1-only optimization cannot directly remove this run-2 state. Its IR-storage retained set is
2,264,869,592 bytes and remains outside the optimization scope. Additional run-2 component/path
queries are prepared and deferred while run-1 is the priority.

Conductor control's **run-2, backward, limit 2** guard also has a complete histogram and root
partition: 222,769,775 objects and 7,654,410,576 shallow bytes. Its last complete engine and
accepted report are run-1/forward/limit 1. The actor/runner/subscription union retains
2,884,215,408 bytes, N2 retains 884,650,200 bytes and raw added-fact stores retain 760,205,352
bytes. Queues/channels retain 858,264 bytes with four physical entries. Both Conductor heaps
have zero live N1 stores and request frontiers. The control's request-lane array query has no
live receiver; its original failure and separate empty-class proof are retained. The four-heap
audit and overlapping-owner comparison are in `heap-review/primary-comparison-1`. None of these
different-progress snapshots proves net memory savings.

## Run-1 component and root-path attribution

Optimized ThingsBoard's captured state is still **run-1, forward, limit 1**, with no complete
engine or accepted report. Its separate component queries give these target sizes:

| Selected component | Bytes | Meaning |
| --- | ---: | --- |
| N2 subscription positional index | 474,552,952 | Required standing postings; representation can change |
| Incoming raw-link positional indexes | 637,038,864 | Required incoming geometry indexes |
| Unused observation positional indexes | 7,121,616 | Small index with no geometry reader |
| Observation exact accumulators | 39,763,512 | Required deduplication when observation is active |
| Ordinary resolved method-exit rule cache | 544,729,744 | Strong raw rule lists, not form payloads |
| `allRelevant` method-exit rule cache | 48 | Empty map for this family only |
| Alias-analysis retained selection | 582,083,568 | Unnecessary-use fraction is not measured |
| Actor-local worklists | 43,556,384 | Includes queue containers, payloads and burst history |
| Pending publication lists | 4,660,064 | Buffered summary state |
| Cumulative method-edge stores | 1,035,713,648 | Admitted edge state; overlaps pending delta payloads |

These retained selections can overlap and must not be added. The full actor census covers all
76,943 actors. It finds 679 unchanged and 50,159 other queued deltas, no queued zero deltas,
5,708 burst-seen entries and 15,568 pending publications. Burst history can contain processed
items. Cumulative edge stores include both processed and queued admissions. The queue census
therefore does not support describing the main memory cost as huge
unprocessed collections in this optimized snapshot. Large standing relations, indexes and raw
rule/alias data remain.

The base-provider selection retains 1,324,319,360 bytes, compared with 2,012,152 bytes in the
Spring-context selection. The source and component scopes support examining resolved rule caches;
they do not support blaming Spring expansion for the whole provider union. Existing managed soft
FormsCache support remains in place. A separate soft cache for raw resolved lists would trade
memory for rebuild time and must use the original manager.

The sparse N1 census finds 2,419,536 leaf nodes, 885,668 single-child nodes and 317,646 wider
nodes. Leaf nodes already have no child map. Single-child maps and key/value/child-order arrays
occupy 375,523,232 bytes. After including removable list shells and modeled owner growth, the
next proposal estimates 425.87 MB for child layout and 107.44 MB for specialized bucket lanes.
These global shallow-layout models are not measured retained-memory savings. Empty cumulative
kind-map and layer-list shells provide a separate 216.06 MB gross target.

The first supplemental MAT job was stopped after its own heap filled and repeated allocation-
failure GC prevented useful export progress. Its 57 completed tables were preserved and verified;
the partial actor table was excluded. The smaller replacement completes all 17 requested tables
and the complete actor census. Its whole histogram and 986-row disjoint root partition match the
primary snapshot. This is a report-tool limitation, not another analyzer OOM or a changed scan.

The first path CSVs show only their selected root row, so they do not prove paths. A separate
successful HTML export shows bounded strong paths for method entries, the provider, a unit runner
and a standing request store. The expanded branches reach Java-local roots on the main thread,
with runtime controller/actor references. Default weak-reference exclusions retain these paths.
Five paths are requested per query; only the shown expanded branches are claimed. The generic
reader's HTML-unavailable status remains preserved, with a separate manual HTML audit.

The combined audit is `heap-review/supplemental-components-review-1`. Original job failures,
complete replacement and HTML results, source bindings, file identities and layout assumptions
are recorded separately there. Conductor's primary run-2 heap remains audited; its additional
component queries are deferred to keep the next iteration focused on run-1.

All primary heap-owner reviews and the optimized run-1 component audit are complete. Optimized OWASP's normal 30-second
CPU capture completes, and it has no memory guard snapshot. Continuous JFR recordings and normal
and near-limit CPU windows are captured during each eligible run. The deeper profile tables state each window's actual iteration, coverage and overlap with snapshot or output work.

The main OWASP normal capture is truncated: its profiler reports that the process exited.
Only about two seconds overlap the full scan, during trace generation, followed by output.
It does not provide a complete 30-second normal analysis window. Main
Conductor and PDF finish before their eligible normal windows. Main ThingsBoard's normal CPU
capture has 3,064 samples. `Arrays.copyOf` accounts for 70.79% of self samples and the accessor
interner's `ConcurrentReadSafeObject2IntMap.getInt` for 26.24%. The captured stacks run through
side-effect requirements and initial-fact registration. Its planned IFDS-plus-1,020-second CPU
window occurs after its actual main timeout. The continuous JFR can supply an actual pre-timeout
window. These limits are saved in `main-evidence-review`.

The light OWASP review places both control and optimized normal CPU windows wholly inside
**run-1, forward, limit 1**, with no logged guard overlap. Their terminal reached, complete and
accepted iterations are **run-3, forward, limit 3**. Control has 3,705 samples and optimized has
3,635. Call-form build and normalization frames occur in 65.43% and 67.32% of samples; native
GC-related frames occur in 20.19% and 18.95%. These are inclusive sample shares, not elapsed-time
shares, and frame groups can overlap. Neither window has a matching request-frontier, radix
snapshot or group-cursor sample. That does not prove these operations have zero cost. The windows
do not explain the slower complete optimized engines. See
`optimized-live-review/owasp-normal-cpu-1/REVIEW.md` for the actual windows and matched frames.

Optimized OWASP admits the same 299,471 mark memberships and 6,063 mark rows as the control.
It sends 3,341 accepted outgoing batches with 66,285 member occurrences, mean width 19.840 and
maximum width 32. The control records 90,535 scalar climb callbacks. Each constructs one event
and attempts one send, but the control does not count successful sends. These are different
counter scopes; their ratio is not a measured reduction in accepted queue entries.

The ThingsBoard normal CPU windows also lie wholly inside **run-1, forward, limit 1**, before
their guards. Both terminal runs reached run-1 and have no complete engine or accepted report.
Control has 27,312 samples and optimized has 25,400. The optimized sample shares are 38.56% for
native GC-related frames, 23.34% for form building, 10.51% for handler dispatch and 1.48% for
queue comparison. Control shares are 30.42%, 6.48%, 29.85% and 12.12%. Control scalar request
join service occurs in 26.06% of samples; optimized request-frontier frames occur in 3.43%.
The scopes overlap and the runs have different progress. These shares do not measure equal-work
savings. New per-actor maxima include 180,360 pending members, 458,496 request int cells,
16,213 pending delta roots and 77 captured old roots. The later guard snapshot measures
80,281,160 bytes for the combined request-frontier and active-view retained set; the counter
maxima do not identify the contribution of each member or root.
See `optimized-live-review/thingsboard-normal-cpu-1` for exact frames and scope limits.

For optimized counters, N1 action totals cover the prompt position-request path. Mark joins use
`RequestBatchStatistics`. Compare N1 admission and capacity data separately from action counts.
Answers count callbacks, not accepted new initials. Batch widths describe accepted sends. Pending
peaks are maxima in one actor, not simultaneous global totals or retained heap bytes. The service
age counter includes time spent inside the active phase. A guard heap must establish actual live
owners. Retained owner unions can overlap and must not be added as independent savings.

## Prescan and total timing

Prescan occurs before the bidi run-1 engine. It also changes overall wall time in this fresh pair.

| Project | Control prescan | Optimized prescan |
| --- | ---: | ---: |
| OWASP | 79.694 s | 64.304 s |
| Conductor | 47.203 s | 73.285 s |
| ThingsBoard | 249.029 s | 324.708 s |
| Stirling-PDF | 25.358 s | 30.612 s |

These timings come from the actual prescan log markers. They are outside the reported bidi engine
intervals. The shared host and a single sequential pair limit causal attribution. Current optimized
reached iterations are run-3/forward/limit 3, run-2/backward/limit 2, run-1/forward/limit 1 and
run-4/backward/limit 4 respectively; the complete and accepted states are in the progress table.

## Optimization outcome and next iteration

The measured optimization preserves all control output, but it does not improve completion or
meet the no-loss target against main. Optimized OWASP, the completed comparison, is slower. The
other projects still stop with OOM or timeout, and their different incomplete progress limits
speed and memory comparisons.

Run-1 component and root-path queries are complete. The
[next optimization proposal](../design/bidirectional-core-optimization-after-request-sets-20261014.md)
ranks measured memory targets and proposes ways to avoid repeated run-1 work. It gives current
behavior, proposed behavior and the reason for improvement for each candidate. Analyzer-core
work remains the scope; IR storage and implementation remain outside it.
