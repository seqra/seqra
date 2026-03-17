package org.opentaint.ir.api.jvm.storage.ers.typed

import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.Transaction
import org.opentaint.ir.api.storage.ers.getBinding

internal class TypedEntityIterableImpl<ENTITY_TYPE : ErsType>(
    override val untypedIterable: EntityIterable
) : TypedEntityIterable<ENTITY_TYPE> {
    override fun iterator(): Iterator<TypedEntity<ENTITY_TYPE>> =
        untypedIterable.iterator()
            .asSequence()
            .map { TypedEntityImpl<ENTITY_TYPE>(it) }
            .iterator()

    override val size get() = untypedIterable.size
    override val isEmpty: Boolean get() = untypedIterable.isEmpty

    override fun contains(entity: TypedEntity<ENTITY_TYPE>): Boolean =
        untypedIterable.contains(entity.untypedEntity)

    override fun plus(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE> =
        TypedEntityIterableImpl(untypedIterable + other.untypedIterable)

    override fun minus(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE> =
        TypedEntityIterableImpl(untypedIterable - other.untypedIterable)

    override fun intersect(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE> =
        TypedEntityIterableImpl(untypedIterable * other.untypedIterable)

    override fun deleteAll() = untypedIterable.deleteAll()
}

internal data class TypedEntityImpl<ENTITY_TYPE : ErsType>(
    override val untypedEntity: Entity
) : TypedEntity<ENTITY_TYPE> {
    override val txn: Transaction get() = untypedEntity.txn
    override val id: TypedEntityId<ENTITY_TYPE> get() = TypedEntityIdImpl(untypedEntity.id)

    override fun compareTo(other: TypedEntity<*>): Int = untypedEntity.compareTo(other.untypedEntity)

    override fun <VALUE : Any> set(property: ErsProperty<ENTITY_TYPE, VALUE, *>, value: VALUE?) {
        val bytes = value?.let { untypedEntity.getBinding(property.valueClass).getBytes(value) }
        when (property.searchability) {
            is ErsSearchability.Searchable -> untypedEntity.setRawProperty(property.name, bytes)
            is ErsSearchability.NonSearchable -> untypedEntity.setRawBlob(property.name, bytes)
        }
    }

    override fun <VALUE : Any> get(property: ErsProperty<ENTITY_TYPE, VALUE, *>): VALUE? {
        val bytes = when (property.searchability) {
            is ErsSearchability.Searchable -> untypedEntity.getRawProperty(property.name)
            is ErsSearchability.NonSearchable -> untypedEntity.getRawBlob(property.name)
        }
        return bytes?.let { untypedEntity.getBinding(property.valueClass).getObject(bytes) }
    }

    override fun <TARGET : ErsType> get(link: ErsLink<ENTITY_TYPE, TARGET>): TypedEntityIterable<TARGET> =
        TypedEntityIterableImpl(untypedEntity.getLinks(link.name))

    override fun <TARGET : ErsType> addLink(
        link: ErsLink<ENTITY_TYPE, TARGET>,
        target: TypedEntity<TARGET>
    ): Boolean = untypedEntity.addLink(link.name, target.untypedEntity)

    override fun <TARGET : ErsType> deleteLink(
        link: ErsLink<ENTITY_TYPE, TARGET>,
        target: TypedEntity<TARGET>
    ): Boolean = untypedEntity.deleteLink(link.name, target.untypedEntity)

    override fun delete() = untypedEntity.delete()
}

internal data class TypeIdImpl<ENTITY_TYPE : ErsType>(
    override val typeId: Int
) : TypeId<ENTITY_TYPE> {
    override fun toEntityId(instanceId: Long): TypedEntityId<ENTITY_TYPE> =
        TypedEntityIdImpl(
            EntityId(
                typeId = typeId,
                instanceId = instanceId
            )
        )
}

internal data class TypedEntityIdImpl<ENTITY_TYPE : ErsType>(
    override val entityId: EntityId
) : TypedEntityId<ENTITY_TYPE> {
    override val typeId: TypeId<ENTITY_TYPE> get() = TypeIdImpl(entityId.typeId)
}
