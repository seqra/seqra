package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IfdsResult
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRValue

abstract class TaintAnalyzer(
    cp: JIRClasspath,
    generates: (JIRInst) -> List<DomainFact>,
    val isSink: (JIRInst, DomainFact) -> Boolean,
    maxPathLength: Int
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(cp, maxPathLength, generates)

    companion object {
        val vulnerabilityType: String = "taint analysis"
    }

    override fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<VulnerabilityLocation> {
        val vulnerabilities = mutableListOf<VulnerabilityLocation>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (isSink(inst, fact)) {
                    fact.variable.let {
                        vulnerabilities.add(VulnerabilityLocation(vulnerabilityType, IfdsVertex(inst, fact)))
                    }
                }
            }
        }
        return vulnerabilities
    }
}

fun TaintBackwardAnalyzerFactory(maxPathLength: Int = 5) = AnalyzerFactory { graph ->
    TaintBackwardAnalyzer(graph, maxPathLength)
}

private class TaintBackwardAnalyzer(graph: JIRApplicationGraph, maxPathLength: Int) : Analyzer {
    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace = TaintBackwardFunctions(graph, maxPathLength)

    override fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<VulnerabilityLocation> {
        error("Do not call sources for backward analyzer instance")
    }
}

private class TaintForwardFunctions(
    cp: JIRClasspath,
    private val maxPathLength: Int,
    private val generates: (JIRInst) -> List<DomainFact>,
) : AbstractTaintForwardFunctions(cp) {

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

    override fun obtainPossibleStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }
}

private class TaintBackwardFunctions(
    graph: JIRApplicationGraph,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength)