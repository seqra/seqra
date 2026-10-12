# Bidirectional project findings

This report records the measured project results from 12 October 2026. The selected inputs and budgets are in [bidirectional-project-inputs.md](bidirectional-project-inputs.md). Runs use the Gradle `runProjectAnalyzer` task. They do not use the CLI.

All eight runs are terminal. Main completes for all four projects. Every bidi analysis ends with OOM. No timeout, masked prescan error or input-model mismatch is observed in these bundles. The results do not establish completed-analysis recall or precision for bidi.

| Project | Main status | Bidi status / failed run | Main unique sinks | Bidi unique sinks / results | Strict missing main locations | Additional unique locations |
|---|---|---|---:|---:|---:|---:|
| OWASP | OK | OOM / run 1 | 4,112 | 0 / 0 | 4,112 | 0 |
| Conductor | OK | OOM / run 2 | 7 | 300 / 300 | 0 | 293 |
| ThingsBoard | OK | OOM / run 1 | 14 | 0 / 0 | 14 | 0 |
| Stirling-PDF | OK | OOM / run 3 | 17 | 61 / 150 | 2 | 46 |

The OWASP and ThingsBoard losses follow an incomplete first run. Stirling-PDF's two strict missing locations also have same-rule findings in the same methods at different returns, as explained below. Additional locations are observed output, not a certified false-positive count. The tool retains raw occurrence counts separately from unique reported sink identities.

## OWASP: incomplete first run removes all output

The main run completes and reports 4,112 unique sink identities. The bidi run stops with `OOM` in forward run 1. It reports no vulnerability. The first run lasts 40.929789656 seconds. No later run or confirmation barrier starts.

| Measurement | Main | Bidi |
|---|---:|---:|
| Terminal status | OK | OOM |
| Reported unique sink identities | 4,112 | 0 |
| Harness wall time | 153.30 s | 124.30 s |
| Peak analyzer RSS | 9,290,682,368 bytes | 9,931,399,168 bytes |
| Analyzer heap limit | 8 GiB | 8 GiB |

RSS includes memory outside the Java heap. Harness wall time includes Gradle setup and profiler cleanup. The lower bidi time is an early failure, not a performance improvement.

The prescan completes normally. It takes approximately 47.60 seconds in bidi. The log then selects 168 of 6,255 rules and starts run 1 at 07:53:31.174 UTC. Heap use rises from 4,431,204,352 bytes at the first progress sample to 7,810,464,472 bytes after GC near termination. The guard threshold is 7,730,941,132 bytes, or 90% of the 8 GiB heap. The run ends at 07:54:12.104 with `BIDI_RUN_END run=1 status=OOM`. The analysis end records `status=OOM reason=ABNORMAL`. The analyzer process exits with code 253.

This is a resource-driven loss of output under the required budget. It does not show 4,112 separate transfer defects. `RunManager` gives an incomplete run no retained result analyzers. `IterationDriver` returns before the confirmation and report barrier when the run status is not COMPLETE. Thus an incomplete run 1 adds no report entry. This follows the current report contract in [AP §8.10](../../spec/ap.md).

The continuous JFR contains 2,108 Java execution samples during run 1. Inclusive sample shares are 36.15% for `JIRCallPlanBuilder.build`, 35.15% for `JIRRuleForms.sinks`, and 23.91% for `JIRRuleForms.cubes`. The run-1 allocation sample weights total approximately 47.27 GB. Inclusive shares are 32.35% for `sinks`, 18.55% for `cubes`, and 9.76% for `SinkRule` constructors. The recording has 9.22 seconds of GC pauses across all phases. These sample shares overlap; they must not be added. Allocation weights estimate allocated bytes, not retained heap.

The measured hot path is form construction. [`cubes`](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt) removes negated literals, builds condition DNF, creates path facts, and removes duplicates after construction. `sinks` then builds the Cartesian product of array choices, removes duplicate alternatives, and creates one owned `SinkRule` per alternative. [`JIRMethodForms`](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRMethodForms.kt) caches call plans by full method key; its [`FormsCache`](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormsCache.kt) keeps strong forward and reverse slots across iterations. Thus built alternatives have cached owners. The measurements do not prove their retained size or that one expansion alone caused OOM. Counts of alternatives and cache owners, or a heap analysis, are still needed. Reuse of immutable rule forms and removal of duplicate work before the Cartesian expansion are candidate optimizations. They must preserve rule alternatives and context-dependent conditions.

