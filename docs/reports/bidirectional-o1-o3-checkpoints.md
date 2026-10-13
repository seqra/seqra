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
the command row's F1 state.

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
fresh child/strong-path queries must still identify the largest methods and condition expansions.
It is not a before/after heap saving because the O1 and final runs reach different progress points.

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

The guard-free O1 OWASP window samples cleaner/filter transfer, with no `JIRCallPlanBuilder.build`
frame. `cleanChain` appears in 23.80% of samples and `FormsCache.get` in 0.10%. The final primary
OWASP window has call-plan builder frames in 37.91% of samples under the same saved-stack reader.
These inclusive shares overlap. They identify construction in the final window, but cannot separate
first builds from O2 reclamation/rebuild or establish an isolated cache cost. Both windows also have
substantial ordinary GC.

O3 ThingsBoard's guard-free window samples the request feedback path: local-service request joins,
manager routing, request admission and standing-link joins. Its final heap review independently
identifies large standing request and queue state. Conductor and PDF need separate JFR windows before
the guard for a useful algorithm comparison. The prepared export plan includes engine, barrier,
whole-iteration, guard and pre-guard windows. CPU/allocation/GC exports remain pending.

Raw logs, terminal summaries, strict comparisons and capture provenance are under
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/attribution-runs` and `attribution-review`.
The external `attribution-review/index.json`, eight order JSON files and source differential preserve
the exact counts, nanosecond timings and their limits. Heap plans and later query results are under
`heap-analysis/checkpoints-preparation`. The saved-stack reader, guard intersections and JFR export
plan are in `attribution-review/profile-export`.
