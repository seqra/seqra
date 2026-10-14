# Analyzer core: proposals after N1/N2

Status: proposal complete. All candidates below are unselected and unimplemented. Rankings use the fresh
Conductor/ThingsBoard retained owners and the completed four-project JFR review. N3 remains excluded and deferred.
Date: 14 October 2026.

This document proposes the next changes after the N1/N2 implementation. It replaces the priorities in the
[13 October proposal](bidirectional-core-optimization-20261013.md).
The [selected N1/N2 design](bidirectional-n1-n3-implementation-proposal.md) remains the contract for the current
implementation. Do not use the old proposal's owner bytes as current bytes or an isolated saving.

Keep NNF source and sink conditions, signed folding including `P AND NOT P -> false`, full occurrence/proof
identities, and the cleaner normalization from `saloed/cleaner-action-normalisation`. IR objects, IR storage,
IR tables and IR providers remain outside scope. The C1–C13 candidates below keep request transport,
scheduling, mailboxes and queue layout unchanged. Following the later request investigation, the user
has requested a separate [mark-set and processing-barrier proposal](bidirectional-request-set-barriers-20261014.md).
That proposal is also unselected. It does not activate the older [N3 design](bidirectional-n3-deferred-proposal.md)
or its broader queue-layout and fairness changes.

## Current evidence and limits

