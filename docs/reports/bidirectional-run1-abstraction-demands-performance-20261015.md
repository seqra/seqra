# Run-1 abstraction demands after the resolver repair

Date: 15 October 2026. Status: validation and all four fresh main baselines pass; all optimized
scans are terminal. Corrected-control phase is also terminal; the all-twelve marker was written
at 03:36:02 UTC.
This report is for `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66` only. Terminal process status
is separate from engine completion, finding acceptance and the run-1 target.

The four optimized scans are now terminal. Only Stirling-PDF completes run-1 within 60 seconds.
ThingsBoard stops with OOM in run-1 and exports none of main's 14 sites. Conductor stops with
OOM in run-2 after accepting run-1. Stirling-PDF stops at run-4 timeout with three strict main
location losses. OWASP completes run-3 with main's sink identities, but its run-1 takes 349.964
seconds. The implementation therefore does not pass the fixed-project time and finding gates.
All corrected-control scans, recording exports and compact profile reviews are complete.
All four guard heaps and the supplementary container and initial census have been analyzed. The
command-loss diagnostic is complete with the coverage limits below. It identifies a specified
source-rewriter cut on the missing main URL route. The next proposal uses the measured owner
splits and keeps the finding-acceptance failure explicit.

The abstraction always emits the eligible abstract `*` initial. Precomputed and dynamic mark
requests add separate supplied concrete-mark initials; they do not replace the abstract initial
or form a conjunction of requested marks. This behavior is unchanged by the resolver repair.
The [abstraction tests](../../core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow/bidi/Run1AbstractionTest.kt)
cover no request, precomputed interest, dynamic interest, separate requested marks and late
suppliers. Unsupplied interest never creates a concrete fact.

## Runtime and validation

The existing override cache now uses both method and receiver class as its key. Real bytecode
tests cover broad and narrow lookup orders, an empty override subtree, and the actual joined
prescan-to-runtime handoff after prescan owners are released. All four tests fail on the old key
and pass on the repaired key, with no errors or skips. The broader gate passes with 1,406 common
and 395 JVM tests, zero failures, errors or skips. Common results are up to date for the unchanged
common sources; the JVM suite executes with the repair and four new tests. Go, JVM SAST and root
test sources compile, and `:classes` passes. Gradle uses one task worker; test heaps and suite
coverage are unchanged. This is separate from the frozen project scan settings.

| Runtime | Revision | Scope |
| --- | --- | --- |
| Main | `f945f92c5ae554ff4f5fc7696ee5a269210478bb` | Saved main with the recorded guard-capture hook |
| Prior optimization control | `77959b0d8ad870f2bc4fd2f8eaf38044bd302b3c` | `a23ad7fcd` plus the identical receiver-class cache repair |
| Optimized | `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66` | Abstraction-owned demands plus the repair |

The control and optimized runtime contain byte-identical repaired resolver code. Comparing
them avoids assigning this correctness repair's workload changes to the abstraction redesign.
IR storage and `SoftReferenceManager` remain unchanged. No `WeakIdentityCache` was added.

## Latest iteration

Limits start at 1. The target is **run-1 engine time at most 60 seconds**, with prescan,
metadata setup and reporting measured separately. Optimized OWASP is terminal and successful
through **run-3, forward, limit 3**. Conductor completed and accepted run-1, then stopped with
OOM in **run-2, backward, limit 2**. ThingsBoard stopped with OOM in **run-1, forward, limit 1**,
before any engine completion or report acceptance. Stirling-PDF completed and accepted run-1
within the target, then stopped at the configured timeout in **run-4, backward, limit 4**;
its last completed engine and accepted report are run-3.

| Project | Latest reached | Last completed | Last accepted forward report | Run-1 engine |
| --- | --- | --- | --- | --- |
| OWASP | run-3 / forward / limit 3 | run-3 / forward / limit 3 | run-3 | 349.963792074 s; target missed |
| Conductor | run-2 / backward / limit 2; OOM | run-1 / forward / limit 1 | run-1 | 69.931044488 s; target missed |
| ThingsBoard | run-1 / forward / limit 1; OOM | None | None | 164.488070844 s; incomplete, target missed |
| Stirling-PDF | run-4 / backward / limit 4; timeout | run-3 / forward / limit 3 | run-3 | 30.914013567 s; target met |

All twelve processes are terminal. The following command times include Gradle startup,
prescan, engine runs, barriers and output. Main uses the legacy engine and has no run-N.
Its complete full-scan phase is reported separately below; it is not a run-1 engine timer.

| Project | Main command, s | Control command, s | Optimized command, s | Control run-1, s | Optimized run-1, s |
| --- | --- | --- | --- | --- | --- |
| OWASP | 167.395 | 476.968 | 736.687 | 247.764 | 349.964 |
| Conductor | 242.851 | 425.192 | 519.703 | 46.420 | 69.931 |
| ThingsBoard | 336.720 | 542.365 | 594.476 | 209.293; OOM | 164.488; OOM |
| Stirling-PDF | 71.236 | 1146.798 | 1111.093 | 56.634 | 30.914 |

Control and optimized have the same latest reached, completed and accepted iterations shown
above. Only optimized Stirling-PDF completes run-1 within 60 seconds; control also meets that
engine target on Conductor. ThingsBoard's earlier OOM is not a completion speedup. All pairs
are single instrumented observations under the recorded host conditions, not isolated causal
benchmarks. The complete source-bound control extraction is
`report-preparation/all12-terminal-controls-20261015T033600Z.json`; its compact timing and
capture-stat review is `report-preparation/all12-timing-summary-20261015T0338.json`.

The strict main comparison gives the following unique sink identities. Control and optimized
match in identities, spans and occurrence counts on every project. This is exported-location
parity between those two cores, not proof of equal internal witnesses or complete analysis.

| Project | Main | Optimized | Main identities missing | Additional identities | Last accepted optimized report |
| --- | --- | --- | --- | --- | --- |
| OWASP | 4,112 | 4,112 | 0 | 0 | run-3 |
| Conductor | 7 | 300 | 0 | 293 | run-1; later run-2 OOM |
| ThingsBoard | 14 | 0 | 14 | 0 | None; run-1 OOM |
| Stirling-PDF | 17 | 56 | 3 | 42 | run-3; later run-4 timeout |

Conductor also has one invalid export location. Its added candidates and Stirling-PDF's additions
remain unrefined; they are not classified as resolved false positives. Raw result multiplicity
is separate: optimized exports 9,179, 300, 0 and 128 rows, respectively. The cause reviews and
duplicate-row limits below remain part of the acceptance decision.

The [first measurement report](bidirectional-run1-abstraction-demands-performance-20261014.md)
records f965's OWASP run-3 and three run-1 graph-coverage failures. Those results are historical;
they cannot supply findings, timing or hotspot results for this repaired revision.

OWASP's repaired-revision setup took 85.413 seconds for prescan, 73.356919975 seconds for
static metadata and 0.440593004 seconds for the catalog. These costs precede run-1. The catalog
has 12,063 methods, 9,914 call edges and 27 unique positions; it has no declared static-mark
queries on this project. Base-wide cleaner interest remains present. The logged logical
memberships and shared set roots are not measured retained bytes.
Run-1 ended with `COMPLETE` at 01:35:30.214 UTC. Its barrier took 1.865538810 seconds; this is
separate from engine time. The compact forward frontier at 01:35:32.031 UTC proves report
acceptance before run-2 started. It does not prove final finding parity with main.

| OWASP iteration | Engine, s | Barrier, s | Result |
| --- | --- | --- | --- |
| run-1 / forward / limit 1 | 349.963792074 | 1.865538810 | COMPLETE; forward report accepted |
| run-2 / backward / limit 2 | 79.896573392 | 0.064836354 | COMPLETE |
| run-3 / forward / limit 3 | 59.881283548 | 0.200925397 | COMPLETE; forward report accepted |

OWASP stopped with `COMPLETE / STOP_RULE`. Command time was 736.687 seconds; sampled peak
RSS was 9.360 GiB. No OOM, timeout, invalid location or prescan error was logged for this case.
Its terminal controls and source binding are in
`report-preparation/partial-optimized-OWASP-20261015T014052.864162Z.json`.

The fresh strict main comparison retains all 4,112 unique sink identities, with no removed or
added unique identities and no span-change groups. Optimized exports 9,179 occurrences; the
5,067 extra occurrences are exact full-result duplicates within its own report. They are not
additional unique sites. Main has trace `codeFlows` for 3,619 results; optimized has none.
This establishes the same reported sink identities and spans against main, not full-result,
trace or related-location parity, nor absolute ground-truth recall. The current comparison is under
`report-preparation/OWASP-main-optimized-review-20261015T014121.733784Z`.

