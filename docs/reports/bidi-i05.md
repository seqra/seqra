# I05 call plans, bindings and aliases

The JVM call builder now constructs the full stage graph. It binds static,
receiver and argument facts, keeps resolved and unresolved paths separate,
applies source and end rules, runs cleaners and returned rewriters, and binds
results back with the declared filters. Constructor pass-over and persisted
aliases use their specified guards. Native pending results retain exclusions
through the returned stages; final demotion remains the engine's E05 duty.

Call resolution shares a small context interface with the existing analyzer.
Empty concrete and expanded lambda targets do not create method keys. An
all-empty result uses unresolved propagation. Independent failure results
remain unresolved paths. The alias helper keeps exactly the paths present
before and after the call. The old callback uses the same helper.

The unresolved-call observer contract and pass-read metadata are available.
I06 wires the tracker hook; E05 calls it for the specified run-1 delta.

## Tests and corrections

Tests preceded the implementation. The missing-API run is
`/tmp/bidi-i05-api-red.log`. Focused tests execute 27 construction tests,
23 transfer tests and 10 resolver/alias adapter tests. They check complete
results against independent vectors and the per-path call walk, including
limits 1–3, requests, original premises, static FLOW exclusions, sibling
paths, late demotion, filters, field actions and reversal.

The plan records each fixture correction before the edit. These include
explicit Kotlin condition types, structural cleaner comparisons, actual
ignored-call statements, object-array sink alternatives and the single-exit
graph required by alias reachability. A diagnostic confirmed that JIR splits
an assignment from a call into a call-result temporary and a later copy.
The final tests check that timing and use a separate before-only heap alias.
No production alias policy change or new Lean proof was needed.

## Final gate

The clean common/JVM test gate and Go test-source, JVM SAST and application
compile checks passed in 68 seconds (`/tmp/bidi-i05-full-gate.log`).

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 72 | 755 | 0 / 0 / 0 |
| JVM dataflow | 16 | 286 | 0 / 0 / 0 |

All 607 new common and 172 new JVM annotated tests have void JVM returns
and appear in fresh XML. Discovery logs are
`/tmp/bidi-i05-{common,jvm}-discovery.txt`; XML copies are under
`/tmp/bidi-i05/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
`git diff --check` passes. I05 is complete.
