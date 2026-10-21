package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler.SummaryEdge
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.python.alias.forEachAliasBeforeCallStatement
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.dataflow.util.cartesianProductMapTo
import kotlin.collections.plusAssign

class PIRMethodCallSummaryHandler(
    private val callInst: PIRCall,
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
    override val factTypeChecker: FactTypeChecker,
) : MethodCallSummaryHandler {
    private val factMapper get() = ctx.methodCallFactMapper

    private val summaryRewriters = hashMapOf<PIRFunction, PIRCallRuleBasedSummaryRewriter>()

    private fun summaryRewriter(callee: MethodEntryPoint): PIRCallRuleBasedSummaryRewriter =
        summaryRewriters.getOrPut(callee.callee) {
            PIRCallRuleBasedSummaryRewriter(callInst, ctx, apManager, callee.callee)
        }

    private val MethodEntryPoint.callee: PIRFunction get() = method as PIRFunction

    private fun FinalFactAp.toCallSiteFrame(callee: MethodEntryPoint): FinalFactAp? =
        factMapper.toCallerFrame(callInst, callee.callee, base)?.let { rebase(it) }

    private fun FinalFactAp.mapToCaller(): List<FinalFactAp> =
        factMapper.mapMethodExitToReturnFlowFact(callInst, this, factTypeChecker)

    override fun prepareSummaryInitialFact(fact: InitialFactAp, callee: MethodEntryPoint): List<InitialFactAp> {
        val callSiteBase = factMapper.toCallerFrame(callInst, callee.callee, fact.base) ?: return emptyList()
        return factMapper.mapMethodExitToReturnFlowFact(callInst, fact.rebase(callSiteBase))
            .map { fact.rebase(it.base) }
    }

    override fun prepareSummaryFinalFact(fact: FinalFactAp, callee: MethodEntryPoint): List<FinalFactAp> =
        fact.toCallSiteFrame(callee)?.mapToCaller().orEmpty()

    override fun prepareFactToFactSummary(summaryEdge: Edge.FactToFact): List<Edge.FactToFact> {
        val callee = summaryEdge.methodEntryPoint
        val callSiteFact = summaryEdge.factAp.toCallSiteFrame(callee) ?: return emptyList()

        return summaryRewriter(callee).rewriteSummaryFact(callSiteFact).flatMap { (resultFact, refinement) ->
            val initialFacts = prepareSummaryInitialFact(refinement.refineFact(summaryEdge.initialFactAp), callee)
            refinement.refineFact(resultFact).mapToCaller().flatMap { finalFact ->
                initialFacts.map { Edge.FactToFact(callee, it, summaryEdge.statement, finalFact) }
            }
        }
    }

    override fun prepareNDFactToFactSummary(summaryEdge: Edge.NDFactToFact): List<SummaryEdge.NdF2F> {
        val callee = summaryEdge.methodEntryPoint
        val callSiteFact = summaryEdge.factAp.toCallSiteFrame(callee) ?: return emptyList()

        val initialFacts = summaryEdge.initialFacts
            .map { prepareSummaryInitialFact(it, callee) }
            .cartesianProductMapTo { it.toHashSet() }

        return summaryRewriter(callee).rewriteSummaryFact(callSiteFact).flatMap { (resultFact, refinement) ->
            check(!refinement.hasRefinement) { "Can't refine NDF2F edge" }
            resultFact.mapToCaller().flatMap { finalFact ->
                initialFacts.map { SummaryEdge.NdF2F(callee, it, finalFact) }
            }
        }
    }

    override fun handleZeroToZero(summaryFact: FinalFactAp?): Set<Sequent> =
        super.handleZeroToZero(summaryFact).flatMapTo(hashSetOf()) { seq ->
            if (seq !is Sequent.ZeroToFact) return@flatMapTo listOf(seq)

            val result = mutableListOf(seq)
            ctx.aliasAnalysis?.forEachAliasBeforeCallStatement(callInst, seq.factAp) { aliased ->
                result += Sequent.ZeroToFact(aliased, seq.traceInfo)
            }
            result
        }

    override fun handleSummary(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.EdgeRefinement,
        summaryEdge: SummaryEdge,
        createSideEffectRequirement: (refinement: ExclusionSet) -> Sequent?,
        handleSummaryEdge: (initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp) -> Sequent
    ): Set<Sequent> {
        val result = hashSetOf<Sequent>()

        result += super.handleSummary(
            currentFactAp,
            summaryEffect,
            summaryEdge,
            createSideEffectRequirement,
        ) { initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp ->
            if (initialFactRefinement != null) {
                createSideEffectRequirement(initialFactRefinement)?.also { result.add(it) }
            }

            if (summaryEdge.hasMemoryEffect()) {
                ctx.aliasAnalysis?.forEachAliasBeforeCallStatement(callInst, summaryFactAp) { aliased ->
                    result += handleSummaryEdge(initialFactRefinement, aliased)
                }
            }

            handleSummaryEdge(initialFactRefinement, summaryFactAp)
        }

        return result
    }

    private fun SummaryEdge.hasMemoryEffect(): Boolean {
        if (this !is SummaryEdge.F2F) return true
        return !final.equalTo(initial)
    }
}
