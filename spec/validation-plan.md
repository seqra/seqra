# Validation plan

This plan contains test data and implementation checks. It does not add analysis
rules. The normative rules are in [ap.md](ap.md), [interpreter.md](interpreter.md),
and [analyzer-core.md](analyzer-core.md). Historical tests must be run against the
closure version they name. Current demand tests use the structural selection
rule of ap.md §9.2; current record-view tests use F76. The build and axiom
audit are in [proof-status.md](proof-status.md).

## Implementation proposal review

Review [ap-impl.md](ap-impl.md) and [analyzer-impl.md](analyzer-impl.md) against
the current normative rules before implementation. Check these boundaries:

1. Emit from the current demand; retain the added mark for concrete patterns and
   use the shared FLOW form for abstract patterns. Reduce publications with the
   whole-premise and exit-mark tests. Retain raw records before reduction.
2. Select hand-off pieces by raw layer, premise count and backward zero only.
   Selection preserves the layer. Reader-result DEMAND does not add callee demand.
3. Apply record read views as transient patterns. Keep side marks, exclusions,
   exact-target suffix dropping and same-direction must-record demotion. Preserve pending results' exclusions
   through the returned rewriter and mappings; demote them immediately before the call field cut.
4. Keep F74 read/clean/write order and its cut points. F75 selects same-base
   abstract marks before the spatial test; only run 1 stores its request.
5. Join forward literal contributions; split backward conjunctions into independent
   DEMAND edges. On a matched TAINT literal, check the layer-only equivalence in
   `CurrentProposal.LiteralLayer`; no second coverage walk is needed.
6. Preserve joint support, action order, current-layer alias selection, reliable
   replay/delivery, immutable run inputs and barrier/report rules.

Use the local proofs listed in [proof-status.md](proof-status.md) for each
behavioral simplification. Historical closure results do not prove the current
iteration. Check local links, section references and public signatures after a
proposal rewrite. The tests below remain the implementation acceptance plan.

## AP tests

Bare rule references in this part name `ap.md`. Test item numbers and §13 refer
to this part.

## 13. Test plan (TDD)

Write the tests first. Each test names the spec item that it checks. The interpreter tests are below, under “Interpreter tests”.

1. Vector tests (`ApplyEdgeVectorsTest`): one test per `example` in `Cases.lean` and `RestrictedCases.lean`, on the
   concept implementation (§4.1 reference form) and on the tree implementation. These Lean files are the test data:
   each `example` gives the inputs and the expected result. Historical `lostCorr` vectors exercise generic
   algebra outside the reachable W2/S8 domain; they are not a below-case demotion rule. Check the gated
   `CurrentSimplifications` equivalence for legal facts, including equal requests. Some vectors (such as
   the §4.2 vector `a = b.f` on a normal-layer `(b, ., [any], {}, *)`) show the model result: an `[any]` result in
   the normal layer. The implementation applies W6T, so the test asserts the same fact in the layer of §4.1: in the
   demand layer for a may `[any]` target or an `[any]` input, and as a normal `[any-taint]` fact for a taint edge or a
   normal `.any` input with a CONCRETE mark; a `.any` input with the mark `*` (the §4.2 vector above) gives `[any]` in
   the demand layer (`Invariant.demand_of_any_ok`, §11.2). The vectors of the `[any-taint]` tail are test data too:
   the vectors of the exclusion `AnyTaintEx.Vec` (every vector: the keep edge, the reads, the case `above` with an
   edge exclusion, the cleaners, the cut, the emission, the start, the satisfaction, the summary, the restriction, the
   sink check, the case `below`), the `decide` vectors of `AnyTaintExCases.lean` (`AnyTaintExCases.X.two_results`,
   `AnyTaintExCases.R.reads`, `AnyTaintExCases.CL.clean_vectors`, `AnyTaintExCases.CUT.cut_ops`,
   `AnyTaintExCases.S.keep_forms`), the refined statement transfer of a source and of a pass rule
   (`AnyTaintExCases2.PassRule.source_vs_pass`), and the round-1 vectors without the exclusion: `AnyTaint.EmitVec`,
   `AnyTaint.Sanity` (a source result is a normal `[any-taint]` edge; the same micro edge as a pass rule is demand),
   `AnyTaintCases.PassRule.source_vs_pass`, `AnyTaintCases.Cut.cut_transfer`. The round-1 vector
   `AnyTaintCases.W.keep_demand` gives the demotion that the exclusion replaces: the test asserts
   `AnyTaintEx.Vec.setter_keep` instead.
2. Equivalence property tests (`EdgeTreeEquivalenceTest`): random path facts and micro edges; the tree result denotes
   the same pairs as the concept result, layer and mark exclusion included. The same for the tree restriction (§7.4).
   The tests of §3.4 (`covers`, `overlap`, `applicable`, `inside`) and `cleanPos` (§4.7) against the denotation of §3,
   on a bounded universe with a fresh accessor and a fresh mark.
3. Layer tests: a cut fact is in the demand layer; an `[any]` result is in the demand layer (W6); a demand-layer input
   gives a demand-layer output; a `*` conclusion is never in the demand layer; a `*` fact applied above a premise gives
   a demand-layer result. The `[any-taint]` tail (W8): a source with an `[any]` target gives a normal `[any-taint]`
   result, and the same edge as a pass rule gives `[any]` in the demand layer; every row of the two tables of §4.1 with
   an `[any-taint]/E` fact or target, also the case `above` of an `[any-taint]/E` fact (normal for a `$`, an
   `[any-taint]/Et` or a `*` target; the `*` target gives the edge exclusion; nothing if `E` does not admit the step).
   THE EXCLUSION ROWS (W8): the keep edge of a strong write gives `[any-taint]/(E ∪ {f})` in the layer of the input; a
   `*/E'` summary or record gives `[any-taint]/(E ∪ E')`; the case `below` keeps `E` at the new path end
   (`AnyTaintEx.Vec.below_keeps_loc`); a read through an excluded accessor gives nothing, through an admitted one
   `[any-taint]` with the Empty exclusion; a two-level write in one statement gives two normal results
   (`AnyTaintExCases.X`; a synthetic statement summary with the keep edges `x.* →_{f} x.*`, `x.f.* →_{g} x.f.*` on the
   AP, not a JVM statement: on the JVM `x.f.g = c` is `t = x.f; t.g = c`, the weak alias write of §11.1). THE
   DEMOTIONS to `[any]` with the Empty exclusion, every item of the list of §2.2: the cut (also with `L = 0`, program
   CUT, on the AP `limit`), every cleaner `part` row of §4.7 other than `atAndBelow` and `below` one accessor below the
   fact (the `exact` cleaner at and one accessor below the fact, a cleaner two accessors below), a may `[any]` target,
   a demand input (a demand-layer summary or record, a demand link, a demand conjunction input), the record demotion; a
   demand-layer `[any-taint]` result becomes `[any]` and drops its exclusion; the preconditions fail on an
   `[any-taint]` target or premise with the mark `*`; the `[any-taint]` rows of §4.2.
4. Mark tests: every row of the mark gate and of the result mark (§4.1 steps 4, 5); a `*∖X` summary conclusion stops an
   added fact with a mark in `X`; a sink for `T ∈ X` neither triggers nor requests; a request for `T ∈ X` does not
   climb; the preconditions of `concat` (§4.1) fail on a `*∖X` premise, on a concrete target mark under the premise
   mark `*`, on a `$` premise with the mark `*`, on a `$` premise with a `*` target, and on a `$` target under the
   premise mark `*`.
5. Cleaner tests: every row of the two tables of §4.7; the same-base abstract split before the path test (`*∖{T}`
   plus the run-1 request, then the concrete answer cleaned exactly), for every reach, including disjoint paths and
   T already in X; another-base pass; no request in either restricted direction; preservation of the path, tail,
   field exclusion and layer. Compare the literal per-path operation with the uniform-tree result and request set,
   including off-spine leaves. Check both root EXACT regressions in `ReviewBaseCleaner` at limits 1, 2, 3:
   concrete request, hand-off, source hit and normal real finding; the former false record rejects T after reversal.
   The all-marks cleaner; a cleaned fact through a field write past the field
   limit keeps its mark exclusion. The summary rewriter (§4.7): at a call it selects a conditional user-defined source
   and an unconditional user-defined cleaner, but not a conditional user-defined cleaner and not a rule that a rule
   error rejected; it acts on a zero-premise summary result and on the default identity of an unresolved callee; it
   cleans an `AnyField` position of a selected source with `atAndBelow` (`interpreter.md` D34), and an `AnyField`
   position of a selected cleaner with `below` (`interpreter.md` §5.2). The cleaner on an
   `[any-taint]/E` fact (§4.7): `atAndBelow` and `below` one accessor below the fact (normal, `E ∪ {f}`, and
   `(x, q.f, $, T)` for `below`), the `exact` cleaner there (demand `[any]`), a cleaner at an excluded accessor
   (`disjoint`, `AnyTaintEx.Vec.clean_excluded`), a cleaner two accessors below (demand `[any]`); the program
   `AnyTaintExCases.CL`.
6. Type filter tests: a fact on an accepted path passes, also with a `*` tail; a fact on a rejected path is dropped; the
   predicate is prefix-closed.
7. ND tests: a conjunction from two facts of different premises gives the union premise set; the last arriving fact
   completes it; one missing literal produces no result. Two NORMAL contributions give a NORMAL result; either
   DEMAND contribution gives DEMAND. A normal multi-premise publication remains NORMAL when selected for demand
   hand-off, which creates a pattern per member. The backward form has no conjunction and has one independent DEMAND
   branch per literal (`CurrentConjunction`). A fact that only overlaps its literal enters the store and gives a
   demand-layer result, unless it is a
   normal `[any-taint]` fact (§4.6); an `[any]`
   literal (`ContainsMarkOnAnyField`) accepts a fact below its position; a summary with several premises needs one
   link per premise; ND conclusions have no `*` tail; a vulnerability through a conjunction is confirmed only if its
   premise members are admissible and its premise set is supported jointly at one call statement (§4.9 condition 3);
   the negative test `NDConfirmed.CexSites`: two premises supplied at two different calls are not confirmed. A
   conjunction of a zero-premise fact and a fact of the premise `i` gives the premise set `{i}` (the zero fact is
   dropped), and two zero-premise facts give `{zero}`. Today's `ExampleTest.test nd rule` as an analysis test. The
   `[any-taint]` rule of §4.6: a normal `[any-taint]` input that overlaps a `$` literal gives a NORMAL result, and the
   vulnerability is confirmed (`AnyTaintND.Example.layer_new`, `confirmed`); an `[any]` input gives a demand
   result; a conjunctive source with an `[any]` target gives an `[any-taint]` result; an `[any-taint]/E` input whose
   admitted part does not overlap the literal (the literal at an excluded accessor) is not stored.
8. Merge tests: rules 1, 2 and 2'; rule 2 for two normal TAINT trees with `[any-taint]` leaves of equal content
   (the intersection of the exclusions); a union of exclusions or mark exclusions is never made. T5 (§7.2): in a demand
   tree an `[any]` leaf absorbs the leaves below it with its mark; in a normal TAINT tree an `[any-taint]` leaf absorbs
   no `$` leaf: the example of §7.2 T5 (`{x: [any-taint] (T), x.g: $ (T)}`, then `clean(x.f, exact, T)`) keeps
   `x.g.$ (T)` in the normal layer (so a sink on `x.g` can be CONFIRMED), and the store (§8.1) does not subsume it.
9. Request tests (run 1): the mark gate raises a request; a standing request is answered by a later added fact and by a
   second added fact; a standing request reaches a second caller edge of an EXISTING added fact (the program of §4.5);
   propagation to a caller with a `*`-mark call-site fact; the answer chain is the request chain; every request
   premise is a policy fact `(x, [], *, {}, *)` or a static position answer `(S, p, *, {}, *)`. A restricted run has
   NO request rule (F72, §4.5): on a fact with the mark `*` (a fact of a FLOW premise), the mark gate of a concrete
   premise mark gives nothing, a literal stores nothing, a sink has no effect and a cleaner of `T` gives `*∖{T}`, each
   with no request; the request store of a restricted run stays empty. (Before F72 the test asserted that a
   restricted run never makes a request.)
   Position request tests (§4.10): on an identity static `*` edge at the root `[]` or a class, a static read, a Go global
   read, the class keep edge of a write, a pass rule between static fields and a conditional exit source whose literal
   is on a static field raise the position request (cut to the static field) and give no fact; a sink on `S` raises the ordinary mark request; a deep read below a static field is
   the ordinary case `above`; the root keep edge adds
   `<C>` to the exclusion; an added fact at or below the position answers it, an added fact above it does not; the
   climb through a caller edge on `S`; the mark answer on a static premise (item 4: the added fact itself at or below,
   the chain answer above); the programs `Statics.CexAbove`, `CexWide`, `CexClean`, `CopyF2F`, `DeepSink`, as
   analysis tests, find their vulnerability in the normal layer, and `DeepSinkParam` in the demand layer.
