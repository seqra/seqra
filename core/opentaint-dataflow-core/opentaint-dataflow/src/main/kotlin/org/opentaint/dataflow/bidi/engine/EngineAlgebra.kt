package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.FactAlgebra
import org.opentaint.dataflow.bidi.interp.MicroEdge
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.ir.api.common.cfg.CommonInst

/** All application modes use the same AP operations and source hooks. */
internal class EngineAlgebra(private val core: RunMethodAnalyzer) : FactAlgebra<PremiseKey, Facts> {
    override fun base(c: Facts): AccessPathBase {
        core.ap.cancellation.checkpoint()
        return c.base
    }
    override fun filter(c: Facts, filter: TypeFilter): Facts? = core.ops.filter(c, filter)
    override fun applyEdge(me: MicroEdge, c: Facts, premise: PremiseKey, statementEdge: Boolean,
                           out: ResultSink) {
        val markSensitive = me.edge.from.mark is MarkSlot.Concrete ||
            (me.edge.to.mark as? MarkSlot.Star)?.excluded?.isEmpty == false
        val input = if (core.config.run1 && markSensitive) {
            staticRootFact(core.ap, c)?.let { out(premise, it) }
            withoutStaticRootFact(core.ap, c) ?: return
        } else c
        core.ops.applyEdge(input, premise, me.edge, statementEdge, core.mode,
            core.collect(premise, input, out), may = me.may,
            unchangedStaticInitial = core.unchangedStaticInitial(premise))
    }

    override fun conjunction(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, node: CommonInst,
                             out: ResultSink) {
        val input = if (core.config.run1) withoutStaticRootFact(core.ap, c) ?: return else c
        core.localServices.conjunction(cj, premise, input, node, out)
    }

    override fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, c: Facts, node: CommonInst,
                                 statementEdge: Boolean, out: ResultSink) {
        val input = if (core.config.run1) withoutStaticRootFact(core.ap, c) ?: return else c
        core.localServices.sourcePredicate(source, premise, input, node, statementEdge, out)
    }

    override fun allowsSource(node: CommonInst, me: MicroEdge): Boolean =
        !core.config.seededSources || core.config.seeds.allowsSource(core.key, node, me.forward)

    override fun sourceHit(node: CommonInst, me: MicroEdge) {
        core.ap.cancellation.checkpoint()
        core.sourceHits?.add(core.key, node, me.forward)
    }
}
