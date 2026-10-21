# Run-1 representative premises: performance and findings

Date: 21 October 2026. Status: **all eight runs and their profile readers
closed; both OOM snapshots analyzed**. The investigation is complete with
the evidence limits below. The correctness and performance targets failed.

**All four 60-second run-1 targets failed.** OWASP and Stirling-PDF completed
run-1 in 863.788 and 249.314 engine seconds, then failed in run-2. Conductor
timed out in run-1 after 1053.793 seconds; Thingsboard ended with OOM in
run-1 after 341.821 seconds. Those failed prefixes are not completion times.
Every candidate command took substantially longer than its fresh main
counterpart, and no candidate completed its full analysis sequence.

The major costs differ by project. Cleaner execution dominates OWASP and
conductor run-1 Java samples. Thingsboard spends 57.84% of run-1 Java
samples under call-form construction and retains 4.766 GB under static
service/point owners. Its pending point population multiplies cleaner
sites by current images. PDF's OOM is later: run-2 retains 22.602 million
pending publication wrappers. No infinite processing cycle was proved.

Run-1 E6 caller-summary bindings and combinations are zero, as intended.
There is no fresh matched pre-patch candidate A/B in this campaign, so these
measurements do not establish a causal speedup from removing run-1 ND edges.
The fresh main comparison is a comparison with the legacy analyzer, not an
isolated control for that patch. The profiles, heaps and source comparison
below identify work amplification without assigning an unmeasured causal
factor to each mechanism.

