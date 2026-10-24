# Rule selection and mark reduction: project measurements

Date: 24 October 2026. The final comparison has 12 closed commands: four
fresh main runs, four fresh pre-change runs and four corrected candidate
runs. All profiles and all six required OOM owner scopes are validated.

Candidate runtime: `40e20d16f03ab046df75a53a489bb331b065a9c1`.
Core tree: `c986cba8f1ea0531f7db2a4c91a10c90c2743fd4`.
Reference: `279ac7a6f500a6c431d5898512a1422afb1b40dd`.
Main: `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
Read the [implementation validation](bidirectional-rule-selection-validation-20261024.md)
and [next proposal](../design/bidirectional-rule-selection-next-iteration-20261024.md).

OWASP completes at **run-11**. Its run-1 engine falls from 132.080 s to
84.465 s in this observation. Thingsboard still fails in **run-1** before
any accepted report. Conductor and PDF complete run-1 within 60 seconds,
then fail in run-2. The selected changes remove large amounts of cleanup
and request work, but repeated concrete-mark transport remains large.

Conductor, PDF and OWASP exactly match the pre-change bidirectional report
multisets. This does not pass every main/CI gate. PDF still misses two
strict main return locations. Thingsboard has no accepted result. OWASP
covers all 4,112 main locations, but exports 9,179 entries; CI requires
exactly 4,112 total traces. Those failures remain explicit below.

## Configuration and provenance

All project commands use Gradle `:runProjectAnalyzer` directly and Java 17.
FULL, statics ON and default alias analysis are fixed on both bidirectional
sides. No additional benchmark mode is run.

| Project | Heap | IFDS timeout |
| --- | ---: | ---: |
| OWASP | 8 GiB | 1,000 s |
| Conductor | 8 GiB | 1,080 s |
| Thingsboard | 12 GiB | 1,080 s |
| Stirling-PDF | 8 GiB | 1,080 s |

The outer limit is 1,200 seconds. OWASP uses the CI rule scope. The real
projects select warning/error rules and Semgrep-style SARIF IDs. Conductor
also loads its configured extension, passthrough and compiled models.
All commands retain batch size 2,000 and disabled multi-release JAR lookup.
Test inputs use revision `bbdbcc5fd35d976d93348760ea60561e4670f68c`.
The census hashes model YAML, serialized rules and extension/model inputs;
it does not hash every shared large IR/dependency file.

Main uses the legacy engine. Its run labels are not applicable. Its checkout
has the saved diagnostic-only memory-guard snapshot patch. All four main
commands complete and no main command needs an OOM heap. This includes the
fresh complete Thingsboard baseline with 14 locations.

The initial implementation is `a06458317`, superseded before any candidate
benchmark. `abdbe345e` is the first measured candidate. That campaign exposed
an unintended equality change: ordinal metadata made otherwise equal JVM
rule rows unequal. The correction `40e20d16f` restores all original equality
and hash fields and excludes only specialization labels. It retains original
ordered occurrences and rule/action IDs. All four corrected candidate
commands are fresh. The eight already closed main/reference commands are
SHA-bound imports. The intermediate abd measurements stay separate.

The actual commands run serially with no owned analyzer, test, MAT or profile
decoder overlap. Untimed compilation closes before the first timed leaf.
All source/input/helper/import guards pass. Minimum sampled MemAvailable
in the corrected campaign is 39.91 GiB. Other host work, JIT and page-cache
warmth still vary; Thingsboard has cold loading faults. These are single
paired observations, not statistical or equal-progress speedup estimates.
OOM engine times include their diagnostic heap pauses.

The [closed terminal](../../../run1-rule-selection-equality-fix-20261024/preparation/terminal.json),
[result/iteration receipt](../../../run1-rule-selection-equality-fix-20261024/validation/final-project-results.json)
and [capture inventory](../../../run1-rule-selection-equality-fix-20261024/validation/capture-inventory.json)
retain exact commands, revisions, settings, capture failures and source hashes.

## Iteration results


Main uses the legacy monolithic engine; a BIDI run label is not applicable. Times below are seconds. Recorded engine times include the separate heap-capture pause.

| Project | Side | Command | Run-1 engine | Latest reached | COMPLETE engine | Accepted forward | Terminal |
| --- | --- | ---: | ---: | --- | --- | --- | --- |
| thingsboard | main | 283.528 | not applicable | not applicable | not applicable | not applicable | OK |
| thingsboard | reference | 395.372 | 210.720 | run-1 | none | none | OOM |
| thingsboard | corrected | 374.821 | 197.026 | run-1 | none | none | OOM |
| conductor | main | 146.286 | not applicable | not applicable | not applicable | not applicable | OK |
| conductor | reference | 147.746 | 14.031 | run-2 | run-1 | run-1 | OOM |
| conductor | corrected | 156.959 | 13.640 | run-2 | run-1 | run-1 | OOM |
| Stirling-PDF | main | 54.104 | not applicable | not applicable | not applicable | not applicable | OK |
| Stirling-PDF | reference | 196.771 | 24.142 | run-2 | run-1 | run-1 | OOM |
| Stirling-PDF | corrected | 180.709 | 25.269 | run-2 | run-1 | run-1 | OOM |
| OWASP | main | 150.347 | not applicable | not applicable | not applicable | not applicable | OK |
| OWASP | reference | 1039.718 | 132.080 | run-8 | run-7 | run-7 | TIMEOUT |
| OWASP | corrected | 841.414 | 84.465 | run-11 | run-11 | run-11 | OK |

| Project / side | Iteration | Direction / limit | Engine | Barrier | Whole iteration | Status |
| --- | --- | --- | ---: | ---: | ---: | --- |
| thingsboard / reference | run-1 | FORWARD / 1 | 210.720 | not reached | not reached | OOM |
| thingsboard / corrected | run-1 | FORWARD / 1 | 197.026 | not reached | not reached | OOM |
| conductor / reference | run-1 | FORWARD / 1 | 14.031 | 0.351 | 14.383 | COMPLETE |
| conductor / reference | run-2 | BACKWARD / 1 | 66.172 | not reached | not reached | OOM |
| conductor / corrected | run-1 | FORWARD / 1 | 13.640 | 0.379 | 14.019 | COMPLETE |
| conductor / corrected | run-2 | BACKWARD / 1 | 74.155 | not reached | not reached | OOM |
| Stirling-PDF / reference | run-1 | FORWARD / 1 | 24.142 | 0.193 | 24.335 | COMPLETE |
| Stirling-PDF / reference | run-2 | BACKWARD / 1 | 124.521 | not reached | not reached | OOM |
| Stirling-PDF / corrected | run-1 | FORWARD / 1 | 25.269 | 0.204 | 25.474 | COMPLETE |
| Stirling-PDF / corrected | run-2 | BACKWARD / 1 | 106.484 | not reached | not reached | OOM |
| OWASP / reference | run-1 | FORWARD / 1 | 132.080 | 2.866 | 134.946 | COMPLETE |
| OWASP / reference | run-2 | BACKWARD / 1 | 172.157 | 0.462 | 172.619 | COMPLETE |
| OWASP / reference | run-3 | FORWARD / 2 | 52.728 | 2.241 | 54.969 | COMPLETE |
| OWASP / reference | run-4 | BACKWARD / 3 | 174.258 | 0.586 | 174.844 | COMPLETE |
| OWASP / reference | run-5 | FORWARD / 4 | 55.955 | 2.088 | 58.043 | COMPLETE |
| OWASP / reference | run-6 | BACKWARD / 5 | 192.501 | 0.908 | 193.409 | COMPLETE |
| OWASP / reference | run-7 | FORWARD / 6 | 52.246 | 2.244 | 54.490 | COMPLETE |
| OWASP / reference | run-8 | BACKWARD / 7 | 122.693 | not reached | not reached | TIMEOUT |
| OWASP / corrected | run-1 | FORWARD / 1 | 84.465 | 1.821 | 86.286 | COMPLETE |
| OWASP / corrected | run-2 | BACKWARD / 1 | 37.972 | 0.175 | 38.147 | COMPLETE |
| OWASP / corrected | run-3 | FORWARD / 2 | 31.583 | 1.386 | 32.970 | COMPLETE |
| OWASP / corrected | run-4 | BACKWARD / 3 | 43.540 | 0.208 | 43.749 | COMPLETE |
| OWASP / corrected | run-5 | FORWARD / 4 | 31.606 | 1.530 | 33.136 | COMPLETE |
| OWASP / corrected | run-6 | BACKWARD / 5 | 53.658 | 0.208 | 53.866 | COMPLETE |
| OWASP / corrected | run-7 | FORWARD / 6 | 33.530 | 1.468 | 34.998 | COMPLETE |
| OWASP / corrected | run-8 | BACKWARD / 7 | 91.505 | 0.468 | 91.973 | COMPLETE |
| OWASP / corrected | run-9 | FORWARD / 8 | 38.308 | 1.405 | 39.713 | COMPLETE |
| OWASP / corrected | run-10 | BACKWARD / 9 | 274.228 | 3.242 | 277.470 | COMPLETE |
| OWASP / corrected | run-11 | FORWARD / 10 | 34.827 | 1.440 | 36.267 | COMPLETE |

Heap pauses remain part of the raw engine times:

| Project / side | Iteration | Heap-capture pause | Heap bytes |
| --- | --- | ---: | ---: |
| thingsboard / reference | run-1 | 29.223 | 18,401,050,181 |
| thingsboard / corrected | run-1 | 29.620 | 18,249,821,818 |
| conductor / reference | run-2 | 17.514 | 11,721,193,540 |
| conductor / corrected | run-2 | 17.806 | 11,622,957,730 |
| Stirling-PDF / reference | run-2 | 19.061 | 12,088,493,204 |
| Stirling-PDF / corrected | run-2 | 19.038 | 12,081,734,490 |


The completed run-1 target passes for Conductor and PDF. It fails for
OWASP and Thingsboard. OWASP's observed completed run-1 reduction is 36.1%.
PDF's run-1 is slightly slower. An OOM prefix is not a completion or a
matched-work timing result. No Thingsboard speedup is inferred from its
shorter failed prefix.

## Locations and acceptance gates


A key includes the rule and sink fingerprint/method; the strict key also checks exported location. Occurrences count repeated SARIF results, not unique sinks.

| Project | Main keys / occurrences | Corrected keys / occurrences | Main keys present | Missing strict keys | Extra keys | Assessment |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| thingsboard | 14 / 14 | 0 / 0 | 0 | 14 | 0 | CENSORED_NO_ACCEPTED_REPORT |
| conductor | 7 / 7 | 41 / 42 | 7 | 0 | 34 | OBSERVED_KEYS_PASS |
| Stirling-PDF | 17 / 17 | 148 / 973 | 15 | 2 | 133 | FAILED_STRICT_TWO_MISSING |
| OWASP | 4112 / 4112 | 4112 / 9179 | 4112 | 0 | 0 | OBSERVED_KEYS_PASS |


Conductor, PDF and OWASP match the reference on the entire fingerprint and
strict-location multisets, including every per-key occurrence count. Both
empty Thingsboard reports are censored failures. Equal exports do not prove
equality of all source, full-mark, branch or trace witnesses. OWASP also
compares accepted run-11 against reference run-7 after its run-8 timeout.

| Gate | Corrected result |
| --- | --- |
| Main OWASP sink locations | PASS, all 4,112 present |
| CI OWASP exact trace count | FAIL, 9,179 versus expected 4,112 |
| Main Conductor sink locations | All seven present in accepted run-1; whole analysis incomplete |
| Main PDF strict locations | FAIL, 15 of 17 present |
| Thingsboard accepted result | FAIL, run-1 OOM; no accepted forward report |
| Local application regression gate | Same 11 fresh-reference failed assertions; no new failures |
| Run-1 engine within 60 seconds | Conductor/PDF pass; Thingsboard/OWASP fail |

The CI [Check stats step](../../.github/workflows/ci-analyzer-owasp.yaml)
tests `TraceGenerationStats.total` against 4,112. Main records total 4,112
(493 Simple, 3,619 generated, zero failures). The corrected engine records
9,179 Simple entries, zero generated traces, and no generation failures.
It reports zero CONFIRMED and 9,179 DEMAND candidates. `NO_DEMAND_EDGE` is
the iteration stop reason; it does not make those entries confirmed or
satisfy the CI count. Repeated exported entries are not approved callback
diagnostic variation. Their 9,179-element multiset already exists on the
pre-change reference.

The first abd candidate added 14 Conductor, 17 PDF and 413 OWASP exported
occurrences. Production-object equality controls reproduce key cardinality
1 → 2 → 1, and the corrected real-project multisets return exactly to the
reference. This confirms the visible regression is corrected. The
[validation record](bidirectional-rule-selection-validation-20261024.md)
keeps RED/GREEN tests and the invalid 1 GiB unit-test setup separate.

### PDF return locations and extra candidates

Main reports `ConvertEmlToPDF.convertEmlToPdf` at IR 103 / lines 133–134,
and `PipelineController.handleData` at IR 50 / lines 90–91. Both reference
and corrected exports choose the last returns: IR 126 / lines 148–149 and
IR 109 / line 130 (`return null`). All original returns join a synthetic
normal exit. SIMPLE export has no thread-flow path, so the location fallback
chooses the method's last return. Recomputed fingerprints reproduce the four
actual location hashes. Current source and duplicate class copies are
[revalidated](../../../run1-rule-selection-equality-fix-20261024/validation/corrected-pdf-source-revalidation.json).

This establishes a relocation mechanism, not the actual tainted-return path.
Do not present the null/constant fallback as the true tainted sink or waive
the two strict missing keys. Full branch/source provenance is still absent.

Conductor has 34 extra unique keys: 13 filesystem sinks from its gRPC
extension, nine SQL, eight XSS, two GraalJS, one traversal and one reflection.
Two internal generated Optional-exit entries have no valid export location
and are dropped on both sides. PDF has 133 extra keys: 96 XSS return sites,
32 traversal, two SSRF, two command injection and one URL rewriting. The PDF
XSS sites alone have 918 occurrences. These are unconfirmed run-1 candidates;
run-2 OOM prevents refinement. They are not individually established false
positives. Their SIMPLE reports do not supply complete source witnesses.

## Selected rule program and work counts

The provider uses final executable `used = assigned ∩ CM-used`. It folds
executable CM, removes GEN outside used, and compacts false/actionless
source/cleaner rows to a fixed point. Metadata uses that final set for reset
targets. Its conditions stay unchanged; live reset positions and original
labels remain. Safe raw queries reject selected-out items before binding.
The shared dataflow/AP/IR/static-transfer code is unchanged. JVM lowering
carries original ordinals and the selected effective reset targets.

| Project | S / K / M | Bidirectional input → final rows | Final used | Rounds |
| --- | --- | --- | ---: | ---: |
| Thingsboard | 381 / 6,137 / 4,753 | 11,939 → 9,415 | 222 | 4 |
| Conductor | 89 / 6,185 / 4,482 | 11,719 → 7,843 | 35 | 4 |
| PDF | 795 / 6,137 / 4,736 | 12,016 → 9,919 | 275 | 4 |
| OWASP | 168 / 6,255 / 4,893 | 11,813 → 7,863 | 68 | 2 |

The ordinary view has 9,421 rows / 226 used keys on Thingsboard and
7,869 / 72 on OWASP. These are final compiler-plan counts, not original
mark counts, specialized bound-rule counts or runtime savings. All four
scopes are complete; no project uses the TOP fallback.

Thingsboard reaches run-1 OOM on both sides, with no COMPLETE/accepted run.
The following cumulative counters use engine END minus START. The prefixes
have different progress. Counters describe operations, not JVM retained bytes.

| Thingsboard run-1 engine counter | Reference | Corrected |
| --- | ---: | ---: |
| Incoming facts | 4,813,754 | 5,671,145 |
| Initial candidates | 7,874,570 | 9,430,822 |
| New entry initials | 562,631 | 594,293 |
| Fresh request memberships | 7,168,338 | 3,027,629 |
| Request matcher calls | 4,520,685 | 5,384,398 |
| Offered request memberships | 22,735,993 | 9,486,674 |
| Accepted caller-input deltas | 4,095,788 | 4,740,359 |
| Created caller-input groups | 2,782,359 | 3,197,932 |
| Created caller-input index postings | 4,315,424 | 5,011,332 |
| FULL cleanup blocks | 99,664,543 | 7,663,979 |
| FULL cleanup scheduled step slots | 286,671,347 | 15,878,333 |
| FULL cleanup inputs | 99,658,011 | 7,661,749 |
| FULL cleanup request memberships | 3,597,516 | 198,942 |
| Local static supply offers | 5,181,652 | 5,622,321 |
| Fresh temporary static supply memberships | 286,044 | 406,807 |
| Request member scans | 37,197,903 | 18,892,888 |
| Request answers | 3,030,761 | 3,734,741 |
| Outgoing request memberships | 13,067,410 | 6,015,410 |

Cleanup block admissions fall by about 13 times. Scheduled slots are not
actual primitive calls: early death, nonmatching bases and intermediate
deduplication can remove later work. The narrow completed singleton-FLOW
counter has a separate logical-call law. No generic CPU saving follows by
subtracting scheduled slots. The candidate admits more body/input state
before reaching the guard even after the cleanup/request reduction.

`newInitials` counts method-entry admission. Static point refinement bypasses
it. It is not an upper bound on store premise keys or global initial values.
Created caller-input groups are not the physical ConclusionGroup class count.
Some counters are gauges or lifetime maxima; their END−START is not per-run
work. The [counter scope receipt](../../../run1-rule-selection-equality-fix-20261024/validation/final-counter-scope-and-values.json)
separates cumulative counts, current values and maxima. Form counters on
projects with later runs are not all run-1 counters.

## CPU, allocation and the slow tail

The final comparison covers 18 JFR recordings and five native profiles,
using the eight sealed baseline lanes plus four corrected lanes. All are
decoded. Intermediate abd captures are a separate historical set.
Seven corrected JFR recordings and two native profiles are sealed in the
[current profile authority](../../../run1-rule-selection-equality-fix-20261024/validation/profile-analysis/analysis-scope.json).

| Run-1 scope | Reference | Corrected |
| --- | ---: | ---: |
| Thingsboard pre-heap wall prefix | 181.429 s | 167.337 s |
| Thingsboard pre-heap allocation sample weight | 962.364 GB | 897.479 GB |
| Thingsboard pre-heap recorded GC pause sum | 70.851 s | 46.733 s |
| Thingsboard normal 30 s allocation weight | 277.713 GB | 230.318 GB |
| Thingsboard native normal GC-worker CPU share | 40.79% | 41.56% |
| Thingsboard last 30 s pause interval union | 27.200 s | 24.370 s |
| Conductor complete run-1 allocation weight | 48.615 GB | 43.663 GB |
| PDF complete run-1 allocation weight | 65.123 GB | 72.571 GB |
| OWASP complete run-1 allocation weight | 224.491 GB | 140.662 GB |

Allocation weights estimate traffic, not retained memory. Java execution
samples and GC-inclusive native CPU samples use different denominators.
Recorded pause sums exclude boundary-crossing collections; only the stated
last-30-second control establishes a nonoverlapping interval union.

Thingsboard's corrected normal window has 9,594 Java samples. Inclusive
CPU/allocation shares are cleanup 4.46%/5.79%, unchanged emission
10.00%/14.49%, call building 10.85%/4.92%, and MethodEdgeStore admission
4.49%/5.41%. The whole pre-heap call-builder share is 25.95%, overlapping
provider/raw matching at about 16.4%. Never add those scopes. The broad
`withForm` frame invokes the analysis callback; it is not cache lookup cost.
These marginals do not establish a two-times gain from a small helper or
unchanged scheduling alone.

The corrected final 30 seconds contain 17 `System.gc()` G1Full events.
With young-GC pauses, they cover 24.370 seconds; the independent interval
union control passes. The inherited MemoryManager explicitly requests GC
at its threshold and can resume soft caching after usage falls. Its code
is unchanged. The tail is a memory-pressure collapse, with little time for
application progress. This is not evidence of a new infinite processing
loop, and it does not justify a reference-manager rewrite.

Thingsboard records 97,212,521 form accesses and 1,674,785 successful builds
by termination. Its dedicated early attach fails after process exit; the
continuous recording covers run-1. The other three dedicated early records
cover complete run-1 and match continuous aggregates. OWASP's requested
normal30 JFR window is only 24.465 seconds because run-1 ends first. Its
native normal capture crosses the barrier and run-2. Whole native shares
therefore cannot be called run-1 shares. Reference near-limit native samples
belong to run-8. The [profile comparison](../../../run1-rule-selection-equality-fix-20261024/validation/profile-analysis/comparison-matrix.md)
keeps every actual phase and coverage limit.

## OOM owners and the concrete-mark axis

All six final-comparison OOM captures are analyzed: Thingsboard in run-1,
Conductor and PDF in run-2, on each side. Each primary scope passes all
41 query controls, for 246 primary controls. The old abd Thingsboard heap
is diagnostic history; its other two
superseded heaps remain captured but deliberately unanalyzed.

Corrected Thingsboard's indexed live physical heap is 11,360,509,544 bytes.
That is distinct from the HPROF file length and the guard's heap-used value.

| Exact retained union, Thingsboard run-1 | Reference MB | Corrected MB |
| --- | ---: | ---: |
| Execution stores and work | 2,220.753 | 2,668.007 |
| Summaries and subscriptions | 2,148.885 | 2,405.206 |
| Static service and point directories | 876.694 | 485.163 |
| Unanswered point rows | 519.448 | 230.142 |
| FLOW image holders | 205.405 | 90.058 |
| Form owners and directories | 724.984 | 723.267 |

Decimal MB is used here. These retained unions overlap and are not an
additive partition. Do not sum them, subtract unrelated scopes, or convert
their differences into whole-run savings at unequal closure prefixes.
IR RAM is a separate accounting category and remains out of scope.

The exact corrected MethodEdgeStore union alone is 2,558,346,784 bytes.
Its disjoint shallow classes include 406.966 MB HashMaps, 338.592 MB
ArrayLists and 385.558 MB Object arrays: 1,131.115 MB, or 44.21% of this
one retained set. There are 4,239,228 ConclusionGroups, 6,467,153 TaintGroups
and 6,467,932 TaintTrees inside it. This is substantial container replication
around relation rows. It is not a proposal to make one small container faster.
Other shallow classes and retained unions must not be added to that total.

The current heap has only 1,687 globally interned InitialAp values. It has
875,915 method-qualified premise arrays and 6,672,101 ConclusionGroups
across owners. The complete method-store census finds 793,890 concrete
initial rows (90.64%), 81,244 FLOW rows and 781 ZERO rows. Concrete rows
use 21,558,635 of 25,251,733 allocated slots (85.37%).

Erasing only the mark within each method gives 48,254 concrete geometries:
16.45 marks per geometry. There are 33,851 width-18 and 4,987 width-28
families. Concrete origin rows are 268,373 ARG, 332,820 STATIC and
192,697 THIS. Thus static **origins** remain important even though actual
static-base facts are not propagated through ordinary run-1 statements.
This is not only a static writer problem. Conclusion TaintTrees already
pack marks, but their scalar initial keys prevent that packing across T.

A complete current output probe uses eight deliberately high-width stores:
1,735 arrays, 62,165 slots and 9,996 occupied cells. All 15,631 TAINT headers
and 5,417 trie nodes are decoded with zero unknowns. Among 9,584 concrete
cells, 9,548 have exact T→T output images; 36 have nonidentity marks. The
9,548 images form 420 complete cohorts after aligned mark substitution,
with up to 28 marks per cohort. Every output base, field, exact/any kind,
layer, exclusion and original mark/origin is retained. The 412 FLOW cells
and 36 nonidentity concrete cells stay outside this strict compression.

This is a local snapshot opportunity of 22.73 times fewer image rows. It is
not a global operation count, byte saving or proved engine speedup. The
input-only 745,636 potentially duplicate headers are an upper bound, not an
output eligibility estimate. A future operation can still inspect a mark,
primitive property or condition and needs an exact adapter or scalar path.
The older exact-current-Facts caller probe found only 9.21% proven duplicate
images in four stress directories. That is a different equivalence and
cannot be used to bound aligned input/output mark batching.

The same-heap form control has 1,674,606 successful payload builds and
900,674 live slots. That slot count is a conservative upper bound on
first-build slots under the checked non-clearing ownership; it can also
include unbuilt or losing slots. At least 773,932 successes therefore
repeat a payload build (46.22%).
Only 14 payloads are live at capture; the owner scope includes directories.
This is not a call-plan count, CPU-time estimate or authorization for a
strong-cache/reference-policy change. Reduce repeated fact processing and
retained relation copies first.

| Later-run snapshot | Latest reached / COMPLETE / accepted | Live physical MB | Execution union MB | Summaries/subscriptions union MB |
| --- | --- | ---: | ---: | ---: |
| Conductor reference | run-2 OOM / run-1 / run-1 | 7,618.545 | 1,528.138 | 1,107.944 |
| Conductor corrected | run-2 OOM / run-1 / run-1 | 7,614.994 | 1,606.402 | 1,295.878 |
| PDF reference | run-2 OOM / run-1 / run-1 | 7,624.794 | 3,933.509 | 1,138.943 |
| PDF corrected | run-2 OOM / run-1 / run-1 | 7,618.988 | 3,736.960 | 1,261.036 |

These later-run unions also overlap. They are separate from the physical
heap denominator and are not equal-progress byte-saving comparisons.
Run-1 service and point instances are zero in every run-2 snapshot. The
current run-2 failures therefore do not retain the old run-1 static service.
Execution state is large in PDF; demands/queries are only 55.138 MB on the
corrected snapshot. This is a later-iteration issue, not a reason to add
an unselected run-2 redesign to the next run-1 iteration.

The [final heap seal](../../../run1-rule-selection-equality-fix-20261024/validation/heap-analysis-final-seal.json),
[six-snapshot comparison](../../../run1-rule-selection-equality-fix-20261024/validation/heap-reference-corrected-comparison.json),
[complete input-axis census](../../../run1-rule-selection-equality-fix-20261024/validation/heap-candidate-thingsboard-mark-axis-maps/input-axis-summary.json),
[complete bounded output probe](../../../run1-rule-selection-equality-fix-20261024/validation/heap-candidate-thingsboard-full-cohort/full-cohort-diagonal-summary.json)
and [exclusive MethodEdgeStore class control](../../../run1-rule-selection-equality-fix-20261024/validation/heap-candidate-thingsboard-mark-axis-owners/exclusive-method-store-class-controls.json)
retain source hashes, same-heap addresses, exact query coverage and limits.
Original failed tool attempts remain recorded: missing GNU time was replaced
by external resource capture; the reference last-band MAT integer parse
failure was corrected only for its 4,462-row tail. No failed query is counted
as a successful zero. Current primary/probe controls pass.

## Next iteration and remaining gates

The [ranked proposal](../design/bidirectional-rule-selection-next-iteration-20261024.md)
first targets exact FULL mark-correlated transport and storage. It shares
common geometry while retaining T→T and U→U separately. It must not create
T→U or U→T, drop origins, change field processing or weaken marked run-2
guidance. Its second candidate removes intermediate storage/queue work only
inside certified plain linear regions. Both require constructive adapters
and current eligibility checks before implementation. No global 2x gain is
claimed from a biased cohort or a CPU marginal.

The selected provider work is implemented and tested. Thingsboard run-1
completion, the 60-second targets for Thingsboard/OWASP, PDF return-path
provenance, the OWASP CI trace total, and later-run memory remain failed or
unresolved acceptance goals. No field, exit-rule, static protocol, IR storage
or reference-manager redesign is added to hide those gaps.
