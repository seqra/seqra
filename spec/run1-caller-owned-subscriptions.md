# Run-1 caller-owned inputs, requests and summary subscriptions

Date: 18 October 2026. Status: **selected design; analyzer not changed**.
Scope: FORWARD run-1, field limit 1. This is the complete caller-subscription
contract. It replaces the proposed entry-offer/source-pair routing in the
[earlier request proposal](../docs/design/bidirectional-bottom-up-request-processing-20261018.md).
It replaces the dynamic static-need graph and graph replay in the
[measured implementation](run1-static-need-replay-batching.md) when implemented.
It does not claim that the measured implementation already follows this file.

Latest measured runtime: `f18b1c5bb`. Conductor and ThingsBoard both reached
**run-1 / completed none / accepted none**. There is no new project run or
timing result for this design. The target remains run-1 **engine time** within
60 seconds. IR storage and cache/reference manager changes are outside scope.

The [proof dossier](run1-caller-owned-subscriptions-proofs.md) follows
[proof-guide.md](proof-guide.md). It separates the selected calculus, the
checked routing/summary models and the adapters still required in Kotlin.

## 1. Reference and required result

`Default` means ordinary incremental propagation with the following **same**
policies in both procedures. It does not mean an old commit with different
static admission or layer rules.

```text
same actual suppliers and canonical initial grammar
same eager actual static-mark admission
same local AP, source/sink, NNF, cleaner, overwrite and call-plan operators
same full call bindings, ND premises, contexts and semantic observations
same early static DEMAND policy and Empty-E run-1 field-write rule
same ordinary universal S.* -> S.* [DEMAND] default for each reached method
same summary publication/identity-compaction policy

Default:
  caller retains ordinary bound inputs and applies callee summaries
  callee abstracts supplied inputs and deduplicates canonical initials
  each method merges requests; requests join actual inputs at each caller

Caller-owned:
  caller admits inputs once and submits canonical initial descriptors
  callee retains the same initial/body/summary work
  covered same-field STATIC REQUEST relays use fixed-call-graph reachability
  productive input/request joins remain at their actual caller statements
```

The requirement is inclusion, for each full method key K:

```text
Default.rawSummary(K)       subset CallerOwned.rawSummary(K)
Default.publishedSummary(K) subset CallerOwned.publishedSummary(K)
Default.semanticEvents(K)   subset CallerOwned.semanticEvents(K)
```

A summary here is a **derivable semantic atom**, including its complete
premise/support, conclusion, AP payload, layer and endpoint. `rawSummary`
is the derivable result set before storage subsumption; `publishedSummary`
applies the same semantic publication/compaction predicate. A packed store
may represent an atom through an ordinary subsuming row rather than keep its
literal old header/leaf. Its adapter must prove that every covered atom
still has all its ordinary applications represented. The inclusion above
does not assert physical row membership after such compaction.
This is not equality of method/field/mark counts.
Prescan reachability can deliver extra conservative interests. These can
start additional **actually supplied** nonstatic marks. Thus equality to the
default dynamic request set, or an unconditional reduction of total work,
is not required. Equality holds for the transport model when both sides use
the same widened interest relation.

The claim concerns finite derivations at a fair complete run barrier. It
does not claim completion under a timeout/OOM. Callback order, batch size and
batch-dependent diagnostic counts, such as `ExternalMethodRecord.callSites`,
can differ. Preserve semantic source/sink/report identities and rule occurrences.

## 2. Data and invariants

### 2.1 Full bindings and independent interests

```kotlin
data class CallSiteKey(
    val caller: MethodKey,       // method + entry + context
    val statement: StatementId,
    val callee: MethodKey,       // full key, not CommonMethod alone
    val stageFrom: CallPoint,
    val stageTo: CallPoint,
)

data class BoundInput(
    val original: FullPremise,   // singleton or complete ND premise
    val current: FullFact,       // AP, tail, E, mx, mark kind, layer, cut flags
    val support: ImmutableSupport,
    val joinHeader: OrdinaryJoinHeader,
)

sealed interface Slot {
    data class Base(val base: NonstaticBase) : Slot
    data class StaticField(val field: FieldId) : Slot // includes class identity
}
typealias Requests = Map<Slot, MarkSet>
```

