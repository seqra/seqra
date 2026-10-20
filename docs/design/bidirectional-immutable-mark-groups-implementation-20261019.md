# Immutable mark groups: implementation proposal

Date: 19 October 2026. Status: implemented; the
[validation report](../reports/bidirectional-immutable-mark-groups-validation-20261019.md)
records the selected gate. The following sections retain the approved design;
statements about unexecuted work describe the proposal checkpoint.
The implementation commit is `029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
The [fresh project report](../reports/bidirectional-immutable-mark-groups-performance-20261020.md)
records the failed resource/finding acceptance gate and each latest reached
iteration. The [next proposal](bidirectional-run1-redundant-work-next-iteration-20261020.md)
contains separate, unimplemented changes.
Use ASD-STE100 Simplified Technical English. Code names keep their meanings.

Implement [Immutable mark groups and static demand guidance, revision 2.1](../../spec/run1-group-policy-and-ownership-census.md).
Keep the other contracts in [Mark groups and direct static handling](../../spec/grouped-run1-statics.md).
Follow [the proof guide](../../spec/proof-guide.md).
The implementation baseline is `b88c85d5b710d28293d7672af92b085a3838fb19`.
Keep its normal runtime provider factories and rule caches.

This proposal replaces the old primitive-metadata, census-lookup and census-
adapter implementation plans. It does not add another static-flow algorithm.
Snippets omit imports and unchanged methods. New helper names are local
implementation choices, not new analysis stages.

## 1. Changes and semantic boundary

| Part | Current B88 | Proposed implementation | Reason |
| --- | --- | --- | --- |
| Declaration setup | Walk methods/calls and bind nine rule roles for ownership discovery | Collect names from serialized items and owner sidecars; finalize once | Avoid method-dependent setup work and census-caused bound-cache population |
| MarkTable | Reserve a full handle, assign its owner later, track eligibility and seal | Intern immutable full/group/zero keys | Remove mutable owner and eligibility state |
| Primitive GROUP filter | Enumerate eligible full marks for each call | Call the existing MarkPolicy on the GROUP name | One suffix test instead of a member scan |
| Primitive policy plumbing | Extra policy type/checks were proposed | Reuse MarkPolicy, JIRTypeFilters.make and TypeFilter.and | Remove unnecessary policy machinery |
| Implicit static demand | List every group and cache a pair for each | Select one pair from the actual FULL input or entry premise | Avoid unrelated candidates and a global group inventory |
| Explicit requested groups | groupAtoms(GroupSet) converts a supplied set | Rename to atomsFor(GroupSet) | Keep this required conversion distinct from global enumeration |

**Selected input-contract changes:** all serialized names are declarations,
including inactive/false/end-only metadata. Each group must have one uniform
primitive flag. An undeclared executable full name fails. A custom provider
must supply declarations or explicitly declare that it creates no marks.

Do not infer SharedModel ownership before combining the complete provider
chain. Do not split a mixed group, rewrite a FULL name, or use OR over its
primitive flags to accept it.

Keep these mechanisms unchanged:

- Existing GROUP condition compiler and separate GROUP/FULL forms.
- Source admission, source AP transfer, action order and original identities.
- Empty sink end-action lists in every run.
- Field transfer, definite kills, empty run-1 E and layer-only demotion.
- Typed Group/Full exclusions, complete ND premises and actual-query rules.
- Static writer/read/supply joins, saved-point refinement and DEMAND layers.
- Base requests, their stores, commit/replace and method-queue-empty barrier.
- ZERO satisfies a static premise only in run-1; it supplies no group.
- Field limits `1,1,2,3,...`, later FULL runs and valid NORMAL persistence.
- Normal prescan, resolved call graph, runtime rule selection, IR and managed
  references.

No GroundingSupport, exact-field writer validation, new request routing,
static relay, cache access mode or provider warmup is part of this change.
Arbitrary FULL policies retain their FULL behavior. The GROUP filtering proof
applies to the primitive suffix policy and its existing conjunctions.

## 2. Code placement and ownership

| Location | Change |
| --- | --- |
| [common AP](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/RuleMarkCatalog.kt) | Keep MarkGroupKey/typed IDs; add immutable declaration keys; replace mutable MarkTable |
| [TaintConfiguration](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/TaintConfiguration.kt) | Collect raw names in loadConfig; export a detached snapshot |
| [TaintRulesProvider](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/taint/TaintRulesProvider.kt) | Add a direct declaration member |
| [outer rule loader](../../core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/SemgrepRuleLoader.kt) / [loaded rule](../../core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/TaintRuleFromSemgrep.kt) | Carry the actual outer primitive flag as metadata |
| [Semgrep provider](../../core/src/main/kotlin/org/opentaint/jvm/sast/rules/JIRSemgrepRuleProvider.kt) / [combined provider](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JIRCombinedTaintRulesProvider.kt) | Merge raw metadata without changing rule selection |
| [JIRBidiAnalysis](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt) | Finalize declarations, inject MarkTable and delete ownership census |
| [EffectiveBidiRulesProvider](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/EffectiveBidiRulesProvider.kt) | Replace OwnedRulesProvider observation; retain the effective rule view |
| [Filter](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Filter.kt) | Remove the FULL/GROUP policy branch |
| [DemandStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/DemandStore.kt) / [RunMethodAnalyzer](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt) | Select implicit demands from actual input/premise |

Put RawMarkDeclarations, GroupName, FullKey and MarkDeclarations beside the
existing MarkGroupKey in common AP. The spec's OwnerKey is this existing
MarkGroupKey; do not introduce a second owner hierarchy. These data types
have no JVM dependency. The current JVM-sast and query-language modules
already depend on common AP through their current dependencies.

Put the finalizer in the JVM bidi adapter. It can use PrimitiveTaintExt.
Keep the pure group-name encoder in common AP so fixtures use the same
encoding. Do not add a JVM dependency to common AP for one suffix constant.

## 3. Serialized metadata collection

### 3.1 Data boundary

```kotlin
// Common AP. Each exported collection is a detached immutable copy.
data class RawMarkDeclarations(
    val names: Set<String>,
    val explicitOwners: Map<String, MarkGroupKey>,
    val primitiveByOwner: Map<MarkGroupKey, Boolean>,
)