Implementation: `5d3a706cfaa8fa5620dab5c5b268d02b1a8432e2`.
Fresh main baseline: `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
The main checkout has only the reviewed MemoryManager snapshot hook;
its binary patch hash is
`53f0abeeb0c98a16db86b8236b91899020fca14e799ff69a6739addf644b402f`.
Both versions use the snapshot hook. Old candidate reports are not current
measurement evidence.

Artifacts are in
`/drive-testcomp/opentaint-go-rules/run1-single-premise-rules-20261021`.
The runtime source and full input trees are pinned by the new campaign.
Both endpoints and final source/input equality passed: 10 datasets,
177499 file memberships and 16 controls. All eight leaves and the study
controller are closed. The candidate core/rules still match the commit.
Authority: `validation/study-closed.json` and
`validation/benchmark-guard/equality.json`.

## Selected change

Run-1 still waits for a complete compatible condition binding. It then
keeps one original premise. Prefer argument, `this`, other non-static,
static field, then exact static class. Use stable logical values to break
ties. ZERO is neutral if a nonzero original exists.

```text
Before: {Iarg0, Iclass1, Iclass2} -> Y / DEMAND
After:   Iarg0                   -> Y / DEMAND
```

This is a selected run-1 approximation. A later call can use the summary
with only the selected input. The omitted inputs are still required for
the original local condition check. Later FULL runs keep exact conjunctions.
Run-1 executable edges, point resumes, subscriptions and summaries must
remain atomic. The run-1 E6 join path must remain unused.

Source target paths/tails, all effective input layers, complete sink
witnesses, field transfers, cleaners, group representation, static requests,
queues and caches keep their existing behavior. The
[design](../design/bidirectional-run1-single-premise-rules-20261021.md)
and [active spec](../../spec/ap.md#04-run-1-representative-premises-selected-21-october)
state the full scope.

## Validation before measurements

| Gate | Tests | Failures | Skipped |
| --- | ---: | ---: | ---: |
| Complete common dataflow suite | 1661 | 0 | 0 |
| Complete JVM dataflow suite | 436 | 0 | 0 |
| Selected application integration | 38 | 0 | 0 |
| Query rule compiler and bidirectional examples | 41 | 0 | 3 |

The three skipped query controls require `OPENTAINT_TEST_BIDI=true`.
They were not enabled in this gate. Five active `BidiExampleTest` controls
passed. Its stale test-only `1,2,3` sequence was corrected to the current
`1,1,2` sequence; the runtime schedule did not change.

Tests cover complete admission in both arrival orders, missing inputs,
ZERO, stable preference under different allocation orders, no union
allocation during run-1 selection, unchanged fixed MULTI source targets,
complete sink evidence and exact later FULL joins. An end-to-end control
checks run-1, run-2 and run-3: run-2 recovers both full-mark sources after
run-1 discards one original; run-3 restores the two-input summary.

The independent scalar oracle applies the selected rule after complete
binding. Tests explicitly state the additional run-1 caller candidates.
They do not erase their premise differences from observations.

The existing narrow Lean model and its 23-theorem audit are unchanged.
They prove local selection, complete-cube gating, fixed-clause positive
Horn coverage, atomic labels and projected guide-entry laws. They do not
prove the whole mutable analyzer or unrestricted run-2 recovery. The
implementation receipt binds the tested files to the committed source:
`implementation-receipt.json`, with gate logs and XML under `tests/`.
The later source audit rechecked all 29 accepted files against both the
worktree and commit, and re-read all 269 archived test XML files. It
confirms 2173 executed passes and three skips without running another
test workload: `validation/tested-source-revalidation-20261021T1150.json`.

## Fixed workload settings

Use `runProjectAnalyzer` through Gradle. Use Java 17, the configured rules,
models and conductor extensions. Static propagation is enabled:
`opentaint.bidi.disableRun1Statics=false`.

| Project | Heap | IFDS timeout | Outer timeout |
| --- | ---: | ---: | ---: |
| OWASP | 8g | 1000 s | 1200 s |
| conductor | 8g | 1080 s | 1200 s |
| thingsboard | 12g | 1080 s | 1200 s |
| Stirling-PDF | 8g | 1080 s | 1200 s |

All runs are serial. The campaign uses continuous JFR, a 30-second CPU
capture at full-engine start +60 seconds, and a near-limit capture where
the workload lasts long enough. Nominal profile names do not prove which
iteration they contain. The final analysis must map every capture to its
actual run and phase.

The MemoryManager hook captures a live heap before cancellation. A JVM
OOM can produce a separate heap dump. A snapshot can add time to the
containing engine or barrier. Report that time separately when available.

Before this campaign, 74 old raw/index files were removed, freeing
161,232,582,628 bytes. Compact prior findings, logs, SARIF and receipts were
retained. User-owned files were not changed.

## Closed scalar results

The table authority is
[`validation/final-benchmark-metrics/final-benchmark-metrics.json`](</drive-testcomp/opentaint-go-rules/run1-single-premise-rules-20261021/validation/final-benchmark-metrics/final-benchmark-metrics.json>).
It cross-checks closed summaries, manifests, exact control markers, the
comparison rollup and final equality with 325 passed checks. Times below
are seconds, rounded to three decimals. Exact logged precision and marker
locations remain in the packet.

| Project | Version | Terminal | Command wall | Prescan | IFDS span | Main fullscan |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| OWASP | main | OK | 129.230 | 41.255 | 86.146 | 44.890 |
| OWASP | candidate | TIMEOUT | 1045.490 | 34.267 | 1000.080 | N/A |
| conductor | main | OK | 91.161 | 25.166 | 49.859 | 24.691 |
| conductor | candidate | TIMEOUT | 1122.648 | 25.826 | 1080.129 | N/A |
| thingsboard | main | OK | 210.378 | 142.747 | 174.260 | 31.507 |
| thingsboard | candidate | OOM | 505.849 | 127.285 | 471.859 | N/A |
| Stirling-PDF | main | OK | 48.088 | 14.584 | 19.901 | 5.315 |
| Stirling-PDF | candidate | OOM | 597.047 | 16.793 | 561.762 | N/A |

Command wall includes launch, setup, prescan, engines, barriers, export and
shutdown. The IFDS span is the interval between its own start/end markers;
it includes prescan. Main fullscan uses the legacy fullscan markers. Engine
and barrier times use their separate high-resolution counters. These scopes
must not be substituted for one another. Main has no Bidi iteration labels.

All measured engines use limit 1: run-1 is FORWARD and run-2 is BACKWARD.
A dash below means that no such engine/barrier was reached or completed;
it is not a zero duration.

| Project | Run-1 status / engine s | Run-1 barrier s | Run-2 status / engine s | Latest reached | Latest COMPLETE | Accepted forward report |
| --- | --- | ---: | --- | --- | --- | --- |
| OWASP | COMPLETE / 863.788 | 4.908 | TIMEOUT / 96.012 | run-2 | run-1 | run-1 |
| conductor | TIMEOUT / 1053.793 | — | not reached | run-1 | unavailable | unavailable |
| thingsboard | OOM / 341.821 | — | not reached | run-1 | unavailable | unavailable |
| Stirling-PDF | COMPLETE / 249.314 | 1.250 | OOM / 294.173 | run-2 | run-1 | run-1 |

The accepted forward report is established by the compact frontier logged
after `report.add`, separately from engine COMPLETE. OWASP's complete
run-1 iteration including its barrier took 868.696 seconds; PDF's took
250.565 seconds. A completed and accepted run-1 is still an unrefined report
when the subsequent backward engine fails. No candidate met the full
analysis or 60-second run-1 objective.

The normal native capture's nominal requested interval is in run-1 for
all four candidates. OWASP's near-limit capture is in run-2; conductor's
is in run-1. Thingsboard and PDF ended before the near-limit delay. No main
native window launched: each main fullscan ended before +60 seconds, and
each IFDS span ended before the near-limit delay. Recorded profiler exits
are zero, but nominal 30-second requests do not establish exact closing
UTC timestamps or continuous JFR retained coverage. The metrics packet
keeps those attribution limits explicit.

### Run-1 counters

The `N2` label is the existing subscription-directory counter name. Its
accepted deltas and created groups count packed input/directory work, not
individual CFG facts or fact leaves. E6 specifically counts caller-summary
ND bindings/combinations, not all local source/sink condition matching.
Run-1 END counters record E6 **0 / 0** in all four projects. Later FULL
iterations can still use exact ND state.

| Run-1 cumulative counter | OWASP | conductor | thingsboard |
| --- | ---: | ---: | ---: |
| Input arrivals | 186297 | 1263508 | 634643 |
| Fresh method initials | 42327 | 72797 | 111174 |
| N2 accepted deltas | 163814 | 742310 | 557032 |
| N2 created groups | 56163 | 380383 | 431864 |
| Offered request memberships | 60099570 | 37959417 | 5887568 |
| Temporary duplicate memberships | 60019598 | 36471302 | 4609018 |
| Committed-store duplicate memberships | 25733 | 1338945 | 1120112 |
| Fresh temporary memberships | 54239 | 149170 | 158438 |
| Peak temporary memberships | 36223 | 5322 | 11067 |
| Maximum request service delay, s | 706.896 | 686.896 | 256.161 |

These are cumulative work counters. Conductor and Thingsboard are partial
run-1 observations, and END counters after cancellation/cleanup are not
live heap populations. Thingsboard committed only 147640 fresh request
members before cancellation; its 158438 fresh temporary members must not
be substituted for that committed count. Request deduplication is working,
but it cannot undo upstream operations that produced duplicate offers.
The counts alone do not identify equal intermediate facts, a static-only
source, an infinite loop, or a saved-work factor. Source-bound counter
packets are `validation/{OWASP,conductor,thingsboard}-run1-counters.json`;
the PDF run-1 END counters are in its closed analyzer log. Final campaign
equality supersedes the earlier packets' historical pending-equality note.

### OOM capture phases

| Project | Phase | Snapshot UTC | Snapshot interval | File bytes |
| --- | --- | --- | ---: | ---: |
| thingsboard | run-1 FORWARD, GROUP | 11:54:34.911–11:55:02.225 | 27.314 s | 18316015804 |
| Stirling-PDF | run-2 BACKWARD, FULL | 12:05:27.075–12:05:45.394 | 18.319 s | 12136867298 |

Both are `projects/PROJECT/bidi/guard-live.hprof` live MemoryManager
captures. Their intervals are included in the containing engine wall
times. File size, sampled RSS and the hook's logged heap-used value are
not retained-owner sizes. Keep PDF's later FULL lifetime separate from
Thingsboard's run-1 population. Ownership interpretation belongs to the
heap analysis; these scalar markers do not establish the OOM cause.

## Closed finding comparison

The unchanged strict key uses rule, sink fingerprint and full method
identity, with its existing fallbacks. Raw location spans, severity/kind,
properties and occurrence multiplicity are checked separately. No match
was relaxed to remove a missing identity. The final
[`campaign-summary.json`](</drive-testcomp/opentaint-go-rules/run1-single-premise-rules-20261021/validation/closed-pair-comparisons/campaign-summary.json>)
binds all four comparisons to the final source/input equality.

| Project | Main identities / occurrences | Candidate identities / occurrences | Main identities retained | Strict missing | Added identities / occurrences |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4112 / 4112 | 14438 / 47599 | 4112 / 4112 | 0 | 10326 / 43487 |
| conductor | 7 / 7 | 0 / 0 | 0 / 7 | 7 | 0 / 0 |
| thingsboard | 14 / 14 | 0 / 0 | 0 / 14 | 14 | 0 / 0 |
| Stirling-PDF | 17 / 17 | 1654 / 61820 | 15 / 17 | 2 | 1639 / 61805 |

Matched identities have no changed raw location spans or severity/kind/
properties in the closed comparison. OWASP retains every observed main
identity and occurrence in its accepted run-1 output. Its additional
identities are unrefined candidates, not individually validated false
positives. This incomplete analysis is not a universal no-FN result.

### Conductor and Thingsboard: no accepted first iteration

The seven conductor and 14 Thingsboard missing identities are output
coverage failures with a source-backed report-admission explanation.
`ReportBuilder` starts empty. `IterationDriver.runOnce` returns on a
non-COMPLETE first engine before the forward barrier calls `report.add`;
`ReportBuilder.add` also requires a COMPLETE forward run. Neither project
has an accepted forward report. Thus the first-engine timeout/OOM leaves
zero accepted findings. This does not prove that the engine could not
derive any particular witness, or establish whether it had already done
so before cancellation. The strict missing locations stay recorded.

The compact rationales retain every missing rule/method/instruction and
its raw span under `validation/closed-pair-comparisons/{conductor,thingsboard}/`.
They found no differing model/settings/input, prescan failure or location
export error explaining these two empty reports. Their original
pending-equality notes are historical; the final rollup binds the passed
after-endpoint equality.

### PDF: two strict return-location gaps and 525 export errors

PDF has an accepted run-1 report. Its two missing identities must therefore
be investigated independently of the later run-2 OOM. Both are
`java.security.xss#sink#1` method-exit sinks:

| Method | Missing main return | Candidate same-rule/method return |
| --- | --- | --- |
| `PipelineController.handleData` | instruction 50, `return %104`, lines 90–91 | instruction 109, `return null`, line 130; 74 occurrences |
| `ConvertEmlToPDF.convertEmlToPdf` | instruction 103, `return %54`, lines 133–134 | instruction 126, `return %89`, line 148; 74 occurrences |

The source audit identifies a concrete reporting information loss. Both
analysis paths merge real returns at `JApplicationSingleExitGraph`'s
synthetic normal exit. The candidate evaluates the boundary sink there
and `JIRBidiAnalysis.kt:149–156` exports that boundary with
`TracePathGenerationResult.Simple`, without the witness's return-specific
support/path. Simple provides no relevant trace locations. The shared
`TraceMessageBuilder.tryResolveNormalGeneratedLocation` then uses its
existing fallback: the last real return in the method. Consequently, a
sink-supported earlier return can be displayed at a later untainted return.

The shared SARIF generators, location resolver and exit-selector fragment
are byte-identical between the pinned main and candidate. Legacy main can
supply a generated path with a relevant return; it can also fall back when
that path is unavailable. The candidate forces Simple, making this fallback
universal for these boundary exports. This explains the observed location
mismatch mechanism. It does **not** prove equivalent core-flow coverage of
either earlier return, so both strict failures remain failures. Source
anchors/hashes and the comparison with main are in
[`validation/source-audits/method-exit-locations-5d3-vs-f945/audit.md`](</drive-testcomp/opentaint-go-rules/run1-single-premise-rules-20261021/validation/source-audits/method-exit-locations-5d3-vs-f945/audit.md>).
The existing method-exit controls assert raw Simple findings, not multi-return
SARIF indices/spans, and do not close this reporting gap.

