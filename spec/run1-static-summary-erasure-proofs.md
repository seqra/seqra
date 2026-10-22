# Proofs for run-1 static summary erasure

Date: 22 October 2026. Status: **constructive design models; runtime unchanged**.
Read [the specification](run1-static-summary-erasure.md). The reference
runtime is `30c1b84606695070a79d7ae467703267b9f931c1`.
Follow [proof-guide.md](proof-guide.md).

## 1. Claim, scope and proof structure

There are two different claims. Do not merge them:

1. Run-1 producer, supply and retained consumer observations survive
   removal of static/static method rows. The rows themselves do not survive.
2. The new actual-input query policy covers each removed guide's run-2
   start. With the unchanged FULL kernel and retained seeds, each finite
   reference run-2 witness has a new witness of the same identity.

The proof is relative to the selected bounded JVM static grammar. It does
not assume that pool membership is sufficient to prove backward guidance.
It does not assume equal final pools, all old summaries, or a complete
new demand frontier. Construct those witnesses from producers and actual
inputs instead.

The models use finite derivations. Recursive calls are allowed; any
particular justified result has a finite derivation. This is not a proof
of whole-engine termination, resource bounds, or reference soundness.
The current project failures remain failures.

### Checked models

| Model | Constructive result | Deliberate boundary |
| --- | --- | --- |
| [Run1StaticSummaryErasure](lean/ApSpec/Run1StaticSummaryErasure.lean) | Recover local producers and ordinary input bindings; compress old relay derivations; preserve complete scalar supplies and retained observations | One shared scalar kernel; canonical-input and normalizer adapters required |
| [Run1StaticRelayProjection](lean/ApSpec/Run1StaticRelayProjection.lean) | Same-path concrete scalar reapplication preserves target region and EXACT/ANY/MUST; derive normalizer idempotence | Empty E; aligned canonical input; scalar extraction/graft/bounds need source correspondence |
| [Run1StaticQueryCompletion](lean/ApSpec/Run1StaticQueryCompletion.lean) | Exact old static-query identity occurs in at most two queries from a legal actual input | Selected actual/guide grammar; canonical empty-E query forms |
| [Run1StaticErasureBackwardReplay](lean/ApSpec/Run1StaticErasureBackwardReplay.lean) | Rebuild each finite FULL witness, source hit, seed and complete binding from those queries | Same ordinary FULL kernel and seed identities; Kotlin refinement remains required |

The first model contains no arbitrary old-summary root. Old summaries
are generated only by a local producer, a first ordinary binding, or a
static promotion. The new derivation type has no static-summary
application constructor. The query model calls existing AP `emitM` and
`emitW` operations. The replay model uses the query coverage theorem,
not an assumed completed answer relation.

## 2. P1: classify erasure without changing other relations

Let `erase(p,v)` be the predicate in spec section 4. It requires run-1,
a static result, and a nonzero premise whose members are all static.

By case analysis:

- ZERO fails the nonzero test.
- A nonstatic or mixed premise fails the all-static test.
- A nonstatic output fails the output test.
- Every later run fails the run test.

Thus none of these branches is selected for removal. In current run-1,
complete condition admission emits each selected singleton origin arm.
Apply the predicate to each arm. Do not replace this process with a new
ND origin representation or a one-member condition test.

`static_writer_storage_erased`, `nonstatic_writer_summary_is_retained`,
`local_registration_precedes_storage` and
`computed_supply_independent_of_storage` check the producer-batch split.
The complete predicate and all raw/selected/publication/record insertion
sites still need their Kotlin adapter controls. The simple producer-batch
model is not a proof that the runtime has no second storage path.

## 3. P2: pure relay cannot add a new scalar static effect

### P2a. Prove the local AP projection first

Let j be a concrete static original. Its actual canonical field input
is the same field/G at ANY and DEMAND. Its exact class input is the same
class/G at EXACT and DEMAND. Keep the exact-field fallback: its actual
field reply can also be ANY at that same field.

