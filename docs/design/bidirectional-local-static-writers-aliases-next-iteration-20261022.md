# Next iteration: remove repeated static GROUP work in run-1

Date: 22 October 2026. Status: **proposal; runtime implementation unchanged**.
Measured runtime: `a4bf789866f912ebefc54bca42d809c89beea2d4`.
The later `0a55060ec` commit changes documentation only.
Follow [proof-guide.md](../../spec/proof-guide.md).

The first two items now have a
[complete specification](../../spec/run1-static-group-protocol-removal.md)
and [proof dossier](../../spec/run1-static-group-protocol-removal-proofs.md).
Those documents define their implementation contract. They select protocol
removal with the existing local AP reducer and scalar caller suffix.
The raw-output and packed-suffix shortcuts discussed below remain further
work; they are not part of the selected two-change specification. Items 3
and 4 also remain unselected. Runtime implementation is unchanged.

This revision follows the latest decisions:

- Focus performance work on **Thingsboard run-1**.
- Seek a large reduction in work, such as a factor of two. A faster
  primitive or a smaller container is not the main objective.
- **Do not apply the ZERO-premise exit restriction.** It needs a correct
  exit-rule design first. Keep current exit behavior in this iteration.
- Keep controller exit-rule selection on controllers. Rules need their
  annotations. Do not replace those checks with a dispatcher exit check.
- Defer the related Spring return/provider rewrite.

The [current performance report](../reports/bidirectional-local-static-writers-aliases-performance-20261022.md)
and the [focused investigation](../reports/bidirectional-thingsboard-run1-workload-investigation-20261022.md)
give the evidence and its limits. No new project run is claimed here.

## 1. Measurement boundary and proposed order

Thingsboard reaches **run-1 OOM** after **260.838 engine seconds**. It has
no completed iteration or accepted report. Conductor completes/accepts
run-1 in 18.134 seconds and reaches run-2 OOM. PDF completes/accepts
run-1 in 25.089 seconds and reaches run-2 OOM. Those two run-1 times are
acceptable for this investigation. OWASP reaches run-8 TIMEOUT, after
completing/accepting run-7; its run-1 takes 172.096 seconds.

| Order | Algorithmic change | Work that becomes unnecessary | Evidence and limit |
| --- | --- | --- | --- |
| 1 | Apply published static GROUP summaries without requesting their premise again | Per-output call continuations, pool interests/replies and canonical input self-composition | The original already has an answered request; 304,292 pending call continuations in the Thingsboard heap |
| 2 | Publish a static GROUP output without rejoining its known static original | Unary conditional joins, repeated premise interests/replies and self-composition | Same invariant; 10,276 pending conditional joins in the heap |
| 3 | Admit equal internal call/resume inputs before executing their suffix | Repeated complete stage execution for an equal input | Missing admission is source-confirmed; repetition frequency needs measurement |
| 4 | Deliver callee input only for a fresh caller subscription delta | Rejected duplicate LinkIn events and repeated abstraction of old input | Unconditional link is source-confirmed; delta/annotation equivalence needs proof |

The first two remove a protocol that rediscovers an established fact.
They do not make that protocol faster. The third can reduce n equal
suffix executions to one, but needs a duplication census before selection.
Do not infer a twofold total speedup from one counter or sample share.
Measure the changes separately.

Keep current exit/Spring behavior, mark groups, rule permissions, every
contributing atomic original, field transfer, cleaners, aliases, empty
run-1 E, field limit 1, ordinary requests, three-set static requests,
canonical answers, selected demands, exact class markers and complete
caller/stage/layer state. Keep source/sink identities, barriers,
cancellation and later FULL execution.

Do not add GroundingSupport, actual-input validation, an answered-input
history, an IR change or a reference-manager change. A witness in a
formal proof is not a new label on runtime facts.

## 2. The invariant: a static GROUP original has already been answered

The production pipeline is:

```text
reached static read -> C.f/*/FLOW original
first real group check -> request C.f/G
writer/source closure -> pool answers the request
PointReady -> concrete static original -> propagation -> summary
```

`Run1Abstraction.emit` rejects static inputs. `ReadReady` creates FLOW,
not GROUP. Concrete original constructors in point materialization,
direct class replies and `staticSupply` use an actual pool reply.
Transfers, call suffixes, wrapper promotions and all-origin rule arms
retain an existing original, or use ZERO. They do not invent a new static
GROUP original. Run-1 has no executable `PremiseSet`.

For a field, the reply occurs after the group enters
`processed = requested AND written`. Membership stays true until the
run ends. Therefore:

