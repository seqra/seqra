# Bottom-up requests through method-entry subscriptions

Date: 18 October 2026. Status: design proposal; analyzer implementation unchanged.
Scope: run-1 FORWARD, field limit 1.

Latest measured runtime: f18b1c5bb. Conductor and ThingsBoard both reached
**run-1 / completed none / accepted none**. This proposal has no project timing
result. The [regression analysis](../reports/bidirectional-static-propagation-regression-20261018.md)
is its measurement basis.

## 1. Selected approach

~~~text
method B needs marks {T,U} on its initial C.f
    -> B stores the outgoing request once
    -> global directory registers a reference for pattern C.f

an actual FLOW input for C.f arrives at method M's entry
    -> retain its ordinary full caller binding
    -> publish a compact entry offer
    -> match outgoing requests for C.f
    -> check prescan Reachable(M, B)
    -> deliver the merged request directly to the input's original

actual concrete input arrives
    -> reuse the nearest method's matching summary answers
    -> start only missing initial analysis
    -> deliver future summary deltas to existing consumers
~~~

Request routing is not a summary. A request creates interest, not T.
Actual answers are ordinary summary edges. There is no global replay when
another call binding arrives.

The subscription point is a **method entry**, as selected by the user.
An internal write is not an entry offer. The fixed prescan call graph is
sufficient for the selected interest test after that entry admission.

## 2. Keys and owners

~~~kotlin
data class RequestKey(val owner: MethodKey, val slot: AbstractSlot)
sealed interface AbstractSlot {
    data class Base(val base: NonstaticBase) : AbstractSlot
    data class StaticField(val cls: ClassId, val field: FieldId) : AbstractSlot
}

// One full key retains method, entry and context.
// Request geometry has no E, mx, concrete premise or S-root slot.
data class StaticPattern(val cls: ClassId, val field: FieldId)

// A real prepared input at the selected callee entry.
data class EntryOffer(
    val entry: MethodKey,
    val pattern: StaticPattern,
    val original: RequestKey, // CALLER's abstract original, not current field
)

// Only references/coordinates. No marks or copied input payload.
typealias RequestRef = RequestKey
typealias EntryRef = EntryOfferId
~~~

| State | Owner | Contents and purpose |
| --- | --- | --- |
| InitialFactStore | Method analyzer | Initials already started; duplicate arrival does not restart a body |
| MethodEdgeStore and fact queue | Method analyzer | Full premise, program point and actual fact; enqueue fresh semantic deltas |
| Bound input relations | Caller subscription owner | Full call/stage, caller/callee keys, original, payload, layer and support; used for actual summary application |
| Raw result union and publication buffer | Method analyzer | Existing RunSummaryStore results and temporary fresh publications |
| Published summaries | Method SummaryStorage | Ordinary summary edges; the sole published answer store |
| Pending requests P | Method analyzer | Temporary merged outgoing marks, not yet joined with known eligible entries |
| Answered requests A | Method SummaryStorage | Merged marks whose request service is installed; no inherited relay copies |
| Eligible entry references | Request owner, per outgoing static request | Distinct entry offers already matched and reachable; no marks or answer edges |
| Request pattern directory | Run | Pattern to RequestRefs; no copied mark sets |
| Entry pattern directory | Run | Pattern to compact EntryRefs; no second added-fact store |
| Reachability cache | Run, fixed prescan graph | Method-level control reachability; independent of fields and marks |

Use the same RequestStorage class for P and A. P and A are disjoint.
Here **answered** means that current request processing is installed. It does
not mean that actual T exists, that an answer edge has arrived, or that no
later summary is possible. A summary answer stream stays open until run closure.

## 3. Arrived facts: store first, enqueue only fresh work

The following APIs are pseudocode. They specify ordering, not new Kotlin APIs.

~~~kotlin
fun onPreparedCallInput(port: FullCallPort, offered: Input) {
    // Store the existing ordinary input relation, including complete support.
    val fresh = boundInputs.unionAndGetDelta(port, offered)
    if (fresh.isEmpty) return

    // Register the consumer before replay; late summaries must not be lost.
    subscribeAndReplaySummaries(port, fresh)
    postToCallee(LinkIn(port, fresh))

    // Only post-binding/post-guard input actually delivered to this entry.
    publishEntryOffers(port, fresh)
}

