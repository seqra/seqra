# Run-1 FULL work batching: project measurements

Date: 23 October 2026. **All 12 commands are closed: four fresh main commands
and eight reference/candidate commands. All 12 profile sets and all six
OOM owner scopes have completed analysis. Bounded optional probe limits
remain explicit.**

Candidate runtime: `fb84969c0b91ccae0500389b5e25e045557b0eb3`.
Reference: `3179b9a22e296aa29147dc4d03dd5e8ab093bcf4`, with runtime equal to
the specification's `8ab1445b8` reference. Main:
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
Read the [implementation validation](bidirectional-full-work-batching-validation-20261023.md).

The selected changes remove repeated temporary supplies and combine FULL
return cleaners. They do not yet make Thingsboard complete run-1. Both
Thingsboard commands fail at the memory guard. Conductor and Stirling-PDF
complete run-1 within 60 seconds, then fail in run-2. Both OWASP commands
complete runs 1–3 and time out in run-4. The exported reference/candidate
sink-key multisets match for all four projects. The two empty Thingsboard
outputs are censored run-1 failures. These observations do not establish
whole-project acceptance or repair the strict PDF location failures.

The selected work reductions are real, but no whole-engine improvement is
established. On the candidate Thingsboard prefix, local supply admission
removes 92.85% of repeated atom offers. The exact completed singleton FLOW
subset removes 74.08% of scalar cleaner calls. Other work still dominates
the failed run-1. The next [proposal](../design/bidirectional-full-work-batching-next-iteration-20261023.md)
first targets requests with no possible selected producer, then unused
call-plan and alias construction. It changes no runtime code.

## Configuration and timing scope

All commands use Gradle `:runProjectAnalyzer` directly, with Java 17. The paired commands
select FULL, enable statics and keep default alias analysis. Models, rules,
extensions, source revisions and runtime arguments are saved and checked.
They use the project settings below. No heap or timeout is increased.
The outer scan limit is 1,200 seconds, measured from analyzer launch.

| Project | Heap | IFDS timeout |
| --- | ---: | ---: |
| OWASP | 8 GiB | 1,000 s |
| Conductor | 8 GiB | 1,080 s |
| Thingsboard | 12 GiB | 1,080 s |
| Stirling-PDF | 8 GiB | 1,080 s |

The test-input revision is `bbdbcc5fd35d976d93348760ea60561e4670f68c`.
All sides use the CI JVM settings
`-Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000` and
`-Djdk.util.jar.enableMultiRelease=false`. Conductor also uses its configured
extension rules, passthrough models and compiled dataflow approximations.
The three real projects select warning/error rules and Semgrep-style
SARIF IDs. OWASP retains the CI rule-severity scope and rule IDs.
The source/input census covers model YAML, serialized rules and those
extension inputs. It does not hash the complete large IR/dependency trees.

The isolated main checkout has the saved opt-in MemoryManager snapshot
patch. It is not an unmodified checkout. That patch adds diagnostic capture
before memory-guard cancellation; none of the four main commands has an
OOM snapshot. Its exact diff is retained as `preparation/main.patch`.

The paired commands ran serially, in Thingsboard, Conductor, Stirling-PDF
and OWASP order, with reference before candidate. Each launch waited for
available host memory of at least heap plus 2 GiB. Unrelated host work
could start during a command. Source and input checks passed before each
command and at final closure. This is one paired observation per project,
not a repeated timing experiment.

The harness adds continuous JFR, GC logs, aggregate counters and
memory-guard heap capture. It requests a 30-second normal CPU window after
60 seconds of run-1. It also requests a late window near the IFDS timeout.
Short run-1 commands can have no normal native window; their available JFR
evidence must be checked instead. A late profile can belong to a later run.
Both paired sides enable `formStatistics` and `optimizationStatistics`.
The candidate adds frequent counters that the reference does not have.
These instrumented timings do not isolate production-default overhead.
The main runs do not enable those bidirectional counters.

The table below uses engine time. It excludes prescan and report barriers.
An OOM engine time includes its diagnostic heap pause. Subtracting that
pause does not turn a failed prefix into a completed timing result.

## Iterations

| Project / side | Command s | Run-1 engine s | Later engine result | Latest reached / COMPLETE / accepted forward |
| --- | ---: | ---: | --- | --- |
| Thingsboard reference | 400.930 | 217.062, OOM | None | run-1 / none / none |
| Thingsboard candidate | 652.621 | 359.365, OOM | None | run-1 / none / none |
| Conductor reference | 206.817 | 15.396, COMPLETE | run-2 OOM, 89.632 s | run-2 / run-1 / run-1 |
| Conductor candidate | 283.087 | 20.516, COMPLETE | run-2 OOM, 140.250 s | run-2 / run-1 / run-1 |
| Stirling-PDF reference | 337.341 | 30.000, COMPLETE | run-2 OOM, 213.093 s | run-2 / run-1 / run-1 |
| Stirling-PDF candidate | 319.973 | 32.499, COMPLETE | run-2 OOM, 202.034 s | run-2 / run-1 / run-1 |
| OWASP reference | 1,074.782 | 236.546, COMPLETE | run-2 COMPLETE 367.011 s; run-3 COMPLETE 116.442 s; run-4 TIMEOUT 207.117 s | run-4 / run-3 / run-3 |
| OWASP candidate | 1,095.265 | 255.243, COMPLETE | run-2 COMPLETE 381.426 s; run-3 COMPLETE 77.812 s; run-4 TIMEOUT 192.709 s | run-4 / run-3 / run-3 |

The 60-second run-1 target passes for Conductor and Stirling-PDF. It fails
for Thingsboard and OWASP on both sides. No reference or candidate command
completes the whole analysis. Command time includes launch/build work,
input loading, prescan, barriers, diagnostics and report output; it is not
the run-1 target metric.

The four fresh main commands use the legacy engine. Their bidirectional
latest-reached, COMPLETE and accepted-forward labels are **not applicable**.

