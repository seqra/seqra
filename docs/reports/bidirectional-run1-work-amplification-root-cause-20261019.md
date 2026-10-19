# Run-1: separate useful analysis from implementation work

Date: 2026-10-19. Runtime source: `b88c85d5b710d28293d7672af92b085a3838fb19`.
Current later commits change documentation only. This report makes no runtime
change and selects no optimization. It corrects the causal scope of the
[previous proposal](../design/bidirectional-run1-normalized-groups-next-iteration-20261019.md).

The earlier proposal mixed necessary static joins, unmeasured duplicate
execution and measured implementation costs. It did not establish that the
proposed extra admission gates would explain the overall regression.
MethodEdgeStore and temporary RequestStorage already deduplicate. Their
existence must be included in any cost argument.

## Latest measured iterations

No project is rerun for this investigation. These are the same closed fixed
measurements used in the [performance report](bidirectional-run1-normalized-groups-performance-20261019.md).

| Project | Latest reached / completed engine / accepted forward report | Run-1 engine |
| --- | --- | ---: |
| OWASP | run-1 / none / none | 906.998 s, TIMEOUT |
| Conductor | run-1 / none / none | 986.046 s, TIMEOUT |
| ThingsBoard | run-1 / none / none | 239.084 s, memory-guard OOM |
| Stirling-PDF | run-2 / run-1 / run-1 | 925.594 s, COMPLETE; run-2 TIMEOUT |

## 1. Why a static premise is still matched

Run-1 removes ordinary CFG propagation of STATIC-valued facts. A reached
static read still emits a relation from its static original to a local
value. That local value propagates normally. A static write publishes an
immediate summary, then stops its STATIC-valued CFG output.

Consider:

```java
void set()   { C.f = sourceT(); }
void copy()  { Object x = C.f; D.h = x; }
void use()   { sinkT(D.h); }
// sourceT creates a full mark owned by g; sinkT checks that owner group in run-1.
```

For one exact source slice, all marked/static-dependent results are DEMAND:

```text
set:   ZERO -> S.C.f/$/g @D             actual pool supply A
copy:  P = S.C.f/*/FLOW                reached-read original, not an entry seed
       P -> x/*/FLOW @D                local propagation
       P -> S.D.h/*/FLOW @D            immediate write summary Y
use:   needs actual g on S.D.h         no check on x in copy requests g
```

The static service must apply the conditional copy relation Y to A:

```text
WriterReady(P,Y,A)
  -> ordinary satisfying(A,P)
  -> ordinary applySummary(binding,P,Y)
  -> actual pool supply S.D.h/$/g @D
```

Without this join, no concrete group is supplied for D.h in this example.
The sink's refinement cannot invent it. The matching is necessary equation
application, not restored static fact transport through every CFG/call edge.

The previous proposal concerned repeating this input selection separately
for several output leaves of P. It did not remove the necessary join. The
heap proves many output-specific registration rows, but it does not measure
their removable matching CPU cost or prove that this is the main slowdown.

Source: [static outputs stop at their operation](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt),
`emitOperationResult`, `emitAfter`; [writer registration and reply](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1PointResume.kt),
`summary`, `WriterReady`; `RunMethodAnalyzer.composeStaticSummary`.

## 2. Grouped rule caching and full-member scans are different paths

A per-method GROUP rule view is valid for queries whose existing provider
contract is method-only. It can rewrite full condition/action marks once,
then simplify equal GROUP checks. Retain the original FULL view and action
identities. A rule-view cache must use the same complete query context as
its provider. A later call-site/alias/type-dependent compilation step has
its own context.

The current implementation already does the semantic projection and positive
simplification, but at form construction:

```text
cached bound FULL rule
  -> full signed folds and existing binding/role policy
  -> full predicate
  -> GroupRuleConditions.select
  -> grouped predicate/action in a cached RUN1_GROUP form
```

`G OR G OR G -> G` is already supported by the GROUP DAG simplifier.
Selected action marks also become groups before builder edge admission.
FormsCache keeps separate GROUP/FULL slots and reuses the selected payload.
It does not repeat full condition compilation for every incoming fact.
There is no additional per-method GROUP provider-rule cache at present.
Such a cache could avoid repeated work across form misses/rebuilds. That
cost has not been attributed as the dominant engine cost.

The measured `eligibleMembers` path is **runtime fact type filtering**, not
`ContainsMark` condition simplification. JVM type filters use full-mark
primitive-tracking properties. Current group policy checks ask whether at
least one eligible full member passes the scalar policy:

```kotlin
// Per group atom at a type-filtered fact leaf.
val members = marks.eligibleMembers(group)
members.isEmpty() || members.any { policy.keeps(it, elementLevel) }

// Current membership lookup, under the catalog lock.
eligible.filter { owner(it) == group }.map { fullAtoms.getValue(it) }
```

For PDF, the starting catalog has 1,172 eligible full marks and 51 groups.
Each lookup scans all 1,172 eligible entries, even if the requested group
has one relevant member. It constructs lists and performs full-owner map
lookups. The same already-grouped fact can pay this cost at every type
filter. A cached `CM(p,g)` predicate does not intercept this path.

