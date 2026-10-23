# Static summary erasure: detailed measurement evidence

Campaign: 22–23 October 2026. Status: **CLOSED24; representative heap analysis complete**.
This is the evidence appendix to the [primary report](bidirectional-static-summary-erasure-performance-20261022.md).
Candidate: `b68b3a1c9389c8d1b8ab6ad513af0c586a546895`.
Fresh main reference: `f945f92c5ae554ff4f5fc7696ee5a269210478bb`.
Read the [selected spec](../../spec/run1-static-summary-erasure.md) and
[implementation validation](bidirectional-static-summary-erasure-validation-20261022.md).

## Scope and current result

The implementation is committed. It removes run-1 static-to-static summary
storage, publication and caller relay. Producer effects remain. Run-2 uses
actual-input query completion. The same binary selects GROUP or FULL in
run-1. Later runs use FULL.

At this snapshot, all four fresh main commands and **24/24 candidate
commands are closed**. No candidate command remains open.

| Project / run-1 mode | Run-1 COMPLETE / attempts | Run-1 median (range), s | Latest reached | Latest COMPLETE | Latest accepted forward | Complete within 60 s |
| --- | ---: | --- | --- | --- | --- | ---: |
| Thingsboard / GROUP | 3/3 | 155.560 (131.923–177.971) | run-2 OOM | run-1 | run-1 | 0/3 |
| Thingsboard / FULL | 0/3 | No median; OOM prefixes 208.315–355.533 | run-1 OOM | None | None | 0/3 |
| Conductor / GROUP | 3/3 | 18.213 (17.677–18.929) | run-2 OOM | run-1 | run-1 | 3/3 |
| Conductor / FULL | 3/3 | 14.592 (14.549–14.942) | run-2 OOM | run-1 | run-1 | 3/3 |
| Stirling-PDF / GROUP | 3/3 | 31.766 (25.349–32.342) | run-2 OOM | run-1 | run-1 | 3/3 |
| Stirling-PDF / FULL | 3/3 | 25.131 (24.782–32.448) | run-2 OOM | run-1 | run-1 | 3/3 |
| OWASP / GROUP | 3/3 | 210.170 (178.420–239.889) | run-4 TIMEOUT (1), run-6 TIMEOUT (1), run-8 TIMEOUT (1) | run-3 (1), run-5 (1), run-7 (1) | run-3 (1), run-5 (1), run-7 (1) | 0/3 |
| OWASP / FULL | 3/3 | 204.290 (131.253–205.207) | run-6 TIMEOUT (2), run-8 TIMEOUT (1) | run-5 (2), run-7 (1) | run-5 (2), run-7 (1) | 0/3 |

Medians use COMPLETE run-1 engines; OOM prefix times include diagnostic
heap capture. All timings are profiled; host differences prevent a causal
speedup claim. Thingsboard FULL has no completed run-1 comparison ratio.

Do not use this closed cohort as a no-FN, whole-engine soundness or final
performance acceptance claim. Closed comparisons retain all 14 main
Thingsboard locations in GROUP and all seven main Conductor locations in
both modes. **Two baseline Stirling-PDF locations remain MISSING in both
modes.** These run-1 reports are complete; the later OOM does not explain
the missing locations. The source-backed method-exit location
divergence does not prove equivalent return-branch or source-flow handling.
Large provisional candidate sets remain. All repetitions, strict finding
comparisons and representative heap ownership analyses are complete.

## Measurement contract

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-static-summary-erasure-20261022`.
The launch receipt is `preparation/paired-launch.json`. The progress
receipt is `preparation/paired-progress.json`. The CLOSED24 terminal receipt
is `preparation/paired-terminal.json`. It binds all 24 closed summaries and
confirms `source_unchanged=true` and `inputs_unchanged=true`. Its SHA-256 is
`7f15220c462e9910f195f86959dae9a888c8631592be0f494f5650b5ae0dd20b`.

Commands use Java 17 and Gradle `runProjectAnalyzer` directly. Aliases and
static propagation are enabled. Batch size is 2000; multi-release JAR
selection is disabled. Conductor keeps its configured extension rules,
passthrough approximations and compiled dataflow approximations.

| Project | Heap | IFDS timeout | Outer timeout |
| --- | --- | --- | --- |
| Thingsboard | 12 GiB | 1080 s | 1200 s |
| Conductor | 8 GiB | 1080 s | 1200 s |
| Stirling-PDF | 8 GiB | 1080 s | 1200 s |
| OWASP | 8 GiB | 1000 s | 1200 s |

Each project has three serial GROUP/FULL pairs. Pair order is GROUP/FULL,
FULL/GROUP, then GROUP/FULL. Each command starts a fresh analyzer JVM.
The mode property is checked in the actual JVM command and the engine log.
The committed runtime source, rules, models and observation settings are
fixed. Both modes use the same Gradle build and runtime classpath. The
receipts do not independently hash every compiled class or dependency for
each command. The input
census binds configured model YAML, serialized rules and Conductor
extension bytes; it does not hash every large source or dependency tree.

At 20:36:22 UTC, only the campaign launcher was suspended to create a gap
after the second GROUP command. Its harness and analyzer continued without
suspension and closed. Two MAT reports then ran in the inter-command gap.
The same launcher resumed at 21:09:31 UTC with the third GROUP command.
Neither MAT job overlapped a measured analyzer command. The signal and
process identities are recorded in
`preparation/first-heap-analysis-gap.json`. No analyzer process was suspended.

At 21:16:25 UTC, only the same launcher was suspended again. The third
GROUP harness and analyzer continued without interruption and have closed.
Three MAT reports ran after that command. The same launcher resumed at
21:39:55 UTC with the third FULL command. No MAT job overlapped a measured
analyzer command. The receipt is
`preparation/second-heap-analysis-gap.json`; no analyzer was suspended.

All commands have continuous JFR, GC logs and the same optional counters.
They are **profiled measurements**. A skipped native sample does not make
a command unprofiled. There are no unprofiled timing pairs in this cohort.
Native CPU windows start after one minute of run-1 if it is still active,
and near the configured limit. Phase records determine what each window
actually covers. MemoryManager captures a live heap before cancellation.

Main has only the established opt-in MemoryManager snapshot observer
patch. The fresh main source and patch hashes are in the launch receipts.
Main uses the legacy engine; its full-scan interval is not a candidate
run-1 interval. Historical reports use other revisions and are not fresh
pre-patch controls for a causal speedup claim.

## Fresh main reference

All four commands closed successfully. Main has no numbered bidirectional
iteration. It exported all traces, with no failed or unexported trace.

| Project | Prescan s | Legacy full scan s | Whole command s | SARIF results | Latest iteration |
| --- | ---: | ---: | ---: | ---: | --- |
| Thingsboard | 165.099 | 35.199 | 245.367 | 14 | Not applicable |
| Conductor | 31.832 | 26.450 | 109.197 | 7 | Not applicable |
| Stirling-PDF | 15.020 | 5.454 | 52.095 | 17 | Not applicable |
| OWASP | 38.341 | 51.863 | 137.216 | 4112 | Not applicable |

OWASP has 4,112 distinct primary keys and 4,112 distinct strict keys,
with one occurrence each. All method identities and full source spans
are present. Its 2,282 source paths exist and every span is within its
file. The receipt is
`validation/finding-comparison/main/OWASP-baseline-comparison-receipt.json`.

## Closed candidate commands and iterations

| Project | Pair / mode | Command status | Whole command s | Latest reached | Latest COMPLETE | Latest accepted forward report |
| --- | --- | --- | ---: | --- | --- | --- |
| Thingsboard | 1 / GROUP | OOM | 865.058 | run-2 | run-1 | run-1 |
| Thingsboard | 1 / FULL | OOM | 709.219 | run-1 | None | None |
| Thingsboard | 2 / FULL | OOM | 554.283 | run-1 | None | None |
| Thingsboard | 2 / GROUP | OOM | 968.570 | run-2 | run-1 | run-1 |
| Thingsboard | 3 / GROUP | OOM | 671.931 | run-2 | run-1 | run-1 |
| Thingsboard | 3 / FULL | OOM | 410.133 | run-1 | None | None |
| Conductor | 1 / GROUP | OOM | 167.656 | run-2 | run-1 | run-1 |
| Conductor | 1 / FULL | OOM | 155.302 | run-2 | run-1 | run-1 |
| Conductor | 2 / FULL | OOM | 158.282 | run-2 | run-1 | run-1 |
| Conductor | 2 / GROUP | OOM | 165.581 | run-2 | run-1 | run-1 |
| Conductor | 3 / GROUP | OOM | 161.631 | run-2 | run-1 | run-1 |
| Conductor | 3 / FULL | OOM | 152.273 | run-2 | run-1 | run-1 |
| Stirling-PDF | 1 / GROUP | OOM | 357.993 | run-2 | run-1 | run-1 |
| Stirling-PDF | 1 / FULL | OOM | 202.353 | run-2 | run-1 | run-1 |
| Stirling-PDF | 2 / FULL | OOM | 233.539 | run-2 | run-1 | run-1 |
| Stirling-PDF | 2 / GROUP | OOM | 477.433 | run-2 | run-1 | run-1 |
| Stirling-PDF | 3 / GROUP | OOM | 573.077 | run-2 | run-1 | run-1 |
| Stirling-PDF | 3 / FULL | OOM | 284.142 | run-2 | run-1 | run-1 |
| OWASP | 1 / GROUP | TIMEOUT | 1065.671 | run-4 | run-3 | run-3 |
| OWASP | 1 / FULL | TIMEOUT | 1064.074 | run-6 | run-5 | run-5 |
| OWASP | 2 / GROUP | TIMEOUT | 1047.911 | run-6 | run-5 | run-5 |
| OWASP | 2 / FULL | TIMEOUT | 1060.299 | run-6 | run-5 | run-5 |
| OWASP | 3 / GROUP | TIMEOUT | 1038.710 | run-8 | run-7 | run-7 |
| OWASP | 3 / FULL | TIMEOUT | 1039.742 | run-8 | run-7 | run-7 |

All 24 run-1 modes match the actual JVM property and engine-mode record.
All twelve GROUP commands enter run-2 with FULL marks. Every later engine
uses FULL. OWASP reaches the run labels shown in the table, through run-8
for both third-pair commands. The third Thingsboard GROUP
receipt records 671.931 seconds for the analyzer command and 671.989
seconds for the wider harness interval. The table uses the command field,
as for the earlier rows.

| Project / pair / mode | Phase | Status | Engine s | Following barrier s |
| --- | --- | --- | ---: | ---: |
| Thingsboard / 1 / GROUP | Prescan | Complete | 254.466 | Not applicable |
| Thingsboard / 1 / GROUP | run-1, forward, limit 1 | COMPLETE | 155.560 | 10.998 |
| Thingsboard / 1 / GROUP | run-2, backward FULL, limit 1 | OOM | 274.406 | No complete barrier |
| Thingsboard / 1 / FULL | Prescan | Complete | 276.839 | Not applicable |
| Thingsboard / 1 / FULL | run-1, forward, limit 1 | OOM | 355.533 | No complete barrier |
| Thingsboard / 2 / FULL | Prescan | Complete | 180.682 | Not applicable |
| Thingsboard / 2 / FULL | run-1, forward, limit 1 | OOM | 303.285 | No complete barrier |
| Thingsboard / 2 / GROUP | Prescan | Complete | 294.628 | Not applicable |
| Thingsboard / 2 / GROUP | run-1, forward, limit 1 | COMPLETE | 177.971 | 14.599 |
| Thingsboard / 2 / GROUP | run-2, backward FULL, limit 1 | OOM | 323.054 | No complete barrier |
| Thingsboard / 3 / GROUP | Prescan | Complete | 182.733 | Not applicable |
| Thingsboard / 3 / GROUP | run-1, forward, limit 1 | COMPLETE | 131.923 | 10.328 |
| Thingsboard / 3 / GROUP | run-2, backward FULL, limit 1 | OOM | 223.026 | No complete barrier |
| Thingsboard / 3 / FULL | Prescan | Complete | 154.186 | Not applicable |
| Thingsboard / 3 / FULL | run-1, forward, limit 1 | OOM | 208.315 | No complete barrier |
| Conductor / 1 / GROUP | Prescan | Complete | 25.171 | Not applicable |
| Conductor / 1 / GROUP | run-1, forward, limit 1 | COMPLETE | 18.213 | 0.824 |
| Conductor / 1 / GROUP | run-2, backward FULL, limit 1 | OOM | 72.440 | No complete barrier |
| Conductor / 1 / FULL | Prescan | Complete | 25.687 | Not applicable |
| Conductor / 1 / FULL | run-1, forward, limit 1 | COMPLETE | 14.549 | 0.442 |
| Conductor / 1 / FULL | run-2, backward FULL, limit 1 | OOM | 71.940 | No complete barrier |
| Conductor / 2 / FULL | Prescan | Complete | 25.427 | Not applicable |
| Conductor / 2 / FULL | run-1, forward, limit 1 | COMPLETE | 14.942 | 0.379 |
| Conductor / 2 / FULL | run-2, backward FULL, limit 1 | OOM | 75.918 | No complete barrier |
| Conductor / 2 / GROUP | Prescan | Complete | 25.103 | Not applicable |
| Conductor / 2 / GROUP | run-1, forward, limit 1 | COMPLETE | 18.929 | 0.745 |
| Conductor / 2 / GROUP | run-2, backward FULL, limit 1 | OOM | 74.427 | No complete barrier |
| Conductor / 3 / GROUP | Prescan | Complete | 25.234 | Not applicable |
| Conductor / 3 / GROUP | run-1, forward, limit 1 | COMPLETE | 17.677 | 0.818 |
| Conductor / 3 / GROUP | run-2, backward FULL, limit 1 | OOM | 70.599 | No complete barrier |
| Conductor / 3 / FULL | Prescan | Complete | 24.841 | Not applicable |
| Conductor / 3 / FULL | run-1, forward, limit 1 | COMPLETE | 14.592 | 0.324 |
| Conductor / 3 / FULL | run-2, backward FULL, limit 1 | OOM | 70.843 | No complete barrier |
| Stirling-PDF / 1 / GROUP | Prescan | Complete | 18.412 | Not applicable |
| Stirling-PDF / 1 / GROUP | run-1, forward, limit 1 | COMPLETE | 25.349 | 1.357 |
| Stirling-PDF / 1 / GROUP | run-2, backward FULL, limit 1 | OOM | 276.655 | No complete barrier |
| Stirling-PDF / 1 / FULL | Prescan | Complete | 17.579 | Not applicable |
| Stirling-PDF / 1 / FULL | run-1, forward, limit 1 | COMPLETE | 24.782 | 0.186 |
| Stirling-PDF / 1 / FULL | run-2, backward FULL, limit 1 | OOM | 124.966 | No complete barrier |
| Stirling-PDF / 2 / FULL | Prescan | Complete | 19.193 | Not applicable |
| Stirling-PDF / 2 / FULL | run-1, forward, limit 1 | COMPLETE | 25.131 | 0.203 |
| Stirling-PDF / 2 / FULL | run-2, backward FULL, limit 1 | OOM | 156.603 | No complete barrier |
| Stirling-PDF / 2 / GROUP | Prescan | Complete | 21.412 | Not applicable |
| Stirling-PDF / 2 / GROUP | run-1, forward, limit 1 | COMPLETE | 32.342 | 1.507 |
| Stirling-PDF / 2 / GROUP | run-2, backward FULL, limit 1 | OOM | 377.893 | No complete barrier |
| Stirling-PDF / 3 / GROUP | Prescan | Complete | 23.937 | Not applicable |
| Stirling-PDF / 3 / GROUP | run-1, forward, limit 1 | COMPLETE | 31.766 | 2.888 |
| Stirling-PDF / 3 / GROUP | run-2, backward FULL, limit 1 | OOM | 464.748 | No complete barrier |
| Stirling-PDF / 3 / FULL | Prescan | Complete | 26.485 | Not applicable |
| Stirling-PDF / 3 / FULL | run-1, forward, limit 1 | COMPLETE | 32.448 | 0.387 |
| Stirling-PDF / 3 / FULL | run-2, backward FULL, limit 1 | OOM | 176.328 | No complete barrier |
| OWASP / 1 / GROUP | Prescan | Complete | 51.716 | Not applicable |
| OWASP / 1 / GROUP | run-1, forward, limit 1 | COMPLETE | 239.889 | 8.105 |
| OWASP / 1 / GROUP | run-2, backward FULL, limit 1 | COMPLETE | 370.385 | 1.092 |
| OWASP / 1 / GROUP | run-3, forward FULL, limit 2 | COMPLETE | 86.785 | 3.912 |
| OWASP / 1 / GROUP | run-4, backward FULL, limit 3 | TIMEOUT | 236.407 | No complete barrier |
| OWASP / 1 / FULL | Prescan | Complete | 55.175 | Not applicable |
| OWASP / 1 / FULL | run-1, forward, limit 1 | COMPLETE | 205.207 | 4.086 |
| OWASP / 1 / FULL | run-2, backward FULL, limit 1 | COMPLETE | 237.971 | 0.791 |
| OWASP / 1 / FULL | run-3, forward FULL, limit 2 | COMPLETE | 68.961 | 2.498 |
| OWASP / 1 / FULL | run-4, backward FULL, limit 3 | COMPLETE | 231.264 | 0.759 |
| OWASP / 1 / FULL | run-5, forward FULL, limit 4 | COMPLETE | 90.971 | 2.905 |
| OWASP / 1 / FULL | run-6, backward FULL, limit 5 | TIMEOUT | 98.069 | No complete barrier |
| OWASP / 2 / GROUP | Prescan | Complete | 44.224 | Not applicable |
| OWASP / 2 / GROUP | run-1, forward, limit 1 | COMPLETE | 210.170 | 5.556 |
| OWASP / 2 / GROUP | run-2, backward FULL, limit 1 | COMPLETE | 225.130 | 0.558 |
| OWASP / 2 / GROUP | run-3, forward FULL, limit 2 | COMPLETE | 63.498 | 2.297 |
| OWASP / 2 / GROUP | run-4, backward FULL, limit 3 | COMPLETE | 195.106 | 0.703 |
| OWASP / 2 / GROUP | run-5, forward FULL, limit 4 | COMPLETE | 61.763 | 2.291 |
| OWASP / 2 / GROUP | run-6, backward FULL, limit 5 | TIMEOUT | 187.588 | No complete barrier |
| OWASP / 2 / FULL | Prescan | Complete | 52.107 | Not applicable |
| OWASP / 2 / FULL | run-1, forward, limit 1 | COMPLETE | 204.290 | 4.984 |
| OWASP / 2 / FULL | run-2, backward FULL, limit 1 | COMPLETE | 260.409 | 0.974 |
| OWASP / 2 / FULL | run-3, forward FULL, limit 2 | COMPLETE | 74.712 | 2.360 |
| OWASP / 2 / FULL | run-4, backward FULL, limit 3 | COMPLETE | 219.343 | 0.950 |
| OWASP / 2 / FULL | run-5, forward FULL, limit 4 | COMPLETE | 64.081 | 3.181 |
| OWASP / 2 / FULL | run-6, backward FULL, limit 5 | TIMEOUT | 110.405 | No complete barrier |
| OWASP / 3 / GROUP | Prescan | Complete | 32.812 | Not applicable |
| OWASP / 3 / GROUP | run-1, forward, limit 1 | COMPLETE | 178.420 | 4.901 |
| OWASP / 3 / GROUP | run-2, backward FULL, limit 1 | COMPLETE | 214.958 | 0.563 |
| OWASP / 3 / GROUP | run-3, forward FULL, limit 2 | COMPLETE | 58.604 | 2.255 |
| OWASP / 3 / GROUP | run-4, backward FULL, limit 3 | COMPLETE | 172.998 | 0.649 |
| OWASP / 3 / GROUP | run-5, forward FULL, limit 4 | COMPLETE | 53.440 | 2.145 |
| OWASP / 3 / GROUP | run-6, backward FULL, limit 5 | COMPLETE | 195.957 | 1.080 |
| OWASP / 3 / GROUP | run-7, forward FULL, limit 6 | COMPLETE | 57.358 | 2.250 |
| OWASP / 3 / GROUP | run-8, backward FULL, limit 7 | TIMEOUT | 20.564 | No complete barrier |
| OWASP / 3 / FULL | Prescan | Complete | 31.981 | Not applicable |
| OWASP / 3 / FULL | run-1, forward, limit 1 | COMPLETE | 131.253 | 2.826 |
| OWASP / 3 / FULL | run-2, backward FULL, limit 1 | COMPLETE | 168.975 | 0.382 |
| OWASP / 3 / FULL | run-3, forward FULL, limit 2 | COMPLETE | 56.307 | 2.280 |
| OWASP / 3 / FULL | run-4, backward FULL, limit 3 | COMPLETE | 167.887 | 0.611 |
| OWASP / 3 / FULL | run-5, forward FULL, limit 4 | COMPLETE | 55.899 | 2.207 |
| OWASP / 3 / FULL | run-6, backward FULL, limit 5 | COMPLETE | 189.394 | 1.045 |
| OWASP / 3 / FULL | run-7, forward FULL, limit 6 | COMPLETE | 54.280 | 2.253 |
| OWASP / 3 / FULL | run-8, backward FULL, limit 7 | TIMEOUT | 131.374 | No complete barrier |

Each OOM engine interval includes its diagnostic heap capture:

| Project / command | Capture iteration | Capture s | Snapshot file bytes |
| --- | --- | ---: | ---: |
| Thingsboard / 1 / GROUP | run-2 | 54.741 | 18,542,350,526 |
| Thingsboard / 1 / FULL | run-1 | 54.515 | 18,323,426,120 |
| Thingsboard / 2 / FULL | run-1 | 50.434 | 18,339,409,803 |
| Thingsboard / 2 / GROUP | run-2 | 42.814 | 18,497,145,479 |
| Thingsboard / 3 / GROUP | run-2 | 34.557 | 18,521,316,245 |
| Thingsboard / 3 / FULL | run-1 | 28.551 | 18,359,837,997 |
| Conductor / 1 / GROUP | run-2 | 17.632 | 11,737,896,926 |
| Conductor / 1 / FULL | run-2 | 17.705 | 11,726,825,099 |
| Conductor / 2 / FULL | run-2 | 17.781 | 11,719,982,535 |
| Conductor / 2 / GROUP | run-2 | 17.646 | 11,738,440,869 |
| Conductor / 3 / GROUP | run-2 | 17.473 | 11,720,956,464 |
| Conductor / 3 / FULL | run-2 | 17.490 | 11,715,930,660 |
| Stirling-PDF / 1 / GROUP | run-2 | 20.740 | 12,189,583,328 |
| Stirling-PDF / 1 / FULL | run-2 | 19.401 | 12,091,467,874 |
| Stirling-PDF / 2 / FULL | run-2 | 26.971 | 12,091,375,512 |
| Stirling-PDF / 2 / GROUP | run-2 | 19.137 | 12,190,214,462 |
| Stirling-PDF / 3 / GROUP | run-2 | 36.634 | 12,157,416,218 |
| Stirling-PDF / 3 / FULL | run-2 | 28.415 | 12,078,300,692 |

GROUP captures are **run-2 snapshots**, not run-1 live-heap measurements.
Snapshot file size is not retained-heap size. Capture time is included in
the reported engine time; subtracting it does not create a completed-mode
comparison.

OWASP GROUP 1 ends with run-4 TIMEOUT; FULL 1 and FULL 2 end with run-6
TIMEOUT. None of these commands has a diagnostic heap capture.

In the first GROUP command, trace generation attempted 256,203 results;
the exported SARIF contains **246,062**. There is an export gap of 10,141
despite zero trace-generation failures. Invalid-location diagnostics occur
during export. Do not use trace-generation counters as exported-result counts.

## Closed finding comparisons

The comparison streams the closed SARIF into a disk-backed index. It
retains full primary and strict rule/method/span keys, duplicate counts,
source hashes and bounded examples. Parser controls reproduce the stock
extraction on fresh main and cover split chunks and duplicate results.
The first GROUP indexing stage used 19.5 MiB peak RSS and took 38.3 seconds.
This is an indexing-stage measurement, before metadata comparison; it is
not the whole helper's peak RSS. Existing indexes are reused without
rereading their large SARIF files.

| Thingsboard result | Exported occurrences | Primary / strict keys | Missing main strict locations / occurrences | Latest reached / COMPLETE / accepted forward |
| --- | ---: | ---: | ---: | --- |
| Fresh main | 14 | 14 / 14 | Not applicable | Not applicable |
| Pair 1 GROUP | 246,062 | 4,036 / 4,043 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 2 GROUP | 246,062 | 4,036 / 4,043 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 3 GROUP | 246,062 | 4,036 / 4,043 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 1 FULL | 0 | 0 / 0 | 14 / 14, censored | run-1 / None / None |
| Pair 2 FULL | 0 | 0 / 0 | 14 / 14, censored | run-1 / None / None |
| Pair 3 FULL | 0 | 0 / 0 | 14 / 14, censored | run-1 / None / None |

All 14 baseline locations and occurrences are present under both comparison
keys in each GROUP report. All three GROUP commands have identical complete
primary-key and strict-key occurrence multisets, not only equal totals.
Each adds 4,022 primary keys and 246,048 occurrences relative to main.
The strict key retains the rule, full method signature, normalized source
path, complete region, instruction index and instruction text. A matching
line number alone is insufficient. Sink-level equality does not establish
equality of the omitted witness premises or concrete source-to-sink paths.

This is observed coverage in the accepted run-1 reports; all three commands
later fail in run-2. The XSS `sink#1` rule accounts for 245,765 candidate
occurrences across 3,755 strict locations in each GROUP report. The excess
does not satisfy a claim of only a few false positives. Do not call every
provisional candidate a confirmed false positive without its source/sink
evidence. The source-bound comparison receipts are under
`validation/finding-comparison/pair-{1,2,3}/{group,full}/thingsboard`.
Exact GROUP repetition equality is recorded in
`validation/finding-comparison/thingsboard-group1-group2-group3-closed-differential.json`.
The repetition comparison uses the closed SQLite indexes under a 192 MiB
address-space cap; it does not reread or materialize the large SARIF files.

