package org.opentaint.dataflow.configuration.jvm

@Suppress("EqualsOrHashCode")
data class TaintMark(val name: String) {
    override fun toString(): String = name

    private val hash = name.hashCode()
    override fun hashCode(): Int = hash
}
