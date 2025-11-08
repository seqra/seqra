package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

object MethodSummaryEdgeApplicationUtils {
    sealed interface SummaryEdgeApplication {
        data class SummaryApRefinement(val delta: FinalFactAp.Delta) : SummaryEdgeApplication
        data class SummaryExclusionRefinement(val exclusion: ExclusionSet) : SummaryEdgeApplication
    }

    fun tryApplySummaryEdge(
        methodInitialFactAp: FinalFactAp,
        methodSummaryInitialFactAp: InitialFactAp,
    ): List<SummaryEdgeApplication> =
        methodInitialFactAp.delta(methodSummaryInitialFactAp).map { delta ->
            if (delta.isEmpty) {
                SummaryEdgeApplication.SummaryExclusionRefinement(
                    methodInitialFactAp.exclusions.union(methodSummaryInitialFactAp.exclusions)
                )
            } else {
                SummaryEdgeApplication.SummaryApRefinement(delta)
            }
        }

    fun emptyDeltaExclusionRefinementOrNull(
        methodInitialFactAp: FinalFactAp,
        methodSummaryInitialFactAp: InitialFactAp,
    ): ExclusionSet? {
        if (methodInitialFactAp.hasEmptyDelta(methodSummaryInitialFactAp)) {
            return methodSummaryInitialFactAp.exclusions
        }
        return null
    }
}