10. Call and ownership tests: every event of the table of §5.3, in two orders; the callee restricts before it publishes;
    a caller reads the summaries of every premise its fact satisfies; a record applies when its premise covers the added
    fact, in every run after the run that made it; the return order of §5.3 step 5; the zero fact passes over a call and
    enters every resolved callee. The must-premise (§4.3): a summary of an `[any-taint]` premise applies only by
    `inside`; through a demand `[any]` link its result is demand; a must record applied by `applicable` only gives a
    demand result with the same fact and no exclusion (the program `AnyTaintExact.CexApp`); `inside` reads the
    exclusions of the added fact and of a must-premise (`AnyTaintEx.Vec.sat_vectors`); a summary with an
    `[any-taint]/E` conclusion gives its exclusion to the result (`AnyTaintEx.Vec.summary_ann`). Canonical FLOW
    matching (`*/{}` with mark `*`) equals annotated overlap: exact below, admitted must above, blocked must above,
    and must at the same path with `inside=false` but `applicable=true`. Keep general matching for a nonempty STAR
    exclusion and a concrete STAR reader with a different mark (`CurrentSimplifications.Flow.Certificate`).
11. Abstraction tests: run 1 emits the most abstract fact; every row of the three tables of §6.3 (every cell of the
    meet table: `AnyTaintEx.Vec.emit_at`, `emit_above_excluded`, `emit_above_exact`, `emit_below`; without the
    exclusion `AnyTaint.EmitVec`); the emitted fact is exactly `a ∩ D-c` with the mark of `a`; the same result for two
    insertion orders; no entry pattern has the `[any-taint]` tail (§9.2); a must and a may added fact at one path give
    two premise keys, and so do two must-premises with different exclusions (§7.1); a must-premise starts as itself,
    with its exclusion, in the normal layer (§6.5; `AnyTaintEx.Vec.start_must`); the backward run has no must-premise;
    a `*∖{T}` entry pattern emits nothing for an added fact with the mark `T` and emits for the mark `U`, with the
    premise inside the entry pattern with its mark (`Handoff.RVec.vEmit_starEx_T`, `vEmit_starEx_U`;
    `HandoffX.XVec.vM_emit`; the vectors of F71: since F72 no pattern has `*∖X`). THE EMISSION OF F72 (§6.3;
    local certificates in [proof-status.md](proof-status.md), full integration open §11.2): a `*` pattern `(x, .f, [any], *)` emits the FLOW form `(x, .f, *, {}, *)` for every added
    fact with a common location, and for the marks `T` and `U` it is ONE initial fact (SHARING); a `*/E` pattern
    emits itself with the mark `*`; a `*` pattern and an added fact with no common location emit nothing; a concrete
    pattern emits `a ∩ D-c` with the mark of `a`; an added fact with the mark `*` (or `*∖X`) under a concrete pattern
    emits NOTHING ("the added fact can't satisfy the demand"); the FLOW premise starts as the identity in the normal
    layer (§6.5); its summary applies by `inside` (the added fact above it) and by `applicable` (the added fact below
    it), and a summary of a concrete premise never applies to an abstract added fact (§4.3).
12. Restriction tests (the intersection, mark-aware, §6.4): every row of the table and every cell of THE MEET (the vectors
    `Handoff.RVec`: `v64_restrictI`, `vOverlap_restrictI`, `vNoExit`, every `row_*`; with the exclusions
    `HandoffX.XVec`: `v64_demand`, `v64_taint`, `v_taint_star`, `v_at_rows`, `v_above_rows`, `v_below_rows`,
    `v_overlap`, `v_inside_only_with_excl`); `[any]` against a `$` exit pattern gives `$`; a premise that only overlaps
    `D-c` gives no result, and a premise inside `D-c` (in its locations and its marks) keeps every pair whose exit
    location `D-p` covers with its mark; THE MARK TESTS (F71): the example of F71, `(x, ., $, T) → (ret, .f, $, T)`
    against `D-p = (ret, .f, $, U)`, gives no result, and with `D-p = (ret, .f, $, T)` it keeps the edge
    (`Handoff.RVec.vMark_user_restrictI`, `vMark_user_same`); a premise mark that `D-c` does not admit gives no result
    (`vMark_prem_restrictI`); the `*∖X` cells on both sides (`vMark_inStarEx_T`, `vMark_inStarEx_U`,
    `vMark_outStarEx_T`, `vMark_outStarEx_U`); the same with the exclusions (`HandoffX.XVec.vM_user`, `vM_prem`,
    `vM_inStarEx`, `vM_outStarEx`); the tree form drops the mark leaves that do not meet the mark of `D-p` (§7.4); the union over
    two demand patterns; no result without `D-p`; a summary with several premises keeps its whole premise set when
    every member lies inside a `D-c`; program 2 (§6.4); an `[any-taint]/E` conclusion above `D-p` gives the chain of
    `D-p` in its layer, with `E2` under a `*/E2` exit pattern and the Empty exclusion under `[any]`, if `E` admits the
    step down, and no result if not; below `D-p` it keeps `E`; at a `*/E2` exit pattern it gets `E ∪ E2`; the premise
    test reads the exclusion of a must-premise; a FLOW premise (F72) lies inside its own `*` pattern and inside no
    concrete pattern, and its `*` conclusion stays whole under its `*` exit pattern (§6.4); the two field-tail exceptions of the
    intersection (`Handoff.RVec.inter_exc_any`, `inter_exc_star`) give a pair outside `D-p`; the earlier rule above a `*/E2` exit pattern is not the intersection
    (`HandoffX.XVec.v_old_above_not_inter`). The vectors `AnyTaintEx.Vec.restrict_vectors` are for the earlier
    restriction: the test asserts the cells of `HandoffX.XVec` where they differ.
13. Iteration tests: programs 1 and 2 as analysis tests with the field limits 1 (run 1), 2 (run 2, backward) and 3 (run
    3), so the limit does not decrease (W3); the vulnerability is reported in every forward run. A worker loop with a
    sink that never returns (§9.2). The backward zero rules of §9.2: the zero fact enters every callee, a zero-premise
    backward summary returns to every caller without a restriction. THE HAND-OFF OF THE DEMAND EDGES (§9.2): it reads
    the publications. Keep every DEMAND-layer summary and every normal summary with several premises.
    Omit every other normal singleton summary from callee demand, including must-premise summaries.
    A zero-premise backward summary gives a seed-path pattern; the implicit zero pattern remains separate.
    Test both directions and every native singleton shape, including normal must→must and must→exact
    summaries whose transient backward views produce DEMAND. Their view results propagate in the caller;
    they do not create a separate demand for the summarized method.
    THE HAND-OFF NORMALIZATION (F72, §9.2): a summary conclusion with the mark `*∖X` gives a pattern
    with the mark `*`, in `D-c` and in `D-p`, in both hand-offs, and the summary keeps `*∖X` (it still stops an added
    fact with a mark in `X`); (F71, before: a `*∖X` entry pattern kept its mark and gave no premise to a requirement
    with a mark in `X`). In selected forward demand pieces, a must conclusion or
    premise becomes the pattern `[any]` with no exclusion. Program WRAP (§6.6, `HandoffCases.Wrap`): forward run 3
    analyses `wrap` only from the zero fact, backward run 2 never enters `wrap` with a non-zero fact (it crosses `wrap`
    by the reversed record), and forward run 3 still reports the vulnerability. Test the getter
    (`HandoffCases.Getter`) with current selection; historical closure witnesses alone do not prove it. THE SEEDS: the
    backward run seeds the DEMAND entries only, not a CONFIRMED one. THE TRIGGER OF AN END FACT (§9.2): in the program
    of §9.2 a requirement that reaches the reversed end-fact edge of the CONFIRMED sink `sinkCall` fires its sink
    seeds, `source()` is hit, and forward run 3 reports the DEMAND vulnerability `sinkAny(r)` again. THE REVERSAL OF A
    CONJUNCTION (§9.1, a regression test): in the program of §9.1 the backward summary of `M` through the conjunction
    is in the demand layer, no record, a demand edge of case 3, and forward run 3 does not report `sinkT(r2)`. THE
    SOURCE SEEDS: a source inside a callee that the backward run crosses by a record is not hit, and the record still
    gives its result (`HandoffSrc.SrcRec.found_unseeded`). THE EXCLUSION: a method key of which a forward run hands
    off no demand edge (for example: only normal singleton leaves), with no seed below, leaves the frontier, publishes nothing
    in the next forward run, and does not come back while this holds. THE NARROWING (the concrete design): no demand
    pattern has a `*` tail, and the demand patterns of forward run 5 with a `D-p` lie inside those of forward run 3, in
    the locations and the marks, with no exception (`HandoffNoStar.narrowing_canon_loc_exactM`); the zero demand and
    the patterns `(gb, none)` of the seed paths are not part of this check. Since F72 `*` patterns occur after run 1,
    so the test of the narrowing for the F72 runs waits for the model (PENDING, §11.2; item 18). THE STOP RULES:
    `STOP_RULE` after a run with no DEMAND entry; `NO_DEMAND_EDGE` after a run with no demand-layer edge, no demand-layer summary and no demand link, and not after
    a run whose only demand-layer object is a demand link (a call cleaner that demotes an `[any-taint]` bound fact,
    §11.2) (§6.6).
14. Store tests: index completeness against a list filter (records, demand patterns, requests, conjunctions,
    subscriptions). The conjunction store keeps two alternatives of one rule apart (§8.9), also with an equal literal
    pattern. The record store adds no demand-layer summary, no summary with two or more premises and no zero-premise
    backward summary (§8.7 R1). The method edge store (§8.1): the TAINT key holds the exclusion of the `[any-taint]`
    leaves, so two results with different exclusions are two trees; `[any-taint]/E` subsumes only the `[any-taint]` facts
    that it covers, and never a `$` fact. The
    added fact store keeps an `[any-taint]/E` added fact with its exclusion (§8.3). The vulnerability store (§8.10):
    one sink statement in two contexts is one key; two sink witnesses of two alternatives or of two method keys stay apart; after an incomplete forward run the report has
    the DEMAND entries of the latest complete forward run; after an incomplete run 1 the report has no entry and the
    output is empty. The output holds every entry of the report, the DEMAND entries too, each with a simple trace and
    the method key of a confirmed sink witness, else of the first sink witness (§8.10).
15. Reversal tests: keep generic micro-edge vectors separate from native record
    read views (R3, §9.1). F76's `P.$(U)→x.f.[any-taint]/E(T)` gives
    `x.f.*/E(T)→P.$(U)`. An admitted exact field chain matches by `applicable`,
    even when `inside` is false, and returns exact `P` in the input layer; an
    excluded chain gives nothing. Test distinct `U/T`, several paths, empty and
    nonempty E, and no copied input suffix. A must forward premise becomes
    may `[any]` with empty old exclusion; both must→must and must→exact views
    produce DEMAND, never normal backward records. Reject malformed native
    concrete-star/demand inputs. Never start, emit, or persist a view. The computed
    certificates and negative pair witnesses are in `CurrentMustReverse`.
    Generic reversed may and conjunction micro edges still give every result
    in the demand layer, including exact results. Generic reversed source edges
    keep the requirement layer and use `[any]` premises. No backward fact has
    `[any-taint]`. Every native normal singleton retains its raw record and gives
    no separate callee demand, including must-premise records. A must-premise
    view's DEMAND result is propagated in the caller. Select from the raw layer,
    premise count and backward-zero status before restriction; a narrowed publication
    cannot reclassify its raw summary (`CurrentSummarySelection`). Test the
    normal exact requirement and may-any demand requirement gates separately.
    A zero-premise cached source view gives zero without a synthetic source hit;
    the native forward record still replays with empty source seeds. A nonzero
    view returns the exact caller premise and can reach an actual caller source.
16. Analysis tests (the gate of the new analyzer): the existing `*AnalysisTest` suites, run with `cleanTest`;
    `DeepCleanSummaryAnalysisTest` and the cleaner suites for §4.7. A finding whose taint comes from an `[any]`-target
    source (a Spring DTO entry-point argument) is CONFIRMED: in run 1 when the sink reads the object in the method of
    the source (`AnyTaintExCases2.PassRule.source_confirmed`), after a callee whose FLOW summary keeps the whole object
    (program I, `AnyTaintExCases2.I.run1_confirmed`), and after a setter of another field (program S:
    `sink(dto.email)` is CONFIRMED and `sink(dto.name)` is NOT REPORTED, `AnyTaintExCases.S`; the same one call deeper,
    program SD; the reads of program R); in forward run 3 through a getter (program G,
    `AnyTaintExCases2.G.run3_confirmed`) or a sink in the callee (program C, `AnyTaintExCases2.C.run3_confirmed`). A
    strong write into the object does not demote it: only the written field is not reported (program S). A finding
    through a pass rule with an `AnyField` target stays a DEMAND entry
    (`AnyTaintExCases2.PassRule.pass_not_confirmed`), and so does a finding under the `exact` cleaner below the object
    (`AnyTaintExCases.CL.exact_result`) or over the field limit (§8.10; on the JVM with `L = 1` and a write two counted
    accessors deep). Programs X and CUT are AP-level tests (item 3), not JVM analysis tests: CUT has the field limit
    `L = 0`, which no run has (§4.4: run 1 needs `L ≥ 1`, and the limit does not decrease); X is a two-level write in
    one statement, and on the JVM `x.f.g = c` is `t = x.f; t.g = c`, the weak alias write (S2, `interpreter.md` A3, gap
    G7), so a JVM analysis test of X expects `sink(x.f.g)` CONFIRMED, the documented false positive of §11.1, and
    `sink(x.f.h)`, `sink(x.k)` CONFIRMED (`validation-plan.md` → Interpreter tests → §7.2 item 30). A weak update of the object (an alias
    write, the default identity of an unresolved callee) gives an expected CONFIRMED false positive (§11.1). A lost finding is a test whose message
    says that no vulnerability reached the sink; read the message, do not count failures.
17. Confirmation tests of the `[any-taint]` tail (§4.9): a normal `[any-taint]/E` sink edge is confirmed when the sink
    pattern meets an admitted location, and does not trigger on an excluded one (`AnyTaintEx.Vec.check_vectors`); the
    support link of an `[any-taint]/E` added fact accepts a `$` or a must-premise inside it (with the exclusions) with
    the same mark, also the emission `a ∩ D-c = a`, and rejects a premise with the mark `*`
    (`AnyTaintExact.CexSupMark`); a demand `[any]` link supports nothing; the program G is demand in run 1 and
    confirmed in run 3 (`AnyTaintExCases2.G.run1_not_confirmed`, `run3_confirmed`); the program C is confirmed in run 3
    through the must branch of `AnyTaintEx.SupLinkX` (`AnyTaintExCases2.C.run3_supported`); the program
    `AnyTaintExCases.B` confirms `sinkAny(e)` in run 1 and in run 3 and never reports `sink(d.name)`.
18. F72 abstract-mark tests. Local operation certificates are checked; full mode
    coverage remains open (§11.2). Keep tests red where a model counterexample
    requires a behavior decision. As analysis tests with the field limits 1, 2 and 3: (i) THE GETTER
    with two marks (historical trace in [proof-history.md](proof-history.md)): `get(p) { ret = p.name; }`, called with a fact of the mark `T` (the DTO fact)
    and a fact of the mark `U`; backward run 2 has ONE `*` requirement `(ret, ., *, {}, *)` in `get`; forward run 3
    has one record (or one FLOW premise `(p, .name, *, {}, *)`) for both marks, and both vulnerabilities are reported,
    the DTO one CONFIRMED; (ii) A MARK-CHANGING PASS RULE `T → U` inside a callee that a `*` pattern reaches: the `*`
    analysis gives nothing for it, and the vulnerability of `U` is found through the concrete pattern that the run-1
    request and answer made; (iii) A PARTIAL CLEANER of `T` under a `*` premise: the fact continues as `*∖{T}` with no
    request, and a flow of `T` through the part that the cleaner does not clean is found through a concrete pattern;
    (iv) the search for a counterexample to R6 (§6.6): a flow that needs a concrete mark in a callee that only `*`
    patterns reach after run 1. The AP-level tests: the hand-off normalization (§9.2); the emission rows of item 11;
    the FLOW form of an `[any]` pattern (`*/{}`) and of a `*/E` pattern (`*/E`); a check on the hand-off of every run
    that no abstract entry pattern has nonempty star exclusion (current §6.1
    construction guard): `(x, [], */{f}, *)` and `(x, [], */{g}, *)` satisfy
    neither test. Canonical abstract entries have `[any]`; local entry-tail
    preservation does not establish full mode coverage.

## Analyzer tests

Bare rule references in this part name `analyzer-core.md`. Test item numbers
and §13 refer to this part.

## 13. Test plan (TDD)

1. SCHEDULE FUZZING. A test runner picks the next event at random (seeded) from all channels and queues. It runs the
   direct calls as events too. For small programs (this plan's AP vectors and
   historical programs in [proof-history.md](proof-history.md)), compare
   the result of many seeds with a reference: the naive fixed point of the closure. Every seed must give the same
   edges, summaries and vulnerabilities.
2. PROTOCOL TESTS. A mock storage that breaks P1, P2, P3 or P4 loses a summary in the fixed schedule of the
   counterexample. The real storage does not.
3. THE `[any]` DELIVERY. In a restricted run (`inside`), a caller fact with `[any]` above the premise of a summary that
   the callee publishes AFTER the subscription, with 10 or more subscriptions: the caller gets the summary (P4). The
   same with an `[any-taint]` caller fact and the summary of its must-premise. With an `[any-taint]/E` caller fact, a
   premise below it through an accessor in `E` gets no summary, and a premise through another accessor gets it. A
   FLOW premise (F72, §5.3): a caller fact below the premise (`applicable`) and a caller fact above it (`inside`) both
   get the summary, by the replay and by the delivery; a summary of a concrete premise does not reach a `*` caller
   fact.
4. SEVERAL PREMISES. A summary `{j1, j2} → g`: premise 1 matched by a delivery, premise 2 by a replay, with the
   conclusion in two deltas: the caller applies both deltas (§5.4).
5. RECORDS. A forward record does not apply in its forward form in a backward run; its reversal applies. A backward
   record applies to a forward fact only through its reversal. A record applies by `inside` in a restricted run
   (§5.3).
6. COUNTER. A handler that sends after a delay: the run does not end before the send (Q2). A new `RunManager` made
   after an aborted one (test harness) analyzes every method (§6.3, §7.6).
7. HAND-OFF. Run programs 1 and 2 of `ap.md` §6.3, §6.4 with structural summary selection.
   Check the actual published pieces and normalized patterns in both directions: DEMAND-layer pieces and
   normal multi-premise pieces give patterns; other normal singleton pieces do not. Backward zero-premise
   pieces retain their seed-path patterns. The mark-aware restriction must reject a conclusion mark outside
   `D-p`, and every retained pattern must come from a stored publication. Historical `Backward` and
   `HandoffRCases` closure results are comparison data, not proofs of the current hand-off. Record-view
   application is checked separately from callee demand selection.
8. MODES. A restricted run (forward and backward) raises no request (F72 R4): on a `*` fact the mark gate of a micro
   edge with a concrete premise mark gives no result, the sink check gives no witness, and a partial cleaner of `T`
   gives the fact `*∖{T}`; the run has no request store, and its request counter is 0. Run 1 raises the requests of
   the same program. A backward run has no sink check. The zero fact enters every
   callee in the backward run. The reversed plan of a JVM call gives the steps of `interpreter.md` §4.9 in its order
   (the table of §4.5), with the reversed source results and the seeds at the rule point `BOUND`. The reversed alias
   edges apply to every requirement; the forward ones only to the results that AC3 and AC4 select.
9. STOP RULES (§7.1). A forward run whose vulnerabilities all have a confirmed sink edge stops the iteration
   (`STOP_RULE`), also when they have demand-layer sink edges too. So does a forward run whose only unconfirmed
   vulnerability an EARLIER run confirmed: it is not a DEMAND vulnerability, it gives no seed, and the driver stops
   with `STOP_RULE`. A forward run with a DEMAND vulnerability and no demand-layer object (a conjunctive sink whose
   literals come from two different call statements, so the joint support fails) stops with `NO_DEMAND_EDGE`; the
   report keeps the DEMAND entry. A demand-layer summary delta alone (a cut of the exit rules) and a demand link alone
   each count as a demand-layer object, so the driver goes on (program DLINK, `analyzer-impl.md` §9.1 row 21):
   `root(): dto = srcAny(); c(dto)`, `c(x): y = x.q.r; m(y)` with the primitive root call cleaner `clean(T, arg0, exact)` at
   `m(y)`, `m(p): sink(p.g.h); throw`, and the field limits 1 to 5: forward run 3 has only normal edges, but the cleaner demotes the bound fact to
   `(p, ., [any], T)` on a DEMAND link, so it does not stop with `NO_DEMAND_EDGE`, and forward run 5 CONFIRMS the
   vulnerability (§7.1). `CurrentDemandLink.Certificate.check` computes the local concrete fact trace; it is not
   a full iteration theorem. The seeds of the hand-off to a backward run come from the witnesses of the DEMAND
   vulnerabilities: a vulnerability that an earlier run confirmed and that the latest run reports in the demand layer
   gives no seed (§7.3).
10. SOURCE SEEDS. In forward run 3, a source that backward run 2 did not reach does not fire; a source on the witness
    outside a recorded call fires, and run 3 reports the vulnerability. A source inside a callee bypassed by its record is not hit
    and does not fire, and the record of the callee gives its result (`x = mk(); sink(x)`, `mk(): ret = source()`:
    run 3 reports the vulnerability in the normal layer through the record, `HandoffSrc.SrcRec.found_unseeded`). A
    requirement that reaches a source records exactly one hit for that (method key, statement, source edge) (§4.7). A source at a call, at a method entry, at a
    method exit and at a read each records its hit. Two sources of one statement that give the same zero result both
    record a hit.
11. REGRESSION. The existing analysis tests, through phase 3 (`bidirectional-task.md` phase 4).
12. END OF A RUN AND OF THE ANALYSIS. After the memory guard ends a run (`OOM`), a handler that throws
    `Cancellation.Cancelled` stops its runner, and the run ends `OOM` before its timeout. A `RunManager.fail` after the
    quiescence leaves the run COMPLETE (the first end wins). A handler that throws ends the run `FAILED` before its
    timeout, and the other runners stop. A runner that does not stop at the join makes the run `FAILED`. An exception at
    the barrier returns the report of the earlier runs with `AnalysisEnd.reason == ABNORMAL` and the status FAILED; so
    does an `Error` at the barrier (a stub `RecordStore` whose `persist` throws `OutOfMemoryError`): the report so far,
    `ABNORMAL`, the status `FAILED` (not `OOM`). A hit of the barrier memory guard (a `Cancellation.Cancelled` at a
    barrier checkpoint, or a cancel after the last one) gives the report so far, `ABNORMAL` and `OOM`. A throw in
    `RunManager(...)`, in `run(...)` on the caller thread, or a policy with `fieldLimit(1) < 1` gives `FAILED` and the
    report so far (empty for run 1), and no runner is alive after the return. A stop by the two stop rules and by the
    policy gives `STOP_RULE`, `NO_DEMAND_EDGE` and `POLICY` (§6.3, §7.1). Each complete run, also the last one, logs
    one frontier, and `continueAfter` gets the frontiers of every complete run in run order (§7.8).
13. REPORT. Run 1 complete (one CONFIRMED and one DEMAND vulnerability), run 2 complete, run 3 incomplete: the report
    is that of run 1, and run 3 refutes nothing. `continueAfter` is never asked after a backward run (§7.1, §7.5).
    Run 1 incomplete (TIMEOUT): the report has no entry, `end = (TIMEOUT, 1, FORWARD, ABNORMAL)`, and the output is
    empty (§7.5 AN INCOMPLETE RUN 1).
