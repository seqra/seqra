# Run-1 static GROUP protocol removal: performance

Date: 22 October 2026. Status: **measurements and source-bound reviews closed**.
Runtime: `30c1b84606695070a79d7ae467703267b9f931c1`.

All four run-1 engines complete. Conductor and Stirling-PDF meet the
60-second engine target. Thingsboard and OWASP do not. Every candidate
command later stops on OOM or timeout. The fresh main commands all finish.
The current end-to-end performance gate therefore fails.

The strict reported-location check also fails on two PDF return locations.
The added-result count is high. A completed run-1 report is available on
all projects, but it is not a completed full analysis. No universal no-FN
or few-FP claim is made.

The selected changes remove two repeated waits for an already-answered
static GROUP input. They keep the same scalar AP reducer, original and
caller suffix. They retain each method's raw summary and demand relation.
The measured workload still has substantial allocation, form construction
and GC. The report distinguishes those observations from a proved cause
or predicted speedup.

Read with the [implementation validation](bidirectional-static-protocol-removal-validation-20261022.md),
[selected spec](../../spec/run1-static-group-protocol-removal.md) and
[proof dossier](../../spec/run1-static-group-protocol-removal-proofs.md).

## Source and measurement boundary


The fresh main reference is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
The remote main hash was checked through HTTPS before seal and launch.
The SSH check failed authentication; it was not treated as a successful check.
The main checkout has only the established optional MemoryManager snapshot
observer patch, SHA-256
`53f0abeeb0c98a16db86b8236b91899020fca14e799ff69a6739addf644b402f`.
It has no rule or analysis-policy patch.

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022`.
`preparation/sealed-inputs.json` binds the source, rules, model YAML and
Conductor extension bytes. The final terminal receipt must show source and
input equality and bind all eight summaries. The census does not hash the
complete large model, source and dependency trees. State this limit.

All commands use Java 17 and the Gradle `runProjectAnalyzer` task directly.
Both sides keep batch size 2000 and disabled multi-release JAR selection.
The candidate enables aliases and static propagation. It enables the existing
optional form and optimization statistics. No extra project or analysis
configuration is part of this campaign.

| Project | Heap | IFDS timeout | Outer timeout | Model |
| --- | --- | --- | --- | --- |
| OWASP | 8 GiB | 1000 s | 1200 s | `owasp-ci-investigation/project/project.yaml` |
| Conductor | 8 GiB | 1080 s | 1200 s | `opentaint-test-conductor/opentaint-project/project.yaml` |
| Thingsboard | 12 GiB | 1080 s | 1200 s | `opentaint-test-thingsboard/opentaint-project/project.yaml` |
| Stirling-PDF | 8 GiB | 1080 s | 1200 s | `e2e-root/model-Stirling-PDF/project.yaml` |

The Conductor command includes its configured extension rules, passthrough
approximations and compiled dataflow approximations. The rules are identical
on main and candidate. Use each manifest for the complete absolute command.

The host is shared. Other users' work was recorded and was not stopped.
Each leaf has one-second process and host samples. The report must include
their pressure ranges. A single pair cannot isolate a patch speedup from
host pressure, warm caches, compilation or the different engines.

The prior selected runtime is `a4bf78986`. Its old results are historical
context, not new measurements of `30c1b8460`. The current campaign has a fresh
main reference but no fresh pre-patch candidate run. Do not infer a causal
per-patch speedup from a comparison to an old OOM/timeout prefix.

## Validation and proof boundary


The selected constructive Lean check passes 66 controls and audits 1,441
owned declarations with no unexpected axioms. Common dataflow passes 1,745
tests; JVM dataflow passes 438. The application gate has nine cleaner
failures and one skip; the rule gate has two exit-scope failures. Fresh
exact-base controls reproduce all eleven failures. These gates remain
failed. Reproduction does not make them PASS or prove project finding parity.

The proof preserves complete demand pairs, sink seeds and implicit static
query guidance relative to complete reference runs. It does not prove a
whole-engine termination bound or a 60-second performance bound. It also
does not require equal callback counts: the old pending CallKey can merge
equal offers before its reply. The direct path can execute an equal scalar
application again. No new permanent deduplication store is included.

The withdrawn ZERO-premise exit restriction and Spring exit relocation
remain out of scope. Current controller annotation handling is retained.
`trackFactsReachAnalysisEnd` remains intentionally ignored, as selected
before this iteration. No GroundingSupport, field-policy, IR, mark-member
census, reference-manager or weak-cache change is included.

## Command outcomes and latest iterations

Main uses the legacy analyzer. Numbered runs apply only to the candidate.
Times below are whole Gradle commands, in seconds, including startup,
prescan and export. They are not the run-1 target times.

| Project | Main command s / status | Candidate command s / status | Latest reached | Latest COMPLETE | Latest accepted forward report |
| --- | --- | --- | --- | --- | --- |
| Thingsboard | 374.788 / OK | 864.157 / OOM | run-2 | run-1 | run-1 |
| OWASP | 215.466 / OK | 1058.845 / TIMEOUT | run-5 | run-4 | run-3 |
| Conductor | 102.186 / OK | 171.309 / OOM | run-2 | run-1 | run-1 |
| Stirling-PDF | 58.121 / OK | 485.141 / OOM | run-2 | run-1 | run-1 |

The accepted report survives a later failed engine. OOM/timeout prefixes
are not complete fixed points. No candidate result is CONFIRMED in this
campaign; the exported reports contain DEMAND candidates.

| Project | Main prescan s | Main legacy full scan s | Candidate prescan s |
| --- | ---: | ---: | ---: |
| Thingsboard | 250.165 | 48.158 | 268.175 |
| OWASP | 74.834 | 66.436 | 64.675 |
| Conductor | 26.826 | 25.038 | 25.652 |
| Stirling-PDF | 18.630 | 6.061 | 23.322 |

The legacy full scan and one candidate engine have different scopes.
Do not treat their ratio as a patch speedup. There is no fresh pre-patch
candidate control in this campaign. The old Thingsboard run-1 OOM is
historical context only.

## Every engine and barrier

Times are seconds. Only run-1 engine time is tested against 60 seconds.
The other times remain necessary to explain the final resource outcome.

| Project | Run | Direction | Field limit | Engine status | Engine s | Barrier s | Complete iteration s |
| --- | --- | --- | ---: | --- | ---: | ---: | ---: |
| Thingsboard | run-1 | forward | 1 | COMPLETE | 220.578 | 26.683 | 247.262 |
| Thingsboard | run-2 | backward | 1 | OOM | 192.441 | No complete barrier | — |
| OWASP | run-1 | forward | 1 | COMPLETE | 239.868 | 6.613 | 246.482 |
| OWASP | run-2 | backward | 1 | COMPLETE | 358.574 | 1.446 | 360.022 |
| OWASP | run-3 | forward | 2 | COMPLETE | 87.997 | 2.787 | 90.784 |
| OWASP | run-4 | backward | 3 | COMPLETE | 219.890 | 0.653 | 220.543 |
| OWASP | run-5 | forward | 4 | TIMEOUT | 15.999 | No complete barrier | — |
| Conductor | run-1 | forward | 1 | COMPLETE | 18.005 | 0.748 | 18.753 |
| Conductor | run-2 | backward | 1 | OOM | 80.042 | No complete barrier | — |
| Stirling-PDF | run-1 | forward | 1 | COMPLETE | 29.530 | 1.477 | 31.007 |
| Stirling-PDF | run-2 | backward | 1 | OOM | 380.393 | No complete barrier | — |

Thingsboard's failed run-2 engine includes a 47.465-second live heap
capture. Conductor includes 17.893 seconds; PDF includes 22.883 seconds.
These intervals are recorded, not subtracted as exact solver overhead.
There is no completed failed-engine barrier.

## Run-1 work


These are operation counts, not unique facts or retained object counts.

| Counter | Thingsboard | Conductor | Stirling-PDF | OWASP |
| --- | ---: | ---: | ---: | ---: |
| Direct static GROUP call applications | 14,938,235 | 32,216 | 11,863 | 0 |
| Nonempty scalar suffix batches | 159,984 | 16,101 | 8,393 | 0 |
| Direct computed-supply leaves | 5,354,390 | 13,220 | 3,067 | 0 |
| Admitted fallback call requests | 0 | 0 | 0 | 0 |
| Fallback computed joins | 0 | 0 | 0 | 0 |

Thingsboard's direct call count is about 464 times Conductor's. Its local
static read descriptor count is 43,505 versus 7,502, and its local FLOW
writer count is 418 versus 45. The static pool has 152 write regions and
649 supplied group memberships. The forward frontier retains 6,956,266
demand edges across 73,608 method keys. This is evidence of amplification;
it is not a distinct-static-relation count or a CPU attribution.

Only 1.071% of the Thingsboard direct applications enter a nonempty suffix
batch. The remaining applications are not an exact count of static outputs.
They also include empty or filtered AP/alias results. Do not label all of
them redundant static promotions without profile or source evidence.

OWASP takes neither selected direct path. Its run-1 time therefore does not
measure a benefit from these removals. Remaining work must be investigated
separately. Do not attribute the new OWASP timeout frontier to these paths.

## Findings from the closed comparison

The strict reported-location gate fails on two PDF keys. OWASP, conductor, and ThingsBoard retain every exported main key. This result does not establish universal absence of false negatives. No added finding is classified as a false positive here.

The unchanged comparator uses the full exported rule ID, sink fingerprint, and full method signature when available. It keeps physical spans and occurrence counts separate. Trace count is not part of sink identity. The [frozen source review](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/finding-review-30c1/source-adjudication.json) contains the complete keys, source hashes, stages, and evidence links.

|Project|Main strict keys|Candidate strict keys|Shared|Missing|New|Main / candidate occurrences|
|---|---:|---:|---:|---:|---:|---:|
|OWASP|4,112|4,112|4,112|0|0|4,112 / 9,179|
|conductor|7|1,260|7|0|1,253|7 / 8,171|
|ThingsBoard|14|4,036|14|0|4,022|14 / 246,062|
|Stirling-PDF|17|1,593|15|2|1,578|17 / 60,737|

No shared strict key has a changed raw-location set. Occurrence changes remain separate from this span check.

All four main commands completed with status OK. Every candidate retained an accepted forward report, then stopped on a resource limit. None completed the analyzer.

|Project|Latest reached engine|Latest complete engine|Latest accepted forward report|
|---|---|---|---|
|OWASP|run-5 — TIMEOUT|run-4|run-3|
|conductor|run-2 — OOM|run-1|run-1|
|ThingsBoard|run-2 — OOM|run-1|run-1|
|Stirling-PDF|run-2 — OOM|run-1|run-1|

Only a complete forward run can replace the DEMAND report. A later OOM or timeout retains that report. Thus these exports describe accepted prefixes. Both missing PDF keys come from a candidate with complete and accepted run-1; they are not omissions caused by failure before the first report. All reported candidate entries are DEMAND, with zero CONFIRMED entries. Individual SARIF result properties do not contain that state.

### Two PDF return-location gaps

Both missing keys use the full exported rule ID `java.security.xss#sink#1`. Their manual classification is **EXPORT_COORDINATE_GAP**. Coverage of each original main path remains **UNPROVEN**.

