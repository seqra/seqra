# Static-domain fix and factored frozen requests

Date: 16 October 2026. Status: selected implementation proposal. No runtime change
is made by this document.

The next iteration contains two changes, in this order:

1. Fix the missing static-domain coverage that aborts run-1.
2. Factor frozen static requests without changing their logical membership.

This document replaces the selected parts of the
[broader proposal](bidirectional-next-iteration-4c64c579f-20261015.md).
Cleaner transfers, form compilation, alias deferral, caller-input representation,
request scheduling, reference managers and IR storage are outside this iteration.

## 1. Evidence and intended result

The latest measured analyzer core is
`4c64c579f4c4a10fc8ffcc5bcd0bf1bcd45cf877`. The later documentation commit
`bf0cc9926` does not change that core. Use the
[project report](../reports/bidirectional-run1-mark-request-performance-20261015.md).
No new project scan was made for this proposal.

| Project | Latest reached | Latest completed engine | Accepted forward report | Run-1 engine |
| --- | --- | --- | --- | --- |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 245.412 s; COMPLETE |
| Conductor | run-1, forward; FAILED | None | None | 8.551 s; FAILED |
| ThingsBoard | run-1, forward; FAILED | None | None | 47.387 s; FAILED |
| Stirling-PDF | run-1, forward; FAILED | None | None | 11.680 s; FAILED |

The three failures lose 7, 14 and 17 baseline rule/sink identities respectively.
Their short failed runs are not passes of the 60-second completed run-1 target.
There is no OOM heap from this batch.

| Change | Current behavior | Proposed behavior | Expected benefit |
| --- | --- | --- | --- |
| Static-domain fix | A delivered static class can be absent from the frozen global domain; the guard aborts before an accepted report | Collect every possible selected producer coordinate before freezing; retain the guard and repair the identified omission | Restore valid analysis and finding coverage |
| Factor frozen requests | Each profile expands common marks into a map row for every selected field | Borrow the same logical row from common and sparse field descriptors | Avoid repeated map entries, slot references and row construction |

ThingsBoard's plan has 3,000,408 physical frozen-map rows across 1,452 distinct map
roots. Its logical per-method totals are 15,671,691 rows and 63,549,683 memberships.
The mark payloads already share storage. The reported 47,142 mark-set roots and
55,832 primitive cells also include wide/class metadata. Thus the target is the
expanded row relation, not 63.5 million separately allocated mark cells.

No saved-byte estimate is available. Replacement descriptors and query costs must
be measured. Field-specific entries remain in the sparse map. The 3,000,408 rows
are a gross target, not the number of entries guaranteed to disappear. A profile
with only explicit field needs may have little map-entry saving.
Catalog setup takes 3.667 seconds, separately from 69.447 seconds of
metadata preparation. Factoring does not remove that metadata phase and does not
by itself establish a 60-second engine time.

## 2. Keep domain, ownership and frozen selection separate

Use these names throughout implementation and proofs:

```text
Sigma.classes       all class coordinates a selected producer can deliver
Sigma.fields        all (class, first static field) coordinates it can deliver
H[method]           transitive structural ownership/read footprint
S[method]           finite field-slot set used to build the frozen request base
B[method, slot]     immutable precomputed mark requests
D[fullKey, slot]    dynamic requests not already in B
R[fullKey, slot]    effective requests = B union D
P[fullKey]          actor-owned temporary RequestStorage
```

`H` includes whole-static and class-wide scopes. `S` is the existing `freezePlan`
field set: transitive accesses, abstractions and explicit field-interest slots.
It is not the set returned by `selectedFields(input)`, which can activate a field
outside S when a real input meets a broad scope.

Domain membership alone adds no method ownership, request or initial fact. A class
point can belong to Sigma even if it has no fields. Do not construct a class-by-field
product. A domain correction can legitimately expose more supplied components to
an existing broad owner; it is not a promise that total work stays unchanged.

The resolved call graph is complete after prescan. Keep that graph and its existing
closure. There is no new call-resolution phase, request index or runtime domain
growth. AP accessor IDs belong to one manager; neutral descriptors, not scratch
IDs, cross from metadata collection to the engine.

## 3. Find and reproduce the exception

The observed failure is:

```text
IllegalStateException:
Static input class is absent from the frozen prescan universe
```

[Run1Abstraction.emit](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1Abstraction.kt)
checks the supplier and input shapes before this global-domain check. The failed
class is not merely absent from that callee's H. The exact class, full methods and
creating producer are not printed. A package-unit name does not identify them.

