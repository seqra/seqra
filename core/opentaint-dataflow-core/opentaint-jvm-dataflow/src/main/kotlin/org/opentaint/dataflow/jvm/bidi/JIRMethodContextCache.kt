package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.engine.MethodContextSource
import org.opentaint.dataflow.bidi.interp.MethodForms
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.jvm.bidi.interp.JIRInterpreter
import org.opentaint.dataflow.jvm.bidi.interp.JIRMethodEntries

/** Adapt the real analysis-lifetime JVM owners to the common run context. */
class JIRMethodContextCache(private val interp: JIRInterpreter, private val entries: JIRMethodEntries) : MethodContextSource {
    override fun forms(key: MethodKey): MethodForms = entries.forms(interp, key)
    override fun graph(key: MethodKey, direction: Direction): MethodInstGraph = entries[key].graph(direction)
}
