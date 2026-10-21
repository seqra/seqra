# Run-1 cleaner work: implementation specification

Date: 21 October 2026. Status: **optimizations 1–5 selected; specification
and local formal model only**. The analyzer implementation is unchanged.

Use runtime `5d3a706cfaa8fa5620dab5c5b268d02b1a8432e2` as the reference.
Commit `b4948b4597120ca69261be347aa857171160b332` adds only the
[measurements](../reports/bidirectional-run1-single-premise-rules-performance-20261021.md)
and the [proposal](bidirectional-cleaner-intermediate-work-20261021.md).
This specification gives the implementation contract for its five numbered
optimizations. It takes precedence over schematic code in that proposal.

The latest measured results are unchanged:

| Project | Latest reached | Latest COMPLETE | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-2 TIMEOUT | run-1 | run-1 |
| conductor | run-1 TIMEOUT | none | none |
| thingsboard | run-1 OOM | none | none |
| Stirling-PDF | run-2 OOM | run-1 | run-1 |

## 1. Selected scope and semantic contract

| Selected optimization | Current behavior | Required new behavior |
| --- | --- | --- |
| 1. Merge static continuation images | One pending point and service registration per scalar current image | One point per complete context/header; exact image union separately for each pending group |
| 2. Execute each eligible GROUP return cleaner once per block | Full-member expansion can execute the same base/group operation many times | Execute its first occurrence in each eligible block and actual invocation suffix; preserve raw addresses |
| 3. Deduplicate complete intermediate cleaner results | Equal results enter the next cleaner more than once | Retain one complete result after each finished run-1 cleaner step |
| 4. Reuse immutable mark-exclusion headers | Duplicate additions and stable projection repeat normalization or atom-kind scans | Return the existing canonical header when permitted; store immutable FULL-atom metadata |
| 5. Avoid an empty static-summary copy | A fallback can copy a summary with empty old and new descriptor lists | Use the original summary in that local empty-to-empty case |

**No new analysis approximation is selected.** Preserve the existing
run-1 approximation and its later refinement. In particular, preserve:

- Complete source/sink condition admission and complete witnesses.
- The selected single run-1 premise and exact later FULL conjunctions.
- Field reads, overwrites, aliases, type filters, tails, field exclusions,
  ordinary FLOW mark exclusions, layers and primitive tracking.
- Initial-fact grammar, exact global class markers, static writer support,
  canonical static group answers and the three request sets.
- Existing request barriers, queue ownership, cancellation policy, cache
  managers, soft-reference policy and the `1,1,2,...` limit schedule.
- The current rule that ignores `trackFactsReachAnalysisEnd`.

Optimizations 1–3 apply to run-1 only. Optimization 2 also requires the
GROUP view and forward direction. Optimizations 4–5 preserve exact API
values; 4 can benefit later runs, while 5 remains in its existing run-1
fallback. Later FULL cleaner lists and their execution remain unchanged.

Internal point IDs, duplicate callback counts, allocation and batching
can change. Semantic observations must remain equal after a complete
fixed point: qualified source/sink occurrences and complete witness keys,
summary facts, DEMAND guidance and full call state. Do not require equal
budget-limited event prefixes or equal batch-dependent diagnostic counts.

The following are **not selected**: the separate per-position construction
template, whole-result cleaner memoization, packed multi-image interpreter
resumption, later-run publication replay, the PDF return-location fix,
cache rewrites and IR storage changes. In particular, optimization 2 does
not change full-member enumeration, rule admission or field lowering.

Follow [proof-guide.md](../../spec/proof-guide.md). If an implementation
requires a different field, rule, layer or supply contract, stop and state
that major change. Do not add a support label or change an abstraction to
make these optimizations easier to implement.

## 2. Ownership and patch boundaries

| Owner | Data after this change | Reason |
| --- | --- | --- |
| `Run1PointResume`, one method actor | Complete point directory, per-group immutable image roots, monotone IDs | Only the actor can replace payloads or resume program operations |
| `Run1StaticService` | Existing group availability, writers and pending `(owner,id,region,groups)` interests | Global routing needs no current-image payload or callback |
| GROUP call-form payload | Existing raw steps plus immutable prior-occurrence metadata for generated return rewriters | Share schedule metadata while keeping every continuation address stable |
| One `cleanChain` invocation and step | Temporary ordered exact result set | Remove duplicate future work without a cumulative history store |
| `MarkExclusions` | Existing immutable atoms/catalog plus private FULL classification | Avoid repeated interpretation of immutable atom keys |
| Existing summary/form owners | Existing summaries, filters and operations | Optimization 5 needs no new owner or cache |

`MethodEdgeStore`, ordinary request storage, summary storage and the run
manager keep their current responsibilities. Do not move static callbacks
or mutable actor state into any of those stores.

Production changes are limited to these existing files, plus focused
tests and a small private helper if needed:

```text
bidi/engine/Run1PointResume.kt             optimization 1
bidi/interp/CallPlan.kt                   optimization 2 metadata
jvm/bidi/interp/JIRCallPlanBuilder.kt     attach metadata after old lowering
jvm/bidi/interp/JIRRuleForms.kt          read-only GROUP-view predicate only
bidi/engine/RunMethodCallServices.kt      optimizations 2, 3 and 5
bidi/ap/MarkExclusions.kt                 optimization 4
```

Keep the existing exact `FlowAlgebra.mergeAdd` implementation. Do not
change the service protocol or add a new general-purpose cache. Account
for the new schedule array in existing form-storage diagnostics.

