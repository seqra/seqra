# Design Changes Log

This document tracks deviations from the original design (in go-ir-impl/*.md) that were discovered and fixed during implementation.

## 1. GoIRValue cannot be sealed interface

**Original design**: `GoIRValue` was designed as a sealed interface in `go-ir-api`.

**Problem**: `GoIRValue` is implemented by classes in both the `value` package (e.g., `GoIRConstValue`, `GoIRParameterValue`) and the `inst` package (e.g., `GoIRAlloc`, `GoIRBinOp`). Kotlin sealed interfaces require all implementations to be in the same package.

**Fix**: Changed `GoIRValue` to a regular (non-sealed) `interface`. Exhaustive matching is still achieved via the `GoIRValueVisitor` and `GoIRInstVisitor` patterns.

## 2. GoIRType sealed hierarchy prevents client extension

**Original design**: `GoIRType` is a sealed interface in `go-ir-api` module.

**Problem**: During deserialization in `go-ir-client`, named type references may appear before the named type definition is received. We needed a placeholder/lazy ref type, but sealed types prevent adding new implementations in other modules.

**Fix**: Instead of creating a placeholder type class, we use a separate `lazyNamedTypeRefs` map in the deserializer. When a NAMED_REF type ID appears before the named type is resolved, we store a temporary `GoIRBasicType(INT)` and fix it up when the named type arrives. This avoids breaking the sealed hierarchy.

## 3. GoIRField name collision

**Original design**: `GoIRField` was used for both entity fields (struct fields in `api` package) and the SSA Field instruction (in `inst` package).

**Problem**: The class `GoIRField` exists in both `org.opentaint.ir.go.api` (entity representing a struct field) and `org.opentaint.ir.go.inst` (SSA instruction for field access). This caused ambiguous imports.

**Fix**: Use fully-qualified references (`org.opentaint.ir.go.inst.GoIRField`) in the deserializer and codegen where both packages are imported.

## 4. Type serialization order — topological sort required

**Original design**: Types were to be serialized as discovered.

**Problem**: The Go serializer sent types in discovery order, which meant dependent types could appear before their dependencies. The Kotlin deserializer processes types sequentially and threw ClassCastException when a type referenced a not-yet-deserialized dependency.

**Fix**: Changed `collectType` in the Go serializer to add types to the list AFTER recursing into sub-types, producing a topological (dependency-first) order.

## 5. Methods not included in package members

**Original design**: Expected all functions (including methods) to come from `pkg.Members`.

**Problem**: go-ssa's `pkg.Members` map only contains package-level functions and named types, not methods. Methods are accessed via `prog.MethodSets`.

**Fix**: Added an explicit method serialization loop in `serializePackage` that iterates over named types' method sets.

## 6. Anonymous functions not serialized

**Original design**: Expected all functions to be package members.

**Problem**: Closures/anonymous functions have `fn.Parent() != nil` and are not package members. They were silently skipped during serialization.

**Fix**: Added serialization of all entries from `allFunctions` map that belong to the package, regardless of whether they are package members.

## 7. Generic instantiations have nil Package

**Original design**: Assumed all functions have a non-nil Package.

**Problem**: go-ssa creates synthetic functions for generic instantiations (e.g., `lo.Map[int, string]`) that have `Package() == nil`. These were filtered out by the `fn.Package() != nil` guard.

**Fix**: Removed the nil-package filter in `collectAll`. Package-less functions are serialized with the first package as a fallback container.

## 8. SSA phi nodes reference future values (forward references)

**Original design**: Expected all value IDs to be defined before use.

**Problem**: In loop headers, phi edges can reference values from later blocks (the back-edge). These values haven't been deserialized yet when the phi node is processed.

**Fix**: Created `LazyValueMap` with `ForwardRefValue` placeholder. `ForwardRefValue` stores the pending value ID and is resolved later. It delegates `name`, `type`, etc. to the actual value once resolved.

## 9. Interface method signatures may be lazy placeholders

**Original design**: Interface method signatures are always `GoIRFuncType`.

**Problem**: When deserializing interface types, the signature type ID might point to a type that was stored as a NAMED_REF placeholder (`GoIRBasicType(INT)`) before the actual type arrived.

**Fix**: Changed the cast `as GoIRFuncType` to safe cast `as? GoIRFuncType` with a fallback that creates a default empty signature.

## 10. External functions/globals not serialized

**Original design**: Only user package entities serialized.

**Problem**: User code calls stdlib functions (e.g., `fmt.Println`) and references stdlib globals. The Go server assigned IDs to these but never serialized their definitions, causing "Unknown function/global ID" errors.

**Fix**: Added `streamExternalPackages()` in the Go server to serialize stub packages for all referenced external dependencies. Added `getOrCreateStubPackage()` fallback in the Kotlin deserializer.

## 11. Non-UTF-8 strings from Go stdlib

**Original design**: All string fields are valid UTF-8.

**Problem**: Some Go stdlib function names (from internal packages) contain non-UTF-8 bytes. gRPC's protobuf implementation rejects messages with invalid UTF-8 in string fields.

**Fix**: Added `sanitizeUTF8()` wrapper using `strings.ToValidUTF8()` for function names, synthetic kinds, and string constants in the Go serializer.

## 12. Codegen imports internal packages

**Original design**: Import scanner collects all referenced external packages.

**Problem**: The scanner was too aggressive — it traversed the full dependency graph including `internal/abi`, `runtime`, etc. from the stdlib, which are not importable from user code.

**Fix**: Changed import scanning to only examine user functions (skip init, synthetic), and only add imports for packages that are directly called (not transitively referenced).

## 13. Variadic arguments passed as slice, not spread

**Original design**: Arguments are passed directly to function calls.

**Problem**: SSA explicitly constructs `[]interface{}` slices for variadic calls. The generated code passed the slice as a single argument instead of spreading it with `...`.

**Fix**: Detect variadic functions in codegen and append `...` to the last argument when the function is variadic.

## 14. ForwardRefValue not handled in codegen

**Original design**: All values are resolved GoIRValue subclasses.

**Problem**: The `valueRef` function's `when` expression had explicit matches for all known `GoIRValue` subclasses but no match for `ForwardRefValue`, which isn't a direct GoIR API type.

**Fix**: Added `else -> value.name` default branch, which works for `ForwardRefValue` because it delegates `name` to the resolved value.

## 15. Tuple types cannot be declared in Go

**Original design**: All SSA values get Go variable declarations.

**Problem**: SSA tuple types (multi-return values like `(int, error)`) are not valid Go variable declarations. `var t0 (int, error)` is a syntax error.

**Fix**: Skip variable declarations for tuple-typed values. Use `_, _ = f()` pattern for discarded multi-return calls.

## 16. Unused labels cause Go compilation errors

**Original design**: All basic blocks get labels.

**Problem**: Go requires all labels to be used (referenced by a goto). Emitting `blockN:` for every block, including the entry block and fall-through blocks, causes compilation errors.

**Fix**: Only emit labels for blocks that are actual goto targets (have predecessors that jump to them via explicit goto).

## 17. Gradle 9.3.1 requires Kotlin 2.1.10

**Original design**: Used Kotlin 1.9.22.

**Problem**: Gradle 9.3.1 ships with Kotlin 2.x internally and has compatibility requirements that prevent using Kotlin 1.9.x plugins.

**Fix**: Updated version catalog to Kotlin 2.1.10.

## 18. JUnit Platform Launcher required for Gradle 9.x

**Original design**: JUnit 5 dependency is sufficient for test execution.

**Problem**: Gradle 9.x requires explicit `junit-platform-launcher` runtime dependency.

**Fix**: Added `runtimeOnly(libs.junit.platform.launcher)` to go-ir-tests module.

## 20. Stack Alloc declarations placed after goto targets

**Original design**: `visitAlloc` for stack allocations emitted `var _alloc_X Type` inline at the usage site.

**Problem**: Go doesn't allow `goto` to jump over variable declarations. When a `goto blockN` precedes a stack alloc in a different block, the Go compiler rejects it with "goto jumps over declaration".

**Fix**: Pre-declare all `_alloc_` local variables at the function top (alongside other SSA variable declarations). The `visitAlloc` for stack allocs now only emits the address-of assignment (`X = &_alloc_X`), not the declaration.

## 19. KotlinCompile task API changed in Gradle 9.x

**Original design**: Used `kotlinOptions { jvmTarget = "..." }`.

**Problem**: Gradle 9.x Kotlin plugin uses `compilerOptions { jvmTarget.set(...) }` API instead.

**Fix**: Updated all build scripts to use the new `compilerOptions` API.

## 20. GoExpr/Assign refactoring — instructions no longer implement GoIRValue

**Original design**: Value-producing instructions (GoIRAlloc, GoIRBinOp, GoIRCall, etc.) implemented both `GoIRInst` and `GoIRValue` (via `GoIRValueInst`). Other instructions referenced these instruction objects directly as operands.

**Problem**: Instructions-as-values creates a confusing dual identity and prevents clean separation between computation and storage. Referencing instruction objects as operands creates tight coupling.

**Fix**: Introduced a three-layer model:
- `GoIRRegister(type, name)` — an SSA register (implements GoIRValue), the indirection layer between instructions
- `GoIRDefInst` — sealed interface for value-defining instructions, with a `register: GoIRRegister` field
  - `GoIRAssignInst(register, expr: GoIRExpr)` — wraps 24 expression types
  - `GoIRPhi(register, edges)` — phi instruction (kept separate, not an expression)
  - `GoIRCall(register, callInfo)` — call instruction (kept separate, not an expression)
- `GoIRExpr` — sealed interface with 24 expression data classes (computation only, no instruction metadata)
- `GoIRValueInst` removed entirely; `GoIRInstVisitor` reduced from 37 to 14 methods
- Operands reference `GoIRRegister` objects, never instruction objects
