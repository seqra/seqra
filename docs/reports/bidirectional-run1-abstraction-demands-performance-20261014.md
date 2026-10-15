# Run-1 abstraction demands: fixed-project performance

Date: 15 October 2026. Status: all main and optimized scans are terminal.
Three optimized projects failed graph-coverage checks. A correctness repair is under verification.
This is a working report. It contains no performance claim for the new runtime.

The implementation is `f965f814ba84083db9ff2b537ca0f706bce1ef10`. Prescan static profiles replace
Position requests, and the initial abstraction owns remaining mark policies. It always retains
abstract `*` initials and adds separate concrete answers only for supplied requested marks.
IR storage and reference-manager internals are unchanged.

## Latest progress

Run-1 starts at limit 1. The target is complete run-1 engine time at most 60 seconds.
Prescan, catalog setup and reporting are separate.

| Project | Latest reached | Last completed | Last accepted report | Fresh run-1 engine |
| --- | --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3; complete | run-3, forward, limit 3 | run-3, forward, limit 3 | 265.491 s; target not met |
| Conductor | run-1, forward, limit 1; failed | None | None | Failed after 0.660 s; no completed run-1 |
| ThingsBoard | run-1, forward, limit 1; failed | None | None | Failed after 1.484 s; no completed run-1 |
| Stirling-PDF | run-1, forward, limit 1; failed | None | None | Failed after 4.419 s; no completed run-1 |

The first launch stopped before building or scanning: available RAM was below the 20 GiB
scheduling floor because another analyzer occupied most host memory. No fixed configuration
was changed. All four main scans have now ended. Optimized OWASP ended without OOM or timeout;
the launcher then stopped before Conductor because available RAM was below the scheduling floor.
Fresh prior-runtime control scans have not started. Host memory pressure during OWASP prevents a
clean timing attribution to this optimization.
The resumed launcher kept all original project settings and scheduled one scan at a time.

The failed optimized outputs contain no findings: 7 main Conductor sites, 14 partial-main
ThingsBoard sites and 17 main Stirling-PDF sites are absent. The cause is analysis abort before
the first report, not a completed propagation result. These are failed comparisons and must not
be labelled no-FN. Only the complete OWASP comparison establishes location parity for this revision.

## Terminal OWASP result

Latest reached, completed and accepted iteration: **run-3, forward, limit 3**.
The strict comparison finds all 4,112 main sink identities and no added unique sinks.
SARIF contains 9,179 optimized occurrences versus 4,112 main occurrences; the 5,067 extra
occurrences are exact duplicate JSON result objects. All 4,112 unique full result objects and
their complete physical and logical locations match main. No exported field varies within a
repeated sink group, and all code-flow arrays are empty. There are no added unique FP locations
or missing main locations on this comparison. Duplicate alert inflation is still an output issue.

The legacy report groups rules by string rule ID and statement. The bidirectional report uses
the full resolved sink-rule object, method and statement, then exports each entry with only its
string rule ID and location. Distinct internal rule keys can therefore become equal exported
rows. The report builder already deduplicates a repeated witness of the same full key across
runs. The saved SARIF does not expose the internal variants, so their exact specialization cannot
be recovered from it. This result does not show 5,067 distinct false positives or trace variants.

| Phase | Optimized time |
| --- | --- |
| Prescan | 63.103 s |
| Static metadata collection | 50.708 s |
| Static graph remap and closure setup | 0.197 s |
| run-1, forward, limit 1 | 265.491 s |
| run-1 report barrier | 1.260 s |
| run-2, backward, limit 2 | 65.086 s |
| run-2 report barrier | 0.110 s |
| run-3, forward, limit 3 | 61.249 s |
| run-3 report barrier | 0.182 s |
| Whole process | 561.066 s |

Peak sampled process RSS was 9,718,587,392 bytes. Available host RAM fell to 568,717,312 bytes
during this run. No guard heap was triggered. The 30-second normal CPU sample covers run-1,
about 60 to 90 seconds after its engine start. Continuous JFR review is pending.

Run-1 retained no N1 request-store rows. Abstraction policy peaks were 6,063 policies and
299,471 mark memberships. The run recorded 24,589,304 admission operations, 24,359,362 duplicate
mark memberships, 230,200 join member scans and 73,296 outgoing mark memberships in 3,767 batches.
Admission operations and duplicate memberships use different units; their ratio is not an
exact duplicate rate. Current counts are zero after release and must not replace the peaks.

The catalog contains no declared static mark queries for OWASP. This result cannot support a
large expected gain from merging static query mark unions on this project.

### Normal run-1 CPU sample

