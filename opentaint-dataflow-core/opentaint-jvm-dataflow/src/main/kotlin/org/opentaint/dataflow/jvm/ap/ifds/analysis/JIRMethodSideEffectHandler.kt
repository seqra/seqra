package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintMarkFieldUnfoldRequest

class JIRMethodSideEffectHandler(
    private val runner: AnalysisRunner
) : MethodSideEffectSummaryHandler {
    override fun handleZeroToFact(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        kind: SideEffectKind
    ): Set<MethodSequentFlowFunction.Sequent> {
        if (kind is TaintMarkFieldUnfoldRequest) {
            when (summaryEffect) {
                is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement -> {
                    if (!summaryEffect.delta.isEmpty) {
                        handleMarkAfterAnyFieldRequest(summaryEffect.delta, kind)
                    }
                }

                is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement -> {
                    // taint mark requested -> mark not in initial fact, delta is empty -> mark not in fact
                }
            }
        }

        return emptySet()
    }

    private fun handleMarkAfterAnyFieldRequest(
        delta: FinalFactAp.Delta,
        request: TaintMarkFieldUnfoldRequest
    ) {
        val mark = request.mark
        val allAccessors = delta.getAllAccessors()
        if (mark !in allAccessors) return

        val relevantStartAccessors = delta.getStartAccessors().filter { accessor ->
            accessor == mark || delta.readAccessor(accessor)?.getAllAccessors()?.contains(mark) ?: false
        }

        if (relevantStartAccessors.isEmpty()) return

        val exclusion = relevantStartAccessors.fold(ExclusionSet.Empty as ExclusionSet, ExclusionSet::add)
        val sideEffectRequirement = request.fact.replaceExclusions(exclusion)
        runner.manager.handleCrossUnitSideEffectReq(request.method, sideEffectRequirement)
    }
}
