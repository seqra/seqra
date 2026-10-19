# Use all known marks and group primitive metadata

The complete selected next-iteration contract, including the ownership-census
optimization, is now in
[Group primitive filtering and ownership census](../../spec/run1-group-policy-and-ownership-census.md).
Use that spec for implementation. This proposal retains the earlier design
and measurement explanation.

Date: 2026-10-19. Status: selected proposal. Analyzer implementation is
unchanged. Use ASD-STE100 Simplified Technical English. Code names retain
their defined meanings.

This revision uses the user's decision: consider all marks eligible. It
replaces the earlier eligible-member/two-bit proposal. It follows the
[proof guide](../../spec/proof-guide.md) and the
[root-cause analysis](../reports/bidirectional-run1-work-amplification-root-cause-20261019.md).
It changes the mark-membership part of
[grouped-run1-statics revision 2.1](../../spec/grouped-run1-statics.md).
It retains that spec's other contracts.

## 1. Selected change and limits

**Major semantic change:** every known full mark participates in its owner
group. An active rule reference is no longer required for membership.

```text
Known full mark = a full ID already created in the shared MarkTable.
Member of g    = a known full mark whose registered owner is g.

ZERO is control, not a full mark.
GROUP handles are selectors, not new full members.
A reserved full ID with no owner has no group membership yet.
```

This includes names already registered from inactive rule declarations,
imported names, GeneratedMark names and model names. It does not create every
possible generated name. Keep current discovery and ownership completion.
Do not visit disabled actions to invent additional names.

| Mechanism | Current | Proposed |
| --- | --- | --- |
| Group membership | Known, owned and active/eligible full IDs | All known full IDs with that owner |
| Membership maintenance | Separate eligible set and observation events | Existing full-ID and owner tables only |
| JVM primitive policy on GROUP | Enumerate eligible members for each query | Read one primitive-tracking bit for a valid group |
| Unknown/custom group policy | One scalar predicate over eligible members | Same predicate over known owned members |
| Implicit static group guides | Enumerate eligible groups | Enumerate all groups with a known full owner |
| FULL type policy | Existing scalar full-name test | Unchanged |
| Later-run Group/Full guide matching | Compare the actual full mark's owner | Unchanged |
| Rules, sources, role admission and full signed conditions | Existing rules | Unchanged |
| Disabled end actions | Empty in all runs | Unchanged; membership does not enable actions |
| Fields, tails, exclusions, initial grammar, requests, layers and statics | Current selected contract | Unchanged |
| RefManager, caches and IR | Existing implementation | Unchanged |

Membership is permission to consider a group member. It is not proof that
the mark reaches a program point. A catalog name or implicit demand guide
cannot create a source, static write, executable full fact or finding.
Original guards, source/action occurrences and writer derivations still apply.

## 2. Problem and latest measured scope

Current GROUP filtering first scans the entire eligible full-mark catalog,
constructs one group's member list, then runs the scalar policy:

```kotlin
val members = marks.eligibleMembers(group)
return members.isEmpty() || members.any { policy.keeps(it, level) }
```

The closed diagnostic uses one selected member, 51 groups, one input fact and
20 filter calls. Each call retains its input object within its catalog case.

| Atom domain | Eligible full marks in current code | Groups | Calls | Full-owner map lookups |
| --- | ---: | ---: | ---: | ---: |
| FULL | 51 | 51 | 20 | 0 |
| FULL | 1,172 | 51 | 20 | 0 |
| GROUP | 51 | 51 | 20 | 1,020 |
| GROUP | 1,172 | 51 | 20 | 23,440 |

Thus unrelated members add work to each filter call. PDF's run-1 Java profile
has 59.33% inclusive `eligibleMembers` samples; Conductor has 43.80%. These
are sample shares, not predicted wall-time savings.

Latest measured reached / completed engine / accepted forward report:

| Project | Latest runs | Run-1 engine |
| --- | --- | ---: |
| OWASP | run-1 / none / none | 906.998 s, TIMEOUT |
| Conductor | run-1 / none / none | 986.046 s, TIMEOUT |
| ThingsBoard | run-1 / none / none | 239.084 s, memory-guard OOM |
| Stirling-PDF | run-2 / run-1 / run-1 | 925.594 s, COMPLETE; run-2 TIMEOUT |

There is no new project measurement for this proposal. The proposed change
does not establish the 60-second engine target or solve ThingsBoard's retained
memory problem by itself.

## 3. Reference semantics and one-bit result

Let `K(g)` contain all known full marks with registered owner g. Let
`primitive(m)` be the exact existing JVM test:

```kotlin
fullName.endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)
```