The source-backed classification identifies these mechanisms:

- GROUP merges 65 artificial full-state exit checks into one owner group.
  The exporter still records separate rule-state occurrences. The exact
  XSS multiplicity is 3,729 strict keys times 65, plus 26 keys times 130.
- Cached new-core exit forms ask the provider with `initialFacts=null`.
  The legacy ZERO-to-fact guard therefore cannot distinguish a later
  hypothetical premise when the cached form runs. Generated end sinks
  can match any function with a result-mark condition.
- Each accepted GROUP run-1 frontier has 256,203 DEMAND results and zero
  CONFIRMED results. All 246,062 exports use Simple generation with no code flow.
  The SARIF does not contain the witness premise needed to assign an
  ND/ANY cause to every individual occurrence.

These exit-provider, grouping, end-rule compiler and exporter seams are
byte-for-byte unchanged from the pre-patch runtime. The exact-base exit
controls also reproduce the scoping issue. Spring controller annotation
handling is unchanged. This evidence does not authorize a new ZERO-only
exit restriction or a Spring rewrite.

An ordinary domain getter, `AdminSettings.getId()`, illustrates the issue:
its class has `@Schema`, and the getter has `@Schema` and `@Override`, with
no controller annotation. Its return is not directly an HTML response sink.
The provisional exit candidate is compatible with the scoping and grouped
state mechanisms. Its exported span is at the class header; the comparison
does not silently replace that span with the source getter line.

All three GROUP exports have 256,203 Simple attempts and 246,062 actual results.
Each has 10,141 invalid-location errors. The first export's classified gap
is 10,140 XSS errors, plus one SQL synthetic-wrapper error. Its 65 serialized
XSS exit-rule variants each have 156 errors; method-name categories are
9,360 generated-protobuf, 780 virtual and one other. The log does not give
the exact failed location-recovery reason for every rejected item.
`SarifGenerator` increments Simple before validating the sink location,
then drops a result with a null location. Thus zero trace-generation
failures do not imply that every attempted result was exported. The
classification and source hashes are in
`pair-1/group/thingsboard/exit-candidate-classification.json` under the
finding-comparison evidence directory. GROUP 3 has the same 65-by-156 XSS
error pattern and method-name categories, recorded in its
`repetition-classification.json`; no new source-classification change is
indicated by these observations.

All three Thingsboard FULL commands export zero results because run-1 fails before any
COMPLETE engine or accepted forward report. Each of the 14 absent main
strict keys is classified as `CENSORED_NO_ACCEPTED_FORWARD_REPORT` in its
`missing-root-causes.json`. These are failed analyses, not established
semantic false negatives or precision improvements. All three GROUP/FULL pair
differentials remain censored; they do not establish full-mode output
equivalence.

All three Conductor pairs complete and accept run-1 in both modes, then
fail in run-2. Their run-1 finding sets can be compared directly, while the
whole analyses remain incomplete.

| Conductor result | Exported occurrences | Primary / strict keys | Missing main strict locations / occurrences | Latest reached / COMPLETE / accepted forward |
| --- | ---: | ---: | ---: | --- |
| Fresh main | 7 | 7 / 7 | Not applicable | Not applicable |
| Pair 1 GROUP | 8,171 | 1,260 / 1,260 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 2 GROUP | 8,171 | 1,260 / 1,260 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 3 GROUP | 8,171 | 1,260 / 1,260 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 1 FULL | 42 | 41 / 41 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 2 FULL | 42 | 41 / 41 | 0 / 0 | run-2 / run-1 / run-1 |
| Pair 3 FULL | 42 | 41 / 41 | 0 / 0 | run-2 / run-1 / run-1 |

All six commands retain all seven main sink locations and occurrences. FULL's
entire strict key/occurrence multiset is a subset of GROUP's. GROUP adds
1,219 keys and 8,129 occurrences relative to FULL. A lower FULL count here
does not lose any observed main sink; it is an output difference between
two accepted run-1 results. Five main findings contain concrete traces;
the Simple candidate output cannot establish that those source paths are
retained. Within each mode, all three repetitions have identical complete
primary-key and strict-key occurrence multisets. All three paired mode
differences are also identical. Exact repetition checks are recorded in
`validation/finding-comparison/conductor-all-repetitions-closed-differential.json`.

| Conductor rule cohort | GROUP keys / occurrences | FULL keys / occurrences |
| --- | ---: | ---: |
| XSS `sink#1` | 986 / 7,888 | 8 / 8 |
| SQL injection | 242 / 242 | 9 / 9 |
| SSRF | 9 / 17 | 1 / 1 |
| Configured gRPC filesystem rule | 14 / 15 | 14 / 15 |
| Other four rule cohorts | 9 / 9 | 9 / 9 |

