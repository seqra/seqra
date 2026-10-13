# Conductor: O1–O3 fixed-workload test

Latest retest after rollback: runtime commit **38a400c0d**, manifest revision **bd6810823**.
Latest reached: **run-2, backward, limit 2, OOM**. Complete engine and accepted forward report: **run-1**.
All seven main identities remain; the incomplete report adds 293 identities. Run-1/2 engine times are
37.554/92.396 seconds. The new run-2 guard heap is 11,693,412,724 bytes. The fixed 8 GiB/1,080-second
configuration is unchanged. See the [latest retest record](../bidirectional-core-rollback-20261013.md).
The measurements below remain the historical pre-rollback record.

The new run-2 heap verifies a 1,538,287,888-byte active-subscription union, with 509,006,864 bytes in
delta-part indexes and 259,069,008 in added-key indexes. All 257,075 form slots lack cached referents.
See the latest retest record for query availability, complete counts and overlap limits.

Historical measurement at pre-rollback code `b0a2448c0`. Latest reached: **run-2, backward, limit 2,
OOM**. Last complete engine: **run-1**. Last accepted forward report: **run-1**.
The primary guard heap captures **run-2** before cancellation. Main has no bidi iteration. See the
[rollback retest record](../bidirectional-core-rollback-20261013.md) for revised-code validation.

The fresh optimized run stops with a memory-guard OOM in backward run 2. Its available SARIF contains all seven
main sink identities and 293 additional locations. The comparison is incomplete: the retained report has three
CONFIRMED entries and 298 DEMAND entries. These additions are not a set of final false positives. The fixed
resource gate fails. Fresh MAT results identify active backward subscription and actor state as large
core owners; managed form slots have no cached payload referents after cleanup.

## Inputs and execution

Main is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with the opt-in guard heap hook. The actual optimized
revision is `b0a2448c0af7906ed49d90f203dd5a2a1c881523`. Their run-manifest patch digests are
`a46bdb253cc3b46f875221a14e9363412103068fdec916b2f66bbe3adef9d24e` and
`9beff0e3d4e983caf282eb9147e37f4de365dc4d6408ac0d3c1aac8a1a090ff8`, respectively.

Both invoke Gradle `runProjectAnalyzer` directly. They use the same model at
`opentaint-test/opentaint-test-conductor/opentaint-project/project.yaml`, with SHA-256
`3457cf5a1b3c63e0710dfa50a9fbca1c4229f2726ea3276cc409e019515ee35d`. The model source head is
`c466eaf70f3ac16b915120dd651e91aae28451c1`; the test-system revision is
`bbdbcc5fd35d976d93348760ea60561e4670f68c`. The complete input audit records matching fixed class and dependency
manifests, source content and toolchain inputs. This review does not rebuild or change them.

The configuration is **8 GiB analyzer heap, 1,080-second IFDS timeout and 1,200-second outer limit**. Both process
captures identify `/usr/lib/jvm/temurin-17-jdk-amd64/bin/java`, storage batch 2,000 and disabled multi-release
loading. Both load built-in rules plus the configured Conductor rules, pass-through models and compiled Java
dataflow models; both filter severity to warning/error and use Semgrep-style exported IDs. Bidi alone enables
the selected engine and optional form statistics. Its first limit is 1.

Both sides use `projects/extensions/conductor/rules`, not the older staged rules copy. The configured rules
manifest digest is `28fbf2b85ecf2dfe69a44afbea57a9e335de02568b7ff051aedf3702f2d66eb6`; that copy differs from the
staged copy in `conductor-grpc-request-sources.yaml`, equally for both analyzer sides. The pass-through digest is
`6ccef26f9f3bb8e32d3ace47fcc98c2fb9b6768a8042faae103313b575dcd704`, and the 54-file compiled dataflow digest is
`82952196eabfdd47895c51fd4981ba9951940182edb9d1e65c4b2cbfe0119c1b`. The built-in rule trees also match.