Source review establishes a many-to-one export path: distinct full bound sink-rule keys can
produce equal public SARIF values, and the serializer keeps every projected row. Legacy storage
instead groups by public string rule ID and statement. Multiple witnesses or completed runs
under the same full key do not independently create report rows. The actual internal keys behind
each measured duplicate group were not captured, so their differing fields remain unknown.
See `report-preparation/duplicate-result-source-review` for the source-bound explanation.

## OWASP run-1 workload evidence

These are current-revision run-1 counters. All twelve scans are terminal.

| Counter | Observed value | Meaning |
| --- | --- | --- |
| N1 request stores created | 0 | The removed runtime request store was not created |
| Peak active policies | 6,063 | Aggregate dynamic policies across actors, not request rows |
| Peak active policy mark cells | 299,471 | Aggregate logical memberships, not retained bytes |
| Peak pending mark members | 233 | Maximum observed one-actor current plus captured delivery frontier |
| Peak pending primitive capacity | 859 | Maximum observed one-actor cell capacity, not bytes or total policy size |
| Member scans | 229,617 | Supplier join member work |
| Accepted outgoing batches / members | 3,641 / 71,245 | Request transport; maximum batch width 32 |
| Policy admission calls | 24,589,178 | Includes local and incoming calls, including duplicate returns |
| Duplicate admitted mark memberships | 24,357,311 | Membership count; a different unit from admission calls |

The last two rows cannot be divided to obtain an exact duplicate-call rate. These counters do
not show a huge single-actor pending request frontier on OWASP. They do not measure the sum
of all actors' simultaneous pending frontiers. A broad queue rewrite remains deferred;
other projects still need their own evidence.

The completed normal text CPU capture has 3,896 samples, including 2,791 with application
frames. `JIRCallPlanBuilder.build` appears in 2,461 samples, sink preparation in 1,196, local
alias computation in 508 and signed normalization in 422. These inclusive paths overlap.
They are not removable CPU fractions, allocation weights or retained-memory measurements.
The profiler log reports `Done` and the terminal harness records exit 0. The raw text hash is
unchanged from the first bounded analysis. Its requested window, approximately
01:30:40.654–01:31:10.654 UTC, lies wholly within run-1. The bounded text analysis is
`report-preparation/optimized-OWASP-normal/provisional-20261015T013321.969662Z.json`.
The terminal binding above preserves the validation of that provisional artifact. The recording
export is complete in order 09 below. Existing aggregate form counters cannot separate first compilation and
rebuilding or assign their totals to each iteration; the profile cannot supply that missing count.

## Conductor memory guard

Run-1 completed in 69.931044488 seconds; its barrier took 1.974691883 seconds and its compact
forward frontier was accepted. Run-2 ended with `OOM` after 247.247770343 seconds. It did not
complete, and there is no accepted run-3 report. The run-2 engine time includes its guard dump.

The guard capture started at 01:46:23.382 UTC and completed at 01:47:01.418 UTC: 38.036 seconds.
It wrote 11,545,090,070 bytes to `projects/conductor/bidi/guard-live.hprof` in the current batch.
The capture is **run-2 / backward / limit 2**, distinct from the last accepted run-1 report.
The byte count is dump size, not retained heap. The actual run-2 ownership result appears
below, separately from the last accepted run-1 report.
Finding comparison must preserve this incomplete-progress limit and every observed missing site.
Command time was 519.703 seconds and sampled peak RSS was 9.631 GiB. Prescan took 67.124
seconds, static metadata 19.274945908 seconds and catalog setup 0.338787637 seconds. The terminal
source/capture binding is `report-preparation/partial-optimized-conductor-20261015T015003.150954Z.json`.
It verifies snapshot size by file stat only; no heap bytes were read.

The fresh sink comparison retains all seven observed main identities and their sink spans.
Optimized exports 300 unique identities: seven retained and 293 additional candidates. The
driver reports three CONFIRMED and 298 DEMAND findings; refinement stopped before a completed
backward handoff and run-3. The additions cannot be classified as resolved false positives
from this partial report. The large candidate excess does not pass precision acceptance.

One invalid-location diagnostic accounts for an unresolved export at a synthetic NATS
`Connection#createDispatcher` entry with a lambda context. Keep that mapping gap separate
from the seven retained main identities. The comparator classifies this as an incomplete or
invalid-input comparison, not a successful full-analysis/no-FN result. The current review is
`report-preparation/conductor-main-optimized-review-20261015T015023.020097Z`.

## ThingsBoard run-1 OOM

ThingsBoard started run-1 at 01:54:20.476 UTC. Prescan took 266.421 seconds, static metadata
80.247345929 seconds and catalog setup 1.684686020 seconds. These costs precede the engine.
The catalog has 51,953 methods, 117,581 call edges and 3,984 unique positions. Its 15,943,560
transitive access memberships are logical repeated needs over shared profiles and persistent
sets, not that many independent stored entries. The source counters record 7,344 access-set
roots and 64,027 membership additions; physical node bytes remain unmeasured.

Run-1 ended with OOM after 164.488070844 seconds. It did not complete or reach its forward
report barrier. Command time was 594.476 seconds and sampled peak RSS was 13.723 GiB.
The guard dump ran from 01:56:31.668 to 01:57:04.893 UTC, taking 33.225 seconds inside the
engine time. It wrote 17,944,326,258 file bytes. Its capture iteration is **run-1 / forward /
limit 1**. File stat confirms the size; the actual run-1 ownership result appears below.
The terminal binding is `report-preparation/partial-optimized-thingsboard-20261015T020218.313870Z.json`.

The strict comparison records all 14 main sink identities as missing and zero additions.
The valid optimized SARIF has zero findings. Source review establishes that F1 OOM returns
before report acceptance, leaving the report empty. This explains the missing output; it does
not establish a propagation defect at each site or guarantee recovery after another change.
All missing identities, spans and source context remain in
`report-preparation/thingsboard-main-optimized-review-20261015T020229.598929Z`.

| Run-1 counter | Value | Meaning |
| --- | --- | --- |
| N1 stores | 0 | Removed request stores remain absent |
| Peak policies / policy mark cells | 284,816 / 42,690,639 | Aggregate logical memberships, not retained bytes |
| Member scans / climbs | 325,282,471 / 319,832,979 | Supplier join and outgoing climb work |
| Outgoing batches / members | 2,409,238 / 163,334,653 | Accepted transport payload; maximum width 673 |
| Duplicate target unions | 146,142,925 | Failed member additions to a source phase's outgoing set |
| Policy admission calls / duplicate members | 12,336,171 / 130,566,508 | Different units; no exact duplicate-call rate |
| Peak pending members / primitive capacity | 1,407,803 / 2,794,396 | Maximum for one actor, not a simultaneous aggregate |
| Peak pending links / captured roots | 26,033 / 288 | Maximum for one actor |
| Maximum service delay | 101.659478502 s | Observed pending service delay |
| N2 groups / index postings created | 3,723,588 / 7,059,791 | Cumulative creations; END gauges are after release |

Each per-actor maximum can come from a different actor or time. These values cannot be summed
to obtain a simultaneous global pending size.

The completed native normal CPU window was requested 60–90 seconds into run-1, before the
guard capture. It contains 36,667 samples, with application frames in 18,305. Inclusive groups
include abstraction in 5,650, N2 group work in 4,448, call-plan building in 3,444 and member
joins in 2,564. Snapshot cursor work appears in 1,041 samples. GC paths are also prominent.
These groups overlap; they do not measure retained bytes or removable CPU fractions.
The terminal binding includes the text-profile hash and bounded analysis. Continuous recording
export is complete in order 11 below; the completed MAT ownership result appears below.

These measurements show large pending delivery work on ThingsBoard, unlike OWASP. They justify
investigating avoidance of repeated joins and duplicate transport before compacting collections.
The dump must still establish which owners cause OOM. The counters alone cannot do so.

## Stirling-PDF run-4 timeout

The latest iteration is **run-4 / backward / limit 4**, which ended with TIMEOUT. Prescan took
18.878 seconds, static metadata 7.269930756 seconds and catalog setup 0.116767446 seconds.
The following engine times exclude their barriers. Run-4 did not complete and run-3 remains
the last accepted forward report. Strict findings are reviewed separately from completion.

| Iteration | Engine, s | Barrier, s | Result |
| --- | --- | --- | --- |
| run-1 / forward / limit 1 | 30.914013567 | 0.210517458 | COMPLETE; forward report accepted |
| run-2 / backward / limit 2 | 27.856765038 | 0.183504616 | COMPLETE |
| run-3 / forward / limit 3 | 93.690476012 | 17.022116870 | COMPLETE; forward report accepted |
| run-4 / backward / limit 4 | 883.796243008 | Not reached | TIMEOUT |