| Main project | Command s | Prescan s | Legacy full-scan s | Terminal status |
| --- | ---: | ---: | ---: | --- |
| OWASP | 539.774 | 99.427 | 117.515 | OK |
| Conductor | 292.347 | 83.108 | 64.665 | OK |
| Thingsboard | 520.090 | 325.330 | 45.152 | TIMEOUT during prescan; later scan and output retained |
| Stirling-PDF | 145.624 | 41.318 | 12.231 | OK |

Main OWASP has the CI trace total 4,112: 493 Simple results and 3,619
generated traces, with no generation failures. Its loading and prescan
also have severe recorded host swapping. This is a valid observed finding
baseline, but its wall time is not a clean causal timing control.

The Thingsboard heap pauses are 30.823 s and 40.836 s. Before capture,
the wall spans are 186.139 s and 318.440 s. Nearest resource samples show
2,489.18 and 2,935.83 process CPU seconds. Mean process CPU use falls from
13.35 to 9.20 cores while other host work changes. The candidate also has
a different event prefix. These data do not isolate algorithmic regression
or establish a completed-run speedup.

Conductor heap pauses are 22.628 s and 37.805 s. Stirling-PDF heap pauses
are 32.910 s and 28.302 s. These later-run failures remain separate from
the completed run-1 timings. Later runs do not execute the new bulk loop.

## Observed locations

Main uses the legacy engine, so bidirectional run labels do not apply.
Its fresh OWASP, Conductor and Stirling-PDF commands complete. Its
Thingsboard prescan times out; the report retains 14 observed locations.
That is an incomplete baseline.

| Project | Main unique sink keys | Reference / candidate unique sink keys | Strict main locations absent |
| --- | ---: | --- | --- |
| Thingsboard | 14, incomplete | No accepted forward report on either side | All 14; both first iterations incomplete |
| Conductor | 7 | 41 / 41, accepted run-1 | 0 / 0 |
| Stirling-PDF | 17 | 148 / 148, accepted run-1 | 2 / 2 |
| OWASP | 4,112 | 4,112 / 4,112, accepted run-3 | 0 / 0 |

The comparison preserves public rule ID, full method identity, sink
fingerprint and raw location evidence. Primary and strict key multisets,
including occurrence counts, match between reference and candidate for
each project. Equal empty Thingsboard multisets do not prove semantic
equivalence: neither side accepted any forward report. All seven main
Conductor keys and all 4,112 main OWASP keys are present in the accepted
outputs. The PDF strict gate still fails on both sides.

Conductor has 42 exported occurrences at 41 unique keys. Both logs record
zero CONFIRMED and 44 internal DEMAND candidates. The 34 added unique
keys account for 35 occurrences; the extra occurrence is the same
`AIModelProvider` File-constructor sink at line 47. Do not equate the
internal count 44, exported count 42 and unique count 41. All added
occurrences are unconfirmed DEMAND candidates and have no exported code
flow. Their equality across the two versions is not proof that all are
false positives or that later analysis will remove them.

The source-bound Conductor review has three concrete checks:

- The nine added SQL keys include DAO call sites and shared Query methods.
  The shown DAO passes a constant SQL string with `?` and uses
  `addParameter(workflowId)`, whose implementation calls `setString` on a
  prepared statement. A source witness must distinguish parameter data
  from query syntax. The shared Query methods can have other callers.
- Eight added controller XSS keys return typed task/log data or queue
  results. The shown annotations do not declare `produces="text/html"`.
  This makes response-type and resolved exit-condition checks necessary;
  it does not prove the response content type in every deployment.
- Configuration-path constructors appear under the gRPC source rule.
  Their visible values come from configuration or system properties,
  while the extension rule restricts its source to `get...` calls inside
  generated gRPC-handler subclasses. A different write or caller could
  still supply the path. The missing source/guard trace prevents a
  conclusive source-scope diagnosis.

These cases do not identify ND-origin splitting, field widening or static
order approximation as their cause. The source/rule hashes and all 35
added occurrences are retained in the
[Conductor case study](../../../run1-full-work-batching-20261023/validation/conductor-precision-case-study/precision-case-study.md).

Stirling-PDF has 973 occurrences and 133 added unique locations on both
sides. The two strict missing keys are `xss#sink#1` returns in
`ConvertEmlToPDF.convertEmlToPdf` and `PipelineController.handleData`.
Main reports returns at lines 133–134 and 90–91. Both new-core reports
instead have the same rule and method at the last returns, lines 148–149
and 130. The new core evaluates a synthetic normal exit and exports
pathless Simple results. `TraceMessageBuilder.tryResolveExitLocation`
then selects the last real return. The source chain is pinned from
`JApplicationSingleExitGraph` through boundary sink recording, Simple
export, and the common SARIF location resolver. The relevant functions
are unchanged between reference and candidate. Every matching method/rule
result is at that last return: 18 occurrences for the EML controller and
nine for Pipeline, with no code flows; main has one traced occurrence for
each earlier return.

The source establishes the synthetic-exit Simple-report location loss.
The strict two-key gate remains **failed and unchanged**. This is not
censored by run-2 OOM: both reports accepted complete run-1. The alternate
reports do not prove retention of the same branch, FULL mark or supported
source binding. The Pipeline branch explicitly supplies
`APPLICATION_OCTET_STREAM`; that is a separate possible baseline rule
precision lead, not a waiver. The
[PDF classification](../../../run1-full-work-batching-20261023/validation/pdf-exit-location-cases-20261023/classification.json)
records location loss as confirmed and branch/full-mark witness equivalence
as unproved. No exit-rule or reporting fix is included here.

Both OWASP outputs have 9,179 occurrences at exactly the 4,112 main keys,
with the same per-key multiplicities and strict locations. The extra rows
are not 5,067 new unique false-positive locations. All 9,179 outputs are
Simple results without generated traces. This is accepted run-3 evidence
from commands that later time out in run-4, not whole-analysis completion.

## Capture coverage and exact receipts

All 12 commands retain nonempty `continuous.jfr` files and GC/resource
logs. The rolling JFR limit is 256 MiB; the existence of a recording does
not itself prove that every earlier phase remains in it. Phase coverage
must be checked before using a sample window.

