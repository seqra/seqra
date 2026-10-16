# Proofs for run-1 exclusion-update demotion

Status: constructive design proofs and reference-model laws for the field-
write/default parts of the [unified run-1 specification](run1-field-writes.md).
[Request/static proofs](static-handling-proofs.md) cover the same specification's
request, initial-emission and later-static parts. This contract replaces the
former general weak-write policy.
Complete strong kills remain kills; only E-requiring residuals demote.

Latest measured runtime: `28a123f0e`. Reached/completed/accepted iterations:
OWASP **run-7 / run-6 / run-5**; conductor **run-2 / run-1 / run-1**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
See the [project measurements](../docs/reports/bidirectional-run1-selective-preservation-20261016.md).
These local proofs do not establish whole-project completion or precision.

## 1. Model and correctness statements

Fix a finite selected AP domain, a positive run limit, legal accessors/marks,
full method contexts, resolved calls and stable rule/action occurrences.
Run-1 has limit exactly 1, its nine legal initial families, empty prior
records/handoff, and no abstract S-root initial. Exclusions in compiled
strong recipes are distinct from exclusions in admitted run-1 facts.

The strong reference uses the existing logical field-write, cleaner, binding,
source and full-premise semantics. Run-1 keeps complete overwrite kills and
disjoint preservation. Only surviving E-requiring residuals take an E-free
DEMAND cover. Ordinary stored static defaults and layer-only demotion remain.
Later runs use the strong reference. Compare partial covers through positive
may coverage, not equality of their findings.

Three properties must not be confused:

| Property | Meaning |
| --- | --- |
| Strong semantic validity | A full supported derivation proves a transfer/return in the selected strong model |
| Run-1 coverage soundness | Every relevant strong positive flow has a covering run-1 candidate derivation |
| Default path precision | Instantiating S identity retains the actual field/class path and origin; it does not introduce another static coordinate |

A broad DEMAND cover can include removed locations; a separate fully
overwritten component is killed. A default can also preserve a location
not justified by later strong execution. These may alternatives can be spurious. That is the reason to request refinement. A NORMAL default certifies metadata's
no-static-write classification, not return or whole-engine confirmation.
All F1 artifacts remain guidance-only for later strong execution.

Required frontend/engine bridges retain explicit scope: complete effective
producer/effect inventories; correct AP overlap/normalization/packing;
ordered local actions and bindings; signed condition and seed coverage;
whole ND premises/support; fair counted processing through quiescence.
No theorem assumes the existence of all required old F1 summaries.

## 2. Strong preservation with a partial cover

For a definite destination cylinder D_w and pre-store value transfer V_w:

```text
Strong_w(X) = (X minus D_w) union V_w(X)
```

Do not replace this equation with `X union V_w(X)`. Instead inspect ordinary
preservation of each current component a:

| Case | Ordinary old result | Run-1 old result |
| --- | --- | --- |
| Entirely within D_w | Empty | Empty |
| Disjoint from D_w | a | a with its original layer |
| Broad ancestor requiring E | Restricted residual | Original payload a at DEMAND, Empty E |

A complete overwrite has no old survivor, so its coverage proof has no old
witness to construct. For a disjoint survivor, choose the unchanged component.
For a partial survivor, choose the DEMAND cover: removing the proposed field
restriction includes every location of the strong residual. Its full AP
payload and tail are retained; only the unresolved layer differs. This gives
a constructive witness for each strong old survivor without restoring every
killed component or enumerating fields.

For the geometry used by a definite write, a destination prefix of the
current path kills that component. A strict broad ancestor can need an
exclusion. Disjoint paths and an exact ancestor point preserve unchanged.
Base equality and ordinary alias/write certainty are part of classification.
A syntactic exclusion on a keep edge alone proves none of these outcomes.
Possible-alias gen retains the existing alias-preservation rule; it cannot
borrow a definite-kill conclusion. Current conclusions, not original premises,
determine the case. Packed leaves are classified independently.

For each strong new-value output choose the same pre-store transfer witness
in V_w(X). Killing old x.f does not remove the pre-store read in x.f=x.f.
Constant untainted values add no taint. Each value output keeps its own
support/layer. Thus old/value union covers the strong store, while exact
x.f and its descendants have no stale preservation branch, even at DEMAND.

Field RemoveAll and cleaner/rewriter field writeback use these same cases.
Cleaning a temporary and writing it back kills old destination components;
only the cleaned value transfer can repopulate them. A broad partial ancestor
can still contribute unresolved demand. Whole-base operations and direct
mark cleaners keep their ordinary separate transfer rules.