For example, an int/array-element filter can permit full T with primitive
tracking and reject U without it, while T/U share g. Rule-condition grouping
alone does not define this policy. Any replacement must retain its current
existential group meaning; it must not choose an arbitrary representative.

Sources: [group predicate selection](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/GroupRuleConditions.kt),
[JIR rule lowering](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt),
[form slots](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormsCache.kt),
[runtime mark policy](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Filter.kt),
[JVM primitive policy](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRTypeFilters.kt),
[full-member scan](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/RuleMarkCatalog.kt).

## 3. MethodEdgeStore deduplicates facts, not every prior operation

Ordinary initials are already deduplicated before their method execution.
`addInitial` returns when the initial is already present. Ordinary stored
successor deltas also pass MethodEdgeStore's merge gate.

The separate addressed static-point path currently does this:

```text
ReadReady / PointReady
  -> installPoint: append PointWork to resumeQueue
  -> execute statement / call stage / cleaner
  -> emit successor result
  -> MethodEdgeStore.add: reject an unchanged successor fact
```

Thus MethodEdgeStore can prevent another successor propagation while the
producing operation has already executed twice. Two distinct read overlaps,
EXACT and ANY_TAINT at the same static field, can normalize to one STAR FLOW
operation input at the same site. This is a concrete source-level witness.

It is not proof that such duplicates dominate a real project. The previous
proposal did not measure their frequency. An extra operation gate must not
be selected merely because a witness exists. It must also retain source/
rule observations and complete site/premise/geometry/layer/call state.

Source: [point installation and work loop](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt),
`installPoint`, `work`, `emitCovered`; [reply normalization](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1PointResume.kt),
`ReadReady` and `PointReady`.

## 4. Temporary RequestStorage already deduplicates

The current admission path is:

```text
producer offers a request
  -> offered.minus(committed R)          constructs intermediate selection
  -> temporary P.addAll                 rejects members already in P
  -> only new members change the store
```

For a duplicate in P, the temporary store remains unchanged. The proposal
would move the P membership check before intermediate construction. It is
an allocation/admission shortcut, not new request deduplication. Existing
scheduling also coalesces an already queued method actor.

OWASP has 30,643,891 offered memberships: 23,873 duplicates in R,
30,573,022 duplicates in P, and 46,996 fresh P admissions. These are repeated
producer/admission attempts, not 30 million retained requests or queued
propagations. Their CPU share was not measured. This ratio cannot justify
claiming that request admission explains the overall runtime.

Sources: [request storage and subtraction](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1Requests.kt),
`minus`, `RequestStorage.addAll`; `RunMethodAnalyzer.collectRequests`.

## 5. What the measurements actually identify

Fifty groups bound the available group selectors. They do not imply fifty
global concretization events: a group can be used at many method/base/point
contexts. More importantly, the implementation can perform many scalar
operations for one already admitted fact, without producing another fact.
Do not use the context argument to assume an unmeasured fact explosion.

The following are distinct findings from the closed run-1 profiles:

| Project | Measured implementation work | Scope of conclusion |
| --- | --- | --- |
| PDF | eligibleMembers in 59.33% of Java samples; FullMarkId.equals is the leaf in 38.63% | Repeated full-catalog member lookup is a major sampled engine cost. The final 30 s retain 52.12% inclusive eligibleMembers. |
| Conductor | eligibleMembers in 43.80% of Java samples; FullMarkId.equals leaf in 35.38% | The same runtime full-member work is substantial. |
| OWASP | MarkExclusions.normalized in 69.23% of Java samples; 40.12% leaf | Repeated exclusion normalization is a major sampled cost, independent of the duplicate-request CPU claim. |
| ThingsBoard | 327.578 blocked-thread s on MarkTable; direct-static frames in 10.54% of Java samples | Catalog contention and static work exist; these values do not attribute its whole runtime or memory guard to duplicate point execution. |

Inclusive shares overlap and are not wall time or predicted savings. Blocked
thread durations can exceed wall time. The current group adapter repeatedly
walks/project marks and exclusions even on canonical GROUP values:

```text
ordinary AP result
  -> runtimeFact walks/projects the selected result
  -> result/cover/actor boundaries can call runtimeFact again
  -> FLOW construction interns mx
  -> withCatalog(the same catalog) normalizes mx again
```

`runtimeFact(FULL)` is an identity path. GROUP mode instead maps leaves and
mark sets. `runtimeAtom(GROUP)` also resolves a group atom through catalog
lookup and group interning again. These are costs introduced by the group
implementation; they do not require more distinct facts. Runtime group
membership still scans the old full-mark population. This is a stronger
causal explanation for the PDF/Conductor/OWASP profiles than an unmeasured
extra fact-concretization or duplicate-resume count.

