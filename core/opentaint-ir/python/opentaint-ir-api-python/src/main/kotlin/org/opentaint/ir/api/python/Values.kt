package org.opentaint.ir.api.python

/**
 * A value used in instructions (operand or result).
 * Analogous to JIRValue / JIRImmediate / JIRRef.
 *
 * Values are also expressions (PIRExpr), since they can appear as the
 * right-hand side of PIRAssign instructions.
 */
sealed interface PIRValue : PIRExpr, org.opentaint.ir.api.common.cfg.CommonValue {
    val type: PIRType
    override val typeName: String get() = type.typeName
    fun <T> accept(visitor: PIRValueVisitor<T>): T
}

// ─── Locals & Parameters ────────────────────────────────────

/** A local variable or temporary (e.g., "$t0", "result"). */
data class PIRLocal(
    val name: String,
    override val type: PIRType,
) : PIRValue {
    override fun toString(): String = name
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitLocal(this)
}

/**
 * Reference to a function parameter, identified by [name].
 *
 * Emitted by the parameter-binding prologue at function entry — one
 * `PIRAssign(PIRLocal(name), PIRParameterRef(name))` per parameter — and by
 * the closure rewriter's prologue when reading the synthetic `<self>` env
 * parameter on capturing children. Consumers that need a parameter index
 * recover it by name lookup against the enclosing function's
 * [PIRFunction.parameters]; the closure rewriter prepends a `<self>`
 * parameter, so storing an index here would force every emitter to track
 * the post-rewrite signature.
 */
data class PIRParameterRef(
    val name: String,
    override val type: PIRType,
) : PIRValue {
    override fun toString(): String = name
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitParameterRef(this)
}

// ─── Constants ──────────────────────────────────────────────

sealed interface PIRConst : PIRValue

data class PIRIntConst(val value: Long) : PIRConst {
    override val type: PIRType get() = PIRClassType("builtins.int")
    override fun toString(): String = value.toString()
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitIntConst(this)
}

data class PIRFloatConst(val value: Double) : PIRConst {
    override val type: PIRType get() = PIRClassType("builtins.float")
    override fun toString(): String = value.toString()
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitFloatConst(this)
}

data class PIRStrConst(val value: String) : PIRConst {
    override val type: PIRType get() = PIRClassType("builtins.str")
    override fun toString(): String = "\"$value\""
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitStrConst(this)
}

data class PIRBoolConst(val value: Boolean) : PIRConst {
    override val type: PIRType get() = PIRClassType("builtins.bool")
    override fun toString(): String = if (value) "True" else "False"
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitBoolConst(this)
}

data object PIRNoneConst : PIRConst {
    override val type: PIRType = PIRNoneType
    override fun toString(): String = "None"
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitNoneConst(this)
}

data object PIREllipsisConst : PIRConst {
    override val type: PIRType = PIRAnyType
    override fun toString(): String = "..."
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitEllipsisConst(this)
}

data class PIRBytesConst(val value: ByteArray) : PIRConst {
    override val type: PIRType get() = PIRClassType("builtins.bytes")
    override fun equals(other: Any?): Boolean =
        other is PIRBytesConst && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = "b\"...\""
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitBytesConst(this)
}

data class PIRComplexConst(val real: Double, val imag: Double) : PIRConst {
    override val type: PIRType get() = PIRClassType("builtins.complex")
    override fun toString(): String = "${real}+${imag}j"
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitComplexConst(this)
}

// ─── Global References ──────────────────────────────────────

/**
 * Reference to a global variable or imported name, identified by its
 * canonical qualified name. The single string is the resolution key —
 * consumers that need a module / simple-name split do it themselves.
 */
data class PIRGlobalRef(
    val qualifiedName: String,
    override val type: PIRType = PIRAnyType,
) : PIRValue {
    override fun toString(): String = qualifiedName
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitGlobalRef(this)
}

/**
 * Reference to a module itself (e.g. `os` in `os.getcwd()`). Distinct
 * from [PIRGlobalRef], which names a value inside a module.
 *
 * `module` is the canonical module fullname; any source-level alias has
 * been resolved away during lowering.
 */
data class PIRModuleRef(
    val module: String,
    override val type: PIRType = PIRAnyType,
) : PIRValue {
    override fun toString(): String = module
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitModuleRef(this)
}
