package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonFinalFactList
import org.opentaint.dataflow.ap.ifds.access.tree.TreeSetWithCompression.Companion.internImpl
import org.opentaint.dataflow.util.SoftReferenceManager

class TreeFinalFactList(
    override val apManager: TreeApManager
) : CommonFinalFactList<AccessTree.AccessNode>(), TreeFinalApAccess {
    override val storage: AccessStorage<AccessTree.AccessNode> = TreeNodeListStorage(apManager.refManager)

    private class TreeNodeListStorage(refManager: SoftReferenceManager) : AccessStorage<AccessTree.AccessNode> {
        private val storage = mutableListOf<AccessTree.AccessNode>()

        override fun add(fact: AccessTree.AccessNode) {
            storage.add(fact)
            intern()
        }

        override fun get(idx: Int): AccessTree.AccessNode = storage[idx]
        override fun removeLast(): AccessTree.AccessNode = storage.removeLast()

        private val interner = AccessTreeSoftInterner(refManager)
        private var operationsBeforeIntern = INTERN_RATE
        private var maxTreeSize = 0

        fun intern(): Unit = interner.internImpl(
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
