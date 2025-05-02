package org.opentaint.ir.analysis.paths

import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRLocal

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath private constructor(val value: JIRLocal?, val accesses: List<Accessor>) {
    companion object {

        fun fromLocal(value: JIRLocal) = AccessPath(value, listOf())

        fun fromStaticField(field: JIRField): AccessPath {
            if (!field.isStatic) {
                throw IllegalArgumentException("Expected static field")
            }

            return AccessPath(null, listOf(FieldAccessor(field)))
        }

        fun fromOther(other: AccessPath, accesses: List<Accessor>): AccessPath {
            if (accesses.any { it is FieldAccessor && it.field.isStatic }) {
                throw IllegalArgumentException("Unexpected static field")
            }

            return AccessPath(other.value, other.accesses.plus(accesses))
        }
    }

    fun limit(n: Int) = AccessPath(value, accesses.take(n))

    val isOnHeap: Boolean
        get() = accesses.isNotEmpty()

    val isStatic: Boolean
        get() = value == null

    override fun toString(): String {
        var str = value.toString()
        for (accessor in accesses) {
            str += ".$accessor"
        }
        return str
    }
}