@JvmInline value class GroupName(val encoded: String)
data class FullKey(val group: GroupName, val name: String)

interface MarkDeclarations {
    fun fullKey(name: String): FullKey
}

class FrozenMarkDeclarations(keys: Map<String, FullKey>) : MarkDeclarations {
    private val byName = Collections.unmodifiableMap(LinkedHashMap(keys))
    override fun fullKey(name: String): FullKey =
        requireNotNull(byName[name]) { "Undeclared executable mark: $name" }
}
```

Raw metadata contains no bound rule, method, statement, condition tree or
serialized payload. It contains no final SharedModel assignment.

### 3.2 Visit the loaded serialized configuration

Add an ordered name set to TaintConfiguration. Visit each item as loadConfig
loads it, before relevance or method specialization. Keep the current
addRules calls and their order unchanged.

```kotlin
private val declaredNames = linkedSetOf<String>()

fun loadConfig(config: SerializedTaintConfig) {
    collect(config)
    // Keep the existing nine addRules calls here, in their current order.
}

private fun collect(c: SerializedCondition?) {
    when (c) {
        is SerializedCondition.Not -> collect(c.not)
        is SerializedCondition.And -> c.allOf.forEach(::collect)
        is SerializedCondition.Or -> c.anyOf.forEach(::collect)
        is SerializedCondition.ContainsMark -> declaredNames += c.tainted
        else -> Unit
    }
}

private fun collect(item: SerializedItem) {
    when (item) {
        is SourceRule -> {
            collect(item.condition)
            item.taint.forEach { declaredNames += it.kind }
        }
        is SinkRule -> {
            collect(item.condition)
            item.trackFactsReachAnalysisEnd.orEmpty().forEach {
                declaredNames += it.kind
            }
        }
        is SerializedRule.Cleaner -> {
            collect(item.condition)
            item.cleans.forEach { it.taintKind?.let(declaredNames::add) }
        }
        is SerializedRule.PassThrough -> {
            collect(item.condition)
            item.copy.forEach { it.taintKind?.let(declaredNames::add) }
        }
    }
    (item.info as? UserDefinedRuleInfo)?.relevantTaintMarks
        ?.forEach(declaredNames::add)
}

private fun collect(config: SerializedTaintConfig) {
    config.entryPoint.orEmpty().forEach(::collect)
    config.source.orEmpty().forEach(::collect)
    config.methodExitSource.orEmpty().forEach(::collect)
    config.sink.orEmpty().forEach(::collect)
    config.passThrough.orEmpty().forEach(::collect)
    config.cleaner.orEmpty().forEach(::collect)
    config.methodExitSink.orEmpty().forEach(::collect)
    config.methodEntrySink.orEmpty().forEach(::collect)
    config.staticFieldSource.orEmpty().forEach(::collect)
}

fun rawMarkDeclarations(): RawMarkDeclarations = RawMarkDeclarations(
    names = Collections.unmodifiableSet(LinkedHashSet(declaredNames)),
    explicitOwners = emptyMap(),
    primitiveByOwner = emptyMap(),
)
```

SourceRule includes SerializedStaticFieldSource. Null clean/pass kinds are
all-mark actions, not names. Read disabled END action kind strings and sink
relevance metadata, but do not bind/validate their positions or execute them.
Do not simplify a condition before this visitor: a false branch can still
contain a declared name.

Visit the existing successfully loaded configurations. Do not expand the
inventory by changing loader acceptance, rule-ID/tag filters or failed-rule
handling. An inactive loaded rule contributes metadata; a rule that the
existing loader never loaded is not added by this visitor.

This visitor only reads names. GeneratedMark values already have serialized
full strings here. Binding can select/copy positions; it cannot invent an
undeclared kind. Do not call MethodTaintConfigurationResolver from this path.

### 3.3 Carry the outer rule flag without changing marks

Add one trailing metadata field to TaintRuleFromSemgrep:

```kotlin
data class TaintRuleFromSemgrep<R>(
    val ruleId: String,
    val root: Structure<R>,
    val markOwners: Map<String, String> = emptyMap(),
    val primitiveByOwner: Map<String, Boolean> = emptyMap(),
)
```

At both SemgrepRuleLoader.loadNormalRule and loadJoinRule, attach the outer
RuleInfo flag to the conversion result:

```kotlin
val converted = convertNormalRuleWithStrategy(strategy, ctx, rule.rule)
val rules = converted.copy(
    primitiveByOwner = mapOf(rule.info.ruleId to rule.info.primitiveTracking),
)
// Return rules with the same rule.info.metadata as before.

