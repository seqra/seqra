# N1 and N2: fixed-project performance report

Status: implementation checks, eight fresh scans, strict comparisons, profiles and OOM heap reviews complete.
Scan date: 13 October 2026. The report uses the committed N1/N2 implementation.

N1 and N2 pass the code checks. OWASP completes and retains every main sink identity. Conductor and
ThingsBoard still stop for memory use. Stirling-PDF still times out. The fixed-project completion gate
does not pass. ThingsBoard has 14 strict missing identities; PDF has three. These losses remain visible.
N3 is deferred and is excluded from this implementation and the next iteration.

## Revisions, inputs and checks

The scan revision is `61fd97acd792f2200b2e4a5746185d740d87712b`. The last runtime change is
`314349718`; earlier implementation commits are `bb3d9f26a`, `d95dc63d2` and `e9202f6f8`.
Main is frozen at `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, verified before launch. Its only runtime
patch is the same opt-in memory-guard capture hook. No runtime, rule or benchmark change occurred during
the eight scans. The [implementation report](bidirectional-n1-n2-implementation-20261013.md) records
the source changes and code checks.

All scans use Gradle `runProjectAnalyzer` directly, Temurin 17, storage batch 2,000 and disabled
multi-release loading. Each pair uses the same model and analysis arguments. OWASP uses 8 GiB and
1,000 seconds for IFDS. Conductor and PDF use 8 GiB and 1,080 seconds. ThingsBoard uses 12 GiB and
1,080 seconds. Every outer budget is 1,200 seconds. Only these four configurations were run.
OWASP uses the disclosed cached release-8 input with the CI analysis configuration; this is not a fresh
CI autobuild. Captured model hashes and source heads match. The earlier 77-check input audit is historical
provenance, not a new full input rehash.

The common suite passes 1,342 tests; the JVM suite passes 377. There are no failures, errors or skips.
Go, SAST and root consumer test compilation and analyzer classes pass. The reporting tools pass 36 tests.
The Lean build passes; its axiom audit checks 19,466 declarations and finds zero unexpected axioms.
The new proofs cover cumulative encoding and checked E6 binding laws. They do not certify the complete
Kotlin engine or close the stated AP transfer and implementation-simulation obligations.

The evidence bundle is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/n1-n2/20261013T223640Z
```

`launch.json`, `exit.json`, project manifests and actual process records bind the scans. The batch exits
0 after all eight terminal summaries. Individual bidi OOM/timeout statuses are analysis failures.
`comparison`, `input-audit`, `counter-review`, `normal-cpu-review`, `profile-review` and `heap-review`
retain the distinct evidence types. Before launch, the cleanup manifest records removal of 60 previous
HPROF/JFR/collapsed artifacts, totaling 170,043,973,440 bytes. Saved old logs, reports and CSVs remain.

## Latest reached and accepted iterations

F means forward; B means backward. Each row states the positive field limit. Run-1 starts at limit 1;
no limit-0 run exists. Main uses the legacy analyzer and has no bidi iteration.

| Project | Latest reached | Last complete engine | Last accepted forward report | Strict main / bidi identities | Missing / added |
| --- | --- | --- | --- | ---: | ---: |
| OWASP | run-3 F/L3 COMPLETE | run-3 F/L3 | run-3 F/L3 | 4,112 / 4,112 | 0 / 0 |
| Conductor | run-2 B/L2 OOM | run-1 F/L1 | run-1 F/L1 | 7 / 300 | 0 / 293 |
| ThingsBoard | run-1 F/L1 OOM | None | None | 14 / 0 | 14 / 0 |
| Stirling-PDF | run-4 B/L4 TIMEOUT | run-3 F/L3 | run-3 F/L3 | 17 / 56 | 3 / 42 |

Acceptance uses the compact forward frontier written after the report update. `BIDI_BARRIER_END` alone
is insufficient: the driver also writes it from a `finally` block. Each profile and heap has its own
capture iteration below; the final reached iteration does not label an earlier capture.

## Findings and losses

The strict key is rule ID, sink fingerprint and full method signature. Physical and logical location sets
are checked separately. All eight SARIF files are valid. No paired-input mismatch or prescan error is
recorded. Every exported identity, multiplicity and raw location equals the prior rollback output.
That equality is historical context; the primary comparison uses fresh main and current reports.

