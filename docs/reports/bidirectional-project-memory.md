# Bidirectional project memory report

Historical pre-O1–O3 measurements. Latest reached in these guard runs: OWASP **run-1 F/L1**,
Conductor **run-2 B/L2**, ThingsBoard **run-1 F/L1**, PDF **run-3 F/L3**; all stop on OOM.
These are also the heap capture iterations. Main has no bidi iteration. Later primary results and
priorities are in the [new proposal](../design/bidirectional-core-optimization-20261013.md), and
revised-code validation is in the [rollback retest record](bidirectional-core-rollback-20261013.md).

Measured on 12 October 2026. GB means 10^9 bytes; GiB means 2^30 bytes. Four live heaps were saved when the memory guard fired. The same project models, rules, heap limits and time limits were used as in the [project tests](bidirectional-project-testing.md). The scan command was the direct `runProjectAnalyzer` Gradle task.

The main shared cost is the strong method-form cache. It retains 2.41–6.46 GB in these snapshots. Conductor also has 3.63 GB in the IR owner union. Stirling-PDF has 1.08 GB in active actor stores. The snapshots do not show old run managers or old actors with retained work fields.

| Project | Live heap, GB | Method cache, GB | Cache share | Lowered-form union, GB | Actor/store union, GB | IR union, GB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 7.769 | 6.459 | 83.14% | 6.357 | 0.020 | 0.816 |
| Conductor | 7.674 | 2.412 | 31.43% | 2.219 | 0.460 | 3.633 |
| ThingsBoard | 11.484 | 5.622 | 48.96% | 5.154 | 0.783 | 2.599 |
| Stirling-PDF | 7.672 | 4.361 | 56.85% | 4.183 | 1.077 | 1.532 |

The method-cache column is one top-level dominator. The other subsystem columns are retained owner unions. They can overlap and do not form an additive partition. The cache includes method graphs, alias data and forms; the lowered-form union measures the forms alone. Strong paths to GC roots confirm the cache's ownership in all four heaps.

## Capture method

The optional JVM property `opentaint.memorySnapshotDir` makes the memory guard save one live HPROF before it calls the cancellation handler. The guard first clears its soft caches and requests further GC. The dump requests another full GC. The property is absent in a normal run. The diagnostic change does not change field limits, AP semantics or the configured policy. It delays cancellation until the dump completes. Limits start at 1.

The dumps add a stop-the-world pause. Use the earlier control runs for performance comparisons. Use these runs to measure memory owners. HPROF file size is not live heap size. MAT calculates object sizes with compressed Java references.

| Project | Heap limit | Active run | Guard logged usage before dump | Dump file bytes | Dump pause |
| --- | ---: | --- | ---: | ---: | ---: |
| OWASP | 8 GiB | Forward 1, limit 1 | 7,807,968,272 | 13,142,403,621 | 24.397 s |
| Conductor | 8 GiB | Backward 2, limit 2 | 7,761,595,632 | 12,069,501,475 | 18.404 s |
| ThingsBoard | 12 GiB | Forward 1, limit 1 | 11,643,041,240 | 19,169,235,695 | 41.330 s |
| Stirling-PDF | 8 GiB | Forward 3, limit 3 | 7,762,614,400 | 12,628,640,901 | 29.425 s |

All four stop in the same run as the control. No timeout occurs. The 8 GiB guard threshold is 7,730,941,132 bytes; the 12 GiB threshold is 11,596,411,699 bytes.

MAT 1.17.0 runs with Java 21 and a 20 GiB tool heap. The analyzer uses Java 17 and its specified 8 or 12 GiB heap. Initial MAT parse jobs run in sequence. Indexed detail queries use an 8 GiB tool heap; one bounded query job can run beside a parse job. No object-discard option is used. The complete histogram counts all objects that remain reachable in MAT. Retained owner unions can overlap; do not add them. A small retained union does not count objects shared with other owners. Allocation profile weights are a different measurement.

## OWASP: compiled sink forms

