# Mark groups and direct statics: implementation proposal

Date: 19 October 2026. Status: **proposal; no runtime implementation**.
Use ASD-STE100 Simplified Technical English. Code names and defined technical
terms keep their meanings. This proposal implements
[the combined specification](../../spec/grouped-run1-statics.md). Its
[proof dossier](../../spec/grouped-run1-statics-proofs.md) defines the checked
scope. Earlier implementation proposals are not extra requirements.

## 1. Implementation base and code conventions

Start from **a80d5c580b3b4715ab39679fbf66f0f190125fa0**. Its core and rules
equal measured **28a123f0eac937e8f2feca9ee5dd84e38fa8f979**. Do not start
from the current bottom-up runtime and delete selected branches from it.
The [manifest](../../spec/baselines/pre-bottom-up-a80d5c580.json) pins the
base. Keep the new specification, proof files and this proposal when the
implementation checkout is prepared. Do not change the shared checkout
as part of this documentation task.

The paths below have these prefixes:

```text
B = core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/
    org/opentaint/dataflow/bidi/
J = core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/
    org/opentaint/dataflow/jvm/bidi/
```

Read the actual base code with `git show a80d5c580:<path>`. Working-tree
links can show a later runtime. Snippets below are proposed Kotlin API
contracts. Helper types and methods that the base does not have are new;
the snippets are not a claim that a patch already compiles.

| Base behavior | Proposed implementation | Expected effect |
| --- | --- | --- |
| Full generated marks in run-1 | One atom per owner group | Merge mark variants in existing TAINT leaves and requests |
| Transitive static read/mark plans start callee initials | Reached reads join published writes in one run service | Remove method-by-field startup and relay work |
| Static current facts traverse method CFGs and calls | Publish a reached write effect immediately; stop its local static branch | Remove static successor propagation |
| Static mark request climbs a call chain | Join the saved static original directly with rooted field groups | Remove static request transport |
| Full-form exact mark indexing | Full NNF plus a mode-aware group index | Keep full literals while matching run-1 groups |
| All run-1 records are quarantined | Export independently valid NORMAL records only | Reuse valid ordinary work; speculative statics stay DEMAND |

Keep ordinary N2, Base P/R, ND joins, actual-query admission, ZERO discovery,
source identities and AP primitives. No IR, SoftReferenceManager or identity
cache rewrite is included.

## 2. Files and new components

| File or component | Change |
| --- | --- |
| New `B/ap/RuleMarkCatalog.kt` | Stable owner/full catalog and interned typed atom keys |
| `B/ap/Facts.kt`, `AccessorTable.kt`, `ApManager.kt` | Group/full atom lookup; dedicated typed FLOW exclusions |
| New `B/ap/MarkExclusions.kt` | Semantic exclusion algebra and interning |
| `B/ap/MarkGate.kt`, `MarkCheck.kt`, `EdgeApplication.kt`, `Clean.kt`, `Reference.kt` | Runtime mark view; group checks, repair and mixed exclusions |
| `B/interp/RulePredicate.kt`, `J/interp/JIRRuleForms.kt` | Full signed NNF; group-to-full-leaf lookup; full action IDs |
| New `B/interp/StaticOperation.kt` | Logical read/write/condition descriptors and stable operation addresses |
| `J/interp/JIRStatementForms.kt`, `JIRBoundaryForms.kt`, `JIRCallPlanBuilder.kt` | Build local descriptors next to existing full forms |
| New `B/engine/Run1StaticService.kt` | Write/read, supply/interest and writer/support joins |
| New `B/engine/Run1PointResume.kt` | Exact stage and cleaner-step continuations |
| New `B/engine/Run1Grounding.kt` | Grounded bits and observers on existing ordinary bindings/support rows |
| `B/engine/RunMethodAnalyzer.kt` | Early effect publication, batches, resume queue and output layer checks |
| `RunMethodLocalServices.kt`, `RunMethodCallServices.kt`, `EngineAlgebra.kt` | Operation hooks, point resume, empty end-fact policy and run-1 control/static application |
| `SummaryStorage.kt`, `Run1Abstraction.kt`, request classes | Base-only group P/R; no static request base |
| `CalleeSubscriptions.kt`, `PublicationIndex.kt`, `SubscriptionManager.kt` | Register control ports; replay existing/late static conditional summaries |
| `RunEvent.kt`, ports, handlers, `SharedObjects.kt`, `RunManager.kt` | Counted static replies and run-scoped service lifetime |
| `B/driver/IterationPolicy.kt`, `IterationDriver.kt`, `HandOff.kt` | GROUP/FULL schedule, NORMAL-only first export and implicit static demand |
| `B/store/DemandStore.kt`, record readers | Group guidance over actual full inputs; typed old/new exclusions |

These are analyzer forms and engine metadata changes. They do not change
the IR representation.

## 3. Stable mark ownership before flattening

The base `JIRSemgrepRuleProvider` flattens `TaintRuleFromSemgrep` groups.
The specialized rule's `serializedId` and `info.ruleId` can be join-internal
IDs. They are not a reliable outer owner. Do not split generated names at
`#`, or use the consuming rule as the owner.

Collect creation provenance before that boundary. Keep it as a sidecar:

```kotlin
@JvmInline value class GroupId(val value: Int)
@JvmInline value class FullMarkId(val value: Int)

sealed interface GroupKey {
    data class Rule(val outerRuleId: String) : GroupKey
    data class Model(val declaredId: String) : GroupKey
    data object SharedModel : GroupKey
}

data class ExactMark(val group: GroupId, val full: FullMarkId)
data class MarkCreation(val fullIdentity: String, val owner: GroupKey)

data class LoadedRules<R>(
    val rules: List<R>,
    val markOrigins: List<MarkCreation>,
)

fun onGeneratedMarkCreated(outer: OuterRuleId, full: GeneratedMark) {
    origins.register(full.completeIdentity, GroupKey.Rule(outer.value))
}
```

`OuterRuleId` and `completeIdentity` above name the loader bridge to add.
They are not existing `GeneratedMark` properties. The loader must retain
the outer ID when it enters the rule-generation context. All children of
one join reuse that ID. Imported mark references perform a lookup; they
do not create a second ownership claim.

```kotlin
fun register(fullName: String, proposedOwner: GroupKey) {
    val old = ownerByName.putIfAbsent(fullName, intern(proposedOwner))
    require(old == null || old == intern(proposedOwner)) {
        "Conflicting owners for full mark $fullName"
    }
}

fun resolveModelMark(name: String, declared: GroupKey.Model?): ExactMark {
    check(ownershipCensusComplete) // declarations precede fallback
    if (declared != null) register(name, declared) // check conflicts too
    else if (name !in ownerByName) register(name, GroupKey.SharedModel)
    return resolveReference(name)
}

fun resolveReference(name: String): ExactMark =
    internFull(name, checkNotNull(ownerByName[name]) { "Unknown imported mark $name" })
```

Preload all generated creation claims and explicit model declarations before
SharedModel fallback. During prescan, a full form can reserve an opaque full
ID without assigning a fallback owner. Full-ID interning and owner assignment
are separate operations:

```kotlin
fun reserveFull(name: String): TaintMark = atomIds.full(fullNames.index(name))

fun finishOwnershipCensus() {
    for (name in undeclaredModelNames)
        if (name !in ownerByName) register(name, GroupKey.SharedModel)
    requireEveryEligibleFullHasOwner()
    ownershipCensusComplete = true
}
```

The atom table has separate Full, Group and control namespaces. Full forms
can hold their stable opaque full handles before the census ends. Reading
their owner/group view requires the completed census. Build the predicate
owner index only after this boundary. Do not create Group facts during
prescan. A late generation must publish its ownership claims before use;
it cannot move an already owned mark from SharedModel to another group.
Prescan name lookup reads the raw full ID/name table; it must not require
`catalog.key(atom)` to construct an owner pair before the census ends.

Keep the complete existing provider chain. Add a new-core adapter outside
it. Observe both normal and `allRelevant` results for every provider role:
method/entry/exit/static sources, method/entry/exit sinks, cleaners,
pass-through rules, rewriters, and effective `relevantTaintMarks`. Include generated
marks and specialization results. The adapter validates ownership and
catalog completeness. The new-core copy also applies the empty end-fact
policy in section 12; legacy selection and shared rule objects stay unchanged.

```kotlin
class OwnedRulesProvider(
    private val delegate: TaintRulesProvider,
    private val catalog: RuleMarkCatalog,
    private val sinkView: SinkRuleView,
) : TaintRulesProvider by delegate {
    // Apply the same observation wrapper to each role listed above.
    fun <R> owned(role: RuleRole, values: Iterable<R>): Iterable<R> =
        values.onEach { catalog.observeFullRule(role, it) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst,
                                   fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSink> =
        owned(RuleRole.METHOD_SINK,
            delegate.sinkRulesForMethod(method, statement, fact, allRelevant)
                .map(sinkView::method))
}
```

Provider overrides must call `owned` before returning. This sketch does
not replace those overrides with reflection. Method specialization can
change positions but must retain full names, occurrence IDs and owners.

## 4. Typed atoms and mode-aware full forms

Keep sorted integer payloads in the existing TAINT tries. Intern a typed
key to obtain each opaque payload ID. Equal integer group/full IDs must
not create equal atom IDs.

```kotlin
sealed interface MarkAtomKey {
    data object ControlZero : MarkAtomKey
    data class Group(val id: GroupId) : MarkAtomKey
    data class Full(val mark: ExactMark) : MarkAtomKey
}

interface RuleMarkCatalog {
    fun full(name: String): TaintMark       // stable full handle; see census above
    fun group(id: GroupId): TaintMark       // intern Group(id)
    fun key(atom: TaintMark): MarkAtomKey
    fun owner(full: FullMarkId): GroupId
    fun eligibleMembers(group: GroupId): FullMarkSelection
}

enum class MarkMode { GROUP, FULL }

fun runtimeAtom(atom: TaintMark, mode: MarkMode): TaintMark =
    when (val key = catalog.key(atom)) {
        MarkAtomKey.ControlZero -> atom
        is MarkAtomKey.Group -> atom.also { check(mode == MarkMode.GROUP) }
        is MarkAtomKey.Full -> when (mode) {
            MarkMode.GROUP -> catalog.group(key.mark.group)
            MarkMode.FULL -> atom
        }
    }
```

`TaintMark.ZERO` remains control. No group projection can produce it.
Run-1 `MarkSet` leaves contain Group atoms; later TAINT leaves contain Full
atoms. `MarkSet` remains an ordinary payload set. It must no longer serve
as the FLOW exclusion algebra.

Keep cached `PathEdge`, `CompiledEdge`, full NNF and retained full source/action
targets unchanged. Pass the run mark view into execution gates:

```kotlin
class RunMarkView(val mode: MarkMode, val catalog: RuleMarkCatalog) {
    fun literal(fullAtom: TaintMark): TaintMark = runtimeAtom(fullAtom, mode)
    fun target(fullAtom: TaintMark): TaintMark = runtimeAtom(fullAtom, mode)
}

class MarkGate(
    private val fromFull: MarkSlot,
    private val toFull: MarkSlot,
    private val view: RunMarkView,
) {
    // Match the runtime input selector; execute the declared from -> to relation.
    // Identity FLOW retains the incoming atom. T -> U maps owner(T) -> owner(U)
    // only in GROUP mode. CONTROL_ZERO keeps its control meaning.
}
```

The base `PathEdge` has one compiled payload and method caches have direction
keys. Do not put a projected GROUP payload into either full cache. If a new
cached selector view is later needed, its explicit key must contain mark mode.
Use the existing managed form lifetime; add no cache-manager rewrite.

The execution view above reads full primitive forms. Group guidance in a
later run uses a separate selector intersection, not `runtimeAtom`:

```kotlin
fun meetActual(guide: Selector, actual: ExactMark): ExactMark? = when (guide) {
    is Selector.Group -> actual.takeIf { it.group == guide.id }
    is Selector.Full -> actual.takeIf { it == guide.id }
}
```

Thus a FULL-mode guide can contain Group(g), while an executable FULL-mode
TAINT fact contains Full(g,T). The intersection keeps the actual full T.

## 5. FLOW exclusions, conditions and cleaners

Use separate group and full exclusions. Intern normalized values with the
existing AP manager lifetime:

```kotlin
class MarkExclusions private constructor(
    val groups: GroupSet,
    val fulls: FullMarkSet,
) {
    fun excludes(mark: ExactMark): Boolean =
        mark.group in groups || mark.full in fulls

    // Normalize: remove fulls whose owners are already in groups.
    fun union(other: MarkExclusions): MarkExclusions = normalized(
        groups + other.groups,
        fulls + other.fulls,
    )

    fun intersect(other: MarkExclusions): MarkExclusions = normalized(
        groups intersect other.groups,
        (fulls intersect other.fulls) +
            fulls.filter { catalog.owner(it) in other.groups } +
            other.fulls.filter { catalog.owner(it) in groups },
    )

    fun isSubsetOf(other: MarkExclusions): Boolean =
        groups.all { it in other.groups } &&
            fulls.all { other.excludes(ExactMark(catalog.owner(it), it)) }
}
```

The subset test is safe under catalog extension. Do not discard a whole
group restriction because a finite list happens to contain its currently
registered members. A later eligible full member must keep the old group's
meaning. Update FLOW merge/subsumption, `Reference`, `MarkGate`, cleaners,
summary matching and native/reversed record readers together. Field E stays
separate.

Keep the full signed NNF. The base exact-mark leaf index needs an additional
owner-group index over the same leaf IDs:

```kotlin
fun RulePredicate.matchingRuntimeLeaves(base: AccessPathBase, atom: TaintMark?,
                                      visit: (Int, Pattern) -> Unit) {
    when (val key = atom?.let(catalog::key)) {
        is MarkAtomKey.Group -> leavesByBaseAndOwner(base, key.id, visit)
        else -> matchingLeaves(base, atom, visit)
    }
}
```

