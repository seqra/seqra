# Semgrep rule selection, mark reduction and early specialization

Date: 24 October 2026. Status: **implemented; new project measurements pending**.
Reference source: `f35aae4e4` (documentation revision; runtime unchanged).
Its runtime is the measured `fb84969c0b91ccae0500389b5e25e045557b0eb3`.
Read the [proof dossier](semgrep-rule-selection-and-specialization-proofs.md)
and [proof guide](proof-guide.md).

This specification replaces the selected parts of the
[work-admission proposal](../docs/design/bidirectional-thingsboard-run1-work-admission-20261023.md).
It includes the two added mark reductions. It does not select local
liveness, sparse propagation or origin batching.

Latest historical Thingsboard state: **run-1 OOM**, with no COMPLETE or
accepted forward iteration. The selected local checks pass. The fresh project campaign is pending.
The [measurement report](../docs/reports/bidirectional-full-work-batching-performance-20261023.md)
keeps the remaining precision and performance failures.

## 1. Selected changes and semantic boundary

| Change | Current behavior | Required behavior | Proof obligation |
| --- | --- | --- | --- |
| Metadata selection | allRelevant=true bypasses Semgrep selection | Start from supported active graphs, plus conservative unknown/shared support | Do not omit metadata candidates with required ownership support |
| Early specialization | Specialize and cache raw candidates, then discard selected-out items | Reject those raw items before binding when binding/report identity is stable | Exact ordered equality to late filtering of the same catalog |
| Executable CM | A CM guard can remain when its mark has no possible assignment after reduction | Replace CM for keys outside assigned ∩ CM-used by false; simplify executable conditions only | No-spontaneous-mark cover and truth preservation; cover NOT too |
| Executable GEN | Emit assignments whose mark is never checked by executable CM | Keep GEN only for keys in that same intersection, with original indices and binding skeleton | Preserve checked-mark outputs and all protected observations |
| Metadata removals | Metadata can remove marks which executable rules cannot produce or use | Keep effective mark-removal actions only for final executable live keys | Preserve resets of live keys and their original position carriers; omit only irrelevant targets |
| Rule removal | The previous normalizer kept false/actionless rows | Drop false rows; drop actionless source/cleaner rows; recompute executable mark sets | Role-aware observations, cascading fixed point and strict structural decrease |

All rows except early specialization change the effective rule program. Do
not call them byte-for-byte equality of every old summary. Inactive FLOW
mark exclusions, impossible guards, unused mark facts, unused source hits
and demand rows for unused marks can disappear. A negative absent-mark
check can become true and can make a source cube zero-satisfied. This is
an explicit condition change, not a new origin-selection rule. Metadata
conditions remain unchanged. Metadata pruning must preserve every reset
of a live key, including its original action-position carrier. Dropping an
unused raw assignment must not accidentally remove a live mark's reset.

The second row is an optimization of that corrected rule program. It
must preserve the complete retained rule lists: order, duplicate variants,
conditions, surviving action identities, report IDs, binding keys and
the normalized metadata. Original ordinals must not be renumbered after
compaction.

Preserve field transfer and field-exclusion rules, AP tails, initial grammar, layers,
static-pool transitions, summary application, contributing-origin rules,
controller annotations, exit rules and later full-mark analysis. Keep
all loaded mark declarations and mark interning. Mark availability below
is compiler information, not a runtime mark-eligibility filter.

The shared provider contract covers JVM legacy, JVM bidirectional modes
and later iterations, Go, and supported direct queries. It is not a
run-1-only core flag. FULL remains the default. The field-limit schedule
is unchanged; public limit zero remains invalid.

## 2. Values, ownership and observations

A FullMark is the complete immutable owner plus full mark name, with the
existing primitive property. Equal displayed names under different owners
remain different. GeneratedMark uses its finalized full identity. A rule
graph ID is not automatically its mark-owner ID.

CM denotes the complete mark-test family, including ordinary ContainsMark,
JVM ContainsMarkOnAnyField and Go ContainsMarkOnAnyAccessor. A position
includes its query kind. Count all those consumers and rewrite all those
variants. Global absence of a full mark makes each such query false;
this does not add a spatial approximation.

A raw item has a nullable serialized item ID. It can produce several
specialized rows. Each row retains that ID, the original binding context,
rule info, action identities and any public report ID. Do not use a public
report ID or group ID as a substitute for the serialized item ID.

The effective view has one immutable snapshot:

```kotlin
// Contract pseudocode, not a new runtime implementation.
data class SelectionSnapshot(
    val owner: ConfigurationOwner,
    val view: ProviderView,
    val catalogGeneration: Long,
    val selectionGeneration: Long,
    val executionIds: Set<ItemId>,          // S
    val knownSemgrepIds: Set<ItemId>,       // K
    val metadataIds: Set<ItemId>,           // M
    val markReduction: ReducedMarks,
)
```

Before the first selection, a null snapshot means the current broad view.
A selected empty set is different. Entering Prescan does not silently
reset an existing snapshot. Reuse/reselection is an explicit generation
transition, not an automatic phase-dependent change.

Logical observations retain full mark, location, rule/action/binding
identity, every relevant origin, layer and field annotation. Early versus
late specialization of the same corrected program preserves those values.
The metadata action policy can erase resets of nonlive marks, but it cannot
erase a live mark's reset. Use the projected reset laws for that comparison.
No origin substitution is selected.

Specialization labels are compiler metadata. They must not change the legacy
`equals` or `hashCode` of a JVM configuration row. Compare all original
semantic fields, in their original hash order, and exclude only the attached
specialization labels. Keep duplicate entries in the ordered rule list and
keep their original rule/action ordinals. Equal configuration rows can still
have distinct internal occurrence IDs. Do not turn ordinal preservation into
additional public vulnerability identities. This is an existing-behavior
preservation requirement, not an added semantic feature.
Opaque observers, imported inputs and unknown provider dependencies need
conservative support or the unchanged path.

## 3. Compute S and metadata support

### 3.1 Keep the existing reducer

Call the current reduce on each loaded outer graph and the prescan item
IDs. Keep its Matching, Taint, Join, final-item and dependency behavior.
Dependency alternatives must not become an AND requirement.

```text
S = union of the unchanged reductions' retained item IDs
K = all known serialized Semgrep item IDs
```

S is not a list of public rule IDs. It is also not the metadata closure.
The raw reducer is a named source adapter; this change does not redesign
or claim new correctness for that existing reducer.

### 3.2 Conservative metadata support

Seed support with every applicable outer graph, every graph supporting an
S item ID, and every uncertain graph. Include all items of a supported graph
as metadata candidates, including unselected internal branches and join
children. The executable-derived normalization in section 4 can then remove
their unused actions, false rules and actionless source/cleaner rules.

Close support over shared mark owners, shared full marks and shared item
IDs. Include marks mentioned by conditions, GEN actions, cleaners and
relevantTaintMarks. When loaded entries share an owner, retaining only
one entry is insufficient. A rejected item must have no path to required
metadata through those relations.

Use finite growth over the loaded graph catalog. Verify the result is
seed-complete and closed. If coverage, import ownership, ID provenance or
closure cannot be established, retain the affected metadata; an ALL
metadata fallback is valid. Do not drop an ambiguous graph to improve a
benchmark. Non-Semgrep and null-ID metadata keep their existing path.

```text
M = item IDs of the conservative supported graphs
    plus IDs retained for ambiguous external provenance
```

No method specialization or runtime mark-member enumeration is required
for this catalog computation. Keep raw rules and declarations intact.

### 3.3 Query policies

| Adapter and role | allRelevant=false | allRelevant=true |
| --- | --- | --- |
| JVM entry/call/exit source, all sink roles, static source | Keep null ID or ID in S | Keep null ID, ID in M, or ID outside K |
| JVM cleaner | Existing broad execution selection | Apply the metadata policy |
| JVM pass-through | Existing broad behavior | Existing broad behavior |
| Go global/field source and call sink | Existing S policy; no true API | No new API |
| Go call source | Existing S policy | Apply the metadata policy |
| Go cleaner | Existing broad execution selection | Apply the metadata policy |
| Go pass-through | Existing broad behavior | No new API |

For normal S-filtered roles, a nonnull unknown ID keeps the old S test.
The unknown-ID exception above applies only to metadata. Before first
selection, every role keeps its original broad behavior.

