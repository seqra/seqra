package org.opentaint.dataflow.jvm.ap.ifds.access.tree

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet.Empty
import org.opentaint.dataflow.jvm.ap.ifds.FinalAccessor
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessPath.AccessNode.Companion.iterator
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

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
        abstractAccessPath(facts.analyzed, concreteFactAccess, mutableListOf()) { abstractAccess, abstractExcludes ->
            val initialAbstractAccessNode = AccessPath.AccessNode.createNodeFromAp(abstractAccess.iterator())
            val initialAbstractAp = AccessPath(concreteFactBase, initialAbstractAccessNode, abstractExcludes)

            val apAccess = AccessTreeNode.createAbstractNodeFromAp(abstractAccess.iterator())
            val ap = AccessTree(concreteFactBase, apAccess, abstractExcludes)

            facts.addAnalyzedInitialFact(initialAbstractAccessNode, abstractExcludes)
            abstractFacts.add(initialAbstractAp to ap)
        }
    }

    private fun abstractAccessPath(
        analyzedTrieRoot: AccessPathTrieNode,
        added: AccessTreeNode,
        currentAp: MutableList<Accessor>,
        createAbstractAp: (List<Accessor>, ExclusionSet) -> Unit
    ) {
        val currentLevelExclusions = analyzedTrieRoot.exclusions()
        if (currentLevelExclusions == null) {
            createAbstractAp(currentAp, Empty)
            return
        }

        if (added.isFinal) {
            val node = AccessTreeNode.create()
            abstractAccessPath(analyzedTrieRoot, FinalAccessor, node, currentAp, createAbstractAp)
        }

        added.forEachAccessor { accessor, node ->
            abstractAccessPath(analyzedTrieRoot, accessor, node, currentAp, createAbstractAp)
        }
    }

    private fun abstractAccessPath(
        analyzedTrieRoot: AccessPathTrieNode,
        accessor: Accessor,
        addedNode: AccessTreeNode,
        currentAp: MutableList<Accessor>,
        createAbstractAp: (List<Accessor>, ExclusionSet) -> Unit
    ) {
        val node = analyzedTrieRoot.children[accessor]
        if (node == null) {
            val exclusions = analyzedTrieRoot.exclusions()

            // We have no excludes -> continue with the most abstract fact
            if (exclusions == null) {
                createAbstractAp(currentAp, Empty)
                return
            }

            // Concrete: a.b.* E
            // Added: a.* S
            if (exclusions.contains(accessor)) {
                // We have initial fact that exclude {b} and we have no a.b fact yet
                // Return a.b.* {}

                currentAp.add(accessor)
                createAbstractAp(currentAp, Empty)
                currentAp.removeLast()

                return
            }

            // We have no conflict with added facts
            return
        }

        currentAp.add(accessor)
        abstractAccessPath(node, addedNode, currentAp, createAbstractAp)
        currentAp.removeLast()
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
            AccessPathTrieNode.add(analyzed, ap.iterator(), exclusions)
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
                root: AccessPathTrieNode,
                access: Iterator<Accessor>,
                exclusions: ExclusionSet
            ) {
                if (!access.hasNext()) {
                    val terminals = root.terminals
                    if (terminals == null) {
                        root.terminals = exclusions
                    } else {
                        root.terminals = terminals.union(exclusions)
                    }
                    return
                }

                val key = access.next()
                val child = root.children.getOrPut(key) { empty() }
                add(child, access, exclusions)
            }
        }
    }
}
