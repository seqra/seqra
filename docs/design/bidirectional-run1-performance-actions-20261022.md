# Run-1 cost: explanation and recommended changes

Date: 22 October 2026. Status: updated two-change proposal, not an implementation spec.
Runtime: `d7244c08cdaefbb157f42e4d89731d301b97e48f`.
The [closed investigation](../reports/bidirectional-run1-causal-performance-20261021.md)
contains the 16 configurations, 13 controls and source/heap/profile bindings.
No analyzer code changes are part of this document.

The selected scope is now: register static write points only for local
transfer results, then correct run-1 summary alias admission. Sharing
identical static FLOW relations globally is deferred until we measure the
first fix. The other optimization recommendations are removed from this
iteration.

## 1. What the comparison establishes

These are **run-1 engine** measurements with statics and aliases ON.

| Project | 15 October | Fresh current measurement | Iteration outcome |
| --- | ---: | ---: | --- |
| OWASP | 331.657 s | 230.832 s | Both complete run-1 |
| conductor | 60.210 s | 67.134 s | Both complete run-1 |
| Stirling-PDF | 36.102 s | 36.884 s | Both complete run-1 |
| thingsboard | OOM | OOM | Both reach run-1; neither completes it |

Thus a uniform current time regression is not established. These are
single runs from different campaigns on a shared host. The two failed
Thingsboard prefixes do not give a speed ratio. The old conductor and
PDF OOMs occur in later iterations, not in run-1.

Resource cost still needs attention. The complete run-1 recordings have
these sampled allocation weights, in decimal GB:

| Project | 15 October | Current |
| --- | ---: | ---: |
| OWASP | 328.037 | 549.175 |
| conductor | 116.271 | 206.214 |
| Stirling-PDF | 114.193 | 111.223 |

Allocation weights estimate churn; they are not retained heap or an exact
allocation census. They support concern about OWASP/conductor resource
cost, but do not isolate one source change across campaigns. For example,
conductor has fewer accepted N2 deltas, 1,026,443 to 741,966, and fewer new
initials, 103,006 to 72,870. Fewer admitted facts did not ensure less work
before admission or less auxiliary allocation.

The older allocation/work counters are in the
[15 October report](../reports/bidirectional-added-facts-removal-performance-20261015.md).
Current allocation weights are in the closed study's
`validation/matrix-profiles/selected-frame-summary.json`.

## 2. Why the optimizations do not ensure a much faster run-1

The cost depends on reached relations and operations, including:

```text
(method, statement, original premise, current Facts, layer, operation stage)
```

Reducing mark values or ND premise products does not remove the other
coordinates. Existing storage deduplication runs after some expensive work.

### ND work was not the only large population

The complete Thingsboard run-1 heap on 15 October contains no PremiseSet
objects. Its stored input entries have singleton premises. This does not
prove that transient ND processing was absent. It does show that removing
ND premise products could not remove its dominant retained input relation.

The current heap has about 12.1 MB in run-1 factored result stores, while
the static-service/point-continuation owner union retains about 2.985 GB.
These owner selections overlap; their bytes must not be added. Further ND
store tuning does not target the largest current static population.

### Current statics create a second closure over FLOW relations

The current path can operate before a concrete group is available:

```text
published FLOW write
  -> activate a static read
  -> propagate its static original with nonstatic current facts
  -> apply call summaries and alias transfers
  -> publish further static FLOW summaries
  -> register writers and activate further readers
```

Static conclusions stop at their operation. However, static premises can
remain on nonstatic current facts, and static summaries are applied through
callers. Removing ordinary static CFG propagation did not remove this work.

Compared with the old core, more inputs use DEMAND, and reached static alias
outputs now publish writers. The existing alias guard admits DEMAND inputs
even for an identity candidate. These changes can interact. Identity-only
controls still OOM, so this one guard path is not a complete explanation.

The current Thingsboard failure has about 1.30 million FLOW writer rows,
32.17 million pending group memberships, and millions of queued replies.
Four static FLOW premises account for 97.9% of physical refinement points.
This is a large relation over few values. It does not require many groups.
Removing the two alias publication hooks restores completed/accepted run-1
in 96.253 seconds. This identifies a cause family, not a safe deletion.

