# Run-1 origins, static routing and GROUP projection: measurements

Date: 21 October 2026. Status: **measurements and analysis closed**.
All four candidate commands ended with a resource failure.
Three completed run-1; only Stirling-PDF met the 60-second run-1 engine
target. Fresh main completed three projects and ended with a prescan
TIMEOUT on thingsboard. The campaign does not show an overall performance
win. Strict finding comparison, eight profile reads and all three live-heap
reviews are complete. The main costs differ by phase: Thingsboard run-1
retains static recipients and dependencies; the two run-2 OOM captures
retain large FULL edge stores and publication batches. Empty containers
add a large cost in both phases.

The implementation is committed in
`d7244c08cdaefbb157f42e4d89731d301b97e48f`. The fresh main baseline is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`, checked against remote main.
Main has only the reviewed MemoryManager snapshot hook. Its runtime patch
SHA-256 is `53f0abeeb0c98a16db86b8236b91899020fca14e799ff69a6739addf644b402f`.
Both versions have the opt-in hook. Previous reports are not current
measurement evidence.

Artifacts are in
`/drive-testcomp/opentaint-go-rules/run1-origins-routing-projection-20261021`.
The implementation receipt verifies all committed files against the
validated source hashes.

## Selected changes

1. After a complete run-1 rule binding, emit every contributing nonzero
   origin. Emit ZERO only for a complete permitted all-ZERO binding.
   Store result origin domains without enumerating their products.
   Keep complete sink witnesses and later FULL ND behavior.
2. Remove exact-class/field routing attempts that cannot match. Keep
   broad fallbacks and all successful service effects.
3. Reuse unchanged receiver-canonical GROUP leaves and wrappers. Remove
   the second projection only on the audited immediate source callback.

The first change corrects the semantic demand relation. It can add
candidates. The other two preserve that corrected behavior. Field
transfer, mark policy, request barriers and the run schedule are unchanged.
The projection helper preserves receiver-owned leaf identity when generic
value-based trie reuse would otherwise retain an equal foreign leaf.

## Latest iteration

OWASP has reached candidate **run-3** (FORWARD, limit 2). Its latest
COMPLETE engine is **run-2**; its accepted forward report is **run-1**. Main uses the
legacy analyzer, so bidirectional iteration numbers do not apply to it.

| Project | Latest reached | Latest COMPLETE | Accepted forward report | State |
| --- | --- | --- | --- | --- |
| OWASP | run-3 | run-2 | run-1 | TIMEOUT; command and report closed |
| conductor | run-2 | run-1 | run-1 | OOM; command and report closed |
| thingsboard | run-1 | none | none | OOM; command and report closed |
| Stirling-PDF | run-2 | run-1 | run-1 | OOM; command and report closed |

| Project | Main status | Main command, s | Candidate status | Candidate command, s |
| --- | --- | ---: | --- | ---: |
| OWASP | OK | 265.13 | TIMEOUT | 1095.43 |
| conductor | OK | 188.61 | OOM | 397.25 |
| thingsboard | TIMEOUT in prescan | 430.02 | OOM in run-1 | 739.13 |
| Stirling-PDF | OK | 104.38 | OOM | 753.66 |

## Fixed workload

The campaign uses Gradle `runProjectAnalyzer` directly, Java 17, the
configured project models and rules, and the conductor extensions.
Static handling is enabled. The candidate also uses the existing form
and optimization counters. Observer cost is not isolated.

| Project | Analyzer heap | IFDS timeout | Outer timeout |
| --- | ---: | ---: | ---: |
| OWASP | 8g | 1000 s | 1200 s |
| conductor | 8g | 1080 s | 1200 s |
| thingsboard | 12g | 1080 s | 1200 s |
| Stirling-PDF | 8g | 1080 s | 1200 s |

All eight main/candidate runs are serial. The harness uses continuous
profile JFR with a 2g rolling cap. It requests 30-second CPU captures
after one minute of full analysis and near the IFDS limit while analysis
is active. Phase markers identify nominal native request/phase overlap;
collapsed native samples have no individual timestamps.
The MemoryManager hook captures a live heap before OOM cancellation.
Snapshot intervals are identified separately within engine time below.

The final source/input equality check passed at 20:32:41 UTC. All eight
actual commands are closed, with no runtime source or configured-input
drift. The initial launcher stopped before any analyzer because available
host memory was below the existing 22 GiB floor. Only task-owned idle
build processes were released. The resumed controller used the same
source authority and fixed settings; it did not retry a measured leaf.

Before this campaign, 56 closed-study captures and generated indexes
were removed, freeing 98,603,759,069 bytes. The SHA-256 inventory and
cleanup receipt are in `preparation`. Compact reports, logs and analyses
remain. No active capture or user workspace file was removed.

The run-1 target is 60 engine seconds. Prescan and report-barrier time
are separate from that target. A resource-failed shorter command is not a
completed-analysis improvement.

| Project | Iteration | Direction / limit | Engine, s | Status | Barrier, s | Snapshot interval inside engine, s |
| --- | --- | --- | ---: | --- | ---: | ---: |
| OWASP | run-1 | forward / 1 | 368.811 | COMPLETE | 7.468 | — |
| OWASP | run-2 | backward / 1 | 465.213 | COMPLETE | 2.255 | — |
| OWASP | run-3 | forward / 2 | 86.287 | TIMEOUT | not run | — |
| conductor | run-1 | forward / 1 | 88.047 | COMPLETE | 3.374 | — |
| conductor | run-2 | backward / 1 | 125.261 | OOM | not run | 26.952 |
| thingsboard | run-1 | forward / 1 | 389.906 | OOM | not run | 56.460 |
| Stirling-PDF | run-1 | forward / 1 | 45.242 | COMPLETE | 4.090 | — |
| Stirling-PDF | run-2 | backward / 1 | 583.105 | OOM | not run | 37.312 |

Engine times are the reported monotonic durations. Snapshot intervals
come from paired wall-clock log markers. They are identified within
engine time and are not silently subtracted as a separate solver run.

Only Stirling-PDF meets the 60-second run-1 engine target. OWASP ends
with 86.237 s left for run-3 in the shared IFDS budget. Its last accepted
forward report remains run-1. The candidate prescan succeeds on all four
projects. Thingsboard main times out in prescan, then finishes its full
scan and report with a sticky TIMEOUT status. Its 14 locations are the
strict comparison reference; they do not establish complete baseline
flow coverage.

The candidate thingsboard OOM occurs before its first report barrier.
It exports an empty SARIF and has no accepted report. Conductor and
Stirling-PDF retain their accepted run-1 reports when run-2 fails.

| Candidate live snapshot | Phase | File bytes | Paired capture interval, UTC |
| --- | --- | ---: | --- |
| conductor | run-2 | 11,760,548,281 | 19:56:44.513–19:57:11.465 |
| thingsboard | run-1 | 18,070,469,775 | 20:16:12.923–20:17:09.383 |
| Stirling-PDF | run-2 | 12,180,499,742 | 20:30:48.676–20:31:25.988 |

File bytes and peak RSS are not retained-heap sizes. The host is shared
with unrelated workloads. These single paired runs are not replicated,
isolated speed measurements. The comparison is current bidirectional
core versus fresh main; it does not isolate the speed effect of the
three selected changes from all prior core changes.

| Main legacy phase | Prescan, s | Full scan, s |
| --- | ---: | ---: |
| OWASP | 62.404 | 96.493 |
| conductor | 51.205 | 53.205 |
| thingsboard | 324.629 | 31.058 |
| Stirling-PDF | 28.896 | 9.123 |

These main phases have no bidirectional iteration labels. Their logged
end markers do not override thingsboard's TIMEOUT status. A main full
scan is not a baseline run-1 engine.

## Strict reported locations

The comparison retains the full rule ID, physical path and span, and
logical coordinates. A separate view removes only source-root URI
syntax verified against each project model. Raw and model-normalized
counts are equal here. No coordinate row is unresolved.

| Project | Main unique | Candidate unique | Main included | Missing main | Added candidate |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 14,438 | 4,112 | 0 | 10,326 |
| conductor | 7 | 1,902 | 7 | 0 | 1,895 |
| thingsboard | 14 | 0 | 0 | 14 | 0 |
| Stirling-PDF | 17 | 1,666 | 15 | 2 | 1,651 |

The no-loss location gate fails on thingsboard and Stirling-PDF. All 16
missing identities have a source review. Thingsboard's run-1 OOM returns
before `report.add`; its fresh report builder stays empty. No forward
report is accepted. This establishes a report-admission/resource cause
for all 14 omissions. It does not show which internal witnesses existed.

Stirling-PDF stores method-exit sinks at a synthetic normal exit and
exports Simple traces. Without a relevant path, source resolution uses
the method's last return. Main's full traces identify two earlier returns:

- `ConvertEmlToPDF.java`: main return instruction 103 at lines 133–134;
  candidate return instruction 126 at lines 148–149.
- `PipelineController.java`: main return instruction 50 at lines 90–91;
  candidate last-return instruction 109 at line 130.

Both use full rule ID `java.security.xss#sink#1`. The observed candidate
locations agree with the exporter mechanism. They remain strict
omissions. Exact source-flow coverage is unproved, and run-2 OOM prevents
completed refinement. Same-method alternatives do not satisfy a location.

