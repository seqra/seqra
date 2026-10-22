# Run-1 cost: explanation and recommended changes

Date: 22 October 2026. Status: recommendation, not an implementation spec.
Runtime: `d7244c08cdaefbb157f42e4d89731d301b97e48f`.
The [closed investigation](../reports/bidirectional-run1-causal-performance-20261021.md)
contains the 16 configurations, 13 controls and source/heap/profile bindings.
No analyzer code changes are part of this document.

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

## 3. Recommended changes, in order

The ranking targets run-1. It does not replace separate later-run memory
proposals. Project speedups and the number of distinct shared keys have
not yet been measured for these proposed implementations.

### First: evaluate identical static FLOW relations once globally

**Current:** an equal static relation gets a writer and requested-group
state under each method owner. On group interest, each owner subscribes
to the same source static and composes the same global answer.

Assume a corresponding write has activated the read of C.b:

```java
void leaf() { C.a = C.b; }
void wrap1() { leaf(); }
void wrap2() { leaf(); }
// Many more wrappers.
```

Each method can retain `C.b/FLOW -> C.a/FLOW`. Each owner then acquires
`C.b/G` separately when `C.a/G` is requested. Its static writer callback
only composes the relation and supplies a global group result. It does
not install a marked edge in that method's demand store.

**New:** retain all method summaries/publications and demand guidance, but
share the pure global relation evaluator and its group acquisition state.

```text
Current evaluator key:
    (method owner, writer id)

Proposed evaluator key:
    (complete static premise, complete output Pattern,
     layer, AP mode, field limit)

For each required group:
    acquire source group once for this complete relation
    compose the relation once
    publish its global answer
```

**Why:** remove the method-owner multiplier from writer state, source
interests, composition and replies. This avoids work instead of only
packing its representation. It directly targets the statics/aliases
failure. It does not by itself fix the static-disabled OWASP workload.

Start with singleton static-field FLOW-to-static-field FLOW relations.
Keep owner-directed nonstatic RHS requests and fixed-group conditional
bindings unchanged. Do not merge different methods' Arg(0) requests.
Do not use only a field pair as the sharing key: preserve tail, field
exclusions and FLOW mark exclusions.

Do not select an arbitrary actor as the permanent evaluator. Its exact
summary selector can become stale while another producer still supports
the relation. Prove monotone coverage for an immutable relation, or retain
a valid producer/refresh contract. Preserve both arrival orders and the
existing req/reach/processed policy. Method demand publication must remain
independent of global evaluator admission.

Relevant code: `Run1PointResume.summary`, `WriterInterest`, `publishMatched`;
`Run1StaticService.registerWriter`; and `retainStaticCallEffect` in
`RunMethodCallServices`.

### Second: share pure AP work across origins

**Current:** `I1 -> F` and `I2 -> F` can perform the same AP transformation
twice. Their different originals must remain, but need not force duplicate
kernel computation. A compiled-edge cache does not cache the transformation.

```java
Object copy(boolean chooseX, Object x, Object y) {
    Object z = chooseX ? x : y;
    Object q = z;
    return q;
}
```

At `q = z`, suppose `Arg(x)/G -> z/G` and `Arg(y)/G -> z/G`
have equal complete current Facts. The copy kernel has the same input
twice. Before: two kernel evaluations. After: one evaluation and two
origin-specific emissions, retaining both `Arg(x)/G -> q/G` and
`Arg(y)/G -> q/G`.

**New:** evaluate equal complete kernel inputs once, then replay the result
and request events through each original's existing collector.

```text
events = pureAP(compiledEdge, completeFacts, mode, requiredSelectors)
for each original with that complete kernel input:
    replay events through collector(original, operationSite)
```

**Why:** avoid repeated trie walks, gating and result construction before
storage admission. Keep every original edge, source/sink effect and request.
Use a bounded actor-local scope. Do not retain all Facts and form payloads
for the full method epoch. The actual reuse rate needs an explicit counter.

The first scope can use plain nonstatic TAINT kernels. Extending to FLOW
must preserve ordered request/result events and original-dependent static
selectors. Calls, rule evaluation, witnesses, operation hooks, filtering
and owner-specific effects remain outside the memoized kernel.

### Third: construct alias projections only when the stage is used

**Current:** call-plan construction enumerates/lowers persisted aliases
before the runtime MemoryEffect guard can reject the stage. A rebuild can
repeat this preparation. Method-level AA results are already lazy; this
proposal is not a new AA engine.

```java
void pure(Box b) { }
void caller(Box b) { pure(b); }
```

For a NORMAL identity result that the existing guard rejects, the current
builder still prepares alias projection edges. The proposed builder does
not prepare that payload. For a DEMAND result admitted by the guard, it
prepares and applies the same payload as today.

**New:** keep a lightweight stage descriptor. Build its alias summary on
the first admitted use, with the existing form lifetime and cache policy.
Preserve stage addresses, static-operation metadata, backward use and
empty-stage behavior. Keep all effects for DEMAND inputs that still pass.

**Why:** avoid unused alias-list and micro-edge construction. In Stirling-PDF
with statics OFF and AA ON, builder/alias frames account for 59.88%/44.73%
of execution samples. These inclusive shares overlap; neither is a promised
saving. The fraction of preparation for rejected stages is unmeasured.

### Small follow-up: return immediately for a selected GROUP TAINT clean

**Current:** `TaintClean` traverses/rebuilds the trie, although `gone` returns
an empty removed set in GROUP mode for a selected-mark cleaner.

**New:** return the original TAINT value on that exact primitive branch.
Keep outer read/write/field-wrapper actions and their requests unchanged.
RemoveAllMarks retains its current behavior.

```text
Primitive operation: RemoveMark(r, T), where T belongs to G
Input:  I -> r/G, DEMAND
Before: walk/rebuild the TAINT trie; emit I -> r/G, DEMAND
After:  emit the same value directly
```

**Why:** avoid an identity walk. This is a local cost reduction, not the
solution to the whole cleaner pipeline. Primitive `TaintClean.run` occurs
in 3.35% of OWASP's both-OFF execution samples, including its callbacks.

## 4. Semantic scope and rejected shortcuts

The four recommendations intend to preserve the current closure, including
method demand guidance, every contributing origin, current field handling,
group rules and report/source/sink identities. They need constructive models
and preservation proofs under [proof-guide.md](../../spec/proof-guide.md)
before implementation. No completed proof or measured speedup is claimed.

Do not apply the diagnostic GROUP kill, alias publication deletion, old
representative-only origin emission or coarse NORMAL static pass. They can
change semantics. The longer full-Facts repeat guard is also not selected:
it reduces form accesses by 24.31% but increases GC cost and observed time.

Changing alias admission to infer real write effects, or moving refinement
from saved condition points to original read seeds, would be separate major
changes. They need explicit contracts and proofs. Do not introduce them as
implementation details of sharing. In particular, read-seed replay must
cover implicit condition/cleaner reads, late arrivals, exclusions, call
stages and every intermediate group demand needed by run-2.

No IR, reference-manager, weak-cache, grounding or field-policy rewrite is
proposed. First measure whether the selected changes reduce global writer
evaluations, pending subscriptions, queue events and actual AP computations.
Then check completed/accepted run-1 and the 60-second engine target on the
same four projects and configurations.
