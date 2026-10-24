# Thingsboard run-1: rule selection and work admission

Date: 23 October 2026. Status: **investigation and revised proposal; no runtime changes**.
Current documentation revision: `51e0272a675037589cdf789202ef875e713448bf`.
Measured runtime: `fb84969c0b91ccae0500389b5e25e045557b0eb3`.
Thingsboard reaches **run-1 OOM**. Latest COMPLETE and accepted forward run: **none**.

This proposal replaces the producer-certificate approach in the
[previous proposal](bidirectional-full-work-batching-next-iteration-20261023.md).
Use the [closed measurements](../reports/bidirectional-full-work-batching-performance-20261023.md).
No new project measurement is claimed.

## 1. Correct selection at the provider boundary

The provider is the right place to remove irrelevant Semgrep metadata.
A second mark/request eligibility registry is not required for this fix.
However, execution-item selection and return-cleanup metadata have different
purposes. Do not use the execution filter blindly for both.

| Query | Current result | Proposed result |
| --- | --- | --- |
| Normal execution | Selected item IDs S | Keep S unchanged |
| allRelevant=true | All items from all loaded graphs | All metadata items from active top-level graphs |
| Before prescan selection | All items | All items |
| Null serialized ID | Keep | Keep |

The implementation calls this flag `allRelevant`, rather than `allRules`.
The flag also reaches the base JVM resolver. That resolver deliberately
relaxes some negative method-name, class-name and arity conditions for
return cleanup. Preserve that behavior. Restrict the Semgrep graph scope
without changing condition resolution.

### Why the execution filter alone is insufficient

Consider one active rule graph. All marks belong to that graph's owner:

```text
A: source() produces T
B: foo(...), if CM(Argument0,T) AND NOT(NumberOfArgs = 1), produces T
   B's relevantTaintMarks = {T}; this is a state self-loop
F: sink(T); predecessor alternatives are A or B
```

```java
int x = source();
int y = foo(x);       // one argument; its body returns x
sink(y);
```

Assume primitive tracking for this owner and an EXACT T fact. The normal
condition of B is false, so prescan need not retain B as an executable
source item. A and F still make the graph active. The existing broad
resolver includes B for cleanup and removes T at the returned position.
Filtering that metadata with S removes B and lets T reach F. This changes
an active rule's behavior. It is not merely removing an unanswered request.
T has a selected producer A, so an absence-of-producer argument cannot
justify removing this reset.

Keep B's metadata because its graph is active. The example is a provider
contract witness, not a newly observed Thingsboard finding.

### Implementation direction

Compute both scopes at the same selection boundary:

```kotlin
// Pseudocode: retain current reduction and condition semantics.
val reductions = rules.map { graph -> graph to reduce(graph.root, prescanIds) }
executionIds = reductions.flatMapTo(hashSetOf()) { it.second.retainedRuleIds }
val knownIds = rules.flatMap { it.taintRules }.flatMap { it.rules }
    .mapNotNull { it.ruleItemId() }.toSet()
metadataIds = reductions.asSequence()
    .filter { (_, reduction) -> reduction.applicable }
    .flatMap { (graph, _) -> graph.taintRules.asSequence() }
    .flatMap { group -> group.rules.asSequence() }
    .mapNotNull { it.ruleItemId() }
    .toSet()

fun <T : ResolvedRule> Iterable<T>.select(allRelevant: Boolean): Iterable<T> {
    val ids = (if (allRelevant) metadataIds else executionIds) ?: return this
    return filter { rule ->
        val id = rule.resolvedRuleId()
        id == null || id in ids || (allRelevant && id !in knownIds)
    }
}
```

Keep the existing order of returned variants. Preserve null versus empty
selection. Recompute both scopes together on reselection; do not retain a
selected-list cache across selector generations. The base resolver must
still receive allRelevant=true for metadata queries.

Keep unknown/non-Semgrep IDs on the old broad metadata route. The known-ID
set describes serialized rule items, not runtime mark members.
The current JVM and Go cleaner adapters do not call this selector. Apply
the metadata selector to their true queries too; leave their actual
false-mode cleaner and pass-through execution unchanged. The common
provider change also reaches legacy JVM/Go and later runs. Test those
adapters; do not label this a run-1-only core change.

Keep all metadata within an active graph, including unselected branches.
Verify common ownership when several loaded graph entries share an owner.
If IDs or ownership are ambiguous, retain the corresponding metadata.
This proposal does not alter the dependency reduction algorithm.

### Measured opportunity and semantic boundary

An offline join of existing reference evidence identifies 19 active outer
rule IDs among 76 loaded entries, with 16 active mark-owner groups among 55.
Its exact static-origin pending-image membership classification is:

| Classification | Memberships |
| --- | ---: |
| Active owner; retain | 2,241,410 |
| Inactive owner; candidate for removal | 2,524,185 |
| Unknown; retain | 19 |
| Total | 4,765,614 |

The inactive-owner share is **52.97%**, a conditional **2.126x** reduction
in that recorded membership lane. It is not an engine speedup, byte saving,
ordinary-request count or a measurement on the candidate heap. A request
support audit must establish which rows arise only from removed metadata.

Do not reuse the previous 79.17% estimate for this fix. That estimate also
classified unselected items inside active graphs, whose cleanup can matter.
The raw join uses owner IDs. Its count of active roots is inferred from
retained item IDs; implementation must use the actual reduction result.

The minimal post-resolution filter does not save base specialization or
its cached bound rules; section 2 addresses that separate cost.

This explicitly changes the all-relevant rule view. Requests, cleaners
and FLOW exclusions for inactive graphs can disappear. Exact equality of
all internal FLOW headers to the current broad-metadata analyzer is therefore
not the intended claim. Prove preservation of selected-rule observations
and their later witnesses, with inactive metadata erased. Active graph
conditions, exclusions and cleanup behavior must remain unchanged.
No new mark legality, field transfer, source/exit or static-pool rule is selected.

The [offline receipt](../../../run1-full-work-batching-20261023/validation/semgrep-selection-and-algorithmic-20261023/metadata-active-root-offline-join.json)
pins the inputs and limits. This is a proposed provider correction, not an
implemented change or a completed end-to-end equivalence proof.

## 2. Avoid binding rules which selection will discard

A provider-level filter after resolution removes cleanup/request work, but
it does not remove specialization or its cache entries. The current route is:

```text
TaintConfiguration.resolveItems(method)
  -> find matching serialized rules
  -> anyArgSpecializationContexts(condition, actions)
  -> resolve/simplify each bound condition and action list
  -> cache the resolved rules for the method
JIRSemgrepRuleProvider
  -> select the resulting rules
```

JIRTaintRulesProvider.selectRules is a no-op. The base cache therefore
keeps rules which the frontend later discards. The new Semgrep metadata
filter alone would retain this cost.

The current reference heap has a **1,082.107 MB** ordinary sink-role cache
union and **680.181 MB** condition union. These overlap. Its census contains
3,737,698 exit-sink, 483,364 call-sink and 57,796 entry-sink objects. These
are resolved objects, not findings. The fraction with discarded item IDs
has not been measured; none of these totals is a removable-byte estimate.

### Example: avoid the complete binding product

```java
consume(a0, a1, /* ... */ a19);
```

Suppose the matching raw catalog has an active rule A and a known inactive
rule B. B has two independent AnyArgument variables in its condition.
The example uses a normal execution query after selection:

```text
Current:
  specialize B over 20 x 20 argument choices;
  resolve/simplify its 400 binding contexts;
  cache surviving bound rules;
  reject every B result at the Semgrep item filter.

Proposed:
  B is outside the selected execution scope;
  reject raw B before argument specialization;
  construct zero B binding contexts, conditions or actions.
```

This is an illustrative binding count, not a measured Thingsboard call.
It removes an entire product for a discarded item. It does not make the
400 operations faster. Active items still use the original specialization.

### Selection must precede specialization and cache admission

```kotlin
// Proposed seam, before resolver.resolveItem; not current code.
val candidates = originalRawCandidates(method)
return candidates.asSequence()
    .filter { rawPolicy.keeps(role, allRelevant, it.serializedId) }
    .flatMap { resolver.resolveItem(it).asSequence() }
    .toList()
```

Use the existing execution set S for the source/sink roles already filtered
in false mode. Use the complete active-graph metadata set M for true
source/cleaner queries. Keep unknown/null IDs and actual pass/cleaner
execution according to section 1. Do not turn every provider role into a
new filtered role. Keep all mark declarations and raw rule inputs.

Preserve bound variant order, complete rule values and binding identities.
A raw ID is copied into each resolved variant, so selection can commute
with specialization. Prove that property for every supported role and
show that omitted binding has no active semantic side effect. Keep normal
versus relevant condition resolution separate.

There is one concrete ID hazard: the resolver can assign sink report IDs
with a per-resolver generated-id counter when explicit ID/CWE is absent.
Skipping an earlier raw sink can renumber a later retained sink. Initially,
use the early-filter path only when report-ID stability is proved, such as
an explicit-ID/CWE-only candidate set. Otherwise retain the original binding
order and post-filter for that query. Do not silently change report IDs or
binding identities. Prove the explicit-ID contract for generated Semgrep
sinks before assuming that the fixed Thingsboard case qualifies.

