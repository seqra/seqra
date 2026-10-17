# Run-1 bottom-up static propagation: project measurements

Date: 17 October 2026. Runtime: `ab6c3cd006cb62e6a017c0e3c32d96573d2214e3`.
Main: `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, verified again before launch.
Status: workload, finding, profile and heap investigations closed.

Run-1 meets the **60-second engine target** on conductor and Stirling-PDF.
OWASP takes 265.789 seconds. ThingsBoard runs out of memory in run-1.
None of the four candidate analyses completes. The static identity lane is
active on three projects, but this does not establish an overall performance
win. The new measurements replace earlier runtime reports.

## Latest reached, completed and accepted iterations

| Project | Outcome | Latest reached | Latest complete engine | Latest accepted forward report | Run-1 engine seconds |
| --- | --- | --- | --- | --- | ---: |
| OWASP | TIMEOUT | run-7 | run-6 | run-5 | 265.789, complete |
| conductor | OOM | run-2 | run-1 | run-1 | 44.385, complete |
| ThingsBoard | OOM | run-1 | none | none | 322.250, incomplete |
| Stirling-PDF | OOM | run-2 | run-1 | run-1 | 39.951, complete |

ThingsBoard's failed engine time includes **41.830 seconds of heap capture**.
Its pre-capture run-1 interval is 280.354 seconds and is still incomplete.
Subtracting a dump does not produce a completed-run time. Main uses the
legacy engine, so bidi iteration labels do not apply to its runs.

## Fixed controls and validation

Use direct Gradle `:runProjectAnalyzer`, JDK Temurin 17.0.18 and the fixed
project models. The CI OWASP controls include batch size 2000 and
`multi-release=false`. Conductor keeps its configured extension rules and
passthrough model. There is no alternative analysis configuration.

| Project | Analyzer heap | Analysis timeout seconds | Outer limit seconds |
| --- | ---: | ---: | ---: |
| OWASP | 8 GiB | 1000 | 1200 |
| conductor | 8 GiB | 1080 | 1200 |
| ThingsBoard | 12 GiB | 1080 | 1200 |
| Stirling-PDF | 8 GiB | 1080 | 1200 |

Fresh main ran first, then the committed candidate. All eight runs were
serial. Both phase source checks passed. Before and after input audits each
passed **80 checks / 41 content datasets**. The runtime, main capture patch,
JDK, models, harness, actual commands and source maps are bound in the study.
The previous owned raw captures and reproducible MAT indexes were removed:
49 files, 97,346,250,949 bytes. Compact reports and unrelated files were kept.

The [runtime validation report](bidirectional-run1-bottom-up-static-validation-20261017.md)
records 1,548 common tests and 411 JVM tests passed, and a passed analyzer
consumer build. The application selection has 148 passes, nine known strict
precision failures and one existing skip. All 13 sequence controls pass.
This is not a fully green application gate. The constructive Lean proof
scope remains separate from the selected runtime adapter tests.

## Overall time and engine time

Command time includes JVM/task startup, prescan, engine phases, report work
and any capture. Prescan and run-1 engine time are separate columns below.
Main full-scan time is its legacy phase, including its trace-generation work;
it is not a bidi run-1 measurement.

| Project | Main command s | Candidate command s | Main prescan s | Candidate prescan s | Main full scan s | Candidate peak RSS GiB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 118.244 | 1039.592 | 32.639 | 33.145 | 43.631 | 9.485 |
| conductor | 91.167 | 186.358 | 24.666 | 24.935 | 24.639 | 9.539 |
| ThingsBoard | 219.393 | 542.408 | 151.076 | 131.279 | 30.465 | 13.860 |
| Stirling-PDF | 48.104 | 225.017 | 14.373 | 17.847 | 4.707 | 9.530 |

All four main analyses complete. Candidate command times end in the abnormal
outcomes above and exceed main's command times. This is an adverse overall
result on the fixed workloads; it does not isolate the effect of this patch
against the previous bidi runtime. A shorter failed prefix is not a speedup. RSS includes more
than the configured Java heap and is not retained analyzer memory.

| Project | Measured engine sequence, seconds |
| --- | --- |
| OWASP | run-1 F 265.789; run-2 B 137.190; run-3 F 54.479; run-4 B 151.045; run-5 F 51.191; run-6 B 223.769; run-7 F 36.231 TIMEOUT |
| conductor | run-1 F 44.385; run-2 B 65.032 OOM |
| ThingsBoard | run-1 F 322.250 OOM, including the dump |
| Stirling-PDF | run-1 F 39.951; run-2 B 128.405 OOM |

OWASP's completed report barriers take 0.435–2.438 seconds each. The other
completed run-1 barriers take 0.597 seconds for conductor and 0.431 seconds
for PDF. The 60-second target excludes these barriers and prescan. Metadata
and static catalog setup also precede the engine; their exact intervals are
retained in the metrics JSON.

## Exact findings versus fresh main

The primary identity contains the full rule, sink fingerprint and method
signature. Source spans are checked separately; there are no location changes
under equal primary identities. Occurrences and unique identities are separate. All input differences are
empty; the comparison's incomplete status is caused by abnormal analysis
outcomes.

| Project | Main unique | Candidate unique | Missing main identities | Additional identities | Main / candidate occurrences |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4112 | 4112 | 0 | 0 | 4112 / 9179 |
| conductor | 7 | 300 | 0 | 293 | 7 / 300 |
| ThingsBoard | 14 | 0 | 14 | 0 | 14 / 0 |
| Stirling-PDF | 17 | 64 | 2 | 49 | 17 / 161 |

The current 358-row triage matrix resolves all changed identities to actual
source spans. It separates these causes:

* **ThingsBoard, 14 missing:** run-1 ends in OOM before any report is accepted.
  Empty output is an observed recall loss. It does not show which flows the
  unfinished engine would eventually find.
* **PDF, two exact-location losses:** the missing XSS identities are the EML
  return at lines 133–134 and the Pipeline return at lines 90–91. The current
  output reports the same rule/full methods at their last returns, lines
  148–149 and 130. The current frontend emits synthetic exit sites with Simple
  traces; the SARIF fallback chooses the last return. This display mechanism
  is checked against the current source and output. It does not prove that
  the underlying path sets are equal. The command identity at ProcessExecutor
  constructor line 183 is present twice.
* **342 additional identities:** these are unconfirmed accepted run-1
  candidates: 293 in conductor and 49 in PDF. Later refinement ends in OOM.
  Additional does not by itself mean false positive; per-flow source/guard
  proofs are unavailable for these Simple traces. This large excess leaves
  the few-false-positives target unproved.

OWASP adds 5,067 occurrences across 2,420 retained identities. PDF's 49 new
identities have 143 occurrences; another three occurrences belong to retained
identities. No equal-identity location change is hidden by deduplication.
Conductor attempts to export 301 candidates and exports 300; the rejected
one has an invalid enriched virtual `createDispatcher` sink site. Its bound
error is preserved separately from strict identity counts.

Run-1 NORMAL persistence occurs after `report.add`. It cannot repair the
already selected synthetic exit display. A failed backward run does not
establish a new accepted forward report. Complete recall and precision
acceptance therefore remain unproved.

## Static transport and request work in run-1

| Project | Fresh packed entry memberships | Static relation keys | Fresh relation memberships | Selected scalar calls | Peak seen-node bindings |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 0 | 0 | 0 | 0 | 0 |
| conductor | 222966 | 17753 | 473120 | 1623863 | 112292 |
| ThingsBoard | 1651801 | 93438 | 4777011 | 9792690 | 319642 |
| Stirling-PDF | 73307 | 8027 | 147189 | 1154301 | 116155 |

A relation key includes the full call port. Memberships are diagonal original
field/current-field pairs. Each seen-node binding is one actor/CFG-node map
key, not one field fact. These counters show physical factoring, not bytes
saved. Scalar initial-store memberships overlap with packed original entries
and must not be added to them. The new catalog has zero transitive field-read
request memberships on all four projects; only mark requests remain.
Selected scalar calls include bound sink checks and calls with no output.

| Project | Request offer calls | Offered mark members | Already in committed R | Already pending in P | Fresh P members |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 61282986 | 61356217 | 42684 | 61014062 | 299471 |
| conductor | 17496246 | 21720875 | 7315525 | 13614418 | 790932 |
| ThingsBoard | 559818234 | 723591385 | 381801586 | 309423979 | 32365820 |
| Stirling-PDF | 972600 | 1373951 | 115374 | 1063617 | 194960 |

These are work counters, not standing requests. The three member columns
partition offered members. Existing queue-empty commits and interprocedural
joins already merge mark sets. The repeated work occurs before admission.
OWASP has 99.442% pending duplicates. ThingsBoard has 95.527% committed or
pending duplicates. This supports avoiding repeated request admission work;
it does not establish that every eligibility test can be skipped.

The grammar bounds one AP shape. It does not bound the number of relations
between full method contexts, originals, current payloads and program points.
The heap census must distinguish these dimensions and auxiliary containers.

## CPU and allocation

The native normal window starts about 60 seconds after the first engine start
and requests 30 seconds. It lies in run-1 for OWASP and ThingsBoard, and in
run-2 for conductor and PDF. OWASP also has a successful near-limit window.
The other candidate analyses end before that window. Main's full scan phases
end before a normal 60-second-delay capture; continuous JFR is available.
All eight JFR exports and the native capture reviews are source/input bound.

| Native window | Request admission % | Cleaner chain % | Conclusion groups % | GC-related native % |
| --- | ---: | ---: | ---: | ---: |
| OWASP normal, run-1 | 3.844 | 31.395 | 0.297 | 62.162 |
| ThingsBoard normal, run-1 | 24.867 | 2.237 | 6.712 | 36.159 |
| conductor normal, run-2 | 0 | 0.759 | 13.748 | 66.247 |
| PDF normal, run-2 | 0 | 1.569 | 24.122 | 44.387 |

These are sampled CPU shares in one window. Inclusive method families overlap
and are not additive removable time. Java JFR execution samples omit native
GC CPU. Native evidence also shows monitor and hash-map work on ThingsBoard.
The counters alone do not assign that work to one cause.

JFR allocation weights for run-1 are 515.174 GB on OWASP, 159.917 GB on
conductor, 1,680.759 GB on ThingsBoard and 178.452 GB on PDF (decimal units).
They estimate sampled allocation volume, not live bytes. OWASP's cleaner
chain appears in 71.151% of run-1 allocation weight. On ThingsBoard, ordinary
summary application and request admission are substantial paths. Allocated
request helpers include admission counts, row views, callbacks, mark sets
and static-slot values. Opt-in statistics also allocate helper objects, so
these profiles describe the fixed measured instrumentation.

ThingsBoard has 89.295 seconds of clipped top-level GC pauses in its
280.354-second pre-capture run-1 interval. Concurrent GC event durations
overlap and must not be summed as wall time. Producer/reader depth, retained
chunk coverage, missing stacks and truncation flags remain in the closed
profile artifacts; export depth cannot restore an absent producer stack.

## Retained memory investigation

The ThingsBoard **run-1** guard heap has a closed 51-query owner report and a
complete weighted census. The census visits all 72,923 actors, 249,487 scalar
initial memberships and 2,981,381 ordinary caller groups. These totals agree
with the independent owner queries. There are no unsupported primary groups.
The whole reachable heap is 11.373 GB across 317,630,739 Java objects; these
are process objects, not semantic facts.
The reader uses the saved MAT indexes; its 57.056-second traversal is a
diagnostic cost, not analyzer engine time.

| ThingsBoard run-1 retained owner selection | Decimal GB |
| --- | ---: |
| Ordinary caller subscriptions | 2.483 |
| Their position index | 1.772 |
| Their exact accumulator | 0.666 |
| Conclusion buckets across stores | 2.217 |
| Method statement directories and owned subtrees | 1.269 |
| Normal call/entry/exit sink caches | 1.092 |
| Forms selection | 0.702 |
| Raw and selected method summaries | 0.631 |
| Combined packed static entry/node/call/event roots | 0.249 |
| Dynamic committed request additions | 0.144 |
| Temporary request additions | 0.014 |
| Worklists, event queues and channels | 0.081 |
| Scalar initial membership stores | 0.023 |
| IR subsystem, outside optimization scope | 1.620 |

Each row is a separate retained-set union. Rows overlap and must not be added.
The index is part of caller storage. A union is an owner footprint, not a net
removable-byte estimate. The heap is captured after managed soft-reference
cleanup and further GC checks, before cancellation releases the stores.

The position index has **28,790,956 admitted handle/position postings** for
2,981,381 current groups. It is an auxiliary candidate index. Its postings
need not equal current fact leaves: the index grows from admitted deltas and
does not remove positions when a cumulative value changes. The census does
not measure stale-posting counts. This index is a major memory target even
though the AP grammar is small.
Its retained selection includes 1.152 GB of linked-map entry shells and
0.245 GB of private boxed integer objects. A replacement must include its
own arrays, capacities and ordering state before a net saving is claimed.

| Original premise in ordinary caller groups | Groups |
| --- | ---: |
| Static, concrete mark | 1,575,303 |
| Static, abstract mark | 956,848 |
| Nonstatic, concrete mark | 36,974 |
| Nonstatic, abstract mark | 140,268 |
| ZERO | 271,988 |

Static originals account for **84.932%** of ordinary groups. Their concrete
part is now larger than their abstract part. Overall, there are 1,618,222
TAINT groups, 1,097,116 FLOW groups and 266,043 REACH groups. Each group retains
a complete caller premise, call/context binding and current header; its tree
can contain several paths and marks. These are not three million distinct
AP shapes, nor a count of all analyzer facts.

Only **2,401** groups have a canonical static original and an exact same-field
DEMAND/STAR/empty-field-exclusion conclusion with a nonempty mark exclusion.
Another **383,206** contain that literal self field plus other geometry. They
cannot be discarded or replaced by a plain identity descriptor. A further
425,419 canonical-static-origin groups have another current header, and
136,580 static-origin FLOW groups currently have a nonstatic base. All
2,981,381 admitted/current header comparisons agree. Extending the existing
empty-mark-exclusion identity case alone cannot remove this whole workload.

The expanded scalar initials contain 144,295 nonzero concrete memberships in
112,644 exact geometry cohorts. Each cohort has **one to seven marks**;
99,428 have one mark. This population does not show hundreds of concrete
initial marks per exact geometry. It excludes packed-only abstract entries.
The optional current-TAINT mark-diagonal pass completes only 115,302 eligible
rows before its separate node budget; 1,496,975 are explicitly unavailable.
Every completed row carries only its original mark, but that subset cannot
establish the same property for all TAINT rows or a global mergeable quotient.

The request stores contain 30,167,538 dynamic committed mark memberships in
164,342 rows, and 2,198,282 pending memberships in 13,870 rows at capture.
Their retained unions are small beside caller storage. This separates two
issues: request admission causes substantial repeated allocation/CPU work,
while the caller position index is a major retained-memory owner. The actor
pending summary lists contain 103,676 fragments across 11,134 per-actor
distinct premises; differing result headers may prevent some merges.

Conductor and PDF guard heaps are from **run-2**, after an accepted run-1
report. They have a different retained-memory pattern:

| Retained selection, decimal GB | conductor run-2 | PDF run-2 |
| --- | ---: | ---: |
| Whole reachable heap | 7.625 | 7.606 |
| Conclusion buckets across stores | 1.917 | 4.317 |
| Method statement directories and owned subtrees | 1.299 | 3.482 |
| Ordinary caller subscriptions | 0.346 | 0.342 |
| Their position index | 0.145 | 0.120 |
| Raw and selected method summaries | 0.350 | 0.513 |
| Normal call/entry/exit sink caches | 0.321 | 0.151 |
| Forms selection | 0.209 | 0.221 |
| IR subsystem, outside optimization scope | 2.249 | 0.978 |

These owner rows also overlap. Dense statement-directory arrays themselves
occupy only 52.779 MB on conductor and 99.374 MB on PDF. Their larger retained
unions include conclusion payloads; replacing the arrays alone cannot save
those whole unions. There are 4,388,843 physical conclusion groups on
conductor and 10,006,416 on PDF. Source structure and measured kind-map
counts give unused kind-map shell lower bounds of 209.615 MB and 474.675 MB.
Two-flag arrays add 105.332 MB and 240.154 MB respectively. Replacement
objects/capacities must be included in any future net-saving claim.

Conductor has 1,056,622 ordinary caller groups and 686,798 scalar initial
memberships. PDF has 1,168,319 groups and 729,641 scalar initials. These
later-run populations do not inherit the ThingsBoard run-1 shape percentages.
The large measured run-2 target is conclusion storage, not the run-1 packed
identity lane or request marks. Both later heaps have zero committed/pending
dynamic request memberships; their empty store objects still have overhead.

All 51 query outcomes per heap are inventoried. ThingsBoard has 48 available
results and three empty selections. Each later heap has 38 available results,
ten empty selections and **three failed queries**, Q37–Q39. Those three
retained-root OQL operations have no result because the run-1-only
`Run1StaticFlow` class has zero live instances, verified independently in the
whole histogram. Their bytes remain unavailable; no automatic retry or
success label hides the failures. Other blank/null query cells also remain
unavailable. The ordinary owner measurements above have complete CSV totals.

## Evidence and next iteration

The closed study root is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/
run1-bottom-up-static-propagation-preparation/verification-20261017T105047.277343Z
```

