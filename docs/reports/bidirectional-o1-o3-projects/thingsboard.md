# ThingsBoard: O1–O3 fixed-workload test

The fresh optimized run stops with a memory-guard OOM in forward run 1 at limit 1. Main completes and exports
14 distinct sink identities. The optimized run exports a valid, empty SARIF file: **all 14 baseline identities
are absent**, with no added identities. The finding and fixed-resource gates are not met. This is an incomplete
analysis and loss of reported results, not evidence of a completed semantic regression at those 14 sites.

## Inputs and execution

Main is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with the opt-in guard heap hook. The actual optimized run
manifest records `18688f5f7f7e8c41ee1ec4c58e5498b14aa6136a`. That revision adds documentation only after
`b0a2448c0`; the core runtime source diff between these revisions is empty. The manifest patch digests are
`a46bdb253cc3b46f875221a14e9363412103068fdec916b2f66bbe3adef9d24e` on main and
`2c412682fd44b8f76d04a6435d130f68d8b439a642d242a13abfd9bea651f5c7` on the optimized side.

Both invoke Gradle `runProjectAnalyzer` directly, with the same fixed model at
`opentaint-test/opentaint-test-thingsboard/opentaint-project/project.yaml`. Its YAML SHA-256 is
`1246cb43621245e0ef971a51052b54a56abd60725860f44824f00f34d57b58f3`; the model source head is
`b31176433e8065ae4cb2a285bcd5e03fce9014f5`. The test-system revision is
`bbdbcc5fd35d976d93348760ea60561e4670f68c`. The prepared input audit confirms fixed class, dependency, source
and toolchain equality. This review does not rebuild inputs or run full source hashes.

The configured limits are **12 GiB heap, 1,080 seconds for IFDS and 1,200 seconds for the outer task**. Both
actual process captures identify the Temurin 17 executable, `-Xmx12g`, storage batch 2,000 and disabled
multi-release loading. Both use the matching built-in rules, warning/error severity filter and Semgrep-style
exported IDs. There is no configured custom rule/pass/dataflow directory for this project. Bidi alone enables
the selected engine and optional form statistics. Its log confirms run 1 starts at field limit 1.

The fixed class-manifest digest is `1419f5df77aabc4df9907acc54050f05abe60f52551237eeacb0c3b0cfa71e35`;
the dependency-manifest digest is `afc84c4f2d1d14fc958561d79a52de8d161e34160cfac1ee805ce57551287792`.
These are the saved input checks, not new hashes taken during analysis.

## Strict finding differences

| Measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Unique strict sink identities | 14 | 0 |
| Exported occurrences | 14 | 0 |
| Baseline identities absent | — | 14 |
| Added identities | — | 0 |
| Internal report states | — | 0 CONFIRMED; 0 DEMAND |
| Terminal status | OK | OOM |

An independent parse of both raw SARIF files confirms these counts using exported rule ID, sink fingerprint
and full method signature. The optimized file has one valid run and an empty result array. This is not a
missing/invalid output file or a sink-location rejection. The logs record zero attempted trace results and no
invalid vulnerability location. Main has 14 traces, including four generated traces and ten Simple traces,
with no generation failures.

All strict differences remain in the comparison artifact. The missing baseline families are:

| Exported rule | Missing identities | Baseline sites |
| --- | ---: | --- |
| `java.security.unrestricted-request-mapping` | 4 | Three `WebConfig` methods and `ThingsboardErrorResponseHandler.handleError` |
| `java.security.java-jwt-decode-without-verify` | 3 | `RestClient.setTokenInfo`, decode calls at lines 334–336 |
| `java.security.stacktrace-printing-in-error-message` | 2 | `MigratorTool.main` line 55 and `TranslationPruner.main` line 100 |
| `java.security.unvalidated-redirect#sink#0` | 2 | `WebConfig.redirectSwagger` line 37 and `AdminController.codeProcessingUrl` line 483 |
| `java.security.hardcoded-password` | 1 | `TbRabbitMqNodeConfiguration.defaultConfiguration`, `setPassword("guest")`, line 50 |
| `java.security.unsafe-reflection` | 1 | `ReflectionUtils.getAnnotationProperty`, `Class.forName`, line 27 |
| `java.security.cookie-issecure-false` | 1 | `CookieUtils.addCookie`, line 61 |

### Cause of the empty accepted report

Both prescans finish with no recorded prescan error. The actual driver starts forward run 1, then receives
`OOM`. There is no `BIDI_BARRIER_END` and no subsequent run.

