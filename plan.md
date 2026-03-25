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

### P1.3 — Basic Benchmark (Strategy 1): DONE
- [x] `BenchmarkProjectCache.kt` — project cloning and caching
- [x] `BenchmarkResult` data class for metrics
- [x] `RealWorldBenchmarkTest.kt` — 5 small/medium projects (logrus, cobra, testify, lo, websocket)

## Phase P2 — Extended Tests & Codegen

### P2.1 — Annotation-Driven Tests: DONE
- [x] `GoIRAnnotationParser.kt` — parses `//@ inst(...)`, `//@ count(...)`, `//@ call(...)`, `//@ cfg(...)`, `//@ entity(...)` annotations
- [x] `GoIRAnnotationVerifier.kt` — verifies annotations against built IR (instruction type, fields, counts, calls, CFG)
- [x] `AnnotationDrivenTests.kt` — JUnit 5 `@TestFactory` that discovers and runs annotated Go files
- [x] 13 annotated Go test files covering: arithmetic, bitwise, unary, pointers, structs, interfaces, control flow, closures, channels, slices, maps, conversions, panic/recover, range/iter

### P2.2 — Code Generator (go-ir-codegen): DONE
- [x] `TypeFormatter.kt` — Go type string formatting (130 lines)
- [x] `PhiEliminator.kt` — SSA phi node elimination (85 lines)
- [x] `GoIRToGoCodeGenerator.kt` — IR→Go code generator (623 lines)

### P2.3 — Round-Trip Tests (Strategy 3): 19 tests, ALL PASSING
- [x] `RoundTripTests.kt` — 6 basic tests (arithmetic, if-else, boolean logic, for-loop, nested if, multiple returns)
- [x] `RoundTripExtendedTests.kt` — 13 extended tests (bitwise, strings, nested loops, countdown, recursion, switch-if, early return, GCD, power, clamp, collatz, digit sum, isPrime)

## Phase P3 — Full Test Suite

### P3.1 — Full Unit Tests: 96 tests, ALL PASSING
- [x] Original 8 test classes (39 tests from P1.2)
- [x] `AnnotationDrivenTests.kt` — 13 dynamic tests from annotated Go files
- [x] `PointerMemoryTests.kt` — 5 tests (stack alloc, heap alloc, deref, composite lit, global access)
- [x] `CollectionTests.kt` — 7 tests (make slice, index, slice expr, make map, map lookup, comma-ok, arrays)
- [x] `TypeConversionTests.kt` — 6 tests (numeric convert, string-bytes, MakeInterface, type assert, comma-ok, ChangeType)
- [x] `SSAPropertyTests.kt` — 7 tests (phi at loop header, phi at merge, unique names, entry block, phi ordering, sequential indices, exit blocks)
- [x] `EntityTests.kt` — 11 tests (imports, exported/unexported, value/pointer receivers, struct fields, interface methods, embedded, globals, variadic, multi-return, anonymous functions)
- [x] `AdvancedControlFlowTests.kt` — 7 tests (switch, nested loops, range with index, select, select with default, defer, goroutine)

### P3.2 — Full Benchmark: DONE
- [x] 20 real-world projects configured in `RealWorldBenchmarkTest.kt`
  - Small: logrus, cobra, websocket, lo, zap
  - Medium: testify, viper, validator, golang-migrate, gin
  - Larger: fiber, go-kit, redis, pgx
  - Large: consul, prometheus, etcd, docker-cli, kubernetes client-go, caddy

### P3.3 — Full Round-Trip & Fuzzing: DONE
- [x] 19 round-trip tests across 2 test classes
- [x] `FuzzRoundTripTests.kt` — 3 fuzz tests (arithmetic, control flow, loop accumulation)
- [x] Fuzz harness: extracts function from reconstructed code, creates Go fuzz test module, runs `go test -fuzz`

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
| Annotation: go-ssa source positions for closures/interfaces may differ from Go source line | Use `//@ count(...)` for closure/interface counts instead of `//@ inst(...)` at specific lines |
| Codegen: `goto` jumps over `_alloc_` variable declarations in stack allocs | Pre-declare `_alloc_` locals at function top; `visitAlloc` only emits address-of assignment |

## Phase Q — Quality (see quality-plan.md for details)

### Q1 — Benchmark testing: pending
### Q2 — Round-trip expansion: DONE (302 tests, 25s)
### Q3 — Bug fixes: 1 codegen bug found and fixed

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
| 2026-03-26 | P2.1 Annotation-driven tests (parser, verifier, 13 annotated files) | DONE |
| 2026-03-26 | P3.1 Full unit tests (96 total, +57 new tests in 6 new test classes) | DONE |
| 2026-03-26 | P3.3 Extended round-trip (19 total) + fuzz tests (3 tests) | DONE |
| 2026-03-26 | P1.3 + P3.2 Benchmark infrastructure + 20 real-world projects | DONE |
| 2026-03-26 | Q: Quality phase — BatchRoundTripRunner, parallel tests, 302 round-trip tests | DONE |
| 2026-03-26 | Q: Fix codegen bug (goto over _alloc_ declarations) | DONE |
