package org.opentaint.dataflow.configuration.jvm

data class TaintMark(val name: String) {
    override fun toString(): String = name
}