First extend the existing failure diagnostic. Keep the check and its position.
Pass the owning MethodKey from the actor to the check helper:

```kotlin
if (cls !in footprint.classUniverse) {
    throw missingStaticCoordinate(
        callee = key,
        caller = ref.caller,
        originalPremise = ref.premise,
        call = ref.call,
        callerLayer = ref.callerLayer,
        inputKind = kindOf(input),
        inputLayer = input.layer,
        fieldExclusion = input.exclusion,
        flowMarkExclusion = (input as? FlowTree)?.markExclusion,
        classId = cls,
        classSymbol = ap.accessors.accessor(cls),
        fields = decodeFirstFieldsBounded(
            node, ap, maxFields = 32, maxBytes = 8192,
            checkpoint = ap.cancellation::checkpoint,
        ), // IDs/symbols + original child count + omitted count
        domainClasses = footprint.classUniverse.size,
        domainFields = footprint.fieldUniverse.size,
    )
}
```

The helper prints bounded identifiers, relevant E/mx headers and field coordinates,
with a truncation count if needed. It must not serialize the whole packed fact,
retain it in a new store, evaluate a predicate/transfer again, force alias analysis
or resolve a call. Print both IDs and symbols so a cross-manager ID error is
distinguishable from a missing symbol. Keep field-miss diagnostics equivalent.

Replay the failing project with its existing configuration. From the actual miss,
locate the producing form/statement. S bind-in can transport an older omission,
so the immediate caller is not automatically the creator. If necessary, add a
temporary check for that one coordinate at existing output callbacks, using their
method/node information. Do not introduce persistent provenance tracking.

Create a real JVM regression fixture that runs that producer and compares its
actual output coordinates with collected Sigma. Do not declare the missing field
in a fixture and then claim that collection covers it. This diagnostic/reproducer
step selects the repair; the current evidence does not justify naming a specific
project producer as the cause.

## 4. Repair metadata coverage

There are two demonstrated source-level coverage obligations and one conditional
coordinate question. Implement the smallest repair established by the reproducer,
then close the complete producer inventory below.

### 4.1 Make alias knowledge explicit

The collector currently uses:

```kotlin
val availableAlias = aliasAnalysis?.takeIf { it.computedAliasInfo() != null }
val unknownAliases = aliasAnalysis != null && availableAlias == null
```

The null-AA fallback therefore means `unknownAliases=false`, although runtime can
enable and compute AA later. A nonnull, uncomputed AA object takes a different path.
Replace that ambiguity with an explicit metadata policy:

```kotlin
sealed interface MetadataAliases {
    data object Disabled : MetadataAliases
    data object Unknown : MetadataAliases
    data class Known(val analysis: JIRLocalAliasAnalysis) : MetadataAliases
}

fun aliasKnowledge(runtimeAAEnabled: Boolean, completed: CompletedPrescanAA?): MetadataAliases = when {
    !runtimeAAEnabled -> MetadataAliases.Disabled
    completed != null && completed.coversRuntimeAliasOutcomes -> MetadataAliases.Known(completed.analysis)
    else -> MetadataAliases.Unknown
}
```

`coversRuntimeAliasOutcomes` is a proof obligation, not an existing flag. A joined
prescan result is usable only if its method, graph/model policy and relevant facts
cover later runtime outcomes. One known prescan context does not establish coverage
of every runtime context. Missing, uncomputed or failed AA is Unknown when runtime
AA can run. Disabled means runtime really disables it. Collection must not start
new AA after prescan workers join.

Unknown alias-sensitive predicates preserve their potential static effects and
mark needs. They must not be folded to false merely because no AA object was
passed. This is metadata collection, not permission to make a runtime rule fire.
An uncertain static atom can admit a potential metadata branch; a remaining
mark-dependent condition still uses its signed eligibility policy. Preserve
validation and the existing runtime rules:

- Source and sink conditions both contribute potential effects and mark interests.
- Entry source/sink eligibility still requires signed `isTrue`, before negative
  projection. Remaining negative literals cannot create unconditional entry GEN.
- Normal source/sink predicate lowering and the separate source-rewriter policy
  keep their current rules. Cleaner eligibility is unchanged.
- A condition proved false in every applicable runtime context contributes no
  effect. `P AND NOT P -> false` remains valid. Validate raw actions/positions before
  folding. Conditions stay NNF; no cubes are introduced.

