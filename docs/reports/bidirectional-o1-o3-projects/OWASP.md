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
guard nor JVM OOME fired, so no guard heap dump was needed in this pair. The separate live-heap measurement
below supplements this pair. All eight [checkpoint scans](../bidirectional-o1-o3-checkpoints.md) have
finished; their heap and profile attribution remains under review.

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

The same normal capture contains form-builder frames in 2,512 samples (39.12%), cache-payload frames in
2,612 (40.68%) and rule/signed/predicate compilation frames in 1,448 (22.55%). These inclusive stack groups
overlap; their percentages cannot be added. They identify substantial construction work in this early run-1
window. The final counters do not separate first builds from rebuilds or provide per-iteration build counts.

Later-run JFR samples also identify signed normalization as a construction cost. Its inclusive allocation
weight is 20.34% in run 2 and 21.53% in run 3. Run 1 allocation stacks include generic compiled-edge application
(14.87%), fact filtering (10.41%) and cleaner-chain application (9.78%). These estimates overlap and describe
allocation traffic. They do not identify retained owners or prove that an operation can be removed. The next
measurements must distinguish necessary transfer work, repeated form construction and retained run state.

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
and the host runs other jobs. There is no same-bidi counters-off control to quantify or subtract diagnostic
overhead. Treat these times as observations from one instrumented pair, rather than a controlled throughput
estimate.

## Separate live-heap replay

A separate run uses the same fixed model, rules, 8 GiB heap and 1,000-second IFDS budget. It captures a live
heap during forward run 1 at limit 1, about 60 seconds after the observed run start. The capture takes
13.137 seconds and produces an 8,548,944,103-byte HPROF file. This forced-GC/dump work is excluded from the
primary timing comparison above. The replay completes all three iterations and preserves all 4,112 strict
sink identities, all 9,179 per-identity occurrence counts and all exported location sets from the primary
bidi output. It adds no new unique identities relative to main.

MAT parses the timed heap with a 20 GiB parser heap and no object-discard options. It indexes 169,554,952
reachable objects with 5,304,628,496 shallow bytes. The file's serialized size is not live heap size.
All 287,882 form slots have a cached nonnull payload. The complete payload count and the enabled form
reference manager's registered-wrapper count are also 287,882. The inverse empty-slot histogram fails
on an empty selection; it remains unavailable. The full-count equality independently establishes that
no slot lacks a cached payload.

The live compiled inventory has 801,752 predicates, 647,974 sink descriptors, 112,138 call plans and
1,494,416 statement summaries. The predicate table has 7,469,778 stored nodes and 11,625,607 child references.
These are live counts at one capture, rather than cumulative rebuild counters. Source and sink predicates
share this compiled representation; the predicate count is not a count of sink conditions alone.

| Selected roots | Bytes in retained union |
| --- | ---: |
| Compiled predicate/source/sink descriptors | 1,715,130,896 |
| Actor predicate state | 101,680,976 |
| Compact containers and their retained graphs | 3,285,685,536 |
| Method recipes and cache shells | 339,437,544 |
| IR storage and interners | 594,788,104 |

Each row sums shallow bytes in one requested retained set. These sets overlap and cannot be added.
The compact-container row includes referenced graphs; it is not exclusive container overhead or an O3
saving. This warm heap does not establish a peak-heap or main-versus-bidi memory saving. IR changes remain
outside scope.

The owner follow-up measures a complete cached-payload retained union of 3,307,810,712 bytes. The wrapper
union is 3,319,325,960 bytes and agrees with the initial soft-reference dominator group. These unions
overlap; do not add them. The payload-root set contains all 287,882 payload objects, while the wrapper
set contains 287,881. One 32-byte payload has another owner. One active callback pin is recorded at the
capture, but these queries do not identify that payload's exact root. Neither union is a guaranteed
cleanup recovery estimate.

Three payloads selected from the earlier 1,000-row prefix have no default strong path to a GC root.
Their all-reference paths go through `SoftReference.referent`, the wrapper registry and the form
manager. Each has a forward call plan and no backward form. The registry itself retains 4,194,408 bytes.
A strongly held registry keeps its wrappers; its links to these payloads are soft. These three examples
show no unintended strong form owner, but do not prove the same result for every cached payload.

All 12,083 actors are current F1/limit-1 actors with nonnull runners, contexts and work stores. They
belong to nine runners. The largest active runner retains 280,574,888 bytes. A selected predicate store
retains 259,648 bytes through an active actor and worker. Method entries retain 339,435,272 bytes through
the interpreter and main thread. These are expected active or persistent owners, with overlapping
graphs. They do not show actors retained from a completed run.

The initial follow-up accounts for all 30 requests: 28 succeed and two scoped address comparisons fail
to parse. A separate two-query correction preserves both selections with decimal long address literals.
Both corrected queries succeed and measure 48,292 caches under the form manager and 287,882 soft references
under its queue. All 30 unique requests are now fulfilled through 32 executions; the original failures
remain in the evidence. The complete initial census and manager fields independently establish 287,882
nonnull cached slots and 287,882 registered form wrappers. The other three reference managers are enabled
with no registered wrappers. The heap-wide soft-reference count is 373,022, so not every soft reference
belongs to the form cache.

## Evidence

The evidence root is `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`:

- `fixed-projects/OWASP/{main,bidi}`: run manifests, logs, resource samples, JVM evidence, SARIF and JFR.
- `fixed-projects/comparison/OWASP.json`: strict identities, completion checks and occurrence differences.
- `fixed-projects/OWASP/main/profile-capture-review.json`: the misplaced main capture and valid JFR window.
- `input-audit/pre-run-committed`: complete model/class/dependency/rule/resource manifests.
- `fixed-projects/OWASP/{review,performance-review}`: completed independent finding and performance reviews,
  including phase measurements, final counters, resource context and read-only reproducers.
- `live-heap-replay`: separate actual run/capture manifests, native process evidence and unchanged strict
  findings. Capture times and CPU windows do not replace the primary pair.
- `heap-analysis/OWASP-live`: bound snapshot, actual MAT commands, archived results and saved-output
  reviews. `warm-owner-review` contains the measured cache paths, actor lifecycle, complete retained
  unions and original query failures; `warm-fix2.xml` preserves the two corrected selections.

The O1 checkpoint completes OWASP with a strong form cache and the same 4,112 unique identities and
9,179 occurrences as the final run. O3 fails in F1. Its parsed guard heap has 7,734,079,192 reachable
shallow bytes. Strong cache arrays retain an overlapping 6,222,787,016-byte set, and materialized
source/sink cube roots retain 5,279,227,672 bytes. The census contains 18,460,694 sink descriptors and
55,543,007 path facts. This identifies compiled cached forms as the dominant O3 memory owner.
These two captures have different progress points, so they do not measure a paired heap saving.
The final OWASP resource pass cannot be assigned to O2 alone. Checkpoint owner details and profile
attribution remain open. IR storage and O4–O12 remain outside the selected implementation.
