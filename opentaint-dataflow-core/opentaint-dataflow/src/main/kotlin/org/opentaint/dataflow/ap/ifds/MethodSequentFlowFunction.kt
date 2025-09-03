package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

sealed interface Sequent {
    object ZeroToZero : Sequent
    data class ZeroToFact(val factAp: FinalFactAp) : Sequent
    data class FactToFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp) : Sequent
}

interface MethodSequentFlowFunction {
    fun propagateZeroToZero(): Set<Sequent.ZeroToZero>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent.ZeroToFact>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<Sequent.FactToFact>
}