| Paired project | Normal native run-1 window | Separate run-1 JFR | Heap capture | Near-limit native window |
| --- | --- | --- | --- | --- |
| Thingsboard, both sides | 30 s requested after 60 s; profiler succeeded while run-1 was active | None; reference attach failed after exit, candidate dump reported no recording | run-1, both sides | None; commands ended before that delay |
| Conductor, both sides | Skipped; run-1 ended before 60 s | Present on both sides | run-2, both sides | None; commands ended before that delay |
| Stirling-PDF, both sides | Skipped; run-1 ended before 60 s | Present on reference; not captured on candidate | run-2, both sides | None; commands ended before that delay |
| OWASP, both sides | 30 s requested after 60 s; profiler succeeded while run-1 was active | Present on both sides | None | 30 s requested and succeeded in run-4 |

For main, OWASP has a normal native window in its legacy full scan.
Conductor's requested window starts near the end of its 64.665-second
scan and ends with the process; it also spans output work and is not a
full 30-second engine-only window. Main Thingsboard and Stirling-PDF have
no native normal window because their full scans end before its delay.
The current captures are retained. No missing or skipped window is read
as zero work.

The evidence root is
`/drive-testcomp/opentaint-go-rules/run1-full-work-batching-20261023`.
The following receipts bind the statements above:

| Receipt, relative to the evidence root | What it establishes |
| --- | --- |
| `preparation/comparison-launch.json` | Exact candidate/reference commits, remote main check, frozen runtime, model/rule/extension input census, harness hashes and project order |
| `preparation/comparison-terminal.json` | All eight paired commands closed; final source and input checks both true |
| `preparation/main-cohort-closed.json` | All four fresh main command statuses, revisions and terminal summary hashes |
| `preparation/main.patch` and `preparation/campaign-plan.json` | Diagnostic-only main diff, configured heaps/timeouts and model paths/hashes |
| `preparation/reference-runtime-equivalence.json` | No tracked runtime/rules difference between `3179b9a22` and the specification's `8ab1445b8` reference |
| `<reference-or-candidate>/<project>/bidi/{manifest,summary}.json` | Exact direct-Gradle command/JVM arguments, terminal status, iteration markers, profiler/dump outcomes and command timing |
| `main-reference/<project>/main/{manifest,summary}.json` | Fresh main arguments, terminal evidence and legacy timing scope |
| `validation/closed-metrics.json` | All 12 closed commands, each engine/barrier status and duration, heap pauses, bounded counters and host pressure |
| `validation/closed-campaign-summary.json` | Paired sink counts, latest reached/COMPLETE/accepted runs, capture file sizes and timestamps |
| `validation/finding-comparison/<side>/<project>/{comparison,closed-inputs,index-receipt}.json` | Main comparison, original report hashes, strict missing keys and bounded parser/index controls |
| `validation/finding-comparison/reference-vs-candidate/<project>/` | Complete primary/strict key-count comparisons and difference lists |
| `validation/pdf-exit-location-cases-20261023/` | Fresh return-location extraction, project/runtime source hashes, unchanged-function controls and classification limits |
| `validation/conductor-precision-case-study/` | Added candidate identities, source/rule snapshots and unresolved witness checks |
| `validation/profile-analysis/{summary.json,summary.md}` and `<side>/<project>/index.json` | Complete all-12 CPU/allocation analysis, actual iteration windows and recording/native coverage |
| `validation/profile-analysis/cpu-caller-attribution/` | All-eight CPU-only builder caller/activity intersections, with exact sample denominators |
| `validation/profile-analysis/compact-validation/receipt.json` | Independent stock/compact event, weight, stack and normalized-fingerprint equality control |
| `validation/profile-analysis/{first-checkpoint-receipt,second-checkpoint-receipt,startup-failure}.json` | Explicit extraction startup failure and intentional checkpoint interruptions; no analyzer rerun |
| `preparation/previous-capture-cleanup-latest.json` | Audited deletion of 80 closed previous raw captures, 258,762,225,573 bytes; compact evidence preserved |

No new analyzer or comparison configuration is introduced by this report.

## Selected work reductions in Thingsboard run-1

The candidate's failed prefix has 4,464,131 scalar supply offers, retained
as 24,143 complete-region rows and 319,090 distinct batch-local memberships.
It removes 4,145,041 repeated memberships, or 92.85% of offered atoms.
The counts sum admissions across batches; they are not live heap sizes.
Upstream binding, composition and packet creation still run.

Completed singleton matching-FLOW blocks replace 5,544,558 raw cleaner
calls with 1,437,375 bulk calls. That removes 4,107,183 calls, or 74.08%,
for those same completed local inputs. This subset is only 1.60% of the
89,707,397 recorded bulk input visits. Raw scheduled slots include other
cases and cannot be read as physical scalar calls removed.

The global static service is small in fields and supplied atoms: the
candidate has 128 reached regions, 117 supplied regions and 94 supplied
FULL atoms. Its END state still has 850,928 unanswered owner rows and
6,062,732 memberships. Reference has 891,705 rows and 5,014,242 memberships.
These differ in progress and ordering. They are not a matched memory cut
and do not count actor pending image roots.

## All-project CPU and allocation overview

The complete analysis covers all 12 closed commands. The tables use
`continuous.jfr` and the explicit phase intervals. Separate `run1.jfr`
files were also checked; their samples are not added to the continuous
recording. All requested phase intervals below lie within the observed
event span. This is a temporal coverage check, not proof of complete
sampling.

Each arrow is reference → candidate. CPU percentages use Java execution
samples with stacks in that row. Allocation percentages use that row's
JFR allocation-sample weight. These weights estimate allocation traffic;
they are not live heap sizes. Scope percentages overlap.

### Run-1

Thingsboard has no COMPLETE or accepted run-1. Its rows stop at the first
heap capture. Conductor and Stirling-PDF use their complete run-1; their
latest reached run is run-2. OWASP uses complete run-1, although its latest
reached run is run-4 and its accepted forward report is run-3.

