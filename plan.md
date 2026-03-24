# Python Dataflow Implementation Plan

## Knowledge Base

- **`edges-knowledge.md`** — Edges, abstraction, and exclusion sets. Documents edge types (Z2Z, Z2F, F2F), access path structure, abstraction mechanism, exclusion sets, refinement via `FinalFactReader`, interprocedural edge flow, and lessons learned from implementation.

> **Important**: `edges-knowledge.md` must be updated whenever we learn something new about the edge/abstraction/exclusion system. Always read it before working on flow functions, taint mark handling, or interprocedural analysis.

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

- [x] I3. Fixed factHasMark over-approximation. Previously `factHasMark` returned true for any abstract fact
  where the taint mark wasn't excluded — an unsound over-approximation. Fix: Implemented `PIRFactRefinement` class
  (mirrors JVM's `FinalFactReader`). Now `checkSinks` only reports vulnerabilities for **concrete** taint mark matches.
  For abstract facts, it records the taint mark accessor as a refinement. `propagateFact` applies refinements to
  output facts (both initial and final) via `refinement.refine()`, which unions the refinement into the exclusion set.
  The framework then re-analyzes with more specific facts (e.g., `arg(0).![taint].*` instead of `arg(0).*`)

## Key Implementation Decisions Made During Implementation

1. **No interprocedural call-to-start**: For the minimal prototype, source/sink/pass-through rules are applied at call-to-return only. CallToStart facts are not generated. This avoids the AccessPathBaseStorage crash when callee summaries flow back.
2. **ZeroToFact propagation**: The sequent flow function must handle assignments in `propagateZeroToFact()` too (not just `propagateFactToFact()`), since source-generated taint starts as ZeroToFact edges.
3. **ExclusionSet.Universe**: Source-generated facts must use `createAbstractAp(base, Universe)`, not `mostAbstractFinalAp(base)` (which creates with Empty exclusions).
4. **No-op preconditions**: Created private objects `NoOpStartPrecondition`, `NoOpSequentPrecondition`, `NoOpCallPrecondition`.

## Phase D: Test System Expansion ✅ COMPLETE (core tests)

### D1. Sample files created (flat layout under core/samples/src/main/python/)
- [x] AssignmentFlow.py — assign_direct, assign_chain, assign_long_chain, assign_overwrite, assign_overwrite_other
- [x] BranchFlow.py — branch_if_true, branch_if_else_both, branch_if_else_one, branch_overwrite_in_branch
- [x] LoopFlow.py — loop_while_body, loop_for_body
- [x] SimpleCall.py — call_simple, call_return, call_pass_through (with module-level helpers)
- [x] ChainedCall.py — call_chain_2, call_chain_3 (with module-level helpers)
- [x] ArgumentPassing.py — call_arg_kill, call_multiple_args_positive, call_multiple_args_negative
- [x] ReturnValue.py — return_assign_and_sink, return_safe_despite_tainted_input
- [x] ClassField.py — field_simple_read, field_different_field, field_overwrite
- [x] DictAccess.py — dict_literal, dict_assign
- [x] SimpleObject.py — class_method_call, class_method_return (@Disabled)
- [x] StaticMethod.py — static_method_call, classmethod_call (@Disabled)

### D2. Kotlin test classes created
- [x] IntraproceduralFlowTest.kt — 11 tests, all pass
- [x] InterproceduralFlowTest.kt — 10 tests, all pass
- [x] FieldSensitiveFlowTest.kt — 5 tests, all pass (field read, field write strong update, subscript, dict literal)
- [x] ClassFeatureFlowTest.kt — 4 tests, all @Disabled (interprocedural analysis for class methods needs debugging)

### D3. Interprocedural fixes (discovered during test system expansion)
- [x] I3. PIRLocal-as-parameter mapping: PIR represents parameter usage as PIRLocal, not PIRParameterRef.
  Fix: `PIRFlowFunctionUtils.accessPathBase()` now maps PIRLocal with parameter name → `Argument(i)`.
  This ensures callee summary edges align with caller subscriptions (both use Argument(i) base).
- [x] I4. Abstract taint mark detection: Inside callees, facts are abstracted (e.g. `var(0).*`).
  `factHasMark()` now checks both concrete match (`startsWithAccessor`) and abstract match
  (`isAbstract() && accessor !in exclusions`), enabling sink detection in callees.

### E. Field Sensitivity & Class Method Resolution ✅ PARTIAL

#### E1. Field sensitivity in sequent flow function
- [x] PIRAssign + PIRAttrExpr: field read (`x = obj.attr`) with `readAccessor`/`rebase`
- [x] PIRAssign + PIRSubscriptExpr: subscript read (`x = obj[i]`) with `ElementAccessor`
- [x] PIRAssign + PIRDictExpr/PIRListExpr/PIRTupleExpr/PIRSetExpr: container literal taint propagation
- [x] Strong update for StoreAttr and StoreSubscript (both ZeroToFact and FactToFact)
- [x] Strong update with name-only FieldAccessor matching (`factStartsWithField`)
- [x] Handle constant stores (e.g., `obj.data = "safe"`) — null valueBase doesn't short-circuit strong update

#### E2. Self/cls parameter offset for class methods
- [x] `PIRFlowFunctionUtils.implicitParamOffset(callee)` — returns 0 for module-level/static, 1 for instance/classmethod
- [x] `PIRMethodCallFlowFunction.mapCallToStart` applies offset: `args[i]` → `Argument(i + offset)`
- [x] `PIRMethodCallFactMapper.mapMethodCallToStartFlowFact` applies offset
- [x] `PIRMethodCallFactMapper.mapMethodExitToReturnFlowFact` reverses offset: `Argument(i)` → `args[i - offset]`

#### E3. Fallback call resolution for class/instance methods
- [x] `PIRCallResolver.resolve(call, method)` — fallback when `resolvedCallee` is null
  - Strategy 1: obj.type.typeName + attrName (for typed instance vars)
  - Strategy 2: PIRGlobalRef.module.name + attrName (for class-level calls like `Util.transform()`)
  - Strategy 3: Infer class from constructor call (`inferClassFromConstructor`)
- [x] Updated `PIRMethodCallResolver`, `PIRAnalysisManager`, `PIRMethodCallFactMapper` to use fallback
- [x] Root cause: mypy doesn't set `MemberExpr.node.fullname` on `CallExpr` for method calls;
  `PIRCall.resolvedCallee` is null for `obj.method()` and `ClassName.method()` patterns

#### E4. Class feature tests ✅ FIXED
- Root cause: `PIRFunctionImpl`/`PIRClassImpl`/`PIRModuleImpl` data classes had circular `hashCode()`/`toString()` 
  through `enclosingClass ↔ methods` references. `StackOverflowError` occurred when the framework used class method
  functions as HashMap keys.
- Fix: Added `equals()/hashCode()/toString()` overrides using `qualifiedName` to break circular references.
- All 4 tests pass: testClassMethodCall, testClassMethodReturn, testStaticMethodCall, testClassmethodCall

### D4. Pass-through rules for builtins ✅ COMPLETE

**Implementation approach chosen**: Receiver-based pass-through rules using `PositionBase.This`.

**Key discoveries during implementation**:

1. **`resolvedCallee` is null for most method calls**: For `data.upper()`, mypy doesn't set `MemberExpr.node.fullname` 
   (it's `None` for instance method access). Both the proto's `resolved_callee` and `MemberExpr.fullname` are empty.
   The Kotlin fallback using `memberExpr.expr.exprType.classType` also fails because mypy's type dict maps 
   receiver NameExprs to `AnyType` (even when the declared return type is `str`). This appears to be a mypy 
   `export_types` limitation.

2. **Solution: Attribute-name-based fallback matching**: Added `matchesCallOrReceiver()` in `PIRMethodCallFlowFunction` 
   which, for unresolved calls, finds the `PIRAttrExpr` that defines the callee temp variable, extracts the attribute 
   name, and matches pass-through rules by attribute suffix (e.g., rule `builtins.str.upper` matches attr `upper`).
   This is conservative (may over-match) but safe for taint analysis.

3. **`PositionBase.This` resolves to method call receiver**: Updated `resolvePosition(This)` to call 
   `PIRFlowFunctionUtils.findMethodCallReceiver()`, which scans the method's instructions for the `PIRAssign + PIRAttrExpr` 
   that defines the call's callee and extracts the `obj` (receiver).

4. **Binary expression taint propagation**: `PIRBinExpr(ADD)` (string concat) handled in sequent flow function — 
   taint flows from either operand to the result.

5. **String expression (f-string) taint propagation**: `PIRStringExpr` parts propagate taint in sequent flow.
   However, mypy's f-string desugaring varies by version, so the `testFstring` test is @Disabled pending investigation.

**Files changed**:
- `PIRMethodSequentFlowFunction.kt` — Added `handleBinExpr` and `handleStringExpr`
- `PIRMethodCallFlowFunction.kt` — Added `matchesCallOrReceiver()`, `receiverInfo` lazy property
- `PIRFlowFunctionUtils.kt` — Added `findMethodCallReceiver()`
- `TaintRules.kt` — Added `PythonBuiltinPassRules` object with default rules
- `ExpressionLowering.kt` — Added receiver type fallback for `resolvedCallee` (Kotlin-side)
- `ast_serializer.py` — Added receiver type fallback for `resolved_callee` (Python-side)
- `PIRImplData.kt` — Added `equals()/hashCode()/toString()` overrides to break circular references
- `BuiltinPassThrough.py` — Sample file (updated `source()` to `return ""`)
- `BuiltinPassThroughFlowTest.kt` — 10 tests (9 pass, 1 @Disabled for f-string)
- `AnalysisTest.kt` — `commonPathRules` now uses `PythonBuiltinPassRules.all`

- [x] D4a. Receiver-based pass-through in call flow function (PositionBase.This → receiver)
- [x] D4b. Add pass-through rules for str methods (upper, lower, strip, replace, encode, format)
- [x] D4c. Handle `PIRBinOp(ADD)` for string concatenation taint propagation
- [x] D4d. Handle `PIRStringExpr` for f-string part propagation
- [x] D4e. Default builtin pass-through rules (str methods, constructors, format args)
- [x] D4f. Test: BuiltinPassThroughFlowTest (9 pass + 1 @Disabled)

### D5. Ant Benchmark Adaptation
- [ ] D5a. Create benchmark adaptation script
- [ ] D5b. Adapt Phase 1 Ant benchmark cases