JVM true queries must still call resolveRelevantRule. Preserve its
negative method-name, class-name and arity relaxation. Go keeps its
existing resolver semantics; do not add JVM relaxation to Go.
Apply the normalized action plan and false/empty-rule removal after the
proper normal/relevant resolver. An executable row rejected by an exact
method/arity check does not by itself reject its relaxed metadata variant.

## 4. Executable-led mark reduction and rule compaction

### 4.1 One-way dependency from execution to metadata

Only the executable rule set determines possible producers and mark uses.
Metadata conditions, actions, relevantTaintMarks, reset masks and supported
graph membership do not make a mark available or used. There is no union
of executable and metadata producer/consumer sets.

For the current executable rules define:

```text
assigned = possible executable GEN targets
checked  = executable CM keys, including negative CM
used     = assigned ∩ checked
```

Include conservative roots/consumers only when they represent executable
behavior. An unknown executable scope can protect all keys. Metadata-only
references never contribute to these sets.

Use used for executable CM folding and GEN pruning. Normalize executable
rules to a fixed point. Then use its final used set for metadata mark-removal
targets. A metadata mark which cannot be assigned by execution is outside
used, even if some executable CM originally mentioned it. An assigned mark
with no remaining executable CM is also outside used.

Keep metadata conditions in their original relevant-resolution mode, with
the existing Boolean simplification. Absent-CM substitution is an executable
rewrite, not a metadata-admission policy. Do not turn a metadata cleaner's
NOT CM(U) guard into true and thereby add a reset.

```text
Execution assigns and checks T. U is outside used.
Metadata source B: AssignMark(U, Result); relevant marks are {T,U}.

Original metadata effects: RemoveMark(T, Result), RemoveMark(U, Result).
Required effects:          RemoveMark(T, Result).
Retain the Result position carrier even though its raw assignment names U.
```

Metadata eligibility refers to the target mark of an effective removal,
not the own mark of a raw source/cleaner position carrier. Current lowering
crosses action positions with relevantTaintMarks. Preserve those positions
for every retained removal. Filtering raw GEN/RemoveMark carriers by their
own mark would be incorrect in the example above.

One implementation can filter the row's relevantTaintMarks to final used
and retain its original positional actions; another can keep labelled
effective removals. Either must emit the same live-key resets. Keep structural,
RemoveAllMarks and opaque effects unless their mark-specific behavior is
known. The spatial reset kernel and field transfer stay unchanged.

```kotlin
// Metadata only. Use the original relevant-resolved guard and admission.
for ((actionOrdinal, position) in originalIndexedResetPositions) {
    for ((markOrdinal, mark) in originalRelevantMarks.withIndex()) {
        if (mark in finalExecutableUsed) {
            emitRemoval(position, mark,
                originalOccurrence, actionOrdinal, markOrdinal)
        }
    }
}
// Remove an empty source/cleaner metadata effect; keep an actionless sink.
```

Original ordinal labels must survive any prior flattening or position
expansion. Filter after obtaining them; do not renumber a compacted action
or relevant-mark list. This snippet does not evaluate or specialize a CM
in the metadata condition.

Keep the original raw binding skeleton, stable rule occurrences and action
ordinals separately from the effective removal list. Removing an item must
not renumber surviving reports or trace identities. Metadata is a consumer
of the final plan; it must never feed another executable normalization round.

### 4.2 Closed mark inventory

Use the effective executable catalog after the S/role policies. Include
all potential method/entry/exit/static GEN actions before method binding;
do not infer absence from the set of marks already observed at runtime.
Currently broad pass/cleaner roles remain in the executable catalog.

Account for all independent executable mark roots and consumers in the
selected view. Known default/wrapper/native/END execution can contribute
protected producer and consumer keys. Protection must describe executable
behavior, not metadata-only references. Legacy END actions must not be
removed through the new core's empty-END assumption. Unknown
inputs, arbitrary mark-sensitive callbacks, unresolved generated names,
renames or opaque providers require protection or an identity fallback.
The new core keeps its current empty END behavior; this spec does not
change legacy END execution.

