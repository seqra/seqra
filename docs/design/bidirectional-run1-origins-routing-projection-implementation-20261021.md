# Run-1 origins, static routing and GROUP projection: implementation specification

Date: 21 October 2026. Status: **selected; runtime implementation pending**.

This document specifies the three selected items from the
[next-iteration proposal](bidirectional-work-avoidance-next-iteration-20261021.md):

1. Emit each contributing run-1 origin.
2. Remove impossible exact-class/field routing pairs.
3. Reuse unchanged canonical GROUP projections.

Use runtime `51dbc4d66fb1bf1b56071dbf18c90c5909e3146b` as the reference.
The core at document revision `6b23203c0` is unchanged from that runtime.
Follow [proof-guide.md](../../spec/proof-guide.md). Kotlin below specifies
the proposed implementation; it is not code already present in the engine.

The [latest measurements](../reports/bidirectional-cleaner-work-performance-20261021.md)
describe the reference, not this candidate:

| Project | Latest reached | Latest COMPLETE | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-6 TIMEOUT | run-5 | run-5 |
| conductor | run-2 OOM | run-1 | run-1 |
| thingsboard | run-1 OOM | none | none |
| Stirling-PDF | run-2 OOM | run-1 | run-1 |

## 1. Semantic changes and preserved behavior

| Item | Current behavior | Required behavior | Semantic status |
| --- | --- | --- | --- |
| All contributing origins | A completed local binding emits `repr(I1,I2) -> C` | Emit `I1 -> C` and `I2 -> C`; retain every distinct nonzero initial | **Selected semantic correction.** More run-1 candidates and complete per-member DEMAND pairs are possible |
| Factored origin processing | Complete binding products are enumerated before representative selection | Keep origin choices separately within each compatible proof/header partition; emit the same arms as scalar all-origin emission | Equivalent to the corrected all-origin contract |
| Exact-class/field routing | Try pairs which the existing matcher always rejects | Omit those pairs; use prefix lookup for exact-class requests | Equivalent service effects and group state |
| GROUP projection | Rebuild canonical leaves/wrappers; project twice on a specific immediate callback path | Reuse canonical identities; omit the second mark walk only on that audited path | Equivalent typed values, canonicalization and boundary effects |

The first item is not a claim of equivalence to representative-only run-1.
It restores the demand pair for each member of a completed reference ND
dependency. Ordinary edges here have one premise; they can be DEMAND.
GROUP conclusions remain DEMAND.

Preserve the following mechanisms:

- Complete source/sink condition admission, NNF choices, rule/action and
  source/sink occurrence identities, source seed permissions and complete
  sink witnesses. Preserve original unary versus MULTI source geometry.
- Field transfer, overwrites, aliases, field limits, exclusions, primitive
  tracking, cleaner requests, selected GROUP cleaner behavior and RemoveAll.
- Run-1 initial grammar, exact global class markers, static read/write
  interception, point resumption, canonical field/group answers and the
  three disjoint request sets.
- Later FULL conjunctions, E6 joins, caller binding, demand matching,
  handoff normalization, reporting and the `1,1,2,...` field-limit schedule.
- Queue ownership, request barriers, cancellation and the selected policy
  which treats `trackFactsReachAnalysisEnd` as empty.

Do not implement the unselected summary catch-up change, lazy conclusion
containers, static supply batching/census, PDF return-location fix, IR or
cache/reference-manager changes in this iteration. Do not add grounding
support, writer validation or another field approximation.

For projection, the selected implementation uses local identity reuse and
one immediate producer/consumer contract. A token on every result, a
global identity cache, new mark metadata and a general trusted-Facts API
are not selected. Unknown inputs retain the current projection path.

Compare semantic observations after complete closure. Duplicate callback
counts, internal IDs and budget-limited progress can differ. The all-origin
correction can also add semantic candidates; the two optimizations must
preserve the corrected contract rather than the old representative result.

## 2. Files and ownership

All production paths in this table are relative to
`core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/`.
New helper names below are proposed private/internal APIs.

| File or owner | Required change |
| --- | --- |
| `bidi/engine/RunMethodLocalServices.kt` | Select the run-1 result stores; fan out complete origins; keep witness and FULL paths separate |
| New `bidi/store/Run1OriginConjunctionStore.kt` | Factored independent-slot result admission, owned by one method actor |
| New `bidi/store/Run1OriginPredicateStore.kt` | Factored NNF source-result admission, owned by one method actor |
| `bidi/store/ConjunctionStore.kt` | Expose complete bindings without combining their premises for witness consumers; retain exact FULL combination |
| `bidi/store/RulePredicateStore.kt` | Expose the existing complete proof/inputs without representative selection for witness consumers; retain FULL combination |
| `bidi/ap/PremiseCombination.kt` | Remove `Run1Premises` and its representative comparator when callers are migrated; retain `ExactPremises` |
| `bidi/engine/Run1StaticService.kt` | Six local routing changes; expose existing trie prefix lookup through `Regions` |
| `bidi/ap/ApManager.kt` | Reuse normalized FLOW/TAINT wrappers and receiving-manager leaf payloads |
| `bidi/engine/RunMethodAnalyzer.kt` | Add the narrowly scoped consumer for an immediate `run1Policy` result |
| Tests and the referenced specs/proof map | Add the independent controls and update the selected premise policy |

