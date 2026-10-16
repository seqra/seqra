# Static-domain fix and factored frozen requests

Date: 16 October 2026. Status: detailed design and implementation proposal for
the two selected changes. Their runtime commits are present in this checkout.
Project captures, strict finding comparisons, profiles and all six required OOM
heap analyses are complete. This document update changes no runtime code.

The selected iteration contains two changes, in this order:

1. Fix the static-domain exception and close the related metadata coverage gaps.
2. Factor frozen static requests without changing their logical membership.

The coverage repair is committed as `d330a335a`; its four-project map-control
phase is complete. Factoring is integrated as `4acde739b`, from isolated commit
`bbdbe7307`. Integrated common/JVM checks pass. The strict application gate keeps
the map control's eight failures. The final factored project phase is terminal;
strict finding comparison, profile analysis and heap comparison are complete.
These are separate changes and need separate measurements.
The proposal below defines their required behavior and acceptance.

| Order | Selected work | Purpose | Runtime commit |
| --- | --- | --- | --- |
| 1 | Fix the static-domain exception | Correct the delivered coordinate and metadata coverage; establish a valid control | `d330a335a` |
| 2 | Factor frozen static requests | Avoid the expanded frozen field-row map; keep its logical requests | `4acde739b` |

Factoring has the highest expected memory impact among these two changes. The
exception fix must come first for correctness. Neither change proves that the
analyzer will meet the 60-second run-1 target.

This document replaces the selected parts of the
[broader proposal](bidirectional-next-iteration-4c64c579f-20261015.md).
Cleaner transfers, form compilation, alias deferral, caller-input representation,
request scheduling, reference managers and IR storage are outside this iteration.

## 1. Evidence and intended result

The original selection used the historical `4c64c579f` measurements. The
corrected map control at `d330a335a` now supplies the comparison workload. Use the
[current project report](../reports/bidirectional-static-domain-frozen-requests-performance-20261016.md).
The table below records that control, not factoring. All first engines use
run-1, forward, limit exactly 1.

| Project | Latest reached | Latest completed engine | Accepted forward report | Run-1 engine |
| --- | --- | --- | --- | --- |
| OWASP | run-6, backward; TIMEOUT | run-5, forward | run-5 | 213.730 s; COMPLETE |
| Conductor | run-1, forward; OOM | None | None | 105.805 s; OOM |
| ThingsBoard | run-1, forward; OOM | None | None | 106.335 s; OOM |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 | 168.350 s; COMPLETE |

The old static-class exception does not recur in these four control scans.
Resource limits remain. The completed run-1 engines exceed the 60-second target;
an OOM prefix is not a completed run-1. Three guard heaps were captured. Strict
main comparisons and heap/profile analysis are separate evidence gates.

The diagnostic Stirling-PDF replay on 16 October reached **run-1, forward,
limit 1**, then failed after 11.115 engine seconds. It completed no engine and
accepted no report. The bounded diagnostic identified the coordinate mismatch
in section 3. This replay is diagnosis evidence, not a performance control.

Latest validation snapshot, **16 October 2026 at 09:05:53 UTC**: the map-control
phase is terminal with stable source and HEAD bindings. Fresh main completed all
four cases; its legacy engine has no bidirectional run number. Integrated common
and JVM tests pass; strict application failures remain. The final factored phase
measures HEAD `05cf9ad62`, which adds documentation to runtime `4acde739b`.

| Factored project at this snapshot | Latest reached | Latest completed engine | Accepted forward report | Run-1 engine |
| --- | --- | --- | --- | --- |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 | 189.346 s; COMPLETE |
| Conductor | run-1, forward; OOM | None | None | 105.232 s; OOM |
| ThingsBoard | run-1, forward; OOM | None | None | 109.892 s; OOM |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 288.383 s; COMPLETE |

