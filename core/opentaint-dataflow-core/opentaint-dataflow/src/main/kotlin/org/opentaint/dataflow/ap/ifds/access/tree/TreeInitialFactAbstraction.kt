package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.ExclusionSet.Empty
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.ReversedApNode
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.createNodeFromReversedAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.foldRight
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.create
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.createAbstractNodeFromReversedAp
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isAlwaysUnrollNext
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class TreeInitialFactAbstraction(
    private val apManager: TreeApManager
): InitialFactAbstraction {
    private val initialFacts = MethodSameMarkInitialFact(apManager, hashMapOf())
    private val interner = AccessTreeSoftInterner(apManager)

    override fun addAbstractedInitialFact(
        factAp: FinalFactAp,
        typeChecker: FactTypeChecker
    ): List<Pair<InitialFactAp, FinalFactAp>> {
        factAp as AccessTree

        // note: we can ignore fact exclusions here
        val facts = initialFacts.getOrPut(factAp.base)
        val addedFact = facts.addInitialFact(factAp.access, interner) ?: return emptyList()

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, addedFact, abstractFacts, typeChecker)
        return abstractFacts
    }

    override fun registerNewInitialFact(
        factAp: InitialFactAp,
        typeChecker: FactTypeChecker
    ): List<Pair<InitialFactAp, FinalFactAp>> {
        factAp as AccessPath

        val facts = initialFacts.getOrPut(factAp.base)

        val excludedAccessors = IntOpenHashSet()
        when (val ex = factAp.exclusions) {
            is ExclusionSet.Concrete -> ex.set.forEach {
                with(apManager) { excludedAccessors.add(it.idx) }
            }
            Empty -> {
                // do nothing
            }
            ExclusionSet.Universe -> error("Unexpected universe exclusion")
        }

        if (!facts.addAnalyzedInitialFact(factAp.access, excludedAccessors)) return emptyList()

        val abstractFacts = mutableListOf<Pair<InitialFactAp, FinalFactAp>>()
        addAbstractInitialFact(facts, factAp.base, facts.allAddedFacts(), abstractFacts, typeChecker)
        return abstractFacts
    }

    private fun addAbstractInitialFact(
        facts: MethodSameBaseInitialFact,
        concreteFactBase: AccessPathBase,
        initialConcreteFact: AccessTreeNode,
        abstractFacts: MutableList<Pair<InitialFactAp, FinalFactAp>>,
        typeChecker: FactTypeChecker
    ) {
        var concreteFactAccess = initialConcreteFact
        while (true) {
            val unrollRequests = mutableListOf<AnyAccessorUnrollRequest>()
            abstractAccessPath(facts.analyzed, concreteFactAccess, unrollRequests) { abstractAccess ->
                apManager.cancellation.checkpoint()

                val initialAbstractAccessNode = apManager.createNodeFromReversedAp(abstractAccess)
                val initialAbstractAp = AccessPath(apManager, concreteFactBase, initialAbstractAccessNode, Empty)

                val apAccess = apManager.createAbstractNodeFromReversedAp(abstractAccess)
                val ap = AccessTree(apManager, concreteFactBase, apAccess, Empty)

                facts.addAnalyzedInitialFact(initialAbstractAccessNode, exclusions = IntOpenHashSet())
                abstractFacts.add(initialAbstractAp to ap)
            }

            concreteFactAccess = facts.unrollAnyAccessors(unrollRequests, typeChecker)
                ?: break
        }
    }

    private fun MethodSameBaseInitialFact.unrollAnyAccessors(
        unrollRequests: List<AnyAccessorUnrollRequest>,
        typeChecker: FactTypeChecker
    ): AccessTreeNode? {
        if (unrollRequests.isEmpty()) return null

        val unrollStrategy = apManager.anyAccessorUnrollStrategy

        val newFacts = mutableListOf<AccessTreeNode>()
        for (unrollRequest in unrollRequests) {
            apManager.cancellation.checkpoint()

            unrollRequest.accessors.forEachInt { accessor ->
                val accessorInstance = with(apManager) { accessor.accessor }
                if (!unrollStrategy.unrollAccessor(accessorInstance)) return@forEachInt

                val accessorFilter = unrollRequest.currentAp.createFilter(typeChecker)
                val accessorStatus = accessorFilter.check(accessorInstance)
                when (accessorStatus) {
                    is FactTypeChecker.FilterResult.Accept,
                    is FactTypeChecker.FilterResult.FilterNext -> {
                        // accept
                    }

                    is FactTypeChecker.FilterResult.Reject -> return@forEachInt
                }

                val prefix = ReversedApNode(accessor, unrollRequest.currentAp)

                val nodeFilter = prefix.createFilter(typeChecker)
                val filteredNode = unrollRequest.node.filterAccessNode(nodeFilter) ?: return@forEachInt

                newFacts += filteredNode.addReversedApParents(prefix)
                    ?: return@forEachInt
            }
        }

        val mergedNewFacts = newFacts.reduceOrNull { acc, f -> acc.mergeAdd(f) }
            ?: return null

        return addInitialFact(mergedNewFacts, interner)
    }

    private fun ReversedApNode?.createFilter(typeChecker: FactTypeChecker): FactTypeChecker.FactApFilter {
        val accessors = mutableListOf<Accessor>()
        foldRight(Unit) { accessor, _ ->
            with(apManager) {
                accessors.add(accessor.accessor)
            }
        }
        return typeChecker.accessPathFilter(accessors.asReversed())
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
        val accessors: IntOpenHashSet,
    )

    private inline fun abstractAccessPath(
        initialAnalyzedTrieRoot: AccessPathTrieNode,
        initialAdded: AccessTreeNode,
        unrollRequests: MutableList<AnyAccessorUnrollRequest>,
        crossinline createAbstractAp: (ReversedApNode?) -> Unit
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

            if (state.added.containsAnyAccessor()) {
                val unrollAccessors = state.analyzedTrieRoot.unrollAccessors(currentLevelExclusions)
                if (unrollAccessors.isNotEmpty()) {
                    unrollRequests += AnyAccessorUnrollRequest(state.currentAp, state.added, unrollAccessors)
                }
            }

            if (state.added.isFinal) {
                val node = apManager.create()
                abstractAccessPath(state.analyzedTrieRoot, FINAL_ACCESSOR_IDX, node, state.currentAp, unprocessed, createAbstractAp)
            }

            state.added.forEachAccessor { accessor, node ->
                abstractAccessPath(state.analyzedTrieRoot, accessor, node, state.currentAp, unprocessed, createAbstractAp)
            }
        }
    }

    private inline fun abstractAccessPath(
        analyzedTrieRoot: AccessPathTrieNode,
        accessor: AccessorIdx,
        addedNode: AccessTreeNode,
        currentAp: ReversedApNode?,
        unprocessed: MutableList<AbstractionState>,
        crossinline createAbstractAp: (ReversedApNode?) -> Unit
    ) {
        val node = analyzedTrieRoot.child(accessor)
        if (node != null) {
            val apWithAccessor = ReversedApNode(accessor, currentAp)
            if (accessor.isAlwaysUnrollNext()) {
                abstractNextAccessPath(addedNode, apWithAccessor) {
                    createAbstractAp(it)
                }
            } else {
                unprocessed += AbstractionState(node, addedNode, apWithAccessor)
            }
            return
        }

        val exclusions = analyzedTrieRoot.exclusions()

        // We have no excludes -> continue with the most abstract fact
        if (exclusions == null) {
            createAbstractAp(currentAp)
            return
        }

        // Concrete: a.b.* E
        // Added: a.* S
        if (!exclusions.contains(accessor)) {
            // We have no conflict with added facts
            return
        }

        // We have initial fact that exclude {b} and we have no a.b fact yet
        if (!accessor.isAlwaysUnrollNext()) {
            // Return a.b.* {}
            createAbstractAp(ReversedApNode(accessor, currentAp))
            return
        }

        val apWithAccessor = ReversedApNode(accessor, currentAp)
        abstractNextAccessPath(addedNode, apWithAccessor) {
            createAbstractAp(it)
        }
    }

    private fun abstractNextAccessPath(
        addedNode: AccessTreeNode,
        currentAp: ReversedApNode,
        createAbstractAp: (ReversedApNode) -> Unit
    ) {
        if (addedNode.containsAnyAccessor()) {
            TODO("Any after unroll-next is not supported yet")
        }

        if (addedNode.isFinal) {
            createAbstractAp(ReversedApNode(FINAL_ACCESSOR_IDX, currentAp))
        }

        addedNode.forEachAccessor { accessor, node ->
            val nextAp = ReversedApNode(accessor, currentAp)
            if (!accessor.isAlwaysUnrollNext()) {
                createAbstractAp(nextAp)
            } else {
                abstractNextAccessPath(node, nextAp, createAbstractAp)
            }
        }
    }

    private class MethodSameMarkInitialFact(
        val manager: TreeApManager,
        val facts: MutableMap<AccessPathBase, MethodSameBaseInitialFact>
    ) {
        fun getOrPut(base: AccessPathBase): MethodSameBaseInitialFact = facts.getOrPut(base) {
            MethodSameBaseInitialFact(manager, added = null, AccessPathTrieNode.empty())
        }
    }

    private class MethodSameBaseInitialFact(
        val manager: TreeApManager,
        private var added: AccessTreeNode?,
        val analyzed: AccessPathTrieNode
    ) {
        fun allAddedFacts(): AccessTreeNode = added ?: manager.create()

        fun addInitialFact(ap: AccessTreeNode, interner: AccessTreeSoftInterner): AccessTreeNode? {
            val currentNode = added ?: manager.create()
            val (updatedAddedNode, addedInitial) = currentNode.mergeAddDelta(ap)

            if (addedInitial == null) return null

            this.added = internIfRequired(interner, updatedAddedNode)

            intern(interner)

            return addedInitial
        }

        private var operationsBeforeIntern = INTERN_RATE

        private fun internIfRequired(interner: AccessTreeSoftInterner, node: AccessTreeNode): AccessTreeNode {
            if (node.size < SIZE_TO_FORCE_INTERN) return node
            return interner.intern(node)
        }

        private fun intern(interner: AccessTreeSoftInterner) {
            val current = added ?: return

            if (operationsBeforeIntern-- > 0) return
            if (current.size < INTERN_SIZE_REQUIREMENT) return

            operationsBeforeIntern = INTERN_RATE
            added = interner.intern(current)
        }

        fun addAnalyzedInitialFact(ap: AccessPath.AccessNode?, exclusions: IntOpenHashSet): Boolean =
            AccessPathTrieNode.add(analyzed, ap, exclusions)
    }

    class AccessPathTrieNode {
        private var children: Int2ObjectOpenHashMap<AccessPathTrieNode>? = null
        private var terminals: IntOpenHashSet? = null
        private var unrolled: IntOpenHashSet? = null

        fun exclusions(): IntOpenHashSet? = terminals

        fun child(accessor: AccessorIdx): AccessPathTrieNode? =
            children?.get(accessor)

        private fun getTerminals(): IntOpenHashSet =
            terminals ?: IntOpenHashSet().also { terminals = it }

        private fun getChildren(): Int2ObjectOpenHashMap<AccessPathTrieNode> =
            children ?: Int2ObjectOpenHashMap<AccessPathTrieNode>().also { children = it }

        fun unrollAccessors(accessors: IntOpenHashSet): IntOpenHashSet {
            val current = unrolled ?: IntOpenHashSet().also { unrolled = it }
            val result = IntOpenHashSet()
            accessors.forEachInt {
                if (current.add(it)) result.add(it)
            }
            return result
        }

        companion object {
            fun empty() = AccessPathTrieNode()

            fun add(
                initialRoot: AccessPathTrieNode,
                initialAccess: AccessPath.AccessNode?,
                exclusions: IntOpenHashSet,
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

    companion object {
        private const val INTERN_RATE = 100
        private const val INTERN_SIZE_REQUIREMENT = 1_000
        private const val SIZE_TO_FORCE_INTERN = 100_000
    }
}
