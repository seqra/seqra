package org.opentaint.ir.analysis.library.analyzers

import mu.KotlinLogging
import org.opentaint.ir.analysis.config.BasicConditionEvaluator
import org.opentaint.ir.analysis.config.CallPositionToJIRValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.config.TaintActionEvaluator
import org.opentaint.ir.analysis.engine.AbstractAnalyzer
import org.opentaint.ir.analysis.engine.AnalysisDependentEvent
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.EdgeForOtherRunnerQuery
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IfdsEdge
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.engine.NewSummaryFact
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.toDomainFact
import org.opentaint.ir.analysis.paths.minus
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
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
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.This

private val logger = KotlinLogging.logger {}

fun isSourceMethodToGenerates(isSourceMethod: (JIRMethod) -> Boolean): (JIRInst) -> List<TaintAnalysisNode> {
    return generates@{ inst: JIRInst ->
        val callExpr = inst.callExpr?.takeIf { isSourceMethod(it.method.method) }
            ?: return@generates emptyList()
        if (inst is JIRAssignInst && isSourceMethod(callExpr.method.method)) {
            listOf(TaintAnalysisNode(inst.lhv.toPath(), nodeType = "TAINT"))
        } else {
            emptyList()
        }
    }
}

fun isSinkMethodToSinks(isSinkMethod: (JIRMethod) -> Boolean): (JIRInst) -> List<TaintAnalysisNode> {
    return sinks@{ inst: JIRInst ->
        val callExpr = inst.callExpr?.takeIf { isSinkMethod(it.method.method) }
            ?: return@sinks emptyList()
        callExpr.values
            .mapNotNull { it.toPathOrNull() }
            .map { TaintAnalysisNode(it, nodeType = "TAINT") }
    }
}

fun isSanitizeMethodToSanitizes(isSanitizeMethod: (JIRMethod) -> Boolean): (JIRExpr, TaintNode) -> Boolean {
    return sanitizes@{ expr: JIRExpr, fact: TaintNode ->
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
}

internal val List<String>.asMethodMatchers: (JIRMethod) -> Boolean
    get() = { method: JIRMethod ->
        any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") }
    }

abstract class TaintAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int,
) : AbstractAnalyzer(graph) {
    abstract val generates: (JIRInst) -> List<DomainFact>
    abstract val sanitizes: (JIRExpr, TaintNode) -> Boolean
    abstract val sinks: (JIRInst) -> List<TaintAnalysisNode>

    override val flowFunctions: TaintForwardFunctions by lazy {
        TaintForwardFunctions(graph, maxPathLength, generates, sanitizes)
    }

    override val isMainAnalyzer: Boolean
        get() = true

    protected abstract fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        val configOk = run {
            val callExpr = edge.to.statement.callExpr ?: return@run false
            val callee = callExpr.method.method

            val config = flowFunctions.taintConfigurationFeature?.getConfigForMethod(callee)
                ?: return@run false

            // TODO: not always we want to skip sinks on ZeroFacts. Some rules might have ConstantTrue or just true (when evaluated with ZeroFact) condition.
            if (edge.to.domainFact !is TaintNode) {
                return@run false
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                Tainted(edge.to.domainFact),
                CallPositionToJIRValueResolver(edge.to.statement),
            )
            var isSink = false
            var triggeredItem: TaintMethodSink? = null
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    isSink = true
                    triggeredItem = item
                    break
                }
                // FIXME: unconditionally let it be the sink.
                // isSink = true
                // triggeredItem = item
                // break
            }
            if (isSink) {
                val desc = generateDescriptionForSink(edge.to)
                val vulnerability = VulnerabilityLocation(desc, edge.to, edge, rule = triggeredItem)
                logger.info { "Found sink: $vulnerability in ${vulnerability.method}" }
                add(NewSummaryFact(vulnerability))
                verticesWithTraceGraphNeeded.add(edge.to)
            }
            true
        }

        if (!configOk) {
            // "config"-less behavior:
            if (edge.to.domainFact in sinks(edge.to.statement)) {
                val desc = generateDescriptionForSink(edge.to)
                val vulnerability = VulnerabilityLocation(desc, edge.to, edge)
                logger.info { "Found sink: $vulnerability in ${vulnerability.method}" }
                add(NewSummaryFact(vulnerability))
                verticesWithTraceGraphNeeded.add(edge.to)
            }
        }

        // "Default" behavior:
        addAll(super.handleNewEdge(edge))
    }
}