Build this group index from already normalized full leaves. Preserve each
position, full literal, ordinal, NNF branch and `ProofKey`. Do not fold T
and U together because their groups match. `P(T) AND NOT P(T)` still folds
to false. Eligible residual negatives keep the spec's possible evaluation.

```kotlin
// Input is the already admitted affected piece from the normal cleaner split.
// Emit disjoint pieces unchanged outside this function.
fun selectedClean(input: Facts, fullT: ExactMark, at: OperationInput) {
    if (!config.run1) return fullSelectedClean(input, fullT, at)
    val g = fullT.group
    when (input) {
        is FlowTree -> {
            needGroups(at, input, GroupSet.of(g)) // before mx/delta tests
            emit(input.withMarkExclusion(input.markExclusion + g))
        }
        is TaintTree -> {
            retainLocalGroupObligation(at, g)
            emit(input.withLayer(Layer.DEMAND))  // no selected group kill
        }
        is Reach -> emit(input)
    }
}
```

Keep F74/F75 field-cleaner lowering and action order. `RemoveAllMarks` can
kill at its admitted locations. A merely possible group condition does not
authorize a definite conditional RemoveAll kill. A late reply resumes the
operation input and executes the kill again.

## 6. Run configuration and initial descriptors

```kotlin
override fun fieldLimit(runIndex: Int): Int {
    require(runIndex >= 1)
    return maxOf(1, runIndex - 1)
}

val RunConfig.markMode: MarkMode
    get() = if (run1) MarkMode.GROUP else MarkMode.FULL

// RunConfig input validation for this selected schedule:
require(fieldLimit == maxOf(1, index - 1))

val ApMode.markMode: MarkMode
    get() = if (run1) MarkMode.GROUP else MarkMode.FULL

// EdgeApplication constructs an immutable execution view for this call.
val gate = MarkGate(fromFull, toFull, ap.markView(mode.markMode))

fun resultLayer(premise: PremiseKey, value: Facts): Layer = when {
    !config.run1 -> value.layer
    value is TaintTree -> Layer.DEMAND
    premise.hasStaticOriginal() -> Layer.DEMAND
    else -> value.layer
}
```

Apply the layer rule at result/initial/publication boundaries, including
source, ND, entry and exit output. It changes only the layer.
Retain the selected field-write primitive and Empty E; do not add ANY
because an output is DEMAND.
Keep the selected FixedLimits fixtures on the same schedule. Do not put
a mutable current mark mode on ApManager or on a shared cached form.

Keep the nine scalar initial families in spec section 4. The ordinary
`addInitial` entry route starts ZERO and nonstatic families only. A static
point descriptor is interned by `ap.initial`, not added to `InitialFactStore`
as a completed entry analysis.

```kotlin
fun startOrdinaryInitial(initial: InitialAp) {
    check(config.run1) // later runs retain ordinary static query entry admission
    require(!initial.isStatic)
    validateRun1Initial(initial, fieldLimit = 1)
    val current = ops.startFact(initial)
    enqueueEntry(initial, forceRun1Layer(current))
}

fun installPointEdge(premise: PremiseKey, current: Facts, site: ResumeSite) {
    validateRun1PointPremise(premise)
    checkKinds(premise, current)
    nonZeroInitial = nonZeroInitial || !premise.isZero // frontier observation
    enqueueResume(site, premise, forceRun1Layer(current))
    // No addInitial(), startFact(), or entry-analysis completion bit.
}
```

Ordinary group STAR entry still makes an ANY current leaf. A point reply
must not perform that conversion. CleanerTemporary is legal only inside
its lowering; it is never an initial or summary base.

## 7. Logical static operations and resume addresses

Retain local static geometry and full rule metadata during prescan. Remove
transitive F/B/W closure. Add immutable descriptors beside analyzer forms:

```kotlin
sealed interface StaticOperation {
    data class Read(val id: OperationId, val from: Pattern,
                    val transfer: FullTransferId) : StaticOperation
    data class Write(val id: OperationId, val target: Pattern,
                     val transfer: FullTransferId) : StaticOperation
    data class MarkInput(val id: OperationId, val position: Pattern,
                         val fullLeaves: LeafSelection) : StaticOperation
}

data class OperationId(
    val form: FormKey,
    val stageOrdinal: Int,
    val action: ActionId?,
    val substep: Int,
)

sealed interface ResumeSite {
    data class StatementInput(val operation: OperationId) : ResumeSite
    data class BoundaryInput(val operation: OperationId) : ResumeSite
    data class CallStageInput(val operation: OperationId) : ResumeSite
    data class CleanerInput(val operation: OperationId,
                            val step: Int, val phase: CleanerPhase) : ResumeSite
    data class ReadOutput(val operation: OperationId) : ResumeSite
}
```

Build descriptors when lowering the actual code/model action. Do not call
every `to.base == STATIC` edge a write: preservation, identity and binding
edges also have that shape. Alias or write metadata alone registers no read.
Selected static cleaners and positive mark conditions register their actual
mark-input operation. Independent sources, ZERO and eligible negative
handling do not wait for a positive static read.

Stable addresses include parallel stage ordinals. `CallPoint.BOUND` alone
is insufficient. Cleaner phases distinguish input, temporary read, clean
and writeback. Rebuild full forms and resolve the operation by stable ID;
do not retain a whole call plan in each waiting callback.

Pass the operation input explicitly through `FormApplier`, `FactAlgebra`
and the result/request collector. A mutable actor-wide current-operation
field is unsafe when nested call and cleaner stages use the collector:

```kotlin
data class OperationInput(val site: ResumeSite, val premise: PremiseKey,
                          val facts: Facts, val support: ExistingSupportRef,
                          val callState: CallResumeState?)

fun collect(at: OperationInput, sink: ResultSink): ApOut = object : ApOut {
    override fun result(f: Facts) = emitAtOperationOutput(at, f, sink)
    override fun markRequest(atom: TaintMark) =
        needGroups(at, at.facts, GroupSet.of(requireRequestedGroup(atom)))
}
```

```kotlin
data class PointContinuation(
    val owner: MethodKey,
    val id: ContinuationId,
    val site: ResumeSite,
    val initial: InitialAp,          // surviving FLOW original
    val current: FlowTree,           // immutable conditional AP relation
    val support: ExistingSupportRef,
    val callState: CallResumeState?, // layer, guard state, identityCandidate
)
```

Extend owner-local work with a separate resume item. `EdgeDelta(node)` is
not enough: it would restart a call or cleaner chain. Reconstruct its exact
suffix and rerun the requested operation. An output-stage read admission
continues after that read; a mark reply starts before its sensitive operation.

## 8. Run-scoped static service and storage layout

Use one logical service with a short lock. Method actors own temporary
batches and continuations. The service owns merged semantic memberships.
Messages contain IDs and immutable deltas; callbacks execute outside its lock.