All Kotlin below is implementation-oriented pseudocode. Existing types
and names are used where available. Proposed private types are identified
explicitly. Production code and tests must enforce the stated contracts.

## 3. Optimization 1: merge pending static images

### 3.1 Complete key and exact payload

Replace only the `points` branch in `Run1PointResume`. Its new key omits
current path and current mx. It retains every other partition:

```kotlin
private data class PointKey(
    val site: ResumeSite,             // complete operation, step and phase
    val initial: InitialAp,           // existing original; do not normalize it
    val base: AccessPathBase,
    val tail: Tail,
    val fieldExclusion: ExclusionSet,
    val layer: Layer,
    val state: CallResumeState?,      // null differs from an explicit state
)

private class PendingFlowImages(private var root: FlowNode) {
    fun addAll(incoming: FlowNode, ap: ApManager) {
        root = FlowAlgebra.mergeAdd(root, incoming, ap.cancellation)
    }
    fun snapshotRoot(): FlowNode = root
}

private class PointRow(val id: Long, val key: PointKey) {
    val pending = HashMap<GroupId, PendingFlowImages>()
}
private data class DetachedAnswer(val group: GroupId, val images: FlowNode)

private val pointKeys = HashMap<PointKey, Long>()
private val points = HashMap<Long, PointRow>()
```

`CallResumeState` retains caller layer, nullable identity classification
and pending demotion. `ResumeSite` retains nested sites and complete action
identities. Never key only by statement index or AP base.

For each key and group, the root denotes exactly the submitted scalar
paths for that group. `mergeAdd` performs structural leaf union. It keeps
both a leaf at `p` and a leaf at `p.q`. Do not use `FlowGroup`, prefix
subsumption, ANY cover or a Cartesian product of groups and images.

An immutable root can be shared by several group wrappers. A later union
replaces only the selected wrapper's root. It cannot mutate another group
or a detached answer.

The existing reply clears current FLOW mx before materialization. Thus
the pending payload need not store it. This does not change `at.facts`,
ordinary FLOW, initial exclusions or writer selectors. Field E, tail and
layer remain in the complete key even when reachable run-1 facts have
empty field E.

```text
Same complete context:
  image a, mx={G1}, request H
  image b, mx={G2}, request H
  image c, mx={G2}, request K

Before: three scalar points.
After:  one row; pending[H]={a,b}; pending[K]={c}.
H can resume a and b. It cannot resume c.
```

### 3.2 Register without scalar expansion

```kotlin
fun request(at: OperationInput, groups: GroupSet) {
    val original = at.premise as? InitialAp ?: return
    val flow = at.facts as? FlowTree ?: return
    if (original.base != STATIC || original.mark !is MarkSlot.Star) return
    if (groups.isEmpty) return

    val key = PointKey(at.site, original, flow.base, flow.tail,
        flow.exclusion, flow.layer, at.callState)
    val id = pointKeys.getOrPut(key) {
        (++next).also { points[it] = PointRow(it, key) }
    }
    val row = points.getValue(id)
    var subscribe = GroupSet.EMPTY
    groups.forEach { group ->
        core.ap.cancellation.checkpoint()
        val old = row.pending[group]
        if (old == null) {
            row.pending[group] = PendingFlowImages(flow.root)
            subscribe += group
        } else old.addAll(flow.root, core.ap)
    }
    if (!subscribe.isEmpty) {
        core.staticBatch.interests += StaticInterest(
            core.key, id, original.toPattern(), subscribe)
    }
    core.requestWork()
}
```

Every image in this submitted FLOW root requested every group in this
call. A later request can add a different image/group association. Merge
its image even when no new interest is necessary.

One interest is sent when a row/group changes from absent to pending.
Further images for that pending group do not create interests. There is
no answered history and no second global reach store.

### 3.3 Detach an answer before callbacks

Keep the current `PointReady` dispatch order and the earlier bodies:

```text
calls -> writerInputs -> joinInputs -> direct -> points
```

Only the last branch changes. `ReadReady`, `WriterInterest`, exact global
markers and writer matching remain unchanged.

```kotlin
private fun answerPackedPoint(effect: StaticOwnerEffect.PointReady) {
    val row = checkNotNull(points[effect.id]) { "Unknown static point reply" }
    // A valid service answer consumes pending memberships exactly once.
    effect.supply.groups.forEach { group ->
        check(row.pending.containsKey(group))
    }
    val detached = ArrayList<DetachedAnswer>()
    effect.supply.groups.forEach { group ->
        val root = checkNotNull(row.pending.remove(group)).snapshotRoot()
        detached += DetachedAnswer(group, root)
    }
    if (row.pending.isEmpty()) {
        check(pointKeys.remove(row.key) == row.id)
        check(points.remove(row.id) === row)
    }
    // From here, read only detached immutable roots and row.key.
    for (answer in detached) {
        core.ap.cancellation.checkpoint()
        materializeScalarImages(row.key, answer.images,
            RegionGroups(effect.supply.region, GroupSet.of(answer.group)))
    }
    core.requestWork()
}
```

Detach **all** groups named by the reply before the first callback. This
keeps partial/multiple replies and any future reentrant test safe. The
current `installPoint` queues work; it does not execute a cleaner inline.

The service removes answered memberships before it emits a reply. The
actor keeps its pending payload until it handles that reply. New images
that arrive while the reply is queued belong to the same pending payload
and receive that answer.