14. VULNERABILITY KEY AND WITNESSES. One sink statement that two contexts reach is one vulnerability; a confirmed
    witness in one context makes it CONFIRMED. Two alternatives of one sink rule that trigger on two bases with the
    same premise set give two witnesses, and the store does not fail (§4.7).
15. WORKLIST. The `unchanged` items come before the `normal` items; a loop of statements that do not touch a base
    ends; the set stays across two `Work` events. A fact on a dead local still reaches a later sink (no liveness check,
    §4.3).
16. EMPTY METHODS. A call whose only callee is a native method is an unresolved call: the pass rules and the default
    identity apply. A call with a native callee and a callee with a body links only to the second one (§4.4).
17. END FACTS AND ALIASES. A sink with an end-fact action that triggers on a demand-layer sink edge gives a
    demand-layer end fact on `{zero}`. A demand-layer summary result that is equal to its start fact goes to the
    aliases; a normal one does not (§4.5).
18. GLOBAL-STATE RULE. A conjunctive exit sink `ContainsMark(S.<C>, STATE) ∧ ContainsMark(Result, T)`: at an exit
    where only the `S` literal holds on a zero-premise item, the `S` part leaves the summary edge and is stored as the
    literal input; a later item with `Result` tainted completes the combination with it (§4.7). A CALLER-SET state
    (`root(){ acquire(); release(); after(); }`, where `acquire` sets the state, `release` has the exit sink and `after`
    a sink on the same position): the exit sink of `release` evaluates the state, the state is not dropped, and the
    caller still sees it after the call (the sink of `after` reports) (§4.7; `ap-history.md` F68).
19. PRESCAN RELEASE. After the prescan info is gathered and the prescan state is released (§9 PRESCAN MEMORY), weak
    references to the prescan AP manager, to one prescan runner and to one prescan method context are cleared after a
    GC (poll a few times).
20. PHASE-3 OUTPUT AND STATUS. The output holds every entry of the report, CONFIRMED and DEMAND (a vulnerability whose
    taint passes only through a pass rule with an `AnyField` target, a may `[any]`, `ap.md` W6, is output with the
    state DEMAND; a vulnerability from a source with an `[any]` target can be CONFIRMED, items 22, 23), each with the
    simple trace; the method key is that of a confirmed witness, else of the first witness; the log has the count per
    state. The status
    maps `COMPLETE` to `OK`, `TIMEOUT` to `TIMEOUT`, `OOM` to `OOM` and `FAILED` to `EXCEPTION` (one `Report` per
    status). A throw in the setup of the analysis gives an empty output and `EXCEPTION` (§9).
21. CONJUNCTIVE EXIT SOURCE. An exit source `AssignMark(U, Result) if ContainsMark(Argument(0), A) ∧
    ContainsMark(Argument(1), B)`, with the exit items `(arg(0), $, A)` under the premise `i0` and `(arg(1), $, B)` under
    `i1`: the full combination gives the ND summary `{i0, i1} → ret.$ (U)`, and the caller applies it by E6; each
    literal input stays in the conjunction store; the exit items stay in the summary (§4.4).
22. THE `[any]`-TARGET SOURCE IS CONFIRMED (analysis test; program G, a Spring DTO through a getter; the Lean results
    are those of `AnyTaintExCases2`: run 1 in the spec closure `AnyTaintEx.D6X`, run 3 in `AnyTaintEx.DRXs` with the
    earlier restriction `restrictX` and the earlier hand-off, the record of the earlier design). `root(): dto = srcAny(); x = get(dto);
    sinkAny(x)`, `get(p): return p.f`, where `srcAny` is a source with an `[any]` target and `sinkAny` a
    `ContainsMarkOnAnyField` sink. Run 1 reports the vulnerability as DEMAND (the getter summary is the case `above`;
    `AnyTaintExCases2.G.run1_flow_above`, `G.run1_not_confirmed`). Backward run 2 hands off the demand
    `(D-c = (p, .f, [any], T), D-p = (ret, ., [any], T))` of `get` (the backward run has no `[any-taint]`, §7.4; it
    reads the refined run 1 with the exclusions dropped: `G.HX_exact`, `G.handoffX_get`). In run 3 the added fact
    `(p, ., [any-taint], {}, T)` of `get` emits the must-premise `(p, .f, [any-taint], {}, T)` (`G.run3_must`,
    `G.run3_must_supported`), the sink edge in `root` is normal (`G.run3_sink_normal`), and run 3 CONFIRMS the
    vulnerability (`G.run3_confirmed_handoff`); the report has it as CONFIRMED and the iteration stops (§7.1).
    Program C (the sink in the callee: `use(o): sinkAny(o.f)`) is CONFIRMED in run 3 too, with the must-premise
    supported through its link (`AnyTaintExCases2.C.run3_supported`, `C.run3_confirmed_handoff`). Program I (`get`
    with `return p`) is CONFIRMED in run 1, with no DEMAND entry, so the iteration stops after run 1
    (`AnyTaintExCases2.I.run1_flow`, `I.run1_confirmed`, `I.run1_no_demand`).
    SINCE F72 (PENDING the model; the Lean results above are of the concrete design): the run-1 pattern of `get` is
    `(D-c = (ret, ., [any], *), D-p = (p, ., *, {}, *))`, a `*` pattern, so backward run 2 weakens the requirement
    `(ret, ., [any], T)` to the FLOW premise `(ret, ., *, {}, *)` (§4.4). Its backward summary
    `(ret, ., *, {}, *) → (p, .f, *, {}, *)` is a normal singleton, so `get` hands off no demand edge.
    Forward run 3 crosses `get` by the reversed record, which applies to the added fact `(p, ., [any-taint], {}, T)`
    by `inside` with a normal result (the case `above` of an `[any-taint]` fact, `ap.md` §4.3), and the expected
    result is the same: run 3 CONFIRMS the vulnerability. The test asserts the hand-off and the result of run 3.
23. THE SETTER KEEPS `[any-taint]` WITH AN EXCLUSION (analysis test; program S of `AnyTaintExCases`, a Spring DTO
    through a setter). `root(): dto = srcAny(); dto.setName(c); sink(dto.name); sink(dto.email)`,
    `setName(n): this.name = n`, `c` clean. The run-1 summary `(this, ., *, {}, *) → (this, ., */{name}, *)` of
    `setName` on the added fact `(this, ., [any-taint], T)` gives `(this, ., [any-taint], {name}, T)` in the normal
    layer, and `dto` is `(dto, ., [any-taint], {name}, T)`, normal (`AnyTaintExCases.S.record_app`, `run1_dto_ann`).
    Run 1 reports no vulnerability for `sink(dto.name)`, in no layer (`S.run1_name_not_reported`; it is not real,
    `S.name_not_real`), CONFIRMS `sink(dto.email)` (`S.run1_email_confirmed`) and has no DEMAND entry
    (`S.run1_no_demand`), so the driver stops after run 1 (`STOP_RULE`). The same through a deeper setter
    (`AnyTaintExCases.SD.run1_email_confirmed`, `SD.same_result`). Program B also has no DEMAND entry in run 1
    (`AnyTaintExCases.B.inv1`), so the driver never starts its run 3: its run-3 results are a test of one restricted
    run with the broad demand `(D-c = (this, ., [any], T), D-p = (this, ., [any], T))` of `setName` given by hand and
    the records of run 1 (`AnyTaintExCases.B.run3_must`, `B.run3_anyE_confirmed`, `B.run3_name_not_reported`).
24. SOURCE AGAINST PASS RULE (program PassRule; the results in the spec closure `AnyTaintEx.D6X` are those of
    `AnyTaintExCases2.PassRule`). The same micro edge `P.$ (T) → Q.[any] (T)` as a source (target `[any-taint]`)
    gives a normal edge, and run 1 confirms the vulnerability; as a pass rule (target `[any]`) it gives a demand edge,
    and the vulnerability stays DEMAND (`AnyTaintExCases2.PassRule.source_vs_pass`, `source_confirmed`,
    `pass_demand`, `pass_not_confirmed`). In the backward run the reversal `Q.[any] (T) → P.$ (T)` of the pass rule
    gives the requirement `(P, ., $, T)` in the demand layer (its forward target is `[any]`, §4.3), so the backward
    summary through it is not a record, and no later forward run confirms the vulnerability through the pass rule. The
    core reads only `MicroEdge.forward` for this (no rule-kind flag).
25. TWO PREMISE KEYS AND THE MUST RECORD. Keep separate keys for must and may
    premises at one path, and for must-premises with different exclusions.
    Same-direction must-record application still demotes the `applicable`-only
    part, after the returned rewriter, binding back and aliases, immediately before
    the call field cut. Check separate inside and pending batches: pending results
    retain E until demotion, take aliases even if the native leaf was an identity,
    and end with DEMAND and Empty E. Count one record crossing if either initial
    batch has a result, including a result that the rewriter later removes.
    `FormsReference` and `NaiveClosure` must use the same order. Check ordinary
    inside results and transient views with no pending demotion. Backward reads
    now use F76 transient views: a must-premise record gives
    a may-any DEMAND result. For the zero-premise factory summary with leaves
    `(ret, [], [any-taint], {name,k}, T)` and `(ret, [k], $, U)`, both leaves
    have views. The must leaf accepts `ret.email.$(T)` and rejects
    `ret.name.$(T)`; its result is zero in the requirement's layer. The exact leaf
    uses generic reversal. The original forward summary and its exclusion stay
    unchanged. No view is persisted; no backward fact has `[any-taint]`.

26. THE SUPPORT NEEDS THE SAME MARK. A must-premise with the mark `*` inside an `[any-taint]` added fact is not
    supported (§7.5; `AnyTaintExact.CexSupMark.cex_sup_mark`). A `$` member below an `[any-taint]/E` added fact through
    an accessor in `E` is not supported; through another accessor it is.
27. VECTORS. The emission table (§4.4; `AnyTaint.EmitVec`; with the exclusion `AnyTaintEx.Vec.emit_at`,
    `emit_above_excluded`, `emit_above_exact`), the A2 rows (`AnyTaintEx.Vec.setter_keep`, `read_excluded`,
    `read_admitted`, `above_star_excl`, `below_keeps`, `below_new_fact`, `cut_drops`), the source result as a normal
    `[any-taint]` edge in run 1 and in a restricted run and the pass-rule result as a demand edge (`AnyTaint.Sanity`),
    the encodings with the exclusion (`PipelineAnyTaintEx.SanityX.d6x_ann`, `drx_ann`), and the conjunction of an
    `[any-taint]` input that its literal does not cover: a normal result, confirmed (`AnyTaintND.Example`).
28. THE TWO-LEVEL WRITE (program X of `AnyTaintExCases`; an AP-level test: the write `x.f.g = c` is ONE statement, a
    synthetic statement summary on the AP with the keep edges `x.* →_{f} x.*` and `x.f.* →_{g} x.f.*`).
    `x = srcAny(); x.f.g = c; sink(x.f.g); sink(x.f.h); sink(x.k)`: the write gives exactly
    `(x, ., [any-taint], {f}, T)` and `(x, .f, [any-taint], {g}, T)`, both normal (`AnyTaintExCases.X.two_results`).
    `sink(x.f.g)` is not reported (`X.fg_not_reported`); run 1 CONFIRMS `sink(x.f.h)` and `sink(x.k)`
    (`X.fh_confirmed`, `X.k_confirmed`). On the JVM a two-level write goes through a local (`t = x.f; t.g = c`): the
    write into `x` is the weak alias write (`interpreter.md` G7), so `x` keeps `(x, ., [any-taint], {}, T)`. A JVM
    analysis test of this program expects `sink(x.f.g)` CONFIRMED, the documented false positive of the alias gap
    (§11 THE ALIAS GAP), and `sink(x.f.h)` and `sink(x.k)` CONFIRMED.
29. THE READS (program R of `AnyTaintExCases`). After the setter of item 23, `y = dto.name` gives nothing and
    `z = dto.email` gives `(z, ., [any-taint], {}, T)`, normal (`AnyTaintExCases.R.reads`). `sinkAny(y)` is not
    reported (`R.y_not_reported`); run 1 CONFIRMS `sinkAny(z)` (`R.z_confirmed`).
