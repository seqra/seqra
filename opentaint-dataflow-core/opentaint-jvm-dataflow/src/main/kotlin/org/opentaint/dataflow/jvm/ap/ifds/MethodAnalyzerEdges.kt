package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesInitialToFinalApSet

class MethodAnalyzerEdges(
    private val apManager: ApManager,
    private val methodEntryPoint: MethodEntryPoint
) {
    private val maxInstIdx = methodEntryPoint.method.instList.maxOf { it.location.index }

    private val zeroToZeroEdges = SameInitialZeroFactEdges(maxInstIdx)
    private val zeroToFactEdges = Object2ObjectOpenHashMap<TaintMark, MethodEdgesFinalApSet>()
    private val taintedToFactSameEdges = Object2ObjectOpenHashMap<TaintMark, MethodEdgesInitialToFinalApSet>()
    private val taintedToFactDifferentEdges by lazy {
        Object2ObjectOpenHashMap<Pair<TaintMark, TaintMark>, MethodEdgesInitialToFinalApSet>()
    }

    fun add(edge: Edge): List<Edge> {
        check(edge.methodEntryPoint == methodEntryPoint)

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
                    apManager.methodEdgesFinalApSet(methodEntryPoint.statement, maxInstIdx)
                }

                val edgeAp = edge.fact.ap
                val addedAp = storage.add(edge.statement, edgeAp) ?: return emptyList()

                if (addedAp === edgeAp) return listOf(edge)

                val addedFact = edge.fact.changeAP(addedAp)
                return listOf(Edge.ZeroToFact(edge.methodEntryPoint, edge.statement, addedFact))
            }

            is Edge.FactToFact -> {
                return addTaintedFactEdge(edge)
            }
        }
    }

    private fun addTaintedFactEdge(edge: Edge.FactToFact): List<Edge.FactToFact> {
        val edgeStorage = if (edge.initialFact.mark == edge.fact.mark) {
            taintedToFactSameEdges.getOrPut(edge.initialFact.mark) {
                apManager.methodEdgesInitialToFinalApSet(methodEntryPoint.statement, maxInstIdx)
            }
        } else {
            taintedToFactDifferentEdges.getOrPut(edge.initialFact.mark to edge.fact.mark) {
                apManager.methodEdgesInitialToFinalApSet(methodEntryPoint.statement, maxInstIdx)
            }
        }

        val initialAp = edge.initialFact.ap
        val finalAp = edge.fact.ap

        val (addedInitial, addedFinal) = edgeStorage.add(edge.statement, initialAp, finalAp) ?: return emptyList()

        if (addedInitial === initialAp && addedFinal === finalAp) return listOf(edge)

        return listOf(
            Edge.FactToFact(
                methodEntryPoint = edge.methodEntryPoint,
                initialFact = edge.initialFact.changeAP(addedInitial),
                statement = edge.statement,
                fact = edge.fact.changeAP(addedFinal)
            )
        )
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

    abstract class EdgeStorage<Storage : Any>(initialStatement: JIRInst) :
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

    companion object {
        fun instructionStorageSize(maxInstIdx: Int): Int = maxInstIdx + INST_IDX_SHIFT + 1
        fun instructionStorageIdx(inst: JIRInst): Int = inst.location.index + INST_IDX_SHIFT

        private const val INST_IDX_SHIFT = 2
    }
}
