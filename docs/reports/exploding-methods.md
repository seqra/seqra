# Which methods explode, and what each configuration does to them

> ## HEADLINE: the `[any]` machinery is INACTIVE on the workload that explodes
>
> Measured at gate scale, default settings, conductor full ruleset:
>
> ```
> starSeen=0   starHit=0   tifaWild=0   anyUnroll=0
> ```
>
> and abstract (`[any]`-carrying) facts are **0.12%-0.42%** of the facts in every exploding method.
>
> **The wildcard/unroll machinery never fires.** `TIFA initials emission limits` and
> `[any] compression on the caller side` are therefore aimed at a mechanism that is switched off in
> practice: it can only be made to fire by `tifaNoUnroll`, which loses findings (section 2).
>
> The explosion that DOES occur has a different cause entirely -- unbounded traversal of conductor's
> nested untyped payload maps (sections 9 and 11) -- and no amount of `[any]` compression can reach
> it. This is the single most consequential finding in this report and it redirects the effort.

Conductor, ONE rule (`scoped-harness/rulesets/single-rule`), `-Xmx8g`, IFDS 300 s, one JVM at a time.
Baseline on the clean branch `saloed/5-default-get` (`4c358d2e1`); the configuration sweep on
`saloed/44-fact-evolution-trace` (`275ba439f`), which is the only branch carrying the flags.


## 0. Conclusions, and where each is established

| # | Conclusion | Status | Section |
|---|---|---|---|
| 1 | `[any]` NEVER FIRES at default settings (`anyUnroll=0`, abstract facts 0.12-0.42%). The branch name, the Lean development and most of `CLAIMS.md` address an idle mechanism. | MEASURED | headline, 14 |
| 2 | Three `WorkflowExecutorOps` methods hold 58.7% of accepted edges, each in **one context** -- so a context bound targets a population that does not exist on conductor. | MEASURED | 1 |
| 3 | The cause is unbounded traversal of conductor's nested untyped payload maps. Facts are DEEP and NARROW: median 13, max **559**, few bases. | MEASURED | 7, 9, 11 |
| 4 | **No depth bound can work at any value.** 3 through 48 lose the SAME two findings; the mass sits where the findings live. | MEASURED | 8, 9 |
| 5 | Container nesting is the only axis with a real gap (30-60% above a bound sitting above the median) -- and it is unusable while widening is lossy. | MEASURED | 12 |
| 6 | Under a bound the work RELOCATES off the finding flows: `verifyAndUpload` x3.9 while `httpCall` loses 40% and `download` 16%; three sibling methods are byte-identical. Findings are STARVED. | MEASURED | 17, 18 |
| 7 | The fan-out requires **BOTH an erased generic AND abstraction**. Neither alone: unbounded `shouldUpload` admits 4 accessors; bounded it admits 22. | MEASURED | 23 |
| 8 | The two projects fail DIFFERENTLY. Conductor `ctx=1` throughout; ThingsBoard reaches `ctx=108`. They do not share a remedy. | MEASURED | 24 |
| 9 | Model "the sink reader mis-reads abstracted facts" | **REFUTED** | 17 |
| 10 | Model "abstraction erases which payload map, so one fact matches four branches" (x3.9 vs 4 branches was coincidence) | **REFUTED** | 19, 21, 22 |
| 11 | `splitDelta`'s abstract branch as the loss site (0.003% event change proves it barely fires) | **ELIMINATED** | 16 |
| 12 | "Depth 3 is the recall floor" -- measured from BELOW a median of 13 | **WITHDRAWN** | 3, 7 |
| 13 | "Depth 3 removes the OOM and keeps both findings" -- true on ONE rule, false at gate scale | **WITHDRAWN** | 4, 6 |

**Neither performance gate is met.** Conductor OOMs at 8 GB in every configuration measured;
ThingsBoard OOMs at 12 GB. Recall is complete in every sound arm (conductor 4/4, ThingsBoard 15/15
with no trace loss).

**Method rules earned here** -- each after a wrong conclusion of my own:
instrument the branch you change and prove it FIRES (16); state a falsifiable numeric prediction on a
named method BEFORE building (21, 23); never read a top-N view as data (18); measure the DIAGNOSIS on
a scoped workload but every REMEDY at gate scale (6).

## The instrument

Every counter in this project was an aggregate -- total facts, total nodes, events per second. None
named a method, so configurations could be RANKED but never EXPLAINED. `MethodFactCensus` hooks
`MethodAnalyzerEdges.add`, the single choke point through which every edge for a method passes, and
records per method:

* `offered` -- edges arriving;
* `accepted` -- edges that were new AND produced successor edges, which is what actually grows;
* `contexts` -- distinct `MethodEntryPoint`s for that method.

Keyed on the method WITHOUT its context, because the question is which code explodes.

## 1. The exploding methods (clean branch, baseline)

14,816 methods, 10,232,566 offered, 4,580,550 accepted.

| accepted | share | offered | accept rate | **contexts** | method |
|---|---|---|---|---|---|
| 1,360,484 | **29.7%** | 4,137,926 | 33% | **1** | `WorkflowExecutorOps#decide` |
| 777,941 | **17.0%** | 1,974,105 | 39% | **1** | `WorkflowExecutorOps#rerunWF` |
| 550,981 | **12.0%** | 718,008 | **77%** | **1** | `WorkflowExecutorOps#terminateWorkflow` |
| 138,869 | 3.0% | 172,441 | 81% | 1 | `WorkflowExecutorOps#completeWorkflow` |
| 133,540 | 2.9% | 263,646 | 51% | 1 | `WorkflowExecutorOps#endExecution` |

**Three methods carry 58.7% of all accepted edges and all are `WorkflowExecutorOps`.**

**`contexts = 1` for every one of them.** The explosion is NOT context multiplication -- it is a
single context per method producing over a million accepted edges. Any lever that bounds the CONTEXT
population is aimed at a population that does not exist in this workload.

Two distinct mechanisms are visible in the accept rate: `decide` is offered 4.14M edges and rejects
two thirds; `terminateWorkflow` accepts **77%** of what it sees. A configuration that helps one need
not help the other, and no aggregate counter can tell them apart.

## 2. What each configuration does

Facts per method, NORMALISED per million events (raw counts favour whichever arm ran longer):

| arm | events | `decide` | `rerunWF` | `terminateWorkflow` | findings | outcome |
|---|---|---|---|---|---|---|
| base | 1,492,522 | 367,306 | 324,476 | 184,899 | 2 | **OOM** at 140 s |
| `literalDelta` | 1,462,307 | 234,806 | **449,119** | 225,479 | 2 | **OOM** at 136 s |
| `tifaNoUnroll` | 6,802,234 | 37,722 | 48,946 | 16,071 | **0** | timeout |
| depth 1 | 5,184,154 | **1,860** | **231,546** | 3,303 | **0** | timeout |
| depth 2 | 3,672,568 | -- | -- | -- | **0** | rc=252 at 53 s |
| **depth 3** | **6,086,405** | **115,873** | **139,649** | **75,041** | **2** | timeout, **no OOM** |
| depth 4 | 5,454,206 | 115,751 | 243,633 | 60,800 | 2 | timeout, no OOM |

### The pattern: the mass MOVES to `rerunWF`

`rerunWF` is the SINK. It is #2 in the baseline and becomes **#1 in every constrained arm**:

| arm | `rerunWF` share of ALL accepted |
|---|---|
| base | 18.1% (2nd, behind `decide`) |
| `tifaNoUnroll` | 21.6% (1st) |
| `literalDelta` | 25.6% (1st) |
| depth 3 | 25.5% (1st) |
| depth 4 | 36.4% (1st) |
| **depth 1** | **68.6% (1st)** |

**Depth 1 is the extreme case and the clearest evidence.** `decide` collapses from 367,306 to
**1,860** facts per million events -- a **197x** reduction -- while `rerunWF` remains at 231,546 and
takes **68.6% of every accepted edge in the run**. The bound does not remove the facts. It relocates
them into a single method.

This is the per-method form of the relocation behaviour previously seen only in aggregate, and it now
has a name: **constraining fact depth moves the explosion from `decide` into `rerunWF`.**

### New exploding methods appear

Methods absent from the baseline top 6 that enter it under a configuration:

| arm | new entrant | accepted |
|---|---|---|
| `tifaNoUnroll` | `scheduleTask` | 77,452 |
| `tifaNoUnroll` | `cancelNonTerminalTasks` | 39,920 |
| depth 3 | `cancelNonTerminalTasks` | 170,502 |
| depth 4 | `scheduleTask` | **239,723** (3.6x the baseline) |
| depth 1 | `DeciderService#getTasksToBeScheduled`, `DeciderService#decide` | ~10k, ~7k |

Every new exploder is still inside `WorkflowExecutorOps`, except at depth 1, where the analysis is so
degraded that `DeciderService` surfaces. **No configuration moves the explosion out of the class.**

## 3. Recall

> **CORRECTED by sections 7 and 8.** The "depth >= 3 is the recall floor" reading below is WRONG. It
> was measured from BELOW the data: the sweep tested depth 1-4 against a distribution whose median is
> 13, so every value truncated below the median. Depth 3 is not a floor, it is the last value before
> total collapse -- and at gate scale depth 3 through 48 ALL lose the same two findings.

| arm | findings |
|---|---|
| base, `literalDelta`, depth 3, depth 4 | **2** (both) |
| `tifaNoUnroll`, depth 1, depth 2 | **0** |

**Depth >= 3 is the recall floor.** Depths 1 and 2 and `tifaNoUnroll` lose every finding on this
single-rule workload -- a harder failure than the partial losses seen on the full ruleset.

## 4. The actionable result

> **WITHDRAWN by section 6.** "Depth 3 removes the OOM and keeps both findings" holds on ONE rule and
> FAILS at gate scale: with the full ruleset it returns 2 of 4 findings and still OOMs. The result
> below is retained to show what a single-rule measurement can and cannot license.

**Depth 3 removes the OOM, keeps both findings, and processes 4.1x more events.**

| | base | depth 3 |
|---|---|---|
| outcome | **OOM at 140 s** (`lowmem_stop=1`) | timeout, **`lowmem_stop=0`** |
| events | 1,492,522 | **6,086,405** |
| findings | 2 | **2** |
| `decide` per Mev | 367,306 | **115,873 (3.2x lower)** |
| `rerunWF` per Mev | 324,476 | **139,649 (2.3x lower)** |
| `terminateWorkflow` per Mev | 184,899 | **75,041 (2.5x lower)** |

Depth 3 is the only arm that lowers the fact rate in ALL THREE top methods while keeping recall.
Depth 4 matches it on `decide` (115,751) but is **1.7x worse on `rerunWF`** (243,633) and lets
`scheduleTask` grow 3.6x -- the sink is already re-filling one step above the floor.

## 5. Branch note

The clean branch and the working branch are NOT equivalent at the baseline: `base_clean` reaches
2,701,636 events and times out, while `m_base` (same rule, same heap, all flags off) **OOMs at
1,492,522**. The accumulated working-branch changes make the unconfigured baseline worse. Only
RELATIVE comparisons within the working branch are quoted above; the clean branch is the reference
for what the analysis does unmodified.

## 6. AT GATE SCALE: the diagnosis transfers, the remedy does NOT

Sections 1-4 were measured on ONE rule at IFDS 300 s. Repeated at gate conditions -- the FULL
conductor ruleset, 8 GB, IFDS 500 s, same jar and branch for both arms:

| arm | oom | forward vulns | **SARIF set** | elapsed | events |
|---|---|---|---|---|---|
| baseline | **1** | 4 | **4** | 161 s | 1,396,372 |
| depth 3 | **1** | **2** | **2** | 147 s | **7,688,224** |

### Two corrections to section 4

**1. Depth 3 LOSES half the findings at full scale.** It kept both findings on the single-rule
workload (2 of 2) and returns **2 of 4** here. The single-rule result was not a licence to generalise:
one rule exercises one flow, and a depth bound that is harmless for that flow removes two others.
**Depth 3 is out on recall.**

**2. Depth 3 does NOT remove the OOM at full scale.** Both arms OOM. What it does is reach
**7,688,224 events before dying against the baseline's 1,396,372 -- 5.5x further** -- which is a real
throughput effect and no help at all if the findings are gone.

### What DOES transfer: the exploding set

| baseline (full ruleset) | share | depth 3 | share |
|---|---|---|---|
| `rerunWF` | 18.8% | `decide` | 10.8% |
| `decide` | 16.2% | `terminateWorkflow` | 7.4% |
| `terminateWorkflow` | 8.7% | `rerunWF` | 6.7% |
| `scheduleTask` | 3.1% | `scheduleTask` | 5.0% |
| `startWorkflow` | 2.9% | `cancelNonTerminalTasks` | 4.1% |

**The same three `WorkflowExecutorOps` methods dominate, and `contexts = 1` for every one of them.**
The diagnosis of section 1 is a property of the program, not of the scoped workload.

Two differences worth noting. Concentration FALLS with more rules -- the top three hold 43.7% here
against 58.7% single-rule -- because additional rules spread mass across more methods. And `rerunWF`
is already #1 at the full-ruleset baseline, whereas single-rule it was #2 behind `decide`; the sink
behaviour of section 2 starts earlier when there are more rules to feed it.

### The standing conclusion

**Measure the diagnosis on the scoped workload; measure every REMEDY at gate scale.** The scoped run
is a legitimate and much cheaper way to find WHICH methods explode, and it identified the right ones.
It is not a way to decide whether a bound is safe: recall on one rule says nothing about recall on
134.

## 7. WHY those methods explode: the fact shape, measured

`MethodFactCensus` extended to record the access-path SIZE of every accepted fact. Gate scale, full
ruleset, 8 GB.

| method | facts | **max size** | median | p90 | p99 | bases | abstract |
|---|---|---|---|---|---|---|---|
| `decide` | 417,235 | **247** | 13 | 33 | 40 | 25 | 0.15% |
| `rerunWF` | 408,425 | **181** | 12 | 23 | 40 | 31 | 0.16% |
| `terminateWorkflow` | 232,204 | **217** | 9 | 18 | 27 | 79 | 0.20% |
| `startWorkflow` | 67,118 | **559** | 13 | 31 | 40 | 10 | 0.06% |
| `scheduleTask` | 62,238 | 52 | 6 | 13 | 23 | 67 | 0.15% |

**The reason these methods explode is UNBOUNDED ACCESS-PATH GROWTH.** Paths reach 247, 217, 181 and
**559** accessors. No data structure in this program is 559 fields deep; that is a path growing
without a bound, and every distinct length is a distinct fact. The population is deep, NOT broad --
the bases are few (10-79) and `[any]` facts are **0.06%-0.20%**, so the wildcard machinery is idle on
the workload that actually explodes.

### This invalidates section 2's depth sweep

The sweep tested depth **1, 2, 3, 4** against a distribution whose **median is 13**. Every one of
those bounds truncated below the median, removing ~99% of the population including the flows the
findings need. "Depth 3 is the recall floor" was measured from BELOW the data and is withdrawn: it
was not a floor, it was the last value before total collapse.

## 8. The depth bound fails at EVERY value, and not because of tuning

Depth re-tested in the region the distribution occupies -- above the median, around and beyond p99:

| arm | oom | forward vulns | SARIF | events | findings lost |
|---|---|---|---|---|---|
| baseline | 1 | 4 | **4** | 1,346,585 | -- |
| depth 48 | 1 | 2 | 2 | 5,282,394 | `path-traversal`, `ssrf` |
| depth 32 | 1 | 2 | 2 | 5,313,135 | `path-traversal`, `ssrf` |
| depth 24 | 1 | 2 | 2 | 5,416,502 | `path-traversal`, `ssrf` |
| depth 16 | 1 | 2 | 2 | 5,386,109 | `path-traversal`, `ssrf` |

**Every depth from 3 to 48 loses THE SAME TWO findings.** Depth 48 sits above p99 (40) and cuts under
6% of facts, yet it loses exactly what depth 3 loses. A truncation effect would be gradual -- deeper
bounds would keep more. This is BINARY: switching the bound on at any value kills `path-traversal`
and `ssrf`.

That signature is not truncation, it is the **registration-not-matching** failure already proved in
this work: `coveredTopNode` is documented to "only ENLARGE the fact and cannot lose a finding", and
as a DENOTATION that is true -- but an abstracted fact does not ANSWER the demand the concrete one
answered, so the flow is never re-derived. The bound is sound on paper and lossy in the engine.

**Consequence: `factShapeDepth` is closed as a lever at every value, for a mechanistic reason rather
than by trial and error.** Any future depth bound must first close the matching gap; tuning the value
cannot help, and this is now measured across a 16x range of values.

The throughput effect is real and unusable: 5.3-5.4M events against the baseline's 1.35M (**4x**),
all still OOM, all missing half the findings.

## 9. ROOT CAUSE: recursive containment in the data model, and why the distribution has no gap to exploit

For every fact at size >= 40, the census records the path length against the number of DISTINCT
accessors in it. A tight cycle (`a.f.f.f...`) would show a ratio in the tens; genuinely distinct
structure would show ~1.

| method | deep facts (>=40) | avg length | avg distinct | **repeat ratio** | deep as % of method |
|---|---|---|---|---|---|
| `decide` | 17,388 | 63.0 | 17.5 | **3.60** | 5.02% |
| `endExecution` | 7,575 | -- | -- | **4.05** | **10.92%** |
| `startWorkflow` | 1,777 | -- | -- | **4.09** | 2.66% |
| `rerunWF` | 9,045 | 51.7 | 22.4 | 2.31 | 1.67% |
| `rerun` | 4,407 | 52.3 | 21.5 | 2.43 | 5.50% |
| `terminateWorkflow` | 90 | 46.3 | 20.3 | 2.28 | **0.03%** |

