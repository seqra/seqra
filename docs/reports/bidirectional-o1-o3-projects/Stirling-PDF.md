# Stirling-PDF: O1–O3 fixed-workload test

Main completes with 17 distinct sink identities. The fresh optimized analysis completes F1/L1, B2/L2 and
F3/L3, then times out in B4/L4. It exports the retained report: **40 CONFIRMED and 88 DEMAND entries**, or
128 SARIF rows with 56 distinct sink identities. The strict comparison retains 14 baseline identities,
loses three and adds 42. Fixed-budget completion and complete finding preservation are not met.

Two strict losses have same-rule, same-method XSS findings at different return instructions. The current
boundary/Simple reporting path explains the change to the last physical return; it does not prove equal
flows or support. The third loss is the `ProcessBuilder` command-injection sink, with no corresponding
current finding in that method. An offline probe and a separate full-configuration diagnostic observe
exact-root command-mark removal at a later `List.add` return rewriter. The captured runtime batch is
DEMAND; it does not establish the earliest loss on a normal source-to-sink chain.

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
timeout alone does not establish why this site is absent. The local propagation difference and actual
forward-report replacement below supply evidence for a loss mechanism. The capped trace does not prove
the complete normal caller chain.

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

### Checkpoint report state at the command site

Both the O3 and O1 checkpoint outputs contain two occurrences at this strict command identity. Both
complete F1 and B2, then stop on memory in F3 without its report barrier. The O3 heap now establishes
the retained state directly: the two native entries are `os-command-injection:sink:1` and
`os-command-injection:sink:3`, both `DEMAND`, both from run 1. Their selected witness counts are two
and one. Native owner, full descriptor, instruction 8 and line 183 match the baseline command site.
The current manager is at forward run 3, limit 3, while the report builder still holds these F1 entries.

Thus the O3 output preserves coarse F1 demands after an incomplete F3. The final run completes F3
and can replace that unconfirmed demand set. This proves the earlier entries' state; the separate
runtime diagnostic below establishes the current code's F1/F3 report states. The O1 heap has the same two
run-1 `DEMAND` entries, with selected witness counts four and two. That count difference alone does
not establish different semantics. Selected witness headers and scalar pointer joins are reviewed. Keep the strict loss
visible and do not assign it to O2 solely from these
different forward barriers. The [checkpoint report](../bidirectional-o1-o3-checkpoints.md) records
all eight results and their limits.

### Fixed-configuration runtime observation

A separate diagnostic runs the actual Gradle `runProjectAnalyzer` task on code `b0a2448c0`, with the
same model, built-in rules, Temurin 17, 8 GiB heap and 1,080-second IFDS budget. A bounded observer is
applied only in an isolated checkout. Its `:classes` build and 48 selected call/report/driver tests pass.
It completes F1, B2 and F3, then times out in B4. Its final output has exactly the primary run's 56
strict identities, 128 occurrences and locations, with no identity or occurrence changes. These
diagnostic times do not replace the primary performance measurements.

The reserved report lane has 40 rows, no drops, no truncated payloads and no writer error. At the F1
barrier, `sink:1` and `sink:3` are both DEMAND/run1, with four and two unconfirmed witnesses. After
the completed F3 barrier, the actual report contains no command entry. Thus, this run removes earlier
unconfirmed command demands at F3; its B4 timeout retains that accepted F3 report.

Three fully captured F1/limit-1 batches use the same controller method, empty context, zero premise,
DEMAND layer and empty field exclusion:

| Operation | Captured result |
| --- | --- |
| `command.add(URL)`, index 55 | The real conditional source reads Arg0 ANY `$UNTRUSTED`, emits `This` EXACT `$ARGS`, binds it to caller local 37 and preserves it through the CALL cut. |
| `command.add("--pdf-forms")`, index 56 | That exact-root `$ARGS` fact binds to `This` and survives the unresolved identity return. The returned action chain includes its matching exact-root cleaner and changes the batch from one fact to zero. The captured source sibling has zero output. |
| Filename append, index 58 | A descendant `This.[Iterable.Element].ANY` untrusted fact survives the exact-root cleaner unchanged and maps back to local 37. |

