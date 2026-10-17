# Run-1 bottom-up static FLOW propagation

Date: 17 October 2026. Status: proposed specification; no runtime change.

This proposal replaces the run-1 static **FLOW** transport in the
[current contract](run1-field-writes.md). It incorporates the selected
[normal-exit gate and NORMAL persistence proposal](../docs/design/bidirectional-next-iteration-after-selective-preservation-20261016.md).
All run-1 FLOW derivations that involve statics are DEMAND. Concrete TAINT,
ZERO discovery, full source/sink conditions and later strong analysis keep
their separate roles. This is an algorithm change, not an AP storage change.

Latest measurements are for runtime `28a123f0e`, before this proposal:

| Project | Latest reached / completed / accepted report | Run-1 engine seconds |
| --- | --- | ---: |
| OWASP | run-7 / run-6 / run-5; timeout | 263.421, complete |
| conductor | run-2 / run-1 / run-1; OOM in run-2 | 49.567, complete |
| ThingsBoard | run-1 / none / none; OOM | 176.913, incomplete |
| Stirling-PDF | run-2 / run-1 / run-1; OOM in run-2 | 36.586, complete |

The failed ThingsBoard engine timer includes 37.94 seconds of heap capture.
The [closed measurement report](../docs/reports/bidirectional-run1-selective-preservation-20261016.md)
states the configuration, strict finding differences and scope. There is no
measurement of this proposed design.

## 1. Decision and behavior change

Use two paths for run-1 static FLOW:

1. **Incoming static availability.** Analyze each needed canonical static
   reader once. Send its nonidentity effects bottom-up through reached call
   relations. Do not carry an unchanged field fact through every intermediate
   method or register an ordinary field-input subscription for that carrier.
   An actual TAINT supplier also activates hypothetical FLOW reader analysis;
   that activation asserts no concrete mark.
2. **Derived static values.** A FLOW edge from a different origin into a
   static field keeps its actual origin and statement. It joins reader
   summaries through ordinary call-bound application. It is not a globally
   available field value at earlier statements or in sibling calls.

Reader analysis is shared; application remains bound to a call. All such
FLOW results are DEMAND, including results that have moved to a local base.

| Topic | Current measured behavior | Proposed behavior |
| --- | --- | --- |
| Abstract field initials | Supplied field intersects transitive F | Only a local value consumer starts an abstract field initial; inherited needs use activation and relays |
| Unchanged field in a relay method | Ordinary method work, input subscription and LinkIn | One field activation; no field initial or unchanged intraprocedural carrier |
| Field identity summaries | Can be computed and retained per field | Elide FLOW identities covered by the stored root default |
| Static FLOW layer | NORMAL or DEMAND under the current rules | Always DEMAND, sticky through subsequent nonstatic transfers |
| Old static FLOW after overwrite | Exact overwritten field can be killed | Incoming virtual availability is retained as a DEMAND approximation; real write effects are still evaluated |
| Concrete TAINT overwrites | Strong selective preservation | Unchanged; actual TAINT is not made globally available |
| Calls | Retain an ordinary subscription for every supplied field flow | Share a field-independent call relation for inherited FLOW; retain bound inputs for derived FLOW and TAINT |
| Default | Unconditional; layer selected by StaticMayWrite | Stored `S.* -> S.* [DEMAND]` only with prescan normal-exit evidence |
| Cross-iteration reuse | Run-1 persistence is skipped | Persist eligible NORMAL rows; static-related FLOW rows are DEMAND and are excluded |

The static FLOW overwrite row is an explicit semantic approximation. Merely
changing a layer does not, by itself, justify ignoring a kill. The selected
reference semantics below retains the incoming virtual carrier at DEMAND.
It must cover the strong static semantics; it need not equal the old run-1
relation. Nonstatic field-write rules and concrete static TAINT kills do not
change.

The simple protocol also treats entry availability as visible at every
reached ZERO/control call port in that owner. This is a separate DEMAND
control approximation; §5.4 states its consequence and a stricter alternative.

## 2. Domain, edge kinds and identities

Run-1 is forward with field limit **1**. S is distinct from ZERO. Class
accessors do not consume that limit. Use the existing closed coordinate
universe and full MethodKey K, including method entry and context.

For a static field f, write:

```text
i_f = (S, [C,f], STAR, EmptyE, *)
R_f(K) = method effects with singleton premise i_f and nonidentity conclusion
W_f(K,p) = FLOW effects at point p with a different original premise
           and a current conclusion in field C.f
```

An edge `C.f -> C.g`, f != g, is both a reader of f and a writer of g. These
are two candidate classifications of one stored edge, not two copies. The
indices retain full AP geometry; a field bucket is only a candidate filter.
The name R_f here denotes a **reader effect**, not RequestStorage.