- Full method: `stirling.software.SPDF.controller.api.pipeline.PipelineController#handleData(stirling.software.SPDF.model.api.HandleDataRequest)`. Main instruction **50**, `return %104`, has span **90:17–91:93**. The candidate exports instruction **109**, `return null`, at **130:13–130:24**, with 74 occurrences. The [main return](/drive-testcomp/opentaint-go-rules/e2e-root/model-Stirling-PDF/sources/app/core/src/main/java/stirling/software/SPDF/controller/api/pipeline/PipelineController.java:90) and [candidate return](/drive-testcomp/opentaint-go-rules/e2e-root/model-Stirling-PDF/sources/app/core/src/main/java/stirling/software/SPDF/controller/api/pipeline/PipelineController.java:130) are different physical locations. Source SHA-256: `a78ccc6c311c4ade3274c4e66b592b6278e60505b7cf7a67d874f1389b4fd2ca`. Main has a 41-node trace from request entry through file processing, `read(bytes)`, and `bytesToWebResponse` to the earlier return.
- Full method: `stirling.software.SPDF.controller.api.converters.ConvertEmlToPDF#convertEmlToPdf(stirling.software.common.model.api.converters.EmlToPdfRequest)`. Main instruction **103**, `return %54`, has span **133:17–134:77**. The candidate exports instruction **126**, `return %89`, at **148:13–149:84**, with 74 occurrences. The [main return](/drive-testcomp/opentaint-go-rules/e2e-root/model-Stirling-PDF/sources/app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertEmlToPDF.java:133) and [candidate return](/drive-testcomp/opentaint-go-rules/e2e-root/model-Stirling-PDF/sources/app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertEmlToPDF.java:148) are different physical locations. Source SHA-256: `e17848e840895bb7cba78774a7b004bf54d9abe39da282483d7be972e9dcdc27`. Main has a 17-node trace from request entry through `getOriginalFilename`, `buildErrorMessage`, and `ResponseEntity.body` to the earlier return.

