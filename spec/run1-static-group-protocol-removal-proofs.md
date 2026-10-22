# Proofs for run-1 static GROUP protocol removal

Date: 22 October 2026. Status: **checked proofs; runtime adapters implemented**.
Read with [the specification](run1-static-group-protocol-removal.md).
The reference runtime is `a4bf789866f912ebefc54bca42d809c89beea2d4`.
See [implementation validation](../docs/reports/bidirectional-static-protocol-removal-validation-20261022.md)
for the production adapter checks. The proof claims and their limits are unchanged.

## 1. Claim and comparison boundary

The selected optimization removes two repeated pool protocols. It keeps
the scalar AP reducers, caller suffixes, original selection, output bounds
and method-demand handoff. The claim is relative to that reference:

> For every finite reference derivation of a required selected demand
> edge, the new procedure constructs the same method/original/result edge.
> It also retains the same sink seeds and implicit static query guidance.
> Thus these removals cannot omit a demand needed by the next iteration.

Compare the denoted relations after a COMPLETE run. Batching can change
callback order, duplicate work and diagnostic counts. Do not compare an
OOM/timeout prefix to a completed fixed point. Do not infer a proof of
the reference engine's unrestricted soundness from this relative claim.

The proof does not assume that equal pool group sets imply equal demands.
It preserves complete method boundaries, including nonstatic returns and
explicit control-ZERO exits.

## 2. Definitions and reused contracts

For an eligible static GROUP original j, let `K(j)` be its canonical
DEMAND current fact. Let a scalar output v include its complete AP, mark,
tail, E and layer. A call context c includes the method, caller original,
call, stage ordinal, caller/link layers, alias flag and suffix state.

Define:

```text
CallReduce(c,j,v,a) = the existing staticSupply pure body on actual input a
SupplyReduce(j,v,a) = existing satisfying, applySummary, layer and limit callback
Demand(m,j,v)       = the selected method summary relation, before handoff
H(m,j,v)            = the existing normalized backward demand pair
```

Neither reducer is replaced by a new transfer relation. The implementation
extracts and reuses the old body. Both adapters must pass the same typed
arguments, not values that merely have the same mark group.

Reused engine contracts are:

1. Positive stores retain admitted run-1 relations. E is empty in run-1.
2. Ordinary call subscriptions replay existing and future summaries to
   their exact caller/stage. First requests and late writers retain their
   existing once-per-membership admission.
3. Admitted work stays counted through delivery. A complete run has
   drained the required finite work. An absent group may remain dormant.
4. The existing AP reducer and handoff normalization are deterministic
   for their complete inputs. Semantic stores admit equal complete results
   idempotently. Rule stores keep their current origin and witness contracts.
   No new condition or exit policy is introduced.

These contracts are not conclusions inferred from a performance counter.
The production correspondence table below identifies their source seams.

## 3. Constructive proof

### P1. An eligible original has a prior answered request

Induct on the finite derivation that produced the original. A local
static read creates a FLOW original. It cannot be the first GROUP case.
Method-entry abstraction also cannot create a static GROUP original.
The first field GROUP constructor consumes an actual PointReady reply.
That reply follows the atomic installation of `processed`. The exact
class constructor consumes an actual matching supply after the request
has been recorded in `classRequested`.

A transfer, source transfer or selected contributing-origin arm inherits
its original. An ordinary call preserves its actual caller original.
A static promotion preserves its callee static original. A ZERO promotion
keeps ZERO. Apply the induction hypothesis to that inherited original.

This constructs the earlier supply/request witness. It does not attach
a new runtime support field. The causal witness time precedes creation
of the GROUP edge, so the proof does not use a future source to justify
an earlier hypothetical input.

Following supply derivations also extracts an admitted source witness.
Thus a source-free FLOW cycle cannot manufacture its first GROUP edge.
The proof uses current admitted GEN supplies; it adds no eligibility test.

### P2. The repeated request has no new discovery effect

For a field group already in `processed`, `answered` contains that group,
`newlyRequested` is empty and no pending request is retained. There is no
new writer/group admission. The only new semantic-work vehicle is the
reply to this redundant consumer.