// The join path uses convertJoinRuleWithStrategy(...) ?: return null,
// then applies the same copy using the JOIN rule's outer RuleInfo.
```

Keep RuleConversionCtx, markPrefix, modeModifier and child mark generation
unchanged. child already preserves topLevelRuleId, modeModifier and the
shared creation-owner map. The two outer loader paths cover matching,
taint and join conversion results. The new field carries the actual option;
it is not recovered by parsing a child ID or a generated FULL name.

Every loaded generated Rule owner must carry its known outer option. The
empty map default supports intermediate conversion objects; it does not
permit omission at the loaded-provider boundary. Update manual/test loaded-
rule builders to supply the flags. Add a loader/provider coverage control
against the original RuleInfo flags. The finalizer's inference for unclaimed
plain/model names is not a substitute for that adapter obligation.

Every join operand/state keeps one outer group. Imported names keep their
original explicit owner. The consuming rule does not overwrite that owner
or its primitive flag.

## 4. Provider metadata and one finalization

### 4.1 Use the existing interface and delegation

```kotlin
// A member of the EXISTING JVM TaintRulesProvider interface.
val markDeclarations: RawMarkDeclarations
    get() = error("This provider must declare its mark metadata")

// JIRTaintRulesProvider: read after all loadConfig calls have completed.
override val markDeclarations: RawMarkDeclarations
    get() = taintConfiguration.rawMarkDeclarations()
```

Do not freeze that snapshot in JIRTaintRulesProvider's constructor.
JIRSemgrepRuleProvider constructs its base before its init block loads
the serialized configuration. An eager snapshot there would be empty.

JIRSemgrepRuleProvider combines the base raw names with generated metadata:

```kotlin
override val markDeclarations: RawMarkDeclarations
    get() = mergeRaw(listOf(
        base.markDeclarations,
        RawMarkDeclarations(
            names = markOwners.keys.toSet(),
            explicitOwners = markOwners.mapValues { (_, owner) ->
                MarkGroupKey.Rule(owner)
            },
            primitiveByOwner = mergedOuterFlags.mapKeys { (owner, _) ->
                MarkGroupKey.Rule(owner)
            },
        ),
    ))
```

Build mergedOuterFlags from the loaded rules' primitiveByOwner maps. Reject
contradictory flags, as for conflicting markOwners. Keep markOwners sidecar
keys even when no retained executable item references the full name.

```kotlin
// JIRCombinedTaintRulesProvider: independent of CombinationOptions.
override val markDeclarations: RawMarkDeclarations
    get() = mergeRaw(listOf(base.markDeclarations, combined.markDeclarations))
```

EXTEND, OVERRIDE and IGNORE still select executable rules as before. Raw
declarations always include both loaded branches. Selection cannot remove
a declared name or change its owner.

Spring, MethodExit and StringConcat wrappers use their current `by base`
delegation. The selected anonymous Semgrep view also forwards this direct
interface member. Do not add another capability interface, factory tree,
lookup mode or wrapper-state adapter.

Unknown providers keep all their runtime roles. They must implement this
metadata member. A mark-free provider returns an explicit empty declaration;
the interface default must not return EMPTY or launch a census.

### 4.2 Raw merge preserves explicit claims

```kotlin
private fun <K, V : Any> claim(target: MutableMap<K, V>, key: K, value: V) {
    val previous = target.putIfAbsent(key, value)
    require(previous == null || previous == value) { "Conflicting declaration: $key" }
}

fun mergeRaw(parts: List<RawMarkDeclarations>): RawMarkDeclarations {
    val names = linkedSetOf<String>()
    val owners = linkedMapOf<String, MarkGroupKey>()
    val flags = linkedMapOf<MarkGroupKey, Boolean>()
    for (part in parts) {
        names += part.names
        names += part.explicitOwners.keys
        part.explicitOwners.forEach { (name, owner) -> claim(owners, name, owner) }
        part.primitiveByOwner.forEach { (owner, flag) -> claim(flags, owner, flag) }
    }
    return RawMarkDeclarations(
        Collections.unmodifiableSet(LinkedHashSet(names)),
        Collections.unmodifiableMap(LinkedHashMap(owners)),
        Collections.unmodifiableMap(LinkedHashMap(flags)),
    )
}
```

Do not assign SharedModel inside mergeRaw. For example, a plain rule provider
can mention a generated full name whose original owner is supplied by a
different Semgrep branch. The explicit claim must win over that unclaimed
reference. Contradictory explicit claims remain errors.

### 4.3 Finalize once from the complete root

```kotlin
// JVM adapter: reuse the current exact suffix constant.
private fun primitive(name: String): Boolean =
    name.endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)

