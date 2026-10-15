# Merged run-1 mark requests: implementation proposal

Date: 15 October 2026. Status: proposed implementation; no analyzer code changed.

This proposal implements [F78](../../spec/run1-requests.md). The
[proof dossier](../../spec/run1-request-proofs.md) states the semantic arguments,
Lean results and their scope. The [original proposal](bidirectional-run1-mark-request-redesign-20261015.md)
records the measurements and selected changes. F78 replaces its proof-obligation
list with a precise contract and a required static-root safety clarification.

The measured analyzer is `27e22bb1ed871651143538783473cb0d4fcbcf3e`.
No new analyzer run was made for these specification and proof changes.

| Project | Latest reached | Last completed engine | Accepted report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 331.657 |
| Conductor | run-2, backward; OOM | run-1, forward | run-1 | 60.210 |
| ThingsBoard | run-1, forward; OOM | None | None | 279.104; incomplete |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 | 36.102 |

The target is a completed run-1 engine within 60 seconds. Prescan and the report
barrier are measured separately. The [performance report](../reports/bidirectional-added-facts-removal-performance-20261015.md)
defines the fixed workloads and measurement limits. These proofs establish no
speedup or time bound.

## 1. Scope and proposed interfaces

The snippets below are Kotlin-like design code, not a patch. The
[F78 spec](../../spec/run1-requests.md) and its
[proofs](../../spec/run1-request-proofs.md) define their contracts. Keep existing
normal AP/summary/ND semantics except for the explicit F78 root-reader correction.

```kotlin
sealed interface Run1Slot {
    data class Base(val base: NonStaticBase) : Run1Slot
    data class StaticField(val cls: ClassAccessor, val field: StaticFieldAccessor) : Run1Slot
}

// Full MethodKey is the owner; it includes entry and context.
typealias RequestRows = ImmutableMap<Run1Slot, ImmutableMarkSet>
data class ChangedRequests(val rows: RequestRows)

// A borrowed selection, with no retained merged array/map.
data class RequestRowView(
    val precomputed: ImmutableMarkSet,
    val dynamic: ImmutableMarkSet,
) : MarkSelection { // precomputed and dynamic are disjoint
    override fun contains(mark: Mark) = mark in precomputed || mark in dynamic
    override fun forEach(visit: (Mark) -> Unit) {
        precomputed.forEach(visit)
        dynamic.forEach(visit)
    }
}
```

Requests carry no full pattern, tail, E, mark exclusion, rule role, caller or call
site. Those ordinary rule and caller identities retain their current owners.

## 2. One shared base; the same storage class for admitted and temporary requests

```kotlin
class RequestStorage(
    private val sharedBase: RequestRows = RequestRows.EMPTY,
) {
    // Only residual memberships; never a copy of sharedBase.
    private val additions = mutableMapOf<Run1Slot, ImmutableMarkSet>()

    fun borrow(slot: Run1Slot): RequestRowView = RequestRowView(
        precomputed = sharedBase[slot].orEmpty(),
        dynamic = additions[slot].orEmpty(),
    )

    fun addAll(slot: Run1Slot, offered: MarkSelection): ImmutableMarkSet {
        val fresh = offered.minus(borrow(slot))
        if (fresh.isEmpty()) return ImmutableMarkSet.EMPTY
        additions[slot] = additions[slot].orEmpty().union(fresh)
        return fresh
    }

    // Only the owner flusher can read temporary rows; producers cannot query P.
    fun pendingRowsForCommit(): Sequence<RequestRow> {
        check(sharedBase.isEmpty())
        return additions.asRequestRows()
    }

    fun isEmptyTemporary(): Boolean {
        check(sharedBase.isEmpty())
        return additions.isEmpty()
    }
}
```

```text
Frozen method/SCC plan       B = one immutable precomputed request map
SummaryStorage(M).requests  R = reference to B + disjoint dynamic additions D
MethodActor(M).temporary    P = empty base + pending additions
Effective admitted membership: R(slot) = B(slot) union D(slot)
```

R and P use the same class. SummaryStorage synchronizes R. The method actor owns
P. Different full MethodKeys can reference the same B; their D/P remain separate.
Neither the abstraction nor subscribers retain a cumulative copy or materialized
B∪D view. Normalizing and freezing B happens once per shared plan.

## 3. Entire workflow for a precomputed request