Latest project iteration remains **run-3, forward, limit 3**; this sample covers only run-1,
forward, limit 1. It has 6,459 samples. Application stacks account for 2,869 samples;
known JIT compiler stacks for 2,310, G1 collector stacks for 1,206 and JFR stacks for 461.
These frame families overlap, including JFR work inside application stacks. They are not
disjoint wall-time shares.

| Inclusive application path | Samples | Share of application samples |
| --- | --- | --- |
| Call-plan construction | 1,136 | 39.60% |
| Generic call walk, all fact kinds | 1,415 | 49.32% |
| Cleaner chain, all fact kinds | 987 | 34.40% |
| Cleaner fact dispatch | 632 | 22.03% |
| Path and type filter | 390 | 13.59% |
| Mark-request admission | 64 | 2.23% |
| Abstraction flush | 17 | 0.59% |

These inclusive paths overlap too. Call walking is not proof of concrete-mark or FLOW-only
propagation. This 30-second sample does not establish request processing as the main CPU cost.
Call-plan construction, cleaner work and type checks are stronger investigation leads in it.

The final form-cache totals cover all three engines and their barriers, not run-1 alone.
Scratch metadata collection does not use this counter. The totals are:
70,782,087 accesses, 884,662 successful builds, 308,762 reversals and 186.196 aggregate seconds
inside forward builds. Build time sums concurrent calls and is not engine wall time. The build
count includes first builds; it is not a measured rebuild count. Stored container counts are
cumulative samples across built forms, not live retained bytes.

### Continuous OWASP recording

The terminal OWASP-only export completed under the three workload locks. It used explicit
depth 256 and matched all selected event counts to `jfr summary`. The saved recording includes
metadata setup and all three engines. Some recorded stacks are truncated; printing at a higher
depth cannot recover missing callers. This partial export proves no completion of other projects.

| Window | Java CPU samples | Sampled allocation weight | GC starts | Clipped top-level GC pauses |
| --- | --- | --- | --- | --- |
| Static metadata setup | 1,210 | 64,353,670,752 B | 31 | 0.403 s |
| run-1, forward, limit 1 | 6,799 | 314,283,020,728 B | 501 | 7.353 s |
| run-2, backward, limit 2 | 1,728 | 71,537,608,280 B | 54 | 2.463 s |
| run-3, forward, limit 3 | 1,336 | 65,780,916,136 B | 41 | 1.958 s |

These are sampled allocation weights, not retained bytes or exact allocation counts. Inclusive
paths overlap. During run-1, cleaner dispatch appears under 32.74% of allocation weight, call-plan
construction under 23.72%, and `FlowClean.run` under 9.23%. Selected-mark-only groupable cleaner
work is not isolated by that last frame. Call-plan construction appears in 26.15% of run-1 Java
CPU samples, 75.75% in run-2 and 85.48% in run-3. Java execution samples omit native compiler and
collector CPU; compare them to the normal native-inclusive sample with that limit in mind.

Signed normalization appears under 40.22% of metadata allocation weight. Avoiding repeated
normalization and selected-mark cleaner intermediates are specific core investigation leads.
Neither percentage is a measured removable fraction or a promised memory reduction.

## Conductor graph-coverage failure

Latest reached: **run-1, forward, limit 1, FAILED**. No engine completed and no forward report
was accepted. Prescan completed successfully in 41.319 s; metadata collection took 9.038 s
and graph/catalog setup 0.185 s. The whole process took 118.264 s, with peak sampled RSS
10,010,558,464 bytes. No OOM or timeout occurred.

At 0.660 s into run-1, a call plan resolved this edge, which was absent from the exported graph:

```text
PerplexityAI#getChatOptions(ChatCompletion)
  -> EmbeddingGenRequest#getModel()
```

`JIRMethodEntries.requireRun1CatalogTarget` rejected it. The selected optimization depends on
complete transitive static coverage, so removing that guard would conceal a correctness issue.
The subscription export versus runtime resolution mismatch is under investigation. This failed
run cannot establish a no-FN result, successful run-1 time or performance improvement.

The source audit found a resolver-cache contract defect: `methodOverrides(method, baseClass)`
uses `baseClass` to select the hierarchy subtree but caches only by method. A broad first lookup
can leak a sibling override into a later narrow query; a narrow first lookup can hide valid broad
targets. Legacy prescan resolvers are per unit, while the new engine shares a resolver, so cache
population order can differ. The saved run does not show the exact first population sequence.
The [minimal cache-key repair](../design/bidirectional-prescan-resolution-cache-correctness-20261015.md)
preserves the resolver and graph guard. Four focused tests fail on the original key and pass
with the repair. Broader validation is pending; the repair is not part of this measured revision.

ThingsBoard's optimized prescan completed in 298.948 s. Static metadata collection took
86.405 s and graph/catalog setup 2.105 s. It reached **run-1, forward, limit 1** and failed
after 1.484 s on another missing catalog edge:

