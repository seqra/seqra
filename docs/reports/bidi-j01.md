# J01: Prescan copies and release

The clean gate passes 1,226 common, 353 JVM and 1,313 Go tests. There are
no failures, errors or skipped tests. JVM SAST and application test sources
compile. All 1,078 common and 239 JVM new annotated tests compile to void
and occur in fresh XML.

## Implementation

JIRAnalysisManager exports owned rule-ID snapshots and owned lambda method
unions by caller method and instruction index. Every retained context contributes;
repeated targets deduplicate. Export reads registered tracker values without
adding a subscriber. Only implementation methods escape. The new method-entry
owner freezes these copied values at all three collection levels.

The runner manager owns its active-run flag, progress Job and runner Jobs.
Runner registration and stop admission share a lock; a lazy Job is registered
before it starts. Setup is inside the termination frame. Shutdown requests
all cancellations before its bounded join. A failed join sets EXCEPTION and
keeps unfinished Job references. Copy/release rejects active or unfinished jobs.
Reset and restart use the same completion guard, so a failed prescan cannot
replace the AP manager or reactivate a live worker. Release checks and clears
owners in one locked transition.

After copying and joined validation, the JVM manager resets context caches and
lambda subscribers and clears contexts/selected IDs. The engine clears runners,
unit stores, dependency maps, completed jobs, progress ownership and its old AP
manager. The AP getter then rejects access. Intentional graph, classpath,
fact-checker and external-method-tracker values remain available.

The additive default release method preserves shared Go managers. Healthy old
prescan/full-scan callers do not invoke release and retain their existing reuse
behavior.

## Tests first and corrections

| Tests | Evidence |
|---|---|
| PrescanCopyTest (6) | Accepted serialized rule IDs, false-condition omission, copied values, all context-target unions, separate methods/indexes, non-subscribing export, mutation isolation, real two-root lambda prescan, exact generated callees after release, full MethodKey call-plan cache separation, empty run. |
| PrescanLifetimeTest (6) | Real joined release/stores, real active and cancelled-but-unfinished Jobs, zero-budget cancellation before wait and EXCEPTION, guarded reset/restart, legacy FullScan reuse, bounded GC for actual AP/runner/context/alias owners with shared values alive, independent new Cancellation. |
| PrescanJoinRegressionTest (2) | Before-first-run and unfinished progress ownership guards; setup throw after an actual retained runner restarts, followed by actual cancellation/join and retained-owner identity checks. |

The API RED fails at missing J01 APIs. The first runnable gate passes 12 of
14 tests. The setup fixture first used a generic resolver; it now implements
the JIR resolver required by the existing manager. Its corrected RED fails
at the unfinished restarted Job. The zero-budget RED exposes skipped
cancellation. After those fixes, review adds RED reset/restart checks; both
fail before the final guards. The corrected injection puts the real unfinished
Job inside the current run's setup. Test teardown always unblocks, cancels and
joins controlled Jobs.

The lifetime test keeps the actual manager, engine, params, checker, external
tracker, RefManager, graph, classpath and copied targets strongly alive while
bounded GC clears the old AP, runner, context and alias owners. It populates
real summaries and aliases. This is a finite JVM lifetime check, not a general
GC guarantee.

## Gate

Run from core:

```sh
./gradlew --continue :opentaint-dataflow-core:opentaint-dataflow:cleanTest :opentaint-dataflow-core:opentaint-dataflow:test :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest :opentaint-dataflow-core:opentaint-jvm-dataflow:test :opentaint-dataflow-core:opentaint-go-dataflow:cleanTest :opentaint-dataflow-core:opentaint-go-dataflow:test :opentaint-jvm-sast-dataflow:compileTestKotlin :compileTestKotlin --console=plain
```

Build succeeds in 1 minute 32 seconds. Log: /tmp/bidi-j01-final-gate.log.
Fresh XML copies: /tmp/bidi-j01/{opentaint-dataflow,opentaint-jvm-dataflow,
opentaint-go-dataflow}/xml. Discovery: /tmp/bidi-j01-common-discovery.txt
and /tmp/bidi-j01-jvm-discovery.txt. RED logs and XML remain under /tmp/bidi-j01.
git diff --check passes. JVM entry and output are J02; complete JVM acceptance
is J03.