## Findings and incomplete state

| Measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Unique strict sink identities | 7 | 300 |
| Exported occurrences | 7 | 300 |
| Main unique sinks absent | — | 0 |
| Added unique sinks | — | 293 |
| Internal report entries / SARIF attempts | 7 | 301 |
| Internal report states | — | 3 CONFIRMED; 298 DEMAND |
| Terminal status | OK | OOM |

An independent parse of the two raw SARIF files confirms these counts using displayed rule ID, sink fingerprint
and full method signature. There is one exported occurrence per strict identity on each side. This is not the
duplicate-row pattern seen in OWASP. The primary comparison preserves raw physical/logical locations and does
not use trace length as a finding oracle. No main sink is absent in the available output; this does not establish
complete optimized propagation or the final finding set after refinement.

All seven main output sites remain: two stacktrace sites, two GraalVM sites, and the SSRF,
configured gRPC-source and path-traversal sites. The additions are:

| Added exported rule | Unique locations | Location classes |
| --- | ---: | --- |
| `java.security.sql-injection` | 287 | 259 methods in 69 source paths; database calls and broad factory/queue calls |
| `java.security.graaljs-polyglot-code-injection` | 2 | `ScriptEvaluator.lambda$eval$0/1`, `Context.eval`, lines 302 and 339 |
| `java.security.path-traversal` | 3 | `DummyPayloadStorage.upload`: `File.exists`, `createNewFile` and output-stream construction, lines 71/74 |
| `java.security.unsafe-reflection` | 1 | `AnnotatedWorkflowSystemTask.execute`, reflective `invoke`, line 91 |

The SQL-labelled locations include `queryWithTransaction`, `execute`, `executeQuery` and `prepareStatement`.
They also include `TaskMapperContext.createTaskModel`, queue/channel creation and NATS `createDispatcher`.
Their exported rule label alone does not prove that each operation is a SQL execution sink. Completion of
refinement and examination of the bound rule conditions are needed before assigning an individual false-positive
cause. The full 293-location inventory is preserved in the comparison artifact, grouped by rule and path.

SARIF exports no per-row DEMAND/CONFIRMED or proof/context properties. The three CONFIRMED entries cannot be
assigned to particular exported rows from this file. At least **290 of the 293 additions are DEMAND** because
the entire internal report has only three CONFIRMED entries. Do not label every addition final or confirmed.

### One rejected virtual location

The report attempts 301 Simple results but exports only 300. The fresh log records one rejected SQL-injection
location: `%3 = %0.createDispatcher()` in virtual
`io.nats.client.Connection#createDispatcher(io.nats.client.MessageHandler)`, specialized for the
`NATSObservableQueue.subscribe` callback. The selected bound sink is
`java/security/sqli.yaml:sql-injection:sink:8`, whose condition reads the receiver mark. It is not one of the
seven main sink identities; this is an additional export gap, not an observed main-baseline loss.

The configured [NATS approximation](</drive-testcomp/opentaint-go-rules/opentaint-test/projects/extensions/conductor/dataflow-src/io.nats.client/com/example/approximations/io_nats_client/Connection.java:16>)
ends with `self.createDispatcher()`. Approximation bodies use enriched virtual methods. The
[location classifier](../../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/TraceMessageBuilder.kt:1132)
marks enriched virtual methods as generated, but its normal-location resolver maps boundaries and lambda
creation locations and returns null for this ordinary enriched-method statement.
[JirSarifGenerator](../../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/JirSarifGenerator.kt:215)
therefore returns no sink location. The Simple export supplies no trace-derived caller locations, and the
[common generator](../../../core/src/main/kotlin/org/opentaint/common/sast/sarif/SarifGenerator.kt:80)
logs the error and drops the result. This is a concrete generated-location adapter gap. It does not prove
that the unresolved SQL-labelled finding is valid. The `generationFailed=0` trace counter does not count this
later location rejection.

## Time and resource result

