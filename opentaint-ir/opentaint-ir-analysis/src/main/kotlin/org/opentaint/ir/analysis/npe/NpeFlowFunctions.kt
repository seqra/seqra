package org.opentaint.ir.analysis.npe

import org.opentaint.ir.analysis.config.BasicConditionEvaluator
import org.opentaint.ir.analysis.config.CallPositionToAccessPathResolver
import org.opentaint.ir.analysis.config.CallPositionToValueResolver
import org.opentaint.ir.analysis.config.EntryPointPositionToAccessPathResolver
import org.opentaint.ir.analysis.config.EntryPointPositionToValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.config.TaintActionEvaluator
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.FlowFunction
import org.opentaint.ir.analysis.ifds.FlowFunctions
import org.opentaint.ir.analysis.ifds.JIRAccessPath
import org.opentaint.ir.analysis.ifds.isOnHeap
import org.opentaint.ir.analysis.ifds.isStatic
import org.opentaint.ir.analysis.ifds.minus
import org.opentaint.ir.analysis.ifds.onSome
import org.opentaint.ir.analysis.ifds.toPath
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.analysis.taint.TaintDomainFact
import org.opentaint.ir.analysis.taint.TaintZeroFact
import org.opentaint.ir.analysis.taint.Tainted
import org.opentaint.ir.analysis.util.getArgumentsOf
import org.opentaint.ir.analysis.util.isConstructor
import org.opentaint.ir.analysis.util.startsWith
import org.opentaint.ir.analysis.util.thisInstance
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.Project
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.common.ext.callExpr
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIREqExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRNeqExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.ext.isNullable
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough

private val logger = mu.KotlinLogging.logger {}

