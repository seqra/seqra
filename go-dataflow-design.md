# Go Dataflow Engine — Design Document

> This document captures the design for a minimal-but-working Go taint dataflow analysis engine,
> based on analysis of the existing Go IR, the Go dataflow stub, the JVM reference engine,
> and the core framework interfaces.

---

## Table of Contents

1. [Current State](#1-current-state)
2. [Architecture Overview](#2-architecture-overview)
3. [Go IR ↔ Access Path Mapping](#3-go-ir--access-path-mapping)
4. [Call Resolver](#4-call-resolver)
5. [Method Analysis Context](#5-method-analysis-context)
6. [Flow Functions](#6-flow-functions)
   - 6.1 [Start Flow Function](#61-start-flow-function)
   - 6.2 [Sequential Flow Function](#62-sequential-flow-function)
   - 6.3 [Call Flow Function](#63-call-flow-function)
7. [Taint Rules Application](#7-taint-rules-application)
8. [GoCallExpr & GoCallFactMapper](#8-gocallexpr--gocallfactmapper)
9. [Summary Handler](#9-summary-handler)
10. [Remaining Entities](#10-remaining-entities)
11. [Implementation Priority](#11-implementation-priority)

---

## 1. Current State

### What exists

| Component | File | Status |
|-----------|------|--------|
| `GoLanguageManager` | `go/GoLanguageManager.kt` | 4/8 methods implemented |
| `GoApplicationGraph` | `go/graph/GoApplicationGraph.kt` | Intra-procedural done, inter-procedural stubbed |
| `GoAnalysisManager` | `go/analysis/GoAnalysisManager.kt` | 0/14 methods implemented |
| `TaintRules` / `GoTaintConfig` | `go/rules/TaintRules.kt` | Fully defined |
| Go IR | `opentaint-ir/go/` | Complete SSA IR with 14 instruction types, 24 expression types |
| Tests | `SampleTest` | 2 test cases (sink-reachable, sink-not-reachable) |

### What needs to be built

All analysis logic inside `GoAnalysisManager` plus supporting classes:
- `GoFlowFunctionUtils` — Go IR value → access path mapping
- `GoCallExpr` — adapter implementing `CommonCallExpr`
- `GoCallResolver` — resolves Go function calls
- `GoMethodAnalysisContext` — method-level analysis state
- `GoMethodCallFactMapper` — maps facts across call boundaries
- `GoMethodStartFlowFunction` — entry point flow logic
- `GoMethodSequentFlowFunction` — intraprocedural flow logic
- `GoMethodCallFlowFunction` — interprocedural flow logic
- `GoMethodCallSummaryHandler` — applies callee summaries
- `GoTaintRulesProvider` — bridges `GoTaintConfig` to rule queries

---

## 2. Architecture Overview

The Go engine follows the same layered architecture as JVM:

```
TaintAnalysisUnitRunnerManager
    └── GoAnalysisManager (implements TaintAnalysisManager)
            ├── extends GoLanguageManager (Go instruction mapping)
            ├── creates GoCallResolver
            ├── creates GoMethodAnalysisContext
            ├── creates GoMethodStartFlowFunction
            ├── creates GoMethodSequentFlowFunction
            ├── creates GoMethodCallFlowFunction
            ├── creates GoMethodCallSummaryHandler
            └── uses GoTaintRulesProvider
```

The engine processes the Go SSA IR (`GoIRInst`, `GoIRFunction`, `GoIRProgram`), maps it
to the framework's abstract access paths (`AccessPathBase`, `Accessor`, `FactAp`), and
produces taint edges (`ZeroToFact`, `FactToFact`) that the core IFDS solver propagates.

---

## 3. Go IR ↔ Access Path Mapping

### 3.1 GoFlowFunctionUtils

A singleton `object GoFlowFunctionUtils` analogous to JVM's `MethodFlowFunctionUtils`.

#### `accessPathBase(value: GoIRValue, method: GoIRFunction): AccessPathBase`

Maps Go IR values to framework access path bases:

| Go IR Value | AccessPathBase | Notes |
|-------------|---------------|-------|
| `GoIRParameterValue(paramIndex)` | `Argument(paramIndex)` | Function parameter reference. Go SSA IR uses `GoIRParameterValue` (not registers) to represent parameter values in function bodies. This directly provides the parameter index. |
| `GoIRRegister(index)` | `LocalVar(index)` | Regular SSA register. `GoIRRegister` has an `index: Int` field that uniquely identifies the register within the function. Use this directly as the `LocalVar` index. |
| `GoIRConstValue` | `Constant(typeName, value)` | Compile-time constants |
| `GoIRGlobalValue` | `ClassStatic` | Package-level globals (mapped to `ClassStatic` with an accessor) |
| `GoIRFunctionValue` | `Constant(funcType, fullName)` | Function reference as value |
| `GoIRBuiltinValue` | `Constant("builtin", name)` | Built-in functions (append, len, etc.) |
| `GoIRFreeVarValue(idx)` | `Argument(paramCount + idx)` or `LocalVar(...)` | Closure captured variables — for MVP, treat as opaque |

**Key design point**: Go SSA IR cleanly separates parameters from registers. `GoIRParameterValue` always maps to `Argument(paramIndex)`, and `GoIRRegister` always maps to `LocalVar(register.index)`. There is no ambiguity — unlike Python's PIR where `PIRLocal` names need to be matched against parameters. This simplifies the mapping logic considerably.

#### `sealed interface Access`

Mirrors JVM's pattern:

```kotlin
sealed interface Access {
    data class Simple(val base: AccessPathBase) : Access
    data class RefAccess(val base: AccessPathBase, val accessor: Accessor) : Access
}
```

Go has no static fields in the JVM sense, so `StaticRefAccess` is not needed. Global variables use `ClassStatic` base with a `FieldAccessor`.

#### `mkAccess(inst: GoIRInst, method: GoIRFunction): Pair<Access, Access>?`

For assignment instructions, returns (LHS access, RHS access). The key expressions:

| Go IR Expression | Access Mapping |
|-----------------|----------------|
| `GoIRFieldAddrExpr(x, field)` / `GoIRFieldExpr(x, field)` | `RefAccess(base(x), FieldAccessor(structType, fieldName, fieldType))` |
| `GoIRIndexAddrExpr(x, idx)` / `GoIRIndexExpr(x, idx)` | `RefAccess(base(x), ElementAccessor)` |
| `GoIRLookupExpr(map, key)` | `RefAccess(base(map), ElementAccessor)` |
| `GoIRUnOpExpr(DEREF, ptr)` | `Simple(base(ptr))` — pointer dereference collapses to the pointee |
| `GoIRAllocExpr` | `Simple(LocalVar(instIdx))` — new allocation |
| Any other expr with a single operand | `Simple(base(operand))` |

### 3.2 Parameter ↔ Argument Mapping

Go SSA IR uses `GoIRParameterValue` to represent parameter values in function bodies. These are distinct from `GoIRRegister` — there is no overlap. `GoIRParameterValue` has a `paramIndex` field that directly gives the parameter's position.

This means the mapping is straightforward:
- `GoIRParameterValue(paramIndex=i)` → `Argument(i)` — always
- `GoIRRegister(index=j)` → `LocalVar(j)` — always

**Why this matters (from Lesson 3)**: The framework's interprocedural summary matching depends on `Argument(i)` bases being consistent. Summary edges have `Argument(i)` as initial bases, and caller subscriptions look for `Argument(i)`. Because Go SSA cleanly uses `GoIRParameterValue` (not registers) for parameters, this alignment is automatic — no name-matching heuristics needed (unlike Python PIR).

---

## 4. Call Resolver

### 4.1 Interface Requirements

```kotlin
interface MethodCallResolver {
    fun resolveMethodCall(callerContext, callExpr, location, handler, failureHandler)
    fun resolvedMethodCalls(callerContext, callExpr, location): List<MethodWithContext>
}
```

### 4.2 Go-Specific Design: `GoCallResolver`

Go has three call modes (from `GoIRCallMode`):

| Mode | Meaning | Resolution Strategy |
|------|---------|-------------------|
| `DIRECT` | Static function call (e.g., `fmt.Println(x)`) | Resolve by `GoIRFunctionValue.fullName` → `GoIRProgram.findFunctionByFullName()` |
| `DYNAMIC` | Call through function value / closure (e.g., `fn(x)` where `fn` is a variable) | For MVP: resolution failure. Future: alias analysis to find possible targets |
| `INVOKE` | Interface method dispatch (e.g., `reader.Read(buf)`) | Resolve to all concrete types that implement the interface, return their matching methods |

#### INVOKE Resolution Strategy

For INVOKE calls, `GoIRCallInfo` provides:
- `receiver: GoIRValue` — the interface-typed receiver
- `methodName: String` — the method being called (e.g., `"Read"`)

The receiver's type is an interface type (via `GoIRInterfaceType` or `GoIRNamedTypeRef` pointing to an interface). The resolver must find all concrete (struct) types in the program that implement this interface and return the corresponding method from each.

**Algorithm:**
1. Extract the interface type from `receiver.type` (resolving `GoIRNamedTypeRef` → `GoIRNamedType` → `GoIRInterfaceType` if needed)
2. Collect the interface's method set (from `GoIRInterfaceType.methods` + embedded interfaces)
3. Scan all `GoIRNamedType` in the program (via `GoIRProgram.allNamedTypes()`)
4. For each named type, check if its method set (`namedType.allMethods()`) is a superset of the interface's method set (structural subtyping — Go's implicit interface satisfaction)
5. For each matching type, find the method with the matching `methodName` and return it

**Optimization**: Build an interface→implementors map once during `GoCallResolver` initialization, so repeated queries are O(1) lookup.

**Important**: Go uses structural subtyping (duck typing) — a type implements an interface if it has all the interface's methods with matching signatures. There is no explicit `implements` declaration.

#### `GoCallResolver` class

```kotlin
class GoCallResolver(
    val cp: GoIRProgram,
) {
    // Pre-computed: for each interface, the list of concrete types implementing it
    private val interfaceImplementors: Map<GoIRNamedType, List<GoIRNamedType>> by lazy {
        buildInterfaceImplementorsMap()
    }

    fun resolve(call: GoIRCallInfo, location: GoIRInst): List<GoIRFunction> {
        return when (call.mode) {
            GoIRCallMode.DIRECT -> {
                val funcValue = call.function as? GoIRFunctionValue
                    ?: return emptyList()
                val callee = cp.findFunctionByFullName(funcValue.fullName)
                if (callee != null) listOf(callee) else emptyList()
            }
            GoIRCallMode.DYNAMIC -> emptyList()  // MVP: unresolved
            GoIRCallMode.INVOKE -> resolveInvoke(call)
        }
    }

    private fun resolveInvoke(call: GoIRCallInfo): List<GoIRFunction> {
        val methodName = call.methodName ?: return emptyList()
        val receiverType = call.receiver?.type ?: return emptyList()

        // Resolve the interface's named type
        val interfaceNamedType = resolveToInterfaceNamedType(receiverType)
            ?: return emptyList()

        // Find all concrete types implementing this interface
        val implementors = interfaceImplementors[interfaceNamedType] ?: return emptyList()

        // Collect the matching method from each implementor
        return implementors.mapNotNull { concreteType ->
            concreteType.methodByName(methodName)
        }
    }

    private fun resolveToInterfaceNamedType(type: GoIRType): GoIRNamedType? {
        return when (type) {
            is GoIRNamedTypeRef -> type.namedType.takeIf { it.kind == GoIRNamedTypeKind.INTERFACE }
            is GoIRInterfaceType -> type.namedType
            else -> null
        }
    }

    private fun buildInterfaceImplementorsMap(): Map<GoIRNamedType, List<GoIRNamedType>> {
        val allTypes = cp.allNamedTypes()
        val interfaces = allTypes.filter { it.kind == GoIRNamedTypeKind.INTERFACE }
        val concreteTypes = allTypes.filter { it.kind != GoIRNamedTypeKind.INTERFACE }

        return interfaces.associateWith { iface ->
            val requiredMethods = collectInterfaceMethodNames(iface)
            concreteTypes.filter { concrete ->
                val concreteMethods = concrete.allMethods().map { it.name }.toSet()
                concreteMethods.containsAll(requiredMethods)
            }
        }
    }

    private fun collectInterfaceMethodNames(iface: GoIRNamedType): Set<String> {
        val methods = mutableSetOf<String>()
        iface.interfaceMethods.mapTo(methods) { it.name }
        for (embed in iface.embeddedInterfaces) {
            methods += collectInterfaceMethodNames(embed)
        }
        return methods
    }
}
```

> **Note**: The MVP implementation checks only method name matching for interface satisfaction. A fully correct implementation would also verify that parameter/return types match (signature-level structural subtyping). For the prototype, name matching is sufficient — signature mismatches are rare in practice and would only cause over-approximation (more callees considered), not unsoundness.

#### `GoMethodCallResolver` (framework adapter)

Wraps `GoCallResolver` to implement `MethodCallResolver`:

```kotlin
class GoMethodCallResolver(
    private val callResolver: GoCallResolver,
) : MethodCallResolver {
    override fun resolveMethodCall(callerContext, callExpr, location, handler, failureHandler) {
        val goCallExpr = callExpr as GoCallExpr
        val resolved = callResolver.resolve(goCallExpr.callInfo, location as GoIRInst)
        if (resolved.isEmpty()) {
            failureHandler.handle(callExpr)  // dispatch to appropriate handler variant
        } else {
            for (callee in resolved) {
                handler.handle(MethodWithContext(callee, EmptyMethodContext))
            }
        }
    }
    
    override fun resolvedMethodCalls(callerContext, callExpr, location): List<MethodWithContext> {
        // Delegate to resolve and wrap
    }
}
```

### 4.3 Completing `GoApplicationGraph.callees()`

The stubbed `callees()` method delegates to `GoCallResolver`, which handles both DIRECT and INVOKE modes:

```kotlin
override fun callees(node: GoIRInst): Sequence<GoIRFunction> {
    val callInfo = node.extractCallInfo() ?: return emptySequence()
    return callResolver.resolve(callInfo, node).asSequence()
}
```

Where `extractCallInfo()` extracts `GoIRCallInfo` from `GoIRCall`, `GoIRGo`, or `GoIRDefer` instructions.

For INVOKE calls, this may return multiple callees (one per concrete type implementing the interface). The framework handles this — it will analyze each callee and merge summaries.

---

## 5. Method Analysis Context

### 5.1 Interface Requirements

```kotlin
interface MethodAnalysisContext {
    val methodEntryPoint: MethodEntryPoint
    val methodCallFactMapper: MethodCallFactMapper
}
```

### 5.2 Go-Specific Design: `GoMethodAnalysisContext`

For the MVP, the Go context is minimal compared to JVM (no alias analysis, no type checker, no local variable reachability):

```kotlin
class GoMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {
    override val methodCallFactMapper: MethodCallFactMapper
        get() = GoMethodCallFactMapper

    val method: GoIRFunction
        get() = methodEntryPoint.method as GoIRFunction
}
```

The JVM engine holds several additional components that can be added later:
- `JIRFactTypeChecker` → Go equivalent would validate facts against Go's type system (future)
- `JIRLocalVariableReachability` → not needed in SSA form (SSA guarantees single-definition, and liveness is structurally determined by use-def chains)
- `JIRLocalAliasAnalysis` → future work for pointer alias analysis

### 5.3 Construction in `GoAnalysisManager`

```kotlin
override fun getMethodAnalysisContext(
    methodEntryPoint: MethodEntryPoint,
    graph: ApplicationGraph<CommonMethod, CommonInst>,
    callResolver: MethodCallResolver,
    taintAnalysisContext: TaintAnalysisContext,
    contextForEmptyMethod: MethodAnalysisContext?,
): MethodAnalysisContext {
    return GoMethodAnalysisContext(methodEntryPoint, taintAnalysisContext)
}
```

---

## 6. Flow Functions

### 6.1 Start Flow Function

**Purpose**: Processes facts at method entry. Handles:
1. Zero fact propagation (emit ZeroToZero, apply **entry-point rules** if any)
2. Non-zero fact filtering (type checking of initial facts)

**Important distinction — Entry-point rules vs Source rules:**
- **Entry-point rules**: Applied in the **start flow function** when entering a method that is itself an analysis entry point (e.g., `main`, HTTP handler). They mark the method's own parameters as tainted. Example: "parameter 0 of `handleRequest` is tainted with user-input mark."
- **Source rules**: Applied in the **call flow function** at call sites. They mark the callee's return value (or arguments) as tainted based on the callee being a known taint source. Example: "calling `source()` taints its return value."

These are **different rule types** with different application points. The start flow function must NOT apply source rules — those belong exclusively in the call flow function.

Currently, `GoTaintConfig` does not have a separate entry-point rule list (only `sources`, `sinks`, `propagators`). For the MVP, `GoTaintConfig` may need an `entryPointSources` field if entry-point tainting is needed, or entry-point rules can be deferred entirely since the current test cases only exercise source rules at call sites.

#### `GoMethodStartFlowFunction`

```kotlin
class GoMethodStartFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>(StartFact.Zero)
        
        // Apply entry-point rules (NOT source rules!)
        // Entry-point rules taint the method's own parameters when
        // this method is an analysis entry point (e.g., HTTP handler).
        //
        // For example: if config declares that parameter 0 of this method
        // is a taint entry point, create a fact:
        //   Argument(0).![mark]/* with ExclusionSet.Universe
        //
        // MVP note: The current test cases don't use entry-point rules
        // (they use source rules applied at call sites in the call flow function).
        // This will be implemented when GoTaintConfig gains an entryPointSources field.
        
        return result
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> {
        // For MVP: pass through without type checking
        return listOf(StartFact.Fact(fact))
    }
}
```

### 6.2 Sequential Flow Function

**Purpose**: Handles taint propagation through intraprocedural statements (assignments, returns).

This is the most complex flow function. It must handle:

1. **Assignments** (`GoIRAssignInst`): `register = expr`
   - Simple copy: `t1 = t0` → if `t0` tainted, `t1` becomes tainted
   - Field read: `t1 = x.field` → if `x.field` tainted, `t1` becomes tainted
   - Field address: `t1 = &x.field` → if `x.field` tainted, `t1` becomes tainted
   - Index access: `t1 = x[i]` → if `x[*]` tainted (element-level), `t1` becomes tainted
   - Allocation: `t1 = alloc T` → no taint propagation

2. **Stores** (`GoIRStore`): `*addr = value`
   - If `addr` points to a field and `value` is tainted, the field becomes tainted
   - This is the Go equivalent of field-write

3. **Returns** (`GoIRReturn`): `return values...`
   - Map return values to `AccessPathBase.Return`

4. **Call results** (`GoIRCall`): handled by CallFlowFunction, not here

5. **Phi nodes** (`GoIRPhi`): `register = phi [v1, v2, ...]`
   - If any incoming value is tainted, the phi result is tainted

#### Key Design: Assignment Handling

For `GoIRAssignInst(register, expr)`:

```
LHS: always Simple(LocalVar(register.index)) — GoIRAssignInst always writes to a GoIRRegister
RHS: depends on expr type
```

| Expression | RHS Access | Flow Rule |
|-----------|-----------|-----------|
| Single operand (type change, convert, etc.) | `Simple(base(operand))` | Copy taint: `base(operand).* → base(register).*` |
| `GoIRFieldExpr(x, field)` | `RefAccess(base(x), FieldAccessor(...))` | Field read: `base(x).field.* → base(register).*` |
| `GoIRFieldAddrExpr(x, field)` | `RefAccess(base(x), FieldAccessor(...))` | Same as field read (address-of preserves taint) |
| `GoIRIndexExpr(x, idx)` / `GoIRIndexAddrExpr` | `RefAccess(base(x), ElementAccessor)` | Element read: `base(x).[*].* → base(register).*` |
| `GoIRLookupExpr(map, key)` | `RefAccess(base(map), ElementAccessor)` | Map read: `base(map).[*].* → base(register).*` |
| `GoIRExtractExpr(tuple, idx)` | `Simple(base(tuple))` | Tuple extract (multi-return): copy taint |
| `GoIRBinOpExpr` / `GoIRUnOpExpr(NOT/NEG)` | No taint propagation | Arithmetic/logic kills taint |
| `GoIRAllocExpr` | No source | Allocation doesn't carry taint |
| `GoIRMakeSliceExpr` / `GoIRMakeMapExpr` / etc. | No taint propagation | Container creation is clean |
| `GoIRSliceExpr(x, ...)` | `Simple(base(x))` | Slice of tainted data is tainted |
| `GoIRMakeClosureExpr(fn, bindings)` | Complex — bindings carry taint | For MVP: treat closure as tainted if any binding is |
| `GoIRMakeInterfaceExpr(x)` | `Simple(base(x))` | Wrapping preserves taint |
| `GoIRTypeAssertExpr(x)` | `Simple(base(x))` | Type assertion preserves taint |
| `GoIRUnOpExpr(DEREF, ptr)` | `Simple(base(ptr))` | Deref preserves taint |
| `GoIRUnOpExpr(ARROW, chan)` | `Simple(base(chan))` | Channel receive carries taint |

#### Key Design: Store Handling

`GoIRStore(addr, value)` writes `value` into memory at `addr`.

- If `addr` was produced by `GoIRFieldAddrExpr(x, field)`: this is a field write
  - Creates: `FactToFact(base(value).*, base(x).field.*)`
  - Kills: existing `base(x).field.*` facts (strong update)
- If `addr` was produced by `GoIRIndexAddrExpr(x, idx)`: this is an element write
  - Creates: `FactToFact(base(value).*, base(x).[*].*)`
  - Does NOT kill (weak update — other elements may be tainted)
- If `addr` is a plain register (pointer): this is a pointer store
  - For MVP: `FactToFact(base(value).*, base(addr).*)`

#### Key Design: Return Handling

`GoIRReturn(results)`:
- For each result value `results[i]`:
  - If tainted, emit `FactToFact(base(results[i]).*, Return.*)`
- Also apply exit source/sink rules here

#### `GoMethodSequentFlowFunction` Skeleton

```kotlin
class GoMethodSequentFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val currentInst: GoIRInst,
    private val generateTrace: Boolean,
) : MethodSequentFlowFunction {

    override fun propagateZeroToZero(): Set<Sequent> {
        return setOf(Sequent.ZeroToZero) + applyUnconditionalSources()
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> {
        return propagate(null, currentFactAp)
    }

    override fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<Sequent> {
        return propagate(initialFactAp, currentFactAp)
    }

    override fun propagateNDFactToFact(initialFacts: Set<InitialFactAp>, currentFactAp: FinalFactAp): Set<Sequent> {
        return setOf(Sequent.Unchanged)  // MVP: no ND support
    }

    private fun propagate(initialFact: InitialFactAp?, currentFact: FinalFactAp): Set<Sequent> {
        return when (currentInst) {
            is GoIRAssignInst -> sequentFlowAssign(initialFact, currentFact, currentInst)
            is GoIRStore -> sequentFlowStore(initialFact, currentFact, currentInst)
            is GoIRReturn -> propagateExitFact(initialFact, currentFact, currentInst)
            is GoIRPhi -> sequentFlowPhi(initialFact, currentFact, currentInst)
            is GoIRMapUpdate -> sequentFlowMapUpdate(initialFact, currentFact, currentInst)
            else -> setOf(Sequent.Unchanged)
        }
    }
}
```

### 6.3 Call Flow Function

**Purpose**: Handles taint propagation at call sites — maps facts from caller to callee and back.

#### What happens at a `GoIRCall(callInfo, register)`:

1. **Zero → Zero**: Always propagate (reachability)
2. **Zero → Zero into callee**: `CallToStartZeroFact`
3. **Zero → Source**: If the callee is a taint source, create `CallToReturnZFact` with the tainted return value
4. **Fact → Call-to-return**: If the fact is NOT passed to the callee (not an argument), preserve it across the call
5. **Fact → Call-to-start**: If the fact IS passed as an argument, map it into the callee
6. **Sink check**: If the callee is a sink and a tainted argument reaches it, report vulnerability

#### `GoMethodCallFlowFunction`

```kotlin
class GoMethodCallFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val returnValue: GoIRRegister?,   // LHS of the call assignment (null if void)
    private val callExpr: GoCallExpr,
    private val statement: GoIRInst,
    private val generateTrace: Boolean,
) : MethodCallFlowFunction {

    override fun propagateZeroToZero(): Set<ZeroCallFact> {
        val result = mutableSetOf<ZeroCallFact>(CallToReturnZeroFact, CallToStartZeroFact)
        
        // Apply source rules: if callee is a source, create taint on return value
        applySources(result)
        
        // Apply sink rules: unconditional sinks
        applySinks(result)
        
        return result
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> {
        return propagateZeroToZero()  // Same behavior for zero-based propagation
    }

    override fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<FactCallFact> {
        val result = mutableSetOf<FactCallFact>()
        
        // Check if fact is relevant to this call
        if (!factIsRelevant(currentFactAp)) {
            result.add(Unchanged)
            return result
        }
        
        // Apply sink rules (conditional: if tainted arg reaches sink)
        applyFactSinkRules(initialFactAp, currentFactAp, result)
        
        // Map fact to callee start position
        mapFactToCallee(initialFactAp, currentFactAp, result)
        
        // Apply pass-through rules
        applyPassRules(initialFactAp, currentFactAp, result)
        
        // Fact survives call (call-to-return) if not consumed
        result.add(CallToReturnFFact(initialFactAp, currentFactAp, null))
        
        return result
    }
}
```

#### Source rule application at call sites

When we encounter `t0 = source()`:
1. The function `source` matches a `TaintRules.Source(function="test.source", mark="taint", pos=Result)`
2. We create a `CallToReturnZFact` with:
   - `factAp = apManager.createAbstractAp(base(t0), ExclusionSet.Universe).prependAccessor(TaintMarkAccessor("taint"))`
   - This places a taint mark on the return value at the call site

#### Sink rule application at call sites

When we encounter `sink(other)`:
1. The function `sink` matches a `TaintRules.Sink(function="test.sink", mark="taint", pos=Argument(0), id="test-id")`
2. Check if argument 0's fact has taint mark "taint"
3. If yes, report vulnerability via `context.taint.taintSinkTracker.addVulnerability(...)`

---

## 7. Taint Rules Application

### 7.1 `GoTaintRulesProvider`

Bridges `GoTaintConfig` to rule queries used by flow functions:

```kotlin
class GoTaintRulesProvider(val config: GoTaintConfig) {

    fun sourceRulesForCall(calleeName: String): List<TaintRules.Source> =
        config.sources.filter { it.function == calleeName }

    fun sinkRulesForCall(calleeName: String): List<TaintRules.Sink> =
        config.sinks.filter { it.function == calleeName }

    fun passRulesForCall(calleeName: String): List<TaintRules.Pass> =
        config.propagators.filter { it.function == calleeName }
}
```

### 7.2 Position → AccessPathBase Mapping

The `PositionBase` variants map to `AccessPathBase` as follows:

| `PositionBase` | `AccessPathBase` | Context |
|---------------|-----------------|---------|
| `Result` | `Return` | Method's return value |
| `Argument(i)` | `Argument(i)` | Method's i-th parameter |
| `This` | `This` | Receiver (Go methods have receiver) |
| `ClassStatic(name)` | `ClassStatic` | Package-level globals |

### 7.3 How Source Rules Create Facts

When `TaintRules.Source(function="test.source", mark="taint", pos=Result)` matches:

```
factAp = createAbstractAp(base=AccessPathBase.Return, exclusions=ExclusionSet.Universe)
            .prependAccessor(TaintMarkAccessor("taint"))
```

At the call site, `Return` is rebased to the caller's variable:
```
Return.![taint]/* → LocalVar(t0).![taint]/*   (with Universe exclusions)
```

### 7.4 How Sink Rules Check Facts

When `TaintRules.Sink(function="test.sink", mark="taint", pos=Argument(0), id="test-id")` matches:

1. Look at the fact on `Argument(0)` at the call site
2. The call argument maps to some caller variable (e.g., `LocalVar(other)`)
3. Check if the fact on `LocalVar(other)` carries `TaintMarkAccessor("taint")`:
   - **Concrete**: `fact.startsWithAccessor(TaintMarkAccessor("taint"))` returns true
   - **Abstract**: `fact.isAbstract() && TaintMarkAccessor("taint") !in fact.exclusions` (from Lesson 2)
4. If yes, report `TaintVulnerabilityWithFact`

### 7.5 How Pass Rules Propagate Facts

When `TaintRules.Pass(function="strings.Replace", from=Argument(0), to=Result)` matches:

This means: if `Argument(0)` is tainted, then `Return` is also tainted.

At the call site, this creates:
- `CallToReturnFFact(initial=base(arg0).*, final=base(returnVar).*)`

---

## 8. GoCallExpr & GoCallFactMapper

### 8.1 `GoCallExpr`

The framework uses `CommonCallExpr` to represent call expressions. Go needs an adapter:

```kotlin
class GoCallExpr(
    val callInfo: GoIRCallInfo,
    val method: GoIRFunction?,      // resolved callee (null if unresolved)
) : CommonCallExpr {
    override val args: List<CommonValue>
        get() = callInfo.args.map { it as CommonValue }

    override val typeName: String
        get() = callInfo.resultType.displayName
}
```

For method calls (receiver-based), we also implement `CommonInstanceCallExpr`:

```kotlin
class GoInstanceCallExpr(
    callInfo: GoIRCallInfo,
    method: GoIRFunction?,
    override val instance: CommonValue,
) : GoCallExpr(callInfo, method), CommonInstanceCallExpr
```

### 8.2 `GoLanguageManager.getCallExpr()` Implementation

```kotlin
override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
    val goInst = inst as GoIRInst
    val callInfo = when (goInst) {
        is GoIRCall -> goInst.callInfo
        is GoIRGo -> goInst.callInfo
        is GoIRDefer -> goInst.callInfo
        else -> return null
    }
    val callee = resolveCallee(callInfo)  // uses GoCallResolver
    return if (callInfo.receiver != null) {
        GoInstanceCallExpr(callInfo, callee, callInfo.receiver as CommonValue)
    } else {
        GoCallExpr(callInfo, callee)
    }
}
```

### 8.3 `GoMethodCallFactMapper`

Maps facts between caller and callee namespaces, analogous to `JIRMethodCallFactMapper`:

```kotlin
object GoMethodCallFactMapper : MethodCallFactMapper {

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) {
        val goCallee = callee as GoIRFunction
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo

        // Map receiver to This (for method calls)
        if (goCallee.isMethod && callInfo.receiver != null) {
            val receiverBase = GoFlowFunctionUtils.accessPathBase(callInfo.receiver!!, ...)
            if (receiverBase != null && factAp.base == receiverBase) {
                onMappedFact(factAp.rebase(AccessPathBase.This), AccessPathBase.This)
            }
        }

        // Map arguments to Argument(i)
        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBase(arg, ...)
            if (argBase != null && factAp.base == argBase) {
                onMappedFact(factAp.rebase(AccessPathBase.Argument(i)), AccessPathBase.Argument(i))
            }
        }
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> {
        val goInst = callStatement as GoIRInst
        return when (factAp.base) {
            is AccessPathBase.Return -> {
                // Map Return to the call's result register
                val resultBase = getCallResultBase(goInst) ?: return emptyList()
                listOf(factAp.rebase(resultBase))
            }
            is AccessPathBase.Argument -> {
                // Map Argument(i) back to caller's argument
                val argBase = getCallArgBase(goInst, (factAp.base as AccessPathBase.Argument).idx)
                    ?: return emptyList()
                listOf(factAp.rebase(argBase))
            }
            is AccessPathBase.This -> {
                // Map This back to caller's receiver
                val recvBase = getCallReceiverBase(goInst) ?: return emptyList()
                listOf(factAp.rebase(recvBase))
            }
            else -> emptyList()
        }
    }

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp
    ): Boolean {
        // Check if factAp's base matches any call position (args, receiver, return)
        val goCallExpr = callExpr as GoCallExpr
        for (arg in goCallExpr.callInfo.args) {
            val argBase = GoFlowFunctionUtils.accessPathBase(arg, ...)
            if (argBase == factAp.base) return true
        }
        if (returnValue != null) {
            val retBase = GoFlowFunctionUtils.accessPathBase(returnValue as GoIRValue, ...)
            if (retBase == factAp.base) return true
        }
        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean {
        // LocalVar facts cannot escape method scope
        return factAp.base !is AccessPathBase.LocalVar
    }
}
```

---

## 9. Summary Handler

### `GoMethodCallSummaryHandler`

Applies callee summaries back to callers when callee analysis completes:

```kotlin
class GoMethodCallSummaryHandler(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val statement: GoIRInst,
) : MethodCallSummaryHandler {

    override val factTypeChecker: FactTypeChecker = FactTypeChecker.Dummy

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> {
        return GoMethodCallFactMapper.mapMethodExitToReturnFlowFact(
            statement, fact, factTypeChecker
        )
    }

    // Use default implementations for handleZeroToZero, handleZeroToFact,
    // handleFactToFact, handleNDFactToFact, handleSummary, prepareFactToFactSummary
    // The defaults delegate through mapMethodExitToReturnFlowFact
}
```

For the MVP, the default implementations in the `MethodCallSummaryHandler` interface are sufficient. They handle:
- Mapping exit facts through `mapMethodExitToReturnFlowFact()`
- Creating `Sequent.FactToFact` edges in the caller
- The `handleSummary` method handles alias-aware refinement (less relevant for Go MVP)

---

## 10. Remaining Entities

### 10.1 `GoLanguageManager` — Complete the Stubs

```kotlin
open class GoLanguageManager(val cp: GoIRProgram) : LanguageManager {
    // Already implemented:
    // getInstIndex, getMaxInstIndex, getInstByIndex, isEmpty

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        // Extract GoCallExpr from GoIRCall/GoIRGo/GoIRDefer
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        return inst is GoIRPanic  // Go's equivalent of throw
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        return (callExpr as GoCallExpr).method
            ?: error("Unresolved callee")
    }

    override val methodContextSerializer: MethodContextSerializer
        get() = DummyMethodContextSerializer  // MVP: no serialization
}
```

### 10.2 `GoApplicationGraph` — Complete Inter-procedural Stubs

```kotlin
class GoApplicationGraph(val cp: GoIRProgram) : ApplicationGraph<GoIRFunction, GoIRInst> {
    private val callResolver = GoCallResolver(cp)

    override fun callees(node: GoIRInst): Sequence<GoIRFunction> {
        val callInfo = extractCallInfo(node) ?: return emptySequence()
        return callResolver.resolve(callInfo, node).asSequence()
    }

    override fun callers(method: GoIRFunction): Sequence<GoIRInst> {
        // For MVP: scan all functions for calls to this method
        // Future: build reverse call graph during initialization
        return cp.packages.values.asSequence()
            .flatMap { it.functions.asSequence() }
            .filter { it.body != null }
            .flatMap { func ->
                func.body!!.instructions.asSequence().filter { inst ->
                    val callInfo = extractCallInfo(inst)
                    callInfo != null && callResolver.resolve(callInfo, inst).contains(method)
                }
            }
    }

    private fun extractCallInfo(inst: GoIRInst): GoIRCallInfo? = when (inst) {
        is GoIRCall -> inst.callInfo
        is GoIRGo -> inst.callInfo
        is GoIRDefer -> inst.callInfo
        else -> null
    }
}
```

### 10.3 `FactTypeChecker` — MVP Dummy

For the prototype, use `FactTypeChecker.Dummy` which accepts all facts. Go-specific type checking (e.g., validating field accessors against struct types) is future work.

### 10.4 `MethodStartPrecondition` / `MethodSequentPrecondition` / `MethodCallPrecondition`

These are used for trace resolution (finding the source-to-sink path). For MVP, return minimal/empty results:

```kotlin
// GoMethodStartPrecondition
override fun factPrecondition(fact: InitialFactAp) = emptyList<TaintRulePrecondition.Source>()

// GoMethodSequentPrecondition
override fun factPrecondition(fact: InitialFactAp) = setOf(SequentPrecondition.Unchanged)

// GoMethodCallPrecondition
override fun factPrecondition(fact: InitialFactAp) = listOf(CallPrecondition.Unchanged)
```

### 10.5 `MethodSideEffectSummaryHandler`

For MVP, return empty results (no cross-unit side effects):

```kotlin
class GoMethodSideEffectSummaryHandler : MethodSideEffectSummaryHandler {
    // All methods use default implementations that return emptySet()
}
```

### 10.6 `isReachable` and `isValidMethodExitFact`

```kotlin
override fun isReachable(apManager, analysisContext, base, statement): Boolean {
    // In SSA form, all defined registers are reachable at their use points
    return true  // MVP: always reachable
}

override fun isValidMethodExitFact(apManager, analysisContext, fact): Boolean {
    return GoMethodCallFactMapper.isValidMethodExitFact(fact)
}
```

### 10.7 `onInstructionReached`

```kotlin
override fun onInstructionReached(inst: CommonInst) {
    // No-op for MVP. Future: track lambda/closure allocations
}
```

### 10.8 `DummyMethodContextSerializer`

```kotlin
object DummyMethodContextSerializer : MethodContextSerializer {
    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        // No-op
    }
    override fun DataInputStream.readMethodContext(): MethodContext {
        return EmptyMethodContext
    }
}
```

### 10.9 Go IR ↔ Common IR Bridge

The Go IR types need to implement `Common*` interfaces where they don't already:

- `GoIRFunction` already implements `CommonMethod`
- `GoIRInst` → needs to provide `CommonInst.location` returning `CommonInstLocation`
- `GoIRValue` already implements `CommonValue`
- `GoIRCall` → could implement `CommonCallInst`
- `GoIRReturn` → could implement `CommonReturnInst`

Check if `GoIRInst` already provides `CommonInstLocation`. The `GoInstLocation` has `functionBody` which gives access to the function. This should suffice:

```kotlin
// In GoInstLocation or a wrapper
val method: CommonMethod get() = functionBody.function
```

---

## 11. Implementation Priority

### Phase 1: Minimal Working Prototype (Goal: pass `SampleTest`)

The two test cases only require:
1. Intraprocedural assignment propagation (`var data = source()`, `var other = data`)
2. Source rule application at call sites (`source()` → taint on return value)
3. Sink rule checking at call sites (`sink(other)` → detect taint)
4. Direct call resolution (DIRECT mode) and interface dispatch (INVOKE mode)

**Required implementations (in order):**

| # | Component | Effort | Notes |
|---|-----------|--------|-------|
| 1 | `GoFlowFunctionUtils` | Medium | `accessPathBase()` and `mkAccess()` |
| 2 | `GoCallExpr` | Small | Adapter for `CommonCallExpr` |
| 3 | `GoLanguageManager` (complete stubs) | Small | `getCallExpr`, `producesExceptionalControlFlow`, `getCalleeMethod` |
| 4 | `GoCallResolver` | Medium | DIRECT + INVOKE resolution (with interface→implementors map) |
| 5 | `GoApplicationGraph` (complete stubs) | Small | `callees()` and `callers()` |
| 6 | `GoTaintRulesProvider` | Small | Rule lookup by function name |
| 7 | `GoMethodAnalysisContext` | Small | Minimal context |
| 8 | `GoMethodCallFactMapper` | Medium | Caller ↔ callee fact mapping |
| 9 | `GoMethodStartFlowFunction` | Small | Zero propagation (entry-point rules deferred) |
| 10 | `GoMethodSequentFlowFunction` | Large | Assignment/return handling |
| 11 | `GoMethodCallFlowFunction` | Large | Source/sink/pass-through at calls |
| 12 | `GoMethodCallSummaryHandler` | Small | Uses default impls mostly |
| 13 | `GoAnalysisManager` (wire everything) | Medium | Create all components |
| 14 | Dummy preconditions & side-effect handler | Small | Empty implementations |

### Phase 2: Enhanced Prototype

- Entry-point taint rules (add `entryPointSources` to `GoTaintConfig`)
- Field-sensitive analysis (struct field tracking)
- Pointer alias analysis (GoIRStore handling)
- Dynamic call resolution (function values, closures)
- Phi node handling
- Go-specific type checking (`GoFactTypeChecker`)
- Method receiver taint propagation
- Multi-return value handling (via `GoIRExtractExpr`)
- Signature-level matching for INVOKE resolution (not just method names)

### Phase 3: Production Features

- Cross-package analysis
- Goroutine analysis (GoIRGo, GoIRSend/channel operations)
- Deferred call analysis (GoIRDefer)
- Panic/recover flow
- Full closure analysis (free variables, closure creation)
- Summary serialization/caching
- Performance optimization (parallel analysis units)
