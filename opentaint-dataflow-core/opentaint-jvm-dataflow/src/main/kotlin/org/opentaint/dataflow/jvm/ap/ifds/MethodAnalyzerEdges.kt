package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.AccessTree.AccessNode

class MethodAnalyzerEdges(private val initialStatement: JIRInst) {
    private val maxInstIdx = initialStatement.location.method.instList.maxOf { it.location.index }

    private val zeroToZeroEdges = SameInitialZeroFactEdges(maxInstIdx)
    private val zeroToFactEdges = Object2ObjectOpenHashMap<TaintMark, ZeroInitialFactEdgeStorage>()
    private val taintedToFactSameEdges = Object2ObjectOpenHashMap<TaintMark, TaintedInitialFactEdgeStorage>()
    private val taintedToFactDifferentEdges by lazy {
        Object2ObjectOpenHashMap<Pair<TaintMark, TaintMark>, TaintedInitialFactEdgeStorage>()
    }

    fun add(edge: Edge): List<Edge> {
        check(edge.initialStatement == initialStatement)

        return addEdge(edge)
    }

    private fun addEdge(edge: Edge): List<Edge> {
        when (edge) {
            is Edge.ZeroToZero -> {
                val edgeAdded = zeroToZeroEdges.addZeroEdge(edge.statement)
                return if (edgeAdded) listOf(edge) else emptyList()
            }

            is Edge.ZeroToFact -> {
                val storage = zeroToFactEdges.getOrPut(edge.fact.mark) {
                    ZeroInitialFactEdgeStorage(initialStatement, maxInstIdx)
                }

                val edgeSet = storage.getOrCreate(edge.fact.ap.base)

                val edgeAccess = edge.fact.ap.access
                val addedAccess = edgeSet.addEdge(edge.statement, edgeAccess)

                if (addedAccess === edgeAccess) return listOf(edge)
                if (addedAccess == null) return emptyList()

                val mergedAp = AccessTree(edge.fact.ap.base, addedAccess, ExclusionSet.Universe)
                val mergedFact = edge.fact.changeAP(mergedAp)

                return listOf(Edge.ZeroToFact(edge.initialStatement, edge.statement, mergedFact))
            }

            is Edge.FactToFact -> {
                return addTaintedFactEdge(edge)
            }
        }
    }

    private fun addTaintedFactEdge(edge: Edge.FactToFact): List<Edge.FactToFact> {
        val edgeStorage = if (edge.initialFact.mark == edge.fact.mark) {
            taintedToFactSameEdges.getOrPut(edge.initialFact.mark) { TaintedInitialFactEdgeStorage(initialStatement) }
        } else {
            taintedToFactDifferentEdges.getOrPut(edge.initialFact.mark to edge.fact.mark) {
                TaintedInitialFactEdgeStorage(initialStatement)
            }
        }

        val edgeStorageForInitialFact = edgeStorage.getOrCreate(edge.initialFact.ap.base)
        val edgeStorageForExitFact = edgeStorageForInitialFact.getOrCreate(edge.fact.ap.base)

        check(edge.initialFact.ap.exclusions == edge.fact.ap.exclusions) { "Edge exclusion mismatch" }

        val edgeSet = edgeStorageForExitFact.getOrCreateNonUniverse(
            edge.initialFact.ap.access, maxInstIdx
        )

        val accessWithExclusion = EdgeNonUniverseExclusionMergingStorage.AccessWithExclusion(
            edge.fact.ap.access, edge.fact.ap.exclusions
        )

        val addedAccessWithExclusions = edgeSet.add(edge.statement, accessWithExclusion)

        return addedAccessWithExclusions.map { addedAccessWithExclusion ->
            if (addedAccessWithExclusion === accessWithExclusion) return@map edge

            val newInitialAp = edge.initialFact.ap.let {
                AccessPath(it.base, it.access, addedAccessWithExclusion.exclusion)
            }

            val newExitAp = AccessTree(
                edge.fact.ap.base, addedAccessWithExclusion.access, addedAccessWithExclusion.exclusion
            )

            Edge.FactToFact(
                initialStatement = edge.initialStatement,
                initialFact = edge.initialFact.changeAP(newInitialAp),
                statement = edge.statement,
                fact = edge.fact.changeAP(newExitAp)
            )
        }
    }

    private class SameInitialZeroFactEdges(maxInstIdx: Int) {
        private val edges = BooleanArray(instructionStorageSize(maxInstIdx))

        fun addZeroEdge(statement: JIRInst): Boolean {
            val edgeIdx = instructionStorageIdx(statement)
            if (edges[edgeIdx]) return false

            edges[edgeIdx] = true
            return true
        }
    }

    private class ZeroInitialFactEdges(maxInstIdx: Int) {
        private val edges = arrayOfNulls<AccessNode?>(instructionStorageSize(maxInstIdx))

        fun addEdge(statement: JIRInst, accessPath: AccessNode): AccessNode? {
            val factSetIdx = instructionStorageIdx(statement)
            val factSet = edges[factSetIdx]

            if (factSet == null) {
                edges[factSetIdx] = accessPath
                return accessPath
            }

            val mergedFacts = factSet.mergeAdd(accessPath)
            if (mergedFacts === factSet) {
                return null
            }

            edges[factSetIdx] = mergedFacts
            return mergedFacts
        }
    }

    private class EdgeNonUniverseExclusionMergingStorage(maxInstIdx: Int) {
        private val edges = arrayOfNulls<Any>(instructionStorageSize(maxInstIdx))

