package org.opentaint.dataflow.ap.ifds.access.automata

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.dataflow.ap.ifds.access.automata.SmallAgGroup.Companion.SMALL_GROUP_SIZE
import org.opentaint.dataflow.util.containsAll
import java.util.BitSet

sealed interface AccessGraphSet {
    val graphSize: Int
    val setSize: Int

    fun add(graph: AccessGraph): AccessGraphSet?

    fun toList(): List<AccessGraph>

    fun toList(dst: MutableList<AccessGraph>)

    companion object {
        fun create(): AccessGraphSet = SmallAgSet()
    }
}

class SmallAgSet : AccessGraphSet {
    private val graphs = ObjectOpenHashSet<AccessGraph>()

    override val graphSize: Int get() = graphs.sumOf { it.size }
    override val setSize: Int get() = graphs.size

    override fun add(graph: AccessGraph): AccessGraphSet? {
        if (graphs.contains(graph)) return null

        val graphsIterator = graphs.iterator()
        while (graphsIterator.hasNext()) {
            val ag = graphsIterator.next()
            if (ag.containsAll(graph)) {
                return null
            }

            if (graph.containsAll(ag)) {
                graphsIterator.remove()
            }
        }

        graphs.add(graph)

        if (graphs.size < SMALL_SET_THRESHOLD) return this

        val compressed = CompressedAgSet()
        graphs.forEach { compressed.add(it) }
        return compressed
    }

    override fun toList(): List<AccessGraph> = graphs.toList()

    override fun toList(dst: MutableList<AccessGraph>) {
        dst.addAll(graphs)
    }

    companion object {
        private const val SMALL_SET_THRESHOLD = 16
    }
}

class CompressedAgSet : AccessGraphSet {
    private val graphs = Object2ObjectOpenHashMap<BitSet, Object2ObjectOpenHashMap<BitSet, PackedAccessGraphGroup>>()

    override val graphSize: Int
        get() = graphs.values.sumOf { groups ->
            groups.values.sumOf { it.unpack().graphSize }
        }

    override val setSize: Int
        get() = graphs.values.sumOf { groups ->
            groups.values.sumOf { it.unpack().groupSize }
        }

    override fun toList(): List<AccessGraph> {
        val result = mutableListOf<AccessGraph>()
        graphs.values.forEach { groups ->
            groups.values.flatMapTo(result) { it.unpack().toList() }
        }
        return result
    }

    override fun toList(dst: MutableList<AccessGraph>) {
        graphs.values.forEach { groups ->
            groups.values.flatMapTo(dst) { it.unpack().toList() }
        }
    }

    override fun add(graph: AccessGraph): AccessGraphSet? {
        val graphIAS = graph.initialAccessorSet()
        val graphAccessors = graph.accessorSet()

        val initialSuccGroupsIterator = graphs.iterator()
        while (initialSuccGroupsIterator.hasNext()) {
            val entry = initialSuccGroupsIterator.next()
            val (groupsIAS, groups) = entry

            if (groupsIAS.containsAll(graphIAS)) {
                if (filterGroups(groups, graph, graphAccessors, checkGroupContainsGraph = true)) return null
            }

            if (graphIAS.containsAll(groupsIAS)) {
                filterGroups(groups, graph, graphAccessors, checkGroupContainsGraph = false)
            }

            if (groups.isEmpty()) {
                initialSuccGroupsIterator.remove()
            }
        }

        val groups = graphs.getOrPut(graphIAS) {
            Object2ObjectOpenHashMap()
        }

        val currentGroup = groups[graphAccessors]
        var modifiedGroup = currentGroup?.unpack() ?: AccessGraphGroup.create()
        modifiedGroup = modifiedGroup.add(graph)

        if (modifiedGroup !== currentGroup) {
            groups[graphAccessors] = modifiedGroup.pack()
        }

        return this
    }

