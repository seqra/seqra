# Proof history

This file is supporting evidence, not a normative spec. The current rules are in
[ap.md](ap.md), [interpreter.md](interpreter.md), and [analyzer-core.md](analyzer-core.md).
[proof-status.md](proof-status.md) gives the current proof scope and open work.

F78's current request proofs are in [run1-request-proofs.md](run1-request-proofs.md)
and the Run1Requests/Run1RequestsSafety/Run1RequestProtocol modules, with the
Run1InitialShapes and Run1PrecomputedRequests extensions. Earlier
full-query, request-exclusion and deferred-memo certificates below retain their
historical policies; they do not define F78 or require those runtime owners.

The catalogs below retain the proof history at commit `fb789d0ab`. The F70/F71
closures use concrete restricted runs and the earlier cleaner. F72 permits FLOW
premises; F75 changes the cleaner in every run. F76 adds transient must-summary
readers; F77 removes the old crossability selector and its extra callee demand.
A theorem about the historical selector does not prove F77 hand-off or iteration.
A theorem about generic reversal does
not prove these new views or their closure integration. A theorem about an earlier
closure is not a theorem about the current closure. Superseded claims and counterexamples
are recorded for their stated version only. Bare section numbers within an AP
extract refer to that version of ap.md; numbers within an analyzer extract refer
to that version of analyzer-core.md.

## AP proof catalog at the cleaner checkpoint

## 10. Theorems

All theorems are in `spec/lean/ApSpec`. "Constructive" means: only `propext` and `Quot.sound`, checked with
`#print axioms` after every main theorem. No `sorry`, no `Classical.choice`, no `native_decide`.

THE THEOREMS AND DECISION F72. Every theorem below about a restricted run or the iteration (§10.7, the restricted
part of §10.8 and §10.10, the restricted forward run of §10.11, §10.12) is about the CONCRETE restricted runs of F70
and F71: the emission `emitM` (`AnyTaintEx.emitX`), which copies the mark of the added fact, the satisfaction `satI`
(`satX`), and the closures `DR`, `AnyTaintEx.DRX`, `Backward.DB`, whose request rules never fire. The theorems hold
for that design, and their names stay. FOR THE RULES OF F72 THEY ARE NOT YET PROVED (§11.2, PENDING: THE LEAN MODEL OF
F72). The theorems whose CLAIM IS FALSE for the F72 runs (they describe the concrete design) are listed in that item.
The theorems of run 1 (§10.1 to §10.6, run 1 in §10.8 to §10.11) do not change.

### 10.1 Soundness of run 1 — `Coverage.lean`

Hypotheses: the program is well-formed (S10 and S5; Lean: `Program.WF`) and the abstraction satisfies C1 (§6.1).

| Theorem | Statement |
|---|---|
| `coverage` | If the value at the entry location `l0` of method `M` flows to `l` at node `n` (`Flow`: statements, calls, call-to-return, cleaners, type filters, nested callee flows), and the initial fact `i` of `M` covers `l0`, then the analysis has an edge `(i → f)` at `n` with `den(i, f)(l0, l)`, OR the request `(M, i, l0.mark)`. |
| `coverage_conc` | If `i` has a concrete mark, the first case holds. |
| `reach_strong`, `vuln_found` | If the zero location of a root flows through any chain of calls to a location that a sink pattern covers (`Reach`), the analysis reports the vulnerability at that sink. |
| `coverage_policy`, `vuln_found_policy` | The same for the run-1 policy (`policy1` is `policy` with the empty demand), for every field limit. |
| `edge_conc`, `req_initial_star` | A concrete-mark initial fact has only concrete-mark conclusions; a request is always on a premise whose mark is not concrete. |

That is the property "if the fact exists and the data flow exists (intra and inter procedural), the fact reaches the
destination". The cleaner case uses `Core.cleanRes_sound` (a covering result, or the request for the entry mark); the
filter case uses `Core.filt_keeps`.

### 10.2 Local lemmas — `Core.lean`, `SharedExcl.lean`

| Lemma | Statement |
|---|---|
| `applyEdge_sound` | THE CORE LEMMA: delta-concat covers the composition of the fact relation and the edge relation, or raises the request for the premise mark — in both cases, strong enough and not strong enough, for every fact mark `*`, `T`, `*∖X`. |
| `transfer_sound`, `applySummary_sound` | The statement transfer covers the statement step; summary application covers the composition with the callee flow. |
| `markComp_sound`, `markComp_none_no_pair` | The result mark composes the mark relations; a `*∖X` target that stops a concrete mark in `X` loses no pair. |
| `cleanRes_sound`, `cleanPos_inside_sound`, `cleanPos_disjoint_sound` | THE CLEANER LEMMA: a real location that the cleaner keeps is covered by a result, or the request for its entry mark is raised; the position test is sound. |
| `filt_keeps` | A prefix-closed type filter never drops a fact that covers a real (accepted) location. |
| `climbsB_of_den` | A request for the mark of the location climbs through an abstract added fact. |
| `limitF_sound`, `CoreAux.norm_sound` | The field limit and the normal form only enlarge. |
| `startFact_sound`, `coversB_sound`, `applicable_sound`, `applicable_mark`, `overlapB_of_common` | The start fact; the syntactic cover test; the mark of an applicable premise; overlap. |
| `check_sound`, `check_request_star` | A covered tainted location triggers the sink or raises the request. |
| `answerInit_covers`, `answerInit_applicable`, `policy_applicable` | The answer covers the requested location and is applicable; the run-1 policy satisfies C1. |
| `SharedExcl.applyEdge_shared_excl`, `den_shared_excl` | ONE exclusion per edge. |

### 10.3 Exactness and invariants — `Exact.lean`, `Invariant.lean`, `Closed.lean`, `Confirmed.lean`, `W6.lean`

| Theorem | Statement |
|---|---|
| `Exact.edge_exact_valid`, `closed_exact_valid` | Under S7 (`MarkWF`) and S13 (a validity predicate that every filter accepts, `FiltValid`, and that goes back along every statement micro edge and both call bindings, `BackOK`): every pair of a normal-layer edge whose end location is valid is a concrete flow. |
| `Exact.edge_exact`, `complete_exact`, `closed_exact` | The same for filters that keep every extension of an accepted path (`FiltUp`; with prefix-closure this makes a filter constant, so this form is for programs without type filters). |
| `Exact.CexFilt`, `CexMark` | The two hypotheses are necessary in the model (the two programs break W6 and S8): a filter lets a fact pass whose lower locations do not exist; a `*`-premise edge with a concrete target forgets a cleaned mark. |
| `Exact.cleanRes_exact` | A normal-layer result of the cleaner denotes only pairs of the input whose end location the cleaner keeps. |
| `Closed.closed_records_exact`, `closed_records_exact_valid` | A property of records: no request on the initial fact and only normal exit edges ⇒ the records are exactly the concrete flow from its location set. |
| `Invariant.final_star_legal`, `final_star_abstract` | W2: a `*` conclusion has the mark `*` or `*∖X` and is in the normal layer. |
| `Invariant.no_univ_star` (+ `no_univ_needs_*`) | S8 ⇒ no `*/Universe` edge fact; each hypothesis is necessary. |
| `Invariant.demand_of_any_ok` | W6 is a layer refinement. |
| `W6.D_le_D6`, `D6_le_D`, `D6_w6`, `D6_normal` | (The rule of `W6.lean` puts EVERY `[any]` result in the demand layer. The spec rule of the forward runs is now W6T, W6 with W8, which demotes only a may `[any]` result (§2.3, §10.11); every normal edge of `W6.D6` is a normal edge of the round-1 closure `AnyTaint.D6T`, `AnyTaintSim.D6_normal_D6T`. The backward run keeps W6 for every `[any]` result (§9.2). `W6.lean` stays as the record of the old rule.) W6 FOR THE WHOLE RUN 1 (`D`): under `W6.SummaryStar` the run with W6 has the same facts, with the same or a raised layer; W6 holds in it; its normal edges are normal edges of the plain run. `SummaryStar` holds for the run-1 policy with no other hypothesis (`W6.summaryStar_policy`), and for every abstraction under the hypotheses of `Invariant.no_univ_star` (`W6.summaryStar_of_noUniv`). |
| `W6.DR_le_DR6`, `DR6_le_DR`, `DR6_w6`, `DR6_normal` | The same for a forward restricted run (`DR`), under `W6.RestrictLE` (the restriction copies the layer; for the earlier restriction `restrictU`: `W6.restrictU_LE`) and `W6.ExactInitConc` (every `$` initial fact has a concrete mark; the mark-copying emission gives it: `W6.DR_eic`). No S8 hypothesis. W6 is proved for `D` and `DR` only, not for `Statics.DS`, `ND.DN` or `Backward.DB`. |
| `W6.vuln_found6`, `edge_exact6`, `edge_exact_valid6`, `edge_exactR6`, `edge_exactR_valid6`, `confirmed_real6`, `confirmed_real_valid6`, `confirmed_real_M6`, `confirmed_real_M6_valid`, `iteration_sound_M6`, `iteration_general6` | With W6 in every forward run: soundness, exactness and confirmation (also the valid forms, under S13; in a restricted run under S14) and the iteration (`iteration_general6` has no S8 hypothesis). |
| `W6.Cex.cex_w6_changes_fact` | Without `SummaryStar`, W6 changes a fact, not only its layer. The program uses an abstraction outside the spec: it emits a `$` initial fact with an abstract mark, so `Invariant.PremConc` and `W6.ExactInitConc` are false for it. |
| `Invariant.star_final_keeps_initial_excl`, `star_initial_complete`, the demand-monotone lemmas | A final `*/Ec` under an initial `*/Ei` keeps `Ei ⊆ Ec`; under `*/Ei` a normal-layer final fact has the `*` tail or `Ei = {}`; the demand layer never goes back. |
| `Confirmed.confirmed_real_valid`, `confirmed_real` | A CONFIRMED vulnerability (§4.9) is a real concrete vulnerability for the reference semantics (§3.5) (valid form; `MarkWF ∧ FiltUp` form; §11.1 lists the expected false-positive sources). `CexConfFilt`, `CexConfMark`: both hypotheses are necessary in the model (the programs break W6 or S8). |
| `Confirmed.Weak.weak_support_gap`, `Confirmed.Rev2.rev2_not_confirmed` | The weaker support condition admits a false positive; the counter-example program `Confirmed.Rev2` is not confirmed. |

### 10.4 Concept against optimization — `Tree.lean`, `Store.lean`, `Subsume.lean`, `RestrictedStore.lean`

| Theorem | Statement |
|---|---|
| `Tree.insert_mem`, `fromList_mem` | The Lean `EdgeTree` (a FLOW tree of §7.2: one premise, layer, exclusion and mark exclusion; `*` leaves are flags) holds exactly its path edges. |
| `Tree.rule1_mem`, `rule1_den`, `rule2_den`, `rule2_mark` | Merge rule 1 is exact; merge rule 2 (exclusions, and mark exclusions) is exact for EQUAL trees; counter-examples for different trees and for a union. |
| `Tree.applyTreeE_mem`, `applyTreeE_den`, `applyTreeE_grouped_key`, `applyTreeE_mx`, `applyTreeE_inv`, `applyTreeE_star_normal` | For a `*`-to-`*` micro edge with the mark `*` on both sides (not the mark gate or the cut of §7.3): the tree form of delta-concat equals the per-path form, fact and layer; output trees have distinct keys and keep the mark exclusion. |
| `Tree.fromList_size`, `fan_list`, `fan_tree`, `prepend_shares`, `walkSteps_le`, `applyListC_spec` | Cost of the tree representation. |
| `Store.*` | The record index (`byEntry`, `byExit`) and the mark-request index return every entry that the concept filter returns; lookup cost. The demand store of `Store.lean` (its part 4) and its subscription store (part 5) model the superseded version-2 designs; the demand store of the spec is the `RStore` index below, and its subscription index is `PipelineStore.deliver_run1`, `deliver_restricted` (§8.4). |
| `Subsume.subsumes_sound`, `markSubsB_sound` | The conclusion subsumption test is sound, with mark exclusions (`*∖Xs` subsumes `*∖Xn` if `Xs ⊆ Xn`). |
| `Subsume.merge_inter`, `merge_mark_inter`, `union_loses_pairs`, `union_marks_loses_pairs` | Merge rule 2 is exact for exclusions and for mark exclusions; a union loses a real pair. |
| `Subsume.record_subsumes`, `recordSubsumesB_sound`, `recordSubsumesLB_*` | Record subsumption. |
| `RStore.near_equiv`, `near_sound`, `emit_complete_M`, `emit_lookup_equiv_M`, `emitM_exact_prefix`, `restrict_complete_U`, `restrict_lookup_equiv_U`, `near_query_cost`, `deep_chain_cost` | The demand store: index = filter; complete for the emission and the restriction (the premise test of the earlier restriction, an overlap; the intersection needs less, §8.6); cost walk + length of the returned chains (not walk + count). |
| `RStore.restrictTreeE_mem_U`, `restrictTreeE_inv`, `restrictTreeE_mx`, `restrictTree_cost` | The restriction of a whole tree equals the per-path restriction (of the earlier restriction `restrictU`; the meet of the intersection at the node of `D-p.path` is argued, §7.4); one well-formed tree; it keeps the mark exclusion; cost walk + width, shared subtrees. |

### 10.5 Reversal — `Reverse.lean`

| Theorem | Statement |
|---|---|
| `revEdge_sound`, `revEdge_exact`, `rev_exact_of_empty_premise`, `rev_starEx_exact` | §9.1: sound for every mark-reversible record; exact with the Empty premise exclusion, also for a `*∖X` record. |
| `policy_premEmpty`, `answerInit_premEmpty`, `answerInit_markRev`, `revEdge_premise_mark` | Run-1 initial facts have the Empty premise exclusion; a reversed premise never has a mark exclusion. |
| `star_exact_no_exact_rev`, `no_rev_of_star_conc`, `no_rev_of_two_marks` | The limits of the reversal. |
| `Reverse.Stmt.rev_step_iff`, `flow_rev_iff_calls`, `rev_WF`, `backward_of_forward_calls` | The reversed program has the converse flow, with calls, cleaners and filters (under `RevStmts`, `RevCalls`: S11 (b), (g)); it is well-formed; the forward closure `D` on the reversed program (`Reverse.Program.rev`) is covered by the forward theorem. This is not a theorem about the backward run `Backward.DB`, which adds the zero rules and the seeds. |
| `backward_reuse`, `backward_reuse_needs_cover`, `backward_reuse_precise` | Reversed records cover the converse flows; the cover condition is necessary; `backward_reuse_precise`: an exact forward record with the Empty premise exclusion reverses into an exact record of the reversed program (under `RevStmts`, `RevCalls`). |

### 10.6 ND edges — `ND.lean`, `NDExact.lean`

| Theorem | Statement |
|---|---|
| `ND.nd_coverage` (+ `nd_coverage_single`, `nd_coverage_flow`, `nd_coverage_conc`) | THE ND COVERAGE THEOREM. For a support derivation `TaintN M n l L0` (the location `l` is tainted at `n` when the entry locations `L0` are) and initial facts lined up with `L0`, each covering its location: the edge keyed by exactly these initial facts covers `l` at `n` (with `den` for one premise), OR the closure has a request on one of them with the mark of its location. |
| `ND.nd_vuln`, `nd_vuln_root`, `nd_vuln_reach`, `nd_vuln_found` | A tree-shaped vulnerability witness gives a vulnerability. |
| `ND.ndConclusion_uncorrelated` | W7: an edge with two or more premises has no `*` tail and has a concrete mark. |
| `ND.answer_loop` | A request at one support position is answered or climbs; the loop over positions ends. |
| `ND.flow_taintN`, `D_sub_DN` | Every ordinary flow is a support derivation with one location; every object of `D` is in the ND closure. |
| `ND.Example.vuln3`, `c3_normal` | Today's `NDRule` sample (`$A = src(); $B = src(); $C = pass($A, $B); sink($C)`) gives the vulnerability, from a normal-layer conjunction. |
| `NDExact.nd_edge_exact`, `nd_edge_exact_valid`, `nd_edge_exact_gen`, `nd_edgeOK` | THE ND EXACTNESS THEOREM. Under S7 and S9, and either `Exact.FiltUp` (`NDExact.nd_edge_exact`) or S13 for valid end locations (`NDExact.nd_edge_exact_valid`: `Exact.FiltValid`, `Exact.BackOK` and `NDExact.ConjOK`): a normal-layer edge with the premise list `P` gives a support derivation `ND.TaintN M n l L0` for EVERY support `L0` that `P` covers and every end location `l` of the edge (with `den` for one premise). The converse of `ND.nd_coverage`. |
| `NDExact.nd_edge_exact_single`, `nd_edge_exact_nd` | The single-premise form (`den i f l0 l ⇒ TaintN … [l0]`) and the form for two or more premises (every covered support, every covered location). |
| `NDExact.CexLit.cex_lit`, `CexConjOK.cex_conjOK` | Both hypotheses are necessary: an abstract literal lets a correlated input with a mark exclusion through (the excluded locations reach nothing); without `ConjOK` a valid end location has an invalid support. |
| `NDExact.covers_nonempty` | Every fact covers some location (also `*/Universe` and `*∖X`), so a support can always be built. |
| `NDConfirmed.SupN`, `ConfirmedN`, `confirmed_real_N`, `confirmed_real_N_valid` | THE ND CONFIRMATION THEOREM. Under S7, S9 and S10 (`ND.NProg.WF`), and either `Exact.FiltUp` or S13 (`NDConfirmed.confirmed_real_N_valid`: `Exact.FiltValid`, `Exact.BackOK`, `NDExact.ConjOK`): a vulnerability whose sink edge is normal, whose premises are exact concrete facts, and whose premise set is supported JOINTLY (§4.9 condition 3: one call supplies every premise) is real for the support semantics (a tree witness `ND.ReachN`). |
| `NDConfirmed.CexSites.cex_sites` | Support of each premise on its own is not enough: two premises supplied at two different calls confirm a vulnerability that has no witness. |
| `NDConfirmed.confirmed_lift`, `confirmedN_iff`, `DN_lower`, `reachN_reach` | Without conjunctions the ND confirmation is the confirmation of §4.9 for one premise. |

### 10.7 Restricted runs and the iteration — `Restricted*.lean`, `Backward.lean`, `BackwardExact.lean`

The restricted closure `DR` (`Restricted.lean`) is the rule list of a restricted run (§6.1), with the rules `initR`,
`ret` and `retRec`. A demanded flow and a demanded witness (`FlowR`, `ReachR`) are defined in §1; in this earlier
model the `D-p` part reads the exit location without its mark (`PFact.coversLoc`), as the restriction `restrictU`
does (§1; the spec form `Handoff.FlowRR` reads it with its mark, `ap-history.md` F71). The coverage and
iteration theorems of this section are generic over the rules, given the contracts C2 (`EmitContractOn`), C4
(`SatContract`) and C5 in its earlier OVERLAP form (`RestrictContract`). They are about the EARLIER design: the
restriction `restrictU` and the hand-off of every summary edge (`Backward.revSummaryDemand`, `Backward.demOf`). They
stay in the model as its record. The spec restriction (the intersection) and the hand-off of the demand edges are
in §10.12 (`ap-history.md` F70). The exactness, record and confirmation theorems of this section that take a
restriction with `RestrictSub` hold for the spec restriction too (`Handoff.restrictI_sub`). All of them are about the
concrete restricted runs; for F72 see the intro of §10.

| Theorem | Statement |
|---|---|
| `RCore.emitM_contract_I`, `emitM_copies`, `satI_contract`, `emitM_satI`, `emitM_inter`, `emitM_complete`, `emitM_shape`, `emitM_chain_strong` | The spec emission satisfies C2 for concrete added facts and copies the mark (C3); the emitted fact is exactly `a ∩ D-c`, lies inside its added fact, and has the chain of the fact or the demand chain. Since F71 `markMatchB` reads a `*∖X` entry mark exactly, and the proof of `RCore.emitM_contract` takes its cell: a location that the entry pattern covers has a mark that is not in `X`. |
| `RCore.satI_markSub`, `satI_conc_record` | The satisfaction compares locations, and the marks of the fact against the marks of the premise: a cleaned fact reads a `*` premise, a concrete fact reads a `*`-premise summary. |
| `RCore.emitM_not_full_any` | C2 fails for a `*`-mark added fact under a `T` demand, for every satisfaction: the concreteness of the run is necessary. |
| `RCov.concInvR_all`, `added_concR`, `no_reqR`, `RExact.DR_concrete`, `DR_no_request`, `final_not_star`, `RMain.no_request_M` | A restricted run with a mark-copying emission is CONCRETE: every initial fact, edge and added fact has a concrete mark, no final fact has the `*` tail, and the run has NO request (also with cleaners). (The concrete design: the F72 emission does not copy the mark under a `*` pattern, and the concreteness claim is false for the F72 runs; they have no request rule, §4.5.) |
| `RCore.restrictU_fails`, `restrictS_contract`, `restrictU_eq_S_nonstar`, `RExact.restrict_U_eq_S` | The earlier restriction (`restrictU`) does not satisfy C5 as a function; the auxiliary restriction `restrictS` does; the two agree on every conclusion without the `*` tail, and with a mark-copying emission they give the same run. So the coverage theorem holds for the run with `restrictU`. (The spec restriction `Handoff.restrictI` satisfies C5 for a premise inside `D-c` directly, §10.12.) |
| `RExact.complete_premise_exact`, `complete_rev_exact` | A normal edge of a concrete run has a premise with the `$` tail, so its record reverses exactly. (False for the F72 runs: a normal edge can have a FLOW premise, §9.1.) |
| `RCases.p1_found_M`, `p2_found_M`, `p1_reachR_M`, `p2_reachR_M` | Run 3 (forward) reports programs 1 and 2, with the emission of §6.3 and the earlier restriction `restrictU`. (With the intersection: `HandoffRCases.p1_found_I`, `p2_found_I`, §10.12.) |
| `RCov.coverageR`, `reach_strongR`, `vuln_foundR` | THE COVERAGE OF A RESTRICTED RUN: for a demanded flow from a covered entry location, an edge covers the pair or the run has the request; its own summaries demand the same flow. |
| `RCov.coverageD`, `reach_to_reachR`, `vuln_foundD` | Run 1 reports every real vulnerability, and its summaries demand its witness. |
| `RCov.iteration_sound`, `iteration_sound_conc`, `RMain.iteration_sound_M`, `iteration_sound_M_identity` | THE ITERATION THEOREM: if every backward step satisfies the strong form of contract B (`BackwardContract`: every vulnerability witness that the summaries of a forward run demand stays demanded), EVERY forward run reports every real vulnerability; any field limits, any records. The form of the earlier §6.6 (`Backward.BackwardContractD`) is weaker and is enough (`Backward.iteration_sound_M_D`). |
| `RCov.backward_identity`, `backward_of_superset`, `RMain.noSink_contract`, `everyWitness_contract_fails` | The identity backward step satisfies B; B is about sink witnesses only. |
| `Backward.DB`, `demOf`, `revSummaryDemand` | The backward run of §9.2: the rules of `DR` on the reversed program, the zero fact from the roots, the zero binding into every callee, the seeds, the balanced return (generic in the emission, the satisfaction, the restriction, the demand and the records); the EARLIER hand-offs in both directions (every summary edge, in every layer, before the restriction; the spec hand-offs `Handoff.handF`, `demOfN` are in §10.12). |
| `Backward.B_general`, `iteration_general`, `BackwardContractD`, `repD_of_rep`, `iteration_sound_D`, `iteration_sound_M_D` | THE BACKWARD RUN SATISFIES THE CONTRACT, for the earlier hand-off: for every forward run, if the backward demand contains its reversed summaries and the seeds contain its reported sinks, the hand-off satisfies the mark-aware contract; so every forward run reports every real vulnerability. (For the spec hand-off: `HandoffBackward.B_generalN`, `HandoffMain.iteration_generalN`, §10.12.) |
| `Backward.BackwardContractRep`, `rep_of_B`, `iteration_sound_rep`, `B_fragment_rep` | The contract for reported vulnerabilities only, with the plain demanded witness; on programs where no call binds back the backward run satisfies it directly. |
| `Backward.dem1_exact`, `dem2_exact`, `p1_found`, `p2_found` | For programs 1 and 2 the hand-off of the backward run is exactly the callee demand of `RCases.dem1M` (`dem2M`), the zero demand `Backward.zeroDem`, and one pattern `(D-c, none)` of the root; forward run 3 reports the vulnerability. (Earlier hand-off. The hand-off of the demand edges contains the same callee patterns, `HandoffRCases.p1_handoff`, `p2_handoff`; that it is exactly these parts is checked by hand, §9.2.) |
| `Backward.lost_plain`, `B_fails_plain` | With the plain converse of the forward bindings (no zero binding into the callee) program 1 is lost. |
| `BExact.DB_concrete`, `DB_no_request`, `DB_no_requestM`, `CexSeed.cex_seed` | The backward run is concrete and raises no request when its seeds have concrete marks (a seed with the mark `*` would raise one). (The concrete design: the F72 backward run has `*` requirements, §9.2, and no request rule.) |
| `BExact.edge_exactB`, `edge_exactB_valid`, `edge_exactB_rev`, `summary_rev_flow`, `rev_record_exact`, `revRecs_exact`, `CexZeroBack.cex_rec` | A normal backward edge with a non-zero premise is exact for the reversed program and reverses into an exact forward record, under S11 (c); the reversal also under S11 (g) (`Reverse.RevStmts`, `RevCalls`) and with the records of the backward run exact off the zero base (`BExact.RecsExactNZ`); all for `Exact.FiltUp`, with no form for type filters; without S11 (c) it is false. |
| `BExact.recsSeq_exact`, `recsSeq_exactV`, `accRecs_exact`, `accRecs_exactM`, `seq_edge_exact`, `seq_confirmed_real` | The persisted forward records stay exact over the whole run sequence (S14 for the forward records: every record is an exit edge of an earlier forward run, `BExact.RecsFromRuns`); every normal edge and every confirmed vulnerability of every forward run is real. |
| `BExact.binv_all`, `no_static_rule_backward` | The backward run needs no static rule, if the reversed program satisfies S12 (a) to (d) and its seeds and records keep the invariant (`BExact.SeedsOK`, `StaticsIter.RecOK`); an `[any]` sink on a class position is outside it (precision only). |
| `RExact.edge_exactR_valid`, `closed_exactR_valid`, `recs_of_DR_valid`, `recs_of_D_valid` (valid locations, S13 and `RExact.RecsExactV`); `edge_exactR`, `complete_exactR`, `recs_of_DR`, `recs_of_D`, `closed_exactR`, `RMain.closed_records_exactM` (`Exact.FiltUp` and `RExact.RecsExact`); `RExact.recs_union` | Exactness and record reuse in forward restricted runs. Hypotheses: S7, the records that the run reads are exact (S14), every satisfaction reads the marks (`RExact.SatMark`), and the restriction only removes pairs (`RestrictSub`). The exit records of the run are exact again, and the union of exact record sets is exact. |
| `RExact.SupM`, `ConfirmedM`, `confirmed_realM_gen`, `confirmed_realM_gen_valid` (every satisfaction with `SatMark`, every restriction with `RestrictSub`, so also the intersection by `Handoff.restrictI_sub`); `RMain.confirmed_real_M`, `confirmed_real_M_valid` (the earlier restriction `restrictU`) | A confirmed vulnerability of a forward restricted run is real for the reference semantics (§3.5), under S7, S14 and `Exact.FiltUp` or S13 (`Exact.FiltValid`, `Exact.BackOK`); the support accepts the emitted exact fact. |

### 10.8 Statics — `Statics.lean`, `StaticsIter.lean`, `StaticsConfirmed.lean`

| Theorem | Statement |
|---|---|
| `Statics.coverageD`, `vulnD` | Under the construction rules (`SWF`), run 1 with the position request of §4.10 (`Design`) reports every real vulnerability. |
| `Statics.DS_edgeOK`, `edge_exactS`, `edge_exact_validS`, `complete_exactS` | Its normal edges denote only real flows. |
| `Statics.cinv_all`, `no_any_above` | No static fact with the `[any]` tail occurs above a static position; a static fact above a position that is not exact is an identity static `*` edge (the premise at the same path, the normal layer). |
| `Statics.gen_read_DS`, `gen_read_sreq` | A static read on the abstract static root gives no fact and raises the position request. |
| `Statics.CexAbove.y_vuln_normal`, `CexWide.w_vuln_normal`, `CexClean.deep_vuln_normal` | A write in the caller, a write in a callee and a cleaner in a callee: the vulnerability is found through a normal edge. |
| `Statics.CopyF2F.c_vuln_normal`, `DeepSink.e_vuln_normal`, `DeepSinkParam.p_vuln` | A pass rule between static fields; a sink below a static field (the ordinary mark request climbs to a static caller premise: a normal edge; to a parameter premise: the request chain, a demand edge, as for an instance field). |
| `Statics.abovePos_len`, `f2f_not_above` | A path above a static position has at most one accessor; a field-to-field edge never lands above a static position. |
| `Statics.CexClean.shallow_misses`, `CexAbove.cex_user_misses`, `CexWide.counterexample` | The rule variants that fail: the chain answer of a static mark request; the narrow climb without a fallback; the first fire (the static root, reads only) with the fallback only for caller premises off the static base (`CexWide.Xn`, also with the at-or-below answer, `CexWide.Xc`). |
| `Statics.CexAny.counterexample` | Not a failing variant: the at-or-below answer, which the final rule uses (§4.10 item 2; `Statics.Design`, part `below`; in the program `CexAny.Xb`, `CexAny.Xc`, with the first fire, `gen = false`), loses a flow WHEN a source puts an `[any]` fact on a bare class (`(S, <C>, [any], T)`). This is the reason for S12 (b): the interpreter rejects such a source (`interpreter.md` §1.4). |
| `StaticsIter.rinv_all`, `no_any_above_R`, `static_step_below`, `static_sink_below`, `no_request`, `DeepReadIter.deep_read_above` | AFTER RUN 1 NO STATIC RULE IS NEEDED (the concrete design; with FLOW premises on `S`, F72, PENDING, §4.10): in a forward restricted run (`DR`), under S12 (a) to (d) (`StaticsIter.SWFR`) and persisted records that keep the static invariant (`StaticsIter.RecOK`), for every demand, no static `*` or `[any]` fact lies above a static position; every static operation at most two accessors deep is the case at or below; no request. A deeper static read or sink follows the ordinary rules of an instance field (`DeepReadIter.deep_read_above`: a restricted run can hold `(S, <C>.f, [any], T)` above a deep read). |
| `StaticsIter.reach_strongDSD`, `iteration_general_DS`, `no_static_rule_after_run1` | The iteration from run 1 = `DS`, with plain forward restricted runs after it: every forward run reports every real vulnerability, and every later forward run satisfies the invariant (under `StaticsIter.RecOK` for the records). The static invariant of the backward run `Backward.DB`: `BExact.no_static_rule_backward` (§10.7, with its hypotheses). |
| `StaticsIter.ExampleIter.run3_confirmed`, `WideIter.run3_confirmed`, `AboveIter.run3_confirmed`, `CleanIter.run3_confirmed`, `ExampleIter.demE_exact` | The worked static programs: forward run 3 confirms the vulnerability through a normal edge with no request; the exact demand for `Example`. |
| `StaticsConfirmed.SupS`, `ConfirmedS`, `confirmed_realS`, `confirmed_realS_valid` | Run 1 with the static rule confirms only real vulnerabilities; the support accepts the mark answer on a static premise (§4.10 item 4). |
| `StaticsConfirmed.confirmedS_iff`, `CexS.cexS_filt`, `CexS.cexS_mark`, `ExampleConf.confirmed`, `CleanConf.confirmed` | Without a static initial fact the static confirmation is the plain one; S7 and the validity are necessary; the worked programs are confirmed in run 1. |

### 10.9 Source seeds — `ForwardSeeds.lean`, `PipelineSeeds.lean`

| Theorem | Statement |
|---|---|
| `FSeeds.keepSources`, `mem_keepStmt`, `unseeded_dropped`, `cond_kept`, `zero_kept` | The program of a forward restricted run (§6.1 rule 6): every unseeded unconditional source edge is dropped; the conditional sources and the zero keep edge stay. |
| `FSeeds.keep_WF`, `keep_bindStar`, `keep_markRev`, `keep_noZeroBack`, `keep_zeroKept`, `keep_exitReach` | The seeded program keeps every hypothesis of `Backward.iteration_general`. |
| `FSeeds.flow_keep`, `reach_keep`, `reachRD_keep`, `flow_keep_mono`, `flow_keep_all_iff` | The seeded program has fewer flows than `P`; more seeds give more flows; seeding every source gives the flows of `P`. |
| `FSeeds.srcHit`, `srcHit_applies` | The source hits of a backward run (§9.2); a hit is a reversed source edge that applies to a concrete requirement. |
| `FSeeds.demanded_src`, `B_src` | Contract B with item 3 (§6.6): the demanded witness is a witness of the program seeded by every superset of the source hits. |
| `FSeeds.iteration_src`, `runSeqSrc` | The iteration with source seeds: every forward run reports every real vulnerability of `P`. |
| `PipelineSeeds.driver_iteration_src` | The same for the driver of `analyzer-core.md` (§7.7). |

### 10.10 Conclusion kinds and the zero-drop — `Kinds.lean`, `NDZ.lean`, `NDZero.lean`, `NDZeroThms.lean`, `NDZeroBase.lean`

