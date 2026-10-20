# Immutable mark groups: fresh project measurements

Date: 20 October 2026. Measurements ran from 19 to 20 October 2026.
Status: all eight fixed workloads, strict comparison and profile analyses
are closed. The implementation passes its local gate. Project acceptance
fails. This report separates measured results from proposed changes.

The measured implementation is `029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
The selected optimization is implemented and its 2,108-test gate passes.
The [implementation proposal](../design/bidirectional-immutable-mark-groups-implementation-20261019.md)
and [local validation](bidirectional-immutable-mark-groups-validation-20261019.md)
record its selected scope and proof/test limits.
The project acceptance gate fails: all projects miss the 60-second run-1
engine target. OWASP and conductor time out in run-1. Thingsboard reaches
OOM in run-1. Stirling-PDF completes run-1, accepts that report, then reaches
OOM in run-2. No project result supports an overall performance or no-FN
acceptance claim.

## Measurement method

The main reference is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
The test checkout is `bbdbcc5fd35d976d93348760ea60561e4670f68c`.
Use the full source/input equality receipt as the input authority. It passed
for 10 data sets, 177,499 regular-file memberships and 16 controls. Candidate
core/rule files were clean at the committed revision. Main has only the
recorded MemoryManager capture hook. Unrelated pre-existing workspace edits
were frozen for the study.

Evidence root:
`/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019`.

Important receipts are `validation/benchmark-guard/equality.json`,
`all8-closed-source-input-equality.json`, `validation/study-closed.json`,
each `launches/<project>/<side>/terminal.json`, and the strict comparator
receipt in `validation/final-finding-comparison`. A successful comparator
exit proves execution, not equal findings or complete analysis.

Each pair ran once, serially, through direct `:runProjectAnalyzer`, with
Java 17, defaultBatchSize 2000 and multiRelease false. No CLI or alternate
project configuration was used. Continuous JFR and the configured native
windows were enabled. Native normal windows request 30 seconds from engine
start +60 seconds. Near-limit windows request 30 seconds from IFDS timeout
-60 seconds. An early finish/OOM can make a window unavailable.

| Project | Maximum heap | IFDS timeout | Outer timeout |
| --- | ---: | ---: | ---: |
| OWASP | 8 GiB | 1,000 s | 1,200 s |
| conductor | 8 GiB | 1,080 s | 1,200 s |
| thingsboard | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | 8 GiB | 1,080 s | 1,200 s |

The approved project models, rule bytes, extension inputs and toolchain
referents are pinned in `validation/benchmark-guard/plan.json`. Historical
project reports are not measurement evidence for this commit. The study
does not include a fresh B88 candidate run, so it cannot isolate a whole-
project speedup caused by this optimization relative to B88.

Old raw snapshots/recordings were cleaned before this study. The cleanup
receipt records 29 old raw files and 45,902,933,650 bytes. Derived prior
reports and proof/test evidence were retained. New originals remain available.

## Time and reached iterations

Times are seconds. Invocation includes Gradle/runner startup and analyzer
result export. It excludes the later profile and heap inspection jobs.
Prescan and IFDS are logger spans. BIDI engine/barrier times are monotonic
durations. Do not subtract different boundaries to obtain unmeasured phases.
Main uses the legacy analysis and has no numbered BIDI iteration.

| Project | Side | Outcome | Invocation | Prescan | IFDS |
| --- | --- | --- | ---: | ---: | ---: |
| OWASP | main | OK | 117.199 | 32.443 | 75.532 |
| OWASP | candidate | TIMEOUT | 1,021.453 | 32.502 | 1,000.043 |
| conductor | main | OK | 92.167 | 24.891 | 49.334 |
| conductor | candidate | TIMEOUT | 1,120.670 | 24.951 | 1,080.064 |
| thingsboard | main | OK | 221.372 | 153.549 | 184.242 |
| thingsboard | candidate | OOM | 564.849 | 131.698 | 530.885 |
| Stirling-PDF | main | OK | 48.095 | 14.329 | 19.282 |
| Stirling-PDF | candidate | OOM | 837.487 | 17.881 | 802.667 |

The legacy full-scan logger spans are 43.082, 24.441, 30.689 and 4.952
seconds in table order. They are not BIDI run-1 engines.

| Project | Run | Direction / field limit | Engine | Engine result | Barrier |
| --- | --- | --- | ---: | --- | ---: |
| OWASP | run-1 | forward / 1 | 966.445 | TIMEOUT | none |
| conductor | run-1 | forward / 1 | 1,054.594 | TIMEOUT | none |
| thingsboard | run-1 | forward / 1 | 396.256 | OOM | none |
| Stirling-PDF | run-1 | forward / 1 | 487.083 | COMPLETE | 3.076 |
| Stirling-PDF | run-2 | backward / 1 | 294.391 | OOM | none |

| Candidate project | Latest reached | Latest engine COMPLETE | Latest accepted forward report | Run-1 <=60 s |
| --- | --- | --- | --- | --- |
| OWASP | run-1 | none | none | fail |
| conductor | run-1 | none | none | fail |
| thingsboard | run-1 | none | none | fail |
| Stirling-PDF | run-2 | run-1 | run-1 | fail |

Stirling-PDF's compact forward frontier proves report acceptance. A barrier
END alone would not prove it. The three early failures have no forward
COMPLETE and no accepted report. Their valid empty exports are a fixed-
budget/report-gating failure, not proof that each underlying flow was lost.

Declaration finalization took 5.336, 5.510, 5.660 and 5.624 milliseconds for
the four candidate projects. This measures finalization only. Serialized
metadata collection occurs in ordinary provider loading and is not timed
separately here. Tests/source establish that setup performs no ownership-
binding census and primitive GROUP filtering performs no member enumeration.
This local work avoidance did not meet the full-project resource targets.

## Strict finding comparison

Keep exact rule IDs, full overload signatures, sink fingerprints, raw spans
and occurrence counts. No identity remapping, END-related filtering or
return-site folding was applied.

| Project | Main unique | Candidate unique | Common | Missing | Added | Candidate occurrences |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 0 | 0 | 4,112 | 0 | 0 |
| conductor | 7 | 0 | 0 | 7 | 0 | 0 |
| thingsboard | 14 | 0 | 0 | 14 | 0 | 0 |
| Stirling-PDF | 17 | 1,656 | 15 | 2 | 1,641 | 61,895 |

The first three projects stop before the first accepted report. All 4,133
missing baseline keys in these rows share that measured resource/report
cause. This is not a semantic per-flow FN theorem and not a no-FN result.
Every main reference completed, and all SARIF files are valid.

### Two strict Stirling-PDF return-site gaps

Both missing keys are `java.security.xss#sink#1`. Fresh main traces report:

- `ConvertEmlToPDF#convertEmlToPdf`, instruction 103, `return %54`, lines
  133–134. Candidate Simple observations select final return instruction 126, `return %89`, lines 148–149.
- `PipelineController#handleData`, instruction 50, `return %104`, lines
  90–91. Candidate Simple observations select final return instruction 109, `return null`, line 130.

The exact full signatures, fingerprints, raw spans, main trace witnesses
and candidate alternatives are retained in the fresh return-location review.
Each candidate alternative has 74 occurrences and no codeflows. They remain
different strict identities.

The current export path passes no relevantLocations for a Simple trace.
`JirSarifGenerator.statementLocation` calls normal generated-location
resolution, then `TraceMessageBuilder.tryResolveExitLocation` selects the last
return when no earlier location anchors the exit. This explains the observed
export-location difference. It does not prove that the same source/path is
represented, or that the earlier flow is semantically lost. Do not claim
either conclusion. Both source files are available in the current model.
The earlier PipelineController source-availability watchlist does not apply
to this study. No END-action cause was established for either gap.

The exact LicenseKeyChecker `Files.exists` key at instruction 14, line 98 is
present once with the same raw location set and no multiplicity change.
This confirms that strict observation; it does not prove preservation of
all eight source paths shown by main.

### Additions and export limits

The 1,641 new Stirling-PDF identities span 834 full methods and 251 reported
path strings, with 61,875 occurrences. Four shared identities add five more
occurrences, for 61,880 Counter additions. Keep this multiplicity difference
separate from unique keys and raw spans. The detailed per-rule/method/path
breakdown is in `reviews/fresh-finding-review/pdf-additions-breakdown.json`.

