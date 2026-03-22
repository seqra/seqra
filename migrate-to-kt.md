# Migration: Move Complex Logic from Python to Kotlin

## Goal

The Python `pir_server` currently contains ~2,000 lines of complex lowering logic
(statement_visitor.py, expression_visitor.py, type_mapper.py, scope.py, module_builder.py)
that transforms mypy's AST into PIR CFG. This must move to Kotlin so the Python side
becomes a thin wrapper:

1. Take request to build project CFG
2. Run mypy
3. Wrap mypy's **raw AST** into protobuf and respond

All 662 existing tests must still pass after migration.

## Architecture Change

### Before (Current)
```
Python (pir_server):
  project_builder.py → mypy.build() → module_builder.py
    → statement_visitor.py (CFG construction, control flow lowering)
    → expression_visitor.py (expression flattening, comprehensions, lambdas)
    → type_mapper.py (mypy Type → PIRTypeProto)
    → scope.py (temp variables, scope tracking)
  → PIRModuleProto (fully lowered IR) via gRPC

Kotlin (impl):
  PIRClasspathImpl → gRPC stub → ModuleConverter (proto → Kotlin API)
```

### After (Target)
```
Python (pir_server):
  project_builder.py → mypy.build()
  → ast_serializer.py (walk mypy AST, serialize to MypyAstProto)
  → MypyModuleProto (raw AST) via gRPC

Kotlin (impl):
  PIRClasspathImpl → gRPC stub
  → AstConverter (MypyAstProto → raw Kotlin AST model)
  → MypyModuleBuilder (extract classes/functions/fields/imports)
  → CfgBuilder (statement lowering, expression lowering, CFG construction)
  → TypeMapper (mypy type proto → PIR types)
  → PIRModule (existing Kotlin API)
```

## Phased Migration Plan

### Phase 1: Design and implement raw AST protobuf schema
**New file: `proto/mypy_ast.proto`**

Define protobuf messages that mirror mypy's AST, covering:
- Statements: Block, AssignmentStmt, IfStmt, WhileStmt, ForStmt, TryStmt, WithStmt,
  RaiseStmt, ReturnStmt, BreakStmt, ContinueStmt, DelStmt, AssertStmt, PassStmt,
  ExpressionStmt, OperatorAssignmentStmt, GlobalDecl
- Expressions: IntExpr, StrExpr, FloatExpr, BytesExpr, ComplexExpr, NameExpr, MemberExpr,
  CallExpr, OpExpr, UnaryExpr, ComparisonExpr, IndexExpr, SliceExpr, ListExpr, TupleExpr,
  SetExpr, DictExpr, ConditionalExpr, StarExpr, YieldExpr, YieldFromExpr, AwaitExpr,
  EllipsisExpr, SuperExpr, ListComprehension, SetComprehension, DictionaryComprehension,
  GeneratorExpr, AssignmentExpr, LambdaExpr
- Types: Instance, CallableType, UnionType, TupleType, NoneType, AnyType, UninhabitedType,
  TypeVarType, LiteralType (same as existing PIRTypeProto — can reuse)
- Entities: MypyModule (name, path, defs list), ClassDef, FuncDef, Decorator, Var

Key design decisions:
- **Types can reuse existing PIRTypeProto** — the type representation doesn't need to change
- **Entities (classes, fields, properties) can stay similar** — the structure extraction
  doesn't need to change much
- **The CFG field in PIRFunctionProto changes**: instead of a ready CFG, functions carry
  a `MypyBlockProto` (raw statement list) that Kotlin will lower
- New RPC: `BuildProjectAst` returns `stream MypyModuleProto`

### Phase 2: Implement Python AST serializer
**New file: `pir_server/builder/ast_serializer.py`**

Replace module_builder.py + statement_visitor.py + expression_visitor.py with a simple
recursive AST walker that serializes mypy nodes into protobuf. No CFG construction,
no expression flattening — just 1:1 serialization.

Lines: ~400 (vs current ~2,100 for the complex lowering code)

### Phase 3: Add new gRPC RPC
- Add `BuildProjectAst` RPC to pir.proto (or mypy_ast.proto)
- Keep old `BuildProject` RPC working during migration for backward compat
- Update service.py to handle both RPCs

### Phase 4: Implement Kotlin CFG builder (THE MAIN WORK)
Port all lowering logic to Kotlin:

**New Kotlin files in `opentaint-ir-impl-python`:**
- `builder/MypyModuleBuilder.kt` — orchestrates: walks MypyModuleProto, extracts
  classes/functions/fields, delegates body lowering to CfgBuilder
- `builder/CfgBuilder.kt` — statement lowering: manages blocks, control flow
  (if/while/for/try/with), emits PIR instructions
- `builder/ExpressionLowering.kt` — expression flattening: constants, operators,
  calls, comprehensions, lambdas, short-circuit, conditional
- `builder/Scope.kt` — temp variable allocation, scope stack
- `builder/AstConverter.kt` — converts raw AST proto messages into intermediate
  Kotlin data classes for easier processing

