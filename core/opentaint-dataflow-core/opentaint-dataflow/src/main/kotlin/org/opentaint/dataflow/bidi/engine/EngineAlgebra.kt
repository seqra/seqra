package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.FactAlgebra
import org.opentaint.dataflow.bidi.interp.MicroEdge
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.dataflow.bidi.interp.Place
import org.opentaint.dataflow.bidi.interp.ResumeSite
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.interp.StaticOperation
import org.opentaint.ir.api.common.cfg.CommonInst

/** All application modes use the same AP operations and source hooks. */
internal class EngineAlgebra(private val core: RunMethodAnalyzer) : FactAlgebra<PremiseKey, Facts> {
    private fun input(at: Place, premise: PremiseKey, facts: Facts): OperationInput =
        OperationInput(at.site ?: ResumeSite.StatementInput(core.operation(at.node)), premise, facts,
            at.callerLayer?.let { CallResumeState(it, at.identityCandidate, at.pendingDemotion) }, core.currentGroundingSupport)

    override fun reached(s: StatementSummary, premise: PremiseKey, c: Facts, at: Place) =
        core.reachedOperations(s, input(at, premise, c))
    override fun base(c: Facts): AccessPathBase {
        core.ap.cancellation.checkpoint()
        return c.base
    }
    override fun filter(c: Facts, filter: TypeFilter): Facts? = core.ops.filter(c, filter)
    override fun applyEdge(me: MicroEdge, c: Facts, premise: PremiseKey, statementEdge: Boolean,
                           out: ResultSink) {
        core.ops.applyEdge(c, premise, me.edge, statementEdge, core.mode,
            core.collect(premise, c, out), may = me.may)
    }

    override fun applyEdgeAt(me: MicroEdge, c: Facts, premise: PremiseKey, at: Place, out: ResultSink) {
        var input = input(at, premise, c)
        if (core.config.run1 && me.edge.from.mark is MarkSlot.Concrete && me.edge.from.base != AccessPathBase.Zero) {
            core.observeGrounding(input)
            val literal = Pattern(me.edge.from,
                if (me.edge.from.tail == Tail.ANY_TAINT) me.edge.fromExclusion else me.edge.exclusion)
            val checked = core.ops.checkMark(c, literal, core.mode)
            if (checked is MarkCheck.Holds) input = input.copy(support = core.guardGrounding(input, checked.facts, literal))
        }
        core.ops.applyEdge(c, premise, me.edge, at.statementEdge, core.mode,
            core.collect(input) { pr, result ->
                val support = if (me.edge.to.mark is MarkSlot.Concrete && me.edge.to.tail != Tail.STAR)
                    core.fixedGrounding(input) else core.currentGroundingSupport
                core.withGroundingSupport(support) { out(pr, result) }
            }, may = me.may)
    }

    override fun operationResult(me: MicroEdge?, premise: PremiseKey, input: Facts, result: Facts, at: Place, out: ResultSink) {
        val operationInput = input(at, premise, input)
        val write = at.staticOperations.filterIsInstance<StaticOperation.Write>().firstOrNull {
            if (me != null) (it.transfer != null && (it.transfer == me.forward || it.transfer == me.edge)) ||
                (it.action != null && it.action == me.actionId)
            else result.base == STATIC && core.ops.leaves(result).any { leaf -> Run1StaticService.intersect(leaf, it.target) != null }
        }
        core.emitOperationResult(operationInput, write, result, out)
    }

    override fun conjunction(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, node: CommonInst,
                             out: ResultSink) {
        core.localServices.conjunction(cj, premise, c, node, out)
    }

    override fun conjunctionAt(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, at: Place, out: ResultSink) =
        core.localServices.conjunctionAt(cj, premise, c, at.node, input(at, premise, c), out)

    override fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, c: Facts, node: CommonInst,
                                 statementEdge: Boolean, out: ResultSink) {
        core.localServices.sourcePredicate(source, premise, c, node, statementEdge, out)
    }

    override fun sourcePredicateAt(source: SourceRuleOccurrence, premise: PremiseKey, c: Facts, at: Place, out: ResultSink) =
        core.localServices.sourcePredicateAt(source, premise, c, at.node, at.statementEdge, input(at, premise, c), out)

    override fun allowsSource(node: CommonInst, me: MicroEdge): Boolean =
        !core.config.seededSources || core.config.seeds.allowsSource(core.key, node, me.forward, me.actionId)

    override fun sourceHit(node: CommonInst, me: MicroEdge) {
        core.ap.cancellation.checkpoint()
        core.sourceHits?.add(core.key, node, me.forward, me.actionId)
    }
}