These observations come from accepted forward run-1: 0 CONFIRMED and 62,420
DEMAND internal vulnerabilities. Run-2 stopped with OOM. They are unrefined
candidates, not a validated count of 1,641 false positives.

All 62,420 attempted traces are Simple. 525 have invalid export locations,
leaving 61,895 SARIF results. No trace-generation exception was reported.
Do not treat an invalid-location rejection as a successful export or as
absence of the internal candidate. Main generated 17 results with no invalid
locations. The earlier three candidate runs attempted zero traces because
they had no accepted report.

The new core deliberately ignores trackFactsReachAnalysisEnd in every run.
Legacy main retains its own policy. This difference does not authorize
filtering baseline findings, and no missing identity was attributed to it
without an actual dependency witness.

## Work counters and finite redundancy

The terminal request counters are incomplete-run prefixes except the
completed Stirling-PDF run-1. They are not logical fact counts or byte totals.

| Run-1 prefix | Request offers | Offered memberships | Temporary duplicates | Peak temporary members | Broad request scans |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 31,048,023 | 31,061,401 | 30,990,510 | 34,410 | 0 |
| conductor | 3,327,792 | 3,384,651 | 2,966,389 | 2,567 | 0 |
| thingsboard | 1,571,690 | 1,612,574 | 981,631 | 4,303 | 0 |

The temporary request store already deduplicates. OWASP rejects 99.77% of
offered memberships there. These counters identify repeated production
before admission; they do not measure duplicate PlanFacts or attribute all
CPU to requests. Direct request matching and small pending stores do not
support a proposal to add a new request index or change the barrier here.

The call pipeline has a finite source witness: k selected cleaners on the
same field/group retain only two distinct continuation facts but carry a
raw list with k duplicate field branches. The loop performs k(k+1)/2 input
visits instead of 2k-1 with exact per-step coalescing. The method store dedups
later. This is quadratic work from linear list growth, not an exponential
or infinite-loop proof. Its actual workload frequency is not measured.

There is also repeated GROUP projection and exclusion normalization inside
nested AP/actor output adapters. An already-normalized same-catalog value
is normalized again. The fresh OWASP native windows and source chain support
investigating identity paths; they do not prove an exact whole-run speedup.

Progress counters continue on all projects. OWASP has sampled flat event
spans up to 80 seconds while native CPU shows work inside cleanChain. A flat
event sample is not absence of internal work. The current evidence does not
prove an infinite loop or whole-program termination.

FLOW storage already has complement joins for equal geometry. Incremental
same-header growth can bypass that join and leave equal final roots under
different exclusions. Different geometries and complete premises can also
retain different rows. The strong AP exclusion interner retains visited
sets. With G groups the subset universe can be exponential in G. These are
source-level possibilities, not observed populations or memory attribution.

## Profile coverage and interpretation

All eight profile analyses are closed. Their selected summary/export counts match in all 56 checks. The surviving chunk headers are contiguous. Reported DataLoss, stack truncation and 256-frame print-cap hits are zero. These checks do not prove complete workload retention, uniform sampling or complete native/JIT stacks. Missing-frame counts and exact input hashes remain in the [all-eight validation table](/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/reviews/all8-derived-profile-validation.md).

Java execution samples are not CPU seconds or a total-process CPU breakdown. Allocation weights estimate allocation for retained events; they are not retained memory. Inclusive families overlap and include callbacks. GC collection elapsed sums can overlap; the pause-interval union is a separate measure. Monitor durations sum blocked-thread time and can exceed wall time.

Native profiles have requested nominal bounds, without exact per-sample times or a bounded attach delay. Native normal and JFR after 60 are close requested windows, not identical observations. Native near-limit is not JFR final 30. The OWASP overlap is only 0.871 s; the Conductor overlap is 0.074 s. No near-limit native capture exists for ThingsBoard or PDF.


## Closed OWASP profile

Seven finalized JFR chunks cover the full logged run-1 interval. The seven
selected event summary/export counts match. Reported DataLoss, truncation
and print-cap-hit counts are zero; these checks do not prove lossless
sampling or complete native stacks.

Run-1 has 72,382 Java execution samples and an estimated allocation weight
of 1,983,676,801,704 bytes. The whole recording estimate is
2,097,196,890,464 bytes and includes other phases. These scopes overlap.
Neither value is retained heap size.