class ForwardNpeFlowFunctions<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : FlowFunctions<TaintDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    private val cp: Project
        get() = graph.project

    internal val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        val cp = cp
        if (cp is JIRClasspath) {
            cp.features
                ?.singleOrNull { it is TaintConfigurationFeature }
                ?.let { it as TaintConfigurationFeature }
        } else {
            null
        }
    }

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<TaintDomainFact> = buildSet {
        addAll(obtainPossibleStartFactsBasic(method))

        // TODO: use common
        if (method !is JIRMethod) return@buildSet

        // Possibly null arguments:
        for (p in method.parameters.filter { it.isNullable != false }) {
            val t = cp.findTypeOrNull(p.type.typeName)!!
            val arg = JIRArgument.of(p.index, p.name, t as JIRType)
            val path = arg.toPath()
            add(Tainted(path, TaintMark.NULLNESS))
        }
    }

    private fun obtainPossibleStartFactsBasic(
        method: Method,
    ): Collection<TaintDomainFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(TaintZeroFact)

        // Extract initial facts from the config:
        val config = taintConfigurationFeature?.let { feature ->
            if (method is JIRMethod) {
                logger.trace { "Extracting config for $method" }
                feature.getConfigForMethod(method)
            } else {
                error("Cannot extract config for $method")
            }
        }
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator(EntryPointPositionToValueResolver(cp, method))
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
        to: CommonValue,
    ): Collection<Tainted> {
        val toPath = to.toPath()
        val fromPath = from.toPathOrNull()

        if (fact.mark == TaintMark.NULLNESS) {
            // TODO: consider
            //  if (from is JIRNewExpr
            //      || from is JIRNewArrayExpr
            //      || from is JIRConstant
            //      || (from is JIRCallExpr && from.method.method.isNullable != true))
            if (fact.variable.startsWith(toPath)) {
                // NULLNESS is overridden:
                return emptySet()
            }
        }

        if (fromPath != null) {
            // Adhoc taint array:
            if (fromPath.accesses.isNotEmpty()
                && fromPath.accesses.last() is ElementAccessor
                && fromPath == (fact.variable + ElementAccessor)
            ) {
                val newTaint = fact.copy(variable = toPath)
                return setOf(fact, newTaint)
            }

            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath + tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }
        }

        return buildSet {
            if (from is JIRNullConstant) {
                add(Tainted(toPath, TaintMark.NULLNESS))
            }

            if (fact.variable.startsWith(toPath)) {
                // 'to' was (sub-)tainted, but it is now overridden by 'from':
                return@buildSet
            } else {
                // Neither 'from' nor 'to' are tainted:
                add(fact)
            }
        }
    }

    private fun transmitTaintNormal(
        fact: Tainted,
        inst: Statement,
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    private fun generates(
        inst: Statement,
    ): Collection<TaintDomainFact> = buildList {
        if (inst is CommonAssignInst<*, *>) {
            val toPath = inst.lhv.toPath()
            val from = inst.rhv
            if (from is JIRNullConstant || (from is JIRCallExpr && from.method.method.isNullable == true)) {
                add(Tainted(toPath, TaintMark.NULLNESS))
            } else if (from is JIRNewArrayExpr && (from.type as JIRArrayType).elementType.nullable != false) {
                val accessors = List((from.type as JIRArrayType).dimensions) { ElementAccessor }
                val path = toPath + accessors
                add(Tainted(path, TaintMark.NULLNESS))
            }
        }
    }

    private val JIRIfInst.pathComparedWithNull: JIRAccessPath?
        get() {
            val expr = condition
            return if (expr.rhv is JIRNullConstant) {
                expr.lhv.toPathOrNull()
            } else if (expr.lhv is JIRNullConstant) {
                expr.rhv.toPathOrNull()
            } else {
                null
            }
        }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(current)) {
                return@FlowFunction emptySet()
            }
        }

        if (current is JIRIfInst) {
            val nextIsTrueBranch = next.location.index == current.trueBranch.index
            val pathComparedWithNull = current.pathComparedWithNull
            if (fact == TaintZeroFact) {
                if (pathComparedWithNull != null) {
                    if ((current.condition is JIREqExpr && nextIsTrueBranch) ||
                        (current.condition is JIRNeqExpr && !nextIsTrueBranch)
                    ) {
                        // This is a hack: instructions like `return null` in branch of next will be considered only if
                        //  the fact holds (otherwise we could not get there)
                        // Note the absence of 'Zero' here!
                        return@FlowFunction listOf(Tainted(pathComparedWithNull, TaintMark.NULLNESS))
                    }
                }
            } else if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
                val expr = current.condition
                if (pathComparedWithNull != fact.variable) {
                    return@FlowFunction listOf(fact)
                }
                if ((expr is JIREqExpr && nextIsTrueBranch) || (expr is JIRNeqExpr && !nextIsTrueBranch)) {
                    // comparedPath is null in this branch
                    return@FlowFunction listOf(TaintZeroFact)
                } else {
                    return@FlowFunction emptyList()
                }
            }
        }

        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact) + generates(current)
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
        at: Statement,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = buildSet {
        if (fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(at)) {
                return@buildSet
            }
        }

        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath + tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        at: Statement,
        from: CommonValue, // actual
        to: CommonValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        at: Statement,
        from: CommonValue, // formal
        to: CommonValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        at: Statement,
        from: CommonValue, // instance
        to: CommonThis, // this
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        at: Statement,
        from: CommonThis, // this
        to: CommonValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        at: Statement,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // FIXME: unused?
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(callStatement)) {
                return@FlowFunction emptySet()
            }
        }

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

        val config = taintConfigurationFeature?.let { feature ->
            if (callee is JIRMethod) {
                logger.trace { "Extracting config for $callee" }
                feature.getConfigForMethod(callee)
            } else {
                error("Cannot extract config for $callee")
            }
        }

        if (fact == TaintZeroFact) {
            return@FlowFunction buildSet {
                add(TaintZeroFact)

                if (callStatement is JIRAssignInst) {
                    val toPath = callStatement.lhv.toPath()
                    val from = callStatement.rhv
                    if (from is JIRNullConstant || (from is JIRCallExpr && from.method.method.isNullable == true)) {
                        add(Tainted(toPath, TaintMark.NULLNESS))
                    } else if (from is JIRNewArrayExpr && (from.type as JIRArrayType).elementType.nullable != false) {
                        val size = (from.type as JIRArrayType).dimensions
                        val accessors = List(size) { ElementAccessor }
                        val path = toPath + accessors
                        add(Tainted(path, TaintMark.NULLNESS))
                    }
                }

                if (config != null) {
                    val conditionEvaluator = BasicConditionEvaluator(CallPositionToValueResolver(callStatement))
                    val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))

                    // Handle MethodSource config items:
                    for (item in config.filterIsInstance<TaintMethodSource>()) {
                        if (item.condition.accept(conditionEvaluator)) {
                            for (action in item.actionsAfter) {
                                val result = when (action) {
                                    is AssignMark -> actionEvaluator.evaluate(action)
                                    else -> error("$action is not supported for $item")
                                }
                                result.onSome {
                                    addAll(it)
                                }
                            }
                        }
                    }
                }
            }
        }
        check(fact is Tainted)

        if (config != null) {
            // FIXME: adhoc
            if (callee.enclosingClass.name == "java.lang.StringBuilder" && callee.name == "append") {
                // Skip rules for StringBuilder::append in NPE analysis.
            } else {
                val facts = mutableSetOf<Tainted>()
                val conditionEvaluator = FactAwareConditionEvaluator(fact, CallPositionToValueResolver(callStatement))
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
        callStatement: Statement,
        calleeStart: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callee = calleeStart.location.method

        if (fact == TaintZeroFact) {
            return@FlowFunction obtainPossibleStartFactsBasic(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = cp.getArgumentsOf(callee)
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(
                    transmitTaintArgumentActualToFormal(
                        fact = fact,
                        at = callStatement,
                        from = actual,
                        to = formal
                    )
                )
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is JIRInstanceCallExpr) {
                addAll(
                    transmitTaintInstanceToThis(
                        fact = fact,
                        at = callStatement,
                        from = callExpr.instance,
                        to = callee.thisInstance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // unused
        exitStatement: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        // TODO: do we even need to return non-empty list for zero fact here?
        if (fact == TaintZeroFact) {
            // return@FlowFunction listOf(Zero)
            return@FlowFunction buildSet {
                add(TaintZeroFact)
                if (exitStatement is JIRReturnInst && callStatement is JIRAssignInst) {
                    // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                    exitStatement.returnValue?.let { returnValue ->
                        if (returnValue is JIRNullConstant) {
                            val toPath = callStatement.lhv.toPath()
                            add(Tainted(toPath, TaintMark.NULLNESS))
                        }
                    }
                }
            }
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
                    addAll(
                        transmitTaintArgumentFormalToActual(
                            fact = fact,
                            at = callStatement,
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
                        at = callStatement,
                        from = callee.thisInstance,
                        to = callExpr.instance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (exitStatement is JIRReturnInst && callStatement is JIRAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(
                        transmitTaintReturn(
                            fact = fact,
                            at = callStatement,
                            from = returnValue,
                            to = callStatement.lhv
                        )
                    )
                }
            }
        }
    }
}

// TODO: class BackwardNpeFlowFunctions