```kotlin
class Run1StaticService(private val geometry: StaticGeometry) {
    private val lock = Any()
    private val writes = RegionIndex<WritePresence>()
    private val groups = RegionIndex<GroupSet>()
    private val reads = RegionIndex<ReadRegistration>()
    private val interests = RegionIndex<GroupInterest>()
    private val writers = RegionIndex<WriterRowRef>()

    fun commit(batch: StaticBatch): List<StaticOwnerEffect> = synchronized(lock) {
        val effects = EffectBatch()
        registerReadsAndInterests(batch, effects)
        registerReachedWriterRows(batch.writerGrowth, effects)
        admitWritePresence(batch, effects)
        admitGroundedSupply(batch, effects)
        joinNewSupportWithReachedWriters(batch, effects)
        effects.detachImmutable()
    }
}

class StaticBatch {
    val readGrowth = ReadRegistrations()
    val interestGrowth = InterestUnions()
    val writeGrowth = WriteRegionUnions()
    val writerGrowth = ReachedWriterRefs()
    val supportGrowth = GroundedBindingGrowth()
    val groupGrowth = RegionGroupUnions()
    val isEmpty: Boolean get() = readGrowth.isEmpty && interestGrowth.isEmpty &&
        writeGrowth.isEmpty && writerGrowth.isEmpty && supportGrowth.isEmpty &&
        groupGrowth.isEmpty
}
```

| Stored item | Key/value and lifetime |
| --- | --- |
| Write presence | Symbolic AP region; run-1 only |
| Supplied groups | Region to GroupSet; no concrete full-mark memberships |
| Read registration | Owner/operation plus admitted region/image keys |
| Interest | Continuation ID, wanted groups, delivered groups by canonical region/image/support |
| Writer row | Reference to reached ordinary effect, original selector relation and existing support row |
| Grounding | Group/AP membership bits on existing N2 slices and complete predicate/ND binding rows |
| Continuation | One actor-owned saved operation input; no captured full method forms |

`RegionIndex` is a positional candidate index, not an applicability proof.
Use ordinary AP primitives for overlap and image construction. A class
EXACT point cannot supply a field. A broad class/root data region remains
symbolic; do not expand it into declared fields. Discover concrete fields
only through reached reads, writers and actual matching regions.

The lock protects indices and monotone membership updates. It does not
read another runner's mutable N2 or predicate store. Grounding messages
carry qualified row IDs and immutable matched-region/group deltas captured
by that row's owner. Program transfer, full AP composition, rule evaluation
and resume run at the addressed owner after unlocking. A queued immutable
slice is temporary message data, not another standing input history.

New writer rows join old complete support; new support joins old writer
rows. Register rows before both joins. `admitGroundedSupply` commits write
presence for each newly supplied region before any read/group reply, even
when the original write batch and the later grounding batch are separate.

A canonical image key contains the legal original descriptor, current AP
position/tail and support identity. It does not contain the identity of a
particular pool writer. Two writers can support the same group answer.
One group can still require distinct deliveries for different AP images.

## 9. Write-gated reads and both arrival orders

```kotlin
fun onReachedRead(op: StaticOperation.Read, control: ReachedControl) {
    temporaryStatics.readGrowth.addOnce(ReadRegistration(key, op.id, control))
}

fun joinReadWrite(read: ReadRegistration, write: WritePresence,
                 effects: EffectBatch) {
    for (cell in geometry.overlap(read.position, write.scope)) {
        val image = geometry.readFlowImage(read, cell)
        if (read.admitted.add(image.key))
            effects.add(ReadAdmitted(read.owner, read.operation, image))
    }
}

fun admitRead(read: ReadRegistration, effects: EffectBatch) {
    reads.registerOnce(read)
    for (write in writes.candidates(read.position)) joinReadWrite(read, write, effects)
}

fun admitWrite(write: WritePresence, effects: EffectBatch) {
    for (fresh in writes.addAndGetGrowth(write))
        for (read in reads.candidates(fresh.scope)) joinReadWrite(read, fresh, effects)
}
```

At the read owner, construct the static FLOW premise and execute the actual
read transfer. Apply its result filters and cut. Enqueue the result at the
read output stage:

```kotlin
fun onReadAdmitted(reply: ReadAdmitted) {
    withOperation(reply.operation) { op ->
        val initial = canonicalStaticFlowInitial(reply.image.originalField)
        val input = flowAtRead(initial, reply.image.matchedInputRegion, Layer.DEMAND)
        applyFullRead(op.transfer, initial, input, config.mode) { premise, image ->
            installPointEdge(premise, image, ResumeSite.ReadOutput(op.id))
        }
    }
}
```

For `x = C.f`, this starts `C.f FLOW -> x FLOW`. It does not start ZERO-to-x
or seed the method at entry. A pending read with no corresponding write is
dormant. A permitted static source/model/boundary value commits write
presence too. Commit presence before a group answer can wake its consumer.

`flowAtRead` takes the admitted read-side geometry. It constructs a legal
conditional FLOW view, not a FLOW EXACT leaf from an exact TAINT write.
The conditional slot remains C.f/STAR/FLOW; actual group refinement later
composes the exact/nonexact supplied region. Preserve the matched path and
read image rather than replacing them with the writer's broad target.

Execute `JIRRuleForms.readSources` independently of this gate. A configured
field-read source can emit ZERO-to-x/Group(g) even when no conditional C.f
read is admitted. A local x source does not by itself publish C.f supply;
only an actual static-target source/effect does that.

A positive static condition or selected cleaner registers its actual
`MarkInput` stage. A field operand uses the same write gate, then tests
its conditional field FLOW input at that stage. A bare class operand uses
direct rooted group matching; it never creates an abstract class initial:

```kotlin
fun onStaticMarkInput(op: StaticOperation.MarkInput, at: OperationInput) {
    when (val position = classifyStaticPosition(op.position)) {
        is StaticFieldPosition -> registerFieldMarkInputRead(op, at, position)
        is StaticClassPosition -> temporaryStatics.interestGrowth.union(
            ClassMarkContinuation(key, op.id, at.site, at.support, position),
            ownersOfPositiveFullLeaves(op.fullLeaves),
        )
    }
}

fun onClassGroupsReady(point: ClassMarkContinuation, supplied: RootedStaticSlice) {
    for (image in geometry.directClassImages(point.position, supplied)) {
        val tag = legalClassGroupInitial(image) // EXACT or STAR, never FLOW
        evaluateMarkInputAtStage(point.site, tag, image.taint.withLayer(Layer.DEMAND))
    }
}
```

The service interest union has typed FlowPoint and ClassMark variants.
Both use compatible rooted supply, but only FlowPoint has a FLOW relation
to compose. Class images use ordinary AP intersection and filters. Retain
all other NNF/ND guards. Negative and independent source branches keep
their ordinary admission. No class-point equality implies field coverage.

## 10. Early writes and ordinary live summaries

Intercept actual static effects at their logical output, before local
successor propagation. Read results with a nonstatic current base continue
normally. A static pass/identity contributes no transport summary.