Separately, 525 candidate results could not be exported at valid virtual
method-exit locations: 450 normal and 75 exceptional exits, across Optional
and Stream models. The closed census records 62345 attempted results and
61820 exported occurrences. `generationFailed=0` is a trace-generation
counter; it does not mean zero SARIF export failures. The source permits
several unresolved/unsupported exit-location paths, so the census must not
be assigned wholesale to one null branch without object/path evidence.
Full per-location records, export-error census and limits are in
`validation/closed-pair-comparisons/Stirling-PDF/`. These errors are distinct
from the two strict missing main identities.

## OOM heap ownership

Both live dumps have been read with MAT. Each tool run closed, its actual
Java process exited, and all 23 query outcomes were inventoried. A blank,
empty, absent or failed query remains unavailable. The top-200 dominator
views are bounded displays, not complete population partitions.

| Snapshot | Reachable objects | Reachable shallow bytes | Method-edge owner union | Run-summary owner union | Caller/publication/request owner union |
| --- | ---: | ---: | ---: | ---: | ---: |
| thingsboard, run-1 OOM | 367676466 | 11394828128 | 241218752 | 201458040 | 393510528 |
| Stirling-PDF, run-2 OOM | 236130874 | 7560068152 | 2451237168 | 717594384 | 821671624 |

These retained unions overlap. Do not add their sizes. Allocation weights,
file sizes and cumulative work counters are different measurements.

### Thingsboard: static continuations dominate analyzer state

