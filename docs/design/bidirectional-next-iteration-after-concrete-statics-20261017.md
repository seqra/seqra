# Next iteration after concrete static propagation

Date: 17 October 2026. Status: historical ranking; order 1 is now implemented.
Its [runtime validation](../reports/bidirectional-static-need-replay-batching-validation-20261017.md)
records the checks. The [fresh project report](../reports/bidirectional-static-need-replay-batching-20261017.md)
measures runtime `f18b1c5bb`: latest reached / completed / accepted forward
are OWASP run-7 / run-6 / run-5, conductor run-1 / none / none, ThingsBoard
run-1 / none / none, and Stirling-PDF run-2 / run-1 / run-1.
The [new proposal](bidirectional-next-iteration-after-replay-batching-20261017.md)
supersedes this ranking. The remaining items below are historical unselected
proposals; their old byte estimates are not evidence for impact after batching.
Order 1 now has a [complete specification](../../spec/run1-static-need-replay-batching.md)
and [proof dossier](../../spec/run1-static-need-replay-batching-proofs.md).
Evidence: runtime `6b9291d7dd69ae5f6a1260c7f1021c7500fa56b8` and the
[historical project report](../reports/bidirectional-run1-bottom-up-concrete-static-20261017.md).
The previous [optimization ranking](bidirectional-next-iteration-after-bottom-up-statics-20261017.md)
uses another runtime and is stale. IR storage is outside scope.

| Project, historical runtime `6b9291d7dd` | Reached / completed / accepted forward in that study | Run-1 engine |
| --- | --- | --- |
| OWASP | run-8 / run-7 / run-7 | 262.966 s, complete |
| conductor | run-1 / none / none | 164.524 s, OOM; includes 16.824 s dump |
| ThingsBoard | run-1 / none / none | 202.996 s, OOM; includes 37.751 s dump |
| Stirling-PDF | run-2 / run-1 / run-1 | 631.556 s, complete; OOM later in run-2 |

All four fresh main invocations succeed. No candidate meets the 60-second
run-1 engine target. OWASP's available report covers all 4,112 main unique
sink identities. Conductor/ThingsBoard fail before an accepted report.
Stirling-PDF retains two strict location differences and 49 added identities;
its Simple report does not prove baseline path coverage. The project gate
is not passed.

## Decision from current measurements

The largest measured core memory owner in run-1 is temporary static routing
work. ThingsBoard has only 118 source rows but 688,637 live waves and
21,306,795 StaticNeed event objects. Conductor has 416 source rows,
1,342,151 waves and 534,193 physical request/source-reentry envelopes as
well as 2,105,315 static need events. The Base-only fraction of those
envelopes is not measured. Stored request unions and caller inputs are much smaller.
This favors algorithmic work avoidance before a smaller per-visit layout.

The order below is the recommended memory order for **run-1**. Footprints
are complete current retained owner scopes, not promised net savings. The
routing proposals share bytes; their targets must not be added.

| Order | Proposal | Current → new | Expected impact and evidence limit |
| --- | --- | --- | --- |
| 1 | Batch graph replay and merge equal-mark traversals | Full-source replay for each binding delta → one replay per owner turn, with an exact multi-start traversal for equal mark batches | Targets 2.115 GB of wave state in conductor and 3.273 GB in ThingsBoard, plus overlapping queued payloads. Directly removes work and its live owners; batch/mark-set sharing still needs measurement |
| 2 | Make reproducible bound-rule lists reclaimable | Strong bound lists → values managed by the existing soft-reference manager, with exact rebuild | Targets a separate 0.321 / 1.092 GB normal sink-cache union in conductor / ThingsBoard. Other strong owners and rebuild cost limit the gain |
| 3 | Give Work fair service and combine request/reentry deliveries | Many queued endpoint batches and repeated P offers → fair owner progress, one pending destination union, and direct R/P admission | Conductor has 534,193 physical request/source-reentry envelopes and about 100 MB of int[] arrays in the complete queue owner; Base-only bytes are unmeasured. This is smaller than the static wave target; sender/receiver work also falls. Measure the residual after order 1 |
| 4, conditional | Notify only matching FLOW-route owners | General effect subscribers → a need-only candidate directory for actual FLOW bindings | Targets part of the same routing payload. Empty-notification fraction is unmeasured, so no independent byte saving is assigned; first add the counters and reassess after order 1 |
| Mainly CPU/allocation | Compose eligible cleaner blocks and reuse analyzer type checks | Repeated field read/clean/write and lookup → proved composed operation or reused check | OWASP run-1 cleaner-chain share is 62.64%; type checks 25.02%, overlapping. Eligible fraction and retained saving are unmeasured, but this is necessary investigation for the 60-second target on OWASP |