[IterationDriver.runOnce](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/IterationDriver.kt:68)
returns immediately for a result that is not COMPLETE. Confirmation and `report.add` occur only in the skipped
barrier. [ReportBuilder](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/Report.kt:30)
starts with empty state and accepts only complete forward results. Since the first run did not finish, no prior
completed report exists to retain. [RunResult](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunStatus.kt:12)
also exposes no run actors or witnesses for an incomplete result.

This gives a concrete chain from resource failure to empty accepted report and zero exports. The artifact does
not show whether a particular missing sink had already been reached in partial run state, or whether its
propagation would fail in a completed run. Do not erase the 14 observed report losses, or label the empty output
a successful no-finding scan.

## Time and memory pressure

| Phase or measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Prescan | 321.765 s | 270.896 s |
| Full main scan / bidi run 1 engine | 80.353 s | 305.235 s, incomplete |
| Total IFDS | 402.129 s | 578.671 s |
| Gradle command wall time | 486.470 s | 684.808 s |
| Harness wall time | 486.473 s | 684.809 s |
| Peak process RSS | 14,506,450,944 B | 15,018,401,792 B |
| Analyzer / Gradle exit codes | 0 / 0 | 253 / 1 |

Run 1 starts at `00:49:41.015` and ends `OOM / ABNORMAL` at `00:54:46.250`. Its selected timeout still has
806.569 seconds at startup, so this is a memory stop, not exhaustion of that timeout. Main completes under the
same fixed settings. A shorter or incomplete phase is not a performance improvement.

The latest captured progress has **189,716,231 handled events and 16,097,186 in flight**. The memory manager
repeatedly clears soft references and briefly returns below its threshold before pressure rises again. This is
an observed workload/backlog symptom, not proof that the queue or a particular store dominates retained memory.
RSS includes native memory and is not live Java heap size.

## Heap-owner analysis and completed profiles

The guard captures `guard-live.hprof` before cancellation at reported heap use **11,681,061,472 bytes**, above
its **11,596,411,699-byte** threshold. Capture starts at `00:54:17.540` and ends at `00:54:46.223`, an interval of
**28.683 seconds** included in the run's engine clock. That forced-GC/dump interval is diagnostic work, not an
ordinary engine timing control. The file is **18,711,415,183 bytes**; serialized HPROF size is not retained heap
size. The analyzer memory-guard status does not by itself prove a JVM allocation failure.

Fresh MAT parsing indexes 324,449,635 reachable objects with 11,397,235,120 shallow bytes. It removes
30,300 unreachable objects with 1,433,280 bytes and uses no object-discard options. The complete histogram
contains 16,082,584 queue-entry objects with 643,303,360 shallow bytes, and 16,088,748 `RequestIn` messages
with 386,129,952 shallow bytes. This agrees with substantial pending request work. These class bodies do not
include referenced fact graphs; their counts alone do not prove which queue retains them or whether
pending requests repeat the same logical key.

The follow-up inventory confirms all 76,943 actors are current forward actors with live runners, contexts
and work stores. All 562 runners are open. The manager is still admitting work and has no terminal status
at capture: 16,097,169 pending reservations and 189,716,227 handled events. The snapshot precedes cancellation.
The 562 queues contain 16,082,582 entries and 45,116,783 allocated array cells. Two further queue-entry objects
are outside these queue sizes, so the whole-class count is not identical to queued entries.

| Selected structure | Bytes in retained union |
| --- | ---: |
| Standing request stores | 2,885,461,840 |
| Queue roots | 1,139,704,728 |
| Queue backing-array roots | 1,210,199,096 |
| Actor edge stores | 378,314,608 |
| Callee subscription state | 606,903,488 |

The complete standing-store inventory has 22,317,268 distinct seen request pairs across 76,943 stores.
Of these stores, 14,991 are nonempty; the largest holds 40,734 pairs. Their positional indexes have 23,168
base roots. This is standing-request cardinality, separate from pending queue messages.

These unions overlap and use different root sets; they must not be added. Standing requests are a larger
retained owner than queue roots in this capture. Queue capacity also exceeds occupied entries. This identifies
request storage and pending delivery as core memory targets; it does not establish the saving of a proposed
change.

A bounded sample reads 48 explicit cells from three large queues. It finds 48 distinct request messages but
43 structural keys: one full method/premise/mark key repeats three times, and another repeats four times.
These are concrete duplicate pending requests, with no measured whole-queue duplication rate.
[RunMethodLocalServices](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt:53)
sends each request-climb result. [RequestStore](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/RequestStore.kt:27)
deduplicates when the receiving actor handles the request. The sample supports examination of duplicate work
before that receiver check; it does not authorize a protocol change outside O1–O3.

