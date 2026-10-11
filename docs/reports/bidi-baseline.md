# Bidirectional implementation baseline

This report records the S00 checks from 2026-10-11. It separates existing test failures
from changes made for the new analyzer. It does not prove the new analyzer behavior.

## Starting state

- Commit: `601737d6be69f41fdf5bf05efd3141d288872947` (`impl task`).
- Branch: `saloed/bidirectional`.
- The tracked worktree was clean before the S00 work.
- These untracked directories already existed: `docs/reports/`, `formal/`, `run-debug/`,
  and `run-debug-2/`. The existing file `docs/reports/exploding-methods.md` was not changed.
- There is no applicable `AGENTS.md` file in this worktree or its parent directory.
- `task.md` requires STE100, the specification, one commit per completed plan step,
  and subagents. This baseline task does not change production code.

## Build environment

Run the commands below from `core`. This is a composite Gradle build.

| Item | Observed value |
|---|---|
| Wrapper | Gradle 8.10, distribution already available |
| System Gradle | 9.4.1; not used for these checks |
| Java used by the wrapper | Temurin 17.0.18 |
| Other installed Java versions | 8, 11, 21, and 25 |
| Common source target | JVM 1.8 |
| Application source target | JVM 17 |

Use included-build task paths. The dataflow included build has no separate wrapper.
Do not use `--dry-run` as proof that no task ran: the composite build can execute included
tasks with that option. A clean test task removes only that module's previous results.

## Reference branches and reuse files

| Reference | Commit | State |
|---|---|---|
| `origin/saloed/backward-main` | `b25397260ca1c978732ca3e89679eb5ed1c1f177` | Available as a remote-tracking ref. No local branch with this name. |
| `saloed/any-field-limit` | `85b7c614b936988e284e31102a8d6089f4533178` | Local branch and `origin` ref both available. |

The backward branch contains `JIRBackwardExitWiringGraph.kt` and `BackwardGraphs.kt`.
The graph computes the statements that can reach an exit. It adds the exceptional exit
as a successor of each remaining statement. The proposal requires a cache per method.

The field-limit branch contains `FieldLimiter` in `AccessTree.kt`. It counts field and
element accessors. It retains uncounted edges, checks cancellation, caches results by
node and budget, and collapses counted edges when the budget reaches zero. Adapt it to
the current `FieldLimitCut` contract; its old representation is not the new fact model.

The current reuse files are present. The check read `Accessors.kt`, `AccessorInterner.kt`,
`AccessPath.kt`, `AccessTree.kt`, `AccessTreeSoftInterner.kt`, `AccessBasedStorage.kt`,
`MethodAnalyzerEdges.kt`, and `Cancellation.kt`. Field, element and class values can be
reused. Existing paths also contain mark and tail accessors; the new paths must contain
only field, element and class accessors. Existing tree algorithms provide merge, delta,
filter, prepend, interning and path-index patterns. The old engine files remain available
for the prescan and the old analysis path.

## Fresh checks

All counts below come from the named module's XML files. No recursive stale-result count
is used. Each test command starts with its corresponding `cleanTest` task.
The [test inventory](bidi-baseline-tests.tsv) records the full class names, counts and
timestamps for these checks. Copies of the XML files are at `/tmp/bidi-s00/<module>/xml`.
The inventory keeps the observations available when a later test task replaces the XML.

### Common and JVM dataflow

Command:

```bash
./gradlew --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test
```

Result: exit 0, `BUILD SUCCESSFUL in 34s`. Both `cleanTest` tasks ran.

| Module | Tests | Failures | Errors | Skipped | XML timestamps (UTC) |
|---|---:|---:|---:|---:|---|
| `opentaint-dataflow` | 148 | 0 | 0 | 0 | 09:05:35–09:06:00 |
| `opentaint-jvm-dataflow` | 114 | 0 | 0 | 0 | 09:05:32–09:05:35 |

Fresh result directories:

- `core/opentaint-dataflow-core/opentaint-dataflow/build/test-results/test`
- `core/opentaint-dataflow-core/opentaint-jvm-dataflow/build/test-results/test`