The PDF factored result does not establish a speedup: its control completes
run-1 in 168.350 seconds. All four strict map-to-factored comparisons preserve
the same finding identities, locations and occurrence counts. This includes
empty reports after run-1 OOM for Conductor and ThingsBoard; it does not prove
baseline recall. Profiles and all six OOM heaps are analyzed. The final report
keeps the failed time, recall and application-test targets explicit.
Keep this observation separate from the expected memory benefit. The table is
a dated capture snapshot, not the final profile and memory analysis. All three
phases have stable terminal source/HEAD bindings. The post-input audit passes
all 80 checks; its 41 content datasets and all non-path check facts match the
pre-input audit. Endpoint equality does not prove continuous input immutability.

The new evidence root is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z
```

Use `diagnostic-pdf/first-miss.txt` for the failure witness,
`corrected-map/launch.json` for the control revision and fixed workload, and
the terminal project summaries when each case finishes. An unfinished log must
not establish successful completion or a memory reduction.

| Change | Current behavior | Proposed behavior | Expected benefit |
| --- | --- | --- | --- |
| Exception and metadata fix | An alias can use a different class name from the resolved static field; missing alias knowledge can also hide rule effects or mark needs | Use the resolved declaring owner for static alias coordinates; collect potential source/sink effects and live mark needs under an explicit metadata policy | Restore domain consistency and required work before measuring a storage change |
| Factor frozen requests | Each profile expands common marks into a map row for every selected field | Borrow the same logical row from common and sparse field descriptors | Avoid repeated map entries, slot references and row construction |

In this table, "current" means the behavior before that selected change. For
factoring, it means the corrected map control, not today's integrated checkout.

In the corrected map control, ThingsBoard's plan has 3,000,410 physical frozen-map
rows across 1,454 distinct map roots. Its logical per-method totals are 15,671,695
rows and 188,923,219 memberships. The mark payloads already share storage. The
reported 52,957 mark-set roots and 79,080 primitive cells also include wide/class
metadata. Thus the target is the expanded row relation, not 188.9 million
separately allocated mark cells.

No saved-byte estimate is available. Replacement descriptors and query costs must
be measured. Field-specific entries remain in the sparse map. The 3,000,410 rows
are a gross target, not the number of entries guaranteed to disappear. A profile
with only explicit field needs may have little map-entry saving.
Catalog setup takes 3.609 seconds, separately from 58.665 seconds of
metadata preparation. Factoring does not remove that metadata phase and does not
by itself establish a 60-second engine time.

The exception fix is a correctness prerequisite. Factoring is the selected
memory optimization. It avoids construction of the repeated row relation, while
preserving the requests. It does not reduce the number of logical facts or mark
answers. Use the corrected map control to measure the new relation size after
the coverage repair; do not carry the historical row count forward as a forecast.

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

## 3. Exception: observed cause and diagnostic contract

The observed failure is:

```text
IllegalStateException:
Static input class is absent from the frozen prescan universe
```

[Run1Abstraction.emit](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1Abstraction.kt)
checks the supplier and input shapes before this global-domain check. The failed
class is not merely absent from that callee's H. The original message did not
identify the coordinate. The bounded replay now records:

```text
delivered class: stirling.software.common.util.ProcessExecutor.Processes
field owner:     stirling.software.common.util.ProcessExecutor$Processes
first field:     WEASYPRINT
callee:          ProcessExecutor#runCommandWithOutputHandling(java.util.List)
caller:          FileToPdf#convertHtmlToPdf(...), call 48
input:           FLOW, STATIC base, DEMAND layer
```

The nested-class spellings differ. The resolved field and direct field transfer
use the binary declaring-class name. The alias builder used the typed reference
name. The real JVM regression reproduces this mismatch through static read,
local alias and persisted call-alias output. The project witness establishes
the bad delivered coordinate; it does not by itself identify the earliest
creating instruction in that project.

Retain the bounded failure diagnostic. Keep the check and its position.
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

The selected repair in `JIRDSUAABuilder.evalRefValue` is:

```kotlin
// Before: a typed reference can have a different nested-class spelling.
val type = value.field.enclosingType.typeName

