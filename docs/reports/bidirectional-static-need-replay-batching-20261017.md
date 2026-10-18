# Static need replay batching: project measurements

Study date: 17 October 2026; heap review closed on 18 October. Measured runtime:
`f18b1c5bb58e5856e4b15b3a71e09e3708dff0d1`. Fresh main baseline:
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
Status: **study and scoped heap review complete; acceptance gates failed**.
The implementation is committed. The project acceptance and 60-second
run-1 gates are **not passed**.

The [specification](../../spec/run1-static-need-replay-batching.md),
[implementation map](../design/bidirectional-static-need-replay-batching-implementation-20261017.md)
and [validation report](bidirectional-static-need-replay-batching-validation-20261017.md)
describe the selected change and its tests. This report uses fresh executions
of the measured runtime and main. Previous runtime reports are historical
and do not establish current acceptance. IR storage is outside scope.

## Method and input integrity

Evidence root:
`/drive-testcomp/opentaint-go-rules/static-need-replay-batching-20261017/`,
called `P` below. `run-plan.json`, `study.json`, and each leaf's manifest
record the exact commands and source maps. All four main invocations ran
first, then all four candidate invocations. Each used Gradle
`:runProjectAnalyzer` directly, with a fresh output directory.

| Project | Heap | IFDS budget | Outer limit |
| --- | --- | ---: | ---: |
| OWASP | 8 GiB | 1,000 s | 1,200 s |
| conductor | 8 GiB | 1,080 s | 1,200 s |
| ThingsBoard | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | 8 GiB | 1,080 s | 1,200 s |

OWASP follows `.github/workflows/ci-analyzer-owasp.yaml`. The other projects
use their configured models, rule selection and extensions. Both sides use
Temurin 17.0.18, batch size 2,000 and disabled multi-release jar lookup. The
candidate uses `--ifds-bidirectional` and numeric optimization statistics.
The Go server build is excluded for these JVM models. No alternate heap,
timeout or rule-filter experiment is included.

Main has only the approved MemoryManager live-heap capture hook. It has no
analyzer optimization from this branch. Every closed invocation has positive
source stability. Both full input audits pass 80 checks over 41 datasets.
All 41 dataset manifests are byte-equal before and after the study; all
non-path check facts agree. Audit times bracket the complete study.
`P/input-audit-comparison.json` records this check. This is endpoint equality,
not continuous monitoring of input changes.

Previous generated raw captures and indexes were removed before this study:
49 files, 99,041,937,718 logical bytes. The cleanup receipt in
`P/preparation/` preserves the exact file list and checks. Current captures
remain available for this report and its dependent analysis.

## Times and reached iterations

All four main invocations succeed. The candidate has one timeout and three
OOMs. None meets the **60-second run-1 engine** target.

| Project | Main command wall | Candidate command wall / status | Run-1 engine | Latest reached / completed / accepted forward |
| --- | ---: | --- | --- | --- |
| OWASP | 119.242 s | 1,039.669 s / TIMEOUT | 268.372 s, COMPLETE | run-7 FORWARD / run-6 BACKWARD / run-5 |
| conductor | 91.178 s | 847.446 s / OOM | 774.693 s, incomplete | run-1 FORWARD / none / none |
| ThingsBoard | 211.378 s | 405.958 s / OOM | 193.875 s, incomplete | run-1 FORWARD / none / none |
| Stirling-PDF | 47.103 s | 309.591 s / OOM | 98.155 s, COMPLETE | run-2 BACKWARD / run-1 FORWARD / run-1 |

Accepted forward means that the driver added a forward result to its report.
It does not mean that project acceptance passed. Main uses the legacy
analysis, so run-N is not applicable. Every candidate starts at run-1,
FORWARD, field limit 1. No limit-0 run exists.