**OWASP.** All 4,112 main identities and their location sets remain. Current exports 9,179 CONFIRMED
occurrences, versus 4,112 main occurrences. The 5,067 extra occurrences repeat existing sink identities
at 2,420 sites. There are no new strict sink sites, removed occurrences or export gaps.

**Conductor.** All seven main identities remain, with unchanged locations and multiplicities. The 293
additional sites comprise 287 SQL-injection, three path-traversal, two GraalJS-injection and one reflection
site. Run-1 accepts three CONFIRMED and 298 DEMAND attempts. One NATS `createDispatcher` lambda
location is rejected during export, leaving 300 results from 301 attempts. The complete rejection line
is saved. It removes none of the seven matched main identities. The run-2 OOM prevents complete
refinement, so the additions are not certified final false positives.

**ThingsBoard.** All 14 main identities are absent from the valid empty report. The first engine stops
with OOM and never reaches the first report barrier. The driver therefore has no accepted report to
export. This is the common proven output-loss cause. Fresh per-site propagation was not observed;
do not infer 14 distinct transfer defects or reuse old witness counts as current evidence. Each missing
rule, method, fingerprint and source span is saved in `input-audit/thingsboard-comparison/missing-sites.json`.

**Stirling-PDF.** The three strict losses remain two XSS return locations and one command-injection site.
The XSS methods are `ConvertEmlToPDF.convertEmlToPdf` and `PipelineController.handleData`. Their current
same-rule/method findings point to the last physical return, rather than the earlier return reported by
main. Current exit witnesses use synthetic boundary nodes and Simple traces. The direct SARIF location
resolver chooses the last return when no trace return is available. This source path explains the observed
location/fingerprint changes; it does not prove equal flows. Keep both strict losses.

The third missing identity is `ProcessExecutor.runCommandWithOutputHandling(List, File)`,
`ProcessBuilder.<init>(command)`, instruction 8, fallback line 183. Current has no same-rule/method
candidate. The earlier [PDF investigation](bidirectional-o1-o3-projects/Stirling-PDF.md) observes exact
receiver-mark removal by a later source return rewriter and removal of coarse command demands after
completed F3. The relevant rule and call/return-rewriter implementation remain unchanged. This supplies
a source-backed loss mechanism, not a fresh full caller-chain trace or a proof of the earliest current
loss. It follows the specified source-override order; changing that policy is outside N1/N2.
The B4 timeout retains the accepted F3 report. It alone does not explain this missing command site.
Current exports 128 occurrences: 40 CONFIRMED and 88 DEMAND. Its 42 added strict sites need complete
refinement and site review before they can be classified as false positives.

## Performance and run-1 cost

All times below are seconds. IFDS includes prescan; overall wall time also includes Gradle, reporting
and wrapper monitoring. The table uses `summary.wall_seconds`, not `command_wall_seconds`.
The guard dump intervals are already included in engine time: 17.177 seconds for Conductor run-2 and
41.847 seconds for ThingsBoard run-1. Do not add them again. RSS includes native memory and is not
the Java heap used size.

| Project | Main IFDS / overall wall | Bidi IFDS / overall wall | Main / bidi peak RSS, GiB | Bidi outcome |
| --- | ---: | ---: | ---: | --- |
| OWASP | 86.790 / 142.269 | 385.590 / 441.791 | 6.579 / 9.364 | Complete |
| Conductor | 77.540 / 136.328 | 253.628 / 312.734 | 9.439 / 9.557 | OOM |
| ThingsBoard | 205.795 / 254.548 | 998.731 / 1,049.098 | 13.435 / 13.853 | OOM |
| Stirling-PDF | 34.304 / 89.424 | 1,080.036 / 1,126.473 | 7.832 / 9.437 | Timeout |

| Project | run-1 F/L1 engine / barrier | run-2 B/L2 engine / barrier | run-3 F/L3 engine / barrier | run-4 B/L4 engine |
| --- | ---: | ---: | ---: | ---: |
| OWASP | 229.428 / 2.149 | 59.147 / 0.061 | 50.201 / 0.096 | — |
| Conductor | 50.902 / 0.772 | 161.988 / —, OOM | — | — |
| ThingsBoard | 757.905 / —, OOM | — | — | — |
| Stirling-PDF | 39.156 / 0.410 | 32.554 / 0.099 | 76.847 / 12.382 | 891.444, TIMEOUT |

