# Proofs for batched static-need replay

Date: 17 October 2026. Status: **design proofs and finite formal model;
runtime adapters implemented and tested**. Contract:
[run1-static-need-replay-batching.md](run1-static-need-replay-batching.md).
This dossier follows [proof-guide.md](proof-guide.md). It distinguishes the
ordinary request closure, the scheduling optimization and their implementation.

The [runtime validation](../docs/reports/bidirectional-static-need-replay-batching-validation-20261017.md)
records the implementation checks. They do not convert the finite model into
a proof of every Kotlin coroutine schedule. Fresh project measurements are
pending; latest reached/completed/accepted iterations for this implementation
are unavailable on all four projects. Historical observations on runtime
`6b9291d7dd69ae5f6a1260c7f1021c7500fa56b8`:
OWASP run-8 / run-7 / run-7; conductor run-1 / none / none;
ThingsBoard run-1 / none / none; Stirling-PDF run-2 / run-1 / run-1.
See the [historical project report](../docs/reports/bidirectional-run1-bottom-up-concrete-static-20261017.md).

## 1. Model and reference

Use a finite inventory of typed nodes: static interest nodes and nonstatic
Base terminals. A terminal has no outgoing static route. The graph consists
only of reverse bindings compiled from actual abstract FLOW inputs. The
previous [need model](lean/ApSpec/BottomUpConcreteStaticNeeds.lean) supplies
this compiler, the source partition, original-field routing, Base reentry,
and a constructive finite path/closure equivalence. Its full binding labels
retain call-port provenance; the generic finite replay model starts after
this compilation boundary.

Represent source marks as finite sets and source maps as finite maps. The
reference endpoint predicate is independent of batching:

```text
ordinary(G,S,e,T) iff
    some source s contains T in S[s], and a finite path in G goes from s to e.
```

This is the logical terminal candidate relation before committed-R filtering.
Actual sent deliveries may omit candidates already in R. Final R also has
ordinary Base needs from other producers; it is not equated with this relation.

An executable witness consists of the source, membership of T and the
ordered finite path of actual bindings. The finite exhaustive closure is a
proof oracle. It is not a runtime requirement to retain all intermediate
requests, paths or witness parents.

The optimized model selects starts at a turn flush, groups equal mark sets,
and computes a multi-start closure for each group. Snapshot models use a
finite graph to check the local law. The runtime reads a live grow-only graph;
section 5 gives the extra incremental argument. Neither equality of lists
under a chosen canonical ordering nor set membership claims equal callback
order or equal JVM object allocation.

## 2. Source commit and batch selection

For a source with old marks O and offered marks A, compute fresh F = A - O.
The new source set is O union F = O union A. A mark in F has an offered
membership witness and is absent from O. Conversely, every offered mark
not already in O belongs to F. No new concrete mark is constructed.

For several commits in one turn, the pending row is the union of their fresh
sets. Membership therefore yields a particular commit with that fresh mark;
conversely each such commit's fresh mark belongs to the pending union.
This proves same-source merging without assuming disjoint source identities.

If the turn recorded graph growth, the committed source snapshot is taken
after that turn's commits. Every pending fresh mark is already in that
snapshot's cumulative row. Replaying both would duplicate interest work; only
the full snapshot is selected. A concurrent source commit that linearizes
after the snapshot is not claimed to be included. Its own pending delta and
parent reservation remain live.

Without graph growth, the pending delta union is the exact new source work
for the turn. Older marks were already scheduled by earlier source commits
or graph-growth flushes. This last statement is an invariant about admitted
work, not an assertion that all older waves have already finished.

## 3. Equal-mark multi-start equivalence

Let A be a finite list of starting nodes and M one nonempty mark set. Define
Reach(G,A) using a path from at least one member of A. Then:

```text
Reach(G,A) = union_(a in A) Reach(G,{a}).
```

Forward construction: take the multi-start path certificate; its starting
member is the required single source. Backward construction: retain the
single-source path and its starting-member witness. Cycles do not change
either construction. A visited search terminates on a finite graph because
each first entry removes one previously unvisited node from a finite inventory.
There is no semantic depth cutoff.