### The prescan cache lifetime is part of this design

Prescan occurs before S and M are known and can populate the same strong
base caches. Creating a filtered wrapper after prescan does not free them.
The original provider and a new view can share TaintConfiguration; adding
another cache can make retention worse.

At the already joined prescan-to-analysis boundary, install a fixed
selection generation and release obsolete method-specialization cache
entries before the new core creates plans and saved suffix addresses.
Keep raw rules, declarations and resolved call graph. Specify which owner
holds each cache and prove no live prescan query or analysis continuation
can read a half-reset generation. Do not change SoftReferenceManager or
introduce a new global reference policy.

This is a complete proposal only after that phase/cache plan is proved.
If the actual frontend has no such safe boundary, design one explicitly;
do not clear shared caches during active rule queries. No cache reset or
runtime filtering is implemented by this investigation.

For validation, count rejected raw items, avoided binding contexts, selected
resolved variants and retained cache entries separately. Compare complete
selected-rule observations. Classify the current resolved caches by item
ID before assigning a numerical saving. This is the strongest direct
algorithmic lead for removing expensive work known to be discarded.

## 3. What Unchanged already saves

The current fast path shares the fact and avoids another MethodEdgeStore
insertion. This is a substantial existing optimization. It does not jump
over the remaining statements:

```text
emitUnchanged(I,n,F)
  -> addUnchanged(EdgeDelta(I, successor(n), F))
  -> process(I, successor(n), F)
  -> obtain statement form or call plan
  -> decide unchanged again
```

For calls, withCall precedes the touched-base check. A cache hit avoids
construction, but not the lease, classification and successor work.
A miss can build a plan even for an untouched fact.

DeltaWorklist.seen includes the original premise. It lasts through one
unchanged burst and clears when that burst drains. It prevents cycles and
repeats within that scope. It does not remove the first walk, combine
origins, or cache transfer results across later bursts.

Therefore an N-statement dead tail still causes N fact visits. However,
it does **not** cause N stored fact copies. Do not infer a large retained-heap
saving from the path length. The fraction and cost of such visits in
Thingsboard remain unmeasured. Unchanged is not evidence of an infinite loop.

## 4. Stop dead-local propagation

The pinned base-only revision is
`42ba593651d9ea8a68bfb0911691377357714b9d`.
Its MethodAnalyzer has isReachable checks in both scalar processEdge and
grouped processFactToFactGroup. JIRAnalysisManager delegates this check
to JIRLocalVariableReachability.

The new core has no corresponding gate in RunMethodAnalyzer.process,
emitCovered or emitUnchanged. JIRMethodEntry creates local reachability
only inside optional alias analysis. JIRInterpreter.isSummaryBase finally
rejects LocalVar at summary publication, after the propagation work.

```java
void handle() {
    Object unused = source();
    unrelated1();
    unrelated2();
    // ... 100 operations with no semantic use of unused
    return;
}
```

```text
After source: I -> LocalVar(unused)/*/T, DEMAND

Current: retain first admission, then walk the unchanged tail; no local summary.
Proposed: keep source effects; drop this current local before its first dead
          successor admission. No subsequent operation visits for this fact.
```

This can remove an entire operation chain for every concrete mark and
origin. It has a larger potential work reduction than making each visit
faster. Its actual Thingsboard impact is not established. It may reduce
admission/temporary allocation as well as CPU, but Unchanged already avoids
most intermediate storage. It is not yet a proved OOM fix.

### Conservative first design

Use a method-owned analysis overlay. Keep the IR and existing alias
analysis unchanged. Initially, compute future semantic mentions with no
local-definition kills:

```text
Need[n] = SemanticLocalUses[n] union UNION(Need[s] for s in successors(n))
```

Seed uses at all nodes and compute a finite backward fixed point. This
also covers uses in loops with no exit. Omitting definition kills retains
extra facts across local redefinitions; it cannot remove a needed old value.
A later design can add proved normal/exception-edge-specific kills.

Semantic uses must include rule predicates, aliases, call operands and
results, returns/throws, cleaners and raw action positions, ND inputs and
boundaries. An unsupported use descriptor keeps the local live. Nonlocal
bases and ZERO/Reach retain their current path. Do not change field writes.

Prune at normal CFG successor admission after producer effects have run:

```kotlin
if (run1 && facts.base is LocalVar &&
    semanticUses.provesNoFutureUse(successor, facts.base)) return
// Otherwise use the original store/queue path.
```

Keep exact saved operation/cleaner/call-stage replies. Do not apply a
physical-local test to callee Argument/Return positions inside a call plan.
After a reply completes its original operation, normal successor admission
can use the same gate. Retain pending images and existing subscriptions
at earlier live uses.

