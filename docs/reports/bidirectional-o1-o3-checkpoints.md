# O3 and O1 checkpoint workload results

All eight checkpoint scans have finished. O1 completes OWASP and keeps all main sink identities.
The other seven scans stop on the memory guard. These runs explain the effect of the earlier
implementation steps; the [four final project reports](bidirectional-o1-o3-implementation.md)
remain the acceptance evidence for the combined implementation.

## Revisions and comparison scope

The O3 checkpoint is `f4756a22ce025233de60dbe13a308cfc9a5f0e0b`. It has compact form containers,
legacy cube lowering and strong method-form caches. The O1 checkpoint is
`ead2d04ca9885c0e016c5edd1d11ed1198a192d2`. It includes O3 and adds NNF source and sink predicates.
Its method-form caches are still strong. The final analyzer code is
`b0a2448c0af7906ed49d90f203dd5a2a1c881523`, which also includes O2 and the final diagnostics.
The checkpoints have the recorded timing and guard-dump diagnostic backports. They are cumulative
revisions, so their differences cannot give independent percentage savings for each optimization.

Each run uses the same selected model, rules, native Temurin 17 runtime, storage batch 2,000 and
disabled multi-release loading as the primary pair. The direct Gradle task is `runProjectAnalyzer`.
The heap is 8 GiB, except for ThingsBoard at 12 GiB. The IFDS timeout is 1,000 seconds for OWASP and
1,080 seconds for the other projects. Every first run has limit 1; all later limits are positive.
No prescan error marker is present. The saved input/native checks pass for all eight runs. This
comparison uses captured input manifests and the unchanged main baseline; it is not a continuous
proof that inputs never changed between checks.

## Strict findings

Sink identity is rule ID, sink fingerprint and full method signature. A failed scan remains a
resource failure even when it retains every main identity. Added DEMAND findings from an incomplete
refinement are not certified false positives.

| Checkpoint | Project | Status | Main unique | Checkpoint unique | Retained | Missing | Added | Exported rows |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| O3 | OWASP | OOM | 4,112 | 0 | 0 | 4,112 | 0 | 0 |
| O3 | Conductor | OOM | 7 | 300 | 7 | 0 | 293 | 300 |
| O3 | ThingsBoard | OOM | 14 | 0 | 0 | 14 | 0 | 0 |
| O3 | Stirling-PDF | OOM | 17 | 61 | 15 | 2 | 46 | 150 |
| O1 | OWASP | OK | 4,112 | 4,112 | 4,112 | 0 | 0 | 9,179 |
| O1 | Conductor | OOM | 7 | 300 | 7 | 0 | 293 | 300 |
| O1 | ThingsBoard | OOM | 14 | 0 | 0 | 14 | 0 | 0 |
| O1 | Stirling-PDF | OOM | 17 | 61 | 15 | 2 | 46 | 150 |

OWASP and ThingsBoard accept no forward report when F1 fails. Their valid empty outputs therefore
lose all main identities. Conductor retains the completed F1 report: 3 CONFIRMED and 298 DEMAND
attempts produce 300 exported identities. Both PDF checkpoints retain 9 CONFIRMED and 141 DEMAND
rows after F3 fails. O1 OWASP ends with 9,179 CONFIRMED rows and no DEMAND rows. Its 4,112 unique
identities and 9,179 occurrences match the final optimized OWASP run.

## Iteration times

Times below are engine seconds. Accepted barrier time is separate. A failed iteration has no accepted
barrier. The guard interval includes forced GC and heap capture and is already charged to engine time;
do not add it again. A shorter failed scan is not a speed improvement.

| Checkpoint / project | F1 engine / barrier | B2 engine / barrier | F3 engine / barrier | Failure phase | Guard interval | Whole command |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| O3 / OWASP | 67.334 / — | — | — | F1 OOM | 19.930 | 126.257 |
| O3 / Conductor | 38.700 / 0.843 | 55.756 / — | — | B2 OOM | 17.355 | 165.294 |
| O3 / ThingsBoard | 138.865 / — | — | — | F1 OOM | 28.873 | 330.578 |
| O3 / Stirling-PDF | 38.363 / 0.201 | 18.164 / 0.078 | 44.275 / — | F3 OOM | 19.053 | 148.285 |
| O1 / OWASP | 166.299 / 0.477 | 6.575 / 0.038 | 2.988 / 0.090 | None | — | 251.411 |
| O1 / Conductor | 40.122 / 0.651 | 51.494 / — | — | B2 OOM | 17.369 | 160.338 |
| O1 / ThingsBoard | 93.881 / — | — | — | F1 OOM | 28.105 | 283.612 |
| O1 / Stirling-PDF | 28.480 / 0.201 | 16.599 / 0.086 | 54.749 / — | F3 OOM | 17.194 | 145.268 |