Do not move rule proof state into `MethodEdgeStore`, `SummaryStorage`, the
static service or `ApManager`. The new result stores retain only local
accepted inputs, proof/header partitions, versions and emitted-origin
membership. They retain no actor callbacks or compiled method forms.
Drop them with the existing local services in `freeze()`.

The service lock and the existing owner-directed effect protocol remain.
Projection state remains local to each call and the existing interners.
There is no new cumulative cache or per-Facts certificate.

## 3. Exact origin emission contract

An input is accepted only after the existing mark/position check returns
`MarkCheck.Holds`. A request or an unmatched fact is not an accepted input.
In run-1 its premise must be one canonical `InitialAp` from this manager,
with empty field exclusion. Keep its complete base, path, tail and mark.

For one complete compatible binding `B`, define:

```kotlin
private fun checkedRun1Initial(premise: PremiseKey): InitialAp {
    check(premise is InitialAp)
    check(premise.manager === ap)
    check(premise.exclusion == ExclusionSet.Empty)
    return premise
}

fun scalarOrigins(B: List<Input>): Set<InitialAp> {
    check(B.isNotEmpty()) // unconditional GEN keeps its existing path
    val nonzero = B.mapTo(LinkedHashSet()) { checkedRun1Initial(it.premise) }
        .filterTo(LinkedHashSet()) { !it.isZero }
    return if (nonzero.isEmpty()) setOf(ap.zero) else nonzero
}

// Reference oracle only. Production must not enumerate origin tuples.
for (B in allCompleteCompatibleBindings) {
    val C = existingFixedTarget(action, B.layer)
    for (I in scalarOrigins(B)) emit(I, forceRun1Layer(I, C))
}
```

No `PremiseSet` or run-1 E6 binding is constructed by this result path.
`{ZERO,I}` contributes only `I`. Repeated `I` contributes one arm.

**ZERO rule:** emit `ZERO -> C` only if one complete compatible cube has an
accepted binding made entirely of ZERO-premise edges, with the existing
action/seed permission. Do not infer this from control-flow reachability,
one ZERO leaf, pooled OR evidence or an empty set of nonzero origins.

```java
Object relay(Object a, Object b) {
    return combine(a, b);
}
// Rule: CM(a,(G,T)) AND CM(b,(G,U)) -> AssignMark(result,(G,V))
```

```text
Run-1: CM(a,G) AND CM(b,G); fixed C = return/G, DEMAND
  I_a -> a/G, I_b -> b/G
  before: repr(I_a,I_b) -> C
  after:  I_a -> C, I_b -> C

  ZERO -> a/G, I_b -> b/G  => I_b -> C; no ZERO -> C
  ZERO -> a/G, ZERO -> b/G => ZERO -> C
  ZERO -> a/G, no b/G     => no result

(A AND B) OR (C AND D):
  A:{ZERO}, B:{I_b}, C:{I_c}, D:{ZERO}
  => I_b -> result, I_c -> result; no ZERO -> result
```

### 3.1 Separate result admission from witness admission

The existing stores are also used for sinks. Keep complete input tuples
and their proof keys there. Add binding-returning entry points which do
not compute a combined premise:

```kotlin
// ConjunctionStore: existing KaryJoin and input/layer semantics.
class CompleteBinding(val layer: Layer, val inputs: List<Input>)
fun addBinding(/* existing scope, arity, slot, input */): List<CompleteBinding>

// RulePredicateStore: existing compatibility, input union and root keys.
class CompletePredicateBinding(
    val proof: ProofKey,
    val layer: Layer,
    val leaves: List<Int>,
    val inputs: List<Input>,
)
fun addBinding(/* existing arguments */, emit: (CompletePredicateBinding) -> Unit)

// Keep the old exact API for FULL clients, implemented over addBinding.
fun add(/* existing arguments */, emit: (Combination) -> Unit) = addBinding(/* ... */) { B ->
    emit(Combination(/* proof if applicable */,
        exactPremises.combine(B.inputs.map { it.premise }), B.layer, /* ... */ B.inputs))
}
```

Existing sink callbacks use `addBinding` and construct their complete
`SinkEdge` list as today. They do not emit one witness per unary arm.
The FULL source/conjunction paths use exact combination as today.
The new run-1 source/result paths use the factored APIs below. Do not
route those paths through `addBinding`'s unchanged Cartesian join.

`MethodLocalServices.conjunctions` must still expose the existing store
needed by later E6 callers. It is not replaced with the run-1 result store.

## 4. Factored result storage and propagation

### 4.1 Stored keys and input domains

For results, origin choices are independent after the following complete
partition has been fixed. Keep each NNF proof shape; do not pre-expand the
predicate into cubes. An active proof shape identifies one supported cube.

```kotlin
private data class Scope(val occurrence: Any, val statement: CommonInst)
private data class LeafHeader(
    val leaf: Int,              // canonical predicate leaf, or explicit slot
    val layer: Layer,
    val factGroup: GroupKey?,   // preserve the complete checked-fact header
)
private typealias Domain = PersistentMap<InitialAp, VersionedInput>
private typealias Domains = PersistentMap<Int, Domain>

private class Shape(
    val proof: Proof,           // same leaf/AND/OR structure and choice IDs
    val headers: PersistentMap<Int, LeafHeader>,
)
private class SupportedShape(val shape: Shape, val choices: Domains, val version: Long)
```

