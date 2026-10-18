# Caller-owned subscriptions: runtime validation

Date: 18 October 2026. The
[selected contract](../../spec/run1-caller-owned-subscriptions.md) is
implemented. The [implementation map](../design/bidirectional-caller-owned-subscriptions-implementation-20261018.md)
describes the stores, call stages, request frontiers and counted barriers.
Core checks pass after the committed mixed-batch correction.
Measured runtime: `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**, conductor **run-1 / none / none**, ThingsBoard
**run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**.
OWASP and ThingsBoard timed out; conductor and PDF stopped with OOM.
All four miss the 60-second run-1 engine target. The fixed matrix and
80-check/41-dataset input audit are closed. The
[current performance report](bidirectional-caller-owned-subscriptions-performance-20261018.md)
records strict finding differences, CPU evidence and memory analysis.
The full core gate passes, but application precision and project no-loss
acceptance fail. These measurements do not prove a semantic transfer loss
or a universal saving. IR and cache/reference-manager rewrites remain outside
implementation scope.

Evidence root:
`/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-20261018/`,
called `P` in the validation table below. Corrected project evidence is in
`/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-retest-20261018/`.
The report explicitly reuses the unchanged fresh main controls with their
original paths and timestamps. Earlier candidate measurements are superseded.

| Check | Result | Evidence under P |
| --- | --- | --- |
| Full common dataflow suite | 1,607 passed; zero failures, errors or skips | `validation/full-core-8/opentaint-dataflow/` |
| Full JVM dataflow suite | 411 passed; zero failures, errors or skips | `validation/full-core-8/opentaint-jvm-dataflow/` |
| Analyzer consumer jar | Passed | `prebuild/candidate-full-core-8.log` |
| Corrected-source application selection | 148 passed, nine failed, one skipped | `validation/application-2/` |
| Fresh main OWASP workflow control | 4,112 traces; zero trace-generation failures | `projects/OWASP/main/` |

The main control uses the legacy engine. It has no numbered bidirectional
run. The corrected candidate preserves all 4,112 strict main OWASP identities,
with 5,067 extra occurrences. That does not establish project-wide acceptance.

The full core command uses Temurin JDK 17:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :projectAnalyzerJar -x :opentaint-ir:go:buildGoServer \
  --continue --offline --max-workers=2 --console=plain
```

The application command uses the same JDK:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew :test --tests '*Bidi*' \
  -x :opentaint-ir:go:buildGoServer \
  --offline --max-workers=2 --console=plain
```

The accepted core receipt includes production source hashes and the consumer
jar hash. Intermediate failed checks remain in `prebuild/` and
`validation/`. They are not accepted gates. In particular, the broad first
check exposed a backward ZERO stage-resume error. The final checks preserve
the reversed stage and the existing restricted-run resume behavior.

The first conductor project run exposed a mixed Base/static request batch
at the nonstatic concrete-answer helper. Its whole-batch Base assertion
failed before unrelated static rows could be ignored. The focused red
check ran seven tests against unchanged production code and reproduced the
same assertion in the direct oracle and input-first integration case.
The opposite arrival-order control passed. That evidence is in
`validation/mixed-request-red-1/`.

The correction filters matching Base rows inside the concrete visitor.
The full green gate includes all three new regressions. A batch with actual
`b/{T,U}`, Base interest `b/{T,V}`, and static interest `C.f/{U}` can start
only `b/T`; the static U request still drives its productive FLOW join.
Both P/A arrival orders, late inputs, eager static admission and legal
EXACT/STAR tags remain covered. No request storage or initial family was
added.

## Checked runtime boundaries

The common suite includes actual source/view registration in both orders,
snapshot/delta overlap, late subscribers, full context separation, unknown
call fallback and fixed-SCC recursion. Actual runner controls check child
delivery before completion, partial Work, concurrent units, cancellation,
rejected delivery, joined cleanup and run-2 service absence.

Callsite controls check both P/A frontiers, actual mark evidence, full stages,
mixed ND member layers, packed field associations and same-field relay
coverage. Summary callbacks capture all candidate values before a callback
can change an input directory. The method-empty drain merges transient
effects from pending sites of one caller. Separate caller owners keep their
separate barriers.

Descriptor controls retain the nine initial families, STAR versus ANY, class
coordinates and actual input presence with no legal initial. Existing field
write, cleaner, source/sink NNF, nonreturning sink, multi-run and generated
differential controls remain in the gate. Static relay request rows are
private transport state; the concrete-static differential retains exact
Base request endpoints and all fact, summary and semantic observation checks.

The [proof dossier](../../spec/run1-caller-owned-subscriptions-proofs.md)
proves finite default-summary coverage. It does not prove the Kotlin/JVM
scheduler, arbitrary frontend models or project completion. Runtime test
passes do not make the failed application precision gate a pass.

## Acceptance results and limits

The fresh application failure packet retains all strict assertions. Eight
rule-set failures have 356 expected and 382 actual memberships: zero missing
and 26 extra. The ninth count-only failure gives one finding without an
identity. These membership sets do not establish strict location or occurrence
equality. Each failed case's own log reaches, completes and accepts **run-9**.
This is application-test scope, separate from the project iterations above.

The source review identifies a matching EXACT/BELOW cleaner residual mechanism.
It is not a captured per-finding causal trace or an isolated parent-commit
comparison. Expected findings, assertions and filters were not weakened.

All four corrected projects ran through direct `runProjectAnalyzer` at the
prescribed settings. Both source endpoints and all 41 dataset fingerprints
are stable. The strict project comparison has 22 missing main identities:
seven conductor and 13 ThingsBoard findings after interrupted run-1 with no
accepted report, plus two PDF location changes. PDF adds 49 unconfirmed
identities. OWASP's identity set is unchanged but output multiplicity differs.
The current performance report retains these failed gates and source-qualified
causes; it does not waive them using finite proofs or core test passes.

No additional runtime optimization is included. The next proposal must use
the new workload, profiles and heap owners and remain a separate design.