The live heap has **288,492,757 objects and 7,769,030,680 bytes**. One `JIRMethodEntries` object retains **6,459,416,584 bytes, or 83.143%**. The union of lowered call plans and rule forms retains 6,356,933,528 bytes. The current method actor union retains 19,678,752 bytes. The AP manager and store-interner union retains 1,351,192 bytes. These unions exclude values shared with other owners and are not an additive breakdown.

The heap contains 10,638,331 `SinkRule` objects. Immediate dominators attribute 10,635,214 of them to 32,814 call plans. There are only 2,882 method forms for 2,865 method entries. Thus the main cause is the size of the forms, rather than a large number of contexts. There is one run manager at index 1, one `InitialAp`, no `PremiseSet`, and 183 AP trie nodes. All 2,882 retained method actors are active forward actors. This heap does not show old-run retention.

The cached forms retain almost all 30.2 million `Pattern` objects and 32.0 million `PathFact` objects. The six shallow classes `ArrayList`, unmodifiable list wrappers, `PathFact`, `Pattern`, reference arrays and `SinkRule` use 5,497,607,176 bytes together. This is an object-size total, not a separate retained-owner total.

The owner chain is:

```text
JIRMethodEntries.byKey
  -> JIRMethodForms.plans
  -> FormsCache.slots
  -> CallPlan.sinks
  -> SinkRule.patterns / endFacts
```

[JIRRuleForms.cubes](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt) first expands each condition into disjunctive normal form (DNF). [JIRRuleForms.sinks](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt) then forms the product of root and element choices for eligible argument literals. A cube with `k` such literals can create `2^k` alternatives before deduplication. [SinkRule](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/RuleStatement.kt) makes a new owned `Pattern`, `PathFact`, path list and list wrapper for each retained literal. It also makes patterns and end-facts containers for each alternative, including empty end-facts lists. The method-form cache keeps these objects for the whole analysis.

The strong-root query confirms the same chain, with `JIRMethodEntries` held by the main Java thread and the interpreter. Its `byKey` map retains 6,306,589,280 bytes. The largest method form is `BenchmarkTest00777.doPost(HttpServletRequest,HttpServletResponse)`: it retains 17,827,760 bytes and 32,102 sink alternatives. Every one has empty end facts. Of these, 23,553 are generated XSS alternatives and 8,418 are response-injection alternatives: 99.59% together. Most have three literal patterns (27,584); 4,387 have two and 131 have one.

Across all 35,727 call plans, the largest sink list has 5,897 alternatives. The named method contains many resolved instances of the same serialized sequence sink. For example, each of several XSS `sink:44` rule objects contributes about 435 alternatives. The multiplication thus occurs inside rule lowering as well as across method call sites. It is not 10 million exported vulnerabilities.

The named cache has 66,655 empty literal paths, 17,871 paths of length 1 and 7,131 of length 2. All nonempty paths start with the same element accessor. This is consistent with array-element branching. The condition-tree probe pins one expansion cause. The resolved HashMap XSS `sink:44` condition is `(W OR B) AND W AND B`. `W` is one receiver-state mark. `B` has 14 choices: seven source-state marks at argument 0, each at its root or element. The first OR has 15 choices because it also contains the same `W`. The compiler therefore enumerates **15 × 14 = 210 raw DNF combinations**. Boolean absorption reduces the expression to `W AND B`, with only 14 choices. The current compiler removes exact duplicates after construction, but does not remove this redundant OR before the product.

In the named cache, that resolved rule has 146 alternatives per call and is compiled at three calls (438 retained copies). Other resolved XSS rule instances have 435 alternatives at a single call. These counts depend on the selected call's array policy and the ordered-list deduplication. They are not one fixed formula for all calls. When argument 0 can be an array, sink lowering can prepend another element to already resolved root/element literals. The absorbed family would have at most 28 raw choices and 21 distinct unordered literal shapes in that case. This is a logic and size estimate, not a tested patch. Alternative IDs, ordered literal slots, end-fact triggers, report keys and seed keys still require validation.

## Conductor: IR storage and small form containers