30. THE CLEANERS (program CL of `AnyTaintExCases`). `x = srcAny(); clean(x.f, reach); sink(x.f); sink(x.f.g);
    sink(x.k)`: `atAndBelow` gives `(x, ., [any-taint], {f}, T)`, normal, so only `sink(x.k)` is reported, and it is
    CONFIRMED (`AnyTaintExCases.CL.atAndBelow_result`); `below` gives it and
    `(x, .f, $, T)`, both normal, so `sink(x.f)` is CONFIRMED (`CL.below_result`); `exact` gives `(x, ., [any], T)` in
    the demand layer, so every sink is a DEMAND entry (`CL.exact_result`).
31. THE CUT (program CUT of `AnyTaintExCases`; an AP-level test: the AP field limit with `L = 0` on the results of
    item 28; no run has `L = 0`, since run 1 needs `L >= 1`, §7.1). Program X with the field limit 0: the cut gives
    `(x, ., [any], T)` in the demand layer with no exclusion (`AnyTaintExCases.CUT.run1_cut`), and `sink(x.f.h)` is a
    DEMAND entry (`CUT.cut_reports`). A JVM analysis test of the cut runs with `L = 1` and a fact deeper than the
    limit: the source `dto.f.g = srcAny()` gives `(dto, .f, [any], T)` in the demand layer with no exclusion
    (`AnyTaintCases.Cut.cut_transfer`, a transfer with no exclusion), so a sink below `dto.f` is a DEMAND entry.

The items 32 to 42 test current structural summary selection, stop rules and the frontier (§4.3,
§4.5, §7.3 to §7.8), and mark-aware restriction and emission (§4.4, §4.6). Named Lean closure witnesses from
`ap-history.md` F70/F71 remain historical comparison data. The
items 22 and 23 cite Lean results of the earlier hand-off (`AnyTaintExCases2.G.handoffX_get`,
`AnyTaintExCases.B.run3_must`, with `restrictX`): their input is a demand-layer getter or a demand given by
hand. The local intersections remain test data; current hand-off and iteration results require structural selection.
Historical `HandoffCases`/`HandoffMain` closure witnesses below do not by themselves prove current selection.

32. WRAP (§7.8; `HandoffCases.Wrap`, the field limits 1, 2, 3). `root(): x.a.b.c = source(); r = wrap(x);
    sink(r.f.a.b.c)`, `wrap(x): z = new Z(); z.f = x; return z`. Run 1: `wrap` has a normal singleton summary, and the
    vulnerability is DEMAND. The frontier of run 1 has no demand edge of `wrap`. Backward run 2 has only the zero fact
    in `wrap`: it crosses `wrap` by the reversed record. Forward run 3 analyses `wrap` only from the zero fact (`wrap`
    is in `zeroOnly`, not in `analysed`), applies the record of `wrap` in the root (one record crossing), cuts in the
    root and reports the vulnerability (`Wrap.wrap_old_vs_new`). A test hand-off that reads every summary edge before
    the restriction (the earlier hand-off) makes runs 2 and 3 analyse `wrap` from a non-zero fact, with a demand-layer
    edge in `wrap` in run 3 (`Wrap.old_b2_wrap_init`, `old_f3_wrap_cut`).
33. NORMAL SINGLETON SUMMARIES GIVE NO CALLEE DEMAND. A callee whose run-1 summaries are all normal singletons (the
    `wrap` of item 32, a setter `set(v): this.f = v`, an identity `id(p): return p`) has no non-zero initial fact in
    the backward run when no independent seed reaches it. Historical local witnesses:
    `HandoffCases.Wrap.bn_wrap_zero_only`, `bn_wrap_edges_zero`. The requirement crosses it by the
    reversed record (`bn_cross`, by `applicable`) and still reaches the source in the caller, so the next forward run
    fires that source and reports the vulnerability (`fn_found`).
34. THE GETTER IS CROSSED BY THE REVERSED BACKWARD RECORD (§7.4; `HandoffCases.Getter`). `root(): x.f.a = source();
    r = get(x); sink(r.a)`, `get(x): ret = x.f`. The run-1 summary of `get` is in the demand layer, so it is a demand
    edge (`Getter.g1_exit_demand`). Backward run 2 enters `get` and gives the NORMAL backward summary
    `(ret, .a, $, T) → (arg, .f.a, $, T)`; it is a normal singleton, so it is not a callee demand edge
    (`demG_exact` is historical comparison data: `get` gets only the zero demand). Forward run 3 analyses `get` only from the zero fact
    (`fg_getter_zero_only`), crosses the call by the reversed record (by `satI`, `fg_record_sat`) and reports the
    vulnerability in the NORMAL layer under the zero premise of the root (`fg_found`), so run 3 confirms it and the
    driver stops (`STOP_RULE`). These Lean results are of the concrete design. SINCE F72 (PENDING the model; §7.4):
    backward run 2 emits the FLOW premise `(ret, ., *, {}, *)` in `get` (the pattern of `get` has the mark `*`), and
    the backward summary is `(ret, ., *, {}, *) → (arg, .f, *, {}, *)`; it is a normal singleton, so
    `get` still gets only the zero demand. Forward run 3 crosses the call by the reversed record by `applicable`, and
    the result is the same: the vulnerability in the NORMAL layer, CONFIRMED, `STOP_RULE`.
35. STRUCTURAL SELECTION AND F76 READ VIEWS. Omit callee demand for every native normal singleton:
    exact→exact, FLOW→FLOW, exact→must/E, must/E→must/E and must/E→exact. Check the actual backward views
    on admitted and excluded fields, distinct marks, and subsequent forward replay of the stored raw record.
    In particular, `p.[any-taint](T) → ret.[any-taint](T)` must produce no callee demand. Its backward view
    accepts `ret.f.g.$(T)` and yields `p.[any](T)` in the DEMAND layer in the caller. A must→exact view likewise
    yields a caller DEMAND result without selecting the callee summary. Preserve selection for DEMAND-layer
    summaries, normal multi-premise summaries and backward zero-premise seed paths. Compare the tree shortcut
    with per-leaf selection before publication restriction (`CurrentSummarySelection`); an input suffix or a
    view-result layer cannot change the structural decision.
36. THE RESTRICTION VECTORS (`ap.md` §6.4; §4.6). The intersection against the earlier restriction: `[any]` against
    `D-p = $` gives `$` (`Handoff.RVec.v64_restrictI`; `restrictU` keeps `[any]`, `v64_restrictU`); a premise that only
    overlaps `D-c` gives no result (`vOverlap_restrictI`; `restrictU` gives one, `vOverlap_restrictU`); no `D-p` gives
    none (`vNoExit`); every row of the conclusion restriction (`RVec.row_*`). With the exclusion (`HandoffX.XVec`):
    the same example (`v64_demand`, `v64_taint`), `[any-taint]/{4} ∩ */{5} = [any-taint]/{4, 5}` (`v_taint_star`),
    the rows at, above and below `D-p` (`v_at_rows`, `v_above_rows`, `v_below_rows`), a premise that only overlaps
    `D-c` (`v_overlap`), a premise inside `D-c` only with its exclusion (`v_inside_only_with_excl`), and the cell above
    a `*/E2` exit pattern, where `restrictX` was not the intersection (`v_old_above_not_inter`). The mark tests of the
    restriction: item 41.
37. THE FRONTIER (§7.8). The frontier log of item 32 over runs 1 to 5 (the field limits 1 to 5): `wrap` is in
    `analysed` in run 1 and in no later run while no seed is in its call subtree (`HandoffMain.exclusion_canon`; the
    later rounds argued, §11); every demand pattern WITH AN EXIT PATTERN of run 5 lies inside a demand pattern of
    run 3 of the same method key, in the locations and the marks (`HandoffNoStar.narrowing_canon_loc_exactM`; the
    concrete design. WRAP has no `*` pattern after run 1, so the expected values do not change with F72; for F72 the
    theorem is PENDING, §11). The
    demand of the root is the zero demand and the seed-path pattern `((x, .a, [any], T), none)`
    (`HandoffCases.Wrap.demN_exact`): these are
    not part of the theorem (§11 THE SEED PATHS), so the test does not assert the narrowing for them; every complete
    run logs one frontier with every field of §7.8; the
    backward run 2 has at least one reversed crossing, and run 3 at least one record crossing. The condition on the
    seeds is needed: if `wrap` calls a callee with the sink of a DEMAND vulnerability on the argument of `wrap`, the
    seed gives a zero-premise backward edge of `wrap` with a non-zero requirement (§7.4 case 2), and `wrap` is in
    `analysed` again in the next forward run, although its summary leaves are normal singletons.
38. THE SEEDS OF THE DEMAND VULNERABILITIES (§7.3). Two vulnerabilities: run 1 confirms one and reports the other as
    DEMAND. Backward run 2 seeds only the DEMAND one. Forward run 3 need not report the CONFIRMED one; the report after
    run 3 still holds both: the first as CONFIRMED (final), the second as CONFIRMED or DEMAND by run 3 (§7.5).
39. THE TRIGGER OF AN END FACT (§4.5, §7.3; argued, §11; program END, `analyzer-impl.md` §9.1 row 31).
    `root(): x = source(); r = M(x); sinkAny(r)`, `M(p): y = sinkCall(p); w = wrap(y); return w`, where `sinkCall` is a sink `ContainsMark(arg0, T)` with the end-fact
    action `AssignMark(U, Result)`, `wrap` an unresolved callee with the pass rule `CopyAllMarks(arg0 →
    Result.[AnyField])` (a may) and `sinkAny` a sink `ContainsMarkOnAnyField(arg0, U)`. Run 1 CONFIRMS the sink of
    `sinkCall` (V1) and reports `sinkAny(r)` (V2) as DEMAND; the hand-off seeds only V2. In backward run 2 the
    requirement of V2 reaches the reversed end-fact edge in `M`, and the analyzer fires the sink seed `(p, ., $, T)` of
    V1 at that statement, once; its seed path gives `M` the pattern `((p, ., $, T), none)` and reaches `source()` in
    the root (a source hit). Forward run 3 triggers V1 again, makes the end fact, publishes
    `zero → (ret, ., [any], U)` of `M` through the pattern `(zero, (ret, ., [any], U))`, and reports V2. A test
    analyzer with no trigger seeds does not report V2 in run 3, and the driver ends with `STOP_RULE` without it.
40. THE REVERSAL OF A CONJUNCTION (§4.3; a regression of a false positive that existed before F70; argued, §11;
    program CONJ, `analyzer-impl.md` §9.1 row 32).
    `root(): a = srcT1(); b = srcT2(); r1 = M(a, b); y.f.g = r1; sinkT(y.f.g); r2 = M(a, c); sinkT(r2)`,
    `M(p1, p2): ret = lib(p1, p2); return ret`, where `lib` is a conjunctive source `ContainsMark(arg0, T1) ∧
    ContainsMark(arg1, T2) → Result.$ (T)` and `c` is clean; the field limits 1, 2, 3. Run 1 reports `sinkT(y.f.g)`
    as DEMAND (the cut) and does not report `sinkT(r2)`. In backward run 2 the reversal of the conjunction in `M` gives
    `(ret, ., $, T) → (p1, ., $, T1)` and `(ret, ., $, T) → (p2, ., $, T2)` in the DEMAND layer: no record,
    both handed off (§7.4 case 3). Forward run 3 analyses `M` with both members: the first call CONFIRMS
    `sinkT(y.f.g)`, and the second call gives nothing, so `sinkT(r2)` is not reported. A test reversal that keeps the
    normal layer makes both backward summaries records, and run 3 reports `sinkT(r2)` CONFIRMED (the false positive).