The subscription key groups inputs at one statement, target and call stage.
The original premise is **inside the input relation**, not in that outer key.
Ordinary layer/flag/header distinctions still partition joins where required.
Keep every part needed by an ordinary match. Preserve each member's original,
layer and support. Combine distinct members only when the ordinary full-port
ND relation permits that complete tuple, joining its layers and premises as
usual. A shared request union cannot make incompatible call tuples compatible.
The structural subscription key does not include a member's caller layer.
Compatible NORMAL and DEMAND members may form one ND application with a
DEMAND result. Their full individual rows remain available to the matcher.

A request is an independent interest in each member of a mark set. It has no
field exclusion E, FLOW mark exclusion mx, query tail, concrete proof or ND
conjunction. Request geometry ignores E/mx in the supplier. Do not subtract
mx from offered interests: excluding T already emits the immediate interest
for T. An abstract FLOW mark is not an actual concrete T.

Only a singleton abstract original propagates a need backwards. Concrete,
ZERO and complete ND originals do not. Choose the destination from the
**original slot**, not from the current fact base. A Base request can therefore
map to a static original, and a static request can map to a Base original.

### 2.2 Exhaustive initial grammar

`S` is the static base, distinct from ZERO. A class accessor does not consume
the field limit. `b` is nonstatic/nonzero. All initial E and mx are empty.
`STAR` and `ANY` have different meanings; see [AP](ap.md).

| Initial | Initial tail | Started current fact | Admission |
| --- | --- | --- | --- |
| ZERO | EXACT | REACH/NORMAL | Actual reach input |
| `b (*)` | STAR | STAR FLOW/NORMAL | Actual compatible base geometry |
| `S.C.f (*)` | STAR | STAR FLOW/DEMAND | Actual compatible field geometry |
| `b (T)` | EXACT | Exact TAINT/NORMAL | Actual exact T plus Base request |
| `b (T)` | STAR | ANY TAINT/DEMAND | Actual nonexact T plus Base request |
| `S.C.f (T)` | EXACT | Exact TAINT/DEMAND | Actual exact field T, eager |
| `S.C.f (T)` | STAR | ANY TAINT/DEMAND | Actual compatible nonexact T, eager |
| `S.C (T)` | EXACT | Exact class TAINT/DEMAND | Actual exact class T, eager |
| `S.C (T)` | STAR | ANY class TAINT/DEMAND | Actual compatible broad class T, eager |

These nine families are exhaustive. There is no S-root initial, abstract
class initial, ANY initial, nonstatic field-path initial or second field
accessor in an initial. In particular, exact tainted empty static root
`(S, ., $, {}, T)` is outside the supplied run-1 domain.

Only an exact supplied point at the selected field/class point gives an
EXACT concrete initial. A compatible nonexact/descendant supply gives STAR.
An exact class/root point does not supply a deeper field. Broad nonexact
actual static supplies use the existing legal coordinate/class normalization
**before** the subscription boundary. Keep class coordinates separate from
field coordinates; a class descriptor is not a class request slot.

### 2.3 Direct static descriptors

```kotlin
sealed interface StaticEntry {
    data class AbstractField(val field: FieldId) : StaticEntry
    data class ConcreteField(
        val field: FieldId, val initialTail: ExactOrStar, val marks: MarkSet,
    ) : StaticEntry
    data class ConcreteClass(
        val cls: ClassId, val initialTail: ExactOrStar, val marks: MarkSet,
    ) : StaticEntry
}

data class PreparedInput(
    val binding: BoundInput,
    val staticEntries: StaticEntrySet, // already canonical; can remain packed
    val otherUnconditionalEntries: InitialSet,
    val presence: InputPresence,
)
```

At the caller/callee boundary static fields and descriptor paths pass as is.
There is **no second static input projection**, legal-field inventory walk or
retagging there. Descriptor decoding must equal the ordinary canonical
projector on that actual input. The existing normalizer remains before this
boundary and is the same in both procedures.

