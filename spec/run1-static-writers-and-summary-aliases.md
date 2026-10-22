# Run-1 local static writers and summary alias admission

Date: 22 October 2026. Status: selected specification; runtime unchanged.
Document base: `ef000dea24c33c46ca950545291390707071a0eb`.
Runtime comparison base: `d7244c08cdaefbb157f42e4d89731d301b97e48f`.
Language: ASD-STE100 Simplified Technical English. Code names and defined
technical terms keep their stated meanings. Follow [proof-guide.md](proof-guide.md).

This specification selects exactly two changes:

1. Register static write points only from executed local transfers.
2. Correct run-1 summary alias admission with `hasNoMemoryEffect`.

It replaces the corresponding rules in sections 7, 9, 10 and 14 of
[the grouped/static specification](grouped-run1-statics.md), and the run-1
alias rules in [interpreter.md §3.8](interpreter.md#38-aliases-on-call-results).
It also overrides the older run-1 guard and identity-partition examples in
`analyzer-core.md`, `analyzer-impl.md`, `ap.md` and `ap-impl.md`.
The [proof dossier](run1-static-writers-and-summary-aliases-proofs.md)
states the checked models and production correspondence obligations.
Older proposals are not extra requirements. In particular, global sharing
of equal static relations is deferred.
The section 7 ownership override applies to local FLOW writers: they own
their bounded scalar relation. Generic computed TAINT joins keep their
existing current-summary and complete-binding behavior.

## 1. Selected semantic scope

| Mechanism | Current | New |
| --- | --- | --- |
| Static write admission | Every retained static summary delta can register write presence and a FLOW writer, including a caller copy | Only a completed local transfer with a static-field result admits a write point; call composition only promotes its summary |
| Computed static groups | ZERO group results supply the pool; all-static conditional results join actual pool inputs | Preserve both paths, including results composed in a caller |
| Local writer lifetime | An exact leaf must remain in the merged method summary store | The writer owns its immutable bounded relation; summary merges do not disable it |
| Summary alias classification | NORMAL, full-header identity comparison against `startFact` | Compare the raw scalar premise and result by the predicate in section 6 |
| DEMAND alias guard | DEMAND bypasses identity classification | A no-memory-effect result skips aliases in both layers |
| Packed summary output | Semantic subtraction extracts an exact identity part | Partition actual path/mark payloads into no-effect and effect parts without changing them |

These changes modify work admission. The alias change also replaces the
old selected alias rule: a same-path, same-mark TAINT result can skip aliases
when its tail differs. Do not claim equality with the old engine's larger
alias-generated candidate set. The reference for alias equivalence is the
new scalar rule. Prove the optimized partition against that rule.

Keep these mechanisms unchanged:

- Mark groups, rule conditions/actions, complete local condition admission,
  source permissions and source/sink occurrence identities.
- Every contributing atomic run-1 origin. ZERO needs a permitted complete
  all-ZERO binding. Later FULL ND retains complete premises.
- AP transfer, field kills, alias write semantics, cleaner behavior, empty
  run-1 field exclusions and existing layer-only demotion.
- Initial grammar, ordinary Base requests, static read/refinement consumers,
  three-set field requests, canonical group answers and exact class markers.
- Summary notification batching, actors, cancellation, counted work,
  complete barriers and selected run-2 demand matching.
- Run-1 GROUP/forward/limit 1; run-2 FULL/backward/limit 1; existing later
  limits. There is no run-0.
- Later FULL alias/record admission, native must-record timing, reversed
  call guards, NORMAL persistence and the diagnostic static-ablation flag.
- The existing empty `trackFactsReachAnalysisEnd` policy.

Do not add GroundingSupport, field validation, an actual-input history,
backward static routing, a global relation table, a new barrier/cache,
reference-manager changes or IR changes. If an implementation requires
another major semantic change, expose it before implementation.

## 2. Domain and reused operation contracts

Use these terms:

```text
fact     = (base, fieldChain, fieldTail, mark, E, layer)
FLOW mark = (*, mx)              mx is a set of excluded mark selectors
TAINT mark = Group(g)            on run-1
summary  = (method, initial, result)
edge     = (initial, currentFact, operationStage)
```

`E` excludes field successors. `mx` excludes marks from a FLOW fact. They
are different. Field-tail kinds are STAR (`*`), EXACT (`.`), ANY (`[any]`)
and MUST (`[any-taint]`). A FLOW mark slot is not a STAR field tail.
Use the full field chain, including the class accessor of a static field.

A static field starts with `(STATIC, [class C, field f])`. The exact
`S.C / Group(g)` carrier is a special global mark, not a static field.
Preserve its existing route. A field cannot supply that carrier or receive
a group from it. Class query metadata does not create a class-wide fact.

The legal run-1 initials remain:

| Position | Tail | Mark |
| --- | --- | --- |
| ZERO | EXACT | Control |
| Nonstatic base, empty chain | STAR | FLOW |
| S.C.f | STAR | FLOW |
| Nonstatic base, empty chain | EXACT or STAR | Group(g) |
| S.C.f | EXACT or STAR | Group(g) |
| S.C | EXACT | Special Group(g) carrier |

These are eight families. Initial E and FLOW mx are empty. No ANY/MUST,
class STAR, nonstatic field-path or S-root initial is added. Only ZERO and
nonstatic initials enter the method-entry abstraction. Static descriptors
resume at admitted operations. Refinement does not apply entry abstraction
again. A current fact can have a tail that its initial cannot have.

The implementation reuses these operations with their current contracts:

```text
bounded(p, value): existing disabled-statics filter, runtime mark projection,
                  layer policy, run1Cover and field limit, in existing order
retain(p, value):  ordinary method summary insertion, publication and demand selection
compose(input, summary): existing AP matcher and summary application
request(Base(b), groups): existing merged temporary/committed request pipeline
refineStatic(point, groups): existing pool interest and point replay
```

`bounded` preserves the complete represented relation, including actual
tail and mx. It does not replace a result with its declared operation
target. Point replay uses a surviving saved FLOW image, not an old value
reintroduced across a kill. Requests ignore E/mx for routing; actual
composition still applies its existing mark and AP checks.

All run-1 group TAINT and static-dependent results are DEMAND. A static
field conclusion is DEMAND even when its original is nonstatic FLOW.
For the static-result boundary below, keep the current explicit DEMAND
assignment after runtime projection and before `run1Cover` and the field
limit. `forceRun1Layer` alone does not enforce this for a nonstatic FLOW
original. Ordinary independent nonstatic FLOW can remain NORMAL. A
surviving field-exclusion update demotes with empty E and preserves its
AP/tail; definite overwrite hits still kill and disjoint fields still pass.

## 3. Storage ownership

| Storage | Owner and contents | Admission and purpose |
| --- | --- | --- |
| Method `RunSummaryStore` | Method actor; ordinary summaries and selected demand entries | Both local results and promoted call results; ordinary method analysis and run-2 guidance |
| Published `SummaryStorage` | Existing publication/request owner | Existing summary batches and Base requests; no replacement store |
| Local FLOW writer directory | Method actor; `(initial, complete bounded scalar output Pattern, layer) -> id` | Local producers only; answer output-group interests from the writer's input |
| Global write-presence set | Static service; merged existing output regions | Actual local results and existing computed-supply commits; activate matching reads |
| Global FLOW writer index | Static service; `(method, writerId)` and output region, admitted group set | Local writer registrations only; join requested output groups once per writer |
| Computed supply joins | Existing method actor pending fixed-group bindings | Preserve ZERO supply and all-static conditional group evaluation from any retained summary |
| Field group state | Static service; `req`, `reach`, `processed`, pending consumers | Preserve the existing request/write protocol and canonical answers |
| Read and refinement directories | Existing method actor/service | Preserve read activation, saved images, delayed replies and completed-row removal |
| Alias classification | Call work item and suspended call state | Ephemeral per-output classification; not part of fact/store identity |

The field sets stay disjoint:

```text
req       = requested, not written
reach     = written, not requested
processed = requested and written
```

A newly requested group in `reach` becomes `processed` and answers at once.
An unanswered group enters `req`. Writers receive groups in `req` or
`processed`. A new unrequested group stays in `reach`; do not broadcast it
to all old FLOW writers. Preserve writer-first and request-first joins.
A request never supplies a group. Release answered pending consumers as
before. Data waiting for a missing group is not runnable work by itself.

No caller promotion may append a write-presence or FLOW-writer registration.
A computed group supply from that summary can still activate a read through
the existing supply commit. That is not a new write point. Conditional
supply joins can still have pending inputs; they are not FLOW writer rows.

## 4. Local static result and caller promotion pipeline

### 4.1 Eligible producer

Admit a local write only after all these checks succeed:

```text
an actual local transfer/action executes
its existing permissions and complete condition binding permit the action
result filters accept a nonempty output
the actual bounded output contains a static field
disableRun1Statics does not remove that field effect
```

Local producers include instruction writes, alias micro-edges, source/model
actions, entry/exit rule actions, and field-cleaner write-back micro-edges.
A call statement can contain a local source or alias micro-edge. It remains
a producer. Applying a callee summary at that statement is not a producer.

Preserve existing write-metadata transfer/action matching. Use the
completed transfer/action identity, not target overlap alone. A
local source/conjunction callback can have `MicroEdge == null` after it
has completed its action. Do not reject that producer solely because the
wrapper callback has no micro-edge object. Keep complete rule admission.
No reached descriptor or prescan write candidate is sufficient by itself.

A direct `CleanStep.Clean` primitive callback is not a micro-edge write.
It must not fabricate one. Preserve its actual result, mark requests,
demand publication and computed supplies. The `CleanStep.Field` write
phase executes micro-edges and remains eligible. This changes registration,
not what a cleaner kills or retains. An untouched pass does not admit a
write. An empty, killed, denied or filtered result does not admit one.

Explicitly promote the accepted direct-cleaner result without local write
admission. Passing `operation = null` into the old `emitOperationResult`
is insufficient: that path currently drops a static result. Preserve the
result through `promoteStaticSummary` instead, including computed supplies.

### 4.2 Two entry points, one bounded result policy

```kotlin
// Pseudocode. LocalTransfer is an invocation argument, not a fact label.
fun onLocalStaticResult(at: OperationInput, producer: LocalTransfer, raw: Facts) {
    bounded(at.premise, raw) { premise, result ->
        retainStaticSummary(premise, result)
        admitLocalWrite(producer, premise, result)
    }
}

fun promoteStaticSummary(premise: PremiseKey, raw: Facts) {
    bounded(premise, raw, ::retainStaticSummary)
}

fun retainStaticSummary(premise: PremiseKey, result: Facts) {
    val delta = summaries.add(premise, result) ?: return
    queuePublications(premise, delta) // Also select ordinary demand guidance.
    publishComputedGroups(premise, delta)
}
```

Run local write admission even if summary insertion has no fresh delta.
An equal promoted summary can have arrived first. The local writer
directory has its own exact-key deduplication. Do not reconstruct producer
identity from a delta produced by a merged summary store.

For each actual static-field leaf, local admission publishes write presence.
A legal FLOW result has a nonzero FLOW initial and also admits a FLOW
writer. ZERO/FLOW is invalid under the existing kind invariant; do not add
that edge shape. A TAINT output does not become a FLOW writer. ZERO/TAINT
can supply its computed group. Use the existing writer key and service protocol.
Do not add the operation site to that key: equal local relations in one
method keep the existing deduplication. The operation identifies eligible
production; it is not a new storage dimension.

### 4.3 Computed group supply

```kotlin
fun publishComputedGroups(premise: PremiseKey, delta: Facts) {
    if (delta !is TaintTree) return
    when {
        premise.isZero -> offerComputedPoolResult(delta)
        premise.hasOnlyStaticMembers() -> keepExistingCompleteStaticBinding(premise, delta)
        else -> Unit
    }
}
```

Preserve this path for local and promoted summaries. A hypothetical
nonstatic initial is not an actual group source. All-static conditional
results still require actual matching pool input for their declared group;
do not treat them as unconditional or mark-preserving FLOW. Keep the
declared output group and AP image. Requests and ZERO activation supply
no group. Empty copy cycles cannot create the first group.

### 4.4 Caller application

Use the ordinary matcher, input binding, original selection and layer
composition. When the resulting current base is a static field, promote
the resulting summary immediately. Do not propagate that current static
fact through the caller CFG. Preserve early publication and method-queue
notification batching. The effect does not prove normal exit.

```kotlin
fun onComposedCalleeResult(premise: PremiseKey, result: Facts) {
    if (run1 && isStaticFieldResult(result)) {
        promoteStaticSummary(premise, result)
        return
    }
    resumeOrdinaryCallStages(premise, result)
}
```

For a packed STATIC result, process real fields and exact global carriers
through their existing separate routes; do not drop the whole tree because
one part is a field. In the run-1 ZERO activation of a static FLOW summary,
retain its static initial. ZERO is not that initial's concrete group input.
Later runs require the actual static premise and use the existing path.

Replace the reachable run-1 calls to `retainStaticCallEffect` with this
promotion. Remove its synthetic `StaticOperation.Write`. Cover the special
`applyStaticSummary` path, ordinary `applyParts` composition, direct ZERO
summary returns and any other reachable run-1 static conclusion. A shared
adapter can route the final result; do not rely on one branch alone.
Later FULL record/ND code remains unchanged.

### 4.5 Writer lifetime and replies

A local writer owns its immutable bounded scalar relation. The relation
already fits in its existing key. Do not retain a cumulative Facts root.

```kotlin
fun localWriterResult(key: WriterKey): Facts = ap.factsOf(
    key.output.fact, key.output.exclusion, key.layer
)
```

Use that relation for local FLOW-writer admission checks and composition.
Do not require `currentValues` to find the exact leaf in a merged summary.
The local result remains a valid run-1 derivation when its representation
is subsumed by another summary. Do not unregister it merely because the
exact leaf is absent. Fixed-group supply joins retain their complete
binding checks and current supply protocol.

On a fresh output-group interest:

```text
nonstatic FLOW original b:
    offer Base(b)/group through ordinary temporary/committed requests
static FLOW original C.f:
    request the actual group at C.f once for this local writer/group
    on reply, compose the immutable writer relation and offer its result
```

Keep actual composition checks, including mx, and both arrival orders.
An excluded group can still cause a request, but cannot be supplied through
a FLOW relation that excludes it. Group-valued origins never propagate a
new ordinary Base request. Preserve pending-row detach/removal and counted
reply lifetime.

## 5. Static examples and required behavior

### 5.1 One write and many wrappers

```java
void leaf() { C.a = C.b; }
void wrap1() { leaf(); }
void wrap2() { leaf(); }
```

After the C.b read activates, let `R = C.b/*/FLOW -> C.a/*/FLOW, DEMAND`.
All three methods retain R. Only leaf's local write admits a writer for R.
An output request for G creates one writer source interest for C.b/G,
not one per wrapper. All wrapper summaries and demand entries still publish.

### 5.2 Nonstatic source passed to a setter

```java
void set(Object p) { C.f = p; }
void main() { set(src()); consume(C.f); }
```

```text
set local writer: p/FLOW -> C.f/FLOW
C.f/G interest -> Base(p)/G -> ordinary caller-input replay -> p/G initial
set local result: p/G -> C.f/G
actual caller composition: ZERO -> C.f/G
summary supply observer: offer C.f/G
```

The caller registers no writer. Keep supply-first, request-first and late
callee-request cases. The nonstatic request can traverse several wrappers.

### 5.3 Static source passed as an argument

```java
void main() { set(C.g); consume(C.f); }
```

```text
set's Base(p)/G request reaches the caller's static-origin input
existing point continuation requests C.g/G
actual C.g/G reply replays the argument and permits set's p/G initial
caller result: C.g/G -> C.f/G
existing conditional supply join matches actual C.g/G -> offer C.f/G
```

The new caller relation needs no new FLOW writer. Do not drop it because
the callee's original was nonstatic. Without an actual C.g/G derivation,
no C.f/G supply follows this path.

### 5.4 Two ordering controls

```text
promoted R stored -> local operation produces R:
    summary delta can be empty; still admit the local writer

local R admitted -> broader promoted summary absorbs its exact stored leaf:
    R's immutable local relation still answers its group interests
```

Several callers retain their own summaries and bindings. They can share
one callee's ordinary request. A writer is not evidence that all callers
have supplied the group. Complete actual caller application is still needed.

## 6. Scalar summary no-memory-effect rule

Classify a nonzero singleton callee summary in raw callee coordinates.
Use its actual initial descriptor and each actual scalar conclusion.
Do not call `startFact` to change an initial tail before classification.

```kotlin
fun hasNoMemoryEffect(initial: PathFact, result: PathFact): Boolean {
    if (initial.base != result.base || initial.path != result.path) return false
    return when {
        initial.mark is MarkSlot.Star && result.mark is MarkSlot.Star ->
            initial.tail == Tail.STAR && result.tail == Tail.STAR
        initial.mark is MarkSlot.Concrete && result.mark is MarkSlot.Concrete ->
            initial.mark == result.mark
        else -> false
    }
}
```

For FLOW, both **field tails** must be STAR. For TAINT, compare actual
runtime marks and ignore field-tail kind. Run-1 marks are immutable group
atoms. Full marks in the formal scalar model remain distinct by complete
identity; this does not select a later FULL runtime change.

Layer, E and FLOW mx are not extra tests in this predicate. Keep their
values in the result. This is an alias-admission test, not equality of AP
denotations, complete transfer relations, or whole-method behavior. It does
not mean that a method has no writes.

ZERO summaries are effect results. An unclassified producer cannot use
this rule to skip aliases. An invalid ND run-1 premise is not reduced to
one member for classification.

| Raw initial -> result | No memory effect |
| --- | --- |
| `p.f/*/FLOW -> p.f/*/FLOW`, either layer | Yes |
| `p.f/*/FLOW -> p.f/[any]/FLOW` | No |
| `p/*/G -> p/./G` | Yes |
| `p/./G -> p/[any]/G` | Yes; keep the actual broader result |
| `p.f/*/G -> p.g/*/G` | No |
| `p/*/G -> p/*/H` | No |
| `arg0/*/FLOW -> return/*/FLOW` | No |
| `ZERO -> p/*/G` | No |

If binding maps arg0 and return to the same caller local, the last
nonzero example remains an effect. Classification precedes binding.

## 7. Packed partition and call-state lifetime

### 7.1 Lossless partition

Partition each original scalar path/mark payload by section 6. A packed
result can have both classes, including different marks at one path.
Retain the actual tail, E, mx, layer and mark in each part.

```kotlin
fun run1SummaryParts(initial: InitialAp, result: Facts): Parts {
    if (initial.isZero) return Parts(effect = result)
    return structuralPartition(result) { leaf ->
        hasNoMemoryEffect(initial.fact, leaf.fact)
    }
}
```

```text
initial p/*/G; outputs:
    p/./G, p/[any]/G -> no-effect part
    p.f/*/G, p/*/H   -> effect part
```

Do not classify a whole tree by its first leaf or full header. Do not use
semantic `without(startFact)` as the partition: tail absorption and semantic
subtraction do not define this structural predicate. An implementation can
walk the existing trie once; it need not allocate one fact per scalar leaf.

Each scalar payload belongs to exactly one part. The union preserves the
original represented outputs. Empty parts are omitted. Keep the parts
separate through call stages until alias admission; do not merge their
different classifications into one Boolean. Equal complete payloads can
share data, but an effect classification must not be lost by merging.

### 7.2 Producer classification and preservation

Use the existing ephemeral flag, with a clear run-1 interpretation:
`true` means no memory effect, `false` means effect, and `null` means not
classified. Do not persist it in ordinary fact or summary keys.

| Event | Run-1 flag |
| --- | --- |
| Nonzero callee summary arm | Section 6 on the raw arm |
| ZERO callee summary or new source/GEN output | false |
| Existing unresolved identity/default or constructor pass | Existing producer policy; do not introduce another unresolved-rule rewrite |
| Binding, return cleaner, rewriter, layer demotion, field cut | Preserve the producing arm's flag |
| Saved operation and later point reply | Preserve through the complete `CallResumeState` |
| No classified producer | null; it does not justify skipping aliases |

For default unresolved/constructor results, keep the existing stage policy.
The new scalar summary classifier does not redefine pass rules. The new
run-1 guard below applies to their existing classifications too.

An independent source output gets its own effect flag; it cannot inherit
a no-effect flag from an unrelated input. Local source/alias static writes
still follow section 4 when they execute. A summary flag never suppresses
another genuine local producer.

### 7.3 Guard and unaffected result path

```kotlin
fun admitsAliases(run1: Boolean, noEffect: Boolean?, layer: Layer): Boolean =
    if (run1) noEffect != true
    else existingFullAliasGuard(noEffect, layer)
```

Run-1 has no DEMAND bypass. Unknown classification is conservative and
admits aliases. Keep existing later FULL/native behavior and unguarded
backward reversal. Do not alter record classifiers to reuse this rule.

Only the guarded ALIASES branch skips a no-effect part. Keep ordinary
summary composition, return rewriting, bind-back, successors, cuts and
selected demand entries. A result sent to the early static-promotion route
still promotes; the alias classifier is not a blanket static-output filter.

```java
void set(Box b, Object t) { b.f = t; }
```

```text
b/*/FLOW -> b/*/FLOW      no effect: skip this arm's aliases
t/*/FLOW -> b.f/*/FLOW    effect: apply this arm's aliases
```

The first arm does not suppress the second. It does not certify the method
as write-free. The raw result remains available even if it has a broader
TAINT tail than its initial.

## 8. Interaction, handoff and observation contract

Apply the local-writer fix first in validation. Then apply the alias fix.
The alias guard can prevent a local alias micro-edge from executing. A
skipped edge produces no result and admits no writer. An admitted alias
micro-edge can still produce a real static field and admit its local writer.
Do not register it in anticipation of the guard.

Method summary promotion remains independent of writer admission. Publish
all retained early effects with their original, actual output and DEMAND
layer. Select demands as before, including caller summaries and independent
local rule/check/cleaner obligations. Computed group supplies preserve the
group that later FULL marks must match.

At the complete run-1 barrier, drain existing queues, Base request commits,
summary publications, computed supply joins, group replies and point
continuations. Dormant unanswered requests do not block the barrier. Keep
the existing implicit static demand permission for run-2. Run-2 needs the
actual static input and full mark; ZERO no longer satisfies C.f. Do not add
an executable run-1 S-root initial, identity summary or broad mark demand.

The writer-only change must preserve requested global group closure and
method demand guidance under the reused transfer/request contracts. The
combined change must preserve the new scalar alias contract and its
required handoff. Neither result is an exact-equality claim for old
alias-generated false candidates. Report/source/sink identities and rule
occurrences remain semantic observations; batch-dependent diagnostic
callback counts can differ, as already selected.

## 9. Implementation map and proof-first order

| Component | Required change |
| --- | --- |
| `EngineAlgebra.operationResult`, local completed actions | Route actual accepted local static-field results to local admission after result filters and existing bounds |
| `RunMethodAnalyzer` | Share existing bounded result policy; separate retention/computed supplies from local write admission |
| `Run1PointResume.summary` | Split local FLOW writer/write-presence admission from generic computed TAINT supply handling |
| `Run1PointResume` writer callbacks | Compose the immutable local relation; no exact merged-summary-leaf liveness requirement |
| `RunMethodCallServices.retainStaticCallEffect` | Replace with pure promotion; remove synthetic write metadata |
| All run-1 static callee-result adapters | Promote with preserved origins, layers and ordinary publication; register no writers |
| Direct `CleanStep.Clean` static result | Keep result/demand/supply, remove synthetic write-point admission |
| `summaryParts` | Add run-1 structural path/mark partition; retain FULL implementation |
| `Guard.MemoryEffect` and call invocation state | Use run-1 no-effect admission with no DEMAND bypass; preserve arm classification through every resume |
| Scalar reference call walk and fixtures | Use the selected run-1 scalar guard; keep later references unchanged |

First check the constructive models and the
[proof dossier](run1-static-writers-and-summary-aliases-proofs.md). Then add
the focused integration controls below before changing runtime behavior.
No runtime implementation is part of this specification delivery.

1. Separate local production, summary retention and computed group supply.
   Prove independent local admission and immutable writer validity.
2. Implement operation-only registration. Verify its closure and demand
   adapters. Measure this change separately.
3. Implement the scalar predicate, packed partition and flag propagation.
   Verify against the selected scalar reference and recheck static admission.
4. Run the required existing common/JVM/rule/application gates. Run the
   approved real-project configurations with `runProjectAnalyzer` only.

## 10. Required validation and performance claims

| Area | Required controls |
| --- | --- |
| Producer origin | Wrapper summaries register zero writers; actual instruction, alias and local rule outputs remain eligible; no-output/filter/permission rejection registers none |
| Writer lifetime | Promoted-before-local; repeated local output; local writer followed by summary tail/mx subsumption; late group reply |
| Group closure | `set(src())`, `set(C.g)`, nested wrappers, multiple callers, recursion, group-changing source, all arrival orders and an unseeded cycle |
| Result geometry | Existing field limit, tail, E/mx routing-versus-composition distinction, exact global carrier and mixed packed static values |
| Scalar alias rule | Both layers; all legal FLOW tails; same/different paths and actual marks; ZERO; TAINT tail variants; FLOW/TAINT mismatch |
| Packed alias rule | Several paths and marks, mixed parts, lossless payloads and empty parts; compare to scalar reference |
| Invocation state | Raw arg/return distinction before binding; cleaners, rewriter, cuts, layer-only demotion and delayed point resumes preserve each arm's flag |
| Genuine effects | Mutator identity arm plus field-change arm; local source/GEN output; local static alias admitted only after the guard |
| Later analysis | Existing FULL/native/ND/reversal controls; required group demand and static handoff; `disableRun1Statics` remains diagnostic |

Count local writer admissions, attempted call registrations, standing FLOW
writers, writer/group jobs, pending supply joins, static source interests,
alias applications and queued replies. Call summary composition must cause
zero direct write-point/FLOW-writer registrations. It can still cause a
computed supply. Do not report supply joins as writer rows.

For one local relation copied into N wrappers and K requested groups, the
registration path now has one local writer and up to K writer/group jobs,
instead of N+1 writers and up to `(N+1)*K` jobs. The N+1 ordinary summaries
remain. This is a bound for that pattern, not for all project work. Alias
admission removes the ALIASES branch for each classified no-effect arm;
it does not remove the arm's other call work. No project speedup is proved.

Use the approved OWASP, conductor, Thingsboard and Stirling-PDF settings.
Report run-1 engine seconds, allocation/retained memory and latest reached,
completed and accepted run separately. The goal is run-1 engine completion
within 60 seconds. No new performance measurement is part of this spec.
The latest closed causal measurements use runtime `d7244c08c`: all four
reach run-1; OWASP, conductor and Stirling-PDF complete/accept run-1;
Thingsboard reaches run-1 and fails with OOM before completion/acceptance.
See the [closed report](../docs/reports/bidirectional-run1-causal-performance-20261021.md).