| Project | Java samples | Allocation weight GiB | Builder CPU% / allocation% | Cleanup-chain CPU% / allocation% |
| --- | ---: | ---: | --- | --- |
| Thingsboard | 42,994 → 36,939 | 905.883 → 858.297 | 32.73 / 19.85 → 34.78 / 21.08 | 14.47 / 25.31 → 9.75 / 15.35 |
| Conductor | 3,768 → 3,653 | 49.054 → 45.349 | 50.03 / 39.48 → 50.04 / 43.85 | 8.70 / 19.03 → 5.94 / 12.74 |
| Stirling-PDF | 4,210 → 4,476 | 63.024 → 56.462 | 66.75 / 61.74 → 67.20 / 66.76 | 5.13 / 11.88 → 3.24 / 4.33 |
| OWASP | 10,554 → 11,106 | 227.266 → 208.855 | 32.76 / 31.70 → 29.70 / 34.47 | 23.38 / 39.38 → 22.11 / 32.12 |

A separate CPU-only pass identifies actual activities within builder
stacks. The following values use all run-1 Java samples as the denominator,
not just builder samples. `resolve`, `keys`, aliases and rule forms can
overlap; they must not be added. Allocation traffic was not attributed
to these finer activities by this CPU-only pass.

| Project | Alias CPU% | Resolve CPU% | Rewriter CPU% | JIRRuleForms CPU% | Builder samples from `process`, % of builder |
| --- | ---: | ---: | ---: | ---: | ---: |
| Thingsboard | 16.87 → 18.15 | 7.86 → 8.07 | 2.92 → 3.13 | 3.64 → 3.58 | 94.33 → 94.84 |
| Conductor | 29.72 → 28.39 | 6.85 → 7.36 | 7.80 → 8.16 | 4.30 → 4.52 | 100.00 → 100.00 |
| Stirling-PDF | 53.37 → 53.89 | 3.18 → 3.53 | 4.30 → 4.40 | 5.82 → 5.43 | 100.00 → 100.00 |
| OWASP | 13.94 → 11.48 | 3.11 → 2.76 | 2.09 → 2.07 | 11.58 → 11.81 | 100.00 → 100.00 |

Call-plan construction is a large existing scope in every project. Alias
work is especially large in Stirling-PDF and Conductor. OWASP also has a
large cleanup chain and rule-form scope; its sink-builder frames account
for 8.50% → 8.72% of all run-1 Java samples. These are measured stack scopes,
not estimates of removable work. A builder stack proves construction,
but does not distinguish a first build from a rebuild. A `process` stack
does not expose the input base or the later `plan.touched` test result.
Those fractions remain unknown. The broad `FormsCache.withForm`/payload
wrapper encloses ordinary form execution and is not a rebuild-cost scope.

### Run-2

Run-2 is separate from the run-1 target. Conductor and Stirling-PDF stop
at their run-2 heap capture and have accepted run-1 only. OWASP completes
run-2 and later accepts run-3 before run-4 times out. Thingsboard never
reaches run-2. The new bulk-cleanup loop and canonical temporary-supply
admission do not execute in run-2.

| Project | Pre-capture/complete span s | Java samples | Allocation weight GiB | Summary-store CPU% / allocation% | Cleanup-chain CPU% / allocation% |
| --- | ---: | ---: | ---: | --- | --- |
| Conductor | 66.982 → 102.372 | 11,657 → 10,839 | 277.706 → 294.137 | 39.96 / 47.58 → 40.99 / 46.94 | 3.98 / 5.98 → 3.96 / 5.39 |
| Stirling-PDF | 180.163 → 173.715 | 23,786 → 21,504 | 660.888 → 636.115 | 45.95 / 50.98 → 45.71 / 50.76 | 6.63 / 10.05 → 6.52 / 10.34 |
| OWASP | 367.011 → 381.426 | 16,671 → 17,275 | 411.270 → 416.046 | 6.52 / 7.35 → 6.71 / 7.69 | 62.27 / 62.27 → 56.75 / 63.13 |

Summary-store stacks dominate the selected scopes in Conductor and
Stirling-PDF run-2. Trie operations are also large: 19.40% → 18.94% and
19.80% → 21.88% of Java samples, respectively. OWASP run-2 instead has
a large cleanup chain, including `TaintClean` at 30.32% → 26.42%. These
later-run costs do not establish a run-1 regression.

### Legacy main

Bidirectional run-1/run-2, latest reached and accepted-forward labels are
**NOT_APPLICABLE** for main. These denominators cover only its legacy
full-scan phase. The Thingsboard command remains incomplete because its
prescan timed out. Main and the new core do different work; these totals
are not an equal-work speed ratio.

| Main project | Legacy full-scan Java samples | Allocation weight GiB |
| --- | ---: | ---: |
| Thingsboard | 6,585 | 48.763 |
| Conductor | 4,849 | 34.921 |
| Stirling-PDF | 1,288 | 9.450 |
| OWASP | 7,484 | 66.897 |

### Coverage and extraction controls

Whole-phase tables, normal windows and late windows stay separate.
Thingsboard and OWASP have run-1 normal native captures near seconds
60–90. Conductor and Stirling-PDF finish run-1 before that delay; their
whole-run JFR is used. The final-30-second JFR slice ends before heap
capture or at engine completion; a short run can give a shorter slice.
OWASP's near-timeout native capture belongs to run-4, not run-1.

All eight run-1 Java-sample denominators above have stacks. Their largest
observed CPU stacks are 100–115 frames, below the 256-frame capture depth,
and none has a JFR truncation flag. Some allocation weight has no stack;
the outputs retain it in the allocation denominator. The Thingsboard
normal native captures also contain substantial parallel-GC work:
43.82% → 45.20% of native samples have both `GangWorker::loop` and a G1
frame. This is positive GC-worker evidence in that window, not a complete
GC CPU measure or a percentage of Java execution samples.

The compact `RecordingFile` adapter was checked against an independent
stock `jfr print --json --stack-depth 256` pass over the closed Thingsboard
reference recording. All selected event counts, each phase/window count,
allocation weight, depth histogram and null/unknown/truncated-frame count
match. An order-independent SHA256 sum also matches for normalized events,
including time, class, GC fields and every method/line/BCI. This adapter
changes extraction only. It does not change analyzer parameters.

