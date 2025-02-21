package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.cfg.callExpr

abstract class AbstractTaintBackwardFunctions(
    protected val graph: JIRApplicationGraph,
    protected val maxPathLength: Int,
) : FlowFunctionsSpace {

    override fun obtainPossibleStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    abstract fun transmitBackDataFlow(from: JIRValue, to: JIRExpr, atInst: JIRInst, fact: DomainFact, dropFact: Boolean): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: JIRInst, nextInst: JIRInst, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: JIRInst, next: JIRInst) = FlowFunctionInstance { fact ->
        // fact.activation != current needed here to jump over assignment where the fact appeared
        if (current is JIRAssignInst && (fact !is TaintNode || fact.activation != current)) {
            transmitBackDataFlow(current.lhv, current.rhv, current, fact, dropFact = false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JIRInst,
        callee: JIRMethod
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

        buildList {
            // TODO: think about activation point handling for statics here
            if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                add(fact)
            }

            if (callStatement is JIRAssignInst) {
                graph.entryPoint(callee).filterIsInstance<JIRReturnInst>().forEach { returnInst ->
                    returnInst.returnValue?.let {
                        addAll(transmitBackDataFlow(callStatement.lhv, it, callStatement, fact, dropFact = true))
                    }
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                val thisInstance = callee.thisInstance
                addAll(transmitBackDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true))
            }

            val formalParams = graph.classpath.getFormalParamsOf(callee)

            callExpr.args.zip(formalParams).forEach { (actual, formal) ->
                // FilterNot is needed for reasons described in comment for symmetric case in
                //  AbstractTaintForwardFunctions.obtainExitToReturnSiteFlowFunction
                addAll(transmitBackDataFlow(actual, formal, callStatement, fact, dropFact = true)
                    .filterNot { it is TaintNode && !it.variable.isOnHeap })
            }
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        if (fact !is TaintNode) {
            return@FlowFunctionInstance if (fact == ZEROFact) {
                listOf(fact)
            } else {
                emptyList()
            }
        }

        val factPath = fact.variable
        val callExpr = callStatement.callExpr ?: error("CallStatement is expected to contain callExpr")

        // TODO: check that this is legal
        if (fact.activation == callStatement) {
            return@FlowFunctionInstance listOf(fact)
        }

        if (fact.variable.isStatic) {
            return@FlowFunctionInstance emptyList()
        }

        callExpr.args.forEach {
            if (fact.variable.startsWith(it.toPathOrNull())) {
                return@FlowFunctionInstance emptyList()
            }
        }

        if (callExpr is JIRInstanceCallExpr) {
            if (factPath.startsWith(callExpr.instance.toPathOrNull())) {
                return@FlowFunctionInstance emptyList()
            }
        }

        if (callStatement is JIRAssignInst) {
            val lhvPath = callStatement.lhv.toPath()
            if (factPath.startsWith(lhvPath)) {
                return@FlowFunctionInstance emptyList()
            }
        }

        transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JIRInst,
        returnSite: JIRInst,
        exitStatement: JIRInst
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = graph.methodOf(exitStatement)
        val formalParams = graph.classpath.getFormalParamsOf(callee)

        buildList {
            formalParams.zip(actualParams).forEach { (formal, actual) ->
                addAll(transmitBackDataFlow(formal, actual, exitStatement, fact, dropFact = true))
            }

            if (callExpr is JIRInstanceCallExpr) {
                addAll(transmitBackDataFlow(callee.thisInstance, callExpr.instance, exitStatement, fact, dropFact = true))
            }

            if (fact is TaintNode && fact.variable.isStatic) {
                add(fact)
            }
        }
    }
}