```text
published premise j = C.f/*/G
    implies C.f/G is already processed in the run-1 pool
```

Availability in `reach` alone would be insufficient: a first request can
wake FLOW writers. The stronger processed invariant closes that gap.
Required interests for existing writers have already been admitted and
secured for delivery. Keep that counted work even if delivery is pending.
New writers receive existing `req + processed` at registration. Keep
those first-request/late-writer operations. A repeated call interest adds
no fresh writer/group admission.

Exact class markers use a separate invariant: an actual reply establishes
supply and the matching `classRequested` membership. Do not treat a class
marker as a field, a class STAR fact or an S-root initial.

| Production original | Old canonical composition input |
| --- | --- |
| `C.f/*/G` | `C.f/[any]/G, DEMAND` |
| Exact special `C/./G` | `C/./G, DEMAND` |

The second column is a current fact, not a new initial shape. Keep any
other legal shape on its old path until its producer/composition proof is
complete. Do not narrow the fact grammar to authorize this optimization.

This invariant uses **current** source/exit behavior. It assumes neither
a ZERO exit filter nor exact concrete-program reachability beyond the
existing run-1 approximation. A computed supply means a supply accepted
by the current engine, including its existing GEN rules.

## 3. Remove the repeated static call handshake

### Which subscriptions remain

This change does not restrict all subscriptions to static read micro-edges.
It removes a pool subscription made while applying an already-published
static GROUP summary. Keep these distinct mechanisms:

| Mechanism | New behavior |
| --- | --- |
| Local static read/write-presence registration | Keep the reached operation's existing registration and read activation |
| First group refinement at a condition or cleaner, or a FLOW writer's input request | Keep it; a FLOW original does not prove that the requested group is available |
| Ordinary caller subscription to callee summaries | Keep it, including wrappers with no local static read |
| Extra pool interest for the concrete static premise of a published GROUP summary | Remove it for the proved shapes; that premise's request has already been answered |

For example, `use() { checkG(get()); }` can need its first C.f/G
refinement at `checkG`, even if only `get` contains `return C.f`.
That first request stays. Once a concrete static GROUP summary is
published, each wrapper applies it without asking the pool again.
The criterion is the published GROUP premise and its producer invariant,
not whether the caller contains a static read micro-edge.

```java
void set() { C.f = source(); }
Object get() { return C.f; }
Object wrap() { return get(); }
void main() { set(); sink(wrap()); }
```

After real G refinement, `get` can publish:

```text
j = C.f/*/G
j -> return/./G, DEMAND
```

Current GROUP activation at `wrap` repeats this pipeline:

```text
split publication into output leaves
for each leaf:
    allocate scalar Facts + Publication + CallKey + CallContinuation
    queue StaticInterest(C.f/G)
method barrier -> pool commit -> known-group PointReady -> actor delivery
    -> materialize canonical input -> match j -> self-apply summary
    -> execute caller suffix
```

The pool answered C.f/G before the summary existed. The continuation
waits for no new semantic information. It can occupy memory while its
batch waits for the method barrier.

New: apply the packed summary at its exact caller/stage, retaining j:

```kotlin
// Pseudocode. Run-1 ZERO activation; never replace the static original by ZERO.
fun applyRun1StaticGroupSummary(part: Subscription, pub: Publication) {
    val initial = pub.premise as InitialAp
    check(run1 && part.ref.premise.isZero && part.added is Reach)
    check(hasCanonicalStaticGroupShape(initial))
    val selectedRef = part.ref.copy(premise = initial)
    val results = ArrayList<PlanFact>()
    for ((value, noEffect) in ap.run1SummaryParts(initial, pub.conclusion)) {
        check(value.layer == Layer.DEMAND)
        if (value.base == STATIC) promoteStaticSummary(initial, value)
        else results += PlanFact(initial, value, noEffect)
    }
    resumeCallees(selectedRef, results)
}
```

Run the existing raw alias partition before binding. Preserve output
paths/marks/tails, caller layer, stage and demotion state. FLOW activation
is unchanged. No pool lookup, answered cache, CallContinuation or
per-leaf Publication copy is needed for the proved GROUP branch. Other
PointReady consumers still perform real discovery/refinement.

The shape helper is a local descriptor check, not a new support manager:

```kotlin
fun hasCanonicalStaticGroupShape(p: PremiseKey): Boolean =
    run1 && mode.markMode == MarkMode.GROUP && p is InitialAp &&
    p.base == STATIC && p.mark is MarkSlot.Concrete &&
    p.exclusion == ExclusionSet.Empty &&
    ((p.path.size == 2 && p.tail == Tail.STAR) ||
     (p.path.size == 1 && p.tail == Tail.EXACT))
```

