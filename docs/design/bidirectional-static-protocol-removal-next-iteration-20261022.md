# Next iteration: static summary erasure and mark-mode experiments

Date: 22 October 2026. Status: **selected design; implementation pending**.
Measured runtime: `30c1b84606695070a79d7ae467703267b9f931c1`.
Read the [performance report](../reports/bidirectional-static-protocol-removal-performance-20261022.md)
and [current contract](../../spec/run1-static-group-protocol-removal.md).

The selected deliverables are the
[static-summary erasure specification](../../spec/run1-static-summary-erasure.md),
its [run-1 mark-mode flag](../../spec/run1-static-summary-erasure.md#14-select-group-or-full-marks-in-run-1),
and [two paired experiments](../../spec/run1-static-summary-erasure.md#15-two-experiments-group-versus-full-in-run-1).
Implement the shared new static design once. Then compare GROUP and FULL
run-1 on that same revision. The flag defaults to GROUP. This extension
does not select the other normalization/backfill proposals below.

The next target remains Thingsboard **run-1 engine time**. Its engine takes
220.578 seconds. Conductor takes 18.005 seconds and PDF takes 29.530 seconds;
both meet the 60-second target. OWASP takes 239.868 seconds, but does not
execute either selected static GROUP shortcut in this measurement.

| Project | Latest reached | Latest COMPLETE | Latest accepted forward report |
| --- | --- | --- | --- |
| Thingsboard | run-2 OOM | run-1 | run-1 |
| Conductor | run-2 OOM | run-1 | run-1 |
| PDF | run-2 OOM | run-1 | run-1 |
| OWASP | run-5 TIMEOUT | run-4 | run-3 |

The allocation profile shows repeated work that the current object-reuse
optimization does not remove. The fresh heaps show a different problem:
later-run pending publications and retained incoming guidance. Keep these
scopes separate. A run-2 heap is not a retained-heap measurement of run-1.

## Priority and semantic scope

| Priority | Proposal | Expected effect and evidence | Semantic contract |
| --- | --- | --- | --- |
| 1: selected run-1 work | Remove static/static summaries and complete actual run-2 queries | Incoming guidance retains 2.683 GB in run-2; 5,890,526 of its 6,956,267 physical pairs are static/static. Remove repeated relay work across wrappers. The removable byte/time fraction is not measured. | **Major change:** use the selected specification and finite coverage proofs; production adapter gates remain. |
| Selected experiment control | Add GROUP/FULL run-1 selection and compare both modes | Measure whether mark merging reduces net work, or whether GROUP condition/cleaner candidates offset the smaller mark vocabulary | **Mark-precision change in FULL only:** exact conditions, exclusions and selected cleaners; keep structural run-1 policy fixed. |
| 2: run-1; narrower proof scope | Normalize GROUP payloads at proved boundaries | Remove repeated whole-DAG walks, scratch maps and frames. Source shows five normalizations on one ordinary transfer path. Thingsboard run-1 allocation weight under `runtimeFact` is 104.276 GB, 11.06%; inclusive rows overlap. | Preserve current facts, requests, layers, origins, rules and demand pairs. Prove the producer closure before the broad change. |
| 3: later runs only | Backfill summaries only when their premise first becomes permitted | Remove repeated publication offers for already-permitted rows. Thingsboard run-2 retains 12.77 million actor-pending Publication objects; PDF retains 19.32 million. The duplicate fraction is not measured. | Preserve current FULL execution and handoff. No effect on run-1, which has no StrongQueries restriction. |

The table ranks expected impact on the selected run-1 target. Static relay
removal has the largest component-level potential and the largest proof
risk. GROUP normalization has the clearest local source argument and can
start with a smaller change. Its measured allocation share does **not**
establish a twofold engine improvement. No
proposal in this document has a measured twofold whole-engine bound.
Use the fixed cohort after implementation to test net benefit.

The default GROUP rule view stays unchanged. The selected FULL experiment
keeps exact marks in conditions/actions, requests, exclusions and cleaners.
This can change candidate precision. It does not change field transfer,
exit-rule scope, controller annotation checks, FULL mark identity or the
direction/field-limit schedule. Both modes keep run-1 concrete taint DEMAND,
empty field exclusions, all-origin handling and the same static algorithm.
Keep `trackFactsReachAnalysisEnd` empty. Do not add GroundingSupport,
reference-manager changes, weak caches or IR changes.

## 1. Research: stop static-only relay summaries

The selected [complete specification](../../spec/run1-static-summary-erasure.md)
and [proof dossier](../../spec/run1-static-summary-erasure-proofs.md) now
replace the research alternative in this section. Remove all run-1
static/static rows after producer effects. Use actual run-2 query
completion and the explicitly selected named-field/EXACT class grammar;
do not build the possible shared exact-pair directory described below.
The design remains unimplemented. The measurements below still refer to
runtime `30c1b8460` and retain their stated limits.

The actual run-2 incoming guidance has 5,890,526 static/static physical
pairs, **84.68%** of its DemandPattern population. The whole incoming
store retains 2.683 GB across 73,608 method keys. These measurements
support investigating repeated static relations across methods. They do
not establish how many pairs are canonical same-group relay results,
how many complete relations repeat, or how many bytes can be removed.
STATIC here includes root, exact class-marker and field positions.
Prove and measure that narrower eligibility before claiming a saving.

### Current versus new

```java
void copy() { C.h = C.f; }
void wrap() { copy(); }
void main() { wrap(); }
```

After an actual G answer, the current engine can retain and publish:

```text
copy: C.f/*/G -> C.h/[any]/G, D
wrap: C.f/*/G -> C.h/[any]/G, D
main: C.f/*/G -> C.h/[any]/G, D
```

Static results go directly to caller summary promotion. They do not
execute an ordinary caller suffix. The original producer has already
secured eventual computed-supply delivery, although its pool batch can
commit after its publication flush.

Research direction: retain the rooted supply derivation, but stop
republishing a pure static relation through every wrapper. Keep local
consumer replies and nonstatic return summaries. A chain with h wrappers
and k such pairs could avoid the h*k relay component.

**This is a major semantic contract change.** The current selected proof
requires equal per-method raw and demand relations. This research
direction deliberately removes some of them. It needs a new coverage
theorem before selection or implementation.

The universal implicit static pair is not enough. An explicit exact-field
guide can admit an exact FULL initial that a broad root ANY guide does
not emit. A possible replacement is a shared relation of complete exact
entry/exit guidance pairs, indexed by actual entry geometry and group.
It must preserve paired exits and first caller bindings, including a
nonstatic setter applied to a static actual. Pool endpoint membership
alone does not prove those anchors exist.

Required proofs are constructive finite relay erasure to a retained
anchor; exact normalized pair coverage; rooted output supply; every local
read/refinement wakeup; sink-only helpers and ZERO paths; and extension of
ordinary FULL execution under the proposed guidance. Preserve cross-group,
ZERO and nonstatic relations until separately proved.

Global guidance can also create more later-run work at methods that did
not previously own a pair. Measure matching candidates and admitted
initials separately. Avoid recreating method-count times pair-count work
inside a global lookup. Do not claim identical precision, identical
resource-limited prefixes or a whole-engine gain from the relay example.

## 2. Normalize GROUP payloads at proved boundaries

### Current versus new

The current canonical GROUP optimization can return the same leaf, node
or Facts object. However, `ApManager.runtimeFact` still calls
`mapCanonicalLeaves`. It walks the DAG before it discovers that nothing
changed. Each call allocates a visited `IdentityHashMap`, a deque and node
frames. Child mapping also allocates temporary lists.

New: normalize an input at its required boundary. Internal operations that
provably preserve canonical GROUP payloads reuse that postcondition. Keep
the header, field, request and layer policies. Do not walk the same
unchanged payload again merely to establish an invariant already proved
by its immediate producer.

This removes work. It does not replace a map with a faster map or keep a
new cache of old roots.

### Example and fact trace

```java
void copy(Box x) {
    Box y = x;
    use(y);
}
```

Let the original be `j=arg0/*/G`. For this illustration, the current
bounded result has canonical GROUP leaves:

```text
j -> {x.left/[any]/G, x.right/./G}, DEMAND, E={}
j -> {y.left/[any]/G, y.right/./G}, DEMAND, E={}
```

For an ordinary micro-edge with no overflow or result filter, source
inspection gives this path:

```text
applyCompiledEdge -> run1Policy.result              payload walk 1
EngineAlgebra -> generic core.collect                payload walk 2
emitOperationResult -> forceRun1Layer                payload walk 3
cut / limit -> generic core.collect                  payload walk 4
emitAfter -> forceRun1Layer                          payload walk 5
MethodEdgeStore.add                                  existing admission
```

This is one source-derived path, not a measured average. For a payload
with k distinct nodes, five checks can visit 5k nodes while returning the
same root. The field cut, transfer, cover and store operations remain.

### First step: reuse an immediate producer postcondition

The engine already has `collectAfterRun1Policy`. Unary source transfer
uses it. Regular micro-edge paths still use the generic collector even
though the AP callback has just applied that policy.

```kotlin
// Current shape.
ops.applyEdge(input, edge, core.collect(at, sink))

// Proposed shape: the AP callback has already applied run1Policy.
ops.applyEdge(input, edge, core.collectAfterRun1Policy(at, sink))
```

Preserve the original-specific layer adjustment and cover. For the
overload without a saved `OperationInput`, keep its existing `raiseMark` routing.
Do not replace it by a saved-point `needGroups` callback. This first
step removes one walk in the displayed path, five to four. It needs no
new global invariant or persistent flag.

Other narrow seams are a mode-aware `targetTree` or `startFact` followed
immediately by another payload normalization. Reuse their established
postcondition only at those specific consumers.

### Larger step: prove the internal producer closure

Define a logical precondition, not a runtime per-node test:

```text
CanonicalPayload(manager, root):
  each reachable atom is a fixed point of this manager's
  runtimeAtom(atom, GROUP), including control ZERO;
  each leaf uses this manager's canonical payload representative;
  every reused child has the same property.
```

Use this value invariant only to select equivalent normalization code.
Keep input admission, field transfer and rule guards unchanged. Do not
use it to require new actual-input or writer validation.

Inventory the default engine's producers. Prove closure for primitive
transfers, selected subsets, summary grafts, cleaners, type filters,
field cuts, covers and store union/subtraction. Prove that actor and
runner delivery share the intended AP manager. A plain constructor is
not a validation boundary.

Then an internal header-only policy can replace a payload walk:

```kotlin
// Internal only. Precondition: CanonicalPayload(ap, fact.root).
fun normalizeKnownGroupHeader(fact: TaintTree): TaintTree {
    val tail = if (fact.root.hasAny) fact.anyTail else Tail.ANY_TAINT
    val exclusion =
        if (tail == Tail.ANY || !fact.root.hasAny) ExclusionSet.Empty
        else ap.intern(fact.exclusion)
    return ap.reuseOrBuildTaintTree(
        fact, layer = DEMAND, exclusion = exclusion, anyTail = tail)
}
```

This is an API sketch. Reuse current constructors and exact header rules.
Keep FLOW normalization initially; it has no TAINT DAG walk. Keep Reach
and ZERO behavior, original-based demotion, `run1Cover`, field limiting,
primitive filtering and request callbacks in the same order.

Keep public/general AP boundaries defensive. A receiving manager can
contain FULL atoms even in a run-1 process. An interned root, empty E,
GROUP mode or DEMAND layer alone does not prove payload canonicality.
Foreign managers can use different leaf representatives or assign a
different meaning to the same numeric atom. Mixed children can also
contain an unprojected subtree. Do not add a per-Fact support label,
WeakIdentityCache or historical-root store to avoid these proofs.

### Proof and implementation gates

1. Prove projection is a fixed point for canonical receiving payloads,
   including shared DAG children and empty root leaves.
2. Prove the header-only policy equals current `runtimeFact` on that
   domain. Preserve EXACT, ANY, MUST, E and original-based DEMAND rules.
3. Prove every selected producer and unchanged-child branch preserves the
   precondition. Keep defensive paths where the proof does not apply.
4. Prove requests and source/cleaner callbacks are unchanged. Keep every
   contributing original and the complete all-ZERO condition.
5. Compare decoded facts and actual method/entry/exit/group/seed handoff
   relations. Object identity alone cannot prove that a walk was removed.
6. Test FULL and foreign inputs, mixed DAGs, cached FULL compiled edges,
   exact GEN, ANY/MUST, aliases, cleaners, static replies and later runs.
7. Measure the removed call paths and allocation under the fixed cohort.
   Do not add a hot per-node counter solely for the experiment.

The narrower immediate-producer step can precede the full closure. Do not
claim the larger step is complete because that narrow step passes tests.

## 3. Backfill only newly permitted summary premises

This is a **run-2 and later** proposal. It does not explain Thingsboard's
220.578-second run-1 engine.

### Current versus new

`RunMethodAnalyzer.addLink` admits a new StrongQueries batch. For every
nonempty admission, it scans `summaries.all()` and queues every currently
permitted raw row again. That includes rows permitted by earlier queries.
`queuePublications` appends to an actor list. PublicationIndex deduplication
runs after the method queue drains, too late to avoid these pending rows.

New: backfill an existing summary only at its permission transition from
false to true. Future fresh raw deltas still use the existing publication
path. No persistent per-premise answered flag is needed.

### Example and fact trace

```java
Object identity(Object p) { return p; }
void callers() {
    sinkA(identity(sourceA()));
    sinkB(identity(sourceB()));
    // Other distinct admitted inputs follow.
}
```

In the restricted method actor, let raw row `pA -> return/A` already be
permitted and unchanged. A new input `pB` becomes admitted.

```text
old: admit pB -> scan all raw rows -> requeue pA;
     also backfill a pB raw row if one already exists
new: admit pB -> do not requeue pA;
     backfill an existing pB raw row only
both: start the fresh pB initial;
      later fresh raw deltas publish through retainSummary as usual
```

For K unrelated fresh admissions and R unchanged, already-permitted rows,
the old backfill can offer that subset K*R times. New work for that subset
is zero. This is a component bound; the project duplicate fraction and
total benefit have not been measured.

### Frontier without a persistent permission cache

Let `fresh` be the newly admitted canonical initial members. Let
`wasEmpty` record whether the admitted set was empty before this batch.

```kotlin
fun newlyPermitted(p: PremiseKey, fresh: Set<InitialAp>, wasEmpty: Boolean): Boolean =
    when {
        p.isZero -> forward && wasEmpty && fresh.isNotEmpty()
        else -> queries.permits(p) && p.members.any { it in fresh }
    }
```

For an ND premise, keep the all-members-now-permitted test. One fresh
member alone is insufficient. Backward ZERO is already unrestricted, so
it needs no query backfill. Forward ZERO needs its first admission event.
Keep the existing order: finish the whole `StrongQueries.admit` batch,
backfill the newly permitted premises, then start the fresh initials.

A first patch can retain the raw scan and avoid redundant publication
creation. A later patch can borrow fresh singleton keys directly and
test ND keys before enumerating Facts. New reverse indexes need a separate
measured case; do not add them automatically.

Prove permission is monotone, and that the current restriction returns
the same complete output once a premise is permitted. Prove first
backfill plus future raw deltas covers all applications, including FULL
E/mx relaxation, both layers, ND premises, late subscribers and demand
selection. Preserve cancellation and counted work.

Do not replace this by arbitrary FULL publication packing. A later
antichain entry can cover a historical value without preserving every
earlier precise application. Establish application coverage before
discarding such values.

## 4. Selected: run-1 mark-mode flag and two experiments

Add `opentaint.bidi.run1MarkMode=GROUP|FULL`, default GROUP. Select rule
views, executable atoms, requests, static service keys and answers with
that same mode. FULL must preserve T versus U within one owner. Keep
the current structural run-1 policy, canonical direct paths and demand
handoff. Do not route FULL through an older static algorithm. The
[complete flag contract](../../spec/run1-static-summary-erasure.md#14-select-group-or-full-marks-in-run-1)
lists the integration gates and explicit precision differences.

Plan two experiments on the same implemented candidate commit:

| Experiment | Run-1 setting | Everything else |
| --- | --- | --- |
| `run1-groups` | `-Dopentaint.bidi.run1MarkMode=GROUP` | Shared new static design and fixed project settings |
| `run1-full` | `-Dopentaint.bidi.run1MarkMode=FULL` | Same binary, inputs, aliases/statics enabled, heap, timeouts and instrumentation |

Run both on Thingsboard first, then the fixed Conductor, Stirling-PDF and
OWASP configurations. Use at least three serial pairs with alternating
order and fresh analyzer JVMs. Run the ordinary configured analyses;
compare run-1 engine time separately from prescan, barriers and later
runs. Record memory/allocation/GC, actual work and request counts, mark
cardinality, strict finding identities, and every latest reached,
COMPLETE and accepted iteration. Keep later OOM outcomes separate from
complete run-1 timings. The
[experiment plan](../../spec/run1-static-summary-erasure.md#15-two-experiments-group-versus-full-in-run-1)
specifies resource caps, ratios, counters, profiles and precision checks.

Measure the net effect. FULL can avoid GROUP condition/cleaner candidates
even though it carries more mark identities. Do not infer performance
from mark count alone. No experiment has run under this extension.

## Ideas not selected

A pure static AP self-composition shortcut could retain the current raw
and demand pairs while avoiding some reducers. It has a smaller proof
scope than relay removal. However, the Thingsboard normal run-1 native
window attributes only 2.59% to the direct/shared call reducer family;
even the broader static dispatch union is about 7.67%. These overlapping
CPU samples do not establish the required large engine gain. Keep this
below the payload-normalization investigation.

Exit-scope correction also remains separate. Added XSS results and broad
rule-provider owners justify investigation, but do not prove all added
findings are false positives or identify one removable Spring cache.
Do not add a ZERO-premise restriction, move controller exits, or restore
ignored END tracking as part of a performance patch.

## Acceptance boundary

Before implementation, use the selected detailed contract and
constructive proofs under [proof-guide.md](../../spec/proof-guide.md).
Complete the FULL-mode and shared runtime adapter gates. Keep semantic
changes explicit and existing failed precision gates marked as failures.

After implementation, rerun approved module checks and both planned modes
on the four fixed project configurations. Report every engine/barrier and
latest reached, COMPLETE and accepted iteration. Compare strict rule/method/span keys and
occurrence counts against fresh main. Analyze each missing result.

Measure both removed work and remaining work. A lower allocation count
with a slower engine is not a successful speedup. A later-run OOM fix
does not satisfy the run-1 target. No new runtime code is authorized by
this proposal.

Source reviews:

- [Static relay anchors and proof gates](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/next-static-relay-elimination.md).
- [GROUP producer and normalization inventory](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/next-group-normalization-boundary.md).
- [Summary-permission frontier](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/next-query-publication-backfill.md).
- [Actual incoming-guidance partition](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-thingsboard-demand-guidance-v3/owner-interpretation.md).
