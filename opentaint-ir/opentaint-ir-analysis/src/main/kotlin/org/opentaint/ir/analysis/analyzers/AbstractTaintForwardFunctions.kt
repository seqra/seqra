package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.cfg.callExpr

abstract class AbstractTaintForwardFunctions(
    protected val graph: JIRApplicationGraph
) : FlowFunctionsSpace {

    abstract fun transmitDataFlow(from: JIRExpr, to: JIRValue, atInst: JIRInst, fact: DomainFact, dropFact: Boolean): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: JIRInst, nextInst: JIRInst, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst): FlowFunctionInstance =
        object : FlowFunctionInstance {
            override val inIds = this@AbstractTaintForwardFunctions.inIds

            override fun compute(fact: DomainFact): Collection<DomainFact> {
                if (fact is TaintNode && fact.activation == current) {
                    return listOf(fact.activatedCopy)
                }

                if (current is JIRAssignInst) {
                    return transmitDataFlow(current.rhv, current.lhv, current, fact, dropFact = false)
                }

                return transmitDataFlowAtNormalInst(current, next, fact)
            }
        }

    override fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        callee: JIRMethod
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override val inIds = this@AbstractTaintForwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            if (fact is TaintNode && fact.activation == callStatement) {
                return emptyList()
            }

            val ans = mutableListOf<DomainFact>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val formalParams = callee.parameters.map {
                JIRArgument.of(it.index, it.name, graph.classpath.findTypeOrNull(it.type.typeName)!!)
            }

            formalParams.zip(actualParams).forEach { (formal, actual) ->
                ans += transmitDataFlow(actual, formal, callStatement, fact, dropFact = true)
            }

            if (callExpr is JIRInstanceCallExpr) {
                val thisInstance = callee.thisInstance
                ans += transmitDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true)
            }

            if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                ans.add(fact)
            }

            return ans
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override val inIds = this@AbstractTaintForwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            if (fact == ZEROFact) {
                return listOf(fact)
            }

            if (fact !is TaintNode) {
                return emptyList()
            }

            if (fact.activation == callStatement) {
                return listOf(fact.activatedCopy)
            }

            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args

            if (fact.variable.isStatic) {
                return emptyList()
            }

            actualParams.mapNotNull { it.toPathOrNull() }.forEach {
                if (fact.variable.startsWith(it)) {
                    return emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return emptyList() // Will be handled by summary edge
                }
            }

            if (callStatement is JIRAssignInst && fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return emptyList()
            }

            return transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override val inIds = this@AbstractTaintForwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            val ans = mutableListOf<DomainFact>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val callee = exitStatement.location.method
            // TODO: maybe we can always use fact instead of updatedFact here
            val updatedFact = if (fact is TaintNode && fact.activation?.location?.method == callee) {
                fact.updateActivation(callStatement)
            } else {
                fact
            }
            val formalParams = callee.parameters.map {
                JIRArgument.of(it.index, it.name, graph.classpath.findTypeOrNull(it.type.typeName)!!)
            }

            if (fact is TaintNode && fact.variable.isOnHeap) {
                // If there is some method A.f(formal: T) that is called like A.f(actual) then
                //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                //  2. We shouldn't propagate from formal to actual (as formal is local)
                //  Second case is why we need check for isOnHeap
                // TODO: add test for handling of 2nd case
                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    ans += transmitDataFlow(formal, actual, exitStatement, updatedFact, dropFact = true)
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                ans += transmitDataFlow(callee.thisInstance, callExpr.instance, exitStatement, updatedFact, dropFact = true)
            }

            if (callStatement is JIRAssignInst && exitStatement is JIRReturnInst) {
                exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                    ans += transmitDataFlow(it, callStatement.lhv, exitStatement, updatedFact, dropFact = true)
                }
            }

            if (fact is TaintNode && fact.variable.isStatic && fact !in ans) {
                ans.add(fact)
            }

            return ans
        }
    }
}