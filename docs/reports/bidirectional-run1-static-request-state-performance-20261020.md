# Run-1 static requests: fresh performance measurements

Date: 20 October 2026. Status: measurements and heap review complete.

The selected changes are implemented. The normal analysis still misses the
60-second **run-1 engine** target on all four projects. All four normal candidate
analyses end with OOM or timeout. No complete no-FN acceptance result is available.

There are two measured sources of large run-1 state. Normal thingsboard
retains millions of unresolved static continuations. With real static fields
disabled, thingsboard still OOMs in run-1 and retains large ordinary
caller-summary joins and method-edge stores. Few mark groups limit one
dimension; they do not limit complete premises, caller bindings or operation
images. Exclusion normalization also remains a CPU and allocation candidate
on both paths. No infinite loop has been established.

The [next-iteration proposal](../design/bidirectional-run1-availability-and-exclusions-next-iteration-20261020.md)
describes the three candidates and their proof and measurement gates. Those
candidates are not implemented.

## Versions, scope and checks

The closed normal cohort uses `6ad77c6b1a968d8f8c308ca05b02555ea8ad3203`.
It includes the selected enum-alias **DIRTY HACK**, three-set static request
protocol, immutable GROUP identity paths, and exact class-marker handling.
The fresh main reference is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with
only the recorded MemoryManager snapshot hook. The test checkout is
`bbdbcc5fd35d976d93348760ea60561e4670f68c`.

The permanent diagnostic flag is in
`0f7feafd88d5ca84dd1ae12ef4b8a4920c3d1b48`. It is off by default. Its on/off
experiment uses that one commit and the clean tested checkout
`/tmp/opentaint-bidi-static-toggle-6ad77` for both sides. The current branch
contains the same commit; its pre-existing user changes are kept. The normal
6ad cohort and the 0f7 diagnostic pair are separate evidence sets.

The flag removes real static-field flows from run-1. It keeps exact `S.C.$`
global markers, ordinary nonstatic field processing and later-run behavior.
An enabled result can miss findings. It is a diagnostic result, not a sound
replacement for static handling. Use the normal project command with:

```text
-Popentaint.bidi.disableRun1Statics=true
```

See [the flag contract](../../spec/run1-static-field-ablation.md).

The flag commit passed 2,151 required JVM tests: 1,644 common, 436 JVM, 38
application and 33 rule/join tests. There were no failures, errors or skips.
The full Lean build passed. The new audit checked 288 declarations and 10
executable controls, with no unexpected axioms. The first application attempt
ran no tests because the installed protoc rejected existing optional fields.
The successful gate reused the existing generated Go outputs after source and
artifact hash checks, and excluded the Go-server rebuild. No source workaround
was made. The test receipt binds the committed source to the tested files.

## Normal workloads and iterations

All workloads use `:runProjectAnalyzer` directly, Java 17, the configured rules
and approximations, and the same serial workload/reader locks. No alternate
heap or timeout configuration was used.

| Project | Heap | IFDS / outer limit, s | Run-1 engine, s | Run-1 result | Latest reached | Latest completed engine | Latest accepted forward report |
| --- | --- | --- | ---: | --- | --- | --- | --- |
| OWASP | 8g | 1000 / 1200 | 885.428 | COMPLETE | run-2, TIMEOUT | run-1 | run-1 |
| conductor | 8g | 1080 / 1200 | 1054.743 | TIMEOUT | run-1, TIMEOUT | none | none |
| thingsboard | 12g | 1080 / 1200 | 349.935 | OOM | run-1, OOM | none | none |
| Stirling-PDF | 8g | 1080 / 1200 | 248.120 | COMPLETE | run-2, OOM | run-1 | run-1 |

OWASP run-2 uses field limit 1 and ends after 75.614 engine seconds.
Stirling-PDF run-2 also uses field limit 1 and ends after 358.964 engine
seconds. Run-1 uses field limit 1. These are the logged engine times; they
exclude prescan and the report barrier. On an OOM path, engine time includes
the diagnostic live-heap hook: 27.309 seconds on thingsboard run-1 and
18.380 seconds on Stirling-PDF run-2. These failed engine durations are not
completion times or an uninstrumented runtime measurement.

