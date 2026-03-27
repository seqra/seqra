package org.opentaint.ir.go.inst

/**
 * Lightweight reference to an instruction by its index within a function.
 * Zero-cost abstraction at runtime (erased to plain Int).
 */
@JvmInline
value class GoIRInstRef(val index: Int) : Comparable<GoIRInstRef> {
    override fun compareTo(other: GoIRInstRef): Int = index.compareTo(other.index)
    override fun toString(): String = "inst#$index"
}
