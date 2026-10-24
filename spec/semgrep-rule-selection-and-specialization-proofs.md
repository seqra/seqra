# Proofs for Semgrep selection and specialization

Date: 24 October 2026. Contract:
[complete specification](semgrep-rule-selection-and-specialization.md).
Reference source: `8c6a512e674b378f3185a037df07f2f5a2ae79f7`.
Implementation remains unchanged.

## 1. Claims and proof boundary

There are two different comparisons:

```text
A: current broad metadata, current executable rules, late binding selection
B: corrected metadata support and executable mark reduction, late selection
C: the same corrected program, guarded early selection and scoped caches
```

A to B is a selected rule-program correction. Compare protected mark
observations and their valid witnesses. Full old FLOW headers, unused
mark outputs and unused source hits need not be equal. B to C preserves
complete ordered provider results and logical report IDs. It is an
optimization of the same rule program.

The constructive modules are:

| Module | Checked model |
| --- | --- |
| [SemgrepProviderSelection](lean/ApSpec/SemgrepProviderSelection.lean) | Supported metadata closure, exact selection policy, stable views, projected reset observations |
| [RuleSpecializationSelection](lean/ApSpec/RuleSpecializationSelection.lean) | Independent late/early ordered binding loops, actual work counters, fallback-ID guard and mixed query traces |
| [SemgrepReducedMarks](lean/ApSpec/SemgrepReducedMarks.lean) | Typed full-mark expressions, absent CM, checked GEN, fixed-point fallback and protected observations |
| [SemgrepReducedMarksTermination](lean/ApSpec/SemgrepReducedMarksTermination.lean) | Strict structural decrease, total fixed point, idempotence and bounded changed rounds |
| [RuleMarkCoverage](lean/ApSpec/RuleMarkCoverage.lean) | Executable source/import/copy witnesses that establish no-spontaneous-mark cover |
| [RuleSelectionEpoch](lean/ApSpec/RuleSelectionEpoch.lean) | Query-key separation, cache soundness, joined handoff and preserved unsafe Go memo/counter |
| [RuleProviderReplay](lean/ApSpec/RuleProviderReplay.lean) | Interactive finite engine replay and unchanged handoff under local provider correspondence |

The audit checks all declarations owned by these modules, including
private helpers and generated declarations. It permits only propext and
Quot.sound. It rejects sorry axioms and Classical.choice. A compiling
source file alone is not sufficient.

These models prove local compiler/protocol laws. They are not a checked
refinement of every Kotlin provider, AP tree operation or engine path.
Source-shaped adapter controls in the specification remain required
before implementation acceptance. No arbitrary-project termination,
completed run-1 speedup or whole-analyzer no-FN theorem is claimed.

## 2. Metadata support is complete and conservative

Graph IDs, owner keys, full mark keys and serialized item IDs are separate
fields. Define sharing by common owners, full marks or item IDs. The
support seeds contain every applicable graph, S-supporting graph and
uncertain graph. A finite growth pass stays within the loaded catalog.

The executable support checker verifies two properties:

1. Every seed is retained.
2. Every catalog neighbor of a retained graph is retained.

If the candidate fails, the algorithm returns the whole catalog, which
satisfies both properties. Thus no correctness claim depends on assuming
a chosen fuel was sufficient for a minimal closure.

Induction on a finite SupportPath gives an executable membership proof
for its last graph. An active graph is a seed. Its complete raw item list
is therefore in M, including unselected internal reset items. Shared-owner,
shared-full-mark and shared-ID steps are covered by the same induction.
An opaque metadata boundary uses the ALL fallback.

Checked anchors include support_is_checked, support_path_complete,
active_graph_supported, shared_owner_supported, shared_full_mark_supported,
shared_item_id_supported and active_graph_metadata_complete.

The model does not change reduce. The adapter must provide its actual
applicable flags and S, preserve dependency alternatives, and populate
complete sharing information. Unknown ownership cannot be treated as an
empty sharing set.

