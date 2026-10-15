# Merged run-1 requests: project measurements

Date: 15 October 2026. Analyzer commit: `4c64c579f4c4a10fc8ffcc5bcd0bf1bcd45cf877`.
Main baseline: `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.

The project acceptance gate fails. OWASP keeps all 4,112 baseline rule/sink
identities, but its run-1 engine takes 245.412 seconds. Conductor, ThingsBoard and
Stirling-PDF fail in run-1 at the frozen static-class check. They have no completed
iteration or accepted report. Their empty reports lose 38 baseline identities in
total. None of the eight scans reports OOM.

The implementation and core tests are recorded in the
[validation report](bidirectional-run1-mark-request-validation-20261015.md).
The [next proposal](../design/bidirectional-next-iteration-4c64c579f-20261015.md)
uses these measurements. Its changes are not implemented.

## Fixed workload and evidence

Both versions ran the same four configurations through the direct Gradle
`:runProjectAnalyzer` task. The CLI was not used. The latest remote main revision
was checked before the batch. Both checkouts built successfully with Temurin
17.0.18+8. Main has only the opt-in heap-capture diagnostic patch. The current
analyzer core has no uncommitted change. Unrelated local files were preserved.

| Project | Model | Java heap | IFDS timeout | Outer timeout |
| --- | --- | ---: | ---: | ---: |
| OWASP | `../owasp-ci-investigation/project/project.yaml` | 8 GiB | 1,000 s | 1,200 s |
| Conductor | `../opentaint-test/opentaint-test-conductor/opentaint-project/project.yaml` | 8 GiB | 1,080 s | 1,200 s |
| ThingsBoard | `../opentaint-test/opentaint-test-thingsboard/opentaint-project/project.yaml` | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | `../e2e-root/model-Stirling-PDF/project.yaml` | 8 GiB | 1,080 s | 1,200 s |

The batch ran main's four cases, then the current version's four cases, in order.
The harness enabled JFR, timed CPU profiles, form/request counters and OOM heap
capture. No alternative limits, heaps or project configurations were tested.
All current first engines start at run-1, forward, limit 1.

Evidence root, called `B` below:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-mark-requests/20261015T214847.522090Z
```

`B/projects/<project>/<main|bidi>/` contains launch arguments, the analyzer PID and
command, logs, resource samples, a terminal summary, JFR and SARIF. The test checkout
is `bbdbcc5fd35d976d93348760ea60561e4670f68c`. Before and after the scans, the full
input audit passed 80 checks covering 41 content datasets. Their content hashes and
file counts match. This is an endpoint check, not continuous mutation monitoring.
Bindings are `input-before-binding.json`, `input-after-binding.json` and
`input-endpoint-comparison.json`.

The previous raw heaps and profiles were removed before this batch. The inventory
found zero previous raw captures. All eight terminal summaries are checked in
`post-batch-terminal-reconciliation.json`. The original operator left a stale
run-plan state; root reconciled it from the actual terminal receipts and dead
analyzer PIDs. Harness exit 0 means that the matrix ran; it does not mean that all
analyzers passed.

## Findings and analysis progress

Unique identities below mean rule ID, sink fingerprint and full method signature,
with the comparator's documented fallbacks. They are not distinct physical source
positions: multiple rules can share a span. Exact location/span and occurrence
comparisons are also retained. The comparator does not equate nearby lines or
waive missing results after a failed scan.

| Project | Main unique identities | Current unique identities | Missing | New unique identities | Main / current occurrences |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 4,112 | 0 | 0 | 4,112 / 9,179 |
| Conductor | 7 | 0 | 7 | 0 | 7 / 0 |
| ThingsBoard | 14 | 0 | 14 | 0 | 14 / 0 |
| Stirling-PDF | 17 | 0 | 17 | 0 | 17 / 0 |