Unlike the current `RulePredicateStore.Header`, `LeafHeader` omits the
premise. The domain retains that premise as its key. Everything else in
the current header remains a partition. Do not group by rule ID or mark
group alone. Inputs from different initials must never be merged into one
checked input or one witness fact.

For one `(scope, leaf, layer, factGroup, initial)`, use the current input
union rule: merge only compatible Facts with `mergeAddDelta`. Retain the
old version if no annotation grows. Never merge different initials,
layers or fact-group headers. Accepted input maps are monotone snapshots.

| Storage | Organization | Lifetime and purpose |
| --- | --- | --- |
| Explicit result slots | Scope -> slot -> header -> initial/input map | One actor/run; determines complete independent slots without origin tuples |
| NNF result nodes | Scope -> node -> shape -> per-leaf domains/version | One actor/run; retains existing proof choices and shared-leaf compatibility |
| Root result admission | Scope/proof/headers/action -> emitted origin set and ZERO flag | One actor/run; emits late origins once for the fixed target |
| Sink/FULL stores | Existing complete binding structures | Existing lifetime; keep exact witness and FULL semantics |

Do not retain a second list of completed origin tuples. Do not attach
origin sets to executable edges; every emitted edge remains atomic.

### 4.2 Explicit conjunctions

Use header partitions for each explicit literal slot. Enumerating different
header/layer partitions is allowed; enumerating origins within one slot
product is not. Repeated explicit literals retain their separate slots,
as in the existing `ConjunctionStore` contract.

```kotlin
fun addOriginInput(scope: Scope, arity: Int, slot: Int, input: Input, emit: (ReadyCube) -> Unit) {
    val initial = checkedRun1Initial(input.premise)
    val header = LeafHeader(slot, input.layer, input.facts?.groupKey)
    if (!slots(scope).mergeInput(slot, header, initial, input)) return

    for (headers in affectedCompleteHeaderPartitions(scope, slot, header)) {
        cancellation.checkpoint()
        // One domain for each required explicit slot; no tuple of initials.
        val domains = headers.map { slots(scope).domain(it) }
        if (domains.any { it.isEmpty() }) continue
        emit(readyExplicitCube(scope, headers, domains))
    }
}
```

The scope fixes arity and the existing alternative/statement identity.
Preserve the current arity/index validation and complete effective-layer
join. A partial partition emits nothing.

### 4.3 NNF source predicates

Adapt the existing incremental proof DAG for a source-result-only store.
Keep its iterative queue, proof hashing/interning, parent order, canonical
leaf IDs, OR branch IDs and header checks. Change its domain representation:

```kotlin
fun onLeaf(predicate: RulePredicate, leaf: Int, accepted: Input) {
    val I = checkedRun1Initial(accepted.premise)
    val H = LeafHeader(leaf, accepted.layer, accepted.facts?.groupKey)
    val D = standingLeafInputs.mergeAndSnapshot(H, I, accepted) ?: return
    publish(predicate.leafNode(leaf), leafShape(H), domains(leaf to D))
}

fun onOr(parent: Int, child: SupportedShape) {
    publish(parent, existingOrShape(parent, child.shape), child.choices)
}

fun joinAndShapes(children: List<SupportedShape>): SupportedShape? {
    val headers = compatibleHeaders(children) ?: return null
    val domains = persistentDomainBuilder()
    for (leaf in headers.keys) {
        val mentioned = children.filter { leaf in it.shape.headers }
        // A shared canonical leaf must select the SAME initial everywhere.
        val keys = intersectInitialKeys(mentioned.map { it.choices.getValue(leaf) })
        if (keys.isEmpty()) return null
        domains[leaf] = keys.associateWith { I ->
            mergeSameHeaderInputVersions(mentioned.map { it.choices.getValue(leaf).getValue(I) })
        }
    }
    return supported(existingAndShape(children, headers), domains.build())
}
```

`compatibleHeaders` rejects unequal layers or complete fact-group headers
at a shared leaf. Disjoint leaves remain independent. AND combines child
**proof/header shapes** as today; it does not multiply their origin maps.
OR does not union domains across different proof choices.

For each fixed proof/header shape, its domain product must denote exactly
the accepted scalar bindings of that shape. This is the adapter invariant:
leaf acceptance is local; the existing cross-child constraint is equality
at repeated canonical leaves. Intersect those domains before publication.
For the same shape, accepted leaf inputs are standing choices, so each
combination of its distinct leaf choices is permitted. This justifies
unioning a growing domain with the previous domain at that same shape.
If a new implementation adds any cross-leaf constraint, this invariant
must be revised and proved before it can use this representation.

The proof is by node construction. A leaf is its accepted domain. OR fixes
the selected child. AND combines independent distinct leaves and intersects
the common ones with identical headers. Every constructed tuple gives an
old compatible binding, and every old compatible binding chooses an entry
in these same domains. This proof is about origin factorization, not
permission to weaken conditions or change NNF proof identity.

At a publication, compare accepted membership and merged input content
with the old snapshot before advancing its version. A newly allocated map
or proof wrapper is not growth. Incrementing a version only because an
AND child was visited would resubmit unchanged work around method cycles.
Retain the existing `mergeAddDelta == null` no-growth check for Facts.

### 4.4 Root deltas and exact ZERO admission

