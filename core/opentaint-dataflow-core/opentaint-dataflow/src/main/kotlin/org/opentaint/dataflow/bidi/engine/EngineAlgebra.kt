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
import org.opentaint.dataflow.bidi.interp.RuleKind
import org.opentaint.ir.api.common.cfg.CommonInst

/** All application modes use the same AP operations and source hooks. */
internal class EngineAlgebra(private val core: RunMethodAnalyzer) : FactAlgebra<PremiseKey, Facts> {
    private fun input(at: Place, premise: PremiseKey, facts: Facts): OperationInput =
        OperationInput(at.site ?: ResumeSite.StatementInput(core.operation(at.node)), premise, facts,
            at.callerLayer?.let { CallResumeState(it, at.identityCandidate, at.pendingDemotion) })

    override fun reached(s: StatementSummary, premise: PremiseKey, c: Facts, at: Place) =
        core.reachedOperations(s, input(at, premise, c))
    override fun base(c: Facts): AccessPathBase {
        core.ap.cancellation.checkpoint()
        return c.base
    }
    override fun filter(c: Facts, filter: TypeFilter): Facts? = core.ops.filter(c, filter)
    override fun applyEdge(me: MicroEdge, c: Facts, premise: PremiseKey, statementEdge: Boolean,
                           out: ResultSink) {
        if (disabledStaticEdge(me)) return
        core.ops.applyEdge(c, premise, me.edge, statementEdge, core.mode,
            core.collect(premise, c, out), may = me.may)
    }

    override fun applyEdgeAt(me: MicroEdge, c: Facts, premise: PremiseKey, at: Place, out: ResultSink) {
        if (disabledStaticEdge(me)) return
        core.ops.applyEdge(c, premise, me.edge, at.statementEdge, core.mode,
            core.collect(input(at, premise, c), out), may = me.may)
    }

    private fun disabledStaticEdge(me: MicroEdge): Boolean = core.config.disableRun1Statics && (
        core.dropsStaticField(me.edge.from) || core.dropsStaticField(me.edge.to) ||
            // A configured source on a static read emits ZERO -> lhs directly.
            me.actionId?.occurrence?.kind == RuleKind.FIELD_SOURCE)

    override fun operationResult(me: MicroEdge?, premise: PremiseKey, input: Facts, result: Facts, at: Place, out: ResultSink) {
        val operationInput = input(at, premise, input)
        val write = at.staticOperations.filterIsInstance<StaticOperation.Write>().firstOrNull {
            if (me != null) (it.transfer != null && (it.transfer == me.forward || it.transfer == me.edge)) ||
                (it.action != null && it.action == me.actionId)
            else when {
                at.completedAction != null -> it.action == at.completedAction
                at.completedConjunction != null -> it.action == null && it.transfer == null &&
                    it.target.fact == at.completedConjunction.target
                else -> false
            }
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

    override fun sourcePredicateActionsAt(source: SourceRuleOccurrence, premise: PremiseKey, c: Facts, at: Place,
                                          out: (PremiseKey, Facts, org.opentaint.dataflow.bidi.interp.ActionId?) -> Unit) =
        core.localServices.sourcePredicateActionsAt(source, premise, c, at.node, at.statementEdge, input(at, premise, c), out)

    override fun allowsSource(node: CommonInst, me: MicroEdge): Boolean =
        !core.config.seededSources || core.config.seeds.allowsSource(core.key, node, me.forward, me.actionId)

    override fun sourceHit(node: CommonInst, me: MicroEdge) {
        core.ap.cancellation.checkpoint()
        core.sourceHits?.add(core.key, node, me.forward, me.actionId)
    }
}