```kotlin
fun emitOperationResult(op: OperationId, premise: PremiseKey, value: Facts,
                        support: ExistingSupportRef,
                        importedEffect: ReachedEffectId? = null) {
    if (config.run1 && value.base == STATIC) {
        if (staticOperations.isActualWrite(op) || importedEffect != null) {
            val effect = reachedEffect(op, premise, value, support)
            temporaryStatics.writeGrowth.add(effect.scope)
            temporaryStatics.writerGrowth.add(effect) // before fact dedup
            summaryDelta(premise, value.withLayer(Layer.DEMAND))
        }
        return // no local static successor branch in run-1
    }
    emitOrdinaryResult(premise, forceRun1Layer(value))
}
```

Use the base publication path:

```text
RunMethodAnalyzer.summaryDelta
  -> retainSummary / RunSummaryStore.add        // raw effect retained
  -> queuePublications / addDemand              // selected guidance
  -> pending Publication
  -> flushPublications at method-queue-empty
  -> SummaryStorage.publish
  -> ordinary caller subscribers and replay
```

`RunSummaryStore.addDemand` alone is insufficient. A support update must
reach the static service even when `summaries.add` returns null. Immediate
effect storage does not mean immediate unbatched notifications.

At the caller, use ordinary call bindings/actions. A resulting static
conclusion is another reached DEMAND effect. Publish it at that stage; do
not run it through the remaining caller CFG. Preserve source, argument,
guard and ordinary caller binding support. An early effect proves no
normal exit or caller continuation.

Retain the imported early-effect ID in the short call frame/PlanFact while
applying that suffix. A BIND_BACK identity is not a new write, but it must
not erase the fact that this value came from a callee write. Pass the ID to
`emitOperationResult`; ordinary identity inputs have no imported effect ID.
This tag stays within the call stage machinery, not along method CFG edges.

Do not generate `C.f -> C.f` pass summaries or an executable `S.* -> S.*`
default in run-1. Retain productive same-field mark/group rewrites.

## 11. Grounded input and writer support

The base's initial membership and accepted predicate inputs are conditional
analysis. They are not proof of supplied groups. Add support bits on existing
ordinary input slices and compatible ND binding rows:

```kotlin
data class N2RowRef(val ownerRunner: RunnerId, val callee: MethodKey,
                    val handle: Int)
data class InputSliceRef(val row: N2RowRef, val original: InitialAp,
                         val matchedRegion: ApRegionKey)

sealed interface GroundedSliceRef {
    data class Ordinary(val slice: InputSliceRef) : GroundedSliceRef
    data class Static(val slice: RootedStaticSliceRef) : GroundedSliceRef
    data class Local(val input: ExistingRuleInputRef) : GroundedSliceRef
}

sealed interface ExistingSupportRef {
    data class EligibleSource(val action: ActionId,
                              val permission: SourcePermission) : ExistingSupportRef
    data class Input(val slice: InputSliceRef) : ExistingSupportRef
    data class Static(val slice: RootedStaticSliceRef) : ExistingSupportRef
    data class Predicate(val row: PredicateCombinationRef) : ExistingSupportRef
    data class Nd(val row: NdCombinationRef) : ExistingSupportRef
}

data class WriterRowRef(
    val effect: ReachedEffectId,
    val original: PremiseKey,
    val support: ExistingSupportRef,
)
```

Qualify N2 handles by their runner and callee. Borrow its current Facts;
do not copy them into another input history. A slice retains actual AP
matching, complete CallerRef, layer and call stage. Grounded memberships
are merged bits alongside those rows.

```kotlin
fun onRootedOrdinaryInput(slice: InputSliceRef, growth: GroupSet) {
    val fresh = grounding.inputs(slice).unionAndGetGrowth(growth)
    if (fresh.isEmpty) return
    joinExistingWriterRows(slice, fresh)
    joinExistingPredicateAndNdRows(slice, fresh)
    joinExistingOutgoingN2Rows(slice, fresh)
}

fun onReachedWriter(row: WriterRowRef) {
    ownedWriterRows.register(row)
    temporaryStatics.writerGrowth.add(row)
}

// The static service addresses this probe to the existing support-row owner.
fun onWriterProbe(probe: WriterSupportProbe) {
    checkOwns(probe.support)
    for (binding in grounding.completeBindings(probe.support))
        sendCounted(WriterBindingReady(probe.writerOwner, probe.writer,
                                     binding.snapshotMatchedSlices()))
}

// The write owner composes immutable matched inputs after the reply arrives.
fun onWriterBindingReady(reply: WriterBindingReady) {
    val row = ownedWriterRows[reply.writer]
    for (image in composeGroundedWriter(row, reply.binding))
        offerGroundedStaticGroups(image.scope, image.groups, reply.binding)
}
```

`completeBindings` means the existing source permission or a complete actual
ordinary/ND join, not a method-wide slot mask. Predicate/ND rows retain
their positive leaf indices, OR choice, AP matches and compatible call
binding. Reuse their existing combinations and add a grounded-member mask:

```kotlin
fun groundMember(join: ExistingJoinRow, member: Int, input: GroundedSliceRef) {
    require(join.acceptsTheSameBinding(member, input))
    join.groundedMembers.add(member)
    if (join.groundedMembers.containsAll(join.requiredMembers))
        join.publishFreshGroundedGroups()
}
```

Do not combine `M(srcG, clean)` with `M(clean, srcH)` to satisfy a guard
`p/G AND q/H`. Both members must satisfy one complete ordinary join. The
global static pool may forget that origin only after the writer is grounded.
Do not label every method entry or every CFG edge with a new support graph.

Source permissions seed support. An abstract FLOW value never supplies a
group. An original-slot request can start a conditional group initial but
cannot mark its input slice grounded. A source with a group-dependent
predicate retains the existing predicate support. Its ZERO shape alone
does not qualify it as an unconditional source root.

```kotlin
fun sourceSupport(action: SourceAction, original: PremiseKey,
                  predicate: PredicateCombinationRef?): ExistingSupportRef = when {
    predicate != null -> ExistingSupportRef.Predicate(predicate)
    original.isZero -> ExistingSupportRef.EligibleSource(action.id,
        requireActualSourcePermission(action))
    else -> supportForCompleteOriginal(original)
}
```

The ordinary premise and reached conclusion already encode the mark
relation. Do not store a second hand-built group transform. For a g-to-h
rewrite the support contains g and the conclusion contains h. The operation
registers any required input group interest at its actual input point.
Do not infer a request for g from an output h as if the row were identity.

The mark result is not the whole writer image. Compose each matched rooted
input region with the reached ordinary effect before offering pool supply:

```kotlin
fun composeGroundedWriter(row: WriterRowRef,
                          binding: CompleteGroundedBinding): List<RegionGroupDelta> =
    applyActualCompleteSummary(
        binding.borrowMatchedInputs(), row.original, row.effect.borrowConclusion(),
        config.mode,
    ).map { result ->
        RegionGroupDelta(scopeOf(result), groupsOf(result))
    }
```

Use ordinary singleton/ND application, full filters and the actual AP image.
For an exact rooted p/g meeting a conditional p/STAR-to-C.f/STAR relation,
do not promote the result to broad C.f solely because the writer target is
STAR. The actual ordinary composition decides its exact/nonexact output.
Pool supply is indexed by those output regions, not just `effect.scope`.

