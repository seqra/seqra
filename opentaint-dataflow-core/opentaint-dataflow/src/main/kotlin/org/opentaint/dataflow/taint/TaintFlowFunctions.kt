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

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstanceCallExpr
import org.opentaint.ir.api.common.cfg.CommonReturnInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.config.BasicConditionEvaluator
import org.opentaint.dataflow.config.CallPositionToAccessPathResolver
import org.opentaint.dataflow.config.CallPositionToValueResolver
import org.opentaint.dataflow.config.EntryPointPositionToAccessPathResolver
import org.opentaint.dataflow.config.EntryPointPositionToValueResolver
import org.opentaint.dataflow.config.FactAwareConditionEvaluator
import org.opentaint.dataflow.config.TaintActionEvaluator
import org.opentaint.dataflow.ifds.ElementAccessor
import org.opentaint.dataflow.ifds.FlowFunction
import org.opentaint.dataflow.ifds.FlowFunctions
import org.opentaint.dataflow.ifds.isOnHeap
import org.opentaint.dataflow.ifds.isStatic
import org.opentaint.dataflow.ifds.minus
import org.opentaint.dataflow.ifds.onSome
import org.opentaint.dataflow.util.Traits
import org.opentaint.dataflow.util.startsWith

private val logger = mu.KotlinLogging.logger {}