Decoding began only after the authoritative eight-command CLOSED receipt
and live-PID/source/input checks. Each pass used one CPU, a 2 GiB address-
space limit, a 768 MiB exporter heap and a 6 GiB available-memory floor.
No complete JSON export was retained. An initial stock-exporter startup
failure, caused by its default class-space reservation, is recorded.
Two later SIGINT exits are intentional completed-directory checkpoints
used to change analysis order and enable the validated adapter. Their
partial next-recording results were not accepted. Completed indexes
were stat/manifest-validated before reuse. These are analysis interruptions,
not failed analyzer commands.

The compact-control receipt, separate paired/main analysis receipts,
first/second checkpoint receipts, source line-table copies, caller
supplements and normal/late coverage all remain under
`validation/profile-analysis/`. The resource receipts exclude earlier
checkpoint work where stated; they must not be added as a complete
analysis CPU census. The heap ownership and diagnostic classification
below use separate retained-state measurements.

## Thingsboard run-1 JFR scopes

The continuous recordings cover both failed run-1 prefixes before their
heap captures. The reference interval is 186.139 s, with 42,994 execution
samples that have stacks. The candidate interval is 318.440 s, with 36,939
such samples. Their allocation sample weights are 972,684,046,728 and
921,589,150,680 bytes, respectively. These are JFR allocation weights,
not object counts, retained bytes or an exact allocation census.

The table gives inclusive stack scopes over these pre-capture intervals.
An allocation weight in GiB is a byte-weight total divided by 2^30.

| Stack scope | Execution-sample share, reference → candidate | Allocation weight GiB, reference → candidate | Allocation-weight share, reference → candidate |
| --- | ---: | ---: | ---: |
| Return cleanup chain | 14.47% → 9.75% | 229.311 → 131.747 | 25.31% → 15.35% |
| Explicit `JIRCallPlanBuilder` work | 32.73% → 34.78% | 179.773 → 180.945 | 19.85% → 21.08% |
| `FormsCache` payload wrapper | 86.04% → 83.90% | 789.863 → 733.324 | 87.19% → 85.44% |
| New cleanup metadata compilation | absent → 0.12% | absent → 0.389 | absent → 0.05% |
| New cleanup statistics methods | absent → 0.13% | absent → 0 sampled | absent → 0 sampled |
| Temporary static-supply map | absent → 0.07% | absent → 0.478 | absent → 0.06% |

The cleanup scope has a smaller sample share on the candidate. The explicit
call-plan builder remains a large scope. The generic cache-payload wrapper
also encloses execution of existing forms. Its share must not be read as
rebuilding work. The new metadata, cleanup-statistics and temporary-map
scopes are individually small in these samples. Zero sampled weight does
not prove zero cost.

The normal window is run-1 seconds 60–90. The late window is the final
30 seconds before heap capture. Both are slices of the continuous JFR;
the late slice is not the separate native near-timeout profile.

| JFR slice / side | Execution samples with stacks | Allocation weight GiB | Cleanup sample / allocation-weight share | Builder sample / allocation-weight share |
| --- | ---: | ---: | ---: | ---: |
| Normal reference | 8,340 | 195.461 | 14.83% / 24.42% | 19.72% / 10.35% |
| Normal candidate | 4,880 | 78.839 | 5.47% / 8.77% | 45.14% / 40.52% |
| Late reference | 1,162 | 30.662 | 20.48% / 31.65% | 30.72% / 20.95% |
| Late candidate | 553 | 11.297 | 11.03% / 14.01% | 40.14% / 33.71% |

The late slices have few execution samples and extensive GC pauses. Their
shares describe the sampled execution, not all elapsed or process CPU time.
Inclusive scopes overlap, so their percentages cannot be added. The pair
also has different failed prefixes, different allocation activity and
different host CPU availability. The intervals are not matched closure
progress. Sample shares, absolute weights and the process-CPU totals above
must therefore stay separate; they do not establish a completed-run speedup
or identify one cause of the longer candidate wall time. The event span
checks establish temporal coverage, not complete sampling.

<!-- heap-overview:start -->
## OOM ownership by iteration

All six OOM snapshots have validated owner accounting. MB means
1,000,000 bytes. Each cell below is a complete exact retained union.
The unions overlap; do not sum columns. The iteration column is reached /
latest COMPLETE / accepted forward.

| Snapshot | Iterations | Method stores MB | Summaries/subscriptions MB | Demand/queries MB | Static service/points MB | Form owners MB |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Thingsboard reference | run-1 / none / none | 1,849.490 | 1,826.225 | 0.000 | 1,209.581 | 720.392 |
| Thingsboard candidate | run-1 / none / none | 2,131.592 | 2,071.342 | 0.000 | 1,067.923 | 724.145 |
| Conductor reference | run-2 / run-1 / run-1 | 1,528.167 | 1,102.564 | 75.554 | 0.000 | 214.085 |
| Conductor candidate | run-2 / run-1 / run-1 | 1,515.882 | 1,113.941 | 75.004 | 0.000 | 213.783 |
| Stirling-PDF reference | run-2 / run-1 / run-1 | 3,935.884 | 1,127.696 | 53.340 | 0.000 | 223.733 |
| Stirling-PDF candidate | run-2 / run-1 / run-1 | 3,883.873 | 1,168.109 | 54.078 | 0.000 | 224.011 |

All four run-2 snapshots have zero run-1 static-service, point-row and
pending-image instances. Complete class-count controls confirm these
zeros independently of the empty queries. Their OOMs are not retained
run-1 static-resume state. Current method execution and
summary/subscription storage are substantial instead. Stirling-PDF's
method-store union alone is 3,935.884 / 3,883.873 MB against
7,628.624 / 7,619.778 MB of live heap.

| Run-2 snapshot | Initial-store owners | Admitted initial memberships | Maximum per owner |
| --- | ---: | ---: | ---: |
| Conductor reference | 17,465 | 746,484 | 640 |
| Conductor candidate | 17,465 | 748,208 | 657 |
| Stirling-PDF reference | 7,258 | 746,768 | 954 |
| Stirling-PDF candidate | 7,258 | 761,618 | 969 |

