# Avoid unanswerable requests and unused call work in run-1

Date: 23 October 2026. Status: **unselected next-iteration proposal**.
Measured runtime: `fb84969c0b91ccae0500389b5e25e045557b0eb3`.
This document changes no runtime code.

Read the [current performance report](../reports/bidirectional-full-work-batching-performance-20261023.md).
Older proposals are historical inputs. Use the closed current campaign
as the evidence for this proposal.

## 1. Results and expected impact

| Project | Candidate run-1 engine | Latest reached | Latest COMPLETE / accepted forward |
| --- | --- | --- | --- |
| Thingsboard | OOM, 359.365 s, including 40.836 s heap capture | run-1 | none / none |
| Conductor | COMPLETE, 20.516 s | run-2 OOM | run-1 / run-1 |
| Stirling-PDF | COMPLETE, 32.499 s | run-2 OOM | run-1 / run-1 |
| OWASP | COMPLETE, 255.243 s | run-4 TIMEOUT | run-3 / run-3 |

The target is a complete run-1 engine within 60 seconds. Prescan and
report barriers are separate. The current implementation passes this
target for Conductor and Stirling-PDF. It fails for Thingsboard and OWASP.
Main Thingsboard also has an incomplete prescan baseline.

| Expected impact rank | Proposal | Current → new | Evidence and expected gain |
| --- | --- | --- | --- |
| 1 | Do not admit FULL requests with no possible selected producer | Store and route requests from all-relevant cleanup metadata → omit only memberships whose answer is impossible under a complete producer certificate | 3,773,069 of 4,765,614 reference pending-image memberships have no selected raw normal producer; a conditional 79.17% lane reduction |
| 2 | Avoid unused call plans and alias expansion | Build before the exact base/alias guards → test pure admission first and defer unused aliases | Candidate call-plan builders occupy 34.78% of Thingsboard run-1 Java samples; aliases occupy 18.15%, within that scope |
| 3 | Honor disabled unresolved-call diagnostics | Store full TAINT inputs before a no-op callback → allocate no disabled diagnostic archive | Exact retained archive union is 103.423 MB reference and 115.844 MB candidate; sampled candidate CPU share is 0.181% |

Rank 1 has the strongest measured large-workload opportunity. Its
79.17% result is an exact FULL-key/selected-item join for one pending lane,
conditional on a closed producer boundary. It is not a proved whole-engine
or byte saving. Rank 2 addresses a large CPU scope, but its removable
fraction is unknown. Rank 3 is a small independent correction. Do not
present it as a 2x improvement.

All six OOM heaps and all twelve profile sets have completed their stated
analysis scopes. Conductor and Stirling-PDF fail in run-2, where the new
run-1 bulk loops do not execute and run-1 service/point/image instances
are zero. Their later-run stores need a separate proposal.

### CPU basis

Each arrow is reference → candidate. Thingsboard uses its failed run-1
prefix before heap capture. Other rows use complete run-1. Shares use
Java execution samples, not process CPU seconds.

| Project | Call-plan builder CPU share | Alias CPU share | Builder samples from `process` |
| --- | ---: | ---: | ---: |
| Thingsboard | 32.73% → 34.78% | 16.87% → 18.15% | 94.33% → 94.84% |
| Conductor | 50.03% → 50.04% | 29.72% → 28.39% | 100% → 100% |
| Stirling-PDF | 66.75% → 67.20% | 53.37% → 53.89% | 100% → 100% |
| OWASP | 32.76% → 29.70% | 13.94% → 11.48% | 100% → 100% |

Aliases are inside the builder scope. Do not add their shares. A builder
frame does not distinguish first construction from rebuild. A `process`
frame does not show the input base or guard result. The form-cache payload
wrapper also encloses ordinary execution. It is not a rebuild scope.
Host activity, GC and different failed prefixes limit causal wall-time claims.

## 2. Semantic scope and proof boundary

The intended major semantic changes are **none**. Preserve supported
completed observations: complete premises, fields, FULL marks, layers,
NORMAL/DEMAND summaries, source hits and logical rule occurrences, including
variants and multiplicity. Preserve field transfer, all origins, exact
all-ZERO source admission, controllers and annotation-based exit rules,
static read/write eligibility, enum alias filtering and run-2 guidance.
`trackFactsReachAnalysisEnd` stays empty. FULL stays the default.