| Project | Main outcome | Legacy full-scan phase, s | Main invocation, s | Candidate invocation, s |
| --- | --- | ---: | ---: | ---: |
| OWASP | OK | 43.209 | 118.218 | 1044.591 |
| conductor | OK | 24.127 | 93.175 | 1119.702 |
| thingsboard | OK | 30.085 | 223.493 | 521.819 |
| Stirling-PDF | OK | 4.884 | 49.120 | 663.135 |

Main has no numbered bidirectional iteration. Its full-scan phase is a
legacy analysis phase, not a bidirectional run-1. Invocation time includes
startup and other phases. The table shows practical outcomes; it does not
measure the speedup of the three selected changes against the same engine
without those changes. Older candidate reports are not measurement authority
for this report.

## Finding comparison

The strict identity uses the rule, sink fingerprint and full method signature.
Raw locations are kept. The comparator does not merge different return sites.

| Project | Main identities | Main identities present | Missing identities | Added identities | Candidate occurrences |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4112 | 4112 | 0 | 10326 | 47599 |
| conductor | 7 | 0 | 7 | 0 | 0 |
| thingsboard | 14 | 0 | 14 | 0 | 0 |
| Stirling-PDF | 17 | 15 | 2 | 1639 | 61820 |

Every comparison is partial. Conductor and thingsboard do not reach the
complete-forward report barrier. Their empty exports are a resource-failure
output gap. They do not establish an individual transfer error or its absence.
OWASP and Stirling-PDF export accepted run-1 DEMAND candidates, not a converged
confirmed result. Added candidates are not all proved false positives.

The two missing Stirling-PDF identities are XSS method-exit locations:

| Method | Main return | Candidate fallback return |
| --- | --- | --- |
| `PipelineController.handleData` | lines 90–91, instruction 50, `return %104` | line 130, instruction 109, `return null` |
| `ConvertEmlToPDF.convertEmlToPdf` | lines 133–134, instruction 103, `return %54` | lines 148–149, instruction 126, `return %89` |

Each rule/full-method pair has 74 candidate occurrences at the fallback
return, with no trace. `JIRBidiAnalysis` exports Simple results. With no trace,
the SARIF boundary resolver selects the last return. This explains the changed
raw location. It does not prove that either main source-to-sink path is
preserved. Both identity checks remain failed. Stirling-PDF also has 525
invalid-location export errors; neither missing method occurs in those errors.

## Where run-1 spends work

| Project | Run-1 JFR Java samples | `normalized` leaf | `projected` leaf | Estimated sampled allocation weight |
| --- | ---: | ---: | ---: | ---: |
| OWASP | 48398 | 19.92% | 19.47% | 1.692 TB |
| conductor | 38159 | 24.25% | 23.43% | 1.772 TB |
| thingsboard | 16879 | 1.11% | 1.39% | 439.184 GB |
| Stirling-PDF | unavailable | unavailable | unavailable | unavailable |

The first three retained recordings cover their nominal run-1 windows.
Stirling-PDF's rolling JFR starts at 20:21:40.736, after run-1 ended at
20:21:26.281. Its retained JFR describes run-2; it cannot supply run-1 totals.
Its separate native normal profile still covers a nominal run-1 window.
Allocation weights estimate allocation churn. They are not retained heap bytes.
Java execution samples are not wall time or total process CPU seconds.

The selected GROUP fastpath returns an unchanged value, but its qualification
still scans all atoms and calls synchronized mark-table lookups. GROUP-only
add/union/intersection can still rebuild group lists, sets and arrays even
when raw set union returned the unchanged operand. Global EMPTY has no catalog
and takes the old empty projection fallback. These are bounded operations.
They remain expensive when repeated at fact-output, transfer and cleaner
boundaries. Rule-provider condition projection is a different path and does
not remove these runtime-header operations.

The retained native stacks narrow this path further. In the normal window,
52.97% of conductor samples and 72.47% of thingsboard samples contain
`MarkExclusions.normalized` or `projected`. The nearest external callers are
`ApManager.runtimeFact`, `FlowClean.run` and `MarkGate.flowMarks`. These are
ancestry counts, including work in mark-table locking, not exclusive Java
self time. The whole-engine JFR leaf percentages and these window-specific
native counts have different denominators. Native run labels use the nominal
requested capture overlap. Collapsed stacks have no timestamps, and these
receipts do not bound attach delay. They cannot be filtered to an exact phase.