Construct `ReadyCube` only from a complete explicit partition or complete
NNF root shape. Its constructor is private to the result store. It must
not accept arbitrary leaf pools supplied by a caller.

```kotlin
fun emitReady(cube: ReadyCube, action: Action, emit: (InitialAp, Layer) -> Unit) {
    check(cube.domains.isNotEmpty()) // empty/unconditional predicates use the old GEN path
    if (!existingActionSeedPermission(cube.proof, action)) return
    val state = results.getOrPut(cube.resultKey(action)) { EmittedOrigins() }

    for (domain in cube.domains.values) for (I in domain.keys) {
        cancellation.checkpoint()
        if (!I.isZero && state.nonzero.add(I)) emit(I, cube.effectiveLayer)
    }

    // The SAME complete compatible cube has a ZERO choice at every leaf.
    val zeroPossible = cube.domains.values.all { it.containsKey(ap.zero) }
    if (zeroPossible && !state.zero) {
        // Choosing ZERO at each distinct leaf constructs the full binding.
        // A repeated leaf uses the same ZERO choice and agreed header.
        state.zero = true
        emit(ap.zero, cube.effectiveLayer)
    }
}
```

This `all` test is valid only because the private `ReadyCube` invariant
has established the complete compatible domain product. It is not a check
over all leaves of an OR expression. The scalar all-ZERO binding is a
constructive proof witness; no runtime support label or retained witness
tuple is required. Empty/unconditional predicates stay on the old GEN path.

`resultKey` includes the existing scope, proof/alternative, headers and
action identity. The action target and effective layer are fixed there.
If an API permits a changing result payload, include its ordinary result
partition/version and replay the new result to all standing origins.
The current fixed-target APIs need no such mutable result payload.

On first completion, emit every distinct nonzero origin and ZERO only
under the rule above. A late origin gets its arm even if another origin
already emitted the target. A later ZERO choice can enable the ZERO arm.
An unchanged input or input-Facts growth without a new result-origin
membership does not enqueue that fixed arm again.

`publish` must retain monotone domain snapshots and enqueue only genuine
membership/input-version growth. Before processing a queued version, skip
it if a newer version replaced it. Parent/root result deltas use the same
rule. Method-edge and summary admission remain the final existing delta
checks. There is no replay on an unchanged node merely because it was read.

### 4.5 Engine integration

Create the two new stores only for forward run-1. Retain the existing
stores for sinks and later FULL paths. Remove the representative policy
after all result callbacks have migrated.

For source roots, preserve `allowSingle || cube.leaves.size >= 2` before
offering any action. The existing seed permission is evaluated for each
action using the original MULTI transfer and this cube's canonical leaves:

```kotlin
fun sourceActionAllowed(cube: ReadyCube, action: SourceAction): Boolean =
    !core.config.seededSources || cube.leaves.any { index ->
        core.config.seeds.allowsSource(core.key, node,
            source.transfer(selected.leaves[index], action, multi = true).forward, action.id)
    }
```

Explicit `ConjunctiveEdge` results keep their current admission; they do
not acquire a new source-seed test. Their `existingActionSeedPermission`
in the generic emission sketch is always true.

```kotlin
// RunMethodLocalServices: explicit ConjunctiveEdge result path.
when (val checked = ops.checkMark(facts, literal, mode, unchangedStaticInitial(premise))) {
    MarkCheck.None -> Unit
    is MarkCheck.Request -> core.needGroups(at, checked.mark)
    is MarkCheck.Holds -> {
        val input = Input(premise, checked.facts.layer, checked.facts)
        if (core.config.run1) run1Conjunctions.addOrigins(edge, node, arity, slot, input) { I, layer ->
            val C = ops.targetTree(edge.target, layer, mode)
            sink(I, core.forceRun1Layer(I, C))
        }
        else existingExactConjunctionResult(edge, node, slot, input, sink)
    }
}

// Source MULTI result path, including GROUP-folded one-leaf MULTI.
run1Predicates.addOrigins(source.id, node, selected, leaf, input, source.actions,
    allowSingle, existingSeedPermission) { action, I, layer ->
    val C = ops.targetTree(action.target, layer, mode)
    sink(I, core.forceRun1Layer(I, C))
}

// Sink path, in either run mode: the complete binding is the witness.
predicateStore.addBinding(/* existing scope and checked input */) { B ->
    witness(core, sinkRule, node, B.inputs.map { SinkEdge(it.premise, it.layer, it.facts!!) }, B.proof)
    fired += sinkRule to B.layer
}
```

Compute a fixed target once per ready action/layer callback when several
origins are emitted together; the snippets show per-arm policy application.
Do not reclassify MULTI as unary after GROUP folding. The original unary
source path continues to use its declared anchor and original Facts.
Do not apply its transfer to the checked AP slice instead.

No change is required in `HandOff.toBackward`: it now sees every unary
arm, so its existing conclusion/member loop exports every complete
`(demand(C), demand(I))` pair. Keep internal sink/dead-local seeding as
today. Later runs retain exact rule reversal and exact ND caller joins.

## 5. Remove impossible exact-class/field routing pairs

### 5.1 Invariants and example

```kotlin
private fun isExactClassPoint(p: Pattern): Boolean =
    p.fact.base == STATIC && p.fact.path.size == 1 && p.fact.tail == Tail.EXACT
```