### Ordinary work remains expensive with statics OFF

OWASP still completes run-1 in 152.372 seconds with both features OFF.
The observer records 72.39 million actor steps, with 99.967% in one package
unit. AP reads/writes, filtering and trie operations occur inside cleaner
callbacks. The primitive cleaner test is only part of that cost.

Group abstraction can also preserve paths that a full-mark cleaner killed.
Several full-name rewriter steps can still map to one group but retain
different field steps and continuation positions. These effects offset
some savings. However, the aggressive GROUP-kill control still performs
83.1 million form accesses. Cleaner survival alone does not explain the
ordinary workload.

The old BaseOnly core shared supported ordinary transfers across origins.
The current core caches compiled forms, then applies their AP kernels per
origin. This difference predates 15 October. It is relevant to the absolute
performance gap, not evidence of a newly introduced regression.

### Some severe historical regressions are already removed

The first corrected static refactor expands broad roots/classes into many
field initials. Its NORMAL static complement also splits broad inputs into
root/class pieces. Controlled removal of the latter cuts a completed
historical run-1 from 149.046 to 53.241 seconds, with nearly equal initial
count. Caller groups fall from 2.61 million to 522,141. The FLOW/trie code
is unchanged, but its submitted workload changes substantially.

That residual subsystem and the old whole-graph replay router are absent
from the current implementation. Their diagnostics explain history; they
are not current fixes.

## 3. First fix: register only operation-produced static writes

### Current versus new

Current summary retention mixes two operations:

```text
retain a static summary
  -> publish method summary and demand guidance
  -> register static write presence and FLOW writer interests
```

This also runs for summaries copied from a callee. The caller fabricates
a `StaticOperation.Write` even though it has only applied a summary.

New rule: register a static write point only when a local micro-edge or
local rule-action application produces a nonempty static-field result.
Applying a callee summary only promotes the composed edge to the caller's
summary storage. It does not create another write point or FLOW writer.

```java
void leaf() { C.a = C.b; }
void wrap1() { leaf(); }
void wrap2() { leaf(); }
```

Assume a corresponding write has activated the read of C.b. Let
`R = C.b/*/FLOW -> C.a/*/FLOW, DEMAND`.

```text
Before: leaf, wrap1 and wrap2 each retain R and register a writer for R.
After:  all three retain R; only leaf's local write registers a writer.

Request C.a/G:
Before: up to three source listeners for C.b/G and three compositions.
After:  one writer, one source listener and one composition.
```

This removes registrations at their source. It does not need a new global
relation table. Different local write points can still produce an equal
relation; global sharing of those remaining cases is a separate proposal.

### Separate summary retention, write admission and computed supply

Use three explicit responsibilities. `LocalTransfer` below names the
executed micro-edge or completed local action; it is not a new support
label stored on ordinary facts.

```kotlin
// Pseudocode. Normalization is the existing bounded run-1 policy.
fun onLocalStaticResult(at: OperationInput, producer: LocalTransfer, raw: Facts) {
    forEachBoundedStaticResult(at.premise, raw) { premise, result ->
        retainStaticSummary(premise, result)
        admitLocalWrite(producer, premise, result)
    }
}

fun promoteStaticSummary(premise: PremiseKey, raw: Facts) {
    forEachBoundedStaticResult(premise, raw, ::retainStaticSummary)
}

fun retainStaticSummary(premise: PremiseKey, bounded: Facts) {
    val delta = summaries.add(premise, bounded) ?: return
    queuePublications(premise, delta) // Includes existing selected demand edges.
    publishComputedGroupResults(premise, delta)
    // No write-point or FLOW-writer registration here.
}
```

The shared boundary keeps the existing disabled-statics filter, runtime
group projection, DEMAND layer, `run1Cover` and field limit. Apply the
existing policy in the same order. Do not widen output geometry or erase
FLOW mark exclusions to simplify registration.

`admitLocalWrite` publishes write presence from the actual bounded field
result. A nonzero FLOW result also registers its ordinary writer relation.
Use the existing actor writer key `(premise, complete output Pattern,
layer)` and service owner/id protocol. Do not add a new point directory or
global equivalence table in this fix.

