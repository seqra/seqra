package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

class AutomataInitialFactAbstraction : InitialFactAbstraction {
    private val addedFacts = hashMapOf<AccessPathBase, AccessGraphAbstraction>()

    override fun addAbstractedInitialFact(factAp: FinalFactAp): List<Pair<InitialFactAp, FinalFactAp>> =
        addAbstractedInitialFact(factAp as AccessGraphFinalFactAp)

    override fun registerNewInitialFact(factAp: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> =
        registerNewInitialFact(factAp as AccessGraphInitialFactAp)

    private fun addAbstractedInitialFact(fact: AccessGraphFinalFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        val basedFacts = addedFacts.getOrPut(fact.base) { AccessGraphAbstraction() }
        return basedFacts.addAndAbstract(fact.access).map {
            Pair(
                AccessGraphInitialFactAp(fact.base, it, ExclusionSet.Empty),
                AccessGraphFinalFactAp(fact.base, it, ExclusionSet.Empty)
            )
        }
    }

    private fun registerNewInitialFact(fact: AccessGraphInitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        val addedBasedFacts = addedFacts[fact.base] ?: return emptyList()
        return addedBasedFacts.registerNew(fact.access, fact.exclusions).map {
            Pair(
                AccessGraphInitialFactAp(fact.base, it, ExclusionSet.Empty),
                AccessGraphFinalFactAp(fact.base, it, ExclusionSet.Empty)
            )
        }
    }

    private class AccessGraphAbstraction(
        val added: MutableSet<AccessGraph> = hashSetOf(),
        val analyzed: MutableMap<AccessGraph, MutableSet<Accessor>> = hashMapOf()
    ) {
        fun addAndAbstract(graph: AccessGraph): List<AccessGraph> {
            if (added.isEmpty()) {
                added.add(graph)
                analyzed[AccessGraph.empty()] = hashSetOf()
                return listOf(AccessGraph.empty())
            }

            if (!added.add(graph)) return emptyList()

            return abstract(listOf(graph), analyzed.map { it.key to it.value })
        }

        fun registerNew(graph: AccessGraph, exclusion: ExclusionSet): List<AccessGraph> {
            if (exclusion !is ExclusionSet.Concrete) return emptyList()

            val analyzedGraphExclusion = analyzed[graph] ?: error("Unexpected graph: $graph")
            val newAccessors = exclusion.set.filterTo(hashSetOf()) { it !in analyzedGraphExclusion }
            analyzedGraphExclusion.addAll(exclusion.set)

            if (newAccessors.isEmpty()) return emptyList()

            return abstract(added, listOf(graph to newAccessors))
        }

        private fun abstract(
            addedGraphs: Iterable<AccessGraph>,
            analyzedGraphs: Iterable<Pair<AccessGraph, Set<Accessor>>>
        ): List<AccessGraph> {
            val result = mutableListOf<AccessGraph>()

            for (addedGraph in addedGraphs) {
                for ((analyzedGraph, exclusion) in analyzedGraphs) {
                    if (exclusion.isEmpty()) continue

                    for (delta in addedGraph.delta(analyzedGraph)) {
                        if (delta.isEmpty()) continue

                        for (accessor in exclusion) {
                            if (delta.startsWith(accessor)) {
                                val singleAccessorGraph = AccessGraph.empty().prepend(accessor)
                                val newGraph = analyzedGraph.concat(singleAccessorGraph)

                                if (analyzed.putIfAbsent(newGraph, hashSetOf()) == null) {
                                    result.add(newGraph)
                                }
                            }
                        }
                    }
                }
            }

            return result
        }
    }
}
