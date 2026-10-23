package org.opentaint.dataflow.configuration.go.serialized

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers

sealed interface GoSerializedCondition {
    data object True : GoSerializedCondition

    data class Or(val anyOf: List<GoSerializedCondition>) : GoSerializedCondition
    data class And(val allOf: List<GoSerializedCondition>) : GoSerializedCondition
    data class Not(val not: GoSerializedCondition) : GoSerializedCondition

    data class ContainsMark(val tainted: String, val pos: PositionBaseWithModifiers) : GoSerializedCondition

    data class ConstantCmp(val pos: PositionBase, val value: ConstantValue, val cmp: ConstantCmpType) : GoSerializedCondition

    data class ConstantMatches(val pos: PositionBase, val pattern: String) : GoSerializedCondition

    data class IsNull(val pos: PositionBase) : GoSerializedCondition

    data class IsConstant(val pos: PositionBase) : GoSerializedCondition

    data class NumberOfArgs(val n: Int) : GoSerializedCondition

    data class IsType(val typeName: String, val pos: PositionBase) : GoSerializedCondition

    enum class ConstantType { Str, Bool, Int }
    enum class ConstantCmpType { Eq, Lt, Gt }
    data class ConstantValue(val type: ConstantType, val value: String)

    fun isFalse(): Boolean = this is Not && this.not is True

    companion object {
        fun mkFalse() = Not(True)

        fun not(arg: GoSerializedCondition): GoSerializedCondition =
            if (arg is Not) arg.not else Not(arg)

        fun and(args: List<GoSerializedCondition>): GoSerializedCondition =
            mkFlatOp(
                args, And::allOf, ::And,
                isNeutral = { it is True },
                mkNeutral = { True },
                isZero = { it.isFalse() },
                mkZero = { mkFalse() },
            )

        fun or(args: List<GoSerializedCondition>): GoSerializedCondition =
            mkFlatOp(
                args, Or::anyOf, ::Or,
                isNeutral = { it.isFalse() },
                mkNeutral = { mkFalse() },
                isZero = { it is True },
                mkZero = { True }
            )

        private inline fun <reified Op : GoSerializedCondition> mkFlatOp(
            args: List<GoSerializedCondition>,
            opArgs: Op.() -> List<GoSerializedCondition>,
            mkOp: (List<GoSerializedCondition>) -> Op,
            isNeutral: (GoSerializedCondition) -> Boolean,
            mkNeutral: () -> GoSerializedCondition,
            isZero: (GoSerializedCondition) -> Boolean,
            mkZero: () -> GoSerializedCondition,
        ): GoSerializedCondition {
            val result = mutableSetOf<GoSerializedCondition>()
            for (arg in args) {
                if (arg is Op) {
                    result.addAll(opArgs(arg))
                    continue
                }
                if (isNeutral(arg)) continue
                if (isZero(arg)) return mkZero()
                result.add(arg)
            }
            return when (result.size) {
                0 -> mkNeutral()
                1 -> result.single()
                else -> mkOp(result.toList())
            }
        }
    }
}