The coordinate mechanism is deterministic. [JIRBidiAnalysis.kt:148](/drive-testcomp/opentaint-go-rules/opentaint-w3/core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt:148) exports each report entry as `Simple`, without a concrete trace. [TraceMessageBuilder.kt:1162](/drive-testcomp/opentaint-go-rules/opentaint-w3/core/src/main/kotlin/org/opentaint/jvm/sast/sarif/TraceMessageBuilder.kt:1162) selects a trace return when one is available; otherwise it selects the last return in the method. The candidate locations are those last returns. Its corresponding records have no codeFlows, and their source-sink and full-trace fingerprints equal the sink-only fingerprint. They provide no source-path certificate.

The reviewed valid result metadata contains the full logical rule ID. It does not expose the exact lowered serialized occurrence ID or complete bound condition/actions for these rows. A candidate record with the same full rule and method therefore does not prove the same lowered occurrence or main path. Neither strict key is remapped. The reported-location check fails; an internal semantic false negative is not proved. No invalid-location log line names either exact method. No actual dependency on ignored sink END actions was established.

### Trace attempts and export losses

|Project|Main attempts / exported|Candidate attempts / exported|Candidate rejected locations / export gap|
|---|---:|---:|---:|
|OWASP|4,112 / 4,112|9,179 / 9,179|0 / 0|
|conductor|7 / 7|8,292 / 8,171|121 / 121|
|ThingsBoard|14 / 14|256,203 / 246,062|10,141 / 10,141|
|Stirling-PDF|17 / 17|61,262 / 60,737|525 / 525|

