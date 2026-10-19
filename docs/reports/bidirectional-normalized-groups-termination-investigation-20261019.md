# Normalized mark groups: finite work or a new loop

Date: 2026-10-19. Runtime:
`b88c85d5b710d28293d7672af92b085a3838fb19`.
Language: ASD-STE100 Simplified Technical English. Program names and defined
technical terms keep their meanings. This investigation makes no runtime change.

**There is measured redundant request work. There are also source-level paths
that process equal facts before deduplication.** Fewer mark groups do not imply
fewer operations. A profile identifies expensive stacks, but it does not count
their calls or distinguish useful calls from repeated calls.

The observed Stirling-PDF run-1 is finite. It completed in 925.594 seconds.
Its full recording shows expensive repeated catalog scans and mark conversion.
The final 30 seconds still show the catalog-scan cost. This is stronger evidence
than a timeout or a flat scheduler counter.

This result does not prove that every runtime execution terminates. OWASP and
Conductor timed out in run-1. ThingsBoard reached the memory guard in run-1.
Their prefixes cannot prove termination. The source audits found no new
unchanged-state scheduling or static-reply cycle in their stated scope.

Fewer mark variants do not establish a smaller total workload. The selected
GROUP semantics can enable extra DEMAND transfers. For example, if distinct
full marks T and U have group g, an actual T can satisfy the run-1 GROUP view
of `ContainsMark(x,U)`. An ordinary action guarded by that check can then
reach an AP position that the exact U check would not reach. The selected
static order/context approximation can also enable extra combinations.
These are explicit parts of the current spec, not new behavior proposed by
this investigation. Their actual contribution to these projects is not
measured. A small group count alone bounds neither reachable AP positions
nor premise, call-stage and dependency combinations.

## Actual iteration results

These are the fresh fixed-configuration runs. Main reference:
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. Source/input equality passed after
all eight invocations. There were no alternate analyzer configurations or retries.

| Project | Run-1 engine seconds | Terminal result | Latest reached / completed engine / accepted forward report |
| --- | ---: | --- | --- |
| OWASP | 906.998 | run-1 TIMEOUT | run-1 / none / none |
| Conductor | 986.046 | run-1 TIMEOUT | run-1 / none / none |
| ThingsBoard | 239.084 | run-1 MemoryManager OOM | run-1 / none / none |
| Stirling-PDF | 925.594 | run-1 COMPLETE; run-2 TIMEOUT | run-2 / run-1 / run-1 |

PDF's run-1 ended at `14:32:08.228`. Its positive forward frontier followed
at `14:32:11.336`. Its barrier took 3.203 seconds. Run-2 used full marks,
BACKWARD direction and field limit 1. It timed out after 99.683 engine seconds.
The accepted report remains the run-1 DEMAND frontier.

## Direct evidence of repeated request offers

The OWASP run-1 END counters give this exact partition:

| Offered request memberships | Count |
| --- | ---: |
| Already in authoritative request storage R | 23,873 |
| Already in temporary request storage P | 30,573,022 |
| Newly admitted to P | 46,996 |
| Total offered | 30,643,891 |

Thus 99.847% of offered memberships were duplicates at admission. There were
30,630,535 offer calls. The existing stores merge requests, but the producers
can still construct and offer an already known request many times. Merging at
storage admission does not remove this upstream work.

These units are Base-request mark memberships. They are not fact counts,
statement counts or AP-operation counts. The counters prove repeated offers;
they do not show how much CPU each offer costs. The 46,996 cumulative P
admissions are also not the final authoritative request set. Later commits
admitted 14,582 fresh authoritative memberships.

## Equal results can be processed before they reach a store

The ordinary call path retains stage results in lists:

```text
for each input in currentStageInputs:
    filter input
    apply each matching micro edge
    append each result to nextStageInputs

process the next stage with the complete list
...
admit the final result to a successor store
```

`RunMethodCallServices.applyStage` does not union equal results between stages.
One production-representable case has a keep edge and a selected self-copy:

