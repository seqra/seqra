# V1 Phase 1 validation

The Phase 1 audit checks the AP and store public contracts in `ap-impl.md`
§0.2 against `ap.md`, the implementation and named tests. All listed public
operations are implemented. No stub transfer operation remains.

## Public operation coverage

| Public contract | Named tests |
|---|---|
| Canonical accessor/mark values, owned keys, paths, initial guards, premise union and fact factories | FactValuesTest, FactShapeTest, PremiseTest, ManagerOwnershipTest, KindFactoryTest, InternalBaseBoundaryTest. |
| Trie values, metadata, equality, soft interning and cache loss | TrieTest, PathWalkTest, InternerTest, TrieMergeDeltaTest. |
| Exact result keys, merge/delta, T1/T2/T2'/T3/T4 and subsumption | ResultsTest, GroupMergeTest, TaintGroupKeysTest, SubsumptionTest. |
| Edge transfer, generic reversal, mark gates, exclusions and requests | ApplyEdgeVectorsTest, EdgeTreeEquivalenceTest, MarkGateTest, LayerRulesTest, ReferenceOperationsTest, ReversalTest. |
| Both satisfaction overloads, summary application, full concrete combinations and subtraction | SummarySelectorTest, SummaryKindsTest, RecordReadViewsTest. |
| Transient native record views, side marks/E, independent F76 conversions and identity | ReferenceRecordViewTest, RecordReadViewsTest, RecordStoreTest. |
| Clean, type filter, positive field cut, literal checks, mark removal and target construction | CleanerTest, TypeFilterTest, FieldLimitTest, MarkCheckTest. |
| Zero/start/policy, shared abstract emission, exact concrete emission, mark-aware reduction and structural selection | EmissionTest, RestrictionTest, DemandSelectionTest, ReferenceSelectorsTest. |
| Request answers and climbs with the original caller premise | RequestActionTest, RequestStoreTest, StandingJoinTest. |
| Leaves and nearby leaves with full patterns | SummarySelectorTest, KindFactoryTest, EdgeTreeEquivalenceTest, ApCancellationTest. |
| Positional path indexes, canonical initial storage and method/raw/publication deltas | PathTrieTest, InitialFactStoreTest, MethodEdgeStoreTest, RunSummaryStoreTest. |
| Exact caller links, standing requests and immutable demand snapshots | AddedFactStoreTest, RequestStoreTest, DemandStoreTest, StandingJoinTest. |
| Raw native records and read-only run views | RecordStoreTest. |
| Literal/ND standing products, complete key partitioning and retained tuple ownership | KaryJoinTest, ConjunctionStoreTest. |
| Exact source hits, context-free vulnerability keys, preserved support shapes, concurrent lossless unions and support predicates | SourceHitStoreTest, VulnerabilityStoreTest. |

The tests cover the AP/store-local portions of validation items 1–12,
14–15 and 17–18. Named form and JVM lowering cases remain assigned to
V2; subscriptions, calls, scheduling and complete one-run closure remain
assigned to V3; support/report/hand-off/iteration cases remain assigned to
V4; whole JVM analysis acceptance remains assigned to J03 and FINAL.
These are later plan gates, not Phase 1 transfer substitutes.

The current checks retain both user decisions: the STAR-target exclusion
table controls nonempty continuations, and public modes/cuts require a
positive limit. Run 1 uses limit one. Internal remaining budget zero
retains uncounted class accessors.

## Audit corrections and focused evidence

Three read-only reviews mapped the manager/reference operations, tree AP
operations and stores to their tests. The audit found two buffered-iteration
gaps and one REACH callback gap. The plan was updated before each regression
and fix:

- `MethodEdgeStore.edgesAt` lacked a checkpoint between NORMAL and DEMAND
  REACH yields. The new key-specific/all-key regression failed in
  `/tmp/bidi-v1-reach-red.log`. Per-yield checkpoints fix it; all 12 method
  edge tests then pass.
- `leaves` and `leavesNear` buffered patterns and emitted them after
  cancellation. Three regressions failed in `/tmp/bidi-v1-ap-red.log`.
  Per-pattern sequence checkpoints fix both APIs, including REACH and
  multi-mark leaves.
- `Results.flush` lacked a checkpoint between REACH callbacks. Its
  regression failed in `/tmp/bidi-v1-results-red.log`. The REACH loop now
  uses the same per-result checkpoint as trie facts.

Collector coverage also checks equal-content FLOW values with different
field/mark exclusions and normal must values with different exclusions.
They remain separate complete keys, in both arrival orders. Same-key
leaves union without store T2 intersection. Direct cancellation checks
cover both satisfaction overloads, summaries, F76 application, full
combinations and subtraction, with empty cancelled outputs and preserved
resumed annotations.

The final focused check passed 27 tests in three classes
(`/tmp/bidi-v1-focused.log`). No new behavior question or Lean change was
needed.

## Clean Phase 1 gate

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

The clean gate passed in 42 seconds (`/tmp/bidi-v1-final-gate.log`). All
selected `cleanTest` tasks ran.

| Module | Tests | Classes | Failures/errors/skips |
|---|---:|---:|---:|
| Common dataflow | 647 | 61 | 0/0/0 |
| JVM dataflow | 119 | 6 | 0/0/0 |
| Go dataflow | 1,313 | 10 | 0/0/0 |

SAST and application compilation pass. Fresh result directories are the
named modules' `build/test-results/test` directories; XML copies are in
`/tmp/bidi-v1/<module>/xml`. All 499 new common and five new JVM annotated
tests have void JVM returns and occur in their XML. Audit logs are
`/tmp/bidi-v1-common-discovery.txt` and `/tmp/bidi-v1-jvm-discovery.txt`.
`git diff --check` passes. V1 is complete; Phase 2 can use these operations
and stores.
