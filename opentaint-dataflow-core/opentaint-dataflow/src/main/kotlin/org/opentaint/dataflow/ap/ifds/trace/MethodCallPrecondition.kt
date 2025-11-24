package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodCallPrecondition {
    sealed interface CallPrecondition {
        data object Unchanged : CallPrecondition
        data class Facts(val facts: List<PreconditionFactsForInitialFact>) : CallPrecondition
    }

    data class PreconditionFactsForInitialFact(
        val initialFact: InitialFactAp,
        val preconditionFacts: List<CallPreconditionFact>
    )

    sealed interface CallPreconditionFact {
        data class CallToReturnTaintRule(val precondition: TaintRulePrecondition) : CallPreconditionFact
        data class CallToStart(val callerFact: InitialFactAp, val startFactBase: AccessPathBase) : CallPreconditionFact
    }

    fun factPrecondition(fact: InitialFactAp): CallPrecondition
}
