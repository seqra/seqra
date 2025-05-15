package org.opentaint.ir.taint.configuration

import kotlinx.serialization.Serializable

@Serializable
data class TaintMark(val name: String) {
    override fun toString(): String = name

    companion object {
        val NULLNESS: TaintMark = TaintMark("NULLNESS")
    }
}
