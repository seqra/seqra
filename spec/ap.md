# Access paths and storages — specification

This document defines facts, edges, AP operations, runs, and stores for
[bidirectional-task.md](../bidirectional-task.md). It is normative.
[interpreter.md](interpreter.md) defines IR construction and rule order.
[analyzer-core.md](analyzer-core.md) defines events, ownership, scheduling, and the
iteration driver. Each rule has one owner; the other specs use its contract.

The current rules include F72 demand sharing, F74 field-cleaner lowering, F75
base-dependent cleaner splitting, F76 must-summary read views, and F77 structural
summary demand selection. [ap-history.md](ap-history.md) records the decisions.
[proof-status.md](proof-status.md) separates current checked results from historical
proofs and open obligations. Local proof results do not establish the full current
iteration. A Lean name is evidence; the rules and their conditions are stated here.

Language: ASD-STE100 Simplified Technical English.

---

## 0. Scope

This spec defines:

* the fact representation for the forward and the backward analysis;
* the operations on facts: delta-concat, micro edge application, summary edge application, summary restriction, field
  limit, mark gate and mark request, mark conjunction (ND edges), cleaner, type filter, emission, reversal;
* the storages that the analyzer uses in one run and across runs;
* the soundness theorems for these operations and the scope in which they hold.

This spec does not define the IR interpretation (`interpreter.md`), the analyzer scheduling, the iteration driver (§6.6
gives only its general requirement and its stop rules; `analyzer-core.md` §7 defines the driver) or the trace
resolution (out of scope; §8.10 gives the traces of the report). It defines the contracts that these parts use. The
contract of the backward run (contract B) is in §6.6.

### 0.1 Construction and proof conditions

These conditions are part of the design. The interpreter and analyzer must enforce
them. Each proof uses only the conditions stated in [proof-status.md](proof-status.md).

| # | Condition | Owner |
|---|---|---|
| S1 | Statement summaries list touched bases and all micro edges. Each micro edge describes exactly its flow in §3.5. The reference semantics treats negated mark literals as true. A pass rule has no mark literal; a pass rule with such a literal is the over-approximation of §11.1. Other rule errors reject the whole rule. | Interpreter |
| S2 | Alias gen edges describe aliases that can hold. Alias writes are weak: an alias base is not touched and keeps its content. The formal reference program is alias-free. | Alias analysis |
| S3 | A method does not reassign formal parameters. | IR |
| S4 | Each exit kind has one exit. Normal and exceptional exits form one virtual exit: end rules and backward starts act at both; only the normal exit produces summaries. | CFG |
| S5 | A type filter is prefix-closed and accepts every path that a real value of its static type can have (§4.8). | Type checker |
| S6 | A complete run is the least fixed point of §6.1. Worklist order does not change that result. | Analyzer |
| S7 | No micro edge or binding has a `*∖X` premise. A concrete target mark requires a concrete premise mark. | Interpreter |
| S8 | No initial fact, micro edge, or binding has `*/Universe`. A `$` premise has a concrete mark. No micro edge has a `$` premise and `*` target; a `$` target requires a concrete premise mark. | Interpreter |
| S9 | Each conjunction literal names a concrete mark. | Interpreter |
| S10 | A statement edge reads a touched base. Every call binding has `*` premise mark and `*` tails on both sides. A conjunctive target has a concrete mark and no `*` tail (W7). | Interpreter |
| S11 | Backward construction: (a) binding target marks are `*`; (b) statement edges are mark-reversible (§1); (c) no forward back-binding targets zero; (d) each non-call keeps zero, no cleaner acts on zero, and a filter on zero accepts its empty path; (e) each node reachable from a root/callee entry can reach its virtual exit; (f) sink patterns have `$` or `[any]`; (g) statement edges and bindings have exact shape (§1): a `*` premise with a `$` or `[any]` target has empty field exclusion. Clause (g) is needed for generic reversed-edge exactness; the record views of §8.7 have their own shapes. | Interpreter and CFG |
| S12 | Static construction: a static position is a read/sink path on `S`, cut to the class and field. An `S→S` edge is an identity restriction or reads and writes at/below a static field. An edge from another base to a bare class position has concrete `$` premise and target; each literal of a conjunctive edge has the same form. No pass rule reads/writes a bare class position. Calls bind `S.*→S.*`. Class accessors do not count and each limit is at least 1. A cleaner on `S` names its mark; removing all marks at a static position is a strong-write kill. The empty-path whole-base all-mark cleaner is the one exception: it drops each `S` fact whole. `S` differs from zero. Run 1 uses §6.2. Restricted runs require the edge/binding/limit conditions and records that preserve the static invariant. | Interpreter |
| S13 | Validity for exactness: filters accept all valid locations; validity of an edge's end implies validity of its start, for statement edges and both call bindings. For conjunctions this holds for each literal. | Type-filter placement |
| S14 | Input records are exact on valid end locations. Must records are end-exact on admitted locations, have concrete conclusions, and satisfy W8 normal form. Reversed backward records require the extra conditions of §8.7; dropping backward filters can break exactness (§11.1). | Record store |
| S15 | Only sources and triggered end-fact actions use a `[any-taint]` target, with concrete premise/target marks. Pass rules with an AnyField target use may `[any]`; AnyField pass premises are rejected. No micro edge has a `[any-taint]` premise. The backward run has no `[any-taint]`; reversing a may target puts every result in the demand layer. | Interpreter |
| S16 | A field cleaner uses a fresh temporary: read the field, clean the temporary root, strongly write it back. Keep the read identity and strong-write keep edges. Reverse all three steps backward. The temporary does not escape or enter a summary (§4.7). | Interpreter |

The current demand contract also requires normalized patterns and the explicit
entry-tail guard in §6.1. This guard is proved for the alternating base model; its
full X-tail and pipeline construction is open. Claims about soundness of every
current iteration remain proof obligations (§11.2).

---

## 1. Terms

| Term | Meaning |
|---|---|
| base; static base `S` | A local, argument, receiver, return, exception, constant, static/global base, or zero. `S` holds static fields and globals. |
| accessor; path | An `AccessorIdx`: a field, element `[e]`, or class/global accessor. A path is a list of concrete accessors. |
| root; root path | An entry method of the analysis; the empty path `[]` of a base. These are different uses of “root”. |
| tail; tail kind | `$`, `*`, may `[any]`, or must `[any-taint]`, together with its field exclusion (§2). |
| `[any-taint]/E`; must-premise | Every admitted descendant carries the concrete mark. A must-premise assumes that property at method entry, only in a forward restricted run. |
| exclusion; Universe | A finite set of forbidden first accessors of a continuation. Universe forbids every first accessor, so only `[]` remains; the AP forbids it (S8). The formal model uses it to encode `$`. |
| mark; zero mark | Concrete `T`, abstract pass-through `*`, or conclusion-only `*∖X`. Marks are unbounded, so two abstract marks meet. Zero has a reserved concrete mark that no rule names. |
| effective mark | Conclusion mark if concrete, else premise mark if concrete, else abstract. |
| fact; pattern | `(base,path,tail,exclusion,mark)`. A pattern describes demanded locations; a fact is propagated. |
| premise; conclusion | An initial assumption at method entry (forward) or exit (backward); a fact at the current node. |
| edge; premise set | `(premise set,layer,statement,conclusion)`. `{zero}` gives a zero-to-fact edge; one nonzero premise gives fact-to-fact; two or more give ND. A multi-member set contains no zero. |
| layer; complete edge | Normal or demand. “Complete edge” means normal, including forward must tails. Only normal summary edges can become records. “Complete run” has a different meaning below. |
| micro edge; statement summary | An interpreter edge of a statement or call binding; a statement's touched bases, micro edges, and filters. Neither is a callee summary edge. |
| touched base | A base that an instruction can change. Untouched facts pass; touched facts keep only micro-edge results. |
| propagation edge; summary edge | An edge derived inside a method; an edge at its forward exit or backward entry. |
| exact; end-exact | Exact means every edge pair is a concrete flow. For a must-premise, end-exact means each admitted end comes from some admitted start; its arbitrary pairs need not be flows. “Exact” also names `$` and a cleaner reach. |
| mark-reversible | `i→f` has an abstract conclusion mark, or a concrete premise mark. |
| exact shape | Excludes `*/E→$` and `*/E→any` with nonempty premise exclusion. |
| record; must record; read view | A persisted normal single-premise raw summary (§8.7); a record whose premise is a must-premise; a transient premise pattern and conclusion used to read one record leaf from the other direction. A read view is not persisted or started as an initial fact. |
| summary selected for demand; demand edge (hand-off) | A published piece selected by §8.5 for next-run demand construction. Selection does not change its layer: a normal multi-premise summary remains normal. It gives the next run a pattern per premise member (§9.2). Other native normal singleton summaries supply records only. Lean's `DemandEdge` names a demand pattern. |
| caller fact; bound fact; added fact | A caller edge's conclusion; its value after binding into callee coordinates; its value after call cleaners. Callee summaries apply to added facts. |
| link | An added fact paired with its caller edge. |
| summary rewriter | A call-rule override that cleans selected marks/positions on summary and unresolved results. It is not a summary edge. |
| abstraction; policy | Selection of initial facts from added facts; specifically the run-1 selection in §6.2. Interpreter mark policy is a different filter rule. |
| request; chain answer | A run-1 mark or static-position request. A mark answer uses the requesting premise's base/path, the request chain (§4.5). |
| static position | A statement-read or sink path on `S` cut to class and field (S12). |
| standing | A request, subscription, or conjunction fact remains active and matches later events until its run ends. |
| run; restricted run | A pass with one direction and field limit. Every run after run 1 is restricted to demand; it can use abstract facts and has no request rules. |
| complete/incomplete run | Complete means the fixed point at quiescence. Timeout, memory guard, or any `Throwable` makes the run incomplete; no incomplete evidence enters the report/hand-off. |
| demand pattern; demand | Entry `D-c` and optional exit `D-p`, oriented for the receiving run; the set of these patterns. Demand is distinct from the demand layer. |
| FLOW form; FLOW premise; sharing | §6.3's abstract initial fact for a `*` pattern; an initial fact with mark `*`; one such premise serves all admitted added facts and marks. |
| satisfies; strong enough | A caller can apply a premise's summary by §4.3; specifically `applicable` for “strong enough”. A fact above a premise is not strong enough because correlation is lost. |
| concrete flow; witness; real | A chain of §3.5 steps; a root-zero-to-sink chain through calls; present in that reference semantics. |
| demanded/recorded witness | Each returning call has one pattern covering entry and exit with marks, or a permitted record/read view preserving the required pair. Each downward entry is demanded. F72 adds the mode conditions of §6.6. |
| vulnerability; sink alternative; sink witness | A `(rule,method,statement)` key; one numbered disjunct of its sink condition; the reached sink edge/set for one alternative and method key in one run. A sink witness is distinct from a concrete witness. |
| support | One call supplies every member of a premise set through normal edges whose own premise sets are supported, down from root zero (§4.9). It is a property of the whole set. |
| DEMAND entry | Reported by the latest complete forward run and never confirmed so far. Confirmation is final, even if a later run only has a demand witness. |
| frontier | Method keys with nonzero initial facts and their outgoing demand edges. The driver logs these and record/seed/work counts. Full current narrowing is open (§6.6). |
| ND edge; taint edge | An edge with at least two nonzero premises; a source edge with `[any-taint]` target (S15). |
| method key; prescan | `MethodEntryPoint` (context plus forward entry), stable across runs; the prior analyzer pass that resolves lambdas/closures. |

NOTATION. `ap.md` and `interpreter.md` use these forms. The interpreter-only forms are in `interpreter.md` §0.

| Form | Meaning |
|---|---|
| `(x, p, t, E, m)` | A fact or a pattern: the base `x`, the path `p`, the tail `t`, the exclusion `E`, the mark `m` (§2.1). |
| `(x, p, t, m)` | The same. The exclusion is Empty, or the tail kind `t = */E` or `t = [any-taint]/E` carries it. |
| `(x, p, t)` | A pattern whose mark is not relevant (for example a pattern read as locations, §3.2). |
| `.`, `[]`, `.f.g`, `[f, g]` | A path. `.` and `[]` are the empty path. `.f.g` and `[f, g]` are the path of the accessors `f` and `g`. |
| `p ++ r`, `p·r` | The path `p` followed by the path `r`. |
| `x.p.*` | A micro-edge side or a fact `(x, p, *, {}, *)`: the `*` tail with the mark `*`. |
| `x.p.$ (T)`, `x.p.[any] (T)`, `x.p.[any-taint] (T)` | The tail `$`, `[any]` or `[any-taint]` (with the Empty exclusion) with the concrete mark `T`. Without `(T)` the mark is `*` (never for `[any-taint]`, W8). |
| `i → f` | An edge with the premise `i` and the conclusion `f`. `j → g` is a summary edge. `{j1, …, jk} → g` is an ND edge. |
| `a →_{f} b`, `i →_{E} f` | A micro edge or an edge with the exclusion `{f}` or `E`. With no subscript the exclusion is Empty. |
| `Zero → (layer, statement, fact)`, `Zero → (statement, fact)` | A zero-to-fact edge: an edge with the premise set `{zero}`. In the second form the text names the layer. |
| `[e]`, `<C>`, `<G>` | The element accessor; the class accessor of the class `C`; the accessor of the Go global `G`. |
| `S`, `zero`, `ret`, `this`, `argi` or `arg(i)` | The static base, the zero base, the return value, the receiver, the argument `i`. |
| `{x, y}` | The touched bases of a statement. |
| `*∖X` | The abstract mark except the marks of the set `X` (§2.2). |
| `(m, i, T)`, `(m, i, p)` | A mark request for the mark `T`, and a position request for the path `p`, on the premise `i` in the method `m` (§4.5, §4.10). |
| §4.2; `interpreter.md` §2.1 | A section of this spec; a section of `interpreter.md`. A list of sections after `interpreter.md` (for example `interpreter.md` §4.4, §4.7) is a list of sections of `interpreter.md`. |

---

## 2. The fact and the edge (the concept)

### 2.1 The tuple

A fact is `(base, path, tail, exclusion, mark)`:

* `path` — the concrete accessors. It never contains `[any]`, `[any-taint]`, a mark or `$`.
* `tail ∈ {*, [any], [any-taint], $}`. The `[any-taint]` tail has a concrete mark, and it occurs only in a forward
  run (W8).
* `exclusion` — a finite set of accessors, only with the `*` tail or the `[any-taint]` tail (W8).
* `mark ∈ {*, T}` for a premise; `mark ∈ {*, T, *∖X}` for a conclusion.

### 2.2 Edges, layers, the edge exclusion and the mark exclusion

An edge is `(premise set, layer) → (statement, conclusion)`. A fact-to-fact edge is also written
`(premise, layer) → (statement, conclusion)`. The zero-to-fact edge is `Zero → (layer, statement, fact)`: its premise
set is `{zero}`. An ND edge is `({premise1, …, premisek}, layer) → (statement, conclusion)` with `k ≥ 2` premises that
are not the zero fact (§4.6).

* An edge has ONE exclusion `E`. In a CORRELATED edge (`*` premise, `*` conclusion) the premise and the conclusion
  share it: for `(x, p, *) →_E (y, q, *)`, the value at `x.p.σ` flows to `y.q.σ` for every `σ` that `E` admits. The
  model stores `E` on the conclusion (the tail kind `*/E` of the target). If the premise also stores an exclusion, the
  edge exclusion is the union of the two (`SharedExcl.applyEdge_shared_excl`, `den_shared_excl`). An implementation
  stores `E` once, on the edge.
* An UNCORRELATED edge (the conclusion is `$` or has an any tail) has no shared exclusion. Its conclusion has the
  Empty exclusion, except an `[any-taint]/E` conclusion (W8): there `E` restricts the continuation of the conclusion
  only (§3.1). Every operation of §4 gives an uncorrelated result the Empty exclusion, except the rows of §4.1, §4.7
  and §6.3 that give an `[any-taint]` result its exclusion. An above-case transfer of a correlated fact loses its
  correlation and gives a demand result (§4.1). A `*/E` premise of an uncorrelated edge (before F72 a restricted
  run could emit one with a concrete mark, §6.3) keeps `E` in its premise: `E` restricts the premise continuation only. The
  exclusion of a must-premise (§6.3) does the same.
* The MARK EXCLUSION `X` of a conclusion `*∖X` stops the marks of `X`: the value at the premise location flows to the
  conclusion location only if its mark is not in `X`. It belongs to the edge, as the exclusion does; a cleaner makes it
  (§4.7). A premise never has one.
* The policy facts and the chain answers of run 1 have the Empty exclusion (`Reverse.policy_premEmpty`,
  `Reverse.answerInit_premEmpty`). A position answer `(S, p, *, {}, *)` has the Empty exclusion by its definition
  (§4.10 item 2). The mark answer on a static premise is the added fact itself (§4.10 item 4). It has a concrete
  mark, so by W2 it has the `$` or the `[any]` tail and the Empty exclusion (an `[any-taint]/E` added fact gives the
  `[any]` answer, with no exclusion: W8 (c)). (The Lean theorems above do not cover these two static answers; the
  statements follow from their definitions.) In a restricted run an emitted fact can have the exclusion of the demand.
  Before F72 it was the meet with a `*/E` entry pattern (§6.3), with a concrete mark, so it started in the demand
  layer (§6.5) and made no record. Since F72 a `*/E` entry pattern has the mark `*` (W2), so it gives its FLOW form
  `(x, p, */E, *)` (§6.3): it starts with the identity in the normal layer (§6.5), as a policy fact does, and its
  exclusion is the shared exclusion of its correlated edges (above). (In the spec runs no demand pattern has the tail
  `*/E`: every `*` pattern has the entry tail `[any]`, and its FLOW form `*/{}` has the Empty exclusion, §6.3.) A
  must-premise of a forward restricted run can
  have the exclusion of its `[any-taint]/E` added fact (§6.3); it starts in the normal layer with it (§6.5). So the
  exclusion of a normal edge is a property of its conclusion (and of a must-premise). Only a strong field write makes
  it larger (by its keep edge, or through a `*/E` summary or record), and, on an `[any-taint]` fact, a cleaner one
  accessor below it (§4.7).
* The LAYER is part of the edge identity. A micro edge has no layer (§4.2): the layer belongs to the propagation edge.
  Only these AP operations put a propagation edge in the demand layer:
  * the start fact of an initial fact with the `[any]` tail, or with the `*` tail and a concrete mark (§6.5). An
    `[any-taint]` start fact (a must-premise, forward restricted runs only) is normal;
  * the case `above` of §4.1 for a fact that is not `[any-taint]`;
  * an `[any]` result (W6: a may), and the normal form W2 of a `*` result (§4.1 step 6);
  * in the backward run, every result of the reversal of a micro edge whose forward target is `[any]` (a pass rule
    with an `AnyField` target, a may), also a `$` result (§9.1);
  * the cut of the field limit (§4.4);
  * a conjunction with an input that is in the demand layer, or that its literal does not cover and that is not
    `[any-taint]` (§4.6);
  * the application of a demand-layer summary edge (§4.3), also of a summary with several premises (§4.6); the
    application of a must record by `applicable` only (§4.3);
  * the `part` rows of the cleaner that give a demand-layer result (§4.7);
  * the END FACTS of a sink (§4.9): an end-fact edge takes no input fact. When a sink edge triggers, or a conjunctive
    sink completes a combination, it applies to the zero fact in the layer of that sink edge or of that combination
    (a combination is in the demand layer if one of its edges is).

  A demand-layer input gives a demand-layer result. The layer of an edge never goes back to normal
  (`Invariant.applyEdge_demand_monotone` and the related lemmas). On an `[any-taint]` fact only these operations give
  a demand-layer result, which W8 (b) names `[any]`, with no exclusion (THE DEMOTIONS; the other lists of this spec
  refer to this one): the field-limit cut (§4.4); a cleaner `part` row of §4.7 other than the `atAndBelow` and `below`
  rows one accessor below the fact (so the `exact` cleaner at the path of the fact or one accessor below it, and every
  cleaner two or more accessors below it); a may target (an `[any]` target of a pass rule, W6); a demand input (a
  demand fact, summary edge or record: another input of a conjunction in the demand layer, §4.6, a demand-layer
  summary edge or record, §4.3, or a demand link, an `[any]` added fact, §4.3); and the must-record demotion
  (§4.3; Lean `AnyTaint.recLayer`, `AnyTaintEx.recLayerX`). An exclusion that meets an `[any-taint]` fact (the keep
  edge of a strong write, a `*/E` summary or record) does NOT give a demand result: the result keeps the layer and
  carries the exclusion (W8 (a); §4.1).

Lean: `PFact` (a premise or a conclusion; the conclusion `*/E` carries the exclusion of the edge; the mark is `MarkA`
with `star`, `conc t`, `starEx x`) and `AFact` (a conclusion plus `demand : Bool`, the layer); with the exclusion of
`[any-taint]`, `AnyTaintEx.XFact` (an `AFact` plus its exclusion `ex`).

### 2.3 Well-formedness rules

| Rule | Text |
|---|---|
| W1 | An edge with no `*` side has the Empty exclusion. |
| W2 | A conclusion with the `*` tail has the mark `*` or `*∖X` and is in the normal layer. A premise may have the `*` tail with a concrete mark (a request answer in run 1; before F72 also, in a restricted run, the meet of an added fact with a `*/E` entry pattern, which F72 replaces by the FLOW form, §6.3). |
| W3 | In a run with the field limit `L`, every result of an operation has at most `L` counted accessors (§4.4). This claim needs nondecreasing limits (§6.6), bounded seeds, and binding-in edges that do not increase the counted path depth. The interpreter bindings into a callee meet this condition (`interpreter.md` §3.1). Then an added fact, a premise emitted from a demand chain (§6.3), and a fact on an untouched base stay in the bound. A generic micro edge (§4.2) has no bound; nondecreasing limits alone do not bound a path-growing binding (`ReviewDemand.binding_in_needs_non_growth_condition`). W3 as a whole is argued, not proved (§11.2). |
| W4 | `[any]` and `[any-taint]` are tails only. A path has no inner `[any]` or `[any-taint]`. |
| W5 | Marks are not accessors. `TaintMarkAccessor`, `FinalAccessor` and `AnyAccessor` do not occur in a path. `TypeInfoAccessor`, `TypeInfoGroupAccessor` and `ValueAccessor` do not occur either: the type-info accessors serve only the lambda analysis of the prescan, and no statement and no rule makes a value accessor. |
| W6 | A conclusion with the `[any]` tail is in the demand layer. |
| W7 | Only a conclusion has a mark exclusion. An edge whose premise set has two or more members (an ND edge, §4.6) has no `*` tail. |
| W8 | THE `[any-taint]` RULE. (a) `[any-taint]` has a concrete mark only (never `*` or `*∖X`). It is a TAINT conclusion (§7.2). It can carry an EXCLUSION `E` of first accessors (`[any-taint]/E`, §1). Only a normal `[any-taint]` conclusion and a must-premise have one; an `[any]` fact, a `$` fact and every demand-layer fact have the Empty exclusion (Lean: `AnyTaintEx.carriesB`, `normX`). (b) THE NORMAL FORM: a conclusion with the `[any-taint]` tail is in the normal layer. A demand-layer result with the `[any-taint]` tail becomes `[any]`, with the same path and mark and with the Empty exclusion (a normal form, as W2; §4.1 step 6). So in a forward run the layer gives the any tail of a conclusion: `[any-taint]` in the normal layer, `[any]` in the demand layer (§7.2). (c) An `[any-taint]` PREMISE (a must-premise) occurs only in a forward restricted run, never in run 1 (§6.5). (d) `[any-taint]` is a FORWARD tail. The backward run has no fact, premise, edge or demand pattern with the `[any-taint]` tail: its seeds and its reversed `[any]` literals give `[any]` (§9.1, §9.2), and the hand-off reads a forward `[any-taint]/E` conclusion as the pattern `[any]` (§9.2). A normal edge with the `[any-taint]` tail is complete (§1). |

W2 holds for every derived fact, in run 1 without the static rule and the conjunctions (the closure `D`:
`Invariant.final_star_legal`) and in every forward restricted run (`RExact.final_star_legalR`). For run 1 with the
static rule (`Statics.DS`) it is argued (§11.2); with the conjunctions (`ND.DN`) it holds by W7 (a conjunction target
has no `*` tail). The backward run is concrete (`BExact.DB_concrete`), and it satisfies W2 when its seeds have
concrete marks and no `*` tail (`HandoffNoStar.DB_legal`), so no backward conclusion has a `*` tail
(`HandoffNoStar.DB_edge_nonstar`); every seed is a sink pattern with the tail `$` or `[any]`, cut by the field limit
(S11 (f), §9.2; `HandoffNoStar.nonstar_of_sinkK`). These are theorems of the concrete restricted runs (the emission
`emitM`). SINCE F72 a restricted run, forward or backward, has `*` conclusions: the edges of a FLOW premise (§6.3).
They have the mark `*` or `*∖X` and are in the normal layer, so W2 holds for them for the same reason as in run 1
(S7: a concrete target mark needs a concrete premise mark; argued, PENDING §11.2). The claims "the backward run is
concrete" and "no backward conclusion has a `*` tail" are false for the F72 runs.

W6 only moves edges from the normal layer to the demand layer, and W8 (b) only renames the tail of a demand-layer
result and drops its exclusion, which enlarges its location set (§11.2 gives the difference to the model). The
interpreter has two types of micro edge with an any target (`interpreter.md` §4.1, I14):

* a SOURCE with an `[any]` target (`AssignMarkOnAnyAccessor`, Go `AnyAccessor`, `AssignMark` on an `AnyField`
  position; unconditional, conditional or conjunctive) has the target `[any-taint]`, with the Empty exclusion: a
  MUST, every location at or below the target position gets the mark (S15). It is a taint edge (§1). An end-fact
  action on an `AnyField` position gives the same target, `zero.$ (zeroMark) → P.[any-taint] (T)` (§4.9;
  `interpreter.md` I14);
* a PASS RULE with an `AnyField` target has the target `[any]`: a MAY. The rule does not know the field (`Map.put`
  writes one element, not every element).

A pass rule with an `AnyField` position on its PREMISE side is a rule error (S15; `interpreter.md` D33), so no forward
pass rule reads an `[any]` premise.

Under W6 every `[any]` result is in the demand layer: a may result never makes a record, and a vulnerability that rests
only on it is not confirmed (it stays a DEMAND entry, §8.10). In the backward run the reversal of a pass rule with an
`AnyField` target gives every result in the demand layer (§9.1), so no record goes through a may either. A result of a
taint edge is `[any-taint]`: it keeps the layer of its input, so a normal one is complete (§1), it can make a record
(§8.7 R1), and a vulnerability whose taint comes from an `[any]`-target source can be CONFIRMED (§4.9). A demotion
of §2.2 makes it `[any]` (W8 (b)): the field-limit cut, a cleaner `part` row other than the `atAndBelow` and `below`
rows one accessor below the fact, a may target, a demand input, and the must-record demotion (the list of §2.2). An
exclusion does not: the result carries it (W8 (a); §4.1).
Lean: the rule W6T (`AnyTaint.w6t`; with the exclusion `AnyTaintEx.w6tX`, which also drops the exclusion of a demoted
result): only the result of a statement micro edge with an `[any]` target that is not a taint edge goes to the demand
layer (§10.11). The text before F69 said that every `[any]` result is demand, so that no `[any]`-target source finding
is ever confirmed; F69 replaces it by W8 (`ap-history.md` F69).

W8 (a) holds for every normal any-tail conclusion of the spec closure `AnyTaintEx.D6X` (run 1 with the exclusion), for
every abstraction, under S15 and S10 (`AnyTaintExKinds.D6X_any_conc`; both hypotheses are necessary,
`AnyTaintSim.CexKinds.cex_taintConc`, `cex_bindNoAny`), so a FLOW edge has no `[any-taint]` conclusion
(`AnyTaintExKinds.D6X_flow_no_any_taint`, under S7, S10 and S15; no `W6.SummaryStar`) and no exclusion
(`AnyTaintExKinds.D6X_flow_no_excl`); with the hypotheses of `Kinds.kinds_D` this is the partition of run 1
(`AnyTaintExKinds.kinds_D6X`, `kinds_D6X_gen`). (Round 1, for `AnyTaint.D6T`: `AnyTaintSim.D6T_any_conc`, and
`D6T_flow_no_any_taint` also under `W6.SummaryStar`, which the run-1 policy has.)
Every edge conclusion of the refined runs is in the normal form of W8 (`AnyTaintExExact.D6X_wf`, `DRX_wf`). In the
concrete design every forward restricted run is concrete (`AnyTaintExExact.DRX_conc`, under C3;
`AnyTaintExCov.concX_all`), and so is the backward run (`BExact.DB_concrete`); every must-premise has an any tail
(`AnyTaintExExact.DRX_mustAny`) and a concrete mark (`AnyTaintExKinds.DRX_must_premise`, under C3, which
`AnyTaintEx.emitX_copies` gives). Since F72 the restricted runs are not concrete (§6.3). A must-premise still has an
any tail and a concrete mark: the F72 emission gives a must-premise only under a concrete pattern, and the FLOW form
of a `*` pattern has the `*` tail (argued, PENDING §11.2).

