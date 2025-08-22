package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.jvm.ap.ifds.access.FactApDelta
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

object MethodSummaryEdgeApplicationUtils {
    sealed interface SummaryEdgeApplication {
        data class SummaryApRefinement(val delta: FactApDelta) : SummaryEdgeApplication
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
}
