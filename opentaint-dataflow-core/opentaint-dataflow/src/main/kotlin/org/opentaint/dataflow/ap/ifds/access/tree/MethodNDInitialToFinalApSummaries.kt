package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSummaryStorage
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class MethodNDInitialToFinalApSummaries(
    private val methodInitialStatement: CommonInst,
) : CommonNDF2FSummary<AccessNode>(methodInitialStatement),
    TreeFinalApAccess {
    private class NDFactToFactEdgeBuilderBuilder : NDF2FBBuilder<AccessNode>(), TreeFinalApAccess

    override fun createStorage(): Storage<AccessNode> = object :
        DefaultNDF2FSummaryStorage<AccessPath.AccessNode?, AccessNode>() {
        private val initialApIndex = AccessPathInterner(methodInitialStatement)
        private val initialAp = arrayListOf<AccessPath>()

        override fun initialApIdx(ap: InitialFactAp): Int {
            ap as AccessPath
            return initialApIndex.getOrCreateIndex(ap.base, ap.access) {
                initialAp.add(ap)
            }
        }

        override fun getInitialApByIdx(idx: Int): InitialFactAp = initialAp[idx]

        override fun createBuilder(): NDF2FBBuilder<AccessNode> =
            NDFactToFactEdgeBuilderBuilder()

        override fun relevantInitialAp(summaryInitialFactPattern: FinalFactAp): BitSet =
            initialApIndex.findBaseIndices(summaryInitialFactPattern.base) ?: emptyPersistentBitSet()

        override fun createStorage(idx: Int): Storage<AccessPath.AccessNode?, AccessNode> =
            FactStorage(idx)

        private inner class FactStorage(
            override val storageIdx: Int,
        ) : Storage<AccessPath.AccessNode?, AccessNode> {
            private var edges: AccessNode? = null
            private var edgesDelta: AccessNode? = null

            override fun add(element: AccessNode): Storage<AccessPath.AccessNode?, AccessNode>? {
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