| Run-1 Java leaf frame | Samples | Share |
| --- | ---: | ---: |
| MarkExclusions.normalized | 19,146 | 26.45% |
| MarkTable.key | 15,233 | 21.05% |
| MarkTable.isGroup | 11,368 | 15.71% |
| MarkTable.group | 2,421 | 3.34% |
| MarkExclusions.projected | 2,211 | 3.05% |

cleanChain occurs on 95.25% of run-1 Java sample stacks. The normalization
family occurs on 71.49% and on 73.09% of estimated allocation weight.
Inclusive values overlap and include callbacks; do not add them or call
them self CPU. Request-union samples are 0.74% inclusive and direct-static
service samples are 0.047%. Static-derived work can execute in generic
code, so this last classifier is not a complete static-origin attribution.

The largest allocation-class marginals include Object arrays (21.29%),
LinkedHashMap.Entry (20.73%), int arrays (9.71%) and boxed GroupId (7.93%).
They are consistent with the collection-heavy normalization source path.
Class marginals do not prove every such object belongs to normalization.

Run-1 has 1,384 recorded collection events, a GC pause-interval union of
4.569 seconds and three recorded monitor events with 0.0479 blocked-thread
seconds. Recorded long-monitor blocking is small. Short contention and
concurrent GC CPU are not bounded by these totals. This profile supports
cleaner/normalization execution as the primary Java sample cost, not a claim
that GC or synchronization has zero cost.

Both requested native windows were captured in run-1. Normal has 3,941
samples, with normalized/key/isGroup leaf shares of 8.15/5.89/8.93%.
Near-limit has 3,011 samples, with shares of 26.77/20.69/14.48%. Their native
GC union shares are 12.21% and 0.56%. These are window populations, not
whole-engine CPU seconds.

The native near-limit nominal interval is 23:16:03.367–23:16:33.367 UTC.
The JFR final-30 interval is 23:16:32.496–23:17:02.496. They overlap by only
0.871 seconds. Keep their labels and populations separate. Actual native
sample timestamps and attachment error are unavailable.

No eligible-member classifier matches the decoded samples. Sample absence
does not prove zero calls; source deletion separately proves removal of
the old enumeration APIs. key/isGroup are also used by generic projection
and exclusion handling. Their presence is not evidence of primitive member
filtering or an exhaustive attribution of every catalog call.

Full derived analysis and a scoped independent review are retained at
`validation/profile-analysis-real/OWASP-bidi/analysis.json` and
`reviews/owasp-closed-profile-review.md`.


## Conductor: catalog work, monitor spinning and sparse Java samples

Conductor times out in run-1. Its JFR covers the full logged engine interval. It has 11,790 Java samples in the whole retained recording, but only 4,369 in run-1. The selected export counts match and there is no retained header gap. This does not explain the sparse sample population or make it a fixed-rate work counter. In particular, low Java sample count does not establish GC dominance.

In run-1, normalization is 13.48% of Java leaf samples and 14.88% inclusive. The broad projection family is 25.73% inclusive. The estimated run-1 allocation weight is 574.804 GB(decimal): normalization occurs on 48.65%, projection on 78.26%, and cleanChain on 90.52%. These overlap; cleaner and policy frames include downstream callbacks. No exact redundant-call fraction follows.

The native windows expose work outside the Java sample population. TrySpin is the leaf on 35.37% of normal samples and 30.67% near-limit. Normalized leaf shares are 13.62% and 11.07%; its inclusive shares are 81.58% and 63.14%. Native GC unions are 3.71% and 11.32%. JfrStackTrace.record_safe is itself sampled at 8.14% and 8.56% leaf. Recording overhead is visible, but its total effect is not measured.

The whole-recording 7,402 monitor events chiefly describe prescan. Prescan alone has 6,718 TaintRulesStorage events and 117.516 blocked-thread seconds within 24.951 wall seconds. Run-1 separately has 652 events and 44.756 blocked-thread seconds; MarkTable accounts for 528 events and 42.365 seconds. Its leading named wait frames are isGroup and key. The run-1 GC pause union is 2.975 s, distinct from 126.914 s of overlapping collection elapsed intervals.

