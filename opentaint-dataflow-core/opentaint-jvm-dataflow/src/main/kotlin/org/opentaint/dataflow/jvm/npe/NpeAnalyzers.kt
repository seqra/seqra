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

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.config.CallPositionToValueResolver
import org.opentaint.dataflow.config.FactAwareConditionEvaluator
import org.opentaint.dataflow.ifds.Analyzer
import org.opentaint.dataflow.ifds.Reason
import org.opentaint.dataflow.jvm.graph.JIRApplicationGraph
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.taint.EdgeForOtherRunner
import org.opentaint.dataflow.taint.NewSummaryEdge
import org.opentaint.dataflow.taint.NewVulnerability
import org.opentaint.dataflow.taint.TaintDomainFact
import org.opentaint.dataflow.taint.TaintEdge
import org.opentaint.dataflow.taint.TaintEvent
import org.opentaint.dataflow.taint.TaintVertex
import org.opentaint.dataflow.taint.TaintVulnerability
import org.opentaint.dataflow.taint.Tainted

private val logger = mu.KotlinLogging.logger {}

class NpeAnalyzer(
    private val traits: JIRTraits,
    private val graph: JIRApplicationGraph,
    private val getConfigForMethod: (JIRMethod) -> List<TaintConfigurationItem>?,
) : Analyzer<TaintDomainFact, TaintEvent<JIRInst>, JIRMethod, JIRInst> {

    override val flowFunctions: ForwardNpeFlowFunctions by lazy {
        ForwardNpeFlowFunctions(traits, graph, getConfigForMethod)
    }

    private fun isExitPoint(statement: JIRInst): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<JIRInst>,
    ): List<TaintEvent<JIRInst>> = with(traits) {
        buildList {
            if (isExitPoint(edge.to.statement)) {
                add(NewSummaryEdge(edge))
            }

            val edgeToFact = edge.to.fact

            if (edgeToFact is Tainted && edgeToFact.mark == TaintMark.NULLNESS) {
                if (edgeToFact.variable.isDereferencedAt(traits, edge.to.statement)) {
                    val message = "NPE" // TODO
                    val vulnerability = TaintVulnerability(message, sink = edge.to)
                    logger.info {
                        val m = graph.methodOf(vulnerability.sink.statement)
                        "Found sink=${vulnerability.sink} in $m"
                    }
                    add(NewVulnerability(vulnerability))
                }
            }

            run {
                val callExpr = getCallExpr(edge.to.statement) ?: return@run
                val callee = getCallee(callExpr)

                val config = getConfigForMethod(callee) ?: return@run

                // TODO: not always we want to skip sinks on Zero facts.
                //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
                if (edgeToFact !is Tainted) {
                    return@run
                }

                // Determine whether 'edge.to' is a sink via config:
                val conditionEvaluator = FactAwareConditionEvaluator(
                    traits,
                    edgeToFact,
                    CallPositionToValueResolver(traits, edge.to.statement),
                )
                for (item in config.filterIsInstance<TaintMethodSink>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        val message = item.ruleNote
                        val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                        logger.trace {
                            val m = graph.methodOf(vulnerability.sink.statement)
                            "Found sink=${vulnerability.sink} in $m on $item"
                        }
                        add(NewVulnerability(vulnerability))
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<JIRInst>,
        callee: TaintVertex<JIRInst>,
    ): List<TaintEvent<JIRInst>> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}