```text
input:                  b / EXACT / Group(g), DEMAND
identity edge:          b/* -> b/*
selected transfer:      b/g -> b/g
GROUP result of each:   b / EXACT / Group(g), DEMAND
```

The edges differ, so the form builder can retain both. If both produce the
same complete stage state, the next stage processes that state twice.
Deduplication of their final successor result occurs after this work. An
additional parallel unresolved identity branch can also produce that result.

This case alone does not explain a new regression: the FULL form can also
retain a keep edge and the matching selected self-copy. Moreover,
`JIRRuleForms.mark` projects marks in the RUN1_GROUP view before the builder's
edge-set admission. Copies for full T and U in the same group can collapse
there. Do not claim that each configured full-mark copy survives as a new
GROUP operation in the selected JVM pipeline.

The generic engine API also permits distinct raw full-mark transfers that
become equal during execution. Compatible synthetic stage chains can multiply
their transient entries. This is an API-level witness, not a measured project
factor or proof that the production JVM builder emits such a chain. The
diagnostic must keep the production-projected control separate from that
generic-API case.

There are other distinct paths to repeated work:

- A summary can arrive between subscriber registration and the replay
  snapshot. Replay can apply an input-summary pair, then the queued delivery
  can apply the same pair again. The singleton join has no applied-pair gate.
- N2 can reject a duplicate call input while the caller still sends LinkIn.
  The callee performs initial construction and request matching before its
  initial-fact gate rejects an unchanged start. This does not imply another
  method-body start.
- An addressed static resume enters `resumeQueue` directly. It runs the
  statement or call phase before successor admission. Static-service reply
  freshness therefore does not by itself prove freshness of the normalized
  operation input.

These are finite repeated-work mechanisms. None is, by itself, evidence of an
unchanged-state infinite loop. A safe optimization must retain complete
premises, stage state, layers, source/action observations, requests without a
value result and static effects. Equal output values alone do not authorize
deletion of those observations.

## Static delivery freshness differs from operation-input freshness

The static source audit gives two exact many-to-one conversions. These are
source-state traces, not measured frequencies in ThingsBoard:

```text
same read ID, same C.f path:
    ReadReady(EXACT)     -> PointWork(C.f/*, C.f/*, D, site, state)
    ReadReady(ANY_TAINT) -> PointWork(C.f/*, C.f/*, D, site, state)

same site, original premise, image geometry and call state:
    PointKey(mx={g}) -> supply g -> clear mx -> marked PointWork K
    PointKey(mx={})  -> supply g -> clear mx -> marked PointWork K
```

The service correctly regards the read overlaps as distinct. It also regards
the two point IDs as distinct subscribers. The owner then maps them to equal
operation inputs. Both enter the resume queue, and both execute before output
admission. Thus a fresh service reply need not represent new operation work.
Many possible mx subsets remain even with a small group catalog. Their actual
population is not measured by the group count.

Writer registration introduces a different multiplier. Its key is
`(complete premise, output Pattern, layer)` within a method owner. If one
premise has M static output leaves and r compatible pool inputs, the service
can schedule M*r writer replies. Each output-specific callback repeats
complete-premise matching. The M outputs may all be necessary, but matching
their shared input relation M times is a factoring candidate. For a
multi-premise writer the tuple count can be a product of candidate counts;
`candidateVisits` does not count those tuples.

ThingsBoard ended in run-1 with 72 supplied region/group memberships,
817,103 point subscriptions, 1,493,242 conditional writer rows, 29,038,614
candidate visits and 2,544,429 owner replies. These are different units.
Their ratios do not establish a duplicate fraction or removable-work bound.
They do establish that a small final group pool can coexist with a large
dependency and delivery workload. The fresh heap below measures its retained cost.

## The fresh ThingsBoard heap is mainly auxiliary state

The run-1 memory-guard capture has 372,928,030 reachable objects and
11,434,863,248 shallow bytes in the parsed heap. The following values are
collective retained unions. They overlap and must not be added.

