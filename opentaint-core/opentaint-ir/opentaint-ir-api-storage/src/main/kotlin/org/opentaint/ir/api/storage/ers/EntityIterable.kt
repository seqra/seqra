package org.opentaint.ir.api.storage.ers

interface EntityIterable : Sequence<Entity> {

    val size: Long get() = toEntityIdSet().size.toLong()

    val isEmpty: Boolean get() = size == 0L

    val isNotEmpty: Boolean get() = !isEmpty

    operator fun contains(e: Entity): Boolean = toEntityIdSet().contains(e.id)

    operator fun plus(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> this
        else -> this.union(other)
    }

    operator fun times(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> EMPTY
        else -> this.intersect(other)
    }

    operator fun minus(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> this
        else -> this.subtract(other)
    }

    fun deleteAll() = forEach { entity -> entity.delete() }

    companion object {

        val EMPTY: EntityIterable = object : EntityIterable {

            override val size = 0L

            override fun contains(e: Entity) = false

            override fun iterator(): Iterator<Entity> = emptyList<Entity>().iterator()

            override fun plus(other: EntityIterable): EntityIterable = other

            override fun minus(other: EntityIterable): EntityIterable = this

            override fun times(other: EntityIterable): EntityIterable = this
        }
    }
}

class CollectionEntityIterable(private val c: Collection<Entity>) : EntityIterable {

    override val size = c.size.toLong()

    override val isEmpty = c.isEmpty()

    override fun contains(e: Entity) = e in c

    override fun iterator() = c.iterator()
}

class EntityIdCollectionEntityIterable(
    private val txn: Transaction,
    private val set: Collection<EntityId>
) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e.id in set

    override fun iterator() = set.mapNotNullTo(ArrayList(set.size)) { id -> txn.getEntityOrNull(id) }.iterator()
}

class InstanceIdCollectionEntityIterable(
    private val txn: Transaction,
    private val typeId: Int,
    private val set: Collection<Long>
) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e.id.typeId == typeId && e.id.instanceId in set

    override fun iterator() =
        set.mapNotNullTo(ArrayList(set.size)) { instanceId -> txn.getEntityOrNull(EntityId(typeId, instanceId)) }
            .iterator()
}

typealias EntityIdPredicate = (EntityId) -> Boolean
typealias InstanceIdPredicate = (Long) -> Boolean

private class FilterEntityIdEntityIterable(
    private val decorated: EntityIterable,
    private val predicate: EntityIdPredicate
) : EntityIterable {

    override fun iterator(): Iterator<Entity> = Sequence<Entity> {
        decorated.iterator()
    }.filter { predicate(it.id) }.iterator()
}

private class FilterInstanceIdEntityIterable(
    private val decorated: EntityIterable,
    private val predicate: InstanceIdPredicate
) : EntityIterable {

    override fun iterator(): Iterator<Entity> = Sequence<Entity> {
        decorated.iterator()
    }.filter { predicate(it.id.instanceId) }.iterator()
}

fun EntityIterable.filterEntityIds(predicate: EntityIdPredicate): EntityIterable {
    return FilterEntityIdEntityIterable(this, predicate)
}

fun EntityIterable.filterInstanceIds(predicate: InstanceIdPredicate): EntityIterable {
    return FilterInstanceIdEntityIterable(this, predicate)
}

/**
 * Iterates entities with instance ids from `LongRange`.
 * The range is expected to have step 1.
 */
private class LongRangeEntityIterable(
    private val txn: Transaction,
    private val typeId: Int,
    private val range: LongRange,
    private val filterIdPredicate: InstanceIdPredicate? = null
) : EntityIterable {

    override val size = if (filterIdPredicate == null) range.last - range.start + 1 else super.size

    override fun contains(e: Entity) =
        if (filterIdPredicate == null) e.id.typeId == typeId && e.id.instanceId in range else super.contains(e)

    override fun iterator(): Iterator<Entity> {
        var result = range.asSequence()
        filterIdPredicate?.let { result = result.filter(it) }
        return result.map { txn.getEntityUnsafe(EntityId(typeId, it)) }.iterator()
    }
}

fun Any.longRangeIterable(
    txn: Transaction,
    typeId: Int,
    range: LongRange,
    filterIdPredicate: InstanceIdPredicate? = null
): EntityIterable {
    return LongRangeEntityIterable(txn, typeId, range, filterIdPredicate)
}

inline fun EntityIterable(crossinline iterator: () -> Iterator<Entity>): EntityIterable = object : EntityIterable {
    override fun iterator(): Iterator<Entity> = iterator()
}