All marks remain declared, legal and eligible. Proposal 1 does not restore
GROUP filtering or enumerate runtime members. It uses a conservative
certificate about possible production in this particular fresh run. It
must not rewrite conditions, remove cleaner masks, change mx exclusions,
filter reached supplies, or change mark interning. Unknown clients retain
the old request path.

Use explicit forward **run-1** gates. Run-2 also uses field limit 1;
FULL mode, direction and limit alone do not identify run-1. GROUP and later
runs keep the old behavior. Do not add GroundingSupport, writer validation,
IR optimization, a new reference policy or a global relation archive.

Follow the [proof guide](../../spec/proof-guide.md). The proofs below are
requirements for the selected next specification. They are not completed
Lean or Kotlin claims. If a proof requires a new field, source/exit,
mark or ownership contract, specify that major change before implementation.

## 3. Do not admit requests with no possible selected producer

### 3.1 Why these requests exist

The source execution sites query `allRelevant=false`. These sites include
call, entry, normal/exceptional exit and static-field sources. The return
rewriter queries `allRelevant=true` to build cleanup metadata. It does not
execute GEN from that all-relevant query. Thus a legal mark can remain in
a cleanup mask even when every normal source item which assigns it is
excluded by the current Semgrep selector.

The actual filter is a set of **serialized item IDs**, not just public
rule IDs or top-level owners. Both Thingsboard heaps contain the same
381-item filter. The raw census has 1,607 FULL keys and 1,290 normal
assignment targets. After exact selection, 231 FULL keys retain at least
one raw normal producer. Null serialized IDs remain selected.

The dominant field is `TenantId.tenants`, a mutable UUID-to-TenantId
cache. Its reference pending request mask has 649 FULL keys:
124 have a selected normal producer and 525 do not. Its one reached key
and 28 processed keys all have a selected normal producer.

The exact global static-origin PointRow pending-image map join is:

| Classification | Stored scalar memberships |
| --- | ---: |
| At least one selected raw normal producer | 992,526 |
| No selected raw normal producer | 3,773,069 |
| Unknown FULL key; retain | 19 |
| Total, matching the independent image/point census | 4,765,614 |

This scope is global; do not label all 3,773,069 rows as TenantId rows.
It excludes ordinary RequestStorage and the separate service pending-owner
relation. Their membership counts are different. No measured saving for
ordinary requests is assigned to this proposal.
A broader check which ignores selection finds only 78,300 memberships
with no raw normal assignment, about 1.64%. Selection is the important
factor. The set of actually supplied marks is not an absence certificate.

Evidence: [exact selected join](../../../run1-full-work-batching-20261023/validation/heap-reference-thingsboard-rule-payload-memberships/selected-normal-producer-field-and-weighted-join.json),
[independent join](../../../run1-full-work-batching-20261023/validation/producer-mark-closure/selected-normal-producer-independent-join.md),
[producer index](../../../run1-full-work-batching-20261023/validation/producer-mark-closure/normal-producer-item-index.json)
and [GEN/selector source review](../../../run1-full-work-batching-20261023/validation/producer-mark-closure/selected-normal-producer-source-review.md).
The candidate weighted pending join was not run. Do not transfer the
reference percentage to its 4,726,793 pending memberships as a measurement.

### 3.2 Certificate after prescan

Build a conservative set from serialized rules. Do not load or specialize
rules for each method, scan runtime members, or use source-condition truth
to reduce the set.

```kotlin
// Proposed immutable analysis capability, not a mark-eligibility registry.
sealed interface Run1Producers {
    data object Unknown : Run1Producers
    data class Closed(
        val generation: SelectionGeneration,
        val fullKeys: Set<FullKey>,
    ) : Run1Producers
}

fun selectedTargets(raw: RawCatalog, selection: Selection): Set<FullKey> =
    raw.normalAssignments.asSequence()
        .filter { selection.isAll || it.serializedId == null ||
                  it.serializedId in selection.retainedIds }
        .map { it.completeFullKey }
        .toSet()
```

