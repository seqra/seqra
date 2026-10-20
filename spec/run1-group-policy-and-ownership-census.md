# Immutable mark groups and static demand guidance

Date: 19 October 2026. Revision: **2.1, direct primitive policy; no binding census**.
Status: implemented. The runtime baseline was
`b88c85d5b710d28293d7672af92b085a3838fb19` (B88). The
[implementation validation](../docs/reports/bidirectional-immutable-mark-groups-validation-20261019.md)
records the passing selected gate. The
[fresh project report](../docs/reports/bidirectional-immutable-mark-groups-performance-20261020.md)
records failed project acceptance: all four projects miss the 60-second
run-1 engine target. The report includes each latest reached iteration.
Use ASD-STE100 Simplified Technical English. Defined code names keep their
meanings. Follow [proof-guide.md](proof-guide.md).

This revision replaces the earlier census-lookup and property-aggregation
design. Keep this file path so existing links remain valid. Its selected scope
is now: immutable group names, uniform primitive tracking, a small mark
interner, serialized mark declarations and static demand matching without
global enumeration. [grouped-run1-statics.md](grouped-run1-statics.md) retains
all other analyzer behavior.

The [implementation proposal](../docs/design/bidirectional-immutable-mark-groups-implementation-20261019.md)
maps this contract to current code, removals, fixture migration and validation.
It introduces no additional semantic changes.

## 1. What static guidance means

After run-1, backward run-2 uses forward demands to admit actual inputs and
restrict computed results. The selected static design provides one implicit
permission family for every method:

```text
For a group g:
    entry = S / [] / ANY  / Group(g), E={}
    exit  = S / [] / STAR / Group(g), E={}
```

S is the static base. This is a paired demand, not an executable summary,
initial fact, static source or write. It lets an actual static FULL fact use
the ordinary demand machinery. It creates no taint by itself. Real backward
CFG/call transfer, strong writes and original FULL rules still apply.

For example, an actual `S.C.f / ANY / Full(g,T)` input can match this permission
for g. Matching retains the actual full T and the ordinary AP geometry. A
FLOW input has no concrete group and cannot use this family to invent T.

B88 constructs these permissions by calling `groupAtoms()` and building one
pair per catalog group. That enumeration is a representation choice. Only
the actual input/premise's group can match. This revision constructs that
one pair at the query point and keeps the ordinary `emit`/`restrict` tests.

Initial emission selects g from the actual incoming leaf. Result restriction
selects g from the current **entry premise**, never from the output mark.
Keep the exit paired with that same g. This implicit pair contributes no
cross-group result. Existing explicit demands and actual-current-premise
admission/publication rules remain unchanged.

## 2. Selected semantic scope

| Mechanism | B88 / earlier proposal | Selected contract |
| --- | --- | --- |
| Group membership | Active/known catalog and member queries | Immutable owner in each full key; all serialized declarations are available |
| Primitive property | Infer OR from eligible/known members | Assert all marks in one group have the same primitive flag |
| Group primitive representation | Generic descriptor, per-full bits and group aggregation | Encode the flag once in the group name |
| MarkTable | Mutable ownership, eligibility, pending names and census seal | Interner of immutable keys and stable opaque IDs |
| Primitive filtering | Enumerate members or read aggregated metadata | Apply the same suffix policy directly to FULL/GROUP names |
| Policy plumbing | Proposed GROUP-specific policy tag, checks and conjunction | Reuse existing MarkPolicy and TypeFilter.and; no new policy type |
| Shared static guidance | Enumerate all groups and retain patterns | Construct one query-dependent paired demand |
| Mark discovery | Bind all methods/roles/relevance views | Visit serialized declarations at load time |
| Rule caches | Census populates them, or proposed CENSUS mode avoids insertion | No ownership-census lookup at all; ordinary runtime caches remain |

**Explicit input-contract changes:** reject a group with mixed primitive flags
and reject an executable undeclared full name. A custom provider must supply
complete declarations. Do not silently fall back to method-binding census.

This revision removes the proposed GROUP-policy tag, rejection check and
special conjunction. Reuse the existing primitive MarkPolicy on FULL/GROUP
names and the existing TypeFilter.and. The selected policy scope is primitive
tracking and its conjunctions. No new custom-policy framework or automatic
member-based lift is selected. Static demand matching is unchanged.

The uniformity assertion includes plain/model groups. A SharedModel with both
`T` and `U%%primitive%%` is invalid. Do not split it into new groups, OR its
flags, change an existing group name or retain a dynamic compatibility lane.
One complete join rule still has one owner/group.

