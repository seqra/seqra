# Python Dataflow Implementation Plan

## Phase A: IR Fixes (Module 1-2) ✅ COMPLETE

- [x] A1. Make `PIRExpr` extend `CommonExpr` (Instructions.kt)
- [x] A2. Make `PIRValue` extend `CommonValue` + add `accept(PIRValueVisitor)` to all subtypes (Values.kt)
- [x] A3. Add `index: Int` to `PIRLocation` interface (Instructions.kt)
- [x] A4. Add `override lateinit var location: PIRLocation` to all 21 instruction data classes (Instructions.kt)
- [x] A5. Create `PIRLocationImpl` data class (new file in opentaint-ir-impl-python)
- [x] A6. Wire instruction locations post-construction in `MypyModuleBuilder` (buildFunction, buildModuleInit, convertLambdaFunction)

## Phase B: Core Engine (Module 3) — New Files ✅ COMPLETE

- [x] B1. Create `PIRCallExprAdapter` (adapter/PIRCallExprAdapter.kt)
- [x] B2. Create `PIRFlowFunctionUtils` (util/PIRFlowFunctionUtils.kt)
- [x] B3. Create `PIRMethodContextSerializer` (serialization/PIRMethodContextSerializer.kt)
- [x] B4. Create `PIRCallResolver` (analysis/PIRCallResolver.kt)
- [x] B5. Create `PIRMethodCallResolver` (analysis/PIRMethodCallResolver.kt)
- [x] B6. Create `PIRMethodAnalysisContext` + `LocalNameCollector` (analysis/PIRMethodAnalysisContext.kt)
- [x] B7. Create `PIRMethodCallFactMapper` (analysis/PIRMethodCallFactMapper.kt)
- [x] B8. Create `PIRMethodStartFlowFunction` (analysis/PIRMethodStartFlowFunction.kt)
- [x] B9. Create `PIRMethodSequentFlowFunction` with ZeroToFact handling (analysis/PIRMethodSequentFlowFunction.kt)
- [x] B10. Create `PIRMethodCallFlowFunction` with source/sink/pass-through rules (analysis/PIRMethodCallFlowFunction.kt)
- [x] B11. Create `PIRMethodCallSummaryHandler` (analysis/PIRMethodCallSummaryHandler.kt)
- [x] B12. Create `PIRMethodSideEffectSummaryHandler` (analysis/PIRMethodSideEffectSummaryHandler.kt)

## Phase B: Core Engine (Module 3) — Modify Existing Stubs ✅ COMPLETE

- [x] B13. Implement `PIRLanguageManager.kt` (8 methods + flattenCfg cache)
- [x] B14. Implement `PIRApplicationGraph.kt` (full rewrite with PIRFunctionGraph)
- [x] B15. Implement `PIRAnalysisManager.kt` (full rewrite + no-op preconditions)

## Phase C: Test Fix (Module 4) ✅ COMPLETE

- [x] C1. Update `AnalysisTest.kt` — pass `cp` to `PIRAnalysisManager`
- [x] C2. Build and fix compilation errors (FieldAccessor/ElementAccessor imports, nullable visit clash)
- [x] C3. Fix ZeroToFact edge exclusion (use createAbstractAp with Universe exclusions for source facts)
- [x] C4. Fix ZeroToFact propagation (add handleAssignZero/handleReturnZero for proper assignment propagation)
- [x] C5. Fix CallToStart crash (disable interprocedural analysis for minimal prototype)
- [x] C6. Both tests pass: testSimpleSample ✅, testSimpleNonReachableSample ✅

## Known issues: RESOLVED
- [x] I1. Refactored: shared `handleAssignShared`/`handleReturnShared` with `mkCopy` lambda in SequentFlowFunction; shared `propagateFact` with `mkCallToReturnFact`/`mkCallToStartFact` lambdas in CallFlowFunction.
- [x] I2. Fixed AccessPathBaseStorage crash. Root cause: PIRInstruction data classes used structural equality — `PIRReturn(value=null)` in `source()` and `sink()` were equal, making their `MethodEntryPoint`s collide in HashMap. Fix: added identity-based `equals`/`hashCode` overrides to all 20 PIRInstruction data classes. Also enabled full interprocedural analysis (CallToStartZeroFact, CallToStartZFact, CallToStartFFact) with bounds-checking in `mapCallToStart` and `mapMethodCallToStartFlowFact`.

## Key Implementation Decisions Made During Implementation

1. **No interprocedural call-to-start**: For the minimal prototype, source/sink/pass-through rules are applied at call-to-return only. CallToStart facts are not generated. This avoids the AccessPathBaseStorage crash when callee summaries flow back.
2. **ZeroToFact propagation**: The sequent flow function must handle assignments in `propagateZeroToFact()` too (not just `propagateFactToFact()`), since source-generated taint starts as ZeroToFact edges.
3. **ExclusionSet.Universe**: Source-generated facts must use `createAbstractAp(base, Universe)`, not `mostAbstractFinalAp(base)` (which creates with Empty exclusions).
4. **No-op preconditions**: Created private objects `NoOpStartPrecondition`, `NoOpSequentPrecondition`, `NoOpCallPrecondition`.

## Phase D: Test System Expansion (NEXT)

- [ ] D1. Add P0 test cases
- [ ] D2. Create new sample files for P1 cases
- [ ] D3. Add pass-through rules for builtins
