# Request-gated static handling

Status: selected design contract; **not implemented**. This contract takes
precedence over the static parts of [F78](run1-requests.md),
[AP](ap.md), [interpreter](interpreter.md) and [core](analyzer-core.md) where
they conflict. Other AP, rule, request and context contracts still apply.
The [proposal](../docs/design/bidirectional-request-gated-static-handling-20261016.md)
describes the changes. [Static proofs](static-handling-proofs.md) gives the
proofs, their assumptions and the checked Lean scope.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. This specification adds no measurement.

## 1. Domain and phase boundary

S is the static base, distinct from ZERO. A normal static field slot is
`[C,f]`: the class accessor C does not count against the field limit. Run-1
starts with limit exactly 1. Later limits are positive and nondecreasing.
`C.f.* (*)` denotes `(S,[C,f],STAR,Empty,STAR)`.

Keep these three objects distinct:

| Object | Meaning |
| --- | --- |
| Run-1 field request F(M) | A field needs abstract input analysis because code reads its value |
| Run-1 mark request R(K,slot) | A set of concrete marks needed on an original abstract input slot |
| Later query Q | A current forward input or backward output requirement, with its full AP, direction, layer, method context and response relation |

K is the full MethodKey. M is the CommonMethod whose fixed plan K borrows.
Later queries are ordinary restricted-analysis work, not run-1 mark requests.
No new mark-request store exists on run-2 or later.

Ordinary transfers, cleaner operations and summary application use full AP
geometry, field exclusions E, mark exclusions and complete premises. Only
run-1 request lookup ignores E and FLOW mark exclusions. A missing request,
lazy fact or unfinished callee query is never evidence for a negative rule
literal, a kill, normal return or absence of a summary.

## 2. Prescan inventories

Prescan resolves all calls before closure. It builds separate inventories:

| Inventory | Contributors | Use |
| --- | --- | --- |
| Legal coordinate universe | All legal producers and consumers, including writes and models | Validate supplied data and generated coordinates |
| Local field-read needs | Actual static value reads and effective non-mark checks that read a value | Abstract field-input gate |
| Local static-mark interests | Effective positive mark checks in source and sink conditions, selected cleaners and excluded-mark creators | Concrete-answer gate and request routing |
| Direct class-component needs | Legal class-point checks/actions | Separate class component path |

`ContainsMark(C.f,T)` contributes **only (C.f,T) to static-mark interests**.
It does not contribute C.f to field-read needs. Apply the same distinction to
other mark predicates and their genuine field/class/root scopes. Collect
effective conditions after signed/static folding. Retain the existing NNF
and residual-negative policy; do not introduce a test for absence of lazy
state. A condition simplified to false contributes nothing. `P AND NOT P`
may be simplified to false.

In the current eligible call/exit source/sink policy, signed contradictions
fold first, then residual negative literals project to true. Thus a lone
`NOT ContainsMark(f,T)` can become unconditional ZERO generation; it does
not need an abstract f input. `P AND NOT Q` retains the effective positive P
need. Entry conditions keep their stricter signed/static eligibility rule.
This contract does not add dynamic absence evaluation.

```text
x = C.f                   local field need: f
C.g = C.f                 local field need: f; none from write target g
C.f = x                   no field need from f
ContainsMark(C.f,T)        mark interest f/T; no field need
alias mentioning C.f      no field need
clean C.f/T               mark interest f/T; no field need from target
```

Real value reads inside a rule remain field reads. A mark predicate alone is
not such a read. A mark change from T to U cannot change field-read geometry.
Keep broad scopes symbolic. Never promote a nonempty mark set to all fields.

For each resolved call edge M→N, close field-read needs and unchanged-origin
static-mark interests by union along that edge. For recursive components use
the least union closure. The field closure is F(M). The static-mark closure
is the shared immutable base B(M) of the request relation. A changed-origin
need remains dynamic: a g/T check reached after `g=f` can request f/T.

