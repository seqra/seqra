# Proofs for request-gated static handling

Status: constructive design proofs and checked local reference laws. Use with
[static-handling.md](static-handling.md). Runtime implementation is unchanged.
Latest measured iteration: **run-1, FORWARD, limit 1; OOM**; no completed
iteration or accepted forward report. No analyzer run supports this new design.

## 1. Reference domain and proof boundary

Fix one run limit L≥1, finite admitted accessors/marks, full method contexts,
resolved calls and rule occurrences. Class accessors do not count toward L.
Paths, tails, exclusions, layers and bounded premise sets then have a finite
alphabet. This is a semantic finiteness fact, not a small-memory estimate.
Prescan includes legal producer coordinates independently of read requests.
S differs from ZERO; an exact tainted empty S-root is impossible. Legal exact
class points are separate from field descendants; broad TAINT is permitted.

The reference uses the existing selected AP transfers, ordered rule actions,
NNF/signed eligibility, field cuts, source/sink seeds, full-premise guards and
modeled normal/exceptional exits. Its run-1 variant has weak ordinary static
stores and immediate static call bypass. Its later variant has strong stores
and balanced normal-return calls. These variants are intentionally different.
We do not claim equality between weak run-1 and the old strong engine.
Run-1 selected-mark static cleaners also use the new first-field spatial
partition; the old global same-base disjoint FLOW exclusion/request is not
part of this reference. Within an affected field the existing policy stays.

Assumptions needed at the implementation boundary are explicit:

1. Frontend reads/models/rules and the resolved call graph cover the selected
   transfer vocabulary, including effective value-consuming pass rules.
2. AP partition, cut, matching, bindings and read views refine their stated
   denotation; ordinary operations retain E and mark exclusions.
3. Sources, sink requirements, signed eligibility and end-action seeds retain
   their logical occurrence IDs and complete positive support.
4. Work is fair and counted through quiescence. Incomplete runs make no
   completeness claim. All ND responses retain the entire premise/support.

The proofs below derive the new read/request/query rules; they do not assume
that all required requests or callees already have summaries. Kotlin refinement
of these rules is a later implementation obligation. The checked local model
is [RequestGatedStatics.lean](lean/ApSpec/RequestGatedStatics.lean). It does not
model the full packed AP store, recursive engine or vulnerability driver.

## 2. Least separate prescan closures

Let `read(M,s)` be a local effective value read, `mark(M,s,T)` a local
effective static-mark interest and M→N a resolved call. Define:

```text
F0(M) = local reads(M)
F(n+1)(M) = F0(M) ∪ union(Fn(N), M→N)

B0(M) = local mark interests(M)
B(n+1)(M) = B0(M) ∪ union(Bn(N), M→N)
```

Each membership has a witness: a finite resolved call path to a local
contributor. Proof by induction on n constructs the path; induction on a
path reconstructs membership. A call cycle adds no contributor by itself.
For any other closed family containing local contributors, induction on that
same path shows it contains F and B. Thus these are the least closures.
SCC sharing implements this mathematical union without copies per actor.

`ContainsMark(s,T)` has one mark contributor and **no read contributor**.
Therefore a path consisting only of mark checks cannot introduce a member
of F. `g=f` contributes read f, never write target g. Alias endpoints, kills
and coordinate existence contribute no read witness. A real rule/model value
read still contributes even if its syntax is not an IR GETSTATIC.

Signed folding does not need a hidden abstract carrier: eligible call/exit
`NOT P` projects to true after contradiction folding; a source then has a
ZERO premise. `P AND NOT P` folds false first. `P AND NOT Q` retains positive
P. Entry eligibility uses its existing stricter guard. No step decides a
negative literal from an empty lazy store.

Checked: `containsMark_does_not_add_field`, `containsMark_adds_covered_mark`,
`read_adds_field_only`, `excludedTarget_does_not_add_field`,
`recursive_read_inherited`, `recursive_mark_inherited`. These reuse the finite
effective-form and call-closure oracle, not the runtime SCC implementation.

## 3. Two gates, shape classification and no invented mark

For a normal static slot s and supplied input a, define:

```text
A(s,a) = { abstract STAR(s) | a supplies s and s∈F }
C(s,a) = { project(a,s,T) | a actually carries T at s and T∈R(s) }
Emit(s,a) = A(s,a) ∪ C(s,a)
```

