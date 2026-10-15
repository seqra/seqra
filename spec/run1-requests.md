# F78: run-1 mark requests

This document is normative for the selected JVM analyzer. It replaces the run-1
full-query/role request protocol, deferred-answer memo, and request exclusion tests
in the earlier specifications. It does not change runtime code. The measured runtime
is `27e22bb1ed871651143538783473cb0d4fcbcf3e`; its reports do not validate F78.

Use this document with [ap.md](ap.md), [analyzer-core.md](analyzer-core.md) and
[interpreter.md](interpreter.md). The constructive proof statements and checked
scope are in [run1-request-proofs.md](run1-request-proofs.md). The formal modules
are `lean/ApSpec/Run1Requests*.lean`, `lean/ApSpec/Run1RequestProtocol.lean`,
[Run1InitialShapes.lean](lean/ApSpec/Run1InitialShapes.lean) and
[Run1PrecomputedRequests.lean](lean/ApSpec/Run1PrecomputedRequests.lean).
Only the proof receipt can establish which named statements are checked. Historical
Position/full-query theorems keep their old hypotheses; they are not F78 proofs.

## 1. Domain and invariants

### 1.1 Closed run-1 initial grammar

An initial here means a method-entry fact admitted to InitialFactStore, not an
arbitrary fact at a statement, a source seed, a record conclusion or a member of a
packed incoming tree. F78 selects JVM run 1 with field limit **exactly 1**. Later
limits remain positive and nondecreasing. The generic measured driver accepts a
custom first limit greater than 1; that larger domain is not certified by this
closed grammar. Nor does a generic AP factory enforce the grammar merely by
constructing an InitialAp.

Let b be an ordinary non-static, nonzero base, not a cleaner temporary; C a legal
class accessor; f one counted static-field accessor; T a frontend-admitted concrete mark.
T may have numeric ID 0 on a nonzero base: reserving that ID does not turn the base
into Zero. All initials have Empty field exclusion, and a premise has no mark
exclusion. The following table is exhaustive; a set of tails denotes separate
legal initials, not one merged initial.

| Family | Base | Path | Tail | Mark | Construction |
| --- | --- | --- | --- | --- | --- |
| Zero | zero | [] | EXACT | canonical zero mark | Root start or incoming Reach |
| Normal non-static abstraction | b | [] | STAR | STAR | Incoming non-static input |
| Special static summary seed | S | [] | STAR | STAR | Incoming static input; rule/request guard below |
| Normal static field abstraction | S | [C,f] | STAR | STAR | Supplied structural field from the closed plan |
| Concrete non-static answer | b | [] | EXACT or STAR | T | Actual requested supplier; exact root versus chain |
| Concrete static field answer | S | [C,f] | EXACT, ANY or STAR | T | Actual requested field supplier or broad projection |
| Direct concrete class component | S | [C] | EXACT, ANY or STAR | T | Direct class-point supplier or conservative broad projection |

These are twelve families if each alternative tail is counted separately. The
table is a legal envelope, not a requirement to emit all its rows for every input.
InitialFactStore equality distinguishes the alternatives. There is no nonempty E,
ANY_TAINT initial, abstract ANY, abstract class initial, non-static path below a
base root, static path below [C,f], cleaner temporary, or concrete empty S-root
initial of any tail. Only the canonical Zero row can have base zero. In particular,
an incoming root/class ANY supplier is legal but is not itself admitted as a
concrete empty-root initial.

The bounded-input proof uses the selected JVM construction, not a cut hidden in
the emitter. Validate class -> static field -> ordinary field/element suffix,
not class -> arbitrary element. Every accepted incoming static path has layout
[]/[C]/[C,f]; non-static input may retain one counted accessor, but its abstraction
and concrete answer use the base root. Statement, entry-rule, exit-rule and call
result cuts, the F74 cut after each lowered read/write, and non-growing root-only
binding-in (including S identity) establish the bound before input delivery. Full
microedge/rule paths remain uncut; their operation results take the specified cuts.
An alternate frontend, direct-input adapter or custom run configuration must prove
the same input invariant or reject that input before emission. Do not add an
emitter cut to hide a violated invariant.

The exact concrete empty S-root supplier `(S,[],EXACT,Empty,T)` is also outside the
accepted input domain. This is an explicit frontend condition, not a storage
theorem. Exact class-point `(S,[C],EXACT,Empty,T)` remains legal. Ordinary inputs
and conclusions may have E, mark exclusions or must leaves; effective forms retain
their deep paths until the specified result cut. The initial grammar does not
erase those ordinary semantics.

### 1.2 Request slots and actual origins

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

Validate K6 before request eligibility: a FLOW fact has one actual abstract-mark
InitialAp premise; TAINT facts keep their actual zero/concrete/joint-concrete
premise. An invalid pairing is an invariant failure. The currently processed
method initial does not replace a source fact's actual premise.

### 1.3 Constructors and starting facts

Every path that admits a run-1 method initial must end in one of the table rows:

1. Root start and incoming Reach create canonical Zero. A direct backward zero
   entry is not a run-1 constructor.
2. Incoming nonzero input creates its permitted abstraction, the special S-root
   seed for static input, and supplied structural field abstractions. A plan row
   or request alone creates no concrete initial and no synthetic Zero start.
3. Current-R TAINT emission and old-TAINT/new-request replay use the same concrete
   projector. A queued answer carries the already checked initial; receiver-side
   admission validates the same grammar and deduplicates it.
4. Exact class-point inputs and broad possible class components use the separate
   direct constructor, not a class request slot. A broad ANY/must supplier cannot
   justify an exact class-point initial.

Source/end-GEN outputs and source/sink seeds are ordinary edge-work inputs under
their actual premise, not a fifth way to admit arbitrary run-1 initials. Run 1
starts with no hand-off source/sink seeds and reads no native records. Later-run
record application and restricted demand emission keep their own larger grammar;
they cannot inject a new run-1 constructor. JointPremiseKey is a set of concrete
initial members for an ND proof, not an additional initial shape. Cleaner
temporaries must be projected away before persistent state or input delivery.

For a legal initial, startFact has exactly these results:

| Initial | Starting fact |
| --- | --- |
| Canonical Zero | REACH, NORMAL |
| Abstract STAR (normal slot or special S root) | FLOW at its path, NORMAL, Empty E/mx |
| Concrete EXACT | singleton exact TAINT at its path, NORMAL, Empty E |
| Concrete STAR or ANY | singleton ANY TAINT at its path, DEMAND, Empty E |

The singleton describes the starting fact only. Later Facts may pack many concrete
marks and many leaves, and method summaries keep each original premise. Supplier
layer is retained by ordinary caller matching; a NORMAL exact starting assumption
does not certify that the caller supplied NORMAL evidence. Prove closure over all
constructors and delivery adapters above before claiming an implementation obeys
the inventory. Factory permissiveness or a finite vector test alone is insufficient.

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
| Static leaf at C.f (the maximum accepted run-1 depth) | C.f, EXACT/ANY, T |
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

Construct the resolved static request base from that same closed need vocabulary:

```text
slotsOfNeed(structural(C,f)) = [StaticSlot(C,f)]
slotsOfNeed(marked(field(C,f),T)) = [StaticSlot(C,f)]
slotsOfNeed(wide or classPoint need) = []
frozenSlots(needs) = flatMap slotsOfNeed needs
frozenBase(needs) = deduplicated activate(needs, frozenSlots(needs)).
```

Activation applies each marked scope only to matching supplied slots and unions its
marks. Thus wide metadata can contribute marks to fields already resolved by
structural/exact-field needs, but does not itself enumerate unknown fields. A bare
class-point need uses the direct concrete route, not an abstract class slot. The
construction is monotone in closed needs: an actual resolved caller edge implies
callee frozen slots/base are included in the caller's frozen slots/base. No runtime
`callerActive` assumption is needed for this precomputed inheritance.

Freeze these already resolved normal-static slot/mark rows as immutable B.
These are actual precomputed requests. A SummaryStorage for an active full MethodKey
borrows its B from that method's conservative closed plan **before** any subscription
or incoming input. Equal method/SCC plans may share the same immutable value; there
is no per-context copy of the rows. Keep full MethodKeys for live ownership, dynamic
admissions and caller effects even when immutable plan content is shared. Do not
create every possible context or materialize a field-by-mark Cartesian product.

R is one logical membership view, not two independently populated request tables:

```text
R_M(s) = B_M(s) union D_M(s)       // D contains only dynamic additions
B_M(s) intersect D_M(s) = empty.
```

The immutable value used by the plan is the same value R reads as its base. Do not
iterate B into P or copy it into D. Construct P with an empty base. No initial
notification wave propagates B recursively through the call graph: the prescan
already computed that closure. All fresh inputs still read the full effective R.
Without that read or bind-before-subscribe, omitting the wave would lose answers
or requests from an input already present when B became visible.

A frozen wide descriptor is different from a resolved B row. Keep its scope metadata
shared until the actual relevant field is known; its newly resolved slot/mark
contribution passes the common R-only filter and enters P if not already in R.
Actual/later incoming fields are included, not only literally named fields. A wide
cleaner can require concrete analysis of an unrelated incoming field whose T would
otherwise be excluded by whole-tree FLOW splitting. No unresolved descriptor counts
as R membership or suppresses a dynamic request. Delayed activation uses the ordinary
method barrier and fresh notifications so retained older caller inputs are replayed.

