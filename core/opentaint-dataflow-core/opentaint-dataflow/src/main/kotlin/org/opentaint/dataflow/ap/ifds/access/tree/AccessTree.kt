package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.ReversedApNode
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.foldRight
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.util.Cancellation
import java.io.DataInputStream
import java.io.DataOutputStream
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
        val result = hashSetOf<Accessor>()
        access.collectAccessorsTo(result)
        return result
    }

    override fun startsWithAccessor(accessor: Accessor): Boolean = access.contains(apManager, accessor)

    override fun getStartAccessors(): Set<Accessor> =
        buildSet { access.forEachAccessor { a, _ -> add(a) } }

    override fun isAbstract(): Boolean = access.isAbstract

    override fun readAccessor(accessor: Accessor): FinalFactAp? =
        access.getChild(apManager, accessor)?.let { AccessTree(apManager, base, it, exclusions) }

    override fun prependAccessor(accessor: Accessor): FinalFactAp =
        AccessTree(apManager, base, access.addParent(accessor, apManager.cancellation), exclusions)

    override fun clearAccessor(accessor: Accessor): FinalFactAp? {
        val newAccess = access.clearChild(accessor).takeIf { !it.isEmpty } ?: return null
        return AccessTree(apManager, base, newAccess, exclusions)
    }

    override fun removeAbstraction(): FinalFactAp? =
        access.removeAbstraction().takeIf { !it.isEmpty }?.let { AccessTree(apManager, base, it, exclusions) }

    override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? {
        val filteredAccess = access.filterAccessNode(filter) ?: return null
        return AccessTree(apManager, base, filteredAccess, exclusions)
    }

    override fun filterFact(filter: FactTypeChecker.FactCompatibilityFilter): FinalFactAp? {
        if (filter is FactTypeChecker.AlwaysCompatibleFilter) return this
        val filteredAccess = access.filterAccessNode(filter, apManager.cancellation) ?: return null
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
        for (accessor in otherAccess) {
            if (accessor == FinalAccessor) return node.isFinal
            node = node.getChild(apManager, accessor) ?: return false
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
        for (accessor in otherAccess) {
            if (accessor == FinalAccessor) {
                return node.isFinal && node.accessors == null
            }

            if (node.accessors?.size != 1) return false
            node = node.getChild(apManager, accessor) ?: return false
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
        override fun startsWithAccessor(accessor: Accessor): Boolean = node.contains(apManager, accessor)
        override fun getStartAccessors(): Set<Accessor> = node.accessors?.toHashSet() ?: emptySet()
        override fun getAllAccessors(): Set<Accessor> {
            val s = hashSetOf<Accessor>()
            node.collectAccessorsTo(s)
            return s
        }

        override fun readAccessor(accessor: Accessor): FinalFactAp.Delta? =
            node.getChild(apManager, accessor)?.let { NodeAccessTreeDelta(apManager, it) }

        override fun isAbstract(): Boolean = node.isAbstract
    }

    override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> {
        other as AccessPath

        if (base != other.base) return emptyList()

        var node = access
        val access = other.access
        if (access != null) {
            for (accessor in access) {
                if (accessor is FinalAccessor) {
                    if (!node.isFinal) return emptyList()
                    return listOf(EmptyAccessTreeDelta)
                }

                node = node.getChild(apManager, accessor) ?: return emptyList()
            }
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
                val concatenatedAccess = access.concatToLeafAbstractNodes(typeChecker, d.node, apManager.cancellation)
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
        @JvmField val interned: Boolean,
        @JvmField val isAbstract: Boolean,
        @JvmField val isFinal: Boolean,
        @JvmField val accessors: Array<Accessor>?,
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
                containsStatic = accessors.any { it is ClassStaticAccessor }
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

            forEachAccessor { field, child ->
                child.print(builder, prefix + field.toSuffix())
            }
        }

        inline fun forEachAccessor(body: (Accessor, AccessNode) -> Unit) {
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

        private fun accessorIndex(accessor: Accessor): Int {
            if (accessors == null) return -1
            return accessors.binarySearch(accessor)
        }

        private fun getNodeByAccessor(accessor: Accessor): AccessNode? =
            accessorNodes?.getOrNull(accessorIndex(accessor))

        fun containsAnyAccessor(): Boolean =
            accessorIndex(AnyAccessor) >= 0

        fun contains(apManager: TreeApManager, accessor: Accessor): Boolean {
            if (accessor is FinalAccessor) return isFinal

            val accessorIdx = accessorIndex(accessor)
            if (accessorIdx >= 0) return true

            val anyAccessorNode = getNodeByAccessor(AnyAccessor)
                ?: return false

            if (anyAccessorNode.contains(apManager, accessor)) return true
            return apManager.anyAccessorUnrollStrategy.unrollAccessor(accessor)
        }

        fun getChild(apManager: TreeApManager, accessor: Accessor): AccessNode? {
            if (accessor is FinalAccessor) return finalNode.takeIf { this.isFinal }

            val node = getNodeByAccessor(accessor)
            if (node != null) return node

            val anyAccessorNode = getNodeByAccessor(AnyAccessor)
                ?: return null

            val anyChild = anyAccessorNode.getNodeByAccessor(accessor)
            if (anyChild != null) return anyChild

            if (!apManager.anyAccessorUnrollStrategy.unrollAccessor(accessor)) return null

            val childWithAny = anyAccessorNode.addParentIfPossible(AnyAccessor, apManager.cancellation)
            val unrolled = childWithAny?.mergeAdd(anyAccessorNode) ?: anyAccessorNode
            return unrolled
        }

        fun addParentIfPossible(
            accessor: Accessor,
            cancellation: Cancellation
        ): AccessNode? {
            if (containsStatic) return null

            return when (accessor) {
                is FinalAccessor -> null
                is ElementAccessor -> create(elementAccess = limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT))
                is FieldAccessor -> addParentFieldAccess(accessor, cancellation)
                is ClassStaticAccessor -> create(accessor, this)
                is ValueAccessor -> {
                    if (accessors?.any { it !is TaintMarkAccessor } == true) {
                        return null
                    }

                    create(accessor, this)
                }

                is TaintMarkAccessor -> {
                    if (this == finalNode || this == abstractNode || this == abstractFinalNode) {
                        create(accessor, this)
                    } else {
                        null
                    }
                }

                is AnyAccessor -> prependAnyAccessor()
            }
        }

        fun addParent(accessor: Accessor, cancellation: Cancellation): AccessNode =
            addParentIfPossible(accessor, cancellation)
                ?: error("Impossible accessor")

        fun removeAbstraction(): AccessNode =
            create(isAbstract = false, isFinal, accessors, accessorNodes)

        private fun prependAnyAccessor(): AccessNode {
            val anyNode = getNodeByAccessor(AnyAccessor)
            val nextNode = if (anyNode == null) {
                this
            } else {
                removeSingleAccessor(AnyAccessor).mergeAdd(anyNode)
            }
            return create(AnyAccessor, nextNode)
        }

        private fun limitElementAccess(limit: Int): AccessNode {
            if (limit > 0) {
                return transformAccessors { accessor, accessNode ->
                    if (accessor is ElementAccessor) {
                        accessNode.limitElementAccess(limit - 1)
                    } else {
                        accessNode
                    }
                }
            }

            return collapseElementAccess().also {
                check(it.getNodeByAccessor(ElementAccessor) == null) { "Array element limit invariant failure" }
            }
        }

        private fun collapseElementAccess(): AccessNode {
            val elementAccess = getNodeByAccessor(ElementAccessor) ?: return this

            val collapsedElementAccess = elementAccess.collapseElementAccess()
            val result = removeSingleAccessor(ElementAccessor)
            return result.mergeAdd(collapsedElementAccess)
        }

        private fun addParentFieldAccess(
            newRootField: FieldAccessor,
            cancellation: Cancellation
        ): AccessNode {
            val filteredNodes = mutableListOf<Pair<FieldAccessor, AccessNode>>()
            val limitedThis = limitFieldAccess(newRootField, filteredNodes, cancellation)

            val resultNode = if (limitedThis != null) {
                create(newRootField, limitedThis)
            } else {
                emptyNode
            }

            return resultNode.bulkMergeAddAccessors(filteredNodes)
                .also { check(!it.isEmpty) { "Empty node after field normalization" } }
        }

        fun clearChild(accessor: Accessor): AccessNode = when (accessor) {
            FinalAccessor -> create(isAbstract, isFinal = false, accessors, accessorNodes)
            else -> removeSingleAccessor(accessor)
        }

        fun filter(exclusion: ExclusionSet.Concrete): AccessNode {
            val isFinal = this.isFinal && FinalAccessor !in exclusion

            val transformedAccessors = transformAccessors(accessors, accessorNodes) { accessor, node ->
                node.takeIf { accessor !in exclusion }
            }

            if (isFinal == this.isFinal && transformedAccessors == null) {
                return this
            }

            val accessors = transformedAccessors?.first ?: accessors
            val accessorNodes = transformedAccessors?.second ?: accessorNodes

            return create(isAbstract, isFinal, accessors, accessorNodes)
        }

        fun collectAccessorsTo(dst: MutableSet<Accessor>) {
            if (isFinal) {
                dst.add(FinalAccessor)
            }

            forEachAccessor { accessor, accessorNode ->
                if (accessor is AnyAccessor) {
                    // note: always ignore any accessor
                    return@forEachAccessor
                }

                dst.add(accessor)
                accessorNode.collectAccessorsTo(dst)
            }
        }

        private fun bulkMergeAddAccessors(accessors: List<Pair<Accessor, AccessNode>>): AccessNode {
            if (accessors.isEmpty()) return this

            val uniqueAccessors = mutableListOf<Pair<Accessor, AccessNode>>()
            val groupedUniqueAccessors = accessors.groupByTo(hashMapOf(), { it.first }, { it.second })

            for ((accessor, nodes) in groupedUniqueAccessors) {
                val mergedNodes = nodes.reduce { acc, node -> acc.mergeAdd(node) }
                uniqueAccessors.add(accessor to mergedNodes)
            }

            uniqueAccessors.sortBy { it.first }
            val addedAccessors = Array(uniqueAccessors.size) { uniqueAccessors[it].first }
            val addedNodes = Array(uniqueAccessors.size) { uniqueAccessors[it].second }

            val mergedAccessors = mergeAccessors(
                addedAccessors, addedNodes, onOtherNode = { _, _ -> }
            ) { _, thisNode, otherNode ->
                thisNode.mergeAdd(otherNode)
            }

            if (mergedAccessors == null) return this

            return create(isAbstract, isFinal, mergedAccessors.first, mergedAccessors.second)
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

            return create(isAbstract, isFinal, accessors, accessorNodes)
        }

        fun mergeAddDelta(other: AccessNode): Pair<AccessNode, AccessNode?> {
            if (this === other) return this to null

            val isFinal = this.isFinal || other.isFinal
            val isFinalDelta = !this.isFinal && other.isFinal

            val isAbstract = this.isAbstract || other.isAbstract
            val isAbstractDelta = !this.isAbstract && other.isAbstract

            val deltaAccessors = arrayListOf<Accessor>()
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

            val delta = create(
                isAbstractDelta, isFinalDelta,
                deltaAccessors.toTypedArray(), deltaAccessorNodes.toTypedArray(),
            ).takeIf { !it.isEmpty }

            val accessors = mergedAccessors?.first ?: accessors
            val accessorNodes = mergedAccessors?.second ?: accessorNodes

            return create(isAbstract, isFinal, accessors, accessorNodes) to delta
        }

        fun filterAccessNode(filter: FactTypeChecker.FactApFilter): AccessNode? {
            var result = transformAccessors { accessor, accessNode ->
                when (val status = filter.check(accessor)) {
                    FactTypeChecker.FilterResult.Accept -> accessNode
                    FactTypeChecker.FilterResult.Reject -> null
                    is FactTypeChecker.FilterResult.FilterNext -> accessNode.filterAccessNode(status.filter)
                }
            }

            if (result.isFinal) {
                result = when (filter.check(FinalAccessor)) {
                    FactTypeChecker.FilterResult.Accept -> result
                    is FactTypeChecker.FilterResult.FilterNext -> result
                    FactTypeChecker.FilterResult.Reject -> result.clearChild(FinalAccessor)
                }
            }

            return result.takeIf { !it.isEmpty }
        }

        fun filterAccessNode(
            checker: FactTypeChecker.FactCompatibilityFilter,
            cancellation: Cancellation,
        ): AccessNode? {
            val interned = internNodes(AccessTreeInterner(), IdentityHashMap(), cancellation)
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

                val checkResult = checker.check(accessor)
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
            other: AccessNode,
            cancellation: Cancellation
        ): AccessNode? {
            val filteredOther = FilteredNode.create(other, cancellation)

            return concatToLeafAbstractNodes(
                typeChecker, filteredOther, mutableListOf(), SUBSEQUENT_ARRAY_ELEMENTS_LIMIT, cancellation,
            )
        }

        fun internNodes(
            interner: AccessTreeInterner,
            cache: IdentityHashMap<AccessNode, AccessNode>,
            cancellation: Cancellation,
        ): AccessNode = internNodesWithCache(interner, cache, cancellation)

        private fun internNodesWithCache(
            interner: AccessTreeInterner,
            cache: IdentityHashMap<AccessNode, AccessNode>,
            cancellation: Cancellation,
        ): AccessNode {
            cache[this]?.let { return it }
            return internNodesDeep(interner, cache, cancellation).also {
                cache[this] = it
            }
        }

        private fun internNodesDeep(
            interner: AccessTreeInterner,
            cache: IdentityHashMap<AccessNode, AccessNode>,
            cancellation: Cancellation,
        ): AccessNode {
            if (interned) return this

            fun transformNode(@Suppress("unused") accessor: Accessor, node: AccessNode): AccessNode =
                node.internNodesWithCache(interner, cache, cancellation)

            val nodeWithAccessorNodesInterned = transformAccessors(::transformNode)
            val internedNode = nodeWithAccessorNodesInterned.markInterned()

            return interner.intern(internedNode)
        }

        private fun markInterned() = AccessNode(
            interned = true,
            isAbstract = isAbstract,
            isFinal = isFinal,
            accessors = accessors,
            accessorNodes = accessorNodes
        )

        private class FilteredNode(
            val node: AccessNode,
            val allNodeAccessors: IdentityHashMap<AccessNode, Set<Accessor>>,
            val cache: IdentityHashMap<AccessNode, MutableMap<Accessor, Optional<Pair<AccessNode, List<Pair<FieldAccessor, AccessNode>>>>>>,
            val typeFilterCache: IdentityHashMap<AccessNode, MutableMap<FactTypeChecker.FactApFilter, Optional<AccessNode>>>,
            val cancellation: Cancellation,
        ) {
            private fun updateNode(node: AccessNode) = FilteredNode(node, allNodeAccessors, cache, typeFilterCache, cancellation)

            fun filterTypes(typeChecker: FactTypeChecker, path: List<Accessor>): FilteredNode? {
                val filter = typeChecker.accessPathFilter(path)

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
                accessor: FieldAccessor,
                filteredNodes: MutableList<in Pair<FieldAccessor, AccessNode>>
            ): FilteredNode? {
                val nodeAccessors = allNodeAccessors[node]
                if (nodeAccessors != null && !nodeAccessors.contains(accessor)) return this

                val nodeCache = cache.getOrPut(node, ::hashMapOf)
                val accessorResult = nodeCache[accessor]
                if (accessorResult != null) {
                    val unpackedResult = accessorResult.getOrNull()
                        ?: return null

                    filteredNodes += unpackedResult.second
                    return updateNode(unpackedResult.first)
                }

                val extractedNodes = mutableListOf<Pair<FieldAccessor, AccessNode>>()
                val filteredNode = node.limitFieldAccess(accessor, extractedNodes, cancellation)
                if (filteredNode == null) {
                    nodeCache[accessor] = Optional.empty()
                    return null
                }

                if (nodeAccessors != null) {
                    allNodeAccessors[filteredNode] = nodeAccessors - accessor
                }

                nodeCache[accessor] = Optional.of(filteredNode to extractedNodes)

                filteredNodes += extractedNodes
                return updateNode(filteredNode)
            }

            companion object {
                fun create(node: AccessNode, cancellation: Cancellation): FilteredNode {
                    val internedNode = node.internNodes(AccessTreeInterner(), IdentityHashMap(), cancellation)
                    val allAccessors = IdentityHashMap<AccessNode, Set<Accessor>>()
                    collectAllAccessors(internedNode, allAccessors)
                    return FilteredNode(internedNode, allAccessors, IdentityHashMap(), IdentityHashMap(), cancellation)
                }

                private fun collectAllAccessors(node: AccessNode, cache: IdentityHashMap<AccessNode, Set<Accessor>>): Set<Accessor> {
                    cache[node]?.let { return it }

                    val allAccessors = hashSetOf<Accessor>()
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
            path: MutableList<Accessor>,
            subsequentArrayElementLimit: Int,
            cancellation: Cancellation,
        ): AccessNode? {
            cancellation.checkpoint()

            val concatNode = if (isAbstract && other != null) {
                other.filterTypes(typeChecker, path)
                    ?.node?.limitElementAccess(limit = subsequentArrayElementLimit)
            } else null

            val nestedAccessors = mutableListOf<Pair<Accessor, AccessNode>>()

            forEachAccessor { accessor, node ->
                val filteredOther = if (accessor is FieldAccessor) {
                    other?.limitFieldAccess(accessor, nestedAccessors)
                } else {
                    other
                }

                val newSubsequentArrayLimit = if (accessor is ElementAccessor) {
                    subsequentArrayElementLimit - 1
                } else {
                    SUBSEQUENT_ARRAY_ELEMENTS_LIMIT
                }

                path.add(accessor)
                val concatenatedNode = node.concatToLeafAbstractNodes(
                    typeChecker, filteredOther, path, newSubsequentArrayLimit, cancellation,
                )
                path.removeLast()

                if (concatenatedNode != null) {
                    nestedAccessors.add(accessor to concatenatedNode)
                }
            }

            val resultNode = create(isAbstract = false, isFinal, accessors = null, accessorNodes = null)
                .bulkMergeAddAccessors(nestedAccessors)

            val concatenatedNode = concatNode?.let { resultNode.mergeAdd(it) } ?: resultNode

            return concatenatedNode.takeIf { !it.isEmpty }
        }

        fun filterStartsWith(apManager: TreeApManager, accessPath: AccessPath.AccessNode?): AccessNode? {
            if (accessPath == null) return this

            if (maxDepth < accessPath.size) {
                return null
            }

            val parentAccessors = mutableListOf<Accessor>()

            var filteredTreeNode = this
            var currentApNode: AccessPath.AccessNode = accessPath

            while (true) {
                val accessor = currentApNode.accessor

                filteredTreeNode = when (accessor) {
                    FinalAccessor -> {
                        if (!filteredTreeNode.isFinal) return null

                        finalNode
                    }

                    else -> {
                        filteredTreeNode.getChild(apManager, accessor)
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
            otherFields: Array<Accessor>?,
            otherNodesE: Array<AccessNode>?,
            onOtherNode: (Accessor, AccessNode) -> Unit,
            merge: (Accessor, AccessNode, AccessNode) -> AccessNode
        ) = mergeAccessors(accessors, accessorNodes, otherFields, otherNodesE, onOtherNode, merge)

        private inline fun mergeAccessors(
            accessors: Array<Accessor>?,
            nodes: Array<AccessNode>?,
            otherAccessors: Array<Accessor>?,
            otherNodesE: Array<AccessNode>?,
            onOtherNode: (Accessor, AccessNode) -> Unit,
            merge: (Accessor, AccessNode, AccessNode) -> AccessNode
        ): Pair<Array<Accessor>, Array<AccessNode>>? {
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

            val mergedAccessors = arrayOfNulls<Accessor>(thisAccessors.size + otherAccessors.size)
            val mergedNodes = arrayOfNulls<AccessNode>(thisAccessors.size + otherAccessors.size)

            while (true) {
                val thisAccessor = thisAccessors.getOrNull(thisIdx)
                val otherAccessor = otherAccessors.getOrNull(otherIdx)

                if (thisAccessor == null && otherAccessor == null) break

                val accessorsCmp = when {
                    otherAccessor == null -> -1 // thisField != null
                    thisAccessor == null -> 1 // otherField != null
                    else -> thisAccessor.compareTo(otherAccessor)
                }

                if (accessorsCmp < 0) {
                    mergedAccessors[writeIdx] = thisAccessor
                    mergedNodes[writeIdx] = thisNodes[thisIdx]
                    thisIdx++
                    writeIdx++
                } else if (accessorsCmp > 0) {
                    val otherNode = otherNodes[otherIdx]
                    onOtherNode(otherAccessor!!, otherNode)

                    modified = true
                    accessorsModified = true

                    mergedAccessors[writeIdx] = otherAccessor
                    mergedNodes[writeIdx] = otherNode
                    otherIdx++
                    writeIdx++
                } else {
                    val thisNode = thisNodes[thisIdx]
                    val otherNode = otherNodes[otherIdx]

                    val mergedNode = merge(thisAccessor!!, thisNode, otherNode)
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
            transformer: (Accessor, AccessNode) -> AccessNode?
        ): AccessNode? = transformAccessors(transformer).takeIf { !it.isEmpty }

        private fun transformAccessors(
            transformer: (Accessor, AccessNode) -> AccessNode?
        ): AccessNode {
            val newAccessors = transformAccessors(accessors, accessorNodes, transformer) ?: return this
            return create(isAbstract, isFinal, newAccessors.first, newAccessors.second)
        }

        private fun limitFieldAccess(
            newRootField: FieldAccessor,
            filteredNodes: MutableList<in Pair<FieldAccessor, AccessNode>>,
            cancellation: Cancellation,
        ): AccessNode? {
            val cache = IdentityHashMap<AccessNode, AccessNode>()
            return limitFieldAccessCached(newRootField, filteredNodes, cache, cancellation)
        }

        private fun limitFieldAccessCached(
            newRootField: FieldAccessor,
            filteredNodes: MutableList<in Pair<FieldAccessor, AccessNode>>,
            cache: IdentityHashMap<AccessNode, AccessNode>,
            cancellation: Cancellation,
        ): AccessNode? {
            cache[this]?.let { return it }

            cancellation.checkpoint()

            val result = transformAccessorsNonEmpty { accessor, node ->
                if (accessor is FieldAccessor && accessor == newRootField) {
                    filteredNodes += accessor to node
                    null
                } else {
                    node.limitFieldAccessCached(newRootField, filteredNodes, cache, cancellation)
                }
            }

            cache[this] = result
            return result
        }

        fun removeAllAccessorChains(
            accessors: Set<Accessor>,
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
            accessors: Set<Accessor>,
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

        private fun removeSingleAccessor(accessor: Accessor): AccessNode {
            val newAccessors = removeSingleAccessor(accessor, accessors, accessorNodes) ?: return this
            return create(isAbstract, isFinal, newAccessors.first, newAccessors.second)
        }

        internal class Serializer(private val context: SummarySerializationContext) {
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
                        writeLong(context.getIdByAccessor(it))
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
                    return create(isAbstract, isFinal)
                }

                val accessors = Array(accessorsSize) {
                    context.getAccessorById(readLong())
                }

                val accessNodes = Array(accessorsSize) {
                    readAccessNode()
                }

                return AccessNode(interned = false, isAbstract, isFinal, accessors, accessNodes)
            }
        }

        companion object {
            const val SUBSEQUENT_ARRAY_ELEMENTS_LIMIT = 2

            private val emptyNode = AccessNode(
                interned = true,
                isAbstract = false, isFinal = false,
                accessors = null, accessorNodes = null
            )

            private val abstractNode = AccessNode(
                interned = true,
                isAbstract = true, isFinal = false,
                accessors = null, accessorNodes = null
            )

            private val finalNode = AccessNode(
                interned = true,
                isAbstract = false, isFinal = true,
                accessors = null, accessorNodes = null
            )

            private val abstractFinalNode = AccessNode(
                interned = true,
                isAbstract = true, isFinal = true,
                accessors = null, accessorNodes = null
            )

            fun abstractNode(): AccessNode = abstractNode

            @JvmStatic
            private fun removeSingleAccessor(
                accessor: Accessor,
                accessors: Array<Accessor>?,
                nodes: Array<AccessNode>?
            ): Pair<Array<Accessor>?, Array<AccessNode>?>? {
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

                val newAccessors = arrayOfNulls<Accessor>(newAccessorsSize)
                val newNodes = arrayOfNulls<AccessNode>(newAccessorsSize)

                accessors.copyInto(newAccessors, endIndex = accessorIdx)
                accessors.copyInto(newAccessors, destinationOffset = accessorIdx, startIndex = accessorIdx + 1)

                nodes.copyInto(newNodes, endIndex = accessorIdx)
                nodes.copyInto(newNodes, destinationOffset = accessorIdx, startIndex = accessorIdx + 1)

                @Suppress("UNCHECKED_CAST")
                return newAccessors as Array<Accessor> to newNodes as Array<AccessNode>
            }

            // Adding inline here leads to java.lang.VerifyError, seems to be issue with Kotlin compiler
            @JvmStatic
            private fun transformAccessors(
                accessors: Array<Accessor>?,
                nodes: Array<AccessNode>?,
                transformer: (Accessor, AccessNode) -> AccessNode?,
            ): Pair<Array<Accessor>, Array<AccessNode>>? {
                if (accessors == null) return null
                nodes!!

                var modified = false
                var accessorsModified = false

                var writeIdx = 0
                val transformedAccessors = arrayOfNulls<Accessor>(nodes.size)
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
                originalAccessors: Array<Accessor>,
                accessors: Array<Accessor?>,
                nodes: Array<AccessNode?>
            ): Pair<Array<Accessor>, Array<AccessNode>>? {
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
                    return trimmedAccessors as Array<Accessor> to trimmedNodes as Array<AccessNode>
                } else {
                    @Suppress("UNCHECKED_CAST")
                    return accessors as Array<Accessor> to nodes as Array<AccessNode>
                }
            }

            @JvmStatic
            fun create(isAbstract: Boolean = false, isFinal: Boolean = false): AccessNode =
                if (isAbstract) {
                    if (isFinal) abstractFinalNode else abstractNode
                } else {
                    if (isFinal) finalNode else emptyNode
                }

            @JvmStatic
            private fun create(elementAccess: AccessNode?): AccessNode =
                elementAccess?.let { access ->
                    create(ElementAccessor, access)
                } ?: emptyNode

            @JvmStatic
            private fun create(accessor: Accessor, node: AccessNode): AccessNode =
                AccessNode(
                    interned = false,
                    isAbstract = false, isFinal = false,
                    accessors = arrayOf(accessor),
                    accessorNodes = arrayOf(node)
                )

            @JvmStatic
            private fun create(
                isAbstract: Boolean,
                isFinal: Boolean,
                accessors: Array<Accessor>?,
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
                accessors: Array<Accessor>?,
                accessorNodes: Array<AccessNode>?,
            ): AccessNode {
                val nonEmptyAccessors = accessors?.takeIf { it.isNotEmpty() }
                val nonEmptyAccessorNodes = accessorNodes?.takeIf { nonEmptyAccessors != null }
                return if (nonEmptyAccessors == null) {
                    base
                } else {
                    AccessNode(
                        interned = false,
                        isAbstract = base.isAbstract,
                        isFinal = base.isFinal,
                        accessors = nonEmptyAccessors,
                        accessorNodes = nonEmptyAccessorNodes
                    )
                }
            }

            @JvmStatic
            fun createAbstractNodeFromReversedAp(reversedAp: ReversedApNode?): AccessNode =
                reversedAp.foldRight(abstractNode) { accessor, node ->
                    when (accessor) {
                        FinalAccessor -> finalNode
                        else -> create(accessor, node)
                    }
                }
        }
    }
}
