package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodCallFlowFunction {
    sealed interface ZeroCallFact

    sealed interface FactCallFact

    sealed interface NDFactCallFact

    data object Unchanged : ZeroCallFact, FactCallFact, NDFactCallFact

    data object CallToReturnZeroFact: ZeroCallFact

    data object CallToStartZeroFact : ZeroCallFact

    data class CallToReturnFFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp) : FactCallFact, ZeroCallFact

    data class CallToStartFFact(
        val initialFactAp: InitialFactAp,
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase
    ) : FactCallFact

    data class CallToReturnZFact(val factAp: FinalFactAp) : ZeroCallFact

    data class CallToStartZFact(val callerFactAp: FinalFactAp, val startFactBase: AccessPathBase) : ZeroCallFact

    data class SideEffectRequirement(val initialFactAp: InitialFactAp) : FactCallFact

    data class CallToReturnNonDistributiveFact(
        val initialFacts: Set<InitialFactAp>,
        val factAp: FinalFactAp,
    ) : FactCallFact, ZeroCallFact, NDFactCallFact

    data class CallToStartNDFFact(
        val initialFacts: Set<InitialFactAp>,
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase
    ) : NDFactCallFact

    fun propagateZeroToZero(): Set<ZeroCallFact>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<FactCallFact>
    fun propagateNDFactToFact(initialFacts: Set<InitialFactAp>, currentFactAp: FinalFactAp): Set<NDFactCallFact>
}