The normal profile request follows the first engine start by 60 seconds. Here that falls near
the beginning of run-3, after run-1 ended; it must not be labelled a run-1 CPU window. The
normal text capture has 19,169 native CPU samples, including 11,291 with application frames.
The completed near-limit text capture has 10,442 native samples, with application frames in
2,801. Its inclusive paths include `ApOps.limit` in 1,387, `MethodEdgeStore.add` in 1,274 and
`FlowGroup.subtractSubsumed` in 868 samples. GC paths are prominent. Paths overlap and sample
counts do not measure removable work or retained bytes. This is later-run evidence, not a run-1
bottleneck. Both profiler returns are zero. Recording export in order 12 confirms the normal
window is wholly in run-3 and the near-limit window is wholly in run-4.

No OOM capture was triggered. Command time was 1,111.093 seconds and sampled peak RSS was
9.553 GiB. The terminal binding is
`report-preparation/partial-optimized-Stirling-PDF-20261015T021634.093218Z.json`. It confirms the
normal text profile is unchanged from its provisional analysis. The accepted run-3 report
contains 40 CONFIRMED and 88 DEMAND attempts; refinement did not finish.

The strict comparator retains 14 of main's 17 unique identities, removes three and adds 42.
Optimized has 56 unique identities in 128 occurrences. The missing sites are the
`ProcessExecutor` constructor at instruction index 8 / line 183, `ConvertEmlToPDF` return 103
and `PipelineController` return 50. Two same-method XSS last-return candidates are review hints;
they do not remove the strict losses. No retained sink-span change or invalid-location gap is
reported. All 128 export attempts produce SARIF results.
The 72 extra occurrences are exact whole-result duplicates within the optimized report;
they do not establish equal internal witnesses or extra unique sites.

This does not pass finding acceptance. The timeout and accepted run-3 limit do not identify
the cause at each missing site. The corrected control has the same three losses; the
source/record/seed diagnostic below identifies a concrete receiver-path cut. Comparator output is in
`report-preparation/Stirling-PDF-main-optimized-review-20261015T021741.367576Z/strict/Stirling-PDF.json`.
Its source-bound review is final. The separate diagnostic below records the cause evidence and its limits.

The loss-cause audit now identifies both XSS differences as exit-location projection. The engine
evaluates the shared synthetic exit sink. With a Simple trace and no relevant thread-flow path,
the location resolver falls back to the final return: index 126 for `ConvertEmlToPDF` and 109
for `PipelineController`. Main's full traces select returns 103 and 50. The method-level sinks
are present, but equality of taint paths/support is unproved and both strict location losses remain.
The command-constructor loss is an ordinary call site and has no such mapping explanation.
The original frontier audit omits actual source-hit, seed and record decisions. The diagnostic
below adds them and identifies a specified source-rewriter cut; it does not claim a globally
first failure across every possible route. The original audit is
`report-preparation/Stirling-PDF-main-optimized-review-20261015T021741.367576Z/loss-cause-source-review`.

### Command-loss diagnostic: current evidence

An isolated snapshot-only replay uses base `85a7c0e`, with the same PDF 8g heap, 1080-second
analysis budget and 1200-second outer limit. Its focused gate passes 56 tests with no failures,
errors or skips, plus `:classes`. Import-only compile failures from earlier diagnostic packets
are preserved. No diagnostic patch changes the measured root runtime or supplies a new timing
comparison. The executed packet is
`report-preparation/command-loss-diagnostic-design/stage-a-v5-import-fix-20261015T051636.860281Z`;
its patch SHA-256 is `bf93f9538163c7ad9c5adb9f8080947f133078fb53617170ee5049db16e0df36`.

The replay ended with TIMEOUT in **run-4 / backward / limit 4**, after completing and accepting
run-3. Its sequence exits 0 at 05:36:42 UTC; the analyzer timeout is preserved separately. Its actual run-1 report contains two public command-constructor target entries as DEMAND;
its complete run-3 report census contains none. Thus run-4 timeout is not the erasure event.
All 11 observed B2 source hits equal the prepared and consumed F3 source-seed tuples, with full
method, context, statement and forward-edge identity. All 218 observed prepared/consumed demand
tuples also match. None of those target source hits carries the command mark.

The getter's NORMAL backward FLOW summary survives in the actual native record index; selection
keeps only its zero REACH summary. That is the intended selection rule, not lost persistence.
Normal pieces still enter current-run publication regardless of that selection. Source review
also admits the observed getter tuple under its consumed B2 demand. The selection exclusion
therefore does not itself explain the loss. The snapshot does not observe whether the current
publication or persisted record matches/resumes the caller, nor name the
supplier rejected for the unconfirmed NORMAL constructor witnesses. The remaining upstream
cause is open in Stage A alone. Stage B below observes those existing decisions.

The independent correlation is
`report-preparation/command-handoff-source-review/stage-a-v5-correlation-20261015T0528`.
The final ledger closes all 37 lanes with zero dropped/error/cutoff/unwritten rows and no I/O
failure. All four exact selectors are present. The 13 isolated source hashes and full rule-tree
digests match after execution. Final comparison retains all 56 primary identities and 128
occurrences, with zero location or multiplicity changes. Its canonical complete SARIF result
multiset equals the primary report; whole-file metadata equality is not claimed. The final
review is in the replay job's `final-review` directory. Stage B supplies the named matching,
support and stage observations below.

The second diagnostic's focused gate passes **72 tests in 11 classes**, with zero failures,
errors or skips, plus `:classes`. It adds actual support questions and supplier results,
current-summary and native-record matches, call/cleaner stages and source admission observations.
The source selectors include entry sources whose input is REACH and which produce no output.
Intentional wrong-shape samples have a separate bounded ledger. Peer review checks original
evaluation order and the 27-file source freeze; the root analyzer remains unchanged.
Its packet is `report-preparation/command-loss-diagnostic-design/stage-b-v3-20261015T061323.565044Z`,
with patch SHA-256 `ed1f2ed16cadb3fc0a0573b39684db01afbef9f2b0a36f2d82bec1f1136a5750`.
The same fixed PDF replay ends at TIMEOUT in **run-4 / backward / limit 4**, after completing
and accepting run-3. Its sequence ends at 06:38:16 UTC. All 27 source hashes and the full
ignored-inclusive primary/isolated rule digests match after execution. Strict comparison
preserves all 56 primary identities and 128 occurrences, with no location or multiplicity
changes and no input differences. The canonical full SARIF result multiset is exactly equal;
whole-file run metadata and all internal witnesses are not asserted equal. Its
`final-review/parity.json` SHA-256 is
`0c9c2759c9d3aa394575d3d2bd4bc053788446666019ddeb9e4e51db84c4774d`.

The snapshot ledger closes all 37 lanes with no cuts, drops or errors. The flow ledger closes
36 lanes: 15 complete and 21 capped. It has no I/O or selector errors, unwritten rows, active
scopes or active example entries. Source, cleaner, ordinary-stage and general END lanes are
among the capped selections. Positive scoped events remain usable; missing rows in these
lanes never prove no matching or no output. These limits remain part of the diagnosis.
The coverage receipt is `final-review/coverage.json`, SHA-256
`4bafb692c7b30aa644abc73c9d0784b048fafcad0dc48a07bfa9c415ff8b4a12`.
Its quota/row cut counter includes output-budget cuts, not only truncated AP fields.

The complete run-1 target support lanes record 17 eligible NORMAL suppliers for the exact
constructor key. Its only matching `$ARGS` supplier is the one-argument overload, with caller
support false; the final question records 17 visits, one shape match and zero supported matches.
At that overload's selected URL call-61 obligation, the complete shape-match lane records no
true match for its singleton confirmable member among 79 eligible candidates. This last result
uses the completed closure and the unchanged all/any loop; its capped END counter remains
unavailable. Full bound-rule/proof identity is not inferred from a public target ID.

Both run-2 getter channels are complete: 620 current-summary rows and 544 record-replay rows.
The exact getter-to-URL call-0 census contains one zero REACH and 49 concrete TAINT current
matches, all TAINT noncommand; the 48 matched reversed views produce static outputs. FLOW
is not interpreted as exclusion. Run-3 replay has 430 dropped rows and cannot supply a complete
replay-absence claim. All eleven run-2 source-hit tuples equal prepared and consumed run-3
source seeds, and all 223 selected explicit demand tuples match across that handoff. The
snapshot lanes proving those observations are complete. No command entry-source hit occurs
in that selected source-hit census.

