# Proofs for run-1 FULL cleanup and supply batching

Date: 23 October 2026. Status: **models checked; runtime adapters implemented**.
Contract: [complete specification](run1-full-return-cleanup-and-static-supply-batching.md).
Reference analyzer: `8ab1445b8167a4b2db73f97788653aa4e21a3933`.

## 1. Claim and proof boundary

The two selected optimizations preserve the supported completed run-1
closure. They preserve normal and DEMAND summary observations, full marks,
origins, source hits and logical sink/rule occurrences. They therefore
preserve the existing run-2 guidance and later supported witnesses.

This is a relative claim. It does not establish general soundness or
termination of the reference analyzer. It does not turn the existing
precision failures or project OOMs into passing results. The Kotlin
adapters now have source review and differential tests. Read the
[implementation validation](../docs/reports/bidirectional-full-work-batching-validation-20261023.md)
for their results and the remaining failed application gates. The models
do not provide a mechanized Kotlin refinement theorem.

The checked constructive modules are:

| Module | Executable model |
| --- | --- |
| [Run1TemporaryStaticSupplies](lean/ApSpec/Run1TemporaryStaticSupplies.lean) | Independent temporary list/map decoders, canonical service state and atomic effects, raw ordering boundary and counted commit |
| [Run1FullReturnCleanup](lean/ApSpec/Run1FullReturnCleanup.lean) | Serial versus bulk FULL cleanup, explicit spatial PART laws, FLOW requests, raw suffixes and atomic reply histories |
| [Run1WorkBatchingClosure](lean/ApSpec/Run1WorkBatchingClosure.lean) | Executable finite derivation transport in both directions, logical observations and handoff membership |

Finite observation membership is the comparison relation. Callback
packets, temporary allocation IDs and repeated diagnostic invocations are
not observation identities. Exact logical source/sink/action identities
remain part of their observations.
Give separate logical occurrences separate complete keys, including
their specialized rule value and binding identity. Equality of that key
set preserves occurrence multiplicity when projected to a report location.
It does not permit deduplication by top-level rule ID and span alone.

## 2. Temporary supply admission

Let L be the finite sequence of actually computed region/mark atoms.
Each atom has a complete immutable region and a FULL key. Let M be the
new map, and `decode(M)` its finite scalar relation.

**Exact decode.** Inserting an atom either creates its complete region
row or adds its full key to that row. No other row changes. Thus:

```text
x in decode(add(a,M)) iff x = a OR x in decode(M)
```

Induction over L gives `x in decode(merge(L)) iff x in L`. This proves
both no loss and no invented field/mark pair. The proof uses full-key
equality, so equal mark names under different rule owners remain separate.

**Well-formedness.** A map has unique complete region rows and unique
members in each row. Insertion preserves these properties. The empty map
has them, so every constructed map has them. Empty packets contribute no
atoms. This is not a field-only key or a union of independent field and
mark sets.

Checked anchors: `mem_decode_add`, `decoded_exact`, `no_invented_FULL`,
`no_lost_region_or_atom`, `merge_wellFormed` and `decoded_no_duplicates`.

## 3. Canonical service packet equivalence

Project the service at one `(field or EXACT class point, FULL mark)`.
The booleans `requested` and `known` define the three disjoint states:

```text
req       = requested AND NOT known
reach     = NOT requested AND known
processed = requested AND known
```

Pending point membership, writer presence and writer admission are
projected at the same complete cell. A local writer ID belongs to one
bounded scalar output field. Read activation is projected at each pair
`(read owner/id, complete overlap Pattern)`.

The source projection starts empty. A pending atom is requested and not
known. A writer's admitted atom belongs to that registered writer and is
requested. These are the existing unanswered/once-admission invariants;
they do not describe an answered archive. The map equivalence theorem is
stronger: it holds for every executable model state.

The model executes the reference transitions:

- Supply sets known, answers old pending memberships and clears them.
  It creates no request and no writer ask. A repeated supply does nothing.
- Request sets requested. A known value answers immediately. An unknown
  value adds pending membership. A fresh request asks each existing writer
  not already admitted for that atom.
- Writer registration asks for the already requested atom once.
- Read registration and write presence activate their complete overlap
  once in either arrival order.

**Idempotence.** Supplying the same atom twice gives the same state and
atomic observations as supplying it once. Known is already true after
the first supply. Read/write delivery also has an idempotent delivered bit.

**Commutation.** Distinct atom keys update distinct membership cells.
Equal keys use the idempotence law. Read cells update by idempotent
disjunction. Therefore any two canonical supply atoms commute.

