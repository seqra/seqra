# Static need replay batching: runtime validation

Date: 17 October 2026. The selected
[batching contract](../../spec/run1-static-need-replay-batching.md) is implemented.
The [implementation map](../design/bidirectional-static-need-replay-batching-implementation-20261017.md)
describes the owner barrier, exact sharing and optional measurements.
Core checks pass. The application selection has nine strict precision failures;
its acceptance gate is not green. Fresh fixed-configuration project runs
are closed; their resource and location gates are not passed. See the
[project measurements](bidirectional-static-need-replay-batching-20261017.md).

## Executed checks

Evidence root, called `P` below:
`/drive-testcomp/opentaint-go-rules/static-need-replay-batching-20261017/`.
Each Gradle receipt records the command, revision, UTC interval, exit code,
and complete core Kotlin/Java/build source maps before and after execution.
All completed checks below have equal source maps.

| Check | Result | Evidence under P |
| --- | --- | --- |
| Focused request, scalar, scheduling and static integration selection | 77 passed; no failures, errors or skips | `validation/focused-1` |
| Full common dataflow suite | 1,585 passed; no failures, errors or skips | `validation/full-core-1` |
| Full JVM dataflow suite | 411 passed; no failures, errors or skips | `validation/full-core-1` |
| Analyzer consumer jar | Passed | `validation/full-core-1/console.log` |
| Final batching and actual-runner controls | 19 passed; no failures, errors or skips | `validation/final-batching-1` |
| Application bidirectional selection | 148 passed, 9 failed, 1 skipped; no errors | `validation/application-1` |
| Lean build and focused audit | 303 declarations, 28 controls, no unexpected axioms | `validation/formal-1` |

Two test-only additions followed the full core check: a source committed after
a full-source snapshot, and an actual run-1 to run-2 handoff. The final 19-case
selection includes both. `validation/final-source-refinement.json` proves
that only those two test files changed after the full check; all production
sources are identical. The two commands cover 1,587 distinct common tests.
No assertion, expected finding, skip or test filter was removed to pass them.

Eight application failures expose full expected/actual rule-membership counters.
They have zero missing expected memberships and 26 extra memberships. The ninth
expects no findings for a concrete two-level cleaner and observes one. Its
message does not give the actual finding's rule or location. These are strict
precision failures; neither a count comparison nor a passing core suite proves
whole-project finding equality. All 13 sequence acceptance cases pass. The
fresh application failure analysis under `validation/` retains these limits.

The common/JVM/consumer command uses Temurin 17.0.18:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :projectAnalyzerJar -x :opentaint-ir:go:buildGoServer \
  --continue --offline --max-workers=2 --console=plain
```

The application selection uses the same JDK:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew :test --tests '*Bidi*' \
  -x :opentaint-ir:go:buildGoServer \
  --offline --max-workers=2 --console=plain
```

## Refinement evidence

The scalar oracle stores source/mark membership and a separate adjacency list.
It executes an independent per-source BFS. Actual routing uses the production
subscription directories, exact grouping and queued receivers. The comparison
checks complete method/base/mark candidates before committed-R subtraction.
It also checks R filtering without equating raw deliveries to logical needs.

Controls check equal contents in separate objects, unequal hash collisions,
overlapping mark sets, same-turn source unions, cumulative versus delta
selection, duplicate binding admission, cycles, a late edge during an active
wave, full subscriber fan-out, and both source/binding arrival orders.
The new snapshot control commits another owner's source from the dispatch
callback after selection. It checks snapshot exclusion, the surviving delta,
and the exact later endpoint union.

Actual `UnitRunner` controls check incomplete Work before requeue, the final
Work and non-Work turns, concurrent owners, rejected children and cancellation.
The run-2 control reuses the same shared objects, observes the run-1 flush
before joined cleanup, then executes partial run-2/L2 Work with no run-1
router or pending accumulator. Both complete with zero reservations.

The deterministic scheduling fixture assigns an owner to each event, Work
quantum and deferred callback. It flushes only that owner. Existing request,
bootstrap, cleaner, concrete-mark, source/sink, ND and scalar semantic checks
retain their assertions. Deferred notification telemetry keeps its owner
until its callback and posted receivers finish.

The [proof dossier](../../spec/run1-static-need-replay-batching-proofs.md)
states the finite equivalence and cost laws. The new validation reruns the
same model; no new axiom or runtime transfer rule is introduced. These models
and finite tests do not prove every concurrent schedule or project termination.

## Measurement state

The fresh study measures committed runtime
`f18b1c5bb58e5856e4b15b3a71e09e3708dff0d1` against fresh main
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. All eight invocations are terminal
with positive source stability. Full before/after input audits pass 80 checks
over 41 byte-identical dataset manifests. The project report gives exact
engine/barrier timings, locations, profile windows and heap interpretation.

| Project | Latest reached / completed / accepted forward | Run-1 engine | Terminal status |
| --- | --- | --- | --- |
| OWASP | run-7 FORWARD / run-6 BACKWARD / run-5 | 268.372 s, COMPLETE | TIMEOUT |
| conductor | run-1 FORWARD / none / none | 774.693 s, incomplete | OOM |
| ThingsBoard | run-1 FORWARD / none / none | 193.875 s, incomplete | OOM |
| Stirling-PDF | run-2 BACKWARD / run-1 FORWARD / run-1 | 98.155 s, COMPLETE | OOM in run-2 |

No project meets the 60-second run-1 engine target. Main succeeds on all
four projects; it has no bidirectional run-N iteration. Available reports
and failed prefixes do not establish complete-project semantic equivalence.
Core/model passes do not convert these resource, location or occurrence
limits into acceptance. Previous-runtime reports remain historical.

The previous raw captures were cleaned before this iteration: 49 files,
99,041,937,718 logical bytes. The exact receipt is
`preparation/previous-raw-cleanup-20261017T221453.604290Z.json` under `P`.
Compact reports and unrelated user files were preserved.