// Pure common-AP helper; it needs no JVM type or policy dependency.
fun groupName(owner: MarkGroupKey, primitive: Boolean): GroupName {
    val payload = when (owner) {
        is MarkGroupKey.Rule -> "R:${owner.outerRuleId}"
        is MarkGroupKey.Model -> "M:${owner.declaredId}"
        MarkGroupKey.SharedModel -> "S:"
    }
    val mode = if (primitive) "%%primitive%%" else "%%ordinary%%"
    return GroupName("@group:${payload.length}:$payload;$mode")
}

// JVM adapter.
fun finalizeDeclarations(raw: RawMarkDeclarations, cancellation: Cancellation): MarkDeclarations {
    val flags = LinkedHashMap(raw.primitiveByOwner)
    val keys = linkedMapOf<String, FullKey>()
    for (name in raw.names + raw.explicitOwners.keys) {
        cancellation.checkpoint()
        val owner = raw.explicitOwners[name] ?: MarkGroupKey.SharedModel
        val value = primitive(name)
        claim(flags, owner, value) // checks the outer flag and group uniformity
        val group = groupName(owner, value)
        check(primitive(group.encoded) == value)
        keys[name] = FullKey(group, name)
    }
    return FrozenMarkDeclarations(keys)
}
```

The owner-kind tag and length prefix make the group encoding injective.
A raw rule ID that contains suffix/delimiter text cannot change the final
mode. Original FULL names and finding/source IDs do not change.

Read rootProvider.markDeclarations once, after configuration loading and
before constructing the executable MarkTable. Selection may have happened;
it does not change the complete raw metadata. Finalization retains only the
immutable full-key index. Temporary name/owner/flag builders do not survive
through it. No runtime member directory is built.

## 5. MarkTable as a small interner

### 5.1 Synchronization and keys

Use three ordinary key-to-ID/ID-to-key interners. Keep one MarkTable monitor,
as in the existing synchronized API; do not add a lock or reference manager
per key. Interner methods below are called while that monitor is held.

```kotlin
private class KeyInterner<K> {
    private val byKey = HashMap<K, Int>()
    private val keys = ArrayList<K>()
    fun intern(key: K): Int = byKey.getOrPut(key) {
        keys.size.also { keys += key }
    }
    fun key(id: Int): K = keys[id]
    fun existingId(key: K): Int = checkNotNull(byKey[key])
}

class MarkTable(private val declarations: MarkDeclarations) {
    private val groups = KeyInterner<GroupName>()
    private val fulls = KeyInterner<FullKey>()
    private val atoms = KeyInterner<MarkAtomKey>().also {
        check(it.intern(MarkAtomKey.ControlZero) == TaintMark.ZERO.id)
    }

    @Synchronized fun full(name: String): TaintMark {
        val key = declarations.fullKey(name)
        val group = GroupId(groups.intern(key.group))
        val full = FullMarkId(fulls.intern(key))
        return TaintMark(atoms.intern(MarkAtomKey.Full(ExactMark(group, full))))
    }
    fun mark(name: String): TaintMark = full(name) // existing compiler spelling

    @Synchronized fun group(id: GroupId): TaintMark {
        groups.key(id.value) // validate an existing group ID
        return TaintMark(atoms.intern(MarkAtomKey.Group(id)))
    }
    @Synchronized fun key(atom: TaintMark): MarkAtomKey = atoms.key(atom.id)
    @Synchronized fun owner(id: FullMarkId): GroupId =
        GroupId(groups.existingId(fulls.key(id.value).group))
    @Synchronized fun name(atom: TaintMark): String = when (val key = atoms.key(atom.id)) {
        MarkAtomKey.ControlZero -> "<zero>"
        is MarkAtomKey.Group -> groups.key(key.id.value).encoded
        is MarkAtomKey.Full -> fulls.key(key.mark.full.value).name
    }
}
```

Retain key-based fullId/groupId/isGroup/groupOf/runtimeAtom/coversAtom helpers.
They use MarkAtomKey rather than the old unresolved Handle.Full. Keep ZERO
handling and the FULL-mode rejection of executable GROUP atoms. A GROUP
selector covers exactly its owner's FULL atoms; a FULL selector does not
cover another full name. Keep MarkExclusions' existing typed operations.

All reads of an interner's mutable maps/lists use the same monitor. A full
key gets its group before its full/atom handles are published. Equal keys
return equal IDs; adding another key cannot change an old key or ID.
IDs remain opaque and local to one complete analysis.

### 5.2 Inject once and retain across numbered runs

```kotlin
class ApManager(
    val cancellation: Cancellation,
    refManager: RefManager = RefManager(),
    val marks: MarkTable,
) {
    // Existing AP state and operations are unchanged.
}

val declarations = finalizeDeclarations(taintConfig.markDeclarations, cancellation)
val ap = ApManager(cancellation, refManager, MarkTable(declarations))
```

Use the same ap/MarkTable for run-1, run-2 and later runs. Intern a declared
name on first actual use; no eager runtime atom population is needed.
A genuinely new configuration needs a new finalized snapshot and analysis
context. Do not append declarations to an active analysis or share opaque
IDs between contexts.

Remove the old mutable RuleMarkCatalog interface. The current source has no
consumer beyond MarkTable's implementation declaration. Do not
retain reserveFull/register/observeModelReference/markEligible/census APIs
as compatibility paths. Delete fixtureOwners and unresolved owner state.

Explicit group conversion remains:

```kotlin
fun atomsFor(groups: GroupSet): MarkSet =
    MarkSet(groups.ids.map { group(GroupId(it)).id }.toIntArray())
