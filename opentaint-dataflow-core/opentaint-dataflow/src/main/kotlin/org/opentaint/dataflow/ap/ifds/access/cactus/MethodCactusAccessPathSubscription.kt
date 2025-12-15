package org.opentaint.dataflow.ap.ifds.access.cactus

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonAPSub
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactNDEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonZeroEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSubStorageWithAp
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class MethodCactusAccessPathSubscription :
    CommonAPSub<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(),
    CactusInitialApAccess, CactusFinalApAccess {
    override fun createZ2FSubStorage(callerEp: CommonInst): Z2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        SummaryEdgeFactTreeSubscriptionStorage()

    override fun createF2FSubStorage(callerEp: CommonInst): F2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        SummaryEdgeFactAbstractTreeSubscriptionStorage()

    override fun createNDF2FSubStorage(callerEp: CommonInst): NDF2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        NDSubStorage(callerEp)
}

private class SummaryEdgeFactAbstractTreeSubscriptionStorage: CommonAPSub.F2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
    private val storage = Object2ObjectOpenHashMap<AccessPathWithCycles, AccessCactus.AccessNode>()

    override fun add(
        callerInitialAp: InitialFactAp,
        callerExitAp: AccessCactus.AccessNode
    ): CommonFactEdgeSubBuilder<AccessCactus.AccessNode>? {
        callerInitialAp as AccessPathWithCycles

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
    override fun find(
        dst: MutableList<CommonFactEdgeSubBuilder<AccessCactus.AccessNode>>,
        summaryInitialFact: AccessPathWithCycles.AccessNode?,
        emptyDeltaRequired: Boolean
    ) {
        storage.mapTo(dst) { (callerInitialAp, callerExitAp) ->
            FactEdgeSubBuilder()
                .setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp)
                .setCallerExclusion(callerInitialAp.exclusions)
        }
    }
}

private class SummaryEdgeFactTreeSubscriptionStorage: CommonAPSub.Z2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
    private var callerPathEdgeFactAp: AccessCactus.AccessNode? = null

    override fun add(callerExitAp: AccessCactus.AccessNode): CommonZeroEdgeSubBuilder<AccessCactus.AccessNode>? {
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
        dst: MutableList<CommonZeroEdgeSubBuilder<AccessCactus.AccessNode>>,
        summaryInitialFact: AccessPathWithCycles.AccessNode?
    ) {
        callerPathEdgeFactAp?.filterStartsWith(summaryInitialFact)?.let {
            dst += ZeroEdgeSubBuilder().setNode(it)
        }
    }
}

private class NDSubStorage(callerEp: CommonInst) :
    DefaultNDF2FSubStorageWithAp<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(callerEp),
    CactusInitialApAccess {
    override fun createBuilder(): CommonFactNDEdgeSubBuilder<AccessCactus.AccessNode> = FactNDEdgeSubBuilder()

    private var maxIdx = 0
    override fun createStorage(idx: Int): Storage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
        maxIdx = maxOf(maxIdx, idx)
        return FactStorage()
    }

    private inner class FactStorage : Storage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
        private var current: AccessCactus.AccessNode? = null

        override fun add(element: AccessCactus.AccessNode): AccessCactus.AccessNode? {
            val cur = current
            if (cur == null) {
                current = element
                return element
            }

            val (mergedExitAp, delta) = cur.mergeAddDelta(element)
            if (delta == null) return null

            current = mergedExitAp
            return delta
        }

        override fun collect(dst: MutableList<AccessCactus.AccessNode>) {
            current?.let { dst.add(it) }
        }

        override fun collect(dst: MutableList<AccessCactus.AccessNode>, summaryInitialFact: AccessPathWithCycles.AccessNode?) {
            val filteredExitAp = current?.filterStartsWith(summaryInitialFact) ?: return
            dst.add(filteredExitAp)
        }
    }

    override fun relevantStorageIndices(summaryInitialFact: AccessPathWithCycles.AccessNode?): BitSet {
        return BitSet().also { it.set(0, maxIdx + 1) }
    }
}

private class ZeroEdgeSubBuilder : CommonZeroEdgeSubBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
private class FactEdgeSubBuilder : CommonFactEdgeSubBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
private class FactNDEdgeSubBuilder : CommonFactNDEdgeSubBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