All 986 GROUP XSS locations have eight occurrences. They include the
ordinary noncontroller `AbstractMessage.getName()` getter. FULL retains
eight locations in actual controller methods; each occurs once, while
GROUP records eight occurrences at each shared location. This agrees with
the grouped artificial-state and cached-exit mechanisms described above.
It does not prove unsafe HTML output at every retained controller site.
GROUP also has 233 SQL and eight SSRF locations absent from FULL. One
inspected GROUP-only SQL site executes a Cassandra prepared-statement
`bind(name)`; this alone does not establish SQL-text injection.
The selected FULL mode also applies exact selected-mark cleaning, while a
GROUP cleaner cannot kill its wildcard group. Without witness premises,
the SQL/SSRF differences cannot be assigned solely to condition-state
coalescing rather than this deliberate mark-precision difference.

The identical gRPC-rule, Graal, path, reflection and stacktrace cohorts
cannot be attributed solely to GROUP merging. Despite its `lib` directory,
the configured gRPC rule is an ERROR taint rule with explicit filesystem
sinks. An added site in `AIModelProvider` uses configuration and a File
constructor; its class is not an `ImplBase` handler, and the report supplies
no concrete qualifying gRPC source path. Both fresh main and candidate
skip the same eight unsupported `$*SINK` pattern variants and keep the
plain variants. This is a shared rule-loading limitation, not evidence of
a new parser regression. Other inspected extra sites include actual script
evaluation, a filesystem write after application access validation, and
reflection on a stored annotated method. They remain provisional candidates,
not automatically exploitable findings or confirmed false positives.

Each Conductor GROUP result has 8,292 DEMAND candidates, zero CONFIRMED candidates and
121 rejected locations: 120 XSS virtual exits, eight variants times 15,
and one SQL state at a NATS dispatcher wrapper. Each Conductor FULL result has
44 DEMAND candidates, zero CONFIRMED candidates and two rejected locations,
leaving 42 exports. None completes the backward refinement. Full-key/cohort classification,
source snippets and hashes are under
`validation/finding-comparison/pair-1/{group,full,group-vs-full}/conductor`;
the corresponding pair-2 and pair-3 directories contain each repetition's
comparison and classification receipts.
All six Stirling-PDF commands accept run-1 and then fail in run-2.
They have **two missing baseline locations in each mode**. These are observed
missing primary and strict keys in an accepted report, not censored empty
output and not a no-FN acceptance result.

| Stirling-PDF result | Exported occurrences | Primary / strict keys | Missing main strict locations / occurrences | Latest reached / COMPLETE / accepted forward |
| --- | ---: | ---: | ---: | --- |
| Fresh main | 17 | 17 / 17 | Not applicable | Not applicable |
| Pair 1 GROUP | 60,737 | 1,593 / 1,595 | **2 / 2, noncensored** | run-2 / run-1 / run-1 |
| Pair 1 FULL | 973 | 148 / 148 | **2 / 2, noncensored** | run-2 / run-1 / run-1 |
| Pair 2 GROUP | 60,737 | 1,593 / 1,595 | **2 / 2, noncensored** | run-2 / run-1 / run-1 |
| Pair 2 FULL | 973 | 148 / 148 | **2 / 2, noncensored** | run-2 / run-1 / run-1 |
| Pair 3 GROUP | 60,737 | 1,593 / 1,595 | **2 / 2, noncensored** | run-2 / run-1 / run-1 |
| Pair 3 FULL | 973 | 148 / 148 | **2 / 2, noncensored** | run-2 / run-1 / run-1 |

Fifteen baseline locations and occurrences match exactly in both modes.
All three GROUP repetitions have identical complete primary and strict
key/occurrence multisets; all three FULL repetitions are also identical.
All six omit the same two baseline keys. All three mode pairs have the same
complete key/occurrence differences.
The two misses have same-rule, same-full-method candidates at different
returns. They remain separate keys in every comparison:

| XSS method-exit case | Main return / source lines | Candidate normal-exit location in both modes |
| --- | --- | --- |
| `ConvertEmlToPDF.convertEmlToPdf(EmlToPdfRequest)` | IR 103, `return %54`, lines 133–134 | IR 126, `return %89`, lines 148–149 |
| `PipelineController.handleData(HandleDataRequest)` | IR 50, `return %104`, lines 90–91 | IR 109, `return null`, line 130 |

The first main return sends a constructed error message; the candidate
location sends the final IOException response. The second main return
sends a single-file response; the candidate location is the final catch's
null return. Main's concrete traces identify their return branches. Bidi
exports a generated method-exit boundary with a Simple trace.
`TraceMessageBuilder` resolves that boundary to the last IR return when
relevant trace locations are absent. This explains the observed coordinate
divergence. It **does not prove** that either old branch or source-to-sink
flow is retained. The GROUP alternatives occur 74 times each; FULL has
18 EML and nine pipeline normal-exit occurrences. FULL reproduces both
missing main keys, so grouping is not required for this reporting seam.

Source snippets and code hashes bind the exporter, boundary forms and
last-return fallback to the pre-patch `30c1b8460` reference. Those exporter
files are unchanged; the relevant `endAt` snippet is unchanged within an
otherwise modified analyzer file. This establishes an existing policy
seam, **not** that the pre-patch engine emitted the same frontier.
Neither missing key is silently merged with its same-method alternative.

FULL's complete exported key/occurrence multiset is contained in GROUP's.
GROUP adds 1,445 primary keys, 1,447 strict keys and 59,764 occurrences
relative to FULL. Its XSS cohort has 59,941 occurrences at 810 locations,
versus FULL's 918 at 96 locations. It also has 726 redirect locations and
seven SQL locations absent from FULL. Examples include XSS at a JSON DTO's
Lombok getter, a redirect candidate where booklet creation returns PDF
bytes, and a SQL candidate at `createFontFromModel`. These support the
grouped-state candidate-expansion diagnosis; missing witnesses prevent a
separate causal or exploitability conclusion for every extra occurrence.
Exact selected-mark cleaning also differs intentionally between modes.

GROUP has 61,262 DEMAND candidates, zero CONFIRMED candidates and 525
location rejects: 518 XSS and seven redirect errors, all at virtual
method-name locations. Its 75 serialized exit variants each have seven
errors. FULL has 973 DEMAND candidates, zero CONFIRMED candidates and no
export gap. All exports use Simple generation; 16 main findings have
traces. Neither mode finishes the backward refinement. Every missing
case, original full key, source span, same-method alternative and unresolved
branch-flow limitation is recorded under
`validation/finding-comparison/pair-1/{group,full,group-vs-full}/Stirling-PDF`.
Pair 2's own-report examples and reviewed missing-key classifications are
under `validation/finding-comparison/pair-2/{group,full,group-vs-full}/Stirling-PDF`.
Pair 3's own-report examples and mode comparison are under
`validation/finding-comparison/pair-3/{group,full,group-vs-full}/Stirling-PDF`.
Exact repetition checks are in
`validation/finding-comparison/Stirling-PDF-all-repetitions-closed-differential.json`.
The first OWASP GROUP command reaches **run-4 TIMEOUT** with its latest
COMPLETE and accepted forward report at **run-3**. FULL 1 and FULL 2
reach **run-6 TIMEOUT** with their latest COMPLETE and accepted forward
report at **run-5**. GROUP 2 also reaches run-6 TIMEOUT with run-5 COMPLETE
and accepted. GROUP 3 and FULL 3 reach run-8 TIMEOUT with run-7 COMPLETE and accepted.
All six accepted reports use full marks. The comparisons use those reports,
not the run-1 frontiers.

| OWASP result | Exported occurrences | Primary / strict keys | Missing main strict locations / occurrences | Latest reached / COMPLETE / accepted forward |
| --- | ---: | ---: | ---: | --- |
| Fresh main | 4,112 | 4,112 / 4,112 | Not applicable | Not applicable |
| Pair 1 GROUP | 9,179 | 4,112 / 4,112 | 0 / 0 | run-4 / run-3 / run-3 |
| Pair 1 FULL | 9,179 | 4,112 / 4,112 | 0 / 0 | run-6 / run-5 / run-5 |
| Pair 2 GROUP | 9,179 | 4,112 / 4,112 | 0 / 0 | run-6 / run-5 / run-5 |
| Pair 2 FULL | 9,179 | 4,112 / 4,112 | 0 / 0 | run-6 / run-5 / run-5 |
| Pair 3 GROUP | 9,179 | 4,112 / 4,112 | 0 / 0 | run-8 / run-7 / run-7 |
| Pair 3 FULL | 9,179 | 4,112 / 4,112 | 0 / 0 | run-8 / run-7 / run-7 |

All 4,112 baseline primary and strict keys and their occurrences are
present in each mode. Their complete primary and strict key/occurrence
multisets are identical across all six reports. Pair 1 compares different
accepted iterations; pair 2 compares run-5 and pair 3 compares run-7 on both sides. These observations
do not isolate a mark-mode effect. There are no new
location keys. Each report has 5,067 extra occurrences, all duplicates at
existing exact locations and retained in the comparison:

| Rule | Exact locations | Candidate occurrences | Extra occurrences |
| --- | ---: | ---: | ---: |
| XSS | 824 | 4,120 | 3,296 |
| Response injection | 824 | 1,648 | 824 |
| Trust boundary | 610 | 1,103 | 493 |
| LDAP injection | 47 | 269 | 222 |
| SQL injection | 424 | 563 | 139 |
| ECB cipher | 61 | 121 | 60 |
| Deprecated DESede | 33 | 66 | 33 |

The reporting code preserves a complete sink-rule object in each bidi
vulnerability key and exports each entry separately using its public rule
ID and statement. Legacy storage instead merges rules under one
`(ruleId, statement)` bucket. Several serialized rule variants can thus
export the same observation key. This is a source-backed reporting seam;
Simple output does not identify the exact internal variant behind each
duplicate. The counts do not enumerate every support witness. Each complete
forward report replaces the earlier DEMAND set instead of appending run-1 entries.
Examples and hashes bind these mechanisms to the code and the original
source spans, including servlet response writes, an LDAP filter built
from a request parameter, and a configured `DESede/ECB` cipher.

Each report has 9,179 DEMAND candidates, zero CONFIRMED candidates and no
export gap. All exports use Simple generation. Main has 3,619 concrete
traces, so exact sink agreement does not establish source-path equivalence
or whole-analysis no-FN acceptance. The closed comparison and source
classification are under
`validation/finding-comparison/pair-1/{group,full,group-vs-full}/OWASP`;
Pair 2's own-report source reviews and mode comparison are under
`validation/finding-comparison/pair-2/{group,full,group-vs-full}/OWASP`. Exact repetition checks
are in `validation/finding-comparison/OWASP-all-repetitions-closed-differential.json`;
Pair 3's own-report reviews and mode comparison are under
`validation/finding-comparison/pair-3/{group,full,group-vs-full}/OWASP`;
the baseline key and
source-field checks are in
`validation/finding-comparison/main/OWASP-baseline-comparison-receipt.json`.
All 24 candidate commands now have closed finding comparisons. The compact
extract, with actual accepted iterations and missing-key classifications,
is in `validation/finding-comparison/final-findings-table.md` and `.json`.

## Thingsboard run-1 workload

The first GROUP command has these run-1 END counters. Counts are work or
scoped memberships, not unique full facts or retained heap bytes.

| Counter | Count | Meaning |
| --- | ---: | --- |
| Input arrivals | 3,469,025 | Calls to run-1 input abstraction |
| Initial candidates / fresh initials | 4,975,498 / 321,765 | Candidate initial admissions and successful admissions |
| Request offers / offered memberships | 4,304,126 / 5,311,613 | Offers before authoritative and temporary-store deduplication |
| Fresh temporary request memberships | 666,043 | New memberships admitted for later commit |
| Caller-input accepted deltas | 2,242,412 | Fresh subscription input deltas |
| Caller-input groups | 1,683,091 | New exact caller/input groups |
| Direct static selected-atom call applications | 159,948 | Retained static-premise call applications; not S-to-S relay rows |
| Canonical direct computed-supply leaves | 3,005,812 | Static-premise/static-result producer-effect offers before erasure |
| Reached write regions / supplied regions | 152 / 119 | Global static service region scope |
| Supplied static selected-atom memberships | 649 | Atom memberships across supplied regions |
| Static-origin / nonstatic-origin FLOW writer rows | 5,030 / 419 | Reached immutable local writer descriptors |
| Read descriptors / pending subscription rows | 43,505 / 267,924 | Retained read directory and currently unanswered static interests |
| Distinct requested / supplied atoms | 35 / 12 | Distinct selected atoms observed in the static service only |
| Pending static memberships | 744,398 | Unanswered point memberships at the END snapshot |
| Handoff demand entries / method keys | 1,065,765 / 73,599 | Whole retained demand frontier, all relation kinds |

The six closed Thingsboard commands have these run-1 counts. GROUP reached engine
completion; each FULL row is an executed prefix before OOM, with an
incomplete closure. These are not completed FULL/GROUP cost ratios.

| Scoped run-1 counter | GROUP 1 COMPLETE | GROUP 2 COMPLETE | GROUP 3 COMPLETE | FULL 1 OOM prefix | FULL 2 OOM prefix | FULL 3 OOM prefix |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Input arrivals | 3,469,025 | 3,455,931 | 3,514,609 | 3,892,895 | 4,027,379 | 4,059,792 |
| Fresh initials | 321,765 | 321,718 | 321,772 | 488,219 | 497,852 | 489,522 |
| Committed fresh request memberships | 666,043 | 666,043 | 666,043 | 7,695,900 | 7,625,464 | 6,989,211 |
| Request member scans | 6,039,069 | 6,035,813 | 6,164,896 | 38,087,521 | 38,977,678 | 35,493,181 |
| Request climbs | 1,186,846 | 1,174,381 | 1,186,386 | 14,666,912 | 14,655,051 | 13,895,081 |
| Outgoing request memberships | 1,149,281 | 1,138,801 | 1,149,203 | 13,821,101 | 13,707,539 | 13,054,581 |
| Caller-input accepted deltas | 2,242,412 | 2,244,354 | 2,248,306 | 3,339,192 | 3,443,054 | 3,447,729 |
| Direct static selected-atom call applications | 159,948 | 159,978 | 159,942 | 902,478 | 950,527 | 893,676 |
| Canonical direct computed-supply leaves | 3,005,812 | 3,189,816 | 3,051,566 | 4,223,791 | 4,032,461 | 3,574,194 |
| Static distinct requested / supplied atoms | 35 / 12 | 35 / 12 | 35 / 12 | 742 / 94 | 742 / 94 | 742 / 84 |
| Static supplied owner groups | 12 | 12 | 12 | 11 | 11 | 2 |
| Pending static subscription rows | 267,924 | 268,710 | 250,942 | 1,054,913 | 886,763 | 905,724 |
| Pending static memberships | 744,398 | 744,631 | 744,450 | 5,803,770 | 5,015,532 | 5,112,358 |
| Explicit handoff demand entries | 1,065,765 | 1,065,393 | 1,065,547 | No complete handoff | No complete handoff | No complete handoff |