The static service and point-owner retained union is **4765655104 bytes**,
41.82% of the reachable heap. It contains 5493768 `PointKey`, `PointRow`
and `PointContinuation` objects each, plus 5429797 `StaticInterest` and
global `InterestRow` objects each. Of the latter, 5428124 are field pending
rows and 1673 are class pending rows. The global field sets contain only
1558 requested, 11 reached and 43 processed group memberships. Thus, the
large object population is continuation state per program input/site,
not millions of different static fields or groups.

There are 66 field states and 42 interned rule groups. The service retains
115151 FLOW writer rows, with 2984318 admitted writer/group memberships.
The actor directories contain 138696 writer selectors, 320322 writer-input
rows, 43160 call continuations and 78773 read descriptors. These are
separate populations; do not call all of them writers or logical facts.

Ordinary summary requests contain 18595 rows and 147640 memberships.
The local predicate/conjunction retained union is 16628720 bytes. All
70767 local services share their policy object with both local stores;
E6 scopes and local slot-join counts are zero, while 952 predicate scopes
remain. Complete local condition evidence is still allowed. These
observations confirm that eliminating executable ND state did not remove
the large static continuation population.

The largest actor has 63746 pending point rows. The point key includes
current FLOW mark exclusions, but the reply path clears those exclusions
before applying a concrete group supply:

```text
request: PointKey(site, original, image including mx, layer, call state)
reply:   relation(current path, current E, mx={}) -> resume at site
```

Only 489 `PlanFact` objects and 28 call frames are reachable at this
instant. They are not the main retained owner. Two field states account
for 4902131 pending field rows, 90.31% of the field total; their field
names were not decoded.

The bounded census covers all 63746 rows of one actual actor. Removing
mx alone gives **55406 keys**, only 13.08% fewer rows. This refutes a large
mx-only reduction for that actor. Keeping the complete context and FLOW
header but storing paths in a separate payload gives **3309 directory
keys**. This is 19.26 old rows per header, not a measured saved-byte factor.
Each requested group must keep its own image set; a reply must not apply
to images requested only for another group. No global ratio is inferred.

The actor has 63175 cleaner point rows (99.10%), 5 original initials and
134 image geometries. Most current bases are `this`. One statement/stage
has 59612 rows over 506 cleaner steps. FLOW mx cardinalities are at most
5 in this actor. The large factor here is many cleaner sites times current
images, rather than hundreds of distinct groups or an observed huge mx
powerset. Source return-rewriter generation still enumerates all relevant
full marks before projecting each action to GROUP. The captured rows do
not prove that all 506 targets/groups are equal.

The first structural probe could not decode class metadata and remained
unavailable. A separately reviewed second probe corrected only that
metadata expression and matched all 63746 rows. Both attempts and their
limits remain. Authority: `validation/thingsboard-point-probe-analysis.*`.
The proposed normalization/packing applies only to saved static
continuations. Ordinary FLOW exclusions and field processing stay intact.

Other large retained owners include the framework `SpringRuleProvider`
(1301745864 bytes), method entries (702793472 bytes), and the IR relationship
store (1625238528 bytes). These top-dominator figures overlap the class
views and other owners. IR storage remains outside optimization scope.
The result is not an assertion that every byte belongs to statics.

### Stirling-PDF: later FULL stores and publication work

This snapshot is **run-2 BACKWARD, limit 1**, after a complete and accepted
run-1. Its method-edge owner union is 2451237168 bytes, 32.42% of reachable
heap. The 7258 method stores have 1159859 non-reach premise directories.
A directory is an original/query scope, not one current fact. Run summaries
retain a union of 717594384 bytes; demand/current query owners retain
83871176 bytes. Persistent normal records retain 9357568 bytes.

The physical histogram also contains **25125672 `Publication` objects**,
603016128 shallow bytes. The first-pass owner queries do not assign that
whole population to a single publication directory or queue. The source
can append pending publication wrappers before publication-index admission
when fresh restricted queries replay existing summaries. A bounded actor
scalar probe confirms **22601733 pending wrappers** in 7258 distinct
actor lists, 89.95% of the physical population. Each append creates a new
wrapper. The lists have 27178938 reference slots in their backing arrays.
The 37 delivery envelopes hold only 536 publication references; do not add
those references as distinct objects. The remaining wrapper ownership is
unresolved. These counts do not measure distinct semantic summaries.