class ForwardTaintFlowFunctions<Method, Statement>(
    private val traits: Traits<Method, Statement>,
    private val graph: ApplicationGraph<Method, Statement>,
    val getConfigForMethod: (Method) -> List<TaintConfigurationItem>?,
) : FlowFunctions<TaintDomainFact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<TaintDomainFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(TaintZeroFact)

        // Extract initial facts from the config:
        val config = getConfigForMethod(method)
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator(
                traits,
                EntryPointPositionToValueResolver(traits, method)
            )
            val actionEvaluator = TaintActionEvaluator(
                EntryPointPositionToAccessPathResolver(traits, method)
            )

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
        from: CommonExpr,
        to: CommonValue,
    ): Collection<Tainted> = with(traits) {
        val toPath = convertToPath(to)
        val fromPath = convertToPathOrNull(from)

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
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            if (fact is TaintZeroFact) {
                return@FlowFunction listOf(TaintZeroFact)
            }
            check(fact is Tainted)

            if (current is CommonAssignInst) {
                taintFlowRhsValues(current).flatMap { rhv ->
                    transmitTaintAssign(fact, from = rhv, to = current.lhv)
                }
            } else {
                transmitTaintNormal(fact)
            }
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = with(traits) {
        buildSet {
            val fromPath = convertToPath(from)
            val toPath = convertToPath(to)

            val tail = (fact.variable - fromPath) ?: return@buildSet
            val newPath = toPath + tail
            val newTaint = fact.copy(variable = newPath)
            add(newTaint)
        }
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: CommonValue, // actual
        to: CommonValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: CommonValue, // formal
        to: CommonValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: CommonValue, // instance
        to: CommonThis, // this
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: CommonThis, // this
        to: CommonValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // FIXME: unused?
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")

            val callee = getCallee(callExpr)
            val config = getConfigForMethod(callee)

            if (fact == TaintZeroFact) {
                return@FlowFunction buildSet {
                    add(TaintZeroFact)

                    if (config != null) {
                        val conditionEvaluator = BasicConditionEvaluator(
                            traits,
                            CallPositionToValueResolver(traits, callStatement)
                        )
                        val actionEvaluator = TaintActionEvaluator(
                            CallPositionToAccessPathResolver(traits, callStatement)
                        )

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

            val statementPassThrough = taintPassThrough(callStatement)
            if (statementPassThrough != null) {
                for ((from, to) in statementPassThrough) {
                    if (convertToPath(from) == fact.variable) {
                        return@FlowFunction setOf(
                            fact,
                            fact.copy(variable = convertToPath(to))
                        )
                    }
                }

                return@FlowFunction setOf(fact)
            }

            if (config != null) {
                val facts = mutableSetOf<Tainted>()
                val conditionEvaluator = FactAwareConditionEvaluator(
                    traits, fact, CallPositionToValueResolver(traits, callStatement)
                )
                val actionEvaluator = TaintActionEvaluator(
                    CallPositionToAccessPathResolver(traits, callStatement)
                )
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
            if (isConstructor(callee)) {
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
                    if (fact.variable.startsWith(convertToPathOrNull(actual))) {
                        return@FlowFunction emptyList() // Will be handled by summary edge
                    }
                }

                if (callExpr is CommonInstanceCallExpr) {
                    // Possibly tainted instance:
                    if (fact.variable.startsWith(convertToPathOrNull(callExpr.instance))) {
                        return@FlowFunction emptyList() // Will be handled by summary edge
                    }
                }

            }

            if (callStatement is CommonAssignInst) {
                // Possibly tainted lhv:
                if (fact.variable.startsWith(convertToPathOrNull(callStatement.lhv))) {
                    return@FlowFunction emptyList() // Overridden by rhv
                }
            }

            // The "most default" behaviour is encapsulated here:
            transmitTaintNormal(fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            val callee = graph.methodOf(calleeStart)

            if (fact == TaintZeroFact) {
                return@FlowFunction obtainPossibleStartFacts(callee)
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")

            buildSet {
                // Transmit facts on arguments (from 'actual' to 'formal'):
                val actualParams = callExpr.args
                val formalParams = getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
                }

                // Transmit facts on instance (from 'instance' to 'this'):
                if (callExpr is CommonInstanceCallExpr) {
                    addAll(
                        transmitTaintInstanceToThis(
                            fact = fact,
                            from = callExpr.instance,
                            to = getThisInstance(callee),
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

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // unused
        exitStatement: Statement,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            if (fact == TaintZeroFact) {
                return@FlowFunction listOf(TaintZeroFact)
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")
            val callee = graph.methodOf(exitStatement)

            buildSet {
                // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
                if (fact.variable.isOnHeap) {
                    val actualParams = callExpr.args
                    val formalParams = getArgumentsOf(callee)
                    for ((formal, actual) in formalParams.zip(actualParams)) {
                        addAll(
                            transmitTaintArgumentFormalToActual(
                                fact = fact,
                                from = formal,
                                to = actual,
                            )
                        )
                    }
                }

                // Transmit facts on instance (from 'this' to 'instance'):
                if (callExpr is CommonInstanceCallExpr) {
                    addAll(
                        transmitTaintThisToInstance(
                            fact = fact,
                            from = getThisInstance(callee),
                            to = callExpr.instance,
                        )
                    )
                }

                // Transmit facts on static values:
                if (fact.variable.isStatic) {
                    add(fact)
                }

                // Transmit facts on return value (from 'returnValue' to 'lhv'):
                if (exitStatement is CommonReturnInst && callStatement is CommonAssignInst) {
                    // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                    exitStatement.returnValue?.let { returnValue ->
                        addAll(transmitTaintReturn(fact, from = returnValue, to = callStatement.lhv))
                    }
                }
            }
        }
    }
}

class BackwardTaintFlowFunctions<Method, Statement>(
    private val traits: Traits<Method, Statement>,
    private val graph: ApplicationGraph<Method, Statement>,
) : FlowFunctions<TaintDomainFact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<TaintDomainFact> {
        return listOf(TaintZeroFact)
    }

    private fun transmitTaintBackwardAssign(
        fact: Tainted,
        from: CommonValue,
        to: CommonExpr,
    ): Collection<TaintDomainFact> = with(traits) {
        val fromPath = convertToPath(from)
        val toPath = convertToPathOrNull(to)

        if (toPath != null) {
            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath + tail
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
    ): List<TaintDomainFact> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        if (current is CommonAssignInst) {
            transmitTaintBackwardAssign(fact, from = current.lhv, to = current.rhv)
        } else {
            transmitTaintBackwardNormal(fact)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = with(traits) {
        buildSet {
            val fromPath = convertToPath(from)
            val toPath = convertToPath(to)

            val tail = (fact.variable - fromPath) ?: return@buildSet
            val newPath = toPath + tail
            val newTaint = fact.copy(variable = newPath)
            add(newTaint)
        }
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: CommonValue, // actual
        to: CommonValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: CommonValue, // formal
        to: CommonValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: CommonValue, // instance
        to: CommonThis, // this
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: CommonThis, // this
        to: CommonValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // FIXME: unused?
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            // TODO: pass-through on invokedynamic-based String concatenation

            if (fact == TaintZeroFact) {
                return@FlowFunction listOf(TaintZeroFact)
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")
            val callee = getCallee(callExpr)

            if (callee in graph.callees(callStatement)) {

                if (fact.variable.isStatic) {
                    return@FlowFunction emptyList()
                }

                for (actual in callExpr.args) {
                    // Possibly tainted actual parameter:
                    if (fact.variable.startsWith(convertToPathOrNull(actual))) {
                        return@FlowFunction emptyList() // Will be handled by summary edge
                    }
                }

                if (callExpr is CommonInstanceCallExpr) {
                    // Possibly tainted instance:
                    if (fact.variable.startsWith(convertToPathOrNull(callExpr.instance))) {
                        return@FlowFunction emptyList() // Will be handled by summary edge
                    }
                }

            }

            if (callStatement is CommonAssignInst) {
                // Possibly tainted rhv:
                if (fact.variable.startsWith(convertToPathOrNull(callStatement.rhv))) {
                    return@FlowFunction emptyList() // Overridden by lhv
                }
            }

            // The "most default" behaviour is encapsulated here:
            transmitTaintBackwardNormal(fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            val callee = graph.methodOf(calleeStart)

            if (fact == TaintZeroFact) {
                return@FlowFunction obtainPossibleStartFacts(callee)
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")

            buildSet {
                // Transmit facts on arguments (from 'actual' to 'formal'):
                val actualParams = callExpr.args
                val formalParams = getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
                }

                // Transmit facts on instance (from 'instance' to 'this'):
                if (callExpr is CommonInstanceCallExpr) {
                    addAll(
                        transmitTaintInstanceToThis(
                            fact = fact,
                            from = callExpr.instance,
                            to = getThisInstance(callee),
                        )
                    )
                }

                // Transmit facts on static values:
                if (fact.variable.isStatic) {
                    add(fact)
                }

                // Transmit facts on return value (from 'returnValue' to 'lhv'):
                if (calleeStart is CommonReturnInst && callStatement is CommonAssignInst) {
                    // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                    calleeStart.returnValue?.let { returnValue ->
                        addAll(
                            transmitTaintReturn(
                                fact = fact,
                                from = callStatement.lhv,
                                to = returnValue,
                            )
                        )
                    }
                }
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            if (fact == TaintZeroFact) {
                return@FlowFunction listOf(TaintZeroFact)
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")
            val callee = graph.methodOf(exitStatement)

            buildSet {
                // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
                if (fact.variable.isOnHeap) {
                    val actualParams = callExpr.args
                    val formalParams = getArgumentsOf(callee)
                    for ((formal, actual) in formalParams.zip(actualParams)) {
                        addAll(
                            transmitTaintArgumentFormalToActual(
                                fact = fact,
                                from = formal,
                                to = actual,
                            )
                        )
                    }
                }

                // Transmit facts on instance (from 'this' to 'instance'):
                if (callExpr is CommonInstanceCallExpr) {
                    addAll(
                        transmitTaintThisToInstance(
                            fact = fact,
                            from = getThisInstance(callee),
                            to = callExpr.instance,
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
}
