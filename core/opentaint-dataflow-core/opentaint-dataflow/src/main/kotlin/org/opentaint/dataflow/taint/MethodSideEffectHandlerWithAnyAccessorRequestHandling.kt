package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler

interface MethodSideEffectHandlerWithAnyAccessorRequestHandling : MethodSideEffectSummaryHandler {
    val runner: AnalysisRunner

    override fun handleZeroToFact(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
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
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        kind: SideEffectKind
    ): Set<MethodSequentFlowFunction.Sequent> {
        if (kind !is TaintMarkFieldUnfoldRequest) {
            return super.handleFactToFact(methodEntryPoint, currentInitialFactAp, currentFactAp, summaryEffect, kind)
        }

        if (handleUnfoldRequest(summaryEffect, kind)) {
            return emptySet()
        }

        val fact = currentInitialFactAp.replaceExclusions(ExclusionSet.Empty)
        val newKind = TaintMarkFieldUnfoldRequest(methodEntryPoint, fact, kind.mark)
        return setOf(MethodSequentFlowFunction.Sequent.FactSideEffect(fact, newKind))
    }

    private fun handleUnfoldRequest(
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        request: TaintMarkFieldUnfoldRequest
    ): Boolean {
        when (summaryEffect) {
            is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement -> {
                if (!summaryEffect.delta.isEmpty) {
                    return handleMarkAfterAnyFieldRequest(summaryEffect.delta, request)
                }
            }

            is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement -> {
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

        val requests = mutableListOf<InitialFactAp>()
        traverseAllAccessorToMarkChains(mark, delta, request.fact, hashSetOf(), requests)

        requests.forEach {
            runner.manager.handleCrossUnitSideEffectReq(request.method, it)
        }

        return true
    }

    private fun traverseAllAccessorToMarkChains(
        mark: Accessor,
        current: FinalFactAp.Delta,
        fact: InitialFactAp,
        visited: MutableSet<FinalFactAp.Delta>,
        result: MutableList<InitialFactAp>
    ) {
        if (!visited.add(current)) return

        val relevantStartAccessors = current.relevantStartAccessors(mark)
        if (relevantStartAccessors.isEmpty()) return

        val exclusion = relevantStartAccessors.fold(ExclusionSet.Empty as ExclusionSet, ExclusionSet::add)
        result += fact.replaceExclusions(exclusion)

        for (accessor in relevantStartAccessors) {
            if (accessor == mark) continue

            val nextDelta = current.readAccessor(accessor) ?: continue
            val nextFact = fact.append(accessor)

            traverseAllAccessorToMarkChains(mark, nextDelta, nextFact, visited, result)
        }
    }

    private fun FinalFactAp.Delta.relevantStartAccessors(mark: Accessor): List<Accessor> {
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

        return startAccessors.filter { accessor ->
            accessor == mark || readAccessor(accessor)?.getAllAccessors()?.contains(mark) ?: false
        }
    }

    private fun InitialFactAp.append(accessor: Accessor): InitialFactAp = with(runner.apManager) {
        val singleAccessorFact = mostAbstractInitialAp(base).prependAccessor(accessor)
        val empty = mostAbstractFinalAp(base)
        val singleAccessorDelta = singleAccessorFact.splitDelta(empty).first().second
        return concat(singleAccessorDelta)
    }
}