CompleteFullKey includes the rule owner and full mark name, including
GeneratedMark conversion and the existing primitive property. Several
producer items can assign one key: retain it if **any** item can be selected.
A null filter means ALL; an empty nonnull filter means no identified items,
with null-ID items still retained. Do not confuse these cases.
Bind the catalog to the actual loaded inputs and conversion rules. An
uncertified loader error, omitted provider or unresolved generated-name
case must select Unknown; a successful offline count alone is not coverage.

A Closed value also needs conservative targets for all other GEN roots
and mark-renaming actions. Copy-only wrapper additions need no new target.
Unknown native records, external initial facts, custom providers, dynamic
names or opaque transfer paths select Unknown. The fixed production JVM
path starts with fresh native records, empty run-1 seeds and ZERO roots;
this is a source-backed prerequisite, not a generic engine guarantee.

Pin the producer capability to the same completed-prescan selector generation
used for execution. Do not reselect it during the run. If a provider cannot
promise this, use Unknown for the whole run. Do not switch to Unknown only
after dropping old requests; those requests would already be lost.

```text
P = selected raw normal targets
    union certified independent GEN/input/native targets
    closed under every declared mark-renaming action

Unknown boundary or unstable selection -> old behavior for all requests
```

### 3.3 Gate request ownership, preserve reads and cleaners

```kotlin
fun mayAnswer(mode: RunMode, producers: Run1Producers, key: FullKey): Boolean =
    !mode.isForwardRun1Full || producers !is Run1Producers.Closed ||
        key in producers.fullKeys

fun requestedSubset(offered: MarkSet): MarkSet = offered.filter { atom ->
    val key = marks.peekFullKey(atom)
    key == null || mayAnswer(mode, producers, key) // UNKNOWN stays admitted
}
```

`peekFullKey` is a proposed noncreating interner lookup. It is not a member
census. Existing raw or opaque atom forms keep their old route.

The measured target is static-origin pending storage. Admit before its
owners allocate pending state:

```text
static PointResume.request:
  offered -> certified subset -> PointKey / per-atom PendingFlowImages

exact class-marker direct interest:
  offered -> certified subset -> DirectRow.waiting / StaticInterest
```

Before implementation, inventory every StaticInterest producer. This also
includes call interests, fixed-premise conditional joins, and
WriterInterest-to-WriterInput forwarding. For each such lane, prove that
its marks are already in P or gate at its owning wait/join allocation.
The two gates above are not a complete inventory. Preserve FLOW activation
and producer effects separately when a concrete-only wait is impossible.

The same global absence theorem could also justify an ordinary request
adapter before `R` subtraction and `temporary.addAll`. That extension has
no measured benefit here. Keep it optional and prove its abstraction,
caller replay and handoff correspondence separately. It is not needed to
claim the recorded static PointRow membership opportunity.

Do not filter only at global service commit. That leaves owners waiting
forever and retains the images this proposal is meant to remove. Do not
change the global three-set transitions or concrete supply admission.

A `StaticOperation.MarkInput` also registers a FLOW read. Keep that read
registration and activation even when its concrete request subset is
empty. Apply the gate only to the concrete direct-interest/pending lane.
Do not drop the whole operation, ordinary static read, reached write,
FLOW edge or query-completion path because one concrete mark is absent.

A cleaner still applies its full original mask and mx update. It still
produces every existing complement and DEMAND result. Negative conditions
and all-ZERO cube logic stay unchanged. Do not replace `CM(p,T)` by false
or `NOT CM(p,T)` by true from this certificate. Prove request-only erasure
separately from the existing source and exclusion semantics.

### 3.4 Example and expected saving

```java
Object x = C.f;
library(x);       // all-relevant return cleanup mentions U and V
sink(x);          // existing rule logic
```

