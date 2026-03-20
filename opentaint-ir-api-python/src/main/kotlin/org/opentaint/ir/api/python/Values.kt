package org.opentaint.ir.api.python

/**
 * A value used in instructions (operand or result).
 * Analogous to JIRValue / JIRImmediate / JIRRef.
 *
 * PIR merges JIR's Value+Expr distinction — there are no separate
 * "expression" objects. All computation is done in instructions
 * that produce values.
 */
sealed interface PIRValue {
    val type: PIRType
}

// ─── Locals & Parameters ────────────────────────────────────

/** A local variable or temporary (e.g., "$t0", "result"). */
data class PIRLocal(
    val name: String,
    override val type: PIRType,
) : PIRValue {
    override fun toString(): String = name
}

/** Reference to a function parameter by name. */
data class PIRParameterRef(
    val name: String,
    override val type: PIRType,
) : PIRValue {
    override fun toString(): String = name
}

// ─── Constants ──────────────────────────────────────────────

sealed interface PIRConst : PIRValue

data class PIRIntConst(val value: Long, override val type: PIRType = PIRClassType("builtins.int")) : PIRConst {
    override fun toString(): String = value.toString()
}

data class PIRFloatConst(val value: Double, override val type: PIRType = PIRClassType("builtins.float")) : PIRConst {
    override fun toString(): String = value.toString()
}

data class PIRStrConst(val value: String, override val type: PIRType = PIRClassType("builtins.str")) : PIRConst {
    override fun toString(): String = "\"$value\""
}

data class PIRBoolConst(val value: Boolean, override val type: PIRType = PIRClassType("builtins.bool")) : PIRConst {
    override fun toString(): String = if (value) "True" else "False"
}

data object PIRNoneConst : PIRConst {
    override val type: PIRType = PIRNoneType
    override fun toString(): String = "None"
}

data object PIREllipsisConst : PIRConst {
    override val type: PIRType = PIRAnyType
    override fun toString(): String = "..."
}

data class PIRBytesConst(val value: ByteArray, override val type: PIRType = PIRClassType("builtins.bytes")) : PIRConst {
    override fun equals(other: Any?): Boolean =
        other is PIRBytesConst && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = "b\"...\""
}

data class PIRComplexConst(
    val real: Double,
    val imag: Double,
    override val type: PIRType = PIRClassType("builtins.complex"),
) : PIRConst {
    override fun toString(): String = "${real}+${imag}j"
}

// ─── Global References ──────────────────────────────────────

/** Reference to a global variable or imported name. */
data class PIRGlobalRef(
    val name: String,
    val module: String,
    override val type: PIRType = PIRAnyType,
) : PIRValue {
    override fun toString(): String = if (module.isNotEmpty()) "$module.$name" else name
}