OWASP has 5,067 additional occurrences at 2,420 existing identities. There are no
removed occurrences or changed spans. These are additional exported occurrences
at retained sink identities, not 5,067 new unique findings. Current OWASP exports
9,179 Simple reports and zero generated traces; concrete trace multiplicity is
not established. Its accepted run-3 frontier classifies
9,179 candidates as DEMAND and zero as CONFIRMED. Identity/span parity does not prove
that every candidate trace is feasible, or that later iterations would complete.

| Project | Final status | Latest reached | Latest completed engine | Accepted forward report |
| --- | --- | --- | --- | --- |
| OWASP | TIMEOUT | run-5, forward, limit 5 | run-4, backward, limit 4 | run-3 |
| Conductor | EXCEPTION | run-1, forward, limit 1 | None | None |
| ThingsBoard | EXCEPTION | run-1, forward, limit 1 | None | None |
| Stirling-PDF | EXCEPTION | run-1, forward, limit 1 | None | None |

Main completes all four cases. Its legacy engine has no bidi run number; reached,
completed and accepted bidi iterations are not applicable to main.

The strict outputs, original identities and resource receipts are under
`B/comparisons/`; `index.json` binds all four comparisons to all eight bundles.
The OWASP comparator used 2.714 seconds and 505.4 MiB peak RSS. The other comparators
each used less than 0.2 seconds. The first two overlapped ThingsBoard prescan, and
the ThingsBoard comparison overlapped PDF prescan. The PDF comparison ran after
all scans. These small cohost costs are recorded; no time is subtracted from the
analyzer measurements.

## Why the three projects lose their findings

All three fail with:

```text
IllegalStateException: Static input class is absent from the frozen prescan universe
Run1Abstraction.emit -> RunMethodAnalyzer.addLink -> UnitRunner.handler
```

The first failed units are
`com.netflix.conductor.es7.dao.query.parser.internal`,
`org.thingsboard.server.common.msg.gen` and `stirling.software.common.util`.
PDF's stack also shows the direct `enterCallees -> link -> handler` route. The
failure occurs before any complete forward report. SARIF is valid but contains
zero attempted/exported traces. Output location mapping did not discard these
findings. ThingsBoard's later `The runner is closed` message follows cancellation;
it is not the first failure.

The precise supplied class, calling method and input are not printed. Therefore
the upstream producer is **unknown** in this batch. The named failed guard and the
absence of an accepted report explain the observed losses. They do not prove
which metadata producer omitted the class.

Source review found two coverage questions for a reproducer. Fallback graph methods
can be collected without alias analysis, while runtime can compute aliases. A
null analysis is not treated like an uncomputed analysis for every metadata
predicate. Also, static alias construction can use a reference owner while direct
field collection uses the declaring owner; ordinary field resolution may make
those owners equal. The latter is conditional, not an identified project cause.
`static-universe-source-triage-v2.json` records both paths and their limits.
The frozen-domain proof must cover every delivered static class/field separately
from each callee's ownership footprint. Removing the guard alone is not a proof.

## Time and memory

The 60-second target applies to a **completed run-1 engine**. Prescan, static setup
and the report barrier are separate. Only OWASP completes run-1, and it misses
the target by 4.09 times. The other elapsed values end in failure.

| Project | Main overall s | Current overall s | Current run-1 engine s | Run-1 result | Main / current peak RSS GiB |
| --- | ---: | ---: | ---: | --- | ---: |
| OWASP | 122.204 | 1,052.401 | 245.411811161 | COMPLETE | 9.015 / 9.542 |
| Conductor | 92.183 | 162.477 | 8.551456620 | FAILED | 9.110 / 9.443 |
| ThingsBoard | 215.399 | 436.071 | 47.387408512 | FAILED | 13.314 / 13.655 |
| Stirling-PDF | 47.129 | 96.265 | 11.679615153 | FAILED | 7.875 / 9.291 |

