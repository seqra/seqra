# Run-1 group static pool: proof dossier

Date: 18 October 2026. Status: **proposal; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Code names and defined
mathematical terms keep their stated meanings.

This dossier follows [proof-guide.md](proof-guide.md) and supports the
[static-pool proposal](run1-group-static-pool.md). It uses the checked
[rule-group mark laws](rule-group-mark-proofs.md). All new models are
constructive. They supply executable witnesses and finite counterexamples.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
No project run measures this design. No analyzer source was changed.

## 1. Three different claims

1. **Concept coverage:** the monotone pool must cover finite real static
   witnesses. It can add DEMAND candidates by forgetting order, caller and
   within-group mark distinctions.
2. **Storage equivalence:** merged/indexed rows must have the same FLOW and
   group observations as a scalar oracle for that same pooled concept.
3. **Refinement discovery:** every eligible full group member and every
   finite compatible writer/copy trace must remain queryable in run-2.

None of these claims is equality to the old flow-sensitive summary set.
An inventory opening is not a semantic writer-to-reader edge. A discovered
query is not a confirmed finding. Ordinary full-mark analysis and the
existing confirmation-certificate contract still apply.

## 2. Model scope

| Model | Executable domain | Checked boundary |
| --- | --- | --- |
| [Run1GroupStaticPool.lean](lean/ApSpec/Run1GroupStaticPool.lean) | Finite coordinates, known group offers, unknown FLOW offers, needs and reached readers | Scalar/merged/indexed transport; all arrival orders; local DEMAND delivery; ambient group selection; fresh delivery |
| [Run1GroupStaticTrace.lean](lean/ApSpec/Run1GroupStaticTrace.lean) | Ordered primitive writes, exact selected cleaners, whole kills, reached reads, needs and transparent relays | Concrete current contents remain covered by pooled history; executable read witness |
| [Run1GroupStaticHandoff.lean](lean/ApSpec/Run1GroupStaticHandoff.lean) | Closed full-mark catalog, writer inventory, primitive reversal and finite discovery paths | Complete group decoding; candidate writer search; immutable root obligation; retained writer/guard path |

Coordinates in the transport model are already normalized overlap cells.
The trace heap uses the same cells. This models an unchanged bounded AP
geometry, not arbitrary JVM heaps. The actual scope/GeometryIndex adapter
must implement the proposal's location-wise intersection oracle. Broad and
narrow keys cannot be compared only by raw ID. AP aliases and field-limit
projection remain ordinary primitive obligations.

Full IDs are absent from pool/local delivery keys. The trace model keeps
full marks only in its independent concrete reference. Query models use
full marks again in run-2. Original rule conditions, support and context
are retained as immutable IDs/data; their full runtime meaning is external.

## 3. Coverage of ordered static traces

The concrete heap maps a coordinate to its current full marks. A write
replaces that list. A selected cleaner removes exactly its full literal.
A whole kill replaces the list with empty. The pool never deletes history.

Each primitive write has one RHS envelope:

```text
Empty             covers only empty actual content
Known(groups)     covers every actual member by its owner group
FLOW              covers possible members without full identity
```

`project` computes the known-group envelope from a finite actual list.
`project_covers` proves its coverage. The FLOW alternative is a conservative
abstraction of unresolved content. `covered_offer` proves that each covered
actual member produces a group atom or unknown FLOW supply.

The central invariant is:

```text
m in currentConcreteHeap[c]
  => (c, owner(m)) in pool.known
     OR c in pool.unknownFlow
```

`step_invariant` proves this for each primitive event. For a write, use its
local RHS certificate. An overwrite can remove old concrete members but
cannot remove a new witness from pool history. A cleaner/whole kill only
shrinks concrete contents. Registration and need events do not change those
contents. `every_finite_trace_covered` lifts these cases to any finite trace
and therefore to each real read prefix.

This does not assume that the desired whole analysis is already covered.
The explicit external premise is local: the envelope for **one RHS
operation** covers that operation's possible content. The implementation
must establish this from its FLOW/value/action compiler, including control-
reached writes with no concrete taint callback. A missing callback is not
an executable proof of clean content.

`atReadWitness` registers the actual read and relevant owner-group need at
that prefix. It returns a local DEMAND delivery with an enabled proof.
No method-return summary, remote original or call-chain request is used.
The old NORMAL relation is not inferred from this witness.

The executable controls expose the intended extra candidates: read-before-
write and post-overwrite/post-kill pool reads can still receive old content.
The current concrete heap has the strong result. Only DEMAND sees history.
This distinction is necessary for the accepted run-1 approximation.

## 4. Group requests, FLOW and delivery

`process_event_coverage` proves an exact finite transport characterization:

```text
enabled(reader, g)
  iff reader registration occurred
      AND (known(reader.coordinate,g) occurred
           OR FLOW(reader.coordinate) occurred AND need(coordinate,g) occurred)
```