FULL 1's temporary store admitted 7,810,137 fresh memberships, but only
7,695,900 reached authoritative commit before cancellation. FULL 2 admitted
7,733,195 and committed 7,625,464. FULL 3 admitted 7,135,288 and committed
6,989,211. Do not equate temporary admission and committed propagation
for a failed prefix. All three FULL attempts already do
substantially more membership work and run out of memory in run-1. The three
GROUP attempts complete run-1 but still fail in run-2. These observations
support retaining GROUP while the repetitions run; they do not establish
a mode-wide speedup factor or output equivalence.

The 3,005,812 direct computed-supply leaves are **not** ZERO-to-static
relay counts. The counter increments in the canonical concrete static
input branch. Equal endpoints alone do not prove equal region effects.
Request duplicates are already rejected by the two request stores before
propagation. Duplicate offer counts do not prove duplicate propagation.
Pending subscription rows belong to the global service; their wanted
atom sets give the pending-membership count. They are not a census of
all actor-side saved points or every point created during the run.
No runtime counter partitions the complete frontier by premise/result
kind. Tests and insertion guards establish S-to-S erasure; this table is
not a measured frontier-shape census.

The periodic scheduler logs show the following unfinished event reservations:

| Thingsboard command / engine | Status | Last sampled reservations | Peak sampled reservations |
| --- | --- | ---: | ---: |
| 1 / GROUP / run-1 | COMPLETE | 1 | 73,671 |
| 2 / GROUP / run-1 | COMPLETE | 12 | 71,566 |
| 3 / GROUP / run-1 | COMPLETE | 2 | 74,008 |
| 1 / FULL / run-1 | OOM | 17,354 | 144,493 |
| 2 / FULL / run-1 | OOM | 17,300 | 113,725 |
| 3 / FULL / run-1 | OOM | 15,874 | 108,370 |
| 1 / GROUP / run-2 | OOM | 958,003 | 958,627 |
| 2 / GROUP / run-2 | OOM | 956,834 | 959,149 |
| 3 / GROUP / run-2 | OOM | 960,899 | 962,338 |

These are approximately ten-second samples of accepted, unfinished event
reservations, including queued or running handlers and a live start-batch
reservation. One Work reservation can span many quanta. Inline same-unit
calls and actor-internal work need not have separate reservations. The
sampled peak is a lower bound on the true peak; the last tick is not a
terminal queue size. These are not exact queue lengths, fact counts, AP
operation counts or bytes. For GROUP 2 run-2, the last pre-capture tick has
957,039 reservations at 20:46:56.008 UTC. A later tick near the capture end
has 956,834. The closed metrics retain sample timestamps and distinguish
these observations.

## Conductor completed run-1 comparisons

All six commands completed run-1 and their report barriers. All then hit
OOM in run-2. Their latest COMPLETE and accepted forward iteration remains
run-1. Each pair passes the mode, binary, input configuration, JVM settings
and profile class checks.

| Pair | GROUP run-1 s | FULL run-1 s | FULL/GROUP run-1 ratio |
| --- | ---: | ---: | ---: |
| 1 | 18.213 | 14.549 | 0.798821 |
| 2 | 18.929 | 14.942 | 0.789381 |
| 3 | 17.677 | 14.592 | 0.825483 |

| Mode | Completed engines | Run-1 median s | Raw run-1 range s | Sample CV |
| --- | ---: | ---: | --- | ---: |
| GROUP | 3 | 18.213 | 17.677–18.929 | 3.44% |
| FULL | 3 | 14.592 | 14.549–14.942 | 1.47% |

The median of the three paired ratios is **0.798821**.
CV is sample standard deviation divided by the arithmetic mean, with
three observations per mode. These small, profiled samples describe this
cohort. Host state and serial order can affect timing. They do not isolate
host effects, establish output equivalence or imply a whole-engine
speedup. All native run-1 windows were skipped because run-1 ended before
the one-minute delay. Continuous JFR remained enabled in every command.

| Scoped run-1 counter | 1 / GROUP | 1 / FULL | 2 / FULL | 2 / GROUP | 3 / GROUP | 3 / FULL |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Input arrivals | 400,160 | 230,877 | 231,260 | 400,934 | 400,664 | 230,963 |
| Fresh initials | 63,464 | 37,291 | 37,291 | 63,442 | 63,467 | 37,291 |
| Committed fresh request memberships | 134,416 | 566,007 | 566,007 | 134,416 | 134,416 | 566,007 |
| Request member scans | 716,845 | 2,235,567 | 2,272,995 | 714,837 | 714,458 | 2,288,031 |
| Request climbs | 240,959 | 1,073,521 | 1,112,962 | 243,561 | 241,084 | 1,129,643 |
| Outgoing request memberships | 229,195 | 1,003,130 | 1,040,538 | 229,819 | 229,241 | 1,055,038 |
| Caller-input accepted deltas | 323,328 | 182,103 | 182,360 | 323,182 | 323,526 | 182,182 |
| Direct static selected-atom call applications | 16,063 | 16,607 | 16,596 | 16,149 | 16,158 | 16,533 |
| Canonical direct computed-supply leaves | 8,063 | 16,028 | 14,580 | 8,044 | 8,041 | 16,160 |
| Static distinct requested / supplied atoms | 37 / 9 | 629 / 18 | 629 / 18 | 37 / 9 | 37 / 9 | 629 / 18 |
| Static supplied owner groups | 9 | 9 | 9 | 9 | 9 | 9 |
| Pending static subscription rows | 24,244 | 112,590 | 111,640 | 26,159 | 24,921 | 105,814 |
| Pending static memberships | 110,224 | 581,011 | 583,210 | 110,900 | 110,225 | 581,075 |
| Explicit handoff demand entries | 132,202 | 81,671 | 81,671 | 132,198 | 132,164 | 81,673 |

These counters use the same definitions and static-service scope as the
Thingsboard counters. Every Conductor handoff has 17,417 method keys.
FULL has more request-membership work and fewer input/initial/caller-input
admissions in all three pairs. The modes use different selected-atom
abstractions and cleaner policies. These counts alone do not assign the
extra work or timing difference to one mechanism.

| Command | Run-2 fresh initials before OOM | Run-2 caller-input deltas before OOM |
| --- | ---: | ---: |
| 1 / GROUP | 776,475 | 1,404,834 |
| 1 / FULL | 748,533 | 1,423,532 |
| 2 / FULL | 736,610 | 1,443,394 |
| 2 / GROUP | 779,900 | 1,409,727 |
| 3 / GROUP | 768,775 | 1,391,814 |
| 3 / FULL | 747,120 | 1,420,987 |

No row is a complete run-2 fixpoint. The following periodic reservations
are not terminal queue lengths or fact/AP-operation counts.

| Command / engine | Last sampled reservations | Peak sampled reservations | Last tick before engine end s |
| --- | ---: | ---: | ---: |
| 1 / GROUP / run-1 COMPLETE | 5,310 | 5,310 | 8.187 |
| 1 / GROUP / run-2 OOM | 10,542 | 34,494 | 0.013 |
| 1 / FULL / run-1 COMPLETE | 4,146 | 4,146 | 4.518 |
| 1 / FULL / run-2 OOM | 379 | 11,955 | 0.015 |
| 2 / FULL / run-1 COMPLETE | 5,550 | 5,550 | 4.921 |
| 2 / FULL / run-2 OOM | 446 | 28,667 | 0.013 |
| 2 / GROUP / run-1 COMPLETE | 5,304 | 5,304 | 8.908 |
| 2 / GROUP / run-2 OOM | 11,177 | 35,949 | 0.017 |
| 3 / GROUP / run-1 COMPLETE | 5,691 | 5,691 | 7.654 |
| 3 / GROUP / run-2 OOM | 11,736 | 37,444 | 0.013 |
| 3 / FULL / run-1 COMPLETE | 3,812 | 3,812 | 4.574 |
| 3 / FULL / run-2 OOM | 374 | 26,976 | 0.016 |

Each run-1 row has only one periodic tick. The run-2 last ticks are near
diagnostic capture end and do not measure ordinary late-solver queue sizes.

## Stirling-PDF closed commands

All six commands completed run-1 and their report barriers, then hit OOM
in run-2. Their latest COMPLETE and accepted forward iteration remains
run-1. Each pair matches its mode, runtime revision, configured inputs,
JVM settings and profile class checks. Pair 1's whole-checkout patch
hashes differ because that hash includes non-runtime documentation;
the separate runtime/input freeze is checked before each command.

| Pair | GROUP run-1 s | FULL run-1 s | FULL/GROUP run-1 ratio |
| --- | ---: | ---: | ---: |
| 1 | 25.349 | 24.782 | 0.977644 |
| 2 | 32.342 | 25.131 | 0.777028 |
| 3 | 31.766 | 32.448 | 1.021477 |

| Mode | Completed engines | Run-1 median s | Raw run-1 range s | Sample CV |
| --- | ---: | ---: | --- | ---: |
| GROUP | 3 | 31.766 | 25.349–32.342 | 13.02% |
| FULL | 3 | 25.131 | 24.782–32.448 | 15.77% |

The median paired run-1 ratio is **0.977644**. CV uses sample
standard deviation divided by the arithmetic mean, with three observations
per mode. These profiled timings have different host pressure; they do
not isolate a causal mode cost, a whole-engine speedup or finding coverage.

| Scoped run-1 counter | GROUP 1 COMPLETE | FULL 1 COMPLETE | FULL 2 COMPLETE | GROUP 2 COMPLETE | GROUP 3 COMPLETE | FULL 3 COMPLETE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Input arrivals | 69,712 | 38,806 | 38,978 | 69,115 | 69,536 | 38,748 |
| Fresh initials | 15,355 | 10,111 | 10,111 | 15,355 | 15,355 | 10,109 |
| Committed fresh request memberships | 11,593 | 138,002 | 138,002 | 11,593 | 11,593 | 138,002 |
| Request member scans | 74,104 | 361,261 | 363,202 | 73,565 | 74,217 | 364,657 |
| Request climbs | 11,282 | 151,468 | 151,012 | 11,270 | 10,761 | 150,896 |
| Outgoing request memberships | 10,557 | 145,839 | 145,531 | 10,551 | 10,102 | 145,233 |
| Caller-input accepted deltas | 59,256 | 35,815 | 35,970 | 58,716 | 59,092 | 35,756 |
| Direct static selected-atom call applications | 8,393 | 5,722 | 5,749 | 8,393 | 8,393 | 5,722 |
| Canonical direct computed-supply leaves | 1,004 | 353 | 353 | 989 | 997 | 353 |
| Static distinct requested / supplied atoms | 35 / 13 | 709 / 101 | 709 / 101 | 35 / 13 | 35 / 13 | 709 / 101 |
| Static supplied owner groups | 13 | 13 | 13 | 13 | 13 | 13 |
| Pending static subscription rows / memberships | 8,297 / 39,667 | 27,904 / 683,358 | 28,144 / 683,358 | 8,265 / 39,665 | 8,400 / 39,667 | 27,843 / 683,358 |
| Explicit handoff demand entries / method keys | 47,474 / 7,258 | 37,406 / 7,258 | 37,407 / 7,258 | 47,486 / 7,258 | 47,486 / 7,258 | 37,403 / 7,258 |

The same counter definitions and static-service scope apply. More FULL
request memberships coexist with fewer input/initial/caller admissions;
these mode-specific work counts do not alone identify a causal bottleneck.

| Command | Run-2 fresh initials before OOM | Run-2 caller-input deltas before OOM | Sampled CPU delta run-1 / run-2 s |
| --- | ---: | ---: | ---: |
| 1 / GROUP | 1,132,197 | 2,184,693 | 161.75 / 3465.00 |
| 1 / FULL | 751,734 | 1,380,611 | 156.78 / 1462.67 |
| 2 / FULL | 742,824 | 1,345,551 | 152.34 / 1718.96 |
| 2 / GROUP | 1,134,695 | 2,168,670 | 217.34 / 4102.93 |
| 3 / GROUP | 1,116,051 | 2,115,961 | 212.49 / 4358.08 |
| 3 / FULL | 792,711 | 1,491,755 | 163.08 / 1655.64 |

Run-2 counts are failed prefixes; sampled CPU deltas omit phase edges.
Both native run-1 windows were skipped because run-1 had ended.
Continuous JFR remained enabled, so both timings are profiled.

| Command / engine | Last sampled reservations | Peak sampled reservations | Last tick before engine end s |
| --- | ---: | ---: | ---: |
| 1 / GROUP / run-1 COMPLETE | 1,005 | 1,005 | 5.332 |
| 1 / GROUP / run-2 OOM | 3,982 | 36,047 | 0.009 |
| 1 / FULL / run-1 COMPLETE | 924 | 924 | 4.755 |
| 1 / FULL / run-2 OOM | 303 | 625 | 0.010 |
| 2 / FULL / run-1 COMPLETE | 893 | 893 | 5.098 |
| 2 / FULL / run-2 OOM | 421 | 978 | 0.010 |
| 2 / GROUP / run-1 COMPLETE | 92 | 3,200 | 2.308 |
| 2 / GROUP / run-2 OOM | 2,986 | 37,610 | 0.010 |
| 3 / GROUP / run-1 COMPLETE | 85 | 3,632 | 1.739 |
| 3 / GROUP / run-2 OOM | 1,397 | 37,440 | 0.027 |
| 3 / FULL / run-1 COMPLETE | 1,363 | 4,721 | 2.366 |
| 3 / FULL / run-2 OOM | 548 | 729 | 0.030 |

The first-pair run-1 engines and FULL 2 have two periodic samples each;
GROUP 2, GROUP 3 and FULL 3 have three each. The run-2 last ticks are near
heap-capture end. These are unfinished event reservations, not exact
fact work or terminal queue lengths. Run-2 heap captures are included
in engine time and are not run-1 retained-heap measurements.

## OWASP closed commands

All 6 closed OWASP run-1 engines completed; each exceeded the
60-second target. The tables retain every reached iteration, status and
report/handoff barrier. Final TIMEOUT engines are censored; earlier
COMPLETE engines remain valid run-1 observations. No OWASP command
captured an OOM heap. Accepted forward iterations can differ between
commands, so finding comparisons do not establish same-stage equivalence.

| Closed pair | GROUP run-1 s | FULL run-1 s | FULL/GROUP run-1 time |
| --- | ---: | ---: | ---: |
| 1 | 239.889 | 205.207 | 0.855423 |
| 2 | 210.170 | 204.290 | 0.972024 |
| 3 | 178.420 | 131.253 | 0.735643 |

These are profiled paired engine measurements, not a causal mode effect
or a whole-analysis speedup. Allocation ratios and recording coverage
are recorded in the closed run-1 profile cohort.