This is also an explicit declaration-domain change. Inspect all loaded
serialized mark names, including names in inactive rules, raw branches that
later fold to false, relevant-mark metadata and disabled sink end actions.
These names are metadata only. Their presence enables no rule/action, source,
request, static supply or executable summary. Do not bind or validate disabled
end-action positions while collecting their names.

Keep unchanged:

- Source/sink rule admission, signed FULL folds, original predicates, source
  AP primitives, ActionIds, candidate/action order and complete ND premises.
- Existing GROUP condition normalization and separate GROUP/FULL forms.
- Empty `trackFactsReachAnalysisEnd` in every run; ordinary source actions.
- AP grammar/tails, field overwrites and kills, empty run-1 field exclusion E,
  layer-only demand demotion, selected-cleaner repair and actual-query rules.
- Run-1 static writer/read/supply joins, saved-point refinement and the
  accepted static order/context approximation. No static CFG relay is added.
- ZERO can satisfy a static premise only in run-1 and supplies no group.
  Later runs require the real static fact.
- Base P/R requests, commit-then-replace, batching, work ownership, cancellation
  and valid NORMAL record export.
- Runs start at run-1 with limits `1,1,2,3,...`; run-1 GROUP, later runs FULL.
- IR, managed references, runtime rule caches and existing provider factories.

No GroundingSupport, exact-field validator, new static routing, fact history,
cache-manager rewrite or provider warmup phase is selected.

## 3. Names, ownership and the uniformity assertion

Keep original FULL names and public rule IDs. Internal group names use a
separate tagged encoding. Include owner kind so Rule, Model and SharedModel
cannot collide.

```kotlin
@JvmInline value class GroupName(val encoded: String)

fun encodeGroup(owner: OwnerKey, primitive: Boolean): GroupName {
    val payload = canonicalTaggedOwner(owner) // injective, includes owner kind
    val mode = if (primitive) "%%primitive%%" else "%%ordinary%%"
    return GroupName("@group:${payload.length}:$payload;$mode")
}

data class FullKey(val group: GroupName, val name: String)
```

The length-prefixed owner encoding and final mode distinguish owners and
flags even when a raw rule ID contains delimiter/suffix text. Original rule
IDs used by findings and source identities do not change.

For generated rules, take the flag from the outer rule's `primitive-tracking`
option. RuleConversionCtx.child preserves both this flag and topLevelRuleId.
All join operands/states belong to the outer join group. Do not parse a
child GeneratedMark.ruleId to recover that owner.

An imported mark keeps its declared original owner and that owner's flag.
The consuming rule's option does not change an imported mark.

Define the exact existing FULL policy:

```kotlin
fun primitive(fullName: String) =
    fullName.endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)
```

At declaration finalization, assert for every full name:

```kotlin
require(primitive(full.name) == primitive(full.group.encoded)) {
    "Mixed primitive tracking in group ${full.group}"
}
```

Also reject contradictory explicit owners or two flag declarations for the
same owner. This assertion includes a label that accidentally imitates the
reserved suffix. Do not silently rewrite its full name or choose one member.

An explicitly declared model owner supplies its group. Unclaimed plain names
keep SharedModel. Resolve all explicit claims across providers **before**
assigning SharedModel. Determine a model group's flag from its complete
serialized name declarations and assert all are equal. A first observed name
at runtime must never choose or change the group's property.

Groups with no full declaration supply no executable taint. Runtime group
values originate from declared full marks, so the primitive equivalence below
has a full-member witness. There is no orphan/empty-member fallback in the
interner or primitive filter.

## 4. Serialized declarations, without rule specialization

Expose complete metadata on the existing loaded provider chain:

```kotlin
data class RawMarkDeclarations(
    val names: Set<String>,
    val explicitOwners: Map<String, OwnerKey>,
    val primitiveByOwner: Map<OwnerKey, Boolean>,
)

// Put this member on the EXISTING TaintRulesProvider interface.
interface TaintRulesProvider : CommonTaintRulesProvider {
    val markDeclarations: RawMarkDeclarations
        get() = error("This provider must declare its mark metadata")
    // Existing role methods remain unchanged.
}

// Immutable after merge, validation and model-owner completion.
interface MarkDeclarations {
    fun fullKey(name: String): FullKey // fail if not declared
}
```

The member is on TaintRulesProvider so Kotlin `by base` wrappers and the
existing selected anonymous Semgrep view forward it through their existing
delegation. JIRTaintRulesProvider, Semgrep and Combined supply their own
inventory/merge implementations. Add a complete-wrapper-stack control.
Do not introduce a separate capability that delegated wrappers silently lose.

