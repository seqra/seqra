# Merged run-1 mark requests: implementation proposal

Date: 15 October 2026. Status: implemented; core validation passes; fresh project acceptance fails.

The [static component codec clarification](bidirectional-run1-static-component-codec-20261015.md)
specifies the packed exclusion headers, separate class-point ownership, supplied
class-mark preservation and selected JVM FLOW boundary required during implementation.

This proposal implements [F78](../../spec/run1-requests.md). The
[proof dossier](../../spec/run1-request-proofs.md) states the semantic arguments,
Lean results and their scope. The [original proposal](bidirectional-run1-mark-request-redesign-20261015.md)
records the measurements and selected changes. F78 replaces its proof-obligation
list with a precise contract and a required static-root safety clarification.

The [fresh measurements](../reports/bidirectional-run1-mark-request-performance-20261015.md)
use implemented `4c64c579f4c4a10fc8ffcc5bcd0bf1bcd45cf877`. The static-domain
coverage obligation remains open: three projects fail at its class guard.

| Project | Latest reached | Last completed engine | Accepted report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 245.412 |
| Conductor | run-1, forward; FAILED | None | None | 8.551; failed |
| ThingsBoard | run-1, forward; FAILED | None | None | 47.387; failed |
| Stirling-PDF | run-1, forward; FAILED | None | None | 11.680; failed |

The target is a completed run-1 engine within 60 seconds. Prescan and the report
barrier are measured separately. The [performance report](../reports/bidirectional-run1-mark-request-performance-20261015.md)
defines the fixed workloads and measurement limits. These proofs establish no
speedup or time bound.

## 1. Scope and proposed interfaces

The snippets below define the design interfaces. The runtime follows these contracts. The
[F78 spec](../../spec/run1-requests.md) and its
[proofs](../../spec/run1-request-proofs.md) define their contracts. Keep existing
AP transfers and ordinary/ND matching follow the spec. The proposed initial and
request policy differs from the historical measured runtime; section 5.1 lists those changes.

```kotlin
sealed interface Run1Slot {
    data class Base(val base: NonStaticBase) : Run1Slot
    data class StaticField(val cls: ClassAccessor, val field: StaticFieldAccessor) : Run1Slot
}

// Full MethodKey is the owner; it includes entry and context.
typealias RequestRows = ImmutableMap<Run1Slot, ImmutableMarkSet>

// Posted to a subscribed CALLER RUNNER, which owns its caller-input directory.
data class ChangedRequests(val callee: MethodKey, val rows: RequestRows) : RunEvent

// Routed to the METHOD OWNER. Rows use that method's original initial slots.
data class MarkRequestsIn(val method: MethodKey, val rows: RequestRows) : RunEvent
data class InitialAnswersIn(val method: MethodKey, val answers: ImmutableSet<InitialAp>) : RunEvent

// A borrowed selection, with no retained merged array/map.
data class RequestRowView(
    val precomputed: ImmutableMarkSet,
    val dynamic: ImmutableMarkSet,
) : MarkSelection { // precomputed and dynamic are disjoint
    val isEmpty: Boolean get() = precomputed.isEmpty() && dynamic.isEmpty()
    override fun contains(mark: Mark) = mark in precomputed || mark in dynamic
    override fun forEach(visit: (Mark) -> Unit) {
        precomputed.forEach(visit)
        dynamic.forEach(visit)
    }
}

data class RequestedRow(val slot: Run1Slot, val marks: RequestRowView)
```

Requests carry no full pattern, tail, E, mark exclusion, rule role, caller or call
site. Those ordinary rule and caller identities retain their current owners.
The event envelope carries the destination or callee key; it does not add a
caller/call-site dimension to stored request memberships. `ChangedRequests`
replaces the payload of the existing `QuestionDelivery` caller-runner event.

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

    // Called only while SummaryStorage holds its lock. checkpoint belongs to the
    // active run; temporary storage still uses the parameterless constructor.
    fun borrowMatchingRows(input: Facts, checkpoint: () -> Unit): List<RequestedRow> = buildList {
        checkpoint()
        val root: TrieNode<*> = when (input) {
            is REACH -> return@buildList
            is FLOW -> input.root
            is TAINT -> input.root
        }
        if (root.isEmpty) return@buildList

        fun addSlot(slot: Run1Slot) {
            checkpoint()
            val marks = borrow(slot) // two immutable segments, not a B union D copy
            if (!marks.isEmpty) add(RequestedRow(slot, marks))
        }

        if (input.base != STATIC) {
            // Accepted input is nonzero/non-temporary. Path and tails do not
            // refine a non-static request: it always names this base-root slot.
            addSlot(Run1Slot.Base(input.base as NonStaticBase))
            return@buildList
        }

        if (root.hasBroadLeaf()) {
            forEachStoredStaticSlot(checkpoint) { slot -> addSlot(slot) }
            return@buildList
        }

        var hasBroadClass = false
        root.forEachBorrowedChild(checkpoint) { cls, classNode ->
            if (classNode.hasBroadLeaf()) {
                hasBroadClass = true // scan stored slots once below, not once per class
            } else {
                classNode.forEachBorrowedChild(checkpoint) { field, fieldNode ->
                    if (!fieldNode.isEmpty) addSlot(Run1Slot.StaticField(cls, field))
                }
                // An exact class leaf alone supplies no descendant field.
            }
        }
        if (hasBroadClass) {
            forEachStoredStaticSlot(checkpoint) { slot ->
                if (root.child(slot.cls)?.hasBroadLeaf() == true) addSlot(slot)
            }
        }
    }

    private fun forEachStoredStaticSlot(
        checkpoint: () -> Unit,
        visit: (Run1Slot.StaticField) -> Unit,
    ) {
        for (slot in sharedBase.keys) {
            checkpoint()
            if (slot is Run1Slot.StaticField) visit(slot)
        }
        for (slot in additions.keys) {
            checkpoint()
            if (slot is Run1Slot.StaticField && !sharedBase.containsKey(slot)) visit(slot)
            // A slot present in both maps was visited above with BOTH segments.
        }
    }

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