`classInterests` and `classRequested` contain only these exact class
points. A `FieldState.region` is the canonical `S.C.f.*` with empty E.
Raw field/deeper supplies and writers have at least two accessors.

```text
Q = S.C / EXACT / G          exact global marker
F = S.C.f / STAR / G         static field region

Field supply F; request Q:
  before: try field states, reject Q/F; Q remains pending
  after:  omit the failed comparisons; Q remains pending
Actual Q supply:
  before and after: answer Q with exactly the matching groups
```

These are distinct positions. A nonexact broad root/class supply is still
allowed by the service API and can overlap either position. Do not remove
that fallback or create a new class-wide run-1 initial.

### 5.2 Six local edits

```kotlin
// (1) requestClass: DELETE this complete loop.
for (state in fields.values)
    answerClass(row, state.region, state.reach + state.processed, out)
// Preserve broadGroups answers, classRequested, row removal and writer notification.

// (2) commit: after the SAME write/group-state processing as before:
if (fieldKey(position) == null) {
    val iterator = classInterests.iterator()
    while (iterator.hasNext()) {
        ap.cancellation.checkpoint()
        val row = iterator.next().value
        answerClass(row, position, supply.groups, out) // same supplied set, not `fresh`
        if (row.wanted.isEmpty) iterator.remove()
    }
}

// (3) registerWriter: preserve field matching and final askOnce.
if (fieldKey(row.region) == null) {
    for ((region, groups) in classRequested)
        if (requestMatches(row.region, region)) wanted += groups
}

// (4) Regions: expose the existing prefix operation, no new index.
fun prefixes(p: Pattern): List<V> =
    index.lookupPrefixes(p.fact.base, p.fact.path.toIntArray()).distinct()
// requestClass: replace around by prefixes ONLY here.
for (key in flowWriterIndex.prefixes(interest.region)) {
    val writer = flowWriters.getValue(key)
    if (requestMatches(writer.region, interest.region)) askOnce(writer, interest.wanted, out)
}

// (5) commit: inside the existing broadGroups fresh-supply branch:
broadGroups[position] = old + fresh
if (!isExactClassPoint(position)) {
    for (key in fieldIndex.around(position)) {
        val state = fields.getValue(key)
        if (intersect(state.region, position) != null) groupWrite(state, fresh, out)
    }
}

// (6) field(): initialize a new canonical field from old broadGroups.
for ((position, groups) in broadGroups) {
    ap.cancellation.checkpoint()
    if (!isExactClassPoint(position) && intersect(canonical, position) != null)
        state.reach += groups
}
```

Keep all other loop order and arguments. In particular, still call
`admitWrite` for an exact class supply, update `broadGroups`, and answer
actual class interests. For field supplies, still admit field write
presence and update `req/reach/processed` before omitting the class loop.
Preserve `askOnce`, writer registration IDs, pending-row removal, `clear`,
the service lock and owner-directed replies outside the lock.

Do not replace `around` globally: field requests, broad supplies and read
presence still need their existing candidates. Root writers and same-path
class writers remain candidates in the exact-class prefix lookup. Keep
the full matcher, including its existing exclusion treatment, afterward.

### 5.3 Correctness argument

For arbitrary finite paths, an exact shorter ancestor has empty overlap
with a strict descendant. If first accessors differ, the prefix test
already rejects the pair. Otherwise `S.C/EXACT` ends before the field
accessor, and the exact-tail test rejects it. Exclusions and mark groups
cannot turn rejection into overlap.

Each removed answer attempt therefore returns before updating requested
groups or producing an effect. Each removed writer test is false before
`askOnce`. Removing them preserves state and the effect list except for
diagnostic visit counts. Every writer which can match an exact query has
a path which is a prefix of that query; a strict descendant cannot match.
Thus prefix lookup retains every successful candidate.

Induct over commits. The private request maps only receive exact class
points through the existing `requestClass` check; `field()` creates the
same canonical two-accessor regions; omitted attempts change neither.
The invariants and all successful state transitions remain true under
either arrival order, batch partitioning and `clear`.

## 6. Reuse unchanged canonical GROUP projections

### 6.1 Receiving-manager normalization remains mandatory

`runtimeFact(f,FULL)` still returns `f`. `Reach` keeps its existing path.
For GROUP, compute the same normalized marks, exclusions and layers
before choosing an identity return. An unchanged numeric mark ID or
DEMAND layer is not proof of receiving-manager canonicalization.

```kotlin
private fun projectedLeaf(old: TaintLeaves, mode: ApMode): TaintLeaves {
    val exact = runtimeMarks(old.exact, mode) // includes receiver intern()
    val any = runtimeMarks(old.any, mode)
    return if (exact === old.exact && any === old.any)
        taintAlg.intern(old)                 // receiver leaf interner still runs
    else taintLeaves(exact, any)
}

fun runtimeFact(f: Facts, mode: ApMode): Facts {
    if (mode.markMode == MarkMode.FULL) return f
    return when (f) {
        is Reach -> f
        is FlowTree -> {
            val layer = if (mode.markMode == MarkMode.GROUP && f.base == STATIC)
                Layer.DEMAND else f.layer
            val mx = intern(f.markExclusion.projected(marks, mode.markMode))
            val E = if (f.tail == Tail.ANY) ExclusionSet.Empty else intern(f.exclusion)
            if (layer == f.layer && mx === f.markExclusion && E === f.exclusion) f
            else flowTree(f.base, layer, E, mx, f.root, f.tail)
        }
        is TaintTree -> {
            val root = checkNotNull(taintAlg.mapLeaves(f.root, cancellation) { projectedLeaf(it, mode) })
            val layer = Layer.DEMAND // current GROUP projection rule
            val tail = if (root.hasAny) f.anyTail else Tail.ANY_TAINT
            val E = if (tail == Tail.ANY || !root.hasAny) ExclusionSet.Empty else intern(f.exclusion)
            if (root === f.root && layer == f.layer && tail == f.anyTail && E === f.exclusion) f
            else taintTree(f.base, layer, root, E, tail)
        }
    }
}
```