The completed OWASP analysis is slower than fresh main. The other three bidi scans still fail their
resource gates. Compared with the prior rollback samples, run-1 durations increase and the terminal
iterations do not advance. PDF has less budget left for B4 because its earlier phases take longer.
Conductor and ThingsBoard process more events before OOM. These are single
shared-host samples with CPU/JFR captures, form counters and new N1/N2 counters enabled on bidi.
Host pressure and prescan times also differ. They do not establish an isolated N1/N2 speedup or slowdown
percentage. A later OOM with more work is not a completed-analysis performance improvement.

## Profiles: actual capture phases

Each successful normal asynchronous capture lasts 30 seconds and starts about one minute after the first
engine begins. Only PDF reaches the requested near-limit capture. All eight scans have continuous JFR.
Main full scans finish before the normal window. Their asynchronous windows are unavailable, not zero
CPU cost. No asynchronous window overlaps a logged guard dump.

| Bidi project / capture | Actual capture iteration | Samples | GC | Trie subtraction | Call/statement stages | Form build / normalization union |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OWASP normal | run-1 F/L1 | 6,450 | 20.62% | 0.91% | 32.43% | 0.31% |
| Conductor normal | run-2 B/L2 | 19,011 | 53.55% | 5.20% | 27.15% | 0.05% |
| ThingsBoard normal | run-1 F/L1 | 23,970 | 29.04% | 0.14% | 4.15% | 9.94% |
| PDF normal | run-2 B/L2 and run-3 F/L3, plus barrier | 15,447 | 36.32% | 4.71% | 22.72% | 8.85% |
| PDF near-limit | run-4 B/L4 | 11,550 | 75.26% | 6.67% | 13.25% | 0.00% sampled |

The PDF normal window intersects B2 for about 11.989 seconds and F3 for 17.911 seconds. It is not a
pure F3 profile. The groups count each matching stack once, overlap, and cannot be added. GC includes
native/concurrent GC samples and does not mean wall pause time. No matching sample is not proof of
no execution. ThingsBoard queue comparisons occur in 6.10% of normal CPU weights. This identifies a
comparison cost, not the benefit of mailbox or scheduler changes.

All eight continuous JFR exports are terminal with no export error. Their GC duration and pause fields
are available. The figures below cover each whole recording, including startup, prescan and reporting.
GB means decimal gigabytes. Allocation weights estimate traffic; they are not retained memory. Java
execution samples omit native GC, so they do not have the same denominator as asynchronous CPU samples.

| Project / latest reached | Main / bidi sampled allocation, GB | Main / bidi GC pause sum, s | Main / bidi Java execution samples |
| --- | ---: | ---: | ---: |
| OWASP / run-3 F/L3 | 221.908 / 600.529 | 3.059 / 13.086 | 11,960 / 16,634 |
| Conductor / run-2 B/L2 | 201.703 / 632.743 | 5.401 / 23.738 | 14,146 / 28,339 |
| ThingsBoard / run-1 F/L1 | 592.252 / 1,782.868 | 11.795 / 188.522 | 43,772 / 87,646 |
| Stirling-PDF / run-4 B/L4 | 131.069 / 2,250.485 | 2.798 / 31.393 | 9,420 / 74,825 |

Main has no bidi iteration. Phase windows below use the actual engine start/end clocks, not the final
iteration or a presumed normal-window phase. Each named frame's share is inclusive of sampled allocation
stacks containing that frame. Frames overlap; their shares cannot be added or read as removable work.

| Bidi phase | Sampled allocation, GB | Observed allocation frame / phase share |
| --- | ---: | --- |
| OWASP run-1 F/L1 | 312.599 | `ApOps.applyCompiledEdge` 14.86%; `filterFacts` 9.89% |
| OWASP run-2 B/L2 | 70.590 | `SignedConditionNormalizer.Compiler.normalize` 17.53% |
| OWASP run-3 F/L3 | 65.592 | `SignedConditionNormalizer.Compiler.normalize` 16.68% |
| Conductor run-1 F/L1 | 119.886 | `EventQueue.heap$lambda$2` 8.27% |
| Conductor run-2 B/L2 | 343.365 | `TrieOpsKt.subtract` 11.52%; `leafViews` 10.12% |
| ThingsBoard run-1 F/L1 | 1,233.552 | `EventQueue.heap$lambda$2` 46.65% |
| PDF run-1 F/L1 | 115.921 | `JIRGraphImpl.<init>` 11.77% |
| PDF run-2 B/L2 | 100.838 | `TrieOpsKt.subtract` 11.23% |
| PDF run-3 F/L3 | 204.153 | `TrieOpsKt.subtract` 11.26% |
| PDF run-4 B/L4 | 1,684.572 | `TrieOpsKt.subtract` 23.33%; `cleanChain` 12.83% |