Let `requires(level)` be the current primitive/boxed type test at an array
element level. Keep the explicit Character case, unboxing, array traversal
and false result beyond the known levels.

```text
referenceKeep(g, level) =
    K(g).isEmpty OR exists m in K(g): (!requires(level) OR primitive(m))

hasPrimitive(g) = exists m in K(g): primitive(m)

For a valid nonempty group:
    fastKeep(g, level) = !requires(level) OR hasPrimitive(g)
```

This needs one primitive bit per valid group. It needs no eligible set,
`hasEligible` bit, eligibility counter or per-group member list. Shared/model
groups can contain both primitive and other marks.

An allocated group key can be empty, for example after rejected conflicting
registration. Use an unpublished/empty slot marker outside the one-bit fast
path. Preserve the scalar empty-group result; do not assume that false and
empty mean the same thing.

The new membership rule is not equivalent to the old rule. Nor is every old
GROUP decision widened:

```text
Primitive-level query, same owner g:

Known T is nonprimitive and active.
Known U is primitive-tracking but inactive.
    before: only T considered -> reject
    after:  T and U considered -> keep

Known T is nonprimitive; no member was active.
    before: eligible set empty -> keep
    after:  known set contains T -> reject
```

These are selected membership changes. The optimization theorem compares
the one-bit implementation with the **new all-known reference**, not with
the old eligible reference. For an actual full member that passes the scalar
policy, its group passes the new reference. This is the local coverage
obligation; whole-analyzer acceptance still requires the normal gates.

## 4. Catalog state and owner publication

The generic AP module must not import the JVM suffix. Install one optional,
pure, immutable full-name property when the actual catalog is constructed:

```kotlin
class FullMarkProperty(val holds: (String) -> Boolean)

class ApManager(
    val cancellation: Cancellation,
    refManager: RefManager = RefManager(),
    fullMarkProperty: FullMarkProperty? = null,
) {
    val marks = MarkTable(fullMarkProperty)
    // Existing manager state remains.
}

class MarkTable(private val property: FullMarkProperty? = null) {
    // Existing full IDs, handles, owners, group keys and census state remain.
    // Remove: private val eligible = LinkedHashSet<FullMarkId>()
    private val fullHasProperty = ArrayList<Boolean>()
    private val groupHasProperty = ArrayList<Boolean?>()
    // null: allocated group has no successful full-owner assignment.
    // false/true: published membership exists; value is its property OR.
}
```

The nullable slot is the publication marker, not an active-eligibility bit.
Even with no property descriptor, a successful owner assignment changes the
slot from null to false. Generic clients can use the same owned-group listing.

All mutation below runs under the existing MarkTable monitor:

```kotlin
// In the common full-ID creation helper used by register AND reserveFull:
fullHasProperty.add(property?.holds?.invoke(name) == true)

// In the common group-ID allocation helper:
groupHasProperty.add(null)

// After successful owner assignment, including existing same-owner repeats:
private fun creditOwned(full: FullMarkId, group: GroupId) {
    groupHasProperty[group.value] =
        (groupHasProperty[group.value] == true) || fullHasProperty[full.value]
}
```

The property is evaluated once per new full ID. Credit it when its owner is
assigned, even if no active rule observes that name. Boolean OR makes repeat
credit idempotent. There is no removal, rename, mutable classifier or owner
reassignment operation.

Preserve current allocation and conflict order. A rejected owner must not
credit its group, alter the old owner's bit or publish an orphan group key.
Do not change existing full/handle IDs while factoring creation helpers.

### Reservation is not owner assignment

```kotlin
reserveFull(name)              // may leave this full ID unowned
register(name, originalOwner) // assigns owner and credits the property
finishOwnershipCensus()       // assigns remaining SharedModel owners, then seals
```

Do not assign SharedModel eagerly in `reserveFull`. A later rule declaration
may provide the original owner. Preserve fixture-model assignment and the
current late-provider/model completion paths.

`markEligible` currently also completes late SharedModel ownership. Remove
its eligibility state update, but keep this side effect at its existing call
sites. Rename it to describe ownership, for example:

```kotlin
@Synchronized
fun completeObservedFullOwnership(atom: TaintMark) {
    val full = requireFullHandle(atom) // preserve existing handle validation
    if (isOwnershipCensusComplete && full !in owners) {
        register(fullNames[full.value], MarkGroupKey.SharedModel)
    }
    // Before census completion, leave owner assignment to the existing flow.
    // No eligible insertion and no second metadata credit are needed.
}
```