The live heap has **226,995,413 objects and 7,674,189,968 bytes**. The IR database and storage owner union retains **3,632,774,144 bytes (47.34%)**. One RAM storage object alone retains 2,256,213,408 bytes. One `JIRMethodEntries` object retains **2,412,240,784 bytes (31.43%)**. These are the two large sources of retained memory. The IR figure is an absolute size in the bidi heap; no main live-heap snapshot was taken, so it is not a measured increase over main.

The lowered-form union retains 2,219,025,736 bytes. It contains 1,255,921 `StatementSummary` objects, 2,557,257 `MicroEdge` objects and 4,514,615 `PathFact` objects, but only 158,813 sinks. Thus sink alternatives do not explain most of this form memory.

Five collection classes inside the form retained set use **1,356,326,008 bytes (61.12%)**: 6,718,762 `LinkedHashMap` objects, 8,801,596 map entries, 3,836,378 map tables, 8,232,642 lists and the same number of list wrappers. Reference arrays add 143,773,112 bytes. [StatementSummary](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/StatementSummary.kt) builds maps for two sets, two filter maps and the edge index. It also owns edge and conjunction lists and per-base edge groups. This happens for small and empty forms. [FormsCache](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormsCache.kt) keeps both directions. Reversal constructs new summaries and indexes while each reversed micro edge keeps the forward edge identity. The cache therefore retains many small containers across runs.

The current actor union retains 459,920,248 bytes. Request and join owners retain 210,309,168 bytes; publication and subscription owners retain 325,580,256 bytes. Persistent records and the saved frontier retain 105,307,008 bytes. These unions overlap and must not be added. Queue and channel owners retain only 917,232 bytes. This heap does not support a queue-backlog explanation for the large retained size.

There is one run manager at index 2. All 17,525 retained method actors are active backward actors at limit 2. No forward actor remains. Each actor still has its expected live work fields. Old actors and old run managers are not the observed cause. Analysis-wide forms and saved records are intentional owners that remain across the barrier.

The RAM storage has one mutable data container and no retained transaction object. The IR retained set includes about 1.72 GB of byte arrays and 1.44 GB of persistent map entries and tree nodes. The immediate-dominator table attributes 20,114,697 byte arrays (619,020,400 bytes) to persistent map entries. Many other byte arrays have shared owners. This requires a property and index drill before a specific IR storage change can be proposed; the table alone does not show a transaction-version leak or prove that all arrays are bytecode blobs.

## ThingsBoard: broad exit rules and cached forms

The live heap has **356,025,402 objects and 11,484,088,216 bytes**. One `JIRMethodEntries` object retains **5,622,117,296 bytes (48.96%)**. Its `byKey` map retains 4,143,929,944 bytes. The strong-root query confirms the method-form chain through that map. The lowered-form union retains 5,153,976,416 bytes.

The form union contains 2,058,457 statement summaries, 5,324,946 micro edges and 11,446,561 path facts. The same five collection classes as in Conductor use **3,164,208,736 bytes (61.39%)**; reference arrays add 374,658,096 bytes. Small-summary collection overhead is again a large part of the retained heap. The largest method form, `DeviceController$1.onSuccess(ClaimResult)`, retains 8,125,456 bytes. It contains a 4,019,240-byte call plan with no sinks. There are 48 call plans with at least 1 MiB retained; the largest has zero sinks. The bounded stage query finds **3,933,936 of the named plan's 4,019,240 retained bytes in its SOURCES summary (97.88%)**. That summary has 7,266 conjunctions with 20,232 literal memberships: 1,566 have two literals and 5,700 have three. DNF source cubes multiplied by source actions are the main cause of this sampled large plan.

The same plan also has 1,026 rewriter cleaner steps but only 104 distinct base/path/reach/mark tuples; the most repeated tuple occurs 81 times. This is a smaller, separate duplication cost. [JIRCallPlanBuilder.rewriterSteps](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt) expands relevant source and cleaner rules across action positions and relevant automaton marks. Its `allRelevant=true` lookup bypasses the prescan rule filter and removes negative method/class/arity constraints during relevant-rule resolution. It can add string-byte cleaners and field read/write summaries. The stage measurement puts conditional source forms ahead of cleaner chains for this plan.

