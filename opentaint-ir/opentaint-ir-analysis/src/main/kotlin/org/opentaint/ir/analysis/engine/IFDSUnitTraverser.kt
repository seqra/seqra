package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.AnalysisEngine
import org.opentaint.ir.analysis.AnalysisResult
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.points2.Devirtualizer
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.impl.fs.logger

interface AnalysisContext {
    val summaries: Map<JIRMethod, IFDSMethodSummary>
}

class IFDSUnitTraverser<UnitType>(
    private val graph: JIRApplicationGraph,
    private val analyzer: Analyzer,
    private val unitResolver: UnitResolver<UnitType>,
    private val devirtualizer: Devirtualizer,
    private val ifdsInstanceProvider: IFDSInstanceProvider
) : AnalysisEngine {
    private val contextInternal: MutableMap<JIRMethod, IFDSMethodSummary> = mutableMapOf()
    private val context = object : AnalysisContext {
        override val summaries: Map<JIRMethod, IFDSMethodSummary>
            get() = contextInternal
    }

    private val initMethods: MutableSet<JIRMethod> = mutableSetOf()

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JIRMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val crossUnitCallees: MutableMap<JIRMethod, MutableSet<IFDSEdge>> = mutableMapOf()

    override fun analyze(): AnalysisResult {
        logger.info { "Started analysis ${analyzer.name}" }
        logger.info { "Amount of units to analyze: ${unitsQueue.size}" }
        while (unitsQueue.isNotEmpty()) {
            logger.info { "${unitsQueue.size} unit(s) left" }

            // TODO: do smth smarter here
            val next = unitsQueue.minBy { dependsOn[it]!! }
            unitsQueue.remove(next)

            val ifdsInstance = ifdsInstanceProvider.createInstance(graph, analyzer, devirtualizer, context, unitResolver, next)
            for (method in foundMethods[next].orEmpty()) {
                ifdsInstance.addStart(method)
            }

            val results = ifdsInstance.analyze()

            // Relaxing of crossUnitCallees
            for ((_, summary) in results) {
                for ((inc, outcs) in summary.crossUnitCallees) {
                    for (outc in outcs.factsAtCalleeStart) {
                        val calledMethod = graph.methodOf(outc.statement)
                        val newEdge = IFDSEdge(inc, outc)
                        crossUnitCallees.getOrPut(calledMethod) { mutableSetOf() }.add(newEdge)
                    }
                }
            }

            results.forEach { (method, summary) ->
                contextInternal[method] = summary
            }
        }

        logger.info { "Restoring full realisation paths..." }
        // TODO: think about correct filters for overall results
        val vulnerabilities = context.summaries.flatMap { (_, summary) ->
            summary.foundVulnerabilities.vulnerabilities
                .map { VulnerabilityInstance(it.vulnerabilityType, extendRealisationsGraph(it.realisationsGraph)) }
                .filter {
                    it.realisationsGraph.sources.any { source ->
                        graph.methodOf(source.statement) in initMethods || source.domainFact == ZEROFact
                    }
                }
        }

        logger.info { "Analysis completed" }
        return AnalysisResult(vulnerabilities)
    }

    private val TaintRealisationsGraph.methods: List<JIRMethod>
        get() {
            return (edges.keys.map { graph.methodOf(it.statement) } +
                    listOf(graph.methodOf(sink.statement))).distinct()
        }

    private fun extendRealisationsGraph(realisationsGraph: TaintRealisationsGraph): TaintRealisationsGraph {
        var result = realisationsGraph
        val methodQueue: MutableSet<JIRMethod> = realisationsGraph.methods.toMutableSet()
        val addedMethods: MutableSet<JIRMethod> = mutableSetOf()
        while (methodQueue.isNotEmpty()) {
            val method = methodQueue.first()
            methodQueue.remove(method)
            addedMethods.add(method)
            for (crossUnitEdge in crossUnitCallees[method].orEmpty()) {
                val caller = graph.methodOf(crossUnitEdge.u.statement)
                val (entryPoints, upGraph) = context.summaries[caller]!!.crossUnitCallees[crossUnitEdge.u]!!
                val newValue = result.mergeWithUpGraph(upGraph, entryPoints)
                if (result != newValue) {
                    result = newValue
                    for (nMethod in upGraph.methods) {
                        if (nMethod !in addedMethods) {
                            methodQueue.add(nMethod)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun getAllDependencies(method: JIRMethod): Set<JIRMethod> {
        val result = mutableSetOf<JIRMethod>()
        for (inst in method.flowGraph().instructions) {
            devirtualizer.findPossibleCallees(inst).forEach {
                result.add(it)
            }
        }
        return result
    }

    private fun internalAddStart(method: JIRMethod) {
        if (method !in context.summaries) {
            val unit = unitResolver.resolve(method)
            if (method in foundMethods[unit].orEmpty()) {
                return
            }

            unitsQueue.add(unit)
            foundMethods.getOrPut(unit) { mutableSetOf() }.add(method)
            val dependencies = getAllDependencies(method)
            dependencies.forEach { internalAddStart(it) }
            dependsOn[unit] = dependsOn.getOrDefault(unit, 0) + dependencies.size
        }
    }

    override fun addStart(method: JIRMethod) {
        initMethods.add(method)
        internalAddStart(method)
    }
}