Added locations are conservative candidates until reviewed; their counts
are not proved FP counts. The candidate reports come from run-1 and have
zero CONFIRMED records.

Evidence: `validation/strict-finding-comparison` and
`validation/strict-finding-comparison-review`, with both identity views,
every missing raw row, source spans, full main traces, source hashes and
the exact report/export contract.

## Implementation checks

| Check | Result |
| --- | --- |
| Common suite | 1,713 pass |
| JVM suite | 438 pass |
| Query controls | 38 pass; three existing skips |
| Analyzer consumer build | pass |
| Constructive Lean audit | 26 statements; 20 executable controls; no classical choice |
| Broader application suite | 166 pass; nine existing failures; one skip |

The broader application fixtures had stale `1,2,3,...` limit lists.
Their test-only settings now use the existing `1,1,2,3,...` schedule.
No production schedule changed.

A separate pre-iteration control at `746573357` uses the same repaired
fixture settings. All 1,640 runtime source blobs match that revision.
All 176 test outcomes match the candidate. All nine failing expected
and actual rule-ID sets match. Both versions have 27 extra cleaner
candidates and no missing expected IDs in those assertions. Each failing
analysis reaches, completes and accepts **run-9**, then stops with
`POLICY`; the reports have zero CONFIRMED findings. This establishes no
new regression in these controls. It does not make the broader suite
green or prove the precise cause of each extra candidate.