There are 2,154,526 compiled sinks. Immediate dominators attribute **2,145,840 to rule statements**, and only 8,663 to call plans. This is a different shape from OWASP. [JIRBoundaryForms.exitRules](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRBoundaryForms.kt) requests the full candidate exit-rule list with null facts, validates each rule, rewrites its condition and lowers the surviving alternatives. It caches normal and exceptional exit forms. This path has no call array-choice callback. Boundary rule count and condition expansion are therefore the sources to inspect.

The rule-configuration union retains 1,351,983,760 bytes. It contains 3,769,404 resolved `TaintMethodExitSink` objects and 12,469,611 `ContainsMark` objects. The top-level provider owner retains 997,768,576 bytes. Its class is `SpringRuleProvider`, but this includes its base provider chain; it does not prove that Spring annotations generate all these rules. [MethodTaintConfigurationResolver](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/MethodTaintConfigurationResolver.kt) specializes rule templates for each method and each any-argument context. [TaintConfiguration](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/TaintConfiguration.kt) caches the resolved lists. The strong validation map in `JIRRuleForms` also keeps candidate rules, including rules that later rewrite to false.

The largest boundary rule statement, on `WidgetTypeDetails.getTags(): String[]`, contains 130 sink alternatives. Its identity drill finds **65 distinct generated XSS `xss#sink#1` method-exit rules, with two one-literal alternatives each**. All have empty end facts. Their serialized IDs include `method-exit-sink:428`, `method-exit-sink:4050` and `method-exit-sink:2445`. The conditions have an OR root. This named boundary proves broad generated XSS exit-rule count, with only a small alternative multiplier. It is a sample, not a census of all 2.15 million boundary sinks. [The sequence converter](../../core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/AutomataToTaintRuleConversion.kt) emits end sinks with an any-method matcher, which explains how these rules can be instantiated throughout the visited method set.

The IR owner union retains 2,598,807,680 bytes. The current actor union retains 783,159,232 bytes, requests and joins 617,213,776 bytes, and publications and subscriptions 313,094,136 bytes. These unions overlap. Queue and channel owners retain 40,488,512 bytes; pending work retains 20,934,064 bytes. They are relevant active work, but they are much smaller than the form cache.

All 72,130 method actors belong to active forward run-1 at limit 1. There is one run manager, 47,159 method entries, 66 initial APs, no premise set and 128,393 trie nodes. No earlier run exists in this scan. The large memory population is already present during zero and initial forward work.

## Stirling-PDF: conjunction reversal and active fact groups

The live heap has **239,437,808 objects and 7,671,940,360 bytes**. One `JIRMethodEntries` object retains **4,361,299,656 bytes (56.85%)**. Its `byKey` map retains 3,652,831,072 bytes. The strong-root query confirms the same form-cache chain. The lowered-form union retains 4,183,351,896 bytes.

This form union contains 2,168,319 conjunction edges, 6,850,303 literal patterns, 8,373,908 micro edges, 14,523,497 path edges and 25,146,067 path facts. This heap has much more edge content per summary than Conductor. [StatementSummary.reversed](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/StatementSummary.kt) expands every forward conjunction literal into an independent reversed branch. It creates a synthetic forward `PathEdge`, a reversed `PathEdge`, two new endpoint `PathFact`s and a `MicroEdge` that holds both directions. Their object sizes total 168 bytes per reversed literal, before lists, indexes and compiled edges. The original forward conjunction and its literals also remain cached. This is a concrete retained-size multiplier; the global pattern count alone is not the count of reversed conjunction literals because it also includes sink patterns.

The largest method form is `InviteLinkController.generateInviteLink(String,String,Long,Integer,boolean,Principal,HttpServletRequest)`. It retains **106,595,024 bytes**. The named cache includes a reversed zero-sink plan that retains 6,125,112 bytes. The named plan's SOURCES summary contains **24,622 edges** and retains 6,107,432 bytes, or 99.71% of the plan's retained bytes. The exact even/odd cache slots identify its forward mate. The forward SOURCES summary has **8,572 conjunctions with 24,619 literal memberships**, plus three ordinary edges; it retains 1,509,248 bytes exclusively. The reversed SOURCES summary has 24,622 edges and retains 6,107,432 bytes exclusively. The 168-byte branch objects alone account for 4,135,992 bytes for those 24,619 literals, before indexes and compilation data. Shared original payloads are excluded from those exclusive retained sizes; the sizes must not be treated as total reachable graph costs. Other large reversed summaries have the same edge count and size. Its 1,460 root cleaner steps have 452 distinct tuples; the most repeated tuple occurs 74 times. Conditional source forms and their reversal dominate this plan; cleaner duplication is secondary.