| Scoped run-1 counter | GROUP 1 COMPLETE | FULL 1 COMPLETE | GROUP 2 COMPLETE | FULL 2 COMPLETE | GROUP 3 COMPLETE | FULL 3 COMPLETE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Input arrivals | 176,908 | 168,954 | 176,903 | 168,954 | 176,908 | 168,951 |
| Fresh initials | 42,327 | 36,592 | 42,327 | 36,592 | 42,327 | 36,592 |
| Request offers / offered memberships | 2,241,142 / 2,254,229 | 5,936,607 / 6,006,026 | 2,241,141 / 2,254,226 | 5,936,611 / 6,006,086 | 2,241,139 / 2,254,220 | 5,936,607 / 6,006,013 |
| Committed fresh request memberships | 54,239 | 299,471 | 54,239 | 299,471 | 54,239 | 299,471 |
| Temporary duplicate request memberships | 2,180,554 | 5,665,129 | 2,180,551 | 5,665,189 | 2,180,545 | 5,665,135 |
| Request member scans / climbs | 112,085 / 19,232 | 182,187 / 86,867 | 112,095 / 19,229 | 182,235 / 86,912 | 112,095 / 19,226 | 182,161 / 86,854 |
| Outgoing request memberships | 16,046 | 72,451 | 16,043 | 72,511 | 16,037 | 72,438 |
| Caller-input accepted deltas | 163,480 | 156,180 | 163,475 | 156,180 | 163,480 | 156,177 |
| Direct static selected-atom call applications / computed-supply leaves | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| Static write regions / supplied regions | 3 / 1 | 3 / 1 | 3 / 1 | 3 / 1 | 3 / 1 | 3 / 1 |
| Static-origin / nonstatic-origin FLOW writer rows | 0 / 75 | 0 / 75 | 0 / 75 | 0 / 75 | 0 / 75 | 0 / 75 |
| Static read descriptors / pending subscription rows | 2,285 / 6,293 | 2,285 / 15,033 | 2,285 / 6,293 | 2,285 / 15,033 | 2,285 / 6,293 | 2,285 / 15,033 |
| Static distinct requested / supplied atoms | 8 / 15 | 36 / 17 | 8 / 15 | 36 / 17 | 8 / 15 | 36 / 17 |
| Static supplied owner groups | 15 | 15 | 15 | 15 | 15 | 15 |
| Pending static memberships | 6,293 | 56,868 | 6,293 | 56,868 | 6,293 | 56,868 |
| Explicit run-1 handoff demand entries / method keys | 138,061 / 12,083 | 141,374 / 12,083 | 138,061 / 12,083 | 141,374 / 12,083 | 138,061 / 12,083 | 141,374 / 12,083 |

The definitions are the same as for other projects. The two zero counters
cover the selected static-premise call subpath; they do not assert zero
static work. Temporary duplicate memberships are rejected before request
propagation; their count is not a duplicate propagation count. Small
static stores alone do not explain total engine cost. The closed profiles
distinguish ordinary call, cleaner, form and AP-projection contexts.

| Command / engine | Status | Input arrivals | Fresh initials | Caller-input accepted deltas | Sampled process CPU delta s |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 / GROUP / run-1 | COMPLETE | 176,908 | 42,327 | 163,480 | 639.84 |
| 1 / GROUP / run-2 | COMPLETE | 149,345 | 111,221 | 143,385 | 1374.12 |
| 1 / GROUP / run-3 | COMPLETE | 35,533 | 23,395 | 33,191 | 213.41 |
| 1 / GROUP / run-4 | TIMEOUT | 99,218 | 84,306 | 106,055 | 857.13 |
| 1 / FULL / run-1 | COMPLETE | 168,954 | 36,592 | 156,180 | 547.41 |
| 1 / FULL / run-2 | COMPLETE | 104,957 | 76,243 | 100,822 | 834.98 |
| 1 / FULL / run-3 | COMPLETE | 28,915 | 20,759 | 27,461 | 171.94 |
| 1 / FULL / run-4 | COMPLETE | 125,965 | 106,330 | 131,814 | 915.48 |
| 1 / FULL / run-5 | COMPLETE | 28,849 | 20,749 | 27,395 | 212.56 |
| 1 / FULL / run-6 | TIMEOUT | 41,071 | 40,243 | 50,319 | 285.09 |
| 2 / GROUP / run-1 | COMPLETE | 176,903 | 42,327 | 163,475 | 544.13 |
| 2 / GROUP / run-2 | COMPLETE | 149,269 | 111,221 | 143,311 | 912.84 |
| 2 / GROUP / run-3 | COMPLETE | 35,533 | 23,395 | 33,191 | 167.77 |
| 2 / GROUP / run-4 | COMPLETE | 126,728 | 106,806 | 132,471 | 803.70 |
| 2 / GROUP / run-5 | COMPLETE | 28,827 | 20,749 | 27,373 | 139.12 |
| 2 / GROUP / run-6 | TIMEOUT | 126,668 | 108,797 | 132,807 | 758.95 |
| 2 / FULL / run-1 | COMPLETE | 168,954 | 36,592 | 156,180 | 551.92 |
| 2 / FULL / run-2 | COMPLETE | 105,026 | 76,243 | 100,895 | 931.03 |
| 2 / FULL / run-3 | COMPLETE | 28,915 | 20,759 | 27,461 | 173.53 |
| 2 / FULL / run-4 | COMPLETE | 125,963 | 106,322 | 131,818 | 875.46 |
| 2 / FULL / run-5 | COMPLETE | 28,849 | 20,749 | 27,395 | 158.22 |
| 2 / FULL / run-6 | TIMEOUT | 75,905 | 67,317 | 83,889 | 412.15 |
| 3 / GROUP / run-1 | COMPLETE | 176,908 | 42,327 | 163,480 | 285.69 |
| 3 / GROUP / run-2 | COMPLETE | 149,299 | 111,221 | 143,342 | 852.62 |
| 3 / GROUP / run-3 | COMPLETE | 35,533 | 23,395 | 33,191 | 162.41 |
| 3 / GROUP / run-4 | COMPLETE | 126,750 | 106,820 | 132,491 | 720.79 |
| 3 / GROUP / run-5 | COMPLETE | 28,827 | 20,749 | 27,373 | 125.27 |
| 3 / GROUP / run-6 | COMPLETE | 134,162 | 115,192 | 140,099 | 814.60 |
| 3 / GROUP / run-7 | COMPLETE | 28,827 | 20,749 | 27,373 | 135.68 |
| 3 / GROUP / run-8 | TIMEOUT | 0 | 4,595 | 4,204 | 37.71 |
| 3 / FULL / run-1 | COMPLETE | 168,951 | 36,592 | 156,177 | 360.92 |
| 3 / FULL / run-2 | COMPLETE | 104,962 | 76,243 | 100,826 | 638.17 |
| 3 / FULL / run-3 | COMPLETE | 28,915 | 20,759 | 27,461 | 138.01 |
| 3 / FULL / run-4 | COMPLETE | 125,934 | 106,324 | 131,795 | 675.45 |
| 3 / FULL / run-5 | COMPLETE | 28,849 | 20,749 | 27,395 | 122.36 |
| 3 / FULL / run-6 | COMPLETE | 134,161 | 115,192 | 140,097 | 769.05 |
| 3 / FULL / run-7 | COMPLETE | 28,849 | 20,749 | 27,395 | 125.48 |
| 3 / FULL / run-8 | TIMEOUT | 98,500 | 86,152 | 105,725 | 506.90 |

Only the final TIMEOUT engines are censored prefixes. Earlier COMPLETE
engines have their own report/handoff barriers. Sampled CPU deltas omit
phase boundaries. The compact frontiers prove these observations:

| Command / iteration | Explicit demand entries | Method keys | Record crossings | Seeds |
| --- | ---: | ---: | ---: | ---: |
| 1 / GROUP / run-1 (forward) | 138,061 | 12,083 | 0 | 48,994 |
| 1 / GROUP / run-2 (backward) | 633,642 | 6,516 | 71,877 | 35,971 |
| 1 / GROUP / run-3 (forward) | 454 | 49 | 49,189 | 9,059 |
| 1 / FULL / run-1 (forward) | 141,374 | 12,083 | 0 | 11,077 |
| 1 / FULL / run-2 (backward) | 388,859 | 6,172 | 46,677 | 27,732 |
| 1 / FULL / run-3 (forward) | 154 | 49 | 39,745 | 9,059 |
| 1 / FULL / run-4 (backward) | 719,247 | 4,564 | 157,800 | 27,710 |
| 1 / FULL / run-5 (forward) | 46 | 23 | 63,342 | 9,059 |
| 2 / GROUP / run-1 (forward) | 138,061 | 12,083 | 0 | 48,994 |
| 2 / GROUP / run-2 (backward) | 633,566 | 6,516 | 71,828 | 35,971 |
| 2 / GROUP / run-3 (forward) | 454 | 49 | 49,189 | 9,059 |
| 2 / GROUP / run-4 (backward) | 719,955 | 4,564 | 164,667 | 27,710 |
| 2 / GROUP / run-5 (forward) | 46 | 23 | 63,126 | 9,059 |
| 2 / FULL / run-1 (forward) | 141,374 | 12,083 | 0 | 11,077 |
| 2 / FULL / run-2 (backward) | 388,859 | 6,172 | 46,779 | 27,732 |
| 2 / FULL / run-3 (forward) | 154 | 49 | 39,745 | 9,059 |
| 2 / FULL / run-4 (backward) | 719,222 | 4,564 | 157,798 | 27,710 |
| 2 / FULL / run-5 (forward) | 46 | 23 | 63,342 | 9,059 |
| 3 / GROUP / run-1 (forward) | 138,061 | 12,083 | 0 | 48,994 |
| 3 / GROUP / run-2 (backward) | 633,584 | 6,516 | 71,871 | 35,971 |
| 3 / GROUP / run-3 (forward) | 454 | 49 | 49,189 | 9,059 |
| 3 / GROUP / run-4 (backward) | 720,021 | 4,564 | 164,685 | 27,710 |
| 3 / GROUP / run-5 (forward) | 46 | 23 | 63,126 | 9,059 |
| 3 / GROUP / run-6 (backward) | 1,164,946 | 3,859 | 210,832 | 27,710 |
| 3 / GROUP / run-7 (forward) | 46 | 23 | 63,176 | 9,059 |
| 3 / FULL / run-1 (forward) | 141,374 | 12,083 | 0 | 11,077 |
| 3 / FULL / run-2 (backward) | 388,866 | 6,172 | 46,716 | 27,732 |
| 3 / FULL / run-3 (forward) | 154 | 49 | 39,745 | 9,059 |
| 3 / FULL / run-4 (backward) | 719,278 | 4,564 | 157,773 | 27,710 |
| 3 / FULL / run-5 (forward) | 46 | 23 | 63,342 | 9,059 |
| 3 / FULL / run-6 (backward) | 1,164,946 | 3,859 | 210,860 | 27,710 |
| 3 / FULL / run-7 (forward) | 46 | 23 | 63,392 | 9,059 |

Demand entries are explicit patterns, not all raw summaries or logical
facts. These counters do not classify premise/conclusion shapes. Accepted
forward reports are labelled per command in the status table. They can be
different stages; no final backward completion is inferred from a report.

| Command / engine | Last sampled reservations | Peak sampled reservations | Last tick before engine end s |
| --- | ---: | ---: | ---: |
| 1 / GROUP / run-1 COMPLETE | 247 | 9,124 | 9.834 |
| 1 / GROUP / run-2 COMPLETE | 8 | 11,402 | 0.354 |
| 1 / GROUP / run-3 COMPLETE | 634 | 8,654 | 6.775 |
| 1 / GROUP / run-4 TIMEOUT | 530 | 11,402 | 6.370 |
| 1 / FULL / run-1 COMPLETE | 180 | 9,122 | 5.176 |
| 1 / FULL / run-2 COMPLETE | 144 | 11,799 | 7.948 |
| 1 / FULL / run-3 COMPLETE | 634 | 8,306 | 8.937 |
| 1 / FULL / run-4 COMPLETE | 25 | 10,899 | 1.235 |
| 1 / FULL / run-5 COMPLETE | 66 | 8,409 | 0.928 |
| 1 / FULL / run-6 TIMEOUT | 977 | 11,283 | 8.057 |
| 2 / GROUP / run-1 COMPLETE | 12 | 9,120 | 0.140 |
| 2 / GROUP / run-2 COMPLETE | 43 | 11,052 | 5.112 |
| 2 / GROUP / run-3 COMPLETE | 670 | 8,609 | 3.491 |
| 2 / GROUP / run-4 COMPLETE | 101 | 10,947 | 5.085 |
| 2 / GROUP / run-5 COMPLETE | 347 | 8,429 | 1.756 |
| 2 / GROUP / run-6 TIMEOUT | 341 | 11,628 | 7.576 |
| 2 / FULL / run-1 COMPLETE | 120 | 9,126 | 4.208 |
| 2 / FULL / run-2 COMPLETE | 12 | 11,643 | 0.382 |
| 2 / FULL / run-3 COMPLETE | 505 | 8,357 | 4.702 |
| 2 / FULL / run-4 COMPLETE | 103 | 10,900 | 9.330 |
| 2 / FULL / run-5 COMPLETE | 465 | 8,297 | 4.070 |
| 2 / FULL / run-6 TIMEOUT | 672 | 11,728 | 0.367 |
| 3 / GROUP / run-1 COMPLETE | 268 | 9,124 | 8.397 |
| 3 / GROUP / run-2 COMPLETE | 50 | 12,028 | 4.945 |
| 3 / GROUP / run-3 COMPLETE | 907 | 8,627 | 8.595 |
| 3 / GROUP / run-4 COMPLETE | 67 | 10,929 | 2.987 |
| 3 / GROUP / run-5 COMPLETE | 487 | 8,337 | 3.432 |
| 3 / GROUP / run-6 COMPLETE | 16 | 10,786 | 5.901 |
| 3 / GROUP / run-7 COMPLETE | 575 | 8,440 | 7.349 |
| 3 / GROUP / run-8 TIMEOUT | 9,024 | 9,024 | 0.559 |
| 3 / FULL / run-1 COMPLETE | 61 | 9,126 | 1.230 |
| 3 / FULL / run-2 COMPLETE | 256 | 10,891 | 8.962 |
| 3 / FULL / run-3 COMPLETE | 586 | 8,259 | 6.299 |
| 3 / FULL / run-4 COMPLETE | 173 | 10,971 | 7.876 |
| 3 / FULL / run-5 COMPLETE | 567 | 8,338 | 5.891 |
| 3 / FULL / run-6 COMPLETE | 33 | 10,999 | 9.378 |
| 3 / FULL / run-7 COMPLETE | 508 | 8,325 | 4.272 |
| 3 / FULL / run-8 TIMEOUT | 678 | 10,803 | 1.350 |

These are sampled unfinished-event reservations, not terminal queue lengths
or exact fact work. The first-pair normal native captures start and end in
active run-1. Their near-limit captures start and end in active GROUP
run-4 and FULL run-6. Each requests 30 seconds. Matching JFR windows use closed launch
receipts and profile elapsed times with approximately one-second clock/
setup uncertainty. Each full uncertainty interval remains inside the
positively observed engine. Later-run samples are not labelled run-1.

## CPU, allocation and resource evidence

### Counter coverage

The bridge emits scoped admissions, service transitions and periodic
handled-event counts. It does not emit an exact all-method AP-application
total, complete raw-summary admission total, engine-wide reached-mark
census or per-fact mark-membership distribution. Those metrics are
**unavailable in both modes**, not zero. Static atom counts cover only
the static service. Periodic handled-event counts are not fact counts.
Command-end form counters can include multiple engines and barriers;
they are not per-run build counters.

The report uses phase-bounded allocation samples, GC records and process
resources for both modes. A run-2 OOM heap is not a measurement of the
earlier GROUP run-1 retained peak. The absence of such a run-1 heap must
not be replaced with the process RSS value. These limits remain visible
when interpreting the paired ratios and proposal savings.

The first GROUP run-1 interval is 20:02:40.254 to 20:05:15.813 UTC. The JFR
analysis selects events by this interval, not by the `run1.jfr` filename.
The dump also includes earlier work and a short part of the barrier.

