# Added-fact removal: project measurements

Date: 15 October 2026. Status: scans and heap reviews complete; full recording review
and PDF finding diagnosis remain open.
Measured runtime: `27e22bb1ed871651143538783473cb0d4fcbcf3e`.
Fresh main: `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.

The current engine does not meet the project acceptance gates. All four main scans
finish. The current scans end with one timeout and three memory-guard stops. Only
Stirling-PDF completes run-1 within the 60-second engine target. The accepted exports
miss 17 strict main locations: 14 in ThingsBoard and three in Stirling-PDF.

This compares the current bidirectional engine with legacy main. It does not isolate
the effect of added-fact removal. Earlier optimization reports concern other runtime
commits and are historical evidence only. No runtime change followed the measured
commit. The separate [validation report](bidirectional-added-facts-removal-validation-20261015.md)
records the passing protocol checks and eight strict application precision failures.

## Latest iteration and run-1 target

Every limit starts at 1. An engine completion, an accepted forward report and overall
analysis completion are separate events. Main uses a legacy engine without run-N.

| Project | End | Latest reached | Last completed engine | Accepted forward report | Run-1 engine seconds | At most 60 seconds |
| --- | --- | --- | --- | --- | ---: | --- |
| OWASP | TIMEOUT | run-5, forward, limit 5 | run-4, backward | run-3 | 331.657 | No |
| Conductor | OOM | run-2, backward, limit 2 | run-1, forward | run-1 | 60.210 | No |
| ThingsBoard | OOM | run-1, forward, limit 1 | None | None | 279.104; incomplete | No |
| Stirling-PDF | OOM | run-4, backward, limit 4 | run-3, forward | run-3 | 36.102 | Yes |

The engine target excludes prescan, static metadata, catalog construction and the
report barrier. An incomplete run does not meet the target. Conductor narrowly
misses the strict bound. There is no supported claim that run-1 finishes on every
project within 60 seconds.

## Settings and evidence

All scans use Gradle `runProjectAnalyzer` directly. OWASP follows the selected CI
model with an 8 GiB heap and a 1,000-second IFDS budget. The selected Conductor,
ThingsBoard and Stirling-PDF models use 8/12/8 GiB and 1,080-second IFDS budgets.
Each has the fixed 1,200-second outer limit. Java is Temurin 17, IR batch size is
2,000, and multi-release jars are disabled. No other project configuration ran.

Evidence root, called `B` below:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/added-facts-removal/20261015T090757.961708Z
```

`runtime-binding.json` binds the two commits, commands, artifacts and validation
state. `combinedmatrix-terminal.json` and `report-preparation/terminal-timings.json`
bind all eight terminal runs. `report-preparation/strict-comparison-matrix.json` and
`optimized-review/four-project-findings-index-20261015T1102.json` bind all comparisons.
Per-run manifests, summaries, logs, SARIF and resource samples are under
`projects/<project>/{main,bidi}`. Full absent and added identities are in each
`optimized-review` packet; they retain method, instruction, rule, path and full span.

Before measurement, cleanup removed 79 owned old capture/index files, or
131,601,563,505 logical bytes. It kept compact old receipts and unrelated files.
The before and after input audits pass all 80 checks over 41 full-content datasets.
The after audit is bound by `input-audit/post-optimized-binding.json`. Endpoint
equality does not prove that no input changed between those observations. The audit
does not claim a new OWASP source build.

Scans run serially. CPU affinity exposes 20 processors. The inspected cgroup has
no CPU quota or throttle and no child memory limit. All ancestor limits and host
contention are not controlled. A separate test build overlaps main ThingsBoard
prescan. Process RSS includes native memory; it is not retained Java heap.

## Timing and outcome

