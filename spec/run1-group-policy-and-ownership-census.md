# Group primitive filtering and ownership census

Date: 19 October 2026. Revision: **1, selected next iteration**.
Implementation status: specification and finite models only. Runtime remains
`b88c85d5b710d28293d7672af92b085a3838fb19` (B88).
Use ASD-STE100 Simplified Technical English. Defined technical terms and code
names keep their meanings.

Follow [proof-guide.md](proof-guide.md). This document gives the complete
contract and implementation design for these two selected changes:

1. Use all known full marks and group metadata for JVM primitive filtering.
2. Avoid eager bound-rule cache insertion during ownership census.

The second change can still bind a rule transiently. This iteration does not
replace exact binding with a serialized-template mark scanner. Its required
saving is to avoid retaining census-only bound results in runtime caches.

This spec takes precedence over the membership, primitive-filter and census
parts of the earlier proposals. [grouped-run1-statics.md](grouped-run1-statics.md)
retains all other analyzer behavior. Earlier optimization lists do not select
additional changes. Kotlin snippets show proposed APIs and required control
flow; new helpers do not yet exist in runtime code.

## 1. Semantic scope and comparison

**Selected major semantic change:** all known full IDs participate in their
registered owner groups. Delete active-eligibility tracking. This is not an
equivalence claim against B88's eligible-member policy.

The metadata fast path and the census lifetime change must preserve the
**new all-known reference**. Census must discover the same ordered names and
owners as the ordinary census under the same effective rule policy.

| Mechanism | B88 | Selected behavior |
| --- | --- | --- |
| Group member | Known, owned, observed as eligible | Every known full ID with that owner |
| GROUP primitive filter | Scan eligible catalog, build members, run scalar policy | Exact all-known reference; recognized JVM policy reads group metadata |
| Opaque/custom GROUP policy | One scalar predicate over eligible members | Same predicate over all known owned members |
| Implicit static guides | Eligible groups | All known owned groups; guides remain permission only |
| Census cache miss | Insert bound list in a runtime method/field cache | Known capable provider: bind transiently, observe, release; no insertion |
| Census cache hit | Reuse existing bound list | Reuse the exact matching existing list |
| Duplicate census query | Invoke ordinary provider path | Optional ordered name-trace replay only under a proved complete stable key |
| Runtime rule lookup | Ordinary cached provider | Same ordinary cached provider |
| Unknown/custom provider | Existing wrapper and role calls | Same calls; no guessed uncached capability or replay key |

Keep these mechanisms unchanged:

- Original FULL rules, signed folds, role admission, source/sink identities,
  source AP transfers, ActionIds, complete ND premises and stable rule order.
- GROUP condition normalization and separate GROUP/FULL compiled forms.
- Empty `trackFactsReachAnalysisEnd` lists in every run. Source actions remain
  enabled. A catalog member cannot enable a disabled action.
- AP paths/tails, initial grammar, field kills, empty run-1 E, layer-only
  demotion for exclusion updates, cleaner group repair and actual-query rules.
- Run-1 static write/read/supply service, write-gated reads, saved-point
  refinement and the accepted static order/context approximation.
- ZERO satisfies a static premise only in run-1. Later runs require the real
  static fact. Neither a name nor ZERO supplies a group.
- Base request P/R, `commit(temporary)` then replacement, existing batching,
  queue ownership, counted work, cancellation and NORMAL record admission.
- Runs start at run-1. Limits are `1, 1, 2, 3, ...`; run-1 is GROUP forward,
  run-2 is FULL backward, and later runs alternate with FULL marks.
- Existing IR, RefManager, SoftReferenceManager and cache lifetime policy.
  This change controls census insertion; it does not replace cache managers.

Do not add GroundingSupport, a field validator, a source-admission phase,
a second fact history, static routing or a general group-member directory.
If implementation needs another major semantic change, expose it before
implementation. Do not use a proof of a different model as authorization.

## 2. Full names, owners and group membership

```text
Known full ID: an ID already created in the shared AP MarkTable.
K(g):         known full IDs whose assigned owner is g.
Valid group:  an owner group with at least one known owned full ID.
Pending ID:  a known full ID with no assigned owner yet.
```

ZERO is control. It has no owner. GROUP handles are selectors, not full IDs.
A pending ID contributes to no group until the existing owner protocol assigns
its owner. A raw group key can be empty after a rejected owner registration.
An empty key is not a published valid group.

Known names include provider `markOwners` declarations, imported/discovered
names, specialized GeneratedMark names and model names. Declarations already
registered from inactive rules also count. Names used only by disabled end
actions can count if already declared/imported. Their actions remain disabled.
Do not scan disabled actions or every possible generated name to enlarge the
catalog. Keep the current discovery inputs and effective observation rules.

Ownership remains the original top-level rule ID. One entire join rule has
one group. Imports retain their declared owner. Plain models use their
existing declared owner or SharedModel. A consuming rule does not reassign
an owner. Conflicting owners remain an error.

### Required catalog state

The AP module must not depend on the JVM suffix. Install one optional, pure,
immutable full-name property before actual-catalog full-ID creation.

```kotlin
class FullMarkProperty(val holds: (String) -> Boolean)

class MarkTable(private val property: FullMarkProperty? = null) {
    // Keep current fullNames, fullByName, handles, owners and group keys.
    // Delete eligible: LinkedHashSet<FullMarkId>.
    private val fullHasProperty = ArrayList<Boolean>()
    private val groupHasProperty = ArrayList<Boolean?>()
    // null: group allocated, no successful owner assignment.
    // false/true: group nonempty; value is OR of its full-member properties.
}
```

All creation/owner hooks below use the existing catalog monitor. Do not
change allocation order or full/handle IDs while factoring common helpers.
Both `register` and `reserveFull` currently create full IDs; cover both.

```kotlin
// Only when a full ID is genuinely new:
fullHasProperty.add(property?.holds?.invoke(name) == true)

// Only when a group key is genuinely new:
groupHasProperty.add(null)

// Only after the existing owner conflict check succeeds:
private fun creditOwned(full: FullMarkId, g: GroupId) {
    groupHasProperty[g.value] =
        (groupHasProperty[g.value] == true) || fullHasProperty[full.value]
}
```

There is one property bit per valid group. The null slot is an empty/unpublished
marker, not another eligible-member set. With no descriptor, successful owner
assignment still changes null to false for group enumeration. Repeated credit
is idempotent. No credited-member set or counter is needed.