private fun TrieNode<*>.hasBroadLeaf(): Boolean = when (val value = leaf) {
    is FlowLeaf -> !value.isEmpty // correlated STAR or demand ANY; mx is ignored
    is TaintLeaves -> !value.any.isEmpty // demand ANY or normal must; exact alone is not broad
    else -> error("Unexpected run-1 leaf kind")
}

private inline fun TrieNode<*>.forEachBorrowedChild(
    checkpoint: () -> Unit,
    visit: (Accessor, TrieNode<*>) -> Unit,
) {
    val keys = accessorData ?: return
    val nodes = childData!! // existing internal immutable backing, not copying public getters
    for (offset in keys.indices) {
        checkpoint()
        visit(keys[offset], nodes[offset])
    }
}
```

The matcher assumes the accepted limit-1 input layout from section 5: class then
static field on S; it does not cut or repair an invalid input. FLOW, TAINT and
REACH denote the existing FlowTree, TaintTree and Reach types in these snippets.
It ignores E and FLOW mark exclusions, and never enumerates a leaf's mark IDs.
Actual requested/supplied mark intersection belongs to section 6.

Each slot occurs once in the result. A broad root returns before child traversal.
A broad class skips direct field lookup; its slots come only from the final scan.
That scan visits B keys and D-only keys, borrowing B/D together where their keys
overlap. Canonical packed child keys are unique. There is no persistent index,
temporary set of all request keys, or materialized B∪D mark set.

For ordinary bases this needs one slot lookup. Explicit static field branches
need direct slot lookups. Only a broad root/class leaf triggers a scan of stored
B/D keys, considering static slots only, at most once per input; class selection uses the existing packed
trie's child lookup. The returned transient list holds row descriptors and shared
immutable mark-set references, not copied marks. It is built completely under the
SummaryStorage lock and discarded after the input join.

```text
B: C.f -> {T}, C.g -> {U}, D.h -> {V}
D: C.f -> {W}, C.k -> {Z}, Base(b) -> {Q}

Input                       Rows returned (geometry only)
b.q.$(X)                    Base(b)
C.f.$(X) or C.f.[any](X)    C.f; precomputed={T}, dynamic={W}, even when X differs
C.$(X) alone               none; direct class-point handling is separate
C.[any](X)                 C.f, C.g, C.k
S.[any](X)                 C.f, C.g, C.k, D.h; never Base(b)
C.[any](X) plus D.h.$(X)    C.f, C.g, C.k, D.h; each once
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

fun releaseRun1AfterJoinedBarrier() {
    check(allRun1RequestWorkJoined())
    check(reportPersistAndHandOffConsumedFrozenRun1State())
    releaseRun1ActorsAndSummaryStores() // includes R/D/P and all request-plan references
    frozenRun1Plans = null // releases B and wide/class mark interests
    // Broad owner release follows the existing barrier's use of frozen summaries,
    // source hits and witnesses. Stable structural footprints remain for record readers.
}
```

The field universe and JVM layout come from canonical prescan IR/rules/models.
Only already resolved slots enter B. A wide descriptor alone does not create a
field×mark product. Collector scratch data and old query/role request rows are
released; wide/class/footprint descriptors are metadata, not another resolved
request store.

### 3.2 Bind the shared map before the first subscriber or input

```kotlin
class SummaryStorage(val method: MethodKey, plan: StaticPlan) {
    private val lock = Any()
    private val requests = RequestStorage(sharedBase = plan.precomputedRequests)
    private val subscribers = mutableSetOf<CallerRunnerPort>()
    // Empty D. The constructor borrows B; it does not iterate/copy B.

    fun borrowRequest(slot: Run1Slot): RequestRowView = synchronized(lock) {
        requests.borrow(slot) // captures immutable row values
    }

    fun borrowMatchingRequests(input: Facts): List<RequestedRow> = synchronized(lock) {
        requests.borrowMatchingRows(input, cancellation::checkpoint)
        // Transient row descriptors borrow immutable B/D mark sets. No iterator
        // over the mutable additions map escapes the lock; no merged R copy.
    }

