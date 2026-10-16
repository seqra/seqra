# Request-gated run-1 statics and field preservation: project measurements

Date: 16 October 2026. Runtime: `28a123f0eac937e8f2feca9ee5dd84e38fa8f979`.
Reference: main `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, verified from the
remote at the start of this study. These results replace earlier runtime
measurements for this iteration. CPU, normal heap ownership and the targeted
weighted-input/publication reviews are complete.

The implementation is committed. The fixed-project acceptance goal fails:
main completes all four projects; the selected runtime times out on OWASP
and runs out of memory on the other three. Two projects complete run-1
within the **60-second engine target**. The strict location comparison has
16 missing main identities and 342 additional candidate identities.

## Runtime and inputs

The [unified specification](../../spec/run1-field-writes.md) includes field-read
requests, static mark requests, initial emission, selective field preservation
and ordinary static defaults. The [implementation design](../design/bidirectional-request-gated-static-handling-20261016.md)
uses the same contract. IR storage, SoftReferenceManager and identity-cache
policy are unchanged.

All eight cases use `:runProjectAnalyzer` directly with JDK 17. Main runs
first, then the selected runtime, with each project run in sequence. The
configurations are fixed: OWASP 8g/1000s, conductor 8g/1080s, ThingsBoard
12g/1080s and Stirling-PDF 8g/1080s. The analyzer outer limit is 1200s.
Conductor uses its configured extension rules, passthrough models and compiled
dataflow models. Stirling-PDF uses the external model specified by the test
setup. No alternate configuration or diagnostic rerun is included.

Before and after audits both pass all **80 checks across 41 datasets**.
The dataset fingerprints, rule sets, models, dependencies and toolchains
match. Both phase source maps and all eight runtime bindings stay stable.
The input endpoint check is not continuous monitoring.

Core validation passed 1,657 tests. The application selection remains a
strict precision failure: 148 passed, 9 failed and one existing skip, with
zero errors. See the separate [validation checkpoint](bidirectional-run1-selective-preservation-validation-20261016.md).
Those failures were not removed or weakened to start these measurements.

## Terminal status and latest iterations

An accepted forward report can contain provisional DEMAND candidates. It
does not establish that the next backward/forward refinement completed.

| Project | Main status / command seconds | Selected status / command seconds | Latest reached | Latest completed | Latest accepted forward report |
| --- | --- | --- | --- | --- | --- |
| OWASP | OK / 120.25 | TIMEOUT / 1039.58 | run-7, F, limit 7 | run-6, B, limit 6 | run-5, F, limit 5 |
| conductor | OK / 92.21 | OOM / 189.51 | run-2, B, limit 2 | run-1, F, limit 1 | run-1, F, limit 1 |
| ThingsBoard | OK / 223.39 | OOM / 392.91 | run-1, F, limit 1 | None | None |
| Stirling-PDF | OK / 47.13 | OOM / 259.51 | run-2, B, limit 2 | run-1, F, limit 1 | run-1, F, limit 1 |

F means FORWARD; B means BACKWARD. Main uses the legacy engine and has no
bidirectional run number. A harness exit of zero means that capture and
terminal recording finished; the analyzer statuses above remain failures.

The selected runtime is slower in full command time on all four projects
and does not finish their analyses. These are single sequential measurements,
with setup, JIT, GC, filesystem cache and diagnostic costs. They do not give
a controlled estimate of the speedup from one individual change.

## Engine and barrier times

| Project | Iteration | Engine seconds | Barrier seconds | Engine status |
| --- | --- | ---: | ---: | --- |
| OWASP | run-1 F / 1 | 263.421 | 2.628 | COMPLETE |
| OWASP | run-2 B / 2 | 136.440 | 0.389 | COMPLETE |
| OWASP | run-3 F / 3 | 52.063 | 2.208 | COMPLETE |
| OWASP | run-4 B / 4 | 148.301 | 0.655 | COMPLETE |
| OWASP | run-5 F / 5 | 53.088 | 2.179 | COMPLETE |
| OWASP | run-6 B / 6 | 226.202 | 1.240 | COMPLETE |
| OWASP | run-7 F / 7 | 41.224 | Unavailable | TIMEOUT |
| conductor | run-1 F / 1 | 49.567 | 0.571 | COMPLETE |
| conductor | run-2 B / 2 | 60.981 | Unavailable | OOM |
| ThingsBoard | run-1 F / 1 | 176.913 | Unavailable | OOM |
| Stirling-PDF | run-1 F / 1 | 36.586 | 0.263 | COMPLETE |
| Stirling-PDF | run-2 B / 2 | 167.193 | Unavailable | OOM |

Conductor and Stirling-PDF pass the run-1 target. OWASP fails it. ThingsBoard
passes 60 seconds before its heap capture starts, then fails to complete.
Its failed engine timer includes 37.94 seconds of heap capture. Conductor's
failed run-2 timer includes 16.73 seconds of heap capture. These timers also
include GC and guard work; they are not uninterrupted solver CPU time.

| Selected project | Prescan seconds | Static metadata seconds | Static catalog seconds |
| --- | ---: | ---: | ---: |
| OWASP | 32.101 | 37.665 | 0.152 |
| conductor | 25.184 | 7.806 | 0.480 |
| ThingsBoard | 128.106 | 48.893 | 2.198 |
| Stirling-PDF | 17.477 | 8.111 | 0.235 |

These setup times are outside the run-1 engine target. Main's reported full
scan intervals are 45.710, 24.969, 30.711 and 5.084 seconds respectively;
those intervals include its trace-generation phase. Do not compare them as
if they were the same operation as a selected run-1 engine.

## Exact vulnerability locations

The comparison preserves rule, full sink identity, raw source span, each
occurrence and multiplicity. Different return statements are different
locations. No equal-ID location changes or input differences were found.

| Project | Main unique identities | Retained | Missing | Additional unique candidates |
| --- | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 4,112 | 0 | 0 |
| conductor | 7 | 7 | 0 | 293 |
| ThingsBoard | 14 | 0 | 14 | 0 |
| Stirling-PDF | 17 | 15 | 2 | 49 |

OWASP has 5,067 additional occurrences and 2,420 multiplicity changes,
despite having no additional unique identities. Its final accepted report
has 9,179 DEMAND findings and zero CONFIRMED findings. Location retention
does not establish semantic truth or completion of run-7.

ThingsBoard has no accepted forward report after its run-1 OOM. The observed
cause of its empty output is resource exhaustion before report admission;
this does not identify a separate per-method transfer defect.

Stirling-PDF's two missing identities are distinct earlier return locations.
The selected output instead contains later return locations in the same
methods. They remain missing under the strict comparison. The source trace
identifies a report-location defect: all real returns feed a synthetic normal
exit, the exit sink witness keeps that boundary, and `Simple` export supplies
no trace. `TraceMessageBuilder` then maps the boundary to the last return.
The graph does not discard earlier returns. The missing EML span is lines
133–134, while the selected span is 148–149; Pipeline's missing span is 90–91,
while the selected span is 130. No branch or catch-path equivalence is claimed.
Its run-1 report was accepted and handoff occurred before run-2 failed.

Conductor logs 301 DEMAND findings but exports 300 occurrences. One candidate
has a logged invalid location at the lambda-enriched NATS `createDispatcher`
call. This is a proven dropped candidate location; it is separate from the
seven retained main identities.

The 342 additional unique candidates are not all proven false positives.
Conductor and Stirling-PDF stop with their provisional run-1 reports before
strong refinement completes. Their output growth is far above the desired
small precision difference. Source triage and the exact missing/additional
identity tables must accompany any precision claim.

## Request work and initial admission in run-1

The following are cumulative reported operation counts, not retained bytes.
The failed ThingsBoard row covers its executed prefix only.

| Project | Input arrivals | Initial candidates | New initial memberships | Offered request members | Already admitted | Already pending | Fresh pending members |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 173,104 | 211,647 | 36,592 | 61,356,296 | 42,731 | 61,014,094 | 299,471 |
| conductor | 1,696,299 | 1,263,969 | 110,449 | 22,568,844 | 8,650,672 | 13,143,266 | 774,906 |
| ThingsBoard | 6,731,521 | 6,621,472 | 535,668 | 223,698,191 | 98,375,113 | 108,012,474 | 17,310,604 |
| Stirling-PDF | 399,115 | 382,289 | 90,247 | 1,379,472 | 108,217 | 1,076,295 | 194,960 |

The request stores merge memberships, but duplicate production still reaches
admission. OWASP's pending duplicates account for about 99.44% of its offered
members. ThingsBoard's admitted and pending duplicates together account for
about 92.26%. These counters show redundant offers; they do not by themselves
give their CPU share or the number of distinct propagated facts.

ThingsBoard creates 4,707,790 caller input groups and 18,819,409 index postings
over the observed run-1 prefix. These are construction totals. Terminal live
gauges are released during cleanup, so a zero terminal gauge is not a zero
at the OOM snapshot. Heap ownership is required to establish live storage.

All four static catalogs report zero logical precomputed field-mark request
rows/members and zero physical precomputed rows. This does not mean that F
is empty or that there are no dynamic static mark requests. F and dynamic R
have separate roles. In ThingsBoard, the closed field-read plan reports
15,706,500 logical method memberships across 7,248 abstraction-set roots.
These logical counts are not a physical retained-byte census.

## CPU and allocation evidence

All eight JFR exports pass the selected-event census and recording-stability
checks. Finalized recording header ranges cover all four selected run-1
intervals and all four main full-scan intervals. No early run-1 rollover loss
was found. The actual JVM records stack depth 256; the run-1 windows have no
recorded TRUNCATED events. Missing allocation stacks remain in their original
denominators. Those checks do not prove every possible caller was visible.

OWASP's full run-1 has 10,011 Java execution samples and about 511.28 GB of
sampled allocation weight. Cleaner handling accounts for 53.41% of execution
samples and 70.37% of sampled allocation weight. `FlowClean.run` accounts for
22.99% and 24.94% respectively. `JIRCallPlanBuilder.build` is present in 19.34%
of execution samples. These are inclusive, overlapping stack memberships;
sampled allocation is neither exact allocated bytes nor retained memory.

The cleaner source path includes field read, FormApplier input/result
filtering, `filterPath` and `JIRFactTypeChecker` field-type/hierarchy checks.
Thus repeated analyzer type/geometry work is a measured optimization lead.
It does not require changing IR storage.

ThingsBoard's failed run-1 shows call-plan construction in 33.75% of Java
execution samples and 19.63% of sampled allocation weight. Cleaner chains
account for 17.10% of sampled allocation weight; their CPU row is outside the
retained top-50 ranking, which is unavailable rather than zero.

The failed run-2 windows shift toward conclusion insertion:
`ConclusionGroup.add` accounts for 37.60% of execution samples and 52.48% of
sampled allocation weight in conductor, and 34.35% / 57.52% in Stirling-PDF.
This supports investigating repeated response/insertion work as well as
request production.

Native 30-second captures provide separate evidence. OWASP's normal window
is wholly run-1; its near-limit window crosses run-6, a barrier and run-7.
ThingsBoard's normal window is run-1, while conductor and Stirling-PDF's
normal windows are run-2. No main native window was scheduled because each
full scan finished before the trigger. Main CPU is available through JFR.
Near-limit windows for the three early OOM cases are unavailable.

In the native normal window, OWASP's cleaner family appears in 66.13% of
samples, request admission in 7.77%, and GC in 23.29%. ThingsBoard's request
admission/replay union appears in 17.44%, and GC in 42.73%. These are native
stack memberships with their own denominators, not percentages of engine
wall time. ThingsBoard's window has no heap-snapshot overlap; ordinary
MemoryManager-triggered GC is distinct from the later heap capture.

Form statistics report 2,157,775 forward form build attempts on OWASP,
809,545 on conductor, 1,577,437 on ThingsBoard and 741,981 on Stirling-PDF.
The cache has statement, call, entry and exit slots. These are cumulative
construction counts, not method counts, live containers or proven rebuilds.
The counters do not distinguish first construction from later reconstruction.
Summed build durations can overlap across threads and are not a removable
fraction of engine wall time.

## Heap ownership

The fact grammar bounds one AP; it does not bound total analyzer state.
Method storage separates full method/context, original initial or complete
ND premise, statement, conclusion base and layer/header. Caller storage adds
the caller/callee relation and call occurrence. The same small canonical AP
vocabulary can therefore have millions of supported storage occurrences.
Output TAINT groups already merge mark sets under one premise. Merging
different original marks or ND premises would need a new dependency relation
and proof; unioning their marks alone would lose correlation.

Three successful guard captures are available: conductor 11,669,500,200
bytes, ThingsBoard 18,306,803,534 bytes and Stirling-PDF 12,015,090,215 bytes.
These are HPROF file sizes, not live heap sizes or possible savings. The
snapshots precede cancellation and cleanup. All three normal MAT owner
reports are complete and individually reviewed. Targeted saved-index owner
and weighted-input checks are also complete.

Conductor's snapshot is **run-2 BACKWARD / limit 2**, with run-1 completed and
accepted. Its reachable heap is 7,623,403,576 bytes in 229,596,659 objects.
Normal parsing removes 30,548 unreachable objects; no object-discard mode is
used. ThingsBoard's snapshot is **run-1 FORWARD / limit 1**, with no completed
iteration or accepted report. Its reachable heap is 11,377,203,368 bytes in
331,576,284 objects.
Stirling-PDF's snapshot is **run-2 BACKWARD / limit 2**, with run-1 completed
and accepted. Its reachable heap is 7,611,301,480 bytes in 235,878,091 objects.

The following ownership unions overlap and must not be added together or
treated as net savings:

| Owner population | conductor, run-2 bytes | ThingsBoard, run-1 bytes | Stirling-PDF, run-2 bytes |
| --- | ---: | ---: | ---: |
| Conclusion buckets, all owners | 1,907,408,768 | 2,390,922,640 | 4,403,647,952 |
| Method statement directories | 1,315,648,512 | 1,684,312,544 | 3,571,191,280 |
| Caller input stores | 349,509,160 | 2,322,932,752 | 342,045,384 |
| Caller input index, included above | 142,345,696 | 1,442,804,344 | 121,133,680 |
| Caller input accumulator, included above | 192,056,240 | 819,357,360 | 210,764,872 |
| Normal sink-role caches | 320,730,064 | 1,092,013,408 | 151,305,232 |
| Forms/context caches | 208,628,656 | 690,979,576 | 220,760,920 |
| Shared static plans/footprints | 22,692,944 | 334,207,848 | 4,665,680 |
| Admitted dynamic request maps | 978,040 | 82,567,184 | 406,448 |
| Temporary request maps | 978,040 | 10,103,456 | 406,448 |
| Queues and worklists | 15,938,224 | 65,356,224 | 8,096,592 |
| IR storage, outside optimization scope | 2,256,940,688 | 1,633,697,232 | 978,133,552 |

The method-directory union includes owned conclusions and AP subgraphs. It
does not measure dense-array overhead. All `ConclusionGroup[]` arrays have
only 52,619,016 shallow bytes in conductor and 44,883,872 in ThingsBoard;
those class totals also include any such arrays outside method directories.

The snapshot has 17,465 actors, 681,196 actor initial memberships,
665,653 non-REACH premise rows and 10,110,405 allocated non-REACH statement cells.
Cell occupancy is not established by capacity. Caller input stores contain
1,097,619 groups and 1,159,265 postings. Actor pending lists contain 1,485,029
publications. The targeted saved-index query measures only 43,707,128 bytes
retained by pending-list roots, with 30,118 distinct full premises summed
per actor. Many publication payloads have other owners. These bytes overlap
other ownership unions; they are not additive savings.
FLOW trees and frozen run-1 profile/base instances have zero histogram
instances at this later-phase capture. There are 5,549,727 TAINT trees.

Thus the run-2 memory problem is not a standing run-1 request history.
Concrete conclusion/state retention and pending summary production need
investigation. The `ConcurrentHashMap.size` cells in the canonical AP query
are unavailable; they are not zero. Two absent-profile OQL outputs are
checked against the histogram rather than silently interpreted as zero.
ThingsBoard has 71,966 actors, 535,668 initial memberships, 464,700 non-REACH
premise rows and 9,151,920 allocated non-REACH statement cells. Its caller stores have
4,707,790 groups and 18,819,408 live postings. Its authoritative dynamic
requests contain 16,023,730 mark memberships and temporary requests contain
1,286,874 at capture. Each request-map retained union is below 83 MB,
much smaller than the caller input/index or conclusion populations.
Large offered-request counters therefore do not imply a multi-gigabyte
standing request map.

ThingsBoard retains 9,579,226 FLOW trees and 1,199,862 TAINT trees. It has
only 224 globally canonical InitialAp instances, reused across actors. That
vocabulary is not the weighted initial membership count and does not locate
their static/nonstatic contributions. The evidence points to repeated
abstract propagation and auxiliary caller/index state as well as concrete
facts. The weighted check below resolves the static/original split.

Stirling-PDF has 7,258 actors and 754,143 initial memberships. Its 747,306
non-REACH premise rows allocate 22,275,047 statement cells. It retains
10,235,671 conclusion buckets and 11,589,202 TAINT trees, with no FLOW trees.
Its pending lists contain 1,975,323 publications. Conclusion/state retention
is therefore the largest measured analyzer-core owner union in this run-2;
its 4.40 GB includes both necessary payloads and avoidable containers.

ThingsBoard also retains 5,081,664 `ConclusionGroup`s, 4,571,798 `FlowGroup`s
and 565,535 `TaintGroup`s. Each conclusion bucket eagerly allocates both kind
maps. Since every nonempty kind map needs at least one base group, these
counts imply at least 5,025,995 empty kind maps. Counting base groups owned
outside conclusion buckets makes the bound conservative. This identifies
avoidable container objects; it does not estimate net retained-byte savings.
The existing group semantics already merge output marks for a common premise.

### ThingsBoard: statics dominate stored run-1 inputs

The bounded MAT API check visits actual actor membership sets and logical
caller entries. All totals reconcile with the independent normal queries.
Canonical objects serve only as a descriptor cache. It reports:

| Original initial/premise category | Actor initial memberships | Caller input groups |
| --- | ---: | ---: |
| Static, abstract mark | 372,664 | 3,797,487 |
| Static, concrete mark | 68,682 | 566,815 |
| Nonstatic, abstract mark | 22,251 | 73,769 |
| Nonstatic, concrete mark | 105 | 1,932 |
| ZERO | 71,966 | 267,787 |
| Total | 535,668 | 4,707,790 |

Static initials account for **82.39%** of memberships; static original
premises account for **92.70%** of caller groups. Initial static shapes are
377,553 field slots and 63,793 class points. The 372,664 abstract static
initials have the selected `C.f.*` shape. Concrete field/class initials use
their permitted EXACT/STAR projections. No static-root initial is present.

Original provenance and current position differ, so current input groups
have a separate classification:

| Current input kind/base | Groups |
| --- | ---: |
| Static FLOW | 3,767,181 |
| Nonstatic FLOW | 104,075 |
| Static TAINT | 567,658 |
| Nonstatic TAINT | 5,861 |
| ZERO REACH | 263,015 |

Current statics account for **92.08%** of all caller groups. Static FLOW
alone accounts for **80.02%**. This confirms a static-dominated retained
input population. It does not assign exactly that percentage of CPU or
retained bytes to statics: index paths and payloads can be shared.

All 68,787 non-ZERO concrete initial memberships form 68,787 exact-geometry
cohorts within their actors. **Every cohort has one mark.** Geometry includes
base, path, tail and E; only concrete T is omitted. Thus this snapshot does
not support multiple concrete initial marks at one geometry as the main
source of run-1 state explosion. Packed current TAINT mark widths and total
scalar leaf counts were not measured by this check.

The larger algorithmic lead is repeated abstract static propagation, with
its per-original method state and caller/index relations. Packing those
relations must retain each original-to-current dependency. Removing ordinary
call subscriptions or unioning distinct premises has no proof here.

### Pending publication count is not its retained cost

| Project / latest reached | Pending fragments | Distinct full premises, summed per actor | Pending-list retained union bytes |
| --- | ---: | ---: | ---: |
| conductor / run-2 B | 1,485,029 | 30,118 | 43,707,128 |
| ThingsBoard / run-1 F | 55,375 | 15,374 | 10,879,392 |
| Stirling-PDF / run-2 B | 1,975,323 | 26,676 | 58,852,512 |

Each pending union is below 0.8% of its reachable heap. Coalescing already
authorized pieces could reduce delivery/insertion work, but these results
give it a low memory priority. The remaining conclusion and caller relations
cannot be removed merely by changing their publication barrier.

## Evidence

The source/input/capture packet is
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-selective-preservation-preparation/verification-20261016T202647.993220Z`.
It contains `operator-endpoint-review.json`, `scans-closure.json`, both phase
directories, `terminal-metrics-1/`, `comparisons/main-current/` and
`measurement-review/profile-interpretation-1/`. Exact source spans, occurrence
tables, original control lines and profile quality denominators are retained.
Normal heap reviews are in `measurement-review/heap-owner-28a/`. Supplemental
conductor queries completed, but their first wrapper returned 4 because it
expected a separate HTML page instead of MAT's inline TextResult. Independent
review accepted the existing query/JSON results and preserved that failed
wrapper receipt. A versioned packaging checker handles the remaining visits;
the plugin and census/count gates are unchanged. Conductor was not queried
again to correct that packaging check.
`measurement-review/heap-owner-28a/final-review.json` closes all normal and
supplemental reviews, source/input/capture bindings and reader processes.
All three shared workload locks were acquired and released at closure.
The previous study's 86 raw files were removed under its exact cleanup
receipt; these three current heaps remain available for the required review.

The [next iteration proposal](../design/bidirectional-next-iteration-after-selective-preservation-20261016.md)
ranks changes from these closed CPU and retained-owner results. The 60-second
target, location retention, precision, core test gate and project completion
are separate acceptance conditions.