41. THE MARK-AWARE RESTRICTION (`ap.md` §6.4; §4.6; `ap-history.md` F71). THE USER'S EXAMPLE, a core test: a
    restricted forward run with a demand store given by hand, a method key with the one demand pattern
    `D-c = (x, ., $, T)`, `D-p = (ret, .f, $, U)`, and the summary delta `(x, ., $, T) → (ret, .f, $, T)` at its exit.
    The premise lies inside `D-c`, but the conclusion mark `T` is not the mark `U` of `D-p`: the analyzer publishes
    nothing for it. The same delta against `D-p = (ret, .f, $, T)` is published; it is a normal singleton,
    so its raw leaf is a record and gives no callee demand with either `D-p`. A
    demand-layer form of the same delta (`m(x){ ret.f.g = x; }` at the field limit 1: the leaf `(ret, .f, [any], T)`,
    which the meet with `D-p = (ret, .f, $, T)` makes `(ret, .f, $, T)` in the demand layer) is published and stored
    as a demand edge (`summaries.demandEdges()`) with `D-p = (ret, .f, $, T)`, and not with `D-p = (ret, .f, $, U)`;
    the frontier counts its demand edge only in the first case. The AP-level data
    (`Handoff.RVec`): the premise test passes (`vMark_user_inside`), the conclusion mark test fails
    (`vMark_user_concMark`), so no result (`vMark_user_restrictI`); the locations alone match, so the test in the
    locations only (before F71) and `restrictU` keep the edge (`vMark_user_loc`, `vMark_user_restrictU`); with the mark
    `T` in `D-p` it is kept (`vMark_user_same`). A premise mark that `D-c` does not admit (`(x, ., $, T)` against
    `D-c = (x, ., $, U)`): inside as locations, not as marks, so no result (`vMark_prem_loc`, `vMark_prem_inside`,
    `vMark_prem_restrictI`). THE `*∖X` CELLS: `D-c = (x, ., $, *∖{T})` gives no result for a premise with the mark `T`
    and keeps a premise with the mark `U` (`vMark_inStarEx_T`, `vMark_inStarEx_U`); `D-p = (ret, .f, $, *∖{T})` gives
    no result for a conclusion with the mark `T` and keeps one with the mark `U` (`vMark_outStarEx_T`,
    `vMark_outStarEx_U`). (Since F72 the `*∖X` pattern cells are dead in a real run: the hand-off normalization R1
    replaces `*∖X` by `*`; the vectors stay as tests of the test function.) An abstract conclusion mark stays whole
    (`inter_exc_absmark`). Before F72 this was AP-level only; since F72 a restricted run has such conclusions, the
    conclusions of a FLOW premise (§3, §4.6). In the X form (with the exclusions): `HandoffX.XVec.vM_user`, `vM_prem`, `vM_inStarEx`,
    `vM_outStarEx`. The
    contract in the locations only is false on the user's example (`Handoff.restrictI_contract_loc_false`,
    `HandoffX.XVec.restrictIX_contract_loc_false`).
42. THE `*∖X` EMISSION (`ap.md` §6.3; §4.4; `ap-history.md` F71). A core test: a backward run with a demand store
    given by hand, a method key with a demand pattern whose entry pattern is `D-c = (ret, ., $, *∖{T})` (between F71
    and F72 a backward entry pattern with the mark `*∖X` came from a run-1 summary conclusion after a cleaner, §7.3;
    since F72 the hand-off replaces `*∖X` by `*`, so no real run has this pattern, and this test only checks the cell
    of the emission), and two requirements at its forward exit, `(ret, ., $, T)` and `(ret, ., $, U)`. Only
    the second gives an initial fact, `(ret, ., $, U)`: a requirement with a mark in `X` cannot come from that summary,
    because the summary does not pass the mark. The AP-level data: the entry pattern `(x, ., $, *∖{T})` and the added
    fact `(x, ., $, T)` give nothing (`Handoff.RVec.vEmit_starEx_T`; before F71 `*∖X` counted as `*`, and the emission
    gave `(x, ., $, T)`, `vEmit_starEx_T_pre70`); the added fact `(x, ., $, U)` gives `(x, ., $, U)`, which lies inside
    the entry pattern with its mark (`vEmit_starEx_U`); in the X form `HandoffX.XVec.vM_emit`.

The items 43 to 47 test the rules of F72 (§3; `ap-history.md` F72). Their Lean model is PENDING (§11): the items
43, 45 and 46 are the CEGAR programs of the plan (`AbsCases.lean`, (i) to (iv)). Until the model is done, each test
records what the iteration gives; a real vulnerability that the F72 iteration misses is a counterexample to R6, and
the coordinator gets the program.

43. SHARING: THE GETTER WITH TWO MARKS (CEGAR (i); §4.4). `root(): a = srcT(); b = srcU(); x = get(a); y = get(b);
    sinkT(x); sinkU(y)`, `get(p): return p.name`, with `srcT`, `srcU` sources with the marks `T`, `U` and `sinkT`,
    `sinkU` sinks of these marks. The run-1 summary of `get` is in the demand layer (the case `above`), so its pattern
    is `(D-c = (ret, ., [any], *), D-p = (p, ., *, {}, *))`, a `*` pattern. Backward run 2 has ONE non-zero initial
    fact in `get`, the FLOW premise `(ret, ., *, {}, *)`, for both requirements (one `initials.add` stores it). A
    restricted run never has more than one initial fact per `*` pattern in `get`: if forward run 3 emits in `get`, it
    emits ONE FLOW premise for both added facts; if the backward FLOW summary is a normal singleton, run 3 crosses `get` by its
    reversed record and has only the zero fact there. Both vulnerabilities are reported. Before F72 the restricted
    runs had one initial fact per mark (NO SHARING).
44. THE HAND-OFF NORMALIZATION AND THE CONCRETE PATTERN (R1, R2; §7.3, §4.4). `m(x): y = x.f; clean(T, y);
    return y`, with a cleaner of `T`. Its run-1 summary has a `*∖{T}` conclusion in the demand layer, so it is a
    demand edge. The `DemandStore` of backward run 2 has the pattern with the mark `*` (not `*∖{T}`), and the stored
    publication keeps `*∖{T}`; the record and the summary still stop the mark `T` at the application. The requirements
    `(ret, ., $, T)` and `(ret, ., $, U)` both get the FLOW premise. THE USER'S CELL: a core test with a demand store
    given by hand, the pattern `D-c = (x, ., $, T)` and the added fact `(x, ., $, *)` (or `(x, ., $, *∖{U})`): no
    initial fact, and no request (§4.4: "The added fact can't satisfy the demand").
45. NO REQUEST AFTER RUN 1 (R4; CEGAR (ii), (iii); §4.6). In both programs the callee `c` reads a field of its
    argument, so its run-1 FLOW summary is in the demand layer, and its pattern has the mark `*`.
    (ii) A MARK-CHANGING PASS RULE: `root(): a.h = srcT(); r = c(a); sinkU(r)`, `c(p): y = p.h; q = lib(y);
    return q`, where `lib` is unresolved with a pass rule from `arg0` with the mark `T` to `Result` with the mark `U`.
    In each restricted run the FLOW premise of `c` gives no result at the pass rule and no request (the mark gate);
    the vulnerability is found through the concrete pattern that the request and the answer of run 1 made (the
    concrete-mark summary of `c`). (iii) A PARTIAL CLEANER of `T` under a `*` premise: `root(): a.h.f = srcT();
    a.h.g = srcT(); a.h.k = srcU(); r = c(a); sinkT(r.f); sinkT(r.g); sinkU(r.k)`, `c(p): y = p.h; clean(T, y.f);
    return y`. The FLOW fact continues as `*∖{T}` with no request. Expected: `sinkT(r.f)` is not reported, and
    `sinkT(r.g)` and `sinkU(r.k)` are reported; `sinkT(r.g)` only through the concrete pattern of run 1 (the request
    of the cleaner and its answer).
46. THE SEARCH FOR A COUNTEREXAMPLE TO R6 (CEGAR (iv)). A DIFFERENTIAL TEST on generated programs (the fuzzer of
    `analyzer-impl.md` §9.2) and on the programs of this list: the iteration with the rules of F72 against the
    iteration of the concrete design (the rules of F71, with the same field limits). The F72 report must hold every
    vulnerability of the concrete report, with the same or a better state. The generator aims at a flow that needs a
    concrete mark in a callee that only `*` patterns reach after run 1, for example a concrete demand that the
    backward weakening loses (a requirement `ret.a` with the mark `T` that only a `*` pattern `ret.*` covers, and a
    mark-specific rule below `ret.a`). A missed vulnerability is a counterexample: the test keeps the program.
47. THE BACKWARD WEAKENING (R2 backward; §4.4, §7.4). In the getter of item 34 the DemandStore of forward run 3 has no
    pattern of `get` except the zero demand, the backward initial fact of `get` is `(ret, ., *, {}, *)` (not
    `(ret, .a, $, T)`), and the frontier of backward run 2 has `get` in `analysed` with one initial fact.

---


## Interpreter tests

Bare rule references in this part name `interpreter.md`. Test item numbers and
§7 refer to this part. Deviation ids are in `implementation-background.md` →
Interpreter migration differences → §6.

## 7. Test plan

### 7.1 Existing tests and the rows they pin

| Test | Rows | Status |
|---|---|---|
| `JIRStatementSummaryTest`: field read, static read, array read | §2.2 reads, D1 | UPDATE: expect `y.* → y.*`, no keep-except |
| `JIRStatementSummaryTest`: self read | §2.2 `x = x.f`, D2 | UPDATE: no refine-only edge |
| `JIRStatementSummaryTest`: field write, static write, array write weak, self write | §2.2 writes, §2.1 write rule | keep (read the exclusion as the edge exclusion) |
| `JIRStatementSummaryTest`: cast, binary, return | §2.2, §5.1 cast filter | keep |
| `JIRStatementSummaryTest`: reversed rows | §4.9 | UPDATE the reversed read |
| `GoStatementSummaryTest`: field store strong | §2.3 `y.f = v` | keep |
| `GoStatementSummaryTest`: comma-ok lookup | §2.3 comma-ok, D1 | UPDATE: `m.* → m.*` |
| `GoSequentExactTest` (copy, field read, phi, alias of an overwritten field) | §2.3, §2.5 | keep; the field read case changes as D1 |
| `GoSequentRoundTripTest`, `GoSequentUnchangedAgreementTest` | §2.1 step 2, §4.9 | keep |
| `AliasSampleTest`, `DSUAliasAnalysisStateTest`, `DSUAliasAnalysisInvalidateOuterHeapAliasesTest`, `GoDSUAliasAnalysisTest`, `GoAliasSampleTest`, `GoAliasFactsTest`, `AliasDirectiveTest` | the alias input of §2.5 and §3.8 | keep |
| `FactCleanerContractTest`, `AnyFieldMarkExclusionTest`, `DeepAccessorExclusionTest` | the old cleaner representation | REPLACE by the vectors of `clean` (ap.md §4.7) and the mapping table of §5.2 |
| `CleanerFieldSensitivityAnalysisTest`, `DeepCleanSummaryAnalysisTest`, `CleanerDslAnalysisTest`, `CleanerDslControlFlowAnalysisTest` | §4.5 order (sinks before cleaners), §5.2 mapping (plain = `exact`, AnyField = `below`), the `Result` cleaner tests | keep (a gate of the new analyzer) |
| `AnyFieldPrimitiveAnalysisTest` | §5.1 mark policy, `[any]` and `[any-taint]` tails, D12, I14, D32 | keep. In the whole-object test the `AnyField` part of the entry-point source is an `[any-taint]` fact (§4.1); the element read keeps it `[any-taint]` on the `byte` position (the mark is primitive-tracking), so the finding is a CONFIRMED entry of run 1 (D32) |
| `ExampleTest` `test nd rule` (`PositiveNdRule`, `PositiveNdRule2`) | §5.3 | keep |
| `MultiReturnDataFlowTest` | §4.7, §3.4 (no leak over the exceptional exit) | keep |
| `JavaDataFlowReachabilityTest`, `KotlinDataFlowReachabilityTest` (lambda, stream, collection samples) | §3.6, §3.7, §3.9 | keep |
| `DataFlowBenchFalseNegativeTest` (exception arms disabled) | §3.4 | keep disabled (G1, ap.md §11.2) |
| Go `InterfaceDispatchTest`, `MethodReceiverTest` | §3.1 receiver, §3.6 | keep |
| Go `ClosureTest`, `ClosurePatternTest` | §2.3 closures, §3.9 | keep |
| Go `GlobalTest`, `GlobalSourceTraceTest` | §2.3 globals, read sources | keep |
| Go `MultiReturnTest`, `MultiReturnPatternTest` | §2.3 `ret.#i`, `extract` | keep |
| Go `PointerHeapTest`, `PointerPatternTest` | §2.3 `*p = v`, §2.5 | keep |
| Go `PassThroughTest`, `MapOpsTest`, `ChannelPatternTest`, `CollectionTest`, `SanitizationTest`, `DeferTest`, `GoroutineTest` | §3.7, weak writes, kills, `go`/`defer` | keep |

### 7.2 New tests