For example, a closed B row `(C.f,T)` is already visible in every relevant caller's
R when its storage is constructed. An unchanged static supplier route to that same
slot needs no recursive propagation. The resolved-call union closure gives
`B_callee(C.f) subset B_caller(C.f) subset R_caller(C.f)`. After K6/origin validation,
when the actual original caller slot equals this queried C.f slot, the FLOW join
can skip the **whole shared B row** before enumerating marks, creating
scalar climb callbacks or sending a batch. This is source-segment provenance, not
a request role or a new key. Keep concrete answers independent. Callee dynamic D
marks still take the ordinary target-R difference; B's inheritance theorem does
not certify arbitrary future D additions.
An actual C.g -> C.f transformation instead offers T on the original C.g slot;
an argument -> C.f transformation offers it on that argument's base slot. Closure
of the unchanged static row does not discharge those different-origin obligations.
Their dynamic additions and all later caller/input routes keep the ordinary protocol.
The skip is per queried slot, not per static input or per whole callee plan. If an
input under original C.f has FLOW branches at both C.f and C.g, B[C.f] may skip,
but B[C.g] can require a new mark on original C.f. Preserve that second join.

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

* R_M in SummaryStorage is the sole cumulative admitted relation: shared frozen
  base B_M plus disjoint dynamic additions D_M, with one effective membership API.
* P_M in the owner actor contains merged contributions since its last submission;
  it uses the same RequestStorage class with an empty base.

Every producer, including local checks, remote batches and wide activation, first
subtracts effective authoritative R and then unions the remaining marks into P.
It does not read P for membership. Only the flusher reads P; contributions not yet
in R always merge there. The abstraction and early propagation filters borrow
immutable effective R values. R synchronization belongs to SummaryStorage. P has
one actor writer. The immutable B is present in every R read, including an otherwise
stale dynamic view; therefore P and D contain no B member. No second cumulative
question table, per-subscriber copy or overlay materialization is permitted.

At the method fact-queue-empty boundary, flush ordinary publications, then execute
exactly this ownership sequence:

```text
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Commit reads P synchronously. Under the storage boundary, compute
`fresh = P - (B union D)` pointwise and set `D = D union fresh`, with B unchanged.
Thus effective `R' = R union P`. After unlocking, queue immutable
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
allowed and union in P. A sender subtracts target effective R before scalar callbacks/
transport; the receiving owner applies the same R-only filter before P insertion.
Neither can query target P. A stale dynamic R read can create extra work but cannot
omit an unadmitted interest, and it never loses the immutable B membership. Union
contributions by full destination M and slot.

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
bind immutable B / effective R < first subscription or incoming input
register subscriber < insert/update exact caller input < deliver LinkIn
commit R < notify/replay fresh request.
```

New TAINT input is answered by the abstraction's current-R read. New request marks
replay current retained TAINT and FLOW caller inputs. New FLOW input joins current R.
These fresh-input reads include B; no B notification is needed. Only a later dynamic
addition, including resolved wide activation, enters the new-request replay arm.
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

The closed initial grammar and precomputed lifecycle additionally require these
separate obligations; their checked model/domain must be reported in
[run1-request-proofs.md](run1-request-proofs.md), not inferred from old factory tests:

* Constructor closure: root/Reach, ordinary abstraction, requested TAINT answers,
  direct class components and queued answers produce only §1.1 shapes from the
  selected bounded input domain. The proof covers all exact/ANY/must and broad
  ancestor cases, excludes an empty-root exact supplier, and keeps T independent
  of the canonical Zero base.
* Start preservation: each table row gives §1.3's typed starting fact, with the
  stated layer, singleton mark and Empty E; K6 stays valid through ordinary work.
  Seeds, generated edge facts, joint premises and later-run records do not add an
  unproved run-1 initial constructor.
* Physical/base identity: constructing R borrows B without copying its rows into
  P or D; effective membership equals B union D. Prove every collection/commit
  transition preserves `P intersect B = D intersect B = empty`, so no fresh
  notification contains B. Mathematical extensional equality alone does not prove
  an implementation physically shares immutable containers.
* No initial wave: binding B before registration/input plus new-FLOW/current-R and
  new-TAINT/current-R handling covers the precomputed case. Prove delayed wide
  activation still takes the dynamic commit/notify arm and changed origins are not
  mistaken for an unchanged precomputed slot. Do not assume all future dynamic
  needs were closed by the prescan.
* No unchanged-static B work: derive caller inclusion from the actual resolved
  call-graph closure, then prove the whole B-row target difference is empty for
  the same original static slot. Keep changed-origin B routing and dynamic D
  filtering as separate cases. This establishes absence of B callbacks/messages,
  not just eventual deduplication after transport.
* Completeness of cuts and input layout: every selected JVM source/native/binding/
  call route reaches the bounded-input domain before emission. The finite supply
  codec is a separate theorem from this interpreter integration. Custom first
  limits greater than 1 and measured abstract-class constructors remain outside
  this new closed-grammar certificate.

Keep F74/F75/D23 and actual cleaner order on ordinary facts; full source and sink NNF,
selected run-1 limit 1 and later positive limits, original support/premise scope, candidate/confirmed distinction,
and the zero-GEN end-action caveat remain. Request membership is not confirmation.
Validate strict finding identities and reached/completed/accepted iterations. Historical
full-query closure tests are references, not the semantic oracle of F78.