An exact class request can revisit matching writers, but each matching
writer/group pair was already admitted by `askOnce`. A newly registered
writer reads `classRequested` independently. For fields it reads
`req + processed`. Remove neither that registration nor previously secured
writer delivery. Therefore skipping the repeated request loses no writer
work, including when the earlier job has not yet run.

The first request for `reach` is different: it moves the group to
`processed` and can wake writers. P1 excludes this case at both optimized
boundaries. This is why availability alone would not prove the change.

### P3. The local input is the same input

For a field, the pool canonicalizes the requested slot to C.f and
materializes its requested GROUP with empty E at DEMAND. STAR taint
materialization yields the current ANY tail. `factsOf(j, Empty, DEMAND)`
performs the same materialization with the existing GROUP atom.

For an exact class marker, both paths keep the exact class path, exact
tail, same GROUP atom, empty E and DEMAND. No field projection or group
enumeration is required. Complete `satisfying` therefore returns the same
matched value. The existing selected-original reconstruction returns j
for both eligible shapes.

No proof of `applySummary(K(j),j,v)=v` is needed: both paths actually call
the same AP reducer on `K(j)`. Keep exact/ANY/MUST outputs, output mark
changes, raw alias partition and bounding callback. Unsupported legal
initial shapes use the old path. This is not a grammar restriction.

### P4. The unary output survives the old wait

This is a storage proof, separate from input availability. The old fixed
GROUP callback uses `currentValues`, which requires the exact scalar
output to remain stored. An arbitrary FULL summary with changing E would
not justify replacing a delayed selector by its original delta.

In the selected run-1 TAINT scope all E values are empty:

- `TaintGroup.add` can remove and reinsert an entry only when its header
  changes. The changed-E branch with equal root/tail cannot occur: an
  entry with empty E would already match the same-key search.
- The ordinary update merges roots. Its merged root uses per-path union
  of exact marks and ANY marks. Child directories preserve old-only children.
- Subtraction computes an incoming delta. It does not subtract from the
  retained merged root. An added ancestor, broader tail or another mark
  therefore cannot erase an old scalar payload.
- Header normalization cannot remove the old ANY tail while an old ANY
  payload remains. Its layer and original key are unchanged.

Consequently an admitted scalar output remains findable by `currentValues`
at the delayed reply. It denotes the same scalar used by the new immediate
composition. No retained historical root or new selector cache is needed.
Keep nonempty-E/FULL behavior outside this proof on its current path.

### P5. Eliminate one call handshake

Take a successful reference activation with context c, original j and
scalar output v. P1 constructs its prior answered-request witness. P2
shows that the new repeated request has no discovery effect. P3 constructs
the exact input that its reply would carry.

Replace the finite steps

```text
save continuation -> commit interest -> known answer -> deliver reply
    -> CallReduce(c,j,v,K(j))
```

by `CallReduce(c,j,v,K(j))` on the same actor. Each argument is unchanged.
Thus every scalar output, raw alias decision, static promotion, resumed
suffix and sink/source occurrence has the same semantic effect. Scalar
output iteration is kept; no rule/suffix additivity assumption is hidden
in this replacement. The old pending CallKey can coalesce duplicate offers.
The new path can execute their equal reducer more than once. Equal results
join idempotently at the existing semantic stores; compare membership and
complete witness identities, not callback multiplicity. Preserve source,
sink and rule occurrence identity when applying this duplicate quotient.

In particular, retain j as the caller original. Substituting ZERO would
fail the proof because it changes both demand exits and source meaning.

### P6. Eliminate one unary supply join

Take a fresh retained static TAINT output v under eligible j. Run-1 has
one executable original, so the old join has one member. P1 supplies its
prior answered input. P2 removes any possible new discovery effect. P3
gives the same canonical input. P4 shows that its delayed output selector
will still return v.

Both procedures execute `SupplyReduce(j,v,K(j))`, then the same
`staticSlices` and service supply admission. They offer the same complete
regions/groups, including changed output groups and exact class markers.
Raw summary admission precedes this hook. The unchanged
`queuePublications`/`addDemand` then runs for that retained delta before
the method batch barrier. The composition result cannot veto it. Neither
the join nor its reply is a demand-storage authority.

The output's first request is still a real request. It can admit writers
and must not be deleted just because the input's repeated request was
deleted. A nonstatic hypothetical input is not covered by P1 and cannot
use this supply shortcut.

