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
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.foldRight
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class TreeInitialFactAbstraction(
    private val apManager: TreeApManager
): InitialFactAbstraction {
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

        val excludedAccessors = when (val ex = factAp.exclusions) {
            Empty -> emptySet()
            is ExclusionSet.Concrete -> ex.set
            ExclusionSet.Universe -> error("Unexpected universe exclusion")
        }

        if (!facts.addAnalyzedInitialFact(factAp.access, excludedAccessors)) return emptyList()

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, facts.allAddedFacts(), abstractFacts)
        return abstractFacts
    }

    private fun addAbstractInitialFact(
        facts: MethodSameBaseInitialFact,
        concreteFactBase: AccessPathBase,
        initialConcreteFact: AccessTree.AccessNode,
        abstractFacts: MutableList<Pair<InitialFactAp, FinalFactAp>>
    ) {
        var concreteFactAccess = initialConcreteFact
        while (true) {

            val unrollRequests = mutableListOf<AnyAccessorUnrollRequest>()
            abstractAccessPath(facts.analyzed, concreteFactAccess, unrollRequests) { abstractAccess ->
                val initialAbstractAccessNode = AccessPath.AccessNode.createNodeFromReversedAp(abstractAccess)
                val initialAbstractAp = AccessPath(apManager, concreteFactBase, initialAbstractAccessNode, Empty)

                val apAccess = AccessTreeNode.createAbstractNodeFromReversedAp(abstractAccess)
                val ap = AccessTree(apManager, concreteFactBase, apAccess, Empty)

                facts.addAnalyzedInitialFact(initialAbstractAccessNode, exclusions = emptySet())
                abstractFacts.add(initialAbstractAp to ap)
            }

            concreteFactAccess = facts.unrollAnyAccessors(unrollRequests)
                ?: break
        }
    }

    private fun MethodSameBaseInitialFact.unrollAnyAccessors(
        unrollRequests: List<AnyAccessorUnrollRequest>
    ): AccessTreeNode? {
        if (unrollRequests.isEmpty()) return null

        val unrollStrategy = apManager.anyAccessorUnrollStrategy

        val newFacts = mutableListOf<AccessTreeNode>()
        for (unrollRequest in unrollRequests) {
            for (accessor in unrollRequest.accessors) {
                if (!unrollStrategy.unrollAccessor(accessor)) continue

                val prefix = ReversedApNode(accessor, unrollRequest.currentAp)
                newFacts += unrollRequest.node.addReversedApParents(prefix)
                    ?: continue
            }
        }

        val mergedNewFacts = newFacts.reduceOrNull { acc, f -> acc.mergeAdd(f) }
            ?: return null

        return addInitialFact(mergedNewFacts)
    }

    private fun AccessTreeNode.addReversedApParents(ap: ReversedApNode): AccessTreeNode? =
        ap.foldRight(this) { accessor, node ->
            node.addParentIfPossible(accessor) ?: return null
        }

    data class AbstractionState(
        val analyzedTrieRoot: AccessPathTrieNode,
        val added: AccessTreeNode,
        val currentAp: ReversedApNode?,
    )

    data class AnyAccessorUnrollRequest(
        val currentAp: ReversedApNode?,
        val node: AccessTreeNode,
        val accessors: List<Accessor>,
    )

    private inline fun abstractAccessPath(
        initialAnalyzedTrieRoot: AccessPathTrieNode,
        initialAdded: AccessTreeNode,
        unrollRequests: MutableList<AnyAccessorUnrollRequest>,
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

            if (state.added.isAnyAccessor) {
                val unrollAccessors = state.analyzedTrieRoot.unrollAccessors(currentLevelExclusions)
                if (unrollAccessors.isNotEmpty()) {
                    unrollRequests += AnyAccessorUnrollRequest(state.currentAp, state.added, unrollAccessors)
                }
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
        val node = analyzedTrieRoot.child(accessor)
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
            val (updatedAddedNode, addedInitial) = currentNode.mergeAddDelta(ap)

            if (addedInitial == null) return null

            this.added = updatedAddedNode
            return addedInitial
        }

        fun addAnalyzedInitialFact(ap: AccessPath.AccessNode?, exclusions: Set<Accessor>): Boolean =
            AccessPathTrieNode.add(analyzed, ap, exclusions)
    }

    class AccessPathTrieNode {
        private var children: MutableMap<Accessor, AccessPathTrieNode>? = null
        private var terminals: MutableSet<Accessor>? = null
        private var unrolled: MutableSet<Accessor>? = null

        fun exclusions(): MutableSet<Accessor>? = terminals

        fun child(accessor: Accessor): AccessPathTrieNode? =
            children?.get(accessor)

        private fun getTerminals(): MutableSet<Accessor> =
            terminals ?: hashSetOf<Accessor>().also { terminals = it }

        private fun getChildren(): MutableMap<Accessor, AccessPathTrieNode> =
            children ?: hashMapOf<Accessor, AccessPathTrieNode>().also { children = it }

        fun unrollAccessors(accessors: Set<Accessor>): List<Accessor> {
            val current = unrolled ?: hashSetOf<Accessor>().also { unrolled = it }
            return accessors.filter { current.add(it) }
        }

        companion object {
            fun empty() = AccessPathTrieNode()

            fun add(
                initialRoot: AccessPathTrieNode,
                initialAccess: AccessPath.AccessNode?,
                exclusions: Set<Accessor>
            ): Boolean {
                var trieNode = initialRoot
                var access = initialAccess

                while (true) {
                    if (access == null) {
                        var modified = trieNode.terminals == null
                        modified = modified or trieNode.getTerminals().addAll(exclusions)
                        return modified
                    }

                    val key = access.accessor
                    trieNode = trieNode.getChildren().getOrPut(key) { empty() }
                    access = access.next
                }
            }
        }
    }
}