Let v be one bounded concrete output scalar. It has empty E, its complete
output path, a target group H, and its actual EXACT, ANY or MUST tag.
H need not equal the input group G.

The same-path concrete row has no residual input suffix. Its input mark
matches G. Mark composition chooses the declared H. It writes the
declared target path and tag. The summary layer and run-1 GROUP policy
make the output DEMAND. Empty E remains empty. Current surface
normalization does not convert MUST into ANY merely because its layer
is DEMAND. The field bound does not cut a second time below an already
bounded static field anchor.

The relay model checks these steps explicitly:

```text
belowAtZero -> summary-layer union -> surfaceNormalize
            -> run1Cover -> GROUP DEMAND policy
```

`reapply_closed_form` derives this result; it does not define the result
as the desired identity. `reapply_preserves_region` preserves base,
path, tail, E and output group. `reapply_preserves_target` also preserves
the DEMAND scalar. `normalizer_idempotent` proves repeated application
has no additional effect. `field_STAR_reapplication`,
`field_EXACT_reapplication` and `class_EXACT_reapplication` connect the
canonical shapes to existing `applySummaryX` semantics.

The historical AP model encodes both ANY and MUST as `Kind.any` and uses
the layer for the older distinction. Current Kotlin stores the tail tag
independently. Therefore `reapply_erases_to_actual_AP` alone would be too
weak. The explicit surface rows prove tag preservation, and
`erasure_does_not_prove_tag_equality` is a checked negative control.

For a packed output, use its existing scalar leaf decomposition. Retain
each scalar's own path, tag and group. Do not take a product of all paths
with all groups, infer arbitrary suffix additivity, or reorder aliases.
Connect the leaf/graft and bound adapters before using this scalar lemma
on a production tree.

The service denotation includes reached write regions and overlap-driven
read activation, not just `(field,G)` membership. Since the complete
scalar region is preserved, repeated relay admission cannot add a new
region/group effect. Preserve `admitWrite` before the existing group
dedup. A nonempty-E FULL scalar is outside this proof, not a reason to
change later-run exclusion behavior.

### P2b. Trace a relay to a real producer

Induct on the old summary derivation:

```text
local(w)                 -> real local immutable writer/kernel
firstBinding(b,callee)   -> same callee producer, ordinary actual/formal binding b
staticRelay(callee,call) -> same producer and input; no new producer
```

In current Kotlin, the static-output call branch goes directly to
promotion. It does not execute a caller suffix. A static-to-nonstatic
summary is different and remains published.

`localWriterWitness` extracts the local producer.
`inputBindings` extracts its exact ordinary call bindings.
`replayInput` executes those bindings in their proper direction.
`requestBindings` gives their reverse request path, and
`request_then_actual_replay` checks the request/answer order.
Pure wrappers add no ordinary input binding.

`oldRowOutputs` applies the scalar normalizer at every old promotion.
`localOutputExists` proves that each such output has a local scalar
preimage using idempotence. `localOutputPreimage` finds that preimage by
searching the finite local result list. It returns an executable subtype
witness. It uses no choice operation or assumed raw-summary equality.

For FLOW relay rows, there is no computed GROUP supply merely from the
row. The real local FLOW writer already owns the immutable relation and
answers future interests. Deleting a promoted FLOW row must not delete
that writer or its ordinary nonstatic request bootstrap.

## 4. P3: preserve producers and first actual caller bindings

Induct on a finite old event derivation. Keep roots, ordinary bindings,
local writers and ordinary complete rules. In the erased summary case:

1. Recursively preserve its actual input witness.
2. Extract the local producer and the ordinary binding path (P2b).
3. Replay that input path with its exact original/port identity.
4. Find the local output preimage.
5. Execute the same local scalar kernel and normalization once.