The receiving leaf interner may return another representative even when
the mark-set references are unchanged. `mapLeaves` must then rebuild the
affected path as today. Its existing unchanged-node identity reuse is
sufficient; do not change trie algebra or add a second cache.

```text
Same-manager canonical GROUP leaf, DEMAND, normalized header:
  before: walk, create equal TaintLeaves candidate, create equal wrapper
  after:  walk, reuse receiver leaf, return the unchanged wrapper

Manager A's root passed to manager B, sharing the same MarkTable:
  before and after: project/intern with B; use B's existing canonical leaf

Unknown DEMAND tree containing FULL T:
  before and after: map T to its GROUP and normalize the header
```

This part reduces allocation. It still walks unknown TAINT input and
classifies its marks. It does not promise to remove all `runtimeFact` CPU.

### 6.2 Omit one proven duplicate callback projection

The selected narrow path is the unary source `transfer` callback in
`RunMethodLocalServices.sourcePredicateAt`:

```text
ops.applyEdge -> applyCompiledEdge -> out.run1Policy
  -> runtimeFact -> run1CoverFacts -> immediate actor collect callback
```

`applyCompiledEdge` sends both primitive results and `Results.flush`
through that policy. The exact callback input has therefore already been
projected and covered by this same AP manager, including generic FULL
constants and raw NORMAL-layer GEN results. No intervening transformation
can insert a new mark. Change only this callsite to the new consumer:

```kotlin
// RunMethodAnalyzer. Internal contract: immediate callback from this
// actor's ops.applyEdge, after its run1Policy. Not a public Facts promise.
internal fun collectAfterRun1Policy(at: OperationInput, sink: ResultSink): ApOut = object : ApOut {
    override fun result(f: Facts) {
        val value = if (config.run1 &&
            (f is TaintTree || Run1StaticService.hasStaticMember(at.premise)))
            f.withLayer(Layer.DEMAND) else f
        if (config.run1) ops.run1Cover(value) { sink(at.premise, it) }
        else sink(at.premise, value)
    }
    override fun markRequest(mark: TaintMark) = needGroups(at, mark)
}

// Only the source transfer callback changes in this iteration.
ops.applyEdge(facts, premise, edge.edge, statementEdge, mode,
    core.collectAfterRun1Policy(at) { _, result ->
        core.sourceHits?.add(core.key, node, edge.forward, action.id)
        sink(premise, result)
    }, may = edge.may)
```

This removes the second `runtimeFact` call from that callback. Keep its
premise-dependent demotion, second cover, source-hit handling, requests
and downstream static routing. Later FULL execution follows the same
callback behavior. All other `collect`, `forceRun1Layer`, cut, limit,
cleaner, summary and custom-form callers keep their existing projection.

Do not replace a collector based only on run number or Facts kind. A new
callsite requires its own producer/consumer proof. Do not add a public
Boolean flag, thread-local mode, per-result token or manager backpointer
to let arbitrary callers claim that a result is already projected.

### 6.3 Correctness argument

Full-to-group atom projection is idempotent. Receiving-manager canonical
mark sets, leaf payloads, mx and E are fixed points of their interners.
Mapping an immutable tree twice with those same operations has the same
typed value and canonical payloads as mapping it once. Wrapper identity
reuse is valid only after all fields equal their factory-normalized values.
Neither identity return changes leaf position, tail, E, mx or layer.

For the narrow callback, let `P` be `runtimeFact`, `K` the cover operation,
and `D_I` the existing premise-dependent layer demotion:

```text
before: raw -> P -> K -> P -> D_I -> K -> sink
after:  raw -> P -> K      -> D_I -> K -> sink

For each value v emitted by K(P(raw)): P(v) = v.
```

Cover only filters/splits projected leaves and changes the existing
headers; these results retain canonical projected marks. Prove its
same-manager closure and header fixed points, including mixed exact/any
E-bearing TAINT. The second cover still runs after `D_I`, which may change
the layer. Requests travel through the unchanged mark-request adapters.
This local equality does not authorize skipping projection at unknown
boundaries or changing static propagation.

## 7. Proof model and remaining implementation obligations

The executable constructive modules are
[Run1AllOrigins.lean](../../spec/lean/ApSpec/Run1AllOrigins.lean) and
[Run1RoutingProjection.lean](../../spec/lean/ApSpec/Run1RoutingProjection.lean).
The [focused audit](../../spec/lean/run1-origins-routing-projection-audit.lean)
lists their selected statements. Historical representative-only proofs
remain historical; equal guide-entry sets are not the new handoff theorem.