| Project | Main command seconds | Main IFDS seconds | Main full-scan seconds | Current command seconds | Main / current peak RSS GiB |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 123.215 | 74.186 | 41.786 | 1084.210 | 6.159 / 9.562 |
| Conductor | 92.192 | 49.845 | 24.657 | 526.715 | 9.356 / 9.561 |
| ThingsBoard | 220.433 | 183.087 | 29.932 | 753.316 | 13.419 / 13.735 |
| Stirling-PDF | 55.108 | 22.137 | 5.869 | 522.393 | 7.825 / 9.522 |

Main full-scan time includes trace work. Current command time includes setup,
refinement runs and capture overhead. The current engine fails to finish all four
analyses under the selected budgets. Partial or OOM runs are not speedups. These
single instrumented scans do not isolate the cost of this optimization or remove
host-load effects.

| Project | Run | Direction | Engine seconds | Barrier seconds | Engine status |
| --- | ---: | --- | ---: | ---: | --- |
| OWASP | 1 | Forward | 331.656673109 | 3.740209324 | COMPLETE |
| OWASP | 2 | Backward | 195.338459230 | 0.928830789 | COMPLETE |
| OWASP | 3 | Forward | 80.674273345 | 4.847282328 | COMPLETE |
| OWASP | 4 | Backward | 191.772104009 | 0.770034708 | COMPLETE |
| OWASP | 5 | Forward | 49.955127067 | Not reached | TIMEOUT |
| Conductor | 1 | Forward | 60.209773046 | 0.600806495 | COMPLETE |
| Conductor | 2 | Backward | 301.732447746 | Not reached | OOM |
| ThingsBoard | 1 | Forward | 279.104154374 | Not reached | OOM |
| Stirling-PDF | 1 | Forward | 36.101783045 | 0.343056007 | COMPLETE |
| Stirling-PDF | 2 | Backward | 30.367706986 | 0.081585034 | COMPLETE |
| Stirling-PDF | 3 | Forward | 61.788185325 | 0.293335354 | COMPLETE |
| Stirling-PDF | 4 | Backward | 314.246454749 | Not reached | OOM |

Current prescan marker intervals are 72.513, 44.274, 263.531 and 27.090 seconds,
in table project order. Static metadata adds 67.492, 12.853, 104.504 and 8.912 seconds.
Catalog construction adds 0.207, 0.252, 1.989 and 0.126 seconds. These costs are outside
the run-1 engine target and still count toward overall analysis time.

## Strict finding comparison

| Project | Main unique / rows | Current unique / rows | Retained | Missing | Added | Current exact duplicate rows |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4112 / 4112 | 4112 / 9179 | 4112 | 0 | 0 | 5067 |
| Conductor | 7 / 7 | 300 / 300 | 7 | 0 | 293 | 0 |
| ThingsBoard | 14 / 14 | 0 / 0 | 0 | 14 | 0 | 0 |
| Stirling-PDF | 17 / 17 | 56 / 128 | 14 | 3 | 42 | 72 |

All retained identities keep the same source path and full region. Duplicate counts
compare canonical full JSON results within the current export; they do not prove
full-result equality across engines. Maximum current multiplicity is nine for OWASP
and 27 for Stirling-PDF. All current engine exits are abnormal, so the comparator
keeps its incomplete-input assessment. The table describes the actual accepted
exports, not completed analysis or established ground truth.

OWASP retains all main locations in accepted run-3. The fixed testcase oracle also
matches all 1,415 expected-true testcase/category pairs in both engines. Both report
515 of 1,325 expected-false cases, accounting for 600 strict sink identities. Another
1,783 identities have a testcase with a different CWE and 32 have no testcase oracle;
these remain untriaged. Testcase coverage does not validate every location or trace.
Parity with main does not establish low absolute false-positive counts.

Conductor retains all seven main locations in accepted run-1. It adds 293 unproved
candidates: 287 SQL, three path, two GraalJS and one reflection. Its internal frontier
has 301 DEMAND entries; one enriched virtual `Connection#createDispatcher` instruction
has no exportable Simple location and is rejected. This accounts for 300 SARIF rows,
not a missing file or one of the seven main sites. Parameterized SQL templates and
SQL-labelled file creation are precision suspects. They are not a validated label
for every added candidate. The candidates have no code flow.

