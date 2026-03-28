# 3. Call Resolution & Application Graph

---

## 3.1 `GoCallExpr` — CommonCallExpr Adapter

> **File**: `org/opentaint/dataflow/go/GoCallExpr.kt`

Wraps `GoIRCallInfo` to satisfy the framework's `CommonCallExpr` interface.

```kotlin
package org.opentaint.dataflow.go

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInstanceCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.value.GoIRValue

open class GoCallExpr(
    val callInfo: GoIRCallInfo,
    val resolvedCallee: GoIRFunction?,
) : CommonCallExpr {
    override val args: List<CommonValue>
        get() = callInfo.args.map { it as CommonValue }

    override val typeName: String
        get() = callInfo.resultType.displayName

    /**
     * Callee function name for rule matching.
     * For DIRECT: the function's full name (e.g., "test.source")
     * For INVOKE: "pkg.Type.methodName" derived from receiver type + method name
     * For DYNAMIC: null (unresolved)
     */
    val calleeName: String?
        get() = resolvedCallee?.fullName
}

class GoInstanceCallExpr(
    callInfo: GoIRCallInfo,
    resolvedCallee: GoIRFunction?,
    override val instance: CommonValue,
) : GoCallExpr(callInfo, resolvedCallee), CommonInstanceCallExpr
```

### Construction

In `GoLanguageManager.getCallExpr()`:
```kotlin
override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
    val goInst = inst as GoIRInst
    val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return null

    // For DIRECT calls, resolve callee immediately
    val callee = when (callInfo.mode) {
        GoIRCallMode.DIRECT -> {
            val funcValue = callInfo.function as? GoIRFunctionValue
            funcValue?.function
        }
        GoIRCallMode.INVOKE -> null  // multiple targets, resolved later by GoCallResolver
        GoIRCallMode.DYNAMIC -> null  // unresolved
    }

    return if (callInfo.receiver != null) {
        GoInstanceCallExpr(callInfo, callee, callInfo.receiver as CommonValue)
    } else {
        GoCallExpr(callInfo, callee)
    }
}
```

---

## 3.2 `GoCallResolver` — Low-Level Call Resolution

> **File**: `org/opentaint/dataflow/go/GoCallResolver.kt`

Resolves Go function calls. Handles three call modes: DIRECT, INVOKE, DYNAMIC.

```kotlin
class GoCallResolver(val cp: GoIRProgram) {

    /**
     * Pre-computed: for each interface named type, all concrete types implementing it.
     * Built once on first access (lazy), then O(1) lookups.
     */
    private val interfaceImplementors: Map<String, List<GoIRNamedType>> by lazy {
        buildInterfaceImplementorsMap()
    }

    fun resolve(call: GoIRCallInfo, location: GoIRInst): List<GoIRFunction> {
        return when (call.mode) {
            GoIRCallMode.DIRECT  -> resolveDirect(call)
            GoIRCallMode.INVOKE  -> resolveInvoke(call)
            GoIRCallMode.DYNAMIC -> emptyList()  // MVP: unresolved
        }
    }
```

### 3.2.1 DIRECT Resolution

```kotlin
    private fun resolveDirect(call: GoIRCallInfo): List<GoIRFunction> {
        val funcValue = call.function as? GoIRFunctionValue ?: return emptyList()
        // funcValue.function is already a resolved GoIRFunction reference
        return listOf(funcValue.function)
    }
```

Note: We use `funcValue.function` directly rather than `cp.findFunctionByFullName()`. The Go IR client resolves function references during deserialization, so `GoIRFunctionValue.function` already points to the correct `GoIRFunction` object.

### 3.2.2 INVOKE Resolution (Interface Dispatch)

```kotlin
    private fun resolveInvoke(call: GoIRCallInfo): List<GoIRFunction> {
        val methodName = call.methodName ?: return emptyList()
        val receiverType = call.receiver?.type ?: return emptyList()

        // Resolve to the interface's named type
        val interfaceFullName = resolveInterfaceFullName(receiverType) ?: return emptyList()

        // Find all concrete types implementing this interface
        val implementors = interfaceImplementors[interfaceFullName] ?: return emptyList()

        // Collect the matching method from each implementor
        return implementors.mapNotNull { concreteType ->
            concreteType.methodByName(methodName)
        }
    }

    private fun resolveInterfaceFullName(type: GoIRType): String? {
        return when (type) {
            is GoIRNamedTypeRef -> {
                if (type.namedType.kind == GoIRNamedTypeKind.INTERFACE) type.namedType.fullName
                else null
            }
            is GoIRInterfaceType -> type.namedType?.fullName
            else -> null
        }
    }
```

### 3.2.3 Interface→Implementors Map

```kotlin
    private fun buildInterfaceImplementorsMap(): Map<String, List<GoIRNamedType>> {
        val allTypes = cp.packages.values.flatMap { it.namedTypes }
        val interfaces = allTypes.filter { it.kind == GoIRNamedTypeKind.INTERFACE }
        val concreteTypes = allTypes.filter { it.kind != GoIRNamedTypeKind.INTERFACE }

        return interfaces.associate { iface ->
            val requiredMethods = collectInterfaceMethodNames(iface)
            val implementors = concreteTypes.filter { concrete ->
                val concreteMethods = concrete.allMethods().map { it.name }.toSet()
                concreteMethods.containsAll(requiredMethods)
            }
            iface.fullName to implementors
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

### Design Notes

- **Structural subtyping**: Go uses duck typing — a type implements an interface if it has all the interface's methods with matching names. The MVP checks method name matching only (not signature-level). This can cause over-approximation (more callees than necessary) but never unsoundness.

- **Map keyed by `fullName`**: Using the interface's `fullName` (e.g., `"io.Reader"`) as key instead of the `GoIRNamedType` object. This avoids identity issues if named type objects are recreated.

- **Package scope**: The implementors scan covers ALL packages in the program (`cp.packages.values`). This is correct — a struct in any package can implement an interface from any other package.

---

## 3.3 `GoMethodCallResolver` — Framework Adapter

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodCallResolver.kt`

