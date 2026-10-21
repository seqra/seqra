package org.opentaint.dataflow.configuration.python

data class TaintMark(val name: String) {
    override fun toString(): String = name
}
