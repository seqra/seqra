package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition.PassRuleCondition

interface MethodCallPrecondition {
    sealed interface CallPrecondition {
        data object Unchanged : CallPrecondition
        data class Facts(val facts: List<PreconditionFactsForInitialFact>) : CallPrecondition
    }

    data class PreconditionFactsForInitialFact(
        val initialFact: InitialFactAp,
        val preconditionFacts: List<CallPreconditionFact>,
    )

    sealed interface CallPreconditionFact {
        data class CallToReturnTaintRule(val precondition: TaintRulePrecondition) : CallPreconditionFact
        data class CallToStart(val callerFact: InitialFactAp, val startFactBase: AccessPathBase) : CallPreconditionFact
    }

    fun factPrecondition(fact: InitialFactAp): CallPrecondition

    data class PassRuleConditionFacts(val facts: List<InitialFactAp>)

    fun resolvePassRuleCondition(precondition: PassRuleCondition): List<PassRuleConditionFacts>
}
