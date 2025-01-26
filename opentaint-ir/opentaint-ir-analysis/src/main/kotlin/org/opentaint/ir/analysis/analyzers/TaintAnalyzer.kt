package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.DumpableAnalysisResult
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IFDSResult
import org.opentaint.ir.analysis.engine.IFDSVertex
import org.opentaint.ir.analysis.engine.SpaceId
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRValue

abstract class TaintAnalyzer(
    graph: JIRApplicationGraph,
    generates: (JIRInst) -> List<DomainFact>,
    val isSink: (JIRInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(graph, maxPathLength, generates)
    override val backward: Analyzer = object : Analyzer {
        override val backward: Analyzer
            get() = this@TaintAnalyzer
        override val flowFunctions: FlowFunctionsSpace
            get() = this@TaintAnalyzer.flowFunctions.backward

        override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
            error("Do not call sources for backward analyzer instance")
        }
    }

    companion object : SpaceId {
        override val value: String = "taint analysis"
    }

    override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (isSink(inst, fact)) {
                    fact.variable.let {
                        vulnerabilities.add(
                            ifdsResult.resolveTaintRealisationsGraph(IFDSVertex(inst, fact)).toVulnerability(value)
                        )
                    }
                }
            }
        }
        return DumpableAnalysisResult(vulnerabilities)
    }
}

private class TaintForwardFunctions(
    graph: JIRApplicationGraph,
    private val maxPathLength: Int,
    private val generates: (JIRInst) -> List<DomainFact>,
) : AbstractTaintForwardFunctions(graph) {

    override val inIds: List<SpaceId> get() = listOf(TaintAnalyzer, ZEROFact.id)

    override fun transmitDataFlow(from: JIRExpr, to: JIRValue, atInst: JIRInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default

        return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
    }

    override fun transmitDataFlowAtNormalInst(inst: JIRInst, nextInst: JIRInst, fact: DomainFact): List<DomainFact> {
        if (fact == ZEROFact) {
            return generates(inst) + listOf(ZEROFact)
        }
        return listOf(fact)
    }

    override fun obtainStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    override val backward: FlowFunctionsSpace by lazy { TaintBackwardFunctions(graph, this, maxPathLength) }
}

private class TaintBackwardFunctions(
    graph: JIRApplicationGraph,
    backward: FlowFunctionsSpace,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, backward, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(TaintAnalyzer, ZEROFact.id)
}