This proves an actual local returned-rewriter removal, with a descendant-preservation control. It does
not prove that all list taint is removed. The matching behavior follows the existing D23 source-override
policy and is shared by the older O3/O1 call-plan code. Its contribution to the strict baseline loss is
an inference from this captured flow, the offline NORMAL control and the completed F3 report change;
these records do not prove an introduced O1/O2 defect or a complete normal caller-chain rejection.

Each ordinary F1/F3 event lane reaches its 25,000-row cap, with 263,813 and 591,192 omitted rows.
All twelve selected operations have captured rows, but this does not establish complete flow coverage
or a whole-run earliest-loss theorem. No payload is truncated; the independently reserved report lane
remains complete. The exact raw batches and three reproducible checks are in
`runtime-observer-runs/PDF-20261013T054001Z/Stirling-PDF/bidi/flow-review`.

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

## Checkpoint memory evidence

Both cumulative checkpoints stop with OOM in forward run 3 at limit 3. Their accepted F1 reports each
contain 61 unique identities and 150 internal entries: nine CONFIRMED and 141 DEMAND. Each retains 15
of the 17 main identities; the two strict XSS exit-location differences remain visible. These checkpoint
reports differ from the final run, which completes F3 and then times out in B4.

| Retained union | O3 | O3+O1 |
| --- | ---: | ---: |
| Strong form cache arrays | 3,149,636,544 B | 1,391,567,056 B |
| Active method-edge stores | 1,731,576,456 B | 3,091,706,680 B |
| Callee subscription state | 254,289,016 B | 464,880,072 B |
| Compiled NNF source/sink descriptors | Not present | 642,532,488 B |
| Observed NNF stores | Not present | 3,170,256 B |

O3 contains 7,267 active F3 actors; O1 contains 7,270. Both have 90 runners. O1 contains 673 live
predicate states and 3,710 interned proofs. Its compiled predicate storage is much larger than its
observed predicate-store union. The snapshots have different event progress. All unions overlap;
the cache decrease and edge-store increase do not measure isolated optimization savings. Restricted
request-store and standing-join queries are empty in both heaps; forward conjunction stores remain.
O1 observed-proof counter zeros are unavailable because the statistics owner is not passed to this
checkpoint engine. The final implementation supplies that owner.

All checkpoint overview and selected command-demand witness queries are reviewed. Sink 1's two selected
O3 F1 witnesses check an ANY `$UNTRUSTED` root in DEMAND; sink 3's witness checks an EXACT `$ARGS`
root in NORMAL. All three are unconfirmed, with a nonzero Arg0 premise, one literal, no path, no end
facts and an empty exclusion. A NORMAL checked fact alone does not establish caller-chain support.

Checkpoint JFR review is complete. Before capture, O3 F3 records 88.847 GB sampled allocation weight
and 5.879 seconds of clipped pauses in 25.219 seconds; O1 F3 records 116.681 GB and 6.439 seconds
in 30 seconds. Subtraction accounts for 9.76% and 12.17% of inclusive allocation weight. Shorter
windows and different progress prevent an isolated memory or speed claim. These OOM heaps explain
the checkpoint failures; they do not establish final B4 retained ownership. The separate full-run
command observer is terminal with the same B4 timeout and selected output. See the [checkpoint report](../bidirectional-o1-o3-checkpoints.md) for revisions,
timings, archives, report-state joins and query limits.

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
- `runtime-observer-runs/PDF-20261013T054001Z`: original-configuration diagnostic manifests and summary,
  strict primary/main comparisons, complete report ledger and capped flow records. The `flow-review`
  folder keeps the source-generation, root-removal and descendant-preservation batches and validators.

Main SARIF SHA-256 is `68bcaf341b15c8f485fc9afb8f8333271b3d5ad35760036ddd628afc4e9835af`; bidi is
`4a3d454300aec50d36c4414d6ff8a50be4e620b4801933fa1af63bb568f33e8a`.
Fixed-budget completion and preservation of all baseline identities fail. Checkpoint heap/profile and
selected witness reviews are complete. The actual local source-override removal and F3 report replacement
are verified; contribution to the full normal baseline path remains an inference under the event caps.
Incomplete DEMAND additions are not final false-positive counts. Further source-policy or deferred core
changes require a separate scope decision. IR and O4–O12 remain outside the selected implementation.