Keep the current `putIfAbsent`/conflict behavior. A rejected registration
must not credit the rejected group or alter the accepted owner's metadata.
Preserve allocation/error order, including an empty group key allocated
before a conflict is detected. Do not publish that key through `groupAtoms()`.

### Preserve reservation and late ownership

```kotlin
reserveFull(name)              // can return a pending full handle
register(name, originalOwner) // assigns owner and credits metadata
finishOwnershipCensus()       // completes fallback owners, then seals
```

Do not eagerly assign SharedModel during `reserveFull`. A later declaration
can provide the original rule owner. Retain fixture-model owners and current
late-provider/model completion.

`markEligible` has an ownership side effect today. Remove its set insertion,
but keep that side effect at its existing call sites:

```kotlin
@Synchronized
fun completeObservedFullOwnership(atom: TaintMark) {
    val full = requireFullHandle(atom)
    if (isOwnershipCensusComplete && full !in owners) {
        register(fullNames[full.value], MarkGroupKey.SharedModel)
    }
}

fun observeModelReference(name: String): TaintMark =
    reserveFull(name).also(::completeObservedFullOwnership)
```

Before census completion, the existing census completes pending owners.
After completion, a raw reserved name can still remain pending until its
existing observation/explicit registration step. It must not become an
executable GROUP fact while unowned. No owner reassignment, removal, mark
rename or property mutation is selected.

### Member/group readers and order

```kotlin
@Synchronized
fun knownMembers(g: GroupId): List<TaintMark> {
    check(isOwnershipCensusComplete) {
        "Finish the mark ownership census before group execution"
    }
    return fullIdsInCreationOrder()
        .filter { assignedOwnerOrNull(it) == g }
        .map { fullAtom(it) }
}

@Synchronized
fun groupAtoms(): List<TaintMark> {
    check(isOwnershipCensusComplete)
    return groupIdsInAllocationOrder()
        .filter { groupHasProperty[it.value] != null }
        .map(::group)
}
```

Member order is FullMarkId creation order. Group order is GroupId allocation
order with empty keys omitted. This replaces active-observation order.
Original rule/action order is separate and stays unchanged. Batch-dependent
diagnostic counts need not match. Do not promise the old call order for an
arbitrary side-effectful custom mark policy under the changed member domain.

The explicit `groupAtoms(GroupSet)` operation still converts only the supplied
IDs. Do not expand selected wanted/supplied group sets to every catalog group.

## 3. Primitive-filter reference and optimized query

```text
primitive(m) = fullName(m).endsWith(PRIMITIVE_TRACKING_ENABLED_MODE)
requires(k)  = current JVM primitive/boxed type test at element level k

referenceKeep(g,k) =
    K(g).isEmpty OR exists m in K(g): (!requires(k) OR primitive(m))

hasPrimitive(g) = exists m in K(g): primitive(m)

For a valid nonempty group:
    fastKeep(g,k) = !requires(k) OR hasPrimitive(g)
```

Keep the existing Character case, unboxing, array traversal and false result
beyond known levels. A shared/model group need not have a uniform property.
Do not select one representative full mark.

The all-known semantic change can alter old decisions in both directions:

```text
primitive position; same group g

old eligible = {nonprimitive T}; known also contains primitive U
    old: reject; all-known: keep

old eligible = {}; known contains nonprimitive T
    old empty fallback: keep; all-known: reject
```

An actual full-policy keep witness is always retained by its all-known group.
Do not claim old/new GROUP equality. Prove the metadata query equal to the
new all-known reference. For empty keys, keep the explicit scalar empty-group
result. A false property bit alone cannot represent an empty key.

### Typed property policy

```kotlin
class PropertyMarkPolicy(
    val catalog: MarkTable,
    val property: FullMarkProperty,
    requiredLevels: BooleanArray,
) : MarkPolicy {
    private val required = requiredLevels.copyOf()
    fun requires(k: Int) = required.getOrElse(k) { false }
    override fun keeps(full: TaintMark, elements: Int): Boolean =
        !requires(elements) || property.holds(catalog.name(full))
}

internal object JvmPrimitiveProperty {
    val descriptor = FullMarkProperty { name ->
        name.endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)
    }
}

// Preserve JIRTypeFilters.byType and its existing path/type calculation.
val primitive = currentPrimitiveLevels(t)
val policy = if (true !in primitive) null else PropertyMarkPolicy(
    manager.marks, JvmPrimitiveProperty.descriptor, primitive.toBooleanArray())
return TypeFilter(checker.localFilter(t), policy)
```

Install the descriptor through a source-compatible optional ApManager
constructor argument. The actual catalog needs it before OwnedRulesProvider
registers declared names. The gather-time scratch catalog needs no JVM policy.

```kotlin
val ap = ApManager(cancellation, refManager, JvmPrimitiveProperty.descriptor)
val rules = OwnedRulesProvider(taintConfig.forBidirectionalCore(), ap.marks)
```

The descriptor is fixed for the catalog lifetime. Do not install it after
names already exist or recognize arbitrary lambdas by sampled behavior.

### Filter query

```kotlin
@Synchronized
internal fun groupProperty(g: GroupId, expected: FullMarkProperty): Boolean? {
    if (property !== expected || !isOwnershipCensusComplete) return null
    return groupHasProperty[g.value]
}

fun keepMark(atom: TaintMark, policy: MarkPolicy, k: Int): Boolean {
    val g = marks.groupId(atom)
    if (g == null) return policy.keeps(atom, k)
    if (policy is PropertyMarkPolicy && policy.catalog === marks) {
        val bit = marks.groupProperty(g, policy.property)
        if (bit != null) return !policy.requires(k) || bit
    }
    val members = marks.knownMembers(g) // retains the census guard
    return members.isEmpty() || members.any { policy.keeps(it, k) }
}
```

A null metadata answer is not permission to bypass the census guard. FULL
scalar filtering and pre-census name reservation keep their current rules.
Missing/mismatched capability, empty keys and opaque policies use the scalar
reference. Preserve root/element traversal, path filters, all filtering sites
and cancellation checks. Read metadata under the existing monitor. Do not
cache a past query answer or add a new replay/invalidation mechanism.

### Conjunction uses one full-member witness