The first JFR export used the printer's default five stack frames. A new
bounded `RecordingFile` reader uses the full recorded stack. Its first-five
control matches the earlier counts, weights and classifications: 39 FULL
controls, 66 GROUP controls and 27 separate GROUP run-1 snapshot controls
pass. Tables and source shares below use the full recorded stacks.
Native collapsed-stack shares keep their separate native denominator.

| Run-1 window | Java CPU samples | Allocation samples | Allocation sample weight | GC pauses |
| --- | ---: | ---: | ---: | ---: |
| Whole engine | 28,371 | 41,351 | 591.170 GB | 27.134 s |
| Engine +60 to +90 s | 6,868 | 8,751 | 149.767 GB | 4.399 s |
| Last 30 s | 2,653 | 7,520 | 69.697 GB | 3.248 s |

Weights estimate allocation throughput; they are not retained memory.
AP traversal accounts for 38.40% of whole-engine allocation sample weight,
47.74% in the +60-to-90-second window and 68.60% in the last 30 seconds.
The first owned allocation frame `TrieOpsKt.walkNodes` accounts for
12.18%, 16.53% and 18.45% of the respective weights. Source attribution
must still identify what repeated computation can be removed.

The closed 30-second native run-1 window has 34,565 samples. G1/GangWorker
stacks account for 39.77%. Inclusive call processing accounts for 40.80%,
subscriptions for 18.85%, summary application for 17.14% and
`FormsCache.payload` for 17.94%. These contexts overlap. Do not sum them
or treat Java-only sample shares as shares of all process CPU.

Full recorded-stack JFR gives this whole-run-1 attribution. All rows are
inclusive and overlap. For example, alias and CFG construction can occur
inside call-plan construction.

| GROUP run-1 context | Java CPU samples | Allocation sample weight |
| --- | ---: | ---: |
| Actual call-plan construction | 46.38% | 183.739 GB / 31.08% |
| Actual alias computation | 27.89% | 104.847 GB / 17.74% |
| Whole CFG construction | 9.02% | 40.584 GB / 6.87% |
| Canonical GROUP projection | 4.17% | 72.735 GB / 12.30% |

Source inspection finds a concrete repeated computation: generated-method
adjacency queries call `flowGraph()`, which constructs the whole graph.
Some alias/local-reachability consumers ask for adjacency once per node
or growing state. Reusing one immutable graph in the analysis consumer
can remove those rebuilds. This is not an IR storage redesign. The
observed whole-CFG share alone does not support a 2x engine speedup claim.

Prescan also performs alias computation: 6,852 Java samples and 74.23 GB of
allocation sample weight in the first GROUP command. That proves work in
both phases, but not same-method duplication. The staged pipeline clears
prescan contexts and runner stores before run-1. Source inspection rules
out retaining both complete alias stores through those owners. This
lifecycle is unchanged from the October 15 bidirectional runtime; it
cannot be presented as a newly introduced ownership regression.

During the first GROUP run-1, sampled process RSS peaks at 14.73 GB. Host
available RAM is 4.19–5.47 GB; swap use is 13.23–13.27 GB; load is
15.42–21.83. Fresh main Thingsboard ran with 30.52–44.46 GB available and
load 3.83–9.49.
The host conditions differ substantially. The timing difference does not
isolate an analyzer-mode or per-patch effect. Unrelated jobs were not
stopped. GC log parsing records 27.165 s of run-1 pauses; the small
difference from JFR follows the different event sources and boundaries.

Phase-scoped resources for all 24 closed commands are below. RSS is
process residency, not retained Java heap. RAM, swap and load ranges
describe the host; they are not an attribution to this analyzer. GC values
use completed pause records from the GC log. Sampling can omit phase edges.

| Project / command / engine | Peak sampled RSS GB | Host available RAM GB | Host swap used GB | Host load | GC pauses s |
| --- | ---: | --- | --- | --- | ---: |
| Thingsboard / 1 / GROUP / run-1 COMPLETE | 14.730 | 4.185–5.465 | 13.227–13.269 | 15.42–21.83 | 27.165 |
| Thingsboard / 2 / GROUP / run-1 COMPLETE | 14.738 | 8.439–29.013 | 18.886–19.036 | 17.38–24.97 | 29.438 |
| Thingsboard / 3 / GROUP / run-1 COMPLETE | 14.802 | 30.059–32.133 | 23.606–23.641 | 9.07–17.66 | 22.191 |
| Thingsboard / 1 / FULL / run-1 OOM | 14.710 | 3.329–19.223 | 17.336–17.729 | 19.82–31.77 | 99.449 |
| Thingsboard / 2 / FULL / run-1 OOM | 14.831 | 6.539–37.222 | 17.522–19.039 | 10.42–26.99 | 96.198 |
| Thingsboard / 1 / GROUP / run-2 OOM | 14.942 | 3.406–17.538 | 13.379–19.177 | 16.61–25.35 | 72.563 |
| Thingsboard / 2 / GROUP / run-2 OOM | 15.057 | 6.560–29.722 | 18.681–19.381 | 16.99–26.38 | 102.060 |
| Thingsboard / 3 / GROUP / run-2 OOM | 15.045 | 29.383–29.980 | 23.273–23.597 | 13.68–19.35 | 69.742 |
| Thingsboard / 3 / FULL / run-1 OOM | 14.657 | 33.315–42.647 | 22.619–23.409 | 6.07–14.18 | 70.539 |
| Conductor / 1 / GROUP / run-1 COMPLETE | 9.964 | 47.237–47.306 | 22.598–22.600 | 7.93–9.37 | 1.492 |
| Conductor / 1 / GROUP / run-2 OOM | 10.173 | 47.033–47.164 | 22.582–22.597 | 9.17–12.39 | 24.923 |
| Conductor / 1 / FULL / run-1 COMPLETE | 9.806 | 47.303–47.371 | 22.524–22.528 | 6.89–8.60 | 1.098 |
| Conductor / 1 / FULL / run-2 OOM | 10.002 | 47.121–47.284 | 22.509–22.524 | 8.16–12.24 | 26.099 |
| Conductor / 2 / FULL / run-1 COMPLETE | 9.807 | 47.242–47.331 | 21.670–21.698 | 7.24–8.82 | 1.085 |
| Conductor / 2 / FULL / run-2 OOM | 9.991 | 46.483–47.223 | 21.189–21.669 | 8.27–12.16 | 30.153 |
| Conductor / 2 / GROUP / run-1 COMPLETE | 9.839 | 46.593–46.666 | 21.163–21.173 | 6.71–8.22 | 1.458 |
| Conductor / 2 / GROUP / run-2 OOM | 10.023 | 46.416–46.585 | 21.154–21.164 | 8.69–11.49 | 26.815 |
| Conductor / 3 / GROUP / run-1 COMPLETE | 9.865 | 46.569–46.676 | 21.150–21.152 | 7.22–9.33 | 1.488 |
| Conductor / 3 / GROUP / run-2 OOM | 10.055 | 46.401–46.554 | 21.137–21.150 | 8.29–10.74 | 25.160 |
| Conductor / 3 / FULL / run-1 COMPLETE | 9.838 | 46.561–46.601 | 21.125–21.126 | 4.63–6.50 | 1.125 |
| Conductor / 3 / FULL / run-2 OOM | 10.029 | 46.373–46.534 | 21.112–21.125 | 6.50–10.78 | 26.168 |
| Stirling-PDF / 1 / GROUP / run-1 COMPLETE | 9.862 | 46.467–46.614 | 21.088–21.092 | 7.41–8.35 | 1.022 |
| Stirling-PDF / 1 / GROUP / run-2 OOM | 10.153 | 41.388–46.446 | 20.783–21.088 | 7.22–14.68 | 75.895 |
| Stirling-PDF / 1 / FULL / run-1 COMPLETE | 9.852 | 45.686–48.137 | 20.655–20.658 | 9.89–10.91 | 1.028 |
| Stirling-PDF / 1 / FULL / run-2 OOM | 10.071 | 45.621–45.921 | 20.624–20.674 | 10.71–15.22 | 45.811 |
| Stirling-PDF / 2 / FULL / run-1 COMPLETE | 9.780 | 44.919–48.250 | 20.668–20.676 | 7.94–9.79 | 1.137 |
| Stirling-PDF / 2 / FULL / run-2 OOM | 10.068 | 37.703–45.968 | 20.675–20.855 | 7.94–19.85 | 56.269 |
| Stirling-PDF / 2 / GROUP / run-1 COMPLETE | 10.113 | 38.107–45.360 | 20.834–20.888 | 11.58–16.60 | 1.641 |
| Stirling-PDF / 2 / GROUP / run-2 OOM | 10.448 | 26.727–46.689 | 17.146–21.510 | 15.35–29.48 | 87.064 |
| Stirling-PDF / 3 / GROUP / run-1 COMPLETE | 10.105 | 36.505–42.172 | 17.136–17.159 | 11.38–15.55 | 1.376 |
| Stirling-PDF / 3 / GROUP / run-2 OOM | 10.447 | 20.023–44.670 | 17.059–17.434 | 14.98–32.24 | 96.231 |
| Stirling-PDF / 3 / FULL / run-1 COMPLETE | 8.540 | 36.764–43.836 | 17.297–17.302 | 19.88–24.48 | 1.567 |
| Stirling-PDF / 3 / FULL / run-2 OOM | 10.287 | 29.487–41.188 | 17.187–17.297 | 19.85–27.92 | 52.976 |
| OWASP / 1 / GROUP / run-1 COMPLETE | 10.162 | 34.280–47.527 | 17.100–17.208 | 8.78–13.53 | 5.160 |
| OWASP / 1 / GROUP / run-2 COMPLETE | 10.282 | 32.781–46.581 | 15.918–17.134 | 10.52–16.35 | 9.399 |
| OWASP / 1 / GROUP / run-3 COMPLETE | 10.295 | 31.063–46.692 | 15.928–15.989 | 10.04–13.56 | 3.218 |
| OWASP / 1 / GROUP / run-4 TIMEOUT | 10.301 | 29.562–42.734 | 15.978–15.988 | 11.12–19.24 | 9.039 |
| OWASP / 1 / FULL / run-1 COMPLETE | 10.004 | 34.507–49.444 | 15.956–15.970 | 11.06–17.73 | 5.470 |
| OWASP / 1 / FULL / run-2 COMPLETE | 10.095 | 34.865–46.960 | 15.943–15.956 | 7.56–18.69 | 6.183 |
| OWASP / 1 / FULL / run-3 COMPLETE | 10.211 | 35.473–46.275 | 15.938–15.943 | 9.00–11.31 | 2.667 |
| OWASP / 1 / FULL / run-4 COMPLETE | 10.122 | 33.791–46.865 | 15.926–15.938 | 5.73–14.25 | 8.030 |
| OWASP / 1 / FULL / run-5 COMPLETE | 10.130 | 29.230–46.670 | 15.924–15.990 | 12.12–14.60 | 3.061 |
| OWASP / 1 / FULL / run-6 TIMEOUT | 10.132 | 29.700–40.912 | 15.980–15.987 | 11.42–14.59 | 3.834 |
| OWASP / 2 / GROUP / run-1 COMPLETE | 10.041 | 39.386–50.734 | 13.979–13.988 | 5.40–10.79 | 4.750 |
| OWASP / 2 / GROUP / run-2 COMPLETE | 10.215 | 40.594–46.924 | 13.950–13.979 | 3.88–8.36 | 6.508 |
| OWASP / 2 / GROUP / run-3 COMPLETE | 10.234 | 40.532–46.892 | 13.950–13.950 | 6.36–8.05 | 2.409 |
| OWASP / 2 / GROUP / run-4 COMPLETE | 10.237 | 40.374–46.857 | 13.940–13.950 | 4.96–6.88 | 7.557 |
| OWASP / 2 / GROUP / run-5 COMPLETE | 10.277 | 40.825–47.792 | 13.939–13.940 | 4.78–6.67 | 2.705 |
| OWASP / 2 / GROUP / run-6 TIMEOUT | 10.287 | 39.865–46.843 | 13.936–13.939 | 4.90–9.50 | 7.227 |
| OWASP / 2 / FULL / run-1 COMPLETE | 10.005 | 30.437–45.307 | 15.959–15.972 | 11.11–15.67 | 5.586 |
| OWASP / 2 / FULL / run-2 COMPLETE | 10.116 | 33.351–46.814 | 15.861–15.958 | 8.66–14.19 | 7.161 |
| OWASP / 2 / FULL / run-3 COMPLETE | 10.140 | 36.203–42.565 | 15.856–15.860 | 8.39–10.70 | 2.663 |
| OWASP / 2 / FULL / run-4 COMPLETE | 10.146 | 35.998–47.083 | 14.003–15.856 | 6.16–12.33 | 7.374 |
| OWASP / 2 / FULL / run-5 COMPLETE | 10.153 | 40.617–47.032 | 14.000–14.003 | 8.56–13.51 | 2.510 |
| OWASP / 2 / FULL / run-6 TIMEOUT | 10.158 | 39.062–47.013 | 13.994–14.000 | 8.16–10.82 | 4.720 |
| OWASP / 3 / GROUP / run-1 COMPLETE | 9.006 | 48.018–51.148 | 13.930–13.933 | 1.62–4.09 | 5.811 |
| OWASP / 3 / GROUP / run-2 COMPLETE | 10.398 | 46.649–46.837 | 13.925–13.930 | 1.51–4.98 | 6.178 |
| OWASP / 3 / GROUP / run-3 COMPLETE | 10.324 | 46.728–46.745 | 13.924–13.925 | 3.03–4.83 | 2.291 |
| OWASP / 3 / GROUP / run-4 COMPLETE | 10.330 | 46.672–46.736 | 13.916–13.924 | 2.25–4.34 | 6.888 |
| OWASP / 3 / GROUP / run-5 COMPLETE | 10.369 | 46.625–47.589 | 13.915–13.916 | 3.10–4.21 | 2.505 |
| OWASP / 3 / GROUP / run-6 COMPLETE | 10.373 | 46.602–46.627 | 13.912–13.915 | 3.07–5.32 | 7.475 |
| OWASP / 3 / GROUP / run-7 COMPLETE | 10.394 | 46.590–46.821 | 13.912–13.912 | 3.52–4.75 | 2.470 |
| OWASP / 3 / GROUP / run-8 TIMEOUT | 10.415 | 46.590–46.610 | 13.912–13.912 | 3.31–3.85 | 0.741 |
| OWASP / 3 / FULL / run-1 COMPLETE | 9.967 | 47.015–50.837 | 13.896–13.897 | 2.84–4.30 | 3.692 |
| OWASP / 3 / FULL / run-2 COMPLETE | 10.197 | 46.773–46.978 | 13.892–13.896 | 3.51–4.69 | 4.893 |
| OWASP / 3 / FULL / run-3 COMPLETE | 10.130 | 46.825–46.860 | 13.892–13.892 | 3.80–4.97 | 2.113 |
| OWASP / 3 / FULL / run-4 COMPLETE | 10.137 | 46.814–46.836 | 13.891–13.892 | 3.53–5.70 | 6.416 |
| OWASP / 3 / FULL / run-5 COMPLETE | 10.179 | 46.803–48.081 | 13.890–13.891 | 3.31–4.91 | 2.613 |
| OWASP / 3 / FULL / run-6 COMPLETE | 10.184 | 46.764–46.799 | 13.889–13.890 | 3.12–4.89 | 7.236 |
| OWASP / 3 / FULL / run-7 COMPLETE | 10.206 | 46.750–47.337 | 13.887–13.889 | 3.14–4.55 | 2.418 |
| OWASP / 3 / FULL / run-8 TIMEOUT | 10.208 | 46.726–46.749 | 13.886–13.887 | 2.25–4.74 | 5.196 |