These memberships span method owners; they are not globally unique
InitialAp objects. The snapshots stop at memory thresholds at different
progress. A larger cell is not an equal-state regression proof. Run-2
storage must not be presented as run-1 memory. Each snapshot has an
explicit `owner-analysis-scope.json` and `owner-summary.json`, indexed by
the [final heap-analysis receipt](../../../run1-full-work-batching-20261023/validation/heap-final-analysis-index.json).
<!-- heap-overview:end -->

## Thingsboard run-1 heap owners

Both Thingsboard captures are forward run-1 FULL, limit 1, with no
COMPLETE or accepted forward iteration. Their complete root-class
partitions account for 11,358,904,512 / 11,367,765,400 live bytes.
These are live-heap measurements, not HPROF file sizes.

The following exact retained unions overlap and must not be added.

| Owner union | Reference MB | Candidate MB |
| --- | ---: | ---: |
| Method execution stores | 1,849.490 | 2,131.592 |
| Summaries and subscriptions | 1,826.225 | 2,071.342 |
| Static service and point directories | 1,209.581 | 1,067.923 |
| Temporary static batches | 470.269 | 36.776 |
| Supply containers within temporary batches | 428.921 | 6.903 |
| Interest lists within temporary batches | 28.630 | 17.881 |
| Unanswered point rows | 599.048 | 526.530 |
| Unanswered FLOW images | 207.657 | 208.475 |
| Lowered forms and their owners | 720.392 | 724.145 |

The supply-container measurement isolates a selected storage effect.
Reference batches retain 2,469,866 scalar packets in 73,049 ArrayLists;
the two largest batches have 1,038,287 and 928,332 packets. Candidate
storage has 1,521 complete Pattern rows in 73,489 canonical FULL maps.
Every inspected candidate supply owner uses the canonical profile and
has no ordered-list lane. A map row can hold multiple FULL atoms.
Packet and region-row counts therefore do not measure duplicate atom
memberships and must not be divided to claim a deduplication factor.
Keys preserve the complete Pattern, not only a field name.

The temporary lanes also contain 150,806 / 92,897 interests,
27,460 / 13,705 write notifications and 2,017 / 105 writer registrations
at capture. These are uncommitted lane contents, not cumulative global
writer counts. The [reference](../../../run1-full-work-batching-20261023/validation/heap-reference-thingsboard-batch-filter/batch-filter-summary.json)
and [candidate](../../../run1-full-work-batching-20261023/validation/heap-candidate-thingsboard-batch-filter/batch-filter-summary.json)
inventories keep these lanes separate.

Point rows fall from 786,620 to 333,488. Their pending memberships fall
only from 4,765,614 to 4,726,793. Bulk cleanup shares an active suffix
point, but keeps the existing per-mark image storage. A smaller point
count therefore does not remove all image and membership work.

The captures have different progress. Admitted initial memberships are
496,563 across 73,049 actors and 566,194 across 73,489 actors. The larger
method and summary unions are not an equal-state regression proof. A
complete fixed point and its cost remain unknown for Thingsboard.

The global FLOW writer directory has only 1,156 reference rows
(756 static-origin and 400 nonstatic-origin), and 3,908 candidate rows
(3,517 and 391). Reference has 3,174 local WriterKeys. The logged
`writerAdmissions` counter counts admitted atom memberships, not
physical writer rows. A large physical writer directory does not explain
the current heaps.

### The dominant static field is a mutable TenantId cache

Exact AP-table decoding identifies
`org.thingsboard.server.common.data.id.TenantId.tenants`, of type
`org.springframework.util.ConcurrentReferenceHashMap`. The captured
project source has:

```java
static final ConcurrentReferenceHashMap<UUID, TenantId> tenants = ...;

public static TenantId fromUUID(UUID id) {
    return tenants.computeIfAbsent(id, TenantId::new);
}
```

The declaration is at line 32 and the call at line 42. This is a mutable
UUID-to-TenantId cache. A final reference does not make its contents
immutable. It is not an enum field. The
[source binding](../../../run1-full-work-batching-20261023/validation/tenant-cache-source-binding.json)
pins the file and hash. This source identity does not prove which
analyzer write or alias operation admitted the cache.

Every queried point for this field has the named static initial premise
`(S, TenantId.tenants, *, {}, *)`: STAR tail, empty field exclusion and
abstract mark. The current image varies with its operation; the probe
does not export its full fact payload.

| TenantId.tenants state, run-1 | Reference | Candidate |
| --- | ---: | ---: |
| Service pending owners | 810,718 | 394,418 |
| Requested but not reached FULL atoms | 649 | 649 |
| Reached but not requested FULL atoms | 1 | 1 |
| Requested and reached FULL atoms | 28 | 28 |
| Local point rows with this initial | 768,998 | 328,869 |
| Local pending memberships | 4,134,721 | 4,083,303 |
| Exact local point retained union MB | 544.534 | 473.478 |

The reference service has 891,705 pending owners and 5,014,242
memberships across 57 field regions. Candidate has 850,928 owners and
6,062,732 memberships across 56 regions. Service owners also cover other
continuations; they are not the local point count.

The complete per-point census for this exact initial separates the
remaining work:

| Point site | Reference rows / memberships | Candidate rows / memberships |
| --- | ---: | ---: |
| CleanerInput | 745,176 / 745,176 | 304,650 / 608,450 |
| CallStageInput | 21,901 / 3,298,381 | 22,285 / 3,382,970 |
| BoundaryInput | 1,921 / 91,164 | 1,934 / 91,883 |

CallStageInput holds about 83% of the candidate field's memberships in
about 6.8% of its point rows. Bulk return cleanup mainly reduces
CleanerInput rows. These counts locate the stored memberships; they do
not prove that different marks have interchangeable image histories.
A global distinct-root count is not a per-point compressibility test.

The [reference exact field and point census](../../../run1-full-work-batching-20261023/validation/heap-reference-thingsboard-field-names-points/exact-fullkey-masks-and-points.json)
and [candidate census](../../../run1-full-work-batching-20261023/validation/heap-candidate-thingsboard-field-names-points/exact-fullkey-masks-and-points.json)
bind complete accessor paths, FULL names, encoded owners and site-class
identities to each heap.

