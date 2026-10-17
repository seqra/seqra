# Run-1 concrete static propagation: runtime validation

Date: 17 October 2026. Scope: the selected
[concrete-static contract](../../spec/run1-bottom-up-concrete-static-propagation.md).
Implementation is complete. Project measurements follow these checks.
IR storage and reference/cache managers are unchanged.

The [implementation map](../design/bidirectional-bottom-up-concrete-static-implementation-20261017.md)
describes actual admission, static need routing and concrete carrier transport.
The [proof dossier](../../spec/run1-bottom-up-concrete-static-proofs.md) states
the constructive model and the runtime refinement limits.

## Executed checks

Evidence is under
`/drive-testcomp/opentaint-go-rules/concrete-static-20261017/`.
Each bound Gradle run records its command, environment, UTC interval, exit code
and all core Kotlin/build source hashes before and after. The final core and
application checks both have equal before/after maps.

| Check | Result | Evidence |
| --- | --- | --- |
| Common dataflow suite | 1,567 passed; no failures, errors or skips | `full-core-2/opentaint-dataflow-xml` |
| JVM dataflow suite | 411 passed; no failures, errors or skips | `full-core-2/opentaint-jvm-dataflow-xml` |
| Analyzer consumer jar | Passed | `full-core-2/console.log` |
| Application bidirectional selection | 148 passed, 9 strict precision failures, 1 skip; no errors | `application-1/xml` |
| Concrete integration selection after combined bootstrap control | 10 passed; no failures or skips | `alternating-bootstrap-1/xml` |
| Constructive models | Build and selected audit passed; 1,050 declarations, no unexpected axioms, 46 executable controls | `lean-build.log`, `lean-selected-audit.log` |

All Gradle checks use Temurin 17.0.18, offline dependencies and two workers.
The core command is:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :projectAnalyzerJar -x :opentaint-ir:go:buildGoServer \
  --continue --offline --max-workers=2 --console=plain
```

The application command is:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew :test --tests '*Bidi*' \
  -x :opentaint-ir:go:buildGoServer \
  --offline --max-workers=2 --console=plain
```

The application gate is not green. Eight failure messages expose complete
expected/actual rule-membership counters: there are no missing expected
memberships and 26 extra memberships. Those counters equal the prior executed
selection. The ninth is the concrete two-level cleaner case: zero expected
findings and one observed finding. Its message does not give the finding's
full identity. Do not claim identity equality from that count. The stream skip
is unchanged. `application-1/failure-analysis.json` preserves this distinction.
This diagnostic comparison does not replace fresh project comparisons with main.

Earlier runs and their failures remain archived. The first broad selection
exposed 34 old-contract assertions. The first full run exposed five common and
five JVM failures. These checks were changed only where the selected contract
requires eager static admission, source-once needs, empty inherited mark plans,
or static DEMAND. The final suites retain geometry, supplied-mark, event,
support, rule and layer assertions; no production rule was removed to pass them.

## Refinement coverage and limits

The independent scalar reference applies the new admission and DEMAND policy
before evaluation. It retains expanded request propagation as its oracle.
The optimized runner decodes descriptors only in tests and compares all nine
semantic observation components, including actual initials, Base endpoints,
source needs, summaries, source hits and sink witnesses. Intermediate expanded
static request rows and batch-dependent callback counts are not observations.

The final added control combines a static write, a static read into a callee
argument, ordinary Base request propagation, reentry into the static route,
and the original W-to-Base boundary. It checks both delivery orders and the
absence of a hit without actual T. Only this test file changed after the full
core/application checks; the ten-case integration selection was rerun with
equal before/after source maps. Production sources are unchanged from those
full checks.

Controls cover the W-to-Base bootstrap with and without actual T, both arrival
orders, cross-field originals, late graph growth, recursion, ignored E/mx for
interest, ordinary Base reentry, concrete/ZERO non-routes, all actual marks,
EXACT/STAR separation, class components, strong kills, mark conversion, whole
ND support and separate call ports. A call-stage control checks the shared
identity flag after a plain source keep. The descriptor rejects nonempty must
payloads rather than decoding them as STAR. A STAR-to-ANY exit remains an
ordinary effect even in a read-free returning method.

These checks cover selected adapters, not every concurrent schedule or every
frontend. The ordinary ND return adapter retains the existing JVM scope of
one ordinary callee stage per call plan. The checked models do not prove the
whole Kotlin engine, frontend contributor completeness or arbitrary analyzer
termination. All-source replay on graph growth remains the specified baseline
and a possible performance risk. Ordinary R/P counters exclude static source
unions and routing waves; they cannot measure total request work on their own.

## Project measurement state

No project workload has run for this implementation yet. Latest reached,
completed and accepted forward iterations are therefore **not available** for
each of OWASP, conductor, ThingsBoard and Stirling-PDF on the new runtime.
Previous-commit project reports are not acceptance evidence for this change.

The previous iteration's raw snapshots, JFR/native profiles and reproducible
MAT indexes were removed before new measurements: 52 files, 96,545,069,853 bytes.
Compact reports and unrelated files were kept. The exact deletion list is in
`previous-capture-cleanup.json` in the evidence directory.
