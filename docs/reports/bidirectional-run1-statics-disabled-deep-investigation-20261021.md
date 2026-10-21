# Run-1 performance with static fields disabled

Date: 21 October 2026. Status: investigation complete. Exact join census,
bounded marker/conclusion decoding and full-run JFR analysis are closed.

Scope: `opentaint.bidi.disableRun1Statics=true`. Runtime source is
`0f7feafd88d5ca84dd1ae12ef4b8a4920c3d1b48`; current documentation commit
`d509db87b` changes no runtime file. The comparison source is
`origin/saloed/base-only-clean`, pinned at
`42ba593651d9ea8a68bfb0911691377357714b9d`.

The successful historical BaseOnly runs on all selected projects are accepted
as supplied by the user. Their reports were removed. This investigation does
not reconstruct their times. It checks which current mechanisms add work.
No analyzer implementation or semantic change is made.

The latest measured disabled thingsboard run reaches **run-1**, FORWARD,
field limit 1, and ends **OOM** after 664.798550985 engine seconds. No engine
run completes and no forward report is accepted. The failed duration includes
30.213 seconds for the live-heap hook. It is not a completion time.

The main finding is **growth in complete premise sets for one XSS group**.
Two method actors account for 93.86% of retained join tuples. Their large
inputs combine an argument with subsets of synthetic global rule-state
markers. The flag keeps these markers; it disables real static fields.
Thus, fewer mark labels and fewer static-field flows do not imply less
work in this core. The snapshot records 126.2 million processing steps.
Repeated tree projection and early form construction add substantial cost.
There is evidence of finite combinatorial growth, but no proof of a literal
nonterminating loop.

## The comparison is more than a mark-count comparison

The current flag removes real static-field effects. It keeps exact global
class markers, ordinary GROUP conditions, selected-cleaner behavior, requests,
complete input premises, caller joins and the new work queues. It does not
restore the old BaseOnly execution pipeline.

| Mechanism | BaseOnly source | Current disabled run-1 |
| --- | --- | --- |
| Marks in rule stages | Full generated labels remain different | Labels in one top-level rule become the same group |
| Selected-mark cleaner | Can remove the selected full mark | Requests the group; actual GROUP taint survives as DEMAND |
| Sequential fact transfer | Groups equal `(statement, current fact)` across single initial premises; caches supported transfer results | Processes separate complete-premise deltas |
| Unchanged repeat set | Lives until all local fact queues drain | Clears when an unchanged sub-burst drains, even if other stored deltas remain |
| Conjunctive caller summary | Finds required input supports, forms products, deduplicates completed sequents before enqueue | Retains every completed ordered member tuple and meets later conclusions with every tuple |
| Persisted call aliases | Queries aliases for actual summary memory effects | Builds aliases for all local bind-back targets before the input/guard check |
| Alias result across prescan | Analysis context can keep its computed alias result across phases | Releases prescan owners and creates new lazy alias owners for bidi analysis |
| Current AP representation | Default shallow mode is packed `BaseOnlyField`, with one structural slot and an implicit structural loop | Depth-1 trie, with explicit tails, layers and headers |

The old core also has conjunctive premises and Cartesian products. These
mechanisms were not introduced from nothing. Their retained state, work
sharing and mark semantics changed.

The branch's default old shallow mode is `BaseOnlyField`, not the separate
field-insensitive `BaseOnly` mode. The no-field example below does not depend
on this distinction. Current and old rulesets also differ in 34 files; the
source comparison does not claim a controlled timing ratio between them.

## Current retained state

The existing disabled heap contains 346,986,536 reachable objects and
11,460,838,896 shallow bytes. All real-field point, writer and call-wait
directories are empty. The separate exact global-marker route remains.

| Retained owner union | Bytes |
| --- | ---: |
| Ordinary predicates and conjunctions | 1934496744 |
| Method-edge owners | 1647320184 |
| Caller/publication/ordinary request owners | 1111817432 |
| Run summary owners | 546334072 |
| Static service and point owners | 58746112 |
| Work queue owners | 36381672 |

These unions overlap and must not be added. There are only 4,501 live
`EdgeDelta` objects. The large retained populations are standing relations
and join state, not a large pending-delta queue at this snapshot.

