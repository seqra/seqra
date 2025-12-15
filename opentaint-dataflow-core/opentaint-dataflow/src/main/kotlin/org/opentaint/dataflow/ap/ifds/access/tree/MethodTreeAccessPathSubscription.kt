package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonAPSub
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactNDEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonZeroEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSubStorage
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class MethodTreeAccessPathSubscription :
    CommonAPSub<AccessPath.AccessNode?, AccessTree.AccessNode>(),
    TreeInitialApAccess, TreeFinalApAccess {
    override fun createZ2FSubStorage(callerEp: CommonInst): Z2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        SummaryEdgeFactTreeSubscriptionStorage()

    override fun createF2FSubStorage(callerEp: CommonInst): F2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        SummaryEdgeFactAbstractTreeSubscriptionStorage()

    override fun createNDF2FSubStorage(callerEp: CommonInst): NDF2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        SummaryEdgeNDFactSubStorage(callerEp)
}

private class SummaryEdgeNDFactSubStorage(
    initialStatement: CommonInst
): DefaultNDF2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode>(){
    private var maxIdx = 0
    private val edgeIndex = AccessTreeIndex()

    private val initialApIndex = AccessPathInterner(initialStatement)
    private val initialAp = arrayListOf<AccessPath>()

    override fun initialApIdx(ap: InitialFactAp): Int {
        ap as AccessPath
        return initialApIndex.getOrCreateIndex(ap.base, ap.access) {
            initialAp.add(ap)
        }
    }

    override fun getInitialApByIdx(idx: Int): InitialFactAp = initialAp[idx]

    override fun createBuilder() = FactNDEdgeSubBuilder()

    private inner class FactStorage(
        val storageIdx: Int
    ) : Storage<AccessPath.AccessNode?, AccessTree.AccessNode> {
        private var current: AccessTree.AccessNode? = null

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

private class SummaryEdgeFactAbstractTreeSubscriptionStorage :
    CommonAPSub.F2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> {
    private val edgeIndex = AccessTreeIndex()

    private val initialApIndex = object2IntMap<AccessPath>()
    private val storage = arrayListOf<Pair<AccessPath, AccessTree.AccessNode>>()

    override fun add(
        callerInitialAp: InitialFactAp,
        callerExitAp: AccessTree.AccessNode,
    ): CommonFactEdgeSubBuilder<AccessTree.AccessNode>? {
        callerInitialAp as AccessPath

        val currentIndex = initialApIndex.getOrCreateIndex(callerInitialAp) { newIndex ->
            storage.add(callerInitialAp to callerExitAp)

            updateIndex(callerExitAp, newIndex)

            return FactEdgeSubBuilder()
                .setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp)
                .setCallerExclusion(callerInitialAp.exclusions)
        }

        val (_, current) = storage[currentIndex]
        val (mergedExitAp, delta) = current.mergeAddDelta(callerExitAp)
        if (delta == null) return null

        storage[currentIndex] = callerInitialAp to mergedExitAp

        updateIndex(delta, currentIndex)

        return FactEdgeSubBuilder()
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
            storage.forEach { (callerInitialAp, callerExitAp) ->
                dst.add(callerExitAp, callerInitialAp)
            }
        } else {
            val relevantIndices = edgeIndex.findStartsWith(summaryInitialFact)
            relevantIndices?.forEach { storageIdx ->
                val (callerInitialAp, callerExitAp) = storage[storageIdx]

                val filteredExitAp = callerExitAp.filterStartsWith(summaryInitialFact)
                    ?: error("Tree index mismatch")

                dst.add(filteredExitAp, callerInitialAp)
            }
        }
    }

    private fun MutableList<CommonFactEdgeSubBuilder<AccessTree.AccessNode>>.add(
        exitAp: AccessTree.AccessNode,
        initial: AccessPath,
    ) {
        this += FactEdgeSubBuilder()
            .setCallerNode(exitAp)
            .setCallerInitialAp(initial)
            .setCallerExclusion(initial.exclusions)
    }
}

private class AccessTreeIndex {
    private class Node {
        private var children: MutableMap<Accessor, Node>? = null
        val index = BitSet()

        private fun getChildren(): MutableMap<Accessor, Node> =
            children ?: hashMapOf<Accessor, Node>().also { children = it }

        fun getOrCreateChild(accessor: Accessor): Node =
            getChildren().getOrPut(accessor, ::Node)

        fun findChild(accessor: Accessor): Node? = children?.get(accessor)
    }

    private val root = Node()

    fun add(rootTreeNode: AccessTree.AccessNode, idx: Int) {
        val unprocessed = mutableListOf(root to rootTreeNode)
        while (unprocessed.isNotEmpty()) {
            val (indexNode, treeNode) = unprocessed.removeLast()

            indexNode.index.set(idx)

            if (treeNode.isFinal) {
                val indexChild = indexNode.getOrCreateChild(FinalAccessor)
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

private class SummaryEdgeFactTreeSubscriptionStorage :
    CommonAPSub.Z2FSubStorage<AccessPath.AccessNode?, AccessTree.AccessNode> {
    private var callerPathEdgeFactAp: AccessTree.AccessNode? = null

    override fun add(callerExitAp: AccessTree.AccessNode): CommonZeroEdgeSubBuilder<AccessTree.AccessNode>? {
        if (callerPathEdgeFactAp == null) {
            callerPathEdgeFactAp = callerExitAp
            return ZeroEdgeSubBuilder().setNode(callerExitAp)
        }

        val (mergedAccess, mergeAccessDelta) = callerPathEdgeFactAp!!.mergeAddDelta(callerExitAp)
        if (mergeAccessDelta == null) return null

        callerPathEdgeFactAp = mergedAccess

        return ZeroEdgeSubBuilder().setNode(mergeAccessDelta)
    }

    override fun find(
        dst: MutableList<CommonZeroEdgeSubBuilder<AccessTree.AccessNode>>,
        summaryInitialFact: AccessPath.AccessNode?,
    ) {
        callerPathEdgeFactAp?.filterStartsWith(summaryInitialFact)?.let {
            dst += ZeroEdgeSubBuilder().setNode(it)
        }
    }
}

private class ZeroEdgeSubBuilder : CommonZeroEdgeSubBuilder<AccessTree.AccessNode>(), TreeFinalApAccess
private class FactEdgeSubBuilder : CommonFactEdgeSubBuilder<AccessTree.AccessNode>(), TreeFinalApAccess
private class FactNDEdgeSubBuilder : CommonFactNDEdgeSubBuilder<AccessTree.AccessNode>(), TreeFinalApAccess
