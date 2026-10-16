# IR interpreter — specification

The [unified run-1 contract](run1-field-writes.md) specifies static read/mark
requests, initial gates, replay and batching together with the following
field-preservation rows on F1: complete overwrite kills and disjoint keeps
remain. Only a surviving residual that needs a field-exclusion update moves
to DEMAND with Empty E. Partial E-requiring
cleaner results use a DEMAND may cover. Prescan computes StaticMayWrite
from all logical effects and resolved callees. Its default identity is an
ordinary stored summary; ordinary calls apply it without a special F1 branch.
Only the edge layer changes on demotion; known tails do not become ANY. Later strong
lowering and stable logical occurrence IDs remain unchanged.

The same [unified contract](run1-field-writes.md) overrides the
static behavior below where it conflicts. In particular, ContainsMark adds
only mark interest; run-1 has selective exclusion-update demotion and ordinary
precomputed static summaries without root initial emission. Run-2/later uses strong
actual-query analysis and modeled normal returns, without F1 footprint
identity shortcuts or executable F1 records. Logical rule/action occurrence
IDs stay stable across semantic cache flavors. Runtime is unchanged.

Status: design spec, the companion of [ap.md](ap.md). This document is normative. `ap.md` defines the access path (AP),
its operations and its primitives. This document defines how the analyzer interprets the IR of the JVM (JIR) and of Go
(GoIR) with these primitives. The two documents together define the analysis. The names of today's code in this
document identify the parts that the implementation reuses or replaces; they are not definitions. The design history
is in [ap-history.md](ap-history.md); it is not normative.

The selected JVM run-1 contract is F78 [run1-requests.md](run1-requests.md), with proof scope in
[run1-request-proofs.md](run1-request-proofs.md). It supersedes the older full-query/role/E/mx request and deferred
answer-memo policy. Runtime `27e22bb1ed871651143538783473cb0d4fcbcf3e` is unchanged. Historical scalar and Go
construction models retain their stated scope; they do not supply a new JVM request slot.

Language: ASD-STE100 Simplified Technical English.

---

## 0. Scope and relation to ap.md

This document defines:

1. the micro edges of the non-call statements (§2);
2. the micro edges of the call statements: the bindings, the result, the aliases (§3);
3. the order in which the interpreter applies the rules, in the forward run and in the backward run (§4);
4. the points where the interpreter applies the type filter, the cleaner, the ND conjunction and the request (§5).

It does not define the AP operations. It uses them by name:

| ap.md operation | Function | Used in |
|---|---|---|
| `concat` as a micro edge (ap.md §4.1, §4.2) | one micro edge on one fact; it applies to every fact on its base that overlaps its premise (I2) | §2, §3 (bindings, the unresolved callee §3.7), §4.1 (the rule statement of a call), §1.4 (the `RemoveAllMarks` kill on `S`) |
| `concat` as a summary edge (ap.md §4.1, §4.3) | only to an added fact that satisfies the premise | §4.5 step 5.2 |
| field limit (ap.md §4.4) | cuts a result, never a micro edge (I10) | §2.1 step 6, §4.3, §4.5 steps 4 and 6, §4.7 |
| mark request (ap.md §4.5) | run 1 only | §5.4 |
| static plan (F78, ap.md §4.10) | joined effective-form/resolved-CG structural and slot-mark closure; no full-query role or Position state | initial emission (§0.3), §2.1 step 4, §5.4 |
| ND conjunction (ap.md §4.6) | an edge keyed by a set of premises | §5.3 |
| `clean` (ap.md §4.7) | removes marks at a position | §5.2 |
| `filter(base, may)` (ap.md §4.8) | removes the facts of `base` whose path `may` rejects | §5.1 |
| sink check (ap.md §4.9) | compares a sink pattern with a fact | §4.1, §5.4 |
| reversal (ap.md §9.1, §9.2) | the backward form of a micro edge | §4.9 |
| emission, storages (ap.md §6, §8) | abstraction and stores | not used directly |

Notation. ap.md §1 defines the common notation: the micro-edge sides `x.p.*`, `x.p.$ (T)`, `x.p.[any] (T)` and
`x.p.[any-taint] (T)` (the `[any-taint]` tail always has a concrete mark, and only a forward run has it, ap.md W8),
the exclusion subscript `a →_{f} b`, the exclusion `E` of a forward `[any-taint]` fact (`(x, p, [any-taint], E, T)`,
also written `[any-taint]/E`, ap.md W8), the accessors `[e]` and `<C>`, the fact tuple, the touched-base set `{x, y}`
and the bound fact. This document adds only:

| Form | Meaning |
|---|---|
| `#i` | The tuple slot `i` (Go, §1.2). It is written `#i` so that it is not the `$` tail. |
| `<G>` | The class accessor of the Go global `G` (§1.2). |
| `y\{f}`, `→ null` | Only in a column "Today" (§2.4, §6). An edge of today's `StatementSummaryBuilder` in today's notation: a side `y.f` is the base `y`, the path `.f` and today's abstract tail; `y\{f}` is the part of `y` outside the field `f` (an edge `y\{f} → y` keeps every location of `y` except those at or below `y.f`; today it also refines a premise that does not exclude `f`); `\{e}` is the same for the element accessor; `→ null` is an edge with no target: it only refines the premise. |

Common rules:

| # | Rule |
|---|---|
| I1 | The interpreter gives each statement a STATEMENT SUMMARY: the touched bases, the micro edges, and the type filters of each touched base: the OPERAND filters, on the input (§2.1 step 3), and the RESULT filters, on the results (the lhs filter, §2.1 step 5; the binding-back filters, §3.1). One base can have both (`x = x.f`, §2.2; analyzer-core.md §4.9 `StatementSummary`). The AP applies it (ap.md §4.2, the statement transfer). Every micro edge reads from a touched base (ap.md S10). |
| I2 | Micro edge application is UNGUARDED. The AP applies every micro edge to every fact on its base that overlaps its premise, in both cases of ap.md §4.1: at or below the premise, and above it. A fact above the premise of a micro edge gives an uncorrelated result in the demand layer. A forward `[any-taint]` fact (ap.md W8) above the premise gives a result in the layer of the fact: every location at or below the fact carries its mark. Its exclusion `E` must admit the step down to the premise, else the edge gives nothing. An exclusion of the edge goes to the result (ap.md §4.1); only an `[any]` target (a may, W6) demotes the result. There is no refinement. Two exceptions: (1) F78 STATIC ROOT CODE: the special S-root summary seed keeps ordinary code transfer but a deeper static read gives no wide DEMAND/Position result. Normal field slots and the caller-read ownership/frame adapter supply the semantics (ap.md §4.10). Rule evaluation is skipped only on that root component (§0.3); no normal abstract class initial is created. Field components use the ordinary AP operations. (2) THE ALIAS EDGES AT A CALL apply only to the results that §3.8 AC3 and AC4 select. |
| I3 | A read never changes an exclusion. Only a STRONG WRITE adds the written accessor to the exclusion of the identity edge of the written base: `a.* →_{f} a.*`. |
| I4 | A call binding is a micro edge (I2 applies). Its premise and its target have the `*` tail and the mark `*` (ap.md S10, S11). A callee summary edge is not a micro edge: it applies only if the added fact satisfies its premise (ap.md §4.3). |
| I5 | The interpreter is the same in every run. The differences: requests exist only in forward run 1 (§5.4). In a forward restricted run, the core fires an unconditional source (a micro edge from the zero fact to another base) only if it is a source seed (ap.md §6.1 rule 6; analyzer-core.md §4.7). The backward run reverses the micro edges and applies no type filter; the core records the sources that it reaches (§4.9). |
| I6 | MARK WELL-FORMEDNESS (ap.md S7): no micro edge or binding has a premise mark `*∖X`, and a micro edge with a concrete target mark has a concrete premise mark (a source: `zeroMark`; a conditional source: `T`). Without it a normal-layer edge can claim a cleaned mark. |
| I7 | NO UNIVERSE (ap.md S8): a micro edge with a `$` premise has a concrete premise mark; a micro edge with a `$` target has a concrete premise mark; no micro edge has a `$` premise and a `*` target; no micro edge, binding or initial fact has the kind `*/Universe`. Every exclusion that the interpreter makes is a finite set of accessors. |
| I8 | NO PRECOMPUTED CALLEE SUMMARY (ap.md §6.3). The interpreter precomputes no callee summary. So a callee summary with a `*` premise in a restricted run comes only from the AP: a persisted run-1 record, its reversal in a backward run (ap.md §9.1), or, since F72, a summary of a FLOW premise that the restricted run emits itself for a `*` demand pattern (ap.md §6.3; `ap-history.md` F72). Before F72 a restricted run had only the first two. An EMPTY METHOD is a method with no instruction (JVM: an empty instruction list, for example a native or an abstract method; Go: a function with no body). It is not analysable, and the analysis never analyses it. The call resolver drops an empty method from the callees of a call (§3.6). A call whose every resolution result is an empty method is an UNRESOLVED call (§3.7: the default identity and the pass rules). No summary exists for an empty method, also no identity summary (D28). An unresolved callee is a statement summary (§3.7), not a callee summary. |
| I9 | PRECISE AND COMPLETE MICRO EDGES (ap.md S1, S2, §4.2): every micro edge (a statement edge with its alias edges, or a call binding edge; not a callee summary edge) gives exactly the concrete flows of its statement (ap.md §3.5). A micro edge has NO LAYER: the layer belongs to the propagation edge, and only the AP operations change it (ap.md §2.2). |
| I10 | NO FIELD LIMIT ON A MICRO EDGE: a micro edge keeps its full paths (also an alias path `c.q.p` of any length). The field limit applies to the results of an application (ap.md §4.4 lists the cut points): the statement step, also the read sources (§2.1 step 6, §4.4); the return of a call, also the source results, the end facts of a sink at a call and the pass-rule results (§4.5 steps 4 and 6); the results of the entry rules (§4.3); the results of the exit rules (§4.7); the conjunction result (§5.3; the Lean model has no cut there, ap.md §11.2); and the backward seed (§4.9). |
| I11 | BACKWARD CONTRACTS (ap.md S11, items (a) to (g)). The interpreter makes these true: (a) every binding has the `*` tail and the mark `*` on both sides (§3.1), so no binding has an `[any]` or an `[any-taint]` target (ap.md S10; Lean `AnyTaint.BindNoAny`, a hypothesis of the kinds invariant of run 1 `AnyTaintExKinds.D6X_any_conc` (round 1: `AnyTaintSim.D6T_any_conc`; necessary there: `AnyTaintSim.CexKinds.cex_bindNoAny`)); (b) every statement micro edge is MARK-REVERSIBLE (ap.md §9.1): its target mark is abstract (`*` or `*∖X`), or its premise mark is concrete. Every row of §2, §1.4 and §4.1 has this form; (c) no FORWARD call binds the zero base back (§3.3); the backward binding back has the reversed zero binding `zero.* → zero.*` (ap.md §9.2); (d) every statement that touches the zero base has the keep edge `zero.$ (zeroMark) → zero.$ (zeroMark)`, and no other micro edge, no binding back and no conjunction has its target on the zero base (the only binding into the zero base is `zero.* → zero.*`, §3.1; Lean `NDZeroBase.NoZeroGen`): the read statement with a read source (§4.4), the rule statement of a call (§4.1), and the method start and the exits with their sources (§4.3, §4.7); no cleaner acts on the zero base; and no type filter is on the zero base (so a type filter on the zero base accepts the empty path, as ap.md S11 (d) asks); (e) for every method that is a root or the callee of a call, every node on a CFG path from the method entry has a CFG path to the method exit: the implementation wires code that never returns to an exit node (§4.9); (f) every sink pattern has the tail `$` (`ContainsMark`) or `[any]` (`ContainsMarkOnAnyField`) (§4.2); the backward seed of an `[any]` pattern has the tail `[any]` and is in the demand layer (W6; the backward run has no `[any-taint]`, I14, §4.9); (g) every statement micro edge and every call binding has an EXACT SHAPE (ap.md S11 (g)): by I3 only an identity keep edge carries an exclusion, so a micro edge with a `*` premise and a `$` or `[any]` target has the Empty premise exclusion, and by (a) every binding is `* → *` (`Reverse.bindRev_of_star`). Only the exactness of the reversed records (ap.md §8.7 R3) needs (g). Lean: `Reverse.BindTargetsStar`, `Backward.StmtsMarkRev`, `Backward.NoZeroBack`, `Backward.ZeroKept`, `Backward.ExitReach`, `Reverse.RevStmts`, `RevCalls`. |
| I12 | STATIC CONSTRUCTION RULES (ap.md S12, items (a) to (g); ap.md §4.10). A STATIC POSITION is the premise path of a statement micro edge on `S`, or the path of a sink pattern on `S`, cut to at most two accessors (`[<C>, f]`, `[<C>]`, Go `[<G>]`). The interpreter makes these true. (a) A statement micro edge from `S` to `S` is an identity restriction `S.q.* →_E S.q.*` (the keep edges of a static read, of a static write and of the kill of `RemoveAllMarks`, §1.4, §2.2, §2.3) or a field-to-field edge (a pass rule between static fields, §1.4). (b) A statement micro edge from another base into `S` whose target path lies strictly above a static position has a `$` target and a `$` premise with a concrete mark: a mark on a class position, `zero.$ (zeroMark) → S.<C>.$ (T)` or `Q.$ (T') → S.<C>.$ (T)` (§1.4). A conjunctive micro edge into such a target has a `$` premise with a concrete mark for each literal (§1.4, §5.3); its result is a concrete `$` fact of this shape. This clause is argued (ap.md §11.2): `Statics.SWF` (part `write`) constrains the plain micro edges only, and the static model has no conjunction. So no rule makes a `*`, an `[any]` or an `[any-taint]` fact on a bare class position, and no pass rule reads or writes a bare class position. (c) A call binds `S` only by `S.* → S.*` (§3.1). (d) F78 selects JVM run-1 field limit exactly 1, with bounded class-leading inputs; later limits are positive and nondecreasing. The class accessor is not counted, so a cut never stops strictly above a static position. The historical generic/Go construction only assumed a positive first limit; it does not prove the new closed JVM initial inventory. (e) A cleaner on `S` names its mark (§1.4); `RemoveAllMarks` on a position of `S` is not a cleaner. One exception: the WHOLE-BASE CLEANER `(S, atAndBelow, all)` with the empty path (the generated Spring cleanup form, §1.4, §5.2). Every fact on `S` lies inside it, so it drops the fact and never cleans a fact in part; it has the effect of the kill of a strong write of the whole base `S` (no keep edge, §2.1). (f) `S` is not the zero base. (g) The JVM run-1 abstraction follows F78: normal abstract initials are non-static base roots or static class/field slots; the special S root is a rule-free summary seed. Concrete answers are additive; exact class points are direct. Caller-read structural ownership and the untouched frame govern root use. There is no emitter input history or deferred-answer memo (§0.3). A rule position can be deeper than a static field: below the static field the ordinary rules apply. Lean: `Statics.SWF` (its parts `ss`, `write`, `toC`, `fromC`, `cut`, `clean`, `base`, `alpha`), `Statics.PosIn`. |
| I13 | VALIDITY (ap.md S13, §4.8). The placement of the type filters (§5.1) makes a validity predicate on locations exist with three properties: every type filter accepts every valid location of its base; the validity goes back along every statement micro edge and along every call binding, into the callee and back (a valid end location comes only from a valid start location); and it goes back from the target of a conjunctive micro edge to each literal (§5.3). A valid location is a location that every type filter accepts (ap.md §4.8). The exactness and confirmation theorems of ap.md hold only for valid end locations. Lean: `Exact.FiltValid`, `Exact.BackOK`, `NDExact.ConjOK`. |
| I14 | THE TAINT ANNOTATION (ap.md S15, W8). The `[any]` target of a SOURCE is a MUST: every location at or below its position gets the mark. The `[any]` target of a pass rule is a MAY: the rule does not know the field (§4.1, D18). The interpreter keeps the two apart in the forward forms: a source rule whose target is `AssignMarkOnAnyAccessor` on `P` (Go `AnyAccessor`) or an `AssignMark` on `PositionWithAccess(P, AnyField)` gives the target tail `[any-taint]`: `zero.$ (zeroMark) → P.[any-taint] (T)` (unconditional), `Q.t' (T') → P.[any-taint] (T)` (conditional, `t'` of §4.2), or a conjunctive micro edge with the target `P.[any-taint] (T)` (§5.3). This holds for every source kind: at a call, at the method start, at an exit and at a read (§4.1, §4.3, §4.4, §4.7). An end-fact action applies as the target of a source (§4.1, END FACTS): an `AssignMark` gives `$`, and an `AssignMark` on `PositionWithAccess(P, AnyField)` gives `[any-taint]`, as for a source. Its target mark is concrete (the rule names it), and its premise mark is concrete (`zeroMark`, or the literal mark `T'`; I6). Its result has the Empty exclusion: only the AP rules give an exclusion to an `[any-taint]` fact (ap.md W8, §4.1). No other micro edge has the target tail `[any-taint]`: a pass rule with an `AnyField` target keeps `[any]`, and no binding has an `[any]` or an `[any-taint]` target (I11 (a)). No micro edge has the premise tail `[any-taint]`: a `ContainsMarkOnAnyField` literal gives the premise `Q.[any] (T')` (§4.2), and a pass rule with an `AnyField` premise is a rule error (§1.3, D33). The BACKWARD run has no `[any-taint]` (ap.md W8): the seed of an `[any]` sink pattern and the reversal of an `[any]` literal give the tail `[any]` (§4.9). The forward target tail tells the reversal the rule kind, so the forms need no other flag: the reversal of an edge whose forward target is `[any]` (a pass rule with an `AnyField` target, a may) gives every result in the demand layer, also a `$` result; the reversal of a source edge (the forward target `[any-taint]`, a must) follows the ordinary rows of ap.md §4.1 (§4.9). Lean: `AnyTaint.TaintEdges` (the source edges with an `[any]` target, a parameter of the runs), `AnyTaint.TaintConc` (a hypothesis of the kinds invariant of run 1 `AnyTaintExKinds.D6X_any_conc` (round 1: `AnyTaintSim.D6T_any_conc`; necessary there: `AnyTaintSim.CexKinds.cex_taintConc`)), `AnyTaint.w6t` and its form with exclusions `AnyTaintEx.w6tX` (W6 for every other `[any]` target), the vectors `AnyTaint.Sanity` and `AnyTaintEx.Vec.source_any_target`; the theorems are in ap.md §10.11. |