For `C.f = C.g`, register a forward writer subscriber over rooted C.g groups:

```kotlin
fun onStaticCopy(writer: WriterRowRef, sourceScope: StaticScope) {
    statics.subscribeWriter(sourceScope, writer) // all available input groups
}

fun onWriterInputGrowth(writer: WriterRowRef, input: RegionGroupDelta) {
    for (image in applyWriterSummary(writer, input))
        offerGroundedStaticGroups(image.scope, image.groups, writer.support)
}
```

A new writer joins old supply; new supply joins old writers. A rootless copy
cycle publishes no group. An ordinary-origin FLOW writer can receive an
interest for g through Base P/R. A mark-changing writer requests its real
input selector, not its output group as if the operation were identity.
Register the relation when lowering the operation; full T-to-U remains
available for run-2.

## 12. Ignore sink end-fact actions in every run

The latest user decision supersedes the guarded end-GEN work in this
iteration. Treat `trackFactsReachAnalysisEnd` as empty for method, entry
and exit sinks in run-1, run-2 and all later runs. Keep normal sink matching,
full predicates, finding identities and `Seed.PredicateSink` discovery.
Source actions, including conditional sources, remain enabled.

Apply the policy before serialized action resolution as well as before full
form validation. Clearing only `JIRRuleForms.endFacts()` is too late: the
base validator also reads end-action positions and can reject a sink because
of an action that should now be ignored.

```kotlin
fun withoutEndFacts(rule: SerializedItem): SerializedItem = when (rule) {
    is SerializedRule.Sink -> rule.copy(trackFactsReachAnalysisEnd = emptyList())
    is SerializedRule.MethodEntrySink -> rule.copy(trackFactsReachAnalysisEnd = emptyList())
    is SerializedRule.MethodExitSink -> rule.copy(trackFactsReachAnalysisEnd = emptyList())
    else -> rule
}

interface SinkRuleView {
    fun method(rule: TaintMethodSink): TaintMethodSink
    fun entry(rule: TaintMethodEntrySink): TaintMethodEntrySink
    fun exit(rule: TaintMethodExitSink): TaintMethodExitSink
}

object EmptyEndFacts : SinkRuleView {
    override fun method(rule: TaintMethodSink) =
        rule.copy(trackFactsReachAnalysisEnd = emptyList())
    override fun entry(rule: TaintMethodEntrySink) =
        rule.copy(trackFactsReachAnalysisEnd = emptyList())
    override fun exit(rule: TaintMethodExitSink) =
        rule.copy(trackFactsReachAnalysisEnd = emptyList())
}
```

Use the first visitor on new-core configuration inputs before their resolver
binds actions. Preserve the outer ownership sidecar when mapping Semgrep
rule trees. Use the second visitor at the complete provider boundary for
bound/model-generated sink rules. Build new immutable views; do not mutate
the shared legacy configuration or its bound caches.

```kotlin
val newCoreInputs = rawInputs.mapSinkItems(::withoutEndFacts)
val fullProvider = buildExistingProviderChain(newCoreInputs)
val newCoreProvider = OwnedRulesProvider(fullProvider, catalog, EmptyEndFacts)
```

Collect effective relevance after this policy. A mark mentioned only by an
ignored end action does not add an eligible full member, static descriptor
or group interest. Keep a mark if a condition/source/other retained action
also uses it. Creation provenance can remain registered without making that
mark eligible.

With the effective empty lists, `JIRRuleForms.endFacts()` yields no edges;
`JIRCallPlanBuilder` adds no END_FACTS action stage from them. Entry/exit
GEN loops are empty. Their reversed action-trigger paths disappear too.
Do not remove the separate backward sink-seed path.

```kotlin
check(compiledSink.endFacts.isEmpty())
check(EmptyEndFacts.method(boundSink).trackFactsReachAnalysisEnd.isEmpty())
// No WaitingGen, pendingGen, GroundedGenReady or end-action support labels.
```

Use the same policy in the ordinary semantic reference and differential
fixtures. Keep the project's strict main finding comparison unchanged;
report every missing/additional identity and attribute scope differences
with evidence. Do not silently filter the finding comparator. Historical
project numbers have not been measured with this policy. Do not claim
coverage of disabled end-fact-dependent flows. The earlier deferral
choice needs no runtime implementation while this action list is empty.

## 13. Direct point refinement uses AP composition

An interest identifies the original FLOW slot and its actual operation input:

```kotlin
fun needGroups(at: OperationInput, input: Facts, wanted: GroupSet) {
    val flow = input as? FlowTree ?: return
    when (val origin = at.premise.singleFlowOriginalOrNull()) {
        is OrdinaryBaseOrigin -> temporary.offer(origin.base, wanted)
        is StaticOrigin -> temporaryStatics.interestGrowth.union(
            continuation(at, origin, flow), wanted,
        )
        null -> Unit // ZERO, group and complete ND originals do not climb
    }
}
```

Join both supply/interest arrival orders. Materialize only supplied wanted
groups at compatible AP regions. Before scheduling, commit delivered groups
for each canonical original/current image and support key.

Do not replace the mark on a FLOW tree. Concrete STAR current leaves are
illegal. Compose the actual supplied TAINT region with the saved conditional
FLOW relation through ordinary AP summary application:

```kotlin
fun materialize(point: PointContinuation, supplied: RegionGroupSlice) {
    val actual = supplied.toTaintFacts(catalog, Layer.DEMAND)
    val tag = canonicalRun1GroupInitial(point.initial, supplied)
    val relation = point.current.withMarkExclusion(MarkExclusions.EMPTY)
    ops.applySummary(actual, point.initial, relation, config.mode,
        resultOnly { image ->
            // This materialization view ignores request mx. Ordinary rule,
            // cleaner and record execution still applies its real exclusions.
            installPointEdge(tag, image.withLayer(Layer.DEMAND), point.site)
        })
}
```

`canonicalRun1GroupInitial` uses the selected projection: exact supply can
use EXACT; nonexact field supply uses C.f/STAR/Group(g), never ANY. The
current result can be EXACT, ANY or MUST as the AP primitive requires.
No entry conversion is repeated. Replace the original FLOW selector in
the premise with the selected group selector, so K6 remains true.

Keep each genuine cleaner/overwrite after the resume point. Do not clear
field E in ordinary later-run operations. Run-1 inputs already obey Empty E.
Do not use a point reply to create a method-entry analysis or a source hit.

## 14. Ordinary Base requests and the static-origin boundary

Simplify the base request types:

```kotlin
data class BaseSlot(val base: AccessPathBase) {
    init { require(base.isOrdinaryNonstaticNonzero && base !is CleanerTemporary) }
}
typealias RequestRows = Map<BaseSlot, GroupSet>

private var temporary = RequestStorage()

fun commitRequests() {
    if (temporary.isEmpty) return
    summaryStorage.commit(temporary)
    temporary = RequestStorage()
}
```

