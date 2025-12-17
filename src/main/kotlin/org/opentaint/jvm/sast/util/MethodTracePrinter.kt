package org.opentaint.jvm.sast.util

import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.FullTrace
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry
import org.opentaint.semgrep.pattern.conversion.automata.PrintableGraph

fun FullTrace.view() {
    PrintableFullTrace(this).view()
}

private class PrintableFullTrace(
    private val trace: FullTrace,
) : PrintableGraph<TraceEntry, Pair<TraceEntry, TraceEntry>> {
    override fun allNodes(): List<TraceEntry> {
        val result = mutableSetOf(trace.startEntry, trace.final)
        result.addAll(trace.successors.keys)
        trace.successors.values.forEach { result.addAll(it) }
        return result.toList()
    }

    override fun edgeLabel(edge: Pair<TraceEntry, TraceEntry>): String = ""

    override fun successors(node: TraceEntry): List<Pair<Pair<TraceEntry, TraceEntry>, TraceEntry>> =
        trace.successors[node].orEmpty().map { (node to it) to it }

    override fun nodeLabel(node: TraceEntry): String = node.toString()
}