After H drains, a new H image must create a new pending H payload and send
an interest again. If K keeps the row alive, use its existing row ID. If
the row was removed, allocate a fresh monotone ID. Never reset or recycle
the shared `next` counter. Keep the existing unknown-ID failure; do not
silently ignore stale replies or add an answered-ID cache.

### 3.4 Preserve scalar materialization

Pack storage, then enumerate scalar images for the old transfer. Do not
send one packed image tree into the interpreter in this iteration.

```kotlin
private fun materializeScalarImages(
    key: PointKey, images: FlowNode, supply: RegionGroups,
) {
    val actual = core.staticFacts(supply) // one group; existing construction
    FlowAlgebra.forEachLeaf(images, IntArray(0), core.ap.cancellation) { path, _ ->
        val scalarRoot = FlowAlgebra.prepend(core.ap, core.ap.path(path),
            FlowAlgebra.LEAF_NODE)
        val relation = core.ap.flowTree(key.base, key.layer,
            key.fieldExclusion, MarkExclusions.EMPTY, scalarRoot, key.tail)
        for (leaf in core.ops.leaves(actual)) {
            core.ap.cancellation.checkpoint()
            val premise = concreteInitial(leaf) // existing helper, unchanged
            val input = core.ap.factsOf(leaf.fact, leaf.exclusion, Layer.DEMAND)
            core.ops.applySummary(input, key.initial, relation, core.mode,
                object : ApOut {
                    override fun result(f: Facts) =
                        core.installPoint(premise, f, key.site, key.state)
                    override fun markRequest(mark: TaintMark) = Unit
                })
        }
    }
}
```

Keep the existing exact class-marker check and static-field initial
normalization in `concreteInitial`. Do not emit a class wildcard initial
or replace a known field/path with a different tail.

The scalar boundary avoids a new assumption about distribution through
local rules, cleaner phases or sink witnesses. It still removes per-image
point rows, continuation wrappers and global pending registrations.

### 3.5 Lifecycle and quiescence

| Situation | Required result |
| --- | --- |
| Duplicate image/group while pending | Exact leaf union; no new interest |
| Another group for the same image | Separate group payload; no cross-group answer |
| Supply before request | Existing service answers immediately through its queue |
| Image arrives while answer is queued | Include it when the actor detaches the payload |
| One of several groups answers | Remove only that group's payload |
| Last group answers | Remove both directory entries before scalar callbacks |
| Same key requested after removal | Fresh ID; old replies cannot address it |
| No corresponding supply ever arrives | Passive pending state; no polling loop |
| Cancellation during union or drain | Keep current cancellation policy; do not claim COMPLETE |
| Freeze/release | Clear both point maps at the existing hook; leave other cleanup order unchanged |

Do not count passive pending rows as runnable work. Keep the existing
batch replacement, method-queue barrier and `requestWork` paths. Do not
add a memory cap, periodic replay, transaction rollback or new cleanup
phase. Remove the obsolete scalar `PointContinuation` type if the final
reference check still finds no other users.

## 4. Optimization 2: skip repeated eligible return cleaners

### 4.1 Eligibility and barriers

Apply this optimization only to the compiler-generated
`Actions(RETURNED, REWRITTEN, rewriterSteps())` in the run-1 GROUP view.
Attach metadata **after all existing lowering**. Keep every raw step,
action ID and temporary key at its original position.

An eligible step is a plain `CleanStep.Clean` with:

```text
legal nonstatic base
selected mark != null
selected runtime atom is a genuine GROUP
```

Its key is `(base, GroupId)`. Path and reach remain on the first original
descriptor. They are not part of this selected nonstatic primitive's
FLOW effect; GROUP TAINT is unchanged by a selected cleaner.

Every `Field`, `Kill`, RemoveAll/null mark, STATIC cleaner, unsupported or
control atom, and every stage boundary is a barrier. Reset selection at
the barrier. A String-bytes field companion remains a barrier. Never
skip it, combine it with its preceding root or alter its temporary key.

Configured bound cleaners have no such metadata and keep their complete
sequence. FULL and reversed plans also have no metadata.

### 4.2 Compile prior-occurrence indices

A proposed private helper stores the immediately previous equal key
inside the same eligible block. Use `-1` for a first key or a barrier.

```kotlin
internal data class ReturnGroupKey(val base: AccessPathBase, val group: GroupId)

private fun eligibleReturnGroupKey(step: CleanStep, marks: MarkTable): ReturnGroupKey? {
    val cleaner = (step as? CleanStep.Clean)?.cleaner ?: return null
    when (cleaner.base) {
        AccessPathBase.Zero, AccessPathBase.ClassStatic -> return null
        is AccessPathBase.CleanerTemporary -> return null
        else -> Unit
    }
    val atom = cleaner.mark ?: return null
    val group = marks.groupId(atom) ?: return null // do not project FULL here
    return ReturnGroupKey(cleaner.base, group)
}

internal class ReturnGroupSchedule private constructor(private val previous: IntArray) {
    val size: Int get() = previous.size
    fun execute(step: Int, firstStep: Int): Boolean = previous[step] < firstStep
    fun storage(counts: FormStorageCounts) { counts.index(previous) }

    companion object {
        fun compile(raw: List<CleanStep>, marks: MarkTable): ReturnGroupSchedule? {
            val previous = IntArray(raw.size) { -1 }
            val last = HashMap<ReturnGroupKey, Int>()
            var hasDuplicate = false
            raw.forEachIndexed { i, step ->
                val key = eligibleReturnGroupKey(step, marks)
                if (key == null) last.clear()
                else {
                    previous[i] = last.put(key, i) ?: -1
                    hasDuplicate = hasDuplicate || previous[i] >= 0
                }
            }
            return if (hasDuplicate) ReturnGroupSchedule(previous) else null
        }
    }
}
```

