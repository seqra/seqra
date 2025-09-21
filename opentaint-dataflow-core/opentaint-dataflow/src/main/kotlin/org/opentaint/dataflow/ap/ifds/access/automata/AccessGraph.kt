package org.opentaint.dataflow.ap.ifds.access.automata

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.serialization.AccessorSerializer
import org.opentaint.dataflow.ap.ifds.tryAnyAccessorOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.BitSet

private typealias NodeMarker = Int

private data class NodePair(val first: NodeMarker, val second: NodeMarker)

private typealias NodeMap = Int2IntOpenHashMap

private const val NO_NODE = -1
private fun nodeMap(): NodeMap = Int2IntOpenHashMap().also { it.defaultReturnValue(NO_NODE) }

private typealias NodeSet = BitSet

private fun nodeSet(expectedSize: Int): NodeSet = BitSet(expectedSize)
private operator fun NodeSet.contains(node: NodeMarker) = get(node)
private fun NodeSet.add(node: NodeMarker): Boolean =
    if (get(node)) false else true.also { set(node) }

private inline fun NodeSet.forEachNode(action: (NodeMarker) -> Unit) {
    var node = nextSetBit(0)
    while (node >= 0) {
        action(node)
        node = nextSetBit(node + 1)
    }
}

private fun NodeSet.removeFirst(): NodeMarker {
    val node = nextSetBit(0)
    check(node >= 0) { "Set is empty" }
    clear(node)
    return node
}