No default empty inventory is allowed for an unknown provider. A provider
that creates no marks can explicitly certify EMPTY. A custom provider that
creates marks in code must declare them through this metadata boundary.
Dynamic creation of an undeclared name is an input-contract failure, not an
invitation to run a census or guess an owner.

### Pure serialized visitor

Visit all eight method-role lists and staticFieldSource. Read names only;
do not match functions/signatures, resolve annotations, inspect a method body,
specialize AnyArgument or construct bound rule objects.

```kotlin
fun visitCondition(c: SerializedCondition?, emit: (String) -> Unit) {
    when (c) {
        is SerializedCondition.Not -> visitCondition(c.not, emit)
        is SerializedCondition.And -> c.allOf.forEach { visitCondition(it, emit) }
        is SerializedCondition.Or -> c.anyOf.forEach { visitCondition(it, emit) }
        is SerializedCondition.ContainsMark -> emit(c.tainted)
        else -> Unit
    }
}

fun visitItem(item: SerializedItem, emit: (String) -> Unit) {
    when (item) {
        is SourceRule -> {
            visitCondition(item.condition, emit)
            item.taint.forEach { emit(it.kind) }
        }
        is SinkRule -> {
            visitCondition(item.condition, emit)
            item.trackFactsReachAnalysisEnd.orEmpty().forEach { emit(it.kind) }
            // Names only. Never bind, validate positions or execute these actions.
        }
        is SerializedRule.PassThrough -> {
            visitCondition(item.condition, emit)
            item.copy.forEach { it.taintKind?.let(emit) }
        }
        is SerializedRule.Cleaner -> {
            visitCondition(item.condition, emit)
            item.cleans.forEach { it.taintKind?.let(emit) }
        }
    }
    (item.info as? UserDefinedRuleInfo)?.relevantTaintMarks?.forEach(emit)
}
```

Null clean/copy kinds mean all marks; they are not a full mark name.
GeneratedMark values are already serialized full strings in these fields.
Method specialization selects, duplicates or omits positions/actions; it does
not create another mark kind from the chosen parameter index. If a future
resolver can create a new name, it must extend the declaration contract first.

### Load, merge and freeze

```kotlin
// TaintConfiguration.loadConfig: visit each serialized item as it is loaded.
// Keep names/claims only; do not keep a second copy of serialized or bound rules.
config.itemsInLoadOrder().forEach { visitItem(it, declarationBuilder::name) }

fun finalizeDeclarations(inputs: List<RawMarkDeclarations>): MarkDeclarations {
    val names = orderedUnion(inputs.map { it.names + it.explicitOwners.keys })
    val owners = mergeUniqueExplicitClaims(inputs.map { it.explicitOwners })
    val flags = mergeUniqueOwnerFlags(inputs.map { it.primitiveByOwner })
    val fullKeys = linkedMapOf<String, FullKey>()
    for (name in names) {
        val owner = owners[name] ?: OwnerKey.SharedModel
        val value = primitive(name)
        val expected = flags.putIfAbsent(owner, value)
        require(expected == null || expected == value) { "Mixed primitive tracking: $owner" }
        fullKeys[name] = FullKey(encodeGroup(owner, value), name)
    }
    return immutableIndexCopy(fullKeys)
}
```

Include existing generated `markOwners` sidecar keys as declarations even if
one has no retained rule use. Record rule primitive flags when loading the
outer normal/join rule; do not construct a bound rule to recover its option.
The sidecar remains authoritative for imports.

The single pass above completes model flags and checks each generated rule's
declared flag. It does not select a representative at runtime. Validation
uses ordinary maps, not a quadratic pairwise scan or a per-group full list.

Leaf providers export raw names and explicit claims separately. Never assign
SharedModel at a leaf before merging: a different child may supply a generated
name's original owner. Explicit conflicting claims remain errors.

| Provider | Declaration behavior |
| --- | --- |
| JIRTaintRulesProvider | Expose the inventory captured by TaintConfiguration.loadConfig |
| JIRSemgrepRuleProvider | Add original generated owner claims and outer rule flags; reuse loaded config metadata |
| JIRCombinedTaintRulesProvider | Union both loaded inventories and claims, independent of EXTEND/OVERRIDE/IGNORE |
| Spring/MethodExit/StringConcat | Delegate declarations; current extra actions use existing marks or CopyAll/RemoveAll |
| Custom provider | Supply a complete inventory or explicitly certify mark-free EMPTY |

Declaration union is not executable rule combination. Keep every runtime
combination, relevance filter and wrapper role unchanged. Do not rerun a
Semgrep provider loading constructor or create a new wrapper tree to read
metadata. Preserve current runtime empty-end views and final sink-copy policy.