Current ThingsBoard ordinary caller inputs retain 181.624 MB, and their
position index 61.575 MB. Dense statement arrays are only 2.965 MB. The old
runtime's large-index ranking must not be carried forward as current evidence.
These are interrupted-run owners; completing more work after a routing fix
can change the ranking. Later-run memory is treated separately below.

R is the committed nonstatic request union in SummaryStorage. P is the
temporary owner-local union, committed at method-empty. A static source
owns the mark union for one full method/original-field key. A routing wave
has temporary traversal state and shares an immutable mark batch.

The first design question is how to avoid repeated work. Changing the
representation of each static visit would leave the replay multiplier in
place. Keep the selected source-once interest model and the packed concrete
carrier. Change when and where the engine repeats a traversal. Do not add
per-mark request rows at every intermediate method.

## Batch route growth before replaying static needs

The [complete specification](../../spec/run1-static-need-replay-batching.md)
defines the owner-turn barrier, exact grouping, live-graph proof, reservation
lifecycle and acceptance checks. The sketch below explains the proposal;
the complete specification controls its detailed behavior. This item is now implemented.

**Current:** every fresh packed static relation, or ordinary static FLOW
admission with a valid abstract original, calls `graphChanged()`. That call
starts a full wave for every committed source. A wave has its own visited
set and delivery events. A later wave repeats the same route. The existing
method-empty barrier batches local source marks, but does not batch these
graph-change replays.

**New:** register actual bindings as today. Accumulate route changes in the
current runner turn. Flush once after its event or Work quantum, before
releasing or requeuing that turn's reservation. Keep local source mark
commits at the existing method-empty barrier. The flush uses all committed
sources if the graph grew; otherwise it uses only fresh source deltas.

```kotlin
// State belongs to the runner. It is transient, not an inherited request store.
fun onBindingRegistered() { pending.graphGrew = true }
fun onSourceCommitted(key: StaticNeedKey, fresh: RequestMarkSet) {
    pending.sourceDeltas.union(key, fresh)
}

fun finishRunnerTurn() {
    val batch = pending.takeAndReset()
    val starts = if (batch.graphGrew) router.committedSourcesSnapshot()
                 else batch.sourceDeltas
    router.replay(starts) // secure all root-wave child messages before returning
}

// Runner sequence, for complete and incomplete Work quanta alike:
handleEventOrWorkQuantum()
finishRunnerTurn()
completeParentReservationOrRequeueIt()
```

Every binding is registered before `onBindingRegistered`. Every committed
source union is visible before `onSourceCommitted`. Other runners have their
own pending turns; one runner cannot reset another runner's dirty state.
Actual bindings stay in their existing caller owners. A wave reads those
owners through the existing counted message path. If a binding is added
after a wave has visited its node, that binding's own turn starts a fresh
replay. A late source delta traverses existing routes. This is the minimal
first design; it adds no separate replay task or global idle barrier.

A method-queue-empty barrier is useful for source marks. One runner Work
quantum can also register many call bindings before its replay flush. A
unit-queue-empty barrier can combine more changes, but must have a separately
counted wake and a fair fallback for a continuously busy unit. Defer that
extension until the simpler turn barrier is measured. There is no arbitrary
pending-memory cap. Measure simultaneously live waves; if they remain the
main owner, design joined routing epochs or selective graph-delta replay
with their own proofs.

