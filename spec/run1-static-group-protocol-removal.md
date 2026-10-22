# Run-1 static GROUP application without repeated pool requests

Date: 22 October 2026. Status: **implemented; validation in progress**.
The runtime reference is `a4bf789866f912ebefc54bca42d809c89beea2d4`.
Later documentation commits do not change this reference.
The [implementation validation](../docs/reports/bidirectional-static-protocol-removal-validation-20261022.md)
records production checks and the new measurement campaign.

This document specifies two changes:

1. Remove the repeated static GROUP call handshake.
2. Remove unary static GROUP computed-supply joins.

Use [the proof dossier](run1-static-group-protocol-removal-proofs.md) with
this document. Follow [proof-guide.md](proof-guide.md). This document
extends [the selected writer and alias specification](run1-static-writers-and-summary-aliases.md).
It keeps the mark, field, source, sink, call and handoff rules of
[grouped-run1-statics.md](grouped-run1-statics.md), including its subsequent
selected corrections. It replaces only the two protocols specified below.

## 1. Semantic scope

**No new analyzer semantic change is selected.** Both changes remove a wait
for an input that the engine has already received. Keep the same AP
application and scalar suffix execution. Do not infer that an arbitrary
static GROUP hypothesis is an actual supply.

| Mechanism | Current behavior | Required new behavior |
| --- | --- | --- |
| A published static GROUP summary at a reached call | Request its premise from the pool again; save a continuation; resume on the answer | Construct the same known canonical input on the actor; execute the same summary application and suffix |
| A fresh static GROUP output under a static GROUP original | Save a unary join; request the original again; compose on the answer | Compose the same scalar output with the same known input on the actor; offer the result to the same pool batch |
| Raw summaries, selected demands and report witnesses | Retain and publish them | Same |
| First GROUP request, local static read and local FLOW writer | Discover supply and resume real pending work | Same |

The scope is **run-1, GROUP mode**, with field limit 1 and empty field
exclusions E. All GROUP results stay DEMAND. Keep these mechanisms:

- All contributing run-1 atomic originals and the exact all-ZERO rule.
- Field transfers, kills, demand demotion and field bounds.
- The three disjoint static sets `req`, `reach`, `processed`.
- Local-transfer-only writer registration. Call promotion registers no writer.
- ZERO computed supplies and nonstatic conditional input handling.
- Exact special class markers and the static ablation flag.
- Raw alias classification, cleaner order and complete call-stage state.
- Current source/GEN, controller and exit-rule behavior and semantic
  observation identities. Batch-dependent diagnostic counts can vary.
- Counted work, cancellation, complete barriers, record persistence and FULL runs.

Do not add a ZERO-premise exit restriction, Spring exit relocation,
GroundingSupport, actual-input validation, a new answered-input history,
an IR change or a mark-member census. A proof witness is not a runtime label.

The existing proposal also discusses packed suffix execution, repeated
internal suffix admission and fresh-only callee input delivery. Those are
**not selected here**. Packed suffix execution needs a separate proof for
rules, negative conditions and cleaner order. This spec keeps the old scalar
suffix and the pure AP reducer. It does not need those additional changes.

## 2. Terms and eligible originals

An **original** is the initial premise retained on a method edge. A current
fact is its conclusion at a statement. They are not interchangeable.

`G` is one run-1 mark group. `j -> v, D` is a DEMAND edge. `Z` is control
ZERO. `.` is an exact tail, `*` is an abstract initial tail, and `[any]` is
a current taint tail. Every displayed run-1 edge has empty E.

| Eligible original `j` | Canonical current input `K(j)` |
| --- | --- |
| `S.C.f/*/G` | `S.C.f/[any]/G, D` |
| Exact special `S.C/./G` | `S.C/./G, D` |

The exact class case is a global marker. It is not a class-wide field
abstraction. Do not create a class STAR initial, an S-root taint initial,
or an `[any]` initial.

