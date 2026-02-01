package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodSequentPrecondition {
    sealed interface SequentPrecondition {
        data object Unchanged : SequentPrecondition
    }

    sealed interface SequentPreconditionFacts: SequentPrecondition {
        val fact: InitialFactAp
    }

    data class PreconditionFactsForInitialFact(
        override val fact: InitialFactAp,
        val preconditionFacts: List<InitialFactAp>
    ): SequentPreconditionFacts

    data class SequentSource(
        override val fact: InitialFactAp,
        val rule: TaintRulePrecondition.Source
    ): SequentPreconditionFacts

    fun factPrecondition(fact: InitialFactAp): Set<SequentPrecondition>
}