GROUP 2's sampled process CPU deltas are 1,671.90 seconds in run-1 and
2,927.99 seconds in run-2. These omit unsampled phase boundaries. Its
run-2 prefix admits 1,629,921 fresh initials and 3,676,604 caller-input
deltas before OOM. It produces no complete run-2 result; its latest
accepted forward report remains run-1.

GROUP 3's sampled process CPU deltas are 1,537.10 seconds in run-1 and
2,592.83 seconds in run-2. Its run-2 prefix admits 1,648,697 fresh initials
and 3,730,857 caller-input deltas before OOM. Its latest accepted forward
report also remains run-1. The host ranges differ across repetitions;
the faster third GROUP engine does not isolate a per-patch or mode effect.

FULL 3's sampled run-1 process CPU delta is 2,496.43 seconds. Its separate
run-1 recording attempt returned zero but reported `No recordings to dump
from`; no run-1 JFR file exists. That recording is unavailable. The native
+60-to-90-second window was active in run-1. This report adds no new profile
attribution for that window or its continuous recording.

Conductor GROUP 1's sampled CPU deltas are 213.92 seconds in run-1 and
760.02 seconds in run-2. FULL 1's sampled deltas are 154.65 and 757.78
seconds. All process CPU deltas omit unsampled phase edges.

Profile receipts and definitions are in
`validation/closed-profile-analysis-receipt.json`,
`validation/thingsboard-pair1-group-run1-profile.json`,
`validation/thingsboard-pair1-group-native-normal-profile.json` and
`validation/metrics/closed-metrics.json` under the evidence root.

The closed GROUP run-2 profile has 36,071 Java CPU samples and 1,130.574 GB
of allocation sample weight. The nearest AP source family accounts for
43.87% of Java CPU and 64.10% of allocation weight. Summary-entry iteration
appears in 9.82% of Java samples and 14.80% of allocation weight. Source
inspection shows that each admitted later query can rescan raw summaries
for publication permission. This is a specific repeated-work candidate;
its exact visit and duplicate-publication counts remain unmeasured.
The last 30 seconds of that engine overlap the stop-the-world heap dump
and have almost no execution samples. They do not describe normal late
solver activity. See `validation/thingsboard-pair1-group-run2-profile.json`.

The first FULL run's separate run-1 JFR dump failed with `No data found`,
although `jcmd` returned zero. Do not count the return code as a usable
recording. Its closed continuous recording is available for phase-bounded
analysis; coverage must be checked from the actual event timestamps.

That continuous recording covers the full failed run-1 interval. Its
33,710 Java CPU samples and 67,693 allocation samples have 936.736 GB of
allocation sample weight and 99.355 s of GC pauses. AP is the nearest
source family for 25.55% of Java CPU and 43.07% of allocation weight;
cleaner actions account for 11.64% of allocation weight. The valid native
+60-to-90-second window has 23,969 samples: G1/GangWorker accounts for
43.16%, actual call-plan construction for 20.91% and generated CFG
construction for 5.41%. These inclusive native contexts overlap. FULL
has no GROUP canonical-leaf projection in that capture, yet still fails
in run-1. Removing that projection alone does not explain or solve its
larger admitted workload. The final engine window is inside the heap
capture; it is not a normal late-solver window.

The full-stack pre-snapshot windows show sustained GC pressure before
diagnostic capture: FULL run-1 spends 27.286 of 30 seconds in GC pauses;
GROUP run-2 spends 20.733 of 30 seconds. These intervals precede the heap
dump. They distinguish memory-pressure stalls from the later diagnostic
stop. They do not prove a whole-engine termination bound or an infinite
loop. See `validation/thingsboard-pair1-group-recorded-contexts.json` and
`validation/thingsboard-pair1-full-run1-recorded-contexts.json`.

### Conductor: first closed mode pair

Both run-1 engines completed. The continuous JFR files cover their full
engine windows. The native +60-second capture was correctly skipped:
run-1 had already ended. Neither run-2 sample is labelled as run-1.
Both commands later reached **run-2 OOM**, with latest COMPLETE and
accepted forward iteration **run-1**.

| Conductor / pair 1 | Run-1 engine s | Java CPU samples | Allocation sample weight GB | JFR GC pauses s |
| --- | ---: | ---: | ---: | ---: |
| GROUP | 18.213 | 4,680 | 79.435 | 1.492 |
| FULL | 14.549 | 4,203 | 52.907 | 1.097 |

FULL is faster in this single profiled pair even though it commits more
request memberships. GROUP admits more call inputs and has a much larger
provisional finding set. Fewer mark identities do not ensure less total
analysis work: grouped conditions and the selected-mark cleaner policy
can admit more paths. FULL keeps exact selected-mark cleaning; a GROUP
cleaner requests demand but cannot remove its wildcard group. The Simple
results do not identify which mechanism caused each extra path. Keep this
measured mode difference separate from a per-patch performance claim.
All three completed run-1 pairs have the same direction: FULL/GROUP
ratios range from 0.789 to 0.825. The full-depth attribution here still
uses pair 1 only. Host variation prevents a controlled causal claim.

Actual call-plan construction appears in 42.14% of GROUP run-1 Java CPU
samples and 47.99% of FULL samples. Its allocation sample weights are
22.615 GB and 20.504 GB. Actual alias computation appears in 27.18% and
30.74% of Java CPU samples, respectively. Whole-CFG construction appears
in 9.00% and 10.68%. These are inclusive, overlapping contexts.
GROUP canonical-leaf projection adds 15.975 GB of allocation sample
weight, or 20.11%; FULL does not perform that GROUP projection.

The run-2 failed prefixes have 351.559 GB and 322.290 GB of allocation
sample weight for GROUP and FULL configurations. Their JFR GC pauses
are 24.894 s and 26.072 s. The GROUP final 30 seconds before heap capture
already contain 18.427 s of GC pauses. This is memory-pressure work
before diagnostic capture, not evidence of an infinite loop.
Source-bound phase results are in
`validation/conductor-pair1-{group,full}-recorded-contexts.json`.

### Stirling-PDF: first closed mode pair

Run-1 completed in **25.349 s**. Its separate run-1 recording covers the
whole engine. The continuous recording also covers the whole failed
run-2 interval. Run-2 ends with OOM; latest COMPLETE and accepted forward
iteration remains run-1. The +60-second native run-1 capture was skipped
because that engine had ended.

| Phase | Java CPU samples | Allocation sample weight GB | JFR GC pauses s |
| --- | ---: | ---: | ---: |
| run-1 COMPLETE | 5,205 | 83.581 | 1.013 |
| run-2 OOM prefix | 81,538 | 2,259.489 | 75.741 |

In run-1, actual call-plan construction appears in 61.19% of Java CPU
samples and 54.29% of allocation weight. Actual alias computation appears
in 44.75% and 42.73%; whole-CFG construction appears in 26.86% and 28.87%.
These contexts overlap. They describe a run-1 that meets the engine
target, not the much larger later failure.

In run-2, `addLink` appears in 38.75% of Java CPU samples and 32.32% of
allocation weight. Publication-index insertion appears in 32.17% and
31.51%. Raw-summary iteration appears in 16.02% and 17.64%, or 398.630 GB
of allocation weight. These contexts also overlap. The source-backed
proposal to backfill only a newly permitted backward query targets a
specific repeated scan. The profile does not assign all publication work
to that scan or show the retained-byte saving.

The final 30 seconds before heap capture contain 17.915 s of GC pauses.
The last engine window overlaps the diagnostic capture and must not be
used as normal late-solver activity. Both readers report zero truncated
recorded CPU stacks. Evidence is in
`validation/stirling-pair1-group-run{1,2}-recorded-contexts.json`.

FULL completes run-1 in **24.782 s**. It has no separate run-1 dump in its
terminal receipt. Its continuous recording covers both full engine
windows. FULL run-1 has 4,299 Java CPU
samples, 68.181 GB of allocation sample weight and 1.025 s of GC pauses.
Actual alias computation appears in 55.48% of Java CPU samples and
52.14% of allocation weight. This overlaps call-plan and CFG construction.

FULL also reaches run-2 OOM, with run-1 latest COMPLETE and accepted. Its
run-2 prefix has 26,844 Java CPU samples, 722.789 GB of allocation sample
weight and 45.768 s of GC pauses. Raw-summary iteration appears in 7.91%
of Java CPU samples and 9.70% of allocation weight. The last 30 seconds
before capture contain 24.655 s of GC pauses. This is a shorter failed
prefix than GROUP's; its lower allocation is not a completed-analysis
improvement ratio. The exact source is
`validation/stirling-pair1-full-recorded-contexts.json`.

### OWASP: first closed mode pair

This command reaches run-4 TIMEOUT. Its latest COMPLETE and accepted
forward iteration is run-3. It has no OOM capture. The closed continuous
JFR has 16 finished chunks and covers all 18 selected phase/window
intervals, with no reported data-loss event. Run-1 CPU and allocation
totals match its separately captured run-1 recording.

| Engine | Status | Java CPU samples | Allocation weight GB | JFR GC pause sum s |
| --- | --- | ---: | ---: | ---: |
| run-1, forward, limit 1 | COMPLETE | 10,721 | 373.428 | 5.128395 |
| run-2, backward, limit 1 | COMPLETE | 15,055 | 602.231 | 9.346207 |
| run-3, forward, limit 2 | COMPLETE | 2,729 | 101.200 | 3.214015 |
| run-4, backward, limit 3 | TIMEOUT prefix | 7,623 | 290.437 | 8.984923 |

Run-1 has only three static write regions and 75 nonstatic-origin FLOW
writer rows. It has 2,285 read descriptors and 6,293 pending static
memberships. The scoped direct-static call and computed-supply-leaf
counters are zero. This does not mean all static work is zero, but it
does not support blaming a large static-writer directory for this case.

| Whole run-1 inclusive context | Java CPU share | Allocation weight GB | Allocation share |
| --- | ---: | ---: | ---: |
| Ordinary call processing | 80.82% | 336.875 | 90.21% |
| cleanChain | 33.16% | 178.360 | 47.76% |
| Actual call-plan construction | 22.43% | 85.524 | 22.90% |
| GROUP canonical projection | 16.32% | 104.452 | 27.97% |
| Actual alias computation | 7.70% | 14.846 | 3.98% |

These contexts overlap. `RunMethodCallServices` obtains plans for incoming
call deltas and executes rule/cleaner stages independently of the size of
the static service. Canonical projection still walks the TAINT DAG before
it can reuse an unchanged result. This identifies generic call and AP
work, not a measured count of removable operations. The new proposal
requires a producer-postcondition proof before omitting repeated walks.

The normal native capture is inside active run-1: 7,443 samples, with
37.51% in G1/GangWorker frames. The near-limit capture is inside active
run-4: 11,679 samples, with 76.08% in those frames. These are native CPU
shares, not pause-time fractions or Java-sample shares. The nominal
matching JFR windows have about one second of clock/setup uncertainty;
the complete uncertainty intervals remain within their stated engines.

The command completes both run-2 and run-3. Its event counts continue to
grow before the run-4 timeout. This is evidence of substantial executed
work; it does not prove a twofold removable-work bound or rule out every
possible repeated-work cycle. Details and source hashes are in
`validation/owasp-pair1-group-phase-analysis.md` and its sealed profile
receipts.

FULL 1 reaches run-6 TIMEOUT, with latest COMPLETE and accepted forward
run-5. It also has no OOM capture. The completed run-1 FULL/GROUP time
ratio is 0.855423. Its allocation sample-weight ratio is 0.653766:
244.134 GB versus 373.428 GB. This is one profiled pair. Different
condition and selected-cleaner abstractions change the admitted workload;
the 129.294 GB difference is not an isolated projection saving.

FULL run-1 has 8,735 Java CPU samples. Ordinary call processing accounts
for 79.40%, cleaner-chain processing 25.00%, actual call-plan construction
25.54%, and actual aliases 9.99%. These contexts overlap. GROUP projection
is not sampled in FULL, consistent with its identity path. Generic call
and cleaner work remains substantial in both modes.

FULL allocation weights for run-2 through run-6 are 441.086, 95.143,
397.102, 97.614 and 110.784 GB. Only run-6 is a timeout prefix. Its
continuous JFR covers all 26 selected phase/window intervals and reports
no data loss. The normal native window is run-1; the near-limit window
is run-6. Their G1/GangWorker shares are 42.70% and 69.43%. Do not compare
that near-limit window with GROUP's run-4 as if they were the same stage.
Details are in `validation/owasp-pair1-full-phase-analysis.md` and its
sealed context, coverage and native-profile receipts.

## First closed heap: FULL run-1 OOM

The first parsed snapshot is Thingsboard pair 1 FULL at **run-1 OOM**.
It has no COMPLETE or accepted forward iteration. Its SHA-256 is
`e06bd3fae2d88b147f99fbd7d902e6a67bf7b88154ab0743ab845fac26b3b1ed`.
MAT read all objects, with no discard options. The inventory and owner
drill closed with return code zero. All inventory queries were checked;
the later-query and incoming-demand queries correctly have no instances
in this run-1 snapshot. This heap cannot explain run-2 backfill cost.

The root dominator class view gives this additive partition. These are
dominator nodes at the root of the tree, grouped by class. They are not
the sum of every instance's retained size.

| Root dominator family | Retained bytes | MAT heap share |
| --- | ---: | ---: |
| Unit runners | 4,221,196,832 | 37.17% |
| IR RAM storage | 1,618,710,192 | 14.25% |
| Spring rule provider | 1,293,254,376 | 11.39% |
| Active run manager | 785,685,504 | 6.92% |
| JVM method-entry owner | 719,526,816 | 6.34% |

IR storage remains outside the optimization scope. A root owner can keep
many kinds of state. For example, the run manager directly dominates
454,420,136 bytes through its summary-storage directory and 331,235,056
bytes through the static service. The Spring owner directly dominates
the exit-rule provider below it; this does not mean that all its bytes
are Spring-specific rules or ignored end actions.

Exact retained-set queries give these separate owner-union views:

| Owner union | Retained bytes |
| --- | ---: |
| Method edge stores, delta worklists and initial stores | 1,770,136,208 |
| Summary stores, publication indexes and subscriptions | 1,772,220,536 |
| Run-1 static service and saved-point directories | 1,301,242,496 |
| Lowered-form and JVM method-entry owners | 719,566,400 |
| Input accumulators | 501,003,016 |
| Ordinary request stores | 48,609,312 |

**These unions overlap. Do not add them to each other or to the root
partition.** The OQL detail tables are capped at 1,000 rows. Their row sums
are not complete class totals. The retained-set histograms and class
instance counts supply the complete values above.

The snapshot has 72,962 actors, 808,519 saved point keys and 4,840,208
pending FLOW-image holders. It has 8,806 `LinkIn` events, 4,376 summary
delivery events and 4,226 Work tokens. Queued input coalescing can still
remove repeated admission work, but this snapshot does not support
calling pending input events the dominant run-1 retained owner.
The ordinary request stores are also much smaller than the saved-point
state; reducing stored mark memberships alone would not remove that
state's full retained cost.

