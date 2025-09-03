package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.FactApDelta
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

class AccessTree(
    override val base: AccessPathBase,
    val access: AccessNode,
    override val exclusions: ExclusionSet
) : FinalFactAp {
    override fun rebase(newBase: AccessPathBase): FinalFactAp =
        AccessTree(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): FinalFactAp =
        AccessTree(base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp =
        AccessTree(base, access, exclusions)

    override fun startsWithAccessor(accessor: Accessor): Boolean = access.contains(accessor)

    override fun isAbstract(): Boolean = access.isAbstract

    override fun readAccessor(accessor: Accessor): FinalFactAp? =
        access.getChild(accessor)?.let { AccessTree(base, it, exclusions) }

    override fun prependAccessor(accessor: Accessor): FinalFactAp =
        AccessTree(base, access.addParent(accessor), exclusions)

    override fun clearAccessor(accessor: Accessor): FinalFactAp? {
        val newAccess = access.clearChild(accessor).takeIf { !it.isEmpty } ?: return null
        return AccessTree(base, newAccess, exclusions)
    }

    override fun removeAbstraction(): FinalFactAp? =
        access.removeAbstraction().takeIf { !it.isEmpty }?.let { AccessTree(base, it, exclusions) }

    override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? {
        val filteredAccess = access.filterAccessNode(filter) ?: return null
        return AccessTree(base, filteredAccess, exclusions)
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
            node = node.getChild(accessor) ?: return false
        }

        return node.isAbstract
    }

    private sealed interface Delta : FactApDelta

    data object EmptyDelta : Delta {
        override val isEmpty: Boolean get() = true
    }

    data class NodeDelta(val node: AccessNode) : Delta {
        override val isEmpty: Boolean get() = false
    }

    override fun delta(other: InitialFactAp): List<FactApDelta> {
        other as AccessPath

        if (base != other.base) return emptyList()

        var node = access
        val access = other.access
        if (access != null) {
            for (accessor in access) {
                if (accessor is FinalAccessor) {
                    if (!node.isFinal) return emptyList()
                    return listOf(EmptyDelta)
                }

                node = node.getChild(accessor) ?: return emptyList()
            }
        }

        val filteredNode = when (val exclusion = other.exclusions) {
            ExclusionSet.Empty -> node
            is ExclusionSet.Concrete -> node.filter(exclusion)
            ExclusionSet.Universe -> error("Unexpected universe exclusion in initial fact")
        }

        if (filteredNode.isEmpty) return emptyList()

        if (!filteredNode.isAbstract) return listOf(NodeDelta(filteredNode))

        val nonAbstractDelta = filteredNode
            .removeAbstraction()
            .takeIf { !it.isEmpty }
            ?.let { NodeDelta(it) }

        return listOfNotNull(nonAbstractDelta, EmptyDelta)
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FactApDelta): FinalFactAp? {
        when (val d = delta as Delta) {
            EmptyDelta -> return this
            is NodeDelta -> {
                val concatenatedAccess = access.concatToLeafAbstractNodes(typeChecker, d.node) ?: return null
                return AccessTree(base, concatenatedAccess, exclusions)
            }
        }
    }

    override val size: Int
        get() = access.size

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
        val isAbstract: Boolean,
        val isFinal: Boolean,
        val accessors: Array<Accessor>?,
        val accessorNodes: Array<AccessNode>?,
    ) {
        private val hash: Int
        val size: Int

        init {
            var hash = 0
            if (isAbstract) hash += 1
            if (isFinal) hash += 2
            if (accessorNodes != null) {
                val accessorsHash = accessorNodes.sumOf { it.hash }
                hash += accessorsHash shl 5
            }
            this.hash = hash
        }

        init {
            var size = 1
            if (accessorNodes != null) {
                size += accessorNodes.sumOf { it.size }
            }
            this.size = size
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessNode) return false

            if (hash != other.hash) return false
            if (isAbstract != other.isAbstract || isFinal != other.isFinal) return false

            if (!accessors.contentEquals(other.accessors)) return false
            return accessorNodes.contentEquals(other.accessorNodes)
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

            forEachAccessor { field, node ->
                currentPath.add(field)
                node.forEachPath(currentPath, body)
                currentPath.removeLast()
            }
        }

        val isEmpty: Boolean
            get() =
                !isAbstract && !isFinal && accessors == null

        private fun accessorIndex(accessor: Accessor): Int {
            if (accessors == null) return -1
            return accessors.binarySearch(accessor)
        }

        private fun getNodeByAccessor(accessor: Accessor): AccessNode? =
            accessorNodes?.getOrNull(accessorIndex(accessor))

        fun contains(accessor: Accessor): Boolean = when (accessor) {
            FinalAccessor -> isFinal
            else -> accessorIndex(accessor) >= 0
        }

        fun getChild(accessor: Accessor): AccessNode? = when (accessor) {
            FinalAccessor -> finalNode.takeIf { this.isFinal }
            else -> getNodeByAccessor(accessor)
        }

        fun addParent(accessor: Accessor): AccessNode = when (accessor) {
            FinalAccessor -> error("Final parent")
            ElementAccessor -> create(elementAccess = limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT))
            is FieldAccessor -> addParentFieldAccess(accessor)
            is TaintMarkAccessor -> {
                check(this == finalNode || this == abstractNode || this == abstractFinalNode)
                create(accessor, this)
            }
        }

        fun removeAbstraction(): AccessNode =
            create(isAbstract = false, isFinal, accessors, accessorNodes)

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

        private fun addParentFieldAccess(newRootField: FieldAccessor): AccessNode {
            val filteredNodes = mutableListOf<Pair<FieldAccessor, AccessNode>>()
            val limitedThis = limitFieldAccess(newRootField.className, filteredNodes)

            val resultNode = if (limitedThis != null) {
                create(newRootField, limitedThis)
            } else {
                emptyNode
            }

            return resultNode.bulkMergeAddAccessors(filteredNodes)
                .also { check(!it.isEmpty) { "Empty node after field normalization" } }
        }

        private fun limitFieldAccess(
            newRootFieldClassName: String,
            filteredNodes: MutableList<in Pair<FieldAccessor, AccessNode>>
        ): AccessNode? {
            val limitedNode = transformAccessors { accessor, node ->
                if (accessor is FieldAccessor && accessor.className == newRootFieldClassName) {
                    filteredNodes += accessor to node
                    null
                } else {
                    node.limitFieldAccess(newRootFieldClassName, filteredNodes)
                }
            }

            return limitedNode.takeIf { !it.isEmpty }
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
            val result = transformAccessors { accessor, accessNode ->
                when (val status = filter.check(accessor)) {
                    FactTypeChecker.FilterResult.Accept -> accessNode
                    FactTypeChecker.FilterResult.Reject -> null
                    is FactTypeChecker.FilterResult.FilterNext -> accessNode.filterAccessNode(status.filter)
                }
            }
            return result.takeIf { !it.isEmpty }
        }

        fun concatToLeafAbstractNodes(typeChecker: FactTypeChecker, other: AccessNode): AccessNode? =
            concatToLeafAbstractNodes(
                typeChecker, other, mutableListOf(), SUBSEQUENT_ARRAY_ELEMENTS_LIMIT
            )

        private fun concatToLeafAbstractNodes(
            typeChecker: FactTypeChecker,
            other: AccessNode?,
            path: MutableList<Accessor>,
            subsequentArrayElementLimit: Int
        ): AccessNode? {
            val concatNode = if (isAbstract && other != null) {
                val filter = typeChecker.accessPathFilter(path)
                other.filterAccessNode(filter)
                    ?.limitElementAccess(limit = subsequentArrayElementLimit)
            } else null

            val nestedAccessors = mutableListOf<Pair<Accessor, AccessNode>>()

            forEachAccessor { accessor, node ->
                val filteredOther = if (accessor is FieldAccessor) {
                    other?.limitFieldAccess(accessor.className, nestedAccessors)
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
                    typeChecker, filteredOther, path, newSubsequentArrayLimit
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

        fun filterStartsWith(accessPath: AccessPath.AccessNode?): AccessNode? {
            if (accessPath == null) return this
            return filterStartsWith(accessPath.iterator())
        }

        private fun filterStartsWith(accessors: Iterator<Accessor>): AccessNode? {
            if (!accessors.hasNext()) return this

            return when (val accessor = accessors.next()) {
                FinalAccessor -> if (isFinal) finalNode else null
                else -> getNodeByAccessor(accessor)?.filterStartsWith(accessors)?.let { create(accessor, it) }
            }
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

        private fun transformAccessors(
            transformer: (Accessor, AccessNode) -> AccessNode?
        ): AccessNode {
            val newAccessors = transformAccessors(accessors, accessorNodes, transformer) ?: return this
            return create(isAbstract, isFinal, newAccessors.first, newAccessors.second)
        }

        private fun removeSingleAccessor(accessor: Accessor): AccessNode {
            val newAccessors = removeSingleAccessor(accessor, accessors, accessorNodes) ?: return this
            return create(isAbstract, isFinal, newAccessors.first, newAccessors.second)
        }

        companion object {
            const val SUBSEQUENT_ARRAY_ELEMENTS_LIMIT = 2

            private val emptyNode = AccessNode(
                isAbstract = false, isFinal = false,
                accessors = null, accessorNodes = null
            )

            private val abstractNode = AccessNode(
                isAbstract = true, isFinal = false,
                accessors = null, accessorNodes = null
            )

            private val finalNode = AccessNode(
                isAbstract = false, isFinal = true,
                accessors = null, accessorNodes = null
            )

            private val abstractFinalNode = AccessNode(
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
                        isAbstract = base.isAbstract,
                        isFinal = base.isFinal,
                        accessors = nonEmptyAccessors,
                        accessorNodes = nonEmptyAccessorNodes
                    )
                }
            }

            @JvmStatic
            fun createAbstractNodeFromAp(accessors: Iterator<Accessor>): AccessNode {
                if (!accessors.hasNext()) {
                    return abstractNode
                }

                return when (val accessor = accessors.next()) {
                    FinalAccessor -> finalNode
                    is TaintMarkAccessor -> create(accessor, createAbstractNodeFromAp(accessors))
                    else -> create(accessor, createAbstractNodeFromAp(accessors))
                }
            }
        }
    }
}
