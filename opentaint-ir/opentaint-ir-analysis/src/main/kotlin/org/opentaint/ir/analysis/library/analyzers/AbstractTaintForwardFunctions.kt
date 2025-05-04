package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionInstance
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.core.cfg.CoreExpr
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation
import org.opentaint.ir.api.core.cfg.CoreValue
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr

abstract class AbstractTaintForwardFunctions<Method, Location, Statement, Value, Expr, Type>(
    protected val cp: JIRProject
) : FlowFunctionsSpace<Statement, Method>
        where Value : CoreValue<Value, Type>,
              Expr : CoreExpr<Type, Value>,
              Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, Expr> {

    abstract fun transmitDataFlow(
        from: Expr,
        to: Value,
        atInst: Statement,
        fact: DomainFact,
        dropFact: Boolean
    ): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: Statement, nextInst: Statement, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: Statement, next: Statement) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == current) {
            listOf(fact.activatedCopy)
        } else if (current is JIRAssignInst) {
            @Suppress("UNCHECKED_CAST")
            transmitDataFlow(current.rhv as Expr, current.lhv as Value, current, fact, dropFact = false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        callee: Method
    ) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == callStatement) {
            return@FlowFunctionInstance emptyList()
        }

        buildList {
            if (callStatement is JIRInst) {
                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
                val actualParams = callExpr.args
                val formalParams = cp.getFormalParamsOf(callee as JIRMethod)

                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    @Suppress("UNCHECKED_CAST")
                    addAll(transmitDataFlow(actual as Expr, formal as Value, callStatement, fact, dropFact = true))
                }

                if (callExpr is JIRInstanceCallExpr) {
                    @Suppress("UNCHECKED_CAST")
                    addAll(
                        transmitDataFlow(
                            callExpr.instance as Expr,
                            callee.thisInstance as Value,
                            callStatement,
                            fact,
                            dropFact = true
                        )
                    )
                }

                if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                    add(fact)
                }
            }
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: Statement,
        returnSite: Statement
    ) = FlowFunctionInstance { fact ->
        if (fact == ZEROFact) {
            return@FlowFunctionInstance listOf(fact)
        }

        if (fact !is TaintNode || fact.variable.isStatic) {
            return@FlowFunctionInstance emptyList()
        }

        if (fact.activation == callStatement) {
            return@FlowFunctionInstance listOf(fact.activatedCopy)
        }

        if (callStatement is JIRInst) {
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args

            actualParams.mapNotNull { it.toPathOrNull() }.forEach {
                if (fact.variable.startsWith(it)) {
                    return@FlowFunctionInstance emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JIRInstanceCallExpr) {
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunctionInstance emptyList() // Will be handled by summary edge
                }
            }

            if (callStatement is JIRAssignInst && fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunctionInstance emptyList()
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
                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
                val actualParams = callExpr.args
                val callee = exitStatement.location.method
                // TODO: maybe we can always use fact instead of updatedFact here
                val updatedFact = if (fact is TaintNode && fact.activation?.location?.method == callee) {
                    fact.updateActivation(callStatement)
                } else {
                    fact
                }
                val formalParams = cp.getFormalParamsOf(callee as JIRMethod)

                if (fact is TaintNode && fact.variable.isOnHeap) {
                    // If there is some method A.f(formal: T) that is called like A.f(actual) then
                    //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                    //  2. We shouldn't propagate from formal to actual (as formal is local)
                    //  Second case is why we need check for isOnHeap
                    // TODO: add test for handling of 2nd case
                    formalParams.zip(actualParams).forEach { (formal, actual) ->
                        @Suppress("UNCHECKED_CAST")
                        addAll(
                            transmitDataFlow(
                                formal as Expr,
                                actual as Value,
                                exitStatement,
                                updatedFact,
                                dropFact = true
                            )
                        )
                    }
                }

                if (callExpr is JIRInstanceCallExpr) {
                    @Suppress("UNCHECKED_CAST")
                    addAll(
                        transmitDataFlow(
                            callee.thisInstance as Expr,
                            callExpr.instance as Value,
                            exitStatement,
                            updatedFact,
                            dropFact = true
                        )
                    )
                }

                if (callStatement is JIRAssignInst && exitStatement is JIRReturnInst) {
                    exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                        @Suppress("UNCHECKED_CAST")
                        addAll(
                            transmitDataFlow(
                                it as Expr,
                                callStatement.lhv as Value,
                                exitStatement,
                                updatedFact,
                                dropFact = true
                            )
                        )
                    }
                }

                if (fact is TaintNode && fact.variable.isStatic) {
                    add(fact)
                }
            }
        }
    }
}