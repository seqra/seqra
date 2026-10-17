# Concrete static propagation: current project measurements

Date: 17 October 2026. Measured runtime:
`6b9291d7dd69ae5f6a1260c7f1021c7500fa56b8`. Fresh main baseline:
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The implementation is committed.
The performance and project correctness gates are **not passed**.

The [runtime validation report](bidirectional-run1-bottom-up-concrete-static-validation-20261017.md)
records the tests and formal checks. The
[implementation map](../design/bidirectional-bottom-up-concrete-static-implementation-20261017.md)
describes the selected change. This report uses new executions of both
revisions. Reports from previous runtime commits are stale for acceptance.
IR storage and reference/cache manager changes remain outside scope.

## Measurement contract

All evidence is in
`/drive-testcomp/opentaint-go-rules/concrete-static-20261017/`, called `P`
below. `run_study.py`, `study.json`, and each leaf's `manifest.json` record
the commands, inputs and source bindings. The study runs the four main
invocations first, then the four candidate invocations, one at a time.
Each invocation uses Gradle `:runProjectAnalyzer` directly.

| Project | Java heap | IFDS limit | Outer limit |
| --- | --- | --- | --- |
| OWASP | 8 GiB | 1,000 s | 1,200 s |
| conductor | 8 GiB | 1,080 s | 1,200 s |
| ThingsBoard | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | 8 GiB | 1,080 s | 1,200 s |

The OWASP rule selection follows `.github/workflows/ci-analyzer-owasp.yaml`.
The other projects use the configured warning/error selection and model
extensions. Both sides use the same project, rules and model inputs.
The candidate adds `--ifds-bidirectional`. The Java process uses Temurin
17.0.18, the CI batch size of 2,000, and disabled multi-release jar lookup.
The Go server build is excluded for these JVM inputs. There is no alternate
heap or timeout experiment.

Main is the fresh remote main revision above. Its isolated checkout has only
the existing live-heap capture hook; the baseline integrity record stores
the exact patch. It has no bidirectional optimization. Core/rule source
hash maps are taken before and after each invocation and are unchanged.
Full input audits before and after the study pass all 80 checks over 41
content datasets, with equal non-path facts and content hashes. The receipts
are `P/input-audit-before.json` and `P/input-audit-after.json`. Main has no run-N
iterations; its iteration fields are not applicable.

Continuous JFR and native CPU profiles are captured under the workload.
The normal native window starts about 60 seconds after the full engine
starts and lasts 30 seconds. A near-limit window is taken only if the
process is still running. Profiles include JVM collector threads. JFRs are retained; the figures below
use native CPU samples and live heaps, not a weighted allocation census.
All percentages below are CPU sample fractions, not wall-time fractions.
Inclusive stack categories overlap and must not be added.

The existing memory guard captures a live heap before it cancels on high
memory use. Dump time is included in the engine's reported elapsed time;
it is shown separately below. A captured heap describes that interrupted
run, not a completed analysis. Heap owner retained sets overlap. Counts of
physical objects, stored bindings and semantic facts are different units.

The prior iteration's raw snapshots, profiles and reproducible MAT indexes
were removed before this study: 52 files, 96,545,069,853 bytes.
`P/previous-capture-cleanup.json` records the exact files.

## Results

All main invocations succeed. The candidate has one timeout and three OOMs.
The 60-second **run-1 engine** target is missed on all four projects.

| Project | Main command wall | Candidate command wall / terminal | Run-1 engine | Latest reached / completed / accepted forward |
| --- | ---: | --- | --- | --- |
| OWASP | 118.210 s | 1,039.605 s / TIMEOUT | 262.966 s, complete | run-8 BACKWARD / run-7 / run-7 |
| conductor | 92.152 s | 237.507 s / OOM | 164.524 s, incomplete | run-1 / none / none |
| ThingsBoard | 216.397 s | 450.438 s / OOM | 202.996 s, incomplete | run-1 / none / none |
| Stirling-PDF | 48.087 s | 807.392 s / OOM | 631.556 s, complete | run-2 BACKWARD / run-1 / run-1 |