## 3. Selection preserves the required lists

A null initial snapshot keeps all rows. Normal execution uses its old
null-ID-or-S predicate. Entering a phase does not change that snapshot.
Selected metadata uses null-ID, supported M, or unknown-ID retention;
uncertainty can select ALL. Cleaner/pass execution keeps its original
unfiltered role policy.

Selection is a list filter. Its list is a subsequence of the original,
and a row appears exactly when the original row appears and its policy
retains it. Copies, order and duplicate multiplicity remain unchanged.
This is stronger than equal sets of public rule IDs.

Checked anchors: initial_selection_unfiltered, normal_execution_unchanged,
phase_change_preserves_selection, execution_rows_unchanged,
selected_rows_are_original_subsequence, selected_row_exact and
copies_preserved_exactly.

A saved query is qualified by its provider view and snapshot generation.
A coherent reload returns the same rows. Stale or foreign-view replies
are rejected by the modeled adapter. Real cache/form ownership must
establish that saved addresses hold the appropriate generation.

## 4. Active reset observations are preserved

A raw source item can be absent from S while remaining necessary reset
metadata. The checked self-loop example has selected producer A for T,
a one-argument foo call, a B transition with a negated one-argument guard,
and a selected final consumer F of T. Relevant resolution admits B's
reset even though ordinary resolution rejects B as a source. Blind S
filtering loses the T reset; supported metadata keeps it.

The formal reset adapter keeps each row's reset position and full mark
mask. ResetCoverage states that every reset of an observable mark has
complete raw ownership support or an unknown route. This premise is about
raw-to-resolved lowering; it is not inferred from a display rule ID.

Fix an observable mark and position. A reset row which contains that mark
must be retained by coverage. Filtering cannot invent rows. Therefore
old and new reset masks have equal observable membership at each position.
A scalar TAINT survives precisely when its mark is not reset there, so
observable TAINT results are equal. FLOW adds reset marks to mx; intersecting
old and new mx with the observable set gives equal results. The full
origin/base/path/tail/layer/field-exclusion header remains equal.

The same mark-membership equality proves positive and negative active
mark checks agree. Unknown marks remain observable under conservative
support. There is an executable control where full inactive mx differs;
this rejects the incorrect stronger full-header equality claim.

Checked anchors: observable_reset_row_is_kept, projected_reset_mask_equal,
active_taint_observations_preserved, active_flow_observation_preserved,
active_mark_check_preserved and active_negative_mark_check_preserved.

The runtime must still use its original spatial cleaner kernel. This
scalar mask proof does not permit clearing an entire ANY/MUST subtree.
Preserve relevant-resolution modes, selector positions and complete
field annotations when lifting the proof to the current AP implementation.
The header denotes a normalized scalar leaf. An EXACT scalar leaf does
not observe an unused containing-tree anyTail carrier. Removing the last
inactive ANY sibling can canonicalize that carrier through the existing
factory; it does not authorize changing an active ANY/MUST annotation.

## 5. Early selection equals late selection

The specializer constructs a complete labelled variant for each context
and payload. It copies the raw serialized ID into every variant. Query,
mode, catalog generation and binding context remain in the label.

The reference recursively binds each raw item, filters its variants and
appends the remaining list. The optimized recursion first tests the raw
ID, then either executes the same binder or skips that item. For one item:

```text
keep(raw ID) = true  -> every bound variant is kept on both sides
keep(raw ID) = false -> every bound variant is discarded on the late side;
                       the early side constructs no variant
```

Induction over the ordered raw list proves exact list equality. Contexts
with no variants, repeated variants, unknown/null IDs and different modes
are covered. No assumption of equal final output is built into the two
loops.

Any deterministic postbinding normalization applied to equal lists gives
equal normalized lists. This connects the mark reduction without changing
the original argument-binding skeleton. It does not prove a different
raw action list has the same AnyArgument contexts.

