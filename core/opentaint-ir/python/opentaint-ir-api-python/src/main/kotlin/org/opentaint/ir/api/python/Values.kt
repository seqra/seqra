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

/** Reference to a function parameter by name. */
data class PIRParameterRef(
    val name: String,
    override val type: PIRType,
) : PIRValue {
    override fun toString(): String = name
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitParameterRef(this)
}

// ─── Constants ──────────────────────────────────────────────

sealed interface PIRConst : PIRValue

data class PIRIntConst(val value: Long, override val type: PIRType = PIRClassType("builtins.int")) : PIRConst {
    override fun toString(): String = value.toString()
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitIntConst(this)
}

data class PIRFloatConst(val value: Double, override val type: PIRType = PIRClassType("builtins.float")) : PIRConst {
    override fun toString(): String = value.toString()
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitFloatConst(this)
}

data class PIRStrConst(val value: String, override val type: PIRType = PIRClassType("builtins.str")) : PIRConst {
    override fun toString(): String = "\"$value\""
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitStrConst(this)
}

data class PIRBoolConst(val value: Boolean, override val type: PIRType = PIRClassType("builtins.bool")) : PIRConst {
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

data class PIRBytesConst(val value: ByteArray, override val type: PIRType = PIRClassType("builtins.bytes")) : PIRConst {
    override fun equals(other: Any?): Boolean =
        other is PIRBytesConst && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = "b\"...\""
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitBytesConst(this)
}

data class PIRComplexConst(
    val real: Double,
    val imag: Double,
    override val type: PIRType = PIRClassType("builtins.complex"),
) : PIRConst {
    override fun toString(): String = "${real}+${imag}j"
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitComplexConst(this)
}

// ─── Global References ──────────────────────────────────────

/** Reference to a global variable or imported name. */
data class PIRGlobalRef(
    val name: String,
    val module: String,
    override val type: PIRType = PIRAnyType,
) : PIRValue {
    override fun toString(): String = if (module.isNotEmpty()) "$module.$name" else name
    override fun <T> accept(visitor: PIRValueVisitor<T>): T = visitor.visitGlobalRef(this)
}