All 940,423 form slots lack a cached nonnull referent, verified by complete class and complement histograms.
Managed-reference categories are disabled with zero registered wrappers. The snapshot has zero active form
pins. The recipe/cache-shell retained union is still 684,989,280 bytes, while actor predicate-state union is
5,563,896 bytes. These sets overlap other owners. Recipe storage has a longer lifetime than compiled payloads;
empty slot referents do not imply zero recipe memory.

The concrete persistent vulnerability store is present. The whole heap contains 19 vulnerability keys and
21 witnesses, despite the empty accepted report. Every witness belongs to run 1 and is unconfirmed.
Decoded rule, method and instruction fields match nine baseline call/assignment sites. Four more baseline
rule/method pairs have candidates at appended method-entry boundaries, with different instruction indexes
from main. The `AdminController.codeProcessingUrl` redirect at index 85 has no matching stored key.
These partial observations do not change the primary comparison's 14 missing identities or prove a complete
propagation result. One unsafe-reflection witness has a nonzero argument premise and DEMAND layer, so a
stored candidate does not imply a confirmable normal finding.

Optional counters are cumulative: 1,286,827 forward builds, zero reversals, 14,455 uncached builds and
7,287,665 hits. Final zero pins after teardown differ from live-snapshot measurements. These totals do not
establish a cache leak or an exclusive saving. Earlier ThingsBoard heaps are not proof for this run.

The completed 30-second normal bidi CPU capture falls within forward run 1 and has 30,981 samples. GC stacks
account for 33.36%; monitor/spin stacks account for 24.00%; cache-payload stacks account for 6.34%. These
inclusive groups overlap. The monitor stacks include message routing and handler completion. This identifies
coordination work as a measured CPU cost; it does not establish queue retention or authorize a deferred rewrite.
Optional shape-reader stacks account for 0.14%, without an allocation-byte measurement.

Main's requested normal capture spans the end of its 80.353-second full phase and output/termination; the
profiler reports `Process exited`. It is not a matched 30-second full-scan comparison. Continuous JFR is
present on both sides. The completed phase review checks seven windows against the run logs, with duration
differences below 2 ms. Main's full phase has 82.16 GB of sampled allocation weight; bidi run 1 has
680.15 GB. These estimates measure allocation traffic, not live or retained memory.

The largest measured core allocation path in run 1 is the priority-queue comparator: 243.58 GB, or 35.81%
of that phase's sampled allocation weight. [EventQueue](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EventQueue.kt:8)
uses generic `compareBy` selectors for an integer rank and two `Long` keys. Boxed `Long` and generic
comparison stacks account for the same traffic and overlap this attribution. A primitive comparator is
a concrete performance candidate; this review makes no retained-memory claim or change outside O1–O3.

GC pause fields summed by collection start are 4.145 seconds in main's full phase and 55.555 seconds in
bidi run 1. Boundary-spanning collections limit exact pause placement. The whole bidi recording includes
20 `System.gc` full collections and one dump-initiated collection. The final 30-second run-1 window overlaps
the heap dump and has only ten Java CPU samples; it is not a normal-workload comparison.

## Evidence and remaining gates

The evidence root is `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`:

- `fixed-projects/thingsboard/{main,bidi}`: actual run manifests, summaries, logs, process captures, resource
  samples, raw SARIF and profiling captures; the bidi folder has the fresh guard dump.
- `fixed-projects/comparison/thingsboard.json`: all 14 strict differences, full locations, completeness/status
  checks and absence of location/prescan rejection signals.
- `input-audit/pre-run-committed`: the original fixed input manifests and configured resource limits.
- `fixed-projects/thingsboard/performance-review`: completed CPU/counter/cleanup and JFR phase review,
  with allocation/GC rankings, timing checks and a reproducible reader.
- `heap-analysis/thingsboard-bidi`: fresh MAT command, archived reports and complete census review in
  `overview-review`; strong owner and candidate-key follow-up is separate from the primary scan.
- `input-audit/post-run-check/combined-endpoint-equality.json`: 41 captured content datasets pass the
  post-run comparison. Two copied toolchains use a later partial recheck; six dangling link targets are
  outside the original regular-file scope and remain separately unavailable.

Main SARIF SHA-256 is `06f69df24968b4268f0d99f49a0b1ae4cf9e70ecccd6cc2799e40be1e717ae1e`;
bidi SARIF SHA-256 is `98057251a76bc213a713dbbab63840472b7e5418e06bc90144cae5995531c501`.
Fixed-budget completion, complete finding preservation, fresh heap-owner diagnosis and O3/O1 checkpoint
attribution remain open. IR and O4–O12 remain outside the selected implementation.
