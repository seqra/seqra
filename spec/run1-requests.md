# F78: run-1 mark requests

This document is normative for the selected JVM analyzer. It replaces the run-1
full-query/role request protocol, deferred-answer memo, and request exclusion tests
in the earlier specifications. It does not change runtime code. The measured runtime
is `27e22bb1ed871651143538783473cb0d4fcbcf3e`; its reports do not validate F78.

Use this document with [ap.md](ap.md), [analyzer-core.md](analyzer-core.md) and
[interpreter.md](interpreter.md). The constructive proof statements and checked
scope are in [run1-request-proofs.md](run1-request-proofs.md). The formal modules
are `lean/ApSpec/Run1Requests*.lean` and `lean/ApSpec/Run1RequestProtocol.lean`.
Only the proof receipt can establish which named statements are checked. Historical
Position/full-query theorems keep their old hypotheses; they are not F78 proofs.

## 1. Domain and invariants

A run-1 request is `(M,s,T)`: full method key M, normal abstract initial slot s,
and concrete mark T. M includes the entry and context. There are exactly two slots:

```text
StaticSlot(C,f)  -> (S, [C,f], STAR, Empty, STAR)
BaseSlot(b)      -> (b, [], STAR, Empty, STAR)
```

b is non-static, nonzero and not a cleaner temporary. A class-only position is not
a normal abstract slot. There is no request path, query tail, E, mark exclusion,
rule role, caller or call statement in the key. A set of marks is a set of separate
interests, never a conjunctive premise. Rule conditions keep their full geometry,
signed normalization, action order, occurrence identity and proof support.

The special abstract `(S,[],STAR,Empty,STAR)` is a summary seed, not a request slot.
Keep its ordinary code transfer. Do not evaluate rules or collect requests on its
root component. A packed field branch is not that root component and keeps its
ordinary processing. Section 5 defines the required summary composition.

An exact concrete fact at the empty static base is outside the construction domain:
`(S,[],EXACT,Empty,T)` cannot occur. This is an explicit frontend/domain condition,
not a theorem inferred from request storage. An exact class-point fact
`(S,[C],EXACT,Empty,T)` is legal and follows the direct concrete path below.
Root/class ANY suppliers remain in scope.

Validate K6 before request eligibility: a FLOW fact has one actual abstract-mark
InitialAp premise; TAINT facts keep their actual zero/concrete/joint-concrete
premise. An invalid pairing is an invariant failure. The currently processed
method initial does not replace a source fact's actual premise.

## 2. Slot matching and concrete answers

Request matching ignores incoming field E and FLOW mark exclusions. Those values
remain part of ordinary facts, transfers, summary/record matching and reversal.
They are not evidence that a concrete mark exists. A TAINT supplier must actually
carry T to answer T. A FLOW supplier only forwards interest.

Match `BaseSlot(b)` against nonempty input on b. Match `StaticSlot(C,f)` against
that field and its descendants, or against a broad ancestor that admits descendant
shape. An exact ancestor cannot supply a deeper field. Other bases/classes/fields
give no result. This matcher has no hidden full-rule query.

For each matched TAINT leaf carrying requested T, use these initial shapes; E is
empty in the emitted initial:

| Supplier | Concrete initial |
| --- | --- |
| Exact non-static base-root leaf | same base, empty path, EXACT, T |
| Other non-static matched leaf | same base, empty path, STAR, T |
| Static leaf at/below C.f | actual full leaf path and EXACT/ANY tail, T |
| Broad static ancestor of C.f | C.f, STAR, T |

A must supplier uses the ANY answer, not an ANY_TAINT initial. EXACT starts NORMAL;
concrete STAR/ANY starts DEMAND/ANY under AP §6.5. Preserve the supplier layer when
reading the resulting summary: emission does not certify a NORMAL caller result.
Independent exact and chain shapes for the same T are not merged.

Exact class-point input starts its exact concrete class initial directly, without
an abstract class slot or request key. A broad supplier's possible class-point
component uses a conservative concrete MAY/DEMAND direct path when class-level
rules need it. It cannot become a NORMAL exact class-point assumption. Never
broaden exact class-point taint into field taint.