All main records were exported, with zero rejected locations. Each candidate export gap equals its observed invalid-location log count. The exporter drops those records before SARIF result construction. Attempt counts must not be reported as exported results or unique sinks.

### Added families and occurrence growth

|Project / full rule family|New strict keys|Occurrences at those new keys|
|---|---:|---:|
|conductor / `java.security.xss#sink#1`|986|7,888|
|ThingsBoard / `java.security.xss#sink#1`|3,748|245,765|
|Stirling-PDF / `java.security.xss#sink#1`|809|59,941|

These are the largest XSS addition families. Other additions include PDF `java.security.unvalidated-redirect#sink#1` (725 keys / 726 occurrences), ThingsBoard SQL injection (267 / 267), and conductor SQL injection (242 / 242). The frozen project inventories preserve all rule, method, file, and instruction-class aggregates.

New keys and extra occurrences at retained keys are different measures:

|Project|Occurrences at new keys|Extra occurrences at retained keys|
|---|---:|---:|
|OWASP|0|5,067|
|conductor|8,164|0|
|ThingsBoard|246,041|7|
|Stirling-PDF|60,717|5|

Repeated exported coordinates do not prove repeated internal replay. They can hide different report keys or lowered rule occurrences. They are not a batching allowance or a reason to discard results. Candidate additions have no concrete source-to-sink traces, so these aggregates are not a false-positive classification. Existing method-exit scope issues are source-based review hypotheses; they do not establish that every XSS addition is spurious. This review changes no runtime behavior and does not add a ZERO restriction, move Spring exits, or restore ignored END actions.

OWASP strict coverage and the CI count check are separate. All 4,112 main strict keys are retained. However, the candidate has **9,179** `TraceGenerationStats` attempts, which do **not** satisfy the workflow expectation of **4,112**. The full CI workflow was not executed by this review.

The cohort records equal source and sealed inputs before and after the runs. The seal covers the model YAML, rules, extensions, and test revision; it is not a fresh full digest of every large source, class, and dependency tree. The remaining finding question is concrete main-path coverage at the two PDF returns. No runtime fix or extra analyzer run was performed.


## CPU and allocation profiles

Each command records continuous profile JFR with stack depth 256 and a
rolling 256 MiB limit. Native CPU sampling requests 30 seconds at full
analysis plus 60 seconds, and near the IFDS limit if the process is still
active. Only OWASP remains active long enough for the near-limit window.

| Native window | CPU sample weight | Actual nominal phase | Heap capture overlap |
| --- | ---: | --- | --- |
| Thingsboard candidate normal | 35,655 | run-1, about +60.121 to +90.121 s | None |
| OWASP candidate normal | 5,125 | run-1 | None |
| OWASP candidate near-limit | 13,364 | run-4 | None |
| Conductor candidate normal | 28,377 | run-2 | About 9.230 s |
| PDF candidate normal | 39,828 | Early run-2 | None |
| OWASP main normal | 10,967 | About 6.35 s overlaps the end of logged IFDS; remainder follows it | None |