The requested 30-second sample after one minute of full analysis cannot run: bidi stops before that minute. The near-time-limit sample cannot run either. The continuous JFR covers the actual first run. These absent windows are a limit of this run, not successful profile captures.

Evidence is in `/drive-testcomp/opentaint-go-rules/bidi-real-20261012/OWASP/{main,bidi}`. Each side has `manifest.json`, `summary.json`, `analyzer.log`, `resources.jsonl`, `continuous.jfr`, `profile-summary.json` and `reports/report-ifds.sarif`. The manifests record revisions, patch hashes, model hash, arguments and fixed budgets. Main has a successful `normal.collapsed` profile; bidi has no delayed async-profiler window.

## Comparison checks

[compare_bidi_projects.py](../../scripts/compare_bidi_projects.py) compares sink fingerprints and full method signatures. It keeps unique sink counts separate from occurrence counts. It excludes code-flow counts from sink identity. It also retains raw source locations and marks equal identities whose spans differ. All 4,112 main OWASP results have a sink fingerprint and a full decorated method signature. With zero bidi results, none of the observed differences is a source-span or trace-count mismatch.

The OWASP assessment is `INCOMPLETE_OR_INVALID_INPUT_COMPARISON`, not a completed-analysis recall result. Its full missing-output sink list is in the bundle's `comparison/OWASP-removed.json`, grouped by rule and source path. Missing SARIF files in unfinished projects have unknown counts. They are not counted as valid empty reports.

Seventeen focused Python tests pass. They cover trace/span changes, overload collisions, duplicate counts, missing and invalid SARIF, partial status, model mismatch, changed instruction indices, a masked prescan error, a barrier failure after a complete engine run, a rejected SARIF location and return-site candidates that retain strict missing counts. These checks validate the comparison tool; they do not replace project analysis.

## Conductor: first-run output retained after backward OOM

Main completes and exports seven unique sink identities. Bidi completes forward run 1 in 40.979852693 seconds. Its barrier takes 0.808662731 seconds and retains 301 report entries: three CONFIRMED and 298 DEMAND. Backward run 2 stops with OOM after 13.189990542 seconds. There is no refined forward run 3. The terminal OOM preserves the report from complete forward run 1, as required by the current report contract.

The exported SARIF has 300 unique sinks and 300 results. All seven main sink identities are present. There are 293 additional exported identities. No matched sink has a raw source-location difference. Every result has a strong sink fingerprint and full method signature. Thus the added count is not a trace-count or span-comparison effect.

The additional output is not a list of 293 certified false positives. The latest report contains 298 DEMAND entries, and the backward failure prevents their refinement. SARIF exports both CONFIRMED and DEMAND entries as Simple traces. It has no per-result state field, so it cannot identify the state of each added sink. At most three added exported sinks can be CONFIRMED; at least 290 are DEMAND. The measured report and the incomplete analysis must be kept separate from a final precision claim.

### One report entry is lost in SARIF generation

The log reports `TraceGenerationStats(total=301, simple=301)`, but the JSON contains 300 results. This is not comparator deduplication. At 07:59:26.526 UTC the generator logs `Invalid vulnerability location` for `%3 = %0.createDispatcher()`, rule `java/security/sqli.yaml:sql-injection`, sink `sink:8`, in virtual `io.nats.client.Connection#createDispatcher(io.nats.client.MessageHandler)`. The context contains the lambda from `NATSObservableQueue.subscribe`.

[`SarifGenerator`](../../core/src/main/kotlin/org/opentaint/common/sast/sarif/SarifGenerator.kt) drops a result when `vulnerabilityLocation` returns null. [`JirSarifGenerator.statementLocation`](../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/JirSarifGenerator.kt) requires a real location for a generated instruction. [`TraceMessageBuilder`](../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/TraceMessageBuilder.kt) treats enriched virtual methods as generated, but its recovery function has no general enriched-virtual-method case. A Simple trace provides no caller trace to help that recovery. The observed virtual instruction is rejected on this path.