The fresh four-project batch uses runtime `61fd97acd792f2200b2e4a5746185d740d87712b` and test-model revision
`bbdbcc5fd35d976d93348760ea60561e4670f68c`. Its artifacts are under
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/n1-n2/20261013T223640Z`.
Use that batch's `projects/*/{main,bidi}/manifest.json`, terminal `summary.json`, `comparison/*.json`,
`counter-review`, `normal-cpu-review` and subsequent reviewed heap/profile outputs. Earlier reports are
historical context only. The output counts below are from the fresh strict comparison, not from an older run.

| Project | Latest reached iteration | Last complete engine | Last accepted forward report | Strict missing / added unique sinks | Terminal result |
| --- | --- | --- | --- | ---: | --- |
| OWASP | run-3, forward, limit 3 | run-3 | run-3 | 0 / 0 | OK; 4,112 main/current unique sinks; 9,179 current occurrences |
| Conductor | run-2, backward, limit 2 | run-1 | run-1 | 0 / 293 | OOM; 3 CONFIRMED and 298 DEMAND report entries |
| ThingsBoard | run-1, forward, limit 1 | None | None | 14 / 0 | OOM; no accepted report |
| Stirling-PDF | run-4, backward, limit 4 | run-3 | run-3 | 3 / 42 | TIMEOUT; 40 CONFIRMED and 88 DEMAND report entries |

An iteration is reached on its actual `BIDI_RUN_START`. A COMPLETE engine and acceptance of a forward
report are separate observations. A later incomplete engine preserves the last accepted report. A
`BIDI_BARRIER_END` marker alone does not prove acceptance. Keep every strict location difference; an
incomplete DEMAND report does not establish final false positives. The PDF command-rule difference remains
a finding issue, independent of an optimization selection. Do not relax that difference to claim success.

Keep the recorded input/model/rule settings, Temurin 17, storage batch 2,000 and disabled multi-release
loading. Use Gradle `runProjectAnalyzer` directly. OWASP keeps its recorded 8 GiB/1,000-second configuration;
Conductor and PDF keep 8 GiB/1,080 seconds; ThingsBoard keeps 12 GiB/1,080 seconds. Run 1 starts at limit 1.
There is no limit-0 run.

The bounded saved async CPU review reports the following inclusive sample groups. These are CPU samples,
not wall pauses, allocation bytes or retained memory. Groups overlap. Do not add their shares. Unsampled
frames do not prove absence of work.

| Capture | Actual phase | Samples | GC | Trie subtraction | Stage | Form build / normalization |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OWASP normal | run-1 | 6,450 | 20.62% | 0.91% | 32.43% | 0.31% |
| Conductor normal | run-2 | 19,011 | 53.55% | 5.20% | 27.15% | 0.05% |
| ThingsBoard normal | run-1 | 23,970 | 29.04% | 0.14% | 4.15% | 9.94% |
| PDF normal | Mixed: about 11.989 s in run-2 and 17.911 s in run-3, plus about 0.1 s of barrier | 15,447 | 36.32% | 4.71% | 22.72% | 8.85% |
| PDF near limit | run-4 | 11,550 | 75.26% | 6.67% | 13.25% | 0.00% |

Source: `normal-cpu-review/README.md` and `evidence.json` in the fresh batch. These samples justify
investigation of staging and AP operations; they do not prove that GC time or a full inclusive stage cost
is removable. Queue comparison is 6.10% in the ThingsBoard normal capture. C13 proposes only primitive
comparison with the same existing queue order; transport, scheduling and layout remain excluded.
No old 48-entry duplicate sample is used as current evidence.

### Completed JFR allocation and CPU evidence

All eight main/current JFR exports are terminal and their saved `profile-summary.json` outputs have no
export errors. The table below uses the current-side continuous recordings. GB is decimal sampled
allocation weight, not exact allocated or retained bytes. Summed GC pause duration covers the whole
continuous recording, including guard activity where present; it is not exclusive analysis time.
Missing-pause-duration count is zero in these exports. Native async GC sample shares above are a different
measurement and must not be converted to pause duration.

| Project | Continuous sampled allocation, GB | Summed GC pauses, seconds | Selected engine sampled allocation, GB |
| --- | ---: | ---: | --- |
| OWASP | 600.529 | 13.086 | run-1: 312.599; run-2: 70.590; run-3: 65.592 |
| Conductor | 632.743 | 23.738 | run-1: 119.886; run-2: 343.365 |
| ThingsBoard | 1,782.868 | 188.522 | run-1: 1,233.552 |
| Stirling-PDF | 2,250.485 | 31.393 | run-4: 1,684.572 |

In ThingsBoard run-1, the exact `EventQueue.heap$lambda$2` frame accounts for 575.457 GB, or 46.65% of the
engine's sampled allocation weight. The whole recording's Long-class weight is 577.378 GB, or 32.38%;
these have different scopes and cannot be added. The run-1 Java CPU inclusive `compareBy` share is 15.35%.
Conductor run-1 also has 9.918 GB/8.27% under the same queue frame. These direct source/frame observations
support C13 without a scheduler amendment or a claim that the logical event count can be reduced.

Conductor run-2 allocation includes subtraction at 11.52%, leaf views at 10.12%, and the mergeAddDelta
lambda at 9.97%. PDF run-4 subtraction accounts for 392.987 GB/23.33%; cleanChain is 12.83%, applyStage
12.55%, and FlowGroup.subtractSubsumed 10.59%. OWASP run-1 includes applyCompiledEdge at 14.86% and
filterFacts at 9.89%. These individual inclusive frames overlap. They prioritize C1/C2 investigation;
they are not additive costs, recoverable percentages or evidence that a new blanket memo is needed.

OWASP run-2/run-3 condition normalization has 17.53%/16.68% of engine sampled allocation; validateJava
has 15.65%/18.31% of the Java CPU samples. Validation cost is observed, but reuse of the same immutable
input identity, owner cardinality and net cache retention are still unknown. C10 remains optional.
PDF run-1 has 25.39% Java CPU and 11.77% allocation under JIRGraphImpl construction. The IR implementation
remains excluded. This is a diagnostic reason to inspect analyzer-owned lookup frequency, not permission
to change IR storage/providers or an established graph-cache saving.

Sources: `projects/*/bidi/profile-summary.json` in the fresh batch and the
[fresh performance report](../reports/bidirectional-n1-n2-performance-20261014.md).

Current N2 counters describe the new representation, not the removed part history. PDF run-4 records
about 2.106 million accepted updates, 210,968 created ordinary groups and 1.884 million index postings;
ordinary updates per created group are 9.900. Its forward E6 counters are 106 new/80 avoided bindings/16
combinations in run-1 and 96/0/39 in run-3. Avoided bindings include exact repeat deliveries. They are not
an isolated count of avoided root versions or a retained-byte estimate. CDF/TB MAT identifies current
cumulative owners below; it does not prove equal-content sharing or PDF's retained E6 combination bytes.

### Fresh Conductor backward owner evidence

The guard capture belongs to **run-2, backward, limit 2**, inside the engine. The snapshot starts at
`23:09:25.518` and ends at `23:09:42.695`, before the engine OOM marker. MAT's complete class histogram
contains 222,534,544 objects and 7,647,197,248 bytes. The raw count is 222,565,545 objects; 31,001 objects
and 1,456,560 bytes are removed from that count during parsing. Do not use raw HPROF file size as live bytes.

| Current owner union | Retained bytes | Scope |
| --- | ---: | --- |
| Active actors, runners and subscriptions | 2,876,202,872 | Shared union, including current actor stores |
| Active subscription managers | 772,413,800 | All 219 managers |
| Cumulative callee directories | 771,316,120 | All 18,750 directories |
| Callee sole positional indexes | 500,285,640 | Current generic index; no historical part index |
| Callee exact accumulators | 257,404,784 | Current exact groups |
| Callee current entry arrays | 175,450,608 | Subset of accumulator ownership |
| All exact-accumulator current AP graphs | 359,131,984 | Includes actor links as well as subscriptions |

These unions overlap. They cannot be added or treated as recoverable savings. The 500,285,640-byte sole
index is a fresh measured target for C11. The accumulator/AP owners motivate current-value and annotation
analysis; they do not prove equal-content sharing for C12. The global 2,056,920 accumulator Entry count
includes actor links and cannot be called the number of subscription groups.

Within the complete sole-index union, 7,332,719 LinkedHashMap entries use 293,308,760 bytes, 6,135,274 boxed
Integer handles use 98,164,384 bytes, and 294,884 hash backing arrays use 62,803,168 bytes. These distinct
class rows total 454,276,312 bytes inside the union; the complete 13-class index union contains no AP payload
classes. Current live directory scalars independently agree with 1,028,460 ordinary groups, 33,855 zero-only
entries and 7,332,719 postings. Top-twenty capacity rows are examples, not global capacities. The current
entry AP node/accessor/child-array subtotal is 135,193,896 bytes within its 175,450,608-byte union.

The active actors/runners/subscriptions union contains 14,861,774 linked-map entries (594,470,960 bytes),
12,456,674 boxed Integers (199,306,784 bytes), 5,098,747 TrieNodes (285,529,832 bytes), and 214,794,344 bytes
of child arrays. These include other owners and cannot all be called removable subscription overhead.
The two explicit Int-handle index consumers, CalleeSubscriptions and AddedFactStore, are the narrow C11
scope; other generic PathTrie callers remain unchanged.

The complete histogram has 17,525 active method actors, no RequestStore, and no NdBinding/NdSummaryJoin/KaryJoin
instances. Their absence is verified from the complete histogram, not from empty entity queries. There
are three physical queued entries across 219 queues; their backing-root union is 701,480 bytes. A small
queued census at one instant does not measure in-service or cumulative work, and it does not support a
Conductor mailbox optimization. N3 remains excluded.

Evidence: `heap-review/conductor/job-20261013T235245.129531Z/review/evidence.json`,
`n1-n2-review/evidence.json`, and `counter-review/conductor-heap-crosscheck/{README.md,evidence.json}` under
the fresh batch. The owner-report availability census includes 24 failed
queries, four available empty outputs and two empty selections. Preserve those states; failed/empty
entity queries do not establish numeric zero. The top-forty dominator examples include SpringRuleProvider
at 409,393,896 bytes and JIRMethodEntries at 208,345,496 bytes. Template-duplication frequency and individual
strong-root paths were not measured; these are not proven duplication or leak sizes.

### What the current request counters establish

ThingsBoard run-1 admits 40,219,985 mark memberships in 107,231 mark rows, and 1,669,410 position memberships
in 1,669,410 position rows. Each position row is therefore singleton. Subtracting these rows from the
recorded histogram gives the exact mark-row bins below. The mean is 375.08 marks per row.

| Mark members per row | Rows |
| --- | ---: |
| 1–63 | 3,040 |
| 64–127 | 14,141 |
| 128–255 | 893 |
| 256–511 | 72,616 |
| 512–1,023 | 16,541 |

The 256–1,023 bins contain 83.14% of mark rows. They do not prove dense manager mark IDs, equal mark sets,
equal encounter order, or a common producer. The recorded logical relation has 41,889,395 memberships,
346,368,959 current primitive capacity cells and 832,108,655 cumulative allocated primitive cells.
The current promotion rules imply at least 112,332,780 cells in the two mark-row lookup arrays alone;
global member columns, encounter lanes and origin lookup add further cells. This is a source-derived
capacity bound, not retained-byte evidence or a predicted saving.

[RequestStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/RequestStore.kt)
stores the global member-to-row and member-to-key columns and a bucket encounter lane. Position memberships
also enter a position lane.
[RequestStorage](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/RequestStorage.kt)
uses two per-row lookup arrays, small-array promotion at nine members, and hash doubling at 75% load.
Append lanes grow by approximately 1.5 and copy the old array. Allocated cells include discarded growth
arrays; they are not simultaneously retained cells. Counts omit object headers and child hash-table storage.

Incomplete runs do not freeze actors into the returned `RunResult`:
[RunManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunManager.kt)
clears their runner ownership after join. The N1 census retires through local-services freeze, which is
used for COMPLETE actors. Thus the failed ThingsBoard END live counters describe its unreleased census,
not a proof that those arrays remain strongly reachable after teardown. Use the pre-cancellation heap
for actual owner/byte claims.

Hundreds of requested marks are permitted by the current semantics. Policy starts one STAR FLOW initial
per nonzero base. NNF source/sink leaves request concrete marks from FLOW. Selected-mark FLOW cleaners
request their mark in run 1 even if that mark is already excluded; F75 retains the original premise.
The JVM return rewriter creates actions for each relevant mark at each applicable action position, using
`allRelevant=true`. Semgrep rule metadata includes the marks accessed by both automaton states and optional
global state, not only the new source's output mark. An abstract added link climbs each requested mark
outside its own exclusions to the full caller initial.

TB records 419,063,266 climbs, 514,630 answers and 371,564,835 duplicate admission attempts. This proves a
large cascade, but it does not attribute original production to cleaners versus sources/sinks. Do not
remove `allRelevant`, narrow valid mark scopes, retire answered standing requests, or suppress F75
production using the cleaner path or exclusions. See
[Clean](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Clean.kt),
[Demand](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt),
[local services](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt),
[JVM call forms](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt),
and [generated rule marks](../../core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/TaintRuleGenerationCtx.kt).

### Fresh ThingsBoard forward owner evidence

The reviewed guard heap belongs to **run-1, forward, limit 1**, before cancellation. Its complete reachable
histogram contains 290,559,281 objects and 11,323,803,576 bytes; 30,589 objects/1,429,280 bytes were removed
from 290,589,870 raw objects during parsing. Source, counters and heap quantities remain separate.

| Current owner/component | Bytes | Measurement |
| --- | ---: | --- |
| Grouped request stores | 2,485,698,088 | Retained union |
| Request row directories | 1,073,866,024 | Retained union, overlapping the request union |
| Request positional indexes | 401,469,648 | Retained union, overlapping the request union |
| Row member-key arrays | 375,127,576 | Complete shallow array census |
| Row member-handle arrays | 375,127,576 | Complete shallow array census |
| Global member-row column | 203,681,600 | Complete shallow array census |
| Global member-key column | 203,681,600 | Complete shallow array census |
| All RequestIntLane arrays | 718,859,688 | Complete shallow array census; includes global columns, row/bucket lanes and temporary lanes |
| Singleton RequestMembers objects | 80,090,016 | 1,668,542 objects, including four singleton mark rows |
| N2 subscription managers | 524,574,760 | Retained union |
| N2 sole positional indexes | 267,940,960 | Retained union, overlapping the manager union |

The 375,127,576-byte key-array census is C3's measured duplication target. It is not the full request union
or a promised net saving after replacement overhead. The `All RequestIntLane arrays` query is global;
do not call it bucket-only storage or add it to its named column subsets. The two global columns total
407,363,200 bytes against 335,107,312 bytes of logical integer payload for the 41,888,414 captured members.
Their approximately 72.26 MB difference includes headers, alignment and spare capacity. Paging can target
that difference, before adding page/directory overhead; it cannot remove their required logical payload.
Uniform large pages for the roughly 1.7 million singleton positions/buckets would regress memory.

The heap's member scalars are 40,219,876 mark and 1,668,538 position memberships, slightly before the END
counts above. Current N2 has 1,582,870 ordinary groups and 2,616,887 postings. Use each capture's actual
progress; do not force it to agree with a later terminal census or an older failed heap.

The complete queue census has 562 rows, 11,171,499 physical entries and 80,696,998 backing cells. The manager
has 409,346,297 handled reservations and 11,673,428 pending reservations. Physical queue entries and pending
reservations differ. The queue backing-root union is 1,131,092,256 bytes and the EventQueue union is
1,080,746,296 bytes; these overlap. The persistent backlog does not select N3, which remains deferred.
Complete class census independently verifies no E6 binding/join instances in this capture.

Evidence: `heap-review/thingsboard/job-20261014T000149.070994Z/{review,n1-n2-review}/evidence.json` and
`counter-review/thingsboard-heap-crosscheck/{README.md,evidence.json}` under the fresh batch.
Query availability is 46 available, six failed, three available empty and one empty selection.
Failures remain unavailable. Top-forty dominator examples include SpringRuleProvider at 1,324,601,560 bytes
and JIRMethodEntries at 684,946,440 bytes. Duplicate frequency and exact inner-owner/root paths are unmeasured.
Do not attribute the whole provider chain to Spring-generated rules or treat those owner sizes as savings.

## Rank by expected retained-memory impact

This ranking estimates net impact at the measured current owners, after replacement storage. It is not a
promise of freed bytes, OOM avoidance or equivalent-progress savings. Prefer a proved work reduction where
it removes required computation without losing effects. Here, equal-set/content reuse and cover-precheck
hit rates remain unknown, so the clearest retained targets are exact representation duplication. Do not
rank an unproved algorithm above them solely because its source owner is large.

| Rank | Candidates | Expected impact | Measured basis and decision limit |
| --- | --- | --- | --- |
| 1 | C3/C4: request columns and small rows | High in TB; C3 first, C4 measured substeps | Duplicated key arrays: 375,127,576 B. C4 targets singleton member objects: 80,090,016 B and about 72.26 MB global-column excess before replacement/page overhead. Do not add the request-owner unions or promise the full component sizes. |
| 2 | C11: primitive Int-handle indexes | High in CDF, useful in TB | CDF callee-only structural rows: 454,276,312 B inside a 500,285,640 B index; TB callee index: 267,940,960 B. Required primitive postings/order/hash capacity replace part of this. Actor raw-link index scope is explicit but its exclusive saving is unmeasured. |
| 3 | C7: single-child AP and small groups | Medium, conditional | CDF active-owner union contains 5,098,747 TrieNodes and 214,794,344 B of child arrays. One-child frequency and small-group sizes must be measured; whole graph storage is required and is not removable. |
| 4 | C6: immutable descriptors/templates/actions/recipes | Potentially medium to high, conditional | TB top-owner examples include a 1,324,601,560 B provider chain and 684,946,440 B method-entry owner. CDF examples are 409,393,896/208,345,496 B. Duplicate content, exclusive bytes and necessary rebuild data remain unknown. |
| 5 | C5: common request sets or ordered blocks | Conditional | Large rows are proven, but equal mark contents/order/contiguous blocks are not. Membership sharing retains O(members) encounter state; stronger compression needs a separate exact encoding proof. |
| 6 | C8: residual reversed blocks | Low or unknown | NNF already avoids source/sink cube inventories; no new substantial residual owner is established. |
| Unranked | C9/C10: optional manager/cache utilities | Unknown; may add retained bookkeeping | No isolated wrapper-growth or immutable-identity-reuse measurement supports a net retained saving. They remain optional and unimplemented. |

C1/C2/C12/C13 primarily target CPU and allocation traffic. No separate substantial retained saving is
established for them. Their evaluation order follows the next table rather than a heap-size ranking.

## CPU/allocation track

| Priority | Candidate | Current versus new | Expected benefit and limit |
| --- | --- | --- | --- |
| 1 | C13: primitive queue comparison | Replace generic selector comparison with primitive lexicographic comparison of the same captured rank/steps/sequence | Highest-confidence narrow allocation fix: TB run-1 exact frame 575.457 GB/46.65%. Same logical events/order/accounting; no promise that all that sampled weight becomes a runtime saving. |
| 2 | C1: AP work avoidance and scratch | Prove cheap no-cover certificates first; otherwise retain exact traversal and allocate scratch only after a change | PDF B4 subtraction 392.987 GB/23.33%, plus CDF subtraction/merge/view frames. A certificate hit rate and summary cost are required before choosing it. Current memoization already exists. |
| 3 | C12: match equal current values once | Group captured equal AP query inputs, select once, fan out to every full binding | Algorithmic avoidance of repeated matching; current sharing frequency and comparison cost are unmeasured. |
| 4 | C2/C6: specific stage/action/normalization reuse | Prove reuse for a complete pure input/state key; preserve every effectful action and occurrence | Fresh stage and normalization costs support investigation, but repeated-input frequency and safe reduction are conditional. No blanket cleaner deduplication. |
| Optional | C10: validation cache | Cache only proved immutable/context-independent validation inputs | OWASP validation CPU is measured; same-identity reuse and net cache cost are not. No automatic selection. |

Evaluate algorithmic avoidance before tuning the same AP walk where its certificate can be proved. The
queue comparator remains a separate narrow technical fix because its boxing cost and unchanged ordering
have direct evidence. N3 transport/fairness/mailboxes/layout are not part of either ranking.

## Current versus proposed behavior

| ID | Current | Proposed | Why it could improve | Classification / present limit |
| --- | --- | --- | --- | --- |
| C1 | Trie merge/filter/subtract walks build scratch containers, including unchanged results | Skip exact-certified no-cover walks; otherwise allocate scratch after the first change with existing memo semantics | Avoid unnecessary visits/allocation while reusing unchanged subtrees | Work/allocation reduction; fresh PDF/CDF paths measured, certificate hit rate/removable share unknown |
| C2 | Stages and ordered cleaner chains can repeat AP selection/transfer work | Prove and specialize specific repeated pure operations within one invocation | Avoid repeated transformations, not merely smaller lists | Algorithmic, conditional; no general action-chain reduction is justified |
| C3 | A request row duplicates keys in lookup arrays and global columns | Hash admission handles and read their keys from the global key column | Remove a duplicated primitive column without changing logical membership or order | Representation; TB key-array target 375,127,576 B, extra indirect-read cost pending |
| C4 | Append lanes repeatedly copy growing arrays; singleton position rows retain general row objects | Inline small lanes/position memberships; page only measured large ordinal lanes | Reduce growth traffic, spare capacity and small-object overhead | Representation; TB global-column excess about 72.26 MB and singleton-member objects 80,090,016 B, before replacement overhead |
| C5 | Equal mark sets can have separate lookup storage and different encounter histories | Share only immutable exact membership data; optionally compress proven equal ordered blocks | Reduce repeated storage when common sets/blocks actually occur | Conditional storage algorithm; it does not remove required logical actions |
| C6 | Generic predicate descriptors, bound rules, action payloads and rebuild recipes have repeated immutable data | Specialize constants/singletons and share owned immutable templates/payloads within reclaimable owners | Reduce surviving descriptors and rebuild traffic | Representation/rebuild work; current exclusive owners and reuse rates pending |
| C7 | Remaining fact groups and single-child trie nodes use general containers | Lazy empty/singleton containers and compact single-child nodes | Reduce headers and arrays for common small cases | Representation; current cardinality census required |
| C8 | Custom/legacy reversed conjunction blocks can materialize independent branches | A compact residual transfer block for a measured owner | Avoid remaining branch inventory while preserving independent demand branches | Conditional; never restore source/sink cubes to create this target |
| C9 | Original managed-reference bookkeeping is used | Separately reviewed lifecycle bookkeeping rewrite | Potentially bound wrapper bookkeeping and registration races | Optional and unimplemented; benefit unknown |
| C10 | Raw rule validation runs directly | Weak identity validation cache, only if repeated validation is expensive | Potentially avoid repeated validation | Optional and unimplemented; benefit unknown |
| C11 | Explicit Int-handle indexes use generic encounter-ordered boxed ID sets | A narrow primitive ordered handle index for CalleeSubscriptions and AddedFactStore | Remove boxed postings/hash-entry overhead without restoring historical delta indexes | Representation; CDF callee structural subtotal 454,276,312 B inside 500,285,640 B index; actor-index exclusive size pending |
| C12 | Different full caller groups can select equal current AP values independently | Select an equal immutable current AP value once per complete query, then fan out to every captured full binding | Avoid repeated AP matching while retaining all caller-specific transfer effects | Algorithmic, conditional on measured equal current values and a full-key selection proof |
| C13 | EventQueue uses generic compareBy selectors for captured primitive fields | Use primitive rank, steps and sequence comparisons with the same PriorityQueue | Avoid comparator boxing while keeping exactly the same priority key/order | CPU/allocation representation fix, independent of deferred N3 |

### C1: avoid unchanged AP scratch and duplicate DAG work

The current trie code already shares unchanged outputs and has iterative memoized traversals. This
candidate concerns remaining work before that return, not a new general memo or a semantic rewrite.
For example, `mergeAccessors` allocates boxed accessor/child builders before knowing whether anything
changes. Filter frames build child lists even when the final output is the original node. Use copy on
first change, primitive scratch, and ownership-transfer factories for newly owned arrays where safe.
Public array getters must remain snapshots; arbitrary caller arrays must still be copied.

Prefer a proved no-overlap/no-cover certificate that skips a whole subtraction over a smaller scratch
container for the same walk. For example, a cheap conservative mark-disjoint certificate could bypass a
TaintGroup stored tree only when no leaf or inherited cover can remove any incoming mark. A path certificate
must account for ancestor ANY/must cover and E's first-step admission. An E mismatch alone does not exclude
stored exact coverage. Any summary used for this precheck needs a complete semantic key and ownership/cost
accounting; computing a new full fold for every precheck can cost more than the skipped walk. Measure check
hit rate and avoided visits. Keep the exact subtraction on uncertain cases. This is a conditional algorithmic
follow-up to [Groups](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Groups.kt),
not a blanket new memo: the current iterative traversal already has operation-local memoization.

The subtraction memo needs incoming node, stored node and inherited cover state. Merge needs a node
pair. In the current path-filter traversal, node plus filter identity identifies the inherited filter
state. A future filter can also depend on path, depth or another inherited state; include every such
semantic input rather than treating node/filter identity as a universal key. Preserve the current single
callback evaluation per state/edge. Do not reuse results across callbacks with observable effects.
Cache null removal explicitly. Preserve cancellation, iterative 32,768-depth walks, E first-step rules,
must versus normal EXACT separation, full metadata-change deltas, and T1–T4/T2′ union behavior. Do not
assume the total inclusive stage or subtraction profile weight is a saving. Source:
[TrieOps](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/TrieOps.kt),
[Filter](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Filter.kt),
and [Trie](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Trie.kt).

### C2: conditional stage and action work reduction

Measure which repeated operations use exactly the same immutable AP input, mode, edge/filter identity
and invocation state. A bounded invocation-local reuse of a pure result can avoid a second traversal.
Requests, triggers, source hits and callback effects must either be emitted exactly as before or exclude
that operation from reuse. Do not retain live actor/store values in a global result cache.

An ordered cleaner chain is not automatically idempotent. Equal syntax can have distinct occurrence IDs,
temporary names, filters, source-override effects and read/drop footprints. F75 requests remain tied to
the original premise. No blanket cleaner deduplication, set-based chain replacement or producer-side
request deduplication is included. For a specific action reduction, first prove equal full facts, ordered
effects, requests and support/trigger behavior at every required observation boundary. Native must-record
demotion stays late, after returned actions and filters. Source:
[RunMethodCallServices](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt).

### C3/C4: compact the current exact standing relation

A handles-only row hash can compare a candidate key with `memberKeys[handle]`, removing `RequestMembers.keys`.
This applies to both mark IDs and position-origin ordinals. Pass an owned column reference, not a capturing
key-getter/callback per row. Prepare required capacity before committing; decoded global metadata must be
available before publishing a membership handle in the lookup. Preserve cancellation/commit coherence and
prove that interrupted preparation cannot produce a seen member without its complete encounter record.
Retain stable admission ordinals, the global key column and current bucket lanes. Preserve the small-row
fallback, sparse/negative manager IDs, exact duplicate result, cancellation before membership commit,
and one snapshot horizon for the complete callback walk. Avoid one capturing callback object per row.
Measure CPU from the additional indirect read against the removed array capacity.
Retain the current initial lookup tiers: empty/singleton fields, linear two-through-eight members, then
sparse hashing from the ninth member, with current 75% load promotion as the first comparison baseline.
Re-tune a threshold only from measured capacity/probe/allocation results. No bitmap indexed by manager ID
or assumed mark density is needed.

Keep empty/singleton lanes inline and small lanes contiguous. Page only large global member columns or
dense bucket lanes above a measured threshold. Those lanes must keep random ordinal access and `countBefore`
without copying all prior cells on growth. Choose page sizes from the current distribution and include
every page header, last-page slack and directory capacity. A singleton position row can inline its
origin/member handle and promote on a second origin.
Do not collapse different initials that request the same position. Empty topology caused by cancellation
must not become a seen membership without an encounter record.
No uniform page size or threshold is chosen from the mean row count. Select a crossover only where the
measured old capacity/copy cost exceeds the complete replacement page/directory/header cost. Benchmark
empty, one, two, four, eight, nine, sixteen and sixty-four members plus both sides of each proposed page
boundary; include singleton-heavy and mixed-lane fixtures.

### C5: exact sharing does not establish Cartesian work avoidance

Equal unordered mark contents can share a membership lookup. They cannot share mark-to-admission ordinals
when their order differs. Interleaved origins also need separate bucket order. Thus a shared immutable
set plus row-local delta still needs the current O(members) encounter metadata unless a stronger ordered
encoding is proved. Do not claim that equal contents remove row-by-mark state wholesale.

A possible later encoding stores exact immutable ordered blocks and row-local differences. It must decode
the same full-origin relation and the same per-bucket order, with a captured prefix horizon that excludes
requests inserted by callbacks. Sharing requires actual common ordered blocks, not a guessed global mark
universe. Keep individual admission/action timing and both join orders. Interning every immutable set
version can create quadratic copying and retain history; use bounded owners and measured natural blocks.

Bulk/lazy propagation of mark sets would change admission/action timing and may change transport/accounting.
It is outside selected N1/N2 and deferred N3. Neither answered requests nor processed requests are pending
transport duplicates. This candidate does not authorize retiring the standing relation or a global seen set.

Before selection, measure distinct exact sets, ordered-set reuse, contiguous runs, mark-ID density,
separate mark/position size histograms, array-family capacity and original producer categories. Keep
census values numeric and bounded. Verify every encoded pair's Answer/Climb/None result for concrete and
excluded abstract added marks, static position exceptions, full caller keys/layers/calls, and re-entry.

### C11/C12: remaining cumulative-subscription work and storage

N2 removed permanent delta-part history and its index. The current
[CalleeSubscriptions](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/CalleeSubscriptions.kt)
still uses one generic `PathTrie<Int>`, as does the indexed raw-link wrapper
[AddedFactStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/AddedFacts.kt).
Each geometry node in
[PathTrie](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/PathTrie.kt)
owns a `LinkedHashSet`, and queries collect boxed IDs before deduplicating them in another encounter-ordered
set. Primitive specialization can remove these wrappers without changing the current one-index encoding.
Fresh Conductor MAT measures the complete sole-index union at 500,285,640 bytes. That union includes
topology and other required storage; its full size is not a predicted saving. Use the detailed class
breakdown to isolate boxed IDs, set entries, capacity and topology before selecting the larger-node layout.

C11 uses a narrow Int-handle index for these two explicit consumers, with empty/singleton primitive fields,
linear ordered handles for two-through-eight members, and an ordered primitive lane plus sparse lookup
from the ninth member as an initial benchmark choice. Begin the sparse table at sixteen slots and grow
at 75% load, then validate the crossover against the actual posting-degree distribution. Query deduplication
uses primitive membership plus first-encounter order; do not sort IDs and thereby change candidate order.
Preserve geometry traversal, node-local insertion order and query
first-encounter deduplication. Capture the same complete handle list and every immutable cumulative value
before the first subscription callback. Keep the raw-link wrapper's current handle and snapshot timing
as well; do not silently turn its sequence into subscription P6 timing. Keep cancellation within traversal,
snapshot and yield. Do not change other generic PathTrie callers, zero-only policy, full admitted group keys,
delayed fresh-delta replay or P6 timing.
Compare every prefix/extension query's ordered handles and captured values with the current index,
including one handle at multiple positions, growth, duplicate insertion and callback re-entry. Count
capacity and singleton distribution before choosing the larger-node hash representation.

C12 factors matching of equal current immutable AP content across distinct full caller groups. The
initial narrow form groups only the captured candidates for one delivery/query. Its complete selection
key includes current AP content and annotations, publication/member initial, mode and ordinary versus
zero-only branch. Root identity alone is insufficient: base, kind, layer, E and mark exclusions can change
selection. Shared root identity with equal full metadata can provide a cheap key; structural grouping
needs a measured benefit after its comparison/hash cost. Equal geometry alone may share the candidate
scan but does not establish equal matched facts.

Compute a pure AP selection once for each proved-equal key, then invoke the current applier once for
every original captured full caller binding, in its existing candidate order. Retain callee/full caller
method context, premise, caller layer, link layer and call statement. E6 combinations, support witnesses,
source hits, record crossings and returned effects still belong to those bindings. This is not a transfer
deduplication or a reduction in caller contexts. Keep full-match guards and per-binding cancellation.

The equivalence gate states that selecting the shared value and fanning it out yields exactly the same
ordered binding/result pairs as separate selection of each original snapshot. Test unequal annotations,
equal geometry with unequal contents, normal/must/EXACT, run-1/restricted, zero-only, multiple full caller
references and layers, and callbacks that grow a group after candidate capture. Native/transient record
reads require their own complete selection key and proof before any extension beyond summary delivery.
Use invocation/query-local grouping; do not intern obsolete cumulative versions or retain them in a
global content registry. Measure equal current AP frequency, repeated selection work and fanout size
before selecting this algorithmic candidate.

### C13: primitive queue comparison without a scheduler change

[EventQueue](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EventQueue.kt)
uses `PriorityQueue(compareBy(rank, steps, sequence))`. The generic selectors box primitive keys while
the queue can compare them directly. Replace only its comparator: compare captured ranks with integer
comparison; if equal, compare captured steps with Long comparison; if equal, compare captured sequence
with Long comparison. Use comparisons, not subtraction, to avoid overflow. There is no size threshold;
the primitive comparator applies to the existing queue at every size.

Keep the same Entry objects and layout, PriorityQueue, signed lexicographic ordering, sequence behavior,
zero/ordinary/nonzero ranks and priority capture at admission. Do not read actor steps or zero-work state
again during comparison. Requeued Work still receives a fresh captured priority as before. Keep all
accept/send/complete reservations, channel intake, close/cancellation and join behavior unchanged.

Tests compare the old tuple ordering against the primitive ordering, including equal keys, signed Long
extremes, FIFO sequence ties, changing actor state after admission, and fresh Work requeue keys. Existing
scheduling/accounting controls must still prove the same poll order and reservation counts. A focused
allocation check should verify that comparison introduces no key boxes; a fixed workload gate measures
actual benefit. Do not rename it mailbox deduplication, fairness, bounded intake or N3 implementation.

### C6–C8: immutable data and small-container follow-ups

Constants and singleton NNF descriptors can use specialized storage without generic topology arrays or
base directories. Keep source/sink leaf order, occurrence IDs, actions, proof scopes and FLOW's complete
positive read footprint. Larger predicates remain NNF DAGs. Shared topology does not merge occurrences.

Bound-rule templates and action/path payloads can be shared after owned immutability is established.
Preserve normal versus relevant lookup policy, complete relevant mark scopes, whole raw-rule validation,
deterministic temporaries and independent rebuilds. Pool owners must permit form eviction; a global strong
registry of bound rules/actions would defeat it. Inspect recipe fields before removing them: provider,
method and rebuild ownership overlap and the whole recipe union is not removable.

Lazy fact-group and single-child trie storage must retain full group keys, independent layers, exclusion
and mark-exclusion annotations and NORMAL exact/must separation. Remaining reverse blocks must preserve
all independent backward branches and action order. No residual candidate is justified without a current
owner census. Do not present already implemented compact StatementSummary/CallPlan collections as new work.

### C9/C10: optional utilities remain unimplemented

The original `SoftReferenceManager` and direct validation remain current behavior. A manager rewrite is
not a prerequisite for managed method-form references or any candidate above. First measure ordinary
wrapper growth and registration races. A possible separate design uses a short lifecycle lock, detachable
identity registry and ReferenceQueue pruning; clear referents outside the lock and never call a form
builder or take a slot lock from cleanup. Preserve existing clients, thresholds, GC and cancellation.

A weak identity validation cache remains optional despite the measured OWASP validation CPU. Same-input
identity reuse and net retention are not measured. Cache only deeply immutable inputs; raw condition/action
lists are not immutable merely because their declared type is List. Prove that validation results and
warnings are independent of method, binding and policy. Otherwise include the complete validation
context/version in an owner-scoped identity key, or keep validation uncached. Preserve distinct occurrence
IDs and whole raw-rule validation before signed folding.

Keys, values, callbacks and diagnostics must not keep temporary bound rules alive. Define weak identity,
owner lifetime, bounded queue maintenance, failure/recomputation and warning deduplication. Values must
not point back to keys. Validate every raw action/literal before signed folding and avoid recursive
hashing/rendering of deep conditions. Neither utility has an established byte or speed benefit.

## Gates before selection and acceptance

Retain the reviewed fresh JFR and Conductor/TB data with each candidate's rank. Identify capture
run/direction/limit, guard timing, exact owner paths and query availability. Treat failed, blank or
incomplete aggregate queries as unavailable. Retained unions overlap; do not add them or call their full
size removable. Separate required current AP state from obsolete history, pending transport and recipes.

For each selected future change, state an exact encoding or work-refinement invariant first. Add a failing
independent control before the correction. Compare full request actions, cumulative selected transfers,
ordered witnesses/support headers, source hits, compressed seed requirements and complete closure in
both unit layouts. Include run-1 and restricted directions, re-entry/cancellation, zero, full E/mx,
must/EXACT, signed false folds and positive limits 1/2/3. Preserve local raises and P1–P6/E6 behavior.

Run the existing common/JVM suites and consumer checks, then the same four fixed workloads after each
selected optimization. Record exact revisions and input/configuration equality. Report latest reached,
last COMPLETE engine and last accepted forward report separately. Keep strict findings and investigate
all missing rows; do not certify an incomplete resource failure as a complete semantic comparison.
Measure run-1 time, phase CPU/allocation/GC, and any guard heap. Keep the final combined gate distinct
from per-change attribution. No alternative resource settings, scheduler experiment or IR optimization
is included in this proposal.
