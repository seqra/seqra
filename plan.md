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
- [x] Extension functions

### P0.4 — go-ssa-server (Go gRPC Server)
- [ ] Go module setup (`go.mod`, dependencies)
- [ ] Generate Go protobuf code
- [ ] `main.go` — entry point, flag parsing, server start
- [ ] `server.go` — gRPC service implementation
- [ ] `builder.go` — go/packages + go/ssa loading
- [ ] `id_allocator.go` — entity ID assignment
- [ ] `serializer.go` — SSA → protobuf conversion (types, packages, functions, instructions)
- [ ] Test with simple Go program

### P0.5 — go-ir-client Module (Kotlin gRPC Client)
- [ ] Proto build integration (protobuf Gradle plugin)
- [ ] `GoSsaServerProcess.kt` — subprocess management
- [ ] `GoIRDeserializer.kt` — proto → GoIR conversion
- [ ] `GoIRClient.kt` — high-level API
- [ ] Impl classes (`GoIRProgramImpl`, `GoIRPackageImpl`, `GoIRFunctionImpl`, etc.)
- [ ] `GoIRInstGraphImpl`, `GoIRBlockGraphImpl`

## Phase P1 — Basic Tests

### P1.1 — Test Infrastructure
- [ ] `GoIRTestBuilder.kt` — helper to build IR from source
- [ ] `GoIRTestExtension.kt` — JUnit 5 shared server
- [ ] `GoIRSanityChecker.kt` — structural validator
- [ ] `GoRunner.kt` — Go compile+run utility

### P1.2 — Core Unit Tests (Strategy 2)
- [ ] Struct tests (~5 tests)
- [ ] Interface tests (~5 tests)
- [ ] Control flow tests (~5 tests)
- [ ] Function/closure tests (~5 tests)
- [ ] Channel/goroutine tests (~5 tests)
- [ ] Generics tests (~5 tests)

### P1.3 — Basic Benchmark (Strategy 1)
- [ ] Benchmark infrastructure (BenchmarkProjectCache, metrics)
- [ ] 5 real-world projects

## Phase P2 — Extended Tests & Codegen

### P2.1 — Annotation-Driven Tests
- [ ] `GoIRAnnotationParser.kt`
- [ ] `GoIRAnnotationVerifier.kt`
- [ ] Annotated Go test files

### P2.2 — Code Generator (go-ir-codegen)
- [ ] `GoIRToGoCodeGenerator.kt`
- [ ] `PhiEliminator.kt`
- [ ] `TypeFormatter.kt`

### P2.3 — Round-Trip Tests (Strategy 3)
- [ ] 10 round-trip test samples
- [ ] `RoundTripTests.kt`

## Phase P3 — Full Test Suite

### P3.1 — Full Unit Tests
- [ ] Expand to ~100 unit tests across all categories

### P3.2 — Full Benchmark
- [ ] 20 real-world projects

### P3.3 — Full Round-Trip & Fuzzing
- [ ] 25 round-trip samples
- [ ] Fuzz test harness generation
- [ ] `FuzzRoundTripTests.kt`

## Progress Log

| Date | Task | Status |
|------|------|--------|
| 2026-03-25 | P0.1 Project setup | DONE |
| 2026-03-25 | P0.2 Protobuf definitions | DONE |
| 2026-03-25 | P0.3 go-ir-api module | DONE |
| 2026-03-25 | P0.4 go-ssa-server | IN PROGRESS |