```text
actual current C.f EXACT/T  -> descriptor initial C.f EXACT/T
actual current C.f ANY/T    -> descriptor initial C.f STAR/T
actual current C.f.h/T      -> existing normalizer -> C.f STAR/T
descriptor C.f STAR/T      -> initial tag C.f STAR/T, start current ANY/D
```

The full actual row stays in the caller. A descriptor is an admission token,
not a replacement for that row during summary/ND application. Deduplication
or packing must retain the canonical initial tag. Do not turn STAR-to-ANY
effects into identities.

### 2.4 Layers and overwrites

Normalize static dependencies before any layer-sensitive guard. Static
initials and static-dependent caller results are DEMAND. Within a derivation,
DEMAND stays DEMAND, including a static-to-local result. Join all actual
contributing ND/input layers when applying a summary.

Callee starts are relative to the callee premise. An independently valid
nonstatic `b.*` callee start is NORMAL even when its caller input is DEMAND.
Keep that caller history in the full input and join it back on application.
Do not demote every relative nonstatic callee summary because of one caller.

Use ordinary strong overwrites. For a definite `x.f = y`:

```text
old x.*   -> DEMAND survivor, same AP, Empty E
old x.f.* -> killed when definitely overwritten
old x.g.* -> unchanged when disjoint
```

General rule: if a **surviving** preservation result would need a field
exclusion update, move it to DEMAND and keep E empty. Demotion alone does not
change its tail to ANY. Evaluate RHS/source/rule outputs separately from the
old-value kill. Requests and observations survive even if all value outputs
are killed.

## 3. Stores and owners

| Store | Owner | Contents |
| --- | --- | --- |
| InitialFactStore | Callee analyzer | Canonical initials already started, with ordinary start metadata |
| MethodEdgeStore and work queue | Method analyzer | Full premise, point, fact/header/support; only fresh semantic deltas enter the queue |
| Bound input relation | Caller subscription | Existing ordinary relation, including full ND support; **no second supplier/added-fact store** |
| Raw result union and publication buffer | Method analyzer | Existing full results and fresh eligible publication deltas |
| SummaryStorage | Full method key | Published ordinary summaries and committed locally produced outgoing requests |
| Temporary outgoing requests | Method analyzer | Write-only merged local offers before commit; same RequestStorage class |
| Callsite request P/A | CallSiteSubscription | Pending/processed merged interests for this callsite's input joins |
| MethodDemandView | `(full callee K, field)` | Union of reached outgoing static interests and callsite subscriber references |
| Static source directory | Run | `field -> source SCC -> RequestRef`; references to authoritative method requests, no copied marks |
| Static view directory | Run | `field -> entry SCC -> ViewRef`; no input payload, marks or source×original pair history |
| Reachability service | Run | Fixed prescan SCC DAG and reusable method reachability answers; no field/mark closure |

```kotlin
class CallSiteSubscription(val key: CallSiteKey) {
    val inputs = existingBoundInputRelation(key)
    var pending = RequestStorage()
    val answered = RequestStorage()
    val requestSubscriptions = Set<RequestStreamRef>()
    val summaries = summaryStorage(key.callee) // borrowed, no answer copy
}

class MethodDemandView(val key: DemandViewKey) {
    val marks = RequestStorage() // only key.field is used
    val subscribers = Set<CallSiteSubscriptionRef>()
}
data class DemandViewKey(val callee: MethodKey, val field: FieldId)
```

`answered` means that a request has been processed against current inputs and
its late-input service is installed. It does **not** mean T was found, a
summary exists, or the answer stream is closed. Later inputs and all later
matching summaries still receive service.

An outgoing need is authoritative once in its source method. View and
callsite unions are explicit extra caches, not inherited per-relay requests.
Count those caches in memory measurements. Do not call the total state
source-only storage.

## 4. Prescan and fixed reachability

```kotlin
fun finishPrescan(resolvedCalls: CallGraph) {
    legalCoordinates = existingLegalCoordinateNormalization()
    sccDag = stronglyConnectedComponents(resolvedCalls)
    reachability = FixedReachability(sccDag) // reflexive, including recursion
}
```