| Theorem | Statement |
|---|---|
| `Kinds.flow_abstract`, `flow_no_exact`, `flow_no_exact_gen`, `taint_concrete`, `kinds_D` | §7.2 for run 1 without the static rule (the closure `D`): a `*` premise gives an abstract conclusion mark and no `$` tail (FLOW); a concrete or zero premise gives a concrete mark and no `*` tail (TAINT); a `*` tail is abstract and normal (W2). Hypotheses: S7 (`Exact.MarkWF`), S8 (`Invariant.no_univ_star` conditions), `Kinds.ExactTargetConc` (S8 duty), the run-1 policy (`Kinds.InitK`). |
| `Kinds.CexK.cex_etc`, `cex_premConc`, `cex_noUniv`, `cex_markWF`, `cex_alpha` | Each hypothesis is necessary: without it a FLOW edge gets a `$` tail or a concrete mark. |
| `Kinds.nd_taint`, `ndz_taint`, `CexND.*`, `ZeroMembers.*` | §4.6: an edge with two or more premises is TAINT: every member concrete, a concrete conclusion with no `*` tail; in `NDZ.DNz` also no zero member. |
| `Kinds.kinds_DR`, `kinds_DR_emitM`, `kinds_DB`, `kinds_DB_taint`, `CexSeedTail.cex_seed_tail` | The restricted runs have REACH and TAINT only; the backward tails need `SeedTails` (S11 (f)). (The concrete design: since F72 a restricted run has FLOW trees, §7.2.) |
| `NDZ.DNz`, `dropZ`, `zStar` | The closure of the spec (§4.6): `ND.DN` with the union of the premise sets WITHOUT the zero fact at a conjunction and at an ND summary application. |
| `NDZero.zero_everywhere`, `zero_everywhere_z` | The zero fact is at every node that an edge reaches, in `ND.DN` and in `NDZ.DNz`. Hypotheses: `Backward.ZeroKept` (S11 (d)), `NDZero.ZeroCalls` (§3.5: no call touches the zero base; the zero binding), `ConjAdj` (a conjunction beside an instruction edge), the policy serves the zero fact by itself. |
| `NDZero.dnz_to_dn`, `dn_to_dnz`, `edge_iff`, `ninit_iff`, `nadded_iff`, `nreq_iff` | THE CORRESPONDENCE: a `NDZ.DNz` edge with the premise set `P'` is a `ND.DN` edge with a list `P` with `NDZ.dropZ P = P'` and the same fact; a NORMAL `NDZ.DNz` edge comes from a NORMAL `ND.DN` edge. The converse (`dn_to_dnz`) needs `NDZero.ZeroLinks`, which `zeroLinks_of_noZeroGen` gives from `NDZeroBase.NoZeroGen` (§4.6; `interpreter.md` I11 (c), (d), §3.1, §5.3). |
| `NDZero.summary_nd_fact`, `summary_nd_exists` | An ND conclusion applied by the single-premise `ret` (a `{zero, i}` summary of the model is the summary `{i}` of the spec) gives the same fact, in a layer that is not lower. |
| `NDZeroThms.nd_coverage_z`, `nd_coverage_zg`, `nd_vuln_found_z`, `nd_vuln_found_zg`, `nd_vuln_reach_z` | Coverage and the vulnerability theorem for `NDZ.DNz`. |
| `NDZeroThms.nd_edge_exact_z`, `nd_edge_exact_valid_z` | Exactness of a normal `NDZ.DNz` edge: a support derivation with the support of `P'` plus zero locations. |
| `NDZeroThms.ConfirmedNz`, `confirmedNz_N`, `confirmed_real_Nz`, `confirmed_real_Nz_valid` | The confirmation of §4.9 on the `NDZ.DNz` premise set: it gives the confirmation of the `ND.DN` vulnerability (a zero member is supplied by the caller zero edge at the same call, `supN_zero`), so a confirmed vulnerability is real (`ND.ReachN`). |
| `NDZeroBase.zero_base_inv`, `dnz_applicable_zero` | Under `NoZeroGen` and `AlphaZero` (the run-1 policy satisfies it, `NDZeroBase.policy1_alphaZero`), every `NDZ.DNz` fact on the zero base is the zero fact, so only the zero premise is satisfied by the zero added fact. |

### 10.11 The `[any-taint]` tail — `AnyTaint*.lean`, `PipelineAnyTaint*.lean`

The model has one tail kind `.any` for both any tails (§11.2 gives the correspondence): a normal `.any` conclusion is
`[any-taint]`, a demand `.any` conclusion is `[any]`, and a premise with the flag `must` is an `[any-taint]` premise.
The exclusion of `[any-taint]/E` is the field `ex` of an annotated conclusion (`AnyTaintEx.XFact`) and the field `jex`
of a premise (`AnyTaintEx.XObj`). The base closures `D` and `DR` apply no W6, and every normal edge of them is exact,
normal `.any` edges included (§10.3, §10.7).

THE SPEC CLOSURES are the refined closures of round 2 (`AnyTaintExDefs.lean`, namespace `AnyTaintEx`):

* `AnyTaintEx.D6X`: run 1 with W6T (`w6tX`) and the exclusion: the core operation `applyEdgeX` (the base
  `applyEdge` with the annotation rules `annX` and the layer rule `layerX`, §4.1), the field limit `limitFX` (§4.4),
  the cleaner `cleanResX` (§4.7), the sink check `checkX` (§4.9);
* `AnyTaintEx.DRX`: a forward restricted run over the objects `XObj` with must flags and exclusions, the start fact
  `startX` (§6.5) and the record demotion `recLayerX` (§4.3); the emission, the satisfaction and the restriction are
  parameters;
* `AnyTaintEx.DRXs`: `DRX` with the rules `emitX` (§6.3), `satX` (§4.3) and the EARLIER restriction `restrictX`.
  The spec restriction of §6.4 is the intersection `HandoffX.restrictIX` (§10.12): the spec forward restricted run is
  `AnyTaintEx.DRX` with `emitX`, `satX` and `HandoffX.restrictIX`. Every theorem below that takes the restriction as a
  parameter with `AnyTaintExExact.RestrictOKX` holds for it (`HandoffX.restrictIX_ok`); the theorems stated for
  `AnyTaintEx.DRXs` (the names with `Xs`, and `AnyTaintExExact.specX_rules`) are for the earlier restriction.

The backward run stays `Backward.DB` (no `[any-taint]`, W8 (d)). It reads a forward run with the exclusions and the
must flags dropped (`AnyTaintExCov.forget6`, `forgetX`).

THE ROUND-1 CLOSURES (`AnyTaintDefs.lean`, namespace `AnyTaint`) model the first F69 text, which demoted an
`[any-taint]` fact at an exclusion: `AnyTaint.D6T` (run 1 with W6T: `w6t`, `transferT`) and `AnyTaint.DRT` (a forward
restricted run over `TObj` with must flags: `emitT`, `startT`, `recLayer`). They are not the spec closures. This spec
cites a round-1 result only where it states a rule of the amended design: the rule W6T, the must flags and the record
demotion, the conjunction rule (`AnyTaintND`) and the round-1 counterexamples; and, next to the refined theorem, for
contrast. The kinds invariant of run 1 and the premises of a normal restricted edge are proved for the spec closures
(`AnyTaintExKinds`, below). The round-1 programs with no exclusion (G, C, I and P) are re-derived in the spec closures
(`AnyTaintExCases2`, below; run 3 with the earlier restriction `AnyTaintEx.DRXs`); the round-1 names of their results (`AnyTaintCases`) are the model of the first F69 text
only. A refined object has a base object of the round-1 closure (the relations `AnyTaintEx.Refines6`, `RefinesR`): the
simulations `AnyTaintEx.Sim6X` and `SimRX` are proved (`AnyTaintExKinds.sim6X` under `AnyTaintEx.NoBelowCleaner`, and
`simRX` also under `AnyTaintEx.RecsRefine`), but no spec claim needs them: each refined run justifies its own
witnesses (§6.6). (The docstrings of `AnyTaintSim` name the round-1 decisions; the statements are those of the tables
below.)

The hypotheses of round 2, each with the spec item that makes it true: `AnyTaintEx.SatInsideX` (the satisfaction reads
`inside` with the exclusions, §4.3; `satX_inside`), `AnyTaintEx.EmitCopiesMarkX` (C3; `emitX_copies`),
`AnyTaintExExact.RestrictOKX` (the restriction keeps the normal form of W8, §6.4; for the spec restriction
`HandoffX.restrictIX_ok`, for the earlier one `AnyTaintExExact.restrictX_ok`; it replaces `AnyTaintEx.RestrictSubX`, which is
false for `restrictX`), and for the records `AnyTaintEx.RecsExactX`,
`AnyTaintExExact.RecsConcX` and `RecsWFX` (S14; the run sequence discharges them). The hypotheses
`AnyTaint.TaintConc` (S15) and `BindNoAny` (S10) serve only the kinds invariant of run 1 (`AnyTaintExKinds.kinds_D6X`;
round 1 `AnyTaintSim.kinds_D6T`), and `W6.SummaryStar` only the round-1 simulation of run 1 (the run-1 policy has it,
`W6.summaryStar_policy`; the refined kinds theorems do not need it). The simulations of the refined runs need
`AnyTaintEx.NoBelowCleaner` (no `below` cleaner: its new `$` fact has no base object) and, for a restricted run,
`AnyTaintEx.RecsRefine`. No theorem has a hypothesis on the choice of the taint edges, except the kinds invariant.

RUN 1 (`AnyTaintEx.D6X`):

| Theorem | Statement |
|---|---|
| `AnyTaintExCov.applyEdgeX_sound`, `annX_admits`, `transferX_sound`, `bind_inX`, `bind_outX`, `applySummaryX_sound`, `limitFX_sound`, `startX_sound`, `checkX_sound`, `cleanResX_sound`, `partX_sound` | THE LOCAL SOUNDNESS: every refined operation covers the composition of the fact relation and the edge relation, and the exclusion of its result admits every reached end location (the core lemma of §4.1 with the exclusion). |
| `AnyTaintExCov.coverage6X`, `reach_strong6X`, `vuln_found6X`, `vuln_found_policy6X` | COVERAGE and the vulnerability theorem of run 1: every real vulnerability is reported, in some layer. For the policy the only hypothesis is S10. |
| `AnyTaintExExact.applyEdgeX_exact`, `applyEdgeX_cov`, `keep_row_exact`, `above_row_exact`, `below_row_exact`, `transferX_exact`, `applySummaryX_exact`, `cleanResX_exact`, `clean_rows_exact`, `below_new_fact_real` | THE LOCAL EXACTNESS: a normal result is exact on the admitted locations. The rows of W8 stay in the normal layer and are exact: the exclusion edge at `r = []`, the case `above` with the edge exclusion, the case `below` that keeps `E`, and the cleaners `atAndBelow` and `below` one accessor below the fact; the new `$` fact of the `below` cleaner is real. |
| `AnyTaintExExact.D6X_wf`, `D6X_NS`, `D6X_edgeOK`, `edge_exact6X`, `edge_exact_valid6X` | EXACTNESS OF RUN 1: every edge conclusion is in the normal form of W8; every pair of a normal edge, also `[any-taint]/E` with its exclusion read, is a concrete flow (under S7 and `Exact.FiltUp`; the valid form under S7 and S13). |
| `AnyTaintExExact.sup_entry6X`, `sup_entry6X_valid`, `confirmed_real6X`, `confirmed_real_valid6X`, `confirmed_real_valid6X_of`, `sink_denX` | CONFIRMATION IN RUN 1 (§4.9): a vulnerability confirmed on a NORMAL sink edge (`AnyTaintEx.Confirmed6X`: also `[any-taint]/E`, and the sink pattern meets an admitted location) under a supported premise (`Sup6X`) is real (under S7 and `Exact.FiltUp`; the valid form under S7 and S13, with a sink pattern of valid locations). |
| `AnyTaintExKinds.D6X_any_conc`, `D6X_flow_no_any_taint`, `D6X_flow_no_excl`, `kinds_D6X`, `kinds_D6X_gen`; `AnyTaintSim.CexKinds.cex_taintConc`, `cex_bindNoAny` | THE KINDS INVARIANT (§7.2, W8 (a)) of `AnyTaintEx.D6X`, by a direct induction (not through a simulation): under `AnyTaint.TaintConc` (S15) and `AnyTaint.BindNoAny` (S10) every normal `.any` conclusion (`[any-taint]/E`) has a concrete mark, for every abstraction; so a FLOW edge has no `[any-taint]` conclusion (`AnyTaintExKinds.D6X_flow_no_any_taint`, also under S7; no `W6.SummaryStar`) and carries no exclusion (`AnyTaintExKinds.D6X_flow_no_excl`); with the hypotheses of `Kinds.kinds_D`, the partition of run 1 (`AnyTaintExKinds.kinds_D6X` for the policy, `kinds_D6X_gen` for every `Kinds.InitK` abstraction). The `below` cleaner's new `$` fact has the concrete mark of its `[any-taint]` fact, so it touches no claim. Both hypotheses are necessary. |
| `AnyTaintSim.D6T_any_conc`, `D6T_flow_no_any_taint`, `kinds_D6T` | ROUND 1, THE KINDS INVARIANT of `AnyTaint.D6T`: the same claims; `AnyTaintSim.D6T_flow_no_any_taint` also under `W6.SummaryStar`, which its simulation needs. |
| `AnyTaintExKinds.sim6X`, `sim6XIn`, `D6X_abs_same`, `applyEdgeX_LE`, `annX_geo_ex`, `RelA` | THE SIMULATION OF RUN 1 (`AnyTaintEx.Sim6X`, `Sim6XIn`) under `AnyTaintEx.NoBelowCleaner`: every object of `AnyTaintEx.D6X` has an `AnyTaint.D6T` object with the same base fact (`AnyTaintEx.Refines6`); an abstract-mark edge of `D6X` is a `D6T` edge with the same fact and layer (`AnyTaintExKinds.D6X_abs_same`). A non-empty result exclusion comes from a base demotion or from an exclusion of the input, the premise or the target (`AnyTaintExKinds.annX_geo_ex`). No spec claim needs it. |
| `AnyTaintSim.D_le_D6T`, `D6T_le_D`, `D_le_D6T_1`, `D6T_le_D_1`, `D6T_same_shape`, `D6T_normal`, `coverage6T`, `vuln_found_policy6T`, `edge_exact6T`, `edge_exact_valid6T`; `AnyTaintSim.CexT.cex_w6t_changes_fact`; `AnyTaintExact.D6T_edgeOK`, `recs_of_D6T_valid`, `confirmed_real_valid6T` | ROUND 1, THE RULE W6T without the exclusion (`AnyTaint.D6T`): `D6T` has the objects of `D`, each edge with the same fact in the same or a raised layer (hypothesis `W6.SummaryStar`; the forms `_1` for the run-1 policy with no hypothesis); without `SummaryStar` W6T changes a fact. Its coverage (S10 for the policy), its exactness (the `AnyTaintSim` forms under `W6.SummaryStar` too), its records and its confirmation. |
| `AnyTaintSim.D6_le_D6T`, `D6_normal_D6T`, `D6_normal_D6T_1` | ROUND 1: W6T is not less precise than the old rule W6: every normal edge of `W6.D6` is a normal edge of `AnyTaint.D6T` (for an abstraction that gives `$` only with a concrete mark; the forms `_1` for the run-1 policy). |

THE RESTRICTED FORWARD RUN (`AnyTaintEx.DRX`, with the rules as parameters; the names with `Xs` and
`AnyTaintExExact.specX_rules` are for `AnyTaintEx.DRXs`, the earlier restriction). These theorems are about the
concrete restricted runs (the hypothesis `AnyTaintEx.EmitCopiesMarkX`, C3 of the concrete design, or the rules
`emitX`, `satX`); for F72 they are PENDING (the intro of §10):

| Theorem | Statement |
|---|---|
| `AnyTaintExCov.concX_all`, `noReqX`; `AnyTaintExExact.DRX_wf`, `DRX_mustAny`, `DRX_conc` | CONCRETENESS (under C3): every premise, conclusion and added fact has a concrete mark; no request; no `*` final tail; every conclusion is in the normal form of W8; every must-premise has an any tail. (The concreteness claim is false for the F72 runs.) |
| `AnyTaintExKinds.DRX_normal_premise`, `DRX_must_premise`, `DRX_premLayer`, `DRXs_normal_premise`, `DRXs_must_premise` | THE PREMISE OF A NORMAL EDGE (§9.1; the form of `RExact.complete_premise_exact` for `AnyTaintEx.DRX`): a normal edge has a `$` premise that is not a must-premise, or a must-premise, which has the `.any` tail and a concrete mark (`[any-taint]`, with or without an exclusion); never a non-must `.any` or a `*` premise. Hypothesis `AnyTaintEx.EmitCopiesMarkX` (C3, which `AnyTaintEx.emitX_copies` gives for the spec run); for the earlier instance `DRXs` none (`AnyTaintExKinds.DRXs_normal_premise`, `DRXs_must_premise`). (False for the F72 runs: a normal edge can have a FLOW premise.) |
| `AnyTaintExKinds.simRX`, `simRXIn` | THE SIMULATION OF A RESTRICTED RUN (`AnyTaintEx.SimRX`, `SimRXIn`) under `AnyTaintEx.NoBelowCleaner` and `AnyTaintEx.RecsRefine`: every object of `DRXs` has an object of the spec instance of `AnyTaint.DRT` with the same base fact (`AnyTaintEx.RefinesR`). No spec claim needs it. |
| `AnyTaintExCov.emitX_contract`, `SatContractX`, `satX_contract`, `RestrictContractNSX`, `restrictX_contractNS`, `restrictX_not_contract` | THE CONTRACTS of §6.1 with the exclusions: C2 for `AnyTaintEx.emitX`, C4 for `satX`; C5 in the earlier overlap form for the earlier restriction `restrictX` on every conclusion without the `*` tail, the full form false, as for `restrictU`. (The spec restriction `HandoffX.restrictIX` satisfies C5 for a premise inside `D-c` on every conclusion, §10.12.) |
| `AnyTaintExCov.coverageRX`, `coverageRX_summary`, `reach_strongRX`, `vuln_foundRX`, `coverageRXs`, `vuln_foundRXs` | COVERAGE relative to the demand (§10.7) and the vulnerability theorem; with the earlier rules (`coverageRXs`, `vuln_foundRXs`) under S10 only. (The spec run with the intersection: `HandoffX.coverageRXI`, `coversN_DRXI`, §10.12.) |
| `AnyTaintExExact.DRX_edgeOK`, `summaryX_pair`, `summaryX_end`, `startX_must_end`, `edge_exactX_gen`, `edge_exactX`, `edge_exactX_valid` | THE EXACTNESS INVARIANT (`AnyTaintEx.EdgeOKX`): a normal edge of a premise that is not a must-premise is exact pair by pair on the admitted locations; a normal edge of a must-premise is END-EXACT on the admitted locations (`AnyTaintEx.EndExactX`). Hypotheses: S7, `Exact.FiltUp` or S13 (`Exact.FiltValid`, `Exact.BackOK`), `AnyTaintEx.SatInsideX`, `AnyTaintExExact.RestrictOKX`, `AnyTaintEx.EmitCopiesMarkX`, the records `AnyTaintEx.RecsExactX` with `AnyTaintExExact.RecsConcX` and `RecsWFX`. A must record applied by `applicable` only gives a demand result, so it needs no exactness. |
| `AnyTaintExExact.recs_of_DRX`, `recs_of_DRX_valid`, `recs_of_DRX_gen`, `recsConc_of_DRX`, `recsWF_of_DRX` | THE RECORDS (S14, §8.7): the exit edges of an `AnyTaintEx.DRX` run, with their must flags and exclusions, are exact or end-exact records, with concrete must records, in normal form. |
| `AnyTaintExExact.restrictX_ok`, `restrictOKX_of`, `specX_rules`; `AnyTaintEx.satX_inside`, `emitX_copies` | THE RULES have the three properties: `emitX` copies the mark, `satX` reads `inside` with the exclusions, the earlier restriction `restrictX` keeps the normal form, the layer, and only removes pairs (`specX_rules`: the earlier rules). The spec restriction has the third property too (`HandoffX.restrictIX_ok`, §10.12). |
| `AnyTaintExExact.sup_entryX`, `confirmed_realX`, `confirmed_realX_valid`, `confirmed_realX_valid_of`, `confirmed_realXs` | THE SUPPORT AND THE CONFIRMATION (§4.9): every admitted location of a supported premise (`AnyTaintEx.SupX`, `SupLinkX`: the same concrete mark, `inside` with the exclusions) is entry-reachable, ALL admitted locations of a must-premise; a vulnerability confirmed on a normal sink edge under a supported premise (`AnyTaintEx.ConfirmedX`) is real. Hypotheses as `AnyTaintExExact.edge_exactX`; for the earlier rules `confirmed_realXs`; for the spec rules through `HandoffX.restrictIX_ok`. |
| `AnyTaintExExact.runX`, `liftRecsX`, `liftRecsX_exact`, `liftRecsX_conc`, `liftRecsX_wf`, `RecsFromRunsX`, `recsSeq_exactX`, `recsSeq_exactX_valid`, `seq_confirmed_realX`, `seq_confirmed_realX_valid` | THE RUN SEQUENCE: if every record is an exit edge of run 1 or of an earlier restricted run of the same program (`RecsFromRunsX`), every record set is exact with concrete must records in normal form, and every confirmed vulnerability of every restricted run is real, with no exactness hypothesis on the records. The reversed backward records and the seeded runs with exclusions are argued (§11.2). |
| `AnyTaintSim.factSim`, `factSim_M`, `DRT_flag_swap`, `DRT_must_any`, `kinds_DRT`; `AnyTaintExact.DRT_edgeOK`, `edge_exactT_valid`, `confirmed_realT_valid`, `RecsFromRunsT`, `seq_confirmed_realT_valid` | ROUND 1, THE MUST FLAGS without the exclusion (`AnyTaint.DRT`): `DRT` has the facts of `DR` (hypotheses `EmitCopiesMark`, `AnyTaint.RestrictFact`); the must flag of a premise changes only layers; a non-must edge is exact pair by pair, a must edge END-EXACT (`AnyTaint.EdgeOKT`, `EndExact`); the confirmation, also over the run sequence when every record is an exit edge of an earlier forward run (`AnyTaintExact.RecsFromRunsT`). |

THE COUNTEREXAMPLES (each one shows that a rule of the amended design is necessary):

| Theorem | Statement |
|---|---|
| `AnyTaintExExact.CexAbove.cex_above` | The check of the exclusion in the case `above` (§4.1): without it the read of the field that a setter wrote gives a normal edge that no flow has. |
| `AnyTaintExExact.CexExactCleaner.cex_exact_cleaner` | The `exact` cleaner one accessor below an `[any-taint]` fact must demote it (§4.7): `[any-taint]/{f}` would miss the real `x.f.g` (a soundness loss), and a normal `[any-taint]/{}` would claim the cleaned `x.f` (not exact, `¬ AnyTaintEx.EdgeOK6X`). |
| `AnyTaintExExact.CexRestrictSub.cex_restrict_sub` | The form `AnyTaintEx.RestrictSubX` is false for `restrictX` on a conclusion that is not in normal form: so the hypothesis is `AnyTaintExExact.RestrictOKX`. |
| `AnyTaintExExact.CexSideConditions.cex_htx`, `cex_hfx` | The two side conditions of the local lemma `applyEdgeX_exact` are necessary; the closures give them. |
| `AnyTaintExCov.CexRoute.route_a_false`, `rx_no_y` | The hand-off of a refined run can be SMALLER than that of the round-1 run on the same demand (the exclusion removes summaries), so the iteration is proved directly (§6.6). |
| `AnyTaintEx.Vec.below_keeps_loc` | The case `below` does not test `r` against the exclusion of the fact: such a test would lose a real flow (§4.1). |
| `AnyTaintExact.CexApp.cex_app`, `CexApp.record_not_pair_exact`, `CexRev.cex_rev`, `CexSupMark.cex_sup_mark`, `CexRecConc.cex_rec_conc` | ROUND 1: without the record demotion a must record applied by `applicable` gives a false normal edge (§4.3); a must record is end-exact, not exact pair by pair; the reversal of a must record is not a converse flow (§8.7 R3); without the same-mark condition `AnyTaint.SupLink` accepts a premise with the mark `*` (§4.9; a concrete premise has the condition, `AnyTaintExact.markSub_conc`); the concrete mark of a must record (`RecsConcT`) is necessary. |
| `AnyTaintCases.W.keep_normal_false`, `keep_normal_not_endExact`, `keep_normal_confirms_unreal` | ROUND 1: a normal keep result WITHOUT the exclusion is false (it confirms the overwritten field, which is not real): so the exclusion edge must carry the written field (§4.1). |

THE ITERATION AND THE BACKWARD RUN:

| Theorem | Statement |
|---|---|
| `AnyTaintExCov.runSeqX`, `forget6`, `forgetX`, `iteration_invariantX`, `iteration_generalX`, `iteration_reportsX`, `runSeqSrcX`, `iteration_src_invariantX`, `iteration_srcX` | THE ITERATION (§6.6): run 1 `AnyTaintEx.D6X`, the backward runs `Backward.DB` (restricted by the hand-off of each forward run, read with the exclusions and the must flags dropped), the forward restricted runs `AnyTaintEx.DRXs`: EVERY COMPLETE FORWARD RUN REPORTS EVERY REAL VULNERABILITY, in some layer (`AnyTaintExCov.iteration_reportsX` is the form to cite for the EARLIER hand-off and the earlier restriction). Hypotheses: those of `Backward.iteration_general` (with the source seeds those of `FSeeds.iteration_src`); none on the taint edges or on the records. The spec hand-off and restriction: `HandoffXIter.iteration_generalNX`, §10.12. |
| `AnyTaintSim.startFact_any_demand`, `DB_any_premise_demand`, `seed_any_normal` | THE BACKWARD ANY TAIL (§9.2): every backward edge of an any-tail premise is a demand edge (under C3 and `BExact.SeedsConc`); the model keeps an `[any]` sink seed within the field limit as a normal `.any` edge, so the backward W6 is argued (§11.2). |
| `AnyTaintSim.backRecT`, `backRecT_sub`, `backRec_exact`, `backRec_exactM`, `backRec_flow`, `recsDR_liftRecs` | THE BACKWARD RECORDS (§8.7 R1): a normal backward summary with a non-zero premise and no any tail, reversed, is an exact forward record (the hypotheses of `BExact.revRecs_exact`); a forward run that reads such records has the facts of `DR`. |
| `AnyTaintSim.revSummaryDemandT_iff`, `backward_reads_sameT`, `revSummaryDemand6T_iff` | ROUND 1, THE FORWARD LABEL: the backward run read the same patterns from `AnyTaint.DRT` as from `DR`, and from `AnyTaint.D6T` as from `D`: the forward must flags were a label for the hand-off (with the emission of §6.3, which reads no pattern layer, the reason for W8 (d)). |
| `AnyTaintSim.runSeqT`, `iteration_generalT`, `iteration_reportsT`, `runSeqSrcT`, `iteration_srcT` | ROUND 1: the iteration with run 1 `AnyTaint.D6T` and the forward restricted runs `AnyTaint.DRT`. |

THE CONJUNCTION (`AnyTaintND`, the rule of §4.6 for an `[any-taint]` input; `ap-history.md` F69). The closure
`AnyTaintND.DNzT` has the conjunction rule but neither W6T nor the exclusion: with them the rule is argued (§11.2).

| Theorem | Statement |
|---|---|
| `AnyTaintND.conjLayerT`, `DNzT`, `dnz_to_dnzT`, `dnzT_to_dnz`, `conj_results_corr` | The rule of §4.6: a normal `[any-taint]` input that overlaps its literal keeps the result normal. `AnyTaintND.DNzT` (`NDZ.DNz` with `AnyTaintND.conjLayerT`) has the facts of `NDZ.DNz`, each edge in the same or a lower layer. |
| `AnyTaintND.nd_coverage_zT`, `nd_coverage_single_zT`, `nd_coverage_zgT`, `nd_vuln_found_zT`, `nd_vuln_reach_zT`, `nd_vuln_found_zgT` | Coverage and the vulnerability theorems of `NDZeroThms.lean` for `AnyTaintND.DNzT`. |
| `AnyTaintND.lit_loc`, `nd_edgeOKT`, `nd_edge_exact_genT`, `nd_edge_exact_zT`, `nd_edge_exact_valid_zT` | EXACTNESS of a normal `AnyTaintND.DNzT` edge against `ND.TaintN`, under the hypotheses of `NDZeroThms.nd_edge_exact_z` and `nd_edge_exact_valid_z`. The new case (`AnyTaintND.lit_loc`): an `[any-taint]` input that overlaps its literal and passes the mark gate has a location in the literal. |
| `AnyTaintND.ConfirmedNzT`, `confirmed_real_NzT`, `confirmed_real_NzT_valid_of`, `confirmed_real_NzT_valid`, `confirmedNz_confirmedNzT` | CONFIRMATION with a normal sink edge (also `[any-taint]`), under the hypotheses of `NDZeroThms.confirmed_real_Nz`, `confirmed_real_Nz_valid`; every vulnerability that the old rule confirms, the new rule confirms. |
| `AnyTaintND.Example.layer_new`, `layer_old`, `c3`, `vuln3`, `confirmed`, `real`, `old_not_confirmed` | `x = srcAny(); y = src();` and the conjunction `(x, .f, $, T) ∧ (y, ., $, U) → (z, ., $, V)`: the result is normal with the new rule and demand with the old one; the vulnerability is confirmed and real; the old rule does not confirm it. W6T is not part of `DNzT` (argued, §11.2). |

THE WORKED PROGRAMS OF ROUND 2 (`AnyTaintExCases`: derivations and complete runs in `AnyTaintEx.D6X` and
`AnyTaintEx.DRXs`, the earlier restriction; on run 3 of program B the intersection gives the same cells, checked by
hand) AND THEIR VECTORS:

| Theorem | Statement |
|---|---|
| `AnyTaintExCases.S.record_app`, `keep_forms`, `run1_record`, `run1_dto_ann`, `run1_name_no_trigger`, `run1_name_not_reported`, `run1_email_normal`, `run1_email_confirmed`, `run1_no_demand`, `name_not_real`, `email_real`; `inv1T`, `run1T_vulns`, `run1T_no_normal`, `run1T_not_confirmed`, `refines_dto` | PROGRAM S (the setter): `root() { dto = srcAny(); dto.setName(c); sink(dto.name); sink(dto.email); }`, `setName(n) { this.name = n; }`. The run-1 record `(this, ., *) → (this, ., */{name})` of `setName` on the added fact `(this, ., [any-taint], T)` gives `(dto, ., [any-taint], {name}, T)` in the NORMAL layer. `sink(dto.name)` is not reported (no sink edge triggers it; it is not real), and `sink(dto.email)` is CONFIRMED in run 1; run 1 has no demand report. For contrast, the round-1 rule (`AnyTaint.D6T`) reports both sinks in the demand layer only. |
| `AnyTaintExCases.SD.run1_deep_record`, `run1_dto_ann`, `run1_name_not_reported`, `run1_email_confirmed`, `same_result` | PROGRAM SD (the setter one call deeper): the exclusion goes through the `*/{name}` record of the outer callee; the results in `root` are those of S. |
| `AnyTaintExCases.B.run1_d_ann`, `run1_e_ann`, `run1_name_not_reported`, `run1_anyE_confirmed`, `recs1_complete`, `inv3`, `must_summary_ops`, `run3_must`, `run3_summary`, `run3_d_ann`, `run3_e_ann`, `run3_name_not_reported`, `run3_anyE_confirmed`, `run3_with_records` | PROGRAM B (the broad demand): `root1() { d = srcAny(); d.setName(c); sink(d.name); }`, `root2() { e = srcAny(); e.setName(c); sinkAny(e); }`. Run 1: `sink(d.name)` is not reported, `sinkAny(e)` is confirmed. Run 3 with the broad demand `((this, ., [any], T), (this, ., [any], T))` of `setName` (written by hand): the must-premise `(this, ., [any-taint], {}, T)` and its NORMAL summary `→ (this, ., [any-taint], {name}, T)`; `sink(d.name)` is still not reported, and `sinkAny(e)` is confirmed; also with every record set inside the run-1 records. |
| `AnyTaintExCases.X.two_results`, `locations_exact`, `run1_results`, `fg_not_reported`, `fh_confirmed`, `k_confirmed`, `run1_no_demand` | PROGRAM X (a two-level write in ONE statement, an AP-level program: on the JVM the write goes through a local, §13 item 16): `x = srcAny(); x.f.g = c; sink(x.f.g); sink(x.f.h); sink(x.k)`. The write gives `(x, ., [any-taint], {f}, T)` and `(x, .f, [any-taint], {g}, T)`, both normal, which cover exactly the locations that are not at or below `x.f.g`; `sink(x.f.g)` is not reported, the two other sinks are confirmed. |
| `AnyTaintExCases.R.reads`, `no_y`, `run1_z`, `y_not_reported`, `z_confirmed` | PROGRAM R (the reads): after the setter of S, `y = dto.name` gives nothing (`name` is excluded) and `z = dto.email` gives `(z, ., [any-taint], {}, T)`, normal; `sinkAny(y)` is not reported, `sinkAny(z)` is confirmed. |
| `AnyTaintExCases.CL.clean_vectors`, `atAndBelow_result`, `below_result`, `exact_result` | PROGRAM CL (the cleaners at `x.f` on `(x, ., [any-taint], T)`): `atAndBelow` gives `(x, ., [any-taint], {f}, T)`; `below` gives it and `(x, .f, $, T)`, all normal; `exact` gives `(x, ., [any], T)` in the demand layer (every sink is a demand entry, also the cleaned `sink(x.f)`). |
| `AnyTaintExCases.CUT.cut_ops`, `run1_cut`, `cut_reports` | PROGRAM CUT (program X with the field limit 0, an AP-level program: no run has `L = 0`, §4.4): the cut gives `(x, ., [any], T)` in the demand layer and drops the exclusion `{g}`; `sink(x.f.h)` is a demand entry only. |
| `AnyTaintEx.Vec.setter_keep`, `setter_keep_base`, `read_excluded`, `read_admitted`, `above_star_excl`, `source_any_target`, `clean_atAndBelow`, `clean_below`, `clean_exact`, `clean_excluded`, `below_new_fact`, `cut_drops`, `emit_at`, `emit_above_excluded`, `emit_above_exact`, `emit_below`, `start_must`, `sat_vectors`, `summary_ann`, `restrict_vectors`, `check_vectors`, `below_keeps`, `below_keeps_loc` | THE VECTORS of the rows of W8 (`decide`): the keep edge (normal with the exclusion; the round-1 rule gave demand), the reads, the case `above` with an edge exclusion, a source, the cleaners, the cut, the emission, the start, the satisfaction, the summary, the restriction, the sink check, the case `below`. |

THE ROUND-1 PROGRAMS IN THE SPEC CLOSURES (`AnyTaintExCases2`: the programs `AnyTaintCases.G.prog`, `C.prog`,
`I.prog`, `PassRule.prog` of round 1, re-derived in `AnyTaintEx.D6X` and `AnyTaintEx.DRXs`). These are the results
that this spec states for G, C, I and P. Run 3 of G and C uses the earlier restriction and the earlier hand-off; with
the intersection and the hand-off of the demand edges the same results are checked by hand (§11.2). None of these
programs has an exclusion edge, a cleaner or a type filter, so
the refined runs make no annotation and give the round-1 objects with the Empty exclusion:

| Theorem | Statement |
|---|---|
| `AnyTaintExCases2.G.inv1`, `run1_flow_above`, `run1_vuln`, `run1_no_normal`, `run1_not_confirmed`, `run1_forgets`, `run1_forget_eq`, `revDemX_bound`, `revDemX_get`, `HX_exact`, `handoffX_get`, `r3_eJ1`, `run3_must`, `run3_must_supported`, `run3_sink_normal`, `run3_vuln_normal`, `run3_confirmed`, `run3_confirmed_handoff` | PROGRAM G (the getter): the complete run 1 (`AnyTaintExCases2.G.inv1`, in `AnyTaintEx.D6X`) has the FLOW summary `(p, ., *) → (ret, ., [any], *)` of `get` in the demand layer (the case `above`), so its only report is DEMAND, not `AnyTaintEx.Confirmed6X`. `AnyTaintExCov.forget6` of the refined run 1 is the round-1 run 1, so the backward run hands off exactly the demand `((p, .f, [any], T), (ret, ., [any], T))` of `get` and the zero demand (`AnyTaintExCases2.G.HX_exact`, `handoffX_get`; no added hypothesis). Run 3 (`AnyTaintEx.DRXs`) with this demand emits the must-premise `(p, .f, [any-taint], {}, T)`, which is supported (`AnyTaintEx.SupX`); its summary `→ (ret, ., [any-taint], {}, T)` is normal (`AnyTaintExCases2.G.r3_eJ1`), the sink edge in `root` is normal, and the vulnerability is `AnyTaintEx.ConfirmedX` for every record set, also with the derived hand-off. |
| `AnyTaintExCases2.C.run3_must`, `run3_sink_normal`, `run3_vuln_normal`, `run3_supported`, `run3_confirmed`, `run3_confirmed_handoff` | PROGRAM C (the sink in the callee): run 3 with the demand `((o, .f, [any], T), none)` of `use` emits the must-premise `(o, .f, [any-taint], {}, T)`; its start fact is the normal sink edge; it is supported through the must branch of `AnyTaintEx.SupLinkX` (inside the normal added fact `(o, ., [any-taint], {}, T)`, with the same mark); `AnyTaintEx.ConfirmedX`, also with the hand-off of the backward run (`AnyTaintCases.C.handoff_use`). |
| `AnyTaintExCases2.I.inv1`, `run1_flow`, `app_normal`, `run1_sink_normal`, `run1_vuln_normal`, `run1_confirmed`, `run1_no_demand` | PROGRAM I (the identity callee): the FLOW summary `(p, ., *) → (ret, ., *)` of `id` is normal; its application is a `keep` row of `AnyTaintEx.annX` with the Empty exclusion and keeps the normal layer; the sink edge is normal, and run 1 confirms the vulnerability (`AnyTaintEx.Confirmed6X`), with no demand report. |
| `AnyTaintExCases2.PassRule.source_vs_pass`, `source_normal`, `source_confirmed`, `pass_demand`, `invPass`, `pass_not_confirmed` | PROGRAM P: the micro edge `P.$ (T) → Q.[any] (T)` as a source gives `(Q, ., [any-taint], {}, T)`, normal, confirmed in run 1; as a pass rule it gives `(Q, ., [any], T)` in the demand layer, and the complete run reports demand only (not `AnyTaintEx.Confirmed6X`). |
| `AnyTaintExCases2.Carry.EdgeFree`, `ExclFreeProg`, `NoClean`, `SumFree`, `SumFreeR`, `RecsLift`, `RecsFree`, `d6x_iff_d6t`, `forget6_d6x`, `sup6x_iff`, `confirmed6x_iff`, `drx_iff_drt`, `supX_iff`, `confirmedX_iff`, `dollar_premise_annotates`; `AnyTaintExCases2.G.run1_carry`, `run1_not_confirmed_carry`; `AnyTaintExCases2.I.run1_carry`, `run1_confirmed_carry`; `AnyTaintExCases2.PassRule.run1_carry`, `confirmed_carry` | THE CARRY-OVER (the conditions `AnyTaintExCases2.Carry.EdgeFree`, `ExclFreeProg`, `NoClean`, `SumFree`, `SumFreeR`, `RecsLift`, `RecsFree`): with no exclusion edge (a `$` premise with a `*` target counts as one, because it gives an annotation, `AnyTaintExCases2.Carry.dollar_premise_annotates`), no cleaner and free summaries, `AnyTaintEx.D6X` is `AnyTaint.D6T` with the Empty exclusions, and `AnyTaintEx.Confirmed6X` is `AnyTaint.ConfirmedT6`; for a restricted run, also under the record conditions, `AnyTaintEx.DRXs` is the spec instance of `AnyTaint.DRT`, and `AnyTaintEx.ConfirmedX` is `AnyTaint.ConfirmedT`. Run 1 of G, I and P by this carry-over. |

THE WORKED PROGRAMS OF ROUND 1 (`AnyTaintCases`, in `AnyTaint.D6T` and `AnyTaint.DRT`): the model of the first F69
text. The results that this spec states for G, C, I and P are the theorems of `AnyTaintExCases2` (above); the rows
below are their round-1 derivations and the contrast with the old rule W6. W is the program of the demotion that the
exclusion replaces (its counterexample rows stay, above):

| Theorem | Statement |
|---|---|
| `AnyTaintCases.I.run1T_flow`, `app_normal`, `run1T_sink_normal`, `run1T_confirmed`, `run1T_no_demand`, `vuln_real`, `run1W6_vuln`, `run1W6_no_normal` | PROGRAM I (the identity callee): `root() { dto = srcAny(); x = id(dto); sinkAny(x); }`, `id(p) { return p; }`: the FLOW summary of `id` keeps the whole object, so run 1 CONFIRMS; with the old rule W6 the vulnerability is demand only. |
| `AnyTaintCases.G.wf`, `bindNoAny`, `taintConc`, `run1W6_vuln`, `run1W6_no_normal`, `run3W6_vuln`, `run3W6_no_normal`, `run1T_flow_above`, `run1T_vuln`, `run1T_no_normal`, `run1T_not_confirmed`, `handoff_exact`, `handoff_get`, `t3_eJ1`, `run3T_must`, `run3T_must_supported`, `run3T_sink_normal`, `run3T_vuln_normal`, `run3T_confirmed`, `run3T_confirmed_handoff`, `vuln_real` | PROGRAM G (the getter): `root() { dto = srcAny(); x = get(dto); sinkAny(x); }`, `get(p) { return p.f; }`. With the old rule W6 the vulnerability is demand in run 1 and in run 3 (`run3W6_no_normal` for every record set inside the complete run-1 records, `recs1W_complete`). With W6T run 1 still has only a demand sink edge (the FLOW summary of `get` is the case `above`). Backward run 2 hands off exactly the demand `((p, .f, [any], T), (ret, ., [any], T))` of `get` and the zero demand. Forward run 3 emits the must-premise `(p, .f, [any-taint], T)`, its sink edge is normal, and the vulnerability is CONFIRMED and real. |
| `AnyTaintCases.C.handoff_use`, `run3_must`, `run3_sink_normal`, `run3_supported`, `run3_confirmed`, `run3_confirmed_handoff`, `vuln_real` | PROGRAM C (the sink in the callee): `root() { dto = srcAny(); use(dto); }`, `use(o) { sinkAny(o.f); }`: run 3 confirms it through the supported must-premise `(o, .f, [any-taint], T)`. |
| `AnyTaintCases.W.keep_demand`, `run1_vuln_demand`, `run1_not_confirmed`, `run3_vuln_demand`, `run3_not_confirmed`, `vuln_not_real`, `keep_normal_false`, `keep_normal_not_endExact`, `keep_normal_confirms_unreal` | PROGRAM W (the strong write): `dto = srcAny(); dto.f = cl; sink(dto.f)`. The round-1 rule made `(dto, ., [any], T)` in the demand layer, so the vulnerability was reported and not confirmed; it is not real, and a normal result WITHOUT the exclusion would confirm it. The exclusion replaces the demotion: the keep edge gives `(dto, ., [any-taint], {f}, T)`, and `sink(dto.f)` is not reported (as `AnyTaintExCases.X.fg_not_reported`). |
| `AnyTaintCases.PassRule.source_vs_pass`, `source_normal`, `source_confirmed`, `pass_demand`, `pass_not_confirmed` | PROGRAM P: the micro edge `P.$ (T) → Q.[any] (T)` as a source (a taint edge: normal, confirmed in run 1) and as a pass rule (a may: demand, not confirmed). |
| `AnyTaintCases.Cut.cut_transfer`, `limitF_cut_demand` | THE CUT (§4.4): an `[any-taint]` fact over the field limit becomes `[any]` in the demand layer. |
| `AnyTaint.EmitVec`, `AnyTaint.Sanity` | The vectors of round 1: every cell of the emission table of §6.3 without the exclusion and the rows `below` and `above` (`decide`); a source result is a normal `[any-taint]` edge of `D6T` and of `DRT`, and the same micro edge as a pass rule is demand. |

THE PIPELINE (`analyzer-core.md` §12):

| Theorem | Statement |
|---|---|
| `PipelineAnyTaintEx.sysD6X`, `sysDRX`, `sysD6X_wf`, `sysDRX_wf`, `clD6X_iff`, `clDRX_iff`, `clD6X_link`, `clD6X_sub`, `clD6X_pub`, `clDRX_link`, `clDRX_sub`, `clDRX_pub` | The closures `AnyTaintEx.D6X` and `AnyTaintEx.DRX` as pipeline systems. A link carries the added fact with its flag and its exclusion; a publication carries the callee premise with its flag and its exclusion (the premise keys of §7.1). |
| `PipelineAnyTaintEx.known_D6X`, `known_DRX`, `result_D6X`, `result_DRX`, `result_DRXs`, `no_lost_summary_D6X`, `no_lost_summary_DRX` | At a reachable quiescent state the processed objects are exactly the closure (`Pipeline.quiescent_exact`); a summary edge is never lost. No instance of `Pipeline.quiescent_dominates` (argued, as for `PipelineAP`). |
| `PipelineAnyTaintExDriver.driver_iterationX`, `driver_iteration_uptoX`, `driver_iteration_srcX`, `resultSeqX`, `resultSeqX_runSeqX`, `resultSeqX_runSeqSrcX`, `vuln_sink_D6X`, `vuln_sink_DRX`, `runSeqX_congr` | THE DRIVER: if every run is complete and the driver computes the hand-offs of `analyzer-core.md` §7.3, §7.4 (each forward run read with the exclusions and the must flags dropped), every forward run reports every real vulnerability, in some layer. No new hypothesis: those of `PipelineAnyTaintDriver.driver_iterationT` with `PipelineAnyTaintEx.sysD6X` and `sysDRX` (with the source seeds those of `PipelineSeeds.driver_iteration_src`). There is no analog of `PipelineAnyTaintDriver.handoff_sameT`: the refined hand-off can be smaller (`AnyTaintExCov.CexRoute.route_a_false`). (The earlier hand-off; the driver of the spec hand-off is `PipelineHandoffDriver.driver_iterationNX`, with every reported vulnerability seeded, and `PipelineHandoffDriverExt.driver_iterationNX_demand`, with the seeds of the DEMAND entries only, §10.12.) |
| `PipelineAnyTaint.sysD6T`, `sysDRT`, `clD6T_iff`, `clDRT_iff`, `result_D6T`, `result_DRT`; `PipelineAnyTaintDriver.driver_iterationT`, `driver_iteration_uptoT`, `driver_iteration_srcT`, `handoff_sameT` | ROUND 1: the same for `AnyTaint.D6T` and `AnyTaint.DRT`; there the driver handed on the same backward demand as from `DR` (the pattern tail was a label). |

### 10.12 The hand-off of the demand edges — `Handoff*.lean`, `PipelineHandoffDriver*.lean`

The model of decision F70 (§6.4, §6.6, §8.5, §8.7 R5, §9.2; `ap-history.md` F70), with the mark-aware restriction of
decision F71 (§3.2, §6.3, §6.4; `ap-history.md` F71). `HandoffDefs.lean` (namespace
`Handoff`) has the definitions: the restriction as an intersection `Handoff.restrictI`, mark-aware (the premise test
`insideB`: the location part `insideLocB` and the mark part `markSubB`; the mark test of the conclusion `concMarkB`;
the conclusion `restrictConcI`, the meet `meetConcK`); the crossable records `CrossK`, `Cross`, the reversed record
`revRec` and the crossable backward leaf `CrossB` (normal, with a crossable reversal; `revRec` itself gives the normal
layer, so `CrossB` tests the layer of the backward leaf); the publications `Pub`, `pubD` (run 1) and `pubR` (a restricted run); the two hand-offs `handF` (forward to
backward) and `demOfN` (backward to forward); the input witness of a forward run `FlowRR`, `ReachRR` (every call that
returns is demanded, the exit location in `D-p` with its mark, or recorded) and its output witness `FlowRDN`, `ReachRDN` (every call that returns is justified by
a published piece or a record); the forward contract `CoversN` and the backward contract `BackwardContractN`. The spec
runs of the base model are `D` with `policy1` (run 1), `DR` with `emitM`, `satI`, `Handoff.restrictI` (a forward
restricted run) and `Backward.DB` with `emitM`, `satI`, `Handoff.restrictI` (the backward run). With the `[any-taint]` tail and its
exclusion they are `AnyTaintEx.D6X`, `AnyTaintEx.DRX` with `emitX`, `satX`, `HandoffX.restrictIX` (read without the
exclusions, `AnyTaintExCov.forget6`, `forgetX`), and `Backward.DB`. The earlier restriction and hand-off stay in the
model as the record of the earlier design (§10.7, §10.11). EVERY THEOREM OF THIS SECTION IS ABOUT THE CONCRETE
RESTRICTED RUNS of F70 and F71 (the emission `emitM`, the hand-offs without the normalization of F72). For the rules
of F72 (§4.3, §4.5, §6.3, §9.2) they are NOT YET PROVED; the model of F72 is the pending task of §11.2, and the
theorems below whose claim is false for the F72 runs are named there.

THE RESTRICTION (`HandoffRestrict.lean`, namespace `Handoff`):

| Theorem | Statement |
|---|---|
| `restrictI_sub` | The restriction only removes pairs and keeps the layer (`RestrictSub restrictI`). |
| `restrictI_contract` | C5 (§6.1), MARK-AWARE (F71): for a premise inside `D-c` in its locations and its marks (`insideB`), a pair of the edge whose exit location `D-p` covers WITH ITS MARK (`p.covers l2`) stays, in the same layer; every cell, also a `*` conclusion, and every mark cell. (Changed in F71: the hypotheses were `insideLocB` and `p.coversLoc l2`.) |
| `restrictI_contract_loc_false` | CEGAR (F71): the location form of C5 (`insideLocB`, `p.coversLoc l2`, the marks ignored) is FALSE for the mark-aware restriction: the example of F71 (§6.4) has a pair whose exit location lies in the locations of `D-p`, but not in its marks, and no result. |
| `RAux.concMarkB_of_den`, `RAux.concMarkB_conc`, `RAux.concMarkB_conc_admits`, `RAux.markMatchB_conc`, `RAux.mark_conc_or_abs` | The mark helpers (F71): a pair whose exit mark `D-p` admits passes the mark test of the conclusion (every mark cell); on a concrete mark the mark test and the emission test are `markSubB`; a mark is concrete or abstract (`Invariant.AbsMark`). |
| `insideB_loc`, `insideB_mark`, `insideB_intro`, `insideB_covers` | `insideB` is the location part and the mark part; every location of the premise, with its mark, is a location of the pattern. |
| `emitM_insideB`, `emitM_covers` | C2, the inside part with the marks (F71): an emitted premise of a CONCRETE added fact lies inside its entry pattern in its locations and its marks, and has the mark of the added fact. |
| `restrictI_someM`, `restrictConcI_mark`, `restrictI_exit_mark` | A result of `restrictI` has passed both mark tests; the restriction keeps the mark of the conclusion; the exit mark of a pair of a result is a mark of `D-p`, unless the conclusion mark is abstract. |
| `restrictI_interM`, `restrictI_inter_conc`; `Handoff.RVec.inter_exc_absmark` | THE INTERSECTION WITH THE MARKS (F71): every pair of a result has its entry location with its mark in `D-c`, and its exit location in `D-p` (except `RExc`) with a mark of `D-p` (except an abstract conclusion mark, the exception (c) of §6.4, which is real); for a concrete conclusion mark, the exit location with its mark in `D-p`, except `RExc`, where the exit mark is still a mark of `D-p`. |
| `restrictI_narrowM`, `restrictI_narrow_conc`, `handF_narrowM`, `handF_narrow_locM`, `demOfN_narrowM`, `handF_narrow_DRM`, `handF_narrow_DR_exactM` | THE NARROWING OF ONE RUN WITH THE MARKS (F71): as the location forms below, with `insideB`; a restricted forward run with `emitM` is concrete, so the mark exception does not occur (`handF_narrow_DRM`), and with no `*` exit pattern in its demand the narrowing is exact in the locations and the marks (`handF_narrow_DR_exactM`). |
| `restrictI_not_RestrictContract` | The earlier overlap form of C5 (`RestrictContract`) is FALSE for the intersection: a premise that only overlaps `D-c` has a demanded pair and no result. |
| `emitM_inside`, `insideLoc_coversLoc`, `emitM_coversLoc` | An emitted premise lies inside the entry pattern that emitted it, as locations; the location form. |
| `restrictI_inter`, `RExc`, `rexc_any_chain`; `Handoff.RVec.inter_exc_any`, `inter_exc_star`, `inter_exc_star_at` | THE INTERSECTION, the location form: every pair of a result has its entry location in `D-c` and its exit location in `D-p`, as locations, except the two cells of `RExc` (an `[any]` conclusion at or above a `*/E` exit pattern, the exit location then on the chain of `D-p`; a `*` conclusion); each exception is real. |
| `restrictConcI_cases`, `restrictI_some`, `restrictI_of`, `restrictConcI_sub`, `restrictConcI_exit`, `restrictConcI_inside`, `restrictI_narrow`, `restrictConcI_star` | The cells of the restriction (§6.4, the table and THE MEET). `restrictI_some` is the location form of `restrictI_someM`; `restrictI_of` takes `insideB` and the mark test of the conclusion (changed in F71: it took `insideLocB`). |
| `handF_narrow`, `handF_narrow_loc`, `demOfN_narrow` | THE NARROWING OF ONE RUN, the location form: a demand edge that a restricted run hands off lies inside the reversal of the demand pattern that published it, as locations (except `RExc`); the same for the backward hand-off with a non-zero premise. |
| `DR_exit_not_star`, `handF_narrow_DR`, `NoStarK`, `emitM_nonstar`, `DR_nonstar`, `restrictConcI_nonstar`, `handF_DR_nonstar`, `handF_narrow_DR_exact` | A restricted forward run with `emitM` has no `*` exit edge, so only the `[any]` exception occurs; with no `*` entry pattern in its demand it hands off no `*` pattern; with no `*` exit pattern in its demand the narrowing is exact. (False for the F72 runs: a FLOW premise has `*` exit edges, §6.4.) |
| `pubD_sub`, `pubR_sub` | A published piece has only pairs of its summary edge (both publications). |
| `star_meet_exact`, `tailExcl_admits_iff`, `meetConcKX`, `meetConcKX_tailF`, `meetConcKX_tailF_of`, `meetConcKX_exact` | A remark, not a spec rule: the exact meet of a `*` conclusion is representable (`*/(E ∪ tailExcl k)`); the spec keeps a `*` conclusion whole, because a restricted run of the concrete design has none. (Since F72 a restricted run has `*` conclusions; whether to use the exact meet is an open point, §11.2.) |
| `Handoff.RVec.v64_restrictI`, `v64_restrictU`, `vOverlap_overlapB`, `vOverlap_inside`, `vOverlap_restrictI`, `vOverlap_restrictU`, `vNoExit`, `row_base`, `row_at_any_exact`, `row_at_any_any`, `row_at_any_star`, `row_at_exact_exact`, `row_at_exact_any`, `row_at_exact_star`, `row_at_star_exact`, `row_at_star_any`, `row_at_star_star`, `row_below_any`, `row_below_star_adm`, `row_below_starc`, `row_below_exact`, `row_below_star_excl`, `row_above_any_exact`, `row_above_any_any`, `row_above_any_star`, `row_above_star_adm`, `row_above_star_excl`, `row_above_exact`, `row_apart` | THE VECTORS (`decide`) of §6.4: the example `[any]` against `$` gives `$` (the earlier restriction kept `[any]`), a premise that only overlaps `D-c` gives nothing (the earlier restriction gave a result), no `D-p` gives nothing, and every row and every meet cell of `restrictConcI`. |
| `Handoff.RVec.vMark_user_inside`, `vMark_user_concMark`, `vMark_user_restrictI`, `vMark_user_restrictU`, `vMark_user_loc`, `vMark_user_same`, `vMark_prem_loc`, `vMark_prem_inside`, `vMark_prem_restrictI`, `vMark_inStarEx_T`, `vMark_inStarEx_U`, `vMark_outStarEx_T`, `vMark_outStarEx_U` | THE MARK VECTORS (`decide`) of §6.4 (F71): the example of F71 (`(x, ., $, T) → (ret, .f, $, T)` against `D-p = (ret, .f, $, U)`) gives nothing, the earlier restriction and the location-only test keep it, and `D-p = (ret, .f, $, T)` keeps it; a premise mark that `D-c` does not admit gives nothing; the `*∖X` cells on the entry side and on the exit side. |
| `Handoff.RVec.vEmit_starEx_T`, `vEmit_starEx_T_pre70`, `vEmit_starEx_U`, `vEmit_abstract`, `markMatchB70`, `emitM70` | THE EMISSION VECTORS (`decide`) of §6.3 (F71): a `*∖{T}` entry pattern emits nothing for an added fact with the mark `T`, and the emission before F71 (`emitM70`, with the old test `markMatchB70`, where `*∖X` counts as `*`) emitted the premise; an added fact with the mark `U` gives a premise inside the entry pattern with its mark; a remark: on an abstract added fact the emission test is looser than `markSubB`, so `emitM_insideB` needs a concrete added fact. (The suffix `70` names the state before F71.) |

THE COVERAGE (`HandoffCoverage.lean`, namespace `Handoff`):

| Theorem | Statement |
|---|---|
| `cross_applies` | A crossable premise (`$`, or `*` with the Empty exclusion) that has a common location with a concrete added fact is satisfied by it (`satI`) or covers it (`applicable`): the record applies (rule `retRec`). (For an abstract added fact, F72: a `*` crossable premise applies too, a concrete one does not, §8.7 R4; argued, PENDING.) |
| `coverageRN` | THE COVERAGE THEOREM of `DR … emitM satI restrictI rc …` for a demanded-or-recorded flow (`FlowRR`): an edge of the initial fact covers the pair, and the run justifies the flow (`FlowRDN … (pubR dem) rc`). A demanded call uses `emitM_insideB` (the premise inside `D-c` with its mark; the added fact is concrete) and `restrictI_contract` (with the exit location in `D-p` with its mark, from `FlowRR`); a recorded call uses `cross_applies`. No request branch: the run is concrete. (No statement change in F71. The concrete design; the F72 coverage with modes is the lemma L1 of §11.2, PENDING.) |
| `reach_strongRN`, `coversN_DR` | The same for a vulnerability witness; THE FORWARD CONTRACT `CoversN` of the restricted run, under `P.WF` only. |
| `run1_reachRDN`, `run1_justifies` | Run 1 (`D … policy1 …`, the publication `pubD`, no record) justifies every real witness and reports its vulnerability. |
| `added_concN`, `no_reqN`, `flowRD_flowRDN`, `reachRD_reachRDN`, `policy1_applicable`, `Handoff.RAux.markSubB_of_conc` | Helpers. |

THE BACKWARD RUN (`HandoffBackward.lean`, namespace `HandoffBackward`):

| Theorem | Statement |
|---|---|
| `cross_step` | The backward run crosses a call by the reversal `Handoff.revRec (j, g)` of a crossable record (rule `retRec`). |
| `seg_genN`, `reach_of_db_genN`, `demanded_genN` | A justified flow (`Handoff.FlowRDN`) and a concrete backward edge give the backward edge at the forward entry, and the flow is demanded or recorded in the next forward run (`Handoff.FlowRR` with the demand `Handoff.demOfN` and the records `rcnext`). At a call: a crossable exit edge or record gives a recorded call; a non-crossable exit edge gives a demand edge of `handF`, then a demanded call (`demOfN` case 3) or, if the backward leaf is crossable (`Handoff.CrossB`), a recorded call. The demanded call has the exit location in `D-p` WITH ITS MARK: the emitted backward premise lies inside its `D-c` with its mark (the requirement is concrete) and covers the exit location with its mark (F71; no statement change). |
| `NextRecs` | The records of the next forward run: the records that the forward run read, its crossable exit edges, and the reversals of the crossable backward exit edges (`Handoff.CrossB`) with a non-zero premise. |
| `B_generalN` | THE BACKWARD CONTRACT `Handoff.BackwardContractN` of `Backward.DB (Program.rev P) … demB emitM satI restrictI recsB …`, under `P.WF`, S11 (a) to (f) (`Reverse.BindTargetsStar`, `Backward.StmtsMarkRev`, `Backward.NoZeroBack`, `Backward.ZeroKept`, `Backward.ExitReach`, sink tails `$` or `[any]`), with `Handoff.handF ⊆ demB`, the reversed crossable records in `recsB`, every publication only removing pairs, and `NextRecs`. |
| `recsBOf`, `recsBOf_spec`, `rcNextOf`, `rcNextOf_spec`, `rcNextOf_cross`, `B_generalN_canon` | The canonical record sets and the contract for the canonical backward run (demand `Handoff.handF`, records `HandoffBackward.recsBOf`). |
| `zero_initN`, `zero_atN`, `cross_em`, `crossB_em`, `crossK_em`, `markRev_em`, `emitM_inside_B`, `emitM_insideB_B`, `concMarkB_of_den_B`, `cross_applies_B`, `restrictI_contract_B` | Helpers; `cross_em`, `crossB_em`: `Cross` and `CrossB` are decidable in constructive logic; `emitM_insideB_B`, `concMarkB_of_den_B` are local copies of the mark lemmas of F71; `restrictI_contract_B` is the local copy of `Handoff.restrictI_contract` (with `insideB` and `p.covers l2` since F71). |

THE ITERATION (`HandoffIter.lean`, namespace `HandoffIter`; `HandoffMain.lean`, namespace `HandoffMain`):

| Theorem | Statement |
|---|---|
| `HandoffIter.Run0Contract`, `iteration_invariantN` | Run 1 justifies and reports every real witness; the strengthened induction (the witness stays justified). |
| `HandoffIter.iteration_abstract_or` | THE ABSTRACT ITERATION: for any run sequence whose run 1 satisfies `HandoffIter.Run0Contract`, whose forward runs satisfy `Handoff.CoversN` and whose backward steps satisfy `Handoff.BackwardContractN`, and in which every reported vulnerability of run `k` is confirmed (`C k`, any predicate) or seeded: every real vulnerability is, at every forward run `k`, reported by run `k` or confirmed by an earlier run. |
| `HandoffIter.iteration_abstract`, `iteration_invariant_neg`, `iteration_abstract_neg`, `iteration_all_seeded` | The form "not confirmed ⇒ seeded" with a decidable `C`; the form with no decidable `C` and the conclusion "no earlier run confirmed it ⇒ run `k` reports it"; the corollary `C = False`. |
| `HandoffMain.canonState`, `backOf`, `rcOf`, `stepState`, `state0`, `canon_pubSub`, `canon_run0`, `canon_covers`, `canon_backward` | THE CANONICAL RUN SEQUENCE of the spec rules (forward runs only: state `k` is run `2k + 1`), and its four ingredients. |
| `HandoffMain.iteration_generalN` | THE ITERATION THEOREM (§6.6): on the canonical sequence, under the hypotheses of `HandoffBackward.B_generalN`, if every reported vulnerability is confirmed (`C k`) or seeded, every real vulnerability is, at every forward run, reported by that run or confirmed by an earlier one. |
| `HandoffMain.iteration_generalN_all` | With every reported vulnerability seeded, every forward run reports every real vulnerability. |
| `HandoffMain.iteration_generalN_incl`, `runSeqN`, `pubSeqN`, `pubSeqN_sub`, `flowRR_mono`, `reachRR_mono`, `flowRDN_mono_rc`, `reachRDN_mono_rc` | The inclusion form: the driver may hand off more demand edges and give more records (a larger backward demand, more backward records, a larger forward demand, any first record set). |

THE EXCLUSION AND THE NARROWING (`HandoffExclusion.lean`, namespace `HandoffExclusion`; `HandoffMain.lean`):

| Theorem | Statement |
|---|---|
| `HandoffExclusion.NoZeroGenP`, `Reaches` | The program conditions on the zero base (the conditions of `NDZeroBase.NoZeroGen` on a `Program`); the call subtree of a method. |
| `HandoffExclusion.zinv_all`, `exclusion_backward`, `exclusion_backward_nodem`, `init_zero_nodem` | In a call-closed method set with no seed, every zero-premise backward edge has the zero fact; a method with only the zero fact as an initial fact (or with no demand) and no seed in its call subtree has only the edge `zero → zero` in the backward run. |
| `HandoffExclusion.exclusion_demand`, `emitM_zero_din`, `forward_zero_init`, `forward_zero_edges`, `exclusion_theorem` | Then `Handoff.demOfN` gives it only the zero pattern, and the next forward run has only the zero fact as an initial fact of it and only zero-premise edges in it. |
| `HandoffExclusion.exclusion_round` | ONE ROUND: a method key with no non-crossable exit edge in forward run `k` and no seed in its call subtree is analysed only from the zero fact in the next forward run (for every backward demand inside `handF`). |
| `HandoffMain.exclusion_canon` | THE EXCLUSION on the canonical sequence (§6.6), under `HandoffExclusion.NoZeroGenP`, no cleaner on the zero base and concrete seeds. |
| `HandoffMain.narrowing_canon_fwd`, `narrowing_canon_back` | The narrowing of each hand-off on the canonical sequence, as locations (forward: only the `[any]` cell of `Handoff.RExc`; backward: `Handoff.RExc`). |
| `HandoffMain.narrowing_canon_fwdM`, `narrowing_canon_backM` | The same in the locations AND the marks (F71; `Handoff.insideB`, the backward form for concrete seeds `BExact.SeedsConc`): the runs are concrete, so in the cells of `Handoff.RExc` the marks still narrow. |
| `HandoffMain.narrowing_canon`, `narrowing_canon_loc` | The narrowing with the exception cells: every demand pattern of forward run `k + 2` (Lean numbering) is the zero demand, a zero-premise backward pattern, or lies inside a demand pattern of forward run `k + 1` of the same method, as locations (entry inside entry, exit inside exit), except the cells of `Handoff.RExc`. The exceptions of `HandoffMain.narrowing_canon_loc` do not name the edge (`HandoffNoStar.base_loc_exception_weak`); the narrowing theorem of §6.6 is the exact form `HandoffNoStar.narrowing_canon_loc_exactM` (below). |
| `HandoffMain.narrowing_canonM`, `narrowing_canon_locM` | The same in the locations AND the marks (F71; `Handoff.insideB`, `PFact.covers`), for concrete seeds; in the cells of `Handoff.RExc` the marks still narrow. |

THE WORKED PROGRAMS AND THE CEGAR OF `Cross` (`HandoffCases.lean`, namespace `HandoffCases`, sub-namespaces `Wrap`,
`VecCross`, `AnyW`, `AnyM`, `Getter`):