Its correctness uses the constructor invariant at this existing
publication boundary. Shape alone is not proof of availability for an
arbitrary manually constructed initial. Add no provenance label, lookup
table or actual-input validation to implement the invariant.

Availability does not prove AP equality. The implementation gate is:

```text
old canonical pool input = startFact(j).withLayer(DEMAND)
applySummary(old input, j, raw output) = raw output
```

The full prefix matches with no residual suffix. Initial E is empty.
Grafting keeps every output's own path, group and exact/ANY/MUST tail;
the layer stays DEMAND. Prove this against the actual AP implementation.
If that identity fails for a shape, keep AP composition locally and
remove only its redundant service round trip until the proof is fixed.

Here is a two-leaf case under field limit 1:

```java
Pair getPair() {
    Object v = C.f;
    Pair p = new Pair();
    p.left = v;
    p.right = v;
    return p;
}
```

```text
j = C.f/*/G
packed summary: j -> {return.left/[any]/G, return.right/[any]/G}, DEMAND
current: two premise handshakes; two scalar inputs to the caller suffix
new:     no premise handshake; one packed input to the same suffix
```

For L output leaves, the old path has L handshakes, scalar copies and
self-compositions. The new path has **zero handshakes** and at most the
two existing packed alias arms. With 32 compatible leaves in one arm,
caller suffix inputs change from **32 to 1**. All required summaries and
demands remain. This is a factor for the stated pattern, not a measured
Thingsboard distribution or a whole-engine speedup.

Packed suffix execution also needs a separate equivalence proof. Include
negative conditions, complete rule joins, source permissions and cleaner
ordering. If a stage is not additive over these outputs, keep its old
scalar execution while removing the already-answered handshake. Do not
change rule evaluation semantics to obtain a batching factor.

## 4. Remove unary static GROUP supply joins

### Showcase: copy a resolved group to another static field

```java
void copy() {
    Object x = C.f;
    checkG(x);
    C.g = x;
}
```

`checkG` has the configured condition `ContainsMark(arg0, G)` and keeps
its argument unchanged. Assume C.f has a permitted G write. Its first
refinement request at the check has completed. C.g has no G supply or
request yet. The relevant state is:

```text
pool C.f: req={}, reach={}, processed={G}
pool C.g: req={}, reach={}, processed={}

j = C.f/*/G                       original; E is empty
j -> x/[any]/G, DEMAND            resumed current fact at the check
j -> C.g/[any]/G, DEMAND          result of C.g = x
```

`[any]` here is the current fact's tail. The initial remains `C.f/*/G`.
All displayed GROUP edges are DEMAND. The static write retains a fresh
summary delta `d = C.g/[any]/G` under j. The following trace starts at
`computedGroups(j, d)`; the real read, first G request and write transfer
are common to both versions.

### Current operation trace

```text
1. Retain the fresh method summary j -> d.
2. computedGroups(j, d):
     allocate ConditionalJoin q with one input slot
     allocate JoinInput r = (q, member=0)
     append StaticInterest(copy, r, C.f/*/G, {G})
3. At the method barrier, commit that input interest.
     C.f/G is already processed.
     create PointReady(copy, r, canonical C.f input with G).
     No fresh writer/group admission is needed at C.f.
4. Deliver PointReady to the method actor.
     remove r; fill q's only input; remove q
     publishMatched: borrow the retained output d
     materialize C.f/[any]/G, DEMAND
     match it against j
     apply the summary to that same canonical input: result = d
     append staticSlices(d) to the output supply batch
5. At the next output commit, offer C.g/G to the pool.
     C.g had no request: reach={G}, req={}, processed={}.
```

The join in step 2 is unary because run-1 has one executable original.
Steps 2 through 4 discover no new input membership. The already-answered
input request is being repeated merely to publish the output.

### Proposed operation trace

```text
1. Retain the same fresh method summary j -> d.
2. computedGroups(j, d):
     the static GROUP producer invariant proves C.f/G is processed
     append staticSlices(d) directly to the output supply batch
3. At the output commit, offer C.g/G through the same service path.
     C.g had no request: reach={G}, req={}, processed={}.
```

There is no ConditionalJoin, JoinInput, repeated C.f interest, PointReady,
input materialization or AP self-composition. Keep j, d, its ordinary
summary publication and all selected demand coordinates. The output can
be offered at an earlier barrier; complete semantic closure stays the
same, subject to the stated AP and protocol proof gates.