Finalization happens before engine execution. It loads no methods and calls
none of the nine bound-rule provider APIs. There is no fallback binding path.
Defensively copy the resulting names/claims into the immutable index; retain
no mutable declaration builder or bound/serialized rule payload through it.

## 5. MarkTable is an immutable-key interner

Keep compact existing FullMarkId, GroupId and TaintMark handles. Their numeric
values are opaque; report/source/action identities do not depend on them.
Use ordinary append-only interners rather than another owner registry:

```kotlin
sealed interface MarkAtomKey {
    data object ControlZero : MarkAtomKey
    data class Group(val id: GroupId) : MarkAtomKey
    data class Full(val mark: ExactMark) : MarkAtomKey
}

data class ExactMark(val group: GroupId, val full: FullMarkId)

class MarkTable(private val declarations: MarkDeclarations) {
    private val groups = Interner<GroupName, GroupId>()
    private val fulls = Interner<FullKey, FullMarkId>()
    private val atoms = Interner<MarkAtomKey, TaintMark>(zeroKey = MarkAtomKey.ControlZero)

    fun full(name: String): TaintMark {
        val key = declarations.fullKey(name)
        val g = groups.intern(key.group)
        val f = fulls.intern(key)
        return atoms.intern(MarkAtomKey.Full(ExactMark(g, f)))
    }
    fun group(g: GroupId): TaintMark {
        groups.key(g) // validate that this ID exists before creating the atom
        return atoms.intern(MarkAtomKey.Group(g))
    }
    fun key(atom: TaintMark): MarkAtomKey = atoms.key(atom)
    fun isFull(atom: TaintMark) = key(atom) is MarkAtomKey.Full
    fun isGroup(atom: TaintMark) = key(atom) is MarkAtomKey.Group
    fun owner(full: FullMarkId): GroupId =
        groups.existingId(fulls.key(full).group)
    fun groupOf(atom: TaintMark): GroupId = when (val key = atoms.key(atom)) {
        is MarkAtomKey.Group -> key.id
        is MarkAtomKey.Full -> key.mark.group
        MarkAtomKey.ControlZero -> error("ZERO has no group")
    }
    fun name(atom: TaintMark): String = when (val key = atoms.key(atom)) {
        is MarkAtomKey.Group -> groups.key(key.id).encoded
        is MarkAtomKey.Full -> fulls.key(key.mark.full).name
        MarkAtomKey.ControlZero -> "<zero>"
    }
    fun coversAtom(guide: TaintMark, actual: TaintMark): Boolean {
        if (guide == actual) return true
        if (guide == TaintMark.ZERO || actual == TaintMark.ZERO || !isGroup(guide)) return false
        return groupOf(guide) == groupOf(actual)
    }
}
```

`Interner` denotes a small canonical key-to-ID/ID-to-key utility, not a new
reference manager. Retain existing synchronization and cancellation where
required. Full/group/zero remain typed and distinct. Equal immutable keys
return the same handle; a new key cannot change an old key or handle.

Full keys include their complete owner at creation. There is no reserve-now,
assign-owner-later state, mutable owners table, eligibility set, census seal,
per-full property bit, per-group OR, member directory or null publication slot.
Keep ownership validation in the declaration builder, outside MarkTable.

Remove `knownMembers`, `eligibleMembers`, global `groupAtoms()` and both
census-finalization methods. Rename explicit `groupAtoms(GroupSet)` conversion
so it cannot suggest catalog enumeration:

```kotlin
fun atomsFor(groups: GroupSet): MarkSet =
    MarkSet(groups.ids.map { group(GroupId(it)).id }.toIntArray())
```

This converts only IDs already supplied by a request/static join. It discovers
no group. Keep that required conversion in Run1StaticService/Run1PointResume.
Diagnostics can count interned IDs directly; they must not enumerate members.
No runtime algorithm uses those counts as completeness or admission gates.

## 6. Direct primitive filtering

A FULL atom's name remains its existing full string. A GROUP atom's name is
the canonical encoded group name. Both use the existing suffix test. Reuse
the existing MarkPolicy interface and TypeFilter.and without changes.

```kotlin
fun isPrimitiveTracking(mark: TaintMark): Boolean =
    manager.marks.name(mark)
        .endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)

// Existing JIRTypeFilters.make: keep this construction unchanged.
private fun make(t: JIRType): TypeFilter {
    val primitive = generateSequence(t) { it.ifArrayGetElementType }
        .map { it.typeName == "java.lang.Character" || it.unboxIfNeeded() is JIRPrimitiveType }
        .toList()
    return TypeFilter(checker.localFilter(t),
        markPolicy = if (true !in primitive) null else MarkPolicy { mark, level ->
            !primitive.getOrElse(level) { false } || isPrimitiveTracking(mark)
        })
}
```