This constructs the same complete supplied atom. It is the function
`compress`. The inverse `embed` maps a new proof into the old procedure
without adding relays. `complete_operational_closure` proves equality of
the modeled positive effect closures. It does not prove equality of
their stored static summaries or callback histories.

The first binding case is necessary:

```java
void set(Object p) { C.out = p; }
void caller() { set(C.in); }
```

The local arg writer and ordinary caller subscription remain. They carry
an actual caller input to the callee's formal. Their GROUP result can be
the first `C.out/G` supply. Preserve that result's effect before erasing
the caller's static/static row. The proof does not start by assuming
`C.out/G` is already in the pool. No caller writer is registered.

The operational model's input key is a canonical static slot/group or
an exact ordinary port. Complete transfer geometry stays in the immutable
writer descriptor and scalar payload. The Kotlin adapter must establish
that its actual binding uses that canonical value and complete selector.
An arbitrary raw static AP cannot be replaced by just `(field,G)`.

The existing conditional source kernel is shared, including its complete
guard binding. `ordinaryRule` requires a proof for every input. This is
not permission to make a new grounding condition, change field paths, or
turn a guarded GEN into an unconditional source. Current admitted
zero-arity GEN remains an allowed root.

## 5. P4: pending replies, order, consumers and recursion

### Storage-independent delayed reply

The existing FLOW writer already reconstructs its complete scalar from
its immutable descriptor. It does not need a raw summary borrow.
The fixed-GROUP fallback does borrow one. For this fallback save the
complete bounded scalar as the unanswered join payload. On the same
reply, call the same reducer, then delete the join.

The previous spec's empty-E scalar-persistence argument proves that the
old borrow would still select this admitted scalar. This justifies the
payload substitution. It does not justify retaining every answered output
or erasing selectors. No condition, original, layer or tail is changed.

### Request/write arrival order

The service transitions stay the same. If the request arrives first, req
and the pending consumer retain it; a later group write moves it to
processed and answers it. If the write arrives first, reach retains it;
the first request moves it to processed and answers it. New FLOW writers
receive req plus processed. An already answered consumer need not remain
stored. Exact class requests use their separate exact-point state.

The new effects occur before storage suppression, so all these old service
transitions are still enabled. `root_arrival_order_equivalent`,
`late_root_does_not_remove_supply` and `duplicate_root_is_idempotent`
check positive closure order/idempotence in the operational model.
They are not a lock/queue implementation proof. Existing once-admission
and counted-delivery adapters remain required.

### Retained consumers and seeds

`nonstatic_and_sink_observations_preserved` applies compression to the
unchanged ordinary kernel. The observation keeps method, original,
output, group/layer, sink statement, rule and occurrence identity.
Thus a getter, returning wrapper and dead-local sink can use the same
finite consumer derivation without a static/static method row. No
normal-return summary is needed for a sink which precedes an infinite
loop. Retain its sink witness and ordinary ZERO control path.

### Recursive cycles

`newRootWitness` traces a positive derivation to an admitted root or an
existing zero-input compiled rule. `no_source_free_cycle` and
`old_no_source_free_cycle` show that pure writer/call cycles cannot invent
their first group. This does not ban an existing source/GEN event and
does not prove the scheduler terminates.

## 6. P5: exact run-2 query coverage

### Domain

The selected actual concrete domain is:

```text
FieldExact(C,f,T), FieldAny(C,f,T), ClassExact(C,T)
```

Old run-1 concrete guide entries are named-field EXACT/ANY after existing
handoff normalization, exact class points, or the shared root GROUP
guide. FLOW guide entries cannot create concrete starts. All concrete
intersections copy the actual FULL identifier after the owner check.

`LegalActual` defines this domain. `legal_limit_one` proves its field
budget; the converse is false. `raw_limit_one_does_not_exclude_broad_values`
checks that a broad marked root/class also fits the numeric budget.
Producer grammar closure is a separate required adapter proof.

### Construct the replacement queries

