package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodSequentFlowFunction {
    sealed interface Sequent {
        data object Unchanged : Sequent
        data object ZeroToZero : Sequent
        data class ZeroToFact(val factAp: FinalFactAp) : Sequent
        data class FactToFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp) : Sequent
        data class SideEffectRequirement(val initialFactAp: InitialFactAp) : Sequent

        data class NDFactToFact(val initialFacts: Set<InitialFactAp>, val factAp: FinalFactAp) : Sequent
    }

    fun propagateZeroToZero(): Set<Sequent>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<Sequent>
    fun propagateNDFactToFact(initialFacts: Set<InitialFactAp>, currentFactAp: FinalFactAp): Set<Sequent>
}