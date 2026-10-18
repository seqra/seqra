# Caller-owned subscriptions: runtime validation

Date: 18 October 2026. The
[selected contract](../../spec/run1-caller-owned-subscriptions.md) is
implemented. The [implementation map](../design/bidirectional-caller-owned-subscriptions-implementation-20261018.md)
describes the stores, call stages, request frontiers and counted barriers.
Core checks pass. The application precision gate fails. Candidate project
measurements have not started, so no candidate run iteration is reached,
completed or accepted yet. The run-1 target remains 60 seconds of engine time.

Evidence root:
`/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-20261018/`,
called `P` below. Earlier runtime performance reports are historical and do
not establish acceptance for this implementation.

| Check | Result | Evidence under P |
| --- | --- | --- |
| Full common dataflow suite | 1,604 passed; zero failures, errors or skips | `validation/full-core-7/opentaint-dataflow/` |
| Full JVM dataflow suite | 411 passed; zero failures, errors or skips | `validation/full-core-7/opentaint-jvm-dataflow/` |
| Analyzer consumer jar | Passed | `prebuild/candidate-full-core-7.log` |
| Application bidirectional selection | 148 passed, nine failed, one skipped | `validation/application-1/` |
| Fresh main OWASP workflow control | 4,112 traces; zero trace-generation failures | `projects/OWASP/main/` |

The main control uses the legacy engine. It has no numbered bidirectional
run. Its successful location comparison with the new candidate still needs
the candidate execution.

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

## Remaining acceptance work

The application failure packet retains each strict expected/actual assertion.
The failures concern cleaner precision. Their source and membership analysis
must distinguish missing expected findings from extra findings; a passing
test count alone does not prove no false negatives.

After the implementation commit, run OWASP, conductor, ThingsBoard and
Stirling-PDF through `runProjectAnalyzer` with the task's fixed memory and
timeout settings. Bind the source and input manifests. Compare actual rule
and location identities with fresh main results, measure each numbered
iteration, review CPU profiles and analyze any MemoryManager OOM capture.
The performance report and the next proposal must use those new results.