### 0.1 Known gaps against ap.md S1, S2 and S5

ap.md S1 asks that the micro edges of a statement give exactly its real flows (S2 the same for the aliases, S5 for the
type filters). The interpreter does not do this at the points below (as today, except where a row names a deviation of
§6). The soundness theorems of ap.md do not cover a flow that a soundness gap loses. A precision gap adds a flow that
does not exist in the real program. The analysis keeps this flow in the normal layer, so the exactness theorems hold
only relative to the gap. ap.md §11.1 lists the expected false-positive sources (among them G2 for a caller-set state,
G7, G8, G9 for the catch local, G11, the default identity of an unresolved callee (§3.7), and the type filter on a `*`
fact). A WEAK UPDATE (G7, G8, the default identity) keeps the old content of a forward `[any-taint]` object whole, so
the object keeps its must claim on the overwritten location: such a false positive is a CONFIRMED entry, as for a `$`
fact (before F69 it was a DEMAND entry; D32).

| # | Gap | Where | Effect |
|---|---|---|---|
| G1 | No exception flow crosses a call, and a catch block does not read `exc`. Out of scope (ap.md §11.2). | §3.4 | a thrown tainted value is lost |
| G2 | THE GLOBAL-STATE RULE: at the normal exit, for an `S` item whose premise is the zero fact (a state that the method or its callees set), the part on which a mark literal of an exit sink (plain or conjunctive) holds is dropped from the summary edge. A conjunctive exit sink keeps that part as the stored input of the literal (§5.3). A caller-set `S` fact is evaluated (it can report, D21) but not dropped: it returns to the caller through the callee summary, also the run-1 FLOW summary and its record (§3.3). Also the entry marks on zero-premise `this`/`arg` facts are removed (every leaf with an entry mark, at any depth; D35). | §4.7 steps 3, 4 | premise-dependent removals, not an AP primitive; precision: a caller-set state passes the exit sink of a callee, so a later sink in the caller sees it, and the exit sink of the caller evaluates it again (expected false-positive sources, ap.md §11.1) |
| G3 | The cleaners run before the callee body. | §4.5 step 5.1 | a sink inside the body of the cleaner method sees the cleaned fact |
| G4 | The rules come from the method that the call names, not from the resolved override. | §3.6 | a sink or source on an override is missed |
| G6 | The mark policy and the policy cases of the type filter. | §5.1 | they can drop a real flow (outside ap.md S5) |
| G7 | The write at an alias is weak (A3, AC2). | §2.5, §3.8 | precision: the old content of the alias path survives (sound if an alias does not hold); an expected false-positive source (ap.md §11.1) |
| G8 | A constructor call keeps the caller facts that it overwrites. | §3.5 | precision: a weak update of the receiver and the arguments; an expected false-positive source (ap.md §11.1) |
| G9 | A catch handler reads the facts AFTER each statement of the try range: a statement that throws counts as completed (its kill, its writes and the cleaners of a call apply). The catch statement does not kill its local. As today. | §2.2 (catch), §3.4 | a flow on the state before the throwing statement is lost (`try { s = clean(s); } catch (E e) { sink(s); }`); precision: an old fact on the catch local stays |
| G10 | No class initializer (`<clinit>`) is analysed: it has no call site, and it is not a root. As today. | §3.3 | a value that a static initializer writes to a static field is lost (for example a source in `static final String K = System.getenv("K")`) |
| G11 | A pass rule with a mark literal in its condition applies without its mark literals (D24). | §4.2 | precision: the pass edge also fires where the literal is false |
| G12 | A call with an empty and a non-empty resolution result enters only the non-empty callee: the resolver drops the empty target (I8; `ap-history.md` F67 (13)). As today on the JVM (D28). | §3.6 | a flow through the empty target is lost (for example the identity of a native override) |
| G13 | No static state crosses roots: the REACHED locations are per root, and every root starts with the zero fact only (ap.md §6.1). As today. D27 clears the statics between the Spring controllers on purpose. | §3.3 | a flow from one entry point to another through a static field is lost |

THE ALIAS ANALYSIS is outside the scope of this specification. The analysis uses it as it is and does not change it.
Its settings (the interprocedural depth 0 of A1, the analysis on or off, its time limit) are as configured, and A6
holds relative to them. Under A1 an alias on the static base does not hold after a call, so AC1 gives no alias base on
`S`, and the alias edges of a call keep I12 (c).

---

### 0.2 Logical identity, managed forms and compact containers (O1/O2/O3)

The interpreter is unchanged across runs in its logical keys and effects, not in Java object addresses. Frozen
provider order supplies occurrence ordinals before validation or filtering. Configuration/body replacement creates
a new `FormOwner`. `FormKey`, `RuleOccurrenceId`, `ActionId`, `ProofKey` and `TemporaryKey` preserve generated
identity across independent rebuilds. Persistent source hits retain the complete forward-edge value, not a compiled
form owner. Historical object-identity reasoning is not a cache-lifetime requirement.

Method owners keep recipes and lazy slots. A used slot has one managed soft forward/reverse payload, registered in
`bidi-method-forms` on the shared `RefManager`. Build forward once per surviving payload; reverse lazily from that
same forward value. Publish no failed forward build. A failed reversal preserves forward. Reject recursive
construction of one slot. Disabled registration uses a transient payload without a strong fallback.

`MethodForms.withStatement/withCall/withEntryRules/withExitRules` and the corresponding `DirectedForms` scopes pin
the payload for the whole application. A reachability fence in `finally` also covers failure and cancellation.
Suspended calls retain keys/support metadata and reacquire a plan on resume. Manager cleanup never locks a form
slot; registration and disable share a lifecycle lock, cleanup swaps the registry and clears outside the lock.
ReferenceQueue maintenance is bounded on registration/enable; explicit cleanup/diagnostics can drain fully.

Public form constructors own one copy in read-only empty/singleton/array containers. Trusted factories adopt only
newly owned storage. Keep original order, duplicate policy and filter composition. Base/point indexes use compact
ordinal views over a shared owned vector. These changes do not alter AP exclusion tables or call/field-cleaner cuts.
The implementation and equivalence gates for O1/O2/O3 remain pending until their authoritative tests pass; no
historical proof establishes the whole new predicate/cache integration.

### 0.3 Initial emission and input ownership

F78 [run1-requests.md](run1-requests.md) is normative for the selected JVM core. The emitter consumes input
without retaining a callee AddedFactStore or deferred-answer memo. Caller N2 retains full actual input headers,
caller premises, contexts, call statements and independent layers for return/ND application and late requests.

Normal abstract initials have only StaticSlot(C,f) and BaseSlot(b) forms. Keep the special S-root summary seed,
but evaluate no rule or mark-request callback on its root component. Do not drop ordinary field branches in a
packed tree, skip code transfers, or confuse an actual source fact's premise with an enclosing abstract analysis.
Validate K6 before early request guards. Concrete inputs still answer requests under zero/concrete caller origins.
Exact class-point input has a direct concrete initial; broad possible class components remain MAY/DEMAND.

F78 §1.1 is the complete selected JVM run-1 initial inventory, at field limit exactly 1:
canonical Zero; abstract non-static base/root, special S/root and S/[C,f]; concrete non-static base/root
EXACT/STAR, S/[C,f] EXACT/STAR, and direct S/[C] EXACT/STAR: ten families in all. E is Empty in all of them. There is
no concrete empty S-root initial, abstract class/deeper initial, ANY/ANY_TAINT initial or cleaner temporary.
T includes every frontend-admitted concrete mark, including numeric 0 on a nonzero base. Source/end-GEN outputs
and seed/record work are not arbitrary extra constructors. The abstraction/answer adapters and startFact mapping
must prove this inventory, not rely on permissive generic InitialAp factories.
Each selected supplied abstract field initial is always emitted independently of
concrete requests. Selection still uses the closed plan and actual relevant wide
activation; it does not add irrelevant incoming fields or change the frame partition.
For a C.f ANY/must supplier, emit concrete C.f.*(T) only for actual matching T
requested in effective R; a nonexact direct class component also uses STAR.
Exact concrete suppliers retain EXACT. Incoming/starting facts may be ANY even
though run-1 InitialAp never is. The measured static answer constructor still emits
ANY and violates this proposed boundary. No runtime change is made here.
Prove no-ANY admission and whole-premise summary/native/reversal compatibility;
the equal DEMAND/ANY starts of STAR(T) and old ANY(T) are insufficient by themselves.

Prove the incoming bound before emission: ordinary statement/boundary/call cuts, F74 per-read/write cuts,
non-growing root bindings and class-leading static accessor layout give []/[C]/[C,f] on S. There is no new
emitter/projector cut. The measured generic driver allows larger first limits and its structural cursor can
construct an abstract class initial; those are not the selected F78 construction. Alternate/native/direct-input
adapters must validate the selected domain. Concrete empty S-root EXACT input is impossible by the explicit
frontend condition; broad root/class ANY inputs remain legal suppliers and project conservatively.

After the complete prescan, inspect every effective entry/statement/call/exit/native/lowered form and copy the
existing resolved graph. Collect structural source AND target/read/write positions, full source/sink positive NNF
leaves, microedge input marks, selected cleaners/rewriters and generic excluded-mark targets. Compute finite union
closure including recursion. Keep full conditions/actions in forms, but project requests onto normal slots and
merge former roles. Freeze already resolved normal-static slot/mark rows as immutable B. The plan and effective R
read the same value, shared across equal method/SCC plans without per-context row copies. Bind it before any
subscription/input. Unresolved wide scopes activate actual/later relevant fields lazily; those declarations alone
are not R membership.
There is no runtime Position event or normal abstract class slot.

SummaryStorage owns one effective cumulative RequestStorage R = B union D; D stores only dynamic residual marks
disjoint from B. The actor writes empty-base pending P of the SAME class. Every producer filters effective R
before P union, never querying P. No B row is copied into P/D, and no initial recursive notification wave is sent.
The abstraction and new-FLOW input handling read full R. At method fact-queue-empty commit P, queue immutable fresh notifications, then replace P with a new
RequestStorage before handling another event. Only actual normal abstract origins forward requests. Request joins
ignore field E and FLOW mark exclusions; ordinary fact operations do not. Every exclusion creator collects its
same-origin mark. R persists after answers. Registration/input admission before LinkIn and commit before notify
cover both arrival orders using retained caller N2. No additional fresh-input TAINT request replay is needed.
Binding B before those input paths makes the initial wave unnecessary; later wide activation still uses P and
fresh notification. A changed C.g -> C.f or argument -> static origin is not covered merely by B's C.f row.
When the actual original static FLOW slot equals the queried slot, resolved-call closure gives callee B(slot) subset caller B(slot); skip the
whole shared row before per-mark callbacks/transport. Dynamic D is filtered against target R normally, and B
still routes when the actual original slot changed. This is immutable-base provenance, not a request role.

Static summary reads transiently partition input by the semantic footprint: owned field analyses, direct class
components and a constructively proved untouched identity frame. Raw S-root identity or coarse class-exclusion
outputs cannot act as unrestricted fallbacks. Preserve source/sink NNF, F74/F75/D23, cleaner order and positive
field cuts. After run 1 the fixed-demand emission and ordinary exclusion rules remain; there are no requests.

## 1. Bases and positions

### 1.1 Bases

| Base | JVM | Go |
|---|---|---|
| `this` | `JIRThis` | the receiver (parameter 0 of a method); at a dynamic call, the function value |
| `arg(i)` | `JIRArgument(i)` | parameter `i`, counted after the receiver |
| `local(i)` | `JIRLocalVar(i)` | register `i` |
| `const(t, v)` | `JIRConstant` (also `null`) | `GoIRConstValue` |
| `ret` | the return value | the return value; several results: `ret.#i` |
| `exc` | the thrown exception | none |
| `S` | `ClassStatic` | `ClassStatic` (the globals) |
| `zero` | the zero fact | the zero fact |

### 1.2 Accessors

| Accessor | JVM | Go | Counted (field limit) |
|---|---|---|---|
| field `f` | `FieldAccessor(declaring class, name, type)` | `FieldAccessor(struct full name, name, "?")` | yes |
| `[e]` | array element | slice, array, map and channel element | yes |
| `<C>` | `ClassStaticAccessor(C)` | `ClassStaticAccessor(global full name)`, written `<G>` | no |
| `#i` | none | `FieldAccessor("tuple", "$i")` | yes |
| `fv_i` | none | `FieldAccessor(fn, "freeVar$i")`: free variable `i` of closure `fn` | yes |
| `<string-bytes>` | the content of a `String` (a virtual field) | none | yes |
| `[value]`, type info | not an accessor of the new analysis (ap.md W5): `ValueAccessor` is made by no statement and no rule; the type-info accessors serve only the lambda analysis of the prescan (ap.md §1) | the same | — |

### 1.3 Rule positions

A rule names positions in CALLEE coordinates. At a method entry and at a method exit, the positions are in the
coordinates of the method itself.