| Theorem | Statement |
|---|---|
| `HandoffCases.crossK_iff`, `markRev_iff`, `cross_iff`, `restrictI_none` | `Cross` as a Boolean test (`decide`); no `D-p`, no result. |
| `HandoffCases.Wrap.inv_w1`, `w1_exit_cross`, `w1_wrap_exits_cross`, `w1_vuln`, `seedsW_exact`, `w1_wrap_normal` | PROGRAM WRAP (§6.6), run 1: the exit edge of `wrap` is the complete summary `(arg, ., *) → (ret, .f, *)`, and it is crossable; `wrap` has no demand-layer edge. |
| `HandoffCases.Wrap.old_b2_wrap_init`, `old_dem_w`, `inv_bo`, `old_demOf_exact`, `old_f3_wrap_init`, `old_f3_wrap_cut`, `old_f3_found` | THE EARLIER HAND-OFF: backward run 2 enters `wrap` with a non-zero premise and hands off a demand pattern of `wrap`; forward run 3 emits a non-zero fact into `wrap` and cuts INSIDE `wrap`, for every record set; it reports the vulnerability. |
| `HandoffCases.Wrap.handF_w1_exact`, `inv_bn`, `bn_cross`, `bn_wrap_zero_only`, `bn_wrap_edges_zero`, `demN_exact`, `inv_fn`, `fn_wrap_zero_only`, `fn_wrap_edges_zero`, `fn_record_applicable`, `fn_cut_in_root`, `fn_found`, `fn_vuln_exact`, `wrap_reachRR`, `wrap_old_vs_new` | THE HAND-OFF OF THE DEMAND EDGES: run 1 hands off no demand edge of `wrap`; backward run 2 crosses `wrap` by the reversed record and has only the zero fact in it; `wrap` gets only the zero demand; forward run 3 analyses `wrap` only from the zero fact and reports the vulnerability through the record of `wrap`, applied in the root by `applicable`, with the cut in the root; the witness is demanded or recorded. `wrap_old_vs_new`: both parts in one statement. |
| `HandoffCases.revRec_any_premise`, `not_cross_of_any`, `dollar_blocked`, `any_record_blocks_dollar`, `coversB_exact_any`; `HandoffCases.VecCross.*` | CEGAR (i): an `[any]` conclusion has an `[any]` reversed premise, so the leaf is not crossable; a `$` requirement neither satisfies it nor is covered by it (an `[any]` requirement crosses it). |
| `HandoffCases.AnyW.cegar_cross_anyw`, `fl_lost`, `fs_found`, `bl_no_srcHit`, `bs_srcHit`, `a1_exit_crossL` | CEGAR (ii), program ANYW (`ret = anyTaint(arg)`, the source in the root, with the source seeds): a looser hand-off that reads only the forward conditions of `Cross` (`CrossL`, `handFL`) loses a real, seeded vulnerability; the hand-off with `Cross` keeps it. |
| `HandoffCases.AnyM.cegar_cross_anym`, `flm_lost`, `fsm_found` | CEGAR (ii'), program ANYM (the source in a callee, with no source seeds): the same loss. |
| `HandoffCases.Getter.g1_exit_demand`, `handF_g1_exact`, `revRec_g_cross`, `revRec_g_crossB`, `demG_exact`, `fg_getter_zero_only`, `fg_record_sat`, `fg_found` | A GETTER `ret = arg.f`: its run-1 summary is in the demand layer (the case `above`), so it is a demand edge; the normal backward summary has a crossable reversal, so the getter gets only the zero demand in forward run 3, which reports the vulnerability in the normal layer through the reversed record. |

WITH THE `[any-taint]` EXCLUSION (`HandoffXRestrict.lean`, `HandoffXCoverage.lean`, namespace `HandoffX`;
`HandoffXIter.lean`, namespace `HandoffXIter`; `PipelineHandoffDriver.lean`, namespace `PipelineHandoffDriver`):

| Theorem | Statement |
|---|---|
| `HandoffX.insideLocXB`, `insideXB`, `meetExX`, `chainExX`, `restrictConcIX`, `restrictIX` | THE RESTRICTION WITH THE EXCLUSION (§6.4): the premise test reads the exclusion of the premise (`insideLocXB`) and, since F71, its mark (`insideXB`: `insideLocXB` and `markSubB`), and the conclusion has the mark test `Handoff.concMarkB` (the same mark tests as `Handoff.restrictI`); the meet adds `E2` of a `*/E2` exit pattern to an `[any-taint]/E` conclusion; above a `*/E2` exit pattern an `[any-taint]/E` conclusion gives the chain `[any-taint]/E2`. |
| `HandoffX.insideXB_loc`, `insideXB_mark`, `insideXB_intro`, `insideXB_of_base`, `insideXB_sound`, `emitX_insideXB`, `restrictIX_someM`, `restrictConcIX_mark`, `restrictIX_of` | The mark-aware X premise test (F71): its two parts; the base test gives it; every admitted location of the premise, with its mark, is a location of the pattern; an emitted premise of a concrete added fact passes it; a result passed both mark tests and keeps the mark of the conclusion; `restrictIX_of` takes `insideXB` and the mark test (changed in F71: it took `insideLocXB`). |
| `HandoffX.restrictIX_interM`, `restrictIX_inter_conc`, `restrictIX_narrowM`, `handF_narrowXM`, `handF_narrowX_DRXM` | THE INTERSECTION AND THE NARROWING OF ONE HAND-OFF WITH THE MARKS (F71), as the base forms `Handoff.restrictI_interM`, `restrictI_inter_conc`, `restrictI_narrowM`, `handF_narrowM`; in a `DRX` run (concrete) the piece lies inside `D-p` with its exclusion and its mark, or it is the `[any]` exception on a demand-layer piece, with a mark of `D-p`. |
| `HandoffX.XVec.vM_user`, `vM_prem`, `vM_inStarEx`, `vM_outStarEx`, `vM_emit`, `restrictIX_contract_loc_false` | THE MARK VECTORS WITH THE EXCLUSIONS (`decide`, F71): the example of F71 (`restrictIX` gives nothing, the earlier `AnyTaintEx.restrictX` keeps the edge), a premise mark that `D-c` does not admit, the `*∖X` cells (the exit side with an `[any-taint]/{f}` conclusion), the emission under a `*∖{T}` entry pattern; CEGAR: the location form of C5 is false for `restrictIX`. |
| `HandoffX.restrictIX_ok`, `restrictIX_sub_base`, `restrictConcIX_fact`, `restrictIX_restrictI` | `AnyTaintExExact.RestrictOKX restrictIX`: the normal form of W8, the same layer, fewer pairs; the facts without the exclusions only lose pairs; with a premise inside `D-c` without its exclusion the result is the base restriction `restrictI`. |
| `HandoffX.restrictIX_contract`, `restrictIX_contract_base`, `restrictIX_not_RestrictContractX` | C5 for a premise inside `D-c` (with its exclusion and, since F71, its mark: `insideXB`; the base form `insideB`), a pair whose exit location `D-p` covers with its mark (`p.covers l2`; changed in F71: `p.coversLoc l2`), every conclusion, also `*`; the overlap form is false. |
| `HandoffX.emitX_inside`, `emitX_inside_base`, `emitX_emitM`, `insideLocXB_sound`, `insideLocXB_of_base` | An emitted premise lies inside its entry pattern (with and without its exclusion), as locations; the location form. |
| `HandoffX.restrictIX_inter`, `RExcX`, `restrictConcIX_inside`, `restrictIX_narrow` | The intersection with the exclusions, as locations, except the cells of `RExcX`: a demand `[any]` at or above a `*/E2` exit pattern, and a `*` conclusion. |
| `HandoffX.pubRXw`, `pubRX`, `pubRX_sub`, `handF_narrowX`, `handF_narrowX_DRX` | The publication of an X run read without the exclusions; the narrowing of its hand-off, as locations (in a `DRX` run only the `[any]` exception, on a demand-layer piece). |
| `HandoffX.XVec.v64_demand`, `v64_taint`, `v_taint_star`, `v_at_rows`, `v_above_rows`, `v_below_rows`, `v_overlap`, `v_inside_only_with_excl`, `v_inside_only_with_excl_loc`, `inter_exc_any`, `inter_exc_star`, `v_old_above_not_inter` | THE VECTORS (`decide`): every cell with the exclusions; the earlier cell above a `*/E2` exit pattern (`[any-taint]` with the Empty exclusion) has a pair outside `D-p`; a premise inside `D-c` only with its exclusion. |
| `HandoffX.EmitInsideX`, `RestrictInsideX`, `CrossSatX`, `RecsEmbed`, `restrictIX_inside_contract`, `emitX_insideX`, `recLayerX_false`, `cross_appliesX` | The contracts of the generic X rules and the spec rules have them; a crossable base record is the X record with no must flag and no exclusion (`RecsEmbed`), which the record demotion never demotes. Since F71 the two contracts read the marks: `EmitInsideX` gives `insideXB` for a concrete added fact, and `RestrictInsideX` takes `insideXB` and `p.covers l2`. |
| `HandoffX.coverageRXI`, `reach_strongRXI`, `coversN_DRXI_gen`, `coversN_DRXI` | THE FORWARD CONTRACT `Handoff.CoversN` of `AnyTaintEx.DRX … emitX satX restrictIX …` read without the exclusions, under `P.WF` and `RecsEmbed`. |
| `HandoffX.run0X_contract` | Run 1 with the exclusion (`AnyTaintEx.D6X` with `policy1`, read by `forget6`) satisfies `HandoffIter.Run0Contract`. |
| `HandoffXIter.canonStateX`, `embedRecs`, `canonX_pubSub`, `canonX_run0`, `canonX_covers`, `canonX_backward` | THE CANONICAL X SEQUENCE: run 1 `D6X`, the backward run of the base model, the forward runs `DRX … emitX satX restrictIX` with the embedded records. |
| `HandoffXIter.iteration_generalNX`, `iteration_generalNX_all`, `iteration_reportsNX_canon` | THE ITERATION THEOREM WITH THE EXCLUSION: as `HandoffMain.iteration_generalN`; with every reported vulnerability seeded, run 1 and every forward run report every real vulnerability. |
| `HandoffXIter.iteration_generalNX_incl`, `iteration_reportsNX`, `runSeqNX`, `pubSeqNX`, `pubSeqNX_sub` | The inclusion form (the driver may hand off more), with the hypotheses of `AnyTaintExCov.iteration_reportsX`. |
| `PipelineHandoffDriver.driver_iterationNX`, `resultSeqX_runSeqNX`, `pubSeqXst`, `pubSeqXst_pubSeqNX` | THE DRIVER (`analyzer-core.md` §7) WITH EVERY REPORTED VULNERABILITY SEEDED: if every run is complete, the driver computes the hand-offs of the demand edges from the final states, and the seeds contain EVERY reported vulnerability (`hseeds`), then every forward run of the driver reports every real vulnerability, in some layer. The driver of §6.6 seeds only the DEMAND entries: its form is `PipelineHandoffDriverExt.driver_iterationNX_demand` (below). The pipeline systems `PipelineAP.sysDB` and `PipelineAnyTaintEx.sysDRX` are generic in the restriction, so no new system. |

THE NO-`*` DEMAND AND THE EXACT NARROWING (`HandoffNoStar.lean`, namespace `HandoffNoStar`). The seeds have concrete
marks and no `*` tail (every sink pattern has the tail `$` or `[any]`, S11 (f)). Since F72 demand patterns with a `*`
tail occur after run 1 (§6.4): the claims of this table about the later runs (no `*` premise, no `*` conclusion, no
`*` pattern, the exact narrowing) are FALSE for the F72 runs; W2 itself still holds there (argued, §2.3). The claims
about run 1 (`Run1K`, `D_premK`, `run1_exit_star_cross`,
`handF_run1_nonstar`, and the X forms for run 1) still hold, because F72 does not change run 1:

| Theorem | Statement |
|---|---|
| `Run1K`, `D_premK`, `run1_exit_star_cross`, `handF_run1_nonstar` | Run 1: every premise has the tail `$` or `*/{}`; every exit edge with a `*` conclusion is normal with an abstract mark (W2), so it is crossable; so the hand-off of run 1 has no `*` entry pattern, and its exit patterns have the tail `$` or `*/{}` (§6.4). |
| `DB_added_nonstar`, `DB_init_nonstar`, `LegalE`, `DB_legal`, `DB_edge_nonstar`, `demOfN_nonstar`, `nonstar_of_sinkK` | The backward run: no `*` added fact; no `*` premise under a demand with no `*` entry pattern; W2 for seeds with no `*` tail, so no `*` conclusion (§2.3); its hand-off has no `*` pattern. |
| `canon_dem_nonstar`, `canon_handF_nonstar`, `NoStarOrEmpty`, `rexc_empty_loc` | The canonical sequence: no demand pattern of a forward run has a `*` tail; a hand-off has no `*` entry pattern, and a `*/{}` exit pattern only after run 1, where the cell (a) of `Handoff.RExc` adds no location. |
| `narrowing_canon_fwd_exact`, `narrowing_canon_back_exact`, `narrowing_canon_back_loc`, `narrowing_canon_loc_exact` | THE NARROWING THEOREM (§6.6), with no exception, as locations: the hand-off of every restricted forward run lies inside its demand; the backward hand-off lies inside the forward hand-off (after run 1 in location form); every demand pattern of forward run `k + 2` (Lean numbering) with an exit pattern lies inside a demand pattern of forward run `k + 1` of the same method, entry inside entry and exit inside exit. The zero demand and the seed-path patterns are not covered. |
| `narrowing_canon_fwd_exactM`, `narrowing_canon_back_exactM`, `narrowing_canon_loc_exactM` | THE NARROWING THEOREM IN THE LOCATIONS AND THE MARKS (F71; the form that §6.6 states): the same three statements with `Handoff.insideB` and `PFact.covers`: every location of the exit pattern, with its mark, is a location of the exit pattern of run `k + 1`, and the same for the entry patterns; no exception. |
| `base_loc_exception_weak`, `base_loc_exception_weak_entry` | A remark: the exception disjuncts of `HandoffMain.narrowing_canon_loc` hold for every `*` exit pattern and for every `[any]` or `*` piece, so they do not name the edge. |
| `PremK6`, `D6X_premK`, `run1X_exit_star_cross`, `handF_run1X_nonstar`, `DRX_init_nonstar`, `handF_DRX_nonstar`, `canonX_dem_nonstar`, `canonX_handF_nonstar`, `insideLocXB_nonstar` | The same on the canonical X sequence (run 1 `AnyTaintEx.D6X`, the forward runs `AnyTaintEx.DRX`): no demand pattern has a `*` tail; with no `*` tail the X premise test differs from the base one only for the exclusion Universe. |
| `narrowing_canonX_fwd_exact`, `narrowing_canonX_back_loc`, `narrowing_canonX_loc_exact`; `HandoffNoStar.NSVec.entry_univ`, `entry_univ_loc` | THE NARROWING ON THE X SEQUENCE: the forward step is exact, and the piece lies inside `D-p` also without its exclusion; the composed form is exact on the exit side, and on the entry side except at a location that a dropped premise exclusion Universe excludes (a real cell of the operations, `HandoffNoStar.NSVec.entry_univ`; the AP has no Universe exclusion, S8). |

THE EXCLUSION AND THE ROUND NARROWING ON THE X CLOSURES (`HandoffXMain.lean`, namespace `HandoffXMain`):

| Theorem | Statement |
|---|---|
| `HandoffXMain.forward_zero_initX`, `forward_zero_edgesX`, `forward_zero_init_forgetX`, `forward_zero_edges_forgetX`, `emitTX_zero` | A forward X run whose demand gives `M` only the zero pattern has only the zero fact (no must flag, no exclusion) as an initial fact of `M`, and only zero-premise edges in `M`; the same on the view without the exclusions. |
| `HandoffXMain.exclusion_roundX`, `exclusion_canonX` | THE EXCLUSION (§6.6) on the X closures: one round, and on the canonical X sequence (`HandoffXIter.canonStateX`), under the hypotheses of `HandoffMain.exclusion_canon`. |
| `HandoffXMain.narrowing_canonX_fwd`, `narrowing_canonX_back`, `narrowing_canonX`, `narrowing_canonX_loc`, `FwdExc`, `Dropped`, `covLoc_split`, `not_dropped_empty` | THE NARROWING OVER ONE ROUND on the canonical X sequence, with the exclusions of the forward piece and premise read; in location form a location can be one that a dropped exclusion excludes (`Dropped`; none for the Empty exclusion). |
| `HandoffXMain.narrowing_canonX_fwdM`, `narrowing_canonX_backM`, `narrowing_canonXM` | The same in the locations AND the marks (F71; `HandoffX.insideXB`, `Handoff.insideB`): the X runs and the backward runs are concrete (`AnyTaintExCov.concX_all`, `BExact.DB_edge_concrete`), so in the cells `FwdExc` and `Handoff.RExc` the marks still narrow. |
| `HandoffXMain.XMVec.exit_dropped`, `exit_dropped_loc`, `entry_dropped`, `entry_dropped_loc` | The vectors (`decide`): each step is a real cell of the operations, and the composed exit (entry) location lies outside `D-p` (`D-c`) at a dropped location, with a `*/{4}` demand pattern (which the spec runs do not have, `HandoffNoStar.canonX_dem_nonstar`). |

THE SOURCE SEEDS, THE FINITE FORMS AND PROGRAMS 1 AND 2 (`HandoffSrc.lean`, `HandoffUpto.lean`, `HandoffRCases.lean`;
the namespaces of the file names):

| Theorem | Statement |
|---|---|
| `HandoffSrc.seg_genN_src`, `reach_of_db_genN_src`, `demanded_genN_src`, `B_srcN`, `B_generalN_of_src` | CONTRACT B WITH THE SOURCE SEEDS (§6.6 item 3): the justified witness is demanded or recorded in the next forward run on `FSeeds.keepSources P σ`, for every `σ` that contains the source hits of the backward run; a recorded call reads no seed. With every source seeded it is `HandoffBackward.B_generalN`. |
| `HandoffSrc.runSeqSrcN`, `iteration_srcN`, `iteration_srcN_upto`, `canonStateSrc`, `iteration_srcN_canon`, `runSeqSrcNX`, `iteration_srcNX`, `iteration_srcNX_upto` | THE ITERATION WITH THE SOURCE SEEDS (§6.6): as `HandoffMain.iteration_generalN`, on the seeded programs; the canonical sequence; the X closures; the finite forms. |
| `HandoffSrc.SrcRec.run1_exit`, `exit_cross`, `rec_next`, `src_dropped`, `found_unseeded` | THE SOURCE SEEDS DO NOT FILTER THE RECORDS (§9.2): a crossable zero-premise record of run 1 fires the effect of a source that the seeds drop, and the next forward run reports the vulnerability in the normal layer through it (a precision point only). |
| `HandoffUpto.BackwardContractNIn`, `iteration_invariant_upto`, `iteration_prog_upto`, `iteration_abstract_upto`, `iteration_generalN_upto`, `iteration_generalN_canon_upto`, `iteration_generalNX_upto`, `iteration_generalNX_canon_upto` | THE FINITE FORMS: the hypotheses only for the runs before forward run `K`, the conclusion for every forward run up to `K`; no run after `K` is read. |
| `HandoffRCases.p1_no_exit`, `p1_found_I`, `p1_handoff`, `p1_chain` | PROGRAM 1 (§6.3) with the intersection: no `D-p`, no result; forward run 3 reports the vulnerability; the hand-off of the demand edges contains the callee patterns (case 2); run 1, backward run 2 and forward run 3 report it. |
| `HandoffRCases.r1_c_not_cross`, `b2_handF`, `b2_emit`, `b2_inside`, `b2_restrictI`, `b2_restrictI_eq_U`, `b2_not_crossB`, `p2_handoff`, `ddM_eq`, `f3_emit`, `f3_inside`, `f3_restrictI`, `f3_restrictI_eq_U`, `p2_found_I`, `p2_chain`, `b2_insideB`, `b2_concMark`, `f3_insideB`, `f3_concMark` | PROGRAM 2 (§6.4) with the intersection: the run-1 summary of `c` is a demand edge; the backward premise lies inside its `D-c`, and the backward summary is in the demand layer (case 3); the forward premise lies inside its `D-c`; on this program the intersection equals the earlier restriction; forward run 3 reports the vulnerability. The mark tests of F71 pass in both restrictions (`b2_insideB`, `b2_concMark`, `f3_insideB`, `f3_concMark`), so the results stay. That the hand-off is EXACTLY the three parts of §9.2 is checked by hand. |

THE DRIVER WITH THE SEEDS OF §6.6 (`PipelineHandoffDriverExt.lean`, namespace `PipelineHandoffDriverExt`):

| Theorem | Statement |
|---|---|
| `PipelineHandoffDriverExt.driver_iterationNX_demand`, `driver_iterationNX_demand_known`, `known_of_resultSeqX`, `known_of_resultSeqX_upto` | THE DRIVER (`analyzer-core.md` §7) with the hand-offs of `PipelineHandoffDriver.driver_iterationNX` and the seeds of THE SEEDS (§6.6): every vulnerability that the driver reads from forward run `k` (Lean numbering: `k` counts the forward runs) is confirmed (`C k`) or seeded. Then every real vulnerability is, at every forward run `k`, reported by run `k` or confirmed by an earlier run; also on the final states. With `C k` = confirmed by a complete forward run up to `k` (§4.9) this is the claim of §6.6. |
| `PipelineHandoffDriverExt.driver_iterationNX_confirmed` | The instance with `C k` = "a forward run up to `k` reports the vulnerability in the normal layer". This test is weaker than CONFIRMED (§4.9 also needs the support), so it is not the confirmation of this spec: it says only that a normal-layer report ends the need for a seed. |
| `PipelineHandoffDriverExt.driver_iterationNX_upto` | THE FINITE FORM: the driver stops after forward run `K`; the runs up to `K` are complete; the conclusion holds for every forward run up to `K`. |
| `PipelineHandoffDriverExt.driver_iteration_srcNX`, `driver_iteration_srcNX_upto`, `resultSeqX_runSeqSrcNX`, `pubSeqXst_pubSeqSrcNX` | The same with the source seeds (§6.1 rule 6). |

---

### 10.13 F72 to F75: checked results and open obligations

The default target `ApSpec.lean` includes the modules below. The existing `Abs` closures and F73 certificates use
the earlier spatial cleaner. F75 has a separate local operation and regression model; the general closure migration
remains pending. The F72 closure results cover the base model with three
tail kinds. They do not cover the forward must-premise `[any-taint]/E`, conjunctions, static position requests,
aliases, or end facts. The separate `FieldCleanerX` module checks local F74 operations on a must-taint fact.
The X iteration and the analyzer pipeline need separate F72 proofs.

| Module and result | Checked scope |
|---|---|
| `AbsDefs`: `normDem_enlarges`, `flowForm_inside`, `satW_markSub`, `satW_step`, `DRA_no_req`, `DBA_no_req` | Demand normalization only enlarges the admitted marks; a FLOW premise lies inside its `*` pattern; satisfaction passes the mark gate; the two restricted closures have no request rule. |
| `AbsDefs`: `emitW_contract`; `AbsWitness`: `emitWitness`, `handFA_run1_PatTail` | L6 requires an explicit tail guard: an abstract entry pattern has `$`, `[any]`, or `*/{}`. A concrete pattern requires a concrete added mark. `emitWitness` computes the premise as data. The tail guard is proved for run 1 and the explicit alternating base sequence under the seed conditions below. The X-tail and pipeline forms remain open. |
| `AbsDefs`: `vec_emitW_starE`, `emitW_needs_patTail` | The guard is necessary: `(x, p, */{f}, *)` and `(x, p, */{g}, *)` can share a location while the emitted FLOW premise fails `satW`. Common locations alone do not imply satisfaction. |
| `AbsExact`: `DRW_edge_exact`, `DRW_records_exact`, `DBW_edge_exact`, their valid forms | L5 for normal base-model edges. The hypotheses are S7, exact input records, and `FiltUp` or S13. Backward exactness also requires `NoZeroIn` and a non-zero premise. These results do not assume that emission copies the added mark. |
| `AbsExact`: `DBW_rev_record_exact`; `AbsHandoff`: `DBW_rev_record_exact_shape` | Reversal of a normal nonzero backward summary under S7, no type filtering, the zero-binding and reversal conditions, and exact input records. `AbsHandoff` derives the empty-premise-exclusion condition from normalized, non-star entry demands. |
| `AbsHandoff`: `demOfNA_backward_entry_shape`, `handFA_forward_entry_shape`, `shapeSeq_entry_shape`, `shapeSeq_PatTail` | Every entry demand of the explicit alternating base sequence has a non-star tail and a normalized mark, with concrete seeds that have no star tail. A forward FLOW exit pattern does not imply a star entry pattern. This is a shape invariant; it proves no coverage or source-seeded driver result. |
| `AbsForward`: `flowMA_replay`, `flowMA_witness`, `cleanMA_covers`, `cleanMA_witness` | A supplied, coherent abstract trace replays into the forward base closure with its indexed result. The result and cleaner witnesses are computed as data. This is local L1 certificate replay; it does not synthesize a trace for every concrete witness or prove backward preservation. |
| `AbsClosure`, `AbsCases` | Finite, executable closed-state checks and explicit derivations for four micro-edge kernels: sharing in a getter, a mark-changing pass rule, a partial cleaner and a nested call. A closed-state check bounds the closure; each reported result also has a derivation. These kernels do not model every IR step of §13 or prove R6 for all programs. |
| `AbsStore`: `emitW_lookup_equiv`, `replayW_equiv`, `deliverW_equiv` | The indexed queries and the list scans give the same members after the exact F72 test. FLOW replay and delivery require both prefix and extension candidates. The regression vectors prove that either earlier one-direction query can miss a match. |
| `AbsStore`: `replayW_test_cost`, `IndexCases.fewer_tests` | With an index already stored, the exact test runs once per candidate, against once per stored item for the list scan. The finite case uses 1 test against 33. Index construction and path traversal have separate costs (§8.6). |
| `ReviewDemand`: `Before.not_run1_contract_with_modes`, `corrected_run1_contract_with_modes`, `actual_fact_dependent_cleaner_modes`, `coarse_abstract_trace_cannot_pass` | F73 checks for the earlier spatial cleaner. Its different-base getter certificate remains compatible with F75. Its same-base spatial certificates are historical; F75 requires concrete mode for T on that base. |
| `ReviewDemand`: `backward_zero_premise_concrete`, `binding_in_needs_non_growth_condition` | Zero-premise backward seed paths keep concrete marks under concrete seeds. A generic path-growing binding can exceed the field limit; W3 needs the stated binding shape. |
| `ReviewReversal`: `exact1`, `exact2`, `exact3`, `records1_exact`, `records3_exact`, `published2_exact`, `demand3_all_exact` | Every listed object of the 1, 1, 2 trace has a derivation, and the closed-state bounds exclude every unlisted object. The record sets, restricted backward publication and next forward demands are exact. Raw normal summaries are persisted before restriction (`analyzer-core.md` §4.1); the hand-off reads the published noncrossable pieces. |
| `ReviewReversal`: `default_reversal_loss`, `Strict.default_reversal_loss`, `no_source_application` | The generic atomic field-cleaner kernel loses a flow with limits 1, 1, 2 and 1, 2, 3. It omits the F74 read and strong write-back, so it is not the reference field-cleaner program and is not a refutation of R6 for the interpreter. WF and S7 alone do not state the needed lowering condition. |
| `CleanerLowering`: `loweredWith_iff`, `lowerWitnessWith` | A named, single-field cleaner is concretely equivalent to read, clean a fresh temporary, and strong write-back, after projection away from the temporary. This holds for all three reaches and for one mark or all marks. Both endpoints are old bases. The model has no aliases. `lowerWitnessWith` computes the intermediate location. This concrete equivalence does not permit substitution of an atomic cleaner in an abstract AP run. |
| `CleanerLowering`: `backwardKeepWitnessWith`, `backward_keep_all_marks`, `backward_keep_record_sound_with` | For every field limit, reach and mark selection, the actual reversed strong write has a normal keep branch outside the field. It keeps mark `*` through the temporary cleaner and reversed read. Its reversed record denotes real lowered flows. The witness is computed as data. |
| `FieldCleanerX`: `exact_vector`, `below_vector`, `atAndBelow_vector`, `outsideWitness` | Apply the actual read, temporary cleaner and strong write to a normal root must-taint fact, with one selected mark and limit 3. All three reaches retain a normal outside-field fact; `exact` also returns a demand field fact and `below` a normal exact field value. This is a local X operation check, not an F72 X closure theorem. |
| `ReviewFieldCleaner`: `exact1`, `exact2`, `exact3`, `table_counts`, `tables_nodup` | The lowered program below uses limits 1, 2, 3. Every listed closure object has a derivation; closed-state checks exclude all other objects. The edge counts are 23, 24, 23, with no duplicate row. No run has a request. |
| `ReviewFieldCleaner`: `hands1_exact`, `published2_exact`, `demand3_all_exact`, `records1_exact`, `records3_exact`, `hand3_empty` | Exact hand-offs, restricted backward publication and record sets. Raw normal summaries are persisted before restriction. All run-3 exit rows are crossable, so the next hand-off is empty. |
| `ReviewFieldCleaner`: `source_hit`, `hits_exact`, `seeds_exact`, `kept`, `lowered_pipeline_preserves` | The backward run reaches the one actual source with concrete mark `T`. The source mask equals the hit set. Run 3 uses that mask and reports the finding in the normal layer (`f3_normal_report`). Full analyzer Support confirmation and general R6 are not claimed. |
| `ReviewRootCleaner`: `root_exact_shape`, `root_exact_reversal_loss`, `strict_empty_mask_exact`, `strictTraceWitness` | Historical loss under the spatial cleaner. A separate root EXACT action has the empty path, so F74 does not lower it. At limits 1, 2, 3, the old run 1 reports but the backward run misses its source. F75 adds the missing run-1 T request on the same-base disjoint fact. |
| `ReviewBackwardCleaner`: `experimental_counterexample`, `badRecordWitness`, `normalFindingWitness` | An experimental backward selected-mark cleaner passes abstract marks unchanged and preserves the layer. A reached normal backward summary becomes a false forward record under the existing reversal and application rules. Run 1 gives a demand finding, the backward run hits the sole source, and forward run 3 reports a normal finding whose location is not concretely reachable. The backward model proves positive derivations in a subset of the candidate rules, not exact closure tables. This experiment does not change the normative cleaner rule. |
| `BaseCleaner`: `abstract_same_base`, `abstract_request`, `residual_den_iff`, `cleanRes_sound_witness`, `cleanRes_exact`, `normal_input`, `cleanMABBase` | F75 local selected-mark operation. Every same-base abstract fact splits before the position test and requests T, including T already excluded. The residual removes exactly T from the input relation. A preserved concrete pair has a computed output or request witness. Every normal output denotes only an input pair the cleaner preserves. Concrete facts, all-marks cleaners and different bases retain their earlier rules. The mode gate excludes same-base T witnesses from abstract mode. |
| `BaseCleaner`: `batch_uniform_nonempty`, `uniform_batch_equiv` | Literal per-leaf operation and a uniform-base batch give the same facts and request set. The executable cost model counts one base guard per leaf against one batch guard. It does not prove a trie implementation or count mark-set insertion, allocation or output storage. |
| `ReviewBaseCleaner`: `RC`, `FC`, `BC`, `Repaired.repaired_counterexample`, `Repaired.repairedWitness` | Full base-model closure definitions with the F75 cleaner and the existing request/emission/restriction rules. Positive derivations prove the run-1 request, concrete demand, actual backward source hit, eligible raw record and run-3 normal finding at limits 1, 2, 3. The data witness also proves the concrete sink flow. These are not complete state tables or a global iteration theorem. |
| `ReviewBaseCleaner`: `Blocked.record_stored`, `Blocked.actual_reversal`, `Blocked.reversed_rejects_T`, `Blocked.rejectedWitness` | The corrected run-1 hand-off reaches and stores the revised abstract record. It keeps mark exclusion T in its reversed conclusion. Applying it to `p.$ (T)` gives no fact. The concrete root T is still dropped. This blocks the identified record; it is not an exhaustive closure proof for the second program. |

THE FIELD-CLEANER REVIEW uses this program:

```text
root() { x = source_T(); r = C(x); sink_T(r.f.n); }
C(arg) { p.n = arg; clean(p.g, T); ret.f = p; return ret; }
```

Fields `n` and `g` are distinct. Use field limits 1, 2, 3. Run 1 carries `p.n.*`, and its exit cut gives
`ret.f.[any]` with mark `*`. Run 2 keeps the precise sink requirement `ret.f.n.$` with mark `T`, but the abstract
demand emits `ret.f.*`, which reverses to `p.*`. The earlier review treated the field action as one atomic
`clean(p.g, T)` instruction. The user corrected that model: use `tmp = p.g; clean(tmp, T); p.g = tmp` (§4.7).
Reversal of the strong write-back keeps the normal branch `p.*/{g}` with mark `*`. The cleaner on `tmp` is
disjoint from that branch. A loss in the atomic kernel is evidence that the lowering condition is needed, not
evidence that the field-cleaner program loses its finding.

The lowered run 2 returns `arg.[any]` with mark `*` under `ret.f.*`. Its restricted publication admits the
concrete caller requirement `ret.f.n.$ (T)`, which gives `x.[any] (T)` and hits the source. The temporary branch
can exclude `T`; that exclusion does not affect the keep branch. The list model can store `{g,g}` for the keep
exclusion; its meaning is `{g}`.

THE EXACT LOWERED TRACE. `N` means NORMAL; `D` means DEMAND. Each cell is the complete fact set for that node
and premise. The zero fact is written `0`. Nodes of `C` are: 0 entry, 1 after `p.n = arg`, 2 after the read,
3 after the temporary cleaner, 4 after write-back, 5 after `ret.f = p` (exit). The root nodes are 0 entry,
1 after the source, and 2 after the call (sink).

Run 1, forward, `L = 1`:

| Method, premise | Node | Facts |
|---|---|---|
| root, `0` | 0 | `0 N` |
| root, `0` | 1 | `0 N`; `x.$ (T) N` |
| root, `0` | 2 | `0 N`; `r.f.[any] (T) D` |
| C, `0` | each of 0 to 5 | `0 N` |
| C, `arg.* (*)` | 0 | `arg.* (*) N` |
| C, `arg.* (*)` | each of 1 to 4 | `arg.* (*) N`; `p.n.* (*) N` |
| C, `arg.* (*)` | 5 | `arg.* (*) N`; `p.n.* (*) N`; `ret.f.[any] (*) D` |

Initial facts: root `0`; C `0` and `arg.* (*)`. Added facts: C `0` and `arg.$ (T)`. No requests.
The sink reports a DEMAND finding. The hand-off contains exactly these `(D-c, D-p)` pairs:

* root: `(r.f.[any] (T), 0)`;
* C: `(ret.f.[any] (*), arg.* (*))`.

The four normal exit summaries are root `0 → 0`, C `0 → 0`, C `arg.* → arg.*`, and C `arg.* → p.n.*`.
Their marks pass through. Run 2 uses their reversals as records.

Run 2, backward, `L = 2`:

| Method, premise | Node | Facts |
|---|---|---|
| root, `0` | 2 | `0 N`; `r.f.n.$ (T) N` |
| root, `0` | 1 | `0 N`; `x.[any] (T) D` |
| root, `0` | 0 | `0 N`; `0 D`; `x.[any] (T) D` |
| C, `0` | each of 5 to 0 | `0 N` |
| C, `ret.f.* (*)` | 5 | `ret.f.* (*) N` |
| C, `ret.f.* (*)` | 4 | `p.* (*) N` |
| C, `ret.f.* (*)` | 3 | `p.*/{g} (*) N`; `tmp.[any] (*) D` |
| C, `ret.f.* (*)` | 2 | `p.*/{g} (*) N`; `tmp.[any] (*∖{T}) D` |
| C, `ret.f.* (*)` | 1 | `p.*/{g} (*) N`; `p.g.[any] (*∖{T}) D` |
| C, `ret.f.* (*)` | 0 | `arg.[any] (*) D`; `p.*/{g,n} (*) N`; `p.g.[any] (*∖{T}) D` |

Initial facts: root `0`; C `0` and `ret.f.* (*)`. The one added fact is C `ret.f.n.$ (T)`.
No requests. The one restricted nonzero publication is C `ret.f.* (*) → arg.[any] (*) D`.
The normal backward summary C `ret.f.* (*) → p.*/{g,n} (*) N` is persisted before restriction.
The source at root node 0 is hit. The next forward demand contains exactly:

* the zero demand `(0, none)` for every method;
* root: `(x.[any] (T), none)`;
* C: `(arg.[any] (*), ret.f.* (*))`.

Run 3 uses the four run-1 forward records and the reversed backward record
C `p.*/{g,n} (*) → ret.f.* (*)`. The source mask keeps exactly the hit source.

Run 3, forward, `L = 3`, has the same fact cells as run 1 except:

* root node 2 has `0 N` and `r.f.n.$ (T) N`;
* C node 5 under `arg.* (*)` has `arg.* (*) N`, `p.n.* (*) N`, and `ret.f.n.* (*) N`.

Its initial and added facts are those of run 1. No requests. The sink reports in the NORMAL layer.
Every exit summary is crossable; the next backward hand-off is empty. This checks the three runs and the source
mask, not the full analyzer Support confirmation.

THE ROOT-EXACT REVIEW (old spatial rule, superseded by F75) changes only the cleaner in the earlier three-instruction callee:

```text
root() { x = source_T(); r = C(x); sink_T(r.f.n); }
C(arg) { p.n = arg; clean(p, EXACT, T); ret.f = p; return ret; }
```

The action cleans `p` itself, not `p.n` (§4.7). It has no field path, so S16 adds no read or write. The concrete
source-to-sink flow survives. Use limits 1, 2, 3. In this callee, nodes 0 to 3 are entry, after the write to `p.n`,
after the root cleaner, and after the write to `ret.f` (exit).

Run 1 has the same root facts as the field review. In C, the zero-premise fact is `0 N` at each node.
Under `arg.* (*)`, node 0 has `arg.* (*) N`; nodes 1 and 2 have `arg.* (*) N` and `p.n.* (*) N`; node 3 also
has `ret.f.[any] (*) D`. The initial facts, added facts, two hand-off demands and four forward records are those
of the field review. The root cleaner is disjoint from `p.n.*`. No request for T is raised, and the sink is DEMAND.

Run 2 has these complete fact cells:

| Method, premise | Node | Facts |
|---|---|---|
| root, `0` | 2 | `0 N`; `r.f.n.$ (T) N` |
| root, `0` | each of 1 and 0 | `0 N` |
| C, `0` | each of 3 to 0 | `0 N` |
| C, `ret.f.* (*)` | 3 | `ret.f.* (*) N` |
| C, `ret.f.* (*)` | 2 | `p.* (*) N` |
| C, `ret.f.* (*)` | 1 | `p.* (*∖{T}) N` |
| C, `ret.f.* (*)` | 0 | `arg.[any] (*∖{T}) D`; `p.*/{n} (*∖{T}) N` |

Its initial facts are root `0`, C `0` and C `ret.f.* (*)`. Its added fact is C `ret.f.n.$ (T)`.
The primitive cleaner result has request T, but the F72 backward closure has no request rule.
The restricted nonzero publication is C `ret.f.* (*) → arg.[any] (*∖{T}) D`; it rejects the caller's concrete T
requirement. The normal backward summary to `p.*/{n} (*∖{T})` is persisted before restriction and reversed for run 3.
No source is hit. The next forward demand is exactly the zero demand for every method and
C `(arg.[any] (*), ret.f.* (*))`; normalization removes the mark exclusion from the demand.

Run 3 uses those demands, the four forward records and the reversed backward record. Its actual source mask is
empty. Initial facts are root `0` and C `0`; the added fact is C `0`. All seven propagation facts are `0 N`:
root at nodes 0 to 2 and C at nodes 0 to 3. No request or sink report exists, and the next hand-off is empty.
The exact propagation counts for the three runs are 17, 13, 7. Run 1 has no normal finding to retain separately.
This historical root-action gap is distinct from the corrected field example. The F75 repair is checked below.

THE BACKWARD-CLEANER EXPERIMENT. The user asked to test a selected-mark cleaner that acts only on concrete marks
in backward runs. In this experiment, `*` and `*∖X` pass it unchanged, in their incoming layer. Forward cleaning
keeps §4.7. This is a candidate rule, not a normative change. A counterexample uses limits 1, 2, 3:

```text
root() { x = source_T(); r = C(x, x); sink_T(r.f); }
C(arg, p) { p.n = arg; clean(p, EXACT, T); ret.f = p; return ret; }
```

Here `p` is a formal argument, so its backward summary result is an eligible summary base. EXACT cleans T at
`p` itself. T can survive at `p.n` and thus at `ret.f.n`, but no concrete input reaches `ret.f` with T.
`ReviewBackwardCleaner.no_callee_root` and `no_real_sink` prove these absences.

Run 1 follows `arg.* (*) N → p.n.* (*) N → ret.f.[any] (*) D`. The cut at limit 1 drops the final `n`.
Applied to the caller's T fact, the summary gives `r.f.[any] (T) D`, which triggers the sink on `r.f` as a
DEMAND finding. The other binding adds `p.$ (T)`. Its FLOW analysis raises a T request; the concrete answer
`p.$ (T)` stops at the forward cleaner. Thus the extra argument supplies no valid T propagation to `ret.f`.

Run 2 uses the actual run-1 hand-off and concrete added requirement `ret.f.$ (T)`. Its FLOW premise is
`ret.f.* (*)`. The proposed cleaner gives this backward trace:

```text
reverse ret.f = p:      p.* (*) N
clean(p, EXACT, T):     p.* (*) N
reverse p.n = arg:     arg.[any] (*) D   and   p.*/{n} (*) N
```

The demand branch returns `x.[any] (T)` to the caller and hits its source. The normal keep branch gives the
raw backward summary `ret.f.* (*) → p.*/{n} (*)`. It passes the existing complete, nonzero-premise and `CrossB`
guards. Its actual reversal moves the field exclusion to the conclusion:

```text
p.* (*) → ret.f.*/{n} (*) N
```

The next forward run retains the hit source. Its added fact `p.$ (T)` satisfies the reversed record, and the
empty continuation is admitted by `{n}`. The record gives `ret.f.$ (T) N`, then `r.f.$ (T) N` in the caller.
Run 3 therefore reports a false NORMAL finding. The forward cleaner is still correct; record application bypasses
the callee body. The false record and normal finding have computed subtype witnesses. The result also holds with
any demand store and record store that contains this bad record; adding other persistent records does not remove it.

The current backward mark exclusion blocks this concrete T application (`current_keep_blocks_T`). Passing the
abstract mark through the cleaner while retaining the normal layer breaks the exactness required for record reversal.

THE F75 REPAIR. Apply the same selected-mark rule in both directions. For the first root EXACT program above,
run 1 now splits `p.n.* (*) N` into `p.n.* (*∖{T}) N` and a request T on the premise `arg.* (*)`. Its concrete
answer follows this trace at limit 1:

```text
arg.$ (T) N → p.n.$ (T) N → ret.f.[any] (T) D
```

The concrete fact survives the root EXACT cleaner, since its path is `p.n`. The resulting demand is
`(D-c = ret.f.[any] (T), D-p = arg.$ (T))`. Run 2's sink requirement is `ret.f.n.$ (T)` and this demand keeps
its concrete mark. At limit 2 its trace is:

```text
ret.f.n.$ (T) N → p.n.$ (T) N → arg.$ (T) N
```

It hits the actual source and gives the eligible raw backward record `ret.f.n.$ (T) → arg.$ (T) N`.
Reversal gives `arg.$ (T) → ret.f.n.$ (T) N`. The next forward run retains the source, applies this record,
and reports `r.f.n.$ (T)` normally at limit 3. `ReviewBaseCleaner.Repaired.repairedWitness` computes the record
and includes the concrete sink flow. The hand-offs and source hit use the corrected closures, not an inserted demand.

In the second program, the backward abstract branch is `p.* (*∖{T}) N`, then `p.*/{n} (*∖{T}) N` on the
reverse write. Its reversed record is `p.* (*) → ret.f.*/{n} (*∖{T}) N`: the mark exclusion is on the conclusion.
It rejects the added `p.$ (T)`. The concrete root T branch stops at the same cleaner in either direction.
This blocks the false normal propagation shown by the pass-through experiment. The selected-mark base rule has
local coverage and normal exactness checks; whole-run coverage, source-mask completeness and general iteration
still require the migration in §11.2.

The axiom check is `lake env lean audit.lean`, after `lake build`. It checks every declaration of the imported
`ApSpec` modules, including private and generated declarations. Only `propext` and `Quot.sound` are admitted.
An axiom check proves no choice axiom was used; it does not give an executable witness API. Local emission has that
API (`emitWitness`); the general coverage and iteration witnesses remain proof-valued and need data-producing models.

General L1 to L4 must include the F74 field-action lowering condition. The X-tail extension and its tail guard,
confirmation support, narrowing, localization and the
source-seeded pipeline instance remain open. No full F72 iteration claim follows from the checked results above.
The proposals keep the F71 restricted-run rules until these obligations are complete, in the order of §11.2.
They include the independently checked local F74 lowering and F75 selected-mark primitive.


## AP proof limits at the cleaner checkpoint

### 11.2 Other limits

* The concrete semantics is alias-free and location-level (§3.5), and per root: every root starts with the zero fact
  only, so no static state crosses roots (as today; D27 clears it between Spring controllers on purpose;
  `interpreter.md` G13).
* The theorems are about the closures `D` (run 1), `DR` (a forward restricted run), `NDZ.DNz` (run 1 with
  conjunctions; the list model `ND.DN`), `Statics.DS` (run 1 with the static rule) and `Backward.DB` (the backward
  run), and, with the `[any-taint]` tail and its exclusion, the spec closures `AnyTaintEx.D6X` (run 1) and
  `AnyTaintEx.DRX` with `emitX`, `satX` and `HandoffX.restrictIX` (a forward restricted run; `AnyTaintEx.DRXs` with the
  earlier restriction), the round-1 closures without the exclusion `AnyTaint.D6T` and `AnyTaint.DRT`, and
  `AnyTaintND.DNzT` (run 1 with conjunctions, without W6T and without the exclusion) (§10.11, §10.12). The rules of
  the CONCRETE restricted runs of F70 and F71 are the emission of §6.3 for a concrete pattern, the satisfaction `inside`
  of §4.3 and the intersection of §6.4 (`emitM`, `satI`, `Handoff.restrictI`), with the hand-offs of §9.2 without the
  normalization (`Handoff.handF`, `demOfN`). The rules of F72 (the FLOW form, `satW`, no request rules, the hand-off
  normalization) are defined in the base model (§10.13). Their general coverage, X-tail extension and pipeline instance remain pending below. A real run differs from the
  modelled rules by optimizations. Each one keeps the soundness:

| Optimization | Why it keeps the soundness | Status |
|---|---|---|
| conclusion subsumption (§8.1) | the dropped pairs are pairs of the kept fact (`Subsume.subsumes_sound`) | the local step is proved; the composition is argued |
| merge rules 1 and 2, also for marks (§3.3, T1, T2, T2') | exact (`Tree.rule1_mem`, `rule2_den`, `rule2_mark`, `Subsume.merge_inter`, `merge_mark_inter`) | proved |
| the T5 fold | the denotation does not change (in a normal TAINT tree an `[any-taint]` leaf absorbs no `$` leaf, §7.2) | argued |
| persisted records (R4) | a normal edge has no false pair (a must-premise edge is end-exact, §10.11); adding edges keeps coverage (rule `retRec`, with `applicable` or `satI`). With the hand-off of the demand edges a crossable record is not only an optimization: it replaces the analysis of its callee, and the coverage needs it (§8.7 R5; `HandoffBackward.NextRecs`, `HandoffMain.iteration_generalN`, proved) | proved for one forward run under S14 (`RExact.recApp_markSub`, `RMain.p3_reuse_exact`); over the forward runs (`BExact.recsSeq_exact`, `recsSeq_exactV`, `seq_edge_exact`, `seq_confirmed_real`; `RecsFromRuns`); over the backward runs (`BExact.recsBSeq_exact`); and for the reversed summaries of one backward run (`BExact.revRecs_exact`, `Exact.FiltUp`). Argued: the two directions together, and the conjunction records (the list below) |
| W6 (`[any]` always in the demand layer; the old rule, the record of §10.3) | a layer refinement: the W6 run has the same facts, with the same or a raised layer (`W6.D_le_D6`, `D6_le_D`, `DR_le_DR6`, `DR6_le_DR`) | proved for `D` (under `W6.SummaryStar`, which `W6.summaryStar_policy` gives for the run-1 policy) and for `DR` (under `W6.RestrictLE` and `W6.ExactInitConc`, which `W6.restrictU_LE` and `W6.DR_eic` give): soundness, exactness, confirmation and the iteration (§10.3). Argued for `Statics.DS`, `ND.DN` and the backward run |
| W6T: W6 with W8 (only a may `[any]` result in the demand layer; the spec rule of the forward runs, §2.3) | run 1 without the exclusion: a layer refinement of `D` (`AnyTaintSim.D_le_D6T`, `D6T_le_D`); a forward restricted run with must-premises: the facts of `DR` (`AnyTaintSim.factSim`); the must-premise edges are end-exact | proved in the spec closures `AnyTaintEx.D6X` and `AnyTaintEx.DRX` (the theorems with the restriction as a parameter, for the intersection through `HandoffX.restrictIX_ok`; the instance `AnyTaintEx.DRXs` has the earlier restriction) (§10.11, §10.12), and in the round-1 closures `AnyTaint.D6T` (under `W6.SummaryStar`) and `AnyTaint.DRT` (under `EmitCopiesMark` and `AnyTaint.RestrictFact`): soundness, exactness, confirmation, the records and the iteration. Argued for `Statics.DS` and for `NDZ.DNz` (the layer raise on top of `AnyTaintND.DNzT`); the backward run keeps W6 (the list below) |
| the exclusion of `[any-taint]` (W8; §4.1, §4.7, §6.3, §6.4) | an exclusion removes only locations that no flow reaches (`AnyTaintExCov.applyEdgeX_sound`, `annX_admits`, `cleanResX_sound`); every normal result is exact on its admitted locations (`AnyTaintExExact.applyEdgeX_exact`, `cleanResX_exact`) | proved for `AnyTaintEx.D6X` and, with the earlier restriction, `AnyTaintEx.DRXs`: soundness (`AnyTaintExCov.iteration_reportsX`), exactness, confirmation and the records (§10.11); with the spec restriction `HandoffX.restrictIX` and the hand-off of the demand edges: soundness (`HandoffXIter.iteration_generalNX`; with the source seeds `HandoffSrc.iteration_srcNX`), the exclusion (`HandoffXMain.exclusion_canonX`), the narrowing (`HandoffXMain.narrowing_canonX`, `HandoffNoStar.narrowing_canonX_loc_exact`), and exactness, confirmation and the records through `HandoffX.restrictIX_ok` (§10.12). Argued: merge rule 2, the subsumption, T5 and the tree key with the exclusion (§3.3, §7.2, §8.1), the tree restriction (§7.4), `Statics.DS`, the conjunctions and the exactness of the seeded runs (the list below) |

* PENDING: THE LEAN MODEL OF F72 (`ap-history.md` F72; the user's order: the spec first, then the proofs, then the
  proposals `ap-impl.md` and `analyzer-impl.md`, which keep the F71 restricted-run rules until the proofs are done
  and include the independently checked local F74 lowering; the user: "Mark the
  updating proofs as the pending task"). The rules R1 to R5 of F72 with the F75 cleaner correction are normative (§4.3, §4.5, §4.7, §4.9, §6.3, §6.4,
  §9.2). Section 10.13 states the checked local F72 results. The earlier theorems of the restricted runs and of the iteration (§0.1,
  §6.1, §6.6, §10.7, the restricted parts of §10.8, §10.10 and §10.11, §10.12; `analyzer-core.md` §7.7, §7.8, §12)
  are proved for the CONCRETE restricted runs of F70 and F71: the emission `emitM` (`AnyTaintEx.emitX`), the
  closures `DR`, `AnyTaintEx.DRX` and `Backward.DB` with request rules that never fire, and the concreteness lemmas
  (`RExact.DR_concrete`). Their names stay; they hold for that design. The task:
  * THE DEFINITIONS (`AbsDefs.lean`, checked with the earlier cleaner; §10.13): `markNorm` (`*∖X` becomes `*`) and `normDem`, the
    hand-offs `handFA`, `demOfNA` (`Handoff.handF`, `demOfN` with `normDem`); `flowK`, `flowForm`; the emission
    `emitW` (nothing if `emitM d a` is nothing; else `flowForm d` if `d.mark = *`, else `emitM d a`); the satisfaction
    `satW` (`satI`, or `applicable` for a premise with the mark `*`); the closures `DRA` (forward: `DR` with no request
    rules) and `DBA` (backward: `Backward.DB` with no request rules: no `reqStmt`, `reqSink`, `reqClean`, `reqUp`,
    `answer`), with the emission, the satisfaction, the restriction and the records as parameters, as in `DR`; the
    mark-agnostic flow `FlowMA`; the witnesses with modes (`FlowRRA`, `ReachRRA`: the input form of `Handoff.FlowRR`,
    `ReachRR`; `FlowRDNA`, `ReachRDNA`: the output form of `FlowRDN`, `ReachRDN`); the contracts with modes
    (`CoversNA`, `BackwardContractNA`, as `Handoff.CoversN`, `BackwardContractN`).
    The corrected base closures are `ReviewBaseCleaner.RC`, `FC` and `BC`, with `BaseCleaner.cleanRes` in all three
    and requests only in `RC`. Migrate the abstract trace cleaner operation and its mode to the F75 base gate,
    then prove coverage and exactness for these corrected closures. The earlier embeddings into `DR` and `DB`
    do not supply that migration.
  * THE MODES (§6.6, CONTRACT B WITH MODES). A demanded witness carries a mode at each call that returns: ABSTRACT if
    a `*` pattern demands it (its inner flow is mark-agnostic), CONCRETE if a concrete pattern demands it (its inner
    flow as before; nested calls of either mode).
  * THE LEMMAS. L1 (forward abstract coverage): from a `*` premise that covers the entry location, a mark-agnostic
    flow is covered by an edge of the premise, with no request (no request rule exists). L2 (run 1 picks the right
    mode): corrected run 1 (`ReviewBaseCleaner.RC` with `policy1` and its requests) justifies every real witness, at each call by a FLOW premise
    (then the inner flow is mark-agnostic: the coverage followed it with no request) or by an answer (a concrete
    premise). L3 (backward abstract coverage): a `*` requirement covers the reversed flow of a mark-agnostic flow (the
    reversal of a `*`-mark micro edge has `*` marks, `Reverse.MarkRev`; a cleaner is its own reversal). L4 (contract B
    with modes): an abstract-mode call justified by a FLOW summary meets a `*` pattern in the next forward run; a
    concrete-mode call justified by a concrete summary meets a concrete pattern. L5 (exactness) is checked for the
    base closures under S7, exact input records and `FiltUp` or S13 (`AbsExact.lean`, §10.13); the backward forms
    also need the stated zero-binding and premise conditions. These closure theorems use the earlier cleaner;
    migrate them to `BaseCleaner.cleanRes` and the base-dependent mode. The X-tail extension remains open: its earlier
    exactness forms need `AnyTaintEx.EmitCopiesMarkX`, which `emitW` does not have. L6 (the emission contract) is
    checked under `PatTailB` and the concrete-added-mark condition (§6.1, §10.13). The guard is checked for the explicit alternating base sequence with concrete, non-star seeds (`AbsHandoff`).
    Its X-tail and pipeline forms remain open.
  * THE ITERATION: use the field-action lowering of §4.7, then prove the form of `HandoffMain.iteration_generalN`
    with the corrected closures and the modes; then the X closures
    (`AnyTaintEx.DRX` with the F72 emission), the source seeds, the finite forms and the driver. The local L5 and
    guarded L6 results of §10.13 do not supply L1 to L4 or this iteration proof.
  * THE CEGAR PROGRAMS, FIRST (closure invariants, as in `HandoffCases.lean`): (i) THE GETTER with two marks `T` and
    `U` under one `*` pattern (§6.3, THE GETTER): one FLOW premise or one record for both added facts, and both
    vulnerabilities reported; (ii) A MARK-CHANGING PASS RULE `T → U` inside a callee that a `*` pattern reaches: the
    `*` analysis gives nothing for it (a concrete premise mark), and the flow is found through the concrete pattern
    that the request and the answer of run 1 made; (iii) A PARTIAL CLEANER of `T` under a `*` premise: the fact
    continues as `*∖{T}` with no request on every same-base path, and the flow of `T` through the part that the cleaner does not clean comes
    from a concrete pattern; (iv) THE SEARCH FOR A COUNTEREXAMPLE TO R6: a flow that needs a concrete mark in a callee
    that only `*` patterns reach after run 1 (for example a concrete demand that the backward weakening loses). If R6
    fails, the program and the smallest rule change go to `ap-history.md`.
  * OPEN POINTS OF THE RULES that the model must decide: (a) L6 is false for a `*` pattern with the tail `*/E`,
    `E ≠ {}`: its FLOW premise `(x, ., */{f}, *)` and the added fact `(x, ., */{g}, *)` have common locations, but
    neither `inside` nor `applicable` holds (§4.3). The model must show that the hand-off gives no such pattern (a
    normal leaf with a `*` conclusion is crossable, §4.3, §6.3), or change the satisfaction or the FLOW form for it;
    (b) the general R6 proof must include the F74 field-action lowering condition (§4.7, §10.13);
    `ReviewReversal` checks an atomic cleaner and cannot establish the interpreter claim. F75 supersedes F73's
    same-base spatial mode and repairs the root EXACT regression locally; the general proof remains pending;
    (c) a `*` conclusion above a `*` exit pattern is kept whole
    (§6.4, the exception (b)): a precision point, and the
    exact meet at one path is representable (`Handoff.star_meet_exact`); (d) the static invariant of the restricted
    runs with FLOW premises on `S` (§4.10); (e) the exceptions of the intersection, the narrowing and the exclusion
    of §6.4 and §6.6 for the F72 runs; (f) the X-tail and tree forms of the FLOW index query (§8.4). The base-model
    queries are checked in `AbsStore` (§10.13).
  * THEOREMS THAT DO NOT TRANSFER TO F72. They describe the concrete design and stay in the model as its
    record: the concreteness of a restricted run (`RCov.concInvR_all`, `added_concR`, `RExact.DR_concrete`,
    `final_not_star`, `complete_premise_exact`, `BExact.DB_concrete`, `AnyTaintExCov.concX_all`,
    `AnyTaintExExact.DRX_conc`, `AnyTaintExKinds.DRX_normal_premise`, `Kinds.kinds_DR`, `kinds_DR_emitM`, `kinds_DB`,
    `kinds_DB_taint`: their hypothesis `EmitCopiesMark` (`EmitCopiesMarkX`) fails for the F72 emission under a `*`
    pattern); the absence of `*` exits and `*` patterns after run 1 (`Handoff.DR_exit_not_star`, `handF_DR_nonstar`,
    `HandoffNoStar.DB_init_nonstar`, `DB_edge_nonstar`, `demOfN_nonstar`, `canon_dem_nonstar`, `canon_handF_nonstar`,
    `DRX_init_nonstar`, `handF_DRX_nonstar`, `canonX_dem_nonstar`, `canonX_handF_nonstar`), and the exact narrowings
    that rest on it (`HandoffNoStar.narrowing_canon_fwd_exact`, `narrowing_canon_back_exact`,
    `narrowing_canon_loc_exact`, their forms `narrowing_canon_fwd_exactM`, `narrowing_canon_back_exactM`,
    `narrowing_canon_loc_exactM`, and `narrowing_canonX_fwd_exact`, `narrowing_canonX_loc_exact`). The request lemmas
    (`RCov.no_reqR`, `RExact.DR_no_request`, `RMain.no_request_M`, `BExact.DB_no_request`) derive "no request" from the
    concreteness; the F72 closures have no request rules, so the claim holds there by definition. These theorems still
    hold for the F72 rules: `HandoffNoStar.handF_run1_nonstar` and `run1_exit_star_cross` (they read run 1 only, which
    F72 does not change, and the normalization changes only marks).
* The backward run is modelled as the closure `Backward.DB` (the rules of `DR` on the reversed program, with the zero
  rules of §9.2), and it satisfies the contract B (with the hand-off of the demand edges and the intersection
  `HandoffBackward.B_generalN`; with the earlier hand-off `Backward.B_general`). The reversal of a conjunctive micro edge into
  one edge per literal with every result in the demand layer (§9.1, §9.2), the alias edges of the reversed call (§9.2;
  the model is alias-free, S2) and the interpreter's call-site rules of the backward run (`interpreter.md` §4.9) are
  argued, not modelled.
* ND edges are modelled for run 1 (`ND.DN`): coverage, the vulnerability theorems (§10.6) and the exactness of the
  normal layer against the support semantics `ND.TaintN` (`NDExact`, under S7, S9, S10 and S13) are proved. The
  CONFIRMATION of a vulnerability through a conjunction is proved too, with the joint support of §4.9 condition 3
  (`NDConfirmed.confirmed_real_N`), in run 1. No edge with two or more premises is a record (§8.7 R1). A restricted
  run with ND edges is ARGUED, not modelled:
  1. it has no request rule (§4.5): before F72 its facts were concrete, so a literal never raised a request; since
     F72 a literal on a fact with an abstract mark gives nothing;
  2. the restriction of a summary with several premises (§6.4, the special cases) only removes pairs from the
     conclusion;
  3. the contract B for a TREE witness (`ND.ReachAll`) needs a demand pattern on every node of the tree. The backward
     run reverses a conjunctive edge into one edge per literal (§9.2), so a requirement at the conclusion reaches every
     branch. Every result of these edges is in the demand layer (§9.1, THE REVERSAL OF A CONJUNCTION), so no backward
     summary through them is a record or crossable, and case 3 of §9.2 gives each branch its demand pattern: the next
     forward run analyses the callee again with every member, and no one-literal record replaces the conjunction.
  4. a one-member conjunction record (§8.7 R4) applies in a later run as an ordinary record, and it is reversed
     (R3). It is real for the support semantics in every caller context, because the zero binding supplies the zero
     premise that the zero-drop removed.

  `ND.D_sub_DN` embeds the distributive part, so the iteration theorem holds unchanged for a program without
  conjunctions.
* PREMISE SETS AND LISTS. The zero fact is a premise in the spec and in the model (§1). `ND.lean` keeps premise LISTS
  (with `[zeroFact]` for a zero-to-fact edge); this spec keeps SETS. A list and its set name the same entry locations,
  so every ND theorem carries over (argued). A conjunction drops the zero fact from the union of its premise sets
  (§4.6): the model edge with the premises `[zeroFact, i]` is the spec edge with the premise set `{i}`. This is PROVED
  (§10.10): the closure `NDZ.DNz` corresponds to `ND.DN` edge by edge (`NDZero.dnz_to_dn`, `dn_to_dnz`), because the
  zero fact is at every node that an edge reaches (`NDZero.zero_everywhere`); coverage, exactness and confirmation
  carry over (`NDZeroThms`). A conjunction result whose premise
  set has one member is an ordinary edge; it can be a record if it is normal, and it is exact for the support
  semantics (`NDZeroThms.nd_edge_exact_z`; through `NDZero.dnz_to_dn` it is the `ND.DN` edge `[zero, i]` of
  `NDExact.nd_edge_exact`).
* The static rule of §4.10 is modelled as a separate run-1 closure `Statics.DS`. Soundness, exactness and the
  iteration that starts from it are proved (`StaticsIter.iteration_general_DS`). The run-1 proof needs that a cleaner
  on `S` names its mark (S12 (e)). The interpreter makes it true: `RemoveAllMarks` on a position of `S`, at any depth,
  is the kill of a strong write, not a cleaner (`interpreter.md` §1.4). The one exception, the whole-base cleaner
  `(S, atAndBelow, all)` (then represented by `AnyClassStatic`), is argued: every fact on `S` lies inside it, so it
  drops each fact whole and raises no request; its effect is that of a statement that touches `S` with no micro edge on `S`, which S12 (a)
  allows. The restricted runs do not need it (`StaticsIter.SWFR`). The
  static rule and the conjunctions are in separate models: a conjunction literal on `S` uses the plain run-1 rules
  (§4.6).
* ARGUED, NOT PROVED. These claims of the spec have no Lean proof. Each item gives the claim in one sentence:
  * The source seeds (§6.1 rule 6) at a call, at the method start and at the method exit. The model has the
    statement sources only (`FSeeds.keepSources`); the others are the same micro edges at another place. The model
    keys a seed by the node before the statement; the implementation keys it by (method key, statement), which is the
    same on the JVM graph.
  * A seeded forward run is exact for `P`. `BExact.recsSeq_exact`, `seq_edge_exact` and `seq_confirmed_real` are
    proved for the runs on `P`. A seeded run has fewer flows (`FSeeds.flow_keep`), and it reads records that are exact
    for `P`, also a record of a source that is not a seed (§9.2; `HandoffSrc.SrcRec.found_unseeded`).
  * The source seeds with the static rule (`Statics.DS`) and with the conjunctions (`ND.DN`).
  * The backward run needs no static rule: proved if the reversed program satisfies S12 (a) to (d) and its seeds keep
    the invariant (`BExact.no_static_rule_backward`, `BExact.SeedsOK`); that the interpreter's reversed program
    satisfies them is argued (the static positions of the reversed program are the forward write targets).
  * Every persisted record keeps the static invariant `StaticsIter.RecOK` that the static theorems of the restricted
    runs assume: argued from the run-1 invariant (`Statics.cinv_all`), the restricted-run invariant
    (`StaticsIter.rinv_all`) and the backward-run invariant (`BExact.recOK_of_DB`), run by run.
  * W3 (every result of a run has at most `L` counted accessors when the field limit does not decrease) is argued.
  * A conjunction of `k > 2` literals is argued by chaining binary conjunctions; the model `ND.Conj` has two literals.
  * A conjunctive sink (§4.9) is argued as a conjunction to a fresh target with a sink on it; `ND.DN` has no
    conjunctive sink rule.
  * The cleaners of a call act on the bound fact before the callee (§5.3 step 4), and they give the added fact its own
    layer on the link (§8.3): argued, because the model `Call` has no cleaners (a cleaner is an instruction of the
    CFG, `Instr.clean`).
  * The summary rewriter (§4.7, §5.3 step 5) overrides the callee flow, so the analysis with it does not cover `Flow`
    (§3.5). The theorems read each call with selected rules as a WRAPPED call: the callee flow, then the cleaner
    `(P, exact, T)` of each selected rule, mark and position (for an `AnyField` position `(P, atAndBelow, T)` for a
    source, `(P, below, T)` for a cleaner, §4.7), on the callee results, before the binding back; the
    source results and the end facts of the call bypass the wrapper. Soundness and exactness with the override are
    argued against this overridden semantics, as for the call cleaners above.
  * A summary with several premises: its restriction (§6.4: every member inside a `D-c` of the method, one member
    inside the `D-c` of the restricting pattern) and its hand-off (§9.2: every leaf is a demand edge, one pattern per
    member) are argued.
  * The conclusion kinds of §7.2, and W2 (§2.3), for run 1 with the static rule (`Statics.DS`). `Kinds.kinds_D` is
    proved for `D`; `DS` adds the position answer `(S, p, *, {}, *)`, a premise with the mark `*`, and the mark answer
    on a static premise, an added fact with a concrete mark (§4.10 items 2 and 4). Each one has the premise mark of its
    kind, so the reasons of §7.2 hold for it. For run 1 with the conjunctions (`NDZ.DNz`): the ND edges are
    `Kinds.ndz_taint`; the single-premise edges are the item THE ZERO-BASE AND KIND INVARIANTS below.
  * The two exits of a JVM method (the normal exit and the exceptional exit) are one virtual exit of the one-exit model
    (S4): the exit rules act at both exits (so an unconditional exit sink can report at both), the backward zero fact
    and the seeds of the exit sinks start at both (S11 (e) reads the virtual exit), and only the normal exit makes a
    summary edge, because no exception flow crosses a call (below).
  * RECORDS ACROSS THE DIRECTIONS (§8.7 R3). A forward run reads the reversed backward records, and a backward run
    reads the reversed forward records. Each step is proved for `Exact.FiltUp` (`BExact.revRecs_exact`,
    `Reverse.backward_reuse_precise`, `RExact.recs_union`, `recs_of_DR`, `BExact.recsB_step`); the induction over the
    alternating sequence is argued. With type filters, a reversed backward record is not exact (§11.1). The soundness
    does not use the exactness of the records. With the hand-off of the demand edges it uses their PRESENCE: a
    crossable leaf that is not handed off must be a record of the next runs (`HandoffBackward.NextRecs`,
    `HandoffMain.iteration_generalN`). (With the earlier hand-off the record sets are free, `Backward.iteration_general`.)
    A backward summary through the reversal of a conjunction is never a record (§9.1, THE REVERSAL OF A CONJUNCTION):
    before F70 R3 reversed such a normal summary into a forward record of one literal, a false positive
    (`ap-history.md` F70). The rule is argued with the restricted runs with ND edges (above).
  * END FACTS in the restricted and backward runs. A real flow from an end fact is found in forward run `n + 2`. Its
    sink triggered in forward run `n` (an end fact exists only then). If the vulnerability of the sink is a DEMAND
    entry, it is seeded. If it is CONFIRMED, it is not seeded (§6.6, THE SEEDS), but a requirement of backward run
    `n + 1` that reaches the end fact reaches its reversed end-fact edge, and THE TRIGGER OF AN END FACT (§9.2) fires
    the sink seeds of its alternative there. In both cases contract B keeps the witness of the trigger demanded or
    recorded, so the sink triggers again in forward run `n + 2`, and the end-fact action is not restricted (§6.1 rule
    6). The reversed end-fact edge also gives the zero fact, read as a reversed unconditional source (§9.2); it only
    adds requirements. The model has no end facts. (With the seeds of the DEMAND entries only and no trigger rule, a
    real DEMAND vulnerability that rests on the end fact of a CONFIRMED sink is refuted: the program of §9.2 THE
    TRIGGER OF AN END FACT.)
  * THE HAND-OFF OF THE DEMAND EDGES (§6.4, §6.6, §8.7 R5, §9.2; `ap-history.md` F70, with the mark-aware
    restriction of F71). Proved (§10.12): its
    soundness (also with the source seeds of the statement sources, in the finite form and for the driver of §6.6
    with the seeds of the DEMAND entries only), the exclusion of one round and the narrowing of one round (in the
    locations and the marks), on the base closures and on the closures with the `[any-taint]` exclusion, and the
    absence of `*` demand patterns (so the narrowing has no exception; on the X closures the exact form is stated as
    locations only, `HandoffNoStar.narrowing_canonX_loc_exact`, and the marks narrow by
    `HandoffXMain.narrowing_canonXM`). All of it is proved for the concrete restricted runs; with F72 the `*` demand
    patterns occur, and these parts are PENDING (the item above). These parts are argued:
    * THE ZERO FACT IS NOT LOCALIZED (a later task). It still enters every callee: the backward rule `zin`, and the
      forward zero demand `(zero, none)` of every method (§9.2). So a method key that THE EXCLUSION (§6.6) excludes is
      still analysed from the zero fact in every run. With only the zero demand it publishes nothing (no `D-p`, no
      result, `HandoffCases.restrictI_none`), so it hands off no demand edge and stays out; the callers use its records
      (§8.7 R4). In run 1 a zero-premise leaf that is not crossable is a demand edge (run 1 publishes every summary).
      In a restricted run a zero-premise summary is published only through a demand pattern `(zero, jb)` (from a
      backward summary `jb → zero` that is not crossable), so a zero-premise leaf that is not crossable (for example
      `zero → (ret, ., [any-taint], T)` from a source in the method: its reversed premise is `[any]`) is a demand edge
      only for a method key with such a pattern. The frontier log measures this work: the method keys that only the
      zero fact reaches (`analyzer-core.md` §7.8). It is a limit of the cost, not of the soundness.
    * THE SEED PATHS ARE NOT NARROWED. The narrowing theorem leaves out the zero demand and the patterns `(gb, none)`
      of the zero-premise backward edges (§9.2 case 2). As locations they shrink when the backward field limit grows
      (a larger limit cuts a requirement less), but their COUNT can grow.
    * THE EXCLUSION OVER SEVERAL ROUNDS (§6.6). Each round is the one-round theorem (`HandoffExclusion.exclusion_theorem`;
      with the `[any-taint]` exclusion its backward parts and `HandoffXMain.forward_zero_initX`, `forward_zero_edgesX`),
      and its hypothesis "no demand edge of `M`" holds again after the round (`HandoffCases.restrictI_none`); the
      composition over the rounds is not stated.
    * NO `*` DEMAND PATTERN WITH THE STATIC RULE AND THE CONJUNCTIONS (§6.4; the concrete design: since F72 the later
      runs have `*` patterns, but run 1 still hands off none). That run 1 hands off no `*` entry
      pattern is proved for `D` (`HandoffNoStar.handF_run1_nonstar`). With the static rule (`Statics.DS`) a position
      answer is `*` with the Empty exclusion and the mark `*`, so its `*` exit edges are crossable too; with the
      conjunctions an ND edge has no `*` tail (W7). Argued.
    * THE STOP RULE `STOP_RULE` (§6.6): with no DEMAND entry the next backward run has no seed, so every later forward
      run only repeats the zero fact and the records. Argued: `HandoffExclusion.zinv_all` for every method with no
      seed, then `HandoffExclusion.exclusion_demand` per method; the composition is not stated. (That every real
      vulnerability is CONFIRMED at that stop is proved, §6.6.)
    * THE STOP RULE `NO_DEMAND_EDGE` (§6.6): in a complete forward run with no demand-layer edge, no demand-layer
      summary and no demand link, every sink witness and every link is normal, so each DEMAND entry fails only the
      joint support of §4.9 condition 3 (a conjunction). Argued from the conditions of §4.9 and the premises of a
      normal edge (`RExact.complete_premise_exact`, `AnyTaintExKinds.DRX_normal_premise`); no Lean theorem states it.
      A demand link must count: a call cleaner can demote an `[any-taint]` bound fact to `[any]` on its link (§2.2),
      the emission `[any] ∩ $ = $` then gives a premise that starts normal (§6.5), and every edge of the callee is
      normal, but the sink fails condition 3.2.2. A later run with a larger backward field limit can give the caller a
      `$` premise whose bound fact is apart from the cleaner: its link is normal, and that run confirms the sink. So
      without the demand links the rule stops too early. OPEN QUESTION: in such a run, if a premise set of a sink witness
      has no call that supplies every member, does no later forward run (a narrower demand, a larger field limit, more
      records) have such a call for a sink witness of the same key? No counterexample is known (with every link
      normal, an emitted member equals its added fact or lies inside a normal `[any-taint]` link, so a call that
      supplies the set in a later run supplies it in this run too), but a restricted run with ND edges is not modelled
      (above). So the rule does not claim that the report is final; its DEMAND entries stay in the output (§8.10).
    * THE TREE FORM AND THE INDEX OF THE INTERSECTION (§7.4, §8.6): the model proves them for the earlier restriction
      (`RStore.restrictTreeE_mem_U`, `restrictTree_cost`, `restrict_complete_U`); the meet at the node of `D-p.path`,
      the exclusion `E2` on a moved `[any-taint]` leaf, the inside test of the premise, and the mark tests of F71 (the
      premise key in its marks, the mark leaves against the mark of `D-p`, §7.4) are argued. So is the
      claim that the pieces that the run stores at each summary delta are the publications of the non-crossable
      leaves (§8.5: the restriction acts leaf by leaf).
    * PROGRAMS 1 AND 2 (§6.3, §6.4, §9.2): that the hand-off of the demand edges of backward run 2 is EXACTLY the three
      parts of §9.2 is checked by hand. The model proves that it contains the callee patterns and that forward run 3
      reports the vulnerability (`HandoffRCases.p1_handoff`, `p2_handoff`, `p1_chain`, `p2_chain`). PROGRAMS G AND C
      of the `[any-taint]` tail (§0.1, §4.9) with the intersection and the hand-off of the demand edges: the Lean runs
      use the earlier restriction and hand-off (`AnyTaintExCases2.G.run3_confirmed`, `C.run3_confirmed`). On these
      programs the two give the same cells (the must-premise lies inside its `D-c`, and `[any-taint] ∩ [any]` keeps
      `[any-taint]`), and the run-1 summary of the getter is in the demand layer, so it is a demand edge: checked by
      hand. The same for run 3 of program B (`AnyTaintExCases.B.run3_anyE_confirmed`, with a demand written by hand).
    * A DEMAND PIECE BESIDE A CROSSABLE PIECE (a cost limit). The normal and the demand trees of one premise key are
      separate edges, and no subsumption crosses the layers (§8.1). So a demand-layer piece whose pairs a crossable
      piece of the same premise and of the same demand pattern already has (a may next to an exact write:
      `M(p) { t.f = p; wrapInto(t, p); return t; }`, with `wrapInto` a may into `t.[AnyField]`, under the exit
      pattern `(ret, .f, $, T)`) is still a demand edge. The backward run enters the callee for it, the reversed may
      gives a demand backward leaf (case 3), and the callee stays in the frontier while the seed exists. Cost only. A
      rule that drops such a piece in the hand-off needs a Lean variant of `HandoffBackward.seg_genN` (later).
  * THE ZERO-BASE AND KIND INVARIANTS OUTSIDE `NDZ.DNz`. (a) In `DR` and `Backward.DB`, every conclusion on the zero
    base is the zero fact (proved for `NDZ.DNz`: `NDZeroBase.zero_base_inv`). The only edges onto the zero base are
    the zero keep edge, the zero binding and, in the backward run, the reversed sources and end-fact actions, whose
    target is the zero fact (`interpreter.md` I11 (d)). (b) The §7.2 partition for a one-member `NDZ.DNz` edge: a
    conjunction target (W7) over a concrete premise (S9). (c) The zero fact is at every node of a restricted run (the
    zero demand, §9.2), as the zero-drop needs.
  * The static exception at a call and at the method boundaries (§4.10 item 1): the rule statement of a call, the
    unresolved callee (also a call whose every callee is an empty method), the `RemoveAllMarks` kill on `S`, and the
    rule statements of the entry and the exits are read as `Instr.stmt` of the model's CFG, in the caller or before
    the exit; the binding `S.* → S.*` keeps the path.
  * A conjunctive micro edge whose target is a class position (`interpreter.md` §1.4; S12 (b)) has a `$` target and
    concrete `$` literals. Its result is a concrete `$` fact of the S12 (b) shape, so it keeps the static invariant.
    (`Statics.DS` has no conjunctions, and `ND.DN` has no static rule.)
  * THE `[any-taint]` TAIL (§2.3 W8, §10.11) outside the proved spec closures `AnyTaintEx.D6X` and `AnyTaintEx.DRX`
    with the spec rules (`emitX`, `satX`, `HandoffX.restrictIX`; and `AnyTaintEx.DRXs` with the earlier restriction)
    (and outside `AnyTaintND.DNzT`). (The kinds invariant of `AnyTaintEx.D6X` and the premises of a normal
    `AnyTaintEx.DRX` edge are proved, `AnyTaintExKinds.kinds_D6X`, `DRX_normal_premise`, `DRXs_normal_premise`, and so
    are the results of the round-1 programs G, C, I and P in the closures with the earlier restriction,
    `AnyTaintExCases2`; §10.11. The hand-off of the demand edges and the intersection with the exclusion are PROVED
    for these closures, not argued: the restriction, the coverage, the narrowing of one hand-off
    (`HandoffX.handF_narrowX`) and of one round (`HandoffXMain.narrowing_canonX`, `HandoffNoStar.narrowing_canonX_loc_exact`),
    the exclusion (`HandoffXMain.exclusion_roundX`, `exclusion_canonX`), the iteration (also with the source seeds and
    in the finite form) and the driver, §10.12.)
    * W6T and the exclusion in run 1 with the static rule (`Statics.DS`): the static rule does not read the layer or
      the exclusion of an any-tail fact, and `Statics.no_any_above` covers both any tails (§4.10), so W6T only raises
      layers, as W6 does, and an exclusion only removes locations that no flow reaches. The mark answer on a static
      premise drops the exclusion of an `[any-taint]/E` added fact (§4.10 item 4): a larger premise.
    * W6T and the exclusion with the conjunctions: `AnyTaintND.DNzT` has the conjunction rule of §4.6 but neither
      W6T nor the exclusion. W6T on top of it is a layer raise of the results of the may `[any]` targets: it removes
      normal edges and adds no fact. With the exclusion the store keeps an `[any-taint]/E` input only if its ADMITTED
      part overlaps the literal (§3.2, §4.6), and every admitted location carries the mark, so the step of
      `AnyTaintND.lit_loc` holds on the admitted part.
    * THE BACKWARD W6 WITH THE REVERSAL OF A MAY AND OF A CONJUNCTION (§9.1, §9.2): every `[any]` result of the
      backward run, and every result of the reversal of a micro edge whose forward target is `[any]` or of a
      conjunctive micro edge, is in the demand layer. This is not a
      pure layer raise in `Backward.DB`: a demoted requirement can reach the zero fact as a demand zero fact, and
      `Backward.DB` reads the normal zero fact. But wherever such a requirement of a zero premise reaches the zero
      fact, the normal zero edge of the zero premise is already at that node (the rule `start`, `Backward.ZeroKept`,
      the rule `zpass` from the seed node), and the hand-off and `FSeeds.srcHit` read every layer (a demand-layer
      backward leaf is always a demand edge, §9.2; the earlier `Backward.demOf` read every edge). So the rule only
      removes normal backward edges: it only removes records (a crossable leaf that it demotes becomes a demand edge),
      and it keeps the demand and the source hits.
    * Merge rule 2, the subsumption, T5 and the key of a TAINT tree with the exclusion (§3.3, §7.2, §8.1; in a normal
      TAINT tree no `$` leaf is absorbed or subsumed under an `[any-taint]` leaf), and the tree restriction with the
      exclusion (§7.4): the model has the per-path forms only (`AnyTaintEx.XFact`, `HandoffX.restrictConcIX`; the
      earlier `AnyTaintEx.restrictConcX`). Each one keeps the location set.
    * The seeded forward runs with the exclusion: the soundness is proved for both hand-offs (the earlier
      `AnyTaintExCov.iteration_srcX`; the hand-off of the demand edges `HandoffSrc.iteration_srcNX`,
      `iteration_srcNX_upto`, for the driver `PipelineHandoffDriverExt.driver_iteration_srcNX`); the exactness and the
      confirmation of a seeded run are argued as for the runs without the exclusion (above).
    * The reversed records across the directions with the exclusion (§8.7 R3): R3 reverses leaf by leaf, and not a
      leaf of a must record or an `[any-taint]/E` leaf with `E ≠ {}`; every other leaf reverses as before F69 (the
      model kind `.any`, `Reverse.rev_exact_of_empty_premise`). The induction over the directions is argued with the
      other record items.
    * `[any-taint]` in a restricted run with ND edges: as the other ND items of this list (the conjunction rule of
      §4.6 is proved for run 1 only, `AnyTaintND`).
    * The cleaners of a call and the summary rewriter (§4.7) on an `[any-taint]/E` fact: as the call-cleaner item
      above. Their rows are the rows of §4.7 (Lean `AnyTaintEx.cleanResX` for a cleaner of the CFG); the rewriter
      cleans an `AnyField` position of a selected source with `atAndBelow` (`interpreter.md` D34), and an `AnyField`
      position of a selected cleaner with its cleaner row `below` (`interpreter.md` §5.2).
    * The pipeline dominance: no instance of `Pipeline.quiescent_dominates` for `PipelineAnyTaintEx.sysD6X` and
      `sysDRX`, as for `PipelineAP` (`analyzer-core.md` §12).
    * The necessity of `AnyTaintEx.SatInsideX` for the rule `ret` (a summary, not a record): the round-1
      counterexample `AnyTaintExact.CexApp` shows it for a record; for a summary it needs a second call site.
* THE IMPLEMENTATION AGAINST THE MODEL. This is the only list of these differences. Each one keeps the theorems,
  except where the item says otherwise:
  * THE ANY TAILS, W6 AND W8 (§2.3; F69). The model has one tail kind `.any` for both any tails. The correspondence
    (Lean `AnyTaintDefs.lean` and `AnyTaintExDefs.lean`, their headers):

    | spec | model |
    |---|---|
    | `*/E` | `.star e` |
    | `$` | `.exact` |
    | an `[any]` conclusion (a may) | `.any` in the demand layer, with no exclusion |
    | an `[any-taint]/E` conclusion (a must) | `.any` in the normal layer, with a concrete mark and the exclusion `ex = E` (`AnyTaintEx.XFact`, `carriesB`, `normX`) |
    | an `[any-taint]/E` premise (a must-premise) | a premise with the flag `must = true` and the exclusion `jex = E` (`AnyTaintEx.XObj`, `startX`; round 1 `AnyTaint.TObj`, `startT`) |
    | a demand pattern | a `PFact` (`DemandEdge`): the hand-off drops the exclusions and the must flags (`AnyTaintExCov.forget6`, `forgetX`; the publication of an X run `HandoffX.pubRX`), so no pattern has the `[any-taint]` tail (§9.2) |
    | a summary leaf, a crossable leaf, a demand edge (§1) | an exit edge `j → g` of the model (one premise, one path conclusion); `Handoff.Cross j g` (a backward leaf: `Handoff.CrossB jb gb`); an element of `Handoff.handF` or `Handoff.demOfN` (a `DemandEdge`) |
    | a taint edge (S15) | a micro edge with `taint e = true` (`AnyTaint.TaintEdges`) |

    The Lean `applyEdge` gives a normal `.any` result on a normal `.any` input, or for an `.any` target on an exact
    derivation: that is the spec `[any-taint]`, and `Exact.edge_exact` proves it exact. The base model demotes a normal
    `.any` fact where an exclusion meets it (`belowCase` with a non-empty exclusion, `aboveCase`); the refined model
    replaces these demotions by the exclusion (`AnyTaintEx.annX`, `layerX`) and keeps the other demotions, the list of
    §2.2 (the cut `limitFX`; the `part` rows of `AnyTaintEx.partX` other than the `atAndBelow` and `below` cleaners one
    accessor below the fact; a may target, `w6tX`; a demand input, as the layer only rises; the record demotion
    `recLayerX`), and the start of an `.any` premise that is not a must-premise. The refined closures are proved on
    their own (§10.11); they are related to the round-1 closures by the simulations `AnyTaintExKinds.sim6X` and `simRX`
    (under `AnyTaintEx.NoBelowCleaner`), which no spec claim needs. They are not a layer refinement of `D` and `DR`:
    an exclusion removes locations, and the `below` cleaner one accessor below an `[any-taint]` fact adds the fact `(x, q.f, $, T)`, which the base model does not have
    (`AnyTaintEx.Vec.below_new_fact`; it is real, `AnyTaintExExact.below_new_fact_real`). The implementation puts only
    the result of a MAY `[any]` target in the demand layer (§4.1 step 6; the rule W6T, `AnyTaint.w6t`,
    `AnyTaintEx.w6tX`), and it names a demand `.any` result `[any]` (W8 (b)). In the round-1 closures W6T only moves
    edges from the normal layer to the demand layer. Soundness ignores the layer, and exactness and confirmation hold
    for every subset of the normal edges (`Invariant.demand_of_any_ok`). For the whole run, `AnyTaint.D6T` has the facts
    of `D` (`AnyTaintSim.D_le_D6T`, `D6T_le_D` under `W6.SummaryStar`), and `AnyTaint.DRT` has the facts of `DR`
    (`AnyTaintSim.factSim` under `EmitCopiesMark` and `AnyTaint.RestrictFact`). With an abstraction outside the spec,
    W6T can change a fact, not only its layer (`AnyTaintSim.CexT.cex_w6t_changes_fact`). THE OLD RULE W6 (every `[any]`
    result in the demand layer) is the record of `W6.lean` (§10.3: `W6.D6_le_D`, `D_le_D6`, `W6.DR_le_DR6`,
    `DR6_le_DR`; `W6.Cex.cex_w6_changes_fact`); every normal edge under it is a normal edge under W6T
    (`AnyTaintSim.D6_normal_D6T`). The backward run keeps W6 for every `[any]` result: argued (the list above). The
    Lean predicate `AFact.complete` (normal, and no `.any` conclusion) is the reading of a normal edge of the backward
    run only, which has no `[any-taint]` and keeps its `[any]` results in the demand layer (§1;
    `AnyTaintSim.backRecT`, `DB_any_premise_demand`). A normal forward edge of the spec is `demand = false` in the
    model, also with the `[any-taint]` tail; its records are the records with must flags and exclusions of §10.11
    (`AnyTaintEx.RecsExactX`, end-exact for a must record).
  * THE EXCLUSION IN THE TESTS OF §3.4. `covers`, `overlap`, `applicable` and `inside` read the exclusion of an
    `[any-taint]/E` fact or premise. The model reads it in `AnyTaintEx.satX`, `overlapX`, `checkX`, `emitX`,
    `restrictX` and the spec restriction `HandoffX.restrictIX` (its premise test `HandoffX.insideLocXB`), but its rule
    `ret` of run 1 and its rule `retRec` test the base `applicable`, and its rule `answer` of run 1 the base `overlapB`,
    without the exclusion of the added fact. So the implementation applies fewer records and makes fewer answers: one
    that the exclusion rejects has no admitted location in common with the added fact, and the coverage of the refined
    runs needs only the admitted locations (`AnyTaintExCov.coverage6X`; a crossable record that the exclusion rejects
    has no admitted location to carry, `HandoffX.cross_appliesX`). For the run-1 policy fact (the Empty
    exclusion) both tests are equal. Argued.
  * Universe (§1). The model keeps the exclusion `Excl.univ`: it encodes a `$` premise in the operation tables
    (`tailExcl`). The AP has no Universe exclusion (S8).
  * The preconditions of `concat` (§4.1). The model computes a result for an edge that breaks S7 or S8; the
    implementation asserts that no such edge exists.
  * The cleaner request (§4.7). The model `cleanRes` also raises the request `T` for a `*∖X` fact with `T ∈ X` (the
    first row of the result table). The implementation does not raise it: an answer for `T` only makes facts that an
    earlier cleaner already cleaned, so the request costs work and adds nothing.
  * The effective mark of the sink check (§4.9). The model `check` reads the effective mark (§1), so it also triggers
    for an abstract `f.mark` under a concrete `i.mark`. This case does not occur (`Coverage.edge_conc`); the
    implementation asserts it.
  * The type filters of the backward run (§9.2). The model keeps the type filters in the backward run; the
    implementation drops them. This only adds backward flows, so it only enlarges the demand, and its reversed records
    are not exact (§11.1).
  * End facts (§4.9). The model has none. Their exactness: §11.1. Their coverage: argued (above).
  * The field limit on a conjunction result (§4.6). The model rules `ND.DN.conj` and `NDZ.DNz.conj` apply no field
    limit to their result. The implementation applies it: it only enlarges (`limitF_sound`) and raises the layer.
* The interpreter (`interpreter.md`) is outside the model, except through S1, S2, S5 and S7 to S13. Its known gaps are
  listed in `interpreter.md` §0.1. The reading of a negated mark literal as true (S1) is not modelled: the model has no
  rule conditions.
* Exceptions are out of scope: no exception flow crosses a call, and a catch block does not read `exc`
  (`interpreter.md` §3.4, G1).
* The tree theorems cover the `*`-to-`*` micro edges with the mark `*` on both sides; other micro edges use the per-path
  operation on the touched subtree. The mark gate and the field limit inside the tree (§7.3) and the T5 fold are not
  modelled in `Tree.lean`.

---


## AP module catalog at the cleaner checkpoint

## 12. The formal model

| File | Content |
|---|---|
| `Basic.lean` | The definitions of the run-1 closure `D` (no static rule, no conjunctions; `policy1` is in `Restricted.lean`): locations, facts (marks `*`, `T`, `*∖X`), `den`, `applyEdge` with `markComp`, the normal form, the field limit, statements, calls, cleaners (`Cleaner`, `cleanPos`, `cleanRes`), type filters, `Flow`, the closure `D`, `Reach`, `answerInit`, `policy`, `revEdge`. |
| `Backward.lean` | The backward run (`DB`), the earlier hand-offs, the contract B for it (general and for programs 1 and 2). |
| `Statics.lean` | The static rule of §4.10 as the run-1 closure `DS`: soundness, exactness, the invariant, the worked programs, the failing variants. |
| `StaticsIter.lean` | No static rule after run 1: the invariant of forward restricted runs, the iteration from `DS`, the worked programs through forward run 3. |
| `NDConfirmed.lean` | The confirmation through a conjunction (joint support), with its counter-examples. |
| `StaticsConfirmed.lean` | The confirmation of run 1 with the static rule. |
| `BackwardExact.lean` | The backward run: concreteness, no request, exactness of its edges, exact reversed records; record exactness over the run sequence; the static invariant of the backward run. |
| `W6.lean` | W6 for the whole run: the simulation, soundness, exactness, confirmation, the iteration. |
| `Restricted.lean` | The restricted runs: demand patterns (`DemandEdge`), the emission `emitM`, the satisfaction `satI`, the earlier restriction `restrictU` (the spec restriction `Handoff.restrictI` is in `HandoffDefs.lean`), the closure `DR`, `FlowR`, `ReachR`, the contracts, `summaryDemand`, `BackwardContract`, and auxiliary rules that some proofs use (for example `restrictS`, §10.7). The version-3/4 rules `emitU`, `emitS`, `satU`, `satS`, `satO` and their theorems (`RMain.*_S`, `RMain.closed_records_exactR`) are a record of the review; no spec claim uses them. |
| `ND.lean` | ND edges: conjunctive micro edges, the support semantics `TaintN`, the closure `DN`, the ND coverage and vulnerability theorems. |
| `NDExact.lean` | The exactness of normal-layer ND edges against `ND.TaintN` (`NDExact.LitConc` = S9, `ConjOK`), with the two counterexamples. |
| `Cases.lean`, `RestrictedCases.lean` | Test vectors (`decide`), programs 1 and 2. |
| `Core.lean`, `SharedExcl.lean`, `RestrictedCore.lean` | The local lemmas. |
| `Coverage.lean`, `RestrictedCoverage.lean`, `RestrictedMain.lean` | Soundness of run 1 and of the iteration; the instantiation with the earlier restriction `restrictU` (§10.7). |
| `Exact.lean`, `Invariant.lean`, `Closed.lean`, `Confirmed.lean`, `RestrictedExact.lean` | Exactness, invariants, the records of a request-free initial fact, confirmed vulnerabilities. |
| `Tree.lean`, `Store.lean`, `Subsume.lean`, `RestrictedStore.lean` | Concept against optimization. |
| `Reverse.lean` | Reversal. |
| `ForwardSeeds.lean`, `PipelineSeeds.lean` | The source seeds of the forward restricted runs (§6.1 rule 6, §9.2, §10.9). |
| `Kinds.lean` | The conclusion kinds of §7.2: FLOW, TAINT, REACH; ND edges are TAINT (§10.10). |
| `NDZ.lean`, `NDZero.lean`, `NDZeroThms.lean`, `NDZeroBase.lean` | The ND closure of the spec with the zero-drop (`NDZ.DNz`), its correspondence with `ND.DN`, its theorems, and the zero-base invariant (§4.6, §10.10). |
| `PipelineNDZ.lean` | The pipeline encoding of `NDZ.DNz` (`analyzer-core.md` §5.5). |
| `Pipeline.lean`, `PipelineProofs.lean`, `PipelineAP.lean`, `PipelineStore.lean`, `PipelineDriver.lean` | The analyzer pipeline of `analyzer-core.md` (its §12): the no-loss theorem, the encodings of the closures, the index lookups, the driver. |
| `AnyTaintExDefs.lean` | THE SPEC CLOSURES of the `[any-taint]` tail with its exclusion (§2.3 W8, §10.11), definitions in the namespace `AnyTaintEx`: the annotated fact `AnyTaintEx.XFact` (`carriesB`, `normX`), the location tests with the exclusions (`coversX`, `coversFX`, `denX`, `overlapX`, `insideExB`, `satX`), the refined operations (`annX`, `layerX`, `applyEdgeX`, `applySummaryX`, `w6tX`, `transferX`, `limitFX`, `cleanPosX`, `partX`, `cleanResX`, `checkX`, `startX`, `recLayerX`, `emitX`, `emitTX`, the earlier restriction `restrictX`), the closures `D6X` (run 1), `DRX` and `DRXs` (a forward restricted run; `DRX` with the spec restriction `HandoffX.restrictIX` of `HandoffXRestrict.lean` is the spec run), the relations to the round-1 closures (`Refines6`, `RefinesR`, `Sim6X`, `SimRX`, `NoBelowCleaner`, `RecsRefine`: statements; `AnyTaintExKinds.lean` proves the simulations), the predicates of the proofs (`EdgeOKX`, `EndExactX`, `RecsExactX`, `SatInsideX`, `EmitCopiesMarkX`, `SupLinkX`, `SupX`, `Confirmed6X`, `ConfirmedX`), the vectors `AnyTaintEx.Vec`. |
| `AnyTaintExCov.lean` | The soundness of the spec closures: the local soundness, the contracts with the exclusions, the coverage of run 1 and of a restricted run, the iteration (`AnyTaintExCov.iteration_reportsX`, `iteration_srcX`), the counterexample `AnyTaintExCov.CexRoute`. |
| `AnyTaintExExact.lean` | The exactness of the spec closures (pair-exact, and end-exact for a must-premise, on the admitted locations), the records, the support and the confirmation; the run sequence; the counterexamples `AnyTaintExExact.CexAbove`, `AnyTaintExExact.CexExactCleaner`, `AnyTaintExExact.CexRestrictSub`, `AnyTaintExExact.CexSideConditions`. |
| `AnyTaintExCases.lean` | The worked programs of round 2: S, SD, B, X, R, CL, CUT (§10.11). |
| `AnyTaintExKinds.lean` | The kinds invariant of the spec run 1 (`AnyTaintExKinds.D6X_any_conc`, `D6X_flow_no_any_taint`, `D6X_flow_no_excl`, `kinds_D6X`, `kinds_D6X_gen`), the premises of a normal edge of a spec restricted run (`AnyTaintExKinds.DRX_normal_premise`, `DRX_must_premise`, `DRXs_normal_premise`, `DRXs_must_premise`), and the simulations of the refined runs by the round-1 runs (`AnyTaintExKinds.sim6X`, `sim6XIn`, `simRX`, `simRXIn`) (§2.3, §7.2, §9.1, §10.11). |
| `AnyTaintExCases2.lean` | The round-1 worked programs G, C, I and P re-derived in the spec run 1 `AnyTaintEx.D6X` and in `AnyTaintEx.DRXs` (the earlier restriction), and the carry-over of a run with no exclusion edge (`AnyTaintExCases2.Carry`) (§4.9, §6.2, §6.3, §10.11). |
| `PipelineAnyTaintEx.lean` | The pipeline encodings of `AnyTaintEx.D6X` and `AnyTaintEx.DRX` (`analyzer-core.md` §12). |
| `PipelineAnyTaintExDriver.lean` | The iteration driver over the encodings of `AnyTaintEx.D6X`, `Backward.DB` and `AnyTaintEx.DRXs`, with the earlier hand-off. |
| `AnyTaintDefs.lean` | ROUND 1 (the `[any-taint]` tail without the exclusion: the model of the first F69 text, §10.11), definitions in the namespace `AnyTaint`: the taint edges and S15 (`AnyTaint.TaintEdges`, `TaintConc`), `BindNoAny` (S10), the rule W6T (`w6t`, `transferT`), the closures `D6T` (run 1) and `DRT` (a forward restricted run with must-premises: `TObj`, `emitT`, `startT`, `recLayer`), the predicates of the proofs (`EndExact`, `EdgeOKT`, `RecsExactT`, `SatInside`, `SupLink`, `SupT`, `ConfirmedT6`, `ConfirmedT`), the vectors `EmitVec` and `Sanity`. |
| `AnyTaintSim.lean` | ROUND 1: the soundness by simulation (run 1 against `D`, a restricted run against `DR`), the kinds invariant, the iteration, the backward `[any]` premises and the backward records. |
| `AnyTaintExact.lean` | ROUND 1: the exactness (pair-exact, and end-exact for a must-premise), the records, the support and the confirmation of `AnyTaint.D6T` and `AnyTaint.DRT`; the run sequence; the counterexamples. |
| `AnyTaintND.lean` | The conjunction rule of §4.6 for an `[any-taint]` input (`AnyTaintND.DNzT`, without W6T and without the exclusion): simulation, coverage, exactness, confirmation, the worked example. |
| `AnyTaintCases.lean` | ROUND 1: the worked programs G, C, W, P, I, S and the cut (§10.11; S with the demotion that the exclusion replaces). |
| `PipelineAnyTaint.lean` | ROUND 1: the pipeline encodings of `AnyTaint.D6T` and `AnyTaint.DRT` (`analyzer-core.md` §12). |
| `PipelineAnyTaintDriver.lean` | ROUND 1: the iteration driver over the encodings of `AnyTaint.D6T`, `Backward.DB` and `AnyTaint.DRT`. |
| `HandoffDefs.lean` | THE HAND-OFF OF THE DEMAND EDGES (decision F70; §6.4, §9.2, §10.12), definitions in the namespace `Handoff`: the restriction as an intersection, mark-aware since F71 (`restrictI`, `insideB`, `insideLocB`, `concMarkB`, `restrictConcI`, `meetConcK`), the crossable records (`CrossK`, `Cross`, `revRec`, `CrossB`), the publications (`Pub`, `pubD`, `pubR`), the hand-offs (`handF`, `demOfN`), the witnesses (`FlowRR`, `ReachRR`, `FlowRDN`, `ReachRDN`) and the contracts (`CoversN`, `BackwardContractN`). |
| `HandoffRestrict.lean`, `HandoffCoverage.lean` | The restriction (C5, the intersection and its exceptions, the narrowing of one run, each also with the marks of F71, the vectors `Handoff.RVec`) and the coverage of the restricted forward run and of run 1 (§10.12). |
| `HandoffBackward.lean`, `HandoffIter.lean`, `HandoffMain.lean` | The backward contract of the backward run with the new hand-off; the abstract iteration; the canonical run sequence, the iteration theorem, the exclusion and the narrowing on it (§6.6, §10.12). |
| `HandoffExclusion.lean` | The exclusion theorem: a method key with no demand edge and no seed below is analysed only from the zero fact (§6.6, §10.12). |
| `HandoffCases.lean` | The program WRAP (the earlier hand-off against the new one), the CEGAR of `Cross` (programs ANYW and ANYM), a getter (§6.6, §8.7 R3, §10.12). |
| `HandoffXRestrict.lean`, `HandoffXCoverage.lean`, `HandoffXIter.lean`, `PipelineHandoffDriver.lean` | The hand-off of the demand edges on the closures with the `[any-taint]` exclusion: the restriction `HandoffX.restrictIX`, the coverage, the iteration, the driver with every reported vulnerability seeded (§10.12). |
| `HandoffXMain.lean` | The exclusion and the narrowing over one round on the closures with the `[any-taint]` exclusion (§6.6, §10.12). |
| `HandoffNoStar.lean` | No demand pattern has a `*` tail, so the narrowing is exact (the base and the X sequence); W2 of the backward run (§2.3, §6.4, §6.6, §10.12). (The concrete design: since F72 the later runs have `*` patterns, §11.2.) |
| `HandoffSrc.lean`, `HandoffUpto.lean`, `HandoffRCases.lean` | The source seeds with the hand-off of the demand edges (contract B item 3, the iteration, base and X); the finite forms; programs 1 and 2 with the intersection (§6.3, §6.4, §6.6, §9.2, §10.12). |
| `PipelineHandoffDriverExt.lean` | The driver with the seeds of the DEMAND entries only, its finite form and its source seeds (§6.6, §10.12). |
| `AbsDefs.lean`, `AbsClosure.lean`, `AbsCases.lean`, `AbsExact.lean`, `AbsWitness.lean`, `AbsStore.lean`, `AbsForward.lean`, `AbsHandoff.lean`, `ReviewDemand.lean`, `ReviewReversal.lean`, `CleanerLowering.lean`, `FieldCleanerX.lean`, `ReviewFieldCleaner.lean`, `ReviewRootCleaner.lean`, `ReviewBackwardCleaner.lean`, `BaseCleaner.lean`, `ReviewBaseCleaner.lean` | The F72 definitions, local proofs, executable witnesses, index equivalence and review regressions. Their assumptions and open obligations are in §10.13. Earlier closures keep the spatial cleaner. F74 checks the field lowering. F75 checks the local base-dependent operation, batch equivalence, corrected base-model closures and both root EXACT traces. The backward pass-through experiment remains a rejected candidate. |

THE MODEL OF F72 IS NOT COMPLETE (§10.13, §11.2). The `Abs` files prove local results. The general coverage and
iteration, the X-tail extension and the analyzer pipeline remain pending. The earlier restricted-run files model
the concrete design of F70 and F71.

Lean names. A qualified name `F.x` in this spec names the declaration `x` in the namespace `ApSpec.F`, or in the file
`F.lean`. The short namespaces: `RCore` is `RestrictedCore.lean`, `RCov` is `RestrictedCoverage.lean`, `RExact` is
`RestrictedExact.lean`, `RMain` is `RestrictedMain.lean`, `RCases` is `RestrictedCases.lean`, `RStore` is
`RestrictedStore.lean`, `BExact` is `BackwardExact.lean`, `FSeeds` is `ForwardSeeds.lean`, and `CoreAux` is in
`Core.lean`. The declarations of `Basic.lean`, `Core.lean` and `Restricted.lean` are in the namespace `ApSpec` itself
(for example `applyEdge`, `satI`, `emitM`, `restrictU`, `markMatchB`); `Core.x` names a declaration of `Core.lean`.
Two files share the namespace of another file: `NDZeroThms.lean` is in the namespace `NDZero` (so `NDZeroThms.x` is the
declaration `NDZero.x` of the file `NDZeroThms.lean`), and `PipelineProofs.lean` is in the namespace `Pipeline` (with
the sub-namespaces `Pipeline.PCex` and `Pipeline.Quiesce`). `Cases.lean` has the namespaces `Cases` and `CleanCases`.
`AnyTaintDefs.lean` is in the namespace `AnyTaint` (so `AnyTaint.x` is a declaration of `AnyTaintDefs.lean`; no file
`AnyTaint.lean` exists), and `AnyTaintExDefs.lean` is in the namespace `AnyTaintEx` (so `AnyTaintEx.x` is a
declaration of `AnyTaintExDefs.lean`; no file `AnyTaintEx.lean` exists). `HandoffDefs.lean`, `HandoffRestrict.lean` and
`HandoffCoverage.lean` are in the namespace `Handoff` (with the sub-namespaces `Handoff.RVec` and `Handoff.RAux`; no
file `Handoff.lean` exists), and `HandoffXRestrict.lean` and `HandoffXCoverage.lean` are in the namespace `HandoffX`
(with the sub-namespaces `HandoffX.XVec` and `HandoffX.XAux`; no file `HandoffX.lean` exists). This spec writes the
declarations of these five files with their namespace: `Handoff.restrictI_sub` is the declaration `restrictI_sub` of
`HandoffRestrict.lean`, and `HandoffX.restrictIX_ok` is the declaration `restrictIX_ok` of `HandoffXRestrict.lean`.
`HandoffCases.lean` has the sub-namespaces `HandoffCases.Wrap`,
`VecCross`, `AnyW`, `AnyM` and `Getter`; `HandoffSrc.lean` has `HandoffSrc.SrcRec`, `HandoffXMain.lean` has
`HandoffXMain.XMVec`, and `HandoffNoStar.lean` has `HandoffNoStar.NSVec`. Every other file has the namespace of its name
(`Statics`, `StaticsIter`, `StaticsConfirmed`, `Backward`, `Reverse`, `ND`, `NDExact`, `NDConfirmed`, `NDZ`, `NDZero`,
`NDZeroBase`, `Kinds`, `W6`, `Store`, `Subsume`, `Tree`, `Pipeline`, `PipelineAP`, `PipelineStore`, `PipelineDriver`,
`PipelineSeeds`, `PipelineNDZ`, `AnyTaintSim`, `AnyTaintExact`, `AnyTaintND`, `AnyTaintCases`, `PipelineAnyTaint`,
`PipelineAnyTaintDriver`, `AnyTaintExCov`, `AnyTaintExExact`, `AnyTaintExCases`, `AnyTaintExKinds`,
`AnyTaintExCases2`, `PipelineAnyTaintEx`, `PipelineAnyTaintExDriver`, `HandoffBackward`, `HandoffIter`,
`HandoffExclusion`, `HandoffMain`, `HandoffCases`, `HandoffXIter`, `PipelineHandoffDriver`, `HandoffXMain`,
`HandoffNoStar`, `HandoffSrc`, `HandoffUpto`, `HandoffRCases`, `PipelineHandoffDriverExt`, and so on). The sub-namespaces of the
`[any-taint]` files: round 2: `AnyTaintEx.Vec`; `AnyTaintExCov.CexRoute`; `AnyTaintExExact.CexAbove`,
`CexExactCleaner`, `CexRestrictSub`, `CexSideConditions`; `AnyTaintExCases.S`, `SD`, `B`, `X`, `R`, `CL`, `CUT`;
`AnyTaintExCases2.G`, `C`, `I`, `PassRule`, `Carry`; round 1: `AnyTaint.EmitVec`, `AnyTaint.Sanity`;
`AnyTaintSim.CexKinds`, `AnyTaintSim.CexT`; `AnyTaintExact.CexApp`, `CexRev`, `CexSupMark`, `CexRecConc`;
`AnyTaintND.Example`; `AnyTaintCases.G`, `C`, `W`, `PassRule`, `Cut`, `I`, `S`. Some sub-namespaces have the same
name in two files (`AnyTaintCases.S` and `AnyTaintExCases.S`; `AnyTaintCases.G`, `C`, `I`, `PassRule` and
`AnyTaintExCases2.G`, `C`, `I`, `PassRule`; `Statics.CexAbove` and `AnyTaintExExact.CexAbove`): this spec writes them
with the name of their file. If neither `ApSpec.G` nor
the file `G.lean` has the declaration `x`, the name `G.x` is in the sub-namespace `G` of the namespace of the file of
the last qualified name before it: `CexWide.w_vuln_normal` after `Statics.CexAbove.y_vuln_normal` is
`Statics.CexWide.w_vuln_normal`, and `CexSeed.cex_seed` after `BExact.DB_no_requestM` is `BExact.CexSeed.cex_seed`.
A name with no qualifier belongs to the namespace or the file of the last qualified name before it, or to `ApSpec`
itself (for example `den`, `DR`, `applicable`). In the tables of §10, a name with no qualifier can also belong to a
file that the heading of its section names.

Build and audit:

```
cd spec/lean && rm -rf .lake/build/lib/lean/ApSpec* && lake build > build.log 2>&1
grep "depends on axioms" build.log | sed 's/.*axioms: //' | sort | uniq -c
grep -c "does not depend on any axioms" build.log
```

(A build prints the audit only for the modules that it compiles, so remove the old `ApSpec` build files first.)

The audit passes when the only sets are `[propext]` and `[propext, Quot.sound]` (and declarations with no axioms),
with no `sorry`, no `native_decide` and no `Classical`. The kernel checks the `example` vectors, almost all by
`decide`, and the derivations of the counterexample and example programs.

---


## Analyzer proof catalog at the cleaner checkpoint

### 5.5 The no-loss theorem

THEOREM (never lose a summary edge). Let a reachable state of a well-formed encoded system be quiescent (§6.2). Then:

* every join of processed subscriptions with a processed publication has its result processed
  (`Pipeline.no_lost_join`): the caller has the application of every publication to every subscription that satisfies
  it;
* every object of the closure is processed (`Pipeline.quiescent_complete`): every link reaches its callee, and every
  (request, link) pair for which §4.6 gives an ANSWER or a CLIMB has it.

The model (`Pipeline.lean`) is a rule system with owners (`Sys`). It has LOCAL rules, whose premises all belong to one
actor, and JOINS of subscriptions with a publication. Its closure `Cl` is the concept. The implementation is an
interleaving transition system (`Step`) with these steps:

* `proc`: an actor processes an object and fires its local rules. A subscription registers its handler and schedules
  its replay. A publication is inserted and schedules its notification.
* `replay`, `notify`, `deliver`: the three shared steps of §5.2 and §5.3.
* `dup`: a duplicate is dropped.

The model lets every actor act between the two parts of a subscription and of a publication. A real schedule lets
only the actors of the other runners act there. So every real schedule (A2) is a schedule of the model, and the
theorems hold for every real schedule.

| Theorem | Statement |
|---|---|
| `Pipeline.reach_sound` | every object that a reachable state holds is in `Cl` |
| `Pipeline.quiescent_complete` | for a well-formed system, at a reachable quiescent state, every object of `Cl` is processed |
| `Pipeline.quiescent_exact` | for a well-formed system, at a reachable quiescent state, the processed objects are exactly `Cl` |
| `Pipeline.no_lost_join` | for a well-formed system, at a reachable quiescent state, every join of processed subscriptions with a processed publication has its result processed |
| `Pipeline.PCex.cex_P1` to `cex_P4` | without P1, P2, P3 or P4: a reachable quiescent state that misses an object of `Cl`. In the variants of P1 and P2, the read and the write are two steps. `PCex.step_finds_edge`: the correct protocol finds the object in the same system |
| `PipelineAP.clD_iff`, `clDR_iff`, `clDB_iff`, `clDS_iff`, `clDN_iff` | On the objects of the AP closure, the closure of the encoded system is exactly the AP closure. The closures: run 1 (`D`), a restricted run (`DR`), the backward run (`DB`), run 1 with the static rule (`DS`), run 1 with the conjunctions (`DN`). The partial matches of `DN` are internal to the k-ary join (`clDN_npart`). |
| `PipelineAP.clD_link`, `clD_sub`, `clD_pub` and their `DR`, `DB`, `DS`, `DN` forms | the link, the subscription and the publication objects are exactly the data that the closure rules read |
| `PipelineAP.sysD_wf` and the other `*_wf` | each encoded system is well-formed (`Pipeline.Sys.WF`): every local rule has at least one premise, all of one actor; every join has subscriptions of one actor and one topic and a publication |
| `PipelineNDZ.sysDNz_wf`, `clDNz_iff`, `result_DNz`, the object theorems (`clDNz_link`, `clDNz_sub`, `clDNz_pub`, `clDNz_ndpub`, `clDNz_npart`) | the encoding of the ND closure of the spec, `NDZ.DNz` (the union of the premise sets without the zero fact, `ap.md` §4.6, §10.10): it is well formed, and at a reachable quiescent state the processed objects are exactly `DNz` (no partial match) |
| `PipelineNDZ.clDNz_ndpub_no_zero`, `clDNz_ndpub_zero_sub`, `joinNz_nd_no_zero_sub` | no index of a k-ary join is the zero fact, and (under `NDZeroBase.NoZeroGen` and `AlphaZero`, which the run-1 policy satisfies, `NDZeroBase.policy1_alphaZero`; run 1) the zero subscription satisfies no index (§5.4) |
| `PipelineAnyTaintEx.sysD6X_wf`, `sysDRX_wf`, `clD6X_iff`, `clDRX_iff`, `result_D6X`, `result_DRX`, `result_DRXs`, the object theorems (`clD6X_link`, `clD6X_sub`, `clD6X_pub`, `clDRX_link`, `clDRX_sub`, `clDRX_pub`) | the encodings of the closures of the tail `[any-taint]` with its exclusion (`ap.md` §10.11): run 1 with the layer rules W6 and W8 and the exclusion (`AnyTaintEx.D6X`) and a restricted forward run with must-premises and exclusions (`AnyTaintEx.DRX`, generic in the rules; the spec instance has `emitX`, `satX` and `HandoffX.restrictIX`, before F70 `AnyTaintEx.DRXs`). They are well formed, and at a reachable quiescent state the processed objects are exactly the closure, with the must flags and the exclusions (THE ENCODING WITH `[any-taint]`, below). `PipelineAnyTaintEx.SanityX.d6x_ann`, `drx_ann`: a normal `[any-taint]/{name}` edge is in both closures, so the encodings are not vacuous on the exclusion |
| `PipelineAnyTaintEx.known_D6X`, `known_DRX`, `no_lost_summary_D6X`, `no_lost_summary_DRX` | every processed object of a reachable state is in the closure (`Pipeline.reach_sound`), and the summary edge is never lost (`Pipeline.no_lost_join`): in `PipelineAnyTaintEx.sysDRX` a subscription of the caller premise and a publication of the callee premise, each with its must flag and its exclusion, whose join condition holds have their caller edge processed |

The encoding (`PipelineAP.lean`): actor = method, topic = callee.

| AP rule | In the pipeline | Owner of the premises → of the conclusion |
|---|---|---|
| `root` | a root object | → the root method |
| `start`, `step`, `pass`, `clean`, `filt`, `reqStmt`, `reqClean`, `reqSink`, `vuln`, `retRec`, `zpass`, `seed`, `conj`, `reqConj` | local rule | the method → the method |
| `added` | local rule that makes the LINK (a message to the callee); then the local rule `link → added` | caller → callee |
| `initA`, `initR`, `answer`, `sanswer`, `sreqStmt` | local rule | the method → the method |
| `reqUp`, `sreqUp` | local rule on `[request, link]` in the callee; the result goes to the caller | callee → caller |
| `ret` | the caller makes the SUBSCRIPTION, the callee makes the PUBLICATION (after the restriction); their JOIN gives the caller edge | join |
| `sret` | a second join of the same subscription and publication (the overlap reading `Statics.SCtx.fbOK`). The final static rule `Statics.Design` has `fb = off`, so it gives nothing, and `matches` has no test for it | join |
| `zin` | local rule of the caller; the result goes to the callee | caller → callee |
| `zret` | the zero subscription of the caller and the zero-premise publication of the callee; their JOIN | join |
| `ND.DN.ndOpen`, `ndBind`, `ndRet` | a k-ary JOIN: one subscription per premise, all at one call statement, with the publication of the summary | join |

THE ENCODING WITH `[any-taint]` (`PipelineAnyTaintEx.lean`). An edge of `AnyTaintEx.D6X` and of `AnyTaintEx.DRX` has an
annotated conclusion (`AnyTaintEx.XFact`: a fact with its exclusion), so `PipelineAnyTaintEx.sysD6X` and `sysDRX`
have their own objects (`PipelineAnyTaintEx.XPObj6`, `XPObj`) and rules. In `sysD6X` a link carries the added fact
and the exclusion of the bound fact (the request climb reads it; the policy reads only the fact), a subscription
carries the bound fact with its exclusion (the application reads it), and a publication carries the exit fact with
its exclusion. In `sysDRX` the
objects carry the must flags and the exclusions (the must flag is the tail `[any-taint]` of a premise, §4.1). A link
carries the added fact with its flag (`[any-taint]`: an any tail, normal on the link) and its exclusion. A
subscription carries the caller premise with its flag and its exclusion, and the result of its join is an edge of
that premise. A publication carries the callee premise with its flag and its exclusion (two premise keys, §4.6). The
join reads the exclusions (`inside`, `AnyTaintEx.satX`), not the flag of the publication, and the layer of its result
is as in `DR`. The record rule with its demotion (`AnyTaintEx.recLayerX`, §4.2 `applyRecord`) is a local rule of the
caller: the records are read-only (A4). Both are forward runs, so they have no zero subscription and no zero
publication.

THE ZERO PUBLICATION (argued, §11). `PipelineAP.sysDB` has two publications of a zero-premise backward summary: the
restricted one (for `ret`) and the unrestricted `PipelineAP.PObj.zpub` (for `zret`). §4.6 publishes only the
unrestricted one. The two agree because no ordinary subscription satisfies the zero premise: no call binds the zero base
(`ap.md` S11 (c), Lean `Backward.NoZeroBack`). No Lean statement says this, and `Backward.NoZeroBack` is not a
hypothesis of `PipelineDriver.result_DB`. The publication of the code is a superset, so a mismatch could only add
results, never lose them.

So at quiescence the analyzer computes exactly the closure that `ap.md` proves sound and exact, in each mode
(`Pipeline.quiescent_exact` with the `cl*_iff` theorems; `PipelineDriver.result_D`, `result_DR`, `result_DB`). With
conjunctions the closure is `NDZ.DNz`, the ND closure with the zero-drop of `ap.md` §4.6 (`PipelineNDZ.result_DNz`).
With the tail `[any-taint]` the closure of run 1 is `AnyTaintEx.D6X` and the closure of a restricted forward run is
`AnyTaintEx.DRX` with the spec rules `emitX`, `satX` and the intersection `HandoffX.restrictIX`
(`PipelineAnyTaintEx.result_D6X`, `result_DRX`, generic in the rules; before F70 the instance `AnyTaintEx.DRXs` with
`restrictX`, `result_DRXs`); the backward run is `Backward.DB` as before, with the intersection `Handoff.restrictI`
(`PipelineAP.sysDB`, generic in the restriction).
This holds for the rules that the closures have. The end facts (`ap.md` §11.1), the aliases and their guard (`ap.md`
§11.2, S2), and the global-state rule with the entry-mark removal (`interpreter.md` G2, D35) are outside them (§11 THE
ANALYZER ACTIONS OUTSIDE THE CLOSURES).
THE F72 BASE CLOSURES ARE DEFINED (`Abs.DRA`, `Abs.DBA`); their pipeline encoding is pending (§11). `DR`, `DRX` and `Backward.DB` keep the request
rules; with the emission `emitM` (`emitX`) no request rule fires (`RExact.DR_no_request`, `BExact.DB_no_request`).
With the emission of F72 a restricted run has `*` facts, so the closures of F72 have no request rules (`DRA`, `DBA`,
§11). The protocol model (`Pipeline.lean`, a rule system with owners) does not depend on the rules, so only their
encoding (the `*_wf` and `cl*_iff` theorems) is new work.

THE MODEL AND THE CODE. Actor: a `RunMethodAnalyzer`. `known`: the RUN stores of the analyzers, the
`SubscriptionManager` tries and the `SummaryStorage` tries. `inbox`: the channels, the local queues, the worklists,
the `pendingPublications` and a direct call in progress. `store`: the `published` index of each `SummaryStorage`.
`Pipeline.St.replays`: the replay inside `subscribe`. `notifies`: a publication between the insert and the end of the
loop over `subscribers`. `deliv`: the `Delivery` events. `handlers`: the `subscribers` lists.

SUBSUMPTION (`Pipeline.quiescent_dominates`). Let `dom` be a preorder on the objects. Let the rules and the joins
SIMULATE it. That is: take a rule (or a join) and, for each premise, an object that dominates it. Then the same rule (or
join) on these objects gives a conclusion that dominates the first conclusion. Let the step `StepD` also drop an
in-flight object that a processed object of the same owner dominates. Then, for a well-formed system, at a reachable
quiescent state, a processed object dominates every object of `Cl`. Soundness stays (`reach_soundD`). That the AP
operations simulate the subsumption of the edge stores is argued (§11). The simulation fails for `applicable` on the
subscriptions, so the subscriptions and the links deduplicate exactly (§5.3).

---


### 7.7 The driver theorems

THE THEOREMS OF THIS SECTION ARE FOR THE CONCRETE RESTRICTED RUNS (`ap-history.md` F70, F71). Their restricted runs
are `DR … emitM satI restrictI` (`AnyTaintEx.DRX … emitX satX restrictIX`) and `Backward.DB … emitM satI restrictI`:
the emission copies the mark of the added fact, so every restricted run is concrete (`RExact.DR_concrete`,
`BExact.DB_concrete`), and the closures keep request rules that never fire (`RExact.DR_no_request`,
`BExact.DB_no_request`). THE RULES OF F72 (§3) change the emission (`emitW`), the satisfaction (`satW`), the two
hand-offs (the normalization R1: `handFA`, `demOfNA`) and the closures (no request rules: `DRA`, `DBA`). FOR THEM THE
THEOREMS BELOW ARE NOT YET PROVED: the task is §11 PENDING: THE LEAN MODEL OF F72 (the modes, the lemmas L1 to L6, the
iteration). The names below stay: they hold for the concrete design. They rest on the mark-copying emission (the
hypothesis `EmitCopiesMark`), which is false for `emitW` (a `*` pattern gives the mark `*`), so these claims DO NOT
HOLD FOR F72: `RExact.DR_concrete` and
`BExact.DB_concrete` (a restricted run has `*` facts), `RExact.DR_no_request` and `BExact.DB_no_request` (with `emitW`
the request rules of `DR` and `DB` can fire; the F72 closures have none). The proofs that use them have to be done
again with the modes (L2, L4).

The driver of §7.1 with the hand-off of §7.3 and §7.4 (`ap-history.md` F70; `ap.md` §10.12). The theorems join three
parts:

LEAN NUMBERING. The Lean theorems count the forward runs only: the forward run `k` of Lean is the run `2k + 1` of
§7.1 (run 1 is `k = 0`), and the backward run after it is the run `2k + 2`.

* THE FORWARD CONTRACT of a restricted forward run (`Handoff.CoversN`). Every witness whose calls that return are
  DEMANDED (one demand pattern has a `D-c` that covers the entry location WITH ITS MARK and a `D-p` that covers the
  exit location WITH ITS MARK; Lean `Handoff.FlowRR.call`, `p.covers l2`) or RECORDED (a crossable record of the run
  has the pair) is JUSTIFIED by the run (by a publication or a crossable record), and the run reports its
  vulnerability. Proved for `DR … emitM satI restrictI` (`Handoff.coversN_DR`, by `coverageRN`: a demanded call uses
  the emission inside `D-c` with its mark, `emitM_insideB`, and the contract of the intersection,
  `restrictI_contract`: a premise inside `D-c` in its locations and its marks, and an exit location that `D-p` covers
  with its mark; a recorded call uses `cross_applies`). Before F71 the demanded witness read the exit location without
  its mark (`p.coversLoc l2`); with the mark-aware restriction that form of the contract is false
  (`Handoff.restrictI_contract_loc_false`). With the tail `[any-taint]`: `DRX … emitX satX restrictIX`, read with the
  exclusions dropped, with the base records embedded (`HandoffX.coversN_DRXI`, `RecsEmbed`; its hypotheses
  `HandoffX.EmitInsideX` and `RestrictInsideX` read the marks too, by `insideXB` and `p.covers l2`). Run 1 justifies
  every real witness and reports it (`Handoff.run1_justifies`; with `[any-taint]` `HandoffX.run0X_contract`).
* THE BACKWARD CONTRACT (`Handoff.BackwardContractN`). Every witness that forward run `k` justifies, of a SEEDED sink,
  is demanded or recorded in the next forward run. Proved for `Backward.DB` of `Reverse.Program.rev P` with `emitM`,
  `satI`, `restrictI`, the demand `handF`, the backward records `recsBOf` (the reversals of the crossable records)
  and the next forward records `rcNextOf` (`HandoffBackward.B_generalN`, `B_generalN_canon`; a crossable call:
  `HandoffBackward.cross_step`). The backward run gives the demanded witness WITH THE MARKS: its emitted premise lies
  inside `D-c` with its mark (in the concrete design the requirement is concrete, `HandoffBackward.emitM_insideB_B`;
since F72 a FLOW requirement lies inside its `*` pattern, R5, PENDING), and `D-p = j` covers
  the exit location of the backward pair with its mark (`j` covers it by the forward pair), so the mark-aware
  restriction keeps the pair (`restrictI_contract_B`); its premise `jb` covers the exit location of the next forward
  run with its mark (`HandoffBackward.seg_genN`).
* THE ITERATION (`HandoffIter.iteration_abstract_or`): run 1 and the two contracts, with "every vulnerability that a
  forward run reports is confirmed or seeded", give the conclusion below.

THEOREM (`HandoffMain.iteration_generalN`). The canonical run sequence of the spec rules (`HandoffMain.canonState`):
run 1 is `D … policy1` with the publication `pubD` and no record; the backward run after forward run `k` is
`Backward.DB` of `Reverse.Program.rev P` with `emitM`, `satI`, `restrictI`, the demand `handF` of run `k`, the records
`recsBOf`, no sinks, the zero rules (`zbind = true`) and the seeds `seeds k`; the next forward run is
`DR … emitM satI restrictI` with the demand `demOfN` of that backward run, the publication `pubR` and the records
`rcNextOf`. The field limits are free. Hypotheses:

* the program satisfies the hypotheses of `Backward.iteration_general` (`ap.md` §6.6): `P.WF`, `Reverse.BindTargetsStar`,
  `Backward.StmtsMarkRev`, `NoZeroBack`, `ZeroKept`, `ExitReach`, and every sink pattern has the tail `$` or `[any]`;
* THE SEEDS (`hseeds`): every vulnerability that forward run `k` reports satisfies a predicate `C k` or is in
  `seeds k`.

Conclusion: for every real flow to a sink (a reachable location that a sink pattern with a concrete mark covers), at
every forward run `k`, an earlier forward run satisfies `C` for it, or run `k` reports it, in some layer.

The seeds of §7.3 satisfy `hseeds` with `C k` = "a complete forward run up to `k` confirmed the vulnerability key"
(cumulative): a vulnerability that run `k` reports is CONFIRMED in the report after run `k`, or it is a DEMAND
vulnerability, and its witnesses are seeds. So after every complete forward run the report holds every real
vulnerability (§7.5 THE OUTPUT), and `STOP_RULE` keeps every real vulnerability CONFIRMED (§7.1). For the driver of
§7.1 this is the pipeline form with the DEMAND seeds (below).

* `HandoffMain.iteration_generalN_all`: `C` false, every reported vulnerability seeded: every forward run reports
  every real vulnerability.
* `HandoffMain.iteration_generalN_incl`, THE INCLUSION FORM: the driver may hand off MORE than the canonical sets.
  The backward demand contains `handF` of run `k` (`hdemB`), the backward records contain the reversed crossable
  records (`hrecB`), the forward demand contains `demOfN` of the backward run (`hdem`), and the records of the next
  forward run contain `rcNextOf` (`HandoffBackward.NextRecs`, `hrc`). So an implementation may also hand off a
  crossable leaf or keep more records (`HandoffMain.flowRR_mono`, `reachRR_mono`, `flowRDN_mono_rc`,
  `reachRDN_mono_rc`).

THEOREM, WITH THE TAIL `[any-taint]` (`HandoffXIter.iteration_generalNX`; `ap.md` §10.11). The same on the canonical
sequence of the spec closures (`HandoffXIter.canonStateX`): run 1 is `AnyTaintEx.D6X … policy1`, read by
`AnyTaintExCov.forget6`; the next forward run is `AnyTaintEx.DRX … emitX satX restrictIX` with the base records
embedded (`HandoffXIter.embedRecs`), read by `forgetX`, with the publication `HandoffX.pubRX`; the backward runs are
as above (the backward run has no `[any-taint]`). Also `HandoffXIter.iteration_generalNX_all`,
`HandoffXIter.iteration_reportsNX_canon`, and the inclusion forms `HandoffXIter.iteration_generalNX_incl` (with `C`)
and `HandoffXIter.iteration_reportsNX` (every vulnerability seeded); their X records of the next forward run contain
the embedded crossable base records (`hrecX`), and may contain more. The hypotheses are those of
`AnyTaintExCov.iteration_reportsX`. PROVED.

THEOREM, THE PIPELINE FORM (`PipelineHandoffDriver.driver_iterationNX`). Hypotheses: those of
`HandoffXIter.iteration_reportsNX`; every run is complete: `st1` (run 1, `PipelineAnyTaintEx.sysD6X`), `stR k` (the
restricted forward run `2k + 3` of §7.1, `sysDRX … emitX satX restrictIX`) and `stB k` (the backward run `2k + 2`,
`PipelineAP.sysDB … emitM satI restrictI`) are reachable quiescent states; the driver computes the hand-offs from the
final states (`pubSeqXst`): the backward demand contains `handF` (`hdemB`), the backward records the reversed
crossable records (`hrecB`), the base records `NextRecs` (`hrcN`), the X records embed them (`hrecX`), the forward
demand contains `demOfN` (`hdem`), and the seeds contain EVERY vulnerability of the forward run (`hseeds`).
Conclusion: every forward run holds every real vulnerability, in some layer. The proof joins
`PipelineAnyTaintEx.result_D6X`, `result_DRX` and `PipelineDriver.result_DB` (both generic in the rules) with
`HandoffXIter.iteration_reportsNX` (through `PipelineHandoffDriver.resultSeqX_runSeqNX`, `pubSeqXst_pubSeqNX`). This
is the form with ALL the seeds.

THEOREM, THE PIPELINE FORM WITH THE DEMAND SEEDS (`PipelineHandoffDriverExt.driver_iterationNX_demand`). The
hypotheses of `PipelineHandoffDriver.driver_iterationNX`, with `hseeds` in the form "every vulnerability that forward
run `k` reports satisfies `C k` or is seeded". With `C k` = "a complete forward run up to `k` confirmed the key"
(CONFIRMED in the sense of `ap.md` §4.9; the seeds of §7.3 satisfy `hseeds`), the conclusion is: at every forward run
`k`, every real vulnerability is reported by run `k`, in some layer, or confirmed by an earlier forward run. On the
final states: `PipelineHandoffDriverExt.driver_iterationNX_demand_known`. PROVED. The instance
`PipelineHandoffDriverExt.driver_iterationNX_confirmed` fixes a weaker `C` (a run up to `k` reported the key in the
NORMAL layer, with no test of the support): it is not the confirmation of §7.5, so it is not the form of this driver.

THE DRIVER OF §7.1 AGAINST THESE THEOREMS:

* THE SEEDS OF THE DEMAND VULNERABILITIES. The AP forms with `C` (`HandoffMain.iteration_generalN`,
  `HandoffXIter.iteration_generalNX` and their inclusion forms) and the pipeline form
  (`PipelineHandoffDriverExt.driver_iterationNX_demand`) are proved.
* A FINITE SEQUENCE. The driver stops after a complete forward run (a stop rule or `continueAfter`), or at an abnormal
  end, which adds nothing to the report (§7.5). The finite forms stop the induction at the last complete forward run
  `K`: they need the hypotheses only for the runs before `K`, and they read no run after `K`
  (`HandoffUpto.iteration_generalN_upto`, `iteration_generalN_canon_upto`, `iteration_generalNX_upto`,
  `iteration_generalNX_canon_upto`; the driver `PipelineHandoffDriverExt.driver_iterationNX_upto`). PROVED.
* THE STOP RULES. The report part of `STOP_RULE` follows from the iteration theorem (§7.1); that every later forward
  run only repeats the zero fact and the records is argued (§11). `NO_DEMAND_EDGE` is argued (§11).
* THE SOURCE SEEDS (§7.4): PROVED, with the forward runs on the seeded program `FSeeds.keepSources P σ`
  (`HandoffSrc.B_srcN`, `iteration_srcN`, `iteration_srcN_canon`, `iteration_srcNX`; the driver
  `PipelineHandoffDriverExt.driver_iteration_srcNX`; finite `HandoffSrc.iteration_srcN_upto`, `iteration_srcNX_upto`,
  `PipelineHandoffDriverExt.driver_iteration_srcNX_upto`). The sources at a call, at the method start and at the method
  exit, and the exactness of a seeded run, are argued (§11 THE SOURCE SEEDS).
* THE STATIC RULE AND THE CONJUNCTIONS with the new hand-off: argued (§11). The forward runs of the theorems above
  have no ND edge, and the backward run of the model has no reversed conjunction (§4.3 THE REVERSAL OF A CONJUNCTION).
* THE END FACTS and THE TRIGGER OF AN END FACT (§4.5): outside the model (§11).
* THE EXCLUSION AND THE NARROWING: §7.8.

THE EARLIER HAND-OFF (before F70). `PipelineDriver.driver_iteration`, `driver_iteration_upto`,
`PipelineSeeds.driver_iteration_src`, `PipelineAnyTaintExDriver.driver_iterationX`, `driver_iteration_uptoX` and
`driver_iteration_srcX` state that every forward run holds every real vulnerability for the hand-off of EVERY summary
edge, in every layer, BEFORE the restriction (`Backward.revSummaryDemand`, `Backward.demOf`), with the restriction
`restrictU` (`restrictX`) and with every reported vulnerability seeded. Their hypotheses say that the hand-offs
CONTAIN those sets. The hand-off of §7.3 and §7.4 does not contain them (it leaves out the crossable leaves and reads
the intersections), so the driver of §7.1 is not an instance of these theorems. They stay true for the earlier
design, with the source seeds (`FSeeds.keepSources`, `FSeeds.srcHit`, `FSeeds.iteration_src`; with the tail
`[any-taint]` `AnyTaintExCov.iteration_srcX`) and with a finite sequence; the new hand-off has its own forms of both
(`HandoffSrc`, `HandoffUpto`, `PipelineHandoffDriverExt`, above). For
the runs with the static rule, `ap.md` proves the iteration of the earlier hand-off
(`StaticsIter.iteration_general_DS`), and `PipelineAP.clDS_iff` gives the closure equality (§11). With the tail
`[any-taint]` the driver reads each forward run with its exclusions and must flags dropped (`AnyTaintExCov.forget6`,
`forgetX`; `PipelineAnyTaintExDriver.resultSeqX`). That hand-off can be smaller than that of the same run without the
exclusion (`AnyTaintExCov.CexRoute.route_a_false`), so every proof reads each refined run directly
(`AnyTaintExCov.iteration_reportsX`; with the new hand-off `HandoffXIter.iteration_generalNX`).


## 12. The formal model

The files of this table model the CONCRETE restricted runs (`ap-history.md` F70, F71). The checked F72 base modules,
their assumptions and the remaining obligations are listed in `ap.md` §10.13 and §11.2. The F72 pipeline instance
is pending (§11). The existing concrete-design theorems keep their scope.

| File | Content |
|---|---|
| `Pipeline.lean` | the rule system with owners, its closure, the state and the steps of the protocol (frozen definitions) |
| `PipelineProofs.lean` | `reach_sound`, `quiescent_complete`, `quiescent_exact`, `no_lost_join`; the counterexamples `PCex.cex_P1` to `cex_P4` and `PCex.step_finds_edge`; the counter model (`Quiesce.creach_inv`, `cnt_zero_iff`, `done_iff`, `done_final`, `bad_early_done`); the dominance theorems (`quiescent_dominates`, `reach_soundD`) |
| `PipelineAP.lean` | the encodings of `D`, `DR`, `DB`, `DS`, `DN`; the `*_wf` theorems; `clD_iff`, `clDR_iff`, `clDB_iff`, `clDS_iff`, `clDN_iff`; the object theorems (`clD_link`, `clD_sub`, `clD_pub` and the other forms) |
| `PipelineStore.lean` | the completeness of the index lookups of §5.3 (`replay_run1`, `deliver_run1`, `replay_restricted`, `deliver_restricted`, `record_lookup`) |
| `PipelineDriver.lean` | `result_D`, `result_DR`, `result_DB` (generic in the rules: also the backward run with `restrictI`), `driver_iteration`, `driver_iteration_upto` (the earlier hand-off, §7.7) |
| `PipelineNDZ.lean` (with `NDZ.lean`, `NDZeroBase.lean`) | `PipelineNDZ.sysDNz_wf`, `clDNz_iff`, `result_DNz`, `clDNz_ndpub_zero_sub`: the encoding of `NDZ.DNz` (§5.4, §5.5) |
| `ForwardSeeds.lean`, `PipelineSeeds.lean` | the source seeds: `FSeeds.keepSources`, `srcHit`, `srcHit_applies`, `B_src`, `iteration_src`; `PipelineSeeds.driver_iteration_src` (`ap.md` §10.9) |
| `PipelineAnyTaintEx.lean` (with `AnyTaintExDefs.lean`) | the encodings of the closures of the tail `[any-taint]` with its exclusion (§5.5): `sysD6X`, `sysDRX`, `XPObj6`, `XPObj`, `sysD6X_wf`, `sysDRX_wf`, `clD6X_iff`, `clDRX_iff`, the object theorems, `known_D6X`, `known_DRX`, `result_D6X`, `result_DRX`, `result_DRXs`, `no_lost_summary_D6X`, `no_lost_summary_DRX`, `SanityX` |
| `PipelineAnyTaintExDriver.lean` (with `AnyTaintExCov.lean`) | the driver with the tail `[any-taint]` and its exclusion, for the earlier hand-off (§7.7): `resultSeqX`, `resultSeqX_runSeqX`, `driver_iterationX`, `driver_iteration_uptoX`, `driver_iteration_srcX` |
| `HandoffDefs.lean` | the hand-off of the demand edges only (`ap-history.md` F70; `ap.md` §10.12), the definitions (namespace `Handoff`): the intersection `restrictI` in the locations and the marks (`ap-history.md` F71: the premise test `insideB`, that is `insideLocB` and `markSubB`; the conclusion mark test `concMarkB`; `meetConcK`, `restrictConcI`), the demanded call `FlowRR.call` with `p.covers l2` (the exit location with its mark), the crossable test `CrossK`, `Cross`, the backward test `CrossB` and the reversed record `revRec` (§1), the publications `Pub`, `pubD`, `pubR` (§4.6), the two hand-offs `handF` (§7.3) and `demOfN` (§7.4), the witnesses `FlowRR`, `ReachRR` (demanded or recorded) and `FlowRDN`, `ReachRDN` (justified), the contracts `CoversN`, `BackwardContractN` (§7.7) |
| `HandoffRestrict.lean`, `HandoffCoverage.lean` | the intersection (namespace `Handoff`): `restrictI_sub`, `restrictI_contract` (a premise inside `D-c` by `insideB`, an exit location that `D-p` covers with its mark), `restrictI_contract_loc_false` (its form in the locations only is false: the user's example), `restrictI_of`, `restrictI_someM`, `restrictI_not_RestrictContract` (the old contract form, a premise that only overlaps `D-c`, is false), `emitM_inside`, `emitM_insideB` (a concrete added fact: the premise lies inside `D-c` with its mark), `insideB_covers`, `insideLoc_coversLoc`, `restrictI_inter` with its exceptions `RExc`, with the marks `restrictI_interM` (the exception of an abstract conclusion mark, `Invariant.AbsMark`) and `restrictI_inter_conc`, `restrictI_narrow`, `restrictI_narrowM`, `restrictI_narrow_conc`, `pubD_sub`, `pubR_sub`; the narrowing of one hand-off `handF_narrow`, `handF_narrow_loc`, `demOfN_narrow`, `handF_narrow_DR`, `handF_narrow_DR_exact`, with the marks `handF_narrowM`, `handF_narrow_locM`, `demOfN_narrowM`, `handF_narrow_DRM`, `handF_narrow_DR_exactM`, and `handF_DR_nonstar`, `DR_exit_not_star`; the vectors `RVec.v64_restrictI`, `v64_restrictU`, `vOverlap_restrictI`, `vOverlap_restrictU`, `vNoExit`, every row `RVec.row_*` of `restrictConcI`, the mark tests `RVec.vMark_user_inside`, `vMark_user_concMark`, `vMark_user_restrictI`, `vMark_user_restrictU`, `vMark_user_loc`, `vMark_user_same`, `vMark_prem_loc`, `vMark_prem_inside`, `vMark_prem_restrictI`, `vMark_inStarEx_T`, `vMark_inStarEx_U`, `vMark_outStarEx_T`, `vMark_outStarEx_U`, `inter_exc_absmark`, and the emission of `*∖X` `RVec.vEmit_starEx_T`, `vEmit_starEx_T_pre70`, `vEmit_starEx_U`; the forward contract `cross_applies`, `coverageRN`, `reach_strongRN`, `coversN_DR`, run 1 `run1_justifies` (§7.7) |
| `HandoffBackward.lean`, `HandoffIter.lean` | the backward contract (namespace `HandoffBackward`): `cross_step`, `seg_genN` (with the local copies of the mark-aware forms `emitM_insideB_B`, `concMarkB_of_den_B`, `restrictI_contract_B`), `reach_of_db_genN`, `demanded_genN`, `B_generalN`, `B_generalN_canon`, `NextRecs`, `recsBOf`, `rcNextOf`, `rcNextOf_back_normal` (a record from the backward run is the reversal of a normal backward leaf), `crossB_em`; the iteration (namespace `HandoffIter`): `Run0Contract`, `iteration_invariantN`, `iteration_abstract_or`, `iteration_abstract`, `iteration_abstract_neg`, `iteration_all_seeded` (§7.7) |
| `HandoffExclusion.lean` | THE EXCLUSION (namespace `HandoffExclusion`, §7.8): `NoZeroGenP`, `Reaches`, `zinv_all`, `exclusion_backward`, `init_zero_nodem`, `exclusion_backward_nodem`, `exclusion_demand`, `forward_zero_init`, `forward_zero_edges`, `exclusion_theorem` (the hypothesis: no demand edge of the method key), `exclusion_round` (the sufficient condition: every summary leaf crossable) |
| `HandoffMain.lean` | the canonical sequence of the spec rules (namespace `HandoffMain`): `canonState`; THE ITERATION `iteration_generalN`, `iteration_generalN_all`, `iteration_generalN_incl` (with `flowRR_mono`, `reachRR_mono`, `flowRDN_mono_rc`, `reachRDN_mono_rc`); the exclusion `exclusion_canon`; the narrowing with both cells of `Handoff.RExc` as exceptions `narrowing_canon_fwd`, `narrowing_canon_back`, `narrowing_canon`, `narrowing_canon_loc`, and its forms with the marks `narrowing_canon_fwdM`, `narrowing_canon_backM`, `narrowing_canonM`, `narrowing_canon_locM` (in the two cells the marks still narrow; the exact form is in `HandoffNoStar.lean`) (§7.7, §7.8) |
| `HandoffCases.lean` | the worked programs (namespace `HandoffCases`, §7.8, §13): `restrictI_none`; WRAP (`Wrap.w1_exit_cross`, `w1_wrap_exits_cross`, `seedsW_exact`, `old_b2_wrap_init`, `old_dem_w`, `old_f3_wrap_init`, `old_f3_wrap_cut`, `handF_w1_exact`, `bn_cross`, `bn_wrap_zero_only`, `bn_wrap_edges_zero`, `demN_exact`, `fn_wrap_zero_only`, `fn_wrap_edges_zero`, `fn_record_applicable`, `fn_cut_in_root`, `fn_found`, `wrap_reachRR`, `wrap_old_vs_new`); the CEGAR of `Cross` (`revRec_any_premise`, `not_cross_of_any`, `dollar_blocked`, `CrossL`, `handFL`, `AnyW.cegar_cross_anyw`, `AnyM.cegar_cross_anym`); the getter (`Getter.g1_exit_demand`, `revRec_g_cross`, `revRec_g_crossB`, `demG_exact`, `fg_getter_zero_only`, `fg_record_sat`, `fg_found`) |
| `HandoffRCases.lean` | programs 1 and 2 of `ap.md` §6.3, §6.4 with the intersection `restrictI` and the hand-off of the demand edges (namespace `HandoffRCases`, §13 item 7): `p1_no_exit`, `p1_found_I`, `p1_handoff`, `p1_chain`; `r1_c_not_cross`, `b2_handF`, `b2_inside`, `b2_insideB`, `b2_concMark`, `b2_restrictI_eq_U`, `b2_not_crossB`, `p2_handoff`, `f3_inside`, `f3_insideB`, `f3_concMark`, `f3_restrictI_eq_U`, `p2_found_I`, `p2_chain` (the mark tests pass, so the results are those before F71) |
| `HandoffUpto.lean` | THE FINITE FORMS (namespace `HandoffUpto`, §7.7): the induction stops at the last run `K`, with the hypotheses only for the runs before `K`: `iteration_generalN_upto`, `iteration_generalN_canon_upto`, `iteration_generalNX_upto`, `iteration_generalNX_canon_upto` |
| `HandoffSrc.lean` | THE SOURCE SEEDS with the hand-off of the demand edges (namespace `HandoffSrc`, §7.4, §7.7): contract B into the seeded program `B_srcN` (the recorded calls read no seed); the iteration `iteration_srcN`, `iteration_srcN_upto`, `iteration_srcN_canon`, with the tail `[any-taint]` `iteration_srcNX`, `iteration_srcNX_upto`; the program `SrcRec` (`SrcRec.found_unseeded`: a record applies a source that the seeds drop; the source seeds do not filter the records) |
| `HandoffXMain.lean` | the exclusion and the narrowing over a round on the spec closures with the tail `[any-taint]` (namespace `HandoffXMain`, §7.8): `forward_zero_initX`, `forward_zero_edgesX`, `exclusion_roundX`, `exclusion_canonX`; `narrowing_canonX_fwd`, `narrowing_canonX_back`, `narrowing_canonX`, `narrowing_canonX_loc` (with the `Dropped` alternative: the hand-off drops the exclusions); with the marks `narrowing_canonX_fwdM`, `narrowing_canonX_backM`, `narrowing_canonXM`; the vectors `XMVec.exit_dropped`, `entry_dropped` |
| `HandoffNoStar.lean` | NO `*` DEMAND PATTERN, AND THE EXACT NARROWING, for the concrete design (F72 has `*` patterns after run 1, so the claims about the sequence and the backward run do not hold for F72; the run-1 results and the general lemmas stay, §7.8, §11) (namespace `HandoffNoStar`, §7.8): run 1 `run1_exit_star_cross`, `handF_run1_nonstar`; the backward run `DB_edge_nonstar`, `demOfN_nonstar`; the sequence `canon_dem_nonstar`, `canon_handF_nonstar`; cell (a) at `*/{}` `rexc_empty_loc`; THE NARROWING `narrowing_canon_fwd_exact`, `narrowing_canon_back_exact`, `narrowing_canon_back_loc`, `narrowing_canon_loc_exact`, and in the locations AND the marks `narrowing_canon_fwd_exactM`, `narrowing_canon_back_exactM`, `narrowing_canon_loc_exactM`; with the tail `[any-taint]` `handF_run1X_nonstar`, `canonX_dem_nonstar`, `narrowing_canonX_fwd_exact`, `narrowing_canonX_loc_exact`; the seeds `nonstar_of_sinkK` |
| `PipelineHandoffDriverExt.lean` | the pipeline forms of the driver of §7.1 (namespace `PipelineHandoffDriverExt`, §7.7): the DEMAND seeds `driver_iterationNX_demand`, `driver_iterationNX_demand_known`; the instance with a weaker `C` (a report in the normal layer) `driver_iterationNX_confirmed`; the finite form `driver_iterationNX_upto`; the source seeds `driver_iteration_srcNX`, `driver_iteration_srcNX_upto` |
| `HandoffXRestrict.lean`, `HandoffXCoverage.lean`, `HandoffXIter.lean`, `PipelineHandoffDriver.lean` | the hand-off of the demand edges on the spec closures with the tail `[any-taint]` (§4.6, §7.7, §7.8): the intersection with the exclusion (namespace `HandoffX`) `restrictIX` (with the mark tests of `restrictI`), `insideLocXB`, `insideXB` (`insideLocXB` and `markSubB`), `restrictIX_ok`, `restrictIX_contract`, `restrictIX_contract_base`, `restrictIX_of`, `emitX_inside`, `emitX_insideXB`, `restrictIX_inter` with `RExcX`, `restrictIX_interM`, `restrictIX_inter_conc`, `restrictIX_narrowM`, `pubRX`, `handF_narrowX`, `handF_narrowX_DRX`, `handF_narrowXM`, `handF_narrowX_DRXM`, the vectors `XVec.v64_demand`, `v64_taint`, `v_taint_star`, `v_at_rows`, `v_above_rows`, `v_below_rows`, `v_overlap`, `v_inside_only_with_excl`, `v_old_above_not_inter`, the mark tests `XVec.vM_user`, `vM_prem`, `vM_inStarEx`, `vM_outStarEx`, `vM_emit`, `XVec.restrictIX_contract_loc_false`; the forward contract `coversN_DRXI` (`RecsEmbed`; the hypotheses `EmitInsideX`, `RestrictInsideX` read the marks) and run 1 `run0X_contract`; the iteration (namespace `HandoffXIter`) `canonStateX`, `embedRecs`, `iteration_generalNX`, `iteration_generalNX_all`, `iteration_reportsNX_canon`, `iteration_generalNX_incl`, `iteration_reportsNX`; the pipeline form `PipelineHandoffDriver.driver_iterationNX` (`resultSeqX_runSeqNX`, `pubSeqXst_pubSeqNX`) |
| `AnyTaintExDefs.lean`, `AnyTaintExCov.lean`, `AnyTaintExExact.lean`, `AnyTaintExKinds.lean`, `AnyTaintExCases.lean`, `AnyTaintExCases2.lean` | the AP model of the tail `[any-taint]` with its exclusion: THE SPEC CLOSURES (`ap.md` §10.11). This document cites: the closures `AnyTaintEx.D6X`, `DRX`, `DRXs`, the objects `XObj`, `XFact`, the operations `w6tX`, `transferX`, `limitFX`, `cleanResX`, `partX`, `annX`, `checkX`, `emitX`, `emitTX`, `satX`, `restrictX`, `startX`, `recLayerX` (with `DRX.retRec`), the predicates `SatInsideX` (`satX_inside`), `EndExactX`, `RecsExactX`, `SupLinkX`, `SupX`, `Confirmed6X`, `ConfirmedX`, the vectors `Vec`; `AnyTaintExCov.forget6`, `forgetX`, `iteration_reportsX`, `iteration_srcX`, `CexRoute.route_a_false`; `AnyTaintExExact.startX_must_end`, `confirmed_real_valid6X`, `confirmed_realX_valid`, `seq_confirmed_realX_valid`, `RecsFromRunsX`, `specX_rules`, `RecsConcX`, `RecsWFX`, `recs_of_DRX`, `CexExactCleaner.cex_exact_cleaner`; `AnyTaintExKinds.D6X_flow_no_any_taint` (run 1, under S7, S10 and S15: an edge whose premise has the mark `*`, a FLOW edge, has no normal `[any-taint]` conclusion), `D6X_any_conc`, `DRX_normal_premise`, `DRX_must_premise` (under `AnyTaintEx.EmitCopiesMarkX`, `AnyTaintEx.emitX_copies`: a normal edge of a restricted run has a `$` premise or a must-premise `[any-taint]` with a concrete mark; before F70 `DRXs_normal_premise`, `DRXs_must_premise`); the worked programs of `AnyTaintExCases` (§13) and of `AnyTaintExCases2` (the round-1 programs G, C, I and PassRule re-derived in `D6X`, and their restricted runs in `DRXs` with the earlier restriction `restrictX` and the earlier hand-off, the record of the earlier design; §7.5, §13) |
| `AnyTaintDefs.lean`, `AnyTaintSim.lean`, `AnyTaintExact.lean`, `AnyTaintND.lean`, `AnyTaintCases.lean`, `PipelineAnyTaint.lean`, `PipelineAnyTaintDriver.lean` | the model of the first F69 form (the demotion at an exclusion, `AnyTaint.D6T`, `DRT`), not the spec closures. This document cites from it only what still states a rule of the spec: the taint edges `AnyTaint.TaintEdges`; the counterexamples `AnyTaintExact.CexApp.cex_app`, `CexApp.record_not_pair_exact`, `CexRev.cex_rev`, `CexSupMark.cex_sup_mark` and the lemma `markSub_conc`; the conjunction `AnyTaintND.DNzT`, `Example`; the transfer with no exclusion `AnyTaintCases.Cut.cut_transfer` (§13 item 31); the vectors `AnyTaint.EmitVec`, `Sanity`. The round-1 programs G, C, I and PassRule of `AnyTaintCases.lean` are only the program terms that `AnyTaintExCases2.lean` imports: their results in the spec closures are those of `AnyTaintExCases2` |

THE TAIL `[any-taint]` AGAINST THE MODEL. The base model has three tail kinds (`.star e`, `.any`, `.exact`). The spec
has four, and the forward `[any-taint]` can carry an exclusion (`ap.md` W8). The spec closures are the refined ones,
`AnyTaintEx.D6X` and `DRX` with `emitX`, `satX` and `HandoffX.restrictIX` (before F70 `DRXs`, with `restrictX`) (an
annotated fact `AnyTaintEx.XFact` is a base fact with its exclusion):

| Spec | Model |
|---|---|
| `*/E` | `.star e` |
| `$` | `.exact` |
| `[any]` (a may) | a `.any` fact in the demand layer, with no exclusion (`AnyTaintEx.normX`) |
| `[any-taint]/E` conclusion (a must) | a `.any` fact in the normal layer with a concrete mark and the exclusion `E` (`AnyTaintEx.XFact`, `carriesB`; in run 1 every normal `.any` conclusion has a concrete mark, `AnyTaintExKinds.D6X_any_conc`) |
| `[any-taint]/E` premise (a must-premise) | a premise with the must flag and the exclusion of `AnyTaintEx.XObj` (`init M j true jex`, `edge M j true jex n f`); it has the tail `.any` and a concrete mark (`AnyTaintExKinds.DRX_must_premise`) |
| `[any-taint]/E` added fact | `added M a true aex`: `.any`, normal on its link, with the exclusion `aex` |
| the sources with an `[any]` target (`ap.md` S15) | the taint edges `AnyTaint.TaintEdges` (`AnyTaintEx.w6tX` reads them) |
| the backward run (no `[any-taint]`) | `Backward.DB`: no must flag, no exclusion; the driver reads each forward run with them dropped (`AnyTaintExCov.forget6`, `forgetX`) |

In the model `AFact.complete` is false on every `.any` fact: it is the backward reading of COMPLETE only. With the tail
`[any-taint]` the forward records and the confirmation read only the layer: the normal exit edges of a run, `.any`
included, are exact records, or END-EXACT records for a must-premise, on the locations that their exclusions admit
(`AnyTaintEx.RecsExactX`; `AnyTaintExExact.recs_of_DRX`), and a confirmed vulnerability has a normal sink edge
(`AnyTaintEx.Confirmed6X`, `ConfirmedX`). The backward records keep the base reading.

---


## Historical frontier arguments

### 7.8 Localization and the frontier

The hand-off of the demand edges only (§7.3, §7.4) LOCALIZES the remaining work: a run analyses from a non-zero fact
only the method keys that a demand edge of the run before reaches (§4.4). Two theorems tell how this part changes from
run to run, and the frontier log measures it (`task.md`; `ap-history.md` F70). As in §7.7, THE TWO THEOREMS ARE FOR
THE CONCRETE RESTRICTED RUNS; for the rules of F72 (§3) they are NOT YET PROVED (§11 PENDING: THE LEAN MODEL OF F72).
The narrowing with no exception uses facts that F72 makes false (WHY THERE IS NO EXCEPTION, below).

THE EXCLUSION THEOREM (`HandoffExclusion.exclusion_theorem`; from the leaves `HandoffExclusion.exclusion_round`; on
the canonical sequence `HandoffMain.exclusion_canon`; on the spec closures with the tail `[any-taint]`, forward runs
`AnyTaintEx.DRX … emitX`, `HandoffXMain.exclusion_roundX` and, on `HandoffXIter.canonStateX`,
`HandoffXMain.exclusion_canonX`). Lean numbering (§7.7): `k` counts the forward runs only, so forward run `k + 1` of
Lean is the forward run after forward run `k`. Let a method key `M` satisfy, after forward run `k`:

* forward run `k` hands off NO DEMAND EDGE of `M` (the frontier field `demandEdges[M]` is 0; Lean `hdem : ∀ d, ¬ demB
  M d` with `demB` = the hand-off `handF` of run `k`). A SUFFICIENT CONDITION: every summary leaf of `M` in run `k`
  is crossable (§1), every exit edge of every initial fact, the zero fact too (`HandoffExclusion.exclusion_round`,
  `HandoffMain.exclusion_canon`);
* no seed of the backward run after run `k` lies in the CALL SUBTREE of `M`: `M` and every method that `M` reaches
  through calls (Lean `HandoffExclusion.Reaches`). A seed here is a seed of the hand-off or a seed that the backward
  run fires by THE TRIGGER OF AN END FACT (§4.5): both give seed paths, which return to every caller (rule `zret`).

Then in the backward run after run `k` every edge of `M` is the zero edge, and in forward run `k + 1` the only
initial fact of `M` is the zero fact, and every edge of `M` has the zero premise. The program hypotheses:
`HandoffExclusion.NoZeroGenP` (the conditions (a) to (c) of `NDZeroBase.NoZeroGen`: the only statement micro edge
into the zero base is the zero keep edge, the only binding into the zero base of a callee is the zero binding, and no
binding back goes to the zero base) and no cleaner on the zero base; the seeds have concrete marks
(`BExact.SeedsConc`). The steps: with no demand edge of `M`, the backward run emits no non-zero initial fact in `M`
(`HandoffExclusion.init_zero_nodem`); with no seed below `M`, every backward edge of `M` is the zero edge
(`HandoffExclusion.zinv_all`, `exclusion_backward_nodem`); so `demOfN` gives `M` only the zero demand
(`HandoffExclusion.exclusion_demand`); so the next forward run emits only the zero fact in `M`
(`HandoffExclusion.forward_zero_init`, `forward_zero_edges`; all in `HandoffExclusion.exclusion_theorem`).

A METHOD KEY THAT LEAVES THE FRONTIER STAYS OUT while no seed lies in its call subtree. In forward run `k + 1` the
only demand pattern of `M` is the zero demand `(zero, none)`. In a restricted run a zero-premise summary is published
only through a demand pattern `(zero, jb)` with a `D-p` (§7.3), and a restriction with no `D-p` has no result
(`HandoffCases.restrictI_none`). So `M` publishes nothing, and forward run `k + 1` hands off no demand edge of `M`,
also no demand edge from the zero fact. The condition of the first bullet holds again, so the one-round theorem
(`HandoffExclusion.exclusion_theorem`) applies to the next round. This composition over several rounds is ARGUED:
each round is a Lean theorem, the induction is not (§11). It is the observation of `task.md`: a method key with no
demand edge after forward run `i` gets none in a later run, while no seed lies in its call subtree.

PROGRAM WRAP (`HandoffCases.Wrap`; the field limits 1, 2, 3):

```java
root():  x.a.b.c = source();  r = wrap(x);  sink(r.f.a.b.c);
wrap(x): z = new Z();  z.f = x;  return z;               // the model: ret.f = arg
```

Run 1 gives `wrap` only the complete summary `(arg, ., *) → (ret, .f, *)`, and it is crossable
(`Wrap.w1_exit_cross`, `w1_wrap_exits_cross`). The root cuts the source to `(x, .a, [any], T)` and reports the
vulnerability in the demand layer, so its sink is a seed (`seedsW_exact`).

* THE EARLIER HAND-OFF analyses `wrap` again. Backward run 2 enters `wrap` with `(ret, .f.a, [any], T)`
  (`Wrap.old_b2_wrap_init`) and hands off `((arg, .a, [any], T), (ret, .f.a, [any], T))` (`old_dem_w`); forward run
  3 emits `(arg, .a.b.c, $, T)` in `wrap` (`old_f3_wrap_init`) and cuts INSIDE `wrap` (`old_f3_wrap_cut`): a
  demand-layer edge in `wrap`, which had none in run 1.
* THE HAND-OFF OF §7.3 gives only the leaf of the root (`Wrap.handF_w1_exact`). Backward run 2 crosses `wrap` by the
  reversed record (`bn_cross`, by `applicable`) and has only the zero fact in `wrap` (`bn_wrap_zero_only`,
  `bn_wrap_edges_zero`). The forward demand is the zero demand and `((x, .a, [any], T), none)` of the root
  (`demN_exact`). Forward run 3 has only the zero fact in `wrap` (`fn_wrap_zero_only`, `fn_wrap_edges_zero`), applies
  the record of `wrap` in the root (`fn_record_applicable`: by `applicable`, not by `satI`; the call is a RECORDED
  call, `wrap_reachRR`), cuts in the ROOT (`fn_cut_in_root`) and still reports the vulnerability (`fn_found`). All in
  one statement: `Wrap.wrap_old_vs_new`.

THE NARROWING THEOREM (the concrete design; for F72 PENDING, WHY THERE IS NO EXCEPTION below): the search space only
shrinks, in the LOCATIONS AND THE MARKS, with NO EXCEPTION (`HandoffNoStar.narrowing_canon_loc_exactM`; its two halves `HandoffNoStar.narrowing_canon_fwd_exactM` and
`HandoffNoStar.narrowing_canon_back_exactM`; one hand-off: `Handoff.handF_narrowM`, `handF_narrow_locM`,
`demOfN_narrowM`, `handF_narrow_DR_exactM`; the forms in the locations only, `HandoffNoStar.narrowing_canon_loc_exact`,
`narrowing_canon_fwd_exact`, `narrowing_canon_back_exact`, `Handoff.handF_narrow`, `handF_narrow_loc`,
`demOfN_narrow`, `handF_narrow_DR_exact`, stay true). Let forward runs `n` and `n + 2` be restricted runs (`n >= 3`;
in the Lean numbering of §7.7 they are the forward runs `k + 1` and `k + 2`). Every demand pattern of forward run
`n + 2` WITH AN EXIT PATTERN (case 3 of §7.4: a fact-to-fact demand edge `(gb', jb)`) lies inside a demand pattern `d`
of forward run `n` of the same method key, in its LOCATIONS AND ITS MARKS: every location of its exit pattern `jb`,
with its mark, is a location of `D-p` of `d` with its mark, and every location of its entry pattern `gb'`, with its
mark, is a location of `D-c` of `d` with its mark. The reason: it comes from `d` through a forward exit edge `j → g`
(piece `g'`) and a backward exit edge `jb → gb` (piece `gb'`), and each restriction puts its premise inside `D-c` and
its result inside `D-p` of the demand pattern that published it, in the locations and the marks
(`Handoff.restrictI_narrow`; with the marks `restrictI_narrowM`, and for a concrete conclusion mark
`restrictI_narrow_conc`). So `jb ⊆ g' ⊆ D-p(d)` and `gb' ⊆ j ⊆ D-c(d)`. The hypotheses: the seeds have concrete
marks and no `*` tail (`BExact.SeedsConc`; a sink pattern has the tail `$` or `[any]`,
`HandoffNoStar.nonstar_of_sinkK`). THE THEOREM DOES NOT NARROW the zero demand and the seed-path patterns
`(gb, none)` (case 2 of §7.4): as locations they shrink when the backward field limit grows (argued), but their COUNT
can grow (§11 THE SEED PATHS). The induction over the rounds is in the theorem: it holds for every round of the
canonical sequence.

WHY THERE IS NO EXCEPTION (`ap-history.md` F70). The intersection keeps two cells whole (`Handoff.RExc`,
`restrictI_inter`): (a) an `[any]` conclusion at or above a `*/E` exit pattern keeps `[any]` (W2: a concrete mark has
no `*` tail), and (b) a `*` conclusion stays as it is. Both need a `*` tail, and in the CONCRETE DESIGN, with the
hand-off of the demand edges, no demand pattern after run 1 has one:

* run 1 hands off no pattern with a `*` entry tail, and its exit patterns (premises of run 1) have the tail `$` or
  `*/{}`: every normal FLOW leaf of run 1 is crossable (§1), and W2 leaves no demand-layer `*` (Lean
  `HandoffNoStar.handF_run1_nonstar`, `run1_exit_star_cross`);
* a backward run with such a demand and with the seeds above emits no `*` premise and has no `*` conclusion
  (`HandoffNoStar.DB_edge_nonstar`), so its hand-off has no `*` pattern (`HandoffNoStar.demOfN_nonstar`); so no demand
  pattern of a forward run has a `*` tail (`HandoffNoStar.canon_dem_nonstar`);
* a restricted forward run has no `*` conclusion (`Handoff.DR_exit_not_star`), and with no `*` exit pattern the
  forward narrowing is exact (`Handoff.handF_narrow_DR_exact`).

F72 MAKES THE LAST TWO BULLETS FALSE. A FLOW requirement of a backward run has `*` conclusions, so the backward
hand-off gives `*` patterns (the claims of `HandoffNoStar.DB_edge_nonstar`, `demOfN_nonstar` and `canon_dem_nonstar`
do not hold for F72), and a FLOW premise of a restricted forward run has `*` conclusions (the claim of
`Handoff.DR_exit_not_star` does not hold). The first bullet still holds: F72 does not change run 1, and the
normalization R1 changes only marks (`handF_run1_nonstar` and `run1_exit_star_cross` read run 1 only). So with F72 the
cells (a) and (b) can occur after backward run 2, and the `_exact` forms below are not the narrowing of the F72
sequence. The candidate is the form with the two cells as exceptions (`HandoffMain.narrowing_canon`,
`narrowing_canon_loc` and their `M` forms); it is not yet proved for the F72 runs (PENDING, §11).

In the concrete design a `*/E` exit pattern occurs only in backward run 2, as a run-1 premise `*/{}`, and there cell (a) adds no location
(`HandoffNoStar.rexc_empty_loc`, `narrowing_canon_back_loc`). The older statements `HandoffMain.narrowing_canon`,
`narrowing_canon_loc` keep both cells as exceptions; the exceptions of `narrowing_canon_loc` are loose (an existential cell that every `*` exit pattern satisfies), so the `_exact` forms are
the narrowing theorem. Their forms with the marks, `HandoffMain.narrowing_canon_fwdM`, `narrowing_canon_backM`,
`narrowing_canonM` and `narrowing_canon_locM`, keep the same two cells for the locations, but in these cells the marks
still narrow. The narrowing is of locations and marks, not of counts: one demand pattern can give several pieces.

THE MARKS HAVE NO EXCEPTION (`ap-history.md` F71). The mark test of the restriction keeps a conclusion with an
abstract mark (`*` or `*∖X`) against a concrete `D-p`, so for one restriction the marks narrow except at an abstract
conclusion mark (`Handoff.restrictI_interM`, `restrictI_narrowM`; the cell is real, `Handoff.RVec.inter_exc_absmark`).
In the concrete design every run of the sequence is concrete: the restricted forward runs (`RExact.DR_concrete`), and
the backward runs with seeds of concrete marks (`BExact.SeedsConc`, `BExact.DB_concrete`). So this cell never occurs
on the sequence, and the narrowing with the marks has no mark exception (`Handoff.restrictI_inter_conc`,
`restrictI_narrow_conc`, `handF_narrow_DRM`; the `M` forms above). THIS DOES NOT HOLD FOR F72: a FLOW premise has
conclusions with abstract marks (§3), so the cell can occur, and the normalization R1 replaces `*∖X` by the larger
`*`. The narrowing of the marks for F72 is PENDING (§11).

WITH THE TAIL `[any-taint]` (forward runs `AnyTaintEx.DRX … emitX satX restrictIX`). One hand-off:
`HandoffX.handF_narrowX`, `handF_narrowX_DRX` (the exceptions `HandoffX.RExcX`), exact with the exclusions. Over a
round on `HandoffXIter.canonStateX`: `HandoffXMain.narrowing_canonX`, `narrowing_canonX_loc`, and with no `*` pattern
`HandoffNoStar.narrowing_canonX_fwd_exact`, `narrowing_canonX_loc_exact`. With the marks (`restrictIX` has the mark
tests of `restrictI`, `HandoffX.insideXB` and `concMarkB`): one hand-off `HandoffX.handF_narrowXM`,
`handF_narrowX_DRXM`, and over a round `HandoffXMain.narrowing_canonX_fwdM`, `narrowing_canonX_backM`,
`narrowing_canonXM`; in the exception cells the marks still narrow. The hand-off reads each forward run with the
exclusions dropped (§7.3). In general a handed-off pattern can then reach past the earlier one at an excluded
location (the `Dropped` alternative of `HandoffXMain.narrowing_canonX_loc`; `HandoffX.XVec.v_inside_only_with_excl`,
`HandoffXMain.XMVec.exit_dropped`, which need a `*/E` demand pattern). With no `*` pattern, as on the sequence of the concrete design (not of F72, which has `*` patterns),
the exit side is exact, and the entry side is exact unless the premise exclusion is Universe, which the AP never has
(`ap.md` §1: no exclusion is Universe; the model keeps `Excl.univ` only to encode a `$` premise;
`HandoffNoStar.narrowing_canonX_loc_exact`).

THE FRONTIER LOG. After each complete run the driver logs the frontier of the run (§7.1; `Frontier`, §10). The log
has counts and method keys, no edge, so it costs one pass over the analyzers of the run (the `counters`, §4.1) and
over the hand-off:

* forward run: the method keys with a non-zero initial fact (`analysed`); the demand edges that the run hands off,
  per method key (`demandEdges`, §7.3); the crossable summary leaves (`crossableLeaves`: the records that replace an
  analysis in the next runs); the record applications that crossed a call in this run (`recordCrossings`); the
  DEMAND and the CONFIRMED vulnerabilities after the run (`demandVulnerabilities`, `confirmedVulnerabilities`); the
  sink seeds that it hands off (`seeds`);
* backward run: the method keys with a non-zero initial fact; the demand edges that it hands off (§7.4); the
  crossable backward leaves; the record applications at a call (`recordCrossings`: the backward records and the
  reversed forward records); the source seeds;
* both: THE WORK OF THE ZERO FACT, the part that §11 THE ZERO FACT leaves: the method keys that the run analysed
  only from the zero fact (`zeroOnly`) and the number of their edges (`zeroOnlyEdges`);
* AN OPTION (a diagnostic): per run, the number of demand-layer results per operation that set the layer
  (`demandByCause`): the field-limit cut, a may target, a cleaner row, the must-record demotion, a demand-layer input
  (§4.3).

By the narrowing theorem the demand patterns with an exit pattern only shrink, in the locations and the marks, per
method key (not the seed-path patterns `(gb, none)`, and not as counts); by the exclusion theorem a method key leaves
`analysed` when the run hands off no demand edge of it (for example, all its leaves are crossable) and no seed is in
its call subtree. The log shows how fast this happens on a real program. For the rules of F72 both theorems are
PENDING (§11), so the log is also the measure of these properties until the proofs are done.

HOW A LATER STOP STRATEGY CAN READ THE LOG (not normative: the policy is out of scope, §0). `continueAfter` gets the
frontiers of every complete run (§7.1). Examples:

* THE FRONTIER IS STABLE: two forward runs with the same set `analysed`, the same demand edges per method key and no
  new CONFIRMED vulnerability. Between them only the field limit changed: by the narrowing theorem the demand patterns
  with an exit pattern cannot grow in the locations or the marks (the seed-path patterns are outside the theorem; for
  F72 the theorem is PENDING), so a further pair of runs can gain only by the larger field limit;
* THE FRONTIER IS SMALL against the work of the zero fact (`zeroOnlyEdges`): a further pair of runs costs mostly a
  full pass of the zero fact (§11) for a small part of the program;
* THE BUDGET: the time of the last pair of runs and the size of the frontier give an estimate of the next pair. If the
  next forward run cannot complete in the rest of the budget, a stop now gives the same report as that incomplete run
  (§7.5: an incomplete run adds nothing) and ends earlier.

---
