package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.util.SoftReferenceManager
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

open class TreeSetWithCompression(maxInstIdx: Int, refManager: SoftReferenceManager) {
    val edges = arrayOfNulls<AccessTreeNode?>(instructionStorageSize(maxInstIdx))

    private val interner = AccessTreeSoftInterner(refManager)
    private var operationsBeforeIntern = INTERN_RATE
    private var maxTreeSize = 0

    fun intern(idx: Int): Unit = interner.internImpl(
        lastUpdated = edges[idx],
        size = edges.size,
        maxNodeSize = maxTreeSize,
        updateMaxNodeSize = { maxTreeSize = it },
        decOperations = { operationsBeforeIntern-- },
        resetOperation = { operationsBeforeIntern = INTERN_RATE },
        getNode = { edges[it] },
        setNode = { i, n -> edges[i] = n }
    )

    companion object {
        inline fun AccessTreeSoftInterner.internImpl(
            lastUpdated: AccessTreeNode?,
            size: Int,
            maxNodeSize: Int,
            updateMaxNodeSize: (Int) -> Unit,
            decOperations: () -> Int,
            resetOperation: () -> Unit,
            crossinline getNode: (Int) -> AccessTreeNode?,
            crossinline setNode: (Int, AccessTreeNode) -> Unit,
        ) {
            lastUpdated?.let { updateMaxNodeSize(maxOf(maxNodeSize, it.size)) }

            if (decOperations() > 0) return
            if (maxNodeSize < MIN_SIZE_TO_INTERN) return
            resetOperation()

            withInterner { interner, cache ->
                for (i in 0 until size) {
                    val node = getNode(i) ?: continue
                    setNode(i, node.internNodes(interner, cache))
                }
            }
        }

        const val MIN_SIZE_TO_INTERN = 100
        private const val INTERN_RATE = 100
    }
}