Use a local shape check at the existing production publication boundary:

```kotlin
fun eligible(p: PremiseKey): Boolean =
    config.run1 && mode.markMode == MarkMode.GROUP &&
    p is InitialAp && p.base == STATIC && p.mark is MarkSlot.Concrete &&
    p.exclusion == ExclusionSet.Empty &&
    ((p.path.size == 2 && p.tail == Tail.STAR) ||
     (p.path.size == 1 && p.tail == Tail.EXACT))
```

Shape alone does not establish actual supply for an arbitrary constructed
fact. Section 3 proves the producer invariant at this boundary. Keep all
other legal shapes on their current path. In particular, do not forbid an
exact field initial merely to make this predicate exhaustive.

## 3. The answered-original invariant

The current producer pipeline is:

```text
reached read + corresponding published write
    -> ReadReady -> static FLOW original
real group check/cleaner/writer-input need
    -> first group request
actual source/writer derivation + request
    -> pool PointReady -> static GROUP original
ordinary transfers and call promotions
    -> same original -> published summary
```

Run-1 method abstraction rejects static entry inputs. `ReadReady` creates
FLOW, not GROUP. A concrete field original comes from an actual pool
answer. The exact class path also needs an actual matching answer.
Ordinary transfers, source transfers and contributing-origin rule arms
retain an existing original or use ZERO. Static summary promotion retains
its static original. They cannot create a fresh static GROUP original
from a bare descriptor. Executable run-1 premises have one member.

Consequently, before an eligible summary can exist:

```text
field original C.f/G => G is already in C.f.processed
class original C/G  => matching supply and classRequested already exist
```

`reach` alone is insufficient. The first request for an arrived group
moves it to `processed` and can admit FLOW writer work. The original
constructor runs only after that request has been answered.

Keep previously admitted writer work, including work still waiting for
delivery. A repeated request for a processed field group admits no new
writer/group pair. A repeated class request can scan writers, but
`askOnce` admits no new pair. A later writer gets `req + processed`, or
the matching `classRequested`, at registration. Removing the repeated
request therefore removes no discovery or late-writer trigger.

This invariant uses current GEN and exit semantics. It does not require
a new proof of concrete-program reachability before admitting a group.

## 4. Storage and ownership

| Storage | Owner and contents | Required change |
| --- | --- | --- |
| Method edge store | Method actor; ordinary statement edges and their complete originals | None |
| Raw method summaries | Method actor; `premise -> complete output relation` | None |
| Selected summary demands | Method actor; separate selected relation used by handoff | None |
| Ordinary caller subscriptions | Runner; callee, caller method, original, statement, stage, layers and flags | None; wrappers without reads still subscribe |
| Static field state | Global static service; `req`, `reach`, `processed`, pending unanswered consumers | None |
| Read and FLOW writer descriptors | Existing actor/service owners; actual local operations and once-per-writer/group admission | None |
| Real saved-point requests | Method actor; FLOW images waiting for a first group answer | None |
| Static call continuations | Method actor; scalar publication, call context, waiting group | Do not allocate for eligible static GROUP summary application |
| Conditional supply joins and join inputs | Method actor; output selector and pending complete binding | Do not allocate for an eligible unary static GROUP original |
| Temporary static batch | Method actor; real interests, writes, writers and computed supplies | Keep; direct computed supplies use it |
| Demand snapshot and sink seeds | Immutable handoff after a COMPLETE run | Same construction and contents by semantic relation |

No replacement cache or global provenance store is needed. `K(j)` is a
short-lived local value. The summary itself keeps its existing original.
Do not save a cumulative historical Facts root to avoid the old join.

Remove call-only directories, reply branches and helpers when they have
no remaining callers. Keep fallback paths for unproved legal shapes.
Do not remove shared PointReady dispatch, saved FLOW images, direct class
requests, writer-input requests or multi-input handling used outside this scope.

