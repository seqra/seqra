# 2. Go IR Utilities — `GoFlowFunctionUtils`

> **File**: `org/opentaint/dataflow/go/GoFlowFunctionUtils.kt`
>
> This is the foundational utility that every flow function depends on.
> It maps Go IR values and expressions to the framework's `AccessPathBase` and `Accessor` types.

---

## 2.1 Access Sealed Interface

Mirrors JVM's `MethodFlowFunctionUtils.Access`:

```kotlin
sealed interface Access {
    val base: AccessPathBase

    data class Simple(override val base: AccessPathBase) : Access

    data class RefAccess(
        override val base: AccessPathBase,
        val accessor: Accessor,
    ) : Access
}
```

**No `StaticRefAccess`** — Go has no JVM-like static fields. Package-level globals use `ClassStatic` base with a `FieldAccessor` via `RefAccess`.

---

## 2.2 `accessPathBase(value: GoIRValue, method: GoIRFunction): AccessPathBase?`

Maps a Go IR value to a framework access path base.

```kotlin
fun accessPathBase(value: GoIRValue, method: GoIRFunction): AccessPathBase? {
    return when (value) {
        is GoIRParameterValue -> AccessPathBase.Argument(value.paramIndex)
        is GoIRRegister       -> AccessPathBase.LocalVar(value.index)
        is GoIRConstValue     -> AccessPathBase.Constant(value.type.displayName, value.value.toString())
        is GoIRGlobalValue    -> AccessPathBase.ClassStatic  // further qualified by FieldAccessor
        is GoIRFunctionValue  -> AccessPathBase.Constant("func", value.function.fullName)
        is GoIRBuiltinValue   -> AccessPathBase.Constant("builtin", value.name)
        is GoIRFreeVarValue   -> {
            // Closure free variables: treat as Argument(paramCount + freeVarIndex)
            // This maps them into the callee's argument space, after regular params.
            // The closure creation in the caller (GoIRMakeClosureExpr) provides the bindings.
            val paramCount = method.params.size
            AccessPathBase.Argument(paramCount + value.freeVarIndex)
        }
    }
}
```

### Design Notes

- **`GoIRParameterValue`** always maps to `Argument(paramIndex)`. This is critical for the framework's summary matching — summaries use `Argument(i)` as initial bases, and the caller subscriptions expect `Argument(i)`.

- **`GoIRRegister`** always maps to `LocalVar(index)`. The `index` field is the register's unique identifier within the function. No ambiguity with parameters — Go SSA cleanly separates them.

- **`GoIRGlobalValue`** maps to `ClassStatic`. When used in a field-like context, a `FieldAccessor` is added by `mkAccess`. The `FieldAccessor` uses `globalValue.global.pkg.importPath` as className, `globalValue.global.name` as fieldName, and the global's type as fieldType.

- **`GoIRFreeVarValue`** — closures capture variables as free vars. In Go SSA, a `GoIRMakeClosureExpr` binds concrete values to each free var. By mapping free vars to `Argument(paramCount + idx)`, the framework's interprocedural machinery naturally connects the closure's bindings to the free var arguments. The `GoMethodCallFactMapper` must handle this when mapping facts for closure calls.

---

## 2.3 `mkAccess(inst: GoIRInst, method: GoIRFunction): Pair<Access, Access>?`

For instructions that represent data movement (assignments, stores), returns a (destination, source) pair. Returns `null` for instructions that don't propagate data.

### 2.3.1 `GoIRAssignInst`

```kotlin
is GoIRAssignInst -> {
    val lhs = Access.Simple(AccessPathBase.LocalVar(inst.register.index))
    val rhs = exprToAccess(inst.expr, method)
    if (rhs != null) Pair(lhs, rhs) else null
}
```

The LHS is always a `Simple(LocalVar(register.index))` because `GoIRAssignInst` always writes to a `GoIRRegister`.

### 2.3.2 `GoIRStore`

```kotlin
is GoIRStore -> {
    val addrAccess = accessForAddr(inst.addr, method) ?: return null
    val valueBase = accessPathBase(inst.value, method) ?: return null
    Pair(addrAccess, Access.Simple(valueBase))
}
```

`GoIRStore` writes `value` into memory at `addr`. The destination depends on how `addr` was produced:

```kotlin
private fun accessForAddr(addr: GoIRValue, method: GoIRFunction): Access? {
    // addr is typically a register produced by FieldAddrExpr or IndexAddrExpr
    if (addr !is GoIRRegister) {
        return Access.Simple(accessPathBase(addr, method) ?: return null)
    }
    // Look up the defining instruction for this register
    val defInst = findDefInst(addr, method) ?: return Access.Simple(AccessPathBase.LocalVar(addr.index))

    return when (val expr = (defInst as? GoIRAssignInst)?.expr) {
        is GoIRFieldAddrExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            val accessor = fieldAccessor(expr, method)
            Access.RefAccess(base, accessor)
        }
        is GoIRIndexAddrExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            Access.RefAccess(base, ElementAccessor)
        }
        else -> Access.Simple(AccessPathBase.LocalVar(addr.index))
    }
}
```