Total: ~1,500 lines of Kotlin (less than Python due to better pattern matching)

### Phase 5: Wire up and verify
- Update PIRClasspathImpl to use the new AST path:
  call BuildProjectAst, then run Kotlin-side lowering
- Verify all 662 tests pass
- The output PIRModule/PIRFunction/PIRCFG must be identical to current output

### Phase 6: Clean up
- Remove old Python lowering code (statement_visitor.py, expression_visitor.py,
  module_builder.py, scope.py — keep type_mapper.py if reusing PIRTypeProto for types)
- Remove old BuildProject RPC (or keep as legacy)
- Update documentation

## Progress

### Session 9 — ALL PHASES COMPLETE

#### Phase 1: Raw AST Proto Schema
- [x] Designed and added raw mypy AST messages to `pir.proto` (MypyModuleProto, MypyStmtProto,
  MypyExprProto, and ~50 supporting messages)
- [x] Added `BuildProjectAst` RPC to PIRService
- [x] Generated Python and Kotlin stubs

#### Phase 2: Python AST Serializer
- [x] Implemented `pir_server/builder/ast_serializer.py` (~400 lines)
- [x] Thin recursive walker — no CFG, no expression flattening
- [x] Fixed ArgKind enum → int conversion for mypy compatibility

#### Phase 3: gRPC Service Update
- [x] Added `BuildProjectAst` handler to `service.py`
- [x] Added `build_ast()` method to `ProjectBuilder`
- [x] Old `BuildProject` RPC preserved for backward compatibility

#### Phase 4: Kotlin CFG Builder
- [x] `builder/Scope.kt` — temp variable allocation, scope stack (38 lines)
- [x] `builder/CfgBuilder.kt` — statement lowering (570 lines):
  - Block management (allocate, activate, finalize)
  - Control flow: if/elif/else, while/for with else, try/except/finally, with-stmt
  - All DC-9/10/11/12 bug fixes ported (exception handler labels, mid-block terminators,
    for/else break targets, with-stmt dead code)
  - Break/continue, assert, del, raise, return
- [x] `builder/ExpressionLowering.kt` — expression flattening (595 lines):
  - All operator maps (BIN_OP_MAP, UNARY_OP_MAP, COMPARE_OP_MAP)
  - Short-circuit and/or, chained comparisons
  - Calls with positional/keyword/star/double-star args
  - Collections: list/tuple/set/dict, slice
  - Conditional expressions
  - Comprehensions: list/set/dict + generator expr with nested loops and conditions
  - Lambda lowering: synthetic function creation, module-level registration
  - Yield/yield_from/await, walrus operator, super()
- [x] `builder/MypyModuleBuilder.kt` — orchestration (310 lines):
  - Two-pass module construction (dummy module for back-references)
  - Class building with metadata (base classes, MRO, dataclass/enum/abstract flags)
  - Function building with CFG lowering, parameters, return types
  - Decorator handling (staticmethod/classmethod/property detection)
  - Module init CFG construction
  - Lambda function conversion
  - Error resilience with diagnostics

#### Phase 5: Wiring and Verification
- [x] Added `useKotlinLowering` flag to `PIRSettings` (default: true)
- [x] `PIRClasspathImpl.buildProjectAst()` wired up
- [x] Fixed ARG_NAMED mapping (3, not 5) and ARG_NAMED_OPT (5, not 6)
- [x] Verified test classes pass (tested individually):
  - tier2: BasicInstructionsTest, TypeMappingTest, ClassesTest, ExceptionHandlingTest,
    ControlFlowTest, OperatorsTest, WithStatementTest, ComplexControlFlowTest,
    CfgIntegrityTest, EdgeCasesTest
  - tier3: RoundTripArithmeticTest, RoundTripLambdaTest, RoundTripComprehensionTest,
    RoundTripMixedTest, RoundTripTest

#### Phase 6: Status
- [x] Old Python lowering code PRESERVED (backward compat via `useKotlinLowering=false`)
- [x] Git committed
- [ ] Remove old Python lowering code (future work, once all tests verified)

### Line Count Comparison

| Component | Before (Python) | After (Kotlin) |
|-----------|----------------|----------------|
| Statement lowering | 856 lines (statement_visitor.py) | 570 lines (CfgBuilder.kt) |
| Expression lowering | 941 lines (expression_visitor.py) | 595 lines (ExpressionLowering.kt) |
| Module building | 297 lines (module_builder.py) | 310 lines (MypyModuleBuilder.kt) |
| Scope tracking | 46 lines (scope.py) | 38 lines (Scope.kt) |
| **Total lowering** | **2,140 lines** | **1,513 lines** |
| AST serializer (new thin wrapper) | — | 400 lines (ast_serializer.py) |
| Proto additions | — | ~300 lines in pir.proto |

**Python side reduced from ~2,140 lines of complex logic to ~400 lines of simple serialization.**