fun onLinkIn(port: FullCallPort, actual: Input) {
    // No callee-side supplier history or added-fact store.
    for (initial in run1Abstraction.projectActualInput(actual))
        offerInitial(initial)
    // Existing FLOW/static activation still reaches actual descendant entries.
    // A read-free method can use its packed lane; do not drop calls/effects.
}

fun offerInitial(initial: Initial) {
    if (!initials.add(initial)) return
    for (start in methodStartPoints)
        offerFact(initial, start, startFact(initial))
}

fun offerFact(original: FullPremise, point: ProgramPoint, actual: Fact) {
    val normalized = selectedRun1Policy(original, actual)
    val delta = methodEdges.unionAndGetDelta(original, point, normalized)
    if (!delta.isEmpty) factQueue.merge(original, point, delta)
}
~~~

Preserve the selected initial grammar, actual-mark admission and DEMAND policy.
No S-root or ANY initial is introduced. Field E stays empty in run-1.
Requests ignore E and FLOW mx. Concrete-origin facts do not route requests.

Merge fact marks only where the full premise, point, payload header and support
permit that merge. T and U in different original premises do not become one ND
premise. A request mark set has no such concrete-proof meaning.

## 4. Processed results: separate all output channels

~~~kotlin
fun processFact(item: FactDelta) {
    val out = ordinaryTransfer(item) // includes full ND and source/sink rules

    for (result in out.facts)
        offerFact(result.original, result.nextPoint, result.fact)
    for (request in out.markRequests)
        offerRequest(request.originalKey, request.marks)
    for (summary in out.summaries)
        queueSummary(summary)
    retainSemanticObservations(out.observations)
}

fun queueSummary(edge: SummaryEdge) {
    val fresh = rawResults.unionAndGetDelta(edge)
    publicationBuffer.merge(eligiblePublications(fresh))
}

fun flushSummaries() {
    summaryStorage.publish(publicationBuffer.takeAndReset())
    // Existing subscriptions apply only publication deltas.
}
~~~

Keep requests and observations even when an operation kills all value outputs.
An empty method queue is a batching boundary, not proof that the method can
never gain another result. The universal S-to-S DEMAND default remains an
ordinary stored summary, not an indication that every request is complete.

## 5. Publish entry offers, not every static fact

~~~kotlin
fun publishEntryOffers(port: FullCallPort, fresh: InputDelta) {
    if (!fresh.isFlow || !fresh.original.isAbstractSingle) return
    if (fresh.current.base != STATIC) return

    for (field in legalStaticFieldsSuppliedBy(fresh.current)) {
        val destination = RequestKey(port.caller, fresh.original.slot)

        // Omission requires the entry-ancestry invariant stated below.
        if (isCoveredSameFieldRelay(port, destination, field)) continue

        onNewEntryOffer(EntryOffer(
            entry = port.callee,
            pattern = field.asPattern(),
            original = destination,
        ))
    }
}
~~~

An offer describes an actual entry input and its caller origin. Its key ignores
E/mx and call-header distinctions irrelevant to interest. Distinct physical
ports with the same entry, field and original share one offer. Their complete
bindings remain separate in boundInputs for concrete truth and ND matching.

Do not publish concrete inputs, ZERO or internal program-point facts as
request producers. Omit covered same-field relays under the rule below.
An actual broad static FLOW input can supply several legal fields; project its
geometry, not concrete marks.
Do not enumerate the entire project field inventory for an exact field input.

The required entry-ancestry invariant is:

~~~text
Every actual abstract static entry basis is backed by:
  (a) a retained nonidentity entry offer at this entry or an ancestor entry, or
  (b) an actual concrete static supply, which needs no backwards FLOW request.

A same-field relay preserves that coverage.
~~~

The required coverage proof has this induction sketch: a nonstatic/changed
static origin creates (a); a diagonal relay inherits its caller's coverage;
actual TAINT creates (b). Unseeded recursive cycles cannot create an actual
entry basis. Retain covering offers for the full run. A synthetic abstract
entry without such coverage is outside this omission rule and must keep an
offer until its adapter has a coverage proof.

If a retained offer starts at entry M and M reaches a skipped relay entry H,
then Reachable(M,H) and Reachable(H,B) give Reachable(M,B). Thus the original
offer answers the later request without a request row at H.
A field change creates a new producer: original C.g/current C.f publishes C.f
and maps the request back to C.g.

