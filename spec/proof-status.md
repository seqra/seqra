# Current proof status

The normative rules are [ap.md](ap.md), [interpreter.md](interpreter.md),
[analyzer-core.md](analyzer-core.md) and [F78 run-1 requests](run1-requests.md).
F78 replaces full-query/role requests, request exclusion tests and the callee
deferred-answer memo. It retains the exact caller input relation for summary
application and late requests. This file states proof scope and evidence; it adds
no analysis rule and makes no claim that the measured runtime implements F78.
[proof-history.md](proof-history.md) retains earlier
theorem catalogs; F70/F71 concrete-run results do not prove current restricted runs.

## F78: merged run-1 requests

Validation on 15 October 2026 passed for the corrected no-ANY initial policy:
`lake build` completed 125 jobs; `lake env lean audit.lean` checked 21,826
declarations with zero unexpected axioms. All executable audit groups passed.
Run-1 InitialAp now has EXACT or STAR tail only, with ten initial families.
An incoming ANY/must field supplier projects to concrete STAR; that initial
starts as an ANY fact in DEMAND. The same rule applies to nonexact class
projections. Exact suppliers keep EXACT. Abstract initials remain independently
additive. A requested normal-slot concrete answer requires both an admitted
request and an actual matching supplier carrying its mark; legal shape alone
does not authorize emission. Direct exact class inputs and metadata-selected
broad class components retain their separate paths, without a class request key.

