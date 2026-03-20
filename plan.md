# Python IR Implementation Plan

## Status: COMPLETE (initial implementation)

## Phase 1: Protobuf & Infrastructure
- [x] Create project directory and git repo
- [x] Write plan.md
- [x] Write `proto/pir.proto` (~400 lines, all entity/type/instruction/value messages)
- [x] Set up Gradle multi-module build (settings.gradle.kts, root build.gradle.kts)
- [x] Set up `opentaint-ir-api-python` module (build.gradle.kts)
- [x] Set up `opentaint-ir-impl-python` module (build.gradle.kts with protobuf plugin)
- [x] Set up `opentaint-ir-test-python` module (build.gradle.kts)
- [x] Create Python `pir_server` package skeleton
- [x] Generate Python and Kotlin stubs from proto
- [x] Implement Python gRPC server skeleton (Ping/Shutdown RPCs)
- [x] Verify: proto compiles, Python server starts, responds to Ping

## Phase 2: Kotlin API Module
- [x] PIR type sealed hierarchy (Types.kt)
- [x] PIR entity interfaces (Entities.kt: PIRModule, PIRClass, PIRFunction, etc.)
- [x] PIR value sealed hierarchy (Values.kt)
- [x] PIR instruction sealed hierarchy (Instructions.kt: ~35 types)
- [x] PIR CFG classes (Cfg.kt: PIRBasicBlock, PIRCFG)
- [x] PIR visitors (Visitors.kt: PIRInstVisitor, PIRValueVisitor with Default chain)
- [x] Verify: API module compiles with no runtime deps on protobuf/gRPC

## Phase 3: Python Lowering (pir_server)
- [x] TypeMapper: mypy Type → PIRTypeProto
- [x] ScopeStack: variable scope tracking
- [x] ExpressionTransformer: mypy Expression → PIRValueProto + instructions
- [x] StatementTransformer: mypy Statement → PIR instructions + basic blocks
- [x] ModuleBuilder: MypyFile → PIRModuleProto
- [x] ProjectBuilder: mypy.build() → stream of PIRModuleProto
- [x] Function executor (for Tier 3 round-trip tests)
- [x] Wire up BuildProject, BuildModule, ExecuteFunction RPCs
- [x] Verify: can analyze a simple .py file and return PIR protos

## Phase 4: Kotlin Converters (impl module)
- [x] TypeConverter: PIRTypeProto → PIRType
- [x] ValueConverter: PIRValueProto → PIRValue
- [x] InstructionConverter: PIRInstructionProto → PIRInstruction + PIRCFG
- [x] ModuleConverter: PIRModuleProto → PIRModule (full hierarchy)
- [x] PIRProcessManager: Python subprocess lifecycle
- [x] PIRClasspathImpl: gRPC client, streaming build, indexing
- [x] Verify: end-to-end Kotlin → Python → Kotlin works

## Phase 5: Tests
- [x] Tier 2: unit tests for basic instructions (assign, binop, call, branch, loop)
- [x] Tier 2: unit tests for type mapping
- [x] Tier 2: unit tests for classes, methods, decorators
- [x] Tier 2: unit tests for exception handling
- [x] All 16 tests pass

## TODO (future work)
- [ ] Tier 3: round-trip tests (PIRReconstructor + ExecuteFunction)
- [ ] Tier 1: real-world benchmark tests (flask, requests, etc.)
- [ ] Common interface mapping (CommonProject, CommonMethod, etc.)
- [ ] Extension functions (PIRModules.kt, PIRClasses.kt, etc.)
- [ ] Comprehension lowering (list/set/dict comprehensions → loops)
- [ ] Lambda lowering (lambda → synthetic function)
- [ ] Augmented assignment lowering improvement
- [ ] More complete exception type mapping in TryStmt handler

## Progress Log

### Session 1
- Created project directory and git repo
- Wrote plan.md
- Wrote proto/pir.proto (full schema: service + all messages)
- Set up Gradle multi-module build (3 modules: api, impl, test)
- Created Kotlin API module: Types.kt, Values.kt, Instructions.kt, Cfg.kt, Visitors.kt, Entities.kt
- API module compiles (zero external dependencies)
- Generated protobuf/gRPC stubs for both Kotlin and Python
- Created Python gRPC server: __main__.py, server.py, service.py, executor.py
- Created Python builders: project_builder.py, module_builder.py, statement_visitor.py, expression_visitor.py, type_mapper.py, scope.py
- Created Kotlin impl: PIRProcessManager.kt, PIRClasspathImpl.kt, PIRImplData.kt
- Created Kotlin converters: TypeConverter.kt, ValueConverter.kt, InstructionConverter.kt, ModuleConverter.kt
- Created 16 Tier 2 tests across 4 test classes
- Fixed mypy integration: needed incremental=False, preserve_asts=True, explicit module names
- Fixed gRPC generated Python import path
- All 16 tests pass (BUILD SUCCESSFUL)