The actual write relation is broader than a STORE opcode. Copies, aliases,
rules, callee results and modeled operations can produce a W_f. ZERO/source
generation produces actual TAINT and uses the concrete path below.

Spatial equality is insufficient to detect identity. Mark exclusion mx,
tail, field geometry and layer affect the transfer. For example,
`f.* -> f.* except T` performs a mark transformation and creates a request.
It must not disappear merely because both endpoints name f.

Elide a FLOW summary leaf only if all of these hold:

```text
its premise is the canonical incoming i_f;
the leaf is the unchanged transfer of that supplied field, including mx/tail;
its layer is DEMAND;
the reached method has its ordinary, normal-exit-gated S identity;
all operation effects and mark-request emissions have already been processed.
```

Elision occurs before retaining the redundant summary and notifying consumers.
It does not remove a current read input before local rules use it. An effect
with the same field but different payload remains an effect. Do not elide
concrete NORMAL identities: a DEMAND root default does not cover their layer.
Keep whole multi-premise rows on the existing ND path.

### 2.1 DEMAND invariant

```kotlin
// Specification pseudocode; not a new public API.
fun run1FlowLayer(origin: PremiseKey, current: FlowTree, inputLayer: Layer): Layer =
    if (origin.hasStaticAbstractMember() || current.base == STATIC)
        DEMAND
    else inputLayer
```

Apply this rule at static initial start and before admission to method work,
call inputs, summaries and publication. Ordinary layer joins keep DEMAND
sticky after the current base changes. A nonstatic-origin FLOW that passes
through a static field cannot later become NORMAL on returning to a local.
Demotion changes no AP tail or geometry; do not introduce `[any]` on that
basis. Field exclusions remain Empty. Preserve actual effect mx and its
immediate original-slot request behavior.

This rule does not demote every independent ZERO-generated source. A concrete
result depending on DEMAND support must retain that support's layer. An
independent source derivation has its own support and normal eligibility.

## 3. Prescan metadata and call control

Retain the already resolved prescan graph. Separate these local inventories:

```text
LocalValueReads(M)       // code/rule value consumers; starts abstract initials
LocalMarkObservers(M)    // source AND sink mark conditions, selected cleaners
LocalStaticEffects(M)    // writes, model/alias effects, wide/unknown effects
```

`ContainsMark(C.f,T)` adds a mark observer, not a value-read request. A write
target or alias endpoint alone adds no value read. Read-clean-writeback has
its real read/mark operations. A method without GETSTATIC can still have
static rule work. Static class-point TAINT remains on its separate projector.

Transitive read/mark plans can guide activation and prune graph walks. They
no longer cause every intermediate method to start a field analysis. Keep
the existing frozen mark base B and the dynamic request relation for this
iteration; do not expand field-by-mark products or add a second mark store.
Do not precompute `every method x every reachable reader x every field`.

For each reached full entry/context, register a field-independent relation:

```text
CallPort(K, statement, calleeKey, callPlanStage)
```

It means that the existing control/call plan can reach that callee stage.
Use the existing ZERO/control traversal and resolved bindings. Prescan graph
membership alone does not identify the correct runtime context, bound input,
call stage or continuation. Register before reading callee effects/requests.
Retain one relation per reached call/target, not one per inherited field.

Call-to-return ZERO bypasses already make this an abstract control model.
The prescan normal-exit bit has that same limitation. Do not claim concrete
return or path-feasibility proofs from these facts.

After the successful prescan, copy exact-entry normal-exit Z2Z evidence before
releasing prescan storage, as in the preceding proposal. For a reached owner:

```kotlin
if (entryPlan.normalExitReachable) {
    publishOrdinarySummary(staticRootIdentity, layer = DEMAND)
}
```

There is no NORMAL run-1 static FLOW default, even in a write-free method.
StaticMayWrite can still support later analysis and diagnostics; it does not
select the layer of this run-1 default. Keep no S-root initial. Default
application still uses ordinary summary semantics; this design changes field
carrier transport, not the stored default's application law.

Normal-exit evidence gates defaults only. Methods with no normal exit must
still process actual reads, mark requests and local sink/source observations.

## 4. Logical state and ownership

Use these logical relations to specify the protocol:

```text
Available(K,f)   // canonical incoming field may be available at K's entry
LocalStarted(K,f)// canonical abstract field analysis actually started here
CallPort(...)   // reached field-independent caller/callee relation
BoundSupply(...)// existing full caller input relation, for derived W / TAINT
Summaries(K)    // ordinary authoritative summary storage, including R_f
Requests(K)    // existing borrowed B + dynamic R; owner temporary P unchanged
```