Prescan already resolves the calls. Use that graph; do not resolve them again.
It must contain every actual call target later used by a same-field request
relay. An unknown/dynamic producer without this guarantee uses the ordinary
request fallback. It must not be silently omitted.

Do not build static field requests, transitive frozen mark sets, per-mark
reachability, a dynamic need-binding graph, a source wave or `graphChanged`
replay. Local read/rule/effect metadata still schedules the ordinary body
and its certified existing packed lanes. A lack of GETSTATIC alone is not a
proof that a method has no rule/model/static effect.

Reachability tests interest only:

```text
view(H,f) accepts outgoingNeed(B,f,T) iff Reach*(H.method, B.method)
```

It never proves that an actual T reached a call/check, never provides an ND
premise, and never applies a summary. Those operations retain the full actual
binding and statement order.

## 5. Arriving call inputs

All handlers below are owner-serialized. Shared register-and-snapshot APIs
are linearizable. Callbacks/messages run after releasing their locks.

```kotlin
fun CallSiteSubscription.onInput(offered: PreparedInput) {
    val fresh = inputs.unionAndGetDelta(offered.binding)
    if (fresh.isEmpty) return

    ensureSummarySubscriptionBeforeReplay()
    ensureRequestStreamsFor(fresh) // exact Base streams and productive views

    // No raw supplier history is sent to the callee for later abstraction.
    // Restrict prepared descriptors to the admitted semantic delta.
    val starts = admittedUnconditionalEntries(offered, fresh)
    submitInitialBatch(key.callee, starts, requiredPresence(offered, fresh))
    applyExistingSummaries(fresh)
    applyDemand(fresh, answered.borrowMatchingRows(fresh))
}

fun ensureRequestStreamsFor(fresh: InputDelta) {
    for (slot in actualCanonicalAbstractSlots(fresh)) {
        if (slot is Base) subscribeExactCalleeRequestStream(slot)
        if (slot is StaticField && fresh.hasEligibleAbstractFlow(slot) &&
            !coveredSameFieldRelay(fresh, slot)) {
            subscribeSharedView(DemandViewKey(key.callee, slot.field))
        }
    }
}
```

The caller performs admission before work dispatch. Repeated semantic input
does not resend starts. A fresh mark/header/support delta still performs all
ordinary joins it enables. A partially fresh input cannot reuse an admission
answer that drops header growth or ND support.

Unconditional starts comprise actual ZERO, legal abstract geometry and eager
actual static/class concrete descriptors. Requested nonstatic concrete starts
are emitted only by the request join in §7. These paths do not both emit them.

Deliver input presence even when a real input has no legal initial. It creates
the callee analyzer, installs its ordinary default and records input-presence
flags, including the ordinary DEMAND-input flag. Merely allocating an empty
SummaryStorage does not satisfy this requirement.

## 6. Callee fact and result work

```kotlin
fun Callee.onInitialBatch(entries: EntrySet, presence: InputPresence) {
    ensureAnalyzerInitialized() // includes stored default S.* -> S.* [D]
    retainRequiredInputPresence(presence)
    for (entry in entries) {
        val initial = decodeCanonicalEntry(entry)
        if (initialStore.add(initial))
            offerFact(initial, entryPoint, ordinaryStart(initial))
    }
}

fun offerFact(original: FullPremise, point: Point, offered: FullFact) {
    val fact = selectedRun1Normalize(original, offered)
    val delta = methodEdges.unionAndGetDelta(original, point, fact)
    if (!delta.isEmpty) methodQueue.merge(original, point, delta)
}

fun processFact(item: FactDelta) {
    val out = ordinaryTransfer(item)
    // out has four independent channels; an empty facts list drops none.
    for (fact in out.facts) offerFact(fact.original, fact.point, fact.actual)
    for (need in out.needs) offerOutgoingNeed(need.original, need.marks)
    for (summary in out.summaries) queueSummary(summary)
    retainSemanticEvents(out.events)
}

fun offerOutgoingNeed(original: FullPremise, marks: MarkSet) {
    val slot = original.singletonAbstractSlotOrNull() ?: return
    offerOutgoingSlot(slot, marks)
}

fun offerOutgoingSlot(slot: Slot, marks: MarkSet) { // method-owner handler
    temporary.offer(slot, marks)
    scheduleMethodDrainOnce()
}

fun commitOutgoingNeeds() {
    val delta = summaryStorage.commit(temporary)
    temporary = RequestStorage()
    // Base -> exact callee-request stream; StaticField -> source directory.
    for ((slot, freshMarks) in delta) publishOutgoingRequest(slot, freshMarks)
}
```

