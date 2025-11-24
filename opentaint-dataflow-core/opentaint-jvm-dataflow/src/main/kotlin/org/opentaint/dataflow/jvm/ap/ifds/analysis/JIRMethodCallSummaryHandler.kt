package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper

class JIRMethodCallSummaryHandler(
    private val statement: JIRInst,
    private val analysisContext: JIRMethodAnalysisContext
) : MethodCallSummaryHandler {
    override val factTypeChecker: FactTypeChecker get() = analysisContext.factTypeChecker

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact, factTypeChecker)

    override fun handleSummary(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        summaryFact: FinalFactAp,
        handleSummaryEdge: (initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp) -> Sequent
    ): Set<Sequent> {
        val result = hashSetOf<Sequent>()

        result += super.handleSummary(
            currentFactAp,
            summaryEffect,
            summaryFact
        ) { initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp ->
            analysisContext.aliasAnalysis?.forEachAliasAfterStatement(statement, summaryFactAp) { aliased ->
                handleSummaryEdge(initialFactRefinement, aliased)
            }

            handleSummaryEdge(initialFactRefinement, summaryFactAp)
        }

        return result
    }
}