| Selected owners | Retained bytes |
| --- | ---: |
| Four bound-rule cache-map families | 3,160,452,352 |
| Run-1 static service and point owners | 2,658,761,136 |
| IR storage, context only and outside optimization scope | 1,619,028,848 |
| Raw/selected summary stores | 283,378,376 |
| Method-edge stores | 149,233,832 |
| Worklist/event-queue/channel owners | 44,327,544 |

The bound-rule maps retain 35,070,715 ContainsMark objects and 8,699,328
bound method-exit sinks. These are rule/configuration objects, not propagated
program facts. Generated broad method-exit templates and per-method binding
create a method/template/query-view workload. The ownership census queries
both relevance flags and all rule roles. GROUP form lowering does not itself
remove the underlying full bound-rule cache.

The final C01/C02 headers partition the four cache fields. There is one
configuration with eight role storages. Only normal and allRelevant maps
are populated; both no-end maps are empty in every role. There are 940,406
method/role/view key memberships, including cached empty lists. This is not
four populated copies, GROUP/FULL duplication or a bound-rule count.
The exit-sink role has 59,277 method keys in each populated view. Six roles
have 58,700 in each; pass-through has 58,752 normal and 58,700 allRelevant.

The strong-root query shows `main Thread <Java Local> -> JIRTaintAnalyzer.
taintConfiguration -> SpringRuleProvider`. The provider retains 3,285,534,424 B
through its delegated chain. This confirms strong ownership at the snapshot,
not a leak after completion or a measured main/candidate heap difference.
A census that observes marks without publishing unused bound payload is the
first work-avoidance candidate. Removing only ignored end actions cannot
remove the much larger condition population.

The complete actor tables contain 64,124 run-1 actors, 209,932 current raw
premise memberships, 1,884,882 local output-writer keys and 1,493,242 service
writer rows. A writer premise is one of its actor's raw premises. Therefore
the service has at least `1,493,242 - 209,932 = 1,283,310` output-specific
registrations beyond one per distinct writer premise. This is a lower bound
on repeated subscription structure. It is not a count of unnecessary output
effects or a byte-saving estimate. It directly supports sharing premise
matching while retaining all output transfers and both arrival orders.

Only 177 canonical InitialAp descriptors are physically present. These are
global shared descriptors, not all per-method facts. No PremiseSet object is
present. Thus the currently retained service writers have singleton
premises; a live multi-premise Cartesian writer product is not this heap's
observed pattern. Earlier transient ND work is not excluded.

At the snapshot, the actors have 3,166,332 processed worklist deltas, 57,218
queued worklist entries, 393,981 unpublished rows and 633 queued addressed
resumes. Direct resume executions remain absent from the step counter.
Large retained tables are not the same as large pending queues. This is one
snapshot, so it does not prove a leak, unbounded growth or termination.

The full actor query's MAT process exited zero. Its strict wrapper recorded
a changed operational log index; all 12 data-index stats stayed unchanged.
That wrapper result and the limited log reconciliation are retained. No
query result or process closure is inferred from a partial reader.

## Executed counting control

Four external diagnostic tests passed, with 35 finite cases. They use the
actual FormApplier, AP operations and RunMethodCallServices. The production
source still matches B88. The tests count attempts and outputs; they do not
run another project configuration.

| Case | Raw final outputs | Distinct final Facts | Stored successor values | Edge / filter calls in the counted FormApplier chain |
| --- | ---: | ---: | ---: | ---: |
| Generic API, five raw-full fork stages, FULL | 6 | 2 | 1 | 30 / 35 |
| Generic API, same stages, GROUP | 32 | 1 | 1 | 62 / 93 |
| JVM-shaped projected pass stage plus parallel identity, FULL | 3 | 1 | 1 | Separate control |
| JVM-shaped projected pass stage plus parallel identity, GROUP | 3 | 1 | 1 | Separate control |