Membership in this union gives an executable witness of its gate. The
abstract branch proves F membership and supply, without requiring R. The
concrete branch supplies both actual T and R(s,T), without requiring F.
Conversely either pair constructs its initial. This proves all four gate
combinations, including concrete-only mark-check analysis. A request alone
is insufficient; FLOW cannot enter C. Non-T data cannot satisfy positive T.

Case analysis on the constructors in the new initial table gives nine
families. Each uses EXACT or STAR and Empty E, no mark exclusion. Normal
abstract static fields use exactly [C,f]. Removing the one special S-root
constructor from the former ten-family envelope gives the new count; it
does not remove legal actual broad TAINT or class components. The existing
projector maps nonexact concrete inputs to STAR and keeps exact inputs EXACT.
Hence no new ANY/ANY_TAINT or concrete empty-root initial is admitted.

Checked normal static-field gates: `abstract_start_iff`, `concrete_start_iff`,
`f1_no_root_initial`, `f1_no_ANY_initial`. The new `InitialGrammar.Shape`
has nine constructors. `InitialGrammar.classification_exact` returns an
executable shape witness; `exactly_old_grammar_minus_root` proves its AP
envelope is exactly the old envelope minus the removed root. `legal_empty_E`,
`legal_no_ANY`, `legal_no_must`, `started_layer`, `started_geometry` and
`nine_sample_families` check the stated grammar/start laws. Shape permission
still does not authorize an unrequested field emission. The old ten-family
predicate is unchanged and retains its historical scope.
The normal-field oracle takes a concrete semantic Leaf, not the runtime
FLOW/TAINT union. Its supplier witness and the design rule above prohibit
invented T; Kotlin dispatch must still refine that type boundary.

## 4. Request coverage and gated run-1 observations

Compare gated run-1 with an eager, root-free reference using the **same weak
stores and bypass**, but admitting every supplied abstract field. Compare
observations at actual value reads/effective positive mark checks, not all
unobserved eager intermediate facts or exact equality with the old core.

Take a finite producer-to-observation derivation. If an incoming field's
value first affects the observation through an ordinary read or a value
transfer, that first read contributes F in the current method or a resolved
callee. The path witness from §2 therefore admits the corresponding abstract
field input. Intermediate value copies preserve their original input origin.
If the observation is instead a direct positive mark check, it contributes
B(s,T); actual supplied T starts concrete-only analysis. A broad supplier
uses the same legal field/class projector, not a root seed.

If FLOW reaches a T check through a changed origin, it cannot prove T. The
existing caller input relation joins the check's mark need to that FLOW and
requests T on its original abstract slot. Example witness:

```text
read f → abstract f → copy g → check g/T
                              ↓ original-slot request f/T
actual supplied f/T → concrete f/T → concrete g/T → positive check
```

The copy itself reads f, so its abstract chain does not depend on an already
known f/T request. g has no field request merely because it is the target or
checked. Unchanged static needs are inherited in B and need no upstream
event. Nonstatic origins follow the same dynamic original-slot rule. TAINT
and concrete initials stop request propagation because their actual marks
are already fixed; concrete answer handling remains enabled separately.

R=B∪D and pending P use set union. Commit adds `P minus R`; after replacement
the new temporary is empty and admitted R is the prior R∪P. Duplicate offers,
excluded marks and barrier placement do not alter that union. For each actual
input/request pair, either the request exists when the input subscribes or a
later fresh request replays that retained caller input. Both construct the
same answer. Precomputed pairs are read through B, never copied/notified as
fresh D. Excluding T triggers interest immediately; later request matching
can ignore both field E and FLOW mark exclusions without losing that interest.

Induction on the finite observation derivation, with fair request replay,
then yields the same relevant positive observation in gated weak run-1.
True generation keeps its ZERO/actual origin. A skipped unobserved field
has no observation to preserve. Writes/aliases need no additional field
seed; the first actual consumer supplies the witness above. The proof uses
the stated frontend read coverage, not arbitrary alias endpoints as seeds.

The existing checked request union/arrival/projector laws remain usable with
their original local hypotheses. The full observation induction in this
section is a design proof; the new local Lean module does not certify its
Kotlin producer inventory, mixed-base replay or full NNF/ND bridge.

## 5. Weak run-1 covers strong positive data paths

For a destination field cylinder D, local strong store is
`(X minus D) ∪ value(X)` and weak store is `X ∪ value(X)`.
Every strong output is therefore a weak output, with the same value witness
or the same surviving input. Cleaners use their own unchanged function on
both sides; weakening a store does not undo an earlier local cleaner.
Existing exclusions are not removed. Sequential induction preserves positive
data-path inclusion under the monotone selected transfers.

