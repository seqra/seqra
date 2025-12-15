package org.opentaint.dataflow.ap.ifds.access.common

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactNDEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class CommonAPSub<IAP, FAP : Any> : MethodAccessPathSubscription, InitialApAccess<IAP>, FinalApAccess<FAP> {
    private val initialBaseZ2FSub = Object2ObjectOpenHashMap<AccessPathBase, Z2FSubs>()
    private val initialBaseF2FSub = Object2ObjectOpenHashMap<AccessPathBase, F2FSubs>()
    private val initialBaseNDF2FSub = Object2ObjectOpenHashMap<AccessPathBase, NDF2FSubs>()

    override fun addZeroToFact(
        callerEp: CommonInst,
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): ZeroEdgeSummarySubscription? =
        initialBaseZ2FSub.getOrPut(calleeInitialFactBase) {
            Z2FSubs()
        }.addZeroToFact(callerEp, callerFactAp)
            ?.build()
            ?.setCalleeBase(calleeInitialFactBase)

    override fun addFactToFact(
        callerEp: CommonInst,
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription? = initialBaseF2FSub.getOrPut(calleeInitialBase) {
        F2FSubs()
    }.add(callerEp, callerInitialAp, callerExitAp)
        ?.build()
        ?.setCalleeBase(calleeInitialBase)

    override fun addNDFactToFact(
        callerEp: CommonInst,
        calleeInitialBase: AccessPathBase,
        callerInitial: Set<InitialFactAp>,
        callerExitAp: FinalFactAp,
    ): FactNDEdgeSummarySubscription? = initialBaseNDF2FSub.getOrPut(calleeInitialBase) {
        NDF2FSubs()
    }.add(callerEp, callerInitial, callerExitAp)
        ?.build()
        ?.setCalleeBase(calleeInitialBase)

    override fun collectFactEdge(
        collection: MutableList<FactEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
        emptyDeltaRequired: Boolean,
    ) {
        val subscription = initialBaseF2FSub[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { subscription.findFactEdge(it, getInitialAccess(summaryInitialFactAp), emptyDeltaRequired) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    override fun collectFactNDEdge(
        collection: MutableList<FactNDEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
        emptyDeltaRequired: Boolean,
    ) {
        val subscription = initialBaseNDF2FSub[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { subscription.findFactNDEdge(it, getInitialAccess(summaryInitialFactAp), emptyDeltaRequired) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    override fun collectZeroEdge(
        collection: MutableList<ZeroEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
    ) {
        val subscription = initialBaseZ2FSub[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { subscription.findZeroEdge(it, getInitialAccess(summaryInitialFactAp)) },
            { it.build().setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    interface F2FSubStorage<IAP, FAP : Any> {
        fun add(callerInitialAp: InitialFactAp, callerExitAp: FAP): CommonFactEdgeSubBuilder<FAP>?

        fun find(
            dst: MutableList<CommonFactEdgeSubBuilder<FAP>>,
            summaryInitialFact: IAP,
            emptyDeltaRequired: Boolean,
        )
    }

    abstract fun createF2FSubStorage(callerEp: CommonInst): F2FSubStorage<IAP, FAP>

    private inner class F2FSubs {
        private var subscriptions = persistentHashMapOf<AccessPathBase, F2FSubStorage<IAP, FAP>>()

        fun add(callerEp: CommonInst, callerInitialAp: InitialFactAp, callerExitAp: FinalFactAp): CommonFactEdgeSubBuilder<FAP>? {
            val storage = subscriptions[callerExitAp.base]
                ?: createF2FSubStorage(callerEp).also {
                    subscriptions = subscriptions.put(callerExitAp.base, it)
                }

            return storage.add(callerInitialAp, getFinalAccess(callerExitAp))
                ?.setCallerBase(callerExitAp.base)
        }

        fun findFactEdge(
            collection: MutableList<CommonFactEdgeSubBuilder<FAP>>,
            summaryInitialFact: IAP,
            emptyDeltaRequired: Boolean,
        ) {
            subscriptions.forEach { (base, storage) ->
                collectToListWithPostProcess(
                    collection,
                    { storage.find(it, summaryInitialFact, emptyDeltaRequired) },
                    { it.setCallerBase(base) },
                )
            }
        }
    }

    interface NDF2FSubStorage<IAP, FAP : Any> {
        fun add(callerInitial: Set<InitialFactAp>, callerExitAp: FAP): CommonFactNDEdgeSubBuilder<FAP>?

        fun find(
            dst: MutableList<CommonFactNDEdgeSubBuilder<FAP>>,
            summaryInitialFact: IAP,
            emptyDeltaRequired: Boolean,
        )
    }

    abstract fun createNDF2FSubStorage(callerEp: CommonInst): NDF2FSubStorage<IAP, FAP>

    private inner class NDF2FSubs {
        private var subscriptions = persistentHashMapOf<AccessPathBase, NDF2FSubStorage<IAP, FAP>>()

        fun add(
            callerEp: CommonInst,
            callerInitial: Set<InitialFactAp>,
            callerExitAp: FinalFactAp,
        ): CommonFactNDEdgeSubBuilder<FAP>? {
            val storage = subscriptions[callerExitAp.base]
                ?: createNDF2FSubStorage(callerEp).also {
                    subscriptions = subscriptions.put(callerExitAp.base, it)
                }

            return storage.add(callerInitial, getFinalAccess(callerExitAp))
                ?.setCallerBase(callerExitAp.base)
        }

        fun findFactNDEdge(
            collection: MutableList<CommonFactNDEdgeSubBuilder<FAP>>,
            summaryInitialFact: IAP,
            emptyDeltaRequired: Boolean,
        ) {
            subscriptions.forEach { (base, storage) ->
                collectToListWithPostProcess(
                    collection,
                    { storage.find(it, summaryInitialFact, emptyDeltaRequired) },
                    { it.setCallerBase(base) },
                )
            }
        }
    }

    interface Z2FSubStorage<IAP, FAP : Any> {
        fun add(callerExitAp: FAP): CommonZeroEdgeSubBuilder<FAP>?
        fun find(dst: MutableList<CommonZeroEdgeSubBuilder<FAP>>, summaryInitialFact: IAP)
    }

    abstract fun createZ2FSubStorage(callerEp: CommonInst): Z2FSubStorage<IAP, FAP>

    private inner class Z2FSubs {
        private var subscriptions = persistentHashMapOf<AccessPathBase, Z2FSubStorage<IAP, FAP>>()

        fun addZeroToFact(callerEp: CommonInst, callerFactAp: FinalFactAp): CommonZeroEdgeSubBuilder<FAP>? {
            val storage = subscriptions[callerFactAp.base]
                ?: createZ2FSubStorage(callerEp).also {
                    subscriptions = subscriptions.put(callerFactAp.base, it)
                }

            return storage.add(getFinalAccess(callerFactAp))?.setBase(callerFactAp.base)
        }

        fun findZeroEdge(
            collection: MutableList<CommonZeroEdgeSubBuilder<FAP>>,
            summaryInitialFact: IAP,
        ) {
            for ((base, storage) in subscriptions) {
                collectToListWithPostProcess(
                    collection,
                    { storage.find(it, summaryInitialFact) },
                    { it.setBase(base) }
                )
            }
        }
    }
}
