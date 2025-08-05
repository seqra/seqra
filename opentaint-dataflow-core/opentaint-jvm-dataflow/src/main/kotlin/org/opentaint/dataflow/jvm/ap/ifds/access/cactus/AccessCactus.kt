package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import org.opentaint.dataflow.jvm.ap.ifds.*
import org.opentaint.dataflow.jvm.ap.ifds.access.FactApDelta
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

import org.opentaint.util.assert
import kotlin.random.Random

typealias Cycle = List<Accessor>

class AccessCactus(
    override val base: AccessPathBase,
    val access: AccessNode,
    override val exclusions: ExclusionSet
): FinalFactAp {
    init {
        assert({ access.isWellFormed() }) {
            "Ill-formed AccessTree"
        }
    }

    override fun rebase(newBase: AccessPathBase): FinalFactAp =
        AccessCactus(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): FinalFactAp =
        AccessCactus(base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp =
        AccessCactus(base, access, exclusions)

    override fun startsWithAccessor(accessor: Accessor): Boolean = access.contains(accessor)

    override fun isAbstract(): Boolean = access.isAbstract

    override fun readAccessor(accessor: Accessor): FinalFactAp? =
        access.getChild(accessor)?.let { AccessCactus(base, it, exclusions) }

    override fun prependAccessor(accessor: Accessor): FinalFactAp =
        AccessCactus(base, access.addParent(accessor), exclusions)

    override fun clearAccessor(accessor: Accessor): FinalFactAp? {
        val newAccess = access.clearChild(accessor).takeIf { !it.isEmpty } ?: return null
        return AccessCactus(base, newAccess, exclusions)
    }

    override fun removeAbstraction(): FinalFactAp? =
        access.removeAbstraction().takeIf { !it.isEmpty }?.let { AccessCactus(base, it, exclusions) }

    override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? {
        val filteredAccess = access.filterAccessNode(filter) ?: return null
        return AccessCactus(base, filteredAccess, exclusions)
    }

    private sealed interface Delta : FactApDelta

    data object EmptyDelta : Delta {
        override val isEmpty: Boolean get() = true
    }

    data class NodeDelta(val node: AccessNode) : Delta {
        override val isEmpty: Boolean get() = false
    }

    override fun delta(other: InitialFactAp): List<FactApDelta> {
        other as AccessPathWithCycles

        val apRefinements = mutableListOf<AccessNode>()
        var emptyDeltaNeeded = false

        CactusUtils.matchAccessPathWithCactus(access, other.access, onFinalMatch = { _ ->
            emptyDeltaNeeded = true
        }) { treeNode ->
            val filteredNode = when (val exclusion = other.exclusions) {
                ExclusionSet.Empty -> treeNode
                is ExclusionSet.Concrete -> treeNode.filter(exclusion)
                ExclusionSet.Universe -> error("Unexpected universe exclusion in initial fact")
            }

            if (filteredNode.isEmpty) return@matchAccessPathWithCactus

            if (!filteredNode.isAbstract) {
                apRefinements.add(filteredNode)
                return@matchAccessPathWithCactus
            }

            val apRefinement = filteredNode
                .removeAbstraction()
                .takeIf { !it.isEmpty }

            emptyDeltaNeeded = true
            apRefinement?.let { apRefinements.add(it) }
        }

        return buildList {
            if (emptyDeltaNeeded) {
                add(EmptyDelta)
            }
            if (apRefinements.isNotEmpty()) {
                addAll(apRefinements.map(::NodeDelta))
            }
        }
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FactApDelta): FinalFactAp? {
        when (val d = delta as Delta) {
            EmptyDelta -> return this
            is NodeDelta -> {
                val concatenatedAccess = access.concatToLeafAbstractNodes(typeChecker, d.node) ?: return null
                return AccessCactus(base, concatenatedAccess, exclusions)
            }
        }
    }

    override val size: Int
        get() = access.size

    override fun toString(): String = buildString {
        access.print(this, "$base", suffix = "/$exclusions")
        if (lastIndex != -1 && this[lastIndex] == '\n') {
            this.deleteCharAt(lastIndex)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccessCactus

        if (base != other.base) return false
        if (access != other.access) return false
        if (exclusions != other.exclusions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + access.hashCode()
        result = 31 * result + exclusions.hashCode()
        return result
    }

    class AccessNode private constructor(
        val isAbstract: Boolean,
        val isFinal: Boolean,
        val elementEdge: Edge?,
        val fieldEdges: Array<Edge>,
    ) {
        val elementAccess: AccessNode? = (elementEdge as? BasicEdge)?.node
        private val allEdges: List<Edge>
            get() = fieldEdges.toList() + listOfNotNull(elementEdge)

        sealed interface Edge {
            val accessor: Accessor

            val node: AccessNode?
        }

        data class BasicEdge private constructor(
            override val accessor: Accessor,
            override val node: AccessNode
        ): Edge {
            companion object {
                fun createWithoutFoldUnsafe(accessor: Accessor, node: AccessNode): BasicEdge {
                    return BasicEdge(accessor, node)
                }

                fun create(accessor: Accessor, node: AccessNode): BasicEdge {
                    val foldedNode = node.foldCycles(listOf(accessor))
                    return BasicEdge(accessor, foldedNode)
                }
            }
        }

        data class CycleEdge(val accessor: Accessor, val node: AccessNode?)

        data class CycleStartEdge(val cycleEdges: List<CycleEdge>): Edge {
            override val accessor: Accessor = cycleEdges.first().accessor

            val cycleSize: Int
                get() = cycleEdges.size

            val accessors: List<Accessor>
                get() = cycleEdges.map { it.accessor }

            val lastNode: AccessNode?
                get() = cycleEdges.getOrNull(cycleSize - 2)?.node

            override val node: AccessNode?
                get() = cycleEdges.first().node

            override fun toString(): String {
                return "{cycle:${cycleEdges.joinToString(separator="") { it.accessor.toSuffix() }}}"
            }
        }

        private val Edge.isLoop: Boolean
            get() = this is CycleStartEdge && this.cycleSize == 1

        private val hash: Int
        private val depth: Int
        val size: Int

        private fun isWellFormed(rootAccessors: Set<Accessor>): Boolean {
            forEachEdge { edge ->
                if (!edge.isLoop && rootAccessors.any { defaultFoldRule(it, edge.accessor) }) {
                    return false
                }
            }

            forEachEdge { edge ->
                if (edge !is CycleStartEdge) {
                    return@forEachEdge
                }

                for (i in 1 until edge.cycleEdges.size - 1) {
                    val edgeFromCycle = edge.cycleEdges[i - 1].node!!.getEdge(edge.cycleEdges[i].accessor)
                    if (edgeFromCycle !is BasicEdge) {
                        return false
                    }
                }
                if (!edge.isLoop) {
                    val node = edge.cycleEdges[edge.cycleEdges.size - 2].node!!
                    if (node.getEdge(edge.cycleEdges.last().accessor) != null) {
                        return false
                    }
                }
            }

            forEachEdge { edge ->
                edge.node?.let {
                    if (!it.isWellFormed(rootAccessors + edge.accessor)) {
                        return false
                    }
                }
            }

            return true
        }

        fun isWellFormed(): Boolean =
            isWellFormed(emptySet())

        init {
            var hash = 0
            if (isAbstract) hash += 1
            if (isFinal) hash += 2
            elementEdge?.let { hash += it.hashCode() shl 2 }
            if (fieldEdges.isNotEmpty()) {
                val fieldHash = fieldEdges.sumOf { it.hashCode() }
                hash += fieldHash shl 5
            }
            this.hash = hash
        }

        init {
            var size = 1
            var depth = 1

            forEachEdge { edge ->
                edge.node?.let {
                    size += it.size
                    depth = maxOf(depth, 1 + it.depth)
                }
            }

            this.size = size
            this.depth = depth

            if (Random.nextDouble() < 0.000001) {
                System.err.println("$size, $depth, ${fieldEdges.size}")
            }
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessNode) return false

            if (hash != other.hash) return false
            if (isAbstract != other.isAbstract || isFinal != other.isFinal) return false

            if (elementEdge != other.elementEdge) return false

            return fieldEdges.contentEquals(other.fieldEdges)
        }

        override fun toString(): String = buildString {
            print(this)
        }

        fun print(builder: StringBuilder, prefix: String = "", suffix: String = ""): Unit = with(builder) {
            val newPrefix = buildString {
                append(prefix)
                forEachEdge { edge ->
                    if (edge is CycleStartEdge) {
                        append(edge.toString())
                    }
                }
            }

            if (isFinal || isAbstract) {
                append(newPrefix)

                if (isFinal) {
                    appendLine(FinalAccessor.toSuffix())
                } else {
                    appendLine(".*$suffix")
                }
            }

            forEachEdge { edge ->
                edge.node?.print(builder, newPrefix + edge.accessor.toSuffix())
            }
        }

        val hasFinal: Boolean
            get() {
                var result = false
                forAllNodesRecursively { result = result || it.isFinal }
                return result
            }

        // TODO: get rid of this method
        inline fun forEachField(body: (FieldAccessor, AccessNode) -> Unit) {
            for (edge in fieldEdges) {
                edge.node?.let {
                    body(edge.accessor as FieldAccessor, it)
                }
            }
        }

        fun forEachChild(body: (Accessor, AccessNode) -> Unit) {
            forEachEdge { edge ->
                edge.node?.let {
                    body(edge.accessor, it)
                }
            }
        }

        private inline fun forEachEdge(body: (Edge) -> Unit) {
            for (edge in fieldEdges) {
                body(edge)
            }
            elementEdge?.let { body(it) }
        }

        // TODO: rewrite this method
        fun forEachPath(body: (List<Accessor>) -> Unit) {
            forEachPath(mutableListOf(), body)
        }

        private fun forEachPath(currentPath: MutableList<Accessor>, body: (List<Accessor>) -> Unit) {
            if (isFinal) {
                currentPath.add(FinalAccessor)
                body(currentPath)
                currentPath.removeLast()
            }

            if (isAbstract) {
                body(currentPath)
            }

            if (elementAccess != null) {
                currentPath.add(ElementAccessor)
                elementAccess.forEachPath(currentPath, body)
                currentPath.removeLast()
            }

            forEachField { field, node ->
                currentPath.add(field)
                node.forEachPath(currentPath, body)
                currentPath.removeLast()
            }
        }

        val isEmpty: Boolean get() =
            !isAbstract && !isFinal && elementEdge == null && fieldEdges.isEmpty()

        val cycles: List<Cycle> get() =
            allEdges.filterIsInstance<CycleStartEdge>().map { cycleStartEdge ->
                cycleStartEdge.cycleEdges.map { it.accessor }
            }

        private fun fieldIndex(field: FieldAccessor): Int {
            // TODO: replace with binary search (?)
            return fieldEdges.indexOfFirst { it.accessor == field }
        }

        private fun getEdge(accessor: Accessor): Edge? = when (accessor) {
            is FieldAccessor -> fieldEdges.getOrNull(fieldIndex(accessor))
            ElementAccessor -> elementEdge
            FinalAccessor -> error("No edge can be attached to final accessor")
        }

        fun contains(accessor: Accessor): Boolean = when (accessor) {
            FinalAccessor -> isFinal
            ElementAccessor -> elementEdge != null
            is FieldAccessor -> fieldIndex(accessor) >= 0
        }

        fun getChild(accessor: Accessor): AccessNode? = getEdge(accessor)?.let { edge ->
            when (edge) {
                is BasicEdge -> edge.node
                is CycleStartEdge -> unrollCycle(edge).second.node
            }
        }

        fun addParent(accessor: Accessor): AccessNode =
            create(
                BasicEdge.create(accessor, this)
            )

        fun removeAbstraction(pushAbstractions: Boolean = true): AccessNode {
            val cyclesUnrolled = if (pushAbstractions) {
                unrollAll()
            } else {
                this
            }

            return create(isAbstract = false, isFinal, cyclesUnrolled.elementEdge, cyclesUnrolled.fieldEdges)
        }

        // Tries to construct CycleStartEdge in assumption that cycle start is the **parent** of `this` node
        private fun tryGetCycle(accessors: List<Accessor>): CycleStartEdge? {
            if (accessors.size == 1) {
                return createLoop(accessors.single())
            }

            val newCycle = mutableListOf<CycleEdge>()

            var curNode = this
            for (i in (0 until accessors.size - 1)) {
                newCycle.add(CycleEdge(accessors[i], curNode))
                if (i != accessors.size - 2) {
                    val edge = curNode.getEdge(accessors[i + 1])
                    if (edge !is BasicEdge) {
                        return null
                    }
                    curNode = edge.node
                }
            }

            if (curNode.contains(accessors.last())) {
                return null
            }

            newCycle.add(CycleEdge(accessors.last(), node = null))
            return CycleStartEdge(newCycle)
        }

        private fun tryGetNewFoldablePath(
            rootAccessors: List<Accessor>,
            rootNode: AccessNode,
            pathFromRoot: MutableList<CycleEdge>,
        ): Pair<List<CycleEdge>, AccessNode>? {
            return allEdges
                .filterIsInstance<BasicEdge>()
                .firstNotNullOfOrNull { edge ->
                    if (eliminationNeeded(rootAccessors, edge.accessor)) {
                        pathFromRoot.add(CycleEdge(edge.accessor, null))
                        pathFromRoot to edge.node
                    } else {
                        pathFromRoot.add(CycleEdge(edge.accessor, edge.node))
                        val path = edge.node.tryGetNewFoldablePath(rootAccessors, rootNode, pathFromRoot)
                        if (path == null) {
                            pathFromRoot.removeLast()
                        }
                        path
                    }
                }
        }

        private fun tryGetNewFoldablePath(
            rootAccessors: List<Accessor>,
            rootNode: AccessNode
        ): Pair<List<CycleEdge>, AccessNode>? =
            tryGetNewFoldablePath(rootAccessors, rootNode, mutableListOf())

        private fun unrollCycle(edge: CycleStartEdge): Pair<AccessNode, BasicEdge> {
            if (edge.isLoop) {
                val ansEdge = BasicEdge.create(edge.accessor, this)
                val ansNode = updateExistingEdge(edge.accessor, ansEdge)
                return ansNode to ansEdge
            }

            // TODO: try to rewrite this in a cleaner way
            var curNode = edge.lastNode!!
            curNode = curNode.bulkMergeAddEdges(listOf(BasicEdge.create(
                edge.cycleEdges.last().accessor,
                this.updateExistingEdge(edge.accessor, newEdge = null)
            )))

            for (i in (1 until edge.cycleSize - 1).reversed()) {
                val accessor = edge.cycleEdges[i].accessor
                val newEdge = BasicEdge.create(accessor, curNode)
                curNode = edge.cycleEdges[i - 1].node!!.updateExistingEdge(edge.cycleEdges[i].accessor, newEdge)
            }

            val cycleEdge = curNode
                .getChild(edge.cycleEdges[1].accessor)!!
                .tryGetCycle(edge.accessors.drop(1) + edge.accessors.first())

            curNode = if (cycleEdge != null) {
                curNode.updateExistingEdge(edge.cycleEdges[1].accessor, cycleEdge)
            } else {
                this.squashToLoops()
            }

            val ansEdge = BasicEdge.create(edge.accessor, curNode)
            val ansNode = updateExistingEdge(edge.accessor, ansEdge)

            return ansNode to ansEdge
        }

        private fun unrollAll(): AccessNode {
            return applyTransformEdges { edge ->
                if (edge is CycleStartEdge) {
                    unrollCycle(edge).second
                } else {
                    edge
                }
            }
        }

        private fun forAllNodesRecursively(body: (AccessNode) -> Unit) {
            body(this)
            forEachEdge { edge ->
                edge.node?.forAllNodesRecursively(body)
            }
        }

        // TODO: investigate performance boost from inlining here
        @Suppress("NOTHING_TO_INLINE")
        private inline fun eliminationNeeded(newRootAccessors: List<Accessor>, accessor: Accessor): Boolean {
            return newRootAccessors.any { defaultFoldRule(it, accessor) }
        }

        private fun foldNeeded(rootAccessors: List<Accessor>): Boolean {
            forEachEdge { edge ->
                if (eliminationNeeded(rootAccessors, edge.accessor)) {
                    return true
                }
            }

            forEachEdge { edge ->
                if (edge.node?.foldNeeded(rootAccessors) == true) {
                    return true
                }
            }

            return false
        }

        private fun squashToLoops(): AccessNode {
            return listOf(this).squashAndMerge()
        }

        private fun squashUnfoldableToLoops(
            rootAccessors: List<Accessor>,
            curCyclePosition: Triple<AccessNode, CycleStartEdge, Int>?, // cycle start node, cycle start edge, position on cycle
        ): AccessNode {
            if (!foldNeeded(rootAccessors)) {
                return this
            }

            forEachEdge { edge ->
                if (!edge.isLoop && eliminationNeeded(rootAccessors, edge.accessor)) {
                    return (curCyclePosition?.first ?: this).squashToLoops()
                }
            }

            return applyTransformEdges { edge ->
                when (edge) {
                    is BasicEdge -> {
                        val isEdgeFromCycle = if (curCyclePosition == null) {
                            false
                        } else {
                            val edgeFromCycle = curCyclePosition.second.cycleEdges.getOrNull(curCyclePosition.third)
                            edgeFromCycle?.accessor == edge.accessor
                        }

                        val newCyclePosition = if (isEdgeFromCycle) {
                            Triple(curCyclePosition!!.first, curCyclePosition.second, curCyclePosition.third + 1)
                        } else {
                            null
                        }

                        val newChild = edge.node.squashUnfoldableToLoops(rootAccessors, newCyclePosition)

                        if (newChild === edge.node) {
                            edge
                        } else {
                            BasicEdge.createWithoutFoldUnsafe(edge.accessor, newChild)
                        }
                    }
                    is CycleStartEdge -> {
                        if (edge.isLoop) {
                            return@applyTransformEdges edge
                        }

                        val newCyclePosition = Triple(this, edge, 1)
                        val newChild = edge.node!!.squashUnfoldableToLoops(rootAccessors, newCyclePosition)

                        if (newChild === edge.node) {
                            return@applyTransformEdges edge
                        }

                        newChild.tryGetCycle(edge.accessors) ?:
                        BasicEdge.createWithoutFoldUnsafe(
                            edge.accessor,
                            newChild
                        )
                    }
                }
            }
        }

        private fun tryFoldOneCycle(rootAccessors: List<Accessor>): AccessNode? {
            val (newCycle, hangingNode) = tryGetNewFoldablePath(rootAccessors, this)
                ?: return null

            val newCycleEdges = newCycle.toMutableList()
            val newEdge = if (newCycle.size == 1) CycleStartEdge(newCycle) else null
            var curCycleNode = (newCycle.getOrNull(newCycle.size - 2)?.node ?: this)
                .updateExistingEdge(newCycle.last().accessor, newEdge)

            for (i in (0 until newCycle.size - 1).reversed()) {
                newCycleEdges[i] = CycleEdge(newCycle[i].accessor, curCycleNode)
                curCycleNode = (newCycle.getOrNull(i - 1)?.node ?: this)
                    .updateExistingEdge(
                        newCycle[i].accessor,
                        BasicEdge.createWithoutFoldUnsafe(newCycle[i].accessor, curCycleNode)
                    )
            }

            val newCycleEdge = CycleStartEdge(newCycleEdges)
            val rootWithNewCycle = curCycleNode.updateExistingEdge(newCycleEdge.accessor, newCycleEdge)

            return rootWithNewCycle.mergeAdd(hangingNode, rootAccessors) // TODO: semantically this is not merge!!!
        }

        private fun foldCycles(rootAccessors: List<Accessor>): AccessNode {
            val folded = generateSequence(this) { newRoot ->
                newRoot.tryFoldOneCycle(rootAccessors)
            }.last()

            val result = folded.squashUnfoldableToLoops(rootAccessors, null)

            return result.takeIf { it != this } ?: this
        }

        fun clearChild(accessor: Accessor): AccessNode =
            with(unrollAll()) {
                when (accessor) {
                    FinalAccessor -> create(isAbstract, isFinal = false, elementEdge, fieldEdges)
                    ElementAccessor -> create(isAbstract, isFinal, elementEdge = null, fieldEdges)
                    is FieldAccessor -> removeSingleField(accessor)
                }
            }

        fun filterElementAccess(filter: (AccessNode) -> AccessNode?): AccessNode =
            with (unrollAll()) {
                val elementAccess = getChild(ElementAccessor) ?: return this

                val filtered = filter(elementAccess)

                if (filtered == elementAccess) return this
                return create(isAbstract, isFinal, elementEdge = filtered?.let { BasicEdge.create(ElementAccessor, it) }, fieldEdges)
            }

        fun filterFields(filter: (FieldAccessor, AccessNode) -> AccessNode?): AccessNode =
            with(unrollAll()) {
                val transformedFields = transformFields { edge ->
                    val field = edge.accessor as FieldAccessor
                    val newChild = filter(field, getChild(field)!!) ?: return@transformFields null
                    BasicEdge.create(field, newChild)
                } ?: return this
                return create(isAbstract, isFinal, elementEdge, transformedFields)
            }

        fun filter(exclusion: ExclusionSet.Concrete): AccessNode {
            if ((!this.isFinal || FinalAccessor !in exclusion) && allEdges.none { it.accessor in exclusion }) {
                return this
            }

            with(unrollAll()) {
                val isFinal = this.isFinal && FinalAccessor !in exclusion

                val transformedEdges = applyTransformEdges { edge ->
                    edge.takeIf { it.accessor !in exclusion }
                }

                return create(isAbstract, isFinal, transformedEdges.elementEdge, transformedEdges.fieldEdges)
            }
        }

        private fun bulkMergeAddEdges(edges: List<BasicEdge>): AccessNode {
            if (edges.isEmpty()) return this

            val fieldEdges = edges.filter { it.accessor is FieldAccessor }
            val elementEdge = edges.singleOrNull { it.accessor is ElementAccessor }

            val uniqueFields = mutableListOf<BasicEdge>()
            val groupedUniqueFields = fieldEdges.groupByTo(hashMapOf(), { it.accessor }, { it.node })

            for ((field, nodes) in groupedUniqueFields) {
                val mergedNodes = nodes.reduce { acc, node -> acc.mergeAdd(node, listOf(field)) }
                uniqueFields.add(BasicEdge.createWithoutFoldUnsafe(field, mergedNodes))
            }

            uniqueFields.sortBy { it.accessor as FieldAccessor }
            val addedFieldEdges = AccessNode(isAbstract = false, isFinal = false, elementEdge, uniqueFields.toTypedArray())

            return mergeAdd(addedFieldEdges)
        }

        fun mergeAdd(other: AccessNode, rootAccessors: List<Accessor> = emptyList()): AccessNode {
            if (this == other) return this

            return mergeNodes(
                other, rootAccessors, onOtherEdge = { _ -> }
            ) { _, newRootAccessors, thisNode, otherNode ->
                thisNode.mergeAdd(otherNode, newRootAccessors)
            }.also { if (it == this) return this else if (it == other) return other }
        }

        fun mergeAddDelta(other: AccessNode): Pair<AccessNode, AccessNode?> {
            val merged = mergeAdd(other)
            return if (merged === this) {
                this to null
            } else {
                merged to merged
            }
        }

        // TODO: possible infinite recursion here?
        fun filterAccessNode(filter: FactTypeChecker.FactApFilter): AccessNode? {
            with (unrollAll()) {
                val result = applyTransformEdges { edge ->
                    when (val status = filter.check(edge.accessor)) {
                        FactTypeChecker.FilterResult.Accept -> edge
                        FactTypeChecker.FilterResult.Reject -> null
                        is FactTypeChecker.FilterResult.FilterNext -> edge.node!!.filterAccessNode(status.filter)?.let { newChild ->
                            BasicEdge.create(
                                edge.accessor,
                                newChild
                            )
                        }
                    }
                }
                return result.takeIf { !it.isEmpty }
            }
        }

        fun concatToLeafAbstractNodes(typeChecker: FactTypeChecker?, other: AccessNode): AccessNode? =
            concatToLeafAbstractNodes(
                typeChecker, other, mutableListOf()
            )

        private fun concatToLeafAbstractNodes(
            typeChecker: FactTypeChecker?,
            other: AccessNode?,
            path: MutableList<Accessor>,
        ): AccessNode? {
            val concatNode = if (isAbstract && other != null) {
                if (typeChecker != null) {
                    val filter = typeChecker.accessPathFilter(path)
                    other.filterAccessNode(filter)
                } else {
                    other
                }
            } else null

            var concatSucceeded = concatNode != null

            val resultNode = applyTransformEdges { edge ->
                path.add(edge.accessor)
                val concatenatedNode = edge.node?.concatToLeafAbstractNodes(typeChecker, other, path)
                path.removeLast()

                if (concatenatedNode != null) {
                    concatSucceeded = true
                }

                when (edge) {
                    is BasicEdge -> {
                        concatenatedNode?.let {
                            BasicEdge.createWithoutFoldUnsafe(edge.accessor, it)
                        }
                    }
                    is CycleStartEdge -> {
                        if (edge.isLoop) {
                            edge
                        } else {
                            concatenatedNode?.let {
                                it.tryGetCycle(edge.accessors) ?: return@applyTransformEdges BasicEdge.createWithoutFoldUnsafe(
                                    edge.accessor,
                                    squashToLoops().removeAbstraction(pushAbstractions = false).squashAndMerge(other ?: create())
                                )
                            }
                        }
                    }
                }
            }.removeAbstraction(pushAbstractions = false).foldCycles(path)


            val concatenatedNode = concatNode?.let { resultNode.mergeAdd(it, path) } ?: resultNode

            return concatenatedNode.takeIf { !it.isEmpty && concatSucceeded }.also { // TODO: isEmpty is redundant (?)
                if (it == this) {
                    return this
                }
            }
        }

        fun filterStartsWith(accessPath: AccessPathWithCycles.AccessNode?): AccessNode? {
            if (accessPath == null) return this

            val matchedNodes = mutableListOf<AccessNode>()
            var finalAccessorReached = false
            CactusUtils.matchAccessPathWithCactus(this, accessPath, onFinalMatch = { _ ->
                finalAccessorReached = true
            }) { treeNode ->
                matchedNodes.add(treeNode)
            }

            val base = createAbstractNodeFromAp(accessPath)

            if (finalAccessorReached) {
                return base
            }
            if (matchedNodes.isEmpty()) {
                return null
            }

            return base.concatToLeafAbstractNodes(null, matchedNodes.reduce(AccessNode::mergeAdd))
        }

        private fun squashAndMerge(
            other: AccessNode,
        ): AccessNode {
            return listOf(this, other).squashAndMerge()
        }

        private fun mergeTwoEdges(
            thisEdge: Edge,
            other: AccessNode,
            otherEdge: Edge,
            merge: (Accessor, List<Accessor>, AccessNode, AccessNode) -> AccessNode,
            rootAccessors: List<Accessor>
        ): Edge {
            if (thisEdge is CycleStartEdge && otherEdge is CycleStartEdge && thisEdge.accessors == otherEdge.accessors) {
                if (thisEdge.isLoop) {
                    return thisEdge
                }

                val newNextNode = merge(thisEdge.accessor, rootAccessors + thisEdge.accessor, thisEdge.node!!, otherEdge.node!!)
                val newEdge = newNextNode.tryGetCycle(thisEdge.accessors)
                if (newEdge != null) {
                    return newEdge
                }
            }

            val thisNode = when (thisEdge) {
                is BasicEdge -> thisEdge.node
                is CycleStartEdge -> unrollCycle(thisEdge).second.node
            }
            val otherNode = when (otherEdge) {
                is BasicEdge -> otherEdge.node
                is CycleStartEdge -> other.unrollCycle(otherEdge).second.node
            }

            val mergedNode = merge(thisEdge.accessor, rootAccessors + thisEdge.accessor, thisNode, otherNode)
            return BasicEdge.createWithoutFoldUnsafe(thisEdge.accessor, mergedNode)
        }

        private fun foldCycles(edge: Edge, rootAccessors: List<Accessor>): Edge {
            return when (edge) {
                // TODO: maybe do something different instead of foldCycles here?
                is BasicEdge -> {
                    BasicEdge.createWithoutFoldUnsafe(
                        edge.accessor,
                        edge.node.foldCycles(rootAccessors + edge.accessor)
                    )
                }
                is CycleStartEdge -> {
                    if (edge.node?.foldNeeded(rootAccessors + edge.accessor) != true) {
                        // Hack to prevent unnecessary unroll/folds
                        edge
                    } else {
                        BasicEdge.createWithoutFoldUnsafe(
                            edge.accessor,
                            unrollCycle(edge).second.node.foldCycles(rootAccessors + edge.accessor)
                        )
                    }
                }
            }.also {
                if (it == edge) {
                    return edge
                }
            }
        }

        private fun mergeNodes(
            other: AccessNode,
            rootAccessors: List<Accessor>,
            onOtherEdge: (Edge) -> Unit,
            merge: (Accessor, List<Accessor>, AccessNode, AccessNode) -> AccessNode,
        ): AccessNode {
            // TODO: don't forget about onOtherEdge here!
            if ((allEdges + other.allEdges).any { eliminationNeeded(rootAccessors, it.accessor) }) {
                return squashAndMerge(other)
            }

            val isAbstract = this.isAbstract || other.isAbstract
            val isFinal = this.isFinal || other.isFinal

            val elementEdge = when {
                other.elementEdge == null -> elementEdge?.let {
                    foldCycles(it, rootAccessors).also { newEdge ->
                        if (newEdge != it) {
                            onOtherEdge(newEdge)
                        }
                    }
                }
                this.elementEdge == null -> other.foldCycles(other.elementEdge, rootAccessors).also(onOtherEdge)
                else -> mergeTwoEdges(this.elementEdge, other, other.elementEdge, merge, rootAccessors)
            }

            var modified = false
            var fieldsModified = false

            var writeIdx = 0
            var thisIdx = 0
            var otherIdx = 0

            val mergedEdges = arrayOfNulls<Edge>(fieldEdges.size + other.fieldEdges.size)

            while (true) {
                val thisEdge = fieldEdges.getOrNull(thisIdx)
                val otherEdge = other.fieldEdges.getOrNull(otherIdx)

                if (thisEdge == null && otherEdge == null) break

                val fieldCmp = when {
                    otherEdge == null -> -1 // thisEdge != null
                    thisEdge == null -> 1 // otherEdge != null
                    else -> (thisEdge.accessor as FieldAccessor).compareTo(otherEdge.accessor as FieldAccessor)
                }

                if (fieldCmp < 0) {
                    mergedEdges[writeIdx] = foldCycles(thisEdge!!, rootAccessors).also {
                        if (it != thisEdge) {
                            onOtherEdge(it)
                        }
                    }
                    thisIdx++
                    writeIdx++
                } else if (fieldCmp > 0) {
                    val normalizedOtherEdge = other.foldCycles(otherEdge!!, rootAccessors).also(onOtherEdge)

                    modified = true
                    fieldsModified = true

                    mergedEdges[writeIdx] = normalizedOtherEdge

                    otherIdx++
                    writeIdx++
                } else {
                    thisEdge!!
                    otherEdge!!

                    modified = true
                    mergedEdges[writeIdx] = mergeTwoEdges(thisEdge, other, otherEdge, merge, rootAccessors)

                    thisIdx++
                    otherIdx++
                    writeIdx++
                }
            }

            val fieldEdges = trimModifiedFields(modified, fieldsModified, writeIdx, fieldEdges, mergedEdges)
            return AccessNode(isAbstract, isFinal, elementEdge, fieldEdges)
        }

        private inline fun applyTransformEdges(
            transformer: (Edge) -> Edge?
        ): AccessNode {
            val newFieldEdges = transformFields(transformer) ?: fieldEdges
            val newElementEdge = elementEdge?.let(transformer)
            return AccessNode(isAbstract, isFinal, newElementEdge, newFieldEdges)
        }

        private fun updateExistingEdge(
            accessor: Accessor,
            newEdge: Edge?,
        ): AccessNode {
            check(getEdge(accessor) != null)

            // TODO: this can be done faster
            return applyTransformEdges { edge ->
                if (edge.accessor == accessor) {
                    newEdge
                } else {
                    edge
                }
            }
        }

        private inline fun transformFields(
            transformer: (Edge) -> Edge?
        ): Array<Edge>? {
            if (fieldEdges.isEmpty()) {
                return null
            }

            var modified = false
            var fieldsModified = false

            var writeIdx = 0
            val transformedFieldEdges = arrayOfNulls<Edge>(fieldEdges.size)

            for (i in fieldEdges.indices) {
                val fieldEdge = fieldEdges[i]

                val transformedEdge = transformer(fieldEdge)
                if (transformedEdge === fieldEdge) {
                    transformedFieldEdges[writeIdx] = fieldEdge
                    writeIdx++
                } else {
                    modified = true

                    if (transformedEdge == null) {
                        fieldsModified = true
                        continue
                    }

                    transformedFieldEdges[writeIdx] = transformedEdge
                    writeIdx++
                }
            }

            return trimModifiedFields(modified, fieldsModified, writeIdx, fieldEdges, transformedFieldEdges)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun trimModifiedFields(
            modified: Boolean,
            fieldsModified: Boolean,
            writeIdx: Int,
            originalFieldEdges: Array<Edge>,
            fieldEdges: Array<Edge?>,
        ): Array<Edge> {
            if (!fieldsModified) {
                check(writeIdx == originalFieldEdges.size) { "Incorrect size" }
                val trimmedEdges = if (writeIdx == fieldEdges.size) {
                    fieldEdges
                } else {
                    fieldEdges.copyOf(writeIdx)
                }

                @Suppress("UNCHECKED_CAST")
                return trimmedEdges as Array<Edge>
            }

            if (writeIdx != fieldEdges.size) {
                val trimmedEdges = fieldEdges.copyOf(writeIdx)
                @Suppress("UNCHECKED_CAST")
                return trimmedEdges as Array<Edge>
            } else {
                @Suppress("UNCHECKED_CAST")
                return fieldEdges as Array<Edge>
            }
        }

        private fun removeSingleField(field: FieldAccessor): AccessNode {
            val fieldIdx = fieldIndex(field)
            if (fieldIdx < 0) return this

            val newFieldSize = fieldEdges.size - 1
            if (newFieldSize == 0) {
                return create(isAbstract, isFinal, elementEdge, fieldEdges = emptyArray())
            }

            val newFieldEdges = arrayOfNulls<Edge>(newFieldSize)

            fieldEdges.copyInto(newFieldEdges, endIndex = fieldIdx)
            fieldEdges.copyInto(newFieldEdges, destinationOffset = fieldIdx, startIndex = fieldIdx + 1)

            @Suppress("UNCHECKED_CAST")
            return create(
                isAbstract, isFinal, elementEdge,
                newFieldEdges as Array<Edge>
            )
        }

        companion object {
            private val emptyNode = AccessNode(
                isAbstract = false, isFinal = false,
                elementEdge = null, fieldEdges = emptyArray()
            )

            private val abstractNode = AccessNode(
                isAbstract = true, isFinal = false,
                elementEdge = null, fieldEdges = emptyArray()
            )

            private val finalNode = AccessNode(
                isAbstract = false, isFinal = true,
                elementEdge = null, fieldEdges = emptyArray()
            )

            private val abstractFinalNode = AccessNode(
                isAbstract = true, isFinal = true,
                elementEdge = null, fieldEdges = emptyArray()
            )

            fun abstractNode(): AccessNode = abstractNode

            @JvmStatic
            private fun createLoop(accessor: Accessor): CycleStartEdge {
                return CycleStartEdge(listOf(CycleEdge(accessor, null)))
            }

            @JvmStatic
            private fun defaultFoldRule(
                high: Accessor,
                low: Accessor
            ): Boolean {
                return when (high) {
                    ElementAccessor -> low === ElementAccessor
                    is FieldAccessor -> (low is FieldAccessor) && (low.className == high.className)
                    FinalAccessor -> error("Unexpected FinalAccessor")
                }
            }

            @JvmStatic
            private fun List<AccessNode>.squashAndMerge(): AccessNode {
                if (size == 1 && single().depth == 1) {
                    return single()
                }

                var isFinal = false
                var isAbstract = false
                var hasElementAccessor = false
                val fieldAccessors = sortedSetOf<FieldAccessor>()

                for (rootNode in this) {
                    rootNode.forAllNodesRecursively { node ->
                        isFinal = isFinal || node.isFinal
                        isAbstract = isAbstract || node.isAbstract
                        node.forEachEdge { edge ->
                            val newAccessors = listOfNotNull(
                                edge.accessor,
                                (edge as? CycleStartEdge)?.cycleEdges?.last()?.accessor
                            )

                            newAccessors.forEach { accessor ->
                                when (accessor) {
                                    is ElementAccessor -> hasElementAccessor = true
                                    is FieldAccessor -> fieldAccessors.add(accessor)
                                    is FinalAccessor -> error("unexpected final accessor")
                                }
                            }
                        }
                    }
                }

                val elementEdge = if (hasElementAccessor) createLoop(ElementAccessor) else null
                val newFieldEdges = arrayOfNulls<CycleStartEdge>(fieldAccessors.size)
                var i = 0
                for (access in fieldAccessors) {
                    newFieldEdges[i++] = createLoop(access)
                }
                @Suppress("UNCHECKED_CAST")
                return create(isAbstract, isFinal, elementEdge, newFieldEdges as Array<Edge>)
            }

            @JvmStatic
            fun create(isAbstract: Boolean = false, isFinal: Boolean = false): AccessNode =
                if (isAbstract) {
                    if (isFinal) abstractFinalNode else abstractNode
                } else {
                    if (isFinal) finalNode else emptyNode
                }

            @JvmStatic
            private fun create(edge: Edge): AccessNode = when (edge.accessor) {
                is FieldAccessor -> AccessNode(
                    isAbstract = false, isFinal = false,
                    elementEdge = null,
                    fieldEdges = arrayOf(edge)
                )
                ElementAccessor -> AccessNode(
                    isAbstract = false, isFinal = false,
                    elementEdge = edge,
                    fieldEdges = emptyArray()
                )
                FinalAccessor -> error("Edge can't have final accessor")
            }

            @JvmStatic
            private fun create(
                isAbstract: Boolean,
                isFinal: Boolean,
                elementEdge: Edge?,
                fieldEdges: Array<Edge>
            ): AccessNode =
                if (isAbstract) {
                    if (isFinal) {
                        createElementAndField(abstractFinalNode, elementEdge, fieldEdges)
                    } else {
                        createElementAndField(abstractNode, elementEdge, fieldEdges)
                    }
                } else {
                    if (isFinal) {
                        createElementAndField(finalNode, elementEdge, fieldEdges)
                    } else {
                        createElementAndField(emptyNode, elementEdge, fieldEdges)
                    }
                }

            @JvmStatic
            private fun createElementAndField(
                base: AccessNode,
                elementEdge: Edge?,
                fieldEdges: Array<Edge>
            ): AccessNode {
                return if (elementEdge == null && fieldEdges.isEmpty()) {
                    base
                } else {
                    AccessNode(
                        isAbstract = base.isAbstract,
                        isFinal = base.isFinal,
                        elementEdge = elementEdge,
                        fieldEdges = fieldEdges
                    )
                }
            }

            @JvmStatic
            private fun createCycle(cycle: Cycle): CycleStartEdge {
                if (cycle.size == 1) {
                    return createLoop(cycle.single())
                }

                var curNode = emptyNode
                val cycleEdges = mutableListOf(CycleEdge(cycle.last(), null))
                for (i in (0 until cycle.size - 1).reversed()) {
                    cycleEdges.add(CycleEdge(cycle[i], curNode))
                    if (i > 0) {
                        curNode = curNode.addParent(cycle[i])
                    }
                }
                return CycleStartEdge(cycleEdges.reversed())
            }

            @JvmStatic
            fun createAbstractNodeFromAp(accessPath: AccessPathWithCycles.AccessNode?): AccessNode {
                if (accessPath == null) {
                    return abstractNode
                }

                var resultNode = if (accessPath.accessor is FinalAccessor) {
                    finalNode
                } else {
                    create(
                        BasicEdge.create(
                            accessPath.accessor,
                            createAbstractNodeFromAp(accessPath.next)
                        )
                    )
                }

                accessPath.cycles.forEach { cycle ->
                    resultNode = resultNode.mergeAdd(create(createCycle(cycle)))
                }

                return resultNode
            }
        }
    }
}