The existing `primitive` list records the Java type at each consecutive
array-element depth. It is cached with the type filter, not computed per mark.
For `int[][] a`:

| Depth | Value | Type | Primitive flag |
| --- | --- | --- | --- |
| 0 | a | int[][] | false |
| 1 | a[i] | int[] | false |
| 2 | a[i][j] | int | true |

A primitive or boxed value keeps the mark only when its name has the primitive
suffix. Unknown depths retain the existing false default. Path validity is
still checked by the existing path filter. A field accessor stops this
element-depth policy; field transfers use their existing value-type filters.

In Filter.keepPolicyMarks, replace the FULL/GROUP branch and member scan with
one existing policy call:

```kotlin
val atom = TaintMark(id)
val keep = policy.keeps(atom, level)
```

Keep the surrounding mark loop, cancellation checks, result interning,
Character/unboxing handling, path filters, root/element traversal and filter
order unchanged. Keep TypeFilter.and's existing conjunction of policy calls.
Do not add GroupMarkPolicy, policy-kind checks, a separate keepMark helper,
special conjunction code, FullMarkProperty or PropertyMarkPolicy.

Uniformity gives `keepsFull(m,k) = keepsGroup(owner(m),k)` for each declared
full m. The same equality holds after the existing pointwise conjunction.
These facts prove primitive behavior unchanged on valid groups. Arbitrary
FULL predicates retain their current FULL behavior; no claim of automatic
GROUP equivalence for another predicate is made. This change adds no member
scan or custom-policy registration/rejection path.

## 7. Static demand matching without group enumeration

Keep one enabled flag in DemandStore for the shared static permission family.
Do not retain a pattern map for every group. Derive a transient pair from the
actual entry/premise, and apply the existing AP matcher:

```kotlin
fun implicitStaticPair(entry: Pattern): DemandPattern? {
    if (!staticGuidanceEnabled || entry.fact.base != STATIC) return null
    val atom = (entry.fact.mark as? MarkSlot.Concrete)?.mark ?: return null
    if (!marks.isFull(atom)) return null // actual later-run FULL input/premise
    val group = MarkSlot.Concrete(marks.group(marks.groupOf(atom)))
    return DemandPattern(
        Pattern(PathFact(STATIC, emptyList(), Tail.ANY, group), ExclusionSet.Empty),
        Pattern(PathFact(STATIC, emptyList(), Tail.STAR, group), ExclusionSet.Empty),
    )
}

fun near(method: MethodKey, actual: Pattern): List<DemandPattern> =
    storedNear(method, actual.fact.base, actual.fact.path) +
        listOfNotNull(implicitStaticPair(actual))

fun covering(method: MethodKey, premise: InitialAp): List<DemandPattern> =
    storedCovering(method, premise.base, premise.path) +
        listOfNotNull(implicitStaticPair(premise.toPattern()))
```

Retain the ordinary ZERO demand in `near` on the zero base. Existing stored
trie lookups, explicit paired demands and their deduplication stay unchanged.
The actual input object, not base/path alone, is now part of these two APIs.

Update the existing call sites:

```kotlin
// RunMethodAnalyzer.addLink, after run-1: actual packed input leaves.
for (leaf in ops.leaves(added)) {
    found += demand.near(methodKey, leaf)
}
val starts = currentQueries.admit(added, found)

// Existing result restriction: select the pair from the ENTRY premise.
for (guide in demand.covering(methodKey, initial)) {
    yieldAll(ops.restrict(initial, facts, guide))
}
```

The input can contain multiple actual groups; collect the corresponding pairs
while visiting its existing leaves. Do not enumerate every declared group.
Ordinary `emit`/`commonPart`/`restrict` preserve tails, E, mx, layer and paired
exit checks. A same-group selector keeps the actual FULL mark, not a group
value in run-2. A static output alone must not authorize a different premise.
Do not add a guide veto to productive actual-current-query publication.

FLOW, ZERO and nonstatic entries contribute no implicit concrete-group pair.
Retain their ordinary explicit demands and actual-query rules. This change
adds no unconditional static pass-through or executable S identity.

```text
Actual run-2 input: C.f / ANY / Full(g,T)
Before: enumerate pairs for g,h,j,...; only g contributes an initial.
After:  construct pair g from this input; obtain the same initial via emit.

Restriction premise has group g; output has group h:
The implicit g pair contributes no result in either version.
Ordinary explicit demands and actual-query publication still apply.
```

## 8. Setup and runtime pipeline

