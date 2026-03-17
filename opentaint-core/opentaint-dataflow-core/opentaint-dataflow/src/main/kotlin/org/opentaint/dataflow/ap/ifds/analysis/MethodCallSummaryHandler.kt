package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.TraceInfo

interface MethodCallSummaryHandler {
    val factTypeChecker: FactTypeChecker

    sealed interface SummaryEdge {
        val final: FinalFactAp

        data class F2F(val initial: InitialFactAp, override val final: FinalFactAp) : SummaryEdge
        data class NdF2F(val initial: Set<InitialFactAp>, override val final: FinalFactAp) : SummaryEdge
    }

    fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp>

    fun handleZeroToZero(summaryFact: FinalFactAp?): Set<Sequent> {
        if (summaryFact == null) return setOf(Sequent.ZeroToZero)

        val summaryExitFacts = mapMethodExitToReturnFlowFact(summaryFact)
        return summaryExitFacts.mapTo(hashSetOf()) {
            Sequent.ZeroToFact(it, TraceInfo.ApplySummary)
        }
    }

    fun handleZeroToFact(
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        summaryEdge: SummaryEdge,
    ): Set<Sequent> = handleSummary(
        currentFactAp,
        summaryEffect,
        summaryEdge,
        createSideEffectRequirement = {
            check(it is ExclusionSet.Universe) { "Incorrect refinement" }
            null
        }
    ) { initialFactRefinement: ExclusionSet?, summaryFactAp ->
        check(initialFactRefinement == null || initialFactRefinement is ExclusionSet.Universe) {
            "Incorrect refinement"
        }

        Sequent.ZeroToFact(summaryFactAp, TraceInfo.ApplySummary)
    }

    fun handleFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        summaryEdge: SummaryEdge,
    ): Set<Sequent> = handleSummary(
        currentFactAp,
        summaryEffect,
        summaryEdge,
        createSideEffectRequirement = { refinement ->
            Sequent.SideEffectRequirement(initialFactAp.refine(refinement))
        }
    ) { initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp ->
        Sequent.FactToFact(initialFactAp.refine(initialFactRefinement), summaryFactAp, TraceInfo.ApplySummary)
    }

    fun prepareFactToFactSummary(summaryEdge: Edge.FactToFact): List<Edge.FactToFact> = listOf(summaryEdge)

    fun handleNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        summaryEdge: SummaryEdge,
    ): Set<Sequent> = handleSummary(
        currentFactAp,
        summaryEffect,
        summaryEdge,
        createSideEffectRequirement = {
            check(it is ExclusionSet.Universe) { "Incorrect refinement" }
            null
        }
    ) { initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp ->
        check(initialFactRefinement == null || initialFactRefinement is ExclusionSet.Universe) {
            "Incorrect refinement"
        }

        Sequent.NDFactToFact(
            initialFacts.mapTo(hashSetOf()) { it.refine(initialFactRefinement) },
            summaryFactAp,
            TraceInfo.ApplySummary
        )
    }

    fun prepareNDFactToFactSummary(summaryEdge: Edge.NDFactToFact): List<Edge.NDFactToFact> = listOf(summaryEdge)

    fun InitialFactAp.refine(exclusionSet: ExclusionSet?) =
        if (exclusionSet == null) this else replaceExclusions(exclusionSet)

    fun handleSummary(
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        summaryEdge: SummaryEdge,
        createSideEffectRequirement: (refinement: ExclusionSet) -> Sequent?,
        handleSummaryEdge: (initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp) -> Sequent
    ): Set<Sequent> {
        val mappedSummaryFacts = mapMethodExitToReturnFlowFact(summaryEdge.final)

        return when (summaryEffect) {
            is SummaryApRefinement -> mappedSummaryFacts.mapNotNullTo(hashSetOf()) { mappedSummaryFact ->
                // todo: filter exclusions
                val summaryFactAp = mappedSummaryFact
                    .concat(factTypeChecker, summaryEffect.delta)
                    ?.replaceExclusions(currentFactAp.exclusions)
                    ?: return@mapNotNullTo null

                handleSummaryEdge(null, summaryFactAp)
            }

            is SummaryExclusionRefinement -> mappedSummaryFacts.mapTo(hashSetOf()) { mappedSummaryFact ->
                // todo: filter exclusions
                val summaryFactAp = mappedSummaryFact.replaceExclusions(summaryEffect.exclusion)

                handleSummaryEdge(summaryEffect.exclusion, summaryFactAp)
            }
        }
    }
}
