package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

fun interface MethodSequentPrecondition {
    sealed interface SequentPrecondition {
        data object Unchanged : SequentPrecondition
        data class Facts(val facts: List<InitialFactAp>) : SequentPrecondition
    }

    fun factPrecondition(fact: InitialFactAp): SequentPrecondition
}