**Why it should help:** one traversal can cover many new bindings. It avoids
waves, visited hash entries, owner messages, lock acquisitions and repeated
Base offers before their receiver can commit. This is an algorithmic work
reduction. It can improve memory and engine time together. The effect depends
on the number of binding changes combined in each batch.

Required proofs before implementation:

1. Every source delta and registered binding belongs to an admitted owner
   turn. That turn flushes before releasing its reservation, including an
   incomplete Work quantum. A concurrent turn cannot clear its pending state.
2. At quiescence the Base endpoint union equals the existing finite reverse
   reachability closure. Prove both source-before-binding and
   binding-before-source, cycles and late edges behind an already visited
   node. Do not use a permanent visited set to suppress later generations.
3. A delivery is interest only. It cannot introduce actual T, a sink hit or
   an ND witness. Keep full call/stage geometry and original routing.
4. Preserve ordinary Base propagation and reentry into the static route,
   including the `A(p) { C.f = p; B(); }` bootstrap.
5. Completion counts dirty replay state and child messages. Cancellation
   releases these owners after the current run joins.
6. Prove a work reduction over a finite batch of graph changes with unchanged
   sources. State the case where each change is its own batch and no reduction
   follows. Do not claim a project-wide complexity improvement from that case.

Measure graph-change calls, replay batches, covered generations, source
waves, visited keys, actual reverse-edge visits, delivered owner messages,
Base terminal offers, and maximum first-pending-to-commit time. Count static
traffic separately from ordinary Base request counters.

An additional exact optimization can combine sources whose immutable mark
sets are equal into one multi-start traversal **within the same replay
generation**:

```kotlin
for ((marks, starts) in sources.groupByEqualMarkSet()) {
    routeFromAll(starts, marks, freshVisited = HashSet())
}
```

For one fixed graph, reachability distributes over the union of starting
nodes. With live graph growth, each late binding turn still requires a fresh
traversal; shared visited state must not survive across replay batches.
If S sources have D distinct mark sets, full graph visits can fall
from S traversals to D traversals in a batch. Do not union different mark
sets and send their cross product from all starting nodes. That can create
new endpoint interests. Measure D and the shared route fraction before
assigning an expected gain. This extension needs its own equivalence and
complexity proof; source identity and durable source rows remain unchanged.

## Make reproducible bound-rule lists reclaimable through the existing manager

ThingsBoard's current run-1 normal call/entry/exit sink cache union retains
1,092,021,952 bytes. It contains 12,175,461 `ContainsMark` objects and
3,737,139 method-exit sink bindings. This is a current measurement of strong
bound-list ownership, not a count of selected/discarded rules. Shared owners
can limit how much a released cache actually frees.

**Current → new:** `TaintConfiguration.TaintRulesStorage.methodItems` strongly
keeps each method-bound list. Store reclaimable list values through the
existing analysis `SoftReferenceManager`; on a miss, repeat the full existing
resolution/validation/combination path. Keep selected forms under their
current owners. Wire the same manager before prescan populates the lists.

```kotlin
fun rules(key: ExistingCompleteRuleKey): List<BoundRule> {
    cache[key]?.get()?.let { return it }
    val value = resolveExactlyAsToday(key)
    val ref = existingManager.createRef(value)
    if (ref == null) cache.remove(key) else cache[key] = ref
    return value
}
```

This is an ownership sketch. Keep synchronization, empty results, ordered
rule ordinals, configuration lifetime, overrides and normal/`allRelevant`
views. If the existing manager is disabled, return the strong local value
without adding a strong cache fallback. This proposal does not require a
new reference manager or identity cache.

**Why it should help:** the manager can drop reproducible bindings under
pressure. The measured three sink roles give a large target outside the
routing queues. Rebinding trades CPU for memory, so measure misses, rebuild
cost and the full combined owner union. Source and sink conditions both
retain their NNF and mark-wise semantics. The first patch's byte target is
the three measured sink caches; measure source-role ownership before assigning
it a saving or extending the change to those caches.

Prove equal ordered bound rules after arbitrary cache loss, including
validation failures and overrides. Test the disabled-manager path and early
prescan population. Do not replace rule selection with a weaker predicate or
assume that a rule discarded by one form is discarded for every consumer.

