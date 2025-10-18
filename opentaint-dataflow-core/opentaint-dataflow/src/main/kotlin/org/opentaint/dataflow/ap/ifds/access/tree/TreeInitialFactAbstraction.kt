package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.ExclusionSet.Empty
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.ReversedApNode
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class TreeInitialFactAbstraction: InitialFactAbstraction {
    private val initialFacts = MethodSameMarkInitialFact(hashMapOf())

    override fun addAbstractedInitialFact(factAp: FinalFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        factAp as AccessTree

        // note: we can ignore fact exclusions here
        val facts = initialFacts.getOrPut(factAp.base)
        val addedFact = facts.addInitialFact(factAp.access) ?: return emptyList()

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, addedFact, abstractFacts)
        return abstractFacts
    }

    override fun registerNewInitialFact(factAp: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>> {
        factAp as AccessPath

        val facts = initialFacts.getOrPut(factAp.base)
        facts.addAnalyzedInitialFact(factAp.access, factAp.exclusions)

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, facts.allAddedFacts(), abstractFacts)
        return abstractFacts
    }

    private fun addAbstractInitialFact(
        facts: MethodSameBaseInitialFact,
        concreteFactBase: AccessPathBase,
        concreteFactAccess: AccessTreeNode,
        abstractFacts: MutableList<Pair<InitialFactAp, FinalFactAp>>
    ) {
        abstractAccessPath(facts.analyzed, concreteFactAccess) { abstractAccess ->
            val initialAbstractAccessNode = AccessPath.AccessNode.createNodeFromReversedAp(abstractAccess)
            val initialAbstractAp = AccessPath(concreteFactBase, initialAbstractAccessNode, Empty)

            val apAccess = AccessTreeNode.createAbstractNodeFromReversedAp(abstractAccess)
            val ap = AccessTree(concreteFactBase, apAccess, Empty)

            facts.addAnalyzedInitialFact(initialAbstractAccessNode, Empty)
            abstractFacts.add(initialAbstractAp to ap)
        }
    }

    data class AbstractionState(
        val analyzedTrieRoot: AccessPathTrieNode,
        val added: AccessTreeNode,
        val currentAp: ReversedApNode?,
    )

    private inline fun abstractAccessPath(
        initialAnalyzedTrieRoot: AccessPathTrieNode,
        initialAdded: AccessTreeNode,
        createAbstractAp: (ReversedApNode?) -> Unit
    ) {
        val unprocessed = mutableListOf<AbstractionState>()
        unprocessed.add(AbstractionState(initialAnalyzedTrieRoot, initialAdded, currentAp = null))

        while (unprocessed.isNotEmpty()) {
            val state = unprocessed.removeLast()

            val currentLevelExclusions = state.analyzedTrieRoot.exclusions()
            if (currentLevelExclusions == null) {
                createAbstractAp(state.currentAp)
                continue
            }

            if (state.added.isFinal) {
                val node = AccessTreeNode.create()
                abstractAccessPath(state.analyzedTrieRoot, FinalAccessor, node, state.currentAp, unprocessed, createAbstractAp)
            }

            state.added.forEachAccessor { accessor, node ->
                abstractAccessPath(state.analyzedTrieRoot, accessor, node, state.currentAp, unprocessed, createAbstractAp)
            }
        }
    }

    private inline fun abstractAccessPath(
        analyzedTrieRoot: AccessPathTrieNode,
        accessor: Accessor,
        addedNode: AccessTreeNode,
        currentAp: ReversedApNode?,
        unprocessed: MutableList<AbstractionState>,
        createAbstractAp: (ReversedApNode?) -> Unit
    ) {
        val node = analyzedTrieRoot.children[accessor]
        if (node == null) {
            val exclusions = analyzedTrieRoot.exclusions()

            // We have no excludes -> continue with the most abstract fact
            if (exclusions == null) {
                createAbstractAp(currentAp)
                return
            }

            // Concrete: a.b.* E
            // Added: a.* S
            if (exclusions.contains(accessor)) {
                // We have initial fact that exclude {b} and we have no a.b fact yet
                // Return a.b.* {}

                createAbstractAp(ReversedApNode(accessor, currentAp))

                return
            }

            // We have no conflict with added facts
            return
        }

        val apWithAccessor = ReversedApNode(accessor, currentAp)
        unprocessed += AbstractionState(node, addedNode, apWithAccessor)
    }

    private class MethodSameMarkInitialFact(val facts: MutableMap<AccessPathBase, MethodSameBaseInitialFact>) {
        fun getOrPut(base: AccessPathBase): MethodSameBaseInitialFact = facts.getOrPut(base) {
            MethodSameBaseInitialFact(added = null, AccessPathTrieNode.empty())
        }
    }

    private class MethodSameBaseInitialFact(
        private var added: AccessTreeNode?,
        val analyzed: AccessPathTrieNode
    ) {
        fun allAddedFacts(): AccessTreeNode = added ?: AccessTreeNode.create()

        fun addInitialFact(ap: AccessTreeNode): AccessTreeNode? {
            val currentNode = added ?: AccessTreeNode.create()
            val addedNode = currentNode.mergeAdd(ap)

            if (addedNode === this.added) return null
            this.added = addedNode
            return addedNode
        }

        fun addAnalyzedInitialFact(ap: AccessPath.AccessNode?, exclusions: ExclusionSet) {
            AccessPathTrieNode.add(analyzed, ap, exclusions)
        }
    }

    class AccessPathTrieNode(
        val children: MutableMap<Accessor, AccessPathTrieNode>,
        private var terminals: ExclusionSet?
    ) {
        fun exclusions(): ExclusionSet? = terminals

        companion object {
            fun empty() = AccessPathTrieNode(hashMapOf(), terminals = null)

            fun add(
                initialRoot: AccessPathTrieNode,
                initialAccess: AccessPath.AccessNode?,
                exclusions: ExclusionSet
            ) {
                var trieNode = initialRoot
                var access = initialAccess

                while (true) {
                    if (access == null) {
                        val terminals = trieNode.terminals
                        if (terminals == null) {
                            trieNode.terminals = exclusions
                        } else {
                            trieNode.terminals = terminals.union(exclusions)
                        }
                        return
                    }

                    val key = access.accessor
                    trieNode = trieNode.children.getOrPut(key) { empty() }
                    access = access.next
                }
            }
        }
    }
}