Always emit the permitted abstract initial and supplied structural field initials.
Requested concrete answers are additive. InitialFactStore deduplicates method
starts; answer callback counts need not equal distinct initial counts.

## 3. Request producers and excluded marks

Only actual abstract normal origins collect or forward requests. A concrete-origin
analysis performs its ordinary mark checks and transfers, but creates no upstream
request. A concrete current fact can still answer another method's request,
including when its own caller premise is concrete or zero.

The primitive interest producers are positive source/sink/conjunction mark checks,
concrete input gates of effective microedges, selected-mark cleaners including
generated rewriters, and every generic abstract target with excluded marks. Keep full source and sink NNF and the raw-validation-before-
folding rule. FALSE has no effect. A request neither satisfies a condition nor
confirms a candidate. Unconditional generation and remove-all cleaners produce no
mark interest. F75 collects a selected cleaner mark on every same-base ordinary
FLOW input before its path/reach test, including disjoint or already-excluded cases.
The special static-root rule guard takes precedence over rule request production.

A generic target `*∖X`, including a legal hand-built compiled edge, collects each
T in X when applied on a normal abstract origin. This is an explicit F78 creator
side effect; the measured generic MarkGate does not already implement it. Do not
reject the legal edge or assume such targets absent. Static metadata includes
these target marks/scopes. Selected cleaners are the other primitive creators.

When an operation adds T to a FLOW mark exclusion, also collect T for the same
full method key and original normal slot. Exclusion propagation through code,
summaries and calls must preserve this origin obligation. If the original request
is still pending, it remains owned by counted method Work. Thus

```text
{T} union laterOffered = {T} union (laterOffered - {T}).
```

Forward requests without reading FLOW mark exclusions. A later T is only another
set contribution; ordinary admission removes duplicates. This is a request-union
statement, not permission to let a cleaned T through a fact transfer. The proof
must cover the exclusion-producing primitive and its caller-origin mapping; it
must not assume the final request-coverage conclusion as a premise.

For an ordinary unchanged static need, installed membership for the same original
slot can avoid a redundant dynamic contribution. A static plan declaration alone
cannot. C.g -> C.f, C.g -> local and argument -> static retain the original slot.

## 4. Complete static plan

The joined prescan exports its existing resolved call graph and effective entry,
statement, call, exit, native and lowered forms. Do not resolve calls again. A
runtime target absent from that graph is a contract failure. Collect code reads,
writes, transfers, cleaners, both source/sink positive condition leaves and concrete
microedge input gates and every generic excluded-mark target. Keep original rule semantics outside the request plan.

Build finite local descriptors and the least union closure over resolved calls:

```text
used[M] = directUsed[M] union union_callee used[callee]
needs[M,f] = directNeeds[M,f] union union_callee needs[callee,f].
```

Use finite iteration/worklists, including recursive components. All actual target
contexts are covered by the chosen conservative union. No DNF expansion is needed.
Mark-free zero generation and remove-all operations still contribute target ownership/structural scope, even though they contribute no request mark.
Merge marks across former roles. A deep static descriptor projects to its class/
field slot; its full condition stays in the form.

Seed the authoritative store when an active full MethodKey gets its SummaryStorage.
Do not create every possible context or materialize a field-by-mark Cartesian
product. A wide scope remains shared immutable metadata until the actual relevant
field is known; instantiate its mark contribution into P and submit normally.
Actual/later incoming fields are included, not only literally named fields. A wide
cleaner can require concrete analysis of an unrelated incoming field whose T would
otherwise be excluded by whole-tree FLOW splitting. No unresolved wide descriptor
counts as admitted membership or suppresses a dynamic request.

The field universe Sigma is finite and complete for the selected program: resolved
static fields in canonical prescan IR, rules, models and accessors, together with
their legal class points. Every accepted runtime static position belongs to it.
Reject a violation; do not silently omit a field. This condition bounds the model's
domain, not the number of eagerly admitted `(field,T)` requests.

