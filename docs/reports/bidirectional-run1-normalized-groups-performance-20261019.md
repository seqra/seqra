# Run-1 normalized groups and direct statics: performance report

The fresh eight-run study is complete. Exact source/input equality passed.
Strict finding comparison, eight JFR analyses, seven available native profiles
and the ThingsBoard heap owner/actor/provider checks are closed. Four counting
diagnostics pass. Runtime acceptance fails; no project meets the 60-second
run-1 engine target. The [next-iteration proposal](../design/bidirectional-run1-normalized-groups-next-iteration-20261019.md)
ranks work avoidance from these measurements.

Date: 2026-10-19. Authoritative candidate:
`b88c85d5b710d28293d7672af92b085a3838fb19`.
The implementation binding and selected test gate are closed. The activated
plan and before endpoint bind this candidate and main reference
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The after endpoint and equality are closed and passed.
The contract is `spec/grouped-run1-statics.md`, revision 2.1.

The target is a complete **run-1 engine within 60 seconds**. Prescan,
ownership setup, barriers and whole invocation time have separate clocks.
Always report the latest reached, completed and accepted forward run.
A timeout/OOM prefix is not a completed run or a measured speedup.

## Selected behavior and the B88 correction

The grouped core uses GROUP conditions in run-1 and the original FULL
conditions in later runs. It retains ordinary AP transfers, source action
and occurrence IDs, source tails and anchors, and complete ordinary/ND
premises. Run-1 group/static-dependent results are DEMAND. Static order and
caller context have the selected conservative approximation. Later FULL
runs use actual CFG/call transfer and strong writes. Field limits are
`1, 1, 2, 3, ...`; run-0 is not an analysis iteration.

GroundingSupport and the extra exact-field writer validator remain absent.
Selected-mark cleaners request a group and cannot clean actual group taint
in run-1. Their actual group result stays DEMAND. RemoveAllMarks and real
structural kills keep their admitted behavior. The new core treats every
sink `trackFactsReachAnalysisEnd` list as empty in every run. Legacy main
retains its policy. Do not filter strict differences caused by that policy.

B88 corrects two missing JVM `StaticOperation.Write` classifications:

| Path | Before B88 | B88 |
| --- | --- | --- |
| Local field/array write with a static alias target | The ordinary alias value edge existed. Its reached static output could be dropped at the run-1 operation seam. | The lowerer attaches Write metadata to that existing value edge. The ordinary reached effect can publish its immediate DEMAND summary. |
| Persisted static alias in a call's ALIASES stage | The ordinary alias/model value edge existed. It lacked Write metadata. | The stage attaches Write metadata to the existing transfer, including its actual stage identity. |

This is an implementation conformance correction. It changes previously
lost behavior. It is not a new alias transfer, static ordering policy or
field abstraction. It may expose additional valid work; it is not itself
a demonstrated performance improvement. Keep, strongKeep, bindings and
cleaner complements are not new write roots. No generic static-destination
fallback is selected.

## Closed implementation validation

The selected four-task gate passes **2,063 tests**, with zero failures,
errors or skips:

| Population | Tests |
| --- | ---: |
| Common dataflow module, unfiltered | 1,591 |
| JVM dataflow module, unfiltered | 426 |
| Selected sequence and signed-rule application classes | 15 |
| Selected rule-tag/join class | 31 |

This is not an unfiltered application-suite result. The committed binding
covers 3,197 current core/rule source files. It records a clean candidate
and equality between the tested files and the committed implementation.
This gate does not prove project finding parity or the 60-second target.

Evidence: `implementation-commit-binding.json`,
`validation/full-gate/receipt.json`, and its archived XML/log/source map.
The source-only static emitter audit is
`preparation/static-emitter-completeness-audit.json`; it found no third
missing production path in its stated scope. That audit is not a new
whole-JVM proof or a replacement for the pipeline regressions.

## Fresh study controls

The fixed plan is one invocation per side for each project. Use the direct
Gradle `runProjectAnalyzer` harness and Java 17. Do not add alternate heaps,
timeouts, retries or filtered findings. Bind the actual main/candidate
sources, patches, rules, project models, extensions and toolchain inputs.

| Control | Fresh evidence |
| --- | --- |
| Activated plan and exact source/runtime binding | PASS; `validation/benchmark-guard/plan.json` |
| Main reference and approved diagnostic patch | PASS; f945 with the recorded MemoryManager capture hook only |
| Serial Java 17 prebuilds | PASS; `execution/prebuild-main` and `execution/prebuild-candidate` |
| Before endpoint and complete input membership | PASS; 10 datasets, 177,499 regular-file memberships, 16 controls |
| All eight terminal/process/output closures | PASS; all8 closed |
| After endpoint and exact before/after equality | PASS;10datasets/177,499filememberships/16controls |
| Strict unchanged comparator, all four projects | CLOSED exit0; all incomplete runtime comparisons |
| Profile-reader handoff | CLOSED; one eight-leaf sweep, zero failures; all seven native profiles read; profile locks released before the test/MAT follow-ups |

Final study control receipts bind these measurements. Do not
promote the old Dac equality receipt or source counts to this fresh study.

## Fresh fixed outcomes

All eight leaves and endpoint equality are closed. Main has no numbered BIDI iteration.
An engine COMPLETE marker and an accepted forward frontier are separate.

| Project | Fixed heap / IFDS timeout / outer limit | Main status / invocation seconds | Candidate status / invocation seconds | Run-1 engine seconds / target | Latest reached / completed / accepted |
| --- | --- | --- | --- | --- | --- |
| OWASP | 8 GiB / 1,000 s / 1,200 s | OK / 120.205 | TIMEOUT / 1,021.420 | 906.998 / FAIL | run-1 / none / none |
| Conductor | 8 GiB / 1,080 s / 1,200 s | OK / 93.199 | TIMEOUT / 1,119.679 | 986.046 / FAIL | run-1 / none / none |
| ThingsBoard | 12 GiB / 1,080 s / 1,200 s | OK / 204.362 | OOM / 674.028 | 239.084 / FAIL | run-1 / none / none |
| Stirling-PDF | 8 GiB / 1,080 s / 1,200 s | OK / 48.124 | TIMEOUT / 1,114.699 | 925.594 COMPLETE / FAIL | run-2 / run-1 / run-1 |

| Project / side | Prescan seconds | Ownership setup seconds | Engine/full-scan seconds | Barrier seconds | Total IFDS seconds | Invocation seconds | Peak sampled RSS bytes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OWASP/main | 33.245 | N/A | 45.408 (full scan) | N/A | 78.659 | 120.205 | 8,420,384,768 |
| OWASP/candidate | 32.803 | 60.178 | run-1 906.998 TIMEOUT | none | 1000.042 | 1,021.420 | 8,964,849,664 |
| Conductor/main | 24.732 | N/A | 24.913 (full scan) | N/A | 49.648 | 93.199 | 10,079,625,216 |
| Conductor/candidate | 24.695 | 69.185 | run-1 986.046 TIMEOUT | none | 1080.047 | 1,119.679 | 10,392,883,200 |
| ThingsBoard/main | 134.726 | N/A | 30.709 (full scan) | N/A | 165.439 | 204.362 | 14,490,615,808 |
| ThingsBoard/candidate | 146.026 | 253.969 | run-1 239.084 OOM | none | 639.457 | 674.028 | 15,052,759,040 |
| Stirling-PDF/main | 14.423 | N/A | 5.551 (full scan) | N/A | 19.975 | 48.124 | 9,686,265,856 |
| Stirling-PDF/candidate | 17.030 | 34.433 | run-1 925.594 COMPLETE; run-2 99.683 TIMEOUT | run-1 3.203 | 1080.075 | 1,114.699 | 10,612,338,688 |

Use the harness `wall_seconds` for invocation time. Keep
`command_wall_seconds` separately. Parse each phase from its own markers.
Do not obtain prescan by subtracting other phases. Setup reduces the
available engine budget but does not count against the 60-second engine
target. Keep TIMEOUT, OOM and outer HARD_TIMEOUT distinct. RSS includes
native/JVM overhead; it is not retained Java heap.

## Fresh finding comparison

The unchanged strict comparator is closed. Its input differences are empty.
Source/location review of the two PDF misses and candidate additions is
closed. The first three candidates have no accepted forward report.

| Project | Main identities | Candidate identities | Shared | Missing | Added | Candidate occurrences | Assessment / latest run |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OWASP | 4,112 | 0 | 0 | 4,112 | 0 | 0 | INCOMPLETE; run-1 / none / none |
| Conductor | 7 | 0 | 0 | 7 | 0 | 0 | INCOMPLETE; run-1 / none / none |
| ThingsBoard | 14 | 0 | 0 | 14 | 0 | 0 | INCOMPLETE; run-1 / none / none |
| Stirling-PDF | 17 | 1,656 | 15 | 2 | 1,641 | 61,895 | INCOMPLETE; run-2 / run-1 / run-1 |

Retain the unchanged comparator's rule, sink fingerprint and full method
signature identity. Disclose fallback identity warnings. Retain occurrence
and raw-location details. Do not replace identity counts with trace counts.
Do not merge changed return sites automatically or hide end-policy changes.

A first-engine timeout can export an empty initially uncommitted report.
A later failure can retain a prior accepted forward report. Decide from
actual source/control/frontier evidence for each leaf. Do not infer report
acceptance from a finally barrier log or from backward completion. Missing
exports in an unfinished analysis do not by themselves prove semantic
false negatives. Keep them in the fixed-budget comparison nonetheless.

There is no all-project no-FN result. Of the 4,135 missing strict identities,
4,133 belong to runs that ended before an accepted forward report. The two
PDF misses are return-location differences: ConvertEmlToPDF return 103,
lines 133–134, versus candidate return 126, lines 148–149; and
PipelineController return 50, lines 90–91, versus candidate return 109,
line 130. Each candidate location has 74 occurrences with the same rule and
full method. The Simple synthetic-exit exporter falls back to the last
return. Keep the strict misses: source/path equivalence is not proved.

LicenseKeyChecker `Files.exists` instruction 14 at line 98 is now present
with the exact main fingerprint, full method and raw location. It was
absent in the old Dac result. This supports the alias metadata correction;
it does not establish replay of every main trace.

