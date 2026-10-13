# Core rollback and retest

Date: 13 October 2026. Status: revised-core tests pass; fixed-project retest follows.

The user requested removal of the manager lifecycle rewrite and `WeakIdentityCache`. The manager source
is restored exactly from `0d0052936^`. The new weak-cache class and its test are removed. Rule validation
now checks each input directly. Diagnostic deduplication stores only rule class, declared ID, diagnostic
kind and reason strings; it does not retain bound rules or conditions. Anonymous warnings can repeat.

The managed method-form cache keeps its original `createRef` integration, callback pins, single-flight
builds and stable rebuild identities. Manager queue/lock-specific tests and wrapper-count diagnostics
are removed. Original public-API cleanup/re-enable tests and form eviction controls remain.

The [new proposal](../design/bidirectional-core-optimization-20261013.md) ranks optimizations using the
latest reviewed snapshots. The manager redesign and weak identity cache are optional N11/N12 candidates.
They require separate selection and design. The old proposal is marked superseded.

## Revision and verification

The full common suite passes 1,320 tests and the JVM suite passes 377 tests, with no failures, errors or
skips. Go, JVM SAST and root consumer test compilation and analyzer classes pass. The command completes
in 2 minutes 6 seconds. The 11 removed common tests exercise the removed weak-cache utility or the
rolled-back manager implementation; the remaining form and semantic controls are retained.
Command logs are in `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/rollback-retest/20261013T200523Z`.
The comparison controls pass 28 tests and the profile-tool controls pass 8 tests. Benchmark and comparison summaries now include reached-run,
complete-engine and accepted-forward-report fields. Actual start markers determine the latest reached run,
including failed or unfinished runs. Main is explicitly not applicable and missing evidence is unavailable.

## Latest project iteration evidence

These are the latest completed measurements available while the revised code is built. They precede
the rollback and do not establish a result for the revised code. Fresh fixed-configuration scans follow
the core gate. No input, heap, timeout, exclusion policy or positive-limit sequence is changed.

| Project | Measured code | Latest reached | Last complete engine | Last accepted forward report |
| --- | --- | --- | --- | --- |
| OWASP | b0a2448c0 | run-3 F/L3 COMPLETE | run-3 | run-3 |
| Conductor | b0a2448c0 | run-2 B/L2 OOM | run-1 | run-1 |
| ThingsBoard | b0a2448c0 | run-1 F/L1 OOM | None | None |
| Stirling-PDF | b0a2448c0 | run-4 B/L4 TIMEOUT | run-3 | run-3 |

The OWASP warm heap captures run-1, Conductor's primary guard heap captures run-2 and ThingsBoard's
primary guard heap captures run-1. PDF's older checkpoint guard heaps capture run-3; the final primary
scan reaches run-4 without a guard heap. Main has no bidi run iteration.
