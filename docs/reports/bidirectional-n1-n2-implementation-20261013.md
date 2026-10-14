# N1/N2 implementation and validation

Status: implementation, code checks and fresh fixed-project scans complete. Three project resource gates fail.
Date: 13 October 2026. Runtime implementation ends at `314349718`.

N1 groups standing requests by full origin or position and uses primitive memberships and encounter lanes.
Concrete added marks probe only their matching memberships. N2 keeps one current subscription value per
full caller reference and admitted group. Replay captures each fresh delta. Delivery captures current
immutable candidates. E6 keeps full caller-reference/link-layer bindings after matching, so Facts growth
does not create a new member version.

The selected design is [the N1/N2 plan](../design/bidirectional-n1-n3-implementation-proposal.md).
N3 remains deferred. Request transport and scheduler changes are outside this iteration.

| Commit | Change |
| --- | --- |
| `bb3d9f26a` | Grouped standing requests, selective two-arrival-order joins, bounded N1 census |
| `d95dc63d2` | Exact accumulator separated from the indexed raw-link wrapper |
| `e9202f6f8` | Checked E6 bindings, layer-only transfer helper and bounded N2 census |
| `314349718` | Cumulative subscription directory, transient replay, captured delivery and run-attributed counters |

The final working tree passed 105 focused tests, then 1,342 common tests and 377 JVM tests. There were no
failures, errors or skips in those final checks. Go, JVM SAST and root consumer test compilation and root
classes passed. The reporting/profile tools passed 36 tests. The Lean build and full axiom audit passed:
19,466 declarations, zero unexpected axioms. Intermediate new-fixture compile/type errors and two invalid
K6 publications were corrected before the final checks; the production K6 guard was retained.

The independent controls compare full request actions, raw accepted subscription leaves and selected
summary effects. They include current forward/backward modes, must/exact fields, exclusions, full references
and independent layers. Returned cleaner/alias/CALL controls use positive limits 1, 2 and 3. Existing closure
and schedule suites cover both unit layouts. The proof module establishes the logical cumulative encoding
and ordered E6 binding quotient. Full current AP distribution and protocol implementation simulation remain
explicit obligations in [proof-status](../../spec/proof-status.md).

Code-check evidence is in
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/n1-n2/20261013T223640Z/gates`.
The checks ran on the exact final implementation before the reviewable commits; committing changed no
runtime or test bytes. Fresh project manifests name `61fd97acd` as the committed scan revision.

All eight fresh main/current scans are terminal. The
[performance report](bidirectional-n1-n2-performance-20261014.md) records the new timings, strict finding
differences, counter scope and capture evidence. The table below gives the latest reached iterations
of the current implementation. Main uses the legacy analyzer and has no bidi iteration.

| Project | Latest reached | Last complete engine | Last accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3, COMPLETE | run-3 | run-3 |
| Conductor | run-2, backward, limit 2, OOM | run-1 | run-1 |
| ThingsBoard | run-1, forward, limit 1, OOM | None | None |
| Stirling-PDF | run-4, backward, limit 4, TIMEOUT | run-3 | run-3 |

Use fresh main and current reports for OWASP, Conductor, ThingsBoard and Stirling-PDF. The verified remote
main is `f945f92c5`; its opt-in guard-capture patch is recorded separately. Use the fixed Gradle
`runProjectAnalyzer` settings from the plan. Report each engine iteration, latest reached run/direction/limit,
last complete engine, last accepted forward report, and each capture's own iteration. Compare strict rule ID,
sink fingerprint and full method signature identities, and inspect physical/logical occurrence spans.

The optional census retains numeric values and fixed histograms. N1 live counters retire through actor
freeze. Failed actors are not frozen, so their END live values are unretired cohort accounting, not a
post-teardown reachability census. N2 directory counts retire on joined runner release.
Cumulative created-group/posting and final-row totals remain available after join. Pending replay counts
include callbacks that are executing. Array-cell counts exclude object headers, hash child tables and
transient scratch; they are not byte measurements. Mark-ID density is unavailable, and no dense bitmap
was selected. Use fresh heaps for retained-byte and owner analysis.

Before the new batch, cleanup removed 60 previous owned raw heap/profile files (170,043,973,440 bytes).
The cleanup manifest records exact paths and sizes. Reports, CSVs, logs, archives and manifests remain.
Manifest: `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/cleanup/pre-n1-n2-20261013T223111Z.json`.

Use the [fresh performance report](bidirectional-n1-n2-performance-20261014.md) and
[next proposal](../design/bidirectional-core-optimization-20261014.md) for the post-implementation evidence.
The project completion/no-FN gate does not pass: ThingsBoard misses 14 main identities and PDF misses three.
No isolated optimization saving or complete no-FN claim is made here. N3 remains deferred.