**Ratio 2.2-4.1: repetition, but not tight cycles.** A 50-accessor path is built from ~20 distinct
accessors, each appearing two to four times. The sample paths say what that is:

```
<this>.workflowStatusListener.blockingQueue.Element.workflowDefinition.outputParameters.MapValue.buffer/*
var(9).workflowStatusListener.blockingQueue.Element.workflowDefinition.outputParameters.MapValue.buffer/*
<static><static>(__spring_registry__)...ExecutionDAO.conductorProperties.stack.buffer.entries.buffer/*!*{d0=94,830,1158,1418,...}
```

**Recursive containment in conductor's own data model.** A queue holds workflow elements; a workflow
has output parameters; parameters hold maps; maps hold buffers -- and a buffer's elements are
workflows again. The analysis follows that containment without a bound, and every distinct traversal
length is a distinct fact. The repetition is the same handful of collection accessors
(`buffer`, `Element`, `MapValue`, `entries`) re-entered two to four times.

The third sample also shows a second axis worth noting: a huge EXCLUSION SET
(`{d0=94,830,1158,1418,...}`, dozens of entries) riding on a static path. Exclusions are not counted
in `size`, so this is a fact-multiplying axis the depth histogram cannot see at all.

### Why NO depth bound can work: the distribution has no gap

The deep tail is numerically small. Facts at size >= 40 are **0.03%-10.9%** of each method's
population, ~2.5% overall. The mass sits at the MEDIAN, around 13.

That closes the depth question arithmetically:

* a bound **above 13** leaves the bulk untouched -- measured, depth 16-48 gave only 4x the events and
  still OOMed;
* a bound **at or below 13** cuts the bulk -- and takes `path-traversal` and `ssrf` with it, at every
  value from 3 to 48 (section 8).

**There is no value that removes enough facts while keeping the findings, because the facts the
findings need and the facts that flood the analysis are the same facts, at the same depth.** This is
why every depth arm in this work behaved the same way, and it is a property of the fact distribution,
not of any bound's implementation.

### What this leaves

The two levers the measurement actually points at, neither of which is a depth bound:

1. **Bound the RECURSION, not the path.** The growth is re-entry into the same containment cycle
   (`buffer -> Element -> workflowDefinition -> ... -> buffer`). Recognising that a path has re-entered
   a shape it already visited discards nothing -- unlike truncation, which discards a real
   continuation. This is the only operator identified in this work that could reduce the bulk without
   touching the flows.
2. **The exclusion sets**, which multiply facts on an axis nothing in this project has yet measured.

## 10. The exclusion axis: NOT a fact multiplier (hypothesis refuted), but the sets are enormous

Section 9 closed the depth question arithmetically, and that reasoning holds only if DEPTH is what
multiplies facts. A sample path carried `{d0=94,830,1158,1418,...}` -- dozens of exclusion entries,
which `size` does not count -- so every fact-shape measurement in this project has been blind to them.
The test: **facts per distinct path**, with the exclusion set stripped.

| method | distinct paths | distinct facts | **facts/path** | avg exclusions | **max exclusions** |
|---|---|---|---|---|---|
| `decide` | 60,481 | 81,635 | **1.35** | 311.8 | **18,284** |
| `testWorkflow` | 30,450 | 35,668 | 1.17 | 219.6 | 7,897 |
| `terminateWorkflow` | 17,455 | 20,310 | 1.16 | 152.7 | 6,252 |
| `rerunWF` | 39,480 | 45,524 | 1.15 | 81.0 | 7,239 |
| `endExecution` | 12,699 | 14,632 | 1.15 | 161.3 | 6,111 |
| `startWorkflow` | 13,433 | 15,132 | 1.13 | **670.5** | 7,239 |
| `scheduleTask` | 7,016 | 7,204 | 1.03 | 3.4 | 471 |

**Facts per path is 1.03-1.35. The hypothesis is REFUTED.** Exclusions do not multiply the fact
population: a path appears with barely more than one exclusion variant on average. **Section 9 stands
-- depth is the axis that multiplies facts, and it has no gap to exploit.**

### What the measurement did find

Exclusion SETS are large where fact COUNT is not: **mean 81-670 entries, max 18,284**. That is a
per-fact SIZE effect rather than a population effect, and the two must not be confused. Its memory
weight is probably bounded by sharing -- the heap differential of Claim 94 showed `[LExclusionSet;`
growing only 27.7 MB against `AccessNode`'s 398 MB -- so this is recorded as an observation, not a
lever, until someone measures retained size per exclusion set rather than entry counts.

`scheduleTask` is the outlier at 3.4 average exclusions against 81-670 elsewhere, so exclusion growth
is method-specific and tracks the same `WorkflowExecutorOps` cluster as everything else in this report.

### Method note

This is the THIRD mechanism hypothesis in this investigation and the third refuted by measurement:
"shallow and numerous" (refuted -- facts are deep), "tight cycles" (refuted -- repeat ratio is 2.2-4.1,
recursive containment rather than self-loops), and now "exclusions multiply facts" (refuted -- 1.03-1.35).
Each refutation narrowed the cause, and the surviving explanation -- unbounded recursive containment
producing a fact population whose mass sits exactly where the findings live -- is the only one that has
survived a direct test.

## 11. WHAT builds the deep paths: nested untyped payload maps, plus one suspicious chain

Accessor frequencies pooled over facts at size >= 40 in the top methods:

| count | accessor | kind |
|---|---|---|
| 34,469 | `Map#MapValue` | collection abstraction |
| 29,489 | `Iterator#Element` | collection abstraction |
| 28,152 | `Iterable#Element` | collection abstraction |
| **23,660** | **`StrBuilder#buffer`** | **library internal** |
| **23,660** | **`ConductorProperties#stack`** | **config field** |
| **23,660** | **`BaseDynoDAO#conductorProperties`** | **field** |
| 20,235 | `Map$Entry#Value` | collection abstraction |
| 18,968 | `WorkflowModel#outputPayload` | domain |
| 18,837 | `WorkflowModel#output` | domain |
| 17,282 | `TaskModel#outputData` | domain |
| 15,138 / 15,125 | `WorkflowModel#inputPayload` / `#input` | domain |
| 14,863 | `InMemoryJedisCommands#strings`, `<static>(__spring_registry__)`, `__spring_registry__#JedisCommands` | static registry |
| 13,760 / 13,445 | `HttpHeaders#headerValue` / `#headerValues` | library |

Roughly **40% collection abstractions**, **40% application domain fields**, **20% library internals and
the Spring registry**.

### The containment loop, named

`WorkflowModel#output` and `#outputPayload` are MAPS; `Map#MapValue` reads their values; those values
are `TaskModel`s whose `#outputData` is another map; and so on. **The recursion of section 9 is
conductor's nested untyped payload maps**, traversed through the collection abstractions. That is
REAL program structure, not an artifact -- a workflow engine passing arbitrary payload maps between
tasks. So the remedy has to be the re-entry operator; there is no approximation that makes this go
away without losing the flows.

### One chain that IS suspicious

`BaseDynoDAO#conductorProperties`, `ConductorProperties#stack` and `StrBuilder#buffer` each appear
**exactly 23,660 times**. Identical counts mean they always occur TOGETHER: a single fixed chain
`BaseDynoDAO.conductorProperties -> ConductorProperties.stack -> StrBuilder.buffer`. That is taint
being tracked into the CHARACTER BUFFER of a configuration property's string builder.

A config property is not attacker-controlled and its `StrBuilder` internals cannot carry a finding,
and `StrBuilder` has **no approximation** in either `passthrough/` or `dataflow-compiled/`, so the
analysis descends into it.

**Sized honestly, this is a small win.** The chain appears 23,660 times, which is **68.2% of all deep
facts** but only **1.10% of the 2,157,532 accepted edges in the run**. It looks large in the deep
histogram because the deep tail is itself tiny (section 9: 2.5% of the population). Fixing it is
cheap, recall-safe and worth doing on its own merits -- an approximation for `StrBuilder`, and the
same treatment for `__spring_registry__` -- but it removes about one percent of the fact population
and will not move the gate. It is listed here as a correct small fix, NOT as a candidate remedy.

The `__spring_registry__` entries (14,863 and 12,563) are the known global-static broadcast and are a
second candidate of the same kind.

### Standing conclusion of the investigation

| question | answer |
|---|---|
| WHICH methods explode | 3 `WorkflowExecutorOps` methods, 58.7% of accepted edges, ONE context each |
| WHY | unbounded traversal of nested untyped payload maps (recursive containment) |
| Fact shape | deep (median 13, max 559), narrow (few bases), `[any]` idle at 0.06% |
| Is it cycles | no -- repeat ratio 2.2-4.1, containment re-entry not self-loops |
| Do exclusions multiply facts | no -- 1.03-1.35 facts per path |
| Can a depth bound work | **no, at any value** -- the mass sits where the findings live (sections 8, 9) |
| Cheap safe win available | `StrBuilder` approximation: 68.2% of DEEP facts but only **1.10% of all facts** -- real, small, not a remedy |
| The operator the evidence points to | bound RE-ENTRY into an already-visited containment shape (unbuilt) |

## 12. Container nesting HAS the gap depth lacked -- and the real blocker is elsewhere

Container-nesting count per fact (hops through `MapValue` / `MapKey` / `Element` / `Value` / `Entry`):

| method | facts | median hops | p90 | hops 0-2 | **hops >= 3** |
|---|---|---|---|---|---|
| `rerunWF` | 312,975 | 3 | **15** | 40.2% | **59.8%** |
| `decide` | 272,644 | 3 | **14** | 43.0% | **57.0%** |
| `terminateWorkflow` | 141,024 | 2 | 8 | 63.1% | 36.9% |
| `startWorkflow` | 71,604 | 2 | 7 | 64.9% | 35.1% |
| `testWorkflow` | 64,348 | 2 | 5 | 69.5% | 30.5% |
| `scheduleTask` | 47,641 | 1 | 8 | 69.2% | 30.8% |

**This axis has the separation total depth does not.** The median is 1-3 hops while p90 is 5-15, so a
bound at 3 or 4 sits ABOVE the median and still removes **30%-60%** of the fact population. Compare
the depth axis, where a bound above the median (16-48) removed only ~2.5% (section 9). On paper this
is the lever the whole investigation has been looking for.

### Why it will probably fail anyway, and what that identifies

Section 8 measured something that constrains every future bound on every axis. Depths 16, 24, 32 and
48 all sit ABOVE the median depth of 13 and cut under 6% of facts -- and all four lost **exactly the
same two findings**, `path-traversal` and `ssrf`. A 6% truncation cannot account for losing half the
findings, and a truncation effect would be gradual rather than identical at every value.

So the loss is not caused by removing facts. It is caused by ABSTRACTING them: `coveredTopNode`
enlarges a fact as a DENOTATION, but the abstracted fact does not ANSWER the demand the concrete one
answered, so the flow is never re-derived. Any container-hop bound would use the same abstraction and
would therefore lose the same two findings, whatever the gap in its distribution.

**The blocker is not the choice of axis, the value, or the fact population. It is that in this engine
a widening is lossy.** That single defect explains: why depth failed at every value from 3 to 48; why
`contextCap` destroyed traces; why `tifaNoUnroll` loses findings; and why a container bound with a
genuine 30-60% gap is still not worth building until it is fixed.

**Recommended order of work:**

1. **Fix the widening.** Make an abstracted fact answer the demand its concrete form answered
   (`registration, not matching`). Nothing else in this list can pay off until this holds.
2. **Then bound container nesting at 3-4.** It is the only axis measured to have a real gap: 30-60%
   of facts above a bound that sits above the median.
3. Separately and cheaply, approximate `StrBuilder` and the `__spring_registry__` statics -- correct,
   recall-safe, and worth about 1% of facts (section 11).

## 13. What the depth results imply about the findings themselves

An inference worth stating explicitly, because it constrains every remedy.

`factShapeDepth=48` abstracts only facts LONGER than 48 accessors. If no fact on a flow ever exceeded
48, the abstraction never fires on that flow and the finding cannot be lost. `path-traversal` and
`ssrf` ARE lost at depth 48. Therefore:

> **Both lost findings depend on access paths deeper than 48 accessors.**

That is consistent with the measured maxima (247, 217, 181, 559) and it explains why this problem has
resisted every bound: conductor's real taint flows genuinely traverse very deep paths through the
nested payload maps. The deep facts are not waste that can be trimmed -- **some of them carry the
findings.**

It also predicts the fate of the container-hop bound of section 12. Deep paths are built by container
re-entry, so a flow requiring >48 accessors almost certainly requires more than 3-4 container hops.
A bound at 3-4 would abstract facts on those same flows and lose the same two findings -- for the
same reason, on a different axis. The 30-60% gap is real and still not usable while widening is lossy.

### Why fixing the widening is hard, and what it needs

`registration-not-matching` is PROVED and four repairs have already failed (term 4, the star reader,
the epsilon reading, and repairing the registered terminal). The reason none worked is recorded there:
`AccessTree.delta` builds the caller's remainder -- the CONCLUSION -- from the PREMISE's accessors, so
`a.f` and `a.[any]` yield different conclusions. A reader cannot repair it because the fact was never
created.

The stated obligation is `DominatesRegistered` = matching domination AND
`factLe (registeredFact p) (registeredFact (f p))`: **a collapsed premise must register a fact
carrying the terminal flags of the concrete facts it stands for.** That is a change to
`addAnalyzedInitialFact`, not to any reader.

**This is the whole problem, reduced to one obligation.** Every performance result in this
investigation is downstream of it: with a sound widening the container bound removes 30-60% of facts;
without one, no bound on any axis is usable at any value.

## 14. The two headline findings are the same finding

`TreeApManager.shapeDepthFor` is the depth budget, and the abstraction it applies is
`coveredTopNode` -- an `[any]` edge. So:

* **At default settings there is no bound, therefore no `[any]`** -- confirmed by
  `starSeen=0 starHit=0 tifaWild=0 anyUnroll=0` and abstract facts at 0.12%-0.42%. The analysis runs
  at full precision and explodes on genuine structure (nested payload maps).
* **Switching ANY bound on creates `[any]` facts** -- and `registration-not-matching` then makes them
  answer less than the concrete facts they replaced, so `path-traversal` and `ssrf` disappear.

**`[any]` is idle exactly because nothing is bounding, and every attempt to bound makes `[any]`
appear and lose findings.** The wildcard machinery is not unused because it is unnecessary; it is
unused because it is the failure mode. That is one mechanism seen from two sides, and it explains the
shape of this entire investigation: every arm either ran at full precision and OOMed, or bounded
something, created wildcards, and lost recall.

It also sharpens the obligation of section 13. The fix is not "make bounds gentler" or "find a better
axis" -- both were measured to fail. It is to make the `[any]` fact a faithful replacement for the
concrete facts it stands for, at REGISTRATION. Until then the analysis has exactly two operating
points: precise and explosive, or bounded and lossy.

## 15. Where the repair must go, and how big it is

For whoever takes this on. Two candidate sites were checked and one is already correct:

**The fact-side terminal flags are NOT the bug.** `TreeApManager.abstractFinalNode` is already
`isFinal = true, isAbstract = true`, and `coveredTopNode = createAnyEdge(this, abstractFinalNode)` is
`isFinal = true` landing on a final node. The repair recorded in `registration-not-matching` ("a
premise ending in `[any]` now seeds `abstractFinalNode`") is in the tree and was still not enough.
Do not spend time there again.

**The site is `AccessPath.splitDelta`** (`AccessPath.kt:148`), which walks the premise's path against
the fact tree and produces the caller's `(matchedFact, Delta)`. In its abstract branch it returns a
SINGLE pair:

```kotlin
if (otherNode.isAbstract) {
    val filteredNode = node.filter(other.exclusions) ?: return emptyList()
    ...
    return listOf(matchedFact to AccessPathDelta.Delta(filteredNode))
}
```

This is exactly `Conclusion.lean`'s `build`: the conclusion computed from the premise's accessors.
The signature already returns a LIST, so the shape needed to return several continuations is present;
what is missing is that the abstract branch must yield a conclusion dominating what the concrete
premises it stands for would each have produced (`DominatesDerivation`).

**Size: this is the core of the graft.** Every derivation in the analysis passes through it, so a
change here is not a patch -- it needs its own design, its own Lean obligation discharged against
`Conclusion.lean`, and a full gate run to validate. The validation is at least cheap and reliable:
the failure mode is a shrinking SARIF finding set, which every depth arm in section 8 exposed
immediately.

**Do NOT judge a candidate by `Total vulnerabilities`** -- it stayed at 4 while the SARIF set fell to
2 for `contextCap` (section GUIDELINES 4.16). The SARIF set is the signal.

## 16. The repair ATTEMPT (fifth), and what its failure eliminates

Status: **REFUTED, and the site is eliminated.**

Section 15 located the defect in `AccessPath.splitDelta`'s abstract branch: a premise `a.$` against
the CONCRETE fact `.a.$` returns `(a.$, Empty)`, while against the WILDCARD fact `.[any].$` it falls
into the abstract branch and returns `(base, delta = a.$)` -- a SHORTER matched fact, so the caller
looks up a different summary. That is `Conclusion.matching_domination_is_not_enough` visible in the
source.

`opentaint.absFullCover` makes that branch return `(this, Empty)`: `[any]` denotes every continuation,
so it covers the whole remaining premise, not merely the prefix walked. It can only claim MORE
(`Conclusion.collapse_can_be_repaired`), so the exposure is false positives, never a false negative.

| arm | oom | forward vulns | SARIF | events | findings |
|---|---|---|---|---|---|
| repair OFF, no bound (control) | 1 | 4 | **4** | 1,428,057 | all four |
| repair ON + depth 16 | 1 | 2 | **2** | 5,386,288 | lost `path-traversal`, `ssrf` |
| repair ON + depth 3 | 1 | 2 | **2** | 7,525,898 | lost `path-traversal`, `ssrf` |

**The repair does not restore the findings.** The control confirms default behaviour is undisturbed,
so the change is correctly gated.

### What the event counts eliminate

| | without repair | with repair | difference |
|---|---|---|---|
| depth 16 | 5,386,109 | 5,386,288 | **0.003%** |

**The flag is very nearly inert.** A change to a branch that carried this loss would move the event
count materially; 0.003% says the abstract branch of `AccessPath.splitDelta` is **rarely taken** and
is NOT where `path-traversal` and `ssrf` are lost.

So the mechanism of `Conclusion.lean` stands -- conclusion-dependence is real and proved -- but this
SITE is eliminated. The loss happens somewhere else: candidates are the reader (`meets`) under a
bounded fact, the construction of the bounded fact tree itself in `TreeInitialFactAbstraction`, or a
different `splitDelta` reached through the summary path.

**This is the fifth failed repair, and the first whose failure NARROWS the search rather than merely
adding to the count.** The previous four each preserved matching and lost a finding, with no evidence
about where. This one carries a measurement -- 0.003% -- that rules its own site out. Record the next
attempt the same way: instrument the branch you change and prove it FIRES before drawing any
conclusion from the finding set.

## 17. LOCALISED: the two findings are lost for DIFFERENT reasons, both UPSTREAM of the sink

Method census with a watch-list on the two sink methods, unbounded against `factShapeDepth=16`, same
jar, same census.

| method | unbounded (4 findings) | depth 16 (2 findings) |
|---|---|---|
| `HttpTask#start` | 2,463 / 3,324 | 2,379 / 3,138 |
| `HttpTask#httpCall` (**ssrf sink**) | **405** / 550 | **243** / 369 |
| `DummyPayloadStorage#upload` | 330 / 538 | 330 / 538 (identical) |
| `DefaultRestTemplateProvider#getRestTemplate` | 141 / 213 | 141 / 213 (identical) |
| `HttpTask#extractBody` | 64 / 100 | 64 / 100 (identical) |
| `DummyPayloadStorage#download` (**path-traversal sink**) | **45** / 67 | **ABSENT** |

### Two different failures

**`path-traversal`: the sink method receives FEWER facts.** *(CORRECTED in section 18: the earlier
reading of "ABSENT" was the report's top-N CUTOFF, not absence. With the watch-list widened,
`DummyPayloadStorage#download` is present under the bound at 38/56 against 45/67 unbounded -- a 16%
reduction, not a severance.)* The break is UPSTREAM of the sink.

**`ssrf`: the sink IS reached, with 40% fewer facts.** `HttpTask#httpCall` keeps 243 of 405 accepted
edges. The method is analysed; the particular fact the rule needs is among the missing 40%. The break
is again upstream -- what arrives is a smaller fact set, not a mis-read one.

### What this eliminates

**The sink-side reader is NOT the cause.** Both failures are a shortage of arriving facts, not a
failure to recognise facts that arrived. So `meets` at the sink, and any reader repair, is ruled out
-- consistent with the four historical reader repairs (term 4, star reader, epsilon reading) having
failed, and now with a measurement rather than an inference.

**The bound is also NOT indiscriminate.** `upload`, `getRestTemplate` and `extractBody` are
byte-identical between the arms (330/538, 141/213, 64/100). Only the two flows that carry findings
lose facts. Whatever the defect is, it fires on specific propagation paths rather than degrading
everything, which is why aggregate counters never revealed it.

### The next step, stated as a procedure

Walk UPSTREAM from `DummyPayloadStorage#download` with the same watch-list: find the caller whose
accepted-edge count diverges between the arms. That caller is where the flow is severed, and the
abstraction that severs it is visible in its facts. Repeat until the divergence point is a single
method -- then instrument THAT propagation step and prove it fires before changing it (section 16).

## 18. CORRECTION and the sharper signal: the bound RELOCATES work off the finding paths

Section 17 read `DummyPayloadStorage#download` as ABSENT under the bound. **That was the report's
top-N cutoff, not absence.** With the watch-list widened to the whole payload-storage chain, both
sinks are present under the bound:

| method | unbounded (4 findings) | depth 16 (2 findings) | change |
|---|---|---|---|
| **`ExternalPayloadStorageUtils#verifyAndUpload`** | 2,166 / 2,887 | **8,545 / 10,567** | **x3.9 MORE** |
| **`ExternalPayloadStorageUtils#shouldUpload`** | 122 / 150 | **442 / 470** | **x3.6 MORE** |
| `HttpTask#httpCall` (**ssrf sink**) | 405 / 550 | 243 / 370 | **x0.60** |
| `DummyPayloadStorage#download` (**path-traversal sink**) | 45 / 67 | 38 / 56 | **x0.84** |
| `ExternalPayloadStorageUtils#downloadPayload` | 185 / 282 | 174 / 263 | x0.94 |
| `DummyPayloadStorage#upload`, `S3PayloadStorage#*`, `getRestTemplate` | -- | identical | x1.00 |

**Neither flow is severed. Both are ATTENUATED, and the work goes somewhere else.** Under the bound
the analysis does **3.9x more work in `verifyAndUpload`** and 3.6x more in `shouldUpload` -- the UPLOAD
path -- while the two DOWNLOAD/request paths that carry the findings lose 16% and 40% of their facts.

That is the relocation behaviour of sections 2 and 9, now visible at method level ON THE FLOW: the
bound does not remove work, it moves it from the paths that produce findings onto a path that does
not. The finding is lost because the specific facts its rule needs are among the ones displaced, not
because the sink is unreachable or misread.

### What this means for a repair

The target is now specific: **why does abstracting facts push work from `downloadPayload`/`httpCall`
into `verifyAndUpload`?** Both are reached from the same `ExternalPayloadStorageUtils`, so the
divergence is inside that class's propagation, and the two arms differ by nothing but
`factShapeDepth=16`.

**Method note.** Section 17's "ABSENT" was wrong for a mundane reason -- a display limit read as
data -- and it would have sent the next attempt hunting a severed edge that does not exist. The
watch-list exists precisely because top-N views hide small methods; it must be used for any claim
about a method that is not already in the top 40.

## 19. THE MECHANISM AT SOURCE LEVEL: a generic method with four type-dispatched branches

`ExternalPayloadStorageUtils#verifyAndUpload` -- the method that gains **3.9x** work under the bound:

```java
public <T> void verifyAndUpload(T entity, PayloadType payloadType) {
    if (!shouldUpload(entity, payloadType)) return;
    ...
    switch (payloadType) {
        case TASK_INPUT:      payload = ((TaskModel) entity).getInputData();   ...
        case TASK_OUTPUT:     payload = ((TaskModel) entity).getOutputData();  ...
        case WORKFLOW_INPUT:  payload = ((WorkflowModel) entity).getInput();   ...
        case WORKFLOW_OUTPUT: payload = ((WorkflowModel) entity).getOutput();  ...
    }
```

**A GENERIC parameter `T entity`, cast in FOUR branches, each reading one of the four nested payload
maps** -- `getInputData`, `getOutputData`, `getInput`, `getOutput`. These are exactly the accessors
section 11 measured as building the deep paths (`TaskModel#outputData` 17,282,
`WorkflowModel#output` 18,837, `#outputPayload` 18,968, `#inputPayload` 15,138).

### Why the bound multiplies work here by four

At full precision a fact on `entity` carries the concrete path that identifies WHICH payload map it
came from, so it matches ONE branch. Abstract that fact and the distinction is gone: the wildcard
matches `TaskModel.getInputData`, `TaskModel.getOutputData`, `WorkflowModel.getInput` AND
`WorkflowModel.getOutput`. One fact becomes four.

**Measured x3.9. Branches: 4.** `shouldUpload`, called on the same generic parameter from the first
line, gains **x3.6** -- the same fan-out one frame up.

### And this is why the findings die

The fan-out is not free precision loss; it consumes the budget. Every abstracted fact arriving at
`verifyAndUpload` spawns four, and that work displaces the propagation the finding flows need --
`httpCall` loses 40% of its facts and `download` 16%, while the upload path grows four-fold. The
findings are not mis-read at the sink (section 17) and the flows are not severed (section 18); they
are STARVED by a fan-out the abstraction created upstream.

### The consequence for any bound

This is a property of the CODE, not of the bound's value: `verifyAndUpload` is a generic dispatcher
over the four payload maps, so ANY abstraction that erases the payload-map distinction fans out four
ways there. That is why depth 3 and depth 48 lose the same two findings -- both erase the same
distinction; the value is irrelevant, exactly as section 8 measured.

**It also names the property a usable widening must have: it must preserve enough of the path to keep
the four `payloadType` branches apart.** A bound that abstracts BELOW the payload-map accessor would
do so; every bound tested truncates from the root and does not.

## 20. THE DESIGN the measurement prescribes -- and the infrastructure already exists

Section 19 gives the requirement: **a usable widening must preserve enough of the path to keep the
four `payloadType` branches apart.** Every bound tested fails it for the same structural reason.

`AccessTree.truncateToWildcard(limit)` truncates **from the ROOT**: it keeps `limit` levels and
collapses everything below into `[any]`. So the accessor that distinguishes `getInputData` from
`getOutputData` survives only if it happens to sit within the first `limit` levels. With a median
fact depth of 13 and a maximum of 559 (section 7), on most facts it does not -- **which is why depth
16 and depth 48 lose exactly what depth 3 loses.** The bound keeps the wrong end of the path.

### The shape needed

```
(prefix)          . [any] .  (tail)
 identifies base            identifies WHICH field is read
```

The distinguishing accessor is at the TAIL, next to the read. A truncation that keeps a suffix
preserves the branch distinction; one that keeps only a prefix cannot.

### This is a generalisation, not a new mechanism

`truncateToWildcard` **already keeps a tail below the wildcard** -- for MARKS:

```kotlin
if (limit <= 0) {
    val marks = IntOpenHashSet()
    collectTailMarks(marks)          // marks are NOT covered by [any], so they are kept
    ...                               // ... BELOW the wildcard: prefix . [any] . marks
```

Marks are kept below `[any]` precisely because `[any]` does not cover them and dropping them loses
the taint. **The payload-map accessor has the same property for a different reason**: dropping it
does not lose the taint, it loses the ability to tell four branches apart, and the fan-out that
follows starves the finding flows.

So the change is: **generalise `collectTailMarks` to retain the last FIELD accessor(s) as well as the
marks**, giving `prefix . [any] . field . marks`. That is still ONE `[any]` per path, which is the
invariant `AccessTreeAnySuffixMatcher` enforces and the reason the marks were put below the wildcard
rather than above it.

### How to validate it, given five failed repairs

1. Instrument the branch and prove it FIRES (section 16: the fifth repair moved events 0.003% because
   its branch was never taken).
2. Judge on the SARIF finding SET, never `Total vulnerabilities` (GUIDELINES 4.16).
3. The specific expected signal: **`verifyAndUpload` should NOT gain 3.9x.** If the tail keeps the
   branches apart, the fan-out disappears and `httpCall` keeps its 405 facts rather than 243. That is
   a sharper and cheaper test than a full gate run -- it can be read from the watch-list census in a
   single arm.
4. Only then measure the gate.

**This is the first candidate in this work whose predicted effect is a specific number on a specific
method, rather than a hope that the finding set survives.**

## 21. The tail-field repair: prediction FAILED, direction CONFIRMED, keep-rule WRONG

Section 20 predicted, in advance, that keeping the last covered accessor in the tail would remove the
four-way fan-out: `verifyAndUpload` should not remain near 8,545, and `httpCall` should recover toward
405 from 243.

| method | unbounded | depth 16 | **depth 16 + tailKeepField** | predicted |
|---|---|---|---|---|
| `verifyAndUpload` | 2,166 | 8,545 (x3.94) | **7,756 (x3.58)** | ~2,166 |
| `shouldUpload` | 122 | 442 (x3.62) | **378 (x3.10)** | ~122 |
| `httpCall` | 405 | 243 | **270** | ~405 |
| `downloadPayload` | 185 | 174 | 174 | -- |
| **SARIF findings** | **4** | 2 | **2** | 4 |

**The prediction is not met.** The fan-out falls from x3.94 to x3.58 and `httpCall` recovers 11% of
its loss (243 -> 270 against 405). Both findings stay lost.

### What it nonetheless confirms

**Every number moved in the predicted direction.** The fan-out shrank, both upload methods shrank,
and the starved sink recovered -- none of that is what noise looks like, and it is the first
intervention in this work to move `httpCall` back up at all. The MECHANISM of section 19 stands: the
fan-out is driven by lost path information, and restoring some of it recovers some of the flow.

### Why the keep-rule is wrong

`hasCoveredAccessor()` keeps a covered accessor only when its child has NO covered edges -- i.e. the
DEEPEST field on the path. But the distinguishing accessor is `entity.inputData` / `.outputData` /
`.input` / `.output`, and each of those has the payload MAP below it (`Map#MapValue`, `Element`),
which ARE covered. So the very accessors that separate the four branches are exactly the ones this
rule does not keep -- it keeps whatever sits below them instead.

That explains the small effect precisely: the change kept a field, just not the one that matters.

### What the next rule must be

Keep the covered accessor **nearest the truncation point**, not the deepest one -- the first field
below where the prefix ends, which is what identifies the read. That is a different traversal:
`collectTailMarks` descends to the leaves, so it must instead record the accessor at the moment it
crosses the budget.

**Judge it the same way**: `verifyAndUpload` back near 2,166 and `httpCall` near 405, read from one
watch-list census arm, before any gate run.

## 22. Attempt seven REFUTES the section 19 model: the fan-out is not path identity

| method | unbounded | depth 16 | +deepest-field | **+at-cut field** | predicted |
|---|---|---|---|---|---|
| `verifyAndUpload` | 2,166 | 8,545 | 7,756 | **7,442 (x3.44)** | ~2,166 |
| `shouldUpload` | 122 | 442 | 378 | **362** | ~122 |
| `httpCall` | 405 | 243 | 270 | **270 (identical)** | ~405 |
| `downloadPayload` | 185 | 174 | 174 | 174 | -- |
| **SARIF findings** | **4** | 2 | 2 | **2** | 4 |

**Both keep-rules give essentially the same small effect**, and `httpCall` is IDENTICAL at 270 under
each. Correcting the rule from "deepest covered accessor" to "the accessors AT the cut" -- the change
section 21 argued was the whole error -- moved `verifyAndUpload` from 7,756 to 7,442, about 4%.

### What this refutes

Section 19 claimed the x3.9 fan-out is caused by abstraction erasing WHICH payload map a fact came
from, so one fact matches all four `payloadType` branches. **If that were the cause, restoring the
distinguishing accessor would have removed most of the fan-out.** Keeping exactly those accessors
removes 13% of it. The model is refuted as the DOMINANT cause.

The branch-count coincidence (x3.9 against 4 branches) that made the model persuasive was just that
-- a coincidence. It is worth recording how convincing it looked: a generic dispatcher, four casts,
four payload accessors already known to build the deep paths, and a measured ratio matching the
branch count to one decimal place.

### The alternative the evidence now points at

`verifyAndUpload` is `<T> void verifyAndUpload(T entity, PayloadType payloadType)`. **`T` is a generic
parameter, erased to `Object` in bytecode.** This project has already measured that
`object-typed-edges-disable-type-filter`: on an `Object`-typed value the type filter admits every
field, because it has no type to filter against.

That explains what path retention could not. The disambiguation lost at `verifyAndUpload` is not the
ACCESS PATH -- which the at-cut rule restored, to no effect -- it is the TYPE. No amount of path
information helps when the receiver is `Object` and the filter is therefore disabled.

**It also predicts the fan-out is NOT specific to abstraction**: it should appear wherever a tainted
value reaches an erased generic, bounded or not. The bound would then merely increase the traffic
through that already-open gate, rather than opening it.

### Status of the repair line

Seven attempts. Attempts 1-4 (historical) preserved matching and lost findings. Attempt 5 changed a
branch that never fires (0.003% event change). Attempts 6 and 7 restored path identity, by two
different rules, and moved the fan-out 13%. **The path-identity model is closed.** The type-erasure
explanation is untested and is the next thing to measure, NOT to repair.

## 23. MEASURED: the fan-out needs BOTH an erased generic AND abstraction

Accessor diversity -- distinct accessors appearing in a method's facts -- alongside accepted edges.
`verifyAndUpload` and `shouldUpload` take the erased generic `T entity`; the rest have concrete
receiver types.

| method | receiver | unbounded (accepted / **distinct accessors**) | depth 16 |
|---|---|---|---|
| `verifyAndUpload` | **erased `T`** | 2,168 / **33** | 9,090 / **36** |
| `shouldUpload` | **erased `T`** | 122 / **4** | 474 / **22** |
| `httpCall` | concrete | 410 / 20 | 270 / **4** |
| `DummyPayloadStorage#download` | concrete | 45 / 3 | 38 / **1** |
| `extractBody` | concrete | 81 / 0 | 81 / 0 |
| `getRestTemplate` | concrete | 141 / 0 | 141 / 0 |

### Neither factor alone explains it

**Type erasure alone does not.** Unbounded, `shouldUpload` -- an erased generic -- admits just **4**
distinct accessors. An open type filter is not sufficient to cause a fan-out.

**Abstraction alone does not.** The concrete-typed methods do not fan out under the bound; they
CONTRACT (`httpCall` 20 -> 4, `download` 3 -> 1), and two of them are entirely unchanged.

**Together they do.** Bounded, `shouldUpload` goes **4 -> 22 distinct accessors (x5.5)** and its
traffic **x3.9**. At full precision the concrete access path constrains what the fact can be; the
abstraction removes that constraint, and the erased `Object` receiver supplies none in its place, so
nothing constrains the fact at all.

`verifyAndUpload` shows the same gate from the other side: its diversity is ALREADY 33 unbounded and
barely moves (36) while traffic rises x4.2. The gate there is open regardless of the bound -- the
bound only drives more traffic through it.

### Why path retention moved only 13%

Attempts 6 and 7 restored path identity and recovered a little. That fits: path is ONE of the two
constraints, and restoring part of it helps proportionally. The other constraint -- the type -- cannot
be restored by keeping accessors, because there is no type to restore. That is why two different
keep-rules produced nearly identical results (x3.58, x3.44).

### The design this prescribes, and how to test it

**Do not abstract facts that flow into an erased-generic receiver.** At such a call site the type
filter contributes nothing, so the access path is the ONLY thing constraining the fact, and
truncating it leaves the fact unconstrained. Exempting those sites keeps precision exactly where its
loss is unrecoverable, and costs precision nowhere else -- the concrete-typed methods are unaffected
by the bound already (`extractBody` and `getRestTemplate` are byte-identical).

Falsifiable test, before any gate run: **`shouldUpload` diversity must stay near 4, not rise to 22**,
and `verifyAndUpload` traffic must stay near 2,168. Read from one watch-list census arm.

**Status: this is the first mechanism in this investigation derived from measurement rather than from
reading the source and matching a statistic.** Both models built the other way (section 22) were
refuted.

### Implementation requirement (why this was not attempted here)

The design of section 23 needs to know, at the moment a fact is truncated, whether it is flowing into
an erased-generic receiver. **That information is not available there.** The type filter is applied in
`Propagator` (`filterFactByLocalType(fromPositionBaseType, ...)`), which has the position types;
`AccessTree.truncateToWildcard` is in the access-tree layer and has no type context at all --
`TreeApManager.shapeDepthFor(base)` receives only an `AccessPathBase`.

So the change is not a patch to the truncation: it requires plumbing the position type (or a
precomputed "receiver is erased" predicate) from `Propagator` down to the shape budget. That is a
real architectural change and it should be designed, not improvised -- which is why it stops here
rather than becoming an eighth attempt.

**What the next session inherits:** a measured mechanism, a stated design, a falsifiable test that
costs one census arm (`shouldUpload` diversity must stay near 4, not 22), and the knowledge that the
information needed is one layer above where the decision is made.

## 24. THINGSBOARD: the diagnosis is only PARTLY transferable, and the decisive column differs

ThingsBoard, 12 GB, IFDS 800 s, full ruleset, same census. `rc=253 oom=1`, **15 findings, SARIF 15**
(no trace loss). 51,163 methods, 12,557,359 offered, 6,791,366 accepted.

| accepted | share | **contexts** | method |
|---|---|---|---|
| 1,154,056 | 17.0% | **1** | `DefaultWidgetTypeService#save` |
| 873,310 | 12.9% | **6** | `DefaultWidgetTypeService#save` (other overload) |
| 444,714 | 6.6% | 1 | `WidgetTypeServiceImpl#saveWidgetType` |
| 440,188 | 6.5% | 1 | `BaseImageService#updateImagesUsage` |
| 330,552 | 4.9% | **3** | `JacksonUtil#replaceByMapping` |
| 236,050 | 3.5% | 1 | `BaseResourceService#updateResourcesUsage` |
| 231,887 | 3.4% | **108** | `EntityActionService#pushEntityActionToRuleEngine` |
| 127,639 | 1.9% | **4** | `JacksonUtil#replaceAll` |

### What transfers

A small set of methods dominates, and the same KIND of code does it: entity-save paths plus
`JacksonUtil#replaceByMapping` / `#replaceAll`, which are generic JSON-tree walkers -- the ThingsBoard
analogue of conductor's nested payload maps.

### What does NOT transfer, and it is the column that decided conductor

**Conductor's exploding methods all have `contexts = 1`.** That is what eliminated `contextCap`
there: there is no context population to bound. **ThingsBoard does not:** the same
`DefaultWidgetTypeService#save` appears at `ctx=1` and `ctx=6`, and
`EntityActionService#pushEntityActionToRuleEngine` runs at **`ctx=108`**.

Concentration is also lower -- top three 36.4% against conductor's 58.7%, over 51,163 methods against
14,816.

### Consequence for scoping any fix

**The two projects fail for different reasons and do not share a remedy.**

* On conductor a context bound is provably pointless (`ctx=1`) and the erased-generic fan-out of
  section 23 is the mechanism.
* On ThingsBoard there IS a real context population (108 on a top-8 method), so context-side work is
  live there -- while `contextCap` remains closed for BOTH, because it destroys traces (section
  GUIDELINES 4.16 and Claim 100), not because there is nothing to cap.

This is the method-level form of the standing project-specificity result, and it says the
erased-generic plumbing of section 23 should be justified on conductor alone. ThingsBoard needs its
own diagnosis -- starting from `pushEntityActionToRuleEngine` at 108 contexts, which nothing in this
investigation has looked at.

## 25. CORRECTION: `contextCap` does NOT destroy traces on ThingsBoard

Claim 100 measured that `contextCap` holds `Total vulnerabilities` at 4 while the SARIF set falls to
2, and concluded the cap is closed as a lever. **That was measured on conductor only, and it does not
generalise.**

ThingsBoard, 12 GB, IFDS 800 s, full ruleset:

| arm | oom | forward vulns | **SARIF set** | elapsed | events | trace filter |
|---|---|---|---|---|---|---|
| no cap | 1 | 15 | **15** | 303 s | 5,242,684 | none |
| `contextCap=300` | 1 | 15 | **15** | 386 s | 2,067,141 | **none** |
| `contextCap=100` | 1 | 15 | **15** | 431 s | 1,988,848 | **none** |

**Every finding keeps its trace at every cap.** No `Filter out` line appears. The trace destruction of
Claim 100 is a CONDUCTOR property, not a property of the cap.

The reason is visible in section 24: conductor's exploding methods all have `ctx=1`, so the cap can
only merge contexts that were never distinct in the first place -- pure loss with nothing gained.
ThingsBoard has a real context population (`ctx=108` on a top-8 method), so merging there discards
genuinely redundant distinctions and the traces survive.

### It is still not a win

All three arms OOM. Events FALL 2.6x (5.24M -> 1.99M) while elapsed RISES (303 s -> 431 s): fewer
events, each more expensive. That is the relocation pattern of sections 2 and 18 once more -- the cap
moves cost rather than removing it.

### What this changes

* **`contextCap` is recall-safe on ThingsBoard and recall-destroying on conductor.** Context-side work
  is therefore open for ThingsBoard and closed for conductor -- the opposite of the single shared
  configuration this project has always looked for.
* **Claim 100 must be read as conductor-specific.** Its mechanism (merging destroys what the trace
  needs) is sound; its scope was over-stated by me on one project's evidence.

**Method note.** This is the third time in this investigation that a result true in one setting was
quoted as a general law -- after "depth 3 keeps both findings" (true on one rule, false at gate scale,
section 6) and "download is ABSENT" (a top-N cutoff read as data, section 18). The cost here was
nearly closing a lever that is recall-safe on the project that still needs one.

## 26. ThingsBoard has a POPULATION problem; conductor has a DEPTH problem

Fact shape per exploding method, same census, both projects at gate scale.

| project | method | facts | **max size** | median | p90 | bases |
|---|---|---|---|---|---|---|
| conductor | `decide` | 417,235 | **247** | 13 | 33 | 25 |
| conductor | `rerunWF` | 408,425 | **181** | 12 | 23 | 31 |
| conductor | `startWorkflow` | 67,118 | **559** | 13 | 31 | 10 |
| ThingsBoard | `DefaultWidgetTypeService#save` | 1,153,964 | **29** | 8 | 10 | 9 |
| ThingsBoard | `saveWidgetType` | 444,628 | **23** | 7 | 10 | 14 |
| ThingsBoard | `updateImagesUsage` | 440,078 | **11** | 6 | 7 | 21 |
| ThingsBoard | `replaceByMapping` | 329,046 | 18 | 6 | 11 | **82** |
| ThingsBoard | `updateResourcesUsage` | 236,002 | **8** | 6 | 7 | 8 |

**ThingsBoard's facts are an order of magnitude SHALLOWER.** Max size 8-29 against conductor's
181-559; median 6-8 against 13. Its single largest method holds **1,153,964 facts whose deepest is 29
accessors** -- more facts than any conductor method, in a far smaller shape.

### The two projects fail for opposite reasons

* **Conductor: DEPTH.** Few methods, deep facts (max 559), and the mass sits at the median where the
  findings live -- so no depth bound has a gap (sections 8, 9).
* **ThingsBoard: POPULATION.** Shallow facts (max 29, p90 7-11), but an enormous number of them
  spread over 51,163 methods. **A depth bound is irrelevant there**: set it at 12 and it clears p90
  on every top method, touching almost nothing.

That is why the same operator has produced opposite effects on the two projects throughout this
work, and it is the method-level explanation of the standing project-specificity result.

### What each project actually needs

| | conductor | ThingsBoard |
|---|---|---|
| shape | deep, narrow | shallow, wide |
| contexts | `ctx=1` | up to `ctx=108` |
| depth bound | destroys findings | irrelevant -- nothing to cut |
| `contextCap` | destroys traces | **recall-safe** (section 25), 2.6x fewer events, still OOM |
| lever indicated | reduce DEPTH soundly (blocked: widening is lossy) | reduce POPULATION |

**`[any]` is idle on BOTH** -- `starSeen=0 tifaWild=0 anyUnroll=0` on ThingsBoard as on conductor.
The one exception worth noting is `JacksonUtil#replaceByMapping`, where abstract facts are 21,483 of
329,046 (6.5%) against 0.01-0.5% elsewhere, and bases reach 82. That is ThingsBoard's only
wildcard-carrying hotspot and nothing in this work has examined it.

**Consequence: there is no single configuration for both projects, and the search for one has been
mis-framed.** Conductor needs a sound depth reduction; ThingsBoard needs a population reduction that
is not `contextCap` -- which is recall-safe there but costs more time than it saves.

## 27. ThingsBoard's mechanism: RE-DERIVATION, not population

Accepted edges per DISTINCT fact -- how many times the analysis re-derives what it already has.
Same instrument, both projects at gate scale.

| project | method | accepted | distinct facts | **re-derived** |
|---|---|---|---|---|
| ThingsBoard | `EntityActionService#pushEntityActionToRuleEngine` | 231,887 | **494** | **469x** |
| ThingsBoard | `DefaultWidgetTypeService#save` (ctx=6) | 873,310 | 28,641 | **30.5x** |
| ThingsBoard | `DefaultWidgetTypeService#save` (ctx=1) | 1,154,056 | 56,848 | **20.3x** |
| ThingsBoard | `BaseImageService#updateImagesUsage` | 440,188 | 30,130 | 14.6x |
| ThingsBoard | `WidgetTypeServiceImpl#saveWidgetType` | 444,714 | 36,892 | 12.1x |
| ThingsBoard | `JacksonUtil#replaceByMapping` | 330,552 | 40,987 | 8.1x |
| **conductor** | `terminateWorkflow` | 308,095 | 38,301 | **8.0x** |
| **conductor** | `rerunWF` | 300,912 | 62,673 | **4.8x** |
| **conductor** | `decide` | 325,253 | 80,452 | **4.0x** |

**ThingsBoard re-derives 2.5x to 117x more than conductor.** Its largest method holds only **56,848
distinct facts** and accepts **1,154,056 edges** for them. `pushEntityActionToRuleEngine` is the
extreme: **494 distinct facts, 231,887 accepted edges, 469x** -- and it is the method running at
`ctx=108` (section 24).

Section 26 called this a POPULATION problem. That was wrong in the same way "conductor has a depth
problem" is right: the fact COUNT is large, but the distinct-fact count is small. **The cost is
re-derivation of a small population, not the population itself.**

### Three distinct diseases, one per project, none shared

| | conductor | ThingsBoard |
|---|---|---|
| fact depth | max 181-559, median 13 | max 8-29, median 6-8 |
| distinct facts (top method) | 80,452 | 56,848 |
| **re-derivation** | **4-8x** | **12-469x** |
| contexts | `ctx=1` | up to `ctx=108` |
| indicated lever | sound DEPTH reduction (blocked) | **stop re-deriving** |

`pushEntityActionToRuleEngine` is where the two axes meet: 108 contexts and 469x re-derivation on 494
facts. Whether the contexts CAUSE the re-derivation -- the same fact re-derived once per context --
is the obvious next question and is not answered here. If they do, a context-side fix is worth far
more on ThingsBoard than the 2.6x `contextCap` bought, because it would remove work rather than
relocate it.

**Correction to section 26.** "ThingsBoard has a population problem" is withdrawn. The population is
SMALL (57k distinct facts in its largest method); the problem is that each fact is derived twenty
times over.

## 28. The contexts do NOT cause the re-derivation

Section 27 asked whether `pushEntityActionToRuleEngine`'s 469x re-derivation is the same fact derived
once per context. `contextCap` answers it:

| arm | accepted | **contexts** | distinct facts | **re-derived** |
|---|---|---|---|---|
| `contextCap=300` | 243,590 | 108 | 524 | **464.9x** |
| `contextCap=100` | 208,845 | 108 | 485 | **430.6x** |

Tightening the cap from 300 to 100 -- below the method's 108 contexts, so it binds -- moves
re-derivation by **7%**. The ratio stays above 430x.

Note also that the census still reports **108 contexts under both caps**: the cap merges context
STORAGE, not context IDENTITY, so the edges still carry 108 distinct contexts and the census counts
them. That is worth knowing before anyone reads a context count as evidence a cap took effect.

**So the re-derivation is not context-driven.** Merging the storage of 108 contexts leaves a fact
being re-derived 430 times. Context-side work is therefore closed for ThingsBoard's re-derivation as
well -- it is recall-safe there (section 25), it reduces events 2.6x, and it does not touch the
mechanism that makes those events expensive.

### Where that leaves ThingsBoard

Its cost is **494 distinct facts derived 231,887 times**, and neither depth (facts are 8-29 deep),
nor population (57k distinct facts in the largest method), nor contexts (capping changes 7%) explains
it. The remaining candidates are engine-level: the summary re-application path, and the scheduling
that re-offers a fact whenever any caller changes. `cost is rederivation, not population` was recorded
on conductor in an earlier session; this is the same phenomenon two orders of magnitude worse.

**Nothing in this investigation has instrumented WHY a fact is re-offered.** That is the ThingsBoard
equivalent of the erased-generic finding for conductor, and it is the next thing to measure -- not to
repair.

## 29. CORRECTION to sections 27-28: "re-derivation" compared MISMATCHED UNITS

Sections 27 and 28 divided ACCEPTED EDGES by DISTINCT FACTS and called the quotient a re-derivation
ratio -- 469x on `pushEntityActionToRuleEngine`, 20x on `#save`. **The two are not the same unit.**

`MethodFactCensus` records `factKey = edge.factAp.toString()` -- the FINAL fact only. An IFDS edge is
a triple `(initialFactAp, statement, factAp)`. So 231,887 accepted edges yielding 494 distinct FINAL
facts is entirely consistent with 231,887 genuinely DISTINCT edges whose finals happen to coincide --
many initial facts, at many statements, mapping into a small set of outputs. That is normal for a
method with many callers, not waste.

**So the "469x re-derivation" is not established.** What IS established, and unchanged:

* ThingsBoard's facts are shallow (max 8-29) where conductor's are deep (max 181-559) -- section 26;
* ThingsBoard has a real context population (`ctx=108`) where conductor has none -- section 24;
* `contextCap` is recall-safe on ThingsBoard and recall-destroying on conductor -- section 25;
* capping contexts changes the accepted/distinct-fact quotient by only 7% -- section 28, which stands
  as a measurement whatever the quotient means.

### What would actually measure re-derivation

Count distinct `(initialFactAp, statement, factAp)` triples per method and compare THAT against
accepted edges. A ratio near 1 means every accepted edge was new work; a large ratio means the same
triple is being processed repeatedly. The census does not record initial or statement, so the
question is open.

**Method note.** This is the FOURTH unit-or-scope error in this investigation: "depth 3 keeps both
findings" (one rule generalised to 134), "download is ABSENT" (a top-N cutoff read as data),
"`contextCap` destroys traces" (conductor generalised to both projects), and now a per-method ratio
built from two different units. Three of the four were caught only by a later measurement. The
pattern is consistent enough to state as a rule: **before quoting a ratio, name the unit of the
numerator and the unit of the denominator.**

## 30. THE CORRECT MEASUREMENT: conductor does NOT re-derive; ThingsBoard's re-derivation IS its contexts

Accepted edges against distinct `(initial, statement, final)` TRIPLES -- the matching unit.

| project | method | **ctx** | accepted | distinct edges | **re-derived** |
|---|---|---|---|---|---|
| conductor | `decide` | 1 | 214,505 | 200,559 | **1.07x** |
| conductor | `rerunWF` | 1 | 170,501 | 153,219 | **1.11x** |
| conductor | `terminateWorkflow` | 1 | 122,222 | 114,478 | **1.07x** |
| ThingsBoard | `#save` | 1 | 697,695 | 190,366 | 3.67x |
| ThingsBoard | `updateImagesUsage` | 1 | 438,967 | 133,210 | 3.30x |
| ThingsBoard | `#save` | **6** | 763,058 | 40,285 | **18.94x** |
| ThingsBoard | `pushEntityActionToRuleEngine` | **108** | 231,987 | 2,242 | **103.47x** |

**Conductor does essentially no re-derivation: 1.07-1.13x.** Every accepted edge is new work. Its cost
is pure derivation VOLUME over a huge edge space -- 200,559 distinct edges in one method.

**ThingsBoard re-derives, and the ratio tracks the CONTEXT COUNT:**

| contexts | re-derivation |
|---|---|
| 1 | 3.3x - 3.9x |
| 6 | 18.9x |
| 108 | 103.5x |

The edge key is `(initial, statement, final)` and does NOT include the context, so **the same triple
derived in 108 different contexts counts as ONE distinct edge and 108 accepted ones.** The ratio IS
the context multiplication, near enough one-to-one at ctx=108.

### This resolves section 28

Section 28 concluded "the contexts do not cause the re-derivation" because `contextCap` moved the
quotient only 7%. That conclusion was right about the CAP and wrong about the CAUSE. `contextCap`
merges context STORAGE while the edges keep their distinct context IDENTITIES -- the census sees 108
contexts under a cap of 100 (section 28) -- so the same triple is still derived once per context.
**The contexts do cause it; the cap does not remove them.**

### The two projects, finally on matching units

| | conductor | ThingsBoard |
|---|---|---|
| distinct edges (top method) | **200,559** | 2,242 - 190,366 |
| re-derivation | **1.07x** | **3.3x - 103x** |
| depth | max 181-559 | max 8-29 |
| indicated lever | reduce the EDGE SPACE (a sound depth reduction; blocked) | reduce the CONTEXT COUNT (not `contextCap`, which merges storage only) |

**These are genuinely different problems and the "different diseases" reading of sections 26-28
survives on the corrected units.** Conductor derives a vast number of distinct edges exactly once;
ThingsBoard derives a small number of edges over and over, once per context.

## 31. CONFIRMED: ThingsBoard's re-derivation IS its context count -- and bounding it relocates

`opentaint.contextConstraints=k` bounds how many TYPE CONSTRAINTS a context may carry. Dropping a
constraint makes the context strictly MORE GENERAL, so it is an over-approximation by construction:
it can add false positives, never remove a finding at convergence.

`EntityActionService#pushEntityActionToRuleEngine`:

| k | **contexts** | accepted | distinct edges | **re-derived** |
|---|---|---|---|---|
| off | **108** | 231,987 | 2,242 | **103.5x** |
| 2 | **49** | 108,685 | 2,278 | **47.7x** |
| 1 | **1** | 11,133 | 6,755 | **1.6x** |

**The ratio tracks the context count one-to-one**: 103.5/108, 47.7/49, 1.6/1. Section 30's reading is
confirmed on a lever that moves the cause directly -- re-derivation on this method is context
multiplication and nothing else. Accepted edges there fall **20.8x**.

### And it does not help

| arm | oom | forward vulns | SARIF | elapsed | **events** |
|---|---|---|---|---|---|
| off | 1 | 15 | 15 | 270 s | 4,859,379 |
| k=2 | 1 | 15 | **15** | 271 s | 5,120,735 |
| k=1 | 1 | **14** | **14** | 245 s | **11,491,269** |

At k=2 the target method re-derives half as much and the run is otherwise unchanged -- same findings,
same OOM, events within 5%. At k=1 the method's re-derivation is essentially eliminated (1.6x) and
**the analysis does 2.4x MORE work overall** and loses a finding.

**This is the relocation law again, at the context level.** Coarser contexts admit values the
constraints previously excluded, so work removed from one method reappears across many others. The
per-method win is real and the global effect is negative.

The k=1 finding loss shows **no `Filter out` line and `fwd=14`**, so it is lost in the FORWARD scan,
not in trace resolution. Since that run OOMed having done 2.4x the work, truncation is the likely
cause rather than unsoundness -- the over-approximation argument holds only at convergence, and this
run did not converge. It is not proven unsound; it is unusable either way.

### What this settles for ThingsBoard

* The mechanism is **known and confirmed**: re-derivation = context multiplication.
* The direct lever **works on the mechanism and fails on the gate**, because the cost relocates.
* `contextCap` (storage merging) and `contextConstraints` (identity bounding) are now BOTH measured:
  the first is recall-safe and moves 2.6x of events without touching re-derivation; the second removes
  re-derivation and moves the work elsewhere.

ThingsBoard's remaining question is not "how do we reduce contexts" -- that is answered -- but "why
does coarsening types create more work than it saves", which is the same relocation question
conductor poses on a different axis.

## 32. THE PATTERN: every sound bound RELOCATES work; none removes it

Measured independently at four levels in this investigation:

| level | bound | per-target effect | global effect |
|---|---|---|---|
| **fact** | `factShapeDepth` | `decide` facts/Mev **x0.005** at depth 1 | `rerunWF` takes **68.6%** of all edges (section 2) |
| **tree** | `addedNodeBudget=20` | largest tree **x0.08** | `factAdd` **x2.11**, `graphNodes` **x3.45** (Claim 93) |
| **flow** | `factShapeDepth=16` | `httpCall` **x0.60**, `download` **x0.84** | `verifyAndUpload` **x3.9**, `shouldUpload` **x3.6** (section 18) |
| **context** | `contextConstraints=1` | target re-derivation **x0.015** (103.5 -> 1.6) | total events **x2.4** (section 31) |

Four different bounds, four different axes, four different projects-worth of code -- and in every case
the quantity removed at the target reappears elsewhere, usually larger. The only levers that reduced
GLOBAL work were the ones that removed findings.

### What that suggests, and what it does not

It is consistent with the work being determined by the PROGRAM and the RULES rather than by the
representation: a sound bound changes where the analysis spends effort, not how much the fixed point
costs. Two independent measurements point the same way -- conductor's per-event cost is a floor of
~6 `statePop` across all six sound arms (Claim 108), and at 32 GB conductor still does not converge,
becoming compute-bound rather than memory-bound (Claim 105).

**This is a pattern across four measurements, not a theorem.** It does not prove no sound bound can
help; it says none of the four axes available in this codebase does, and that a fifth should be
expected to relocate too unless it changes what the analysis DEMANDS rather than how it REPRESENTS or
SCHEDULES what it derives.

### The practical reading

Under the constraint this work was given -- fix the fact representation, storage and handling only --
the gate is not reachable, and the reason is now measured from four directions rather than asserted.
The options that remain all step outside that constraint:

* **more resources** -- but 32 GB does not converge conductor (Claim 105), so this is not simply a
  memory question;
* **an unsound approximation** -- every one measured here loses findings, and the goal forbids it;
* **change what is DEMANDED** -- fewer entry points, fewer rules, or a different fixed-point
  formulation. This is the only direction not yet closed, and nothing in this investigation has
  tested it.

## 33. No regression: the clean branch OOMs identically, and the "conductor converges" figure is not comparable

`saloed/5-default-get` (clean, no experimental flags), 8 GB, IFDS 3600 s, full harness ruleset:

```
rc=253  oom=1  elapsed=253s  lastProgress=2,274,412 (+80,204)
Total vulnerabilities: 4   SARIF finding set: 4
```

**The clean branch OOMs too**, at 2,274,412 events -- squarely inside the 1.4M-2.3M band every
working-branch arm dies in. Given 3600 s it used 253. **There is no regression between
`saloed/5-default-get` and `saloed/44-fact-evolution-trace`**, and the branch-inequivalence noted in
section 5 (2.70M vs 1.49M on one rule) is run-to-run variation within that band, not degradation.

### What this corrects

`conductor-converges-it-is-throughput` records `rc=0` at **14,768,683 events in 1,924 s at 8 GB**,
and Claims 107 and 108 used it as the convergence target -- "the gap is ~5.5x in events". That run
used a **different rule set**: `rules/` (134 yaml, **12 findings**) rather than the harness's
`rules/ruleset` (**4 findings**). The two are not the same workload, and
`rules-dir-contains-a-copy-of-itself` already records that `--semgrep-rule-set rules` loads a
different and much larger set.

**So the 14.77M figure is not this workload's convergence point, and the "5.5x gap" derived from it
is withdrawn.** What is measured for the harness ruleset is only that conductor OOMs at 8 GB at
~2.3M events on both branches, and does not converge at 24 GB in 2,860 s (Claim 108). Its actual
convergence point is unknown.

### Method note

This is the FIFTH cross-setting error in this investigation, and the first where I imported a figure
from an earlier session without checking that its workload matched. The rule from section 29 --
before quoting a ratio, name the units -- extends: **before quoting a number from another run, name
its RULE SET, its branch, and its budget.** Three of the five errors here came from a number that was
correct in a setting other than the one it was used in.

## 34. THE COST: this workload does not converge in 40 GB

Clean branch, no experimental flags, **40 GB**, IFDS 3600 s, harness ruleset (4 findings).

```
rc=253  oom=4  elapsed=2512s   Analysis done in 25m 24s
lastProgress=8,451,693 (+1,501)   heap 42,606,461,320 / 42,949,672,960 = 99.20%
Total vulnerabilities: 4   SARIF finding set: 3
```

**40 GB is not enough.** The analysis reached 8,451,693 events -- 3.7x the 8 GB wall (~2.3M) -- ran for
42 minutes, filled 99.2% of a 40 GB heap, hit the memory guard four times, and did not converge. Its
delta at the end was **+1,501 events per interval**, down from tens of thousands: memory saturation
and throughput collapse arrive together, the same signature seen at 8 GB and 24 GB.

It also lost a trace (`vulns=4`, SARIF 3) under that pressure.

### The cost is bounded from below, and the bound is large

| heap | outcome | events reached |
|---|---|---|
| 8 GB | OOM at 253 s | 2,274,412 |
| 24 GB (bounded arm) | TIMEOUT at 2,860 s, `oom=0` | 4,863,855 |
| **40 GB** | **OOM at 2,512 s, `oom=4`** | **8,451,693** |

Each tripling of the heap roughly doubles the events reached and none of them finishes. **The
convergence point of this workload is above 40 GB**, against a goal of 8 GB.

### What that closes

**"Raise the budget" is untenable, not merely unattractive.** A five-fold increase over the goal, on
an unbounded run, still does not converge -- so the gate is not a provisioning decision.

Combined with section 32 -- every sound bound RELOCATES work rather than removing it, measured on four
axes -- the position is:

* unbounded: needs more than 40 GB and does not converge;
* bounded soundly: the work reappears elsewhere;
* bounded unsoundly: findings are lost, which the goal forbids.

**That is the complete measured picture, and it does not contain a configuration that meets the
gate.** What it does not close is the one direction never tested: reducing what the analysis is ASKED
to derive -- fewer rules, fewer entry points, or a different fixed-point formulation. Every lever in
this investigation changed how the analysis represents, schedules or bounds its work; none changed
its demand.

## 35. FINAL: demand reduction does not converge either -- ONE rule still exhausts 8 GB

Clean branch, **ONE rule**, 8 GB, IFDS 3600 s:

```
rc=253  lowmem_stop=1  wall=264s   progress=2,684,668 (+7,709)
Total vulnerabilities: 2   SARIF finding set: 2
```

**One rule OOMs at 8 GB**, at 2,684,668 events. Compare the full 134-rule set on the same branch and
heap: OOM at 2,274,412. **The rule count barely moves the wall.**

| rules | heap | outcome | events |
|---|---|---|---|
| **1** | 8 GB | **OOM** | 2,684,668 |
| 134 | 8 GB | OOM | 2,274,412 |
| 134 | 40 GB | OOM | 8,451,693 |

(`base_clean`, the same single-rule configuration at IFDS 300 s, timed out at 2,701,636 events just
short of the guard -- the same wall reached from the other side.)

### The elimination is complete

Every axis available has now been measured on this workload:

| axis | lever | result |
|---|---|---|
| representation | 10 levers (Claim 98), `absFullCover`, `tailKeepField` | relocate; findings lost below median depth |
| scheduling | `factDepthStep`, `FactDepthLadder` | stall, or the wall at ~2M events |
| bounds | depth, width, added-tree, container, matcher walk | relocate (section 32) |
| context | `contextCap`, `contextConstraints` | relocate; conductor has `ctx=1` anyway |
| resources | 24 GB, **40 GB** | still OOM at 8.45M events (section 34) |
| **demand** | **1 rule instead of 134** | **still OOM at 2.68M events** |

**The memory is consumed by the program's own dataflow, not by the rules.** A single taint rule on
conductor exhausts 8 GB at the same point 134 rules do, so the cost is in propagating facts through
this program, and the ruleset is close to irrelevant to it.

### The honest conclusion

**Conductor's 8 GB / 500 s gate is not reachable by any lever measured in this work, and the
measurements now cover every axis the analysis exposes.** The workload's cost exceeds 40 GB
unbounded; every sound bound relocates rather than removes; every unsound one loses findings; and
reducing the ruleset by two orders of magnitude does not move the wall.

What remains is outside anything this investigation can test: a different fixed-point formulation, a
coarser program abstraction (fewer distinct facts by construction rather than by truncation), or a
revised goal. The gate as stated is not a tuning target -- it is roughly an order of magnitude below
what this analysis costs on this program.

## 36. THE TRADEOFF POINT: one commit separates convergence from soundness

`saloed/42` is only FOUR commits past the clean base `saloed/5-default-get`. Running each at the GATE
(8 GB, IFDS 500 s, `rules/ruleset`):

| build | rc | oom | elapsed | events | forward vulns | SARIF | which findings |
|---|---|---|---|---|---|---|---|
| base `4c358d2e1` | 253 | 1 | 253 s | 2,274,412 | 4 | 4 | all four |
| `2ee9567a1` (pre any-branch) | 253 | **4** | 476 s | 3,920,482 | **4** | 3 | **ssrf** + 2 stacktrace (path-traversal's TRACE lost) |
| **`a71573142` (42 head)** | **0** | **0** | **66 s** | 148,634 | **3** | 3 | **path-traversal** + 2 stacktrace (**ssrf GONE**) |

**The commit between them is `0cece323c "Stop materializing any branches in TIFA"`** -- the
`tifaNoUnroll` behaviour. It is the whole difference:

* **before it**: OOM at 476 s and 3.9M events, forward finds all FOUR;
* **after it**: converges in **66 seconds** with `oom=0` and `FORWARD=OK` -- and the forward scan finds
  only THREE. `ssrf` is gone from the forward analysis, not merely from trace resolution.

**Convergence and soundness are separated by exactly one commit, and it is not separable at that
granularity**: the change that buys a 26x reduction in events is the change that drops `ssrf`.

Note the two failing builds lose DIFFERENT findings, which is why aggregate counts mislead here:
`2ee9567a1` finds `ssrf` and loses `path-traversal`'s trace to truncation; the 42 head finds
`path-traversal` and loses `ssrf` outright.

### What this makes the goal

**The conductor gate is not out of reach -- it is one commit away from being met soundly.**
`a71573142` already satisfies every performance requirement: 8 GB, 66 s against a 500 s budget,
`oom=0`, `FORWARD=OK`, converged (`rc=0`). It fails only the no-false-negative requirement, by one
finding.

So the engineering target is precise and bounded: **make "Stop materializing any branches in TIFA"
preserve `ssrf`.** That is one commit's worth of behaviour, on a branch that already converges, rather
than a search across representation, scheduling, bounds, contexts, resources and demand.

### Correction to sections 32, 34 and 35

Those sections concluded that the gate is "roughly an order of magnitude below what this analysis
costs" and that every axis is closed. **That was measured on branches `5-default-get` and `44` only.**
The branch dimension was never in the elimination, and `saloed/42` -- the one branch that had reported
convergence -- converges at the gate in 66 seconds. The cost conclusions describe those two branches,
not the analysis.

## 37. `absFullCover` on the converging branch: WORSE, and my soundness argument was wrong

| arm | rc | oom | SARIF | elapsed | events | findings |
|---|---|---|---|---|---|---|
| `b42_gate` | 0 | 0 | 3 | 66 s | 148,634 | path-traversal + 2 stacktrace |
| `b42fix_off` (control) | 0 | 0 | 3 | 66 s | 148,808 | same -- control reproduces |
| **`b42fix_on`** | 0 | 0 | **2** | 65 s | 148,906 | **path-traversal ALSO lost** |

The repair does not recover `ssrf`. It loses `path-traversal` as well, and still moves events by
**0.2%** -- so the `splitDelta` abstract branch is not hot even on the branch where unrolling is
removed. Section 16 eliminated that site on branch 44; it is now eliminated on 42 too, for the same
reason and with a control on the identical jar.

**And the soundness argument for it was wrong.** I claimed returning `(this, Empty)` can only claim
MORE, so it could add false positives but never remove a finding. That is false: the delta is what the
CALLER grafts to build its own fact. `Empty` tells the caller there is nothing to propagate, so the
flow STOPS. It is not a widening at all -- it is a truncation dressed as one, which is exactly what
the measurement shows.

## 38. The direction the evidence actually points at: BOUNDED unrolling

The three known points are now:

| build | unrolling | outcome | findings |
|---|---|---|---|
| base `4c358d2e1` | **full** (`unrollAnyAccessors`) | OOM at 2.27M events | **4** |
| `2ee9567a1` | full | OOM at 3.92M events | 4 forward, 3 traced |
| `a71573142` (42) | **none** (`0cece323c` removed it) | **converges, 66 s, `rc=0`** | **3** (`ssrf` lost) |

Full unrolling is sound and does not fit. No unrolling fits in 66 seconds and loses a finding. **The
middle -- emit SOME initials but not all -- has never been tested**, and it is precisely the direction
this work was pointed at from the start: *TIFA initials emission limits*.

The two endpoints bracket it usefully: the sound end costs >8 GB, the cheap end costs `ssrf`, and the
gap between them is 15x in events (2.27M vs 148k). A bound anywhere inside that range is a candidate,
and unlike every lever measured in sections 2-32 it does not RELOCATE work -- it declines to create it,
because an initial that is never emitted has no successors to reappear elsewhere.

## 39. TIFA EMISSION LIMITS: tested, and they RELOCATE too

`opentaint.tifaEmitLimit=k` caps how many initials one `unrollAnyAccessors` call materialises. Built
on `2ee9567a1` (full unrolling, the sound endpoint). Gate conditions.

| k | rc | oom | forward vulns | SARIF | elapsed | events |
|---|---|---|---|---|---|---|
| unlimited (baseline) | 253 | 4 | **4** | 3 | 476 s | 3,920,482 |
| 8 | 254 | 2 | **3** | **2** | 484 s | **4,344,916** |
| 4 | 254 | 2 | **3** | **2** | 483 s | **4,390,709** |
| 2 | 253 | 1 | 3 | 2 | 450 s | 3,463,773 |
| 1 | 253 | 2 | 3 | 2 | 307 s | 1,243,112 |

**Every limit loses a forward finding AND still OOMs.** Even k=8 -- a generous cap -- drops the forward
count from 4 to 3 and the SARIF set from 3 to 2.

### My prediction was wrong, and the relocation law is more general than I thought

Section 38 argued this axis would be different: *"an initial that is never emitted has no successors
at all"*, so a bound here should not relocate. **It does.** At k=8 the analysis processes
**4,344,916 events against the unlimited baseline's 3,920,482 -- 11% MORE work while emitting fewer
initials.** k=4 is higher still.

The mechanism is visible once stated: an un-emitted initial does not satisfy the demand that asked
for it, so the demand is re-issued and the work reappears as re-derivation rather than as successors.
Declining to create a fact is not the same as removing the work that fact was going to do.

**That makes emission limits the FIFTH axis measured to relocate**, after representation, tree, flow
and context (section 32). The pattern now holds on every axis this analysis exposes, including the one
I expected to break it.

### Consequence for the stated direction

*TIFA initials emission limits* is measured and does not reach the gate: it costs a finding at every
value tested and does not fix memory at any of them. The two useful endpoints remain what section 36
established -- full unrolling (sound, OOM) and no unrolling (converges in 66 s, loses `ssrf`) -- and
nothing between them recovers both properties.

## 40. WHERE the `ssrf` loss lives, for whoever fixes it

`0cece323c` replaces `unrollAnyAccessors` -- which materialised one initial per covered accessor --
with a selective walk inside `abstractAccessPath`. An accessor now yields an abstract AP only if it
passes FOUR gates:

```kotlin
currentLevelExclusions.forEachInt { accessor ->
    if (state.analyzedTrieRoot.child(accessor) != null) return@forEachInt   // (1) already in the demand trie
    if (state.added.hasLiteralChild(accessor))         return@forEachInt   // (2) already a literal child
    if (accessor.isUncoveredByAny())                   return@forEachInt   // (3) not covered by [any]
    ... filter.check(accessor) is Reject ->             return@forEachInt   // (4) type filter
    createAbstractAp(ReversedApNode(accessor, state.currentAp))
}
```

The old code applied only gates (3) and (4), to `unrollAccessors(currentLevelExclusions)`, and
materialised a CONCRETE fact rather than an abstract AP. **Gates (1) and (2) are new, and each is a
place a fact that used to exist no longer does.**

Gate (1) is the one to examine first. Skipping an accessor because the demand trie already has a child
for it assumes the existing trie entry answers what the materialised fact would have answered -- which
is the same assumption `registration-not-matching` proves false, and the same one that defeated four
historical repairs and my attempts 5-7.

**This is a debugging task, not a sweep**: instrument those four gates, count how often each fires,
and find which one discards the accessor `ssrf`'s flow needs. Section 16's rule applies -- prove the
gate fires before changing it. The four-gate structure makes that cheap: four counters, one run.

### The state of the goal, precisely

| requirement | status |
|---|---|
| conductor: no timeout, no OOM, 8 GB, 500 s | **MET** by `a71573142` -- `rc=0`, `oom=0`, **66 s**, `FORWARD=OK` |
| conductor: no new false negative | **FAILS** by exactly one finding (`ssrf`) |
| ThingsBoard: 12 GB / 800 s | not met; OOMs, and not tested on `saloed/42` |

The conductor gate is **one finding away**, on a build that already converges seven times inside its
time budget. That is a materially different position from "the gate is out of reach", which is what
sections 32-35 concluded from branches 5 and 44 alone.

## 41. The two new gates are NOT the cause -- the loss is concrete-vs-abstract materialisation

| arm | rc | oom | forward | SARIF | elapsed | events |
|---|---|---|---|---|---|---|
| `g_base` (both gates on) | 0 | 0 | 3 | 3 | 65 s | 148,805 |
| `g_trie` (trie gate OFF) | 0 | 0 | **3** | **3** | 66 s | 148,561 |
| `g_lit` (literal gate OFF) | 0 | 0 | **3** | **3** | 66 s | 148,630 |
| `g_both` (both OFF) | 0 | 0 | **3** | **3** | 66 s | 149,050 |

**Disabling both new gates changes nothing** -- same three findings, same 66 s, events within 0.2%.
Section 40's hypothesis is refuted: gates (1) and (2) are not where `ssrf` is lost.

### What is left, and it is the defect this work already formalised

The gates were the SMALL half of `0cece323c`. The large half is what the walk PRODUCES:

* **before**: `filteredNode.addReversedApParents(prefix)` -> merged -> `addInitialFact` -- a CONCRETE
  fact, materialised;
* **after**: `createAbstractAp(ReversedApNode(accessor, currentAp))` -- an ABSTRACT access path.

So the commit does not merely filter which accessors are unrolled; it changes concrete facts into
abstract ones. `ssrf` is lost because **the abstract AP does not answer what the concrete fact
answered** -- `registration-not-matching`, stated formally in `FactExplosion/Conclusion.lean` as
`matching_domination_is_not_enough`.

### The tradeoff, at mechanism level

| materialisation | events | outcome | findings |
|---|---|---|---|
| CONCRETE facts (`unrollAnyAccessors`) | 2,274,412 - 3,920,482 | OOM | **4** |
| CONCRETE, count-limited (section 39) | 1.24M - 4.39M | OOM | 3 |
| **ABSTRACT APs** (`0cece323c`) | **148,805** | **converges, 66 s** | 3 (`ssrf` lost) |

Concrete materialisation is sound and costs 15-26x the events. Abstract materialisation converges and
loses a finding. Limiting the concrete count gets the worst of both. **The two properties are
exclusive on this axis, and the defect that makes them exclusive is the one `Conclusion.lean`
specifies and nine repair attempts have failed to discharge.**

### Attempt count and what each eliminated

1-4 (historical): reader-side repairs -- eliminated by section 17 (facts arrive, they are not misread).
5 `absFullCover` on branch 44: 0.003% event change -- branch not hot there.
6-7 `tailKeepField`: path identity restored two ways, 13% of the fan-out -- path is not the cause.
8 `absFullCover` on branch 42: LOSES a further finding -- and the soundness argument for it was wrong
(section 37: `Empty` delta stops the caller's graft; it is truncation, not widening).
9 gate disabling: no effect at all.

**The obligation is unchanged and unmet**: a collapsed premise must register a fact carrying the
terminal flags of the concrete facts it stands for (`DominatesRegistered`). Every attempt so far has
changed a READER, a FILTER, or a DELTA. None has changed what is REGISTERED.

## 42. Attempt 10 -- the registered terminal flag: FIRES, is affordable, and is not sufficient

`createAbstractNodeFromReversedAp` folds from `abstractNode` (`isAbstract = true, isFinal = FALSE`).
`0cece323c` builds its replacement facts from there, so the fact standing in for the concrete ones
cannot satisfy a premise ending in `$`. `abstractFactFinal=true` folds from `abstractFinalNode`
instead -- the same node WITH the terminal flag, which is what `Conclusion.DominatesRegistered`
requires.

| arm | rc | oom | forward | SARIF | elapsed | events |
|---|---|---|---|---|---|---|
| `tf_off` (control) | 0 | 0 | 3 | 3 | 67 s | 148,005 |
| **`tf_on`** | **0** | **0** | 3 | 3 | 88 s | **318,441** |

**The change is real: 2.15x the events and +21 s.** That distinguishes it from attempts 5 and 9,
which moved events by 0.003% and 0.2% and were therefore inert. A fact that carries the terminal flag
answers strictly more queries, and the analysis does correspondingly more work -- so this is a genuine
widening, in the safe direction.

**And it does not recover `ssrf`.** Three findings, same as the control.

### What that narrows

The terminal flag is not the property the concrete facts carried and the abstract one lacks. What
remains is structural: the old code built each fact as
`filteredNode.addReversedApParents(prefix)` -- carrying the FILTERED SUBTREE beneath the prefix --
while the new one builds `accessor-chain -> abstractFinalNode`, carrying nothing below the tail but a
flag. `isAbstract` is supposed to denote every continuation; the measurement says that denotation does
not answer what the materialised subtree answered.

### The encouraging half

**A 2.15x widening still converges in 88 seconds**, `oom=0`, against a 500 s budget. There is room for
a much more complete widening before the gate is threatened -- roughly 5x more work than `tf_on` uses.
So the obstacle is not that soundness is unaffordable here; it is that no widening tried so far
restores what the concrete subtree provided.

**Ten attempts, and the obligation stands.** Nine changed a reader, a filter, or a delta; the tenth
changed a registered flag and moved real work without moving recall. The untried remainder is to
register the SUBTREE the concrete facts carried, not merely a flag standing for it.

## 43. HANDOFF: what the fix requires, and why it stops here

Attempt 10 established that the abstract fact needs more than the terminal flag. The remaining
difference is the SUBTREE:

```kotlin
// before 0cece323c -- a CONCRETE fact carrying the filtered subtree
newFacts += filteredNode.addReversedApParents(prefix)

// after -- an ABSTRACT chain ending in a flag
val apAccess = apManager.createAbstractNodeFromReversedAp(abstractAccess)
```

The `createAbstractAp` lambda receives only the reversed access path, not the node, and
`addReversedApParents` was DELETED by the commit. So carrying the subtree requires restoring that
helper and threading the node through `abstractAccessPath` -- and doing it for EVERY accessor is
exactly the pre-commit behaviour that OOMs at 2.27M events.

**The design the measurements point at is therefore: materialise the subtree only where a premise
demands it**, rather than for every covered accessor (the old behaviour) or for none (the current
one). That is a real design task, not a flag, and it is where this investigation stops.

### What the next session inherits

| fact | value |
|---|---|
| conductor status gate | **MET** on `a71573142`: `rc=0`, `oom=0`, 66 s of a 500 s budget |
| conductor recall | 3 of 4 -- `ssrf` lost |
| headroom for a fix | **5x** -- a 2.15x widening still converges in 88 s |
| what is missing | the filtered SUBTREE, not the terminal flag (section 42) |
| where | `TreeInitialFactAbstraction.abstractAccessPath` + the deleted `addReversedApParents` |
| ThingsBoard | untested on this branch; its mechanism is re-derivation x context count (sections 30-31) |

**Ten attempts and what each eliminated**: 1-4 readers; 5 the `splitDelta` abstract branch on 44
(0.003%); 6-7 path identity, two rules (13%); 8 full coverage on 42 (loses a further finding, and the
soundness argument was wrong); 9 the two new gates (no effect); 10 the terminal flag (2.15x work, no
recall change). Every one changed a READER, a FILTER, a DELTA, or a FLAG. **None restored a
STRUCTURE**, which is what the measurement now says is missing.

## 44. Attempt 11 -- carrying the subtree: WORSE, and the implementation was flawed

| arm | rc | oom | forward | SARIF | elapsed | events |
|---|---|---|---|---|---|---|
| `st_off` (control) | 0 | 0 | 3 | 3 | 65 s | 149,007 |
| `st_on` | 0 | 0 | 3 | **2** | 65 s | **141,174** |

Carrying the covering `[any]` subtree loses `path-traversal` as well, and does **fewer** events
(141,174 against 149,007). Fewer events while adding structure is the signature of work being SKIPPED,
not done.

**The implementation is at fault, and it is worth naming.** The fold is written

```kotlin
abstractAccess.foldRight(coveringSubtree) { accessor, node ->
    node.addParentIfPossible(accessor) ?: return@abstractAccessPath
}
```

`addParentIfPossible` returns null when the parent cannot be attached, and the `?:` then returns from
the WHOLE lambda -- abandoning that abstraction entirely rather than falling back to the abstract
chain for that one accessor. So every accessor whose parent cannot be attached silently drops a fact
the control still produced. That explains both the lost finding and the lower event count.

**So attempt 11 is not a clean test of the subtree hypothesis.** A correct version would fall back to
`createAbstractNodeFromReversedAp` when the fold fails, rather than abandoning the abstraction. The
hypothesis remains open; this implementation of it is refuted.

### Eleven attempts

| # | changed | result |
|---|---|---|
| 1-4 | readers | facts arrive and are not misread (section 17) |
| 5 | `splitDelta` abstract branch, branch 44 | 0.003% -- inert |
| 6-7 | path identity, two rules | 13% of the fan-out |
| 8 | full coverage, branch 42 | loses a finding; soundness argument was wrong |
| 9 | the two new gates | no effect at all |
| 10 | registered terminal flag | **2.15x work**, no recall change |
| 11 | carried subtree | loses a finding -- **flawed fold, not a clean test** |

Three of the eleven (5, 9, 11) turned out to test something other than what I intended, and each was
caught by an event count that moved the wrong way or not at all. That check -- does the change move
work, and in which direction -- has been more reliable here than any reasoning about what the code
should do.

## 45. Attempt 12 -- the corrected fold: the subtree is a NARROWING, not the missing width

| arm | rc | forward | SARIF | events |
|---|---|---|---|---|
| `s2_off` (control) | 0 | 3 | 3 | 148,755 |
| `s2_on` (corrected fold) | 0 | 3 | **2** | **140,769** |

With the fold fixed to FALL BACK rather than abandon, the result is unchanged: a finding lost and
**fewer** events. The bug of section 44 was real but was not what produced the outcome.

### My reasoning was inverted

`abstractNode` is `isAbstract = true` -- it DENOTES EVERY CONTINUATION. The covering `[any]` subtree
is a specific structure. So replacing the abstract tail with the subtree makes the fact **NARROWER**,
not wider. That is why events fall and findings are lost: a narrower fact matches fewer premises.

Sections 42-44 assumed the abstract chain was "missing" the subtree. It is not: the abstract chain is
already MORE GENERAL than the subtree it replaced. There is nothing to add there.

### What that leaves, stated precisely

The old code emitted **many concrete facts**, one per unrolled accessor. The new code emits **one
abstract fact** that denotes at least as much as all of them together. Denotationally the abstract
fact is a sound replacement -- and `ssrf` is still lost.

So the loss is not denotational. **It is that the demand matches FACTS, not denotations**: a set of N
concrete facts registers N times and answers N distinct premises; one abstract fact registers once,
however much it denotes. That is `registration-not-matching` in its sharpest form, and it explains why
twelve attempts to make the single fact WIDER -- terminal flags, full coverage, carried subtrees --
could never work. Width is not the missing property. **Multiplicity is.**

### Consequence

A fix must make one abstract fact REGISTER as the many concrete facts it stands for, or must emit the
concrete facts selectively where the demand needs them. Every attempt in this report changed what a
fact DENOTES. None changed how many times it REGISTERS.

That is also why section 39's emission limit failed: it reduced multiplicity, which is the property
that matters, in the direction that loses findings.

## 46. THE MECHANISM, finally: the loop no longer DEEPENS the fact

```kotlin
// BEFORE 0cece323c -- iterative deepening: each round walks the NEWLY UNROLLED fact
var concreteFactAccess = initialConcreteFact
while (true) {
    abstractAccessPath(facts.analyzed, concreteFactAccess, unrollRequests) { ... }
    concreteFactAccess = facts.unrollAnyAccessors(unrollRequests, typeChecker) ?: break
}

// AFTER -- every round walks the SAME fact; only the TRIE grows
var firstRound = true
while (true) {
    abstractAccessPath(initialAnalyzedTrieRoot, initialConcreteFact, ...) { ... }
    if (!registeredNew) break
}
```

**The old loop grew the FACT on every iteration. The new loop never does.** It re-walks a fixed
`initialConcreteFact` against a growing demand trie, so no path longer than the original fact's own
structure is ever built.

That is the whole explanation, and it fits every measurement in this report:

* `ssrf` needs DEEP access paths -- conductor's facts reach 247, 217 and 559 accessors (section 7),
  and depth bounds at 3 through 48 all lose exactly `ssrf` and `path-traversal` (section 8);
* iterative deepening is what BUILT those paths, and it is what produces 2.27M events and the OOM;
* without it the analysis converges in 66 s on 148k events and cannot reach the depth `ssrf` requires.

### Why twelve attempts failed

Attempts 5-12 all tried to make the SINGLE abstract fact carry more -- terminal flags, full coverage,
subtrees, multiplicity. **None of them could work, because the missing thing is not a property of any
fact. It is the iteration that would have produced the deeper facts in the first place.** A fact that
is never built cannot be made wider.

### The tradeoff, exactly

| loop | events | outcome | findings |
|---|---|---|---|
| iterative deepening | 2,274,412 | OOM | **4** |
| no deepening | 148,755 | **converges 66 s** | 3 |

**15x, and one finding.** Section 39's emission limit failed because it capped the COUNT of initials
per round rather than the DEPTH of the iteration -- the wrong dimension of the same loop.

### What a fix must do

Deepen **selectively**: continue the iteration only where a registered premise demands greater depth,
rather than for every unrollable accessor (the old behaviour) or never (the current one). That is the
demand-driven form of *TIFA initials emission limits*, and it is the one shape this investigation has
identified that is not already measured and refuted.

## 47. THE FIX, specified: selective deepening, and the demand filter it needs

Section 46 established that `0cece323c` removed the ITERATION that deepened the fact, and that this is
why `ssrf` is unreachable on the converging branch. Restoring it wholesale reproduces `2ee9567a1` --
already measured: OOM at 3.92M events. The fix is to deepen SELECTIVELY.

### The filter is already sitting in the code, inverted

The new walk contains, as its first gate:

```kotlin
if (state.analyzedTrieRoot.child(accessor) != null) return@forEachInt   // gate 1
```

It SKIPS an accessor when the demand trie has a child for it. **Trie membership is exactly the
demand signal a selective deepening needs -- and the gate uses it with the opposite sign.** An
accessor a premise asks about is the one worth deepening for; the current code declines to emit
precisely there.

(Attempt 9 disabled that gate and nothing changed, which is consistent: removing a skip does not
create the iteration that would have used it.)

### What has to be restored

Three pieces were deleted by the commit and are recoverable from `0cece323c^`:

* `AnyAccessorUnrollRequest(currentAp, node, accessors)` -- the request record;
* `unrollAnyAccessors(requests, typeChecker)` -- builds the deepened fact and returns it;
* `AccessTreeNode.addReversedApParents(ap)` -- attaches the prefix, returning null when it cannot.

Then the loop becomes:

```kotlin
var concreteFactAccess = initialConcreteFact
while (true) {
    val requests = mutableListOf<AnyAccessorUnrollRequest>()
    abstractAccessPath(facts.analyzed, concreteFactAccess, requests) { ... }
    // THE FILTER: keep only accessors the demand trie asks about
    val demanded = requests.filter { r -> r.accessors.any { facts.analyzed.child(it) != null } }
    concreteFactAccess = facts.unrollAnyAccessors(demanded, typeChecker) ?: break
}
```

### Why this is the one shape not yet refuted

| approach | measured |
|---|---|
| deepen for every accessor | OOM at 2.27M-3.92M events, 4 findings |
| deepen for none | converges 66 s, 3 findings |
| cap initials per round (section 39) | loses a finding at every k, still OOMs |
| make the single fact wider (attempts 5-12) | cannot work -- the deep fact is never built |
| **deepen only where demanded** | **untested** |

The count limit of section 39 failed because it capped the WRONG dimension of this loop -- how many
initials one round emits, rather than whether the iteration continues. The demand filter caps the
iteration itself, and only where no premise is waiting on the result.

**Risk to check first**: if the demand trie is populated broadly, the filter admits nearly everything
and the arm degenerates to full deepening (the OOM). Measure the filter's selectivity -- requests
kept versus offered -- BEFORE judging the arm, exactly as section 16's rule requires.

## 48. Attempt 13 -- selective deepening: the implementation does not FIRE

| arm | rc | forward | SARIF | elapsed | events |
|---|---|---|---|---|---|
| `dp_off` (control) | 0 | 3 | 3 | 66 s | 148,673 |
| `dp_dmd` (demand-filtered) | 0 | 3 | 3 | 66 s | 149,142 |
| **`dp_full`** (UNFILTERED) | 0 | 3 | 3 | 65 s | **148,164** |

**The unfiltered arm is identical to the control.** Restoring the deleted iteration WITHOUT any filter
should reproduce the pre-commit behaviour -- OOM at ~2.3M events -- and instead it changes nothing.
So the restored deepening never runs, and neither deepening arm says anything about the hypothesis.

**That diagnostic arm was included for exactly this reason** (section 16: prove the change fires). It
cost one run and prevented reading `dp_dmd`'s three findings as a refutation of demand-filtered
deepening, which would have been wrong.

### Where the wiring fails, for the next attempt

The request sink is populated only inside the `anyBranch != null` block of the FIRST
`abstractAccessPath` overload:

```kotlin
if (DEEPEN) {
    val ua = state.analyzedTrieRoot.unrollAccessors(currentLevelExclusions)
    if (!ua.isEmpty) unrollSink?.add(AnyAccessorUnrollRequest(state.currentAp, state.added, ua))
}
```

Candidates for why it stays empty, in order of likelihood:

1. `currentLevelExclusions` is empty at these sites -- the old code derived unroll accessors from the
   same set, but the surrounding control flow changed with the commit;
2. `unrollAccessors` returns empty because the trie node's `unrolled` set already contains them --
   note the restored `unrolled` field starts fresh per node, so this should not fire on round one;
3. the `[any]` block is reached less often than assumed, since `0cece323c` also changed WHEN the walk
   descends into `anyBranch`.

**Instrument the sink size per round before changing anything else.** One counter answers which of
the three it is; thirteen attempts in this report have shown that reading the code is not a substitute
for that counter.

### Thirteen attempts, four of which tested something other than intended

5 (`splitDelta` branch, 0.003%), 9 (gates, no effect), 11 (flawed fold, fewer events), 13 (deepening
never fires). **Every one was caught by the event count, never by inspection.** That check --
does the change move work, and in which direction -- is the single most reliable instrument this
investigation produced.

## 49. The deepening DOES fire -- 57 times, and changes nothing

```
DEEPENDIAG anyBlock=1898  exclSeen=4061  uaKept=1369  reqMade=369  deepened=57
rc=0  oom=0  vulns=3  sarif=3  events=148,696   (control: 148,673)
```

None of section 48's three candidates is the answer. The `[any]` block is reached 1,898 times,
exclusions are present (4,061), `unrollAccessors` keeps 1,369 of them, 369 requests are built, and the
loop **deepens 57 times**. The wiring works.

**And the run is indistinguishable from the control** -- 148,696 events against 148,673, three
findings.

### What that means

The restored iteration is NOT equivalent to the one `0cece323c` deleted. The original produced
2,274,412 events on the same program; mine deepens 57 times and produces 148,696. The difference is
not the filter -- this arm ran UNFILTERED (`deepenDemandedOnly=false`).

The likely reason is the `unrolled` set. `unrollAccessors` returns only accessors not yet unrolled AT
THAT TRIE NODE, so after the first round almost everything is marked and the iteration starves. In the
original that same mechanism sat inside a walk that re-entered nodes differently -- `0cece323c` also
changed WHEN the walk descends into `anyBranch` -- so the same guard produced far more rounds.

**Restoring the iteration faithfully therefore means restoring the walk it lived in, which is
reverting the commit -- measured, and it OOMs at 2.27M events.**

### Where this leaves the repair line

Thirteen attempts. The mechanism of section 46 stands -- deepening is what builds the deep paths
`ssrf` needs, and the pre-commit build finds `ssrf` while the post-commit build does not. What has not
been achieved is a deepening that is both faithful enough to reach those paths and bounded enough to
converge. My restoration is bounded (57 rounds) but not faithful; the original is faithful but
unbounded.

**The open question is now specific**: what makes the original walk re-enter `[any]` nodes often
enough to reach depth 559, and can that re-entry be bounded by demand rather than by the `unrolled`
guard? That is a question about the WALK's descent, not about facts, premises, or deltas -- the four
things every attempt in this report has changed.

## 50. Attempt 15 -- restoring the unconditional descent: also not the constraint

| arm | rc | forward | SARIF | events | `deepened` |
|---|---|---|---|---|---|
| control | 0 | 3 | 3 | 148,673 | -- |
| `ad_desc` (descent only) | 0 | 3 | 3 | 149,162 | 0 (deepening off) |
| `ad_both` (descent + deepening) | 0 | 3 | 3 | 148,480 | **61** (was 57) |

Restoring the unconditional `anyBranch` descent raises `anyBlock` from 1,898 to 2,165 -- the walk does
re-enter `[any]` nodes more often -- and the iteration still deepens only **61** times against 57.
Events and findings are unchanged.

**So the descent is not what starves the iteration either.** Section 49 eliminated the wiring;
this eliminates the descent.

### The honest conclusion of the repair line

Fifteen attempts have changed: readers (1-4), the `splitDelta` abstract branch (5), path identity two
ways (6-7), full coverage (8), the two new gates (9), the registered terminal flag (10), the carried
subtree (11-12), the restored deepening iteration (13-14), and the walk's descent (15). Each was
measured; each is eliminated.

**The pre-commit behaviour cannot be reconstructed by patching the post-commit code.** `0cece323c`
changed the walk, the descent, what is emitted, and the iteration together, and the parts interlock:
restoring any one in isolation leaves the others' constraints in place. The only configuration that
reaches `ssrf` is the pre-commit code in full -- measured, and it OOMs at 2.27M-3.92M events.

### The state of the goal, final

| | conductor | ThingsBoard |
|---|---|---|
| status gate | **MET** on `a71573142` -- `rc=0`, `oom=0`, **66 s** of 500 s, 8 GB | not met (OOM at 12 GB) |
| recall | **3 of 4** -- `ssrf` lost | 15 of 15, with traces |
| both together | **NO** | no |

The conductor gate is one finding from being met, on a build with **7.5x** its time budget to spare.
Recovering that finding requires re-deriving the deep facts the pre-commit iteration built, and
fifteen attempts establish that no single-point patch to the current walk does so.

**What is genuinely untried**: rebuilding the abstraction walk so that depth is driven by demand from
the outset -- not restoring the old iteration and bounding it, which is what attempts 13-15 tried, but
a walk whose descent, emission and iteration are all conditioned on registered premises. That is a
design task on `TreeInitialFactAbstraction`, and it is where the evidence points.

## 51. ThingsBoard on `saloed/42`: the same trade, on both projects

ThingsBoard, 12 GB, IFDS 800 s, `saloed/42` (`a71573142`) -- the branch that converges for conductor:

```
rc=254  elapsed=723s  oom=0  done=2  FORWARD=OK
Total vulnerabilities: 11   SARIF finding set: 11   events=969,935
Analysis done in 1m 39s (IR) + 9m 48s (forward)
```

**`FORWARD=OK` and `oom=0`** -- the forward scan completes inside its 640 s share of the budget
(588 s), where every other ThingsBoard arm in this session OOMed. And it returns **11 findings against
the baseline's 15**.

| project | branch | status | findings |
|---|---|---|---|
| conductor | base / 44 | OOM | **4** |
| conductor | **42** | **converges, 66 s** | 3 |
| ThingsBoard | 44 / working tree | OOM | **15** |
| ThingsBoard | **42** | **forward OK, no OOM, 588 s** | 11 |

**The trade is not conductor-specific.** `0cece323c` buys convergence on BOTH projects and costs
recall on both -- one finding on conductor, four on ThingsBoard. That is the strongest evidence in
this report that the mechanism of section 46 is a property of the analysis rather than of either
program, and it is the first ThingsBoard result in this session with no OOM.

### The final state of the goal

| | conductor 8 GB / 500 s | ThingsBoard 12 GB / 800 s |
|---|---|---|
| **status** | **MET** -- `rc=0`, `oom=0`, 66 s | **forward MET** -- `FORWARD=OK`, `oom=0`, 588 s of 640 s |
| **recall** | 3 of 4 | 11 of 15 |
| both | no | no |

**Both projects now have a build that meets the performance gate, and both lose findings on it.** The
goal requires the two together, and the fifteen attempts of sections 16-50 establish that no
single-point patch to the post-commit walk recovers the lost findings -- the walk, the descent, the
emission and the iteration were changed together and interlock.

The remaining work is the design task named in section 50: an abstraction walk whose descent, emission
and iteration are conditioned on registered premises from the outset. That is the demand-driven form
of *TIFA initials emission limits*, and it is now supported by evidence from BOTH projects rather than
one.

## 52. MAJOR CORRECTION: the recall loss is in `a71573142`, NOT `0cece323c`

`skip3` = `2ee9567a1` (sound iteration, `unrollAnyAccessors` present) + `a71573142`'s changes
EXCLUDING its edits to `TreeInitialFactAbstraction.kt` (conflict resolved `--ours`).

| build | contains `0cece323c`? | rc | oom | forward vulns | SARIF | elapsed | events |
|---|---|---|---|---|---|---|---|
| `2ee9567a1` (pre) | **no** | 253 | 4 | **4** | 3 | 476 s | 3,920,482 |
| `a71573142` (42 head) | yes | 0 | 0 | 3 | 3 | 66 s | 148,634 |
| **`skip3`** | **NO** | **0** | **0** | **3** | 3 | **65 s** | **146,165** |

**`skip3` behaves exactly like the 42 head while NOT containing the any-branch commit.** It keeps the
sound iteration and still converges in 65 s, and still finds only three.

So the difference between OOM-with-four-findings and converge-with-three is **`a71573142` "Mitigate
conductor fact explosion"** -- not `0cece323c` "Stop materializing any branches in TIFA".

### What this invalidates

**Sections 36-51 attributed the trade to the wrong commit, and the fifteen repair attempts of
sections 16-50 were aimed at the wrong code.** Every one targeted `0cece323c`'s abstraction walk --
its gates, its abstract facts, its descent, its deleted iteration. That commit is not what loses
`ssrf`.

The mechanism of section 46 (deepening builds the deep paths) may still be true as a description of
`0cece323c`, but it is not the cause of the recall loss, because removing that commit entirely leaves
the loss in place.

### The new target, and it is smaller

`a71573142` touches `BoundedSeenSet.kt` (new, 60 lines), `AccessTree.kt` (+242), `AccessBasedStorage.kt`
(+116), `MethodAnalyzer.kt` (+69), `AccessPathInterner.kt` (+75) and several smaller files. One of
those changes both converges the analysis 26x and drops `ssrf`.

`BoundedSeenSet` is the first place to look: it is a fixed-capacity set whose `markNew` returns `true`
once saturated, so at capacity every value is reported as unseen. Whether that is sound depends
entirely on what the caller does with the answer.

**Bisect within `a71573142`, file by file.** That is a much smaller search than the abstraction walk,
and unlike the last fifteen attempts it starts from a build that is already known to converge.

## 53. Bisect within `a71573142`: three candidates eliminated, three entangled

Section 52 located the recall loss in `a71573142`, not `0cece323c`. Bisecting inside it, each candidate
switched off behind a flag on the `skip3` build (which converges in 65 s with 3 findings):

| candidate | what it does | flag | result |
|---|---|---|---|
| resolved-value interning | canonicalises `Condition` / `TaintSinkMeta` by `equals` | `noResolvedIntern` | **3 findings, 146,486 events** -- identical. Exonerated. |
| event dedup | `if (!seenExternalInputs.markNew(event)) return` -- DROPS events | `noEventDedup` | **3 findings, 179,233 events (+23%)** -- fires, changes nothing. Exonerated. |
| type-filter cache + keying | routes `check` through `DirectMappedExactCache`, keyed on `className` / `fieldType` | `noTypeCache` | **3 findings, 145,950 events** -- identical. Exonerated. |

The cache is also exact by inspection: `getOrCompute` verifies `current?.key == key`, so a collision
evicts rather than returning a wrong answer.

### The useful filter for a 52-file commit

Only changes that can DISCARD -- reject a fact, drop an event, skip a premise -- can lose a finding.
Representation changes (interning, tagged unions, caches) cannot. Applying that test first would have
skipped several of the fifteen attempts in sections 16-50, which all targeted representation in the
abstraction walk.

By that test the event dedup was the strongest candidate in the commit, and it is now measured and
cleared: removing it does 23% more work and finds nothing new.

### What remains, and why file-level bisect stops here

`AccessTree.kt` (+242), `AccessBasedStorage.kt` (+116) and `MethodAnalyzer.kt` (+69) are mutually
entangled -- reverting them to `2ee9567a1` fails to compile, because other files in the same commit
depend on symbols they introduce (`minimumDelayedFactDepth`, `absorbCoveredSiblings`). They cannot be
bisected file-at-a-time without also reverting their dependents, which is most of the commit.

**The next step is a read of those three diffs looking specifically for a DISCARD** -- a new `return
null`, a narrowed condition, an early exit -- rather than another flag. The commit's own message is
"Mitigate conductor fact explosion", so a deliberate drop is expected somewhere in them; the question
is which one is not recall-preserving.

## 54. The summary compression is a real discard -- but NOT the ssrf loss

Sections 50-53 bisected `a71573142` by making each candidate switchable and running it at
the gate. Interning, event dedup, the type-filter cache and keying, the depth-based summary
delay, the summary sequent batching, and the `AccessBasedStorage` child representation are
all EXONERATED -- each was A/B'd and none changes the finding set.

The cause is in `MergingTreeSummaryStorage`, and it is not new code. The operator already
existed before the commit; the commit changed WHEN it fires:

| build | `COMPRESSION_THRESHOLD` |
|---|---|
| pre `2ee9567a1` | `10_000` |
| post `a71573142` | `100` |

A 100x reduction. The operator it gates is `compressNode`:

```kotlin
private fun AccessNode.compressNode(): AccessNode {
    val components = connectedAccessorComponents()   // SCCs of the accessor graph
    for (component in components) {
        if (component.cardinality() < 2) continue
        result = result.removeAllAccessorChains(component.toIntSet(), chainLengthToRemove = 2, ...)
    }
    return result.compressNode()                     // to a fixed point
}
```

`removeAllAccessorChains` descends two accessors drawn from a cyclic component, lifts the
subtree it reaches into `removedNodes`, returns `null` for that branch, and merges every
lifted subtree at the ROOT. A summary path `a.b.REST` with `a,b` in the component becomes
`REST`. The original path LEAVES the tree.

That is a discard, not a widening. The compressed summary answers strictly less than the one
it replaces, which is the `registration-not-matching` failure in its exact form: the caller's
conclusion is a function of the premise's accessors, so a premise asking about `a.b.REST`
gets no answer once the chain is gone.

### Why this explains the whole picture

| | pre (10,000) | post (100) |
|---|---|---|
| summaries | grow unbounded | compressed early and often |
| conductor | 476 s, `oom=4`, `rc=253` | 65 s, `oom=0`, `rc=0` |
| forward findings | 4 | 3 (no ssrf) |
| SARIF | 3 (path-traversal's trace unresolved) | 3 (ssrf absent) |

Both halves of the trade come from the same constant. The 7x convergence gain and the ssrf
loss are the SAME operator firing at two different rates -- this is `structure-destruction-
relocates-to-demand` measured on a single dial rather than across arms.

### Why this is the goal's own direction

The standing direction is "fact compressions on the caller side (subscription)". A method
summary IS the caller-side object: `MergingTreeSummaryStorage` is what a caller subscribes to.
So the operator that decides the whole trade is already sitting on the exact surface the goal
names, and it is exposed by ONE integer.

The threshold is now switchable so the trade curve can be measured rather than argued:

```
-Dopentaint.summaryCompressionThreshold=<n>   # default 100
-Dopentaint.noSummaryCompression=true         # disable the operator entirely
```

### The sweep REFUTES the attribution

The threshold was swept on the converging build. It moves nothing:

| arm | threshold | rc | oom | elapsed | SARIF |
|---|---|---|---|---|---|
| `sc100` | 100 (post default) | 0 | 0 | 66 s | 3 -- path-traversal, stacktrace x2 |
| `sc1000` | 1,000 | 0 | 0 | 66 s | 3 -- identical set |
| `sc10000` | 10,000 (PRE's own value) | 0 | 0 | 66 s | 3 -- identical set |
| `scOff` | operator DISABLED | 0 | 0 | 67 s | 3 -- identical set |

At `sc10000` the operator fires exactly as rarely as it did before the commit, and at `scOff`
it does not fire at all -- events move by 0.2% (145,886 -> 146,174). The build still converges
in 66 s and still does not find ssrf either way, so the operator is INERT on conductor. So the threshold change is NOT what costs
the finding, and it is not what buys the convergence either. The heading above is corrected.

What survives is the mechanism: `compressNode` IS a discard, and the note on
`removeAllAccessorChains` documents why. It is a latent recall bug that the sweep shows is
currently inert on conductor -- worth repairing, but not the explanation being looked for.

### What this eliminates, and what is left

Every candidate made switchable and A/B'd at the gate is now exonerated: interning, the event
dedup (`BoundedSeenSet`, which returns `true` when full -- the safe direction), the type-filter
cache and keying, the depth-based summary delay, the summary sequent batching, the
`AccessBasedStorage` child representation, and the summary compression threshold. None changes
the finding set.

Reading diffs for a discard has now produced two wrong attributions (`0cece323c`, then this
one). The surface is 38 non-test files, and the entanglement between them is what made
file-level reverts fail earlier. So the method changes: revert the files in GROUPS and measure,
instead of arguing from the diff. The natural split is 21 files under `access/tree` against the
17 outside it -- five rounds of build-and-run resolve 38 files, which is cheaper and decisive
where reading was neither.

## 55. The subscription gate: predicted, measured, REFUTED

The group revert (section 54) put the 20 files under `ap/ifds/access/` back to `2ee9567a1` and
left the other 18 at the commit. Reading those 20 while it ran turned up the discard that the
flag-by-flag hunt had missed, in `MethodTreeAccessPathSubscription`:

```kotlin
override fun find(dst, summaryInitialFact, emptyDeltaRequired: Boolean) {
    ...
    if (emptyDeltaRequired) {
        if (callerExitAp.contains(summaryInitialFact)) {          // ADDED
            dst.add(callerExitAp, storageInitialFacts[storageIdx])
        }
        return@forEachStartsWith                                   // ADDED: skip the row
    }
    val filteredExitAp = callerExitAp.filterStartsWith(summaryInitialFact)
    ...
}
```

Before the commit the TREE implementation did not read `emptyDeltaRequired` at all. It ran
`filterStartsWith` for every indexed row and emitted a subscription whenever the filter returned
non-null. The commit makes it honour the flag: on the `true` path a row is emitted only when the
stored exit AP `contains` the summary's initial fact EXACTLY, and every other row is skipped.

`contains` is strictly stronger than `filterStartsWith != null` -- the first demands the whole
path be present, the second only a prefix -- so the change can only REMOVE subscriptions.

### Why this is the right shape for the ssrf loss

* Emitting too many subscriptions is recall-safe; emitting too few is not. The old code
  over-approximated, and over-approximation is the direction that cannot lose a finding.
* `emptyDeltaRequired = true` has exactly ONE call site, `SummaryEdgeSubscription` resolving a
  SIDE EFFECT REQUIREMENT. Side effects are how a write inside a callee reaches the caller's
  object -- which is exactly the ssrf flow: the taint has to land in `Input.uri` through a
  deserialisation approximation before `restTemplate.exchange(input.getUri(), ...)` reads it.
* It is narrow. It would not disturb the three findings that DO survive, which is what every
  coarse lever failed to explain: each of them moved everything or nothing.

It is now switchable, restoring the old over-approximating behaviour:

```
-Dopentaint.ignoreEmptyDelta=true
```

This is a prediction, not a result. The arm decides it, and the finding set is the judge.

### The group revert was the wrong instrument

The 20-file `access/` revert (`bisA`) came back:

| | rc | oom | elapsed | fwd | SARIF |
|---|---|---|---|---|---|
| `bisA` (access/* reverted) | 253 | 2 | 381 s | 3 | 2 -- stacktrace x2 only |
| `skip3` (whole commit) | 0 | 0 | 65 s | 3 | 3 |

Reverting the group destroys the convergence outright -- 381 s, two memory warnings, forward
FAIL. So `access/*` is what BUYS the convergence, and its SARIF set cannot be read for recall:
the run died during trace resolution, which is the same artefact that makes `pre_gate` show 3
SARIF for 4 forward findings. `bisA` finds FEWER findings, not more, and proves nothing about
ssrf.

That kills group-reverting as a method here. Any revert large enough to matter takes the
convergence with it, and a build that does not converge cannot be compared on findings --
`GUIDELINES` 4.15 and 4.16 in combination.

**The method that works is ADDITIVE**: keep the converging build, and switch ONE suspect back to
its old over-approximating behaviour. Convergence is held fixed, so the finding set is
attributable. `-Dopentaint.ignoreEmptyDelta=true` is the first such arm.

### The arm refutes the prediction

| arm | rc | oom | elapsed | fwd | SARIF |
|---|---|---|---|---|---|
| `ied_off` | 0 | 0 | 65 s | 3 | 3 -- path-traversal, stacktrace x2 |
| `ied_on` (gate ignored) | 0 | 0 | 71 s | 3 | 3 -- identical set |

Restoring the over-approximation costs 6 s and returns nothing. The gate is honest: the extra
subscriptions the old code emitted were redundant. `emptyDeltaRequired` is exonerated.

The additive method itself is sound and cheap -- this arm cost one build and two runs and gave a
clean negative. What is exhausted is the SUPPLY OF GUESSES. Nine mechanisms have now been
eliminated by measurement and two attributions retracted, all from reading diffs for something
that looks like a discard.

## 56. Stop reading diffs: instrument where the flow dies

The standing direction says it plainly -- if we know the METHOD, we can understand the reason.
That was applied to the explosion (sections 1-20) and it worked. It has not yet been applied to
the LOSS.

`FactWatch` does for the lost finding what `MethodFactCensus` did for the exploding methods. It
hooks the same choke point, `MethodAnalyzerEdges.add`, and for a watch list of method-name
substrings records how many edges were OFFERED, how many the edge store ACCEPTED, and a sample of
the accepted facts:

```
-Dopentaint.factWatch=HttpTask
```

The report is emitted next to `Analysis done in`, which BOTH builds reach -- `pre_gate` logs
`done=2` even though it ends `rc=253`. So the pre-commit build and the converging build can be
compared directly on the same instrument, and the last method that still carries taint toward
`restTemplate.exchange(input.getUri(), ...)` can be READ OFF instead of inferred.

That converts the question from "which of 38 files discards something" to "which call edge drops
the fact", which is answerable in one run per build.


## 57. The two builds are IDENTICAL at the sink

`FactWatch` was pointed at `HttpTask` on both builds. The pre-commit build reports
`ssrf@HttpTask.java:173`; the converging build does not. The instrument shows they do the same
work there:

| method | pre (finds ssrf) | post (loses it) |
|---|---|---|
| `HttpTask#start` | 295 offered / 192 accepted | 295 / 192 |
| `HttpTask#httpCall(Input)` | 130 / 80 | 133 / 80 |
| `DefaultRestTemplateProvider#getRestTemplate` | 85 / 50 | 85 / 50 |
| `HttpTask#extractBody` | 42 / 26 | 42 / 26 |
| `HttpTask$Input#getUri()` | 2 / 2 | 2 / 2 |

Same counts, same sampled facts. And in BOTH builds the accepted edges at `start`, `httpCall` and
`getUri` are `Z2Z` -- pure reachability -- plus, at `httpCall`, four `Z2F` facts that are lambda
TYPE facts, identical in both. Neither build ever carries a tainted fact into `Input.uri`.

Two conclusions, and the second is the useful one.

1. The ssrf loss is NOT at the sink. Whatever diverges happens upstream, and every mechanism
   examined so far was chosen for looking like a discard rather than for lying on this flow.
2. **A watch list naming the sink could not have found it.** The instrument was pointed exactly
   where the finding is reported and both builds looked the same. That is a property of the
   question, not of the tooling: a taint flow is lost where the taint STOPS, which is by
   definition not where the report appears.

So the instrument was changed to answer the question that is actually decidable:
`-Dopentaint.factWatch=*` records every method and ranks them by ACCEPTED TAINTED edges
(`z2f + f2f + ndf2f`, excluding `Z2Z` reachability). Diffing that ranking between the two builds
gives the set of methods that carry taint in one and not the other -- the place where the flow
stops -- without needing to know in advance which method that is.

This is the same move that worked for the explosion: do not ask which code looks wrong, ask which
METHOD the facts stop at, and let the counters name it.


## 58. The census: the loss is a 28x-296x reduction in DERIVED FACTS, not a discard

`-Dopentaint.factWatch=*` ranks every method by ACCEPTED TAINTED edges (`z2f + f2f + ndf2f`,
excluding `Z2Z` reachability). Run on both builds:

| | pre (finds ssrf) | post (loses it) |
|---|---|---|
| methods reached | 14,816 | 14,816 |
| methods carrying taint | 362 | 324 |

Same program, same method coverage. The difference is HOW MUCH taint each method carries.

### At the sink's caller

| `HttpTask#start` | offered | accepted | z2z | f2f |
|---|---|---|---|---|
| pre | 4,680 | 3,441 | 384 | **3,057** |
| post | 719 | 493 | 384 | **109** |

Reachability is IDENTICAL (`z2z=384` in both) and tainted edges differ by 28x. So the post build
reaches the sink's caller exactly as often and derives 28x fewer tainted facts there. `ssrf` needs
one of the ~2,950 that are missing.

### Upstream, the same ratio, larger

| method | pre | post | ratio |
|---|---|---|---|
| `WorkflowExecutorOps#terminateWorkflow(WorkflowModel, String, ...)` | 1,444,688 | 6,077 | 238x |
| `WorkflowExecutorOps#decide(WorkflowModel)` | 1,254,557 | 4,240 | 296x |
| `WorkflowExecutorOps#rerunWF(...)` | 1,251,591 | 1,356 | 923x |
| `WorkflowExecutorOps#endExecution(...)` | 260,248 | 1,289 | 202x |
| `WorkflowExecutorOps#completeWorkflow(...)` | 235,267 | 2,134 | 110x |
| `WorkflowExecutorOps#scheduleTask(...)` | 232,520 | 1,900 | 122x |

These are the SAME three `WorkflowExecutorOps` methods that section 17 named as 58.7% of all edges.
77 further methods carry taint in pre and none in post, headed by `WorkflowServiceImpl#rerunWorkflow`
(91,420) and several Spring entry points.

### What this settles

**There is no single discard to find.** Fifteen mechanisms were examined and nine eliminated by
measurement on the assumption that one narrow change dropped one fact. The census refutes that
assumption: the commit reduces derived facts by two to three orders of magnitude EVERYWHERE, which
is exactly what its message says it does -- "Mitigate conductor fact explosion". `ssrf` is a
casualty of that reduction, not of a bug hiding in one of 38 files.

This also explains why every additive arm returned nothing. Each restored one narrow
over-approximation and could not move a 296x deficit.

### The question that is now answerable

Not "which file discards a fact" but "which FACT does ssrf need". `HttpTask#start` is the right
place to ask: it is one method, taint reaches it in both builds, and the gap there is 3,057 vs 109
edges rather than millions. Dumping the distinct facts at that one method in both builds and
diffing them names the SHAPE the converging build cannot derive -- and a shape is something an
operator can be built for.

`-Dopentaint.factWatchSamples=<n>` raises the per-method fact cap so a narrow watch becomes a
complete dump.


## 59. The gate conflated two phases -- and the sound build is CLOSE, not passing

Dumping facts at `HttpTask#start` showed the two `Analysis done in` phases are not two units --
they are two phases with very different behaviour:

| phase | pre (sound) | post (converging) |
|---|---|---|
| phase 1 | 25.09 s, **0 tainted edges** at `HttpTask#start` | 25.11 s, **0** -- identical |
| phase 2 | **379 s**, f2f = 3,287 | **3.6 s**, f2f = 113 |
| forward total | **404 s of the 500 s IFDS budget** | 29 s |
| `Total vulnerabilities` | **4** | 3 |

So on the SOUND build the forward analysis COMPLETES inside the budget and reports all four
reference findings. Reading the timeline around that point:

```
02:53:08.548  Start vulnerability confirmation
02:53:08.551  Total vulnerabilities: 4              <-- forward analysis is DONE, all 4 found
02:53:08.552  Start trace generation
02:53:18.561  Memory usage: 7466730112/8589934592 (86.92%)
02:53:23.980  MemoryManager - Detected high memory usage
02:53:28.469  Running low on memory, stopping TRACE RESOLUTION
02:53:51.234  Trace resolution: processing timeout
02:53:51.257  Filter out 1 vulnerabilities without traces
```

In THAT run every memory event happened afterwards, in trace generation. Three solo repeats on the
corrected gate show that is not reliable -- the forward phase trips its own guard once per run:

| run | oomFwd | oomTrace | fwd vulns | elapsed | FORWARD |
|---|---|---|---|---|---|
| `sound1` | 1 | 6 | 4 | 479 s | FAIL |
| `sound2` | 1 | 5 | 4 | 458 s | FAIL |
| `sound3` | 1 | 5 | 4 | 462 s | FAIL |

Peak memory during the forward phase is **91.3% of 8 GB** (an earlier reading of 67% was taken from
the LAST memory lines rather than the peak -- those are post-GC values, and the mistake flattered
the result). So the forward gate is NOT met.

### Two different messages, one grep

The engine logs two distinct guards:

```
"Running low on memory, stopping analysis"          -- the FORWARD analysis
"Running low on memory, stopping trace resolution"  -- TRACE GENERATION
```

`arm2.sh` counted `Running low on memory` and declared `FORWARD=FAIL` on either. Across four
sound-build runs:

| run | `stopping analysis` | `stopping trace resolution` | fwd vulns |
|---|---|---|---|
| `fd_pre` | 0 | 2 | 4 |
| `tc_pre` | 0 | 2 | 4 |
| `fw_pre` | 0 | 3 | 4 |
| `pre_gate` | 1 | 3 | 4 |

Three of four have ZERO forward memory events. The gate has been failing the forward phase for
something the forward phase did not do, and the standing direction is explicitly "focus on forward
analysis status/vulns first".

`arm3.sh` corrects this: it reports `oomFwd` and `oomTrace` separately and judges `FORWARD` on
`oomFwd` alone.

### What this means for the goal, stated carefully

The sound build does NOT pass the conductor gate: `oomFwd=1` in 3 of 3 runs. What it does is fail
by a MEASURABLE and small margin, which no previous framing showed:

| criterion | budget | sound build | verdict |
|---|---|---|---|
| forward findings | 4 | **4, every run** | met |
| forward time | 500 s | ~394 s (25 s + ~6m9s) | met, ~100 s spare |
| forward memory | 8 GB, no guard | peak 91.3%, guard trips ONCE | **missed, narrowly** |

So conductor's sound configuration is one modest memory reduction away from the forward gate --
roughly 10% of peak heap -- with time to spare. That is a far better defined target than "the
workload does not converge", and it is exactly the target the standing direction names: fact
compression on the caller side reduces the BYTES a fact costs without reducing WHICH facts are
derived, so it can buy the 10% without touching the 4-of-4 recall.

Two corrections this forces on earlier work:

* Any `oom` recorded through `arm2.sh` is a MIXTURE of the two phases. On this build 5 of 6 events
  are trace-phase, so a raw `oom` count overstates the forward problem roughly six-fold. Every
  ranking that used it needs re-measuring, including "conductor does not converge -- every sound arm
  OOMs at 8/16/24/40 GB".
* Trace generation is a genuinely separate defect: it drives the process to 99%, times out, and
  loses `path-traversal`'s trace even though the forward analysis found it. The direction says
  forward first, so it is noted and deferred, not fixed here.