| Project / engine | Status | Engine seconds | Following barrier seconds |
| --- | --- | ---: | ---: |
| OWASP run-1 FORWARD | COMPLETE | 268.371836 | 2.292317 |
| OWASP run-2 BACKWARD | COMPLETE | 133.907903 | 0.437838 |
| OWASP run-3 FORWARD | COMPLETE | 52.448708 | 2.186834 |
| OWASP run-4 BACKWARD | COMPLETE | 144.791899 | 0.680217 |
| OWASP run-5 FORWARD | COMPLETE | 52.335813 | 2.146150 |
| OWASP run-6 BACKWARD | COMPLETE | 218.283324 | 1.380978 |
| OWASP run-7 FORWARD | TIMEOUT | 50.707045 | unavailable |
| conductor run-1 FORWARD | OOM | 774.692973 | unavailable |
| ThingsBoard run-1 FORWARD | OOM | 193.874724 | unavailable |
| Stirling-PDF run-1 FORWARD | COMPLETE | 98.154732 | 1.255659 |
| Stirling-PDF run-2 BACKWARD | OOM | 156.269520 | unavailable |

Conductor's engine time includes a 16.304 s live dump; ThingsBoard's includes
28.215 s. Stirling-PDF's 18.135 s dump is in run-2. Do not charge it to run-1.
Subtracting only the run-1 capture intervals still leaves both failed run-1
prefixes above 60 seconds.

| Candidate preparation, outside run-1 | Prescan | Rule metadata | Static catalog |
| --- | ---: | ---: | ---: |
| OWASP | 32.761 s | 37.112 s | 0.119 s |
| conductor | 25.231 s | 7.828 s | 0.153 s |
| ThingsBoard | 129.351 s | 48.111 s | 0.619 s |
| Stirling-PDF | 17.612 s | 7.347 s | 0.107 s |

`P/runtime-measurements.json` retains exact engine/barrier nanoseconds and
phase markers. Command wall includes setup, analysis, reporting and teardown.
The candidate takes longer and fails under limits where fresh main succeeds.
An incomplete candidate and a completed main do not give an equal-work
speedup ratio. These are single diagnostic executions; they do not establish
a speedup against an older candidate runtime.

| Project | Main logged full-scan phase | Main / candidate peak process RSS |
| --- | ---: | --- |
| OWASP | 44.168 s | 8,986,120,192 / 10,207,617,024 B |
| conductor | 24.586 s | 9,926,053,888 / 10,254,327,808 B |
| ThingsBoard | 30.521 s | 14,382,194,688 / 14,875,631,616 B |
| Stirling-PDF | 4.993 s | 7,629,438,976 / 10,346,500,096 B |

Main's logged full phase includes confirmation and trace generation. It is
not the candidate's run-1 engine timer. RSS is the sampled process peak over
the invocation, not retained Java heap or a per-iteration measurement.

## Reported vulnerability locations

The strict comparator retains full rule, method and sink fingerprint, with
explicit fallbacks where needed. It also checks raw locations, spans and
occurrence counts. Callback counts are not sink identities.

| Project | Main unique | Candidate unique | Common | Missing exact identities | Added exact identities | Main / candidate occurrences |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| OWASP | 4,112 | 4,112 | 4,112 | 0 | 0 | 4,112 / 9,179 |
| conductor | 7 | 0 | 0 | 7 | 0 | 7 / 0 |
| ThingsBoard | 14 | 0 | 0 | 14 | 0 | 14 / 0 |
| Stirling-PDF | 17 | 64 | 15 | 2 | 49 | 17 / 161 |

All comparisons remain incomplete because the candidate terminates
abnormally. SARIF export succeeds on both sides. Equal identities have no
distinct raw-location/span change. Full removed/added identities and
occurrence details are in `P/comparison/`.

OWASP's available accepted run-5 report covers every baseline sink identity.
It adds 5,067 occurrences across 2,420 existing identities. These are report
occurrences, not new unique sink locations or the batching-dependent
diagnostic counts allowed by the spec. Simple output does not establish the
individual path provenance. The timeout prevents an overall acceptance pass.

Conductor and ThingsBoard fail before any forward report is accepted. Their
empty exports omit 7 and 14 baseline observations. The source-bound reviews
trace the cause: RunManager returns no completed analyzers on OOM;
IterationDriver leaves before the report barrier; ReportBuilder also rejects
a non-COMPLETE result. These are resource-censored omissions. They do not
prove that a completed analysis would miss the paths. The no-FN gate still
does not pass.