```

Update Run1StaticService.facts and Run1PointResume to use atomsFor. It visits
only the supplied GroupSet. It does not enumerate declarations or members.

## 6. Primitive tracking: one existing policy call

Only change the policy evaluation branch in Filter.keepPolicyMarks:

```kotlin
// Before: GROUP -> eligibleMembers(group) -> members.any(policy).
// After, for both FULL and GROUP:
val atom = TaintMark(id)
val keep = policy.keeps(atom, level)
```

Keep the surrounding loop, cancellation checkpoints, result interning and
the unchanged-set identity return. Keep JIRTypeFilters.make, its per-type
cache, Character/unboxing, array-depth flags and suffix test unchanged.
Keep TypeFilter.and unchanged.

```text
Type int[][]: primitive flags = [false, false, true]
Group ordinary: root/first array values pass; primitive value fails.
Group %%primitive%%: the primitive policy accepts all three depths.
The ordinary path filter still decides which access paths are valid.
```

The encoded group name now exposes the same primitive flag as every full
name in that group. Thus the current suffix policy works directly. Do not
add GroupMarkPolicy, custom-policy registration, policy-kind checks,
special AND code, property metadata or any member fallback.

## 7. Static demands without a group inventory

### 7.1 Store one flag and select from the query

Replace SharedStaticGroupDemand and patternsByGroup with an immutable enabled
flag plus the existing AP manager reference. Keep Builder.includeStaticGroups
as the current handoff switch. Its lifetime and enable conditions do not change.

```kotlin
private fun implicitStaticPair(actual: Pattern): DemandPattern? {
    ap.cancellation.checkpoint()
    if (!staticGroupsEnabled || actual.fact.base != STATIC) return null
    val atom = (actual.fact.mark as? MarkSlot.Concrete)?.mark ?: return null
    if (ap.marks.fullId(atom) == null) return null
    val group = MarkSlot.Concrete(ap.marks.group(ap.marks.groupOf(atom)))
    return DemandPattern(
        entry = Pattern(PathFact(STATIC, emptyList(), Tail.ANY, group), ExclusionSet.Empty),
        exit = Pattern(PathFact(STATIC, emptyList(), Tail.STAR, group), ExclusionSet.Empty),
    )
}

fun near(method: MethodKey, actual: Pattern): List<DemandPattern> {
    ap.cancellation.checkpoint()
    val stored = byMethod[method]
        ?.around(actual.fact.base, actual.fact.path.toIntArray()).orEmpty()
    return when (actual.fact.base) {
        AccessPathBase.Zero -> stored + zeroDemand
        STATIC -> stored + listOfNotNull(implicitStaticPair(actual))
        else -> stored
    }
}

fun covering(method: MethodKey, initial: InitialAp): List<DemandPattern> {
    ap.cancellation.checkpoint()
    val stored = byMethod[method]
        ?.lookupPrefixes(initial.base, initial.path?.toIntArray() ?: EMPTY_PATH).orEmpty()
    return stored + listOfNotNull(implicitStaticPair(initial.toPattern()))
}
```

This is one DemandPattern with an entry and an exit. It is guidance, not
two facts or an executable summary. It selects g from the actual incoming
FULL leaf for emission, and from the entry initial for restriction. It
never selects g from an output mark.

Keep the existing explicit trie, payload ownership and AP tests. The pair
does not bypass emit/commonPart/restrict, tails, E/mx or layer checks.
FLOW, ZERO and nonstatic inputs contribute no implicit concrete-group pair.
Keep their ordinary explicit demands and actual-query behavior.

### 7.2 Change the two runtime call sites

```kotlin
// RunMethodAnalyzer.addLink: retain the existing sequence and set.
val guides = sequence {
    val found = LinkedHashSet<DemandPattern>()
    for (leaf in ops.leaves(added)) {
        ap.cancellation.checkpoint()
        found += checkNotNull(config.demand).near(key, leaf)
    }
    for (guide in found) {
        ap.cancellation.checkpoint()
        context.shared.abstractionStatistics?.intersection()
        yield(guide)
    }
}
val starts = checkNotNull(currentQueries).admit(added, guides)

// restrictBy: select by the complete ENTRY initial.
demand.covering(key, initial).asSequence().flatMap { pattern ->
    ops.manager.cancellation.checkpoint()
    ops.restrict(initial, facts, pattern)
}
```

Packed inputs can contain several actual groups. The existing leaf walk
collects their pairs; LinkedHashSet removes duplicate equal pairs. Do not
add a global group scan or a second packed-fact expansion.

Migrate DemandStore tests/oracles to pass the actual Pattern/InitialAp.
Delete the old base/path-only overloads: they cannot select the correct
group. Do not infer a group from a diagnostic universe or result fact.

### 7.3 Preserve independent actual-query publication

StrongQueries is unchanged. It admits actual current inputs in addition to
concrete guide intersections. Keep RunMethodAnalyzer.published's existing
priority:

```kotlin
when {
    config.run1 || (!forward && premise.isZero) ||
        currentQueries?.permits(premise) == true -> listOf(facts)
    else -> /* existing per-member restrictBy path */
}
```

For a premise in group g and output in group h, the implicit g pair alone
does not admit the h result. Explicit demands or the existing complete
actual-query permission can still admit it. Do not add a new guide veto
after the permitted branch. Keep complete ND permission checks.

## 8. Delete ownership census; keep runtime rule behavior

### 8.1 JIRBidiAnalysis

In gather, retain root resolution, prescan IDs/lambdas, selectRules and the
successfully joined ResolvedPrescanGraph. Remove only the ownership role
walk and scratch catalog:

```kotlin
// Keep:
taintConfig.selectRules(ids)
prescan = PrescanResult(ids, lambdas, roots, source.factTypeChecker,
    source.externalMethodTracker, graph)

