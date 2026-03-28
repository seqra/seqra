# Go Dataflow Engine — Implementation Plan

## Status: DONE (Phase 1 MVP)

---

## Phase 1: Core Utilities
- [x] `GoFlowFunctionUtils.kt` — Access path mapping (Go IR → framework)
- [x] `GoCallExpr.kt` — CommonCallExpr adapter
- [x] `DummyMethodContextSerializer.kt` — No-op serializer

## Phase 2: Call Resolution
- [x] `GoCallResolver.kt` — DIRECT + INVOKE resolution
- [x] `GoMethodCallResolver.kt` — Framework adapter
- [x] `GoLanguageManager.kt` — Complete stubs (getCallExpr, etc.)

## Phase 3: Graph + Rules
- [x] `GoApplicationGraph.kt` — Complete callees/callers
- [x] `GoTaintRulesProvider.kt` — Rule lookup by function name

## Phase 4: Context + Mapper
- [x] `GoMethodAnalysisContext.kt` — Per-method context
- [x] `GoMethodCallFactMapper.kt` — Caller ↔ callee fact mapping

## Phase 5: Flow Functions
- [x] `GoMethodStartFlowFunction.kt` — Entry flow
- [x] `GoMethodSequentFlowFunction.kt` — Intraprocedural flow
- [x] `GoMethodCallFlowFunction.kt` — Call-site flow

## Phase 6: Summary + Stubs
- [x] `GoMethodCallSummaryHandler.kt` — Summary application
- [x] `GoMethodSideEffectHandler.kt` — Side-effect stub
- [x] `GoMethodStartPrecondition.kt` — Trace stub
- [x] `GoMethodSequentPrecondition.kt` — Trace stub
- [x] `GoMethodCallPrecondition.kt` — Trace stub

## Phase 7: Wire Everything
- [x] `GoAnalysisManager.kt` — Complete all 14 methods

## Phase 8: Build & Fix
- [x] Compile the project
- [x] Fix all compilation errors (GoIRValue exhaustiveness, TaintSinkTracker API, MethodCallResolver API, CommonAssignInst returnValue)

## Phase 9: Run Existing Tests
- [x] Run `SampleTest` (2 tests) — PASS
- [x] Fix analysis failures (returnValue null for GoIRCall, propagateZeroToFact not handling facts)

## Phase 10: Add Go Test Samples
- [x] `util.go` — Add typed source/sink variants (sourceInt, sourceAny, sinkAny, sinkInt)
- [x] `interprocedural.go` — Arg/return passing, deep calls, arg position
- [x] `control_flow.go` — if/switch/for tests
- [x] `basic_flow.go` — Simple flow, taint killing, overwrite

## Phase 11: Add Kotlin Test Classes
- [x] Update `AnalysisTest.kt` — Convenience methods (assertReachable/assertNotReachable), pass rule support, `"./..."` pattern
- [x] `BasicFlowTest.kt` — 4 tests
- [x] `InterproceduralTest.kt` — 12 tests
- [x] `ControlFlowTest.kt` — 4 tests

## Phase 12: Full Test Suite
- [x] Run all tests — **19/19 PASS**
- [x] Git commit

---

## Progress Log

| Date | Action | Status |
|------|--------|--------|
| 2026-03-28 | Started implementation | Done |
| 2026-03-28 | Phases 1-7: Created 16 new Kotlin files, modified 3 existing | Done |
| 2026-03-28 | Phase 8: Fixed compilation errors (5 issues) | Done |
| 2026-03-28 | Phase 9: Fixed runtime issues (returnValue extraction, zeroToFact propagation) | Done |
| 2026-03-28 | Phases 10-12: Added 3 Go test files, 3 Kotlin test classes, all 19 tests pass | Done |

---

## Files Created/Modified

### New files (16 Kotlin)
- `GoFlowFunctionUtils.kt` — Access path mapping utility
- `GoCallExpr.kt` — CommonCallExpr adapter
- `GoCallResolver.kt` — DIRECT + INVOKE call resolution
- `GoMethodCallFactMapper.kt` — Caller ↔ callee fact mapping
- `DummyMethodContextSerializer.kt` — No-op serializer
- `analysis/GoMethodAnalysisContext.kt` — Per-method context
- `analysis/GoMethodCallResolver.kt` — Framework MethodCallResolver adapter
- `analysis/GoMethodStartFlowFunction.kt` — Entry flow function
- `analysis/GoMethodSequentFlowFunction.kt` — Intraprocedural flow function
- `analysis/GoMethodCallFlowFunction.kt` — Call-site flow function
- `analysis/GoMethodCallSummaryHandler.kt` — Summary handler
- `analysis/GoMethodSideEffectHandler.kt` — Side-effect stub
- `trace/GoMethodStartPrecondition.kt` — Trace stub
- `trace/GoMethodSequentPrecondition.kt` — Trace stub
- `trace/GoMethodCallPrecondition.kt` — Trace stub
- `rules/GoTaintRulesProvider.kt` — Rule lookup

### Modified files (3 Kotlin + 4 test/sample)
- `GoLanguageManager.kt` — Added cp param, implemented getCallExpr/producesExceptionalControlFlow/getCalleeMethod
- `graph/GoApplicationGraph.kt` — Implemented callees/callers with GoCallResolver
- `analysis/GoAnalysisManager.kt` — Implemented all 14 factory methods
- `rules/TaintRules.kt` — Added CommonTaintConfigurationSink implementation
- `AnalysisTest.kt` — Added convenience methods, pass rule support, `"./..."` pattern
- `util.go` — Added typed source/sink variants
- Added `basic_flow.go`, `interprocedural.go`, `control_flow.go`
- Added `BasicFlowTest.kt`, `InterproceduralTest.kt`, `ControlFlowTest.kt`
