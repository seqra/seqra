package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.core.analysis.ApplicationGraph
import org.opentaint.ir.api.core.cfg.CoreExpr
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation
import org.opentaint.ir.api.core.cfg.CoreValue
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import javax.swing.plaf.nimbus.State

abstract class AbstractTaintBackwardFunctions<Method, Location, Statement, Value, Expr, Type>(
    protected val graph: ApplicationGraph<Method, Statement>,
    protected val maxPathLength: Int,
) : FlowFunctionsSpace<Statement, Method>
        where Value : CoreValue<Value, Type>,
              Expr : CoreExpr<Type, Value>,
              Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, Expr> {

    override fun obtainPossibleStartFacts(startStatement: Statement): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    abstract fun transmitBackDataFlow(
        from: Value,
        to: Expr,
        atInst: Statement,
        fact: DomainFact,
        dropFact: Boolean
    ): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: Statement, nextInst: Statement, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: Statement, next: Statement) = FlowFunctionInstance { fact ->
        // TODO Caelmbleidd

        // fact.activation != current needed here to jump over assignment where the fact appeared
        if (current is JIRAssignInst && (fact !is TaintNode || fact.activation != current)) {
            @Suppress("UNCHECKED_CAST")
            this as AbstractTaintBackwardFunctions<JIRMethod, JIRInstLocation, JIRInst, JIRValue, JIRExpr, JIRType>
            transmitBackDataFlow(current.lhv, current.rhv, current, fact, dropFact = false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        callee: Method
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->

        buildList {
            if (callStatement is JIRInst) { // TODO Caelmbleidd
                @Suppress("UNCHECKED_CAST")
                this@AbstractTaintBackwardFunctions as AbstractTaintBackwardFunctions<JIRMethod, JIRInstLocation, JIRInst, JIRValue, JIRExpr, JIRType>
                callee as JIRMethod

                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

                // TODO: think about activation point handling for statics here
                if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                    add(fact)
                }

                if (callStatement is JIRAssignInst) {
                    graph.entryPoint(callee).filterIsInstance<JIRReturnInst>().forEach { returnInst ->
                        returnInst.returnValue?.let {
                            addAll(
                                transmitBackDataFlow(
                                    callStatement.lhv,
                                    it,
                                    callStatement,
                                    fact,
                                    dropFact = true
                                )
                            )
                        }
                    }
                }

                if (callExpr is JIRInstanceCallExpr) {
                    val thisInstance = (callee as JIRMethod).thisInstance
                    addAll(transmitBackDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true))
                }

                val formalParams = (graph as JIRApplicationGraph).classpath.getFormalParamsOf(callee as JIRMethod)

                callExpr.args.zip(formalParams).forEach { (actual, formal) ->
                    // FilterNot is needed for reasons described in comment for symmetric case in
                    //  AbstractTaintForwardFunctions.obtainExitToReturnSiteFlowFunction
                    addAll(transmitBackDataFlow(actual, formal, callStatement, fact, dropFact = true)
                        .filterNot { it is TaintNode && !it.variable.isOnHeap })
                }
            }
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: Statement,
        returnSite: Statement
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        if (fact !is TaintNode) {
            return@FlowFunctionInstance if (fact == ZEROFact) {
                listOf(fact)
            } else {
                emptyList()
            }
        }

        if (callStatement is JIRInst) {
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
        }

        transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->

        buildList {
            if (callStatement is JIRInst) {
                @Suppress("UNCHECKED_CAST")
                this@AbstractTaintBackwardFunctions as AbstractTaintBackwardFunctions<JIRMethod, JIRInstLocation, JIRInst, JIRValue, JIRExpr, JIRType>
                exitStatement as JIRInst
                graph as JIRApplicationGraph

                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
                val actualParams = callExpr.args
                val callee = graph.methodOf(exitStatement)
                val formalParams = graph.classpath.getFormalParamsOf(callee)

                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    addAll(transmitBackDataFlow(formal, actual, exitStatement, fact, dropFact = true))
                }

                if (callExpr is JIRInstanceCallExpr) {
                    addAll(
                        transmitBackDataFlow(
                            callee.thisInstance,
                            callExpr.instance,
                            exitStatement,
                            fact,
                            dropFact = true
                        )
                    )
                }

                if (fact is TaintNode && fact.variable.isStatic) {
                    add(fact)
                }
            }
        }
    }
}