Commit performs union with committed requests and returns only fresh marks.
Install a static source reference before making its first delta observable;
registering it also replays its current committed snapshot (§8). Duplicate
notifications are safe because each downstream owner uses union admission.

Use every ordinary need producer: source **and** sink conditions, reads into
locals followed by checks, cleaners, conversions, and immediate interests
from excluded marks. Do not suppress a same-field local need on the assumption
of an inherited frozen request plan; that plan no longer exists.

NNF rules and ND premises stay in the ordinary kernel. A conjunction of
requested marks is not proof of a conjunction of actual facts. Negative
checks use the unchanged per-fact semantics, not absence in a pending request
or global fact store. Unknown/nontransparent effects retain the ordinary
kernel. This contract introduces no new transparent-region proof.

## 7. Callsite request admission and joining

```kotlin
fun CallSiteSubscription.onRequest(slot: Slot, offered: MarkSet) {
    val fresh = offered - answered[slot] - pending[slot]
    if (fresh.isEmpty) return
    pending.offer(slot, fresh)
    scheduleCallerDrainOnce()
}

fun CallSiteSubscription.drainRequests() {
    val delta = answered.commit(pending) // A visible before outward work
    pending = RequestStorage()
    for (input in inputs.candidates(delta))
        applyDemand(input, delta.borrowMatchingRows(input))
}

fun RequestStorage.borrowMatchingRows(input: BoundInput): BorrowedRows {
    // rows is a plain Slot -> immutable MarkSet map, not an AP query index.
    val slot = input.current.directRun1RequestSlotOrNull()
    if (slot != null) return rows.borrowRowIfPresent(slot)

    // Broad/class suppliers: test existing request keys using ordinary
    // geometry. Do not enumerate project fields or expand mark members.
    return rows.borrowEntries { (slot, _) ->
        ordinaryInterestGeometryMatches(slot, input.current)
    }
}

fun CallSiteSubscription.applyDemand(input: BoundInput, rows: BorrowedRows) {
    val wanted = MarkSetBuilder()
    for ((slot, marks) in rows) {
        if (!ordinaryInterestGeometryMatches(slot, input.current)) continue
        // Per SLOT, before mark union. A Base need must not be cut here.
        if (input.current.isFLOW && slot is StaticField &&
            coveredSameFieldRelay(input, slot)) continue
        wanted.union(marks)
    }
    if (wanted.isEmpty) return

    when (input.current.kind) {
        FLOW -> {
            val originalSlot = input.original.singletonAbstractSlotOrNull()
                ?: return
            callerOwner(key.caller).offerOutgoingSlot(originalSlot, wanted.snapshot())
        }
        TAINT -> if (input.current.base != STATIC) {
            val actual = wanted.intersect(input.current.concreteMarks)
            submitInitialBatch(key.callee,
                ordinaryRequestedNonstaticInitials(input, actual),
                InputPresence.alreadyDelivered)
        }
        REACH -> Unit
    }
}
```

The storage API must retain slot/mark association. It is insufficient to
return one already-unioned mark set: a mixed input group can contain a
covered static row and a productive Base/changed-field row.
`directRun1RequestSlotOrNull` returns a key only when the **entire** supplied
geometry has exactly one possible canonical request slot: Base(b) for one
nonstatic base, or StaticField(C,f) for geometry rooted at one field C.f.
A broad root/class, a packed set such as `{C.f,C.g}`, or a mixed component
row returns null and tests all existing request keys. It must not select only
the first packed component. Matching a packed row tests its relevant actual
components with the ordinary interest geometry. This is request matching,
not another static initial projection. All borrowed rows expose immutable
mark snapshots; neither branch uses E or mx.

