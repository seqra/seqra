# J02: JVM entry and phase-3 output

The clean common, JVM and application gate passes. It covers 1,226 common,
361 JVM and 791 application tests with no failures or errors. The nine
application skips match the existing baseline. All 1,078 common, 247 JVM
and 21 application new annotated tests compile to void and occur in fresh XML.

## Implementation

The common BidiEntry interface supplies gather, run, output conversion and
status conversion. JIRBidiAnalysis copies prescan rules, lambda targets,
resolved roots, checker and external tracker. It consumes that snapshot
once and builds independent new AP, interpreter, context, record and witness
owners. The actual IterationDriver uses the remaining analysis budget;
rule selection and entry construction consume that same budget. SharedObjects
closes its dispatcher after analysis.

TaintAnalyzer has an optional hook. JIRTaintAnalyzer accepts a nullable
IterationPolicy; null retains the legacy prescan/full-scan path. The new
entry uses the same cached application graph. Prescan must have joined before
gather; then both legacy managers release their owners before the new run.
Setup catches Throwable, including factory and prescan graph/manager faults,
and returns empty output with EXCEPTION and trace status OK. Closing a failed
lazy engine factory does not retry it or allocate an unused engine.

Every report entry becomes a vulnerability, including DEMAND. Conversion
uses a confirmed witness method key when available, otherwise the first
witness, with the original statement/rule and a Simple trace. The existing
CWE filter remains. COMPLETE/TIMEOUT/OOM/FAILED map to
OK/TIMEOUT/OOM/EXCEPTION. Trace status is OK and counts are logged by state.

## Tests first and corrections

| Tests | Evidence |
|---|---|
| JIRBidiOutputTest (6) | Both report states, all four statuses, confirmed witness preference/full context, original rule/site, empty first report, required/one-use gather, actual empty driver. |
| JIRBidiRealEntryTest (1) | Actual joined prescan/root, selected copied rule IDs, released old owners, actual confirmed exact exit sink, original Simple output. |
| JIRBidiSetupBudgetTest (1) | A positive budget expires during rule selection; actual first driver timeout receives no renewed time. |
| BidiEntryHookTest (6) | Optional/null paths, both states/statuses, Simple/CWE filter, empty incomplete report, gather/run/factory Exception/Error/OOM. Scripted reports validate wiring only. |
| JIRTaintBidiEntryTest (2) | Actual optional constructor, shared cached graph, legacy null control, actual confirmed output, and an unfinished real Job blocking gather/run. |
| BidiPrescanSetupGuardTest (1) | Six graph/manager Exception/Error/OOM cells produce empty EXCEPTION and no hook calls or factory retry on close. |

The API RED logs fail at missing entry and hook APIs. The first 15 focused
tests pass after implementation. Review then records the one-budget and
prescan setup corrections before adding two new behavior RED tests. Both
fail at the intended assertions before the correction; all 17 focused tests
then pass. The real-entry test initially used a nonexistent MarkSet.size;
it reads the public owned ids snapshot instead.

## Gate

Run from core:

```sh
./gradlew --continue :opentaint-dataflow-core:opentaint-dataflow:cleanTest :opentaint-dataflow-core:opentaint-dataflow:test :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest :opentaint-dataflow-core:opentaint-jvm-dataflow:test :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin :opentaint-jvm-sast-dataflow:compileTestKotlin :cleanTest :test --console=plain
```

Build succeeds in 3 minutes 37 seconds. Log: /tmp/bidi-j02-final-gate.log. Fresh XML copies are under
/tmp/bidi-j02/final/{opentaint-dataflow,opentaint-jvm-dataflow,application}/xml.
Discovery logs are /tmp/bidi-j02-{common,jvm,app}-discovery.txt.
RED logs and XML remain under /tmp/bidi-j02 and /tmp/bidi-j02-*.log.
git diff --check passes. The complete whole-program acceptance gate is J03;
full trace reconstruction remains phase 5.
