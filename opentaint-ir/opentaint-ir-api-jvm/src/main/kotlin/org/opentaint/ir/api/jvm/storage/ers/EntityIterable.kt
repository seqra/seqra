package org.opentaint.ir.api.jvm.storage.ers

interface EntityIterable : Iterable<Entity> {

    val size: Long

    val isEmpty: Boolean

    val isNotEmpty: Boolean get() = !isEmpty

    operator fun contains(e: Entity): Boolean

    operator fun plus(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> this
        else -> CollectionEntityIterable(union(other))
    }

    operator fun times(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> EMPTY
        else -> CollectionEntityIterable(intersect(other))
    }

    operator fun minus(other: EntityIterable): EntityIterable = when(other) {
        EMPTY -> this
        else -> CollectionEntityIterable(subtract(other))
    }

    fun deleteAll() = forEach { entity -> entity.delete() }

    companion object {

        val EMPTY: EntityIterable = object : EntityIterable {

            override val size = 0L

            override val isEmpty = true

            override fun contains(e: Entity) = false

            override fun iterator(): Iterator<Entity> = emptyList<Entity>().iterator()

            override fun plus(other: EntityIterable): EntityIterable = other

            override fun minus(other: EntityIterable): EntityIterable = this

            override fun times(other: EntityIterable): EntityIterable = this
        }
    }
}

class CollectionEntityIterable(private val set: Collection<Entity>) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e in set

    override fun iterator() = set.iterator()
}

class EntityIdCollectionEntityIterable(
    private val txn: Transaction,
    private val set: Collection<EntityId>
) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e.id in set

    override fun iterator() = buildList {
        set.forEach { id -> txn.getEntityOrNull(id)?.let { e -> add(e) } }
    }.iterator()
}

class InstanceIdCollectionEntityIterable(
    private val txn: Transaction,
    private val typeId: Int,
    private val set: Collection<Long>
) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e.id.typeId == typeId && e.id.instanceId in set

    override fun iterator() = buildList {
        set.forEach { instanceId -> txn.getEntityOrNull(EntityId(typeId, instanceId))?.let { e -> add(e) } }
    }.iterator()
}