The [closed comparison](/drive-testcomp/opentaint-go-rules/run1-origins-routing-projection-20261021/validation/application-regressions/comparison.md)
retains source checks and rule-ID differences. A separate existing
acceptance case documents a conservative exact-cleaner DEMAND residual.
It is related evidence, not a trace of every extra reported here.

Independent controls compare complete scalar bindings, old/new static
service effects and receiver-normalized projections. The explicit
two-slot control visits 100 origin memberships. The predicate control
visits 176 memberships instead of 7,680 scalar bindings. These are local
operation counts; they do not establish a whole-project speedup.

## Current CPU and allocation profiles

All eight JFR leaves pass their count, phase, source and chunk checks.
The sweep has closed and its readers have exited. Java shares below
are inclusive sampled stack ancestry. Allocation shares use sampled byte
weights. These families overlap; do not add their shares. They are not
invocation counts, CPU seconds or retained-memory sizes.

| Candidate run-1 | Java samples | Sampled allocation weight, GB | Cleaner chain: samples / allocation | Canonical GROUP walk: samples / allocation | Actual call-form builder: samples / allocation |
| --- | ---: | ---: | --- | --- | --- |
| OWASP | 15,257 | 538.946 | 36.55% / 49.87% | 17.05% / 30.30% | 22.84% / 15.63% |
| conductor | 9,033 | 205.808 | 22.73% / 41.83% | 9.68% / 23.64% | 17.06% / 10.48% |
| thingsboard | 33,229 | 801.814 | 9.56% / 19.60% | 6.44% / 16.48% | 36.12% / 22.22% |
| Stirling-PDF | 6,193 | 110.029 | 10.85% / 21.93% | 4.99% / 15.83% | 47.04% / 38.16% |

OWASP changes workload during run-1. At engine +60 to +90 seconds, actual
call-form construction appears in 87.37% of Java samples and 93.16% of
allocation weight. In the final 30 seconds, cleaner-chain ancestry is
52.16% / 64.39%, and canonical walking is 24.43% / 40.09%. The +60..90-second phase
window remains build-heavy. `FormsCache.withForm` encloses the
transfer callback, so its inclusive samples are not cache build time.