PDF's 1,641 added identities have 61,875 occurrences. Another five repeats
occur on four shared identities. XSS accounts for 824 added identities and
61,051 occurrences; redirect accounts for 750 and 751. All additions are
unconfirmed run-1 DEMAND candidates. They are not proved false positives.
The exporter attempted 62,420 Simple entries and retained 61,895 SARIF
results. Keep the 525 invalid-location errors as an explicit export gap.
The unchanged comparator and review did not filter end-policy differences.

Evidence: `validation/final-finding-comparison`,
`reviews/fresh-finding-review/review.json` and its exact location/addition
packets.

## Fresh work counters

The per-run counters below come from terminal control lines in each
candidate log. The closed phase ledger retains those lines verbatim.

Separate cumulative offers/builds/joins from stored row/region/member
snapshot cardinalities and maximum delays. Request calls, offered members,
new memberships and rows have different units. Temporary-store zeros after
cleanup do not prove delivery. Request delay is not end-to-end repair time.
Form-build spans across workers are not CPU seconds or one wall fraction.
Build counts alone do not prove eviction. Terminal form counters can cover
several runs; do not call them run-1 counters without per-run evidence.
Ordinary RulePredicateStore joins are not removed GroundingSupport work.
The new-initial counter covers ordinary `addInitial` admissions. Direct
static point resumes do not increment it. Input arrivals and new initials
therefore do not count the full static-origin workload.

### Closed OWASP run-1 counter prefix

These are terminal run-1 counters from the corrected candidate. They are
not CPU attribution or final cross-project acceptance. The census has
1,033 eligible full marks and 53 eligible groups.

| Counter / unit | Value |
| --- | ---: |
| Run-1 input arrivals | 100,544 |
| Newly admitted method initials | 25,052 |
| Request offer calls | 30,630,535 |
| Offered request members | 30,643,891 |
| Members rejected by committed R | 23,873 |
| Members rejected by temporary P | 30,573,022 |
| Fresh temporary memberships | 46,996 |
| Maximum first-fresh-P-to-flush-start age | 530.853 s |
| Static write regions / supplied regions | 5 / 3 |
| Static region/group memberships | 29 |
| Static read descriptors / point subscriptions | 309 / 372 |
| Conditional writer rows / FLOW writer rows | 741 / 9 |
| Static candidate visits / owner replies | 18,387 / 1,646 |
| Removed N1 added-fact stores | 0 |

About 99.847% of offered request members are duplicates. This does not
prove that requests dominate CPU, nor that each offer queues new work.
P/R admission and work scheduling already merge/coalesce them. Request age
is measured from first fresh pending admission to the start of a flush,
before synchronous commit. It is not end-to-end delivery delay.
The static counts have different units and cannot be compared as CPU cost.

Form counters record 287,882 successful builds and 57.794 aggregate worker
seconds of build spans. This is not wall time or proof of cache eviction.
`observedSupportShapes` counts ordinary predicate proof shapes; it is not
removed GroundingSupport. Closed profile attribution is below; build counts
alone do not attribute that cost.

Evidence: the OWASP candidate analyzer log, closed manifest/summary and
`validation/root-preparation-review/partial-outcomes-first-three.json`.

### Closed Conductor run-1 counter prefix

The terminal census has 1,126 eligible full marks and 45 groups. The
candidate reached run-1; it completed no engine and accepted no forward
report. These counters do not establish CPU shares or final acceptance.

| Counter / unit | Value |
| --- | ---: |
| Run-1 input arrivals / new method initials | 142,159 / 37,170 |
| Offered request members | 3,266,654 |
| Members rejected by committed R / temporary P | 312,323 / 2,892,567 |
| Fresh temporary memberships | 61,764 |
| Request commits / fresh committed members | 8,229 / 59,698 |
| Peak global temporary rows / members | 299 / 2,457 |
| Maximum first-fresh-P-to-flush-start age | 854.471 s |
| Static write regions / supplied regions | 255 / 198 |
| Static region/group memberships | 283 |
| Read descriptors / point subscriptions | 7,496 / 100,057 |
| Conditional writer rows / FLOW writer rows | 14,128 / 313 |
| Static candidate visits / owner replies | 4,638,565 / 76,487 |
| Removed N1 added-fact stores | 0 |

Small final group supply does not imply little work. The service still
performs 4.64 million candidate visits. This is a work-count observation,
not evidence that the service dominates CPU. Matching and complete
premise rules must be preserved by any proposed reduction. The request
peak is the shared sum across temporary stores, not a single method's
peak. The final temporary count is zero after cleanup.

Form counters report 229,274 successful builds and 47.397 aggregate worker
seconds of build spans. Ordinary predicate support growth is not removed
GroundingSupport. The closed profiles below have separate CPU, allocation
and blocking observations.

Evidence: Conductor's closed summary and analyzer log, and
`validation/root-preparation-review/partial-outcomes-20261019-1411.json`.

### Closed ThingsBoard run-1 counter prefix

The candidate has 1,257 eligible full marks and 51 groups. It reached
run-1 only. No engine completed and no forward report was accepted.