Overall wall time includes Gradle, project loading and report output. RSS includes
heap and native memory. It is not retained analyzer-core memory. All current cases
take longer overall than main; three also stop before producing an accepted report.
Their time to failure is not a successful-analysis speedup. This comparison with
main does not isolate the effect of this optimization from other new-core changes.
No result from an older analyzer commit is used as a fresh control.

| Project | Main prescan s | Current prescan s | Current metadata setup s | Current catalog setup s |
| --- | ---: | ---: | ---: | ---: |
| OWASP | 33.858 | 32.821 | 31.608258 | 0.170754 |
| Conductor | 25.258 | 46.844 | 13.939952 | 1.902825 |
| ThingsBoard | 148.230 | 255.476 | 69.447208 | 3.667134 |
| Stirling-PDF | 13.780 | 33.096 | 10.152210 | 0.328837 |

Prescan values use logged phase endpoints. Setup uses its own elapsed timers.
These phases consume the common IFDS budget, but are outside run-1 engine time.
Main's full-scan spans are 46.623, 25.141, 29.546 and 4.815 seconds. They include
trace generation of 4.620, 0.909, 0.058 and 0.663 seconds respectively; they are not
run-1 engine times.

OWASP's complete iteration sequence is:

| Iteration | Direction / limit | Engine s | Result | Barrier s |
| --- | --- | ---: | --- | ---: |
| run-1 | Forward / 1 | 245.411811161 | COMPLETE | 2.697711040 |
| run-2 | Backward / 2 | 215.580752052 | COMPLETE | 0.559781325 |
| run-3 | Forward / 3 | 92.358676072 | COMPLETE; accepted report | 4.543684922 |
| run-4 | Backward / 4 | 297.184559569 | COMPLETE | 2.350026495 |
| run-5 | Forward / 5 | 74.667934463 | TIMEOUT | No completed barrier |

There are no MemoryManager OOM reports, JVM OOMs or heap captures. Thus there is
no current heap-owner census or retained-byte diagnosis. High RSS and allocation
samples do not establish that a specific request or fact store retains those bytes.
The absence of OOM during three early failures does not establish memory safety
for a completed run-1.

## Request work and fact admissions

These counters describe run-1. The failed cases are partial prefixes. Created
caller-input groups are cumulative admissions, not simultaneous live groups.
Initial counts are method-local admissions, not a global vocabulary size.

| Project | Input arrivals | New initials | Created caller-input groups | Offered mark memberships | Fresh pending memberships | Committed dynamic memberships |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 173,364 | 36,592 | 48,751 | 24,742,690 | 299,471 | 299,471 |
| Conductor; failed run-1 | 176,711 | 117,336 | 188,392 | 106,109 | 4,376 | 3,474 |
| ThingsBoard; failed run-1 | 2,927,185 | 1,734,056 | 3,015,421 | 14,346,066 | 3,262,966 | 1,347,944 |
| Stirling-PDF; failed run-1 | 171,144 | 62,096 | 109,422 | 403,641 | 114,719 | 110,283 |

OWASP's 24,742,690 offered memberships partition exactly into 46,840 already in
authoritative R, 24,396,379 already in temporary P, and 299,471 fresh in P.
Thus 98.600% of attempts repeat pending marks. They do not create 24.7 million
standing requests. There are 3,874 commits, 6,063 created dynamic keys and an
aggregate P peak of 4,610 rows / 198,895 members. All 162,300 matching calls use
direct slot lookup; none scans broad keys.

OWASP performs 10,685 batch geometry tests and 57,242 row attempts. ThingsBoard's
partial run performs 1,074,306 tests and 216,775,968 row attempts. These steps count
input/row combinations, not trie depth or separate facts. Concrete leaf/row
attempts from the direct emitter also contribute to this counter; it is not a
notification-only measurement. Slot-directed delta
matching is a candidate to avoid this cross product. It needs profile attribution
before a CPU saving is predicted.