abstract class TaintBackwardAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int,
) : AbstractAnalyzer(graph) {
    abstract val generates: (JIRInst) -> List<DomainFact>
    abstract val sinks: (JIRInst) -> List<TaintAnalysisNode>

    override val isMainAnalyzer: Boolean
        get() = false

    override val flowFunctions: TaintBackwardFunctions by lazy {
        TaintBackwardFunctions(graph, generates, sinks, maxPathLength)
    }

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        if (edge.to.statement in graph.exitPoints(edge.method)) {
            add(EdgeForOtherRunnerQuery(IfdsEdge(edge.to, edge.to)))
        }
    }
}

class TaintForwardFunctions(
    graph: JIRApplicationGraph,
    private val maxPathLength: Int,
    private val generates: (JIRInst) -> List<DomainFact>,
    private val sanitizes: (JIRExpr, TaintNode) -> Boolean,
) : AbstractTaintForwardFunctions(graph.classpath) {

    override fun transmitDataFlow(
        from: JIRExpr,
        to: JIRValue,
        atInst: JIRInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val default: List<DomainFact> = if (
            dropFact ||
            (sanitizes(from, fact) && fact.variable == (from as? JIRInstanceCallExpr)?.instance?.toPath())
        ) {
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

        if (
            from.values.any {
                it.toPathOrNull().startsWith(fact.variable) ||
                    fact.variable.startsWith(it.toPathOrNull())
            }
        ) {
            val instanceOrNull = (from as? JIRInstanceCallExpr)?.instance
            if (instanceOrNull != null && !sanitizes(from, fact)) {
                val instancePath = instanceOrNull.toPathOrNull()
                if (instancePath != null) {
                    return default + newPossibleTaint + fact.moveToOtherPath(instancePath)
                }
            }
            return default + newPossibleTaint
        } else if (fact.variable.startsWith(toPath)) {
            return emptyList()
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(
        inst: JIRInst,
        nextInst: JIRInst, // unused
        fact: DomainFact,
    ): List<DomainFact> {
        // Generate new facts:
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(inst)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        // Pass-through:
        val callExpr = inst.callExpr ?: return listOf(fact)
        if (callExpr !is JIRInstanceCallExpr) {
            return listOf(fact)
        }
        val instance = callExpr.instance

        // Sanitize:
        if (instance.toPath() == fact.variable && sanitizes(callExpr, fact)) {
            return emptyList()
        }

        // TODO: do no do this:
        // val factIsPassed = callExpr.values.any {
        //     it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
        // }
        // return if (factIsPassed && !sanitizes(callExpr, fact)) {
        //     // Pass-through, but also (?) taint the 'instance'
        //     listOf(fact) + fact.moveToOtherPath(instance.toPath())
        // } else {
        //     // Pass-through
        //     listOf(fact)
        // }

        // Pass-through
        return listOf(fact)
    }

    override fun obtainPossibleStartFacts(startStatement: JIRInst): List<DomainFact> = buildList {
        add(ZEROFact)

        val method = startStatement.location.method
        val config = taintConfigurationFeature?.getConfigForMethod(method)
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance

                    is Argument -> method.flowGraph().locals
                        .filterIsInstance<JIRArgument>()
                        .singleOrNull { it.index == position.index }
                        ?: error("Cannot resolve $position for $method")

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }
            val actionEvaluator = TaintActionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance.toPathOrNull()
                        ?: error("Cannot resolve $position for $method")

                    is Argument -> {
                        val p = method.parameters[position.index]
                        val t = cp.findTypeOrNull(p.type)
                        if (t != null) {
                            JIRArgument.of(p.index, p.name, t).toPathOrNull()
                        } else {
                            null
                        }
                            ?: error("Cannot resolve $position for $method")
                    }

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        when (action) {
                            is AssignMark -> {
                                add(actionEvaluator.evaluate(action).toDomainFact())
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }
        }
    }
}

class TaintBackwardFunctions(
    graph: JIRApplicationGraph,
    val generates: (JIRInst) -> List<DomainFact>,
    val sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {

    override fun transmitBackDataFlow(
        from: JIRValue,
        to: JIRExpr,
        atInst: JIRInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact> {
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
                val newPath = (toPath / diff).limit(maxPathLength)
                return listOf(fact.moveToOtherPath(newPath))
            }
        } else if (factPath.startsWith(fromPath) || (to is JIRInstanceCallExpr && factPath.startsWith(to.instance.toPath()))) {
            return to.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it, nodeType = "TAINT") }
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(
        inst: JIRInst,
        nextInst: JIRInst,
        fact: DomainFact,
    ): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(fact) + sinks(inst)
        }
        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val callExpr = inst.callExpr as? JIRInstanceCallExpr ?: return listOf(fact)
        if (fact.variable.startsWith(callExpr.instance.toPath())) {
            return inst.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it, nodeType = "TAINT") }
        }

        return listOf(fact)
    }
}
