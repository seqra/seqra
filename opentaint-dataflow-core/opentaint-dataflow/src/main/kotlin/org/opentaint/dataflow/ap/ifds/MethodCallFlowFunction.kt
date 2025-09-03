package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodCallFlowFunction {
    sealed interface ZeroCallFact

    sealed interface FactCallFact

    object CallToReturnZeroFact: ZeroCallFact

    object CallToStartZeroFact : ZeroCallFact

    data class CallToReturnFFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp) : FactCallFact

    data class CallToStartFFact(
        val initialFactAp: InitialFactAp,
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase
    ) : FactCallFact

    data class CallToReturnZFact(val factAp: FinalFactAp) : ZeroCallFact

    data class CallToStartZFact(val callerFactAp: FinalFactAp, val startFactBase: AccessPathBase) : ZeroCallFact

    data class SideEffectRequirement(val initialFactAp: InitialFactAp) : FactCallFact

    fun propagateZeroToZero(): Set<ZeroCallFact>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<FactCallFact>
}