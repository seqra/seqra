# Conductor: O1–O3 fixed-workload test

The fresh optimized run stops with a memory-guard OOM in backward run 2. Its available SARIF contains all seven
main sink identities and 293 additional locations. The comparison is incomplete: the retained report has three
CONFIRMED entries and 298 DEMAND entries. These additions are not a set of final false positives. The fixed
resource gate fails, and fresh heap-owner analysis is pending MAT.

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

## Heap-owner analysis — pending MAT

The memory guard repeatedly reports usage above its 7,730,941,132-byte threshold. Before cancellation it captures
`guard-live.hprof` with reported heap usage 7,744,179,472 bytes. Capture finishes with an 11,693,829,182-byte file.
That file size is serialized HPROF size, not retained heap size. No JVM allocation-failure cause is inferred from
the analyzer's memory-guard OOM status.

Run 2 records 12 soft-reference cleanups and 11 recoveries. The first two recovered after-GC heaps are
4,722,136,720 and 6,635,586,000 bytes, with observed drops of 3,037,247,856 and 1,099,722,440 bytes. Later recovery
drops shrink to roughly 9.7 MB while the recovered heap approaches the guard threshold. Cleanup clears all
registered soft-reference managers and GC also collects ordinary dead objects. These are aggregate heap drops;
the portion attributable to method forms remains unmeasured.

Fresh MAT parsing, dominator unions, strong root paths and the split between current actor state, compiled forms,
stable metadata and IR remain **pending**. The cumulative final counters record 578,702 forward builds,
348,850 reversals and 18,966 uncached builds, but do not identify the live memory owner. They cannot establish
cache retention, a leak or the amount reclaimed. Earlier Conductor heaps and reports are not proof for this run.

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

Raw main SARIF SHA-256 is `88ee4fa415256dfb6c5c114c04c5a27a012c0f6b2e3252d28c256e0d75e8d829`;
raw bidi SARIF SHA-256 is `2bfb701433085c3c87e64144ed2f263ffa1c59058d716f081e62c5ebef743ced`.
Heap-owner diagnosis, final fixed-budget completion and O3/O1 checkpoint attribution remain open. IR storage
and O4–O12 remain outside this selected implementation.
