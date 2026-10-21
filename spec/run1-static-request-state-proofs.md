# Static request state: proof and validation map

Date: 20 October 2026. Contract:
[Run-1 static request state and immutable GROUP identities](run1-static-request-state-and-group-identities.md).
The comparison base is `029ee41fd6`. Apply the selected enum filter to both
algorithms in all equivalence and inclusion claims below.

## Selected 21 October all-origin exception

The [all-origin correction](grouped-run1-statics-proofs.md#selected-21-october-all-origin-correction)
overrides this map's run-1 complete-ND writer/application requirements.
After complete compatible local admission, emit one atomic arm for every
contributing nonzero original. ZERO needs a complete permitted all-ZERO
binding. Each static pool/caller application matches that arm's initial.
Keep full sink evidence, original source targets and later FULL exact ND.
The three-set request protocol, canonical field covers, exact class carriers,
enum filter and immutable GROUP laws do not change.

The new local models prove scalar/factored origin equality, exact ZERO,
shared-leaf compatibility and full handoff pairs. Use their production
adapter gates. They do not prove whole-engine termination or full run-2
recovery. Old complete-ND or representative-only results remain reference
proofs; they do not establish equality of the new run-1 edge relation.

## Proof boundary

The enum target filter is a deliberate restriction. A mutable enum object
can carry taint through a local alias. Removing the copy-back alias can
remove that flow. The constructive counterexample is part of the model.
No theorem here establishes unrestricted no-FN behavior for this hack.

Canonical field answers are DEMAND covers. The required relation is
candidate inclusion, not equal run-1 precision. Each selected run-1
ordinary initial, and every FULL ND member, must pass the existing AP matcher. A request is not evidence
of a supplied group. ZERO activation is not such evidence either.

The models separate positive group derivation, request scheduling, AP cover
and immutable projection. Production adapters must preserve all four
contracts. These local models do not prove the complete JVM interpreter,
the scheduler, report confirmation, or a 60-second execution bound.

The resolved class contract permits only exact `S.C / Group(g)` actual
carriers and initials. The current grammar refines the historical grammar;
it does not claim the old nine-family equivalence. Class ANY metadata can
match the exact carrier and fields without creating a class-wide fact.
Strict BELOW excludes the exact carrier. No proof below changes predicate
or cleaner reach, ordinary field processing, or global-mark routing.

## Constructive models

| Obligation | Model and checked result |
| --- | --- |
| Exact enum filter | `EnumAliasTargets.selected_exact`: resolved STATIC + ACC_ENUM roots only; failed lookup and ordinary fields remain |
| Alias suffix and direct operation scope | Same module: descendant/root invariance, direct operation identity, filter idempotence and length nonincrease |
| Explicit soundness limit | `EnumAliasTargets.Controls.mutable_enum_flow_removed`: executable mutable-state counterexample |
| Three-set representation | `Run1ThreeSetRequests.disjoint`, `requested_partition`, `written_partition`: exact representation of logical requested/written bits |
| Both arrival orders | `both_orders`, `request_idempotent`, `request_cannot_supply` |
| Requested writer admission | `ask_once`, `requested_pair_closure`, `unrequested_write_no_broadcast`: one writer/group pair and no unrequested broadcast |
| Finite requested closure | `requestReplay` constructs a request-driven trace from each ordinary finite source/hop derivation; `requested_transitive_closure` preserves its complete writer/group trace and `replayOrdinary` reconstructs the ordinary witness |
| Canonical field cover | `canonicalWitness` constructs an executable suffix witness; `canonical_covers` covers EXACT and descendant regions, including the empty suffix |
| FULL/group permission | `full_mark_guidance`: preserve the FULL mark's group and the covered image |
| No source-free supply | `rootOf`, `empty_cycle_cannot_supply`: extract an actual seed from every supplied source/hop derivation |
| Complete local/ND reference input requirement | `CompleteBinding`, `firstBindingRoot`, `empty_cycle_cannot_complete_nd`: evidence for every member, not one member chosen as sufficient |
| Inherited marked/GEN and complete application | Existing `Run1OrdinaryStaticSupply.staticApply_complete_arity`, `compileStatic_complete_application`, `generated_output_uses_declared_group` and `marked_output_exact_image`; unchanged AP/output laws remain; complete-member application is reference/FULL evidence, while run-1 uses the selected atomic adapter |
| Actor reply lifetime | `admit_idempotent`, `consume_only_answered`, `queued_reply_preserves_new_membership`: an old reply cannot remove a different outstanding group |
| Dormant requests | `unavailable_quiescent`: pending data without an effect is not runnable work |
| Immutable atom projection | `ImmutableGroupNormalization.atom_list_equivalence`, `projected_list_idempotent`: typed GROUP/FULL/ZERO projection equals the reference |
| Exclusion factory invariant | `normalize_idempotent`, `normalize_denotation`: private normalized values are fixed points with unchanged symbolic meaning |
| Catalog-safe identity | `factory_attachment_equivalence`, `factory_projection_equivalence`, `foreign_attach_rejected`, `foreign_group_fallback` |
| Representation saving | `no_replacement_array_for_stable`, `slots_never_increase`: no replacement array for unchanged keys; changed projection does not increase array slots |
| Current eight-family grammar | `GroupedRun1Initials.classification`, `eight_families`, `class_initial_is_exact`, `baseline_class_chain_strictly_rejected`: executable parser and explicit subset of the historical grammar |
| Exact global-mark carrier | `ExactClassMarkers.initial_legal`, `current_exact`, `current_demand`, `direct_preservation`, `successful_adapter_is_exact`: exact payload, existing DEMAND layer and direct adapter |
| No class abstraction or field/class supply mixing | `no_abstract_class`, `no_class_star`, `rejects_class_any`, `rejects_class_must`, `field_never_supplies_class`, `root_never_supplies_class`, `class_never_supplies_field` |
| Ordinary marker preservation | `pass_path_fact_preserved`, `field_write_preserves_marker`: pass-through and field-position disjointness; explicit class rule actions remain outside this identity claim |
| Query metadata, not class-wide facts | `class_any_keeps_exact_global`, `class_any_keeps_field`, `field_query_does_not_become_class`, `strict_below_excludes_global`, `strict_below_keeps_field`: preserve inclusive and strict positional domains |

Models are in [spec/lean/ApSpec](lean/ApSpec). The selected audit is
[run1-static-request-state-audit.lean](lean/run1-static-request-state-audit.lean).
It checks all declarations owned by the three optimization modules, the
current initial grammar and the exact-class marker module. Only `propext`
and `Quot.sound` are permitted logical dependencies. Classical choice,
`Classical`, `sorryAx` and additional axioms fail the audit. Executable
controls check enum selection, typed normalization, request arrival order,
pair admission, queued reply membership, eight initial families and exact
class/field separation. The focused class audit is
[exact-class-markers-audit.lean](lean/exact-class-markers-audit.lean).

```sh
cd spec/lean
lake build
lake env lean run1-static-request-state-audit.lean
lake env lean exact-class-markers-audit.lean
```

## Preservation argument and adapter obligations

1. A requested output field/group joins each current matching FLOW writer
   once. A writer registered later joins the same requested set. A group
   written before its first request moves from reach to processed and asks
   existing writers at that first request. Thus neither event order omits
   a required writer/group pair.
2. For a static-origin FLOW writer, requesting its input repeats this rule
   on the input field. A finite source-backed writer derivation has finite
   depth. Replaying its final writer reduces the remaining derivation to
   its input. The source case answers from actual written membership.
   Fair owner processing and complete registration are adapter assumptions.
3. A fixed-group conditional writer keeps complete local condition admission,
   then publishes the selected atomic original. A static original joins an
   actual matching pool input; a nonstatic original follows ordinary singleton
   caller application. Omitted old run-1 originals are not later rematched.
   FULL ND still requires every member. A request or canonical hypothesis
   cannot replace an actual match of the selected required group.
4. Replace a former field-region answer by the canonical field cover at the
   same group and DEMAND layer. Existing `startFact` changes the marked
   current tail to ANY. ANY includes the empty suffix. The ordinary matcher
   and transfer must preserve candidate inclusion under this cover. This
   AP monotonicity is a production/reference-test obligation; the suffix
   lemma alone does not prove every AP primitive.
5. Keep the original program stage, selected atomic run-1 call binding and original-slot
   request in each actor payload. Existing point repair and ordinary summary
   composition publish the intermediate nonstatic group demands. Keep the
   implicit static permission family. The existing
   [grouped static proof dossier](grouped-run1-statics-proofs.md) then supplies
   inherited FULL backward laws. The representative entry-guide/output
   adapters need their own coverage check. Test the connected path
   through setter, wrappers, getter and a dead sink local at run-2.
6. Release only answered pending memberships. A queued effect owns its
   payload until consumption. Admit new memberships before enqueue and use
   monotonically fresh IDs. Pending rows own no work reservation. Existing
   queue accounting must count effects before releasing producer work.

The proof does not require unrequested copies to be evaluated. A later
request starts the finite requested dependency slice. It also does not
authorize skipping intermediate nonstatic marked demands: static permission
alone would not supply those demands.

## Production evidence

Use independent reference tests before optimization. Keep identity assertions
separate so the pre-change implementation can pass its behavior oracle and
fail the intended work-reduction assertions.

- JVM enum tests check both real statement aliases and injected call aliases,
  both rule views, unresolved metadata, constant-specific subclass roots,
  direct reads, explicit stores and enum initialization. Existing alias
  metadata tests check that remaining alias effects still have write tags.
- GROUP tests compare typed subsets with an independent scalar key/denotation
  oracle. They cover foreign/null catalogs, later first-use FULL members,
  public set interning, packed leaves, output headers and a FULL ID after a
  GROUP ID. Existing concurrent interning tests remain active.
- Static service tests must include the large all-static FLOW route, both
  event orders, req/reach/processed transitions, canonical answers and pending
  release. Pipeline tests must cover source-free copies, complete local run-1 conditions,
  atomic selected supply and later FULL ND joins,
  mark exclusions, broad/class supplies, obsolete selectors and run-2.
- Class adapter tests must reject a class STAR initial/current fact, preserve
  exact global markers, keep field supply separate, and retain late field
  matches for class ANY metadata. Check strict BELOW separately from
  AT_AND_BELOW. The query does not authorize a class-valued abstract fact.

Local build/test receipts and fresh workload evidence are stored outside the
checkout at `/drive-testcomp/opentaint-go-rules/run1-static-requests-20261020`.
The final implementation report must identify the committed source, exact
test gates and the latest reached/completed/accepted project iterations.
Passing a finite model or local test is not project acceptance.

## Current checkpoint

The default Lean build passes 168 jobs. The selected audit checks 943
declarations in five modules and passes 67 executable controls, with no
unexpected axioms. The focused current-grammar/exact-class audit checks
339 declarations and passes 39 controls. These results include the resolved
exact class contract. Historical class-chain proofs remain labeled as
comparison models; their constructors are not current legal initials.

The final broad local gate passes 2,139 tests: 1,632 common, 436 JVM,
38 application and 33 rule tests, with no failures, errors or skips. Its
receipt is `validation/gate-final-exact-class-v2/receipt.json`. All 3,215
core/rule source files stayed fixed during the gate. This includes the
exact-class adapter and input validation checks.

The first common run exposed four old exact field-premise expectations and
two tests with a whole-static `S.*` pool observer. The first JVM run exposed
an old enum alias endpoint expectation. The migrated field expectations
preserve paths, groups, conclusions, layers, source/ZERO behavior and
historical controls. Actual results are not normalized to hide a change.
The pool observer now requests its known field; the copy-cycle test requests
the group before the source to exercise requested closure. These observer
tests do not authorize `S.C.*` facts. The old class-wide actual-input test now
requires rejection before an ordinary request chain can start. The resolved
contract permits only an
exact `S.C` global marker and preserves class ANY query metadata separately.

Fresh main references for all four projects are closed. Main has no numbered
BIDI iteration. No candidate project measurement or post-optimization
performance claim is established by this checkpoint.
