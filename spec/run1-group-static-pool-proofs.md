# Writer-backed static groups: proof dossier

Date: 18 October 2026. Status: **revised proposal; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined mathematical
terms and code names keep their stated meanings.

This dossier follows [proof-guide.md](proof-guide.md) and supports the
[revised static proposal](run1-group-static-pool.md). The user keeps static
order/caller-context approximation but requires every delivered group to
have a finite source/writer derivation. The former FLOW-plus-interest rule
is superseded. Its older proofs remain historical, not evidence of group
precision in this revision.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
No analyzer implementation or project measurement is part of this work.

## 1. Concept and optimization

The concept is a source-rooted least fixed point over finite node/group
facts. Sources and explicit boundaries supply seeds. Productive operations
have nonempty positive premises. Requests and generic FLOW supply no seeds.
A conditional grouped initial remains a premise, not global availability.

The reference and direct procedure use the **same** run-1 group operations,
static order/context approximation, AP policy and eligible guard policy.
The direct procedure contracts only static identity availability relays.
It preserves productive local operations and their complete premise lists.
The required result is equal projected group derivability, including
conjunctive guard dependencies. It is not equality to concrete full-mark
execution feasibility or to a previous differently approximated analyzer.

The protocol optimization merges need/answer atoms and processes fresh
frontiers. Its executable finite oracle uses same-group edges. This proves
an important practical communication scope: writers, copies, readers and
ordinary providers that preserve groups. Conditional/multi-input and
rewriter semantics are covered by the positive concept calculus; their
optimized adjacency compilation remains an explicit adapter obligation.

## 2. Active models and historical models

| Active model | Scope | Constructive result |
| --- | --- | --- |
| [Run1StaticGroupDerivation.lean](lean/ApSpec/Run1StaticGroupDerivation.lean) | Finite positive group rules, seeds, transforms, multi-input guards and static relay contraction | Rooted exact closure, no invented groups, executable proof trees, equal projected reference/direct derivations |
| [Run1StaticGroupProtocol.lean](lean/ApSpec/Run1StaticGroupProtocol.lean) | Finite same-group writer/provider/consumer graph, wanted/answered sets and fresh work | Rooted answers, complete eager delivery, reverse needs, late registration, finite saturation and no duplicate answers |
| [Run1StaticGroupRequestSlice.lean](lean/ApSpec/Run1StaticGroupRequestSlice.lean) | Backward need closure across all positive premises, including changed groups and guards | Exact answers for requested facts with lazy activation of their provider scope |
| [Run1GroupStaticHandoff.lean](lean/ApSpec/Run1GroupStaticHandoff.lean) | Closed-catalog full-member decoding and finite writer/reversal search | Full-mark B2 query coverage, retained original read and intermediate writer guards |

The [prior mark dossier](rule-group-mark-proofs.md) still supplies ownership,
full-literal identity, cleaner and group-decoder laws. This revision does
not change those mark definitions or the F1/GROUP/1, B2/FULL/1 schedule.

`Run1GroupStaticPool.lean` and `Run1GroupStaticTrace.lean` model the previous
coarse FLOW-plus-need concept. Their laws remain true for that historical
model. They do not establish the new rooted-group invariant. The earlier
1,015-declaration/59-control receipt must not be used as this revision's
validation. Its independent handoff model is still applicable and is
included again in the active audit.

## 3. Source-rooted group derivations

A fact is `(node, group)`. A positive rule contains a finite nonempty list
of premise facts and one conclusion. A real zero-input source is an explicit
seed, admitted by the source/control/model adapter.

```text
root source/boundary fact
         OR
all positive premise facts + a declared operation
         => conclusion fact
```

`enabled` requires **all** listed premises. A group at point x does not
satisfy a same-group antecedent at point y. `advance` computes one closure
round; `closeAt` computes a finite number of rounds. Their lists are a
finite semantic oracle, not the proposed per-full-mark runtime storage.