### 3.1 Prescan, close and freeze

```kotlin
fun buildStaticPlans(joinedPrescan: PrescanResult): Map<Method, StaticPlan> {
    val graph = joinedPrescan.resolvedCallGraph // already complete; no new resolution
    val local = collectEffectiveForms(
        entry = true, statements = true, calls = true, exits = true,
        nativeModels = true, generatedRewriters = true,
        sourceAndSinkNnfLeaves = true, concreteInputGates = true,
        selectedCleaners = true, excludedTargetMarks = true,
        structuralReadWriteAndGenerationTargets = true,
    ) // FALSE contributes nothing; no NNF -> DNF expansion

    val closed = existingSccUnionClosure(graph, local)
    return closed.sharedPlans { needs ->
        val frozenSlots = needs.structuralFieldSlots union needs.exactFieldMarkSlots
        val rows = buildImmutableRows {
            for (slot in frozenSlots) {
                addAll(slot, needs.fieldMarks[slot])
                addAll(slot, needs.classWideMarks[slot.cls])
                addAll(slot, needs.allStaticMarks)
            }
        }
        StaticPlan(
            precomputedRequests = rows, // store these resolved rows once
            wideNeeds = needs.sharedWideDescriptors,
            classPointNeeds = needs.sharedClassPointDescriptors,
            structuralSlots = frozenSlots,
            footprint = needs.semanticFootprint,
        )
    }
}

// Kept separately for later native/reversed readers; this holds no request map.
val stableStaticFootprints = frozenRun1Plans.mapValues { it.value.footprint }

fun afterRun1Joined() {
    releaseRun1ActorsAndSummaryStores()
    frozenRun1Plans = null // releases B and wide/class mark interests after all work joined
}
```

The field universe and JVM layout come from canonical prescan IR/rules/models.
Only already resolved slots enter B. A wide descriptor alone does not create a
field×mark product. Collector scratch data and old query/role request rows are
released; wide/class/footprint descriptors are metadata, not another resolved
request store.

### 3.2 Bind the shared map before the first subscriber or input

```kotlin
class SummaryStorage(val method: MethodKey, val plan: StaticPlan) {
    private val lock = Any()
    private val requests = RequestStorage(sharedBase = plan.precomputedRequests)
    // Empty D. The constructor borrows B; it does not iterate/copy B.

    fun borrowRequest(slot: Run1Slot): RequestRowView = synchronized(lock) {
        requests.borrow(slot) // captures immutable row values
    }
}

fun summaryStorage(method: MethodKey): SummaryStorage = registry.computeIfAbsent(method) {
    SummaryStorage(method, frozenPlans[method.method])
} // published only after its B reference is installed

class MethodActor(val method: MethodKey) {
    val summaries = summaryStorage(method)
    var temporary = RequestStorage() // empty base, empty additions
}
```

```text
B is visible before registration, input admission and LinkIn.
There is no initial MarkRequestsIn/ChangedRequests batch for B.
There is no B -> P -> D copy and no initial notification wave.
```

### 3.3 New inputs read the full admitted relation

```kotlin
fun submitCallInput(part: Subscription) {
    val calleeStore = summaryStorage(part.callee) // binds B first
    val inputs = callerDirectories.getOrPut(part.callee) {
        calleeStore.registerSubscriber(callerRunner) // once per caller runner/callee
        CallerInputDirectory()
    }
    inputs.admitOriginalHeaderAndFacts(part)
    replayOrdinarySummaries(part)
    if (part.added is FLOW) inspectNewFlowInput(part, calleeStore)
    sendCounted(LinkIn(part)) // TAINT arm reads R at the callee
}

fun onLinkIn(part: Subscription) {
    emitPermittedAbstractInitials(part.added)
    emitDirectClassComponents(part.added)
    activateRelevantWideNeeds(part.added)
    if (part.added is TAINT) {
        for (row in summaries.requestsMatching(part.added)) {
            emitActualConcreteAnswers(row.slot, row.marks, part.added)
        }
    }
    // No supplier history, speculative answer scan or answer memo.
}
```

Precomputed requests have been visible since storage construction, so every new
input sees them. Fresh dynamic requests replay older retained caller inputs through
the ordinary notification channel. Both arms are required; no extra fresh-TAINT
request replay is added in the caller.