Its completed run-1/run-2/run-3 observations now identify a concrete backward-path cut. At URL
call instruction 58, `%44 = command.add(%43)`, an incoming NORMAL root EXACT `$ARGS` fact has
the zero premise. The backward source stage emits NORMAL root/Element `$UNTRUSTED` facts on
the appended argument; that argument comes from `tempOutputFile.toString()`. A separate
receiver branch then passes thirteen cleaner steps and produces zero outputs at the fourteenth.
The same replay's earlier cached stage metadata identifies reverse step ordinal 13 as
`Clean(This, [], EXACT, $ARGS)`. The recorded step therefore removes the previous receiver's
root `$ARGS` fact instead of carrying it toward the earlier `command.add(URL)` at instruction
55. This names a real source-rewriter/cleaner gate, not a missing handoff copy or an inference
from absent getter records.

`JIRCallPlanBuilder.rewriterSteps` creates the unconditional selected-mark cleanup for each
relevant mark and action position of a user source whose condition is not statically false.
`CallPlan.Actions.reversed` reverses its step order; `CleanStep.Clean.reversed` keeps the same
cleaner. The AP primitive correctly removes the identical exact root. This implements the
[summary-rewriter contract](../../spec/interpreter.md): the source rule overrides the real
callee flow for its relevant marks. The compiler function body and the call/cleaner kernels
are unchanged between corrected control and optimized. The full builder file has catalog
changes and is not byte-identical. Both measured reports have the command loss.

The observed cut is therefore specified rule-guided overwrite/inversion on the later append,
not an AP-cleaning error. Preserving an earlier accumulated receiver label across that append
needs a separate rule/specification design and recall oracle. This report does not authorize
changing the overwrite contract or relaxing the failed main-comparison gate. The source and
runtime proof is the replay job's `independent-getter-route-review/NAMED-GATE.md` and
`named-call58-rewriter-gate.json`; the latter SHA-256 is
`d1f200a4fd7c7342a7a2d11b5062814142c9eb7fead2526d634e26ccd637d695`.

The later source-admission result is also explicit: the command `$UNTRUSTED` entry source at
URL instruction 110 is allowed in run-1, then denied in run-3 with `seededSources=true`, for
both EXACT and ANY_TAINT forward edges. The missing seed is therefore enforced by the normal
gate; those observations alone do not prove that the seed policy is wrong. The actual run-1
constructor question has one matching NORMAL supplier but an unsupported caller, so it remains
DEMAND. These positive events can be used despite unrelated capped lanes. Capped upstream
support rows cannot establish a complete support DAG or a globally first failing operation.

## Corrected-control results

### OWASP

Corrected control is terminal with `OK / STOP_RULE`, reached/completed/accepted **run-3 / forward /
limit 3**. It retains all 4,112 main identities. Control and optimized have identical strict
identity, span and occurrence counts: 4,112 unique and 9,179 occurrences, with no losses,
additions, span changes or multiplicity changes. This does not prove internal witness equality.
The pair reviews are `report-preparation/OWASP-main-control-review-20261015T022503.721143Z` and
`report-preparation/OWASP-control-optimized-review-20261015T022505.284590Z`.

| Iteration | Control engine, s | Optimized engine, s |
| --- | --- | --- |
| run-1 / forward / limit 1 | 247.763707508 | 349.963792074 |
| run-2 / backward / limit 2 | 62.199289557 | 79.896573392 |
| run-3 / forward / limit 3 | 49.693519017 | 59.881283548 |

Observed run-1 time increased by about 41%. Control command time was 476.968 seconds and sampled
peak RSS 8.718 GiB, versus optimized 736.687 seconds and 9.360 GiB. Both miss the run-1 target.
The run-1 forward frontier counts are equal, as are the aggregate final form build counts:
884,662 successful builds, 448,688 call plans and 2,986,565 compiled predicates on each side.
These final counters span all iterations and cannot establish first-build/rebuild or per-phase
cost. They do not support attributing the timing increase to more compiled forms.

Resource samples show substantial and different host contention. Approximate run-1 windows
have minimum available RAM of 5,204,320,256 bytes for optimized and 484,458,496 bytes for control;
sampled analyzer major-fault deltas are 427 and 47,419. Maximum host load is 22.28 and 11.03.
The observed timing difference is not an isolated algorithm-cost measurement. The windows use
second-precision manifest clocks and are expanded by one second. The receipt is
`report-preparation/OWASP-run1-resource-comparison-partial-20261015T022340.489380Z.json`.
Control terminal controls and unchanged normal-profile hash are bound in
`report-preparation/partial-control-OWASP-20261015T022710.763939Z.json`.

### Conductor

Corrected control is terminal with OOM in **run-2 / backward / limit 2**. Its last completed
engine and accepted forward report are **run-1 / forward / limit 1**, matching optimized's
progress. Control run-1 meets the 60-second engine target; optimized run-1 does not.

| Iteration | Control engine, s | Optimized engine, s |
| --- | --- | --- |
| run-1 / forward / limit 1 | 46.419625930 | 69.931044488 |
| run-2 / backward / limit 2; OOM, includes guard | 265.311363728 | 247.247770343 |

Control's run-1 barrier took 1.289172346 seconds. Its guard capture ran from 03:07:25.344 to
03:07:43.101 UTC, taking 17.757 seconds within run-2 and writing 11,554,313,669 file bytes.
Capture progress is run-2, distinct from the retained run-1 report. File stat agrees with the
closed markers; the actual run-2 ownership comparison is below. Control command time
was 425.192 seconds and sampled peak RSS 9.660 GiB, versus optimized 519.703 seconds and
9.631 GiB. The terminal source, positive iterations, counters, actual Java 17 launch with
8g/1080-second settings, and profile metadata are bound in
`report-preparation/partial-control-conductor-20261015T031055.018123Z.json`.
The normal profiler completed with exit 0; its body was not read. Control JFR export is complete;
its compact windows are available through the combined recording index below.

Control retains all seven main sink identities and their spans, adding 293 candidates. Control
and optimized have identical strict exported identity, span and occurrence counts: 300 unique,
300 occurrences, no losses, additions, span changes or multiplicity changes. Each reports three
CONFIRMED and 298 DEMAND entries, with 301 SARIF generation attempts but 300 exports because of
the synthetic NATS location mapping gap. Both comparisons remain
`INCOMPLETE_OR_INVALID_INPUT_COMPARISON`; these results do not prove internal witness equality,
complete-analysis recall or resolved false positives. Pair evidence is in
`report-preparation/conductor-main-control-review-20261015T030837.925638Z` and
`report-preparation/conductor-control-optimized-review-20261015T030838.339684Z`.

Optimized run-1 is about 50.65% longer in this instrumented pair. Resource conditions differ:
approximate run-1 windows have minimum available RAM of 16,176,885,760 bytes for optimized and
18,086,203,392 bytes for control; sampled analyzer major-fault deltas are 103 and 27, and maximum
host load is 23.82 and 12.66. These one-second samples use second-precision manifest clocks and
one-second expanded bounds. They do not isolate a causal algorithm-cost fraction. The receipt is
`report-preparation/conductor-run1-resource-comparison-partial-20261015T031130.348732Z.json`.

### ThingsBoard

Corrected control is terminal with OOM in **run-1 / forward / limit 1**, before any completed
engine or accepted forward report, matching optimized's progress. Both export valid empty
SARIF: all 14 main identities are missing, with no additions. The control-to-optimized strict
comparison is empty on both sides, not a no-FN result. Both pair reviews remain
`INCOMPLETE_OR_INVALID_INPUT_COMPARISON`; incomplete F1 explains the empty accepted report but
does not establish a propagation defect or recovery at each missing site. The pair evidence is
`report-preparation/thingsboard-main-control-review-20261015T031739.077184Z` and
`report-preparation/thingsboard-control-optimized-review-20261015T031739.321058Z`.

| Run-1 measurement | Control | Optimized |
| --- | --- | --- |
| Engine, s; OOM, includes guard | 209.292801731 | 164.488070844 |
| Guard capture, s | 55.651 | 33.225 |
| Command wall, s | 542.365 | 594.476 |
| Sampled peak RSS, GiB | 13.727 | 13.723 |

Control's guard ran from 03:15:57.891 to 03:16:53.542 UTC and wrote 17,630,867,827 file bytes.
Capture progress is run-1; no forward report was accepted. File stat matches the closed markers;
the measured owners are compared below. Neither side completed F1, so their
engine durations do not measure completion of the same workload or establish a speedup.
The terminal source, controls, counters, actual Java 17 launch with 12g/1080-second settings,
and profile metadata are bound in
`report-preparation/partial-control-thingsboard-20261015T031816.342245Z.json`.
The normal profiler completed with exit 0; its body was not read. Control JFR export is complete;
its compact windows are available through the combined recording index below.