| Law | Constructive argument and model coverage | Production correspondence gate |
| --- | --- | --- |
| No incomplete result | Scalar projection takes only accepted complete bindings; factored slots require every domain | Existing checkMark, NNF root, explicit arity, action and seed adapters |
| All nonzero origins | A scalar arm exists exactly when its initial occurs in a complete binding | Full InitialAp identity, target classification and per-arm layer policy |
| Exact ZERO | A ZERO arm exists iff one accepted binding uses only ZERO origins | Same compatible cube/proof and seed permission; no pooled OR shortcut |
| Factored origin equality | `factor_exact` equates independent-domain projection with executable Cartesian scalar enumeration; `shared_leaf_exact` requires both shared origin and header agreement | NNF shape/header invariant, domain intersection and same-header input union |
| Complete handoff pairs | `handoff_pair_exact` retains both the result and origin coordinates | Real leaf extraction, tail/group normalization and store merge/subsumption |
| Late and duplicate inputs | `fresh_exact` and `no_fresh_for_duplicate` give exact new origin membership | Actor node/root versions, stale queue entries and freeze lifecycle |
| Class/field disjointness | Arbitrary-length path recursion rejects an exact class ancestor in either direction | Real `Pattern`, tail and E encoding and private map invariants |
| Prefix completeness | A successful writer/exact-query overlap requires a writer prefix | Real PathTrie lookup order, dedup and the unchanged complete matcher |
| Removed-loop effects | Removing empty effect lists preserves their concatenation | Old attempts return before mutations; preserve surrounding commit order |
| Projection identities | Atom/tree idempotence and normalized-value reuse | Receiving-manager interners, root sharing, E/mx/tail/layer factories |
| Immediate callback skip | Idempotent normalization permits a duplicate normalization to be omitted | Actual applyEdge output paths, cover closure, demotion and request effects |

The handoff argument uses the full pair, not just its entry. For one
reference ND result with distinct nonzero members `I1,...,Ik`:

```text
H({I1,...,Ik} -> C) = {(demand(C), demand(Ij)) | j=1..k}
H({I1 -> C, ..., Ik -> C}) = the same set of complete pairs
```

Each pair is constructed from the same conclusion and the same initial
before the same handoff normalization. Conversely, every unary arm names
a contributing initial. An all-ZERO binding uses the existing canonical
ZERO case. For stored rows, compare the denoted demand relation after
existing normalization and subsumption, not physical row counts.

A full reference caller binding can apply each new arm because it
satisfies every original member. Applying an arm from a partial caller
binding can add a run-1 candidate; this is the selected approximation.
FULL rule checks and conjunctions remain unchanged. This fixed-clause
argument is not a proof of whole-analyzer discovery or later convergence.
Internal sink/dead-local guidance needs the integration controls below.

The models prove local finite laws, not the mutable Kotlin adapters or
whole-engine termination. The path model proves overlap existence, not
the complete returned Pattern payload. The projection model proves value
idempotence, not JVM interner identity. These production obligations must
be discharged by focused correspondence controls before implementation
acceptance. Do not replace them with an assertion that all run-1 inputs
already satisfy the fast-path invariant.

Build and audit before production changes:

```bash
cd spec/lean
lake build ApSpec.Run1AllOrigins ApSpec.Run1RoutingProjection
lake env lean run1-origins-routing-projection-audit.lean
```

No `sorry`, `admit`, classical choice or native decision axiom is allowed.
Use constructive witnesses and kernel-checked executable controls. Record
the actual build/audit result and its scope, rather than claiming that
these modules prove every required engine adapter.

Specification validation on 21 October: the focused Lean 4.33.1 build
passes. The audit checks 26 statements and 20 executable controls, with
only `propext` and `Quot.sound`; no classical choice, placeholder or native
decision axiom occurs. Production tests and project measurements have not
been run for this document-only iteration. Runtime code is unchanged.

## 8. Tests and acceptance

Write runtime regression controls before implementing their corresponding
production changes. Use independent scalar or old-code oracles, not the
new result store to compute expected values.

### 8.1 Origin and NNF controls

- Complete/incomplete two- and three-slot rules; repeated origins;
  nonstatic, static-field and exact class-marker origins together. Every
  required arm must exist, without a representative preference.
- All-ZERO, mixed ZERO/nonzero, late all-ZERO completion and alternatives
  containing both ZERO and nonzero inputs. No ZERO from partial cubes,
  different OR branches, incompatible headers or failed seed permission.
- Shared canonical leaves with disjoint/overlapping initial domains,
  incompatible layers/group headers, nested AND/OR and shared DAG nodes.
  Compare exact output arms and ZERO admission with complete scalar tuples.
- All input orders, duplicate submissions, Facts annotation growth, late
  origins and stale queued versions. Unchanged state produces no new
  origin arm; a newly enabled arm is emitted once per result key.
- Original unary source anchors and MULTI targets, including GROUP-folded
  one-leaf MULTI. Source/action IDs and seed checks remain distinct.
- Sink proofs and full checked-input witnesses equal the reference.
  No sink witness is built from one projected origin alone. Preserve
  internal sinks with dead locals and the ignored end-action policy.
- Method/caller integration checks complete normalized handoff pairs and
  a run-1/2/3 trace needing the origin discarded by the old representative.
  Include a static origin and a local origin. Run-2 gets the correct group
  demand; FULL rule reversal still checks the declared full marks.