### 2.3.3 `GoIRMapUpdate`

```kotlin
is GoIRMapUpdate -> {
    val mapBase = accessPathBase(inst.map, method) ?: return null
    val valueBase = accessPathBase(inst.value, method) ?: return null
    Pair(
        Access.RefAccess(mapBase, ElementAccessor),
        Access.Simple(valueBase)
    )
}
```

---

## 2.4 `exprToAccess(expr: GoIRExpr, method: GoIRFunction): Access?`

Maps a Go IR expression to an `Access`. This is the core expression dispatcher.

```kotlin
fun exprToAccess(expr: GoIRExpr, method: GoIRFunction): Access? {
    return when (expr) {
        // --- Field access ---
        is GoIRFieldExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            Access.RefAccess(base, fieldAccessor(expr, method))
        }
        is GoIRFieldAddrExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            Access.RefAccess(base, fieldAccessor(expr, method))
        }

        // --- Index/element access ---
        is GoIRIndexExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            Access.RefAccess(base, ElementAccessor)
        }
        is GoIRIndexAddrExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            Access.RefAccess(base, ElementAccessor)
        }
        is GoIRLookupExpr -> {
            val base = accessPathBase(expr.x, method) ?: return null
            Access.RefAccess(base, ElementAccessor)
        }

        // --- Conversions/wrapping (preserve taint) ---
        is GoIRChangeTypeExpr      -> singleOperandAccess(expr.x, method)
        is GoIRConvertExpr         -> singleOperandAccess(expr.x, method)
        is GoIRMultiConvertExpr    -> singleOperandAccess(expr.x, method)
        is GoIRChangeInterfaceExpr -> singleOperandAccess(expr.x, method)
        is GoIRMakeInterfaceExpr   -> singleOperandAccess(expr.x, method)
        is GoIRTypeAssertExpr      -> singleOperandAccess(expr.x, method)
        is GoIRSliceToArrayPointerExpr -> singleOperandAccess(expr.x, method)

        // --- Pointer ops ---
        is GoIRUnOpExpr -> when (expr.op) {
            GoIRUnaryOp.DEREF -> singleOperandAccess(expr.x, method)   // *ptr → same taint
            GoIRUnaryOp.ARROW -> singleOperandAccess(expr.x, method)   // <-chan → taint from channel
            else -> null  // NOT, NEG, XOR — arithmetic/logic kills taint
        }

        // --- Slice of array/slice (sub-view preserves taint) ---
        is GoIRSliceExpr -> singleOperandAccess(expr.x, method)

        // --- Range iteration (iterator carries taint from container) ---
        is GoIRRangeExpr -> singleOperandAccess(expr.x, method)
        is GoIRNextExpr  -> singleOperandAccess(expr.iter, method)

        // --- Tuple extract (multi-return value) ---
        is GoIRExtractExpr -> singleOperandAccess(expr.tuple, method)

        // --- Binary op: string concatenation preserves taint, arithmetic doesn't ---
        is GoIRBinOpExpr -> {
            if (expr.op == GoIRBinaryOp.ADD && isStringType(expr.type)) {
                // String concatenation: taint flows from either operand
                // Return left operand; the flow function will also check right operand
                singleOperandAccess(expr.x, method)
            } else {
                null  // Arithmetic/logic/comparison kills taint
            }
        }

        // --- Allocations: no taint source ---
        is GoIRAllocExpr       -> null
        is GoIRMakeSliceExpr   -> null
        is GoIRMakeMapExpr     -> null
        is GoIRMakeChanExpr    -> null

        // --- Closure creation: taint if any binding is tainted ---
        is GoIRMakeClosureExpr -> {
            // For MVP: if any binding is tainted, the closure is tainted.
            // The bindings map to the closure's free vars.
            // More precise: track each binding → free var individually.
            // We return the first binding as the access; the flow function
            // handles multi-operand propagation for closures specifically.
            if (expr.bindings.isNotEmpty()) {
                singleOperandAccess(expr.bindings.first(), method)
            } else {
                null
            }
        }

        // --- Select: complex, for MVP treat result as opaque ---
        is GoIRSelectExpr -> null
    }
}

private fun singleOperandAccess(value: GoIRValue, method: GoIRFunction): Access? {
    val base = accessPathBase(value, method) ?: return null
    return Access.Simple(base)
}
```

---

## 2.5 `fieldAccessor` Helpers