No exclusion is Universe (§1). A `*/Universe` conclusion needs a `$`-premise edge that acts on a `*`-tail fact. Every
`$`-premise micro edge has a concrete premise mark (S8: `zeroMark` for sources, `T` for conditional sources). A `*`-tail
fact has the mark `*` or `*∖X` (W2). So the mark gate never lets such a fact through (§4.1 step 4).
`Invariant.no_univ_star` proves it under S8, and `Invariant.final_star_legal` proves W2 for every derived fact.

### 2.4 The zero fact

The zero fact is `(zero, [], $, {}, zeroMark)`. Its one location is the zero location. An unconditional source rule
is a micro edge from the zero fact (a conditional source reads another fact, `interpreter.md` §4.1). The zero fact is
a premise like every other initial fact: a zero-to-fact edge is an edge with the premise set `{zero}`, in the normal or
the demand layer. A conjunction drops the zero fact from the union of its premise sets (§4.6). The cases where an
operation treats the zero fact in a special way:

* the statement transfer (§4.2) and the call (§3.5, §5.3): the zero fact passes, and it enters every resolved callee;
* the conjunction (§4.6): the union of the premise sets drops the zero fact;
* the cleaner (§4.7) and the type filter (§4.8): no cleaner is on the zero base, and a type filter on the zero base
  accepts the empty path (S11 (d));
* the sink check (§4.9): an unconditional sink triggers on the zero fact; the premise set `{zero}` is supported at a
  root and through the zero edges of the callers;
* the run-1 abstraction (§6.2), the emission (§6.3) and the start fact (§6.5): the zero fact gives the zero fact;
* the backward run (§9.2): the zero rules.

---

## 3. Denotation

### 3.1 Admitted continuations

`E.admits σ` is true if `σ = []`, or if the first accessor of `σ` is not in `E`.

| Tail | As a premise, the continuations `σ` that it covers | As a conclusion, the relation of the premise continuation `σ` to its own continuation `τ` |
|---|---|---|
| `*/E` | all `σ` that `E` admits | `τ = σ` and `E` admits `σ` (CORRELATED) |
| `[any]` | all `σ` | any `τ` (not correlated) |
| `[any-taint]/E` | all `σ` that `E` admits | any `τ` that `E` admits (not correlated) |
| `$` | `σ = []` | `τ = []` |

`[any-taint]` with the Empty exclusion has the denotation of `[any]`. Its exclusion `E` removes the continuations that
start with an accessor of `E`: on a premise it restricts `σ`, on a conclusion `τ`. It is not shared: the edge stays
uncorrelated. The layer gives the reading (W6, W8): a normal `[any-taint]/E` conclusion is exact (end-exact for a
must-premise, §1), so EVERY admitted location at or below its path carries the mark (a must); a demand `[any]`
conclusion over-approximates (a may). An `[any-taint]/E` premise covers the `σ` that `E` admits: its edges hold when
every admitted location of it carries the mark (a must-premise, §1). Lean: `AnyTaintEx.coversX` (a premise),
`coversFX` (a conclusion), `denX` (an edge).

### 3.2 Location set and edge relation

```
covers(i, l)  ⇔  l.base = i.base ∧ l.path = i.path ++ σ ∧ σ admitted by i.tail ∧ i.mark admits l.mark

den(i, f)(l0, l1)  ⇔  l0.base = i.base ∧ l1.base = f.base
                    ∧ i.mark admits l0.mark ∧ l1.mark = out(f.mark, l0.mark) ∧ f.mark passes l0.mark
                    ∧ ∃ σ τ. l0.path = i.path ++ σ ∧ l1.path = f.path ++ τ
                             ∧ σ admitted by i.tail ∧ (σ, τ) related by f.tail
```

with `admits(*, m)`, `admits(T, m) ⇔ m = T`, `admits(*∖X, m) ⇔ m ∉ X`, `out(*, m) = out(*∖X, m) = m`,
`out(T, m) = T`, and `passes(*∖X, m) ⇔ m ∉ X` (true for the other marks).

`den(i, f)(l0, l1)` reads: "the value at `l0` on method entry flows to `l1` at the statement of the edge". Lean:
`PFact.covers`, `den`, `MarkA.admits`, `MarkA.passes`. The tail of an `[any-taint]/E` side admits only what `E`
admits (§3.1; Lean: `AnyTaintEx.coversX`, `denX`).

Derived relations:

* `i` COVERS the fact `c` if every location of `c` is a location of `i` (Lean: `coversB`). It compares the marks: the
  marks of `c` must be a subset of the marks of `i` (Lean: `markSubB`).
* Two facts OVERLAP if they have a common location with the marks ignored (Lean: `overlapB`; with the exclusion of an
  `[any-taint]/E` fact, `AnyTaintEx.overlapX`). Every overlap test of this spec ignores the marks: the request match
  (§4.5), the conjunction store (§4.6), the sink check (§4.9) and the restriction (§6.4). Each of these places tests
  the marks separately. Each one reads the exclusion of an `[any-taint]/E` fact as part of its location set: an
  excluded location overlaps nothing.
* The restriction reads a demand pattern WITH ITS MARKS (§6.4; `ap-history.md` F71): the premise must lie inside `D-c`
  in its locations and in its marks (Lean: `Handoff.insideB`), and the mark of the conclusion must meet the mark of
  `D-p` (`Handoff.concMarkB`). The emission reads the entry pattern with its mark (`markMatchB`, §6.3; a `*∖X` entry
  pattern does not admit a mark of `X`; since F72 no pattern has the mark `*∖X`: the hand-off replaces it by `*`,
  §9.2). The demanded flows read both patterns with their marks (`PFact.covers`, §1).

### 3.3 Exclusion algebra and merge rules

* The edge exclusion is the union of what the model stores on the two sides (`SharedExcl.applyEdge_shared_excl`).
* MERGE RULE 1: two edges with the same premise, the same layer, the same exclusion and the same mark exclusion merge
  their conclusions (union).
* MERGE RULE 2: two edges with the same premise, the same layer and the same conclusion merge their exclusions by
  INTERSECTION: `admits(E1 ∩ E2, σ) = admits(E1, σ) ∨ admits(E2, σ)`. The union of the two relations is exact
  (`Subsume.merge_inter`). Two `[any-taint]/E1` and `[any-taint]/E2` conclusions with the same premise, layer, path
  and mark merge in the same way, into `[any-taint]/(E1 ∩ E2)` (argued, §11.2: the model has no merge rule for the
  exclusion of `[any-taint]`).
* MERGE RULE 2 FOR MARKS: two edges with the same premise, layer, exclusion and conclusion but mark exclusions `X1`, `X2`
  merge into one with `X1 ∩ X2`: a mark passes the merged edge if and only if it passes one of them
  (`Tree.rule2_mark`).
* A UNION of exclusions happens only along one derivation (a field write after another, a cleaner after another). A
  union across two different edges is FORBIDDEN: it removes pairs that one of the two edges has
  (`Subsume.union_loses_pairs`).

### 3.4 Reference types and tests

The reference forms of §4 and §6 use these types. A premise, a pattern and an added fact are a `Pattern`: a path fact
with the exclusion of its `*` tail or of its `[any-taint]` tail (W8). The implementation interns a premise as an
`InitialAp` (§7.1).

```kotlin
/** ANY is `[any]` (a may), ANY_TAINT is `[any-taint]` (a must, W8; forward runs only). ANY_TAINT can carry an
 *  exclusion; with the Empty exclusion the two have the same location set (§3.1). */
enum class Tail { STAR, ANY, ANY_TAINT, EXACT }

/** An any tail: `[any]` or `[any-taint]`. */
val Tail.isAny: Boolean get() = this == Tail.ANY || this == Tail.ANY_TAINT

/** A canonical (sorted, de-duplicated) set of concrete marks: the X of *∖X. Operations: `in`, `isSubsetOf`, `+`. */
class MarkSet(val ids: IntArray) {
    companion object { val EMPTY = MarkSet(IntArray(0)) }
}

/** A finite set of accessors: the exclusion E. Empty, or Concrete with a canonical non-empty set. No Universe (§1). */
sealed interface ExclusionSet {
    data object Empty : ExclusionSet
    class Concrete(val ids: IntArray) : ExclusionSet            // sorted, de-duplicated AccessorIdx, never empty
    fun admits(r: List<Accessor>): Boolean                     // r = [] or the first accessor of r is not in the set
    fun union(other: ExclusionSet): ExclusionSet
    fun isSubsetOf(other: ExclusionSet): Boolean
}

/** `p` is a prefix of this list. */
fun <T> List<T>.startsWith(p: List<T>): Boolean = size >= p.size && subList(0, p.size) == p

/** The static base S (`ClassStatic`, §4.10). */
val STATIC: AccessPathBase = AccessPathBase.ClassStatic

/** `*` is Star(MarkSet.EMPTY). A premise never has excluded marks. Since F72 a demand pattern never has them either:
 *  the hand-off replaces `*∖X` by `*` (§9.2). (Under F71 an entry pattern from a run-1 summary conclusion kept them,
 *  and the emission and the restriction read them exactly, §6.3, §6.4.) An added fact and a conclusion can have
 *  them. */
sealed interface MarkSlot {
    data class Star(val excluded: MarkSet) : MarkSlot     // *  or  *∖X
    data class Concrete(val mark: TaintMark) : MarkSlot   // T; the zero mark is a concrete mark
}

data class PathFact(val base: AccessPathBase, val path: List<Accessor>, val tail: Tail, val mark: MarkSlot)

/** A premise, a demand pattern or an added fact: a fact with its own exclusion (Empty unless the tail is STAR, or
 *  ANY_TAINT in a forward run: the exclusion of `[any-taint]/E`, W8). */
data class Pattern(val fact: PathFact, val exclusion: ExclusionSet)

/** A demand pattern (§1): the entry pattern D-c and the exit pattern D-p (null: the demand does not reach the exit). */
data class DemandPattern(val entry: Pattern, val exit: Pattern?)

/** §3.1: the tail admits the continuation r. */
fun tailAdmits(tail: Tail, exclusion: ExclusionSet, r: List<Accessor>): Boolean = when (tail) {
    Tail.STAR, Tail.ANY_TAINT -> exclusion.admits(r)   // r = [] or the first accessor of r is not excluded
    Tail.ANY -> true
    Tail.EXACT -> r.isEmpty()
}
fun Pattern.tailAdmits(r: List<Accessor>) = tailAdmits(fact.tail, exclusion, r)

/** The marks of `c` are a subset of the marks of `i` (Lean: markSubB). */
fun markSub(i: MarkSlot, c: MarkSlot): Boolean = when (i) {
    is MarkSlot.Star -> when (c) {
        is MarkSlot.Star -> i.excluded.isSubsetOf(c.excluded)   // *∖X ⊇ *∖Y if and only if X ⊆ Y; * ⊇ every abstract mark
        is MarkSlot.Concrete -> c.mark !in i.excluded
    }
    is MarkSlot.Concrete -> c is MarkSlot.Concrete && c.mark == i.mark
}

/** Tail inclusion at the same path: every continuation of c is a continuation of i (Lean: tailSubB). */
private fun tailSub(i: Pattern, c: Pattern): Boolean = when (i.fact.tail) {
    Tail.ANY -> true
    Tail.EXACT -> c.fact.tail == Tail.EXACT
    Tail.STAR, Tail.ANY_TAINT -> when (c.fact.tail) {       // the exclusion of i must exclude only what c excludes
        Tail.EXACT -> true
        Tail.ANY -> i.exclusion == ExclusionSet.Empty
        Tail.STAR, Tail.ANY_TAINT -> i.exclusion.isSubsetOf(c.exclusion)
    }
}

/** `i` covers every location of `c`, marks included (Lean: coversB). */
fun covers(i: Pattern, c: Pattern): Boolean {
    if (i.fact.base != c.fact.base || !markSub(i.fact.mark, c.fact.mark)) return false
    val p = i.fact.path
    val q = c.fact.path
    if (!q.startsWith(p)) return false
    return if (q.size == p.size) tailSub(i, c) else i.tailAdmits(q.drop(p.size))
}

/** A common location, marks ignored (Lean: overlapB). */
fun overlap(a: Pattern, b: Pattern): Boolean {
    if (a.fact.base != b.fact.base) return false
    val p = a.fact.path
    val q = b.fact.path
    return when {
        q.startsWith(p) -> a.tailAdmits(q.drop(p.size))
        p.startsWith(q) -> b.tailAdmits(p.drop(q.size))
        else -> false
    }
}

/** §4.3, run 1; a record also in every later run (with inside, R4): the premise j covers the added fact a
 *  (Lean: applicable). A premise with an any tail needs a fact with an any tail. Run 1 has no `[any-taint]`
 *  premise; a must record that applies by applicable only gives a demand result (§4.3). */
fun applicable(j: Pattern, a: Pattern): Boolean =
    covers(j, a) && (!j.fact.tail.isAny || a.fact.tail.isAny)

/** §4.3, the summaries of a restricted run: j lies inside a as locations, and markSub(j, a) (Lean: satI; with the
 *  exclusions of `[any-taint]/E` facts and premises AnyTaintEx.satX). A normal result of a must-premise (an
 *  `[any-taint]` j) needs this test (Lean: SatInsideX). Since F72 a FLOW premise (the mark `*`) also applies by
 *  applicable (§4.3; `satisfies` of §6.3). */
fun inside(j: Pattern, a: Pattern): Boolean =
    covers(a.copy(fact = a.fact.copy(mark = MarkSlot.Star(MarkSet.EMPTY))), j) && markSub(j.fact.mark, a.fact.mark)
```

An empty Concrete `ExclusionSet` must not exist (return `Empty`). `Accessor` is an interned accessor, and
`AccessorIdx` is its intern id (the accessor interning of today, §7.6): `admits(r)` reads the `AccessorIdx` of the
first accessor of `r` and tests it against `ids`. Keep the excluded accessors as a CANONICAL (sorted, de-duplicated)
`IntArray` of `AccessorIdx`; the same for `MarkSet`. The edge trees are keyed by both, so two equal sets
must be equal values. Because the sets of accessors and marks are unbounded (§1), these tests are exact: `covers`,
`overlap` and `cleanPos` (§4.7) need no "all accessors" or "all marks" case. The tests read `ANY_TAINT` with its
exclusion as a location set, as `*/E` reads its exclusion: with the Empty exclusion it is `ANY` (§3.1). So `covers`,
`overlap`, `applicable` and `inside` read the exclusion of an `[any-taint]/E` fact or premise (Lean:
`AnyTaintEx.coversX`, `overlapX`, `satX`). The operations that read the difference of the two any tails (the layer)
say so (§4.1, §4.3, §4.6, §4.9, §6.3).

### 3.5 Concrete semantics

The theorems compare the analysis with this semantics. It is the program that the micro edges describe (S1). It is
alias-free and location-level. Lean: `Basic.lean` (`Stmt.step`, `Flow`, `Reach`). `Flow` is this semantics without
the override of the summary rewriter (REFERENCE SEMANTICS OF RULES, below).

* STATEMENT STEP. A statement with the touched bases `B` and the micro edges `M` moves the location `l` to `l'` if
  `l.base` is not in `B` and `l' = l`, or if a micro edge `e` of `M` relates them: `den(e)(l, l')` (§3.2). So an
  untouched base keeps its locations, and a touched base keeps only what a micro edge gives.
* CLEANER STEP. A cleaner `(x, p, reach, mark)` (§4.7) removes the location `l` if `l.base = x`, the cleaner removes
  the mark of `l` (its own mark, or every mark), and `l.path = p ++ σ` with `σ` in the reach: `exact`: `σ = []`;
  `below`: `σ ≠ []`; `atAndBelow`: every `σ`. Every other location passes unchanged (Lean: `Cleaner.cleansB`).
* FILTER STEP. A type filter `filter(b, may)` (§4.8) removes a location on the base `b` whose path `may` rejects. Every
  other location passes unchanged.
* CALL STEP. A call has the touched caller bases, the binding edges into the callee and the binding edges back. A
  caller location on an untouched base passes over the call. A location `l` on a touched base goes into the callee
  through a binding edge `e1` (`den(e1)(l, l1)`), flows in the callee from the entry location `l1` to an exit location
  `l2`, and comes back through a binding edge back `e2` (`den(e2)(l2, l3)`) as `l3`. For the call `r = m(a1..an)` the
  touched caller bases are the arguments, the result and the static base; the bindings into the callee are
  `ai.* → argi.*`, `S.* → S.*` and `zero.* → zero.*`; the bindings back are `argi.* → ai.*`, `ret.* → r.*` and
  `S.* → S.*`. The interpreter gives the bindings of each call kind (`interpreter.md` §3). The zero base is never a
  touched base of a call: the zero location passes over every call, and it also enters the callee through
  `zero.* → zero.*`. A binding has the premise mark `*` (S10), so the zero binding has the `*` form, and the zero fact
  is at or below its premise. Lean: `Call`, `Flow.pass`, `Flow.call`.
* FLOW. A flow of the method `M` from the entry location `l0` is a chain of these steps from the entry node of `M` to a
  node `n`. It ends at the location `l`: the value at `l0` on method entry is at `l` at `n` (Lean: `Flow`). A call step
  contains the flow of the callee.
* VULNERABILITY AND WITNESS. A sink pattern `s` with the concrete mark `T` at the node `n` is REACHED if the zero
  location of a root flows to a location at `n` that `s` covers. The flow can go down into callees: from a location at a
  call node, through a binding edge into the callee, then a flow in the callee (Lean: `Reach`). Such a chain is a
  WITNESS of the vulnerability. A vulnerability is REAL if it has a witness.
* REFERENCE SEMANTICS OF RULES. The interpreter rules are read in this semantics as follows (S1). A source or a sink
  fires with every negated mark literal true. A pass rule has no mark literal (a pass rule with a mark literal is a
  rule error that is not rejected, `interpreter.md` §1.3: it fires without its mark literals, §11.1). A user-defined
  rule that the summary rewriter selects REPLACES the callee flow for its marks at its positions (§4.7). The override
  is not in `Flow`. The theorems read the program in which each call with selected rules is wrapped: the callee flow,
  then the cleaner `(P, exact, T)` for each selected rule, mark and position (for an `AnyField` position
  `(P, atAndBelow, T)` for a source, `(P, below, T)` for a cleaner, §4.7), on the callee results; the source
  results and the end facts of the call bypass the wrapper (argued, §11.2). An end-fact action is a source at its
  sink statement that fires when its sink fires (§4.9). The model has none (§11.2). A micro edge with an any target
  gives the value of its premise location to EVERY location at or below its target path (§3.2). For a source with an
  `[any]` target this is the rule itself (a must, S15). For a pass rule with an `AnyField` target it over-approximates
  the callee, which writes some field: W6 keeps its results in the demand layer (§2.3), and the backward run keeps
  every result of its reversal in the demand layer (§9.1). A conjunction of literals on
  different facts is path-insensitive: each literal can hold on its own path. Its semantics is the SUPPORT semantics: a location is tainted
  at a node together with the list of entry locations that its derivation needs, and a vulnerability witness is a tree
  (§4.6; Lean: `ND.TaintN`, `ND.ReachAll`). "Real" in this spec always means real in this reference semantics.

---

## 4. Operations and primitives

### 4.1 The core computation: delta-concat

`concat(c, from →_E to)` computes the result of an edge `from → to` with the edge exclusion `E` on the conclusion `c` of
a current edge. `Ec` is the exclusion of the edge of `c`. `E` is the one exclusion of the edge (§2.2): the shared
exclusion of a correlated edge, or the exclusion of a `*` premise of an uncorrelated edge, else Empty. Every edge
application of the analysis uses `concat`: a micro edge (a statement edge or a call binding, §4.2), a summary edge
(§4.3), a summary with several premises (§4.6), a record and a reversed record (§8.7 R3, R4). Lean: `applyEdge`.

The special cases of `concat`:

* run 1 only: the mark request (step 4) and the static exception (after step 2);
* a restricted run: the request row of step 4 gives NO fact and NO request (F72; §4.5). A fact with an abstract mark
  (a fact of a FLOW premise, §6.3) can meet a concrete premise mark; the flow of a concrete mark comes from a concrete
  demand pattern. (Before F72 the row never occurred, because every fact of a restricted run was concrete, and the
  implementation asserted it.);
* `concat` does not apply the field limit. The operation that calls it does (§4.4).

Preconditions. `concat` ASSERTS them; an edge that breaks one is a bug of the interpreter or of the analyzer:

* the premise mark is `*` or `T`, never `*∖X` (S7);
* a concrete target mark needs a concrete premise mark (S7);
* a `$` premise has a concrete mark (S8);
* a `$` premise has no `*` target (S8);
* a `$` target needs a concrete premise mark (S8, `Kinds.ExactTargetConc`);
* an `[any-taint]` target or premise has a concrete mark (W8, S15). (With S7 an `[any-taint]` target then has a
  concrete premise mark.)

So a `$` premise meets only facts with a concrete mark: on a `*`-tail fact (mark `*` or `*∖X`, W2) the mark gate gives
a request or nothing (step 4). A summary edge meets the preconditions too: an initial fact never has `*∖X` (§2.2); a
`$` initial fact is the zero fact, an answer or an emission, all with a concrete mark
(canonical abstract entry patterns have `[any]` under §6.1); a concrete premise has only
concrete conclusions (`Coverage.edge_conc`), which have no `*` tail (W2); and a `*` premise has only FLOW conclusions,
with an abstract mark and no `$` or `[any-taint]` tail (§7.2). The conclusion kinds of §7.2 rest on the second, the
fifth and the last precondition.

A micro edge with an `[any]` target is a may (a pass rule); a micro edge with an `[any-taint]` target is a must (a
taint edge, with the Empty exclusion). The interpreter gives the `[any-taint]` target only to the sources (S15). An
`[any-taint]` premise is the premise of a summary edge or a record of a forward restricted run (a must-premise, §1),
with its exclusion; no micro edge has one (S15), and the backward run has none (W8 (d)). An `[any-taint]` target with
a non-empty exclusion is the conclusion of a summary edge or a record (§4.3).

Step 1 — base. If `c.base ≠ from.base`, the result is empty.

Step 2 — position:

* `below r`: `c.path = from.path ++ r`. The fact is AT OR BELOW the premise.
* `above r`: `from.path = c.path ++ r`, `r ≠ []`. The fact is ABOVE the premise: NOT STRONG ENOUGH.
* `apart`: neither. The result is empty.

A fact in the case `below` is strong enough only if `applicable(premise, fact)` holds (§1, §4.3). Example: an `[any]`
fact at a `*/E` premise with `E ≠ {}` is in the case `below r = []`, but the premise does not cover it.

STATIC EXCEPTION (run 1 only, statement micro edges only; the rule is §4.10 item 1). The exception applies if all
these conditions are true:

1. The edge `i → c` in the method `m` is an IDENTITY STATIC `*` EDGE (§4.10): its premise is `i = (S, q, */E0, *)`,
   and `c` is `(S, q, */Ec, *)` or `(S, q, */Ec, *∖X)` at the same path `q`, in the normal layer.
2. `q` is the root path `[]` or a class `[<C>]`.
3. The premise `from` of the micro edge is on `S`, in the case `above r` (`from.path = q ++ r`, `r ≠ []`).
4. `Ec` admits `r`.

Then the result is no fact and no mark request. The step raises the position request `(m, i, p')`, where `p'` is
`from.path` cut to at most two accessors. A restricted run, a call binding and a summary edge have no exception. A
statement micro edge at a call (a source of the rule statement of the call, a pass rule of an unresolved callee, the
`RemoveAllMarks` kill on `S`) acts on the bound fact or on the added fact. Its edge is then the caller edge, so `m` is
the caller (§5.3 steps 3 and 4).

Step 3 — overlap and result shape.

Case `below r`. The premise must admit `r`: a `*` premise if `E` admits `r`; a `$` premise if `r = []`; an `[any]`
premise for every `r`; an `[any-taint]/Ej` premise (a must-premise of a summary or a record) if `Ej` admits `r`. For a
`*` target and `r ≠ []`, `E` must also admit `r` (row 1). This second test applies even when the premise has its
own `Ej`. Both tests precede the mark gate. `Et` is the exclusion of an `[any-taint]` target (a
summary conclusion `[any-taint]/Et`; Empty for a micro edge). Then:

| `to.tail` | `r` | `c.tail` | result path | result tail | to the demand layer |
|---|---|---|---|---|---|
| `*` | `≠ []` (`E` admits `r`) | any | `to.path ++ r` | `c.tail` (with `Ec`; an `[any-taint]/Ec` fact keeps `Ec` at the new path end) | no |
| `*` | `[]` | `$` | `to.path` | `$` | no |
| `*` | `[]` | `*/Ec` | `to.path` | `*/(Ec ∪ E)` | no |
| `*` | `[]` | `[any]` | `to.path` | `[any]` | yes (W6) |
| `*` | `[]` | `[any-taint]/Ec` | `to.path` | `[any-taint]/(Ec ∪ E)` (the EXCLUSION EDGE, W8) | no |
| `[any]` | any | any | `to.path` | `[any]` | yes (W6: a may) |
| `[any-taint]/Et` | any | any | `to.path` | `[any-taint]/Et`; `[any]` in the demand layer (W8) | no |
| `$` | any | any | `to.path` | `$` | no (an `[any]` `c` is in the demand layer already, W6) |

The `$` and `[any-taint]` targets require a concrete premise mark (S8, S7 and W8). A legal `*`-tail fact has an
abstract mark (W2), so it cannot pass that mark gate: it gives no fact and, in run 1, may request the premise mark
(step 4). Thus these below-case targets add no demand flag. May `[any]` targets force demand (W6).
The exclusion edge (the keep edge `x.* →_{f} x.*` of a strong write, a `*/E` summary or record with `E ≠ {}`) gives an `[any-taint]` fact its
exclusion: the result is `[any-taint]/(Ec ∪ E)` in the layer of `c`, not `[any]` (W8; Lean `AnyTaintEx.Vec.setter_keep`;
the round-1 rule demoted it, `setter_keep_base`). In the row `*`, `≠ []`, the exclusion `Ec` restricts the
continuation after `c.path`, not `r`, so the case `below` does not test `r` against `Ec`
(`AnyTaintEx.Vec.below_keeps_loc`: such a test would lose a real flow).
The gated base reduction is checked in `CurrentSimplifications.LostCorr.applySimple_eq` and
`applySimple_may_eq`; geometry still precedes the mark gate and requests.

Case `above r`. The premise decides WHETHER the edge applies: the tail of `c` must admit `r` (`*/Ec` and
`[any-taint]/Ec`: `Ec` admits `r`; `[any]`: yes; `$`: no overlap). If it does not, the edge gives no result and no
request. The target decides WHAT comes out. For a `*` or an `[any]` fact `c`: `(to.path, $)` if `to.tail = $`, else
`(to.path, [any])`, always in the demand layer: a `*` fact loses its correlation, and an `[any]` fact is in the demand
layer already (W6). For an `[any-taint]/Ec` fact `c` whose `Ec` admits `r`, EVERY location at or below
`c.path ++ r` carries the mark, so every premise location carries it and nothing is lost. The result is in the layer
of `c`, unless the table says demand:

| `to.tail` | result | to the demand layer |
|---|---|---|
| `$` | `(to.path, $)` | no |
| `[any-taint]/Et` | `(to.path, [any-taint]/Et)` | no |
| `*` | `(to.path, [any-taint]/E)`: the exclusion `E` of the EDGE (§2.2), not `Ec` | no |
| `[any]` | `(to.path, [any])` | yes (W6: a may) |

So a read `y = c.P.g` (the micro edge `x.P.g.* → y.*`) on `(x, P, [any-taint], Ec, T)` gives `(y, ., [any-taint], {},
T)` if `g ∉ Ec`, and nothing if `g ∈ Ec` (`AnyTaintEx.Vec.read_admitted`, `read_excluded`; the check is necessary:
`AnyTaintExExact.CexAbove.cex_above`, a setter and then a read of the written field). (Lean:
`AnyTaintEx.applyEdgeX`: the base `belowCase` and `aboveCase` for a fact of the kind `.any` in the normal layer, the
exclusion rules `annX` and the layer rule `layerX`, then `AnyTaintEx.w6tX` for a may target. The rows of the
exclusion are exact: `AnyTaintExExact.keep_row_exact`, `above_row_exact`, `below_row_exact`. §11.2 gives the
correspondence.)

Step 4 — mark gate. The premise mark `from.mark` (never `*∖X`) against the fact mark `c.mark`:

| `from.mark` | `c.mark` | result |
|---|---|---|
| `*` | any | apply |
| `T` | `T` | apply |
| `T` | `T' ≠ T` | empty |
| `T` | `*∖X`, `T ∈ X` | empty (the mark was cleaned) |
| `T` | `*` or `*∖X` with `T ∉ X` | NO fact; in run 1 the request `T` on the premise of `c` (§4.5). A restricted run: NO fact and NO request (F72, §4.5) |

The gate comes after step 3: an apart fact, or a fact that the premise or `E` does not admit, gives no request. Lean:
`markGate`.

Step 5 — result mark `comp(to.mark, c.mark)`:

| `to.mark` | `c.mark` | result mark |
|---|---|---|
| `*` | `m` | `m` |
| `T` | any | `T` |
| `*∖X` | `*` | `*∖X` |
| `*∖X` | `*∖Y` | `*∖(X ∪ Y)` |
| `*∖X` | `T` | `T` if `T ∉ X`; NO fact if `T ∈ X` |

A `*∖X` target occurs only on a summary conclusion (a callee with a cleaner). Lean: `markComp`.

Step 6 — layer and normal form. The result is in the demand layer if `c` is, if the applied edge is (a summary edge,
§4.3), or if step 3 says so. Then:

* W6: a result with the `[any]` tail is in the demand layer. Every result of a may `[any]` target has the `[any]`
  tail (step 3).
* W8: a result with the `[any-taint]` tail in the demand layer becomes `[any]`, with the same path and mark and the
  Empty exclusion. A result with the `[any-taint]` tail in the normal layer stays `[any-taint]`, with its exclusion.
* W2: a result with the `*` tail and (a concrete mark, or the demand layer) becomes uncorrelated: `[any]` with the
  Empty exclusion, in the demand layer.

The step only enlarges the fact (`CoreAux.norm_sound`, `Invariant.demand_of_any_ok`); W8 changes the name of the
tail and drops the exclusion, so it only enlarges the location set (§3.1). The step does not apply the field limit
(§4.4). Lean: W6 and W8 together are the rule W6T (`AnyTaint.w6t`, `transferT`; with the exclusion `AnyTaintEx.w6tX`,
`transferX` and the normal form `normX`): the model keeps the kind `.any` and raises only the layer of a may result
(§11.2).

Theorem `Core.applyEdge_sound` (THE CORE LEMMA): `concat` covers the composition of the fact relation and the edge
relation, or raises the request for the premise mark. This holds in both cases: at or below the premise, and above it.
With the exclusion of `[any-taint]` (W8): `AnyTaintExCov.applyEdgeX_sound` (the result
exclusion admits every reached end location, `annX_admits`), and a normal result is exact on the admitted locations
(`AnyTaintExExact.applyEdgeX_exact`; its side conditions are necessary, `CexSideConditions.cex_htx`, `cex_hfx`).

Reference form (the vector tests of §13 compare the tree implementation with it). It uses the types of §3.4. The edge
carries its one exclusion:

```kotlin
/** A path edge `from → to` with its ONE exclusion (W1: Empty if no side has the STAR tail). `fromExclusion` and
 *  `toExclusion` are the own exclusions of an ANY_TAINT side (W8): a must-premise `[any-taint]/Ej` and a summary
 *  conclusion `[any-taint]/Et`. They are Empty for a micro edge and for every other tail. */
data class PathEdge(val from: PathFact, val to: PathFact, val exclusion: ExclusionSet,
                    val fromExclusion: ExclusionSet = ExclusionSet.Empty,
                    val toExclusion: ExclusionSet = ExclusionSet.Empty)

/** The current conclusion: the fact, the exclusion of its edge (`*`: the shared exclusion; ANY_TAINT: its own
 *  exclusion E of `[any-taint]/E`, W8; Empty otherwise), the layer of its edge. */
data class Conclusion(val fact: PathFact, val exclusion: ExclusionSet, val demand: Boolean)

private fun PathEdge.premiseAdmits(r: List<Accessor>): Boolean = when (from.tail) {
    Tail.EXACT -> r.isEmpty()
    Tail.ANY_TAINT -> fromExclusion.admits(r)   // a must-premise: Ej
    else -> exclusion.admits(r)        // `*`: E; `[any]`: E is Empty unless the target is `*` (row 1)
}

private class Shape(val path: List<Accessor>, val tail: Tail, val exclusion: ExclusionSet, val demand: Boolean)

private fun below(c: Conclusion, edge: PathEdge, r: List<Accessor>): Shape? {
    if (!edge.premiseAdmits(r)) return null
    val to = edge.to
    if (to.tail == Tail.STAR && r.isNotEmpty() && !edge.exclusion.admits(r)) return null
    return when (to.tail) {
        Tail.STAR -> when {
            r.isNotEmpty() -> Shape(to.path + r, c.fact.tail, c.exclusion, demand = false)   // keeps Ec (W8)
            c.fact.tail == Tail.EXACT -> Shape(to.path, Tail.EXACT, ExclusionSet.Empty, false)
            c.fact.tail == Tail.ANY -> Shape(to.path, Tail.ANY, ExclusionSet.Empty, demand = true)   // W6
            else -> Shape(to.path, c.fact.tail, c.exclusion.union(edge.exclusion), false)  // `*/Ec`, or the exclusion
        }                                                                                 // edge on `[any-taint]/Ec` (W8)
        Tail.ANY -> Shape(to.path, Tail.ANY, ExclusionSet.Empty, demand = true)               // W6: a may
        Tail.ANY_TAINT, Tail.EXACT -> Shape(to.path, to.tail,                                  // a must, or `$`
            if (to.tail == Tail.ANY_TAINT) edge.toExclusion else ExclusionSet.Empty, false)    // Et (W8); concrete gate
    }
}

private fun above(c: Conclusion, edge: PathEdge, r: List<Accessor>): Shape? {
    val admitted = when (c.fact.tail) {
        Tail.STAR, Tail.ANY_TAINT -> c.exclusion.admits(r); Tail.ANY -> true; Tail.EXACT -> false
    }
    if (!admitted) return null                     // no common location: no result and no request
    val to = edge.to
    if (c.fact.tail != Tail.ANY_TAINT) {           // a `*` fact loses its correlation; an `[any]` fact is demand (W6)
        val tail = if (to.tail == Tail.EXACT) Tail.EXACT else Tail.ANY
        return Shape(to.path, tail, ExclusionSet.Empty, demand = true)
    }
    return when (to.tail) {                        // every admitted location of c carries its mark: nothing is lost
        Tail.EXACT -> Shape(to.path, Tail.EXACT, ExclusionSet.Empty, demand = false)
        Tail.ANY_TAINT -> Shape(to.path, Tail.ANY_TAINT, edge.toExclusion, demand = false)    // Et
        Tail.ANY -> Shape(to.path, Tail.ANY, ExclusionSet.Empty, demand = true)               // W6: a may
        Tail.STAR -> Shape(to.path, Tail.ANY_TAINT, edge.exclusion, demand = false)          // the edge exclusion (W8)
    }
}

sealed interface EdgeOutcome {
    data object None : EdgeOutcome
    data class Fact(val conclusion: Conclusion) : EdgeOutcome
    data class Request(val mark: TaintMark) : EdgeOutcome                  // run 1 only (§4.5)
    data class PositionRequest(val path: List<Accessor>) : EdgeOutcome     // run 1 only (§4.10 item 1)
}

/** The path `q` is the root path `[]` or a JVM class position `[<C>]`. `isClass` is true only for the class accessor
 *  `<C>` of a JVM class. A Go global `[<G>]` is a static FIELD, not a class (§4.10). */
fun rootOrClass(q: List<Accessor>): Boolean = q.isEmpty() || (q.size == 1 && q[0].isClass)

/** `edgeDemand`: the layer of the applied edge; true for a demand-layer summary edge (§4.3), false for a micro edge.
 *  `staticIdentity`: true only in run 1, for a statement micro edge, when the edge of `c` is an identity static `*`
 *  edge (the static exception after step 2). For a statement micro edge at a call, `c` is the bound or the added
 *  fact, and its edge is the caller edge (§5.3). `restricted`: true in a restricted run. */
fun concat(c: Conclusion, edge: PathEdge, edgeDemand: Boolean = false,
           staticIdentity: Boolean = false, restricted: Boolean = false): EdgeOutcome {
    val from = edge.from
    check(from.mark !is MarkSlot.Star || from.mark.excluded == MarkSet.EMPTY)          // S7
    check(edge.to.mark !is MarkSlot.Concrete || from.mark is MarkSlot.Concrete)          // S7
    check(from.tail != Tail.EXACT || from.mark is MarkSlot.Concrete)                    // S8
    check(from.tail != Tail.EXACT || edge.to.tail != Tail.STAR)                         // S8
    check(edge.to.tail != Tail.EXACT || from.mark is MarkSlot.Concrete)                 // S8 (ExactTargetConc)
    check(edge.to.tail != Tail.ANY_TAINT || edge.to.mark is MarkSlot.Concrete)          // W8, S15
    check(from.tail != Tail.ANY_TAINT || from.mark is MarkSlot.Concrete)                // W8
    if (c.fact.base != from.base) return EdgeOutcome.None
    val p = from.path
    val q = c.fact.path
    val isAbove = p.size > q.size && p.startsWith(q)
    val shape = when {
        q.startsWith(p) -> below(c, edge, q.drop(p.size))
        isAbove -> above(c, edge, p.drop(q.size))
        else -> null
    } ?: return EdgeOutcome.None
    if (staticIdentity && from.base == STATIC && isAbove && rootOrClass(q)) {   // the static exception (§4.10)
        check(!restricted)
        return EdgeOutcome.PositionRequest(p.take(2))                          // cut to the static field or class
    }
    val premiseMark = from.mark
    if (premiseMark is MarkSlot.Concrete) {                                   // step 4: the mark gate
        when (val cm = c.fact.mark) {
            is MarkSlot.Concrete -> if (cm.mark != premiseMark.mark) return EdgeOutcome.None
            is MarkSlot.Star -> {
                if (premiseMark.mark in cm.excluded) return EdgeOutcome.None
                if (restricted) return EdgeOutcome.None                       // F72: no request after run 1 (§4.5)
                return EdgeOutcome.Request(premiseMark.mark)
            }
        }
    }
    val mark = compose(edge.to.mark, c.fact.mark) ?: return EdgeOutcome.None  // step 5
    return EdgeOutcome.Fact(normalize(PathFact(edge.to.base, shape.path, shape.tail, mark),
        shape.exclusion, c.demand || edgeDemand || shape.demand))
}

fun compose(to: MarkSlot, c: MarkSlot): MarkSlot? = when (to) {
    is MarkSlot.Concrete -> to
    is MarkSlot.Star -> when (c) {
        is MarkSlot.Star -> MarkSlot.Star(to.excluded + c.excluded)
        is MarkSlot.Concrete -> if (c.mark in to.excluded) null else c
    }
}

/** Step 6: W6, W8, then the normal form W2. */
fun normalize(f: PathFact, exclusion: ExclusionSet, demand: Boolean): Conclusion = when {
    f.tail == Tail.ANY -> Conclusion(f, ExclusionSet.Empty, demand = true)                                  // W6
    f.tail == Tail.ANY_TAINT -> {
        check(f.mark is MarkSlot.Concrete)                                                                  // W8 (a)
        if (demand) Conclusion(f.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)                // W8 (b)
        else Conclusion(f, exclusion, demand = false)                                    // keeps E (W8 (a))
    }
    f.tail != Tail.STAR || (f.mark is MarkSlot.Star && !demand) -> Conclusion(f, exclusion, demand)
    else -> Conclusion(f.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)                         // W2
}
```

### 4.2 Apply a micro edge

A micro edge is an edge of a statement summary or a call binding edge (caller base to callee base, callee exit base to
caller base; `interpreter.md` §2, §3). A callee summary edge is not a micro edge: it applies only to an added fact that
satisfies its premise (§4.3). A micro edge applies to EVERY fact on a touched base that overlaps its premise: both cases
of §4.1. Example: the fact `(a, ., *, {}, *)` and the micro edge `(a, .f, *) → (b, ., *)` of `b = a.f` give
`(b, ., [any], {}, *)` in the demand layer (`Cases.lean`). Exceptions: the static rule of run 1 (§4.1 static exception,
§4.10 item 1), and the alias edges of a call result (this spec §5.3 step 5; `interpreter.md` §3.8 AC3 to AC5).

The statement transfer of one conclusion `c` of the edge `(i, layer) → c` in the method `m` (Lean: `transfer`;
`interpreter.md` §2.1 gives the same steps for the IR):

1. No liveness drop. A fact on a local that is dead at the statement goes on (`interpreter.md` §2.1). It reaches no
   use, so it costs work only.
2. If `c.base` is not touched: the result is `c` (`Sequent.Unchanged`).
3. Apply the operand type filters of `c.base` (§4.8; `interpreter.md` §2.1 step 3). A rejected fact is dropped.
4. Apply `concat(c, e)` (§4.1) for every micro edge `e` of the statement that is not conjunctive. The result is the
   union of the outcomes. A `Fact` outcome is a result fact. A `Request` outcome gives no fact; it raises the request
   `(m, i, T)` (run 1, §4.5). A `PositionRequest` outcome gives no fact; it raises the position request `(m, i, p)`
   (run 1, §4.10). A touched base keeps only what an edge gives again. That is the KILL.
5. Apply the lhs type filter to the results on the lhs (§4.8; `interpreter.md` §2.1 step 5).
6. Apply the field limit to each result (§4.4).

A conjunctive micro edge (§4.6) does not go through step 4: the conjunction store (§4.6, §8.9) applies it.

The special cases of the transfer:

* RUN 1 ON THE STATIC BASE (the rule is §4.10 item 1; the test is §4.1, static exception). Run 1 only, statement micro
  edges only: when the edge of `c` is an identity static `*` edge at the root `[]` or at a class `[<C>]`, a micro edge
  whose premise path `p` lies strictly below `c` (the exclusion of `c` admits the rest) gives no fact and no mark
  request; it raises the position request for `p` cut to at most two accessors. Below a static field the ordinary rules
  apply. The kill stays: the touched bases keep only what the other micro edges give. See the last two rows of the
  table below.
* THE ZERO FACT. A statement that does not touch the zero base passes the zero fact unchanged (step 2). A statement
  that touches it has the micro edge from the zero fact to the zero fact (S11 (d)), so the zero fact passes. The read
  sources and the exit sources of the interpreter fire on the zero fact (`interpreter.md` §4.4, §4.7).
* A RESTRICTED RUN has no request and no position request (§6.1). Since F72 a `Request` outcome of step 4 does not
  occur there: where run 1 raises a mark request, a restricted run gives nothing (§4.1 step 4, §4.5).
* THE BACKWARD RUN applies the reversed statement (§9.2) with steps 2, 4 and 6 only: no type filter. (Without a filter
  the backward run only keeps more requirements, so this is sound.)

Every micro edge (a statement edge, with its alias edges, or a call binding edge) is precise and complete (S1, S2).
The field limit never applies to a micro edge: a micro edge keeps its full paths, of any length. The analyzer applies
the limit to the RESULT, after it applies the micro edge to the propagated edge (§4.4; Lean: `transfer` limits the
results, and `Program.WF` puts no bound on a micro edge).

A micro edge has NO LAYER. The layer belongs to the propagation edge, and only the AP operations of §2.2 change it. The
interpreter sets no layer. A negated mark literal counts as true for a source and a sink (the reference semantics,
§3.5). This is the expected over-approximation of a path-insensitive engine, as the conjunction is (§4.6). Positive
literals on different facts make a conjunction for a source (§4.6) and a conjunctive sink (§4.9). A pass rule has no
mark literal (`interpreter.md` §4.2). Only an unconditional cleaner applies (§4.7, `interpreter.md` §4.2).

The cases below are checked by `decide` in `Cases.lean`. The two static rows follow §4.10. The model checks the read
row (`Statics.gen_read_DS`, `gen_read_sreq`); the write row follows from §4.10 item 1 and is not checked separately:

| Statement | Input fact | Result | Layer |
|---|---|---|---|
| `a = b.f` | `(b, .f, *, E, *)` | `(a, ., *, E, *)` | normal |
| `a = b.f` | `(b, ., *, E, *)`, `f ∉ E` | `(a, ., [any], {}, *)` | demand |
| `a = b.f` | `(b, ., *, {f}, *)` | nothing for `a` | |
| `a = b.f` | `(b, ., [any], {}, *)` | `(a, ., [any], {}, *)` | demand (the input is demand by W6; the model copies the layer) |
| `a = b.f` | `(b, ., $, {}, T)` | nothing for `a` | |
| `a = b.f` | `(b, ., [any-taint], E, T)`, `f ∉ E` | `(a, ., [any-taint], {}, T)` | normal (the case `above` of an `[any-taint]` fact, §4.1) |
| `a = b.f` | `(b, ., [any-taint], E, T)`, `f ∈ E` | nothing for `a` | |
| `a = b.f` | `(b, .f, [any-taint], E, T)` | `(a, ., [any-taint], E, T)` | normal |
| `a.f = b` | `(a, ., [any-taint], E, T)` | `(a, ., [any-taint], E ∪ {f}, T)`, by the keep edge `a.* →_{f} a.*` | normal (the exclusion edge, W8) |
| `a.f.g = b` | `(a, ., [any-taint], {}, T)` | `(a, ., [any-taint], {f}, T)` and `(a, .f, [any-taint], {g}, T)`, by the keep edges `a.* →_{f} a.*` and `a.f.* →_{g} a.f.*` | normal |
| `a.f = b` | `(b, .g, *, E, *)` | `(a, .f.g, *, E, *)`; under `L = 1`: `(a, .f, [any], {}, *)` | normal; under the cut: demand |
| `a.f = b` | `(a, .f, *, E, *)` | nothing (strong update) | |
| `a.f = b` | `(a, ., *, E, *)` | `(a, ., *, E ∪ {f}, *)`, no request | normal |
| `x = C.s` (run 1) | the identity static `*` edge to `(S, ., *, E, *)`, `<C> ∉ E` | `(S, ., *, E, *)`; nothing for `x`; the position request `[<C>, s]` | normal |
| `C.s = x` (run 1) | the identity static `*` edge to `(S, ., *, E, *)`, `<C> ∉ E` | `(S, ., *, E ∪ {<C>}, *)`; the class keep edge gives the position request `[<C>]` | normal |

The premise of the edge does not change in any case. Only its layer can change, from normal to demand. (`Cases.lean`
checks the fifth row with the mark `*`, which no `$` fact of the AP has, §7.2; with a concrete mark the result is the
same: no overlap.) The `[any-taint]` rows are not in `Cases.lean`. `AnyTaintEx.Vec.setter_keep` checks the keep row
by `decide` (the round-1 rule gave `(a, ., [any], {}, T)` in the demand layer: `setter_keep_base`,
`AnyTaintCases.W.keep_demand`); `AnyTaintEx.Vec.read_admitted` and `read_excluded` check the two read rows above the
fact; `AnyTaintExCases.X.two_results` checks the two-level write (together its two results cover exactly the
locations that are not at or below `a.f.g`, `locations_exact`; a write in one statement: on the JVM `a.f.g = b` is
`t = a.f; t.g = b`, a weak alias write, §11.1); `AnyTaintExCases2.G.r3_eJ1` derives the row
`(b, .f, [any-taint], {}, T)` in `AnyTaintEx.DRXs` (the getter `ret = p.f` of a must-premise; round 1
`AnyTaintCases.G.t3_eJ1`).

### 4.3 Apply a summary edge

`applySummary(a, j, g) = concat(a, j → g, edgeDemand = the layer of the summary edge)` (§4.1; Lean: `applySummary`).
The input `a` is the ADDED FACT of a link (§1), in callee coordinates; the result has the layer of `a` on that link
(§8.3) or a higher one. PRECONDITION: `a` SATISFIES the premise `j`. The caller tests it before it applies the
summary (§5.3 events E2 and E4); `applySummary` does not test it again.

* RUN 1: `j` covers `a` (strong enough):

  ```
  applicable(j, a)  ⇔  covers(j, ·) ⊇ covers(a, ·)  ∧  (j has an any tail ⇒ a has an any tail)
  ```

  The application is the case `below`, and the mark gate passes (`applicable_mark`). The abstraction of run 1 covers
  every added fact (C1, §6.1). Lean: `coversB`, `applicable`, `applicable_sound`; reference form `applicable` (§3.4).
  Run 1 has no `[any-taint]` premise (W8 (c)). An `[any-taint]/E` added fact gives the policy fact (§6.2), and the
  result of a FLOW summary on it follows §4.1 (an `[any-taint]/E` fact `c`): for example the FLOW summary
  `(this, ., *) → (this, ., */{name})` of a setter `this.name = n` gives `(this, ., [any-taint], E ∪ {name}, T)`,
  normal (`AnyTaintExCases.S.record_app`, `run1_dto_ann`). (The model tests `applicable` without the exclusion of the
  added fact; a policy fact has the Empty exclusion, so the test gives the same result.)
* RESTRICTED RUN: the premise lies INSIDE the fact as LOCATIONS (the location part of `a` covers every location of
  `j`, marks ignored), and the marks of `a` are a subset of the marks of `j` (Lean: `satI`, `RCore.satI_markSub`,
  `satI_conc_record`; reference form `inside`, §3.4). So a concrete fact satisfies a `*` premise, and a cleaned fact
  `*∖X` satisfies a `*` premise. This is the reverse of run 1: the emitted fact of a concrete pattern is `a ∩ D-c`
  (§6.3), so it always lies inside its added fact (`RCore.emitM_satI`), and it can be smaller than `a` at the same
  path. The application is the
  case `below` if `a` is at or below `j`. It is the case `above` if `a` is above `j` (for example `a = (x, ., [any], T)`
  and `j = (x, .f, [any], T)`); then the result is in the demand layer (§4.1 step 3), unless `a` is `[any-taint]/E`:
  every admitted location of an `[any-taint]/E` fact carries its mark, so the case `above` loses nothing (§4.1).
  `inside` reads the exclusions of an `[any-taint]/E` added fact and of a must-premise as part of the location sets
  (§3.4): a premise `j` at the path of `a` needs the exclusion of `a` to be a subset of the exclusion of `j`; a premise
  strictly below `a`, at `a.path ++ r`, needs the exclusion of `a` to admit `r` (Lean: `AnyTaintEx.satX`, the base
  `satI` with `insideExB`; `satX_inside`).
* A CANONICAL FLOW PREMISE OF A RESTRICTED RUN `(x, p, *, {}, *)` is satisfied exactly when it OVERLAPS the added
  fact. The overlap reads the added fact's tail and field exclusion. Its unrestricted mark admits every added mark,
  including `*` and `*∖X`. This equals `inside OR applicable` on that guarded domain
  (`CurrentSimplifications.Flow.canonical_flow_satisfaction_X`, the local annotated test).
  Other FLOW premises retain `inside OR applicable`. In particular, do not use overlap for a nonempty premise
  exclusion: `(x, ., */{f}, *)` and `(x, ., */{g}, *)` overlap but neither test holds. Concrete summary premises
  retain `inside` and its mark test; record read views retain R3/R4's matching guards. An abstract added mark never
  satisfies a concrete premise and raises no restricted-run request (§4.5).
  The demand builders must enforce §6.1's entry guard: every abstract entry has tail `[any]`, and every emitted
  FLOW premise has the Empty exclusion (§6.3). Full preservation of this guard through the current iteration
  remains open (§11.2). The local overlap equality does not prove a store or iteration invariant.
* AN `[any-taint]` PREMISE (a must-premise; forward restricted runs only, §6.3) is satisfied only by `inside`: the
  added fact covers EVERY admitted location of the premise, so it has an any tail (a concrete fact has no `*` tail, W2) and lies at or
  above the premise. A summary of a must-premise is end-exact, not exact pair by pair (§1), so it needs every
  admitted location of the premise. A NORMAL result also needs a normal `[any-taint]` link: an `[any]` added fact (a
  demand link, §8.3) gives a demand result. (Lean: `AnyTaintEx.DRX`, rule `ret`, with the satisfaction `satX`; the
  exactness needs a satisfaction that reads `inside` with the exclusions, `AnyTaintEx.SatInsideX`, which `satX` has,
  `AnyTaintEx.satX_inside`; the summary closure form `AnyTaintExExact.summaryX_end`. Round 1, without the exclusion:
  `AnyTaint.SatInside`, `satI_inside`.)
* A RECORD `j → g` (§8.7) applies in the direction in which it was derived when `applicable(j, a)`, or when `a`
  satisfies `j` by `inside` (restricted runs), in every run after the run that made it (R4). In the other direction it
  applies through its read view (R3). S14 and the R3/R4 layer guards make normal
  results exact, or end-exact for a must record applied by `inside`. A backward
  must-premise view gives demand results and makes no pair-exactness claim.
  The expected false-positive sources of §11.1 still apply. Records are not restricted.
  THE RECORD DEMOTION. A must record (an `[any-taint]` premise, §8.7) that applies by `applicable` only (the added fact
  lies inside the premise, and `inside` is false) gives its result in the DEMAND layer: the record needs every
  location of its premise, and the added fact has only some of them. By `inside` it gives the result of the
  application. Keep the result and its exclusion through the summary rewriter, binding back and aliases.
  Then raise its layer to DEMAND, immediately before the call field limit. Its path and mark do not change;
  W8 (b) drops its must exclusion (Lean: `AnyTaint.recLayer`, `recLayer_fact`,
  `AnyTaintEx.recLayerX`). Demotion commutes with the final field cut, including path truncation
  (`CurrentRecordTiming.cut_commutes`); this does not permit earlier demotion through the rewriter. The
  demotion stays with the exclusion (it is not the demotion that the exclusion replaces), and it is necessary
  (`AnyTaintExact.CexApp.cex_app`): the must record
  `(p, ., [any-taint], T) → (ret, ., [any-taint], T)` of `get(p) { ret = p.g }`, applied at `x = get(dto)` to the
  added fact `(p, .f, [any-taint], T)` (the source `dto.f.[any-taint] (T)`), would give the normal
  `(x, ., [any-taint], T)`, but `p.g`, and so `x`, carries no taint.

The mark condition makes the mark gate pass: a summary application never raises a request (`Coverage.summary_step`,
`RCov.sat_step`, `RCore.summary_stepR`). A summary conclusion `*∖X` stops an added fact whose concrete mark is in `X`
(§4.1 step 5). In a restricted run the CALLEE restricts the summary by its demand patterns before it publishes it
(§6.4).

The special cases of the summary application:

* SEVERAL PREMISES. A summary with one premise, applied to a caller edge with a premise set, keeps the premise set of
  the caller edge. A summary whose premise set has two or more members (an ND summary `{j1, …, jk} → g`) applies
  through event E6: one added fact per member (§4.6, §5.3).
* THE BACKWARD RUN. The zero-premise summary of a callee applies to the zero fact of each caller with no restriction
  and no satisfaction test (§9.2, the balanced return).
* STATICS. A static initial fact is an ordinary premise: run 1 applies its summaries by `applicable`. An added fact
  above a static initial fact does not read its summaries; the position request (§4.10) makes the precise initial fact
  instead.
* FIELD LIMIT. The caller applies it after the binding back (§5.3 step 5, §4.4).

* The exclusion of a summary edge FILTERS the delta: the summary `(arg0, ., *) → (ret, ., */{f})` applied to the
  added fact `(arg0, ., */{})` gives `(ret, ., */{f})` (bound back to the lhs `r`), and applied to
  `(arg0, .f.g, $, T)` gives nothing. Applied to `(arg0, ., [any-taint], E, T)` it gives `(ret, ., [any-taint],
  E ∪ {f}, T)` in the layer of the added fact (the exclusion edge of §4.1).
* Run 1 never applies a summary to a fact that only overlaps its premise. Another initial fact of run 1 serves that
  fact.

### 4.4 The field limit

A run has a positive field limit `L ≥ 1`. Every public mode and cut operation rejects `L ≤ 0`, before it reads the
fact. This also applies to the zero fact and an unchanged path. The analyzer applies `limit_L` to every result of
an operation that can make a path longer. It never applies it to a micro edge or a summary edge before the
application. The cut points:

* the statement transfer, after the micro edges and the lhs type filter (§4.2 step 6); this covers the read sources
  (`interpreter.md` §4.4);
* the call return, after the summary rewriter, the binding back and the aliases (§5.3 step 5); this covers the
  summary application, the record application and the unresolved callee;
* the source results and the end facts of a sink at a call, after their binding back and the aliases (§5.3 step 3);
* the results of the entry rules at the method start: the entry-point sources and the end facts of an entry sink
  (`interpreter.md` §4.3);
* the results of the exit rules at an exit (normal or exceptional), before the summary edge of the normal exit: the
  exit sources (also the conjunction result of a conjunctive exit source, §4.6) and the end facts of an exit sink
  (`interpreter.md` §4.7);
* the conjunction result (§4.6) and the application of a summary with several premises (§4.6, event E6);
* the backward seed (§9.2).