StatementSummary.touched alone is insufficient: FormApplier.statement
still evaluates sources and conjunctions on its untouched branch. A
further sparse-CFG shortcut must prove exact identity and no observation
for every skipped operation. That is a separate extension.

Required proofs: complete semantic-use coverage; no observable descendant
of a rejected local; both directions of finite summary/observation transport;
late-reply and ND preservation; equal NORMAL/DEMAND and marked run-2 guidance.
Tests must include aliases, non-returning SCCs, exception handlers, local
redefinition, field writes, selected cleaners, exact all-ZERO cubes and
controller exit rules. No new GroundingSupport or field approximation is needed.

## 5. Share transfer execution across equal current facts

The old base-only engine also groups pending work by statement and exact
current fact, with a set of initial origins. It evaluates an eligible
origin-independent transfer once. Affected calls and exits fall back per
origin. The current worklist and RuleWorklist include the premise in their
keys and process those origins separately.

```java
void merge(Object p0, Object p1, /* ... */ Object p7) {
    Object x;
    switch (choice()) {
        case 0: x = p0; break;
        case 1: x = p1; break;
        // ... remaining alternatives
        default: x = p7;
    }
    Object y = x;
    sink(y);                         // x is live at its copy
}
```

Assume eight distinct initial origins reach the copy with the identical
complete current fact F. This example concerns an analyzer relation, not
a claim that all branches execute at runtime:

```text
Current:   (I0,n,F), ..., (I7,n,F) -> eight transfer evaluations
Proposed:  (n,F,{I0,...,I7})       -> one pure transfer evaluation
                                      -> preserve all eight labelled results
```

Dead-local pruning does not remove this copy: x is live. Unchanged's seen
set does not combine its eight premises. Preserve every origin; do not
choose a representative or form a Cartesian product of marks and paths.
F_T and F_U remain different facts, so this alone does not solve one
propagation per concrete mark.

A first specification should restrict sharing to certified pure transfers.
Keep source/sink/ND registration, requests, writer binding and publication
under each original label. Late origins need an exact transfer cache or
labelled output replay; a queue set alone only merges co-arriving inputs.
The cache must have the existing method/run lifetime, not a new global history.

Thingsboard's 9.67 million TaintTree and 7.33 million TaintGroup instances
show scale, not same-current-fact duplication. The relevant multiplicity
per method/node/fact is **unknown**. Measure it before selecting a broad
origin-storage redesign or promising a factor of two.

## 6. Investigation decision and next measurements

Use provider-level metadata selection in place of a producer certificate.
Prioritize avoiding discarded raw-rule binding, with an explicit prescan
cache lifetime. This directly removes work which the selector rejects.
The missing liveness gate and origin sharing are concrete differences
from base-only, but their Thingsboard multiplicities remain unknown.
Unchanged already removes most intermediate storage on transparent paths.
None of these leads is established as the sole cause of Thingsboard OOM.

Before implementation, model the selected feature constructively and write
its adapter tests. Then isolate its work reduction under the same FULL,
statics, aliases, heap and timeout settings. Use direct runProjectAnalyzer.
Do not reduce the configured timeout to hide an incomplete run-1.

Measure these domains separately:

- Metadata: selected execution items, metadata items, requested memberships
  and their active/inactive owners. Confirm that active cleanup still runs.
- Binding: candidate raw items, rejected items, AnyArgument contexts,
  selected variants and generation-owned caches; retain the prescan cost
  as a separate phase.
- Liveness: local fact visits, proved-dead successor admissions, their
  unchanged-hop/call-plan work, and remaining effectful inputs. Do not count
  avoided hops as avoided stored copies.
- Origin sharing: exact node/F multiplicity, eligible transfer evaluations,
  origin-only replay and fallback calls. Keep concrete marks in the key.
- Outcomes: run-1 engine time/status, process CPU, GC and retained owners;
  latest reached, COMPLETE and accepted forward iterations; active rule
  observations and later marked demand witnesses.

If dead visits or exact-fact multiplicity are small, reject the corresponding
performance hypothesis. A source difference or an eight-to-one example
alone is not a measured Thingsboard speedup.

Source reviews:
[Semgrep selection](../../../run1-full-work-batching-20261023/validation/semgrep-selection-and-algorithmic-20261023/semgrep-selection-review.md),
[sparse/local propagation](../../../run1-full-work-batching-20261023/validation/semgrep-selection-and-algorithmic-20261023/sparse-local-propagation-source-review.md),
[equal-current-fact batching](../../../run1-full-work-batching-20261023/validation/algorithmic-execution-opportunities-20261023/equal-current-origin-batching.md).
