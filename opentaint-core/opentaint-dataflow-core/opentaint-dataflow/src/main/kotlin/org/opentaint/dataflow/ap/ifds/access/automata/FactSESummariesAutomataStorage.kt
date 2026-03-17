package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.FactSEBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.SideEffectExclusionMergingStorage
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.Storage
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

class FactSESummariesAutomataStorage(methodEntryPoint: CommonInst) :
    CommonFactSideEffectSummary<AccessGraph, AccessGraph>(methodEntryPoint),
    AutomataInitialApAccess, AutomataFinalApAccess {
    override fun createStorage(): Storage<AccessGraph, AccessGraph> = SEStorage()
}

private class SEStorage : Storage<AccessGraph, AccessGraph> {
    private val storage = ConcurrentHashMap<AccessGraph, SEExclusionStorage>()

    override fun add(
        iap: AccessGraph,
        se: Map<SideEffectKind, ExclusionSet>,
        added: MutableList<FactSEBuilder<AccessGraph>>
    ) {
        val storageNode = storage.computeIfAbsent(iap) { SEExclusionStorage(iap) }
        for ((kind, exclusion) in se) {
            storageNode.add(kind, exclusion)?.let { added += it }
        }
    }

    override fun collectSummariesTo(
        dst: MutableList<FactSEBuilder<AccessGraph>>,
        initialFactPattern: AccessGraph?
    ) {
        storage.values.forEach {
            dst += it.summaries()
        }
    }
}

private class SEExclusionStorage(
    val iap: AccessGraph
) : SideEffectExclusionMergingStorage<AccessGraph>() {
    override fun createBuilder(): FactSEBuilder<AccessGraph> =
        Builder().setInitialAp(iap)
}

private class Builder : FactSEBuilder<AccessGraph>(), AutomataInitialApAccess {
    override fun nonNullIAP(iap: AccessGraph?): AccessGraph = iap
        ?: error("iap not initialized")
}