```kotlin
fun fieldAccessor(expr: GoIRFieldExpr, method: GoIRFunction): FieldAccessor {
    val structTypeName = resolveStructTypeName(expr.x.type)
    return FieldAccessor(structTypeName, expr.fieldName, expr.type.displayName)
}

fun fieldAccessor(expr: GoIRFieldAddrExpr, method: GoIRFunction): FieldAccessor {
    val structTypeName = resolveStructTypeName(expr.x.type)
    return FieldAccessor(structTypeName, expr.fieldName, expr.type.displayName)
}

fun fieldAccessorForStore(field: GoIRFieldAddrExpr, method: GoIRFunction): FieldAccessor {
    // Same as above; Store uses FieldAddrExpr to get address
    return fieldAccessor(field, method)
}

private fun resolveStructTypeName(type: GoIRType): String {
    return when (type) {
        is GoIRNamedTypeRef -> type.namedType.fullName
        is GoIRPointerType -> resolveStructTypeName(type.elem)
        is GoIRStructType -> type.namedType?.fullName ?: "anonymous"
        else -> type.displayName
    }
}
```

---

## 2.6 `findDefInst` — Register Defining Instruction Lookup

Used by `accessForAddr` to trace where an address register was produced (e.g., was it a `GoIRFieldAddrExpr`?).

```kotlin
fun findDefInst(register: GoIRRegister, method: GoIRFunction): GoIRDefInst? {
    val body = method.body ?: return null
    // GoIRRegister.index is the instruction index of the defining GoIRDefInst
    // In Go SSA, each register is defined by exactly one GoIRDefInst
    return body.instructions.getOrNull(register.index) as? GoIRDefInst
}
```

> **Important**: In Go SSA IR, `GoIRRegister.index` corresponds to the index of the `GoIRDefInst` that defines it. Each register is defined exactly once (SSA property). So `body.instructions[register.index]` is the defining instruction. This needs to be verified against the actual Go IR implementation — if `register.index` is a separate counter from instruction indices, we need to scan `body.instructions` for the `GoIRDefInst` whose `register` matches.

---

## 2.7 Utility Functions

```kotlin
fun isStringType(type: GoIRType): Boolean {
    return type is GoIRBasicType && type.kind == GoIRBasicTypeKind.STRING
}

fun isCallInst(inst: GoIRInst): Boolean {
    return inst is GoIRCall || inst is GoIRGo || inst is GoIRDefer
}

fun extractCallInfo(inst: GoIRInst): GoIRCallInfo? {
    return when (inst) {
        is GoIRCall  -> inst.call   // property name is `.call`, not `.callInfo`
        is GoIRGo    -> inst.call
        is GoIRDefer -> inst.call
        else -> null
    }
}

fun extractResultRegister(inst: GoIRInst): GoIRRegister? {
    return when (inst) {
        is GoIRCall -> inst.register
        else -> null
    }
}
```

---

## 2.8 Position Resolution

Maps `PositionBase` (from taint rules) to `AccessPathBase` at a specific call site.

```kotlin
fun resolvePosition(pos: PositionBase): AccessPathBase {
    return when (pos) {
        is PositionBase.Result -> AccessPathBase.Return
        is PositionBase.Argument -> AccessPathBase.Argument(pos.idx ?: 0)
        is PositionBase.This -> AccessPathBase.This
        is PositionBase.ClassStatic -> AccessPathBase.ClassStatic
        is PositionBase.AnyArgument -> AccessPathBase.Argument(0) // MVP: treat as arg 0
    }
}

fun resolvePositionWithModifiers(pos: PositionBaseWithModifiers): Pair<AccessPathBase, List<Accessor>> {
    val base = resolvePosition(pos.base)
    val accessors = when (pos) {
        is PositionBaseWithModifiers.BaseOnly -> emptyList()
        is PositionBaseWithModifiers.WithModifiers -> pos.modifiers.map { mod ->
            when (mod) {
                is PositionModifier.ArrayElement -> ElementAccessor
                is PositionModifier.AnyField -> AnyAccessor
                is PositionModifier.Field -> FieldAccessor(mod.className, mod.fieldName, mod.fieldType)
            }
        }
    }
    return Pair(base, accessors)
}
```

---

## 2.9 Binary Op Taint Propagation (String Concat)

The `GoIRBinOpExpr` with `ADD` on strings deserves special handling in the sequent flow function. When `exprToAccess` returns the left operand for string concat, the sequent flow function must also check the right operand:

```kotlin
// In GoMethodSequentFlowFunction, when handling GoIRAssignInst with GoIRBinOpExpr(ADD, string):
if (expr is GoIRBinOpExpr && expr.op == GoIRBinaryOp.ADD && isStringType(expr.type)) {
    val leftBase = accessPathBase(expr.x, method)
    val rightBase = accessPathBase(expr.y, method)
    // If currentFact matches either operand, propagate to register
    if (leftBase != null && currentFact.base == leftBase) { /* propagate */ }
    if (rightBase != null && currentFact.base == rightBase) { /* propagate */ }
}
```

This special case is handled inside the sequent flow function, not in `exprToAccess`.