```text
(!a(k) OR primitive(m)) AND (!b(k) OR primitive(m))
    = !(a(k) OR b(k)) OR primitive(m)
```

```kotlin
fun andPolicies(a: MarkPolicy?, b: MarkPolicy?): MarkPolicy? = when {
    a == null -> b
    b == null -> a
    a is PropertyMarkPolicy && b is PropertyMarkPolicy &&
        a.catalog === b.catalog && a.property === b.property ->
        PropertyMarkPolicy(a.catalog, a.property, orRequirements(a, b))
    else -> MarkPolicy { mark, k -> a.keeps(mark, k) && b.keeps(mark, k) }
}
```

`orRequirements` ORs each required level, with false beyond either input's
known levels. It returns a new immutable mask. `TypeFilter.may` still combines
by AND. For opaque/mixed/different-property policies, apply the combined scalar
predicate under one `knownMembers` existential. Independent group decisions
are wrong: policy A can accept only T while B accepts only U in the same group.

## 4. Reference ownership census

Census is name discovery and ownership completion. It emits no fact, summary,
request, static supply, finding or source seed.

Keep this complete input traversal, in the current order:

```kotlin
selectRules(prescanRuleIds)
for (method in (graph.methods + roots.map { it.method }).distinct()) {
    val first = method.instructions.firstOrNull() ?: continue
    censusMethod(method, first)
    for (statement in method.instructions) {
        statement.callExpr?.let { censusMethod(it.method.method, statement) }
        statement.readStaticFieldOrNull()?.let { field ->
            censusStaticSource(field, statement, allRelevant = false)
            censusStaticSource(field, statement, allRelevant = true)
        }
    }
}
for (call in graph.calls) censusMethod(call.callee, call.statement)
```

The declared/unresolved call target is required. The resolved graph alone
can omit rule/model candidates. Do not skip methods or replace both walks
with only transitive call-graph metadata. Keep cancellation checks.

```kotlin
fun censusMethod(method: CommonMethod, statement: CommonInst) {
    for (all in listOf(false, true)) {
        visit(EntryPoint(method, statement, all))
        visit(MethodSource(method, statement, all))
        visit(ExitSource(method, statement, all))
        visit(MethodSink(method, statement, all))
        visit(EntrySink(method, statement, all))
        visit(ExitSink(method, statement, all))
        visit(Cleaner(method, statement, all))
        visit(PassThrough(method, statement, all))
    }
}
```

Every census rule query uses `fact=null`; exit-sink queries also use
`initialFacts=null`. Static-field source is the ninth role. Both relevance
views remain distinct even when they happen to return equal rules.

Register the provider's entire existing `markOwners` sidecar in its existing
order, before observation. Keep the top-level owner mapping through wrappers.
Then observe the **effective post-wrapper rule outputs**:

```kotlin
fun visitEffectiveNames(rule: TaintConfigurationItem, emit: (String) -> Unit) {
    visitConditionNamesInCurrentOrder(rule.condition, emit)
    when (rule) {
        is TaintConfigurationSource -> rule.actionsAfter.forEach { visitAction(it, emit) }
        is TaintPassThrough -> rule.actionsAfter.forEach { visitAction(it, emit) }
        is TaintCleaner -> rule.actionsAfter.forEach { visitAction(it, emit) }
        is TaintConfigurationSink -> Unit // end actions remain disabled
    }
    if (rule !is TaintConfigurationSink) {
        rule.info.relevantTaintMarksOrEmpty().forEach(emit)
    }
}
```

Condition observation includes ContainsMark and ContainsMarkOnAnyField,
including negated leaves. Keep the current stack traversal order. Actions
observe AssignMark, CopyMark and RemoveMark; CopyAllMarks/RemoveAllMarks add no
name. Sink legacy relevance metadata must not restore end-only discovery.
Factor this visitor from OwnedRulesProvider so census and runtime observation
cannot drift. A sink's retained condition still contributes names.

All-known membership does not remove this workflow. A serialized declaration
sidecar does not necessarily cover specialized or model-created names.

## 5. Separate census lookup from runtime lookup

Use an explicit capability. Do not unwrap an unknown delegated provider.
Do not add a global mutable "census mode" to a shared configuration.

```kotlin
enum class RuleLookupMode { RUNTIME, CENSUS }

interface OwnershipCensusRulesProviderFactory {
    fun ownershipCensusView(): CensusView
}

data class CensusView(
    val provider: TaintRulesProvider,
    val replayKey: (CensusQuery) -> StableCensusKey?,
)

// Keep B88's existing initial new-core view boundary exactly once.
fun TaintRulesProvider.forOwnershipCensus(): CensusView =
    forBidirectionalCore().censusViewPreservingState()

// Below that boundary, do not invoke another view-creation boundary.
internal fun TaintRulesProvider.censusViewPreservingState(): CensusView =
    (this as? OwnershipCensusRulesProviderFactory)?.ownershipCensusView()
        ?: CensusView(this, replayKey = { null })
```

A known view applies the new-core empty-end policy and selects CENSUS lookup
at capable configuration leaves. The fallback uses exactly the current
new-core view and ordinary calls/cache behavior. OwnedRulesProvider still
protects sink end lists for providers without pre-resolution capability.

Construct the entire census wrapper tree **once per gather session**, not
once per query. Preserve wrapper-local state and invocation order. Keep the
runtime `forBidirectionalCore()` view on RUNTIME lookup. The legacy provider
and shared configuration do not change mode. Do not restore or change the
runtime Spring view factory as part of this optimization. New descent for
census must reuse each already-selected wrapper's local state or leave that
branch on its existing path. A fresh clone is not a state-preservation proof.

The leaf lookup capability and the replay certificate are different. Even
without replay, a capable leaf must have stable binding inputs for its exact
query/cache key in the session's generation. Standard setup creates that
session after configuration loading, rule selection and the joined prescan;
its binding inputs must not mutate during the session. If that contract cannot
be established, keep ordinary cached lookup for that leaf. A null replay key
can still use CENSUS lookup when the leaf is stable but a surrounding wrapper
cannot certify skipped-call equivalence.

### Exact leaf cache behavior

Add an internal lookup parameter to TaintConfiguration. Public/default runtime
calls keep their existing API and RUNTIME behavior. Cover eight method role
storages and the static-field source storage.

```kotlin
@Synchronized
fun configForMethod(method: JIRMethod, all: Boolean,
                    ignoreEnd: Boolean, mode: RuleLookupMode): List<T> {
    val cache = exactCacheFor(all, ignoreEnd)
    cache[method]?.let { return it } // a cached empty list is also a hit
    val result = resolveExactlyAsToday(method, all, ignoreEnd).adjustEmptyList()
    if (mode == RuleLookupMode.RUNTIME) cache[method] = result
    return result
}

@Synchronized
fun configForField(field: JIRField, mode: RuleLookupMode): List<T> {
    fieldItems[field]?.let { return it }
    val result = resolveFieldItems(field).adjustEmptyList()
    if (mode == RuleLookupMode.RUNTIME) fieldItems[field] = result
    return result
}
```

Preserve each storage's current monitor and error behavior. On a CENSUS miss,
insert neither a nonempty list nor a cached empty entry. Do not clear existing
entries. Borrow only the exact role/method/relevance/end-policy family; an
ordinary-end cache is not a no-end cache. Do not move unrelated entries into
another lifetime manager.

`resolveExactlyAsToday` denotes the existing pipeline, not a new algorithm:

```text
existing method storage/findRules order
-> existing name/class/hierarchy and signature checks
-> fresh MethodTaintConfigurationResolver for the whole query
-> resolveRule OR resolveRelevantRule, same end-policy flag
-> same specialization, full signed folding, actions, metadata and ordering
```

The resolver-local generated sink-ID counter must see the same successful
candidate/specialization sequence. Do not filter selected IDs before binding,
reset per candidate, hash new IDs or assign GROUP ordinals to FULL rules.
The shared TaintMarkManager still interns the same strings.

Runtime rebuilding can construct equal immutable rule copies. Preserve
logical rule/action/source identities, not JVM object addresses. The existing
form cache remains the runtime owner of compiled forms.

The ordinary provider forwards the lookup mode through all nine role methods:

```kotlin
class JIRTaintRulesProvider(
    private val config: TaintConfiguration,
    private val ignoreSinkEndFacts: Boolean = false,
    private val lookupMode: RuleLookupMode = RuleLookupMode.RUNTIME,
) : TaintRulesProvider, BidirectionalRulesProviderFactory,
    OwnershipCensusRulesProviderFactory {
    override fun ownershipCensusView(): CensusView = CensusView(
        JIRTaintRulesProvider(config, true, RuleLookupMode.CENSUS),
        certifiedConfigurationKeyOrNull(config),
    )

    override fun bidirectionalRulesView(): TaintRulesProvider =
        if (ignoreSinkEndFacts && lookupMode == RuleLookupMode.RUNTIME) this
        else JIRTaintRulesProvider(config, true, RuleLookupMode.RUNTIME)

    override fun sinkRulesForMethod(method, statement, fact, allRelevant) =
        config.sinkForMethod(method, allRelevant, ignoreSinkEndFacts, lookupMode)
    // Same forwarding for entry/source/exit/cleaner/pass-through/static roles.
}
```

`certifiedConfigurationKeyOrNull` is a new certificate helper, not an existing
generation API. It returns no key unless this configuration's inputs are
fixed for the session. A wrapper must still certify its own complete key;
a child's key alone cannot certify the wrapper.

## 6. Complete provider wrappers

Implement the capability only where the full wrapper behavior is known.
Every overridden role stays on the path. A wrapper cannot expose a child's
name visitor as if it were the complete provider.

| Provider | Required census view |
| --- | --- |
| JIRTaintRulesProvider | Same role mapping; CENSUS mode in TaintConfiguration; no-end sinks |
| JIRSemgrepRuleProvider | Same selected/allRelevant role behavior, unchanged markOwners; selection stays after binding |
| JIRCombinedTaintRulesProvider | Recreate both child views once, preserve CombinationOptions and full-rule combine order |
| JIRMethodExitRuleProvider | Keep its initialFacts gate; census passes null as before |
| StringConcatRuleProvider | Preserve the selected instance's first-rule cache through an explicit state adapter; otherwise retain its existing branch |
| SpringRuleProvider | Preserve generated-method/field suppression, controller action expansion, cleanup and repository rules |
| Unknown/custom wrapper | Existing complete provider path; no recursive stripping, no replay-key claim |

A view factory shares each wrapper's current role implementation and changes
only children/leaf lookup mode. Do not duplicate slightly different role logic.
For Semgrep, adapt the existing selection view; do not construct another
JIRSemgrepRuleProvider over the same configuration. Its constructor loads rules
and can fail after the configuration builder has closed. All nine roles must
forward CENSUS access; the old anonymous view's three sink overrides alone
are insufficient.

Preserve the selected Combined instance's lazy `markOwners` sidecar too.
A census adapter can delegate that property to the selected instance; it must
not accidentally re-read a stateful child's owner map through a fresh lazy
initializer. Apply the state-preservation requirement to all wrapper-local
state, not only the StringConcat cache.

```kotlin
class SpringRuleProvider(private val base: TaintRulesProvider,
                         private val springCtx: SpringWebProjectContext)
    : TaintRulesProvider by base,
      OwnershipCensusRulesProviderFactory {
    override fun ownershipCensusView(): CensusView {
        val child = base.censusViewPreservingState()
        val wrapped = SpringRuleProvider(child.provider, springCtx)
        return CensusView(wrapped, completeSpringReplayKeyOrNull(child, springCtx))
    }
    // Keep every current overridden role implementation.
}
```

The census route can forward the already-selected empty-end policy to capable
leaves without bypassing Spring. That binding must preserve full conditions,
IDs and effective sink outputs under the existing empty-end rule contract.
Runtime Spring behavior and view-creation boundaries remain as B88. Unknown
child branches still use ordinary caching and the existing effective sink
copy path; report that coverage.

StringConcat needs explicit state ownership, because its cached rule contains
the first method. A source-compatible state adapter can use this shape:

```kotlin
private class ConcatState { var pass: TaintPassThrough? = null }

class StringConcatRuleProvider private constructor(
    private val base: TaintRulesProvider,
    private val state: ConcatState,
) : TaintRulesProvider by base, BidirectionalRulesProviderFactory,
    OwnershipCensusRulesProviderFactory {
    constructor(base: TaintRulesProvider) : this(base, ConcatState())
    override fun bidirectionalRulesView() =
        StringConcatRuleProvider(base.forBidirectionalCore()) // fresh as in B88
    override fun ownershipCensusView(): CensusView {
        val child = base.censusViewPreservingState()
        return CensusView(StringConcatRuleProvider(child.provider, state), { null })
    }
    // Current generated-pass code reads/writes state.pass, in the same order.
    // Keep the existing method, condition and action construction.
}
```

Only a census view of this selected instance shares that instance's cache.
Do not share one cache across unrelated provider instances or change existing
fresh `forBidirectionalCore()` boundaries. The conservative StringConcat
adapter above claims no neutral replay key; its leaves can still avoid bound
cache insertion. If a state-preserving adapter is unavailable, use the exact
original branch, with no new descent through it.

Combination must finish before name projection. In OVERRIDE mode the current
provider compares complete bound conditions, then returns combined rules plus
non-overridden base rules. Preserve this. Even IGNORE currently evaluates the
combined provider before returning base rules; do not silently remove that
call or its errors. AllRelevant is not a substitute for normal selection.

## 7. Optional neutral trace replay

Repeated transient binding can add setup work. Permit session-local reuse of
an ordered **name trace**, after all wrapper behavior. It stores no bound
rule, condition, action, sink metadata, compiled form or closure retaining one.
Do not implement a second long-lived bound cache.

```kotlin
class CensusSession(private val view: CensusView,
                    private val owned: OwnedRulesProvider) {
    private val traces = HashMap<StableCensusKey, List<String>>()

    fun visit(q: CensusQuery) {
        val key = view.replayKey(q)
        val cached = key?.let(traces::get)
        if (cached != null) {
            cached.forEach(owned::observeFullName)
            return
        }
        val trace = if (key != null) ArrayList<String>() else null
        for (rule in effectiveRules(view.provider, q)) {
            visitEffectiveNames(rule) { name ->
                owned.observeFullName(name)
                trace?.add(name)
            }
        }
        // Do not cache a partial result if resolution/observation throws.
        if (key != null) traces[key] = checkNotNull(trace).toList()
    }
}
```

`effectiveRules` performs the exact existing role pipeline on this complete
view, with sink end lists empty. Preserve its evaluation boundary:

```kotlin
// Sink roles: finish the eager sink-copy map before observing any sink names.
val effective = rawSinkRules.map { rule ->
    if (rule.trackFactsReachAnalysisEnd.isEmpty()) rule
    else rule.copy(trackFactsReachAnalysisEnd = emptyList())
}
for (rule in effective) visitEffectiveNames(rule, emit)

// Source/cleaner/pass roles: retain current iteration-and-observation order.
for (rule in rawRoleRules) visitEffectiveNames(rule, emit)
```

If a sink iterator yields a rule then throws, the sink map throws before any
sink name is observed. A streaming sink observer would change that behavior.
Reuse the factored OwnedRulesProvider pipeline for fallback providers too;
ordinary lookup fallback alone does not preserve observation/error order.
`observeFullName` retains the current ordered
observed-name set and catalog observation/owner completion. Replay names in
the same order. Empty traces are valid memo hits.

A replay key is a **capability certificate**, not a key guessed from method
name. For one frozen session it must include or prove irrelevant:

```kotlin
data class StableCensusKey(
    val generation: CensusGeneration, // provider/selection/model/body/context view
    val role: CensusRole,
    val subject: MethodOrStaticField,
    val allRelevant: Boolean,
    val ignoreSinkEndFacts: Boolean,
    val statementScope: StatementScope,
)
```

The query contract fixes null fact and null initialFacts. For a proved
method-only chain, StatementScope can be an explicit `Independent` value.
Otherwise include the actual statement or return no key. Do not merge roles,
methods/fields, relevance modes, provider instances or selection generations.

A stable immediate name set is insufficient. Skipping a repeated call must
also preserve subsequent logical rule identities, output order, errors and
provider state effects. Bind with the existing local-counter sequence.
Known-child intern/matcher cache warming can be omitted only when its absence
has no semantic effect. An unknown/stateful child makes the wrapper replay
key null unless that complete behavior has an explicit proof.

No replay key is required for correctness: the exact CENSUS binding path still
avoids cache insertion without one. Do not widen a key to claim extra savings.
No size cap, clipping policy or new pending-work barrier is introduced.

## 8. End-to-end lifetime and runtime handoff

```kotlin
fun gatherAfterResolvedPrescan(...) {
    taintConfig.selectRules(ids)
    val names = collectOwnershipNames(taintConfig, graph, roots)
    prescan = PrescanResult(ids, lambdas, roots, checker, tracker, graph, names)
}

private fun collectOwnershipNames(...): List<String> {
    val view = taintConfig.forOwnershipCensus() // once for this session
    val owned = OwnedRulesProvider(view.provider, MarkTable())
    val session = CensusSession(view, owned)
    visitCurrentPrescanQueries(graph, roots, session::visit)
    return owned.observedFullNames() // neutral ordered names only
}

private fun prepareShared(copied: PrescanResult): SharedObjects {
    val ap = ApManager(cancellation, refManager, JvmPrimitiveProperty.descriptor)
    val rules = OwnedRulesProvider(taintConfig.forBidirectionalCore(), ap.marks)
    copied.markNames.forEach(ap.marks::observeModelReference)
    ap.marks.finishOwnershipCensus()
    logKnownCatalogStats(ap.marks)
    return prepareExistingRuntimeObjects(ap, rules, copied)
}
```

The helper frame must end before the engine starts. Neither PrescanResult nor
an engine-owned object retains the census view, session, trace memo, scratch
catalog or transient rule lists. PrescanResult keeps the same neutral names
and existing prescan values it kept before. Continue existing clearing of
prescan/call snapshots. Do not clear unrelated provider caches to obtain a
memory result.

An already-existing wrapper-local cache can retain its ordinary synthetic
rule, as before. State-preserving adaptation must not erase that behavior.
The no-retain claim concerns new census ownership and capable configuration
cache misses; it does not claim that all provider-owned rule objects disappear.

Runtime rule lookups use RUNTIME mode. They borrow existing entries or bind
and cache on actual demand as before. Current GROUP/FULL form ownership and
managed soft references remain unchanged. A cache miss can move binding work
from setup to engine; measure both clocks.

Rename catalog statistics to `knownFull` and `knownOwnedGroups`. Avoid counting
by scanning all members once per group; expose synchronized counts from existing
full-ID storage and nonnull group slots. Counting is setup metadata only.
Historical eligible counts remain labeled as historical counts.