// Delete:
// val census = OwnedRulesProvider(..., MarkTable())
// graph/root/declared-call/resolved-call/static-field ownership role walks
// census.observedFullNames() and PrescanResult.markNames
```

At prepareShared, finalize the root metadata before executable AP creation:

```kotlin
val declarations = finalizeDeclarations(taintConfig.markDeclarations, cancellation)
val ap = ApManager(cancellation, refManager, MarkTable(declarations))
val rules = EffectiveBidiRulesProvider(taintConfig.forBidirectionalCore(), ap.marks)
// Existing entries/interpreter/SharedObjects construction follows.
```

Delete observeModelReference loops, finishOwnershipCensus and the
BIDI_MARK_CATALOG loop over groupAtoms/eligibleMembers. Counts can come from
declaration collection sizes or interned-ID counts. They are diagnostics,
not runtime admission/completeness gates.

Keep entries' run1Adjacency argument, requireRun1CatalogTarget and
releaseRun1CatalogCoverage. They validate/release completed **call-graph**
coverage, not mark ownership. Keep their existing handoff lifetime. Do not
delete them because their names contain "catalog".

Preserve preparation and engine budget accounting. Report the new declaration
setup time separately; do not move it into the run-1 engine clock.

### 8.2 Effective rule view

Rename OwnedRulesProvider to EffectiveBidiRulesProvider to describe its
remaining duties. Remove its names set, owner-registration init, mark/
condition/action observation visitors, owned mapping and censusMethod.
Preserve the existing eager role-result materialization. The old owned(values)
evaluates the returned Iterable before form construction continues. Removing
it must not defer a custom provider's failure until after partial compilation.
Keep the six nonsink overrides with toList and no observation. Keep
groupConditions and the eager final empty-end copies for all three sink roles:

```kotlin
class EffectiveBidiRulesProvider(
    private val delegate: TaintRulesProvider,
    private val catalog: MarkTable,
) : TaintRulesProvider by delegate {
    internal fun groupConditions(cancellation: Cancellation) =
        GroupRuleConditions(catalog, cancellation)

    override fun sourceRulesForMethod(/* unchanged parameters */) =
        delegate.sourceRulesForMethod(/* same arguments */).toList()
    // Keep equivalent materialization for the other five nonsink roles.

    override fun sinkRulesForMethod(/* unchanged parameters */) =
        delegate.sinkRulesForMethod(/* same arguments */).map {
            if (it.trackFactsReachAnalysisEnd.isEmpty()) it
            else it.copy(trackFactsReachAnalysisEnd = emptyList())
        }
    // Keep equivalent final copies for method-entry and method-exit sinks.
}
```

Known providers' pre-resolution empty-end views stay in forBidirectionalCore.
The final copies still cover custom providers. Update references in
JIRRuleForms/JIRMethodEntries and tests. Remove GroupRuleConditions.selectedNodes'
isOwnershipCensusComplete check. Every full key now has a validated immutable
owner; do not replace the check with a new eligibility/grounding mechanism.

Keep runtime factories, binding caches, role ordering, original source/sink
rules, complete ND conditions and the existing GROUP condition normalizer.
Removing observation must not remove selected cleaner group requests/repair.

Removing census warming can change StringConcat's first cached raw rule.method.
The current new-core lowering uses the actual callee, actions and ordinals.
Keep that lowering; add a warm/cold semantic regression. Do not add a new
warmup, wrapper-state replay or StringConcat cache rewrite.

## 9. Fixture and custom-provider migration

Production and tests must use the same frozen ownership boundary. The current
StoreFixtures, EngineShellFixtures and FiniteProgramFixtures seal an empty
catalog and then auto-register model names. Remove that mode.

Declare each fixture's finite names/owners before manager construction:

```kotlin
val declarations = testDeclarations(
    TestMark("T", MarkGroupKey.Model("T")),
    TestMark("U%%primitive%%", MarkGroupKey.Model("U")),
    TestMark("later T", MarkGroupKey.Model("later")),
)
val ap = ApManager(cancellation, refManager, MarkTable(declarations))
val first = ap.marks.full("T")
// Later, even in run-2, this is first USE of an already declared name:
val later = ap.marks.full("later T")
```

testDeclarations validates the explicit complete inventory before creating
immutable keys. Common fixtures use the shared pure encoder and check the
same uniformity contract, without depending on the JVM module:

```kotlin
fun testDeclarations(vararg input: TestMark): MarkDeclarations {
    val flags = linkedMapOf<MarkGroupKey, Boolean>()
    val owners = linkedMapOf<String, MarkGroupKey>()
    val keys = linkedMapOf<String, FullKey>()
    for ((name, owner) in input) {
        val flag = name.endsWith("%%primitive%%")
        val oldFlag = flags.putIfAbsent(owner, flag)
        val oldOwner = owners.putIfAbsent(name, owner)
        require(oldFlag == null || oldFlag == flag)
        require(oldOwner == null || oldOwner == owner)
        keys[name] = FullKey(groupName(owner, flag), name)
    }
    return FrozenMarkDeclarations(keys)
}
```

TestMark is a test-only data class of name/owner inputs. Add a JVM adapter
control that compares this suffix/encoding with PrimitiveTaintExt and the
production finalizer. Do not construct arbitrary inconsistent FullKeys for
valid-input fixtures.

The helper does not allow new runtime names or mutate an active snapshot.
Pass the inventory through fixture constructors. For program-building helpers, collect
symbolic mark declarations before materializing AP facts, or supply the
complete inventory when constructing the fixture. Keep a test-owned list
for proof-only finite enumeration; do not restore a production member API.

Replace old mutable-owner/late-registration assertions with immediate owner,
stable-handle, undeclared-name and late-use-of-predeclared-name controls.
Keep existing fixture groups when choosing explicit test owners. A mixed
SharedModel test becomes a rejection case, not a reason to split groups in
production. Keep exact FULL custom-lambda controls; replace the old opaque
GROUP existential-lift assertion with uniform primitive cases.

J03ObservedDriverBridge currently builds markGroups with global groupAtoms
and eligibleMembers. Supply its read-only full-name-to-encoded-group map from
the test's declaration inventory/snapshot instead. Update its symbolic-demand
observation to the new enabled flag and test-supplied groups. Do not intern
every declared atom for observation or add a production member-enumeration API.

Custom JVM test providers declare their created names, including metadata-only
ones. A no-mark test provider returns explicit EMPTY. Add a negative setup
test for a provider with missing metadata; do not silently return EMPTY.

## 10. Deletion and retained-code checklist

| Delete | Retain |
| --- | --- |
| reserveFull/register/declareOwner/declareModel mutable runtime APIs | Frozen loader claims and immutable fullKey lookup |
| owners/eligible/fixtureOwners/unresolved Handle state and census seal | Typed IDs, ExactMark/MarkAtomKey and ZERO separation |
| eligibleMembers/knownMembers/global groupAtoms() | atomsFor(explicit GroupSet) |
| SharedStaticGroupDemand/patternsByGroup | One enabled flag and actual-query-selected paired demand |
| PrescanResult.markNames and all ownership-binding walks | Rule IDs, roots, lambdas, resolved graph and normal prescan |
| OwnedRulesProvider observation and censusMethod | Effective rule policy, final empty-END copies and GROUP compiler |
| isOwnershipCensusComplete checks and member-based primitive branch | runtimeAtom FULL validation, typed exclusions and current suffix policy |
| Old proposed census factories/modes/no-insert lookup/memo/replay adapters | Current runtime provider factories and ordinary caches |

Search production and fixtures for each removed API after migration. Remove
dead imports and old tests of the deleted mutation protocol. Do not add
aliases that keep the old ownership lifecycle executable.

## 11. Proof and implementation gates

The existing constructive [UniformMarkGroups model](../../spec/lean/ApSpec/UniformMarkGroups.lean)
and [audit](../../spec/lean/uniform-mark-groups-audit.lean) cover the selected
concept. The recorded audit has 565 owned declarations, zero unexpected
axioms and 20 executable controls. It has no policy-tag dependency.
No new Lean/Kotlin/project job is claimed by this proposal.

| Adapter obligation | Existing proof | Required implementation control |
| --- | --- | --- |
| Complete raw names and no binding | Serialized.binding_names_scanned / setup_scan_and_no_binding_queries | All nine lists, signed branches, false/END/relevance/sidecar-only names; zero bound-role callbacks |
| Uniform owner/property and encoding | validated_owners / uniform_group / encode_injective | Imports, joins, owner-kind/ID delimiter collisions and mixed-flag rejection |
| Stable typed keys/IDs | Interner.existing_slot_stable / intern_idempotent / projected_key_exact | Concurrent same-key interning, distinct FULL/GROUP/zero and no unknown name |
| Primitive equivalence and existing AND | full_group_policy / nonempty_scalar_eq_group / same_witness_and_scalar | Primitive/boxed/Character/arrays, both group flags, existing TypeFilter.and and complete Facts |
| Query-selected static demand | Guidance.emission_equivalence / restriction_equivalence / premise_member_union | Reference all-group pairs versus selected pairs on actual full inputs, packed groups, E/mx/tails/layers |
| No extra fact or publication veto | no_output_without_ordinary_result / unchanged_bypass_equivalence | FLOW/ZERO, cross-group output and complete actual-query/ND publication |

Exact Kotlin string encoding and serialized-field coverage are adapter duties.
The Lean proof does not prove arbitrary JVM provider code, whole-project
findings, termination, retained bytes or a 60-second bound. Add a constructive
model refinement first if implementation exposes a new semantic case. Do
not close a mismatch by changing fields, source transfer or static routing.

Implement in these reviewable steps:

1. Add raw metadata collection/flag transport/provider forwarding and controls.
   Keep the old runtime path until this boundary is complete.
2. Add immutable MarkTable injection and migrate fixtures. Remove mutable
   owner/eligibility/census methods and observation as one coherent change.
3. Replace the primitive member branch and both DemandStore query APIs.
   Keep existing policies, AP tests and actual-query publication.
4. Remove the ownership graph walks, scratch catalog and dead diagnostics.
   Verify that normal prescan/call-graph coverage and runtime caches remain.
5. Run focused adapter controls, then the existing four-task acceptance gate.
   Commit the implementation only when it is requested and the gate passes.
6. Repeat the four approved project configurations with the same main pin,
   inputs, heaps, timeouts and direct runProjectAnalyzer harness.

Use the workload/proof locks already required by task.md for actual jobs.
Retain historical proof receipts. Re-run the focused Lean target/audit only
when model/proof changes or required checks justify it.

The baseline acceptance tasks, from core, are:

```sh
./gradlew :opentaint-dataflow-core:opentaint-dataflow:test
./gradlew :opentaint-dataflow-core:opentaint-jvm-dataflow:test
./gradlew :test --tests '*JIRBidiSequenceAcceptanceTest' --tests '*BidiSignedRuleResolverTest'
./gradlew :opentaint-java-querylang:test --tests '*RuleTagsAndJoinTest'
```

Place new ImmutableMarkTableTest and ImplicitStaticGroupDemandTest controls in
common dataflow, so its unfiltered task includes them. Extend RuleTagsAndJoinTest
for the outer-flag transport checks, so its existing selector includes them.
Put new BidiMarkDeclarationsTest loader/provider/wrapper controls in root
application tests and run them explicitly:

```sh
./gradlew :test --tests '*BidiMarkDeclarationsTest'
```

TaintConfiguration and the combined/provider wrappers are in the separate
JVM-sast included build, above JVM dataflow. Do not add a reverse dependency
from JVM dataflow tests to that build. Root application tests already have
the required dependencies. Do not assume a filtered baseline task includes
a new class. Keep the baseline tests unfiltered where shown. The old total
of 2,063 passing tests is baseline evidence, not the new gate outcome.

Prioritize GroupedMarkViewsTest, TypeFilterTest, DemandStoreTest, HandOffTest,
NormalizedHandOffTest, GroupedStaticPipelineTest, GroupedSourceTransferTest
and SourceActionIdentityTest in common dataflow. In JVM dataflow, preserve
JIRTypeFiltersTest, GroupRuleViewsTest, JIRRuleViewCacheTest, JIRBidiSetupBudgetTest,
JIRBidiRealEntryTest and JIRStaticAliasMetadataTest; migrate OwnedRulesProviderTest
to the effective view and declaration boundary. Add a complete wrapper-chain
forwarding test and warm/cold StringConcat semantic comparison.
For a lazy custom role result that fails during iteration, retain the same
pre-form-construction materialization/failure boundary after observation is
removed.

## 12. Savings and measurement acceptance

For Q primitive GROUP policy calls and M catalog full marks, remove the
catalog/member scan term from those Q calls. Each primitive decision becomes
one current policy call and suffix test, independent of M. Preserve normal
fact/type traversal; this is not a constant-time claim for filtering a whole
packed fact.

Declaration work reads serialized syntax and distinct name/claim maps. It
does not multiply by reached methods, call sites or nine bound-role views.
Ordinary prescan/runtime binding still exists. Do not claim that the entire
measured bound-cache union disappears.

Implicit static candidates scale with groups present in the actual packed
input, not all declared groups. The same pair can be transiently constructed
at multiple queries; no new persistent memo is needed. Successful semantic
contributions remain equal to the enumerated reference. Candidate counts
and batch-dependent diagnostic callbacks need not be equal.

Required counting controls:

```text
20 fixed primitive GROUP calls, declared-name populations 51 and 1,172:
    full-member scans = 0
    owner/member probes for filtering = 0
    global group inventory scans = 0
    direct policy/name/suffix work counted normally

Declaration setup:
    bound method/static role callbacks caused by collection = 0
    bound cache insertions caused by collection = 0
```

These are local work bounds. Measure setup, engine and barrier clocks
separately. The target remains completion of run-1 engine within 60 seconds.
Compare strict finding identities against the approved main reference;
do not hide differences caused by the empty-END policy.

Latest baseline checkpoint, from the [B88 report](../reports/bidirectional-run1-normalized-groups-performance-20261019.md):

| Project | Latest reached / completed engine / accepted forward report | Run-1 engine |
| --- | --- | ---: |
| OWASP | run-1 / none / none | 906.998 s, TIMEOUT |
| Conductor | run-1 / none / none | 986.046 s, TIMEOUT |
| ThingsBoard | run-1 / none / none | 239.084 s, memory-guard OOM |
| Stirling-PDF | run-2 / run-1 / run-1 | 925.594 s, COMPLETE; run-2 TIMEOUT |

These results do not measure this proposal. For every new report, include
latest reached, completed engine and accepted forward run. A timeout/OOM
prefix is not a completed run or evidence of speedup. Keep the approved
four project configurations; capture profiles and memory-guard snapshots
as required by task.md. No new project run is part of writing this proposal.
