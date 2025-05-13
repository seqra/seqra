package org.opentaint.ir.analysis.ifds2.taint.npe

import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.ifds2.taint.Vulnerability
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

fun runNpeAnalysis(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver,
    startMethods: List<JIRMethod>,
): List<Vulnerability> {
    val manager = NpeManager(graph, unitResolver)
    return manager.analyze(startMethods)
}