## 5. Static ownership and the untouched frame

This section is an explicit proof-driven refinement of the accepted proposal. The
old root summary cannot be applied unrestrictedly beside normal field analyses:

* If M only cleans C.f/T, its rule-skipping root identity would reintroduce cleaned
  f/T even when the concrete field analysis correctly gives no output.
* If M writes C.f, a root code keep edge can exclude the whole class C. Reading
  that raw root result for untouched C.g/T would lose g/T.

Define structural ownership H(M) from the transitive semantic code/rule footprint.
Include source AND target fields of copies, reads/writes, generated operations and
call effects, including mark-free generation/kill targets, not just sink fields. Resolve wide scopes against Sigma/actual input
components. An unknown or whole-static effect owns the entire affected finite scope;
it has no uncertified untouched remainder. H is structural, not a mark mask. A
normal field abstract analysis owns all its marks; requested concrete analyses are
additional. Admission of T is not the event that establishes ownership of a field.

At a caller read, transiently partition the original static input into disjoint
field-cylinder components, separate legal class-point components and an untouched
residual. Respect the original E, mark payload and layer for this ordinary semantic
partition. Erasing E in an emitted initial or ignoring it in a request test does
not erase E here. Broad root/class ANY is split conservatively without making a
NORMAL exact assumption. This read-time view is not a new stored fact grammar or
an IR-storage change.

For each owned field component, use only its normal field analysis and additive
concrete analyses. Project a broad supplier into that field component before
ordinary summary satisfaction; passing the raw ancestor to a descendant-only
matcher is insufficient. If the required field result is pending, wait for its
ordinary summary delivery. Do not temporarily fall back to the root identity.
Class points use the direct concrete path of section 2.

For each unowned component, use the identity FRAME derived from primitive
noninterference, not the raw root's unrestricted/coarse-E output. The finite
footprint construction gives a decidable witness that every relevant primitive
and resolved callee leaves that component unchanged. Derive the frame by induction
on its actual normal-return execution/call derivation; do not manufacture a normal
return for a path that has none. Zero/source generation remains a separate ordinary
effect. The special root seed retains its ordinary code/summary/control role, but
is not the data semantics of the untouched frame.

The component equations are:

```text
input = union ownedParts union classParts union residual
result = union normalFieldResults(ownedParts)
         union directClassResults(classParts)
         union identityFrame(residual).
```

The scalar proof establishes equality on finite valid locations from the explicit
primitive footprint. For packed AP trees, stream field projections and retain a
compact residual view where representable; otherwise enumerate the finite required
projection rows transiently. Do not copy every concrete mark onto every field:
project only marks actually present, retaining packed leaf marks. Respect the
existing ANY/must/layer rules. An AP codec proof is required to connect this view
to the stored grammar.

Apply the same ownership/frame guard to native records, reversed leaf views and
later-run reads of root-origin summaries. Persisted unrestricted root identity or
coarse-E rows cannot bypass the partition. Store or reconstruct the stable owner
classification needed by those readers; do not relabel an old record as field
evidence. No full pipeline/record exactness follows from the finite frame lemma.

## 6. The two RequestStorage instances

For each active full method key M, use the same class twice:

* R_M in SummaryStorage is the sole cumulative admitted relation.
* P_M in the owner actor contains merged contributions since its last submission.

Producers always union into P; they do not read P for membership. Only the flusher
reads it. The abstraction and early propagation filters borrow immutable R values.
R synchronization belongs to SummaryStorage. P has one actor writer.

At the method fact-queue-empty boundary, flush ordinary publications, then execute
exactly this ownership sequence:

```text
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Commit reads P synchronously. Under the storage boundary, compute
`fresh = P - R` pointwise and set `R = R union P`. After unlocking, queue immutable
grouped fresh slot/mark messages to subscribed caller runners. Empty fresh gives
no notification. Commit neither retains mutable P nor calls solver/actor handlers
inline, including for local delivery. The owner replaces P before handling its next
event. Requests arriving during commit stay queued and enter the new P. No takeBatch
API, unit collector, bespoke mailbox, version cursor or extra cumulative mirror exists.

Preserve the same Work reservation through commit, replacement and secured child
deliveries. A request-only actor is scheduled without inventing a fact/zero seed.
An ordinary scheduling quantum can yield while P remains pending; it does not move
this submission barrier. Completion requires empty facts, pending publications and
P, and no active commit or unreserved required delivery. Fail/cancel on rejected
required work in an active run; never select COMPLETE after forgetting it.

There is no selected quota, emergency admission, rescan fallback or scheduler change.
Finite visits check cancellation. This policy promises neither hard request latency
while a method remains busy nor a fixed pending-memory limit.

## 7. Batched propagation and late inputs

The caller input store is the actual bound-input relation retained by existing
summary subscriptions (`CalleeSubscriptions`, called N2 in implementation/proof
references). It is not an additional request or emitter store.

Route request batches to the destination owner before writing P. Use normal counted
events, including same-unit replay effects. Equal proposals before admission are
allowed and union in P. A producer may subtract target R before scalar callbacks/
transport; it cannot query target P. A stale R read can create extra work but cannot
omit an unadmitted interest. Union contributions by full destination M and slot.

After K6 validation, handle concrete answers independently. Only FLOW with an actual
abstract normal InitialAp origin forwards requests. For matching offered marks:

```text
useful = offeredMarks - target.R[originalSlot]
```

Ignore input E and FLOW mark exclusions in this join. Batch changed slots for a
callee, select each relevant input handle once for that batch, and union marks from
matching slots before routing. A fresh FLOW input/growth also joins current R.
Preserve original admitted headers when accumulating fresh geometry; a normalized
AP delta is not a new group key. If using a current-value rather than fresh-geometry
visit, state and measure that repeated work. Retain no request-by-input seen matrix.

The caller input store still owns the full caller premise, method/context/entry/call and independent
layers for ordinary/ND summary matching. Request projection does not merge those
bindings. A finite owner-local visit queues answers and further request effects;
callbacks cannot mutate its live directory. No speculative suspension/horizon
protocol is added by F78.

For each real incoming supplier, enforce:

```text
register subscriber < insert/update exact caller input < deliver LinkIn
commit R < notify/replay fresh request.
```

New TAINT input is answered by the abstraction's current-R read. New request marks
replay current retained TAINT and FLOW caller inputs. New FLOW input joins current R.
No separate fresh-input TAINT request replay or callee deferred-answer memo is needed.
If A is caller input admission, E the emitter read, Q commit and V late-request visit, both
answer arms missing would require `A < E < Q < V < A`. Register-before-read and
commit-before-notify must be linearizable; all replay effects are counted and queued.
Direct incoming TAINT that bypasses caller subscription registration violates this contract.

Keep R after an answer for other shapes/callers/later inputs. Release R, P and caller inputs
only after their run work has joined. Ghost input histories in semantic equations
do not require another runtime supplier table. Restricted runs have no R/P/request
protocol: input intersects their fixed demand directly under AP §6.3.

## 8. Scope of proof and validation

The constructive models must provide executable witnesses for plan membership,
field/class projection, primitive frame composition, fresh union admission and both
arrival orders. State the finite domain and frontend conditions explicitly. Do not
assume an abstract 'coverage' predicate with the desired final conclusion built in.
The read-time packed AP adapter, complete JVM metadata extraction, recursive engine
closure, native/opposite record readers and scheduling refinement need their own
bridges; a finite scalar theorem is not a full implementation proof.

Keep F74/F75/D23 and actual cleaner order on ordinary facts; full source and sink NNF,
positive limits from 1, original support/premise scope, candidate/confirmed distinction,
and the zero-GEN end-action caveat remain. Request membership is not confirmation.
Validate strict finding identities and reached/completed/accepted iterations. Historical
full-query closure tests are references, not the semantic oracle of F78.