## Give ordinary work fair service and reduce Base admission work

The scheduler change is separate from replay coalescing. Use the existing
quantum to bound channel draining and to give pending nonzero Work a turn
during a delivery stream. Preserve intra-actor ZERO/nonzero phase separation,
actor ownership and counted requeue. Fair service must also prevent old pending actors from
repeatedly losing to newly admitted actors with lower step counts. P still
commits at method-empty; this does not permit an early commit while method facts remain pending. The measured service-delay
counter includes method work, and is not a direct scheduler-latency measure.

First simplify owner-local admission through the existing R/P stores:

```kotlin
fun offerRequest(base: Base, marks: RequestMarkSet) {
    val committed = summaryStorage.borrowRequest(Run1Slot.Base(base))
    val changed = temporary.offerMissing(base, marks, committed)
    if (changed) requestWork()
}
```

This is an API sketch. Prove equal effective `R union P`, avoid intermediate
mark sets for pure duplicates, and never use P to answer facts before commit.
It needs no new authoritative store or cross-owner P read. It reduces cost
per offer; batching and fair service reduce repeated offers.

Only if message counts remain large after those changes, consider transient
request/source-reentry delivery coalescing:

**Current:** a sender subtracts committed R. The receiver unions into
temporary P. Several sources or waves can send the same Base mark while it
is already in P. Ordinary request joins can also send static-source reentry
rows in the same envelope class; these are routed to the source mechanism,
not stored as ordinary static requests. Each duplicate still requires a
message, owner callback and set operations. A stream of priority-1 events can delay priority-2 Work,
which commits P at the method-empty barrier.

**New:** union pending delivery batches per full destination method and
complete typed slot before posting actor work. Keep at most one scheduled
notification for that pending union. The actor offers Base rows to P and
static-origin rows to its source builder. At method-empty it commits P with
`summaryStorage.commit(temporary)` and commits the source builder through the
router, as today. The sender must not read another actor's mutable P.

```kotlin
data class NeedEndpoint(val method: MethodKey, val slot: Run1Slot)

fun enqueueNeed(key: NeedEndpoint, marks: RequestMarkSet) {
    pendingByOwner.union(key, marks)
    scheduleOwnerOnce(key.method)
}

fun receiveNeeds(method: MethodKey) {
    val batch = pendingByOwner.take(method) // atomic take; later arrivals reschedule
    for ((slot, marks) in batch) when (slot) {
        is Run1Slot.Base -> actor.offerRequest(slot, marks)
        is Run1Slot.StaticField -> actor.offerStaticSource(slot, marks)
    }
}
```

This is transient delivery coalescing, not a second authoritative request
history. Remove taken batches. Release the run's pending owners on close.
Keep the selected Base-only R/P storage and empty exclusions. Static-field
rows remain source reentry; they do not become ordinary static requests.

**Why it should help:** repeated offers are combined before their allocation
and message costs. It gives P time to become R. It cannot substitute for
reducing full static replays, and may add synchronization if the duplicate
rate falls after that change. Measure again before selecting it separately.

Prove lossless union under send/take races, counted child work before parent
completion, both arrival orders and static reentry. Prove fair service for
ordinary actor work without changing summary semantics. Use a finite bound
on repeated identical deliveries to compare message counts; do not infer
removable retained bytes from cumulative duplicate counts.

## Notify only owners with a matching FLOW route

**Current:** a SummaryStorage subscriber can have ordinary ZERO/concrete
inputs, packed concrete inputs, or actual FLOW routes. Static need dispatch
uses the general subscriber directory. A receiver can therefore be notified
and then find no reverse FLOW endpoint.

**New:** keep the existing effect subscribers. Add a mark-independent
registration for static need routing only when that owner has an actual
compatible abstract-origin FLOW route. A field lookup can first use a
candidate directory and then apply the unchanged geometry test.