Available is scheduling/abstract FLOW metadata, not an actual concrete mark
and not a satisfied rule premise. It has no caller origin or mark-set product.
LocalStarted uses ordinary initial deduplication. A relay can publish a summary
whose canonical static premise is legal without starting that initial at its
entry. Such a premise is a symbolic dependency, not a fabricated supplier.

At most one Available membership is admitted per full owner/field. Different
callers can share reader computation. Actual applications still use their
own premise, context, callsite, input geometry and layer. Do not attach each
original caller fact to all transitive reader activations.

There is one cumulative summary store, not a second R_f result cache. Keep
request batching exactly as selected:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Derived W, noncanonical transformed FLOW and concrete inputs require retained
bound supplier payloads. A transformation can retain the same original field
while changing mx, tail or geometry; absence of a spatially different origin
does not make it the virtual identity. Omit such an input only with a proved
subsumption by the selected virtual approximation and preserved side effects.
The shared CallPort plus Available supplies the virtual unchanged FLOW input
when an R_f effect or request actually needs it. This replaces the inherited
FLOW part of the current caller-input relation, not the entire relation.

Available can itself grow large. The first implementation may use merged
owner/field memberships, pruned by transitive need. Its size is a measured
cost, not a claimed elimination of all inherited state. Do not replace it
with an eagerly expanded transitive reader directory or an origin-by-call
history. There is no selected memory quota, eviction or new scheduler.

## 5. Activation, bottom-up effects and derived writes

### 5.1 Activation is downward; it is not value application

Every geometrically eligible actual incoming static supply, FLOW **or TAINT**,
activates its canonical field **before** the local emission gate or concrete
request filter. Use the existing projection from selected transitive needs:
an exact ancestor supplies no deeper field. This includes a relay that will
emit no local initial. A compatible bound W supply also activates the callee's
canonical reader. It does not make that W value available at the caller's entry.

```kotlin
fun onIncomingStaticSupply(field: StaticField) {
    if (!available.add(field)) return
    prepareLocalStaticConsumers(field) // includes call-local before-stage work
    if (localValueReads.contains(field)) {
        startCanonicalInitial(field, layer = DEMAND)
        replayReachedRootDefaults(field)
    }
    for (port in reachedCalleesNeeding(field)) {
        sendCounted(port.callee, ActivateStatic(field))
        replayReaderEffectsAndRequests(port, field)
    }
}

fun onNewCallPort(port: CallPort) {
    register(port) // before the following current-state reads
    prepareRelevantLocalCallConsumers(port)
    replayRootDefault(port) // independent of callee.transitiveNeeds
    for (field in available.intersect(port.callee.transitiveNeeds)) {
        sendCounted(port.callee, ActivateStatic(field))
        replayReaderEffectsAndRequests(port, field)
    }
}
```

These are logical handlers. Sends and storage reads obey the existing owner
and lock boundaries. Loop over selected slots, not all legal fields. A
method with only mark observations does not start an abstract field initial
just to perform a concrete check; requests/TAINT handle that work in §6.

The transitive activation rule is a least fixed point over reached calls:

```text
Available(K,f) AND CallPort(K,c,N) AND Needs(N,f) => Available(N,f)
```

Actual TAINT must activate the hypothetical reader even before its T is
requested. Otherwise a getter of f could produce no f-to-return FLOW, and a
later return/T check could never request actual f/T. Concrete admission is a
separate current/late-request join over the retained real input.

The callee's canonical reader result is hypothetical and reusable. A single
root activation is not permission to apply that result to every caller.

### 5.2 Reader growth goes upward through call ports

Store each fresh R_f effect once in the callee's ordinary summary storage.
Notify reached caller ports with batched immutable publication references.
For the inherited path, a caller applies the effect only if it has Available
for f. The caller can create a temporary canonical field input at that call;
it does not retain it as a per-field input subscription.

Every CallPort registers a shared root-default view before reading its
current value, independently of the callee's field needs. Reconstruct local
identity inputs from LocalStarted and the port, not from `edgesAt(call)`:
the current `emitUnchanged` path does not retain every header there. Cover
all three arrival orders of LocalStarted, the port and the root default.

```kotlin
fun onReaderEffect(port: CallPort, effect: Publication) {
    if (effect.isStaticRootDefault()) {
        for (field in available.intersect(localStarted)) {
            // Keep a local reader's incoming value through this call.
            // Pure relays have no localStarted field and do no identity work.
            applyAtCall(port, premise = canonicalInitial(field),
                        input = canonicalFlow(field, DEMAND), effect = effect)
        }
        return
    }
    val field = effect.canonicalStaticPremiseFieldOrNull() ?: return
    if (available.contains(field)) {
        val virtualInput = canonicalFlow(field, layer = DEMAND)
        applyAtCall(port, premise = canonicalInitial(field), input = virtualInput,
                    effect = effect)
    }
    // Full bound W inputs also receive this effect via ordinary subscription.
}
```