1. Builder tests, one per row of §2.2 and §2.3: the edges, the touched bases, the filters.
2. Write rule: the strong write of a two-accessor path (`C.s = x`) gives two identity edges with one exclusion each;
   `*p = v` gives none.
3. Binding tests, one per row of §3.1, JVM and Go, with the filters.
4. Order tests: a sink sees the fact before a cleaner at the same call; a pass rule sees the cleaned fact; a source
   result goes through the aliases; a constructor fact passes over the call.
5. Go cleaner at the call site (D7): a non-user-defined `RemoveMark` rule cleans the argument; the same rule with a
   resolved callee and with an unresolved callee.
6. Cleaner mapping: one test per row of §5.2, including `<string-bytes>`. Keep the primitive AP tests
   (`AnyTaintExCases.CL`, `AnyTaintEx.Vec.clean_exact`) separate from the lowered field-action tests (F74).
   On the normal fact `(x, p, [any-taint], E, T)`, a named action at `x.p.f`, with `f ∉ E`, keeps the normal
   outside-field fact `(x, p, [any-taint], E ∪ {f}, T)` for every reach. `atAndBelow` gives only that fact;
   `below` also gives normal `(x, p.f, $, T)`; `exact` also gives demand `(x, p.f, [any], T)` with no exclusion.
   With `f ∈ E`, the read gives no temporary fact and the action keeps the original fact. The single-field
   vectors are `FieldCleanerX.exact_vector`, `below_vector`, `atAndBelow_vector`. Also test the read and write
   filters, the ordinary statement cuts, a shared fresh temporary in the reversed action, and projection
   away from that temporary before publication.
7. Type filter placement: one test per row of §5.1; a `*`, an `[any]` and an `[any-taint]` fact always pass, with
   their tails and exclusions; the policy drops a mark on an `int` base.
8. Requests: one test per row of §5.4 in run 1, also the static rows (JVM `x = C.s`, `C.s = x`, Go `x = G`, a sink on
   `S`); no request in a restricted run and in the backward run: on a fact with the mark `*` (F72) each mark row of
   §5.4 gives nothing (no sink report, no gate result; a cleaner gives `*∖{T}`), and no request occurs.
9. ND: the result does not depend on the order in which the two premise facts arrive; a conjunctive sink reports only
   a full combination.
10. The rule statement of a call (§4.1): the zero fact keeps itself; a conditional source gives only its target, and
    the read fact does not pass over the call.
11. Static positions: one test per row of §1.4, also the rule errors.
12. Contracts: a check over the statement summaries, the call stages and the bindings of a program for every item of
    I11 and I12, and for the interpreter duties of ap.md S9 and S10: every literal has a concrete mark (S9); the
    premise base of every micro edge is a touched base (I1, S10); the target of a conjunctive micro edge has a concrete
    mark and no `*` tail (S10, W7). Also I6 and I7 (also the clause: a micro edge with a `$` target has a concrete
    premise mark), and I14 (only a source edge has the target tail `[any-taint]`, with a concrete target mark and a
    concrete premise mark; no micro edge has the premise tail `[any-taint]`; no binding has an `[any]` or an
    `[any-taint]` target; no pass rule has an `[any]` premise, D33). The builder of a statement summary asserts the
    same.
13. Backward run: the reversed touched set and the identity edge of an alias target (§4.9); the call order of §4.9,
    with a reversed source, a rewriter, an unresolved callee and a constructor; at the start of the run only the sinks
    of the DEMAND entries of the report after the previous forward run seed, and a CONFIRMED vulnerability seeds
    nothing; THE TRIGGER OF AN END FACT: a requirement that reaches the reversed end-fact edge of a sink alternative
    fires the sink seeds of that alternative once per (method key, statement, alternative), also when its
    vulnerability is CONFIRMED (the program of `ap-history.md` F70, the review round: `root(){ x = source();
    r = M(x); sinkAny(r); }`, `M(p){ y = sinkCall(p); w = wrap(y); return w; }`, where the sink `sinkCall` on `T` is
    CONFIRMED in run 1 and has the end fact `y.$ (U)`, `wrap` is unresolved with the pass rule
    `CopyAllMarks(Argument(0) → Result.AnyField)`, and `sinkAny` reads `U`: forward run 3 reports the vulnerability of
    `sinkAny`); a seed after the
    reversed cleaners is not killed; a seed is cut by the field limit; the seed of an exit sink at the exceptional exit; at `x = m(a)`, where
    `b.q` is an alias of `x`, a requirement on `b.g` passes over the call to before it (step 1).
14. Array elements of a call sink (§4.2): a sink `ContainsMark(Argument(0), T)` on an `Object[]` argument triggers on
    `arg(0).[e].$ (T)`, and on a `String` argument it does not; if its vulnerability is a DEMAND entry of the report
    after the forward run, the next backward run seeds both alternatives.
15. Exit rules at the exceptional exit (§4.7): an exit sink on `Result` triggers on the thrown tainted value; an exit
    source at the exceptional exit adds no summary edge; an unconditional exit source and an unconditional exit sink
    fire at both exits, and the unconditional exit sink reports at both exits (D26).
16. Cleaners (§4.2, D20): `RemoveMark(T, P) if ContainsMark(P, T)` and a cleaner with a negated literal do not clean;
    the same cleaner with no condition cleans.
17. Lambdas (§3.9, D19): a call through a functional interface of the project, with a lambda that the prescan knows,
    has no unresolved path; the same call with no known lambda takes the unresolved path; a call through a JDK
    functional interface (`java.util.function.Function.apply`) with a known lambda keeps the unresolved path of the
    resolver's own failure.
18. No liveness (§2.1 step 1, D25): in `void serve() { while (true) { String req = in.readLine(); exec(req); } }` the
    fact on `req` reaches the sink `exec`; a fact on a dead local is not dropped.
19. Rule positions (§1.3, D29): `Argument(0).AnyField.f` and `Argument(0).AnyField.AnyField` are rule errors, logged
    once, and the forms of the other rules of the method are built; `Argument(0).AnyField` is accepted. A source rule
    whose condition has such a position gives no source and no rewriter cleaner; an `Or` with one bad disjunct gives no
    form. A pass rule with an `AnyField` premise (`CopyMark(T, Argument(0).AnyField → Result)`,
    `CopyAllMarks(Argument(0).AnyField → Result.AnyField)`, Go `CopyTaintMark` with `AnyAccessor` on the from
    position) is a rule error, logged once and rejected (D33); `CopyMark(T, Argument(0) → Result.AnyField)` is
    accepted.
20. The generated Spring cleanup form (§1.4, §5.2, D27) contains the primitive cleaner
    `(S, [], atAndBelow, all)`, built without a fact or a configuration position. It drops every fact on `S`,
    also `S.*` in run 1, with no request, and passes facts on other bases. The cached backward form uses the same
    cleaner at the reversed Actions stage. Other generated methods have no native cleaner. The Spring dispatcher:
    a static value that one dispatched controller taints does not reach the next one, and the registry fields keep their facts
    across `__cleanup__()`.
21. Empty methods (I8, D28): a call to a native method of the project takes the unresolved path (the pass rules and
    the default identity apply); a call with an empty and a non-empty resolution result enters only the non-empty
    callee; no summary exists for the empty method.
22. Aliases on call results (§3.8 AC3 to AC5): a normal-layer result equal to its start fact is not copied to the
    aliases; a demand-layer result equal to its start fact is copied (a run-1 answer for the premise
    `(x, [], *, {}, T)` with the demand start fact `(x, [], [any], {}, T)`).
23. The global-state rule (§4.7 step 3, G2, D30): a plain exit sink on `S.<C>.$ (ST)` drops that part of a
    zero-premise item (a state that the method sets) from the summary edge. For the conjunctive exit sink
    `ContainsMark(ClassStatic(C), ST) and ContainsMark(Result, T)` with an untainted `Result`, the `S` part is dropped
    and stored; a later item with a tainted `Result` completes the combination with the stored part. A caller-set state
    passes the exit sink of a callee and is still seen by the caller after the call: in
    `root(){ acquire(); release(); after(); }`, where `acquire` sets `ST` on `ClassStatic(L)`, `release` has the exit
    sink `ContainsMark(ClassStatic(L), ST)` and `after` a sink on the same position, both sinks report, in run 1 and in
    the restricted runs.
24. The mark policy below `[e]` (§5.1): a mark that is not primitive-tracking on `b.[e]` of a `byte[]` and of an
    `Integer[]` value is dropped; on `b.[e]` of a `String[]` value it stays; below a field no type is read.
25. End facts (§4.1): a sink with an end-fact action that triggers on a demand-layer bound fact gives a demand-layer
    zero-to-fact edge; a conjunctive sink gives its end facts when an item completes the combination, in the layer of
    the combination. An end-fact action `AssignMark(T, PositionWithAccess(P, AnyField))` gives
    `Zero → (sink statement, P.[any-taint] (T))` with the Empty exclusion, in the layer of the sink edge (I14).
26. The summary rewriter (§5.2, D23): at a call with a user source `if IsConstant(...) and ContainsMark(...)`, a user
    cleaner `if ContainsMark(...)` and an unconditional user cleaner, the rewriter selects the source and the
    unconditional cleaner, and not the conditional cleaner; a rule that a rule error rejected is not selected (§1.3).
    The rewriter removes `T` from a zero-premise summary result and from the default identity of an unresolved callee.
    For a selected source with the target `PositionWithAccess(Result, AnyField)`, the rewriter cleans `ret` with
    `(ret, atAndBelow, T)` (D34): a callee result `(ret, [f], $, T)` and a callee result `(ret, [], [any-taint], T)`
    are both dropped, and only the source result of the call stays. For a selected unconditional user cleaner
    `RemoveMark(T, PositionWithAccess(Result, AnyField))`, the rewriter cleans `ret` with `(ret, below, T)` (the row of
    the cleaner in §5.2, as today): a callee result `(ret, [f], $, T)` is dropped, a callee result `(ret, [], $, T)`
    stays, and a callee result `(ret, [], [any-taint], T)` gives `(ret, [], $, T)` in its layer.
27. The static exception at the rule statement of an exit (§4.7, §2.1 step 4; ap.md §4.10 item 1): in run 1 an exit
    source with the literal on `S.<C>.f`, on the fact `S.*` with the premise `S.*`, gives the position request
    `[<C>, f]` and no fact, and the fact `S.*` stays in the worklist.
28. A conjunctive exit source (§4.7 step 1, §5.3, D31): `AssignMark(S2, ClassStatic(C)) if
    ContainsMark(ClassStatic(C), S1) and ContainsMark(Result, T)` at the normal exit stores the input of each literal,
    and the item that completes the combination gives `S.<C>.$ (S2)` with the union of the premise sets of its inputs,
    in either order of arrival. With two non-zero premises the summary is an ND summary, and a caller applies it by
    E6. The rule is not a rule error.
29. The taint annotation (I14, §4.1, D32): `AssignMarkOnAnyAccessor` on `Argument(0)` (a source at a call), an
    entry-point source with `AssignMark` on `PositionWithAccess(Argument(0), AnyField)`, an exit source and a
    conjunctive source with an `AnyField` target give the target tail `[any-taint]` with a concrete mark; a conditional
    source with `ContainsMarkOnAnyField(Q, T')` gives the premise `Q.[any] (T')`; a pass rule
    `CopyMark(T, P → Q.AnyField)` gives the target `Q.[any] (T)` and a result in the demand layer. The source
    `AssignMark(T, PositionWithAccess(Q, AnyField)) if ContainsMark(P, T)` and the pass rule
    `CopyMark(T, P → Q.AnyField)` differ only in the target tail (`P.$ (T) → Q.[any-taint] (T)` and
    `P.$ (T) → Q.[any] (T)`; in Lean one micro edge with two taint flags): the source gives a normal `[any-taint]`
    result, and run 1 confirms the sink `ContainsMarkOnAnyField(Q, T)`; the pass rule gives a demand `[any]` result,
    and run 1 does not confirm it (Lean, in the spec closure `AnyTaintEx.D6X`:
    `AnyTaintExCases2.PassRule.source_vs_pass`, `source_normal`, `source_confirmed`, `pass_demand`,
    `pass_not_confirmed`; round 1: the vectors `AnyTaint.Sanity` and `AnyTaintCases.PassRule.source_vs_pass`). A
    source result has the Empty exclusion,
    also when its premise fact has one (`AnyTaintEx.Vec.source_any_target`).
