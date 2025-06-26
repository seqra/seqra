package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Analyzer
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst

context(Traits<Method, Statement>)
class UnusedVariableAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<UnusedVariableDomainFact, UnusedVariableEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: UnusedVariableFlowFunctions<Method, Statement> by lazy {
        UnusedVariableFlowFunctions(graph)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: Edge<UnusedVariableDomainFact, Statement>,
    ): List<UnusedVariableEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }
    }

    override fun handleCrossUnitCall(
        caller: Vertex<UnusedVariableDomainFact, Statement>,
        callee: Vertex<UnusedVariableDomainFact, Statement>,
    ): List<UnusedVariableEvent<Method, Statement>> {
        return emptyList()
    }
}