```kotlin
// Existing rule/config loading; no method binding.
val declarations = finalizeDeclarations(listOf(rootProvider.markDeclarations))

// Gather keeps resolved call graph, roots, lambdas and existing prescan metadata.
// Delete the ownership census and its graph/declared/resolved-target role walk.
// Do not delete normal prescan call resolution or ordinary rule selection.

// Actual analyzer setup.
val ap = ApManager(cancellation, refManager, MarkTable(declarations))
val rules = effectiveNewCoreRules(taintConfig.forBidirectionalCore())
// This view keeps the current empty-end policy and GROUP condition compiler.
// It performs no name observation or owner mutation.

// On actual rule compilation/use:
val full = ap.marks.full(resolvedAction.mark.name)
val runtime = if (run == 1) ap.marks.group(ap.marks.groupOf(full)) else full
```

Remove PrescanResult.markNames, the scratch catalog, observedFullNames,
censusMethod, census view factories/modes, no-insert lookup, trace replay and
wrapper-state adapters proposed only for that census. Remove name-observation
side effects from OwnedRulesProvider; retain its effective rule policy and
condition compiler. Rename the remaining view to describe those duties.

Runtime binding remains lazy and ordinarily cached. Do not change normal /
allRelevant selection, combination order, original generated sink-ID sequence
or runtime wrapper construction. Declarations cannot prune executable rules.

Removing census warming can change a physical cache's first population.
For StringConcat the incidental cached rule.method can differ, but the selected
new-core lowering uses the actual callee and unchanged condition/actions/
ordinals; it does not read that field. Compare AP operations, type filters,
FormOwner/ActionIds and semantic observations, not raw cached object equality.
No StringConcat cache rewrite or new warmup is selected. A custom provider
must not require census callbacks to establish its runtime semantics.

One complete analysis, including all its numbered runs, owns one frozen
declaration snapshot and one MarkTable. Late mark use means interning a name
already in that snapshot; it does not append declarations. It cannot change
a group's owner or primitive flag, or flip a prior filter decision.

A genuinely new provider/configuration generation needs a newly finalized
snapshot and a new analysis/interner before it executes. Opaque IDs are not
portable across these contexts. No live-snapshot mutation, extension lane or
automatic restart/replay protocol is selected. Ordinary later iterations in
the same analysis retain their interner, valid records and actual-query rules.
Symbolic guidance needs no refresh when an already-declared mark is first used.

## 9. Proofs and limits

The selected reference is the all-serialized, uniformly grouped input domain.
The earlier eligible/member-OR and no-retain-census designs are historical.
Do not cite their proofs as proof that mixed groups or undeclared names remain
supported. The new input assertions are selected semantic restrictions.

Required constructive results:

1. **Declaration completeness.** By structural traversal of serialized
   conditions/actions, every bound executable mark name belongs to the loaded
   declaration set. Binding preserves the exact kind strings, even when it
   selects/duplicates positions, folds a branch or adds array positions.
   Code-created provider marks meet the same explicit declaration contract.
2. **Owner preservation.** Merge all explicit claims before fallback. An
   imported/generated name retains its original top-level owner. Uniformity
   validation rejects mixed flags and contradictory declarations before use.
3. **Encoding and interning.** Canonical group names distinguish owner/flag.
   Immutable key interning preserves old keys/IDs and makes repeated insertion
   idempotent. Group projection preserves the full owner. ZERO stays separate.
4. **Primitive equivalence.** In a valid nonempty group, all member primitive
   tests equal the group's test. Therefore scalar existential evaluation equals
   direct GROUP evaluation. An actual accepted full witness remains accepted.
   Conjunction preserves equality using the same input, without enumeration.
5. **Implicit permission equivalence.** For an actual FULL static input,
   every old group pair except the input owner's pair contributes no initial.
   For restriction, every pair except the premise owner's pair contributes
   no result. Keep the same selected pair and opaque AP transfer; thus the
   successful contribution set equals the old all-groups family. This is not
   equality of candidate lists or diagnostic callback counts.
6. **No fact creation.** Declaration validation and key interning create
   no taint/source. A permission alone emits nothing. FLOW/ZERO receive no
   concrete-group permission. Existing writer/source/guard derivations remain.
7. **No specialized setup work.** The metadata collector's recursive steps
   read only serialized syntax and owner sidecars. Its model makes zero
   method-specialization calls. Generic wrapper purity or a stable census
   replay oracle is no longer an assumption needed for setup.

The additive model is [UniformMarkGroups.lean](lean/ApSpec/UniformMarkGroups.lean),
with its [focused audit](lean/uniform-mark-groups-audit.lean).
Removing the policy tag changes no model definition or theorem. The existing
primitive/conjunction results cover the direct MarkPolicy path; no new Lean
execution is required for this specification-only simplification.

