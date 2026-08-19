package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
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
        if (kind is TaintMarkFieldUnfoldRequest) {
            handleUnfoldRequest(summaryEffect, kind)
        }

        return super.handleZeroToFact(currentFactAp, summaryEffect, kind)
    }

    /**
     * A callee asks for its abstract initial fact to be unfolded when a taint mark its sink needs may
     * be hidden under the abstraction. The request has to be answered on fact-to-fact edges too, not
     * only on zero-to-fact ones: when the caller is itself analyzed from an initial fact -- i.e. the
     * tainted object was passed into the caller as well -- the callee's side effect summary arrives
     * here. Dropping it loses every sink whose condition reads a *field* of a formal parameter more
     * than one frame below the source.
     *
     * Answered only while the request is still un-refined, i.e. its fact is the bare abstraction and
     * no accessor below the parameter has been materialized yet. Fact-to-fact edges vastly outnumber
     * zero-to-fact ones, and refining on all of them does not terminate in any reasonable time.
     */
    override fun handleFactToFact(
        currentInitialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        kind: SideEffectKind
    ): Set<MethodSequentFlowFunction.Sequent> {
        if (kind is TaintMarkFieldUnfoldRequest && kind.fact.getAllAccessors().isEmpty()) {
            handleUnfoldRequest(summaryEffect, kind)
        }

        return super.handleFactToFact(currentInitialFactAp, currentFactAp, summaryEffect, kind)
    }

    private fun handleUnfoldRequest(
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        request: TaintMarkFieldUnfoldRequest
    ) {
        when (summaryEffect) {
            is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement -> {
                if (!summaryEffect.delta.isEmpty) {
                    handleMarkAfterAnyFieldRequest(summaryEffect.delta, request)
                }
            }

            is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement -> {
                // taint mark requested -> mark not in initial fact, delta is empty -> mark not in fact
            }
        }
    }

    private fun handleMarkAfterAnyFieldRequest(
        delta: FinalFactAp.Delta,
        request: TaintMarkFieldUnfoldRequest
    ) {
        val mark = request.mark
        val allAccessors = delta.getAllAccessors()
        val deltaHasMark = mark in allAccessors

        val startAccessors = hashSetOf<Accessor>()
        for (accessor in delta.getStartAccessors()) {
            if (accessor !is AnyAccessor) {
                startAccessors.add(accessor)
                continue
            }

            val anySuccessors = delta.readAccessor(accessor)?.getStartAccessors()
                ?: continue

            anySuccessors.filterTo(startAccessors) { it !is AnyAccessor }
        }

        // When the caller already knows where the mark sits, refine on exactly that branch. When it
        // does not -- because the caller is analyzed abstractly too and only knows the *shape* the
        // value takes below the callee's parameter -- refine on that shape instead, so the callee
        // materializes the accessor and can answer once the mark arrives from further up. Only a
        // single concrete field qualifies: that is the shape a field-sensitive library model produces
        // (`file.path`, `bean.url`), and fanning out over several accessors, or over elements,
        // re-analyzes far too much of the program for the chance of finding the mark.
        val relevantStartAccessors = if (deltaHasMark) {
            startAccessors.filter { accessor ->
                accessor == mark || delta.readAccessor(accessor)?.getAllAccessors()?.contains(mark) ?: false
            }
        } else {
            startAccessors.filter { it is FieldAccessor }.takeIf { it.size == 1 }.orEmpty()
        }

        if (relevantStartAccessors.isEmpty()) return

        val exclusion = relevantStartAccessors.fold(ExclusionSet.Empty as ExclusionSet, ExclusionSet::add)
        val sideEffectRequirement = request.fact.replaceExclusions(exclusion)
        runner.manager.handleCrossUnitSideEffectReq(request.method, sideEffectRequirement)
    }
}