**Finite packet theorem.** Two finite lists with the same membership have
the same effect when every action commutes and is idempotent. Move one
action across a finite list by the commutation law and absorb duplicates
by idempotence; induct over the list. This is a proof about executable
state transitions, not an assumption of equal final closures. Exact decode
then proves equality of list and merged-map commit results.

Keep the old earlier commit lanes in the same order. Apply the supply
theorem to their resulting state. Any later deterministic request, read,
writer or ordinary event sees equal state. Induction gives equality after
any finite later event trace. Ghost answer/effect sets in the formal model
record observations; they are not proposed answered-request runtime stores.

Checked anchors: `three_sets_disjoint`, `supply_idempotent`,
`fresh_supply_removes_pending`, `supply_never_asks_writer`,
`late_writer_gets_processed_request`, `late_read_and_late_write`,
`delivery_idempotent`, `deliveries_commute`, `packetization_independent`,
`packet_atomic_correspondence`, `merged_packet_commit_equal`,
`whole_commit_equal`, `finite_boundary_trace_equal` and
`query_demand_witness`. The direct packet theorem checks bulk mark-set
supply against scalar expansion; the commit theorem uses the actual
chronological packet fold, not an unexplained reversal.

**Spatial boundary.** The decoder keeps complete region geometry.
Canonical field answers use the same field-root descriptor. EXACT class
answers have the same empty-E EXACT point. The deterministic AP overlap
adapter still computes complete read overlaps; it is not replaced by a
field-only test. Class and field slots are distinct constructors.

For an unrestricted broad-class supply with nonempty E, first-hit reply
geometry can differ after regrouping. The exclusion-bearing counterexample
in the specification demonstrates this. The raw route remains the
unchanged ordered list. Thus its behavior is equal by identity, not by a
false generic commutation claim. The profile is selected before collecting
the batch and is preserved on replacement.

## 4. FULL spatial cleaner kernel

Fix the complete nonstatic cleaner geometry G. Decode each packed TAINT
leaf to its full mark, exact/ANY/MUST kind, concrete path and layer.
Eligible TAINT is DEMAND and has empty E. Scalar EXACT observations do
not carry an unused containing-tree anyTail; ANY/MUST leaves retain it.

The cleaner model's Nat mark is the complete runtime atom handle, not a
bare mark name. The existing FULL key-injectivity contract, checked by
`Run1MarkModeExperiment.full_keys_injective`, remains unchanged. Equal
names with different owners cannot share that handle or a selected mask
entry. No group-wildcard or member-eligibility rewrite is used here.

The finite kernel classifies the leaf relative to G:

| Case | Selected-mark image |
| --- | --- |
| Disjoint | Keep the same leaf |
| Inside | Remove the leaf |
| Ordinary PART | Keep the same leaf as its DEMAND complement |
| ANY/MUST at a BELOW point | Keep an EXACT leaf at that point |
| MUST immediately above a non-EXACT cleaner | Keep the DEMAND MUST complement; for BELOW also keep the EXACT cleaner-point exception |

The exact cases follow the reference `cleanPosAt`/`TaintClean.cut` cases.
Leaves strictly below an EXACT cleaner are unchanged. A new exact-point
exception is disjoint from BELOW, so applying BELOW again leaves it
unchanged. Other PART outputs are already DEMAND. No field exclusion is
added by the run-1 kernel.

Let K be this selected-mark spatial relation. Exhaustive constructor
proofs give `K composed with K = K`: every two-step image is already a
one-step image, and every one-step image is stable. In particular, an
EXACT cleaner at an ANY/MUST root keeps the required DEMAND complement.
It is not a whole-subtree kill.

The category proof must be lifted with concrete paths. Retained cells keep
their original paths; the only new location is the fixed cleaner point.
Do not identify two different ancestor or descendant paths merely because
they share a category. The implementation adapter must use the existing
trie path and the fixed geometry when decoding those cases.

Checked kernel anchors include `image_transitive`, `image_output_self`,
`exact_root_partial_demand_header`, `below_parent_must_keeps_both` and
`below_exact_is_stable`. The checked `Located` lift carries actual paths:
`located_old_or_fresh`, `located_transitive`, `located_output_self` and
`located_image_idempotent` prove their preservation and stable exact-point
creation. `located_serial_bulk_scalar` proves the packed path-aware
equivalence.

`eraseExactCarrier` removes only the unused containing-tree carrier of an
EXACT scalar leaf. The spatial kernel commutes with that erasure. The
checked `located_serial_with_normalization` law permits intermediate
packing/normalization that preserves those normalized scalar observations.
It does not assert raw tree-header equality. In particular, removing the
last ANY sibling can change an exact-only tree's unused anyTail. ANY/MUST
payload annotations themselves remain observable and are not erased.

## 5. Serial and bulk TAINT equivalence