| Obligation | Checked constructive result |
| --- | --- |
| Uniform declared property and stable owner | `validated_property`, `validated_owners`, `uniform_group` |
| Canonical typed owner/flag encoding | `decode_encode`, `encode_injective` |
| Direct primitive decision and existing conjunction | `full_group_policy`, `nonempty_scalar_eq_group`, `primitive_policy_and`, `group_policy_and`, `same_witness_and_scalar` |
| Binding cannot invent a name from position specialization | `Serialized.binding_names_scanned`, `bound_property_from_validated_declarations` |
| Serialized setup makes no binding query | `Serialized.setup_scan_and_no_binding_queries` |
| Immutable canonical key slots and projection | `Interner.existing_slot_stable`, `intern_lookup`, `intern_idempotent`, `projected_key_exact` |
| Entry/premise-specific implicit permission | `Guidance.emission_equivalence`, `restriction_equivalence`, `premise_member_union` |
| Same pair and no result without ordinary transfer | `Guidance.selected_uses_same_pair`, `no_output_without_ordinary_result` |
| FLOW/ZERO have no implicit concrete-group pair | `Guidance.flow_zero_no_selection` |
| Actual-query/other ordinary publication paths retained | `Guidance.unchanged_bypass_equivalence` |
| Scoped reduction in candidate-guide applications | `Guidance.selected_candidates_nonincrease`, `selected_candidates_strict` |

The encoding model is an injective typed owner/flag pair. Exact Kotlin string
encoding and suffix parsing remain adapter obligations. Declaration validation
is finite; the Kotlin one-pass map implementation must refine it without
changing original import precedence. The serialized binding relation permits
position selection/copying, not arbitrary fresh name creation.

The guide proof compares successful contribution sets against a complete
proof-only enumerated family. It factors the existing entry/premise mark
guard, then uses the same opaque AP operation and paired exit. It is not
equality of candidate lists/callback counts or a proof of all AP primitives.
The unchanged-bypass theorem keeps productive actual-query publication
outside this implicit permission replacement. No runtime group inventory is
prescribed by the reference lists in the proof.

Exact Kotlin serialized-field coverage, suffix/encoding implementation,
provider declaration forwarding, unchanged full rule lowering and AP matching
remain production adapter duties. No proof adds a runtime validator for
fields, guards or source histories.

The primitive and permission results are local refinements of the validated
reference. Existing source, ND, request, static supply, record and finite
handoff models remain catalog-parametric. Instantiate their full universe
with the validated serialized declarations. No JVM-wide finding, termination,
wall-time or universal 60-second theorem is claimed.

## 10. Implementation sequence, removals and validation

1. Collect names and owner/flag claims during serialized loading. Merge and
   validate all provider metadata before using MarkTable. Add mixed-group,
   collision/import and undeclared-name controls first.
2. Replace mutable ownership/eligibility with immutable-key interning. Update
   group/full projection, typed exclusions and fixtures; keep original FULL
   names, opaque-ID stability within the interner and public rule identities.
3. Encode the group flag and use the current primitive policy directly.
   Remove the member branch in Filter.keepPolicyMarks. Reuse MarkPolicy,
   JIRTypeFilters.make and TypeFilter.and; add no policy types or checks.
4. Replace global static pattern enumeration with entry-dependent matching.
   Compare successful complete emitted initials/restricted results, including
   cross-group, FLOW/ZERO, tails/E/mx and both layers.
5. Delete the entire ownership-binding census and its state. Keep prescan,
   normal rule selection/binding/caches and current wrapper factories.
6. Run the selected common/JVM/application/rule gates. When implementation is
   requested, commit only this iteration's changes, then repeat the same four
   task.md project configurations with unchanged heaps, timeouts and main pin.

| Component | Selected change |
| --- | --- |
| Loader / RuleConversionCtx / TaintRuleFromSemgrep | Original owners and primitive flags before flattening; no new executable marks |
| Serialized TaintConfiguration.loadConfig | Pure complete mark-name collector; no bound-rule query |
| Provider declaration capability | Merge all loaded branches; preserve runtime wrappers/roles and original ownership |
| ap/RuleMarkCatalog.kt | Immutable-key interners; remove owners/eligibility/census/member/global-group APIs |
| ap/Filter.kt | Call the existing policy on the runtime atom; remove FULL/GROUP branching and member scans |
| ap/Primitives.kt, JVM JIRTypeFilters | Reuse MarkPolicy, TypeFilter.and, cached type/depth flags and existing suffix test unchanged |
| DemandStore and two query call sites | Actual-input/premise-specific implicit static pair |
| Run1StaticService / Run1PointResume | Rename explicit GroupSet conversion to atomsFor; no universe lookup |
| JIRBidiAnalysis / OwnedRulesProvider | Remove bound census, scratch/full-name handoff and observation side effects; retain effective rules |

