package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.api.jvm.storage.ByteArrayKey
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.InstanceIdCollectionEntityIterable

private typealias ValueIndex = Persistent23TreeMap<ByteArrayKey, CompactPersistentLongSet>

internal class Properties(
    val props: PersistentLongMap<ByteArray> = PersistentLong23TreeMap(), // instanceId -> prop value
    private val valueIndex: ValueIndex? = null // prop value -> Set<instanceId>
) {

    internal fun deleteProperty(instanceId: Long): Properties? {
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
            return Properties(newProps, newValueIndex)
        }
        return Properties(newProps)
    }

    internal fun setProperty(instanceId: Long, value: ByteArray): Properties? {
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
            return Properties(newProps, newValueIndex)
        }
        return Properties(newProps)
    }

    internal fun getEntitiesWithValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<Properties?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { Properties(props, newIndex) }
        return newProperties to InstanceIdCollectionEntityIterable(
            txn, typeId, actualIndex[ByteArrayKey(value)] ?: return newProperties to EntityIterable.EMPTY
        )
    }

    internal fun getEntitiesLtValue(
        txn: RAMTransaction,
        typeId: Int,
        value: ByteArray
    ): Pair<Properties?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { Properties(props, newIndex) }
        val bound = ByteArrayKey(value)
        val result = mutableListOf<Long>()
        actualIndex.beginRead().iterator().forEach {
            if (it.key < bound) {
                result.addAll(it.value)
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
    ): Pair<Properties?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { Properties(props, newIndex) }
        val bound = ByteArrayKey(value)
        val result = mutableListOf<Long>()
        actualIndex.beginRead().iterator().forEach {
            if (it.key <= bound) {
                result.addAll(it.value)
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
    ): Pair<Properties?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { Properties(props, newIndex) }
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
    ): Pair<Properties?, EntityIterable> {
        val (actualIndex, newIndex) = ensureValueIndex()
        val newProperties = newIndex?.let { Properties(props, newIndex) }
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