Stirling-PDF exports its accepted run-1 report after run-2 fails. Two baseline
locations are absent, with different-return candidates in the same rule and
method:

| Method | Main return | Candidate return |
| --- | --- | --- |
| `ConvertEmlToPDF.convertEmlToPdf` | IR 103, lines 133–134 | IR 126, lines 148–149 |
| `PipelineController.handleData` | IR 50, lines 90–91 | IR 109, line 130, `return null` |

The current Simple exporter has no witness code flow. At a synthetic normal
exit, TraceMessageBuilder selects the last return; the SARIF generator uses
its instruction in the fingerprint. This explains the displayed location
substitution. It proves neither that the displayed null/constant is tainted
nor that the original baseline branch is covered. Both strict removals remain
failures. These are not pre-first-report omissions: run-1 did complete.

The 49 new identities comprise 28 path-traversal locations and 21 XSS
locations, with 143 occurrences. Three extra occurrences on common identities
account for the rest of the 146 added occurrences. They are unvalidated
candidates, not 49 proved false positives. The no-FN and few-FP gates remain
open. The fresh PDF review records exact fingerprints and source paths.

## Work and CPU

Source unions remain small compared with routing work. A source is a full
method/original-field key with a cumulative mark set. A wave is a temporary
multi-start traversal for one exact mark set in one selection. Intermediate
methods have no authoritative static request rows.

| Run-1 cumulative measure | OWASP | conductor | ThingsBoard | Stirling-PDF |
| --- | ---: | ---: | ---: | ---: |
| Fresh binding-change records | 0 | 237,746 | 141,831 | 113,105 |
| Full-source selections | 0 | 59,412 | 8,077 | 25,484 |
| Selected source rows | 0 | 46,785,863 | 4,857,642 | 13,277,515 |
| Created grouped waves | 0 | 4,769,287 | 179,279 | 1,208,397 |
| Static node entries | 0 | 333,253,162 | 55,925,501 | 30,678,399 |
| Subscriber deliveries | 0 | 407,180,714 | 139,964,426 | 33,254,234 |
| Ordinary + packed binding scans | 0 | 1,573,274,939 | 147,322,363 | 67,439,219 |
| Peak work-owned waves | 0 | 236,122 | 172,776 | 7,986 |
| Abandoned work-owned waves at joined clear | 0 | 102,711 | 172,776 | 0 |
| Accumulated grouping worker elapsed | 0 | 10.408 s | 0.643 s | 0.927 s |
| Scalar new initial admissions | 36,592 | 54,268 | 122,628 | 87,392 |

Conductor and ThingsBoard columns are interrupted prefixes. Scalar initials,
packed memberships, input arrivals, node visits and queue events are different
units. The large event counts are not counts of distinct facts. Work-owned
wave gauges are not JVM-live wave counts. Grouping worker time is not a CPU
sample share or engine wall fraction.

Local batching works: conductor combines about 4.00 binding-change records
per full selection and 9.81 selected roots per wave. ThingsBoard combines
about 17.56 and 27.10. The completed PDF run-1 has consistent groups/waves and
roots/selected-row counts. Grouping uses 1,208,397 waves for 13,277,515 selected
rows, 90.90% fewer waves than one wave per selected row. All 13,277,515
source roots remain selected. These are local mechanism measurements, not
a measured whole-engine speedup.

The remaining multiplier is replay across turns. Each dirty turn can start a
new full replay while many earlier waves remain queued. Each wave has fresh
visited state and sends its own counted notifications. The implementation
does not join active waves from different turns.

| Normal native window, wholly run-1 | Parsed samples | Named GC stacks | Static replay/routing stack union |
| --- | ---: | ---: | ---: |
| OWASP | 8,082 | 60.17% | 0% |
| conductor | 31,153 | 51.27% | 24.53% |
| ThingsBoard | 32,566 | 36.57% | 34.90% |
| Stirling-PDF | 27,807 | 34.15% | 16.01% |

