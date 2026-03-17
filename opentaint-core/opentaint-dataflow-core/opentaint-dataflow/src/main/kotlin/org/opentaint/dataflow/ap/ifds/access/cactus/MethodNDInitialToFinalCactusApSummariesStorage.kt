package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.access.cactus.AccessCactus.AccessNode
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSummaryStorageWithAp
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodNDInitialToFinalCactusApSummariesStorage(methodEntryPoint: CommonInst) :
    CommonNDF2FSummary<AccessNode>(methodEntryPoint), CactusFinalApAccess {
    private class Builder : NDF2FBBuilder<AccessNode>(), CactusFinalApAccess

    override fun createStorage(): Storage<AccessNode> = object :
        DefaultNDF2FSummaryStorageWithAp<AccessPathWithCycles.AccessNode?, AccessNode>(methodEntryPoint),
        CactusInitialApAccess {
        override fun createBuilder(): NDF2FBBuilder<AccessNode> = Builder()

        override fun createStorage(idx: Int): Storage<AccessPathWithCycles.AccessNode?, AccessNode> = FactStorage(idx)

        private inner class FactStorage(
            override val storageIdx: Int,
        ) : Storage<AccessPathWithCycles.AccessNode?, AccessNode> {
            private var edges: AccessNode? = null
            private var edgesDelta: AccessNode? = null

            override fun add(element: AccessNode): Storage<AccessPathWithCycles.AccessNode?, AccessNode>? {
                val currentEdges = edges
                if (currentEdges == null) {
                    edges = element
                    edgesDelta = element
                    return this
                }

                val (modifiedEdges, modificationDelta) = currentEdges.mergeAddDelta(element)
                if (modificationDelta == null) return null

                edges = modifiedEdges
                edgesDelta = edgesDelta?.mergeAdd(modificationDelta) ?: modificationDelta
                return this
            }

            override fun getAndResetDelta(delta: MutableList<AccessNode>) {
                delta += edgesDelta ?: return
                edgesDelta = null
            }

            override fun collectTo(dst: MutableList<AccessNode>) {
                edges?.let { dst += it }
            }
        }
    }
}