Approximate run-1 resource windows have minimum available host RAM of 29,141,262,336 bytes for
optimized and 14,154,809,344 bytes for control; sampled analyzer major-fault deltas are 813 and
872, and maximum host load is 18.83 and 25.68. As above, one-second samples and expanded
second-precision bounds do not isolate algorithm costs or retained-memory differences. The receipt
is `report-preparation/thingsboard-run1-resource-comparison-partial-20261015T031816.365395Z.json`.

### Stirling-PDF

Corrected control is terminal with TIMEOUT in **run-4 / backward / limit 4**. Its last completed
engine and accepted forward report are **run-3 / forward / limit 3**, matching optimized's
progress. Both completed run-1 within the 60-second engine target.

| Iteration | Control engine, s | Optimized engine, s |
| --- | --- | --- |
| run-1 / forward / limit 1 | 56.633855615 | 30.914013567 |
| run-2 / backward / limit 2 | 40.930198715 | 27.856765038 |
| run-3 / forward / limit 3 | 95.778064695 | 93.690476012 |
| run-4 / backward / limit 4; TIMEOUT | 832.728022026 | 883.796243008 |

Control barriers took 0.500419911, 0.203463068 and 15.016338463 seconds for runs 1, 2 and 3.
Run-4 did not complete or reach a barrier. Control command time was 1,146.798 seconds and
sampled peak RSS 9.696 GiB, versus optimized 1,111.093 seconds and 9.553 GiB. No guard capture
was logged. The terminal source, controls, counters, actual Java 17 launch with 8g/1080-second
settings, and profile metadata are bound in
`report-preparation/partial-control-Stirling-PDF-20261015T033627.274047Z.json`.
Both native profiler commands returned zero; their bodies were not read. Control JFR export is
complete; its engine-window attribution is included in the compact review below.

Control and optimized both retain 14 of main's 17 strict identities, miss three, and add 42.
The missing command site in `ProcessExecutor` at instruction 8 and the XSS return sites at
`ConvertEmlToPDF` instruction 103 and `PipelineController` instruction 50 remain explicit losses.
Control-to-optimized has identical strict identity, span and occurrence multisets: 56 unique
identities and 128 occurrences, with no changes. Each final accepted F3 report has 40 CONFIRMED and
88 DEMAND entries, and all 128 SARIF generation attempts export. Both pair reviews remain
`INCOMPLETE_OR_INVALID_INPUT_COMPARISON`. Matching outputs do not establish matching internal
witnesses or resolve the missing-site causes; the terminal metadata alone does not diagnose the command loss. The separate diagnostic above
identifies a specified receiver-path cut. The reviews are
`report-preparation/Stirling-PDF-main-control-review-20261015T033625.676624Z` and
`report-preparation/Stirling-PDF-control-optimized-review-20261015T033625.959185Z`.

Approximate run-1 resource windows have minimum available host RAM of 38,214,750,208 bytes for
optimized and 20,080,537,600 bytes for control; sampled analyzer major-fault deltas are 24 and
45, and maximum host load is 11.15 and 23.67. These sampled conditions differ, so the shorter
optimized run-1 is not an isolated causal algorithm-cost measurement. The receipt is
`report-preparation/Stirling-PDF-run1-resource-comparison-partial-20261015T033627.288791Z.json`.

All four corrected controls now have terminal summaries. The combined matrix terminal marker
is recorded at 03:36:02.436439 UTC; it proves that the twelve runs ended, not that their analysis
or finding gates passed. Each project retained the 20 GiB scheduling floor. The nine recording
exports completed at 02:47:09 UTC were extended with the last three completed at 03:49:25 UTC.
The combined recording index below binds all twelve. Both optimized heaps and corrected-control
Conductor and ThingsBoard have measured ownership below. All four guard-heap analyses are complete.

## ThingsBoard run-1 heap ownership

The first actual MAT analysis is complete for the optimized **run-1 / forward / limit 1**
guard dump. There is no completed engine or accepted report. The parser exits 0 at 04:18:48 UTC;
all 26 requested queries return CSV data and report no query errors. The complete histogram
contains 315,471,332 reachable objects and **11,374,504,144 bytes**. Raw objects minus 29,589
unreachable objects match that count. The unbounded root-dominator partition has 991 rows and
its retained bytes equal the complete heap total.

| Selected owner set | Default MAT retained union, bytes | Scope |
| --- | --- | --- |
| Actors, runners and subscription managers | 5,405,248,360 | Combined core ownership |
| All `AddedFactStore` objects | 1,523,417,616 | Actor raw links and unresolved-call observation stores |
| N2 subscription/publication owners | 1,617,261,144 | Cumulative caller-side state |
| Method entries, forms and caches | 704,069,848 | Includes strong alias/graph owners, not just compiled forms |
| Spring provider wrapper | 1,327,132,728 | Includes its base configuration's bound-rule caches |
| IR storage | 1,633,904,848 | Measured context; storage changes remain out of scope |
| Queues and buffered channels | 1,238,184 | Queued envelopes and channel owners |
| Canonical AP manager | 8,681,216 | Manager-owned union, not all AP values retained elsewhere |

These selected unions overlap and must not be added. Default MAT reachability can include
reference referents; it does not prove strong-only payload paths or equal reclaimable savings.
The separate disjoint root partition assigns 3,937,146,488 bytes to `UnitRunner` roots and
1,247,034,776 bytes to `SubscriptionManager` roots. It supports the conclusion that persistent
core state is large. It does not isolate every nested field's ownership.

The 562 physical event queues contain 2,676 entries and 190,558 capacity cells. They are small
in this capture. Actor-local abstraction delivery remains a different pending collection:
73,669 actors hold 284,816 policies and 42,690,639 logical mark memberships. Their waiting
counters sum to 3,344,521 members and 6,856,102 integer cells. The 2,106 active phases report
7,473,951 members, 16,824,371 integer cells and 84,601 outgoing target builders. These are
simultaneous scalar field totals, not retained-byte totals or distinct marks. They do not
establish dominance over persistent storage.

The complete accumulator census closes: 147,338 raw/observation stores and 87,496 N2 directories
own 234,834 accumulators. Their 3,798,591 and 3,723,573 groups total 7,522,164 entries. N2 has
7,059,775 current index postings. The two domains keep distinct owner progress and indexes;
their counts do not prove duplicate payload bytes or permit deleting one store.

Catalog ownership is small: the shared catalog's default retained size is 4,923,696 bytes.
There are 48,000 physical profiles for 51,953 methods, with 39 mark-directory roots and nine
cleaner roots. Logical transitive memberships are not flattened physical copies. The 2,106
open COW views contain 980 prior group roots; their per-view retained-size sum is 502,904 bytes,
not a complete COW-root union. Neither observed catalog nor COW storage is the primary owner.

All immutable request-mark arrays together use 183,527,160 shallow bytes across 300,109 arrays.
Only 11 compiled `ForwardReversePayload` objects remain, while the forms/entry union also
contains alias states, conversion outputs and 926,687 soft-reference wrappers. Thus its 704 MB
cannot be called retained compiled-form payload memory. The provider wrapper's large bound-rule
cache likewise needs a binding/lifetime design, rather than assuming Spring itself generates
the entire footprint.

The complete heap has 623 canonical `InitialAp` objects, 6,836,723 `FlowTree` objects and 814,602
`TaintTree` objects. A global initial count does not measure admitted memberships per actor or
repeated mark-neutral propagation. The supplementary actor census below measures memberships;
it does not classify the concrete-mark cohorts or measure repeated transfers.

Evidence is in `heap-review/optimized/thingsboard/job-20261015T040356.874180Z-8fd2f16922`,
including its terminal receipt, complete query-status table, class census and owner exports.
The corrected-control ThingsBoard result is compared next; both Conductor results follow.

### Supplementary ThingsBoard census

The supplement reads the same optimized **run-1 / forward / limit 1** guard capture. No engine
or report has completed. MAT reuses its existing parsed indexes and exits 0 at 05:47:12 UTC.
Of 34 requested queries, 32 export CSV data. Two return no result: exact occupied statement
cells and the both-kind conclusion cohort. The global-interner query exports a row but its
count fields are blank. Those results remain unavailable; no zero is inferred from them.

All 73,669 actor rows are present. Their initial stores contain 532,543 memberships. This is
an actor-membership count, unlike the 623 globally interned initial objects. It does not say
how many admitted initials differ only by concrete mark or share a transfer block. The median
is one membership, nearest-rank p95 is 53, and maximum is 437; 39,179 actors have one. These
counts include abstract and zero initials, so they do not establish concrete-mark repetition.
Method stores have 459,672 non-REACH premise arrays and 9,426,014 allocated statement cells. The exact
physical array histogram closes at 459,672 arrays and 45,913,664 shallow bytes. Occupied cells
are unavailable, so neither sparse utilization nor a byte saving from sparse statement storage
is established. Queued work counts are separate from occupied statement cells.

