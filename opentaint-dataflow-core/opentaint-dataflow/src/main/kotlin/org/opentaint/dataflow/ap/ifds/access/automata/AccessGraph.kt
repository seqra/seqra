package org.opentaint.dataflow.ap.ifds.access.automata

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2LongMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectIterator
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.util.PersistentArrayBuilder
import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.PersistentInt2LongMap
import org.opentaint.dataflow.util.PersistentInt2LongMap.emptyPersistentInt2LongMap
import org.opentaint.dataflow.util.add
import org.opentaint.dataflow.util.bitSetOf
import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.filter
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.removeFirst
import org.opentaint.dataflow.util.toBitSet
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.BitSet

private typealias NodeMarker = Int

private typealias NodePair = Long
private typealias AgEdge = NodePair

private typealias NodeMap = Int2IntOpenHashMap

private const val NO_EDGE = PersistentInt2LongMap.NO_VALUE
private const val NO_NODE = -1

private fun nodeMap(): NodeMap = Int2IntOpenHashMap().also { it.defaultReturnValue(NO_NODE) }

private typealias NodeSet = BitSet

private fun nodeSet(expectedSize: Int): NodeSet = BitSet(expectedSize)

private const val MASK = 0xffffffff

private inline val NodePair.first: NodeMarker get() = (this ushr Int.SIZE_BITS).toInt()
private inline val NodePair.second: NodeMarker get() = (this and MASK).toInt()

@Suppress("NOTHING_TO_INLINE")
private inline operator fun NodePair.component1(): NodeMarker = first

@Suppress("NOTHING_TO_INLINE")
private inline operator fun NodePair.component2(): NodeMarker = second

@Suppress("NOTHING_TO_INLINE")
private inline fun NodePair(first: NodeMarker, second: NodeMarker): NodePair =
    (first.toLong() shl Int.SIZE_BITS) or (second.toLong() and MASK)

@Suppress("NOTHING_TO_INLINE")
private inline fun AgEdge(from: NodeMarker, to: NodeMarker): AgEdge =
    NodePair(from, to)

private inline val AgEdge.from: NodeMarker get() = this.first
private inline val AgEdge.to: NodeMarker get() = this.second

private fun LongArrayList.removeLast(): Long = removeLong(lastIndex)
private fun IntArrayList.removeLast(): Int = removeInt(lastIndex)