Conductor's +60 to +90-second run-1 intersection lasts only 28.047 seconds.
Stirling-PDF finishes run-1 before +60 seconds; that window is unavailable.
Do not borrow samples from run-2. Thingsboard's final 30 seconds have only
29 Java samples, with service statistics in 25. They cover OOM capture and
shutdown diagnostics, not a representative steady-state service hotspot.

Canonical output reuse still walks unknown GROUP inputs. Each walk starts
fresh traversal scratch. OWASP's canonical-walk ancestry contains 125.507
GB of sampled object-array allocation weight and 10.873 GB of
IdentityHashMap weight. These values overlap other attribution tables.
They show allocation churn; they do not show retained arrays or an exact
rate of unchanged inputs. FULL, ZERO/Reach and FLOW already avoid this
TAINT walk. Empty TAINT roots are prohibited, so an additional empty-TAINT
fast path has no valid input.

The new factored origin-predicate store appears in 0.039% of OWASP run-1
Java samples, 0.011% on conductor, one sample on thingsboard and 0.161% on
Stirling-PDF. This does not prove zero cost or an isolated speedup. Direct
static-service ancestry is 0.151%, 1.15%, 6.08% and 0.34%, respectively.
These direct frames do not attribute asynchronous work caused by statics.
Thingsboard point-resume ancestry is another overlapping 9.78% of Java
samples and 17.07% of allocation weight.

OWASP has 3,335,947 offered run-1 request memberships: 3,263,104 temporary
duplicates, 18,604 authoritative duplicates and 54,239 fresh memberships.
The sum reconciles. Yet the request family appears in only 0.367% of its
run-1 Java samples and 0.399% of allocation weight. Duplicate offers exist;
the current samples do not identify direct request handling as its main
CPU cost.

The later FULL runs differ. OWASP run-2 cleaner-chain ancestry is
62.38% / 63.97%. Stirling-PDF run-2 has 85,587 Java samples and 2,562.735 GB
allocation weight over 583.105 engine seconds. Its exact Java leaf shares
include `RunSummaryStore.entries` iteration (6.46%), `Sequences.plus`
(4.89%) and `RunMethodAnalyzer.addLink` (3.12%). These are observed summary
replay/iteration and FULL-set costs, rather than GROUP projection. Their
exact operation multiplicity still needs source and heap evidence.

The source audit confirms that summaries already batch when a method's
queue becomes empty. Each flush posts one Delivery per subscribed caller
runner, then expands that runner's matching subscriptions. Newly admitted
queries separately replay retained summaries. Scheduler “in flight” counts
protocol reservations, not facts. Thingsboard's observed 2,846,917-ticket
burst therefore cannot be used as a scalar-fact count or an infinite-loop
proof.

The native CPU captures corroborate the phase distinction. OWASP normal
and near-limit captures have 5,380 and 6,672 samples, with nominal run-1
and run-3 intersections. Conductor normal has 12,096 samples but only
27.138 seconds of nominal run-1 overlap. Thingsboard normal has 22,224
samples in a nominal 30-second run-1 interval. Stirling-PDF normal has
27,086 samples in run-2: `addLink` appears in 17.90% of inclusive stacks,
and trie subtraction in 15.78%. These overlap and use a different sample
denominator from Java JFR. The collapsed CPU denominator includes GC
worker samples; it is not a fraction of Java mutator work. Collapsed captures have no per-sample time;
request intervals establish nominal intersections only. Only OWASP has a
native near-limit capture. The other candidates fail before that capture
point. Main has an OWASP normal capture; other main captures are absent.
Continuous JFR supplies their observed full phases.

Current compact evidence is in `validation/profiles-current-v2`; source
interpretation is in `validation/source-notes/current-profile-interpretation.md`.
The [profile tables](/drive-testcomp/opentaint-go-rules/run1-origins-routing-projection-20261021/validation/source-notes/report-ready-profile-tables.md)
retain exact numerators, window intersections, recording hashes and checks.
The first decoder was explicitly stopped before any closed leaf because
its repeated frame classification was too slow. A separately sealed reader
uses bounded classification memoization with 75 passing differential
controls. It changes no analyzer run or recording. The original packet
and interrupted outputs remain preserved; only closed v2 leaves supply
profile conclusions.

## Live OOM heaps