Test the distinction on actual frontend values. At a call, an unavailable local
alias can make `unknownStaticAtom OR ContainsMark(P)` a potential metadata effect.
If the runtime atom is false, the remaining mark predicate must stay conditional.
Separately, test entry source and sink conditions with a false argument-constant
atom OR `ContainsMark(P)`: neither metadata nor runtime may enable an entry GEN
action or sink. The current entry resolver returns arguments/This, not local
variables, while alias-dependent constant evaluation uses local variables only.
Thus an unknown local-AA atom is not an actual entry case; do not invent one or
broaden entry metadata to satisfy the test. Signed entry `isTrue` remains required.

Potential action scopes and mark needs require separate metadata views. Replacing
an unknown U by true can establish a potential action, but it turns `U OR P` into
true and loses the P interest needed when runtime U is false. The same applies to
`NOT U OR P`. Keep a signed symbolic condition for mark inventory before this
fold. Reuse the computed atom results; do not evaluate atoms or query providers
again:

```kotlin
fun rewriteSourceOrSink(
    rule: TaintConfigurationItem,
    rewriter: RuleConditionRewriter<JirCondition>,
    projectNegatives: Boolean = true,
    potentialMarkLiteral: ((PathFact) -> Unit)? = null,
): ExprOrConstant

// Metadata call inventory uses projectNegatives = true.
// The runtime result keeps its current policy.
val signed = normalizeSigned(rule.rawCondition)
val symbolic = keepUnknownAndMarkAtomsFoldKnownAtoms(signed, computedAtomResults)
val liveSigned = normalizeSigned(symbolic)
if (!liveSigned.isFalse) {
    val interests = normalizeSigned(projectNegativeMarkAtomsOnly(liveSigned))
    visitPositiveMarkAtoms(interests, potentialMarkLiteral)
}
```

Unknown static atoms, including their negative signs, remain symbolic. Do not use
a projection that replaces every negative atom by true: that would erase P in
`NOT U OR P` again. Prove signed falsity before the source/sink negative-mark
projection. Known-true OR P, known-false AND P and contradictory mark branches
still add no P need. This uses NNF and no cubes or raw-rule cache.

Pass the optional inventory callbacks only through metadata call source/sink
compilation. Lower surviving mark leaves with the existing position/literal code
and SOURCE/SINK roles. Entry and exit resolvers have no alias-sensitive local
value; their current effective-form inventory and eligibility stay in place.
The ordinary runtime path has no callback or symbolic inventory allocation.

The integration points are
[JIRStaticMetadataCollector](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRStaticMetadata.kt),
[JIRBidiAnalysis.gather](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt)
and the existing metadata policy arguments of boundary/call form builders.
Ordinary runtime predicate evaluation is not replaced by metadata approximation.

### 4.2 Cover producer coordinates without widening every footprint

Runtime statement aliases and persisted call aliases can emit static endpoints;
the metadata visitor currently omits those alias payloads. Prove that their
coordinates already follow from collected code/rule endpoints, or add their
finite coordinate basis to the shared domain. Do not force full alias analysis
solely to enumerate a whole classpath.

If an independent domain is needed, use neutral values and the existing builder
arguments. There is no need for a new per-method domain store:

```kotlin
data class StaticProducerDomain(
    val classes: Set<ClassStaticAccessor>,
    val fields: Set<Pair<ClassStaticAccessor, FieldAccessor>>,
)

// Use the completed graph, validated effective code/rule/model endpoints,
// and the proved alias-coordinate basis. Keep each class/field pair intact.
val domain = collectProducerDomain(resolvedPrescanGraph, selectedProviders)
val locals = collectPotentialMethodEffects(resolvedPrescanGraph, aliasPolicies)

val profiles = Run1StaticProfiles.build(
    ap, resolvedPrescanGraph.adjacency, locals.compile(ap),
    declaredFieldUniverse = domain.fields.mapToEngineSlots(ap),
    declaredClassUniverse = domain.classes.mapToEngineClasses(ap),
)
```

`declaredFieldUniverse` and `declaredClassUniverse` already exist; the JVM caller
currently supplies neither. Add a separate descriptor only where the coverage
argument needs a domain coordinate without a method effect. If the alias-policy
repair alone restores the complete collected basis, reuse it rather than add a
redundant domain representation.

Do not populate Sigma with arbitrary classpath fields, promote Sigma into every
H or S, or broadcast all marks to all fields. Conversely, if the missing producer
also reads/writes a method-owned component, repair that method's H/needs as well.
Adding only the domain cannot prove that the untouched frame is safe.

Direct static accesses use a resolved declaring owner; one AA construction uses
the typed reference owner. An inherited reference may already be canonical in
real IR. First assert the actual values in a real JVM fixture. If a mismatch is
real, collect the actual admitted coordinates or use one proved canonical helper.
Changing emitted coordinates is a semantic repair and requires its own equivalence
checks. It is not an assumed fix for these unprinted project failures.