This is a report export gap. It is not a missing main sink in this pair: all seven main sinks match other exported results. The comparison output retains the rejection line and records one unexported attempted result. The rule match and the semantic state of this extra virtual sink are not proved by its source-location failure.

### Measured optimization targets

Conductor's CPU profile differs from OWASP. `JIRGraphImpl` construction has 6.28% of run-1 Java samples and 6.69% of run-2 samples. Virtual method `flowGraph` construction appears on that path. [`JTryBoundaryExceptionsApplicationGraph`](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JTryBoundaryExceptionsApplicationGraph.kt) calls `method.flowGraph()` during each successor and predecessor query. Reuse of an immutable method graph is an investigation target.

Run-1 allocation sample weights total approximately 137.49 GB. The event-queue comparator accounts for approximately 10.46 GB, or 7.61%, in the captured stacks. `Long.valueOf` has 7.65%. [`EventQueue`](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EventQueue.kt) uses `compareBy` on `Long` selectors. A comparator that compares primitive rank, steps and sequence values directly can remove this boxing while retaining the priority order. This is a concrete implementation optimization candidate.

Run-2 allocation weights total approximately 46.02 GB. `TrieOps.subtract` has an inclusive allocation share of 9.92%, and `leafViews` has 6.10%. These identify allocation work to inspect. Allocation samples and CPU stacks do not establish retained heap size. No heap dump was taken. The OOM guard stop is proved; an exact retained-object cause is not yet proved.

Evidence is in `/drive-testcomp/opentaint-go-rules/bidi-real-20261012/conductor/{main,bidi}` and `comparison/conductor.json`. Main harness wall time is 185.55 seconds; bidi harness wall time is 128.26 seconds with an incomplete backward run. These times are not equivalent completed workloads.

## ThingsBoard: incomplete first run removes all output

Main completes and exports 14 unique sinks. Bidi stops with OOM in run 1 after 41.661902314 seconds and exports a valid empty SARIF report. No confirmation barrier or later iteration starts. The incomplete first run therefore contributes no report entry, as in OWASP. This proves loss of baseline output under the required 12 GiB budget, but it does not identify 14 separate transfer defects.

The last progress sample records 8,878,468 handled events and 538,074 events in flight. Sampled heap use is 11,620,368,040 bytes out of a 12,884,901,888-byte limit. The guard threshold is 11,596,411,699 bytes. The prescan has no observed failure. Main harness wall time is 230.38 seconds; bidi harness wall time is 229.41 seconds with an incomplete first run.

The run-1 JFR has 9,186 Java samples. `JIRGraphImpl` construction has 15.00% of inclusive samples, and virtual `flowGraph` construction has 11.60%. Allocation sample weights total approximately 134.56 GB; graph construction has 10.05% of these weights. Repeated virtual graph construction is a concrete hot path to inspect. The large event backlog and the guard stop are measured; the profile is not a retained-object census.

Evidence is in `/drive-testcomp/opentaint-go-rules/bidi-real-20261012/thingsboard/{main,bidi}` and `comparison/thingsboard.json`.

## Stirling-PDF: retained first-run report and two return-location regressions

Main completes and exports 17 unique sinks. Bidi completes forward run 1 in 40.734207209 seconds and its barrier in 0.530274240 seconds. The report has nine CONFIRMED and 141 DEMAND entries. Backward run 2 completes in 23.308319478 seconds and its barrier in 0.102752385 seconds. Forward run 3 stops with OOM after 18.809838876 seconds. Its incomplete result adds no entry and refutes no previous entry. The final output retains the 150 entries of complete forward run 1.

All 150 attempted results are exported. They represent 61 unique reported sink identities, with 15 of the 17 main identities present and 46 additional identities. There is no invalid-location rejection in this pair. The 141 DEMAND entries remain unrefined because run 3 is incomplete. No per-result state field permits a complete classification of the extra locations as CONFIRMED or DEMAND. They must not be called 46 certified false positives. Main harness wall time is 49.10 seconds; bidi harness wall time is 130.26 seconds with an incomplete third run.

### Exact reported-location differences

Both strict missing main locations use rule `java.security.xss#sink#1`. The bidi report has the same rule in the same full method signature, but resolves it to the method's last return.