## 5. Construct the known input

For an eligible original, the pool's answer has a deterministic AP value.
Construct this value without querying the pool:

```kotlin
// Actor-local, pure materialization. No pool lookup or mark enumeration.
fun knownStaticInput(j: InitialAp): Facts {
    check(eligible(j))
    return ap.factsOf(j.toPattern().fact, ExclusionSet.Empty, Layer.DEMAND)
}
```

For a field STAR original, `factsOf` creates the same ANY current taint
fact as `Run1StaticService.facts`. For an exact class original it keeps
EXACT. The existing GROUP atom is used directly. An equivalent operation
is `ops.startFact(j).withLayer(DEMAND)` for the two eligible shapes.

Keep the ordinary `satisfying` check and existing AP reducer. Do not
replace the reducer by a raw-output identity assertion in this change.
Its tails, output groups, field bounds and alias behavior remain executable
code shared with the reference path.

## 6. Call application pipeline

### Current

```text
ordinary caller subscription receives static GROUP publication
  -> split output into scalar leaves
  -> save CallContinuation for each leaf
  -> append StaticInterest for j/G
  -> method barrier -> service answers already-processed group
  -> actor reconstructs canonical input and selected original
  -> existing satisfying / raw alias partition / AP application
  -> promote a static result, or resume this caller's scalar suffix
```

### New

```text
same ordinary subscription receives the same publication
  -> split output into the same scalar leaves
  -> construct K(j) locally
  -> existing satisfying / selected-original reconstruction
  -> existing raw alias partition / AP application
  -> same promotion or same scalar caller suffix
```

Extract the pure part of `staticSupply` so both adapters use it:

```kotlin
fun applyStaticSummary(part: Subscription, pub: Publication, member: Int) {
    check(config.run1 && part.ref.premise.isZero && part.added is Reach)
    check(!part.zeroOnly)
    checkPremise(part.ref.premise)
    checkPremise(pub.premise)
    val j = pub.premise.member(member)
    if (dropsStaticField(j)) return // Existing ablation gate precedes both paths.
    when {
        j.mark is MarkSlot.Star -> existingStaticFlowApplication(part, pub, member)
        eligible(pub.premise) -> {
            val actual = knownStaticInput(pub.premise as InitialAp)
            for (leaf in ops.leaves(pub.conclusion)) {
                cancellation.checkpoint()
                val scalar = ap.factsOf(leaf.fact, leaf.exclusion, pub.conclusion.layer)
                applyStaticInput(part, j, scalar, actual)
            }
        }
        else -> existingStaticCallInterest(part, pub, member)
    }
}

fun applyStaticInput(part: Subscription, j: InitialAp, result: Facts, actual: Facts) {
    val matched = ops.satisfying(actual, j, mode) ?: return
    for (leaf in ops.leaves(matched)) {
        // The same reconstruction as the old staticSupply path.
        val selected = existingConcreteStaticInitial(leaf)
        val ref = part.ref.copy(premise = selected)
        val input = part.copy(ref = ref, linkLayer = matched.layer, added = matched)
        val results = ArrayList<PlanFact>()
        applyParts(input, j, result, results)
        resumeCallees(ref, results)
    }
}
```

This is pseudocode for extraction, not a new public API. `applyParts`
classifies the **raw callee** initial/result before binding, carries its
no-memory-effect flag, and promotes static results through the existing
retention path. Keep caller method, call, stage ordinal, caller layer,
link layer and suffix state. Keep every returned `PlanFact`'s original.

In particular, the ZERO control subscription must not turn
`C.f/G -> return/G` into `Z -> return/G`. ZERO activates a static premise
only in run-1. Later runs still need an actual matching static fact.

The selected path has zero new StaticInterest, CallContinuation and
PointReady events. It still performs one scalar suffix application per
old scalar leaf. Ordinary callee subscriptions, initial group refinement
at a caller condition/cleaner, and FLOW summary activation remain.