`observeModelReference` still reserves the name and invokes this completion.
Update current `markEligible` callers to the ownership helper; retain their
discovery/order where required. Known unowned IDs contribute to no group.
Grouped execution retains the existing census-before-execution contract.
Post-census raw reservations still wait for their existing owner-completion
step. They must not create published GROUP facts before that step.

| Mutation | Required action |
| --- | --- |
| New full ID in any path | Initialize its immutable full property |
| New group key | Initialize null publication slot |
| Successful original/model/fixture owner assignment | Credit full property, including inactive names |
| Ownership census | Complete pending owners before publishing completion |
| Active rule observation | Keep required discovery/owner completion; no membership event |
| Repeated name or same owner | No new ID; repeated OR is harmless |
| Conflicting owner | Existing error; no credit for rejected owner |
| Late model/provider extension | Existing owner protocol; publish metadata with successful ownership |

## 5. Member and group consumers

Use a clear API name. `knownMembers(g)` replaces `eligibleMembers(g)` for
the scalar fallback and reference tests. It reads existing full-ID/owner
state; it does not maintain another directory:

```kotlin
// Pseudocode; use the existing catalog monitor and handle validation.
fun knownMembers(g: GroupId): List<TaintMark> {
    check(isOwnershipCensusComplete) {
        "Finish the mark ownership census before group execution"
    }
    return fullIdsInCreationOrder()
        .filter { assignedOwnerOrNull(it) == g }
        .map { fullAtom(it) }
}

fun groupAtoms(): List<TaintMark> =
    groupIdsInAllocationOrder()
        .filter { groupHasProperty[it.value] != null }
        .map { group(it) }
```

Define deterministic order: full members use FullMarkId creation order;
groups use GroupId allocation order with empty slots omitted. This replaces
active-observation order. Preserve original rule/ActionId identities; do not
promise old callback counts or arbitrary side-effectful-policy call order.

Only these production consumers need membership changes:

```text
Filter.keepPolicyMarks       -> knownMembers fallback / metadata fast path
JIRBidiAnalysis catalog stats -> knownFull and knownOwnedGroups
SharedStaticGroupDemand      -> all known owned groups for implicit guides
```

The explicit `groupAtoms(GroupSet)` operation still maps the supplied group
IDs. Do not expand it to all known groups.

Production run-2 already loops over actual FULL input leaves and compares
their owners with GROUP guides through `coversAtom`. Keep this path. It does
not enumerate catalog members to emit executable full marks. Update reference
drivers that do enumerate members to use `knownMembers`.

More known groups can create more implicit static guide patterns. The guide
still needs an actual full fact, rule/source occurrence or writer derivation.
It is not a supply. Keep the original signed full-rule fold, residual-negative
policy and role admission before GROUP lowering. The word "eligible" in
source/rule admission must not be mechanically replaced across the analyzer.

Keep current name discovery and ownership census in this iteration. Removing
bound-rule census queries needs a separate completeness proof for all names
and owners. It is not required to remove active-eligibility tracking.

## 6. Recognized policy and filter pipeline

Keep the MarkPolicy SAM and its scalar method. Add an immutable recognized
policy for the existing JVM meaning:

```kotlin
class PropertyMarkPolicy(
    val catalog: MarkTable,
    val property: FullMarkProperty,
    requiredLevels: BooleanArray,
) : MarkPolicy {
    private val required = requiredLevels.copyOf()
    fun requires(level: Int) = required.getOrElse(level) { false }

    override fun keeps(mark: TaintMark, elements: Int): Boolean =
        !requires(elements) || property.holds(catalog.name(mark))
}

internal object JvmPrimitiveProperty {
    val descriptor = FullMarkProperty { name ->
        name.endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)
    }
}

// JIRBidiAnalysis.prepareShared: before actual-catalog registration.
val ap = ApManager(cancellation, refManager, JvmPrimitiveProperty.descriptor)
val rules = OwnedRulesProvider(taintConfig.forBidirectionalCore(), ap.marks)
// Continue current copied-name import and ownership census.

// JIRTypeFilters: same Character/unbox/array-level computation as today.
val primitive = currentPrimitiveLevels(t)
val markPolicy = if (true !in primitive) null else PropertyMarkPolicy(
    manager.marks, JvmPrimitiveProperty.descriptor, primitive.toBooleanArray())
return TypeFilter(checker.localFilter(t), markPolicy)
```

The scratch name-gathering catalog needs no JVM property. Actual full IDs
are classified in the actual catalog. Do not infer all names' properties
from one rule flag or representative member.