| Position | Base and path | Note |
|---|---|---|
| `Argument(i)` | `arg(i)` | |
| `This` | `this` | Go: also the target of a dynamic call |
| `Result` | `ret` | at a call: the lhs after the binding back |
| `ClassStatic(C)` (JVM) | `S.<C>` | a CLASS POSITION: the rules on it follow §1.4 |
| `PositionWithAccess(P, Field f)` | the path of `P` and `f` | |
| `PositionWithAccess(P, Element)` | the path of `P` and `[e]` | |
| `PositionWithAccess(P, AnyField)` | the path of `P` | sink and condition: the `[any]` pattern (also in the backward run, I14); source target: the `[any-taint]` tail (a must, forward only, I14); pass-rule target: the `[any]` tail (a may, §4.1); pass-rule source (the premise side): a rule error (below, D33); cleaner: the reach (§5.2); an action position in the summary rewriter: `atAndBelow` for a source (§5.2, D34), `below` (the row of the cleaner, as today) for a cleaner (§5.2). Go: `AnyAccessor`, the same. `AssignMarkOnAnyAccessor` on `P` is the same source target. |

Two invariants hold for every rule position:

1. it has at most one `AnyField` accessor (Go: `AnyAccessor`);
2. if it has an `AnyField` accessor, this accessor is the LAST accessor: no concrete accessor comes after it.

A rule with a position that breaks an invariant (for example `Argument(0).AnyField.f` or
`Argument(0).AnyField.AnyField`) is a RULE ERROR (D29). A rule error REJECTS THE WHOLE RULE: the interpreter logs it
once and gives the rule no form at any place (no sink, source, end fact, pass rule, cleaner, entry or exit rule), and
the summary rewriter does not select it (§5.2). The check reads every position of the rule before the static
evaluation: every action and every mark literal of the condition, a negated one too. The builder of the forms never
fails on such a rule. A PASS RULE WITH AN `AnyField` PREMISE (`CopyAllMarks(P.AnyField → Q)`,
`CopyMark(T, P.AnyField → Q)`, also with an `AnyField` target; Go `CopyData`, `CopyTaintMark` with `AnyAccessor` on
the from position) is a rule error too, with the same effect (D33). The rule reads one field that it does not know, so its result is a may,
but a `$` result of an `[any]` premise keeps its layer (ap.md §4.1), and W6 cannot keep it out of the normal layer.
Today's rule base has no such rule: the only `AnyField` in a pass rule is a target (Go `json.Unmarshal`,
`arg(0) → arg(1).*`). One rule error does not reject: a pass rule with a mark literal is logged once and applies
without its mark literals (§4.2, D24).

### 1.4 Rules on a static position (JVM)

A static access in the IR always names a field: the path `[<C>, s]` (§2.2). A CLASS POSITION `S.<C>` holds marks only:
a rule can put a concrete mark on it (for example the state marks that the rule generator puts on a synthetic class),
but not a tree of fields. A rule can also name a static field `S.<C>.s`. Go rules have no static position. The
interpreter maps a rule element on a static position as follows, so that the construction rules of I12 hold.

| Rule element on a static position | Interpreter form | Reason |
|---|---|---|
| a source target (`[any-taint]`, I14) or a pass-rule target (`[any]`) on a class position: `AssignMarkOnAnyAccessor` on `ClassStatic(C)`, or the target `PositionWithAccess(ClassStatic(C), AnyField)` | rule error: the interpreter rejects the rule | a static access always names a field; an `[any]` or an `[any-taint]` fact on a bare class lies above every static field of `C` (I12 (b); Lean `Statics.CexAny`) |
| `AssignMark(T)` on `ClassStatic(C)`, with no condition | `zero.$ (zeroMark) → S.<C>.$ (T)` | a concrete mark on a class position (§4.1) |
| a conditional rule whose target is a class position (`AssignMark(T)` on `ClassStatic(C)` with a mark condition) | for each alternative of the condition (§4.2): the premise `Q.$ (T')` for each positive literal `ContainsMark(Q, T')`. An alternative with one positive literal gives the edge `Q.$ (T') → S.<C>.$ (T)` (§4.1); an alternative with two or more positive literals gives a conjunctive micro edge with the target `S.<C>.$ (T)` (an ND edge, §5.3), at a call (§4.1) and at an exit (§4.7 step 1, D31). A `ContainsMarkOnAnyField` literal in such a rule is a rule error: the interpreter rejects the rule | a write above a static position needs a `$` premise with a concrete mark (I12 (b); Lean `Statics.SWF`, part `write`); `ContainsMarkOnAnyField` gives an `[any]` premise |
| `CopyAllMarks` or `CopyMark` FROM or TO a class position `ClassStatic(C)` (no field) | rule error: the interpreter rejects the rule | a pass rule is a handcrafted summary, and no data flows from or to a bare class |
| `CopyAllMarks` or `CopyMark` from a static field to a static field (or between a static field and another base) | the rows of §4.1: `b.* → b.*` (`b` = the base of `P`), `P.* → Q.*` (or `P.$ (T) → Q.$ (T)`) | a field-to-field edge (I12 (a)) |
| `RemoveAllMarks(P)`, `P` on `S` at any depth: a class `ClassStatic(C)`, a static field `S.<C>.f`, or deeper (reach `atAndBelow`) | the KILL of a strong write with no value at `P`: a statement summary that touches `S`, with the keep edges `strongKeep(S, path of P)` of §2.1 and no other micro edge. For a class: `S.* →_{<C>} S.*`. For a static field: `S.* →_{<C>} S.*` and `S.<C>.* →_{f} S.<C>.*` (the row of `C.s = x` in §2.2 without the value edges). Deeper: one identity keep edge per proper prefix of the path, each with the next accessor as its exclusion. In run 1 the shared static profile supplies the proper structural initials for such keep edges; a broad identity static edge gives no result for a deeper premise (§2.1 step 4). At a call the kill applies at the place of the cleaner, in the rule order (§4.5 step 5.1). The backward run applies the same keep edges (their reversal is the same edge) at the place of the reversed cleaners (§4.9 step 6) | a strong update removes every mark at and below `P`; the kill is not a cleaner on `S`, and no mark is enumerated. It acts only if its rule is unconditional (§4.2), as every cleaner rule; a conditional `RemoveAllMarks` on `S` does not act |
| `RemoveAllMarks(P.AnyField)`, `P` on `S`, also a class (reach `below`) | rule error: the interpreter rejects the rule | the kill of a write also removes `P` itself, and a cleaner of every mark on `S` is not allowed (I12 (e)) |
| `RemoveMark(T, P, reach)`, `P` on `S` | the cleaner `(P, reach, T)` of §5.2 | a cleaner on `S` names its mark (I12 (e)) |
| a sink or a conjunction literal on a static position (`ContainsMark`, `ContainsMarkOnAnyField`) | the rows of §4.1, §4.2 and §5.3: the sink check of ap.md §4.9, or the literal of ap.md §4.6 (a condition of a rule with a class target: the row above) | F78 slot/mark collection on the actual normal abstract origin; exact class-point inputs are direct, and the special S-root has no rule evaluation (ap.md §4.5, §4.10) |
| the one literal of a conditional source on a static position (at a call, §4.1; at an exit, §4.7) | the premise `Q.t' (T')` of a statement micro edge (§4.1, §4.2) | in run 1 the static exception of §2.1 step 4 gives no result below a broad identity static edge; the joined field plan and admitted slot marks supply the analysis work (§0.3), without a full query or Position request |
| every other rule element on a static position | the rows of §4.1, §4.2 and §5.2, with the base and the path of §1.3 | |

A rule position can be deeper than a static field (for example `ClassStatic(C)` with `Field s` and `Field g`): below the
static field the ordinary rules apply, as for an instance field (ap.md §4.10).

The generated Spring `__cleanup__()` method supplies the primitive cleaner `(S, [], atAndBelow, all)` directly
to its call form (D27, §5.2). Every fact on `S` lies inside it, so it drops the whole fact and raises no request.
The dispatcher saves the registry fields before the call and restores them after it. This uses the existing
static base and cleaner; it adds no configuration rule position.

---

## 2. Non-call statements

### 2.1 The statement step

A call statement never uses this step. `x = m(...)` is a call (§3). The zero fact uses the order of §4.4. For a fact
`c` that is not the zero fact, on the base `b` at the statement `s`:

1. NO LIVENESS STEP. The step keeps a fact on a local that is dead at `s`, in every run and in both languages (D25).
   The alias analysis is not changed: it keeps its own inputs (§2.5 A1).
2. If `s` does not touch `b`: `c` passes unchanged (`Unchanged`).
3. Apply the operand type filters of `b` (§5.1). A rejected fact is dropped, also on `b` itself.
4. Apply every micro edge of `b` to `c` with `concat` (I2, ap.md §4.2). The result is the union. A touched base keeps
   only what an edge regenerates: that is the kill. F78 STATIC CODE ADAPTER: on the special identity S-root
   component a deeper static read gives no wide DEMAND/Position result. Normal field abstractions supply the
   read/write work; there is no normal abstract class slot. Ordinary field branches use the AP tables. Code keep
   exclusions still apply, but caller readers derive the untouched identity frame rather than reusing a coarse
   root class exclusion. Rule statements use the common root guard of §4; they do not evaluate root conditions or
   actions. Generic excluded-mark targets collect their original-slot interests (§5.4). No Position event exists.
5. Apply the lhs type filter (§5.1) to the results on the lhs. (On the input it has no effect: the statement kills the
   lhs.)
6. Apply the field limit (ap.md §4.4).

The write rule (I3). The reference form uses the types of ap.md §3.4 and the edge `PathEdge` of ap.md §4.1.
`ExclusionSet.of(a)` makes the Concrete exclusion `{a}`.

```kotlin
/** The micro edge `b.p.* →_E b.p.*`, with the mark `*` on both sides. */
fun keepEdge(b: AccessPathBase, p: List<Accessor>, exclusion: ExclusionSet): PathEdge {
    val side = PathFact(b, p, Tail.STAR, MarkSlot.Star(MarkSet.EMPTY))
    return PathEdge(side, side, exclusion)
}

/** A STRONG write of the path a1..an on the base b: keep every prefix of b except the written accessor. */
fun strongKeep(b: AccessPathBase, path: List<Accessor>): List<PathEdge> =
    path.indices.map { k -> keepEdge(b, path.take(k), ExclusionSet.of(path[k])) }

/** A WEAK write keeps the whole base. A read keeps the whole base and adds no exclusion. */
fun weakKeep(b: AccessPathBase): List<PathEdge> = listOf(keepEdge(b, emptyList(), ExclusionSet.Empty))
```

A strong write with an empty path (Go `*p = v`) gives no identity edge: the base is replaced.

### 2.2 JVM

| Statement | Micro edges | Touched | Type filters (§5.1) |
|---|---|---|---|
| `x = y` | `y.* → y.*`, `y.* → x.*` | {x, y} | y: static type of y; x: static type of x |
| `x = (T) y` | as `x = y` | {x, y} | y: `T` (narrows y); x |
| `x = c` (a constant or `null`) | `c.* → c.*`, `c.* → x.*` | {x, c} | c; x |
| `x = y.f`, x ≠ y | `y.* → y.*`, `y.f.* → x.*` | {x, y} | y: static type of y AND declaring class of f; x |
| `x = x.f` | `x.f.* → x.*` | {x} | x, operand (on the input): static type of x AND declaring class of f; x, lhs (on the results): static type of x (I1) |
| `x = y[i]` | `y.* → y.*`, `y.[e].* → x.*` | {x, y} | y: the array type; x |
| `x = C.s` | `S.* → S.*`, `S.<C>.s.* → x.*`. Run 1: on an identity static `*` edge at the root `[]` or at the class `[<C>]`, the read edge gives no result; the profile supplies structural slot `[<C>, s]` (§2.1 step 4) | {S, x} | x |
| `y.f = x`, y ≠ x | `y.* →_{f} y.*`, `x.* → x.*`, `x.* → y.f.*` | {y, x} | y: static type of y AND declaring class of f; x |
| `y.f = y` | `y.* →_{f} y.*`, `y.* → y.f.*` | {y} | y |
| `y[i] = x` (weak) | `y.* → y.*`, `x.* → x.*`, `x.* → y.[e].*` | {y, x} | y: the array type; x |
| `C.s = x` | `S.* →_{<C>} S.*`, `S.<C>.* →_{s} S.<C>.*`, `x.* → x.*`, `x.* → S.<C>.s.*`. Run 1: on an identity static `*` edge at the root `[]`, the class keep edge gives no result; the profile supplies structural slot `[<C>]` (§2.1 step 4) | {S, x} | x |
| `x = a op b` | `a.* → a.*`, `a.* → x.*`, `b.* → b.*`, `b.* → x.*` | {x, a, b} | a; b; x |
| `x = new T`, `new T[n]`, `instanceof`, `-a`, `a.length`, phi | none (kill) | {x} | x |
| `return v` | `v.* → v.*`, `v.* → ret.*` | {ret, v} | none |
| `return` (void) | none (kill) | {ret} | none |
| `throw t` | `t.* → t.*`, `t.* → exc.*` | {exc, t} | none |
| any other statement (branch, goto, monitor, catch) | none | {} (all facts pass) | none |

If one base is on both sides (`x = x op b`), the two edges of `x` become the identity `x.* → x.*`. The alias edges of a
write are in §2.5. A read source at `x = C.s` adds the zero base to the touched bases (§4.4).

### 2.3 Go

Go has no type filters (as today, `FactTypeChecker.Dummy`). The pointer model of today stays: `*y` and `&y.f` do not
add an accessor, so a pointer and its target are one base.

| Statement | Micro edges | Touched |
|---|---|---|
| `x = y` for ChangeType, Convert, MultiConvert, ChangeInterface, MakeInterface, TypeAssert, SliceToArrayPointer, `*y`, Slice, Range | `y.* → y.*`, `y.* → x.*` | {x, y} |
| `x = y.f`, `x = &y.f` | `y.* → y.*`, `y.f.* → x.*` | {x, y} |
| `x = y[i]`, `x = &y[i]`, `x = m[k]`, `x = <-ch` | `y.* → y.*`, `y.[e].* → x.*` | {x, y} |
| the comma-ok form of lookup, type assert and receive | as above; the target is `x.#0.*` | {x, y} |
| `x = extract(t, i)` | `t.* → t.*`, `t.#i.* → x.*` | {x, t} |
| `x = G` (a global) | `S.* → S.*`, `S.<G>.* → x.*`. Run 1: on an identity static `*` edge at the root path `[]`, the read edge gives no result; the profile supplies structural slot `[<G>]` (§2.1 step 4) | {S, x} |
| `x = freevar(i)` in closure `fn` | `this.* → this.*`, `this.fv_i.* → x.*` | {x, this} |
| `x = a + b` (string) | `a.* → a.*`, `a.* → x.*`, `b.* → b.*`, `b.* → x.*` | {x, a, b} |
| `x = next(it)` | `it.* → it.*`, `it.[e].* → x.#k.*` for k ∈ {1, 2} (map) or k = 2 (slice, array); no target for other types | {x, it} |
| `x = phi(v1, ..., vn)` | for each `vi`: `vi.* → vi.*`, `vi.* → x.*` | {x, v1..vn} |
| `x = makeClosure(fn, b0..bn)` | for each `i`: `bi.* → bi.*`, `bi.* → x.fv_i.*` | {x, b0..bn} |
| other binary and unary (NOT, NEG, XOR) operations, Alloc, MakeSlice, MakeMap, MakeChan, Select, builtin, function value | none (kill) | {x} |
| `*p = v` | `v.* → v.*`, `v.* → p.*` (strong, empty path: no identity for p) | {p, v} |
| `y.f = v` | `y.* →_{f} y.*`, `v.* → v.*`, `v.* → y.f.*` | {y, v} |
| `y[i] = v` (weak) | `y.* → y.*`, `v.* → v.*`, `v.* → y.[e].*` | {y, v} |
| `m[k] = v` (weak) | `m.* → m.*`, `v.* → v.*`, `v.* → m.[e].*`, `k.* → k.*`, `k.* → m.[e].*` | {m, v, k} |
| `ch <- v` (weak) | `ch.* → ch.*`, `v.* → v.*`, `v.* → ch.[e].*` | {ch, v} |
| `G = v` | `S.* →_{<G>} S.*`, `v.* → v.*`, `v.* → S.<G>.*`. The root keep edge is at the root, so it needs no deeper structural slot | {S, v} |
| `return v` | `v.* → v.*`, `v.* → ret.*` | {ret, v} |
| `return v0, ..., vn` (n ≥ 1) | for each `i`: `vi.* → vi.*`, `vi.* → ret.#i.*` | {ret, v0..vn} |
| any other statement | none | {} |