Do not make request creation depend on an existing global subscription.
Keep current actual FLOW entry activation and local evaluation of source/sink
conditions, cleaners and every immediate-interest operation. A late B actor or
late local need finds retained offers when its outgoing request registers.
This proposal changes request transport; replacing that forward activation
with a new synthetic-entry procedure is outside scope.

## 6. Global subscription: pattern first, fixed reachability second

~~~kotlin
fun registerOutgoingStaticRequest(q: RequestRef) {
    // Under the directory lock: install q, snapshot existing pattern groups.
    // Release the lock before checking reachability or posting owner messages.
    val groups = directory.registerRequestAndSnapshotEntries(q)
    for ((entryScc, refs) in groups) {
        if (!prescanReachable(entryScc, scc(q.owner.method))) continue
        for (ref in refs) postToOwner(q.owner, EligibleEntry(q, ref))
    }
}

fun onNewEntryOffer(entry: EntryOffer) {
    // Dual atomic operation; null means this logical offer is already stored.
    val (ref, groups) = directory.registerEntryAndSnapshotRequests(entry) ?: return
    for ((requestScc, requests) in groups) {
        if (!prescanReachable(scc(entry.entry.method), requestScc)) continue
        for (q in requests) postToOwner(q.owner, EligibleEntry(q, ref))
    }
}
~~~

The directory has two views: pattern -> entry SCC -> EntryRefs, and pattern ->
request-owner SCC -> RequestRefs. Select the exact field first, then test one
reachability relation per SCC group before producing per-reference work.
Store no mark sets in either view. Both register-and-snapshot operations use
the same directory lock; callbacks run after release. Either arrival order
finds the pair. The request owner deduplicates any repeated EntryRef notification.
The global directory never mutates P or A.

Reachability is reflexive and handles recursive SCCs. Build SCCs once after
prescan; memoize searches on the fixed SCC DAG as needed. Do not build a
field-by-mark transitive closure. A negative reachability result stays negative
in this run; there is no graphChanged notification or full-source restart.

This deliberately uses **method-level interest reachability**, as selected.
It can offer extra requests across control paths or contexts compared with
the old actual-FLOW route. It cannot deliver T or satisfy a source/sink check.
Full MethodKeys and original support still govern actual fact and summary
application. No equality claim with the old dynamic request set is made.

## 7. Pending/answered joins: two event handlers

All operations below run on the outgoing request's owner.

~~~kotlin
var temporary = RequestStorage()                  // P, actor-owned
val answered = summaryStorage.outgoingRequests    // A, merged authoritative rows
val registeredPatterns = Set<AbstractSlot>()
val eligibleEntries = Map<RequestKey, Set<EntryRef>>()

fun offerRequest(q: RequestKey, offered: MarkSet) {
    val fresh = offered - answered.borrow(q.slot) - temporary.borrow(q.slot)
    if (fresh.isEmpty) return
    temporary.union(q.slot, fresh)

    if (q.slot.isStatic && registeredPatterns.add(q.slot))
        registerOutgoingStaticRequest(q) // once, no marks copied globally
    scheduleOwnerWorkOnce()
}

fun drainRequests() {
    // commit returns only fresh rows; publish A before outward delivery.
    val freshRows = summaryStorage.commit(temporary)
    temporary = RequestStorage()

    for ((slot, marks) in freshRows) {
        val q = RequestKey(method, slot)
        if (slot.isStatic)
            for (ref in eligibleEntries[q])
                deliverRequestDirectly(ref, marks)
        else
            ordinaryBaseRequests.joinExistingInputs(q, marks)
    }
}

fun onEligibleEntry(q: RequestKey, ref: EntryRef) {
    if (!eligibleEntries[q].add(ref)) return

    // Pending marks wait for drainRequests(), which will see this entry.
    val marks = answered.borrow(q.slot)
    if (!marks.isEmpty) deliverRequestDirectly(ref, marks)
}

fun deliverRequestDirectly(ref: EntryRef, marks: MarkSet) {
    val destination = entryDirectory[ref].original
    postMergedRequest(destination.owner, destination.slot, marks)
    // Base original -> ordinary nonstatic request processing.
    // Changed static original -> its owner creates one outgoing subscription.
}
~~~

The proposed commit unions P into A and returns its fresh rows. It must not
also broadcast them: the explicit static/Base branches above own notification.
If the existing Base notification API is retained instead, remove the explicit
Base join above. There must be one Base notification path, not both.
Direct entry matching is the sole transport of static outgoing requests.