```text
CalculatedFieldEntityActor#doProcessCfMsg(ToCalculatedFieldSystemMsg)
  -> FromDeviceRpcResponseActorMsg#getMsgType()
```

No iteration completed and no report was accepted. This is separate from main's prescan timeout.

Stirling-PDF also reached **run-1, forward, limit 1**, then failed after 4.419 s on:

```text
User#setAuthenticationType(AuthenticationType)
  -> ApplicationProperties.Security.LoginMethods#toString()
```

Its prescan took 29.187 s, metadata collection 10.764 s and graph/catalog setup 0.127 s.
The whole process took 83.253 s, with peak sampled RSS 9,207,353,344 bytes. ThingsBoard's whole
process took 448.112 s, with peak sampled RSS 14,544,265,216 bytes. Neither run reported OOM
or timeout. Neither completed an iteration or accepted a forward report.

No optimized MemoryManager OOM occurred in this batch, so no guard heap exists to analyze.
The three failed engines ended before the normal +60-second or near-limit sampling windows.
They cannot supply ordinary run-1 workload profiles. Continuous setup recordings were captured.

## Main baseline status

Main uses the legacy engine and has no bidirectional run-N. No run number is inferred for it.

| Project | Outcome | Prescan | Legacy full engine | Whole process | Peak sampled RSS |
| --- | --- | --- | --- | --- | --- |
| OWASP | OK | 51.135 s | 85.177 s | 205.491 s | 9,042,190,336 B |
| Conductor | OK | 65.717 s | 62.722 s | 253.293 s | 10,162,814,976 B |
| ThingsBoard | Prescan timeout; partial output | 324.962 s | 41.304 s | 473.574 s | 14,464,802,816 B |
| Stirling-PDF | OK | 39.866 s | 14.566 s | 119.450 s | 8,480,628,736 B |

Main ThingsBoard exhausted its prescan allocation of 30% of the 1,080-second IFDS budget.
Its later full-engine phase did not exhaust the full-engine budget. It wrote 14 findings,
but they are a partial baseline. No MemoryManager OOM was reported. Available host RAM fell
to 4,985,364,480 bytes during that process, with 15,348 major faults. This run alone cannot
establish an intrinsic prescan cost or a complete ThingsBoard no-FN comparison.

The new runtime requires a successful prescan before it builds the static closure. A joined
but timed-out prescan is not a complete graph certificate. If that occurs, report setup failure
and no reached run-1; do not accept a partial graph or label it a run-1 timeout.

## Revisions and validation

| Runtime | Revision | Purpose |
| --- | --- | --- |
| Main | `f945f92c5ae554ff4f5fc7696ee5a269210478bb` | Saved `origin/main`; guard-capture hook only |
| Prior runtime | `a23ad7fcd99553190c82557cff6076503959aec6` | Clean checkout for a fresh performance control |
| Optimized | `f965f814ba84083db9ff2b537ca0f706bce1ef10` | Committed abstraction-owned demands |

The final 1,406 common-core and 391 JVM tests pass, with no failures, errors or skips.
Go, JVM SAST and root test sources compile; `:classes` passes. The existing Lean build
and audit pass: 19,472 declarations and no unexpected axioms. These proofs do not prove
the new full catalog closure. The benchmark tools pass 36 tests.

The fresh full input check passes all 85 checks, including 42 exact content datasets.
The same post-run check also passes after all eight main/optimized scans.
It covers models, source trees, class roots, dependencies, rules, pass/dataflow extensions,
toolchains, runner defaults, runtime provenance and the prior-runtime rule tree.
The previous 86 raw capture/index files were removed; compact evidence and reports were retained.
After this failed first measurement pass, its 11 raw recordings/profiles (333,345,718 bytes)
were also removed before the corrected-runtime batch. Logs, SARIF, input receipts and compact
OWASP exports/reviews remain. `cleanup-before-corrected-runtime/receipt.json` records each hash.

## Evidence

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-abstraction-demands/20261014T233118.236284Z
```

`checks/validation.json` binds passing checks to the implementation SHA. `pre-input-binding.json`
binds the fresh input check. `runtime-binding.json` records the clean core. Launch and resource
receipts identify the deferred attempt and the successful restart. Old scan reports are historical and cannot supply
current findings, run iteration, timing, memory or hotspot results.

`terminal-comparisons/` contains all four strict comparisons. The successful OWASP profile
export is `profile-analysis/partial-optimized-OWASP/export-1/`; its scope is explicitly partial.
Prior-runtime controls have not started. The resolver repair requires a new committed runtime
and a fresh batch before a final optimization result or next-iteration ranking can be accepted.
