# OWASP: O1–O3 fixed-workload test

The fresh optimized run completed under the original 8 GiB heap and 1,000-second IFDS timeout. All 4,112
main sink identities are present. There are no added or missing unique sinks. The optimized run is slower and
exports more occurrences at those same sinks. This is the OWASP result; the other project gates remain open.

## Inputs and execution

Main is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with only the opt-in guard heap hook. The optimized revision
is `b0a2448c0af7906ed49d90f203dd5a2a1c881523`. Its runtime source is committed. The unrelated `issues.md` edit
remains in the workspace patch digest and is not executable code.

Both runs use the same fixed model, classes, dependencies and built-in rules. The model source head is
`79b9bd6177e07991a9c11dc19e457c840e229931`; its YAML SHA-256 is
`e8fd467e410051c3b0b36d461d953780e5819345fd3b7fddc68a3f8769e09873`. Both use Temurin 17, storage batch 2,000
and disabled multi-release loading. The task calls Gradle `runProjectAnalyzer` directly. Bidi starts at limit 1.

The OWASP model is the existing portable build made with Maven 3.6.3 and Temurin 17.0.18, with release-8 classes.
The CI workflow requests Maven 3.9.11 and an unpinned upstream checkout. This test uses the CI analysis heap,
timeout and rule configuration, and checks its expected 4,112 main traces; it does not reproduce a fresh CI
autobuild. Full input hashes and this limit are in the external input audit.

## Findings

| Measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Unique strict sink identities | 4,112 | 4,112 |
| Exported occurrences | 4,112 | 9,179 |
| Main unique sinks absent | — | 0 |
| Added unique sinks | — | 0 |
| Final internal state | — | 9,179 CONFIRMED; 0 DEMAND |

The comparison keeps rule ID, sink fingerprint and full method signature. It also retains physical and logical
locations in the evidence. It does not merge changed return instructions. Both outputs are valid; both analyses
complete without a prescan error. No finding regression is observed by this strict sink oracle.

The independent raw SARIF check shows that all 5,067 extra rows are exact exported duplicates. Multiplicity
is one at 1,692 sinks, two at 1,571 sinks, five at 824 sinks and nine at 25 sinks. They are not new false-positive
locations. The legacy collector groups by rule ID and statement. The current report key includes the full rule
value, while SARIF exports less key detail. ReportBuilder merges proof/context witnesses within one full key,
and JIRBidiAnalysis exports one row per report entry. The export has no proof/context properties, so the exact
internal partition behind each duplicate remains unproved. Do not infer one result per proof from the count.

## Time and memory

| Phase or measure | Main | Optimized bidi |
| --- | ---: | ---: |
| Prescan | 64.923 s | 78.917 s |
| Full analysis after prescan | 48.142 s | 423.786 s |
| Total IFDS | 113.068 s | 505.244 s |
| Whole Gradle command | 174.472 s | 584.359 s |
| Peak process RSS | 7,058,644,992 B | 9,983,750,144 B |
| Recorded GC pause sum | 4.064 s | 17.325 s |
| Terminal OOM / timeout | Neither | Neither |

| Bidi iteration | Direction | Limit | Engine | Barrier | Engine result |
| --- | --- | ---: | ---: | ---: | --- |
| 1 | Forward | 1 | 310.850 s | 0.869 s | COMPLETE |
| 2 | Backward | 2 | 57.808 s | 0.091 s | COMPLETE |
| 3 | Forward | 3 | 54.008 s | 0.157 s | COMPLETE |

The driver stops with `COMPLETE / STOP_RULE`. This proves completion within the fixed IFDS budget. It does not
show a speed improvement over main. RSS includes native memory and is not the live Java heap. Neither memory
guard nor JVM OOME fired, so no guard heap dump was needed in this pair. Separate live-heap owner measurement
and checkpoint attribution are still required by the plan.

The final optional counters record 884,662 successful forward builds, 308,762 reverse builds and 71,103,285
hits from 71,987,947 accesses. Aggregate build time is 199.603 seconds across builds; it is not an exclusive wall
interval. The counters include 2,986,565 predicate compilations, 23,018,433 nodes and 16,440,071 leaves, including
rebuilds. Runtime totals are 566,917 proof records, 1,064,212 support shapes and 93,214 canonical root outcomes.
The final pin count is zero; the peak is 14. Registered form references are zero after teardown. These are
cumulative counts and final lifecycle observations, not live or peak owner sizes.

## CPU and allocation evidence

The 30-second normal bidi capture ran during run 1 and has 6,421 CPU samples. Stacks with GC frames account
for 3,520 samples, or 54.82%, counted once per stack. This includes concurrent GC and does not measure wall
pause time. The JFR pause sum above is a separate measure. Java JFR samples identify collection-size access,
instruction visits and class lookup in run 1, and signed normalization and hash tables in later runs.

Main's full scan ended before the requested 60–90-second window. Its first normal capture began during report
generation and is excluded from the full-scan comparison. The continuous JFR retains the complete 48.142-second
main full-scan phase and its final 30 seconds. The capture guard was corrected for later runs. Neither process
survived to the configured near-limit window, so that capture is unavailable.

JFR sampled allocation weights sum to about 222 GB on main and 590 GB on bidi across their recordings. These
are estimates of allocation traffic, not retained heap bytes. Form rebuilds, normalization, generic transfer
objects and diagnostic sampling need review against the allocation stacks before assigning their individual
costs. Shape sampling appears in 74 of the 6,421 normal CPU samples, or 1.15%. Its frame/class is absent from
the retained top-25 JFR allocation rankings, so those tables do not quantify its allocation bytes. The counter
totals imply 1,193,424 temporary sampler executions. This pair has profiling and optional current-side counters,
and the host runs other jobs. Treat its
times as observations from one instrumented pair, rather than a controlled throughput estimate.

## Evidence

The evidence root is `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`:

- `fixed-projects/OWASP/{main,bidi}`: run manifests, logs, resource samples, JVM evidence, SARIF and JFR.
- `fixed-projects/comparison/OWASP.json`: strict identities, completion checks and occurrence differences.
- `fixed-projects/OWASP/main/profile-capture-review.json`: the misplaced main capture and valid JFR window.
- `input-audit/pre-run-committed`: complete model/class/dependency/rule/resource manifests.
- `fixed-projects/OWASP/{review,performance-review}`: independent follow-up review, when complete.

The remaining work is real-heap owner analysis and the O3/O1 checkpoint runs. IR storage and O4–O12 remain
outside the selected implementation.