## 3. Layer monotonicity without AP information loss

Layers are Booleans with false=N and true=D. OR is associative, commutative,
idempotent and has N as identity. Define `moveToDemand(e)` by changing only
the edge layer. For every payload p:

```text
moveToDemand((p,N)) = (p,D)
moveToDemand((p,D)) = (p,D)
applyIdentity((p,inputLayer), edgeLayer) = (p,inputLayer OR edgeLayer)
```

These equations construct the result and prove exact payload equality,
idempotence and layer monotonicity. Base/path/tail/marks/E/mx and the
originating premise do not change. In particular, known STAR stays STAR.
A DEMAND input cannot become NORMAL under a NORMAL default. Independent
value/source derivations remain separate alternatives, not promotions.

The old W2/W8 demand-driven STAR/must-to-ANY map is superseded for this
operation. Its earlier checked laws describe a different, coarser map; they
do not prove the new tail-preservation law. ANY remains available when an
actual transfer loses information. Demand projection into a query pattern
is also separate from executable edge demotion.

## 4. Empty-E covers and the must counterexample

For a legal result r with nonempty E, let `Cover1(r)` retain its actual
base/path/tail/mark/origin, move it to DEMAND and use Empty E. This is an
explicit exclusion-free cover. If E is already empty, layer demotion itself
changes none of the payload.
Every location admitted by r's exclusion is still admitted by the cover;
the latter may admit additional possible locations. Those additions carry
no NORMAL correlation or must guarantee. This proves positive may coverage.

For must results, compare potential taint and guarantees separately. All
actually retained marked locations remain possible in the cover. No claim
that *every* location is marked remains. The DEMAND result is provisional; its retained tail is not a new NORMAL
strong certificate. Clearing E and changing the layer are separate operations.

Executable counterexample domain: locations f and g, T true only at g.
`must / {f}` is satisfied. Replacing it with `must / Empty` at NORMAL would
assert T at f and is false. A DEMAND may cover does not make that assertion.
Such a reference residual is reachable in run-1: an AnyField source emits
normal ANY_TAINT, then a partial cleaner removes the f part. No forbidden
must initial is needed to reach this case.

For direct selected-mark cleaners, exact exclusion-free kills/keeps remain
their usual results. Field overwrites/writebacks also keep complete kills
under §2. Only a surviving E-requiring residual needs this cover. Static cleaners
first partition their actual first-field scope; disjoint g is unchanged
and cannot request T merely because f shares base S. For an excluded-mark
copy g→f, applicability uses g, the output exclusion belongs to f and the
interest routes to the original g origin. The field-E cover must not clear
mx, erase that request, or discard the copy by comparing source to target.

## 5. Global run-1 Empty-E invariant

Define the closed run-1 producer system with:

1. Empty-E starts/initials and true ZERO/source outputs.
2. Ordinary preservation kills/keeps, E-requiring residual covers, and
   independent pre-store value transfers.
3. Exact E-free cleaner/filter results or Cover1 for E-requiring residuals.
4. Every other local/model/alias/binding/summary/cut output using the same
   run-1 normal form before entering any queue, index or store.
5. Packing, union and delivery that preserve payloads and do not invent E.

Induct on the finite derivation of an admitted fact. Starts use (1). A killed
preservation branch emits nothing; an unchanged branch retains Empty E, and
a partial branch emits a DEMAND cover without adding an exclusion. A value
branch uses its source witness and (2)/(4). A partial-clean/model result
uses (3)/(4), whose result explicitly has Empty E and the required layer. A
default applies only to an actual admitted fact and uses layer join, so it
also preserves Empty E. Packing/replay uses (5). Hence every intermediate
fact has Empty E, not just the final method summary.

Executed method premises originate in Empty-E initials and their summary
conclusions in this producer system. The independently inserted default
summary also has Empty-E endpoints; inserting it emits no root initial.
Its later ordinary application preserves the invariant. Thus both summary
sides have Empty E. No old record can inject E because F1 has no previous
run records. Any explicit external/model E-bearing input must be covered
before admission, rather than assumed absent by the induction.

Changing store lowering alone does not close the producer system: partial
cleaners and other explicit E producers need the same boundary policy. Implementation must route *all*
listed operations through the selected recipes and assertions. A demand
cover may be chosen without ever allocating the excluded reference result.
Later strong facts keep their ordinary E; no global factory erasure follows.