### 3.4 Unchanged and changed origins

```kotlin
fun collectFlowEffects(part: Subscription, rows: Sequence<RequestedRow>, out: Effects) {
    validateExistingPremiseFactInvariant(part.ref.premise, part.added) // K6
    if (part.added !is FLOW) return // concrete answers have their own branch
    val original = part.ref.premise.asNormalAbstractRun1SlotOrNull() ?: return

    for (row in rows) {
        if (!slotMatches(row.slot, part.added)) continue // ignores request E/mx

        // Per queried slot, never per whole input or whole plan.
        val baseOffer = if (original == row.slot && original is Run1Slot.StaticField) {
            ImmutableMarkSet.EMPTY // closed B_callee(slot) is already in B_caller(slot)
        } else {
            row.marks.precomputed // changed origin must still receive these marks
        }
        val offered = baseOffer.union(row.marks.dynamic)
        if (offered.isEmpty()) continue
        val targetR = summaryStorage(part.ref.caller).borrowRequest(original)
        val useful = offered.minus(targetR)
        if (useful.isNotEmpty()) out.requests.merge(part.ref.caller, original, useful)
    }
}
```

```text
N checks C.f/T; M calls N:
    origin C.f -> input C.f:  skip inherited precomputed f/T row as a whole
    origin C.g -> input C.f:  offer g/T; f/T does not discharge it
    origin arg -> input C.f:  offer arg/T

One f-origin FLOW carrying both f and g:
    queried f/T: skip unchanged inherited f/T
    queried g/U: offer f/U; do not skip the whole input or the entire B map
```

The no-send proof applies only to resolved frozen B rows and an unchanged original
slot. A runtime wide activation or future D addition is not retroactively B.

### 3.5 Deferred wide activation and all other producers

```kotlin
fun MethodActor.collectRequests(slot: Run1Slot, offered: MarkSelection) {
    val fresh = offered.minus(summaries.borrowRequest(slot)) // B union D; never read P
    if (fresh.isEmpty()) return
    temporary.addAll(slot, fresh) // repeated pending contributions merge
    requestWork()                // existing counted Work, also for request-only actors
}

fun MethodActor.activateRelevantWideNeeds(input: Facts) {
    for (slot in plan.relevantFieldSlots(input)) {
        collectRequests(slot, plan.wideNeeds.marksFor(slot))
    }
}

fun onMarkRequestsIn(event: MarkRequestsIn) {
    actor(event.method).collectRequests(event.slot, event.marks)
} // re-filter a queued/stale sender proposal against current R
```

A newly resolved wide request enters P once as a merged pending membership. Its
fresh admission must notify callers so earlier inputs are handled. Repeated
activation after admission produces no new P membership. No extra activation cache
or per-input request matrix is needed.

## 4. Commit-and-replace and method scheduling

```kotlin
fun SummaryStorage.commit(temporary: RequestStorage) {
    val changed = synchronized(lock) {
        immutableRows {
            for ((slot, marks) in temporary.pendingRowsForCommit()) {
                val fresh = requests.addAll(slot, marks) // subtracts B union D
                if (fresh.isNotEmpty()) put(slot, fresh)
            }
        }
    }
    if (changed.isEmpty()) return
    for (subscriber in subscribers) {
        subscriber.sendCounted(ChangedRequests(changed)) // queued, including local delivery
    }
} // never retains temporary or invokes a solver/actor callback inline

fun MethodActor.onFactQueueEmpty() {
    flushNormalSummaries()
    summaries.commit(temporary)
    temporary = RequestStorage()
    // Keep Work reserved through commit, replacement and secured child sends.
    if (facts.isNotEmpty() || publications.isNotEmpty() || !temporary.isEmptyTemporary()) {
        continueExistingWork()
    } else {
        completeExistingWork()
    }
}
```

```text
fresh = P - (B union D)
D'    = D union fresh
B'    = B
P'    = empty new RequestStorage
```

A quantum yield keeps its existing scheduling behavior but does not publish P.
Arriving events wait until the owner completes replacement. Cancellation/rejected
required delivery follows existing failure handling and cannot certify COMPLETE.
There is no unit barrier, hard pending limit, rescan fallback or new scheduler.

## 5. Pin every run-1 initial at the admission boundary