SummaryStorage owns cumulative R with no frozen static B. Keep both ordinary
joins: new request with old N2 inputs, and new input with old R. Concrete
group input answers immediately; FLOW only propagates the wanted group set
through its original ordinary Base. Request matching ignores field E/mx.
Use the base request set's sorted primitive-array and owner-builder logic
for `GroupSet`, with GroupId accessors instead of TaintMark accessors. Do
not put temporary sets into the AP manager's permanent intern tables. TAINT
leaf sets still hold opaque atom IDs; request sets hold dense GroupIds.

At a caller, a helper Base request can map to a static FLOW original:

```kotlin
fun routeMappedRequest(part: Subscription, groups: GroupSet) {
    when (part.ref.premise.singleFlowOriginalOrNull()) {
        is OrdinaryBaseOrigin -> offerCallerBaseRequest(part.ref, groups)
        is StaticOrigin -> registerStaticPointInterest(
            part.actualBoundInputSite, part.ref.premise, part.added, groups,
        )
        null -> Unit
    }
}
```

Do not create a static request row or send an entry `InitialAnswersIn` for
this static caller origin. Resume at the bound call input stage. Retain the
ordinary caller accumulator and candidate index; do not add a callee copy
of the same input history.

## 15. ZERO/control application of static summaries: run-1 only

Add a control subscription for each actual reached run-1 callee stage.
This is separate from backward `zeroOnly`:

```kotlin
data class Run1ControlPort(
    val caller: MethodKey,
    val callee: MethodKey,
    val call: CommonInst,
    val stage: OperationId,
    val layer: Layer,
    val inputRow: N2RowRef,
)

fun onAdmittedCalleeInput(admitted: N2Admission, stage: OperationId) {
    val part = admitted.current
    if (!config.run1 || !part.ref.premise.isZero || part.added !is Reach) return
    onCalleeControl(Run1ControlPort(part.caller, part.callee, part.ref.call,
                                   stage, part.linkLayer, admitted.rowRef))
}

fun onCalleeControl(port: Run1ControlPort) {
    check(config.run1)
    val actual = n2.borrow(port.inputRow)
    check(actual.ref.premise.isZero && actual.added is Reach)
    controlPorts.registerBeforeSnapshot(port)
    for (pub in summaryStorage(port.callee).staticConditionalSnapshot())
        applyStaticConditional(port, pub)
}

fun onStaticConditionalPublication(callee: MethodKey, pub: Publication) {
    if (!config.run1) return
    for (port in controlPorts.forCallee(callee)) applyStaticConditional(port, pub)
}
```

The snapshot borrows the existing PublicationIndex entries. Add an index of
static-premise members, not a second summary store. Both late publication
and late control arrivals use the same application function.
The control directory is a posting index over existing forward-ZERO N2
rows. It stores row references and stage addresses, not another Facts
accumulator. Expose the admitted N2 handle for this purpose.

```kotlin
fun applyStaticConditional(port: Run1ControlPort, pub: Publication) {
    check(config.run1)
    when {
        pub.premise.isSingletonStaticFlow ->
            composeConditionalAtCall(port, pub.premise, pub.conclusion)
        pub.premise.hasStaticGroupMembers ->
            registerStaticGroupMembersAtCall(port, pub)
        else -> Unit
    }
}
```

For FLOW, compose the conditional relation with the actual call bindings,
retaining its static original:

```text
callee: C.f FLOW -> ret FLOW
control at caller callee-stage
caller: C.f FLOW -> lhs FLOW, DEMAND
```

```kotlin
// No applySummary(Reach, staticPremise, ...): that ordinary match must fail.
resumeAtCalleeReturn(port.stage, premise = pub.premise,
                     current = pub.conclusion.withLayer(Layer.DEMAND))
```

For a group-valued member, the port registers a local join with rooted
compatible pool supply. ZERO does not provide g. For ND, retain every member
index and use the existing complete KaryJoin at this actual call stage.
Static members use their matched rooted field/group slice and retain that
static original. Nonstatic members require ordinary N2 matches at the same
call. Never satisfy an entire mixed ND premise from ZERO or global slot masks.

```kotlin
fun onStaticMemberSupply(port: Run1ControlPort, pub: Publication,
                         member: Int, supply: RootedStaticSlice) {
    val matched = matchStaticMember(pub.premise.member(member), supply) ?: return
    val binding = conditionalStaticBinding(port, matched) // original retained
    existingNdJoin(port, pub).addSubscription(member, binding)
}
```

In run-2 and later, use only the normal matcher: C.f requires an actual
matching C.f fact. The implicit demand is permission, not a fact. Do not
extend `matches()` with a global Reach-to-static rule. Keep backward
`zeroOnly`, nonzero-REACH guards and internal sink discovery unchanged.

## 16. Handoff, exact marks, records and schedule

At a complete run-1 barrier, export selected demands and independently valid
NORMAL records. Keep sink predicates/actions full. A static early source
effect reverses to an explicit ZERO exit:

```text
forward:  ZERO -> C.f / Group(g)
backward: entry C.f / Group(g), exit CONTROL_ZERO
```

Add one implicit demand view, without materializing method/field products:

```kotlin
class SharedRun2StaticDemand(private val catalog: RuleMarkCatalog) {
    fun matches(method: MethodKey, actual: FullQuery): Boolean =
        actual.base == STATIC && catalog.isEligible(actual.mark)

    // Symbolic contract for every method/group:
    // entry S/[]/ANY/Group(g), exit S/[]/STAR/Group(g).
    // Match against the actual Full(g,T), preserving that T and its AP.
}
```

Integrate this view into DemandStore lookup and publication permission.
It is not an executable summary or initial. A Group guide intersected
with actual Full(g,T) returns Full(g,T). The reverse containment case must
not fabricate T from FLOW.

```kotlin
fun admitLater(actual: Facts, guides: Sequence<DemandPattern>): List<InitialAp> =
    actualCanonicalQueries(actual) +
        concreteGuideIntersections(actual, guides, catalog)
```

Keep the base StrongQueries current-premise publication authorization.
Do not add a helper permission pass, a writer-search graph or a stricter
old-exit veto. Run-2 walks actual full-mark CFG/call/cleaner transfer.

```kotlin
if (result.status == RunStatus.COMPLETE) {
    // Existing persist filters NORMAL and existing record eligibility.
    shared.records.persist(config.direction, result.analyzers.map { it.key to it.summaries })
    val next = buildOrdinaryHandOff(config, result)
    if (config.run1) next.demand.installSharedStaticGroupView(catalog)
}
```

Remove only the base blanket `if (!config.run1)` around persistence. Check
that grouped/static-dependent/early results are DEMAND before this point.
Old Group mx retains its denotation in native and reversed record views.
A record excluding g cannot answer or close Full(g,T); actual analysis
remains available.

Run-2 seed extraction uses original full predicates and the complete eligible
catalog. Group membership selects all eligible full members when such seed
decoding is needed. Static demand matching itself filters actual inputs;
it does not create facts by enumerating the catalog.

Exact source hits retain full method key, statement, forward PathEdge and
action occurrence. Collect every matching source action before result
filtering, including actions with equal AP/full tuples. Keep explicit ZERO
unmarked. Those hits become exact `Seed.Source` permissions for run-3.