For a mask M, each mark T independently selects K if T is in M, or the
identity relation otherwise. A cleanup never renames T or transfers it to
another original. For two masks A and B:

```text
clean(B, clean(A, X)) = clean(A union B, X)
```

Proof: fix an original typed leaf and mark T. If neither mask selects T,
both sides keep it. If exactly one selects T, both sides apply K once.
If both select T, both sides agree by K's idempotence. Union the resulting
typed leaves. This proves the packed result without multiplying an
independent path union and mark union.

Induction over raw steps gives left-to-right serial cleanup equal to
bulk cleanup by their distinct full mask. The same law proves repeated
marks idempotent and distinct marks commutative. It also proves an absent
mark causes no TAINT effect. It does not permit skipping other present
block marks when the invocation is packed.

Checked anchors: `rel_compose`, `apply_composition`, `serial_bulk`,
`bulk_idempotent`, `duplicate_marks`, `missing_mark_no_effect` and
`taint_union_distribution`.

## 6. FLOW requests and collector compatibility

For a nonstatic selected cleaner on the same base, the reference FLOW
root, tail, layer and field E do not change. Only mx grows. This branch
requests its selected mark regardless of spatial overlap or old mx.

Induction over the block therefore gives:

```text
final mx = old mx union all selected FULL marks
request set = all selected FULL marks
request image for each selected atom = the unchanged input root
```

The old intermediate mx headers differ from the bulk header. This is
permitted because point matching and pending image storage omit mx, and
there is no observer between eligible pure steps. Do not infer equality
of intermediate FLOW packets or omit atoms already excluded in mx.

The collector-fixed gate is essential. FULL projection is identity;
empty-E cover is identity; eligible TAINT already has DEMAND; static
origins are already at the required layer. Selected cleanup preserves
this domain. Thus intermediate policy/collector calls cannot alter a
later spatial classification. If any current input fails the gate, the
unchanged one-step path executes. This is a fallback, not earlier
normalization to a new field abstraction.

Finally apply the existing result dispatcher. A STATIC result returned by
a nonstatic cleaner's base mismatch is promoted and consumed at the first
step. It does not reach the next list. A nonempty disjoint block cannot
be replaced by unconditional pass-through. Reach keeps its existing
no-request result. Empty suffixes execute no cleaner.

Checked anchors: `flow_serial_bulk`, `flow_image_unchanged`,
`flow_request_membership`, `flow_request_set_preserved`,
`mismatched_flow_no_request`, `mismatch_dispatch_equal`,
`static_mismatch_promotes_once`, `reach_continues_unchanged` and the
eligibility fallback theorems.

## 7. Raw suffix and static reply preservation

An invocation enters at raw j. Let k be an old selected-mark occurrence
inside `[j,end)`. The old request stores the current image at k. Every
preceding eligible FLOW step changes only mx, so that image and complete
non-address headers equal the new request image at j. The old raw step k
and new active entry j remain different addresses, as specified.

For an answer T, the existing materializer emits only T, with its existing
canonical initial. Distinct-mark operations before k leave T unchanged.
At least one T cleaner remains in both suffixes. Repeated T
cleaners are idempotent by section 5. Therefore replay from j and replay
from k give the same block-exit scalar observations, including PART rows.
An entry in the middle excludes every mark that occurs only in the prefix.

The formal attempt witness contains raw prefix, intervening steps,
selected occurrence, suffix, complete context and immutable images.
Constructive list search provides an old raw occurrence for each selected
new atom. A selected leaf in an old or new merged image has an executable
attempt preimage. Applying the suffix theorem to that attempt transports
the result. No point-ID bijection is needed.

Apply this argument to each atomic attempt in a finite request history.
Different complete contexts remain distinct; equal contexts can merge
their per-mark images. For one addressed pending row,
detach answered atoms before callbacks and keep other memberships.
New images after detach belong to new pending attempts. The unchanged
per-mark runtime map still stores precisely the union of images that
requested that atom. The model history is proof data only.

Checked anchors: `entry_is_raw_suffix`, `old_step_is_raw_suffix`,
`scalar_resume`, `scalar_packet_resume`, `attempt_exit_equal`,
`request_image_exact`, `decoded_has_executable_preimage`,
`selected_preimage_sound`, `new_attempt_has_old_occurrence`,
`history_exit_equal`, `late_images_preserved` and `detach_no_second_answer`.
`new_requests_after_detach` checks that fresh callback-added images remain
answerable without reintroducing the old detached attempts.

## 8. Finite closure, joins and handoff

Cut the program execution at unchanged ordinary boundaries, pure-block
exits and atomic service deliveries. Local proofs above give equal
normalized observations at those boundaries. Source and sink conditions,
their complete origin joins and their actions remain outside the block.