The cut:

* If the path has at most `L` counted accessors, the fact does not change.
* Otherwise, cut the path before the `(L+1)`-th counted accessor. Uncounted accessors before that point stay in the
  prefix. The tail becomes `[any]`, the exclusion Empty, the mark stays (also `*∖X`), and the edge goes to the demand
  layer. An `[any-taint]/E` fact over the limit becomes `[any]` too, and the cut drops
  its exclusion: the cut path is above the fact, so not every location below it carries the mark (W8;
  `AnyTaintEx.limitFX`; round 1:
  `AnyTaintCases.Cut.cut_transfer`, `limitF_cut_demand`).

Run 1 starts at limit 1. Every later run also has a positive limit (S12 (d), §4.10). The class accessor
`<C>` is not counted, so a cut never stops strictly above a static position.

The zero-limit vectors `AnyTaintEx.Vec.cut_drops` and `AnyTaintExCases.CUT` are historical Lean data. They remain
outside the current public API domain. Current cut tests reject limit zero and use positive limits.

Lean: `cutPath`, `limitF`; `limitF_sound` (the cut only enlarges). The field limit is the only depth bound of the
analysis. Each run may have its own limit (`RCov.iteration_sound`). The bound W3 needs a limit that does not decrease
from run to run (§6.6).

### 4.5 Mark gate and mark request (run 1 only)

Some rules need a concrete mark `T`: a sink, a conditional source, a mark-specific pass rule, and a literal of a
conjunction. Such a rule can meet a fact with the mark `*`, or `*∖X` with
`T ∉ X`. Then the rule gives no fact for `T`. It raises the REQUEST `(m, i, T)` on the premise `i` of the fact, in its
method `m`. A rule that drops the fact without a request loses the flow. A rule that applies to such a fact lets
every mark through.

A selected-mark cleaner uses the same request mechanism but its F75 gate is different (§4.7): every same-base
abstract fact gives the request T in run 1, also on a disjoint path or when T is already excluded. The fact continues
with T excluded. The request refers to the incoming edge's premise, not to its new excluded conclusion.

A request is always on a premise with an abstract mark (`Coverage.req_initial_star`). In run 1 the policy (§6.2) and
the position answers (§4.10 item 2) make such premises. So a request premise of run 1 is a policy fact
`(x, [], *, {}, *)` or a static position answer `(S, p, *, {}, *)`.

A request STANDS for the whole run. The callee `m` checks it against every LINK (added fact `a`, caller edge) of its
added fact store (§8.3) where `a` overlaps `i` (§3.2). It checks the links that exist when the request arrives, and
every link that arrives later. A new caller edge of an existing added fact is a new link too (§5.3, events E2 and E5).
For each link:

* If `a.mark = T`: ANSWER. Emit the initial fact `answer(i, a, T)`, the CHAIN ANSWER. It has the base and the path
  of the request premise `i` (the REQUEST CHAIN):
  * `(i.base, i.path, $, {}, T)` if `a` has the `$` tail and `a.path = i.path`;
  * otherwise `(i.base, i.path, i.kind, T)`: the request premise with the mark `T` (it keeps the exclusion of `i`).

  For a policy premise `(x, [], *, {}, *)` the answer is `(x, [], $, {}, T)` for the added fact `(x, [], $, T)`, and
  `(x, [], *, {}, T)` for every other added fact. The answer starts per §6.5: a `*` tail starts as `[any]` in the
  demand layer; a `$` tail stays `$` in the normal layer. The chain answer is never deeper than the request.
  The special case of a static premise is §4.10 item 4: if `i` is on `S` and `a` is at or below `i`, the answer is `a`
  itself with the mark `T`, not the chain answer.
* If `a.mark` is `*`, or `*∖X` with `T ∉ X`: PROPAGATE the request through the caller edge `(ic → c)` of the link:
  raise the request `(caller, ic, T)` (Lean: `climbsB`).
* Otherwise (`a.mark = T' ≠ T`, or `*∖X` with `T ∈ X`): do nothing.

One answer does not stop the request. The answered initial facts are initial facts like every other: callers read their
summaries (§5). Lean: `answerInit`, rules `answer`, `reqUp`; `answerInit_covers`, `answerInit_applicable`.

The check is per caller edge, not per added fact. Example (the sink needs `T`):

```
m(p)    { sink(p); }
B(v)    { m(v); }               A(u)    { m(u); }
main1() { s = srcU(); B(s); }   main2() { t = srcT(); A(t); }
```

`B` calls `m` first and gives the added fact `(p, [], *, {}, *)`. The sink raises the request `T` on the premise of `m`.
The request climbs to `B`, but `main1` has only the mark `U`, so nobody answers. Then `A` gives the SAME added fact
through a new caller edge. A request that checks only new added facts never climbs to `A`, and it loses the
vulnerability `main2 → A → m → sink`.

Requests exist ONLY in run 1. NO REQUEST AFTER RUN 1 (decision F72, rule R4; `ap-history.md` F72). A restricted
run, forward or backward, has NO REQUEST RULE: no raise, no answer and no climb (and no position request, §4.10). It
is not concrete (§6.3): a `*` demand pattern gives a FLOW premise, and the facts of a FLOW premise have abstract
marks. On a fact with the mark `*`, or `*∖X` with `T ∉ X`, an operation that needs the concrete mark `T` gives
NOTHING, with no request:

* the mark gate of a micro edge with the concrete premise mark `T` (§4.1 step 4);
* the literal of a conjunction (§4.6): the store keeps nothing;
* a cleaner of `T` on the fact's base (§4.7): the fact continues as `*∖(X ∪ {T})`, even if its path is disjoint;
* the sink check (§4.9): no effect;
* the emission under a concrete pattern (§6.3), and the satisfaction of a concrete premise, of a summary or of a
  record (§4.3, §8.7 R4).

THE RULE: ANALYSE WITH A CONCRETE MARK ONLY IF A CONCRETE-MARK DEMAND EXISTS. Run 1 keeps its requests. Their answers
make the concrete-mark summaries where the marks matter, and the hand-off gives them to the next run as concrete
demand patterns (or retained normal singleton records, §8.7 R5). The backward run keeps the concrete mark of such a
demand, so every later run has its concrete demand patterns too. The claim that this loses no flow (R6: every flow
that needs a concrete mark is demanded by a concrete pattern or crossed by a concrete record, and every flow that a
`*` pattern demands needs no concrete mark) is PENDING proof (§6.6, CONTRACT B WITH MODES; §11.2). BEFORE F72 a
restricted run was concrete, so no request rule could fire: `RCov.no_reqR`, `RExact.DR_no_request`,
`RMain.no_request_M`, and for the backward run, whose seeds have concrete marks, `BExact.DB_no_request`
(`BExact.CexSeed.cex_seed`: a seed with the mark `*` would raise one). These theorems are about the concrete design
(the emission `emitM` that copies the mark); the implementation asserted the absence of a request. F72 replaces this
reason (decision F35; `ap-history.md` F35, F72).

A position request (§4.10) works in the same way: it stands, it uses the request store (§8.8), and the events E2 and
E7 of §5.3 check it per link. Its answer and its climb are §4.10 items 2 and 3.

### 4.6 Mark conjunction: ND edges

A conjunction occurs only in a forward run. Each literal contributes one matched input with its premise set and
contribution layer. The conclusion requires ALL contributions of the same conjunction alternative at the same
statement. Write these contributions as `P ∧ Q` for a binary conjunction. They are parts of one joint rule, not
independently applicable forward summaries: `{a, b} → c` cannot produce `c` from `a` alone.

A CONJUNCTIVE micro edge `x1.ρ1.t1(T1) ∧ … ∧ xk.ρk.tk(Tk) → z.π.t(T)` gives the mark `T` at `z.π` if every literal holds
at the statement. A literal tail `tj` is `$` (`ContainsMark`) or `[any]` (`ContainsMarkOnAnyField`). The target tail
`t` is `$` or `[any-taint]` (W7: no `*` tail): a conjunctive micro edge is a source (a pass rule has no mark literal,
`interpreter.md` §4.2), so an `[any]` target is a taint edge (S15). The interpreter makes the edge for a rule with
several mark literals (`interpreter.md` §4.2,
§5.3): at a call (its rule statement) and at an exit (the exit rule statement). An exit source whose condition
alternative has two or more positive literals is a conjunctive micro edge of the exit rule statement (`interpreter.md`
§4.7, D31), not a rule error.

* The CONJUNCTION STORE (§8.9) keeps, STANDING for the run, per (conjunctive micro edge, statement, literal index),
  each fact `c` at the statement (for a conjunctive exit source: at the exit) that:
  * OVERLAPS the literal pattern `xj.ρj.tj` (§3.2, marks ignored), and
  * passes the mark gate of the literal mark `Tj` (§4.1 step 4, so `c.mark = Tj`).

  It keeps the fact with the premise set of its edge (`{zero}` for a zero-to-fact edge, `{i}`, or a larger set). Lean:
  rule `conj` of `ND.DN`. The literal index is the place of the literal in its conjunctive micro edge. Each conjunctive
  micro edge has its own entries: two alternatives of one rule (two disjuncts of its condition, `interpreter.md`
  §4.2) are two conjunctive micro edges, and they never share an entry, also not for an equal literal pattern.
* If the mark gate gives the request `Tj` (a fact with the mark `*`, or `*∖X` with `Tj ∉ X`), the store keeps nothing
  and raises the request (run 1, §4.5; Lean: rule `reqConj`). In a restricted run the store keeps nothing and raises
  no request (F72, §4.5; before F72 every fact was concrete, so this never occurred). A literal has no static
  exception: on the static base it uses the mark request (§4.10 covers
  only the statement micro edges and the sinks).
* When a fact arrives, the store combines it with the stored facts of the other literals: one fact per literal, every
  combination. The result `z.π.t(T)` has the UNION of the premise sets WITHOUT THE ZERO FACT; if every input has the
  premise set `{zero}`, the result has `{zero}`. The zero fact adds no condition: it is at every node that an edge
  reaches (it passes every statement and enters every callee with every other added fact, §5.3), so `{zero, i}` and
  `{i}` hold at the same points (`NDZero.zero_everywhere`). The number of the members names the edge: `{zero}` a
  zero-to-fact edge, one member a fact-to-fact edge, two or more an ND edge. Then the field limit applies to the result
  (§4.4). Lean: the closure `NDZ.DNz` (`ND.DN` with the zero-drop) corresponds to the list model `ND.DN` edge by edge
  (`NDZero.dnz_to_dn`, `dn_to_dnz`), so its coverage, exactness and confirmation are the theorems of §10.10.
* The target of a conjunctive micro edge is not on the zero base (`NDZeroBase.NoZeroGen` item (d); `interpreter.md`
  §5.3).
* A literal's contribution is DEMAND if its input is DEMAND, or if the literal does not COVER the input
  (`!coversB lit c`: the input has a location outside the literal, for example an `[any]` input for a `$` literal,
  or an input above the literal) and the input is not `[any-taint]`. Otherwise the contribution is NORMAL.
  The conjunction joins these contribution layers: NORMAL if ALL are NORMAL; DEMAND if ANY is DEMAND.
  Premise count does not decide the layer. An `[any-taint]/E` input (normal)
  that overlaps its literal and passes its mark gate gives a NORMAL contribution also if the literal does not cover it:
  every admitted location of the input carries the mark, and the overlap reads `E` (§3.2: an excluded location
  overlaps nothing), so the literal holds at a common location (Lean: `AnyTaintND.conjLayerT`, `lit_loc`, for an input
  with the Empty exclusion; with `E` the rule is argued, §11.2). An `[any-taint]` target gives an `[any-taint]` result,
  with the Empty exclusion, in the layer of the conjunction (a must, S15; in the demand layer it is `[any]`, W8). Lean:
  `ND.conjLayer`, `ND.Example.c3_normal`; with the `[any-taint]` rule `AnyTaintND.DNzT` (`NDZ.DNz` with
  `AnyTaintND.conjLayerT`), its example `AnyTaintND.Example.layer_new`, `layer_old`, `confirmed`. A normal result is
  exact against the support semantics, under S7, S9, S10 and S13 (`NDExact.nd_edge_exact`; the valid form `nd_edge_exact_valid`; for
  the spec closure `NDZ.DNz`: `NDZeroThms.nd_edge_exact_z`, `nd_edge_exact_valid_z`; with the `[any-taint]` rule
  `AnyTaintND.nd_edge_exact_zT`, `nd_edge_exact_valid_zT`, under the same hypotheses).
* The engine is path-insensitive: the stored facts are per statement, not per execution path, so the literals can hold
  on paths that exclude each other (`if c then a := srcA else b := srcB; r := f(a, b)`). This is the EXPECTED
  over-approximation; it does not move the result to the demand layer. The reference semantics of a conjunction
  (`ND.TaintN`, below) is path-insensitive in the same way.
* An ND edge propagates through micro edges with its premise set unchanged. Its conclusion is uncorrelated (`$` or an
  any tail; W7): a `*` tail is the correlation with ONE premise.
* An edge whose premise set has two or more members (an ND edge) is ALWAYS a TAINT edge (§7.2), never REACH or FLOW.
  No member is the zero fact (above), and every member has a concrete mark: an input of a conjunction
  passes the mark gate of its literal (a concrete mark, S9), and a fact with a concrete mark has a premise with a
  concrete mark or the zero premise (`Kinds.flow_abstract`, S7; directly `Kinds.nd_taint`, `ndz_taint`). The members
  of a summary with several premises applied at a call (event E6) are concrete for the same reason (§4.3: a concrete premise is satisfied only by a concrete added
  fact). The conclusion has a concrete mark and no `*` tail (W7). Lean: `Kinds.nd_taint` (`ND.DN`), `Kinds.ndz_taint`
  (`NDZ.DNz`: also no zero member).
* At a call, the callee sees an ordinary added fact. A callee summary `j → g` with one premise, applied to a caller
  edge with a larger premise set, keeps the premise set of the caller edge. A callee SUMMARY WITH SEVERAL PREMISES (its
  premise set has two or more members: an ND summary `{j1, …, jk} → g`) needs one link at the call statement per
  member `jm`: an added fact that satisfies `jm` (§4.3), with its caller edge. The rule is standing: the link that
  arrives last completes it (event E6 of §5.3; the store of §8.9). The result has the union of the premise sets of the
  caller edges, without the zero fact. It is in the demand layer if the
  summary is, or if one added fact of the combination is in the demand layer on its link (§8.3; Lean: `ND.DN.ndBind`).
  Then the caller binds it back and applies the field limit (§5.3 step 5). In a restricted run the callee restricts
  such a summary before it publishes it (§6.4). A conjunctive exit source (above) makes such a summary too: a full
  combination at the exit is an exit item with the union of the premise sets, after the field limit; it takes the
  rest of the exit order and becomes a summary at the normal exit (`interpreter.md` §4.7, D31); with two or more
  members it is an ND summary, which the callers apply by event E6.
* A multi-premise edge can be NORMAL or DEMAND by the contribution rule above. It is never persisted or read as a
  reversed record (§8.7 R1). The forward hand-off makes one demand pattern per premise member (§9.2), without
  changing the publication's layer. The backward run has no conjunction: it reverses a conjunctive micro edge into
  one independent micro edge per literal, with every result in the demand layer (§9.1).
* The model has binary conjunctions (`ND.Conj`: two literals). A conjunction of `k` literals is argued by chaining
  (§11.2).
* A conjunctive sink (positive literals on several positions) uses the same store (§4.9, §8.9). The model has no
  conjunctive sink; it is argued (§11.2).
* The concrete semantics of a conjunction is the SUPPORT semantics of §3.5 (`ND.TaintN`); a vulnerability witness is a
  tree.

Lean: `ND.lean` (§10.6). `CurrentConjunction.part_factor`, `joined_normal_iff`, and `joined_demand_iff` check the
local binary contribution rule against `AnyTaintND.conjLayerT`; `normal_publication_example` checks that selection
does not demote a normal multi-premise summary. Full restricted ND integration remains open (§11.2).

### 4.7 The cleaner

A cleaner `clean(position, reach, mark)` at a statement removes the mark `T` (or every mark) from the locations of its
position `x.p`. The reach is `exact` (`x.p` only), `below` (everything strictly below `x.p`, the position `x.p.*`) or
`atAndBelow`. (The reach `below` excludes `x.p` itself; the case `below r` of §4.1 includes `r = []`.) In the
concrete semantics (§3.5) a location keeps its value unless the cleaner cleans it (Lean: `Cleaner`, `Cleaner.cleansB`,
`Flow.clean`).

A cleaner action on a field uses READ, CLEAN, WRITE-BACK (review decision F74). For `clean(x.f, T)`:

```text
tmp = x.f
clean(tmp, T)
x.f = tmp
```

`tmp` is fresh. The read and the strong write use the ordinary micro edges of `interpreter.md` §2.2 and §2.3,
including the identity edges and the write's keep edge with the field exclusion `{f}`. The primitive cleaner acts
on `tmp` with an empty path, the selected reach and the selected mark. The backward run reverses the write,
then takes the same primitive cleaner, then reverses the read. A normal FLOW fact outside the written field keeps
all its marks. The temporary is local to the action and is not a method summary. The table below specifies the
primitive cleaner; an atomic primitive on `x.f` is not the reference form of this field action.

PRIMITIVE DISPATCH (F75). Apply the first matching case in the current AP coordinates:

1. If `c.base ≠ cleaner.base`, keep `c`.
2. If the cleaner selects T and `c.mark` is `*` or `*∖X`, give the same fact with mark `*∖(X ∪ {T})`, before any
   path or reach test. Preserve its path, tail, field exclusion and layer. Run 1 also requests T on the incoming
   edge's premise, including when T is already in X. The request stands in the request store (§4.5); repeated
   requests have the same key. A restricted run, forward or backward, raises no request.
3. Otherwise apply the spatial table below: concrete-mark facts and all-marks cleaners use it.

The selected T flow therefore uses a concrete answer in run 1, then a concrete demand or record. A same-base
abstract fact cannot skip the second case. The spatial test compares the location set of `c` with the cleaned
locations, marks ignored: `inside` (every location is cleaned), `disjoint` (no location is) or `part` (some
locations are). The test is exact (Lean: `cleanPos`):

| `c` against the position `x.p` | `exact` | `below` | `atAndBelow` |
|---|---|---|---|
| apart | `disjoint` | `disjoint` | `disjoint` |
| strictly below (`c.path = p ++ r`, `r ≠ []`) | `disjoint` | `inside` | `inside` |
| at (`c.path = p`), tail `$` | `inside` | `disjoint` | `inside` |
| at, tail `*/E` or an any tail | `part` | `part` | `inside` |
| strictly above (`p = c.path ++ r`), tail `$` | `disjoint` | `disjoint` | `disjoint` |
| strictly above, tail `*/E` or `[any-taint]/E` | `part` if `E` admits `r`, else `disjoint` | the same | the same |
| strictly above, tail `[any]` | `part` | `part` | `part` |

The result:

| `c.mark` | cleaner mark | `inside` | `disjoint` | `part` |
|---|---|---|---|---|
| `*` or `*∖X` | every mark | dropped | `c` | `c` in the demand layer, normalised: a `*` tail becomes `[any]` (W2) |
| `T` (cleaned) | `T` or every mark | dropped | `c` | the part that the cleaner does not surely clean: a fact with an any tail at `x.p` under a `below` cleaner becomes `(x, p, $, T)`, in the layer of `c` (an `[any-taint]` fact keeps the normal layer); a normal `[any-taint]/E` fact at `x.q` under a cleaner ONE ACCESSOR BELOW it, at `x.q.f` (so `p = q ++ [f]`, and `E` admits `f`): `atAndBelow` gives `(x, q, [any-taint], E ∪ {f}, T)`, and `below` gives `(x, q, [any-taint], E ∪ {f}, T)` and `(x, q.f, $, T)`, both in the layer of `c`; any other `c` goes to the demand layer (it has a concrete mark, so it has no `*` tail, W2; an `[any-taint]/E` fact becomes `[any]` with the Empty exclusion, W8: under the `exact` cleaner at `x.q` or at `x.q.f`, which has no shape for "every location but one", and under every cleaner two or more accessors below `x.q`; the cleaner demotions of §2.2) |
| `T'` (not cleaned) | `T` | `c` | `c` | `c` |

Lean: `BaseCleaner.cleanRes` for the F75 selected-mark split. The earlier `cleanRes` and the X operations retain
the earlier spatial split for comparison; their unchanged concrete-mark and all-marks rows are `addEx`, `concPart`.
With the exclusion:
`AnyTaintEx.cleanPosX`, `partX`, `cleanResX`; the vectors `AnyTaintEx.Vec.clean_atAndBelow`, `clean_below`,
`clean_exact`, `clean_excluded`; the program CL (`AnyTaintExCases.CL.atAndBelow_result`, `below_result`,
`exact_result`); the rows are exact (`AnyTaintExExact.cleanResX_exact`, `clean_rows_exact`; the new `$` fact of the
`below` cleaner is real, `below_new_fact_real`), and the `exact` cleaner must demote
(`AnyTaintExExact.CexExactCleaner.cex_exact_cleaner`: `[any-taint]/{f}` would miss the real `x.f.g`, a soundness
loss, and a normal `[any-taint]/{}` would claim the cleaned `x.f`).

So the cleaner SPLITS a `*`-mark fact by the mark. The edge `*∖{T}` propagates every mark except `T`, exactly, in the
normal layer. The mark `T` goes through the cleaner only on the concrete answer of the request, which the cleaner cleans
exactly (except on `[any]`, which is in the demand layer already). After run 1 it goes through only on a fact of a
concrete demand pattern (F72, §4.5). The union of the two covers every real flow
in the intended run-1 coverage contract. The local F75 checks are in §10.13. `Core.cleanRes_sound`, `Coverage.coverage` and `Exact.cleanRes_exact` prove the
earlier spatial cleaner. Current base normal-edge exactness is checked directly
in `CurrentExact`; full coverage and the combined extensions remain open.

* A summary conclusion `*∖X` stops an added fact with a concrete mark in `X` (§4.1 step 5). A sink for `T ∈ X` on a
  `*∖X` fact neither triggers nor requests (§4.9). A request for `T ∈ X` does not climb through a `*∖X` fact (§4.5).
* The mark exclusion is not tied to a position, so a field write, a field read or a cut does not change it.
* IN A RESTRICTED RUN (F72) a fact can have an abstract mark: a fact of a FLOW premise (§6.3). The rows with an
  abstract mark and the dispatch apply WITH NO REQUEST. The mark T through the part that the cleaner does not
  clean comes only from a concrete demand pattern (§4.5). The all-marks rows raise no request.
  Before F72 every fact of a restricted run was concrete
  (`RExact.DR_concrete`, for the emission that copies the mark, for any records): only the rows with a concrete mark
  applied, and no `*∖X` fact occurred. A reused run-1 record with a `*∖X` conclusion gives a concrete mark on a
  concrete fact, or nothing, and `*∖(X ∪ Y)` on a fact with the mark `*` or `*∖Y` (§4.1 step 5).
* THE STATIC BASE (run 1). A request that the cleaner raises on a static premise is answered as §4.10 item 4 says. A
  cleaner on `S` names its mark (S12 (e)): the all-marks `part` row makes an `[any]` static fact above a static
  position. A `RemoveAllMarks` rule on a position of `S` is not a cleaner: it is the kill of a strong write, a
  statement summary with keep edges (`interpreter.md` §1.4). One exception: the WHOLE-BASE CLEANER `(S, atAndBelow,
  all)` with the empty path (S12 (e)), used directly by the generated Spring cleanup form. Every fact on `S` lies
  inside it (the `inside` row), so it drops the fact whole: it raises no request and makes no `[any]` static fact.
* THE ZERO BASE. No cleaner is on the zero base (S11 (d)).
* The interpreter places the cleaner (`interpreter.md` §5.2): at a call to a cleaner method, on the bound facts before
  they enter the callee, and on the facts of an unresolved call before its pass rules. The model has no cleaner inside
  a call (a cleaner is an instruction of the CFG, `Instr.clean`), so the call cleaners are argued (§11.2).
* THE SUMMARY REWRITER (`interpreter.md` §5.2) is not a cleaner placement. It is a RULE-GUIDED OVERRIDE of the callee
  flow: a user-defined rule replaces the real data flow of the callee for its marks at its positions. It acts for the
  calls that user-defined rules cover. It selects:
  * every user-defined SOURCE rule of the call whose condition is not statically false;
  * every user-defined CLEANER rule of the call only if it is UNCONDITIONAL: its condition is statically true.

  It never selects a rule that a rule error rejected (`interpreter.md` §1.3, §5.2): such a rule has no form at all.
  For each selected rule, each relevant mark `T` of the rule and each action position `P` of the rule, it applies
  `clean(P, exact, T)` for a position with no `AnyField`. For an `AnyField` position `P.[any]` it applies
  `clean(P, atAndBelow, T)` if the rule is a source (`interpreter.md` D34: today's code gives `(P, below, T)`, the
  spec text before F69 gave `exact`), and the cleaner of its row of `interpreter.md` §5.2, `clean(P, below, T)`, if
  the rule is a cleaner (`RemoveMark(T, P.AnyField, …)`; as today, `JIRMethodCallRuleBasedSummaryRewriter.kt:105`).
  It applies them to the summary results and to the unresolved results of the call, before the binding back (§5.3
  step 5). The source writes every location at or below `P` (an `[any-taint]` result, S15), so the rewriter cleans
  them all; `exact` would keep the callee results below `P`. The override is by design:
  it is part of the reference semantics of the rule (§3.5). So the cleaner rule of the last item of this list does not
  govern it, but the rewriter agrees with it: a conditional user-defined cleaner does not act through the rewriter, as
  it does not act at the call. The rewriter is argued with the call cleaners (§11.2).
* THE BACKWARD RUN. A primitive cleaner is its own reversal (§9.2). A field action reverses its write and read
  around that cleaner (F74). `interpreter.md` §4.9 places these operations on the requirement at the callee start.
* `clean` is unconditional. Only an unconditional cleaner acts. A cleaner rule whose condition keeps a mark literal
  after the static evaluation does not act (`interpreter.md` §4.2): the fact does not decide such a condition, and
  cleaning there is unsound. The summary rewriter (above) selects a cleaner rule only if the rule is unconditional.

### 4.8 The type filter

A type filter `filter(b, may)` at a statement drops a fact on the base `b` whose path cannot exist on a value of the
static type of `b`. `may` is a predicate on paths. Contract (S5):

* `may` accepts every path that a real value of the static type can have;
* `may` is PREFIX-CLOSED: `may(p ++ q) ⇒ may(p)`.

Then a fact that covers a real location has a path that `may` accepts (its path is a prefix of the path of the
location), and the filter never drops it (`Core.filt_keeps`). Lean: `Instr.filt`, `Flow.filt`, rule `filt`;
`Program.WF.filtPrefix`.

The filter checks the concrete path of the fact only. It keeps a `*`, an `[any]` or an `[any-taint]` tail whole. The
analysis does not store a filter in a fact or an edge, and it does not propagate a filter to later statements. So a
`*`, `[any]` or `[any-taint]` fact that passes a filter can still denote locations below its path that the filter
rejects (`Exact.CexFilt`). Those
locations do not exist on a real value. On the JVM this is an expected false-positive source (§11.1).

So the exactness theorem holds for VALID locations only: the end locations that every filter accepts
(`Exact.edge_exact_valid`, `closed_exact_valid`, `Closed.closed_records_exact_valid`). The validity predicate is the
assumption S13 (§0.1), with three parts:

1. every type filter accepts every valid location of its base (`Exact.FiltValid`);
2. the validity goes back along every statement micro edge and along every call binding, into the callee and back: a
   valid end location of the edge comes only from a valid start location (`Exact.BackOK`);
3. with conjunctions, the validity goes back from the target of a conjunctive micro edge (§4.6) to each literal: if a
   location of the target is valid, every location of each literal is valid (`NDExact.ConjOK`; without it the valid
   form of the ND exactness is false, `NDExact.CexConjOK.cex_conjOK`).

The type-filter placement of the interpreter makes S13 true (`interpreter.md` §5.1). A confirmation (§4.9) needs a
sink pattern whose locations are valid (`Confirmed.confirmed_real_valid`; `Confirmed.CexConfFilt` shows that the
condition is necessary in the model: its program breaks W6 and S8).

The interpreter places the filters (`interpreter.md` §5.1 gives the table). The mark policy (a concrete mark on a
primitive value) is NOT a type filter: it reads the mark, the model has no mark filter, and it can drop a real flow, so
it is outside S5 (`interpreter.md` G6). The AP has no summary-side filter (`interpreter.md` D13, D14). Without a
filter the analysis only adds facts, so this is sound and costs precision (`interpreter.md` Q3).

The special cases: the backward run applies no type filter (§9.2; §11.2 gives the difference to the model); a type
filter on the zero base accepts the empty path (S11 (d)); Go has no type filter (`interpreter.md` §2.3).

