# Bidirectional project test report

Historical pre-O1–O3 measurements. Latest reached: OWASP **run-1 F/L1**, Conductor **run-2 B/L2**,
ThingsBoard **run-1 F/L1**, PDF **run-3 F/L3**; all stop on OOM. The last complete engine and accepted
forward report are run-1 for Conductor and run-2/run-1 respectively for PDF; neither exists for OWASP
or ThingsBoard. Main has no bidi iteration. See the [latest proposal](../design/bidirectional-core-optimization-20261013.md)
and [rollback retest record](bidirectional-core-rollback-20261013.md) for later evidence.

Measured on 12 October 2026. All eight selected runs finished. All four main runs completed. All four bidi runs stopped on the memory guard. No run reached its time limit. The required finding and performance gates do not pass.

The baseline is remote main `f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The tested branch is `517fef60e` with the runner and timing patch saved in each run manifest. Use the [input inventory](bidirectional-project-inputs.md) for fixed source heads, input hashes, rules, and commands. Use the [findings report](bidirectional-project-findings.md) for each missing location and its cause. The [live-heap report](bidirectional-project-memory.md) identifies the retained memory owners in all four bidi scans.

## Findings by project

These are unique exported sink identities. They include the rule ID, full method signature, and sink fingerprint. Trace counts are not finding counts. Raw source locations are kept in the comparison files.

| Project | Heap / IFDS limit | Main status | Bidi status | Main sinks | Bidi sinks | Main sinks preserved | Main sinks absent | Added sinks |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 8 GiB / 1000 s | OK | OOM, run 1 | 4112 | 0 | 0 | 4112 | 0 |
| Conductor | 8 GiB / 1080 s | OK | OOM, run 2 | 7 | 300 | 7 | 0 | 293 |
| ThingsBoard | 12 GiB / 1080 s | OK | OOM, run 1 | 14 | 0 | 0 | 14 | 0 |
| Stirling-PDF | 8 GiB / 1080 s | OK | OOM, run 3 | 17 | 61 | 15 | 2 | 46 |

OWASP main also meets the CI check of 4112 traces. OWASP and ThingsBoard bidi have no complete forward run. Their empty reports follow the incomplete-run contract. The cause of the lost output is the memory stop; these counts do not prove thousands of separate transfer defects.

Conductor preserves all main sinks, but does not finish refinement. Its saved report has 3 CONFIRMED and 298 DEMAND entries. One virtual instruction has no source location and is dropped during SARIF export: 301 internal entries become 300 results. The 293 additions are unfinished output, not a measured final false-positive count.

Stirling-PDF saves run-1 output after run-3 fails: 9 CONFIRMED and 141 DEMAND entries. It exports 150 occurrences at 61 unique sinks. Its two missing main locations are XSS return sinks. The same rules and full methods appear in bidi at a different return. With no full trace, exit-location resolution selects the last return in the method. This includes a `return null` in `PipelineController`. Keep the two strict location regressions in the report; do not silently merge their source spans or infer a lost taint path from the location change alone.

The no-FN requirement is not established by an incomplete scan. None of these four bidi runs supports a claim of complete recall or a final false-positive rate. The root causes of the observed output differences are recorded; further analysis after a memory fix is still required to assess final results.

## Performance by project

IFDS time comes from the start and finish markers and includes prescan. Harness wall time includes Gradle setup, report generation, and profiler cleanup. It can exceed the command time by a few seconds. RSS includes native memory and can exceed the heap limit. The shorter failed runs do not show a performance improvement. These are single measurements on a shared host, not statistical estimates.

| Project | Main IFDS | Bidi IFDS | Main wall | Bidi wall | Main peak RSS | Bidi peak RSS | Main GC pauses | Bidi GC pauses |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 104.03 s | 89.85 s | 153.30 s | 124.30 s | 8.65 GiB | 9.25 GiB | 3.37 s | 9.22 s |
| Conductor | 93.81 s | 81.13 s | 185.55 s | 128.26 s | 9.41 GiB | 9.46 GiB | 7.83 s | 13.83 s |
| ThingsBoard | 189.96 s | 193.26 s | 230.38 s | 229.41 s | 13.51 GiB | 13.59 GiB | 10.36 s | 23.09 s |
| Stirling-PDF | 20.02 s | 101.34 s | 49.10 s | 130.26 s | 9.05 GiB | 9.58 GiB | 2.11 s | 12.41 s |

All four show a resource regression: main finishes within the same heap, while bidi does not. Stirling-PDF also uses over five times the main IFDS time before it fails. No timeout or outer process kill occurred. Bidi's run guard uses the specified 90% heap threshold; the terminal OOM is a guard result, not necessarily a thrown Java `OutOfMemoryError`.

| Project | Main prescan | Bidi prescan | Run 1 engine / status | Run 1 barrier | Run 2 engine / status | Run 2 barrier | Run 3 engine / status |
| --- | ---: | ---: | --- | ---: | --- | ---: | --- |
| OWASP | 40.14 s | 47.60 s | 40.930 s / OOM | No barrier | Not run | Not run | Not run |
| Conductor | 45.62 s | 25.72 s | 40.980 s / COMPLETE | 0.809 s | 13.190 s / OOM | No barrier | Not run |
| ThingsBoard | 158.93 s | 148.98 s | 41.662 s / OOM | No barrier | Not run | Not run | Not run |
| Stirling-PDF | 14.61 s | 17.72 s | 40.734 s / COMPLETE | 0.530 s | 23.308 s / COMPLETE | 0.103 s | 18.810 s / OOM |

Limits are 1, 2, and 3 for runs 1, 2, and 3. No limit-0 run occurs. Run-1 engine time is about 45.6%, 50.5%, 21.6%, and 40.2% of total bidi IFDS time, respectively. In ThingsBoard, prescan uses most of that time. Its last run-1 progress sample has 8,878,468 handled events and 538,074 events in flight. Conductor run 1 handles over ten million events before it completes. These are workloads to examine before changing field-limit policy.

## Profile results and optimization points

All eight workloads have continuous JFR recordings. The summary separates prescan, IFDS, each run, and legacy full scan. It also extracts Java samples for seconds 60–90 of IFDS and the last 30 seconds of each phase. The full first window exists for OWASP main, Conductor main, both ThingsBoard runs, and Stirling-PDF bidi. OWASP bidi has 29.85 seconds and Conductor bidi has 21.13 seconds after the first minute before analysis stops. Stirling-PDF main finishes IFDS in 20.02 seconds, so its recording covers that shorter workload. A separate async-profiler CPU capture succeeds for OWASP main. Stirling-PDF bidi also produces a capture, but its process exits before the requested 30 seconds finish. Other full-analysis runs finish before that delayed capture can start. Every workload finishes far before the near-time-limit window; no near-limit capture is claimed.

JFR execution samples cover Java stacks. Inclusive shares overlap. Allocation sample weights estimate allocated bytes; they do not measure retained heap. GC pause values above cover the entire recorded process. Profiles identify expensive paths, but do not prove which objects occupy the heap at the guard.

| Project | Measured bidi hot path | First optimization to test |
| --- | --- | --- |
| OWASP | Run 1: call-plan construction 36.15% and sink-form construction 35.15% of Java samples; condition cubes 23.91%. Sampled allocation weight is 47.27 GB. | Reuse immutable lowered rule parts under correct context keys. Remove duplicate alternatives before DNF and array-choice products create owned path facts and sinks. Measure form counts and retained size before changing the expansion. |
| Conductor | Run 1: graph construction 6.28%. Queue comparator boxing accounts for about 7.64% of allocation weight. Run 2: trie subtraction 9.92% of allocation weight. | Cache the fixed virtual-method graph within its valid lifetime. Compare queue rank, steps, and sequence as primitives. Then measure trie-copy work and preserve immutable ownership. |
| ThingsBoard | Run 1: graph construction 15.00% of Java samples and 10.05% of allocation weight. Alias-set collection uses 7.52% of allocation weight; queue comparison uses about 6.92%. | Remove repeated graph construction. Reuse stable alias views or iterate without copying whole sets. Remove comparator boxing. Measure the large pending-event population and retained state. |
| Stirling-PDF | Graph construction uses 18.76% of run-1 samples and 22.27% of run-2 samples. Run 3 spends 16.67% in form-stage application. Trie subtraction accounts for 13.70% of run-2 allocation weight. | Cache graph traversal data, then reduce short-lived callbacks and trie copies. Repair exit source-location mapping as a separate reporting change. Preserve distinct return locations. |

The graph cost has a concrete source cause: [JIRVirtualMethod.flowGraph](../../core/opentaint-ir/opentaint-ir-core/src/main/kotlin/org/opentaint/ir/impl/features/classpaths/virtual/JIRVirtualMethod.kt) constructs a new graph on each call. [Exception-boundary traversal](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JTryBoundaryExceptionsApplicationGraph.kt) requests that graph for each successor or predecessor lookup. A cache must respect body initialization and method lifetime. It must not retain obsolete run actors.

The queue cost also has a concrete source: [EventQueue](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EventQueue.kt) uses `compareBy` with boxed rank, steps, and sequence keys. A primitive comparator must retain the same ordering and FIFO tie break. These changes concern repeated implementation work. They do not require a new exclusion rule or a limit-0 run.

All four prescans also spend substantial time in shared rule resolution and condition matching. This is a separate common cost. Improving it alone will not explain or remove the bidi memory failure after prescan.

## Live-heap follow-up

Four diagnostic repeats save live heaps at the same memory-stop stages, with the same project inputs and configured limits. The strong method-form cache retains 6.459 GB in OWASP (83.14% of the live heap), 2.412 GB in Conductor (31.43%), 5.622 GB in ThingsBoard (48.96%) and 4.361 GB in Stirling-PDF (56.85%). GB here means 10^9 bytes. Conductor also has 3.633 GB in the IR database/storage owner union; Stirling-PDF has 1.077 GB in its current actor/store union. These retained unions overlap and must not be added.

OWASP retains millions of generated XSS sink alternatives and owned literal copies. Conductor and ThingsBoard retain millions of small summary containers. ThingsBoard also resolves and caches many broad method-exit rules and large conditional source conjunctions. A measured OWASP condition has a redundant OR that expands 210 raw combinations instead of 14 choices after Boolean absorption. Stirling-PDF keeps large forward conjunctions and their expanded reversed branches, plus eager fact-group containers in active run-3. The complete actor tables show only the current run in each heap. Old run managers or actors are not the observed cause.

This makes form representation the first shared memory optimization target. The CPU and allocation changes above remain useful. The diagnostic dump adds a GC and a process pause, so its timings do not replace the control measurements. The optional dump hook is off in normal runs. Full byte counts, strong root paths, rule/plan examples and safe representation candidates are in the [memory report](bidirectional-project-memory.md). Its artifacts are under `/drive-testcomp/opentaint-go-rules/bidi-memory-20261012`.

## Evidence and checks

The complete artifact root is `/drive-testcomp/opentaint-go-rules/bidi-real-20261012`. Each `<project>/<main|bidi>` directory contains the command and input manifest, terminal summary, analyzer and console logs, per-second RSS and CPU samples, SARIF, JFR, and profile summary. `comparison` contains all missing and added identities grouped by rule and source path, occurrence counts, raw spans, and terminal-state evidence. `tested-current.patch` saves the source change used by all four bidi runs; its normalized hash matches their manifests. Earlier setup failures are archived in separate named directories and are not included in the measured tables.

Recreate the comparison and profile summaries with:

```sh
python3 scripts/compare_bidi_projects.py --root /drive-testcomp/opentaint-go-rules/bidi-real-20261012
python3 scripts/summarize_bidi_profiles.py --root /drive-testcomp/opentaint-go-rules/bidi-real-20261012
python3 -m unittest discover -s scripts -p 'test_*bidi*.py'
```

Both analyzer builds pass. The actual project commands prove that the new flag selects bidi and that main keeps its existing engine. The driver test and integration test pass all 16 cases. All 21 Python comparison and profile checks pass. Their logs and the driver XML are saved in the artifact root's `validation` directory. The runner patch changes no AP operation or exclusion handling. It adds JVM engine selection, increasing positive limits, and time/status logs. The benchmark output establishes the report findings above; it does not establish that phase 4 passes.
