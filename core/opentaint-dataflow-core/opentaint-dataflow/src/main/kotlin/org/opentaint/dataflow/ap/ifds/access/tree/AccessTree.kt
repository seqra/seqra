package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.access.DeepAccessorExclusion
import org.opentaint.dataflow.ap.ifds.access.DeepAccessorExclusion.Companion.addAccessorFromDepth0
import org.opentaint.dataflow.ap.ifds.access.DeepAccessorExclusion.Companion.addAccessorFromDepth1
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.ReversedApNode
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.foldRight
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ANY_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.TYPE_INFO_GROUP_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.VALUE_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldDepthAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isTaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isTypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.serialization.DeepExclusionsSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.forEachIntEntry
import org.opentaint.dataflow.util.getOrCreate
import org.opentaint.dataflow.util.reversedForEachInt
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.IdentityHashMap
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class AccessTree(
    val apManager: TreeApManager,
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

    override fun abstractOnly(): FinalFactAp =
        AccessTree(apManager, base, access.abstractOnly(), exclusions)

    override fun clearAllAccessorOccurrences(
        accessor: Accessor,
        keepStartAccessor: Boolean,
    ): FinalFactAp? {
        val accessorIdx = with(apManager) { accessor.idx }
        val cleared = access.clearAllAccessorOccurrences(
            accessorIdx, keepStartAccessor, retainDeepAccessorExclusions = exclusions !is ExclusionSet.Universe, IdentityHashMap()
        ) ?: return null

        return if (cleared === access) this else AccessTree(apManager, base, cleared, exclusions)
    }

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

        return access.contains(factAp.access)
    }

    override fun equalTo(factAp: InitialFactAp): Boolean {
        factAp as AccessPath

        if (base != factAp.base) return false

        return access.equalTo(factAp.access)
    }

    private sealed interface AccessTreeDelta : FinalFactAp.Delta

    data class EmptyAccessTreeDelta(
        val deepAccessorExclusion: DeepAccessorExclusion?,
    ) : AccessTreeDelta {
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
                return listOf(EmptyAccessTreeDelta(deepAccessorExclusion = null))
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

        return listOfNotNull(nonAbstractDelta, EmptyAccessTreeDelta(filteredNode.deepAccessorExclusion))
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? {
        when (val d = delta as AccessTreeDelta) {
            is EmptyAccessTreeDelta -> {
                val deepAccessorExclusion = d.deepAccessorExclusion ?: return this
                val annotated = access.annotateAbstractNodes(deepAccessorExclusion, IdentityHashMap())
                if (annotated === access) return this
                return AccessTree(apManager, base, annotated, exclusions)
            }
            is NodeAccessTreeDelta -> {
                val concatenatedAccess = access.concatToLeafAbstractNodes(typeChecker, d.node)
                    ?: return null
                return AccessTree(apManager, base, concatenatedAccess, exclusions)
            }
        }
    }

    override val size: Int
        get() = access.countNodes()

    override val depth: Int
        get() = access.maxDepth

    override fun exceedsFieldLimit(limit: Int): Boolean = access.maxFieldDepth > limit

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
        @JvmField val deepAccessorExclusion: DeepAccessorExclusion?,
        @JvmField val accessors: IntArray?,
        @JvmField val accessorNodes: Array<AccessNode>?,
    ) {
        @JvmField val hash: Long
        @JvmField val size: Long
        @JvmField val maxDepth: Int
        @JvmField val maxFieldDepth: Int
        @JvmField val containsStatic: Boolean

        init {
            check(deepAccessorExclusion == null || isAbstract) {
                "AnyFieldAccessorExclusions on a non-abstract node"
            }
        }

        init {
            var hash = 0L
            var depth = 0
            var fieldDepth = 0
            var containsStatic = false

            if (isAbstract) hash += 1
            if (deepAccessorExclusion != null) hash += deepAccessorExclusion.hashCode().toLong() shl 3

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

                for (i in accessorNodes.indices) {
                    val childFieldDepth = accessorNodes[i].maxFieldDepth
                    val edgeFieldDepth = if (accessors!![i].isFieldDepthAccessor()) childFieldDepth + 1 else childFieldDepth
                    if (edgeFieldDepth > fieldDepth) fieldDepth = edgeFieldDepth
                }

                containsStatic = containsStatic || accessorNodes.any { it.containsStatic }
            }

            if (containsAnyAccessor()) {
                depth += 10_000
            }

            this.hash = hash
            this.maxDepth = depth
            this.maxFieldDepth = fieldDepth
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
            if (deepAccessorExclusion != other.deepAccessorExclusion) return false

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
                    val annotation = deepAccessorExclusion?.toString().orEmpty()
                    appendLine("/*$annotation$suffix")
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

        private fun mergeAddMaybeNull(l: AccessNode?, r: AccessNode?): AccessNode? {
            if (l == null)
                return r
            if (r == null)
                return l
            return r.mergeAdd(l)
        }

        fun getChild(accessor: AccessorIdx): AccessNode? {
            if (accessor == FINAL_ACCESSOR_IDX) return manager.finalNode.takeIf { this.isFinal }

            val node = getNodeByAccessor(accessor)

            val anyAccessorNode = getNodeByAccessor(ANY_ACCESSOR_IDX)
                ?: return node

            val anyChild = anyAccessorNode.getNodeByAccessor(accessor)
            var resultNode = mergeAddMaybeNull(anyChild, node)

            if (manager.isCoveredByAny(accessor)) {
                val anyAccessorNoRepeats = anyAccessorNode.clearChild(accessor)
                val originalAnyNoRepeats = anyAccessorNoRepeats.addParentIfPossible(ANY_ACCESSOR_IDX)
                resultNode = mergeAddMaybeNull(originalAnyNoRepeats, resultNode)
            }

            return resultNode
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

                accessor == TYPE_INFO_GROUP_ACCESSOR_IDX -> create(accessor, this)
                accessor.isTypeInfoAccessor() -> create(accessor, this)

                else -> error("Unsupported accessor: $accessor")
            }
        }

        fun equalTo(otherAccess: AccessPath.AccessNode?): Boolean {
            if (otherAccess == null) {
                return isEmptyAbstract
            }

            var node = this
            otherAccess.toList().forEachInt { accessor ->
                if (accessor == FINAL_ACCESSOR_IDX) {
                    return node.isFinal && node.accessors == null
                }

                if (node.accessors?.size != 1) return false
                node = node.getChild(accessor) ?: return false
            }

            return node.isEmptyAbstract
        }

        fun contains(otherAccess: AccessPath.AccessNode?): Boolean {
            if (otherAccess == null) {
                return isAbstract
            }

            var node = this
            otherAccess.toList().forEachInt { accessor ->
                if (accessor == FINAL_ACCESSOR_IDX) return node.isFinal
                node = node.getChild(accessor) ?: return false
            }
            return node.isAbstract
        }

        sealed interface MatchResult {
            data object NotMatched : MatchResult
            data class MatchedWithRemainder(val remainder: AccessNode?) : MatchResult
        }

        fun splitOnMatching(otherAccess: AccessPath.AccessNode?): MatchResult  {
            if (otherAccess == null) {
                if (!isAbstract || deepAccessorExclusion != null) return MatchResult.NotMatched

                val remainder = removeAbstraction().takeIf { !it.isEmpty }
                return MatchResult.MatchedWithRemainder(remainder)
            }

            val accessorsOnPath = otherAccess.toList()

            var node = this
            accessorsOnPath.forEachInt { accessor ->
                if (accessor == FINAL_ACCESSOR_IDX) {
                    if (!node.isFinal) return MatchResult.NotMatched

                    val remainder = this.reconstructRemainder(accessorsOnPath, idx = 0)
                    return MatchResult.MatchedWithRemainder(remainder)
                }

                node = node.getNodeByAccessor(accessor)
                    ?: return MatchResult.NotMatched
            }

            if (!node.isAbstract || node.deepAccessorExclusion != null) return MatchResult.NotMatched

            val remainder = this.reconstructRemainder(accessorsOnPath, idx = 0)
            return MatchResult.MatchedWithRemainder(remainder)
        }

        private fun reconstructRemainder(accessors: IntList, idx: Int): AccessNode? {
            if (idx == accessors.size) {
                return removeAbstraction().takeIf { !it.isEmpty }
            }

            val accessor = accessors.getInt(idx)

            val levelRemainder = clearChild(accessor)
                .takeIf { !it.isEmpty }

            if (accessor == FINAL_ACCESSOR_IDX) {
                return levelRemainder
            }

            val childRemainder = getNodeByAccessor(accessor)
                ?.reconstructRemainder(accessors, idx + 1)
                ?.takeIf { !it.isEmpty }
                ?.let { create(accessor, it) }

            if (levelRemainder == null) return childRemainder
            if (childRemainder == null) return levelRemainder
            return levelRemainder.mergeAdd(childRemainder)
        }

        fun addParent(accessor: AccessorIdx): AccessNode =
            addParentIfPossible(accessor)
                ?: error("Impossible accessor")

        fun removeAbstraction(): AccessNode =
            manager.create(isAbstract = false, isFinal, deepAccessorExclusion = null, accessors, accessorNodes)

        private fun AccessNode.filterDeepExclusion(deepAccessorExclusion: DeepAccessorExclusion?): AccessNode? {
            if (deepAccessorExclusion == null) return this

            var filtered: AccessNode = this
            deepAccessorExclusion.accessorsFromDepth0.forEach {
                filtered = filtered.clearAllAccessorOccurrences(it, keepStartAccessor = false, cache = IdentityHashMap()) ?: return null
            }
            deepAccessorExclusion.accessorsFromDepth1.forEach {
                filtered = filtered.clearAllAccessorOccurrences(it, keepStartAccessor = true, cache = IdentityHashMap()) ?: return null
            }

            val belowClaim = deepAccessorExclusion.collapseToDepth0()
            val cache = IdentityHashMap<AccessNode, AccessNode>()
            var annotated = filtered.transformAccessors { _, node ->
                node.annotateAbstractNodes(belowClaim, cache)
            }
            if (annotated.isAbstract) {
                val merged = DeepAccessorExclusion.merge(annotated.deepAccessorExclusion, deepAccessorExclusion)
                annotated = annotated.updateDeepExclusion(merged)
            }
            return annotated
        }

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
            FINAL_ACCESSOR_IDX -> manager.create(isAbstract, isFinal = false, deepAccessorExclusion, accessors, accessorNodes)
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

            return manager.create(isAbstract, isFinal, deepAccessorExclusion, accessors, accessorNodes)
        }

        fun clearAllAccessorOccurrences(
            accessorIdx: AccessorIdx,
            keepStartAccessor: Boolean,
            retainDeepAccessorExclusions: Boolean = true,
            cache: IdentityHashMap<AccessNode, AccessNode?>,
        ): AccessNode? {
            val transformed = transformAccessorsNonEmpty { currentAccessorIdx, node ->
                if (keepStartAccessor || currentAccessorIdx != accessorIdx) {
                    node.clearAccessorOccurrencesBelow(accessorIdx, retainDeepAccessorExclusions, cache)
                } else {
                    null
                }
            }

            return transformed?.let {
                if (retainDeepAccessorExclusions) {
                    it.annotateAnyFieldAccessorExclusion(accessorIdx, keepStartAccessor)
                } else {
                    it.updateDeepExclusion(null)
                }
            }
        }

        private fun clearAccessorOccurrencesBelow(
            accessorIdx: AccessorIdx,
            retainDeepAccessorExclusions: Boolean,
            cache: IdentityHashMap<AccessNode, AccessNode?>,
        ): AccessNode? {
            if (cache.containsKey(this)) return cache[this]
            manager.cancellation.checkpoint()

            val transformed = transformAccessorsNonEmpty { currentAccessorIdx, node ->
                if (currentAccessorIdx == accessorIdx) null
                else node.clearAccessorOccurrencesBelow(accessorIdx, retainDeepAccessorExclusions, cache)
            }

            val result = transformed?.let {
                if (retainDeepAccessorExclusions) {
                    it.annotateAnyFieldAccessorExclusion(accessorIdx, keepCurrentNodeAccessor = false)
                } else {
                    it.updateDeepExclusion(null)
                }
            }

            cache[this] = result
            return result
        }

        private fun annotateAnyFieldAccessorExclusion(accessorIdx: AccessorIdx, keepCurrentNodeAccessor: Boolean): AccessNode {
            if (!isAbstract) return this

            val annotated = if (keepCurrentNodeAccessor) {
                deepAccessorExclusion.addAccessorFromDepth1(accessorIdx)
            } else {
                deepAccessorExclusion.addAccessorFromDepth0(accessorIdx)
            }

            return updateDeepExclusion(annotated)
        }

        private fun updateDeepExclusion(annotation: DeepAccessorExclusion?) =
            if (annotation == deepAccessorExclusion) {
                this
            } else {
                manager.create(isAbstract, isFinal, annotation, accessors, accessorNodes)
            }

        fun abstractOnly(): AccessNode =
            manager.create(isAbstract = true, isFinal = false, deepAccessorExclusion, accessors = null, accessorNodes = null)

        fun annotateAbstractNodes(
            incoming: DeepAccessorExclusion,
            cache: IdentityHashMap<AccessNode, AccessNode>,
        ): AccessNode {
            cache[this]?.let { return it }

            manager.cancellation.checkpoint()

            val transformed = transformAccessors { _, node ->
                node.annotateAbstractNodes(incoming, cache)
            }

            val result = if (!transformed.isAbstract) {
                transformed
            } else {
                val merged = DeepAccessorExclusion.merge(transformed.deepAccessorExclusion, incoming)
                transformed.updateDeepExclusion(merged)
            }

            cache[this] = result
            return result
        }

        fun collectAccessorsTo(dst: IntOpenHashSet) {
            if (isFinal) {
                dst.add(FINAL_ACCESSOR_IDX)
            }

            forEachAccessor { accessor, accessorNode ->
                if (accessor != ANY_ACCESSOR_IDX) {
                    // note: always ignore any accessor
                    dst.add(accessor)
                }

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

            return manager.create(isAbstract, isFinal, deepAccessorExclusion, mergedAccessors.first, mergedAccessors.second)
        }

        private data class AccessNodeMergePair(val left: AccessNode, val right: AccessNode) {
            private val hash = System.identityHashCode(left) * 31 + System.identityHashCode(right)

            override fun hashCode(): Int = hash

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is AccessNodeMergePair) return false
                return left === other.left && right === other.right
            }
        }

        fun mergeAdd(other: AccessNode, foldToAny: Boolean = true): AccessNode =
            mergeNodeLoop(other, foldToAny, { it }) { a, b, results ->
                a.mergeAddStep(b, results)
            }

        private fun intersectDeepExclusion(other: AccessNode): DeepAccessorExclusion? = when {
            !this.isAbstract -> other.deepAccessorExclusion
            !other.isAbstract -> this.deepAccessorExclusion
            else -> DeepAccessorExclusion.intersect(this.deepAccessorExclusion, other.deepAccessorExclusion)
        }

        private fun mergeAddStep(
            other: AccessNode,
            results: Object2ObjectOpenHashMap<AccessNodeMergePair, AccessNode>
        ): AccessNode {
            val isAbstract = this.isAbstract || other.isAbstract
            val isFinal = this.isFinal || other.isFinal
            val deepExclusions = intersectDeepExclusion(other)

            val mergedAccessors = mergeAccessors(
                other.accessors, other.accessorNodes, onOtherNode = { _, _ -> }
            ) { _, thisNode, otherNode ->
                results.getComputedResult(AccessNodeMergePair(thisNode, otherNode))
            }
            if (
                isAbstract == this.isAbstract
                && isFinal == this.isFinal
                && deepExclusions == this.deepAccessorExclusion
                && mergedAccessors == null
            ) {
                return this
            }

            val accessors = mergedAccessors?.first ?: accessors
            val accessorNodes = mergedAccessors?.second ?: accessorNodes

            return manager.create(isAbstract, isFinal, deepExclusions, accessors, accessorNodes)
        }

        fun mergeAddDelta(other: AccessNode, foldToAny: Boolean = true): Pair<AccessNode, AccessNode?> =
            mergeNodeLoop<Pair<AccessNode, AccessNode?>>(other, foldToAny, { it to null }) { a, b, results ->
                a.mergeAddDeltaStep(b, results)
            }

        private fun mergeAddDeltaStep(
            other: AccessNode,
            results: Object2ObjectOpenHashMap<AccessNodeMergePair, Pair<AccessNode, AccessNode?>>,
        ): Pair<AccessNode, AccessNode?> {
            val isFinal = this.isFinal || other.isFinal
            val isFinalDelta = !this.isFinal && other.isFinal

            val isAbstract = this.isAbstract || other.isAbstract
            val deepExclusion = intersectDeepExclusion(other)

            val abstractionPointChanged = isAbstract != this.isAbstract || deepExclusion != this.deepAccessorExclusion
            val isAbstractDelta = abstractionPointChanged && isAbstract
            val deltaDeepExclusion = if (isAbstractDelta) deepExclusion else null

            val deltaAccessors = IntArrayList()
            val deltaAccessorNodes = arrayListOf<AccessNode>()

            val mergedAccessors = mergeAccessors(
                other.accessors, other.accessorNodes,
                onOtherNode = { field, node ->
                    deltaAccessors.add(field)
                    deltaAccessorNodes.add(node)
                }
            ) { field, thisNode, otherNode ->
                val (addedNode, addedNodeDelta) = results.getComputedResult(AccessNodeMergePair(thisNode, otherNode))

                if (addedNodeDelta != null) {
                    deltaAccessors.add(field)
                    deltaAccessorNodes.add(addedNodeDelta)
                }

                addedNode
            }

            if (
                !abstractionPointChanged
                && isFinal == this.isFinal
                && mergedAccessors == null
            ) {
                return this to null
            }

            val delta = manager.create(
                isAbstractDelta, isFinalDelta, deltaDeepExclusion,
                deltaAccessors.toIntArray(), deltaAccessorNodes.toTypedArray(),
            ).takeIf { !it.isEmpty }

            val accessors = mergedAccessors?.first ?: accessors
            val accessorNodes = mergedAccessors?.second ?: accessorNodes

            return manager.create(isAbstract, isFinal, deepExclusion, accessors, accessorNodes) to delta
        }

        private inline fun <T: Any> mergeNodeLoop(
            other: AccessNode,
            foldToAny: Boolean,
            mergeSameNode: (AccessNode) -> T,
            mergeNodes: (AccessNode, AccessNode, cache: Object2ObjectOpenHashMap<AccessNodeMergePair, T>) -> T
        ): T {
            if (this === other) return mergeSameNode(this)

            val results = Object2ObjectOpenHashMap<AccessNodeMergePair, Any>()
            val stack = mutableListOf<AccessNodeMergePair>()

            val initial = AccessNodeMergePair(this, other)
            stack.add(initial)

            while (stack.isNotEmpty()) {
                val mergePair = stack.last()

                val (a, b) = mergePair
                if (a === b) {
                    results[mergePair] = mergeSameNode(a)
                    stack.removeLast()
                    continue
                }

                val currentResult = results.putIfAbsent(mergePair, NodeExpansionRequested)
                if (currentResult != null && currentResult !== NodeExpansionRequested) {
                    if (currentResult is AccessNodeMergePair) {
                        results[mergePair] = results[currentResult]
                    }
                    stack.removeLast()
                    continue
                }

                if (currentResult == null) {
                    if (foldToAny) {
                        trimAnyCoveredAndPushChildren(mergePair, stack, results)
                    }
                    else {
                        pushSharedChildPairs(a, b, stack)
                    }
                    continue
                }

                // currentResult === NodeExpansionRequested
                @Suppress("UNCHECKED_CAST")
                results[mergePair] = mergeNodes(a, b, results as Object2ObjectOpenHashMap<AccessNodeMergePair, T>)
                stack.removeLast()
            }

            @Suppress("UNCHECKED_CAST")
            return (results as Object2ObjectOpenHashMap<AccessNodeMergePair, T>).getComputedResult(initial)
        }

        private fun trimAnyCoveredAndPushChildren(
            mergePair: AccessNodeMergePair,
            stack: MutableList<AccessNodeMergePair>,
            results: Object2ObjectOpenHashMap<AccessNodeMergePair, Any>,
        ) {
            val (a, b) = mergePair

            if (a.accessors == null || b.accessors == null)
                return

            val aAccessorsUntrimmed = a.accessors
            val aNodesUntrimmed = a.accessorNodes!!

            val aAnyIdx = aAccessorsUntrimmed.indexOf(ANY_ACCESSOR_IDX)
            val bTrimmed =
                if (aAnyIdx >= 0)
                    AccessTreeAnySuffixMatcher(aNodesUntrimmed[aAnyIdx]).getNonMatchingNode(b)
                else b

            val bAccessorsUntrimmed = bTrimmed.accessors
            val bNodesUntrimmed = bTrimmed.accessorNodes

            val bAnyIdx = bAccessorsUntrimmed?.indexOf(ANY_ACCESSOR_IDX) ?: -1
            val aTrimmed =
                if (bAnyIdx >= 0)
                    AccessTreeAnySuffixMatcher(bNodesUntrimmed!![bAnyIdx]).getNonMatchingNode(a)
                else a

            if (aTrimmed !== a || bTrimmed !== b) {
                val trimmedPair = AccessNodeMergePair(aTrimmed, bTrimmed)
                results[mergePair] = trimmedPair
                stack.add(trimmedPair)
                results[trimmedPair] = NodeExpansionRequested
                pushSharedChildPairs(aTrimmed, bTrimmed, stack)
            }
            else {
                pushSharedChildPairs(a, b, stack)
            }
        }

        private fun pushSharedChildPairs(
            a: AccessNode,
            b: AccessNode,
            stack: MutableList<AccessNodeMergePair>,
        ) {
            val aAccessors = a.accessors ?: return
            val bAccessors = b.accessors ?: return
            val aNodes = a.accessorNodes!!
            val bNodes = b.accessorNodes!!

            var ai = 0
            var bi = 0
            while (ai < aAccessors.size && bi < bAccessors.size) {
                val cmp = aAccessors[ai].compareTo(bAccessors[bi])
                when {
                    cmp < 0 -> ai++
                    cmp > 0 -> bi++
                    else -> {
                        stack.add(AccessNodeMergePair(aNodes[ai], bNodes[bi]))
                        ai++
                        bi++
                    }
                }
            }
        }

        fun filterAccessNode(filter: FactTypeChecker.FactApFilter): AccessNode? = with(manager) {
            // An any-accessor matches zero or more accessors. If the filter rejects it, only the
            // empty match stays: remove the edge, keep its subtree at this node, then filter.
            val anyNode = getNodeByAccessor(ANY_ACCESSOR_IDX)
            if (anyNode != null && filter.check(ANY_ACCESSOR_IDX.accessor) === FactTypeChecker.FilterResult.Reject) {
                return removeSingleAccessor(ANY_ACCESSOR_IDX).mergeAdd(anyNode).filterAccessNode(filter)
            }

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
            return interned.filterAccessNodeCached(checker)
        }

        fun filterAccessNodeCached(
            checker: FactTypeChecker.FactCompatibilityFilter
        ): AccessNode? {
            val results = IdentityHashMap<AccessNode, AccessNode?>()
            val expanded = IdentityHashMap<AccessNode, Unit>()
            val stack = mutableListOf<AccessNode>()
            stack.add(this)

            while (stack.isNotEmpty()) {
                val node = stack.last()

                if (results.containsKey(node)) {
                    stack.removeLast()
                    continue
                }

                if (expanded.containsKey(node)) {
                    results[node] = node.filterChildren(checker, results)
                    stack.removeLast()
                    continue
                }

                expanded[node] = Unit
                node.accessorNodes?.forEach { stack.add(it) }
            }

            return results[this]
        }

        private fun filterChildren(
            checker: FactTypeChecker.FactCompatibilityFilter,
            childResults: IdentityHashMap<AccessNode, AccessNode?>,
        ): AccessNode? = transformAccessorsNonEmpty { accessor, child ->
            val checkedNode = childResults[child] ?: return@transformAccessorsNonEmpty null

            if (!checkedNode.isAbstract) {
                return@transformAccessorsNonEmpty checkedNode
            }

            val checkResult = with(manager) { checker.check(accessor.accessor) }
            when (checkResult) {
                is FactTypeChecker.CompatibilityFilterResult.Compatible ->
                    checkedNode

                is FactTypeChecker.CompatibilityFilterResult.NotCompatible ->
                    checkedNode.removeAbstraction().takeIf { !it.isEmpty }
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
            if (interned) return this

            val stack = mutableListOf<AccessNode>()
            val expanded = IdentityHashMap<AccessNode, Unit>()
            stack.add(this)

            while (stack.isNotEmpty()) {
                manager.cancellation.checkpoint()

                val node = stack.last()

                if (cache.containsKey(node)) {
                    stack.removeLast()
                    continue
                }

                if (expanded.containsKey(node)) {
                    val withInternedChildren = node.transformAccessors { _, child -> cache[child] }
                    cache[node] = interner.intern(withInternedChildren.markInterned())
                    stack.removeLast()
                    continue
                }

                if (node.interned) {
                    cache[node] = node
                    stack.removeLast()
                    continue
                }

                expanded[node] = Unit
                node.forEachAccessor { _, child ->
                    if (!cache.containsKey(child)) {
                        stack.add(child)
                    }
                }
            }

            return cache[this] ?: error("Impossible")
        }

        private fun markInterned() = AccessNode(
            manager,
            interned = true,
            isAbstract = isAbstract,
            isFinal = isFinal,
            deepAccessorExclusion = deepAccessorExclusion,
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
                    ?.filterDeepExclusion(deepAccessorExclusion)
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

            val resultNode = manager.create(isAbstract = false, isFinal, deepAccessorExclusion = null, accessors = null, accessorNodes = null)
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
            merge: (AccessorIdx, AccessNode, AccessNode) -> AccessNode,
        ): Pair<IntArray, Array<AccessNode>>? {
            if (otherFields == null) return null
            val otherNodesBeforeAny = otherNodesE!!

            if (accessors == null) {
                for (i in otherFields.indices) {
                    onOtherNode(otherFields[i], otherNodesBeforeAny[i])
                }

                return otherFields to otherNodesBeforeAny
            }

            return mergeAccessorsRaw(accessors, accessorNodes!!, otherFields, otherNodesE, onOtherNode, merge)
        }

        private inline fun mergeAccessorsRaw(
            thisAccessors: IntArray,
            thisNodes: Array<AccessNode>,
            otherAccessors: IntArray,
            otherNodes: Array<AccessNode>,
            onOtherNode: (AccessorIdx, AccessNode) -> Unit,
            merge: (AccessorIdx, AccessNode, AccessNode) -> AccessNode,
        ): Pair<IntArray, Array<AccessNode>>? {
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
            return manager.create(isAbstract, isFinal, deepAccessorExclusion, newAccessors.first, newAccessors.second)
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
            return manager.create(isAbstract, isFinal, deepAccessorExclusion, newAccessors.first, newAccessors.second)
        }

        internal class Serializer(
            val manager: TreeApManager,
            private val context: SummarySerializationContext
        ) {
            private val deepExclusionsSerializer = with(manager) {
                DeepExclusionsSerializer(context, { it.idx }, { it.accessor })
            }

            fun DataOutputStream.writeAccessNode(node: AccessNode) {
                var mask = 0
                if (node.isFinal) {
                    mask += 1
                }
                if (node.isAbstract) {
                    mask += 2
                }
                if (node.deepAccessorExclusion != null) {
                    mask += 4
                }
                write(mask)

                node.deepAccessorExclusion?.let {
                    with(deepExclusionsSerializer) {
                        writeAnyFieldAccessorExclusions(it)
                    }
                }

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

                val anyFieldAccessorExclusions = if (mask.and(4) > 0) {
                    with(deepExclusionsSerializer) {
                        readAnyFieldAccessorExclusions()
                    }
                } else {
                    null
                }

                val accessorsSize = readInt()
                if (accessorsSize == 0) {
                    return manager.create(isAbstract, isFinal, anyFieldAccessorExclusions, accessors = null, accessorNodes = null)
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

                return AccessNode(manager, interned = false, isAbstract, isFinal, anyFieldAccessorExclusions, accessors, accessNodes)
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
                deepAccessorExclusion = null,
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
                    deepAccessorExclusion = null,
                    accessors = intArrayOf(accessor),
                    accessorNodes = arrayOf(node)
                )

            @JvmStatic
            fun TreeApManager.create(
                isAbstract: Boolean,
                isFinal: Boolean,
                deepAccessorExclusion: DeepAccessorExclusion?,
                accessors: IntArray?,
                accessorNodes: Array<AccessNode>?
            ): AccessNode =
                if (isAbstract) {
                    if (isFinal) {
                        createElementAndField(abstractFinalNode, deepAccessorExclusion, accessors, accessorNodes)
                    } else {
                        createElementAndField(abstractNode, deepAccessorExclusion, accessors, accessorNodes)
                    }
                } else {
                    if (isFinal) {
                        createElementAndField(finalNode, deepAccessorExclusion = null, accessors, accessorNodes)
                    } else {
                        createElementAndField(emptyNode, deepAccessorExclusion = null, accessors, accessorNodes)
                    }
                }

            @JvmStatic
            private fun createElementAndField(
                base: AccessNode,
                deepAccessorExclusion: DeepAccessorExclusion?,
                accessors: IntArray?,
                accessorNodes: Array<AccessNode>?,
            ): AccessNode {
                val nonEmptyAccessors = accessors?.takeIf { it.isNotEmpty() }
                val nonEmptyAccessorNodes = accessorNodes?.takeIf { nonEmptyAccessors != null }
                return if (nonEmptyAccessors == null && deepAccessorExclusion == null) {
                    base
                } else {
                    AccessNode(
                        base.manager,
                        interned = false,
                        isAbstract = base.isAbstract,
                        isFinal = base.isFinal,
                        deepAccessorExclusion = deepAccessorExclusion,
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

            @JvmStatic
            fun TreeApManager.createAbstractNodeFromAccessors(accessors: IntList): AccessNode {
                var result = abstractNode
                accessors.reversedForEachInt { accessor ->
                    result = when (accessor) {
                        FINAL_ACCESSOR_IDX -> finalNode
                        else -> create(accessor, result)
                    }
                }

                return result
            }

            private fun <K, V: Any> Object2ObjectOpenHashMap<K, V>.getComputedResult(key: K): V =
                get(key) ?: error("Result for $key was not computed")

            private val NodeExpansionRequested = Any()
        }
    }
}
