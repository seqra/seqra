package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntComparator
import it.unimi.dsi.fastutil.ints.IntIntMutablePair
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.forEachIntEntry

class IntDisjointSets(
    private val strategy: RankStrategy,
    private val parent: Int2IntOpenHashMap = Int2IntOpenHashMap(),
) : ImmutableIntDSU {
    interface RankStrategy: IntComparator {
        override fun compare(a: Int, b: Int): Int
    }

    init {
        parent.defaultReturnValue(NO_VALUE)
    }

    fun find(x: Int): Int {
        val p = parent.get(x)
        if (p == NO_VALUE) return x

        val root = find(p)
        parent.put(x, root)
        return root
    }

    fun union(x: Int, y: Int): Boolean {
        val u = find(x)
        val v = find(y)

        if (u == v) {
            return false
        }

        val cmp = strategy.compare(u, v)
        check(cmp != 0) { "Strategy cmp is 0 for non-equal elements" }

        if (cmp < 0) {
            merge(p = u, c = v)
        } else {
            merge(p = v, c = u)
        }

        return true
    }

    fun forEachElementInSet(setElement: Int, body: (Int) -> Unit?) {
        val setRoot = find(setElement)
        val allElements = allElements()

        if (!allElements.contains(setRoot)) {
            body(setRoot)
            return
        }

        allElements.forEachInt { e ->
            if (find(e) == setRoot) body(e) ?: return
        }
    }

    fun collectElementParentPairs(dst: MutableList<IntIntMutablePair>) {
        parent.forEachIntEntry { key, value ->
            dst += IntIntMutablePair(key, value)
        }
    }

    private fun merge(p: Int, c: Int) {
        parent.put(c, p)
    }

    fun removeAll(elements: IntOpenHashSet) = removeAll(elements) { _, _ -> /*do nothing*/ }

    fun prepareRemoveAll(
        elements: IntOpenHashSet,
        removedRoots: IntOpenHashSet
    ) = removeAll(elements) { element, status ->
        when (status) {
            is RemoveResult.EntireSetRemoved -> removedRoots.add(element)
            is RemoveResult.SetElementRemoved -> {}
            is RemoveResult.NewSetRepr -> {
                // keep old root as a child of new root
                parent.put(element, status.repr)
            }
        }
    }

    private inline fun removeAll(elements: IntOpenHashSet, onEachRemoved: (Int, RemoveResult) -> Unit) {
        val allElements = allElements()

        elements.forEach {
            val result = if (allElements.contains(it)) {
                removeExistingElement(it)
            } else {
                RemoveResult.EntireSetRemoved
            }

            onEachRemoved(it, result)
        }
    }

    sealed interface RemoveResult {
        data object EntireSetRemoved : RemoveResult
        data object SetElementRemoved : RemoveResult
        data class NewSetRepr(val repr: Int) : RemoveResult
    }

    private fun removeExistingElement(element: Int): RemoveResult {
        val children = IntArrayList()
        parent.forEachIntEntry { key, value ->
            if (value == element) {
                children.add(key)
            }
        }

        val rawElementParent = parent.remove(element)

        if (children.isEmpty) return RemoveResult.EntireSetRemoved

        val newRoot = if (rawElementParent != NO_VALUE) {
            rawElementParent
        } else {
            children.sort(strategy)
            children.getInt(0)
        }
        children.forEachInt { c ->
            parent.put(c, newRoot)
        }

        // one of children inherited rootness, so it can't be its own parent
        if (rawElementParent == NO_VALUE) {
            parent.remove(newRoot)
        }

        return if (rawElementParent != NO_VALUE) {
            RemoveResult.SetElementRemoved
        } else {
            RemoveResult.NewSetRepr(newRoot)
        }
    }

    fun allElements(): IntOpenHashSet {
        val allElements = IntOpenHashSet()
        allElements.addAll(parent.keys)
        allElements.addAll(parent.values)
        return allElements
    }

    override fun mutableCopy(): IntDisjointSets = clone()

    private fun clone(): IntDisjointSets = IntDisjointSets(strategy, parent.clone())

    fun allSets(): Collection<IntOpenHashSet> {
        val groups = Int2ObjectOpenHashMap<IntOpenHashSet>()
        allElements().forEachInt { element ->
            val root = find(element)
            val group = groups.get(root)
                ?: IntOpenHashSet().also { groups.put(root, it) }
            group.add(element)
        }
        return groups.values
    }

    override fun hashCode(): Int = error("HashCode is not supported")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntDisjointSets) return false

        if (this.parent == other.parent) return true

        val thisElements = this.allElements()
        if (thisElements != other.allElements()) return false

        thisElements.forEachInt { element ->
            if (this.find(element) != other.find(element)) return false
        }

        return true
    }

    companion object {
        private const val NO_VALUE = Int.MIN_VALUE
    }
}