For every endpoint e and T in M, both procedures produce exactly the same
pair (e,T). At a shared visited node, all starts in this group carry the same
M, and the remaining transition relation is independent of source identity
and marks. One scan can therefore replace their repeated scans. Full actual
bindings are still used to decide the outgoing transitions.

Partitioning selected rows by **equal set contents** is disjoint and exhaustive
over the nonempty rows. Apply the preceding law to each partition, then take
the output union. Every resulting endpoint/mark has a starting row with that
mark and a valid path; every ordinary per-source endpoint/mark is in its
partition's multi-start result. This proves the complete endpoint/mark
relation, not merely equal reachable-node counts.

Different mark sets cannot use this argument. With disconnected paths
`A -> p` and `B -> q`, source A/{T} and source B/{U}, the reference emits only
p/T and q/U. Starting A and B with {T,U} also emits p/U and q/T. That is a
constructive counterexample to merging unequal sets. Hash equality alone
has the same problem; equal contents must be checked.

Source records and source-change observations remain keyed separately.
The optimization changes downstream traversal ownership, not the source
catalog. It needs no permanent per-node mark set, source-to-node provenance
table, global mark interner or copied binding graph.

## 4. Dispatch fan-out and path witnesses

The runtime's atomic enter-once operation is at a logical static node. Its
successful entry broadcasts the visit to every current subscriber runner.
Each runner scans the subset of actual bindings it owns. Their union is
the node's actual reverse adjacency.

Thus a proof step from node v to target u has a witness in one recipient's
owned binding directory. All recipients must be notified. Moving the shared
enter-once guard into each recipient would allow one runner to suppress the
others; it is not a valid refinement of the node transition.

A static target queues another visit with the same immutable M. A Base target
offers that M through the ordinary Base protocol. R may remove marks already
committed at that endpoint. Such removal does not change the effective
committed-or-pending union: those marks already have a committed witness.
Do not use another actor's mutable P to answer or suppress a required commit.
Several paths can offer one endpoint repeatedly; the equality result concerns
the union. No global terminal deduplication theorem is assumed.

## 5. Live graph soundness and completeness

### Soundness

Every source selected at a flush comes from a committed source row or from
fresh deltas already added to that row. Every traversed edge is read from an
actually admitted binding. The graph grows and does not retract bindings.
Concatenate the finite predecessor path with each traversed binding. This
constructs a final-graph path witness for every output. Equal-mark sharing
preserves some valid root for that witness by section 3. A source delta cannot
invent a mark, and the route cannot produce a TAINT fact or truth observation.

### Completeness for a final finite path

Choose a required source s, mark T and finite path P to a Base endpoint.
Take the finite list containing the insertion of T into S[s] and the first
admission of each binding in P. Compute the last insertion by folding over
their execution indices. This yields an executable latest-event witness;
it needs no classical choice or global graph snapshot.

If this event is the source insertion, that source's owner records its fresh
delta before finishing the turn. Its flush either selects the delta, which
contains T, or selects the full source map, which contains it too. All P's
bindings have already been admitted and remain available.

If the latest event is a binding admission, its owner marks the turn dirty
after registration. Its later source snapshot contains s/T, since the mark's
insertion precedes this event. The full-source batch includes that start.
Again all P's bindings already exist before dispatch.

In either case the new wave has fresh visited state. Induct along P. Its
starting node is dispatched. At each node, all existing subscriber owners
are notified and serviced; the owner of the next binding scans it. If the
next static node was already entered in this wave, its complete broadcast
was already secured; otherwise this step secures it. The terminal step
offers T, or finds T already committed. Thus the endpoint belongs to the
effective Base request union.

This proof also covers source-before-binding, binding-before-source,
sources committed after another snapshot, routes behind an old visited node,
late subscribers, cycles and overlapping batches. A late subscriber with an
actual new route is a binding admission and has its own dirty turn. An owner
cannot clear another owner's pending flag/deltas. An old wave's visited bits
never belong to this new wave.