// After: agree with the direct transfer and the resolved field coordinate.
val type = value.field.field.enclosingClass.name
Expr.FieldLoad(RefValue.Static(type), value.field.field)
```

`JIRStaticDomainProducerTest` must exercise the real producer and compare its
emitted class/first-field pair with metadata collected from the same method.
Keep nested and inherited-reference syntax controls. The inherited fixture's IR
already resolves to the parent owner; do not claim an observed subclass-owner
mismatch from that fixture. Retain the red test and green test receipts.

Replay all three originally failing projects with the same configuration. If a
new coordinate fails, diagnose it through the same bounded callback route;
S bind-in can transport an older omission, so the immediate caller is not
automatically the creator. No persistent provenance tracking is required.

## 4. Repair metadata coverage

The coordinate repair must also close the metadata coverage obligations below.
These are correctness changes. They can restore previously omitted work, so
factoring must be compared with this repaired version.

### 4.1 Make alias knowledge explicit

The pre-repair collector used:

```kotlin
val availableAlias = aliasAnalysis?.takeIf { it.computedAliasInfo() != null }
val unknownAliases = aliasAnalysis != null && availableAlias == null
```

The null-AA fallback therefore means `unknownAliases=false`, although runtime can
enable and compute AA later. A nonnull, uncomputed AA object takes a different path.
Replace that ambiguity with the explicit selected policy:

```kotlin
internal enum class MetadataAliases {
    Disabled, Unknown;

    companion object {
        fun forRuntime(enabled: Boolean): MetadataAliases =
            if (enabled) Unknown else Disabled
    }
}

// Derive this from runtime settings, not the AA-disabled scratch compiler.
val policy = MetadataAliases.forRuntime(params.aliasAnalysisParams.useAliasAnalysis)
val collector = JIRStaticMetadataCollector(scratchCompiler, policy)
// Apply the same policy to retained prescan contexts and graph-only methods.
```

A completed prescan AA result is not certified to cover all independently timed
runtime outcomes. This iteration therefore has no `Known` state and borrows no
AA owner. Missing, uncomputed, failed and completed contexts all use Unknown
when runtime AA is enabled. Disabled means runtime really disables it. A future
Known policy would need a separate method/context and model-coverage proof.
Collection must not start new AA after prescan workers join.

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

Keep the source/sink distinction when enabling the callback:

```kotlin
// CALL source: no action means no emitted source form or source mark need.
forms.rewriteSourceOrSink(
    sourceRule, rewriter,
    potentialMarkLiteral = metadataSourceLiteral.takeIf {
        sourceRule.actionsAfter.isNotEmpty()
    },
)

// CALL sink: checking a predicate still needs its marks without end actions.
forms.rewriteSourceOrSink(
    sinkRule, rewriter,
    potentialMarkLiteral = metadataSinkLiteral,
)
```

An empty source action list must not create predicate interests or ownership
through this callback. A sink with no end actions still performs its check.
Keep the executable empty-source/empty-end-sink control.

The integration points are
[JIRStaticMetadataCollector](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRStaticMetadata.kt),
[JIRBidiAnalysis.gather](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt)
and the existing metadata policy arguments of boundary/call form builders.
Ordinary runtime predicate evaluation is not replaced by metadata approximation.

### 4.2 Cover producer coordinates without widening every footprint

Use the existing collected basis after the canonical-owner and metadata-policy
repairs. Do not add an independent per-method producer-domain store. The selected
constructor argument is in
[the frontend bridge proof](../../spec/run1-request-proofs.md#13-selected-jvm-producer-domain-construction):

```text
StaticFieldLoad(C.f)
  -> canonical pair (declaringClass(f), f)
  -> direct metadata access and local alias share that pair
  -> a later alias suffix retains the first class/field pair
  -> call binding and the transitive call closure preserve coverage