The log gives the same queue pattern. In-flight scheduler tasks fall from
68,230 at engine second 10 to 51 near failure. The scheduler still advances
from 4,881,208 events at second 611.517 to 4,936,146 at second 632.823.
Meanwhile, heap use is near the guard threshold and repeated soft-reference
cleanup recovers less memory. These are scheduler counts, not fact counts.
They show continuing activity; they do not prove semantic progress or global
termination. The final 30-second heap hook must not be treated as ordinary
analysis throughput.

The whole heap has 320 `InitialAp` atoms, 474,536 `PremiseSet` objects,
81,775 E6 join/key objects and 8,451,997 `NdBinding` objects. The atoms are
shared templates across method owners. The premise interner also keeps
intermediate pairwise unions. None of these counts is the count of processed
facts or distinct completed effects.

The exact census now separates actual per-key tuple counts, equal folded
effects, distinct support unions, groups and caller/callee methods. It
accounts for all 81,775 scopes and all 8,451,997 slot bindings. No scope is
unsupported or capped.

## The measured growth is in XSS premise combinations

Every nonzero E6 member support, every completed effect's premise union and
every physical `PremiseSet` uses **one group: `java/security/xss.yaml:xss`**.
The 42-group catalog is not the main cardinality factor. The large factor
is the number of different sets of initial atoms in this one group.

| Caller method | Join scopes | Retained tuples | Distinct folded effects | Process steps | Accepted deltas |
| --- | ---: | ---: | ---: | ---: | ---: |
| `DefaultTbClusterService.broadcastToCalculatedFields` | 18 | 4216657 | 2157779 | 9846187 | 703733 |
| `TbRuleEngineProducerService.sendToRuleEngine` | 36 | 3431178 | 1792508 | 68818070 | 2859839 |
| `CleanUpService.cleanUpRelatedData` | 108 | 415296 | 321192 | 374195 | 151164 |
| All 73669 actors | 81775 | 8148066 | 4355172 | 126150750 | 8360177 |

The first two actors account for **93.86% of retained tuples** and 90.70%
of distinct folded effects. A process step enters statement/call processing;
an accepted delta is a different counter. Their ratio is not itself the
fraction of redundant steps. It shows that a small pending queue can drive
many operations over standing state.

At thingsboard's `broadcastToCalculatedFields`, the real code has this loop:

```java
for (String serviceId : tbReServices) {
    TopicPartitionInfo tpi = topicService.getCalculatedFieldNotificationsTopic(serviceId);
    toCfProducer.send(tpi, new TbProtoQueueMsg<>(msgId, toCfMsg), callbackWrapper);
    toRuleEngineNfs.incrementAndGet();
}
```

The call at source line 197 resolves to `InMemoryTbQueueProducer.send`.
Its largest observed two-slot join has **234259 choices in one slot and
one choice in the other**. It is not a product of two large local families.
One incoming family is already large before this join applies it.

One bounded sample of the resulting premise contains:

```text
Argument(1) / STAR / XSS
AND 18 exact ClassStatic / one-accessor / XSS initial atoms
```

The bounded decoder confirms that all 65 marker names are synthetic XSS
sink state, rather than program class names. For example, initial 189,
accessor 625, has this name:

```text
java/security/xss.yaml:xss#sink#1;sink_98;_<S>_;pos
```

These global rule-state markers survive the static FIELD diagnostic.
They are not 18 different taint groups or 18 ordinary static fields.
The catalog has 65 such exact markers, all in XSS. It has only 20 nonstatic
XSS initial atoms. In a large join, adding one marker to an existing set
can make a new complete premise even though the mark group stays XSS:

```text
slot A: family of original supports I
slot B: one original support {marker_j}
result support: I union {marker_j}

I already has marker_j: same union as another tuple can result.
I lacks marker_j: another complete support set can result.
```

There are 474536 physical multi-atom premise sets, with cardinalities 2–19.
Several individual join scopes have exactly 131072 (`2^17`) distinct folded
effects. These data show a large family of marker subsets. The bounded
examples do not prove that a given scope contains every possible subset.
Physical premise counts also include intermediate unions, not only emitted
final effects.

The decoder checks the eight largest tuple scopes. All eight have the same
stored conclusion:

```text
callee premise: Argument(2).* / XSS AND one synthetic state marker / XSS
conclusion:     Argument(2).field(48556).[any] / XSS / DEMAND
               empty field exclusion
```