The argument is for fair service of admitted work at successful quiescence.
It does not prove that the existing scheduler avoids starvation before a
deadline, or that an unbounded analysis terminates. Turn batching does not
change channel-drain policy or event priority.

## 6. Completion, cancellation and semantic composition

While a turn holds dirty replay state, its accepted parent reservation is
still live. Resetting the accumulator into a local batch does not release
that reservation. At flush, each visible queued child has a reservation
admitted before it becomes visible. Only after all root notifications are
secured may a complete event release its parent. An incomplete Work quantum
flushes, then retains its existing reservation when requeued.

The same argument applies at each static visit and terminal delivery. By
induction on admitted parent/child steps, count zero cannot coexist with
unfinished pending replay or queued descendants on a successful path. If
there are no subscribers, that particular root needs no descendant; a later
binding is covered by its own accepted turn and the preceding completeness
argument. Test-only deferred callbacks must also count as unfinished work.

A rejected required send, failed snapshot/group operation or cancellation
does not satisfy a successful release transition. It selects the existing
failure/timeout/OOM path. After owners join, the run releases transient
owners. Do not treat a cancellation cleanup clear as a successful replay.
Kotlin lifecycle and lock-order refinement must be tested; a finite reservation
accounting lemma is not a proof of coroutine cancellation behavior.

For the coupled analyzer, routing exposes only the same final Base interest
relation. The nonstatic solver can then emit only actual concretizations
under the unchanged abstraction rules. Static-origin reentry is just another
source insertion and is covered by section 5. Every source/route dependency
in a finite ordinary derivation is eventually enabled; every optimized
derivation has an ordinary path witness. Induction on finite derivations
therefore gives the same least closure under the existing transfer interfaces.

For this mutual argument, the common inputs are the same initial suppliers,
legal candidate coordinates and transfer interfaces. Equality of the final
admitted input/summary/source sets is a conclusion, not a premise. In the
ordinary-to-optimized direction, first reproduce a finite derivation's source
and binding prerequisites by induction, then use section 5 to route its need.
In the reverse direction, expand each optimized route into the finite ordinary
binding path that witnessed it. This constructs the corresponding ordinary
derivation without assuming its final graph already equals the optimized one.

This composition also requires the same FLOW binding compiler, source/sink
NNF event producers, ordinary R/P joins, concrete transfer and whole ND support.
It does not use absence of a request as evidence for a condition. Strong kills,
full original/support, sticky static DEMAND,
summary compaction and NORMAL persistence remain the previous contract's
obligations. Batching cannot weaken them.

## 7. Cost scope

On one finite fixed graph, each group scans the union of the static nodes
reachable from its starts. Membership in that union yields a particular start
with a scan, so its nonnegative weighted static-node scan cost is at most the
sum of the separate costs. Subscriber fan-out and owned binding checks can be
included in each static node's weight. Base endpoints are terminal outputs;
they have no global deduplication bound here. With equal-set starts that share
a positive-weight static node, scan work is strictly smaller. At most one wave
per distinct nonempty selected mark set is needed.

Combining g graph-growth notifications into b dirty turns changes full-source
selections from g to b. On an already reachable common route of positive
weight W with stable sources, its repeated work falls from g*W to b*W.
If b < g, that part of the cost strictly falls. A final-graph upper-bound
model can bound each replay separately when additions expose new routes.

These are practical finite cohorts. If b = g, all selected sets differ or
routes do not overlap, the corresponding reduction disappears. Hashing,
group construction and live outstanding batches still consume resources.
Hash-map grouping additionally costs comparisons over mark-set contents;
collisions can repeat those comparisons. The work theorem counts route visits,
not a universal linear bound on grouping time.
Different live schedules can enable different downstream work at different
times; no universal whole-engine instruction, allocation or peak-heap
inequality is asserted. The 60-second run-1 engine target needs measurement.

## 8. Formal checks and implementation boundary

