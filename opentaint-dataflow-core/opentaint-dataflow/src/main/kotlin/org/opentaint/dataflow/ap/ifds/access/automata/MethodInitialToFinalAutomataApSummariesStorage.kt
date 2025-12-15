package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSummary.F2FBBuilder
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.concurrentReadSafeForEach
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class MethodInitialToFinalAutomataApSummariesStorage(
    methodInitialStatement: CommonInst,
) : CommonF2FSummary<AccessGraph, AccessGraph>(methodInitialStatement),
    AutomataInitialApAccess, AutomataFinalApAccess {
    override fun createStorage(): Storage<AccessGraph, AccessGraph> = InitialToFinalApStorage()
}

private class InitialToFinalApStorage : CommonF2FSummary.Storage<AccessGraph, AccessGraph> {
    private val initialFactGraphIndex = object2IntMap<AccessGraph>()
    private val initialFactGraphs = arrayListOf<AccessGraph>()
    private val finalFactGraphStorages = arrayListOf<FinalApStorage>()

    private val initialGraphIndex = GraphIndex()

    override fun add(
        edges: List<CommonF2FSummary.StorageEdge<AccessGraph, AccessGraph>>,
        added: MutableList<F2FBBuilder<AccessGraph, AccessGraph>>,
    ) {
        val modifiedStorages = BitSet()

        for (edge in edges) {
            val storageIdx = getOrCreateStorageIdx(edge.initial)
            val storage = finalFactGraphStorages[storageIdx]

            if (storage.add(edge.exclusion, edge.final)) {
                modifiedStorages.set(storageIdx)
            }
        }

        modifiedStorages.forEach { storageIdx ->
            val storage = finalFactGraphStorages[storageIdx]
            val storageEdges = mutableListOf<F2FBBuilder<AccessGraph, AccessGraph>>()
            storage.addAndResetDelta(storageEdges)

            val initialAg = initialFactGraphs[storageIdx]
            storageEdges.mapTo(added) { it.setInitialAp(initialAg) }
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

    override fun collectSummariesTo(
        dst: MutableList<F2FBBuilder<AccessGraph, AccessGraph>>,
        initialFactPatter: AccessGraph?,
    ) {
        if (initialFactPatter != null) {
            filterEdgesTo(dst, initialFactPatter)
        } else {
            allEdgesTo(dst)
        }
    }

    private fun allEdgesTo(dst: MutableList<F2FBBuilder<AccessGraph, AccessGraph>>) {
        finalFactGraphStorages.concurrentReadSafeForEach { idx, finalStorage ->
            val initialAg = initialFactGraphs[idx]
            collectToListWithPostProcess(dst, {
                finalStorage.allEdgesTo(it)
            }, {
                it.setInitialAp(initialAg)
            })
        }
    }

    private fun filterEdgesTo(dst: MutableList<F2FBBuilder<AccessGraph, AccessGraph>>, accessPattern: AccessGraph) {
        initialGraphIndex.localizeGraphHasDeltaWithIndexedGraph(accessPattern).forEach { storageIdx ->
            val initialAg = initialFactGraphs[storageIdx]

            if (accessPattern.delta(initialAg).isEmpty()) {
                return@forEach
            }

            val finalStorage = finalFactGraphStorages[storageIdx]
            collectToListWithPostProcess(dst, {
                finalStorage.allEdgesTo(it)
            }, {
                it.setInitialAp(initialAg)
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

    fun addAndResetDelta(modified: MutableList<F2FBBuilder<AccessGraph, AccessGraph>>) {
        val exclusion = exclusionStorage ?: return
        if (exclusionModified) {
            agStorage.allGraphs().forEach { ag ->
                modified += FactToFactEdgeBuilderBuilder()
                    .setExclusion(exclusion)
                    .setExitAp(ag)
            }
        } else {
            agStorage.mapAndResetDelta { ag ->
                modified += FactToFactEdgeBuilderBuilder()
                    .setExclusion(exclusion)
                    .setExitAp(ag)
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

    fun allEdgesTo(dst: MutableList<F2FBBuilder<AccessGraph, AccessGraph>>) {
        val exclusion = exclusionStorage ?: return
        collectToListWithPostProcess(dst, {
            agStorage.allGraphsTo(it)
        }, { ag ->
            FactToFactEdgeBuilderBuilder()
                .setExclusion(exclusion)
                .setExitAp(ag)
        })
    }

    override fun toString(): String = "($exclusionStorage -> $agStorage)"
}

class FactToFactEdgeBuilderBuilder : F2FBBuilder<AccessGraph, AccessGraph>(),
    AutomataInitialApAccess, AutomataFinalApAccess {
    override fun nonNullIAP(iap: AccessGraph?): AccessGraph = iap!!
}