**ThingsBoard.** The exact queue-selector frame carries 575,457,242,024 bytes of sampled F1 allocation
weight. `Long` objects carry 577,377,957,160 bytes, or 32.38% of the whole recording. The primitive
captured priority fields are boxed by `compareBy`. A direct primitive comparator is a separate allocation
candidate that can preserve queue order and layout. These measurements do not select N3 mailboxes or
scheduler changes, nor establish a net wall-time benefit. Its normal CPU share is smaller because the
normal 30-second window and whole-engine Java/allocation profiles have different scopes.

**Stirling-PDF.** B4 subtraction carries 392,986,934,376 bytes of sampled allocation weight. The handled
event counter remains at 1,686,411 for several minutes with two reservations in flight, while JFR records
AP/stage execution and allocation. This identifies expensive ongoing handler work. The late 75.26% GC
CPU share does not mean 75.26% wall pause time; the whole recording's measured pause sum is 31.393 s.
Investigate exact work avoidance and scratch allocation before changing the required fact domain.

**OWASP.** Raw-rule validation appears in 15.65% of B2 and 18.31% of F3 Java execution samples.
Normalization also consumes allocation in those phases. This supplies a cost signal for the optional
validation-cache and immutable-template proposals. It does not establish equal input identity reuse,
cache cardinality or net memory benefit. Neither utility is implemented here.

PDF F1 graph construction also appears in 25.39% of Java execution samples. IR implementation/storage
changes remain outside scope. Any future analyzer-side reuse would need a separate source and lifetime
analysis. No IR optimization is included in the new proposal.

## N1 and N2 counters

N1 measures logical admission and current capacity separately. ThingsBoard run-1 admits 41,889,395
members: 40,219,985 mark members in 107,231 rows and 1,669,410 position members in singleton rows.
Mean mark-row size is 375.078. Of its mark rows, 83.14% contain 256–1,023 marks. This is not a measure
of mark-ID density, equal set contents or equal admission order. It records 371,564,835 duplicate
admission attempts, 419,063,266 climbs and 514,630 answers. These attempts do not measure pending
transport duplicates or permit removal of required marks.

Its END census has 346,368,959 primitive capacity cells and 832,108,655 cumulative allocated cells.
The allocated count includes replaced arrays, not retained memory. N1 counters retire through actor
freeze. An incomplete engine releases runner ownership without freezing each actor, so these END
`live*` values are unretired failed-cohort accounting, not proof of post-teardown reachability. The
pre-cancel heap must establish actual retained owners. N2 directory counters retire on runner release;
their zero END values are not the earlier guard census.

| Project / run | N2 accepted deltas | New ordinary groups | New zero-only entries | New index postings |
| --- | ---: | ---: | ---: | ---: |
| OWASP run-1 F/L1 | 160,482 | 48,751 | 0 | 50,790 |
| OWASP run-2 B/L2 | 19,797 | 668 | 10,205 | 1,339 |
| OWASP run-3 F/L3 | 11,809 | 11,529 | 0 | 11,627 |
| Conductor run-1 F/L1 | 882,882 | 534,674 | 0 | 1,073,634 |
| Conductor run-2 B/L2 | 2,981,248 | 1,028,460 | 33,855 | 7,332,719 |
| ThingsBoard run-1 F/L1 | 1,884,873 | 1,582,880 | 0 | 2,616,913 |
| Stirling-PDF run-1 F/L1 | 124,956 | 81,003 | 0 | 128,277 |
| Stirling-PDF run-2 B/L2 | 160,673 | 53,137 | 17,923 | 118,469 |
| Stirling-PDF run-3 F/L3 | 885,628 | 708,533 | 0 | 1,301,626 |
| Stirling-PDF run-4 B/L4 | 2,106,337 | 210,968 | 17,711 | 1,884,451 |

Accepted deltas are cumulative traffic, not retained history. Created
groups/postings are per-run differences of cumulative totals. Zero-only admission is separate from
ordinary cumulative groups. E6 bindings, avoided versions and combinations are zero in OWASP, Conductor
and ThingsBoard. Replay peaks include executing callbacks and span the whole analysis; they are not
per-run physical queue high-water marks.