The additive model and audit are
[StaticNeedReplayBatch.lean](lean/ApSpec/StaticNeedReplayBatch.lean) and
[static-need-replay-batch-audit.lean](lean/static-need-replay-batch-audit.lean).
The model uses a finite row list as the union oracle for source events and
pending deltas. `same_source_union` and `fresh_delta_exact` justify refining
that oracle to the runtime's normalized per-source maps. The row list is not
a proposed runtime store. Its multi-start search uses a virtual seed joined
to every start, then removes that seed from the result. Runtime dispatch
simply enters all roots in one wave; it need not allocate a synthetic graph.

| Obligation | Model result |
| --- | --- |
| Exact mark-set contents and source/mark support | `equal_marks_exact`, `group_support` |
| Executable multi-start closure and path certificate | `multi_nodes_exact`, `multi_path_complete` |
| Equal-mark grouping preserves typed endpoint candidates | `grouped_exact` |
| Empty marks skipped; no more groups than input rows | `group_marks_nonempty`, `group_count_le_rows` |
| Same-source union and fresh-delta decomposition | `same_source_union`, `fresh_source_decomposition`, `fresh_delta_exact` |
| Finite registration/flush trace invariant and exact final closure | `every_trace_valid`, `trace_flush_exact` |
| Committed-R filtering preserves effective union | `committed_filter_stutter` |
| Fixed-graph weighted static-node scan cost | `fixed_graph_visit_cost_nonincrease`, `runtime_static_scan_nonincrease` |
| Fewer stable-route replays | `stable_route_repeat_nonincrease`, `stable_route_repeat_strict` |
| Grouping overhead requires enough saved work | `cost_with_grouping`, `Controls.strict_static_saving_with_equality`, `Controls.grouping_overhead_can_exceed_saving` |
| Unequal-mark cross-product counterexample | `Controls.distinct_mark_cross_product_unsound` |

The trace model serializes abstract graph/source registrations and flush
evaluations. It proves arbitrary finite update order in that model. It does
not model overlapping Kotlin waves or an atomic live graph snapshot. The
latest-insertion proof in section 5 is the design bridge to that runtime.
For runtime static-scan costs, give Base terminal nodes zero weight: actual
Base offers do not have the finite search's node deduplication guarantee.

The existing counted-work model in
[BottomUpStaticTransport.lean](lean/ApSpec/BottomUpStaticTransport.lean)
proves `enqueue_before_release` and `no_early_completion` for its abstract
Accounting invariant. This change must additionally establish that every
pending replay batch belongs to an active parent as described in section 6.
Those arithmetic lemmas do not establish the coroutine/owner refinement.

The [validation report](../docs/reports/bidirectional-static-need-replay-batching-proof-validation-20261017.md)
records the checked controls, commands and source hashes. The focused audit
checks all 303 declarations owned by the additive module, including generated
and private helpers and their transitive axiom dependencies. It passes with
zero unexpected axioms; 28 controls also have the kernel-checked
`Controls.controls_pass` certificate. Only propext and Quot.sound are allowed.
None of these theorems is a kernel-checked proof of the full Kotlin runtime.

The required runtime adapters are:

1. Fresh ordinary/packed binding admission covers every logical route;
   installed payloads, full keys, geometry and source producer events agree
   with the existing compiler's interface.
2. The source union is synchronized, immutable snapshots are read outside
   actor state, and pending accumulators are runner-owned with no lost reset.
3. The flush hook runs for both complete and incomplete Work and all ordinary
   events. Same-unit inline work stays in the enclosing turn.
4. Grouping uses exact value equality, fresh per-batch visited state and a
   single enter-once decision before complete subscriber fan-out.
5. All queues and deferred test callbacks refine the counted parent/child
   protocol; cancellation joins before releasing owners.
6. Ordinary Base reentry and actual concrete replay preserve semantic report
   identities and rule occurrences, not just request-set equality.

Run the model controls first, then implement and test these adapters. No
analyzer implementation, runtime measurement or whole-engine termination
claim is part of this documentation change.