```kotlin
@Synchronized
internal fun groupProperty(g: GroupId, expected: FullMarkProperty): Boolean? {
    if (property !== expected || !isOwnershipCensusComplete) return null
    return groupHasProperty[g.value] // null also means empty/unpublished
}

fun keepMark(atom: TaintMark, policy: MarkPolicy, level: Int): Boolean {
    val g = marks.groupId(atom)
    if (g == null) return policy.keeps(atom, level) // unchanged FULL path

    if (policy is PropertyMarkPolicy && policy.catalog === marks) {
        val bit = marks.groupProperty(g, policy.property)
        if (bit != null) return !policy.requires(level) || bit
    }

    val members = marks.knownMembers(g)
    return members.isEmpty() || members.any { policy.keeps(it, level) }
}
```

The same property object and catalog are required. An arbitrary lambda is
not recognized by its name, return samples or a coincidentally equal key.
Empty/unpublished groups and missing capabilities use the scalar reference.
Before census completion, the metadata reader returns no answer and the
scalar GROUP entry retains the census guard. Returning null must not enable
early filtering through an unguarded owner-or-null scan. FULL scalar
filtering and pre-census name/handle reservation retain their current rules.

The fast query uses current metadata under the existing monitor. It does not
cache a past query result or add an invalidation/replay mechanism. Existing
late-provider rules still apply. Keep root/element traversal, path filters,
all filter placements and cancellation checks.

### Preserve one member for policy conjunction

```text
(!a(level) OR primitive(m)) AND (!b(level) OR primitive(m))
    = !(a(level) OR b(level)) OR primitive(m)
```

For two PropertyMarkPolicy instances with the same catalog and property
identity, combine requirement masks by OR. Path filters still combine by AND.

For any other pair, combine the original scalar predicates, then use one
`knownMembers` existential. Do not AND independent group answers:

```text
K(g) = {T,U}; policy A accepts T only; policy B accepts U only.
exists A = true; exists B = true; exists (A AND B) = false.
```

Mixed/opaque policies retain this scalar fallback. No general per-policy
truth cache, member directory or multi-property mask is selected.

## 7. Example and source of savings

```java
int n = parameter();
consume(n); // type filtering at a primitive position
```

Fact annotation: `P -> n/$/Group(g) @DEMAND`. Known full marks T and U belong
to g; T is nonprimitive and U is primitive-tracking. P, field/tail state and
layer are unchanged by filtering.

```text
Current: each filter call scans every eligible full mark, selects g,
         then evaluates member suffixes. Active eligibility decides which
         members can witness the group.

New reference: consider all known owned T,U. U witnesses keep.

New optimized: successful owner registration has already set
               hasPrimitive[g]=true. Read that bit; retain the same grouped
               fact as the new reference. No member list or name query.
```

For Q recognized GROUP visits and N known full IDs, enumeration under the
new reference costs O(Q*N), plus member predicates and temporary lists.
The fast query costs O(Q). Full-name classification costs O(N) property
evaluations; owner credits cost O(C) for C successful assignments/repeats.
String classification and existing map costs are separate from query costs.
Retained metadata is O(N+groups) bits/slots. Remove the eligible set.

Unknown policies still scan. More known groups can also admit more work;
measure that effect separately from avoided scans. No whole-project speedup
or termination bound follows from the local query theorem.

## 8. Proofs and implementation obligations

The additive model is
[GroupPrimitiveMetadata.lean](../../spec/lean/ApSpec/GroupPrimitiveMetadata.lean).
`AllKnown` is the selected reference. The enclosing two-bit eligible model
remains a comparison, not the selected runtime design.

| Obligation | Constructive proof / required adapter |
| --- | --- |
| Active eligibility has no effect | `AllKnown.ignores_eligibility` |
| Owner-only scalar reference equals its summary | `AllKnown.reference_eq_guarded` |
| One bit suffices for a valid nonempty group | `AllKnown.one_bit_nonempty` |
| Summary is an executable owner/property OR fold | `AllKnown.hasPrimitive_eq_one_bit_summary`, `one_bit_summary_append` |
| Actual full-policy witness keeps its group | `AllKnown.owned_entry_coverage`, `member_one_bit_coverage` |
| Empty slot cannot be treated as nonprimitive | `AllKnown.empty_boundary`; use null/fallback at the boundary |
| Conjoined recognized policies use one witness | `AllKnown.all_known_conjunction` and `full_policy_conjunction` |
| Creation and duplicate credit | `AllKnown.creation_bit_update`, `register_duplicate_noop`; Boolean OR idempotence |
| Existing records and owners remain stable | `AllKnown.register_preserves_existing`, `owner_cannot_be_reassigned` |
| Old/new eligibility are not universally ordered | `AllKnown.eligibility_change_can_reject`, `eligibility_change_can_keep` |
| Query work comparison under the same new policy | `fixed_members_nonincrease`, `fixed_members_strict`, with the new member population |