Thingsboard's normal native window has 30.81% `ObjectMonitor::TrySpin` leaf
samples and 6.59% `SpinPause`. In its run-1 JFR, MarkTable owns 403.17 of 408.80
recorded monitor-blocked thread seconds. These durations sum across threads;
they are not 403 seconds of wall time. Conductor's near-limit window nominally
falls in run-1 and has 78.32% GC-category native samples. OWASP's near-limit
window nominally falls in run-2, so it must not be used as run-1 evidence.
The normal native GC shares
are 54.75% on OWASP, 13.18% on conductor, 13.03% on thingsboard and 50.19% on
Stirling-PDF. Do not add these percentages to the Java leaf percentages.
The same normal native profiles include `JfrStackTrace.record_safe` at
8.10% on conductor and 6.03% on thingsboard. The measurements include
profiling overhead; no uninstrumented speedup is inferred from those shares.

Thingsboard's final 30 seconds contain heap-dump/GC activity and only 22 Java
execution samples. They are not a representative steady-state CPU window.
Its whole run-1 Java leaf leaders also include the JIR graph constructor
(7.80%) and static-service intersection (3.15%). A form-scope wrapper includes
callback time; it is not a measurement of form rebuilding alone. No IR storage
change is proposed.

GC data confirms real heap pressure. All 19 thingsboard guard-high or
back-to-normal markers have a nearest after-GC checkpoint with the full
12 GiB committed. The nearest checkpoint to its run-1 snapshot reports
11,624,783,416 used bytes. All four conductor guard markers likewise have
the full 8 GiB committed. These are timestamp correlations, not identical
GC/snapshot states. The transition guard uses actual post-GC HEAP-pool usage;
its final confirmation, periodic logs and snapshot logs use
`maxMemory - freeMemory`. That periodic metric does not establish a false OOM. Retained
ownership still requires the heap analysis below.

## Logical requests and retained continuations

| Terminal run-1 counter | thingsboard | conductor |
| --- | ---: | ---: |
| Supplied group memberships | 69 | 373 |
| Requested, not written field/group pairs | 1610 | 2360 |
| Written, not requested field/group pairs | 8 | 69 |
| Requested and written field/group pairs | 45 | 295 |
| Pending service continuation rows | 5515013 | 2395566 |
| Pending continuation/group memberships | 9518210 | 3139662 |
| Static-origin FLOW writer rows | 127729 | 31007 |
| Nonstatic-origin FLOW writer rows | 3305 | 4555 |

The field/group sets are small compared with the consumer continuation store.
Groups merge within a continuation. A different `(owner, continuation ID)`
still retains another pending row. The counts above are terminal counters,
not exact snapshot object counts. Pending totals include special exact-class
interests; do not divide them by the normal-field request count as a measured
per-field average.

OWASP's completed run-1 has only 13,897 pending static subscriptions /
25,245 pending memberships. Stirling-PDF's completed run-1 has 51,710 /
92,152. This static wait population is much smaller on those projects.
Do not generalize thingsboard's heap ownership to all four workloads.

Real static-field current values do not relay through the ordinary call
chain. Exact `S.C.$` global markers keep their separate current path. But
`S.C.f/FLOW -> local/argument/return FLOW` dependencies still propagate.
They retain the original static field. Method, original field, current AP,
statement/call stage and caller binding form a product. Few mark groups do
not make this product small. Ordinary fact storage deduplicates its own
edges; that does not merge the separate point/call/writer wait payloads.

Conductor also offers 37,979,557 ordinary request members. Existing temporary
and authoritative stores reject 37,830,387 duplicates; only 149,170 members
are fresh. Temporary duplicates account for 95.93% of all offers. Peak
temporary storage is only 296 rows / 4,780 members. This is repeated admission
work, not evidence that temporary requests fail to deduplicate. It is a
separate path from the millions of pending static continuations. Its sampled
cost must justify any priority over the larger measured hotspots.