class AccessGraph(
    val manager: AutomataApManager,
    val initial: NodeMarker,
    val final: NodeMarker,
    private val edges: PersistentInt2LongMap,
    private val nodeSucc: Array<PersistentBitSet?>,
    private val nodePred: Array<PersistentBitSet?>,
) {
    private val numNodes: Int get() = nodeSucc.size

    val size: Int get() = edges.size

    private val hash: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { dfsHash() }

    fun getAllOwnAccessors() =
        edges.keys.mapTo(hashSetOf()) {
            with (manager) {
                it.accessor
            }
        }

    fun getInitialSuccessorsAccessors() = buildSet {
        stateSuccessors(initial).forEach { accessorIdx ->
            with(manager) { add(accessorIdx.accessor) }
        }
    }

    private fun dfsHash(): Int {
        var hash = 0
        val visited = BitSet()

        val unprocessed = IntArrayList()
        unprocessed.add(initial)

        while (!unprocessed.isEmpty) {
            val node = unprocessed.removeLast()
            val successors = nodeSucc[node] ?: continue

            hash *= 17
            hash += successors.hashCode()

            if (!visited.add(node)) continue

            successors.forEach { accessor ->
                val edge = edges.get(accessor)
                unprocessed.add(edge.to)
            }
        }

        return hash
    }

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccessGraph) return false

        if (edges.size != other.edges.size) return false
        if (edges.keys != other.edges.keys) return false

        return graphEqualsDfs(other)
    }

    private fun graphEqualsDfs(other: AccessGraph): Boolean {
        val nodeMapping = nodeMap()

        val unprocessed = LongArrayList()
        unprocessed.add(NodePair(initial, other.initial))

        while (unprocessed.isNotEmpty()) {
            val (thisNode, otherNode) = unprocessed.removeLast()

            val currentMapping = nodeMapping.put(thisNode, otherNode)
            if (currentMapping != NO_NODE) {
                if (currentMapping != otherNode) return false
                continue
            }

            val nextAccessors = this.nodeSuccessors(thisNode)
            val otherNextAccessors = other.nodeSuccessors(otherNode)
            if (nextAccessors != otherNextAccessors) return false

            nextAccessors.forEach { accessor ->
                val thisNext = this.getStateSuccessor(thisNode, accessor)
                val otherNext = other.getStateSuccessor(otherNode, accessor)
                unprocessed.add(NodePair(thisNext, otherNext))
            }
        }

        return nodeMapping[final] == other.final
    }

    override fun toString(): String {
        val printablePaths = printablePaths()
        if (printablePaths.isEmpty()) return "(EMPTY)"

        return printablePaths.joinToString("\n") { path ->
            buildString {
                for ((idx, elem) in path.withIndex()) {
                    append(repr(elem.first))
                    append(" --- ${elem.second} ---> ")
                    if (idx == path.lastIndex) {
                        append(repr(elem.third))
                    }
                }
            }
        }
    }

    private fun repr(s: NodeMarker): String {
        val initial = if (initial == s) " INITIAL" else ""
        val final = if (final == s) " FINAL" else ""
        return "(${s}${initial}$final)"
    }

    private fun printablePaths(): List<List<Triple<NodeMarker, Accessor, NodeMarker>>> {
        val currentPath = mutableListOf<Triple<NodeMarker, Accessor, NodeMarker>>()
        val printablePaths = mutableListOf<List<Triple<NodeMarker, Accessor, NodeMarker>>>(currentPath)
        val visitedNodes = nodeSet(numNodes)
        visitedNodes.add(initial)
        printablePaths(initial, printablePaths, currentPath, visitedNodes)
        printablePaths.removeAll { it.isEmpty() }
        printablePaths.sortByDescending { it.size }
        return printablePaths
    }

    private fun printablePaths(
        node: NodeMarker,
        paths: MutableList<List<Triple<NodeMarker, Accessor, NodeMarker>>>,
        currentPath: MutableList<Triple<NodeMarker, Accessor, NodeMarker>>,
        visitedNodes: NodeSet
    ): Unit = with(manager) {
        var current = currentPath
        stateSuccessors(node).forEach { accessor ->
            val successor = getStateSuccessor(node, accessor)
            val edge = Triple(node, accessor.accessor, successor)
            if (!visitedNodes.add(successor)) {
                // loop
                paths += listOf(edge)
            } else {
                current += edge
                printablePaths(successor, paths, current, visitedNodes)
                current = mutableListOf()
                paths.add(current)
            }
        }
    }

    fun initialNodeIsFinal(): Boolean = initial == final

    fun isEmpty(): Boolean = edges.isEmpty()

    fun stateSuccessors(state: NodeMarker): PersistentBitSet =
        nodeSucc[state] ?: error("No successors")

    fun nodeSuccessors(node: NodeMarker): PersistentBitSet =
        nodeSucc[node] ?: error("Missed node")

    fun nodePredecessors(node: NodeMarker): PersistentBitSet =
        nodePred[node] ?: error("Missed node")

    fun getEdge(accessor: AccessorIdx): AgEdge? =
        edges.get(accessor).takeIf { it != NO_EDGE }

    fun getEdgeFrom(edge: AgEdge): NodeMarker = edge.from

    fun getEdgeTo(edge: AgEdge): NodeMarker = edge.to

    fun iterateEdges(): ObjectIterator<Int2LongMap.Entry> =
        edges.int2LongEntrySet().fastIterator()

    private fun allNodes(): NodeSet =
        nodeSucc.allNonNullIndicesSet()

    fun accessors(): BitSet =
        edges.keys.toBitSet { it }

    fun getStateSuccessor(state: NodeMarker, accessor: AccessorIdx): NodeMarker =
        getStateSuccessorUnsafe(state, accessor).also { check(it != NO_NODE) { "Missed state" } }

    private fun getStateSuccessorUnsafe(state: NodeMarker, accessor: AccessorIdx): NodeMarker {
        val edge = edges.get(accessor)
        return if (edge != NO_EDGE && edge.from == state) edge.to else NO_NODE
    }

    private fun create(initial: NodeMarker, final: NodeMarker): AccessGraph =
        AccessGraph(manager, initial, final, edges, nodeSucc, nodePred)

    fun startsWith(accessor: AccessorIdx): Boolean =
        getStateSuccessorUnsafe(initial, accessor) != NO_NODE

    fun read(accessor: AccessorIdx): AccessGraph? {
        val newInitial = getStateSuccessorUnsafe(initial, accessor)
        if (newInitial == NO_NODE) return null
        if (newInitial == initial) return this
        return create(newInitial, final).removeUnreachableNodes()
    }

    fun prepend(accessor: AccessorIdx): AccessGraph {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.prepend(accessor)

        if (mutableResult === mutableCopy) return this

        return mutableResult.persist()
    }

    fun clear(accessor: AccessorIdx): AccessGraph? {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.clear(bitSetOf(accessor)) ?: return null

        if (mutableResult === mutableCopy) return this

        return mutableResult.persist()
            .removeUnreachableNodes()
    }

    fun filter(exclusionSet: ExclusionSet): AccessGraph? = when (exclusionSet) {
        ExclusionSet.Empty -> this
        ExclusionSet.Universe -> if (initialNodeIsFinal()) manager.emptyGraph() else null
        is ExclusionSet.Concrete -> with(manager) {
            filter(exclusionSet.set.toBitSet { it.idx })
        }
    }

    private fun filter(exclusionSet: BitSet): AccessGraph? {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.clear(exclusionSet) ?: return null

        if (mutableResult === mutableCopy) return this

        return mutableResult.persist()
            .removeUnreachableNodes()
    }

    fun concat(other: AccessGraph): AccessGraph {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.concat(other)

        if (mutableResult === mutableCopy) return this

        return mutableResult.persist()
    }

    fun delta(other: AccessGraph): List<AccessGraph> {
        if (other.isEmpty()) return splitOutEmptyDelta(this)
        if (this.isEmpty()) return emptyList()

        val finalNodeMapping = matchGraphPrefix(other)
        if (finalNodeMapping == NO_NODE) return emptyList()

        val deltaNode = create(finalNodeMapping, final)
        val resultDelta = deltaNode.removeUnreachableNodes()
            ?: return emptyList()

        return splitOutEmptyDelta(resultDelta)
    }

    private fun splitOutEmptyDelta(delta: AccessGraph): List<AccessGraph> {
        if (delta.initialNodeIsFinal() && !delta.isEmpty()) {
            return listOf(delta, manager.emptyGraph())
        }

        return listOf(delta)
    }

    fun containsAll(other: AccessGraph): Boolean {
        if (other.isEmpty()) return this.initial == this.final
        if (this.isEmpty()) return false

        val finalNodeMapping = matchGraphPrefix(other)
        if (finalNodeMapping == NO_NODE) return false
        return finalNodeMapping == this.final
    }

    private fun matchGraphPrefix(other: AccessGraph): NodeMarker {
        check(manager === other.manager)

        val nodeMapping = nodeMap()

        val visitedNodes = LongOpenHashSet()
        val unprocessed = LongArrayList()
        unprocessed.add(NodePair(this.initial, other.initial))

        while (unprocessed.isNotEmpty()) {
            val nodePair = unprocessed.removeLast()
            if (!visitedNodes.add(nodePair)) continue
            val (thisNode, otherNode) = nodePair

            val currentMapping = nodeMapping.put(otherNode, thisNode)
            if (currentMapping != NO_NODE && currentMapping != thisNode) {
                return NO_NODE
            }

            other.stateSuccessors(otherNode).forEach { accessor ->
                var thisSuccessor = this.getStateSuccessorUnsafe(thisNode, accessor)

                if (thisSuccessor == NO_NODE) {
                    val anySuccessor = this.getStateSuccessorUnsafe(thisNode, manager.anyAccessorIdx)
                    if (anySuccessor == NO_NODE) return NO_NODE

                    val accessorObj = with(manager) { accessor.accessor }
                    if (!AnyAccessor.containsAccessor(accessorObj)) return NO_NODE

                    thisSuccessor = anySuccessor
                }

                val otherSuccessor = other.getStateSuccessor(otherNode, accessor)
                unprocessed.add(NodePair(thisSuccessor, otherSuccessor))
            }
        }

        val finalNodeMapping = nodeMapping.get(other.final)
        if (finalNodeMapping == NO_NODE) {
            return NO_NODE
        }

        return finalNodeMapping
    }

    fun splitDelta(other: AccessGraph): List<Pair<AccessGraph, AccessGraph>> {
        if (other.isEmpty()) return listOf(manager.emptyGraph() to this)
        if (this.isEmpty()) return emptyList()

        val splitPoints = findGraphSplit(other)

        val result = mutableListOf<Pair<AccessGraph, AccessGraph>>()
        splitPoints.forEach { splitNode: NodeMarker ->
            val matchedPrefix = AccessGraph(manager, initial, splitNode, edges, nodeSucc, nodePred)
                .removeUnreachableNodes()
                ?: return@forEach

            if (!other.containsAll(matchedPrefix)) return@forEach

            val deltaSuffix = AccessGraph(manager, splitNode, final, edges, nodeSucc, nodePred)
                .removeUnreachableNodes()
                ?: return@forEach

            result += matchedPrefix to deltaSuffix
        }

        return result
    }

    private fun findGraphSplit(other: AccessGraph): NodeSet {
        check(manager === other.manager)

        val splitPoints = NodeSet()

        val visitedNodes = LongOpenHashSet()
        val unprocessed = LongArrayList()
        unprocessed.add(NodePair(this.initial, other.initial))

        while (unprocessed.isNotEmpty()) {
            val nodePair = unprocessed.removeLast()
            if (!visitedNodes.add(nodePair)) continue
            val (thisNode, otherNode) = nodePair

            if (otherNode == other.final) {
                splitPoints.add(thisNode)
            }

            other.stateSuccessors(otherNode).forEach { accessor ->
                val thisSuccessor = this.getStateSuccessorUnsafe(thisNode, accessor)
                if (thisSuccessor == NO_NODE) return@forEach

                val otherSuccessor = other.getStateSuccessor(otherNode, accessor)
                unprocessed.add(NodePair(thisSuccessor, otherSuccessor))
            }
        }

        return splitPoints
    }

    fun merge(other: AccessGraph): AccessGraph {
        check(manager === other.manager)

        val mutableCopy = mutable()
        val mergedMutable = mutableCopy.merge(other)
        val mergeResult = mergedMutable.persist()
        return mergeResult
    }

    fun filter(filter: FactTypeChecker.FactApFilter): AccessGraph? {
        val rejectedAccessors = BitSet()
        stateSuccessors(initial).forEach { accessor ->
            val accessorObj = with(manager) { accessor.accessor }
            when (val status = filter.check(accessorObj)) {
                FactTypeChecker.FilterResult.Accept -> return@forEach

                FactTypeChecker.FilterResult.Reject -> {
                    rejectedAccessors.add(accessor)
                }

                is FactTypeChecker.FilterResult.FilterNext -> {
                    val successor = getStateSuccessor(initial, accessor)
                    val allNextRejected = filterNextNodes(successor, status.filter)
                    if (allNextRejected) {
                        rejectedAccessors.add(accessor)
                    }
                }
            }
        }

        if (rejectedAccessors.isEmpty) return this

        val mutableCopy = mutable()
        val filtered = mutableCopy.clear(rejectedAccessors)

        if (filtered === mutableCopy) return this
        if (filtered == null) return null

        return filtered.persist().removeUnreachableNodes()
    }

    private fun filterNextNodes(
        node: NodeMarker,
        filter: FactTypeChecker.FactApFilter,
    ): Boolean {
        // final node can be an abstraction point
        if (node == final) return false

        var allSuccessorsRejected = true
        stateSuccessors(node).forEach { accessor ->
            val accessorObj = with(manager) { accessor.accessor }
            when (val status = filter.check(accessorObj)) {
                FactTypeChecker.FilterResult.Accept -> {
                    allSuccessorsRejected = false
                }

                FactTypeChecker.FilterResult.Reject -> return@forEach

                is FactTypeChecker.FilterResult.FilterNext -> {
                    val successor = getStateSuccessor(node, accessor)
                    val allNextRejected = filterNextNodes(successor, status.filter)
                    if (!allNextRejected) {
                        allSuccessorsRejected = false
                    }
                }
            }
        }
        return allSuccessorsRejected
    }

    private fun removeUnreachableNodes(): AccessGraph? {
        val unprocessed = nodeSet(numNodes)

        val reachableSuccessors = nodeSet(numNodes)
        traverse(unprocessed, reachableSuccessors, initial, ::nodeSuccessors) { it.to }
        if (!reachableSuccessors.contains(final)) return null

        val reachablePredecessors = nodeSet(numNodes)
        traverse(unprocessed, reachablePredecessors, final, ::nodePredecessors) { it.from }
        if (!reachablePredecessors.contains(initial)) return null

        val reachableNodes = reachableSuccessors.also { it.and(reachablePredecessors) }
        val unreachableNodes = allNodes().also { it.andNot(reachableNodes) }
        if (unreachableNodes.isEmpty) return this

        return mutable().removeUnreachableIntermediateNodes(unreachableNodes).persist()
    }

    private inline fun traverse(
        workSet: NodeSet,
        visited: NodeSet,
        start: NodeMarker,
        transition: (NodeMarker) -> BitSet,
        nextNode: (AgEdge) -> NodeMarker
    ) {
        workSet.set(start)
        while (!workSet.isEmpty) {
            val node = workSet.removeFirst()
            if (!visited.add(node)) continue

            transition(node).forEach { accessor ->
                val edge = edges.get(accessor)
                check(edge != NO_EDGE) { "No edge for $accessor" }
                val next = nextNode(edge)
                workSet.set(next)
            }
        }
    }

    fun mutable() = MutableAccessGraph(
        manager,
        initial, final,
        edges, edges.mutable(),
        PersistentArrayBuilder(nodeSucc),
        PersistentArrayBuilder(nodePred),
    )

    internal class Serializer(
        private val manager: AutomataApManager,
        private val context: SummarySerializationContext
    ) {
        private fun accessorId(accessor: AccessorIdx): Long = with(manager) {
            context.getIdByAccessor(accessor.accessor)
        }

        private fun accessorById(id: Long): AccessorIdx = with(manager) {
            context.getAccessorById(id).idx
        }

        private fun DataOutputStream.writeAdjacentSets(sets: Array<PersistentBitSet?>) {
            sets.forEach { set ->
                writeInt(set?.size ?: -1)
                set?.forEach { accessor ->
                    writeLong(accessorId(accessor))
                }
            }
        }

        private fun DataInputStream.readAdjacentSets(numNodes: Int): Array<PersistentBitSet?> {
            return Array(numNodes) {
                val setSize = readInt()
                if (setSize == -1) {
                    null
                } else {
                    val set = List(setSize) { accessorById(readLong()) }.toBitSet { it }
                    emptyPersistentBitSet().persistentAddAll(set)
                }
            }
        }

        fun DataOutputStream.writeGraph(graph: AccessGraph) {
            check(graph.manager === manager)

            writeInt(graph.initial)
            writeInt(graph.final)

            writeInt(graph.edges.size)
            graph.edges.forEach { (accessor, edge) ->
                writeLong(accessorId(accessor))
                writeInt(edge.from)
                writeInt(edge.to)
            }

            writeInt(graph.numNodes)
            writeAdjacentSets(graph.nodeSucc)
            writeAdjacentSets(graph.nodePred)
        }

        fun DataInputStream.readGraph(): AccessGraph {
            val initial = readInt()
            val final = readInt()

            val edgesSize = readInt()
            val edgesBuilder = PersistentInt2LongMap()
            repeat(edgesSize) {
                val accessor = accessorById(readLong())
                val from = readInt()
                val to = readInt()
                edgesBuilder[accessor] = AgEdge(from, to)
            }
            val edges = edgesBuilder.persist(null)

            val numNodes = readInt()
            val nodeSucc = readAdjacentSets(numNodes)
            val nodePred = readAdjacentSets(numNodes)

            return AccessGraph(manager, initial, final, edges, nodeSucc, nodePred)
        }
    }

    companion object {
        private const val INITIAL_NODE = 0

        private val emptyNodes = arrayOf<PersistentBitSet?>(emptyPersistentBitSet())

        fun newEmptyGraph(manager: AutomataApManager) = AccessGraph(
            manager,
            INITIAL_NODE, INITIAL_NODE,
            emptyPersistentInt2LongMap(),
            emptyNodes, emptyNodes
        )

        private fun <E> Array<E?>.allNonNullIndicesSet(): BitSet {
            val size = this.size
            val result = BitSet(size)
            for (i in 0 until size) {
                if (this[i] == null) continue
                result.set(i)
            }
            return result
        }
    }
}

