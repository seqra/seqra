# Stirling-PDF: O1–O3 fixed-workload test

Main completes with 17 distinct sink identities. The fresh optimized analysis completes F1/L1, B2/L2 and
F3/L3, then times out in B4/L4. It exports the retained report: **40 CONFIRMED and 88 DEMAND entries**, or
128 SARIF rows with 56 distinct sink identities. The strict comparison retains 14 baseline identities,
loses three and adds 42. Fixed-budget completion and complete finding preservation are not met.

Two strict losses have same-rule, same-method XSS findings at different return instructions. The current
boundary/Simple reporting path explains the change to the last physical return; it does not prove equal
flows or support. The third loss is the `ProcessBuilder` command-injection sink, with no corresponding
current finding in that method. An offline probe reproduces command-mark loss at a later `List.add` return
rewriter. It does not establish the first loss point in the full run.

## Inputs and execution

Main is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with the opt-in guard heap hook. The actual optimized
manifest records `aa7b384f2d0fe217931e6c7e5f849c125e8011d4`; its changes after `b0a2448c0` are documentation
only, with no core runtime source difference. The manifest patch digests are
`a46bdb253cc3b46f875221a14e9363412103068fdec916b2f66bbe3adef9d24e` on main and
`9beff0e3d4e983caf282eb9147e37f4de365dc4d6408ac0d3c1aac8a1a090ff8` on bidi.

Both use `/drive-testcomp/opentaint-go-rules/e2e-root/model-Stirling-PDF/project.yaml`, SHA-256
`b62263d3134af8b0c93426dedcd15a976808e4c8e838950c1a4959022d5d3ef4`, from source head
`d80e627899daf804f1390a0b75a1da3fd093aa84`. The test-system revision is
`bbdbcc5fd35d976d93348760ea60561e4670f68c`. Both use the same prepared classes and dependencies. The saved
class-manifest digest is `81ce8fe152509ac8d9549db7d3ff77105e6bc2f12769d4c78fc9544f4e06fca2`, and the
dependency digest is `8a62c5f940557c079f751b3182e6e18968071a6fc4aada3c5d9d010f9ea733b6`. A preexisting test-source
edit is recorded in both source copies; this report does not treat the source checkout as pristine.

Both invoke `runProjectAnalyzer` directly with **8 GiB heap, 1,080 seconds for IFDS and 1,200 seconds for
the outer task**. Actual process captures confirm Temurin 17, `-Xmx8g`, storage batch 2,000 and disabled
multi-release loading. Both use the same 66-file built-in rule tree, warning/error severity selection and
Semgrep-style exported IDs; no custom rule/pass/dataflow directory is configured. Bidi alone enables
`--ifds-bidirectional` and optional form statistics. Run 1 starts at field limit 1.

## Strict finding differences

| Measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Unique strict sink identities | 17 | 56 |
| Exported occurrences | 17 | 128 |
| Retained baseline identities | — | 14 |
| Baseline identities absent | — | 3 |
| Added identities | — | 42 |
| Excess occurrences relative to main | — | 114 |
| Internal report states | — | 40 CONFIRMED; 88 DEMAND |
| Terminal status | OK | TIMEOUT |

An independent parse of the raw SARIF files confirms these counts using rule ID, sink fingerprint and full
method signature. Both files are valid. Main has 16 generated traces and one Simple result; bidi has
128 Simple results. Neither log records a trace-generation failure, rejected vulnerability location or
prescan error. The 72 bidi rows beyond its 56 unique identities are exact duplicate exported JSON rows.
The multiplicity distribution is 46 identities occurring once, five twice, three nine times, one 18 times
and one 27 times. SARIF does not carry the report state, proof or full context key. It cannot establish
which added identities are CONFIRMED or whether two duplicate rows have equal internal proofs.

The added identities and occurrences are grouped below. The full signatures, fingerprints, instructions
and raw spans remain in `comparison/Stirling-PDF.json`.