### 4.9 Sink check and confirmation

A sink checks the pattern `s = (v, ρ, t, T)` with the tail `t = $` (`ContainsMark`) or `t = [any]`
(`ContainsMarkOnAnyField`). For an edge `(i, layer) → f` in the method `m`:

* If `f` and `s` do not overlap (§3.2, marks ignored): no effect.
* If `f.mark = *∖X` with `T ∈ X`: no effect (the mark was cleaned).
* If `f.mark = T'`: the sink is TRIGGERED if `T' = T`; otherwise no effect.
* If `f.mark` is `*`, or `*∖X` with `T ∉ X`: in run 1 raise the REQUEST `(m, i, T)`. Here `i.mark` is abstract too:
  a concrete premise has only concrete conclusions (`Coverage.edge_conc`). The implementation asserts it (§11.2 gives
  the difference to the model). IN A RESTRICTED RUN (F72): no effect and no request (§4.5). Such a fact is a fact of a
  FLOW premise (§6.3). So a sink fires only on a fact with a concrete mark, under a concrete premise or the zero
  fact. The method of a sink gets a concrete demand pattern from its seed (§9.2), and the chain of calls down to it
  stays concrete, so the confirmation does not change (R6; PENDING, §6.6, §11.2). (Before F72 a restricted run had
  no such fact, and the implementation asserted it.)

Examples: `(x,.,$,T)` triggers, `(x,.f,$,T)` does not, `(x,.,[any],T)` triggers, `(x,.,[any-taint],T)` triggers (as
`[any]`: the same location set), `(x,.,*,{},*)` raises the request `T`. For the pattern `(x, .f, $, T)`: the fact
`(x, ., [any-taint], {f}, T)` does not trigger (an excluded location overlaps nothing, §3.2), and
`(x, ., [any-taint], {g}, T)` triggers. Lean: `check`, `check_sound`, `check_request_star`; with the exclusion
`AnyTaintEx.checkX`, `AnyTaintEx.Vec.check_vectors`. An `[any-taint]` sink fact is normal, so its sink edge can be
confirmed (below): a common ADMITTED location of the sink fact and the sink pattern is reached
(`AnyTaintExExact.sink_denX`; round 1 `AnyTaintExact.sink_den`). A sink on the static base `S`
uses this check too: in run 1 the request on a static premise is answered by the added fact itself (§4.10 item 4).

An UNCONDITIONAL SINK has no positive mark literal: it has no literal, or every literal is negated (a negated literal
counts as true, §3.5). Its sink pattern is the zero fact `(zero, [], $, {}, zeroMark)`, so it triggers on the zero fact
at the sink statement. Its sink edge is the edge of the zero fact there: the premise set `{zero}`, the conclusion the
zero fact, in the normal layer. The two confirmation checks below confirm it if the premise set `{zero}` is supported
(condition 3): at a root, or through a chain of calls whose caller edges are the zero edges of the callers.

A CONJUNCTIVE SINK has positive mark literals on several positions (`interpreter.md` §4.2). Each literal is a sink
pattern. The conjunction store (§8.9) keeps, standing for the run, per (sink alternative, statement, literal index),
each sink edge whose check of that literal triggers. The literal index is the place of the literal in its sink
alternative (§1). Two alternatives of one rule never share an entry. A check that gives the request raises it (run 1).
When every literal of one alternative has a stored edge, each combination (one edge per literal) is a SINK EDGE SET of
the vulnerability, with the set of the sink facts. The vulnerability store keeps every sink edge set as a sink witness
(§1) under the one key of the vulnerability (§8.10). A sink edge set is in the demand layer if one of its edges is.

END FACTS. A sink rule can have end-fact actions (`interpreter.md` §4.1, END FACTS). An end-fact action gives the
zero-to-fact edge `Zero → (sink statement, P.$ (T))` (`P.[any-taint] (T)` for an `AnyField` position, `interpreter.md` I14). It takes no input fact: when a sink edge triggers, or when a
conjunctive sink completes a combination, it applies to the zero fact in the layer of that sink edge or of that
combination (§2.2). Its premise set is `{zero}`, whatever the premise set of the sink edge: an end fact is
context-insensitive (§11.1).

THE BACKWARD RUN has no sink check. Its sink rule is the seed (§9.2). An end fact exists only after its sink
triggers, so the reversed end-fact edge of a sink alternative also fires the sink seeds of that alternative (§9.2, THE
TRIGGER OF AN END FACT).

A sink witness (§1) is CONFIRMED exactly when both checks pass:

* NORMAL EDGES (condition 1). Every sink edge of the witness is normal. The test reads the layer only, including
  `[any-taint]/E` facts (Lean: `AnyTaintEx.Confirmed6X`, `ConfirmedX`; round 1 `AnyTaint.ConfirmedT6`,
  `ConfirmedT`, `AnyTaintND.ConfirmedNzT`).
* JOINT SUPPORT (condition 3). The witness's combined premise set is supported. For one edge use its premise set;
  for several use their union without zero, or `{zero}` if every edge has `{zero}` (§4.6).

ADMISSIBLE MEMBERS (condition 2) are part of the support definition, not an independent confirmation check.
A member is admissible if it is the zero fact, an EXACT concrete fact `(x, p, $, T)` (a request answer in run 1,
an emitted fact in a restricted run), or, in a forward restricted run, a must-premise
`(x, p, [any-taint], E, T)` (an emitted fact, §6.3).

SUPPORT (condition 3) is a property of the premise SET (§1; Lean: `Confirmed.Sup` in run 1, `RExact.SupM` in a
restricted run, `NDConfirmed.SupN` with conjunctions):

1. At a root, the premise set is supported if every premise is the zero fact.
2. In a callee, the premise set `{j1, …, jk}` is supported if ONE call statement supplies every member. Each `jm`
   must be admissible, and there must be a normal caller edge `(Pm, normal) → cm` at that call with these properties:
   1. its own premise set `Pm` is supported (the rule applies again in the caller);
   2. the binding of `cm` gives the added fact `a`, and `a` is normal on that link (§8.3);
   3. `jm = a`: the same base, path, tail and mark. Thus they are zero or concrete exact facts: `jm` is the answer
      of `a` in run 1 (the chain answer of §4.5, or the added fact itself on a static premise, §4.10 item 4), or the
      emission `a ∩ D-c = a` in a restricted run. OR (a forward restricted run) `a` has the `[any-taint]` tail and
      `jm` lies inside `a` (`inside`, §4.3, with exclusions), with the SAME concrete mark. Here `jm` is exact `$` or
      a must-premise `[any-taint]`. This branch includes `jm = a`, the emission of an `[any-taint]/E` added fact.
      Every admitted location of `a` carries its mark, so every location of `jm` is supplied (Lean:
      `AnyTaintEx.SupLinkX`, `SupX`; round 1 `AnyTaint.SupLink`, `SupT`). For a concrete premise, `inside` already
      implies the same mark (`AnyTaintExact.markSub_conc`); without that guard an abstract premise inside `a` would
      demand marks that `a` does not carry (`AnyTaintExact.CexSupMark.cex_sup_mark`).

   Different members can use different caller edges at that one call. Those edges can be supported through
   different calls of the caller, so support is a tree.
3. No other premise set is supported.

Support implies member admissibility; repeating condition 2 at confirmation adds no restriction
(`CurrentSimplifications.Confirmation.confirm_equiv`, a local check with unchanged same-call suppliers).
For one premise, support is the chain of supported caller edges. Exactness alone is not enough
(`Confirmed.Weak.weak_support_gap`). For several premises, support at separate calls is not enough: the members
must meet at one call (`NDConfirmed.CexSites.cex_sites`).

A conjunctive sink is a conjunction to a fresh target with a sink on it: it is confirmed if every sink edge of its set
is normal and the UNION of their premise sets (without the zero fact, §4.6; `{zero}` if every edge has `{zero}`)
is supported jointly (condition 3). A conjunction is the expected
over-approximation of a path-insensitive engine (§4.6), so a vulnerability through a normal conjunction result can be
confirmed: it is real for the path-insensitive support semantics `ND.TaintN` (`NDConfirmed.confirmed_real_N`,
`confirmed_real_N_valid`; for a program without conjunctions the rule is the one of `Confirmed.confirmed_real`,
`NDConfirmed.confirmedN_iff`).

The analyzer computes the support and the confirmation only for a COMPLETE run (§1), at its fixed point (S6), after
the last event: condition 3 is a least fixed point over the caller edges, and it can change until the run ends. An
incomplete run confirms nothing (§8.10).

A vulnerability (§1) is confirmed in a run if one of its sink witnesses of that run is confirmed; otherwise it is a
demand vulnerability of that run (§1). Its state in the report is DEMAND (a DEMAND vulnerability, §1) if no complete
forward run confirmed it so far (§8.10). A result of an `[any]`-target source is `[any-taint]` (W8), so a
vulnerability whose taint comes from such a source CAN be confirmed. In run 1: when the sink reads the tainted object
in the method of the source (`AnyTaintExCases2.PassRule.source_confirmed`); also after a setter of one field (program S:
`root() { dto = srcAny(); dto.setName(c); sink(dto.name); sink(dto.email); }`, `setName(n) { this.name = n; }`; the
setter gives `(dto, ., [any-taint], {name}, T)`, normal, so `sink(dto.email)` is confirmed,
`AnyTaintExCases.S.run1_email_confirmed`, and `sink(dto.name)` is not reported at all, `run1_name_not_reported`: it
is not real, `name_not_real`); or after a callee whose FLOW summary keeps the whole object (program I,
`AnyTaintExCases2.I.run1_confirmed`). In a restricted run also through a callee: a getter (program G,
`AnyTaintExCases2.G.run3_confirmed`, also with the hand-off of the backward run, `run3_confirmed_handoff`) or a sink in
the callee (program C, `AnyTaintExCases2.C.run3_confirmed`: the must-premise is supported through the must branch of
`AnyTaintEx.SupLinkX`, `AnyTaintExCases2.C.run3_supported`). Run 1 does not confirm program G: the FLOW summary of the
getter is the case `above` of a policy fact, a demand edge (`AnyTaintExCases2.G.run1_flow_above`,
`run1_not_confirmed`); the must-premise that the demand gives in run 3 confirms it (`AnyTaintExCases2.G.run3_must`).
(These are the round-1 programs G, C, I and P, re-derived in `AnyTaintEx.D6X` and `AnyTaintEx.DRXs`; §10.11. Run 3
uses the earlier restriction `AnyTaintEx.restrictX` and the earlier hand-off; with the intersection and the hand-off
of the demand edges the same results are checked by hand, §11.2.) A
strong write into the object does not demote it: the exclusion removes only the written field (`AnyTaintExCases.X.fh_confirmed`,
`fg_not_reported`); without the exclusion a normal result would confirm the overwritten field, which is not real
(`AnyTaintCases.W.keep_normal_confirms_unreal`). A vulnerability that rests only on a may `[any]` (a pass rule with an
`AnyField` target, `AnyTaintExCases2.PassRule.pass_not_confirmed`) or on another demotion of §2.2 (the list is there;
for example the field limit cut (`AnyTaintExCases.CUT.cut_reports` is historical zero-limit data outside the current API);
the `exact` cleaner below the object,
`AnyTaintExCases.CL.exact_result`) stays a
demand vulnerability, and the output holds it as a DEMAND entry (§8.10).

`Confirmed.confirmed_real_valid` (run 1, under S7, S10 and S13) and `RExact.confirmed_realM_gen_valid` (every forward
restricted run, also under S14; the support `RExact.SupM`; the satisfaction `satI`; the restriction of §6.4 by
`Handoff.restrictI_sub`; the instance with the earlier restriction is `RMain.confirmed_real_M_valid`) prove that a confirmed
vulnerability is real for the reference semantics (§3.5), for a sink pattern whose locations are valid (§4.8). The
forms without the validity condition (`Confirmed.confirmed_real`, `RMain.confirmed_real_M`) are for programs without
type filters (`Exact.FiltUp`). On the real program, a confirmed vulnerability is real modulo the expected
false-positive sources of §11.1. These theorems are about the closures `D` and `DR`, which have no static rule.
With the static rule of §4.10, `StaticsConfirmed.confirmed_realS` and `confirmed_realS_valid` prove it for run 1: the
support of condition 3 then also accepts the mark answer on a static premise (§4.10 item 4: the added fact itself).
With the `[any-taint]` tail and its exclusion (the closures `AnyTaintEx.D6X` and `AnyTaintEx.DRX`, §10.11): run 1,
under S7 and S13 (`AnyTaintExExact.confirmed_real_valid6X`; for `Exact.FiltUp` `AnyTaintExExact.confirmed_real6X`);
a forward restricted run, under S7, S13, the records of S14 (`AnyTaintEx.RecsExactX`, `AnyTaintExExact.RecsConcX`,
`RecsWFX`) and the three properties of the rules `AnyTaintEx.SatInsideX`, `EmitCopiesMarkX` and
`AnyTaintExExact.RestrictOKX` (`AnyTaintExExact.confirmed_realX_valid`; for `Exact.FiltUp`
`AnyTaintExExact.confirmed_realX`; the spec rules have the three properties, the restriction by
`HandoffX.restrictIX_ok`; the instance with the earlier restriction is `AnyTaintExExact.confirmed_realXs`); every
restricted run of the run sequence when every record is an exit edge of an earlier forward run of the same program
(`AnyTaintExExact.RecsFromRunsX`), with no exactness hypothesis on the records (`seq_confirmed_realX_valid`); with
the reversed backward records and with the source seeds this is argued (§8.7 R4,
§11.2). With conjunctions: `AnyTaintND.confirmed_real_NzT_valid` (run 1; `AnyTaintND.DNzT` has no W6T and no
exclusion: both are argued, §11.2). The sink edge can be `[any-taint]/E`: the sink pattern must meet an ADMITTED
location of it (`AnyTaintEx.checkX`), and a supported must-premise has all its admitted locations entry-reachable
(`AnyTaintExExact.sup_entryX`).

### 4.10 Statics in run 1: the position request

The static base `S` holds every static field at the path `[<C>, f]`: the class accessor `<C>` (not counted by the field
limit) and the field `f`. A Go global `G` is at the path `[<G>]`. A rule can also put a concrete mark on a class
position `[<C>]` with no field, for example `(S, <C>, $, {}, T)`. The run-1 static fact is abstract: the policy fact
`(S, ., *, {}, *)` (§6.2). An operation whose premise is below an abstract static fact is the case `above` of §4.1, and
the ordinary result is `[any]` in the demand layer. In run 1 the static base uses a POSITION REQUEST instead. So no
static fact with the `[any]` tail occurs above a static position (`Statics.no_any_above`). The same holds for the
`[any-taint]` tail: the model has one kind `.any` for both any tails, and a source with an `[any]` target on a bare
class is a rule error (S12 (b); `interpreter.md` §1.4). (This is proved for `Statics.DS`, which has no W6T and no
exclusion; with them it is argued, §11.2.) The rule needs the construction rules S12.

IDENTITY STATIC `*` EDGE. A propagation edge `i → f` with the premise `i = (S, q, */E0, *)` and the conclusion
`f = (S, q, */E, *)` or `f = (S, q, */E, *∖X)` at the same path `q`, in the normal layer (Lean: `Statics.idEdgeB`).
The rule fires only at the root path `q = []` or at a CLASS `q = [<C>]` (a position answer of item 2 at a class).
Below a static field the ordinary rules apply, as for an instance field. A Go global `[<G>]` is a static field, not a
class. (No Go statement micro edge reads below `[<G>]`, `interpreter.md` §2.3, so this case never occurs. The model
test `Statics.genFireB` reads "at most one accessor"; for these programs it gives the same result.)

1. RAISE (Lean: rule `sreqStmt`; `Statics.genFireB`). This is the one place of the rule; §4.1 (static exception),
   §4.2 and `interpreter.md` §2.1 step 4 point to it. Let `i → f` be an identity static `*` edge at the root path `[]`
   or at a class `[<C>]`, in the method `m`. Every STATEMENT MICRO EDGE whose premise is on `S` at the path
   `p = q ++ r`, with `r ≠ []` and `E` admitting `r`, gives NO fact and NO mark request on this edge. It raises the
   position request `(m, i, p')`, where `p'` is `p` cut to at most two accessors: the static field `[<C>, f]` or the
   class `[<C>]`. This holds for every such micro edge, for every target base, premise tail and premise mark: a read
   `S.<C>.f.* → x.*` (`x = C.f`, `p' = [<C>, f]`), a Go read `S.<G>.* → x.*` (`x = G`, `p' = [<G>]`), the class keep
   edge `S.<C>.* →_{f} S.<C>.*` of a write `C.f = v` or of a `RemoveAllMarks` kill (`p' = [<C>]`), a pass rule or a
   conditional source whose premise is on a static field (at a statement, at a call or at an exit).
   * A statement micro edge AT A CALL acts on a fact of the caller edge `(i → c)`: a source of the rule statement of
     the call and a `RemoveAllMarks` kill on `S` act on the bound fact, a pass rule of an unresolved callee on the
     added fact (§5.3 steps 3 and 4). The binding `S.* → S.*` does not change the path, so the test reads the caller
     edge `(i → c)` in the caller `m`, and the position request is `(m, i, p')`.
   * The RULE STATEMENTS OF THE METHOD BOUNDARIES are statement micro edges too: the entry rules at the method start
     (`interpreter.md` §4.3) and the exit rules at an exit (`interpreter.md` §4.7). They act on a fact `c` of an edge
     `(i → c)` of `m`, so a conditional exit source whose premise is on `S` strictly below an identity static `*` edge
     raises the position request `(m, i, p')`. (The entry-point sources are unconditional: they read only the zero
     fact. The touched bases and the keep edges of these rule statements: `interpreter.md` §4.3, §4.7. A conjunctive exit
     source is a conjunctive micro edge: its literals use the conjunction store and, on `S`, the mark request, §4.6.)
   * The other micro edges of the statement apply as usual, so the kill stays (§4.2). The root keep edge
     `S.* →_{<C>} S.*` of a write (Go: `S.* →_{<G>} S.*`) is at `q = []`, not strictly below it: it applies and adds the
     class accessor to the exclusion (§4.2, the static rows of the table).
   * A SINK on `S` raises no position request: it uses the ordinary sink check and mark request (§4.9, §4.5), and item
     4 answers the request.
   * Below a static field (an identity edge at `[<C>, f]` or deeper, or a static fact that is not an identity edge) the
     ordinary rules apply: a deep read is the case `above` of §4.1 and gives `[any]` in the demand layer, as for an
     instance field (on an `[any-taint]/E` static fact it follows the `[any-taint]` table of the case `above`).
2. ANSWER (rule `sanswer`). The position request stands for the run. An added fact `a` of `m` that overlaps `(S, p)`
   and is AT OR BELOW `p` answers it: the answer is the initial fact `(S, p, *, {}, *)` of `m`, with the identity start
   edge in the normal layer (§6.5). An added fact above `p` does not answer. Callers read the summaries of the answer
   as usual (§4.3).
3. CLIMB (rule `sreqUp`). For every link of `m` (existing or later) whose caller edge `ic → c` has its premise `ic` on
   `S`, and whose added fact overlaps `(S, p)` and is ABOVE `p`: raise the position request `(caller, ic, p)`. The
   first caller that has an added fact at or below `p` answers, and the precise fact goes down the calls.
4. MARK ANSWER ON A STATIC PREMISE (Lean: `Statics.SCtx.ansInit`). For a mark request `(m, i, T)` (§4.5) whose premise
   `i` is on `S`, and an added fact `a` of `m` that overlaps `i`:
   * if `a.mark = T` and `a` is AT OR BELOW `i`: the answer is `a` itself with the mark `T`,
     `(a.base, a.path, a.kind, T)`, not the chain answer (an `[any-taint]/E` `a` gives the tail `[any]` with the Empty
     exclusion: run 1 has no `[any-taint]` premise, W8 (c));
   * if `a.mark = T` and `a` is above `i`: the answer is the chain answer `answer(i, a, T)` of §4.5;
   * if `a.mark` is abstract and does not exclude `T`: the request climbs (§4.5).

   The reason for the first case: do not use the chain answer when `a` is at or below a static premise. At the static
   root the chain answer is `(S, ., *, {}, T)`, and it starts as `(S, ., [any], T)` in the demand layer (§6.5). A cleaner on the static root then turns a precise caller
   fact into this fact, and the vulnerability is lost (`Statics.CexClean.shallow_misses`).

Every other operation on `S` is the ordinary one: the call bindings `S.* → S.*` (S12 (c)), the summary application
(§4.3), the cleaner (§4.7; its request uses item 4), and the conjunction literal (§4.6).

AFTER RUN 1 NO STATIC RULE IS NEEDED. A restricted run has no position request, no mark request and no other static
rule: the demand alone handles the statics. For the forward restricted runs this is proved under two hypotheses:
S12 (a) to (d), with the field limit of the run, and persisted records that keep the static invariant (a static
record conclusion with a `*` or an any tail above a static position is an identity static `*` record; Lean
`StaticsIter.RecOK`, with the model kind `.any` for both any tails). The proof has four steps:

1. For EVERY demand, no static fact of the run with a `*` or an any tail lies above a static position.
2. The reason: the emission gives the added fact, its meet at the same path, or the demand pattern below it; the
   restriction only moves a conclusion down; and a fact above a position is exact.
3. So every static read, write keep edge and sink of the run at most two accessors deep is the case at or below of
   §4.1, and none of them makes an any-tail result by the case `above`. A deeper static read or sink follows the
   ordinary rules of an instance field, and it can meet an `[any]` fact above it
   (`StaticsIter.DeepReadIter.deep_read_above`).
4. The run raises no request.

§10.8 lists the theorems (`StaticsIter.rinv_all`, `static_step_below`, `no_request`, `no_static_rule_after_run1`,
`iteration_general_DS`). The backward run satisfies the same invariant and raises no request
(`BExact.binv_all`, `no_static_rule_backward`) if the reversed program satisfies the construction rules S12 (a) to (d)
and its seeds and records keep the invariant (`BExact.SeedsOK`, `StaticsIter.RecOK`); an `[any]` sink on a class
position is outside it (precision only). That the interpreter's reversed program satisfies them is argued (§11.2).
That the persisted records keep the static invariant over the run sequence is argued (§11.2).

These theorems are about the CONCRETE restricted runs (the emission `emitM`; step 2 reads only its rows). Since F72 a
restricted run, forward or backward, can have a FLOW premise on `S`: the FLOW form of a `*` pattern (§6.3), with the
`*` tail. Step 4 still holds: the F72 runs have no request rule (§4.5). That no FLOW premise lies above a static
position, or that the ordinary case `above` of §4.1 then loses no flow that a concrete pattern does not give, is part
of the pending model of F72 (§11.2).

Lean: `Statics.lean`, the run-1 closure `DS` with the rules `sreqStmt`, `sanswer`, `sreqUp` and the answer
`Statics.SCtx.ansInit`; the final rule is `Statics.Design`. §10.8 lists its theorems and the worked programs.

---

## 5. Calls and ownership

### 5.1 Concrete semantics of a call

The concrete call step (the touched bases, the bindings, the zero binding) is in §3.5. The interpreter gives the
bindings of each call kind (`interpreter.md` §3).

### 5.2 Who owns what

| Side | Owns | Does |
|---|---|---|
| CALLER (the method that contains the call) | the subscriptions: (caller edge, call statement, added fact) | binds each caller fact into the callee (micro edges); applies every published summary edge of the callee whose premise its added fact satisfies, and every record that applies to it (§8.7 R4); binds back; applies the field limit |
| CALLEE (the called method) | the added facts (with the caller edges that made each added fact), the demand patterns of the method, the emission, its initial facts, its edges and summaries, its requests (run 1) | emits the initial facts for each added fact (§6); analyses them; restricts each new summary edge by its demand patterns (restricted runs, §6.4) and then PUBLISHES it to the subscribers; answers and propagates its mark requests (§4.5) and its position requests (§4.10) |

### 5.3 Call processing

The interpreter gives the order of the steps at a call (`interpreter.md` §4.5). For the caller edge `(i, layer) → c`
at the call statement:

1. RELEVANCE. If `c.base` is not a touched base of the call, the edge passes over the call (call-to-return). The
   touched bases are those of §3.5 (`interpreter.md` §3.1 lists them for each call kind), with one special case: the
   zero base is never touched (THE ZERO FACT below). `S` is touched at every call, in every run (`interpreter.md`
   §3.3).
2. BIND. Apply the caller-side type filter of each binding into the callee (`interpreter.md` §3.1, §5.1). For each
   binding edge `e` into the callee: the bound fact `b = concat(c, e)` (a micro edge, §4.2).
3. SINKS AND SOURCES. The sinks of the call check `b` (§4.9); a triggered sink can add end facts: zero-to-fact edges in
   the layer of the sink edge or of the completed combination (§2.2, §4.9; `interpreter.md` §4.1, END FACTS). The
   sources of the call apply to `b`: they are the rule statement of the call (`interpreter.md` §4.1), with statement
   micro edges (§4.2) and conjunctions (§4.6). The fact `b` has the premise set and the layer of
   the caller edge `(i → c)`, so in run 1 the static exception (§4.1, §4.10 item 1) tests the caller edge, and a
   position request goes to `(caller, i, p')`. A source result goes back to the caller through the binding back and
   the aliases, with NO summary rewriter (the rewriter removes the mark of a user-defined source at its own
   position), then the field limit (§4.4; `interpreter.md` §4.5 step 4). The end facts go the same way.
4. PER CALLEE POSITION. The cleaners of the call clean `b` (§4.7), chained in the rule order. A `RemoveAllMarks` rule on
   a position of `S` is not a cleaner: at its place in the rule order it applies to `b` as a statement summary, the
   kill of a strong write (`interpreter.md` §1.4), with the static exception as in step 3. The generated Spring
   cleanup form uses the primitive whole-base cleaner `(S, [], atAndBelow, all)` (S12 (e)). Each result `a` is an
   ADDED FACT (§1), with its own layer on the link (§8.3). Then:
   * for each resolved callee: the CALLEE PROCESSING below, for the link (`a`, caller edge);
   * at a JVM constructor call: `a` also passes over the call (`interpreter.md` §3.5);
   * for an unresolved callee or a resolution failure: the statement summary of the unresolved callee applies to `a`
     (`interpreter.md` §3.7), as statement micro edges (§4.2 step 4), with the static exception as in step 3. It is
     never restricted. A method with no instruction (native, abstract, no body) is never a resolved callee: the call
     resolver drops it, and a call with no other callee is unresolved (`interpreter.md` §3.7). A call with an empty
     and a non-empty target enters only the non-empty one, so a flow through the empty target is lost, as today on
     the JVM (`interpreter.md` G12).
5. RETURN. For each result of step 4 (a summary result or an unresolved result), in callee coordinates: apply the
   summary rewriter (§4.7; `interpreter.md` §5.2). Then bind the result back, with the binding-back type filters
   (`interpreter.md` §3.1). Then apply the aliases (`interpreter.md` §3.8: they apply to the results that its alias
   rule AC3 names). Among the summary results, only a COMPLETE identity result does not go to the aliases: a
   normal-layer result that equals the start fact of its premise (§6.5; `interpreter.md` AC4). A demand-layer summary
   result always goes to the aliases. An `applicable`-only must-record result also goes to the aliases;
   its record demotion follows these mappings, immediately before the field limit (§4.3).
   A normal default identity or constructor pass-over result skips the aliases
   (`interpreter.md` AC3, AC4).
   Then apply the field limit (§4.4). A constructor pass-over result skips the rewriter. This order is the order of
   `interpreter.md` §4.5 step 6.

THE ZERO FACT at a call (`interpreter.md` §4.6). The zero fact does not use steps 1 to 5; it uses this order:

1. The call does not touch the zero base, so the zero fact passes over the call.
2. The unconditional sinks and sources of the call fire on the zero fact, in the order of `interpreter.md` §4.6, and
   the stored facts of a conjunction can complete it (§4.6). The source rules form the rule statement of the call: a
   call stage (`interpreter.md` §4.1) with the micro edge from the zero fact to itself (S11 (d)). Their results go
   back as in step 3. In a forward restricted run, only the source seeds fire (§6.1 rule 6).
3. The zero fact enters every resolved callee through the binding `zero.* → zero.*` (§3.5), with no cleaner: it is an
   added fact of the callee, and the callee emits the zero fact for it (§6.2, §6.3). The results of the callee
   summaries that this added fact satisfies are summary results: they return by step 5.

The zero fact itself takes no cleaner, no pass rule and no rewriter. An unresolved call only passes the zero fact over.
In the backward run the zero fact enters the callees by the rule of §9.2.

The callee processing is a set of STANDING rules. Each event triggers its actions; the actions can make new events.
The order of the events does not change the fixed point (S6). An implementation can process them in any order.