Two large actors retain 169,608,712 and 138,127,376 bytes. Their ordinary
method edge stores retain only 205,392 and 255,280 bytes. The next child
drill identifies **temporary StaticBatch state** as the cause: 168,545,936
and 136,931,120 bytes. Supply lists account for 168,528,600 and 136,798,992
bytes within those batches. This is pending producer-effect payload,
not an ordinary stored fact explosion in these two actors.

The exact retained union of all StaticBatch owners is 521,812,144 bytes,
including 2,841,377 RegionGroups supply records. Supplies are appended to
lists until the method fact queue is empty. The global static service
deduplicates them after submission. This identifies a concrete place to
merge equal producer effects before retaining and submitting them.
The two largest batches contain 974,206 and 794,986 supply entries, but
**each has only 13 distinct complete output-region patterns**. The query
retains the complete primitive path, base, tail, mark-slot tag and field
exclusion. Its 13-row result is below the 1,000-row cap. Source inspection
covers its path-list filter: every production supply comes from
`staticSlices`, whose TAINT leaf views use the queried primitive-array
list representation. This supports 13 region rows with merged mark sets
per large batch. It does not say that there are only 13 marks or that all
2.84 million global records have the same duplicate factor.
This union overlaps the actor and other owner views above.

The same heap has 934 live Full atom-key objects, zero Group atom-key
objects and one ControlZero key. The immutable atom interner strongly
keeps every assigned key. All production batch supplies are singleton
atom slices. Even allowing ZERO conservatively, each of these two batches
therefore has at most `13 * 935 = 12,155` distinct `(Pattern, atom)` pairs.
This proves at least 962,051 duplicate memberships in the first batch and
782,831 in the second: more than 98% in each. Exact atom distributions are
not needed for this upper bound.

Together, these two batches alone contain at least **1,744,882 duplicate
memberships**. Deduplicating them reduces the snapshot's 2,841,377 pending
supply records to at most 1,096,495, even if every other batch has no
duplicates: a **2.59x lower bound on pending supply-work reduction** in
this FULL run-1 snapshot. The two batches individually admit at least
80.14x and 65.40x record reduction. This does not establish a whole-engine
time ratio, a GROUP run-1 peak reduction or a final FULL closure size.

The supplementary point/actor report has 46 queries. Ten site-classification
queries failed because their nested OQL string quotes were lost in command
parsing; their absent rows are not zero counts. The actor/component, batch
union and configuration-cache results above are separate successful queries.
A second site-query attempt also used the wrong heap-argument quoting
mode: ten zero-result site tables are invalid measurements, and four
path/exclusion census queries report parse errors. Only the six explicit
OQL size, distinct-pattern and bounded-payload queries in that report
supply valid evidence. The source coverage proof above supplies the path
implementation check. Neither failed site attempt gives a site count.
This is why return code zero alone is not a MAT acceptance check.

The same-snapshot form counters show 1,885,879 successful builds, 920,423
live FormSlots and zero build failures; nine build attempts are in progress.
Source inspection proves that each successful production build uses an
already published slot, and the strong method-entry maps never remove
its owner during this run. A slot has at most one first successful build.
Thus at least **965,456 successful builds repeated a previously built
slot**, or 51.19% by count. This is a lower bound, not a repeated CPU-time
share. Build costs vary, and keeping all payloads could exceed the same
heap limit. The bound does not justify a reference-manager rewrite.
The proof and six source hashes are in
`validation/forms-cache-lifetime-source-proof.json` and `.md`.

There are 1,113,722 distinct pending
FLOW-root object identities for the 4,840,208 holders. That is global root
sharing, not a per-point coalescing factor or proof that saved images can
be discarded.

All eight configuration-role stores have empty no-end-action cache maps.
The normal method-sink and method-exit-sink owners retain 537,366,056 and
536,070,360 bytes, respectively. Their normal caches cover 39,805 and
51,953 methods. Source review explains the bypass: the production Spring
wrapper does not forward the explicit bidirectional-view capability.
The late adapter removes end actions after delegate binding. These cache
owners include required conditions and other rule data; their whole size
is not an estimate of avoidable end-action memory. A role-preserving early
view-selection fix is a next-iteration proposal, not part of this patch.

Explicit strong-root queries exclude `Reference.referent`. They find
strong paths for the run manager, largest unit runner, JVM entry owner
and Spring rule provider. The method-form payload query finds no live
payload through a FormSlot soft reference at capture time. Active
payload objects can still exist elsewhere. This is consistent with
cache reclamation; it does not prove how much repeated form construction
can be removed while preserving the memory limit.

Evidence: `validation/heap-first-full/inventory-receipt.json`,
`validation/heap-first-full-drill/inventory-receipt.json`, and the
`CurrentErasureOwners` / `CurrentOwnerDrill` ZIP reports beside that heap.
The inventory has 21 sections: 19 result files and two valid empty results.
The owner drills are supplementary. The partial 46-query report is
`guard-live_CurrentPointActorDri.zip`; the MAT-generated suffix is truncated.
Its receipt is `validation/heap-first-full-point-drill/inventory-receipt.json`.
The partial supply census is in `guard-live_CurrentSupplyCensus.zip`, with
receipt `validation/heap-first-full-supply-census/inventory-receipt.json`.
It has six valid and fourteen invalid sections. Only the valid sections
provide measurements. The other four representative inventories and
their owner classifications are complete in the sections below.

## First closed heap: GROUP run-2 OOM

The second parsed heap is Thingsboard pair 1 GROUP at **run-2 OOM**.
Its latest COMPLETE and accepted forward iteration is **run-1**. The
active run-2 uses FULL marks and limit 1. The heap SHA-256 is
`c2f59a16773ecb095b339fc5da2e178629f8801cf3ca9d4cdf70ad5b6b7f01bc`.
MAT reports 11,415,771,784 live object bytes. All 26 inventory sections
were checked: 24 result files and two valid absent run-1-owner sections.

| Root dominator family | Retained bytes | MAT heap share |
| --- | ---: | ---: |
| Unit runners | 3,327,168,816 | 29.15% |
| IR RAM storage | 1,618,641,656 | 14.18% |
| Spring rule provider | 1,285,154,704 | 11.26% |
| JVM method-entry owner | 1,050,960,248 | 9.21% |
| Active run manager | 596,782,400 | 5.23% |
| Run configuration | 423,508,416 | 3.71% |

This is a root-node partition, not a sum over every instance. Separate
retained-set views show 2,006,044,184 bytes for method execution stores,
1,602,728,928 bytes for summaries/subscriptions, and 486,437,232 bytes
for incoming demand plus actual query owners. **These unions overlap**
with each other and with the root partition. DemandStore alone retains
405,638,552 bytes; subtracting it from a union does not give a direct
class ownership partition. The run-1 service and saved-point union is
zero here. The run-1 temporary static mechanism does not survive as an
active run-2 owner.

The live event census has 656,415 LinkIn objects, 283,383 ZeroIn objects,
9,589 Delivery objects, 4,911 Work objects and 3,760 Start objects:
958,058 total. A separate EventQueue.Entry census has 955,897 instances.
Class counts are not a root-path proof that every event is still queued.
The exact retained union of all LinkIn events is only **36,224,688 bytes**,
about 0.317% of live heap. Delivery events retain 18,297,568 bytes in a
separate union. Most referenced facts can remain live through other
stores. Do not explain the OOM as one large, independently owned payload
for each of the roughly 958,000 event reservations.

Input coalescing can still remove repeated handler/projection work. Its
complete pending-key multiplicity has not been measured; the 1,000-row
LinkIn table is a sample. The source-backed backward-query backfill issue
also remains a CPU/allocation candidate. These event byte sizes do not
establish a dominant retained-memory fix or a 2x whole-engine benefit.

Evidence: `validation/heap-first-group-run2/inventory-receipt.json`,
`validation/group1-run2-heap-owner-analysis.json` and `.md`, and
`guard-live_CurrentRun2Owners.zip` beside the first GROUP heap. The complete
root-class table has 973 rows. Capped actor/store/query samples are not
used as global totals.

## Closed cohort and completed representative heap work

All 24 candidate commands are closed. Raw pairs, phase times, ratios,
findings and actual latest reached/COMPLETE/accepted labels are recorded
above. No candidate command completes the whole analysis: 18 end with OOM
and six with TIMEOUT. There is no fresh same-mode pre-patch timing control.

Strict rule/method/path/span/instruction comparisons are complete for all
24 commands. The two Stirling-PDF return keys remain missing and
noncensored. Thingsboard FULL has no accepted forward report; its 14
absent baseline keys are censored. Smaller result counts alone do not
establish a false negative or a precision improvement. Simple exports do
not prove the concrete source paths present in main.

The existing nine cleaner and two exit-scope test failures remain failed
gates. Fresh exact-base controls reproduce all eleven with the same
normalized observations. These controls do not turn them into accepted
results and do not establish project parity.

All six representative OOM heaps and the end-list census are analyzed.
All eighteen OOM snapshots are retained. The selected first-pair heaps
cover each OOM project/mode; later repetition heaps are not independently
assigned retained-owner totals. OWASP has no OOM heap in any command.

Use root-level dominators for additive sizes. Keep overlapping retained
sets separate. Check children and strong versus reference-mediated root
paths. A run-2 heap is not a run-1 peak measurement. MAT analysis has no
measured analyzer running at the same time.

The next-iteration proposal remains a proposal. Prefer removal of whole
repeated work. Generic AP hot frames, duplicate offers and a large
candidate count alone do not establish a twofold engine saving or an
infinite loop. IR storage and reference-manager changes remain out of
scope.

## Completed FULL run-1 end-list census

Snapshot: Thingsboard pair 1 FULL, **run-1 OOM**, no COMPLETE or accepted
forward iteration. This is the same `e06bd3fa...` heap used above. All six
discovery queries and all ten byte queries passed. The partition step
executed six queries; the generic validator rejected two empty results.
Their separate complete-complement control proves zero nonempty entry and
exit lists. The original failed validation record is preserved.

| Sink role | Bound rule owners | Nonempty ArrayList owners | Empty ArrayList owners | Shared EmptyList owners |
| --- | ---: | ---: | ---: | ---: |
| Call | 483,364 | 4 | 483,359 | 1 |
| Entry | 57,796 | 0 | 57,796 | 0 |
| Exit | 3,737,697 | 0 | 3,737,697 | 0 |

These are rule-owner counts, not unique list counts. Distinct-object
queries and cross-role unions account for shared lists.

| Complete retained union | Bytes | Objects |
| --- | ---: | ---: |
| All allocated empty end lists | 102,679,056 | 4,278,294 |
| All nonempty end lists | 480 | 16 |
| All allocated end lists | 102,679,536 | 4,278,310 |

These are separately measured unions; do not add arbitrary retained
views. The all-list scope is about 0.9% of the 11,356,759,304-byte live
heap. It excludes marks kept by the strong interner and other shared
owners. It does not measure engine-time savings. Required conditions,
specialization contexts and the 3,737,697 exit-rule objects remain.
The full 1.29 GB Spring subtree is not an ignored-action saving. The
early-view change is deferred as a small measured memory scope.

Evidence under the campaign root:
`validation/full1-end-action-measured/end-action-findings.json`,
`validation/heap-full1-end-action-stage3/inventory-receipt.json`, and
`validation/heap-full1-end-action-stage2-complement-control/partition-complement-control.json`.
Nineteen focused tool controls check the complement, class/heap identity,
partial coverage, quoted-query errors and tampering. This control does
not validate the older fourteen failed supply-census sections; they
remain invalid and provide no counts.

## Conductor representative run-2 heaps

Both first-pair snapshots are **run-2 BACKWARD/FULL OOM**, after run-1
COMPLETE and accepted. GROUP/FULL below selects run-1 only. All 26 generic
queries passed for each heap. The GROUP root-class table has 996 rows and
is complete. The FULL table reaches its 1,000-row cap; its aggregate live
and root-partition totals are unknown.

| Owner scope, bytes | Configured GROUP | Configured FULL |
| --- | ---: | ---: |
| Complete live heap | 7,611,576,064 | Unknown: capped root table |
| Method execution retained union | 1,600,632,472 | 1,511,555,392 |
| Summary/subscription retained union | 1,019,981,720 | 1,119,817,168 |
| Demand/actual-query retained union | 91,943,736 | 75,115,360 |
| Lowered-form owner retained union | 227,788,368 | 213,635,056 |
| LinkIn retained union | 260,920 | 6,688 |
| Delivery retained union | 1,677,080 | 10,736 |
| Run-1 static owner retained union | 0 | 0 |

Every owner union above is complete but overlaps other views. Do not add
them. GROUP's additive RAM IR storage root is 2,243,197,184 bytes; its
UnitRunner root partition is 2,120,203,328 bytes. IR storage remains
outside the proposed changes.

The complete event counts are 10,574 in GROUP and 401 in FULL. The method
execution union in GROUP contains 3,451,451 ConclusionGroup objects and
6,919,951 HashMaps. Source maps these to statement/premise cells and
separate ordinary summary/publication state. They are not counts of
pending messages or distinct semantic facts. This establishes retained
execution state as a large analyzer owner, not that all such state is
redundant. These run-2 snapshots do not establish a run-1 slowdown.

Full query limits, source ranges and hashes are in
`validation/conductor-pair1-group-heap-owner-analysis.json` and
`validation/conductor-pair1-full-heap-owner-analysis.json`, with their MD
reports. The corresponding `heap-conductor-pair1-{group,full}` directories
contain the guarded inventory receipts and validated ZIP bindings.

## Stirling-PDF representative run-2 heaps

Both first-pair snapshots are **run-2 BACKWARD/FULL OOM**, after run-1
COMPLETE and accepted. Each inventory has 26 validated queries and zero
failures. The GROUP root partition is complete; the FULL root table
reaches its cap, so its complete live heap total is unknown.

| Owner scope, bytes | Configured GROUP | Configured FULL |
| --- | ---: | ---: |
| Complete live heap | 7,591,896,976 | Unknown: capped root table |
| Method execution retained union | 2,763,005,168 | 3,921,286,504 |
| Summary/subscription retained union | 1,364,598,488 | 1,146,274,384 |
| Demand/actual-query retained union | 74,412,632 | 53,498,616 |
| Lowered-form owner retained union | 235,640,808 | 221,322,136 |
| LinkIn retained union | 111,808 | 1,136 |
| Delivery retained union | See source report | 49,760 |
| Run-1 static owner retained union | 0 | 0 |

These exact complete unions overlap and are not added. GROUP's additive
UnitRunner root is 4,037,495,864 bytes; its RAM IR storage root is
977,404,624 bytes. The global ConclusionGroup counts are 8,458,470 in
GROUP and 10,757,637 in FULL. Event counts are only 3,994 and 318.
The source-bound owner reports identify durable statement/premise and
summary state as the large analyzer scope. They do not prove redundant
relations, a causal mode effect or the retained cause of earlier run-1
cost. The two OOM states are different progress prefixes.

Evidence: `validation/stirling-pair1-{group,full}-heap-owner-analysis.json`
and their MD reports. The combined
`validation/conductor-stirling-representative-heaps-summary.json` binds
all 104 validated sections from the four new inventories to their report,
ZIP, heap identity, phase and source interpretation. The complete
`validation/representative-heap-owner-index.json` also includes the two
reused Thingsboard heaps and the first OWASP pair's no-heap metadata.
Every OOM snapshot here is run-2 except Thingsboard FULL, which is run-1.
No large MAT process overlapped a measured analyzer command.
