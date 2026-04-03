package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonFinalFactList
import org.opentaint.dataflow.ap.ifds.access.tree.TreeSetWithCompression.Companion.SIZE_TO_FORCE_INTERN
import org.opentaint.dataflow.ap.ifds.access.tree.TreeSetWithCompression.Companion.internImpl

class TreeFinalFactList(
    override val apManager: TreeApManager
) : CommonFinalFactList<AccessTree.AccessNode>(), TreeFinalApAccess {
    override val storage: AccessStorage<AccessTree.AccessNode> = TreeNodeListStorage(apManager)

    private class TreeNodeListStorage(val apManager: TreeApManager) : AccessStorage<AccessTree.AccessNode> {
        private val storage = mutableListOf<AccessTree.AccessNode>()

        override fun add(fact: AccessTree.AccessNode) {
            storage.add(internIfRequired(fact))
            intern()
        }

        override fun get(idx: Int): AccessTree.AccessNode = storage[idx]
        override fun removeLast(): AccessTree.AccessNode = storage.removeLast()

        private val interner = AccessTreeSoftInterner(apManager)
        private var operationsBeforeIntern = INTERN_RATE
        private var maxTreeSize = 0L

        fun internIfRequired(node: AccessTree.AccessNode): AccessTree.AccessNode {
            if (node.size < SIZE_TO_FORCE_INTERN) return node
            return interner.intern(node)
        }

        fun intern(): Unit = interner.internImpl(
            apManager.cancellation,
            lastUpdated = storage.last(),
            size = storage.size,
            maxNodeSize = maxTreeSize,
            updateMaxNodeSize = { maxTreeSize = it },
            decOperations = { operationsBeforeIntern-- },
            resetOperation = { operationsBeforeIntern = INTERN_RATE },
            getNode = { storage[it] },
            setNode = { i, n -> storage[i] = n }
        )
    }

    companion object {
        private const val INTERN_RATE = 1000

        fun factCompressionRequired(fact: FinalFactAp): Boolean =
            (fact as AccessTree).access.size > 100
    }
}
