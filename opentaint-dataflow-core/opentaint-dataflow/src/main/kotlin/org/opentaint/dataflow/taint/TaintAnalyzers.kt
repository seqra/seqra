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

package org.opentaint.dataflow.taint

import mu.KLogging
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.config.CallPositionToValueResolver
import org.opentaint.dataflow.config.FactAwareConditionEvaluator
import org.opentaint.dataflow.ifds.Analyzer
import org.opentaint.dataflow.ifds.Edge
import org.opentaint.dataflow.ifds.Reason
import org.opentaint.dataflow.util.Traits

private val logger = object : KLogging() {}.logger

class TaintAnalyzer<Method, Statement>(
    private val traits: Traits<Method, Statement>,
    private val graph: ApplicationGraph<Method, Statement>,
    private val getConfigForMethod: (Method) -> List<TaintConfigurationItem>?
) : Analyzer<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: ForwardTaintFlowFunctions<Method, Statement> by lazy {
        ForwardTaintFlowFunctions(traits, graph, getConfigForMethod)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<Statement>,
    ): List<TaintEvent<Statement>> = with(traits) {
        buildList {
            if (isExitPoint(edge.to.statement)) {
                add(NewSummaryEdge(edge))
            }

            run {
                val callExpr = getCallExpr(edge.to.statement) ?: return@run

                val callee = getCallee(callExpr)

                val config = getConfigForMethod(callee) ?: return@run

                // TODO: not always we want to skip sinks on Zero facts.
                //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
                if (edge.to.fact !is Tainted) {
                    return@run
                }

                // Determine whether 'edge.to' is a sink via config:
                val conditionEvaluator = FactAwareConditionEvaluator(
                    traits,
                    edge.to.fact,
                    CallPositionToValueResolver(traits, edge.to.statement),
                )
                for (item in config.filterIsInstance<TaintMethodSink>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        val message = item.ruleNote
                        val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                        logger.info {
                            "Found sink=${vulnerability.sink} in ${vulnerability.method} on $item"
                        }
                        add(NewVulnerability(vulnerability))
                    }
                }
            }

            if (TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK) {
                val statement = edge.to.statement
                val fact = edge.to.fact
                if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                    val branchExprCondition = getBranchExprCondition(statement)
                    if (branchExprCondition != null && isLoopHead(statement)) {
                        val conditionOperands = getValues(branchExprCondition)
                        for (s in conditionOperands) {
                            val p = convertToPath(s)
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                }
            }
            if (TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK) {
                val statement = edge.to.statement
                val fact = edge.to.fact
                if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                    val arrayAllocation = getArrayAllocation(statement)
                    if (arrayAllocation != null) {
                        for (arg in getValues(arrayAllocation)) {
                            if (convertToPath(arg) == fact.variable) {
                                val message = "Untrusted array size"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                }
            }
            if (TaintAnalysisOptions.UNTRUSTED_INDEX_ARRAY_ACCESS_SINK) {
                val statement = edge.to.statement
                val fact = edge.to.fact
                if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                    val arrayAccessIndex = getArrayAccessIndex(statement)
                    if (arrayAccessIndex != null) {
                        if (convertToPath(arrayAccessIndex) == fact.variable) {
                            val message = "Untrusted index for access array"
                            val vulnerability = TaintVulnerability(message, sink = edge.to)
                            add(NewVulnerability(vulnerability))
                        }
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Statement>,
        callee: TaintVertex<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}

class BackwardTaintAnalyzer<Method, Statement>(
    private val traits: Traits<Method, Statement>,
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: BackwardTaintFlowFunctions<Method, Statement> by lazy {
        BackwardTaintFlowFunctions(traits, graph)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to), reason = Reason.External))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Statement>,
        callee: TaintVertex<Statement>,
    ): List<TaintEvent<Statement>> {
        return emptyList()
    }
}
