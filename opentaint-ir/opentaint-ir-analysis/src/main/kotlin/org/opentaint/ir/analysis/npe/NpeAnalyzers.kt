package org.opentaint.ir.analysis.npe

import org.opentaint.ir.analysis.config.CallPositionToValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.ifds.Analyzer
import org.opentaint.ir.analysis.ifds.Reason
import org.opentaint.ir.analysis.taint.EdgeForOtherRunner
import org.opentaint.ir.analysis.taint.NewSummaryEdge
import org.opentaint.ir.analysis.taint.NewVulnerability
import org.opentaint.ir.analysis.taint.TaintDomainFact
import org.opentaint.ir.analysis.taint.TaintEdge
import org.opentaint.ir.analysis.taint.TaintEvent
import org.opentaint.ir.analysis.taint.TaintVertex
import org.opentaint.ir.analysis.taint.TaintVulnerability
import org.opentaint.ir.analysis.taint.Tainted
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.ext.callExpr
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}

context(Traits<Method, Statement>)
class NpeAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override val flowFunctions: ForwardNpeFlowFunctions<Method, Statement> by lazy {
        ForwardNpeFlowFunctions(graph)
    }

    private val taintConfigurationFeature: TaintConfigurationFeature?
        get() = flowFunctions.taintConfigurationFeature

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        if (edge.to.fact is Tainted && edge.to.fact.mark == TaintMark.NULLNESS) {
            if (edge.to.fact.variable.isDereferencedAt(edge.to.statement)) {
                val message = "NPE" // TODO
                val vulnerability = TaintVulnerability(message, sink = edge.to)
                logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
            }
        }

        run {
            val callExpr = edge.to.statement.callExpr ?: return@run
            val callee = callExpr.callee

            val config = taintConfigurationFeature?.let { feature ->
                if (callee is JIRMethod) {
                    logger.trace { "Extracting config for $callee" }
                    feature.getConfigForMethod(callee)
                } else {
                    error("Cannot extract config for $callee")
                }
            } ?: return@run

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
                    logger.trace { "Found sink at ${edge.to} in ${edge.method} on $item" }
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    add(NewVulnerability(vulnerability))
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