The first two rows prove that the generic engine can do more work while
retaining fewer distinct results. The full five-stage chain is not a claim
about the production JVM builder. It has at most five forward links in this
test. The last two rows reproduce the production-projected form shape using
actual call services, without invoking the JVM lowerer. Both modes process
three equal final results before one successor admission. GROUP has fewer
physical pass edges, but the same three output callbacks in this control.
Thus this control proves repeated work, but does not attribute the observed
regression to a new GROUP-stage multiplier.

A separate control emits one equal GROUP fact through two distinct source
action IDs. Both actual source-hit identities remain present. A future work
reduction must preserve them.

The first diagnostic launch discovered no tests because the external init
script selected the wrong included-build path. That harness failure is
retained. The corrected launch ran all four tests with zero failures, errors
or skips. No production code or diagnostic semantics changed between launches.

## Why the current operation counts are incomplete

`RunMethodAnalyzer.steps` counts stored/unchanged `EdgeDelta` processing.
Addressed resumes call the statement or call handlers directly and do not
increment that counter. One step can also process a packed trie, many micro
edges or many call-stage results. `edgeDeltas` counts admitted deltas, after
much of this work. Neither counter is a total AP-operation count.

The actual missing measurement is therefore:

```text
raw operation inputs / edge and filter attempts
    versus distinct complete operation inputs
    versus admitted semantic output deltas
```

This investigation keeps those units separate. It does not infer a total
operation count from CPU samples, allocation weights or completed work
reservations.

## Why the progress log can look stuck

The log prints `InFlight.handled`. It counts completed work reservations.
It does not count new facts, AP deltas or individual method steps.

```text
one Work reservation
    -> work(quantum)
    -> incomplete: requeue the same reservation
    -> work(quantum)
    -> complete: increment handled
```

Thus many processing turns can occur while the handled count stays constant.
PDF had long intervals with five in-flight reservations and no completed
reservation. That did not prove an infinite loop: the same run later completed.

There is also a finite-cost risk inside one static-service turn:

```kotlin
combinations = combinations.flatMap { prefix ->
    matching.map { prefix + it }
}
```

`Run1StaticService.candidateBindings` constructs this Cartesian product under
the service lock. The inner expansion has no work-quantum boundary. Twenty
members with two candidates each produce 1,048,576 tuples. The input is finite,
but the operation can be large. This is a source-based risk. It is not the
measured explanation for PDF's long tail.

## The measured PDF run-1 cost

The Java 17 JFR export uses depth 256. All seven selected event counts match
the recording summary. Finalized chunk coverage includes the whole run-1 and
its final 30-second window. Logger alignment still has uncertainty.

| Observation | Whole run-1 | Final 30 seconds of run-1 |
| --- | ---: | ---: |
| Java execution samples | 59,466 | 2,095 |
| Samples with `eligibleMembers` in the stack | 59.330% | 52.124% |
| Leaf samples in `FullMarkId.equals` | 38.625% | 34.129% |
| Leaf samples in `eligibleMembers` | 19.456% | 17.184% |
| Mark projection, policy and exclusion union | 77.103% | 79.045% |
| Samples with `runtimeFact` in the stack | 15.629% | 24.726% |
| Recorded allocation weight | 2,292,674,172,272 B | 76,326,177,784 B |
| Allocation weight with `runtimeFact` in the stack | 47.863% | 59.763% |
| Recorded monitor-enter events | 133 | 0 |
| GC pause union | 5.829 s | 0.109 s |

Inclusive stack shares overlap. Do not add them. Java execution samples are
not wall-time fractions. Allocation weights estimate allocation over time;
they are not retained heap or exact allocation totals. Zero recorded monitor
events does not exclude short waits below the recording threshold.

### Group policy still scans full marks

The selected run began with 1,172 eligible full marks and 51 groups. The current
implementation obtains a group's members as follows:

```kotlin
@Synchronized
override fun eligibleMembers(group: GroupId): List<TaintMark> =
    eligible.filter { owner(it) == group }
        .map { checkNotNull(fullAtoms[it]) }
```

Every lookup scans the current full eligible catalog. It also constructs
temporary lists. Group type filtering calls this lookup repeatedly:

```kotlin
val members = marks.eligibleMembers(marks.groupOf(atom))
members.isEmpty() || members.any { policy.keeps(it, level) }
```

For the PDF startup census, one such lookup checks 1,172 eligible full
members, even when it asks for one of only 51 groups. Each check calls
`owner`; the map lookup uses a FullMarkId key. The lookup therefore performs
many scalar catalog operations per group-policy visit. This is another
source of excessive operation count inside an AP operation. A catalog-owned
group directory could return the same current members without scanning
unrelated groups. The actual number of lookups is not recorded.

Direct static-service frames account for 0.267% of the run-1 Java samples.
Ordinary predicate-store frames account for 0.027%. The direct static tuple
product is not the measured dominant path in this PDF recording.
This does not exclude ordinary AP work created by static propagation.
The run-1 CPU stacks have no missing frames or recorded truncation; their
maximum observed depth is 110, below the print depth of 256. These export
flags do not exclude truncation that occurred when the recording was captured.

The group abstraction reduces mark variants. This implementation adds a
full-catalog scan to repeated group-policy visits. It can spend more time
per visit even when fewer marks propagate. The full-mark equality and catalog
frames explain substantial CPU cost through the final run-1 window.

### Already selected values are converted again

The ordinary result path has repeated conversion boundaries:

```text
AP operation result
    -> ApOut.run1Policy.result
    -> ApManager.runtimeFact
    -> actor result collection / forceRun1Layer
    -> ApManager.runtimeFact again

FLOW construction
    -> ApManager.intern(markExclusions)
    -> markExclusions.withCatalog(theSameCatalog)
    -> normalized
```

`withCatalog` currently calls `normalized` even when the immutable exclusions
already use that catalog. Normalization calls synchronized catalog methods for
the stored atoms. `runtimeFact` can also walk and project an already selected
GROUP trie. These operations can add allocation and lock traffic without
adding a new semantic fact.

Removing a whole conversion needs a closure proof for that path. Raw factories,
shared subtrees and generic adapters do not all enforce the GROUP domain.
Keep the existing layer forcing, field cover, FULL validation and mixed-catalog
checks. This investigation does not authorize their removal.

## Early parallel processing also pays for catalog contention

The native CPU captures expose cost that Java-only sampling does not fully
describe. PDF's normal capture was scheduled in early run-1. Its near-limit
nominal window is in run-2, so use JFR for the run-1 tail. Native collapsed
stacks have no per-sample timestamps. The rounded invocation start and
profiler attach delay prevent a bounded actual-window claim.

| Candidate nominal native window | Samples | Selected monitor spin/entry leaves | Inclusive exclusion normalization |
| --- | ---: | ---: | ---: |
| PDF, early run-1 | 20,316 | 50.901% | 47.012% |
| Conductor, early run-1 | 21,407 | 41.211% | 59.546% |
| Conductor, near-timeout run-1 | 22,138 | 40.722% | 65.494% |
| OWASP, near-timeout run-1 | 3,031 | 0.033% | 78.951% |
| ThingsBoard, early run-1 | 29,897 | 32.729% | 51.279% |

The selected native leaves are `ObjectMonitor::TrySpin`, `SpinPause` and
`ObjectMonitor::enter`. In almost all their PDF, Conductor and ThingsBoard
samples, the stack has `MarkTable.key`, `isGroup` or `group` callers. This ties
the sampled spinning to catalog calls. Native stacks do not identify a monitor
object's address. PDF's whole run-1 JFR separately attributes 2.823 of 2.911
recorded blocked-thread overlap seconds to `MarkTable`.

Early parallel contention and the late full-catalog scan are different costs.
The final PDF window has no recorded monitor-enter events, but it still spends
more than half its Java samples under `eligibleMembers`. The late scan cannot
be explained only by early contention. The measurements include profiling
overhead; JFR stack recording also appears in native leaf costs.

## Scope of the loop audit

The source checks found these progress gates:

- Static read, point, writer and writer-interest replies require a fresh
  delivered overlap or group membership. Re-registering the same interest
  does not by itself produce an endless reply cycle.