Main Thingsboard, Conductor and PDF finish their full scans before the
normal trigger. Their native windows are unavailable, not zero CPU.
Conductor's window mixes solver pressure and heap capture. Collapsed stacks
have no timestamps, so its samples cannot be split exactly. Capture times
use nominal log-to-poll alignment; buffering and scheduling limit precision.

Thingsboard normal run-1 has the following inclusive ancestry. Rows
overlap. The denominator is 35,655 sample weight, not wall seconds.

| Family | Weight | Fraction |
| --- | ---: | ---: |
| Recognizable native GC ancestry | 14,216 | 39.871% |
| Form builders | 6,788 | 19.038% |
| Summary subscription/query | 9,895 | 27.752% |
| All AP frames | 9,859 | 27.651% |
| Summary application | 5,053 | 14.172% |
| AP trie kernel | 4,446 | 12.469% |
| Static promotion/common retention | 3,175 | 8.905% |
| Static call dispatch | 2,723 | 7.637% |
| Cleaners | 2,098 | 5.884% |
| Runtime GROUP projection | 1,637 | 4.591% |
| Shared static input helper | 868 | 2.434% |
| Direct static composition family | 230 | 0.645% |
| Computed static supplies | 219 | 0.614% |
| Static owner replies | 8 | 0.022% |

The narrow direct-call/composition family union is 2.586%; the broader
static-dispatch/composition union is 7.668%. These are observed ancestry
unions, not removable-work bounds. Large direct-call counts alone do not
prove that a static AP bypass can halve run-1 time. The full native review
contains disjoint stack-signature partitions and intersections. For example,
projection and AP walk overlap in 1,474 samples. Do not add their fractions.

OWASP's normal run-1 window has form-builder ancestry of 52.234% and GC
ancestry of 39.317%, also overlapping. Its zero selected static calls make
it an important case for investigating remaining common work. PDF's early
run-2 window has subscription/query ancestry of 30.403%, trie kernel
26.060% and GC ancestry 38.576%. Conductor's snapshot-mixed GC share is
88.829%; OWASP run-4 near-limit is 78.450%. Neither describes run-1.

Form builders are real work, but the profiles do not distinguish first
construction from rebuilding after a soft-reference clear. `withForm`
also encloses consumer execution. A cache access, a builder sample or a
cleared reference is not a measured form-rebuild count.

### Run-1 JFR allocation

Java execution samples and allocation weights have different denominators.
GB in this section means decimal 10^9 bytes. Allocation weights estimate
allocated bytes; they are not live or retained heap.

| Candidate | Java execution samples | Allocation weight GB | Coverage |
| --- | ---: | ---: | --- |
| Thingsboard | 37,534 | 942.636 | Recording envelope covers run-1 |
| OWASP | 10,418 | 375.719 | Recording envelope covers run-1 |
| Conductor | 4,710 | 80.786 | Recording envelope covers run-1 |
| PDF | Unavailable | Unavailable | Run-1 lost from rolling recording |

PDF's retained recording starts at 15:12:07 UTC; run-1 ended at
15:11:06.789. The raw summary's zero run-1 samples therefore mean lost
coverage. Its normal native window is early run-2 and mostly precedes the
retained JFR. Do not use either to claim zero run-1 work.

| Inclusive allocation frame | Thingsboard GB / fraction | OWASP GB / fraction | Conductor GB / fraction |
| --- | --- | --- | --- |
| `walkNodes` | 156.238 / 16.57% | 102.711 / 27.34% | 16.841 / 20.85% |
| `runtimeFact` | 104.276 / 11.06% | 110.334 / 29.37% | 16.436 / 20.34% |
| `mapCanonicalLeaves` | 99.257 / 10.53% | 105.929 / 28.19% | 15.547 / 19.25% |
| `IdentityHashMap.init` | 91.679 / 9.73% | 63.205 / 16.82% | 11.132 / 13.78% |

These parent/child rows overlap. `walkNodes` also serves other operations.
The same constructor bytes can appear under both `IdentityHashMap.<init>`
and `init`; do not count them twice. The top-25 reader gives no exact
allocation intersections. An absent frame is unavailable, not zero.

Source inspection explains one avoidable-work candidate: GROUP TAINT
`runtimeFact` always walks the DAG, even when it returns the same root.
The walk allocates a visited map, stack and frames. Identity reuse avoids
rebuilding the value but does not avoid this scratch work. Immediate
producer/collector boundaries can normalize the same owned result again.
Skipping a first normalization for arbitrary GROUP input remains unsafe:
foreign/FULL leaves and receiver ownership still require validation.