| Counter / unit | Value |
| --- | ---: |
| Static write regions / supplied regions | 105 / 72 |
| Static region/group memberships | 72 |
| Read descriptors / point subscriptions | 39,659 / 817,103 |
| Conditional writer rows / FLOW writer rows | 1,493,242 / 1,794 |
| Static candidate visits / owner replies | 29,038,614 / 2,544,429 |
| Input arrivals / new method initials | 354,365 / 73,138 |
| Offered request members | 752,052 |
| Fresh temporary request memberships | 21,037 |
| N2 created header groups / index postings | 337,203 / 359,534 |
| N2 multi-premise E6 bindings | 0 |
| Removed N1 added-fact stores | 0 |

The small pool has a large consumer/dependency expansion. The conditional
writer count includes per-owner semantic leaf registrations; it is not a
count of unique global static equations. Candidate visits include spatial
member candidates and read/interest checks. They are not the number of
Cartesian tuples. Closed profiles identify sampled work. The heap section
below identifies retained rule and static auxiliary owners; the proposal
separates those owners from sampled CPU cost.

The MemoryManager capture logged `heapUsed=11,627,845,984` above its
11,596,411,699-byte threshold. The capture ran from 14:14:02.073 to
14:14:30.219, a 28.146-second span inside the engine clock. The closed
HPROF file has 18,939,386,234 bytes. File size is not live heap size. This
is a guard OOM result, not proof that the JVM exhausted the heap. The
capture estimate uses the existing `maxMemory - freeMemory` helper; it is
not a measured retained-byte total. The guard also uses after-GC heap pool
readings. Actual
snapshot owner results appear below.

Form statistics report 1,033,207 successful builds and 292.905 aggregate
worker seconds of build spans. This is not 292.905 wall seconds or proof
that managed soft references caused those builds.

Evidence: ThingsBoard's closed summary/log and
`closed-controls/thingsboard/bidi-snapshot.json`.

### Stirling-PDF completed run-1, then run-2 timed out

Run-1 completed at14:32:08.228 with925.594407873 engine seconds. Its
forward frontier was accepted at14:32:11.336. The barrier took3.202911281
seconds. Run-2 then started BACKWARD at limit1 and timed out after
99.682657472 engine seconds. The accepted frontier remains run-1. This is proof that this observed run-1 terminated; it is not a
general absence-of-nontermination theorem.

The completed static counters show182 write regions,149 supplied regions,
605 region/group memberships,21,687 read descriptors,386,081 point
subscriptions,17,125 conditional writer rows,57 FLOW writer rows,
2,849,031 candidate visits and402,950 owner replies. The frontier has71,933
demand edges across7,258 method keys and62,420 DEMAND occurrences. These
are not exported unique identity counts or exact confirmed findings.

The progress log's handled-event total could stay flat while a Work
reservation yielded and resumed. Thus long flat event intervals alone do
not prove absent semantic progress. Profiles must identify actual work.

## Finite execution or a new loop

The fresh PDF run-1 **completed** in925.594407873s. That is conclusive
finiteness evidence for this invocation, not a generic whole-JVM theorem.
OWL/Conductor timed out in run-1, and ThingsBoard ended on the memory guard;
those prefixes cannot establish termination. The source audits found no new
unchanged-state static/scheduler recurrence in their stated scope.

The ten-second RunManager event count is `InFlight.handled`, not a semantic
fact/step counter. A Work reservation can yield and resume without incrementing
handled. Flat counts alone cannot prove zero semantic progress. A synchronous
Cartesian static join can also take a long time inside one reservation.
Its eager tuple product is a finite-cost risk, not a measured root cause yet.

### Repeated work before admission

The OWASP counter partition proves repeated request production: 30,643,891
offered memberships contain only 46,996 fresh temporary admissions.
The duplicate fraction is 99.847%. Existing P/R merging prevents duplicate
storage; it does not remove the producer's preceding work. These are
request memberships, not AP-operation counts or a CPU attribution.

Four separate external diagnostics pass, with 35 finite cases and no
runtime source change. In an engine-API five-stage control, GROUP executes
62 edges and 93 filters for 32 final outputs with one distinct fact. The
FULL control executes 30 edges and 35 filters for six outputs with two
distinct facts. This API shape is not attributed to the production JVM
builder. Its projected pass-rule control produces three equal outputs
before one successor admission in **both** modes. Thus pre-store repeated
execution is real; this control does not prove a new GROUP multiplier in
the measured projects. Two source action IDs remain observable despite
equal GROUP outputs.

The static source audit finds additional finite multipliers: distinct
EXACT/ANY_TAINT read replies normalize to the same STAR operation input;
different mx point keys can converge after materialization clears mx;
and M static output selectors can repeat matching of one shared premise
against r compatible inputs M*r times. These are source-state witnesses,
not measured project duplicate fractions. Fresh service replies do not
necessarily mean fresh normalized operation inputs.

The `steps` counter excludes addressed resume execution. One counted step
can also process many packed trie leaves, micro edges or stage values.
No current counter gives total AP attempts. Profiles identify costly
stacks, not their invocation count. The
[detailed investigation](bidirectional-normalized-groups-termination-investigation-20261019.md)
keeps measured counts, executable controls and source hypotheses separate.
Its source-bound evidence is in `validation/redundant-operation-repro` and
`validation/report-working/redundant-operations-*-audit.md`.