For the new static cleaner partition, every supplied field component is
either disjoint or affected. The disjoint component is returned unchanged
and raises no cleaner-induced request; concrete cleaning is identity there.
The affected component uses the existing within-field cleaner/replay law.
Union of those two witnesses preserves positive concrete behavior without
requesting T on unrelated fields. The partition retains E and headers, so it
does not restore absent locations. For an excluded-target copy, first prove
source applicability, then form the target exclusion and request the original
source's T; output disjointness alone proves no source inapplicability.

A strong returning call either preserves a location or derives a value.
The first case is included in run-1's immediate static continuation. In the
second, an actual consumer/mark check admits the necessary callee chain by
§4, or the value originates from retained nonstatic/ZERO work. Actual callee
output adds that value. A nonreturning call may still have a run-1 bypass;
that is extra may behavior, not a normal-return proof. Local ordered prefix
cleaners execute before the bypass, so it cannot restore their removed input.

Induct on a finite balanced positive source-to-sink derivation to obtain
run-1 may coverage, relative to the frontend/seed/ND assumptions in §1 and
the observation argument in §4. This does not prove old finding-set parity,
cleaner exactness across bypassed callees, runtime speed or an accepted report.
In the local Lean model weak stores retain inputs, while selected explicit
cleaners remain strong (`weak_clean_is_strong`). `strong_step_in_weak`,
`strong_trace_in_weak` and `strong_body_in_weak` prove that model's inclusion.
The model has no runtime cleaner-producer implementation or CallPlan stages.

## 6. Why run-1 cannot be executable later evidence

Three constructive counterexamples establish necessary restrictions:

```text
GEN f/T; helper(skip; return); check f/T
    F1 may skip helper; no f→f summary exists, but B2 must cross it.

helper: f=untainted; return
    F1 weak f/T→f/T is invalid under later strong semantics.

helper: never returns
    F1 bypass is not a valid normal-return identity.
```

In the second example append `x=f; return x`. The weak stale value now has
a nonstatic endpoint. Filtering root or static-output records cannot fix it.
Therefore no F1 publication is an executable native/opposite edge. Projection
to guidance retains only a scheduling/coverage hint, not the claim it encodes.
All later executable records must have a strong witness. This establishes
phase isolation by construction, including packed/nonstatic publications.
Semantic flavor belongs in cache/record selection, not in logical source,
sink or action occurrence IDs, which must stay stable across handoffs.

NORMAL singleton hints must be retained: `id(x){return x}` may have no
DEMAND summary. Dropping its only NORMAL record and keeping demandEdges()
alone would remove its guide. Even a retained guide is not enough for all
missing static identities; current-query admission in §8 is required.

The same restriction applies to report seeds and stop checks. Weak F1 can
reach a NORMAL sink match; treating that label as final or seeding B2 from
DEMAND matches only would bypass strong checking. Retain each F1 candidate's
sink/occurrence/support regardless of its layer and seed B2 from all such
candidates. A zero DEMAND-edge count cannot suppress this transition. This
constructively supplies the real path's B2 starting requirement used in §9,
rather than assuming the old layer-only report filter preserves it.
Use unconfirmed report candidacy, which can reuse the current DEMAND report
entry path. Preserve the raw NORMAL/DEMAND fact layers; changing report
status does not create or reclassify executable transfer edges.

Checked local controls: `whole_F1_quarantine`, `weak_cache_not_strong`,
`missing_F1_helper_still_answers`, `killing_helper_has_no_identity`,
`nonreturning_helper_has_no_identity`. The semantic boundary is stronger
than just removing the special root identity. Old record/publisher code
must change; the checked model is not evidence that it already does so.

## 7. Strong S→S relation, exclusions and return

For scalar transfers let an atom be a legal location/mark/layer with its
input origin. ZERO is a separate source point. Define R_M(u,v) by a finite
balanced strong derivation from method entry to its modeled normal exit.
Construct the relation from identity, strong store/copy/kill, selected clean,
filter, ZERO generation, ordered composition, branch union and returned calls.

For a strong overwrite of f, membership is exactly either an unaffected
input outside D_f or an applicable value transfer into D_f. Thus old f does
not survive a constant write, while disjoint g does. For `g=f`, source f
produces g; old g has no survival edge unless an alternative returning path
preserves it. Self-assignment reads the pre-store input, which supplies its
own replacement. A selected cleaner keeps precisely atoms outside its
scope/mark test. Generation has ZERO origin, not an old static input origin.

