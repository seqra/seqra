@file:JvmName("AnalysisMain")
package org.opentaint.ir.analysis

import kotlinx.serialization.Serializable
import mu.KLogging
import org.opentaint.ir.analysis.engine.IfdsUnitManager
import org.opentaint.ir.analysis.engine.IfdsUnitRunner
import org.opentaint.ir.analysis.engine.Summary
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.engine.VulnerabilityInstance
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

internal val logger = object : KLogging() {}.logger

typealias AnalysesOptions = Map<String, String>

@Serializable
data class AnalysisConfig(val analyses: Map<String, AnalysesOptions>)

/**
 * This is the entry point for every analysis.
 * Calling this function will find all vulnerabilities reachable from [methods].
 *
 * @param graph instance of [JIRApplicationGraph] that provides mixture of CFG and call graph
 * (called supergraph in RHS95).
 * Usually built by [newApplicationGraphForAnalysis].
 *
 * @param unitResolver instance of [UnitResolver] which splits all methods into groups of methods, called units.
 * Units are analyzed concurrently, one unit will be analyzed with one call to [IfdsUnitRunner.run] method.
 * In general, larger units mean more precise, but also more resource-consuming analysis, so [unitResolver] allows
 * to reach compromise.
 * It is guaranteed that [Summary] passed to all units is the same, so they can share information through it.
 * However, the order of launching and terminating analysis for units is an implementation detail and may vary even for
 * consecutive calls of this method with same arguments.
 *
 * @param ifdsUnitRunner an [IfdsUnitRunner] instance that will be launched for each unit.
 * This is the main argument that defines the analysis.
 *
 * @param methods the list of method for analysis.
 * Each vulnerability will only be reported if it is reachable from one of these.
 *
 * @param timeoutMillis the maximum time for analysis.
 * Note that this does not include time for precalculations
 * (like searching for reachable methods and splitting them into units) and postcalculations (like restoring traces), so
 * the actual running time of this method may be longer.
 */
fun runAnalysis(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver<*>,
    ifdsUnitRunner: IfdsUnitRunner,
    methods: List<JIRMethod>,
    timeoutMillis: Long = Long.MAX_VALUE
): List<VulnerabilityInstance> {
    return IfdsUnitManager(graph, unitResolver, ifdsUnitRunner, methods, timeoutMillis).analyze()
}