The generic finite derivation has a seed constructor and a rule constructor
with a proof child for **every** required input. Its transport function
recursively preserves all those children and replaces only local output
membership by the proved equivalent membership. The inverse uses the
same construction in reverse. This yields executable proof witnesses;
there is no choice of an unproved representative origin.

The implementation must instantiate those atoms with complete typed rows
and logical occurrences. It must not equate callback counts with logical
occurrences. Source, sink, join, ordinary call and query adapters are the
same on both sides. Packing is legal only under their existing scalar
decode laws. Negative absence guards are not introduced by this model.

Checked anchors in `Run1WorkBatchingClosure`: `transport`, `transportBack`,
`finite_closures_equivalent` and `transportObservation`. The supply model
also constructs `query_demand_witness` from its executable equal service
state and the same downstream finite adapter rules.

At COMPLETE, take the retained summary and logical sink seed sets. They
are equal by the closure transport. The handoff is the unchanged finite
union of per-row normalizations. `handoff_membership` proves equal guide
membership. Static actual-input query completion is unchanged. The later
FULL rules therefore have the same supported finite derivations. This
preserves both the correct mark demand and its field/return path; FLOW
guidance alone is not substituted for a FULL mark demand.

## 9. Ownership, cancellation and cost

The static delta remains actor-owned until the same queue-empty boundary.
Synchronous commit keeps the Work reservation. Replacement preserves the
whole-batch profile. Effect sends must be secured before that reservation
can drain. A quantum yield neither commits nor releases the batch. The
counted-state model checks that release needs commit, replacement and
secured effects. It does not choose a new barrier or bound pending memory.

The cleanup compiler keeps raw indices and only linear-size metadata.
The serial fallback and later/reversed paths remain executable reference
paths. Kernel traversal and raw suffix-mask construction retain
cancellation checkpoints. Cancellation prefixes can differ; neither
adapter may report COMPLETE with uncommitted or unsecured work.

The supply cost model counts retained scalar memberships/region rows,
not JVM bytes. Duplicates can only reduce that count; repeated identical
offers have constant unique membership. The cleanup model removes repeated
applications of one spatial relation. It does not remove mask construction,
packing, source binding or per-origin materialization.

The 2.648x measurement bounds pending supply records only. The full-depth
cleaner profile is a scope that includes unqualified work. No proof here
claims a 2x whole-engine gain, a live-byte saving, or 60-second completion.

## 10. Mandatory implementation correspondence

| Adapter | Required evidence before acceptance |
| --- | --- |
| Full key and complete region | Existing interner/atom identity; owned path/tail/E keys; direct raw profile remains ordered |
| Supply map and service | Differential list/map commits with requests, late reads/writers, exact class points and the raw counterexample |
| Spatial decoder | Exhaustive source case review plus actual trie differential tests for exact/ANY/MUST and concrete paths; unused exact carrier is normalized |
| Bulk packed output | Decode both outputs to typed scalar rows; preserve marks, origin, E, tails/layers and partial exceptions |
| Collector/dispatcher | Gate/fallback checks, STATIC promotion once, no pass-through rewrite or moved field cut |
| Point owner | Request-attempt/image decode, suffix witness, late images and detach-before-callback; no added history |
| Rule observations | Same source/sink/action occurrence keys, every contributing origin and exact all-ZERO case |
| Handoff | Actual run-2 marked source replay and run-3 witness, not only abstract endpoint counts |
| Lifetime | Owning actor, counted commit/replacement/sends, cancellation and joined release |

These are production refinement obligations, not hidden assumptions about
completed implementations. The current source review is in
`validation/selected-run1-work-spec-20261023/source-review.md` under the
saved campaign. The selected spec lists the future TDD controls and fixed
project measurements. Actual analyzer implementation remains unchanged.

## 11. Checked commands and receipt

Run from `spec/lean`:

```bash
lake build ApSpec
lake env lean run1-full-work-batching-audit.lean
lake env lean run1-temporary-static-supplies-audit.lean
lake env lean run1-full-return-cleanup-audit.lean
```

The integrated build passes **174 jobs**. The combined audit checks
**1,610 owned declarations**: 877 supply, 653 cleanup and 80 closure
declarations. Its 71 executable controls pass. The supply standalone audit
adds 24 integration controls, for **95 distinct executable controls** in
total. The standalone cleanup audit also passes.

The owned-constant audits permit only `propext` and `Quot.sound`. They
reject Classical choice, sorry axioms and other unproved assumptions.
The finite witness transport functions are executable and axiom-free.
The [check receipt](lean/run1-full-work-batching-checks.json) pins the final
model, audit and specification hashes and separates checked claims from
the runtime adapter checks and the remaining project acceptance gates.
