package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IfdsEdge
import org.opentaint.ir.analysis.engine.PathEdgeFact
import org.opentaint.ir.analysis.engine.SummaryFact
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.minus
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.cfg.locals
import org.opentaint.ir.api.cfg.values
import org.opentaint.ir.api.ext.cfg.callExpr

private fun isSourceMethodToGenerates(isSourceMethod: (JIRMethod) -> Boolean): (JIRInst) -> List<TaintAnalysisNode> {
    return generates@{ inst: JIRInst ->
        val callExpr = inst.callExpr?.takeIf { isSourceMethod(it.method.method) } ?: return@generates emptyList()
        if (inst is JIRAssignInst && isSourceMethod(callExpr.method.method)) {
            listOf(TaintAnalysisNode(inst.lhv.toPath()))
        } else {
            emptyList()
        }
    }
}

private fun isSinkMethodToSinks(isSinkMethod: (JIRMethod) -> Boolean): (JIRInst) -> List<TaintAnalysisNode> {
    return sinks@{ inst: JIRInst ->
        val callExpr = inst.callExpr?.takeIf { isSinkMethod(it.method.method) } ?: return@sinks emptyList()
        callExpr.values
            .mapNotNull { it.toPathOrNull() }
            .map { TaintAnalysisNode(it) }
    }
}

fun TaintAnalyzerFactory(
    isSourceMethod: (JIRMethod) -> Boolean,
    isSanitizeMethod: (JIRMethod) -> Boolean,
    isSinkMethod: (JIRMethod) -> Boolean,
    maxPathLength: Int
) = AnalyzerFactory { graph ->
    val generates = isSourceMethodToGenerates(isSourceMethod)
    val sinks = isSinkMethodToSinks(isSinkMethod)

    val sanitizes = { expr: JIRExpr, fact: TaintNode ->
        if (expr !is JIRCallExpr) {
            false
        } else {
            if (isSanitizeMethod(expr.method.method) && fact.activation == null) {
                expr.values.any {
                    it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
                }
            } else {
                false
            }
        }
    }

    TaintAnalyzer(graph, generates, sanitizes, sinks, maxPathLength)
}

private val List<String>.asMethodMatchers: (JIRMethod) -> Boolean
    get() = { method: JIRMethod ->
        any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") }
    }

fun TaintAnalyzerFactory(
    sourceMethodMatchers: List<String>,
    sanitizeMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int
) = TaintAnalyzerFactory(
    sourceMethodMatchers.asMethodMatchers,
    sanitizeMethodMatchers.asMethodMatchers,
    sinkMethodMatchers.asMethodMatchers,
    maxPathLength
)

open class TaintAnalyzer(
    graph: JIRApplicationGraph,
    generates: (JIRInst) -> List<DomainFact>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    val sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(graph, maxPathLength, generates, sanitizes)

    companion object {
        const val vulnerabilityType: String = "taint analysis"
    }

    override fun getSummaryFacts(edge: IfdsEdge): List<SummaryFact> {
        return if (edge.v.domainFact in sinks(edge.v.statement)) {
            listOf(VulnerabilityLocation(vulnerabilityType, edge.v))
        } else {
            emptyList()
        }
    }
}

fun TaintBackwardAnalyzerFactory(
    isSourceMethod: (JIRMethod) -> Boolean,
    isSinkMethod: (JIRMethod) -> Boolean,
    maxPathLength: Int
) = AnalyzerFactory { graph ->
    val generates = isSourceMethodToGenerates(isSourceMethod)
    val sinks = isSinkMethodToSinks(isSinkMethod)

    TaintBackwardAnalyzer(graph, generates, sinks, maxPathLength)
}

fun TaintBackwardAnalyzerFactory(
    sourceMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int
) = TaintBackwardAnalyzerFactory(
    sourceMethodMatchers.asMethodMatchers,
    sinkMethodMatchers.asMethodMatchers,
    maxPathLength
)