```text
At the return cleaner: I = S.C.f/*/FLOW
Current fact: I -> x/STAR/FLOW, with the existing layer.
Active sources can assign U; every raw normal producer of V is excluded.
Assume a complete Closed certificate includes U and excludes V.

Current:
  run the same U,V cleaner; add U,V to mx as required;
  save x's FLOW image for U and V;
  subscribe and ask FLOW writers for both;
  V remains unanswered until release.

New:
  run exactly the same cleaner and preserve both mx/DEMAND effects;
  save and route only U;
  answer U with the same canonical initial and raw suffix;
  keep no V point membership or request propagation.
```

The sample is schematic. It does not claim these exact two marks occur
at one measured source line. With a closed producer boundary, no concrete
V exists in the old closure, so its request cannot obtain a concrete answer.
Removing that request and its request-only descendants removes work but
no supported fact, summary, source hit or sink occurrence.

The recorded reference lane would keep at most 992,545 memberships only
if the root/rename proof shows that none of the 3,773,069 excluded raw
memberships can enter the final P through another route. A Closed value
alone does not establish this number: its conservative P can be larger
than the 231 selected raw targets. If other roots add keys, repeat the
weighted join against that final P and reduce the claimed saving.

Under that narrower condition, 992,545 is the 992,526 selected rows plus
all 19 unknown rows. That is a 79.17% reduction, about 4.80 times fewer lane
memberships on that recorded event history. The target is at least a 2x
reduction in admitted pending memberships under a valid certificate.
This is not a live-byte, whole-run or wall-time guarantee. Counts and
form lifetimes can differ in a new run.

### 3.5 Required constructive proof and counterexamples

Prove the following before enabling the optimization:

1. Every concrete GEN root, explicit initial/input and native fact has its
   FULL key in P. Source selector and raw-to-full conversion commute with
   this over-approximation. Cover every wrapper and source role.
2. Every other concrete transfer copies a key in P or performs a declared
   rename whose target is in P. Include requests, static replies, aliases,
   ND actions, class markers and query normalization. Cycles cannot create
   the first concrete atom without such a root.
3. A dropped request cannot answer. Its descendants can only create more
   unanswered requests; they do not directly emit a summary, observation
   or demand needed independently of a concrete answer. Keep the existing
   cleaner/exclusion results and FLOW-read activation in this proof.
   Cover every interest owner, fixed-premise join and writer-forwarding
   path; do not infer that the two primary gates cover all allocations.
4. Finite derivations transport in both directions after request-state
   erasure. Preserve all typed NORMAL/DEMAND rows and run-2 handoff atoms,
   including mark demand. Unknown capabilities execute the identical old path.
5. Count stored memberships and writer asks on the same offered event trace.
   The admitted set is a subset; the selected-key derivations stay equal.
   Do not prove a lower CPU bound from counts alone.

Check two producers of one mark with only one selected; null IDs;
null/empty filters; late supplies; selected guarded sources; wrappers;
GeneratedMark; same name under different owners; primitive keys; negative
conditions; selected and RemoveAllMarks cleaners; static MarkInput read
activation; exact class markers; run-2 guidance; and explicit native/custom
inputs that force Unknown. A regression must show that a selected source
which appears late still answers its earlier request.

## 4. Avoid unused call plans and aliases

### 4.1 Exact touched-base test before `withCall`

Today, `RunMethodCallServices.process` calls `withCall`, then tests
`item.facts.base !in plan.touched`. Forward `JIRCallPlanBuilder.touched`
contains only STATIC, the receiver, actual argument bases and the result
base. That set does not require call resolution, rule specialization,
return cleaners or persisted alias expansion.

```java
void check(Object other) {
    Object held = source();
    helper(other);
    sink(held);
}
```

```text
Fact at helper: I -> held/EXACT/T [L]
T: one full (rule-owner, mark-name) value; L: the existing layer.
held is absent from {STATIC, receiver, actual arguments, result}.

Current: lease the form; build it on a miss; test touched; emit unchanged.
New:     exact operand test; emit the same unchanged fact.
```

This avoids the cache lease and, on a miss, the entire construction for
this invocation. A cache hit does not represent a saved build. Reach/ZERO
work uses the current path. STATIC always uses the current path.

Add a conservative language-adapter query with an UNKNOWN default:

```kotlin
// Proposed API seam; not implementation code.
enum class CallTouch { TOUCHED, UNTOUCHED, UNKNOWN }

fun callTouch(method: MethodKey, node: CommonInst,
              call: CommonCallExpr, base: AccessPathBase): CallTouch = UNKNOWN

// RunMethodCallServices.process, after active()/cancellation checks:
if (core.config.run1 && item.facts !is Reach &&
    core.forward && core.context.shared.interpreter.callTouch(
        core.key, item.node, call, item.facts.base) == UNTOUCHED) {
    core.emitUnchanged(item)
    return
}
core.forms.withCall(core.key, item.node, call) { plan -> /* current path */ }
```

The JVM adapter must use the same operand-to-base conversion as the
builder. A small shared operand helper can supply both queries. Preserve
unsupported/non-simple return handling: return UNKNOWN and use the old
path rather than masking its error. Do not resolve targets, load rules or
ask alias analysis in this query. Other language adapters retain UNKNOWN.

The test changes neither the fact nor the current meaning of touched.
It must not become a new approximation of which callee can affect a fact.

### 4.2 Defer the guarded alias-only stage

Today, `JIRCallPlanBuilder.build` calls `aliases(back)` before the engine
has any result to classify. The helper queries persisted aliases and
constructs full-path transfers and static-write descriptors. Later,
`applyStage` can reject the entire alias stage for a value.

```java
void caller(Box p) {
    Box a = p;
    touch(p);
}
void touch(Box q) { }
```

```text
Raw callee summary: q/STAR/FLOW -> q/STAR/FLOW
Current run-1 classifier: noEffect = true
Return cleanup retains that classification.

Current: build alias transfers; MemoryEffect rejects the input.
New:     keep the same rejection; do not construct aliases for this input.
Both:    ordinary bind-back still runs.
```

Keep a deterministic final alias slot in the plan. Materialize its summary
only for an input admitted by the current guard and the exact potential
input-base check below. An empty alias summary emits nothing. It must not
pass the input through as an identity.

```kotlin
// Bind-back metadata only; this does not enumerate aliases.
val possibleAliasInputs = back.edges.asSequence()
    .filter { it.edge.to.base is AccessPathBase.LocalVar }
    .map { it.edge.from.base }
    .toSet()

// Proposed alias-stage dispatch for run-1:
if (!Guard.MemoryEffect.admits(true, value.identityCandidate, value.facts.layer))
    return emptyList()
if (value.facts.base !in possibleAliasInputs)
    return emptyList()
val summary = aliasSlot.materializeWithOriginalHelper()
applyWithOriginalFormApplier(summary, value)
```

Unknown identity stays admitted. Do not tighten `MemoryEffect` or change
its DEMAND behavior. The extra base check is restricted to the alias-only
summary. Reach/ZERO can pass `MemoryEffect` while matching no alias edge;
without this check it can force every deferred alias slot unnecessarily.

The alias helper creates no source, conjunction, filter, static read or
mark-input operation. Its static metadata contains writes only.
`Run1PointResume.reached` sends no semantic effect for such a Write; its
unused reached-bookkeeping row is not a read subscription. This makes the
unmatched-base shortcut possible. Prove this property before using it.
Do not extend the shortcut to arbitrary summaries.

### 4.3 Keep actual alias writes and demand effects

```java
void caller(Box incoming) {
    C.f = incoming;
    Box p = C.f;
    update(p);
}
void update(Box q) {
    q.value = source();
}
```

Assume the current alias analysis reports the persisted `p`/`C.f` relation.
The earlier reached write supplies the read. The source result can produce
the following return-stage value:

```text
ZERO -> Argument(0).value/EXACT/T [L], noEffect = false
Argument(0) is a possible alias input.

Current: apply the prebuilt alias transfer into S.C.f.value.
New:     build that same transfer now, then apply it.
Both:    apply the existing field bound and static DEMAND policy;
         publish the local static writer/supply before S-to-S erasure.
```

This example does not add a new static write or field rule. Keep the
original `StaticOperation.Write`, complete transfer and final stage ordinal.
Use the current `FormApplier`, collector and `emitOperationResult` path.
The existing field bound can create an ANY conclusion; it must not create
an ANY initial. Dropping the write descriptor or bypassing `localWrite`
would lose future static answers and run-2 demand guidance.