### P7. Complete finite derivations and recursion

Translate a finite reference derivation from its leaves. Keep all source,
local transfer, first request, writer, ordinary call, rule and sink steps.
Replace each eligible call protocol by P5 and each eligible unary supply
protocol by P6. Their premises already have translated finite witnesses.
Apply the same subsequent ordinary transition to the resulting values.

This recursively constructs the same edge at the same method/stage with
the same original and complete result. It also constructs each subsequent
real output supply and request result. Recursion does not require an
acyclic call graph: any observed edge has a finite successful derivation.
An unproductive cycle has no finite GROUP source witness under P1.

Conversely, a new direct step can be expanded into the old redundant
request and its immediate known answer, followed by the same reducer.
This gives equality of the finite semantic closure, not equal callback
traces. Duplicate offers map to an already represented derivation and do
not require a fresh old continuation for each occurrence. Fair counted
delivery connects the finite derivations to a
COMPLETE fixed point. It is not a termination bound for the whole analyzer.

### P8. Every method demand pair is preserved

Raw admission and `queuePublications`/`addDemand` stay unchanged. By P7,
every ordinary call and wrapper obtains the same values under the same
original. Therefore the selected relation `(method, original, result)`
is preserved at **each** boundary. It is not enough to preserve one pool
field/group endpoint or only the entry patterns of the demand store.

Apply the same handoff projection H to each retained pair. Image of equal
relations under the same function is equal. This preserves STAR/MUST
normalization, the group owner, paths, method identity and every paired
exit. In particular:

```text
j=C.f/*/G -> return/G   gives (return/G, C.f/*/G) in each reader/wrapper
Z -> C.f/G              gives (C.f/G, explicit Z), not an absent exit
```

Normal merge/subsumption may represent equal denotations with different
nodes; compare the denoted pair relation. P4 closes the selected concrete
storage-selector issue rather than assuming exact historical nodes survive.

### P9. Internal sinks and implicit static guidance

P5 keeps scalar suffix execution and its sink occurrences, even when a
local result never becomes a return summary. `HandOff.toBackward` uses
those same demand report entries and resolves the same original FULL
rule occurrences to sink seeds. No new exit filter or rule provider is
introduced. It therefore constructs the same seed set.

The shared implicit static GROUP family also stays enabled. It accepts
an actual full static query under its group; it supplies no fact and
cannot substitute for a return demand at a getter/wrapper. The unchanged
actual-query rule retains its existing permissions.

For the dead-local example in the spec, ordinary run-2 sink discovery
starts at the same internal sink. Getter reversal creates the same static
requirement. Balanced ZERO returns carry it through `deep` and `wrap`.
The actual static query can then use implicit static guidance and reach
the setter. Neither version needs an invented forward return summary for
the dead local. A helper that throws after the check also needs no normal
return summary; keep its represented exceptional exits. A truly exit-free
infinite body has an existing backward ZERO-discovery/CFG obligation.
These protocol removals neither prove nor repair that separate case.

### P10. No new omission in run-2

Run-2 receives the same explicit demand relation by P8, the same sink
seeds by P9, the same implicit guidance switch and the same retained valid
records. Its rules, field limit, FULL mark handling and actual-query
admission are unchanged.

Induct on a finite reference run-2 derivation. Its seed and permission
lookups have the same answers. Each primitive, call, rule, cleaner and
source step has the same inputs and applies the same transition. Construct
the corresponding next step. Thus a run-2 path cannot be lost because
either removed run-1 protocol omitted its guidance. A later run still
requires a real C.f fact to satisfy C.f; ZERO receives no new permission.

This is a relative preservation theorem. It does not assert that existing
source/exit precision issues, historical handoff bugs or project failures
are fixed. It proves no loss caused by the two selected removals.

### P11. Work reduction

For each admitted eligible scalar call continuation, the reference protocol
creates one repeated interest, one reply and a pending call row; the new
procedure creates zero. For each eligible scalar computed output it removes one
unary join, one input row, one repeated interest and one reply. These removed
protocol counts add over finite derivations. The reducers have equal semantic
effects. Physical AP/suffix counts can differ because the old pending table
coalesces equal offers and currentValues can visit equal scalar payloads
under multiple headers. An aligned-job model is not a proof of equal
physical counts for an arbitrary scheduler trace.