Wraps `GoCallResolver` to implement the framework's `MethodCallResolver` interface.

```kotlin
class GoMethodCallResolver(
    private val callResolver: GoCallResolver,
    private val cp: GoIRProgram,
) : MethodCallResolver {

    override fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
    ) {
        val goCallExpr = callExpr as GoCallExpr
        val resolved = callResolver.resolve(goCallExpr.callInfo, location as GoIRInst)

        if (resolved.isEmpty()) {
            failureHandler.handle(callExpr)
        } else {
            for (callee in resolved) {
                handler.handle(MethodWithContext(callee, EmptyMethodContext))
            }
        }
    }

    override fun resolvedMethodCalls(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst
    ): List<MethodWithContext> {
        val goCallExpr = callExpr as GoCallExpr
        val resolved = callResolver.resolve(goCallExpr.callInfo, location as GoIRInst)
        return resolved.map { MethodWithContext(it, EmptyMethodContext) }
    }
}
```

### Design Notes

- **`EmptyMethodContext`** — For the MVP, all method contexts are empty. The JVM engine uses `JIRInstanceTypeMethodContext` to constrain virtual dispatch by the allocation site's type. Go doesn't need this for DIRECT calls, and for INVOKE calls the over-approximation is acceptable.

- **Resolution failure** — When `resolve()` returns empty (DYNAMIC calls), `failureHandler.handle()` is called. This triggers the call flow function's pass-through-unresolved behavior — facts survive the call via `CallToReturnFFact`.

---

## 3.4 `GoApplicationGraph` — Complete Stubbed Methods

> **File**: `org/opentaint/dataflow/go/graph/GoApplicationGraph.kt` (modify existing)

### `callees(node: GoIRInst)`

```kotlin
class GoApplicationGraph(val cp: GoIRProgram) : ApplicationGraph<GoIRFunction, GoIRInst> {

    internal val callResolver = GoCallResolver(cp)

    override fun callees(node: GoIRInst): Sequence<GoIRFunction> {
        val callInfo = GoFlowFunctionUtils.extractCallInfo(node) ?: return emptySequence()
        return callResolver.resolve(callInfo, node).asSequence()
    }
```

### `callers(method: GoIRFunction)`

```kotlin
    override fun callers(method: GoIRFunction): Sequence<GoIRInst> {
        // Scan all functions for call instructions that resolve to `method`.
        // This is O(n) across all instructions — acceptable for MVP.
        // Future: build a reverse call graph during initialization.
        return cp.packages.values.asSequence()
            .flatMap { pkg -> pkg.functions.asSequence() + pkg.namedTypes.asSequence().flatMap { it.allMethods().asSequence() } }
            .filter { it.body != null }
            .flatMap { func ->
                func.body!!.instructions.asSequence().filter { inst ->
                    val callInfo = GoFlowFunctionUtils.extractCallInfo(inst)
                    callInfo != null && callResolver.resolve(callInfo, inst).any { it == method }
                }
            }
    }
```

### Design Notes

- **`callers()` includes methods from named types**: Regular functions (`pkg.functions`) AND methods on named types (`namedType.allMethods()`) must be scanned. Otherwise, method-to-method calls would be missed.

- **Performance**: `callers()` is O(N) where N is total instructions across all packages. For production, a reverse call graph should be pre-built in `GoCallResolver` initialization. For MVP with ~264 test functions, this is fine.

- **`callResolver` is `internal`**: Exposed as `internal` so `GoAnalysisManager` can share the same resolver instance with `GoMethodCallResolver`.

---

## 3.5 `GoLanguageManager` — Complete Remaining Stubs

> **File**: `org/opentaint/dataflow/go/GoLanguageManager.kt` (modify existing)

```kotlin
open class GoLanguageManager(val cp: GoIRProgram) : LanguageManager {

    // Existing (unchanged):
    // getInstIndex, getMaxInstIndex, getInstByIndex, isEmpty

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        val goInst = inst as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return null

        val callee = when (callInfo.mode) {
            GoIRCallMode.DIRECT -> (callInfo.function as? GoIRFunctionValue)?.function
            else -> null
        }

        return if (callInfo.receiver != null) {
            GoInstanceCallExpr(callInfo, callee, callInfo.receiver as CommonValue)
        } else {
            GoCallExpr(callInfo, callee)
        }
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        return inst is GoIRPanic
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val goExpr = callExpr as GoCallExpr
        return goExpr.resolvedCallee
            ?: error("Cannot get callee for unresolved call: ${goExpr.callInfo}")
    }

    override val methodContextSerializer: MethodContextSerializer
        get() = DummyMethodContextSerializer
}
```

### Constructor Change

The existing `GoLanguageManager` has no constructor parameter. We need to add `cp: GoIRProgram`:

```kotlin
// Before: open class GoLanguageManager : LanguageManager
// After:  open class GoLanguageManager(val cp: GoIRProgram) : LanguageManager
```

`GoAnalysisManager` extends `GoLanguageManager` and already has `cp`:
```kotlin
// Before: class GoAnalysisManager(val cp: GoIRProgram) : GoLanguageManager(), TaintAnalysisManager
// After:  class GoAnalysisManager(cp: GoIRProgram) : GoLanguageManager(cp), TaintAnalysisManager
```

---

## 3.6 `DummyMethodContextSerializer`

> **File**: `org/opentaint/dataflow/go/DummyMethodContextSerializer.kt`

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
