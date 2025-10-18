package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.forEach
import java.util.BitSet

class MethodTreeAccessPathSubscription: MethodAccessPathSubscription {
    private val initialBaseSubscription =
        Object2ObjectOpenHashMap<AccessPathBase, FactEdgeSubscriptionStorage>()
    private val initialBaseTreeSubscription =
        Object2ObjectOpenHashMap<AccessPathBase, ZeroEdgeSubscriptionStorage>()

    override fun addZeroToFact(
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): ZeroEdgeSummarySubscription? =
        initialBaseTreeSubscription.getOrPut(calleeInitialFactBase) {
            ZeroEdgeSubscriptionStorage()
        }.addZeroToFact(callerFactAp as AccessTree)
            ?.build()
            ?.setCalleeBase(calleeInitialFactBase)

    override fun addFactToFact(
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription? = initialBaseSubscription.getOrPut(calleeInitialBase) {
        FactEdgeSubscriptionStorage()
    }.add(callerInitialAp as AccessPath, callerExitAp as AccessTree)
        ?.build()
        ?.setCalleeBase(calleeInitialBase)

    override fun collectFactEdge(
        collection: MutableList<FactEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp
    ) {
        val subscription = initialBaseSubscription[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { subscription.findFactEdge(it, (summaryInitialFactAp as AccessPath).access) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    override fun collectZeroEdge(
        collection: MutableList<ZeroEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp
    ) {
        val subscription = initialBaseTreeSubscription[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { subscription.findZeroEdge(it, (summaryInitialFactAp as AccessPath).access) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }
}


private class FactEdgeSubscriptionStorage {
    private var subscriptions = persistentHashMapOf<AccessPathBase, SummaryEdgeFactAbstractTreeSubscriptionStorage>()

    fun add(
        callerInitialAp: AccessPath,
        callerExitAp: AccessTree
    ): FactEdgeSubBuilder? {
        val storage = subscriptions[callerExitAp.base]
            ?: SummaryEdgeFactAbstractTreeSubscriptionStorage().also {
                subscriptions = subscriptions.put(callerExitAp.base, it)
            }

        return storage.add(callerInitialAp, callerExitAp.access, callerExitAp.exclusions)
            ?.setCallerBase(callerExitAp.base)
    }

    fun findFactEdge(
        collection: MutableList<FactEdgeSubBuilder>,
        summaryInitialFact: AccessPath.AccessNode?
    ) {
        subscriptions.forEach { (base, storage) ->
            collectToListWithPostProcess(
                collection,
                { storage.find(it, summaryInitialFact) },
                { it.setCallerBase(base) },
            )
        }
    }
}

private class SummaryEdgeFactAbstractTreeSubscriptionStorage {
    private val edgesStartingWithAccessor = Object2ObjectOpenHashMap<Accessor, BitSet>()

    private val initialApIndex = Object2IntOpenHashMap<AccessPath>()
        .also { it.defaultReturnValue(NO_VALUE) }

    private val storage = arrayListOf<Pair<AccessPath, AccessTree.AccessNode>>()

    fun add(
        callerInitialAp: AccessPath,
        callerExitAp: AccessTree.AccessNode,
        exclusion: ExclusionSet
    ): FactEdgeSubBuilder? {
        check(exclusion == callerInitialAp.exclusions) { "Edge invariant" }

        val currentIndex = initialApIndex.getInt(callerInitialAp)
        if (currentIndex == NO_VALUE) {
            val newIndex = storage.size
            initialApIndex.put(callerInitialAp, newIndex)
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

        updateIndex(mergedExitAp, currentIndex)

        return FactEdgeSubBuilder()
            .setCallerNode(delta)
            .setCallerInitialAp(callerInitialAp)
            .setCallerExclusion(callerInitialAp.exclusions)
    }

    private fun updateIndex(final: AccessTree.AccessNode, idx: Int) {
        if (final.isFinal) {
            edgesStartingWithAccessor.getOrPut(FinalAccessor, ::BitSet).set(idx)
        }

        final.accessors?.forEach {
            edgesStartingWithAccessor.getOrPut(it, ::BitSet).set(idx)
        }
    }

    fun find(
        collection: MutableList<FactEdgeSubBuilder>,
        summaryInitialFact: AccessPath.AccessNode?
    ) {
        if (summaryInitialFact == null) {
            storage.forEach { (callerInitialAp, callerExitAp) ->
                collection.add(callerExitAp, callerInitialAp)
            }
        } else {
            val relevantIndices = edgesStartingWithAccessor[summaryInitialFact.accessor]
            relevantIndices?.forEach { storageIdx ->
                val (callerInitialAp, callerExitAp) = storage[storageIdx]

                val filteredExitAp = callerExitAp.filterStartsWith(summaryInitialFact)
                    ?: return@forEach

                collection.add(filteredExitAp, callerInitialAp)
            }
        }
    }

    private fun MutableList<FactEdgeSubBuilder>.add(exitAp: AccessTree.AccessNode, initial: AccessPath) {
        this += FactEdgeSubBuilder()
            .setCallerNode(exitAp)
            .setCallerInitialAp(initial)
            .setCallerExclusion(initial.exclusions)
    }

    companion object {
        private const val NO_VALUE = -1
    }
}

private class ZeroEdgeSubscriptionStorage {
    private var subscriptions = persistentHashMapOf<AccessPathBase, SummaryEdgeFactTreeSubscriptionStorage>()

    fun addZeroToFact(callerFactAp: AccessTree): ZeroEdgeSubBuilder? {
        val storage = subscriptions[callerFactAp.base]
            ?: SummaryEdgeFactTreeSubscriptionStorage().also {
                subscriptions = subscriptions.put(callerFactAp.base, it)
            }

        return storage.add(callerFactAp.access)?.setBase(callerFactAp.base)
    }

    fun findZeroEdge(
        collection: MutableList<ZeroEdgeSubBuilder>,
        summaryInitialFact: AccessPath.AccessNode?
    ) {
        for ((base, storage) in subscriptions) {
            val sub = storage.findForSummaryFact(summaryInitialFact) ?: continue
            collection += sub.setBase(base)
        }
    }
}

private class SummaryEdgeFactTreeSubscriptionStorage {
    private var callerPathEdgeFactAp: AccessTree.AccessNode? = null

    fun findForSummaryFact(summaryFactAp: AccessPath.AccessNode?): ZeroEdgeSubBuilder? =
        callerPathEdgeFactAp?.filterStartsWith(summaryFactAp)?.let {
            ZeroEdgeSubBuilder().setNode(it)
        }

    fun add(otherCallerPathEdgeFactAp: AccessTree.AccessNode): ZeroEdgeSubBuilder? {
        if (callerPathEdgeFactAp == null) {
            callerPathEdgeFactAp = otherCallerPathEdgeFactAp
            return ZeroEdgeSubBuilder().setNode(otherCallerPathEdgeFactAp)
        }

        val (mergedAccess, mergeAccessDelta) = callerPathEdgeFactAp!!.mergeAddDelta(otherCallerPathEdgeFactAp)
        if (mergeAccessDelta == null) return null

        callerPathEdgeFactAp = mergedAccess

        return ZeroEdgeSubBuilder().setNode(mergeAccessDelta)
    }
}

private data class ZeroEdgeSubBuilder(
    private var base: AccessPathBase? = null,
    private var node: AccessTree.AccessNode? = null,
) {
    fun build(): ZeroEdgeSummarySubscription = ZeroEdgeSummarySubscription()
        .setCallerPathEdgeAp(AccessTree(base!!, node!!, ExclusionSet.Universe))

    fun setBase(base: AccessPathBase) = this.also {
        this.base = base
    }

    fun setNode(node: AccessTree.AccessNode) = this.also {
        this.node = node
    }
}

private data class FactEdgeSubBuilder(
    private var callerInitialAp: AccessPath? = null,
    private var callerBase: AccessPathBase? = null,
    private var callerNode: AccessTree.AccessNode? = null,
    private var callerExclusion: ExclusionSet? = null,
) {
    fun build(): FactEdgeSummarySubscription = FactEdgeSummarySubscription()
        .setCallerAp(AccessTree(callerBase!!, callerNode!!, callerExclusion!!))
        .setCallerInitialAp(callerInitialAp!!)

    fun setCallerInitialAp(callerInitialAp: AccessPath) = this.also {
        this.callerInitialAp = callerInitialAp
    }

    fun setCallerBase(callerBase: AccessPathBase) = this.also {
        this.callerBase = callerBase
    }

    fun setCallerNode(callerNode: AccessTree.AccessNode) = this.also {
        this.callerNode = callerNode
    }

    fun setCallerExclusion(exclusion: ExclusionSet) = this.also {
        this.callerExclusion = exclusion
    }
}