The available conclusion cohorts are:

| `ConclusionGroup` cohort | Objects | Meaning |
| --- | --- | --- |
| No FLOW or TAINT base entries | 146,237 | Can still carry REACH |
| FLOW only, exactly one base | 3,964,847 | Singleton base map; its tree list need not be singleton |
| TAINT only, exactly one base | 425,257 | Singleton base map; its tree list need not be singleton |
| One kind, multiple bases | 243,085 | Wider base maps remain necessary |
| Both kind maps nonempty | Unavailable | Original no-result query is preserved |

The four successful cohort counts sum to the complete 4,779,426-object class histogram. This
crosscheck does not change the failed query's status. The empty-map counts also account for
28,026 TAINT-only and 215,059 FLOW-only multi-base buckets, which sum to the wider cohort.

The nested representation has measurable empty storage. Empty FLOW map shells occupy
28,776,960 shallow bytes, and empty TAINT map shells occupy 207,654,864. Unused REACH arrays
occupy 111,176,136 shallow bytes. FLOW/TAINT outer layer arrays occupy 107,311,080 and
11,940,336 shallow bytes. Empty layer-list retained unions are 15,424,752 and 192,392,736 bytes
for FLOW NORMAL/DEMAND, and 14,446,080 and 9,004,896 for TAINT NORMAL/DEMAND. These list unions
include backing-array objects; their object totals are not list counts. For example, the FLOW
DEMAND union contains 4,008,182 lists and the same number of backing arrays. The selections have
different owner scopes and can overlap broader retained unions. Owner growth, backing storage,
sharing and promotion costs must be measured before predicting net savings.

The new owner splits narrow the algorithmic targets:

| Selected owner set | Default MAT retained union, bytes | Limit |
| --- | --- | --- |
| Caller cumulative-input directories alone | 1,247,295,120 | Eligible new-protocol fraction and shared payloads remain unknown |
| Necessary summary storage and publication indexes | 369,966,024 | Must remain under a callee-owned input join |
| Normal call/entry/exit sink cache maps | 1,092,011,616 | Selected/discarded binding density remains unknown |
| AllRelevant call/entry/exit sink cache maps | 144 | This capture only |
| Normal source/cleaner cache maps | 65,067,128 | Separate family; preserve its eligibility |
| AllRelevant source/cleaner cache maps | 42,577,144 | Preserve broader return-rewriter behavior |

These unions overlap other owners and do not measure reclaimable savings. The eight cache
store rows contain 365,353 normal and 79,610 allRelevant cached-method entries across families.
Those are method-map counts, not bound-rule counts or selected-rule density. The results support
avoiding duplicate input relations, retiring discarded sink bindings and lazy nested containers.
They do not support dropping necessary summaries, concrete suppliers or predicate support.

The actual job is
`report-preparation/concrete-mark-entry-source-review/measurement-design/mat-recipe-v4-20261015/results/optimized/thingsboard/job-20261015T053753.092456Z-1f6da32dcd`.
The compact review is `heap-review/supplement-execution-20261015/final-review`. It preserves all
34 statuses and per-field completeness. No new parse or query retry supplies the missing fields.
All eight review-file hashes match the completion receipt at
`heap-review/supplement-execution-20261015/completion.json`, SHA-256
`09c78b2da5944591440a3a40db6616dd638602ad53701a0dd846743bccbcff7b`.

### Corrected-control ThingsBoard heap

The corrected-control capture is also **run-1 / forward / limit 1**, with no completed engine or
accepted report. MAT exits 0 at 04:51:57 UTC. All 26 queries return data, with no query errors
and unchanged capture bytes. Its complete histogram contains 301,530,851 reachable objects and
**11,389,516,736 bytes**. Raw objects minus 30,798 unreachable objects close that count, and
the 1,002-row disjoint root partition equals the complete heap total.

| Selected owner set | Control default retained union, bytes | Optimized, bytes |
| --- | --- | --- |
| Actors/runners/subscriptions | 5,718,279,696 | 5,405,248,360 |
| Standing N1 request stores | 2,431,949,392 | Absent; abstraction policies replace them |
| All added-fact stores | 973,316,024 | 1,523,417,616 |
| N2 subscription/publication owners | 1,005,209,952 | 1,617,261,144 |
| Method entries/forms/caches | 89,182,560 | 704,069,848 |
| Queues/channels | 3,324,480 | 1,238,184 |

These unions overlap. The N1 union is the old store's footprint, not a measured net saving.
Control has 2,412,168 standing rows: 138,934 mark rows and 2,273,234 position rows. Its 29,826,754
logical memberships comprise 27,553,520 mark memberships and 2,273,234 position memberships;
every captured position row is a singleton. Its 217,538,222 primitive cells and 2,893,878 reference
cells are numeric capacity counters, not bytes. All 73,669 stores are still unreleased.

Both heaps contain 73,669 forward actors, 51,953 method entries, 73,669 method-form owners,
51,953 local-alias owners and 454,685 alias state objects. Nearly equal alias-object cohorts
can move between default retained selectors through alternate reference paths. The difference
between the forms unions therefore does not prove 615 MB of new alias allocation, a cache
regression or reclaimable savings. Neither heap contains many compiled form payloads.

Control has 2,372,944 raw/observation groups and 2,348,549 N2 groups, totaling 4,721,493 entries;
optimized has 7,522,164. N2 postings are 4,340,386 versus 7,059,775. Control handles 10,951,521
messages versus optimized's 7,745,679. These different directions show why equal actor counts
and elapsed times do not establish matched semantic progress. The new core removes standing
N1 storage, but this pair does not demonstrate a net peak-memory reduction or completion.

Control's 562 physical queues contain 2,200 entries and 702,989 capacity cells, versus 2,482
counted reservations. Queue entries, capacities and reservations have different units.
The source-bound archive and exact query statuses are in
`control-heap-review/control/thingsboard/job-20261015T043631.716919Z-95b9cec8fc`.

## Conductor run-2 heap ownership

The optimized Conductor dump is **run-2 / backward / limit 2**. Run-1 remains the last completed
engine and accepted report. MAT exits 0 at 04:27:04 UTC with unchanged capture bytes. Its complete
reachable histogram has 215,875,689 objects and **7,633,136,808 bytes**; the disjoint root
partition matches that total. Twenty queries return CSV data. Six run-1-only selections are
empty, with no logged query errors; the complete histogram independently records zero
`Run1Abstraction` and phase instances. Empty field results are not fabricated scalar zeros.

| Selected owner set | Default MAT retained union, bytes |
| --- | --- |
| Actors, runners and subscription managers | 2,807,011,752 |
| All added-fact stores | 549,844,664 |
| N2 subscription/publication owners | 950,228,720 |
| Method entries, forms and caches | 208,752,488 |
| Proofs, witnesses, records and demand | 148,409,960 |
| Provider wrapper | 413,246,240 |
| IR storage; changes out of scope | 2,248,197,744 |
| Queues and buffered channels | 824,464 |

These unions overlap and are not exclusive savings or strong-only referent measurements.
The 219 physical event queues contain two entries and 176,569 capacity cells. Only four compiled
form payloads remain. Current heap state includes 418,638 `FlowTree` and 3,581,011 `TaintTree`
objects; this is a backward-run capture and must not be used as a run-1 propagation census.
There is no live run-1 abstraction policy owner to explain this OOM. Persistent core state is
again larger than general event-queue storage.

The source-bound archive, per-query statuses and class counts are in
`heap-review/optimized/conductor/job-20261015T041855.191950Z-6ff7db151b`. Corrected-control
ownership is compared below. All four actual guard heaps have been reviewed.

### Corrected-control Conductor heap

The corrected-control capture is also **run-2 / backward / limit 2**, with run-1 last completed
and accepted. Its MAT process exits 0 at 04:36:30 UTC with unchanged capture bytes. The complete
histogram contains 215,857,087 reachable objects and 7,638,692,392 bytes; the 958-row disjoint
root partition matches. Nineteen queries return data, two CSV selections and five field
selections are empty, and no query errors are logged. Known-empty run-1 receivers are verified
by the complete histogram; empty fields retain their original status.

| Selected owner set | Control default retained union, bytes | Optimized, bytes |
| --- | --- | --- |
| Actors/runners/subscriptions | 2,854,225,600 | 2,807,011,752 |
| All added-fact stores | 561,034,936 | 549,844,664 |
| N2 owners | 973,081,664 | 950,228,720 |
| Method entries/forms/caches | 208,239,088 | 208,752,488 |
| Proof/record/demand owners | 107,690,184 | 148,409,960 |
| Provider wrapper | 409,370,280 | 413,246,240 |
| IR storage; out of optimization scope | 2,249,101,616 | 2,248,197,744 |
| Queues/channels | 823,776 | 824,464 |

