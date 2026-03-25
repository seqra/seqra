# Go IR Implementation Plan

## Phase P0 — Core Infrastructure

### P0.1 — Project Setup
- [x] Create project directory, init git
- [x] Write plan.md
- [x] Set up Gradle multi-module build (root, settings, version catalog)
- [x] Write .gitignore

### P0.2 — Protobuf Definitions
- [x] Write `proto/goir/service.proto` with all message types
- [x] Verify proto compiles

### P0.3 — go-ir-api Module (Kotlin IR Interfaces & Classes)
- [x] Type hierarchy (`GoIRType`, all 13 type classes, `GoIRBasicTypeKind`)
- [x] Value hierarchy (`GoIRValue`, 6 non-instruction values, `GoIRConstantValue`)
- [x] Instruction hierarchy (`GoIRInst`, all 37 instructions, markers)
- [x] Visitors (`GoIRInstVisitor`, `GoIRValueVisitor` with Default)
- [x] Entity interfaces (`GoIRProgram`, `GoIRPackage`, `GoIRFunction`, `GoIRNamedType`, etc.)
- [x] CFG interfaces (`GoIRBasicBlock`, `GoIRBlockGraph`, `GoIRInstGraph`, `GoIRInstRef`)
- [x] Enums (`GoIRBinaryOp`, `GoIRUnaryOp`, `GoIRCallMode`, `GoIRChanDirection`)
- [x] Supporting types (`GoIRCallInfo`, `GoIRSelectState`, `GoIRPosition`, `GoIRParameter`, `GoIRFreeVar`)
- [x] Extension functions (`findFunctionByName`, `findNamedTypeByName`, etc.)

### P0.4 — go-ssa-server (Go gRPC Server)
- [x] Go module setup (`go.mod`, dependencies)
- [x] Generate Go protobuf code
- [x] `main.go` — entry point, flag parsing, server start
- [x] `server.go` — gRPC service implementation
- [x] `builder.go` — go/packages + go/ssa loading
- [x] `id_allocator.go` — entity ID assignment
- [x] `serializer.go` — SSA → protobuf conversion (types, packages, functions, instructions)
- [x] Test with simple Go program

### P0.5 — go-ir-client Module (Kotlin gRPC Client)
- [x] Proto build integration (protobuf Gradle plugin)
- [x] `GoSsaServerProcess.kt` — subprocess management
- [x] `GoIRDeserializer.kt` — proto → GoIR conversion (with ForwardRefValue for SSA phi edges)
- [x] `GoIRClient.kt` — high-level API
- [x] Impl classes (`GoIRProgramImpl`, `GoIRPackageImpl`, `GoIRFunctionImpl`, etc.)
- [x] `GoIRInstGraphImpl`, `GoIRBlockGraphImpl`

## Phase P1 — Basic Tests

### P1.1 — Test Infrastructure
- [x] `GoIRTestBuilder.kt` — helper to build IR from source
- [x] `GoIRTestExtension.kt` — JUnit 5 shared server
- [x] `GoIRSanityChecker.kt` — structural validator (indexing, CFG, SSA, entity invariants)
- [x] `GoRunner.kt` — Go compile+run utility

### P1.2 — Core Unit Tests (Strategy 2): 39 tests, ALL PASSING
- [x] Smoke tests (5 tests) — pipeline end-to-end, parameters, CFG structure, package metadata
- [x] Struct tests (5 tests) — declaration, field access, embedding, literals, methods
- [x] Interface tests (5 tests) — declaration, multiple methods, type assert, comma-ok, MakeInterface
- [x] Control flow tests (6 tests) — if/else, for loop, for-range, panic, multiple returns, inst graph
- [x] Function/closure tests (6 tests) — direct call, closures, multi-return, variadic, anon args, method call
- [x] Channel/goroutine tests (6 tests) — make chan, send, recv, goroutine, defer, select
- [x] Generics tests (5 tests) — type params, generic struct, comparable, interface constraint, generic method
- [x] Diagnostic test (1 test) — dumps full program structure for debugging

### P1.3 — Basic Benchmark (Strategy 1)
- [ ] Benchmark infrastructure (BenchmarkProjectCache, metrics)
- [ ] 5 real-world projects