- Later FULL `ConjunctionStore`, `RulePredicateStore` and E6 retain exact
  ND behavior. Run-1 result stores allocate no PremiseSet or origin tuples.

Extend `ConjunctionStoreTest`, `RulePredicateStoreTest`, local conjunction
and source/sink integration fixtures. Add a focused test for the new
factored stores which counts attempted origin memberships: for a fixed
independent header partition with n and m origins, it must not visit n*m
origin bindings. Different proof/header combinations remain separately
accounted work.

### 8.2 Static routing controls

Compare the old and new service with the same batches and normalized
owner effects/state after closure:

- Exact class versus same/different-class fields, including deeper paths;
  genuine class supplies, partial groups and either arrival order.
- Canonical field answers, req/reach/processed transitions and pending-row
  removal; writer askOnce and both static-origin/nonstatic FLOW writers.
- Broad root/nonexact-class supply and writer fallback, with admitting
  and blocking E; exact class supply followed by late field creation.
- Read-presence notifications, duplicate writes/supplies, split batches
  and `clear` followed by reuse. Exact-class requests do not visit field
  descendants; valid root/same-path writer candidates still receive groups.

The old matcher is the semantic oracle. Diagnostic candidate counts may
fall. No new public diagnostic API is needed to test internal visits.

### 8.3 GROUP projection controls

- Same-manager projected TAINT retains the wrapper by identity when every
  normalized field agrees; FLOW reuses only with unchanged layer, E and mx.
- Distinct managers sharing one MarkTable, with a receiver leaf already
  interned; uninterned equal MarkSets/TaintLeaves; independent catalogs
  with equal numeric IDs. Preserve current interpretation/fallback.
- FULL, GROUP, control ZERO and mixed mark sets; unknown DEMAND values
  containing FULL marks; exact and any leaves, deep/shared trie roots.
- Factory normalization for E, tail and root.hasAny; static FLOW demotion;
  mixed E-bearing exact/any cover and premise-dependent layer changes.
- The actual unary source applyEdge callback, including raw NORMAL GEN,
  generic/custom FULL constants and requests, gives the same values and
  source-hit observations with one fewer projection. Direct generic
  collect callers continue to project. Later FULL behavior is equal.

### 8.4 Project gates and measurement

After the focused controls pass, run the repository's common/JVM suites,
selected application controls and analyzer consumer build. Reuse the
fixed configuration and projects from the latest report. Keep the static
ablation setting explicit; compare identical settings, including the
`disableRun1Statics=true` ThingsBoard diagnostic where available.

Report engine run-1 time separately from prescan/report time. The target
is completion of run-1 within 60 engine seconds. Report peak heap/RSS,
outcome, **latest reached run-N**, latest COMPLETE run and accepted
forward report for every measured project, including OOM/timeout cases.
Do not report the old results above as candidate validation.

For the semantic correction, compare direct arms and handoff demand pairs
with the all-member reference. Old representative findings alone are an
insufficient oracle. For routing and projection, compare with a staged
all-origin reference to isolate their equivalence and performance effects.
Retain strict qualified source/sink identity and witness comparisons after
complete closure; distinguish expected added candidates from any lost
reference result or changed FULL behavior.

Use existing counters/profiles and small test-local counters to measure
origin-product elimination, exact-class failed comparisons, leaf/wrapper
allocations and the selected duplicate projection. Add no permanent
per-edge history or census store. Stop broadening the benchmark once the
selected gates close unless a new failure requires another measurement.

## 9. Implementation order and completion criteria

1. Pin the reference; build/audit the local models. Add independent oracle
   controls for the origin correction, exact-class routing and projection.
2. Expose binding-only witness APIs. Migrate sinks without changing their
   complete evidence. Retain exact FULL result APIs and E6.
3. Implement factored explicit and NNF source-result stores. Integrate
   all-origin output, exact ZERO admission, deltas and lifecycle cleanup.
   Remove the run-1 representative policy. Validate the full handoff pairs.
4. Implement the six local static routing edits. Close the service-state,
   prefix and broad-fallback controls against the old service.
5. Implement local canonical leaf/wrapper reuse. Add the one audited
   immediate source callback consumer. Keep all other boundaries generic.
6. Update the normative selected premise sections in `spec/ap.md` §0.4,
   `spec/grouped-run1-statics.md` §0.1, their proof map and `proof-status.md`.
   Mark the representative design historical. Do not rewrite unrelated
   mark, field, static, caller or handoff contracts.
7. Run the relevant integration gates and fixed project measurements.
   Record actual run iterations, proof/test scope and finding differences.

For a fixed compatible result partition with k distinct origins, the
correction can grow output from one arm to k. For independent slot sizes
n_j, factorization replaces origin tuple enumeration `product(n_j)` with
domain storage `O(sum(n_j))` and distinct arm output. Incremental NNF
shape joins, shared-domain intersections, witness products, and repeated
growth waves still cost work; no whole-engine linear bound is claimed.

Routing removes proven failed attempts without a new index. Identity
reuse removes leaf-candidate/wrapper allocations; the narrow callback
removes one duplicate mark walk per result on that path. These claims do
not establish a fixed speedup, an OOM fix or the universal 60-second target.
Completion requires the stated semantic/correspondence gates and fresh
measurements, with no hidden semantic change added to meet that target.
