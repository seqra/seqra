package org.opentaint.dataflow.ap.ifds.access.cactus

import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.FactSEBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.Storage
import org.opentaint.ir.api.common.cfg.CommonInst

class FactSESummariesCactusStorage(
    methodInitialInst: CommonInst
) : CommonFactSideEffectSummary<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(methodInitialInst),
    CactusInitialApAccess, CactusFinalApAccess {
    override fun createStorage(): Storage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        CactusSEStorage()
}

private class CactusSEStorage : Storage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
    private var initialAccessToStorage =
        persistentHashMapOf<AccessPathWithCycles.AccessNode?, CactusSEMergeStorage>()

    private fun getOrCreate(initialAccess: AccessPathWithCycles.AccessNode?): CactusSEMergeStorage =
        initialAccessToStorage.getOrElse(initialAccess) {
            CactusSEMergeStorage(initialAccess).also {
                initialAccessToStorage = initialAccessToStorage.put(initialAccess, it)
            }
        }

    override fun add(
        iap: AccessPathWithCycles.AccessNode?,
        se: Map<SideEffectKind, ExclusionSet>,
        added: MutableList<FactSEBuilder<AccessPathWithCycles.AccessNode?>>
    ) {
        val storageNode = getOrCreate(iap)
        for ((kind, exclusion) in se) {
            storageNode.add(kind, exclusion)?.let { added += it }
        }
    }

    override fun collectSummariesTo(
        dst: MutableList<FactSEBuilder<AccessPathWithCycles.AccessNode?>>,
        initialFactPattern: AccessCactus.AccessNode?
    ) {
        initialAccessToStorage.values.forEach { storage ->
            dst += storage.summaries()
        }
    }
}

private class CactusSEMergeStorage(val initialAccess: AccessPathWithCycles.AccessNode?) :
    CommonFactSideEffectSummary.SideEffectExclusionMergingStorage<AccessPathWithCycles.AccessNode?>() {
    override fun createBuilder(): FactSEBuilder<AccessPathWithCycles.AccessNode?> =
        FactSECactusApBuilder().setInitialAp(initialAccess)
}

private class FactSECactusApBuilder: FactSEBuilder<AccessPathWithCycles.AccessNode?>(),
    CactusInitialApAccess, CactusFinalApAccess {
    override fun nonNullIAP(iap: AccessPathWithCycles.AccessNode?): AccessPathWithCycles.AccessNode? = iap
}