The memory guard has another direct observation. ThingsBoard's selected
bound-rule cache families retain 3,160,452,352 B, static service/point owners
retain 2,658,761,136 B, and MethodEdgeStore owners retain 149,233,832 B.
These are overlapping retained unions. The first population contains
35,070,715 ContainsMark objects and 8,699,328 bound exit sinks. They are not
concretized program facts. GROUP simplification after binding does not
remove the hard-cached FULL rule objects.

Only normal/allRelevant bound maps are populated; no-end maps are empty.
The strong main-thread Java-local provider path is recorded. This explains
large auxiliary retention in this snapshot. There is no main heap for a
byte-for-byte regression attribution, and not every cached rule is proven
unused. The static owner has 1,493,242 writer rows and 817,103 point
subscriptions, but their exact repeated-key/geometry distribution remains
unmeasured. Its 72 supplied regions have only 72 total region/group
memberships: each currently supplied region has one group. Thus this snapshot
does not show a many-groups-per-static-field explosion or a live static ND
product. It shows large consumer/registration state beside a small actual
pool. Do not claim that factoring these rows alone solves the OOM.

There are also source-confirmed unnecessary address constructions:
`Run1PointResume.reached` creates a read key/address before its Write branch
does nothing; `summary` creates a writer key/address before discovering that
some nonstatic concrete/mixed-premise outputs match neither service branch.
Those addresses have no reply consumer. The ordinary summary and reached
write region can still be required. Their exact live population and retained
bytes have not been isolated. These are actual collection-creation seams,
not evidence that every service writer or point continuation is redundant.

## Bounded causal check

Two external JUnit methods passed four cases. Each case uses the actual
MarkTable and ApOps.filter, one exact fact, one prebuilt type filter, 51
groups, one eligible full member in the selected group, 20 filter calls and
20 scalar policy calls. Other eligible full marks belong to other groups.
The scalar test policy keeps its input; this isolates membership lookup.
No static service, request propagation or rule compilation is involved.

| Runtime atom | Eligible full marks E | Groups | Filter / policy calls | Full-owner map lookups | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| FULL | 51 | 51 | 20 / 20 | 0 | Same input object each time |
| FULL | 1,172 | 51 | 20 / 20 | 0 | Same input object each time |
| GROUP | 51 | 51 | 20 / 20 | 1,020 | Same input object each time |
| GROUP | 1,172 | 51 | 20 / 20 | 23,440 | Same input object each time |

The test replaces only its private MarkTable owner's HashMap with a counting
HashMap after census, validates its contents/counter, then restores it in
finally. It changes no analyzer source. All 3,197 tracked core/rule source
files match B88 before and after. Each case reuses its one unchanged input
object. The two GROUP cases use the same logical group, AP and layer in
independent catalogs; the FULL cases use the corresponding exact full atom.
The selected group contains one full member, so the larger catalog adds no
relevant policy member. Counter totals are exactly `20 * E` in GROUP mode.

Thus increasing unrelated full marks increases implementation operations
without increasing groups, admitted facts or useful filter calls. This is
a causal operation-count result. It is not an end-to-end speedup estimate.
The existing project profiles establish that this path is hot in PDF and
Conductor; the unit fixture establishes why it can do so much work.

The first launch stopped at an instrumentation self-check before the filter
loops: map-equality validation had already incremented the counter. Its
failure is preserved. The corrected launch resets before the self-probe and
passes both methods. Test execution was serialized under the same three
locks; actual wrapper/worker closure is recorded. The receipt identifies
idle build daemons separately. No project configuration was rerun.

Evidence: `validation/catalog-work-repro/attempt-2/receipt.json`, SHA-256
`5bb0a6c4f4fe3193ab424cc3b57bbf218782575ca3bcfdd82859903300cd85aa`,
and its two-test XML with the four printed count records. The compact
`validation/catalog-work-repro/result.json` records all cases and closure.
The fixture, original failed attempt and prepared-source history remain
beside it.

## Investigation decision

Defer selection of extra static/request deduplication gates until their
project-level avoided operation counts are known. Keep them as hypotheses.
The fixed-fact catalog check above now confirms full-member probe growth.
Apply the same fixed-work method to repeated projection/normalization before
selecting its exact boundaries. For static/request gates, count attempted
complete operation inputs versus new stored inputs, by producer/site, before
claiming the extra gate addresses the main cost. Continue to distinguish the
type-policy path from cached rule conditions.

The evidence already locates major implementation overhead for three
projects. It does not prove a single cause for every project, a general
termination theorem, or a particular speedup after a fix. No confirmed new
unchanged-state loop was found; PDF's observed run-1 completed.

Evidence root:
`/drive-testcomp/opentaint-go-rules/grouped-statics-alias-writes-20261019`.
Use the closed profiles, `validation/heap-analysis-real/reader-closure-summary.json`,
complete actor/provider tables, `validation/report-working/group-provider-cache-source-audit.md`,
`reviews/stored-versus-executed-boundaries-contracts.md`, and
`validation/report-working/static-pipeline-purpose-and-redundant-state-audit.md`. Preserve all earlier failed runtime acceptance and
strict finding differences.