```

The AA static container is introduced by a static-field load. Its exposed alias
therefore retains that originating first field. Statement alias writes and
persisted call aliases need not add every field of a class to Sigma.

Also check the accepted rule constructors. A generic transfer from `arg.f` to a
bare static class could invent a new `(class, field)` pair by retaining the suffix.
The selected JVM pass-rule validation already rejects a bare `ClassStatic(C)`
target for `CopyMark` and `CopyAllMarks`. Generated class-point actions add no
residual suffix. Explicit field targets declare their fixed class/field pair.
The proof depends on these existing validation rules; it does not apply to
arbitrary hand-built generic AP providers or future constructors without review.

Keep neutral accessor descriptors across metadata and engine managers. Compile
them through the destination AP manager, under different interning orders in the
regression control. Never copy numeric IDs from the scratch manager.

Do not populate Sigma with arbitrary classpath fields, promote Sigma into every
H or S, or broadcast all marks to all fields. If a newly reproduced omission
also reads or writes a method-owned component, repair its H/needs as well.
Adding only the domain cannot prove that the untouched frame is safe. Keep
`declaredFieldUniverse` and `declaredClassUniverse` as existing generic API inputs;
this repair needs no new JVM domain population path.

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

### 5.1 Storage and query cost

Let N be the number of selected slots in one distinct profile, F its retained
sparse field entries, and C its class-common entries. The existing selected set
remains. Relative to it, the expanded map owns up to N row entries; the new base
owns a descriptor and F sparse entries and borrows the existing C class entries.
Count shared objects once across profiles, rather than multiply them by method
uses. Mark payload sharing already exists in the map control.

| Operation | Materialized map control | Factored base |
| --- | --- | --- |
| Freeze final B | Visit selected slots and build each nonempty union row | Freeze common/sparse descriptors; do not construct expanded rows |
| Row membership | Lookup the row and test its stored mark set | Check S, then test common and sparse sets |
| Borrow one row | Borrow one stored set | Borrow one set or a two-segment immutable view |
| Enumerate one B row | Read each union ID once | Consume common IDs plus sparse IDs; emit each distinct ID once |
| Broad matching | Visit nonempty B map keys, then D-only keys | Visit S and test B emptiness, then D-only keys |
| Matching result | Detached list of immutable row descriptors | Same list remains; some descriptors contain a two-segment view |

A two-segment cursor consumes `|common| + |field|` source cells, including
overlaps. A broad request-store pass examines up to `|S| + |D keys|` slots.
The old B-key pass can be smaller when many selected slots have empty B. The
optimization removes standing expanded rows and their construction; it does not
remove these scans, transient views or matched-row lists. Measure these costs
and their allocation before claiming an engine or memory gain.

### 5.2 Concrete before/after example

The following descriptors produce three nonempty logical rows:

```text
S                  = {C.f, C.g, D.h}
rootMarks          = {T}
commonByClass[C]   = {T, U}        // root already included
fieldMarks[C.f]    = {U, V}        // partial overlap is allowed

Logical B(C.f)     = {T, U, V}
Logical B(C.g)     = {T, U}
Logical B(D.h)     = {T}
Logical B(C.z)     = {}            // z is outside S
```

```kotlin
// Map control: retain one expanded row per selected nonempty field.
for (slot in selected) {
    val marks = unionRequestMarks(
        common(slot.cls), fieldMarks[slot] ?: RequestMarkSet.EMPTY, checkpoint,
    )
    if (!marks.isEmpty) expanded[slot] = marks
}