The bound is linear in the number of admitted eligible protocols, independent
of how fast the service can answer them. Peak live rows depend on queue
order. The proof establishes removed protocol work and storage, not a
whole-engine speedup factor or a fixed time limit.

## 4. Production correspondence

Paths below are relative to
`core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/`.
The source reference is the selected runtime, before either removal.

| Proof | Production seam and required correspondence |
| --- | --- |
| P1 | `engine/Run1Abstraction.kt` rejects static method-entry inputs; `engine/Run1PointResume.kt` read/reply/materialization constructors; `engine/RunMethodCallServices.kt` staticSupply and promotion preserve originals |
| P2 | `engine/Run1StaticService.kt` request, groupWrite, requestClass, registerWriter and askOnce; admitted effects remain counted until delivered |
| P3 | `engine/Run1StaticService.kt` facts; `ap/Demand.kt` initialFacts; `ap/ApManager.kt` factsOf normalization; old selected-original reconstruction in staticSupply |
| P4 | `ap/Groups.kt` TaintGroup.add; `ap/TrieOps.kt` mergeAddDelta; `ap/Trie.kt` TAINT leaf union; `ap/Conclusions.kt` withRoot; `engine/Run1PointResume.kt` currentValues |
| P5 | `engine/RunMethodCallServices.kt` staticSupply/applyParts/resumeCallees; `ap/Run1SummaryAliases.kt`; complete Subscription/CallerRef coordinates |
| P6 | `engine/Run1PointResume.kt` computedGroups/publishMatched; `engine/RunMethodAnalyzer.kt` composeStaticSummary, retainSummary and queuePublications |
| P7 | Existing replay/subscription, actor work reservations, method batch barrier and run completion; no new dedup gate |
| P8 | `store/RunSummaries.kt` separate raw/demand collections; `driver/HandOff.kt` forEachDemandPiece/toBackward/location normalization |
| P9 | `driver/HandOff.kt` original FULL sink occurrence selection; `store/DemandStore.kt` implicitStaticPair and actual-query consumers |
| P10 | Unchanged RunConfig/iteration policy, records, FULL rules and ordinary backward transitions |

The Lean models are executable finite protocol/store models, not a Lean
translation of Kotlin/JVM. The source argument above connects their
constructors to the actual ports and storage operations. Implementation
must also pass the production controls listed in the specification.
No compiled abstract model alone proves that an edited Kotlin adapter
passes those controls.

## 5. Formal models and check receipt

The origin model is
[Run1StaticPremiseAvailability.lean](lean/ApSpec/Run1StaticPremiseAvailability.lean).
It derives a processed witness by structural recursion through the
producer grammar, extracts a source witness, and checks first/repeated
request effects. Its older raw-output identity adapter is **not used**
to justify bypassing the AP reducer in this specification.

The new model is
[Run1StaticGroupProtocolRemoval.lean](lean/ApSpec/Run1StaticGroupProtocolRemoval.lean).
Its [focused audit](lean/run1-static-group-protocol-removal-audit.lean)
imports both modules, checks all owned declarations for unexpected axioms,
and evaluates their controls. It rejects `Classical.choice`, `sorryAx`,
native decision axioms and any additional axiom. Only `propext` and
`Quot.sound` are allowed logical dependencies. The finite witness
constructors do not use classical choice.

The model retains complete method observations, scalar reducers, persistent
stored leaves and handoff projection. Arbitrary unchanged reducers are
reused, rather than asserted to be identity functions. No new runtime
support storage is implied by the finite derivation witnesses.

### Checked declaration map

The first row uses namespace `ApSpec.Run1StaticPremiseAvailability`.
The other rows use `ApSpec.Run1StaticGroupProtocolRemoval`.