The factory owns the fresh array. No mutable map survives compilation.
Classify keys once through the existing mark view/table; do not perform
atom-kind lookup again for every runtime fact.

Extend `CallStage.Actions` with optional private/internal schedule data.
The old public constructor keeps its existing default behavior. A named
GROUP-return factory attaches the schedule to the generated stage only.
Retain the immutable raw `steps` list.

```kotlin
class Actions private constructor(
    override val from: CallPoint,
    override val to: CallPoint,
    val steps: List<CleanStep>, // private constructor takes an owned snapshot
    internal val returnGroupSchedule: ReturnGroupSchedule?,
) : CallStage {
    constructor(from: CallPoint, to: CallPoint, steps: List<CleanStep>) :
        this(from, to, ownedList(steps), null)

    companion object {
        // Public factory: the JVM compiler is a different module.
        fun run1GroupReturnRewriter(steps: List<CleanStep>, marks: MarkTable): Actions {
            val raw = ownedList(steps)
            return Actions(RETURNED, REWRITTEN, raw,
                ReturnGroupSchedule.compile(raw, marks))
        }
    }

    override fun reversed(): Actions =
        Actions(to, from, steps.asReversed().map { it.reversed() })
        // The default constructor creates NO forward GROUP schedule.
    // Keep the current equals/hashCode implementations over from/to/raw steps.
}

// JIRRuleForms: read-only property over the existing private view.
internal val isRun1GroupView: Boolean get() = view == RuleView.RUN1_GROUP

// JIRCallPlanBuilder, after the old rewriterSteps() has completed:
val raw = rewriterSteps()
val actions = if (forms.isRun1GroupView)
    Actions.run1GroupReturnRewriter(raw, manager.marks)
else Actions(RETURNED, REWRITTEN, raw)
```

`isRun1GroupView`, the named factory and the key helper are proposed APIs.
Do not rewrite provider metadata, change admission/lowering order or
assign new full-mark ordinals. Metadata is derived and does not change
existing `Actions.equals/hashCode`. Separate GROUP/FULL form slots stay
separate. Include the private array once in storage accounting. Rebuilding
the same view must reproduce the same raw indices and schedule.

### 4.3 Execute relative to the actual suffix start

Pass the optional schedule to `cleanChain` from both ordinary stage
application and `CleanerInput` resumption. The latter already reloads the
raw `Actions` stage by its saved stage ordinal.

```kotlin
// Change cleanChain's first argument from raw steps to the Actions owner.
private fun cleanChain(actions: CallStage.Actions, /* existing arguments */) {
    val steps = actions.steps
    // Existing helper bodies and invocation state remain in this function.
}

val schedule = actions.returnGroupSchedule.takeIf {
    core.config.run1 && core.forward && core.mode.markMode == MarkMode.GROUP
}
check(schedule == null || schedule.size == steps.size)
for (stepIndex in firstStep until steps.size) {
    core.ap.cancellation.checkpoint()
    if (schedule != null && !schedule.execute(stepIndex, firstStep)) continue
    val phase = if (stepIndex == firstStep) firstPhase else CleanerPhase.INPUT
    executeExistingStep(steps[stepIndex], stepIndex, phase)
}

// Ordinary invocation and resumed suffix use the same Actions owner:
cleanChain(stage, fact, ctx, ordinal) { out += it }
cleanChain(actions, input, ctx, site.operation.stageOrdinal,
    site.step, site.phase) { out += it }
```

`firstStep` is the invocation's original start, not the previous executed
index. A barrier always executes. The raw start always executes, because
its previous equal occurrence is earlier than itself. Thus the saved
`firstPhase` remains attached to the same raw step.

```text
Raw indices:   0:G, 1:H, 2:G, 3:Field, 4:G, 5:G
Previous:     -1,  -1,   0,      -1,  -1,   4
Start at 0:   execute 0,1,3,4
Start at 2:   execute 2,3,4
Start at 5:   execute 5
```

The suffix rule also handles a defensive mid-block FLOW entry. A global
mask made only for start zero would incorrectly skip its first G.

### 4.4 Static and late-answer cases

For matching nonstatic FLOW, an eligible step adds G to mx and requests
G. Repeated additions have the same final union. For a GROUP TAINT value,
all eligible selected cleaners preserve the value.

A static original can request the same group at several cleaner sites.
The retained first site receives old or late supply. Its concrete TAINT/G
reply passes the rest of the eligible block unchanged and covers the
output from a deleted later site. Geometry, original, layer and complete
call state remain equal at the block exit. Compare that exit relation,
not the number of internal point registrations.

A current STATIC value is a different case. The existing wrapper can
publish or drop it at the first plain cleaner and omit it from `next`.
Keep that first descriptor, including path/reach and raw index. It is
always selected. No eligible step changes a nonstatic base into STATIC.
Do not bypass this wrapper under a disjoint-base shortcut.

## 5. Optimization 3: deduplicate finished step results

### 5.1 Complete equality and narrow lifetime

Make `PlanFact` a value key, or use an equivalent private value key:

```kotlin
private data class PlanFact(
    val premise: PremiseKey,
    val facts: Facts,
    val identityCandidate: Boolean?,
)
```

