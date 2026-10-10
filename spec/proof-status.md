# Current proof status

The normative rules are [ap.md](ap.md), [interpreter.md](interpreter.md), and
[analyzer-core.md](analyzer-core.md): F72 demand sharing, F74 field-cleaner lowering,
F75 base-dependent cleaner splitting, F76 must-summary read views, and F77
structural summary demand selection. This file
states proof scope and evidence.
It adds no analysis rule. [proof-history.md](proof-history.md) retains earlier
theorem catalogs; F70/F71 concrete-run results do not prove current restricted runs.

The current general closure model has three tails: exact, star with field exclusion,
and may-any. It is single-premise and alias-free. It does not combine the full
must-tail/X, static, conjunction, end-fact, interpreter, and pipeline extensions.
`CurrentDefs.lean` defines run 1 `RC`, restricted forward `FC`, and backward `BC`.
All use `BaseCleaner.cleanRes`; only `RC` has request rules. `FW` and `BW` select
the current `emitW`, `satW`, and mark-aware `restrictI` operations.
These generalized closures do not implement every W6 layer refinement. The
tail/mark shape proofs alone do not prove the typed FLOW any-demand/star-normal
flags of the full spec. These closures use the generic record reader. Their
alternating hand-off sequences still use the historical no-any selector.
F76 read views and F77 structural selection are checked separately below;
they are not an integrated current closure/iteration theorem.

