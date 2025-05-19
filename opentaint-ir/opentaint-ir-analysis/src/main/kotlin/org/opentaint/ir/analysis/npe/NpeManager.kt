package org.opentaint.ir.analysis.npe

import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.ifds.UnitResolver
import org.opentaint.ir.analysis.ifds.UnitType
import org.opentaint.ir.analysis.ifds.UnknownUnit
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.analysis.taint.TaintRunner
import org.opentaint.ir.analysis.taint.TaintZeroFact
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst

private val logger = mu.KotlinLogging.logger {}

context(Traits<Method, Statement>)
class NpeManager<Method, Statement>(
    graph: ApplicationGraph<Method, Statement>,
    unitResolver: UnitResolver<Method>,
) : TaintManager<Method, Statement>(graph, unitResolver, useBidiRunner = false)
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override fun newRunner(
        unit: UnitType,
    ): TaintRunner<Method, Statement> {
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

    override fun addStart(method: Method) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        // Note: DO NOT add deps here!
    }
}