Write admission must not depend on a fresh summary-store delta. A call
can promote an equal summary before a local operation produces its result.
The local operation must still reach the writer registry. That registry's
own key decides whether the local write registration is fresh.

Computed group supplies remain enabled, including supplies established by
caller summary composition. This is the user's selected clarification:
remove call-created write points, keep computed group supplies.

```kotlin
fun publishComputedGroupResults(premise: PremiseKey, delta: Facts) {
    if (delta !is TaintTree) return
    when {
        premise.isZero -> offerComputedGroups(delta)
        premise.hasOnlyStaticMembers() -> keepExistingConditionalGroupJoin(premise, delta)
        else -> Unit // A hypothetical nonstatic input does not supply a global group.
    }
}
```

Keep complete binding checks for conditional group supplies. This code
does not turn a conditional result into an unconditional group. A computed
supply can still activate reads through the existing pool commit logic;
that does not create a new FLOW writer or a write-point registration.

### Local operations include call-site micro-edges

This is a distinction between a local transfer and imported summary
composition, not between ordinary statements and call statements:

- An executed local field-write, source/model or alias micro-edge that
  produces a static field remains a producer, including at a call stage.
- A locally completed source/conjunction action remains a producer. The
  current `operationResult(null, ...)` callback does not mean that its
  result came from a callee. Preserve existing transfer/action matching,
  rule permissions and result filters.
- Merely reaching a write descriptor is insufficient. It must have an
  accepted result. Summary application must not fabricate that descriptor.
- Keep static-read registration and current-point group refinement. They
  are consumers, not extra writer registrations.

The direct `CleanStep.Clean` callback is not a micro-edge write. Remove
its synthetic write-point route too; keep its ordinary result, requests,
demand edges and computed group-supply behavior. A `CleanStep.Field` write
phase executes real micro-edges and still qualifies when it produces a
static field. This changes registration only, not what either cleaner
removes or preserves.

Exact global class markers keep their existing special path. They are
not class-wide static field writes. No later-run, field-write, cleaner,
condition, ND-origin or mark-group contract changes are selected here.

### Keep a local writer valid when summaries merge

The current `currentValues(writer)` requires its exact leaf to remain in
the combined summary store. Previously a merged replacement could acquire
another writer through the summary hook. That hook will no longer register
writers for promoted summaries.

Therefore a local writer must own its immutable bounded relation, already
represented by its key. A summary merge must not disable that writer:

```kotlin
fun writerRelation(writer: WriterKey): Facts = ap.factsOf(
    writer.output.fact, writer.output.exclusion, writer.layer
)
```

Use that relation for local FLOW-writer eligibility and static-origin
composition instead of requiring an exact current summary leaf. Fixed-group
conditional supply joins keep their existing binding checks. Keep the local
FLOW relation's field tail, E and mx. The relation
has a reached local derivation and stays valid in the monotone run-1
closure. Do not retain an old cumulative Facts root or add grounding state.
State and prove this writer-lifetime invariant before implementation.

### Required argument-binding cases

```java
void set(Object p) { C.f = p; }
void main() { set(src()); consume(C.f); }
```

The local `p/FLOW -> C.f/FLOW` writer answers a `C.f/G` interest by offering
the existing `Base(p)/G` request. Ordinary request replay emits `p/G` for
the actual caller input. The local write produces `p/G -> C.f/G`; caller
composition produces `ZERO -> C.f/G`. Summary retention publishes that
computed group supply. The caller registers no new writer.

```java
void main() { set(C.g); consume(C.f); }
```

For this case, the same Base request reaches the caller's static-origin
input. The existing point continuation requests `C.g/G`; a pool answer
replays the call input and permits the callee's `p/G` initial. Caller
composition then retains `C.g/G -> C.f/G`. Keep the ordinary all-static
conditional group join so that the actual `C.g/G` supply establishes
`C.f/G`. Do not require a caller FLOW writer for this step, and do not
invent G when `C.g/G` has no derivation.

### Implementation boundary and validation

Replace `retainStaticCallEffect` with direct `promoteStaticSummary` calls.
Remove its synthetic `StaticOperation.Write`. Apply this to every reachable
run-1 static conclusion from callee-summary composition, including
`applyStaticSummary` and `applyParts`. Later-run native and ND handling
remain unchanged. Real call-stage micro-edge results keep the local route.

