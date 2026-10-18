# Caller-owned subscriptions: project performance

Date: 18 October 2026. **Measurements and analysis are closed; acceptance
gates fail.** The candidate is `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
OWASP ended with **TIMEOUT** in **run-7**, completed **run-6**, and retained
its **run-5** forward report. Corrected conductor ended with **OOM** in
**run-1**, with no completed engine or accepted forward report. ThingsBoard
ended with **TIMEOUT** in **run-1**, with no completed engine or accepted
report. Stirling-PDF ended with **OOM** in **run-2**, with **run-1** complete
and its **run-1** forward report accepted.
All four miss the 60-second run-1 engine target: OWASP **270.490 s**,
conductor **116.897 s, interrupted**, ThingsBoard **873.914 s, interrupted**,
and PDF **63.304 s**.

This report uses the corrected candidate only. Earlier candidate timings
are superseded and do not establish current performance. The first attempt
stopped on a mixed-request implementation exception; the correction and its
validation are described below. Strict location comparison, CPU analysis and both OOM heap reviews are
closed. PDF's heap output has the tool qualification stated below. The
no-loss and 60-second gates fail. No overall performance gain or semantic
no-false-negative claim is established.

## Evidence and fixed settings

The current evidence root is
`/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-retest-20261018/`,
called `P`. The original fresh main and validation evidence root is
`/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-20261018/`,
called `B`. `P/baseline-reuse.json` explicitly binds the original main
files and timestamps. The fresh after-input audit closed at 13:27:54 UTC;
`P/input-endpoint-review.json` confirms 80 checks and 41 equal dataset
fingerprints. `P/scans-closure.json` and `P/measurement-slot-handoff.json`
record all eight source-stable terminals and process closure. Those main runs are reused because their source and
configuration are unchanged. They are not relabeled as new runs. Remote
main was rechecked and still selected the same commit.

| Binding | Value |
| --- | --- |
| Corrected candidate | `63d5481e4e4b69ecf042c1149bb574ad40fd1624` |
| Main | `f945f92c5ae554ff4f5fc7696ee5a269210478bb` |
| Main diagnostic change | MemoryManager guard-snapshot hook; patch SHA-256 `53f0abeeb0c98a16db86b8236b91899020fca14e799ff69a6739addf644b402f` |
| Validated candidate jar | SHA-256 `e816e12fb4a82ae210cfe5d07cbce715471cb6e10f646bc7ade855fb9d9fbd63` |
| Execution | Direct Gradle `:runProjectAnalyzer`; Temurin JDK 17; serial runs |
| Before-input audit | 80 checks passed; 41 full content datasets |
| After-input audit | 80 checks / 41 datasets passed; all content fingerprints equal |
| Candidate build | Passed; full core/rules source maps identical before/after |

The main checkout includes the stated diagnostic hook. Candidate aggregate
form and optimization counters are enabled. Their overhead is not measured
separately. Both sides use continuous JFR and the same native profile and
heap-capture controls. The candidate jar and all 1,162 recorded production
source files match the compiled validation receipt. The commit binding is
`P/implementation-commit-binding.json`.

Only the prescribed project settings are used:

| Project | Analyzer heap | IFDS timeout | Outer timeout | Rules |
| --- | --- | --- | --- | --- |
| OWASP | 8g | 1,000 s | 1,200 s | Workflow all-rule selection |
| conductor | 8g | 1,080 s | 1,200 s | Warning/error rules and prescribed conductor extensions |
| ThingsBoard | 12g | 1,080 s | 1,200 s | Warning/error rules |
| Stirling-PDF | 8g | 1,080 s | 1,200 s | Warning/error rules |

`B/main-plan.json` records the models and fixed settings.
`P/candidate-plan.json` pins the corrected commit, validation, tools, inputs
and baseline reuse. No alternate project configuration is used.

Before the original study, 48 prior generated raw profiles, heaps and
binary-index files were removed: 98,462,872,025 bytes. Before the corrected
retest, four superseded candidate JFR/native files were removed:
232,537,257 bytes. Logs, SARIF, failure/source/control evidence and the main
recordings were retained. The cleanup receipts are in
`B/preparation/previous-raw-cleanup-20261018T111109.655292Z.json` and
`P/preparation/old-candidate-cleanup-20261018T123524.305354Z.json`.

## Main results

Main uses the legacy engine. **Numbered bidirectional iterations do not
apply to main.** Its logged full phase can include cancellation,
confirmation and trace generation. It is not a pure engine or run-1 timer.

| Project | Terminal status | Command wall time, s | Legacy full phase, s | Maximum sampled process RSS, bytes | Trace attempts / generation failures |
| --- | --- | ---: | ---: | ---: | ---: |
| OWASP | OK | 118.212115 | 43.189 | 9,099,083,776 | 4,112 / 0 |
| conductor | OK | 91.179482 | 24.480 | 9,964,941,312 | 7 / 0 |
| ThingsBoard | TIMEOUT | 962.402442 | 780.291 | 14,425,485,312 | 13 / 0 |
| Stirling-PDF | OK | 49.095422 | 5.573 | 9,731,665,920 | 17 / 0 |

All four main runs produced SARIF. Trace attempts are not unique
rule/location identities. ThingsBoard's output is partial because its
analysis timed out. Its trace count does not establish completion. Exact
phase and trace controls are in `B/preparation/main-closed-controls-20261018.json`;
actual terminal, source and process-closure records are referenced by
`P/baseline-reuse.json`.

Main full analysis receives 80% of the staged budget left after prescan.
The bidirectional driver receives the remaining staged budget. Thus the
same configured timeout does not assign equal pure-engine budgets. The
source bindings are in `B/preparation/timeout-budget-source-note.json`.
This also explains why main ThingsBoard stopped before the harness's
near-limit profile window.

## Corrected candidate results

The corrected OWASP command started at 12:37:29 UTC. Its run-1 engine started
at 12:38:59.929 UTC with field limit 1. The terminal receipt is
`P/candidate-launches/OWASP/terminal.json`; the final iteration packet is
`P/candidate-launches/OWASP/live-progress-20261018T125501.277413Z.json`.
The engine logged run-1 processing at 12:43:19.962 UTC, 260.033 seconds
after START, with work still in flight. This proves the 60-second target
was missed without relying on a live wrapper or report barrier. The
separate observation is
`P/candidate-launches/OWASP/run1-processing-observation-20261018T124325.004895Z.json`.

| Project | Latest reached | Latest completed engine | Latest accepted forward report | Current state |
| --- | --- | --- | --- | --- |
| OWASP | run-7 | run-6 | run-5 | TIMEOUT |
| conductor | run-1 | None | None | OOM |
| ThingsBoard | run-1 | None | None | TIMEOUT |
| Stirling-PDF | run-2 | run-1 | run-1 | OOM |

| Project / iteration | Direction | Engine time | Barrier time | Iteration time |
| --- | --- | --- | --- | --- |
| OWASP / run-1 | FORWARD | 270.489967070 s | 2.606853760 s | 273.105804268 s |
| OWASP / run-2 | BACKWARD | 141.313403714 s | 0.460966631 s | 141.774617837 s |
| OWASP / run-3 | FORWARD | 50.408218461 s | 2.203927756 s | 52.612361584 s |
| OWASP / run-4 | BACKWARD | 150.733524154 s | 0.641541084 s | 151.375398438 s |
| OWASP / run-5 | FORWARD | 51.629267055 s | 2.160609050 s | 53.790081237 s |
| OWASP / run-6 | BACKWARD | 223.460816609 s | 1.315883912 s | 224.777001486 s |
| OWASP / run-7 | FORWARD | 33.036334107 s, TIMEOUT | Not run | Not available |
| conductor / run-1 | FORWARD | 116.896807317 s, OOM | Not run | Not available |
| ThingsBoard / run-1 | FORWARD | 873.914412440 s, TIMEOUT | Not run | Not available |
| Stirling-PDF / run-1 | FORWARD | 63.304167697 s | 1.185833970 s | 64.490242219 s |
| Stirling-PDF / run-2 | BACKWARD | 117.133398589 s, OOM | Not run | Not available |

OWASP command wall time was **1,039.590061 seconds**. Its maximum sampled
process RSS was **10,248,540,160 bytes**. It produced SARIF and both requested
native CPU captures. The source maps and jar matched after actual process
closure. No OOM heap was produced. Finding equivalence remains unassessed.
Corrected conductor started at 12:55:33 UTC. Its run-1 engine started at
12:56:44.560 UTC and ended with OOM after **116.896807317 seconds**. The
60-second target was missed. Command wall time was **189.399921 seconds**;
maximum sampled process RSS was **10,140,884,992 bytes**. The memory guard
captured an **11,554,102,010-byte heap** from 12:58:25.070 to 12:58:41.423
UTC. That 16.353-second capture falls inside the engine timer. The normal
30-second native capture closed, and continuous JFR and SARIF are present.
There is no near-limit capture because the engine ended before that window.
The terminal receipt is `P/candidate-launches/conductor/terminal.json`;
its final control packet is
`P/candidate-launches/conductor/live-progress-20261018T125927.236354Z.json`.
The source maps and jar matched after process closure. No implementation
exception recurred. Heap owners and finding identities remain unassessed.

Corrected ThingsBoard started at 13:00:18 UTC under the fixed settings. Its
run-1 engine started at 13:04:16.673 UTC with field limit 1. It timed out
after **873.914412440 seconds**, with no completed engine or accepted report.
The 60-second target failed. Command wall time was **1,113.773137 seconds**;
maximum sampled process RSS was **14,748,823,552 bytes**. Both requested
native captures closed. Continuous JFR and SARIF are present. No OOM heap
was produced. Source maps and jar matched after actual process closure.
The terminal receipt is `P/candidate-launches/thingsboard/terminal.json`.
Its prior active-engine lower bound is retained in
`P/candidate-launches/thingsboard/run1-processing-observation-20261018T130654.076497Z.json`.

Corrected Stirling-PDF started at 13:20:29 UTC under the fixed settings. Its
run-1 engine started at 13:21:19.884 UTC. The start control is
`P/candidate-launches/Stirling-PDF/live-progress-20261018T132219.989035Z.json`.
Run-1 completed in **63.304167697 seconds**, exceeding the target. Its
barrier completed, and its forward report was accepted. Run-2 started at
13:22:24.375 UTC. This transition is bound by
`P/candidate-launches/Stirling-PDF/live-progress-20261018T132324.146782Z.json`.
Its normal native window spans the run-1/run-2 boundary; it cannot be
reported as pure run-1 CPU. Run-2 ended with OOM after **117.133398589
seconds**. Command wall time was **235.443497 seconds**; maximum sampled
process RSS was **10,340,794,368 bytes**. The memory guard captured a
**12,045,341,412-byte heap** from 13:24:03.334 to 13:24:21.492 UTC; those
18.158 seconds fall inside run-2. The normal native capture closed, and
continuous JFR and SARIF are present. No near-limit capture was possible.
Source maps and jar matched after actual process closure. Evidence is in
`P/candidate-launches/Stirling-PDF/terminal.json` and
`P/candidate-launches/Stirling-PDF/live-progress-20261018T132449.653169Z.json`.

An accepted forward report requires the actual forward frontier after the
report update. A completed engine and completed report barrier are separate
observations. Report acceptance does not establish confirmed findings,
analysis completion or precision acceptance. No run-0 is used.

## Strict finding comparison

The strict comparator preserves the full rule identity, source location and
span. It also counts each occurrence. It does not equate nearby spans or
collapse findings by line alone. All eight actual SARIF outputs were read.

| Project | Main identities | Candidate identities | Common | Missing | New | Removed occurrences | Extra occurrences |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 4,112 | 4,112 | 0 | 0 | 0 | 5,067 |
| conductor | 7 | 0 | 0 | 7 | 0 | 7 | 0 |
| ThingsBoard | 13 | 0 | 0 | 13 | 0 | 13 | 0 |
| Stirling-PDF | 17 | 64 | 15 | 2 | 49 | 2 | 146 |

The project no-loss gate fails: **22 strict baseline identities are missing**.
Conductor's seven and ThingsBoard's 13 are absent after an interrupted
run-1, before any accepted forward report. The memory/timeout failure
prevents the analysis from producing the required report. These outcomes
do not isolate a semantic transfer failure. Main ThingsBoard also timed
out, so its 13 findings are only a partial baseline.

OWASP preserves all 4,112 measured identities. Its 5,067 extra occurrences
affect 2,420 existing identities. Thus equal location sets do not establish
equal output multiplicity. The final candidate report is from run-5, with
no confirmed findings in that forward frontier. It is not a completed
analysis or a precision pass.

The measured source explains a grouping difference. Main buckets results by
`(ruleId, statement)` and merges full rule variants. The bidirectional
report key includes the full rule object, method and statement, and exports
one simple result per full key. Same-key origins and marks remain witnesses;
they are not separate result entries. The report replaces its demand map at
each accepted forward run. Run-5's frontier, the final demand map and SARIF
all contain 9,179 results, so repeated report appending does not explain the
extra occurrences. The observed multiplicities are 1→2 for 1,571 identities,
1→5 for 824, and 1→9 for 25. Their weighted excess is 5,067. SARIF does not
serialize which full-rule fields differ for each duplicate; that finer
classification remains unavailable. The source and measured-main bindings
are in `P/reviews/owasp-occurrence-source-20261018T134645.063615Z/` and
`P/reviews/owasp-occurrence-measured-main-source-check.json`.

The two strict missing Stirling-PDF identities are XSS findings. The
comparison finds candidate results at different spans in the same source
methods: EML lines 133:17–134:77 versus 148:13–149:84, and Pipeline
90:17–91:93 versus 130:13–130:24. Current source supplies a report-location
mechanism: exit-rule results use a virtual exit; the simple result has no
thread flow, and location mapping can select the last return. The paired
locations remain **strict missing/new**, not an equality exemption. Without
a per-finding concrete trace, equivalent flow at each return is unproved.
Run-2's OOM also prevents later confirmation.

The 49 new PDF identities comprise **28 path-traversal and 21 XSS**
identities, with 143 occurrences. Three additional occurrences are on
common identities, giving 146 extra occurrences in total. Each new
occurrence has zero code flows. The accepted run-1 frontier has 161 DEMAND
and zero CONFIRMED findings. These are unconfirmed candidates. The data
does not prove that all 49 are false positives or that all are valid.

Exact identities, spans, occurrence changes, paired location candidates and
source hashes are in `P/comparisons/main-corrected-1/` and
`P/reviews/closed-runtime-native-findings-20261018T133944.709077Z/`.
The comparator's first attempt stopped before comparison because its output
parent was absent. The parent was created; the unchanged comparator then
completed. The failed prelaunch log is retained. No analyzer was rerun.

## CPU and allocation work

All seven available native captures parsed with zero malformed lines. Each
capture requests 30 seconds. The normal trigger is full-analysis start plus
60 seconds. The late trigger is IFDS start plus the configured timeout minus
60 seconds. Short main phases did not reach the normal trigger; absent
profiles are unavailable, not zero CPU.

The percentages below count a stack's weight once for each named context.
The denominator includes all valid samples, including JVM/native/GC stacks.
Contexts can overlap. These are sampled code contexts, not additive or
removable time shares.

| Candidate capture | Samples | Caller routing / fixed reachability | Request stores / mark sets | Static packed body | Cleaner chain / kernel | GC signal |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP normal | 7,993 | 0.00% | 2.09% | 0.39% | 33.78% | 61.17% |
| OWASP late | 5,406 | 0.00% | 0.00% | 0.02% | 1.26% | 44.73% |
| conductor normal | 33,667 | 0.06% | 0.09% | 20.00% | 0.86% | 75.63% |
| ThingsBoard normal | 18,430 | 48.83% | 14.47% | 0.99% | 1.05% | 34.58% |
| ThingsBoard late | 24,896 | 24.72% | 10.58% | 9.88% | 0.68% | 56.31% |
| PDF normal | 35,736 | 0.00% | 0.01% | 2.60% | 3.81% | 38.82% |

OWASP normal, conductor normal and both ThingsBoard captures lie within
run-1. OWASP's late window crosses run-6, its barrier and run-7;
it is not run-1. PDF's normal window overlaps run-1 for only 2.615 seconds,
its barrier for 1.186 seconds and run-2 for about 26.198 seconds. Do not use
its whole capture as a run-1 profile. None of the nominal native windows
overlaps a heap dump. Capture endpoints are nominal start plus 30 seconds;
the harness's later join timestamp is not the sample endpoint.

ThingsBoard's actual stacks show `publishSourceDelta` and `subscribeView`
calling `CallerOwnedStaticRequests.offer`, including monitor entry and mark
set rebuilds. This supports a request-service hotspot, rather than an
inference from generic AP frames. OWASP has no caller-owned static views;
its normal capture instead shows cleaner work, including type filtering
in 13.01% of samples. Conductor's normal window has strong GC and packed
static-body signals; those do not identify a retained owner by themselves.
The sole main native capture, ThingsBoard normal, is dominated by legacy
subscription code (99.14%). The full class paths in the primitive evidence
distinguish that legacy code from the new subscription manager.

All eight continuous JFR recordings exported successfully. The reader
preserved actual stack truncation flags, used a 256-frame cap and streamed
events without a large intermediate event file. Each export verified source,
recording and process closure. All engine intervals cited below lie within
the union of retained chunk ranges; this does not prove continuous sampling.

| Candidate engine interval | Java CPU samples | Sampled allocation weight, bytes | Clipped top-level GC pauses, s |
| --- | ---: | ---: | ---: |
| OWASP run-1, COMPLETE | 8,172 | 504,269,538,320 | 4.5299 |
| conductor run-1, OOM | 20,667 | 576,436,251,368 | 37.0152 |
| ThingsBoard run-1, TIMEOUT | 118,740 | 1,398,340,690,120 | 28.8111 |
| PDF run-1, COMPLETE | 16,573 | 457,813,924,840 | 5.6448 |
| PDF run-2, OOM | 26,363 | 699,969,264,064 | 40.7047 |

These weights estimate allocation traffic, not exact allocated bytes or live
memory. In ThingsBoard, `FixedPrescanReachability.search` is the leading
allocation site: **133,727,072,856 bytes of sampled weight**, or 9.5633%.
It allocates a Boolean array and an integer stack sized to all 48,000
components for each uncached SCC pair. The latest observed cache contains
704,682 pairs. This is direct evidence for repeated fixed-graph search
allocation. It does not make all graph-service CPU removable.

GC pause wall time differs from GC CPU across parallel and concurrent
workers. The native GC percentages and clipped pause table must not be
subtracted from each other. The conductor and PDF interrupted engine
intervals include their separately stated heap-capture intervals.

Evidence: `P/analysis-tools/native-closed-1.json`,
`P/analysis-tools/runtime-closed-1.json`, the eight
`P/jfr-results/<project>-<side>-1/receipt.json` files, and
`P/reviews/jfr-summary-20261018.json`.

## Request workload

The fixed graph removes the old wave/replay objects, but it does not bound
productive request joins. The run-1 counters expose large repeated mark
processing:

| Run-1 counter | OWASP | conductor | ThingsBoard | PDF |
| --- | ---: | ---: | ---: | ---: |
| Scalar new initials; excludes packed initials | 36,592 | 55,303 | 178,507 | 87,392 |
| Fresh FLOW input reads | 7,495 | 160,480 | 3,696,480 | 11,288 |
| Request geometry tests | 23,297 | 242,742 | 613,617 | 16,559 |
| Request mark-member scans | 191,575 | 4,593,070 | 1,820,403,014 | 663,344 |
| Offered outgoing request members | 61,284,189 | 12,393,638 | 324,655,547 | 1,107,468 |
| Committed Base plus static request memberships | 299,471 | 602,031 | 20,947,699 | 161,539 |
| Target climb candidates | 6,431 | 1,881,293 | 93,685,420 | 211,804 |

These units are not interchangeable. A mark-member scan is not a distinct
fact. The committed count is summed across method/slot rows. It is not the
number of unique marks or a retained-byte measurement. Interrupted runs can
leave fresh temporary requests uncommitted. `freshFlowInputReads` counts
FLOW only. The old `allCommittedBaseMembers` name now includes Base and
static requests; the table gives its actual population.

ThingsBoard has 169,555,092 authoritative duplicate offers and 131,829,534
temporary duplicate offers. Its current join code gathers the matching mark
set for each input before it filters the destination's committed requests,
then unions by destination. Thus merging transport does not remove repeated
member scans across eligible input bindings. This is a source-supported
work-avoidance target. Request geometry and actual-mark checks still need
to preserve their ordinary semantics.

The largest observed run-1 cache sample has 105,153 source references,
215,869 views, 608,590 subscriber references and 37,343,414 view mark
memberships. These are periodic gauges, not exact final live populations.
OWASP's corresponding view counts are all zero, despite its 61.3 million
request offers. A single static-specific explanation does not cover all
four projects.

## Memory owners and causes

Conductor's capture is **run-1/FORWARD**, with no completed engine or
accepted report. PDF's capture is **run-2/BACKWARD**, with run-1 completed
and accepted. These are different lifetimes. PDF's later heap does not
measure its run-1 peak. Neither OWASP nor ThingsBoard produced an OOM heap;
their timeouts are not OOMs.

| Heap observation | conductor run-1 | PDF run-2 |
| --- | ---: | ---: |
| Reachable physical objects | 224,619,895 | 236,674,964 |
| Complete reachable shallow heap, bytes | 7,611,768,576 | 7,617,586,672 |
| Method summary store owner scope | 838,351,344 B | 493,385,936 B |
| Method statement-directory owner scope | 266,635,424 B | 3,389,495,568 B |
| Ordinary caller input owner scope | 80,341,472 B | 203,246,520 B |
| Caller positional-index owner scope | 85,166,248 B | 132,015,920 B |
| Unresolved-observer input owner scope | 664,392,968 B | Run-1 service absent |
| Per-site P/A request scope | 14,754,056 B | Run-1 service absent |
| Authoritative method request scope | 5,014,504 B | Run-1 service absent |
| Shared static-view mark scope | 3,348,136 B | Run-1 service absent |
| Fixed reachability pair-cache scope | 6,973,784 B | Run-1 service absent |
| Packed static lanes/subscriptions scope | 227,806,712 B | Run-1 service absent |
| Ordinary rule-cache scope | At least 362,860,960 B; 80-class cap | 176,394,648 B; uncapped |
| Forms/context scope | At least 165,567,312 B; 80-class cap | At least 220,152,664 B; 80-class cap |
| IR RAM scope; out of scope for optimization | 2,246,810,840 B | 978,255,728 B |

**Retained owner scopes overlap. Do not add them or describe them as
removable bytes.** Physical groups, trie nodes and arrays are not distinct
semantic facts. The complete shallow census and each retained scope have
different meanings. Owner tables show only the first 80 samples where
capped. Exported method keys are heap identities; human method signatures
and global top methods are unavailable from this packet.

Conductor's large core owners are not its five-megabyte authoritative
request store. The observer scope contains **1,760,022 accumulator entries**,
compared with 314,524 physical GroupKeys in ordinary caller inputs. The
complete class census has zero `ExternalMethodTracker` instances. Current
source still exposes `JIRInterpreter` as an observer when its tracker is
null. The engine retains concrete observer inputs, then the callback returns
without an effect. Its packed static path can also scalarize for this unused
observer capability. This is a specific source of avoidable auxiliary state
and work. Ordinary caller bindings still serve full summary/ND application;
they cannot be removed merely because both use the same accumulator class.

Conductor's summary scope contains **2,045,888 ConclusionGroup objects,
4,091,776 HashMaps, 2,045,888 Boolean arrays and 4,115,236 ArrayLists**.
Current run-1 adds the same retained delta to the raw and selected summary
unions. These unions agree at completed run-1 handoff, since every raw result
is selected without restriction. They need not agree at an interrupted
instruction prefix, and later restricted runs require separate unions.
This supports avoiding a duplicate run-1 relation. It does not justify
halving the 838 MB estimate: shared values, interners and metadata remain.
The source review is
`P/reviews/summary-union-observer-source-20261018T140812.195819Z/`.

PDF's run-2 memory pattern is different. Its statement-directory scope alone
is 3.39 GB, with **7,779,328 ConclusionGroup and 7,907,242 TaintTree objects**.
`MethodEdgeStore` stores a conclusion group per premise and statement, with
a dense statement array per premise. This identifies retained method state,
not old static routing waves or a large live request table. It does not
establish the number of semantic fact atoms or the occupancy of every dense
array. Run-1-only classes have zero instances in the complete census and
Q04's current `staticRequests` is null, which independently confirms their
release. This later-run issue is separate from the first run-1 memory work.

### Heap-tool result and qualification

Conductor's normal nondiscard Java 21/MAT read completed in **412.069 s**,
with all 35 queries exporting nonempty results. Capture, source and packet
bindings stayed stable; all owned processes closed.

PDF's MAT process exited zero after **437.663 s**, produced its archive and
closed all owned processes. The strict wrapper remains **FAILED/PRESERVED**:
one inspection caught its own Java process with an empty command line during
shutdown. No retry or acceptance override was made. Its completed exported
data supports the qualified owner review above. All 35 outcomes were
inspected: 26 nonempty CSVs, six empty selections, and three no-result query
errors for run-1-only retained selections (Q21, Q22, Q24). These query errors
are unavailable results, not measured zeros. The independent whole-heap
census and current owner fields support the service-absence statement.

Before the actual reads, the heap launcher needed an environment adapter
for protected `/proc/.../exe` links and an exiting task with its userspace
memory already released. It retained readable process classification,
exact installed-Java identity for MAT, the same 35 queries, the same 20 GB
normal nondiscard settings, source/capture gates and serial locks. The
original prelaunch failures and adapter sources/controls are retained. No
analyzer, query or benchmark configuration changed.

Both reviews, exact query outcomes and tool states are in
`P/heap-tools/interpretation/{conductor,Stirling-PDF}-owner-review.json`.
`P/heap-tools/serial-mat-handoff-1.json` confirms actual closure, unchanged
packet pins and all three locks available after both reads. No heavy reader
remains active.

Periodic `Memory usage` and capture-start `heapUsed` use
`Runtime.maxMemory() - Runtime.freeMemory()`. They can overstate used heap
by the uncommitted capacity, `maxMemory - totalMemory`. They are raw
MemoryManager estimates. The GC listener separately uses the sum of
after-GC heap-pool usage for its threshold decision. The source bindings
are in `B/preparation/memory-log-scope-source-note.json`. No runtime change
is made for this measurement issue.

## Validation and the corrected boundary

The [implementation map](../design/bidirectional-caller-owned-subscriptions-implementation-20261018.md)
and [validation report](bidirectional-caller-owned-subscriptions-validation-20261018.md)
record the selected code and checks. The full common suite passes **1,607
tests**, and the JVM suite passes **411 tests**, with no failures, errors
or skips. The analyzer jar builds. Evidence is in `B/validation/full-core-8/`.

The application selection has **148 passes, nine precision failures and
one skip**. Its precision gate fails. Each failed case's own log block
reached, completed and accepted **run-9**. These are application test
iterations, separate from project measurements. Eight failures serialize
356 expected and 382 actual rule memberships: zero missing and 26 extra.
The ninth reports one finding without its identity. These sets do not
preserve strict location or occurrence multiplicity. Fresh evidence is in
`B/validation/application-2/failure-analysis.json` and its receipt.

The source review identifies sequential EXACT/BELOW cleaner residuals as a
matching kernel mechanism. It is not a captured causal trace for each
finding or an isolated parent-commit regression test. Assertions, expected
findings and filters were not weakened.

The superseded conductor run reached run-1 and failed on a whole-batch
Base assertion. One request batch can contain Base and static-field rows.
The corrected concrete visitor selects matching Base rows before checking
actual marks. Static concrete admission remains eager, and productive FLOW
joins retain their own static rows. Two focused tests reproduced the old
assertion; all three new regressions pass in the full gate. No request
storage, initial family or further optimization was added.

## Assessment and next iteration

The selected implementation and mixed-batch correction are committed. The
full core gate is green. The application precision gate, strict project
no-loss gate and all four 60-second run-1 gates fail. No candidate completes
the full configured analysis: two timeouts and two memory stops, compared
with three successful main runs and one main timeout. Thus this matrix does
not establish an overall performance improvement over main. The budget
allocation difference and legacy/full-phase scope above still apply.

No fresh same-policy parent-candidate A/B was run. Superseded candidate
results are not used to claim the isolated speedup or regression from this
optimization. The current data instead identifies the remaining costs:
unused observer state and duplicate summary unions in conductor run-1,
request/search traffic in ThingsBoard run-1, cleaner work in OWASP run-1,
and stored statement state in PDF run-2.

The [new optimization proposal](../design/bidirectional-next-iteration-after-caller-owned-subscriptions-20261018.md)
ranks expected run-1 memory impact, separates CPU/allocation benefits, and
states current behavior, new behavior, proof obligations and limits. It
starts with avoiding disabled observation and duplicate run-1 summary
state. No proposed optimization is implemented here. IR storage and
reference-manager rewrites remain outside implementation scope.