| Current result | Evidence | Conditions and limits |
|---|---|---|
| F75 cleaner coverage with computed output-or-request witness; normal-result exactness | [BaseCleaner.lean](lean/ApSpec/BaseCleaner.lean): `cleanRes_sound_witness`, `cleanRes_exact` | Local operation, not full run coverage. Same-base abstract selected-mark facts always request in run 1, including an already excluded mark. Concrete/all-mark spatial behavior is unchanged. |
| C2/L6 emission produces an actual premise covering the pair, satisfying the added fact, and wholly inside the demand | [CurrentDemand.lean](lean/ApSpec/CurrentDemand.lean): `emitCertificate`; [AbsDefs.lean](lean/ApSpec/AbsDefs.lean): `emitW_contract` | Abstract pattern tail must be exact, any, or empty-exclusion star. A concrete pattern requires a concrete added mark. Demand and added fact cover the same marked location. Nonempty-star counterexample: `Abs.emitW_needs_patTail`. |
| C5 reduction computes a publication retaining the demanded pair, input mark and layer; no new pair | `CurrentDemand.restrictCertificate`, `reduceEmission` | Whole premise inside `D-c` in locations **and marks**; actual input denotation pair; `D-p` present and covers the exit **with its mark**. Premise overlap and location-only exit tests are insufficient. Exact demand intersection has AP §6.4's three representability limits. |
| Normalization computes the actual hand-off pattern and preserves location sets and covered marks | `CurrentDemand.normalizeCertificate` | Changes mark exclusions only. Star field exclusions stay. Must-tail forgetting is a separate X operation, outside this base certificate. |
| Current prefix index gives exactly the same reduction results as a full demand scan | `CurrentDemand.restrict_index_equiv`, `insidePrefixWitness` | Key is `base::path`. Lookup returns ancestors of the premise key; run full `restrictI` on each candidate. Equivalence is result membership, not order or duplicate multiplicity. |
| Per-leaf tree reduction and rebuilt tree have exactly the same whole facts, layers, and mark exclusions | [CurrentTreeReduction.lean](lean/ApSpec/CurrentTreeReduction.lean): `tree_reference_equiv`, `tree_reference_key`, `tree_reference_wf` | Executable reference rebuild. Generic model trees permit cells that canonical typed FLOW values exclude. No optimized structural-walk or full X-tree theorem is claimed. |
| Actual restricted closure initial facts and publications have computed demand certificates | [CurrentDemandClosure.lean](lean/ApSpec/CurrentDemandClosure.lean): `forwardEmission`, `backwardEmission`, `publishedReduction` | Uses the explicit entry-shape guard. These certify local constructors, not coverage of every real witness. Concrete seeds are needed for canonical hand-off/representation results below. |
| Current run-1 and restricted-forward normal edges/records are exact | [CurrentExact.lean](lean/ApSpec/CurrentExact.lean): `RC_edge_exact`, `FW_edge_exact`, `FW_records_exact`; valid variants | S7 mark well-formedness; upward-closed filter acceptance (`FiltUp`), or S13 filter/validity conditions. Restricted runs require exact input records. The proof inducts directly on F75 closures, not old-cleaner embeddings. |
| Current backward nonzero normal edges/records are exact | `Current.BW_edge_exact`, `BW_records_exact`; valid variants | Same mark/filter/record conditions on the backward program; no binding into callee zero. Zero-premise seeds are excluded. |
| Actual reversed backward normal record pairs are forward flows | `Current.BW_rev_record_exact`; [CurrentHandoff.lean](lean/ApSpec/CurrentHandoff.lean): `BW_rev_record_exact_shape` | Mark well-formedness, upward-closed forward filter acceptance (`FiltUp`), exact backward input records, no forward zero back-binding, mark-reversible/exact-shape statements and calls. Premise is nonzero and has empty exclusion for star; the hand-off shape derives the latter. Dropping real backward filters does not establish this theorem. |
| Historical alternating base hand-offs preserve the C2 entry-tail guard | `Current.shapeSeq_entry_shape`, `shapeSeq_PatTail` | Concrete non-star sink seeds and the earlier generic selector. This sequence does not implement F77. Arbitrary record sets suffice for this shape result; their exactness is separate. Current builders must enforce the guard. |
| Local paired demand guards prevent abstract exact FLOW entries and narrowed conclusions | [CurrentKinds.lean](lean/ApSpec/CurrentKinds.lean): `emitW_tree_init`, `restrictI_tree_flow`; historical sequence `shapeSeq_tree_shape` | S7/S8 and the stated paired guards. The sequence also needs concrete non-star seeds, `RecordKinds`, and the earlier selector. Abstract entry patterns are any; exact exit demand implies concrete entry mark. `EntryShape` alone is too weak. Full F77 guard preservation and W6/typed-tree packing remain open. |
| Native record-kind conditions survive raw insertion, guarded generic base reversal, and retained unions | `Current.native_record_kinds`, `revRec_kinds`, `reversed_record_kinds`, `record_kinds_union` | Derive native kinds from reached closure edges. Generic reversed reads use the historical eligible-premise predicate and concrete-premise preservation. This is a conditional generic/base result, not a completeness theorem for all native records or F76 concrete-star views. |
| Exact-premise forward must summary has a computed backward view with exactly its annotated converse pairs | [CurrentMustReverse.lean](lean/ApSpec/CurrentMustReverse.lean): `readView`, `exactCertificate_converse`, `transferCertificate`, `exact_transfer_rejects` | Native normal singleton forward record, concrete premise/target marks, exact premise with empty annotation. Preserve conclusion E on the star read premise; discard the incoming suffix at the exact target. Relation equality is local; S14 native-record exactness is required to infer real program flows. View is transient, never started or persisted. |
| Computed must-premise views cover every annotated converse pair and every application stays demand-layer | `CurrentMustReverse.mustAnyCertificate_covers`, `mustExactCertificate_covers`, `must_read_demand`, `must_application_demand` | Normal singleton native must record, concrete marks, must or exact conclusion. Keep conclusion E on the star input; forget old must-premise E in the may-any output. These are pair-inclusion and layer results, not pair-exactness or program-flow coverage. |
| Must-to-may copying preserves published pairs while forgetting must exclusions | [CurrentMustHandoff.lean](lean/ApSpec/CurrentMustHandoff.lean): `copyCertificate`, `handoff_retains_pair`, `x_any_restricted_demand` | Copy each selected published piece, including an exact narrowed piece. Must-to-may forgetting can add locations. `normal_any_selection` and `selectAny_equiv` describe the historical generic selector only. No full annotated hand-off coverage claim. |
| Exact-to-must reader guards compute a normal transient view | `CurrentMustReverse.exact_guards_eq_normal_read`, `exact_guards_iff_normal_view`, `normalMustWitness` | Native singleton forward record, normal must target and concrete marks, exact premise with empty annotation. Conclusion E is retained. These are reader guards, not callee-demand selection. |
| F76's star/E view matches each canonical concrete exact or may-any requirement covering an admitted marked location | `CurrentMustReverse.concrete_read_matches`, `normal_view_transfer` | Arbitrary E. Matching is `inside || applicable`; exact transfer returns the old premise without the suffix. Abstract marks are outside this contract. |
| Normal exact-to-must view returns a distinct upstream mark and reaches zero through an actual reversed source edge | `CurrentMustReverse.Certificate.upstreamSourceWitness`, `normal_read_reuses_distinct_mark`, `restricted_piece_normal` | Executable local chain with U ≠ T and nonempty E; annotated restriction computes an exact normal publication. No source-hit store or full iteration model. |
| F77 selector retains exactly demand-layer, multiple-premise, and backward-zero summaries | [CurrentSummarySelection.lean](lean/ApSpec/CurrentSummarySelection.lean): `demand_iff`, `omitted_iff`, `normal_forward_singleton_omitted`, `normal_backward_nonzero_omitted`, `restriction_preserves_selection` | Native summaries have nonempty premise sets and satisfy store-kind rules. The finite carrier conservatively keeps an invalid empty set. Actual annotated restriction preserves selection through the unchanged layer. No closure, persistence, or global no-loss theorem. |
| A must-premise backward view produces caller DEMAND without callee demand | `CurrentSummarySelection.must_view_omitted`, `Certificate.caller_demand_without_callee_demand` | Actual native record, read view, and summary application. The native normal singleton is omitted regardless of the view's output layer. |
| Forward conjunction joins literal contribution layers; normal hand-off selection preserves NORMAL; a split branch is not a sufficient forward summary | [CurrentConjunction.lean](lean/ApSpec/CurrentConjunction.lean): `part_factor`, `joined_normal_iff`, `joined_demand_iff`, `normal_publication_example`, `backward_split_example`, `ReverseWitness.both_support`, `ReverseWitness.a_alone_not_support` | Exact rewrite of the current binary `AnyTaintND.conjLayerT`. Local accepted-slot coordination, exact and empty-exclusion must examples, and a concrete support counterexample. No full standing-store, annotated restricted ND closure, or iteration theorem. Selection and persistence policies are unchanged. |
| Below-case correlation-loss test can be removed without changing facts or requests | [CurrentSimplifications.lean](lean/ApSpec/CurrentSimplifications.lean): `LostCorr.applySimple_eq`, `applySimple_may_eq` | Legal STAR conclusions have abstract marks (W2); exact and must targets require concrete premise marks. Such a gate cannot propagate STAR. May-any targets already force DEMAND. Geometry before requests and the above case are unchanged. Local base transfer equality, not full annotated closure equivalence. |
| Confirmation's separate member-admissibility check is implied by joint support | `CurrentSimplifications.Confirmation.confirm_equiv` | Keep admissibility inside the support's supplier test and zero-only root support. Supplier predicates stand for already computed normal links and caller support at one call. No new fixed-point or vulnerability theorem. |
| Two side conversions equal the three current must-record view branches | `CurrentSimplifications.MustViews.readView_factored_equiv` | Equality for every `NativeRecord`, including rejected shapes. Preserve concrete marks, exact-side empty annotations, conclusion E, input-exclusion weakening and result layers. Generic reversal remains separate. |
| Canonical FLOW satisfaction is annotated location overlap | `CurrentSimplifications.Flow.canonical_flow_satisfaction_X` | Premise STAR with empty field exclusion and mark `*`. Equality uses existing `satX`, `applicable`, and `overlapX` for arbitrary added fact and added exclusion. Keep general matching for excluded or concrete reader premises. Per-leaf equality only; no new optimized tree/trie walk or iteration guard-preservation theorem. |
| One action-list stage and an identity-candidate flag preserve the local plan rules | `CurrentSimplifications.Analyzer.action_stage_merge`, `merge_reverse`, `origin_quotient`, `update_commutes` | Arbitrary ordered actions; reversal swaps endpoints and reverses actions. The alias guard observes identity candidacy and current layer only. Retain unset candidacy before initialization and independent `StageKind`. No whole call-plan, runtime benchmark or iteration claim. |
| One premise-key/layer group check equals per-leaf selection | `CurrentSummarySelection.group_selection_equiv`, `counted_selection_equiv`, `countedPacked_work` | All leaves share the premise key and layer. Equality preserves list order and duplicate multiplicity. Tails, marks, paths, bases, and exclusions do not affect selection. No packing or later-consumer cost claim. |
| Normalized TAINT destination lookup preserves unique exclusion keys and every retained annotated leaf | [CurrentTaintGroupKeys.lean](lean/ApSpec/CurrentTaintGroupKeys.lean): `insert_unique`, `insert_canonical`, `merge_normalize`, `place_reads` | Local subtraction/factory/destination model. Removing the last must leaf resets E to Empty; lookup uses that remainder key. Does not prove T2, full store delta or trie sharing. |
| Sink oracle joins positive literals only within one method key | [CurrentSinkJoin.lean](lean/ApSpec/CurrentSinkJoin.lean): `method_isolation`, `scoped_iff`, `same_method_retained` | Finite local join partition; computed counterexample rejects inputs from different contexts. Sink checks and confirmation are separate. |
| Hand-off counts equal accepted stored patterns, excluding duplicate and implicit-zero insertions | [CurrentDemandFrontier.lean](lean/ApSpec/CurrentDemandFrontier.lean): `method_count_delta`, `build_count` | Local builder/count interface. Pattern tags stand for already normalized canonical patterns; the model does not prove AP normalization. |
| Exact counted-depth metadata skips a subtree exactly when all paths fit the budget | [CurrentDepthCache.lean](lean/ApSpec/CurrentDepthCache.lean): `depthWitness`, `skip_iff_fits`, `stored_skip_iff_fits` | Counted and uncounted accessors, sibling maxima, signed Int representability. The 32,768/32,767 executable regression rejects the old Short saturation shortcut. Does not prove the whole field-limit tree operation. |
| Root EXACT cleaner produces the coarse demand link and preserves the precise descendant in the DLINK fixture | [CurrentDemandLink.lean](lean/ApSpec/CurrentDemandLink.lean): `precise_descendant_disjoint`, `Certificate.check` | Actual annotated read, binding, cut, concrete emission and cleaner vectors at limits 2–5. Local fact trace only; no CFG, source-hit, hand-off or full five-run confirmation theorem. A named field cleaner uses F74 lowering and cannot supply this demotion fixture. |
| Alias selection accepts every demand-layer result and skips only normal identity results | [CurrentAliasGuard.lean](lean/ApSpec/CurrentAliasGuard.lean): `demand_passes`, `normal_identity_skips`, `selection_iff` | Local origin/layer guard used by the engine and form reference. Computed regression retains demand default identity and constructor results. Does not prove alias analysis or the whole call plan. |
| Restricted runs have no request objects | `Current.FC_no_req`, `BC_no_req` | By closure definition, not by concreteness. Abstract facts can occur. |

