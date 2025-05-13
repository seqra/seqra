package org.opentaint.ir.analysis.library.analyzers

import mu.KotlinLogging
import org.opentaint.ir.analysis.config.BasicConditionEvaluator
import org.opentaint.ir.analysis.config.CallPositionToAccessPathResolver
import org.opentaint.ir.analysis.config.CallPositionToJIRValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.config.TaintActionEvaluator
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.toDomainFact
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough

private val logger = KotlinLogging.logger {}

abstract class AbstractTaintForwardFunctions(
    protected val cp: JIRClasspath,
) : FlowFunctionsSpace {

    internal val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    protected abstract fun transmitDataFlow(
        from: JIRExpr,
        to: JIRValue,
        atInst: JIRInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact>

    protected abstract fun transmitDataFlowAtNormalInst(
        inst: JIRInst,
        nextInst: JIRInst,
        fact: DomainFact,
    ): List<DomainFact>

    final override fun obtainSequentFlowFunction(
        current: JIRInst,
        next: JIRInst,
    ) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == current) {
            listOf(fact.activatedCopy)
        } else if (current is JIRAssignInst) {
            // Note: 'next' is ignored
            transmitDataFlow(current.rhv, current.lhv, current, fact, false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    final override fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        callee: JIRMethod,
    ) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == callStatement) {
            return@FlowFunctionInstance emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val formalParams = cp.getArgumentsOf(callee)
        buildSet {
            // TODO: when dropFact=true, consider removing (note: once!) the fact afterwards

            formalParams.zip(actualParams).forEach { (formal, actual) ->
                addAll(transmitDataFlow(actual, formal, callStatement, fact, dropFact = true))
            }

            if (callExpr is JIRInstanceCallExpr) {
                addAll(transmitDataFlow(callExpr.instance, callee.thisInstance, callStatement, fact, dropFact = true))
            }

            if (fact is TaintNode && fact.variable.isStatic) {
                add(fact)
            }

            if (fact == ZEROFact) {
                addAll(obtainPossibleStartFacts(callStatement))
            }
        }
    }

    final override fun obtainCallToReturnFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        graph: JIRApplicationGraph,
    ) = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.method.method

        // FIXME: adhoc for constructors:
        if (callee.isConstructor) {
            return@FlowFunctionInstance listOf(fact)
        }

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        if (fact is TaintNode && callExpr is JIRDynamicCallExpr && callee.enclosingClass.name == "java.lang.invoke.StringConcatFactory" && callStatement is JIRAssignInst) {
            for (arg in callExpr.args) {
                if (arg.toPath() == fact.variable) {
                    return@FlowFunctionInstance setOf(
                        fact,
                        Tainted(fact).copy(variable = callStatement.lhv.toPath()).toDomainFact()
                    )
                }
            }
            return@FlowFunctionInstance setOf(fact)
        }

        val config = taintConfigurationFeature?.let { feature ->
            logger.trace { "Extracting config for $callee" }
            feature.getConfigForMethod(callee)
        }

        if (fact == ZEROFact) {
            val facts = mutableSetOf<Tainted>()
            if (config != null) {
                val conditionEvaluator = BasicConditionEvaluator(CallPositionToJIRValueResolver(callStatement))
                val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
                for (item in config.filterIsInstance<TaintMethodSource>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        for (action in item.actionsAfter) {
                            when (action) {
                                is AssignMark -> {
                                    facts += actionEvaluator.evaluate(action)
                                }

                                else -> error("$action is not supported for $item")
                            }
                        }
                    }
                }
            }
            logger.debug { "call-to-return-site flow function for callee=$callee, fact=$fact returns ${facts.size} facts: $facts" }
            return@FlowFunctionInstance facts.map { it.toDomainFact() } + ZEROFact
        }

        if (fact !is TaintNode) {
            return@FlowFunctionInstance emptyList()
        }

        if (fact.activation == callStatement) {
            return@FlowFunctionInstance listOf(fact.activatedCopy)
        }

        if (config != null) {
            val conditionEvaluator = FactAwareConditionEvaluator(
                Tainted(fact),
                CallPositionToJIRValueResolver(callStatement)
            )
            val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
            val facts = mutableSetOf<Tainted>()
            var defaultBehavior = true

            for (item in config.filterIsInstance<TaintPassThrough>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    defaultBehavior = false
                    for (action in item.actionsAfter) {
                        when (action) {
                            is CopyMark -> {
                                facts += actionEvaluator.evaluate(action, Tainted(fact))
                            }

                            is CopyAllMarks -> {
                                facts += actionEvaluator.evaluate(action, Tainted(fact))
                            }

                            is RemoveMark -> {
                                facts += actionEvaluator.evaluate(action, Tainted(fact))
                            }

                            is RemoveAllMarks -> {
                                facts += actionEvaluator.evaluate(action, Tainted(fact))
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }
            for (item in config.filterIsInstance<TaintCleaner>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    defaultBehavior = false
                    for (action in item.actionsAfter) {
                        when (action) {
                            is RemoveMark -> {
                                facts += actionEvaluator.evaluate(action, Tainted(fact))
                            }

                            is RemoveAllMarks -> {
                                facts += actionEvaluator.evaluate(action, Tainted(fact))
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }

            if (!defaultBehavior) {
                if (facts.size > 0) {
                    logger.trace { "Got ${facts.size} facts from config for $callee: $facts" }
                }
                return@FlowFunctionInstance facts.map { it.toDomainFact() }
            } else {
                // Fall back to the default behavior, as if there were no config at all.
            }
        }

        // Default behavior for "analyzable" method calls is to remove ("temporarily")
        //  all the marks from the 'instance' and arguments, in order to allow them "pass through"
        //  the callee (when it is going to be analyzed), i.e. through "call-to-start" and
        //  "exit-to-return" flow functions.
        // When we know that we are NOT going to analyze the callee, we do NOT need
        //  to remove any marks from 'instance' and arguments.
        // Currently, "analyzability" of the callee depends on the fact that the callee
        //  is "accessible" through the JIRApplicationGraph::callees().
        if (callee in graph.callees(callStatement)) {

            if (fact.variable.isStatic) {
                return@FlowFunctionInstance emptyList()
            }

            for (actual in callExpr.args) {
                // Possibly tainted actual parameter:
                if (fact.variable.startsWith(actual.toPathOrNull())) {
                    return@FlowFunctionInstance emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                // Possibly tainted instance:
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunctionInstance emptyList() // Will be handled by summary edge
                }
            }

        }

        if (callStatement is JIRAssignInst) {
            // Possibly tainted lhv:
            if (fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunctionInstance emptyList() // Overridden by rhv
            }
        }

        // TODO: do we even need to call this here???
        transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
    }

    final override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst,
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = exitStatement.location.method
        // TODO: maybe we can always use fact instead of updatedFact here
        val updatedFact = if (fact is TaintNode && fact.activation?.location?.method == callee) {
            fact.updateActivation(callStatement)
        } else {
            fact
        }
        val formalParams = cp.getArgumentsOf(callee)

        buildList {
            if (fact is TaintNode && fact.variable.isOnHeap) {
                // If there is some method A::f(formal: T) that is called like a.f(actual) then
                //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                //  2. We shouldn't propagate from formal to actual (as formal is local)
                //  Second case is why we need check for isOnHeap
                // TODO: add test for handling of 2nd case
                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    addAll(transmitDataFlow(formal, actual, exitStatement, updatedFact, dropFact = true))
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                addAll(
                    transmitDataFlow(
                        callee.thisInstance,
                        callExpr.instance,
                        exitStatement,
                        updatedFact,
                        dropFact = true
                    )
                )
            }

            if (callStatement is JIRAssignInst && exitStatement is JIRReturnInst) {
                exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                    addAll(transmitDataFlow(it, callStatement.lhv, exitStatement, updatedFact, dropFact = true))
                }
            }

            if (fact is TaintNode && fact.variable.isStatic) {
                add(fact)
            }
        }
    }
}
