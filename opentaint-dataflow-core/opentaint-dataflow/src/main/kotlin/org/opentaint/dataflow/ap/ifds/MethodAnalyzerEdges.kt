package org.opentaint.dataflow.ap.ifds

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class MethodAnalyzerEdges(
    apManager: ApManager,
    private val methodEntryPoint: MethodEntryPoint,
    languageManager: LanguageManager
) {
    private val maxInstIdx = languageManager.getMaxInstIndex(methodEntryPoint.method)

    private val zeroToZeroEdges = SameInitialZeroFactEdges(maxInstIdx, languageManager)
    private val zeroToFactEdges = apManager.methodEdgesFinalApSet(methodEntryPoint.statement, maxInstIdx, languageManager)
    private val taintedToFactEdges = apManager.methodEdgesInitialToFinalApSet(methodEntryPoint.statement, maxInstIdx, languageManager)
    private val ndFactToFactEdges = apManager.methodEdgesNDInitialToFinalApSet(methodEntryPoint.statement, maxInstIdx, languageManager)

    fun add(edge: Edge): List<Edge> {
        check(edge.methodEntryPoint == methodEntryPoint)

        return addEdge(edge)
    }

    fun reachedStatements() = zeroToZeroEdges.reachedStatements()

    fun reachedStatementsWithFact(languageManager: LanguageManager): Map<CommonInst, Set<FinalFactAp>> {
        val result = hashMapOf<CommonInst, Set<FinalFactAp>>()
        for (instIdx in 0 until maxInstIdx + 1) {
            val stmt = languageManager.getInstByIndex(methodEntryPoint.method, instIdx)
            val z2f = mutableListOf<FinalFactAp>()
            val f2f = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
            val ndf2f = mutableListOf<Pair<Set<InitialFactAp>, FinalFactAp>>()

            zeroToFactEdges.collectApAtStatement(z2f, stmt)
            taintedToFactEdges.collectApAtStatement(f2f, stmt)
            ndFactToFactEdges.collectApAtStatement(ndf2f, stmt)

            val facts = hashSetOf<FinalFactAp>()
            facts.addAll(z2f)
            f2f.mapTo(facts) { it.second }
            ndf2f.mapTo(facts) { it.second }

            if (facts.isNotEmpty()) {
                result[stmt] = facts
            }
        }
        return result
    }

    private fun addEdge(edge: Edge): List<Edge> {
        when (edge) {
            is Edge.ZeroToZero -> {
                val edgeAdded = zeroToZeroEdges.addZeroEdge(edge.statement)
                return if (edgeAdded) listOf(edge) else emptyList()
            }

            is Edge.ZeroToFact -> {
                val storage = zeroToFactEdges

                val edgeAp = edge.factAp
                val addedAp = storage.add(edge.statement, edgeAp) ?: return emptyList()

                if (addedAp === edgeAp) return listOf(edge)

                return listOf(Edge.ZeroToFact(edge.methodEntryPoint, edge.statement, addedAp))
            }

            is Edge.FactToFact -> {
                return addTaintedFactEdge(edge)
            }

            is Edge.NDFactToFact -> {
                val initial = edge.initialFacts
                val finalAp = edge.factAp

                val (addedInitial, addedFinal) = ndFactToFactEdges.add(edge.statement, initial, finalAp) ?: return emptyList()

                return listOf(
                    Edge.NDFactToFact(
                        methodEntryPoint = edge.methodEntryPoint,
                        initialFacts = addedInitial,
                        statement = edge.statement,
                        factAp = addedFinal
                    )
                )
            }
        }
    }

    private fun addTaintedFactEdge(edge: Edge.FactToFact): List<Edge.FactToFact> {
        val initialAp = edge.initialFactAp
        val finalAp = edge.factAp

        val (addedInitial, addedFinal) = taintedToFactEdges.add(edge.statement, initialAp, finalAp) ?: return emptyList()

        if (addedInitial === initialAp && addedFinal === finalAp) return listOf(edge)

        return listOf(
            Edge.FactToFact(
                methodEntryPoint = edge.methodEntryPoint,
                initialFactAp = addedInitial,
                statement = edge.statement,
                factAp = addedFinal
            )
        )
    }

    fun allZeroToFactFactsAtStatement(statement: CommonInst, finalFactPattern: InitialFactAp): List<FinalFactAp> {
        val result = mutableListOf<FinalFactAp>()
        zeroToFactEdges.collectApAtStatement(result, statement, finalFactPattern)
        return result
    }

    fun allFactToFactFactsAtStatement(statement: CommonInst, finalFactPattern: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        val result = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        taintedToFactEdges.collectApAtStatement(result, statement, finalFactPattern)
        return result
    }

    fun allNDFactToFactFactsAtStatement(statement: CommonInst, finalFactPattern: InitialFactAp): List<Pair<Set<InitialFactAp>, FinalFactAp>> {
        val result = mutableListOf<Pair<Set<InitialFactAp>, FinalFactAp>>()
        ndFactToFactEdges.collectApAtStatement(result, statement, finalFactPattern)
        return result
    }

    fun allFactToFactFactsAtStatement(statement: CommonInst, initialFactAp: InitialFactAp, finalFactPattern: InitialFactAp): List<FinalFactAp> {
        val result = mutableListOf<FinalFactAp>()
        taintedToFactEdges.collectApAtStatement(result, statement, initialFactAp, finalFactPattern)
        return result
    }

    fun allNDFactToFactFactsAtStatement(statement: CommonInst, initialFacts: Set<InitialFactAp>, finalFactPattern: InitialFactAp): List<FinalFactAp> {
        val result = mutableListOf<FinalFactAp>()
        ndFactToFactEdges.collectApAtStatement(result, statement, initialFacts, finalFactPattern)
        return result
    }

    private class SameInitialZeroFactEdges(
        maxInstIdx: Int,
        private val languageManager: LanguageManager
    ) {
        private val edges = BitSet(instructionStorageSize(maxInstIdx))

        fun addZeroEdge(statement: CommonInst): Boolean {
            val edgeIdx = instructionStorageIdx(statement, languageManager)
            if (edges.get(edgeIdx)) return false

            edges.set(edgeIdx)
            return true
        }

        fun reachedStatements(): BitSet = edges
    }

    abstract class EdgeStorage<Storage : Any>(initialStatement: CommonInst) :
        AccessPathBaseStorage<Storage>(initialStatement) {
        private var locals: Int2ObjectOpenHashMap<Storage>? = null

        override fun getOrCreateLocal(idx: Int): Storage {
            val edges = locals ?: Int2ObjectOpenHashMap<Storage>().also { locals = it }
            return edges.getOrPut(idx) { createStorage() }
        }

        override fun findLocal(idx: Int): Storage? = locals?.get(idx)
        override fun forEachLocalValue(body: (AccessPathBase, Storage) -> Unit) {
            locals?.forEach { (idx, storage) -> body(AccessPathBase.LocalVar(idx), storage) }
        }

        private var constants: MutableMap<AccessPathBase.Constant, Storage>? = null

        override fun getOrCreateConstant(base: AccessPathBase.Constant): Storage {
            val edges = constants ?: Object2ObjectOpenHashMap<AccessPathBase.Constant, Storage>()
                .also { constants = it }

            return edges.getOrPut(base) { createStorage() }
        }

        override fun findConstant(base: AccessPathBase.Constant) = constants?.get(base)

        override fun forEachConstantValue(body: (AccessPathBase, Storage) -> Unit) {
            constants?.forEach { body(it.key, it.value) }
        }

        private var statics: MutableMap<AccessPathBase.ClassStatic, Storage>? = null

        override fun getOrCreateClassStatic(base: AccessPathBase.ClassStatic): Storage {
            val edges = statics ?: Object2ObjectOpenHashMap<AccessPathBase.ClassStatic, Storage>()
                .also { statics = it }

            return edges.getOrPut(base) { createStorage() }
        }

        override fun findClassStatic(base: AccessPathBase.ClassStatic) = statics?.get(base)

        override fun forEachClassStaticValue(body: (AccessPathBase, Storage) -> Unit) {
            statics?.forEach { body(it.key, it.value) }
        }
    }

    companion object {
        fun instructionStorageSize(maxInstIdx: Int): Int = maxInstIdx + 1
        fun instructionStorageIdx(inst: CommonInst, languageManager: LanguageManager): Int =
            languageManager.getInstIndex(inst)
    }
}