// Factored: retain the existing selected set and common payloads,
// plus the sparse field overlay. Build no expanded field-row map.
val base = FrozenStaticRequests.freeze(
    footprint, rootMarks, commonByClass, fieldMarks, checkpoint,
)
check(base.borrow(slotCF).containsId(markV.id))
check(!base.hasRow(slotCZ))
```

`freeze` borrows an already owned common map, detaches mutable field scratch
once, and returns `EmptyFrozenRequestBase` when the logical row count is zero.
Its production call uses the existing `classWideMarks` as `commonByClass`.

Both forms contain three logical rows and six logical mark memberships. The
factored form stores only the `C.f` field overlay in addition to the common
descriptors and existing S. It does not create a union array for `C.f`. If all
of that overlay's marks already belonged to `common(C)`, freeze would omit the
overlay while retaining the same S and B. Existing whole-profile and mark-set
sharing still applies. This example counts relation members, not heap bytes.

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
val requests = RequestStorage(sharedBase = profile.precomputedRequests)

// Owned by the method actor:
var temporary = RequestStorage(sharedBase = EmptyFrozenRequestBase)
```

Keep the existing `precomputedRequests` property name and change its type from
`RequestRows` to `FrozenRequestBase`. Update every consumer to the base API;
do not expose an expanded-map compatibility getter in production.

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

### 7.1 Complete matching selector

The selector below shows the existing geometry with the new frozen-base access.
It omits only optional statistics. `hasRequestBroadLeaf` recognizes a FLOW
leaf or a concrete `TaintLeaves.any` leaf that can supply descendants. The latter
represents ANY or ANY_TAINT according to the layer. An exact ancestor alone
does not supply its descendant fields. The existing helpers inspect geometry
without applying field exclusions or FLOW mark exclusions.

```kotlin
// In RequestStorage; optional statistics arguments are omitted here.
fun borrowMatchingRows(
    input: Facts,
    checkpoint: () -> Unit,
): List<RequestedRow> = buildList {
    checkpoint()
    val root: TrieNode<*> = when (input) {
        is Reach -> return@buildList
        is FlowTree -> input.root
        is TaintTree -> input.root
    }
    if (root.isEmpty) return@buildList

    fun addSlot(slot: Run1Slot) {
        checkpoint()
        val row = borrow(slot) // immutable B view plus captured immutable D
        if (!row.isEmpty) add(RequestedRow(slot, row))
    }

    if (input.base != STATIC) {
        addSlot(Run1Slot.Base(input.base))
        return@buildList
    }
    if (root.hasRequestBroadLeaf()) {
        forEachStaticSlot(checkpoint) { addSlot(it) }
        return@buildList
    }

    var hasBroadClass = false
    root.requestChildren(checkpoint) { cls, node ->
        if (node.hasRequestBroadLeaf()) hasBroadClass = true
        else node.requestChildren(checkpoint) { field, child ->
            if (!child.isEmpty) addSlot(Run1Slot.StaticField(cls, field))
        }
    }
    if (hasBroadClass) forEachStaticSlot(checkpoint) { slot ->
        if (root.child(slot.cls)?.hasRequestBroadLeaf() == true) addSlot(slot)
    }
}

// In SummaryStorage: keep the existing lock through list construction.
fun borrowMatchingRequests(input: Facts): List<RequestedRow> =
    synchronized(lock) {
        requests.borrowMatchingRows(input, ap.cancellation::checkpoint)
    }
```

These are class-body sketches: `borrow`, `forEachStaticSlot`, `requests` and
`lock` remain members of their owning classes. The implementation keeps
its statistics arguments. The `forEachStaticSlot` body is the B-row/D-only loop
above. Each narrow field needs one lookup; broad branches visit stored selected
slots, not the global domain. Explicit fields and broad-class passes are
disjoint, so the selector does not emit the same slot twice. The caller consumes
the completed list after unlocking; it does not borrow a mutable map iterator.

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

### 8.3 Dynamic request delivery to the caller

Factoring changes the borrowed B segment, not this event route. For a dynamic
request `callee:f/T` and an eligible abstract input whose original caller slot
is g, the route is:

```text
callee actor: collectRequests(f, {T})
  -> callee P, after subtracting current callee R
  -> method fact/publication queue empty
  -> callee SummaryStorage.commit(P); replace P
  -> fresh D(f)/T notification to registered subscribers
  -> RunEvent.ChangedRequests(calleeKey, delta), queued on caller runner
  -> caller SubscriptionManager.onRequests(calleeKey, delta)
  -> match retained caller inputs to f
  -> recover original caller g from input.ref.premise
  -> merge T by (full caller MethodKey, g), subtract caller R(g)
  -> RunEvent.MarkRequestsIn(callerKey, {g -> {T}})
  -> caller actor: collectRequests(g, {T})
  -> caller P; commit at that method's queue-empty boundary
```

Every renamed slot uses the original premise, not the callee's output slot.
Concrete-origin inputs answer from their actual supplied marks and never climb.
An unchanged static slot skips inherited B only; fresh D still follows this
route. A late subscription reads cumulative R after registration, so it sees a
request committed before registration without a second retained request copy.

```kotlin
// In joinRequests, after the FLOW and normal-origin guards:
val original = checkNotNull(normalRun1Slot(part.ref.premise))
val target = ClimbTarget(part.ref.caller, original)
val admitted = port.summaryStorage(target.method).borrowRequest(original)
val destination = climbs.getOrPut(target, ::RequestMarkSetBuilder)
offered.takeSnapshot().forEachMark { mark ->
    ap.cancellation.checkpoint()
    if (!admitted.containsId(mark.id)) destination.add(mark)
}

// After visiting all candidate inputs, group the accumulated original slots.
val requestsByMethod = LinkedHashMap<MethodKey, MutableMap<Run1Slot, RequestMarkSet>>()
for ((target, marks) in climbs) {
    ap.cancellation.checkpoint()
    if (!marks.isEmpty) requestsByMethod
        .getOrPut(target.method, ::LinkedHashMap)[target.slot] = marks.takeSnapshot()
}
for ((method, rows) in requestsByMethod) {
    ap.cancellation.checkpoint()
    val accepted = port.send(RunEvent.MarkRequestsIn(method, freezeRequestRows(rows)))
    ap.cancellation.checkpoint()
    check(accepted) { "An active run rejected a request delivery" }
}
```

The last snippet describes the target and batching contract; the existing
`joinRequests` performs the actual row grouping, cancellation checks and required
delivery checks. Do not add a separate forwarding store or alter its barriers.

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

The old `requestScannedKeys` counts nonempty frozen map keys and dynamic keys.
After factoring, a broad pass counts examined S slots and dynamic keys, including
empty B slots in S. Use `requestFrozenSlotsExamined` and
`requestDynamicKeysExamined` to expose that split. A higher examined-slot count
does not mean more logical requests. Compare matched rows and logical B counts
as separate units.

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
| Source/sink metadata eligibility | Empty-action sources add no predicate interest; empty-end sinks still inventory their live predicate marks |
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
| 1. Diagnostic and reproducer | `Run1Abstraction`, actor key handoff, actual JVM producer fixture | Actual failing coordinate recorded; real producer regression fails before repair |
| 2. Coverage repair only | `JIRDSUAABuilder`, `JIRStaticMetadata`, `JIRBidiAnalysis`, metadata policy and symbolic mark inventory | Selected-frontend constructor argument and executable controls support H/Sigma coverage; corrected map control retained |
| 3. Immutable mark/base views | `RequestMarkSet`, `Run1Requests`; empty/map adapters and sorted union cursor | Independent lookup/iteration/cardinality/cancellation controls pass |
| 4. Factored freeze | `Run1StaticProfiles.freezePlan`, `covers`, mark ownership census and logical statistics | Expanded/factored equality on finite closures; no production expanded base retained |
| 5. Storage integration | `SummaryStorage`, `RunManager`, precomputed iteration in `SubscriptionManager` | Same R/P protocol, matching lists, inherited-B behavior and late wide activation |
| 6. End-to-end verification | Existing common/JVM tests and fixed project matrix | Progress, strict findings, completed engine times and resource evidence reported |