### 4.4 Required representation and cache seams

The current `CallStage.Edges.summary` is eager. A lazy getter alone is
insufficient. `FormsCache` calls `formBuilt`; its storage census traverses
`CallPlan.storage`, which includes every stage summary. That would expand
aliases before the runtime guard.

Use an explicit owned deferred alias descriptor with a nonforcing peek:

```kotlin
// Schematic contract; choose the final API in the selected spec.
interface AliasSlot {
    val operation: OperationId
    val possibleInputs: Set<AccessPathBase>
    fun peek(): StatementSummary?             // never builds
    fun materialize(): StatementSummary      // exact old helper, once per payload
}

// Storage census:
countDescriptor(aliasSlot)
aliasSlot.peek()?.let(counts::include)
```

Keep the published stage order and logical ordinals stable. The alias slot
is last; all preceding operation and raw cleaner addresses stay unchanged.
Do not edit the stage list when a fact arrives. An old empty alias result
must have empty behavior at the new slot. Equality, hashing, directories,
debug views and the storage census must not force materialization.

The slot belongs to the existing managed form payload. Preserve cancellation
and safe once-only publication. The recipe must retain narrow immutable
bind-back inputs and stable language services. Do not capture per-fact
state, request callbacks, a method-edge store or the whole call-plan builder.
No new global cache or reference policy is needed.

This defers alias-target enumeration and lowering. It does not defer every
use of alias analysis. The builder's rule, type and call-resolution work
can need the existing alias-analysis service before this stage.

Raw call-stage and cleaner replies reload a plan and use their stored
ordinal, step, phase and call state. The same descriptor and IDs must be
rebuilt after eviction. Preserve caller layer, identity classification and
pending demotion through every reply.

Before `CallPlan.reversed` computes exit targets and `passOverBases`, force
all deferred aliases. An alias unused in run-1 can still be required by
run-2. Later forward leases must also get the original full behavior.
Specify this force boundary through an explicit run-use policy. Do not
use FULL mode as a proxy for run-1, or omit aliases from reversed targets.

An enabled or custom unresolved-call observer is another full-view
boundary. Its callback receives a CallPlan at ADDED and can inspect more
than `passReads`. Before invoking that callback, materialize the aliases
and provide the complete legacy plan view. Its stages must contain the
ordinary alias Edges when nonempty and omit the formerly absent empty
alias stage. Preserve touched bases, entry/exit, sinks, common stage and
rule objects, action IDs, raw addresses and passReads. Do not pass an
internal deferred descriptor to an existing callback.

```kotlin
// Only after the existing exact diagnostic admission accepts a fresh input:
observer.reached(ctx.call, fact.facts.base, ctx.plan.fullLegacyView())
```

The full view can be retained inside the same managed payload. It must
not become a global archive. Its construction must not rerun rule lowering
or change preceding operation ordinals. Disabled callbacks request no
view. Census inspection still uses the nonforcing representation. The
same full-view boundary can supply later forward use and reversal.

Direct and reference APIs are also full-view consumers. Preserve the
current behavior of `Interpreter.callPlan`, `MethodForms.call`, and the
existing general `withCall` callback. They must not return an internal
deferred-stage representation. Give the engine an explicit internal
run-1 lease/use policy; compatibility entry points select the full legacy
view. Generic providers keep their current eager default. Do not infer
this policy from the mark mode alone.

## 5. Honor the disabled unresolved-diagnostic capability

All fixed manifests omit `--track-external-methods`. The frontend flag and
`CommonAnalysisOptions` default to false. The source review checks the
resolved Clikt default as well as all twelve argument lists.

`JIRInterpreter` always implements `UnresolvedCallObserver`.
`SharedObjects.unresolvedObserver` casts the interpreter to that interface,
so it remains nonnull when `externalMethodTracker` is null. The callback
tests the tracker only after call services have stored the fact.

```java
Object x = source();
externalLibrary.accept(x); // unresolved call
```