“Accepted forward” means the driver added a forward result at its report
barrier. It does not mean that this study passes project acceptance.
All runs start at run-1 with limit 1; no limit-0 run exists.

| Candidate engine | Status | Engine seconds | Following barrier seconds |
| --- | --- | ---: | ---: |
| OWASP run-1 FORWARD | COMPLETE | 262.965505 | 2.506757 |
| OWASP run-2 BACKWARD | COMPLETE | 134.209778 | 0.368848 |
| OWASP run-3 FORWARD | COMPLETE | 50.477235 | 2.122553 |
| OWASP run-4 BACKWARD | COMPLETE | 145.940088 | 0.692096 |
| OWASP run-5 FORWARD | COMPLETE | 49.853608 | 2.151282 |
| OWASP run-6 BACKWARD | COMPLETE | 217.369166 | 1.357686 |
| OWASP run-7 FORWARD | COMPLETE | 52.077265 | 2.059690 |
| OWASP run-8 BACKWARD | TIMEOUT | 5.878056 | unavailable |
| conductor run-1 FORWARD | OOM | 164.523657 | unavailable |
| ThingsBoard run-1 FORWARD | OOM | 202.995982 | unavailable |
| Stirling-PDF run-1 FORWARD | COMPLETE | 631.556305 | 1.625513 |
| Stirling-PDF run-2 BACKWARD | OOM | 117.633695 | unavailable |

Conductor's run-1 timer includes a 16.824 s live dump. ThingsBoard's includes
37.751 s. Even after subtracting only these capture intervals, interrupted
run-1 elapsed times exceed 60 seconds. Stirling-PDF's 18.310 s dump is in
run-2 and must not be charged to its run-1 result.

| Candidate preparation, outside run-1 engine | Prescan | Rule metadata | Static catalog |
| --- | ---: | ---: | ---: |
| OWASP | 32.392 s | 37.397 s | 0.121 s |
| conductor | 24.787 s | 8.002 s | 0.151 s |
| ThingsBoard | 160.704 s | 49.872 s | 0.613 s |
| Stirling-PDF | 17.874 s | 7.979 s | 0.111 s |

`P/runtime-measurements.json` retains exact engine/barrier nanoseconds, phase
markers and counter snapshots. Preparation is relevant to command wall time,
but is outside the user's run-1 engine target. These are single executions
with diagnostics enabled. An incomplete candidate and a completed main do
not give a speedup ratio at equal work. The candidate is slower and less
robust than fresh main under the required limits; a gain over the previous
optimization cannot be claimed from stale reports.

## Reported vulnerability locations

The comparison uses rule ID, sink fingerprint and full method signature.
Where those are unavailable, it retains explicit fallback identities and
warnings. Trace counts are excluded from sink identity. Raw locations,
spans and occurrence counts remain in the comparison artifacts.

| Project | Main unique sinks | Candidate unique sinks | Common | Missing exact identities | Added exact identities | Main / candidate occurrences |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| OWASP | 4,112 | 4,112 | 4,112 | 0 | 0 | 4,112 / 9,179 |
| conductor | 7 | 0 | 0 | 7 | 0 | 7 / 0 |
| ThingsBoard | 14 | 0 | 0 | 14 | 0 | 14 / 0 |
| Stirling-PDF | 17 | 64 | 15 | 2 | 49 | 17 / 161 |

`P/comparison/` contains the full locations, occurrence deltas and source
log evidence. All four comparisons are marked incomplete because the
candidate terminates abnormally. SARIF generation succeeds, and no exported
result gap or rejected vulnerability location is found in these bundles.
Equal sink identities have equal raw location sets in every project.

