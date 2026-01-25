package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.tryAnyAccessorOrNull
import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.filter
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.getValue
import org.opentaint.dataflow.util.int2ObjectMap
import org.opentaint.dataflow.util.object2IntMap
import org.opentaint.dataflow.util.toBitSet
import java.util.BitSet

class AutomataInitialFactAbstraction(initialStatement: CommonInst) : InitialFactAbstraction {
    private val addedFacts = AccessGraphBasedStorage(initialStatement)

    override fun addAbstractedInitialFact(factAp: FinalFactAp): List<Pair<InitialFactAp, FinalFactAp>> =
        addAbstractedInitialFact(factAp as AccessGraphFinalFactAp)

    override fun registerNewInitialFact(factAp: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> =
        registerNewInitialFact(factAp as AccessGraphInitialFactAp)

    private fun addAbstractedInitialFact(fact: AccessGraphFinalFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        val basedFacts = addedFacts.getOrCreate(fact.base)
        return basedFacts.addAndAbstract(fact.access).map {
            Pair(
                AccessGraphInitialFactAp(fact.base, it, ExclusionSet.Empty),
                AccessGraphFinalFactAp(fact.base, it, ExclusionSet.Empty)
            )
        }
    }

    private fun registerNewInitialFact(fact: AccessGraphInitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        val addedBasedFacts = addedFacts.find(fact.base) ?: return emptyList()
        return addedBasedFacts.registerNew(fact.access, fact.exclusions).map {
            Pair(
                AccessGraphInitialFactAp(fact.base, it, ExclusionSet.Empty),
                AccessGraphFinalFactAp(fact.base, it, ExclusionSet.Empty)
            )
        }
    }

    private class AccessGraphBasedStorage(initialStatement: CommonInst) :
        MethodAnalyzerEdges.EdgeStorage<AccessGraphAbstraction>(initialStatement) {
        override fun createStorage(): AccessGraphAbstraction = AccessGraphAbstraction()
    }

    private class AccessGraphAbstraction {
        private val added = object2IntMap<AccessGraph>()
        private val addedGraphs = arrayListOf<AccessGraph>()
        private val addedIndex = GraphIndex()

        private val analyzed = object2IntMap<AccessGraph>()
        private val analyzedGraphs = arrayListOf<AccessGraph>()
        private val analyzedIndex = GraphIndex()

        private val analyzedExclusion = arrayListOf<BitSet>()
        private val analyzedExclusionIndex = int2ObjectMap<BitSet>()

        fun addAndAbstract(graph: AccessGraph): List<AccessGraph> = with(graph.manager) {
            if (added.isEmpty()) {
                added.put(graph, 0)
                addedGraphs.add(graph)
                addedIndex.add(graph, 0)

                analyzed.put(emptyGraph(), 0)
                analyzedGraphs.add(emptyGraph())
                analyzedIndex.add(emptyGraph(), 0)
                analyzedExclusion.add(BitSet())

                return listOf(emptyGraph())
            }

            added.getOrCreateIndex(graph) { addedGraphIdx ->
                check(addedGraphs.size == addedGraphIdx)
                addedGraphs.add(graph)
                addedIndex.add(graph, addedGraphIdx)
                return abstractAdded(graph)
            }

            return emptyList()
        }

        fun registerNew(graph: AccessGraph, exclusion: ExclusionSet): List<AccessGraph> = with(graph.manager) {
            if (exclusion !is ExclusionSet.Concrete) return emptyList()

            val analyzedGraphIdx = analyzed.getValue(graph)
            val analyzedGraphExclusion = analyzedExclusion[analyzedGraphIdx]
            val newAccessors = exclusion.set.toBitSet { it.idx }.filter { it !in analyzedGraphExclusion }

            if (newAccessors.isEmpty) return emptyList()

            analyzedGraphExclusion.or(newAccessors)
            newAccessors.forEach { accessor ->
                analyzedExclusionIndex.getOrPut(accessor, ::BitSet).set(analyzedGraphIdx)
            }

            return abstractAnalyzed(graph, newAccessors)
        }

        private fun AutomataApManager.abstractAdded(addedGraph: AccessGraph): List<AccessGraph> {
            val relevantAnalyzedGraphIndices = BitSet()
            addedGraph.accessors().forEach { accessor ->
                val graphsWithAccessorExcluded = analyzedExclusionIndex.get(accessor) ?: return@forEach
                relevantAnalyzedGraphIndices.or(graphsWithAccessorExcluded)
            }

            val analyzedGraphsWithDelta = analyzedIndex.localizeGraphHasDeltaWithIndexedGraph(addedGraph)
            relevantAnalyzedGraphIndices.and(analyzedGraphsWithDelta)

            val newAnalyzedGraphs = mutableListOf<AccessGraph>()
            relevantAnalyzedGraphIndices.forEach { analyzedGraphIdx ->
                val analyzedGraph = analyzedGraphs[analyzedGraphIdx]
                val exclusion = analyzedExclusion[analyzedGraphIdx]

                abstractGraph(
                    newAnalyzedGraphs,
                    analyzedGraph, addedGraph, exclusion
                )
            }

            return newAnalyzedGraphs.mapNotNull { it.registerNewAnalyzed() }
        }

        private fun AutomataApManager.abstractAnalyzed(
            analyzedGraph: AccessGraph,
            exclusion: BitSet,
        ): List<AccessGraph> {
            val relevantAddedGraphsIndices = addedIndex.localizeGraphsWithAccessors(exclusion)
            val addedGraphsWithDelta = addedIndex.localizeIndexedGraphHasDeltaWithGraph(analyzedGraph)
            relevantAddedGraphsIndices.and(addedGraphsWithDelta)

            val newAnalyzedGraphs = mutableListOf<AccessGraph>()
            relevantAddedGraphsIndices.forEach { addedGraphIdx ->
                val addedGraph = addedGraphs[addedGraphIdx]
                abstractGraph(
                    newAnalyzedGraphs,
                    analyzedGraph, addedGraph, exclusion
                )
            }

            return newAnalyzedGraphs.mapNotNull { it.registerNewAnalyzed() }
        }

        private fun AutomataApManager.abstractGraph(
            newAnalyzedGraphs: MutableList<AccessGraph>,
            analyzedGraph: AccessGraph,
            addedGraph: AccessGraph,
            exclusion: BitSet
        ) {
            for (delta in addedGraph.delta(analyzedGraph)) {
                if (delta.isEmpty()) continue

                exclusion.forEach { accessor ->
                    if (!delta.startsWith(accessor)) {
                        if (!delta.startsWith(anyAccessorIdx)) return@forEach
                        if (tryAnyAccessorOrNull(accessor.accessor) { true } != true) return@forEach
                    }

                    val singleAccessorGraph = emptyGraph().prepend(accessor)
                    val newGraph = analyzedGraph.concat(singleAccessorGraph)

                    newAnalyzedGraphs.add(newGraph)
                }
            }
        }

        private fun AccessGraph.registerNewAnalyzed(): AccessGraph? {
            analyzed.getOrCreateIndex(this) { idx ->
                check(analyzedGraphs.size == idx)
                analyzedGraphs.add(this)
                analyzedIndex.add(this, idx)

                check(analyzedExclusion.size == idx)
                analyzedExclusion.add(BitSet())
                return this
            }
            return null
        }
    }
}