`48556` is an opaque field-accessor ID in this diagnostic. The output's
geometry is covered by the broad group-valued argument initial. This
confirms that the top scopes do not produce different mark groups or
different result payloads. It does not prove that a unary summary with that
output is present after all method transfers. Dropping such ND summaries
would still require a proof about transfers, observations and run-2 guides.

The actor with 43794 one-tuple ND scopes is
`BaseRuleChainService.deleteRuleNodes`. Each key has a different complete
callee premise. Its scopes have arities 2–7. The separate explosion in key
count therefore also occurs before a large local Cartesian product.

The census folds 8148066 tuples to **4355172 exact effects**. It finds
3792894 equal-effect duplicates, or **46.55%** of tuples. Equality includes
the complete original premise union, effective DEMAND link and caller
layer, within the same actor, callee context, complete callee premise,
publication layer, call and stage. It does not merge caller contexts or
different premises. The census measures potential duplicate applications
and records; it does not establish a 46.55% heap or engine-time saving.

This resolves the main cardinality question: a small AP grammar and few
groups do not bound the number of complete conjunctive premises. Current
storage deduplicates equal sets correctly. It retains different subsets
and supersets, and propagates their results separately.

The failed run also records the following cumulative operations:

| Counter | Count |
| --- | ---: |
| Form-cache requests | 151904844 |
| Form-cache hits | 150592255 |
| Successful form builds | 1312588 |
| Incoming abstraction updates | 4895060 |
| New method-initial admissions | 277237 |
| Completed E6 combinations | 8148071 |
| Offered ordinary request members | 15959635 |
| Fresh temporary request members | 456970 |

The form counter starts after prescan. It counts each cache request, including
statement, call-plan, entry, exit and nested lookups. It is not a count of
distinct facts or actor steps. Its 463.5 seconds of summed build intervals
can overlap across workers and include nested builds. It is not an exclusive
engine-time share. The terminal counters can follow the live-heap snapshot.

The request stores do deduplicate: 5,826,237 offered memberships are already
authoritative, and 9,676,428 repeat in temporary storage. Thus, merging works
at storage admission. It still permits repeated offers and the work before
admission. These totals do not establish that request processing is the
largest CPU cost.

## Fewer marks can create more support combinations

The rule generator creates a synthetic class position for an automaton
prefix, and separate full marks for its states. It ANDs a global-state
check with the ordinary data condition. The GROUP provider changes marks;
it keeps the different positions. This source-supported transfer can result:

```text
Full:  CM(x,T) AND CM(S.C,U) => AssignMark(x,V)
Group: CM(x,G) AND CM(S.C,G) => AssignMark(x,G)
```

For equal target geometry, the output keeps the existing group payload but
gains the marker premise. The source compiler retains this action. The
storage key then distinguishes `I -> x/G` from `I union {C/G} -> x/G`.
Different marker positions remain separate even though all marks use G.
Selected-mark cleaners also no longer remove that group in run-1. These
are selected semantic changes, rather than a static-field switch failure.

The old pinned rules have separate servlet and Spring XSS joins. Current
rules combine their tags under the outer `xss` rule. That broadens the
possible owner family. It is an input difference; this investigation does
not assign it an exact performance share or reconstruct deleted timings.
The decoded marker names establish the current XSS sink-state family.
They do not identify every dynamic source action that built each set.

This is a constructed program and source rule, not an identified project
hotspot:

```java
Object step(Object a, Object b) {
    configuredStage(a, b);
    return a;
}

Object caller(Object p0, Object p1, Object p2) {
    Object x = choose(p0, p1, p2);
    Object y = choose(p0, p1, p2);
    while (unknown()) x = step(x, y);
    return x;
}
```

```text
Full source rule:
    CM(a,T) AND CM(b,U) => AssignMark(a,V)
    T, U and V are different full marks in the same rule group.

Run-1 projection:
    CM(a,G) AND CM(b,G) => AssignMark(a,G)
```

The condition positions differ, so simplification keeps both leaves. Let
`P0`, `P1` and `P2` be the caller's group-valued initial facts. The GROUP
output can satisfy the same condition on the next loop pass:

```text
{P0}    -> x/G, {P1} -> y/G  => {P0,P1}    -> x/G
{P0,P1} -> x/G, {P2} -> y/G  => {P0,P1,P2} -> x/G
```

The current stores admit each complete support set separately. Exact-set
deduplication works, but it does not reject a larger support that gives the
same conclusion. Every nonempty subset can be constructed by adding its
members in successive loop passes. In FULL mode, a V output does not satisfy
the T condition. The same feedback path is absent.