```text
At ADDED: I -> Argument(0)/EXACT/{T,U} [L], a packed TAINT value
Tracking is disabled.

Current:
  key = (CallerRef(method, I, callerLayer, call), full GroupKey)
  observed.add(key, full TAINT tree)  // exact merge, key/entry/root retention
  observer.reached(...) -> tracker is null -> return

New:
  observer capability is disabled
  no diagnostic accumulator and no exact diagnostic merge

Both: unresolved transfer, pass rules, sources, cleaners and aliases use
      their existing semantic paths.
```

This archive selects TAINT inputs, not FLOW or Reach. It stores full packed
facts until the call services freeze. It is not only a set of positions.

Select the observer at the composition root. Keep the existing observer
interface unchanged. Use a nullable capability in SharedObjects, with its
current cast as the compatibility default:

```kotlin
// Proposed constructor seam; existing generic/custom callers keep the default.
class SharedObjects(
    val interpreter: Interpreter, // Other current constructor parameters are omitted here.
    val unresolvedObserver: UnresolvedCallObserver? =
        interpreter as? UnresolvedCallObserver,
)

// JIRBidiAnalysis.prepareShared already owns the optional tracker.
val observer = if (copied.externalMethodTracker != null) interpreter else null
return SharedObjects(/* current arguments */, unresolvedObserver = observer)

// Call services:
private var observed = if (core.config.run1 && shared.unresolvedObserver != null)
    AddedFactAccumulator(core.ap) else null
```

When enabled, retain the current exact deduplication domain, callback
arguments, full legacy plan view and release behavior. Custom observers
keep the existing default selection.
When disabled, no semantic consumer reads this private accumulator.

Do not remove `CalleeSubscriptions.values`. It uses the same accumulator
class for exact caller inputs and replay. The 2,707,285 Key/Entry pairs in
the reference heap include more than one owner kind. The exact owner split
is now known: the disabled diagnostic archive has
355,254 entries and a 103.423 MB retained union in the reference; the
candidate has 419,811 entries and 115.844 MB. The separate semantic
subscription unions are 417.771 MB and 470.070 MB. These unions overlap
other owners and are not additive. The actual tracker is null on both sides.
This removes a useless archive, but its size does not explain the full OOM.

The targeted Thingsboard profile puts this observer at 0.207% reference
and 0.181% candidate Java execution samples before heap capture. Allocation
weights are 457 MB and 560 MB, about 0.047% and 0.061% of their totals.
Most sampled generic accumulator admissions are under CalleeSubscriptions,
not this observer. Thus the source defect is clear, but it is not the
largest measured CPU cost. These traffic estimates and retained-byte
unions measure different quantities. Read the
[targeted scope receipt](../../../run1-full-work-batching-20261023/validation/profile-analysis/targeted-unresolved/receipt.json).

## 6. Required proofs and implementation sequence

First write a selected specification and constructive Lean models. Then
add differential runtime controls. Implement only after those proofs and
controls define the supported domain. No candidate in this document is
selected by the document itself.

| Part | Required invariant or correspondence |
| --- | --- |
| Producer certificate | Complete GEN/input/rename closure, stable selection and exact FullKey conversion; Unknown keeps all requests |
| Request erasure | Removed memberships cannot answer or independently create observations; keep FLOW activation and all cleaner/mx effects |
| Cheap touched test | UNTOUCHED equals the existing JVM operand-derived touched set; UNKNOWN and Reach keep the old path |
| Alias lowering | Deferred materialization equals the existing helper, with all paths, enum filtering and static-write metadata |
| Alias admission | Same MemoryEffect guard; every alias source is in the possible-input set; unmatched values have no semantic output/effect |
| Addresses | Stable stage/cleaner ordinals and call state across eviction, empty aliases and late replies |
| Static producers | Same collector, local writer/supply effects and summary erasure for every admitted alias result |
| Reversal and APIs | Force complete aliases before reversal, later forward, enabled/custom callbacks and raw/reference full-view use |
| Disabled diagnostics | Private state erasure preserves semantic state; enabled/custom exact admission and complete plan view remain unchanged |
| Ownership and handoff | Nonforcing census, safe once-only materialization, cancellation/release and both directions of finite derivation transport |