OWASP's available run-7 report contains every main sink identity. There are
5,067 extra occurrences across 2,420 existing identities, with no added
unique sink. They must not be counted as 5,067 new false-positive locations.
This is available-report coverage, not a successful complete-run gate.
These are report occurrences, not the batch-dependent diagnostic callback
counts that the specification permits to vary. Their individual provenance
is not established by the Simple reports and still needs validation.

Conductor and ThingsBoard fail before the first forward report is accepted.
Their empty exported reports omit all main findings. The immediate cause is
run-1 resource failure before report publication, not evidence that a completed
analysis proved those paths safe. The heap and CPU investigation below
identifies the resource owners. A no-false-negative project gate cannot pass.

Stirling-PDF exports the accepted run-1 DEMAND report after run-2 fails. Its
49 added identities and 146 added occurrences are unvalidated candidates,
not an established count of real vulnerabilities or false positives.
There are also two exact baseline locations absent from that report:

| Method, XSS rule | Main location | Candidate display | Assessment |
| --- | --- | --- | --- |
| `ConvertEmlToPDF.convertEmlToPdf` | IR 103, lines 133–134, IllegalArgumentException response | IR 126, lines 148–149, outer IOException response | Same rule/method, different return; original path coverage unproven |
| `PipelineController.handleData` | IR 50, lines 90–91, `bytesToWebResponse` return | IR 109, line 130, catch `return null` | Same rule/method, different return; original path coverage unproven |

The reporting path explains the substitutions. `JIRBidiAnalysis.kt:194–202`
uses `TracePathGenerationResult.Simple`. At a synthetic method exit,
`TraceMessageBuilder.kt:1141–1168` has no relevant trace and selects the last
return. `JirSarifGenerator.kt:73–77` includes that instruction index in the
sink fingerprint. The displayed constant/null return is not proof that the
constant/null is tainted. It is also not proof that the original baseline
branch has been preserved. These are exact-location failures with an
identified reporter mechanism; they remain strict removals in the table.

`P/comparison/pdf-review.json` records both baseline source paths, current
candidates and source bindings. The added identities comprise 28 path-traversal
locations across 8 files and 21 XSS locations across 9 files. They have 143
occurrences, all without witness code flows. Three extra occurrences at
common identities account for the rest of the 146 occurrence increase.
Later refinement is blocked by run-2 OOM. No whole-method absence or
complete flow parity is inferred from the same-method candidates.

The no-false-negative and few-false-positive requirements remain open for
Stirling-PDF. Abnormal termination and location substitutions must not be
used to silently weaken the primary comparison.

## Time and repeated work

R is the committed per-method nonstatic mark-request union in SummaryStorage.
P is its owner-local temporary store, committed when the method queue is empty.
A static need source holds one mark union at `(full method key, original
static field)`. A wave is one traversal from a source with a fresh visited
set. Intermediate visits do not have authoritative static request rows.

The closed native profiles give these sample fractions. The normal windows
are wholly run-1 and precede the live-heap captures. OWASP's near-limit
window is wholly run-7 FORWARD, although the invocation later reaches run-8.

| Capture | Samples | Collector stacks | Static need handling | Cleaner chain | Monitor slow paths |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP run-1 | 4,144 | 23.26% | 0% | 62.64% | 0% |
| OWASP run-7, near limit | 6,791 | 57.43% | 0% | 0.04% | 0% |
| conductor run-1 | 31,999 | 61.05% | 20.59% | 0% | 13.07% |
| ThingsBoard run-1 | 31,315 | 39.43% | 29.86% | 0.01% | 30.51% |
| Stirling-PDF run-1 | 14,264 | 24.23% | 49.81% | 0.05% | 34.23% |

`P/native-review/` retains the stack categories, intersections, frame tables,
source hashes and capture-time binding. Collector fractions select actual
collector/worker stacks; they exclude ordinary mutator allocation barriers.
A zero sample count is not proof that the path never runs.