The preparation helpers process relevant before-callee bindings, sources,
cleaners, requests and guards through ordinary local work admission, even if
no reader/default response arrives. Pure identity prefixes need no retained
field payload. Changed outputs enter the existing bound input relation,
including a same-origin f value with altered mx/tail. The shared virtual
branch uses its canonical descriptor. Repeated publication replay must not
refire prefix actions or duplicate source-hit observations. Reuse existing
local work deduplication for real local consumers; do not introduce an
origin-by-call history for pure transit. This adapter is a proof obligation.

`applyAtCall` uses the prepared callee-stage input and ordinary call-plan
application, not enqueue-after-call:

1. Use the matching prepared virtual descriptor or actual bound input after
   before-callee stages; do not rerun their side effects for each response.
2. Apply the summary with the proper callee input and caller premise.
3. Resume at the callee stage's output, then run rewriters, bind-back,
   end actions, cuts and ordinary successor processing.

Starting a local reader also joins already reached ports and stored defaults;
otherwise a default published before LocalStarted could be missed. At the
call handler, an unchanged canonical static FLOW uses this shared path instead
of registering another ordinary field-input subscription. Ordinary nonstatic
call inputs remain bound inputs, including nonstatic FLOW with a static origin.

Local static-reading call rules are local consumers/observers in their own
right. They must run even if the callee has no nonidentity R_f response.
Before-stage effects that change a field create a real W and use §5.3.

Nonstatic results enter the ordinary method worklist at DEMAND. Static
nonidentity results are W effects at that continuation. An unchanged static
result need not run through the body merely to return an identity already
covered by the default. Relay-only owners still retain resulting summaries
and participate in handoff; absence from InitialFactStore is not absence
from the set of completed owners.

This reverse call relation is an **implicit summary dependency**. “No R_f
subscriptions for inherited fields” means no ordinary retained field-input
subscription. It does not mean that callee growth has no caller notification
or late replay.

### 5.3 A derived W keeps its point, full origin and input

For `a.* -> C.f.*` at statement p, bind the actual a origin. Ordinary control
propagation determines which later calls can receive the value. Retain the
real input there and subscribe to matching callee effects/requests before
LinkIn. No notification turns this into an entry supply in the caller or an
earlier call. Apply the ordinary full AP matcher, including effect mx/tail.

```text
W_f(K,p, original=a, value=v)
  + ordinary reachable call input at c
  + callee R_f: i_f -> Y
  => original a -> Y at c's continuation, DEMAND
```

The callee can share its canonical i_f analysis with other suppliers. The
full binding remains in the caller. W supplied to a callee also activates
its reader closure, including a callee with no local reads but deeper readers.
Thus “activate on a local initial only” is insufficient.

For this first version, keep ordinary intraprocedural handling for derived
W values until their actual call/exit consumers. Do not add a second sparse
CFG solver for writes. Avoiding unchanged incoming carriers already changes
the dominant inherited path. W values may still be a significant residual.

### 5.4 The static availability approximation

In the expanded reference model, an incoming canonical static FLOW has an
unchanged DEMAND branch at each relevant local/control step. A static store
or cleaner still emits its ordinary value/mark effects and requests, but it
does not remove this virtual branch. The algorithm represents the branch by
Available and call ports rather than by field facts at all CFG nodes.

This deliberately admits extra DEMAND dependencies after an overwrite:

```java
void clearThenRead() {
    C.f = constant;
    sink(C.f);
}
```

An incoming abstract f can guide a candidate in run-1. Actual incoming f/T
still follows the concrete strong kill, and run-2 must not validate that old
value. There is no NORMAL FLOW record to reintroduce it in later runs.
Conservative availability must not act as actual TAINT or as evidence for a
positive/negative mark condition.

The default preserves a returning call's virtual static input at DEMAND.
It is not proof that an arbitrary derived write existed before that call.
Do not make writes across the entire application globally visible.

The simple activation protocol uses ZERO-reached call ports rather than
retaining a proof that the individual virtual field crossed every preceding
call. This can reseed an ambient field at a later port:

```java
void example() {
    neverReturns();
    emptyHelper();
    sink(C.f);
}
```

The first call has no normal default. ZERO bypass can still discover the
second call. Its own default or a reader response can introduce a DEMAND f
path at that port. Thus the selected model has two approximations: retained
incoming FLOW across static overwrites, and ambient FLOW at ZERO-reached ports.
The default gate is a per-callee check; it is not a whole-path return proof.
The nontermination case is within the accepted false positives. Extra
exception/control candidates still require strong refinement; none may
become NORMAL solely through this virtual path.

