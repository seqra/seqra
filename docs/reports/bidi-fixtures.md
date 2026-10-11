# S00 test fixtures

The fixture source is
[`BidiFixtures.kt`](../../core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow/bidi/fixtures/BidiFixtures.kt).
The fixture checks are in
[`BidiFixtureTest.kt`](../../core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow/bidi/fixtures/BidiFixtureTest.kt).
Both files use the common test root required by [plan.md](../../spec/plan.md).

The raw values have no production AP dependency. Later tests must adapt them to the public AP and form
interfaces. These fixture checks prove that the test data retain their keys. They do not prove a production
operation, cache, selector or analyzer result.

## Observations

A raw fact keeps its base, full accessor path, tail, field exclusion, mark, mark exclusion and layer.
`FixtureResult` compares a multiset of facts and a separate set of requests. Duplicate output facts remain
visible. A request keeps its method key, incoming premise and mark or static position.

A method key keeps the method signature, context and forward entry. A caller keeps the premise set, caller
layer and call statement. A link keeps its callee key, caller, link layer and full added fact. The caller
layer can differ from the link and added-fact layers.

The stable form fixture supplies one object per slot and direction. Statements and exit forms are per
method. Calls and entry forms are per method key. Call sites and rule alternatives have separate identities.
This follows the lifetime split in [ap-impl.md](../../spec/ap-impl.md) §23.7 and §31.2. The harness cache only
provides stable inputs; production cache tests must check the production cache separately.

The accessor and mark sets include fresh values. Later denotation tests can use these values to expose an
omitted exclusion cell. Raw operation tests must compare full identity. Store tests must compare the union
of decoded locations and marks in both directions, with premise and layer keys. Mutual single-leaf coverage
does not prove the T2 union witness.

## Current expectations for historical vectors

Use the rule in [ap.md](../../spec/ap.md), not an old vector's selector or closure version. Keep the historical
label when adapting a vector. The limits in [proof-status.md](../../spec/proof-status.md) still apply.

| Historical data or module | Required current expectation | Fixture or later owner |
|---|---|---|
| `W6`, base `AFact.kind.any`, and older any-tail examples | In a forward normal TAINT conclusion, any is MUST (`ANY_TAINT`) with E. A DEMAND result is MAY (`ANY`) with Empty E. Backward has no MUST. Keep STAR/E separate from any. | `must`, `may`, `mustDemotion`; A01–A07 |
| F70/F71 concrete restricted emission and reduction (`emitM`, `restrictS/U`, `DR/DRX`) | F72 abstract demands emit a shared FLOW premise. Reduction checks the whole premise in both locations and marks, then the demanded exit and mark. Keep the input layer and AP §6.4 representability exceptions. Old concrete closure results do not prove these current operations. | A02, A05, A07 |
| `ReviewRootCleaner`, old spatial abstract selected-mark cleaner | F75 splits every same-base abstract fact before spatial tests. Add T to X, keep geometry/E/layer, and request T in run 1 even when already excluded. Restricted runs make no request. Different bases pass. | `sameBaseCleanerExpected`, `alreadyExcludedCleanerExpected`; A06 |
| `CleanerLowering`, `FieldCleanerX`, `ReviewFieldCleaner`; atomic `clean(x.f,T)` examples | F74 named field cleaning is READ → temporary-root CLEAN → strong WRITE-BACK. The outside-field keep branch stays normal and retains its marks. Reverse action order backward. An atomic field-position primitive cannot replace this action. | I04; current fixtures retain raw accessors and stable form slots |
| Generic `revRec` and old blocked must-reader examples | F76 uses a transient concrete STAR/E read premise. Preserve the native conclusion E there. An exact target drops the incoming suffix and can return a distinct upstream mark. A must-premise view can return caller DEMAND. Never start, emit or persist the view. | `exactToMustRecord`, `nativeReadPremise`, `nativeReadExpected`; A05, A09, E05 |
| `CurrentMustHandoff.normal_any_selection`, `selectAny_equiv`, `CurrentHandoff.shapeSeq`, old `Cross` selector and alternating hand-offs | F77 selects raw DEMAND summaries, normal multi-premise summaries and normal backward zero-premise summaries. A native normal singleton is a record, with no callee demand, regardless of tail or read-view output layer. Persist R1 raw summaries separately. Historical selector sequences do not prove F77. | A07–A09, D01–D02 |
| Old selected-must hand-off copies | Convert each selected forward MUST piece to MAY with Empty E. An exact narrowed piece stays exact. Normalize abstract mark exclusions to `*` on both sides; retain STAR field exclusions. Preserve method keys. | `selectedMustHandOff`, `normalizedFlowHandOff`; D01–D02 |
| Conjunction vectors that force a DEMAND result or treat a reversed branch as a sufficient forward summary | Join accepted literal layers in forward mode. A fully normal conjunction stays NORMAL when selected. The forward hand-off makes one reversed demand per member. Backward branches are independent DEMAND results; one branch does not establish full forward support. | `normalConjunction`, `conjunctionHandOff`, `reversedConjunctionBranches`; E04, D01–D04 |
| Backward demand `jb → zero` and old backward-zero hand-offs | A selected demand becomes `(zero,jb)`. A normal nonzero singleton is a backward record instead. A selected zero-premise conclusion becomes `(gb,none)`. The implicit `(zero,none)` publishes nothing in a restricted forward run. | D01–D04 |
| Earlier must-record output timing examples | Keep pending output E through post-result actions and mappings. Then demote applicable-only output before the CALL cut. The reader output layer does not change callee-demand selection. | E05, I06 |

The current raw fixtures are data, not copies of historical theorem statements. The F76 vector uses distinct
input/output marks and a nonempty E. The hand-off data preserve normal conjunction layer and method context.
The F75 data include the repeated-request case.
The selected must data use a normal multi-premise summary, as required by F77.
The annotated STAR normalization data test an arbitrary pattern, without a native-summary selection claim.

## Checks

Run from `core` after the S00 baseline build has finished:

```bash
./gradlew :opentaint-dataflow-core:opentaint-dataflow:test \
  --tests 'org.opentaint.dataflow.bidi.fixtures.BidiFixtureTest'
```

This filter must discover `BidiFixtureTest`. Inspect its fresh result at
`core/opentaint-dataflow-core/opentaint-dataflow/build/test-results/test/TEST-org.opentaint.dataflow.bidi.fixtures.BidiFixtureTest.xml`.
The expected count is 12 tests. Do not infer success from old XML or a cached unrelated suite.

Result: PASS on 2026-10-11. The task compiled both fixture files and executed all 12 tests.
The fresh XML timestamp is `2026-10-11T09:10:14` UTC: 12 tests, 0 failures, 0 errors,
0 skipped. Gradle reported `BUILD SUCCESSFUL in 1s`. The baseline worker saved the prior
common-module XML before this focused run. `git diff --check` passed, and all five local
report links resolve.