class AccessGraph(
    val initial: NodeMarker,
    val final: NodeMarker,
    val edges: PersistentMap<Accessor, AgEdge>,
    private val nodeSucc: PersistentList<PersistentSet<Accessor>?>,
    private val nodePred: PersistentList<PersistentSet<Accessor>?>,
) {
    data class AgEdge(val from: NodeMarker, val to: NodeMarker)

    private val numNodes: Int get() = nodeSucc.size

    val size: Int get() = edges.size

    private val hash: Int by lazy {
        val initialHash = nodeSuccessors(initial).hashCode()
        val finalHash = nodePredecessors(final).hashCode()
        (finalHash * 17 + initialHash) * 17 + edges.keys.hashCode()
    }

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccessGraph) return false

        if (edges.keys != other.edges.keys) return false

        return graphEqualsDfs(other)
    }

    private fun graphEqualsDfs(other: AccessGraph): Boolean {
        val nodeMapping = nodeMap()

        val unprocessed = mutableListOf(NodePair(initial, other.initial))
        while (unprocessed.isNotEmpty()) {
            val (thisNode, otherNode) = unprocessed.removeLast()

            val currentMapping = nodeMapping.put(thisNode, otherNode)
            if (currentMapping != NO_NODE) {
                if (currentMapping != otherNode) return false
                continue
            }

            val nextAccessors = this.nodeSuccessors(thisNode)
            val otherNextAccessors = other.nodeSuccessors(otherNode)
            if (nextAccessors.size != otherNextAccessors.size) return false

            for (accessor in nextAccessors) {
                val thisNext = this.getStateSuccessor(thisNode, accessor) ?: error("Missed state")
                val otherNext = other.getStateSuccessor(otherNode, accessor) ?: return false
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
    ) {
        var current = currentPath
        for (accessor in stateSuccessors(node)) {
            val successor = getStateSuccessor(node, accessor) ?: error("Missed state")
            val edge = Triple(node, accessor, successor)
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

    fun stateSuccessors(state: NodeMarker): Iterable<Accessor> =
        nodeSucc[state] ?: error("No successors")

    fun nodeSuccessors(node: NodeMarker): PersistentSet<Accessor> =
        nodeSucc[node] ?: error("Missed node")

    fun nodePredecessors(node: NodeMarker): PersistentSet<Accessor> =
        nodePred[node] ?: error("Missed node")

    private fun allNodes(): NodeSet =
        nodeSucc.allNonNullIndicesSet()

    fun getStateSuccessor(state: NodeMarker, accessor: Accessor): NodeMarker? =
        edges[accessor]?.takeIf { it.from == state }?.to

    private fun create(initial: NodeMarker, final: NodeMarker): AccessGraph =
        AccessGraph(initial, final, edges, nodeSucc, nodePred)

    fun startsWith(accessor: Accessor): Boolean =
        getStateSuccessor(initial, accessor) != null

    fun read(accessor: Accessor): AccessGraph? {
        val newInitial = getStateSuccessor(initial, accessor) ?: return null
        if (newInitial == initial) return this
        return create(newInitial, final).removeUnreachableNodes()
    }

    fun prepend(accessor: Accessor): AccessGraph {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.prepend(accessor)

        if (mutableResult === mutableCopy) return this

        return mutableResult.persist()
    }

    fun clear(accessor: Accessor): AccessGraph? {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.clear(listOf(accessor)) ?: return null

        if (mutableResult === mutableCopy) return this

        return mutableResult.persist()
            .removeUnreachableNodes()
    }

    fun filter(exclusionSet: ExclusionSet): AccessGraph? = when (exclusionSet) {
        ExclusionSet.Empty -> this
        ExclusionSet.Universe -> if (initialNodeIsFinal()) empty() else null
        is ExclusionSet.Concrete -> filter(exclusionSet)
    }

    private fun filter(exclusionSet: ExclusionSet.Concrete): AccessGraph? {
        val mutableCopy = mutable()
        val mutableResult = mutableCopy.clear(exclusionSet.set) ?: return null

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
        if (other.isEmpty()) return listOf(this)
        if (this.isEmpty()) return emptyList()

        val finalNodeMapping = matchGraphPrefix(other) ?: return emptyList()

        val deltaNode = create(finalNodeMapping, final)
        val resultDelta = deltaNode.removeUnreachableNodes()
            ?: return emptyList()

        return listOf(resultDelta)
    }

    fun containsAll(other: AccessGraph): Boolean {
        if (other.isEmpty()) return this.initial == this.final
        if (this.isEmpty()) return false

        val finalNodeMapping = matchGraphPrefix(other) ?: return false
        return finalNodeMapping == this.final
    }

    private fun matchGraphPrefix(other: AccessGraph): NodeMarker? {
        val nodeMapping = nodeMap()

        val visitedNodes = hashSetOf<NodePair>()
        val unprocessed = mutableListOf(NodePair(this.initial, other.initial))
        while (unprocessed.isNotEmpty()) {
            val nodePair = unprocessed.removeLast()
            if (!visitedNodes.add(nodePair)) continue
            val (thisNode, otherNode) = nodePair

            val currentMapping = nodeMapping.put(otherNode, thisNode)
            if (currentMapping != NO_NODE && currentMapping != thisNode) {
                return null
            }

            for (accessor in other.stateSuccessors(otherNode)) {
                val thisSuccessor = this.getStateSuccessor(thisNode, accessor)
                    ?: tryAnyAccessorOrNull(accessor) { this.getStateSuccessor(thisNode, AnyAccessor) }
                    ?: return null

                val otherSuccessor = other.getStateSuccessor(otherNode, accessor)
                    ?: error("No successor")

                unprocessed.add(NodePair(thisSuccessor, otherSuccessor))
            }
        }

        val finalNodeMapping = nodeMapping.get(other.final)
        if (finalNodeMapping == NO_NODE) {
            return null
        }

        return finalNodeMapping
    }

    fun splitDelta(other: AccessGraph): List<Pair<AccessGraph, AccessGraph>> {
        if (other.isEmpty()) return listOf(empty() to this)
        if (this.isEmpty()) return emptyList()

        val splitPoints = findGraphSplit(other)

        val result = mutableListOf<Pair<AccessGraph, AccessGraph>>()
        splitPoints.forEachNode { splitNode ->
            val matchedPrefix = AccessGraph(initial, splitNode, edges, nodeSucc, nodePred)
                .removeUnreachableNodes()
                ?: return@forEachNode

            if (!other.containsAll(matchedPrefix)) return@forEachNode

            val deltaSuffix = AccessGraph(splitNode, final, edges, nodeSucc, nodePred)
                .removeUnreachableNodes()
                ?: return@forEachNode

            result += matchedPrefix to deltaSuffix
        }

        return result
    }

    private fun findGraphSplit(other: AccessGraph): NodeSet {
        val splitPoints = NodeSet()

        val visitedNodes = hashSetOf<NodePair>()
        val unprocessed = mutableListOf(NodePair(this.initial, other.initial))
        while (unprocessed.isNotEmpty()) {
            val nodePair = unprocessed.removeLast()
            if (!visitedNodes.add(nodePair)) continue
            val (thisNode, otherNode) = nodePair

            if (otherNode == other.final) {
                splitPoints.add(thisNode)
            }

            for (accessor in other.stateSuccessors(otherNode)) {
                val thisSuccessor = this.getStateSuccessor(thisNode, accessor)
                    ?: continue

                val otherSuccessor = other.getStateSuccessor(otherNode, accessor)
                    ?: error("No successor")

                unprocessed.add(NodePair(thisSuccessor, otherSuccessor))
            }
        }

        return splitPoints
    }

    fun merge(other: AccessGraph): AccessGraph {
        val mutableCopy = mutable()
        val mergedMutable = mutableCopy.merge(other)
        val mergeResult = mergedMutable.persist()
        return mergeResult
    }

    fun filter(filter: FactTypeChecker.FactApFilter): AccessGraph? {
        val rejectedAccessors = mutableListOf<Accessor>()
        for (accessor in stateSuccessors(initial)) {
            when (val status = filter.check(accessor)) {
                FactTypeChecker.FilterResult.Accept -> continue

                FactTypeChecker.FilterResult.Reject -> {
                    rejectedAccessors.add(accessor)
                }

                is FactTypeChecker.FilterResult.FilterNext -> {
                    val successor = getStateSuccessor(initial, accessor) ?: error("Missed state")
                    val allNextRejected = filterNextNodes(successor, status.filter)
                    if (allNextRejected) {
                        rejectedAccessors.add(accessor)
                    }
                }
            }
        }

        if (rejectedAccessors.isEmpty()) return this

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
        for (accessor in stateSuccessors(node)) {
            when (val status = filter.check(accessor)) {
                FactTypeChecker.FilterResult.Accept -> {
                    allSuccessorsRejected = false
                }

                FactTypeChecker.FilterResult.Reject -> continue

                is FactTypeChecker.FilterResult.FilterNext -> {
                    val successor = getStateSuccessor(node, accessor) ?: error("Missed state")
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
        transition: (NodeMarker) -> Iterable<Accessor>,
        nextNode: (AgEdge) -> NodeMarker
    ) {
        workSet.set(start)
        while (!workSet.isEmpty) {
            val node = workSet.removeFirst()
            if (!visited.add(node)) continue

            for (accessor in transition(node)) {
                val edge = edges[accessor] ?: error("No edge for $accessor")
                val next = nextNode(edge)
                workSet.set(next)
            }
        }
    }

    fun mutable() = MutableAccessGraph(
        initial, final, edges.builder(), nodeSucc.builder(), nodePred.builder()
    )

    internal class Serializer(private val accessorSerializer: AccessorSerializer) {
        private fun DataOutputStream.writeAdjacentSets(sets: PersistentList<PersistentSet<Accessor>?>) {
            sets.forEach { set ->
                writeInt(set?.size ?: -1)
                set?.forEach { accessor ->
                    with (accessorSerializer) {
                        writeAccessor(accessor)
                    }
                }
            }
        }

        private fun DataInputStream.readAdjacentSets(numNodes: Int): PersistentList<PersistentSet<Accessor>?> {
            return List(numNodes) {
                val setSize = readInt()
                if (setSize == -1) {
                    null
                } else {
                    List(setSize) {
                        with (accessorSerializer) {
                            readAccessor()
                        }
                    }.toPersistentSet()
                }
            }.toPersistentList()
        }

        fun DataOutputStream.writeGraph(graph: AccessGraph) {
            writeInt(graph.initial)
            writeInt(graph.final)

            writeInt(graph.edges.size)
            graph.edges.forEach { (accessor, edge) ->
                with (accessorSerializer) {
                    writeAccessor(accessor)
                }
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
            val edgesBuilder = persistentHashMapOf<Accessor, AgEdge>().builder()
            repeat(edgesSize) {
                val accessor = with (accessorSerializer) {
                    readAccessor()
                }
                val from = readInt()
                val to = readInt()
                edgesBuilder[accessor] = AgEdge(from, to)
            }
            val edges = edgesBuilder.build()

            val numNodes = readInt()
            val nodeSucc = readAdjacentSets(numNodes)
            val nodePred = readAdjacentSets(numNodes)

            return AccessGraph(initial, final, edges, nodeSucc, nodePred)
        }
    }

    companion object {
        private const val INITIAL_NODE = 0

        private val emptyNodes = persistentListOf(persistentHashSetOf<Accessor>())

        private val EMPTY = AccessGraph(
            INITIAL_NODE, INITIAL_NODE,
            persistentHashMapOf(),
            emptyNodes, emptyNodes
        )

        fun empty() = EMPTY

        private fun <E> List<E?>.allNonNullIndicesSet(): BitSet {
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
    val initial: NodeMarker,
    val final: NodeMarker,
    val edges: MutableMap<Accessor, AccessGraph.AgEdge>,
    private val nodeSucc: MutableList<PersistentSet<Accessor>?>,
    private val nodePred: MutableList<PersistentSet<Accessor>?>,
) {
    private val numNodes: Int get() = nodeSucc.size

    private val removedNodes = nodeSet(numNodes)

    private fun create(initial: NodeMarker, final: NodeMarker): MutableAccessGraph =
        MutableAccessGraph(initial, final, edges, nodeSucc, nodePred)

    fun persist(): AccessGraph = AccessGraph(
        initial, final,
        edges.toPersistentHashMap(),
        nodeSucc.toPersistentList(),
        nodePred.toPersistentList()
    )

    fun prepend(accessor: Accessor): MutableAccessGraph {
        val edge = findEdge(accessor)
        if (edge == null) {
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
        val (edgeFrom, edgeTo) = edge

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

    private fun nodeSuccessors(node: NodeMarker): PersistentSet<Accessor> =
        nodeSucc[node]
            ?: error("Missing succ node")

    private fun nodePredecessors(node: NodeMarker): PersistentSet<Accessor> =
        nodePred[node]
            ?: error("Missing pred node")

    private fun getAndRemoveNodeSuccessors(node: NodeMarker): PersistentSet<Accessor> =
        nodeSucc.removeAtAndClean(node)
            .also { removedNodes.add(node) }
            ?: error("Missing succ node")

    private fun getAndRemoveNodePredecessors(node: NodeMarker): PersistentSet<Accessor> =
        nodePred.removeAtAndClean(node)
            .also { removedNodes.add(node) }
            ?: error("Missing pred node")

    private fun removeNodeSuccessor(node: NodeMarker, accessor: Accessor) {
        val currentSuccessors = nodeSuccessors(node)
        nodeSucc[node] = currentSuccessors.remove(accessor)
    }

    private fun removeNodePredecessor(node: NodeMarker, accessor: Accessor) {
        val currentPredecessors = nodePredecessors(node)
        nodePred[node] = currentPredecessors.remove(accessor)
    }

    private fun addNodeSuccessor(node: NodeMarker, accessor: Accessor) {
        val currentSuccessors = nodeSuccessors(node)
        nodeSucc[node] = currentSuccessors.add(accessor)
    }

    private fun addNodeSuccessors(node: NodeMarker, accessors: PersistentSet<Accessor>) {
        val currentSuccessors = nodeSuccessors(node)
        nodeSucc[node] = currentSuccessors.addAll(accessors)
    }

    private fun addNodePredecessor(node: NodeMarker, accessor: Accessor) {
        val currentPredecessors = nodePredecessors(node)
        nodePred[node] = currentPredecessors.add(accessor)
    }

    private fun addNodePredecessors(node: NodeMarker, accessors: PersistentSet<Accessor>) {
        val currentPredecessors = nodePredecessors(node)
        nodePred[node] = currentPredecessors.addAll(accessors)
    }

    private fun addStateSuccessor(state: NodeMarker, accessor: Accessor, successor: NodeMarker) {
        edges[accessor] = AccessGraph.AgEdge(state, successor)
        addNodeSuccessor(state, accessor)
        addNodePredecessor(successor, accessor)
    }

    private var lastCreatedNode = -1

    private fun createNode(): NodeMarker {
        var nodeIdx = lastCreatedNode + 1
        while (removedNodes.contains(nodeIdx) || nodeSucc.getOrNull(nodeIdx) != null) {
            nodeIdx++
        }

        val freshNode = nodeIdx.also { lastCreatedNode = it }
        nodeSucc.ensureIndexSet(freshNode, persistentHashSetOf())
        nodePred.ensureIndexSet(freshNode, persistentHashSetOf())
        return freshNode
    }

    fun clear(accessors: Iterable<Accessor>): MutableAccessGraph? {
        val initialSuccessors = nodeSuccessors(initial)

        val accessorsToClear = accessors.filterTo(hashSetOf()) { initialSuccessors.contains(it) }
        if (accessorsToClear.isEmpty()) return this

        // We have to remove all transitions from the start node -> no more transitions to final node -> graph is empty
        if (initialSuccessors.size == accessorsToClear.size) {
            if (initial == final) {
                return AccessGraph.empty().mutable()
            }
            return null
        }

        val initialPredecessors = nodePredecessors(initial)
        if (!initialPredecessors.isEmpty()) {
            if (initialNodeIsReachableWithoutEdges(accessorsToClear)) {
                // Initial node is accessible through other edges. Can't remove accessor because it is in the loop.
                return this
            }
        }

        for (accessor in accessorsToClear) {
            val (edgeFrom, edgeTo) = edges.remove(accessor) ?: error("No edge")
            check(edgeFrom == initial)

            removeNodeSuccessor(initial, accessor)
            removeNodePredecessor(edgeTo, accessor)
        }

        return create(initial, final)
    }

    private fun initialNodeIsReachableWithoutEdges(bannedAccessors: Set<Accessor>): Boolean {
        val visitedNodes = nodeSet(numNodes)
        val unprocessed = mutableListOf(initial)

        while (unprocessed.isNotEmpty()) {
            val node = unprocessed.removeLast()
            if (!visitedNodes.add(node)) continue

            for (succAcc in nodeSuccessors(node)) {
                if (succAcc in bannedAccessors) continue

                val (_, edgeTo) = edges.getValue(succAcc)
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

            for (accessor in other.stateSuccessors(otherState)) {
                val currentTo = this.getStateSuccessor(thisState, accessor)
                val otherSuccessor = other.getStateSuccessor(otherState, accessor) ?: error("No state")
                if (currentTo != null) {
                    unprocessed += EdgeMergeEvent(thisNode = currentTo, otherNode = otherSuccessor)
                    continue
                }

                val edge = findEdge(accessor)
                if (edge == null) {
                    val mappedNode = nodeMapping.get(otherSuccessor)
                    val nextNode = if (mappedNode == NO_NODE) {
                        createNode().also { nodeMapping.put(otherSuccessor, it) }
                    } else {
                        normalize(mappedNode, eliminatedNodes)
                    }

                    addStateSuccessor(thisState, accessor, nextNode)

                    unprocessed += EdgeMergeEvent(thisNode = nextNode, otherNode = otherSuccessor)
                    continue
                }

                /**
                 * (thisState) --/-->
                 * (otherState) --- accessor ---> (otherSuccessor)
                 * (this.edgeFrom) --- accessor ---> (this.edgeTo)
                 * */
                val (edgeFrom, edgeTo) = edge

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

        var otherFinalNode: NodeMarker? = null
        finalNodes.forEachNode { finalNode ->
            val normalizedFinalNode = normalize(finalNode, eliminatedNodes)

            val currentFinal = otherFinalNode
            if (currentFinal == null) {
                otherFinalNode = normalizedFinalNode
                return@forEachNode
            }

            if (normalizedFinalNode == currentFinal) return@forEachNode

            val (eliminatedNode, newFinalNode) = mergeNodeEdgesEnsureNotInitial(currentFinal, normalizedFinalNode)
            eliminatedNodes.put(eliminatedNode, newFinalNode)
            otherFinalNode = newFinalNode
        }

        val otherFinal = otherFinalNode
            ?: error("Final node is unreachable in $other")

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
        nodes.forEachNode { node ->
            val successors = getAndRemoveNodeSuccessors(node)
            for (accessor in successors) {
                val (edgeFrom, edgeTo) = edges.remove(accessor) ?: continue
                check(edgeFrom == node)
                removeNodePredecessor(edgeTo, accessor)
            }

            val predecessors = getAndRemoveNodePredecessors(node)
            for (accessor in predecessors) {
                val (edgeFrom, edgeTo) = edges.remove(accessor) ?: continue
                check(edgeTo == node)
                removeNodeSuccessor(edgeFrom, accessor)
            }
        }
        return create(initial, final)
    }

    private fun getStateSuccessor(state: NodeMarker, accessor: Accessor): NodeMarker? =
        edges[accessor]?.takeIf { it.from == state }?.to

    private fun findEdge(accessor: Accessor): AccessGraph.AgEdge? = edges[accessor]

    private fun mergeNodeEdges(srcNode: NodeMarker, dstNode: NodeMarker) {
        check(srcNode != dstNode) {
            "Merge node with itself"
        }

        val srcSuccessors = getAndRemoveNodeSuccessors(srcNode)
        val srcPredecessors = getAndRemoveNodePredecessors(srcNode)

        for (accessor in srcSuccessors) {
            val (edgeFrom, edgeTo) = edges[accessor] ?: error("No edge: $accessor")
            check(edgeFrom == srcNode)
            edges[accessor] = AccessGraph.AgEdge(dstNode, edgeTo)
        }

        for (accessor in srcPredecessors) {
            val (edgeFrom, edgeTo) = edges[accessor] ?: error("No edge: $accessor")
            check(edgeTo == srcNode)
            edges[accessor] = AccessGraph.AgEdge(edgeFrom, dstNode)
        }

        addNodeSuccessors(dstNode, srcSuccessors)
        addNodePredecessors(dstNode, srcPredecessors)
    }

    companion object {
        private fun <E> MutableList<E?>.ensureIndexSet(idx: Int, element: E) {
            while (idx >= size) add(null)
            this[idx] = element
        }

        private fun <E> MutableList<E?>.removeAtAndClean(idx: Int): E? {
            val value = set(idx, null)
            while (this[lastIndex] == null) removeLast()
            return value
        }
    }
}