Split `Run1PointResume.summary` into local write admission and computed
group-supply handling. Generic summary retention calls only the latter.
Local filtered/bounded results call the former independently of summary
deduplication. Keep batching, cancellation and complete-barrier accounting.

Before implementation, prove local-writer coverage of the removed
caller-writer jobs through existing Base request and static-point replay.
Cover both arrival orders, multiple callers, recursion, conditional source
actions, group-changing outputs and exclusion-bearing FLOW relations.
Prove that all method summaries and selected run-2 demands still publish,
and that summary merging cannot disable a local writer. These are required
proof obligations, not completed proofs.

Measure writer admissions by producer kind before and after this fix.
Promoted summary edges must produce zero write-point/FLOW-writer admissions.
Check wrapper-chain writer counts, local-write-after-promoted-summary
ordering, both argument-binding examples, delayed groups and an unseeded
copy cycle. Preserve computed supplies and all required run-2 demands.
No new performance measurement is claimed. The latest measurements above
reach run-1 on all projects; Thingsboard does not complete run-1.

## 4. Selected change: correct summary alias admission

This follows the write-registration fix. It prevents redundant alias
outputs from local micro-edges that can still create writer/refinement
work. Measure the two changes separately.

### The selected predicate

For a singleton summary edge, compare its raw initial and scalar conclusion
before caller binding or return rewriting. The bases and complete field
chains must be equal. Then:

- FLOW: both field-tail kinds must be STAR.
- TAINT: the actual marks must be equal; field-tail kinds do not matter.

```kotlin
// Proposed scalar helper. This does not transform either fact.
fun hasNoMemoryEffect(initial: PathFact, result: PathFact): Boolean {
    if (initial.base != result.base || initial.path != result.path)
        return false

    return when {
        initial.mark is MarkSlot.Star && result.mark is MarkSlot.Star ->
            initial.tail == Tail.STAR && result.tail == Tail.STAR

        initial.mark is MarkSlot.Concrete && result.mark is MarkSlot.Concrete ->
            initial.mark == result.mark

        else -> false
    }
}
```

STAR here means the field-tail kind, not merely a FLOW mark slot. Do not
convert an ANY tail to STAR to make the predicate pass. In run-1, compare
runtime group atoms. A later FULL use would compare the complete marks,
not only their groups.

Layer, field exclusions and FLOW mark exclusions are not extra equality
conditions in this selected predicate. Preserve them in the propagated
facts. Ignoring them for alias admission does not erase them or make
transfers with different exclusions globally interchangeable.

| Initial -> conclusion | No memory effect? |
| --- | --- |
| `p.f/*/FLOW -> p.f/*/FLOW` | Yes, also on DEMAND |
| `p.f/*/FLOW -> p.f/[any]/FLOW` | No |
| `p/*/G -> p/./G` | Yes; TAINT tails can differ |
| `p/./G -> p/[any]/G` | Yes; retain the broader result |
| `p.f/*/G -> p.g/*/G` | No; field chains differ |
| `p.f/*/G -> p.f/*/H` | No; marks differ |
| `arg0/*/FLOW -> return/*/FLOW` | No; bases differ |

The helper is an alias-admission predicate. It is not a claim that the
APs are identical or that the whole method has no writes. For example:

```java
void set(Box b, Object t) { b.f = t; }
```

```text
b/*/FLOW -> b/*/FLOW       no effect for alias admission
t/*/FLOW -> b.f/*/FLOW     effect: apply aliases
```

The second arm must not be suppressed because the first is no-effect.

### Current versus new

Current `summaryParts` rejects identity classification for a DEMAND
conclusion or a different full group key. That key includes tail/header
properties which are not conditions in the selected predicate. It then
uses semantic subtraction against `startFact`.

Separately, the current guard admits a DEMAND result even when it was
classified as an identity:

```kotlin
// Current guard.
layer == Layer.DEMAND || !identityCandidate

// Selected run-1 alias guard.
!hasNoMemoryEffect
```