An existence result is not obtained by selecting data from a proposition. The
certificates above compute the relevant AP function first and carry its proof.
Runtime checks evaluate those data through Lean's compiler.

The maintained candidate index is a checked optimization. On the distinct
33-demand workload, `CurrentDemand.reductionWorkWitness` computes 33 full restriction
calls for a scan and 1 for indexed lookup. This counts full restriction calls;
it excludes index construction. Lookup also walks at most the key length plus
one path-map nodes. Both queries return the same result set. Cleaner batching
has a separate 33-to-1 base-guard check witness in `BaseCleaner.workWitness`;
it still maps the residual over all leaves. Neither result claims constant total
runtime or an optimized sharing theorem for the whole analyzer.

The group selector has a separate work certificate. On 33 sibling must leaves,
`CurrentSummarySelection.Certificate.workWitness` computes 33 selector calls and
33 leaf visits for the reference, versus one call and no leaf walk for the group
shortcut. `counted_selection_equiv` proves equal results; `countedPacked_work`
proves the group cost. These counts exclude premise equality, construction,
trie packing, and consumers.

The regression modules preserve their design versions:

| Module | What its executable witness establishes |
|---|---|
| `CleanerLowering`, `FieldCleanerX`, `ReviewFieldCleaner` | F74 read/clean/write lowering, reach policies, outside-field X fact, and reached 1/2/3 program source hit and finding |
| `ReviewRootCleaner` | Root-EXACT loss under the old spatial rule; historical counterexample, superseded by F75 |
| `ReviewBackwardCleaner` | Backward abstract pass-through can produce a reached stored record and false normal finding; the rejected experiment |
| `ReviewBaseCleaner.Repaired` | F75 produces the concrete request/hand-off, source hit, record, and normal finding for the root-EXACT example |
| `ReviewBaseCleaner.Blocked` | F75's actual stored record reverses with mark exclusion and rejects `T`, blocking that bad-record application |