These overlapping selections describe two incomplete OOM frontiers, not equivalent completed
work or a demonstrated saving. All 17,465 control actors are backward. Raw and N2 directories
each hold 1,065,902 groups, versus 1,048,521 in optimized. Control N2 has 8,382,370 postings and
33,591 zero-only entries. Both heaps have two physical queue entries. Global integer-lane
arrays remain present in control because added-fact indexes use that class too; their
46,179,000 shallow bytes are not standing run-1 request storage. Evidence is
`control-heap-review/control/conductor/job-20261015T042706.359982Z-6ab9fe61b0`.

The combined four-heap review is
`report-preparation/mat-four-heap-review-20261015T045738.573907Z`. Its verification resums
whole histograms, root partitions, queues and accumulator cohorts. All 104 original query
statuses are preserved: 91 data results, two empty CSV selections and 11 empty field selections.
Known-empty receivers are checked independently. Its `packet.json` SHA-256 is
`cd69adbe1e75a55f8524b6bcdc6ae1a73db11b5de45410ac8668535295de4852`.
No empty field selection is turned into an invented scalar zero.

## Memory pressure before the guard

These OOM statuses come from the analyzer's 90% post-GC high-memory guard. They do not require
a JVM `OutOfMemoryError`. Before it stops the engine, `MemoryManager` asks the registered
managed-reference owners to clear their references and requests further GC. The live dump is
taken before engine cancellation releases its stores.

All four guarded scans show repeated recoveries below the threshold before the final failure.
Across its pre-guard log, optimized ThingsBoard has 17 high-memory notifications and 12
recoveries. The first reported recovery is 6,151,597,688 bytes; the last is 11,591,076,744 bytes,
near its 11,596,411,699-byte threshold. Optimized Conductor has 15 notifications and 11
recoveries; its last recovery is 7,729,745,568 bytes against a 7,730,941,132-byte threshold.
Control has 14/8 and 15/11 notifications/recoveries for ThingsBoard and Conductor respectively.
These are whole pre-guard log counts, not per-iteration cache-rebuild counts.

The pressure and rising post-GC occupancy are observed. Their byte changes do not identify
exclusive cache savings or retained owners. In particular, a default MAT union that reaches
`Reference.referent` is not proof of strong payload retention. The source-bound controls are
in `report-preparation/guard-pressure-source-review-20261015T0412.json`. The existing
`SoftReferenceManager` implementation is unchanged. The measured default unions do not prove
which payload bytes become reclaimable after a cache change.

## Run-1 workload against corrected control

The completed compact review extracts the same `BIDI_REQUEST_BATCH_STATISTICS` family at
`run=1, direction=FORWARD, limit=1, phase=END`. These are cumulative analysis counters at
that engine end, not normal-window counts or differences between snapshots. Both ThingsBoard
engines are incomplete. Member scans count FLOW selection-cell inspections or TAINT leaf-ID
membership probes, including empty hash cells where applicable. Climbs count cursor actions
before coverage and outgoing-set deduplication. Transported mark memberships are a third unit;
the columns cannot yield an exact duplicate-message rate or a distinct-request count.

| Project | Runtime | Member scans | Climbs | Outgoing batches | Outgoing mark memberships |
| --- | --- | --- | --- | --- | --- |
| conductor | control | 10,961,758 | 6,992,236 | 100,174 | 4,023,992 |
| conductor | optimized | 13,726,852 | 12,052,834 | 253,883 | 6,680,822 |
| thingsboard | control | 245,867,746 | 186,578,593 | 1,039,988 | 103,326,643 |
| thingsboard | optimized | 325,282,471 | 319,832,979 | 2,409,238 | 163,334,653 |
| Stirling-PDF | control | 1,781,400 | 1,161,209 | 14,748 | 936,781 |
| Stirling-PDF | optimized | 1,355,345 | 1,250,291 | 15,918 | 1,019,506 |

Removing retained N1 request stores did not reduce every join or transport count. Conductor
and ThingsBoard have more member scans, climbs and outgoing mark memberships in optimized.
PDF has fewer member scans but more climbs and transported memberships. Thus a smaller request
representation alone does not establish less work. Future changes should measure concrete
initials and repeated supplier joins, then avoid work before it creates these collections.
The heap results identify large persistent fact and call-input collections at the guard;
the supplementary census above separates several representation and owner costs.

## Exported recording evidence

All twelve recording exports are complete, with selected event counts matching each recording
summary and unchanged recording-file stats. The first nine remain in
`profile-analysis/exports/partial-terminal-nine-20261015T022700Z`; corrected-control orders
06–08 are in `profile-analysis/exports/partial-control-678-job-20261015T030814.780555Z`,
completed at 03:49:25 UTC. Their original receipts remain explicitly partial.

The combined index, `report-preparation/all12-recording-export-union-20261015T0351.json`,
binds all twelve child manifests and 191 windows to the current all-matrix source/launch
checks, unchanged recording stats and post-run input receipt. Its SHA-256 is
`27a587b52c7a7a3d51d4c10a6fd554c11fe94911b462a4877af5255c12020768`.
This proves completed export for all cases; per-engine recording coverage, stack truncation
and finding acceptance retain their separate limits.

The first-nine compact review is `report-preparation/jfr-partial-analysis`. The completed
last-three review is `report-preparation/jfr-control-three-analysis-20261015T034957.643846Z`;
its 1,603 scalar, native-control and scope checks pass. The original partial-nine files remain
unchanged. Java samples below exclude
native CPU. Allocation weights are sampled estimates, not exact allocation totals or retained
bytes. Windows and inclusive frame paths overlap and must not be added.

| Runtime / project | Window | Java samples | Allocation weight, GiB | Top-level GC pause, s |
| --- | --- | --- | --- | --- |
| Control / OWASP, reached run-3 | run-1 engine | 7,940 | 289.762 | 9.591 |
| Optimized / OWASP, reached run-3 | run-1 engine | 11,727 | 300.513 | 10.328 |
| Optimized / Conductor, reached run-2 | run-1 engine | 4,928 | 102.056 | 5.523 |
| Optimized / Conductor, reached run-2 | run-2 before guard; 209.184 s | 11,779 | 358.960 | 34.526 |
| Optimized / ThingsBoard, reached run-1 | run-1 before guard; 131.192 s | 32,908 | 449.532 | 41.627 |
| Optimized / ThingsBoard, reached run-1 | normal 30 s within run-1 | 8,770 | 111.701 | 5.484 |
| Control / conductor, reached run-2 | run-1 engine | 5,520 | 92.851 | 2.786 |
| Control / conductor, reached run-2 | run-2 before guard; 247.516 s | 16,295 | 361.227 | 28.938 |
| Control / thingsboard, reached run-1 | run-1 before guard; 153.542 s | 23,742 | 353.958 | 43.395 |
| Control / thingsboard, reached run-1 | normal 30 s within run-1 | 4,589 | 66.383 | 5.513 |
| Control / Stirling-PDF, reached run-4 | run-1 engine | 5,100 | 103.129 | 3.038 |

OWASP whole-run-1 inclusive CPU includes call-plan building in 3,548 samples and cleaner-chain
execution in about 26.3% of its Java samples. Its requested normal window is more build-heavy:
1,107 of 1,270 Java samples include call-plan building, 370 include alias computation and 156
include signed normalization. Metadata setup has about 59.5 GiB of sampled allocation weight;
normalization appears under about 23.7 GiB of that weight and predicate compilation under
22.3 GiB. These inclusive values overlap. They support avoiding repeated preparation,
without identifying the required versus redundant fold count or first-build/rebuild count.
The bounded allocation readout also includes 19.818 decimal GB under the `Results` constructor
and 32.658 GB under `FlowClean.run`. These overlap other AP/cleaner/callback work and do not
isolate the removable selected-FLOW branch. They support a separately designed single-result
specialization, with no retained-memory or measured speedup claim. Its source-bound audit is
`report-preparation/selected-flow-cleaner-fastpath-review`.

Conductor's requested normal interval spans approximately 9.729 seconds of run-1, its 1.975-second
barrier and 18.295 seconds of run-2. It is not a pure run-1 profile. In run-2 before the guard,
call flow, subscription and record replay are substantial overlapping paths. The last 30 seconds
before the guard contain 18.077 seconds of top-level pause. The last 30 seconds of the engine
instead lie inside its dump guard and contain only one Java sample; use the pre-guard workload
window when choosing an optimization.

