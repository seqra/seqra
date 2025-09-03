package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.ExclusionSet.Empty
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.cactus.AccessCactus.AccessNode as AccessCactusNode

class CactusInitialFactAbstraction: InitialFactAbstraction {
    private val initialFacts = MethodSameMarkInitialFact(hashMapOf())

    override fun addAbstractedInitialFact(factAp: FinalFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        factAp as AccessCactus

        // note: we can ignore fact exclusions here
        val facts = initialFacts.getOrPut(factAp.base)
        val addedFact = facts.addInitialFact(factAp.access) ?: return emptyList()

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, addedFact, abstractFacts)
        return abstractFacts
    }

    override fun registerNewInitialFact(factAp: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        factAp as AccessPathWithCycles

        val facts = initialFacts.getOrPut(factAp.base)
        facts.addAnalyzedInitialFact(factAp.access, factAp.exclusions)

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, facts.allAddedFacts(), abstractFacts)
        return abstractFacts
    }

    private fun addAbstractInitialFact(
        facts: MethodSameBaseInitialFact,
        concreteFactBase: AccessPathBase,
        concreteFactAccess: AccessCactusNode,
        abstractFacts: MutableList<Pair<InitialFactAp, FinalFactAp>>
    ) {
        abstractAccessPath(facts.analyzed, concreteFactAccess,
            AccessPathWithCycles.AccessNode.Builder()
        ) { builder, abstractExcludes ->
            val initialAbstractAccessNode = builder.build()
            val initialAbstractAp = AccessPathWithCycles(concreteFactBase, initialAbstractAccessNode, abstractExcludes)

            val apAccess = AccessCactusNode.createAbstractNodeFromAp(initialAbstractAccessNode)
            val ap = AccessCactus(concreteFactBase, apAccess, abstractExcludes)

            facts.addAnalyzedInitialFact(initialAbstractAccessNode, abstractExcludes)
            abstractFacts.add(initialAbstractAp to ap)
        }
    }

    private fun abstractAccessPath(
        analyzedTrieRoot: AccessPathTrieNode,
        added: AccessCactusNode,
        currentAp: AccessPathWithCycles.AccessNode.Builder,
        createAbstractAp: (AccessPathWithCycles.AccessNode.Builder, ExclusionSet) -> Unit
    ) {
        val currentLevelExclusions = analyzedTrieRoot.exclusions()
        if (currentLevelExclusions == null) {
            createAbstractAp(currentAp, Empty)
            return
        }

        if (added.isFinal) {
            val node = AccessCactusNode.create()
            abstractAccessPath(analyzedTrieRoot, FinalAccessor, added.cycles, node, currentAp, createAbstractAp)
        }

        added.forEachChild { accessor, node ->
            abstractAccessPath(analyzedTrieRoot, accessor, added.cycles, node, currentAp, createAbstractAp)
        }
    }

    private fun abstractAccessPath(
        analyzedTrieRoot: AccessPathTrieNode,
        accessor: Accessor,
        cycles: List<Cycle>,
        addedNode: AccessCactusNode,
        currentAp: AccessPathWithCycles.AccessNode.Builder,
        createAbstractAp: (AccessPathWithCycles.AccessNode.Builder, ExclusionSet) -> Unit
    ) {
        val node = analyzedTrieRoot.children[accessor to cycles]
        if (node == null) {
            val exclusions = analyzedTrieRoot.exclusions()

            // We have no excludes -> continue with the most abstract fact
            if (exclusions == null) {
                createAbstractAp(currentAp, Empty)
                return
            }

            val possibleNextAccessors = cycles.map { it.first() } + listOf(accessor)
            // Concrete: a.b.* E
            // Added: a.* S
            if (possibleNextAccessors.any { exclusions.contains(it) }) {
                // We have initial fact that exclude {b} and we have no a.b fact yet
                // Return a.b.* {}

                currentAp.append(accessor, cycles)
                createAbstractAp(currentAp, Empty)
                currentAp.removeLast()

                return
            }

            // We have no conflict with added facts
            return
        }

        currentAp.append(accessor, cycles)
        abstractAccessPath(node, addedNode, currentAp, createAbstractAp)
        currentAp.removeLast()
    }

    private class MethodSameMarkInitialFact(val facts: MutableMap<AccessPathBase, MethodSameBaseInitialFact>) {
        fun getOrPut(base: AccessPathBase): MethodSameBaseInitialFact = facts.getOrPut(base) {
            MethodSameBaseInitialFact(added = null, AccessPathTrieNode.empty())
        }
    }

    class MethodSameBaseInitialFact(
        private var added: AccessCactusNode?,
        val analyzed: AccessPathTrieNode
    ) {
        fun allAddedFacts(): AccessCactusNode = added ?: AccessCactusNode.create()

        fun addInitialFact(ap: AccessCactusNode): AccessCactusNode? {
            val currentNode = added ?: AccessCactusNode.create()
            val addedNode = currentNode.mergeAdd(ap)

            if (addedNode == this.added) return null
            this.added = addedNode
            return addedNode
        }

        fun addAnalyzedInitialFact(ap: AccessPathWithCycles.AccessNode?, exclusions: ExclusionSet) {
            AccessPathTrieNode.add(analyzed, (ap ?: emptyList()).iterator(), exclusions)
        }
    }

    class AccessPathTrieNode(
        val children: MutableMap<Pair<Accessor, List<Cycle>>, AccessPathTrieNode>,
        private var terminals: ExclusionSet?
    ) {
        fun exclusions(): ExclusionSet? = terminals

        companion object {
            fun empty() = AccessPathTrieNode(hashMapOf(), terminals = null)

            fun add(
                root: AccessPathTrieNode,
                node: Iterator<Pair<Accessor, List<Cycle>>>,
                exclusions: ExclusionSet
            ) {
                if (!node.hasNext()) {
                    val terminals = root.terminals
                    if (terminals == null) {
                        root.terminals = exclusions
                    } else {
                        root.terminals = terminals.union(exclusions)
                    }
                    return
                }

                val key = node.next()
                val child = root.children.getOrPut(key) { empty() }
                add(child, node, exclusions)
            }
        }
    }
}