For an excluded-mark value transfer, collect interest at its actual source
geometry, not blindly at its output target. `g -> f (* except T)` reads g
and needs source mark T; original-slot routing can then take that interest
farther upstream. A direct cleaner has the same source/output scope. A
target coordinate alone never creates a field-read request.

Do not add field needs for alias endpoints, write targets, existence in the
coordinate universe or generic `keep(S)` edges. Legal coordinate collection
must still include a constant-write target even if lowering emits keep only.

## 3. Run-1 initials and request ownership

The table is exhaustive for selected run-1 method-entry initials. Each
alternative tail denotes a separate family. All E are Empty; no initial has
mark exclusions. Legal shape alone does not authorize emission.

| Family | Base/path | Tail | Mark | Admission |
| --- | --- | --- | --- | --- |
| ZERO | zero / [] | EXACT | canonical zero mark | Root start or Reach |
| Nonstatic abstract | b / [] | STAR | STAR | Supplied nonstatic input |
| Static field abstract | S / [C,f] | STAR | STAR | Supplied field geometry and F(M) contains C.f |
| Nonstatic concrete | b / [] | EXACT or STAR | T | Actual matching TAINT supplier and R(K,b) contains T |
| Static field concrete | S / [C,f] | EXACT or STAR | T | Actual matching TAINT supplier and R(K,C.f) contains T |
| Direct concrete class component | S / [C] | EXACT or STAR | T | Actual direct/broad class supplier and separate class projection |

There are nine families when alternative tails are counted separately. There
is no special abstract S-root initial, abstract class initial, concrete empty
S-root initial, ANY/ANY_TAINT initial or path deeper than the normal static
slot. Nonexact concrete suppliers project to STAR, not ANY. A concrete STAR
initial still starts as the existing DEMAND/ANY fact; this is not an ANY
initial. Direct class components retain their existing separate supplier and
layer policy. Actual broad TAINT data is legal; it projects to needed fields
or class components without creating an abstract S-root.

The two normal-field gates are independent:

```kotlin
validateAgainstClosedDomain(input)
for (slot in suppliedStaticSlots(input)) {
    if (fieldReads.requests(slot)) emit(abstractStar(slot))
    for (mark in actualMarks(input, slot).intersect(requests.marks(slot))) {
        emit(projectExactOrStar(input, slot, mark))
    }
}
```

No field request plus a mark request can therefore emit a concrete-only
initial. A request without actual matching TAINT emits no concrete mark.
FLOW does not invent T. Field and mark requests do not make source facts.

R=B∪D lives in SummaryStorage. B is borrowed, not copied into D or P. Dynamic
requests accumulate in the existing per-method temporary RequestStorage P.
At the method-queue-empty barrier:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Commit merges mark sets, exposes only fresh dynamic additions and notifies
current exact caller-input subscriptions. New subscriptions borrow all of R,
including B. A precomputed unchanged static route is satisfied by inherited
B; it creates no startup request event, duplicate D row or upstream offer.
Changed origins use the normal dynamic route on the original abstract input.
Concrete initials/current TAINT skip upstream request propagation. Excluding
T from a normal abstract origin immediately requests T on that origin.
Both request-first and input-first arrivals must produce the same union.

## 4. Run-1 stores and calls

An ordinary static store is weak: retain old destination facts and add the
written value facts. Do not introduce a store-induced destination exclusion.
Existing E/mark exclusions remain. Explicit Clean, RemoveAll and logical
cleaner/rewriter writeback retain their specified semantics. Weak alias value
updates stay weak but do not seed F.

For a run-1 selected-mark static cleaner, partition FLOW by first static
field cylinder **before** the selected-mark operation. An f/T cleaner leaves
disjoint g unchanged and creates no g/T interest merely because both use S.
Within the affected f cylinder keep the existing exact/below transfer,
concrete replay and original-slot request rules. Genuine broad cleaners keep
their real scope. Keep unlike E/mark headers separate in packed input.
This spatial rule replaces the old same-base disjoint static FLOW behavior;
it does not weaken Clean/RemoveAll into ordinary weak stores.