`Facts.equals` already retains kind, base, layer, tree, tail, field E and
FLOW mx. Keep the complete original premise and all three identity values.
The invocation fixes method, node, plan, stage, caller layer and resume
context. Do not share this collection across invocations, inputs, stages
or asynchronous replies. Do not introduce a visited history.

### 5.2 Replace only the completed-step collector

```kotlin
// Inside cleanChain, once for each EXECUTED step:
val next: MutableCollection<PlanFact> =
    if (core.config.run1) LinkedHashSet() else ArrayList()
for (input in current) {
    executeExistingBranches(input) { finished -> next.add(finished) }
}
current = if (core.config.run1) next.toList()
          else next as ArrayList<PlanFact> // this branch constructed that list
```

Replace each existing `next += planFact(...)` at that boundary. Keep first
arrival order. Later FULL keeps its existing ArrayList with no new list
copy or collector wrapper. The constructor branch makes the shown private
cast safe. An equivalent typed helper must retain that allocation behavior.

Do not deduplicate inside `Field` READ, CLEAN or WRITE, inside its
`statement()` result list, or across `applyStage` inputs. Finish the old
read/clean/write and temporary removal before admission to `next`. Keep
all same-step callbacks, cuts, filter calls and static write publication.

```java
Inner i = new Inner(); i.value = source();
Box x = new Box(); x.inner = i;
sanitize(x); // selected cleaner on x.inner.value
```

```text
F = I -> arg0.inner.[any]/G / DEMAND
Existing nested read/clean/write at limit 1 can produce [F,F].
Before: both copies execute the next cleaner.
After:  the current step still executes completely; next receives [F].
```

Do not merge different paths, layers, exclusions, premises or identity
values. Different FLOW exclusion subsets remain different keys. This
optimization removes equal results; it does not replace field transfer
or change the strength of an overwrite.

### 5.3 Effects and cancellation

Deduplication can omit duplicate **future-step** callbacks. The proof must
use semantic idempotence at the existing owners:

| Effect | Required invariant |
| --- | --- |
| Ordinary mark request | Same initial/group membership already merges |
| Static point request | Same complete context/group/image has one pending membership |
| Static writer/publication | Existing summary admission and writer selectors reject an equal semantic delta |
| Source/sink observation | Same qualified occurrence and complete witness is unchanged; distinct occurrences remain distinct |
| Unresolved observation | Existing qualified-input admission remains in place |

Do not infer that a callback is pure. If a future cleaner effect is
non-idempotent semantically, it must remain outside this elimination or
get a separate preservation design. Batch diagnostic counts can vary as
already permitted; distinct rule or report identities cannot disappear.

Retain cancellation checks at executed steps and processed inputs. A
cancelled prefix need not have the same callback count or partial output
as the old prefix. It must not be accepted as a completed fixed point.

## 6. Optimization 4: immutable exclusion-header fast paths

### 6.1 Metadata describes the normalized retained atoms

Add one private immutable classification to `MarkExclusions`:

```kotlin
private enum class FullAtoms { UNKNOWN, ABSENT, PRESENT }

class MarkExclusions private constructor(
    private val atoms: MarkSet,
    private val catalog: MarkTable?,
    private val fullAtoms: FullAtoms,
) { /* existing public operations */ }
```

| Factory result | Required classification |
| --- | --- |
| Empty | Existing catalog-independent `EMPTY`, ABSENT |
| Nonempty without a catalog | UNKNOWN |
| Bound nonempty result | PRESENT iff a retained atom is FULL in that exact catalog; otherwise ABSENT |

ZERO is not FULL. A GROUP can shadow a FULL atom of that group. Compute
metadata **after** removing such FULL atoms. If that removes the last
FULL, record ABSENT.

Keep equality/hash based on the same atom content as today. Do not add
catalog or metadata to semantic equality. Public arrays remain copies.
Do not infer atom kind from integer ranges or read an interner without
its existing monitor. Existing IDs have stable keys as the table grows;
no group-member inventory or extra global lookup table is needed.

### 6.2 Normalize once, then return unchanged headers

Retain this exact normalization:

```text
N_T(S) removes Full(t) iff Group(owner(t)) is also present in S.
It retains ZERO and every GROUP selector.
```

Compute group IDs and an initial FULL flag during the normal scan. If
groups exist, filter shadowed FULLs and compute the retained FULL flag
during that filter. Use existing synchronized `catalog.key(atom)` access.
Do not add a fresh scan merely to compute metadata.

```kotlin
private fun normalized(atoms: MarkSet, catalog: MarkTable?): MarkExclusions {
    if (atoms.isEmpty) return EMPTY
    if (catalog == null) return MarkExclusions(atoms, null, UNKNOWN)

    // Pseudocode: inspect each key under the existing catalog monitor.
    val scan = scanExistingAtomKeys(atoms, catalog) // group IDs + any FULL
    if (scan.groups.isEmpty())
        return bound(atoms, catalog, scan.hasFull)
    var retainedFull = false
    val kept = filterUsingExistingKeys(atoms, catalog) { key ->
        when (key) {
            ControlZero -> true
            is Group -> true
            is Full -> (key.mark.group !in scan.groups).also {
                if (it) retainedFull = true
            }
        }
    }
    return bound(kept, catalog, retainedFull)
}
```

The scan/bound helpers are private implementation details, not new
catalog APIs. Temporary local sets can follow the old normalizer. No
extra atom-kind directory survives the call.