Actual native CPU samples expose large repeated mark/catalog overhead:

| Candidate window | Samples | Inclusive mark-projection/policy/exclusion union | Inclusive exclusion normalization | Selected monitor spin/entry leaves |
| --- | ---: | ---: | ---: | ---: |
| PDF normal, nominal run-1 |20,316 |94.915% |47.012% |50.901% |
| Conductor normal, nominal run-1 |21,407 |83.832% |59.546% |41.211% |
| Conductor near-limit, nominal run-1 |22,138 |83.585% |65.494% |40.722% |
| OWASP near-limit, nominal run-1 |3,031 |92.643% |78.951% |0.033% |
| ThingsBoard normal, nominal run-1 |29,897 |64.244% |51.279% |32.729% |

These are overlapping native CPU sample categories, not wall-time fractions
or removable-cost guarantees. The broad union includes policy lookup, so it
must not be called all identity conversion. Selected leaves are
`ObjectMonitor::TrySpin`, `SpinPause`, and `ObjectMonitor::enter`. In almost
all those selected samples, the stack has `MarkTable.key/isGroup/group`
callers. Native callers do not identify a monitor object's address. The closed JFR monitor records below identify MarkTable waiting
in the phase windows. The two streams have different denominators. JFR recording itself also
appears in native leaf costs. Measurements include diagnostic overhead.

The current code repeatedly walks already selected GROUP values and
normalizes typed exclusions. `withCatalog(sameCatalog)` still rebuilds the
same denotation; normalization repeatedly calls synchronized catalog methods.
This is measured work in the captured windows. It does not prove that every
call is removable. PDF's closed JFR tail also shows substantial catalog work.
PDF's near-limit native capture is nominally in run-2, so use its JFR
final-run-1 window for the tail. That closed window still has 52.124% of
Java samples under `eligibleMembers` and 34.129% leaf samples in
`FullMarkId.equals`.

Evidence: `validation/native-first-look/analysis.json`,
`monitor-callers.json`, the scheduler/static source audits in the working
evidence, and the actual run markers. No runtime change is made by this read.

## Fresh profiles, blocking, GC and memory

All eight real Java 17 exports and V3 analyses closed once, with zero failed
leaves. Each analysis passed the event hash and all seven summary/export count
checks. The selected tool has 83 passing synthetic controls. The serial
reader sweep held the three shared locks throughout and released them on
exit. It removed 180,019,823,087 bytes of validated derived event JSON. Original
JFR/native/heap/log files, complete compact analyses and receipts remain.

Candidate profiles bind B88; main profiles bind f945. The table uses each
candidate's **run-1 engine** window and each legacy main **IFDS** window.
These scopes differ. Do not use the table as a normalized speedup comparison.
Main has no numbered BIDI run. PDF's latest reached run is run-2; its completed
and accepted forward frontier is run-1. The other candidates reached run-1
without completing or accepting it.

| Project / side | Profile phase / latest reached | Header-covered window s | Java execution samples | Allocation samples / estimated GB | Monitor events / blocked-thread s | GC pause union s |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OWASP/main | IFDS / N/A | 78.659 | 7,401 | 13,565 / 110.429 | 87 / 2.751 | 1.667 |
| OWASP/candidate | run-1 / run-1 | 906.998 | 68,118 | 237,931 / 2,458.084 | 0 / 0.000 | 6.176 |
| Conductor/main | IFDS / N/A | 49.648 | 7,039 | 13,207 / 80.523 | 6,669 / 115.890 | 1.331 |
| Conductor/candidate | run-1 / run-1 | 986.046 | 8,466 | 264,526 / 741.046 | 661 / 30.020 | 3.531 |
| ThingsBoard/main | IFDS / N/A | 165.439 | 34,743 | 46,201 / 477.357 | 20,742 / 343.202 | 7.144 |
| ThingsBoard/candidate | run-1 / run-1 | 239.084 | 13,563 | 55,596 / 367.497 | 1,674 / 351.583 | 25.615 |
| Stirling-PDF/main | IFDS / N/A | 19.975 | 4,234 | 5,436 / 41.808 | 3,308 / 65.229 | 0.816 |
| Stirling-PDF/candidate | run-1 / run-2 | 925.594 | 59,466 | 239,247 / 2,292.674 | 133 / 2.911 | 5.829 |

All eight selected phase windows have full header-range coverage, with no
uncovered interval in the requested phase. This does not bound log/clock
alignment error or prove uniform sampling. Full retained ranges and all
phase windows are in `closed-eight-profile-phase-table.json`. The export
request is stack depth 256. No execution sample in these windows is marked
truncated or reaches that print cap; observed maxima are 102–137 frames.
This does not recover or exclude capture-time truncation.

### Candidate run-1 hotspots

The denominators are the run-1 execution samples and estimated allocation
weights in the table above. Inclusive categories overlap. Do not add shares.
The projection union includes runtime conversion, exclusion normalization
and group policy/member lookup; it is not all removable identity conversion.