Distinguish source applicability, output scope and original input origin.
For `g -> f (* except T)`, g is the source: transfer g to f, attach the mark
exclusion to affected output f and request T on the original g origin. The
fact that source g is disjoint from output f cannot discard this transfer.
Apply this distinction to generated rewriters and every excluded-mark creator.

At the CALLEES stage, every arriving static fact has an immediate local
continuation with `identityCandidate=true`. It does not wait for any target
to return, including a nonreturning target. It does not bypass local prefix
rules or the later returned-rule/bind-back/alias/cut stages. Callee outputs
add data and do not retract this continuation. Nonstatic inputs keep their
existing run-1 call protocol.

Deliver to a target only supplied fields in F(target) or a possible scoped
static-mark interest. Retain direct class-component delivery separately.
Preserve exact caller premises, contexts, layer and headers. Selection can
return several facts; explicit tips with E and broad projections with Empty
E must not be merged by deleting their differences.

There is no special abstract S-root propagation, root-driven field expansion,
root-origin field-summary route, return watcher or pending static frame.
Generic compiled `keep(S)` operators remain legal. Their pattern endpoints
are not special propagated root initials or method-summary instances.

Run-1 bypass and weak stores are intentional may behavior. They are not
proof of preservation under later strong stores or of normal return.

## 5. Safe transition from run-1 to run-2

Run-1 results are **guidance only** for later analysis. Do not persist or read
any run-1 publication as an executable native/opposite transfer record. This
restriction covers all endpoint bases: a weak static value can become a
nonstatic return, argument or rule result. Retaining only such nonstatic
records would still be unsafe.

Before building either native records or HandOff, reject any special abstract
S-root identity summary, including packed components. Retain permitted run-1
publications as coarse entry/exit guidance and candidate/diagnostic evidence.
Guidance must not certify an edge, a whole premise, a source hit, normal
return or a confirmed vulnerability in a later strong run.

Include NORMAL singleton run-1 publications in guidance, as well as existing
DEMAND publications. For each surviving publication retain each member's
entry geometry and output geometry, in the direction of the next run. Use
the existing location-only/demand normalization: must→may, forget forward
must guarantees/E as prescribed by the demand projector, and erase FLOW
mark exclusions for canonical abstract patterns. Retain concrete marks and
all premise members as hints. Guidance is not an executable ND conjunction.
Do not weaken the separate complete-premise requirement for real summaries.

```kotlin
val run1 = rejectSpecialStaticRootIdentity(result.snapshot())
val guide = makeCoarseGuidance(run1, includeNormalSingletons = true)
val seeds = makeCandidateSeeds(run1, guide)
// No nativeRecords.persist(run1), and no opposite transfer view of run1.
startRun2(guide, seeds, nativeRecords = strongRecordsOnly)
```

Guidance may enlarge a query; it must not veto an actual current query's
start or response. Keep the existing source/sink seed-coverage contract.
Filtering a weak edge must not remove its candidate seed or necessary hint.
Treat **all run-1 sink matches**, NORMAL as well as DEMAND, as candidates
for strong refinement. Build B2 sink seeds from both groups, preserving rule
occurrence, positive support, statement and full context. A NORMAL run-1
label is not a strong confirmation under weak stores/bypass.
Publish these report witnesses with `confirmed=false`; the existing report
can classify them as DEMAND candidates. Keep their raw fact/edge layers
unchanged. This report classification does not demote NORMAL transfer facts
or authorize an executable DEMAND summary.

The old forward stop checks for no DEMAND vulnerability or no DEMAND edge
must not prevent B2 when a run-1 candidate still needs strong refinement.
All-NORMAL run-1 work can still need B2. Candidate count, pending strong
refinement and the retained guide/query seeds determine this phase decision;
do not pretend a coarse guide is a new executable DEMAND edge. An explicit
time/policy limit can still stop with a provisional/incomplete result.
Only a complete run hands off state. Partial/OOM state is not a completed
proof or a strong record.

