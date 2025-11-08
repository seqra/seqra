package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodCallPrecondition {
    sealed interface CallPrecondition {
        data object Unchanged : CallPrecondition
        data class Facts(val facts: List<CallPreconditionFact>) : CallPrecondition
    }

    sealed interface CallPreconditionFact {
        data class CallToReturnTaintRule(val precondition: TaintRulePrecondition) : CallPreconditionFact
        data class CallToStart(val callerFact: InitialFactAp, val startFactBase: AccessPathBase) : CallPreconditionFact
    }

    fun factPrecondition(fact: InitialFactAp): CallPrecondition
}