    fun registerSubscriber(callerRunner: CallerRunnerPort) = synchronized(lock) {
        subscribers.add(callerRunner) // once per runner/full callee key
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
    val admission = inputs.admitOriginalHeaderAndFacts(part)
    if (admission != null) {
        replayOrdinarySummaries(admission.fresh) // existing fresh-delta contract
        val current = admission.current // original header + current accumulated Facts
        if (current.added is FLOW) {
            validateExistingPremiseFactInvariant(current.ref.premise, current.added)
            if (current.ref.premise.asNormalAbstractRun1SlotOrNull() != null) {
                val effects = Effects()
                collectFlowEffects(current, calleeStore.borrowMatchingRequests(current.added), effects)
                effects.sendCountedAfterVisit()
            } // special-root/concrete origins never start a request-row scan
        }
    }
    sendCountedToOwner(LinkIn(part)) // new TAINT reads R at the callee
}

fun MethodActor.onLinkIn(part: Subscription) {
    check(part.callee == method)
    emitPermittedAbstractInitials(part.added)
    forEachDirectClassInitial(part.added, plan.classPointNeeds, ::admitRun1Initial)
    activateRelevantWideNeeds(part.added)
    if (part.added is TAINT) {
        val rows = summaries.borrowMatchingRequests(part.added)
        forEachConcreteAnswer(part.added, rows, ::admitRun1Initial)
    }
    // No supplier history, speculative answer scan or answer memo.
}
```

Precomputed requests have been visible since storage construction, so every new
input sees them. Section 7 defines the late dynamic-request path. New TAINT input
is answered only at the callee; the caller's fresh-input request join handles FLOW.
This snippet deliberately uses `admission.current` for fresh FLOW/growth. It can
rescan older geometry on growth, which must be counted and measured. It preserves
the original admitted group header; a normalized AP delta is not a replacement
header. Ordinary summary replay still uses `admission.fresh`.

### 3.4 Unchanged and changed origins

```kotlin
fun collectFlowEffects(part: Subscription, rows: Iterable<RequestedRow>, out: Effects) {
    validateExistingPremiseFactInvariant(part.ref.premise, part.added) // K6
    if (part.added !is FLOW) return // concrete answers have their own branch
    val original = part.ref.premise.asNormalAbstractRun1SlotOrNull() ?: return

    val offered = MarkSetBuilder()
    for (row in rows) {
        if (!slotMatches(row.slot, part.added)) continue // ignores request E/mx

        // Per queried slot, never per whole input or whole plan.
        if (original != row.slot || original !is Run1Slot.StaticField) {
            offered.addAll(row.marks.precomputed) // changed origins keep these marks
        }
        // Otherwise skip the whole B row: caller's closed B already contains it.
        offered.addAll(row.marks.dynamic) // fresh D is never suppressed by B inheritance
    }
    if (offered.isEmpty()) return
    val target = part.ref.caller // full caller MethodKey, not part.callee
    val targetR = summaryStorage(target).borrowRequest(original)
    val useful = offered.freeze().minus(targetR)
    if (useful.isNotEmpty()) out.request(target, original, useful)
    // Union all matching rows, read target R and merge once per input visit.
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
    val target = actor(event.method) // routed to this method's owner runner
    for ((slot, marks) in event.rows) target.collectRequests(slot, marks)
} // re-filter a queued/stale sender proposal against current R
```

A newly resolved wide request enters P once as a merged pending membership. Its
fresh admission must notify callers so earlier inputs are handled. Repeated
activation after admission produces no new P membership. No extra activation cache
or per-input request matrix is needed.

## 4. Commit-and-replace and method scheduling

```kotlin
fun SummaryStorage.commit(temporary: RequestStorage) {
    val (changed, recipients) = synchronized(lock) {
        val freshRows = immutableRows {
            for ((slot, marks) in temporary.pendingRowsForCommit()) {
                val fresh = requests.addAll(slot, marks) // subtracts B union D
                if (fresh.isNotEmpty()) put(slot, fresh)
            }
        }
        freshRows to if (freshRows.isEmpty()) emptyList() else subscribers.toList()
        // Registration and recipient capture share this storage boundary.
    }
    if (changed.isEmpty()) return
    val event = ChangedRequests(callee = method, rows = changed)
    for (callerRunner in recipients) {
        requireAccepted(callerRunner.postCounted(event)) // explicit caller-runner destination
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
onRequestedTaint(supplier) {
    val rows = summaries.borrowMatchingRequests(supplier)
    forEachConcreteAnswer(supplier, rows, ::admitRun1Initial)
}
onDirectClassInput(supplier)          {
    forEachDirectClassInitial(supplier, plan.classPointNeeds, ::admitRun1Initial)
}

// Source/end-GEN and sink/source seeds create edge work under their actual premise.
// They do not admit arbitrary new run-1 method initials. ND members are not a
// eleventh shape. Later-run restricted emission/record readers have their own grammar.
```

Validate static layout as class→static-field→ordinary suffix, with one counted
accessor at run-1. Positivity alone does not prove the bound. Current generic
factories, historical static ANY/abstract-class starts and custom first limits above 1 do
not yet conform to this proposed boundary.

### 5.1 Emitted initial facts: current runtime versus proposed F78

Here **current** means the historical code at `27e22bb1e`; the relevant runtime files
were unchanged at the proposal review revision `fa86688b8`. **New** means the selected
F78 implementation. Its project measurements are pending. `C.f.*(*)` has an abstract mark; `C.f.*(T)` has concrete mark T. All
emissions below have Empty E. A normal-slot concrete answer always requires an
actual supplier carrying the requested mark. Direct class components are the
separate exception already specified by F78; there is no class request slot.

| Input/case | Current emitted InitialAp | New emitted InitialAp |
| --- | --- | --- |
| Root start or incoming REACH | Canonical Zero | Same |
| Non-static input on b | Abstract `b.*(*)` | Same |
| Requested non-static exact root carrying T | `b.$(T)` | Same shape |
| Other requested matching non-static supplier carrying T | Base-root `b.*(T)` | Same shape |
| Any static input | Abstract `S.*(*)` | Same summary seed; rule evaluation on its root component is now skipped |
| A selected actual field node C.f | Abstract `C.f.*(*)`, regardless of a concrete request | Same; abstract field remains additive |
| A selected class node C, for example the prefix of a static write | May emit abstract `C.*(*)` when the profile contains `[C]` | No abstract class initial; selected field analyses and the guarded frame replace that role |
| A pure broad S/class supplier supplying selected C.f, with no explicit f child | Catalog requests can project concrete field answers, but the structural cursor does not expand this broad leaf into abstract field starts | Emit selected supplied abstract `C.f.*(*)` even without R(C.f,T); concrete field answers are separate |
| Requested exact field leaf C.f carrying T | `C.f.$(T)` if the active full query matches | `C.f.$(T)` if the normal field slot matches and R requests T |
| Requested ANY/must leaf at C.f carrying T | `C.f.[any](T)` | `C.f.*(T)`; never an ANY initial |
| Requested broad root/class ancestor of C.f carrying T | `C.f.*(T)` when the active query matches | Same STAR shape, with the new slot-only request test |
| Exact class-point input `C.$(T)` | Concrete `C.$(T)` needs an active matching question; otherwise it is not guaranteed | Direct concrete `C.$(T)`, independent of normal-slot requests; no abstract class analysis |
| Nonexact class-point/broad class component carrying T | A matching class query can emit `C.[any](T)`; a broad root projected to C can give `C.*(T)` | Metadata-selected class component emits `C.*(T)`; never an ANY class initial |
| Concrete empty-S-root answer | A matching legacy root question can emit one; broad root input can yield `S.[any](T)` | No concrete empty-S-root initial of any tail and no root request. Keep the abstract S seed and permitted field/class projections |

The historical paths above used `Run1Abstraction.emit`, `StaticSupplierCursor`
and `RequestGroupCursor.emit` at `27e22bb1e`. The last two runtime classes are now
removed. The old metadata collector added an abstract class position for a static
write, and old emission had no unconditional direct-class branch. Abstract-class
removal and F78's direct exact-class start are actual constructor changes. The new
[Run1RequestMatching](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1RequestMatching.kt)
contains the selected projectors.
These are source-level differences, not counts observed in a new workload run.

```text
Selected C.f is supplied by C.f.[any](T); inspect only C.f initials here:
    current, active matching request:  C.f.*(*) + C.f.[any](T)
    new, R(C.f) lacks T:               C.f.*(*)
    new, R(C.f) contains T:            C.f.*(*) + C.f.*(T)
    (The S-root seed and any legacy abstract C row are covered by the table above.)

Exact class input C.$(T), with no active matching question carrying T
(including root/catalog questions):
    current: abstract S root; possibly abstract C if selected by the profile
    new:     abstract S root + direct concrete C.$(T); no abstract C
```

Eligibility and delivery also change even where the resulting shape is unchanged:

| Decision | Current | New |
| --- | --- | --- |
| Which request enables a concrete field start? | Original initial plus full query pattern and role; the supplier must pass that query's geometry/exclusion tests | Full MethodKey + normal slot + mark; role/deep rule path are absent from the request |
| A rule asks about C.f.g but incoming concrete T is at C.f | Full-query geometry can prevent an answer | An admitted C.f/T slot request can start `C.f.$(T)`; the rule still checks its full g path and may produce no result |
| Input field E and FLOW mark exclusions | The old request join can reject on these values | Ignored only for request matching/forwarding; ordinary transfers and caller summary matching retain them |
| Precomputed request visibility | Input-selected catalog rows call `requireSelection` and can activate local questions while emitting that input | Frozen B is visible before any input; it creates no startup notification wave |
| T arrives before a dynamic request | The abstraction retains an unrequested answer shape/mark memo; later activation can consume it | No concrete initial yet and no callee memo. Later R admission replays retained caller inputs and queues the actual concrete answer |
| T arrives after its request | The abstraction queues a concrete answer for its service point; caller fresh-input replay can also answer | The callee's new-TAINT/current-R path admits the concrete initial; caller fresh-input request replay handles FLOW only |
| Dynamic publication while a method stays busy | A nonzero scheduling quantum can flush abstraction work | P stays pending until that method's fact queue is empty; quantum yield alone does not commit it |

Slot-only eligibility can create starts that the old full-query test did not
create. Removing class/root initials removes other starts. This is not an
identity of old/new initial sets or a claim that their total count decreases.
Actual T is never invented, and request membership alone never satisfies a rule.

The historical generic cursor could also retain a deeper supplier path in a static
answer, and the old driver permitted a first limit above 1. The selected F78
domain requires limit exactly 1 and bounded inputs before emission; it rejects a
violated contract rather than adding an emitter cut. Likewise, exact concrete
empty-S-root input is forbidden by the selected frontend assumption. Those generic
API capabilities do not prove that such inputs occur in the selected JVM workloads.

F78 does not add a `T != 0` restriction on nonzero bases. The current generic mark
and initial APIs permit that ID, but the old static catalog builder had an `id > 0`
check. Its replacement preserves every frontend-admitted mark, as the spec
requires; it must not infer a Zero base from the mark ID alone.

Concrete STAR and the former ANY initial start with the same DEMAND/ANY fact,
but their stored keys and applicability differ: STAR can accept an exact caller
fact that ANY rejected. Summary/record readers and handoff must follow the new
premise policy. The checked local denotational results do not establish whole-run
equivalence, equal timeout prefixes or measured finding/performance parity.

## 6. Request projection and exclusion creators

```kotlin
fun TAINT.concreteLeaves(): Sequence<ConcreteBranch> = sequence {
    for (node in positions()) {
        if (node.exactMarks.isNotEmpty()) yield(exactBranch(node.path, node.exactMarks))
        if (node.anyMarks.isNotEmpty()) yield(anyOrMustBranch(node.path, node.anyMarks))
    }
    // Keep branch-specific mark sets. Do not union exactMarks with anyMarks.
}

fun forEachConcreteAnswer(
    supplier: TAINT,
    rows: Iterable<RequestedRow>, // only admitted marks: effective R or a committed fresh delta
    emit: (InitialAp) -> Unit,
) {
    for (leaf in supplier.concreteLeaves()) { // visit the branch once for the whole mark batch
        for (row in rows) {
            if (!slotMatches(row.slot, leaf)) continue
            val suppliedAndRequested = leaf.actualMarks.intersect(row.marks)
            if (suppliedAndRequested.isEmpty()) continue
            val slot = row.slot
            val (base, path) = slot.geometry()
            val exact = when (slot) {
                is Run1Slot.Base -> leaf.isExactBaseRoot
                is Run1Slot.StaticField -> leaf.isExactAt(slot)
            } // geometry once per matching branch/row, before enumerating marks
            suppliedAndRequested.forEach { mark ->
                val initial = if (exact) exactInitial(base, path, mark)
                    else chainInitial(base, path, mark) // nonexact field/ancestor or ordinary base
                emit(initial) // one scalar key is unavoidable per distinct concrete initial
            }
        }
    } // preserve exact and chain alternatives; InitialFactStore deduplicates
    // Emitted E is empty; ordinary caller matching retains original E/layer.
}

fun forEachDirectClassInitial(input: Facts, needs: ClassPointNeeds, emit: (InitialAp) -> Unit) {
    if (input !is TAINT) return
    for (leaf in input.concreteLeaves()) {
        if (leaf.isExactClassPoint) {
            leaf.actualMarks.forEach { mark -> emit(exactInitial(STATIC, leaf.path, mark)) }
        } else if (leaf.isAnyOrMustStaticRootOrClass) {
            for (need in needs.matchingOwnedClassPoints(leaf)) {
                leaf.actualMarks.forEach { mark -> // class selected by point ownership; no mark mask
                    emit(chainInitial(STATIC, path(need.cls), mark))
                }
            }
        }
    } // all branches/marks; no nullable single-answer shortcut and no class request key
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

fun selectedMarkCleaner(origin: PremiseKey, input: Facts, cleaner: SelectedCleaner) {
    validateExistingPremiseFactInvariant(origin, input)
    if (input is FLOW && input.base == cleaner.base) {
        origin.asNormalAbstractRun1SlotOrNull()?.let { slot ->
            actor.collectRequests(slot, cleaner.marks) // F75 before path/E/mx tests
        }
    }
    applyOrdinaryCleaner(origin, input, cleaner) // original AP geometry, outputs and E rules
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

## 7. Dynamic requests: from the callee to the caller

M calls N. A request stored in N names an **N slot**. The caller runner joins that
slot with the existing bound inputs for N. A matching FLOW input supplies M's
**original initial slot**, through its retained CallerRef. That is the destination
of the next request. The request does not follow a summary edge or a guessed
inverse binding, and N's publisher does not inspect M's input directory.
`CallerInputDirectory` below denotes the existing `CalleeSubscriptions` relation,
not a new input store. `CallerRunnerPort.postCounted` uses the existing subscriber
port's counted post; it does not introduce another queue.

```mermaid
sequenceDiagram
    participant N as Callee N actor
    participant R as SummaryStorage(N)
    participant C as Caller runner
    participant M as Caller M actor
    N->>N: Collect marks in temporary P_N
    N->>R: commit(P_N) when N fact queue is empty
    R->>C: Queued ChangedRequests(N, fresh rows)
    C->>C: Join N slots with retained bound inputs for N
    alt Matching FLOW under original slot p in M
        C->>M: Queued MarkRequestsIn(M, p, useful marks)
        M->>M: Merge P_M; commit at M's own barrier
    else Matching TAINT carrying a requested mark
        C->>N: Queued InitialAnswersIn(N, concrete initials)
    end
```

### 7.1 Collect a local need, then publish it at the method barrier

```kotlin
fun MethodActor.onLocalMarkNeed(premise: PremiseKey, input: Facts, marks: MarkSelection) {
    validateExistingPremiseFactInvariant(premise, input) // K6 before eligibility
    if (input !is FLOW) return // concrete facts are evaluated directly
    val original = premise.asNormalAbstractRun1SlotOrNull() ?: return
    collectRequests(original, marks) // section 3.5: R difference -> P -> request Work
}

// Mark checks, concrete input gates and generic exclusion creators use that path.
// F75 selected-mark cleaners collect before geometry/mx tests, including disjoint
// and already-excluded FLOW inputs. The special static root evaluates no rules.
// Queue-empty handling then executes section 4's commit-and-replace exactly once.
```

Only a fresh admission to R_N creates `ChangedRequests(N, rows)`. Sending a
proposal to P_N is not admission and sends no caller notification. B is already
admitted before any input; this dynamic path publishes only fresh D memberships.

### 7.2 The subscribed caller runner visits its inputs once per batch

```kotlin
fun onChangedRequests(event: ChangedRequests) { // SubscriptionManager on caller runner
    val inputs = callerDirectories[event.callee] ?: return
    val effects = Effects()
    val rows = event.rows.map { (slot, freshMarks) ->
        RequestedRow(slot, RequestRowView(
            precomputed = ImmutableMarkSet.EMPTY,
            dynamic = freshMarks,
        )) // ALL event marks are fresh D; this does not discard any event member
    }
    inputs.visitRequestBatch(event.callee, rows) { part ->
        when (part.added) {
            is FLOW -> collectFlowEffects(part, rows, effects) // section 3.4
            is TAINT -> {
                validateExistingPremiseFactInvariant(part.ref.premise, part.added)
                forEachConcreteAnswer(part.added, rows) { initial ->
                    effects.answer(part.callee, initial) // back DOWN to N, not up to M
                }
            }
            is REACH -> validateExistingPremiseFactInvariant(part.ref.premise, part.added)
        }
    }
    effects.sendCountedAfterVisit() // directory visit ended before any delivery
}

fun CallerInputDirectory.visitRequestBatch(
    callee: MethodKey,
    rows: List<RequestedRow>,
    visit: (Subscription) -> Unit,
) {
    check(!visitingRequests)
    visitingRequests = true
    val seen = IntOpenHashSet() // one transient set for ALL slots in this batch
    try {
        for (row in rows) {
            cancellation.checkpoint()
            val (base, path) = row.slot.geometry()
            index.visitAround(base, path) { handle -> // existing prefix/extension candidates
                cancellation.checkpoint()
                if (seen.add(handle)) {
                    val value = values.snapshot(handle)
                    visit(Subscription(callee, value.ref, value.group.layer, value.facts))
                    // Original CallerRef + group header + current accumulated Facts.
                    // A candidate still needs the complete slot matcher in section 3.4/6.
                }
            }
        }
    } finally {
        visitingRequests = false
    }
}
```

`collectFlowEffects` unions marks from **all** matching rows before one target-R
difference and one destination merge. Each row still applies its own unchanged-B
skip. A concrete fact never enters that FLOW branch. Ordinary/ND caller matching
keeps the complete original premise and independent layers; the request join does
not rewrite those bindings. The transient `seen` set ends with the visit; no
request-by-input seen matrix is retained.

### 7.3 Queue the resulting caller requests and callee answers

```kotlin
class Effects {
    // Transient output builders for this visit, not additional cumulative stores.
    private val requests = mutableMapOf<MethodKey, RequestRowsBuilder>()
    private val answers = mutableMapOf<MethodKey, MutableSet<InitialAp>>()

    fun request(method: MethodKey, original: Run1Slot, marks: ImmutableMarkSet) {
        requests.getOrPut(method) { RequestRowsBuilder() }.union(original, marks)
    }

    fun answer(callee: MethodKey, initial: InitialAp) {
        answers.getOrPut(callee) { linkedSetOf() }.add(initial)
    }

    fun sendCountedAfterVisit() {
        for ((method, builder) in requests) {
            val rows = builder.freeze()
            if (rows.isNotEmpty()) sendCountedToOwner(MarkRequestsIn(method, rows))
        }
        for ((callee, initials) in answers) {
            if (initials.isNotEmpty()) {
                sendCountedToOwner(InitialAnswersIn(callee, initials.toImmutableSet()))
            }
        }
    }
}

fun sendCountedToOwner(event: RunEvent) {
    check(event !is ChangedRequests) // those deliveries target a subscribed runner explicitly
    // Existing RunManager route resolves the full MethodKey to its owner runner.
    // MarkRequestsIn and InitialAnswersIn always enqueue, including same-unit delivery.
    requireAccepted(existingRunRoute(event))
}

fun requireAccepted(accepted: Boolean) {
    cancellation.checkpoint()
    check(accepted) { "An active run rejected required queued work" }
    // Existing failure handling fails/cancels the run; this cannot certify COMPLETE.
}

fun onInitialAnswersIn(event: InitialAnswersIn) { // method-owner handler
    val target = actor(event.method)
    for (initial in event.answers) {
        cancellation.checkpoint()
        target.admitRun1Initial(initial) // grammar check + InitialFactStore dedup + start
    }
}
```

At M, `onMarkRequestsIn` from section 3.5 subtracts **M's** current R, then merges
into **M's** P. It never writes P from another runner. If M has no fact work,
`requestWork()` schedules a request-only visit. At M's method-empty barrier,
`SummaryStorage(M).commit(P_M)` notifies M's callers, and the same path repeats.
Neither requests nor answers call a method actor inline from a directory visit.
Existing counted Work remains reserved until every required child send is secured.

Adapt the existing [SubscriptionManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/SubscriptionManager.kt)
notification/replay/send path and [RunMethodHandlers](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodHandlers.kt)
owner dispatch. The former `QuestionDelivery(callee, delta)` becomes the batched
`ChangedRequests(callee, rows)` delivery; `MarkRequestsIn` and `InitialAnswersIn`
keep their method-owner routing roles.

### 7.4 Example with different callee and caller slots

```text
E calls M; M calls N. M's original p flows to N's x.

Stored caller input for N: (caller=M, original=p.*, added=N.x.*)
N needs T on x:            P_N[BaseSlot(x)] += {T}
N's empty-queue barrier:  R_N[x] gains T; ChangedRequests(N, {x -> {T}})
M's caller runner:        match x against added N.x.*; recover ORIGINAL M.p.*
Queued to M owner:        MarkRequestsIn(M, {p -> {T} - R_M[p]})
M owner:                  P_M[p] += received marks; R_M changes only at M's barrier
M's empty-queue barrier:  ChangedRequests(M, {p -> fresh marks}) reaches E's runner

If E's bound input to M carries concrete p.$(T):
    E's runner queues InitialAnswersIn(M, {p.$(T)}).
    M starts that concrete analysis. Its ordinary call to N supplies concrete x/T.
    N's abstraction reads R_N[x] and starts the requested concrete x analysis.

For a static copy with original C.g and current C.f, the same join routes the
request to M's StaticField(C,g), not to StaticField(C,f).
```

New interests travel through matching FLOW inputs. Already admitted memberships
produce no further delivery. A matching concrete supplier answers an interest;
if no input carries T, no concrete answer is invented. The resulting concrete
analysis uses the ordinary forward pipeline. R remains active after an answer so
later inputs can answer the same interest. An admitted interest alone does not
keep Work scheduled once facts, publications and P are empty.

### 7.5 Both arrival orders and subscription races

```text
bind B/effective R < register subscriber < admit original caller input < LinkIn
commit R < queue ChangedRequests < caller's late-input visit

New FLOW/growth: current admitted input joins full current R at the caller.
New TAINT:       LinkIn joins full current R at the callee abstraction.
New request:    ChangedRequests joins current retained FLOW and TAINT inputs.
```

Registration and commit's recipient capture use the same SummaryStorage lock.
If registration precedes commit, that caller runner is a recipient. If commit
precedes registration, the new input's current-R read sees the admission. A queued
notification can find no input yet; admission precedes its own current-R read,
which then covers that case. Delivery is queued even when the subscriber is local.

For TAINT, let A be caller input admission, E the emitter read, Q commit and V late
request replay. `Q <= E` is covered by the emitter. Otherwise
`A < E < Q < V`, so replay sees the retained input. FLOW uses the same argument
with its caller-side read. Sending TAINT directly to a callee without the caller
registration/admission path violates this contract.

Keep the existing caller input relation through joined work. There is no callee
supplier history, deferred-answer memo or subscriber copy of R. Restricted runs
do not use this request protocol.

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
checkBorrowRowsDirectBaseAndFieldLookupsWithoutMarkEnumeration()
checkBorrowRowsBroadRootAndClassScanOnceAndIgnoreEAndMx()
checkBorrowRowsBAndDOverlapHasOneSlotWithTwoBorrowedSegments()
checkBorrowRowsExactClassDoesNotMatchFieldAndMixedBranchesDoNotDuplicate()
checkEveryNewTaintAndFlowInputReadsPrecomputedBase()
checkUnchangedRowNoSendAndMixedChangedOriginStillSends()
checkDeferredWideBeforeAfterCommitAndBothArrivalOrders()
checkConcreteOriginStillAnswersButNeverClimbs()
checkEveryExclusionCreatorAndOrdinaryCleanerOutputs()
checkRootCleanerLeakSiblingWriteLossAndNoReturnFrame()
checkNativeReversedAndLaterReadersKeepComponentGuards()
checkCommitReplacementRequestOnlyWorkAndCancellation()
checkChangedRequestsCalleeKeyAndOriginalCallerSlotRouting()
checkOneInputVisitAcrossAllChangedSlotsAndMergedTargetMarks()
checkFreshFlowGrowthUsesOriginalHeaderAndCurrentValue()
checkRegisterCommitRecipientCaptureAndBothInputReadOrders()
checkLocalAndRemoteEffectsQueueAfterVisitAndRejectRequiredSend()
checkAllPackedClassBranchesAndSelectedCleanerPreGeometryCollection()
checkJoinedBarrierConsumesFrozenResultsBeforeBroadOwnerRelease()
```

Measure precomputed base bytes/references, D/P memberships, suppressed unchanged
rows, fresh slot batches, duplicate proposals, input visits, marks scanned, concrete
answers/starts, direct row lookups, broad-scan key visits, matched row counts,
fresh-FLOW current-value visits/rescanned geometry, temporary peak
size and submission delay. Count shared owners as a
retained union; do not add overlapping base references as memory savings.

The current numeric counter contract is:

| Counter | Unit and scope |
| --- | --- |
| `precomputedBaseRoots`, `physicalPrecomputedRows`, `runtimeMarkSetRoots`, `ownedMarkPrimitiveCells` | Distinct frozen plan maps, their row entries, nonempty immutable mark sets, and owned primitive mark cells, counted by object identity after plan freeze. These are not retained bytes. |
| `logicalMethodRequestRows`, `logicalMethodRequestMembers` | B memberships expanded per method plan. Shared references contribute again here; they do not create another physical B. |
| `requestOfferCalls`, `offeredRequestMembers` | Method-owner collection calls and the mark IDs they examine before effective R filtering. Caller-side filters before transport are outside this admission census. |
| `authoritativeDuplicateRequestMembers`, `temporaryDuplicateRequestMembers` | Offered IDs already in effective R, then R-fresh IDs already in P, respectively. Each repeated attempt contributes in member units. |
| `freshTemporaryRequestMembers`, `currentTemporaryRequestRows/Members`, `peakTemporaryRequestRows/Members` | Cumulative accepted P memberships, aggregate current P cells/rows, and their aggregate high-water values. They overlap later D admission; they are not facts or queue entries. |
| `requestCommits`, `freshRequestRows/Members`, `createdDynamicRequestRows` | Commit calls, changed slot rows/new D memberships published by those commits, and newly created D row keys. Changed rows can recur across commits; created row keys cannot recur within one storage lifetime. |
| `requestMatcherCalls`, `requestDirectLookups`, `requestBroadScans`, `requestScannedKeys`, `requestMatchedRows` | Borrowed matcher calls, direct slot probes, stored-key scan passes, keys examined, and returned unique slot descriptors. A descriptor borrows B and D without a combined mark-set copy. |
| `freshFlowInputReads`, `growthFlowInputReads` | Actual current-value reads for admitted FLOW input updates; growth reads are a subset. They do not count logical facts or marks. |
| `inheritedBaseRowsSkipped` | Matching unchanged static B row segments skipped as a whole before mark enumeration. This is a row count, not a skipped-mark count. |
| `replayVisits/Candidates/Tests/Answers/Climbs`, request-batch geometry/member counters | Finite batch visits and the actual input, geometry, answer, climb, or member operations performed. Inclusive work counters overlap; concrete answers are still deduplicated by `InitialFactStore`. |
| `maxServiceDelayNanos` | Oldest fresh P admission's observed wait until the method-empty commit begins. It excludes commit execution time and gives no fixed latency guarantee. |

Counters aggregate numeric totals only. Cancellation can record a visited prefix
without a completed admission or replay. Read run-attributed START/END differences
for work, and keep failed-cohort accounting distinct from actual heap reachability.
Obsolete emitter-policy and deferred-answer-memo fields are removed from the
current abstraction snapshot; historical snapshots retain their original meaning.

Use only the fixed OWASP, Conductor, ThingsBoard and Stirling-PDF configurations
with `runProjectAnalyzer` and a fresh main comparison. Report strict findings,
completed run-1 engine time and latest reached/completed/accepted iterations.
An earlier OOM or smaller incomplete report is not an improvement. The Lean proofs
cover their stated finite semantics, not future Kotlin conformance, pooled ND
confirmation, whole-driver correctness or a universal 60-second bound.

## 11. Review against the selected spec

This section records the proposal review before implementation. That review changed
documentation only. The current code and validation are described in the
[implementation report](../reports/bidirectional-run1-mark-request-validation-20261015.md).

| Spec contract | Review result and proposal correction |
| --- | --- |
| F78 §2: select request rows by the accepted slot geometry | Section 2 now implements `borrowMatchingRows`: direct base/field lookups, at most one stored-key scan for broad root/classes, exact-ancestor rejection and unique B/D row views. No mark IDs, request exclusions, persistent index or merged mark-set copy are needed. |
| F78 §1–2 versus the current runtime | Section 5.1 now lists source-audited current/new initial shapes, added/removed admission paths, request eligibility and emission timing. It separates actual constructor differences from generic inputs/configurations not established in selected workloads. |
| [F78 §6–7: commit, delivery and destination ownership](../../spec/run1-requests.md#6-the-two-requeststorage-instances) | The earlier notification omitted its callee key and hid the final sends. Sections 1, 4 and 7 now show full callee identity, explicit caller-runner notification, original caller-slot recovery, grouped method-owner requests and separate callee answers. |
| [F78 §7: batch each relevant caller input once](../../spec/run1-requests.md#7-batched-propagation-and-late-inputs) | A single handle set spans all changed slots; matching FLOW rows union before one target-R difference/merge. TAINT traverses each concrete branch once for the mark batch. No persistent seen matrix is added. |
| F78 §7: preserve admitted headers on growth | Section 3.3 now uses the admission result: ordinary summaries read fresh delta, FLOW requests read current accumulated input under its original header. Repeated current-value geometry is explicitly measured. |
| F78 §6–7: linearization and counted queued effects | Registration and commit recipient capture share the storage lock. Notifications leave the lock before posting; results leave the input visit before sending. Local delivery queues too, and rejected required sends take existing failure handling. |
| [F78 §1–3: producers and emitted initials](../../spec/run1-requests.md#2-slot-matching-and-concrete-answers) | All source/sink, gate, cleaner and exclusion producers remain covered. The snippets now state F75 pre-geometry collection and enumerate packed class branches/marks. Abstract starts stay additive; concrete answers need actual supplied/admitted marks; no ANY initial is emitted. |
| [F78 §5 and §7: stable footprint and joined-state lifetime](../../spec/run1-requests.md#5-static-ownership-and-the-untouched-frame) | Broad release now follows report/persist/HandOff consumption of frozen results. Stable structural footprint and normal-return provenance remain available to ordinary/native/opposite readers after request marks are released. |

The shared B reference, disjoint D/P, lack of an initial B wave, per-slot inherited-B
skip, exclusion-insensitive request join, method-empty barrier and return-gated frame
already matched the selected spec at review. AP codecs, complete effective-form
extraction, annotated record readers and scheduling/accounting needed the stated
implementation checks. The implementation report records the executed gates;
the design review alone does not validate code.