The source synchronizes MarkTable reads and repeatedly invokes them from exclusion normalization and typed conversion. This supports a bounded investigation of repeated same-catalog work. It does not attribute every native spin to MarkTable or justify a lock-free redesign. The derived tables contain no exact spin-by-catalog stack intersection.

Evidence: [Conductor profile review](/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/reviews/conductor-closed-profile-review.md) and its bound JSON.

## ThingsBoard: service work and generic form/cache paths

ThingsBoard reaches OOM in run-1. JFR covers that full logged interval. It records 21,405 run-1 Java samples. Cache get/build occurs on 43.39%, direct static service/resume on 38.54%, and the narrow form-compilation family on 9.14%, all inclusive. Direct leaf observations include service.commit 4.89% and service.intersect 4.05%. The generic AP/form/cache categories do not identify all work by its static or ordinary origin. FormsCache.withForm includes callbacks; payload/get/build is not solely construction cost.

The estimated run-1 allocation weight is 616.987 GB. Direct static service/resume occurs on 35.06%, projection on 26.13%, cache get/build on 25.84%, and normalization on 19.74%. These overlapping weights do not identify retained owners. Object/int arrays, collection objects and Pattern are prominent class marginals; they are not an object-ownership census.

Only the normal native window was captured. Its 26,747 samples have TrySpin 28.25%, normalized 9.87% and JfrStackTrace.record_safe 5.08% leaf shares. GC is 16.20% of this native window. Normalization is 58.34% inclusive and direct static service/resume 8.28%. The close JFR after 60 window has different phase bounds and a Java-only population, so its shares need not agree.

The run-1 GC pause union is 32.690 s. Its monitor events sum 961.594 blocked-thread seconds, including named service.commit waits of 619.255 s and MarkTable-class waits of 294.389 s. Thread durations overlap and some event stacks are unavailable. They do not measure equivalent wall delay, lock-held CPU or an avoidable-work fraction.

The final 30 JFR window includes the actual 27.172 s heap capture, 90.57% of that window. It has only 14 Java samples. Its long MarkTable wait events can include capture/safepoint interference. Do not present this window as steady-state engine activity or infer a normal lock duration from it. The near-limit native window is unavailable, not zero work.

Evidence: [ThingsBoard profile review](/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/reviews/thingsboard-closed-profile-review.md), its bound JSON and the [closed snapshot markers](/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/reviews/closed-snapshot-markers.json). Retained-owner interpretation belongs in the separate heap chapter.

## Stirling-PDF: run-1 native evidence and a late run-2 JFR suffix

PDF completes and accepts run-1, then reaches OOM in backward run-2. Its retained JFR contains only 00:01:12.423–00:05:22.411 UTC on 20 October, about 250 s. Prescan and all of run-1 are outside this range. **Run-1 JFR execution, allocation, GC and monitor values are unavailable, not zero.** The normal native window is separate retained evidence from run-1.

That native run-1 window has 22,810 samples: TrySpin 32.53%, normalized 8.36% and JfrStackTrace.record_safe 7.18% at the leaf. Projection is 89.69% inclusive, normalization 47.52%, and cleanChain 84.38%. Native GC is 7.51% in that window. Inclusive ancestors overlap and include callbacks. There is no all-run-1 JFR CPU or allocation estimate to pair with these values.

For run-2, the first 52.913 s is not retained. Header coverage is 241.478 of 294.391 logged engine seconds. The file also extends 8.510 s past engine END. Its available run-2 events include 73,610 Java samples and an estimated 1.995 TB of allocations. These are late-run-2 observations, not a full-run total or retained heap size.

The late backward phase has TrieOps.subtract at 10.01% of Java leaf samples, summary-entry iteration at 6.67%, and sequence concatenation at 5.19%. The AP/trie/check/edge family is 42.13% inclusive Java and 59.99% of allocation weight. Allocation class marginals include int/Object arrays, iterators, TaintLeaves, MarkSet and SubtractKey. Broad projection is 5.18% Java but only 0.30% allocation. Its classifier includes general exclusion methods; it does not prove GROUP conversion in a backward FULL phase. This evidence must not be used as run-1 cleaner attribution.