Collections starting during run-1 report pause sums of 45.118 seconds on
Thingsboard, 5.238 on OWASP and 1.499 on Conductor. PDF is unavailable.
These are aggregate collection-start scopes, not reconstructed exact pause
intervals. Thingsboard includes ten `G1Full` events with `System.gc()` as
the cause, reporting 19.906 seconds of pauses. That cause does not identify
the caller or prove form-cache eviction caused the work.

Whole retained-record pause sums are 135.909 seconds for Thingsboard,
28.221 for OWASP, 34.868 for Conductor and 99.109 for PDF. Those include
other phases and diagnostics. They are not run-1 pauses. Native GC CPU
weight is not a pause fraction and is not converted to wall seconds.

### Later-run allocation

Thingsboard run-2 has 555.002 GB sampled allocation weight, Conductor
386.777 GB, and the retained PDF run-2 portion 1,912.490 GB. Their
`subtract` ancestry is 19.56%, 29.91% and 38.66%. The nested
`TaintGroup.subtractSubsumed` shares overlap those values. This is work in
stored-group admission, not a run-1 static-protocol count. OWASP run-2 has
595.685 GB and visible cleaner-chain/application ancestry.

These observations explain why removing requests cannot alone establish
a fast whole analyzer. The remaining allocation and retained-owner causes
must be separated. The next proposal does not silently change FULL ND,
field processing, source/exit scope or IR storage to address them.

## Heap ownership

All three fresh OOM captures belong to **run-2**, not run-1. Their latest
COMPLETE and accepted report iteration is **run-1**. MAT uses the source
bound to the measured runtime, with a private workspace and one capture
at a time. A successful parser exit does not make every query valid.
The OOM status comes from the MemoryManager guard. It does not establish
that the JVM threw an allocation exception.

The complete Thingsboard histogram contains 11,404,727,392 reachable
bytes and 374,676,386 objects. Conductor has 7,610,834,104 reachable bytes
and 230,364,905 objects. PDF has 7,565,897,280 bytes and 235,963,576 objects.
These are live reachable objects after MAT processing, not the HPROF file
size or a sum of overlapping owner reports.

| Retained owner union, bytes | Thingsboard | Conductor | PDF |
| --- | ---: | ---: | ---: |
| Run managers and runners | 2,457,938,256 | 2,713,285,808 | 4,824,261,880 |
| Method actors and edge work | 1,819,874,864 | 2,090,334,664 | 4,071,093,152 |
| Publication and subscription indexes | 503,534,728 | 652,523,136 | 815,715,304 |
| Records and incoming demand snapshot | 2,821,963,984 | 84,980,128 | 31,623,384 |
| Contexts and method forms | 994,238,816 | 229,416,464 | 235,895,280 |
| Resolved-rule owners | 1,348,018,624 | 431,519,288 | 236,323,776 |
| AP managers and interners | 38,033,936 | 9,293,104 | 17,435,168 |
| Actor pending-publication lists | 380,801,064 | 56,126,856 | 566,506,416 |
| IR owners, outside the selected scope | 2,647,289,688 | 3,637,463,912 | 1,532,302,680 |

These unions overlap. Do not add them, subtract one from another, or call
their bytes an available saving. An object referenced by two owners can
remain live after either owner releases it.

### Pending publications are actual actor work

Thingsboard has 12,935,530 global Publication objects. Querying the actual
actor lists gives 12,770,146 pending entries across 3,308 nonempty actors.
The independent retained-list query contains exactly that many Publication
objects: 98.72% of the global population. This establishes their owner;
it does not infer pending status from a class histogram alone.

The largest actor has 882,265 pending publications, 4,210 cumulative
accepted edge/summary deltas and 207 fact-worklist entries at the snapshot.
The top 40 actors hold 84.17% of pending publications. The retained
list union is 380,801,064 bytes, including 306,483,504 bytes of Publication
wrappers and 67,098,792 bytes of backing arrays. This is only 3.34% of
the reachable heap. This does not establish that removing wrappers would
provide enough memory for completion.
Shared payloads are often retained elsewhere; their complete size and
duplicate fraction cannot be read from this union.

Conductor similarly has 1,903,333 actor-pending publications, versus
1,915,375 global Publication objects. Its 408 nonempty actor lists hold
99.37% of that class population. The largest list has 219,016 entries.
The pending union is 56,126,856 bytes. Its larger owners include active
actor/index work and IR; the latter remains outside this proposal.