| Rule family | Path or class | Added identities | Added occurrences |
| --- | --- | ---: | ---: |
| Path traversal | `ConvertWebsiteToPDF.java` | 3 | 3 |
| Path traversal | `OCRController.java` | 1 | 1 |
| Path traversal | `stirling/software/common/util/TempFileManager` | 8 | 8 |
| Path traversal | proprietary `SignatureController` | 2 | 2 |
| Path traversal | proprietary `AdminLicenseController` | 1 | 1 |
| Path traversal | proprietary `SignatureService` | 10 | 10 |
| XSS | `ConvertEmlToPDF.java`, changed return | 1 | 2 |
| XSS | `FilterController.java` | 4 | 4 |
| XSS | `PipelineController.java`, changed return | 1 | 1 |
| XSS | `stirling/software/common/util/WebResponseUtils` | 2 | 10 |
| XSS | proprietary `AdminSettingsController` | 6 | 59 |
| XSS | proprietary `InviteLinkController` | 1 | 9 |
| XSS | proprietary `UserController` | 2 | 2 |
| **Total** | | **42** | **112** |

The other two excess occurrences repeat retained identities. The 42 added identities therefore comprise
25 path-traversal sites and 17 XSS sites. An added result from this retained, mixed-state report is not
automatically a final false positive. It needs complete refinement and site-specific support review.

### All three strict missing identities

The exact rule/full-method/fingerprint keys are preserved; neither changed-return pair is automatically
matched into the 14 retained identities.

| Rule | Full method signature | Main instruction and source span | Main sink fingerprint |
| --- | --- | --- | --- |
| `java.security.os-command-injection` | `stirling.software.common.util.ProcessExecutor#runCommandWithOutputHandling(java.util.List, java.io.File)` | index 8, `processBuilder.<init>(command)`; fallback path `stirling/software/common/util/ProcessExecutor`, line 183 | `wY2lI7b1TEw2RC154xc+UyLWgRr7oi07c8cEICJI+QM=` |
| `java.security.xss#sink#1` | `stirling.software.SPDF.controller.api.converters.ConvertEmlToPDF#convertEmlToPdf(stirling.software.common.model.api.converters.EmlToPdfRequest)` | index 103, `return %54`; `ConvertEmlToPDF.java` 133:17–134:77 | `PQLoBIezs1hooJgNROGqqMItTCrK3e4M30qb7Nc47I8=` |
| `java.security.xss#sink#1` | `stirling.software.SPDF.controller.api.pipeline.PipelineController#handleData(stirling.software.SPDF.model.api.HandleDataRequest)` | index 50, `return %104`; `PipelineController.java` 90:17–91:93 | `06ygLUfQlHncy8dVaxT/0Sr5FENRUrFXg+b9LTuYQTE=` |

The two Java paths above are under `app/core/src/main/java/stirling/software/SPDF/controller/api/`, in
`converters/` and `pipeline/` respectively. The ProcessExecutor row has no same-rule/full-method candidate
in the current output. It is a third strict loss, separate from the two return-location changes. The
timeout alone does not establish why this site is absent. The local propagation difference below can
explain the loss, but the full-run rule selection and fact history are unavailable.

### Command-mark loss at the source return rewriter

The offline probe uses the fixed model, packaged rules and production call builders. It performs no prescan
or IFDS run. The matched source at `command.add(URL)` has a live positive condition over seven untrusted
marks and generates `$ARGS` on the list receiver. The constructor sink also remains live and reads `$ARGS`
on argument 0. Neither condition contains a negative atom or folds to false.

At the later `command.add("--pdf-forms")`, instruction 56, the cached source predicate remains live. Its
return rewriter removes the existing exact `$ARGS` mark on callee `This`. A control that supplies this
mark on caller local 37 to the real plan at forward limit 1 produces no exit fact. The identical plan
preserves that fact when its cleaners are bypassed for diagnosis. Four checked `List.add` plans reproduce
this difference. The source cannot regenerate the mark from the receiver: its condition reads the new
argument's untrusted mark.