The last check rejects the witnessed record. It does not enumerate every possible
finding. A reached local regression is not a global iteration proof.

The remaining current obligations are:

1. L1–L4 mode coverage: abstract forward/backward coverage, run-1 mode selection,
   and contract B through F74 lowered actions and F75 cleaners.
2. Full current no-loss iteration with F77 selection, source-seeded confirmation, method exclusion,
   and round-to-round narrowing. Mark normalization and AP §6.4 exceptions limit
   exact narrowing claims.
3. Combined must-tail/X and F76 read-view integration, statics, restricted conjunction support, aliases,
   end facts, and actual interpreter forms; their stronger hand-off guards.
4. Current pipeline/driver encoding and dominance under store subsumption.
   `Pipeline`'s generic reliable-join/quiescence theorem requires a correct encoding;
   historical concrete AP/driver instances do not supply the current instance.
5. Tree shortcuts beyond the proved per-leaf rebuild and candidate index. The
   old `restrictS/U` tree theorem does not prove the current mark-aware tail meet.

These obligations are explicit limits, not established invariants of every
implementation run. The proposals use the checked demand reference and enforce
its representation conditions; they do not claim these wider proofs.

Build and audit from the repository root:

```bash
cd spec/lean
lake build
lake env lean audit.lean
```

[audit.lean](lean/audit.lean) examines every declaration from every imported
`ApSpec` module, including private and generated declarations. It rejects transitive
axioms other than `propext` and `Quot.sound`; thus `sorryAx`, classical choice, and
native proof oracles fail the audit. It also executes the cleaner, lowering,
regression, emission, reduction, normalization, index-cost, tree, must-hand-off,
must-summary read-view, full-selector, destination-key, sink-join, frontier-count,
depth-cache, demand-link, alias-guard, conjunction-layer/split, and local-simplification witnesses
(including annotated FLOW overlap and its exclusion/mark boundaries).
The exact declaration count is reported by the audit and changes with the model.