| Parameter choices | FULL initial atoms | GROUP initial atoms | FULL source outputs | GROUP x supports | GROUP completed tuples |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 3 | 6 | 3 | 9 | 7 | 21 |
| 8 | 16 | 8 | 64 | 255 | 2040 |
| 12 | 24 | 12 | 144 | 4095 | 49140 |

These are executable finite-model results. The GROUP support count is
`2^n - 1`. The tuple count in this two-slot example is `n * (2^n - 1)`.
This is finite growth, without static fields or a growing AP depth. It
does not prove that thingsboard follows this exact pattern.

The ordinary GROUP identity `{a/G} -> ret/G` also exists in this example.
It logically covers `{a/G,b/G} -> ret/G` for this conclusion and layer.
Suppressing the latter early would stop this particular amplifier. That
would require a new cross-premise dominance design and proof. Neither core
currently makes that subset test. Equal-effect tuple deduplication alone
does not remove different support unions.

In particular, removing an ND summary also removes its explicit per-member
reversed guides from `HandOff.toBackward`. A pure positive-Horn proof is
insufficient. The actual run-2 input authorization, source reversal,
publication, layers and source/sink observations must be included. No such
pruning is selected or implemented by this investigation.

## Shared transfers in the old core

```java
// Different incoming origins reach the same current fact x/G.
Object copy(Object x) {
    Object z = x;
    return z;
}
```

```text
At z = x:
    P0 -> x/G
    P1 -> x/G
    P2 -> x/G

Old supported F2F path:
    compute transfer(x/G) once
    attach its result to P0, P1 and P2

Current path:
    process(P0, x/G), compute transfer(x/G)
    process(P1, x/G), compute transfer(x/G)
    process(P2, x/G), compute transfer(x/G)
```

This preserves separate initial-to-result relations. Sharing computation
does not require merging initial marks or dropping a premise. The old
optimization covers ordinary non-return/non-throw sequential instructions,
including field operations. Unsupported operations and nontransparent calls
fall back to per-premise handling. It is not a universal ND batching rule.

Current TAINT conclusions already pack marks. That packing shares output
payloads within one complete premise; it does not share execution across
premises. A restoring design must keep requests attached to every original
input and keep condition joins and source/sink hooks. It must not introduce
a field-transfer or caller-correlation change.

The old unchanged-fact guard also lasts longer. Consider two queued changed
facts at A and B, with the same premise and current AP. Their untouched CFG
paths meet at Z:

```text
Stored queue: [A, B]
A -> untouched path -> Z
B -> untouched path -> Z

Old: retain the seen set through both paths; visit Z once.
New: drain A's unchanged burst, clear seen, then visit Z again from B.
```

This is a finite repeat pattern. Its frequency in thingsboard is not yet
measured. Restoring the old guard lifetime is a separate candidate from
sharing changed transfers.

An ownership difference does not explain the failure. Both cores keep
locally drained method actors and their semantic edge stores strongly during
the active run. Old soft references cover transfer and prepared-summary
memos. They do not evict the method's semantic facts when its local queue
drains. The current core releases internal edges after global completion,
which this failed run does not reach.

## Repeated runtime projection

For one successful ordinary changed-result path, with no cut needed:

```text
AP operation output: run1Policy.result -> runtimeFact                 #1
Engine collector: collect.result -> forceRun1Layer -> runtimeFact     #2
Operation output: emitOperationResult -> forceRun1Layer -> runtimeFact #3
Field-limit output: collect.result -> forceRun1Layer -> runtimeFact   #4
Propagation output: emitAfter -> forceRun1Layer -> runtimeFact         #5
```

This is a source-path count, not five calls for every analyzer event.
Untouched statements, source predicates, calls, rejected inputs and cuts
can follow other paths.

FLOW projection scans mark-exclusion atoms through synchronized MarkTable
operations even when the result is unchanged. TAINT projection also checks
runtime atoms. `MarkGate` and cleaners add their own header operations.
The closed normal native window has 66.05% normalized/projected exclusion
ancestry and 28.55% `TrySpin` leaf samples. Those shares overlap and are not
whole-engine CPU seconds. The complete JFR analysis provides a different,
broader denominator:

| Full run-1 observation | Value |
| --- | ---: |
| Java execution samples | 46367 |
| Exact normalized/projected Java leaves | 912 / 1.97% |
| `runtimeFact` Java ancestry | 14.24% |
| `runtimeFact` sampled allocation weight | 665.263 GB / 32.40% |
| All sampled allocation weight | 2053.521 GB |
| GC pause interval union | 53.509 s |
| MarkTable blocked-thread duration | 95.740 s |

JFR headers cover the whole logged run-1, with no header gap. Samples are
not CPU seconds. Sampled allocation weights estimate transient allocation;
they are not retained heap bytes. Blocked-thread durations can overlap.

The technical issue is broader than exclusion wrappers. `runtimeFact` walks
TAINT trees through `mapLeaves`, even when the marks are already GROUP
atoms. This walk creates traversal maps, stacks and node frames. Its sampled
descendants include 374.449 GB of `Object[]`, 45.779 GB of `TaintLeaves`,
37.578 GB of `IdentityHashMap`, 34.825 GB of node frames and 31.470 GB of
`TaintTree`. These numbers are parts of the 665.263 GB ancestry total and
must not be added to it. The heap retains only 438 `MarkExclusions` wrappers;
that small count does not measure transient projection cost.

## Early and late costs differ

| Engine interval | Java samples | Form get/build ancestry | ND-match ancestry | `runtimeFact` ancestry |
| --- | ---: | ---: | ---: | ---: |
| 0–60 s | 13697 | 78.16% | 0.00% | 1.48% |
| 60–120 s | 2083 | 67.74% | 0.00% | 5.23% |
| 480–540 s | 10008 | 0.43% | 20.27% | 23.36% |
| 540–600 s | 8472 | 0.87% | 29.57% | 21.99% |
| 600–660 s | 2393 | 6.31% | 32.34% | 19.97% |

These inclusive categories overlap. The first minute has a substantial
form path before E6 becomes prominent. This work counts toward the requested
60-second engine target. Reducing late join state alone does not demonstrate
that target. The early compact stack table identifies actual construction:
`JIRCallPlanBuilder.build` occurs in 67.68% of first-minute Java samples,
`JIRIntraProcAliasAnalysis.compute` in 46.91%,
`aliasesPersistedThroughCall` in 41.58%, and CFG construction in 20.22%.
These are overlapping ancestry shares, not separate elapsed-time budgets.
The source cache path invokes the builder on a miss. Thus, this early cost
is not explained only by the many hot cache requests later in the run.

Later, support combinations drive repeated matching,
application and tree work. Cartesian enumeration itself has only 0.13%
full-run Java ancestry; optimizing its inner loop alone misses the larger
standing-support and downstream-processing problem.

Near failure, memory pressure adds cost. The 600–660-second bucket has
23.040 seconds of GC pause intervals. Run-1 has 2058 collection events;
their summed elapsed duration, 491.748 seconds, can overlap and must not be
called exclusive engine time. The live-heap hook starts near engine second
634.5 and occupies the final 30.213 seconds. This last bucket includes the
hook; its pauses must not all be attributed to ordinary analyzer GC. The
earlier allocation and standing-state evidence is separate from that hook.

## The early alias path has concrete work differences

Current `RunMethodCallServices.process` obtains the full call plan before
it checks whether the input is ZERO or has an untouched base.
`JIRCallPlanBuilder.build` calls `aliases(back)` for every local bind-back
target. It computes the persisted aliases before it attaches the
`MemoryEffect` guard. The interpreter checks that guard later.

```text
Current:
    build all bind-back aliases
    obtain plan
    check ZERO / untouched input / memory-effect guard

Old:
    apply actual summary
    if summary has a memory effect:
        query persisted aliases for the affected local
```

The old core also queries aliases for actual locally produced source facts.
It does not disable aliases in BaseOnly mode. The difference is demand for
alias work, not a different DSU algorithm: the JVM alias analysis and DSU
implementations are unchanged between the two pins.

The old method actor keeps its analysis context across phase changes. A
computed alias result in that context can be reused. Current prescan owners
are released before the new driver starts; `JIRMethodEntry` creates fresh
lazy alias owners. The production path does not use the existing helpers to
borrow computed results. This permits recomputation after prescan. It does
not prove duplicate retained prescan alias state: those old owners were
released. Nor does it mean one full DSU computation per call plan; a new
alias owner caches its computed result.