Checked anchors: bind_preserves_serialized_id, filter_bind,
exact_ordered_variants, post_bound_rewrite_equal and
guarded_post_bound_rewrite_equal.

## 6. Report-ID effects require the guarded path

The executable stateful binder assigns explicit IDs and CWE IDs without
changing its counter. Fallback IDs consume it. The two-item counterexample
has a discarded fallback sink before a retained fallback sink:

```text
Late:  generated-1 discarded; generated-2 retained.
Early without guard: generated-1 retained.   // wrong public identity
```

stableQuery tests every candidate. When it is true, induction proves each
binding has zero counter effect. The pure filtering proof then lifts to
the complete reported rows and final counter. Otherwise guardedSink is
definitionally the whole original binding/late-filter pipeline.

Induction over a mixed trace of fresh and cached queries preserves exact
reported rows and the counter. The control interleaves fallback queries,
a stable GROUP query and a cached result: generated IDs remain 2 then 3,
while one stable discarded binder is omitted.

Checked anchors: guarded_sink_observation_equal,
unstable_query_uses_entire_original_pipeline,
stable_query_zero_counter_effect and fixed_cache_query_trace_equal.
The data predicates are public-report stability, not merely presence of
a serialized ID. Other observable binder effects also need classification;
unknown effects take the original path.

## 7. Absent CM is false under constructive producer coverage

The expression model has full owner/name keys and query-bearing positions:
exact, anyField and anyAccessor. Nonmark predicates are separate opaque
Boolean inputs. A recursive fold replaces only absent CM leaves and uses
Boolean constructors for NOT, AND and OR.

For any valuation with no true mark outside P, induction on the expression
proves equal Boolean evaluation before and after folding. At CM, a key in
P is unchanged. A key outside P cannot be true, so false is exact. The
NOT case complements equal values; AND and OR combine equal child values.
Thus negative absent CM correctly becomes true. No nonmark test changes.

RuleMarkCoverage supplies a constructive producer witness:

- Imported roots carry membership in the protected producer list.
- GEN roots carry the actual rule, action, position and membership proof;
  a syntactically false rule cannot be an enabled root.
- Mark-preserving transfers carry their parent witness.

Recursion over that data gives membership in P. The source witness is
executable data, not an existence claim obtained by classical choice.
An enabled Boolean guard proves the source is not syntactically false.
This supplies AssignedCover from a complete constructor history.

Checked anchors: fold_truth_equivalent, fold_uses_subset,
fold_weight_nonincrease, witness_assigned, enabled_guard_is_live,
absent_mark_cannot_be_true and folded_guard_from_witnesses.

The runtime adapter must cover all real GEN/import/rename cases, including
static/entry/exit sources and any legacy END/native behavior. An unknown
case is protected or disables reduction. Availability is a conservative
compiler fact; it does not make an otherwise legal mark invalid.

## 8. Unchecked GEN removal preserves checked outputs

Action values keep their original action ID, typed target, full key and
payload. A GEN action is retained exactly when its full key is in C.
Every non-GEN action remains in the same order.

Filtering the list once for C and then observing C gives the same list
as observing the original list for C. This follows by action constructor
cases: retained GEN remains, unobserved GEN contributes no observed row,
and other actions are unchanged. The result includes duplicate values,
positions and original IDs; it is not just mark-set equality.

For one rule, guard folding preserves truth under AssignedCover. Apply
the action-list law when the guard is true; both sides emit nothing when
false. Append the per-rule lists in their original order. This proves the
per-round checked-output law.

Checked anchors: checked_action_outputs_order_ids,
rewrite_rule_checked_outputs, step_checked_outputs and
prune_action_slots_nonincrease.

A copy, cleaner or arbitrary callback which can observe more than declared
CM is not silently treated as a dead consumer. Known non-CM observations
are protected; an unclassified client uses protectAll. The removal changes
unused source/action/fact outputs explicitly, while preserving the checked
observation domain.