All three captures have completed their MAT reviews. Whole reachable
thingsboard **run-1** heap is 11,368,979,336 bytes in 340,817,520 objects. This is a
physical heap census, separate from the 18,070,469,775-byte snapshot file.

| Thingsboard selected owner union | Retained-set bytes |
| --- | ---: |
| Static service and point-resume owners | 2,463,512,480 |
| Ordinary caller/publication/request owners | 774,488,520 |
| Method-edge owners | 711,439,472 |
| Raw/selected method-summary owners | 547,057,056 |
| Exact sink witness/join owners | 439,381,104 |
| New factored result owners | 12,563,040 |
| Immutable mark catalog/declarations | 459,472 |

Each row is one selected retained set. These sets overlap; they are not
an additive partition and must not be subtracted as exclusive buckets.
Top individual dominators also include IR storage (1,619,793,352 bytes),
the rule provider (1,277,070,904), and method entries (717,534,264).
IR storage remains outside the optimization scope. A bounded child view
places 1,276,834,520 provider bytes under `JIRTaintRulesProvider`.
Source identifies its strong per-method resolved FULL-rule caches. GROUP
rewriting occurs after FULL resolution. Method entries retain graphs,
aliases, form-owner slots and call metadata; only form payloads use soft
references. These totals are not wholly removable cache bytes. The two
literal provider/entry root queries failed and remain unavailable.

### Thingsboard retains recipients, not millions of global static facts

The committed global service has 88 field states and 42 interned groups.
Its field partitions contain 1,538 requested, 89 reached and 539 processed
memberships. Yet it has 2,066,298 pending interest rows and 911,533 FLOW
writer rows; 879,028 of those writers have static origins. Writer-admission
history retains 29,661,973 group memberships across those rows.

There are 72,519 method actors in the capture. This is not a distinct-source
method count. Their point-resume directories retain 1,614,699 WriterInputs,
574,921 PointRows, 497,224 call continuations, and 27,208 conditional joins.
WriterInputs occur in 15,257 owners; the largest has 1,243. PointRows hold
1,100,573 pending group images. Four initial identities account for 563,315
PointRows (97.98%). The four decoded field identities are:

| Initial object ID | Static field |
| ---: | --- |
| 190503028 | `ModelConstants.MOBILE_APP_STORE_INFO_EMPTY_OBJECT` |
| 190503061 | `ModelConstants.MOBILE_APP_VERSION_INFO_EMPTY_OBJECT` |
| 163881384 | `TenantId.tenants` |
| 163880378 | `TenantId.SYS_TENANT_ID` |

The first two are allocated object constants. `tenants` is a static
reference cache; `SYS_TENANT_ID` comes from `TenantId.fromUUID` through
that cache. All four field references are final; their objects need not
be immutable. These are not enum fields. The concentration is evidence
about retained initial identities, not proof that each recipient is
unnecessary or that final fields can be removed from alias processing.
The exact requested source/output quotient remains unmeasured.

The field/group request gate and per-writer admission already exist.
They offer each group to each writer row at most once, but do not merge
all pending recipients of the same answer. For example, summaries
`C.b/FLOW -> C.a/FLOW` and
`C.b/FLOW -> C.c/FLOW` can each create a separate source `C.b/G` waiter
when both outputs request G. This is writer-summary composition, despite
the absence of ordinary intraprocedural static propagation. The current
queries do not count equal `(owner, source initial, group)` acquisitions;
no saving estimate for merging them is established.

The bounded subowner queries give the following retained sets:

| Thingsboard run-1 selection | Retained-set bytes |
| --- | ---: |
| Field pending-interest maps | 750,281,128 |
| Actor WriterInput maps | 273,126,632 |
| Packed point maps | 281,700,976 |
| Call-continuation maps | 206,073,368 |

These sets overlap each other and the larger static selection. The
largest sampled WriterInput map has 1,243 entries. A strong root path
runs through `Run1PointResume`, its method actor, `UnitRunner.actors`,
`RunManager.runners` and the main thread. These are live run-1 dependencies,
not a detached static service surviving into run-2.

The 2,064,639 field pending rows each retain an InterestRow, a full
StaticInterest registration and a duplicate region/path chain. The field
answer path reads only registration owner/id, which its Key already has;
it uses the field state's canonical region. It also retains both the
original wanted snapshot and the remaining wanted groups. A narrow
`Key -> remaining GroupSet` representation can drop unused payload without
changing the request relation. The measured removable-class cohort is
313,825,128 bytes before any obsolete group-vector saving. This is a
layout estimate for a future change, not an achieved heap reduction.
Map entries, Keys, current groups and class-region registrations remain
required. A separate source audit proves the field-state/effect mapping.

