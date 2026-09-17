package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler

interface MethodSideEffectHandlerWithAnyAccessorRequestHandling : MethodSideEffectSummaryHandler {
    val runner: AnalysisRunner

    override fun handleZeroToFact(
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        kind: SideEffectKind
    ): Set<MethodSequentFlowFunction.Sequent> {
        if (kind !is TaintMarkFieldUnfoldRequest) {
            return super.handleZeroToFact(currentFactAp, summaryEffect, kind)
        }

        handleUnfoldRequest(summaryEffect, kind)
        return emptySet()
    }

    override fun handleFactToFact(
        methodEntryPoint: MethodEntryPoint,
        currentInitialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        kind: SideEffectKind
    ): Set<MethodSequentFlowFunction.Sequent> {
        if (kind !is TaintMarkFieldUnfoldRequest) {
            return super.handleFactToFact(methodEntryPoint, currentInitialFactAp, currentFactAp, summaryEffect, kind)
        }

        if (handleUnfoldRequest(summaryEffect, kind)) {
            return emptySet()
        }

        val nextRequests = kind.nextRequests(summaryEffect)
        val ex = when (summaryEffect) {
            is SummaryEdgeApplication.SummaryApRefinement -> ExclusionSet.Empty
            is SummaryEdgeApplication.SummaryExclusionRefinement -> summaryEffect.exclusion
        }
        val fact = currentInitialFactAp.replaceExclusions(ex)
        return nextRequests.mapTo(hashSetOf()) {
            MethodSequentFlowFunction.Sequent.FactSideEffect(fact, it)
        }
    }

    private fun TaintMarkFieldUnfoldRequest.nextRequests(
        effect: SummaryEdgeApplication
    ): List<TaintMarkFieldUnfoldRequest> = when (effect) {
        is SummaryEdgeApplication.SummaryExclusionRefinement -> listOf(this)
        is SummaryEdgeApplication.SummaryApRefinement -> {
            if (suffix != null || effect.delta.isEmpty) {
                listOf(this)
            } else {
                effect.delta.startAccessors().map { copy(suffix = it) }
            }
        }
    }

    private fun handleUnfoldRequest(
        summaryEffect: SummaryEdgeApplication,
        request: TaintMarkFieldUnfoldRequest
    ): Boolean {
        when (summaryEffect) {
            is SummaryEdgeApplication.SummaryApRefinement -> {
                if (!summaryEffect.delta.isEmpty) {
                    return handleMarkAfterAnyFieldRequest(summaryEffect.delta, request)
                }
            }

            is SummaryEdgeApplication.SummaryExclusionRefinement -> {
                // taint mark requested -> mark not in initial fact, delta is empty -> mark not in fact
            }
        }

        return false
    }

    private fun handleMarkAfterAnyFieldRequest(
        delta: FinalFactAp.Delta,
        request: TaintMarkFieldUnfoldRequest
    ): Boolean {
        val mark = request.mark
        val allAccessors = delta.getAllAccessors()
        if (mark !in allAccessors) return false

        val nextAccessors = request.suffix?.let { setOf(it) }
            ?: delta.relevantStartAccessors(mark)

        val exclusion = nextAccessors.fold(ExclusionSet.Empty as ExclusionSet, ExclusionSet::add)
        runner.manager.handleCrossUnitSideEffectReq(request.method, request.fact.replaceExclusions(exclusion))

        return true
    }

    private fun FinalFactAp.Delta.startAccessors(): Set<Accessor> {
        val startAccessors = hashSetOf<Accessor>()
        for (accessor in getStartAccessors()) {
            if (accessor !is AnyAccessor) {
                startAccessors.add(accessor)
                continue
            }

            val anySuccessors = readAccessor(accessor)?.getStartAccessors()
                ?: continue

            anySuccessors.filterTo(startAccessors) { it !is AnyAccessor }
        }
        return startAccessors
    }

    private fun FinalFactAp.Delta.relevantStartAccessors(mark: Accessor): List<Accessor> =
        startAccessors().filter { accessor ->
            accessor == mark || readAccessor(accessor)?.getAllAccessors()?.contains(mark) ?: false
        }
}