```kotlin
operator fun plus(mark: TaintMark): MarkExclusions {
    val combined = atoms + mark
    if (combined === atoms) return this
    return normalized(combined, catalog)
}

fun union(other: MarkExclusions): MarkExclusions {
    val table = tableWith(other) // validate BEFORE any identity return
    val combined = atoms + other.atoms
    if (combined === atoms && catalog === table) return this
    return normalized(combined, table)
}

fun projected(table: MarkTable, mode: MarkMode): MarkExclusions {
    if (mode == MarkMode.FULL) return withCatalog(table)
    if (isEmpty) return EMPTY
    if (catalog === table && fullAtoms == ABSENT) return this
    return normalized(MarkSet(atoms.data.map {
        table.runtimeAtom(TaintMark(it), mode).id
    }.toIntArray()), table) // old GROUP fallback
}
```

`MarkSet.plus` already returns its old object for an unchanged raw set.
Use that fact; no new set algorithm is required. A raw duplicate is safe
for a catalog-free header, whose metadata remains UNKNOWN.

For union, unchanged atoms alone are insufficient. A catalog-free
`{Full(t),Group(g)}` can acquire a catalog where owner(t)=g. It must still
normalize, remove t and bind the result. Keep `catalog === table` in the
shortcut. Keep `plus(MarkSet)` on the validated union path.

### 6.3 Preserve the existing catalog boundaries

There are two different existing contracts. Do not combine them:

| Operation | Existing contract to preserve |
| --- | --- |
| `withCatalog`, union, intersection, subset and FULL attachment | Reject different bound catalog identities, even for equal raw IDs |
| `projected(table,GROUP)` | Keep its old fallback that interprets raw IDs with the supplied table, including a different bound source catalog |

The GROUP fallback must not reuse source-table ABSENT metadata. Do not
silently turn that fallback into a rejection in this optimization.

FULL **exclusion selectors** can include GROUP. They still exclude each
later FULL member. This differs from an executable FULL fact atom, for
which `runtimeAtom(..., FULL)` rejects GROUP. Do not apply the executable
rejection to exclusion headers.

`withCatalog` already has a same-catalog identity path. Preserve it.
`ApManager.intern(mx)` must still attach/validate the manager's catalog
before interning. Intersection, subset and filtering keep their current
algorithms; any newly built header must satisfy the metadata invariant.

An unchanged header is not permission to skip `runtimeFact`, cover,
filtering, DEMAND demotion or a request. Outside optimization 2's proved
block selection, a cleaner still requests G when mx already excludes G.

## 7. Optimization 5: avoid the local empty metadata copy

Keep both existing `cleanChain.statement` fallback conditions and their
descriptor predicates. Guard only the empty derived list:

```kotlin
val compiled = if (core.config.run1 && phase == CleanerPhase.READ &&
        summary.staticOperations.isEmpty()) {
    val id = core.operation(node, stageOrdinal).copy(substep = step)
    val operations = summary.edges
        .filter { it.edge.from.base == STATIC && it.edge.to.base != STATIC }
        .map { StaticOperation.Read(id, it.forward) }
    if (operations.isEmpty()) summary else summary.withStaticOperations(operations)
} else if (core.config.run1 && phase == CleanerPhase.WRITE &&
        summary.staticOperations.isEmpty()) {
    val id = core.operation(node, stageOrdinal).copy(substep = step)
    val operations = summary.edges
        .filter { it.edge.to.base == STATIC && it.edge.from.base != STATIC }
        .map { StaticOperation.Write(id,
            Pattern(it.edge.to, ExclusionSet.Empty), it.forward) }
    if (operations.isEmpty()) summary else summary.withStaticOperations(operations)
} else summary
```

Keep `applier.statement`, `Place`, cuts, filters, source/untouched
callbacks, result collection and temporary removal unchanged. Empty
static metadata does not mean an empty field transfer.

Keep the shared `StatementSummary.withStaticOperations` API unchanged.
Do not add a nonempty structural-equality shortcut. Do not move operation
ID creation or change JIR descriptor restaging. The local empty-to-empty
case has equal edges, filters, sources, conjunctions, target directories
and empty operation lists. Only wrapper/derived-directory allocation is
removed. No selected semantic key depends on that wrapper's identity.

## 8. Correctness arguments and formal model

### 8.1 Reference observations and proof composition

The reference is the pinned runtime, with complete successful processing.
Internal callback multiplicity and fresh IDs are not semantic facts.
Compare exact annotated facts and qualified rule/witness observations.
Never drop annotation fields from the comparison to make it pass.

Prove each step below before its production change. Then compose them:

```text
reference runtime
  = same scalar static answers from a factored pending store       (1)
  = same block-exit FLOW/TAINT and late-answer continuations        (2)
  = same complete next-step results and semantic effect closure    (3)
  = same exclusion-header content, ownership checks and projection (4)
  = same statement value and effects without an empty wrapper copy (5)
```

These equalities concern eventual semantic observations. Optimizations
1 and 2 intentionally change internal registrations. Optimization 3 can
reduce duplicate future effects. It is incorrect to demand equal raw
internal event sequences and then silently weaken another contract.

### 8.2 Static image factoring

Let a scalar reference item be `(header,group,path,mx)`. The abstraction
is executable:

```text
packed[header][group] = distinct exact paths of matching reference items
```

For every packed membership, retain a constructive reference-item
witness. Every scalar item maps to its matching packed membership.
Path and group stay associated; there is no cross-product.