```kotlin
fun registerActualFlow(binding: Binding) {
    registerInExistingOrdinaryOrPackedFlowDirectory(binding)
    needSubscribers.register(binding.fullCalleeKey, binding.owner, binding.geometry)
    onBindingRegistered() // after both registrations; replays late sources
}

fun dispatch(visit: StaticNeedVisit) {
    for (owner in needSubscribers.candidates(visit.key)) {
        owner.post(visit) // owner checks its current full bindings as today
    }
}
```

The directory must contain owners with a static current value and a
nonstatic abstract original: those are the important W-to-Base endpoints.
Concrete current inputs and ZERO inputs cannot carry a need backwards.
Register both ordinary STATIC FLOW inputs and packed diagonal FLOW relations.
Candidate coverage ignores E/mx and includes compatible broad root/class
geometry; the ordinary full matcher still decides endpoints. Do not use
the prescan call graph instead of reached, post-cleaner bindings.
Do not use current base as the origin. Preserve static cross-field routes.

**Why it should help:** it avoids empty notifications, global admission
traffic and repeated index probes. The added directory has a cost. Start
with the smallest owner-level FLOW registration; measure empty notifications
before choosing a per-field index. Its savings overlap the replay proposal.

Prove that every successful endpoint in the full-subscriber reference is
still a candidate, and that applying the unchanged matcher gives the same
endpoint union. Cover late registration, owner lifetime, duplicates,
multiple call ports and recursive bindings. Keep subscriber snapshots safe
when delivery grows another route.

## Other measured paths

### Reduce OWASP field-cleaner and type-check work

OWASP completed run-1 in 262.966 s; it later reached run-8, completed run-7
and accepted run-7. Its run-1 profile shows cleaner chains in 62.64% of CPU
samples and the field read/write wrapper in 47.83%. Type-filter work is
25.02%; lookup work is 22.37%. These shares overlap. Static need batching
alone does not address this project.

**Current → new:** a field cleaner chain repeats read, temporary-value clean,
write and type checks for each selected mark. Design a composed operation
for a proved eligible block with the same geometry and support. Reuse its
read/type result, apply the selected mark operations, and preserve the
ordinary final output and all interests. Unknown blocks use the existing
sequence. First measure the eligible fraction; the whole 47.83% is not a
proved removable cost.

```kotlin
when (val block = compileCleanerBlock(steps, input)) {
    null -> executeOrdinarySequence(steps, input)
    else -> executeComposedCleaner(block, input) // same facts AND event union
}
```

This is a design boundary. Define its recognizer
and executable semantics in Lean first. Start with direct selected-mark
nonstatic FLOW cleaners that keep geometry and support unchanged. A run-1
block can union their mx additions and requested marks. Field read/clean/write
blocks need a separate composition proof; they cannot use the direct-cleaner
rule merely because both paths call `cleanFacts`.

Test partial matches, aliases, strong kills, layer-sensitive guards, changed
marks, empty outputs with retained events, source and sink conditions, and
static DEMAND before evaluation. No source/sink action or unknown operation
may be moved across the composed block. The expected benefit is less repeated
trie rebuilding, temporary lists and type checking, not fewer semantic facts.

A separate narrow type-check proposal can memoize the complete analyzer
accessor/type check within its existing context lifetime. Existing classpath
and filter caches remain in place. Preserve classpath identity, generic and
unknown types, negative results and cancellation/error behavior. Measure key
cardinality, hits and retained bytes before introducing a new strong table.
This is an analyzer check optimization; IR storage stays outside scope.

### Preserve method-exit locations for project comparison

The two Stirling-PDF strict baseline removals have same-rule/method findings
at later returns. The Simple reporter's synthetic-exit fallback chooses the
last return. The original branches remain unproven without witness traces.
This is a correctness/reporting prerequisite, not a large-memory proposal.

**Current → new:** preserve the reached sink/return instruction through the
bidirectional observation and Simple report path instead of selecting an
unrelated final return. Retain multiple real return sites. Prove that a
reported site belongs to the reached rule occurrence and support; a synthetic
method-exit marker alone cannot prove a particular branch. Test two returns,
exception returns and last-return null/constant values. Do not weaken the
comparison to rule-plus-method identity to hide the difference. Trace
resolution can then assess actual false-positive paths after run-2 completes.
Also validate OWASP's 5,067 extra occurrences at existing sink identities.
Do not treat finding multiplicity as the allowed callback-count diagnostic
exception, or deduplicate real source/rule occurrences using only a sink hash.