Generated Semgrep identities can be reduced when their complete producer
and consumer scope is known. Do not constant-fold foreign marks from an
incomplete catalog. Do not add a per-fact producer lookup or filter requests
at a static owner. This is a once-per-snapshot compiler analysis.

The inventory spans the entire selected executable catalog, across all
methods and rule roles. Never compute absence from one method's bound
rows or one provider query: a producer in method A can satisfy a CM in
method B. Resolve symbolic or generated keys conservatively; an incomplete
identity mapping uses protection rather than an empty producer set.
The catalog used by this analysis is a compiler copy. Keep the original
raw rules and binding skeleton for actual method specialization.

### 4.3 One reduction round

Let P be possible executable GEN keys plus protected executable producers,
and C be current executable CM keys plus protected executable consumers.
Set used = P ∩ C. For every executable condition:

```text
CM(position,T), T not in used -> false
NOT false                  -> true
A AND false                -> false
A OR false                 -> A
```

Use the existing Boolean/NNF simplifier once when compiling the global
catalog. Each reduction round then folds constants with the fixed
constructors of the checked fold function. Preserve all nonmark tests.
A full key is tested at all positions: absence is global within this
closed view, not a new position/type/reachability approximation.

Collect both P and C at the start of the same round. Remove executable
AssignMark/GEN actions with targets outside used. Keep other
executable actions under the existing contract. The separate metadata pass
filters effective removal targets by final used, while retaining their
original position carriers. Preserve structural, opaque, copy and all-mark
actions, original IDs and relative order.
Do not remove a mark merely because its CM check is currently unreachable
under an unproved program analysis.

A false row is removed for every role. A source or cleaner with no retained
actions is removed even if its condition is true or mentions marks. An
actionless sink is not removed: its check/report is an effect. No general
empty-action deletion is specified for other roles.

Rule removal is part of the round, not a final formatting pass. The next
round recomputes P and C from the remaining executable rules. A deleted
source can remove a CM use; a deleted false source can remove the last
producer. Neither old mark sets nor removed guards may keep those keys live.

```kotlin
val available = assignedTargets(rows) + protectedProducers
val checked = containsMarkKeys(rows) + protectedObservers
val used = available intersect checked
val folded = rows.map {
    it.withCondition(foldAbsentCMWithBooleanConstructors(it.condition, used))
}
val next = folded.map { row ->
    row.keepOriginalActionIndices { action ->
        action !is AssignMark || action.fullKey in used
    }
}.filter { row ->
    !row.condition.isFalse &&
        !(row.role in setOf(SOURCE, CLEANER) && row.actions.isEmpty())
}
```

This pseudocode uses complete bound identities; it does not compact action
indices or rebuild binding contexts. An unknown executable scope can
protect all keys: disable CM/mark-action pruning in both views rather than
treating an unknown used set as empty. Only proved effectless rule compaction
can remain. A literal's absence can only be used with the stated
no-spontaneous-mark cover, not with a claim that mark names are illegal.
Here rows denotes the complete catalog model, not the rows returned for
one method. Publish its final mark-reduction plan with the snapshot; each
query applies that same plan to its original bound rows, then removes
false and actionless source/cleaner variants. Bind original candidates
before compaction whenever skipping them would change binding/report IDs.

### 4.4 Fixed point and normalization placement

Repeat the reduction because removing a rule can remove the last producer
or consumer for another mark. CM-use sets, expression weight and GEN
sets only shrink. Truth sets need not shrink: NOT CM can become true.
Use executable equality to verify a fixed point. A bounded implementation
uses the proved structural bound: give a CM leaf weight two, a Boolean or
other leaf weight one, and each connective one plus its child weights.
Sum one per row, guard weights and action-list lengths over all rows. The
positive row cost accounts for removing a rule. Every changed
round strictly decreases that nonnegative measure. Initial weight plus
one is therefore sufficient; stop as soon as another round is equal.
Measure the catalog after its one-time Boolean/NNF normalization. Do not
put the unrestricted simplifier inside this measured loop: De Morgan
rewriting can increase structural weight. Reordering an equal-weight
tree also fails the stated same-or-strict law. Reuse production Boolean
constructors only after proving their correspondence to the checked fold;
otherwise use the typed fold specified by the model. The existing bound
resolver's final simplification stays outside the catalog fixed-point loop.
The total normalizer always returns a verified fixed point. It does not
need an arbitrary fuel limit. The checked model also retains an exact-old
fallback for a caller that explicitly uses its separate short-fuel API;
an unchecked partial result is never valid.
This bound covers global compiler rounds. It does not bound per-query
simplification, method binding or analyzer work.