`Derivation` and `Tree` are Type-valued proof data. `deriveAt` returns a
concrete derivation for each computed fact. `bounded_exact` proves both
directions between finite closure membership and such derivations.
`boundTree` computes a round bound and bounded witness for any finite tree.
It uses finite decidable search, not classical witness selection.

`solve` computes the full finite least closure. `solution_exact` equates its
facts with finite proof trees; `solutionRoot` returns a registered root.
`Saturation` contains soundness, seed inclusion and rule stability witnesses.
The solver removes a positive rule after firing it; `solve_firing_bound`
bounds firings by the finite rule count. Blocked source cycles remain empty.
These are finite oracle operations, not a required runtime full-rule scan.

`rootWitness` returns a registered source/boundary seed from every answer.
A declared rewriter can change its group, so the root's group need not equal
the output's group. The complete derivation retains that operation and all
its guard premises; root membership alone is not the full justification.

`closure_least` proves that every closed set containing the seeds contains
all computed facts. `empty_seed_closure` proves no groups exist without
seeds, even through copies and cycles. `requests_do_not_invent` proves that
selecting an answer by a need cannot change that result.

The source catalog is a primitive adapter boundary. It must contain only
reached configured source actions and explicitly modeled boundary groups.
An ordinary input reply is not a new independent seed. It must preserve
its actual caller/source derivation. A canonical `p/g` hypothesis can compile
`p/g => f/g`; it cannot supply p/g or f/g by itself.

Controls include the hypothetical-input case, wrong-group requests, empty
cycles, declared group transforms and a two-point/two-premise guard. A
zero-premise operation is rejected as an implicit source. A real source
must use the explicit seed interface after its ordinary guard permission.

## 4. Precise direct communication versus expanded relays

`Expanded` gives the comparison calculus. Its productive operations retain
their full premise lists. An identity static relay can move an established
group only between locations of the **same** static field. It cannot change
the group, create a source or replace a local operation point.

`compactNode` collapses those static relay vertices to one field node.
`contract` computes a compact proof and erases only identity relays.
`expand` reconstructs a reference proof from a compact proof, including all
productive local operations and their premises. Finite search obtains the
actual original rule/premise witnesses.

`compact_expanded_exact` proves both directions of projected group
reachability for this same approximation. Thus the optimization does not
get its savings by adding arbitrary group facts. Call-order/caller-context
precision was already removed in both sides of this comparison.

This is a finite calculus equivalence. The real AP, source guard and call
compiler must supply its local operation witnesses. It does not prove that
the previous measured Kotlin implementation already compiles this calculus.

## 5. Writer/consumer protocol and provider replies

The protocol stores wanted and answered node/group atoms, registered edges
and a finite valid source catalog. It does not store a per-edge/group
history. Proven groups propagate forward eagerly, independently of wants.
Wants propagate backward on provider edges. A request may activate an
ordinary P/R provider but cannot answer itself.

`Safe` maps each supplied atom to an executable rooted `Derivation`.
`applySafe`, `stepSafe` and `closeSafe` preserve it through productive work.
`closeCertificate` and `lookupCertificate` return answer witnesses.
`sourceRoot` exposes the real registered root. `no_source_no_answer` and
`request_cycle_cannot_bootstrap` exclude unseeded answer cycles.

`acceptProviderReply` requires an existing rooted Derivation. This is the
important difference from trusting a callback or an analyzed grouped
initial. `observeUnknownFlow` and `observeCanonicalHypothesis` cannot add
answers. Their controls reject the old FLOW/hypothesis bootstrap.
Runtime need not retain proof objects: its provider/compiler refinement
must justify the same invariant using correct roots and transfer firing.

`close_complete` proves that every finite registered seed/edge derivation
is in the final answer set. `closed_answer_sets_equal` proves equal final
answers from any two safe prior frontiers. Thus different need order and
prior partial work cannot change supplied group membership.

