package org.opentaint.ir.go.value

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRGlobal
import org.opentaint.ir.go.type.GoIRType

/**
 * Base sealed interface for all IR values.
 * A value is anything that can be used as an operand of an instruction.
 * Value-producing instructions also implement this via GoIRValueInst.
 */
interface GoIRValue {
    val type: GoIRType
    val name: String

    fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T
}

/** Sealed interface for constant value representations. */
sealed interface GoIRConstantValue {
    data class IntConst(val value: Long) : GoIRConstantValue
    data class FloatConst(val value: Double) : GoIRConstantValue
    data class ComplexConst(val real: Double, val imag: Double) : GoIRConstantValue
    data class StringConst(val value: String) : GoIRConstantValue
    data class BoolConst(val value: Boolean) : GoIRConstantValue
    data object NilConst : GoIRConstantValue
}

// ─── Non-instruction values ─────────────────────────────────────────

data class GoIRConstValue(
    override val type: GoIRType,
    override val name: String,
    val value: GoIRConstantValue,
) : GoIRValue {
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = visitor.visitConst(this)
}

data class GoIRParameterValue(
    override val type: GoIRType,
    override val name: String,
    val paramIndex: Int,
) : GoIRValue {
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = visitor.visitParameter(this)
}

data class GoIRFreeVarValue(
    override val type: GoIRType,
    override val name: String,
    val freeVarIndex: Int,
) : GoIRValue {
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = visitor.visitFreeVar(this)
}

data class GoIRGlobalValue(
    override val type: GoIRType,
    override val name: String,
    val global: GoIRGlobal,
) : GoIRValue {
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = visitor.visitGlobal(this)
}

data class GoIRFunctionValue(
    override val type: GoIRType,
    override val name: String,
    val function: GoIRFunction,
) : GoIRValue {
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = visitor.visitFunction(this)
}

data class GoIRBuiltinValue(
    override val type: GoIRType,
    override val name: String,
) : GoIRValue {
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = visitor.visitBuiltin(this)
}