class MutableAccessGraph(
    private val manager: AutomataApManager,
    val initial: NodeMarker,
    val final: NodeMarker,
    private val originalPersistentEdges: PersistentInt2LongMap,
    private val mutableEdges: PersistentInt2LongMap,
    private val nodeSucc: PersistentArrayBuilder<PersistentBitSet?>,
    private val nodePred: PersistentArrayBuilder<PersistentBitSet?>,
) {
    private val numNodes: Int get() = nodeSucc.size

    private val removedNodes = nodeSet(numNodes)

    private fun create(initial: NodeMarker, final: NodeMarker): MutableAccessGraph =
        MutableAccessGraph(
            manager,
            initial, final,
            originalPersistentEdges, mutableEdges,
            nodeSucc, nodePred
        )

    fun persist(): AccessGraph = AccessGraph(
        manager,
        initial, final,
        mutableEdges.persist(originalPersistentEdges),
        nodeSucc.persist(),
        nodePred.persist()
    )

    fun prepend(accessor: AccessorIdx): MutableAccessGraph {
        val edge = findEdge(accessor)
        if (edge == NO_EDGE) {
            val newInitial = createNode()
            addStateSuccessor(newInitial, accessor, initial)
            return create(newInitial, final)
        }

        /**
         * (new initial) -- accessor --> (current initial)
         * (from) -- accessor --> (to)
         * _______________________________________
         * unify (from) and (new initial)
         * unify (current initial) and (to)
         * */
        val currentInitial = initial
        val edgeFrom = edge.from
        val edgeTo = edge.to

        // (new initial) has no predecessors
        // we add transition (from) -- accessor --> (to) which is already in the graph
        val newInitial = if (edgeFrom == currentInitial) edgeTo else edgeFrom

        if (currentInitial == edgeTo) {
            /**
             * (from) -- accessor --> (to / initial)
             * */
            return create(newInitial, final)
        }

        mergeNodeEdges(srcNode = currentInitial, dstNode = edgeTo)

        val newFinal = if (currentInitial == final) edgeTo else final

        return create(newInitial, newFinal)
    }

    private fun nodeSuccessors(node: NodeMarker): PersistentBitSet =
        nodeSucc[node]
            ?: error("Missing succ node")

    private fun nodePredecessors(node: NodeMarker): PersistentBitSet =
        nodePred[node]
            ?: error("Missing pred node")

    private fun getAndRemoveNodeSuccessors(node: NodeMarker): PersistentBitSet =
        nodeSucc.set(node, null)
            .also { removedNodes.add(node) }
            ?: error("Missing succ node")

    private fun getAndRemoveNodePredecessors(node: NodeMarker): PersistentBitSet =
        nodePred.set(node, null)
            .also { removedNodes.add(node) }
            ?: error("Missing pred node")

    private fun removeNodeSuccessor(node: NodeMarker, accessor: AccessorIdx) {
        val currentSuccessors = nodeSuccessors(node)
        nodeSucc[node] = currentSuccessors.persistentRemove(accessor)
    }

    private fun removeNodePredecessor(node: NodeMarker, accessor: AccessorIdx) {
        val currentPredecessors = nodePredecessors(node)
        nodePred[node] = currentPredecessors.persistentRemove(accessor)
    }

    private fun addNodeSuccessor(node: NodeMarker, accessor: AccessorIdx) {
        val currentSuccessors = nodeSuccessors(node)
        nodeSucc[node] = currentSuccessors.persistentAdd(accessor)
    }

    private fun addNodeSuccessors(node: NodeMarker, accessors: BitSet) {
        val currentSuccessors = nodeSuccessors(node)
        nodeSucc[node] = currentSuccessors.persistentAddAll(accessors)
    }

    private fun addNodePredecessor(node: NodeMarker, accessor: AccessorIdx) {
        val currentPredecessors = nodePredecessors(node)
        nodePred[node] = currentPredecessors.persistentAdd(accessor)
    }

    private fun addNodePredecessors(node: NodeMarker, accessors: BitSet) {
        val currentPredecessors = nodePredecessors(node)
        nodePred[node] = currentPredecessors.persistentAddAll(accessors)
    }

    private fun addStateSuccessor(state: NodeMarker, accessor: AccessorIdx, successor: NodeMarker) {
        createEdge(state, successor, accessor)
        addNodeSuccessor(state, accessor)
        addNodePredecessor(successor, accessor)
    }

    private fun createEdge(from: NodeMarker, to: NodeMarker, accessor: AccessorIdx) {
        val edge = AgEdge(from, to)
        val storedEdge = mutableEdges.put(accessor, edge)
        if (storedEdge == edge) return
        check(storedEdge == NO_EDGE) { "Edge overwrite" }
    }

    private var lastCreatedNode = -1

    private fun createNode(): NodeMarker {
        val freshNode = findHole(lastCreatedNode, { lastCreatedNode = it }, removedNodes) {
            nodeSucc[it] != null
        }

        nodeSucc[freshNode] = emptyPersistentBitSet()
        nodePred[freshNode] = emptyPersistentBitSet()
        return freshNode
    }

    private inline fun findHole(
        lastIdx: Int,
        updateLastIdx: (Int) -> Unit,
        removedSet: BitSet,
        holeIsNotAvailable: (Int) -> Boolean
    ): Int {
        var holeIdx = lastIdx + 1
        while (removedSet.contains(holeIdx) || holeIsNotAvailable(holeIdx)) {
            holeIdx++
        }

        val freshNode = holeIdx
        updateLastIdx(freshNode)
        return freshNode
    }

    fun clear(accessors: BitSet): MutableAccessGraph? {
        val initialSuccessors = nodeSuccessors(initial)

        val accessorsToClear = accessors.filter { initialSuccessors.contains(it) }
        if (accessorsToClear.isEmpty) return this

        // We have to remove all transitions from the start node -> no more transitions to final node -> graph is empty
        if (initialSuccessors.size == accessorsToClear.cardinality()) {
            if (initial == final) {
                return manager.emptyGraph().mutable()
            }
            return null
        }

        val initialPredecessors = nodePredecessors(initial)
        if (!initialPredecessors.isEmpty) {
            if (initialNodeIsReachableWithoutEdges(accessorsToClear)) {
                // Initial node is accessible through other edges. Can't remove accessor because it is in the loop.
                return this
            }
        }

        accessorsToClear.forEach { accessor ->
            val edge = removeEdge(accessor)
            check(edge != NO_EDGE && edge.from == initial) { "No edge" }

            removeNodeSuccessor(initial, accessor)
            removeNodePredecessor(edge.to, accessor)
        }

        return create(initial, final)
    }

    private fun initialNodeIsReachableWithoutEdges(bannedAccessors: BitSet): Boolean {
        val visitedNodes = nodeSet(numNodes)
        val unprocessed = bitSetOf(initial)

        while (!unprocessed.isEmpty) {
            val node = unprocessed.removeFirst()
            if (!visitedNodes.add(node)) continue

            nodeSuccessors(node).forEach { succAcc ->
                if (succAcc in bannedAccessors) return@forEach

                val edge = mutableEdges.get(succAcc)
                val edgeTo = edge.to
                if (edgeTo == initial) return true

                unprocessed.add(edgeTo)
            }
        }

        return false
    }

    private data class EdgeMergeEvent(
        val thisNode: NodeMarker,
        val otherNode: NodeMarker,
    )

    private fun normalize(node: NodeMarker, eliminatedNodes: NodeMap): NodeMarker {
        var result = node
        while (true) {
            val replacement = eliminatedNodes.get(result)
            if (replacement == NO_NODE) return result

            check(replacement != result) {
                "Normalization failed: loop"
            }

            result = replacement
        }
    }

    private fun normalize(event: EdgeMergeEvent, eliminatedNodes: NodeMap): EdgeMergeEvent =
        event.copy(
            thisNode = normalize(event.thisNode, eliminatedNodes)
        )

    fun concat(other: AccessGraph): MutableAccessGraph {
        val mergeResult = syncMerge(other = other, mergeStartState = final, otherMergeStartState = other.initial)
        return create(initial, mergeResult.otherFinalNode)
    }

    fun merge(other: AccessGraph): MutableAccessGraph {
        val mergeResult = syncMerge(other = other, mergeStartState = initial, otherMergeStartState = other.initial)

        val finalNode = if (mergeResult.thisFinalNode != mergeResult.otherFinalNode) {
            val (_, replacement) = mergeNodeEdgesEnsureNotInitial(mergeResult.thisFinalNode, mergeResult.otherFinalNode)
            replacement
        } else {
            mergeResult.thisFinalNode
        }

        return create(initial, finalNode)
    }

    data class SyncMergeFinalState(
        val thisFinalNode: NodeMarker,
        val otherFinalNode: NodeMarker,
    )

    // note: initial state remains unchanged after merge
    fun syncMerge(
        other: AccessGraph,
        mergeStartState: NodeMarker,
        otherMergeStartState: NodeMarker
    ): SyncMergeFinalState {
        val finalNodes = nodeSet(numNodes)

        val eliminatedNodes = nodeMap()
        val nodeMapping = nodeMap()

        val processedEvents = ObjectOpenHashSet<EdgeMergeEvent>()
        val unprocessed = mutableListOf(EdgeMergeEvent(thisNode = mergeStartState, otherNode = otherMergeStartState))

        while (unprocessed.isNotEmpty()) {
            val mergeEventDenormalized = unprocessed.removeLast()
            val mergeEvent = normalize(mergeEventDenormalized, eliminatedNodes)

            if (!processedEvents.add(mergeEvent)) continue

            var thisState = mergeEvent.thisNode
            val otherState = mergeEvent.otherNode

            nodeMapping.put(otherState, thisState)

            if (otherState == other.final) {
                finalNodes.add(thisState)
            }

            other.stateSuccessors(otherState).forEach { accessor ->
                val currentTo = this.getStateSuccessor(thisState, accessor)
                val otherSuccessor = other.getStateSuccessor(otherState, accessor)
                if (currentTo != NO_NODE) {
                    unprocessed += EdgeMergeEvent(thisNode = currentTo, otherNode = otherSuccessor)
                    return@forEach
                }

                val edge = findEdge(accessor)
                if (edge == NO_EDGE) {
                    val mappedNode = nodeMapping.get(otherSuccessor)
                    val nextNode = if (mappedNode == NO_NODE) {
                        createNode().also { nodeMapping.put(otherSuccessor, it) }
                    } else {
                        normalize(mappedNode, eliminatedNodes)
                    }

                    addStateSuccessor(thisState, accessor, nextNode)

                    unprocessed += EdgeMergeEvent(thisNode = nextNode, otherNode = otherSuccessor)
                    return@forEach
                }

                /**
                 * (thisState) --/-->
                 * (otherState) --- accessor ---> (otherSuccessor)
                 * (this.edgeFrom) --- accessor ---> (this.edgeTo)
                 * */
                val edgeFrom = edge.from
                val edgeTo = edge.to

                val (eliminatedNode, mergedEdgeStart) = mergeNodeEdgesEnsureNotInitial(thisState, edgeFrom)
                eliminatedNodes.put(eliminatedNode, mergedEdgeStart)

                if (eliminatedNode == thisState) {
                    thisState = mergedEdgeStart
                }

                val mergedEdgeEnd = if (edgeTo == eliminatedNode) mergedEdgeStart else edgeTo

                addStateSuccessor(mergedEdgeStart, accessor, mergedEdgeEnd)

                unprocessed += EdgeMergeEvent(thisNode = mergedEdgeEnd, otherNode = otherSuccessor)
            }
        }

        var otherFinalNode: NodeMarker = NO_NODE
        finalNodes.forEach { finalNode: NodeMarker ->
            val normalizedFinalNode = normalize(finalNode, eliminatedNodes)

            val currentFinal = otherFinalNode
            if (currentFinal == NO_NODE) {
                otherFinalNode = normalizedFinalNode
                return@forEach
            }

            if (normalizedFinalNode == currentFinal) return@forEach

            val (eliminatedNode, newFinalNode) = mergeNodeEdgesEnsureNotInitial(currentFinal, normalizedFinalNode)
            eliminatedNodes.put(eliminatedNode, newFinalNode)
            otherFinalNode = newFinalNode
        }

        val otherFinal = otherFinalNode
        check(otherFinal != NO_NODE) { "Final node is unreachable in $other" }

        return SyncMergeFinalState(
            thisFinalNode = normalize(final, eliminatedNodes),
            otherFinalNode = otherFinal
        )
    }

    private fun mergeNodeEdgesEnsureNotInitial(first: NodeMarker, second: NodeMarker): NodePair {
        val (eliminatedNode, replacement) = if (first == initial) {
            NodePair(second, first)
        } else {
            NodePair(first, second)
        }

        mergeNodeEdges(srcNode = eliminatedNode, dstNode = replacement)
        return NodePair(eliminatedNode, replacement)
    }

    fun removeUnreachableIntermediateNodes(nodes: NodeSet): MutableAccessGraph {
        val removedAccessors = BitSet()
        nodes.forEach { node ->
            nodeSucc[node]?.let { removedAccessors.or(it) }
            nodeSucc[node] = null

            nodePred[node]?.let { removedAccessors.or(it) }
            nodePred[node] = null
        }

        removedAccessors.forEach { accessor ->
            val edge = mutableEdges.remove(accessor)
            if (edge == NO_EDGE) return@forEach

            val edgeFrom = edge.from
            val edgeTo = edge.to

            val currentPredecessors = nodePred[edgeTo]
            if (currentPredecessors != null) {
                nodePred[edgeTo] = currentPredecessors.persistentRemoveAll(removedAccessors)
            }

            val currentSuccessors = nodeSucc[edgeFrom]
            if (currentSuccessors != null) {
                nodeSucc[edgeFrom] = currentSuccessors.persistentRemoveAll(removedAccessors)
            }
        }

        removedNodes.or(nodes)

        return create(initial, final)
    }

    private fun getStateSuccessor(state: NodeMarker, accessor: AccessorIdx): NodeMarker {
        val edge = findEdge(accessor)
        if (edge == NO_EDGE) return NO_NODE

        if (edge.from != state) return NO_NODE
        return edge.to
    }

    private fun findEdge(accessor: AccessorIdx): AgEdge =
        mutableEdges.get(accessor)

    private fun removeEdge(accessor: AccessorIdx): AgEdge =
        mutableEdges.remove(accessor)

    private fun mergeNodeEdges(srcNode: NodeMarker, dstNode: NodeMarker) {
        check(srcNode != dstNode) {
            "Merge node with itself"
        }

        val srcSuccessors = getAndRemoveNodeSuccessors(srcNode)
        val srcPredecessors = getAndRemoveNodePredecessors(srcNode)

        srcSuccessors.forEach { accessor ->
            val edge = mutableEdges.get(accessor)
            check(edge.from == srcNode)
            mutableEdges.put(accessor, AgEdge(dstNode, edge.to))
        }

        srcPredecessors.forEach { accessor ->
            val edge = mutableEdges.get(accessor)
            check(edge.to == srcNode)
            mutableEdges.put(accessor, AgEdge(edge.from, dstNode))
        }

        addNodeSuccessors(dstNode, srcSuccessors)
        addNodePredecessors(dstNode, srcPredecessors)
    }
}
