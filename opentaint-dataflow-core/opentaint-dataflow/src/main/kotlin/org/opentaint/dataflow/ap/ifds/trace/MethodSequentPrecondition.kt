package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

fun interface MethodSequentPrecondition {
    sealed interface SequentPrecondition {
        data object Unchanged : SequentPrecondition
        data class Facts(val facts: List<PreconditionFactsForInitialFact>) : SequentPrecondition
    }

    data class PreconditionFactsForInitialFact(
        val initialFact: InitialFactAp,
        val preconditionFacts: List<InitialFactAp>
    )

    fun factPrecondition(fact: InitialFactAp): SequentPrecondition
}