## 6. Strong static handling on run-2 and later

Run-2 is backward. It uses the **converse of the strong forward relation**,
with ordinary AP reversal and annotations. It does not use weak stores, the
run-1 static bypass, run-1 field gates or run-1 mark-request propagation.
Run-3 and later forward runs use the strong forward relation. Strong-phase
forms and records cannot share a weak run-1 payload/cache key.

### 6.1 Current queries supply missing callee analyses

When a later call has an actual forward input or backward output requirement,
register that requirement with the callee even if run-1 analyzed no matching
input and published no matching summary. This rule applies to **all bases**:
static data can cross into returns/arguments, and NORMAL singleton nonstatic
summaries also cease to be executable after the run-1 boundary.
Close this rule under actual nested call bindings, including a path such as
`C.f -> local -> argument -> return -> C.g` and its backward traversal.

```kotlin
fun queryStrongCallee(call: Call, requirement: Facts) {
    val q = existingSubscriptions.register(call, requirement, fullPremise)
    callee.startRestricted(q.requiredEntry, guide.hintsFor(q))
    callee.allowResponseFor(q) // current query authorizes start AND response
    // If no proven response exists yet, subscribe and wait.
}
```

This is a semantic interface, not a new mandated retained input store.
Existing caller N2/subscriptions and initial/summary deduplication can own
it. In backward mode `requiredEntry` is at the forward exit. The query's
opposite endpoint is computed by strong analysis; a missing old guide does
not restrict it to the empty set. Keep source-hit/ZERO outcomes and all
complete-premise outcomes. Ordinary demand/limit abstraction still applies.

A response is applicable by the current query's full geometry, annotations
and whole premise. A run-1 `owns(field)` check or an old DemandStore row must
not reject it. Prior guidance can schedule/enlarge work but cannot filter
these current-query responses. No universal static root is seeded to obtain
identity; only actual current requirements start work. Later actual broad
requirements are legal under the existing restricted grammar, which is not
the run-1 initial grammar.

Disable the old footprint-complement/root-summary shortcut for strong
queries. F is a read inventory, not a write/clean/return preservation mask.
Its complement cannot authorize identity across a write or nonreturning
call. Strong responses use their proven full relation and ordinary AP read
views, even when F(callee) is empty.

Unknown is pending, not empty or identity. Publish fresh proven responses
as soon as found. Recursive queries remain in the ordinary worklist until
the simultaneous query/response closure reaches a fixed point. Fair work and
accounting include new queries, responses, source hits and notifications.
An empty result is final only at that fixed point or under an independent
local proof. Cancellation yields an incomplete run.

### 6.2 S→S relation

Let D_f be all represented locations at/below C.f. For a local fact set X,
an ordinary strong store `C.f = v` denotes:

```text
store_f,v(X) = (X outside D_f) union valueTransfer_v_to_f(X)
```

All marks and the existing cut/layer policy remain in the value transfer.
For `C.g = C.f`, retain old f when f≠g, kill old g, and copy source f to g.
For f=g, read the pre-store input before killing/replacing; it is a value
assignment, not a read of an already empty destination. A constant untainted
write has no tainted value output. Explicit cleaners remove only their own
scope and marks. A true source contributes ZERO→destination, not S→S.

| Strong method behavior on an actual normal-return path | Required static relation |
| --- | --- |
| No relevant effect | f/T→f/T, for the admitted actual f/T query |
| `g=f`, f≠g | f/T→f/T and f/T→g/T; no old-g identity |
| `f=untainted` | No old-f→f edge; disjoint fields retain identity |
| Selected `clean f/T` | Remove T only in its scope; retain unaffected marks/locations |
| `f=source(T)` | ZERO→f/T; no fabricated old-f origin |
| No normal-return derivation | No caller continuation via a callee return summary |