| # | Event | Actions |
|---|---|---|
| E1 | A new added fact `a` of the callee | The callee adds `a` to its added fact store (§8.3). It EMITS the initial facts for `a` (§6): in run 1 the policy fact (§6.2), in a restricted run the emission for each demand pattern of the callee (§6.3): `a ∩ D-c` for a concrete pattern, the FLOW form of `D-c` for a `*` pattern (F72; one initial fact for every added fact under it). Each new initial fact is event E3. |
| E2 | A new link (added fact `a`, caller edge), also a new caller edge of an existing added fact | The callee stores the caller edge with `a` (§8.3). It checks every standing mark request and position request that overlaps `a`, and answers it or propagates it through THIS caller edge (§4.5; §4.10 items 2 and 3). The caller SUBSCRIBES `(caller edge, call statement, a)` (§8.4). It applies every published summary edge with one premise whose premise `a` satisfies (§4.3), and every record that applies to `a` (§8.7 R4), then step 5. For a summary with several premises the new link is event E6. |
| E3 | A new initial fact `j` of the callee (an emission, an answer or a position answer) | The callee analyses `j` from its start fact (§6.5). (The interpreter filters the start fact by the context type, `interpreter.md` §4.3.) Each summary edge of `j` is event E4. |
| E4 | A new summary delta `j → g` of the callee: an exit fact after the exit order of `interpreter.md` §4.7 (exit sources, exit sinks, the removals of that section; no summary for a local base) | In a restricted run the callee restricts it by each demand pattern of the callee (§6.4); a zero-premise summary of the backward run is not restricted (§9.2, the balanced return). It PUBLISHES each result. For a summary with one premise: for each subscription whose added fact satisfies `j`, the caller applies the result (§4.3), then step 5. A summary with several premises goes to event E6. |
| E5 | A new mark request `(m, i, T)` in the callee `m` (run 1) | The callee stores it (§8.8). For each added fact `a` of `m` that overlaps `i`, and each caller edge of `a`: answer or propagate, as in E2 (§4.5). |
| E6 | A new link at the call statement whose added fact satisfies one premise of a callee summary with several premises (§4.6), or a new such summary | The caller combines the link with the stored links of the other premises of that summary (§4.6, §8.9). A full combination (one link per premise) applies the summary, then step 5. |
| E7 | A new position request `(m, i, p)` in the callee `m` (run 1) | The callee stores it (§8.8). For each added fact `a` of `m` that overlaps `(S, p)`, and each caller edge of `a`: answer it if `a` is at or below `p`, or climb through the caller edge if `a` is above `p` and the caller premise is on `S` (§4.10 items 2 and 3). |

The result of a summary application is in the demand layer if the added fact is in the demand layer on its link
(§8.3), if the summary edge is, or if the application itself moves it there (§4.1). The result of a must record that
applies by `applicable` only is in the demand layer too (the record demotion, §4.3).

---

## 6. Runs and abstraction

### 6.1 Rules of a run and their contracts

A complete run is the least fixed point of these rules:

* Each root starts from zero. Each initial fact starts as §6.5 specifies.
* A statement uses transfer, conjunction, cleaner, type filter, and sink check in
  the interpreter's order (§4).
* A call uses §5.3 and the summary application of §4.3.
* An added fact gives initial facts by §6.2 in run 1 and §6.3 in later runs.
* Run 1 raises, answers, and climbs mark requests (§4.5), and applies static
  position requests (§4.10). It has no input records.

A restricted run is every run after run 1, forward or backward. It:

1. emits only from its demand patterns; root zero is also initial, and backward
   zero follows §9.2;
2. applies callee publications after §6.4 restriction, using §4.3 satisfaction;
   backward zero-premise balanced returns are the §9.2 exception;
3. applies records by `applicable` or `inside` (§8.7 R4), with must-record demotion;
4. has no mark/position request rule or static request rule;
5. uses its own field limit and the preceding run's hand-off (§9.2);
6. fires an unconditional source in a forward run only if the preceding backward
   run reached it. Zero keeps, conditional sources, and triggered sink end facts
   are not source-restricted. Run 1 fires all sources.

An abstraction depends on the method, added fact, and immutable run configuration,
not event order. Its contracts are:

| # | Contract |
|---|---|
| C1 | Run-1 abstraction `α(m,a)` satisfies `applicable(α(m,a),a)`. |
| C2 | For each common location **with its mark** covered by `a` and `D-c`, emission gives a premise `j` covering it, `a` satisfies `j`, and all of `j` lies inside `D-c` in locations and marks. A concrete `D-c` requires a concrete added mark. An abstract `D-c` requires the entry-tail guard below. |
| C3 | A concrete pattern emits the added fact's mark. A `*` pattern emits its FLOW form with mark `*`. |
| C4 | Satisfaction implies that the added marks are a subset of the premise marks. In **run 1**, `applicable(j,a)` for concrete `a:T` implies `applicable(answer(j,a,T),a)`. This answer step does not use restricted-run satisfaction. |
| C5 | If the **whole** premise `j` lies inside `D-c`, then every pair `(l1,l2)` of `j→g` whose exit `l2` is covered by `D-p` **with its mark** survives restriction in `j→g'`, in the same layer. The result has only pairs of the input. |

The entry-tail guard is: a `*` entry pattern has `$`, `[any]`, or `*/{}`.
Nonempty `*/E` is excluded from C2: two incomparable field exclusions can have
common locations while neither satisfaction test succeeds. Hand-off normalization
removes **mark** exclusions, not field exclusions (§9.2). The alternating base
construction proves this guard under S7/S8/S11 and concrete non-star sink seeds;
an external demand producer must enforce it too. X-tail/pipeline extensions remain
open. A concrete pattern intentionally emits nothing from an abstract added fact;
preserving that concrete flow requires the mode contract in §6.6.

Canonical typed-tree demand has stronger construction conditions: every entry
has a non-star tail and normalized mark; an abstract entry has `[any]`; a `$`
exit demand has a concrete entry mark. Native records must have valid initial
kinds, and an abstract record premise must have only FLOW conclusions (§8.7).
These conditions prevent an abstract `$` result that a FLOW tree cannot encode.
The alternating base construction proves them under S7/S8 on both programs,
concrete non-star seeds, and those record-kind conditions. `EntryShape` alone
does not prove typed-tree representability. External demand builders must check
the stronger conditions; generic per-path operations keep their total function
cells. The full X-tail/pipeline extension remains open.

The current executable certificates and all proof assumptions are in
[proof-status.md](proof-status.md). Premise overlap alone and exit-location checks
that ignore marks do not satisfy C5.

### 6.2 Run 1

The run-1 abstraction gives:

* for the zero fact: the zero fact;
* for every other added fact `a`: the most abstract fact `(a.base, [], *, {}, *)`.

Lean: `policy1`, `policy_applicable` (C1). The caller gets the marks back through the summary application: a `*`-mark
premise passes the mark of the added fact through. A sink, a mark-specific rule or a cleaner inside the callee gets the
mark through a request (§4.5). A statement micro edge below the policy fact `(S, [], *, {}, *)` gets the precise
static fact through a position request (§4.10 items 1 to 3); a sink, a cleaner or a literal uses the mark request with
the answer of §4.10 item 4. Run 1 is the ONLY run with requests. The answers of the requests (§4.5, §4.10)
are the other initial facts of run 1.

An `[any-taint]/E` added fact gets the policy fact too: run 1 has no `[any-taint]` premise (W8 (c)). Its chain answer
is `(i.base, i.path, i.kind, T)` (§4.5), which starts as `[any]` in the demand layer (§6.5). So run 1 confirms an
`[any-taint]` finding only through a premise set `{zero}` or an exact answer, for example when the sink reads the
tainted object in the method of the source (`AnyTaintExCases2.PassRule.source_confirmed`), or through a callee whose
FLOW summary keeps the whole object (§4.1, row 1 or row `*`, `[]`, `[any-taint]/Ec`; program I,
`AnyTaintExCases2.I.run1_flow`, `app_normal`, `run1_confirmed`), also a callee that overwrites a field of it: the
field goes into the exclusion (the setter of program S, `AnyTaintExCases.S.run1_email_confirmed`; a setter one call
deeper, program SD, `AnyTaintExCases.SD.run1_email_confirmed`, `same_result`). Through a callee that reads below the
object (a getter `ret = p.f`) it does not: the FLOW summary of the policy fact is the case `above`, a demand edge
(`AnyTaintExCases2.G.run1_flow_above`, `run1_not_confirmed`). The demand of a later run gives the must-premise that
confirms it (§6.3).

### 6.3 Restricted run: the emission

For each added fact `a` and demand pattern of its method, test common locations
and marks with the tables below. No common part means no emission. A concrete
pattern emits `a∩D-c`. A `*` pattern emits its FLOW form:

| `D-c` tail | FLOW tail |
|---|---|
| `$` | `$` |
| `[any]` | `*/{}` |
| `*/E` | `*/E` |

The FLOW form keeps the pattern base/path and has mark `*`. One premise serves all
added facts and marks admitted by that pattern. It starts in the normal layer.
The `*/E` function cell does not remove the C2 guard of §6.1.

| `D-c` mark | added mark | emission when locations overlap |
|---|---|---|
| `T` | `T` | `a∩D-c` with mark `T` |
| `T` | another concrete mark, `*`, or `*∖X` | none |
| `*` | any mark | FLOW form of `D-c` |

A canonical pattern has no `*∖X` mark. The backward run uses the same emission,
with its requirement as the added fact. Neither direction raises a request.

For a concrete pattern, compute the location intersection as follows. In these
tables `p=D-c.path`, `t=D-c.tail`, and a tail admits a suffix as §3.1 specifies.

| Added fact against `D-c` | Intersection |
|---|---|
| Another base or apart | none |
| Below: `a.path=p·r`, `r≠[]` | `a` if `t` admits `r`, else none |
| At: `a.path=p` | at `p`, with the tail meet below |
| Above: `p=a.path·r`, `r≠[]` | at `p`, with the tail meet, if `a.tail` admits `r`; else none |

| Added tail | `$` entry | `[any]` entry | `*/E2` entry |
|---|---|---|---|
| `$` | `$` | `$` | `$` |
| `[any]` | `$` | `[any]` | `*/E2` |
| `[any-taint]/E1` | `$` | `[any-taint]` | does not occur in canonical concrete patterns |

An emitted `[any-taint]` keeps `E1` at the added path; at a deeper demand path
it has empty exclusion. A `$` result has empty exclusion. No entry pattern has
`[any-taint]`. A concrete added fact has no `*` tail (W2). For the abstract-pattern
overlap test, equal-path star tails meet by exclusion union; at a deeper path use
the shallower tail's admission test. Emission then gives the FLOW form, not that
location meet.

A normal `[any-taint]` added fact produces a must-premise when the result still
has an any tail. It differs from the may `[any]` premise at the same path. All
premise keys include their tails and applicable field exclusions (§7.1).
Every forward method has zero demand, so an added zero emits zero. Backward zero
enters each reached callee directly (§9.2). A sink never fires under a FLOW premise.

### 6.4 Summary restriction (restricted runs, in the callee)

The callee restricts raw summary `j→g` by each demand pattern before publication.
Restriction keeps `j`, the conclusion mark, and the layer. It reduces the
conclusion's locations where the representation permits this. Decide persistence
and demand selection on the raw summary (§8.5, §8.7). Restriction preserves the
layer and complete premise key, so all publications of one group have the same
selection result. A narrowed publication does not replace the raw record.

Reject the edge if `D-p` is absent, if the **whole** premise is not inside `D-c`
in locations and marks, if the conclusion mark does not meet the `D-p` mark, or
if the conclusion base differs from the `D-p` base. Premise overlap is insufficient.
The inside test reads a must-premise's field exclusion (§3.4).

| `D-p` mark | concrete `U` conclusion | `*` conclusion | `*∖Y` conclusion |
|---|---|---|---|
| `T` | keep iff `T=U` | keep | keep iff `T∉Y` |
| `*` | keep | keep | keep |
| `*∖X` | keep iff `U∉X` | keep | keep |

Canonical `D-p` has no mark exclusion; the final row makes the generic test
complete. A `*` entry pattern can restrict both its FLOW premise and concrete
premises wholly inside it. A concrete entry pattern cannot restrict a FLOW premise.
The mark test retains an abstract conclusion as is; it does not convert a
pass-through mark into a concrete mark.

Let `p=D-p.path`. After the gates above:

| Conclusion against `D-p` | Result |
|---|---|
| At `p` | tail meet below, at the same path |
| Below: `g.path=p·r`, `r≠[]` | keep `g` if `D-p.tail` admits `r`, else none |
| Above: `p=g.path·r`, `r≠[]`, `g` has `[any]` | at `p`, `$` if `D-p` is `$`, else `[any]` |
| Above, `g` has `[any-taint]/E` | none if `E` rejects `r`; otherwise at `p`, `$` for `$` demand, `[any-taint]/E2` for `*/E2` demand, `[any-taint]/{}` for `[any]` demand |
| Above, `g` has `*/E` | keep `g` whole if `E` admits `r`, else none |
| Above, `g` has `$`; apart; or another base | none |

At the same path, use this meet. No exit demand has `[any-taint]`.

| `g` tail | `$` demand | `*/E2` demand | `[any]` demand |
|---|---|---|---|
| `$` | `$` | `$` | `$` |
| `[any]` | `$` | `[any]` | `[any]` |
| `[any-taint]/E` | `$` | `[any-taint]/(E∪E2)` | `[any-taint]/E` |
| `*/E` | `*/E` | `*/E` | `*/E` |

Restriction retains every demanded pair of C5 and introduces no pair. It is an
exact intersection with the demand except for these representability limits:

* A may `[any]` at/above `*/E2` has no field-exclusion slot; the result also covers
  descendants that `E2` excludes.
* A `*` conclusion stays whole. Cutting a correlated star conclusion to `$` can
  add pairs; a retained star can therefore extend outside the exit demand.
* A retained abstract conclusion mark stays pass-through. It can pass entry marks
  outside a concrete exit demand.

These limits can occur with FLOW analysis. Do not claim exact global demand
narrowing from this operation. The must-tail rows do read and preserve exclusions.
Several patterns can produce several pieces; publish their union. The backward
zero-premise balanced-return exception publishes the raw edge (§9.2).

For a multi-member premise set `J`, apply this single-member restriction to each
`j∈J` and each candidate demand pattern, then union the resulting conclusions.
Each selected **whole member** must lie inside that pattern's `D-c`; do not require
every member to lie inside one single-base pattern. Each publication retains all
of `J`, so application still needs one satisfying link per member (§4.6). The
single-premise C5 proof does not prove full ND support/restriction integration.

One mark mismatch example is enough to test the gates: `x.$(T)→ret.f.$(T)` under
`D-c=x.$(T), D-p=ret.f.$(U)` gives none when `T≠U`. Location-only reduction would
incorrectly retain it. The executable C5 certificate and indexed-reduction proof
are listed in [proof-status.md](proof-status.md).

### 6.5 The start fact

| initial fact `i` | start conclusion | layer |
|---|---|---|
| `(x, p, */E, *)` | `(x, p, */E, *)` (identity) | normal |
| `(x, p, */E, T)` | `(x, p, [any], {}, T)` (W2) | demand |
| `(x, p, [any], m)` | `(x, p, [any], {}, m)` | demand |
| `(x, p, [any-taint], E, T)` (a must-premise; forward restricted runs only) | `(x, p, [any-taint], E, T)` (itself, with its exclusion) | normal |
| `(x, p, $, m)` | `(x, p, $, {}, m)` | normal |

A premise never has the mark `*∖X` (§2.2). The row `(x, p, */E, *)` is the policy fact and the position answer of run 1
and, since F72, the FLOW premise of a restricted run (the FLOW form of a `*` pattern, §6.3): it starts with the
identity, in the normal layer, so its edges are FLOW (§7.2) and its normal summaries can be records (§8.7 R1). Before
F72 a restricted run had no such premise. Lean: `startFact`, `startFact_sound`; the must-premise with its exclusion:
`AnyTaintEx.startX` (`AnyTaintEx.Vec.start_must`; its edge is END-EXACT, `AnyTaintExExact.startX_must_end`; round 1
`AnyTaint.startT`). An `[any-taint]` premise occurs only in a forward restricted run: the emission of an
`[any-taint]/E` added fact (§6.3). The backward run has none (W8 (d)): its any-tail premises are `[any]`, and every
edge of them is a demand edge (`AnyTaintSim.DB_any_premise_demand`, under C3 of the concrete design and
`BExact.SeedsConc`; since F72 an `[any]` premise comes only from a concrete pattern, and a `*` pattern with the tail
`[any]` gives the FLOW form `*/{}` instead, §6.3). Run 1 has
none: the policy fact, the chain
answers and the mark answer on a static premise are not `[any-taint]` (§4.5, §4.10 item 4, §6.2). The start fact of a
must-premise relates every admitted premise continuation to every admitted continuation of the conclusion, as if a
source fired at the method entry: its edges are end-exact, not exact pair by pair (§1). The zero fact starts as
itself, in the normal layer. A position answer `(S, p, *, {}, *)` starts with the identity, in the normal layer
(§4.10). The interpreter adds the start rules of a method: the filter of the start fact by the context type, and the JVM entry rules
of the zero fact (`interpreter.md` §4.3).

### 6.6 The run sequence

Runs alternate forward and backward: 1 forward, 2 backward, 3 forward, and so on.
Each uses its own field limit; the driver increases the limits (§4.4). A complete
run is a fixed point. An incomplete run adds nothing to the report or hand-off
and refutes nothing. The report contains all confirmed entries and the DEMAND
entries of the latest complete forward run (§8.10).

A later run receives the preceding run's demand edges and seeds (§9.2), plus the
persistent records (§8.7). Confirmed vulnerabilities are final. The next backward
run seeds the report's DEMAND witnesses; it can also seed an alternative whose
reversed end-fact action is reached. The next forward run fires the source hits
of the backward run. The driver owns barriers, budgets, and stop order
([analyzer-core.md](analyzer-core.md) §7).

Contract B states the required preservation across a backward run. For each real
witness justified by the preceding forward run:

1. each entered call is demanded at an entry location with its mark;
2. each returning call is demanded by **one** pattern covering both entry and exit
   locations with their marks, or crossed by a retained record with that pair;
3. each unconditional source outside recorded calls is a source seed.

A justified returning call uses a published piece of an actual reached summary,
or a record read by that run, and that piece/record contains the witness pair.
A pattern and a record may preserve different calls of the same witness.

F72 adds modes. An abstract call is justified by a FLOW premise and must use a
mark-agnostic trace. This trace carries the **actual AP fact** through transfers,
bindings, summaries, cuts, and cleaners; it cannot choose a narrower fact at a
cleaner. A selected-mark cleaner is allowed in abstract mode only if its base
differs from the fact base or its selected mark differs from the witness mark.
A same-base cleaner of the witness mark requires concrete mode even on a disjoint
path (F75). An all-mark cleaner uses the spatial rule and must retain the witness
location. Each resulting fact must cover that location. Abstract micro edges have
`*` premise marks; nested calls are abstract or use a `*`-premise record. Concrete
mode can contain either kind of nested call.

The current iteration obligation is to show that run 1 selects the needed mode
and the backward hand-off preserves it: abstract flow is covered by abstract
patterns/records, and each flow that needs a concrete mark is covered by a concrete
pattern/record. This obligation remains open (§11.2). Historical concrete-run
iteration and exact narrowing do not prove it. Mark normalization can enlarge a
pattern; the three restriction limits in §6.4 also limit narrowing claims.

After a complete forward run the driver applies these stop rules:

| Reason | Condition and effect |
|---|---|
| `STOP_RULE` | The report has no DEMAND entry. There is no next backward sink seed. The confirmed report is final. |
| `NO_DEMAND_EDGE` | No demand-layer edge, summary, or added-fact link remains. A DEMAND report entry can still lack joint conjunction support; keep it in the output. |
| `POLICY` | The configured iteration policy stops. Keep the last complete report. |
| `ABNORMAL` | Timeout, memory guard, or exception. Discard the incomplete run and keep the last complete report. |

Stopping at a complete forward run is the design contract. Its full current
no-loss proof, method exclusion, and round-to-round narrowing are open. A method
with no demand edge and no seed in its call subtree is intended to be crossed by
records and analysed only from zero; do not apply an additional pruning rule
without establishing those conditions.

---

## 7. Representation (the optimization)

The concept of a conclusion is a set of path facts. The representation groups path edges into TREES.

### 7.1 Initial fact and premise key

```kotlin
/** The initial fact (premise): one linear path. Its mark is * or a concrete mark (never *∖X). It is also the premise
 *  key of a premise set with one member (below). */
class InitialAp(
    val base: AccessPathBase,
    val path: PathNode?,           // interned, linked from the root node; no [any], $ or mark accessors (W4, W5)
    val tail: Tail,                // ANY_TAINT: a must-premise, only in a forward restricted run (W8), concrete mark
    val exclusion: ExclusionSet,   // Empty in run 1; a restricted run can emit the exclusion of a `*/E` demand
                                   // (the FLOW form of a `*/E` pattern, F72);
                                   // a must-premise has the exclusion of its `[any-taint]/E` added fact (W8)
    val mark: MarkSlot,            // `*` (a FLOW premise: run 1, and since F72 a restricted run) or concrete
) : PremiseKey {
    fun toPattern(): Pattern       // the list form of §3.4, for the reference forms
}

/** The premise SET of an edge (§4.6): never empty. ONE member is the common case (the zero fact, a policy fact, an
 *  answer, an emission): the InitialAp itself is the key, with no wrapper. Two or more members: a PremiseSet, a
 *  canonical array (sorted by the intern id, no duplicates): an ND edge. No member is the zero fact (§4.6), every
 *  member has a concrete mark, and its edges are TAINT (§7.2). Both are interned, so equal keys are the same object. The layer is not part of the premise key; every store
 *  key that needs the layer has it as a separate part. */
sealed interface PremiseKey {
    val size: Int
    fun member(k: Int): InitialAp
    val isZero: Boolean            // the premise set {zero}: the one member is the zero fact
    val nonZeroCount: Int          // §4.6: 0 a zero-to-fact edge ({zero}), 1 a fact-to-fact edge, >= 2 an ND edge
}
class PremiseSet(val members: Array<InitialAp>) : PremiseKey      // size >= 2
```

`PathNode` is the current `AccessPath.AccessNode` without the `[any]`, `$` and mark accessors.

The tail and the exclusion are part of the premise key. So the must-premise `(x, p, [any-taint], E, T)`, a
must-premise at the same path with another exclusion, and the `[any]` premise `(x, p, [any], T)` are different
premise keys, with their own edges and summaries: a must-premise starts in the normal layer, the `[any]` premise in
the demand layer (§6.5). One path gets several when must and may added facts meet a demand at that path (§6.3). A
published summary edge and a record keep the tail and the exclusion of their premise (§8.4, §8.7; Lean: the
publication of `PipelineAnyTaintEx.sysDRX` carries the must flag and the exclusion of the callee premise, and a link
the added fact with its flag and its exclusion; round 1 `PipelineAnyTaint.sysDRT`).

### 7.2 Conclusions: three kinds

The conclusions of one edge group have ONE CONCLUSION KIND (§1). The premise mark separates FLOW from the other two
kinds: a premise with the mark `*` has FLOW conclusions, and the zero premise or a concrete premise has REACH or TAINT
conclusions. The base of the conclusion separates REACH from TAINT: a conclusion on the zero base (the zero fact) is
REACH, a conclusion on every other base is TAINT. So one premise set can have conclusions of two kinds, in two edge
groups: for example `{zero}` has the zero fact (REACH) and the result of a source (TAINT).

| Kind | Premise set | Conclusions | Normal layer | Demand layer |
|---|---|---|---|---|
| REACH | `{zero}` (a zero-to-zero edge); in the backward run also a concrete requirement `{jb}` that reached an unconditional source or an end-fact action (§9.2) | the zero fact | one bit | one bit |
| FLOW | one initial fact with the mark `*`: a policy fact or a position answer (run 1), or, since F72, the FLOW premise of a `*` pattern (a restricted run, §6.3) | abstract marks only: `*∖X`, with `X` the mark exclusion of the tree | `*` leaves, with the exclusion of the tree | `[any]` leaves, no exclusion |
| TAINT | `{zero}` (a source), concrete initial facts (also a must-premise `[any-taint]/E`, forward restricted runs only), or a set of them: EVERY premise set with two or more members (an ND edge, §4.6) | concrete marks only | `$` and `[any-taint]` leaves, with ONE exclusion of the tree for its `[any-taint]` leaves | `$` and `[any]` leaves, no exclusion |

S7 keeps abstract premises abstract and concrete premises concrete. S8 and the
canonical demand guard exclude abstract `$` conclusions. W2 makes star leaves
normal; W6 makes may-any leaves demand; W8 permits only concrete normal must
leaves. Thus typed FLOW has no `$`, must tail, or concrete mark, and typed TAINT
has no star tail or mark exclusion. The layer names a TAINT any leaf: must in
normal, may in demand. Distinct must exclusions require distinct TAINT groups.

These are required representation rules, including in restricted runs with FLOW
premises. `CurrentKinds` checks local base tail/mark rules and paired-demand
guards for its earlier-selector sequence. It does not establish F77 sequence
preservation. Full W6/layer, X, conjunction, and static packing preservation
remains open ([proof-status.md](proof-status.md)); the old concrete-run kind
catalog does not establish it. Runtime constructors enforce the full kind/layer
rules and must not silently widen or discard an unsupported result.

```kotlin
/** The conclusions of one edge group at a node. */
sealed interface Facts { val base: AccessPathBase; val layer: Layer }

/** REACH: the zero fact (§2.4) is at the node. */
class Reach(override val layer: Layer) : Facts { override val base get() = AccessPathBase.Zero }

/** FLOW: a trie of paths; a leaf flag at a node is the leaf `p.*` (normal) or `p.[any]` (demand), with the mark `*∖X`. */
class FlowTree(
    override val base: AccessPathBase,
    override val layer: Layer,
    val exclusion: ExclusionSet,   // the exclusion of the `*` leaves (W1); always Empty in the demand layer
    val markExclusion: MarkSet,    // X of `*∖X`; empty for most trees
    val root: FlowNode,            // FlowNode(leaf: Boolean, accessors, children)
) : Facts

/** TAINT: a trie of paths; at a node, the marks of the `$` leaves and of the any leaves. In a normal tree `any` holds
 *  the marks of the `[any-taint]` leaves (W8), in a demand tree the marks of the `[any]` leaves (W6). */
class TaintTree(
    override val base: AccessPathBase,
    override val layer: Layer,
    val exclusion: ExclusionSet,   // the exclusion E of the `[any-taint]` leaves (W8); always Empty in the demand layer
    val root: TaintNode,           // TaintNode(exact: MarkSet, any: MarkSet, accessors, children)
) : Facts

// Both node types keep `boundedDepth`: the max number of counted accessors on a path below; the O(1) limit check.
```

Rules:

* T1. Merge rule 1: two trees with the same key merge by union.
* T2. Merge rule 2 (normal FLOW trees): two trees with the same premise, layer, mark exclusion and EQUAL content merge
  into one tree with the intersection of the exclusions (`Tree.rule2_den`). Not valid for different contents. The
  same for two normal TAINT trees with the same premise and layer and EQUAL content: the intersection of the
  exclusions of their `[any-taint]` leaves (§3.3; argued, §11.2).
* T2'. Merge rule 2 for marks (FLOW trees): the same with the mark exclusions (`Tree.rule2_mark`).
* T3. No union of exclusions or mark exclusions across trees. No merge across layers. No merge across conclusion
  kinds.
* T4. `add` returns the new part only (the delta), as `mergeAddDelta` does today. Exception: when merge rule 2 shrinks an
  exclusion or a mark exclusion of a stored tree, the delta is the WHOLE merged tree with the new exclusion.
* T5. Inside one demand-layer tree, an `[any]` leaf at `p` may absorb every leaf below `p` with the same mark (FLOW: every
  leaf; TAINT: the leaves with the mark `m` of the `[any]` leaf). The denotation does not change. NO `$` LEAF IS
  ABSORBED UNDER `[any-taint]` IN A NORMAL TREE: inside one normal TAINT tree with the exclusion `E`, an `[any-taint]`
  leaf at `p` may absorb only the `[any-taint]` leaves with its mark `m` at `p ++ r` with `r ≠ []` and `E` admitting
  `r`; it never absorbs a `$` leaf at or below `p`. The reason: a later primitive cleaner `part` row of §4.7 that demotes the
  `[any-taint]` leaf (the demotions of §2.2) does not touch a `$` leaf apart from the cleaner, and that `$` leaf must
  stay normal and exact. Direct AP example: the normal tree `{x: [any-taint] (T), x.g: $ (T)}`, then `clean(x.f, exact, T)`:
  the cleaner demotes `x.[any-taint] (T)` to `x.[any] (T)` in the demand layer, and `x.g.$ (T)` is disjoint from the
  cleaner and stays normal, so `sink(x.g)` stays CONFIRMED; after an absorption only `x.[any] (T)` would remain, and
  `sink(x.g)` would be a DEMAND entry. The same holds for the subsumption (§8.1). The F74 named field action
  instead keeps normal `x.[any-taint]/{f}` as well as the separate `$` leaf; its local X vectors are in §10.13.