### 4.3 Required domain proof

The proof must establish coverage from constructors, not assume every delivered
coordinate already belongs to Sigma:

| Producer/route | Required argument |
| --- | --- |
| Static code reads/writes | Collected canonical class/first-field equals the emitted coordinate |
| Statement and persisted call aliases | Alias conversion emits only coordinates in the collected finite basis |
| Source, sink end actions, pass and generated/native forms | Every possible accepted static endpoint is collected under a conservative metadata policy |
| Cleaners, strong writes and excluded targets | H includes the affected component and request creators remain covered |
| Bind-in/back, cuts and summary composition | Preserve an existing coordinate or use a collected target; no new unchecked prefix |
| Initial emission | Uses supplied/owned class or field coordinates; no new coordinate is invented |
| Later native/opposite records | Preserve the domain and stable footprint after request-plan release |

Induct over actual operations on the completed call graph. Keep the selected
run-1 bound before delivery and all guard checks. No emitter cut, unknown-component
identity return, dropped branch or runtime insertion into Sigma is a repair.

## 5. Factor the corrected frozen request relation

Keep the materialized map while fixing the exception. Call that corrected version
the **map control**. Factoring must match this control, not the aborted `4c64`
execution. A coverage fix may legitimately add previously omitted work.

The exact current construction, after closure, is:

```text
S = accessed fields union abstraction fields union field-interest slots
common(C) = commonByClass[C], if present; otherwise rootMarks

B(slot) = common(slot.class) union fieldMarks[slot],  if slot in S
          empty,                                     otherwise
```

`commonByClass[C]` already includes root marks. Retain that once-per-class union,
root marks and sparse field marks. Avoid the final expansion into one map entry
per nonempty field row. Keep existing SCC/profile and immutable mark-set sharing.

```kotlin
interface FrozenRequestBase {
    val isEmpty: Boolean
    val logicalRows: Long
    val logicalMembers: Long
    fun hasRow(slot: Run1Slot): Boolean // logical B(slot) is nonempty, not slot in S
    fun borrow(slot: Run1Slot): ImmutableRequestMarks
    fun forEachStaticRow(checkpoint: () -> Unit, visit: (Run1Slot.StaticField) -> Unit)
}

interface ImmutableRequestMarks : RequestMarkSelection {
    val isEmpty: Boolean // constant time; not a hidden cardinality scan
}

class FrozenStaticRequests internal constructor(
    private val selected: Set<Run1Slot.StaticField>, // existing ordered S
    private val root: RequestMarkSet,
    private val commonByClass: Map<Int, RequestMarkSet>,
    private val byField: Map<Run1Slot.StaticField, RequestMarkSet>,
    override val logicalRows: Long,
    override val logicalMembers: Long,
    private val checkpoint: () -> Unit,
) : FrozenRequestBase {
    private fun common(cls: Int) = commonByClass[cls] ?: root
    override val isEmpty get() = logicalRows == 0L

    override fun hasRow(slot: Run1Slot): Boolean = slot is Run1Slot.StaticField &&
        slot in selected && (!common(slot.cls).isEmpty || byField[slot]?.isEmpty == false)

    override fun borrow(slot: Run1Slot): ImmutableRequestMarks {
        if (slot !is Run1Slot.StaticField || !hasRow(slot)) return RequestMarkSet.EMPTY
        return distinctUnionView(common(slot.cls), byField[slot] ?: RequestMarkSet.EMPTY, checkpoint)
    }

    override fun forEachStaticRow(checkpoint: () -> Unit, visit: (Run1Slot.StaticField) -> Unit) {
        if (isEmpty) return
        for (slot in selected) {
            checkpoint()
            // Record a frozen selected-slot examination here, even for an empty B row.
            if (hasRow(slot)) visit(slot)
        }
    }
}
```

Declare `ImmutableRequestMarks` beside `RequestMarkSelection` in the store package.
The existing `RequestMarkSet` becomes an immutable-view implementation. Empty and
single-segment fast paths return existing sets. A two-segment view owns only two
mark-set references and the existing cancellation handle; it does not allocate
a merged array or enter a cache/interner. At binding/read time it borrows
the already frozen selected footprint and maps without copying them. The frozen base is
one immutable value, not a third mutable request store.

At freeze, detach the mutable local `fieldMarks` once into an immutable sparse
map before publication. Subsequent readers share that frozen map. Drop a
field-specific entry if all its marks are already in `common(cls)`; keep partial
overlaps for the distinct union view. This needs no residual array or new cache.
Keep S unchanged when dropping a redundant mark overlay. The equality test
expands the original unpruned field needs independently.