ThingsBoard still admits millions of initials and caller inputs before failing.
The remaining work is not all request transport. The current counts do not give
the quotient obtained by removing the initial mark from an otherwise identical
premise. They do not prove that all these groups can be merged without losing
caller or ND-premise correlation.

Its frozen static plan has 1,452 distinct precomputed map roots and 3,000,408
physical map rows. The per-method logical totals are 15,671,691 rows and 63,549,683
mark memberships. Mark payloads are shared: the reported 47,142 mark-set roots
and 55,832 owned primitive cells also include wide/class metadata. The logical
63.5 million memberships are not 63.5 million separately allocated mark cells.
The 3,984 unique positions include class/root positions; that count is not a
distinct-field count. Factoring the static plan can avoid eager map-row expansion
while preserving its logical requests. No retained-byte saving is measured.

The method-empty barrier's longest observed pending wait is 178.469 seconds in
OWASP and 14.266 seconds in ThingsBoard's partial run. It measures first pending
admission to commit start. It is not request CPU time. P gauges and caller-store
gauges can be zero after release; that is not a heap census at peak use.

## Profile review

All eight JFR exports passed. Each recording's event totals agree with its summary;
the raw files remained unchanged. All 44 actual phase windows have complete
finalized chunk-header coverage, with no gaps. This includes every reached engine,
setup, barrier and output phase. All eight actual exports completed without retry.

The table below uses main's legacy full-scan window, including trace generation,
and the current run-1 log window. They are different phases and workloads. The
table is a sample census, not a speedup calculation. Allocation is the sum of
sampled byte weights over the window, not a live or retained size. GC pause values
are top-level pauses clipped to that window; they are not GC CPU time.

| Project / version / window | Java samples | Sampled allocation GiB | GC starts | Clipped pauses s |
| --- | ---: | ---: | ---: | ---: |
| OWASP main; full scan complete | 4,348 | 65.825 | 100 | 1.098 |
| Conductor main; full scan complete | 3,182 | 35.360 | 32 | 0.784 |
| ThingsBoard main; full scan complete | 6,782 | 77.046 | 41 | 2.002 |
| Stirling-PDF main; full scan complete | 1,209 | 9.483 | 4 | 0.192 |
| OWASP current; run-1 complete | 7,268 | 311.776 | 608 | 7.262 |
| Conductor current; run-1 failed | 1,337 | 25.566 | 16 | 0.909 |
| ThingsBoard current; run-1 failed | 12,610 | 189.825 | 139 | 9.025 |
| Stirling-PDF current; run-1 failed | 2,198 | 42.485 | 72 | 1.401 |

These exact named-method rows show where the recorded run-1 stacks spend work.
Each cell is **Java sample % / sampled allocation %**. Inclusive rows overlap;
do not sum them. A dash means that the method is outside the saved top 50, so
its share is unavailable, not zero.

| Recorded method | OWASP, complete run-1 | Conductor, failed run-1 | ThingsBoard, failed run-1 | PDF, failed run-1 |
| --- | ---: | ---: | ---: | ---: |
| `JIRCallPlanBuilder.build` | 25.21 / 22.47 | 56.84 / 55.03 | 45.56 / 41.65 | 61.97 / 46.90 |
| `RunMethodCallServices.cleanChain` | 30.99 / 53.67 | — / — | — / — | — / — |
| `cleanFacts` | 21.57 / 34.29 | — / — | — / — | — / — |
| `JIRLocalAliasAnalysis.compute` | — / — | 42.03 / 42.18 | 32.56 / 29.07 | 47.86 / 32.89 |
| `aliasesPersistedThroughCall` | 9.30 / — | 35.08 / 35.43 | 27.42 / 24.23 | 43.72 / 30.77 |

OWASP's field-cleaner read/clean/write path is a direct optimization target:
`cleanChain` alone covers 179,655,841,312 sampled allocation bytes. Its nested
statement helper covers 35.36% of sampled allocation. These overlap. Call-plan
construction is another material path. Source review shows repeated raw validation
and repeated normalization of an already normalized condition inside one build.
The profile does not establish how much of the whole build those repeated passes
consume.

