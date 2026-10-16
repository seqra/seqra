# Proofs for run-1 weak field writes

Status: constructive design proofs and reference-model laws for
[run1-field-writes.md](run1-field-writes.md). Analyzer implementation is
unchanged. The new contract supersedes the earlier unannotated weak-store
and unconditional NORMAL static-continuation rules.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. This dossier records no new analyzer run.

## 1. Model and correctness statements

Fix a finite selected AP domain, a positive run limit, legal accessors/marks,
full method contexts, resolved calls and stable rule/action occurrences.
Run-1 has limit exactly 1, its nine legal initial families, empty prior
records/handoff, and no abstract S-root initial. Exclusions in compiled
strong recipes are distinct from exclusions in admitted run-1 facts.

The strong reference uses the existing logical field-write, cleaner, binding,
source and full-premise semantics. Run-1 uses weak field writes with DEMAND
old preservation, exclusion-free partial-result covers and tagged static
default continuations. Later runs use the strong reference. Compare these
models through **positive may coverage**, not equality of their findings.

Three properties must not be confused:

| Property | Meaning |
| --- | --- |
| Strong semantic validity | A full supported derivation proves a transfer/return in the selected strong model |
| Run-1 coverage soundness | Every relevant strong positive flow has a covering run-1 candidate derivation |
| Default path precision | Instantiating S identity retains the actual field/class path and origin; it does not introduce another static coordinate |

A DEMAND old-field survivor can be spurious under the strong reference. That
is the reason to request refinement. A NORMAL default certifies metadata's
no-static-write classification, not return or whole-engine confirmation.
All F1 artifacts remain guidance-only for later strong execution.

Required frontend/engine bridges retain explicit scope: complete effective
producer/effect inventories; correct AP overlap/normalization/packing;
ordered local actions and bindings; signed condition and seed coverage;
whole ND premises/support; fair counted processing through quiescence.
No theorem assumes the existence of all required old F1 summaries.

## 2. Weak stores cover strong stores

For a concrete represented state X, destination cylinder D_w and pre-store
value transfer V_w, the reference write is:

```text
Strong_w(X) = (X minus D_w) union V_w(X)
Weak_w(X)   = X union V_w(X)
```

If z is in Strong_w(X), its witness is either an old z in X outside D_w or
a pre-store value-transfer witness. The first constructs z in Weak_w(X)'s
old branch; the second constructs it in the value branch. This proves
inclusion constructively without assuming that overwritten old values
actually survive. Reading V from the pre-store X also covers self-assignment.

Lift to an annotated current component a. If a is disjoint from D_w, its
entire old contribution is valid under this write and keeps its layer. If
a overlaps D_w, retain it at `oldLayer join DEMAND`. This covers every actual
surviving old location, while refraining from asserting its entire old
geometry is a strong NORMAL survivor. A broad component can contain both
survivors and overwritten locations; demoting it whole avoids an E-bearing
residual. No finite-universe field enumeration is needed.

Relevance uses the current AP: a fact originally from y may now be at x.f,
so testing only the premise y would miss the overwrite. Exact ancestor
points are disjoint from a descendant field write; a STAR ancestor has
descendants that may overlap and therefore demotes. Possible aliases use
the same conservative overlap contract. A false-negative overlap test is
not covered by this proof; the producer/alias bridge must exclude it.

Value edges keep their ordinary pre-store input/support and resulting layer.
They can coexist with DEMAND old preservation. Demoting only the old branch
does not suppress new taint. A constant untainted RHS has an empty taint
value transfer. A whole-base local assignment uses the separate whole-base
strong replacement rule, not this field-cylinder rule.
Field RemoveAll lowered as an overwrite and cleaner writeback use the same
weak-store proof. A direct mark-clean stage is separate: it can remove T
from a temporary exactly, while the subsequent field write still preserves
the old destination only as demand. A whole-base kill is also separate.