ThingsBoard stops in run-1 before its first forward barrier and report update. The
initial empty committed report therefore remains empty. This is the immediate
export cause of all 14 strict losses. It does not prove that a sink was never reached
internally or identify a per-sink transfer defect. The memory analysis must explain
the workload that prevents the first report.

Stirling-PDF's accepted run-3 retains 14 main locations and misses three:

1. Command injection at `ProcessExecutor#runCommandWithOutputHandling(List,File)`,
   instruction 8, line 183 (`ProcessBuilder.start`).
2. XSS at `ConvertEmlToPDF`, return instruction 103, lines 133–134.
3. XSS at `PipelineController`, return instruction 50, lines 90–91.

The same two XSS methods have current candidates at later returns, lines 148–149
and 130. They do not replace strict missing locations. A Simple boundary result uses
the synthetic normal exit; the export fallback selects the method's last return.
This explains the displayed later locations. It does not prove that both engines
carry the same mark on the same earlier catch path. The earlier EML return handles
`IllegalArgumentException` with the original filename; the later return handles
`IOException` with the literal `File processing error`.

The command loss occurs despite an accepted run-3, so later run-4 OOM does not explain
it. Fresh main provides a request-to-getter-to-URL-append-to-executor trace. Current
frontier logs omit the individual source hits, source seeds and mark/record payloads
needed to locate its first failed propagation. Its exact root cause remains open.
Neither a zero-only omission from compact frontier maps nor a diagnostic from an
older runtime proves that cause. The PDF review packet records the precise missing
observations. The 42 added sites are 25 path and 17 XSS candidates.

## CPU capture scope

Continuous JFR exists for all eight runs. The normal CPU capture requests 30 seconds
at first-forward-engine start plus 60 seconds. Main full scans finish before this
trigger and before the near-limit trigger. No main normal/near CPU window exists.
Five current CPU captures complete. Their inclusive stack groups overlap and cannot
be added or treated as removable wall-time fractions.

| Project / capture | Actual engine scope | Samples | Selected inclusive stack groups |
| --- | --- | ---: | --- |
| OWASP normal | run-1 only | 3825 | Call-plan build 65.73%; sink build 31.35%; signed normalization 11.69%; native GC 22.30% |
| OWASP near limit | End of run-4, barrier, start of run-5 | 7694 | Native GC 61.04%; call-plan build 22.86%; sink build 14.34% |
| Conductor normal | Mostly run-2; run-1 ends as trigger fires | 26247 | Native GC 40.70%; call stage 23.77%; summary matching 8.14% |
| ThingsBoard normal | run-1 only | 25240 | Native GC 31.19%; call stage 29.86%; question replay 25.84%; request cursor 19.55%; call-plan build 12.23% |
| Stirling-PDF normal | End of run-2, barrier, start of run-3 | 19126 | Native GC 39.24%; call stage 20.78%; subtraction 4.90% |

Window alignment uses the captured timestamps and phase markers, with roughly
two-second scheduling uncertainty. ThingsBoard's window does not overlap its heap
dump. The Conductor and PDF windows cannot be presented as run-1 profiles.

Supplementary dumps preserve the same live JFR recordings for early-event review:
Conductor check/dump takes 0.900624 seconds in run-2; PDF takes 2.885515302 seconds in
run-4. Neither recording is stopped, restarted or reconfigured. These are captures
of the same fixed scan. Full run-1 coverage and CPU/allocation/GC export remain under
review. Overlapping recording samples must not be added.

## Run-1 work counts

These are terminal run-1 counters. Released end gauges do not describe peak live
state. A mark membership, a batch, an admission call and an input group are distinct
units. Counts do not establish retained bytes or removable fractions.