The exhaustive table is [F78 §1.1](../../spec/run1-requests.md#11-closed-run-1-initial-grammar).
The selected first config is forward run-1, limit 1, empty hand-off seeds/records.
Incoming geometry already satisfies the existing selected cut/binding routes;
there is no new emitter cut.

```kotlin
fun validateRun1Initial(i: InitialAp) {
    require(run.number == 1 && run.forward && run.limit == 1)
    require(i.exclusion.isEmpty && !i.base.isCleanerTemporary)
    require(i.tail == EXACT || i.tail == STAR) // ANY/ANY_TAINT are never run-1 initials

    if (i.base.isZero) {
        require(i == canonicalZero) // exact root, canonical zero mark
        return
    }
    when (val mark = i.mark) {
        is AbstractMark -> {
            require(mark.excluded.isEmpty() && i.tail == STAR)
            when (val position = parseInitialPosition(i)) {
                is NonStaticRoot -> Unit
                is StaticRoot -> Unit // special rule-free summary seed
                is StaticField -> Unit
                else -> error("Invalid abstract run-1 initial")
            }
        }
        is ConcreteMark -> when (val position = parseInitialPosition(i)) {
            is NonStaticRoot, is StaticField, is ClassPoint -> Unit
            else -> error("Invalid concrete run-1 initial") // includes empty S root
        }
    }
    // No T!=0 guard on nonzero bases; admitted concrete mark IDs can include 0.
    // No ANY/ANY_TAINT, abstract class, deep static or nonstatic initial path.
}

fun admitRun1Initial(i: InitialAp) {
    validateRun1Initial(i)
    if (initialFactStore.add(i)) startAtMethodEntry(i, initialFacts(i))
}
```

```text
Ten cases and their first conclusion:
    Zero                                   -> REACH, NORMAL
    abstract b/root, S/root, S/C.f          -> FLOW/STAR, NORMAL
    concrete b/root/EXACT                   -> exact TAINT, NORMAL
    concrete b/root/STAR                    -> ANY TAINT, DEMAND
    concrete S/C.f/EXACT                    -> exact TAINT, NORMAL
    concrete S/C.f/STAR                     -> ANY TAINT, DEMAND
    concrete S/C/EXACT                      -> exact TAINT, NORMAL
    concrete S/C/STAR                       -> ANY TAINT, DEMAND
```

```kotlin
// All InitialFactStore admissions end in these constructors:
onRootStart()                         { admitRun1Initial(canonicalZero) }
onIncomingReach()                     { admitRun1Initial(canonicalZero) }
onSuppliedNormalSlot(slot)            { admitRun1Initial(slot.abstractInitial()) }
onStaticInput()                       { admitRun1Initial(specialStaticRoot) }
onRequestedTaint(slot, mark, supplier) {
    require(mark in summaries.borrowRequest(slot))
    projectConcrete(slot, mark, supplier).forEach(::admitRun1Initial)
}
onExactClassInput(supplier)           { directClass(supplier)?.let(::admitRun1Initial) }
onBroadClassNeed(need, supplier)      { projectClass(need, supplier)?.let(::admitRun1Initial) }

// Source/end-GEN and sink/source seeds create edge work under their actual premise.
// They do not admit arbitrary new run-1 method initials. ND members are not a
// eleventh shape. Later-run restricted emission/record readers have their own grammar.
```

Validate static layout as class→static-field→ordinary suffix, with one counted
accessor at run-1. Positivity alone does not prove the bound. Current generic
factories, historical static ANY/abstract-class starts and custom first limits above 1 do
not yet conform to this proposed boundary.

## 6. Request projection and exclusion creators

```kotlin
fun TAINT.concreteLeaves(): Sequence<ConcreteBranch> = sequence {
    for (node in positions()) {
        if (node.exactMarks.isNotEmpty()) yield(exactBranch(node.path, node.exactMarks))
        if (node.anyMarks.isNotEmpty()) yield(anyOrMustBranch(node.path, node.anyMarks))
    }
    // Keep branch-specific mark sets. Do not union exactMarks with anyMarks.
}

fun projectConcrete(slot: Run1Slot, mark: Mark, supplier: Facts): Sequence<InitialAp> = sequence {
    if (supplier !is TAINT) return@sequence
    for (leaf in supplier.concreteLeaves()) {
        if (mark !in leaf.actualMarks || !slotMatches(slot, leaf)) continue
        val initial = when (slot) {
            is Run1Slot.Base -> if (leaf.isExactBaseRoot) {
                exactInitial(slot.base, emptyPath, mark)
            } else {
                chainInitial(slot.base, emptyPath, mark)
            }
            is Run1Slot.StaticField -> when {
                leaf.isExactAt(slot) -> exactInitial(STATIC, slot.path, mark)
                leaf.isAnyOrMustAt(slot) -> chainInitial(STATIC, slot.path, mark)
                leaf.isBroadAncestorOf(slot) -> chainInitial(STATIC, slot.path, mark)
                else -> null
            }
        }
        if (initial != null) yield(initial)
    } // preserve both exact and chain alternatives; InitialFactStore deduplicates
    // Emitted E is empty; ordinary caller matching retains original E/layer.
}

// Incoming and starting facts can have ANY; InitialAp never does in run-1.
// The separate direct class projector also uses STAR for every nonexact answer.
// C.f is a selected supplied field slot. Inspect its initials here;
// the separate special S-root seed remains present.
checkFieldSlotInitials(
    input = anyTaint(STATIC, path(C, f), T),
    requested = emptyMarks(),
    expectedInitials = setOf(abstractInitial(STATIC, path(C, f))),
)
checkFieldSlotInitials(
    input = anyTaint(STATIC, path(C, f), T),
    requested = marksOf(T),
    expectedInitials = setOf(
        abstractInitial(STATIC, path(C, f)),
        chainInitial(STATIC, path(C, f), T),
    ),
)

fun introduceExcludedMarks(origin: PremiseKey, input: FLOW, excluded: MarkSet) {
    validateExistingPremiseFactInvariant(origin, input) // K6 before eligibility guards
    origin.asNormalAbstractRun1SlotOrNull()?.let { slot ->
        actor.collectRequests(slot, excluded) // before publishing excluded output
    }
    publishOrdinaryExcludedOutput(input, excluded)
}
```

The request matcher ignores field E and FLOW mark exclusions. Ordinary rule/fact
transfers retain their existing exclusion tests. Every generic excluded target,
not just a named cleaner, uses the creator side effect. Legal hand-built edges
stay legal. Concrete-origin inputs produce no upstream interest and still answer
callee requests from actual concrete marks.

The chain is the selected initial representation. It is not an ANY initial
renamed after admission. Both start with an ANY/DEMAND concrete fact, but their
stored premise matching differs; tests must cover summary application and later
record views under the new STAR premise. Equal starting facts alone do not prove
equivalence to the historical static ANY-answer implementation.

## 7. Late requests without an answer memo

```kotlin
fun onChangedRequests(callee: MethodKey, delta: ChangedRequests) {
    val effects = Effects() // transient packed answers and per-destination mark sets
    callerInputs(callee).visitMatchingBatch(delta.rows) { input ->
        validateExistingPremiseFactInvariant(input.ref.premise, input.added)
        when (input.added) {
            is TAINT -> collectActualConcreteAnswers(delta, input, effects)
            is FLOW -> collectFlowEffects(input, dynamicOnlyRows(delta), effects)
            is REACH -> Unit
        }
    }
    effects.sendCountedAfterVisit() // no live-directory mutation or inline callbacks
}
```

```text
register subscriber < admit original caller input < deliver LinkIn
commit R < notify/replay fresh request

A = input admission; E = emitter's R read; Q = request commit; V = late replay
Q <= E: emitter sees the request
E < Q: A < E < Q < V, so replay sees the retained input
```

Keep R active after an answer. Preserve original CallerRef, group header, input
layer and complete ND premise scope in caller storage. No callee supplier relation,
request-by-input seen cache or subscriber copy of R is introduced.

## 8. Static-root ownership and return-gated frame

```kotlin
fun readStaticResult(input: Facts, callee: MethodKey, evidence: ReturnEvidence): Effects {
    val plan = stableStaticFootprints[callee.method]
    val parts = projectOriginalStaticInput(input, plan) // transient; preserves E/marks/layer
    val out = Effects()
    for (part in parts.ownedFields) out += readNormalFieldSummaries(part, callee)
    for (part in parts.classPoints) out += readDirectClassSummaries(part, callee)
    if (evidence.provesNormalReturn(callee)) out += identityFrame(parts.untouched)
    return out
    // No raw root identity/coarse-E fallback while an owned field result is pending.
}

fun onNormalExit(premise: InitialAp, facts: Facts) {
    endAt(premise, facts, summary = true) // existing normal-exit publication owner
    // Special-root publication supplies return/control evidence, not unrestricted data semantics.
}

fun readNativeOrOppositeRootRecord(record: Record, input: Facts): Effects {
    require(record.hasNativeReachedNormalExitProvenance)
    return readStaticResult(input, record.method, returnEvidenceFrom(record))
    // Apply the same ownership domain before/through native or opposite read views.
}
```

The special root still has ordinary code/summary/control work and no rule/request
evaluation. Frame only the footprint complement on an actual normal return. Do
not restore cleaned f/T, lose untouched g/T through coarse root E{C}, invent a
return or use method existence/completion as evidence. Keep compact footprint
metadata for later record readers. Neither stored IR nor the record format changes.

## 9. Source changes and order

| Step | Components | Required control |
| --- | --- | --- |
| 1 | `Run1StaticProfiles`, `JIRStaticMetadata`, `JIRBidiAnalysis` | Effective forms, finite layout/domain, closed frozen rows and deferred wide scopes match the model |
| 2 | Initial admission, `Demand.kt` adapters and independent request matcher | All ten allowed cases have actual supply/start witnesses; ANY and other forbidden shapes are rejected; no new emitter cut |
| 3 | `PublicationIndex`, `Summary.kt`, `RecordStore` and component reader | Root cleaner/write counterexamples, normal-return gate, broad projections and later/opposite reads |
| 4 | New `RequestStorage`, `SummaryStorage`, `MarkQuestions` replacement | Shared B reference, disjoint residual D/P, no initial wave, no cumulative mirror |
| 5 | `Run1Abstraction`, subscription/call services and handler ports | Bind/read ordering, concrete answers, changed-origin FLOW, late wide activation, counted queued effects |
| 6 | `RunMethodAnalyzer`, local services, `MarkGate`/`EdgeApplication`/`Clean` | R-only collection, every exclusion creator, method-empty commit/replacement, request-only completion |
| 7 | Remove obsolete query/role/memo code; update request statistics | No retained supplier/answer memo or duplicate cumulative request table |

Do not enable root rule suppression before guarded readers, or remove the memo
before input ordering/read arms exist. These are review steps, not runtime changes
made by this document. IR storage, reference managers, WeakIdentityCache, bound-rule
retirement and a broad scheduler rewrite remain outside scope.

## 10. Validation and measurement

```kotlin
// Independent scalar oracles use the checked model, not the old full-query cursor.
checkAllTenInitialsAndSupplyGates()
checkNoAnyInitialIncludingStaticFieldAndClassAnswers()
checkAnyOrMustSupplierKeepsAbstractAndAddsConcreteChainOnlyWhenRequested()
checkNoConcreteStaticRootNoAbstractClassNoMustNoNonemptyE()
checkSharedBaseHasNoPendingResidualOrInitialNotificationCopies()
checkEveryNewTaintAndFlowInputReadsPrecomputedBase()
checkUnchangedRowNoSendAndMixedChangedOriginStillSends()
checkDeferredWideBeforeAfterCommitAndBothArrivalOrders()
checkConcreteOriginStillAnswersButNeverClimbs()
checkEveryExclusionCreatorAndOrdinaryCleanerOutputs()
checkRootCleanerLeakSiblingWriteLossAndNoReturnFrame()
checkNativeReversedAndLaterReadersKeepComponentGuards()
checkCommitReplacementRequestOnlyWorkAndCancellation()
```

Measure precomputed base bytes/references, D/P memberships, suppressed unchanged
rows, fresh slot batches, duplicate proposals, input visits, marks scanned, concrete
answers/starts, temporary peak size and submission delay. Count shared owners as a
retained union; do not add overlapping base references as memory savings.

Use only the fixed OWASP, Conductor, ThingsBoard and Stirling-PDF configurations
with `runProjectAnalyzer` and a fresh main comparison. Report strict findings,
completed run-1 engine time and latest reached/completed/accepted iterations.
An earlier OOM or smaller incomplete report is not an improvement. The Lean proofs
cover their stated finite semantics, not future Kotlin conformance, pooled ND
confirmation, whole-driver correctness or a universal 60-second bound.