| Phase or measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Prescan | 63.369 s | 42.870 s |
| Full main scan / bidi driver | 49.627 s | 232.477 s, incomplete |
| Total IFDS | 112.999 s | 275.995 s |
| Gradle command wall time | 182.531 s | 343.847 s |
| Harness wall time | 182.540 s | 343.860 s |
| Peak process RSS | 9,784,729,600 B | 10,068,185,088 B |
| Recorded whole-JFR GC pause sum | 7.222 s | 38.560 s |
| Analyzer / Gradle exit codes | 0 / 0 | 253 / 1 |

| Bidi iteration | Direction | Limit | Engine | Barrier | Result |
| --- | --- | ---: | ---: | ---: | --- |
| 1 | Forward | 1 | 61.865 s | 1.263 s | COMPLETE |
| 2 | Backward | 2 | 169.330 s | Not run | OOM |

Run 2 ends `OOM / ABNORMAL`; no later forward refinement runs. The driver keeps the report from the completed
forward barrier, which explains the retained DEMAND entries. Neither prescan has a recorded error. The fixed
configuration was kept, but optimized analysis did not complete. An incomplete elapsed time is not a performance
improvement.

The recorded run-2 engine time includes the live-heap diagnostic: capture starts at `00:31:30.574` and ends at
`00:31:55.552`, a **24.978-second** interval that includes forced GC and dump work. Do not treat that interval as
ordinary engine work or subtract it to claim a completed comparison. RSS includes native memory; it is not the
live Java heap.

## Profile evidence and limits

This review reads the saved profile summary only; it performs no JFR export. The 30-second bidi normal capture
begins about 60 seconds after full-analysis start, near the end of the 61.865-second forward run. It spans the
forward tail, barrier and early backward run, so it is not a pure run-1 profile. Main finishes its full scan
before a full-scan 60–90-second window exists; its continuous JFR preserves the full 49.627-second phase.

The mixed bidi capture has 29,737 CPU samples. GC stacks account for 13,719 samples (46.13%), AP trie stacks
for 3,913 (13.16%) and cache-payload stacks for 2,212 (7.44%). Each family counts a matching stack once;
families overlap. These shares measure CPU work in that window, not wall pauses or the entire run. Optional
shape-reader stacks account for 280 samples (0.94%); their allocation bytes are not isolated by the saved
rankings. There is no same-bidi counters-off control.

Saved Java JFR samples in run 1 show equality/hash-table work; its sampled allocation ranking includes generic
edge application and boxed `Long` comparison values. Run 2 shows hash-table work; `TrieOps.subtract` has 10.76%
of sampled allocation weight, and the merge lambda has 10.05%. These are allocation traffic estimates and
overlapping stack shares, not retained owners. They do not authorize queue/trie optimization outside O1–O3.
The recorded whole-JFR GC pause sums include diagnostics. Profile percentages and single-pair timings are
observations under instrumentation, not a controlled throughput estimate.

Host available memory fell to 1.357 GiB during main and 2.137 GiB during bidi. Both processes incurred thousands
of major faults. This recorded memory pressure is another limit on the timing comparison.

## Fresh heap-owner analysis

The memory guard repeatedly reports usage above its 7,730,941,132-byte threshold. Before cancellation it captures
`guard-live.hprof` with reported heap usage 7,744,179,472 bytes. Capture finishes with an 11,693,829,182-byte file.
That file size is serialized HPROF size, not retained heap size. No JVM allocation-failure cause is inferred from
the analyzer's memory-guard OOM status.

Run 2 records 12 soft-reference cleanups and 11 recoveries. The first two recovered after-GC heaps are
4,722,136,720 and 6,635,586,000 bytes, with observed drops of 3,037,247,856 and 1,099,722,440 bytes. Later recovery
drops shrink to roughly 9.7 MB while the recovered heap approaches the guard threshold. Cleanup clears all
registered soft-reference managers and GC also collects ordinary dead objects. These are aggregate heap drops;
the portion attributable to method forms remains unmeasured.