## 3. Layer monotonicity and canonical demotion

Layers are Booleans with false=N and true=D. OR is associative, commutative,
idempotent and has N as identity. Case analysis proves:

```text
old ≤ old OR overwrite
input ≤ input OR default
D OR anything = D
```

Thus a read-only NORMAL default cannot turn an existing demand edge normal.
A later value edge with the same payload has a separate derivation; it is
not a promotion of the retained old edge. Storage/joins must preserve the
demand obligation even when an independent normal alternative also exists.

W2/W8 normalization is part of the map: demand STAR becomes may ANY, and
demand must ANY_TAINT becomes may ANY. Demand EXACT remains EXACT. This
weakens correlation/guarantees and retains the actual path, mark exclusions,
base and originating premise. It never silently leaves a scalar STAR or
ANY_TAINT conclusion in the DEMAND layer. At the semantic component level
the location path remains the original path; syntactic tail equality is
not the claimed precision result.

The map is idempotent: once demand/canonical, another demotion changes
nothing. It changes no source/rule occurrence ID and invents no concrete
mark. Concrete suppliers and original-slot requests retain their existing
separate proof obligations.

## 4. Empty-E covers and the must counterexample

For a legal result r with nonempty E, let `Cover1(r)` retain its actual
base/path/mark/origin, move it to DEMAND, weaken STAR/must as above and use
Empty E. If E is already empty, keep the ordinary canonical result.
Every location admitted by r's exclusion is still admitted by the cover;
the latter may admit additional possible locations. Those additions carry
no NORMAL correlation or must guarantee. This proves positive may coverage.

For must results, compare potential taint and guarantees separately. All
actually retained marked locations remain possible in the cover. No claim
that *every* location is marked remains. This is essential, not a cosmetic
layer change.

Executable counterexample domain: locations f and g, T true only at g.
`must / {f}` is satisfied. Replacing it with `must / Empty` at NORMAL would
assert T at f and is false. A DEMAND may cover does not make that assertion.
Such a reference residual is reachable in run-1: an AnyField source emits
normal ANY_TAINT, then a partial cleaner removes the f part. No forbidden
must initial is needed to reach this case.

For direct selected-mark cleaners, exact exclusion-free kills/keeps remain
their usual results. Field overwrites/writebacks still use §2, not that kill
exception. Only an E-requiring residual needs this cover. Static cleaners
first partition their actual first-field scope; disjoint g is unchanged
and cannot request T merely because f shares base S. For an excluded-mark
copy g→f, applicability uses g, the output exclusion belongs to f and the
interest routes to the original g origin. The field-E cover must not clear
mx, erase that request, or discard the copy by comparing source to target.

## 5. Global run-1 Empty-E invariant

Define the closed run-1 producer system with:

1. Empty-E starts/initials and true ZERO/source outputs.
2. Weak preservation/value transfers using the phase recipe.
3. Exact E-free cleaner/filter results or Cover1 for E-requiring residuals.
4. Every other local/model/alias/binding/summary/cut output using the same
   run-1 normal form before entering any queue, index or store.
5. Packing, union and delivery that preserve payloads and do not invent E.

Induct on the finite derivation of an admitted fact. Starts use (1). A
preservation branch keeps Empty E and adds no destination exclusion. A value
branch uses its source witness and (2)/(4). A partial-clean/model result
uses (3)/(4), whose result explicitly has Empty E and canonical layer. A
default applies only to an actual admitted fact and uses layer join, so it
also preserves Empty E. Packing/replay uses (5). Hence every intermediate
fact has Empty E, not just the final method summary.

Every run-1 premise originates in an Empty-E initial and every summary
conclusion in this producer system. Therefore both payload sides have no
field exclusion. The default descriptor itself has Empty-E syntax and is
not a fact producer. No old record can inject E because F1 has no previous
run records. Any explicit external/model E-bearing input must be covered
before admission, rather than assumed absent by the induction.