At a verified fixed point, no rule is false, no source/cleaner is actionless,
every remaining CM has an executable possible/protected producer, and every
remaining GEN has an executable CM/protected consumer.
Mutual cycles can remain. This is not a new reachability solver.

```text
R1 source: CM(U) -> Assign(V)       // V has no CM consumer
R2 source: true  -> Assign(U)
R3 sink:   CM(T)                  // T has a separate retained producer

Round 1: remove Assign(V), then remove actionless R1.
Round 2: U has no remaining CM consumer; remove Assign(U) and R2.
         U is no longer available. Recompute before the next round.
Metadata-only CM(U) cannot keep U live or become its producer.
```

The final metadata action pass uses used* = P* ∩ C*. It cannot add a
producer, revive a removed executable rule or change the fixed-point test.
Use the same false/empty-role tests for metadata variants, testing their own
original resolved/simplified condition. For metadata source/cleaner rows,
empty means no effective metadata action remains; a carrier of a live-key
removal is not empty. Metadata-only conditions never contribute to P*/C*
and never revive an unused mark.

Compute mark sets from serialized catalog metadata. Apply the executable
rewrite to already specialized rows before core lowering. Keep the
original argument-binding skeleton and complete binding identities. This
prevents removal of an action from changing AnyArgument context products
or numbering unrelated surviving variants.

Perform the full-mark reduction before any existing GROUP projection.
Do not test group-level availability as a substitute for a full mark.
An old GROUP candidate can disappear when its full-mark guard is impossible;
this is the selected condition correction, not a field/layer change.
Keep the frozen normalization for all subsequent queries/iterations of
the selected analysis generation.

The normalized row must carry its original logical mapping, for example:

```kotlin
data class ExecutableRowView(
    val originalBoundRow: BoundRule,
    val normalizedCondition: Condition,
    val retainedOriginalActionOrdinals: IntArray,
    val originalRuleOccurrence: RuleOccurrenceKey,
)
```

An implementation can use an equivalent typed representation. Lowering
must use the retained original rule/action indices, rather than withIndex
on compacted lists. This is provider/compiler metadata, not a label
attached to propagated facts or a new grounding mechanism.

## 5. Avoid discarded specialization

Compare two providers of the same corrected snapshot:

```text
Reference: original ordered binding -> S/M filter -> executable normalization
Optimized: raw S/M filter -> original ordered binding -> same normalization
```

Filter raw items before AnyArgument context creation and condition/action
specialization. Keep the original function/class/signature matcher behavior.
Static-field sources need their own raw field-resolution seam.

For each retained item, produce the same complete ordered binding list.
Raw serialized IDs must be preserved by every bound variant. Preserve
empty variants, duplicate variants, original action ordinals, array
expansion, conditions, rule info and the normal/relevant mode.

The postbinding normalizer is identical on both paths. This spec does not
claim that modifying a raw action list before binding preserves its context
product. Do not make that extra transformation as an implementation detail.

### 5.1 Identity-safe fast path

SerializedId is not the public report ID. JVM has a resolver-local fallback
counter; Go has a configuration-wide fallback counter. An earlier rejected
fallback sink can change a later retained generated ID if its binding is
skipped.

Use early binding only when the entire candidate query is proved stable,
for example all sink candidates have explicit report IDs or a stable CWE.
Do not check only retained items. If any candidate can affect fallback IDs,
execute the original complete ordered binding and then filter/normalize.
Skipping binding for a selected-out item must not change another item's ID.

Use original broad bound rows for unsafe queries, then apply the pure
executable normalization. Do not fold a false sink before its original
fallback-ID effects have occurred. Preserve the old errors/diagnostic
policy where they are observable; an unclassified binding effect uses
fallback rather than an unproved purity assumption.

### 5.2 Practical work bound

Count binder invocations and actual enumerated binding contexts. For a
stable discarded item with two independent 20-way AnyArgument variables:

```text
Reference: 400 binding contexts, then zero retained variants.
Optimized:   0 binding contexts, zero retained variants.
```

The proved reduction concerns these logical work units. It does not give
a CPU-time, live-byte or complete Thingsboard run-1 bound. The source
selector alone, applied after binding, does not remove this work.

## 6. Cache ownership and handoff

### 6.1 Scope every selected cache

The same configuration can serve ordinary, no-END bidi, direct unfiltered,
default and wrapper views. Do not install one global S on that object.
Use an explicit owned resolver view/capability. An opaque/custom provider
keeps its overrides and the late-selection route.

A selected cache key, or its enclosing immutable epoch, must distinguish:

```text
configuration owner, provider view, raw catalog/body generation,
selection/normalization generation, role, normal/relevant mode,
ordinary/no-END mode, method or static-field subject
```

JVM declares four method maps per role and a separate static-field cache.
Go's sourceMemo and cleanerMemo currently share true/false binding because
that binding is identical. Once raw selection differs, split selected
mode keys or selected epochs. Query order must not choose the cached view.
Do not return a selected list through an unfiltered compatibility query.
These method/field keys describe the known base resolver, which ignores
the wrapper's statement/fact/initialFacts parameters. Keep Spring,
combined and custom context-dependent rewriting outside that base cache.
Caching a wrapper result requires all of its real inputs; this spec does
not add a second per-method wrapper cache.

### 6.2 Joined phase boundary

Build a complete immutable S/M/normalization snapshot, then publish it as
one generation. Join all old query readers and retire old executable
contexts before changing cache roots. No old call plan, cleaner suffix or
saved resume address may issue new queries against a half-reset epoch.
Externally retained immutable rule values need not be mutated.

For bidi JVM, use the existing joined successful prescan path, gather the
resolved graph/IDs/lambdas, install the snapshot, release prescan storage,
then prepareShared and new forms. Keep its existing success requirement.

For legacy JVM and Go, put the existing stopped-job/join check before
selectPhase(FullScan) and its cache mutation. A joined timeout/cancellation
keeps the existing continuation policy. Do not add a new prescan-OK check.
If jobs failed to join, leave selection/cache state unmodified and use
the existing failure path. Do not disable callbacks to force quiescence.

Keep the same snapshot across later bidi iterations and reversal. A new
selection/catalog generation needs a new owned form/query generation.
Entering Prescan alone does not clear a previous selection.
Publishing the identical complete snapshot is a no-op, including while
readers use it. A different snapshot requires the joined boundary. An
integer/hash match alone is insufficient unless it identifies the same
immutable policy and catalog content.

### 6.3 Preserve original provider combination admission

Normalize the same admitted program on both the late and early paths.
Preserve the existing EXTEND, OVERRIDE and IGNORE choices before false or
empty rows disappear. A removed extra row must not enable a base fallback.

For Go OVERRIDE, carry the original extra-query presence with that query's
result. It is a query-local value, not the normalized list's size and not a
mutable map shared by statements. For JVM OVERRIDE, retain the original
extra conditions as well as original presence. Filter base rows by their
original condition keys. A folded extra condition must not override a base
row which the original combination retained. Keep the original combined
row order and logical labels when normalized rows disappear.

Known provider catalogs can expose their raw executable scope through a
separate capability. Default method delegation must not silently bypass a
custom provider's overridden roles. Unknown scope uses TOP protection and
the original query path. Catalog union is conservative compiler scope;
it does not replace the original per-query combination decision.

The [composition model](lean/ApSpec/RuleProviderComposition.lean) proves
normalization commutes with those original admission decisions when every
normalized row retains its original row label. Runtime wrappers must supply
that label and query-local presence; no source/field approximation is added.

### 6.4 Retire obsolete safe caches; preserve unsafe identity

Drop obsolete selection-sensitive, ID-stable cache slots after their
readers are gone. Keep raw catalogs, declarations, hierarchy/pattern
metadata and the original alias compatibility behavior. An additional filtered
wrapper above the old full strong cache is insufficient.