### Later FULL runs and static guidance

SharedStaticGroupDemand enumerates all known owned groups, including late
groups, through the same dynamic catalog view. It remains one symbolic family,
not a method-by-field-by-full-mark product. Empty/orphan groups are omitted.

Production run-2 matches actual FULL leaves with GROUP guides by owner through
`coversAtom`. It already preserves the actual full identity; keep that path.
Do not enumerate every catalog member to emit new full facts. Reference drivers
and finite decoders that enumerate members must use K(g).

More known groups can add guide patterns and GROUP candidates. They cannot
create a static supply, source hit or finding by themselves. Actual full
queries, original predicates, exact source/action occurrences, complete joins
and real transfer decide later results. Preserve the current late-extension
protocol before a complete barrier; current metadata equality is not a new
engine replay mechanism.

A late primitive member can change an existing group's decision from reject
to keep. Updating its bit does not revisit an earlier dropped input. The
finite witness claim therefore uses a complete fixed catalog generation, or
the inherited contract that affected work is replayed before completion.
Do not claim that B88 has a generic catalog-to-filter replay API. The known
provider adapter must establish census completeness; a new dynamic-generation
restart/replay mechanism is outside these two selected changes. If an adapter
needs such a mechanism, expose that gap instead of adding it silently.

## 9. Examples and expected savings

### Primitive query

```java
int n = parameter();
consume(n);
```

Annotated slice: `P -> n/$/Group(g) @DEMAND`. T and U belong to g; U has the
primitive suffix. Keep P, AP geometry and layer.

```text
B88: each filter call scans eligible full IDs, creates g's member list,
     then evaluates member policies.
New scalar reference: same decision formula over all known owned members.
New optimized: registration already ORed primitive(U) into g's bit;
               read the bit and keep this slice. No full-member scan.
```

For Q recognized queries, N known full IDs and C owner credits:

```text
all-known scalar lookup: O(Q*N), plus selected member checks/lists
metadata query:         O(Q), plus O(N) classification and O(C) credits
retained metadata:      O(N + groups); eligible set removed
```

String-test cost and ordinary maps remain separate. Opaque policies retain
the scalar scan. More known groups can admit work, so local savings are not
a whole-project performance theorem.

### Census-only bound rules

```java
String echoA(String p) { return p; }
String echoB(String p) { return p; }
// Broad exit sink r: CM(Result, Full(g,T)); end-action list is empty.
```

For either method the runtime slice remains
`arg0/$/g -> Result/$/g @DEMAND`. Its original method/rule occurrence remains.
Census itself propagates none of these facts.

```text
before:
  bind A/normal, A/allRelevant, B/normal, B/allRelevant
  observe names -> retain miss results in four runtime cache entries

after, cold capable cache:
  bind the same ordered queries -> observe the same names
  drop transient lists -> no new runtime bound-cache entries
  repeated certified query -> replay only its ordered name trace
  runtime first use -> rebuild the same logical full rules and cache normally
```

Normal/allRelevant happen to agree in this example. They remain different keys.
An existing cached list is borrowed in either version and remains retained.
The change avoids census-caused retention, not every full-rule allocation.

Let U be unique capable cold query keys, B(q) their bound payload and T(q) their
neutral trace. Runtime cache growth caused by census is zero for those keys.
The old cache can retain the union of B(q); new session state retains only
traces/keys plus the current transient pipeline. Session state is released
before engine entry. Wrapper combination can hold several transient lists;
do not claim one-rule peak memory. Existing cache hits, unsupported branches,
live forms and immutable provider templates remain retained.

The strict saving requires at least one census-only miss payload with no other
retaining owner. No universal byte saving or CPU improvement is claimed.
Transient rebuilding can cost setup/runtime CPU. A verified replay key avoids
repeated binding only in its certified scope.

## 10. Correctness and proof obligations

Use a two-stage comparison:

```text
B88 eligible reference -> selected all-known scalar reference
    explicit semantic change, not an equality theorem

all-known scalar reference -> metadata + no-retain census implementation
    equivalence within the stated provider/property contracts
```

### Primitive policy

The constructive model
[GroupPrimitiveMetadata.lean](lean/ApSpec/GroupPrimitiveMetadata.lean), namespace
`AllKnown`, proves owner-only membership, nonempty-group one-bit equality,
actual full-witness coverage, OR updates, duplicate laws and same-property
conjunction. It proves the empty-boundary counterexample and both directions
of changed old eligibility decisions.

| Claim | Checked declaration |
| --- | --- |
| Eligibility flags do not affect membership summary | `AllKnown.ignores_eligibility` |
| Scalar reference equals owner summary | `AllKnown.reference_eq_guarded` |
| Fast bit equals scalar for a valid group | `AllKnown.one_bit_nonempty` |
| Executable owner/property OR | `AllKnown.hasPrimitive_eq_one_bit_summary`, `one_bit_summary_append` |
| Accepted actual full member remains accepted as a group | `AllKnown.owned_entry_coverage`, `member_one_bit_coverage` |
| Same-member conjunction | `AllKnown.all_known_conjunction` |
| Successful owner publication and no-op duplicates | `AllKnown.creation_bit_update`, `register_duplicate_noop` |
| Stable previous records/owners | `AllKnown.register_preserves_existing`, `owner_cannot_be_reassigned` |
| Empty-group exception and old/new differences | `AllKnown.empty_boundary`, `eligibility_change_can_reject`, `eligibility_change_can_keep` |
| Scoped entry-visit comparison | `fixed_members_nonincrease`, `fixed_members_strict` |

The owned-record creation model abstracts successful owner publication, not
raw full-ID reservation. Pending Entry records contribute nothing. Kotlin
must map every actual owner assignment to the OR update before publication.
Duplicate state laws do not authorize silently accepting an owner conflict.

### Census preservation

For every fixed effective provider generation and complete query q:

```text
ordinary(q) = exact cached result OR existing whole-query binding
census(q)   = same exact cached result OR same whole-query binding
observe(q) = ordered effective full-name trace after every wrapper
```

The required proof has these parts:

1. Equal complete queries give equal ordered logical bound results, including
   original rule/action identities. For capable leaves, both miss paths call
   the same resolver; mode changes only insertion. A cache hit denotes the
   same generation and result. Keep exact role/relevance/end-policy keys.