A stricter variant would retain a `VirtualInputReached(K,call,f)` token and
join it with defaults/reader responses, without storing a full Facts payload.
That would preserve more field-specific control history but reintroduce a
call-by-field relation. It is not selected in this simple protocol. Actual
W and TAINT always keep their real program-point/control bindings in either
variant. The expanded-reference proof and tests must include the selected
ZERO-port reseeding rather than claim equality with old input-header history.

### 5.5 Trace through two relay methods

```java
Object A() { Object x = B(); return x; }
Object B() { Object y = Reader(); return y; }
Object Reader() { return C.f; }
```

Assume A receives an actual supply of f and the two call ports are reached.
The sequence is:

```text
1. Available(A,f); no f initial in A, because it has no local value read.
2. Activate B, then Reader through their reached call ports.
3. Available(B,f); no f initial in B either.
4. Reader starts i_f at DEMAND and derives R_f: i_f -> return.* [DEMAND].
5. B's shared call port receives that effect. Its virtual i_f input applies
   it through Reader's call binding: i_f -> y.* [DEMAND].
6. B propagates the NONSTATIC y value and publishes i_f -> return.* [DEMAND].
7. A's shared call port applies B's effect: i_f -> x.* [DEMAND], then return.
```

There is no retained f input subscription in A or B, no static f initial in
either relay, and no computed f identity summary. The nonstatic result and
its canonical f dependency remain. If B or its port arrives after Reader's
summary, current-summary replay produces the same sequence.

A pure pass-only call is different: there may be no R_f effect at all.
The stored root default still supplies identity when a local reader analysis
needs to continue its incoming f past that call. Shared call ports must
observe/replay the root default as well as nonidentity reader effects.
Apply it transiently to the needed virtual field through ordinary call
semantics. Feed the unchanged result into a local consumer's work only where
needed; do not instantiate carrier work or field identities in a pure relay.
If that call's default is not available, an empty R_f set must not manufacture
an identity continuation at that call. This does not establish that every
later ZERO-reached call is blocked; the ambient control approximation in
§5.4 can introduce a separate DEMAND path there. This root-default path needs
no retained field input.

## 6. Mark requests, concrete values and whole premises

### 6.1 Frozen requests and virtual inputs

Keep the existing static/dynamic mark split and original-slot rules.
Frozen B is borrowed into effective RequestStorage with no startup wave,
copy into temporary storage or separate cumulative table. Changing the
local abstract-initial gate must not silently shrink B's current closure.

An inherited virtual FLOW at a CallPort joins the callee's effective request
rows exactly as its old retained input did. The canonical unchanged slot
can skip inherited B membership because the caller already borrows it.
Dynamic additions still route to the caller's original canonical f slot.

```text
new Available or CallPort -> join current reader effects AND request rows
new dynamic request      -> visit bound W/TAINT AND available virtual inputs
new bound actual input   -> join current requests through the existing path
```

These handlers replace the request replay formerly supplied by the removed
inherited input subscriptions. New temporary requests still merge mark sets
and commit at the method-queue-empty barrier. Request matching ignores field
E and FLOW mx; actual summary/mark evaluation does not ignore them.

### 6.2 Changed origins must survive canonicalization

```java
// A has initial C.g.*
C.f = C.g;
B();                         // B checks ContainsMark(C.f,T)
```

B's canonical reader can ask for f/T. The W binding in A routes this to
**A's original g/T**, not to f/T. A later actual g/T answer must traverse the
copy and actual call stages before it supplies B. The analogous nonstatic
source requests its original BaseSlot. Concrete origins skip upstream request
propagation. Canonical activation carries no replacement provenance.

Dropping inherited input subscriptions therefore requires two replay arms:
virtual unchanged-slot replay, and existing full-origin bound-input replay.
Keeping only summary replay would lose required concrete answers.

### 6.3 Concrete TAINT is a separate path

Do not globally broadcast actual static T, infer T from FLOW, or fabricate
a T answer from Available. Keep current requested concrete initial projection,
tail restrictions, direct class components and exact caller input retention.
Nonexact concrete field/class initials are STAR, never `[any]`; their existing
DEMAND start semantics remain. No abstract root/class initial is added.

For TAINT, ordinary subscriptions can be necessary even when the spatial
origin and conclusion are the same field. ZERO/source/model generation also
bootstraps actual inputs without an off-diagonal FLOW W. Thus the rule
“subscribe only for W” applies to the new **FLOW carrier path**, not to every
static summary or every concrete supplier.

Preserve concrete cleaners and overwrite kills. Do not use inherited virtual
availability to bypass an actual kill, source condition, call binding or
before/after action and then produce a NORMAL TAINT result.

### 6.4 ND and observations

`f/T AND g/U -> h/V` remains one whole multi-premise relation. R_f indexing
does not split it into independent implications. A mark-set union is an
interest set, not proof that all marks are present. Keep the original same-call
join, premise union, contexts, occurrence IDs and layers.