```text
complete(FieldExact(C,f,T)) = [FieldExact(C,f,T)]
complete(FieldAny(C,f,T))   = [FieldAny(C,f,T), FieldExact(C,f,T)]
complete(ClassExact(C,T))   = [ClassExact(C,T)]
```

`actual_query_preserved` retains the actual query.
`exact_completion_is_AP_intersection` and
`exact_completion_is_emitW` prove that the extra exact start is a real
existing AP intersection. `completion_keeps_full_mark`,
`completion_keeps_named_path`, `completion_keeps_static_base` and
`completion_adds_no_locations` prove that it invents no field, base,
mark or marked location. The initial identity can differ even when the
denoted locations were already included; that distinction is the reason
to add the EXACT start explicitly.

For each old guide, do a constructive finite case split:

| Actual / old guide | Old additional query | Covered new member |
| --- | --- | --- |
| Field EXACT / same field EXACT or ANY, matching owner | Same field EXACT/T | Actual |
| Field ANY / same field ANY, matching owner | Same field ANY/T | Actual |
| Field ANY / same field EXACT, matching owner | Same field EXACT/T | Extra EXACT |
| Exact class / same exact class, matching owner | Same class EXACT/T | Actual |
| Legal actual / shared root GROUP, matching owner | Same actual geometry/T | Actual |
| Distinct field/class, wrong owner, field vs exact class | No query | No witness required |
| FLOW or excluded-FLOW actual | No FULL query | Ordinary FLOW path only |

`old_extra_query_in_completion` proves this case split with AP emit.
`old_query_batch_covered` lifts it to every finite list of old guides.
`coveredQuery` returns the same query and a proof that it occurs in the
new batch. It requires no retained old-guide lookup in the new procedure.

`old_intersection_keeps_actual_mark` and
`same_owner_different_fulls_stay_distinct` prevent a same-group shortcut
from merging two distinct FULL queries.

### Checked counterexamples and the user decision

`actual_only_misses_exact_field` proves that actual-only admission can
miss an old exact initial. Root/class broad marked values have descendant
field intersections that same-path completion cannot reproduce:

```text
old emit(C.f/EXACT/G, S/ANY/T)   = C.f/EXACT/T
old emit(C.f/EXACT/G, S.C/ANY/T) = C.f/EXACT/T
```

`broad_root_needs_a_descendant_query` and
`broad_class_needs_a_descendant_query` check these failures.
`broad_root_rejected` and `broad_class_rejected` prove that the selected
actual domain excludes them. This follows the user's explicit decision
to pin named fields and EXACT class markers in run-2 too. It is not an
unproved claim that the generic AP API cannot construct those values.

## 7. P6: current authorization preserves full originals

Install the whole deduplicated query batch before executing its fresh
starts. Each old required start has exactly the same canonical initial
in the new admitted set by P5. Interned identity must preserve all query
coordinates and the receiving AP manager.

For a nonzero premise, publication requires every member in the admitted
set. Apply P5 to each affected static member and keep the other members'
ordinary guidance. Therefore each formerly permitted complete premise
remains permitted. Do not infer permission from one ND member.

Current `published` returns the computed facts for an admitted original
without the old paired-exit restriction. Therefore removing an old
static/static pair does not require storing its old exit: its start is
reconstructed, and its ordinary computed results are already authorized.
Retained nonstatic/mixed/ZERO guides keep their existing intersections
and exit behavior elsewhere. The proof does not globally delete exit
filters or force unrelated FULL results into a different group.

For ZERO, current permission requires a nonempty admitted set. This
authorizes a computed ZERO source result only. It does not fabricate that
result, start ZERO from nothing, or let ZERO match C.f in run-2.

The replay model represents all required query and FULL inputs as
complete rule premises at the exact port. Its ND controls require both
members and reject a member from another invocation. The runtime
`StrongQueries.permits` and saved call-stage adapters must retain that
same complete condition.

