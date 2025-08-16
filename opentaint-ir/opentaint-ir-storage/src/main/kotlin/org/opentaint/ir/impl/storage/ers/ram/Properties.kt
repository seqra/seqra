package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.api.storage.ByteArrayKey
import org.opentaint.ir.api.storage.asComparable
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.InstanceIdCollectionEntityIterable

private typealias ValueIndex = Persistent23TreeMap<ByteArrayKey, CompactPersistentLongSet>

internal class PropertiesMutable(
    internal val props: PersistentLongMap<ByteArray> = PersistentLong23TreeMap(), // instanceId -> prop value
    private val valueIndex: ValueIndex? = null // prop value -> Set<instanceId>
) {

    internal fun deleteProperty(instanceId: Long): PropertiesMutable? {
        val (value, newProps) = props.write {
            remove(instanceId)
        }
        if (value == null) {
            return null
        }
        valueIndex?.let { valueIndex ->
            val byteArrayKey = ByteArrayKey(value)
            val instanceIdSet = valueIndex[byteArrayKey] ?: throw NullPointerException("Value index is inconsistent")
            val newInstanceIdSet = instanceIdSet.remove(instanceId)
            val newValueIndex = valueIndex.write {
                if (newInstanceIdSet.isEmpty()) {
                    remove(byteArrayKey)
                } else {
                    put(byteArrayKey, newInstanceIdSet)
                }
            }.second
            return PropertiesMutable(newProps, newValueIndex)
        }
        return PropertiesMutable(newProps)
    }

    internal fun setProperty(instanceId: Long, value: ByteArray): PropertiesMutable? {
        var unchanged = false
        val (oldValue, newProps) = props.write {
            if (valueIndex == null) {
                put(instanceId, value)
                null
            } else {
                val oldValue = get(instanceId)
                unchanged = oldValue != null && value contentEquals oldValue
                if (!unchanged) {
                    put(instanceId, value)
                }
                oldValue
            }
        }
        if (unchanged) {
            return null
        }
        valueIndex?.let { valueIndex ->
            val newValueIndex = valueIndex.write {
                val byteArrayKey = ByteArrayKey(value)
                val instanceIdSet = get(byteArrayKey) ?: CompactPersistentLongSet()
                var newInstanceIdSet = instanceIdSet.add(instanceId)
                put(byteArrayKey, newInstanceIdSet)
                oldValue?.let {
                    val byteArrayKeyOld = ByteArrayKey(it)
                    newInstanceIdSet = get(byteArrayKeyOld) ?: return@let
                    newInstanceIdSet = newInstanceIdSet.remove(instanceId)
                    if (newInstanceIdSet.isEmpty()) {
                        remove(byteArrayKeyOld)
                    } else {
                        put(byteArrayKeyOld, newInstanceIdSet)
                    }
                }
            }.second
            return PropertiesMutable(newProps, newValueIndex)
        }
        return PropertiesMutable(newProps)
    }

    internal fun getEntitiesWithValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<PropertiesMutable?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { PropertiesMutable(props, newIndex) }
        return newProperties to InstanceIdCollectionEntityIterable(
            txn, typeId, actualIndex[ByteArrayKey(value)] ?: return newProperties to EntityIterable.EMPTY
        )
    }

    internal fun getEntitiesLtValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<PropertiesMutable?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { PropertiesMutable(props, newIndex) }
        val bound = ByteArrayKey(value)
        val result = mutableListOf<Long>()
        actualIndex.beginRead().iterator().forEach {
            if (it.key < bound) {
                result.addAll(it.value)
            } else {
                return@forEach
            }
        }
        return newProperties to InstanceIdCollectionEntityIterable(
            txn, typeId,
            result.ifEmpty {
                return newProperties to EntityIterable.EMPTY
            }.asReversed(),
        )
    }

    internal fun getEntitiesEqOrLtValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<PropertiesMutable?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { PropertiesMutable(props, newIndex) }
        val bound = ByteArrayKey(value)
        val result = mutableListOf<Long>()
        actualIndex.beginRead().iterator().forEach {
            if (it.key <= bound) {
                result.addAll(it.value)
            } else {
                return@forEach
            }
        }
        return newProperties to InstanceIdCollectionEntityIterable(
            txn, typeId,
            result.ifEmpty {
                return newProperties to EntityIterable.EMPTY
            }.asReversed(),
        )
    }

    internal fun getEntitiesGtValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<PropertiesMutable?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { PropertiesMutable(props, newIndex) }
        val bound = ByteArrayKey(value)
        val result = mutableListOf<Long>()
        actualIndex.beginRead().tailIterator(actualIndex.createEntry(bound)).forEach {
            if (it.key > bound) {
                result.addAll(it.value)
            }
        }
        return newProperties to InstanceIdCollectionEntityIterable(
            txn, typeId,
            result.ifEmpty {
                return newProperties to EntityIterable.EMPTY
            },
        )
    }

    internal fun getEntitiesEqOrGtValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<PropertiesMutable?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { PropertiesMutable(props, newIndex) }
        val bound = ByteArrayKey(value)
        val result = mutableListOf<Long>()
        actualIndex.beginRead().tailIterator(actualIndex.createEntry(bound)).forEach {
            result.addAll(it.value)
        }
        return newProperties to InstanceIdCollectionEntityIterable(
            txn, typeId,
            result.ifEmpty {
                return newProperties to EntityIterable.EMPTY
            },
        )
    }

    /**
     * Creates value index if it wasn't created and return it, otherwise return null
     */
    private fun ensureValueIndex(): Pair<ValueIndex, ValueIndex?> {
        val valueIndex = this.valueIndex
        if (valueIndex != null) {
            return valueIndex to null
        }
        val newIndex = Persistent23TreeMap<ByteArrayKey, CompactPersistentLongSet>().write {
            props.beginRead().forEach { entry ->
                val byteArrayKey = ByteArrayKey(entry.value)
                val instanceIdSet = get(byteArrayKey) ?: CompactPersistentLongSet()
                put(byteArrayKey, instanceIdSet.add(entry.key))
            }
        }.second
        return newIndex to newIndex
    }
}