PDF has 19,317,637 pending publications across 439 nonempty actors,
versus 20,499,949 global Publication objects. The pending lists contain
94.23% of that class population. The largest list has 3,674,610 entries.
Its 566,506,416-byte pending union is material, but still smaller than
the 4,071,093,152-byte actor/work union. These unions overlap.

Source inspection finds a concrete repeated-work path in restricted runs.
Each nonempty `StrongQueries.admit` in `RunMethodAnalyzer.addLink` scans
all raw summaries and requeues every currently permitted row. An unchanged
row can be reoffered after each unrelated query. `queuePublications`
allocates a wrapper before downstream PublicationIndex deduplication.
Quantum yields retain the actor list; it flushes when the method queue
drains. Thus downstream deduplication does not prevent this pending work.

Only a premise's first false-to-true permission transition needs old-row
backfill. Fresh raw deltas already publish separately. This is an
algorithmic candidate, with an exact frontier rule in the
[next proposal](../design/bidirectional-static-protocol-removal-next-iteration-20261022.md).
The heap does not measure how many pending rows this path produced or
how many are duplicates. The source path supports the investigation;
it is not a measured complete causal decomposition. Run-1 has no such
StrongQueries restriction, so this cannot explain its engine time.

### Storage associations exceed the small mark vocabulary

Conductor has 5,004,261 ConclusionGroup objects, 6,381,711 TaintTree
wrappers, but only 3,476 TaintLeaves and 2,656 MarkSet objects. Each
ConclusionGroup constructor creates two HashMaps. Those cells account
for 10,008,522 maps, 97.44% of the global HashMap object count. PDF has
8,626,639 ConclusionGroups and 10,676,783 TaintTree wrappers. A small mark
grammar does not bound method/original/statement/index associations.

This is evidence of materialized relation storage, rather than a large
population of distinct interned mark payloads. It does not prove that
every cell or wrapper is redundant. Nor does it justify replacing the
algorithm by smaller containers without first identifying avoidable rows.
Current E6 binding/combination counters are zero in these snapshots;
they do not support a live full-ND product explanation for these owners.
The later-run allocation profile independently shows subtraction and
subsumption work in these growing stores.

### Incoming guidance and rule owners

Thingsboard has 6,956,267 physical DemandPattern objects. That equals
the 6,956,266 logged run-1 explicit admissions plus the control zero
pattern. The records/incoming-demand owner union contains the same
population, and the largest displayed RunConfig retains 2,701,095,032
bytes. A separate query follows the actual run-2 manager's incoming
DemandStore. That selected owner retains **2,683,225,184 bytes**, 23.53%
of the reachable heap, and all 6,956,267 physical DemandPattern objects.
Its `byMethod` directory contains 6,956,266 of them across 73,608 unique
method keys and 73,608 unique PathTrie references. The remaining control
pattern belongs to the same store. This identifies incoming run-1 guidance
as a major retained owner; it is not inferred only from a broad class name.
Physical counts still do not prove unique semantic field/group relations
across methods or establish which pairs can be removed.

The corrected partition has a positive type/reference control and these
complete physical DemandPattern counts:

| Entry / paired exit base | Objects |
| --- | ---: |
| STATIC / STATIC | 5,890,526 |
| STATIC / explicit ZERO | 10,434 |
| STATIC / other or absent | 453,776 |
| Nonstatic / STATIC | 106,630 |
| Neither STATIC | 494,901 |
| Total | 6,956,267 |

The categories cover the complete incoming-store population. **84.68%**
are static/static; 92.89% have a static entry or exit. The global PathFact
partition has 12,352,259 static and 1,568,311 other objects, summing to
13,920,570. The global PathFact population also includes objects outside
the selected incoming store, so do not treat it as a method-owned pair count.

This gives a measured reason to investigate static relay elimination.
It does not prove that every static/static pair is a duplicate, has one
group, fits the canonical shortcut or came only from caller promotion.
STATIC includes root, exact class-marker and field positions in this
partition. It is not a count of field-only relay summaries.
Deleting those pairs would violate this iteration's preservation contract.
The next proposal marks a shared exact-guidance replacement as a **major
new contract**, with constructive coverage and FULL-execution proof gates.

The displayed SpringRuleProvider retains 1,286,445,232 bytes. That owner
also references its delegated base provider and context. Its size does
not isolate custom Spring rule generation. The global histogram has
12,477,747 ContainsMark and 3,741,234 TaintMethodExitSink objects. Counts
alone do not prove which cache or rule family created all of them.
No broad Spring, source or exit-rule rewrite is selected from this evidence.

