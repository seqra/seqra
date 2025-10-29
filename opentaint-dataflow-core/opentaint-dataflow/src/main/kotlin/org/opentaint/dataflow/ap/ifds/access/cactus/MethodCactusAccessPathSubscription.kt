package org.opentaint.dataflow.ap.ifds.access.cactus

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.util.collectToListWithPostProcess

class MethodCactusAccessPathSubscription: MethodAccessPathSubscription {
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
        }.addZeroToFact(callerFactAp as AccessCactus)
            ?.build()
            ?.setCalleeBase(calleeInitialFactBase)

    override fun addFactToFact(
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription? = initialBaseSubscription.getOrPut(calleeInitialBase) {
        FactEdgeSubscriptionStorage()
    }.add(callerInitialAp as AccessPathWithCycles, callerExitAp as AccessCactus)
        ?.build()
        ?.setCalleeBase(calleeInitialBase)

    override fun collectFactEdge(
        collection: MutableList<FactEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
        emptyDeltaRequired: Boolean
    ) {
        val storage = initialBaseSubscription[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { storage.findFactEdge(it) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    override fun collectZeroEdge(
        collection: MutableList<ZeroEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp
    ) {
        val storage = initialBaseTreeSubscription[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { storage.findZeroEdge(it, (summaryInitialFactAp as AccessPathWithCycles).access) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }
}


private class FactEdgeSubscriptionStorage {
    private var subscriptions = persistentHashMapOf<AccessPathBase, SummaryEdgeFactAbstractTreeSubscriptionStorage>()

    fun add(
        callerInitialAp: AccessPathWithCycles,
        callerExitAp: AccessCactus
    ): FactEdgeSubBuilder? {
        val storage = subscriptions[callerExitAp.base]
            ?: SummaryEdgeFactAbstractTreeSubscriptionStorage().also {
                subscriptions = subscriptions.put(callerExitAp.base, it)
            }

        return storage.add(callerInitialAp, callerExitAp.access, callerExitAp.exclusions)
            ?.setCallerBase(callerExitAp.base)
    }

    fun findFactEdge(collection: MutableList<FactEdgeSubBuilder>) {
        for ((base, storage) in subscriptions) {
            collectToListWithPostProcess(
                collection,
                { storage.find(it) },
                { it.setCallerBase(base) }
            )
        }
    }
}

private class SummaryEdgeFactAbstractTreeSubscriptionStorage {
    private val storage = Object2ObjectOpenHashMap<AccessPathWithCycles, AccessCactus.AccessNode>()

    fun add(
        callerInitialAp: AccessPathWithCycles,
        callerExitAp: AccessCactus.AccessNode,
        exclusion: ExclusionSet
    ): FactEdgeSubBuilder? {
        check(exclusion == callerInitialAp.exclusions) { "Edge invariant" }

        val current = storage[callerInitialAp]
        if (current == null) {
            storage[callerInitialAp] = callerExitAp
            return FactEdgeSubBuilder()
                .setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp)
                .setCallerExclusion(callerInitialAp.exclusions)
        }

        val (mergedExitAp, delta) = current.mergeAddDelta(callerExitAp)
        if (delta == null) return null

        storage[callerInitialAp] = mergedExitAp

        return FactEdgeSubBuilder()
            .setCallerNode(delta)
            .setCallerInitialAp(callerInitialAp)
            .setCallerExclusion(callerInitialAp.exclusions)
    }

    // todo: filter
    fun find(collection: MutableList<FactEdgeSubBuilder>) {
        storage.mapTo(collection) { (callerInitialAp, callerExitAp) ->
            FactEdgeSubBuilder()
                .setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp)
                .setCallerExclusion(callerInitialAp.exclusions)
        }
    }
}

private class ZeroEdgeSubscriptionStorage {
    private var subscriptions = persistentHashMapOf<AccessPathBase, SummaryEdgeFactTreeSubscriptionStorage>()

    fun addZeroToFact(callerFactAp: AccessCactus): ZeroEdgeSubBuilder? {
        val storage = subscriptions[callerFactAp.base]
            ?: SummaryEdgeFactTreeSubscriptionStorage().also {
                subscriptions = subscriptions.put(callerFactAp.base, it)
            }

        return storage.add(callerFactAp.access)?.setBase(callerFactAp.base)
    }

    fun findZeroEdge(
        collection: MutableList<ZeroEdgeSubBuilder>,
        summaryInitialFact: AccessPathWithCycles.AccessNode?
    ) {
        subscriptions.mapNotNullTo(collection) { (base, storage) ->
            storage.findForSummaryFact(summaryInitialFact)?.setBase(base)
        }
    }
}

private class SummaryEdgeFactTreeSubscriptionStorage {
    private var callerPathEdgeFactAp: AccessCactus.AccessNode? = null

    fun findForSummaryFact(summaryFactAp: AccessPathWithCycles.AccessNode?): ZeroEdgeSubBuilder? =
        callerPathEdgeFactAp?.filterStartsWith(summaryFactAp)?.let {
            ZeroEdgeSubBuilder().setNode(it)
        }

    fun add(otherCallerPathEdgeFactAp: AccessCactus.AccessNode): ZeroEdgeSubBuilder? {
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
    private var node: AccessCactus.AccessNode? = null,
) {
    fun build(): ZeroEdgeSummarySubscription = ZeroEdgeSummarySubscription()
        .setCallerPathEdgeAp(AccessCactus(base!!, node!!, ExclusionSet.Universe))

    fun setBase(base: AccessPathBase) = this.also {
        this.base = base
    }

    fun setNode(node: AccessCactus.AccessNode) = this.also {
        this.node = node
    }
}

private data class FactEdgeSubBuilder(
    private var callerInitialAp: AccessPathWithCycles? = null,
    private var callerBase: AccessPathBase? = null,
    private var callerNode: AccessCactus.AccessNode? = null,
    private var callerExclusion: ExclusionSet? = null,
) {
    fun build(): FactEdgeSummarySubscription = FactEdgeSummarySubscription()
        .setCallerAp(AccessCactus(callerBase!!, callerNode!!, callerExclusion!!))
        .setCallerInitialAp(callerInitialAp!!)

    fun setCallerInitialAp(callerInitialAp: AccessPathWithCycles) = this.also {
        this.callerInitialAp = callerInitialAp
    }

    fun setCallerBase(callerBase: AccessPathBase) = this.also {
        this.callerBase = callerBase
    }

    fun setCallerNode(callerNode: AccessCactus.AccessNode) = this.also {
        this.callerNode = callerNode
    }

    fun setCallerExclusion(exclusion: ExclusionSet) = this.also {
        this.callerExclusion = exclusion
    }
}