### Selected producer keys explain a larger request subset

Both actual Semgrep providers have the same nonnull filter of 381
serialized item IDs. Complete HashMap entry extraction matches the
HashSet's size and produces 381 unique strings on each side. A null
filter keeps all items. An empty nonnull filter rejects identified items;
null-ID items remain selected. These are serialized item IDs, not
top-level mark-group IDs.

The selected raw normal Assign index contains 231 FullKeys. The exact
join uses the encoded group, including its primitive flag, plus the full
mark name. It does not join bare runtime IDs or displayed names across
heaps.

| TenantId.tenants mask | Has selected raw normal Assign | No selected raw normal Assign |
| --- | ---: | ---: |
| Requested | 124 | 525 |
| Reached | 1 | 0 |
| Processed | 28 | 0 |

A separate reference run-1 scalar census counts 4,765,614 global pending
memberships. Its row count exactly matches both the PendingFlowImages
class census and the summed PointRow pending sizes. It exports only
runtime atom IDs, with explicit 8-million-row and 128-MiB limits. The
selected-producer join gives:

| Global reference pending classification | Memberships |
| --- | ---: |
| Has selected raw normal Assign | 992,526 |
| No selected raw normal Assign | 3,773,069 |
| FULL name not decoded in this probe | 19 |

The middle row is 79.1728% of the total. It is a conditional liveness
signal, not an analyzer-wide producer-absence certificate or a measured
runtime saving. The weight scope is global, not specific to TenantId.
Native, custom, synthetic and default GEN paths still require closure;
unknown paths must remain open. No mark legality or runtime eligibility
changes are made here.

This differs from the broad raw census that ignores selection. That
join has only 78,300 memberships without any raw normal Assign
(about 1.64%). The broad percentage does not bound the selected-producer
case. The [source-bound selected join](../../../run1-full-work-batching-20261023/validation/heap-reference-thingsboard-rule-payload-memberships/selected-normal-producer-field-and-weighted-join.json)
retains this distinction, all input hashes and the 19 unknown atoms.

An independent join reproduces every count. Of the 3,773,069 memberships,
3,694,769 have raw normal producers whose item IDs are all unselected;
78,300 have no raw normal producer. The
[independent receipt](../../../run1-full-work-batching-20261023/validation/producer-mark-closure/selected-normal-producer-independent-join.md)
also checks the raw inputs and relevant provider definitions across sides.

Source inspection explains how the two rule sets meet:

```text
completed prescan -> selected serialized source items S
normal GEN       -> source query with allRelevant=false -> selected S
return cleanup   -> metadata query with allRelevant=true -> broader masks
FLOW cleanup     -> requests for those broader FULL masks
pending owner    -> image membership until a concrete answer or release
```

The broader metadata query does not itself execute GEN. It can therefore
request a legal mark whose normal producers are all excluded. This is the
main new request-admission opportunity. A complete proof must still cover
every other GEN/input/native/rename route. If those routes add possible
keys, the final producer set and weighted bound must expand. This finding
does not justify changing cleaner masks, mark legality or negative rules.

### Rule-cache ownership and remaining bounds

The reference SpringRuleProvider root retains 1,292.479 MB. Almost all
of that subtree belongs to its delegated JIRMethodExitRuleProvider
(1,292.479 MB), then JIRSemgrepRuleProvider (1,292.479 MB). The Spring
context itself retains 1.999 MB. Candidate has the same ownership chain,
with a 1,292.143-MB delegated subtree and a 2.004-MB context. These are
nested object-retained measurements; do not add them.

Both actual JIRTaintRulesProvider instances have
`ignoreSinkEndFacts=false`. Reference's ordinary sink-role method-cache
union is 1,082.107 MB; the six no-end-facts cache maps retain only 288
bytes. Its resolved sink-rule object union is 1,056.694 MB and its
resolved condition union is 680.181 MB. These unions overlap. The class
census includes 3,737,698 exit-sink objects, 483,364 call-sink objects and
57,796 entry-sink objects. These are resolved data objects, not counts of
satisfied sinks or reports.

SpringRuleProvider does not expose the pre-resolution bidirectional
provider capability. The effective view can strip end actions only
after delegated resolution. However, the selected wrapper shares the
same TaintConfiguration, and the original provider can retain ordinary
caches. Adding a wrapper does not by itself free those caches and can
create a second cache. The 1,082.107 MB is therefore not a removable-byte
estimate.

**The isolated END-list payload remains UNKNOWN.** Two probes failed
because `.size` is not a field on every list implementation. A corrected
all-list-root probe was stopped after about ten minutes in MAT's own
object-cache conversion. The failed and stopped attempts remain in the
[cache/weight scope receipt](../../../run1-full-work-batching-20261023/validation/heap-reference-thingsboard-rule-payload-memberships/analysis-scope.json)
and [bounded stop record](../../../run1-full-work-batching-20261023/validation/heap-reference-thingsboard-end-lists-names/bounded-query-stop.json).
This tool cost is not analyzer CPU evidence. Candidate weighted and
broad payload scans were not continued at closure.

Reference has only 1,418 interned InitialAp objects globally. Its
626,715 premise arrays use 82.929 MB of shallow storage, far below the
method-store union. A new storage format needs a complete per-method,
per-context key census. Equal initial prefixes alone do not permit a
Cartesian product of marks: `I_T → T` and `I_U → U` must keep their
correlation. No new origin-family or image-cohort layout is selected by
these measurements.

The reference root partition accounts for 1,626.599 MB under IR RAM
storage. This is accounting context; IR storage remains out of scope.

All six primary scopes are closed. Reference Thingsboard has 39 of 40
validated queries: its 183-MB ungrouped dominator detail was not read.
Its complete 990-row root-class partition and every required owner
union are separately valid. The other five primary reports pass all 40
queries. Exact field/name reports pass their query checks; a narrow
reader allowance handles 772 generated ZIP members while retaining
the original CSV, row and total-byte limits. The scalar weighted query
has its own explicit limits and complete count controls.