The available run-2 collection events have a 64.856 s pause union. Their elapsed sums include overlapping concurrent collections and may carry duration intervals that begin before the first surviving chunk header. They cannot reconstruct the missing prefix. Recorded monitor duration is 100.734 blocked-thread seconds, chiefly named SummaryStorage.candidates waits (97.124 s). These are late-run-2 observations, not F1 catalog contention.

Run-2 final 30 contains the 18.398 s heap capture, 61.33% of that window. Its 1,467 Java samples, GC events and waits are capture-affected. The pause union does not measure all snapshot/safepoint interruption. No near-limit native window is available.

Evidence: [PDF profile review](/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/reviews/Stirling-PDF-closed-profile-review.md), its bound JSON and the closed snapshot markers.

## Main reference profiles: keep fullscan separate from prescan

All four current main analyses are OK and their JFR headers cover the logged IFDS/fullscan intervals. Main has no numbered BIDI runs. No native artifact is present for main. The table uses the legacy fullscan window, not the whole recording or IFDS envelope. Prescan is included in IFDS and must not be added to that envelope again.

| Main project | Prescan s | Legacy fullscan s | IFDS s | Fullscan Java samples | Estimated fullscan allocation GB | Fullscan GC pause union s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 32.443 | 43.082 | 75.532 | 4,215 | 70.759 | 1.101 |
| Conductor | 24.891 | 24.441 | 49.334 | 3,077 | 37.251 | 0.747 |
| ThingsBoard | 153.549 | 30.689 | 184.242 | 7,483 | 83.566 | 2.090 |
| Stirling-PDF | 14.329 | 4.952 | 19.282 | 1,202 | 9.943 | 0.155 |

Main fullscan Java leaves emphasize bytecode method lookup in OWASP/Conductor/ThingsBoard, while PDF's leading leaf is rule-based summary rewriting (16.72%). ArrayList.grow is the leading allocation leaf in all four fullscan windows. These sample populations are not directly comparable CPU denominators for candidate engine phases, which perform different work and may stop early.

The [all-eight validation table](/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/reviews/all8-derived-profile-validation.md) preserves exact byte weights, count checks, top leaves, missing-frame units and all eight input hashes. Allocation estimates do not establish retained memory, and no profile share establishes a whole-project speedup or no-FN result.

## Actual OOM memory snapshots

The capture hook runs from MemoryManager.handleOOM before cancellation and
release. HotSpotDiagnosticMXBean.dumpHeap uses live=true. These are live
analysis snapshots at the configured memory guard, not a claim of a JVM
OutOfMemoryError or a post-completion leak. Snapshot time is included in the
reported engine duration.

| Candidate | Actual phase | Guard heap used bytes | Capture time | HPROF bytes | Complete reachable histogram: objects / shallow bytes |
| --- | --- | ---: | ---: | ---: | --- |
| thingsboard | run-1, forward, limit 1 | 11,630,918,656 | 27.172 s | 18,413,106,003 | 355,639,042 / 11,425,724,784 |
| Stirling-PDF | run-2, backward, limit 1 | 7,758,379,288 | 18.398 s | 12,201,737,237 | 237,110,008 / 7,592,829,592 |

Raw HPROF size is not retained heap. The marker receipt binds actual start,
end, phase and log bytes. Installed MAT 1.17/Java 21 runs one owner report per capture, with a separate 24 GiB reader budget. Workloads and
readers use the same three serialization locks. These reader settings are
not analyzer benchmark settings.

Each report has 36 inventoried query outcomes. Thingsboard has 22 complete
CSV, 10 sampled header tables, two top displays and two empty/unavailable
selections. PDF has 21, 10, two and three respectively. There is no failed
query. A tool exit of zero is not query acceptance. Sampled headers do not
prove full owner populations. Empty selections alone do not prove zero live
objects. Complete Q01 histograms support the stated physical populations.

The selected retained unions below **overlap**. Do not add rows, compare their
sum with the heap, or read a union as uniquely recoverable bytes. Object
counts are physical objects, not logical fact counts or operation counts.