FLOW reader relays are singleton abstract dependencies. Concrete and ND
observation/summary joins retain the ordinary full binding. Local sink and
conditional source work must run even without a returned summary or normal
exit. Negative checks never use absent lazy state as evidence.

## 7. Arrival orders, recursion and completion

Cover these joins in both orders:

```text
Available x CallPort
(Available, CallPort) x stored reader effect
(LocalStarted, CallPort) x stored root default
bound W input x stored reader effect
virtual/bound input x effective request row
actual concrete supplier x requested mark
```

Register before current-state replay; store a fresh effect/request before
notifying registered consumers. Either a new side sees the old side, or the
later delta sees the new registration. Do not mark an unanswered request
closed, assume an empty reader set is final, or retain a request-by-input
seen matrix. Ordinary fact/summary union removes repeated results.

Merge activation fields and publication references per target owner before
sending counted work. Emit only fresh availability/summary/request deltas.
Do not copy all transitive summaries to every reader or retain publication
histories. Ordinary method-queue-empty publication/request barriers remain.

Use the existing owner work system for local and cross-unit effects. Pending
activation, relay publication, bound replay, request commit and child delivery
all count before completion. No inline callback under a store lock. Cancellation
or rejected active delivery cannot produce a successful partial result.

Recursive call components reach a monotone fixed point: activation, summaries,
requests and bound inputs only grow over the finite run-1 domain. Static
FLOW identities create no publications. Finite support gives termination
under fair processing; this is not a 60-second performance proof. Late callers
and late W inputs replay stored effects rather than rerun a completed reader.

## 8. Run-2 and persistence

At a successfully joined run-1 barrier, persist ordinary eligible NORMAL
rows, preserving existing native/reverse admission guards. There is no new
persistent ND format and no persistence of DEMAND rows. With the new rule,
static-related FLOW and the static default supply **no** NORMAL records.
Unrelated NORMAL FLOW and independently supported eligible TAINT/ZERO rows
can still be reused.

Pass DEMAND reader/write dependencies to the existing demand handoff, including
relay-only owners and every required original support. Do not replace their
executable AP payloads with root patterns or layer-driven `[any]`. Keep the
existing typed demand projection: forward-to-backward handoff can project a
STAR conclusion into its ANY demand entry. That projection is not a changed
executable fact or an ANY run-1 initial. A candidate requiring static flow must
remain reachable to the strong run-2 backward analysis and later forward
refinement. Failure to hand off a relay is a potential false negative.

Later runs use actual demand and strong static write/cleaner semantics. They
may produce real S-to-S relations with field exclusions; the blanket run-1
DEMAND default is not a reusable NORMAL substitute. Current-query admission,
late response handling and report confirmation policy remain separate. No
run-1 failure/timeout prefix is persisted as an executable result.

## 9. Correctness claims and required proofs

The intended claim is **coverage with preserved support**, not equality with
the old run-1 relation. Establish two separate refinements:

```text
strong reference static behavior
    is covered by expanded run-1 virtual-static DEMAND behavior;
expanded virtual-static behavior
    is implemented by local readers + bottom-up relays + bound W joins.
```

The proof sketches below define the obligations. They are not executable
proof results or an implementation certificate.

1. **Layer closure.** Static starts are DEMAND. All static current FLOW is
   demoted before storage. Every ordinary join preserves DEMAND, including
   static-to-local and local-to-static-to-local paths. Consequently no
   static-FLOW-dependent result is admitted as an independent NORMAL record.
2. **Default and identity cover.** Applying the gated root DEMAND identity to
   an actual field preserves that field's payload and joins its layer. It
   covers the elided unchanged DEMAND leaf. The entry/normal-return witness,
   tail correlation, mark exclusions and call actions must be the same.
   Same-position transformations and NORMAL TAINT do not satisfy this lemma.
3. **Strong-to-DEMAND coverage.** Induct over a finite strong derivation.
   Static overwrite kills remove paths, so retaining a virtual incoming
   path adds demand rather than removing a true path. Each real read/write,
   cleaner, rule and call effect is still evaluated with its support.
   Availability at ZERO-reached call ports is also an explicit covering
   control step, not a claimed returning execution. Concrete condition
   evaluation is unchanged. Prove the actual demand
   ordering/seed law; a label named DEMAND alone is not that proof.
4. **Activation completeness.** Take the first local consumer on a real
   incoming-static dependency path. Entry supply or a bound W activates its
   callee chain before local emission gates. Reached CallPort replay and
   transitive need closure eventually start that reader. No caller-specific
   origin is required to compute its canonical hypothetical dependency.
