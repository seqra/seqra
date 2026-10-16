# Static-domain repair and frozen requests: project measurements

Date: 16 October 2026. Status: working report; final factoring results are pending.

The corrected materialized-map control is `d330a335a8ac4faf8ba839f2a74bcfd2500c79bd`.
Factoring is integrated as `4acde739bdc23d48eacb3c125429c461597f75be`, from
isolated commit `bbdbe7307`. Its fixed-project measurements are pending. Fresh main is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`.

Use the [selected design](../design/bidirectional-static-domain-and-frozen-requests-20261016.md).
Historical reports are not measurements of these revisions.

## Fixed workload and evidence

Evidence root, called B below:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z
```

Each phase has `launch.json`, before/after core source bindings and an actual
terminal receipt. Each project has its manifest, analyzer log, process identity,
resource samples, SARIF, profile captures and terminal summary. An exit from the
matrix harness does not establish analyzer success.

| Project | Heap | IFDS time limit | Outer time limit |
| --- | ---: | ---: | ---: |
| OWASP | 8 GiB | 1,000 s | 1,200 s |
| Conductor | 8 GiB | 1,080 s | 1,200 s |
| ThingsBoard | 12 GiB | 1,080 s | 1,200 s |
| Stirling-PDF | 8 GiB | 1,080 s | 1,200 s |

The phases use the existing project models and direct Gradle
`:runProjectAnalyzer`. They use the same rules, project inputs and Java 17.
The pre-scan input audit passed 80 checks covering 41 content datasets. The
post-scan audit and endpoint comparison are pending. Previous raw captures were
removed before this batch; `B/prior-raw-cleanup.json` records the cleanup.

## Fresh main

All four main scans completed. Main uses the legacy engine and has no
bidirectional reached, completed or accepted run number.

| Project | Status | Command wall s | Peak process RSS bytes | Bidi run |
| --- | --- | ---: | ---: | --- |
| OWASP | OK | 186.436 | 7,951,122,432 | N/A, legacy |
| Conductor | OK | 154.322 | 10,140,729,344 | N/A, legacy |
| ThingsBoard | OK | 323.725 | 14,585,487,360 | N/A, legacy |
| Stirling-PDF | OK | 87.257 | 7,333,937,152 | N/A, legacy |

The command wall scope includes setup and report export. It is not the run-1
engine scope. RSS includes memory outside the configured Java heap.
`B/fresh-main/terminal.json` confirms stable source and HEADs for this phase.

## Corrected map control

The phase finished at 08:04:46 UTC on 16 October 2026. All analyzer processes
are gone. Its wrapper receipt confirms unchanged core source, HEADs and capture
harness. Analyzer outcomes below remain resource failures, not successful scans.

| Project | Status | Latest reached | Latest completed engine | Accepted forward report | Run-1 engine s |
| --- | --- | --- | --- | --- | ---: |
| Stirling-PDF | OOM | run-4, backward, limit 4 | run-3, forward | run-3 | 168.350, COMPLETE |
| Conductor | OOM | run-1, forward, limit 1 | None | None | 105.805, OOM |
| ThingsBoard | OOM | run-1, forward, limit 1 | None | None | 106.335, OOM |
| OWASP | TIMEOUT | run-6, backward, limit 6 | run-5, forward | run-5 | 213.730, COMPLETE |

Stirling-PDF continues beyond the old static-domain exception and accepts a
report. Its completed run-1 exceeds the 60-second engine target. Conductor also
continues beyond the old exception, but reaches OOM before completing run-1.
Its failed prefix does not establish a completed run-1 time.

| Project/run | Direction | Engine s | Status | Barrier s |
| --- | --- | ---: | --- | ---: |
| Stirling-PDF/run-1 | Forward | 168.350408405 | COMPLETE | 1.295884956 |
| Stirling-PDF/run-2 | Backward | 27.927794890 | COMPLETE | 0.176676361 |
| Stirling-PDF/run-3 | Forward | 84.521827241 | COMPLETE | 0.472466056 |
| Stirling-PDF/run-4 | Backward | 164.003324173 | OOM | No successful barrier |
| Conductor/run-1 | Forward | 105.805429394 | OOM | No successful barrier |
| ThingsBoard/run-1 | Forward | 106.334662236 | OOM | No successful barrier |
| OWASP/run-1 | Forward | 213.729949159 | COMPLETE | 2.846339002 |
| OWASP/run-2 | Backward | 184.439226323 | COMPLETE | 0.749906612 |
| OWASP/run-3 | Forward | 78.895283839 | COMPLETE | 3.734110215 |
| OWASP/run-4 | Backward | 227.970837077 | COMPLETE | 1.542513527 |
| OWASP/run-5 | Forward | 74.165562483 | COMPLETE | 3.394645965 |
| OWASP/run-6 | Backward | 121.014597360 | TIMEOUT | No successful barrier |

The Stirling-PDF guard snapshot has 12,063,819,900 bytes. It was captured during
**run-4, backward**, before cancellation. Its accepted report is from run-3.
The Conductor guard snapshot has 11,721,619,519 bytes and was captured during
**run-1, forward**. Both have paired snapshot START/END markers. Snapshot file
bytes are not retained heap bytes. Owner analysis is pending until captures end.

The ThingsBoard guard snapshot has 18,393,949,142 bytes and paired START/END
markers during **run-1, forward**. Its corrected frozen plan has 3,000,410
physical rows and 188,923,219 logical per-method mark memberships. Mark payloads
are shared, so the membership total is not an allocated mark-cell count.

The corrected plan reports 19,663 physical frozen rows for Stirling-PDF and
4,492 for Conductor. These row counts alone do not identify the source of either
OOM. The current implementation already packs concrete output marks together;
distinct original initial premises can still have separate method state.
Live owner counts and CPU profiles must determine the next proposal.

## Strict main-to-map findings

The strict comparator preserves complete rule/sink identities, raw locations and
spans, and occurrence multiplicity. The reference and candidate inputs match.
Its successful execution does not make a failed analyzer a successful scan.

| Project | Main identities | Map identities | Missing | New identities | Main/map occurrences |
| --- | ---: | ---: | ---: | ---: | ---: |
| OWASP | 4,112 | 4,112 | 0 | 0 | 4,112 / 9,179 |
| Conductor | 7 | 0 | 7 | 0 | 7 / 0 |
| ThingsBoard | 14 | 0 | 14 | 0 | 14 / 0 |
| Stirling-PDF | 17 | 56 | 3 | 42 | 17 / 128 |

Conductor and ThingsBoard have valid empty exports after run-1 OOM, with no
accepted forward report and no attempted report generation for a finding. Their
21 missing identities follow the resource failure. Stirling-PDF has three strict
location losses in its accepted run-3 report; source triage remains in progress.
Same-rule later returns do not establish the same path or location coverage.
The 42 new PDF identities are additional candidates, not confirmed false
positives or confirmed feasible traces. Its 128 exports and OWASP's 9,179 exports
are Simple reports with no generated concrete trace evidence.

Exact results are under `B/comparisons/main-map/`, including its index and
missing-finding triage. The comparator ran after project captures, during the
application control; its resource receipt records that overlap.

## Integrated validation

The integrated commit passed all 1,480 common tests and 408 JVM tests, with no
failures, errors or skips. Application test compilation and root analyzer classes
passed. The gate has stable before/after source bindings and archived XML at
`B/validation/integrated-common-jvm/review/`.

The fresh map-control application selection ran 158 tests: 149 pass, eight
failures, no errors and one existing skip. Independent XML review confirms the
same expected and actual rule-ID multisets as the historical eight failures:
26 extra case/rule memberships and zero missing expected IDs. This remains a
failed strict gate.
The factored gate also ran 158 tests with the same pass/fail/error/skip counts.
Its testcase identities and failed rule-ID memberships/multiplicities match
the fresh map control. Six raw assertion lists differ in order only. The raw
collector differences remain preserved; this does not turn either failed gate
into a pass. Archives are under `B/validation/{corrected-map-app,factored-app}/review/`.

## Required remaining evidence

1. Retain the completed factored application comparison and its known failures.
2. Freeze the final measured commit and retain its validation/source binding.
3. Measure all four fixed projects on the integrated commit.
4. Compare strict identities, full locations/spans and occurrence counts against
   fresh main and the corrected map control. Finding recall is not yet verified.
5. Export the closed profiles, establish actual recording coverage and attribute
   CPU/allocation/GC by setup, engine, barrier and capture window.
6. Analyze every required OOM snapshot with valid, nonadditive owner measurements.
   A missing required snapshot is an evidence gap.
7. Complete the post-input audit, then write the final performance conclusions
   and rank the next optimizations from the final measurements.

No factoring speedup, saved-byte total or no-missing-finding result is claimed
by this working report.