## Later run-2 work, after the run-1 priority

Stirling-PDF reached run-2, completed and accepted run-1. Its run-2 heap has
4,205,626,776 bytes in conclusion bodies, including 9,705,174 ConclusionGroups.
Queue/worklist owners are only 7,890,608 bytes. Run-1 static waves are absent.
This case must not be folded into the run-1 routing claim.

First investigate **merging pending summary publications**. Current actor
pending lists have 3,767,185 entries. They batch until method-empty, but keep
individual deltas. The new design would keep the existing barrier and combine
compatible deltas by full premise and exact conclusion header before sending.
That can avoid pending wrappers and repeated summary/caller work. The count
of combinable keys and the pending owner's retained bytes are not measured;
this is an investigation candidate, not an established large saving.

```kotlin
pending.offer(fullPremise, exactHeader, delta)
// Existing method-empty barrier:
publish(pending.takeMergedBatch())
```

Keep base, layer, E/mx, tail kind and full ND premise in the key. Do not join
unrelated original/support sets. Prove that packed publication preserves the
ordinary kernel, NNF checks, ND matching, observer events and both late
arrival orders. Equal headers alone do not prove that every observer is
distributive over the batch. Keep an ordinary fallback where equivalence
is not established. Measure before selecting this change.

A separate **technical layout option** has a stronger current byte bound.
`ConclusionGroup` allocates two kind HashMaps and a two-element boolean array
at creation. The current heap has exactly two maps per group, but only 2,581
FlowGroups. At least 9,702,593 FLOW maps are empty. Those map shells and all
REACH arrays occupy 698,648,640 bytes. The new representation would allocate
kind maps only when used and store REACH in an integer mask:

```kotlin
private var reachBits: Int = 0
private var flow: MutableMap<Base, FlowGroup>? = null
private var taint: MutableMap<Base, TaintGroup>? = null

fun addReach(layer: Layer): Boolean {
    val bit = 1 shl layer.ordinal
    if (reachBits and bit != 0) return false
    reachBits = reachBits or bit
    return true
}
```

**Why:** it avoids known unused containers while keeping the same stored
facts and matching algorithm. The 698.649 MB is old gross object storage,
not measured net savings. Check replacement object layout and empty-map
ownership. Lazy layer lists may save more, but their unused fraction is
unmeasured; shared tree objects prevent deriving it from tree counts alone.
Dense statement arrays are a smaller target: run-1 ThingsBoard arrays occupy
2.965 MB, and run-2 PDF allocates 21,332,960 cells. Neither figure is a count
of occupied fact buckets.

Prove exact `add` results/deltas and enumeration for REACH/FLOW/TAINT in both
layers, including T1/T2 subsumption and annotation changes, cancellation and
interning. This is a technical optimization because current evidence does
not show that the 9.7 million conclusion states themselves are avoidable.
It is deferred while the primary target remains completion of run-1 within
60 seconds.

## Validation for a selected next change

Follow [proof-guide.md](../../spec/proof-guide.md). First model the scheduling
or routing change with a constructive finite reference. Prove equal semantic
observations and a stated work reduction. Then add negative controls and
implement the selected change. Diagnostic callback counts may depend on
batching; source/sink identities, rule occurrences, actual initials, summary
effects, layers, full ND support and Base endpoint unions must be preserved.

Keep the current ordinary reference, both late arrival orders, static/Base
alternation, strong kills, concrete cleaners/conversions, EXACT/STAR kinds,
class components and full method/call/stage identity. Need routing must never
manufacture concrete truth. Do not drop STAR-to-ANY exit effects as identities.

Use the same four configurations and a fresh main comparison. Report run-1
engine time apart from prescan, setup, dump and report barriers. Always state
latest reached, completed and accepted forward iterations. Completion within
60 seconds is the run-1 target. A lower interrupted-run fact count or an
earlier OOM is not an optimization gain.
