package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem

interface MethodCallFlowFunction {
    sealed interface CallFact

    sealed interface Call2ReturnFact

    sealed interface ZeroCallFact: CallFact

    sealed interface FactCallFact: CallFact

    sealed interface NDFactCallFact: CallFact

    sealed interface ZeroCallFailureFact: ZeroCallFact

    sealed interface FactCallFailureFact: FactCallFact

    sealed interface NDFactCallFailureFact: NDFactCallFact

    data object Unchanged : ZeroCallFact, FactCallFact, NDFactCallFact

    data object CallToReturnZeroFact: ZeroCallFact, Call2ReturnFact, ZeroCallFailureFact

    data object CallToStartZeroFact : ZeroCallFact

    data class CallToReturnFFact(
        val initialFactAp: InitialFactAp,
        val factAp: FinalFactAp,
        val traceInfo: TraceInfo?,
    ) : FactCallFact, ZeroCallFact, Call2ReturnFact, FactCallFailureFact, ZeroCallFailureFact

    data class CallToStartFFact(
        val initialFactAp: InitialFactAp,
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase,
        val traceInfo: TraceInfo?,
    ) : FactCallFact

    data class CallToReturnZFact(
        val factAp: FinalFactAp,
        val traceInfo: TraceInfo?,
    ) : ZeroCallFact, FactCallFact, NDFactCallFact, Call2ReturnFact, ZeroCallFailureFact, FactCallFailureFact, NDFactCallFailureFact

    data class CallToStartZFact(
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase,
        val traceInfo: TraceInfo?,
    ) : ZeroCallFact

    data class CallToReturnNonDistributiveFact(
        val initialFacts: Set<InitialFactAp>,
        val factAp: FinalFactAp,
        val traceInfo: TraceInfo?,
    ) : FactCallFact, ZeroCallFact, NDFactCallFact, Call2ReturnFact, FactCallFailureFact, ZeroCallFailureFact, NDFactCallFailureFact

    data class CallToStartNDFFact(
        val initialFacts: Set<InitialFactAp>,
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase,
        val traceInfo: TraceInfo?,
    ) : NDFactCallFact

    data class SideEffectRequirement(val initialFactAp: InitialFactAp) : FactCallFact, FactCallFailureFact

    data class ZeroSideEffect(val kind: SideEffectKind) : ZeroCallFact, ZeroCallFailureFact
    data class FactSideEffect(val initialFactAp: InitialFactAp, val kind: SideEffectKind) : FactCallFact, FactCallFailureFact

    data class Drop(
        val traceInfo: TraceInfo?,
    ) : ZeroCallFact, FactCallFact, NDFactCallFact, Call2ReturnFact

    sealed interface TraceInfo {
        data object Flow : TraceInfo
        data class Rule(val rule: CommonTaintConfigurationItem, val action: CommonTaintAction): TraceInfo
    }

    fun propagateZeroToZero(): Set<ZeroCallFact>
    fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact>
    fun propagateFactToFact(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp): Set<FactCallFact>
    fun propagateNDFactToFact(initialFacts: Set<InitialFactAp>, currentFactAp: FinalFactAp): Set<NDFactCallFact>

    fun propagateZeroToZeroResolutionFailure(): Set<ZeroCallFailureFact>
    fun propagateZeroToFactResolutionFailure(currentFactAp: FinalFactAp, startFactBase: AccessPathBase): Set<ZeroCallFailureFact>
    fun propagateFactToFactResolutionFailure(initialFactAp: InitialFactAp, currentFactAp: FinalFactAp, startFactBase: AccessPathBase): Set<FactCallFailureFact>
    fun propagateNDFactToFactResolutionFailure(initialFacts: Set<InitialFactAp>, currentFactAp: FinalFactAp, startFactBase: AccessPathBase): Set<NDFactCallFailureFact>
}
