package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.dataflow.util.containsAll
import org.opentaint.dataflow.util.copy
import org.opentaint.dataflow.util.toSet
import java.util.BitSet

typealias VariableValue = Int

const val TrueValue = 1
const val FalseValue = -1
const val UnknownValue = 0

inline val VariableValue.isTrue get() = this > 0
inline val VariableValue.isFalse get() = this < 0
inline val VariableValue.isUnknown get() = this == 0
inline val VariableValue.negated get() = -this

data class MethodFormulaCubeCompact(
    @JvmField val positiveLiterals: BitSet = BitSet(),
    @JvmField val negativeLiterals: BitSet = BitSet()
) {
    val size get() = positiveLiterals.cardinality() + negativeLiterals.cardinality()

    val isEmpty: Boolean get() = positiveLiterals.isEmpty && negativeLiterals.isEmpty

    fun copy() = MethodFormulaCubeCompact(positiveLiterals.copy(), negativeLiterals.copy())

    override fun toString(): String {
        val positive = positiveLiterals.toSet().joinToString(" ")
        val negative = negativeLiterals.toSet().joinToString(" ") { "-$it" }
        return listOf(positive, negative).filter { it.isNotEmpty() }.joinToString(" ", prefix = "[", postfix = "]")
    }

    fun mutableAdd(other: MethodFormulaCubeCompact) {
        positiveLiterals.or(other.positiveLiterals)
        negativeLiterals.or(other.negativeLiterals)
    }

    fun mutableIntersect(other: MethodFormulaCubeCompact) {
        positiveLiterals.and(other.positiveLiterals)
        negativeLiterals.and(other.negativeLiterals)
    }

    fun mutableRemove(other: MethodFormulaCubeCompact) {
        positiveLiterals.andNot(other.positiveLiterals)
        negativeLiterals.andNot(other.negativeLiterals)
    }

    fun value(variable: Int): VariableValue {
        if (positiveLiterals.get(variable)) return TrueValue
        if (negativeLiterals.get(variable)) return FalseValue
        return UnknownValue
    }

    fun add(other: MethodFormulaCubeCompact): MethodFormulaCubeCompact =
        copy().also { it.mutableAdd(other) }

    fun hasConflict(): Boolean = positiveLiterals.intersects(negativeLiterals)

    fun containsAll(other: MethodFormulaCubeCompact): Boolean =
        positiveLiterals.containsAll(other.positiveLiterals)
                && negativeLiterals.containsAll(other.negativeLiterals)

    fun usedLitVars(): BitSet {
        val result = BitSet()
        result.or(positiveLiterals)
        result.or(negativeLiterals)
        return result
    }

    companion object {
        fun singleLiteral(litVar: Int, negated: Boolean): MethodFormulaCubeCompact {
            val cube = MethodFormulaCubeCompact()
            if (!negated) {
                cube.positiveLiterals.set(litVar)
            } else {
                cube.negativeLiterals.set(litVar)
            }
            return cube
        }
    }
}