ThingsBoard has 4,047 samples in monitor slow paths under
`RunManager.afterHandler`, 3,390 under `accept`, and 2,048 under static-need
lookup of `summaryStorage`. These operations use the same global admission
monitor. The samples establish CPU in lock/spin paths, not blocked duration
or a removable fraction. Reduce duplicate delivery before redesigning locks.

OWASP run-1 has 1,982 samples (47.83%) in the field cleaner read/write wrapper
and 1,604 (38.71%) in `cleanFacts`. The whole cleaner-chain share cannot be
assigned to a plain-cleaner batching fast path. Type-filter application
appears in 1,037 samples (25.02%); class/type lookup appears in 927 (22.37%).
The paths already use classpath caches. The finding is repeated lookup work,
not proof of repeated cache misses or a missing IR cache.

OWASP run-7 instead shows call-plan building in 39.15% of samples and signed
condition normalization in 9.78%, with substantial GC. Existing method-form
slots are managed soft references. No form build/eviction counters were
captured, so these samples do not establish repeated rebuilding.

All main full-scan phases finish before the +60-second native capture
threshold: 44.125, 24.974, 30.676 and 4.782 seconds respectively. No native
main/candidate CPU ratio is available. These main phase times can include
trace generation and are not pure run-1 engine times.

| Run-1 ordinary counters | Scalar new initials | Caller groups created | Offered Base mark memberships | Duplicate in temporary P | Fresh temporary memberships |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 36,592 | 48,710 | 61,356,223 | 61,014,120 | 299,471 |
| conductor | 41,941 | 99,814 | 97,987,940 | 96,308,151 | 190,021 |
| ThingsBoard | 113,881 | 388,658 | 23,392,087 | 23,252,908 | 92,346 |
| Stirling-PDF | 87,392 | 212,233 | 1,073,356 | 871,006 | 121,698 |

These are counts of work and storage admissions, not retained bytes. Scalar
initial counts omit packed logical initials. Request memberships count marks,
not messages. Conductor's maximum first-fresh-P-to-commit delay is 116.391 s;
it includes method work and scheduling. It is not a measured pure queue delay.

The last periodic run-1 logs show 88,583,504 handled events for conductor,
141,285,139 for ThingsBoard and 449,020,487 for Stirling-PDF. They are sampled
progress points, not exact final event counts. ThingsBoard logs 21,334,640
in-flight reservations near capture. This includes queued and executing
protocol work; it is not a fact population. The event volume and native
routing stacks show a large auxiliary workload even before heap attribution.

Ordinary request counters do not count static need sources, traversal waves
or their delivery messages. A small authoritative Base request store does
not show that total request processing is small. End-of-run live gauges
can be zero because failure cleanup has released the owners; those zeros
are not the captured live heap.

The current code has a direct work multiplier:

```text
new actual static FLOW binding
  -> StaticNeedRouter.graphChanged()
  -> snapshot every committed source row
  -> start a fresh wave for every source row
  -> visit each reachable (full method key, original field) again
  -> notify subscribed caller owners
  -> offer Base requests at W-to-Base boundaries
```

Each wave shares one immutable mark batch. Its visited set belongs to that
wave. It suppresses cycles within the wave, but cannot suppress a duplicate
visit in a later wave. Both packed static bindings and ordinary static FLOW
admissions can trigger graph replay. Correctness requires replay after late
graph growth. It does not require a full replay immediately for each small
binding delta. A new batching design must prove that it preserves both
arrival orders and the static-to-Base-to-static bootstrap.

Static needs are posted as priority-1 events. Ordinary nonzero actor Work
has priority 2. A long stream of needs can delay the actor's work and its
method-empty temporary request commit. At a Base endpoint the sender checks
the committed request store, while the owner also has uncommitted temporary
rows. Repeated deliveries can therefore pass the sender check and become
temporary duplicates at the owner. These facts explain a possible feedback
path; CPU samples and counters do not by themselves prove scheduler
starvation or a particular retained-memory fraction.

## Live heap owners