## 8. P7: rebuild each finite FULL witness

Use one ordinary FULL kernel on both sides. It contains the same AP
transfers, source rules, strong updates, cleaners, native/summary calls,
aliases and complete joins. The only changed inference is static query
admission. The model records exact method, statement, stage, invocation,
originals, current full fact, layer and ordinary annotations.

Induct on a finite reference witness:

1. **Sink seed:** reuse the same retained seed/occurrence (P4).
2. **Seed input:** reuse its identical FULL atom.
3. **Actual arrival:** reuse the atom at the same port and its selected
   canonical static grammar. An actual input is not assumed present in
   all methods or all call stages.
4. **Query admission:** use P5 to construct the equal query member of
   `complete(actual)`; preserve the actual arrival witness.
5. **Ordinary operation/call/join:** recursively rebuild every complete
   premise, then execute the same rule with the same identities.
6. **Source hit:** the same source action and sink occurrence are
   unchanged ordinary observations, not guessed from pool membership.

`Run1StaticErasureBackwardReplay.replay` is this executable construction.
`finite_full_witness_replayed`, `source_hit_identity_preserved`,
`retained_sink_seed_preserved`, `actual_arrival_keeps_exact_port` and
`complete_binding_preserved` state the consequences.
`no_required_old_guide_in_new_queries` checks that the new admission
definition does not consult the removed guide list. `replay_size_equal`
compares this one witness tree, not total scheduler work.

The kernel is a shared set of program transitions with complete premises,
not the old solver's completed facts inserted as new roots. For a Kotlin
refinement, each actual runtime step must map to that same kernel on both
sides. Negative checks or strong updates remain inside the same AP/rule
operation; they are not new monotone pool rules. This is a finite local
replay theorem, not an automatic theorem for arbitrary nonmonotone
stores, custom callbacks, or resource-limited schedules.

Compose P3/P4, P5/P6 and this induction. Every removed static guide start
used in a supported old run-2 derivation is reconstructed. The same FULL
derivation then reaches the same source hit and observation. Run-2 may
build and use real static/static summaries. They are not erased. At
run-2 completion, preserve the existing source-hit/seed and later handoff
construction; the run-1 policy is not carried into run-3 automatically.

## 9. P8: lifecycle, records and continuation

P1 establishes which rows are absent; P3/P4 establish which effects still
need delivery. Keep the corresponding jobs counted even if a method
retains no summary. A service send cannot be dropped merely because the
method queue and publication batch are empty. Required admitted work must
drain before COMPLETE and before run-1-only state is cleared.

Keep the existing publication barrier for retained summaries, cancellation
checks, actor ownership and service-lock boundaries. These are production
simulation obligations, not consequences of positive closure equality.

The stock policies still enter run-2:

- STOP_RULE uses the retained report.
- NO_DEMAND_EDGE excludes run-1.
- The increasing-limit policy continues as before.
- The fixed-limit policy uses the run index.
- The run-2 backward run does not use forward stop checks.

No new fake explicit demand row or special stop flag is required.
An arbitrary custom policy can inspect raw result counts and can make a
different choice when those counts change. The relative continuation
claim covers stock/count-independent policies. Do not claim equality for
all custom count-sensitive policies or call their consumed counts merely
diagnostic.

Current run-1 static results are DEMAND and already do not become native
normal records. Still enforce erasure structurally at every persistence
path. Later FULL static/static records stay valid and unchanged.

## 10. P9: work claims

`wrappers_add_old_summary_hops` proves that h wrappers add h old summary
hops. `wrappers_add_no_input_replay_bindings` proves that these wrappers
add no real ordinary input binding to the producer replay. Each erased
row has zero raw/selected/publication/handoff storage in the new design.
For k such relayed rows the removed component is h*k hops/rows, subject
to the reference's ordinary deduplication.

`completion_at_most_two` bounds starts offered per distinct legal actual
field FULL input; an exact marker offers one. The admitted interner/set
deduplicates identity. The symbolic boundary policy contains no stored
method/field/group combinations.

