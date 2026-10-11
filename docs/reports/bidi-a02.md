# A02 direct per-path AP model

The direct model in `bidi/ap/Reference.kt` implements location and mark matching,
transfer, normalization, cleaner position and output, counted cuts, mark checks,
start facts, answers, policy, emission, demand restriction, generic reversal and
native record read views. R1 eligibility and structural demand selection are
separate functions. `Primitives.kt` adds cleaners, type-filter conjunction, mark
policies, conjunctive edges and request actions. Request-kind values are in
`bidi/store/RequestStore.kt`; the store itself belongs to A08.

The compiled edge cache stays assigned to A05. The direct model has no trie or
candidate index dependency.

## Tests before implementation

Commands run from `core` with `./gradlew --console=plain`.

The first command selected `PrimitiveValuesTest`. It failed in `compileTestKotlin`
because the new primitives and direct operations did not exist. The log is
`/tmp/bidi-a02-red.log`. Kotlin compile checks also required integer IDs in vararg
test helpers and plain tail names in one diagnostic string.

| Class | Tests | Checks |
|---|---:|---|
| DenotationTest | 10 | Raw locations and relation pairs, exclusions, correlated suffixes and T2 union equality in both directions. |
| ReferenceSelectorsTest | 10 | All pairs of 264 patterns, plus 20 nested-prefix pairs in a deeper universe. |
| ApplyEdgeVectorsTest | 15 | 450 transfer matrix assertions and focused geometry, mark, exclusion and request cases. |
| PrimitiveValuesTest | 5 | Cleaner and joint-rule shapes, filter conjunction, mark policy and request values. |
| ReferenceOperationsTest | 25 | Cleaner rows, F75 request candidates, positive cuts and invalid-limit rejection, mark checks, start, answer, policy, emission and restriction. |
| ReferenceRecordViewTest | 13 | Generic reversal, three F76 native views, distinct side marks, native guards and separate R1/F77 selection. |
| FactShapeTest | 12 | A01 shapes plus positive-limit checks for every mode. |

The bounded denotation oracle reads raw value fields. It calls no production AP
operation, trie or candidate index to compute expected results. Its exhaustive
selector universe has fresh accessors and marks and one extra suffix depth. The
focused nested checks use a depth-four universe. T2 compares full relation unions
in both directions; it does not use pairwise leaf coverage.

Native reversal review found two missing guards. The helper accepted an exact native
premise with nonempty field exclusion through generic fallback, and accepted a
backward native must conclusion with empty exclusion. Three boundary regressions
ran before the fix: two failed and one already passed through a generic guard.
The log is `/tmp/bidi-a02-native-red.log`. The plan was updated before adding explicit
native rejections. Generic `revEdge` keeps its separate contract.

One added test used the concrete-mark partial-cleaning row for an abstract ANY fact.
The full common run caught it. The plan was updated and the expected value was
corrected from the separate abstract all-marks row in `ap.md` §4.7. The reference
operation did not change.

## Resolved specification cases

The user supplied these decisions:

1. Follow the exclusion table. A nonempty continuation to a STAR target must pass
   the shared edge exclusion, including when a must premise has its own exclusion.
   Both tests occur before the mark gate. Tests cover admitted and rejected first
   accessors, later excluded accessors, at/below propagation and request suppression.
2. Limit zero is impossible. All modes and public cut calls require a positive
   limit, including calls on zero or unchanged facts. Cut tests use limit one and
   deeper paths. Existing zero-limit Lean data remains historical, outside the
   current public API domain.

The runtime witness before these decisions confirmed the transfer mismatch and the
previous zero-limit class-prefix result. Its log is `/tmp/bidi-a02/pending-witness.log`;
XML is in `/tmp/bidi-a02/pending-xml`. The earlier sources were restored after that
check. After the decisions, the updated exclusion tests failed in 2 of 15 tests
(`/tmp/bidi-a02-exclusion-red.log`). The positive-limit tests failed in 2 of 37 tests
(`/tmp/bidi-a02-positive-limit-red.log`). The production checks were then added.

`ap.md`, `ap-impl.md`, and `validation-plan.md` now state the same rules. The compiled
child predicate in the proposal also includes the shared-exclusion check for A05.
No Lean change was required: the user resolved the contradictory domain and rules.

## Gate

The final fresh common suite and consumer compile check were:

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

This passed in 28 seconds. The common suite ran 275 tests in 23 classes, with zero
failures, errors or skipped tests. JVM and Go production and test compilation, SAST
compilation, and application compilation passed. The log is `/tmp/bidi-a02-gate.log`.
Fresh common XML is saved in `/tmp/bidi-a02/final-common/xml`. `git diff --check`
passes. S00 had no baseline failures. A02 is complete.