All three snapshots are paired with completed guard START/END markers and
the exact byte count. They are analyzed one at a time with Java 21 MAT,
20 GiB parser heap, normal parsing and no object discard. The current
source-bound 49-query packet is `P/heap-tools-v2/`. The input audits and
runtime source maps are checked before and after each analysis. Parser
indexes and full query results are kept.

Q33/Q35 display at most 80 wave/event headers; they are samples without a
statistical selection guarantee. Their sums are not used. Physical class
counts come from the complete Q01 histogram. Owner bytes below come from
complete retained-set histograms, using their shallow-byte sum. Different
owner queries overlap and must not be added. Blank and empty query results
remain explicit in each `query-outcomes.json`.

GB in the table means 10^9 bytes. Heap configuration GiB uses 2^30 bytes.
Raw HPROF size is not the live object size.

| Snapshot | Capture iteration | HPROF bytes | Parsed reachable bytes | Main measured core owner |
| --- | --- | ---: | ---: | --- |
| conductor | run-1 FORWARD, limit 1 | 11,751,989,021 | 7,603,221,680 | Queues/worklists 2.604 GB; static event payloads 2.312 GB, overlapping |
| ThingsBoard | run-1 FORWARD, limit 1 | 18,555,034,033 | 11,407,918,152 | Static event payloads 4.174 GB, including wave state |
| Stirling-PDF | run-2 BACKWARD, limit 2 | 12,049,781,367 | 7,619,388,608 | Conclusion bodies 4.206 GB |

`P/heap-review/` contains the derived owner tables and all query limitations.
The three actual jobs are `heap-tools-v2/jobs/job-thingsboard-20261017T165827.696769Z`,
`job-conductor-20261017T171403.788204Z`, and
`job-Stirling-PDF-20261017T172213.686078Z`. MAT exits 0 and closes all recorded
processes in all three. Query acceptance remains separate from tool exit.

### ThingsBoard: run-1 temporary routing state

The reachable histogram has 352,270,386 objects and 11,407,918,152 bytes.
The main request-related populations are:

| Population / owner | Captured count or retained bytes | Scope |
| --- | ---: | --- |
| Authoritative static need source rows | 118 | Full router source map |
| Live waves | 688,637 | Physical `StaticNeedWave` objects |
| Static need keys | 53,719,025 | Physical key objects, including repeated equal keys |
| Static need events | 21,306,795 | Physical events, including queued/executing references |
| EventQueue entries | 21,300,032 | All event kinds, physical shells |
| Actual EventQueue items | 21,300,029 | Sum of the captured runner queue sizes |
| Wave visited/mark owner union, Q34 | 3,273,456,144 B | Overlaps event payloads |
| Static event payload owner union, Q36 | 4,173,932,024 B | 36.59% of captured heap; includes wave state |
| Worklist/queue/channel owner union, Q39 | 1,698,971,224 B | Overlaps event payloads |

Q34 contains 54,407,528 HashMap nodes and 42,991,921 static need key objects.
These are temporary visited relations. They are not new taint facts or
permanent static request rows. Q36 retains 144,548,431 objects. A pending
visit keeps its wave and the wave's full visited set alive, including keys
for work already processed. Many independent waves can therefore retain
large overlapping logical traversals.

The source map's exclusive retained set is only 5,832 bytes of map shells.
Its keys/mark payloads are shared with other live owners, so that number is
not the total footprint of all source payloads. The contrast that matters
is the exact 118 source rows versus hundreds of thousands of live waves.

