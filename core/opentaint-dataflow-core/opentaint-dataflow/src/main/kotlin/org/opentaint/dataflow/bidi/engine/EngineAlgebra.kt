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
                           out: ResultSink): Unit =
        core.ops.applyEdge(c, premise, me.edge, statementEdge, core.mode, core.collect(premise, c, out), may = me.may)

    override fun conjunction(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, node: CommonInst,
                             out: ResultSink): Unit =
        core.localServices.conjunction(cj, premise, c, node, out)

    override fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, c: Facts, node: CommonInst,
                                 statementEdge: Boolean, out: ResultSink) =
        core.localServices.sourcePredicate(source, premise, c, node, statementEdge, out)

    override fun allowsSource(node: CommonInst, me: MicroEdge): Boolean =
        !core.config.seededSources || core.config.seeds.allowsSource(core.key, node, me.forward)

    override fun sourceHit(node: CommonInst, me: MicroEdge) {
        core.ap.cancellation.checkpoint()
        core.sourceHits?.add(core.key, node, me.forward)
    }
}
