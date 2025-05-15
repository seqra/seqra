package org.opentaint.ir.analysis.npe

import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.ifds.UnitResolver
import org.opentaint.ir.analysis.ifds.UnitType
import org.opentaint.ir.analysis.ifds.UnknownUnit
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.analysis.taint.TaintRunner
import org.opentaint.ir.analysis.taint.TaintZeroFact
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

private val logger = mu.KotlinLogging.logger {}

class NpeManager(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver,
) : TaintManager(graph, unitResolver, useBidiRunner = false) {

    override fun newRunner(
        unit: UnitType,
    ): TaintRunner {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        val analyzer = NpeAnalyzer(graph)
        val runner = UniRunner(
            graph = graph,
            analyzer = analyzer,
            manager = this@NpeManager,
            unitResolver = unitResolver,
            unit = unit,
            zeroFact = TaintZeroFact
        )

        runnerForUnit[unit] = runner
        return runner
    }

    override fun addStart(method: JIRMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        // Note: DO NOT add deps here!
    }
}
