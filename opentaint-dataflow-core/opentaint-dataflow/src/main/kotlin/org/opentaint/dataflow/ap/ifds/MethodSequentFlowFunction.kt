package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

sealed interface Sequent {
    object ZeroToZero : Sequent
    data class ZeroToFact(val factAp: FinalFactAp) : Sequent
    data class FactToFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp) : Sequent
    data class SideEffectRequirement(val initialFactAp: InitialFactAp) : Sequent
}

interface MethodSequentFlowFunction {
    fun propagateZeroToZero(): Set<Sequent>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<Sequent>
}