| Other ThingsBoard owner union | Retained bytes | Interpretation |
| --- | ---: | --- |
| Ordinary caller inputs, including index | 181,623,968 | 388,658 groups; not the event population |
| Caller position index only | 61,575,096 | Overlaps caller inputs |
| All conclusion bodies | 236,650,328 | Method and summary stores together |
| Dense method statement directories | 97,357,424 | Includes conclusion bodies and other containers |
| Dense `ConclusionGroup[]` arrays | 2,965,128 | 547,132 allocated cells; occupied count not measured |
| Committed ordinary request roots | 4,594,920 | Base-only R |
| Temporary ordinary/static request roots | 8,100,792 | P and pending local static source builders |
| Packed abstract actor roots | 11,641,872 | Shared DAG owners, not mark counts |
| Packed concrete actor roots | 7,490,496 | Empty seen/pending containers at capture |
| Normal call/entry/exit sink cache union | 1,092,021,952 | Reproducible bindings, not a measured discarded fraction |
| Forms/method context union | 689,312,464 | Not all reclaimable cache data |
| IR storage context | 1,619,264,024 | Context only; optimization outside scope |

All 72,024 concrete actor entry fields are null at this snapshot; their
seen/pending maps and concrete caller directories are empty. This describes
current packed storage, not execution history or every scalar concrete fact.
The ordinary scalar vocabulary has only 43 canonical InitialAp objects;
method-local memberships and packed logical originals use different counts.

All 49 outcomes were inventoried: 39 complete CSVs, 6 CSVs with some unavailable
blank fields, 2 bounded header samples and 2 empty selections. There are no
failed queries. A null selected terminal status is expected before the guard
cancels the live run. Empty StrongQueries matches the run-1 lifetime. The
profile's computed method-count property is unavailable and is not counted
as zero. Null paths in scalar initial vocabulary are retained as nulls.

The main conclusion is algorithmic: repeated reverse-route closure and its
queued work consume far more request memory than the durable source union.
A small source store has not removed the cost of reprocessing its contents.
Optimizing semantic fact packing alone would miss this owner. The multiplier
exists even for a single-mark batch: each new wave builds another visited
relation. Many marks per AP are not required for this failure mode.

These are interrupted run-1 snapshots. Removing route work will allow more
fact work to finish. The current small caller/conclusion owners do not prove
that their eventual completed-run peaks will stay small.

### Conductor: run-1 routing and queued endpoint requests

The reachable heap has 228,791,929 objects and 7,603,221,680 bytes.
There are 416 authoritative static source rows, 1,342,151 live waves,
29,683,890 physical need keys and 2,105,315 StaticNeed event objects.
The actual runner queues hold 2,639,654 items. This is real pending protocol
work, not just an inflated progress counter.

The wave visited/mark union retains 2,115,163,776 bytes; static event payloads
retain 2,312,351,912 bytes. Worklists/queues/channels retain 2,604,236,344 bytes.
These scopes overlap. Ordinary caller inputs retain only 56,344,608 bytes,
conclusion bodies 219,873,544 bytes, and committed/temporary request roots
2,398,440 / 2,038,768 bytes. The queue/worklist retained union includes 534,193 physical
`MarkRequestsIn` envelopes and about 100 MB of int[] arrays. An envelope can
carry Base requests or static-source reentry rows. Their Base-only fraction
and exact queued membership are unmeasured. The large duplicate-offer
counters describe repeated work; they do not describe a similarly large R/P store.

Packed abstract/concrete actor roots retain 25,941,312 / 15,812,728 bytes,
and packed caller roots retain 8,354,872 bytes. Concrete payloads are present
here, unlike the ThingsBoard capture, but these owner scopes are much smaller
than the routing state. Normal sink caches retain 320,744,424 bytes, forms
165,620,064 bytes, and IR context 2,264,699,344 bytes. The first optimization
target remains route processing; IR changes stay outside scope.

All 49 outcomes are inventoried with no failed queries: 39 complete CSVs,
7 CSVs with unavailable blank fields, 2 bounded samples and one empty
selection. Null identity-candidate fields in sampled concrete descriptors
are retained as nulls, not interpreted as false or missing descriptors.

### Stirling-PDF: run-2 conclusion storage

