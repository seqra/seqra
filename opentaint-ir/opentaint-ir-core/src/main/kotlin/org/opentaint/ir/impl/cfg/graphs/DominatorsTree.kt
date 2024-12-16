package org.opentaint.opentaint-ir.impl.cfg.graphs

import kotlinx.collections.immutable.toPersistentList
import kotlin.LazyThreadSafetyMode.NONE

open class DominatorsTree<NODE>(private val dominators: GraphDominators<NODE>) : Iterable<DominatorNode<NODE>> {

    open val graph = dominators.graph

    protected open val _heads = ArrayList<DominatorNode<NODE>>()
    protected open val _tails = ArrayList<DominatorNode<NODE>>()

    protected open val targets = HashMap<NODE, DominatorNode<NODE>>()

    init {
        buildTree()
    }

    val heads by lazy(NONE) { _heads.toPersistentList() }
    val tails by lazy(NONE) { _tails.toPersistentList() }

    val head: DominatorNode<NODE>? get() = _heads.firstOrNull()

    fun parentOf(node: DominatorNode<NODE>) = node.parent

    fun childsOf(node: DominatorNode<NODE>) = node.childrens.toPersistentList()

    fun predecessors(node: DominatorNode<NODE>) = graph.predecessors(node.target).map { nodeOf(it) }

    fun successors(node: DominatorNode<NODE>): List<DominatorNode<NODE>> {
        return graph.successors(node.target).map { nodeOf(it) }
    }

    fun isImmediateDominatorOf(idom: DominatorNode<NODE>, node: DominatorNode<NODE>): Boolean {
        return node.parent === idom
    }

    fun isDominatorOf(dom: DominatorNode<NODE>, node: DominatorNode<NODE>): Boolean {
        return dominators.isDominatedBy(node.target, dom.target)
    }

    fun nodeOf(inst: NODE): DominatorNode<NODE> {
        return targets[inst]
            ?: throw IllegalArgumentException("Dominator tree does not have a corresponding target for ($inst)")
    }

    override fun iterator(): Iterator<DominatorNode<NODE>> {
        return targets.values.iterator()
    }

    protected open fun buildTree() {
        for (inst in graph) {
            val node = findOrNewChild(inst)
            val parent = findOrNewParent(inst)
            if (parent == null) {
                _heads.add(node)
            } else {
                parent.addChild(node)
                node.parent = parent
            }
        }

        // identify the tail nodes
        _tails.addAll(filter { it.isTail })
        // potentially a long-lived object
        _heads.trimToSize()
        _tails.trimToSize()
    }

    open fun findOrNewChild(inst: NODE): DominatorNode<NODE> {
        var node = targets[inst]
        if (node == null) {
            node = DominatorNode(inst)
            targets[inst] = node
        }
        return node
    }

    protected open fun findOrNewParent(inst: NODE): DominatorNode<NODE>? {
        return dominators.immediateDominator(inst)?.let { findOrNewChild(it) }
    }

}

class DominatorNode<NODE>(
    var target: NODE,
    var parent: DominatorNode<NODE>? = null
) {
    internal var childrens: MutableList<DominatorNode<NODE>> = ArrayList()

    fun addChild(child: DominatorNode<NODE>): Boolean {
        return if (childrens.contains(child)) {
            false
        } else {
            childrens.add(child)
            true
        }
    }

    val isHead: Boolean get() = parent == null
    val isTail: Boolean get() = childrens.isEmpty()

}

/** Dominance frontier using Cytron's algorithm */
class CytronDominanceFrontier<NODE>(val tree: DominatorsTree<NODE>) {

    protected val frontiers = HashMap<DominatorNode<NODE>, List<DominatorNode<NODE>>>()

    init {
        tree.heads.forEach { bottomUpDispatch(it) }
        tree.graph.forEach {
            val dominatorNode = tree.findOrNewChild(it)
            if (!isFrontierKnown(dominatorNode)) {
                throw RuntimeException("Frontier not defined for node: $it")
            }
        }
    }

    fun frontierOf(node: DominatorNode<NODE>): List<DominatorNode<NODE>> {
        return frontiers[node]?.toPersistentList() ?: throw RuntimeException("Frontier not defined for node: $node")
    }

    fun isFrontierKnown(node: DominatorNode<NODE>?): Boolean {
        return frontiers.containsKey(node)
    }

    private fun bottomUpDispatch(node: DominatorNode<NODE>) {
        if (isFrontierKnown(node)) {
            return
        }
        for (child in tree.childsOf(node)) {
            if (!isFrontierKnown(child)) {
                bottomUpDispatch(child)
            }
        }
        process(node)
    }

    /**
     * Cytron et al., TOPLAS Oct. 91:
     * ```
     *      for each X in a bottom-up traversal of the dominator tree do
     *
     *      DF(X) < - null
     *      for each Y in Succ(X) do
     *        if (idom(Y)!=X) then DF(X) <- DF(X) U Y
     *      end
     *      for each Z in {idom(z) = X} do
     *        for each Y in DF(Z) do
     *              if (idom(Y)!=X) then DF(X) <- DF(X) U Y
     *        end
     *      end
     * ```
     */
    fun process(node: DominatorNode<NODE>) {
        val frontiers = tree.successors(node).filter { tree.isImmediateDominatorOf(node, it) }.toMutableSet()
        // local

        // up
        for (child in tree.childsOf(node)) {
            for (frontier in frontierOf(child)) {
                if (!tree.isImmediateDominatorOf(node, frontier)) {
                    frontiers.add(frontier)
                }
            }
        }
        this.frontiers[node] = frontiers.toList()
    }
}
