# Final phase-1–3 implementation review

Every plan step is complete. The clean closing gate runs 3,909 tests:
3,897 pass and 12 specified tests are skipped. There are no failures or
errors. The result contains 1,226 common, 361 JVM, 1,313 Go, 941 application
and 68 selected query tests. The application skips are nine legacy cases
and the inherited nested-collection case. Query retains two existing skips.
JVM SAST test sources compile.

## Current behavior and scope

The implementation follows the normative tables, including the user's
exclusion decision. Run 1 starts at limit 1; public modes/configurations and
cut operations reject nonpositive limits. Later limits do not decrease.
Historical zero-limit proof data remains outside the current API domain.

The final review checks current F74 field-action lowering and temporary
lifetime, F75 run-1 requests and selected-mark split, F76 transient/native
record separation and late demotion, F77 structural callee demand, normal
forward conjunctions and independent backward branches. It also checks
P1–P6/Q1–Q4, complete replay/delivery indexes, same-call support, alias and
field-cut stages, source hooks, owned inputs, joined barriers, stop order,
report status and released owners. No new concrete defect or missing
enabled requirement was found. The prior step reports contain the bounded
annotation, finite closure, schedule and acceptance evidence.

The public JVM analyzer has an optional bidi policy. Null retains the
legacy path. Phase 3 returns both CONFIRMED and DEMAND entries with Simple
traces. The remaining budget includes prescan and entry setup. Setup
Throwables return empty output and EXCEPTION; incomplete first runs return
empty reports with their actual cause. Successful joined prescan state is
copied and released before fresh bidi owners run.

The 13 actual JVM sequences, 135 inherited bidi cases, two generated Spring
cases and five opt-in query cases pass. Legacy application/query controls
also pass. EXACT must cleaning retains its specified DEMAND residual;
subtree cleaning and unrelated-mark controls remain enabled. The Java
Object array alternatives are pinned separately from the two normal native
factory return leaves. Entry marks are removed from zero-premise argument
facts at root and nonempty depth; returned taint remains as specified.

Phase 4 retains full project benchmarks. Phase 5 retains full trace
construction and span/path parity. The specified disabled exception,
known alias and nested-collection cases remain outside the enabled gate.
The existing end-fact reach gap remains specified. These tests do not prove
unbounded current-iteration properties; F72/F75/F76 proof obligations keep
their existing scope. No Lean artifact or proof-status file changed in
this closing work, so no new Lean result is claimed.

## Final proposal alignment

The two proposals now reflect the tested owners:

- Canonical arrays and premise/path values own their backing data and
  return public copies. Canonical factories enforce manager ownership.
- Demand snapshots own paths/indexes and enforce typed tail/E guards.
- In-flight rejected sends roll back reservations; releases reject
  underflow and call the zero callback outside the bookkeeping lock.
- The driver reads the first limit once and caps policy timeouts after
  callback time is charged. It uses required actual handler factories.
- Prescan and entry setup factories are guarded, successful join precedes
  copying, old owners release before bidi, entry setup consumes the shared
  budget, and close does not force a failed/unused lazy engine.

The runtime constructor keeps its public seven-argument API. Its internal
factory seam supports J03 observations while the default uses the actual
IterationDriver. The proposal shows that same public default behavior.

## Contract owners

Common paths below are relative to
core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi.
JVM paths are relative to the matching opentaint-jvm-dataflow source tree.
Test names identify actual contract owners; proposal test rows can span
several classes. Proposal names MethodEdgeStore/AddedFactStore/RunSummaryStore
map to MethodEdges/AddedFacts/RunSummaries. EventDispatch is implemented in
RunMethodHandlers and MethodServices.

| Contract | Actual owner | Existing contract tests |
| --- | --- | --- |
| Canonical values, paths, premises, zero, manager ownership, three Facts kinds | `ap/Facts.kt`, `Premise.kt`, `PathNode.kt`, `Conclusions.kt`, `ApManager.kt` | `FactValuesTest`, `FactShapeTest`, `PremiseTest`, `ManagerOwnershipTest`, `KindFactoryTest` |
| Generic trie operations, independent E/mx merge rules, subsumption and normalized destination keys | `ap/TrieOps.kt`, `TrieInterner.kt`, `Groups.kt`, `ApOut.kt` | `TrieTest`, `TrieMergeDeltaTest`, `GroupMergeTest`, `TaintGroupKeysTest`, `SubsumptionTest`, `ResultsTest` |
| Every public AP facade operation in ap-impl §0.2 | `ap/ApOps.kt`; delegated `EdgeApplication`, `Summary`, `Clean`, `Filter`, `Limit`, `MarkCheck`, `Demand`, `LeafViews`, `Reference` | `ApplyEdgeVectorsTest`, `SummaryKindsTest`, `SummarySelectorTest`, `CleanerTest`, `TypeFilterTest`, `MarkCheckTest`, `FieldLimitTest`, `EmissionTest`, `RestrictionTest`, `DemandSelectionTest`, `RequestActionTest`, `ApCancellationTest` |
| Per-method delta, exact links/requests, immutable demand, native records/hits and lossless witnesses | `store/MethodEdges.kt`, `InitialFacts.kt`, `AddedFacts.kt`, `RunSummaries.kt`, `DemandStore.kt`, `RequestStore.kt`, `RecordStore.kt`, `SourceHitStore.kt`, `VulnerabilityStore.kt` | Corresponding store tests; `TemporaryStorageBoundaryTest`, `RecordReadViewsTest`, `VulnerabilityStoreTest` |
| Standing products and same-call E6 | `store/KaryJoin.kt`, `StandingJoin.kt`, `ConjunctionStore.kt`; `engine/RunMethodCallServices.kt` | `KaryJoinTest`, `StandingJoinTest`, `ConjunctionStoreTest`, `NdJoinAdapterTest`, fixed delayed replay/delivery and seeded closure vectors |
| STATEMENT/STAGE/GEN, reversal, stable rule/action identity and cached directions | `interp/FormApplier.kt`, `MicroEdge.kt`, `StatementSummary.kt`, `RuleStatement.kt`, `CallPlan.kt`, `FormsCache.kt`, `FormsReference.kt` | `FormApplierTest`, reversal tests, `FormsCacheTest`, `FormsReferenceTest`, `FormsSinkTriggerTest` |
| Actual JVM statement/rule/call/boundary lowering, type/mark policy, graph and method caches | JVM `interp/JIRStatementForms.kt`, `JIRTypeFilters.kt`, `JIRRuleForms.kt`, `JIRCallPlanBuilder.kt`, `JIRBoundaryForms.kt`, `JIRMethodEntries.kt`, `JIRExitWiredGraph.kt`, `JIRInterpreter.kt`; root `JIRMethodContextCache.kt` | JVM construction/effect/validation/call/adapter/boundary/graph/contract/cache tests; actual Spring forms tests |
| Local least closure, modes/cuts/ends/requests/sinks, raw vs selected summaries and call return | `engine/RunMethodAnalyzer.kt`, `EngineAlgebra.kt`, `DeltaWorklist.kt`, `RunMethodLocalServices.kt`, `RunMethodCallServices.kt` | `ExitRulesTest`, `RequestJoinTest`, `CutPointTest`, `ModesTest`, `RecordReplayTest`, `RecordDemotionTest`, `CallZeroTest`, `EndFactTriggerTest`, `NaiveClosureTest` |
| P1–P6/Q1–Q4, complete indexes, fresh per-run actors and joined release | `engine/SummaryStorage.kt`, `PublicationIndex.kt`, `CalleeSubscriptions.kt`, `SubscriptionManager.kt`, `RunManager.kt`, `UnitRunner.kt`, `InFlight.kt`, `EventQueue.kt` | `SummaryStorageProtocolTest`, `IndexCompletenessTest`, `AnyDeliveryTest`, `ProtocolContractsTest`, shell/scheduling/failure/join/cleanup/lifetime tests and `ScheduleFuzzerTest` |
| Same-call joint support, complete-only report and both normalized hand-offs | `driver/Support.kt`, `Report.kt`, `HandOff.kt`, `Seeds.kt`, `Frontier.kt` | `SupportTest`, `AnyTaintSupportTest`, `SupportIntegrationTest`, `ReportTest`, `HandOffTest`, `NormalizedHandOffTest`, seed/frontier tests |
| Fresh engines, stop order, one budget, immutable logs, late barrier guard and result lifetime | `driver/IterationDriver.kt`, `IterationPolicy.kt` | `DriverTest`, `BarrierFailureTest`, `DriverIntegrationTest`, `DemandLinkTest`, D04 current sequences, V4 caller-state and earlier-confirmed stop controls |
| Successful joined prescan, owned value copy, released old contexts/runners/AP and retained lambda reachability | Legacy `ap/ifds/TaintAnalysisUnitRunnerManager.kt`; JVM `ap/ifds/analysis/JIRAnalysisManager.kt` | `PrescanCopyTest`, `PrescanJoinRegressionTest`, `PrescanLifetimeTest`; real app/query transition assertions |
| Optional phase-3 entry, copied roots/rules, setup budget/failures, two states/four statuses and Simple/CWE output | Common `driver/BidiEntry.kt`; JVM `JIRBidiAnalysis.kt`; app `common/sast/dataflow/TaintAnalyzer.kt`, `JIRTaintAnalyzer.kt` | `JIRBidiOutputTest`, `JIRBidiRealEntryTest`, `JIRBidiSetupBudgetTest`, `BidiEntryHookTest`, `JIRTaintBidiEntryTest`, `BidiPrescanSetupGuardTest` (17 focused tests) |
| Actual joined native JVM iterations and additive legacy-suite controls | App `AnalysisTest.kt`, `J03ObservedDriverBridge.kt`, `BidiAcceptanceObservation.kt`, `BidiExistingAnalysisSuites.kt`; query `SampleBasedTest.kt`, `TestAnalysisRunner.kt` | `JIRBidiSequenceAcceptanceTest` (13 vectors), inherited bidi cleaner/primitive/Java/Kotlin/MultiReturn suites, `BidiExampleTest`, `SpringDispatcherAcceptanceTest` |

## Closing checks

The fresh closing command and XML/discovery evidence are recorded in
[J03](bidi-j03.md). Logs remain at /tmp/bidi-j03-full-gate.log and
/tmp/bidi-j03-{common,jvm,app,query}-discovery.txt. Every new annotated test
is void and present in fresh XML: 1,078 common, 247 JVM, 36 application and
five query tests. Each inherited bidi suite has its own fresh class XML.

The five specification/proposal documents are checked together for local
links, numbered section references and code fences. The plan and all bidi
reports also pass the link/fence check: 43 files, 153 local links, 184 fence
pairs and 485 qualified section references, with no issues. git diff --check passes. The final
code, scope, accepted user decisions and proof boundaries match the plan.