Common classes: `DeepAccessorExclusionTest`, `FactCleanerContractTest`, `AccessGraphTest`,
`AutomataAccessTest`, `AutomataInitialFactAbstractionTest`, `GraphIndexTest`,
`CactusAccessTest`, `AnyFieldAccessorExclusionTest`, `TreeInitialFactAbstractionTest`,
`AccessorInternerTest`, and `MethodTraceSearchTest`.

JVM classes: `AliasSampleTest`, `DSUAliasAnalysisInvalidateOuterHeapAliasesTest`,
`DSUAliasAnalysisStateTest`, and `JIRStatementSummaryTest`.

### SAST and application

Command:

```bash
./gradlew --console=plain --continue \
  :opentaint-jvm-sast-dataflow:cleanTest :opentaint-jvm-sast-dataflow:test \
  cleanTest test
```

Result: exit 0, `BUILD SUCCESSFUL in 2m 30s`. Log: `/tmp/bidi-s00-sast-app.log`.
The SAST, application, Java query and Go query `cleanTest` tasks all ran. The unqualified
root `test` task also runs root subproject test tasks.

| Module | Tests | Failures | Errors | Skipped | Classes | XML timestamps (UTC) |
|---|---:|---:|---:|---:|---:|---|
| Application | 770 | 0 | 0 | 9 | 71 | 09:06:44–09:08:30 |
| Java query language, full suite | 207 | 0 | 0 | 23 | 15 | 09:06:17–09:06:41 |
| Go query language, full suite | 762 | 0 | 0 | 4 | 12 | 09:08:31–09:08:40 |

Fresh result directories:

- `core/build/test-results/test`
- `core/opentaint-java-querylang/build/test-results/test`
- `core/opentaint-go-querylang/build/test-results/test`

The SAST `test` task reports `NO-SOURCE`. There were no SAST test sources at the start.
Its `compileKotlin` task passes as `UP-TO-DATE`. This result proves build task discovery
and a valid existing build output. It supplies no SAST test coverage. There is no SAST XML
result directory. The new phase-3 integration must add its own SAST tests.

### Java query language

Command:

```bash
./gradlew :opentaint-java-querylang:cleanTest :opentaint-java-querylang:test \
  --tests 'org.opentaint.semgrep.ExampleTest'
```

The executed command also used `--console=plain`. Result: exit 0,
`BUILD SUCCESSFUL in 8s`. Log: `/tmp/bidi-s00-example.log`.
Fresh XML: `core/opentaint-java-querylang/build/test-results/test/TEST-org.opentaint.semgrep.ExampleTest.xml`.
It contains 63 tests, 0 failures, 0 errors and 2 skipped tests. Its timestamp is
09:09:00 UTC. This separate check replaced the full-suite XML. The full-suite observation
remains in the inventory and in `/tmp/bidi-s00/java-querylang-full/xml`.

### Go shared consumer

Commands:

```bash
./gradlew :opentaint-dataflow-core:opentaint-go-dataflow:compileKotlin
./gradlew :opentaint-dataflow-core:opentaint-go-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-go-dataflow:test
```

The executed commands also used `--console=plain`. The compile command exits 0:
`BUILD SUCCESSFUL in 1s`; `compileKotlin` reports `UP-TO-DATE`.
Log: `/tmp/bidi-s00-go-compile.log`.

The clean test command exits 0: `BUILD SUCCESSFUL in 3s`. Its `cleanTest` task ran.
Log: `/tmp/bidi-s00-go-tests.log`. Fresh results are in
`core/opentaint-dataflow-core/opentaint-go-dataflow/build/test-results/test`.
They contain 1313 tests, 0 failures, 0 errors and 0 skipped tests in 10 classes.
The XML timestamps are 09:09:48–09:09:49 UTC.

## Baseline failures

All requested baseline commands finished. The existing common, JVM, application, Java query,
Go query and Go dataflow suites have no baseline failures or errors. There are no environment
or setup failures. SAST has no existing test sources. Skipped tests remain visible in the
per-module counts and inventory; they are not successful test executions.

No feature implementation was used to obtain these results. The old engine remains available.
No behavior question arose in these build checks, so no Lean clarification was needed.
S00 can use this report for its baseline gate when the separate fixture checks also pass.