| Selected owner family | thingsboard run-1 bytes | PDF run-2 bytes |
| --- | ---: | ---: |
| Static service and saved static point owners, Q33 | 4,213,559,264 | unavailable; complete Q01 separately has no such live owners |
| Combined method actors and caller state, Q30 | 3,882,163,384 | 4,442,653,280 |
| Method-edge owners, Q08 | 196,369,032 | 2,401,613,672 |
| Raw/selected method summary owners, Q11 | 372,402,104 | 702,413,440 |
| Published-summary index, Q20 | 242,066,296 | 545,229,360 |
| Caller input owners, Q16 | 164,741,616 | 258,736,224 |
| Ordinary request stores, Q21 | 11,884,512 | 1,161,280 |
| Canonical AP vocabulary, Q23 | 1,259,256 | 88,844,728 |
| Soft trie interners, Q24 | 21,598,128 | 1,872,384 |
| Graph/form lifetime owners, Q25 | 664,773,256 | 235,447,976 |
| Bound rule-list owners, Q26 | 1,201,394,888 | 169,077,032 |
| Worklist/channel owners, Q29 | 40,560,696 | 35,429,688 |
| MarkTable and immutable declarations, Q36 | 458,248 | 452,536 |
| IR context only, Q31 | 1,619,576,464 | 981,616,256 |

IR is context only and remains outside the proposed optimization scope.
The ordinary request and interner rows do not support a request-index,
MarkTable-growth or reference-manager rewrite as the principal memory fix.

### Thingsboard: static registrations and saved continuations

The large run-1 auxiliary population is concrete:

| Class family within Q33 | Physical objects per named class |
| --- | ---: |
| StaticWriterRegistration and WriterRow | 3,010,244 each |
| Run1PointResume.WriterKey | 3,660,632 |
| PointKey, PointContinuation and its one-leaf FlowTree | 2,200,055 each |
| StaticInterest and InterestRow | 1,401,311 each |
| CallKey and CallContinuation | 249,926 each |

Each WriterRow constructs its own delivered map. Hash nodes, table arrays,
boxed IDs, Patterns and per-point relations are large parts of this same
union. Per-map occupancy and the unique complete-premise count are not in
these tables. They do not establish that all registrations are duplicates.
The complete histogram has no retained PremiseSet. An ND Cartesian product
is a possible source path, but is not the retained cause shown by this capture.

The source pipeline is:

```text
fresh bounded static summary -> ordinary summaries.add deduplication
  -> one WriterKey per owner / complete premise / output leaf / layer
  -> service WriterRow(owner,id), delivered binding map and input postings
  -> pool growth dirties each dependent writer
  -> candidateBindings for that writer's complete premise
  -> owner currentValues recheck and ordinary AP composition
  -> new actual group supply

static refinement request
  -> PointKey(site, original, current image, layer, call state)
  -> saved single-leaf continuation and service InterestRow
  -> actual writer supply
  -> ordinary AP relation at the saved site
```

The method store already deduplicates before registration. The remaining
multiplication is owner/output/site/binding state, not proof that identical
MethodEdgeStore inserts are retained. The actor can accumulate millions of
selectors and repair points with only 42 interned groups, 925 used FULL keys
and 968 atoms. A small vocabulary does not bound these qualified collections.

One bounded strong-root followup selects the actual service address
0x683dca0a8 from Q04. Both expanded all-reference and strong-reference reports
show RunManager.staticService and a main-thread Java local. The service alone
retains 1,935,036,968 bytes; the larger Q33 union also selects actor-held point
owners. The strong path proves live ownership at capture time. It is not a
ThreadLocal field or proof of a leak after release.

Bound runtime rule lists are a second material owner: 12,465,987 ContainsMark
and 3,736,749 TaintMethodExitSink objects occur within their retained union.
The ownership census is gone; normal runtime role/relevance caches remain.
Retained method-exit conditions do not imply execution of disabled end actions.
No individual source-rule identity or signed FULL fold may be removed to
reduce this collection without a separate equivalent design.

The graph/form owner row is mainly graph, alias and slot infrastructure.
Complete Q01 has only 11 ForwardReversePayload, 38 bidi StatementSummary and
seven CallPlan objects at capture. Do not call the entire 664.8 MB a live form
payload cache. It does not measure past form rebuilds or cache reclaimability.

### Stirling-PDF: backward method state and publication batches

This is a run-2 OOM. Run1StaticService and Run1PointResume are absent from the
complete histogram. Applying the thingsboard static explanation to this
snapshot would be wrong.