For trie union, prove by induction on a relative path. At the root, the
FLOW leaf is Boolean union. For a child present on one side, the child
is retained. For a child present on both sides, apply the induction
hypothesis. Sorted accessor merge retains all child cases. Shared node
identities can reuse a relative merge result; traversal still visits each
distinct prefix path. Thus `leaves(mergeAdd(a,b))` is exact set union.

Registration preserves the abstraction by that union law. A reply selects
exactly its groups. Removing their payloads preserves every other group.
Empty-row removal changes no membership. Scalar materialization uses the
same relation with empty mx as the reference, with the same current
base/path/tail/E/layer and original. Therefore each output has a reference
output witness, and each reference output is still emitted.

The service sees the same existential field/group requests. Its writer
and group-availability transitions are unchanged. Duplicate interests do
not create new group availability. Queued replies use the payload current
at actor handling; later requests after a drain subscribe again. Detaching
roots and never reusing IDs preserve answer isolation. Valid service
execution answers each pending membership once; unexpected IDs retain
their failure rather than becoming a new semantic recovery path.

### 8.3 Eligible block selection

For matching nonstatic FLOW, each selected primitive changes only mx by
set union with G and requests G. Union is associative, commutative and
idempotent. No eligible step changes geometry or base. A selected GROUP
TAINT cleaner is identity. Therefore one retained key produces the same
block-exit FLOW/TAINT relation and ordinary requested-group set.

If the last previous equal index is p, it lies in the current suffix
exactly when `p >= firstStep`. Execute when `p < firstStep`. Clearing the
compiler map at a barrier makes the same proof local to each block.
Preserving raw indices proves that saved resumption coordinates still
name the same operation and phase. The first descriptor always remains.

For a static original, the first retained G continuation provides the
same concrete G output at block exit as each removed later G point.
All eligible suffix cleaners preserve that TAINT/G output. Keep every
image, original, layer and call state fixed in this proof. The STATIC
current-value publish/drop case terminates at the first retained step,
which has its original descriptor. Barriers end the proof region.

### 8.4 Intermediate result deduplication

At a completed step, exact equality partitions the next list into
classes. Keep the first member of each class. The next operation receives
the same set of complete keys. Equal keys have equal primitive results;
their semantic requests and publications join at the existing idempotent
owners. Induction over remaining executed steps preserves result and
effect closure. No induction crosses a new invocation or different
saved state. Same-step effects occur before deduplication and remain.

The production proof must establish that idempotent-owner correspondence
for each effect in section 5.3. A generic pure-function list lemma alone
does not prove the mutable interpreter. Preserve distinct source/sink
occurrences and full witness keys in the reference controls.

### 8.5 Header and summary identities

The existing constructive normalization model proves normalization
idempotence and its typed GROUP/FULL denotation. A normalized unchanged
raw addition is identity. A union identity also requires unchanged catalog
ownership. Metadata ABSENT proves same-catalog GROUP projection is already
at its fixed point. Compute the flag from the retained normalized atoms;
UNKNOWN cannot provide that premise. Keep the old foreign-catalog fallback
and all other rejection branches exactly.

The empty summary copy replaces an empty descriptor list by an empty
list and keeps every other immutable field. The original wrapper has the
same interpreter-visible value, order, filters and effects. Nonempty
descriptors and their identities do not enter this argument.

### 8.6 Kernel-checked local laws and limits

The constructive model is
[Run1CleanerWork.lean](../../spec/lean/ApSpec/Run1CleanerWork.lean), with
[run1-cleaner-work-audit.lean](../../spec/lean/run1-cleaner-work-audit.lean).
It reuses
[ImmutableGroupNormalization.lean](../../spec/lean/ApSpec/ImmutableGroupNormalization.lean).

The 26 checked declarations cover:

- Exact packed membership, duplicate offers, mx erasure and no group/image
  cross-product.
- Answer membership, partial drain, empty-row pruning, late offers and
  scalar callback union.
- Eligible-key request/exclusion equality, retention of the first key,
  suffix-mask arithmetic, barrier reset and a finite repeated-key count.
- Identity of TAINT suffix replies at block exit.
- Exact result/effect membership after full-key duplicate removal.
- Canonical duplicate addition, cached FULL-flag validity and stable
  projection, plus the empty summary value law.
- Five executable finite controls.

The axiom audit must contain no `Classical.choice`, `sorryAx` or new local
axiom. Standard Lean extensionality/quotient foundations can appear. The
model uses lists and decidable equality; witnesses are executable.

Both checks below pass for this specification. The audit reports only
standard `propext`/`Quot.sound` dependencies where foundations are needed.
It reports no `Classical.choice` or `sorryAx`.

```bash
cd spec/lean
lake build ApSpec.Run1CleanerWork
lake env lean run1-cleaner-work-audit.lean
```

This is **not** a whole-analyzer proof. Complete-header decoding, exact
Kotlin trie refinement, metadata compilation, scalar AP transfer, actor
ID/service lifecycle, stateful effect admission and reporting correspondence
still require the source proofs and independent production controls above.
The TAINT suffix law encodes the current selected GROUP primitive; it does
not prove that primitive from Kotlin. The header model encodes complete
contexts; it cannot detect a missing Kotlin field on its own. Do not claim
whole-engine no-FN or termination from these local theorems.

## 9. Implementation sequence and required controls

Use proof-first development. Add independent reference controls before
each production change. Do not build expected results with the new helper.