## 6. Prescan effect closure

Let W0(M) be a local static mutation/unknown-effect contributor. The finite
prescan graph includes all resolved callees. Iterate:

```text
W(n+1)(M) = W0(M) OR any(Wn(N), M calls N)
```

A true bit has an executable witness: a finite call path ending in W0.
Prove this by induction on n, prepending a chosen finite enumerated call
when its target bit supplies the witness. Conversely induction on such a
path derives the true bit. This gives the least closed Boolean family:
any closed family containing W0 contains each path witness.

In a cycle without a contributor, every approximant is false. If a cycle
can reach a contributor, append that finite path to obtain true for every
reaching member. Finite monotone iteration stabilizes; SCC sharing is a
representation of this closure, not a new semantic premise.

Provided the effect inventory covers each selected static-mutating operation,
W(M)=false excludes static modifications on every finite path in M/callees.
Cleaners/RemoveAll/rewriters/possible static aliases and unknown effects must
be contributors; “no PUTSTATIC” alone does not establish this property.
Reads and effective mark tests are not writes. This effect inventory never
adds an abstract field-read request.

No-write classification does not imply return: a pure recursive loop has
no write witness and no finite normal-return derivation. Thus the closure
proves the default's chosen layer, not a later executable return identity.

## 7. Ordinary default storage and application

Prescan defines `Default(M)` as ordinary STAR identity on S with edge layer
W(M). On reached method initialization insert it through the same retained
summary, demand-selection, publication and notification path as any summary.
No root initial or root method fact is created. The summary premise pattern
and the admitted initial grammar have separate purposes.

Let the actual static input be `(payload, L)`. Identity retains payload and
ordinary layer composition gives `L OR W(M)`. Exhausting the four Boolean
pairs proves the exact layer table; identity proves exact base/path/tail/
mark/E/mx equality. Unrelated writes can demote f but cannot change f into g
or erase its known tail. Neither input-first nor publication-first requires
a special call branch: ordinary stored replay covers the latter, ordinary
subscriber notification covers the former. Input delivery keeps its actual
payload/layer; gates inside abstraction determine initial emission only.

The constructor must store the **identity transfer** plus its edge layer.
Normalizing its root conclusion to ANY before application would instead
create a cross-location relation. Similarly, applying the old final
normalizer after joining DEMAND would unnecessarily turn an actual STAR
into ANY. Both are excluded by the selected layer-only contract. This
requires a generic summary/AP representation refinement in Kotlin; it is
not proved by reusing the old Facts-backed summary adapter unchanged.
Existing genuine ANY transfers must keep their distinct meaning.

Retained summary patterns can have root endpoints. Only root initial
emission/processing is forbidden in F1. Do not filter default rows by a
blanket root-pattern ban and do not add a default-specific bypass.

## 8. Ordinary demand handoff and strong reconstruction

A DEMAND default is selected by the ordinary DEMAND selector and retained
with other selected summaries. After live SummaryStorage is released,
ordinary actor.summaries.demandEdges() still contains it. The existing actor
traversal therefore exports it without synthesizing a descriptor, adding a
second iterator or scanning the prescan graph. NORMAL defaults follow the
same general guidance policy as other NORMAL F1 summaries.