The heap contains 28,194,786 Publication wrappers, with 676,674,864 shallow
bytes. Actor/caller Q30 contains 28,141,646 of them; Q29 worklists/channels
contain 314; the merged PublicationIndex Q20 contains none. These views
have distinct selection boundaries. They do not prove that all wrappers
are queued events, unique summaries or semantically redundant.

Source supplies a specific retaining mechanism. RunMethodAnalyzer appends
a new Publication for each fresh selected piece to pending. A quantum yield
keeps that list. flushPublications runs after the method fact queue is empty.
A busy method can therefore retain a long delta history before the global
publication index gets an opportunity to merge it. Detached delivery batches
are another possible wrapper owner. The sampled actor headers are not a
complete census of pending list sizes, so their exact partition is still
unmeasured.

The large method-edge row, 2.402 GB, also shows real retained backward method
state. The late run-2 AP/trie/subtraction profile agrees that this phase is
not just static-service administration. Do not treat every byte of the
4.443 GB actor/caller union as publication wrappers or avoidable history.

The complete statement-directory selection Q07 contains 5,465,778
ConclusionGroup buckets and 10,931,556 HashMaps: two maps per bucket.
It also contains 5,772,866 TaintGroup buckets and 11,545,732 layer lists.
These match the current constructors. The maps occupy 524,714,688 shallow
bytes and the layer lists 277,097,568 bytes within Q07. MethodEdgeStore creates
a statement array per complete premise and a bucket at each reached cell.
ConclusionGroup is a storage bucket, not a rule mark group. This is another
source of fixed auxiliary overhead. Empty-kind/layer occupancy is unmeasured;
the counts do not prove that half the containers can be removed.

A second bounded strong-root followup selects UnitRunner 0x67d75e838 from
Q02. It retains 534,770,728 bytes. The expanded strong paths include its
runner coroutine, RunManager.runnerJobs and a main-thread Java local. This
confirms active runner ownership at capture, not a post-run leak or a field-
level partition of all publications.

### Evidence for the heap conclusions

Under the evidence root, the complete owner jobs are:

- `validation/heap-analysis-preparation/jobs/job-thingsboard-bidi-20261020T003041.911405Z`.
- `validation/heap-analysis-preparation/jobs/job-Stirling-PDF-bidi-20261020T004329.550903Z`.

Each preserves terminal.json, query-outcomes.json and review/Q01–Q36 CSV.
`validation/heap-analysis-real/terminal.json` binds both closed reports.
The actual capture bindings pin the source, phase, capture SHA and tool bytes.
The strong-path jobs are
`validation/heap-root-followups/roots-thingsboard-bidi-20261020T005222.399485Z`
and `roots-Stirling-PDF-bidi-20261020T005401.346840Z` in that directory.
Each preserves terminal.json, both complete R01/R02 TXT and their inventory.
Both tools exited zero, all recorded reader processes closed, binary/data
indexes stayed unchanged, and the known operational text log only appended.
The path reports are bounded to 20 paths; they are not all-path proofs.

Source-bound ownership reviews are in reviews/thingsboard-heap-review and
reviews/Stirling-PDF-heap-review. The additive expanded-path review is in
reviews/heap-strong-root-review. This report uses complete retained unions
and expanded paths, not unexpanded HTML, sampled capacities or allocation
weights, for its memory conclusions.

## Next iteration and acceptance

The [next implementation proposal](../design/bidirectional-run1-redundant-work-next-iteration-20261020.md)
ranks shared static binding admission, immutable GROUP normalization, local
call continuation coalescing, early merge of unpublished backward TAINT summaries and the
exact static repair-key equivalence. Each states current/new behavior,
source examples, storage, late-event rules, proof obligations and validation.
No new runtime change is included in this report.

The measurements show extensive finite work and large auxiliary collections.
They do not establish an infinite loop. The repeated-cleaner source witness
proves a scoped quadratic workload. The two heaps identify current owners;
no snapshot proves general termination or post-completion leakage.

The implementation target is complete, but the real-project target is not.
A later iteration must reduce the measured work and state, complete run-1
within 60 seconds of engine time, preserve strict baseline findings and
avoid a large unrefined report expansion. None of those project acceptance
conditions is claimed by the passing local tests or comparator exit status.