These are component bounds. They are not a global run-1 or run-2 bound.
Extra exact starts may execute additional FULL work. The latest baseline
reached Thingsboard run-2 OOM, with latest COMPLETE/accepted run-1;
Conductor and PDF have the same iteration status. OWASP reached run-5
TIMEOUT, latest COMPLETE run-4, latest accepted run-3. No new project
measurement or performance acceptance is claimed here.

## 11. Runtime refinement gates

These obligations must be proved by source correspondence and focused
production controls during implementation. They are not discharged by
the abstract models alone:

1. **Constructor closure:** actual source/GEN, alias, static service,
   call binding, reversed/native record, field-cut and seed constructors
   conserve named fields or exact class markers at the run-2 boundary.
   Check broad providers explicitly. Do not cast invalid values away.
2. **Complete scalar projection:** extraction/graft, receiving manager,
   empty E, canonical field/class input, exact-field fallback, output
   limit and run-1 cover match P2. Keep cross-group and MUST cases.
3. **Producer conservation:** moving effects before erasure preserves
   reached operation discovery, current GEN eligibility and first
   ordinary input bindings. Keep local-only writer registration.
4. **Pending scalar ownership:** no required delayed reply reads an
   erased summary; it owns the same unanswered payload and removes it
   after completion.
5. **Service/queue simulation:** exact overlap, req/reach/processed,
   late writers, class interests, once-admission and work accounting
   implement the same positive event semantics.
6. **Query/store simulation:** canonical initial identity, whole-batch
   admission, all-member permission, raw-summary backfill, selected
   publication and actual backward inputs map to the checked model.
7. **Observation/handoff simulation:** complete rule/source/sink
   occurrences, ZERO exits, retained mixed pairs, report frontier,
   records and later source-hit seeds keep their current meaning.
8. **Storage inventory:** prove the absence of erased rows in every
   raw/selected/publication/handoff/native owner; no replacement archive.

The reference source seams are listed in spec section 12. The source
review establishes why each adapter is needed; no runtime patch or JVM
test is included in this documentation task. A newly required semantic
change must be exposed under the proof guide, not introduced to make an
adapter proof pass.

## 12. Reproduction and audit

Use Lean 4.33.1 from `spec/lean/lean-toolchain`. The selected model check
and [owned-declaration audit](lean/run1-static-summary-erasure-audit.lean)
must pass. The audit rejects every axiom dependency except the standard
`propext` and `Quot.sound`; there is no `Classical.choice`, `sorryAx`,
native decision axiom or noncomputable witness search.

Run these commands from `spec/lean`:

```sh
lake build ApSpec.Run1StaticSummaryErasure ApSpec.Run1StaticRelayProjection ApSpec.Run1StaticQueryCompletion ApSpec.Run1StaticErasureBackwardReplay
lake env lean run1-static-summary-erasure-audit.lean
```

Both final commands pass. The focused build completes 53 jobs. Existing
dependency lint warnings do not fail that build. The owned audit gives:

| Owned module | Declarations | Executable controls |
| --- | ---: | ---: |
| Run1StaticSummaryErasure | 848 | 32 |
| Run1StaticRelayProjection | 202 | 13 |
| Run1StaticQueryCompletion | 263 | 8 |
| Run1StaticErasureBackwardReplay | 509 | 22 |
| Total | 1,822 | 75 |

All 75 controls pass. Every owned declaration passes the axiom allowlist.
The [machine-readable receipt](lean/run1-static-summary-erasure-checks.json)
binds final source hashes, commands, counts, and audit output. The combined
audit helper needed explicit type annotations during development; the
corrected final helper passes. No feature-model source was changed to
hide an audit failure.

The controls are local formal-model controls. They are not Kotlin or
project acceptance tests. No analyzer implementation or new performance
result is included in this check.