~~~text
fresh marks × existing eligible entries
    plus
answered marks × fresh eligible entries
    equals all required pairs, once
~~~

Publish A before queuing child messages. Owner serialization then prevents
both handlers from delivering the same pair. No runtime set of all
(request, mark, entry) deliveries is required.

For ordinary Base requests, retain the same two-sided rule: fresh request
marks join old bound inputs; fresh bound inputs join A. A static caller
original reenters offerRequest rather than a per-relay static request store.
Incoming requests check both P and A before doing downstream work.

## 8. Reuse actual summary answers at the nearest boundary

~~~kotlin
fun subscribeAndReplaySummaries(port: FullCallPort, fresh: InputDelta) {
    // This is the nearest answer owner: the actual callee at this call stage.
    val answers = summaryStorage(port.callee)
    val oldEdges = answers.registerConsumerAndSnapshot(port, fresh)
    for (edge in oldEdges) {
        val matched = ordinaryFullMatch(fresh, edge.premise) ?: continue
        resumeOrdinaryCallStages(port, matched, edge)
    }
    // Future notifications use onSummaryDelta below, one notification path.
}

fun onSummaryDelta(callee: MethodKey, delta: SummaryDelta) {
    for (input in boundInputs.matching(callee, delta))
        applyOrdinarySummary(input.fullPort, input, delta)
}
~~~

“Closest answer” means the nearest matching method summary on the source's
actual call path. Look in that callee's SummaryStorage first; do not launch
a whole-program nearest-answer search. Returning results are then composed
bottom-up and cached at the next method boundary by the ordinary engine.

Replay **all** applicable edges. One answer is not a winner that suppresses
other answers. An existing answer can be used immediately; the consumer
remains subscribed for later answers. Missing answers remain dormant without
holding a work token. Repeated requests do not restart initial analysis.

The query includes the full actual premise and EXACT/STAR distinction.
Knowing that request T was processed does not authorize applying a T summary
to FLOW, to another call's support, or to an unmatched ND premise.
The request-table proof establishes exactly-once mark/eligible-entry joins.
It does not establish exactly-once physical summary callbacks; the ordinary
summary protocol and semantic fact/result deduplication remain in force.

## 9. Traces

~~~java
A(p) { C.f = p; H(); }
H()  { B(); }                         // no local related read
B()  { check ContainsMark(C.f,T); }   // may loop or throw afterwards
~~~

~~~text
1. Actual post-binding input at H entry: A's p/* -> C.f/* [D].
   Entry offer: (entry H, field C.f, original A/p).
2. B's reached check emits outgoing B/C.f/{T}.
3. Global match C.f; prescan H ->* B is true.
4. Deliver Base(A,p)/{T} directly. H stores no inherited request.
5. If an actual p/T supplier exists, ordinary Base handling emits its initial.
6. Actual C.f/T reaches call inputs and uses ordinary cached summaries.
   If no actual p/T exists, no concrete hit is produced.
~~~

~~~java
A(p) { B(); C.f = p; } // the late write is not an entry offer for the earlier B()
~~~

~~~text
No input derived from that write arrived at B's entry.
Do not globally index the internal write and apply A -> B to it.
If that value later reaches a real entry M, it is indexed there;
the selected check is then M ->* requestOwner.
~~~

~~~text
Late entry, old request:        new EntryRef × A -> direct delivery.
Late marks, old entry:          drain P × known EntryRefs -> direct delivery.
Repeated request/entry:         no fresh pair.
C.g -> C.f entry input:         request C.f maps back to original C.g.
No normal return:               entry match and requests still work.
Late concrete source:          existing summaries replay; only new initial work runs.
~~~

## 10. Batching, completion and lifetime

~~~kotlin
fun finishOwnerWorkTurn() {
    flushSummaries()
    drainRequests()
    flushMergedDestinationMessages()
    // Secure child reservations before releasing/requeuing this turn.
}
~~~

An empty method queue remains a useful batching boundary. Also service
outgoing P when an owner yields its normal work quantum, so a continuously
busy method does not keep all requests pending. This needs no arbitrary
pending-memory cap. Queue one owner wake, not one event per request mark.

Persistent subscriptions and summary waiters are dormant metadata, not
unfinished work. A complete run barrier requires empty queues, P, publication
buffers and outgoing deliveries, plus joined active handlers. Keep P/A and
subscriptions for the whole run; clear them after the run joins.

## 11. Change, cost and remaining proof obligations