The static retained set contains 630,670,320 bytes of int arrays,
229,504,864 bytes of HashMap nodes, 150,751,168 bytes of PathFacts and
111,115,872 bytes of Patterns, among other classes. These are physical
class sizes within that retained set. They do not assign all arrays to
writer admissions or give an exclusive WriterInput retained size.

Uncommitted actor batches also hold 691,725 interests, 999,009 supplies,
133,208 static writer registrations and 2,028 nonstatic FLOW writer
registrations. The committed writer count plus those two registration
counts equals the 1,046,769 writer keys retained by the owners. Exact
supply duplicates and stale writer-selector ratios are unmeasured.
Do not use the 88 committed field states as the complete universe of
uncommitted interests.

Method-edge directories have 208,522 non-REACH premise rows across the
actors. They retain 968,222 ConclusionGroups. These are storage populations,
not a scalar fact count. The method-edge retained set includes 1,936,444
HashMaps and 3,584,626 ArrayLists. Empty family/layer containers therefore
remain a concrete representation target alongside communication sharing.

The selected factored stores contain only 512 NNF scopes and 947 accepted
origin-domain memberships. Their combined retained set is 12.56 MB,
mostly empty per-actor store/map headers. The mark catalog has 42 groups,
934 full keys and 977 atoms. These measured populations do not support a
mark-catalog or origin-product explanation for this run-1 OOM. They do not
isolate the total downstream effect of the all-origin correction.

### Conductor run-2 retains FULL state and empty containers

The whole reachable conductor heap is 7,622,148,688 bytes in 231,351,853
objects. Method-edge owners retain 1,408,746,896 bytes; summaries retain
443,629,536; ordinary caller/publication/request owners retain 614,187,968;
and demand/current-query owners retain 187,233,888. These selected retained
sets overlap. The largest individual dominator is IR storage at
2,256,683,664 bytes, which remains outside the proposed optimization scope.

The complete histogram has zero live instances of the static service,
point-resume directory and all four forward join/result stores. This agrees
with freeze and backward construction. Their empty retained queries alone
would not establish zero, but the class census does. This capture does not
support a leaked run-1 static service as the conductor run-2 OOM cause.

There are 17,142 active method-edge owners and 722,071 non-REACH premise
rows. Their retained set has 3,174,894 ConclusionGroups, 6,349,787 HashMaps,
3,356,336 TaintGroups, 6,712,672 ArrayLists and 3,356,336 outer layer arrays.
Unused family/layer containers surround the stored FULL facts. This is
a measured representation cost, distinct from the number of fact marks.

Actors also hold 2,362,983 pending publications. The whole heap has
2,371,284 Publication objects with 56,910,816 shallow bytes. Wrapper count
alone does not include retained facts or backing capacity. The direct
pending-list retained union is 69,618,136 bytes. Its largest list has
211,424 publications and retains 6,042,608 bytes. A strong root goes through
the method actor and `UnitRunner.queued`. Pending batches are a real cost,
but they are much smaller than the method-edge owner union in this capture.
The generic work-queue selection excludes these actor pending lists.

### Stirling-PDF run-2 retains a large publication batch

The whole reachable heap is 7,584,231,496 bytes in 236,805,716 objects.
Method-edge owners retain 2,464,176,776 bytes; summaries retain 708,680,056;
ordinary caller/publication/request owners retain 814,538,216. These sets
overlap. The complete class census again has zero static-service,
point-resume or forward join/result-store instances. This run-2 OOM is
not explained by a retained run-1 static service.

There are 7,260 method-edge owners and 1,154,648 non-REACH premise rows.
Their retained set contains 5,632,237 ConclusionGroups, 11,264,474 HashMaps,
5,960,168 TaintGroups and 11,920,336 ArrayLists. The whole heap contains
8,272,142 ConclusionGroups and 8,765,288 TaintGroups. The same storage
pattern is repeated across edge, summary and other stores.