Each row uses exact per-stack unions, with zero malformed lines. Categories
can overlap; do not add inclusive shares. Samples include collector threads.
They measure requested 30-second windows, not the whole engine or removable
wall time. All normal windows precede their corresponding dumps.

ThingsBoard witness stacks show static fan-out entering UnitRunner.postEvent
and RunManager.accept, and static forwarding entering RunManager.summaryStorage.
Both paths use RunManager's admission monitor. The static/monitor intersection
is 20.56% of all samples, but not all of it is attributed to one lock. Avoiding
notifications also avoids admission work; lock-only tuning leaves the replay
multiplier in place.

OWASP has no static routing work. Its normal cleaner-chain stack union is
31.96%; the cleaner/type-filter overlap is 14.15%, and cleaner/IR-lookup
overlap is 7.46%. Source paths lead through cleaner composition, path filters,
accessor type tests and IR class lookup. This motivates avoiding repeated
analyzer checks; it does not justify an IR storage change. GC allocation
causes and the eligible cleaner fraction are not proved by these samples.

OWASP's near-limit profile has 5,767 samples and spans the end of run-6,
its barrier, and the start of run-7. Its 35.11% builder/rewriter union is not
run-1 evidence. The other candidates fail before their near-limit window.
Main finishes too early for a normal native window. All eight continuous
JFR captures are retained; no weighted allocation census is claimed here.

### Ordinary Base request duplication

| Run-1 Base admission | Offered members | Already in temporary P | P fraction | Fresh P members |
| --- | ---: | ---: | ---: | ---: |
| OWASP | 61,356,273 | 61,014,095 | 99.44% | 299,471 |
| conductor | 1,243,884,931 | 1,241,699,440 | 99.8243% | 373,051 |
| ThingsBoard | 75,348,303 | 74,889,211 | 99.3907% | 210,962 |
| Stirling-PDF | 1,081,566 | 879,583 | 81.32% | 121,698 |

These source-checked counters exclude StaticField collection, which returns
before Base admission accounting. Static routing can still contribute when
it reaches a Base endpoint. For conductor the exact observed partition is
1,243,884,931 = 1,812,440 R duplicates + 1,241,699,440 P duplicates + 373,051
fresh P members. This is direct evidence of repeated Base admission work,
not an inference from delivered-versus-committed counters.

P duplicates do not add new storage members, but still pay collection and
R/P membership checks. Delivered offers also pay messaging and owner handling;
local rule/cleaner offers do not require a delivery. Work priority can defer
the method work that commits P to R. These are candidates for work avoidance;
a duplicate membership fraction is not a CPU, retained-byte or
achievable-saving fraction.

## Heap review

The conductor MAT report for its run-1 snapshot is complete. ThingsBoard
run-1 has a limited packet of 15 preserved owner tables. Stirling-PDF's
run-2 report is closed, with per-query failures and unavailable cells stated
below. The owner comparisons keep these scopes distinct.
The normal MAT parser uses Java 21 and a 20 GiB heap, one capture at a time.
Source/input/capture bindings and actual parser-process closure are checked.

Conductor's reachable heap has 224,544,086 objects and 7,619,556,096 shallow
bytes. The following are **collective retained selections**, not disjoint
owners or promised net savings. They overlap with each other.

| Conductor run-1 scope | Retained bytes | Query |
| --- | ---: | --- |
| IR storage context, outside optimization scope | 3,233,231,288 | Q45 |
| Combined actor and caller owners | 1,791,722,536 | Q46 |
| All physical conclusion/kind-group bodies | 1,320,264,456 | Q09 |
| Worklists, queues and channels | 675,598,360 | Q39 |
| Raw and selected summary stores | 665,625,248 | Q11 |
| Unresolved-call observation accumulators | 508,036,464 | Q48 |
| Static-need and concrete-static-entry event objects | 499,626,776 | Q36 |
| Live wave marks and visited sets | 403,418,672 | Q34 |
| Normal CALL/ENTRY/EXIT bound sink caches | 320,733,824 | Q42 |
| Method statement directories | 219,585,136 | Q08 |
| Ordinary caller inputs, including their index | 121,387,464 | Q14 |
| Committed ordinary Base request roots | 3,467,952 | Q19 |
| Authoritative static source roots | 574,352 | Q32 |
| Temporary replay delta-map roots | 12,264 | Q51 |

