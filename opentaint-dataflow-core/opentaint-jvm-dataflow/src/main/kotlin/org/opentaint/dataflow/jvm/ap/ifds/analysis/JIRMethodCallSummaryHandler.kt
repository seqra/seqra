package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.ir.api.jvm.cfg.JIRInst

class JIRMethodCallSummaryHandler(
    private val statement: JIRInst,
    private val analysisContext: JIRMethodAnalysisContext,
    private val apManager: ApManager
) : MethodCallSummaryHandler {
    override val factTypeChecker: FactTypeChecker get() = analysisContext.factTypeChecker

    private val summaryRewriter by lazy {
        JIRMethodCallRuleBasedSummaryRewriter(statement, analysisContext, apManager)
    }

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact, factTypeChecker)

    override fun handleSummary(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        summaryFact: FinalFactAp,
        createSideEffectRequirement: (refinement: ExclusionSet) -> Sequent?,
        handleSummaryEdge: (initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp) -> Sequent
    ): Set<Sequent> {
        val result = hashSetOf<Sequent>()

        result += super.handleSummary(
            currentFactAp,
            summaryEffect,
            summaryFact,
            createSideEffectRequirement,
        ) { initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp ->
            if (initialFactRefinement != null) {
                createSideEffectRequirement(initialFactRefinement)?.also { result.add(it) }
            }

            analysisContext.aliasAnalysis?.forEachAliasAfterStatement(statement, summaryFactAp) { aliased ->
                result += handleSummaryEdge(initialFactRefinement, aliased)
            }

            handleSummaryEdge(initialFactRefinement, summaryFactAp)
        }

        return result
    }

    override fun prepareFactToFactSummary(summaryEdge: Edge.FactToFact): List<Edge.FactToFact> =
        summaryRewriter.rewriteSummaryFact(summaryEdge.factAp).map { (resultFact, refinement) ->
            Edge.FactToFact(
                summaryEdge.methodEntryPoint,
                refinement.refineFact(summaryEdge.initialFactAp),
                summaryEdge.statement,
                refinement.refineFact(resultFact)
            )
        }

    override fun prepareNDFactToFactSummary(summaryEdge: Edge.NDFactToFact): List<Edge.NDFactToFact> =
        summaryRewriter.rewriteSummaryFact(summaryEdge.factAp).map { (resultFact, refinement) ->
            check(!refinement.hasRefinement) { "Can't refine NDF2F edge" }
            Edge.NDFactToFact(
                summaryEdge.methodEntryPoint,
                summaryEdge.initialFacts,
                summaryEdge.statement,
                resultFact,
            )
        }
}