### 2.4 Pinned examples

The column "Today" uses today's notation (§0).

| Statement | Today (`StatementSummaryBuilder`) | New |
|---|---|---|
| `x = y.f` | `y\{f} → y`, `y.f → y.f`, `y.f → x` | `y.* → y.*`, `y.f.* → x.*` |
| `y.f = x` | `y\{f} → y`, `x → x`, `x → y.f` | `y.* →_{f} y.*`, `x.* → x.*`, `x.* → y.f.*` |
| `y[i] = x` | `y → y`, `x → x`, `x → y.[e]` | the same |
| `a.f = a` | `a\{f} → a`, `a → a.f` | `a.* →_{f} a.*`, `a.* → a.f.*` |
| `x = x.next` | `x\{next} → null` (refine only), `x.next → x` | `x.next.* → x.*` |
| Go `x, ok = m[k]` | `m\{e} → m`, `m.[e] → m.[e]`, `m.[e] → x.#0` | `m.* → m.*`, `m.[e].* → x.#0.*` |

The effect on facts (the cases of ap.md §4.2):

| Statement | Input fact | Today | New |
|---|---|---|---|
| `x = y.f` | `(y, ., *, E, *)`, f ∉ E | the premise is refined by `{f}`; the abstraction emits `y.f.*` | `(y, ., *, E, *)` normal; `(x, ., [any], {}, *)` demand |
| `x = y.f` | `(y, .f, *, E, *)` | `(x, ., *, E, *)` | the same |
| `y.f = x` | `(y, ., *, E, *)` | `(y, ., *, E ∪ {f}, *)` and a refinement | `(y, ., *, E ∪ {f}, *)`, normal, no request |
| `x = y.f` | `(y, ., [any-taint], E, T)`, normal, `f ∉ E` | not pinned: no `[any-taint]` today (D32) | `(y, ., [any-taint], E, T)` and `(x, ., [any-taint], {}, T)`, both normal: every location at or below `y.f` carries `T` (I2; Lean `AnyTaintEx.Vec.read_admitted`) |
| `x = y.f` | `(y, ., [any-taint], E, T)`, normal, `f ∈ E` | not pinned (D32) | `(y, ., [any-taint], E, T)` only: no location of the fact is at or below `y.f`, so the read edge gives nothing (ap.md §4.1; `AnyTaintEx.Vec.read_excluded`) |
| `y.f = x` | `(y, ., [any-taint], E, T)`, normal | not pinned (D32) | `(y, ., [any-taint], E ∪ {f}, T)`, normal: the keep edge `y.* →_{f} y.*` adds `f` to the exclusion (ap.md W8, §4.1; `AnyTaintEx.Vec.setter_keep`; the first F69 text demoted it to `[any]` demand, `setter_keep_base`). The exclusion is necessary: a normal `[any-taint]` result with no exclusion would claim `y.f`, which the write overwrites (`AnyTaintCases.W.keep_normal_confirms_unreal`). The value edge `x.* → y.f.*` gives the facts of `x` below `y.f` |

### 2.5 Aliases on writes

| Rule | Text |
|---|---|
| A1 | At a write to `b.p` with `b` a local, the alias analysis gives the alias paths `(c, q)` of `b` that hold BEFORE the statement (JVM: `DSUAliasAnalysis`, interprocedural depth 0; Go: `GoDSUAliasAnalysis`). Constant alias bases are skipped. |
| A2 | For each alias `(c, q)` with `c ≠ b` and each value `v` of the write: the micro edge `v.* → c.q.p.*`. |
| A3 | The alias base `c` is NOT touched (a gen-only target). It keeps its old content: the write is weak at the alias (gap G7; an expected false-positive source, ap.md §11.1). |
| A4 | A read uses no alias. A static write and a Go global write have no alias. |
| A5 | Backward run: the alias target gets the identity edge `c.* → c.*` (§4.9, ap.md §9.2). |
| A6 | The alias edges are precise and complete (I9, ap.md S2). This is a contract on the alias analysis. The paths are not cut (I10). |

---

## 3. Call statements

### 3.1 Bindings

A call `r = m(o, a0, ..., an)`. The touched caller bases: `S` (§3.3), the receiver `o`, every argument `ai`, the lhs
`r`, and in Go the target of a dynamic call. A fact on another base passes over the call. A fact on a touched base
follows the call order of §4.5:

1. the binding edges into the callee (this section);
2. the sinks, the sources and the cleaners of the call (§4.5 steps 3 to 5.1);
3. the callee processing of ap.md §5.3 (add the fact, emit, subscribe, apply the summaries);
4. the summary rewriter (§5.2), the binding edges back, the aliases (§3.8) and the field limit.

| Binding | JVM edge | Go edge | Type filter (JVM, §5.1) |
|---|---|---|---|
| receiver in | `o.* → this.*` | `o.* → this.*` (the effective receiver) | o: declaring class of the method that the call names |
| dynamic target in | none | `v.* → this.*` (`v` = the function value) | none |
| argument in | `ai.* → arg(i).*` | `ai.* → arg(i).*` | ai: static type of `ai` |
| static in | `S.* → S.*` | `S.* → S.*` | none |
| zero in | `zero.* → zero.*` | `zero.* → zero.*` | none |
| receiver back | `this.* → o.*` | `this.* → o.*` and `this.* → v.*` | o: static type of `o` |
| argument back | `arg(i).* → ai.*`; none if `ai` is a constant | `arg(i).* → ai.*` | ai: static type of `ai` |
| result back | `ret.* → r.*`; none without an lhs | `ret.* → r.*` (several results: `r` is the tuple; `extract` reads `#i`) | r: static type of `r` |
| static back | `S.* → S.*` (no form makes a fact on a constant base, so the binding back has no constant edge) | the same | none |
| exception back | none (dropped) | none | none |
| local back | none: a `local` fact is not a valid exit fact | none | none |

Every binding edge has the empty `from` path, the `*` tail and the mark `*` on both sides (I4, I11), so a caller fact
is always strong enough for it. The AP applies it as a micro edge (I4). A local passed twice (`m(a, a)`) gets two
bindings. No binding back has the zero base (I11).

### 3.2 The lhs

The lhs `r` has no binding into the callee. So a fact on `r` before the call is killed (a strong update of `r`). If `r`
is also an argument (`r = m(r)`), its argument binding carries the fact. The lhs gets only what the binding back of
`ret` gives, and the source rules at the call (§4.5).

### 3.3 Statics and the zero fact

* `S.* → S.*` in both directions, with no filter. `S` is touched at every call, in every run, as in the model call
  (ap.md §3.5): the static fact comes back only through the callee summaries. A callee that touches no static gives
  its identity summary. In run 1 it is a normal summary edge, so it is a record in every later run (ap.md §8.7), and
  it gives the static fact back with no special rule at the call.
* The zero fact. No call binding touches the zero base, so the zero fact passes over every call. It is also bound into
  every resolved callee by `zero.* → zero.*` (the zero fact `(zero, [], $, {}, zeroMark)` is below this premise), as
  today. A binding is a `*`-to-`*` edge (I4), so the zero binding is `zero.* → zero.*`. The form
  `zero.$ (zeroMark) → zero.$ (zeroMark)` is not a binding: it is the keep edge of a statement that touches the zero
  base (I11). There is no binding back for the zero fact (I11): the fact that passed over is the same (ap.md §3.5). An
  unresolved call only passes the zero fact over. The sink and source rules of the call read the zero fact through
  the rule statement of the call (§4.1, §4.6).
* The roots. No class initializer (`<clinit>`) is analysed: it has no call site, and it is not a root (gap G10). Every
  root starts with the zero fact only (ap.md §6.1), so no static state crosses roots (gap G13).

### 3.4 Exceptions

`throw t` moves `t` into `exc` (§2.2). The exceptional exit makes no summary edge (`producesExceptionalControlFlow`).
The exit rules apply at the exceptional exit too (§4.7): there the rule position `Result` reads `exc`.
The binding back has no edge for `exc`. So no exception flow crosses a call, and a catch block does not read `exc`. This
is today's behaviour (the exception tests of `DataFlowBenchFalseNegativeTest` are disabled). It is outside the model
(ap.md S4, §11.2; gap G1). Go has no exception base.

### 3.5 Constructors (JVM)

At a call to `<init>`, every added fact (the bound fact after the cleaners, `S` included) also PASSES OVER the call:
it keeps its caller base, and it also enters the callee. This is the ad-hoc rule of today (`isConstructor` in
`JIRMethodCallFlowFunction`). So a constructor call is a weak update of its receiver and its arguments. It is an
expected false-positive source (gap G8, ap.md §11.1). The pass-over result does not take the summary rewriter; it takes
the binding back and the field limit (§4.5 step 6). In the NORMAL layer it is an IDENTITY result and takes no alias.
A DEMAND-layer result takes aliases (AC4).

### 3.6 Virtual calls and several callees

* JVM: the call resolver gives a set of callees, each with a context (`JIRInstanceTypeMethodContext`: the receiver type;
  `JIRArgumentTypeMethodContext`: the type of one argument), and possibly a resolution failure. Go: the resolved
  callees with `EmptyMethodContext`; an empty set is a failure. The resolver reads the context of the caller's method
  key (the type constraints of `this` and of an argument), so the callees of a call depend on that context: the call
  plan is per method key (analyzer-core.md §4.8).
* The resolver drops every empty method (I8) from the callees. If every resolution result is an empty method, the call
  is unresolved (§3.7).
* The relevance check, the sinks, the sources and the cleaners run ONCE per caller fact. They use the rules of the
  method that the call statement names, not of the resolved callee.
* The cleaned bound fact enters EVERY resolved callee. The start fact of each callee is filtered by the context type
  (§5.1).
* The caller gets the union of the summary results of all callees. A failure adds the unresolved path (§3.7) to the
  union.

### 3.7 Unresolved callees

A callee is UNRESOLVED if the resolver gives a failure (JVM: the called method, or an override, is outside the analysed
units, `MethodResolutionFailed`; Go: an empty set of callees), or if the call targets a lambda or a closure that is not
yet known (§3.9), or if every resolution result is an empty method (I8: the resolver drops an empty method from the
callees, §3.6). AN EMPTY RESOLUTION RESULT (the resolver gives no callee and no failure) is handled as a resolution
failure: the call is unresolved, as in Go. On the JVM it occurs only for a raw lambda expression
(`JIRCallResolver.kt:98-101`). The required lambda features of the classpath (`LambdaAnonymousClassFeature`,
`LambdaExpressionToAnonymousClassTransformerFeature`; ap-impl.md §31.2) make every lambda value an allocation of a
lambda class, so with them this case does not occur. They must be installed.

An unresolved callee has a STATEMENT summary (ap.md §4.2: the micro edges of a statement). The AP applies it to each
added fact `a` (the bound fact after the cleaners) at the callee base `P`, in every run, as statement micro edges
(§2.1 step 4), and never restricts it. The fact `a` has the premise set and the layer of its
caller edge `(i → c)`: in run 1 the static exception of §2.1 step 4 tests that caller edge, and the caller profile
supplies the structural work (§0.3):

1. Default identity: `P.* → P.*`. The fact passes over the call. The summary rewriter (§5.2) applies to it (D23). It
   is a weak update of every callee base: an expected false-positive source (ap.md §11.1; on a forward `[any-taint]`
   object, §0.1).
2. Pass rules of the method that the call names (§4.1). JVM: with conditions; Go: unconditional. JVM type filters by
   the declared signature (§5.1).
3. JVM DEFAULT GETTER RULES (the analysis option `defaultGetModel`; it is on unless the option `disableDefaultGetModel`
   is set; today `JIRMethodGetDefault`): for an instance method whose name starts with `get` and whose class is
   outside the analysed units (not a class of the project), the unconditional pass rule
   `CopyAllMarks(This → Result)`, and, if the return type can be an array (an array type or `Object`), also
   `CopyAllMarks(This → Result.[e])`. They join the pass rules (union).
4. The summary rewriter on the pass results, the binding back, the aliases (§3.8), the field limit.

The lhs gets only what a pass rule writes to `Result`. The external method tracker records the call (no effect on the
facts).

### 3.8 Aliases on call results

| Rule | Text |
|---|---|
| AC1 | JVM: for each caller local `x` that a binding back writes (`r`, `o`, `ai`), the alias paths `(b, q)` of `x` that hold BOTH before and after the call (`forEachAliasAfterCallStatement`; constant bases skipped). Go: the heap aliases at the statement (`forEachHeapAliasAtStatement`). |
| AC2 | For each alias: the binding-back edge `P.* → b.q.*` beside `P.* → x.*`. The alias base is a gen-only target (weak, gap G7). |
| AC3 | The alias edges apply to: every summary result that is not an identity result (AC4), so every demand-layer result and every zero-premise summary result; the source results at the call; the end facts of a sink (§4.1); the pass-rule results. |
| AC4 | An IDENTITY result is a summary result in the NORMAL layer with a nonzero singleton premise and a conclusion equal to the start fact of that premise (ap.md §6.5). The default identity of an unresolved callee (§3.7) and the constructor pass-over (§3.5) are IDENTITY results too when they are in the NORMAL layer. An identity result is not copied to the aliases (as today): the alias already holds the same facts, because the writes keep the aliases (§2.5). A demand-layer result is never an identity result: it always goes to the aliases, also when it is equal to its start fact (a demand start fact can be coarser than the premise, so the alias does not hold it). An `applicable`-only native must-record result also takes aliases: its pending demotion occurs after these mappings (ap.md §4.3). |
| AC5 | AC3 and AC4 are an exception to I2: they select the results to which the alias edges apply. The selection leaves out only the normal-layer identity results, which the alias base already holds. The backward run applies the reversed alias edges to every requirement (§4.9). |

### 3.9 Lambdas and closures

* JVM: the prescan (ap.md §1) runs the current core (ap.md §7.6) and gives the lambda classes that reach each call to a
  functional interface method. The new analysis reads this result before run 1 and has no lambda event. When the call
  resolver gives a `Lambda` result for a call (`JIRCallResolver.MethodResolutionResult.Lambda`), the result is the
  lambda methods that the prescan knows for that call, and nothing else: it has no unresolved path. If the prescan knows
  no lambda for the call, the result is a resolution failure: the unresolved path (§3.7). Today a `Lambda` result
  always adds a resolution failure beside the lambda methods (`JIRMethodCallResolver.kt:184-209`; deviation D19, §6).
  Only this failure goes. A resolution failure that the resolver gives beside the `Lambda` result for its own reason
  (no known override, or a base method outside the analysed units, `JIRCallResolver.kt:160-163, 174-178`) stays: it
  adds the unresolved path. So D19 changes the result only for a functional interface of the project; a call through
  a JDK functional interface (for example `java.util.function.Function.apply`) keeps the unresolved path.
* Go: a call through a function value is DYNAMIC. The value binds to `this` (§3.1). `makeClosure` writes the free
  variables to `x.fv_i` (§2.3), and the closure body reads `this.fv_i`. If the resolver gives no callee, the call takes
  the unresolved path; Go keeps today's closure tracker (Go is outside analyzer-core.md, its §0).
* Go `go` and `defer`: the interpreter handles them as calls at their statement, with no result.

---

## 4. Rules and their order