Main's unresolved default path passes the fact to its rewriter in caller coordinates. Its source actions
are indexed by callee `This`, so they do not match caller local 37 and the mark survives. Current forms bind
the receiver into callee coordinates before applying the return rewriter.
[The summary-rewriter specification](../../../spec/interpreter.md:1064) requires this coordinate order
and source override: a selected user-defined source removes its relevant marks at its action positions
from summary and unresolved results. The current local behavior therefore follows the specified policy.

This proves a local propagation difference for a marked list followed by a benign append. The probe uses
an unreduced provider and an empty method context; it does not restore the full run's selected rules,
caller contexts or F1/F3 fact history. It cannot prove the first actual loss point or exclude other losses.
The strict missing finding remains a failed gate. A change to the source override policy or rule model
requires further review.

### Fresh XSS exit-location evidence

| Method | Main return | Current same-rule/full-method candidate | Current sink fingerprint |
| --- | --- | --- | --- |
| `ConvertEmlToPDF.convertEmlToPdf` | index 103, `return %54`, 133:17–134:77 | index 126, `return %89`, 148:13–149:84; two occurrences | `6Jk9vRaNLFqnvRbJnI04BX+6v6gViiHL4wACO4UbctE=` |
| `PipelineController.handleData` | index 50, `return %104`, 90:17–91:93 | index 109, `return null`, 130:13–130:24; one occurrence | `XJZK3xYsWT0rHLNMZAAHSgLtC+jwGDwliolwIil166Q=` |

Fresh source inspection distinguishes the branches. ConvertEml's baseline site returns error bytes built
from an exception and the original filename; its current site is the enclosing method's final IOException
catch, returning constant `"File processing error"` bytes. Pipeline's baseline returns the single output
file; its current site is the final catch's `return null`. These are different instructions and spans.

[JIRBoundaryForms.exitRules](../../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRBoundaryForms.kt:74)
evaluates method-exit sinks at synthetic normal/exceptional exit nodes.
[RunMethodLocalServices.witness](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt:204)
stores that node in the vulnerability key.
[JIRBidiAnalysis.toVulnerabilities](../../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt:113)
exports the node with a Simple trace.
[SarifGenerator.generateTracePaths](../../../core/src/main/kotlin/org/opentaint/common/sast/sarif/SarifGenerator.kt:168)
supplies no trace path for Simple results.
[JirSarifGenerator.statementLocation](../../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/JirSarifGenerator.kt:215)
then resolves the synthetic exit through
[TraceMessageBuilder.tryResolveExitLocation](../../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/TraceMessageBuilder.kt:1162).
Without relevant trace locations, this function chooses the last physical return in the method. The fresh
current spans agree with that fallback in both cases. The fingerprint is computed from the selected sink
location, so the return change also changes the strict identity.

This is source-backed evidence of lost return-origin precision in the report adapter. It explains the
displayed location and fingerprint, without proving that either current finding has the same originating
flow as main. Both losses and both additions remain in the primary strict comparison. The separate
`method_exit_location_candidates` data is a review aid, not a replacement count.

## Completion, time and retained report

| Phase or measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Prescan | 17.005 s | 20.123 s |
| Main full scan | 5.476 s | — |
| Total IFDS | 22.483 s | 1,080.032 s, incomplete |
| Driver after prescan | — | 1,059.714 s |
| Gradle command wall time | 55.109 s | 1,113.136 s |
| Harness wall time | 55.109 s | 1,113.147 s |
| Peak process RSS | 9,562,771,456 B | 10,114,785,280 B |
| Analyzer / Gradle exit codes | 0 / 0 | 254 / 1 |

| Bidi run | Direction / limit | Engine | Barrier | Status |
| --- | --- | ---: | ---: | --- |
| 1 | Forward / 1 | 31.713047986 s | 0.290538196 s | COMPLETE |
| 2 | Backward / 2 | 21.109121651 s | 0.075334285 s | COMPLETE |
| 3 | Forward / 3 | 55.726124189 s | 8.817073912 s | COMPLETE |
| 4 | Backward / 4 | 941.970494441 s | — | TIMEOUT |