The source replays every already permitted summary when a batch admits
any fresh restricted query. It appends wrappers before publication-index
deduplication and flushes only after the method queue drains. This gives
a direct source-backed work amplification path. The census establishes
the pending population, not the exact duplicate-summary ratio. The enum
name column rendered a heap string reference; its decoded content is
unavailable. The phase follows ordered run controls, numeric config index
2/limit 1 and the immutable run-context construction. No replacement enum
value was inserted to pass the frozen validator. Numeric identities, list
counts, capacities and full populations were checked separately in
`validation/pdf-publication-probe-v1/numeric-publication-result.json`.

The static service queries have no CSV result and their retained-union
query is empty. Those outcomes are unavailable, not numeric zero. The
run-2 source releases the run-1 static owners. Local services keep their
owner and exact combination policy, but backward analysis does not allocate
the two forward local join stores. This explains the blank forward-store
columns in Q23; it is not evidence of a missing actor owner.

The IR relationship store retains 986029296 bytes. Unit runners are the
largest displayed analyzer owner class (3879676320 retained bytes across
90 instances), including ordinary method state. This OOM needs a separate
FULL-state investigation; a run-1 static-key fix does not address it.

Heap authority is under `validation/heap-tools-single-premise/jobs/`:
`job-thingsboard-20261021T121037.042423Z` and
`job-Stirling-PDF-20261021T122929.038020Z`. Each has the bound capture hash,
actual launch/closure, archive, and all accepted/unavailable query outcomes.

## CPU and allocation evidence

The eight continuous-JFR readers are closed. All seven selected event
counts match each recording's summary. OWASP, conductor and thingsboard
retain headers covering their full logged run-1 interval. PDF's rolling
recording lost all run-1 data and the first 37.051 seconds of run-2. Its
matching retained event counts do not recover those missing intervals.

| Full logged run-1 window | Java samples | Estimated allocation weight, bytes | Cleaner ancestry / Java | Cleaner ancestry / allocation | Normalization + projection exact leaves / Java |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP, COMPLETE | 44887 | 1690052826640 | 84.96% | 82.83% | 36.08% |
| conductor, TIMEOUT | 36174 | 1855689596080 | 86.61% | 91.63% | 45.51% |
| thingsboard, OOM | 16470 | 436586916880 | 6.98% | 37.00% | 2.60% |
| Stirling-PDF, COMPLETE | unavailable | unavailable | unavailable | unavailable | unavailable |

These are Java samples and estimated allocation churn, not all-process
CPU seconds or retained bytes. Inclusive rows overlap. OWASP's run-1 GC
pause union is 7.669 seconds. Its retained data has no DataLoss event or
stack-cap hit. Conductor has 1187 run-1 monitor events, with 41.291 summed
blocked-thread seconds; 1011 events and 37.104 thread-seconds are under
MarkTable. Thread-time sums are not wall time or CPU time. Missing
thresholded events do not prove absence of contention.

OWASP's run-1 constructor ancestry is 9.77% of Java samples and 5.01% of
allocation weight. Its exact constructor leaf is 3.33%; withStaticOperations
ancestry is 9.74%. Conductor has 6.02% constructor ancestry, 2.01% exact
constructor leaf and 5.91% withStaticOperations ancestry. These counts do
not prove empty argument lists or constructor call counts. FormsCache
callback ancestry is not a cache-miss/build rate.

Thingsboard is different from the first two projects. Its 9526/16470
run-1 Java samples under `JIRCallPlanBuilder.build` show actual construction
or dependency work. A retained cache hit returns before that builder is
called. This does not distinguish first builds for distinct slots from
rebuilds or transient uncached builds. Method/context owners stay strong;
their form payloads use managed soft references. The current capture has
no form-cache statistics. Cache thrashing is not established, and no cache
manager rewrite is proposed. A post-lowering cleaner compaction cannot
remove all earlier construction work. The source audit is
`validation/thingsboard-form-build-source-audit.*`.

PDF retains 257.123 seconds of its 294.173-second run-2 engine. In that
suffix, 76556 Java samples show trie subtraction at 9.95% as an exact
leaf, summary enumeration at 5.97%, and `runtimeFact` at 4.38%. Cleaner
ancestry is 4.21%. Its estimated allocation weight is 2113219301096 bytes.
These FULL/backward values do not describe run-1 GROUP processing. The
last 30 seconds overlap 18.319 seconds of heap capture; thingsboard's
last 30 seconds overlap 27.314 seconds. Neither tail is an uncontaminated
workload interval. Full interpretations are
`validation/profile-interpretation-{OWASP,conductor,thingsboard,Stirling-PDF}.*`.