* T6. Intern the nodes, the mark sets and the exclusion sets.

### 7.3 Delta-concat on a tree

The computation of §4.1 on all paths of one tree at once. `Ec` is the tree exclusion (of a FLOW tree, or of the
`[any-taint]` leaves of a normal TAINT tree, §7.2). The results are grouped by their
new conclusion kind, layer, exclusion and mark exclusion (§7.2). The kinds make the mark gate simple (§4.1 step 4): on
a FLOW tree an edge with a concrete premise mark `T` gives no fact, only the request `T` (run 1) if `T ∉ X` (a
restricted run: no fact and no request, F72); on a
TAINT tree it keeps the leaves with the mark `T`. A micro edge from the zero fact applies only to REACH (the zero keep
edge gives REACH, a source gives TAINT).

1. Walk `from.path` from the root node of the tree. On each proper prefix node, read its payload as the case `above`
   (`r` is the rest of `from.path` after the node):
   * a `*` leaf gives a result only if `Ec` admits `r` (Lean: `Tree.cS`);
   * an `[any]` leaf always gives a result;
   * an `[any-taint]` leaf of a normal TAINT tree gives a result only if `Ec` admits `r` (W8);
   * a `$` leaf gives nothing (no overlap).

   Each result of a `*` or an `[any]` leaf is `[any]` (or `$` for a `$` target) at `to.path`, in the demand layer. A
   result of an `[any-taint]` leaf follows the table of the case `above` of §4.1: `$`, `[any-taint]/Et` or (a `*`
   target) `[any-taint]/E` with the edge exclusion, in the layer of the tree; or `[any]` in the demand layer (a may
   `[any]` target). Apply the mark gate to each leaf mark (step 4). Lean: `Tree.contribB`. STATIC EXCEPTION (run 1,
   a statement micro edge with its premise on `S`; the rule is §4.10 item 1, the test §4.1): a `*` leaf of an identity static `*` edge at the root path or at a class gives
   no result; it raises the position request for `from.path` cut to at most two accessors.
2. At the node of `from.path` take the subtree `U`. Filter its root by the premise tail (with the exclusion of a
   must-premise) and the edge exclusion.
3. Transform `U` by the target tail:
   * a `*` target: the child subtrees (`r ≠ []`) keep the tree exclusion and are re-rooted under `to.path`; the root
     `*` leaf (`r = []`) gets the exclusion `Ec ∪ E`, so it goes to another tree; a root `[any-taint]` leaf gets the
     exclusion `Ec ∪ E` too (the exclusion edge, W8), so it goes to another TAINT tree, in the layer of the tree;
   * an `[any]` target (a may): fold `U` into one `[any]` payload with the Empty exclusion, in the demand layer (W6);
   * an `[any-taint]` target (a taint edge, or a summary conclusion `[any-taint]/Et`): fold `U` into one
     `[any-taint]` payload with the exclusion `Et` (Empty for a micro edge), in the layer of the tree;
   * a `$` target: fold `U` into one `$` payload with the Empty exclusion, in the layer of the tree.

   Both `$` and `[any-taint]` targets require a concrete premise mark. A FLOW leaf therefore gives no fact after
   the mark gate; no below-case correlation check adds demand (§4.1).
4. Apply the mark gate per payload mark, then the target mark (§4.1 steps 4, 5; a `*∖X` target adds `X` to the mark
   exclusion and stops the concrete marks in `X`). A payload mark that the gate sends to a request gives no result for
   that mark; it raises the request `(m, i, T)` (run 1, §4.5; a restricted run raises none, F72).
5. Layer and normal form (§4.1 step 6). A result is in the demand layer if the tree is in the demand layer, if the
   applied edge is a demand-layer summary edge (§4.3), or if a step above says so. Then a result with the `[any]` tail
   is in the demand layer (W6), a demand-layer result with the `[any-taint]` tail becomes `[any]` with the Empty
   exclusion (W8), and a result
   with the `*` tail and a concrete mark, or in the demand layer, becomes `[any]` with the Empty exclusion, in the
   demand layer (W2).
6. Where §4.4 puts a cut point (for example the statement transfer), apply the field limit with `boundedDepth`; cut
   paths go to the demand-layer tree. The cut is not part of `concat` (§4.1).
7. Route each result by its own layer bit.

Cost: the walk visits `|from.path| + 1` nodes; the transformation visits `U` only. The concept form visits every path
fact (`Tree.lean`: `applyTreeE_mem`, `applyTreeE_den`, `walkSteps_le`).

### 7.4 The restriction on a tree

The required result is the union of §6.4 restriction results for every input leaf,
with the full premise test. It preserves marks, mark exclusions, and layers.
Rebuild trees by their complete kind keys, including must-tail field exclusions.
Current base-model tree/reference equivalence is in
[proof-status.md](proof-status.md). The indexed demand query can reduce candidate
tests; it does not replace per-leaf mark/tail tests.

An optimized walk may share retained subtrees and move any leaves to the demand
path, but it must equal this reference. In particular an any leaf at an exact
exit demand becomes `$`; an abstract mark must pass `marksMeet`; retained star
leaves keep their correlation. The old `restrictS/U` tree theorem does not prove
this current mark-aware operation. The X-tail tree shortcut remains open.

### 7.5 Interning

Tries are hash-consed bottom-up as today. The walk of §7.3 has no memo: it shares every unchanged subtree, and a leaf
map keeps an identity memo of the nodes that it maps (F68). A memo of the walk is a later optimization, if a profile
asks for it. `boundedDepth` is part of the node, not of the hash.

### 7.6 Relation to the current code

The prescan (§1) still runs the current core. So the new AP lives beside the current types.

| Part | Decision |
|---|---|
| `AccessorIdx` | Reuse. |
| `AccessPath.AccessNode`, accessor interning (`AccessorInterner.AccessorStorage`) | Adapt: an interned path node; the accessor and mark tables keep only the field and the class storages (`ap-impl.md` §2, §3.1). |
| `AccessTree` merge, `mergeAddDelta`, interners, identity caches | Reuse the algorithms for `FlowNode` and `TaintNode` (§7.2). |
| `AccessBasedStorage` trie | Adapt for the path indexes of §8 (`PathTrie`, `ap-impl.md` §7.2). |
| `EdgeStorage` | Replace by the conclusion group: one premise key, every base and kind (`ap-impl.md` §4.3). |
| `AccessPathBaseStorage` | Not used: it rejects the zero base; the conclusion group keys its trees by base (`ap-impl.md` §7.3). |
| Exact-key subscription maps | Reuse with the new fact types. |
| `StatementSummaryBuilder`, `buildReversed` | Adapt (`interpreter.md`). |
| `InitialFactAp`, `FinalFactAp`, `ApManager` | New types `InitialAp`, `PremiseKey` and the conclusion kinds `Reach`, `FlowTree`, `TaintTree` (§7.2). They do not implement the old interfaces. |
| `Edge` (`ZeroToZero`, `ZeroToFact` requires Universe, `FactToFact`) | The premise key and the conclusion kind (§7.2): `ZeroToZero` is REACH; `ZeroToFact` and a concrete `FactToFact` are TAINT, or REACH if the conclusion is the zero fact (backward `{jb} → zero`); an abstract `FactToFact` is FLOW. Each has its layer. |
| `NDFactToFact` | A TAINT edge whose premise key has two or more premises that are not the zero fact (§4.6). |
| `DeepAccessorExclusion` | Replaced by the mark exclusion `*∖X` of the edge (§4.7). The old exclusion is tied to an abstraction point at a depth, so it is lost when the field limit cuts the path; the mark exclusion is not tied to a position. |
| `FactReader` (mark as accessor suffix) | New reader over `(path, tail, mark)`: `check` (§4.9). |
| `FactTypeChecker` | The type filter primitive (§4.8). |
| `EdgeNonUniverseExclusionMergingStorage` (union merge) | Replace by merge rules 1 and 2. |
| `TaintSinkTracker` (rule assumptions), vulnerability records | The standing conjunction store (§8.9); the vulnerability store of §8.10. |
| `TreeInitialFactAbstraction` | Replace by §6.2 (run 1) and §6.3 (restricted runs). |
| `MethodAnalyzer` depth gate, `[any]` depth charge | Not used: the field limit is the only depth bound (§4.4). |

---

## 8. Storages

Every store has a CONCEPT (a list of entries with a filter) and an INDEX. `Store.lean`, `RestrictedStore.lean` and
`PipelineStore.lean` prove that the index returns every entry that the filter returns (the first two also give the
cost of the lookup), for these stores: the subscription store (§8.4), the demand store (§8.6), the record store
(§8.7) and the mark requests of the request store (§8.8). The position requests, the conjunction store, the
vulnerability store and the source hit store have no index theorem. The OWNER of each store is the method that §5.2
names. Lifetimes:

* RUN: one run (one direction, one field limit).
* HAND-OFF: from the end of one run until the next run has read it. This is what one run passes to the next: the
  demand edges (§8.5), the DEMAND entries of the report (§8.10) and the source hits of a backward run (§8.11). The next
  run reads them as its demand or as its seeds (§9.2 defines the hand-off; §8.6 stores the demand).
* PERSISTENT: all runs of one analysis.

The method key of a method is the same in every run (§1), so a key that contains it stays valid across runs.

PATH TRIES. Several indexes are path tries keyed by `base :: path` (the base, then the accessors of the path). For the
key `k`, `lookupPrefixes(k)` returns the entries whose key is a prefix of `k` (the entries at or above `k`, also at
`k`), and `lookupExtensions(k)` returns the entries whose key has `k` as a prefix (the entries at or below `k`, also at
`k`). An OVERLAP query is `lookupPrefixes(k) ++ lookupExtensions(k)`, with the entries at `k` once. Each lookup gives
candidates; the store then applies the exact test that the section names (`overlap`, `applicable`, `inside`, §3.4).

### 8.1 Method edge store (RUN, per method)

* Key and value per conclusion kind (§7.2): REACH: `(statement, premise key, layer)`, one bit. FLOW: `(statement,
  premise, layer, base, exclusion, mark exclusion)`, a `FlowTree`. TAINT: `(statement, premise key, layer, base,
  exclusion)`, a `TaintTree` (the exclusion of its `[any-taint]` leaves, W8; Empty in the demand layer).
* `add(...)` merges by rule 1, 2 or 2' (T1; T2 and T2' for FLOW, T2 also for a normal TAINT tree) and returns the
  delta (T4), or null if the fact adds nothing.
* Subsumption inside one layer: the store drops a conclusion if a stored conclusion of the same premise key, layer and
  conclusion kind subsumes it (`Subsume.subsumesB`): the same base; the same mark (TAINT), or the stored mark `*∖Xs`
  and the dropped mark `*∖Xn` with `Xs ⊆ Xn` (FLOW); `[any]` at `p` subsumes every fact at or below `p` with its
  mark (the demand layer); `[any-taint]/Es` at `p` subsumes, in its layer (the normal layer: W8), only the
  `[any-taint]` facts with its mark that it covers (§3.4 `covers`: `[any-taint]/En` at `p ++ r` with `r ≠ []` if `Es`
  admits `r`, or at `p` with `Es ⊆ En`), and NEVER a `$` fact at or below `p` (a later demoting cleaner `part` row
  must leave that `$` fact normal and exact: the example of §7.2 T5); `*/Es` subsumes `*/En` at the same path if
  `Es ⊆ En`. `subsumes_sound` proves that every pair of
  the dropped fact is a pair of the stored fact (the model kind `.any` is both any tails; with the exclusion of
  `[any-taint]` it is argued, §11.2). The premise key includes the tail and the exclusion, so a fact of a
  must-premise never subsumes a fact of the `[any]` premise at the same path, or the reverse (§7.1).
* A demand-layer conclusion never subsumes a normal one (`Subsume.recordSubsumesLB_layer` for records).
* The unchanged propagation (`Sequent.Unchanged`, §4.2 step 2) skips the store: its items do not go through `add`.
  The method analyzer keeps them in their own queue, with a set that discards the repetitions, and it takes them
  before every other item. When that queue is empty, it drops the set (`analyzer-core.md` §4.3).
* The store lives for its run. No store of a run stays for a trace resolution: the trace resolution is out of scope
  (§8.10).

### 8.2 Initial fact store (RUN, per method; callee)

* `initials: Set<InitialAp>`: the initial facts of the method in this run (the zero fact, the emissions and the
  answers).
* The store keeps no support. The support of a premise set (§4.9 condition 3) is a property of the whole run: the
  confirmation computes it after a complete run, at the barrier, over the links of the added fact stores (§8.3;
  `analyzer-core.md` §7.5). Support is a property of a premise SET, not of one initial fact: two premises that are each
  supported at a different call do not make their set supported (`NDConfirmed.CexSites`).

### 8.3 Added fact store (RUN, per method; callee)

* A path trie keyed by `base :: path`. Each added fact keeps the set of its caller edges
  `(caller method, caller premise key, layer of the caller edge, call statement)`. One (added fact, caller edge) pair
  is a LINK.
* Each link also keeps the LAYER OF THE ADDED FACT on that link. It can differ from the layer of the caller edge: a
  cleaner `part` row can move the added fact to the demand layer (§4.7). Condition 3.2 of the confirmation (§4.9) reads
  it: only a link with a normal added fact supports a premise. An added fact with an any tail is `[any-taint]` on a
  normal link and `[any]` on a demand link (W8); the emission reads it (§6.3). A normal `[any-taint]` added fact has
  its exclusion `E`, and the store keeps it with `E` (an `[any-taint]/E` and an `[any-taint]/E'` added fact at one
  path are two added facts); an `[any]` added fact has none (W8 (b)). Lean: the flag `am` and the exclusion `aex` of
  `AnyTaintEx.XObj.added` (round 1: `AnyTaint.TObj.added`).
* A new link is event E2 of §5.3, also when the added fact exists already. The store gives the request store (§8.8)
  every new link.
* The standing request match (§4.5, §4.10) reads the store with OVERLAP queries (§8): the key is `base :: path` of
  the request premise (mark request) or `S :: p` of the requested position (position request).

### 8.4 Subscription store (RUN; caller)

* Key: the callee. Value: the subscriptions `(caller edge, call statement, added fact a)`.
* On a published summary edge of an initial fact `j`: find the subscribed `a` that satisfy `j` (§4.3), and apply. The
  index is a path trie keyed by `base :: a.path`. In run 1 (`applicable`: `a` at or below `j`) the query is
  `lookupExtensions(j.path)`. In a restricted run (`satI`: `j` inside `a`) the query is `lookupPrefixes(j.path)`.
  A FLOW premise uses their union, `around(base :: j.path)` (§8.7 R2): canonical FLOW matches by overlap; other
  FLOW matches by `inside OR applicable` (§4.3).
  Then the store applies the exact test of §4.3 (`PipelineStore.deliver_run1`, `deliver_restricted`; the F72 query
  of a FLOW premise has checked base query equivalence in `AbsStore`;
  X-tail/tree pipeline integration remains open, §11.2).
* On a new subscription: apply every published summary edge of every initial fact that `a` satisfies, and every record
  that applies to `a` (§8.7 R4).
* Only this store answers the "satisfies" query.

### 8.5 Run summary store (HAND-OFF, per method; callee)

* Key: `(premise key, layer)`. Value: the exit conclusions (§7.2: REACH, FLOW trees or a TAINT tree). A summary edge is an exit fact after the exit order of
  `interpreter.md` §4.7 (event E4 of §5.3). Only the normal exit makes a summary edge (`interpreter.md` §3.4).
* In a restricted run the callee restricts each new summary edge by every demand pattern of the method (§6.4). It
  PUBLISHES the results to the subscription store. Run 1 publishes every summary edge as it is.
* At the end of a complete run, the store adds the summary edges that §8.7 R1 admits to the persistent record store.
* The hand-off reads publications (§9.2). Select demand on the raw summary:
  keep every demand-layer summary, every normal multi-premise summary, and every
  normal backward zero-premise summary. Omit all other native normal singleton
  summaries; R1 persists them as records. Tail, mark, exclusion, and read-view
  layer do not add a selection condition.
  Selection is routing for the next run, not a layer change. A selected normal
  conjunction remains normal and still requires every premise when applied forward (§4.6).
* At each summary delta, build publications (§6.4; run 1 keeps the raw delta).
  If selected, store those same publications with `addDemand`; otherwise store
  no demand piece. Selection is uniform within one premise-key/layer group.
  The hand-off reads `demandEdges()` only. Persist the eligible raw summaries,
  including all exclusions, at the barrier (R1).
* The premise key keeps its tail and its exclusion: a must-premise and the `[any]` premise at the same path have
  their own summaries (§7.1). A native normal singleton forward summary with the `[any-taint]` tail on its premise
  or its conclusion is a record (§8.7 R1); the backward run has no `[any-taint]` (W8 (d)).
  A normal singleton must-premise record also gives no callee demand. Its
  backward read view can produce a demand-layer result in the caller (R3).
  Any resulting caller summary follows the same selection rule. Selected must
  demand pieces become may patterns without exclusions (§9.2).

### 8.6 Demand store (RUN, read only; callee)

Store demand patterns by method key, with a path-map index keyed by
`D-c.base::D-c.path`. The receiving run reads an immutable store built at its
preceding barrier. Zero demand is implicit for every forward method key.
Validate the normalized marks and typed-tree construction conditions of §6.1.

| Use | Candidate query | Full test |
|---|---|---|
| Emission for added fact `a` | `near(a.base::a.path)`: ancestors, node, and strict descendants | §6.3 emission, with actual tails, marks, and exclusions |
| Restriction for premise `j` | `covering(j.base::j.path)`: ancestors and node | §6.4 whole-premise inside, conclusion mark, and tail reduction |

`near` is needed for emission because an added fact can be above or below the
entry chain. A successful restriction requires the demand entry path to be a
prefix of the whole premise path, so descendants are unnecessary. A candidate
query never replaces the full test. With a multi-member premise, union the member
queries and apply the member-wise union rule in §6.4. Test the selected whole
member, and retain the complete original premise set in every publication.

The base emission query is checked in `Abs.emitW_lookup_equiv`; the current
mark-aware restriction query is checked in `CurrentDemand.restrict_index_equiv`.
Both equal a full scan as result membership. Restriction lookup walks at most key
length plus one path nodes; emission also enumerates the descendant subtree.
Both run full tests on candidate payloads. The maintained 33-demand
example needs 1 full restriction call instead of 33. Index construction is outside
that cost measure; no list ordering or duplicate multiplicity claim is made.

### 8.7 Persistent record store (PERSISTENT)

The native store retains the raw summary's initial kind and conclusion kind.
A valid initial kind is zero, concrete exact/any (including a forward must
premise), or abstract star with empty field exclusion. An abstract premise has
only FLOW conclusions: abstract marks and star-normal or any-demand tails.
Insertion and retained unions must preserve these conditions. Generic reversed
native shapes have the guards below. Transient read views have the separate R3
contract; they do not have to satisfy native initial-kind conditions. Current base tail/mark preservation is checked by `Current.native_record_kinds`,
`revRec_kinds`, and `record_kinds_union`; the full X integration remains open.
These theorems do not prove all typed FLOW layer flags; W6 and full packing
preservation remain open. The concrete runtime stores must enforce the full
kind/layer requirements of §7.2.
Evaluate R1 persistence and §8.5 hand-off selection on the raw summary, before
restriction. Record eligibility does not define the summary's layer. A normal
multi-premise conjunction summary is not a singleton record. Read-view
construction and application have separate mark, shape, and exclusion guards.

One direction-neutral store for the forward and the backward analysis:

```kotlin
enum class Direction { FORWARD, BACKWARD }

/** A record: a normal summary edge with ONE premise (R1), in the orientation in which it was derived. */
class Record(
    val method: MethodKey,
    val direction: Direction,          // FORWARD: premise = entry fact; BACKWARD: premise = exit fact
    val premise: InitialAp,            // the one member of the premise set; the zero fact only for FORWARD (R1);
                                       // the tail ANY_TAINT (a must record, with its exclusion) only for FORWARD
                                       // (R1); backward read views give DEMAND results (R3)
    val conclusion: Facts,             // normal layer (§7.2): REACH (forward {zero} → zero, backward {jb} → zero),
                                       // a FLOW tree (a `*` premise, may carry a mark exclusion) or a TAINT tree
)

interface RecordStore {
    fun add(record: Record)
    fun byEntry(method: MethodKey, addedFact: Pattern): Sequence<Record>      // the added fact of a link
    fun byExit(method: MethodKey, fact: Pattern): Sequence<Record>            // a fact at the exit side
}
```

`byEntry` returns the candidate records for a reader in the direction of the record. `byExit` returns the candidate
records for a reader in the other direction: a requirement of the backward run at the forward exit reads the forward
records, and a forward fact at the forward entry reads the backward records (R3).

Rules:

* R1. THE PRINCIPLE: a summary edge is persisted (and so read reversed, leaf by leaf, by R3) ONLY IF IT IS COMPLETE
  (§1). A demand-layer
  summary edge is never persisted or read as a reversed record. The store adds only a complete summary edge whose premise set
  has ONE member:
  * of a forward run: every normal such edge, also a zero-premise edge (the premise set `{zero}`), also an edge with
    `[any-taint]/E` leaves (with their exclusion), also an edge of a must-premise (a MUST RECORD, with the exclusion
    of its premise: end-exact, S14; `AnyTaintExExact.recs_of_DRX_valid`);
  * of a backward run: only a normal edge whose premise is NOT the zero fact. The backward run has no `[any-taint]`
    (W8 (d)), and its `[any]` results are in the demand layer (W6, §9.1), so a backward record has no any tail. A
    zero-premise backward edge can come from a seed (§9.2): the seed relates the zero location to every location of
    its sink pattern (Lean: `Backward.seed_den`), which is not a flow of the reversed program, so it is never persisted
    and never read as a reversed record. The backward records are exact forward records (Lean: `AnyTaintSim.backRecT`,
    `backRecT_sub`, `backRec_exact`, under the hypotheses of `BExact.revRecs_exact`). A backward summary through the
    reversal of a conjunctive micro edge is in the demand layer (§9.1, THE REVERSAL OF A CONJUNCTION), so it is never
    added.

  An edge whose premise set has two or more members (§4.6) is never added.

  Each persisted native summary gives no callee demand (§8.5). A read view's
  demand-layer output belongs to the caller; it does not add demand for the
  summarized method.
* R2. `byEntry` is a path trie keyed by `base :: premise path`. For the added fact at `q`, the lookup is
  `lookupPrefixes(base :: q)` for `applicable` (`Store.forward_equiv_prefixes`, `applicable_mem_candidatesB`) and
  `lookupExtensions(base :: q)` for `inside`, then the exact test. The union `around(base :: q)` returns every record
  that one of the two tests accepts (`PipelineStore.record_lookup`). `byExit` is a path trie keyed by
  `base :: leaf path` for EACH leaf path of the conclusion tree. Its lookup is `around(base :: q)` for the fact at
  `q`: a leaf whose reversal covers the fact is at or above it, and a leaf whose reversal lies inside the fact is at or
  below it (`PipelineStore.record_lookup`, with the reversed premise as the key).
* R3. A reader in the other direction computes one transient read view per
  permitted leaf of a native record. R2 finds candidates by the old conclusion's
  base/path. R4 tests the view's actual premise, including its mark and exclusion.
  The view carries a premise `Pattern` and one `Conclusion` with its layer. Do not
  construct an `InitialAp` or a persisted `Record` from it, emit it, or apply
  `startFact`: starting a concrete star would lose its exclusion and demote it.

  For a native normal singleton **forward** record `P → x.p.t(T)`, use the special must view only if both sides
  have concrete marks, each tail is `$` or `[any-taint]`, and at least one tail is `[any-taint]`. Each `$` side has
  empty exclusion. Keep the native store and R1 guards. Derive the view by two independent conversions:

  * New premise from the old conclusion: keep `x.p.$(T)`; change `x.p.[any-taint]/E(T)` to `x.p.*/E(T)`.
  * New conclusion from the old premise: keep `P.$(U)` in NORMAL; change `P.[any-taint]/Ej(U)` to `P.[any](U)`
    in DEMAND, forgetting `Ej`.

  The guard and conversions equal `CurrentMustReverse.readView`
  (`CurrentSimplifications.MustViews.readView_factored_equiv`). Preserve the old conclusion's `E` on the new star
  premise. The two marks can differ: each stays with its original side.
  Application also keeps the input's layer: a demand requirement gives demand
  results even through a normal view.
  “Ordinary `[any]`” means the may tail; W6 makes its result demand-layer.
  The star is a **field tail**, still with concrete mark `T`. It does not bypass
  the concrete mark gate or raise a restricted-run request.

  A view with an exact old premise is exactly the annotated converse (`CurrentMustReverse.exact_converse`),
  including different marks and paths. Its exact target discards the incoming
  suffix: `x.p.h.$(T)` gives `P.$(U)`, not `P.h.$(U)`. An excluded first accessor
  gives no result. S14 exactness of the native record is still required to infer
  real program flows from this local relation equality.
  The must-premise views retain every annotated converse pair after weakening
  (`CurrentMustReverse.must_any_converse_covers`, `must_exact_converse_covers`).
  This inclusion is not converse program-flow exactness. A must-premise record
  is only end-exact. Its views give demand results and cannot create a normal
  backward record (`must_application_demand`). A normal reversal would claim false
  converse pairs for `ret=p.g` (`AnyTaintExact.CexRev.cex_rev`).

  Other native record shapes use generic leaf reversal (§9.1), only when
  mark-reversible. This includes `$→$` and canonical abstract `*→*/E` leaves.
  An arbitrary concrete-star native premise is not an allowed substitute for `P`:
  a star-to-star view would wrongly correlate its suffix with the old any target.
  Keep generic micro-edge reversal separate from these record views.

  Exactness of generic reversed backward records still needs S11's mark/shape/zero
  conditions, exact backward input records, and the filter/validity conditions
  in [proof-status.md](proof-status.md). Dropping backward type filters does not
  prove exactness. Full current read-view/closure integration remains open.
  F77 omits callee demand for all native R1-eligible normal singleton summaries,
  including every special must view above. R1 retains the whole raw record. `byExit` finds its
  read view; the next forward run can reuse the native record. A demand-layer
  view result continues analysis in the caller. Its layer does not request
  reanalysis of the summarized method.
* R4. In its own direction, a native record applies by `applicable || inside`
  (§4.3) in later runs. It is not restricted. A must native record keeps ordinary
  transfer on its `inside` part; its `applicable`-only part gives DEMAND results
  with any must exclusion forgotten. It needs every admitted premise location
  (`AnyTaintExact.CexApp.cex_app`).

  An opposite-direction read uses R3's actual `Pattern` and result layer. S14
  exactness of the native record is required for normal results. A must native
  record is end-exact; its backward view is demand-layer. Normal records add no
  false pair under these conditions, subject to §11.1's reference-semantics and
  filter/end-fact limits. The local certificates and full integration limits are
  in [proof-status.md](proof-status.md).

  An exact or empty-exclusion star premise matches a concrete added fact at a
  common location by one of the two tests. In the F76 branch, the star read premise
  keeps `E` and matches canonical concrete `$` or may `[any]` requirements covering
  an admitted location (`CurrentMustReverse.concrete_read_matches`). Exact chains
  give the old exact premise without their suffix (`normal_view_transfer`).
  Abstract requirements still need an abstract premise mark;
  concrete record views do not raise restricted-run requests.
* R5. After run 1, emission reads only demand (§6.3). A record or read view never
  causes an emission. Native normal summaries admitted by R1 give no callee
  demand. A method with no selected demand and no seed in its call subtree has
  only zero analysis.
  Returning calls use the native record in the same direction and the permitted
  read view in the other direction. Records remain available even when their
  internal sources are not next-run seeds. Full current no-loss iteration,
  including the F77 selection, remains open (§11.2).

### 8.8 Request store (RUN 1 only, per method; callee)

* Entries: `(method, initial fact, mark)` for a mark request (§4.5) and `(method, initial fact, position)` for a
  position request (§4.10), plus the answers already emitted. A request stands for the whole run.
* Index: per method, a path trie (§8). A mark request is keyed by `base :: path` of its premise `i`. A position
  request is keyed by `S :: p`, the requested position, because the match reads `(S, p)` and not the premise.
* On every new link (added fact, caller edge) of the method (§8.3), find ALL standing requests that overlap the added
  fact (`Store.standing_complete`, for the mark requests): for a mark request the premise `i`, for a position request
  the position `(S, p)`. Answer each one, or propagate it through THIS caller edge (§4.5; §4.10 items 2 and 3; §5.3
  event E2).
