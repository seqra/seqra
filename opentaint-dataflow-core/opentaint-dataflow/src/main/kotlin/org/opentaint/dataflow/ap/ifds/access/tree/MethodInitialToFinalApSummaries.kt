package org.opentaint.dataflow.ap.ifds.access.tree

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
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodInitialToFinalApSummaries(
    methodInitialStatement: CommonInst
) : MethodInitialToFinalApSummariesStorage {
    private val storage = MethodTaintedSummariesStorage(methodInitialStatement)

    override fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
        storage.add(edges, added)
    }

    override fun filterEdgesTo(
        dst: MutableList<FactToFactEdgeBuilder>,
        pattern: FinalFactAp?,
        finalFactBase: AccessPathBase?
    ) {
        storage.filterEdgesTo(dst, EdgeStoragePattern(pattern as? AccessTree, finalFactBase))
    }

    override fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>) {
        storage.collectAllEdgesTo(dst)
    }
}

private class EdgeStoragePattern(
    val initialFactPattern: AccessTree?,
    val finalFactBase: AccessPathBase?
)

private class MethodTaintedSummariesStorage(methodEntryPoint: CommonInst) :
    MethodSummaryFactEdgesForExitPoint<MethodFactToFactSummaries, EdgeStoragePattern>(methodEntryPoint) {

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
        storage.filterTo(dst, containsPattern)
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

    fun filterTo(dst: MutableList<FactToFactEdgeBuilder>, pattern: EdgeStoragePattern) {
        val initialFactBase = pattern.initialFactPattern?.base
        if (initialFactBase != null) {
            val storage = find(initialFactBase) ?: return
            filterTo(dst, storage, initialFactBase, pattern.finalFactBase, pattern.initialFactPattern.access)
        } else {
            forEachValue { base, storage ->
                filterTo(dst, storage, base, pattern.finalFactBase, pattern.initialFactPattern?.access)
            }
        }
    }

    fun collectAllEdgesTo(dst: MutableList<FactToFactEdgeBuilder>) {
        forEachValue { base, storage ->
            filterTo(dst, storage, base, finalFactBase = null, containsPattern = null)
        }
    }

    private fun filterTo(
        dst: MutableList<FactToFactEdgeBuilder>,
        storage: MethodTaintedSummariesGroupedByFact,
        initialFactBase: AccessPathBase,
        finalFactBase: AccessPathBase?,
        containsPattern: AccessTree.AccessNode?
    ) {
        collectToListWithPostProcess(dst, {
            storage.filterEdgesTo(it, containsPattern, finalFactBase)
        }, {
            it.setInitialFactBase(initialFactBase).build()
        })
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

    fun filterEdgesTo(
        dst: MutableList<FactToFactEdgeBuilderBuilder>,
        containsPattern: AccessTreeNode?,
        finalFactBase: AccessPathBase?
    ) {
        if (finalFactBase != null) {
            val storage = find(finalFactBase) ?: return
            collectTo(dst, storage, finalFactBase, containsPattern)
        } else {
            forEachValue { base, storage ->
                collectTo(dst, storage, base, containsPattern)
            }
        }
    }

    private fun collectTo(
        dst: MutableList<FactToFactEdgeBuilderBuilder>,
        storage: MethodTaintedSummariesGroupedByFactStorage,
        finalFactBase: AccessPathBase,
        containsPattern: AccessTree.AccessNode?
    ) = collectToListWithPostProcess(dst, {
        storage.collectSummariesTo(it, containsPattern)
    }, {
        it.setExitFactBase(finalFactBase)
    })
}

private class MethodTaintedSummariesInitialApStorage :
    AccessBasedStorage<MethodTaintedSummariesInitialApStorage>() {
    private var current: MethodTaintedSummariesMergingStorage? = null

    override fun createStorage() = MethodTaintedSummariesInitialApStorage()

    fun getOrCreate(initialAccess: AccessPath.AccessNode?): MethodTaintedSummariesMergingStorage =
        getOrCreateNode(initialAccess).getOrCreateCurrent(initialAccess)

    fun filterSummariesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>, containsPattern: AccessTreeNode) {
        filterContains(containsPattern).forEach { node ->
            node.current?.summaries()?.let { dst.add(it) }
        }
    }

    fun collectAllSummariesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
        allNodes().forEach { node ->
            node.current?.summaries()?.let { dst.add(it) }
        }
    }

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
            val initialAccess = (edge.initialFactAp as AccessPath).access
            val exitAccess = (edge.factAp as AccessTree).access

            val exclusion = edge.initialFactAp.exclusions
            check(exclusion == edge.factAp.exclusions) { "Edge invariant" }

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

    fun collectSummariesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>, containsPattern: AccessTreeNode?) {
        if (containsPattern != null) {
            filterSummariesTo(dst, containsPattern)
        } else {
            collectAllSummariesTo(dst)
        }
    }

    private fun filterSummariesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>, containsPattern: AccessTreeNode) {
        nonUniverseAccessPath.filterSummariesTo(dst, containsPattern)
    }

    private fun collectAllSummariesTo(dst: MutableList<FactToFactEdgeBuilderBuilder>) {
        nonUniverseAccessPath.collectAllSummariesTo(dst)
    }
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
