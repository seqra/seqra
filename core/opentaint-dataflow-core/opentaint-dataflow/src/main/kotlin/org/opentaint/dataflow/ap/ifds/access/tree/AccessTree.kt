package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.ReversedApNode
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.foldRight
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ANY_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.VALUE_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isTaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.add
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.forEachIntEntry
import org.opentaint.dataflow.util.getOrCreate
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.BitSet
import java.util.IdentityHashMap
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class AccessTree(
    private val apManager: TreeApManager,
    override val base: AccessPathBase,
    val access: AccessNode,
    override val exclusions: ExclusionSet
) : FinalFactAp {
    override fun rebase(newBase: AccessPathBase): FinalFactAp =
        AccessTree(apManager, newBase, access, exclusions)

    override fun exclude(accessor: Accessor): FinalFactAp =
        AccessTree(apManager, base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp =
        AccessTree(apManager, base, access, exclusions)

    override fun getAllAccessors(): Set<Accessor> {
        val result = IntOpenHashSet()
        access.collectAccessorsTo(result)

        return result.mapTo(hashSetOf()) {
            with(apManager) { it.accessor }
        }
    }

    override fun startsWithAccessor(accessor: Accessor): Boolean =
        with(apManager) { access.contains(accessor.idx) }

    override fun getStartAccessors(): Set<Accessor> = with(apManager) {
        access.accessors?.mapTo(hashSetOf()) { it.accessor }.orEmpty()
    }

    override fun isAbstract(): Boolean = access.isAbstract

    override fun readAccessor(accessor: Accessor): FinalFactAp? = with(apManager) {
        access.getChild(accessor.idx)
            ?.let { AccessTree(apManager, base, it, exclusions) }
    }

    override fun prependAccessor(accessor: Accessor): FinalFactAp = with(apManager) {
        AccessTree(apManager, base, access.addParent(accessor.idx), exclusions)
    }

    override fun clearAccessor(accessor: Accessor): FinalFactAp? = with(apManager) {
        val newAccess = access.clearChild(accessor.idx).takeIf { !it.isEmpty } ?: return null
        return AccessTree(apManager, base, newAccess, exclusions)
    }

    override fun removeAbstraction(): FinalFactAp? =
        access.removeAbstraction().takeIf { !it.isEmpty }
            ?.let { AccessTree(apManager, base, it, exclusions) }

    override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? {
        val filteredAccess = access.filterAccessNode(filter) ?: return null
        return AccessTree(apManager, base, filteredAccess, exclusions)
    }

    override fun filterFact(filter: FactTypeChecker.FactCompatibilityFilter): FinalFactAp? {
        if (filter is FactTypeChecker.AlwaysCompatibleFilter) return this
        val filteredAccess = access.filterAccessNode(filter) ?: return null
        return AccessTree(apManager, base, filteredAccess, exclusions)
    }

    override fun contains(factAp: InitialFactAp): Boolean {
        factAp as AccessPath

        if (base != factAp.base) return false

        val otherAccess = factAp.access

        if (otherAccess == null) {
            return access.isAbstract
        }

        var node = access
        otherAccess.toList().forEachInt { accessor ->
            if (accessor == FINAL_ACCESSOR_IDX) return node.isFinal
            node = node.getChild(accessor) ?: return false
        }
        return node.isAbstract
    }

    override fun equalTo(factAp: InitialFactAp): Boolean {
        factAp as AccessPath

        if (base != factAp.base) return false

        val otherAccess = factAp.access
        if (otherAccess == null) {
            return access.isEmptyAbstract
        }

        var node = access
        otherAccess.toList().forEachInt { accessor ->
            if (accessor == FINAL_ACCESSOR_IDX) {
                return node.isFinal && node.accessors == null
            }

            if (node.accessors?.size != 1) return false
            node = node.getChild(accessor) ?: return false
        }

        return node.isEmptyAbstract
    }

    private sealed interface AccessTreeDelta : FinalFactAp.Delta

    data object EmptyAccessTreeDelta : AccessTreeDelta {
        override val isEmpty: Boolean get() = true
        override fun startsWithAccessor(accessor: Accessor): Boolean = false
        override fun getStartAccessors(): Set<Accessor> = emptySet()
        override fun getAllAccessors(): Set<Accessor> = emptySet()
        override fun readAccessor(accessor: Accessor): FinalFactAp.Delta? = null
        override fun isAbstract(): Boolean = true
    }

    data class NodeAccessTreeDelta(
        private val apManager: TreeApManager,
        val node: AccessNode
    ) : AccessTreeDelta {
        override val isEmpty: Boolean get() = false

        override fun startsWithAccessor(accessor: Accessor): Boolean = with(apManager) {
            node.contains(accessor.idx)
        }

        override fun getStartAccessors(): Set<Accessor> = with(apManager) {
            node.accessors?.mapTo(hashSetOf()) { it.accessor }.orEmpty()
        }

        override fun getAllAccessors(): Set<Accessor> = with(apManager) {
            val s = IntOpenHashSet()
            node.collectAccessorsTo(s)
            return s.mapTo(hashSetOf()) { it.accessor }
        }

        override fun readAccessor(accessor: Accessor): FinalFactAp.Delta? = with(apManager) {
            node.getChild(accessor.idx)
                ?.let { NodeAccessTreeDelta(apManager, it) }
        }

        override fun isAbstract(): Boolean = node.isAbstract
    }

    override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> {
        other as AccessPath

        if (base != other.base) return emptyList()

        var node = access
        val access = other.access
        access?.toList()?.forEachInt { accessor ->
            if (accessor == FINAL_ACCESSOR_IDX) {
                if (!node.isFinal) return emptyList()
                return listOf(EmptyAccessTreeDelta)
            }

            node = node.getChild(accessor) ?: return emptyList()
        }

        val filteredNode = when (val exclusion = other.exclusions) {
            ExclusionSet.Empty -> node
            is ExclusionSet.Concrete -> node.filter(exclusion)
            ExclusionSet.Universe -> error("Unexpected universe exclusion in initial fact")
        }

        if (filteredNode.isEmpty) return emptyList()

        if (!filteredNode.isAbstract) return listOf(NodeAccessTreeDelta(apManager, filteredNode))

        val nonAbstractDelta = filteredNode
            .removeAbstraction()
            .takeIf { !it.isEmpty }
            ?.let { NodeAccessTreeDelta(apManager, it) }

        return listOfNotNull(nonAbstractDelta, EmptyAccessTreeDelta)
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? {
        when (val d = delta as AccessTreeDelta) {
            EmptyAccessTreeDelta -> return this
            is NodeAccessTreeDelta -> {
                val concatenatedAccess = access.concatToLeafAbstractNodes(typeChecker, d.node)
                    ?: return null
                return AccessTree(apManager, base, concatenatedAccess, exclusions)
            }
        }
    }

    override val size: Int
        get() = access.countNodes()

    override fun toString(): String = buildString {
        access.print(this, "$base", suffix = "/$exclusions")
        if (this[lastIndex] == '\n') {
            this.deleteCharAt(lastIndex)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccessTree

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
        val manager: TreeApManager,
        @JvmField val interned: Boolean,
        @JvmField val isAbstract: Boolean,
        @JvmField val isFinal: Boolean,
        @JvmField val accessors: IntArray?,
        @JvmField val accessorNodes: Array<AccessNode>?,
    ) {
        @JvmField val hash: Long
        @JvmField val size: Long
        @JvmField val maxDepth: Int
        @JvmField val containsStatic: Boolean

        init {
            var hash = 0L
            var depth = 0
            var containsStatic = false

            if (isAbstract) hash += 1

            if (isFinal) {
                depth = 1
                hash += 2
            }

            if (accessors != null) {
                containsStatic = accessors.any { it.isStaticAccessor() }
            }

            if (accessorNodes != null) {
                val accessorsHash = accessorNodes.sumOf { it.hash }
                hash += accessorsHash shl 5

                depth = accessorNodes.maxOf { it.maxDepth } + 1

                containsStatic = containsStatic || accessorNodes.any { it.containsStatic }
            }

            if (containsAnyAccessor()) {
                depth += 10_000
            }

            this.hash = hash
            this.maxDepth = depth
            this.containsStatic = containsStatic
        }

        init {
            var size = 1L
            if (accessorNodes != null) {
                size += accessorNodes.sumOf { it.size }
            }
            this.size = size
        }

        override fun hashCode(): Int = hash.toInt()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessNode) return false

            if (hash != other.hash) return false
            if (isAbstract != other.isAbstract || isFinal != other.isFinal) return false

            if (!accessors.contentEquals(other.accessors)) return false
            return accessorNodes.contentEquals(other.accessorNodes)
        }

        fun countNodes(visited: IdentityHashMap<AccessNode, Unit> = IdentityHashMap()): Int {
            visited[this] = Unit
            forEachAccessor { _, node ->
                node.countNodes(visited)
            }
            return visited.size
        }

        override fun toString(): String = buildString { print(this) }

        fun print(builder: StringBuilder, prefix: String = "", suffix: String = ""): Unit = with(builder) {
            if (isFinal || isAbstract) {
                append(prefix)

                if (isFinal) {
                    appendLine(FinalAccessor.toSuffix())
                } else {
                    appendLine("/*$suffix")
                }
            }

            forEachAccessor { fieldIdx, child ->
                val field = with(manager) { fieldIdx.accessor }
                child.print(builder, prefix + field.toSuffix())
            }
        }

        inline fun forEachAccessor(body: (AccessorIdx, AccessNode) -> Unit) {
            if (accessors != null) {
                for (i in accessors.indices) {
                    body(accessors[i], accessorNodes!![i])
                }
            }
        }

        val isEmpty: Boolean
            get() = !isAbstract && !isFinal && accessors == null

        val isEmptyAbstract: Boolean
            get() = isAbstract && !isFinal && accessors == null

        private fun accessorIndex(accessor: AccessorIdx): Int {
            if (accessors == null) return -1
            return accessors.binarySearch(accessor)
        }

        private fun getNodeByAccessor(accessor: AccessorIdx): AccessNode? =
            accessorNodes?.getOrNull(accessorIndex(accessor))

        fun containsAnyAccessor(): Boolean =
            accessorIndex(ANY_ACCESSOR_IDX) >= 0

        fun contains(accessor: AccessorIdx): Boolean {
            if (accessor == FINAL_ACCESSOR_IDX) return isFinal

            val accessorIdx = accessorIndex(accessor)
            if (accessorIdx >= 0) return true

            val anyAccessorNode = getNodeByAccessor(ANY_ACCESSOR_IDX)
                ?: return false

            if (anyAccessorNode.contains(accessor)) return true

            return with(manager) {
                anyAccessorUnrollStrategy.unrollAccessor(accessor.accessor)
            }
        }

        fun getChild(accessor: AccessorIdx): AccessNode? {
            if (accessor == FINAL_ACCESSOR_IDX) return manager.finalNode.takeIf { this.isFinal }

            val node = getNodeByAccessor(accessor)
            if (node != null) return node

            val anyAccessorNode = getNodeByAccessor(ANY_ACCESSOR_IDX)
                ?: return null

            val anyChild = anyAccessorNode.getNodeByAccessor(accessor)
            if (anyChild != null) return anyChild

            with(manager) {
                if (!anyAccessorUnrollStrategy.unrollAccessor(accessor.accessor)) return null
            }

            val childWithAny = anyAccessorNode.addParentIfPossible(ANY_ACCESSOR_IDX)
            val unrolled = childWithAny?.mergeAdd(anyAccessorNode) ?: anyAccessorNode
            return unrolled
        }

        fun addParentIfPossible(accessor: AccessorIdx): AccessNode? {
            if (containsStatic) return null

            return when {
                accessor == FINAL_ACCESSOR_IDX -> null
                accessor == ELEMENT_ACCESSOR_IDX -> manager.create(
                    elementAccess = limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT)
                )
                accessor.isFieldAccessor() -> addParentFieldAccess(accessor)
                accessor.isStaticAccessor() -> create(accessor, this)
                accessor == VALUE_ACCESSOR_IDX -> {
                    if (accessors?.any { !it.isTaintMarkAccessor() } == true) {
                        return null
                    }

                    create(accessor, this)
                }

                accessor.isTaintMarkAccessor() -> {
                    if (this == manager.finalNode || this == manager.abstractNode || this == manager.abstractFinalNode) {
                        create(accessor, this)
                    } else {
                        null
                    }
                }

                accessor == ANY_ACCESSOR_IDX -> prependAnyAccessor()
                else -> error("Unsupported accessor: $accessor")
            }
        }

        fun addParent(accessor: AccessorIdx): AccessNode =
            addParentIfPossible(accessor)
                ?: error("Impossible accessor")

        fun removeAbstraction(): AccessNode =
            manager.create(isAbstract = false, isFinal, accessors, accessorNodes)

        private fun prependAnyAccessor(): AccessNode {
            val anyNode = getNodeByAccessor(ANY_ACCESSOR_IDX)
            val nextNode = if (anyNode == null) {
                this
            } else {
                removeSingleAccessor(ANY_ACCESSOR_IDX).mergeAdd(anyNode)
            }
            return create(ANY_ACCESSOR_IDX, nextNode)
        }

        private fun limitElementAccess(limit: Int): AccessNode {
            if (limit > 0) {
                return transformAccessors { accessor, accessNode ->
                    if (accessor == ELEMENT_ACCESSOR_IDX) {
                        accessNode.limitElementAccess(limit - 1)
                    } else {
                        accessNode
                    }
                }
            }

            return collapseElementAccess().also {
                check(it.getNodeByAccessor(ELEMENT_ACCESSOR_IDX) == null) {
                    "Array element limit invariant failure"
                }
            }
        }

        private fun collapseElementAccess(): AccessNode {
            val elementAccess = getNodeByAccessor(ELEMENT_ACCESSOR_IDX) ?: return this

            val collapsedElementAccess = elementAccess.collapseElementAccess()
            val result = removeSingleAccessor(ELEMENT_ACCESSOR_IDX)
            return result.mergeAdd(collapsedElementAccess)
        }

        private fun addParentFieldAccess(
            newRootField: AccessorIdx
        ): AccessNode {
            val filteredNodes = mutableListOf<IntObjectImmutablePair<AccessNode>>()
            val limitedThis = limitFieldAccess(newRootField, filteredNodes)

            val resultNode = if (limitedThis != null) {
                create(newRootField, limitedThis)
            } else {
                manager.emptyNode
            }

            return resultNode.bulkMergeAddAccessors(filteredNodes)
                .also { check(!it.isEmpty) { "Empty node after field normalization" } }
        }

        fun clearChild(accessor: AccessorIdx): AccessNode = when (accessor) {
            FINAL_ACCESSOR_IDX -> manager.create(isAbstract, isFinal = false, accessors, accessorNodes)
            else -> removeSingleAccessor(accessor)
        }

        fun filter(exclusion: ExclusionSet.Concrete): AccessNode {
            val isFinal = this.isFinal && FinalAccessor !in exclusion

            val transformedAccessors = transformAccessors(accessors, accessorNodes) { accessor, node ->
                with(manager) {
                    node.takeIf { accessor.accessor !in exclusion }
                }
            }

            if (isFinal == this.isFinal && transformedAccessors == null) {
                return this
            }

            val accessors = transformedAccessors?.first ?: accessors
            val accessorNodes = transformedAccessors?.second ?: accessorNodes

            return manager.create(isAbstract, isFinal, accessors, accessorNodes)
        }

        fun collectAccessorsTo(dst: IntOpenHashSet) {
            if (isFinal) {
                dst.add(FINAL_ACCESSOR_IDX)
            }

            forEachAccessor { accessor, accessorNode ->
                if (accessor == ANY_ACCESSOR_IDX) {
                    // note: always ignore any accessor
                    return@forEachAccessor
                }

                dst.add(accessor)
                accessorNode.collectAccessorsTo(dst)
            }
        }

        private fun bulkMergeAddAccessors(accessors: List<IntObjectImmutablePair<AccessNode>>): AccessNode {
            if (accessors.isEmpty()) return this

            val groupedUniqueAccessors = Int2ObjectOpenHashMap<MutableList<AccessNode>>()
            accessors.forEach { accessorWithNode ->
                val group = groupedUniqueAccessors.getOrCreate(accessorWithNode.leftInt(), ::mutableListOf)
                group.add(accessorWithNode.right())
            }

            val uniqueAccessors = mutableListOf<IntObjectImmutablePair<AccessNode>>()
            groupedUniqueAccessors.forEachIntEntry { accessor, nodes ->
                val mergedNodes = nodes.reduce { acc, node -> acc.mergeAdd(node) }
                uniqueAccessors.add(IntObjectImmutablePair(accessor, mergedNodes))
            }

            uniqueAccessors.sortBy { it.firstInt() }
            val addedAccessors = IntArray(uniqueAccessors.size) { uniqueAccessors[it].firstInt() }
            val addedNodes = Array(uniqueAccessors.size) { uniqueAccessors[it].second() }

            val mergedAccessors = mergeAccessors(
                addedAccessors, addedNodes, onOtherNode = { _, _ -> }
            ) { _, thisNode, otherNode ->
                thisNode.mergeAdd(otherNode)
            }

            if (mergedAccessors == null) return this

            return manager.create(isAbstract, isFinal, mergedAccessors.first, mergedAccessors.second)
        }

        fun mergeAdd(other: AccessNode): AccessNode {
            if (this === other) return this

            val isAbstract = this.isAbstract || other.isAbstract

            val isFinal = this.isFinal || other.isFinal

            val mergedAccessors = mergeAccessors(
                other.accessors, other.accessorNodes, onOtherNode = { _, _ -> }
            ) { _, thisNode, otherNode ->
                thisNode.mergeAdd(otherNode)
            }
            if (
                isAbstract == this.isAbstract
                && isFinal == this.isFinal
                && mergedAccessors == null
            ) {
                return this
            }

            val accessors = mergedAccessors?.first ?: accessors
            val accessorNodes = mergedAccessors?.second ?: accessorNodes

            return manager.create(isAbstract, isFinal, accessors, accessorNodes)
        }

        fun mergeAddDelta(other: AccessNode): Pair<AccessNode, AccessNode?> {
            if (this === other) return this to null

            val isFinal = this.isFinal || other.isFinal
            val isFinalDelta = !this.isFinal && other.isFinal

            val isAbstract = this.isAbstract || other.isAbstract
            val isAbstractDelta = !this.isAbstract && other.isAbstract

            val deltaAccessors = IntArrayList()
            val deltaAccessorNodes = arrayListOf<AccessNode>()

            val mergedAccessors = mergeAccessors(
                other.accessors, other.accessorNodes,
                onOtherNode = { field, node ->
                    deltaAccessors.add(field)
                    deltaAccessorNodes.add(node)
                }
            ) { field, thisNode, otherNode ->
                val (addedNode, addedNodeDelta) = thisNode.mergeAddDelta(otherNode)

                if (addedNodeDelta != null) {
                    deltaAccessors.add(field)
                    deltaAccessorNodes.add(addedNodeDelta)
                }

                addedNode
            }

            if (
                isAbstract == this.isAbstract
                && isFinal == this.isFinal
                && mergedAccessors == null
            ) {
                return this to null
            }

            val delta = manager.create(
                isAbstractDelta, isFinalDelta,
                deltaAccessors.toIntArray(), deltaAccessorNodes.toTypedArray(),
            ).takeIf { !it.isEmpty }

            val accessors = mergedAccessors?.first ?: accessors
            val accessorNodes = mergedAccessors?.second ?: accessorNodes

            return manager.create(isAbstract, isFinal, accessors, accessorNodes) to delta
        }

        fun filterAccessNode(filter: FactTypeChecker.FactApFilter): AccessNode? = with(manager) {
            var result = transformAccessors { accessor, accessNode ->
                when (val status = filter.check(accessor.accessor)) {
                    FactTypeChecker.FilterResult.Accept -> accessNode
                    FactTypeChecker.FilterResult.Reject -> null
                    is FactTypeChecker.FilterResult.FilterNext -> accessNode.filterAccessNode(status.filter)
                }
            }

            if (result.isFinal) {
                result = when (filter.check(FinalAccessor)) {
                    FactTypeChecker.FilterResult.Accept -> result
                    is FactTypeChecker.FilterResult.FilterNext -> result
                    FactTypeChecker.FilterResult.Reject -> result.clearChild(FINAL_ACCESSOR_IDX)
                }
            }

            return result.takeIf { !it.isEmpty }
        }

        fun filterAccessNode(
            checker: FactTypeChecker.FactCompatibilityFilter,
        ): AccessNode? {
            val interned = internNodes(AccessTreeInterner(), IdentityHashMap())
            return interned.filterAccessNodeCached(checker, IdentityHashMap())
        }

        fun filterAccessNodeCached(
            checker: FactTypeChecker.FactCompatibilityFilter,
            cache: IdentityHashMap<AccessNode, AccessNode>
        ): AccessNode? {
           cache[this]?.let { return it }

            val result = filterAccessNodeBody(checker, cache)
                ?: return null

            cache[this] = result
            return result
        }

        fun filterAccessNodeBody(
            checker: FactTypeChecker.FactCompatibilityFilter,
            cache: IdentityHashMap<AccessNode, AccessNode>,
        ): AccessNode? {
            return transformAccessorsNonEmpty { accessor, node ->
                val checkedNode = node.filterAccessNodeCached(checker, cache)
                    ?: return@transformAccessorsNonEmpty null

                if (!checkedNode.isAbstract) {
                    return@transformAccessorsNonEmpty checkedNode
                }

                val checkResult = with(manager) { checker.check(accessor.accessor) }
                when (checkResult) {
                    is FactTypeChecker.CompatibilityFilterResult.Compatible -> {
                        return@transformAccessorsNonEmpty checkedNode
                    }

                    is FactTypeChecker.CompatibilityFilterResult.NotCompatible -> {
                        return@transformAccessorsNonEmpty checkedNode.removeAbstraction().takeIf { !it.isEmpty }
                    }
                }
            }
        }

        fun concatToLeafAbstractNodes(
            typeChecker: FactTypeChecker,
            other: AccessNode
        ): AccessNode? {
            val filteredOther = FilteredNode.create(manager, other)

            return concatToLeafAbstractNodes(
                typeChecker, filteredOther, IntArrayList(), SUBSEQUENT_ARRAY_ELEMENTS_LIMIT,
            )
        }

        fun internNodes(
            interner: AccessTreeInterner,
            cache: IdentityHashMap<AccessNode, AccessNode>,
        ): AccessNode = internNodesWithCache(interner, cache)

        private fun internNodesWithCache(
            interner: AccessTreeInterner,
            cache: IdentityHashMap<AccessNode, AccessNode>,
        ): AccessNode {
            cache[this]?.let { return it }

            manager.cancellation.checkpoint()

            return internNodesDeep(interner, cache).also {
                cache[this] = it
            }
        }

        private fun internNodesDeep(
            interner: AccessTreeInterner,
            cache: IdentityHashMap<AccessNode, AccessNode>,
        ): AccessNode {
            if (interned) return this

            fun transformNode(@Suppress("unused") accessor: AccessorIdx, node: AccessNode): AccessNode =
                node.internNodesWithCache(interner, cache)

            val nodeWithAccessorNodesInterned = transformAccessors(::transformNode)
            val internedNode = nodeWithAccessorNodesInterned.markInterned()

            return interner.intern(internedNode)
        }

        private fun markInterned() = AccessNode(
            manager,
            interned = true,
            isAbstract = isAbstract,
            isFinal = isFinal,
            accessors = accessors,
            accessorNodes = accessorNodes
        )

        private class FilteredNode(
            val manager: TreeApManager,
            val node: AccessNode,
            val allNodeAccessors: IdentityHashMap<AccessNode, IntOpenHashSet>,
            val cache: IdentityHashMap<AccessNode, Int2ObjectOpenHashMap<Optional<Pair<AccessNode, List<IntObjectImmutablePair<AccessNode>>>>>>,
            val typeFilterCache: IdentityHashMap<AccessNode, MutableMap<FactTypeChecker.FactApFilter, Optional<AccessNode>>>,
        ) {
            private fun updateNode(node: AccessNode) =
                FilteredNode(manager, node, allNodeAccessors, cache, typeFilterCache)

            fun filterTypes(typeChecker: FactTypeChecker, path: IntArrayList): FilteredNode? {
                val accessorPath = with(manager) { path.map { it.accessor } }
                val filter = typeChecker.accessPathFilter(accessorPath)

                val nodeCache = typeFilterCache.getOrPut(node, ::hashMapOf)
                val filterCache = nodeCache[filter]
                if (filterCache != null) {
                    val filteredNode = filterCache.getOrNull()
                        ?: return null

                    return updateNode(filteredNode)
                }

                val filteredNode = node.filterAccessNode(filter)

                if (filteredNode == null) {
                    nodeCache[filter] = Optional.empty()
                    return null
                }

                nodeCache[filter] = Optional.of(filteredNode)

                return updateNode(filteredNode)
            }

            fun limitFieldAccess(
                accessor: AccessorIdx,
                filteredNodes: MutableList<IntObjectImmutablePair<AccessNode>>
            ): FilteredNode? {
                val nodeAccessors = allNodeAccessors[node]
                if (nodeAccessors != null && !nodeAccessors.contains(accessor)) return this

                val nodeCache = cache.getOrPut(node, ::Int2ObjectOpenHashMap)
                val accessorResult = nodeCache.get(accessor)
                if (accessorResult != null) {
                    val unpackedResult = accessorResult.getOrNull()
                        ?: return null

                    filteredNodes += unpackedResult.second
                    return updateNode(unpackedResult.first)
                }

                val extractedNodes = mutableListOf<IntObjectImmutablePair<AccessNode>>()
                val filteredNode = node.limitFieldAccess(accessor, extractedNodes)
                if (filteredNode == null) {
                    nodeCache[accessor] = Optional.empty()
                    return null
                }

                if (nodeAccessors != null) {
                    val newAccessors = nodeAccessors.clone()
                    newAccessors.remove(accessor)
                    allNodeAccessors[filteredNode] = newAccessors
                }

                nodeCache[accessor] = Optional.of(filteredNode to extractedNodes)

                filteredNodes += extractedNodes
                return updateNode(filteredNode)
            }

            companion object {
                fun create(manager: TreeApManager, node: AccessNode): FilteredNode {
                    val internedNode = node.internNodes(AccessTreeInterner(), IdentityHashMap())
                    val allAccessors = IdentityHashMap<AccessNode, IntOpenHashSet>()
                    collectAllAccessors(internedNode, allAccessors)
                    return FilteredNode(manager, internedNode, allAccessors, IdentityHashMap(), IdentityHashMap())
                }

                private fun collectAllAccessors(
                    node: AccessNode,
                    cache: IdentityHashMap<AccessNode, IntOpenHashSet>
                ): IntOpenHashSet {
                    cache[node]?.let { return it }

                    val allAccessors = IntOpenHashSet()
                    node.forEachAccessor { accessor, child ->
                        allAccessors.add(accessor)
                        allAccessors += collectAllAccessors(child, cache)
                    }

                    cache[node] = allAccessors
                    return allAccessors
                }
            }
        }

        private fun concatToLeafAbstractNodes(
            typeChecker: FactTypeChecker,
            other: FilteredNode?,
            path: IntArrayList,
            subsequentArrayElementLimit: Int,
        ): AccessNode? {
            manager.cancellation.checkpoint()

            val concatNode = if (isAbstract && other != null) {
                other.filterTypes(typeChecker, path)
                    ?.node?.limitElementAccess(limit = subsequentArrayElementLimit)
            } else null

            val nestedAccessors = mutableListOf<IntObjectImmutablePair<AccessNode>>()

            forEachAccessor { accessor, node ->
                val filteredOther = if (accessor.isFieldAccessor()) {
                    other?.limitFieldAccess(accessor, nestedAccessors)
                } else {
                    other
                }

                val newSubsequentArrayLimit = if (accessor == ELEMENT_ACCESSOR_IDX) {
                    subsequentArrayElementLimit - 1
                } else {
                    SUBSEQUENT_ARRAY_ELEMENTS_LIMIT
                }

                path.add(accessor)
                val concatenatedNode = node.concatToLeafAbstractNodes(
                    typeChecker, filteredOther, path, newSubsequentArrayLimit,
                )
                path.removeLast()

                if (concatenatedNode != null) {
                    nestedAccessors.add(IntObjectImmutablePair(accessor, concatenatedNode))
                }
            }

            val resultNode = manager.create(isAbstract = false, isFinal, accessors = null, accessorNodes = null)
                .bulkMergeAddAccessors(nestedAccessors)

            val concatenatedNode = concatNode?.let { resultNode.mergeAdd(it) } ?: resultNode

            return concatenatedNode.takeIf { !it.isEmpty }
        }

        fun filterStartsWith(accessPath: AccessPath.AccessNode?): AccessNode? {
            if (accessPath == null) return this

            if (maxDepth < accessPath.size) {
                return null
            }

            val parentAccessors = IntArrayList()

            var filteredTreeNode = this
            var currentApNode: AccessPath.AccessNode = accessPath

            while (true) {
                val accessor = currentApNode.accessor

                filteredTreeNode = when (accessor) {
                    FINAL_ACCESSOR_IDX -> {
                        if (!filteredTreeNode.isFinal) return null

                        manager.finalNode
                    }

                    else -> {
                        filteredTreeNode.getChild(accessor)
                            ?.also { parentAccessors.add(accessor) }
                            ?: return null
                    }
                }

                currentApNode = currentApNode.next ?: break

                if (filteredTreeNode.maxDepth < currentApNode.size) {
                    return null
                }
            }

            return parentAccessors.foldRight(filteredTreeNode, ::create)
        }

        private inline fun mergeAccessors(
            otherFields: IntArray?,
            otherNodesE: Array<AccessNode>?,
            onOtherNode: (AccessorIdx, AccessNode) -> Unit,
            merge: (AccessorIdx, AccessNode, AccessNode) -> AccessNode
        ) = mergeAccessors(accessors, accessorNodes, otherFields, otherNodesE, onOtherNode, merge)

        private inline fun mergeAccessors(
            accessors: IntArray?,
            nodes: Array<AccessNode>?,
            otherAccessors: IntArray?,
            otherNodesE: Array<AccessNode>?,
            onOtherNode: (AccessorIdx, AccessNode) -> Unit,
            merge: (AccessorIdx, AccessNode, AccessNode) -> AccessNode
        ): Pair<IntArray, Array<AccessNode>>? {
            if (otherAccessors == null) return null
            val otherNodes = otherNodesE!!

            if (accessors == null) {
                for (i in otherAccessors.indices) {
                    onOtherNode(otherAccessors[i], otherNodes[i])
                }

                return otherAccessors to otherNodes
            }

            val thisAccessors = accessors
            val thisNodes = nodes!!

            var modified = false
            var accessorsModified = false

            var writeIdx = 0
            var thisIdx = 0
            var otherIdx = 0

            val mergedAccessors = IntArray(thisAccessors.size + otherAccessors.size)
            val mergedNodes = arrayOfNulls<AccessNode>(thisAccessors.size + otherAccessors.size)

            while (true) {
                val thisAccessor = thisAccessors.getOrElse(thisIdx) { -1 }
                val otherAccessor = otherAccessors.getOrElse(otherIdx) { -1 }

                if (thisAccessor == -1 && otherAccessor == -1) break

                val accessorsCmp = when {
                    otherAccessor == -1 -> -1 // thisField != null
                    thisAccessor == -1 -> 1 // otherField != null
                    else -> thisAccessor.compareTo(otherAccessor)
                }

                if (accessorsCmp < 0) {
                    mergedAccessors[writeIdx] = thisAccessor
                    mergedNodes[writeIdx] = thisNodes[thisIdx]
                    thisIdx++
                    writeIdx++
                } else if (accessorsCmp > 0) {
                    val otherNode = otherNodes[otherIdx]
                    onOtherNode(otherAccessor, otherNode)

                    modified = true
                    accessorsModified = true

                    mergedAccessors[writeIdx] = otherAccessor
                    mergedNodes[writeIdx] = otherNode
                    otherIdx++
                    writeIdx++
                } else {
                    val thisNode = thisNodes[thisIdx]
                    val otherNode = otherNodes[otherIdx]

                    val mergedNode = merge(thisAccessor, thisNode, otherNode)
                    if (mergedNode === thisNode) {
                        mergedAccessors[writeIdx] = thisAccessor
                        mergedNodes[writeIdx] = thisNode
                    } else {
                        modified = true
                        mergedAccessors[writeIdx] = thisAccessor
                        mergedNodes[writeIdx] = mergedNode
                    }

                    thisIdx++
                    otherIdx++
                    writeIdx++
                }
            }

            return trimModifiedAccessors(modified, accessorsModified, writeIdx, thisAccessors, mergedAccessors, mergedNodes)
        }

        private fun transformAccessorsNonEmpty(
            transformer: (AccessorIdx, AccessNode) -> AccessNode?
        ): AccessNode? = transformAccessors(transformer).takeIf { !it.isEmpty }

        private fun transformAccessors(
            transformer: (AccessorIdx, AccessNode) -> AccessNode?
        ): AccessNode {
            val newAccessors = transformAccessors(accessors, accessorNodes, transformer) ?: return this
            return manager.create(isAbstract, isFinal, newAccessors.first, newAccessors.second)
        }

        private fun limitFieldAccess(
            newRootField: AccessorIdx,
            filteredNodes: MutableList<IntObjectImmutablePair<AccessNode>>,
        ): AccessNode? {
            val cache = IdentityHashMap<AccessNode, AccessNode>()
            return limitFieldAccessCached(newRootField, filteredNodes, cache)
        }

        private fun limitFieldAccessCached(
            newRootField: AccessorIdx,
            filteredNodes: MutableList<IntObjectImmutablePair<AccessNode>>,
            cache: IdentityHashMap<AccessNode, AccessNode>,
        ): AccessNode? {
            cache[this]?.let { return it }

            manager.cancellation.checkpoint()

            val result = transformAccessorsNonEmpty { accessor, node ->
                if (accessor == newRootField) {
                    filteredNodes += IntObjectImmutablePair(accessor, node)
                    null
                } else {
                    node.limitFieldAccessCached(newRootField, filteredNodes, cache)
                }
            }

            cache[this] = result
            return result
        }

        fun removeAllAccessorChains(
            accessors: IntOpenHashSet,
            chainLengthToRemove: Int,
            cache: IdentityHashMap<AccessNode, AccessNode>,
            cancellation: Cancellation,
        ): AccessNode {
            cache[this]?.let { return it }

            cancellation.checkpoint()

            val removedNodes = mutableListOf<AccessNode>()
            val node = removeAllAccessorChains(accessors, removedNodes, chainLengthToRemove, 0, cache, cancellation)
            val mergedRemovedNode = removedNodes.reduceOrNull { acc, node -> acc.mergeAdd(node) }

            val result = if (mergedRemovedNode == null) {
                node ?: error("Impossible: No nodes removed")
            } else {
                node?.mergeAdd(mergedRemovedNode) ?: mergedRemovedNode
            }

            cache[this] = result
            return result
        }

        private fun removeAllAccessorChains(
            accessors: IntOpenHashSet,
            removedNodes: MutableList<AccessNode>,
            chainLengthToRemove: Int,
            currentChainLength: Int,
            cache: IdentityHashMap<AccessNode, AccessNode>,
            cancellation: Cancellation,
        ): AccessNode? {
            if (currentChainLength == chainLengthToRemove) {
                val node = removeAllAccessorChains(accessors, chainLengthToRemove, cache, cancellation)
                removedNodes += node
                return null
            }

            return transformAccessorsNonEmpty { accessor, node ->
                val transformed = node.removeAllAccessorChains(accessors, chainLengthToRemove, cache, cancellation)
                if (accessor !in accessors) return@transformAccessorsNonEmpty transformed

                transformed.removeAllAccessorChains(accessors, removedNodes, chainLengthToRemove, currentChainLength + 1, cache, cancellation)
            }
        }

        private fun removeSingleAccessor(accessor: AccessorIdx): AccessNode {
            val newAccessors = removeSingleAccessor(accessor, accessors, accessorNodes) ?: return this
            return manager.create(isAbstract, isFinal, newAccessors.first, newAccessors.second)
        }

        internal class Serializer(
            val manager: TreeApManager,
            private val context: SummarySerializationContext
        ) {
            fun DataOutputStream.writeAccessNode(node: AccessNode) {
                var mask = 0
                if (node.isFinal) {
                    mask += 1
                }
                if (node.isAbstract) {
                    mask += 2
                }
                write(mask)

                writeInt(node.accessors?.size ?: 0)
                if (node.accessors != null) {
                    node.accessors.forEach {
                        val accessor = with(manager) { it.accessor }
                        writeLong(context.getIdByAccessor(accessor))
                    }
                    node.accessorNodes!!.forEach { child ->
                        writeAccessNode(child)
                    }
                }
            }

            fun DataInputStream.readAccessNode(): AccessNode {
                val mask = read()
                val isFinal = mask.and(1) > 0
                val isAbstract = mask.and(2) > 0

                val accessorsSize = readInt()
                if (accessorsSize == 0) {
                    return manager.create(isAbstract, isFinal)
                }

                val deserializedAccessors = Array(accessorsSize) {
                    context.getAccessorById(readLong())
                }

                val deserializedAccessNodes = Array(accessorsSize) {
                    readAccessNode()
                }

                val accessorNodes = hashMapOf<Accessor, AccessNode>()
                deserializedAccessNodes.forEachIndexed { index, node ->
                    val accessor = deserializedAccessors[index]
                    accessorNodes[accessor] = node
                }

                val accessorIndices = IntArray(accessorsSize) {
                    with(manager) { deserializedAccessors[it].idx }
                }

                val accessors = accessorIndices.sortedArray()
                val accessNodes = Array(accessorsSize) { dstIdx ->
                    val dstAccessor = with(manager) { accessors[dstIdx].accessor }
                    accessorNodes[dstAccessor] ?: error("Accessor mismatch: $dstAccessor")
                }

                return AccessNode(manager, interned = false, isAbstract, isFinal, accessors, accessNodes)
            }
        }

        companion object {
            const val SUBSEQUENT_ARRAY_ELEMENTS_LIMIT = 2

            @JvmStatic
            private fun removeSingleAccessor(
                accessor: AccessorIdx,
                accessors: IntArray?,
                nodes: Array<AccessNode>?
            ): Pair<IntArray?, Array<AccessNode>?>? {
                if (accessors == null) {
                    return null
                }
                nodes!!

                val accessorIdx = accessors.binarySearch(accessor)
                if (accessorIdx < 0) return null

                val newAccessorsSize = accessors.size - 1
                if (newAccessorsSize == 0) {
                    return null to null
                }

                val newAccessors = IntArray(newAccessorsSize)
                val newNodes = arrayOfNulls<AccessNode>(newAccessorsSize)

                accessors.copyInto(newAccessors, endIndex = accessorIdx)
                accessors.copyInto(newAccessors, destinationOffset = accessorIdx, startIndex = accessorIdx + 1)

                nodes.copyInto(newNodes, endIndex = accessorIdx)
                nodes.copyInto(newNodes, destinationOffset = accessorIdx, startIndex = accessorIdx + 1)

                @Suppress("UNCHECKED_CAST")
                return newAccessors to newNodes as Array<AccessNode>
            }

            // Adding inline here leads to java.lang.VerifyError, seems to be issue with Kotlin compiler
            @JvmStatic
            private fun transformAccessors(
                accessors: IntArray?,
                nodes: Array<AccessNode>?,
                transformer: (AccessorIdx, AccessNode) -> AccessNode?,
            ): Pair<IntArray, Array<AccessNode>>? {
                if (accessors == null) return null
                nodes!!

                var modified = false
                var accessorsModified = false

                var writeIdx = 0
                val transformedAccessors = IntArray(nodes.size)
                val transformedNodes = arrayOfNulls<AccessNode>(nodes.size)

                for (i in nodes.indices) {
                    val field = accessors[i]
                    val node = nodes[i]

                    val transformedNode = transformer(field, node)
                    if (transformedNode === node) {
                        transformedAccessors[writeIdx] = field
                        transformedNodes[writeIdx] = node
                        writeIdx++
                    } else {
                        modified = true

                        if (transformedNode == null) {
                            accessorsModified = true
                            continue
                        }

                        transformedAccessors[writeIdx] = field
                        transformedNodes[writeIdx] = transformedNode
                        writeIdx++
                    }
                }

                return trimModifiedAccessors(modified, accessorsModified, writeIdx, accessors, transformedAccessors, transformedNodes)
            }

            private fun trimModifiedAccessors(
                modified: Boolean,
                accessorsModified: Boolean,
                writeIdx: Int,
                originalAccessors: IntArray,
                accessors: IntArray,
                nodes: Array<AccessNode?>
            ): Pair<IntArray, Array<AccessNode>>? {
                if (!modified) return null

                if (!accessorsModified) {
                    check(writeIdx == originalAccessors.size) { "Incorrect size" }

                    val trimmedNodes = if (writeIdx == nodes.size) {
                        nodes
                    } else {
                        nodes.copyOf(writeIdx)
                    }

                    @Suppress("UNCHECKED_CAST")
                    return originalAccessors to trimmedNodes as Array<AccessNode>
                }

                if (writeIdx != accessors.size) {
                    val trimmedAccessors = accessors.copyOf(writeIdx)
                    val trimmedNodes = nodes.copyOf(writeIdx)
                    @Suppress("UNCHECKED_CAST")
                    return trimmedAccessors to trimmedNodes as Array<AccessNode>
                } else {
                    @Suppress("UNCHECKED_CAST")
                    return accessors to nodes as Array<AccessNode>
                }
            }

            @JvmStatic
            fun TreeApManager.create(isAbstract: Boolean = false, isFinal: Boolean = false): AccessNode =
                if (isAbstract) {
                    if (isFinal) abstractFinalNode else abstractNode
                } else {
                    if (isFinal) finalNode else emptyNode
                }

            fun createInitialNode(
                manager: TreeApManager,
                isAbstract: Boolean,
                isFinal: Boolean
            ): AccessNode = AccessNode(
                manager,
                interned = true,
                isAbstract = isAbstract, isFinal = isFinal,
                accessors = null, accessorNodes = null
            )

            @JvmStatic
            private fun TreeApManager.create(elementAccess: AccessNode?): AccessNode =
                elementAccess?.let { access ->
                    create(ELEMENT_ACCESSOR_IDX, access)
                } ?: emptyNode

            @JvmStatic
            private fun create(accessor: AccessorIdx, node: AccessNode): AccessNode =
                AccessNode(
                    node.manager,
                    interned = false,
                    isAbstract = false, isFinal = false,
                    accessors = intArrayOf(accessor),
                    accessorNodes = arrayOf(node)
                )

            @JvmStatic
            private fun TreeApManager.create(
                isAbstract: Boolean,
                isFinal: Boolean,
                accessors: IntArray?,
                accessorNodes: Array<AccessNode>?
            ): AccessNode =
                if (isAbstract) {
                    if (isFinal) {
                        createElementAndField(abstractFinalNode, accessors, accessorNodes)
                    } else {
                        createElementAndField(abstractNode, accessors, accessorNodes)
                    }
                } else {
                    if (isFinal) {
                        createElementAndField(finalNode, accessors, accessorNodes)
                    } else {
                        createElementAndField(emptyNode, accessors, accessorNodes)
                    }
                }

            @JvmStatic
            private fun createElementAndField(
                base: AccessNode,
                accessors: IntArray?,
                accessorNodes: Array<AccessNode>?,
            ): AccessNode {
                val nonEmptyAccessors = accessors?.takeIf { it.isNotEmpty() }
                val nonEmptyAccessorNodes = accessorNodes?.takeIf { nonEmptyAccessors != null }
                return if (nonEmptyAccessors == null) {
                    base
                } else {
                    AccessNode(
                        base.manager,
                        interned = false,
                        isAbstract = base.isAbstract,
                        isFinal = base.isFinal,
                        accessors = nonEmptyAccessors,
                        accessorNodes = nonEmptyAccessorNodes
                    )
                }
            }

            @JvmStatic
            fun TreeApManager.createAbstractNodeFromReversedAp(reversedAp: ReversedApNode?): AccessNode =
                reversedAp.foldRight(abstractNode) { accessor, node ->
                    when (accessor) {
                        FINAL_ACCESSOR_IDX -> finalNode
                        else -> create(accessor, node)
                    }
                }

            fun AccessNode.extractMatchingSuffix(
                suffix: AccessPath.AccessNode?
            ): List<Pair<AccessNode, AccessPath.AccessNode?>> {
                if (suffix == null) return listOf(this to null)

                val interned = internNodes(AccessTreeInterner(), IdentityHashMap())
                return extractMatchingSuffix(interned, NodeManager(), suffix)
            }

            private class NodeManager {
                val allNodes = mutableListOf<AccessNode>()
                val nodeIndex = IdentityHashMap<AccessNode, Int>()

                fun nodeId(node: AccessNode): Int {
                    val nextIdx = allNodes.size
                    val nodeIndex = nodeIndex.putIfAbsent(node, nextIdx)
                    if (nodeIndex != null) return nodeIndex

                    allNodes.add(node)
                    return nextIdx
                }
            }

            private fun extractMatchingSuffix(
                rootNode: AccessNode,
                nodeManager: NodeManager,
                suffix: AccessPath.AccessNode
            ): List<Pair<AccessNode, AccessPath.AccessNode?>> {
                val predecessors = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<BitSet>>()

                rootNode.buildPredecessors(predecessors, BitSet(), nodeManager)

                val suffixAccessors = suffix.toList()

                val isFinal = suffixAccessors.getInt(suffixAccessors.lastIndex) == FINAL_ACCESSOR_IDX
                val suffixSize = if (isFinal) suffixAccessors.size - 1 else suffixAccessors.size

                val cutNodes = Array(suffixSize) { IntArrayList() }
                findCutNodes(nodeManager, suffixSize, isFinal, suffixAccessors, predecessors, cutNodes)

                val rootId = nodeManager.nodeId(rootNode)
                val cutNodeReplacement = rootNode.manager.create(isAbstract = true)

                return rebuildExtractedTrees(
                    nodeManager, rootId, cutNodeReplacement,
                    suffixAccessors, suffixSize, suffix, isFinal,
                    cutNodes, predecessors,
                )
            }

            private fun AccessNode.isReversedRoot(isFinal: Boolean): Boolean =
                if (isFinal) this.isFinal else this.isAbstract

            private fun findCutNodes(
                nodeManager: NodeManager,
                suffixSize: Int,
                isFinal: Boolean,
                suffixAccessors: IntArrayList,
                predecessors: Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<BitSet>>,
                cutNodeIds: Array<IntArrayList>
            ) {
                val nodeLevel = Int2IntOpenHashMap()
                val levelNodes = Array(suffixSize + 1) { IntOpenHashSet() }

                for ((i, node) in nodeManager.allNodes.withIndex()) {
                    if (!node.isReversedRoot(isFinal)) continue

                    matchWrtReversedSuffix(i, suffixAccessors, suffixSize, predecessors, nodeLevel, levelNodes)
                }

                findCutNodesIgnoringDominated(
                    nodeManager, levelNodes, suffixAccessors, suffixSize, isFinal, cutNodeIds
                )
            }

            private fun findCutNodesIgnoringDominated(
                nodeManager: NodeManager,
                nodeMatch: Array<IntOpenHashSet>,
                suffixAccessors: IntArrayList,
                suffixSize: Int,
                isFinal: Boolean,
                extractedNodeIds: Array<IntArrayList>,
            ) {
                val splitNodes: Array<AccessNode?> = nodeManager.allNodes.toTypedArray()

                for (k in 0 until suffixSize) {
                    val levelNodeIds = extractedNodeIds[k]

                    nodeMatch[k].forEachInt { nodeId ->
                        val node = splitNodes[nodeId] ?: return@forEachInt

                        val accessorToMatch = suffixAccessors.getInt(k)
                        if (node.getNodeByAccessor(accessorToMatch) == null) return@forEachInt

                        levelNodeIds.add(nodeId)
                        val remainder = removeSuffixTail(node, k, suffixAccessors, suffixSize, isFinal)
                        splitNodes[nodeId] = remainder
                    }
                }
            }

            private fun removeSuffixTail(
                node: AccessNode,
                suffixLevel: Int,
                suffixAccessors: IntArrayList,
                suffixSize: Int,
                isFinal: Boolean,
            ): AccessNode? {
                if (suffixLevel == suffixSize) {
                    val result = clearSuffixEnd(node, isFinal)
                    return result.takeUnless { it.isEmpty }
                }

                val accessorToMatch = suffixAccessors.getInt(suffixLevel)
                val child = node.getNodeByAccessor(accessorToMatch)
                    ?: return node

                val cleanedChild = removeSuffixTail(
                    child, suffixLevel + 1, suffixAccessors, suffixSize, isFinal
                )

                var cleanedNode = node.clearChild(accessorToMatch)
                if (cleanedChild != null) {
                    val childWithAccessor = create(accessorToMatch, cleanedChild)
                    cleanedNode = cleanedNode.mergeAdd(childWithAccessor)
                }

                return cleanedNode.takeUnless { it.isEmpty }
            }

            private fun clearSuffixEnd(node: AccessNode, isFinal: Boolean): AccessNode =
                if (isFinal) {
                    node.clearChild(FINAL_ACCESSOR_IDX)
                } else {
                    node.removeAbstraction()
                }

            private fun rebuildExtractedTrees(
                nodeManager: NodeManager,
                rootId: Int,
                cutNodeReplacement: AccessNode,
                suffixAccessors: IntArrayList,
                suffixSize: Int,
                fullSuffix: AccessPath.AccessNode,
                isFinal: Boolean,
                cutNodeIds: Array<IntArrayList>,
                predecessors: Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<BitSet>>,
            ): List<Pair<AccessNode, AccessPath.AccessNode?>> {
                val result = mutableListOf<Pair<AccessNode, AccessPath.AccessNode?>>()

                var nextSuffix: AccessPath.AccessNode? = fullSuffix
                val blockedEdges = LongOpenHashSet()
                for (k in 0 until suffixSize) {
                    val levelSuffix = nextSuffix ?: error("Suffix size exceeded")
                    nextSuffix = levelSuffix.next

                    val nodeIds = cutNodeIds[k]
                    if (nodeIds.isEmpty) continue

                    val subTree = rebuildFromCutNodes(rootId, nodeIds, cutNodeReplacement, predecessors, blockedEdges)
                            ?: continue

                    result.add(subTree to levelSuffix)

                    val accessor = levelSuffix.accessor
                    nodeIds.forEachInt { nodeId ->
                        blockedEdges.add(encodeEdge(nodeId, accessor))
                    }
                }

                val rootNode = nodeManager.allNodes[rootId]
                var remainder: AccessNode? = rootNode
                for ((prefix, matchedSuffix) in result) {
                    if (remainder == null) break
                    remainder = subtractWithSuffix(remainder, prefix, matchedSuffix)
                }
                if (remainder != null) {
                    result.add(remainder to null)
                }

                return result
            }

            private fun encodeEdge(nodeId: Int, accessor: Int): Long =
                (nodeId.toLong() shl Int.SIZE_BITS) or (accessor.toLong() and 0xFFFFFFFFL)

            private fun subtractWithSuffix(
                from: AccessNode,
                prefix: AccessNode,
                suffix: AccessPath.AccessNode?
            ): AccessNode? {
                var current = from

                if (prefix.isAbstract) {
                    current = subtractSuffixChain(current, suffix) ?: return null
                }

                prefix.forEachAccessor { accessor, prefixChild ->
                    val fromChild = current.getNodeByAccessor(accessor) ?: return@forEachAccessor
                    val remaining = subtractWithSuffix(fromChild, prefixChild, suffix)

                    current = current.clearChild(accessor)
                    if (remaining != null) {
                        current = current.mergeAdd(create(accessor, remaining))
                    }
                }

                return current.takeUnless { it.isEmpty }
            }

            private fun subtractSuffixChain(
                from: AccessNode,
                suffix: AccessPath.AccessNode?
            ): AccessNode? {
                if (suffix == null) {
                    val result = from.removeAbstraction()
                    return result.takeUnless { it.isEmpty }
                }

                val accessor = suffix.accessor
                val fromChild = from.getNodeByAccessor(accessor) ?: return from
                val remaining = subtractSuffixChain(fromChild, suffix.next)

                var current = from.clearChild(accessor)
                if (remaining != null) {
                    current = current.mergeAdd(create(accessor, remaining))
                }

                return current.takeUnless { it.isEmpty }
            }

            private fun rebuildFromCutNodes(
                rootId: Int,
                cutPointIds: IntArrayList,
                cutPointNode: AccessNode,
                predecessors: Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<BitSet>>,
                blockedEdges: LongOpenHashSet,
            ): AccessNode? {
                var currentLevel = Int2ObjectOpenHashMap<AccessNode>()
                cutPointIds.forEachInt { nodeId ->
                    currentLevel.put(nodeId, cutPointNode)
                }

                while (true) {
                    if (currentLevel.size == 1) {
                        val rootNode = currentLevel.get(rootId)
                        if (rootNode != null) {
                            return rootNode
                        }
                    }

                    val nextLevel = Int2ObjectOpenHashMap<AccessNode>()

                    currentLevel.forEachIntEntry { childId, childSubTree ->
                        if (childId == rootId) {
                            nextLevel.mergeValueNode(rootId, childSubTree)
                            return@forEachIntEntry
                        }

                        val childPredecessors = predecessors.get(childId)
                            ?: return@forEachIntEntry

                        childPredecessors.forEachIntEntry { accessor, parentIds ->
                            val edge = create(accessor, childSubTree)
                            parentIds.forEach { parentId ->
                                if (!blockedEdges.contains(encodeEdge(parentId, accessor))) {
                                    nextLevel.mergeValueNode(parentId, edge)
                                }
                            }
                        }
                    }

                    if (nextLevel.isEmpty()) return null
                    currentLevel = nextLevel
                }
            }

            private fun Int2ObjectOpenHashMap<AccessNode>.mergeValueNode(
                nodeId: Int, node: AccessNode
            ) {
                val existing = this.get(nodeId)
                this.put(nodeId, existing?.mergeAdd(node) ?: node)
            }

            private fun matchWrtReversedSuffix(
                nodeId: Int,
                suffix: IntArrayList,
                suffixLength: Int,
                predecessors: Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<BitSet>>,
                nodeLevel: Int2IntOpenHashMap,
                levelNodes: Array<IntOpenHashSet>,
            ) {
                val currentLevel = nodeLevel.getOrDefault(nodeId, Int.MAX_VALUE)
                if (currentLevel > suffixLength) {
                    if (currentLevel != Int.MAX_VALUE) {
                        levelNodes[currentLevel].remove(nodeId)
                    }

                    nodeLevel.put(nodeId, suffixLength)
                    levelNodes[suffixLength].add(nodeId)
                }

                if (suffixLength == 0) return

                val accessor = suffix.getInt(suffixLength - 1)
                if (accessor == FINAL_ACCESSOR_IDX) {
                    error("Impossible")
                }

                val nodePredecessors = predecessors.get(nodeId)
                    ?: return

                val accessorPredecessors = nodePredecessors.get(accessor)
                    ?: return

                accessorPredecessors.forEach {
                    matchWrtReversedSuffix(it, suffix, suffixLength - 1, predecessors, nodeLevel, levelNodes)
                }
            }

            private fun AccessNode.buildPredecessors(
                predecessors: Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<BitSet>>,
                visited: BitSet,
                nodeManager: NodeManager,
            ) {
                val nodeId = nodeManager.nodeId(this)
                if (!visited.add(nodeId)) return

                forEachAccessor { i, node ->
                    val childId = nodeManager.nodeId(node)
                    val nodePredecessors = predecessors.getOrCreate(childId, ::Int2ObjectOpenHashMap)
                    val nodeIPredecessors = nodePredecessors.getOrCreate(i, ::BitSet)
                    nodeIPredecessors.add(nodeId)

                    node.buildPredecessors(predecessors, visited, nodeManager)
                }
            }
        }
    }
}