Complete histograms contain no old run-1 point-resume/static-service
owners in these run-2 snapshots. The active actor contexts are backward
limit 1. This rules out retaining those specific old protocol objects as
the cause of the current OOM. It does not rule out persisted run-1 guidance
or other records, which intentionally survive handoff.

Each base packet has 41 query outcomes: 20 complete CSVs, two bounded top
displays, one empty/unavailable result, 13 no-CSV results and five failed
queries over absent run-1 owners. Complete histograms independently
establish those owner absences. Missing tables remain unavailable.

The optional incoming-demand packet preserves two failed adapter attempts.
V1 used invalid OQL string quotes. V2 fixed the OQL literals, but MAT's
outer command parser consumed them; its apparent all-nonstatic partition
is invalid. V3 preserves quotes through that parser. All eight V3 queries
produce valid CSVs, including the actual known ZERO-pattern control.
The final counts above use V3 plus the valid V1 manager/store queries.
All attempts, source bindings and capture/process closure receipts remain
available. No analyzer code changed during these reader corrections.

## Host resources and comparison limits

RSS is process resident memory, not Java heap. Host swap is total used
swap, not a paging rate. Values below are whole-command sample ranges.

| Project / side | RSS max GiB | Host MemAvailable GiB min–max | Host swap used GiB min–max |
| --- | ---: | --- | --- |
| Thingsboard main | 13.193 | 0.977–14.967 | 20.815–28.553 |
| Thingsboard candidate | 14.024 | 3.005–28.003 | 28.345–30.691 |
| OWASP main | 6.894 | 7.144–34.818 | 29.822–30.603 |
| OWASP candidate | 9.590 | 20.940–39.907 | 29.230–30.542 |
| Conductor main | 9.383 | 30.649–46.931 | 29.231–29.249 |
| Conductor candidate | 9.657 | 37.522–46.847 | 29.190–29.218 |
| PDF main | 7.899 | 38.132–46.630 | 29.188–29.195 |
| PDF candidate | 9.563 | 30.027–45.583 | 29.177–29.379 |

Thingsboard main had especially low available memory during prescan. That
250.165-second phase cannot be attributed to the candidate. No PSI, disk
activity or host CPU occupancy was collected. Major faults can be file-backed.
The resource ranges do not prove paging thrash or explain a timing difference.

Nominal run-1 process CPU / major-fault estimates are 2,398.770 seconds /
356 on Thingsboard, 608.880 / 149 on OWASP, 223.095 / 4 on Conductor and
184.010 / 16 on PDF. Parallel CPU time can exceed wall time. Interpolation
brackets, sample support, load ranges and clock-alignment limits are kept
in the resource review. No native sample fraction is multiplied by these
CPU totals to claim exact ownership.

## Evidence and next iteration

The campaign uses a fresh main/current pair at each prescribed configuration.
It shows complete run-1 engines but failed end-to-end resource outcomes,
large unrefined reports, two strict export-coordinate gaps, and significant
remaining allocation/GC work. It does not establish a causal per-patch
speedup or a twofold whole-engine improvement.

The [next proposal](../design/bidirectional-static-protocol-removal-next-iteration-20261022.md)
ranks static-only relay elimination first by run-1 potential, with an
explicit new semantic contract and proof requirement. Repeated GROUP
normalization has a narrower source-local proof scope. First-permission
summary backfill is a separate later-run candidate. The report establishes
neither a removable 84.68% of heap nor a twofold engine improvement.

Compact authorities:

- [Eight-command terminal receipt](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/preparation/cohort-terminal.json).
- [Strict source adjudication](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/finding-review-30c1/source-adjudication.md).
- [Resource and iteration review](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/resources-iterations-closed-review.json).
- [Native interpretation](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/native-interpretation.md).
- [JFR interpretation](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/jfr-profile-interpretation.md).
- [JFR header and recording hashes](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/jfr-metadata/review.json).
- [Thingsboard run-2 owners](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-thingsboard/owner-interpretation.md).
- [Conductor run-2 owners](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-conductor/owner-interpretation-v2.md).
- [PDF run-2 owners](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-Stirling-PDF/owner-interpretation.md).
- [Incoming-guidance root attribution](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-thingsboard-demand-guidance-v1/owner-interpretation.md).
- [Corrected static partition](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-thingsboard-demand-guidance-v3/compact-review.json).
- [Combined partition interpretation](/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022/validation/mat-thingsboard-demand-guidance-v3/owner-interpretation.md).