There are 1,605 authoritative static source rows, 102,713 physical waves,
1,369,301 StaticNeed event objects and 191,798 MarkRequestsIn event objects.
These physical objects are not exact queue occupancy. All 219 runner
accumulators have an empty pending source map and a false graph flag
at capture; detached batches and queued waves have separate owners.

The heap also has 17,465 actor store owners, 2,759,758 ConclusionGroup objects,
287,191 FlowGroup objects and 2,674,623 TaintGroup objects. The global scalar
InitialAp vocabulary has 395 objects. That vocabulary is neither actor-weighted
admission nor the packed initial population. A small grammar does not make
the method/context/statement product small. Both persistent store bodies and
temporary routing work matter in this capture.

### ThingsBoard run-1

The following individually completed query tables were copied and checked
before the parser stopped. They belong to a limited 15-table packet, not an
accepted complete 54-query report.

| ThingsBoard run-1 scope | Retained bytes | Query |
| --- | ---: | --- |
| Static-need and concrete-static-entry event objects | 4,075,308,816 | Q36 |
| Worklists, queues and channels | 3,554,709,312 | Q39 |
| Live wave marks and visited sets | 3,258,710,824 | Q34 |
| IR storage context, outside optimization scope | 1,620,144,632 | Q45 |
| Normal CALL/ENTRY/EXIT bound sink caches | 1,092,022,048 | Q42 |
| Combined actor and caller owners | 808,527,728 | Q46 |
| All physical conclusion/kind-group bodies | 301,325,256 | Q09 |
| Raw and selected summary stores | 183,913,432 | Q11 |
| Unresolved-call observation accumulators | 49,484,624 | Q48 |
| Committed ordinary Base request roots | 5,321,696 | Q19 |
| Authoritative static source roots | 292,016 | Q32 |

The reachable heap has 350,223,042 objects and 11,414,128,752 shallow bytes.
There are 1,349 source rows, 172,785 physical waves, 19,145,155 StaticNeed
event headers, 9,170,898 visit objects and 158,996 MarkRequestsIn headers.
These are physical populations, not exact queue occupancy or distinct facts.
All selections in the table overlap; neither columns nor rows can be added.

The main core pressure here is temporary replay state and queued delivery,
not a large authoritative request store. Turn batching combines local route
updates but leaves many waves active across turns. The 172,776 abandoned
work-owned waves agree in scale with the physical census; they are different
measurements. Bound sink lists are another large strong owner. The group-body
scope is much smaller than the wave scope, so a denser fact representation
alone does not address this capture's main core owner.

The preserved evidence is under
`P/heap-tools/jobs/job-thingsboard-20261017T233522.874686Z/partial-owner-evidence/`.
The final Q54 query was stopped by the operator after more than ten minutes
in instance-field collection. The Java process exited with status 143, and
the wrapper exited with status 4. A single thread snapshot shows instance
field reads during OQL UNION; it does not establish a deduplication defect
or a Q54-specific GC share. This is an interrupted postprocessing query,
not another analyzer timeout or OOM.

The parser produced no final archive. Its shutdown removed the temporary
report before the other tables were copied. Thus 15 tables survive, 38
others are unavailable, and Q54 is interrupted/unavailable. Do not reconstruct
the lost results from the fact that their query names appear in a log. The
original capture is unchanged, all recorded parser processes are absent,
and post-run source/input checks have no binding errors. There was no retry.
`limited-query-outcomes.json` inventories all 54 outcomes. The root review,
`P/reviews/thingsboard-limited-owner-root-review.json`, verifies the 15
surviving CSV hashes and accepts only those scoped results.

### Stirling-PDF run-2

This capture belongs to BACKWARD run-2. Its completed and accepted forward
iteration remains run-1. The reachable heap has 237,192,751 objects and
7,612,375,424 shallow bytes. The scopes below overlap.

