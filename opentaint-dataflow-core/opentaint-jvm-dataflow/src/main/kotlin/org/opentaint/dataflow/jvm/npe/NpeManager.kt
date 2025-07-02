/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.jvm.npe

import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ifds.UniRunner
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.taint.TaintManager
import org.opentaint.dataflow.taint.TaintRunner
import org.opentaint.dataflow.taint.TaintZeroFact

private val logger = mu.KotlinLogging.logger {}

context(JIRTraits)
class NpeManager(
    graph: ApplicationGraph<JIRMethod, JIRInst>,
    unitResolver: UnitResolver<JIRMethod>,
    private val getConfigForMethod: (JIRMethod) -> List<TaintConfigurationItem>?,
) : TaintManager<JIRMethod, JIRInst>(graph, unitResolver, useBidiRunner = false, getConfigForMethod) {
    override fun newRunner(
        unit: UnitType,
    ): TaintRunner<JIRMethod, JIRInst> {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        val analyzer = NpeAnalyzer(graph, getConfigForMethod)
        val runner = UniRunner(
            graph = graph,
            analyzer = analyzer,
            manager = this@NpeManager,
            unitResolver = unitResolver,
            unit = unit,
            zeroFact = TaintZeroFact
        )

        runnerForUnit[unit] = runner
        return runner
    }

    override fun addStart(method: JIRMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        // Note: DO NOT add deps here!
    }
}

fun jirNpeManager(
    graph: ApplicationGraph<JIRMethod, JIRInst>,
    unitResolver: JIRUnitResolver,
    getConfigForMethod: ((JIRMethod) -> List<TaintConfigurationItem>?)? = null
): NpeManager = with(JIRTraits) {
    val config: (JIRMethod) -> List<TaintConfigurationItem>? = getConfigForMethod ?: run {
        val taintConfigurationFeature = (graph.project as JIRClasspath).features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }

        return@run { method: JIRMethod -> taintConfigurationFeature?.getConfigForMethod(method) }
    }

    NpeManager(graph, unitResolver, config)
}
