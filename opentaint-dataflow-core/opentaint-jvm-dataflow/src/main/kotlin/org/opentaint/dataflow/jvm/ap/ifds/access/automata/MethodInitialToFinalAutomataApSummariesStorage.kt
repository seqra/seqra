package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Edge
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.jvm.ap.ifds.MethodSummaryFactEdgesForExitPoint
import org.opentaint.dataflow.jvm.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import java.util.concurrent.ConcurrentHashMap

class MethodInitialToFinalAutomataApSummariesStorage(
    methodInitialStatement: JIRInst
) : MethodInitialToFinalApSummariesStorage {
    private val storage = ExitPointStorage(methodInitialStatement)

    override fun toString(): String = storage.toString()

    override fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        storage.add(edges, added)
    }

    override fun filterEdges(
        pattern: FinalFactAp,
        finalFactBase: AccessPathBase?
    ): Sequence<FactToFactEdgeBuilder> =
        storage.filterEdges(StorageFilterPattern(pattern, finalFactBase))

    override fun allEdges(): Sequence<FactToFactEdgeBuilder> = storage.allEdges()

    private class StorageFilterPattern(
        val initialFactPattern: FinalFactAp,
        val finalFactBase: AccessPathBase?
    )

    private class ExitPointStorage(methodEntryPoint: JIRInst) :
        MethodSummaryFactEdgesForExitPoint<BasedInitialAp, StorageFilterPattern>(methodEntryPoint) {
        override fun createStorage(): BasedInitialAp = BasedInitialAp(methodEntryPoint)

        override fun storageFilterEdges(
            storage: BasedInitialAp,
            containsPattern: StorageFilterPattern
        ): Sequence<FactToFactEdgeBuilder> = storage.filter(containsPattern)

        override fun storageAllEdges(storage: BasedInitialAp): Sequence<FactToFactEdgeBuilder> = storage.allEdges()

        override fun storageAdd(
            storage: BasedInitialAp,
            edges: List<Edge.FactToFact>,
            added: MutableList<FactToFactEdgeBuilder>
        ) {
            storage.add(edges, added)
        }
    }

    private class BasedInitialAp(
        private val methodEntryPoint: JIRInst
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

        fun filter(containsPattern: StorageFilterPattern): Sequence<FactToFactEdgeBuilder> {
            val initialFactBase = containsPattern.initialFactPattern.base
            val storage = find(initialFactBase) ?: return emptySequence()

            val edges = if (containsPattern.finalFactBase == null) {
                storage.allEdges()
            } else {
                storage.filter(containsPattern.finalFactBase)
            }

            return edges.map {
                it.setInitialBase(initialFactBase).build()
            }
        }

        fun allEdges(): Sequence<FactToFactEdgeBuilder> = mapValues { base, storage ->
            storage.allEdges().map { it.setInitialBase(base).build() }
        }.flatten()
    }

    private class BasedFinalAp(methodEntryPoint: JIRInst) :
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

        fun allEdges(): Sequence<FactToFactEdgeBuilderBuilder> = mapValues { base, storage ->
            storage.allEdges().map { it.setFinalBase(base) }
        }.flatten()

        fun filter(finalFactBase: AccessPathBase): Sequence<FactToFactEdgeBuilderBuilder> {
            val storage = find(finalFactBase) ?: return emptySequence()
            return storage.allEdges().map { it.setFinalBase(finalFactBase) }
        }
    }

    private class InitialToFinalApStorage {
        private val finalFactsGroupedByInitial = ConcurrentHashMap<AccessGraph, FinalApStorage>()

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilderBuilder>) {
            val modifiedStorages = mutableListOf<Pair<AccessGraph, FinalApStorage>>()

            for (edge in edges) {
                val initial = edge.initialFactAp as AccessGraphInitialFactAp
                val final = edge.factAp as AccessGraphFinalFactAp

                check(initial.exclusions == final.exclusions)

                val storage = finalFactsGroupedByInitial.computeIfAbsent(initial.access) {
                    FinalApStorage()
                }

                if (storage.add(initial.exclusions, final.access)) {
                    modifiedStorages.add(initial.access to storage)
                }
            }

            modifiedStorages.forEach { (initialAg, storage) ->
                val storageEdges = mutableListOf<FactToFactEdgeBuilderBuilder>()
                storage.addAndResetDelta(storageEdges)
                storageEdges.mapTo(added) { it.setInitialAg(initialAg) }
            }
        }

        fun allEdges(): Sequence<FactToFactEdgeBuilderBuilder> =
            finalFactsGroupedByInitial.asSequence().flatMap { (initialAg, finalStorage) ->
                finalStorage.allEdges().map { it.setInitialAg(initialAg) }
            }

        override fun toString(): String =
            finalFactsGroupedByInitial
                .asSequence()
                .map { (initial, finalStorage) ->
                    "($initial -> $finalStorage)"
                }
                .joinToString("\n")
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

        fun allEdges(): Sequence<FactToFactEdgeBuilderBuilder> {
            val exclusion = exclusionStorage ?: return emptySequence()
            return agStorage.allGraphs().map { ag ->
                FactToFactEdgeBuilderBuilder()
                    .setExclusion(exclusion)
                    .setFinalAg(ag)
            }
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