| Stirling-PDF run-2 scope | Retained bytes | Query |
| --- | ---: | --- |
| Combined actor and caller owners | 4,620,261,000 | Q46 |
| All physical conclusion/kind-group bodies | 4,230,622,232 | Q09 |
| Method statement directories | 3,448,282,576 | Q08 |
| IR storage context, outside optimization scope | 978,093,480 | Q45 |
| Raw and selected summary stores | 501,071,816 | Q11 |
| Ordinary caller inputs, including their index | 325,432,440 | Q14 |
| Rules, joins, records and guidance | 270,132,480 | Q43 |
| Forms and method context | 221,246,760 | Q41 |
| Normal CALL/ENTRY/EXIT bound sink caches | 151,305,232 | Q42 |
| Strong current query owners | 33,669,576 | Q44 |
| Worklists, queues and channels | 7,724,008 | Q39 |

This is persistent statement/fact state pressure. The queue scope is small
relative to the conclusion-group and actor scopes. Reducing run-1 wave
backlogs cannot by itself remove this run-2 population. The directory scope
also reaches group bodies; it is not 3.45 GB of index metadata. A class/body
population is not a count of distinct semantic facts or proof of mark-level
duplication. Later-run work avoidance needs a separate investigation.

The physical census has 9,790,892 ConclusionGroups, 9,891,365 TaintGroups
and 2,581 FlowGroups. Q09 includes 19,581,784 HashMaps with 939,925,632
shallow bytes, and 19,787,891 ArrayLists with 474,909,384 shallow bytes.
These are parts of the 4.23 GB group selection, not extra owner scopes.
Q08 reaches 7,933,030 ConclusionGroups. Q07 counts 21,328,521 allocated
statement cells; capacity is not occupied fact count. Q10 records 678,170
raw and 497,236 selected full-premise key memberships across 7,258 summary
stores. An ND premise key counts once, not once per conjunct. The result
has insufficient geometry to attribute keys to static or nonstatic shapes.
The next investigation must measure group occupancy and derivation causes
before choosing work avoidance or a different group representation.

The MAT process exits 0 and produces its archive. The query reader exits 5:
30 queries have available CSVs, five have unavailable blank cells, twelve
have empty selections, and seven fail with an OQL no-result error. The seven
failed retained selections target run-1 packed actor/router/wave/pending
state: Q25, Q27, Q32, Q34, Q51, Q53 and Q54. Their errors remain failures;
they are not successful zero-byte retained queries. The full per-query
inventory is in
`P/heap-tools/jobs/job-Stirling-PDF-20261018T000624.748710Z/query-outcomes.json`.
Separately, the complete Q01 histogram has zero instances of the run-1
packed actor classes, router, waves, visits, StaticNeed events, pending replay
and MarkKey. That is the physical absence evidence. It agrees with joined
run-1 cleanup, without changing the failed-query outcomes.

### An avoidable observation store

`RunMethodCallServices.observed` is an AddedFactAccumulator used only to
suppress repeated unresolved-call observer callbacks. It retains full
original premises, layers and TAINT payloads, although the JVM observer uses
only the called method/signature, position and pass-rule category. Conductor's
Q48 selection is 508 MB; it overlaps conclusion-group and other views.

The actual runner and process arguments in all four candidate invocations
omit `--track-external-methods`. The option defaults to false, so
ProjectAnalyzer constructs a null tracker and passes it to JIRInterpreter.
However, that interpreter always implements UnresolvedCallObserver.
SharedObjects exposes a non-null observer, and the core records facts before
`reached` immediately returns on the null tracker. The non-null hook also
forces packed concrete static entries through the ordinary walker solely
for observation.

Thus the fixed configuration performs observation-only storage/work for a
disabled feature. The next proposal exposes disabled capability before
collection. It must preserve all model/transfer effects when avoiding the
observation-driven scalar path. Enabled and generic observer behavior needs
its existing contract; it cannot be silently replaced by coarser callbacks.
No implementation change is included in this report.

### Query and ownership limits

The first report runs all 54 queries with zero query failures: 43 complete
CSV results, seven results with unavailable blank cells, two bounded display
samples and two empty selections. Valid nonblank columns remain usable.
Q33/Q35 show only 80 headers each; Q01 supplies population counts. Blank
fields, computed getters and empty selections are not silently counted as
zero. The review records each case. Transient MarkKey objects are absent in
the whole-heap histogram; temporary grouping is not this capture's main
retained owner.