Both the classifier and the run-1 guard must change. Updating only the
classifier leaves the DEMAND bypass and does not suppress those aliases.
This is broader than the earlier C6/C7 controls: their remaining tail and
header restrictions do not implement the selected TAINT rule.

### Packed summaries and flag lifetime

A packed conclusion needs two parts. Partition its actual leaf payloads
by the predicate; do not assign one Boolean to a mixed result.

```text
Initial: p/*/G
Packed output:
    p/./G       -> no effect
    p/[any]/G   -> no effect
    p.f/*/G     -> effect
    p/*/H       -> effect
```

```kotlin
val (noEffect, effect) = partitionLeaves(conclusion) { leaf ->
    hasNoMemoryEffect(initial.fact, leaf.fact)
}
applyPart(noEffect, hasNoMemoryEffect = true)
applyPart(effect,   hasNoMemoryEffect = false)
```

Use a structural path/mark partition. Do not use `without(startFact)` as
the classifier: semantic subtraction can erase or combine distinctions
which this predicate deliberately treats differently. Preserve every
part's actual tail, layer, fields, E and mx. A one-walk packed partition
can avoid scalar result allocation in the implementation.

Classify the callee summary arm before caller projection. For example,
`arg0 -> return` is still an effect even if caller binding happens to map
both positions to one local. Carry the classification through return
cleaners, bind-back, cuts and suspended/resumed run-1 call stages. A layer
change alone must not turn no-effect into effect. Source-generated values
retain their own effect classification.

Skip only the guarded ALIASES stage for a no-effect part. Keep ordinary
summary application, bind-back, caller successors and all demand guidance.
Direct static-operation publication is a separate route; this predicate
is not a blanket static-writer filter.

For this run-1 iteration, later FULL/native-record handling and reversed
call-stage guards remain unchanged. Extending the new classifier to those
paths must be an explicit later scope decision.

## 5. Deferred: share identical static FLOW evaluation globally

The previous proposal shared equal static FLOW relations across method
owners. First measure operation-only write registration. It removes the
wrapper-copy multiplier at the source, without a new global evaluator.

Revisit global sharing only if equal relations from distinct remaining
local producers still account for substantial writer/group work. That
would be a separate change. Its key would need the complete static
premise, output Pattern, layer, AP mode and field limit, not just two field
names. It would also need coverage and arrival-order proofs against the
engine after section 3. No global relation table is selected now.

## 6. Semantic scope, proofs and measurement

The first fix changes who may register a static writer: only a reached
local transfer can do so. Caller summaries remain published and available
as run-2 guidance. Computed ZERO and all-static conditional group supplies
remain enabled. The required writer-lifetime ownership change is explicit
in section 3; it is not permission for a new support or grounding mechanism.

The selected alias change modifies current run-1 alias admission. It
intentionally treats same-mark TAINT tail changes as no-effect and removes
the run-1 DEMAND bypass. It must not silently change AP transfer, field
writes, cleaner behavior, summaries, mark values or demand layers.

Global relation sharing is deferred. If it is selected later, prove it
against the engine after the selected fixes, rather than against redundant
caller-writer registrations that section 3 has already removed.

Before implementation, follow [proof-guide.md](../../spec/proof-guide.md):

- Model the scalar alias criterion and lossless packed partition.
- Model local write admission separately from method-summary retention and
  computed group supply. Prove both bootstrap traces and writer coverage.
- Verify that only effect parts reach aliases; preserve no-effect parts'
  ordinary results and demand edges, including resumed call stages.
- Prove local writer validity across summary merging. A later sharing
  proposal also needs group/relation arrival-order and coverage proofs.
- Check full marks, runtime groups, tail changes, mixed output marks/paths,
  both layers, real mutator arms and direct static-write routes.

No completed proofs or new performance results are claimed here. Record
separate measurements after the write-registration fix and alias correction
on the same four projects. Report latest reached/completed/accepted iteration.
Count alias applications, distinct relation keys, evaluator/group jobs,
pending interests and replies, in addition to run-1 time and heap.
The target remains completion of run-1 engine work within 60 seconds.

The other prior recommendations are not selected. No AP memo, lazy alias
construction, cleaner fast path, read-seed replay, IR, reference-manager,
weak-cache, grounding or field-policy rewrite is included.
