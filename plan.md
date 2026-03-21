# Python IR Implementation Plan

## Status: COMPLETE (full test system)

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
- [x] Tier 2: basic instructions (assign, binop, call, branch, loop) — 8 tests
- [x] Tier 2: type mapping — 3 tests
- [x] Tier 2: classes, methods, decorators — 3 tests
- [x] Tier 2: exception handling — 2 tests
- [x] Tier 2: control flow (if/elif/else, while, for, break/continue, ternary, short-circuit) — 15 tests
- [x] Tier 2: operators (all binary, unary, comparison ops) — 26 tests
- [x] Tier 2: functions (params, defaults, *args, **kwargs, kw-only, recursive, static/class methods) — 14 tests
- [x] Tier 2: collections (list/tuple/set/dict, subscript, slice, unpack, delete) — 16 tests
- [x] Tier 2: type annotations (int, str, Optional, Union, Callable, Tuple, Any) — 12 tests
- [x] Tier 2: advanced classes (inheritance, abstract, enum, dataclass, nested, property) — 11 tests
- [x] Tier 2: advanced exceptions (try/except/else/finally, raise from, bare raise, nested) — 8 tests
- [x] Tier 2: generators & async (yield, yield from, async/await) — 8 tests
- [x] Tier 3: round-trip tests (PIRReconstructor + ExecuteFunction) — 10 tests
- [x] Tier 1: real-world benchmarks (click, requests, attrs, typer) — 4 tests
- [x] All 140 tests pass

## TODO (future work)
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

### Session 2
- Implemented Tier 3 round-trip tests: PIRReconstructor.kt (CFG → Python code) + RoundTripTest.kt (10 tests)
- Implemented Tier 1 real-world benchmarks: BenchmarkTest.kt (click, requests, attrs, typer — 4 tests)
- Fixed ProjectBuilder module name derivation for package files (_find_search_root, _path_to_module)
- Fixed DictExpr lowering: mypy's DictExpr uses .items not .keys/.values
- Fixed staticmethod/classmethod/property detection: mypy sets FuncDef.is_static/is_class/is_property flags directly
- Added dataclass detection via class_def.info.metadata['dataclass']
- Added enum detection via base class fullname check (enum.Enum, etc.)
- Expanded Tier 2 tests: 7 new test classes covering control flow, operators, functions, collections, types, advanced classes, advanced exceptions, generators/async
- Optimized tests: shared classpath per test class via @TestInstance(PER_CLASS) + @BeforeAll
- All 140 tests pass across 3 tiers (BUILD SUCCESSFUL)