| Counter | OWASP, completed run-1 | Conductor, completed run-1 | ThingsBoard, OOM run-1 | Stirling-PDF, completed run-1 |
| --- | ---: | ---: | ---: | ---: |
| Input arrivals | 173372 | 1211565 | 7290577 | 156981 |
| Initial candidates / new initials | 295893 / 36592 | 2198351 / 103006 | 12372829 / 679576 | 254033 / 20459 |
| Peak pending answer shapes / marks | 6166 / 73300 | 49586 / 154170 | 282135 / 908638 | 39122 / 79910 |
| Peak active questions / mark cells | 6063 / 299471 | 53238 / 1552874 | 555409 / 112528596 | 10523 / 425188 |
| Request admission calls | 24589723 | 5707384 | 20998088 | 754451 |
| Duplicate admitted mark memberships | 24362548 | 18114379 | 1470265587 | 1534714 |
| Replay visits / full input tests | 93768 / 95403 | 7566821 / 7751226 | 155573488 / 161755355 | 318441 / 323718 |
| Largest replay candidate set | 14 | 533 | 10581 | 44 |
| Longest replay visit seconds | 0.019434 | 0.087296 | 4.097657 | 0.037309 |
| FLOW climbed mark memberships | 90693 | 14775368 | 1606614611 | 1288232 |
| Outgoing request batches / mark memberships | 3954 / 76250 | 406830 / 14366699 | 10083462 / 1567267716 | 25111 / 1230040 |
| Maximum batch width | 32 | 598 | 684 | 424 |
| N2 accepted deltas / created groups | 159475 / 48751 | 1026443 / 686675 | 6645277 / 5803738 | 127989 / 82725 |
| Peak queued N2 replay work | 3 | 10 | 10 | 7 |

ThingsBoard still has enormous request and caller-N2 work after removal of the callee
history. Its 112,528,596 active mark cells alone need about 450 MB of logical 32-bit
payload, before containers and sharing. This is a logical floor, not a measured
retained union. Pending concrete answers are a different, much smaller mark count.
Duplicate admission counts include catalog and dynamic work; they cannot all be
attributed to FLOW transport. The small queued replay count does not measure all
actor worklists or other queues.

The count of possible AP shapes is not the count of storage keys. N2 separates full
caller premises, call sites, contexts, admitted fact kind, layer, base, E and mx.
Concrete marks remain scalar in an InitialAp, while TAINT conclusion leaves already
pack multiple marks. Thus one scalar initial mark does not prove that every retained
payload carries only one mark. The entry-weighted cohorts below split original
premise kinds. They do not measure how many full N2 keys become equal after removing
only the initial mark, caller or path dimension. The report does not attribute the
whole group explosion to such an unmeasured quotient.

OWASP's normal CPU window instead points to form construction. Source inspection
finds repeated validation and signed NNF normalization in source and sink call-plan
construction. No measurement yet separates cold form construction from rebuilding
after soft-reference reclamation. Final counters span multiple runs and method
contexts, so dividing them by resolved-call counts is not a valid rebuild rate.

## Memory guard and heap analysis

The engine guard is 90% of heap, separate from the 99% report-barrier guard. Each OOM
has a live capture before cancellation. The capture interval is included in engine
and command time. HPROF file length is not reachable or retained heap.

| Project | Guard iteration | Threshold bytes | Capture seconds | HPROF bytes |
| --- | --- | ---: | ---: | ---: |
| Conductor | run-2, backward | 7730941132 | 31.873 | 11677651095 |
| ThingsBoard | run-1, forward | 11596411699 | 34.179 | 17856464803 |
| Stirling-PDF | run-4, backward | 7730941132 | 24.050 | 11835886270 |

OWASP reaches run-5 with timeout and has no observed OOM, so there is no guard heap.
ThingsBoard and PDF repeatedly clear soft references, recover just below the engine
threshold, re-enable caching and cross the threshold again. This is measured cache
pressure; it does not by itself authorize a reference-manager rewrite or prove that
the registry is the dominant owner.