        fun add(statement: JIRInst, accessWithExclusion: AccessWithExclusion): List<AccessWithExclusion> {
            val edgeSetIdx = instructionStorageIdx(statement)
            val currentEdgeAccess = edges[edgeSetIdx]

            if (currentEdgeAccess == null) {
                edges[edgeSetIdx] = accessWithExclusion
                return listOf(accessWithExclusion)
            }

            val edgeData = if (currentEdgeAccess is AccessWithExclusion) {
                Object2ObjectOpenHashMap<ExclusionSet, EdgeMergeUtils.Storage>().also { map ->
                    map[currentEdgeAccess.exclusion] = EdgeMergeUtils.Storage(
                        currentEdgeAccess.exclusion, currentEdgeAccess.access, currentEdgesDelta = null
                    )
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                currentEdgeAccess as MutableMap<ExclusionSet, EdgeMergeUtils.Storage>
            }

            if (!add(accessWithExclusion, edgeData)) return emptyList()

            if (edgeData.size == 1) {
                val storage = edgeData.values.single()
                edges[edgeSetIdx] = AccessWithExclusion(storage.edges, storage.exclusion)
            } else {
                edges[edgeSetIdx] = edgeData
            }

            val modifiedEdges = edgeData.mapNotNull { (ex, storage) ->
                storage.getAndResetDelta()?.let { AccessWithExclusion(it, ex) }
            }

            return modifiedEdges
        }

        data class AccessWithExclusion(val access: AccessNode, val exclusion: ExclusionSet)

        private fun add(
            accessWithExclusion: AccessWithExclusion,
            data: MutableMap<ExclusionSet, EdgeMergeUtils.Storage>,
        ): Boolean {
            var modified = false
            val queue = mutableListOf(accessWithExclusion)
            while (queue.isNotEmpty()) {
                val current = queue.removeLast()
                modified = modified or EdgeMergeUtils.mergeAdd(
                    access = current.access,
                    exclusion = current.exclusion,
                    allStorages = { data.values.iterator() },
                    saveStorage = { ex, storage -> data[ex] = storage },
                    removeStorage = { ex -> data.remove(ex) },
                    enqueue = { ex, access -> queue.add(AccessWithExclusion(access, ex)) }
                )
            }
            return modified
        }
    }

    private abstract class EdgeStorage<Storage : Any>(initialStatement: JIRInst) :
        AccessPathBaseStorage<Storage>(initialStatement) {
        private val locals = Int2ObjectOpenHashMap<Storage>()

        override fun getOrCreateLocal(idx: Int): Storage = locals.getOrPut(idx) { createStorage() }
        override fun findLocal(idx: Int): Storage? = locals.get(idx)
        override fun <R : Any> mapLocalValues(body: (AccessPathBase, Storage) -> R): Sequence<R> =
            locals.asSequence().map { (idx, storage) -> body(AccessPathBase.LocalVar(idx), storage) }

        private var constants: MutableMap<AccessPathBase.Constant, Storage>? = null

        override fun getOrCreateConstant(base: AccessPathBase.Constant): Storage {
            val edges = constants ?: Object2ObjectOpenHashMap<AccessPathBase.Constant, Storage>()
                    .also { constants = it }

            return edges.getOrPut(base) { createStorage() }
        }

        override fun findConstant(base: AccessPathBase.Constant) = constants?.get(base)

        override fun <R : Any> mapConstantValues(body: (AccessPathBase, Storage) -> R): Sequence<R> =
            constants?.asSequence()?.map { body(it.key, it.value) } ?: emptySequence()

        private var statics: MutableMap<AccessPathBase.ClassStatic, Storage>? = null

        override fun getOrCreateClassStatic(base: AccessPathBase.ClassStatic): Storage {
            val edges = statics ?: Object2ObjectOpenHashMap<AccessPathBase.ClassStatic, Storage>()
                .also { statics = it }

            return edges.getOrPut(base) { createStorage() }
        }

        override fun findClassStatic(base: AccessPathBase.ClassStatic) = statics?.get(base)

        override fun <R : Any> mapClassStaticValues(body: (AccessPathBase, Storage) -> R): Sequence<R> =
            statics?.asSequence()?.map { body(it.key, it.value) } ?: emptySequence()
    }

    private class TaintedInitialFactEdgeStorage(private val initialStatement: JIRInst) :
        EdgeStorage<TaintedExitFactEdgeStorage>(initialStatement) {
        override fun createStorage(): TaintedExitFactEdgeStorage = TaintedExitFactEdgeStorage(initialStatement)
    }

    private class TaintedExitFactEdgeStorage(initialStatement: JIRInst) :
        EdgeStorage<TaintedFactAccessEdgeStorage>(initialStatement) {
        override fun createStorage() = TaintedFactAccessEdgeStorage()
    }

    private class TaintedFactAccessEdgeStorage {
        private val sameInitialAccessEdges = Object2ObjectOpenHashMap<AccessPath.AccessNode?, EdgeNonUniverseExclusionMergingStorage>()

        fun getOrCreateNonUniverse(
            initialAccess: AccessPath.AccessNode?,
            maxInstIdx: Int
        ): EdgeNonUniverseExclusionMergingStorage = sameInitialAccessEdges.getOrPut(initialAccess) {
            EdgeNonUniverseExclusionMergingStorage(maxInstIdx)
        }
    }

    private class ZeroInitialFactEdgeStorage(initialStatement: JIRInst, private val maxInstIdx: Int) :
        EdgeStorage<ZeroInitialFactEdges>(initialStatement) {
        override fun createStorage(): ZeroInitialFactEdges = ZeroInitialFactEdges(maxInstIdx)
    }

    companion object {
        private fun instructionStorageSize(maxInstIdx: Int): Int = maxInstIdx + INST_IDX_SHIFT + 1
        private fun instructionStorageIdx(inst: JIRInst): Int = inst.location.index + INST_IDX_SHIFT

        private const val INST_IDX_SHIFT = 2
    }
}