The current actor union retains **1,076,851,064 bytes**. It contains 2,423,317 trie nodes and 1,745,016 conclusion groups. The actor union has 3,540,955 `HashMap` objects. [ConclusionGroup](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Groups.kt) creates two maps and a two-element boolean array per group. Two maps per conclusion group account for 3,490,032 maps, or about 98.56% of the actor union's map count. All actor-union maps use 169,965,840 bytes; the conclusion-group maps account for 167,521,536 bytes; boolean arrays add 41,880,384 bytes. Taint groups also create two layer lists and their backing arrays even when only one layer is present. Dense per-premise statement arrays use 14,646,024 bytes, which is much smaller than these group containers.

One active unit runner retains 645,652,824 bytes. This is active work at the guard, not an old unit. Requests and joins retain 175,140,560 bytes, publications and subscriptions 177,119,208 bytes, and persistent records and frontier owners 35,650,888 bytes. The IR union retains 1,531,780,008 bytes. These unions overlap. Queue and channel owners retain only 324,744 bytes; they are not a large retained backlog in this capture.

There is one run manager at index 3. All 7,267 method actors are active forward actors at limit 3, with live work fields. No run-1 or run-2 actor remains. The earlier direction's compiled forms and forward edge identities remain in the analysis-wide cache by design. This is distinct from old actor-store retention.

## Measured optimization priorities

The [analyzer core design proposal](../design/bidirectional-core-optimization.md) covers the core changes below and the earlier CPU/allocation candidates. It ranks them by expected memory impact, uses NNF predicates for both source and sink rules, and adds managed soft references for rebuildable forms. IR representation changes are outside that proposal.

1. Simplify positive conditions before DNF and array products. The measured OWASP absorption example reduces 210 raw combinations to 14 choices. Canonicalize duplicate literal sets and remove redundant positive clauses before copying facts. Then reduce form payload copies. OWASP needs immutable literal/path sharing across sink alternatives and call forms. Stirling-PDF needs the same sharing across source cubes and actions, and a compact reversed-branch representation that preserves forward identity and source/may classification. Keep every required alternative and literal slot.
2. Reduce form collection overhead. Conductor and ThingsBoard spend about 61% of the lowered-form union in five collection classes. Use immutable empty/singleton collections, compact indexes and shared owned values where the key includes all rule, context and direction semantics. Keep defensive ownership at public boundaries.
3. Share repeated rule-rewriter cleaner values after source forms are addressed. The named ThingsBoard plan has 1,026 steps but 104 distinct tuples; the named Stirling-PDF plan has 1,460 steps but 452 tuples. The relevant-rule lookup expands action positions and automaton marks. Preserve the complete required mark universe and all cleaner reach/exclusion semantics when sharing or normalizing these actions.
4. Reduce current fact-group containers. Stirling-PDF stores two maps, reach arrays and both layer lists per group. Allocate absent kinds and layers when needed, and store reach flags as bits. Retest typed FLOW/TAINT groups, publication deltas and joins under the same run limits.
5. Reduce IR representation cost after the form cost is addressed. The current RAM tables have the following retained sizes. Most of the single RAM storage owner's bytes belong to blob tables. The raw byte-array histogram alone could not establish this ownership.

| Project | RAM properties | RAM links | RAM blobs |
| --- | ---: | ---: | ---: |
| OWASP | 91,152,024 | 66,573,520 | 381,844,296 |
| Conductor | 457,455,544 | 334,066,664 | 1,464,690,272 |
| ThingsBoard | 318,285,848 | 238,287,872 | 1,062,832,816 |
| Stirling-PDF | 184,238,008 | 136,808,888 | 659,224,448 |