The proof does not claim that toggling the current `weak` flag closes the
producer system. Current partial cleaners and strong writeback seams are
concrete counterexamples to that claim. Implementation must route *all*
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

## 7. Default templates, locality and availability

Define the total frozen function `Default(M)=IdentityS at layer W(M)`.
Since the prescan plan covers each resolved method, lookup constructs this
descriptor even when the executed analyzer/initial/summary sets are empty.
Actor existence is not a hypothesis. Shared lookup causes no field expansion,
initial insertion or startup propagation event.

For actual a with base S, instantiate the identity at a, then join layers
and canonicalize. Before normal-form weakening this is pointwise identity
on the supplied coordinates. After normalization its base/path/mark/origin
are unchanged; the only tail weakening is required by demand. Therefore
a field C.f never becomes unrelated C.g or a fabricated root input.
For W=false the input layer is retained. For W=true the result is demand.
For an already demand input both cases remain demand. These are exhaustive
Boolean cases, including the user's normal C.f plus demand-default example.

The write bit can be true due to unrelated g, so layer precision is
conservative. Geometry remains local to the supplied component. Applying a
generic root may relation first would not be an equivalent implementation:
its cross-location pairs can lose that input-field correlation.

At calls replace the old independent static bypass with this one lane;
otherwise the old NORMAL bypass could coexist with the demoted default and
hide the intended refinement debt. Prefix actions execute first; exact local
kills do not reappear from a pre-prefix saved fact. Post-call stages keep
their order and canonicalization. Exact caller premise/context and the
identity classification remain attached; no actual normal return is asserted.
Requested callee data is selected from the pre-default input, so the default
does not demote independent value computations. Their own inputs/transfers,
including any real overlapping write, determine their layer.

Ordinary root-execution artifacts stay forbidden. Tagged root-shaped default
descriptors are now allowed and must survive guide lookup. The former blanket
root-identity export prohibition cannot be used to delete them.

## 8. Demand handoff and strong reconstruction

Handoff contains two sources of guidance: executed publications, including
the new DEMAND write-survival edges, and the total frozen default view.
The latter is independent of `result.analyzers`. Both default layers remain
available as coarse guidance. Dropping the NORMAL descriptors would lose a
write-free helper's only inherited guide when F1 never analyzed its statics.

For actual static output requirement q in B2, intersect the symbolic default
scope with q. Because q already has base S, the identity-scope intersection
is q's own geometry. Normalize a query initial from that **actual requirement**,
not from the root wildcard guide. A general old `flowForm(rootGuide)` is not
this construction and can create unwanted root work. No guide invents T;
actual concrete demands/suppliers retain their real mark.

The current query authorizes start and computed response. The template is
not an executable identity or reversed F1 certificate. Its broad response
scope must permit a strong copy from f to answer a g requirement, and permit
source-hit/ZERO and mixed nonstatic responses under their real support.
Ordinary strong query matching/return guards then validate those answers.
All-base current-query fallback remains for nested local/argument/return
paths, including complete ND premises; static descriptors alone cannot
prove that mixed coverage.

For strong-query soundness, induct on an answer derivation: only real strong
primitive, supported rule, composition and modeled-return constructors can
publish executable answers. A default lookup merely registers/advises work,
so it adds no counterexample edge. Strong overwrite removes the old field;
copy supplies its source; generation supplies ZERO; absent return yields
no normal-return summary. Reuse the checked strong-body/converse laws from
the prior module within their stated scalar scope.

For completeness, take a finite balanced strong derivation required by an
actual B2 query. Its static call is admitted by total default lookup plus
actual specialization, even with no F1 actor/input. Its mixed calls use the
all-base query rule. Induct on the finite callee derivation; its actual
responses are not rejected by old F1 ownership or missing guide rows. Fair
notification resumes the caller. Finite recursive returns are included;
an unsupported cycle cannot manufacture an answer. The prior generic-state
balanced-call proof checks this finite derivation construction. It does
not prove the actual packed asynchronous runner.

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
record has a later strong witness: no tagged default, weak survivor or
coarse guide is persisted/reversed as executable evidence, even if NORMAL.
This also excludes F1 contamination through nonstatic endpoints.