| Current | Proposed | Why it should help |
| --- | --- | --- |
| Fresh bindings can restart all static sources | Fresh entry offers join matching subscriptions only | Removes unrelated global replay |
| Wave-local visited sets are rebuilt | One P/A row per outgoing request; one eligible-entry reference | Keeps duplicate knowledge across events |
| Marks travel through intermediate static relays | One entry reachability test, direct original request | Avoids relay mark payloads and queued waves |
| Physical ports can repeat one logical need edge | Compact entry-offer key, full ports only in the ordinary input relation | Keeps request dedup separate from truth |
| Answer/source races need replay | Register then replay existing summaries, then consume deltas | Reuses completed work for late sources |

Let Q be outgoing source keys, E distinct entry offers, L eligible source/entry
pairs, C the tested pattern/SCC groups, and J their actual mark memberships
joined. Request routing stores
O(Q mark memberships + E + L), plus a mark-independent call-graph cache.
It performs J eligible mark-entry joins. Pattern scans, failed reachability
tests/cache misses within C, duplicate notifications, mark unions and message
service are additional costs; this is not a total O(J) execution-time claim.
Grouping avoids constructing all failed per-reference pairs, but a popular
field can still have many method groups even when J is zero.
It does not retain wave histories or per-relay
mark rows. L can still be large; references are not automatically cheap.
Measure it before claiming a project memory gain.

The selected prescan gate can widen interest. There is no unconditional
whole-project “strictly less work” claim: extra interest and a dense L can
offset the removed replay. Measure both against the completed ab6 run-1
baseline as well as f18b.

The [comparison with ordinary propagation](bidirectional-request-savings-vs-ordinary-20261018.md)
also identifies a loss of sharing even without widened interest:

~~~text
r > 0 readers need the same T; p originals supply a shared entry H.
ordinary: merge at H, then fan out       -> r + p mark-offer attempts
proposed: one join for each source/entry -> r * p attempts and eligible refs
~~~

For r = p = 4 this is 8 versus 16. Source-local P/A does not eliminate
duplicates between different readers before original fan-out. An uncovered
transparent chain can save h * k relay memberships, but a canonical static
check may already be covered by the old frozen plan. Thus the earlier
64-versus-2,080 full-replay control is not a proof of improvement over ordinary
incremental analysis. The linked note gives the scope, proof model and a
possible entry-group merge refinement; that refinement is not selected here.

The [constructive table model](../../spec/lean/ApSpec/IncrementalRequestTable.lean)
proves P/A separation, duplicate admission, both fresh pair frontiers, arbitrary
arrival-order coverage and no repeated pair delivery. Its
[focused audit](../../spec/lean/incremental-request-table-audit.lean) passes
192 declarations with zero unexpected axioms and 11 executable controls.
A 64-candidate control gives 64 pair joins versus 2,080 full-replay joins.
These are finite protocol counts, not project timing.

Before implementation, complete the remaining refinement proofs under the
[proof guide](../../spec/proof-guide.md):

1. Each entry offer has an actual post-bound FLOW witness; geometric projection
   and mapping to the original are correct. Concrete/ND inputs cannot invent a route.
2. Same-field relay omission covers every ordinary bootstrap path; cross-field
   paths split at real producer boundaries.
3. The fixed prescan entry reachability test conservatively covers required
   routes. Define the widened-interest reference; do not equate it to exact FLOW reachability.
4. Global subscribe/replay and owner P/A joins implement the finite model,
   including duplicate notifications and concurrent registration.
5. Late source and late summary joins preserve full support, all applicable
   effects, observations, tails, layers and ND conjunctions.
6. Request delivery cannot fabricate T or a hit. Completion and cancellation
   retain/release every active reservation correctly.

The Lean table model assumes reachability-qualified candidates; it does not
prove these JVM/AP/call-graph bridges. Complete those refinements before
implementing the analyzer change.

Required focused controls: both arrival orders; repeated marks; 64 parallel
ports with one offer; disconnected entries; recursive call SCCs; changed fields;
nonreturning checks; an internal write after a call; late EXACT/STAR answers;
split-call ND support; request-only owners; and P/publication handoff.

Required counters: P/A memberships, fresh/repeated offers, pattern candidates,
reachability tests/cache misses, eligible pair references, direct deliveries,
destination P/A rejections, local fact deltas and summary applications.
Report each project's latest reached/completed/accepted run iteration.