This heap has 237,409,697 objects and 7,619,388,608 bytes. It is a BACKWARD
run-2 snapshot after run-1 completed and its report was accepted. It has
7,258 active actors, 285 runner queue items and 324 in-flight reservations.
Queues/worklists retain 7,890,608 bytes. Run-1 static transport/router/wave
owners are absent from the physical histogram. It is a different problem
from the two run-1 OOMs.

| Run-2 owner union | Retained bytes | Scope |
| --- | ---: | --- |
| All conclusion bodies | 4,205,626,776 | 55.20% of captured heap |
| Method statement directories | 3,430,223,944 | Overlaps conclusion bodies |
| Actor raw/selected summaries | 498,550,136 | Overlaps conclusion bodies |
| Ordinary caller inputs | 325,534,656 | Includes 113,670,128 B position index |
| Rule/join/record/guidance union | 269,655,544 | Not all demand edges |
| Strong actual query owners | 33,454,640 | Ordinary later-run demand queries |
| Forms/method context | 221,051,832 | Reproducible and active data |
| Normal sink cache union | 151,305,360 | Three normal roles |
| IR context | 977,994,048 | Outside optimization scope |

The conclusion union has 9,705,174 ConclusionGroups, 19,410,348 HashMaps,
9,705,174 boolean arrays, 9,807,303 TaintGroups and only 2,581 FlowGroups.
`Groups.kt` eagerly allocates two kind maps and a two-element REACH array
per ConclusionGroup, and two capacity-one layer lists per kind group.
This is a large container cost around mostly TAINT data. It does not prove
that all 4.206 GB is redundant or removable. The unused layer-list fraction
has not been measured.

The source and complete histogram support a narrower target: at least
9,702,593 empty FLOW maps, plus all 9,705,174 REACH arrays, have 698,648,640
bytes of old shell storage. Lazy kind maps and inline REACH bits could
avoid those objects. That is a gross old-object target; replacement layout
and any extra state still require measurement. Dense premise statement
rows allocate 21,332,960 cells. Their occupied fraction is not measured;
their arrays occupy 98,284,792 bytes. Capacity alone is not the dominant
owner here.

Actor pending lists also have 3,767,185 unpublished publication entries.
The existing method-empty summary barrier is active, but its pending list
holds individual deltas. A future study should measure whether they can be
merged by full premise and conclusion header before publication. There is
no dedicated pending-list retained union or combinable-key census here;
these counts do not establish duplicate summaries or a byte saving.

All 49 outcomes remain visible: 31 complete CSVs, 4 with unavailable blank
fields, 10 empty selections, and 4 failed retained queries over absent
run-1 owners (Q25/Q27/Q32/Q34: “OQL Query does not yield a result”). The wrapper
exits 5 to flag these unavailable queries, while MAT itself exits 0. They
are not replaced with numeric zeros or used as run-1 memory evidence. The
complete histogram and live run-2 fields independently establish the absent
run-1 populations and the current run's scope. The other complete owner
queries above remain usable. No second heap parse was needed.

## Acceptance and next step

The selected implementation is complete at `6b9291d7d`. Common/JVM suites
pass; the application selection retains nine strict precision failures
and one existing skip, as detailed in the validation report. The finite
constructive models and selected audit pass. These checks do not establish
whole-project completion or legacy path equality.

The current project result is a failed resource/acceptance gate. Run-1 does
not meet 60 seconds on any tested project. Conductor/ThingsBoard cannot
publish a first report; OWASP has full available baseline identity coverage
but times out in run-8; Stirling-PDF has an accepted run-1 report with strict
location differences and unvalidated extra candidates, then OOMs in run-2.
There is no basis to claim a performance gain at equal completed work.

The [next-iteration proposal](../design/bidirectional-next-iteration-after-concrete-statics-20261017.md)
puts algorithmic static-route batching and traversal sharing first, then
reclaimable bound-rule values and request/reentry delivery reduction. It
also treats OWASP cleaner/type-check work, the reporting location problem,
and the separate run-2 conclusion layout. None of those new changes is
implemented in this iteration. IR storage stays outside scope.