Required controls:

- Normal/join/import ownership; delimiters in IDs; raw labels imitating the
  primitive suffix; both uniform flags; mixed rule/model/SharedModel rejection.
- All nine serialized roles, signed condition branches, null clean/copy kinds,
  relevant metadata and disabled end-action names without action validation.
- No binding calls at declaration setup, including normal/allRelevant and
  static fields. Unknown/custom providers require declarations, not fallback.
- Equal immutable keys/handles, owner projection, typed Group/Full exclusions,
  no pending ownership, no unknown-name registration and no ZERO group.
- Primitive/boxed/Character/reference/array levels, FULL unchanged, direct
  GROUP suffix filtering and the existing TypeFilter.and on the same input.
- Enumerated-reference versus query-specific permission results for static
  actual inputs/premises, cross-group output, packed groups, late declared
  marks, FLOW/ZERO, nonstatic input and all ordinary AP/paired-exit checks.
- No source/fact from a name or permission; unchanged real writes/kills,
  requests, conditional sources, ND and exact source/action identities.
- Warmed versus unwarmed StringConcat at distinct actual signatures: compare
  compiled AP operations, filters and ActionIds under the same FormOwner.

For 20 fixed primitive GROUP filter calls at declared name populations 51 and
1,172, require zero full-member scans, zero owner/member probes and zero group
inventory scans. Count direct name accesses/suffix tests normally. Remove the
old opaque GROUP fallback diagnostic; retain arbitrary-policy FULL controls.
No new GROUP-policy type or rejection control is required. Compare complete
Facts, not booleans.

For setup, require zero bound-rule lookups caused by mark declaration work
and zero census-caused runtime cache insertions. Measure ordinary prescan
binding separately. Existing bound cache entries/live forms remain; do not
claim that the full measured cache union disappears. No census memo, property
classifier, generic cache access mode, member list or group-pattern map remains.

Report setup, engine and barrier times separately. Always include latest
reached/completed/accepted runs and strict finding identities. No extra test
configurations, heap changes, IR/cache-manager changes or pending-memory caps
are selected.

## 11. Measurement and proof checkpoint

These are B88 measurements, not results for this revision:

| Project | Latest reached / completed engine / accepted forward report | Run-1 engine |
| --- | --- | ---: |
| OWASP | run-1 / none / none | 906.998 s, TIMEOUT |
| Conductor | run-1 / none / none | 986.046 s, TIMEOUT |
| ThingsBoard | run-1 / none / none | 239.084 s, memory-guard OOM |
| Stirling-PDF | run-2 / run-1 / run-1 | 925.594 s, COMPLETE; run-2 TIMEOUT |

B88's selected local gate passes 2,063 tests; fixed-project acceptance fails.
The fixed-fact diagnostic measured `20*51` versus `20*1172` owner probes for
GROUP and zero for FULL. PDF/Conductor show 59.33%/43.80% inclusive
eligibleMembers Java sample shares. These are not wall-time predictions.

ThingsBoard's four bound-cache families have a nonadditive retained union
of 3,160,452,352 B. Not all of it is census-only. Removing mark-setup queries
avoids their work/retention; ordinary prescan/runtime caches still exist.
The 60-second target is run-1 engine time. No new project run is part of this
spec update. See the [current performance report](../docs/reports/bidirectional-run1-normalized-groups-performance-20261019.md).

The new focused model/audit pass on Lean 4.33.1: **565 owned declarations,
zero unexpected axioms, 20 executable controls**. No sorry, custom axiom or
classical choice is used; the audit permits standard `propext`/`Quot.sound`.
Only the focused target/audit ran under the three existing workload locks.
All checks are terminal and the locks are released.

Receipt, exact sources/commands and logs:
`/drive-testcomp/opentaint-go-rules/grouped-statics-alias-writes-20261019/validation/uniform-mark-groups-proof/receipt.json`.

```sh
# From spec/lean, under the three existing workload locks:
lake env lean -o .lake/build/lib/lean/ApSpec/UniformMarkGroups.olean ApSpec/UniformMarkGroups.lean
lake env lean uniform-mark-groups-audit.lean
```

Earlier GroupPrimitiveMetadata and OwnershipCensus models remain unchanged
as historical evidence. Their 200/27 and 448/19 declaration/control audits
do not certify this revision. Runtime code remains unchanged; no project
run or Kotlin implementation gate was executed for this spec update.