- Raw static writer registration follows an ordinary summary delta. A reply
  borrows the current summary; an obsolete selector can produce no result.
- Requests merge in P/R. A duplicate does not create another stored request.
  An empty work wake-up alone does not sustain a new reservation cycle.
- Premise keys use strong canonical maps. Clearing a soft trie cache does not
  replace a still-live canonical premise with another identity.
- Ordinary AP merge uses structural values and returns no delta for an
  unchanged union. The removed GroundingSupport formulas are not present.
- The production JVM call-plan builder emits strictly advancing forward
  CallPoint arcs. Reversal makes them strictly descending. Its stage walker
  therefore has no generated stage-graph cycle. The generic API alone does
  not enforce this property.
- Production form rebuilding reuses its FormOwner. JVM instruction equality
  uses instruction class and method/index location, rather than allocation
  identity. Merely rebuilding an equal instruction does not make a fresh key.

These are scoped source checks, not a kernel-checked whole-JVM theorem. The
finite-key argument also needs valid JVM AP layouts, stable finite provider/IR
vocabularies, complete transfer adapters and the selected ordinary AP laws.
Initial grammar alone does not prove every transient runtime key is bounded.

The evidence supports investigation of work avoidance before operation
execution, in addition to reuse of group-member snapshots and proven identity
normalizations. The present request stores already merge memberships. Adding
another final-output set would not remove the work described above. A useful
proposal must place its gate before that work and prove that it keeps all
required observations. Member snapshots must refresh on relevant late
registrations and eligibility growth; do not freeze the initial census.
Actual implementation remains unchanged. The [performance report](bidirectional-run1-normalized-groups-performance-20261019.md)
and [next-iteration proposal](../design/bidirectional-run1-normalized-groups-next-iteration-20261019.md)
include the closed profiles and fresh memory evidence.

## Evidence

Study root:
`/drive-testcomp/opentaint-go-rules/grouped-statics-alias-writes-20261019`.

- `all8-closed-source-input-equality.json`, SHA-256
  `7722abe141fc44f1c1ae8eeae139a2a1125929d523d8a25d4f4342f41ebde671`.
- `validation/profile-analysis-real/Stirling-PDF-bidi/analysis.json`, SHA-256
  `66c3fbb3c11d867a872bec3a805c99225596cf22e60b30971e6725a7e4e75465`.
- `validation/report-working/pdf-priority-profile-brief.json`, SHA-256
  `1ceae9cfc47a9b3286efd7b9584b3d1f27c79641c2f10a79bbc64aff28137f54`.
- `validation/native-first-look/analysis.json` and `monitor-callers.json`.
- Source-bound audits: `validation/report-working/termination-scheduler-audit.md`,
  `termination-static-audit.md` and `termination-ap-and-normalization-audit.json`.
- `validation/report-working/redundant-operations-ordinary-audit.md`, with
  source hashes and the exact OWASP request-counter partition.
- `validation/report-working/redundant-operations-static-audit.md` and its
  B88 source binding, for the normalized-input and writer multiplicity traces.
- `validation/redundant-operation-repro/production-call-plan-source-audit.md`,
  for the actual stage graph and the pre-builder GROUP projection control.
- `validation/redundant-operation-repro/result.json` and
  `attempt-2/receipt.json`, with source/test pins, complete XML and raw counts.
- `validation/heap-analysis-real/thingsboard-owner-review.json`, the original
  closed 34-query job and its complete outcome inventory.
- `validation/heap-analysis-real/actors-20261019T154124.181819Z/actor-count-review.json`,
  the three complete actor tables and operational-log reconciliation.
- `validation/heap-analysis-real/root-cache-followup-preparation/terminal.json`
  and `query-outcomes.json`, the final two complete cache CSVs and expanded
  root paths. The data indices stayed unchanged; operational-log append-only
  validation and actual process closure passed.

The first derived PDF event JSON was validated, then removed under the recorded
retention policy. The original JFR, native captures, exact counts/hashes,
analysis and receipts remain available. No analyzer rerun was used.