NNF permits OWASP to complete with a strong form cache at O1, while O3 fails in F1. Thus the final
OWASP resource pass cannot be assigned to O2 alone. Conductor and ThingsBoard still fail at both
checkpoints. For PDF, the final implementation completes F3 and reaches B4, where it times out.
That is more completed refinement, but the final fixed-budget resource gate still fails.

## PDF command difference

Both checkpoint outputs contain the main command sink in
`ProcessExecutor.runCommandWithOutputHandling(List, File)` at the `ProcessBuilder` constructor,
with fingerprint `wY2lI7b1TEw2RC154xc+UyLWgRr7oi07c8cEICJI+QM=`. Each contains two occurrences.
Both stop before the F3 barrier, so the output still contains F1 findings. The final run accepts F3
and then lacks this identity. SARIF has no per-row CONFIRMED/DEMAND property, so it cannot establish
the command row's F1 state. The later O3 heap query does establish it: both native entries, for
`os-command-injection:sink:1` and `os-command-injection:sink:3`, are retained run-1 `DEMAND` entries.
Their witness counts are two and one. The active manager is at F3/limit 3. This proves coarse F1
demand retention through the failed F3; final F1 state and the first actual mark loss remain unproved.
The O1 heap has the same two native run-1 `DEMAND` entries. Its selected witness counts are four and
two, compared with O3's two and one. Different proof representations and support shapes prevent a
semantic conclusion from these counts alone. Both checkpoints retain F1 through an incomplete F3.

The source differential and local probe do not prove an O1 or O2 defect. The positive source condition
and exact returned cleaner have the same relevant behavior at O3 and O1. The local NORMAL `$ARGS`
mark loss follows the shared source-override policy. The first actual full-run loss and its proof
history still need per-site evidence. Keep this strict difference open; do not change the cleaner
policy or model solely to restore the baseline row. The two other missing PDF identities have the
source-backed exit-location mappings recorded in the [PDF report](bidirectional-o1-o3-projects/Stirling-PDF.md).

## Evidence still under review

All seven memory failures have guard captures. Their serialized file sizes are not retained heap
sizes. Source-specific MAT queries must distinguish strong cached forms, current protocol stores,
compiled conditions, provider data and IR owners. O3/O1 caches use alternating forward/reverse
entries in `Object[]`; current O2 slot and soft-payload queries do not apply to them.

The first O3 OWASP overview has 28 successful queries. MAT indexes 293,368,874 reachable objects with
7,734,079,192 shallow bytes, after removing 28,561 unreachable objects. It uses no object-discard
options. Strong cache arrays retain 6,222,787,016 bytes; compiled forms retain 6,220,162,152 bytes;
source/sink cube roots retain 5,279,227,672 bytes. These sets overlap and cannot be added. Method
entries alone retain 6,400,972,072 bytes. The complete census has 18,460,694 sink descriptors,
55,543,007 path facts and 52,406,657 patterns. In comparison, active method-edge stores retain
11,477,600 bytes and event queues retain 682,312 bytes. All 6,345 actors are active F1 actors in
nine open runners. This snapshot identifies compiled cached forms as the dominant owner at O3;
the bounded owner queries below identify a largest method cache and a concrete condition expansion.
It is not a before/after heap saving because the O1 and final runs reach different progress points.

The later bounded queries identify the largest cached context as
`org.owasp.benchmark.testcode.BenchmarkTest00777.doPost(HttpServletRequest, HttpServletResponse)`.
Its strong call cache retains 10,202,584 bytes through a 292-cell array. Paths lead through
`FormsCache.slots`, the method's plans, the context recipe map and the main thread. That context
map retains 6,186,588,872 bytes across methods; the largest cache alone does not explain the total.

One selected 879,776-byte call plan has exactly 2,960 materialized sink objects from 25 physical bound
rule objects and 24 raw condition roots. Five XSS rules each produce 396 alternatives. The selected
alternative-root set retains 862,344 bytes, with 8,601 path facts and 8,601 patterns; the raw-condition
root set retains 20,968 bytes. These are distinct requested sets, not an isolated whole-project saving.
The query's array count matches the returned object count. This provides concrete condition-level
evidence of positive cube expansion inside a strongly cached form. All nine final condition queries
succeed. An earlier optional array-field query failed; the failure remains recorded, and later valid
scalar, child and source-specific queries supply the required owner evidence.