There is no demonstrated infinite loop in these hot methods. OWASP and
Stirling-PDF complete run-1. Conductor's timeout alone proves neither an
infinite loop nor termination for that input. The observed fanout, repeated
bounded work, locking and GC give concrete sources of cost. Soft form rebuilds
also reuse the same analysis-wide form owner and structural operation IDs;
the inspected rebuild path does not invent fresh owner identities.
Conductor's final four periodic run-1 observations still advance from
1,818,217 to 1,819,947 handled events, with five in flight. These are
scheduler events, not fresh facts. They show continuing dispatch but cannot
exclude a rescheduling cycle. Stirling-PDF also has a small active tail and
then completes run-1. Neither a timeout nor event progress alone proves
termination; the measured continuation retention and GC remain concrete costs.

### Why the initial grammar does not bound the work tightly

Simple initial paths and 42 groups do not bound method owners, statements,
current AP images, caller stages, exclusion variants or complete conjunctive
premises. GROUP initials G and H are separate premise keys and traversals.
TAINT conclusion leaves already merge output marks; the missing sharing is
not a one-mark limit on every conclusion. Any change across original
premises must retain their input/output correlation.

The field-disabled run-1 also reports:

| Cumulative ordinary-work counter | Count |
| --- | ---: |
| Initial candidates / new initials | 7497820 / 277237 |
| Caller-input exact-key groups created | 2194810 |
| Accepted caller-input deltas | 4694578 |
| E6 multi-premise member bindings | 8452002 |
| E6 complete member combinations | 8148071 |

E6 is entered for a multi-premise summary. It retains member bindings and
completed tuples, then applies growing conclusions to those tuples. Its
counters omit those later repeated conclusion applications. Exact global
markers can participate; these are not exclusively nonstatic-base joins.
The counters do not give live heap populations or prove redundant tuples.
Current directory counts printed as zero after cleanup also do not describe
the earlier live heap. The diagnostic heap below separates these owners.

## OOM heaps and diagnostic comparison

The normal `6ad77c6b1` thingsboard dump is from run-1, FORWARD, field limit 1. Its complete
reachable histogram has 11,412,753,800 shallow bytes. The static-service and
point-resume retained union has 4,786,381,496 bytes in 169,266,907 physical
objects. The method-edge retained union has 246,615,680 bytes. The mark
retained union has 459,472 bytes and the interner has 42 groups. Group atoms
themselves do not explain this OOM.

| Thingsboard run-1 actor directory | Complete snapshot population |
| --- | ---: |
| Operation points | 5463405 |
| Writer input waits | 413752 |
| Call continuations | 22842 |
| Direct inputs | 1780 |
| Conditional joins / join inputs | 2522 / 2522 |
| FLOW writer keys | 154011 |
| Read anchors | 78735 |

These sums cover 70,693 reachable point owners. They are not a join to the
static service's committed owner/ID keys. The service separately has
5,513,340 normal-field pending rows across 64 field states and 1,673 class
interests. The point population is the largest measured actor wait family.
Ordinary summary request stores have 19,405 rows / 153,637 memberships;
those stores are not the millions of static pending points.

The full histogram has exactly 5,463,405 each of `PointRow`, `PointKey` and
`PointContinuation`. The static/point retained union also contains that many
exclusively retained `FlowTree` wrappers. Their respective shallow sizes are
131,121,720, 174,828,960, 218,536,200 and 218,536,200 bytes. These are physical
object shells; the union also includes nested maps, paths and selectors.
They are not four independent counts of semantic facts. Two field-state IDs
alone have 2,557,663 and 2,361,858 pending rows. The frozen query packet does
not select their field names, so named-field attribution is unavailable.
Each of those field states has 34 requested-but-unwritten groups and one
processed group. Releasing answered requests cannot remove these unanswered
memberships. The missing groups can still arrive later; the failure snapshot
does not prove permanent absence. Unanswered waits do not hold work
reservations, but their retained payloads still force heap pressure. This
store is not itself evidence of an infinite queued-work loop.

The selected algorithm still constructs a continuation for each distinct
FLOW image and operation/call state while its group is absent. Ordinary
method storage can share packed current values, but those continuation keys
retain individual images and selectors. This creates a second large store
beside the underlying relations. Reducing the mark dimension alone does not
remove it. The next proposal targets this algorithmic retention, ordinary
completed join effects, and the independently measured exclusion CPU/allocation work.

