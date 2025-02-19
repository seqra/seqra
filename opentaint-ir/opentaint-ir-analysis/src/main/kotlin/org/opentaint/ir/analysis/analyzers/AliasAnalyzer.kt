package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IfdsResult
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.paths.FieldAccessor
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.values

fun AliasAnalyzerFactory(
    generates: (JIRInst) -> List<DomainFact>,
    isSink: (JIRInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) = AnalyzerFactory { graph ->
    AliasAnalyzer(graph.classpath, generates, isSink, maxPathLength)
}

private class AliasAnalyzer(
    cp: JIRClasspath,
    generates: (JIRInst) -> List<DomainFact>,
    isSink: (JIRInst, DomainFact) -> Boolean,
    maxPathLength: Int
) : TaintAnalyzer(cp, generates, isSink, maxPathLength) {

    override fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<VulnerabilityLocation> {
        val vulnerabilities = mutableListOf<VulnerabilityLocation>()
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
                            VulnerabilityLocation(
                                vulnerabilityType,
                                IfdsVertex(inst, fact)
                            )
                        )
                    }
                }
            }
        }
        return vulnerabilities
    }
}