The earlier 125-job /21,810-declaration PASS receipt checked the superseded
twelve-family policy, which allowed concrete field/class ANY initials. It remains
historical evidence and does not validate this correction. The
[proof dossier](run1-request-proofs.md#12-executable-witness-matrix-and-formal-scope)
retains those historical source/log bindings and the fresh full receipt for the
corrected sources, including no-ANY and request-gated additive emission controls.
No analyzer run or runtime change was made.

The [proof dossier](run1-request-proofs.md) gives the constructive arguments for
all nine original obligations and the storage, arrival and completion laws.
Its finite semantic domain includes all ten initial families for the selected
JVM run-1 domain (forward, limit exactly 1, empty handoff seeds and previous-run
records), the explicit impossible exact static-root supplier assumption, a complete
prescan field/form domain, broad suppliers and separate legal class points. The theorem statements
do not assume a predicate that already asserts request coverage.

| Result | Checked evidence | Scope |
| --- | --- | --- |
| Exhaustive run-1 initial grammar, supplied answers and start facts | [Run1InitialShapes.lean](lean/ApSpec/Run1InitialShapes.lean): `classification_exact`, `legal_no_any`, `legalAP_no_any`, `request_projector_no_any`, `class_projector_no_any`, `every_emitted_no_any`, `request_emission_exact`, `every_shape_has_supply_witness`, `started_layer`, `started_tail`, `started_FLOW_exactly_abstract` | Ten families in the selected limit-1 domain; no ANY/ANY_TAINT initial. Shape permission alone does not authorize emission. Generic runtime factories still need a conformance check. |
| Selected abstract field stays additive; concrete field requires its request and supplier | `supplier_starts_no_any`, `abstract_start_additive`, `no_request_abstract_only`, `nonexact_field_additive`, `nonexact_field_without_request`, `nonexact_field_with_request` | For each selected supplied structural slot, lack of R(T) removes only concrete T. It does not expand to irrelevant fields or authorize concrete T from a request alone. |
| Nonexact field/class projection uses STAR with local coverage | `Run1Requests.nonexact_field_chain`, `nonexact_class_chain`, `concrete_chain_any_start_equal`, `concrete_chain_any_same_coverage`, `concrete_chain_covers_exact_and_any`; denotational `concrete_chain_any_same_summary_relation`/`same_reversed_relation`; `replacement_changes_syntactic_summary_guard` | Same DEMAND/ANY start and ordinary denotational relation do not make native caller guards, initial identities, annotated records/reversal, hand-off or driver states equal. The explicit guard counterexample requires runtime refinement work. |
| Shared precomputed base, no duplicate memberships or initial notification wave | [Run1PrecomputedRequests.lean](lean/ApSpec/Run1PrecomputedRequests.lean): `initialise_shares_base`, `trace_no_base_copies`, `trace_preserves_base_reference`, `every_input_after_binding_sees_base`, `no_precomputed_delivery_after_any_trace` | Immutable base reference plus disjoint dynamic residual; temporary requests have an empty base. Logical lifecycle proof, not JVM allocation measurement. |
| Unchanged precomputed static routes need no propagation | `caller_frozen_base_inherits`, `frozen_batch_no_send`, `changed_origin_not_suppressed` | Actual frozen descriptors construct inherited slots and marks. Suppression is per queried slot and actual unchanged origin; changed-origin needs and late wide activations retain dynamic handling. |
| Exact two-slot projector, exclusion-insensitive request matching and separate class points | [Run1Requests.lean](lean/ApSpec/Run1Requests.lean): `project_exists_iff`, `projector_ignores_field_E`, `direct_class_preserved` | Finite scalar leaves; actual concrete marks are required for answers. Broad suppliers do not prove an exact NORMAL class point. |
| Effective producers, least finite graph closure and wide-scope activation | `effective_leaf_collected`, `closure_seed`, `closure_call`, `closure_least`, `recursive_call_coverage`, `wide_activation_complete` | Explicit effective-form vocabulary and complete canonical field domain. The finite oracle computes the least closure; SCC code must refine it. |
| Static/dynamic coverage and original-slot routing | `unchanged_static_plan`, `effective_static_dispatch`, `static_dynamic_cover`, `changed_slot_retains_dynamic` | Installed membership discharges a need; all other normal-origin needs retain their dynamic route. |
| Concrete initials and the special root generate no upstream requests | `concrete_never_requests`, `special_root_never_requests` | Actual premise, not the enclosing method analysis; concrete-answer handling stays separate. |
| Owned field partition and untouched identity frame | [Run1RequestsSafety.lean](lean/ApSpec/Run1RequestsSafety.lean): `finite_partition_exact`, `partition_disjoint`, `primitive_frame`, `frame_identity`, `root_field_composition` | Explicit finite component primitives/return semantics. This corrects unrestricted root-summary composition; it is not a proof of today's packed summary reader. |
| Cleaner/root counterexamples and native/opposite relation preservation | `Counterexamples.raw_root_cleaner_leak`, `Counterexamples.raw_root_write_loses_sibling`, `Counterexamples.constructive_frame_repairs_both`, `native_and_opposite_read_preserved` | Concrete counterexamples and component relation equality. Full F76/driver integration retains its separate scope. |
| One admitted relation plus temporary writes, exact fresh delta and commit/replacement | [Run1RequestProtocol.lean](lean/ApSpec/Run1RequestProtocol.lean): `commit_membership`, `commit_temporary_empty`, `fresh_nodup`, `every_prefix_retains`, `completed_admits_all` | Finite logical `(MethodKey,slot,mark)` cells, not a selected physical map representation. No callee supplier/memo field. |
| Batching, stale admitted reads and excluded-mark duplicates preserve the final union | `two_batches_one_barrier`, `barrier_placement_same_union`, `filter_destination_safe`, `excluded_has_emitted_need`, `derived_flow_exclusion_erasure` | Creator/transfer/call/join induction constructs the same-origin need; duplicates and pending requests are permitted. Event timing and timeout prefixes can differ. |
| Both input/request orders, registration race and local completion ownership | `answer_witness`, `both_arrival_orders`, `registration_race`, `completion_empty_owners` | A<E and Q<V, retained caller inputs, reliable counted queued effects. This is not a termination or 60-second theorem. |

Proof review required two explicit semantic clarifications: component-owned
summary reads plus an untouched identity frame, and same-origin request emission
for generic excluded-mark targets as well as cleaners. Both belong to F78 and the
[implementation proposal](../docs/design/bidirectional-run1-mark-request-implementation-20261015.md).
No Kotlin, IR storage or measured runtime change is included in this revision.

The remaining F78 implementation obligations are concrete refinements: complete
effective-form extraction; canonical AP-tree/component codecs preserving E and
uncertainty; ordinary/native/reversed reader guards; the existing caller index;
and actual lock, queue, cancellation and Work accounting. Finite semantic proofs
do not certify future code, pooled ND confirmation or the whole alternating driver.
Concrete STAR and the former ANY initial can start from the same DEMAND/ANY fact,
but their premise applicability and identity differ. The corrected policy's local
coverage/non-narrowing law does not establish summary, record/reversal, hand-off
or full-driver equivalence. Those bridges require explicit validation.
Historical memo proofs below are retained evidence for their old policy, not a
reason to keep a second request map, full query or deferred answer cache.

## Existing AP and driver proofs

The current general closure model has three tails: exact, star with field exclusion,
and may-any. It is single-premise and alias-free. It does not combine the full
must-tail/X, static, conjunction, end-fact, interpreter, and pipeline extensions.
`CurrentDefs.lean` defines run 1 `RC`, restricted forward `FC`, and backward `BC`.
All use `BaseCleaner.cleanRes`; only `RC` has request rules. `FW` and `BW` select
the current `emitW`, `satW`, and mark-aware `restrictI` operations.
These closures quantify over semantic input histories. They do not prove the selected emitter representation,
and their exact joint-support predicate does not validate a pooled candidate.
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
| A matched TAINT literal has the input layer; its unused coverage walk can be removed | [CurrentProposal.lean](lean/ApSpec/CurrentProposal.lean): `LiteralLayer.matched_taint_layer`, `LiteralLayer.Certificate.checked` | Concrete literal gate, annotated overlap, exact/any literal and exact/must/may TAINT input. A normal exact hit is covered; a normal must hit uses the must exception; a demand hit stays demand. FLOW requests and the generic literal rule remain separate. No full tree matching or ND closure theorem. |
| Applying a full concrete summary combination needs only a layer join | [CurrentSummaryApplication.lean](lean/ApSpec/CurrentSummaryApplication.lean): `uncorrelated_member_application`, `Certificate.native_answer_checked`, `Certificate.checked`; `CurrentProposal.Combination.joined_layers`, `Combination.Certificate.checked` | Each nonempty input already matches its concrete premise. Every premise tail, including concrete STAR/E; exact/must/may input and uncorrelated exact/must/may W7 target, with annotations. Each member returns the target with input/summary layer OR and no request; the two old output flags are complementary. The native run-1 answer witness starts DEMAND and applies its DEMAND summary with a distinct target mark. Local per-leaf application and Boolean accumulator equality, not an ND tree or pipeline theorem. |
| The proposal's per-leaf summary selectors equal the reference location and any-tail tests | `CurrentSummaryApplication.Selectors.applicable_at_exact`, `applicable_below_exact`, `inside_at_spatial`, `inside_must_flow_rejected`, `inside_must_excluded_flow_rejected`, `inside_above_exact`, `Certificate.checked` | All surface tails and field exclusions. May-tail exclusions are empty. A concrete must premise cannot match an abstract STAR input, so its differing spatial cell is rejected by the mark test. The proof models the selector rows, not the full Kotlin trie walk, marks traversal or store query. |
| T2 preserves union locations while mutual single-leaf coverage rejects the merged result | `CurrentProposal.T2Oracle.union_locations`, `T2Oracle.Certificate.checked` | Two normal must leaves with different exclusions merge to one empty-exclusion leaf. The union law holds at every concrete marked location for this witness. Compare raw operation outputs by full leaf identity before store reduction; compare stored results by union denotation. No general T2 tree or store equivalence theorem. |
| Must-record demotion does not commute with a general post-result field read | `CurrentProposal.RecordTiming.constructed_summary`, `applicable_only`, `timing_difference` | Actual annotated copy, strong-write keep, summary application, demotion and read. The read is a legal AP operation, but is not an ordinary JVM root return binding. This local boundary does not establish a whole call-plan counterexample or a timing equivalence. |
| Must-record demotion commutes with root mappings, filters, cuts and primitive root cleaners | [CurrentRecordTiming.lean](lean/ApSpec/CurrentRecordTiming.lean): `mapping_commutes`, `filter_commutes`, `cut_commutes`, `root_cleaner_commutes`, `Certificate.checked` | Concrete exact/any inputs with arbitrary annotations and layers for mappings and cleaners; arbitrary target prefixes cover forward alias application. Filters read the same location/mark fact, not its layer or annotation. No full F74 action-chain, alias-selection, optimized trie or call-plan theorem. No valid whole JVM timing counterexample is established. |
| Native must-record pending demotion preserves operation data and finishes after return mappings, before the call cut | `CurrentRecordTiming.LateReturn.pending_pipeline_data`, `split_eq_tagged`, `ordinary_finish`, `pending_finish`, `already_demand_finish`, `pending_pipeline_admitted`, `Certificate.checked` | Arbitrary sequences of per-leaf output operations. Separate inside/pending batches equal a per-member tagged reference; false pending candidates admit aliases before finalization. Surviving pending facts end DEMAND with Empty E; legal already-demand facts receive only the cut. Actual annotated summary/read, binding and alias witnesses check the local order. No early-demotion equivalence, request/premise handling, full F74/DAG, optimized trie or iteration theorem. |
| Below-case correlation-loss test can be removed without changing facts or requests | [CurrentSimplifications.lean](lean/ApSpec/CurrentSimplifications.lean): `LostCorr.applySimple_eq`, `applySimple_may_eq` | Legal STAR conclusions have abstract marks (W2); exact and must targets require concrete premise marks. Such a gate cannot propagate STAR. May-any targets already force DEMAND. Geometry before requests and the above case are unchanged. Local base transfer equality, not full annotated closure equivalence. |
| Historical raw-link confirmation: its separate member-admissibility check is implied by joint support | `CurrentSimplifications.Confirmation.confirm_equiv` | Keep admissibility inside the support's supplier test and zero-only root support. Supplier predicates stand for already computed normal links and caller support at one call. This remains a theorem of that historical support predicate; it does not confirm a pooled ND candidate after raw-store removal. No new fixed-point or vulnerability theorem. |
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
| Cumulative subscription storage preserves logical P5 and the checked E6 binding quotient | [CumulativeSubscriptions.lean](lean/ApSpec/CumulativeSubscriptions.lean): `grow_encodes`, `growth_trace_encodes`, `accepted_persists`, `fresh_delta_union`, `index_refines`, `effects_refine`, `projected_choices`, `combination_root_insensitive`, `combination_effects_quotient`, `normal_result` | Constructive exact accepted-leaf encoding per full admitted key, finite growth and position/effect lifting; proof history is ghost data, not a retained runtime list. Ordered E6 member projection preserves full references/link layers and every result descriptor after matching. Target denotes the pre-factory conclusion root; equal joined layers give equal canonical factory results. Does not prove the full current AP matcher/transfer distribution, protocol implementation simulation or whole-engine closure. |
| Historical raw-link request phase includes crossed arrivals; a uniform FLOW group needs one geometric match | [RequestSets.lean](lean/ApSpec/RequestSets.lean): `frontier_pairs`, `frontier_arms_disjoint`, `flow_group_effects`, `work_complete_iff` | Exact-delta logical set laws and the extended Work completion predicate. Request and link types stand for full admitted keys. FLOW collapse requires a uniform target and mx in one raw group. The emitter now uses N2 query replay; this theorem does not certify that replacement or require a runtime raw store. These results do not prove the Kotlin path cursor, captured-view ownership, runner simulation, full NNF support or whole-engine closure. Independent scalar and schedule tests must cover those implementation obligations. |

## Historical added-fact-store removal: proof scope

F78 supersedes the request identity, full exclusion predicate and deferred memo
in this section. These statements describe the earlier measured emitter policy.
Their checked theorems remain historical results with their original hypotheses.

The current design is [initial emission without an added-fact store](../docs/design/bidirectional-added-facts-removal-20261015.md).
Caller N2 remains the input relation used for summary application and late mark queries. No theorem about an
input relation requires a second callee copy of that relation. Historical raw-link objects can be ghost inputs
in an independent reference model; they are not a runtime representation requirement.

The proof obligations are deliberately separate:

1. **Emitter refinement.** Run 1 always emits the abstract STAR initial, also for `S`. Concrete initials are
   additive supplier-backed answers. The deferred memo has only question-specific answer shapes and marks,
   with no CallerRef or raw supplier history. Full geometry, E and mx determine eligibility before projection.
   Requesting T clears T only from the matching question's memo rows; the question stays active for later inputs.
   Later runs emit directly against the fixed demand and retain no emitter input history.
2. **Two-sided replay.** Old input/new question and old question/new input produce the same answer/climb relation
   as a full logical supplier join. Subscriber registration also replays standing questions. N2's existing
   run-1 summary index is not sufficient: request queries include prefixes and extensions and then the full AP
   predicate. Persistent question state, admitted N2 headers and counted pending work are part of this contract.
3. **ND scope.** Source and summary results keep full joint premises. Caller application still requires all
   members at one caller/call scope. A pooled ND sink may remain a candidate even when different callers supplied
   its members. It is not confirmed by normal layers or pooled availability alone: without a valid support or
   concrete validation result, `confirmed=false` and a new report entry is DEMAND.
4. **End effects and reports.** Sink end actions still generate zero-premise facts. A pooled candidate can thus
   create derived candidates that ordinary full-ND-premise return matching does not reject. Later trace validation
   must check this trigger dependency too; a complete trace engine is deferred. `Simple` output is not a trace
   certificate. Sticky previously confirmed entries, hand-off selection and stop rules remain explicit driver
   contracts. Source-hit/seed identities stay separate from witness proof identities.
5. **Lifecycle.** Optional exact confirmation through N2 needs a successfully joined read-only window before
   subscriptions are released. Current frozen actor edges and released N2 are not usable after that release.
   Cancellation must not commit an incomplete report or retain a replacement raw-input history.

[AddedFactsRemoval.lean](lean/ApSpec/AddedFactsRemoval.lean) and
[AddedFactsRemovalMemo.lean](lean/ApSpec/AddedFactsRemovalMemo.lean) supply the constructive local models below.
Checked on 15 October 2026 from `spec/lean`: `lake build` passed all 120 jobs, and
`lake env lean audit.lean` audited 19,879 declarations with zero unexpected axiom dependencies. The executable
certificates passed. The 45 explicit new `#print axioms` entries use only `propext`, `Quot.sound`, or no axioms.
Both modules are imported by `ApSpec.lean` and included in the audit. The
[external receipt](/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-abstraction-demands-v2/20261015T004517.956676Z/report-preparation/added-facts-removal-proof-validation/receipt.json)
binds the commands, source and logs; its SHA256 is `e6ce0433ab8abbb719af359faebc104e3f1f5daf5c9e0b6c90d477aff4286364`.
These are local proof gates, not completed Kotlin/runtime or fixed-project acceptance gates.

| Local result | Declarations in `ApSpec.AddedFactsRemoval` | Scope and limits |
|---|---|---|
| Finite question/answer transitions equal the ghost full-history output relation | `trace_refines`, `trace_outputs`, `output_iff`, `pending_iff`, `request_preserves_other_pending`, `request_is_persistent`, `late_supplier_emits`, `both_arrival_orders`, `duplicate_offer`, `origin_multiplicity_storage` | Offers are already eligible for the exact question, including its mark. Question-specific consumption and persistent interest hold for finite event sequences. This does not prove that Kotlin computes every eligible offer or bound the number of distinct answers. |
| Retained runtime state needs no supplier field or completed-output history | `Runtime`, `advance`, `erase_observation_step`, `advance_output_set`, `projected_output_iff` | `State.emitted` is logical observer output. The runtime erases it and sends transient outputs to ordinary projected-initial deduplication. It must not retain answered pairs merely to mirror the proof. No optimized packed-map implementation or heap-size result follows. |
| Packed answer rows refine scalar question/mark memberships | `memoCells_iff`, `packed_insert_refines`, `packed_consume_refines`, `putMark_idempotent`, `one_row_many_marks`; `Certificate.packed_consumption` in `AddedFactsRemovalMemo` | The decoder expands logical `(geometry, answer shape) -> marks` rows. Insertion and consumption preserve the exact scalar cell relation; consuming one geometry/mark leaves other marks and geometries. One fixed row can pack many marks. These are list/set laws, not a JVM persistent-map, primitive MarkSet, padding, allocation or byte-cost proof. |
| Run-1 admission always includes the abstract root and adds ready concrete answers | `run1_abstract_always`, `run1_abstract_shape`, `run1_concrete_additive`, `rc_unconditional_abstract`; `Certificate.run1_static_root_with_concrete` | Uses actual `policy1`; nonzero inputs have root STAR/Empty/*, including `S`. The structural list and ready concrete answers are supplied to this local adapter. Catalog completeness and concrete-answer eligibility are separate obligations, not inferred from interest membership. |
| A finite pooled ND candidate does not establish one-call confirmation | `Certificate.mixed_caller_is_not_confirmation` | Executable two-origin Boolean witness: pooled literals true, same-caller conjunction false. This is a distinguishing precision control, not a whole-engine ND sink, end-GEN or confirmation theorem. |
| Exact N2 replay and two-sided deltas lift the same effect relation | `exact_replay_refinement`, `n2_history_replay`, `n2_two_sided_frontier` | Conditional on exact replay/current cumulative N2 encoding; full origin/admitted keys remain until the effect test. The frontier identity covers crossed input/question arrivals before projection. No Kotlin prefix+extension index, registration race, concurrency or accounting simulation is proved. |
| Immediate restricted emission equals a ghost demand/input scan and instantiates the existing init rules | `restricted_immediate_exact`, `forward_immediate_init`, `backward_immediate_init`; `Certificate.immediate_emitW_values` | Uses actual base `emitW` and the current FC/BC init constructors. This is local membership/constructor coverage, not whole annotated-tree, record or iteration equivalence. |
| Historical per-leaf geometry checks precede answer projection | `checkedAnswer_iff`, `checkedAnswer_initial`, `rc_answer_from_checked`; `Certificate.geometry_distinguishes`, `projected_initial_is_not_supplier`, `exact_consumption_and_late_supplier`, `question_key_distinctions`, `original_slot_and_query_E_distinguish` | Annotated historical same-slot answer kernel. The RC bridge requires equal initial/pattern, empty query E and a nonstatic base. Distinct-slot static catalog geometry, current layer/ANY normalization, full typed trees and FLOW climb routing remain replay/AP obligations. |

Local finite emitter/replay proofs do not prove the Kotlin AP geometry kernel, N2 candidate-index completeness,
callback isolation, pending-work accounting, full NNF closure, source-seeded iteration or concrete trace validity.
No exact current pooled-ND confirmation or whole-engine no-loss theorem is claimed. A local emitter equivalence
uses fixed run inputs; a full iteration comparison must use the same candidate/confirmation policy on both sides.
Replacing raw Support with unverified DEMAND can add seeds and change later run configurations, so unchanged old
support-enabled frontiers are not a consequence of this local theorem. The strict project/main finding gate remains.

## Preserved caller input and local-operation evidence

For N2, P5 means that accepted logical leaves remain under their full caller/group keys, even when transient
replay delta objects are released. The admitted group is not reconstructed from a delta's normalized E.
The independent cumulative fixtures compare raw accepted history with current candidate selection and
summary transfer in run 1 and restricted modes, and compare ordered returned cleaners/aliases/cuts at
positive limits 1, 2 and 3. These are bounded implementation checks. The remaining proof obligation is to
instantiate `effects_refine` with every admitted current AP selector/transfer and to connect the cumulative
encoding to the generic reliable-join protocol under re-entry, records/read views and full current forms.
Existing base/concrete pipeline theorems do not discharge that obligation. N3 scheduler/accounting changes
are deferred and are not part of this proof module.

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
2. Full current no-loss iteration with F77 selection, source-seeded emission, the selected candidate/confirmation policy, method exclusion,
   and round-to-round narrowing. Mark normalization and AP §6.4 exceptions limit
   exact narrowing claims.
3. Combined must-tail/X and F76 read-view integration, statics, restricted conjunction support, aliases,
   end facts, and actual interpreter forms; their stronger hand-off guards.
4. Current pipeline/driver encoding and dominance under store subsumption.
   `Pipeline`'s generic reliable-join/quiescence theorem requires a correct encoding;
   historical concrete AP/driver instances do not supply the current instance.
5. Tree shortcuts beyond the proved per-leaf rebuild and candidate index. The
   old `restrictS/U` tree theorem does not prove the current mark-aware tail meet.
6. Connect F78's checked slot, component and no-memo protocol semantics to the
   actual AP-tree codecs, caller candidate visitor, native/reversed record reads
   and counted work. Preserve full ordinary summary premises without restoring a
   callee caller-separated input relation. Concrete verification of pooled ND and
   zero-GEN derived candidates remains a separate deferred task.

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
depth-cache, demand-link, alias-guard, conjunction-layer/split, matched-literal layer,
summary-combination layer, summary selectors, summary-union comparison, record-timing boundary,
root-mapping demotion commutation, late-return batch/finalization, and local-simplification witnesses
(including annotated FLOW overlap and its exclusion/mark boundaries).
The exact declaration count is reported by the audit and changes with the model.
