# Caller-owned subscriptions and input processing

Date: 18 October 2026. Status: proposed refinement; analyzer unchanged.
Scope: run-1 input abstraction, requests and summary application.
Latest measured conductor and ThingsBoard: **run-1 / completed none /
accepted none**, on f18b1c5bb. No new project measurement.

This refines the [entry-subscription proposal](bidirectional-bottom-up-request-processing-20261018.md)
and addresses the [shared-bottleneck counterexample](bidirectional-request-savings-vs-ordinary-20261018.md).

## 1. Ownership

Move input projection and the request/input join to the caller's subscription.
The callee still evaluates its body once per new canonical initial.

~~~text
caller at statement s:
  retain full bound inputs
  merge incoming request marks
  project actual inputs into canonical callee initials
  apply existing/future callee summaries

callee:
  deduplicate canonical initials
  process local fact deltas
  retain/publish ordinary summaries
  publish local outgoing mark needs
~~~

The existing caller-owned CalleeSubscriptions already stores bound inputs and
applies summaries. The change is to consolidate input abstraction and request
processing there, with subscriptions grouped by call statement/target/stage.
Moving the same old per-input operations without grouping would give no saving.

## 2. One object per call statement, target and stage

~~~kotlin
data class CallSiteKey(
    val caller: MethodKey,
    val statement: Statement,
    val callee: MethodKey,
    val stageFrom: CallPoint,
    val stageTo: CallPoint,
)

class CallSiteSubscription(val key: CallSiteKey) {
    val inputs = existingBoundInputRelation(key) // same store, no second copy
    var pendingRequests = RequestStorage()      // P
    val answeredRequests = RequestStorage()     // A, processed request marks
    val summaries = summaryStorage(key.callee)  // borrowed answer stream
}
~~~

The original premise is inside inputs, not the subscription key. Thus p
different originals share one request accumulator. Keep complete original,
payload, layer, flags and ND support in each input row. Do not share truth
between different statements, targets or call stages. Join-specific header
distinctions still partition the ordinary relation where required.

## 3. Arriving facts

~~~kotlin
fun CallSiteSubscription.onFact(original: FullPremise, actual: Fact) {
    val fresh = inputs.unionAndGetDelta(original, actual)
    if (fresh.isEmpty) return

    ensureSummarySubscriptionBeforeReplay()
    ensureRequestSubscriptionsFor(fresh)

    // Pure unconditional projection: abstract geometry + eager actual statics.
    // Requested nonstatic concrete starts are handled by applyDemand below.
    sendInitials(key.callee,
        projectUnconditionalInitials(fresh, calleePolicy(key.callee)),
        requiredInputPresence(fresh))
    applyExistingSummariesTo(fresh)

    // Only A: pending requests will see this row when P is drained.
    applyDemand(fresh, answeredRequests.matchingRows(fresh))
}

fun Callee.onInitialBatch(initials: InitialSet, presence: InputPresence) {
    retainRequiredInputPresence(presence) // includes ordinary demand-input flags
    for (initial in initials)
        if (initialStore.add(initial))
            enqueueStartFact(initial)
}
~~~

Batch initial submissions and preserve packed static descriptors. The callee
keeps global initial deduplication across all callers. It receives starts and
required presence metadata, rather than raw supplier inputs for abstraction.
There is no new callee-side added-fact store.
Presence metadata must also be delivered when the projected initial set is empty.

Keep the current initial grammar, actual-mark policy and DEMAND normalization.
Static actual T remains eagerly admitted under the current contract. Ordinary
nonstatic concrete admission still requires its mark request. Keep local rule
evaluation and need production in the callee; move only input handling.
Callee initial layers are relative to the callee premise. Caller history stays
in the full input row and joins back when its summary is applied.
Unconditional projection includes actual ZERO, legal abstract geometry, and
the existing eager static/class concrete initials; requested nonstatic concrete
initials are emitted only by applyDemand.

## 4. Request deltas merge before visiting originals

~~~kotlin
fun CallSiteSubscription.onRequest(slot: Slot, marks: MarkSet) {
    val fresh = marks - answeredRequests[slot] - pendingRequests[slot]
    if (fresh.isEmpty) return
    pendingRequests.union(slot, fresh)
    scheduleOwnerWorkOnce()
}

fun CallSiteSubscription.drainRequests() {
    val delta = answeredRequests.commit(pendingRequests)
    pendingRequests = RequestStorage()
    for (input in inputs.matching(delta))
        applyDemand(input, delta.matchingRows(input))
}

