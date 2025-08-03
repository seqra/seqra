package org.opentaint.dataflow.jvm.ap.ifds.access.tree

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Edge
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.jvm.ap.ifds.MethodSummaryEdgesForExitPoint
import org.opentaint.dataflow.jvm.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodInitialToFinalApSummaries(
    methodInitialStatement: JIRInst
) : MethodInitialToFinalApSummariesStorage {
    private val storage = MethodTaintedSummariesStorage(methodInitialStatement)

    override fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        storage.add(edges, added)
    }

    override fun filterEdges(pattern: FinalFactAp): Sequence<FactToFactEdgeBuilder> =
        storage.filterEdges(pattern as AccessTree)

    override fun allEdges(): Sequence<FactToFactEdgeBuilder> =
        storage.allEdges()
}

private class MethodTaintedSummariesStorage(methodEntryPoint: JIRInst) :
    MethodSummaryEdgesForExitPoint<Edge.FactToFact, FactToFactEdgeBuilder, MethodFactToFactSummaries, AccessTree>(
        methodEntryPoint
    ) {

    override fun createStorage(): MethodFactToFactSummaries =
        MethodFactToFactSummaries(methodEntryPoint)

    override fun storageAdd(
        storage: MethodFactToFactSummaries,
        edges: List<Edge.FactToFact>,
        added: MutableList<FactToFactEdgeBuilder>
    ) = storage.add(edges, added)

    override fun storageAllEdges(storage: MethodFactToFactSummaries): Sequence<FactToFactEdgeBuilder> =
        storage.allEdges()

    override fun storageFilterEdges(
        storage: MethodFactToFactSummaries,
        containsPattern: AccessTree
    ): Sequence<FactToFactEdgeBuilder> = storage.filterEdges(containsPattern.base, containsPattern.access)
}

private class MethodFactToFactSummaries(
    private val methodEntryPoint: JIRInst
) : SummaryFactStorage<MethodTaintedSummariesGroupedByFact>(methodEntryPoint) {
    override fun createStorage() = MethodTaintedSummariesGroupedByFact(methodEntryPoint)

    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        val sameInitialBaseEdges = edges.groupBy { it.initialFact.ap.base }
        for ((initialBase, sameBaseEdges) in sameInitialBaseEdges) {
            val baseAdded = mutableListOf<FactToFactEdgeBuilderBuilder>()
            getOrCreate(initialBase).add(sameBaseEdges, baseAdded)
            baseAdded.mapTo(added) { it.setInitialFactBase(initialBase).build() }
        }
    }

    fun filterEdges(base: AccessPathBase, containsPattern: AccessTreeNode): Sequence<FactToFactEdgeBuilder> =
        find(base)
            ?.filterEdges(containsPattern)
            ?.map { it.setInitialFactBase(base).build() }
            .orEmpty()

    fun allEdges(): Sequence<FactToFactEdgeBuilder> = mapValues { base, storage ->
        storage.allEdges().map { it.setInitialFactBase(base).build() }
    }.flatten()
}


private class MethodTaintedSummariesGroupedByFact(methodEntryPoint: JIRInst) :
    SummaryFactStorage<MethodTaintedSummariesGroupedByFactStorage>(methodEntryPoint) {
    override fun createStorage() = MethodTaintedSummariesGroupedByFactStorage()

    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilderBuilder>) {
        val sameExitBaseEdges = edges.groupBy { it.fact.ap.base }
        for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {

            val baseAdded = mutableListOf<FactToFactEdgeBuilderBuilder>()
            getOrCreate(exitBase).add(sameBaseEdges, baseAdded)
            baseAdded.mapTo(added) { it.setExitFactBase(exitBase) }
        }
    }

    fun filterEdges(containsPattern: AccessTreeNode): Sequence<FactToFactEdgeBuilderBuilder> =
        mapValues { base, storage ->
            storage.findSummaries(containsPattern).map { it.setExitFactBase(base) }
        }.flatten()

    fun allEdges(): Sequence<FactToFactEdgeBuilderBuilder> =
        mapValues { base, storage ->
            storage.allSummaries().map { it.setExitFactBase(base) }
        }.flatten()
}