Heap-owner analysis is complete for all three captures. The original Conductor MAT
query set has unsupported selected-root query arguments; its errors are preserved.
Corrected, versioned queries provide owner unions, class dominators, physical queues,
active questions, pending answers, caller N2, observer state and form owners.
The consolidated receipt is `report-preparation/heap-operator-completion.json`
(SHA-256 `939d2a486774094d44de6eb859115dadbaccf7cc0e1967874d8781df1c70449d`).
It binds all 13 terminal jobs and preserves query errors and field limitations.
Query completion alone is not query validity. Owner unions overlap and must not be
added or described as reclaimable savings. IR remains context only and is outside
the optimization scope.

The original Conductor review already verifies a complete 7,646,014,288-byte reachable
heap and a matching disjoint root-dominator partition. Its valid class-root unions
show 2,797,840,824 bytes in actors/runners/subscriptions, including a caller-N2 union
of 1,113,073,056 bytes. Forms/context/cache owners have 208,479,248 bytes; proof,
records and demand owners have 146,276,256 bytes. These unions overlap. The remaining
IR owner union is 2,252,111,536 bytes and is outside this iteration's scope.

Conductor's heap is in backward run-2. It has 17,465 current actors and no
`Run1Abstraction` instances. All 18,650 exact accumulators belong to caller N2;
observer accumulators are absent in this backward run. Its 219 physical event queues
contain three entries; the broader queue/channel owner union is 822,512 bytes.
Thus this capture does not support retained run-1 questions or a huge transport
queue as the cause of its later OOM. Caller-N2 and method state remain large.
`heap-review/optimized/conductor/job-20261015T105823.235286Z-244a29f602/review`
contains the original successful tables and all seven unavailable query errors.
The corrected Conductor follow-up reuses the same parsed capture and validates the
seven selector repairs. Independent complete censuses verify empty run-1 and observer
populations. Its versioned recipe and selector checks are under
`heap-analysis-v2-20261015T1112Z` and `heap-review-v2/optimized/conductor`.

The Conductor N2 directories have 1,265,465 input groups and 10,576,358 positional
postings. Their union contains 10,609,959 linked hash entries (424,398,360 shallow
bytes) and 9,315,073 boxed integers (149,041,168 shallow bytes). This is direct
evidence of a large index representation cost. It is not a proof that the full
573,439,528 bytes can be removed. The 33,601 zero-only entries and other owner fields
also occur in this union. The existing index stores a handle at each leaf position;
it does not duplicate that handle at every ancestor by construction.

Actor worklists in the same capture contain 5,315 pending deltas and 11 unchanged
repeat-set entries. Their list capacities total 620,180 cells. The three transport
entries and these local deltas are separate populations; neither explains millions
of retained N2 groups as an unprocessed-message backlog.

The later entry-weighted Conductor census partitions all 1,265,465 N2 groups:
86,629 have a single FLOW/STAR original premise, 9,292 have canonical zero, and
1,169,544 have a single nonzero concrete original premise. There are no PremiseSet
objects in the complete heap census, independently validating the empty ND selection.
The InitialAp dictionary covers all 6,519 objects. This is direct evidence of a large
concrete-premise relation in **backward run-2**, not a run-1 conclusion or a measurement
of concrete mark width in each conclusion. The ThingsBoard run-1 cohort below uses
bounds to account for its observer entries.

The ThingsBoard run-1 capture has 315,262,326 objects and 11,384,496,856 reachable
bytes under default MAT. Its complete class histogram and disjoint root-dominator
partition agree. All 30 versioned queries export; the invalid `c.core` projection
and the projected-answer `a.answers.size` field remain unavailable. A CSV export
does not make an unsupported field valid.