Identity is computed from the method's actual strong path. It is not a
default answer to a missing summary. Branches join their proven alternatives;
a branch that returns may preserve f even if another branch kills it.
Only paths that reach the modeled normal exit contribute normal summaries.
Exceptional paths use the existing exceptional-return contract, not a normal
identity. Apply call-prefix and return actions in their existing order.

Normal-return evidence means a modeled return action. Virtual-exit reachability
used by AP S11/interpreter I11(e) to totalize backward traversal is not such
evidence. Artificial completion of code that never returns cannot by itself
publish a strong normal-return summary. The transfer adapter must retain this
distinction without changing IR storage. Local source/sink queries inside a
method do not require that the method later returns; only a continuation back
to its caller requires the corresponding return witness.
ZERO/source/end-generation and ND outputs need the same witness to return
to a caller. Reaching a synthetic exit or creating a candidate is insufficient.

Preservation above a field must retain the **full residual**, not only an
S-root with class C excluded. One lowering of the untouched part is:

```text
S.* except C  union  S.C.* except f
```

Intersect both with incoming geometry and E. This keeps sibling C.g and
other classes while removing D_f. Exact class points are separate from
descendants: writing C.f does not kill an exact point C. Mark exclusions
retain their ordinary denotation. Request-side exclusion erasure is not
available for these operations. Cross-class copies retain their actual
source/destination coordinates. Whole-base cleaners retain their specified
whole-fact behavior; no residual bypass restores removed data.

### 6.3 Calls, reversal and persistent records

Later static facts have **no unconditional call-to-return bypass**. Their
continuation uses a proven strong callee response under the complete caller
premise and an actual modeled return path. A pure helper obtains identity
through §6.1 even if F(helper) is empty. A helper that overwrites f obtains
the kill relation, and a helper that never returns obtains no normal static
continuation. A recursive method returns only through a finite derivation
with a real base return; self-subscription alone creates no identity.

For strong relation R_M, backward answers Q by relational preimage:

```text
pre_M(Q) = { input | exists output in Q, R_M(input, output) }
```

For conjunctive rules replace the scalar input with the complete premise;
do not apply a member independently. ZERO generation remains a source-hit
case. The current native/opposite AP read-view rules, including may/must
and exclusions, must refine this relation rather than blindly swap stored
tuples. No weak run-1 edge enters that reversal.

Persist only proven strong publications from complete later runs. Tag or
structurally separate their semantics from run-1 guidance. Reuse them only
under the existing direction, limit, demand, full-premise and read-view
contracts. Lack of a reusable record always falls back to the current query;
it never manufactures preservation. This specification does not assert that
a bounded abstract S→S edge proves exact concrete JVM memory behavior.

## 7. Correctness target and required validation

The reference is the existing selected finite AP transfer/rule semantics,
with run-1 weak stores/bypass explicitly selected above, and strong semantics
later. JVM frontend gaps, alias over-approximation, field cuts and residual
negative projection retain their declared scope. They are not erased by a
static optimization proof.

Prove these results separately:

1. Prescan closure is the least read/mark closure; mark predicates add no F.
2. The nine-family initial grammar, the two independent gates and actual
   concrete suppliers are exact for the selected run-1 domain.
3. Request gating preserves relevant read/mark observations of the eager
   **same weak run-1** reference, using dynamic changed-origin replay.
4. Weak stores/bypass are a may approximation of local strong positive data
   paths, without claiming that cleaners disappear or calls really return.
5. The run-1 boundary exports no executable weak or special-root edge.
6. Strong stores, residual E, class points and real-return composition give
   the required S→S relation; reversal gives its exact preimage.
7. Current-query discovery and response handling are complete independently
   of run-1 summary/field-plan availability, including recursive calls.
8. Guidance changes scheduling/coverage hints only; strong result validity
   comes from strong witnesses with complete premises.

The [proof dossier](static-handling-proofs.md) states constructive arguments,
counterexamples, checked local laws and integration checks. Lean local proofs
do not certify Kotlin refinement or the full source-to-sink driver. Runtime
implementation and project validation are separate work. The performance
target remains run-1 **engine time** within 60 seconds.