All retained unions above can overlap and must not be added. The frozen
22-query packet completed without a query execution error: 17 complete CSV
results, two top-dominator displays, one bounded pending-row sample, one
result with a blank phase-status cell, and one empty current-demand query.
The last two are unavailable, not zero. The sample does not establish total
pending memberships. Snapshot phase comes from the bound runtime log.

Stirling-PDF's dump is from run-2, BACKWARD, field limit 1. Its complete
reachable histogram has 236,349,884 objects / 7,571,742,352 shallow bytes.
The full histogram has no `Run1StaticService`, `Run1PointResume` or
`PointContinuation` instance. The run-1 wait graph is not retained here.

| Stirling-PDF run-2 owner union | Retained-set shallow bytes |
| --- | ---: |
| Method-edge stores | 2524034256 |
| Run summary owners | 701637344 |
| Caller/publication/ordinary request owners | 799688360 |
| Demand and current queries | 82885504 |
| Marks | 444792 |

These unions overlap. The complete method-directory query has 7,258 stores
and 1,138,023 premise-directory entries for non-REACH conclusions. This map
can include ZERO-premised TAINT results; it is not a non-ZERO count.
This is a different live analyzer
population from thingsboard's run-1 waits. It is not an IR storage proposal
or proof that one repeated pattern explains every premise. The run-1
availability candidate will not, by itself, remove this run-2 population.
The next iteration keeps the run-1 target first; later-run premise growth
needs its own source and workload investigation.
The method-edge retained set contains 5,765,635 `ConclusionGroup` objects,
6,193,450 `TaintTree` wrappers and 1,138,023 conclusion-group arrays. These
are physical representation populations. They do not prove the same number
of distinct logical facts or unnecessary propagations.

The same 22-query packet has 12 complete CSV results, two top displays,
one result with a blank status cell, five no-result outputs, and two empty
selections. No query execution failed. The unavailable static-owner scalar
queries supply no invented zero sums; the separate full histogram establishes
absence of the three classes above. Both MAT attempts exited 0, preserved
their original dumps and closed their reader processes.

### Same-commit static-field diagnostic

Both leaves use `0f7feafd8`, the same tested clean checkout and the fixed
thingsboard 12g / 1080-second IFDS / 1200-second outer settings. The runner
stops a completed run-1 after its engine marker. It records any run-2 race
and accepted-forward observation; it does not turn a diagnostic stop into
a successful full analysis.

| `disableRun1Statics` | Run-1 engine, s | Result | Latest reached | Latest completed | Latest accepted observation |
| --- | ---: | --- | --- | --- | --- |
| false | 304.550 | OOM | run-1 | none | none |
| true | 664.799 | OOM | run-1 | none | none |

The control's failed duration includes its 27.785-second live-heap hook.
The 18,413,820,935-byte dump is preserved. Its terminal counters have
5,624,846 static subscriptions and 8,310,678 pending memberships. These
counters corroborate the large continuation population on F. Exact retained
owner measurements above come from the normal 6ad heap. The matching F
measurements are below.

The enabled leaf also ends OOM. Its 18,727,803,961-byte dump includes a
30.213-second live-heap hook in the failed engine duration. Terminal
`readDescriptors`, `pointSubscriptions`, both writer-row counts, candidate
visits, normal-field `req/reach/processed`, pending memberships and writer
admissions are all zero. The remaining 65 write/supply regions and group
memberships, plus 388,531 owner replies, concern the preserved exact global
marker route. They are not evidence that real field handling remained active.

Both leaves are closed and source/input endpoint equality passed. Neither
completed run-1 or produced an accepted forward report. The two failed
durations do not establish a speedup or a completion-time comparison. The
experiment shows that real-field handling is not the only source of run-1
resource failure.

The enabled-control heap has 371,064,043 reachable objects /
11,445,873,440 shallow bytes. Its actor-directory census covers 69,563
owners and contains 5,566,789 points, 798,593 writer inputs, 47,222 calls,
2,019 direct inputs and 608 join inputs. Its service has 5,622,934 normal-field
pending rows plus 1,912 class interests. Two fresh field-state IDs hold
2,746,280 and 2,100,743 pending rows; each has 34 requested-but-unwritten
groups and one processed group. Field names are unavailable. The complete
histogram also has 13,034,294 `GroupSet` wrappers despite only 42 interned
groups. These are physical immutable-set wrappers, not that many group IDs.
The static continuation problem remains on F.