F78 COMMON GUARD (JVM run 1). Before entry, statement, call, exit, source/sink, cleaner,
pass or generated-rewriter rule evaluation, isolate the special abstract S-root
component and pass it through this rule boundary without rule effects or requests.
Ordinary field branches retain the complete stages below. Code interpretation and
bindings are not rule evaluation. Collect all effective rule metadata before this
runtime guard. The caller-read field ownership/identity frame prevents an unrestricted
root pass from reintroducing cleaned fields or losing untouched siblings (§0.3).

### 4.1 Rule kinds

| Kind | JVM | Go | Where | AP form |
|---|---|---|---|---|
| source at a call | `TaintMethodSource` | `Source` | call statement (the rule statement, below) | unconditional: `zero.$ (zeroMark) → P.$ (T)` (`AssignMark`) or `zero.$ (zeroMark) → P.[any-taint] (T)` (`AssignMarkOnAnyAccessor`, Go `AnyAccessor`, or `AssignMark` on `PositionWithAccess(P, AnyField)`: a must, I14), premise mark `zeroMark`; conditional: `Q.t' (T') → P.t (T)`, with `t' = $` for `ContainsMark(Q, T')` and `t' = [any]` for `ContainsMarkOnAnyField(Q, T')` (§4.2), and the target tail `t` (`$` or `[any-taint]`) as above; a conjunction over several facts: §5.3 |
| entry-point source | `TaintEntryPointSource` | none | method start, zero | `zero.$ (zeroMark) → P.$ (T)`, or `zero.$ (zeroMark) → P.[any-taint] (T)` for an `AnyField` target (I14; for example the field taint that the Spring rule provider adds to a DTO argument, `SpringRuleProvider.kt:61-76`); the condition must be true |
| exit source | `TaintMethodExitSource` | none | every exit (normal and exceptional, §4.7) | as a source at a call, in the coordinates of the method; a conjunction over several facts is an ND edge at the exit (§4.7 step 1, §5.3; D31), not a rule error |
| read source | `TaintStaticFieldSource` at `x = C.s` | `FieldReadSource` at `x = y.f`, `GlobalReadSource` at `x = G` | the read statement, zero | `zero.$ (zeroMark) → x.$ (T)`, or `zero.$ (zeroMark) → x.[any-taint] (T)` for an `AnyField` target (I14); the condition must be true (§4.4) |
| sink at a call | `TaintMethodSink` | `Sink` | call statement | the sink check (ap.md §4.9); its end facts (below) |
| entry sink | `TaintMethodEntrySink` | none | method start, zero | unconditional only (as today); its end facts |
| exit sink | `TaintMethodExitSink` | none | every exit (normal and exceptional, §4.7) | the sink check (ap.md §4.9); its end facts |
| pass rule | `CopyAllMarks`, `CopyMark` | `CopyData`, `CopyTaintMark` | unresolved call | `CopyAllMarks(P → Q)`: `b.* → b.*` (`b` = the base of `P`), `P.* → Q.*`; `CopyMark(T, P → Q)`: `b.* → b.*`, `P.$ (T) → Q.$ (T)`; with `AnyField` positions: the next table |
| cleaner | `RemoveMark(T, P, reach)`, `RemoveAllMarks(P)` | `RemoveMark`, `RemoveAllMarks` | call statement | `clean` (§5.2) |
| summary rewriter | user-defined source rules whose condition is not statically false, and unconditional user-defined cleaner rules (§5.2) | the same | call statement | `clean(P, exact, T)` on the summary results; an `AnyField` action position: `(P, atAndBelow, T)` for a source (D34), `(P, below, T)` for a cleaner (§5.2) |

A rule position on `S` follows §1.4 first.

Pass rules with an `AnyField` TARGET (`PositionWithAccess(Q, AnyField)`, Go `AnyAccessor`). The field that the rule
writes is not known, so the result is uncorrelated: the target tail is `[any]`. This target is a MAY (D18). Under W6
every result of such an edge is in the demand layer (ap.md §2.3; Lean `AnyTaint.w6t`), and in the backward run every
result of its reversal is in the demand layer, also a `$` result (§4.9). A source is different: its `AnyField` target
is a MUST with the tail `[any-taint]`, and its results keep their layer (ap.md W8; I14, D32). A pass rule with an
`AnyField` position on its premise side is a rule error (§1.3, D33), so it has no row here.

| Pass rule | AP form (with `b.* → b.*`, `b` = the base of `P`) |
|---|---|
| `CopyAllMarks(P → Q.AnyField)` | `P.* → Q.[any]` |
| `CopyMark(T, P → Q.AnyField)` | `P.$ (T) → Q.[any] (T)` |

THE RULE STATEMENT OF A CALL. The source rules of a call statement form a CALL STAGE of their own, beside the bindings
(the sources stage, analyzer-core.md §4.5). A call stage gives only the results of its edges: a fact on a base that no
edge of the stage reads gives no result (it does not pass through the stage). The rule statement reads the bound,
uncleaned facts (callee coordinates) and the zero fact. Its touched set is every base of its edges (a call stage: the
zero base, the base of every position that a source literal reads, and the source targets), so its reversal adds no
identity edge. Its edges are the keep edge `zero.$ (zeroMark) → zero.$ (zeroMark)` (I11) and the source edges
of the table above. It has no other keep edge: the bound facts go on through the call, not through the rule statement,
so its only results are the zero fact and the source targets. The source targets go to the caller through the binding
back and the aliases (§3.8 AC3), with the field limit, and join the results of the call (union). The zero fact has no
binding back (§3.3): the zero fact of the caller is the one that passes over the call. The sink patterns of the call
read the same facts (§4.5 step 3, §4.6). The call bindings never touch the zero base (§3.3). The source edges are
statement micro edges for §2.1 step 4: in run 1 the static exception applies to a source edge whose premise is on `S`.
The bound fact has the premise set and the layer of its caller edge `(i → c)`, so the test reads that caller edge.
The joined caller plan supplies normal field work; there is no Position event or abstract class slot (§0.3).

END FACTS. A sink rule can have end-fact actions (`trackFactsReachAnalysisEnd`: a list of `AssignMark(T, P)`). When the
sink triggers, the interpreter applies these actions as the targets of a source: each action gives the zero-to-fact edge
`Zero → (sink statement, P.$ (T))`, or `P.[any-taint] (T)` for an `AnyField` position (I14), in the layer of the sink edge (or of the combination, below). At a call it goes to
the caller through the binding
back and the aliases (AC3); at a method start or at the exit it joins the facts there (§4.3, §4.7). The field limit
applies to it in each case (I10).

THE END-FACT STAGE takes no input fact. When a sink triggers (a plain sink on a fact, or a conjunctive sink on the
combination that an item completes, §5.3), the stage applies its edges to the zero fact, in the layer of the sink edge
or of the completed combination (ap.md §2.2, §4.9). At a call this is the end-facts stage of the call plan
(analyzer-core.md §4.5). At a method start and at an exit the same rule gives the end facts that join the facts there.

The interpreter gives the end facts of the vulnerability to the vulnerability store: the sink witness keeps them
(ap.md §8.10; today the end-fact requirement of `TaintSinkTracker`). Today the trace resolution reports such a
vulnerability only if one end fact reaches the end of the analysis (`VulnerabilityChecker`). The trace resolution is
outside this specification (ap.md §8.10 THE TRACES): no part of the analysis checks whether an end fact reaches the
end of the analysis. This is a KNOWN GAP for now: every vulnerability of a sink rule with end-fact actions that the
report holds is in the output, also one whose end facts never reach the end of the analysis (ap.md §11.1).

Pooled ND candidates add a related precision limit (§5.3). If different call sites supply `x:T` and `y:U`, a pooled
`x:T AND y:U` candidate can generate `Return:V` under zero. Full caller-side ND matching cannot reject this
zero-premise result. Preserve the specified zero-GEN operation; do not silently change its premise to implement
storage removal. The allowed candidate approximation includes these derived effects. A later concrete validator
must check the trigger under one concrete call, or retain an explicit derivation obligation. It must not treat
the generated zero fact as an unconditional source proof. Such validation is deferred, not an existing filter.

### 4.2 Conditions

* Validate the whole raw rule before folding, including positions and actions in a discarded branch.
* For a source or sink, apply `SignedConditionNormalizer` before static evaluation and again afterward.
  Fold constants, duplicate operands and direct complements. Use non-distributing unit propagation to prove
  false/true scopes after array specialization. Do not guess assignments, distribute operators or use a SAT solver.
  A proved FALSE has no action, read/drop footprint, request, witness, seed or trigger. If a scope is neither proved
  false nor proved true, retain its live read structure. General absorption is outside O1.
* Evaluate surviving non-mark atoms (`IsConstant`, `IsNull`, `ConstantEq/Lt/Gt/Matches`, `TypeMatches`,
  `IsStaticField`, ...) statically per statement, with the existing alias information. Preserve signed atoms until
  both folds are complete. A polarity-sensitive evaluator must not erase `P AND NOT P` first.
* After these folds, project residual negative mark literals to true for eligible call/exit sources and sinks,
  then fold constants again. Entry eligibility requires the signed/static condition to be true before projection.
  Cleaner action normalization and the remaining cleaner/pass policies below are separate.
* A mark atom is a LITERAL. For a sink, `ContainsMark(P, T)` is the pattern `(P, $, T)` and
  `ContainsMarkOnAnyField(P, T)` is the pattern `(P, [any], T)`. For a source, a literal is the premise
  of a micro edge: `ContainsMark(Q, T')` gives the premise `Q.$ (T')`, and `ContainsMarkOnAnyField(Q, T')` gives the
  premise `Q.[any] (T')`. An `[any]` premise covers every fact at or below `Q`; a fact above `Q` also applies, with a
  demand-layer result (I2, ap.md §4.1; a forward `[any-taint]` fact keeps its layer if its exclusion admits the step
  down to `Q`, I2). Both premises have a concrete mark, so S8 and I7 hold. A literal of a conjunction has the same two
  forms (ap.md §4.6). The reversal of an `[any]` literal (§4.9) gives the requirement `Q.[any] (T')` in the demand
  layer (W6), and the seed of an `[any]` sink pattern has the tail `[any]` too: the backward run has no `[any-taint]`
  (I14). The sink check reads a forward `[any-taint]` fact as an `[any]` fact with no location in its excluded part
  (ap.md §4.9; Lean `AnyTaintEx.checkX`, the vectors `AnyTaintEx.Vec.check_vectors`).
* ARRAY ELEMENTS OF A CALL SINK (JVM, as today). For a sink at a call, a positive literal on an argument position
  `arg(i)·ρ` whose argument may be an array is the disjunction of the patterns `(arg(i), ρ, t, T)` and
  `(arg(i), [e]·ρ, t, T)`. The argument may be an array if `typeMayBeArray` (§5.1) holds for the static type of the
  argument value at the call (today `JIRFactTypeChecker.callArgumentMayBeArray` and `arrayElementConditionReaders`,
  `JIRMethodCallTaintUtil.kt:186-203`). Compile this as a local OR of the root and element-prefixed leaves.
  Do not form the Cartesian product across arguments or actions. This applies only to call-sink arguments, not to
  receivers, `Result`, entry/exit sinks or source leaves. Backward seeds visit both active choices (§4.9).
* For a source or sink, the residual negative-literal projection after signed folding is an over-approximation.
  Keep one owned positive NNF DAG per bound occurrence; production does not expand it to DNF cubes.
  A conjunction of literals that different facts satisfy (a positive literal on another position): a source, at a call or at an exit, makes an ND edge (§5.3, §4.7 step 1); a sink is a CONJUNCTIVE SINK
  (ap.md §4.9, §5.3). The interpreter sets no layer. The layer of a result belongs to the propagation edge, and only
  the AP operations change it (ap.md §2.2): a fact that only overlaps a literal gives a demand-layer result, but a
  normal `[any-taint]` fact keeps its layer (ap.md §4.1, §4.6; I2). An `[any-taint]` fact overlaps a literal only in
  the locations that its exclusion admits (ap.md §4.6). A negated literal that counts as true is the expected
  over-approximation of a path-insensitive engine, as the conjunction is (the reference semantics of rules, ap.md
  §3.5; ap.md §4.6, §11.1).
* CONDITION AND PROOF SCOPE. `RuleOccurrenceId` identifies a bound configured occurrence before filtering.
  `ProofKey` identifies an observed canonical leaf/OR-choice support in that scope. Keys include the form owner,
  physical method, applicable context, statement and provider ordinal; cache epoch, run index, direction and field
  limit do not change them. Separate configured occurrences never share support merely because their displayed
  rule IDs or tuples are equal. Legacy/manual forms can retain numbered alternatives; production NNF has no
  prebuilt cube inventory. Witnesses keep proof scope, method context, run and complete ordered support headers.
* A PASS RULE (`CopyAllMarks`, `CopyMark`) has no mark-dependent condition: after the static evaluation of the non-mark
  atoms, its condition has no mark literal. The copied mark of `CopyMark(T, P → Q)` is not a condition: it is the
  premise `P.$ (T)` of the pass edge (§4.1; an `AnyField` premise is a rule error, D33). A pass rule is a handcrafted
  summary of an unresolved callee, and a mark literal has no exact form for it: `CopyAllMarks` has the target mark
  `*`, so a concrete premise mark would make an edge that is not mark-reversible (I11 (b)), and an ND edge needs a
  concrete target mark (ap.md W7). The interpreter
  reports a pass rule with a mark literal as a rule error, logged once and not rejected (§1.3), and applies it without
  its mark literals (it fires more; this is sound, but not precise: gap G11). So a pass rule never makes an ND edge.
* JVM CLEANER ACTION NORMALIZATION. Before the interpreter builds forms, the shared method-rule resolver normalizes
  each resolved cleaner action against its own condition. It writes the condition in negation normal form. For
  `RemoveMark(T, P, Exact)`, it assumes `ContainsMark(P, T)`: removing an absent mark at that same position is a no-op.
  For a terminal `P.AnyField` action, it also assumes `ContainsMarkOnAnyField(P, T)`. It folds the condition under these
  assumptions, drops actions whose condition becomes false, and groups actions with equal resulting conditions.
  `RemoveAllMarks` has no named-mark assumption. A non-AnyField action with `ExactAndAnyField` reach keeps its mark
  condition: an absent mark at `P` does not imply that all descendant marks are absent. Both ordinary resolution and
  relevant-rule resolution use this normalization. The JVM adaptation uses the cleaner normalization of
  `saloed/cleaner-action-normalisation`, commit `25a5307cf`, with this broad-reach guard.
* For a resolved CLEANER the safe direction is the opposite: a cleaner that fires removes real taint. So the interpreter applies
  only the UNCONDITIONAL cleaners:
  * a non-mark atom is decided statically: a false atom removes the rule, a true atom drops out of the condition;
  * if no mark literal is left, the cleaner is unconditional, and it applies (§5.2);
  * if a mark literal is left (positive or negated, on any position, `ContainsMark` or `ContainsMarkOnAnyField`), the
    cleaner does not act and raises no request: the propagation edge passes unchanged with its layer.

  A cleaner that does not act only keeps more taint, so this is sound. It is the expected over-approximation of a
  fact-local, path-insensitive engine, as the conjunction is (ap.md §4.6). Cleaning on a condition that the fact does
  not decide is unsound. Today the condition is evaluated on the fact (`TaintFactAwareConditionEvaluator`); this is
  deviation D20 (§6).
  Examples: `RemoveMark(T, P)` with no condition is `(P, exact, T)`. In JVM resolution,
  `RemoveMark(T, P, Exact) if ContainsMark(P, T)` becomes this unconditional action.
  `RemoveMark(T, Argument(0)) if Not(ContainsMark(Argument(1), RAW))` keeps its unrelated mark condition and never cleans
  in the interpreter. A broad root removal conditioned on the root mark also keeps its condition and never cleans.
  The summary rewriter (§5.2) is not a cleaner placement, and D20 does not govern it. It selects a user-defined
  cleaner rule only if the rule is unconditional, so a conditional user-defined cleaner does not act through it either.