PDF has 106 E6 binding insertions, 80 avoided repeats and 16 complete combinations in F1; F3 has
96 insertions, no avoided repeats and 39 combinations. Backward increments are zero. An avoided
repeat can be an exact replay/delivery duplicate; this count does not isolate root-version removal or
measure byte savings. Its B4 ordinary subscription traffic averages 9.900 accepted updates per new group.

## Fresh OOM heaps

The Conductor guard captures **run-2 B/L2**, before cancellation. Its serialized file is 11,755,919,925
bytes. The ThingsBoard guard captures **run-1 F/L1**, before cancellation; its file is 17,459,123,319
bytes. Serialized file size is not retained heap size. No fresh OWASP or PDF guard is triggered.

Both fresh MAT owner reports are terminal and reviewed. Retained unions overlap and cannot be added or treated as recoverable
savings. Failed/empty queries must retain their availability state. Compare bytes with logical counts
and captured progress; two OOM captures with different event prefixes do not isolate a change's saving.

### Conductor: run-2 backward, limit 2

The fresh MAT report passes its archive/hash/capture checks. The complete class histogram has
222,534,544 reachable objects and 7,647,197,248 bytes. The parser reads 222,565,545 objects and removes
31,001 unreachable objects, totaling 1,456,560 bytes. There is no object-discard sampling.
The actual manager is admitting in run-2/L2, with 3,805,855 handled events and four pending reservations.
It has 219 runners/jobs and 17,525 active backward actors. The full class census proves that no
RequestStore or E6 NdBinding/NdSummaryJoin object remains. Failed empty selections do not prove that fact;
the complete histogram does.

| Selected root set | Retained-union bytes |
| --- | ---: |
| Active actors, runners and subscription managers | 2,876,202,872 |
| Active subscription managers | 772,413,800 |
| Cumulative callee directories | 771,316,120 |
| Their sole positional indexes | 500,285,640 |
| Their exact accumulators | 257,404,784 |
| Their current entry arrays and retained children | 175,450,608 |
| Their zero-only directories | 6,621,616 |
| All accumulator Entry graphs, including actor links | 359,131,984 |
| Priority-queue backing arrays and retained children | 701,480 |

The live N2 census has 1,028,460 cumulative ordinary groups, 33,855 zero-only entries and 7,332,719
index postings. Its 3,864,130 accepted deltas include traffic from F1 and B2; they are not a retained
delta history. One replay callback is pending/executing. The whole analysis replay peak is ten.
Global accumulator Entry count is 2,056,920 because actor raw links use the same class. It is not the
subscription-only count. The only three queued priority entries are nonzero Work; 219 backing arrays
have 701,312 shallow bytes. This capture does not have a large pending queue.

The sole subscription index retains 7,332,719 linked-map entries with 293,308,760 shallow bytes,
6,135,274 boxed Integers with 98,164,384 bytes, and 294,884 hash-table arrays with 62,803,168 bytes.
This identifies current posting overhead after historical parts and the second index are removed.
The active-owner union also contains 5,098,747 AP trie nodes (285,529,832 shallow bytes) and the same
number of child arrays (214,794,344 bytes). Their child cardinality and duplicate-content rate are not
yet measured; these class counts do not establish a single-child saving.

The report preserves 26 available, four available-empty, two empty-selection and 24 failed queries.
Most failures select no instances; they stay unavailable. Scalar and owner queries used above are
available, and complete class absence is verified independently. The top-40 dominator table is an
example list, not a complete owner total. It includes a SpringRuleProvider retaining 409,393,896 bytes
and a JIRMethodEntries owner retaining 208,345,496 bytes. Their removable duplicate data is unknown.

The prior rollback B2 capture had 679,558 cumulative groups and 2,723,788 handled events, with a
1,538,287,888-byte subscription-manager union. The new capture has more groups/progress and a smaller
subscription union, consistent with removal of history and one index. The overall live heap still reaches
the same guard range. These different prefixes do not establish isolated savings or completion speed.
Required AP state, current postings, other analyzer owners and out-of-scope IR state still consume memory.

### ThingsBoard: run-1 forward, limit 1

The complete histogram has 290,559,281 reachable objects and 11,323,803,576 bytes. MAT reads
290,589,870 objects and removes 30,589 unreachable objects, totaling 1,429,280 bytes. No sampling or
object discard is used. The actual manager is admitting at run-1/L1 with 409,346,297 handled events,
11,673,428 pending reservations and 562 runners/jobs. All 76,943 active actors are forward.