This is a direct source difference that agrees with the early profile.
The exact fraction of methods with reusable prescan results, and the number
of plans whose aliases are never used, were not counted. A reuse design must
check method, graph, alias parameters and rule-dependent inputs. Reusing
incompatible results would be a semantic change.

## Priorities for the next design

The first target for retained memory is **synthetic-state premise growth**.
Current behavior stores different marker subsets even when the mark group
and result are equal. The new design should avoid redundant support growth
at its producer, rather than only pack the resulting tuples. This requires
an explicit spec and run-2 guidance proof. General subset pruning or deleting
same-group assignments is a major change and is not approved by this report.
The field/tail example above explains why mark equality alone is insufficient.

For the **60-second engine target**, avoid alias preparation before it is
needed, and reuse compatible computed analysis results where possible.
The old memory-effect guard supplies a concrete behavior to restore. This
changes when computation occurs; it must preserve every alias edge when the
guard admits it. It requires no IR storage rewrite or new reference manager.

Next, make immutable GROUP facts cheap to reuse. Current code can project an
already projected tree five times on one changed-result path. A design with
a checked canonical runtime boundary can keep the same payload and layer
without repeated whole-tree walks. This targets the measured transient
allocation and CPU path; it does not remove different premise sets.

Restore supported transfer sharing across separate original premises, and
consider the old unchanged-set lifetime. The examples above show the work
saved while retaining each premise. The project-wide benefit still needs
measurement; 126.2 million steps are not all redundant.

Exact E6 folded-effect deduplication is a smaller, bounded target. Before:
retain and replay 8148066 ordered tuples. After: retain one folded effect
per equal complete union/layer/scope and replay 4355172 effects in this
snapshot. This still keeps the large slot-member families and different
effects. A new hash/record representation can offset some byte savings.
Neither a 46.55% heap saving nor completion within 60 seconds follows.

No candidate is implemented. The evidence does not establish which single
change would make thingsboard complete run-1 within 60 seconds. The current
failure has both an early preparation cost and later combinatorial state.

## Evidence

The new external study is
`/drive-testcomp/opentaint-go-rules/run1-statics-disabled-deep-20261021`.

- `baseline-source-comparison.{md,json}` pins both source revisions and
  the F2F, ND, unchanged-work, cleaner and AP comparisons.
- `support-growth/work-domain-audit.md`, `source-bindings.json`,
  `support_closure_model.py` and `model-results.json` keep the finite
  counterexample, executable witnesses and the limits of subset pruning.
- `runtime-boundary-source.{md,json}` binds the inspected projection path.
- [Exact join census summary](/drive-testcomp/opentaint-go-rules/run1-statics-disabled-deep-20261021/F-ON-exact-nd-census-summary.md)
  links the complete per-scope table, exact counters and process closure.
- [Bounded marker/conclusion decoder](/drive-testcomp/opentaint-go-rules/run1-statics-disabled-deep-20261021/F-ON-atom-names-summary.md)
  identifies the synthetic markers, top conclusions and high-scope actor.
- [Full-run JFR findings](/drive-testcomp/opentaint-go-rules/run1-statics-disabled-deep-20261021/jfr-findings-F-ON.md)
  pins the fixed time buckets, units, allocation and GC observations.
- `generated-state-group-feedback-source.{md,json}` binds the rule-generation
  mechanism and old/current XSS rule comparison.
- `form-statistics-scope.{md,json}` and `run1-log-progress.json` keep counter
  meanings and the closed progress-log extraction.
- `jfr-early-form-build-note.{md,json}` binds the early builder/alias stack
  tables and explains why cache callbacks are not counted as builder work.
- `alias-call-preparation-comparison.{md,json}` binds old/current guards,
  cache lifetime, unchanged DSU sources and the prescan-owner release.
- The original closed capture authorities remain in
  `run1-static-requests-20261020/validation`. Its normal four-project cohort,
  same-F flag pair and failed run-1 diagnostic are separate evidence sets.

The first census plugin failed registration before its query ran. Its output
is preserved and contributes no measurements. A manifest-only registration
fix enabled the successful exact census. All final reader processes are
closed and all three serial locks are released. The original captures are
retained. The 38.38 GB expanded JFR JSON was removed after both analyses and
count/hash checks passed.

No complete runtime speedup, global termination theorem or implemented
optimization is claimed here. The latest measured disabled iteration is
**run-1, OOM**; no run is complete or accepted.