The alias-model reader is a separate compatibility consumer. It extracts
original pass-through CopyAllMarks actions without evaluating their guards.
Give it the original pass-rule view, not the compacted executable result.
Otherwise dropping a folded-false executable pass rule can silently change
alias handling. Those ignored guard marks do not become executable CM uses.
The ordinary execution view still drops false rules as specified.

Go's unsafe sink memo and global fallback counter need a compatibility
exception. Retain or transfer those original broad query results, with
the original counter state, into a selection-independent owner lane.
Apply selection and executable normalization after reading that lane.
A new unsafe query uses the original generator state and order.
Do not reset the counter, hash a new ID or rebuild cached fallback IDs.
The lane and counter remain configuration-owner resources shared by its
views. A selected epoch refers to them; it must not clone a private counter
for each view. Interleaved compatible queries keep the original global
counter order.

Those raw compatibility results survive a selection change only while the
catalog and method/body generation stay the same. They must not hit after
a changed catalog/body. Follow the existing configuration-load behavior
for invalidation; Go's current counter is not reset by clearing its memo.
This spec does not change IDs across a raw configuration reload.

The same conservative lane can retain JVM fallback queries. Known stable
queries use new scoped caches. The compatibility lane is not an answered
request archive or a new rule registry: it preserves existing raw memo
semantics. Its old broad retention is an explicit memory exception.

Do not change SoftReferenceManager, add WeakIdentityCache or modify IR
storage. No cache byte saving is claimed until actual strong roots and
compatibility exceptions have been measured.

## 7. Proof and implementation gates

The [dossier](semgrep-rule-selection-and-specialization-proofs.md) maps
these requirements to checked constructive modules. Local models do not
prove Kotlin lowering or source census completeness automatically.

Before implementation acceptance, cover:

- Unchanged S reduction; active unselected internal resets; shared owner,
  mark and item-ID closure; null/empty/unknown roles; all public true routes.
- Negative method/class/arity relaxation; normal versus relevant caches;
  ordinary/no-END views; default/approximation and opaque wrapper roles;
  original combination presence/condition keys before compaction.
- Positive/negative absent CM, compound simplification, cascading reduction,
  unused GEN, protected external/END uses, same-name/different-owner marks,
  primitive keys, effective reset positions and binding/action identity.
- Executable-only P/C; removal of false rules and actionless source/cleaner
  rules inside the loop; lost last producers/consumers; actionless sink reports.
- Metadata-only CM/GEN cannot preserve mark eligibility; removal targets
  outside assigned ∩ checked disappear; RemoveAllMarks/structural effects stay.
- Original metadata guard admission; no new cleaner admission from absent-CM
  substitution; exact reset membership for live keys; unused carrier marks
  do not justify dropping a live-key reset.
- Unlabelled versus labelled configuration equality/hash; differing ordinal
  labels; all original semantic fields; duplicate ordered occurrences and
  distinct internal IDs without additional `VulnerabilityKey` identities.
- Exact ordered late/early rule lists and the real 20-by-20 context product;
  explicit/CWE IDs; JVM fallback numbering; Go memo/counter preservation.
- Global catalog normalization before measuring; production round/fold
  correspondence; no query-local producer sets or unproved NNF growth bound.
- Shared configurations/views, true-first/false-first queries, no obsolete
  epoch hits/publications, successful join, joined timeout and failed join.
- Actual selected source/sink identities, every required origin, static
  answers and marked run-2/later witness guidance under the revised rules.

No existing failed expectation may be changed to hide a new mismatch.
The runtime implementation now follows this contract. Implementation checks
and the fixed project campaign remain separate acceptance gates. No new
candidate project result is certified here. Use the fixed project parameters.

## 8. Measurement limits

The earlier reference static-origin pending-image join found a conditional
52.97% inactive-owner membership share. It is not a prediction for the new
ownership closure or the added normalization. The 79.17% producer-only
bound is not the selected metadata policy.

The reference sink-cache union is 1,082.107 MB. Its discarded-ID fraction
is unknown. These retained unions overlap other measurements. Do not claim
that whole cache is removable. Count surviving fallback retention.

Future reports must include latest reached, COMPLETE and accepted forward
iterations; run-1 engine time is the 60-second target. Neither local proofs
nor omitted binding counts guarantee completion or a whole-engine 2x gain.