## 10. Checked scope and required controls

The new local model is [Run1WeakFieldWrites.lean](lean/ApSpec/Run1WeakFieldWrites.lean).
Its scalar Fact wrapper models normal concrete ANY as must with a separate
anyEx channel; demand ANY is may. Normal STAR controls use abstract marks.
This is not a new legal NORMAL/may-ANY or concrete-STAR runtime form.

| Checked local law | Theorems |
| --- | --- |
| Positive weak-store coverage | `weak_keep_is_may_cover`, `weakWrite_covers_strong_output` |
| Old/value layer separation | `affected_keep_is_DEMAND`, `unaffected_keep_keeps_layer`, `fresh_value_keeps_its_own_layer` |
| Geometry and canonical demand | `publication_preserves_full_geometry`, `demand_STAR_is_ANY`, `no_demand_STAR_publication` |
| Empty E and no hidden normal widening | `publication_has_empty_E`, `publication_is_may_cover`, `normal_publication_does_not_forget_E`, `every_producer_EEmpty`, `all_queued_producer_results_EEmpty` |
| Exact mark cleaner | `cleaner_never_keeps_selected_mark`; partial excluded residuals use the separate demand-cover branch |
| Least static-write closure | `closedWrite_has_contributor`, `closedWrite_least`, `recursive_write_inherited`, `default_NORMAL_iff_no_reachable_write` |
| Actual-input default | `default_instantiates_actual_geometry`, `default_layer_is_join`, `writing_default_demotes_actual`, `callee_delivery_is_pre_default` |
| No startup root / later actual query | `no_propagated_root_initial`, `backward_authorization_preserves_full_AP`, `backward_static_requirement_authorized`, `later_query_not_vetoed_by_default` |

The fresh receipt and validation scope are in [proof-status.md](proof-status.md).
The aggregate build passed 127 jobs and the imported axiom audit checked
23,337 declarations with zero unexpected axioms. All executable certificates
passed. Only `propext` and `Quot.sound` are allowed.
The previous static proof receipt
validates its older scalar models; it does not alone validate this update.

Implementation must verify these bridges with meaningful controls:

| Case | Required result |
| --- | --- |
| x.* across x.f write | Old root geometry demand; Empty E |
| Exact x.g across x.f, f≠g | Disjoint fact keeps its layer |
| Exact x.f; constant write | Old fact retained only as demand, no value taint |
| Self-assignment / source value | Pre-store value branch retained separately with its own layer/origin |
| AnyField GEN then partial cleaner | No NORMAL must with cleared E; demand may cover, Empty E |
| Possible static alias / unknown callee | Conservative static-write bit; no read-request seed from target |
| Write-free SCC / reachable-write SCC | NORMAL default / DEMAND default respectively |
| No F1 actor or static initial | Default lookup still exists, without actor or root propagation |
| Normal C.f plus demand default | Actual C.f becomes demand; no unrelated field or root |
| Demand C.f plus normal default | Remains demand |
| Partial root blacklist / ordinary root execution | Tagged defaults retained; actual synthetic root execution rejected |
| B2 actual g requirement through g=f | Query initial from actual g; computed strong source f allowed |
| Killing/nonreturning helper | No stale old-field identity / no normal-return certificate |
| Mixed static/local/argument/return path | All-base query recursion and full support retained |

The local model and constructive arguments are not a certification of JVM
producer inventory, packed AP codec, actual scheduling, ND confirmation or
the complete iteration driver. These remain explicit runtime refinements;
no analyzer implementation changed in this specification update.