Old A marks join only fresh inputs. Fresh P marks join existing inputs when
drained. Pending marks are not also joined on input arrival. Thus each
admitted semantic input/request pair is handled once by one of those two
frontiers. Registering a request service does not require repeating input
delivery. A fresh ND/header delta is a new semantic candidate, not a duplicate.

Static TAINT was already eagerly submitted on input arrival. It does not
propagate a need backwards or require another request-dependent static start.
Nonstatic TAINT starts only marks actually present and compatible with ordinary
geometry. `wanted` can never manufacture T.

Method-local outgoing needs and callsite pending needs use separate owners.
`offerOutgoingSlot` in this handler addresses **key.caller**, not the callee
whose request stream was received.

## 8. Shared static demand views

```kotlin
fun subscribeSharedView(key: DemandViewKey, callsite: CallSiteRef) {
    val view = views.getOrCreate(key)
    view.ensureDirectoryRegistration() // register before snapshot/replay
    val snapshot = view.subscribeAndSnapshot(callsite)
    postRequest(callsite, Slot.StaticField(key.field), snapshot)
}

fun onFirstStaticSource(source: RequestRef) {
    val groups = directory.registerSourceAndSnapshotViews(source)
    // Directory lock released before reachability or owner work.
    for ((entryScc, viewRefs) in groups.forField(source.field))
        if (reachability(entryScc, source.owner.method.scc))
            for (view in viewRefs) postSourceSnapshotToView(view, source)
}

fun onNewView(view: ViewRef) {
    val groups = directory.registerViewAndSnapshotSources(view)
    for ((sourceScc, sources) in groups.forField(view.field))
        if (reachability(view.callee.method.scc, sourceScc))
            for (source in sources) postSourceSnapshotToView(view, source)
}

fun onCommittedStaticDelta(source: RequestRef, fresh: MarkSet) {
    for (view in directory.reachableViewsFor(source.field, source.owner))
        postSourceDeltaToView(view, source, fresh)
}

fun MethodDemandView.onSource(source: RequestRef, marks: MarkSet) {
    require(source.field == key.field)
    require(reachability(key.callee.method, source.owner.method))
    val fresh = this.marks.unionAndGetDelta(key.field, marks)
    if (!fresh.isEmpty) notifyAllSubscribers(fresh)
}
```

Both directory registration/snapshot operations use the same synchronization
boundary. A source before a view is found by view replay; a view before a
source is found by source registration. A concurrent source update is covered
by its snapshot or by its delta notification, possibly both. Never neither.
View subscriber registration has the same snapshot/delta rule. Callbacks run
outside locks and are admitted by the receiving owner's union.

Select field first, then SCC reachability. The directory holds references,
not a request-source×input-original pair set. Do not retain one delivered-mark
set for each source/view or source/original pair. `MethodDemandView.marks`
merges all reachable sources **before** callsite/original fan-out.

The view key retains the full callee key. Reachability deliberately uses the
fixed method graph and can be coarser than contexts and statement execution.
That widening affects interest, never actual summary matching.

## 9. Which static relays may be omitted

```text
productive input:
  caller original Base(p), current static f
  caller original static g, current static f, g != f
  any other eligible input lacking a proved same-field coverage certificate

covered relay:
  ordinary abstract static f original -> canonical static f input
  with retained entry coverage and an actual prescan caller->callee edge
```

For every omitted relay, retain the ordinary actual input for summaries and
ND support. Omit only its request-view subscription and its static-slot
backward request join. Do not drop forward activation, local rules, input
presence or any fact/summary edge.

The entry-coverage invariant has a finite witness: following actual same-field
entry ancestry reaches a retained productive subscription, or an external
actual input that requires no backward FLOW request. Unseeded recursive
cycles cannot create a fact/initial. Productive views and source requests
remain available for the run. A synthetic externally started abstract entry
without such a witness keeps the ordinary fallback subscription. The witness
is a proof obligation for the producer; this design does not require storing
a path object for every runtime fact.

Consider a default reverse request path. Every maximal same-field segment
corresponds to a forward prescan path from the next productive callee entry
to the source of that segment. Reflexive/transitive reachability delivers
the same mark through that entry's view. The productive join retains the
exact full actual binding and maps to its original. Repeat at changed fields
and Base slots. Each finite default path therefore yields an optimized path
to the **same Base endpoint and mark**, with its productive bindings intact.
Cycles do not require a depth limit.