## 17. Owner queues, catalog growth and completion

Add counted effects such as `ReadAdmitted`, `PointGroupsReady`,
`GroundedBindingChanged` and `WriterGroupsReady` to the
ordinary owner event path. The method owner, not the static service, executes
AP operations and resumes call/cleaner work.

```kotlin
fun onMethodQueueEmpty(): Boolean {
    check(worklist.isEmpty && resumeQueue.isEmpty)
    flushPublications()
    commitRequests() // synchronous commit; then replace P

    if (!temporaryStatics.isEmpty) {
        val effects = statics.commit(temporaryStatics)
        temporaryStatics = StaticBatch()
        for (effect in effects) runnerPort.sendCounted(effect) // outside lock
    }

    return worklist.isEmpty && resumeQueue.isEmpty && pending.isEmpty &&
        temporary.isEmpty && temporaryStatics.isEmpty
}
```

Retain the parent Work reservation through commits, replacement and secured
child sends. Yielding a quantum is not a commit barrier. Commit delivered
bits before scheduling. Do not invoke actor callbacks under SummaryStorage
or static-service locks. Cancellation/rejected sends make the run incomplete;
do not persist its partial records.
The work loop drains both ordinary and resume items before this function.
Keep the existing ZERO priority; a quantum yield retains both temporary
batches until a real method-queue-empty boundary.

Prescan collects the complete full catalog before run-1. Stable ownership
cannot change. A late provider extension registers before use and schedules
dependent-view refresh as counted work. Keep group seed decoder registrations
by their real occurrence so newly eligible full members can join them.
Actual-query permissions remain additive. Do not let a late extension
silently change an already accepted completion barrier.

After all runners join and handoff/export completes, release pool regions,
continuations, pending reads, support observers and static batches. Keep
full catalog, full source metadata and valid records for later runs. Dormant
unanswered reads/interests are not queued work. Add no pending-memory cutoff
or global graph-change replay wave.

## 18. Removal list and retained machinery

```text
Remove from the selected base:
  Run1StaticProfiles transitive F/B/W plans
  FrozenStaticRequests and inherited static B
  static Run1Slot / wide-class static request branches
  static entry seeding in Run1Abstraction
  W-dependent executable S-root default in initialize()
  unchangedStaticInitial request suppression
  staticRootFact / withoutStaticRootFact geometry dispatch guards

Do not port from later commits:
  Run1StaticFlow / Run1ConcreteStaticFlow and packed static lanes
  StaticIdentityEntries / ConcreteStaticEntries / ConcreteStaticSubscriptions
  StaticNeedRouter / PendingNeedReplay / replay statistics
  CallerOwnedStaticRequests / FixedPrescanReachability / Run1PreparedInitials
  static need ports/events and callsite pending/answered request caches
  helper GroupGuideBuilder / reverse writer-search or provider graph

Retain:
  ordinary AddedFactAccumulator, CalleeSubscriptions and N2 candidate index
  InitialFactStore, MethodEdgeStore, RunSummaryStore, PublicationIndex
  SummaryStorage locking/register-before-snapshot/publication protocol
  Base P/R and ordinary input replay
  ConjunctionStore, KaryJoin and full predicate/ND support
  StrongQueries, ZERO discovery, source hits and exact Source seeds
  full forms, selective field writes and ordinary record readers
```

Remove obsolete static statistics and tests only with their implementation.
Retain pure AP laws and migrate applicable controls. Do not delete ordinary
input storage under an added-facts-removal label. No new actor input-history
cache or full source derivation tree is proposed.

## 19. Implementation sequence and validation

Each step starts with the relevant finite model and failing adapter controls.
Do not enable a partial static/group mode for project acceptance.

| Step | Concrete patch | Required controls before moving on |
| --- | --- | --- |
| 1 | Catalog, typed atoms, exclusions, mark view, full predicate group index | Outer join ownership, GeneratedMark, model/import ownership, same-group signed literals, mixed exclusions, ZERO |
| 2 | Run schedule and layer/initial gates | run-1 limit 1, run-2 limit 1, run-3 limit 2; exact Group initials DEMAND; no static entry/ANY initial |
| 3 | Stable operation descriptors and resume queue | Call-stage and field-cleaner reply resumes once at its actual input; form eviction/rebuild retains IDs |
| 4 | Grounding on ordinary bindings; empty end-fact rule policy | Same binding for ND; no cross-caller mask; old fact/new support; all sink roles/directions ignore end actions before validation |
| 5 | Static write/read service and early publication | No write/no read edge; both arrival orders; late support; complete raw/live publication; no rootless cycle groups |
| 6 | Point composition and control/static summary bridge | EXACT/MUST/ANY current images; static original retained; no ZERO mark supply; mixed ND complete; B2 ZERO does not satisfy static |
| 7 | Handoff, NORMAL export, full seeds and removals | Implicit all-method demand, actual full query retained, excluded group query not closed, exact all-source identity |
| 8 | Baseline suites and fixed projects | Same finding identity comparator; time/memory/work counts; every reached/completed/accepted run |

Use deterministic small fake-method integration fixtures for the complete
trace: source setter, two wrappers, getter, dead local, sink, pass-only call
and full backward source. Add variants with writer after sink, a real kill,
conditional source/ND, static copy cycle, late writer, two full marks in one
group, bare class point and broad class/root data.

Compare packed group joins with a scalar oracle over the same selected
grouped concept. Compare supply/read/interest arrival permutations. Assert
semantic rows and source/rule identities; do not assert callback counts.
Run the constructive proof audit before Kotlin acceptance. The production
AP/condition/call/queue adapters still require these runtime controls.

Benchmark only the authorized OWASP, conductor, ThingsBoard and Stirling-PDF
settings through `runProjectAnalyzer`. Keep source/spec/settings hashes and
the same reference finding comparator. The target is **run-1 engine time
within 60 seconds**. Profile the actual workload; include OOM/timeout and
per-iteration time, not only total runtime.

New useful counters are write regions, supplied region/groups, pending read
descriptors, point subscriptions/deliveries, writer rows, grounded input
memberships, complete ND bindings, ordinary P/R members,
raw/published summaries and suppressed static successor branches. Separate
standing memberships from attempted callbacks. Add no per-event trace history.

## 20. Evidence checkpoint

This task changes documentation only; existing proof evidence remains in
place. No runtime source, project run, profile or memory snapshot is produced
by this proposal.
The selected base's reached/completed/accepted runs remain:

```text
OWASP:        run-7 / run-6 / run-5
Conductor:    run-2 / run-1 / run-1   (run-1 engine 49.567 s)
ThingsBoard:  run-1 / none  / none
Stirling-PDF: run-2 / run-1 / run-1
```

The latest measured post-refactoring runtime remains `63d5481e4e4b69ec`:
OWASP **run-7/run-6/run-5**, conductor **run-1/none/none**, ThingsBoard
**run-1/none/none**, Stirling-PDF **run-2/run-1/run-1**. These values do not
measure the new design. See the spec's measurement checkpoint and linked
reports. No time or memory saving is claimed before implementation testing.