The failed project prefixes spend much of their visible work building call plans
and computing/binding aliases. Alias results already compute once per analysis
object. Those rows do not prove cache churn. Eager forward alias-stage construction
may be avoidable when no admitted result uses it, but other rule/resolution paths
can need the same result first. The next proposal makes that eligibility a gate.

OWASP's largest allocation classes include object arrays, 15.13%; integer arrays,
9.44%; `LinkedHashMap`, 5.87%; and `ArrayList`, 4.19%. The other projects also show
arrays and collection storage near the top. These are allocation classes, not
retained owners. They cannot identify a removable cache or prove that request
stores own the peak RSS. The completed run-1's 36,592 admitted initials coexist
with hundreds of GiB of sampled allocation: counting initial shapes alone does
not describe repeated transfer and form-construction work.

Two requested 30-second native CPU captures exist, both for OWASP current, with
profiler return 0. The normal capture lies wholly inside run-1, approximately
60 seconds after full-analysis start. It has 4,492 weighted samples: cleaner
chains, 46.62%; statement/type filtering, 20.01%; accessor checks, 15.38%; request
admission/commit, 5.48%; batch request join, 0.067%; and new-input emission, 0.223%.
These are overlapping frame families. The disjoint stack partition assigns
28.83% to matched native GC frames and 6.37% to compiler work. This denominator
includes native work; it differs from the JFR Java-sample denominator.

The near-limit capture lies wholly in **run-5**, approximately
22:15:47–22:16:17 UTC, with 4,461 samples. It shows call-plan builds in 53.44%, sink
building in 33.00% and signed normalization in 13.76%. These groups overlap.
This later-run result must not be relabeled as a run-1 profile. It also does not
distinguish cold construction from rebuilding after eviction. The other scans
end their full-analysis phase before the normal +60-second trigger; they have
JFR coverage but no forced extra CPU window. Their near-limit triggers are not
reached.

There is a capture limit. Export requested stack depth 256, but every selected
window has a maximum observed depth of 64. The saved JVM arguments do not set a
deeper recording stack depth. OWASP run-1 has 53 truncated Java stacks out of
7,268 samples and 66 truncated allocation stacks out of 40,816 events. The
failed project prefixes also contain truncated stacks. Deeper printing cannot
recover uncaptured callers. Named-method shares refer to the recorded frames;
absence from the rankings does not prove absence of work. In particular, request
admission, join and emitter methods are outside the saved JFR top 50 and have no
whole-run percentage here. Use the normal CPU window only for its own interval.

Full tables, all five OWASP engine windows, allocation classes, missing-stack
weights and coverage/truncation flags are in
`B/preliminary/jfr-final-review/profile-eight-case-summary.json`, SHA-256
`d93ca7e05bb6453d4ceef857e1cc442a234c3004584928fad44915eb881dac4f`.
`terminal-operator-checks.json` confirms source/event/file checks and release of
all three analysis locks. The CPU windows are bound separately in
`B/preliminary/owasp-run1-cpu.json` and `owasp-near-limit-cpu.json`.

## Acceptance and next action

The core tests pass, but the project recall gate and run-1 time target do not pass.
First close and reproduce the frozen static-domain coverage gap. Then test the
proposed reductions against completed run-1 workloads. Keep the same exact project
configurations and strict location comparison. The next proposal ranks current
opportunities and states where memory ownership or eligible work is still unknown.

The numeric source/log extraction is
`B/report-preparation/metrics/review-20261015T222918.081570Z/metrics.json`.
Its SHA-256 is `4a0350ccf7333198cfa0a8802d673d37fc54a5c1daded5dc27195479e94355b1`.
All eight progress summaries agree with the run/barrier/frontier controls.