MAT parses the fresh dump with a 20 GiB parser heap and no object-discard options. It indexes
220,919,989 reachable objects with 7,638,215,776 shallow bytes, after removing 30,752 unreachable objects
with 1,449,664 bytes. The following are individual retained owners. Their reachable graphs overlap;
these sizes must not be added as a whole-heap total.

| Owner | Retained bytes | Verified ownership |
| --- | ---: | --- |
| IR RAM storage | 2,264,721,800 | Existing storage graph; IR changes remain outside scope |
| Largest active subscription directory | 834,525,800 | `SubscriptionManager.byCallee`; handler and runner lead to worker 1 |
| Spring rule provider | 372,739,600 | Almost all retained by its base exit-rule provider; analyzer/main root |
| Largest active runner | 260,497,104 | Current backward actors and runner state |
| Analysis-wide method entries | 206,697,200 | Physical method recipes and cache shells; interpreter/main root |
| Current run manager | 89,039,384 | Mainly current summary storage |
| Persistent record store | 48,558,144 | Previous-run records shared with the driver/current run |

The complete lifecycle queries identify backward run 2 at limit 2. All 17,525 actors retain their current
runner, context and work stores. All 219 runners and 219 subscription managers belong to that run.
The subscription directories contain 18,750 callee entries. Their structures contain 2,086,288 accepted
delta parts, 703,732 merged exact groups and 33,855 zero-only references. The largest directory has 927
callee entries. These are active work structures before cancellation, rather than completed-run actors
left after freeze.

Complete retained-set queries give the following component measurements. Each query selects all named roots
in this snapshot and sums the shallow bytes in their retained union. The unions overlap; their sizes cannot
be added or treated as independent savings.

| Selected component | Bytes in retained union |
| --- | ---: |
| Active actors, runners and subscription managers together | 2,853,512,400 |
| Active subscription managers | 1,487,146,248 |
| Active method actors | 1,287,324,328 |
| Actor method-edge stores | 733,598,936 |
| Accepted subscription delta parts | 566,535,456 |
| Delta-part path indexes | 500,992,808 |
| Merged added-key indexes | 256,274,344 |
| Merged exact values | 114,537,144 |

These results place substantial core memory in active protocol and AP state after form cleanup. The
method-edge stores contain 87,874 grouped premises, 17,848 reach premises and 3,246,834 allocated group-array
reference cells. Those cells measure capacity, not live facts or bytes. Two map-count queries use a field
that does not exist on `ConcurrentHashMap`; their blank counts remain unavailable. The corresponding
retained-set queries are valid. No empty count is inferred from a blank cell. Independent unbounded map-entry
exports supply the missing counts separately, including 17,525 context recipes and 14,838 physical recipes.

The delta-part index union contains 7,264,648 linked-map entries with 290,585,920 shallow bytes, and
6,778,966 boxed integers with 108,463,456 shallow bytes. Accepted parts retain 3,733,964 trie nodes plus
their arrays, about 419.6 MB within that component union. The dense method-group arrays account for only
14,593,136 shallow bytes within the 733.6 MB edge-store union; this snapshot does not support calling those
arrays the main memory cause.

The underlying Semgrep rule-provider graph retains 372,739,560 bytes. It contains about 3.61 million
raw/resolved `ContainsMark` nodes and 907,424 exit-sink descriptors. These provider objects are separate
from compiled NNF predicates; they must not be counted as surviving compiled forms or assigned exclusively
to Spring-generated rules.

All 257,075 form slots lack a cached nonnull referent. This count comes from complete class and complement
histograms; the separate nonnull-slot retained-set query failed and is unavailable. Four payload objects
remain, with two populated payloads rooted in worker locals. They retain 1,880 and 65,952 bytes individually.
All four managed-reference categories are disabled and have zero registered wrappers. There are two compiled
predicates and no actor predicate stores in this backward snapshot. The compiled descriptor retained union is
1,952 bytes; the compact-container union is 250,880 bytes. These unions overlap other owners.

This confirms form cleanup and identifies substantial active protocol/AP state. It does not measure an
exclusive cache saving or prove a leak. Previous-run records and analysis-wide recipes have longer lifetimes
by design. The cumulative final
counters record 578,702 forward builds, 348,850 reversals and 18,966 uncached builds; these are rebuild totals,
not live objects. Earlier Conductor heaps and reports are not proof for this run.

## Checkpoint memory evidence

Both cumulative checkpoints stop in backward run 2 at limit 2. O3 and O3+O1 each retain all seven main
sink identities, with 300 unique exported identities and 301 internal entries. Each internal report has
three CONFIRMED and 298 DEMAND entries. These are incomplete refinement results.

| Retained union | O3 | O3+O1 |
| --- | ---: | ---: |
| Strong form cache arrays | 955,109,440 B | 1,080,360,544 B |
| Active method-edge stores | 583,962,520 B | 571,327,624 B |
| Callee subscription state | 954,585,968 B | 841,005,232 B |
| Compiled NNF source/sink descriptors | Not present | 147,350,352 B |

Both heaps contain 17,525 active backward actors and 219 runners. They have different event progress;
the unions overlap and do not measure isolated savings. O1 has no live forward predicate stores in this
backward snapshot. Its observed-proof counter zeros are unavailable measurements: this source checkpoint
does not pass the optional statistics owner to the engine. The final implementation supplies that owner.

These results support the final heap diagnosis: the fixed-budget failure remains after compiled forms
are cleared, with substantial active subscription and AP state. All checkpoint overview queries are
reviewed. The checkpoint JFR review is also complete. Before heap capture, the last 30 seconds of B2
have 99.358 GB of sampled allocation weight in O3 and 122.858 GB in O1, with 6.516 and 6.753 seconds
of clipped GC pauses. Subtraction accounts for 11.75% and 10.42% of inclusive allocation weight;
the `mergeAddDelta` lambda accounts for 13.06% and 12.69%. The shares overlap and progress differs. They support the active
AP/subscription diagnosis, without an isolated O1 speed or saving claim. See the [checkpoint report](../bidirectional-o1-o3-checkpoints.md)
for revisions, timings, archives, progress counts and query limits.

## Evidence and remaining gates

The evidence root is `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`:

- `fixed-projects/conductor/{main,bidi}`: actual revisions/settings, logs, resource samples, process captures,
  raw SARIF and saved profiles; the bidi folder also has the fresh guard dump.
- `fixed-projects/comparison/conductor.json`: all retained/added identities, raw locations, rejected-location
  message, statuses and grouped occurrence inventory.
- `input-audit/pre-run-committed`: fixed model/class/dependency/source/rule/model fingerprints and configured
  versus staged extension differences.
- `fixed-projects/conductor/performance-review`: completed read-only CPU, phase, cleanup and counter review,
  with tables, source-backed limits and a reproducible reader.
- `heap-analysis/conductor-bidi`: frozen query XML, actual MAT commands, archived reports and completed
  overview/strong-owner/lifecycle reviews in `overview-review`.
- `input-audit/post-run-check/result-20261013T042255.731787Z/equality-manifest.json`: the full post-checkpoint
  recheck passes all 77 strict checks across 41 captured content datasets. Six copied-JDK dangling link
  targets remain outside the original regular-file content scope; their historical targets are unavailable.

Raw main SARIF SHA-256 is `88ee4fa415256dfb6c5c114c04c5a27a012c0f6b2e3252d28c256e0d75e8d829`;
raw bidi SARIF SHA-256 is `2bfb701433085c3c87e64144ed2f263ffa1c59058d716f081e62c5ebef743ced`.
Final fixed-budget completion remains open. Final and checkpoint heap/profile reviews are complete. IR storage
and O4–O12 remain outside this selected implementation.