* Go pass rules have no condition.

### 4.3 Entry order (method start, as today)

| Language | Fact | Order |
|---|---|---|
| JVM | zero | 1. the zero fact; 2. entry sinks with a true condition (vulnerability, end facts); 3. entry-point sources with a true condition. The marks of step 3 are the ENTRY MARKS (§4.7). The field limit applies to the end facts of step 2 and to the results of step 3 (ap.md §4.4). |
| JVM | initial fact | 1. the start fact (ap.md §6.5); 2. the filter by the context type (§5.1). |
| Go | zero | the zero fact only (no entry rules). |
| Go | initial fact | the start fact, with no filter. |

THE RULE STATEMENT OF THE METHOD START (JVM; STATEMENT mode, analyzer-core.md §4.9). It touches the zero base, with the
keep edge `zero.$ (zeroMark) → zero.$ (zeroMark)` (I11 (d)), and each base `b` with a context filter (§5.1, row
"method start"), with the identity edge `b.* → b.*` and that filter as its operand filter. Its other edges are the
entry-point sources `zero.$ (zeroMark) → P.$ (T)`, and `zero.$ (zeroMark) → P.[any-taint] (T)` for an `AnyField`
target (I14). A source target is a gen-only target (not touched, as in A3). So an
initial fact passes unchanged, or passes its context filter. The filter reads the context of the method key, so the
entry rules are per method key (analyzer-core.md §4.8).

### 4.4 Statement order (non-call)

| Fact | Order |
|---|---|
| zero | 1. `zero → zero`; 2. read sources with a true condition (JVM `x = C.s`; Go `x = y.f`, `x = G`); 3. JVM: exit sources with a true condition, at each exit (§4.7). A statement with a read source touches the zero base: its summary has the keep edge `zero.$ (zeroMark) → zero.$ (zeroMark)` and the source edge (I11). Every other non-call statement does not touch the zero base, so the zero fact passes unchanged. The type info facts (lambda allocations, closures) exist only in the prescan. |
| other | §2.1, steps 1 to 6. |

### 4.5 Call order (a fact)

This order is for a caller fact that is not the zero fact. The zero fact uses §4.6. For the caller edge
`(i, layer) → c` at the call statement `s`, in JVM and in Go:

1. RELEVANCE. If `s` does not touch `c.base`, `c` passes over the call. Stop.
2. BINDING IN. Apply the binding edges into the callee, with the caller-side type filters (§3.1). The result is the
   bound facts, one per callee position.
3. SINKS. Check the sink rules on the bound facts: the PRE-CALL, UNCLEANED fact. Requests: §5.4. End facts: §4.1;
   they go to the caller as the source results of step 4.
4. SOURCES. Apply the rule statement of the call (§4.1) to the bound facts, as statement micro edges (§2.1 step 4,
   with the static exception on the caller edge); evaluate the ND conjunctions (§5.3). The results go to the caller
   through the binding back and the aliases (no rewriter), then the field limit.
5. PER CALLEE POSITION, for each bound fact `a`:
   1. CLEANERS: `clean` for each cleaner rule, chained in the rule order (§4.8, §5.2). A `RemoveAllMarks` rule on a
      position of `S` applies at its place in this order as the kill of §1.4, not as a cleaner: a statement summary
      that touches only `S` (§2.1 steps 2 to 4). A bound fact on another base passes the kill unchanged; a bound fact
      on `S` keeps only what a keep edge gives (with the static exception on the caller edge). The generated Spring
      cleanup form adds its primitive whole-base cleaner here (§1.4, §5.2);
   2. RESOLVED callees: the callee processing of ap.md §5.3 for each callee, with the cleaned fact as the added fact
      (events E1 and E2: emit without a callee input store, retain the required caller N2 subscription, service
      standing/new mark queries in both arrival orders, and apply the summaries that it satisfies and the records
      that apply to it, ap.md §8.7 R4: their premise covers it or lies inside it); each new initial fact
      starts with the context filter (§4.3); JVM constructor: the cleaned fact also passes over the call (§3.5);
   3. UNRESOLVED callee: the default identity and the pass rules (§3.7), as statement micro edges on the added fact
      (§2.1 step 4, with the static exception on the caller edge).
6. RETURN. For every result of step 5 (a summary result, an unresolved result): the summary rewriter in callee
   coordinates (§5.2), then the binding back with its filters, then the aliases (§3.8), then any pending native
   must-record demotion (ap.md §4.3), then the field limit. A
   constructor pass-over result skips the rewriter.

The order has three effects: a sink sees the fact before the cleaners; the pass rules see the cleaned fact; the summary
rewriter acts only on the results of the call.

APPLICATION MODES. A CALL STAGE gives only the results of its edges: the binding in, the rule statement of the call
(§4.1), the unresolved callee (§3.7), the constructor pass-over (§3.5), the binding back and the aliases (§3.8). A
bound fact on a base that no edge of a stage reads gives no result in that stage. The `RemoveAllMarks` kill on `S` is
a statement summary that touches only `S`: a fact on another base passes it unchanged (§2.1 step 2). The end-fact stage
takes no input fact (§4.1).

### 4.6 Call order (the zero fact)

| Language | Today | New |
|---|---|---|
| JVM | unconditional sinks, unconditional sources, call-to-return zero, call-to-start zero | the same |
| Go | call-to-return zero, call-to-start zero, sinks, sources (one set of outputs) | as JVM; the outputs of one step are a set, so the result does not change |

The zero fact evaluates the sink rules and the rule statement of the call (§4.1) with no fact. An unconditional rule
fires (also a rule whose mark literals are all negated, §4.2). The stored assumptions of a conjunctive micro edge or
of a sink alternative at the statement can complete a conjunction (§5.3, as today). The source results go to the
caller through the binding back and the aliases (AC3), then the field limit. Then the zero fact passes over the call
and enters every resolved callee (§3.3). The zero fact takes no cleaner, no pass rule and no rewriter.

### 4.7 Exit order

JVM, at `JMethodExitNormalInst` (after every `return`) and at `JMethodExitExceptionalInst` (after every `throw` and
every exception that leaves the method), for each fact `f`. At the exceptional exit the rule position `Result` reads
the exception base `exc` (the thrown value), as today (`JIRMethodSequentFlowFunction.kt:120-126`). Steps 1 and 2 apply
at both exits; steps 3 to 5 apply only at the normal exit, because only the normal exit makes a summary edge (§3.4).
The order of the steps is as today. The exit sources of an exit form the RULE STATEMENT OF THE EXIT: its edges are
statement micro edges for §2.1 step 4, so in run 1 a conditional exit source whose premise is on `S` takes the static
exception on the edge of `f` (§1.4). It touches the zero base, with its keep edge (I11 (d)), and the base of every
position that a literal of an exit source reads, with the identity edge `b.* → b.*`. A source target is a gen-only
target. It has no type filter. So `f` stays in the worklist (step 1), and in the backward run a requirement on a read
base or on a target passes (§4.9).

THE UNCONDITIONAL EXIT RULES (the unconditional exit sources and the unconditional exit sinks) fire on the zero fact at
BOTH exits (§4.4; D22). This is a deviation from today (D26): today they fire only at the normal exit
(`JIRMethodSequentFlowFunction.kt:228-233`). So an unconditional exit sink can report at both exits of one method;
this double report is accepted.

1. The worklist is `f` and the results of the exit sources whose condition `f` satisfies, after the field limit (ap.md
   §4.4). The results of a plain exit source keep the premise of `f`. A CONJUNCTIVE EXIT SOURCE (an alternative of its
   condition with two or more positive literals) is an ND edge at the exit, as at a call (§5.3; D31). Its target tail is
   `$`, or `[any-taint]` for an `AnyField` target (I14). Each literal
   stores its input in the conjunction store of the method key (per conjunctive micro edge, exit statement and literal
   index; ap.md §8.9). A full combination joins the worklist as an item with the union of the premise sets (§5.3),
   after the field limit. It goes through steps 2 to 5 as every item, so at the normal exit it becomes a summary, an
   ND summary if its premise set has two or more members; the callers apply it by event E6 (ap.md §4.6, §5.3). It is
   not a rule error.
2. For each item: check the exit sinks. Their end facts (§4.1), after the field limit, join the worklist. A literal of
   a conjunctive exit sink stores its input in the conjunction store (§5.3).
3. THE GLOBAL-STATE RULE: for an item on `S` whose premise is the zero fact (a state that the method or its callees
   set), if a mark literal (`ContainsMark`, `ContainsMarkOnAnyField`) of an exit sink holds on a part of it, drop that
   part from the summary edge. This applies to a plain and to a conjunctive exit sink. The rest of the item stays. The
   dropped part is not lost for the sink: for a conjunctive exit sink, step 2 stored it as the input of that literal.
   So it stays an assumption for the next evaluation attempts of the sink, and a later item can complete the
   combination with it. A caller-set `S` fact (an item whose premise is not the zero fact) is evaluated (step 2: it can
   report, D21; a conjunctive literal stores it) but not dropped: it returns to the caller through the callee summary,
   also the run-1 FLOW summary and its record (§3.3). The scope is as today: today the exit sinks run only on
   zero-premise edges (`JIRMethodExitRuleProvider.kt:18-19`), and only a reached sink drops the `ClassStatic` facts
   that its condition read (`JIRSequentTaintUtil.kt:76-85`, `JIRMethodSequentFlowFunction.kt:186-188`). The drop also
   for a conjunctive exit sink that is not complete is deviation D30. The rule generator keeps the state of a rule as a
   mark on a synthetic class position (§1.4); the exit sink of the method that sets it, or of a caller of that method,
   consumes it. This is gap G2.
4. For a zero-premise fact on `this` or `arg(i)`: remove the entry marks (§4.3), so that an entry-point source does not
   leak into the callers (gap G2). The removal reads the MARK: it removes EVERY LEAF WITH AN ENTRY MARK, AT ANY
   DEPTH, with both tails: a `$` leaf `(b, p, $, m)` for every path `p`, and an `[any-taint]` leaf
   `(b, p, [any-taint], E, m)` with its exclusion (for example the leaf that the `AnyField` entry-point source of a
   Spring DTO argument made, §4.1). The other leaves of the fact stay. Today `TaintMarkRemover`
   (`JIRMethodSequentFlowFunction.kt:301-314`) removes only the `$` leaf `(b, [], $, m)` at the root path: deviation
   D35.
5. Emit the summary edge. Not at the exceptional exit (its facts end there); not for a `local` base.

Go has no exit rules. The summary edge is the fact after each `return`, for a base that is not a `local`.

### 4.8 Several rules of one kind

| Kind | Combination |
|---|---|
| sinks | each rule is checked; the report is the union |
| sources | the union of the results |
| pass rules | the union of the results |
| cleaners | chained: the rules in list order, the actions in order. Each action applies to the survivors of the previous action. The requests are collected. Keep this order: partial cleaning can change AP precision. |
| summary rewriter | chained, as the cleaners |

CURRENT PREDICATE REVERSAL. The alternative notation below is the reference notation, not an instruction to
build cubes. For a compiled occurrence, reverse each active positive leaf requirement. Unary leaves use ordinary
source reversal; multi leaves force DEMAND, including exact results. A leaf in both classes permits both effects.
The backward run performs no AND join. Demand hand-off seeds all active positive leaves of every matching bound
sink at the witnessed full method key and statement, including unwitnessed OR branches. TRUE seeds no mark
requirement; FALSE seeds nothing. When an end action reverses, trigger this same full positive scope once per
(method key, statement, `RuleOccurrenceId`), across its end actions, also for a CONFIRMED entry. Do not restrict
that trigger to the proof that happened to fire forward. Preserve unconditional source-hit admission separately.

### 4.9 Backward run

The backward run applies the AP rules of ap.md §9.2 to the reversed program. This section gives the interpreter part.
Micro edges use AP §9.1's generic reversal. Cached summary records use the
separate transient read views of AP §8.7 R3. F76's concrete star premise for a
must-summary conclusion changes the record reader, not these interpreter forms.
The interpreter gives only the forward forms; the analyzer core makes the backward forms by a reversal: the reversed
statement summary, the reversed entry and exit rules, and the reversed call plan (analyzer-core.md §4.5, §4.9). The
call order below is the reversal of the forward call order of §4.5, stage by stage; the reversal drops every type
filter and every forward-only guard (the alias selection AC3, AC4; the sink trigger of the end facts: in its place the
reversed end-fact edge fires the sink seeds of its alternative, RULE ROLES below).
The backward run is a restricted run (ap.md §6.1). It has no request (§5.4). Since F72 it is not concrete: a demand
pattern with the mark `*` weakens the requirement to a FLOW requirement with the mark `*` (ap.md §6.3, §9.2;
`ap-history.md` F72), and the requirements that it gives have the marks `*` and `*∖X`. On such a requirement a
reversed micro edge whose premise has a concrete mark gives nothing, with no request (the reversal of a conditional
source or of a mark-changing pass rule, for example). A concrete requirement (from a seed, through the concrete
patterns) stays concrete. The interpreter forms do not change: the AP operations read the mode. (Before F72 every
backward fact was concrete, because the seeds have concrete marks and the emission copied the mark: Lean
`BExact.DB_concrete`, `DB_no_request`, for the concrete design.)

STATEMENTS. The reversed statement summary of a non-call statement (ap.md §9.1, §9.2; Lean `Reverse.Stmt.rev`):

* the touched bases: the forward touched bases and every target base of a forward micro edge;
* the micro edges: the reversal of every forward micro edge (ap.md §9.1), and the identity edge `b.* → b.*` for every
  target base `b` that the forward statement does not touch (a gen-only alias target, A5);
* a conjunctive edge `L1 ∧ … ∧ Lk → z` reverses into the k independent plain edges `rev(Lj, z)`
  (ap.md §9.2). The forward result is NORMAL if every literal contribution is NORMAL, and DEMAND if any is DEMAND
  (ap.md §4.6). The backward run makes no ND edge. If the edge has two or more positive literals (a conjunctive source
  at a call, §4.1, or at an exit, §4.7 step 1, D31), EVERY result of these plain edges is in the demand layer, also a
  `$` result, as for the reversal of a may (the pass-rule row of RULE ROLES): a requirement that reaches one literal
  is not a converse flow of the conjunction. So no backward summary through such an edge is a record (ap.md §8.7 R1)
  and the hand-off gives it to the next forward run as a demand edge (ap.md
  §9.2, case 3). This removes a false positive that existed before F70: the reversal of such a backward summary was a
  forward record from one literal, which drops the other literals (`ap-history.md` F70, the review round). Argued:
  the model has no restricted run with ND edges (ap.md §11.2);
* no type filter, no mark policy, no request (and no liveness step, as in the forward run, §2.1 step 1);
* the zero fact: a forward statement that touches the zero base keeps it (I11), so its reversal keeps it too.

RULE ROLES (ap.md §9.2 gives the AP rules):