1. Keep the scalar pending and raw cleaner references. Add the semantic
   observation decoder and complete-key partition controls.
2. Implement 4 and 5. Check old normalization/projection and local summary
   value/effect equality before using identity returns.
3. Implement 3 at completed run-1 step outputs. Check the nested convergence
   example and all negative key distinctions.
4. Implement 2 after unchanged GROUP lowering. Check all suffix starts,
   barriers, raw addresses and late static G replies.
5. Implement 1 with exact trie payloads and scalar drain. Check the service
   lifecycle and every other continuation branch.
6. Combine all five and run the existing configured project campaign. Keep
   correctness failures visible and report latest reached/COMPLETE/accepted
   iteration separately.

| Area | Required focused controls |
| --- | --- |
| Static payload | Independent scalar set versus exact trie union; duplicate, parent/child and shared-subtree paths; several groups; mx variants |
| Key partition | Different originals/sites/phases/actions/base/tail/E/layer/state remain separate; all identity values and pending demotion |
| Reply lifecycle | Supply before/after request; queued late image; partial/multiple groups; drain/re-register with retained/fresh ID; unknown ID |
| Static ownership | Earlier call/writer/join/direct dispatch unchanged; no callback/FlowNode in the service; passive pending is quiescent |
| Schedule | Independent prefix scan versus prior-index array for every suffix start; mixed keys and barriers; first STATIC early exit |
| Saved addresses | Original CleanerInput step/phase and OperationId.substep; unchanged field TemporaryKeys; deterministic form rebuild |
| Cleaner equality | Nested `[F,F]` convergence; different premises, layers, field E, FLOW mx, paths and null/true/false identity |
| Effect preservation | Same-step callbacks all run; duplicate future requests/publications are semantically idempotent; distinct rule/witness occurrences survive; include `X,Y,X` and both distinct-input arrival orders |
| Header | Empty, ZERO, GROUP, FULL, mixed and shadowed FULL; bound/free catalogs; same raw ID with different kinds in two tables |
| Catalog | Conflicting bound union/intersection/subset/FULL attachment still fail; GROUP cross-catalog fallback matches the old algorithm |
| Later FULL | GROUP exclusion selectors still match FULL members; executable FULL still rejects GROUP; raw/reversed cleaner plans are unchanged |
| Empty copy | READ/WRITE empty-to-empty returns original; nonempty derivation and existing descriptors preserve old identities and effects |
| Release | Cancellation in union/drain/cleaner execution; existing freeze/clear hook; no retained row, schedule callback or actor after release |
| Combined | Representative premises; field-overwrite guidance; static getter/writer/cleaner/sink path; source/sink complete conditions through run-1, run-2 and run-3 |

Use the existing common/JVM integration fixtures where possible. Expected
new focused suites can be named `StaticPointImagePackingTest`,
`ReturnGroupScheduleTest`, `CleanerStepDeduplicationTest`,
`MarkExclusionsFastPathTest` and `EmptyStaticSummaryFallbackTest`. These
are planned tests; they have not run as part of this specification task.

Storage controls can use synthetic headers outside the reachable run-1
grammar, but label them as partition tests. Also keep reachable program
examples that enforce the pinned grammar. Do not turn a synthetic storage
case into permission to emit a new initial shape.

## 10. Complexity and acceptance

Let H be distinct complete pending headers, U pending header/group pairs,
and L the distinct header/group/path memberships. Optimization 1 retains
H point rows, U payload headers and exact tries for L memberships, instead
of per-image point/service wrappers. It does not remove required paths,
other continuation kinds or the static writer store. The number of scalar
materialization invocations remains proportional to answered image/group
memberships. CPU cost also depends on path and AP transfer work.

For K eligible cleaner steps and D distinct keys in one block/suffix,
optimization 2 executes D primitive operations instead of K. The compiled
array uses one integer per raw step and needs one comparison per visited
step. Full-member lowering and the raw scan remain. Barriers create new
blocks; do not turn a local D bound into a whole-plan group bound.

Optimization 3 processes the number of distinct complete intermediate
keys at the next step, rather than list multiplicity. It cannot remove
distinct FLOW exclusion subsets. Optimization 4 avoids repeated scans
for unchanged canonical headers. Optimization 5 avoids one unnecessary
wrapper and derived directories in its exact empty case.

The measured thingsboard actor's 63746 rows versus 3309 header keys is a
local directory count, not a predicted byte saving. No actual eligible
collapse factor, intermediate duplicate ratio or cache-rebuild rate was
measured. This specification does not promise the 60-second target.

Before acceptance, require the focused controls, applicable existing
common/JVM/application suites and strict finding comparison with fresh
main. Use only the configured direct Gradle project runs from `task.md`.
Preserve their memory/time settings and report the static-disable flag.
The old campaign used `disableRun1Statics=false`; do not attribute its
static owner counts to a disabled-statics experiment.

Report engine time, latest reached/COMPLETE/accepted run, unique summary
and request state, pending rows/memberships, executed/skipped cleaner
steps, intermediate multiplicity, CPU and allocation. If optional existing
form-cache statistics are used, identify that diagnostic setting and keep
its scope separate. Do not infer rebuild counts from `withForm` ancestry.
Do not add a new retained diagnostic history as an implementation shortcut.

Retain the two strict PDF return-location failures and 525 export failures
as separate known issues until their own fix is selected. A speedup or a
passing local proof cannot waive those failures. No unrelated core fix is
part of these five optimizations.
