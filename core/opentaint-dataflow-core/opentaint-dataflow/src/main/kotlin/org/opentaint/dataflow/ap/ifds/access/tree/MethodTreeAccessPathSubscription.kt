package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonAPSub
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactNDEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonZeroEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSubStorage
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.SoftReferenceManager
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreate
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap
import org.opentaint.ir.api.common.cfg.CommonInst
import java.lang.ref.Reference
import java.util.BitSet

class MethodTreeAccessPathSubscription(
    override val apManager: TreeApManager,
) : CommonAPSub<AccessPath.AccessNode?, AccessTree.AccessNode>(),
    TreeInitialApAccess, TreeFinalApAccess {
    private val interner: AccessTreeSoftInterner = AccessTreeSoftInterner(apManager)

    override fun createZ2FSubStorage(callerEp: CommonInst): Z2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        SummaryEdgeFactTreeSubscriptionStorage(apManager)

    override fun createF2FSubStorage(callerEp: CommonInst): F2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        SummaryEdgeFactAbstractTreeSubscriptionStorage(apManager, interner)

    override fun createNDF2FSubStorage(callerEp: CommonInst): NDF2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        SummaryEdgeNDFactSubStorage(callerEp, apManager)
}

private class SummaryEdgeNDFactSubStorage(
    initialStatement: CommonInst,
    val apManager: TreeApManager,
): DefaultNDF2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode>(){
    private var maxIdx = 0
    private val edgeIndex = object : AccessTreeIndex(apManager.refManager) {
        override fun getIndexedItem(idx: Int): AccessTree.AccessNode? =
            (exitApStorage[idx] as FactStorage).current()
    }

    private val initialApIndex = AccessPathInterner(initialStatement)
    private val initialAp = arrayListOf<AccessPath>()

    override fun initialApIdx(ap: InitialFactAp): Int {
        ap as AccessPath
        return initialApIndex.getOrCreateIndex(ap.base, ap.access) {
            initialAp.add(ap)
        }
    }

    override fun getInitialApByIdx(idx: Int): InitialFactAp = initialAp[idx]

    override fun createBuilder() = FactNDEdgeSubBuilder(apManager)

    private inner class FactStorage(
        val storageIdx: Int
    ) : Storage<AccessPath.AccessNode?, AccessTree.AccessNode> {
        private var current: AccessTree.AccessNode? = null

        fun current(): AccessTree.AccessNode? = current

        override fun add(element: AccessTree.AccessNode): AccessTree.AccessNode? {
            val cur = current
            if (cur == null) {
                current = element
                updateIndex(element, storageIdx)
                return element
            }

            val (mergedExitAp, delta) = cur.mergeAddDelta(element)
            if (delta == null) return null

            current = mergedExitAp
            updateIndex(delta, storageIdx)

            return delta
        }

        override fun collect(dst: MutableList<AccessTree.AccessNode>) {
            current?.let { dst.add(it) }
        }

        override fun collect(dst: MutableList<AccessTree.AccessNode>, summaryInitialFact: AccessPath.AccessNode?) {
            val filteredExitAp = current?.filterStartsWith(summaryInitialFact) ?: return
            dst.add(filteredExitAp)
        }

        private fun updateIndex(final: AccessTree.AccessNode, idx: Int) {
            edgeIndex.add(final, idx)
        }
    }

    override fun createStorage(idx: Int): Storage<AccessPath.AccessNode?, AccessTree.AccessNode> {
        maxIdx = maxOf(maxIdx, idx)
        return FactStorage(idx)
    }

    override fun relevantStorageIndices(summaryInitialFact: AccessPath.AccessNode?): BitSet {
        if (summaryInitialFact == null) {
            return BitSet().also { it.set(0, maxIdx + 1) }
        }

        return edgeIndex.findStartsWith(summaryInitialFact)
            ?: emptyPersistentBitSet()
    }
}

private class SummaryEdgeFactAbstractTreeSubscriptionStorage(
    val apManager: TreeApManager,
    private val interner: AccessTreeSoftInterner,
) : CommonAPSub.F2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> {
    private val initialApIndex = object2IntMap<AccessPath>()
    private val storageInitialFacts = arrayListOf<AccessPath>()
    private val storageFinalFacts = arrayListOf<AccessTree.AccessNode>()

    private val edgeIndex = object : AccessTreeIndex(apManager.refManager) {
        override fun getIndexedItem(idx: Int): AccessTree.AccessNode = storageFinalFacts[idx]
    }

    override fun add(
        callerInitialAp: InitialFactAp,
        callerExitAp: AccessTree.AccessNode,
    ): CommonFactEdgeSubBuilder<AccessTree.AccessNode>? {
        callerInitialAp as AccessPath

        apManager.cancellation.checkpoint()

        val currentIndex = initialApIndex.getOrCreateIndex(callerInitialAp) { newIndex ->
            storageInitialFacts.add(callerInitialAp)
            storageFinalFacts.add(callerExitAp)

            updateIndex(callerExitAp, newIndex)

            return FactEdgeSubBuilder(apManager)
                .setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp)
                .setCallerExclusion(callerInitialAp.exclusions)
        }

        val current = storageFinalFacts[currentIndex]
        val (mergedExitAp, delta) = current.mergeAddDelta(callerExitAp)
        if (delta == null) return null

        storageInitialFacts[currentIndex] = callerInitialAp
        storageFinalFacts[currentIndex] = interner.intern(mergedExitAp)

        updateIndex(delta, currentIndex)

        return FactEdgeSubBuilder(apManager)
            .setCallerNode(delta)
            .setCallerInitialAp(callerInitialAp)
            .setCallerExclusion(callerInitialAp.exclusions)
    }

    private fun updateIndex(final: AccessTree.AccessNode, idx: Int) {
        edgeIndex.add(final, idx)
    }

    override fun find(
        dst: MutableList<CommonFactEdgeSubBuilder<AccessTree.AccessNode>>,
        summaryInitialFact: AccessPath.AccessNode?,
        emptyDeltaRequired: Boolean
    ) {
        if (summaryInitialFact == null) {
            storageInitialFacts.forEachIndexed { index, callerInitialAp ->
                dst.add(storageFinalFacts[index], callerInitialAp)
            }
        } else {
            val relevantIndices = edgeIndex.findStartsWith(summaryInitialFact)
            relevantIndices?.forEach { storageIdx ->
                val callerExitAp = storageFinalFacts[storageIdx]

                val filteredExitAp = callerExitAp.filterStartsWith(summaryInitialFact)
                    ?: return@forEach

                dst.add(filteredExitAp, storageInitialFacts[storageIdx])
            }
        }
    }

    private fun MutableList<CommonFactEdgeSubBuilder<AccessTree.AccessNode>>.add(
        exitAp: AccessTree.AccessNode,
        initial: AccessPath,
    ) {
        this += FactEdgeSubBuilder(apManager)
            .setCallerNode(exitAp)
            .setCallerInitialAp(initial)
            .setCallerExclusion(initial.exclusions)
    }
}

private abstract class AccessTreeIndex(private val refManager: SoftReferenceManager) {
    abstract fun getIndexedItem(idx: Int): AccessTree.AccessNode?

    private var maxIdx = 0
    private var indexReference: Reference<AccessTreeIndexImpl>? = null

    fun add(rootTreeNode: AccessTree.AccessNode, idx: Int) {
        maxIdx = maxOf(maxIdx, idx)
        indexReference?.get()?.add(rootTreeNode, idx)
    }

    fun findStartsWith(path: AccessPath.AccessNode): BitSet? {
        if (maxIdx < INDEX_LIMIT) {
            return BitSet().also { it.set(0, maxIdx + 1) }
        }

        return getOrCreateIndex().findStartsWith(path)
    }