Keep `RequestRows = Map<Run1Slot, RequestMarkSet>` for actual temporary/dynamic
deltas and event payloads. Do not force them through the virtual-base interface.
A map adapter is useful for the corrected reference implementation and existing
small fixtures; production must not keep both the expanded map and factored base.

## 6. Distinct, ordered mark views

Common and field marks can overlap. The view must yield each ID once, in the same
ascending order as the current frozen `RequestMarkSet`. The two existing arrays
are enough:

```kotlin
class UnionCursor(private val a: RequestMarkSet, private val b: RequestMarkSet,
                  private val checkpoint: () -> Unit) : RequestMarkCursor {
    private var i = 0
    private var j = 0
    private var hasLast = false
    private var last = 0
    override val isComplete get() = i == a.size && j == b.size

    override fun step(budget: Int, visit: (TaintMark) -> Unit): Int {
        require(budget > 0)
        var used = 0
        while (used < budget && !isComplete) {
            checkpoint()
            val id = if (j == b.size || (i < a.size && a.idAt(i) <= b.idAt(j)))
                a.idAt(i++) else b.idAt(j++)
            used++ // one consumed source ID; a duplicate also consumes work
            if (!hasLast || id != last) {
                hasLast = true
                last = id
                visit(TaintMark(id))
            }
        }
        return used
    }
}
```

A duplicate-only `step(1)` can return `used=1` with no callback. It still advances.
Current cursor consumers do not require `used == callback count`. This explicit
contract avoids scanning an arbitrary duplicate suffix in one budget unit. Keep
signed comparisons; do not subtract IDs or reserve 0/MIN/MAX as a sentinel.

Membership uses `a.containsId(id) || b.containsId(id)`. Exact cardinality is not
`a.size + b.size` when they overlap. Remove unused hot-path size getters or replace
them with explicitly named counting operations. No row getter may hide a union
or intersection pass. The dynamic segment remains disjoint from the full B view,
so R iterates distinct B first, then its existing sorted D.

Precomputed consumers use a cursor loop instead of requiring a concrete set:

```kotlin
fun ImmutableRequestMarks.forEachMark(checkpoint: () -> Unit, visit: (TaintMark) -> Unit) {
    val values = cursor()
    while (!values.isComplete) {
        checkpoint()
        values.step(256, visit)
    }
}
```

Update precomputed iteration in `RequestRowView` and `SubscriptionManager` through
this helper. Concrete D iteration and event payloads keep their current sets.

## 7. RequestStorage and matching integration

Keep two instances of the same mutable storage class:

```kotlin
// Owned by SummaryStorage for the full MethodKey:
val requests = RequestStorage(sharedBase = profile.frozenRequests)

// Owned by the method actor:
var temporary = RequestStorage(sharedBase = EmptyFrozenRequestBase)
```

Generalize only the frozen segment of the borrowed row:

```kotlin
data class RequestRowView(
    val precomputed: ImmutableRequestMarks,
    val dynamic: RequestMarkSet,
) : RequestMarkSelection {
    val isEmpty get() = precomputed.isEmpty && dynamic.isEmpty
    override fun containsId(id: Int) = precomputed.containsId(id) || dynamic.containsId(id)
    // Cursor: distinct sorted B, followed by disjoint sorted D, as today.
}

fun RequestStorage.borrow(slot: Run1Slot) = RequestRowView(
    sharedBase.borrow(slot), additions[slot] ?: RequestMarkSet.EMPTY,
)
```

Retain `borrowMatchingRows(input)`'s current geometry and detached descriptor list:

| Input geometry | Selection |
| --- | --- |
| Non-static base | Direct lookup of its Base slot |
| Explicit static C.f branch | Direct lookup of that field slot |
| Broad static root | Visit nonempty B rows in S order, then D-only keys |
| Broad class plus explicit sibling fields | Direct lookups for explicit fields; one stored-row pass filtered to broad classes |
| Exact ancestor only | It does not supply deeper fields |

Replace the old frozen-map key loop with the virtual row iterator:

```kotlin
sharedBase.forEachStaticRow(checkpoint, visit)
for (slot in additions.keys) {
    checkpoint()
    if (slot is Run1Slot.StaticField && !sharedBase.hasRow(slot)) visit(slot)
}
```