At a call compose actual bind-in, R_callee and bind-back/post-actions. The
callee factor includes a finite normal-return witness. A pure helper derives
identity on its actual queried f even with F(helper)=empty. A killing helper
does not; a nonreturning helper has no such witness. A recursion cycle alone
has no finite proof constructor, so it cannot manufacture a return.

This witness excludes artificial virtual-exit completion edges used for the
old S11/I11 backward reachability condition. Totalized CFG reachability is
not a return proof. The adapter must distinguish actual modeled returns
from those completion edges; otherwise the no-return theorem applies only
to the small reference model and cannot be transferred to the frontend.
Local sink/source work before a nonreturning exit needs no method-return
witness; the restriction concerns continuation back to a caller.

Ordinary E is part of atom membership. For killing `[C,f]`, a surviving
static atom is in another class, in sibling field g≠f, or a legal exact class
point outside the killed field cylinder. These cases construct the full
residual. Keeping only `S.* except C` loses siblings. Ignoring input E adds
atoms not supplied. Request matching's exclusion erasure proves neither
ordinary preservation nor summary applicability. Partition/aggregate only
by denotational union with headers and layers retained; broader DEMAND data
does not prove an exact NORMAL class point.

Induction on each finite answer derivation proves soundness in R_M: primitives
use the equations above, sequence constructs intermediate witnesses, choice
selects its path, and a call carries the callee return witness. The converse
induction on a balanced R_M derivation, coupled with query closure in §8,
proves completeness. These are proofs in the selected abstract transfer
semantics, not an exact semantics for all concrete JVM heap behavior.

For ND rules use a hyperderivation `R_M(P,v)` over the entire premise set P.
Its constructor requires all literals under the same rule/support/context
and actual caller. The same two inductions apply constructor by constructor,
retaining all children at every call. Replacing this with independent scalar
member answers is invalid. The new Lean binary model does not check this
full ND lift or the existing confirmation implementation.

Checked scalar laws include `copy_exact`, `kill_exact`, `zero_generation`,
`no_generation_per_input`, `ordinary_spatial_E_retained`,
`output_iff_actual_return`, `abort_no_summary`, `identity_only_on_return`.
Class-point controls operate on separate atom constructors. Packed AP
residual encoding and reader guards remain implementation refinements.

## 8. Current-query discovery and recursive completeness

Use the following positive inference rules, independent of old F1 summary
availability or field ownership:

1. An actual current input/output requirement registers an exact call
   subscription and admits its strong query.
2. Ordinary local strong transfers add their derived work.
3. Each actual encountered nested binding admits its callee query by rule 1,
   including nonstatic intermediates on an S→S route.
4. A reached exit publishes its proven answer to all matching subscriptions.
   The current query authorizes both start and response, even without an old
   demand pattern. Complete-premise matching and ordinary AP guards remain.
5. New query/old answer and old query/new answer both schedule fresh counted
   delivery. Pending work creates no answer by itself.

Let U be the finite descriptor alphabet from §1, including queries, local
facts, responses, subscriptions and support combinations. Start at actual
seed/query descriptors. Iterate the positive rules by fresh insertion.
This constructs an increasing sequence in the finite powerset of U and
therefore stabilizes after at most |U| successful distinct insertions.
Duplicate attempts and wall time do not have that bound. The union is closed;
induction on the rules shows it is contained in every closed superset of the
seeds, so it is the least fixed point. No excluded-middle choice is needed:
enumeration, membership and every witness are finite and executable.

For completeness take any finite balanced strong derivation for an actual
query. Induct on its tree height. Its local children are scheduled by rule 2;
each call child is admitted by rule 3 regardless of the prior guide. Induction
produces the child response. Rule 4 resumes its real caller; rule 5 handles
either arrival order. Fair work eventually publishes the answer. This proof
does not assume a complete F1 summary store, an acyclic graph, F(callee)
membership or a fictitious default identity. A recursive finite returning
derivation is covered; unproductive recursion adds no response.

Soundness follows simultaneously by induction on successful insertions:
query registration is only a request, local facts have strong transfer
witnesses, and every returned answer has its exit and premise witnesses.
Guidance/records may accelerate the same rules only when they retain these
requirements. Hence changing/removing old guidance cannot invent an edge;
it also cannot suppress an actual query or response.