The closed native windows give this additional view. All four normal windows are inside **run-1**. Counts
are weighted samples, not seconds or calls. Inclusive ancestry overlaps.

| Normal run-1 window | All weights | Java weights | cleanChain / all | Normalization / all | GROUP projection / all |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 5970 | 2965 | 46.70% | 3.53% exact leaf | 2.76% exact leaf |
| conductor | 26229 | 22751 | 84.00% | 32.08% ancestry | 24.80% ancestry |
| thingsboard | 23594 | 20436 | 75.90% | 37.52% ancestry | 31.91% ancestry |
| Stirling-PDF | 3109 | 2980 | 93.79% | not established | not established |

Conductor's normal window has 11731 weights whose monitor-entry ancestry
first reaches `MarkTable.key` or `isGroup`, 44.73% of all weights.
Thingsboard has 11136 such weights, 47.20%. This supports repeated shared
metadata access during cleaner/header work. It does not authorize removal
of interner synchronization.

Conductor's near-limit window is also run-1. It has 13819 all weights and
2962 Java weights. Cleaner ancestry is 21.07% of all weights and 98.31% of
Java weights; native GC dominates the whole profile. OWASP's near-limit
window belongs to **run-2**, so it must not support a run-1 CPU percentage.
This native evidence uses a different window and denominator from the
full-run Java tables. In particular, thingsboard's 75.90% early-window
cleaner ancestry does not replace its 6.98% full run-1 Java share.

Authority: `validation/profile-analysis/sweep-receipt.json` and
`validation/profile-reader-final-closure.json`. All sixteen recorded JFR
commands exited zero, their processes are absent, and the three shared
locks were independently acquired and released. Original JFR files remain.
Verbose derived exports were removed only after count/hash validation.

The four main recordings cover their legacy fullscan intervals. They
have no Bidi iteration labels. Their estimated fullscan allocation weights
are 70935524816 bytes for OWASP, 36970399712 for conductor, 82376323128
for thingsboard and 10219894088 for PDF. Main PDF still has visible cleaner
work: `JIRTaintCleanActionEvaluator.evaluate` is present in 11.00% of its
fullscan Java samples. Thus the old path is not cost-free. Other legacy
cleaner functions missing from bounded top displays are unavailable as a
complete family total, not zero. These phase domains differ from candidate
run-1. The compact data do not expose exact normalization-leaf intersections
with cleaner ancestry; those columns are marginal counts. Full comparisons:
`validation/profile-interpretation-main-comparison.*`.

## Why base-only did not have the same cleaner workload

Compare the implementation with `origin/saloed/base-only-clean` at
`42ba593651d9ea8a68bfb0911691377357714b9d`. The user's successful old
experiments are accepted as historical evidence. Their raw reports are
gone. This is a source comparison, not a new matched timing experiment.
The fresh measured baseline in the tables remains main at `f945f92c5`.

The new core reduces the mark domain but does not reduce all the work
that depends on the original full marks. Its work unit is often one
intermediate fact at one lowered cleaner phase. The old rewriter can
reuse a complete rewritten final AP at a call.

| Mechanism | Base-only branch | Current run-1 |
| --- | --- | --- |
| Return-rewriter selection | Index by base, then full mark; deduplicate action positions | Build a flat sequence from all admitted positions and full members; group projection happens later |
| Actual mark processing | Still visits all indexed marks for the current base; a presence check can skip actual cleaning | Execute every lowered step for each current list item; even a disjoint-base result passes through output policy |
| Repeated result | `rewrittenFacts[FinalFactAp]` memoizes the whole rewrite within the retained call handler; BaseOnly also caches prepared summaries | Cache compiled call plans, but no equivalent whole-rewrite result cache in `cleanChain` |
| Field action | Direct compact-AP cleaner; `.distinct()` removes equal surviving alternatives | Read into a typed temporary, clean it, write it back, cut to the limit; intermediate lists can retain equal results |
| Abstract mark | An absent concrete mark can record a reader refinement and skip the cleaner | Selected FLOW adds G to mx and emits a request, even if G is already excluded; static originals can retain a continuation at each step |
| Cost of an unchanged value | Base/mark checks and compact accessor operations can return the old value | Output GROUP projection, cover, exclusion normalization, interning and callbacks can run again |

The old algorithm also enumerates relevant full marks. It is incorrect to
say that it considers only the incoming mark. The difference is the work
performed after that enumeration and the reuse boundary. BaseOnly mode 0
also collapses structural fields and is not the same geometry as run-1
field limit 1. It cannot be used as a whole-engine semantic equivalence
oracle without those differences.