fun CallSiteSubscription.applyDemand(input: Input, rows: RequestRows) {
    val wanted = MarkSetBuilder()
    for ((slot, marks) in rows) {
        if (!requestGeometryMatches(slot, input.current)) continue
        if (input.kind == FLOW && slot.isStatic &&
            isCoveredSameFieldRelay(input, slot)) continue
        wanted.union(marks)
    }
    if (wanted.isEmpty) return
    when (input.kind) {
        FLOW -> offerCallerNeed(input.original, wanted.snapshot())
        TAINT -> if (input.current.base != STATIC)
            sendInitials(key.callee,
                requestedNonstaticActualInitials(input, wanted.snapshot(), calleePolicy(key.callee)))
        ZERO -> Unit
    }
}
~~~

Commit A before outward delivery. Fresh requests visit existing input rows;
fresh inputs visit A. A repeated T from another reader stops before scanning
the p originals. Concrete input never propagates a request backwards.
Concrete initials require actual matching marks; requests do not supply T.
The two projection paths are disjoint: eager static starts run on fact arrival,
while requested nonstatic concrete starts run in applyDemand.
The covered-relay test applies per input/slot before unioning marks. A group
can contain both productive and covered diagonal rows; creating its view for
one productive row must not route requests through the covered rows.
Keep those rows for ordinary fact and summary processing.

Batch destination requests and preserve the ordinary no-exclusions interest
matching. If the caller original is a changed static field, emit the outgoing
need for that original field. Base originals use ordinary nonstatic requests.

## 5. Keep a shared request stream across call statements

Caller ownership alone fixes the one-callsite case, but does not merge demand
between different callsites. Use a shared request view:

~~~kotlin
data class DemandViewKey(val callee: MethodKey, val field: StaticField)

class MethodDemandView {
    val marks = RequestStorage() // merged across reachable need sources
    val subscribers = Set<CallSiteSubscriptionRef>()
}

fun MethodDemandView.onOutgoingNeed(source: NeedKey, offered: MarkSet) {
    if (!prescanReachable(key.callee.method, source.owner.method)) return
    val fresh = marks.unionAndGetDelta(key.field, offered)
    if (!fresh.isEmpty)
        notifySubscribers(fresh)
}

fun subscribeCallSite(view: MethodDemandView, ref: CallSiteSubscriptionRef) {
    // Atomic register + snapshot; callbacks run after releasing the lock.
    val current = view.subscribeAndSnapshot(ref)
    postToCaller(ref, current)
}
~~~

The view is a request stream, not a summary or supplier store. It contains
merged marks and subscriber references. It is shared by all caller statements
targeting the same full callee key/field.

Register a static view only for an eligible abstract FLOW input actually
prepared at that call stage. Static TAINT uses eager projection, not this view.
Use the selected entry-ancestry rule to bypass covered same-field relay inputs
both at registration and per-row demand application. An uncovered synthetic
diagonal needs the ordinary fallback until coverage is proved.
Otherwise transparent calls would regain inherited request state.
The reachability anchor remains the selected callee entry.

Global pattern matching now addresses one demand view per subscribed callee/
field, rather than each input original or callsite independently. On view
creation, replay matching stored outgoing needs. On a new outgoing need,
update matching existing views. Merge their source marks before notifying
callers. Ordinary Base slots use their existing exact callee request stream.

## 6. Summary answers remain ordinary

~~~kotlin
fun CallSiteSubscription.onSummary(delta: SummaryDelta) {
    for (input in inputs.candidates(delta)) {
        val matched = fullOrdinaryMatch(input, delta) ?: continue
        resumeCallerCallStages(key, input.original, matched, delta)
    }
}
~~~

New inputs replay existing summaries; new summaries visit existing inputs.
Do not retain a separate answer copy in the callsite. Keep every applicable
answer and complete ND conjunctions within the actual full call binding.
Register before replay; preserve call-stage flags and caller-layer joins.

## 7. What this fixes, and what it does not prove

~~~text
One callsite, r readers share T, p originals:
  r request admissions + p original visits
  instead of r * p eligible source/original pairs.

s callsites share one callee/field demand view:
  r source offers merge once at the view
  one fresh-T notification per callsite
  each callsite's originals are visited once for T.
~~~

Without the shared view, s callsites still receive r * s source notifications,
even if their local P/A rejects duplicates. Ownership alone cannot erase that
cost. The shared view restores sharing for a common callee.

This still is not a universal work/heap improvement theorem. Different callee
views can have overlapping reachable reader sets; global matching may repeat
across those views. The view and callsite request caches also consume memory.
Prove and measure that cost against ordinary request propagation, including
the old frozen coverage.

Required proofs before implementation:

~~~text
same projected initials and input-presence behavior after moving projection
full-port summary application and ND support unchanged
both arrival orders at the view and the callsite
fresh marks merge before input-original fan-out
covered static relay omission and fixed-CG interest completeness
late sources, multiple targets/stages, recursion, cancellation and completion
~~~

The existing finite P/A model is a useful local oracle. It does not yet prove
this view-to-callsite composition or its runtime ownership. No new savings
theorem or project result is claimed by this refinement.