| Forward rule | Backward role |
|---|---|
| source (at a call, entry-point, exit, read), end-fact action | its reversed edge: a requirement on its target continues to the zero fact (unconditional source: the zero demand of ap.md §9.2) or to its read position (conditional source). The reversed `[any]` literal of a conditional source (also of a conjunctive source, §5.3) gives the requirement `Q.[any] (T')` in the demand layer (W6): `Q.[any] (T') → P.t (T)` reverses to `P.t (T) → Q.[any] (T')`. The reversed edges of a conjunctive source with two or more positive literals give every result in the demand layer (STATEMENTS above; §5.3). A source target `P.[any-taint] (T)` is the reversed premise `P.[any] (T)` (the backward run has no `[any-taint]`, I14). The source is a must, so the results of its reversed edge follow the ordinary rows of ap.md §4.1: a requirement at or below `P` continues in its layer. When the reversed edge of an unconditional source applies, the backward run records a SOURCE HIT: a source seed of the next forward run (ap.md §8.11, §9.2). The backward run has no sink check, so a forward source is not a backward sink (ap.md §9.2). THE TRIGGER OF AN END FACT (ap.md §9.2): an end fact exists only after its sink triggers (§4.1, END FACTS). So when the reversed edge of an end-fact action of the sink alternative `A` at the statement `s` of the method key `M` applies to a requirement, it gives the zero fact (as above) and the backward run also fires the sink seeds of `A` at `(M, s)` (the SEEDS below, once per `(M, s, A)`), also when the vulnerability of `A` is CONFIRMED. Then the next forward run demands the witness of the trigger, and the sink triggers again (the forward run does not restrict an end-fact action, ap.md §6.1 rule 6). Argued: the model has no end facts (ap.md §11.2). |
| sink (at a call, entry, exit) of a DEMAND entry of the report after the previous forward run (ap.md §8.10, §9.2); the sink alternative of an end-fact action that a requirement reaches (the row above) | a SEED (below). A sink with several positive literals seeds one requirement per literal. A sink with no positive literal seeds no requirement: the zero demand covers its witness. The seed of an `[any]` pattern (`ContainsMarkOnAnyField`) has the tail `[any]` and is in the demand layer (W6; I14). |
| other sinks, also the sinks of a CONFIRMED vulnerability (except through THE TRIGGER OF AN END FACT) | none |
| pass rule, default getter rule | its reversed edges (from and to swapped). The `AnyField` target of a pass rule (a may, D18) is the reversed premise `Q.[any]`. EVERY result of such a reversed edge is in the demand layer, also a `$` result: the forward target `[any]` tells it (I14; the backward W6, argued, ap.md §11.2). So no backward summary through it is a record. (An `AnyField` premise is a rule error, D33.) |
| cleaner (an unconditional cleaner, §4.2, also the generated Spring whole-base cleaner, §1.4), summary rewriter | the same primitive cleaner; a field action is reversed write, clean temporary, reversed read (§5.2, F74). Reverse the action order. |
| the `RemoveAllMarks` kill on `S` (§1.4) | the same keep edges: the reversal of a keep edge is the same edge (ap.md §9.2); at the place of the reversed cleaners (step 6 below) |
| the global-state rule and the removal of the entry marks (G2) | none |
| type filter, mark policy | none |

THE ANY TAILS IN THE BACKWARD RUN (ap.md W8, §9.2). The backward run has no `[any-taint]`: every backward premise,
requirement and pattern with an any tail has the tail `[any]`, and W6 puts every `[any]` result in the demand layer, as
before F69 (argued, ap.md §11.2). The interpreter gives only the forward forms (I14). The reversal reads the forward
target tail of each edge (the rule roles above): a forward `[any]` target (a may) gives demand results, a forward
`[any-taint]` target (a must) gives the results of the ordinary rows.

CALL ORDER (the mirror of §4.5). For a requirement `c` after the call statement `s`:

1. RELEVANCE. The reversed call touches the forward touched bases of `s` and the alias bases of §3.8. If it does not
   touch `c.base`, `c` passes over the call. An alias base that the forward call does not touch also keeps the
   requirement over the call: `b.* → b.*` from after the call to before it, in caller coordinates (A5; the reversal of
   its forward pass-over; the pass-over stage `AFTER → BEFORE` of the reversed plan, analyzer-core.md §4.5).
2. REVERSED BINDING BACK. The converse of each forward binding back and of each call alias edge (`b.q.* → P.*`, §3.8),
   into callee coordinates at the forward exit. No filter.
3. REVERSED SOURCES. The reversed source edges of the rule statement of `s` (§4.1) and the reversed end-fact edges, on
   the result of step 2. A requirement that reaches an unconditional source continues to the zero fact. A conditional
   source gives a requirement at its read position; it enters at step 7.
4. REVERSED REWRITER. The summary rewriter (§5.2) on the result of step 2.
5. CALLEES, on the result of step 4:
   1. RESOLVED callees: the requirement enters each callee at its forward exit (the callee processing of ap.md §5.3 in
      the backward orientation); the backward summaries of the callee bring it to the forward entry of the callee;
   2. UNRESOLVED callee: the reversal of the statement summary of §3.7 (the default identity, the reversed pass rules
      and default getter rules);
   3. JVM CONSTRUCTOR (§3.5): a requirement on a bound position (the receiver, an argument, `S`) also goes from step 2
      directly to step 6 (the reversal of the pass-over; it skips the rewriter and the callee).
6. REVERSED CLEANERS. The cleaners of `s` on the requirement at the callee start (callee coordinates): the
   unconditional cleaners (§4.2), and the keep edges of a
   `RemoveAllMarks` kill on `S` (§1.4), in the rule order.
7. SEEDS AND READ POSITIONS. Here the seeds of the sinks of `s` and the requirements of step 3 at the read positions
   enter, at the bound positions.
8. REVERSED BINDING IN. The converse of each forward binding into the callee, to the caller before the call. No filter.
9. The field limit of the backward run on every result (ap.md §4.4).

THE ZERO FACT. The backward run starts at each root with the zero fact. The zero fact enters a method at every forward
exit node (the normal exit and the exceptional exit, ap.md S4). It passes over every call. It also enters every
resolved callee DIRECTLY: it is an initial fact of the callee at the forward exit of the callee, with no emission and
no demand pattern (ap.md §9.2; Lean `Backward.DB`, rule `zin`). This entry is not a binding and not the reversal of a
binding. Each caller applies a zero-premise backward summary of the callee to its own zero fact at the call site (the
balanced return; ap.md §9.2). The implementation wires a node that reaches no exit node to an exit node (I11 (e)), so
the zero fact reaches every node on a CFG path from the method entry (the backward reference branch does this with
`JIRBackwardExitWiringGraph`, which wires the node to the exceptional exit).

SEEDS. The backward run starts with the seeds of the sink witnesses of the DEMAND entries of the report after the
previous forward run: the vulnerabilities that the latest complete forward run reported and that no complete forward
run confirmed so far (ap.md §8.10, §9.2). A CONFIRMED vulnerability is final (ap.md §8.10), so it gets no seed there:
every real vulnerability is reported by each forward run or confirmed by an earlier one (ap.md §6.6). During the run,
THE TRIGGER OF AN END FACT (RULE ROLES) adds the sink seeds of a sink alternative whose reversed end-fact edge applies
to a requirement, once per (method key, statement, alternative), also when its vulnerability is CONFIRMED (ap.md
§9.2). Each seed is the zero-to-fact
edge `Zero → (sink statement, requirement)` where the zero fact reaches the sink statement (ap.md §9.2). The requirement is the sink pattern, cut by the field limit of the
backward run (ap.md §4.4; Lean `Backward.DB`, rule `seed`). For an `[any]` pattern the requirement has the tail
`[any]` and is in the demand layer (W6; the backward run has no `[any-taint]`, I14). At a sink call the seed enters
AFTER the reversed cleaners of that call (step 7), at the bound positions, and goes on through the reversed binding
in. This mirrors §4.5, where the sink check (step 3) comes before the cleaners (step 5.1). So a method that is both a
sink and a cleaner of the same
mark does not kill its own seed. The seed of an exit sink enters at the forward exit (normal or exceptional) where the sink is (§4.7). There it takes
the reversed exit sources of that exit, as the start fact does: forward, the exit sources (§4.7 step 1) come before the
exit sinks (step 2), so an exit sink can read the result of an exit source. A source hit there is recorded (§4.9 RULE
ROLES). An entry sink is
unconditional (§4.1), so it seeds no requirement.

---

## 5. Where the primitives apply

### 5.1 Type filter: `filter(base, may)`

The JVM predicate for the static type `t` is today's `JIRFactTypeChecker`, written as a prefix-closed predicate on the
path. The reference form below uses these helpers:

| Helper | Value |
|---|---|
| `typeMayHaveSubtypeOf(t, c)` | true if a value of the static type `t` can be an instance of the class `c` (today's test in `JIRFactTypeChecker`): for a class type, one of the two types is assignable to the other; for an interface, an implementation can be a subtype of `c`; for a type variable, every bound passes |
| `typeMayBeArray(t)` | true if `t` is an array type, `Object`, or a type variable or wildcard whose bounds allow an array |
| `t.elementTypeOrNull()` | the element type of an array type `t`, else `null` |
| `t.unboxIfNeeded()` | the primitive type of a boxed type `t` (for example `int` for `Integer`), else `t` |
| `a.declaringClass` | the class that declares the field accessor `a`, `null` if it is not known |

The filters also make the validity condition I13 (ap.md S13) true: the exactness and confirmation theorems read only
the locations that every filter accepts, and this placement owns that condition.

```kotlin
/** may(t, p): a path p below a value of static type t may exist. Prefix-closed: may(t, p ++ q) ⇒ may(t, p). */
fun may(t: JIRType?, p: List<Accessor>): Boolean {
    if (t == null || p.isEmpty()) return true
    return when (val a = p.first()) {
        is FieldAccessor -> t is JIRRefType &&
            (a.declaringClass == null || typeMayHaveSubtypeOf(t, a.declaringClass))   // deeper accessors: not checked
        ElementAccessor -> t is JIRRefType && typeMayBeArray(t) &&
            (t.elementTypeOrNull()?.let { may(it, p.drop(1)) } ?: true)
        else -> true                                                                     // <C> (ap.md W5: no other accessor)
    }
}

/** The mark policy (not a may-predicate): it drops a concrete mark directly on a value of a primitive or boxed type,
 *  unless the mark is primitive-tracking. It reads the type at the root (`t`) and below each `[e]` (the element type),
 *  as today (`JIRFactTypeChecker` gives `FilterNext` with the element type at `[e]`). Below a field accessor it reads
 *  no type. Types of ap.md §3.4. */
fun markPolicyKeeps(t: JIRType?, f: PathFact): Boolean {
    val m = f.mark
    if (m !is MarkSlot.Concrete) return true
    var type: JIRType = t ?: return true
    for (a in f.path) {
        if (a != ElementAccessor) return true                  // below a field or a class: no type is read
        type = type.elementTypeOrNull() ?: return true         // the element type is not known
    }
    return type.unboxIfNeeded() !is JIRPrimitiveType || isPrimitiveTracking(m.mark)
}

/** A PRIMITIVE-TRACKING mark is a mark of a rule with the option `primitive-tracking: true`. Today the rule loader
 *  adds the suffix `%%primitive%%` to the name of each such mark (`PrimitiveTaintExt`). */
fun isPrimitiveTracking(mark: TaintMark): Boolean = mark.name.endsWith("%%primitive%%")
```

Rules:

* The filter tests only the concrete path: a fact passes if `may(t, f.path)`. The `*`, `[any]` and `[any-taint]` tails
  are always kept; the filter never changes a tail or the exclusion of an `[any-taint]` fact. The analysis does not
  store a filter in a fact or an edge, and it
  does not propagate it (ap.md §4.8). So a `*`, `[any]` or `[any-taint]` fact keeps the locations below its path that
  `t` cannot have: an expected false-positive source (ap.md §11.1). Such a location is not valid (I13), so the
  exactness and the confirmation of an `[any-taint]` edge do not read it.
* Policy cases are outside ap.md S5 (they can drop a real flow), as today: the mark policy above; `[e]` on a class type
  other than `Object` (for example `Cloneable`, `Serializable`). The mark policy is not a
  type filter: it reads the mark, not only the path, and the model has no mark filter. It is gap G6.
* THE APPLICATION POINT OF THE MARK POLICY: at every point of the table below where the analyzer applies a type filter
  to a base with the static type `t`, it also applies `markPolicyKeeps(t, ·)` to the same facts, after the filter (as
  today: the policy is part of `JIRFactTypeChecker`). Go and the backward run apply no mark policy.
* Two filters on one base are a conjunction.

| Point | Base | Type | Today | New |
|---|---|---|---|---|
| operand of `x = y`, `x = a op b` | y; a, b | the static type of the operand | yes | the same |
| cast `x = (T) y` | y | `T` (narrows y on the normal path) | yes | the same |
| field read or write `y.f` | y | the static type of y AND the declaring class of f | yes | the same |
| array read or write `y[i]` | y | the static type of the array | yes | the same |
| lhs of an assignment | x | the static type of x | yes | the same, on the results (§2.1 step 5) |
| binding in: receiver | o | the declaring class of the method that the call names | yes | the same |
| binding in: argument | ai | the static type of `ai` (caller side) | yes | the same |
| binding back: receiver, argument | o, ai | the static type at the caller | yes | the same |
| binding back: result | r | the static type of the lhs | yes | the same |
| method start | `this`; `arg(k)` | the context type; `this` with no context: the enclosing class | yes | the same |
| pass rule | the from and to positions | the declared types of the positions in the signature of the called method | yes | the same |
| summary application, under a `*` node | caller content | the type of the path to the `*` | yes | none (D13) |
| summary exit, compatibility | summary conclusion | the last field type of the premise | yes | none (D14) |
| backward run | none | none | none | none |
| Go | none | none | none | none |

### 5.2 Cleaner: `clean(cleaner)`

A cleaner is `(base, path, reach, mark)` with `reach ∈ {exact, below, atAndBelow}` and `mark` one mark or all marks
(ap.md §4.7). The interpreter maps the rule actions as follows. The base is in callee coordinates (§1.3). A cleaner on
a static position follows §1.4 first.

FIELD-POSITION ACTIONS (F74). A cleaner on an ordinary field `x.f` has this reference form:

```text
tmp = x.f
clean(tmp, reach, mark)
x.f = tmp
```

A generated `tmp` has `TemporaryKey(ActionId, substep)`. The action key includes its occurrence, application role,
original action ordinal, relevant-mark ordinal and generated field/string-byte step. Rebuilding the same generated
operation preserves this value key; distinct operations remain separate. Manual temporaries remain fresh.
The forward and reversed field actions share the key. The read and strong write use §2.2 or §2.3, with their identity and keep
edges, filters and field limits. The primitive cleaner has the empty path on `tmp`; it keeps the action's reach
and mark. Keep the temporary facts until the write-back; then discard the temporary. The backward order is the
reversed write, the same primitive cleaner, and the reversed read. This form applies at the cleaner placements
and the summary rewriter below. It preserves a normal FLOW fact outside `x.f` with all its marks; an atomic
primitive on the field path can instead exclude a mark from the entire ancestor fact. The AP primitive table
(`ap.md` §4.7) remains the table for `clean(tmp, reach, mark)`.

| Rule action or generated form | Cleaner | Note |
|---|---|---|
| `RemoveMark(T, P, Exact)`, P with no `AnyField` | `(P, exact, T)` | |
| `RemoveMark(T, P.AnyField, Exact)` | `(P, below, T)` | today: `cleanAnyFieldMark(keepStart = true)` |
| `RemoveMark(T, P, ExactAndAnyField)` | `(P, atAndBelow, T)` | the meaning of the rule (D9) |
| `RemoveMark(T, P.AnyField, ExactAndAnyField)` | `(P, below, T)` | every location at or below a field of `P` is strictly below `P` |
| `RemoveAllMarks(P)` | `(P, atAndBelow, all)` | today: the subtree at P goes |
| `RemoveAllMarks(P.AnyField)` | `(P, below, all)` | today: only an `[any]` child goes (D10) |
| generated Spring cleanup form (D27) | `(S, [], atAndBelow, all)`: the whole-base cleaner | built directly with the existing static base; every fact on `S` goes (§1.4, I12 (e)) |
| Go `RemoveMark(T, P)`, `RemoveAllMarks(P)` | as the JVM `Exact` rows | P with `AnyAccessor` maps to `below` |
| JVM position of type `String` | also `(P.<string-bytes>, same reach, same mark)` | as today |

ON AN `[any-taint]` FACT the primitive cleaner compares the locations as for an `[any]` fact, with no location in the excluded
part (ap.md §4.7, `cleanPos`). For the forward fact `(x, p, [any-taint], E, T)` and a cleaner of `T` (or of all marks),
with `P = x.p`:

* at `P` itself: `(P, below, ·)` (the `AnyField` rows) keeps `(x, p, $, T)` in the layer of the fact;
  `(P, atAndBelow, ·)` drops the fact; `(P, exact, ·)` gives a `part` result;
* one accessor below, at `P.f` with `f ∉ E`: `(P.f, atAndBelow, ·)` gives `(x, p, [any-taint], E ∪ {f}, T)` in the
  layer of the fact; `(P.f, below, ·)` gives the same fact and also `(x, p.f, $, T)`, in the layer of the fact;
  `(P.f, exact, ·)` gives a `part` result;
* a cleaner whose path goes through an excluded accessor (`P.f` or deeper, with `f ∈ E`) cleans no location of the
  fact: the fact passes;
* every other cleaner strictly below `x.p` gives a `part` result.

These are primitive AP rows. A named field action uses the read and write above; apply the row to its temporary.

Each `part` result of this list goes to the demand layer with the tail `[any]` and no exclusion (ap.md §4.7, W8): this
loses precision (no fact shape has "every location except one", ap.md §11.1). Lean: `AnyTaintEx.cleanResX`, the vectors
`AnyTaintEx.Vec.clean_atAndBelow`, `clean_below`, `clean_exact`, `clean_excluded`, `below_new_fact` (the `below` row
gives the fact `(x, p.f, $, T)`, which the cleaner without exclusions does not give), and the program
`AnyTaintExCases.CL` (`CL.atAndBelow_result`, `CL.below_result`, `CL.exact_result`).

The non-mark atoms of a cleaner condition are evaluated statically (§4.2). Only an unconditional cleaner acts. A
cleaner with a mark literal in its condition does not act and raises no request.

| Point | Facts | Rules | Today | New |
|---|---|---|---|---|
| call, before the callee processing (§4.5 step 5.1) | each bound fact | every cleaner rule of the method that the call names | JVM yes; Go no | JVM and Go (D7) |
| call, unresolved callee | the same cleaned fact feeds the default identity and the pass rules | the same | JVM yes (the step comes before the resolution); Go no | JVM and Go (D7) |
| call, summary rewriter | the summary results and the unresolved results, in callee coordinates, before the binding back | the user-defined source rules and the unconditional user-defined cleaner rules of the call (below) | JVM, Go; also the conditional user-defined cleaner rules | the same, without the conditional cleaner rules (D23); it is an interpreter feature |
| backward run | the requirement after the reversed binding back (the rewriter) and at the callee start (the cleaners) | unconditional cleaners (§4.2); the rewriter | — | §4.9 |

USER-DEFINED RULE. A rule is user-defined if it comes from the rule set of the user and not from the library
configuration (today: its `info` implements `UserDefinedRuleInfo`, JVM, or `GoUserDefinedRuleInfo`, Go; for example a
rule that the Semgrep rule loader makes). It names a set of RELEVANT MARKS (`relevantTaintMarks`).

THE SUMMARY REWRITER acts at a call to a method that user-defined rules cover. Its purpose is RULE-GUIDED FLOW: the
rule overrides the real data flow of the callee for its marks, so the callee can neither add nor keep these marks at
the rule positions. RULE SELECTION (the non-mark atoms are evaluated statically, §4.2):

* every user-defined SOURCE rule of the call whose condition is not statically false;
* every user-defined CLEANER rule of the call whose condition is statically true (an unconditional cleaner).

A rule that a rule error rejected (§1.3) is never selected.

For each selected rule, each relevant mark `T` of the rule and each action position `P` of the rule (a source: the
positions of its `AssignMark` actions; a cleaner: the positions of its `RemoveMark` actions), the rewriter applies
`clean(P, exact, T)`, chained (§4.8). An `AnyField` ACTION POSITION OF A SOURCE (`AssignMarkOnAnyAccessor` on `P`, or
`AssignMark` on `PositionWithAccess(P, AnyField)`; Go `AnyAccessor`) gives `clean(P, atAndBelow, T)` instead (D34):
the source marks every location at or below `P` (a must, I14), so these locations are its rule positions. An
`AnyField` action position of a CLEANER (`RemoveMark(T, P.AnyField, …)`) takes the cleaner of its row in the table above,
`(P, below, T)`, as today (`JIRMethodCallRuleBasedSummaryRewriter.kt:105`). The
rewriter is an override by design, not a cleaner placement: D20 does not govern it. A conditional user-defined
cleaner does not act through the rewriter, as it does not act at the call (§4.2, D20). The rewriter applies to every summary result and to every unresolved result, also to a zero-premise summary
result and to the default identity (D23). A summary result is a caller edge, so a request of the rewriter goes to the
CALLER premise (§5.4). A cleaner on `Result` acts only through the rewriter (open question Q1).

### 5.3 ND conjunction

* The interpreter evaluates the conjunctive SOURCE rules at step 4 of the call order (§4.5): on every bound fact
  before the cleaners, and on the zero fact (§4.6); and at step 1 of the exit order (§4.7), on every fact at the exit
  (a conjunctive exit source, D31). A pass rule makes no conjunction (§4.2).
* An edge whose premise set has two or more members is an ND edge and a TAINT edge (ap.md §4.6, §7.2): no member is
  the zero fact, every member has a concrete mark, and the conclusion has a concrete mark.
* Every literal names its mark, so it has a concrete mark (ap.md S9). Its tail is `$` (`ContainsMark`) or `[any]`
  (`ContainsMarkOnAnyField`) (§4.2). ap.md §4.6 sets the layer of the result: normal if every input is normal and
  covered by its literal. A normal `[any-taint]` input that only overlaps its literal also gives a normal result:
  every location of it carries the mark, so the literal holds (ap.md §4.6; Lean `AnyTaintND.conjLayerT`, `lit_loc`).
  The overlap reads the exclusion of the input: an input whose exclusion removes every common location does not
  overlap the literal, so it stores nothing (ap.md §4.6). A normal result is exact against the path-insensitive
  support semantics, under ap.md S7, S9, S10 and S13 (ap.md §4.6; `NDExact.nd_edge_exact`, the valid form
  `nd_edge_exact_valid`; with `[any-taint]` inputs: ap.md §10.11. The conjunction model `AnyTaintND.DNzT` has no W6T
  and no exclusion: these two parts are argued, ap.md §11.2). The target of a conjunctive micro edge has a concrete
  mark and no `*` tail (ap.md S10, W7), and it is not on the zero base (I11 (d)). Its tail is `$`, or `[any-taint]` for
  an `AnyField` source target (I14); it is never `[any]`, because a pass rule makes no conjunction (§4.2).
* A fact that overlaps a literal and passes its mark gate is a contribution at its canonical leaf in a bound
  occurrence and statement. `RulePredicateStore` forwards OR proofs and streams compatible AND combinations.
  Repeated canonical leaves must bind the same premise/layer/checked-fact-group header; `A AND A` cannot combine
  two different A premises. A shared OR used by separate AND operands can select different branches. Keep OR scope
  and compatible leaf bindings; do not mix disjoint OR proofs as if they were one conjunction.
* Compute unary and multi eligibility from the constant-folded predicate, not arrival order. Unary keys propagate
  by OR union and AND intersection. Multi keys follow distinct-leaf co-occurrence through an AND ancestor.
  A leaf can belong to both. Apply each unary source use immediately through the ordinary AP transfer with the
  original facts, path suffixes, exclusions, the static-profile transfer rule (§0.3) and result filters. Multi uses apply MARK
  checks and conjunctive target construction. A one-leaf root proof does not emit a second fixed target.
* Source contributions need only premise and effective layer. Sink contributions also keep the checked facts.
  Use shared immutable proof records and persistent bindings, with separate growing-fact versions. Equal structural
  proofs must still emit required fact growth. Root deduplication must preserve occurrence and proof/support scope.
  Do not retain compiled predicate topology in actor state and do not impose a support cap.
* The result has the union of the premise sets of the inputs without the zero fact, or `{zero}` if every input has
  `{zero}` (ap.md §4.6). The number of the members names the edge: `{zero}` a zero-to-fact edge, one member a
  fact-to-fact edge, two or more an ND edge. The field limit applies to the result.
* A literal on a `*`-mark fact raises a request (run 1), not an assumption.
* A sink whose condition has positive literals on several positions is a CONJUNCTIVE SINK. Each literal is a sink
  pattern. Bound predicates use RulePredicateStore with scoped proofs; the manual literal adapter uses the
  conjunction store (ap.md §4.9, §8.9). The interpreter
  evaluates it at the same points as the other sinks (§4.5 step 3, §4.6, §4.7). Each literal raises its own request in
  run 1 (§5.4). At the normal exit, the part of an `S` item on which a literal holds is the stored input of that
  literal, and the global-state rule drops it from the summary edge if the premise of the item is the zero fact
  (§4.7 step 3).
* A callee summary with several premises (an ND summary) applies at step 5 of the call order.
  ap.md §4.6 and §8.9 define how the caller finds matching N2 subscriptions for every member at the same caller/call
  scope (event E6 of ap.md §5.3). The full joint caller premise and member layers survive return application.
* Sink inputs are pooled within the full method key, rule occurrence and statement; they need no CallerRef dimension
  in the emitter. A sink candidate can therefore combine members supplied by different callers. Preserve ordered
  literal premises/layers and compatible NNF proof choices. A normal layer or pooled availability alone does not
  confirm it. Without a valid support query or concrete validation, `confirmed=false` and a new report entry is
  DEMAND (analyzer-core.md §7.5). Existing source-hit/seed identities are separate; a witness has no source-route map.
  The current JVM `Simple` output is not a trace certificate. A full trace engine is deferred. Optional exact N2
  confirmation must run after join and before subscription release, without a second global supplier collection.
  Keep sticky earlier CONFIRMED entries, hand-off and termination policy; an unverified report need not force
  indefinite refinement. Removing old raw Support can add DEMAND sink seeds and change B2/F3 inputs. Compare
  local AP behavior at fixed run inputs; compare whole iterations with the same candidate policy, not with the old
  support-enabled frontiers. See §4.1 for zero-GEN derived candidates and the separate trigger-validation obligation.
* Sinks and the zero-to-zero step never make an ND edge (a conjunctive sink makes a vulnerability, not an edge). A
  conjunctive exit source makes one, as a conjunctive source at a call does (§4.7 step 1, D31).
* The backward run reverses a conjunctive edge into one plain edge per literal (§4.9). An `[any]` literal gives a
  requirement with the tail `[any]`, in the demand layer (W6; the backward run has no `[any-taint]`, I14). If the edge
  has two or more positive literals (a conjunctive source at a call or at an exit), every result of these plain edges
  is in the demand layer, also a `$` result: a requirement that reaches one literal is not a converse flow of the
  conjunction (§4.9 STATEMENTS; ap.md §9.2). So no backward summary through it is a record, and the next
  forward run analyses the callee again from the demand of each literal: the conjunction needs all its members there
  (argued, ap.md §11.2).

### 5.4 Requests

Use F78 [run1-requests.md](run1-requests.md), not the historical scalar Request/Position outputs. Validate K6
first. Local interest collection and upstream propagation require the actual edge/input's normal abstract InitialAp
origin. The original caller premise supplies the destination slot. A concrete current fact can answer another
method's request but never propagates one. The special S-root component evaluates no rules and requests nothing.

| Producer | Interest on the original normal slot |
|---|---|
| Source/sink/conjunction positive mark literal | its required concrete mark; keep full NNF condition/proof separately |
| Effective concrete microedge input gate, including pass/native forms | its concrete input mark |
| Same-base selected-mark cleaner or generated rewriter | selected T before any path/reach test, including disjoint/already-excluded input (F75) |
| Generic abstract target `*∖X`, including a legal hand-built compiled edge | collect each target-excluded T at the actual normal abstract origin; duplicates are permitted |
| Unconditional zero generation or all-mark cleaner | no mark request |
| Special S-root summary seed | no rule evaluation and no request |

The generic excluded-target side effect is an explicit F78 conformance change. Do not reject a legal target or
assume such targets absent merely because the measured MarkGate has no creator callback. Static extraction also
collects these marks/scopes. Inherited summary exclusions retain the prior creator/request route across calls;
no extra runtime exclusion-provenance store is introduced.

Ignore incoming field E and FLOW mark exclusions in request matching/propagation. Adding an excluded T has already
collected that same-origin request, so union removes the duplicate. This does not let T through a cleaned FLOW,
make a literal true, or invent an answer. Ordinary rule effects, summary matching and cleaners keep AP exclusions.
Concrete answers still require actual TAINT marks and keep the exact/chain/static/class distinctions of F78 §2.

Every producer first filters effective authoritative R = shared B union dynamic D, then merges remaining
contributions into owner-only empty-base temporary P. Producers never read P for membership. Frozen B is present
even in a stale dynamic read, so P/D remain disjoint from B. There is no copied B seed table or initial wave.
Route another method's batch to its owner before P write, including same-unit replay effects. At method fact-
queue-empty execute `summaryStorage.commit(temporary)` then `temporary = new RequestStorage()`. Commit synchronously
reads P and queues immutable fresh notifications outside its lock, without retaining P or reentering actors.
Work remains counted until replacement and all required deliveries are secured. Request-only methods schedule Work
without synthetic facts. No unit barrier, extra mailbox/latch, quotas, rescan or scheduler quantum is selected.

New TAINT input reads full R at the emitter. Fresh dynamic request batches visit existing caller N2 TAINT/FLOW;
fresh FLOW input/growth reads full R. Bind B before any subscription/input, register/admit original N2 input
before LinkIn, and commit before notification. Lazy wide activation follows this dynamic arm; a B row on C.f
does not discharge a changed C.g/argument origin. Queue all answers
and propagated sets before they can mutate a visited directory. These two arrival arms replace the callee deferred-
answer memo and N2 fresh-input TAINT request replay. One answer does not retire R or another caller's obligation.

Restricted runs have no requests/R/P. A concrete-mark gate on FLOW gives no concrete result; a selected-mark
cleaner retains its ordinary same-base excluded-mark result without a request. The static ownership/frame guard
still applies when reading persisted root-origin summaries; it is not request transport after run 1.

---

## 6. Deviations from today's code

The migration differences are in
[implementation-background.md](implementation-background.md). Current
construction and ordering rules are defined above; existing behavior does not
override them. The implementation proposal is [ap-impl.md](ap-impl.md), Part II.

## 7. Test plan

The interpreter vectors, existing-test mappings, and new regression cases are in
[validation-plan.md](validation-plan.md).

## 8. Open questions

The resolved questions are in [`ap-history.md`](ap-history.md) (§5 and the end of the file). Each open question
states the current rule; the implementation follows it until the user decides.

| # | Question | Current rule |
|---|---|---|
| Q1 | A cleaner action on `Result` that is not user-defined has no effect today: no bound fact has the base `ret`. Proposal: apply `clean` to the results on `ret` before the binding back, for every cleaner rule. A decision is necessary. | As today: it has no effect. The rewriter applies an unconditional user-defined cleaner on `Result` (§5.2). |
| Q2 | Go has no type filter. The placement points of §5.1 fit Go too. Add a Go type checker? | No Go type filter (§2.3). |
| Q3 | D13 and D14 drop two precision filters of the summary application. Restore them as a filter on the summary conclusion, or make `may` recurse through the declared field types (prefix-closed too, and it covers most of D13)? | No summary-side filter (D13, D14). |
| Q10 | The gaps G1–G4, G6, G9, G10, G12 and G13 (§0.1) lose real flows. Keep them as today, or close some of them (G3: check the sinks of the cleaner method on the uncleaned fact; G4: read the rules of the resolved override too)? | All gaps stay as today. |