The disabled leaf's closed heap has 346,986,536 reachable objects /
11,460,838,896 shallow bytes. Its complete actor-directory census covers
73,669 owners. Points, direct inputs, call continuations, writers and writer
inputs are all zero; 3,578 read anchors remain as metadata. This complete
directory query establishes zero point and call rows. The physical histogram
records zero `CallContinuation` and static-service `InterestRow` instances;
it has no `PointContinuation` row. No absent scalar-query result is used as
a measured zero.

| Same-F owners, active run-1 | Flag false: retained-set shallow bytes | Flag true: retained-set shallow bytes |
| --- | ---: | ---: |
| Static service and point owners | 4875984808 | 58746112 |
| Ordinary predicates and conjunctions | 15403752 | 1934496744 |
| Method-edge owners | 215383776 | 1647320184 |
| Caller/publication/ordinary request owners | 365425120 | 1111817432 |
| Run summary owners | 180981696 | 546334072 |
| Work queue owners | 42301560 | 36381672 |
| Mark owners | 459472 | 459472 |

These unions overlap. In the disabled heap, the largest ordinary unions are
retained analysis state, not a large queue of unprocessed static
continuations. Its whole heap has only 4,501 `EdgeDelta` objects. This snapshot does not exclude
repeated processing over time, but a large queued-delta backlog does not
explain its retained population.

The predicate/conjunction union retains 8,217,737 `NdBinding` objects,
8,407,378 unmodifiable-list wrappers and 8,810,709 linked-map entries. Those
three shell populations use 197,225,688, 201,777,072 and 352,428,360 shallow
bytes respectively; nested arrays and other state use more. Source inspection
shows that E6 keeps matched member sets and every completed ordered tuple.
Later conclusion growth revisits those tuples. Different tuples can fold
to the same complete caller premise, layer and resume context, but this
packet does not count such equal effects. The whole 1.93 GB union is not a
measured saving from effect deduplication. It also contains local predicate
state, member sets and conclusions that that candidate keeps.

Method-edge owners retain 742,611 per-premise conclusion arrays, 2,177,589
`ConclusionGroup` objects and 3,665,328 `TaintTree` wrappers. The premise
directory counts non-REACH conclusions, including any ZERO-premised TAINT;
it is not a count of distinct initial atoms. The whole heap has only 320
`InitialAp` objects, but 474,536 `PremiseSet` objects and the same number of
`InitialAp[]` arrays. There are 81,775 `NdSummaryJoin` / `NdKey` objects.
Shared atoms can appear in many complete premises and per-owner stores.
Caller/publication/request
owners retain 2,194,809 exact-key accumulator entries and 961,396 caller
references. TAINT leaves already pack marks. The multiplication is across
premises, call contexts, current fact headers and sites.

Only 438 `MarkExclusions` wrappers and 131 `GroupSet` wrappers are live in
the whole disabled heap. Their retained shells do not explain the OOM.
This does not contradict their CPU profile: temporary normalization work
and catalog locking can be expensive without retaining many wrappers.

The disabled heap's 22 queries have 15 complete CSV results, two bounded top
displays, one blank status, three no-result outputs and one empty selection.
There are no query execution or result-cap failures. Blank, absent and empty
results remain unavailable. The control has 17 complete CSV results, two
top displays, one bounded 80-row sample, one blank status and one empty
selection. Its sample supplies no membership total. Neither job has a query
execution or result-cap failure. The bound logs establish both snapshot
phases. Both actual MAT readers are closed and the three locks are released.
The two OOMs occur at different progress points; their retained populations
do not compare identical completed semantic frontiers. Removing static
effects changes later demands and scheduled work. The table establishes
two retained-state problems, not a causal byte-for-byte transfer from one
store to another, a semantic subset relation or a completion-time speedup.
It also does not prove that normal analysis will reach the same ordinary
join population as the disabled experiment.

The closed normal native windows also retain the same exclusion hot path:

| `disableRun1Statics` | Samples | Exclusion ancestry | `TrySpin` leaf | GC ancestry |
| --- | ---: | ---: | ---: | ---: |
| false | 24782 | 64.35% | 27.28% | 18.73% |
| true | 24880 | 66.05% | 28.55% | 10.33% |