| ThingsBoard owner selection | Bytes | Scope |
| --- | ---: | --- |
| Actors/runners/subscriptions | 4932731184 | Retained union |
| Caller N2 | 1714139208 | Retained union |
| Summary/publication/shared questions | 876307464 | Retained union |
| Local active questions | 575705904 | Retained union |
| Authoritative shared question roots | 510728744 | Retained union |
| Pending concrete answers | 98508808 | Retained union |
| Unflushed question builders | 55008512 | Retained union |
| Projected answer initials | 6938504 | Retained union |
| Observer state | 67253096 | Retained union |
| Immutable request mark arrays | 910444952 | Distinct selected arrays, shallow bytes |
| Forms/context/cache owners | 685849816 | Retained union |
| Spring provider | 1326426744 | Retained union |
| IR storage; out of scope | 1619116064 | Retained union |
| Queues/channels | 12756016 | Retained union |

These selections overlap, including the active and published mark populations.
Their bytes are not additive. The immutable-array selection does not count mutable
builder capacity or prove that all arrays hold the same contents. Default MAT
ownership is not a strong-reference-only proof or an estimate of net savings.

The captured N2 directories contain 5,803,728 groups and 9,235,116 postings. Terminal
END counters later record ten more groups/postings, so they are not an identical
frontier. There are 73,669 live run-1 abstractions and 808 canonical InitialAp objects
in the complete heap census. The latter is not an entry-weighted distribution of
concrete versus abstract premises. Local active question counters at capture are
555,409 keys and 112,528,596 mark cells. Pending answers contain 281,321 shapes and
908,274 marks; 38,353 question keys await publication.

The entry-weighted ThingsBoard census partitions all 5,974,480 accumulator entries
into 3,189,630 FLOW/STAR premises, 272,589 canonical zero and 2,512,261 single nonzero
concrete premises. The complete class census has no PremiseSet instances. Exact
address joins separate 5,803,728 N2 entries from 170,752 observer entries. Allowing
all observer entries in each category gives these independent N2 bounds:

| Original premise | N2 lower bound | N2 upper bound |
| --- | ---: | ---: |
| FLOW/STAR | 3018878 | 3189630 |
| Canonical zero | 101837 | 272589 |
| Single concrete, nonzero | 2341509 | 2512261 |

The bounds are not one joint partition. They show a large concrete-premise cohort
and a larger abstract FLOW cohort in run-1. The 1.567 billion transported concrete
mark memberships therefore must not be confused with 5.804 million stored concrete
inputs. Marks are already packed within a conclusion; original premise dimensions
remain separate. A quotient that removes only the premise mark still needs exact
full-key comparison to measure its possible merge rate. The cohort packet is under
`heap-review-n2-cohorts/optimized/thingsboard/job-20261015T115054.035400Z-e84cfabea5/review`.

The physical transport queues contain 12,112 entries and actor worklists contain
83,995 deltas (625 unchanged and 83,370 other), plus 4,029 repeat-set entries.
Transport capacity is 814,838 cells; actor list capacity is 2,282,319 cells. These
are separate from millions of retained N2 groups. Pending work matters for service
delay, but the measured queue union is much smaller than the standing core state.

Source review finds separate cumulative mark-set construction in the local emitter
and shared publication owner. Reusing a checked immutable cumulative set at the
existing publication seam is a new possible optimization. It must preserve fresh
delta notification and visibility order; the measured overlapping owner sets are
scope evidence, not a predicted 575 MB plus 510 MB saving.

The complete ThingsBoard review is under
`heap-review-v2/optimized/thingsboard/job-20261015T111545.125451Z-68d4429b2d/review`.
The completed exact N2-index follow-up is included below.

Stirling-PDF's backward run-4 heap has 229,940,380 objects and 7,619,516,752 reachable
bytes. Its complete histogram and disjoint dominator partition agree. It has 7,258
call-service owners, all with null observer state, and no Run1Abstraction instances.
All 7,481 accumulators belong to N2 by address and contain 1,112,249 groups.