2. Every wrapper preserves that relation by its unchanged role operation.
   Full-condition OVERRIDE and existing evaluation/error order are included.
   Projecting names before this step is not a valid proof.
3. Optional replay requires an equal complete key, equal ordered trace and
   preservation of subsequent semantic state effects. A finite memo hit
   replays that same trace. Unsupported providers use ordinary calls.
4. Induction over the unchanged prescan query list gives the same declaration
   and observation trace. Each step either invokes the same result or replays
   its exact certified trace. Shared ordered-name insertion therefore yields
   the same names, owners/conflicts and stable full-ID creation order.
5. Census misses do not insert a runtime cache entry; hits do not remove or
   replace one. Induct over the query list to preserve all preexisting entries.
   Runtime lookups after census still use the ordinary cache algorithm.
6. A session trace stores neutral data only. Dropping its frame removes its
   ownership of transient bound objects. Exact JVM reachability remains an
   adapter obligation: no closure, result or provider field may retain them.
7. Equal catalog creation/owner traces give equal all-known K(g) and primitive
   summaries. Thus census lifetime cannot change the primitive reference.
   Compose this with the one-bit theorem, not with old eligible equivalence.

The checked finite model is
[OwnershipCensus.lean](lean/ApSpec/OwnershipCensus.lean), with its
[focused audit](lean/ownership-census-audit.lean).

| Claim | Checked declaration |
| --- | --- |
| Same per-query neutral result/catalog/error | `visit_exact`, `acquisition_same_catalog_or_error` |
| No capable leaf insertion, even without a replay key | `census_lookup_no_bound_insertion`, `census_only_exact` |
| Existing cache entries stay unchanged | `preexisting_bound_preserved`, `successful_census_run_preserves_cache` |
| Unknown/stateful fallback is the ordinary API | `unsupported_is_ordinary`, `fallback_exact` |
| Replay commits only after successful binding/observation | `failed_observation_no_memo`, `commit_visit_preserves_memo_valid` |
| Session memo has at most one slot per complete finite key | `memo_slots_bound` |
| Query-list induction against the ordinary cached reference | `census_equals_cached_reference`, `query_list_exact` |
| Same completed known catalog and primitive summary | `query_list_same_all_known`, `visit_same_all_known_catalog_and_primitive` |
| Stable identities when the complete provider certificate holds | `stable_rebuild_identities` |
| Removed cold cache-root edge and scoped exclusive retention | `fresh_miss_root_edge_difference`, `exclusive_payload_retention_difference`, `fresh_bound_write_cost` |

The list proof uses one fixed complete-query resolver world, a semantically
valid initial cache and valid neutral memo entries. `StableProvider` exposes
the separate obligation that rebuilding/skipping a call preserves logical
identities, ordered observations/errors and subsequent observable provider
state. It does not prove arbitrary mutable JVM wrappers pure. The Kotlin
adapters must preserve the per-role eager/iteration failure boundary.

The model's object graph proves a strict retention difference only when the
removed payload has no other root. It does not measure a JVM heap or prove
that all objects in the measured bound-cache union become unreachable.
Finite memo cardinality is a property of the fixed query universe, not a
new memory threshold or clipping policy.

### Hidden-major-change review

The primitive domain changes; fields, sources, calls, cleaners and layers do
not. The census changes insertion/lifetime; it does not select rules, omit
role calls, prune the graph or change generated identities. Unknown providers
keep their complete path. Proofs must not add grounding labels, exact-field
validation, source gating, new group supplies or later-run guide vetoes.

The new primitive model proves local full-witness coverage. Existing static,
request, source and handoff models still require their production adapters.
No finite model here proves arbitrary JVM compilation, a whole project finding
set, termination of external provider code or a 60-second engine bound.

## 11. Implementation plan and tests

Implement in reviewable steps, with no project configuration changes:

1. Keep the selected all-known reference explicit in catalog/filter tests.
   Update normative decoder terminology and affected existing tests.
2. Remove active-eligibility state; preserve every creation, owner completion,
   fixture, conflict, pending-name and late-extension path. Add metadata and
   counts at those existing catalog owners.
3. Add typed policy, exact JVM property wiring and same-property AND. Switch
   only the compatible GROUP branch to metadata. Keep FULL/path behavior.
4. Add CENSUS leaf lookup mode and known provider factories. Factor shared
   role/visitor code, preserve exact cache families and use one session view.
5. Add certified neutral replay only for proved stable complete chains. For
   any uncertain chain/key use no replay; for an unsupported provider use
   its existing cached lookup. Release session state before engine entry.
6. Run the selected common/JVM/application/rule gates. Commit only the selected
   implementation/tests/docs when implementation is requested. Then repeat
   task.md's four fixed projects with the same heaps/timeouts and main pin.

Required focused controls:

| Area | Required cases |
| --- | --- |
| Membership | Inactive declared primitive/nonprimitive, mixed/shared/model groups, disabled-only declared names, no invented disabled actions |
| Catalog lifecycle | Both full-ID creation paths, reserve then explicit owner, fixture owner, late SharedModel completion, repeats, conflict/orphan, early GROUP rejection, FULL before census |
| Policy | Primitive/boxed/Character/array levels, no descriptor, mismatched descriptor/catalog, empty group, FULL scalar, opaque/mixed AND split-witness counterexample |
| Facts | Compare full root/element-chain filter outputs and path order, not just Boolean results |
| Leaf caches | Cold/hit/cached-empty for all method roles/relevance/end modes and static fields; CENSUS adds no entries, existing entries untouched, RUNTIME still caches |
| Resolver identity | Anonymous generated sink IDs, pruned candidates, AnyArgument specialization, signature failures, selected/allRelevant and no-end rebuilds |
| Wrappers | Combined EXTEND/OVERRIDE/IGNORE; suppressed base names; null-ID models; Spring controller/generated/cleanup/repository roles; StringConcat state across existing/new view boundaries; method-exit gate; no Semgrep constructor reload |
| Replay | Same key, distinct roles/relevance/statements/generations, empty trace, exception/partial query, late extension, no-key custom/stateful provider |
| Handoff | Known-owned dynamic static guides, pending/orphan omission, actual FULL mark retained, no fact/source from membership alone |
| Lifetime | No census frame/memo retains configuration-miss payload after helper return; no raw bound list/closure in trace memo or PrescanResult; account separately for unchanged wrapper/runtime owners |

