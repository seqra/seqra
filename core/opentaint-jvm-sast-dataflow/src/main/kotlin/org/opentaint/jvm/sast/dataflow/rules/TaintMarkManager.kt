package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.TaintMark

class TaintMarkManager {
    private val taintMarks = hashMapOf<String, TaintMark>()

    fun taintMark(name: String): TaintMark = taintMarks.getOrPut(name) { TaintMark(name) }
}
