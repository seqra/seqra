@file:Suppress("LiftReturnOrAssignment")

package org.opentaint.ir.analysis.ifds2.taint

import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

fun runTaintAnalysis(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver,
    startMethods: List<JIRMethod>,
): List<Vulnerability> {
    val manager = TaintManager(graph, unitResolver)
    return manager.analyze(startMethods)
}