The O3 Conductor overview accounts for all 28 queries: 26 nonempty CSV outputs and two valid empty
standing-request outputs. The complete histogram independently has no `RequestStore` instances.
Its reachable heap has 228,806,565 objects and 7,670,902,464 shallow bytes. All 17,525 actors belong
to current B2/limit-2 work in 219 open runners. Strong cache arrays retain 955,109,440 bytes; callee
subscription state retains 954,585,968 bytes; method-edge stores retain 583,962,520 bytes. These
unions overlap. IR storage retains 2,409,815,040 bytes and remains outside scope. The O3 form cache
is material here, but it does not dominate this heap as it does in OWASP. The final Conductor guard
heap has cleared cached form references and still fails in B2; its active protocol owners are detailed
in the [Conductor report](bidirectional-o1-o3-projects/conductor.md).

All 28 O3 ThingsBoard overview queries succeed. Its reachable heap has 354,148,131 objects and
11,451,469,328 shallow bytes. All 75,618 actors are current F1/limit-1 actors in 562 runners. Strong
cache arrays retain 2,746,708,144 bytes; standing request stores retain 1,359,497,600 bytes; event
queues retain 647,244,752 bytes. These unions overlap. The complete request tables have 10,312,959
seen pairs, and the queues have 10,491,392 entries. The manager has handled 67,316,251 events at
capture, compared with 189,716,227 in the final primary heap. These are different progress points;
neither the smaller request set nor the larger cache measures a paired saving. Both snapshots show
substantial request traffic. The final run clears its form cache and still exhausts the fixed heap,
with the owners recorded in the [ThingsBoard report](bidirectional-o1-o3-projects/thingsboard.md).

The O3 PDF overview accounts for all 28 queries: 26 nonempty CSV outputs and two verified empty
standing-request outputs. Its reachable heap has 243,922,028 objects and 7,684,057,280 shallow bytes.
All 7,267 actors are current F3/limit-3 actors in 90 open runners. Strong cache arrays retain
3,149,636,544 bytes and method-edge stores retain 1,731,576,456 bytes; these sets overlap. No request
store or standing-request join is present, consistent with restricted forward run 3. Forward
conjunction state remains. IR storage retains 1,091,063,632 bytes and remains outside scope. The
separate report query establishes retained F1 command demands. The selected witness-header and scalar
queries are now reviewed. Sink 1 has two unconfirmed F1 witnesses: each checks Arg0 at the root with
one ANY `$UNTRUSTED` mark in DEMAND and an Arg0/root/STAR premise with that same concrete mark.
They differ in the context's exact-type flag. Sink 3 has one unconfirmed F1 witness with an EXACT
`$ARGS` mark in NORMAL and an Arg0/root/EXACT premise. All three have one literal, no end facts,
no accessor path and an empty exclusion. The exact entry-to-witness-to-literal pointer joins are verified.
These are coarse F1 supports, not proof of a normal source-to-caller chain or of the final run's first
mark-loss point. The saved scalar review is under `order-4-O3-Stirling-PDF/job-20261013T053003.061048Z`.

The O1 Conductor overview accounts for all 34 requests, with 29 nonempty CSV outputs and five valid
empty outputs. The complete histogram independently has no live request store, standing join or
predicate support store. All 17,525 actors and 219 runners belong to active B2/limit-2 work. Strong
cache arrays retain 1,080,360,544 bytes, while NNF descriptor/source/sink roots retain 147,350,352 bytes;
these sets overlap. The live census has 156,231 predicate descriptors. IR storage retains
2,418,601,528 bytes and remains outside scope. This is a different progress point from the O3 and final
snapshots, so the cache sizes do not isolate an NNF saving.

The checkpoint's optional compiled counters are wired: they record 157,704 predicates, 190,545 nodes
and 173,907 leaves cumulatively. Its observed counters are not passed into `SharedObjects`; their
printed zeros are unavailable runtime totals. Old cache access/build/pin counters are also uninstrumented.
The final implementation has the counter wiring verified by the complete core test gate.

