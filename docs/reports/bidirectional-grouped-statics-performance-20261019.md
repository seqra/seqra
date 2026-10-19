# Grouped marks and direct statics: performance report

Date: 2026-10-19. Measured new-core revision:
`ff2966455da2e5ffd0db655a1ff06c5954ed3c8a`.

All four new-core scans stopped in **run-1, forward, field limit 1**.
No scan completed run-1 or accepted a forward report.
All four failed the **60-second run-1 engine target**.
The implementation passes the selected tests, but project acceptance fails.
There is no evidence of a completed-work performance improvement.

## 1. Changes and source controls

The runtime implements the [combined specification](../../spec/grouped-run1-statics.md)
and [implementation proposal](../design/bidirectional-grouped-statics-implementation-20261019.md).
It uses the selected pre-bottom-up base
`a80d5c580b3b4715ab39679fbf66f0f190125fa0`.
Run-1 uses rule groups. Direct static reads and writes replace the old
static relay mechanism. All run-1 static FLOW and group TAINT edges are DEMAND.
The later schedule is backward/full/limit 1, forward/full/limit 2,
backward/full/limit 3, then the existing schedule.

Per the latest user decision, the new core treats
`trackFactsReachAnalysisEnd` as empty **in every run**.
This applies to method-call, entry-point and method-exit sink providers.
Sink conditions and report seeds remain active. Source conditions and
source actions remain active. Sink end-fact GEN and its reversed triggers
are absent. Retained source conditions and writer grounding remain active.
The legacy reference retains its current end-action behavior.
The strict finding comparison does not filter this difference.

The fresh reference is main
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`, checked against the remote.
Its only runtime patch is the optional diagnostic heap-dump hook.
The project checkout is `bbdbcc5fd35d976d93348760ea60561e4670f68c`.
Each manifest pins its source revision, patch, model and arguments.
The input guard also pins the complete rule bytes.

The final input guard passed before and after all eight scans.
It checked 177,499 file memberships in 10 datasets and 13 controls,
including all 2,048 project YAML referents, extension inputs and the JDK.
The guard result is `CLOSED_SOURCE_INPUT_EQUALITY_PASS`.
Runtime sources did not change between validation and measurement.
The report and next proposal are documentation changes only.

## 2. Validation

The final combined pass has **2,039 tests, zero failures and zero skips**:
1,577 common-core tests, 416 JVM dataflow tests, 15 selected JVM
acceptance/resolver tests, and 31 rule tests.
Acceptance fixtures reach run-3 with field limits `1, 1, 2`.
They check full marks, source action identities and negative controls.
The Lean build and constructive audit also pass.
This is evidence for the tested contracts. It does not prove the complete
JVM implementation or establish project finding parity.

```bash
cd core
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 OPENTAINT_TEST_BIDI=true ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :test --tests '*JIRBidiSequenceAcceptanceTest' --tests '*BidiSignedRuleResolverTest' \
  :opentaint-java-querylang:test --tests '*RuleTagsAndJoinTest' \
  -PincludeBidi --continue --offline --max-workers=2 --console=plain
