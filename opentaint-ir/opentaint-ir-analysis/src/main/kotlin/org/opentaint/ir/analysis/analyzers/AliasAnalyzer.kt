package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.AnalysisResult
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IFDSResult
import org.opentaint.ir.analysis.engine.IFDSVertex
import org.opentaint.ir.analysis.paths.FieldAccessor
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.values

class AliasAnalyzer(
    graph: JIRApplicationGraph,
    generates: (JIRInst) -> List<DomainFact>,
    isSink: (JIRInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) : TaintAnalyzer(graph, generates, isSink, maxPathLength) {

    override fun calculateSources(ifdsResult: IFDSResult): AnalysisResult {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (isSink(inst, fact)) {
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

                        vulnerabilities.add(
                            VulnerabilityInstance(
                                value,
                                ifdsResult.resolveTaintRealisationsGraph(IFDSVertex(inst, fact))
                            )
                        )
                    }
                }
            }
        }
        return AnalysisResult(vulnerabilities)
    }

    override val name: String = value
}