`Extension`, `liftDerivation` and `late_registration_same_answers` cover
new registered sources/edges. Old answers stay valid; replaying the new
binding reaches the same final answer set as a fresh closure on the grown
network. Request union has separate commutative/idempotent membership laws.
`request_orders_same_answer_set` combines them with answer closure.

Both late-answer and late-writer cases therefore work. A source before a
request, a request before a writer, or a writer before a source cannot
turn an unsupported group into an answer or lose a supported finite path.
Dynamic AP registration and provider activation still need fair scheduling.

The active requested scope permits lazy ordinary input emission.
`Run1StaticGroupRequestSlice.reverseNeeds` selects every premise of every
candidate rule for a requested conclusion. `scope_premise_closed` proves
that its finite computed need set contains all those inputs and guards.
The selector closure is stored as **wants**, never as supplied groups.

`restrictTree` computes a supply proof using only scope-selected rules and
real source seeds. `restoreTree` computes the reverse proof. The
`requested_solution_exact` theorem proves that restricted and full closures
agree on each requested fact. It handles different-group predecessors and
multiple guard positions. An unrequested group may remain parked; no negative
reachability claim follows from its local absence. The runtime must activate
each selected provider and wake actions on fresh **guard** supply as well
as RHS supply. The proof does not assume whole-engine closure in advance.

## 6. Fresh work and completion

`enabled` rejects a backward need already stored at the producer and a
forward/seed answer already stored at the target. `provider_reply_not_twice`
and `step_does_not_repeat_answer` establish duplicate suppression.
The state commits a fresh atom before later work can observe it.

A finite missing-key potential counts absent wanted and answered atoms.
`action_decreases` proves strict decrease for each productive action.
`close` therefore computes saturation without an arbitrary runtime fuel cap.
`close_stable` returns a state with no productive next action.
`close_idempotent` and `close_preserves_nodup` give stable deduplicated state.

`step_count_bounded` bounds productive actions by the starting potential.
`empty_state_step_bound` gives at most twice the finite node/group key count.
This counts state insertions, not all scans, callbacks or JVM operations.
The executable oracle's `actions` enumerates finite keys/edges. Production
must use adjacency and fresh bitsets; it must not retain that Cartesian list.

An unanswered request remains in `pending`. Once all available growth is
processed, `next = none` even if pending is nonempty. It cannot hold a
barrier forever. A later registered source/edge can reactivate the retained
interest through the proved growth interface. No false negative cache is
created at the earlier barrier.

The optimized runner's owner-turn/method-empty batches, lock boundaries,
cancellation and fairness remain adapters. Equal final answer sets do not
prove equal callback counts or elapsed time.

## 7. Conditional rules, rewrites and AP adapters

| Adapter | Required local contract |
| --- | --- |
| Actual sources/boundaries | Validate control/action and explicit allowed group set; no catalog/need/FLOW universal root |
| Ordinary input | Retain canonical geometry and real caller/source premises; hypothetical initials are conditional results |
| Positive group guards | Preserve every bound position and all required antecedents; compile original NNF DAG without cube expansion |
| Signed/non-mark guards | Fold full signed identities first; retain ordinary eligible-negative and non-mark policy |
| Mark operations | Forward group image and reverse preimage agree; h can request g, not necessarily h |
| Selected cleaners | Keep the user-selected group retention, request independent of mx, and real input grounding |
| RemoveAll/field writes | Separate stages; preserve true-condition cleaner admission; group possibility cannot enable a kill; retain Empty-E survivor demotion |
| AP scopes | Match writer, reader and provider geometry at a common location; do not confuse exact ancestors, broad scopes or initial/current tails |
| Calls/returns | Preserve productive argument/formal/output bindings and reached exit stages; static availability needs no relay links |
| Storage | One local node per stage/AP, group bitsets, no remote-static-original set or writer-reader product |

The positive-rule concept handles transforms and multiple premises. The
same-group protocol proof does not implement their optimized watchers.
Before runtime work, extend/refine the production circuit/transfer compiler
against the concept model and use its executable derivations as the oracle.
This is a practical scope limit, not an assumed whole-engine result.