## Phase P2 — Extended Tests & Codegen

### P2.1 — Annotation-Driven Tests
- [ ] `GoIRAnnotationParser.kt`
- [ ] `GoIRAnnotationVerifier.kt`
- [ ] Annotated Go test files

### P2.2 — Code Generator (go-ir-codegen): DONE
- [x] `TypeFormatter.kt` — Go type string formatting (130 lines)
- [x] `PhiEliminator.kt` — SSA phi node elimination (85 lines)
- [x] `GoIRToGoCodeGenerator.kt` — IR→Go code generator (430 lines)

### P2.3 — Round-Trip Tests (Strategy 3): 6 tests, ALL PASSING
- [x] `RoundTripTests.kt` — test runner with `roundTrip()` helper
- [x] arithmetic test — add, sub, mul, function composition
- [x] if-else test — abs, max with conditional returns
- [x] boolean logic test — isPositive, both with nested conditions
- [x] for loop with accumulator — sumTo, factorial (tests phi elimination)
- [x] nested if test — classify with multi-level branching
- [x] multiple returns test — divide with early return

## Phase P3 — Full Test Suite

### P3.1 — Full Unit Tests
- [ ] Expand to ~100 unit tests across all categories

### P3.2 — Full Benchmark
- [ ] 20 real-world projects

### P3.3 — Full Round-Trip & Fuzzing
- [ ] 25 round-trip samples
- [ ] Fuzz test harness generation
- [ ] `FuzzRoundTripTests.kt`

## Bugs Found & Fixed During Testing

| Bug | Fix |
|-----|-----|
| Go serializer sent types in discovery order, not dependency order → ClassCastException | Changed `collectType` to add types AFTER recursing sub-types (topological order) |
| Methods (value/pointer receiver) not serialized as ProtoFunction → missing method IDs | Added method serialization loop in `serializePackage` after named types |
| Anonymous functions (closures) not serialized → Unknown function ID in MakeClosure | Added serialization of non-member functions from `allFunctions` in `serializePackage` |
| Instantiated generic functions (Package()==nil) not collected or serialized | Removed Package!=nil filter in `collectAll`; serialize package-less functions in first package |
| Phi edges in loops reference values from later blocks → Unknown value ID | Created `LazyValueMap` with `ForwardRefValue` placeholder for forward references |
| Interface method signature type cast fails for NAMED_REF types → ClassCastException | Changed `as GoIRFuncType` to `as? GoIRFuncType` with fallback for lazy named refs |
| External functions (e.g. fmt.Println) not serialized → Unknown function ID | Added `streamExternalPackages` in Go server to serialize external dependency stubs |
| External globals not serialized → Unknown global ID | Extended `streamExternalPackages` to also serialize external globals; added stub fallback in deserializer |
| Go gRPC rejects non-UTF-8 strings from stdlib function names | Added `sanitizeUTF8()` for all string fields in serializer |
| Codegen generated internal package imports from stdlib dependency tree | Changed import scanning to only include direct references from user functions |
| ForwardRefValue not handled in codegen valueRef → outputs placeholder text | Changed default branch in `valueRef` to use `value.name` for forward ref delegation |

## Progress Log

| Date | Task | Status |
|------|------|--------|
| 2026-03-25 | P0.1 Project setup | DONE |
| 2026-03-25 | P0.2 Protobuf definitions | DONE |
| 2026-03-25 | P0.3 go-ir-api module | DONE |
| 2026-03-25 | P0.4 go-ssa-server | DONE |
| 2026-03-25 | P0.5 go-ir-client | DONE |
| 2026-03-25 | P1.1 Test infrastructure | DONE |
| 2026-03-25 | P1.2 Core unit tests (39 tests) | DONE |
| 2026-03-25 | Bug fixes: 5 serialization/deserialization bugs | DONE |
| 2026-03-26 | P2.2 Code generator (go-ir-codegen) | DONE |
| 2026-03-26 | P2.3 Round-trip tests (6 tests) | DONE |
| 2026-03-26 | Bug fixes: 7 round-trip related bugs | DONE |