private class MethodTaintedSummariesInitialApStorage :
    AccessBasedStorage<MethodTaintedSummariesInitialApStorage>() {
    private var current: MethodTaintedSummariesMergingStorage? = null

    override fun createStorage() = MethodTaintedSummariesInitialApStorage()

    fun getOrCreate(initialAccess: AccessPath.AccessNode?): MethodTaintedSummariesMergingStorage =
        getOrCreateNode(initialAccess).getOrCreateCurrent(initialAccess)

    fun findSummaries(containsPattern: AccessTreeNode): Sequence<FactToFactEdgeBuilderBuilder> =
        filterContains(containsPattern).flatMap { it.current?.summaries().orEmpty() }

    fun allSummaries(): Sequence<FactToFactEdgeBuilderBuilder> =
        allNodes().flatMap { it.current?.summaries().orEmpty() }

    private fun getOrCreateCurrent(access: AccessPath.AccessNode?) =
        current ?: MethodTaintedSummariesMergingStorage(access).also { current = it }
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
            val initialAccess = (edge.initialFact.ap as AccessPath).access
            val exitAccess = (edge.fact.ap as AccessTree).access

            val exclusion = edge.initialFact.ap.exclusions
            check(exclusion == edge.fact.ap.exclusions) { "Edge invariant" }

            addNonUniverseEdge(initialAccess, exitAccess, exclusion, modifiedStorages)
        }

        modifiedStorages.flatMapTo(added) { it.getAndResetDelta() }
    }

    private fun addNonUniverseEdge(
        initialAccess: AccessPath.AccessNode?,
        exitAccess: AccessTreeNode,
        exclusion: ExclusionSet,
        modifiedStorages: MutableList<MethodTaintedSummariesMergingStorage>
    ) {
        val storage = nonUniverseAccessPath.getOrCreate(initialAccess)
        val storageModified = storage.add(exitAccess, exclusion)

        if (storageModified) {
            modifiedStorages.add(storage)
        }
    }

    fun findSummaries(containsPattern: AccessTreeNode): Sequence<FactToFactEdgeBuilderBuilder> =
        nonUniverseAccessPath.findSummaries(containsPattern)

    fun allSummaries(): Sequence<FactToFactEdgeBuilderBuilder> =
        nonUniverseAccessPath.allSummaries()
}

private class MethodTaintedSummariesMergingStorage(val initialAccess: AccessPath.AccessNode?) {
    private var exclusion: ExclusionSet? = null
    private var edges: AccessTreeNode? = null
    private var edgesDelta: AccessTreeNode? = null

    fun add(exitAccess: AccessTreeNode, addedEx: ExclusionSet): Boolean {
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

    fun summaries(): Sequence<FactToFactEdgeBuilderBuilder> {
        val exclusion = this.exclusion ?: return emptySequence()
        val edges = this.edges!!
        return FactToFactEdgeBuilderBuilder()
            .setInitialAp(initialAccess)
            .setExitAp(edges)
            .setExclusion(exclusion)
            .let { sequenceOf(it) }
    }
}

private data class FactToFactEdgeBuilderBuilder(
    private var initialBase: AccessPathBase? = null,
    private var exitBase: AccessPathBase? = null,
    private var exclusion: ExclusionSet? = null,
    private var initialAp: AccessPath.AccessNode? = null,
    private var exitAp: AccessTreeNode? = null,
) {
    fun build(): FactToFactEdgeBuilder = FactToFactEdgeBuilder()
        .setInitialAp(AccessPath(initialBase!!, initialAp, exclusion!!))
        .setExitAp(AccessTree(exitBase!!, exitAp!!, exclusion!!))

    fun setInitialFactBase(base: AccessPathBase) = this.also {
        initialBase = base
    }

    fun setExitFactBase(base: AccessPathBase) = this.also {
        exitBase = base
    }

    fun setExclusion(exclusion: ExclusionSet) = this.also {
        this.exclusion = exclusion
    }

    fun setInitialAp(ap: AccessPath.AccessNode?) = this.also {
        initialAp = ap
    }

    fun setExitAp(ap: AccessTreeNode) = this.also {
        exitAp = ap
    }
}