These are retained owner unions for each current RAM table, in bytes. They exclude values shared with other owners. The IR database union also includes decoded objects and other caches. Each heap has one current RAM data container, represented by `RAMDataContainerMutable` with `isMutable=false`. A packed representation must still support the virtual method and lambda write paths; the frozen flag alone is not a proof that the whole analysis can use immutable storage.

The named-cache sample also limits the proposed empty-container fix. Conductor has 657 edge-stage summaries in its largest call cache: all have empty conjunction lists, and 569 have empty operand and result filter maps. Only 34 have all four fields empty. Stirling-PDF has 1,467 such summaries: 1,456 have empty conjunctions, 1,262 empty operand filters, 1,245 empty result filters, but only 68 have all four fields empty. The four fields are edges, conjunctions, operand filters and result filters. The query does not check the touched bases; a kill summary can match all four empty-field tests and still have an effect. These counts do not permit replacement with `StatementSummary.EMPTY`. Sharing individual empty containers has a much broader scope than sharing complete empty summaries. These are bounded cache samples, not whole-project estimates.

Earlier profiles identify repeated graph construction, queue comparator boxing, alias-set copies and trie operations as CPU and allocation costs. Those remain useful optimizations. The retained-size evidence makes form representation the first shared memory target. Repeat all four fixed workloads after a memory change to check completion, peak memory, findings and iteration times. This investigation changes diagnostics and reports; it does not measure an optimized engine.

## Evidence

The artifact root is `/drive-testcomp/opentaint-go-rules/bidi-memory-20261012`. Each `<project>/bidi` directory contains `guard-live.hprof`, analyzer and console logs, resource samples, continuous JFR, a command manifest and terminal summary. `mat-parse.log` records parsing and query results. `guard-live_BidiMemory.zip` and `mat-report/pages` contain the retained-size, histogram, dominator and actor lifecycle tables.

The exact capture patch is `tested-current.patch`. Its normalized SHA-256 is `09eb4f775aa5ddc44805b70b0c2275ff77e0c1437cae9e6814a1e8e2261b206e`, which matches all capture manifests. It includes the opt-in dump hook. The baseline report remains based on the earlier patch without this hook.

Recreate the owner report with the installed MAT tool and [the query file](../../scripts/bidi-memory-report.xml):

```sh
/drive-testcomp/opentaint-go-rules/opentaint-w3-benchmark-results/heap-composition/tools/mat/ParseHeapDump.sh \
  /absolute/path/to/guard-live.hprof \
  /drive-testcomp/opentaint-go-rules/opentaint-w3/scripts/bidi-memory-report.xml
```

The owner query file exports complete tables with `limit=-1`; the largest-owner views alone have a 1,000-row limit. Empty class filters can produce a logged query error. Check the complete class and actor tables before interpreting an absent filtered table as zero. The query syntax and lifecycle filters were checked on small Java heaps. Those fixtures are tool checks, not project measurements. The supplemental property/index queries also use the actual cached Xodus library in their fixture.

[The detail report](../../scripts/bidi-memory-detail.xml) has snapshot-specific addresses. Use [the binder](../../scripts/bind_bidi_memory_detail.py) with the selected heap's owner CSV to bind it to another captured heap. [The container and IR report](../../scripts/bidi-memory-containers-ir.xml) measures named-cache empty fields, current RAM tables and complete boundary statement counts. [The boundary detail report](../../scripts/bidi-memory-rule-statement-detail.xml) requires a current rule-statement address. Their output archives and per-heap bound XML files remain beside each dump. Complete CSV histogram totals sum across class loaders; equal class names do not identify one class-loader instance.

`memory-summary.json` stores exact bytes, full actor lifecycle counts, class counts and source/input manifests. `summarize_memory.py` recreates it from the CSV tables. `validation/memory-provenance.json` stores the MAT version, tool heaps, patch and query hashes. The 16 driver cases and 21 Python checks pass after the dump-hook build; logs and driver XML remain in `validation`. Python compilation and `git diff --check` also pass.