Q36 selects StaticNeed and ConcreteStaticEntriesIn **event headers**. Q54
selects sources, pending maps, wave state and StaticNeed visit payloads.
These root sets are not nested. Their byte totals must not be subtracted
or used for a disjoint-owner claim. One 508,737,344-byte root ConcurrentHashMap
cannot be attributed to a named subsystem with this packet; the exact-address
follow-up keeps that owner unknown.

## Validation and limits

The common gate passes 1,585 tests; the JVM gate passes 411 tests and the
consumer jar builds. The final 19-case batching/runner selection passes,
including two later test-only additions. Production sources are unchanged
between those checks and the measured commit. The finite Lean audit checks
303 declarations and 28 controls with zero unexpected axioms. It does not
prove the Kotlin coroutine scheduler.

Application checks have 148 passes, 9 failures and 1 skip. The failures remain
visible; assertions, rule filters and skips were not weakened. The validation
report states their extra cleaner observations and path limits. Core/model
passes are not application or project acceptance.

The implementation is complete, but the measured resource and location gates
fail. The 60-second target, application precision gate and complete-project
no-FN/few-FP gate remain unpassed. These failures do not become passes when
the measurement and documentation work is closed.

## Next iteration

The [new proposal](../design/bidirectional-next-iteration-after-replay-batching-20261017.md)
ranks the expected **run-1 memory** opportunities as follows. The order uses
current owner scopes and confidence, not promised net savings.

| Order | Change | Current behavior and reason to change it |
| --- | --- | --- |
| 1 | Combine overlapping graph replays into counted cohorts | Turn-local replay still leaves 3.26 GB of ThingsBoard wave state and overlapping event/queue owners. Combine updates while a cohort is active; give later updates a fresh cohort so late routes remain complete. |
| 2 | Make reproducible bound-cache values reclaimable | Normal bound sink lists retain 1.092 GB in ThingsBoard and 0.321 GB in conductor. Use the existing manager and rebuild exact ordered rule lists when needed. Shared owners and rebuild CPU limit the gain. |
| 3 | Skip disabled unresolved-call observation | Conductor retains 0.508 GB of observation history for a null tracker; ThingsBoard retains 0.049 GB. Expose disabled capability before allocation. Avoiding observer-only packed decoding also requires a transfer-equivalence proof. |
| 4 | Give Work fair service and combine delivered Base admission | Large all-producer P-duplicate counts show repeated admission work. Fair progress and destination unions can avoid part of it. Base-only queued retained bytes are not measured; local rule offers are not message traffic. |

Algorithmic replay avoidance has the largest current target. The cache has
a larger cross-project target than the disabled observer, although removing
disabled work is simpler to validate independently. The rows overlap and
must not be added.

OWASP needs a separate CPU/allocation improvement in cleaner/path/type work:
its run-1 has no static replay. PDF run-2 needs a separate investigation of
persistent statement/fact state. Neither can be credited with a benefit from
a run-1 replay change. No next proposal is selected or implemented here.

## Evidence index

All paths below are relative to `P`:

- `study.json`, `run-plan.json`, `runtime-measurements.json`, and
  `input-audit-comparison.json` bind the eight executions and inputs.
- `comparison/*-closed-review.json` and the per-project identity/location
  files preserve strict missing, added and occurrence comparisons.
- `cpu-review/all-closed-derived/` and each closed native review give sample
  denominators, stack unions and phase limits.
- `heap-tools/jobs/job-conductor-20261017T232451.422942Z/` contains the full
  conductor owner report; the ThingsBoard and PDF job paths above retain
  their actual limited/failed-query outcomes.
- `heap-tools/reviews/all-three-operator-closure-20261018T001757.178143Z.json`
  binds all three original tool attempts, capture stability and process closure.
- `validation/`, `implementation-commit-binding.json`, and `reviews/`
  bind the formal checks, runtime tests and independent document reviews.