## 9. Iteration, safe fallback and metadata separation

Each round uses current P and C simultaneously. Folding introduces no CM
keys. Pruning introduces no GEN keys. The checked-use and assigned-key
sets are nonincreasing; expression weight is nonincreasing. Truth need not
be monotone because negative CM can become true.

The short-fuel executable normalizer computes a candidate and checks whether
another round changes it. A passing check gives a fixed point. A failing
check returns the exact original rules. It never accepts an unchecked
partial result.

The selected total normalizer has a stronger constructive result. Its
measure assigns weight two to CM, weight one to other leaves, and one plus
child weights to connectives. Add each ordered action-list length and sum
over rows. Structural induction proves that folding either keeps an
expression exactly or strictly lowers its weight. Filtering either keeps
an action list exactly or strictly lowers its length. Row and list
induction then prove every changed round strictly lowers programWeight.
There cannot be more changed rounds than its initial value. Initial
programWeight plus one rounds suffice for a fixed point. The executable
stopAtFixed loop stops earlier on equality and returns the same result.

Checked anchors in the termination module are fold_same_or_strict,
changed_step_decreases, sufficient_rounds_fixed, normalizeTotal_fixed,
normalizeTotal_idempotent, changed_rounds_bounded and equality_checks_bounded.
No classical least-fixed-point existence theorem is used: the function
computes the list and its structural bound.

This bound is for that explicit fold step. Catalog Boolean/NNF conversion
runs once before measuring. The unrestricted production simplifier is not
part of the measured loop: NOT(A AND B) can expand to two negated leaves.
Equal-weight canonical reordering also needs separation from changed
rounds. The production adapter must implement the same-or-strict fold,
or use the typed model step; this audit does not prove an arbitrary Kotlin
simplifier has that property. Existing bound-row simplification remains
outside the global catalog loop.

At a nonopaque fixed point, every remaining CM has a producer/protected
key, and every remaining GEN has a checked/protected key. A self-supporting
cycle can remain; the algorithm does not assume global reachability.
A four-round cascade and an insufficient-fuel fallback are executable
controls.

Protected observations compose across rounds under RoundCover. The
normalizer theorem explicitly requires a valid assigned cover at each
round for the compared input interpretation. This is not a theorem that
arbitrary external input marks disappear. The producer-witness adapter,
protection policy and irrelevant-mark projection establish its valid scope.
Do not apply it to an input which lacks that cover.

The Bundle model updates execution only. Its metadata parameter and
complete declarations are definitionally unchanged. Item IDs and original
position metadata remain unchanged across every iteration. The test with
an unused GEN at another target retains that target in metadata.

Checked anchors: step_assigned_subset, step_checked_subset,
normalize_fixed_or_original, fixed_cm_has_producer, fixed_gen_is_checked,
normalize_protected_observations, normalize_row_identities,
metadata_parameter_untouched and no_new_mark_eligibility. The total module
carries those properties to normalizeTotal, including complete metadata
and declaration identity.

## 10. Scoped cache and unsafe compatibility proofs

The cache key includes owner, view, catalog, generation, snapshot, role,
mode, END policy and method/field subject. Exact key lookup cannot return
a row for another view or mode. A sound entry records the pure query's
complete result; induction over the lookup list proves a hit equals that
result. Remembering a correct current entry preserves soundness.

The handoff requires joined old jobs, zero query readers and no live old
plans/resume artifacts. It advances the generation and empties obsolete
scoped entries. Old keys cannot hit or publish into the new cache. A
current analysis cannot silently reselect. Changing phase to Prescan does
not silently clear selection. There is deliberately no prescan-OK bit in
the generic handoff; each caller preserves its old status policy.

