package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.MethodSummaryFactEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.concurrentReadSafeForEach
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap
import java.util.BitSet

class MethodInitialToFinalAutomataApSummariesStorage(
    methodInitialStatement: CommonInst
) : MethodInitialToFinalApSummariesStorage {
    private val storage = ExitPointStorage(methodInitialStatement)

    override fun toString(): String = storage.toString()

    override fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        storage.add(edges, added)
    }

    override fun filterEdgesTo(
        dst: MutableList<FactToFactEdgeBuilder>,
        pattern: FinalFactAp?,
        finalFactBase: AccessPathBase?
    ) {
        storage.filterEdgesTo(dst, StorageFilterPattern(pattern, finalFactBase))
    }

    override fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>) {
        storage.collectAllEdgesTo(dst)
    }

    private class StorageFilterPattern(
        val initialFactPattern: FinalFactAp?,
        val finalFactBase: AccessPathBase?
    )

    private class ExitPointStorage(methodEntryPoint: CommonInst) :
        MethodSummaryFactEdgesForExitPoint<BasedInitialAp, StorageFilterPattern>(methodEntryPoint) {
        override fun createStorage(): BasedInitialAp = BasedInitialAp(methodEntryPoint)

        override fun storageFilterEdgesTo(
            dst: MutableList<FactToFactEdgeBuilder>,
            storage: BasedInitialAp,
            containsPattern: StorageFilterPattern
        ) {
            storage.filterTo(dst, containsPattern)
        }

        override fun storageCollectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>, storage: BasedInitialAp) {
            storage.collectAllEdgesTo(dst)
        }

        override fun storageAdd(
            storage: BasedInitialAp,
            edges: List<Edge.FactToFact>,
            added: MutableList<FactToFactEdgeBuilder>
        ) {
            storage.add(edges, added)
        }
    }

    private class BasedInitialAp(
        private val methodEntryPoint: CommonInst
    ) : SummaryFactStorage<BasedFinalAp>(methodEntryPoint) {
        override fun createStorage(): BasedFinalAp = BasedFinalAp(methodEntryPoint)

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
            val sameInitialBaseEdges = edges.groupBy { it.initialFactAp.base }
            for ((base, basedEdges) in sameInitialBaseEdges) {
                val storage = getOrCreate(base)
                val baseAdded = mutableListOf<FactToFactEdgeBuilderBuilder>()
                storage.add(basedEdges, baseAdded)
                baseAdded.mapTo(added) { it.setInitialBase(base).build() }
            }
        }

        fun filterTo(dst: MutableList<FactToFactEdgeBuilder>, containsPattern: StorageFilterPattern) {
            val initialFactPattern = containsPattern.initialFactPattern as? AccessGraphFinalFactAp
            val initialFactBase = initialFactPattern?.base
            if (initialFactBase != null) {
                val storage = find(initialFactBase) ?: return
                collectTo(dst, storage, initialFactBase, initialFactPattern.access, containsPattern.finalFactBase)
            } else {
                forEachValue { base, storage ->
                    collectTo(dst, storage, base, accessPattern = null, containsPattern.finalFactBase)
                }
            }
        }

        fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>) {
            forEachValue { base, storage ->
                collectTo(dst, storage, base, accessPattern = null, finalFactBase = null)
            }
        }

        private fun collectTo(
            dst: MutableList<FactToFactEdgeBuilder>,
            storage: BasedFinalAp,
            initialFactBase: AccessPathBase,
            accessPattern: AccessGraph?,
            finalFactBase: AccessPathBase?
        ) = collectToListWithPostProcess(dst, {
            storage.collectEdgesTo(it, accessPattern, finalFactBase)
        }, {
            it.setInitialBase(initialFactBase).build()
        })
    }

    private class BasedFinalAp(methodEntryPoint: CommonInst) :
        SummaryFactStorage<InitialToFinalApStorage>(methodEntryPoint) {
        override fun createStorage(): InitialToFinalApStorage = InitialToFinalApStorage()

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilderBuilder>) {
            val sameFinalBaseEdges = edges.groupBy { it.factAp.base }
            for ((base, basedEdges) in sameFinalBaseEdges) {
                val storage = getOrCreate(base)
                val baseAdded = mutableListOf<FactToFactEdgeBuilderBuilder>()
                storage.add(basedEdges, baseAdded)
                baseAdded.mapTo(added) { it.setFinalBase(base) }
            }
        }

        fun collectEdgesTo(
            dst: MutableList<FactToFactEdgeBuilderBuilder>,
            accessPattern: AccessGraph?,
            finalFactBase: AccessPathBase?
        ) {
            if (finalFactBase != null) {
                val storage = find(finalFactBase) ?: return
                collectEdgesTo(dst, storage, accessPattern, finalFactBase)
            } else {
                forEachValue { base, storage ->
                    collectEdgesTo(dst, storage, accessPattern, base)
                }
            }
        }

        private fun collectEdgesTo(
            dst: MutableList<FactToFactEdgeBuilderBuilder>,
            storage: InitialToFinalApStorage,
            accessPattern: AccessGraph?,
            finalFactBase: AccessPathBase
        ) = collectToListWithPostProcess(dst, {
            storage.collectEdgesTo(it, accessPattern)
        }, {
            it.setFinalBase(finalFactBase)
        })
    }

    private class InitialToFinalApStorage {
        private val initialFactGraphIndex = object2IntMap<AccessGraph>()
        private val initialFactGraphs = arrayListOf<AccessGraph>()
        private val finalFactGraphStorages = arrayListOf<FinalApStorage>()

        private val initialGraphIndex = GraphIndex()

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilderBuilder>) {
            val modifiedStorages = BitSet()

            for (edge in edges) {
                val initial = edge.initialFactAp as AccessGraphInitialFactAp
                val final = edge.factAp as AccessGraphFinalFactAp

                check(initial.exclusions == final.exclusions)

                val storageIdx = getOrCreateStorageIdx(initial.access)
                val storage = finalFactGraphStorages[storageIdx]

                if (storage.add(initial.exclusions, final.access)) {
                    modifiedStorages.set(storageIdx)
                }
            }

            modifiedStorages.forEach { storageIdx ->
                val storage = finalFactGraphStorages[storageIdx]
                val storageEdges = mutableListOf<FactToFactEdgeBuilderBuilder>()
                storage.addAndResetDelta(storageEdges)

                val initialAg = initialFactGraphs[storageIdx]
                storageEdges.mapTo(added) { it.setInitialAg(initialAg) }
            }
        }

        private fun getOrCreateStorageIdx(initial: AccessGraph): Int {
            return initialFactGraphIndex.getOrCreateIndex(initial) { newIdx ->
                initialFactGraphs.add(initial)
                finalFactGraphStorages.add(FinalApStorage())
                initialGraphIndex.add(initial, newIdx)
                return newIdx
            }
        }

        fun collectEdgesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>, accessPattern: AccessGraph?) {
            if (accessPattern != null) {
                filterEdgesTo(dst, accessPattern)
            } else {
                allEdgesTo(dst)
            }
        }

        private fun allEdgesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
            finalFactGraphStorages.concurrentReadSafeForEach { idx, finalStorage ->
                val initialAg = initialFactGraphs[idx]
                collectToListWithPostProcess(dst, {
                    finalStorage.allEdgesTo(it)
                }, {
                    it.setInitialAg(initialAg)
                })
            }
        }

        private fun filterEdgesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>, accessPattern: AccessGraph) {
            initialGraphIndex.localizeGraphHasDeltaWithIndexedGraph(accessPattern).forEach { storageIdx ->
                val initialAg = initialFactGraphs[storageIdx]

                if (accessPattern.delta(initialAg).isEmpty()) {
                    return@forEach
                }

                val finalStorage = finalFactGraphStorages[storageIdx]
                collectToListWithPostProcess(dst, {
                    finalStorage.allEdgesTo(it)
                }, {
                    it.setInitialAg(initialAg)
                })
            }
        }

        override fun toString(): String {
            val builder = StringBuilder()
            finalFactGraphStorages.concurrentReadSafeForEach { idx, finalStorage ->
                val initialAg = initialFactGraphs[idx]
                builder.appendLine("($initialAg -> $finalStorage)")
            }
            return builder.toString()
        }
    }

    private class FinalApStorage {
        private var exclusionStorage: ExclusionSet? = null
        private val agStorage = AccessGraphStorageWithCompression()
        private var exclusionModified: Boolean = false

        fun addAndResetDelta(modified: MutableList<FactToFactEdgeBuilderBuilder>) {
            val exclusion = exclusionStorage ?: return
            if (exclusionModified) {
                agStorage.allGraphs().forEach { ag ->
                    modified += FactToFactEdgeBuilderBuilder()
                        .setExclusion(exclusion)
                        .setFinalAg(ag)
                }
            } else {
                agStorage.mapAndResetDelta { ag ->
                    modified += FactToFactEdgeBuilderBuilder()
                        .setExclusion(exclusion)
                        .setFinalAg(ag)
                }
            }

            exclusionModified = false
        }

        fun add(exclusion: ExclusionSet, finalApAg: AccessGraph): Boolean {
            val mergedExclusion = exclusionStorage?.union(exclusion) ?: exclusion
            if (mergedExclusion === exclusionStorage) {
                return agStorage.add(finalApAg)
            }

            exclusionStorage = mergedExclusion
            agStorage.add(finalApAg)
            exclusionModified = true

            return true
        }

        fun allEdgesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
            val exclusion = exclusionStorage ?: return
            collectToListWithPostProcess(dst, {
                agStorage.allGraphsTo(it)
            }, { ag ->
                FactToFactEdgeBuilderBuilder()
                    .setExclusion(exclusion)
                    .setFinalAg(ag)
            })
        }

        override fun toString(): String = "($exclusionStorage -> $agStorage)"
    }

    class FactToFactEdgeBuilderBuilder(
        var initialApBase: AccessPathBase? = null,
        var finalApBase: AccessPathBase? = null,
        var initialApAg: AccessGraph? = null,
        var finalApAg: AccessGraph? = null,
        var exclusion: ExclusionSet? = null
    ) {
        fun build() = FactToFactEdgeBuilder()
            .setInitialAp(AccessGraphInitialFactAp(initialApBase!!, initialApAg!!, exclusion!!))
            .setExitAp(AccessGraphFinalFactAp(finalApBase!!, finalApAg!!, exclusion!!))

        fun setInitialBase(base: AccessPathBase) = also { this.initialApBase = base }
        fun setFinalBase(base: AccessPathBase) = also { this.finalApBase = base }
        fun setInitialAg(ag: AccessGraph) = also { this.initialApAg = ag }
        fun setFinalAg(ag: AccessGraph) = also { this.finalApAg = ag }
        fun setExclusion(exclusion: ExclusionSet) = also { this.exclusion = exclusion }
    }
}