The D test is **nonempty B membership**, not `slot !in S`. A field can belong to S
with empty B and later acquire D. Skipping it by S membership would lose requests.
Preserve B row order, then existing D insertion order. Scan S, not global Sigma,
for broad frozen rows. Keep the current matching policy; this iteration does not
add the separate slot-directed delta-join optimization or a new request index.

Build the descriptor list while holding SummaryStorage's existing lock. Each
descriptor borrows immutable B segments and the captured immutable D value. Do
not return a lazy iterator over mutable additions after unlocking. The temporary
matched-row list is still present; factoring removes the standing expanded base.

`hasRow` checks segment emptiness directly; it creates no throwaway union view.
A broad scan can examine S slots that have no B row, whereas the old map omitted
them. Count and test that cost, especially for sparse plans. Narrow lookups remain
direct. Do not hide a new expanded-key cache behind this API.

## 8. Complete static and dynamic workflow

1. Prescan resolves all calls and collects neutral producer coordinates, method
   effects and mark needs. Apply the exception repair before freezing.
2. Compute the existing SCC/call closure once. Freeze S, common descriptors, sparse
   field needs and the stable structural footprint. Drop closure scratch data as
   today. Do not create the expanded B map.
3. Before publishing a callee's SummaryStorage, bind that profile's shared base.
   Do not copy B into D or P. There is no startup request notification wave.
4. Fresh TAINT input joins current R at callee abstraction. Fresh eligible FLOW
   and input growth join current R at the subscribed caller owner. Both see B
   immediately, even before any dynamic commit.
5. New dynamic needs use the existing owner P, method-queue-empty barrier and commit:

```kotlin
val fresh = offered.minus(summaryStorage.borrowRequest(slot)) // B union D
if (fresh.isEmpty) return
temporary.addAll(slot, fresh)
requestWork() // existing counted Work behavior

// Existing method fact/publication queue-empty boundary, not a quantum yield:
summaryStorage.commit(temporary)
temporary = RequestStorage()
```

6. Commit subtracts current R, installs only fresh D and captures subscribers under
   the existing lock. Notifications and resulting effects queue after unlocking.
   A late request replays retained caller inputs; the emitter gets no input memo.

For every slot, preserve `D intersect B = empty`. P stays private to its owner and
is not used for answering. Registration/commit linearization, quantum behavior,
counted work and required-delivery failure handling stay unchanged.

### 8.1 Inherited B and original caller slots

| Caller origin -> callee output slot | Handling |
| --- | --- |
| Static f -> same f | Skip only that row's inherited B; dynamic D still climbs |
| Static g -> f | Route matching B and D to original caller g |
| Non-static b -> f | Route matching B and D to original caller b |
| Concrete origin | Answer from actual supplied marks; never propagate requests |

For a packed output `f -> {f,g}`, skip inherited B[f]/T but still route B[g]/U to
the original f/U slot. Preserve full caller MethodKey, premise and call site.
The input having STATIC base does not permit skipping every precomputed row.

### 8.2 Wide activation outside S

```text
S = {C.f}; root needs = {T}; existing H owns all statics
Sigma also contains C.g; g is outside S

Before input: B(g) = empty; R(g) = empty
Actual g supplier arrives:
  emit its selected abstract field start
  wideRequests offers g/T through P
At method-empty commit:
  D(g) gains T; notify registered callers
Replay actual TAINT supplier:
  emit the appropriate concrete g initial carrying T
```

The factored base must not make B(g) nonempty merely because root has T. Keep
`wideRequests(input)` and R filtering on this path. Repeated activation deduplicates
through the existing stores. A request without an actual T supplier cannot invent
a concrete initial.

## 9. Initial shapes, class points and lifetime