The [final analysis index](../../../run1-full-work-batching-20261023/validation/heap-final-analysis-index.json)
binds all accepted scopes, every failure and deferred probe, heap
hashes, unchanged runtime HEAD `fb84969c0`, and the final no-MAT process
check. It does not turn an auxiliary failure into a zero result or claim
a completed Thingsboard fixed point.

## Avoidable diagnostic work found in the source

The JVM interpreter implements UnresolvedCallObserver even when external
tracking is disabled. SharedObjects returns that interface by a cast.
RunMethodCallServices then accumulates exact unresolved-call TAINT facts
before invoking the callback. The callback immediately returns when its
ExternalMethodTracker is null. All fixed manifests omit the tracking flag;
the frontend flag defaults to false.

This diagnostic accumulator does not feed rule transfer or summary replay.
The separate CalleeSubscriptions accumulator does. Removing the disabled
diagnostic lane is a new proposal, not a change in this iteration. Both
heaps confirm that the actual tracker is null.

| Thingsboard run-1 owner | Reference entries / retained union MB | Candidate entries / retained union MB |
| --- | ---: | ---: |
| Disabled diagnostic archive | 355,254 / 103.423 | 419,811 / 115.844 |
| Semantic caller subscriptions | 2,352,031 / 417.771 | 2,660,073 / 470.070 |

The reference entries sum to all 2,707,285 accumulator entries. Most entries
are semantic caller inputs. The diagnostic archive is avoidable, but its
103.423 MB union is about 0.91% of the reference live heap. It does not
explain the full OOM or promise a 2x saving. These owner unions overlap
other unions; do not sum them as additional heap bytes. Evidence is in
`validation/heap-<side>-thingsboard-diagnostic-archives/query-validation.json`.

A separate compact profile read measures this path. In the Thingsboard
pre-capture intervals, `observeUnresolved` has 89/42,994 reference samples
and 67/36,939 candidate samples: 0.207% and 0.181%. Its allocation weights
are 457 MB and 560 MB, about 0.047% and 0.061% of each total. These are
small sampled traffic scopes. They do not rank diagnostics above alias
construction for CPU, and they do not measure retained archive bytes.

The generic accumulator's `add` method has 381 and 346 execution samples.
Only 45 and 37 are under the observer; 336 and 309 are under caller-input
admission. Thus most sampled accumulator admission work serves the
separate caller-input store. The targeted read preserves the all-project
analysis receipt and passes the six slice-comparison controls. Its evidence
is `validation/profile-analysis/targeted-unresolved` under the campaign.

## Conclusions and evidence limits

New optional candidate counters issue at least 285,477,675 shared atomic
updates in the Thingsboard prefix. The same statistics flag therefore
does not provide equal observer cost. But the count alone does not show a
dominant CPU cost. The pre-capture JFR has only 3 reference and 6 candidate
execution samples in atomic-operation frames, or 0.007% and 0.016%.
Inlining and sampling limit this observation. Together with the small
new-statistics scope above, it does not support assigning the main wall-time
increase to the atomic-update count. These instrumented runs still do not
isolate production-default overhead.

Pre-capture GC pauses total 65.150 s and 104.532 s in Thingsboard. The last
30 seconds contain 27.176 s and 28.461 s of pauses. The last full collections
leave about 11,060 MiB and 11,061 MiB live. This is a retained-state limit;
it does not prove an infinite loop. The Thingsboard owner data above show
retained stores and unanswered memberships; they do not prove termination.

All six OOM owner scopes are closed: both Thingsboard run-1 heaps and both
Conductor and Stirling-PDF run-2 heaps. Snapshot file sizes are not live
retained byte counts. The final analysis index binds the complete primary
scopes, accepted targeted followups, and all stopped/failed optional probes.
The reference oversized dominator detail and the separate END-list payload
remain explicit limits. No analysis result is inferred from those failures.
IR storage is measured separately and stays out of scope.

| Project | Performance conclusion | Precision conclusion |
| --- | --- | --- |
| Thingsboard | Run-1 still OOM. Selected duplicate work falls, but retained requests, method/summary stores and call construction remain large. A complete timing is unknown. | No accepted forward report; both the new core and main baseline are incomplete. No semantic no-FN conclusion follows. |
| Conductor | Run-1 completes in 20.516 s versus 15.396 s. No observed run-1 wall-time improvement; run-2 OOM is a separate store problem. | All seven main keys retained; 34 added unconfirmed unique candidates need witnesses. |
| Stirling-PDF | Run-1 completes in 32.499 s versus 30.000 s. No observed run-1 wall-time improvement; run-2 OOM is a separate store problem. | Two strict return locations remain missing; confirmed reporting-location loss does not prove branch/mark witness equivalence. |
| OWASP | Run-1 completes in 255.243 s versus 236.546 s; both exceed 60 s. Candidate run-3 is faster in this pair, but whole analysis still times out in run-4. | All 4,112 unique main keys retained at accepted run-3. The 9,179 raw Simple occurrences fail literal workflow trace-total parity. |

These are one-pair observations under recorded host/GC activity. They
establish neither a causal slowdown from the selected patch nor a completed
speedup. They do establish that the current two optimizations are insufficient
for the run-1 target. The next proposal separates a measured large request
admission opportunity from an unmeasured alias-rejection fraction and a
small disabled diagnostic archive.

Exact trie-node visit totals and physical header-copy totals were not
instrumented. Logical cleaner-call counts and JFR allocation weights do
not supply those totals. The same FULL-key/selected-rule join was not
weighted on the candidate heap. No candidate percentage is extrapolated.

The current campaign is
`/drive-testcomp/opentaint-go-rules/run1-full-work-batching-20261023`.
It retains command manifests, source/input hashes, per-run summaries,
bounded location comparisons, profiles, GC logs and heap-capture receipts.
It also retains fresh reference controls for all pre-existing validation
failures. The campaign removed 258.8 GB of closed previous raw captures
and kept their compact evidence.

The [next-iteration proposal](../design/bidirectional-full-work-batching-next-iteration-20261023.md)
uses the completed profile and heap scopes. Its producer certificate and
request-erasure proofs are future requirements, not established engine
theorems. No further analyzer optimization is selected or implemented by
this report.
