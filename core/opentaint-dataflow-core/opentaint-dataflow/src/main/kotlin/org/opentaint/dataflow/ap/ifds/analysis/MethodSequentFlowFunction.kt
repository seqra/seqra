package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem

interface MethodSequentFlowFunction {
    sealed interface Sequent {
        data object Unchanged : Sequent
        data object ZeroToZero : Sequent

        data class ZeroToFact(val factAp: FinalFactAp, val traceInfo: TraceInfo?) : Sequent
        data class FactToFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp, val traceInfo: TraceInfo?) : Sequent
        data class NDFactToFact(val initialFacts: Set<InitialFactAp>, val factAp: FinalFactAp, val traceInfo: TraceInfo?) : Sequent

        data class SideEffectRequirement(val initialFactAp: InitialFactAp) : Sequent

        sealed interface SideEffect : Sequent
        data class ZeroSideEffect(val kind: SideEffectKind) : SideEffect
        data class FactSideEffect(val initialFactAp: InitialFactAp, val kind: SideEffectKind) : SideEffect
    }

    sealed interface TraceInfo {
        data object Flow : TraceInfo
        data object ApplySummary : TraceInfo
        data class Rule(val rule: CommonTaintConfigurationItem, val action: CommonTaintAction): TraceInfo
    }

    fun propagateZeroToZero(): Set<Sequent>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<Sequent>
    fun propagateNDFactToFact(initialFacts: Set<InitialFactAp>, currentFactAp: FinalFactAp): Set<Sequent>
}