Count local work. For a cold untouched invocation, old plan construction
is one and new construction is zero. For a form generation with no admitted
matching alias input and no full-view consumer, old alias expansion is one
and new expansion is zero. An admitted input or compatibility consumer can
still force one expansion. Do not infer equal cache-eviction schedules.
Disabled diagnostics must have zero diagnostic entries. Semantic caller
subscriptions remain intact.

Keep the current source review and its hashes:
[call work and observer](../../../run1-full-work-batching-20261023/validation/next-iteration-source-options/source-review.md).
The disabled archive dates to `27e22bb1e` on 15 October; eager alias
construction dates to `161f4151a` on 11 October. These dates alone do not
explain the historical performance difference.

## 7. Validation targets

Use `fb84969c0` as the runtime baseline. Keep the four fixed project inputs,
heaps, timeouts, aliases, statics and FULL mode. Use direct Gradle
`runProjectAnalyzer`. No alternative project configuration was run for this
proposal. Report all iteration times and latest reached/COMPLETE/accepted
forward states. Retain the current precision failures as failed gates.

For requests, separately count offered memberships, certificate rejects,
unknown fallbacks, admitted owners/images, writer asks and concrete answers.
Avoid high-frequency shared atomic counters or retained diagnostic histories.
The 79.17% join is a prior-state opportunity, not a new-run acceptance result.
Require equal typed scalar NORMAL/DEMAND closures in finite controls, with
late selected sources and the same run-2 marked witnesses.

For aliases, count the bind-back edge occurrences with local targets for
each candidate form generation. The eager helper queries persisted aliases
once per occurrence. Compare the total weight with the materialized weight,
including callback/raw-view forcing. Aim for materialized weight at most
half the eager weight on Thingsboard. This measures at least a 2x reduction
in this alias-enumeration lane; it does not assume equal per-query cost or
a 2x whole-engine gain. Measure early UNTOUCHED admissions separately.
If the removed fraction is small, do not present it as a large optimization.

Keep form-census on/off, cancellation, eviction, backward-only alias targets,
GROUP/later-forward controls, empty aliases, disabled/enabled/custom observers
and direct/raw API views in the differential suite. No new alias or request
architecture is justified by a counter alone.

## 8. Deferred work and separate correctness followups

The provider cache is a separate memory concern. The reference ordinary
sink-role cache union is 1,082.107 MB; resolved conditions alone have a
680.181 MB union. Source inspection shows eager AnyArg binding products
before late Semgrep selection. A future design can avoid binding raw
unselected items for `allRelevant=false`, while preserving the full view
for metadata and prescan. It needs an exact cache-lifetime/phase plan.

Do not call the whole cache unused END payload. That payload measurement
is UNKNOWN: bounded optional probes were stopped or failed. The Spring
wrapper currently blocks the no-END provider factory, but adding that
factory alone can retain old prescan caches and add a second cache. It is
not a selected or proved memory fix. Do not remove controller exit rules.

Do not redesign global writer storage from membership counters. The
reference has 1,156 global FLOW writer rows, not millions. The dominant
field is the mutable TenantId cache, not an enum constant. Its final field
reference does not make the object read-only. Keep the current enum hack;
no general final/static-field filter is proposed.

Defer shared image cohorts, broad mark eligibility, a new mask compiler,
FULL-origin families and IR changes. The new metadata/statistics scopes
are small in JFR. Large atomic call counts alone do not identify a CPU
bottleneck. This proposal first avoids requests for certified impossible
answers and alias work rejected by existing guards.

Keep these correctness issues separate:

- PDF: two strict main locations are missing at accepted run-1. The
  synthetic-exit Simple-report location loss is confirmed; matching
  branch/FULL-mark witnesses remain unproved. No exit-rule fix is included.
- OWASP: accepted run-3 has all 4,112 main unique keys, but 9,179 Simple
  occurrences and no generated traces. The workflow raw-total gate fails.
  Do not remove occurrences just to match that number.
- Application controls: eleven exact-reference failures remain. No changed
  expectation or semantic repair is part of this proposal.

The implementation of the two selected batching optimizations remains
unchanged. A future selection must state its exact scope and proof boundary.
