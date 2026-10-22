# Local static writers and summary aliases: performance

Date: 22 October 2026. Status: **closed measurements and reviews; project gates fail**.
The implementation is committed in
`a4bf789866f912ebefc54bca42d809c89beea2d4`.
The fresh main reference is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
The [validation report](bidirectional-local-static-writers-aliases-validation-20261022.md)
records the module gates and existing precision failures. The
[specification](../../spec/run1-static-writers-and-summary-aliases.md)
defines the selected behavior and proof limits.

All four candidate commands end with OOM or timeout; all four fresh main
commands succeed. Conductor and PDF meet the 60-second run-1 engine
target. OWASP misses it and Thingsboard fails before its first report.
The strict location gate fails on Thingsboard and PDF. Thus this campaign
does not establish an overall completed-analysis improvement or finding
parity. Thingsboard has thousands of writers but hundreds of thousands
of pending consumers. The remaining costs depend on the phase.

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-local-writers-aliases-20261022`.
Paths below are relative to that root unless stated otherwise. Previous
reports are context, not current measurement evidence.

## Change and source authority

Only completed local transfers register static write points. Call summary
application retains and publishes the caller summary without registering
another writer. Computed ZERO and all-static group supplies remain active.
A local FLOW writer owns its immutable bounded scalar relation.

Run-1 summary alias admission uses the raw callee premise and result.
Equal base/path FLOW needs two STAR field tails. Equal base/path TAINT
needs the same actual mark and ignores field-tail kind. DEMAND does not
bypass this test. Packed output and delayed replies keep each producing
arm's classification. Later FULL/native/reversed rules remain unchanged.

The alias change replaces the old candidate admission rule. It is not an
exact-equality claim for the old DEMAND alias closure. There is no new
field, mark, rule, run-limit, reference-manager or IR policy.

`implementation-receipt.json` and
`validation/frozen-implementation-source.json` pin the implementation and
all 23 changed code/test files. `preparation/combined-launch.json` verifies
their equality before the combined cohort starts. Later specification
clarifications change documentation only. The final check passes all
342 source/input/control comparisons in
`validation/final-source-input-check.json`. It retains the stated limit
on large model/dependency-tree coverage.

Main is the observed remote main, not the stale local `main` branch. Its
only runtime patch is the established opt-in MemoryManager snapshot hook.
The binary patch SHA-256 is
`53f0abeeb0c98a16db86b8236b91899020fca14e799ff69a6739addf644b402f`.
`preparation/main-input-control-review.json` records the model YAML,
small rule/extension files, source revisions and actual JVM launch checks.
It does not claim a second byte census of all large model storage trees.

## Fixed workload

All scans use Gradle `runProjectAnalyzer` directly and the configured
models. Both sides use `/usr/lib/jvm/java-17-openjdk-amd64/bin/java`.
Conductor uses its required extension rules, passthrough and dataflow
approximations. Static handling and aliases are enabled.

| Project | Heap | IFDS timeout | Outer timeout |
| --- | ---: | ---: | ---: |
| OWASP | 8g | 1000 s | 1200 s |
| conductor | 8g | 1080 s | 1200 s |
| thingsboard | 12g | 1080 s | 1200 s |
| Stirling-PDF | 8g | 1080 s | 1200 s |

The main cohort and the combined cohort each run projects in sequence.
No task-owned heavy heap reader or test suite runs with the combined
cohort. The host is shared with unrelated work. These are single runs,
not isolated replicated speed measurements.

The observer captures continuous JFR with a **256 MiB rolling cap** and
30-second native CPU windows after one minute of full analysis and near
the IFDS limit when analysis is still active. A rolling recording can lose
an early iteration. Phase allocation estimates must state that coverage;
the native normal window remains a separate artifact. The observer cost
is not isolated. The MemoryManager hook captures a live heap before OOM
cancellation. Capture time stays inside the reported engine time and
must be identified separately.

Before this campaign, 66 prior raw capture/index files were removed,
freeing **103,325,411,016 bytes**. Compact evidence remains. The inventory
and deletion receipt are in `preparation/previous-capture-cleanup-*`.
Current captures remain available for analysis.

## Latest iteration and command outcome

Main has no numbered bidirectional iterations. The run-1 target is
**60 engine seconds**. Prescan and report barriers are separate.

| Project | Latest reached | Latest COMPLETE | Accepted forward report | Candidate state |
| --- | --- | --- | --- | --- |
| OWASP | run-8 | run-7 | run-7 | TIMEOUT; command and SARIF closed |
| conductor | run-2 | run-1 | run-1 | OOM; command and SARIF closed |
| thingsboard | run-1 | none | none | OOM; command and SARIF closed |
| Stirling-PDF | run-2 | run-1 | run-1 | OOM; command and SARIF closed |

| Project | Main state | Main command, s | Candidate command, s | Main peak RSS, GiB | Candidate peak RSS, GiB |
| --- | --- | ---: | ---: | ---: | ---: |
| OWASP | OK | 120.205 | 1040.674 | 6.715 | 9.600 |
| conductor | OK | 97.204 | 172.333 | 9.208 | 9.489 |
| thingsboard | OK | 245.409 | 451.857 | 13.362 | 13.921 |
| Stirling-PDF | OK | 49.122 | 432.995 | 9.031 | 9.943 |

| Project | Iteration | Direction / limit | Engine, s | State | Barrier, s |
| --- | --- | --- | ---: | --- | ---: |
| OWASP | run-1 | forward / 1 | 172.096 | COMPLETE | 4.894 |
| OWASP | run-2 | backward / 1 | 210.690 | COMPLETE | 0.910 |
| OWASP | run-3 | forward / 2 | 58.779 | COMPLETE | 2.327 |
| OWASP | run-4 | backward / 3 | 170.342 | COMPLETE | 0.744 |
| OWASP | run-5 | forward / 4 | 55.192 | COMPLETE | 2.200 |
| OWASP | run-6 | backward / 5 | 191.495 | COMPLETE | 1.115 |
| OWASP | run-7 | forward / 6 | 57.459 | COMPLETE | 2.293 |
| OWASP | run-8 | backward / 7 | 34.720 | TIMEOUT | not run |
| conductor | run-1 | forward / 1 | 18.134 | COMPLETE | 0.743 |
| conductor | run-2 | backward / 1 | 80.176 | OOM | not run |
| thingsboard | run-1 | forward / 1 | 260.838 | OOM | not run |
| Stirling-PDF | run-1 | forward / 1 | 25.089 | COMPLETE | 1.305 |
| Stirling-PDF | run-2 | backward / 1 | 352.304 | OOM | not run |

OWASP still misses the run-1 target. Its command uses the full IFDS
budget and exports its accepted run-7 report. A shorter failed command
would not establish a completed-analysis improvement. Main's legacy full
scan is not a run-1 engine comparison.

## Finding comparison

The closed OWASP pair preserves all **4,112 unique reference sink
identities**, with **zero removed and zero added unique identities**.
Their raw span sets also agree. The candidate has 9,179 occurrences at
these identities, against 4,112 in main. Legacy storage merges by
`(ruleId, statement)`; the bidi report retains complete rule objects and
exports each retained report entry. The 5,067 extra occurrences are not
5,067 new false-positive locations or one copy per completed iteration.
All candidate entries are DEMAND after the run-8 timeout. Location
inclusion passes; complete-analysis acceptance does not.
The CI exact trace-count assertion would also fail: 9,179 is not 4,112.
Unique location parity does not make the candidate CI gate green.

All four pair reviews are closed. They compare full rule IDs, physical
paths and spans, and logical sink coordinates, with raw and
model-normalized keys kept separate. Every missing reference identity and
representative added families have a source review. Location inclusion
and complete-analysis acceptance are separate gates after OOM or timeout.

| Closed pair | Main unique | Candidate unique | Main included | Missing | Added |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 4,112 | 4,112 | 0 | 0 |
| conductor | 7 | 1,260 | 7 | 0 | 1,253 |
| thingsboard | 14 | 0 | 0 | 14 | 0 |
| Stirling-PDF | 17 | 1,593 | 15 | 2 | 1,578 |

Conductor preserves all seven main identities and their raw location sets.
Its 1,253 additions include 986 XSS method-exit-shaped locations and 267
other locations. The XSS family has 7,888 occurrences: 813 return
locations, 57 throws and 116 gotos. It has no ordinary XSS call site.
Other families include 242 SQLi locations, 13 filesystem, eight SSRF,
two GraalJS, one path and one reflection location. These are added
candidates, not individually proven false positives.

One reviewed XSS example is the unannotated helper
`AIModelProvider.getModel(LLMWorkerInput)`: its return at line 71 and
throw at line 69 each acquire eight XSS occurrences. The known
method-exit eligibility defect is a concrete source-level suspect for
this family. The Simple export lacks the premise/configuration evidence
needed to attribute every added candidate to that defect. Restore the
provider's eligibility as a separate explicit precision proposal; do not
silently change it in the selected optimization.

Conductor attempts 8,292 Simple/DEMAND reports and exports 8,171.
The 121 invalid-location rejections include 120 XSS method-exit sinks and
one SQLi method sink at virtual NATS `createDispatcher`. None removes a
main reference identity. Keep these rejected candidates in the evidence;
they do not establish that export is universally complete.

`validation/combined-finding-review.md` and its compact per-project JSON
bind the strict comparisons and source review. A comparator exit code of
zero means that it wrote evidence, not that an incomplete analysis passed.

All 14 missing Thingsboard identities have a source-file/line review.
Its run-1 OOM exits before the first `report.add`; the fresh report builder
remains empty. This establishes the report-admission/resource cause for
the missing output. It does not prove which internal flow witnesses were
present, or rule out a separate semantic omission in an unfinished run.

The two PDF omissions are strict earlier-return coordinates:

- `ConvertEmlToPDF.java`: main return instruction 103 at lines 133–134;
  candidate same-rule/full-method return 126 at lines 148–149.
- `PipelineController.java`: main return instruction 50 at lines 90–91;
  candidate same-rule/full-method return 109 at line 130, plus a goto
  at line 67.

The fresh main traces end at the earlier returns. The candidate uses
pathless Simple method-exit reports; source resolution falls back to the
last return. This explains the coordinate limitation. Keep all strict
keys distinct: it does not prove equivalent source-to-sink paths or
justify remapping the omitted sites. The license-check identity is
retained in this fresh pair.

PDF's 1,578 added locations include 809 XSS, 725 redirect, 32 path,
seven SQLi, two command, two SSRF and one URL location. It exports
60,737 occurrences and rejects 525 invalid locations: 518 XSS exit
entries and seven redirect exit entries. The additions are DEMAND
candidates, not individually proven false positives. Across all projects,
4,134 of 4,150 reference identities remain and 2,831 unique candidates
are added. The no-loss reported-location gate fails. The result does not
establish the requested small false-positive set or full-analysis parity.

## Run-1 workload evidence

OWASP completes and accepts run-1, then reaches run-8 TIMEOUT. At its
run-1 boundary the static service has **zero static-origin FLOW writer
rows**, **75 other FLOW writer rows**, three write regions, one supplied
region and 15 supplied groups. It has 6,293 pending consumer memberships
and 16,120 owner replies. Static writer population is small on this input.

The 30-second native normal window falls in run-1. It has 9,280 samples;
the `GangWorker` stack family accounts for 59.87% of them. GC dominates
that capture. This is evidence of allocation/collection cost, not a
Java-only execution-sample share or an allocation byte census. The
closed phase/profile review below gives the separate allocation evidence.

Conductor completes and accepts run-1, then reaches run-2 OOM. Run-1
has 919 static-origin and 46 other FLOW writer rows, against 2,451 and
107 in the writer-only control. Write regions fall from 503 to 83;
writer/group admissions fall from 47,137 to 15,978. The alias change
removes work before storage admission. These structural differences do
not depend on a timing ratio. Combined run-1 takes 18.134 seconds, against
50.198 in the control; shared-host/test overlap prevents an isolated
speedup claim. Its later OOM is active backward state, as the heap review
below shows.

Thingsboard reaches run-1 OOM without a complete engine or accepted report.
The static service has 5,040 static-origin and 406 other FLOW writer rows,
152 write regions, and 649 supplied groups. It has 268,523 pending
consumer rows, 744,922 pending memberships, 177,930 writer/group
admissions and **19,894,416 owner replies**. Only 58,875 read/write
candidate visits are recorded. The large reply count is not the writer
population, and cannot be inferred from a small set of groups alone.
Source bounds show that at least 19,657,611 replies, or **98.81%**, are
`PointReady`: `ReadReady` is bounded by 58,875 candidate visits, and
`WriterInterest` events are bounded by 177,930 writer/group admissions.
`PointReady` includes calls, writer inputs, fixed-group joins, exact class
inputs and saved FLOW-image refinement. It is not a cleaner-only count.

The closed native normal window supports this distinction:

| CPU ancestry | OWASP run-1 | thingsboard run-1 | conductor run-2 |
| --- | ---: | ---: | ---: |
| Explicit native GC symbol union | 63.56% | 45.63% | 84.92% |
| `runtimeFact` | 6.15% | 6.30% | 0.02% |
| `walkNodes` | 5.51% | 8.90% | 0.33% |
| Static owner effect handling | 0.01% | 12.82% | 0 |
| Static call supply | 0 | 9.38% | 0 |
| Fixed/writer supply `publishMatched` | 0 | 2.76% | 0 |
| Saved FLOW-image materialization | 0.01% | 0.011% | 0 |
| Cleaner chain | 13.62% | 3.89% | 0.27% |

Shares use all native samples, include worker CPU and overlap. They are
not wall-time pause fractions, allocation weights or event counts.
Conductor's normal window starts after run-1, in run-2. OWASP's near-limit
window crosses run-7, its barrier and run-8; it is not a run-1 profile.

Thingsboard's sampled owner work mainly follows static call supply and
ordinary summary application, rather than packed cleaner-point repair.
Answered call/point rows are removed; a later eligible consumer can
subscribe again and obtain an immediate answer for a known group. This
is a repetition mechanism, not a measured duplicate-key rate. New images
still need their answers. Do not suppress all later subscriptions.
`validation/combined-native-review.{md,json}` retains exact stack hashes,
phase alignment, category matchers and overlaps. The JFR review below
adds allocation and GC evidence.

The completed phase review adds allocation and GC evidence:

| Project / phase | Engine, s | Sampled allocation, decimal GB | Coverage |
| --- | ---: | ---: | --- |
| OWASP run-1 | 172.096 | 372.106 | Engine inside retained recording envelope |
| conductor run-1 | 18.134 | 81.656 | Engine inside retained envelope |
| thingsboard run-1 | 260.838 | 1,065.692 | Final endpoint uncertain at metadata rounding precision |
| Stirling-PDF run-1 | 25.089 | 47.349 | Partial; whole-run allocation unknown |
| OWASP run-2 | 210.690 | 594.868 | Engine inside retained envelope |
| conductor run-2 | 80.176 | 393.143 | Engine inside retained envelope |
| Stirling-PDF run-2 | 352.304 | 2,322.393 | Engine inside retained envelope |

The retained PDF recording starts about 6.665 seconds after run-1 starts;
its prescan is unavailable. The raw summary's zero prescan samples/bytes
do not mean zero work. OWASP's retained recording starts before prescan
and covers all eight engine intervals. Metadata gives an envelope, not
proof of a sample at each instant. No missing interval is extrapolated.

Run-1 `runtimeFact` allocation ancestry is 30.00% for OWASP, 18.97% for
conductor, 9.10% for Thingsboard and 7.96% for PDF's retained portion.
The source-proven second projection is one part of these totals. Keep
the first receiving-manager projection and do not claim the whole share
as removable work.

Restricted-run subsumption is a different cost. `TrieOps.subtract`
accounts for 30.10% of conductor run-2 allocation ancestry and 36.07%
of PDF run-2. Nested `TaintGroup.subtractSubsumed` accounts for 27.21%
and 33.35%. These are overlapping ancestry shares. PDF's native normal
window also has 3,012 monitor-spin leaf samples; 2,956 include leaf-node
interning ancestry. This identifies sampled contention, not the retained
memory cause or a reason to change the reference manager.

Retained JFR GC pause sums are 34.507 seconds for OWASP, 35.004 for
conductor, 91.135 for Thingsboard and 68.931 for PDF. These cover their
respective retained recording intervals, including several phases.
Native GC-worker CPU shares are not wall-time pause fractions. The
complete review records per-phase attribution by GC cycle start, crossing
cycles, all 13 engine phases and their top frames in
`validation/combined-profile-review.{md,json}`.

Conductor's normal native window nominally overlaps its live-heap capture
by about 8.8 seconds, with about one second of anchor uncertainty. Its
84.92% GC-symbol share therefore includes diagnostic dump/GC work. It is
not a clean solver CPU window. Collapsed samples have no timestamps for
removing that interval. The other normal windows do not overlap their
heap captures.

Main's retained legacy full-scan profiles are also reviewed. They take
44.793, 26.008, 39.387 and 5.370 seconds for OWASP, conductor,
Thingsboard and PDF, respectively. Their sampled allocations are
71.730, 37.417, 83.188 and 9.993 decimal GB. They include different
analysis/report work from a bidi run-1 engine and do not give an isolated
speed ratio for the two selected changes. Legacy CPU/allocations include
method lookup, summary rewriting and AccessTree merge work. Candidate
costs now include repeated GROUP projection and, in FULL runs, generic
TAINT subsumption. Raw phase tables remain in each main profile summary.

## Combined conductor live heap

Conductor reaches run-2 OOM after it completes and accepts run-1. Its
combined capture contains **7,611,929,984 reachable bytes**. The selected
owner unions are:

| Owner selection | Retained bytes |
| --- | ---: |
| Current method actors and edge work | 2,101,137,056 |
| Publication/subscription | 648,443,944 |
| AP manager and store interners | 9,299,080 |
| Persistent records/current demand | 96,011,456 |
| Context/forms | 229,129,472 |
| Resolved rules | 431,475,368 |
| Witness/report state | 13,164,888 |
| Event queues/channels | 1,590,400 |
| IR storage | 3,632,692,536 |

These unions overlap. Do not add them. All 17,206 actors are backward,
limit 1, with active edge/initial stores and owner context. Run-1 static
service/point-resume instances are absent; only the static service's
Companion singleton remains. This is active run-2 state, not retained
run-1 writer state. An obsolete link-store field in the generic OQL is
unavailable, not zero. The report has no query error; that blank field
does not certify link cleanup.

The global census has 4,991,529 ConclusionGroups, 5,168,242 TaintGroups,
6,369,247 TaintTrees and 2,377,101 Publication objects. It has only
20,448 InitialAp objects, 2,598 MarkSets and 3,259 TaintLeaves. A small
fact grammar does not bound the relation over method, original premise,
statement, current base and annotations. The store owns containers for
that relation, even when current scalar values repeat across origins.

For example, at a join in this method, two requested hypothetical inputs
can produce the same current value under different originals:

```java
Object choose(Object p, Object q, boolean b) {
    Object x = b ? p : q;
    return x;
}
```

```text
I0 = arg0/./G; I1 = arg1/./G
I0 -> x/./G, DEMAND
I1 -> x/./G, DEMAND
```

Both origins are required for later guidance. The method store currently
owns separate premise/statement groups for them. A small number of group
names or current AP shapes therefore does not imply a small store or
few per-origin AP applications. This example explains the product in the
key; the heap does not measure an exact equal-input kernel reuse rate.

The global upper bound on stored TAINT alternatives per TaintGroup is
about **1.232**. This is a many-small-groups pattern. The physical lower
bound for empty FLOW maps, reach arrays and empty TAINT layer containers
is **548,875,992 shallow bytes**. It is not a measured achievable saving
and does not include all live group state. The source and exact counts
are bound in `validation/combined-conductor-heap-review.{md,json}`.

## Combined Thingsboard live heap

Thingsboard reaches run-1 OOM, with no completed iteration or accepted
report. Its live capture has **11,441,129,264 reachable bytes**. All
73,669 active method actors are forward, limit 1. This is active run-1
state; the presence of its static service is not a lifecycle leak.

| Owner selection | Retained bytes |
| --- | ---: |
| Current method actors and edge work | 3,910,286,992 |
| Publication/subscription/replay | 1,388,957,272 |
| Run-1 static service and point engines | 651,113,304 |
| AP manager and store interners | 36,629,576 |
| Event queues/channels | 7,599,816 |
| Context/forms | 727,372,352 |
| Resolved rules | 1,325,641,928 |
| Witness/report state | 241,692,832 |
| IR storage | 2,690,100,072 |

These retained unions overlap. Do not add them or subtract one from
another. The static selection includes all point engines, not just the
global service. IR remains out of scope. The largest individual rule
provider retains 1,267,033,512 bytes; the method-entry cache retains
725,551,864 bytes. These are separate dominator queries, not extra bytes
to add to the table.

The global census has 4,992,989 ConclusionGroups, 5,193,981 TaintGroups,
7,010,796 TaintTrees and 6,932,742 TrieNodes. In contrast, it has only
983 InitialAp objects, 1,020 MarkSets and 1,607 TaintLeaves. The small
canonical fact alphabet is replicated across method/premise/statement
storage cells. It does not bound the number of those cells. The actor
selection retains 3,938,855 ConclusionGroups; publication state retains
1,054,133, with overlap possible. This is a storage-product problem,
not evidence of millions of distinct mark names.

The static physical census separates consumers from writers:

| Physical object | Count |
| --- | ---: |
| Pending call continuations and their keys | 304,292 each |
| Pending point rows and their keys | 226,498 each |
| Saved FLOW-image roots | 618,263 |
| Fixed-group conditional joins | 10,276 |
| FLOW writer rows | 5,446 |
| Service pending consumer rows | 268,523 |
| Field states | 81 |
| Event queue entries | 13,351 |

Physical writer-row objects are not a direct query of current map
membership. The runtime counters and heap census have different
boundaries; do not silently equate them. Pending calls retain 304,292
scalar Publications and TaintTrees. The source creates that scalar copy
per output leaf while its complete group continuation is pending. This
supports the consumer pattern seen in native stacks. It does not
establish how many completed equivalent calls repeat.

Only 13,285 PointReady envelopes are live at this instant. The roughly
19.9 million owner replies are cumulative work, not standing queue size.
Pending rows can wait for a group after the runnable queue drains. Actor
counters sum 48,073,520 processed EdgeDelta steps, with a largest actor
at 4,537,863. Those are neither scalar fact counts nor proof of infinite
processing. No repeated no-progress state or infinite cycle is proved.

The global TaintTree/TaintGroup ratio bounds stored alternatives by about
1.350 on average under the owned-wrapper constructor contract. The
physical unused-scaffold lower bound is **461,507,352 shallow bytes**.
Neither number gives a per-list maximum or an achievable memory saving.
Exact field names, equivalent call-key repetition and the stored
static/nonstatic-origin split are unavailable in this report schema.
The complete limits and all 19 query bindings are in
`validation/combined-thingsboard-heap-review.{md,json}`. All queries close
without a MAT error.

## Combined Stirling-PDF live heap

PDF reaches run-2 OOM after it completes and accepts run-1. Its live
capture has **7,578,514,456 reachable bytes**. All 7,258 actors are
backward, limit 1. No Run1StaticService or Run1PointResume instance is
live; only the service Companion singleton remains. This is active FULL
run-2 work, not retained run-1 static machinery.

| Owner selection | Retained bytes |
| --- | ---: |
| Current method actors and edge work | 4,158,574,232 |
| Publication/subscription | 778,740,144 |
| AP manager and store interners | 9,917,360 |
| Persistent records/current demand | 31,625,656 |
| Context/forms | 235,629,424 |
| Resolved rules | 232,652,632 |
| Event queues/channels | 776,456 |
| IR storage | 1,535,319,232 |

These retained unions overlap; do not add them. The global census has
8,495,619 ConclusionGroups, 8,948,150 TaintGroups, 10,509,971 TaintTrees
and **23,584,539 Publications**. The actor union contains 23,584,084 of
those Publications, or 566,018,016 shallow bytes. The separate
publication/subscription union contains no Publication wrappers; it owns
its trie/index state instead. A type name alone does not identify its
owner or prove that it has already passed publication deduplication.

The source has two paths into actor-side `pending`:

```text
fresh raw summary delta, or a new query's replay of permitted raw summaries
    -> queuePublications -> pending += Publication(premise, piece)