All 34 O1 ThingsBoard overview queries succeed. Its reachable heap has 358,368,691 objects and
11,486,260,320 shallow bytes. All 73,549 actors are current F1/limit-1 actors in 562 open runners.
Strong cache arrays retain 4,393,215,672 bytes; NNF descriptor/source/sink roots retain 2,301,710,424
bytes. These sets overlap. The live census has 2,471,818 predicates. In contrast, observed predicate
stores retain 5,317,256 bytes, with 14 state scopes, 24 interned proofs and four canonical/emitted
roots. This capture shows substantial compiled potential storage and a small observed support store.

The request tables have 4,247,048 seen pairs; queues have 2,328,141 entries. The manager has handled
25,532,581 events. These are different progress points from O3 and final, so the cache and request
sizes are not a paired saving. Cumulative compilation records 2,610,266 predicates, 2,950,142 nodes
and 2,789,339 leaves. Runtime counter zeros remain unavailable because of the older wiring gap.
The inherited query titled "manual cube source and sink roots" also selects NNF-backed `SinkRule`
objects at O1. Its heterogeneous retained set is not evidence that production NNF lowering builds cubes.

A separate six-query aggregate follow-up partitions the complete 2,471,818 live predicates into
2,329,585 one-node/one-leaf predicates, 16 one-node constants and 142,217 multi-node predicates.
The singleton descriptor-root union retains 1,758,031,280 bytes. Its 2,329,585 distinct `byBase`
maps retain an overlapping 969,107,360-byte union. Primitive arrays within the descriptor union total
987,744,040 shallow bytes; those bytes are already included. The source retains nine topology arrays
per singleton, four arrays in `BaseLeaves` and further map backing arrays. Construction scratch is
discarded. This identifies a specialized singleton representation as a storage candidate; it does not
measure a replacement saving or assign the final OOM to that cache. All six requests and the complete
partition are verified in `order-7-O1-thingsboard/job-20261013T050224.288015Z/review`.

The O1 PDF overview accounts for all 34 requests, with 32 nonempty and two verified empty outputs.
Its reachable heap has 234,270,507 objects and 7,646,386,000 shallow bytes. All 7,270 actors are current
F3/limit-3 actors in 90 open runners. Strong cache arrays retain 1,391,567,056 bytes and method-edge
stores retain 3,091,706,680 bytes. These sets overlap. Live predicate support stores retain 3,170,256
bytes, with 673 state scopes, 3,710 interned proofs and 1,035 canonical/emitted roots. The live census
has 664,496 compiled predicates. No standing request store or join is present in this restricted phase.
IR storage retains 1,084,457,856 bytes and remains outside scope. Edge state is a larger owner than
compiled cache arrays at this snapshot. Different progress prevents an isolated comparison to O3.

All seven OOM checkpoint overviews are now parsed and checked against their source layouts. The
consolidated external manifest is `heap-analysis/checkpoints-preparation/all-seven-overview-review.json`.
Earlier observed-counter zeros, empty selections and heterogeneous class filters retain the limits
above. These results identify remaining owners; they do not turn any failed scan into a completed one.

Continuous JFR files exist for all eight runs. Seven normal profiler captures have saved collapsed
stacks and successful capture logs. O3 OWASP cannot attach, returns 1 and has no collapsed output;
its normal CPU profile is unavailable. No run reaches its configured near-limit window. Counter
fields absent from the earlier cache implementation are unavailable, rather than zero.

Five valid normal windows overlap forced GC and heap capture in the OOM guard:

| Checkpoint / project | Containing iteration | Snapshot overlap in requested 30-second window |
| --- | --- | ---: |
| O3 / Conductor | B2 | 12.156 s |
| O3 / ThingsBoard | F1 | 0 |
| O3 / Stirling-PDF | F3 | 8.063 s |
| O1 / OWASP | F1 | 0 |
| O1 / Conductor | B2 | 15.209 s |
| O1 / ThingsBoard | F1 | 24.342 s |
| O1 / Stirling-PDF | F3 | 7.191 s |

The windows use the profiler's monotonic metadata, with a two-second uncertainty allowance; guard
markers have millisecond resolution. Collapsed stacks have no per-sample timestamps, so the capture
samples cannot be removed afterward. Do not assign the mixed windows' GC/dump weights to analyzer
algorithms. All failed iterations' final 30 seconds also overlap 17–29 seconds of the guard.