5. **Bottom-up composition.** Induct back over the same finite call path.
   R_f growth or later activation meets a reached call port, then ordinary
   call stages map the result to the caller's canonical premise. Nonstatic
   work and nonidentity static W results retain it until their next consumer
   or summary. No field initial is needed in a pure relay. Recursive cycles
   follow from fair fixed-point iteration, not acyclic order.
6. **Bound W substitution.** Applying R_f to actual W uses the full input and
   original caller premise, never the canonical f as a replacement origin.
   Temporal control places results after the correct call. Ordinary AP
   summary composition proves the resulting leaf and request mapping.
7. **Request coverage.** Replace each removed input's request join with the
   virtual input join; use inherited B inclusion for unchanged slots and
   original-slot routing for W. Commit/replay order covers both late sides.
   Every real requested concrete supplier still takes the actual bound route;
   activation never answers T. Keep the no-ANY initial constructor proof.
8. **ND and local observation coverage.** Preserve whole premise/same-call
   joins, full source/sink conditions and local observations before exit.
   Neither a default gate nor a missing return can suppress a local sink.
9. **Handoff and NORMAL reuse.** The expanded DEMAND paths yield sufficient
   original-premise seeds for actual strong later queries, including relay
   owners. Ordinary eligible NORMAL persistence is justified independently
   of the discarded virtual FLOW path and retains reverse-reader guards.
10. **Scheduler refinement and lifetime.** Each relation delta has a counted
    owner event, all races reduce to a covered join order, and prescan views
    are copied before release. No active work or virtual dependency is lost
    at quiescence, cancellation, record construction or demand handoff.

## 10. Gaps in the original sketch, resolved scope and open bridges

| Gap | Required specification rule |
| --- | --- |
| No W exists for the first incoming field | Available plus shared reached call ports bootstrap readers and return effects |
| Actual TAINT arrives before any concrete mark request | Its geometry activates hypothetical FLOW before concrete filtering; its mark is answered only through the actual path |
| A relay emits no initial, so “notify when initial emitted” stops early | Activate on incoming supply before the local-read gate; W also activates its callee |
| A call has no nonidentity reader result | Shared ports replay the stored root default for a needed local identity continuation; absence of R_f is not a return proof |
| Ambient entry data is reconstructed at a later ZERO-reached port | This is an explicit extra DEMAND control approximation; the normal-exit gate remains per callee |
| “Globally available” can make a later write satisfy an earlier read | Only incoming virtual FLOW is ambient; W/TAINT stay bound to their actual program point |
| `f -> f` can clean marks or have a different layer | Elide full semantic FLOW identities after request/effect processing, not equal field names |
| All-static-FLOW DEMAND is mistaken for a TAINT policy | Retain concrete actual inputs, kills, same-position subscriptions and full conditions |
| Published R is enqueued after a call without its actions | Resume through the ordinary callee stage and remaining call plan |
| A mark-only method is mistaken for a method with no static work | Keep mark observer/request/concrete evaluation without adding a field-read request |
| Canonical activation destroys changed origin provenance | Full bound W substitutes the caller's real original slot, including cross-field chains |
| A same-origin field has changed mx/tail | Retain a bound transformed input; spatially equal origin is not sufficient for the virtual identity fast path |
| Removing input subscriptions removes request replay too | Add virtual current/late-request joins at shared call ports |
| Per-field readers flatten conjunctive conditions | ND stays on the full-premise ordinary path |
| One topological pass is assumed enough | Recursive SCCs use monotone activation/effect/request closure |
| Eliminating carriers is assumed to eliminate all state | Measure activation, call-port, W, relay summaries and handoff state as new owners |

The strict claim “no intraprocedural static work in a method with no local
static access” needs a qualification: a derived W returned by one callee can
feed another callee or an exit. This version removes the unchanged incoming
carrier. It keeps ordinary W propagation to preserve that dependency's
position and origin. Eliminating that residual as well would require a
separately specified sparse control path between write/call/exit consumers.

Open implementation bridges are the exact integration with existing call
before/after stages, packed AP identity decomposition and W classification,
the concrete request replay after virtual activation, and the demand-handoff
seed law for relay-only owners. These require designs/tests against the
actual engine before implementation. The scope does not include replacing
concrete input storage, rewriting reference managers or changing IR storage.

## 11. Expected improvement and costs

The latest static-catalog logs report these logical method/field read-plan
memberships. These are **CommonMethod metadata counts**, not live contexts,
runtime initials, memory bytes or static instruction counts:

| Project | Local F | Closed F | Closed / local |
| --- | ---: | ---: | ---: |
| OWASP | 2,148 | 6,255 | 2.91 |
| conductor | 6,192 | 1,298,007 | 209.63 |
| ThingsBoard | 33,240 | 15,706,500 | 472.52 |
| Stirling-PDF | 19,874 | 40,182 | 2.02 |