No order occurs in the right-hand side. `swap_arrivals` and
`event_membership_equal` give the same observations after reordering or
duplicate offers. `flow_enabled_spec` permits unknown FLOW to reach a
reader before any group is selected. This is needed for static-copy chains.

`scalarEventWitness` and `unknownEventWitness` construct the corresponding
DEMAND delivery. `no_supply_no_group`, `no_reader_no_delivery` and
`observe_never_normal` exclude request-only, unreachable and NORMAL results.
The group value means possibility; these theorems do not claim actual T.

`ambientWitness` applies the same rule at one reached local stage. A local
FLOW plus local interest selects a group without a remote original.
`ambient_need_alone_not_supply` rejects a need without a supplied value.
Ordinary singleton-input P/R requests are unchanged; they are not replaced
by this local ambient rule.

The model has no exclusion gate in this join. This matches the selected
complement rule: an excluded group must still be requested. The prior
mark proofs establish the NORMAL outside-group restriction and DEMAND
survivor repair. The pool path itself remains DEMAND.

An ambient state is one operation **input stage**. The model does not prove
that merging pre-cleaner and post-cleaner states is safe; it is unsafe.
The adapter must keep action/call/F74/F75 stages distinct and rerun transfer
from the input. RemoveAll kills local metadata and groups. No late need may
inject into the operation's output. A later shared-field read is a separate
operation and can conservatively see pool history.

## 5. Merged storage and fresh work

`scalarOffers` retains every offer in a finite oracle list. `process` merges
offers. `process_scalar_atoms` proves equal membership in supply, FLOW,
needs and reader sets. `process_scalar_equal` proves equal observations.
`indexed_scalar_equal` proves the same group lookup with per-coordinate rows.

`process_append_equal` and `batch_union_equal` permit batching. Duplicate
members and batch boundaries cannot alter the final candidate relation.
These proofs do not require a specific method-queue-empty scheduling order.
The actual runner must fairly commit all submitted batches and deliver all
growth before completion.

`deliverable_group_iff` enumerates the finite enabled groups.
`fresh_group_iff` computes the difference from answered local groups.
`fresh_after_commit_empty` proves that committing those groups first leaves
no repeat delivery. The answered argument is a mathematical view of local
accumulated state, not a required second per-reader history store.

Atomic commit-before-enqueue and atomic registration/snapshot selection are
implementation obligations. The model proves the logical join and freshness
contracts, not the Kotlin lock, coroutine or cancellation implementation.
Redundant broad-scope notifications must still meet the local join before
they enqueue semantic work.

## 6. Group decoding and finite writer discovery

`decodeStaticNeeds` uses the complete closed-catalog decoder from the mark
model. `every_full_group_member_gets_seed` preserves every eligible full
member. `decoded_static_seed_has_catalog_member` rejects an invented catalog
identity. `first_static_handoff_keeps_limit_one` checks F1/B2 limit 1.
An original full mark absent from F1 observations remains queryable.

The writer inventory contains code stores, copies, source rules, models,
aliases, broad effects and boundaries. `opened_iff` proves that each opening
retains the exact need and an overlapping inventory writer.
`open_writer_complete` preserves every compatible listed writer.
`observed_groups_do_not_gate_discovery` excludes F1-observed-group filtering.

`opening_keeps_full_root` preserves the original read query and context.
`predecessor_keeps_origin` preserves them through reversal. The current
query uses the writer's ordinary full AP/mark reversal. A mark rewriter can
change the current full mark; the root obligation must remain unchanged.

The reference describes individual writer/reversal steps. `LocalBridge`
requires only closed inventory, conservative spatial overlap and inclusion
of each ordinary primitive reverse result. `compilePath` computes a search
depth and a discovered target from a finite reference path.
`every_finite_reference_trace_discovered` proves that target is present.
This covers finite witnesses through recursive static copies. It does not
prove global termination, and its fuel is not a runtime or AP-limit cap.

`compileGraphPath` returns the **whole** candidate path.
`compiled_graph_keeps_all_writers` and `compiled_graph_keeps_all_guards`
retain intermediate conditions. This closes a design gap: a flat final
writer plus original read loses guards on intermediate copies/sources.
Query deduplication must union parent/reversal edges separately.

The model's `Validation` record lists external ordinary checks. Its Boolean
conjunction is a boundary contract, not a JVM path validator. Its negative
controls show that lookup alone cannot provide source, order, strong-kill,
condition or support evidence. They do not certify the existing trace-
resolution or confirmation-certificate producer.

## 7. Handoff adapters that remain required

The new full-mark search does not use the unchanged runtime by assumption.
The following are explicit implementation requirements:

| Boundary | Required adapter |
| --- | --- |
| [HandOff.kt](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/HandOff.kt) | Read the compact static ledger as well as ordinary analyzer summaries. Start full queries at actual internal points, including nonreturning methods. |
| [FormsCache.kt](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormsCache.kt) | Preserve operation/call/cleaner stages. Method-exit backward starts alone do not implement point queries. |
| [StrongQueries.kt](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/StrongQueries.kt) | Add a typed query-only admission path. Current actual-input admission does not manufacture full marks from FLOW. |
| Reverse CFG/calls | Traverse ordinary predecessors, callee exits and compatible incoming caller ports without an equal F1 guide. Keep full key, stage and context. |
| Writer lookup | Cover all producer kinds and broad scopes. Reverse actual operations. Keep original read and every dependency edge. Add no semantic static jump. |
| [Seeds.kt](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/Seeds.kt) | Preserve exact `(MethodKey, statement, forward PathEdge)` source permissions, full marks and predicate occurrences. |
| Source/publication gates | Authorize the session's whole reverse closure and later ordinary forward closure. A source permission alone does not remove every missing-guide gate. |
| Full-mark forward analysis | Start from actual configured roots and permitted sources. Apply real order, calls, returns, overwrites, cleaners and conditions. No direct marked writer/read injection. |
| Confirmation | Supply the existing certificate from a valid ordinary full-mark witness. An F3 sink event alone is not confirmation. |

For a safety fallback, retain unpruned full-mark analysis in the relevant
root-reachable scope when exact guide authorization is unavailable. The
session still cannot invent source permissions or convert speculative
inventory arcs to semantic edges. This fallback can increase run-2/later
work. The target of this proposal is first to remove run-1 relay state.

The ordinary finite-path lifting framework in the prior
[mark dossier](rule-group-mark-proofs.md) can compose local seed/step
adapters once they exist. It is not a proof that these new runtime adapters
already meet their contracts.

## 8. Scoped cost law

`transparent_chain_saving` compares per-field/group relay carriers in a
positive transparent chain. The expanded reference counts h*g carriers;
the pool counts zero carriers at those relay methods. `execute_relays`
shows that trace relay events do not change shared state.

The single universal default summary per reached method is outside this
per-field carrier count. So are consumer delivery, productive local/call/
return work, ordinary analysis, inventories, candidates and backward search.
This is not a lower bound on every optimized old implementation.

`full_marks_same_group_same_atom` and `repeated_offer_one_atom` show the
shared atom for same-group full marks. The prior homogeneous cohort theorem
proves the strict m-to-1 mark-slot saving for m > 1. The implementation must
keep groups through local work; expanding full marks at each read loses it.

These cost laws do not prove a byte reduction, a project speedup or a
60-second bound. Coarse FLOW/group candidates and broad B2 writer search
can add work. Measure the complete run, not only pool/request row counts.

## 9. Reproduction and acceptance

Use Lean `leanprover/lean4:v4.33.1` from `spec/lean`:

```sh
lake env lean --version
lake build
lake env lean run1-group-static-pool-audit.lean
```

The focused audit checks every declaration owned by the three new modules,
including generated helpers and instances. It rejects all axioms except
standard `propext` and `Quot.sound`, then executes the named controls. The
receipt records source hashes, tool version, exit codes and log hashes.

Before implementing runtime changes, the required controls include:

```text
pool:  all supply/need/registration orders; duplicate commits; no supply
scope: broad FLOW plus narrow need both ways; disjoint classes/fields
local: distinct cleaner/call stages; late input; RemoveAll; no repeat enqueue
flow:  abstract RHS; static copies/cycles; no full-mark expansion
query: internal nonreturning check; incoming caller; no relay guide
guard: conditional source/copy; intermediate guards; whole ND obligation
order: writer before/after read; overwritten value; mismatched caller
seed:  full U absent in F1; exact source edge; no actual source from lookup
```

The finite models cover their stated subsets. Runtime differential tests
must implement the remaining AP, stage, prescan, queue and handoff adapters.
No project benchmark or analyzer implementation is part of this proof work.

Final check on 18 October 2026: **PASS**. Lean 4.33.1 builds all **145 jobs**.
The focused audit checks **1,015 declarations**, finds **zero unexpected
axioms**, and executes **59 passing controls**. The only permitted axioms
are standard `propext` and `Quot.sound`; classical choice is not admitted.

| Module | Audited declarations | Passing controls |
| --- | ---: | ---: |
| Run1GroupStaticPool | 409 | 25 |
| Run1GroupStaticTrace | 161 | 11 |
| Run1GroupStaticHandoff | 445 | 23 |
| Total | 1,015 | 59 |

The [validation receipt](/drive-testcomp/opentaint-go-rules/run1-group-static-pool-20261018/validation/20261018T192253.053957Z/receipt.json)
has SHA-256 `6dd57f63c8924d86d9b3861c68b5daf151e27bea7a75c9b427d0ab76a35f2615`.
It binds stable proof-source hashes to the
[build log](/drive-testcomp/opentaint-go-rules/run1-group-static-pool-20261018/validation/20261018T192253.053957Z/build.log)
and [audit log](/drive-testcomp/opentaint-go-rules/run1-group-static-pool-20261018/validation/20261018T192253.053957Z/audit.log).
Runtime tests and project measurements were not rerun. The latest measured
run status remains the one at the start of this dossier.