| Selected root set / array family | Bytes | Scope |
| --- | ---: | --- |
| Active actors, runners and subscription managers | 5,594,549,616 | Retained union |
| All grouped request stores | 2,485,698,088 | Retained union |
| Request row directories | 1,073,866,024 | Retained union |
| Request positional indexes | 401,469,648 | Retained union |
| Request origin directories | 7,325,320 | Retained union |
| Request member-key lookup arrays | 375,127,576 | Complete shallow array census |
| Request member-handle lookup arrays | 375,127,576 | Complete shallow array census |
| Global member-to-row columns | 203,681,600 | Complete shallow array census |
| Global member-to-key columns | 203,681,600 | Complete shallow array census |
| All RequestIntLane arrays | 718,859,688 | Complete shallow array census; includes global columns |
| Active subscription managers | 524,574,760 | Retained union |
| Their sole positional indexes | 267,940,960 | Retained union |
| Their exact accumulators | 210,777,360 | Retained union |
| EventQueue owners | 1,080,746,296 | Retained union |
| Current priority-queue backing-array roots | 1,131,092,256 | Retained union |
| Current priority-queue backing arrays | 322,797,936 | Complete shallow array census |

These rows overlap. The all-lane query selects every RequestIntLane array, not only bucket encounter
lanes; it includes both global member columns and temporary lanes. Do not add it to the named columns. A union selected from
array roots has different retention boundaries from its owner-object union. The bundle does not establish
the alternate strong-root path. Neither queue union is a whole recoverable saving.

At the capture, N1 has 41,888,414 admitted members: 40,219,876 mark members and 1,668,538 position members.
There are 107,231 mark rows and 1,668,538 position rows. Every position row is singleton. The snapshot
has 346,362,996 current primitive capacity cells. END counters include another 981 admissions after the
capture and remain unretired failed-cohort accounting. They are not a replacement for this live heap.
The 107,228 key arrays and 107,228 handle arrays each include the shared empty array. Singleton
RequestMembers objects have 80,090,016 shallow bytes. The row key arrays are a concrete target for
handles-only lookup; removing them does not remove logical requests or encounter order.

N2 has 1,582,870 current groups, no zero-only entries, 2,616,887 postings and no pending replay callbacks.
Its 1,884,861 accepted updates are traffic, not retained history. E6 classes are absent in the complete
histogram. Six failed queries stay unavailable; the report also has 46 available, three available-empty
and one empty-selection output.

All 562 queue census rows are exported and match the complete EventQueue class count. They contain
11,171,499 physical entries and 80,696,998 backing cells. Of the queues, 512 are empty but retain
23,726,369 cells. The global Entry rank queries count 11,169,577 ordinary and 1,924 nonzero Work
objects. Their sum, 11,171,501, equals the complete global Entry class census and proves that no rank-0
Entry remains. Two Entry objects are outside current queue slots; their root paths are not queried.
Pending reservations and RunEvent objects also include channel/executing state and are separate
quantities. Large unused queue capacity and a backlog persist. No global duplicate-key
rate or mailbox/scheduler benefit is measured. N3 remains excluded.

The top-40 dominator examples include a SpringRuleProvider retaining 1,324,601,560 bytes and a
JIRMethodEntries owner retaining 684,946,440 bytes. Their redundant immutable data and required rebuild
metadata need a separate census before template/recipe changes. The largest listed IR storage owner
remains outside the optimization scope.

The prior rollback run-1 capture had 24,981,269 seen pairs, about 197.7 million handled events and a
3,095,473,728-byte request-store union. The current live capture has about 41.9 million members and
409.3 million handled events with a smaller request union. This supports a more compact representation
at these observed prefixes. Different method/request mixes prevent an isolated percentage saving.
The accepted report remains empty because run-1 still fails. Better representation alone has not
removed the large logical relation or made the project complete.

## Next iteration

The [new proposal](../design/bidirectional-core-optimization-20261014.md) uses these fresh owner and
profile measurements. Earlier priority tables are historical. N3 remains deferred. It distinguishes
processed standing state, accepted cumulative state and pending transport, and explains each candidate's
current behavior, new behavior and expected benefit. IR storage remains outside the scope.
Managed-reference manager and weak identity validation-cache changes remain optional unimplemented
candidates; this iteration does not introduce them.
