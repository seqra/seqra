package org.opentaint.dataflow.jvm.ap.ifds

class AccessTree(val base: AccessPathBase, val access: AccessNode, val exclusions: ExclusionSet) {
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
        val elementAccess: AccessNode?,
        val fields: Array<FieldAccessor>?,
        val fieldNodes: Array<AccessNode>?
    ) {
        private val hash: Int
        val size: Int

        init {
            var hash = 0
            if (isAbstract) hash += 1
            if (isFinal) hash += 2
            elementAccess?.let { hash += it.hash shl 2 }
            if (fieldNodes != null) {
                val fieldHash = fieldNodes.sumOf { it.hash }
                hash += fieldHash shl 5
            }
            this.hash = hash
        }

        init {
            var size = 1
            elementAccess?.let { size += it.size }
            if (fieldNodes != null) {
                size += fieldNodes.sumOf { it.size }
            }
            this.size = size
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessNode) return false

            if (hash != other.hash) return false
            if (isAbstract != other.isAbstract || isFinal != other.isFinal) return false

            if (elementAccess != other.elementAccess) return false

            if (!fields.contentEquals(other.fields)) return false
            return fieldNodes.contentEquals(other.fieldNodes)
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

            elementAccess?.print(builder, prefix + ElementAccessor.toSuffix())

            forEachField { field, child ->
                child.print(builder, prefix + field.toSuffix())
            }
        }

        inline fun forEachField(body: (FieldAccessor, AccessNode) -> Unit) {
            if (fields != null) {
                for (i in fields.indices) {
                    body(fields[i], fieldNodes!![i])
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
            !isAbstract && !isFinal && elementAccess == null && fields == null

        private fun fieldIndex(field: FieldAccessor): Int {
            if (fields == null) return -1
            return fields.binarySearch(field)
        }

        private fun getFieldNode(field: FieldAccessor): AccessNode? =
            fieldNodes?.getOrNull(fieldIndex(field))

        fun contains(accessor: Accessor): Boolean = when (accessor) {
            FinalAccessor -> isFinal
            ElementAccessor -> elementAccess != null
            is FieldAccessor -> fieldIndex(accessor) >= 0
        }

        fun getChild(accessor: Accessor): AccessNode? = when (accessor) {
            FinalAccessor -> error("Can't get final child")
            ElementAccessor -> elementAccess
            is FieldAccessor -> getFieldNode(accessor)
        }

        fun addParent(accessor: Accessor): AccessNode = when (accessor) {
            FinalAccessor -> error("Final parent")
            ElementAccessor -> create(elementAccess = limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT))
            is FieldAccessor -> addParentFieldAccess(accessor)
        }

        fun removeAbstraction(): AccessNode =
            create(isAbstract = false, isFinal, elementAccess, fields, fieldNodes)

        private fun limitElementAccess(limit: Int): AccessNode {
            if (elementAccess == null) return this

            if (limit > 0) {
                val limitedChild = elementAccess.limitElementAccess(limit - 1)
                if (limitedChild === elementAccess) return this
                return create(isAbstract, isFinal, limitedChild, fields, fieldNodes)
            }

            return collapseElementAccess().also {
                check(it.elementAccess == null) { "Array element limit invariant failure" }
            }
        }

        private fun collapseElementAccess(): AccessNode {
            if (elementAccess == null) return this

            val collapsedElementAccess = elementAccess.collapseElementAccess()
            val result = create(isAbstract, isFinal, elementAccess = null, fields, fieldNodes)
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

            return resultNode.bulkMergeAddFields(filteredNodes)
                .also { check(!it.isEmpty) { "Empty node after field normalization" } }
        }

        private fun limitFieldAccess(
            newRootFieldClassName: String,
            filteredNodes: MutableList<Pair<FieldAccessor, AccessNode>>
        ): AccessNode? {
            val elementAccess = elementAccess?.limitFieldAccess(newRootFieldClassName, filteredNodes)

            val transformedFields = transformFields { field, node ->
                if (field.className == newRootFieldClassName) {
                    filteredNodes += field to node
                    null
                } else {
                    node.limitFieldAccess(newRootFieldClassName, filteredNodes)
                }
            }

            if (elementAccess === this.elementAccess && transformedFields == null) {
                return this
            }

            val fields = transformedFields?.first ?: fields
            val fieldNodes = transformedFields?.second ?: fieldNodes

            val limitedNode = create(isAbstract, isFinal, elementAccess, fields, fieldNodes)
            return limitedNode.takeIf { !it.isEmpty }
        }

        fun clearChild(accessor: Accessor): AccessNode = when (accessor) {
            FinalAccessor -> create(isAbstract, isFinal = false, elementAccess, fields, fieldNodes)
            ElementAccessor -> create(isAbstract, isFinal, elementAccess = null, fields, fieldNodes)
            is FieldAccessor -> removeSingleField(accessor)
        }

        fun filterElementAccess(filter: (AccessNode) -> AccessNode?): AccessNode {
            if (elementAccess == null) return this

            val filtered = filter(elementAccess)

            if (filtered === elementAccess) return this
            return create(isAbstract, isFinal, elementAccess = filtered, fields, fieldNodes)
        }

        fun filterFields(filter: (FieldAccessor, AccessNode) -> AccessNode?): AccessNode {
            val transformedFields = transformFields { field, node -> filter(field, node) } ?: return this
            return create(isAbstract, isFinal, elementAccess, transformedFields.first, transformedFields.second)
        }

        fun filter(exclusion: ExclusionSet.Concrete): AccessNode {
            val isFinal = this.isFinal && FinalAccessor !in exclusion
            val elementAccess = elementAccess?.takeIf { ElementAccessor !in exclusion }

            val transformedFields = transformFields { field, node ->
                node.takeIf { field !in exclusion }
            }

            if (isFinal == this.isFinal && elementAccess === this.elementAccess && transformedFields == null) {
                return this
            }

            val fields = transformedFields?.first ?: fields
            val fieldNodes = transformedFields?.second ?: fieldNodes

            return create(isAbstract, isFinal, elementAccess, fields, fieldNodes)
        }

        private fun bulkMergeAddFields(fields: List<Pair<FieldAccessor, AccessNode>>): AccessNode {
            if (fields.isEmpty()) return this

            val uniqueFields = mutableListOf<Pair<FieldAccessor, AccessNode>>()
            val groupedUniqueFields = fields.groupByTo(hashMapOf(), { it.first }, { it.second })

            for ((field, nodes) in groupedUniqueFields) {
                val mergedNodes = nodes.reduce { acc, node -> acc.mergeAdd(node) }
                uniqueFields.add(field to mergedNodes)
            }

            uniqueFields.sortBy { it.first }
            val addedFieldAccessors = Array(uniqueFields.size) { uniqueFields[it].first }
            val addedFieldNodes = Array(uniqueFields.size) { uniqueFields[it].second }

            val mergedFields = mergeFields(
                addedFieldAccessors, addedFieldNodes, onOtherNode = { _, _ -> }
            ) { _, thisNode, otherNode ->
                thisNode.mergeAdd(otherNode)
            }

            if (mergedFields == null) return this

            return create(isAbstract, isFinal, elementAccess, mergedFields.first, mergedFields.second)
        }

        fun mergeAdd(other: AccessNode): AccessNode {
            if (this === other) return this

            val isAbstract = this.isAbstract || other.isAbstract

            val isFinal = this.isFinal || other.isFinal

            val elementAccess = when {
                this.elementAccess == null -> other.elementAccess
                other.elementAccess == null -> this.elementAccess
                else -> this.elementAccess.mergeAdd(other.elementAccess)
            }

            val mergedFields = mergeFields(
                other.fields, other.fieldNodes, onOtherNode = { _, _ -> }
            ) { _, thisNode, otherNode ->
                thisNode.mergeAdd(otherNode)
            }

            if (
                isAbstract == this.isAbstract
                && isFinal == this.isFinal
                && elementAccess === this.elementAccess
                && mergedFields == null
            ) {
                return this
            }

            val fields = mergedFields?.first ?: fields
            val fieldNodes = mergedFields?.second ?: fieldNodes
            return create(isAbstract, isFinal, elementAccess, fields, fieldNodes)
        }

        fun mergeAddDelta(other: AccessNode): Pair<AccessNode, AccessNode?> {
            if (this === other) return this to null

            val isFinal = this.isFinal || other.isFinal
            val isFinalDelta = !this.isFinal && other.isFinal

            val isAbstract = this.isAbstract || other.isAbstract
            val isAbstractDelta = !this.isAbstract && other.isAbstract

            val (elementAccess, elementAccessDelta) = when {
                this.elementAccess == null -> other.elementAccess to other.elementAccess
                other.elementAccess == null -> this.elementAccess to null
                else -> this.elementAccess.mergeAddDelta(other.elementAccess)
            }

            val deltaFields = arrayListOf<FieldAccessor>()
            val deltaFieldNodes = arrayListOf<AccessNode>()

            val mergedFields = mergeFields(
                other.fields, other.fieldNodes,
                onOtherNode = { field, node ->
                    deltaFields.add(field)
                    deltaFieldNodes.add(node)
                }
            ) { field, thisNode, otherNode ->
                val (addedNode, addedNodeDelta) = thisNode.mergeAddDelta(otherNode)

                if (addedNodeDelta != null) {
                    deltaFields.add(field)
                    deltaFieldNodes.add(addedNodeDelta)
                }

                addedNode
            }

            if (
                isAbstract == this.isAbstract
                && isFinal == this.isFinal
                && elementAccess === this.elementAccess
                && mergedFields == null
            ) {
                return this to null
            }

            val delta = create(
                isAbstractDelta, isFinalDelta, elementAccessDelta,
                deltaFields.toTypedArray(), deltaFieldNodes.toTypedArray()
            ).takeIf { !it.isEmpty }

            val fields = mergedFields?.first ?: fields
            val fieldNodes = mergedFields?.second ?: fieldNodes
            return create(isAbstract, isFinal, elementAccess, fields, fieldNodes) to delta
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
                typeChecker.filterByAccessPathType(path, other)
                    ?.limitElementAccess(limit = subsequentArrayElementLimit)
            } else null

            val elementAccess = this.elementAccess?.let {
                path.add(ElementAccessor)
                it.concatToLeafAbstractNodes(
                    typeChecker, other, path, subsequentArrayElementLimit - 1
                ).also {
                    path.removeLast()
                }
            }

            val nestedFields = mutableListOf<Pair<FieldAccessor, AccessNode>>()

            val currentThisFields = mutableListOf<Pair<FieldAccessor, AccessNode>>()
            forEachField { field, node -> currentThisFields.add(field to node) }
            for ((fieldClassName, entries) in currentThisFields.groupBy { it.first.className }) {
                // todo: check that filtered branches are applicable to any leaf
                val filteredOther = other?.limitFieldAccess(fieldClassName, nestedFields)
                for ((field, node) in entries) {
                    path.add(field)
                    val concatenatedNode = node.concatToLeafAbstractNodes(
                        typeChecker, filteredOther, path, SUBSEQUENT_ARRAY_ELEMENTS_LIMIT
                    )
                    path.removeLast()

                    if (concatenatedNode != null) {
                        nestedFields.add(field to concatenatedNode)
                    }
                }
            }

            val resultNode = create(isAbstract = false, isFinal, elementAccess, fields = null, fieldNodes = null)
                .bulkMergeAddFields(nestedFields)

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
                ElementAccessor -> elementAccess?.filterStartsWith(accessors)?.let { create(elementAccess = it) }
                is FieldAccessor -> getFieldNode(accessor)?.filterStartsWith(accessors)?.let { create(accessor, it) }
            }
        }

        private inline fun mergeFields(
            otherFields: Array<FieldAccessor>?,
            otherNodesE: Array<AccessNode>?,
            onOtherNode: (FieldAccessor, AccessNode) -> Unit,
            merge: (FieldAccessor, AccessNode, AccessNode) -> AccessNode
        ): Pair<Array<FieldAccessor>, Array<AccessNode>>? {
            if (otherFields == null) return null
            val otherNodes = otherNodesE!!

            if (fields == null) {
                for (i in otherFields.indices) {
                    onOtherNode(otherFields[i], otherNodes[i])
                }

                return otherFields to otherNodes
            }

            val thisFields = fields
            val thisNodes = fieldNodes!!

            var modified = false
            var fieldsModified = false

            var writeIdx = 0
            var thisIdx = 0
            var otherIdx = 0

            val mergedFields = arrayOfNulls<FieldAccessor>(thisFields.size + otherFields.size)
            val mergedNodes = arrayOfNulls<AccessNode>(thisFields.size + otherFields.size)

            while (true) {
                val thisField = thisFields.getOrNull(thisIdx)
                val otherField = otherFields.getOrNull(otherIdx)

                if (thisField == null && otherField == null) break

                val fieldCmp = when {
                    otherField == null -> -1 // thisField != null
                    thisField == null -> 1 // otherField != null
                    else -> thisField.compareTo(otherField)
                }

                if (fieldCmp < 0) {
                    mergedFields[writeIdx] = thisField
                    mergedNodes[writeIdx] = thisNodes[thisIdx]
                    thisIdx++
                    writeIdx++
                } else if (fieldCmp > 0) {
                    val otherNode = otherNodes[otherIdx]
                    onOtherNode(otherField!!, otherNode)

                    modified = true
                    fieldsModified = true

                    mergedFields[writeIdx] = otherField
                    mergedNodes[writeIdx] = otherNode
                    otherIdx++
                    writeIdx++
                } else {
                    val thisNode = thisNodes[thisIdx]
                    val otherNode = otherNodes[otherIdx]

                    val mergedNode = merge(thisField!!, thisNode, otherNode)
                    if (mergedNode === thisNode) {
                        mergedFields[writeIdx] = thisField
                        mergedNodes[writeIdx] = thisNode
                    } else {
                        modified = true
                        mergedFields[writeIdx] = thisField
                        mergedNodes[writeIdx] = mergedNode
                    }

                    thisIdx++
                    otherIdx++
                    writeIdx++
                }
            }

            return trimModifiedFields(modified, fieldsModified, writeIdx, fields, mergedFields, mergedNodes)
        }

        private inline fun transformFields(
            transformer: (FieldAccessor, AccessNode) -> AccessNode?
        ): Pair<Array<FieldAccessor>, Array<AccessNode>>? {
            if (fields == null) return null
            val nodes = fieldNodes!!

            var modified = false
            var fieldsModified = false

            var writeIdx = 0
            val transformedFields = arrayOfNulls<FieldAccessor>(fields.size)
            val transformedNodes = arrayOfNulls<AccessNode>(fields.size)

            for (i in fields.indices) {
                val field = fields[i]
                val node = nodes[i]

                val transformedNode = transformer(field, node)
                if (transformedNode === node) {
                    transformedFields[writeIdx] = field
                    transformedNodes[writeIdx] = node
                    writeIdx++
                } else {
                    modified = true

                    if (transformedNode == null) {
                        fieldsModified = true
                        continue
                    }

                    transformedFields[writeIdx] = field
                    transformedNodes[writeIdx] = transformedNode
                    writeIdx++
                }
            }

            return trimModifiedFields(modified, fieldsModified, writeIdx, fields, transformedFields, transformedNodes)
        }

        private fun trimModifiedFields(
            modified: Boolean,
            fieldsModified: Boolean,
            writeIdx: Int,
            originalFields: Array<FieldAccessor>,
            fields: Array<FieldAccessor?>,
            nodes: Array<AccessNode?>
        ): Pair<Array<FieldAccessor>, Array<AccessNode>>? {
            if (!modified) return null

            if (!fieldsModified) {
                check(writeIdx == originalFields.size) { "Incorrect size" }

                val trimmedNodes = if (writeIdx == nodes.size) {
                    nodes
                } else {
                    nodes.copyOf(writeIdx)
                }

                @Suppress("UNCHECKED_CAST")
                return originalFields to trimmedNodes as Array<AccessNode>
            }

            if (writeIdx != fields.size) {
                val trimmedFields = fields.copyOf(writeIdx)
                val trimmedNodes = nodes.copyOf(writeIdx)
                @Suppress("UNCHECKED_CAST")
                return trimmedFields as Array<FieldAccessor> to trimmedNodes as Array<AccessNode>
            } else {
                @Suppress("UNCHECKED_CAST")
                return fields as Array<FieldAccessor> to nodes as Array<AccessNode>
            }
        }

        private fun removeSingleField(field: FieldAccessor): AccessNode {
            val fields = fields ?: return this
            val nodes = fieldNodes!!

            val fieldIdx = fields.binarySearch(field)
            if (fieldIdx < 0) return this

            val newFieldSize = fields.size - 1
            if (newFieldSize == 0) {
                return create(isAbstract, isFinal, elementAccess, fields = null, fieldNodes = null)
            }

            val newFields = arrayOfNulls<FieldAccessor>(newFieldSize)
            val newNodes = arrayOfNulls<AccessNode>(newFieldSize)

            fields.copyInto(newFields, endIndex = fieldIdx)
            fields.copyInto(newFields, destinationOffset = fieldIdx, startIndex = fieldIdx + 1)

            nodes.copyInto(newNodes, endIndex = fieldIdx)
            nodes.copyInto(newNodes, destinationOffset = fieldIdx, startIndex = fieldIdx + 1)

            @Suppress("UNCHECKED_CAST")
            return create(
                isAbstract, isFinal, elementAccess,
                newFields as Array<FieldAccessor>, newNodes as Array<AccessNode>
            )
        }

        companion object {
            const val SUBSEQUENT_ARRAY_ELEMENTS_LIMIT = 2

            private val emptyNode = AccessNode(
                isAbstract = false, isFinal = false,
                elementAccess = null, fields = null, fieldNodes = null
            )

            private val abstractNode = AccessNode(
                isAbstract = true, isFinal = false,
                elementAccess = null, fields = null, fieldNodes = null
            )

            private val finalNode = AccessNode(
                isAbstract = false, isFinal = true,
                elementAccess = null, fields = null, fieldNodes = null
            )

            private val abstractFinalNode = AccessNode(
                isAbstract = true, isFinal = true,
                elementAccess = null, fields = null, fieldNodes = null
            )

            fun abstractNode(): AccessNode = abstractNode

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
                    AccessNode(
                        isAbstract = false, isFinal = false,
                        elementAccess = access,
                        fields = null, fieldNodes = null
                    )
                } ?: emptyNode

            @JvmStatic
            private fun create(field: FieldAccessor, node: AccessNode): AccessNode =
                AccessNode(
                    isAbstract = false, isFinal = false,
                    elementAccess = null,
                    fields = arrayOf(field), fieldNodes = arrayOf(node)
                )

            @JvmStatic
            private fun create(
                isAbstract: Boolean,
                isFinal: Boolean,
                elementAccess: AccessNode?,
                fields: Array<FieldAccessor>?,
                fieldNodes: Array<AccessNode>?
            ): AccessNode =
                if (isAbstract) {
                    if (isFinal) {
                        createElementAndField(abstractFinalNode, elementAccess, fields, fieldNodes)
                    } else {
                        createElementAndField(abstractNode, elementAccess, fields, fieldNodes)
                    }
                } else {
                    if (isFinal) {
                        createElementAndField(finalNode, elementAccess, fields, fieldNodes)
                    } else {
                        createElementAndField(emptyNode, elementAccess, fields, fieldNodes)
                    }
                }

            @JvmStatic
            private fun createElementAndField(
                base: AccessNode,
                elementAccess: AccessNode?,
                fields: Array<FieldAccessor>?,
                fieldNodes: Array<AccessNode>?
            ): AccessNode {
                val nonEmptyFields = fields?.takeIf { it.isNotEmpty() }
                val nonEmptyFieldNodes = fieldNodes?.takeIf { nonEmptyFields != null }
                return if (elementAccess == null && nonEmptyFields == null) {
                    base
                } else {
                    AccessNode(
                        isAbstract = base.isAbstract,
                        isFinal = base.isFinal,
                        elementAccess = elementAccess,
                        fields = nonEmptyFields,
                        fieldNodes = nonEmptyFieldNodes
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
                    ElementAccessor -> create(elementAccess = createAbstractNodeFromAp(accessors))
                    is FieldAccessor -> create(field = accessor, node = createAbstractNodeFromAp(accessors))
                }
            }
        }
    }
}
