package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.config.CallPositionToValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.ifds.Analyzer
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Reason
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.ext.callExpr
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.impl.cfg.util.loops
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}

context(Traits<Method, Statement>)
class TaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    private val getConfigForMethod: (ForwardTaintFlowFunctions<Method, Statement>.(Method) -> List<TaintConfigurationItem>?)? = null,
) : Analyzer<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override val flowFunctions: ForwardTaintFlowFunctions<Method, Statement> by lazy {
        if (getConfigForMethod != null) {
            ForwardTaintFlowFunctions(graph, getConfigForMethod)
        } else {
            ForwardTaintFlowFunctions(graph)
        }
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        run {
            val callExpr = edge.to.statement.callExpr ?: return@run

            val callee = callExpr.callee

            val config = with(flowFunctions) { getConfigForMethod(callee) } ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                CallPositionToValueResolver(edge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                    add(NewVulnerability(vulnerability))
                }
            }
        }

        if (TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                if (statement is JIRIfInst) {
                    val loops = statement.location.method.flowGraph().loops
                    val loopHeads = loops.map { it.head }
                    if (statement in loopHeads) {
                        for (s in statement.condition.operands) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                    val loops = statement.location.method.flowGraph().loops
                    if (loops.any { statement in it.instructions }) {
                        for (s in statement.condition.operands) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                    if (loops.any { statement in it.instructions }) {
                        for (s in statement.condition.operands) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                }
            }
        }
        if (TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                    val expr = statement.rhv
                        val arg = expr.params.first()
                        if (arg.toPath() == fact.variable) {
                            val message = "Untrusted array size"
                            val vulnerability = TaintVulnerability(message, sink = edge.to)
                            add(NewVulnerability(vulnerability))
                        }
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Method, Statement>,
        callee: TaintVertex<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}

context(Traits<Method, Statement>)
class BackwardTaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override val flowFunctions: BackwardTaintFlowFunctions<Method, Statement> by lazy {
        BackwardTaintFlowFunctions(graph)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to), reason = Reason.External))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Method, Statement>,
        callee: TaintVertex<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> {
        return emptyList()
    }
}