| Proof | Checked statements and executable witnesses |
| --- | --- |
| P1: prior answer and source | `originAvailability`, `origin_available_no_later`, `published_static_premise_is_processed`, `sourceWitness`, `unseeded_static_summary_cannot_publish` |
| P2: no new repeated-request discovery | Availability model `processed_request_wakes_no_writer`, `unrequested_reach_does_not_suffice`, `repeated_exact_class_request_has_no_fresh_writer_job`, `late_exact_class_writer_is_covered`; protocol `call_repeat_request_wakes_no_writer` |
| P3: identical canonical input | `same_canonical_input`; field ANY and exact class controls include the DEMAND current-fact layer |
| P4: selected physical leaf persistence | `empty_exclusions_cannot_replace_existing`, `run1_merge_is_union`, `admitted_selector_persists_in_merge`, `exact_leaf_survives_finite_later_admissions`, `liveSelectorWitness` |
| P5: scalar call replacement | `call_scalar_arguments_and_effects_equal`, `call_boundary_effect_simulation`, `call_boundary_all_demands_preserved` |
| P5/P7: duplicate-offer quotient | `repeated_equal_effects_do_not_change_handoff_membership`, `duplicate_boundary_in_context_preserves_handoff_membership` |
| P6: unary supply replacement | `immutable_computed_boundary_equal`, `computed_original_raw_and_demand_kept`, `persistent_selector_seed_membership` |
| P7: finite closure | `finite_boundary_trace_effects_equal`, `replayDerived`, `complete_positive_supply_closure_equal`, `replayObserved`, `complete_semantic_effect_closure_equal` |
| P8: all method pairs | `finite_handoff_method_entry_exit_group_seed_equality`, `method_scope_and_complete_pair_retained`, `concrete_group_not_erased_by_normalization` |
| P9: internal sink and implicit permission | `symbolic_static_permission_kept`; distinct returning-wrapper and sink-only dead-local controls, explicit ZERO-exit and implicit FULL-owner controls |
| P10: next-iteration guidance | `preserveRun2Guidance` constructs an explicit-pair, sink-seed or implicit-static authorization from its reference witness |
| P11: removed protocol work | `protocol_work_zero`; `aligned_reference_jobs_same_scalar_work` applies only to aligned reference jobs, not arbitrary physical callback counts |

The canonical materializer in the model encodes the two selected shapes.
The source argument P3 connects that codec to `factsOf`. The model does
not re-prove every AP primitive. Similarly, the P10 Lean declaration
transports guidance witnesses; the ordinary full backward transition
argument in P10 reuses the unchanged run-2 engine. No whole Kotlin/JVM
execution theorem is claimed.

### Focused validation receipt

Lean version: **4.33.1**, pinned by `spec/lean/lean-toolchain`.
The focused module compilation and audit pass. The audit checks **1,441
owned declarations**: 533 availability and 908 protocol-removal declarations.
All **66 executable controls** pass: 24 availability and 42 protocol-removal
controls. No unexpected axioms occur.

Controls include field/class canonical inputs, complete handoff coordinates,
explicit ZERO exits, distinct wrapper method
pairs, a dead local with a sink but no invented return summary, FULL-owner
implicit guidance, persistent exact and ANY leaves, and duplicate boundary
effects. Diagnostic lists can have different lengths while semantic
membership remains equal. These are formal-model controls; they are not
claims that the future Kotlin implementation already passes its tests.

Reproduce from `spec/lean`:

```sh
lake build ApSpec.Run1StaticPremiseAvailability ApSpec.Run1StaticGroupProtocolRemoval
lake env lean run1-static-group-protocol-removal-audit.lean
```

The completed focused check used `lake env lean -o` to compile the new
module against the existing checked availability module, then ran the
audit above. It did not run the entire historical formal suite.

| Artifact | SHA-256 |
| --- | --- |
| `ApSpec/Run1StaticPremiseAvailability.lean` | `259ba854b454ca3128b9e92e10f6edb48f53115362e761f05e162142be0184a8` |
| `ApSpec/Run1StaticGroupProtocolRemoval.lean` | `a6f79177729b3fa9c6f87838381ccead6a6fefb9ed49e733d352fc4c4d3f104d` |
| `run1-static-group-protocol-removal-audit.lean` | `7178648fcdb6287b3043c973dee02dad9e2d43adc78dc61f028ecc0be5e29825` |

The implementation remains unchanged. No new project run is part of this
proof delivery. The latest reference Thingsboard run reached **run-1 OOM**,
with **no completed iteration** and **no accepted report iteration**.