| Project | Projection CPU % | Normalize CPU % | Eligible members CPU % | Runtime fact CPU % | Direct statics CPU % | Projection allocation % | Normalize allocation % |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 86.24 | 69.23 | 1.44 | 59.19 | 0.03 | 87.00 | 73.00 |
| Conductor | 63.24 | 12.84 | 43.80 | 12.93 | 1.58 | 85.35 | 43.00 |
| ThingsBoard | 4.77 | 2.51 | 0.42 | 2.77 | 10.54 | 26.84 | 19.50 |
| Stirling-PDF | 77.10 | 2.06 | 59.33 | 15.63 | 0.27 | 75.67 | 5.65 |

OWASP's `MarkExclusions.normalized` leaf has 40.123% of run-1 execution
samples. PDF's `FullMarkId.equals` leaf has 38.625%, and eligibleMembers
has 59.330% inclusive. Conductor also has 43.799% inclusive eligibleMembers
samples. These are different costs despite the common projection union.
ThingsBoard's profile differs: its JIRGraph construction leaf has 7.351%
of samples, the direct-static category has 10.536%, and recorded blocking
and GC activity are material. Its large retained rule/static auxiliary
owners are measured in the memory section below.

Ordinary RulePredicateStore has 0.0118–0.0269% of recorded candidate run-1
execution samples. The generic raw-FULL predicate API can expose alias proof
products, but that test is not a production JVM explanation here. Selected
`JIRRuleForms.mark` projects GROUP before the builder's plain set. Do not
claim that group conversion creates duplicate JVM forms without production
evidence. No removed GroundingSupport recurrence appears in this source.

### Exact run-1 tail windows

Each final-30 window below has full header-range coverage. The conductor
and ThingsBoard CPU streams are sparse. Monitor durations sum blocked-thread
time, so they can exceed 30 seconds when threads wait in parallel.

| Project | Java samples | Estimated allocation GB | Monitor events / blocked-thread s | Main recorded monitor class |
| --- | ---: | ---: | ---: | --- |
| OWASP | 2,547 | 71.264 | 0 / 0.000 | No recorded events |
| Conductor | 49 | 14.412 | 8 / 7.019 | MarkTable |
| ThingsBoard | 9 | 0.615 | 15 / 206.239 | MarkTable |
| Stirling-PDF | 2,095 | 76.326 | 0 / 0.000 | No recorded events |

PDF's final 30 seconds have 52.124% inclusive eligibleMembers and 34.129%
leaf FullMarkId.equals samples. Its runtimeFact category has 59.763% of
allocation weight. This gives late run-1 evidence without treating its
near-limit native run-2 capture as run-1. OWASP's final-30 normalization
category has 78.956% of execution samples and 84.092% of allocation weight.
Do not rank conductor's 49 or ThingsBoard's nine tail samples as stable
CPU estimates. Their monitor records remain separate blocking evidence.

For complete run-1, MarkTable accounts for 29.817 of conductor's 30.020
blocked-thread seconds, 327.578 of ThingsBoard's 351.583, and 2.823 of
PDF's 2.911. OWASP has no recorded run-1 monitor events. A zero event
count does not prove no contention; recording thresholds can omit waits.

### Native capture coverage

The seven available candidate native profiles are closed. Main has no
native capture; ThingsBoard has no near-limit capture. They are unavailable,
not zero work. Each existing capture was requested for 30 seconds. The
anchors and overlap below are nominal; profiler attach delay has no recorded
finite bound, and collapsed stacks have no per-sample timestamps.

| Candidate / capture | Samples | Nominal phase overlap | Projection union CPU % | Native GC CPU % |
| --- | ---: | --- | ---: | ---: |
| OWASP / normal | 3,513 | run-1, nominal 30 s | 64.105 | 7.059 |
| OWASP / near-limit | 3,031 | run-1, nominal 30 s | 92.643 | 1.221 |
| Conductor / normal | 21,407 | run-1, nominal 30 s | 83.832 | 14.397 |
| Conductor / near-limit | 22,138 | run-1, nominal 30 s | 83.585 | 15.381 |
| ThingsBoard / normal | 29,897 | run-1, nominal 30 s | 64.244 | 25.641 |
| Stirling-PDF / normal | 20,316 | run-1, nominal 30 s | 94.915 | 1.861 |
| Stirling-PDF / near-limit | 40,061 | run-2, nominal 30 s | 2.007 | 41.849 |

Native sample denominators are separate from JFR execution samples. The
whole-run JFR table, early native capture and late native capture can have
different mixtures. This is not a contradiction or a license to combine
their shares. PDF near-limit is nominally run-2 and has 41.849% native GC
samples. It is not its run-1 tail.

Keep execution samples, native CPU samples, estimated allocated bytes,
blocked-thread durations, pause unions, sampled RSS and retained heap
separate. Execution samples are not CPU seconds. Allocation weights are not
live objects or retained bytes. GC elapsed duration is not GC CPU. Nested
phase windows overlap. Attribute retained owners only from a validated
snapshot; the actual ThingsBoard owner results follow.

Evidence: `profile-analysis-real/sweep-receipt.json`, the eight pinned
`analysis.json` files, `report-working/closed-eight-profile-phase-summary.json`,
`closed-eight-profile-phase-table.md`, the detailed JSON companion, and
`profile-reader-actual-closure.json`. The profile sweep released the shared
locks before later serialized readers. No runtime change or new capture
was made for this analysis.