Keep coverage repair and factoring as separate reviewable commits. Do not combine
pending-admission fast paths, delta-join changes, class-point descriptor factoring
or scheduler changes into them. A prototype may use the map adapter for controls;
the final runtime selects only one base representation.

At this document's snapshot, steps 1–2 are committed as `d330a335a`. Their five
common diagnostic tests, 121 focused JVM tests and 408 full JVM tests pass, with
no failures, errors or skips in those selections. These receipts do not establish
project completion or a full application-suite pass. Steps 3–5 are integrated
as `4acde739b` from isolated commit `bbdbe7307`, whose 66 focused common tests
pass. The integrated source passes all 1,480 common and 408 JVM tests, with no
failures, errors or skips. Application test compilation and root classes pass.
Both fresh application gates ran 158 tests: 149 pass, eight failures, no errors
and one existing skip. The failed rule-ID memberships and multiplicities match;
six raw assertion lists differ only in order. Both strict gates remain failed.
Step 6's final fixed-project phase and strict finding comparisons are terminal;
see the dated snapshot in section 1. Profile and memory comparisons are complete.
All six OOM captures have closed owner reviews. No net saved-byte total or
factoring speedup is established.

After integrating the selected factoring change, run the common and JVM gates
and compile the application tests before the final project matrix:

```bash
env JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 OPENTAINT_TEST_BIDI=true \
  ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileTestKotlin \
  --max-workers=2 --console=plain \
  -x :opentaint-ir:go:buildGoServer
```

Run the existing strict bidirectional application selection as well. Record its
actual pass/fail/skip counts and diagnose changes from its prior eight failures.
Do not change a finding comparator or expected result to make this gate pass.

## 13. Measurement and acceptance

Use the exact existing OWASP, Conductor, ThingsBoard and Stirling-PDF configurations
and direct Gradle `:runProjectAnalyzer`. Do not change project heaps, timeouts or
field limits. Preserve the original strict identity, span and occurrence comparator.

Keep continuous JFR and the same 30-second CPU capture windows for both versions:
start the normal window 60 seconds after full analysis starts; start the late
window 60 seconds before the IFDS time limit. If analysis ends before a window,
record that window as unavailable. Capture a heap whenever MemoryManager reports
OOM and inspect its retained owners after the scans finish. A missing or failed
required capture is an evidence gap, not a reason to infer the memory owner from
allocation samples.

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

Bind each heap to its actual engine and owner lifetime. A run-4 PDF snapshot
can occur after the run-1 mark plan has been released. Such a snapshot can
diagnose run-4 owners, but absence of B there cannot establish the saving at
peak run-1. Use the plan census and a comparable live owner interval for the
factoring claim; do not equate a later OOM heap with a run-1 census.

For each project, publish one comparison row with these fields:

```text
revision; terminal status;
latest reached run / latest completed engine / accepted forward report;
completed run-1 engine seconds; all later engine and barrier seconds;
missing identities / changed spans / added identities / occurrence changes;
physical B rows / sparse entries / unique descriptors / logical B memberships;
peak RSS; sampled allocation and CPU windows;
OOM events / required heap capture status / retained-owner evidence
```

Use `none`, `in progress` or `not captured` where appropriate. A failed run-1
duration is not a completed run-1 time. Compare physical layout and equal logical
membership separately from sampled allocation and retained heap bytes. Source
counts can prove that expanded rows were removed; they cannot prove a byte saving
or a project speedup.

Before new measurements, retain compact evidence and clean prior raw captures as
required by `task.md`. Run scans serially and perform heavy profile/heap analysis
after captures finish. The existing eight strict application failures remain
visible until they are actually resolved; no assertion or comparator is relaxed.

Factoring succeeds when logical B/R, required initial/summary behavior and findings
match the corrected control, the expanded B relation is absent from production,
and full replacement/query costs improve measured memory without an unacceptable
engine regression. The broader target remains a completed run-1 within 60 engine
seconds on every fixed project, with no missing baseline findings. The fresh
measurements do not meet that target.