Unsafe raw compatibility rows are separate. They preserve original broad
results and the fallback counter. unsafeSink implements a real cache hit
and a miss that consumes the next ID. Handoff commutes with that query:
its public result is equal, and its next state is the corresponding handed-
off state. It holds for cached and previously unseen signatures.

The naive-clear control returns 43 instead of the cached 42. The supported
handoff keeps 42, and both old/new paths assign 43 to a fresh signature.
Catalog/body keys still prevent stale raw compatibility hits after an
actual configuration change; preserve the old reload/counter policy.

Checked anchors: lookup_sound, remember_sound, readScoped_sound,
old_scoped_key_cannot_hit, old_query_cannot_publish,
handoff_waits_for_readers, handoff_waits_for_artifacts,
no_mid_analysis_reselection and unsafe_query_handoff_commutes.
repeated_snapshot_is_identity additionally proves that publishing the same
complete immutable policy is a no-op, even while its readers remain active.

The pure oracle is cache bookkeeping, not a proof of binder purity. The
binding module supplies equality for stable queries. The source workflow
must establish real reader joins, owner-qualified views, correct cache
migration and preservation of unsafe Go rows/counter.

## 11. Finite replay and later-run guidance

The interactive replay model chooses its next query from the evolving
engine state. It does not assume an old fixed query tape. For every local
query, require the same complete returned rows and related logical provider
state. Private cache contents may differ.

Induct on finite fuel. At zero, engine states agree. With no next query,
both stop. Otherwise, equal engine states choose the same query. The local
law gives equal rows and related provider states. The same engine update
therefore yields equal next engine states, and induction applies.

This yields interactive_replay_equal. Applying the unchanged handoff
function to equal final engine states gives guidance_equal. Complete mark,
field, layer, action and occurrence data are part of the rows/state; the
proof does not replace them with endpoint counts.

For B to C, the binding, normalization and cache laws supply these local
premises, subject to their adapter gates. Source/sink/ND/field/static
processing is common to both. Thus the optimized provider cannot lose a
summary or marked run-2 guide relative to the corrected late provider in
this finite supported scope.

For A to B, the relevant comparison is protected concrete witnesses.
An absent CM cannot be a true positive literal under producer coverage;
its negation is true on that domain. A GEN not used by any retained CM or
protected consumer is not a required marked witness input. Required resets
remain in the frozen supported metadata. Replaying the unchanged transfer
steps preserves the required witness paths and their marked guidance.
Source census, expanded spatial reset coverage, mode projection and actual
handoff adapters must discharge those premises before runtime acceptance.
This does not claim equality of all old abstract candidates or internal rows.

## 12. Counted work, audit and runtime obligations

Both binding loops carry operational counters. Induction identifies each
counter with the sum of per-item binding work. The late sum partitions
into retained and discarded terms. Nonnegative discarded work proves
nonincrease; one discarded item with positive contexts proves strict
reduction. The executable 20-by-20 context enumerator witnesses 400 to 0.
GEN pruning also gives a nonincreasing action-list length.

Scoped cache retirement proves zero old scoped entries and unchanged
compatibility entry count. It does not claim all caches are gone, a heap
byte count, no GC, or a 60-second bound.

Run from spec/lean:

```bash
lake build ApSpec
lake env lean semgrep-rule-selection-audit.lean
```

The [check receipt](lean/semgrep-rule-selection-checks.json) records the
final hashes, commands, declaration counts, executable controls and source
review. Failed intermediate attempts are not passing checks.

The final integrated build passes 181 jobs. The owned audit checks 2,381
declarations and 118 executable controls. It finds no unexpected axioms;
its permitted dependencies are propext and Quot.sound.

Before runtime implementation, retain the spec's differential controls.
In particular, test actual JVM/Go raw-to-bound mark identity, CM Any variants,
reset positions after unused GEN removal, sink ID counters, phase joins and
marked run-2/static replay. The checked models do not waive any of those
implementation requirements or the current failed project gates.
