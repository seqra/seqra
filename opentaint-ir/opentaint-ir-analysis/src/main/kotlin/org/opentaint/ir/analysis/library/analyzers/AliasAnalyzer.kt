package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.AnalysisDependentEvent
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IfdsResult
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.engine.NewSummaryFact
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.paths.FieldAccessor
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.values

fun AliasAnalyzerFactory(
    generates: (JIRInst) -> List<DomainFact>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = AnalyzerFactory { graph ->
    AliasAnalyzer(graph, generates, sanitizes, sinks, maxPathLength)
}

private class AliasAnalyzer(
    graph: JIRApplicationGraph,
    generates: (JIRInst) -> List<DomainFact>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : TaintAnalyzer(graph, generates, sanitizes, sinks, maxPathLength) {

    override fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent> = buildList {
        ifdsResult.resultFacts.map { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (fact in sinks(inst)) {
                    fact.variable.let {
                        val name = when (val x = it.value) {
                            is JIRArgument -> x.name
                            is JIRLocal -> inst.location.method.flowGraph().instructions
                                .first { x in it.operands.flatMap { it.values } }
                                .lineNumber
                                .toString()
                            null -> (it.accesses[0] as FieldAccessor).field.enclosingClass.simpleName
                            else -> error("Unknown local type")
                        }

                        val fullPath = buildString {
                            append(name)
                            if (it.accesses.isNotEmpty()) {
                                append(".")
                            }
                            append(it.accesses.joinToString("."))
                        }

                        verticesWithTraceGraphNeeded.add(IfdsVertex(inst, fact))

                        add(
                            NewSummaryFact(
                                VulnerabilityLocation(
                                    vulnerabilityType,
                                    IfdsVertex(inst, fact)
                                )
                            )
                        )
                    }
                }
            }
        }
        addAll(super.handleIfdsResult(ifdsResult))
    }
}