## 7. Computed static supply pipeline

`retainSummary` first admits the bounded raw delta. It must still queue
its ordinary publication and selected demand. Computed supply is an
additional side effect, never a replacement for that retention.

### Current

```text
retain j -> static delta
  -> per leaf: save unary ConditionalJoin and JoinInput
  -> request j/G again -> immediate known-group reply
  -> borrow the still-retained scalar result
  -> composeStaticSummary(j, scalar, canonical input)
  -> field limit / force DEMAND / staticSlices -> supply batch
```

### New

```text
retain the same j -> static delta
  -> per leaf: construct the same known canonical input
  -> same scalar composition / limit / force DEMAND / staticSlices
  -> same supply batch
```

Extract the pure composition from `composeStaticSummary`:

```kotlin
fun computedGroups(p: PremiseKey, delta: Facts) {
    if (delta !is TaintTree || delta.base != STATIC) return
    when {
        p.isZero -> existingZeroSupply(delta)
        eligible(p) -> {
            val j = p as InitialAp
            val actual = knownStaticInput(j)
            for (leaf in ops.leaves(delta)) {
                cancellation.checkpoint()
                val scalar = ap.factsOf(leaf.fact, leaf.exclusion, delta.layer)
                composeStaticSummaryWithActualInput(j, scalar, actual) { value ->
                    staticBatch.supplies += staticSlices(value)
                }
            }
        }
        else -> existingComputedGroupPath(p, delta)
    }
    requestWork()
}

fun composeStaticSummaryWithActualInput(j: InitialAp, value: Facts,
                                      actual: Facts, emit: (Facts) -> Unit) {
    val matched = ops.satisfying(actual, j, mode) ?: return
    ops.applySummary(matched, j, value, mode, existingBoundedStaticOut(j, emit))
}
```

`existingBoundedStaticOut` is the current `composeStaticSummary` callback:
keep TAINT outputs, force the existing run-1 layer, apply the field limit,
and use the current collection rule. Summary application is not rule
execution; its existing no-op mark-request callback stays unchanged.

The old callback finds a scalar delta through `currentValues`. For this
selected scope, every accepted scalar leaf remains in the raw store:
E is empty, the entry with that header grows by root union, and incoming
subsumption does not remove earlier stored leaves. Section P4 in the proof
dossier states this obligation precisely. Do not generalize it to FULL
facts with nonempty E. Those paths keep their existing selector behavior.

The output pool still admits the actual supplied region/group, activates
waiting reads and answers pending group consumers. An unrequested output
goes to `reach`; its first real request still moves it to `processed`
and admits required writer work. No nonstatic hypothetical input becomes
a pool supply.

## 8. Principal traces

### Reader through wrappers

```java
void set() { C.f = sourceG(); }
Object get() { Object x = C.f; checkG(x); return x; }
Object wrap() { return get(); }
void main() { set(); sinkG(wrap()); }
```

The local write and read registration, FLOW propagation and first G
refinement are common to both versions. The check inside `get` makes its
own GROUP original available. A check only in `main` can instead refine a
saved caller point; it need not create a GROUP summary inside `get`.
After the getter's first request is answered:

```text
C.f.processed contains G
j = C.f/*/G
get:  j -> return/G, D
wrap: j -> return/G, D
main: j -> value/G, D -> same sink occurrence
```

Current: each static GROUP call application waits for the pool again.
New: each application uses `K(j)` locally. Both still execute the same
wrapper suffix and publish the same wrapper summary. A wrapper needs no
local read to apply a published summary.

### Static copy and output request

```java
void copy() {
    Object x = C.f;
    checkG(x);       // ContainsMark(arg0,G); identity transfer.
    C.g = x;
}
```

```text
before: C.f processed={G}; C.g has no G request or supply
j = C.f/*/G
local result and retained summary: j -> C.g/[any]/G, D

old: unary join -> repeated C.f/G request -> reply -> scalar composition
new: same scalar composition immediately on the method actor
both: offer C.g/G -> C.g.reach={G}
later first C.g/G request: reach -> processed; answer and admit real writer work
```

The ordinary demand pair is kept in both versions:
`copy: (entry=C.g/[any]/G, exit=C.f/*/G)`.

### A dead local in a wrapper

```java
void main() { set(); wrap(); }
void wrap() { deep(); }
void deep() { Object t = get(); sinkG(t); } // t does not escape.
```

`get` has its static-to-return summary. `deep` receives the same marked
local at the sink. It can have no static-to-return summary. Do not invent
one. Both versions retain the same sink occurrence and FULL seed rule.
Run-2 uses ordinary ZERO sink discovery and balanced returns to carry
the static requirement through `deep` and `wrap`. The implicit static
GROUP demand admits an **actual** full static query where required.
Neither removed protocol supplied a missing wrapper-return demand.

### No supplied group, late writer, exact class

```java
void cycle() { C.f = C.g; C.g = C.f; }
```

A FLOW copy cycle without an admitted GROUP source supplies no group.
There is no eligible GROUP summary to shortcut. A first unanswered request
stays pending. A later writer receives the existing requested/processed
groups during writer registration. Exact class replies use the same local
input construction but retain exact position matching and `classRequested`;
they cannot supply descendants as field facts.

## 9. Demand-edge contract and run-2

Compare complete runs under the same roots, rules, AP mode, alias settings
and source/exit policy. Compare semantic relations, not batch counts or
callback order. A timeout or OOM prefix is not a completed fixed point.

For each method `m`, keep:

```text
raw summaries:             (m, original, complete result)
selected demand relation: (m, original, complete result)
sink/report witnesses:    (method, statement, original rule occurrence, FULL predicate)
```

An output includes its base, complete chain, tail, exact mark/group,
exclusions and layer. Preserve ZERO as an explicit original. Preserve
every contributing original; a set of entries without its exits is not
the same relation.

The unchanged forward-to-backward handoff maps each selected pair to:

```text
(m, j -> v)
  -> (m, entry=normalizeOutput(v), exit=normalizeOriginal(j))
```

`normalizeOutput` locates a MUST tail as ANY, converts a STAR entry to
ANY with empty E, and normalizes abstract mark exclusions. The original
uses the same existing location/mark normalization. `Z -> C.f/G` keeps
an **explicit ZERO exit**, not an omitted exit.

The call change gives the same input to the same scalar application and
suffix, with the same original. Thus each caller and wrapper retains
the same result/demand relation. The supply change runs after raw delta
admission and does not replace demand publication. It offers the same
supplies, so it enables the same later requests and summaries. The proof
composes these facts over finite derivations, including recursive calls.

Therefore every explicit demand pair required by the reference run is
still in the new handoff, or has the same semantic cover in the unchanged
demand store. Every original sink seed is still available. The shared
implicit static GROUP family stays enabled, including for later-created
method actors. It is query guidance, not a supplied fact or a replacement
for a nonstatic return demand.

Run-2 is unchanged: backward, field limit 1, FULL marks. A group demand
for g admits the relevant full marks owned by g under the current rules.
The removed wait cannot omit a mark demand, change its owner, or replace
a contributing exit by ZERO. The same run-2 engine therefore receives the
same guidance and seeds. This is a relative no-loss theorem for these two
changes; it does not repair an existing missing edge in the reference
analyzer or prove unrestricted whole-analyzer soundness.

## 10. Scheduling, cancellation and work bounds

Run the local composition on the existing method actor. Never call actor
code while holding the static service lock. Retain cancellation checkpoints
for scalar leaves and AP work. Use the current counted publication/work
ports and method-queue-empty batch barrier. Direct work must complete or
remain counted before the run can become quiescent.