ThingsBoard's normal interval is wholly in run-1. It includes request-frontier work in 2,528
samples, FLOW cursor work in 1,331, call-plan building in 1,722 and alias computation in 1,181.
Whole pre-guard run-1 includes building in 10,760 and alias computation in 7,126 of 32,908 Java
samples. The last 30 seconds before its guard contain 20.723 seconds of top-level pause.
The retained-owner result is separate below. The full engine reaches the recording's coarse final-second
uncertainty; normal and pre-guard windows pass the conservative recording-bound checks.
Original stacks can be truncated at 64 frames; requesting print depth 256 cannot restore them.
No omitted top-50 frame is treated as zero work.

Stirling-PDF's normal recording window is run-3: 5,727 Java samples, about 97.1 GiB sampled
allocation weight and 2.381 seconds of top-level pause. Its near-limit window is run-4:
1,373 samples, about 25.2 GiB and 0.550 seconds of pause. Near-limit inclusive call-flow work
appears in 1,190 samples, group insertion in 582 and trie subtraction in 308. Their allocations
also overlap. This supports group/trie/stage work in the late run; it does not establish a
deadlock, a stage-count pattern or a run-1 optimization saving. The run-1 window includes call-plan
building in 2,777 of 5,109 samples and alias computation in 2,434, with overlap.

The completed control profiles change some window comparisons. Conductor control's normal
30-second window is wholly run-2: 3,752 Java samples, 87.329 GiB allocation weight and 2.500
seconds of top-level pause. Optimized's normal window crosses run-1, its barrier and run-2,
so these are not matching phase profiles. Control's last 30 seconds before its run-2 guard
contain 12.091 seconds of pause, versus optimized's 18.077 seconds.

ThingsBoard has a pure run-1 normal window on both cores. Control has 4,589 Java samples,
66.383 GiB allocation weight and 5.513 seconds of pause. Its inclusive call-plan and alias
sample counts are 1,848 and 1,583; its old request-frontier phase appears in 802 samples.
Optimized's abstraction phase appears in 2,528 of 8,770 samples. The different algorithms,
progress and host load prevent treating these shares as a direct cost ratio. The last 30 seconds
before the guards contain 20.532 seconds of pause in control and 20.723 seconds in optimized.
Both whole-engine windows reach the coarse final-second recording uncertainty; the normal
and pre-guard windows pass both recorded-bound and selected-envelope checks.

Stirling-PDF control's normal window is run-2, whereas optimized's is run-3. Both near-limit
windows are run-4. Control's near-limit window has 1,156 Java samples, 22.786 GiB allocation
weight and 0.512 seconds of pause. Inclusive group insertion, trie subtraction and cleaner-chain
sample counts are 528, 269 and 135, versus optimized's 582, 308 and 184. Whole-run-4 allocation
weights are about 1,657.6 decimal GB in control and 1,467.7 decimal GB in optimized; neither is
live heap. The F3 barriers take 15.016 and 17.022 seconds and include support, report, persist,
handoff and frontier work. They are not transfer-only timers.

Final form-build counts differ between control and optimized on these three projects. The
counters span all reached runs, including incomplete work; they cannot explain a run-1 elapsed
change or distinguish first builds from rebuilds. The selected statistics implementations are
byte-identical across the two revisions. The compact review preserves those source hashes,
per-window truncation flags and unlisted-frame status.

## Fresh main baseline results

The following rows describe only the new main phase. Main uses the legacy engine, so its
run-N fields are not applicable. They do not establish an optimized result or a performance change.

| Project | Status | Command wall, s | Prescan, s | Full-scan phase, s | Sampled peak RSS, GiB |
| --- | --- | --- | --- | --- | --- |
| OWASP | OK; SARIF written | 167.395 | 48.514 | 52.215 | 6.878 |
| Conductor | OK; SARIF written | 242.851 | 70.224 | 68.231 | 9.350 |
| ThingsBoard | OK; SARIF written | 336.720 | 237.368 | 47.559 | 13.526 |
| Stirling-PDF | OK; SARIF written | 71.236 | 20.751 | 8.444 | 7.154 |

The main full-scan phase includes confirmation and trace generation. OWASP's trace generation
is 7.219 seconds within that phase; these times must not be added. Its requested normal CPU
window did not start because the phase ended before 60 seconds. Its continuous recording has
now been exported with event-count and unchanged-file checks; the log-anchor plus-60 window
contains post-analysis output rather than engine work. All four main recording exports are
complete in `profile-analysis/exports/partial-terminal-nine-20261015T022700Z/order-01` through
`order-04`. The new timing evidence is
`report-preparation/partial-main-OWASP-20261015T011550.924837Z.json` in the batch below.
Strict comparisons are available above for all four optimized and corrected-control bundles.
Stirling-PDF's command route has the source-backed rewriter cut described above; its two strict
XSS location differences have the exit-fallback explanation. All three strict losses remain.
Conductor's evidence is `report-preparation/partial-main-conductor-20261015T011957.615736Z.json`.
Its normal CPU capture was requested after 60 seconds. The profiler log says `Process exited`,
so the native window is partial. The exported requested JFR interval spans analysis and output;
it does not establish a full 30-second native analysis capture.
Other analyzers launched after the batch started. OWASP's sampled available host RAM fell to
15.0 GiB. Resource samples preserve available RAM, swap, load and analyzer major faults; these
observations do not isolate the time cost of contention. Compare them before attributing a
timing difference to the optimization.
ThingsBoard completed prescan within its budget. Its evidence is
`report-preparation/partial-main-thingsboard-20261015T012615.666636Z.json`.
Stirling-PDF's evidence is
`report-preparation/partial-main-Stirling-PDF-20261015T012651.211266Z.json`.
`main-progress.json` records all four projects as terminal. This is main-phase completion only.

## Fixed-workload evidence

The fresh batch is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-abstraction-demands-v2/20261015T004517.956676Z
```

Project models, heaps, timeouts, Java properties and direct Gradle runner settings are frozen.
All workload phases share the existing three locks. The scan scheduling floor remains 20 GiB
available RAM and 45 GiB output disk. The batch waited for available RAM. The gate cleared at
01:11:30 UTC and the main build started. The launcher checked resources again before each
later configured project.

Previous raw captures were removed with exact inventory and hash receipts. Logs, SARIF, compact
profile exports and source reviews were kept. The fresh full input audit passes all 85 checks,
including 42 content datasets and the committed repaired control. Its manifest is
`input-audit/result-20261015T005219.094954Z/equality-manifest.json` in the fresh batch.
`checks/validation.json` binds passing checks to the repaired revision. The runtime binding is
BOUND. The launcher runs main, optimized and corrected control phases in sequence,
with a resource check before each configured project.
The full post-matrix audit also passes all 85 checks. All 42 content-dataset digests equal the
pre-run values, with the same check names and corrected-control revision. It ran from
03:36:22.486 to 03:38:12.740 UTC, after all three phases were terminal. The separate receipt is
`input-audit/result-20261015T033622.485997Z/equality-manifest.json`, SHA-256
`e6535822ca8380336e76ede9c89450c8daa37214a17fdcb8742ad7677eeaf608`.
The BOUND runtime receipt is unchanged. These two endpoint audits do not prove continuous
input monitoring between them.

After Stage A closed and its final result comparison passed, its three unused raw diagnostic
profiles were also removed: 208,643,495 bytes, with an exact inventory and removal receipt at
`cleanup-binding/stage-a-diagnostic-profiles-20261015T0600/receipt.json`. Its snapshots, logs,
SARIF, source bindings and final review remain. The twelve primary workload recording sets and
four current guard heaps are separate and remain available for this measurement review.
Stage B's three unused raw diagnostic profiles were removed after its final source/output
checks: 240,983,246 bytes, recorded in
`cleanup-binding/stage-b-diagnostic-profiles-20261015T0643/receipt.json`. Its closed ledgers,
runtime observations, comparisons, source hashes, logs and SARIF remain available.

All project scans, strict comparisons, iteration timing extraction, recording exports and
compact profile reviews are complete. All four guard-heap ownership results and the
supplementary census are measured above. The command-loss diagnostic and its final parity
review are complete; capped lanes and the scope of its named local gate remain explicit.
The current heap-analysis recipe is prepared for optimized `85a7` captures. Its source/launch
checks and 15 small synthetic controls pass. A reviewed check requires the launch receipt to
precede the selected capture run and use the correct output directory. The first MAT parse
started at 04:03:56 UTC on the optimized
ThingsBoard run-1 guard dump and completed successfully at 04:18:48 UTC. All 26 queries return
data; the ownership findings above retain their specific limits. The
parser uses a 20g tool heap and requires 22 GiB available RAM at launch. This is a tool scheduling floor;
the fixed analyzer heaps and timeouts remain unchanged.
