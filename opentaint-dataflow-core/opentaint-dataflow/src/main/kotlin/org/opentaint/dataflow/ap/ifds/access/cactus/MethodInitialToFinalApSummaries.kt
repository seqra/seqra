package org.opentaint.dataflow.ap.ifds.access.cactus

import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.ap.ifds.access.cactus.AccessCactus.AccessNode as AccessCactusNode

class MethodInitialToFinalApSummaries(
    methodInitialStatement: CommonInst
) : MethodInitialToFinalApSummariesStorage {
    private val storage = MethodTaintedSummariesStorage(methodInitialStatement)

    override fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        storage.add(edges, added)
    }

    override fun filterEdgesTo(
        dst: MutableList<FactToFactEdgeBuilder>,
        pattern: FinalFactAp,
        finalFactBase: AccessPathBase?
    ) {
        storage.filterEdgesTo(dst, EdgeStoragePattern(pattern as AccessCactus, finalFactBase))
    }

    override fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>) {
        storage.collectAllEdgesTo(dst)
    }
}

private class EdgeStoragePattern(
    val initialFactPattern: AccessCactus,
    val finalFactBase: AccessPathBase?
)

private class MethodTaintedSummariesStorage(methodEntryPoint: CommonInst) :
    MethodSummaryEdgesForExitPoint<Edge.FactToFact, FactToFactEdgeBuilder, MethodFactToFactSummaries, EdgeStoragePattern>(
        methodEntryPoint
    ) {

    override fun createStorage(): MethodFactToFactSummaries =
        MethodFactToFactSummaries(methodEntryPoint)

    override fun storageAdd(
        storage: MethodFactToFactSummaries,
        edges: List<Edge.FactToFact>,
        added: MutableList<FactToFactEdgeBuilder>
    ) = storage.add(edges, added)

    override fun storageCollectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>, storage: MethodFactToFactSummaries) {
        storage.collectAllEdgesTo(dst)
    }

    override fun storageFilterEdgesTo(
        dst: MutableList<FactToFactEdgeBuilder>,
        storage: MethodFactToFactSummaries,
        containsPattern: EdgeStoragePattern
    ) {
        storage.filterEdgesTo(dst, containsPattern)
    }
}

private class MethodFactToFactSummaries(
    private val methodEntryPoint: CommonInst
) : SummaryFactStorage<MethodTaintedSummariesGroupedByFact>(methodEntryPoint) {
    override fun createStorage() = MethodTaintedSummariesGroupedByFact(methodEntryPoint)

    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        val sameInitialBaseEdges = edges.groupBy { it.initialFactAp.base }
        for ((initialBase, sameBaseEdges) in sameInitialBaseEdges) {
            val baseAdded = mutableListOf<FactToFactEdgeBuilderBuilder>()
            getOrCreate(initialBase).add(sameBaseEdges, baseAdded)
            baseAdded.mapTo(added) { it.setInitialFactBase(initialBase).build() }
        }
    }

    fun filterEdgesTo(dst: MutableList<FactToFactEdgeBuilder>, pattern: EdgeStoragePattern) {
        val initialBase = pattern.initialFactPattern.base
        val storage = find(initialBase) ?: return

        collectToListWithPostProcess(dst, {
            if (pattern.finalFactBase == null) {
                storage.collectAllEdgesTo(it)
            } else {
                storage.filterTo(it, pattern.finalFactBase)
            }
        }, {
            it.setInitialFactBase(initialBase).build()
        })
    }

    fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>) {
        forEachValue { base, storage ->
            collectToListWithPostProcess(dst, {
                storage.collectAllEdgesTo(it)
            }, {
                it.setInitialFactBase(base).build()
            })
        }
    }
}


private class MethodTaintedSummariesGroupedByFact(methodEntryPoint: CommonInst) :
    SummaryFactStorage<MethodTaintedSummariesGroupedByFactStorage>(methodEntryPoint) {
    override fun createStorage() = MethodTaintedSummariesGroupedByFactStorage()

    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilderBuilder>) {
        val sameExitBaseEdges = edges.groupBy { it.factAp.base }
        for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {

            val baseAdded = mutableListOf<FactToFactEdgeBuilderBuilder>()
            getOrCreate(exitBase).add(sameBaseEdges, baseAdded)
            baseAdded.mapTo(added) { it.setExitFactBase(exitBase) }
        }
    }

    fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
        forEachValue { base, storage ->
            collectToListWithPostProcess(dst, {
                storage.collectAllSummariesTo(it)
            }, {
                it.setExitFactBase(base)
            })
        }
    }

    fun filterTo(dst: MutableList<FactToFactEdgeBuilderBuilder>, finalFactBase: AccessPathBase) {
        val storage = find(finalFactBase) ?: return
        collectToListWithPostProcess(dst, {
            storage.collectAllSummariesTo(it)
        }, {
            it.setExitFactBase(finalFactBase)
        })
    }
}

private class MethodTaintedSummariesInitialApStorage {
    private var initialAccessToStorage =
        persistentHashMapOf<AccessPathWithCycles.AccessNode?, MethodTaintedSummariesMergingStorage>()

    fun getOrCreate(initialAccess: AccessPathWithCycles.AccessNode?): MethodTaintedSummariesMergingStorage =
        initialAccessToStorage.getOrElse(initialAccess) {
            MethodTaintedSummariesMergingStorage(initialAccess).also {
                initialAccessToStorage = initialAccessToStorage.put(initialAccess, it)
            }
        }

    fun collectAllSummaries(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
        initialAccessToStorage.values.forEach { storage ->
            storage.summaries()?.let { dst.add(it) }
        }
    }
}

private class MethodTaintedSummariesGroupedByFactStorage {
    private val nonUniverseAccessPath = MethodTaintedSummariesInitialApStorage()

    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilderBuilder>) {
        addNonUniverseEdges(edges, added)
    }

    private fun addNonUniverseEdges(
        edges: List<Edge.FactToFact>,
        added: MutableList<FactToFactEdgeBuilderBuilder>
    ) {
        val modifiedStorages = mutableListOf<MethodTaintedSummariesMergingStorage>()

        for (edge in edges) {
            // edges here are already separated by marks, statements and bases
            val initialAccess = (edge.initialFactAp as AccessPathWithCycles).access
            val exitAccess = (edge.factAp as AccessCactus).access

            val exclusion = edge.initialFactAp.exclusions
            check(exclusion == edge.factAp.exclusions) { "Edge invariant" }

            addNonUniverseEdge(initialAccess, exitAccess, exclusion, modifiedStorages)
        }

        modifiedStorages.flatMapTo(added) { it.getAndResetDelta() }
    }

    private fun addNonUniverseEdge(
        initialAccess: AccessPathWithCycles.AccessNode?,
        exitAccess: AccessCactusNode,
        exclusion: ExclusionSet,
        modifiedStorages: MutableList<MethodTaintedSummariesMergingStorage>
    ) {
        val storage = nonUniverseAccessPath.getOrCreate(initialAccess)
        val storageModified = storage.add(exitAccess, exclusion)

        if (storageModified) {
            modifiedStorages.add(storage)
        }
    }

    fun collectAllSummariesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
        nonUniverseAccessPath.collectAllSummaries(dst)
    }
}

private class MethodTaintedSummariesMergingStorage(val initialAccess: AccessPathWithCycles.AccessNode?) {
    private var exclusion: ExclusionSet? = null
    private var edges: AccessCactusNode? = null
    private var edgesDelta: AccessCactusNode? = null

    fun add(exitAccess: AccessCactusNode, addedEx: ExclusionSet): Boolean {
        val currentExclusion = exclusion
        if (currentExclusion == null) {
            exclusion = addedEx
            edges = exitAccess
            edgesDelta = exitAccess
            return true
        }

        val currentEdges = edges!!
        val mergedExclusion = currentExclusion.union(addedEx)
        if (mergedExclusion === currentExclusion) {
            val (modifiedEdges, modificationDelta) = currentEdges.mergeAddDelta(exitAccess)
            if (modificationDelta == null) return false

            edges = modifiedEdges
            edgesDelta = edgesDelta?.mergeAdd(modificationDelta) ?: modificationDelta
            return true
        }

        val mergedAp = currentEdges.mergeAdd(exitAccess)
        exclusion = mergedExclusion
        edges = mergedAp
        edgesDelta = mergedAp

        return true
    }

    fun getAndResetDelta(): Sequence<FactToFactEdgeBuilderBuilder> {
        val delta = edgesDelta ?: return emptySequence()
        edgesDelta = null

        return FactToFactEdgeBuilderBuilder()
            .setInitialAp(initialAccess)
            .setExitAp(delta)
            .setExclusion(exclusion!!)
            .let { sequenceOf(it) }
    }

    fun summaries(): FactToFactEdgeBuilderBuilder? {
        val exclusion = this.exclusion ?: return null
        val edges = this.edges!!
        return FactToFactEdgeBuilderBuilder()
            .setInitialAp(initialAccess)
            .setExitAp(edges)
            .setExclusion(exclusion)
    }
}

private data class FactToFactEdgeBuilderBuilder(
    private var initialBase: AccessPathBase? = null,
    private var exitBase: AccessPathBase? = null,
    private var exclusion: ExclusionSet? = null,
    private var initialAp: AccessPathWithCycles.AccessNode? = null,
    private var exitAp: AccessCactusNode? = null,
) {
    fun build(): FactToFactEdgeBuilder = FactToFactEdgeBuilder()
        .setInitialAp(AccessPathWithCycles(initialBase!!, initialAp, exclusion!!))
        .setExitAp(AccessCactus(exitBase!!, exitAp!!, exclusion!!))

    fun setInitialFactBase(base: AccessPathBase) = this.also {
        initialBase = base
    }

    fun setExitFactBase(base: AccessPathBase) = this.also {
        exitBase = base
    }

    fun setExclusion(exclusion: ExclusionSet) = this.also {
        this.exclusion = exclusion
    }

    fun setInitialAp(ap: AccessPathWithCycles.AccessNode?) = this.also {
        initialAp = ap
    }

    fun setExitAp(ap: AccessCactusNode) = this.also {
        exitAp = ap
    }
}
