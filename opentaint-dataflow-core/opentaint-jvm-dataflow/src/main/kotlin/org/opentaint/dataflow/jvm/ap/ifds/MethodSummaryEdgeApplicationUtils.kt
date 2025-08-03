package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.jvm.ap.ifds.access.FactApDelta

object MethodSummaryEdgeApplicationUtils {
    sealed interface SummaryEdgeApplication {
        data class SummaryApRefinement(val delta: FactApDelta) : SummaryEdgeApplication
        data class SummaryExclusionRefinement(val exclusion: ExclusionSet) : SummaryEdgeApplication
    }

    fun tryApplySummaryEdge(
        methodInitialFact: Fact.FinalFact,
        methodSummaryInitialFact: Fact.InitialFact,
    ): List<SummaryEdgeApplication> =
        methodInitialFact.ap.delta(methodSummaryInitialFact.ap).map { delta ->
            if (delta.isEmpty) {
                SummaryEdgeApplication.SummaryExclusionRefinement(
                    methodInitialFact.ap.exclusions.union(methodSummaryInitialFact.ap.exclusions)
                )
            } else {
                SummaryEdgeApplication.SummaryApRefinement(delta)
            }
        }
}