Do not use `original is Static` as a skip predicate. For example:

```text
A: original p -> current C.f enters H          // productive view(H,f)
H: initial C.f -> local b; pass b to G
G: requests Base(b)/T
H's actual Base join maps the need to original C.f/T
view(H,f) receives it via reflexive Reach*(H,H)
A's actual productive join requests p/T
```

Cutting H's Base join because its original is static loses this endpoint.
A change `original C.g -> current C.f` likewise maps f/T to **g/T** and must
remain productive.

## 10. Summary answers and caller continuation

```kotlin
fun CallSiteSubscription.onSummary(delta: SummaryDelta) {
    // Match complete ordinary tuples, including ND members from distinct
    // compatible original premises. No single-row approximation here.
    for (match in inputs.ordinaryMatches(key, delta)) {
        val result = ordinaryFullPortMatchAndApply(key, match, delta)
        for (out in result.facts) resumeOrdinaryCallStages(key, out)
        retainSemanticEvents(result.events)
        for (need in result.needs) offerOutgoingNeed(need.original, need.marks)
    }
}

fun CallSiteSubscription.applyExistingSummaries(fresh: InputDelta) {
    for (summary in summaries.candidates(fresh))
        for (match in inputs.ordinaryMatchesEnabledBy(key, fresh, summary))
            applyAndDispatchAllChannels(key, match, summary) // as onSummary
}

fun queueSummary(edge: FullSummary) {
    val fresh = rawResults.unionAndGetDelta(edge)
    publicationBuffer.merge(samePublicationPolicy(fresh))
}

fun flushSummaries() {
    summaryStorage.publish(publicationBuffer.takeAndReset())
}
```

Register the summary consumer before replaying existing summaries. New inputs
join old summaries; new summaries join old inputs. The ordinary relation must
also schedule a complete ND match when its last required input/support arrives.
The request index does not replace that full ND relation.

Apply **all** matching summary edges. “Closest answer” means the existing
callee SummaryStorage stream at this actual statement/stage; it is not a
single answer selected by distance in the global graph. A source whose T
request was already processed still replays stored answers and remains
subscribed for future answers. No need for a normal return summary to emit
a source/sink event or request at an earlier point.

Publish the ordinary universal `S.* -> S.* [D]` default when an actual input
reaches/creates a method, even if the start batch is empty. There is no S-root
initial, no normal-exit check and no special run-1 call bypass. Apply the
default through the same SummaryStorage subscription path as other summaries.

If pure static identities are compacted, use the **same** full guard on both
sides of the comparison. Require actual coverage by the root default, exact
identity payload/layer/support and singleton ordinary semantics. A concrete
STAR premise to ANY conclusion, nonidentity effect, ND premise, source/sink
event, or different layer is not an identity just because its field matches.
The no-loss theorem compares the semantic derivable atoms for equal raw and
published policies; it does not claim a removed physical row still exists.
Ordinary packed/subsumption storage must preserve the applications of those
atoms. A separate
semantic-default expansion can be used only when stated explicitly.

## 11. Barriers, progress and lifetime

Use method queue empty as the default batching barrier for local outgoing
requests and summary publication. A caller-owner drain is also scheduled when
requests arrive while its method queue is already empty. A view flush must
likewise be scheduled without waiting for unrelated fact work.

```text
own one work reservation before retaining/posting a new task
run local fact work
at queue-empty drain:
  commit local temporary requests
  drain callsite request P into A and process its delta
  publish summary buffer
  flush batched initial/view/request notifications
release reservation only after descendants are reserved
```

This barrier lets multiple offers merge. Empty queue means “flush now”, not
“this method is final”. Unit/run completion must account for local queues,
pending drains, view/directory messages, initial batches, publication buffers
and cross-unit deliveries. A parent cannot release its last reservation
before reserving its child work. If fact work is continuous, the run barrier
still drains outstanding requests before completion; no threshold or pending
memory cap is part of this design.