* On a new request, read every existing link of the added fact store whose added fact overlaps it (§5.3 events E5 and
  E7).

### 8.9 Conjunction store (RUN, per method)

* Entries: `(conjunctive micro edge or sink alternative, statement, literal index) → set of (fact, premise key,
  layer)`: the facts that overlap a literal of a conjunctive micro edge and pass its mark gate (§4.6), standing for the
  run. The literal index is the place of the literal in its conjunctive micro edge or sink alternative (§1). A
  conjunctive exit source keys its entries by its exit statement (§4.6; `interpreter.md` §4.7, D31).
* The same entries for a conjunctive sink: the sink edges that trigger a literal of a sink alternative (§4.9). An
  entry stays for the run, also when the global-state rule drops the evaluated part from the summary edge
  (`interpreter.md` §4.7, D30; only on a zero-premise item on `S`: a state that the method or its callees set): a
  later item can complete the combination with it. A caller-set `S` fact is stored as the input of its literal, and
  it is not dropped (§11.1).
* On a new fact for a literal: combine it with the stored facts of the other literals of the same conjunctive micro
  edge or sink alternative (one per literal, every combination). A result has the union of the premise sets WITHOUT
  THE ZERO FACT, and `{zero}` if every input has `{zero}` (§4.6). For a conjunctive sink, each combination is a sink
  edge set of the vulnerability: a sink witness (§4.9, §8.10).
* The same for a callee summary with several premises at a call statement: `(callee summary, premise index) → links`
  (the added fact with its caller edge; §4.6, event E6).

### 8.10 Vulnerability store and the report (PERSISTENT)

A reported vulnerability (Kotlin: `Report.Entry`, `analyzer-core.md` §10; the store keeps the `SinkWitness`es) has
these fields:

| Field | Content |
|---|---|
| key | `(rule, method, statement)` (Kotlin: `VulnerabilityKey`): the sink rule, the METHOD of the method key WITHOUT its context, and the sink statement. |
| sink witnesses | The sink witnesses (§1) of the key in the run of its state: the first complete forward run that confirmed it, or the latest complete forward run. The store keeps the witnesses of every run, each with its run. |
| state | CONFIRMED if a complete forward run confirmed one of its sink witnesses (§4.9); else DEMAND. |

A sink witness (Kotlin: `SinkWitness`) has these fields:

| Field | Content |
|---|---|
| alternative | The sink alternative of the rule at the statement that the witness triggered (§1). |
| method key | The method key of its sink edges, with the context. The confirmation reads the support of the premise sets in this method key (§4.9 condition 3). |
| pattern | DERIVED, not stored: the sink patterns of `alternative` of the rule at the statement in the method key (`SinkRule.patterns`; the same in every run, `interpreter.md` I5): the sink pattern `s` (§4.9); for an unconditional sink, the zero fact (§4.9); for a conjunctive sink, the literal patterns. |
| sink edges | One sink edge (its premise key, its layer and its sink fact), or, for a conjunctive sink, one sink edge set (one edge per literal, §4.9) with the set of the sink facts. |
| confirmed | Whether the witness is confirmed (§4.9). Only a complete forward run confirms a witness. |
| end facts | The end facts of the witness, if the sink rule has end-fact actions (§4.9; `interpreter.md` §4.1, END FACTS). |
| run | The run that reported the witness. |

* The key has no context. One sink statement that is reached in several contexts is ONE vulnerability. The same key in
  two runs is the same vulnerability. One entry of the report holds the sink witnesses of its key (the table above).
* Each sink witness keeps its own alternative and its own method key. Two sink witnesses with different alternatives
  or different method keys are different sink witnesses: they never merge.
* A CONFIRMED vulnerability persists. It is real for the reference semantics (§3.5), modulo the expected
  false-positive sources of §11.1.
* At every complete FORWARD run, every real vulnerability is reported by that run or was confirmed by an earlier
  complete forward run (§6.6). A DEMAND entry after complete forward run `n` (reported by run `n`, confirmed by no
  complete forward run so far) is seeded (§9.2). If complete forward run `n + 2` does not report it (no vulnerability
  with the same key), it is REFUTED: under B it is not real.
* THE REPORT of the analysis reads the COMPLETE forward runs only (§1, §6.6). It holds:
  * every vulnerability that a complete forward run confirmed, with the state CONFIRMED. This state is final;
  * every demand vulnerability of the LATEST complete forward run (§1), with the state DEMAND (a DEMAND entry).

  A key that is in both groups has the state CONFIRMED. An INCOMPLETE run (forward or backward) adds nothing to the
  report and refutes nothing: it is not the fixed point of its rules (S6), so no theorem applies to it, and it
  confirms nothing (§4.9). If run 1 is incomplete, no forward run is complete: the report has no entry and the output
  is empty, and the status of the analysis (`AnalysisEnd`, `analyzer-core.md` §7.1) gives the cause. This is a
  deviation from today: a full scan that times out outputs the vulnerabilities that it found before the timeout
  (`TaintAnalyzer.kt:157-223`; `ap-history.md` F68).
* THE TRACES. The trace resolution is out of scope (§0). No store of a run stays for it (§8.1). THE OUTPUT of the
  analysis holds EVERY entry of the report: the CONFIRMED vulnerabilities and the DEMAND vulnerabilities of the
  latest complete forward run (`ap-history.md` F68, which amends F67 (4)). Each one gets a SIMPLE trace: the trace
  with only the sink statement (Kotlin: `TracePathGenerationResult.Simple`). Its method key is the method key of a
  confirmed sink witness if the entry has one, else of its first sink witness. The analysis logs the number of the
  entries per state (CONFIRMED, DEMAND). So the output holds every real vulnerability that the report holds (§0.1,
  §6.6). A DEMAND entry can be false (§11.1). The end-fact check of today's trace step is out of scope too: a known
  gap (§11.1).
* The DEMAND entries of the report after a complete forward run are also its HAND-OFF: the next backward run reads
  their sink witnesses of that run as its sink seeds (§9.2). A CONFIRMED vulnerability is final, so it is not seeded,
  also when a later run reports it only in the demand layer. Only THE TRIGGER OF AN END FACT (§9.2) fires the sink
  seeds of an alternative of a CONFIRMED vulnerability: a requirement that reaches its reversed end-fact edge needs the
  witness of the trigger. A backward run reports no vulnerability (§9.2, the backward sink role).
* THE `[any-taint]` TAIL (F69). A vulnerability whose taint comes from an `[any]`-target source (for example the
  whole-object DTO source of a Spring entry point) can be CONFIRMED (§4.9; a field read after a setter of another
  field, confirmed in run 1: `AnyTaintExCases.S.run1_email_confirmed`; a getter in a callee, confirmed in forward run
  3: `AnyTaintExCases2.G.run3_confirmed`). Its entry is CONFIRMED, and the state is final. A field that a setter or a
  strong write overwrote is not reported at all: the exclusion removes it (`AnyTaintExCases.S.run1_name_not_reported`;
  the round-1 rule kept it a DEMAND entry in every forward run, through the setter record). Only a vulnerability that
  rests on a may `[any]` (a pass rule with an `AnyField` target) or on another demotion of §2.2 stays a DEMAND entry.
  The output still holds every DEMAND entry (F68). Before F69 every `[any]`-target source finding was a DEMAND entry
  (W6); after F69 it is one only when it rests on such an approximation (`ap-history.md` F68, F69).

### 8.11 Source hit store (HAND-OFF; backward run)

* Entries: `(method key, statement, source edge)`: the unconditional sources that the backward run reached (§9.2,
  SOURCE HITS). The backward analyzer of the method adds an entry when it applies the reversed source edge of the
  statement to a requirement and gets a result.
* The next forward run reads the entries as its source seeds (§6.1 rule 6): per (method key, statement), the set of
  the source edges that may fire.
* A source edge has an identity that is the same in both runs: its method key, its statement and its forward micro
  edge (Lean: `σ M n e`). The interpreter gives the same micro edges in every run (`interpreter.md` I5).

---

## 9. Reversal and the backward direction

### 9.1 Micro-edge reversal and record read views

Generic `rev(i,f)` (Lean `revEdge`, `revKinds`) reverses a micro edge. R3 also
uses it for ordinary record leaves. F76 must-summary record views use R3's
separate side conversions; they do not change interpreter micro edges.

The generic reversal moves the edge exclusion to the new conclusion. The new
premise has empty field exclusion. It changes marks as follows: the new premise
mark is `i.mark` if `f.mark` is abstract, else `f.mark`; the new conclusion mark
is `f.mark` if abstract, else `i.mark`. A reversal requires mark reversibility:
an abstract conclusion mark or a concrete premise mark.

| Forward premise tail | Forward target tail | Reversed premise | Reversed target |
|---|---|---|---|
| `*` | `*/E` | `*` | `*/E` |
| `$` | `$` | `$` | `$` |
| `[any]` | `*/E` | `*` | `*/E` |
| `$` | `*/E` | `$` | `$` |
| `*` | `$` | `$` | `[any]` |
| `[any]` | `$` | `$` | `[any]` |
| `$` | `[any]` | `[any]` | `$` |
| `*` | `[any]` | `[any]` | `[any]` |
| `[any]` | `[any]` | `[any]` | `[any]` |
| `$` | `[any-taint]` | `[any]` | `$` |
| `[any]` | `[any-taint]` | `[any]` | `[any]` |

These are generic algebra rows, not a list of permitted native records.
S8 forbids the micro row `$→*`. S11(g) requires empty premise exclusion for
`*→$` and `*→any`. S15 permits `[any-taint]` only as a source target, with empty
initial exclusion; no micro edge has a must-premise. No backward side has
`[any-taint]`. A forward `[any]` premise is a source condition literal; AnyField
pass premises are rule errors.

Generic reversal is the exact converse when the edge is mark-reversible and
has exact shape (no nonempty premise exclusion on `*→$` or `*→any`). With empty
premise exclusion this is `Reverse.rev_exact_of_empty_premise`. This theorem
covers generic algebra, not end-exact must records or F76's separate reader.
The native record shape and current exactness limits are in §8.7 and
[proof-status.md](proof-status.md).

A reversed **may** micro edge (forward target `[any]`) gives every result in the
demand layer, including `$` results. A reversed source (forward target
`[any-taint]`) follows ordinary transfer and keeps the requirement's layer.
The forward target tail identifies the two cases; no rule-kind flag is needed.
This rule differs from F76's star premise for a cached must-summary conclusion.

The backward run has no conjunction. Reverse `{a, b} → c` into independent
branches `c → a` and `c → b`, and put every result in the demand layer. The
forward conjunction can be NORMAL; the split branches demand its prerequisites.
One branch alone does not establish the forward conjunction:

```text
Forward:  {p.$(A), q.$(B)} → ret.$(C)     NORMAL
Backward: ret.$(C) → p.$(A)               DEMAND
          ret.$(C) → q.$(B)               DEMAND
```

If the first branch were persisted as NORMAL, its forward read would be
`p.$(A) → ret.$(C)`, dropping the requirement for `q.$(B)`. Then
`sink_C(M(source_A(), untainted))` could be confirmed falsely. DEMAND branches
cannot become records and instead guide the next forward run to check the joint
rule. `CurrentConjunction.ReverseWitness.both_support` and
`a_alone_not_support` check the local support distinction. Full restricted ND
integration remains open.

### 9.2 The backward run

A backward fact is a requirement: taint at a covered location can reach a sink.
The backward program swaps method entry/exit and reverses CFG edges. It uses the
same AP operations, F75 cleaner, emission, and stores; the interpreter owns the
reversed statement/call order ([interpreter.md](interpreter.md) §4.9).

| Part | Backward rule |
|---|---|
| Statement | Reverse each micro edge by §9.1. Touch forward touched bases and gen-only target bases. A target base untouched forward also gets an identity keep edge, so reverse transfer retains it. |
| Conjunction | Reverse into one edge per literal, all results in the demand layer. They cannot become records. |
| May target | Reverse the may micro edge with every result in the demand layer. Every backward `[any]` result is demand; there is no `[any-taint]`. |
| Cleaner | The primitive is its own reversal. A field action is reversed strong write, temporary-root cleaner, reversed read (F74). Reverse action order. |
| Type filter | Apply none. This enlarges backward demand and can make reversed records inexact (§11.1). |
| Call | Into the callee, reverse forward return bindings and call alias edges. Back to caller, reverse forward entry bindings, including `zero.*→zero.*`. Alias bases also keep identity over the callee. |
| Emission | §6.3 with the requirement as added fact: `*` patterns give FLOW requirements; concrete patterns preserve concrete marks. No request rule exists. |
| Sink check | None. Reversed sources are transfer edges, not sink checks. |

Zero starts at each root's forward exit, passes each call, and enters each reached
callee at its forward exit directly, without emission or a demand pattern. Plain
reversal alone would send zero upward and miss requirements in callees. S11 keeps
zero on each path to a seed.

Seed the DEMAND witnesses of the latest complete forward report. At each witness
method key/statement, fire all alternatives of that sink rule where zero reaches
it. Each literal gives its concrete sink pattern, cut by this backward limit. An
`[any]` seed is demand-layer. An unconditional sink adds no requirement because
zero already exists. At a sink call, seed after its reversed cleaners.

End facts need one extra trigger. When a reversed end-fact action of alternative
`A` applies, it transfers to zero **and seeds A's sink patterns** once per
`(method key,statement,A)`, also for an already CONFIRMED vulnerability. An end fact
exists only after that trigger succeeds; the next forward run needs its inputs.
Without this rule a demand sink relying on a confirmed sink's end fact can lose
its source seed. End-fact integration remains outside the base proof model.

A zero-premise backward summary returns to each caller's zero at the call site
that entered the callee. Apply the reversed binding back and field limit without
restriction or satisfaction test: the balanced-return exception. A nonzero
summary uses normal restriction and satisfaction. Persist eligible raw nonzero
backward summaries; never persist a zero-premise backward seed path (§8.7).
Forward records apply through their transient leaf read views (§8.7 R3).

Record a source hit when a concrete requirement reaches an unconditional source
and its reversed source edge applies. A `*` requirement cannot pass that concrete
mark gate. A caller's concrete mark passed through a FLOW summary can yield a hit.
The hits are next forward source seeds. A recorded call needs no inner source
seed; its record supplies the result. Applying a read view is not a source hit;
record a hit only at an actual reversed source edge. The source filter never removes records.

Hand-offs read stored publications selected by §8.5. A selected NORMAL conjunction
remains NORMAL; the builder creates one independent pattern per premise member.
These patterns guide analysis and are not independently sufficient forward flows.
Selection uses the raw summary's layer, premise count, and backward zero-premise
case. Normalize
each pattern mark `*∖X` to `*`; keep concrete marks. Preserve star-tail **field**
exclusions. Convert a forward `[any-taint]/E` conclusion or must-premise to a
pattern `[any]` without `E` or must flag. Patterns never have `[any-taint]`.
The summary/record itself retains all exclusions; normalization changes demand
only. Swapping patterns is not the relational reversal of §9.1.

| Direction | Demand construction |
|---|---|
| Forward to backward | For each selected publication `j→g'`, add `(D-c=g',D-p=j)`. With several premise members add one pattern per member. Native normal singleton summaries supply records only. |
| Backward to forward | For each method add `(zero,none)`. For every zero-premise backward summary with conclusion `gb`, add `(gb,none)`. For each selected nonzero publication `jb→gb'`, add `(gb',jb)` per premise member. Native normal nonzero singleton summaries supply records only. |

Normalize both pattern sides in both directions. The backward hand-off also gives
source hits; the forward hand-off gives DEMAND sink witnesses. Records persist
across the barrier as §8.7 requires. Zero work repeats in each run; its localization
is not specified. The current local normalization and hand-off shape results are
checked, while the full mode/iteration contract of §6.6 remains open.

---

## 10. Checked results

The current proof matrix, assumptions, executable witnesses, and remaining
obligations are in [proof-status.md](proof-status.md). Historical theorem catalogs
are in [proof-history.md](proof-history.md); they are not current proof claims.

### 10.1 Soundness of run 1 — `Coverage.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.2 Local lemmas — `Core.lean`, `SharedExcl.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.3 Exactness and invariants — `Exact.lean`, `Invariant.lean`, `Closed.lean`, `Confirmed.lean`, `W6.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.4 Concept against optimization — `Tree.lean`, `Store.lean`, `Subsume.lean`, `RestrictedStore.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.5 Reversal — `Reverse.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.6 ND edges — `ND.lean`, `NDExact.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.7 Restricted runs and the iteration — `Restricted*.lean`, `Backward.lean`, `BackwardExact.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.8 Statics — `Statics.lean`, `StaticsIter.lean`, `StaticsConfirmed.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.9 Source seeds — `ForwardSeeds.lean`, `PipelineSeeds.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.10 Conclusion kinds and the zero-drop — `Kinds.lean`, `NDZ.lean`, `NDZero.lean`, `NDZeroThms.lean`, `NDZeroBase.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.11 The `[any-taint]` tail — `AnyTaint*.lean`, `PipelineAnyTaint*.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.12 The hand-off of the demand edges — `Handoff*.lean`, `PipelineHandoffDriver*.lean`

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

### 10.13 F72 to F77: checked results and open obligations

See [proof-status.md](proof-status.md) for current scope and
[proof-history.md](proof-history.md) for the historical catalog.

## 11. What the proofs do not cover

### 11.1 Expected false-positive sources

The theorems say that a confirmed vulnerability is real for the reference semantics (§3.5): the program that the
micro edges describe. On the JVM the micro edges over-approximate the real program at the points below. There a
confirmed vulnerability (and a normal edge, and a record) can be false. These are EXPECTED false-positive sources: the
analysis keeps their results in the normal layer and does not refine them.

* A type filter on a `*`, `[any]` or `[any-taint]` fact. The filter checks the concrete path only, so the fact keeps
  the locations below its path that a real value cannot have (§4.8). A normal `[any-taint]/E` fact claims EVERY
  admitted location below its path, also a location that the static type does not have; the exactness theorems are
  for the valid locations only (S13).
* A reversed backward record. The backward run does not type-filter, so the record has no type filter (§8.7 R3; the
  reversal theorems `BExact.rev_record_exact` and `revRecs_exact` assume `Exact.FiltUp`).
* The weak alias write. The alias base keeps its old content (S2, `interpreter.md` A3, gap G7).
* The constructor pass-over. An added fact also passes over a constructor call, so the constructor does not
  overwrite the caller facts (`interpreter.md` §3.5, gap G8).
* The default identity of an unresolved callee. It is a weak update of the receiver and of the arguments
  (`interpreter.md` §3.7): the real callee can overwrite a field, and the facts of the caller keep it.
* THE WEAK UPDATES AND `[any-taint]`. A weak update (the alias write, the constructor pass-over, the default identity
  of an unresolved callee, an alias of a call result, `interpreter.md` AC2) keeps an `[any-taint]` object whole: the
  exclusion of W8 comes only from a strong write on the object itself. Example: `a = dto.address; a.city = clean;
  sink(dto.address.city)`. The write is strong for `a`, and the alias edge to `dto.address.city` is gen-only, so `dto`
  keeps `(dto, ., [any-taint], T)` with no exclusion, and the sink is CONFIRMED, although it is not real. The same for
  a deep write through a getter, and for `dto.setName(clean)` on an unresolved `setName` (the default identity). Such
  a false positive is now a CONFIRMED entry; before F69 it was a DEMAND entry (the result of an `[any]`-target source
  was `[any]`, W6). A `$` fact has the same false positive today (`interpreter.md` §0.1, G7, G8, §3.7).
* The catch local. A catch statement does not kill its local, so an old fact on it stays (`interpreter.md` G9).
* The path-insensitive reading. A conjunction can combine literals that hold on paths that exclude each other (§4.6),
  and a negated mark literal counts as true (§3.5).
* A pass rule with a mark literal. It is a rule error that is not rejected (`interpreter.md` §1.3), and the
  interpreter applies it without its mark literals (`interpreter.md` §4.2, D24), so it also fires where a literal is
  false.
* The global-state rule (`interpreter.md` §4.7 step 3, G2, D30). An exit sink drops an evaluated part on `S` only
  from a zero-premise item: a state that the method or its callees set. A caller-set `S` fact is evaluated (it can
  report, and a conjunctive literal stores it as its input, §8.9), but it is not dropped: it returns to the caller
  through the callee summary, also through the run-1 FLOW summary and its record. As today: today's exit sinks run
  only on zero-premise edges (`JIRMethodExitRuleProvider.kt:18-19`), and only a reached sink drops
  (`JIRSequentTaintUtil.kt:76-85`). So a later sink in the caller can see the state, and the exit sink of the caller
  can evaluate it again. The model has no global-state rule (a gap of the interpreter, `interpreter.md` §0.1 G2).
* The end facts of a sink (§4.9; `interpreter.md` §4.1). An end fact is a context-insensitive zero-to-fact edge, as
  today: its premise set is `{zero}`, whatever the premise set of its sink edge. So its summaries reach every caller,
  also a caller that does not supply the premise of the sink edge, and its records are not exact. The backward run
  applies the reversed end-fact edge to every requirement, with no check of the trigger (`interpreter.md` §4.9; it
  also fires the sink seeds of the alternative, §9.2 THE TRIGGER OF AN END FACT), so a backward record through it
  reverses into a forward zero-premise record that is not exact either (§8.7 R3). The model has no end facts.
* A sink rule with end-fact actions: a KNOWN GAP for now. Today the vulnerability check of the trace step reports such
  a vulnerability only if one of its end facts reaches the end of the analysis (`VulnerabilityChecker`). That check is
  out of scope with the trace resolution (§8.10 THE TRACES). So every vulnerability of such a rule that the report
  holds is in the output, also one whose end facts never reach the end of the analysis (`ap-history.md` F67, F68).

THE DEMAND ENTRIES OF THE OUTPUT. The output holds the DEMAND vulnerabilities of the latest complete forward run too
(§8.10; `ap-history.md` F68). They are not confirmed: a DEMAND entry rests on a demand-layer edge, an
over-approximation of §2.2 (the case `above`, a lost correlation, the field limit cut, W2, W6, and the other
demand-layer operations of §2.2, whose `[any-taint]` results W8 (b) names `[any]`), so it can be false also for the
reference semantics. Only a later complete forward run that does not report it
refutes it (§8.10). The historical soundness theorems cover the output of their
versions: at every complete forward run, every real vulnerability
is reported by that run, in some layer, or was confirmed by an earlier complete forward run, and the report keeps
every confirmed vulnerability (`HandoffMain.iteration_generalN`; with the `[any-taint]` tail and its exclusion
`HandoffXIter.iteration_generalNX`; the earlier hand-off: `Backward.iteration_general`, `W6.iteration_general6`,
`AnyTaintExCov.iteration_reportsX`). They do not prove F77 iteration coverage.

THE PRECISION LIMITS OF THE `[any-taint]` TAIL. These keep a real `[any]`-target source finding a DEMAND entry. They
are precision losses, not false positives. The first five items are the demotions of §2.2, the complete list. (An
exclusion does not demote: a strong write into an `[any-taint]` object and a setter summary or record `*/{f}` keep it
normal with `f` in its exclusion, §4.1.)

* The field limit cut (§4.4): an `[any-taint]/E` fact over the limit becomes `[any]` in the demand layer, and the
  exclusion is dropped. `AnyTaintExCases.CUT.cut_reports` gives historical zero-limit data outside the current API;
  current tests use a positive limit. A later forward run with a larger limit repairs it (§6.6).
* A primitive cleaner `part` row of §4.7 other than the `atAndBelow` and `below` rows one accessor below the fact: the `exact`
  cleaner at or one accessor below an `[any-taint]` object, or any cleaner two or more accessors below it. The whole
  object becomes `[any]` in the demand layer, in every run (`AnyTaintExCases.CL.exact_result`): no shape exists for
  "every location but one" (`AnyTaintExExact.CexExactCleaner.cex_exact_cleaner`). A named field action applies
  this primitive to its fresh temporary (F74). Its strong-write keep edges retain normal facts outside the
  field. For a single field, EXACT also returns a demand fact on that field, while the outside-field fact
  stays normal (`FieldCleanerX.exact_vector`). This also applies to the summary rewriter.
* A may target: a pass rule with an `AnyField` target is a may (§2.3): its results stay `[any]` in the demand layer
  (W6), also when the real callee writes every field; in the backward run every result of its reversal is in the
  demand layer (§9.1).
* A demand input: an `[any-taint]` fact through a demand-layer summary edge or record, a demand link (an `[any]` added
  fact), or a conjunction with another input in the demand layer gives a demand result (§2.2, §4.3, §4.6).
* The must-record demotion: a must record that applies by `applicable` only gives a demand result (§4.3).
* Run 1 does not confirm an `[any-taint]` finding through a getter in a callee (§6.2: the FLOW summary is the case
  `above`, a demand edge); a later forward run with the demand does (`AnyTaintExCases2.G.run3_confirmed`).
* THE MERGES IN A NORMAL TAINT TREE (§3.3, §7.2 T2 and T5, §8.1). They keep the location set, so they are exact, but a
  later demotion acts on the merged fact. So no `$` leaf is absorbed or subsumed under an `[any-taint]` leaf (§7.2
  T5). Merge rule 2 and the absorption or subsumption of an `[any-taint]` fact by an `[any-taint]` fact stay: after
  them a later cleaner `part` row that demotes the kept `[any-taint]` fact also demotes the locations of the other
  fact, also where that fact alone is disjoint from the primitive cleaner. Primitive AP example: `(x, ., [any-taint], {f}, T)` subsumed by
  `(x, ., [any-taint], {}, T)`, then `clean(x.f, exact, T)`: the kept fact becomes `(x, ., [any], T)` in the demand
  layer, and `(x, ., [any-taint], {f}, T)` alone would be disjoint from the cleaner and stay normal. This direct
  AP example is not the F74 named field action, whose outside-field keep branch stays normal.
* F76 removes the cached source-record reuse limit: an exact-premise must
  conclusion uses a concrete `*/E` backward read premise and preserves `E`.
  A must forward premise instead gives a may `[any]` demand result (§8.7 R3).
  Native record insertion and generic micro-edge reversal are unchanged.
* THE HAND-OFF DROPS THE EXCLUSION (§9.2): the backward demand of an `[any-taint]/E` summary conclusion is the pattern
  `[any]`, so the backward run can follow requirements on the excluded locations, which no forward fact reaches. For
  the same reason one step of the narrowing of §6.6 can be coarser on the patterns that the hand-off reads (no
  exclusions) than with the exclusions (`HandoffX.XVec.v_inside_only_with_excl`). Historical F71 X runs checked exact
  location narrowing under their guards (`HandoffNoStar.narrowing_canonX_loc_exact`). Current composed narrowing
  and combined X integration remain open (§6.6).

### 11.2 Other limits

The current rules are defined, but a local demand contract is not a full iteration
proof. The remaining obligations are:

* mode coverage and contract B for F72/F75, including F74 lowering, F76 read views,
  and F77 structural selection;
* the combined `[any-taint]`/field-exclusion closures, entry guard, static rule,
  and restricted conjunction support;
* current round-to-round narrowing, method exclusion, source-seeded confirmation,
  and the pipeline/driver instance;
* optimized tree shortcuts beyond the checked per-leaf reference, dominance under
  store subsumption, and integration of aliases, end facts, and call rule forms.

The checked base results cover emission with the §6.1 guards, mark-aware summary
restriction, normalization, candidate indexes, normal-edge exactness, and the
stated reversed-record conditions. [proof-status.md](proof-status.md) gives the
exact scope. The F75 cleaner always requests its selected mark in run 1 for a
same-base abstract fact, even when that mark is already excluded; suppressing this
request is not an authorized optimization.

The reference program is alias-free, location-level, and per root. Static state
does not cross roots. Exception flow does not cross calls, and catch blocks do not
read `exc` (interpreter gap G1). The model does not implement rule conditions,
the interpreter, or trace resolution. Interpreter gaps are in
[interpreter.md](interpreter.md) §0.1. Expected false positives are in §11.1.

Subscriptions and links use exact deduplication. Subsumption of stored objects
must not be used to justify FLOW join completeness without its own proof.
Current tree reduction must agree with §6.4 per leaf; the old `restrictS/U` tree
proof does not prove the mark-aware meet. Local merging, interning, and candidate
index results do not prove the composition of every optimization.

---

## 12. The formal model

The constructive Lean model is in [lean](lean). The root [ApSpec.lean](lean/ApSpec.lean)
imports its modules. [proof-status.md](proof-status.md) states the current module
map, build commands, executable checks, and axiom audit. Historical module details
are in [proof-history.md](proof-history.md).

## 13. Test plan (TDD)

The test vectors, regressions, and implementation checks are in
[validation-plan.md](validation-plan.md). Current checks use the F72/F75 demand
operations, F76 record views with their guards, and F77 structural selection; older closure results apply
only to their stated version.