30. The strong write, the reads and the cut (§2.4; the programs of `AnyTaintExCases`):
    * the setter, program S: `root(){ dto = srcAny(); dto.setName(c); sink(dto.name); sink(dto.email); }`,
      `setName(n){ this.name = n; }`. The run-1 record `(this, [], *, *) → (this, [], */{name}, *)` of `setName`
      applies to the added fact `(this, [], [any-taint], {}, T)` and gives `(this, [], [any-taint], {name}, T)`,
      normal; in `root` the object is `(dto, [], [any-taint], {name}, T)`, normal. `sink(dto.name)` is not reported, in
      no layer, and it is not real; `sink(dto.email)` is CONFIRMED in run 1 (`AnyTaintExCases.S.record_app`,
      `S.run1_dto_ann`, `S.run1_name_not_reported`, `S.name_not_real`, `S.run1_email_confirmed`). Under the first F69 text (the demotion)
      both sinks are DEMAND entries (`AnyTaintExCases.S.run1T_vulns`, `S.run1T_not_confirmed`);
    * the deep setter, program SD (`root` calls `setNameDeep(c){ this.setName(c); }`): the same result in `root`
      (`AnyTaintExCases.SD.run1_name_not_reported`, `SD.run1_email_confirmed`, `SD.same_result`);
    * the broad demand, program B: run 3 with the demand `(D-c = (this, [], [any], T), D-p = (this, [], [any], T))` of
      `setName` emits the must-premise `(this, [], [any-taint], {}, T)` and gives the summary to
      `(this, [], [any-taint], {name}, T)`, normal; `sink(d.name)` stays not reported, and `sinkAny(e)` is CONFIRMED in
      run 1 and in run 3 (`AnyTaintExCases.B.run1_name_not_reported`, `B.run1_anyE_confirmed`, `B.run3_must`,
      `B.run3_summary`, `B.run3_name_not_reported` with the records of run 1, `B.run3_anyE_confirmed`). Run 3 with
      this demand made by hand is a test of the restricted run; the JVM analysis test checks the results of run 1
      (run 1 of B has no DEMAND entry, `B.inv1`, so the iteration stops after it);
    * the two-level write, program X: `x = srcAny(); x.f.g = c` as ONE statement (the micro edges
      `strongKeep(x, [f, g])` and `c.* → x.f.g.*`) gives `(x, [], [any-taint], {f}, T)` and
      `(x, [f], [any-taint], {g}, T)`, both normal, which cover exactly the locations not below `x.f.g`;
      `sink(x.f.g)` is not reported, and `sink(x.f.h)` and `sink(x.k)` are CONFIRMED (`AnyTaintExCases.X.two_results`,
      `X.locations_exact`, `X.fg_not_reported`, `X.fh_confirmed`, `X.k_confirmed`). This is an AP-LEVEL test: a
      synthetic statement summary on the AP (`validation-plan.md` → AP tests → §13 item 1, `ApplyEdgeVectorsTest`, and item 3, the layer tests;
      on the tree form `AnyTaintProgramsTest` of `ap-impl.md`), not a JVM analysis test. No JVM statement writes two levels: the builder of §2.2 gives `t = x.f; t.g = c`, and
      the write `t.g = c` is strong on `t` only; at the alias path `(x, [f])` of `t` it is the gen-only edge
      `c.* → x.f.g.*`, and `x` is not touched (§2.5 A2, A3: the weak alias write, gap G7). So the JVM ANALYSIS TEST
      of this program expects the documented false positive of the alias gap: `x` keeps
      `(x, [], [any-taint], {}, T)`, normal, and `sink(x.f.g)` is a CONFIRMED entry (§0.1, ap.md §11.1), as are
      `sink(x.f.h)` and `sink(x.k)`; `t` is `(t, [], [any-taint], {g}, T)`, normal, so `sink(t.g)` is not reported;
    * the reads, program R (S, then `y = dto.name; z = dto.email; sinkAny(y); sinkAny(z)`): the read through the
      excluded `name` gives nothing, the read of `email` gives `(z, [], [any-taint], {}, T)`, normal; `sinkAny(y)` is
      not reported, `sinkAny(z)` is CONFIRMED (`AnyTaintExCases.R.reads`, `R.y_not_reported`, `R.z_confirmed`);
    * the cut, program CUT (X with the field limit 0), also an AP-LEVEL test: the AP `limit` with `L = 0` on the
      results of the synthetic statement summary of X (no run has `L = 0`: the field limit of run 1 is at least 1,
      I12 (d)). The cut makes `(x, [f], [any-taint], {g}, T)` the fact `(x, [], [any], T)` in the demand layer with no
      exclusion, so `sink(x.f.g)` and `sink(x.f.h)` are DEMAND entries (`AnyTaintExCases.CUT.cut_ops`, `CUT.run1_cut`,
      `CUT.cut_reports`; the vector `AnyTaintEx.Vec.cut_drops`). The JVM ANALYSIS TEST of the cut uses the field limit
      1 and a deeper write: the source `dto.f.g = srcAny()` (JIR `s = srcAny(); t = dto.f; t.g = s`) gives, through
      the alias path `(dto, [f])` of `t`, the result `(dto, [f, g], [any-taint], {}, T)`, which the cut makes
      `(dto, [f], [any], T)` in the demand layer with no exclusion (the same cut on one model statement:
      `AnyTaintCases.Cut.cut_transfer`), so `sink(dto.f.g)` is a DEMAND entry;
    * the necessity of the exclusion: a normal `[any-taint]` result of a strong write with no exclusion would confirm a
      vulnerability that is not real (`AnyTaintCases.W.keep_normal_confirms_unreal`).
31. The getter, program G (the Spring DTO shape): `root(){ dto = srcAny(); x = get(dto); sinkAny(x); }`,
    `get(p){ return p.f; }`. Run 1 reports the vulnerability, not confirmed: the run-1 FLOW summary of `return p.f` is
    the case `above`, `(p, [], *, {}, *) → (ret, [], [any], *)` in the demand layer
    (`AnyTaintExCases2.G.run1_flow_above`, `run1_vuln`, `run1_not_confirmed`). The backward run 2 hands off the
    demand `(D-c = (p, [f], [any], T), D-p = (ret, [], [any], T))` of `get` and the zero demand
    (`AnyTaintExCases2.G.HX_exact`, `handoffX_get`); both tails are `[any]`, because the backward run has no
    `[any-taint]` (I14, §4.9). Run 3 emits the must-premise `(p, [f], [any-taint], {}, T)`
    (`AnyTaintExCases2.G.run3_must`, `run3_must_supported`), the sink edge in `root` is normal (`run3_sink_normal`),
    and the vulnerability is CONFIRMED (`run3_confirmed`; with the hand-off of run 2: `run3_confirmed_handoff`) and
    real (`AnyTaintCases.G.vuln_real`). Under the old rule W6 it is a demand entry in run 1 and in run 3 (round 1:
    `AnyTaintCases.G.run1W6_no_normal`, `run3W6_no_normal`). The ANALYSIS TEST: a Spring controller method with a
    DTO argument (the Spring rule provider adds the `AnyField` source, §4.1) passes a getter value of the DTO to a sink;
    the output has the vulnerability as a CONFIRMED entry after run 3. (The Lean results of programs G and C are of
    the closures `AnyTaintEx.D6X` (run 1) and `AnyTaintEx.DRXs` (run 3 with the earlier restriction and hand-off, the
    record of the earlier design), in `AnyTaintExCases2`; run 3 is derived there directly. With the spec closure
    `AnyTaintEx.DRX` and `HandoffX.restrictIX` the run-3 results are argued (D32). These
    programs have no exclusion edge and no cleaner, so the refined run 1 is the round-1 run 1 of `AnyTaint.D6T` with
    the Empty exclusion: `AnyTaintExCases2.G.run1_forgets`, `G.run1_carry`, `Carry.d6x_iff_d6t`.)
32. The sink in the callee, program C: in `root(){ dto = srcAny(); use(dto); }`, `use(o){ sinkAny(o.f); }` run 3
    confirms the vulnerability through the must-premise `(o, [f], [any-taint], {}, T)`, whose start fact is the normal
    sink edge in `use`; the premise is supported through the must-premise link (`AnyTaintEx.SupLinkX`: it lies inside
    the normal added fact `(o, [], [any-taint], {}, T)`, with the same mark) (`AnyTaintExCases2.C.run3_must`,
    `run3_sink_normal`, `run3_supported`, `run3_confirmed`, `run3_confirmed_handoff`). With a setter call
    `dto.setName(c)` before `use(dto)`, the added fact of `use` is `(o, [], [any-taint], {name}, T)`: against the pattern `(o, [f], [any], T)`
    the emission gives the same must-premise, and against a pattern `(o, [name], [any], T)` it gives nothing (ap.md
    §6.3; Lean `AnyTaintEx.emitX`, the vector `AnyTaintEx.Vec.emit_above_excluded`).
33. The conjunction with an `[any-taint]` input (§5.3): at `z = combine(x, y)`, with `x` an `[any-taint]` fact
    `(x, [], [any-taint], T)`, `y` the fact `(y, [], $, U)` and the source rule
    `AssignMark(V, Result) if ContainsMark(Argument(0).f, T) and ContainsMark(Argument(1), U)`, the result
    `(z, [], $, V)` is in the normal layer, and a sink on `z` is confirmed; under the old layer rule the result is in
    the demand layer (Lean `AnyTaintND.Example.layer_new`, `layer_old`, `confirmed`, `old_not_confirmed`). With the
    input `(x, [], [any-taint], {f}, T)` the literal `ContainsMark(Argument(0).f, T)` does not overlap the input, so it
    stores nothing and the rule does not fire (§5.3; argued: the conjunction model has no exclusion, ap.md §11.2).
34. Backward (§4.9): no backward edge has an `[any-taint]` tail. The seed of `sinkAny(x)` is `(x, [], [any], T)` in the
    demand layer; the reversed `[any]` literal of a conditional source gives an `[any]` requirement in the demand layer.
    The reversed pass rule `CopyMark(T, P → Q.AnyField)` (forward `P.$ (T) → Q.[any] (T)`) applied to the normal
    requirement `(Q, [], $, T)` gives the requirement `(P, [], $, T)` in the DEMAND layer, and the backward summary
    through it is not a record: in `root(){ v = src(); l = mk(v); sink(l); }`, `mk(p){ l = new L(); l.add(p); return
    l; }`, with `add` unresolved and that pass rule, the vulnerability stays a DEMAND entry in run 3. The reversed
    unconditional source `zero.$ (zeroMark) → P.[any-taint] (T)` takes the requirement `(P, [f], $, T)` to the zero
    fact in the normal layer and records the source hit. THE REVERSED CONJUNCTION (§4.9 STATEMENTS, §5.3): with the
    conjunctive source `AssignMark(T, Result) if ContainsMark(Argument(0), T1) and ContainsMark(Argument(1), T2)` of
    `lib`, the requirement `(ret, [], $, T)` gives `(p1, [], $, T1)` and `(p2, [], $, T2)` in the DEMAND layer, and no
    backward summary through them is a record. In `root(){ a = srcT1(); b = srcT2(); r1 = M(a, b); y.f.g = r1;
    sinkT(y.f.g); r2 = M(a, c); sinkT(r2); }`, `M(p1, p2){ ret = lib(p1, p2); return ret; }` (the field limits 1, 2,
    3), forward run 3 reports `sinkT(y.f.g)` and does not report `sinkT(r2)`: `c` carries no `T2` (`ap-history.md`
    F70, the review round; before the fix a false CONFIRMED entry).
35. The removal of the entry marks (§4.7 step 4, G2, D35): an entry point whose entry-point sources put the entry
    mark `m` on `Argument(0)` and on `PositionWithAccess(Argument(0), AnyField)` (the Spring DTO shape). At its normal
    exit the zero-premise fact with the leaves `(arg(0), [], $, m)`, `(arg(0), [], [any-taint], {name}, m)`,
    `(arg(0), [f], $, m)` and `(arg(0), [g], $, U)` gives a summary edge with the leaf `(arg(0), [g], $, U)` only:
    every leaf with the entry mark goes, at any depth and with both tails (the `[any-taint]` leaf with its exclusion),
    and a leaf with another mark stays. A fact on `arg(0)` whose premise is not the zero fact keeps its leaves with
    `m`.

---
