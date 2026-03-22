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

### Session 9 — Phase 1: Raw AST Proto Schema
- [ ] Design mypy_ast.proto
- [ ] Generate Python and Kotlin stubs
- [ ] Implement ast_serializer.py
- [ ] Add BuildProjectAst RPC
- [ ] Verify AST serialization round-trips correctly

### Session 9 — Phase 4: Kotlin CFG Builder
- [ ] Implement AstConverter.kt (proto → Kotlin AST model)
- [ ] Implement Scope.kt
- [ ] Implement CfgBuilder.kt (statement lowering)
- [ ] Implement ExpressionLowering.kt (expression lowering)
- [ ] Implement MypyModuleBuilder.kt (orchestration)
- [ ] Wire up to PIRClasspathImpl
- [ ] Verify all 662 tests pass

### Session 9 — Phase 6: Cleanup
- [ ] Remove old Python lowering code
- [ ] Git commit
