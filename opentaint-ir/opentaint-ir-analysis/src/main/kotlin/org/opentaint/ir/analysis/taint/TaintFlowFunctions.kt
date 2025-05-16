package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.config.BasicConditionEvaluator
import org.opentaint.ir.analysis.config.CallPositionToAccessPathResolver
import org.opentaint.ir.analysis.config.CallPositionToJIRValueResolver
import org.opentaint.ir.analysis.config.EntryPointPositionToAccessPathResolver
import org.opentaint.ir.analysis.config.EntryPointPositionToJIRValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.config.TaintActionEvaluator
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.FlowFunction
import org.opentaint.ir.analysis.ifds.FlowFunctions
import org.opentaint.ir.analysis.ifds.onSome
import org.opentaint.ir.analysis.ifds.toPath
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.analysis.util.getArgumentsOf
import org.opentaint.ir.analysis.util.startsWith
import org.opentaint.ir.analysis.util.thisInstance
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRThis
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough

private val logger = mu.KotlinLogging.logger {}

class ForwardTaintFlowFunctions(
    private val cp: JIRClasspath,
    private val graph: JIRApplicationGraph,
) : FlowFunctions<TaintDomainFact> {

    internal val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    override fun obtainPossibleStartFacts(
        method: JIRMethod,
    ): Collection<TaintDomainFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(TaintZeroFact)

        // Extract initial facts from the config:
        val config = taintConfigurationFeature?.getConfigForMethod(method)
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator(EntryPointPositionToJIRValueResolver(cp, method))
            val actionEvaluator = TaintActionEvaluator(EntryPointPositionToAccessPathResolver(cp, method))

            // Handle EntryPointSource config items:
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        val result = when (action) {
                            is AssignMark -> actionEvaluator.evaluate(action)
                            else -> error("$action is not supported for $item")
                        }
                        result.onSome { addAll(it) }
                    }
                }
            }
        }
    }

    private fun transmitTaintAssign(
        fact: Tainted,
        from: JIRExpr,
        to: JIRValue,
    ): Collection<Tainted> {
        val toPath = to.toPath()
        val fromPath = from.toPathOrNull()

        if (fromPath != null) {
            // Adhoc taint array:
            if (fromPath.accesses.isNotEmpty()
                && fromPath.accesses.last() is ElementAccessor
                && fromPath == (fact.variable / ElementAccessor)
            ) {
                val newTaint = fact.copy(variable = toPath)
                return setOf(fact, newTaint)
            }

            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath / tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }
        }

        if (fact.variable.startsWith(toPath)) {
            // 'to' was (sub-)tainted, but it is now overridden by 'from':
            return emptySet()
        } else {
            // Neither 'from' nor 'to' are tainted:
            return setOf(fact)
        }
    }

    private fun transmitTaintNormal(
        fact: Tainted,
        inst: JIRInst,
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: JIRInst,
        next: JIRInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        if (current is JIRAssignInst) {
            transmitTaintAssign(fact, from = current.rhv, to = current.lhv)
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        from: JIRValue,
        to: JIRValue,
    ): Collection<Tainted> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: JIRValue, // actual
        to: JIRValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: JIRValue, // formal
        to: JIRValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: JIRValue, // instance
        to: JIRThis, // this
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: JIRThis, // this
        to: JIRValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        from: JIRValue,
        to: JIRValue,
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst, // FIXME: unused?
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.method.method

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        if (fact is Tainted
            && callExpr is JIRDynamicCallExpr
            && callee.enclosingClass.name == "java.lang.invoke.StringConcatFactory"
            && callStatement is JIRAssignInst
        ) {
            for (arg in callExpr.args) {
                if (arg.toPath() == fact.variable) {
                    return@FlowFunction setOf(
                        fact,
                        fact.copy(variable = callStatement.lhv.toPath())
                    )
                }
            }
            return@FlowFunction setOf(fact)
        }

        val config = taintConfigurationFeature?.getConfigForMethod(callee)

        if (fact == TaintZeroFact) {
            return@FlowFunction buildSet {
                add(TaintZeroFact)

                if (config != null) {
                    val conditionEvaluator = BasicConditionEvaluator(CallPositionToJIRValueResolver(callStatement))
                    val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))

                    // Handle MethodSource config items:
                    for (item in config.filterIsInstance<TaintMethodSource>()) {
                        if (item.condition.accept(conditionEvaluator)) {
                            for (action in item.actionsAfter) {
                                val result = when (action) {
                                    is AssignMark -> actionEvaluator.evaluate(action)
                                    else -> error("$action is not supported for $item")
                                }
                                result.onSome { addAll(it) }
                            }
                        }
                    }
                }
            }
        }
        check(fact is Tainted)

        if (config != null) {
            val facts = mutableSetOf<Tainted>()
            val conditionEvaluator = FactAwareConditionEvaluator(fact, CallPositionToJIRValueResolver(callStatement))
            val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
            var defaultBehavior = true

            // Handle PassThrough config items:
            for (item in config.filterIsInstance<TaintPassThrough>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        val result = when (action) {
                            is CopyMark -> actionEvaluator.evaluate(action, fact)
                            is CopyAllMarks -> actionEvaluator.evaluate(action, fact)
                            is RemoveMark -> actionEvaluator.evaluate(action, fact)
                            is RemoveAllMarks -> actionEvaluator.evaluate(action, fact)
                            else -> error("$action is not supported for $item")
                        }
                        result.onSome {
                            facts += it
                            defaultBehavior = false
                        }
                    }
                }
            }

            // Handle Cleaner config items:
            for (item in config.filterIsInstance<TaintCleaner>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        val result = when (action) {
                            is RemoveMark -> actionEvaluator.evaluate(action, fact)
                            is RemoveAllMarks -> actionEvaluator.evaluate(action, fact)
                            else -> error("$action is not supported for $item")
                        }
                        result.onSome {
                            facts += it
                            defaultBehavior = false
                        }
                    }
                }
            }

            if (!defaultBehavior) {
                if (facts.size > 0) {
                    logger.trace { "Got ${facts.size} facts from config for $callee: $facts" }
                }
                return@FlowFunction facts
            } else {
                // Fall back to the default behavior, as if there were no config at all.
            }
        }

        // FIXME: adhoc for constructors:
        if (callee.isConstructor) {
            return@FlowFunction listOf(fact)
        }

        // TODO: CONSIDER REFACTORING THIS
        //   Default behavior for "analyzable" method calls is to remove ("temporarily")
        //    all the marks from the 'instance' and arguments, in order to allow them "pass through"
        //    the callee (when it is going to be analyzed), i.e. through "call-to-start" and
        //    "exit-to-return" flow functions.
        //   When we know that we are NOT going to analyze the callee, we do NOT need
        //    to remove any marks from 'instance' and arguments.
        //   Currently, "analyzability" of the callee depends on the fact that the callee
        //    is "accessible" through the JIRApplicationGraph::callees().
        if (callee in graph.callees(callStatement)) {

            if (fact.variable.isStatic) {
                return@FlowFunction emptyList()
            }

            for (actual in callExpr.args) {
                // Possibly tainted actual parameter:
                if (fact.variable.startsWith(actual.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                // Possibly tainted instance:
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

        }

        if (callStatement is JIRAssignInst) {
            // Possibly tainted lhv:
            if (fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunction emptyList() // Overridden by rhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        transmitTaintNormal(fact, callStatement)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        calleeStart: JIRInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callee = calleeStart.location.method

        if (fact == TaintZeroFact) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = cp.getArgumentsOf(callee)
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is JIRInstanceCallExpr) {
                addAll(transmitTaintInstanceToThis(fact, from = callExpr.instance, to = callee.thisInstance))
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst, // unused
        exitStatement: JIRInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact == TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = exitStatement.location.method

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            if (fact.variable.isOnHeap) {
                val actualParams = callExpr.args
                val formalParams = cp.getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(transmitTaintArgumentFormalToActual(fact, from = formal, to = actual))
                }
            }

            // Transmit facts on instance (from 'this' to 'instance'):
            if (callExpr is JIRInstanceCallExpr) {
                addAll(transmitTaintThisToInstance(fact, from = callee.thisInstance, to = callExpr.instance))
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (exitStatement is JIRReturnInst && callStatement is JIRAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(transmitTaintReturn(fact, from = returnValue, to = callStatement.lhv))
                }
            }
        }
    }
}

class BackwardTaintFlowFunctions(
    private val project: JIRClasspath,
    private val graph: JIRApplicationGraph,
) : FlowFunctions<TaintDomainFact> {

    override fun obtainPossibleStartFacts(
        method: JIRMethod,
    ): Collection<TaintDomainFact> {
        return listOf(TaintZeroFact)
    }

    private fun transmitTaintBackwardAssign(
        fact: Tainted,
        from: JIRValue,
        to: JIRExpr,
    ): Collection<TaintDomainFact> {
        val fromPath = from.toPath()
        val toPath = to.toPathOrNull()

        if (toPath != null) {
            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath / tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }

            if (fact.variable.startsWith(toPath)) {
                // 'to' was (sub-)tainted, but it is now overridden by 'from':
                return emptySet()
            }
        }

        // Pass-through:
        return setOf(fact)
    }

    private fun transmitTaintBackwardNormal(
        fact: Tainted,
        inst: JIRInst,
    ): List<TaintDomainFact> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: JIRInst,
        next: JIRInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        if (current is JIRAssignInst) {
            transmitTaintBackwardAssign(fact, from = current.lhv, to = current.rhv)
        } else {
            transmitTaintBackwardNormal(fact, current)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        from: JIRValue,
        to: JIRValue,
    ): Collection<Tainted> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: JIRValue, // actual
        to: JIRValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: JIRValue, // formal
        to: JIRValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: JIRValue, // instance
        to: JIRThis, // this
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: JIRThis, // this
        to: JIRValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        from: JIRValue,
        to: JIRValue,
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst, // FIXME: unused?
    ) = FlowFunction<TaintDomainFact> { fact ->
        // TODO: pass-through on invokedynamic-based String concatenation

        if (fact == TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.method.method

        if (callee in graph.callees(callStatement)) {

            if (fact.variable.isStatic) {
                return@FlowFunction emptyList()
            }

            for (actual in callExpr.args) {
                // Possibly tainted actual parameter:
                if (fact.variable.startsWith(actual.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                // Possibly tainted instance:
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

        }

        if (callStatement is JIRAssignInst) {
            // Possibly tainted rhv:
            if (fact.variable.startsWith(callStatement.rhv.toPathOrNull())) {
                return@FlowFunction emptyList() // Overridden by lhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        transmitTaintBackwardNormal(fact, callStatement)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        calleeStart: JIRInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callee = calleeStart.location.method

        if (fact == TaintZeroFact) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = project.getArgumentsOf(callee)
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is JIRInstanceCallExpr) {
                addAll(transmitTaintInstanceToThis(fact, from = callExpr.instance, to = callee.thisInstance))
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (calleeStart is JIRReturnInst && callStatement is JIRAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                calleeStart.returnValue?.let { returnValue ->
                    addAll(
                        transmitTaintReturn(
                            fact = fact,
                            from = callStatement.lhv,
                            to = returnValue
                        )
                    )
                }
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact == TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = exitStatement.location.method

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            if (fact.variable.isOnHeap) {
                val actualParams = callExpr.args
                val formalParams = project.getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(
                        transmitTaintArgumentFormalToActual(
                            fact = fact,
                            from = formal,
                            to = actual
                        )
                    )
                }
            }

            // Transmit facts on instance (from 'this' to 'instance'):
            if (callExpr is JIRInstanceCallExpr) {
                addAll(
                    transmitTaintThisToInstance(
                        fact = fact,
                        from = callee.thisInstance,
                        to = callExpr.instance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }
        }
    }
}
