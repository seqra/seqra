package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.SideEffectSummary.FactSideEffectSummary
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.access.FactSideEffectSummariesApStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

abstract class CommonFactSideEffectSummary<IAP, FAP: Any>(val methodEntryPoint: CommonInst):
    FactSideEffectSummariesApStorage, InitialApAccess<IAP>, FinalApAccess<FAP> {

    interface Storage<IAP, FAP : Any> {
        fun add(iap: IAP, se: Map<SideEffectKind, ExclusionSet>, added: MutableList<FactSEBuilder<IAP>>)
        fun collectSummariesTo(dst: MutableList<FactSEBuilder<IAP>>, initialFactPattern: FAP?)
    }

    abstract fun createStorage(): Storage<IAP, FAP>

    private val storage = MethodTaintedSideEffectSummaries()

    override fun add(sideEffects: List<FactSideEffectSummary>, added: MutableList<FactSideEffectSummary>) {
        storage.add(sideEffects, added)
    }

    override fun filterTaintedTo(dst: MutableList<FactSideEffectSummary>, pattern: FinalFactAp?) {
        storage.filterSummariesTo(dst, pattern)
    }

    private inner class MethodTaintedSideEffectSummaries : SummaryFactStorage<Storage<IAP, FAP>>(methodEntryPoint) {
        override fun createStorage() = this@CommonFactSideEffectSummary.createStorage()

        fun add(sideEffects: List<FactSideEffectSummary>, added: MutableList<FactSideEffectSummary>) {
            val sameInitialBaseEdges = sideEffects.groupBy { it.initialFactAp.base }
            for ((initialBase, sameBaseEdges) in sameInitialBaseEdges) {
                val ses = sameBaseEdges.groupBy(
                    { getInitialAccess(it.initialFactAp) },
                    { Pair(it.kind, it.initialFactAp.exclusions) }
                )

                val baseStorage = getOrCreate(initialBase)
                for ((iap, se) in ses) {
                    val sameKindSe = se.groupBy({ it.first }, { it.second })
                        .mapValues { (_, exclusions) -> exclusions.reduce(ExclusionSet::union) }

                    collectToListWithPostProcess(
                        added,
                        { baseStorage.add(iap, sameKindSe, it) },
                        { it.setInitialFactBase(initialBase).build() }
                    )
                }
            }
        }

        fun filterSummariesTo(dst: MutableList<FactSideEffectSummary>, pattern: FinalFactAp?) {
            val patternBase = pattern?.base
            if (patternBase != null) {
                val storage = find(patternBase) ?: return
                collectTo(dst, storage, patternBase, getFinalAccess(pattern))
            } else {
                forEachValue { base, storage ->
                    collectTo(dst, storage, base, pattern?.let { getFinalAccess(it) })
                }
            }
        }

        private fun collectTo(
            dst: MutableList<FactSideEffectSummary>,
            storage: Storage<IAP, FAP>,
            initialFactBase: AccessPathBase,
            containsPattern: FAP?
        ) {
            collectToListWithPostProcess(dst, {
                storage.collectSummariesTo(it, containsPattern)
            }, {
                it.setInitialFactBase(initialFactBase).build()
            })
        }
    }

    abstract class SideEffectExclusionMergingStorage<IAP> {
        private val sideEffects = ConcurrentHashMap<SideEffectKind, ExclusionSet>()

        abstract fun createBuilder(): FactSEBuilder<IAP>

        fun add(kind: SideEffectKind, exclusions: ExclusionSet): FactSEBuilder<IAP>? {
            val currentExclusion = sideEffects.putIfAbsent(kind, exclusions)
            if (currentExclusion == null) {
                return toBuilder(kind, exclusions)
            }

            val mergedExclusion = currentExclusion.union(exclusions)
            if (currentExclusion === mergedExclusion) return null

            sideEffects[kind] = mergedExclusion
            return toBuilder(kind, mergedExclusion)
        }

        fun summaries(): List<FactSEBuilder<IAP>> =
            sideEffects.map { (kind, exclusions) ->
                toBuilder(kind, exclusions)
            }

        private fun toBuilder(kind: SideEffectKind, exclusions: ExclusionSet) =
            createBuilder()
                .setKind(kind)
                .setExclusion(exclusions)
    }

    abstract class FactSEBuilder<IAP>(
        private var initialBase: AccessPathBase? = null,
        private var initialAp: IAP? = null,
        private var exclusion: ExclusionSet? = null,
        private var kind: SideEffectKind? = null,
    ): InitialApAccess<IAP> {
        abstract fun nonNullIAP(iap: IAP?): IAP

        fun build(): FactSideEffectSummary =
            FactSideEffectSummary(createInitial(initialBase!!, nonNullIAP(initialAp), exclusion!!), kind!!)

        fun setInitialFactBase(base: AccessPathBase) = this.also { initialBase = base }
        fun setExclusion(exclusion: ExclusionSet) = this.also { this.exclusion = exclusion }
        fun setKind(kind: SideEffectKind) = this.also { this.kind = kind }
        fun setInitialAp(ap: IAP) = this.also { initialAp = ap }
    }
}