| Observation after the supply settles | Current | Proposed |
| --- | --- | --- |
| Method summary | `j -> C.g/[any]/G, DEMAND` | Same |
| C.f group state | `processed={G}` | Same |
| C.g group state | `reach={G}` | Same |
| Extra input joins / interests / replies | One of each | Zero |

If a C.g/G request arrives later, both versions move it from `reach` to
`processed`, answer it and admit any required FLOW writer interests.
That first output-field request remains necessary. The optimization
removes the repeated input-field request, not that request or its effects.

### Implementation sketch

New: the processed-original invariant and canonical self-apply identity
permit output supply through the existing batch:

```kotlin
fun computedGroups(premise: PremiseKey, delta: Facts) {
    if (delta !is TaintTree || delta.base != STATIC) return
    when {
        premise.isZero -> staticBatch.supplies += staticSlices(delta)
        hasCanonicalStaticGroupShape(premise) ->
            staticBatch.supplies += staticSlices(delta)
        else -> keepExistingComputedSupplyPath(premise, delta)
    }
}
```

Keep fresh raw delta admission, result bounds and ordinary publication.
A hypothetical **nonstatic** argument/group still cannot supply the pool.
ZERO source supply is unchanged. The service still admits the output,
deduplicates field/group membership and answers real waiting consumers.

For L outputs this removes L joins, input rows, interests, replies and
self-compositions. It retains the actual output memberships. In an
unseeded FLOW copy cycle, no GROUP original or supply is created.

## 5. Further candidates need a work census

### Admit an equal internal suffix input once

`installPoint` queues without semantic admission. Static replies also
enter `resumeCallees` directly. The successor edge store deduplicates only
after cleaners, rule checks and callee subscriptions can run again.

For n equal complete inputs and an m-stage suffix, current work can be
n*m; a stage fixed point can execute m operations plus input admission:

```text
key = (method, original premise, full ResumeSite, complete CallResumeState)
delta = existingAtStage.add(incoming) // preserve semantic/annotation growth
empty delta -> no stage execution
fresh delta -> execute this stage on delta
```

A statement index is insufficient. Keep cleaner phase, stage ordinal,
caller layer, no-effect flag and pending demotion. Prove late-rule/ND
and source side-effect coverage. This is a new scheduling/storage design,
not a small cache patch. Measure exact repeated stage inputs after the
first two changes; do not add a large permanent store on speculation.

### Send only fresh callee input

`enterCallees` calls `subscribe` and `link` unconditionally. Rejected
subscription duplicates can still cause LinkIn and callee abstraction.
Use the same caller admission for both effects:

```text
no delta -> no replay and no LinkIn
fresh delta -> replay old summaries on delta; deliver delta to callee
```

Keep cumulative caller input for new summaries and late requests.
Ordinary summary replay already uses `Admission.fresh`. The proposed
change is the unconditional link. Prove input-emission additivity,
annotation growth, ZERO/static behavior and late-request coverage.

## 6. Proof and measurement gates

The constructive model is
[Run1StaticPremiseAvailability.lean](../../spec/lean/ApSpec/Run1StaticPremiseAvailability.lean).
The focused investigation records its checked scope and audit. This is
not a whole-AP equivalence or a Thingsboard completion proof.

Before runtime implementation:

1. Prove every static GROUP original has a causally earlier actual pool
   reply, including current GEN, all-origin arms, calls and class markers.
   Prove processed/classRequested membership, not just availability.
2. Prove canonical self-application and packed/scalar result agreement
   for all actual tails, groups, paths and alias parts. Keep other legal
   shapes on the old path until justified.
3. Prove equal finite summary, selected-demand, source/sink and group
   closure. Keep every method-boundary demand pair. Group closure alone
   does not prove the required handoff.
4. Test both arrival orders, nested/multiple callers, recursion, changing
   groups, absent sources, late writers, disjoint fields, exact class
   markers, mixed alias parts and unchanged controller/exit behavior.
   Check actual run-2 demand handoff as a correctness gate.
5. Measure Thingsboard in the existing **12g / 1080 s** configuration,
   with Gradle `runProjectAnalyzer`, statics and aliases enabled. Use
   separate patches and give reached/completed/accepted iteration.

Count removed call handshakes, replies and unary joins; packed/scalar
outputs; suffix entries; EdgeDelta steps; raw summary and selected-demand
growth. For proved paths the removed protocols must execute zero times.
Explain remaining work. Report engine time and retained memory separately.
The run-1 target remains 60 seconds. A twofold path reduction is not a
promised twofold whole-engine speedup.

Projection/container tuning and restricted-run replay are deferred. The
exit correctness issue remains open. Do not apply the withdrawn ZERO
filter or move annotation-based controller checks to an unannotated root.