The guard-free O1 OWASP window samples cleaner/filter transfer, with no sampled `JIRCallPlanBuilder.build`
frame. `cleanChain` appears in 23.80% of samples and `FormsCache.get` in 0.10%. The final primary
OWASP window has call-plan builder frames in 37.91% of samples under the same saved-stack reader.
These inclusive shares overlap. They identify construction in the final window, but cannot separate
first builds from O2 reclamation/rebuild or establish an isolated cache cost. Both windows also have
substantial ordinary GC.

O3 ThingsBoard's guard-free window samples the request feedback path: local-service request joins,
manager routing, request admission and standing-link joins. Its final heap review independently
identifies large standing request and queue state. All eight continuous-JFR exports and their compact
review are complete, with 151 overlapping engine, barrier, iteration, normal, last-30-second and
non-guard/pre-guard windows, annotated for guard overlap.
The manifests match the project, checkpoint, recording and original analysis status; input/native checks
pass and the reader has no warnings. Export success does not change the seven OOM statuses.

The following windows end before the first heap-capture guard in the named engine. O1 OWASP has no
guard; its row uses the final 30 seconds of F1. They include ordinary GC. Allocation is sampled weight
in decimal GB, and pause time is clipped top-level `GCPhasePause` duration.

| Checkpoint / project | Engine | Window | Allocation weight | GC pauses |
| --- | --- | ---: | ---: | ---: |
| O3 / OWASP | F1 | 30.000 s | 40.681 GB | 5.718 s |
| O1 / OWASP | F1 | 30.000 s | 55.886 GB | 0.518 s |
| O3 / Conductor | B2 | 30.000 s | 99.358 GB | 6.516 s |
| O1 / Conductor | B2 | 30.000 s | 122.858 GB | 6.753 s |
| O3 / ThingsBoard | F1 | 30.000 s | 43.240 GB | 15.495 s |
| O1 / ThingsBoard | F1 | 30.000 s | 61.659 GB | 13.391 s |
| O3 / Stirling-PDF | F3 | 25.219 s | 88.847 GB | 5.879 s |
| O1 / Stirling-PDF | F3 | 30.000 s | 116.681 GB | 6.439 s |

O3 OWASP's pre-guard allocation paths include sink construction at 27.35%, cube construction at
16.74% and call-plan construction at 23.19%, with overlapping inclusive shares. Its separate nominal
normal JFR interval has only two serialization samples; the failed normal profiler capture remains
unavailable. O1 OWASP's valid, guard-free normal F1 interval has 59.907 GB sampled allocation weight
and 0.767 seconds of clipped pauses. Compiled transfer, cleaner-chain and filter paths account for
18.61%, 13.69% and 13.54% of its inclusive allocation weight.

Conductor's pre-guard B2 subtraction shares are 11.75% for O3 and 10.42% for O1; the `mergeAddDelta`
lambda shares are 13.06% and 12.69%. ThingsBoard's generic queue comparison/boxed-Long shares are 36.91% and 28.03%
in the pre-guard windows. Its O3 normal interval is guard-free, with a 47.97% queue/boxing allocation
share. Ordinary GC still consumes substantial time before capture. PDF's pre-guard F3 subtraction
shares are 9.76% and 12.17%; the different window lengths and event progress prevent an isolated O1
performance claim. These observations identify transfer, trie and queue work; they do not authorize
deferred optimization or IR changes.

JFR execution samples contain Java stacks and omit native GC CPU. Allocation weights are not retained
bytes. Inclusive shares and windows overlap; neither can be added. Start-attributed GC pause sums remain
separate from clipped pause durations. Uncovered intervals and missing event types remain unavailable,
and event-time envelopes do not prove continuous recording coverage. Full tables and source links are
in `attribution-review/profile-export/exported-review-1/REVIEW.md`.

Raw logs, terminal summaries, strict comparisons and capture provenance are under
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/attribution-runs` and `attribution-review`.
The fresh post-checkpoint input endpoint check is
`input-audit/post-run-check/result-20261013T042255.731787Z/equality-manifest.json`: all 77 checks pass
across the 41 captured content datasets. The original six uncaptured dangling toolchain targets remain
unavailable for historical comparison. Diagnostic harness bytes and analyzer revisions are separate
provenance. This check is endpoint equality, not continuous mutation monitoring.
The external `attribution-review/index.json`, eight order JSON files and source differential preserve
the exact counts, nanosecond timings and their limits. Heap plans and later query results are under
`heap-analysis/checkpoints-preparation`. The saved-stack reader, guard intersections and JFR export
plan are in `attribution-review/profile-export`.