internal class PropertiesImmutable(private val attributes: AttributesImmutable) {

    operator fun get(instanceId: Long): ByteArray? {
        return attributes[instanceId]
    }

    internal fun getEntitiesWithValue(txn: RAMTransaction, typeId: Int, value: ByteArray): EntityIterable {
        return AttributesCursorEntityIterable(
            txn = txn,
            typeId = typeId,
            forwardDirection = true,
            cursor = attributes.navigate(value, leftBound = true).apply { movePrev() }
        ) { _, v ->
            v contentEquals value
        }
    }

    fun getEntitiesLtValue(txn: RAMTransaction, typeId: Int, value: ByteArray): EntityIterable {
        val valueComparable = value.asComparable()
        return AttributesCursorEntityIterable(
            txn = txn,
            typeId = typeId,
            forwardDirection = false,
            cursor = attributes.navigate(value, leftBound = true)
        ) { _, v ->
            v.asComparable() < valueComparable
        }
    }

    fun getEntitiesEqOrLtValue(txn: RAMTransaction, typeId: Int, value: ByteArray): EntityIterable {
        val valueComparable = value.asComparable()
        return AttributesCursorEntityIterable(
            txn = txn,
            typeId = typeId,
            forwardDirection = false,
            cursor = attributes.navigate(value, leftBound = false).apply { if (hasMatch) moveNext() }
        ) { _, v ->
            v.asComparable() <= valueComparable
        }
    }

    fun getEntitiesGtValue(txn: RAMTransaction, typeId: Int, value: ByteArray): EntityIterable {
        val valueComparable = value.asComparable()
        return AttributesCursorEntityIterable(
            txn = txn,
            typeId = typeId,
            forwardDirection = true,
            cursor = attributes.navigate(value, leftBound = false).apply { if (!hasMatch) movePrev() }
        ) { _, v ->
            v.asComparable() > valueComparable
        }
    }

    fun getEntitiesEqOrGtValue(txn: RAMTransaction, typeId: Int, value: ByteArray): EntityIterable {
        val valueComparable = value.asComparable()
        return AttributesCursorEntityIterable(
            txn = txn,
            typeId = typeId,
            forwardDirection = true,
            cursor = attributes.navigate(value, leftBound = true).apply { movePrev() }
        ) { _, v ->
            v.asComparable() >= valueComparable
        }
    }
}

internal fun PropertiesMutable.toImmutable(): PropertiesImmutable {
    return PropertiesImmutable(props.beginRead().map { it.key to it.value }.toAttributesImmutable())
}