The entry-weighted census partitions those groups into 14,638 FLOW/STAR premises,
3,283 canonical zero and 1,094,328 single nonzero concrete premises. These three
disjoint cohorts exhaust the entry count, so no entry has an ND premise. The heap
still contains 33 PremiseSet objects elsewhere; their existence does not contradict
the entry partition. The ND query exports a no-result error, which is preserved;
that error alone is not used as evidence of zero. The InitialAp dictionary contains
all 3,052 objects. The packet is under
`heap-review-n2-cohorts/optimized/Stirling-PDF/job-20261015T115828.523739Z-dddae51f5a/review`.

| Stirling-PDF owner selection | Retained union bytes |
| --- | ---: |
| Actors/runners/subscriptions | 5028103544 |
| Caller N2 | 730315024 |
| Summary/publication/shared questions | 221118800 |
| Forms/context/cache owners | 16302120 |
| Proof/records/demand | 180050272 |
| Spring provider | 214972656 |
| IR storage; out of scope | 982411008 |
| Queues/channels | 488640 |

These unions overlap. The forms selection has a small retained union in this
capture; that does not establish low form allocation or include every shared
JIRMethodEntry/alias object. There are three transport events and 85,600 actor-local
pending deltas, with 404,029 local list capacity cells. Later-run retained method
state is much larger than these queues.

The actor union contains 8,846,449 ConclusionGroup objects, 17,729,410 HashMap objects
(851,011,680 shallow bytes), and 8,846,449 boolean arrays (212,314,776 bytes).
Source constructs both fact-kind maps and a two-element REACH array per conclusion
group. All production group owners enforce that conclusion kind follows a fixed
premise. Thus one map is unused in each such group. This supports a targeted container
proposal; it does not establish that the required conclusions can be discarded.
The full PDF review and explicit empty-receiver checks are under
`heap-review-v2/optimized/Stirling-PDF/job-20261015T113038.584625Z-17b317d7bf/review`.

The exact N2-index follow-ups confirm 703,625,352 retained bytes in Conductor,
849,281,176 in ThingsBoard and 521,086,640 in Stirling-PDF. Conductor's index contains
10,576,358 linked hash entries (423,054,320 bytes),
9,315,073 boxed integers (149,041,168 bytes), and 83,509,032 bytes of hash-node arrays,
plus trie and set metadata. This is a measured auxiliary owner, not a net saving
estimate. The versioned follow-ups are under `heap-analysis-n2-index-20261015T1131Z`
and `heap-review-n2-index`.

The final cache query selects the union of normal call, entry and exit sink
`methodItems` roots. It excludes the separate `allRelevant` view.

| Project / captured iteration | Normal sink-cache retained union bytes |
| --- | ---: |
| Conductor, run-2 | 320730448 |
| ThingsBoard, run-1 | 1092020512 |
| Stirling-PDF, run-4 | 151316032 |

These are fresh measurements for the current runtime. They include selected and
discarded bound rules, so they are neither discarded-only bytes nor net savings.
They overlap the broader Spring-provider and other owner selections. Their recipe
is `heap-analysis-normal-sink-cache-20261015T1148Z`; completed query packets are
under `heap-review-normal-sink-cache/optimized/<project>`.

## Open gates

The project acceptance gate fails. The application precision gate still has eight
failures, ThingsBoard has no accepted report, and PDF has three strict location
losses. The heap review is complete; full recording review is required before the
next optimization ranking is final. PDF command propagation and the relation between
its two earlier XSS paths and Simple output still need direct evidence. A proposal
must keep these unknowns separate from measured memory or CPU costs.

The [next core proposal](../design/bidirectional-next-iteration-27e22bb1e-20261015.md)
separates persistent-memory changes from duplicate-work changes. Its ranking remains
provisional while the recording review and finding diagnosis remain open. It authorizes no new
optimization implementation.