The two removed requests have immediate known answers. Collapsing them
changes delivery time, not enabled semantic facts. Keep pending first
requests and already-admitted writer jobs. Do not clear static state before
the complete run and handoff barriers. Only COMPLETE runs export demands.

Let H be the number of admitted eligible call continuations in the current
path, O the number of eligible scalar call offers, and B the number of
eligible scalar computed outputs. The current `CallKey` can merge offers
while a continuation is waiting, so H can be smaller than O.

| Work | Current | New |
| --- | --- | --- |
| Repeated call premise interests/replies | H / H | 0 / 0 |
| Call continuation rows | Up to H pending | 0 for these activations |
| Unary computed-supply joins/input rows | B / B | 0 / 0 |
| Repeated computed-supply interests/replies | B / B | 0 / 0 |
| Scalar AP applications and caller suffixes | Existing reducer, after pending-key admission | Same reducer and semantic effects; execution count can differ |
| Required summaries, demands, real discovery and output supply | Existing work | Same semantic relation |

The actor/service protocol work removed is linear in H+B. Peak pending rows
depend on delivery order. The direct path can do local AP/suffix work for
O offers where the old pending table coalesced them into H continuations.
Old `currentValues` can also visit a scalar payload under more than one
compatible header. Preserve semantic effects; do not require equal callback
or AP execution counts. Do not add an unselected permanent dedup store to
hide this limitation.

Measure O, H, suffix entries and protocol events separately. The bound does
not prove a reduction in total work, a twofold whole-engine speedup or
completion within 60 seconds. It proves elimination of the selected pool
protocol and retained rows. Production measurements must establish the net
benefit; a large O/H ratio can justify a separate suffix-admission proposal.

## 11. Implementation and acceptance order

1. Check the constructive models and their axiom audit.
2. Extract the pure canonical-input reducers without changing their result.
3. Implement call protocol removal as a separate patch; keep scalar suffixes.
4. Implement unary supply protocol removal as a separate patch.
5. Remove only unreachable protocol storage and callbacks; keep fallback users.
6. Run the production correspondence controls below before project measurement.

| Control | Required observation |
| --- | --- |
| Canonical field and exact class inputs | Local materialization equals the old pool answer, including DEMAND and E |
| Exact/ANY/MUST outputs, changed output group | Same scalar AP result and same bounding callback |
| Raw no-effect/effect arms and bound aliases | Same flags, outputs and demand pairs |
| Nested/multiple callers, late summaries, recursion | Same original, stage, layers and per-method demand relation |
| Equal call offers while the old continuation waits | Same semantic effects and witnesses; measure offered/admitted counts separately |
| Fresh delta followed by a broader delta | Old scalar selector remains valid under run-1 empty E |
| Request-before-write and write-before-request | Same first-request effects and same final three-set state |
| Late writer and pending writer delivery | Same once-per-writer/group admissions; no dropped counted work |
| Unseeded cycles and nonstatic group hypotheses | No invented static supply |
| Dead local sink, helper that throws, ZERO static source | Same sink seeds, explicit ZERO exits and actual run-2 paths |
| Exact class, disjoint fields, ablation enabled | No field/class routing expansion; ablation still suppresses static work |
| Controller/exit/source/cleaner rules | Current behavior unchanged |
| Noneligible and later FULL cases | Existing path remains |

Compare decoded semantic outputs and the actual `HandOff` demand/seed
snapshot, not only pool membership or the number of findings. Run the
existing focused suites and approved project gates after implementation.
Use the fixed Thingsboard configuration for performance comparison; do
not add a workload/configuration here.

The latest reference measurement reached **Thingsboard run-1 OOM**;
completed iteration: **none**; accepted report iteration: **none**. See the
[existing report](../docs/reports/bidirectional-local-static-writers-aliases-performance-20261022.md).
This specification adds no new project measurement. Future reports must
give the latest reached, completed and accepted iteration separately.