`run-plan.json` is `SCANS_TERMINAL`. The postphase receipt is
`postphase-closure-20261017T121827.812025Z/receipt.json`, SHA256
`0dcf79ab09a91e322f661d9edc472ff80a299c871b7d6e7264fe51b7d7594e26`.
The exact commands, input endpoints, phase maps and runtime validation are
referenced there. Metrics are under `measurement-review/current-metrics-1`,
strict comparisons under `comparisons/main-current`, source triage under
`measurement-review/strict-finding-triage`, and closed profile summaries under
`measurement-review/{native-cpu-closed-1,profile-windows-closed-1}`.

The closed heap review is `measurement-review/closed-current-heaps.json`.
It binds all three terminals, source/input endpoints, outcome inventories and
the complete primary ThingsBoard census, including the capped optional
diagonal pass. Source/owner/fan-in interpretations are under
`measurement-review/{thingsboard,conductor,pdf}-owner-next-proposal`.
The primary census result is under
`heap-census-execution-v1/jobs/job-thingsboard-20261017T124700.250290Z`.

The [next proposal](../design/bidirectional-next-iteration-after-bottom-up-statics-20261017.md)
ranks caller-index, bound-rule-cache and conclusion-container work by expected
memory impact. It separately describes request-admission and cleaner work for
the run-1 time target. Those changes remain unselected and unimplemented.
IR storage remains outside scope.