For example, a configured method can generate a return rewriter for many
full names that all have group G:

```java
Object y = configured(x); // source/cleaner metadata defines its return rewriter
```

```text
Current compiled sequence: Clean(return,G), Clean(return,G), ... K times
Incoming FLOW:            I -> return.*/FLOW, mx={G}
Every matching step:       same geometry and mx; request G again;
                          normalize/project the output again
Incoming GROUP TAINT:     selected cleaners cannot remove it in run-1;
                          each step still handles the unchanged value
```

Thus K full names can still cause K cleaner executions after they map to
one group. The condition simplifier does not simplify this action list.
For a static original, equal original/group requests at different cleaner
steps name different resume sites. The global group sets can be small
while pending point rows are large. The measured thingsboard actor has
63746 such rows, with 59612 at one stage over 506 observed step indices.
That is actual site/image multiplication. It does not prove that all 506
steps have the same target/group or are eligible for one compaction.

Per-step work also has a concrete avoidable path. `MarkExclusions.plus`
normalizes an unchanged set, and GROUP projection checks its atom kinds
through synchronized `MarkTable` access. `ApOps.clean` installs the
run-1 output policy; the caller's collection path also normalizes the
result. The 36.08% OWASP and 45.51% conductor exact Java leaf shares for
normalization/projection establish substantial header work. They do not
give the number of redundant cleaner invocations.

There is also transient multiplication before the ordinary fact store,
as the next section shows. Base-only's in-clean `.distinct()` avoids its
corresponding equal compact-AP alternatives. Late `MethodEdgeStore`
deduplication cannot undo work already done inside the new call plan.
No actual project-wide intermediate duplicate ratio was measured.

This campaign has `disableRun1Statics=false`. The nested-cleaner example
needs no statics or ND edges, so disabling statics does not remove that
source path or the full-mark action expansion. The static continuation
counts describe the enabled campaign; they must not be presented as
measured owners in a disabled-statics run.

These source differences explain why fewer groups alone cannot provide
the expected performance bound. Their individual time factors remain
unmeasured. Thingsboard additionally has major actual form-construction
cost. Existing profiles do not determine its first-build/rebuild ratio.
The next proposal must address avoided action/intermediate work and
retained continuation sites, rather than assume that faster mark lookup
alone solves the regression. The pinned comparison is
`validation/base-only-cleaner-comparison.*`.

## Why a small group count does not bound this work

A source-bound scalar control follows the production nested-cleaner
lowering. One cleaner can retain the original fact and produce a rewritten
fact. Both enter the next cleaner before `MethodEdgeStore` admission.
With 12 actions, the control has 4096 intermediate entries.

For GROUP TAINT, selected cleaners cannot kill the group atom. The control
can have one complete key repeated 4096 times, including when the 12
actions select different groups. Exact per-step deduplication reduces it
to one. For FLOW, distinct groups can produce different mark-exclusion
subsets. Those are different complete keys: exact duplicate elimination
must not remove them. With K groups, a subset domain can contain 2^K
members. Fifty groups do not mean only fifty possible headers.

These are executable source-bound controls, not an actual project action
census or measured duplicate factor. The heap establishes the large
persistent continuation population. The bounded census measures its
site/image shape, and the profiles measure time samples and allocation
weights. They do not count actual intermediate list duplication. No
repeating identical engine state or infinite loop has been proved.

## Next iteration and completion status

The [next proposal](../design/bidirectional-cleaner-intermediate-work-20261021.md)
separates static continuation image packing, GROUP return-rewriter
compaction, exact intermediate cleaner deduplication, immutable
exclusion-header reuse and empty summary-copy avoidance. A separate FULL
candidate prevents repeated replay of already authorized summaries.
A separately selectable construction follow-on reuses per-position lowering
work. Each candidate states current/new behavior and its proof scope.
No further runtime change has been made during this investigation.

Closed work: implementation and commit; test/source revalidation; all
configured project pairs; final source/input equality; strict finding
comparison and missing-location explanation; both first-pass OOM readers;
both bounded owner/key censuses; all eight CPU/allocation readers; the
base-only source comparison; and the ranked next-iteration proposal.
Unavailable fields and missing PDF recording intervals remain explicit.
The source models prove work amplification paths, not their actual
project-wide multipliers. No measured cache-hit/rebuild ratio, infinite
cycle or universal no-FN certificate is claimed. The output and performance
targets failed. Completing this investigation does not turn those failures
into a passing analyzer result.
