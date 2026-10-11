# A01 fact values, paths and premise keys

The new common package is `org.opentaint.dataflow.bidi.ap`. This step adds the value
types, thread-safe accessor and mark tables, interned paths, full initial keys,
canonical premise unions, and the manager key factories. Trie factories follow in A03.
`MethodKey` uses the existing `MethodEntryPoint`.

`InitialAp` rejects abstract exact and must shapes, excluded abstract premise marks,
and field exclusions on exact or may tails. Zero has only its canonical empty-path
exact shape. Raw reference values can describe abstract exact geometry outside native
typed facts. A canonical conclusion rejects a demand STAR tail. Run-specific must
checks remain assigned to A07 and E02; no mode argument was added to initial factories.

Mark and field-exclusion sets normalize sorted content and own their arrays. Public
arrays are snapshots. Paths cache exact total and counted lengths. Fields and elements
count; classes do not. Iterative construction and conversion handle the 32,768-cell
test. Accessor values are published before their IDs, with no reader retry loop.

`AccessPathBase` now includes Zero and identity-based CleanerTemporary. Old storage,
serialization, JVM position resolution, JVM and Go call mappers, Spring rule positions,
and trace rendering reject these internal bases. Existing serialization tags remain
unchanged. The field temporary's type and lifetime checks belong to I04 and the stores.

## Tests before implementation

All Gradle commands run from `core` with `./gradlew --console=plain`.

The first common test command used
`:opentaint-dataflow-core:opentaint-dataflow:test --tests 'org.opentaint.dataflow.bidi.*'`.
It failed in `compileTestKotlin`: Zero and CleanerTemporary were unresolved.
The next command with the new shape/value/premise tests failed in the same task:
the `bidi.ap` APIs did not exist. The logs are `/tmp/bidi-a01-red.log` and
`/tmp/bidi-a01-values-red.log`.

After the first implementation, the focused common and JVM tests passed. These checks
also compiled the Go consumer, SAST and application with explicit included-build tasks.
The log is `/tmp/bidi-a01-green.log`. The common classes were FactShapeTest (11),
FactValuesTest (12), PremiseTest (6), InternalBaseBoundaryTest (4), and BidiFixtureTest
(12). JVM JIRInternalBasePositionTest ran 3 tests. No test failed or was skipped.

Review found two manager ownership gaps: a foreign path suffix could create a second
numeric path key, and union's identity shortcut could return a foreign premise.
The plan was updated first. ManagerOwnershipTest reproduced both in 3 failing tests
(`/tmp/bidi-a01-ownership-red.log`). Path suffixes, initial paths and union operands
now check manager ownership before interning or identity shortcuts.

Review also found that `Position.resolveAp(baseAp)` accepted internal bases. The plan
was updated first. JIRInternalBaseResolutionTest reproduced this in 1 of 2 tests
(`/tmp/bidi-a01-resolution-red.log`). The resolver now checks the supplied base before
mapping any position shape. Valid program-base mapping still passes.

No specification behavior decision or Lean change was needed.

## Gate

The full affected common, JVM and Go suites ran with fresh results:

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-go-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :opentaint-dataflow-core:opentaint-go-dataflow:test \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

This command passed in 38 seconds. The common suite ran 196 tests in 17 classes; Go
ran 1,313 tests in 10 classes. All passed with no errors or skipped tests. The JVM
suite ran 117 tests before the final position-overload regression was added.
The log is `/tmp/bidi-a01-gate.log`.

After that guard was added, run the affected JVM gate again:

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The final JVM gate passed in 12 seconds: 119 tests in 6 classes, with no failures,
errors or skipped tests. SAST and application compilation passed. Its log is
`/tmp/bidi-a01-jvm-gate.log`. `git diff --check` passed on the final content.
Fresh XML is under each affected module's `build/test-results/test` directory.
The S00 baseline had no existing failures. This step adds no new observed baseline
regression. The source review checked the canonical keys and shared boundaries.
