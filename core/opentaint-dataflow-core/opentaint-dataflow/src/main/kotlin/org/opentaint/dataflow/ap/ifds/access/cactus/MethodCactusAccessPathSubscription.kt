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

class MethodCactusAccessPathSubscription(
    override val cactusManager: CactusApManager,
) : CommonAPSub<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(),
    CactusInitialApAccess, CactusFinalApAccess {
    override fun createZ2FSubStorage(callerEp: CommonInst): Z2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        SummaryEdgeFactTreeSubscriptionStorage(cactusManager)

    override fun createF2FSubStorage(callerEp: CommonInst): F2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        SummaryEdgeFactAbstractTreeSubscriptionStorage(cactusManager)

    override fun createNDF2FSubStorage(callerEp: CommonInst): NDF2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> =
        NDSubStorage(cactusManager, callerEp)
}

private class SummaryEdgeFactAbstractTreeSubscriptionStorage(
    private val cactusManager: CactusApManager,
) : CommonAPSub.F2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
    private val storage = Object2ObjectOpenHashMap<AccessPathWithCycles, AccessCactus.AccessNode>()

    override fun add(callerInitialAp: InitialFactAp, callerExitAp: AccessCactus.AccessNode): CommonFactEdgeSubBuilder<AccessCactus.AccessNode>? {
        callerInitialAp as AccessPathWithCycles
        val current = storage[callerInitialAp]
        if (current == null) {
            storage[callerInitialAp] = callerExitAp
            return FactEdgeSubBuilder(cactusManager).setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp).setCallerExclusion(callerInitialAp.exclusions)
        }
        val (mergedExitAp, delta) = current.mergeAddDelta(callerExitAp)
        if (delta == null) return null
        storage[callerInitialAp] = mergedExitAp
        return FactEdgeSubBuilder(cactusManager).setCallerNode(delta)
            .setCallerInitialAp(callerInitialAp).setCallerExclusion(callerInitialAp.exclusions)
    }

    // todo: filter
    override fun find(dst: MutableList<CommonFactEdgeSubBuilder<AccessCactus.AccessNode>>, summaryInitialFact: AccessPathWithCycles.AccessNode?, emptyDeltaRequired: Boolean) {
        storage.mapTo(dst) { (callerInitialAp, callerExitAp) ->
            FactEdgeSubBuilder(cactusManager).setCallerNode(callerExitAp)
                .setCallerInitialAp(callerInitialAp).setCallerExclusion(callerInitialAp.exclusions)
        }
    }
}

private class SummaryEdgeFactTreeSubscriptionStorage(
    private val cactusManager: CactusApManager,
) : CommonAPSub.Z2FSubStorage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
    private var callerPathEdgeFactAp: AccessCactus.AccessNode? = null
    override fun add(callerExitAp: AccessCactus.AccessNode): CommonZeroEdgeSubBuilder<AccessCactus.AccessNode>? {
        if (callerPathEdgeFactAp == null) {
            callerPathEdgeFactAp = callerExitAp
            return ZeroEdgeSubBuilder(cactusManager).setNode(callerExitAp)
        }
        val (mergedAccess, delta) = callerPathEdgeFactAp!!.mergeAddDelta(callerExitAp)
        if (delta == null) return null
        callerPathEdgeFactAp = mergedAccess
        return ZeroEdgeSubBuilder(cactusManager).setNode(delta)
    }
    override fun find(dst: MutableList<CommonZeroEdgeSubBuilder<AccessCactus.AccessNode>>, summaryInitialFact: AccessPathWithCycles.AccessNode?) {
        callerPathEdgeFactAp?.filterStartsWith(summaryInitialFact)?.let {
            dst += ZeroEdgeSubBuilder(cactusManager).setNode(it)
        }
    }
}

private class NDSubStorage(private val cactusManager: CactusApManager, callerEp: CommonInst) :
    DefaultNDF2FSubStorageWithAp<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode>(callerEp), CactusInitialApAccess {
    override fun createBuilder(): CommonFactNDEdgeSubBuilder<AccessCactus.AccessNode> = FactNDEdgeSubBuilder(cactusManager)
    private var maxIdx = 0
    override fun createStorage(idx: Int): Storage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
        maxIdx = maxOf(maxIdx, idx)
        return FactStorage()
    }
    private inner class FactStorage : Storage<AccessPathWithCycles.AccessNode?, AccessCactus.AccessNode> {
        private var current: AccessCactus.AccessNode? = null
        override fun add(element: AccessCactus.AccessNode): AccessCactus.AccessNode? {
            val cur = current
            if (cur == null) { current = element; return element }
            val (merged, delta) = cur.mergeAddDelta(element)
            if (delta == null) return null
            current = merged
            return delta
        }
        override fun collect(dst: MutableList<AccessCactus.AccessNode>) { current?.let { dst.add(it) } }
        override fun collect(dst: MutableList<AccessCactus.AccessNode>, summaryInitialFact: AccessPathWithCycles.AccessNode?) {
            current?.filterStartsWith(summaryInitialFact)?.let { dst.add(it) }
        }
    }
    override fun forEachRelevantStorageIndex(summaryInitialFact: AccessPathWithCycles.AccessNode?, body: (Int) -> Unit) {
        for (idx in 0..maxIdx) body(idx)
    }
}

private class ZeroEdgeSubBuilder(override val cactusManager: CactusApManager) : CommonZeroEdgeSubBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
private class FactEdgeSubBuilder(override val cactusManager: CactusApManager) : CommonFactEdgeSubBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
private class FactNDEdgeSubBuilder(override val cactusManager: CactusApManager) : CommonFactNDEdgeSubBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