## 8. Run-2 handoff and confirmation

The reused handoff model expands a group demand against the complete full-
mark catalog. `every_full_group_member_gets_seed` and the prior decoder
soundness/completeness laws retain unobserved full members. The first B2
field limit remains 1. Exact groups do not imply exact full-mark membership.

Writer inventory lookup remains independent of F1 observed/supplied group
sets. `open_writer_complete`, `compilePath` and
`every_finite_reference_trace_discovered` cover finite compatible writer/
reverse traces under local inventory, overlap and reversal obligations.
`compileGraphPath` retains the original read and every intermediate guard.
An opening is query-only DEMAND; it is not a semantic writer-to-reader edge.

The actual runtime still needs explicit internal-point backward seeds,
ordinary incoming caller-port expansion, and session authorization beyond
missing F1 relay guides. Preserve exact forward source-permission identity.
Later forward work must start from actual roots and permitted sources and
reconstruct real CFG/call order, kills, conditions and ND support.
Neither a group derivation in the approximated F1 graph nor an isolated
writer query supplies the existing confirmation certificate.

The same source, scope, queue, point-query and confirmation adapter limits
from the previous handoff design remain. Group precision removes the
arbitrary-group bootstrap; it does not eliminate these separate duties.

## 9. Scoped savings

`relay_chain_work` counts h identity relay steps in a transparent expanded
static chain. `contracted_chain_is_source` proves that its compact witness
is the original single source proof. `strict_chain_saving` gives a strict
relay-step saving for h > 0. Productive calls and local/guard work are not
included in that count.

`full_supply_exact` proves that a projected group exists exactly when a
supplied full mark has that owner. T and U in one rule share one group atom;
a different owner does not. Together with the prior homogeneous cohort law,
this is a scoped full-mark-to-group storage saving.

Communication now retains sparse writer/local dependencies. They are a real
cost. It must not regain the remote-original/full-mark product or a producer-
reader matrix. No theorem here guarantees fewer bytes, a project speedup or
completion within 60 seconds. The current measured projects were not rerun.

## 10. Reproduction and evidence

Use Lean `leanprover/lean4:v4.33.1` in `spec/lean`:

```sh
lake env lean --version
lake build
lake env lean run1-static-group-communication-audit.lean
```

The focused audit visits all declarations owned by the three new group models
and the reused handoff model, including generated helpers. It admits only
standard `propext` and `Quot.sound`, then executes all named controls.
The final receipt binds stable source hashes, commands, exit codes and logs.
Older coarse-pool counts and receipts remain historical.

Final check on 18 October 2026: **PASS**. Lean 4.33.1 builds **148 jobs**.
The active audit checks **1,332 declarations**, finds **zero unexpected
axioms**, and passes **90 executable controls**. Only standard `propext`
and `Quot.sound` are admitted; no classical choice is admitted.

| Module | Audited declarations | Passing controls |
| --- | ---: | ---: |
| Run1StaticGroupDerivation | 495 | 35 |
| Run1StaticGroupProtocol | 360 | 26 |
| Run1StaticGroupRequestSlice | 32 | 6 |
| Reused Run1GroupStaticHandoff | 445 | 23 |
| Total | 1,332 | 90 |

The [validation receipt](/drive-testcomp/opentaint-go-rules/run1-static-group-precision-20261018/validation/20261018T204238.281574Z/receipt.json)
has SHA-256 `2058fbf9f4d8c77b9dce7e297595bdbf1fb7a2369088ec03c858f51baa348399`.
It binds stable proof-source hashes to the
[build log](/drive-testcomp/opentaint-go-rules/run1-static-group-precision-20261018/validation/20261018T204238.281574Z/build.log)
and [audit log](/drive-testcomp/opentaint-go-rules/run1-static-group-precision-20261018/validation/20261018T204238.281574Z/audit.log).
No runtime test or project benchmark was rerun. Latest measured run status
remains the one at the start of this dossier.
