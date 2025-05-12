@file:Suppress("LiftReturnOrAssignment")

package org.opentaint.ir.analysis.ifds2.taint.npe

import mu.KotlinLogging
import org.opentaint.ir.analysis.config.BasicConditionEvaluator
import org.opentaint.ir.analysis.config.CallPositionToAccessPathResolver
import org.opentaint.ir.analysis.config.CallPositionToJIRValueResolver
import org.opentaint.ir.analysis.config.FactAwareConditionEvaluator
import org.opentaint.ir.analysis.config.TaintActionEvaluator
import org.opentaint.ir.analysis.ifds2.FlowFunction
import org.opentaint.ir.analysis.ifds2.FlowFunctions
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.Zero
import org.opentaint.ir.analysis.library.analyzers.getArgument
import org.opentaint.ir.analysis.library.analyzers.getArgumentsOf
import org.opentaint.ir.analysis.library.analyzers.thisInstance
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.ElementAccessor
import org.opentaint.ir.analysis.paths.isDereferencedAt
import org.opentaint.ir.analysis.paths.minus
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIREqExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRNeqExpr
import org.opentaint.ir.api.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.cfg.JIRNullConstant
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRThis
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.api.ext.isNullable
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.ir.taint.configuration.This

private val logger = KotlinLogging.logger {}