    private fun getOrCreateIndex(): AccessTreeIndexImpl {
        indexReference?.get()?.let { return it }
        val index = rebuildIndex()
        indexReference = refManager.createRef(index)
        return index
    }

    private fun rebuildIndex(): AccessTreeIndexImpl {
        val index = AccessTreeIndexImpl()
        for (i in 0..maxIdx) {
            val item = getIndexedItem(i) ?: continue
            index.add(item, i)
        }
        return index
    }

    companion object {
        private const val INDEX_LIMIT = 10
    }
}

private class AccessTreeIndexImpl {
    private class Node {
        private var children: Int2ObjectOpenHashMap<Node>? = null
        val index = BitSet()

        private fun getChildren(): Int2ObjectOpenHashMap<Node> =
            children ?: Int2ObjectOpenHashMap<Node>().also { children = it }

        fun getOrCreateChild(accessor: AccessorIdx): Node =
            getChildren().getOrCreate(accessor, ::Node)

        fun findChild(accessor: AccessorIdx): Node? = children?.get(accessor)
    }

    private val root = Node()

    fun add(rootTreeNode: AccessTree.AccessNode, idx: Int) {
        val unprocessed = mutableListOf(root to rootTreeNode)
        while (unprocessed.isNotEmpty()) {
            val (indexNode, treeNode) = unprocessed.removeLast()

            indexNode.index.set(idx)

            if (treeNode.isFinal) {
                val indexChild = indexNode.getOrCreateChild(FINAL_ACCESSOR_IDX)
                indexChild.index.set(idx)
            }

            treeNode.forEachAccessor { accessor, treeChild ->
                val indexChild = indexNode.getOrCreateChild(accessor)
                unprocessed.add(indexChild to treeChild)
            }
        }
    }

    fun findStartsWith(path: AccessPath.AccessNode): BitSet? {
        var currentNode = root
        var currentPath = path

        while (true) {
            currentNode = currentNode.findChild(currentPath.accessor) ?: return null
            currentPath = currentPath.next ?: return currentNode.index
        }
    }
}

private class SummaryEdgeFactTreeSubscriptionStorage(
    val apManager: TreeApManager,
) : CommonAPSub.Z2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> {
    private var callerPathEdgeFactAp: AccessTree.AccessNode? = null

    override fun add(callerExitAp: AccessTree.AccessNode): CommonZeroEdgeSubBuilder<AccessTree.AccessNode>? {
        apManager.cancellation.checkpoint()

        if (callerPathEdgeFactAp == null) {
            callerPathEdgeFactAp = callerExitAp
            return ZeroEdgeSubBuilder(apManager).setNode(callerExitAp)
        }

        val (mergedAccess, mergeAccessDelta) = callerPathEdgeFactAp!!.mergeAddDelta(callerExitAp)
        if (mergeAccessDelta == null) return null

        callerPathEdgeFactAp = mergedAccess

        return ZeroEdgeSubBuilder(apManager).setNode(mergeAccessDelta)
    }

    override fun find(
        dst: MutableList<CommonZeroEdgeSubBuilder<AccessTree.AccessNode>>,
        summaryInitialFact: AccessPath.AccessNode?,
    ) {
        callerPathEdgeFactAp?.filterStartsWith(summaryInitialFact)?.let {
            dst += ZeroEdgeSubBuilder(apManager).setNode(it)
        }
    }
}

private class ZeroEdgeSubBuilder(
    override val apManager: TreeApManager,
) : CommonZeroEdgeSubBuilder<AccessTree.AccessNode>(), TreeFinalApAccess

private class FactEdgeSubBuilder(
    override val apManager: TreeApManager,
) : CommonFactEdgeSubBuilder<AccessTree.AccessNode>(), TreeFinalApAccess

private class FactNDEdgeSubBuilder(
    override val apManager: TreeApManager,
) : CommonFactNDEdgeSubBuilder<AccessTree.AccessNode>(), TreeFinalApAccess