```

## 3. Fixed project results

All eight scans used `runProjectAnalyzer` directly through Gradle.
There was one invocation per side and project, with no retry or alternate
configuration. Scans and heavy profile readers ran in sequence.
The outer limit was 1,200 seconds from analyzer launch.

| Project | Heap / configured timeout | Main status / command seconds | New core status / command seconds | New run-1 engine seconds | Latest reached / completed / accepted |
| --- | --- | --- | --- | ---: | --- |
| OWASP | 8 GiB / 1,000 s | OK / 121.218 | TIMEOUT / 1,021.527 | 908.767 | run-1 / none / none |
| conductor | 8 GiB / 1,080 s | OK / 92.175 | TIMEOUT / 1,120.940 | 986.838 | run-1 / none / none |
| ThingsBoard | 12 GiB / 1,080 s | TIMEOUT / 961.428 | TIMEOUT / 1,116.710 | 700.235 | run-1 / none / none |
| Stirling-PDF | 8 GiB / 1,080 s | OK / 49.101 | TIMEOUT / 1,106.670 | 1,026.865 | run-1 / none / none |

Main uses the legacy analysis. A bidirectional run number does not apply
to it. Neither side had an OOM, JVM stack overflow, build exception or
outer hard timeout. There was no OOM-triggered heap dump to inspect.

The new core is worse for fixed-budget completion on OWASP, conductor
and Stirling-PDF. Both ThingsBoard scans are incomplete.
The duration of an incomplete prefix cannot establish a speedup.
Earlier reports measure other runtime revisions and are stale for current
acceptance. In particular, the old conductor completion does not describe
this measured implementation.

### Setup and memory

Engine time excludes prescan, mark ownership census and report barriers.
All new-core scans failed before the first report barrier.

| Project | Main / new prescan seconds | New ownership census seconds | Eligible full marks / groups | Main / new peak RSS, GiB |
| --- | --- | ---: | ---: | --- |
| OWASP | 33.454 / 32.418 | 58.788 | 1,033 / 53 | 8.21 / 9.60 |
| conductor | 25.115 / 25.257 | 68.589 | 1,126 / 45 | 9.31 / 10.34 |
| ThingsBoard | 149.798 / 128.919 | 250.973 | 1,257 / 51 | 13.47 / 14.99 |
| Stirling-PDF | 13.994 / 17.185 | 35.919 | 1,172 / 51 | 9.04 / 9.68 |

RSS includes native memory and JVM overhead. It is not Java retained heap.
The census reduces mark cardinality, but it also has a large setup cost.
That cost does not explain the run-1 engine target failure.

## 4. Finding comparison and missing reports

The unchanged strict comparator uses rule ID, sink hash and full sink
signature. It compares sink identities and their occurrences, rather than
complete source-to-sink pairs. All SARIF files exist.

| Project | Main identities | New identities | Missing | Added | Latest new run |
| --- | ---: | ---: | ---: | ---: | --- |
| OWASP | 4,112 | 0 | 4,112 | 0 | run-1, TIMEOUT |
| conductor | 7 | 0 | 7 | 0 | run-1, TIMEOUT |
| ThingsBoard | 13, partial reference | 0 | 13 | 0 | run-1, TIMEOUT |
| Stirling-PDF | 17 | 0 | 17 | 0 | run-1, TIMEOUT |

The fixed-budget finding gate fails: **4,149 project-qualified reference
identities are absent**. Input and identity warnings are empty.
The comparator labels the result incomplete because the analyses did not
complete. No missing identity or end-action-dependent identity was removed.

The new-core export is censored by the run-1 timeout.
[IterationDriver](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/IterationDriver.kt)
returns a failed engine result before accepting it into
[Report](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/Report.kt).
Only a COMPLETE forward result can update the initially empty report.
[JIRBidiAnalysis](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt)
then exports that report. This explains why all four exports are empty.
It does not prove that every missing witness existed inside the engine.
The 4,149 missing identities are not 4,149 proved semantic false negatives.
An empty export also cannot establish precision or the absence of false
positives. Finding parity remains unproved.

## 5. Where run-1 spends work

The profiles show major auxiliary work. Fact cardinality alone does not
describe this workload. Guard equality, mark normalization, group-member
filtering and request admission all process work beyond accepted AP facts.
Profile shares below are sampled observations, not promised speedups.

### CPU windows and sampled allocations

The table uses exact class/method family predicates. Each cell is an
inclusive sample union for **normal / near-limit** CPU windows.
The two windows are separate 30-second observations within run-1.

| Family | OWASP run-1 | conductor run-1 | ThingsBoard run-1 | Stirling-PDF run-1 |
| --- | ---: | ---: | ---: | ---: |
| Exclusion normalizer methods | 15.62% / 15.88% | 52.06% / 0.02% | 47.34% / 46.69% | 14.73% / 0.00% |
| Support and requirement classes | 0.00% / 17.39% | 16.11% / 63.57% | 9.36% / 10.47% | 47.22% / 99.69% |
| Native GC family | 64.85% / 64.98% | 18.46% / 31.26% | 32.13% / 25.54% | 24.14% / 0.00% |

Sample totals, normal / near-limit, are 11,530 / 16,891 for OWASP,
26,922 / 39,211 for conductor, 31,239 / 27,421 for ThingsBoard,
and 14,716 / 8,983 for Stirling-PDF.
The guard cost grows late in conductor and Stirling-PDF.
This supports a guard-work priority without assigning one cause to every
project. The native samples also expose GC work that pause time omits.

Expanded JFR analysis reads all recorded frames through the JDK streaming
consumer. It avoids the default shallow stack print used by the first
summary. These are the allocation samples whose timestamps fall inside
the actual run-1 markers:

| Retained run-1 sample scope | OWASP | conductor | ThingsBoard | Stirling-PDF |
| --- | ---: | ---: | ---: | ---: |
| Estimated allocated bytes, decimal TB | 2.344 | 2.161 | 0.782 | 0.689 |
| Exclusion normalizer inclusive weight | 76.36% | 1.12% | 32.17% | 8.52% |
| Support/requirement inclusive weight | 0.04% | 87.70% | 1.72% | 52.76% |

**Conductor retains only the last 174 seconds** of its 986.838-second
engine. Its recording begins at 03:45:21 UTC. The limit removed the earlier
chunks. These numbers cannot describe all conductor run-1 allocation.
The other three recordings span their run-1 marker intervals.
Allocation is sampled, so this is not an exhaustive byte count.
Stirling-PDF has 51,926 of 98,610 run-1 allocation traces truncated at
the configured depth of 256 frames. Missing outer frames limit attribution.
Family weights overlap and cannot be added.

In the conductor suffix, `TrieNode.equals` and `TaintTree.equals` occur
in the same 72.97% allocation-weight family. Every such sampled Trie
allocation stack has support-class frames and an outward
`GroundingRequirement.equals` parent. The path constructs equality work
through `GroundingSupport.Row.equals` and long OR comparisons.
This establishes guard-driven tree comparison for the retained suffix.
It does not establish its full-engine fraction.

The initial shallow-export summary has about 108.37 GB of sampled inclusive
allocation weight at `MarkTable.eligibleMembers`, 13.85% of ThingsBoard's
run-1 weight. The expanded family reader did not count that method
separately, so this is the initial estimate rather than a recomputed
full-stack method total.
That method scans the complete eligible inventory for each group query.
The proposed current-member index can remove that repeated scan.
It does not claim to remove this entire allocation family.
The family can overlap the other families above.

After-GC used-heap samples range from 3.25 to 8.06 GB for OWASP,
7.56 to 8.07 GB for the conductor suffix, 7.36 to 12.09 GB for
ThingsBoard, and 2.99 to 7.45 GB for Stirling-PDF, in decimal bytes.
These are heap-used observations after individual collections. They are
not retained ownership measurements or a proof that the remaining heap
was all live. High allocation and expensive comparisons are established;
the owners of all standing heap bytes are not.

### Guard state and deep comparisons

The Stirling-PDF normal CPU window has 14,716 weighted samples.
`GroundingSupport` or `GroundingRequirement` class frames occur in
6,949 samples, **47.22%**, as an inclusive union.
Some stacks have 1,024 nested `Or.equals` frames at the capture depth of
2,048. This is direct evidence of expensive structural comparison.
A broader 75.59% substring match includes `withGroundingSupport` wrappers;
it must not be described as time spent only in guard algebra.

The current support constructors can represent this growth:

```text
S0 = A
S(n+1) = S(n) OR (S(n) AND B)
```

Each expression has the Boolean meaning `A`, but its syntax can grow.
Finite AP and mark domains do not bound that syntax.
The exact recurrence has not been reproduced in a production core fixture
or identified in this project. Large finite alternatives can also cause
the observed comparisons. The next proposal separates that open question
from the measured comparison cost.

| Run-1 terminal guard metadata | OWASP | conductor | ThingsBoard | Stirling-PDF |
| --- | ---: | ---: | ---: | ---: |
| TRUE rows | 1,934,330 | 61,555 | 3,035 | 41,881 |
| Conditional or certified rows | 424,585 | 1,176,171 | 18,437 | 69,950 |

These are statement/premise/accepted-position/layer rows in support
directories. Ordinary bulk TRUE fact rows are outside this counter.
These are not all fact rows or unique support nodes. Their retained bytes
and the share used only for unchanged transport are unknown.

### Mark and request work

In the OWASP normal CPU window, mark-exclusion frames occur in 1,838 of
11,530 samples, **15.94%**. Normalization frames occur in 1,547 samples,
**13.42%**. These families overlap.
The source repeatedly attaches a catalog, projects already grouped atoms,
and normalizes unchanged immutable exclusion values.

| Run-1 request members | OWASP | conductor | ThingsBoard | Stirling-PDF |
| --- | ---: | ---: | ---: | ---: |
| Offered | 30,040,101 | 3,722,858 | 5,310,686 | 673,040 |
| Already in summary storage | 25,539 | 1,361,499 | 1,108,183 | 44,674 |
| Already in temporary storage | 29,967,774 | 2,323,902 | 4,019,109 | 620,423 |
| Fresh temporary members | 46,788 | 37,457 | 183,394 | 7,943 |

Each column partitions offered **members**, not calls.
OWASP duplicates are 99.84425% of offered members.
Its 30,026,921 offer calls are a different denominator.
Early membership checks can avoid temporary set construction.
They cannot remove required AP transfers or claim fewer real work-queue
entries: queued flags already suppress duplicate reservations.
Terminal zero pending gauges are read after release and do not prove that
no requests were pending at timeout.

### Direct static work

| Run-1 static service | OWASP | conductor | ThingsBoard | Stirling-PDF |
| --- | ---: | ---: | ---: | ---: |
| Write regions | 0 | 1,217 | 65 | 80 |
| Supplied regions | 0 | 1,205 | 65 | 79 |
| Region/group supply memberships | 0 | 1,205 | 65 | 79 |
| Read descriptors | 286 | 7,291 | 43,104 | 21,492 |
| Point subscriptions | 0 | 28,364 | 4,202 | 6,163 |
| Candidate visits | 0 | 37,310,934 | 4,206 | 7,730 |

Region/group memberships are not distinct groups.
OWASP has no published static write or group supply in this prefix.
Direct static joins cannot explain its timeout.
ThingsBoard and Stirling-PDF also have small service visit counts.
This rules out one common static-join explanation for all four failures.
It does not rule out static-related ordinary fact or guard work.

Conductor needs a separate join review.
At most `7,291 * 1,217 = 8,873,147` of its visits can be reader/writer pairs
in this counter scope. At least 28,437,787 are in interest or binding paths.
That lower bound is **not** a redundant-work percentage.
Owner replies count outbox effects and cannot form a hit ratio with visits.

### Form work

Observed form builds are 608,702 / 215,250 / 1,203,303 / 177,295 for
OWASP / conductor / ThingsBoard / Stirling-PDF. All recorded builds succeed.
These are cumulative build events, not distinct methods or live forms.
The aggregated build durations are not CPU shares.
Method actor counts cannot establish a per-slot rebuild ratio.
Manager-wide cleared soft references are not a count of cleared forms.
No reference-manager or weak-cache change is justified by those counts alone.

## 6. Fresh main ThingsBoard timeout

Main timed out and then exported 13 partial findings.
Its normal profile has 3,041 samples: 1,709 at `ArrayList.grow` and
1,285 at `ConcurrentReadSafeObject2IntMap.getInt`.
The path includes cleaner position checking and accessor interning.
Progress logging then remains unchanged for several minutes.

The map read loop has no bounded retry or cancellation check. A progress
problem is plausible, but sampled stacks do not prove an infinite spin.
The synchronized insertion recheck also prevents a stale miss alone from
proving duplicate equal-accessor growth. That root cause is unresolved.
The legacy timeout budget and its staged 80% full-analysis allocation
explain the early deadline. The fixed configuration was used correctly.
Completion log markers and a SARIF file do not prove work closure.

## 7. Next iteration and evidence

The [next proposal](../design/bidirectional-grouped-statics-next-iteration-20261019.md)
ranks guard work, repeated mark work, persistent unchanged transport,
request admission, direct-static joins and setup metadata work.
It contains behavior changes, code sketches and proof gates.
No next-iteration runtime change is included in this measured revision.
IR storage, a new weak cache and reference-manager changes remain outside
the selected work.

The evidence root is:

```text
/drive-testcomp/opentaint-go-rules/grouped-statics-implementation-20261019
```

It contains all eight `projects/<project>/<main|bidi>/` bundles with
manifests, summaries, process samples, logs, SARIF and JFR.
All four new-core bundles have normal and near-limit CPU windows.
Main ThingsBoard has a normal window. The three fast main scans finish
before the normal-window trigger and have no CPU window.
The normal window starts about 60 seconds after full analysis starts and
lasts 30 seconds. The new-core near-limit window also lasts 30 seconds.
JFR is continuous with a 256 MiB recording limit. Old chunks can be removed.
Allocation weights estimate allocated bytes; they do not measure retained
heap. Inclusive families overlap. Sampled Java stacks do not measure total
process CPU. GC pause time excludes concurrent GC CPU.

Key audit files:

- `runtime-measurements.json`, SHA-256
  `332f557027875d00d22bbffc69d78551b45f17a2a111aab7f491762ca8119505`.
- `validation/benchmark-guard/equality.json`, SHA-256
  `aa60da509100738fb8419f4c107eb4236e51b53427bbcf1ec14d975799161d0f`.
- `validation/final-combined-eleventh.log` and copied XML test results.
- `comparison/summary.json`, plus per-project JSON and removed/added
  identity and occurrence files with all raw differences.
- `reviews/fresh-comparison-censoring-review.json`, source-bound export review.
- `reviews/*-review.json`, independent source and measurement checks.
- `profile-review/summary.json`, expanded allocation stack and capture-coverage
  review, SHA-256
  `4b7ec79621ea53268e82ed60fafcbba249b07fee16068ff713a00461ae52fe1e`.

The guard includes the original source patches and frozen input hashes.
Previous raw captures were cleaned before this measurement.
Current profiles remain available. There are no new OOM heaps.

```bash
python3 scripts/compare_bidi_projects.py \
  --root /drive-testcomp/opentaint-go-rules/grouped-statics-implementation-20261019/projects \
  --output-dir /drive-testcomp/opentaint-go-rules/grouped-statics-implementation-20261019/comparison
```

This command completed as a diagnostic comparison. Exit zero does not
mean that the completion or finding gates passed.