private class TaintBackwardAnalyzer(
    val graph: JIRApplicationGraph,
    generates: (JIRInst) -> List<DomainFact>,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int
) : Analyzer {
    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace = TaintBackwardFunctions(graph, generates, sinks, maxPathLength)

    override fun getSummaryFacts(edge: IfdsEdge): List<SummaryFact> = buildList {
        if (edge.v.statement in graph.exitPoints(edge.method)) {
            add(PathEdgeFact(IfdsEdge(edge.v, edge.v)))
        }
    }
}

private class TaintForwardFunctions(
    graph: JIRApplicationGraph,
    private val maxPathLength: Int,
    private val generates: (JIRInst) -> List<DomainFact>,
    private val sanitizes: (JIRExpr, TaintNode) -> Boolean
) : AbstractTaintForwardFunctions(graph.classpath) {
    override fun transmitDataFlow(from: JIRExpr, to: JIRValue, atInst: JIRInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val default = if (dropFact || (sanitizes(from, fact) && fact.variable == (from as? JIRInstanceCallExpr)?.instance?.toPath())) {
            emptyList()
        } else {
            listOf(fact)
        }

        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val newPossibleTaint = if (sanitizes(from, fact)) emptyList() else listOf(fact.moveToOtherPath(toPath))

        val fromPath = from.toPathOrNull()
        if (fromPath != null) {
            return if (sanitizes(from, fact)) {
                default
            } else if (fromPath.startsWith(fact.variable)) {
                default + newPossibleTaint
            } else {
                normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
            }
        }

        return if (from.values.any { it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull()) }) {
            val instanceOrNull = (from as? JIRInstanceCallExpr)?.instance
            if (instanceOrNull != null && !sanitizes(from, fact)) {
                default + newPossibleTaint + fact.moveToOtherPath(instanceOrNull.toPath())
            } else {
                default + newPossibleTaint
            }
        } else if (fact.variable.startsWith(toPath)) {
            emptyList()
        } else {
            default
        }
    }

    override fun transmitDataFlowAtNormalInst(inst: JIRInst, nextInst: JIRInst, fact: DomainFact): List<DomainFact> {
        if (fact == ZEROFact) {
            return generates(inst) + listOf(ZEROFact)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val callExpr = inst.callExpr ?: return listOf(fact)
        val instance = (callExpr as? JIRInstanceCallExpr)?.instance ?: return listOf(fact)
        val factIsPassed = callExpr.values.any {
            it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
        }

        if (instance.toPath() == fact.variable && sanitizes(callExpr, fact)) {
            return emptyList()
        }

        return if (factIsPassed && !sanitizes(callExpr, fact)) {
            listOf(fact) + fact.moveToOtherPath(instance.toPath())
        } else {
            listOf(fact)
        }
    }

    override fun obtainPossibleStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        val method = startStatement.location.method

        // Possibly null arguments
        return listOf(ZEROFact) + method.flowGraph().locals
            .filterIsInstance<JIRArgument>()
            .map { TaintAnalysisNode(AccessPath.fromLocal(it)) }
    }
}

private class TaintBackwardFunctions(
    graph: JIRApplicationGraph,
    val generates: (JIRInst) -> List<DomainFact>,
    val sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {
    override fun transmitBackDataFlow(from: JIRValue, to: JIRExpr, atInst: JIRInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + sinks(atInst)
        }

        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val factPath = fact.variable
        val default = if (dropFact || fact in generates(atInst)) emptyList() else listOf(fact)
        val fromPath = from.toPathOrNull() ?: return default
        val toPath = to.toPathOrNull()

        if (toPath != null) {
            val diff = factPath.minus(fromPath)
            if (diff != null) {
                return listOf(fact.moveToOtherPath(AccessPath.fromOther(toPath, diff).limit(maxPathLength)))
            }
        } else if (factPath.startsWith(fromPath) || (to is JIRInstanceCallExpr && factPath.startsWith(to.instance.toPath()))) {
            return to.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it) }
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(inst: JIRInst, nextInst: JIRInst, fact: DomainFact): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(fact) + sinks(inst)
        }
        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val callExpr = inst.callExpr as? JIRInstanceCallExpr ?: return listOf(fact)
        if (fact.variable.startsWith(callExpr.instance.toPath())) {
            return inst.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it) }
        }

        return listOf(fact)
    }
}