| Full method | Main location | Bidi location |
|---|---|---|
| `stirling.software.SPDF.controller.api.converters.ConvertEmlToPDF#convertEmlToPdf(stirling.software.common.model.api.converters.EmlToPdfRequest)` | instruction 103, `return %54`, `ConvertEmlToPDF.java` lines 133–134 | instruction 126, `return %89`, lines 148–149; two exported occurrences |
| `stirling.software.SPDF.controller.api.pipeline.PipelineController#handleData(stirling.software.SPDF.model.api.HandleDataRequest)` | instruction 50, `return %104`, `PipelineController.java` lines 90–91 | instruction 109, `return null`, line 130 |

The raw `vulnerabilitySinkHash/v1` values are:

| Method | Main fingerprint | Bidi fingerprint |
|---|---|---|
| `ConvertEmlToPDF.convertEmlToPdf` | `PQLoBIezs1hooJgNROGqqMItTCrK3e4M30qb7Nc47I8=` | `6Jk9vRaNLFqnvRbJnI04BX+6v6gViiHL4wACO4UbctE=` |
| `PipelineController.handleData` | `06ygLUfQlHncy8dVaxT/0Sr5FENRUrFXg+b9LTuYQTE=` | `XJZK3xYsWT0rHLNMZAAHSgLtC+jwGDwliolwIil166Q=` |

The source files are in the fixed portable model under `sources/app/core/src/main/java/`. The baseline trace for `convertEmlToPdf` propagates the original filename through `buildErrorMessage`, then `ResponseEntity.body`, to return 103. The trace for `handleData` propagates `bytes` through `WebResponseUtils.bytesToWebResponse` to return 50. The new locations instead show each method's final return; `handleData`'s final return is a literal null.

### Source owner chain

[`JApplicationSingleExitGraph`](../../core/opentaint-utils/opentaint-jvm-util/src/main/kotlin/org/opentaint/jvm/graph/JApplicationSingleExitGraph.kt) connects the real return instructions to one synthetic normal exit. [`JIRBoundaryForms`](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRBoundaryForms.kt) builds the method-exit sink at this boundary. [`RunMethodLocalServices`](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt) stores that boundary as the vulnerability statement. [`JIRBidiAnalysis.toVulnerabilities`](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt) exports the stored statement with a Simple trace.

[`TraceMessageBuilder.tryResolveExitLocation`](../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/TraceMessageBuilder.kt) first uses a real return from the available trace. If there is no trace, it selects the last matching return instruction in the method. Main has a full trace, while Simple bidi output has no trace. [`JirSarifGenerator.statementLocation`](../../core/src/main/kotlin/org/opentaint/jvm/sast/sarif/JirSarifGenerator.kt) therefore uses the last return for the new synthetic-exit findings. The sink fingerprint then hashes this selected instruction index. This explains the different fingerprints and source spans.

These are two strict reported-location regressions with a proved output-adapter cause. They are not evidence that field-limit refinement had to finish before either method-exit sink could report: bidi already reports the same-rule boundary findings in run 1. The incomplete analysis and absence of a path trace still prevent a guarantee that each new witness is the same taint flow as the baseline witness. The comparator keeps the two strict missing counts and records the alternative returns separately in `method_exit_location_candidates`. It does not merge them silently.

## Final status audit

| Project / side | Gradle exit | Analyzer exit | Terminal status | Prescan failure observed | SARIF location rejection |
|---|---:|---:|---|---|---:|
| OWASP / main | 0 | 0 | OK | no | 0 |
| OWASP / bidi | 1 | 253 | OOM | no | 0 |
| Conductor / main | 0 | 0 | OK | no | 0 |
| Conductor / bidi | 1 | 253 | OOM | no | 1 |
| ThingsBoard / main | 0 | 0 | OK | no | 0 |
| ThingsBoard / bidi | 1 | 253 | OOM | no | 0 |
| Stirling-PDF / main | 0 | 0 | OK | no | 0 |
| Stirling-PDF / bidi | 1 | 253 | OOM | no | 0 |

All pairs use equal recorded model hashes and equal memory/timeout settings. These findings concern the required settings only. No run with a larger heap, altered field-limit policy or restricted rule set was used to obtain this comparison. The four machine-readable assessments remain `INCOMPLETE_OR_INVALID_INPUT_COMPARISON` because each bidi run ends with OOM.