    private fun filterGroups(
        groups: MutableMap<BitSet, PackedAccessGraphGroup>,
        graph: AccessGraph,
        graphAccessors: BitSet,
        checkGroupContainsGraph: Boolean
    ): Boolean {
        val groupsIterator = groups.iterator()
        while (groupsIterator.hasNext()) {
            val entry = groupsIterator.next()
            val (groupAc, packedGroup) = entry
            val group = packedGroup.unpack()

            if (checkGroupContainsGraph) {
                if (groupAc.containsAll(graphAccessors)) {
                    if (group.filter(graph, checkGroupContainsGraph = true)) return true
                }
            }

            if (graphAccessors.containsAll(groupAc)) {
                group.filter(graph, checkGroupContainsGraph = false)
            }

            val compressedGroup = group.compress()
            if (compressedGroup == null) {
                groupsIterator.remove()
            } else {
                entry.setValue(compressedGroup.pack())
            }
        }

        return false
    }

    private fun AccessGraph.accessorSet(): BitSet =
        accessors()

    private fun AccessGraph.initialAccessorSet(): BitSet =
        stateSuccessors(initial)
}

typealias PackedAccessGraphGroup = Any

private fun PackedAccessGraphGroup.unpack(): AccessGraphGroup {
    if (this is AccessGraphGroup) return this

    val graph = this as AccessGraph
    return SmallAgGroup().add(graph)
}

private fun AccessGraphGroup.pack(): PackedAccessGraphGroup {
    if (this !is SmallAgGroup) return this
    val singleGraph = this.singleGroupElement() ?: return this
    return singleGraph
}

sealed interface AccessGraphGroup {
    val graphSize: Int
    val groupSize: Int
    fun filter(graph: AccessGraph, checkGroupContainsGraph: Boolean): Boolean
    fun compress(): AccessGraphGroup?
    fun add(graph: AccessGraph): AccessGraphGroup
    fun toList(): List<AccessGraph>

    companion object {
        fun create(): AccessGraphGroup = SmallAgGroup()
    }
}

class SmallAgGroup : AccessGraphGroup {
    private val graphs = arrayOfNulls<AccessGraph>(SMALL_GROUP_SIZE)

    override val graphSize: Int get() = graphs.sumOf { it?.size ?: 0 }
    override val groupSize: Int get() = graphs.count { it != null }

    override fun filter(graph: AccessGraph, checkGroupContainsGraph: Boolean): Boolean {
        for (i in graphs.indices) {
            val ag = graphs[i] ?: continue

            if (checkGroupContainsGraph) {
                if (ag.containsAll(graph)) {
                    return true
                }
            }

            if (graph.containsAll(ag)) {
                graphs[i] = null
            }
        }

        return false
    }

    override fun compress(): AccessGraphGroup? {
        if (graphs.any { it != null }) return this
        return null
    }

    fun singleGroupElement(): AccessGraph? = graphs.singleOrNull { it != null }

    override fun add(graph: AccessGraph): AccessGraphGroup {
        for (i in graphs.indices) {
            if (graphs[i] == null) {
                graphs[i] = graph
                return this
            }
        }

        val hugeGroup = HugeAgGroup()
        for (element in graphs) {
            val ag = element ?: continue
            hugeGroup.add(ag)
        }
        hugeGroup.add(graph)

        return hugeGroup
    }

    override fun toList(): List<AccessGraph> = graphs.filterNotNull()

    override fun toString(): String = "(group: $groupSize size: $graphSize)"

    companion object {
        const val SMALL_GROUP_SIZE = 4
    }
}

class HugeAgGroup : AccessGraphGroup {
    private val graphs = ObjectOpenHashSet<AccessGraph>()

    override val graphSize: Int get() = graphs.sumOf { it.size }
    override val groupSize: Int get() = graphs.size

    override fun filter(graph: AccessGraph, checkGroupContainsGraph: Boolean): Boolean {
        val iterator = graphs.iterator()
        while (iterator.hasNext()) {
            val ag = iterator.next()
            if (checkGroupContainsGraph) {
                if (ag.containsAll(graph)) {
                    return true
                }
            }

            if (graph.containsAll(ag)) {
                iterator.remove()
            }
        }

        return false
    }

    override fun compress(): AccessGraphGroup {
        if (graphs.size > SMALL_GROUP_SIZE) return this

        val smallAgGroup = SmallAgGroup()
        graphs.forEach { smallAgGroup.add(it) }
        return smallAgGroup
    }

    override fun add(graph: AccessGraph): AccessGraphGroup {
        graphs.add(graph)
        return this
    }

    override fun toList(): List<AccessGraph> = graphs.toList()

    override fun toString(): String = "(group: ${graphs.size} size: $graphSize)"
}