Factoring changes no emitted initial compared with the corrected map control.
The exception fix can restore previously missing supplied/owned analyses; it must
stay inside [F78's initial table](../../spec/run1-requests.md#11-closed-run-1-initial-grammar).
Run-1 still starts at limit exactly 1.

- Always emit the selected supplied field's abstract `C.f.*(*)` independently of
  concrete request membership. If actual T and R(T) match, add the concrete answer.
- Nonexact `C.f.[any](T)` input emits `C.f.*(T)`, never an ANY initial. Exact field
  supply uses the existing exact answer. Non-static answers keep an empty path.
- Exact class points directly emit their actual marks. Broad/nonexact class points
  use structural point ownership and all actual supplied marks, with STAR/DEMAND
  starts. There is no class request slot or abstract class initial.
- The special abstract S-root remains the summary seed. Skip rule evaluation and
  request creation on that root component. Do not admit a concrete empty-S-root
  initial or hide an invalid supplier behind an emitter cut.

Keep direct class-point metadata construction unchanged. Its `rootClassMarks`
differs from the field-request root, which initially includes cleaner marks. Do
not substitute one for the other or filter direct class output to a requested-mark
mask. Factoring that separate metadata relation is outside this change.

Request matching still ignores incoming field E and FLOW mark exclusions. Concrete
answers still require actual marks. Ordinary transfers, projections, source/sink
conditions and summary/record matching retain their E/mx semantics.

After joined run-1 and the existing report/persistence/handoff consumers finish,
release the mark plan as today. Later native/opposite record readers retain the
stable mark-free H/Sigma footprint and normal-return checks. Footprints must not
point back to mark descriptors. Borrowed immutable views cannot be mutated by
release; no cursor or matching list is saved as a new long-lived owner.

## 10. Statistics without rebuilding B

For each selected class, let `S_C` be its fields, `K_C = common(C)`, and `F_s` the
sparse field-specific needs. Compute exact logical totals once during freeze:

```text
logicalMembers = sum_C |S_C| * |K_C|
                 + sum_s_in_fieldNeeds |F_s minus K_class(s)|

logicalRows = sum_C ( |S_C|,                              if K_C nonempty
                     number of nonempty field rows in C, otherwise )
```

Use checked Long arithmetic and cancellation checkpoints. Common counts use the
existing class grouping; intersection work is only over sparse field needs. Do
not enumerate all virtual field/mark pairs, allocate union snapshots for counters,
or count only when a consumer later touches a slot.

Report separate units:

| Counter | Meaning |
| --- | --- |
| Logical B rows/memberships | Same relation as the corrected map control; distinguish physical profile totals from per-method uses |
| Materialized B rows | Expanded map entries; production factored base should have none |
| Descriptor counts | Unique frozen bases, selected-set roots, common/class maps and sparse field entries |
| Mark storage | Identity-distinct immutable sets and owned primitive cells, including wide/class roles separately |
| Borrow/match work | Direct lookups, stored slots examined and emitted descriptors, with scanned-versus-matched units explicit |
| Runtime R/P | Existing D/P rows, mark members, duplicates, commits and pending wait; unchanged definitions |

Do not report zero virtual requests when materialized rows become zero. Include
descriptor, selected-set, view and query costs in the resource comparison. Keep
the already shared class-wide maps shared, rather than retain a second copy in
the new base. Count the retained sparse field entries explicitly. The gross
expanded-entry reduction is `old expanded entries - new sparse field entries`,
before accounting for descriptor and replacement-map costs. If the sparse map is
dense, zero expanded B maps alone does not demonstrate a memory saving. No new
global canonicalization or cache is needed.

## 11. Proof and test obligations

| Obligation | Proof/control |
| --- | --- |
| Producer-domain coverage | Constructor/transfer induction from real emitted coordinates; null/uncomputed/failed/disabled AA and actual project producer fixtures |
| Descriptor remapping | Compile the same neutral domain under different AP interning orders; actual producer symbols map to the new manager's class/field IDs |
| Ownership coverage | Every possible read/write/cleaned component lies in the method's transitive H; domain-only coordinates do not create ownership |
| B lookup equality | For every slot, independently expand descriptors and prove `Bfactored(slot) = Bmap(slot)`, including empty/outside-S rows |
| Row and mark iteration | Same nonempty slots, unique rows and sorted unique B IDs; D-only keys remain visible when S contains an empty B row |
| Union cursor | ID 0 and signed boundary IDs; overlapping/identical/empty sets; duplicate-only step(1), progress and cancellation |
| R/P semantics | Equal membership, subtraction and fresh D deltas; immutable borrowed views; no initial B wave or copies into P/D |
| Full-key isolation | Two keys borrow the same B object; D/P update, cancellation and release in one do not alter the other or an earlier immutable view |
| Delivery | Both input/request orders, late subscribers, input growth, cycles, remote/queued callbacks and commit/replacement races |
| Caller routing | Mixed `f -> {f,g}`, changed static/non-static origins and concrete no-climb controls |
| Static outputs | Exact/ANY/must packed suppliers, broad ancestors plus explicit fields, separate E groups, class points and all actual T/U marks |
| Summary safety/lifetime | Cleaner-T/no-reintroduction, strong-write-f/preserve-g, nonreturning callee, and later native/opposite record readers |

The expanded oracle must use raw finite descriptors and an independent finite
closure; it must not call the new virtual borrow/union implementation to compute
expected values. Cover diamonds, SCCs, deep call chains, no-needs profiles and
profiles shared by multiple full method keys.

The alias fallback control must exercise the real gather path for a graph method
with no prescan context, with runtime AA enabled. Calling the collector with a
nonnull uncomputed object alone does not cover that route. Also keep a no-fields
class point and an explicitly supplied field outside a callee's H.

B equality plus disjoint residual D gives equal effective R. Equal matching rows
and actual-mark intersection then give equal concrete answers and climbed needs.
The unchanged commit/registration protocol preserves both arrival orders. Lift
these local equalities to completed finite execution under the same candidate
policy, checking initials, summaries, records and reports. This argument does not
promise equal timeout prefixes or a time bound.

Update the F78 spec and proof dossier with the explicit producer-domain argument
and virtual-base laws before implementation acceptance. Existing Lean receipts
keep their finite-model scope. New Kotlin/JVM coverage and physical-layout claims
require their own tests; changing documentation does not extend a receipt.

## 12. Implementation plan

| Step | Files/work | Completion gate |
| --- | --- | --- |
| 1. Diagnostic and reproducer | `Run1Abstraction`, actor key handoff, actual JVM producer fixture | Exact missing coordinate and producer established; original guard still fails on the uncorrected case |
| 2. Coverage repair only | `JIRStaticMetadata`, `JIRBidiAnalysis`, metadata policy plumbing; declared-domain input only if required | Real producer outputs covered; H and Sigma proofs closed; corrected map control retained |
| 3. Immutable mark/base views | `RequestMarkSet`, `Run1Requests`; empty/map adapters and sorted union cursor | Independent lookup/iteration/cardinality/cancellation controls pass |
| 4. Factored freeze | `Run1StaticProfiles.freezePlan`, `covers`, mark ownership census and logical statistics | Expanded/factored equality on finite closures; no production expanded base retained |
| 5. Storage integration | `SummaryStorage`, `RunManager`, precomputed iteration in `SubscriptionManager` | Same R/P protocol, matching lists, inherited-B behavior and late wide activation |
| 6. End-to-end verification | Existing common/JVM tests and fixed project matrix | Progress, strict findings, completed engine times and resource evidence reported |

Keep coverage repair and factoring as separate reviewable commits. Do not combine
pending-admission fast paths, delta-join changes, class-point descriptor factoring
or scheduler changes into them. A prototype may use the map adapter for controls;
the final runtime selects only one base representation.

## 13. Measurement and acceptance

Use the exact existing OWASP, Conductor, ThingsBoard and Stirling-PDF configurations
and direct Gradle `:runProjectAnalyzer`. Do not change project heaps, timeouts or
field limits. Preserve the original strict identity, span and occurrence comparator.

1. Use diagnostic replays only to identify the failure, starting with a failing
   project. Label them diagnostic, not performance controls.
2. Validate the coverage-only map control before factoring. Record its strict
   main identity/span/occurrence comparison and latest reached/completed/accepted
   iterations. Check that the identified producer and domain failure are repaired;
   classify any remaining exception, loss, OOM or timeout separately. A completed
   run-1 and accepted report provide a successful project control. If this map
   version instead hits its resource limit, keep that failed result visible; do
   not require it to complete as a prerequisite for the selected memory change.
3. Compare the factored version with that corrected map control for attribution;
   a comparison with failed `4c64` cannot isolate a storage benefit. Use the same
   fixed projects and capture policy for both. Compare logical work at matched
   completed progress, not only time-to-failure or final released gauges. A partial
   map run is not a complete semantic/report oracle: use the independent completed
   finite controls and proof laws, and qualify project comparisons by their actual
   reached/completed/accepted scope.
4. Run the final version against freshly pinned main for all four projects.
   Report any missing baseline identity/span and its cause. Preserve additional
   occurrences separately from new unique findings and concrete trace evidence.
5. Report every engine and barrier; latest reached, completed and accepted-report
   iteration; prescan/metadata/catalog time; request counters; allocation/CPU
   windows; peak RSS; and any OOM snapshot owners. Keep failed/timeout results
   explicit. Without a heap, do not invent a retained-byte saving.

Before new measurements, retain compact evidence and clean prior raw captures as
required by `task.md`. Run scans serially and perform heavy profile/heap analysis
after captures finish. The existing eight strict application failures remain
visible until they are actually resolved; no assertion or comparator is relaxed.

Factoring succeeds when logical B/R, required initial/summary behavior and findings
match the corrected control, the expanded B relation is absent from production,
and full replacement/query costs improve measured memory without an unacceptable
engine regression. The broader target remains a completed run-1 within 60 engine
seconds on every fixed project, with no missing baseline findings. These two
changes are not yet measured to meet that target.