method worklist drains -> flushPublications -> SummaryStorage index growth
```

The index deduplicates after these wrappers have been constructed and
buffered. Thus edge-store and publication-index deduplication do not
prevent all earlier publication work. The heap query does not select
`pending` directly or decode complete publication keys. It cannot label
all actor-owned wrappers duplicate, pending in one list, or removable.
The source path supports first-authorization replay as a focused next
proposal, with its removable fraction still to be measured.

The average stored TAINT alternative upper bound from the global
tree/group ratio is about 1.1746, under the owned-wrapper contract.
The physical lower bound for unused maps, reach arrays and TAINT layer
containers is **966,094,632 shallow bytes**. These are different
measurements and cannot be added as predicted savings. Exact pending
list lengths, complete publication keys and their duplicate share are
not queried. The actor counter has 33,357,158 EdgeDelta steps and
8,390,434 demand-layer increments; these are not counts of distinct
scalar facts.

The closed 19-query review is in
`validation/combined-pdf-heap-review.{md,json}`. There are no MAT query
errors. The generic obsolete link-store column is unavailable, not zero.
Active state before cancellation cleanup is not itself a lifecycle leak.

## Capture intervals

| Combined live snapshot | Phase | File bytes | Capture interval, UTC |
| --- | --- | ---: | --- |
| conductor | run-2 | 11,746,103,620 | 10:38:50.010–10:39:07.714 |
| thingsboard | run-1 | 18,419,945,521 | 10:46:17.181–10:46:46.044 |
| Stirling-PDF | run-2 | 12,150,010,513 | 10:53:30.684–10:53:50.021 |

The paired intervals are 17.704, 28.863 and 19.337 seconds. These
intervals remain inside the listed engine times. File size is not retained
heap or RSS. The owner reviews use the reachable class census and retained
sets, rather than these file sizes.

## Writer-only conductor control

The separate control `8157a605540679bda1f91c01836f80442b54f3aa`
contains the writer change and final producer-descriptor correction,
without the alias change. Its artifacts are in
`writer-only-fixed-projects/conductor/bidi`.
It completes and accepts run-1 in **50.198 engine seconds**, then ends
with **run-2 OOM**. Latest reached/completed/accepted are
**run-2/run-1/run-1**. It is not the combined project result.

This control overlaps acceptance tests on the shared host. Its structural
counts and heap census are stronger evidence than a small timing
difference. The earlier writer control is superseded and is not used.

Its run-2 capture retains **7,626,491,152 bytes** of reachable objects.
The following are retained unions; they overlap and must not be added:

| Owner selection | Retained bytes |
| --- | ---: |
| Current method actors and edge work | 2,038,278,464 |
| Publication/subscription | 621,132,064 |
| AP manager and store interners | 9,654,392 |
| Configuration/rules | 431,560,584 |
| Context/forms | 228,943,176 |
| IR storage | 3,648,797,456 |

The census has 5,005,897 `TaintGroup` objects and 6,177,867 `TaintTree`
objects: at most about 1.234 stored TAINT alternatives per group globally.
It has 9,921,661 HashMaps. Run-1 static service and point-continuation
classes have zero live instances in this run-2 capture. Mark payloads and
their interner do not explain this OOM. The analyzer has millions of small
per-origin/per-point containers. IR is outside the selected optimization
scope.

The layout/census lower bound for unused container scaffolding is
**530,673,672 shallow bytes**. This is neither an exact achievable saving
nor an additional retained union. The class census, owner queries and
bound are retained in the control's `mat-compact-initial.json` and
`validation/run2-followup-layout-bounds.json`.

JFR estimates 206.087 GB of sampled allocations in control run-1 and
385.327 GB in control run-2. In run-1, `runtimeFact` accounts for 22.81%
of inclusive allocation weight and `walkNodes` for 25.11%. In run-2,
`TrieOps.subtract` accounts for 29.34%, `TaintGroup.subtractSubsumed`
for 25.87%, and summary enumeration for 4.80%. Inclusive shares overlap.
Allocation weights measure churn, not retained heap or a promised saving.

## Interpretation and next iteration

The selected changes remove caller-created writers and no-effect alias
work. The writer-only conductor control supports that work-admission
effect structurally. It does not establish a project speedup in isolation.
The combined analyzer still fails every overall resource gate against
fresh main. Conductor and PDF pass only the run-1 time target; OWASP and
Thingsboard do not. The failed strict location and candidate-count gates
also remain explicit.

The remaining workload has three distinct parts:

1. Run-1 repeatedly projects immutable GROUP results, with a
   source-confirmed second projection at four immediate AP callbacks.
   Allocation/GC is substantial even when the canonical fact alphabet
   is small. Remove only those proved repeated walks in a future patch.
2. Thingsboard has many static consumers and per-origin/current-position
   storage cells. PointReady replies dominate cumulative static events.
   A small writer count does not remove required call composition or
   bound the number of pending consumers. Complete-key repetition must
   be measured before a consumer-suppression design can be selected.
3. Later FULL runs retain millions of small conclusion containers and,
   especially in PDF, actor-side Publication wrappers. Generic
   subsumption allocates heavily. Old-answer replay after new query
   batches is a source-confirmed avoidable path, but its share of these
   complete heaps is not measured. The run-2 captures contain no live
   run-1 static service/point engines.

The large method-exit candidate families and exact-base negative tests
also identify a scope defect. A repair needs a separate provider-context
design. A ZERO-only guard can block legitimate nonzero static-origin
endpoint checks; a blanket static exception is insufficient. No such
repair is included in this implementation or silently assumed by its
proofs.

The [next proposal](../design/bidirectional-local-static-writers-aliases-next-iteration-20261022.md)
was revised after user review. Performance work now focuses on Thingsboard
run-1 and algorithmic removal of repeated static GROUP handshakes and
unary supply joins. The ZERO exit restriction and Spring rewrite are
withdrawn/deferred; controller annotation-based checks remain. See the
[focused source investigation](bidirectional-thingsboard-run1-workload-investigation-20261022.md)
for the new local model and the still-open AP/packed-suffix proof gates.
The former projection, container and later-run replay ideas are deferred.
These revisions do not change the measurements above. No further runtime
optimization has been implemented.

All eight command/profile reviews, four strict finding reviews and three
live-heap owner reviews are closed. The postprocessing terminal receipt
has five successful steps: comparison, profile decoding and three heap
reports. These are evidence-processing successes, not analyzer acceptance
passes. Current raw captures and compact, hashed source evidence remain
under the campaign root. `validation/final-completion-audit.json` records
64 completed evidence checks, the unchanged 23-file runtime/test source
and the failed project gates. Evidence completeness does not change those
gate outcomes.
