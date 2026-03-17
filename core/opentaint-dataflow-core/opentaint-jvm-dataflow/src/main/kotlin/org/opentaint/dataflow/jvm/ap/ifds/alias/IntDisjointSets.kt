package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

class IntDisjointSets(
    private val parent: Int2IntOpenHashMap = Int2IntOpenHashMap(),
    private val rank: Int2IntOpenHashMap = Int2IntOpenHashMap(),
    private var hash: Int = 0,
) : ImmutableIntDSU {
    fun find(x: Int): Int {
        if (!parent.containsKey(x)) return x
        val p = parent[x]
        val root = find(p)
        parent[x] = root
        updateHash(x)
        return root
    }

    private fun getRank(x: Int): Int {
        if (!rank.containsKey(x)) return 0
        return rank[x]
    }

    fun union(x: Int, y: Int) {
        val u = find(x)
        val v = find(y)

        if (u == v) {
            return
        }

        val rankU = getRank(u)
        val rankV = getRank(v)

        when {
            rankU > rankV -> merge(u, v)
            rankU < rankV -> merge(v, u)
            else -> {
                merge(u, v)
                rank[u] = rankU + 1
            }
        }
    }

    fun forEachElementInSet(setElement: Int, body: (Int) -> Unit) {
        val setRoot = find(setElement)
        val allElements = allElements()

        if (setRoot !in allElements) {
            body(setRoot)
            return
        }

        for (e in allElements) {
            if (find(e) == setRoot) body(e)
        }
    }

    fun unionSets(others: List<IntDisjointSets>) {
        for (other in others) {
            for ((a, b) in other.parent) {
                union(a, b)
            }
        }
    }

    private fun updateHash(element: Int) {
        hash += element.hashCode()
    }

    private fun updateHashRemoveElement(element: Int) {
        hash -= element.hashCode()
    }

    private fun merge(x: Int, y: Int) {
        parent[y] = x
    }

    fun removeAll(pred: (Int) -> Boolean) {
        val elementsToRemove = allElements().filter { pred(it) }
        elementsToRemove.forEach { removeExistingElement(it) }
    }

    private fun Int2IntOpenHashMap.removeOrNull(x: Int): Int? {
        if (!containsKey(x)) return null
        return remove(x)
    }

    private fun removeExistingElement(element: Int) {
        val children = parent.filter { it.value == element }.keys.toList()

        val elementParent = parent.removeOrNull(element)
        val elementRank = rank.removeOrNull(element)

        updateHashRemoveElement(element)

        if (children.isEmpty()) return

        val newRoot = elementParent ?: children.first()

        for (c in children) {
            parent[c] = newRoot
        }
        // one of children inherited rootness, so it can't be its own parent
        if (elementParent == null) {
            parent.remove(newRoot)
            rank[newRoot] = elementRank ?: 1
        }
    }

    private fun allElements(): MutableSet<Int> {
        val allElements = parent.keys.toHashSet()
        allElements.addAll(parent.values)
        return allElements
    }

    override fun mutableCopy(): IntDisjointSets = clone()

    private fun clone(): IntDisjointSets =
        IntDisjointSets(parent.toMap(Int2IntOpenHashMap()), rank.toMap(Int2IntOpenHashMap()), hash)

    fun allSets(): Collection<List<Int>> =
        allElements().groupBy { find(it) }.values

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntDisjointSets) return false

        if (this.parent == other.parent) return true

        val thisPartitions = this.allSets().map { it.toSet() }.toSet()
        val otherPartitions = other.allSets().map { it.toSet() }.toSet()

        return thisPartitions == otherPartitions
    }
}