## ThingsBoard run-1 retained memory

The only fresh memory-guard capture is ThingsBoard, FORWARD/run-1/limit 1.
It was taken at 14:14:02.073–14:14:30.219 before the OOM engine marker.
Normal MAT parsing uses Java 21 and a 24 GiB inspection heap; this does not
change the analyzer's fixed 12 GiB budget. The original 34-query job exited
zero, with source/capture/tool checks passing and all recorded processes
closed. Its inventory has 21 complete CSVs, nine samples of at most 80
rows, two top displays and two empty unavailable selections. No query
failed. Samples are not global occupancy measurements.

The complete reachable histogram has 372,928,030 objects and
11,434,863,248 shallow bytes. HPROF bytes, heap-used helpers, sampled RSS
and physical parsed heap have different units.

| Exact selected retained-root scope | Bytes |
| --- | ---: |
| Four bound-rule cache-map families | 3,160,452,352 |
| Method actors and caller subscription managers | 2,683,215,864 |
| Run-1 static service and point owners | 2,658,761,136 |
| IR storage, context only and outside optimization scope | 1,619,028,848 |
| Shared graphs and two-view form owners | 668,360,064 |
| Conclusion/flow/taint kind bodies | 421,960,736 |
| Raw/selected summary stores | 283,378,376 |
| Current publication indexes | 188,133,160 |
| Caller input owners | 150,600,664 |
| Method-edge stores | 149,233,832 |
| Worklist/event-queue/channel owners | 44,327,544 |

These unions overlap. In particular point owners refer to their core;
do not add or subtract the actor and static rows to isolate a component.
Retained unions are not promised savings or proof of strong-only ownership.
IR remains out of the optimization scope.

### Bound-rule materialization is a large non-fact population

Q26 selects all four physical cache-field families in every
`TaintConfiguration.TaintRulesStorage`: `methodItems`,
`methodAllRelevantItems`, `methodWithoutEndFactsItems` and
`methodWithoutEndFactsAllRelevantItems`. These are relevance/end-action
policy views, not the GROUP/FULL FormsCache views.

Their retained set includes 35,070,715 ContainsMark objects (841,697,160 B),
35,500,720 condition Atom objects (568,011,520 B), 16,480,429 ArrayLists
(395,530,296 B) and 8,699,328 bound TaintMethodExitSink objects
(347,973,120 B). These are configuration/condition objects, not propagated
facts. The SpringRuleProvider top dominator retains 3,285,534,424 B through
its delegated provider/configuration chain; it does not itself declare
these cache maps.

Generated accepting MethodExit templates can match broad method sets.
Each method/template/query-view binding rebuilds condition and metadata
objects. The ownership census queries both relevance flags and all eight
roles over graph methods and call targets. Actual source/cleaner rewriting
also uses allRelevant, so that view cannot be deleted globally.
Empty `trackFactsReachAnalysisEnd` does not mean an empty exit-sink role.
The full condition trees remain important for later FULL analysis.

The source audit also finds that Spring's provider wrapper does not expose
the known-provider bidirectional factory. On that chain, end-action removal
occurs after binding. This is a work-avoidance seam; it does not explain
all 3.16 GB, whose main mass is conditions and metadata. Any fix must retain
Spring's overridden roles. Early rule discards must also preserve the
resolver's original generated-ID numbering and occurrence order.

### Exact provider cache views and strong ownership

The final four-query follow-up reads the same parsed snapshot without a new
capture. All four outputs are complete: two root-path texts and two physical
header CSVs. MAT exited zero in 3.045 seconds; the actual process chain is
closed. All 12 data-index stats are unchanged. The operational log has an
exact saved-prefix/append-only check. No retry or extra workload occurred.

C01/C02 join one configuration to its eight role storages:

| Rule role | Normal map method keys | allRelevant map method keys | No-end / no-end allRelevant keys |
| --- | ---: | ---: | ---: |
| Entry source | 58,700 | 58,700 | 0 / 0 |
| Call source | 58,700 | 58,700 | 0 / 0 |
| Exit source | 58,700 | 58,700 | 0 / 0 |
| Call sink | 58,700 | 58,700 | 0 / 0 |
| Pass-through | 58,752 | 58,700 | 0 / 0 |
| Cleaner | 58,700 | 58,700 | 0 / 0 |
| Method-exit sink | 59,277 | 59,277 | 0 / 0 |
| Method-entry sink | 58,700 | 58,700 | 0 / 0 |

These are 940,406 method/role/view map memberships, including cached empty
lists. They are not rule counts or a measurement of duplicate payload.
Only the ordinary and allRelevant views are populated. Thus the 3.16 GB
union is not evidence of four populated copies or GROUP/FULL cache duplication.

R02 excludes weak/soft referents and shows a concrete strong path:

```text
main Thread <Java Local>
  -> JIRTaintAnalyzer.taintConfiguration
  -> SpringRuleProvider
  -> delegated provider/configuration chain
```

The selected provider individually retains 3,285,534,424 B. This is live
strong ownership at the snapshot. It is not proof of a post-analysis leak,
all root paths, or net reclaimable bytes. No main heap was captured, so the
whole population cannot be claimed as a new regression relative to main.