Checked: `actual_query_admitted`, `query_admission_independent_of_F1`,
`query_registration_idempotent`, `unknown_not_empty`, `unknown_not_identity`,
`completed_query_has_no_fake_edges`. These are local admission/result laws.
The generic `BalancedCalls` model works over arbitrary states, including
nonstatic intermediates. `eval_sound`, `eval_complete` and
`balanced_query_exact` prove that executable fuel approximants enumerate
exactly finite balanced derivations. `eval_mono` and `least_closed_relation`
prove monotonicity and leastness. `unproductive_recursion_has_no_return` and
`mixed_static_local_nested_call` give executable recursion/mixed-state
controls. This checks finite balanced-call discovery without assuming a
complete summary store. Fair asynchronous processing, full AP publishers
and ND support are still the engine refinement of that construction.

## 9. Backward run-2 and later forward runs

Define the backward strong relation by converse, not by reversing weak F1
records. For actual output requirement Q:

```text
Back_M(Q) = { u | exists v, v∈Q and R_M(u,v) }
```

Forward-to-backward construction reverses each finite derivation's primitive
relations and composition order, retaining the very same call-return witness,
marks, ordinary exclusions, context and support. It constructs a preimage
witness u. Backward-to-forward construction reverses those steps again.
Induction on the two finite derivations proves both inclusions. For ND the
preimage is the whole supported premise, not one arbitrary literal.

Consequently run-2 demand for g/T through `g=f` produces f/T; demand for old
f/T after an untainted overwrite has no old-f preimage; demand satisfied by
generation reaches its ZERO/source obligation; nonreturn has no normal
preimage. Returning skip derives the missing f/T preimage from its actual
query, not a saved F1 identity. Later forward runs use R_M itself.

Native/opposite packed read views must refine this denotation with their
existing may/must/layer rules. Tuple swapping alone is not the proof.
Checked: `converse_exact`, `opposite_has_actual_return`,
`forward_answers_exact`, `backward_answers_exact`. Finite candidate-list
answer exactness is relative to the enumerated candidates; coverage of the
runtime packed candidate search remains a separate refinement.

For a real selected source-to-sink path, §4–5 yields run-1 may evidence.
With the existing occurrence/seed contract, its actual sink requirement
starts B2. Converse completeness follows the real path across calls even
when F1 omitted identities and produces the source hit. The ordinary B2→F3
handoff keeps that source occurrence and demanded geometry. Forward query
completeness reconstructs the strong path in F3. Repeating this argument
preserves such paths in later refinements within the selected model and
limit abstraction. This is conditional on the §1 frontend/seed/ND bridges;
it is not a checked whole-engine no-FN theorem or an assertion that all
run-1 false positives disappear on run-2.

## 10. Validation and integration controls

The new local Lean module passed the aggregate build (126 jobs) and axiom
audit (22,798 declarations, zero unexpected axioms), including all executable
certificates. Historical F78 and Statics
receipts keep their original hypotheses and do not validate this design.
The current receipt and its exact scope are in [proof-status.md](proof-status.md).

Runtime work must test the bridges, not just repeat the local equations:

| Case | Required result |
| --- | --- |
| ContainsMark-only source and sink | Mark interest only, no abstract field initial; supplied actual T still satisfies it |
| f→g, then check g/T | Read f, mark g/T, dynamic original f/T replay; no target-g field seed |
| Signed NOT P / P AND NOT P / entry | Existing projection/contradiction/entry eligibility; no lazy absence test |
| Pure helper with empty F and no F1 summary | B2 actual query starts and receives strong identity |
| Strong overwrite / nonreturning helper | No old-field identity / no normal-return response |
| f→local→id(arg)→g | Actual all-base nested query closure in both directions |
| Broad supplied fact with E; same-class siblings | Ordinary exclusions retained, killed f removed, sibling g retained |
| Exact class point with no fields | Separate legal component retained without a field request |
| Late query/answer, recursive finite return | Both arrival orders; finite return found, cycle alone creates no edge |
| Weak F1 output with nonstatic endpoint | Guidance retained, native/opposite executable record rejected |
| All-NORMAL F1 sink matches, no DEMAND edges | Candidate retained and B2 seeded; old layer-only stop tests do not skip strong refinement |
| ND and generated mark | Entire premise matched; ZERO/source origin and occurrence retained |

Fixed OWASP/project regressions and source-bound profiling follow implementation.
Every report must state reached, completed and accepted run iteration. None
of these runtime validations has been run for this documentation change.