The checked creation model combines an owned full record's creation and
publication. Kotlin can reserve its ID before ownership is known. Map the
model's publication to the successful owner assignment, not raw reservation.
Reuse the owner-only Entry model for pending IDs. It contributes nothing
until ownership is assigned. The Kotlin adapter must show that every owner
assignment credits exactly the original full property before publication.

The model does not authorize silent acceptance of conflicting runtime owners:
its duplicate-ID no-op is a state law. Keep the current runtime exception.
It has no removal, reassignment or mutable property operation.

Exact suffix wiring, every Kotlin creation/owner hook, null boundary,
deterministic ordering, synchronized publication and complete Facts traversal
remain implementation-refinement obligations. The finite cost result counts
entry visits and metadata reads, not JVM time, lock time or allocated bytes.
The current Kotlin diagnostic supplies the measured full-catalog probe count.
It does not measure the proposed fast path.

The focused [audit](../../spec/lean/group-primitive-metadata-audit.lean) passes
on Lean 4.33.1: **200 owned declarations, no unexpected axioms, 27 executable
controls**. There is no sorry, custom axiom or classical choice. The audit
permits Lean's standard `propext` and `Quot.sound` only. The check covers this
model and audit under the three existing workload locks. No project workload
or analyzer implementation was run for this proposal. Exact commands, hashes
and logs are bound by
`/drive-testcomp/opentaint-go-rules/grouped-statics-alias-writes-20261019/validation/group-primitive-metadata-proof/coverage-receipt.json`.
Prior eligible and all-known receipts remain archived beside it.

## 9. Implementation sequence and acceptance

1. Update the normative spec before runtime edits. Replace active full-mark
   membership with the known-owned domain in its catalog, type-policy and
   implicit-static-guide sections. Keep rule/source eligibility distinct.
   Replace claims that disabled end-only names cannot be catalog members;
   their actions remain disabled. Recheck proofs that enumerate members or
   groups against this domain. Do not claim old/new semantic equivalence.
   Specifically update grouped-run1-statics sections 3, 5, 11 and its removal
   table, the member/handoff clauses in
   [rule-group-marks](../../spec/rule-group-marks.md), and the catalog/filter/
   end-action/handoff parts of the
   [prior implementation proposal](bidirectional-grouped-statics-implementation-20261019.md).
2. Remove the eligible set and membership updates. Introduce `knownMembers`
   and deterministic known-owned group enumeration. Preserve all discovery,
   fixture, owner-completion, conflict and late-extension paths.
3. Add the optional property and owner-publication metadata. Map every
   `register`/`reserveFull` creation and every owner assignment to the proof.
4. Add PropertyMarkPolicy, the JVM constructor wiring and typed AND. Switch
   only its compatible GROUP filter branch to the bit. Keep other policy
   families on the all-known scalar reference.
5. Update catalog stats and reference drivers. Test inactive known marks,
   mixed/model groups, pending/late owners, duplicates, rejected conflicts,
   no-descriptor cases, mismatched policy catalogs, empty slots and ordering.
   Check that pre-census GROUP execution is rejected and FULL scalar filtering
   still works before census completion.
   Compare complete Facts at root and array levels. Verify original guards,
   disabled actions, fields/layers and actual FULL guide matching.
6. Extend the fixed-fact diagnostic with recognized policies. For 20 fixed
   primitive-level calls at 51 and 1,172 known members, require zero member
   probes, 20 metadata reads, no per-visit suffix test and no member lists.
   Keep an opaque-SAM control on the scalar fallback. With all its original
   marks eligible/known, it must retain its original `20*N` owner probes.
   Preserve FULL's zero member probes and scalar decisions.
7. Run the required selected core/JVM/application/rule gates. Then use the
   same four task.md project configurations, heaps and timeouts. Report
   engine/setup/barrier costs, changes in admitted groups/facts and strict
   finding identities. Always report latest reached/completed/accepted runs.

Separate the membership change from the fast-path optimization in review and
tests. Prove optimized/reference equality under the new domain; use explicit
old/new counterexamples for the changed domain. Recheck the normal acceptance
gate after both changes. No runtime implementation is part of this proposal.

Affected components: `RuleMarkCatalog.kt`, `ApManager.kt`, `Primitives.kt`,
`Filter.kt`, `JIRTypeFilters.kt`, `JIRBidiAnalysis.kt`, membership callers,
reference drivers, focused tests and the normative member-domain proofs.
No new GroundingSupport, field validator, fact history, static routing
mechanism, request algorithm, cache manager or IR representation is selected.