The largest avoidable collection candidate is eager bound-rule publication
during ownership census. A known-provider census can observe the same full
mark/owner stream without inserting misses into long-lived bound caches.
Actual runtime queries still bind or rebuild exact original FULL rules.
Pushing the no-end provider view through Spring alone can populate new caches
beside the existing ordinary caches. It is not the 3.16 GB fix. Coordinate
that adapter with census/cache lifetime; preserve all Spring overrides,
runtime allRelevant behavior and original generated-ID numbering.

### Static auxiliary rows exceed current premise memberships

The complete actor follow-up returns all 64,124 physical run-1 actors and
their 64,124 point owners, plus one static service. Its mode fields are
run-1/limit 1 throughout. These are complete tables, not Q05's sample.

| Snapshot population or counter | Count |
| --- | ---: |
| Canonical InitialAp descriptors, physical global objects | 177 |
| PremiseSet objects | 0 |
| Current raw premise memberships across actors | 209,932 |
| Current point-key map memberships | 1,350,884 |
| Local output-writer keys | 1,884,882 |
| Service singleton writer rows | 1,493,242 |
| Call continuation keys | 457,724 |
| Processed worklist deltas, excludes addressed resumes | 3,166,332 |
| Queued worklist entries | 57,218 |
| Unpublished rows | 393,981 |
| Queued addressed resumes | 633 |

The descriptor count is not the number of per-method facts. Q01 has one
additional PointKey object outside the current maps; do not replace map
membership with physical-object counts. Local operation-address ReadKeys
also include write-address records and are not service read descriptors.

Since each writer premise belongs to an actor's raw premises, the service
has at least `1,493,242 - 209,932 = 1,283,310` output-specific registrations
beyond one per distinct writer premise. This is a conservative lower bound
on repeated subscription structure. It is not a count of redundant output
effects or saved bytes. It supports factoring input selection/matching by
complete premise while retaining every output and both arrival orders.
No retained PremiseSet exists, so current all-static writer registrations
are singleton. A live ND Cartesian product is not the observed heap
pattern; earlier transient ND work is not excluded.

The selected static owners retain many map nodes/arrays, Pattern/PathFact,
boxed Long and list objects. Most retained memory is in persistent auxiliary
tables and rule materialization, rather than the selected queue owners or
MethodEdgeStore. One snapshot does not prove a leak, unbounded growth or a
whole-run operation count.

The follow-up MAT tool exited zero. Its strict wrapper exited four because
the operational log index grew on reopening; all 12 data-index stats stayed
unchanged. The original failure receipt and the limited log reconciliation
remain intact. There was no reparse or reader retry. The final provider-root/cache follow-up also closed, as described below.

Evidence: `validation/heap-analysis-real/thingsboard-owner-review.json`,
the closed original 34-query terminal and outcome inventory,
`actors-20261019T154124.181819Z/actor-count-review.json`, and the source-bound
`validation/report-working/bound-rule-retention-source-analysis.md`.

## Historical diagnostic context — excluded from fresh results

The preceding `dac301f2e7e814ae4ec8a253cb9054e105998200` study exposed the
alias metadata defect. Its report, profiles and snapshot investigation
remain separate historical diagnostics. Only its OWASP profile analysis
closed before the sweep was stopped; a Conductor reader was interrupted.
Those results do not establish acceptance of either Dac or B88. None of
its times, counters, samples, findings or heap owners is copied into the
fresh tables above.

Historical links:
`/drive-testcomp/opentaint-go-rules/grouped-statics-ordinary-20261019/validation/report-working/report-draft.md`
and that study's `validation/profile-analysis-real/abort-receipt.json`.

## Result and limits

The implementation and fixed measurement study are complete. Performance
acceptance and all-project no-FN acceptance fail. OWASP/Conductor did not
finish run-1; ThingsBoard hit the memory guard in run-1. PDF finished run-1
but only its DEMAND frontier is accepted; run-2 timed out. Strict PDF export
has two missing main identities, with unresolved path equivalence, and many
additional DEMAND identities. Do not call these results exact finding parity.

The evidence supports excessive work and auxiliary materialization. It does
not prove a new infinite loop. OWASP has 99.847% duplicate offered request
memberships. Static writer matching can repeat per output. Bound-rule ASTs
and static tables are larger retained scopes than MethodEdgeStore or the
selected queues. Full-catalog scans and repeated mark/exclusion conversion
are directly sampled engine costs. No additional runtime optimization was
made during the investigation.

The next proposal ranks avoided rule materialization, shared static premise
matching, current group-member snapshots and identity conversion shortcuts.
It retains the current semantic boundary and states proof gates. Smaller
representations and source-only duplicate mechanisms have lower-confidence
or lower-impact positions. IR storage remains out of scope.

All actual workload, test, profile and MAT readers are closed. The original
34-query results, the actor follow-up's strict-wrapper log exception, and
the final four-query provider review remain separately recorded. The final
provider evidence is `validation/heap-analysis-real/root-cache-followup-preparation/`:
`terminal.json`, `query-outcomes.json`, complete C01/C02 CSVs and expanded
R01/R02 texts. The study root and earlier receipts bind the other evidence.