With real fields disabled, nearest external callers of exclusion work are
`runtimeFact` (7,600 samples), `MarkGate.flowMarks` (5,864) and `FlowClean.run`
(2,938). These are normal-window ancestry counts, including lock descendants,
not exclusive time or whole-engine CPU totals. The columns overlap. The
same nominal-window and attach-delay limits apply. This independently
supports optimizing immutable runtime exclusions; the cost remains when
the real-field path is disabled. Neither pair JFR has been read for this
comparison, so no pair-wide JFR coverage or allocation total is claimed.

## Evidence

The external study is
`/drive-testcomp/opentaint-go-rules/run1-static-requests-20261020`.
Its normal source/input endpoints close with equality for ten datasets and
sixteen controls. All eight normal leaves and their analyzer/profiler
processes are closed. The strict comparison preserves raw finding identities.

- `validation/benchmark-guard/equality.json`, `main-reference/equality.json`
  and `validation/benchmark-guard/main-candidate-bridge.json` bind the normal
  source/input cohort and the fresh main reference.
- `validation/final-finding-comparison/summary.json` and
  `validation/finding-evidence-note.{md,json}` retain the strict comparison
  and the two return-site investigations.
- `validation/static-field-ablation/complete-gate-receipt.json` and
  `tested-0f7feafd.json` bind the flag checks to the commit.
- `validation/profile-analysis/sweep-receipt.json`, each `analysis.json`,
  and `validation/native-exclusion-callers.json` retain profile counts,
  stack/capture hashes, phase windows and actual retained coverage.
- `validation/run1-event-progress-6ad77.json` binds the periodic run-1 event
  observations to their source logs. It does not count fresh semantic facts.
- `validation/thingsboard-heap-first-pass-6ad77.{md,json}` and
  `validation/Stirling-PDF-heap-first-pass-6ad77.{md,json}` bind the two
  normal OOM snapshots, actual MAT commands, complete query outcomes and
  retained-set tables. The 80-row pending sample supplies no total.
- `validation/heap-tools/first-pass-reader-closure-6ad77.json` closes both
  actual MAT jobs, observed reader identities and all three reader locks.
- `validation/static-ablation-run1-0f7feafd/{equality,thingsboard-pair}.json`
  closes the same-F pair. `validation/static-ablation-findings-0f7feafd/analysis.json`
  binds actual flag values, failed engine times, native captures, hashes,
  counters and independent process closure. Its earlier provisional native
  analysis is preserved separately; final equality is now available.
- `validation/thingsboard-F-diagnostic-{OFF,ON}-heap-first-pass.{md,json}`
  binds both actual dumps, all 22 query outcomes per leaf, complete owner
  unions, physical class populations and closed MAT process identities.
  `validation/heap-tools-F` is the separate frozen F reader packet. The
  original normal-cohort packet and its jobs remain unchanged.
  `validation/heap-tools-F/first-pass-reader-closure-F.json` independently
  closes both actual reader identities and all three locks. Neither job
  used an added query or retry.
  `validation/thingsboard-F-diagnostic-heap-comparison.{md,json}` keeps the
  final same-F owner comparison and its different-progress limitations.
- `validation/nonstatic-premise-and-queue-source-analysis-0f7feafd.md`
  pins complete-premise partitions, packed TAINT output, FLOW header
  replacement, old queued snapshots and exact-header caller inputs. These
  mechanisms are distinct from a measured owner-byte partition.
- `validation/ordinary-e6-join-source-analysis-0f7feafd.{md,json}` binds
  E6 counters and the exact completed-effect signature to 16 source files.
  It proves no measured effect-collision factor or universal memory saving.
- `validation/gc-heap-analysis-v2/sweep-receipt.json` and
  `validation/reader-sweeps-closure-6ad77-v2.json` close the GC readers.
  The first GC parser attempt failed on an oversized unrelated frontier
  line. It remains preserved. V2 drains only unrelated oversized lines in
  bounded chunks, keeps genuine memory-line rejection and clock/line-number
  behavior, and passed 34 pure controls before the explicit new sweep.
- Original JFR/native captures and complete OOM heaps remain available.
  Expanded successful JFR JSON exports were removed only after count/hash
  validation; failed exports were kept. Previous-study raw cleanup has its
  own receipt in `preparation/old-raw-cleanup.json`.