A fair completed run eventually services each queued delta. No stronger
termination claim is made for an infinite workload. Cancellation produces
an incomplete run, not a false completed barrier.

Keep source requests, active views, callsite inputs/A and summary subscriptions
for the run, including streams whose current P is empty. Remove them only
with run teardown or another separately proved lifetime rule. Do not close
a request because it once found a fact or because a method returned once.

At a complete run-1 handoff, persist NORMAL summaries as usual. Static-related
results are already DEMAND. Later runs use their ordinary demand/abstraction
logic; these run-1 routing caches do not become later-run fact evidence.

## 12. Required traces

```java
// Bootstrap: need before any concrete T exists.
A(p) { C.f = p; B(); }
B()  { check ContainsMark(C.f, T); }
```

```text
A's FLOW p->f at B() stores its full binding and activates view(B,f)
B's abstract f evaluation emits outgoing static f/T (not a hit)
view(B,f) -> A's pending f/T -> actual FLOW join -> outgoing Base(p)/T
actual p/T arriving later joins A's committed Base request
ordinary execution writes actual f/T and sends its eager canonical descriptor
B evaluates the concrete T and applies/publishes ordinary effects
```

```java
// Changed field; transparent static request relays can be skipped.
A(p) { C.g = p; H(); }
H()  { B(); }
B()  { C.f = C.g; check ContainsMark(C.f, T); }
```

```text
B produces original-g/T, not f/T
Reach*(H,B) -> shared view(H,g) -> A's actual p->g join -> p/T
H's covered g->g request relay needs no copied request row
actual summaries and strong-write effects still use their real ports
```

```java
// A coarse interest must not become concrete truth.
A(p) { B(); C.f = p; }
B()  { check ContainsMark(C.f, T); }
```

The later write is not a prepared input at the earlier B() stage. The prescan
edge A->B cannot itself supply that input. If another valid entry view makes
the interest reach p conservatively, actual propagation still decides whether
T reaches B's check. A rule/ND hit never uses the interest as its premise.

```text
arrival orders:
  source before view / view before source
  view marks before callsite subscriber / subscriber before marks
  request before actual input / actual input before request
  summary before input / input before summary
```

All orders must preserve the same covered semantic result. Duplicate
notifications may occur at snapshot/delta boundaries; receiving unions and
the P/A frontiers stop repeated semantic work.

## 13. Savings and limits

Ownership alone saves no body analysis: the default already deduplicates
initials, merges request marks and reuses summaries. Direct canonical static
descriptors avoid a new projection boundary, not the existing pre-boundary
normalization. Do not claim a legal-inventory scan is removed for every input
if the default packed lane did not perform that scan.

Possible savings are duplicate dispatch after caller admission, canonical
start batching, and eliminated **uncovered** static relay request joins.
For a chain with h skipped relays and k uncovered marks, one productive join
can replace h+1 joins. Under separate source/view/callsite caches the logical
request memberships are 3k instead of (h+1)k, excluding common endpoints.
That state saving is strict only for h>2. It is not a byte or total-runtime
bound. Shared r-reader/p-original union restores the default r+p behavior;
it is not a new saving over that default.

Pattern/reachability work, overlapping views and extra conservative initials
can offset those gains. The exact comparison and required counters are in
[caller subscriptions versus default](../docs/design/bidirectional-caller-subscriptions-vs-default-20261018.md).

## 14. Implementation boundary

No runtime change is authorized by writing this specification. Before a
separate implementation is accepted, verify these adapters against the
constructive models and distinguishing cases in the proof dossier:

```text
RunMethodCallServices: couple ordinary input admission to prepared dispatch
CalleeSubscriptions/SubscriptionManager: retain full inputs, P/A and full joins
Run1Abstraction/static codecs: exact canonical descriptor round trip
RunMethodAnalyzer: presence-only initialization, relative starts, all events
SummaryStorage: committed source requests, summary replay and publication
UnitRunner/run accounting: no lost snapshot/delta work or early completion
prescan: every elided same-field binding is covered by the fixed graph
```

Reuse the existing bound-input and summary stores. Do not add a weak cache,
reference manager rewrite, IR change or second supplier history to implement
this protocol.