Actor coverage follows from ZERO: roots admit zero, local/entry/binding
operations preserve it, and each actually reached resolved call sends Reach
to its callee. Fair handling admits that callee's zero actor. Normal actor
initialization publishes its default before that method can become quiescent.
Induct on the finite reached call path in a completed F1. Static initials are
not a premise. This source-bound bridge is not a checked runner theorem.
The [unified pipeline trace](../docs/design/bidirectional-request-gated-static-handling-20261016.md#8-ordinary-demand-selection-and-handoff)
shows a helper writing g, receiving caller f/T, emitting no static initial,
and answering with its ordinary stored DEMAND identity.

Handoff demand projection is not identity execution. It can produce a broad
static query pattern under the ordinary restricted grammar. Later strong
analysis may start S-root FLOW from that demand; only run-1 forbids root
initial emission. No actual-field adapter is required specifically for a
default. The strong computation must reconstruct preservation, kills and
copies with ordinary field E rather than execute the provisional default as proof.

Keep concrete current requirements and concrete guides additive. For
`f=source(U)` and actual B2 f/U, FLOW alone cannot cross the reversed concrete-U
gate. The existing general current-query fallback or a concrete guide must
admit that branch. All-base fallback also covers mixed local/argument/return
paths and full ND support. This is the general later-query contract, not a
new default handler. Actual computed source f can answer an output g query.

For soundness, induct on a strong answer derivation: only strong primitives,
supported rules, composition and modeled-return witnesses publish executable
answers. A projected default guide starts work and proves no answer itself.
For completeness, follow a finite balanced strong derivation. Ordinary demand
and current-query fallback admit its necessary work; computed responses are
not rejected by F1 ownership or missing old guide rows. Fair notifications
resume callers. A recursive cycle with no return witness creates no answer.
The earlier balanced-call proof checks this finite-derivation argument in
its generic state model, not the packed asynchronous runner.

## 9. Candidate coverage and strong phase isolation

Induct on a selected strong positive source-to-sink derivation. Local field
writes use §2; exclusion-requiring residuals use §4; exact cleaners/filters
use their ordinary supported outcomes. Actual value reads/effective positive
mark leaves still activate the existing read/mark plans and original-slot
request replay. A real static returning path is covered by its default
preservation or genuine callee value outputs; genuine nonstatic/ZERO work
remains. Joins keep layers/support and ND keeps every premise child. Thus
the corresponding relevant positive run-1 candidate survives, possibly
DEMAND and with extra may alternatives.

This proof retains the selected signed/NNF policy, not dynamic absence
testing. It requires the existing frontend/seed/full-premise bridges in §1.
It does not prove equality of NORMAL witnesses or reports with the old
strong run-1 engine, and it gives no time or memory bound.

Preserve every F1 sink candidate's occurrence/support/seed regardless of raw
layer; label it unconfirmed. No old stop rule based only on missing demand
edges may prevent its B2 refinement. §8 then supplies the real strong source
hit for each genuine selected path. Strong later runs rebuild corresponding
forward paths under the existing seed/limit contracts. Every executable
record has a later strong witness: no F1 default, E-free partial cover or
coarse guide is persisted/reversed as executable evidence, even if NORMAL.
This also excludes F1 contamination through nonstatic endpoints.

## 10. Checked scope and required controls

[Run1StrongPreservation.lean](lean/ApSpec/Run1StrongPreservation.lean)
models the selected rule from actual base/path-prefix/tail geometry. It checks
complete kills before partial-ancestor exclusion needs and keeps write
certainty separate. It preserves the opaque original premise and current
payload, joins no layer back to NORMAL, and proves a witness for each strong
old survivor or independent pre-store value. It introduces no E when inputs
and supplied value branches have Empty E.

| Selected local law | Theorem/control |
| --- | --- |
| Complete overwritten region killed | `at_or_below_is_killed`, `killed_region_is_written`, `definite_kill` |
| Only proper ancestor needs a residual restriction | `exclusion_has_proper_ancestor`, `root_STAR_needs_exclusion` |
| Unchanged component has no written location | `identity_has_no_written_location`, `root_EXACT_unchanged` |
| Full payload/origin and surviving layer retained | `preservation_full_payload`, `preservation_layer_monotone` |
| No field E introduced | `preservation_no_new_E`, `write_outputs_EEmpty` |
| Strong survivors and pre-store values covered | `strong_survivor_covered`, `pre_store_value_unchanged`, `strong_write_survival_coverage` |
| N/D kills, mixed components, writeback and self-assignment | `Controls.checks_pass` |

The [selected-write validation receipt](../docs/reports/bidirectional-run1-selective-preservation-proof-validation-20261016.md)
binds this new module and aggregate checks. Its location-coverage and layer
laws are local scalar proofs, not a packed Kotlin transfer simulation or a
proof of frontend write certainty, cleaner lowering or RHS generation. The
value list is explicitly supplied from pre-store work; its producer remains
an integration obligation. Whole-driver/ND/seed proofs retain their scope.

The new [Run1DefaultSummaryApplication.lean](lean/ApSpec/Run1DefaultSummaryApplication.lean)
checks `ordinary_root_STAR_layer_only`, `ordinary_identity_preserves_payload`,
`joined_layer_monotone` and `attachment_keeps_requests`. It reuses ordinary
summary geometry with layers attached separately; it is not a custom static
call operator. `SelectedControls.STAR_stays_STAR_at_DEMAND` pins the user's
no-information-loss case. Historical controls exhibit both old final-tail
normalization and the root-ANY suffix-loss counterexample. The
[new receipt](../docs/reports/bidirectional-ordinary-static-summary-proof-validation-20261016.md)
binds the final check and axiom audit. The model does not prove generic
Kotlin representation/normalizer refinement or actual storage/notification.

The earlier [Run1WeakFieldWrites.lean](lean/ApSpec/Run1WeakFieldWrites.lean)
model is historical. In particular its blanket old retention,
`exact_field_target_preserved_DEMAND`, demotion-to-ANY and default-call helper
do not describe the selected field-write policy. Least write-closure laws
remain reusable within their independent metadata scope.
Its scalar Fact wrapper models normal concrete ANY as must with a separate
anyEx channel; demand ANY is may. Normal STAR controls use abstract marks.
This is not a new legal NORMAL/may-ANY or concrete-STAR runtime form.

| Checked local law | Theorems |
| --- | --- |
| Historical weak-store coverage only | `weak_keep_is_may_cover`, `weakWrite_covers_strong_output`; not the new selective-kill law |
| Historical blanket old/value policy | `affected_keep_is_DEMAND`, `unaffected_keep_keeps_layer`, `fresh_value_keeps_its_own_layer`; complete overwritten components now die |
| Historical coarser demotion | `publication_preserves_full_geometry`, `demand_STAR_is_ANY`, `no_demand_STAR_publication`; superseded for selected layer-only demotion |
| Empty E and no hidden normal widening | `publication_has_empty_E`, `publication_is_may_cover`, `normal_publication_does_not_forget_E`, `every_producer_EEmpty`, `all_queued_producer_results_EEmpty` |
| Exact mark cleaner | `cleaner_never_keeps_selected_mark`; partial excluded residuals use the separate demand-cover branch |
| Least static-write closure | `closedWrite_has_contributor`, `closedWrite_least`, `recursive_write_inherited`, `default_NORMAL_iff_no_reachable_write` |
| Historical default helper | `default_instantiates_actual_geometry`, `default_layer_is_join`, `writing_default_demotes_actual`, `callee_delivery_is_pre_default`; no ordinary-storage/application refinement claim |
| No startup root / later actual query | `no_propagated_root_initial`, `backward_authorization_preserves_full_AP`, `backward_static_requirement_authorized`, `later_query_not_vetoed_by_default` |

The earlier receipt and its scope are in [proof-status.md](proof-status.md).
That aggregate build passed 127 jobs and its imported axiom audit checked
23,337 declarations with zero unexpected axioms. All executable certificates
passed. Only `propext` and `Quot.sound` are allowed.
The previous static proof receipt
validates its older scalar models; it does not alone validate this update.

Implementation must verify these bridges with meaningful controls:

| Case | Required result |
| --- | --- |
| x.* across x.f write | Old root geometry demand; Empty E |
| Exact x.g across x.f, f≠g | Disjoint fact keeps its layer |
| x.f.* or exact x.f; constant write | Old component killed in both layers; no value taint |
| x.f.g.*; x.f write | Descendant component killed, with no preservation output |
| Self-assignment / source value | Old destination killed; pre-store value branch can rederive the result with its own layer/origin |
| Field cleaner writeback | Old overwritten T killed; no cleaned T restored from preservation |
| AnyField GEN then partial cleaner | Only an E-requiring residual demotes; Empty E, known tail retained |
| Possible static alias / unknown callee | Conservative static-write bit; no read-request seed from target |
| Write-free SCC / reachable-write SCC | NORMAL default / DEMAND default respectively |
| Reached ZERO actor, no static initial | First counted actor work publishes the default through normal retained/live stores; ordinary handoff exports DEMAND rows |
| Metadata for a not-yet-created actor | Default edge available for normal initialization; no eager actor/global handoff scan |
| Normal C.f.* plus demand default | Exact same C.f.* payload at DEMAND; no ANY conversion, unrelated field or root |
| Demand C.f plus normal default | Remains demand |
| Root summary pattern versus root initial | Ordinary stored default retained; no root initial emitted in F1 |
| B2 g requirement through g=f | Ordinary demand/current-query admission; computed strong source f allowed |
| Killing/nonreturning helper | No stale old-field identity / no normal-return certificate |
| Mixed static/local/argument/return path | All-base query recursion and full support retained |

The local model and constructive arguments are not a certification of JVM
producer inventory, packed AP codec, actual scheduling, ND confirmation or
the complete iteration driver. These remain explicit runtime refinements;
no analyzer implementation changed in this specification update.