B4 starts at `00:58:30.173` and ends at `01:14:12.143`; its selected remaining timeout is
941.955980689 seconds. The driver ends `TIMEOUT / ABNORMAL`, not by the policy or STOP_RULE.
[IterationDriver.runOnce](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/IterationDriver.kt:68)
skips the barrier for an incomplete run.
[ReportBuilder](../../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/Report.kt:28)
retains prior confirmed entries and replaces the demand set only at a complete forward barrier. The final
output therefore retains the complete F3 demand set and accumulated confirmations; no partial B4 barrier
or F5 refinement is accepted. This differs from first-run failure, which has no prior report to retain.

## Resource and owner limits

The memory manager detects pressure twice, clears soft references and records recovery below its threshold.
No guard or JVM OOME heap dump is produced. The terminal status is timeout. Later B4 progress remains at
1,755,605 handled events and two in flight for a long interval. A thread snapshot at `01:06:55.581` finds one
bidi worker running call-stage processing and nine peers parked for executor work. The command takes 2.632 s
including startup/attach/output; this is not a measured JVM pause. Flat counters can span partial work and
requeues, so they do not prove one uninterrupted call or a deadlock. RSS includes native memory and is not live
Java heap size.

Continuous JFR and both requested 30-second CPU captures are present. The normal bidi request is anchored
at full analysis +60 seconds; the near-limit request is anchored at IFDS +1,020 seconds. A successful
profiler return alone does not establish matching phase windows. The completed review places the normal
window in forward run 3 and the near-limit window in backward run 4. The late capture has 8,745 CPU samples:
67.56% contain GC frames, 15.22% stage-application frames and 14.33% fact-group operation frames. These
inclusive families overlap and measure CPU work, not wall pauses. Owned-map lookup appears in only 0.46%;
the sustained profile does not support the snapshot's single lookup frame as the main cost.

Run 4 JFR allocation weights total about 2.06 TB. Trie subtraction accounts for 25.67%, stage application
11.68% and cleaner-chain application 11.14% of that phase's sampled weight. These overlapping estimates show
heavy temporary allocation during transfer/group operations, not retained bytes. All ten saved phase windows
match the run log. Main's 5.476-second full phase has no normal 60–90-second capture. Fresh heap-owner
attribution is unavailable from this run without a heap snapshot; older Stirling heaps are not proof for the
optimized runtime.

Optional form counters are cumulative over rebuilds: 788,915 successful builds, 381,250 reversals,
337,675,839 hits and 3,636 uncached builds. End-of-analysis active pins and tracked form references are zero
after teardown. These counts do not state what was live during B4, establish retained bytes or assign the
timeout to the form cache.

## Evidence and remaining gates

The evidence root is `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`:

- `fixed-projects/Stirling-PDF/{main,bidi}`: actual manifests, terminal summaries, logs, process captures,
  raw SARIF, resource samples and profiling captures.
- `fixed-projects/comparison/Stirling-PDF.json`: all three strict losses, 42 added identities, occurrence
  changes and the separately labelled exit-location candidates.
- `input-audit/pre-run-committed`: fixed input, model, built-in rule and toolchain equality evidence.
- `fixed-projects/Stirling-PDF/performance-review`: separate current-run performance review.
- `fixed-projects/Stirling-PDF/command-loss-review`: bound source/sink predicates, real call-plan controls,
  baseline coordinate comparison and full probe limitations in `BOUND-RESULT.md`.

Main SARIF SHA-256 is `68bcaf341b15c8f485fc9afb8f8333271b3d5ad35760036ddd628afc4e9835af`; bidi is
`4a3d454300aec50d36c4414d6ff8a50be4e620b4801933fa1af63bb568f33e8a`.
Fixed-budget completion, all baseline sink identities, full-run ProcessExecutor loss attribution, complete
added-site validation and per-optimization checkpoint attribution remain open. IR and O4–O12 are outside the selected
implementation.