Include throwing-iterator controls for all three sink roles: yield one rule,
then throw; no sink observation may escape the eager copy boundary. Keep the
corresponding source-role prefix behavior. Count both view creation and local
state reuse; a once-per-session fresh clone alone is not sufficient.

Extend the bounded operation-count diagnostic. With 20 recognized primitive
GROUP calls at N=51 and N=1,172, require zero member probes, 20 metadata reads,
no per-visit suffix classification and no member-list allocation. Keep the
opaque-SAM control on scalar enumeration; with the diagnostic's same known
members it still has `20*N` owner probes. FULL retains zero member probes.
Count initialization and metadata reads separately.

For census, count exact cache hits, capable misses, bound results constructed,
new runtime cache entries, trace replay hits, fallback calls, and role/view
coverage. A no-retain claim requires zero census-miss cache insertion in every
selected capable leaf. Count surviving bound payload owners separately.
Do not infer a whole-cache saving from removed census entries.

Measure setup, engine and report barriers separately. Runtime rebuilding can
move time into the engine. Compare strict finding identities and original
rule/source occurrences. Batch-dependent diagnostics can differ. Always report
latest reached/completed/accepted iteration. Do not broaden/repeat checks after
they pass unless a new change or unresolved issue requires it.

### Affected code and removal list

| Component | Required change |
| --- | --- |
| `ap/RuleMarkCatalog.kt` | Remove eligible set/API, add known readers/counts, optional property and owner metadata hooks |
| `ap/ApManager.kt`, `ap/Primitives.kt`, `ap/Filter.kt` | Optional descriptor, typed policy/conjunction and compatible GROUP branch |
| `jvm/bidi/interp/JIRTypeFilters.kt` | Existing level calculation, recognized property policy |
| `jvm/bidi/interp/OwnedRulesProvider.kt` | Shared effective-name visitor, discovery/ownership helper names, census session entry |
| `jvm/bidi/JIRBidiAnalysis.kt` | Once-per-gather census view/session, neutral handoff, actual property wiring and known counts |
| JVM `TaintRulesProvider.kt` | Explicit ownership-census factory/query/view contracts; unknown fallback |
| `TaintConfiguration.kt`, `JIRTaintRulesProvider.kt` | Exact-key CENSUS cache access for all method/field roles; runtime default unchanged |
| Semgrep/combined/Spring/exit/StringConcat wrappers | Preserve complete role logic while forwarding view capability |
| Reference drivers, tests and proof adapters | All-known member domain, census/key/identity/lifetime laws |

Remove only obsolete active-eligibility storage/bookkeeping and old census
miss insertions on the new route. Keep original declarations and discovery,
ordinary runtime bound caches, original FULL rules, forms and source metadata.
Do not blanket-clear normal/allRelevant/no-end maps or remove a provider role.

## 12. Evidence and validation status

The measured baseline is B88. Its selected 2,063-test local gate passes;
fixed-project acceptance fails. These are previous measurements, not results
for this specification:

| Project | Latest reached / completed engine / accepted forward report | Run-1 engine |
| --- | --- | ---: |
| OWASP | run-1 / none / none | 906.998 s, TIMEOUT |
| Conductor | run-1 / none / none | 986.046 s, TIMEOUT |
| ThingsBoard | run-1 / none / none | 239.084 s, memory-guard OOM |
| Stirling-PDF | run-2 / run-1 / run-1 | 925.594 s, COMPLETE; run-2 TIMEOUT |

The current fixed-fact diagnostic measured 1,020 versus 23,440 owner probes
for 20 GROUP calls at eligible-catalog sizes 51 versus 1,172. FULL measured
zero in both cases. PDF/Conductor have 59.33%/43.80% inclusive eligibleMembers
Java sample shares. These shares overlap other stacks and do not predict
wall-time savings.

ThingsBoard's four bound-cache field families have a nonadditive retained
union of 3,160,452,352 B. One configuration's eight role storages contain
940,406 normal/allRelevant method/view key memberships, including empty lists;
no-end maps are empty in that snapshot. Not all retained bytes are proved to
be census-only. Existing prescan/runtime cache entries and live forms can
survive this optimization. Setup savings are outside the 60-second engine
clock, although lower retention can affect engine memory pressure.

See the [fresh performance report](../docs/reports/bidirectional-run1-normalized-groups-performance-20261019.md)
and [root-cause investigation](../docs/reports/bidirectional-run1-work-amplification-root-cause-20261019.md).
The spec adds no new project run and claims no achieved performance improvement.

The existing primitive check is Lean 4.33.1: 200 owned declarations audited,
zero unexpected axioms, 27 executable controls. Its receipt is
`/drive-testcomp/opentaint-go-rules/grouped-statics-alias-writes-20261019/validation/group-primitive-metadata-proof/coverage-receipt.json`.
Only the focused module/audit ran under the three existing workload locks.
No sorry, custom axiom or classical choice is permitted. Standard `propext`
and `Quot.sound` are the only allowed audit dependencies.

The census model/audit also pass on Lean 4.33.1: **448 owned declarations,
zero unexpected axioms, 19 executable controls**. Its source/commands/logs
are bound by
`/drive-testcomp/opentaint-go-rules/grouped-statics-alias-writes-20261019/validation/ownership-census-proof/receipt.json`.
The check used the same three locks, ran only the focused target/audit and
left the primitive proof files unchanged. All checks are terminal and the
locks are released.

Focused commands, from `spec/lean` while holding the three workload locks:

```sh
lake env lean -o .lake/build/lib/lean/ApSpec/GroupPrimitiveMetadata.olean ApSpec/GroupPrimitiveMetadata.lean
lake env lean group-primitive-metadata-audit.lean
lake env lean -o .lake/build/lib/lean/ApSpec/OwnershipCensus.olean ApSpec/OwnershipCensus.lean
lake env lean ownership-census-audit.lean
```

The receipts bind the actual separate executions, not a new combined project
run. Do not use an unfiltered Lean build as a substitute for the focused check.

Independent contract and source reviews cover the exact query families,
wrapper state, effective observation boundaries and generated identities.
The discovered wrapper-reset and streaming-sink error gaps are fixed in this
spec. Runtime conformance, Kotlin gate results and fixed-project acceptance
remain future work. No analyzer implementation is changed by this document.