@Suppress("PublicApiImplicitType")
class ForwardNpeFlowFunctions(
    private val cp: JIRClasspath,
    private val graph: JIRApplicationGraph,
) : FlowFunctions<TaintFact> {

    internal val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    override fun obtainPossibleStartFacts(
        method: JIRMethod,
    ): Collection<TaintFact> = buildSet {
        addAll(obtainPossibleStartFactsBasic(method))

        // Possibly null arguments:
        for (p in method.parameters.filter { it.isNullable != false }) {
            val t = cp.findTypeOrNull(p.type)!!
            val arg = JIRArgument.of(p.index, p.name, t)
            val path = AccessPath.from(arg)
            add(Tainted(path, TaintMark.NULLNESS))
        }
    }

    private fun obtainPossibleStartFactsBasic(
        method: JIRMethod,
    ): Collection<TaintFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(Zero)

        // Possibly null arguments:
        // for (p in method.parameters.filter { it.isNullable != false }) {
        //     val t = cp.findTypeOrNull(p.type)!!
        //     val arg = JIRArgument.of(p.index, p.name, t)
        //     val path = AccessPath.from(arg)
        //     add(Tainted(path, TaintMark.NULLNESS))
        // }

        // Extract initial facts from the config:
        val config = taintConfigurationFeature?.let { feature ->
            logger.trace { "Extracting config for $method" }
            feature.getConfigForMethod(method)
        }
        if (config != null) {
            // Note: both condition and action evaluator require a custom position resolver.
            val conditionEvaluator = BasicConditionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance

                    is Argument -> run {
                        val p = method.parameters[position.index]
                        cp.getArgument(p)
                    } ?: error("Cannot resolve $position for $method")

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }
            val actionEvaluator = TaintActionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance.toPathOrNull()
                        ?: error("Cannot resolve $position for $method")

                    is Argument -> run {
                        val p = method.parameters[position.index]
                        cp.getArgument(p)?.toPathOrNull()
                    } ?: error("Cannot resolve $position for $method")

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }

            // Handle EntryPointSource config items:
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        when (action) {
                            is AssignMark -> {
                                add(actionEvaluator.evaluate(action))
                            }

                            else -> error("$action is not supported for $item")
                        }
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

        if (fact.mark == TaintMark.NULLNESS) {
            // if (from is JIRNewExpr ||
            //     from is JIRNewArrayExpr ||
            //     from is JIRConstant ||
            //     (from is JIRCallExpr && from.method.method.isNullable != true)
            // ) {
            if (fact.variable.startsWith(toPath)) {
                // NULLNESS is overridden:
                return emptySet()
            }
        }

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
        inst: JIRInst,
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    private fun generates(inst: JIRInst): Collection<TaintFact> = buildList {
        if (inst is JIRAssignInst) {
            val toPath = inst.lhv.toPath()
            val from = inst.rhv
            if (from is JIRNullConstant || (from is JIRCallExpr && from.method.method.isNullable == true)) {
                add(Tainted(toPath, TaintMark.NULLNESS))
            } else if (from is JIRNewArrayExpr && (from.type as JIRArrayType).elementType.nullable != false) {
                val accessors = List((from.type as JIRArrayType).dimensions) { ElementAccessor(null) }
                val path = toPath / accessors
                add(Tainted(path, TaintMark.NULLNESS))
            }
        }
    }

    private val JIRIfInst.pathComparedWithNull: AccessPath?
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
        current: JIRInst,
        next: JIRInst,
    ) = FlowFunction<TaintFact> { fact ->
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(current)) {
                return@FlowFunction emptySet()
            }
        }

        if (current is JIRIfInst) {
            val nextIsTrueBranch = next.location.index == current.trueBranch.index
            val pathComparedWithNull = current.pathComparedWithNull
            if (fact == Zero) {
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
                    return@FlowFunction listOf(Zero)
                } else {
                    return@FlowFunction emptyList()
                }
            }
        }

        if (fact is Zero) {
            return@FlowFunction listOf(Zero) + generates(current)
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
        at: JIRInst,
        from: JIRValue,
        to: JIRValue,
    ): Collection<Tainted> = buildSet {
        if (fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(at)) {
                return@buildSet
            }
        }

        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        at: JIRInst,
        from: JIRValue, // actual
        to: JIRValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        at: JIRInst,
        from: JIRValue, // formal
        to: JIRValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        at: JIRInst,
        from: JIRValue, // instance
        to: JIRThis, // this
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        at: JIRInst,
        from: JIRThis, // this
        to: JIRValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        at: JIRInst,
        from: JIRValue,
        to: JIRValue,
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst, // FIXME: unused?
    ) = FlowFunction<TaintFact> { fact ->
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
            logger.trace { "Extracting config for $callee" }
            feature.getConfigForMethod(callee)
        }

        if (fact == Zero) {
            return@FlowFunction buildSet {
                add(Zero)

                if (callStatement is JIRAssignInst) {
                    val toPath = callStatement.lhv.toPath()
                    val from = callStatement.rhv
                    if (from is JIRNullConstant || (from is JIRCallExpr && from.method.method.isNullable == true)) {
                        add(Tainted(toPath, TaintMark.NULLNESS))
                    } else if (from is JIRNewArrayExpr && (from.type as JIRArrayType).elementType.nullable != false) {
                        val size = (from.type as JIRArrayType).dimensions
                        val accessors = List(size) { ElementAccessor }
                        val path = toPath / accessors
                        add(Tainted(path, TaintMark.NULLNESS))
                    }
                }

                if (config != null) {
                    val conditionEvaluator = BasicConditionEvaluator(CallPositionToJIRValueResolver(callStatement))
                    val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))

                    // Handle MethodSource config items:
                    for (item in config.filterIsInstance<TaintMethodSource>()) {
                        if (item.condition.accept(conditionEvaluator)) {
                            for (action in item.actionsAfter) {
                                when (action) {
                                    is AssignMark -> {
                                        add(actionEvaluator.evaluate(action))
                                    }

                                    else -> error("$action is not supported for $item")
                                }
                            }
                        }
                    }
                }
            }
        }
        check(fact is Tainted)

        // TODO: handle 'activation' (c.f. Boomerang) here

        // if (config == null) {
        //     return@FlowFunction emptyList()
        // }

        if (config != null) {
            // FIXME: adhoc
            if (callee.enclosingClass.name == "java.lang.StringBuilder" && callee.name == "append") {
                // Skip rules for StringBuilder::append in NPE analysis.
            } else {
                val facts = mutableSetOf<Tainted>()
                val conditionEvaluator = FactAwareConditionEvaluator(fact, CallPositionToJIRValueResolver(callStatement))
                val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
                var defaultBehavior = true

                // Handle PassThrough config items:
                for (item in config.filterIsInstance<TaintPassThrough>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        defaultBehavior = false
                        for (action in item.actionsAfter) {
                            when (action) {
                                is CopyMark -> {
                                    facts += actionEvaluator.evaluate(action, fact)
                                }

                                is CopyAllMarks -> {
                                    facts += actionEvaluator.evaluate(action, fact)
                                }

                                is RemoveMark -> {
                                    facts += actionEvaluator.evaluate(action, fact)
                                }

                                is RemoveAllMarks -> {
                                    facts += actionEvaluator.evaluate(action, fact)
                                }

                                else -> error("$action is not supported for $item")
                            }
                        }
                    }
                }

                // Handle Cleaner config items:
                for (item in config.filterIsInstance<TaintCleaner>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        defaultBehavior = false
                        for (action in item.actionsAfter) {
                            when (action) {
                                is RemoveMark -> {
                                    facts += actionEvaluator.evaluate(action, fact)
                                }

                                is RemoveAllMarks -> {
                                    facts += actionEvaluator.evaluate(action, fact)
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
        callStatement: JIRInst,
        calleeStart: JIRInst,
    ) = FlowFunction<TaintFact> { fact ->
        val callee = calleeStart.location.method

        if (fact == Zero) {
            // return@FlowFunction obtainPossibleStartFacts(callee)
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
                        fact,
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
        callStatement: JIRInst,
        returnSite: JIRInst, // unused
        exitStatement: JIRInst,
    ) = FlowFunction<TaintFact> { fact ->
        // TODO: do we even need to return non-empty list for zero fact here?
        if (fact == Zero) {
            // return@FlowFunction listOf(Zero)
            return@FlowFunction buildSet {
                add(Zero)
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

// @Suppress("PublicApiImplicitType")
// class BackwardNpeFlowFunctions(
//     private val project: JIRClasspath,
//     private val graph: JIRApplicationGraph,
// ) : FlowFunctions<TaintFact> {
//
//     override fun obtainPossibleStartFacts(
//         method: JIRMethod,
//     ): Collection<TaintFact> {
//         return listOf(Zero)
//     }
//
//     private fun transmitTaintBackwardAssign(
//         fact: Tainted,
//         from: JIRValue,
//         to: JIRExpr,
//     ): Collection<TaintFact> {
//         val fromPath = from.toPath()
//         val toPath = to.toPathOrNull()
//
//         if (toPath != null) {
//             // TODO: think about arrays here
//             // // Adhoc taint array:
//             // if (fromPath.accesses.isNotEmpty()
//             //     && fromPath.accesses.last() is ElementAccessor
//             //     && fromPath.copy(accesses = fromPath.accesses.dropLast(1)) == fact.variable
//             // ) {
//             //     val newTaint = fact.copy(variable = toPath)
//             //     return setOf(fact, newTaint)
//             // }
//
//             val tail = fact.variable - fromPath
//             if (tail != null) {
//                 // Both 'from' and 'to' are tainted now:
//                 val newPath = toPath / tail
//                 val newTaint = fact.copy(variable = newPath)
//                 return setOf(fact, newTaint)
//             }
//
//             if (fact.variable.startsWith(toPath)) {
//                 // 'to' was (sub-)tainted, but it is now overridden by 'from':
//                 return emptySet()
//             }
//         }
//
//         // Pass-through:
//         return setOf(fact)
//     }
//
//     private fun transmitTaintBackwardNormal(
//         fact: Tainted,
//         inst: JIRInst,
//     ): List<TaintFact> {
//         // Pass-through:
//         return listOf(fact)
//     }
//
//     override fun obtainSequentFlowFunction(
//         current: JIRInst,
//         next: JIRInst,
//     ) = FlowFunction<TaintFact> { fact ->
//         if (fact is Zero) {
//             return@FlowFunction listOf(Zero)
//         }
//         check(fact is Tainted)
//
//         if (current is JIRAssignInst) {
//             transmitTaintBackwardAssign(fact, from = current.lhv, to = current.rhv)
//         } else {
//             transmitTaintBackwardNormal(fact, current)
//         }
//     }
//
//     private fun transmitTaint(
//         fact: Tainted,
//         from: JIRValue,
//         to: JIRValue,
//     ): Collection<Tainted> = buildSet {
//         val fromPath = from.toPath()
//         val toPath = to.toPath()
//
//         val tail = (fact.variable - fromPath) ?: return@buildSet
//         val newPath = toPath / tail
//         val newTaint = fact.copy(variable = newPath)
//         add(newTaint)
//     }
//
//     private fun transmitTaintArgumentActualToFormal(
//         fact: Tainted,
//         from: JIRValue, // actual
//         to: JIRValue, // formal
//     ): Collection<Tainted> = transmitTaint(fact, from, to)
//
//     private fun transmitTaintArgumentFormalToActual(
//         fact: Tainted,
//         from: JIRValue, // formal
//         to: JIRValue, // actual
//     ): Collection<Tainted> = transmitTaint(fact, from, to)
//
//     private fun transmitTaintInstanceToThis(
//         fact: Tainted,
//         from: JIRValue, // instance
//         to: JIRThis, // this
//     ): Collection<Tainted> = transmitTaint(fact, from, to)
//
//     private fun transmitTaintThisToInstance(
//         fact: Tainted,
//         from: JIRThis, // this
//         to: JIRValue, // instance
//     ): Collection<Tainted> = transmitTaint(fact, from, to)
//
//     private fun transmitTaintReturn(
//         fact: Tainted,
//         from: JIRValue,
//         to: JIRValue,
//     ): Collection<Tainted> = transmitTaint(fact, from, to)
//
//     override fun obtainCallToReturnSiteFlowFunction(
//         callStatement: JIRInst,
//         returnSite: JIRInst, // FIXME: unused?
//     ) = FlowFunction<TaintFact> { fact ->
//         // TODO: pass-through on invokedynamic-based String concatenation
//
//         if (fact == Zero) {
//             return@FlowFunction listOf(Zero)
//         }
//         check(fact is Tainted)
//
//         val callExpr = callStatement.callExpr
//             ?: error("Call statement should have non-null callExpr")
//         val callee = callExpr.method.method
//
//         // // FIXME: adhoc for constructors:
//         // if (callee.isConstructor) {
//         //     return@FlowFunction listOf(fact)
//         // }
//
//         if (callee in graph.callees(callStatement)) {
//
//             if (fact.variable.isStatic) {
//                 return@FlowFunction emptyList()
//             }
//
//             for (actual in callExpr.args) {
//                 // Possibly tainted actual parameter:
//                 if (fact.variable.startsWith(actual.toPathOrNull())) {
//                     return@FlowFunction emptyList() // Will be handled by summary edge
//                 }
//             }
//
//             if (callExpr is JIRInstanceCallExpr) {
//                 // Possibly tainted instance:
//                 if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
//                     return@FlowFunction emptyList() // Will be handled by summary edge
//                 }
//             }
//
//         }
//
//         if (callStatement is JIRAssignInst) {
//             // Possibly tainted rhv:
//             if (fact.variable.startsWith(callStatement.rhv.toPathOrNull())) {
//                 return@FlowFunction emptyList() // Overridden by lhv
//             }
//         }
//
//         // The "most default" behaviour is encapsulated here:
//         transmitTaintBackwardNormal(fact, callStatement)
//     }
//
//     override fun obtainCallToStartFlowFunction(
//         callStatement: JIRInst,
//         calleeStart: JIRInst,
//     ) = FlowFunction<TaintFact> { fact ->
//         val callee = calleeStart.location.method
//
//         if (fact == Zero) {
//             return@FlowFunction obtainPossibleStartFacts(callee)
//         }
//         check(fact is Tainted)
//
//         val callExpr = callStatement.callExpr
//             ?: error("Call statement should have non-null callExpr")
//
//         buildSet {
//             // Transmit facts on arguments (from 'actual' to 'formal'):
//             val actualParams = callExpr.args
//             val formalParams = project.getArgumentsOf(callee)
//             for ((formal, actual) in formalParams.zip(actualParams)) {
//                 addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
//             }
//
//             // Transmit facts on instance (from 'instance' to 'this'):
//             if (callExpr is JIRInstanceCallExpr) {
//                 addAll(transmitTaintInstanceToThis(fact, from = callExpr.instance, to = callee.thisInstance))
//             }
//
//             // Transmit facts on static values:
//             if (fact.variable.isStatic) {
//                 add(fact)
//             }
//
//             // Transmit facts on return value (from 'returnValue' to 'lhv'):
//             if (calleeStart is JIRReturnInst && callStatement is JIRAssignInst) {
//                 // Note: returnValue can be null here in some weird cases, e.g. in lambda.
//                 calleeStart.returnValue?.let { returnValue ->
//                     addAll(transmitTaintReturn(fact, from = callStatement.lhv, to = returnValue))
//                 }
//             }
//         }
//     }
//
//     override fun obtainExitToReturnSiteFlowFunction(
//         callStatement: JIRInst,
//         returnSite: JIRInst,
//         exitStatement: JIRInst,
//     ) = FlowFunction<TaintFact> { fact ->
//         if (fact == Zero) {
//             return@FlowFunction listOf(Zero)
//         }
//         check(fact is Tainted)
//
//         val callExpr = callStatement.callExpr
//             ?: error("Call statement should have non-null callExpr")
//         val callee = exitStatement.location.method
//
//         buildSet {
//             // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
//             // TODO: "if passed by-ref" part is not implemented here yet
//             val actualParams = callExpr.args
//             val formalParams = project.getArgumentsOf(callee)
//             for ((formal, actual) in formalParams.zip(actualParams)) {
//                 addAll(transmitTaintArgumentFormalToActual(fact, from = formal, to = actual))
//             }
//
//             // Transmit facts on instance (from 'this' to 'instance'):
//             if (callExpr is JIRInstanceCallExpr) {
//                 addAll(transmitTaintThisToInstance(fact, from = callee.thisInstance, to = callExpr.instance))
//             }
//
//             // Transmit facts on static values:
//             if (fact.variable.isStatic) {
//                 add(fact)
//             }
//         }
//     }
// }