Actors retain **25,447,916 pending publications**. The whole heap contains
25,490,542 Publication objects with 611,773,008 shallow bytes. The direct
pending-list retained union is **744,663,872 bytes**, including backing
capacity and exclusively retained descendants. The largest list holds
3,046,598 entries, has capacity 4,102,267 and retains 90,064,576 bytes.
Its strong root reaches a live actor and an active worker through
`UnitRunner.actors` and `RunEvent.Work`.

Current source reopens old summaries for each fresh input-query batch,
then queues permitted answers even if selected guidance already contains
them. This is a concrete repeated-offer path. The heap does not establish
how many of the 25.45 million pending entries this path created, or how
many a proposed admission gate would remove. Summary publication already
uses the method queue-empty barrier; adding that same barrier is not a fix.

### Empty containers have a measured cost

The complete global controls count empty family maps and layer lists:

| Empty container owners | conductor run-2 | Stirling-PDF run-2 |
| --- | ---: | ---: |
| ConclusionGroup with empty FLOW map | 4,792,855 | 8,269,663 |
| ConclusionGroup with empty TAINT map | 73,909 | 25,291 |
| TaintGroup with empty NORMAL list | 3,822,570 | 6,425,443 |
| TaintGroup with empty DEMAND list | 1,093,497 | 2,258,299 |

These are physical population counts, not additional retained-set unions.
Lazy families/layers and in-object REACH bits can remove empty wrappers
without removing any fact. A group can temporarily empty a list during
annotation replacement, so observed empty counts are not a guarantee that
all those lists would remain unallocated for a complete run. The next
proposal states the layout assumptions and byte estimates separately.

The closed initial evidence is under `validation/heap-controller/execution`
and `validation/heap-tools/jobs`; compact totals are in
`validation/heap-summary-current.json`. The separate follow-up is
`validation/heap-followup-v2`, with reviewed CSV hashes and corrected
availability in `validation/followup-summary-current.json`. The field-name
pass is `validation/static-field-identities-v1`.

Every query has an availability status. Blank, absent or capped queries
are not numeric zero. Five writer-tuple queries failed to parse. The first
field-name query failed because MAT division returned a Double index.
The subsequent four constant-index queries use the observed integer path
data and all succeed. Literal provider/entry root arguments and the empty
NORMAL FLOW-list queries remain unavailable; they were not retried.
The original errors and outputs remain preserved. All reader processes
have exited. No analyzer command was rerun for these follow-ups.

## Counter and owner limits

The final form counters cover the whole analysis invocation and all its
iterations. `forwardFormShapes` counts successful build samples, including
rebuilds. `ownedContainerEntries` sums sampled backing cells from forward
and reversed builds. Neither counter gives distinct live forms, live
facts or retained bytes. The build timer includes optional build census
work; nested or concurrent timer intervals can overlap. A final run-3
counter cannot be assigned to run-1.

After successful run-1, freeze detaches actor-local edge, initial, queue
and call state and nulls all four local join stores. Backward run-2
constructs none of those four stores. Run-1 demand/seeds, normal records,
complete report witnesses and shared canonical keys can survive. These
source facts guide heap queries. They do not establish an object's
absence, a leak or the cause of the conductor OOM without capture roots.

Pinned source notes are in `validation/source-notes` and
`validation/conductor-run1-to-run2-source-lifetimes.*` in the artifact
directory. The diagnostic flag is `disableRun1Statics=false`: static
handling is enabled.

## Conclusions and next iteration

The 60-second run-1 target fails on OWASP and conductor; thingsboard does
not complete run-1. Stirling-PDF completes run-1 in 45.242 seconds, then
OOMs in run-2. Three completed run-1 engines and the observed state growth
do not establish an infinite loop. A whole-engine termination proof is
still absent. The evidence shows large auxiliary dependency stores,
repeated summary offers, form construction and projection/cleaner churn.
It does not support a large mark catalog or the new factored origin store
as the main retained-memory cause.

The [next-iteration proposal](../design/bidirectional-retained-work-next-iteration-20261021.md)+ranks changes by expected memory impact and evidence. It separates
avoiding repeated summary work from reducing the cost of required stored
state. Shared writer acquisition remains a hypothesis until its exact
duplicate quotient and late-answer behavior are established. No proposed
next change has been implemented in this campaign.

All eight commands, profile reads and heap reviews are closed, with source
and input stability checks. All strict omissions have a source review.
The campaign fails the resource and strict location gates. It does not
establish overall performance improvement, complete missing-flow coverage
or finding parity with main.