The closed log root is
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-selective-preservation-preparation/verification-20261016T202647.993220Z`.
The source files are `selected-current/projects/{OWASP,conductor,thingsboard,Stirling-PDF}/bidi/analyzer.log`,
at `Bidi static catalog setup`; counts are `directAbstractions` and
`transitiveAbstractions`. The measurement report records ThingsBoard's closed
F count. The large ratios identify inherited read needs, not a measured
reduction in executed work.

In ThingsBoard's run-1 heap:

* Static abstract initials account for 372,664 of 535,668 initial memberships
  (69.57%). Their local-read versus inherited-only split is not measured.
* Static abstract original premises account for 3,797,487 of 4,707,790 caller
  input groups (80.66%). Currently static FLOW accounts for 3,767,181 groups
  (80.02%). These are overlapping classifications, not additive savings.
* There are 45,964 currently nonstatic FLOW groups with static abstract
  origins. Their nonidentity value transport must remain.
* The caller-store union retains about 2.323 GB, including about 1.443 GB of
  indices. The conclusion union retains about 2.391 GB. These owner views
  overlap; they are not a combined saving estimate.

Expected impact, in order:

1. Avoid inherited static FLOW input groups, their indices and repeated call
   joins. Replace them with one field-independent call relation and merged
   owner/field activation. This targets retained state as well as work.
2. Avoid abstract initials and intraprocedural carrier work in relay methods.
   Only local readers start abstract field analyses. Fewer propagated carrier
   facts should also reduce conclusion allocations and queue traffic.
3. Avoid field identity summary retention and fanout. Only nonidentity reader
   effects travel bottom-up; default identity remains shared.
4. Reuse canonical reader computation across callers/derived suppliers while
   keeping their actual application separate. This can reduce duplicate work
   even when derived W/concrete bindings remain necessary.

There is no defensible saving percentage or 60-second prediction yet.
Conductor/PDF heaps are from run-2, and OWASP has no corresponding run-1 heap;
do not transfer ThingsBoard's static percentages to them.

Costs can grow: Available memberships, reverse call fanout, more DEMAND
dependencies, later strong refinement and bound W inputs. Giving write-free
static FLOW DEMAND also reduces NORMAL reuse relative to the preceding
proposal. The benefit must be net of these costs. Coalescing notifications
alone does not remove retained inputs; this design must actually omit their
inherited FLOW admission.

## 12. Validation and implementation gate

Before touching runtime, discharge the bridges in §9 with an expanded scalar
reference and independent packed-AP/call adapters. Required examples include:

* A -> B -> C, only C reads f: only C starts f; B relays effects and requests.
* A only calls a reader and has no W: the reader's return still reaches A.
* A source supplies f/T before any request; a getter returns f and its caller
  later checks return/T: abstract activation leads to the real concrete answer.
* Two callers supply the same field from different origins: shared analysis,
  distinct bound results and changed-origin requests.
* `x=readF(); C.f=source(); sink(x)`: the later actual write cannot satisfy
  the earlier read or produce a NORMAL result for it.
* `C.f=constant; sink(C.f)`: extra virtual FLOW is DEMAND; actual old f/T is
  killed, including cleaner/read-writeback variants.
* f->g->h across calls, plus nonstatic->f->local: complete origin and sticky
  DEMAND survive every base/field change.
* Same-field mx transformation: no false identity pruning; request T emitted.
* A mark-only source/sink condition, multi-mark NNF and full ND same-call
  support; no T from abstract activation, no negative proof from absence.
* Local sink before an always-throwing exit, caught exceptions, unreachable
  call, multiple contexts/entries and recursive SCCs with/without returns.
* A nonreturning call followed by another ZERO-reached call: any ambient
  reseeded result remains DEMAND, yields no NORMAL record, and is subject
  to actual strong refinement. Do not assert whole-path blocking from one
  missing default.
* Every pair of arrival orders in §7, including request-only and relay-only
  owners; no premature quiescence or missed handoff seed.
* Static class points/broad TAINT and unknown/model effects: retain the
  current bounded projector and conservative fallback, never drop silently.
* Eligible unrelated NORMAL records persist; no static-related FLOW record
  persists; native/reverse reading and larger positive limits retain guards.

Use the same fixed project configurations and strict finding identities as
the closed report. Record latest **reached, completed and accepted** iteration
for every result, even a failure. Separate run-1 engine time from prescan,
metadata setup and report barriers. Do not claim success from a timed prefix.

Add owner/counter attribution for local versus inherited activation/initials,
avoided and retained caller-input groups, W origins and full payloads,
identity versus nonidentity summaries, virtual request joins, replay fanout,
Available/call-port bytes and later-run demand growth. Compare net retained
heap and actual run-1 completion with runtime `28a123f0e`; retain main as the
finding reference. No benchmark, test result or proof execution is added by
this document.
