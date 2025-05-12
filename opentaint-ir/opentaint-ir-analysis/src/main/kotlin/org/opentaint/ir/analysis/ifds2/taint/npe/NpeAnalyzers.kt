package org.opentaint.ir.analysis.ifds2.taint.npe

import mu.KotlinLogging
import org.opentaint.ir.analysis.config.CallPositionToJIRValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.ifds2.Analyzer
import org.opentaint.ir.analysis.ifds2.taint.EdgeForOtherRunner
import org.opentaint.ir.analysis.ifds2.taint.NewSummaryEdge
import org.opentaint.ir.analysis.ifds2.taint.NewVulnerability
import org.opentaint.ir.analysis.ifds2.taint.TaintEdge
import org.opentaint.ir.analysis.ifds2.taint.TaintEvent
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.TaintVertex
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.Vulnerability
import org.opentaint.ir.analysis.paths.isDereferencedAt
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSink

private val logger = KotlinLogging.logger {}

class NpeAnalyzer(
    private val graph: JIRApplicationGraph,
) : Analyzer<TaintFact, TaintEvent> {

    override val flowFunctions: ForwardNpeFlowFunctions by lazy {
        ForwardNpeFlowFunctions(graph.classpath, graph)
    }

    private val taintConfigurationFeature: TaintConfigurationFeature?
        get() = flowFunctions.taintConfigurationFeature

    private fun isExitPoint(statement: JIRInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    private fun isSink(statement: JIRInst, fact: TaintFact): Boolean {
        // TODO
        return false
    }

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<TaintEvent> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        if (edge.to.fact is Tainted && edge.to.fact.mark == TaintMark.NULLNESS) {
            if (edge.to.fact.variable.isDereferencedAt(edge.to.statement)) {
                val message = "NPE" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge)
                logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
            }
        }

        var defaultBehavior = true
        run {
            val callExpr = edge.to.statement.callExpr ?: return@run
            val callee = callExpr.method.method

            val config = taintConfigurationFeature?.let { feature ->
                logger.trace { "Extracting config for $callee" }
                feature.getConfigForMethod(callee)
            } ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                CallPositionToJIRValueResolver(edge.to.statement),
            )
            var triggeredItem: TaintMethodSink? = null
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                defaultBehavior = false
                if (item.condition.accept(conditionEvaluator)) {
                    triggeredItem = item
                    break
                }
                // FIXME: unconditionally let it be the sink.
                // triggeredItem = item
                // break
            }
            if (triggeredItem != null) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method} on $triggeredItem" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge, rule = triggeredItem)
                // logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
        }

        if (defaultBehavior) {
            // Default ("config"-less) behavior:
            if (isSink(edge.to.statement, edge.to.fact)) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method}" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge)
                // logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex,
        callee: TaintVertex,
    ): List<TaintEvent> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee)))
    }
}

// class BackwardTaintAnalyzer(
//     private val graph: JIRApplicationGraph,
// ) : Analyzer<TaintFact, TaintEvent> {
//
//     override val flowFunctions: BackwardNpeFlowFunctions by lazy {
//         BackwardNpeFlowFunctions(graph.classpath, graph)
//     }
//
//     private fun isExitPoint(statement: JIRInst): Boolean {
//         return statement in graph.exitPoints(statement.location.method)
//     }
//
//     private fun isSink(statement: JIRInst, fact: TaintFact): Boolean {
//         // TODO
//         return false
//     }
//
//     override fun handleNewEdge(
//         edge: TaintEdge,
//     ): List<TaintEvent> = buildList {
//         if (isExitPoint(edge.to.statement)) {
//             add(EdgeForOtherRunner(Edge(edge.to, edge.to)))
//         }
//     }
//
//     override fun handleCrossUnitCall(
//         caller: TaintVertex,
//         callee: TaintVertex,
//     ): List<TaintEvent> {
//         return emptyList()
//     }
// }
