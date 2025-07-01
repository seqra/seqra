package org.opentaint.ir.api.jvm.storage.ers.typed

import org.opentaint.ir.api.jvm.storage.ers.Entity
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.FindOption
import org.opentaint.ir.api.jvm.storage.ers.Transaction

fun <ENTITY_TYPE : ErsType> Transaction.newEntity(type: ENTITY_TYPE): TypedEntity<ENTITY_TYPE> =
    TypedEntityImpl(newEntity(type.typeName))

fun <ENTITY_TYPE : ErsType> Transaction.getEntityOrNull(id: TypedEntityId<ENTITY_TYPE>): TypedEntity<ENTITY_TYPE>? =
    getEntityOrNull(id.entityId)?.let { TypedEntityImpl(it) }

fun <ENTITY_TYPE : ErsType> Transaction.getTypeId(type: ENTITY_TYPE): TypeId<ENTITY_TYPE> =
    TypeIdImpl(getTypeId(type.typeName))

/// region find extension functions

fun <ENTITY_TYPE : ErsType, VALUE : Any> Transaction.find(
    property: ErsProperty<ENTITY_TYPE, VALUE, ErsSearchability.Searchable>,
    value: VALUE
): TypedEntityIterable<ENTITY_TYPE> = TypedEntityIterableImpl(
    find(
        type = property.ownerType.typeName,
        propertyName = property.name,
        value = value
    )
)

fun <ENTITY_TYPE : ErsType, VALUE : Any> Transaction.findLt(
    property: ErsProperty<ENTITY_TYPE, VALUE, ErsSearchability.Searchable>,
    value: VALUE
): TypedEntityIterable<ENTITY_TYPE> = TypedEntityIterableImpl(
    findLt(
        type = property.ownerType.typeName,
        propertyName = property.name,
        value = value
    )
)

fun <ENTITY_TYPE : ErsType, VALUE : Any> Transaction.findEqOrLt(
    property: ErsProperty<ENTITY_TYPE, VALUE, ErsSearchability.Searchable>,
    value: VALUE
): TypedEntityIterable<ENTITY_TYPE> = TypedEntityIterableImpl(
    findEqOrLt(
        type = property.ownerType.typeName,
        propertyName = property.name,
        value = value
    )
)

fun <ENTITY_TYPE : ErsType, VALUE : Any> Transaction.findGt(
    property: ErsProperty<ENTITY_TYPE, VALUE, ErsSearchability.Searchable>,
    value: VALUE
): TypedEntityIterable<ENTITY_TYPE> = TypedEntityIterableImpl(
    findGt(
        type = property.ownerType.typeName,
        propertyName = property.name,
        value = value
    )
)

fun <ENTITY_TYPE : ErsType, VALUE : Any> Transaction.findEqOrGt(
    property: ErsProperty<ENTITY_TYPE, VALUE, ErsSearchability.Searchable>,
    value: VALUE
): TypedEntityIterable<ENTITY_TYPE> = TypedEntityIterableImpl(
    findEqOrGt(
        type = property.ownerType.typeName,
        propertyName = property.name,
        value = value
    )
)

fun <ENTITY_TYPE : ErsType, VALUE : Any> Transaction.find(
    property: ErsProperty<ENTITY_TYPE, VALUE, ErsSearchability.Searchable>,
    value: VALUE,
    option: FindOption
): TypedEntityIterable<ENTITY_TYPE> = TypedEntityIterableImpl(
    find(
        type = property.ownerType.typeName,
        propertyName = property.name,
        value = value,
        option = option
    )
)

/// endregion

fun <ENTITY_TYPE : ErsType> Transaction.all(type: ENTITY_TYPE): TypedEntityIterable<ENTITY_TYPE> =
    TypedEntityIterableImpl(all(type.typeName))

interface TypeId<ENTITY_TYPE : ErsType> {
    val typeId: Int
    fun toEntityId(instanceId: Long): TypedEntityId<ENTITY_TYPE>
}

interface TypedEntityId<ENTITY_TYPE : ErsType> {
    val entityId: EntityId
    val typeId: TypeId<ENTITY_TYPE>
    val instanceId: Long get() = entityId.instanceId
}

interface TypedEntityIterable<ENTITY_TYPE : ErsType> : Iterable<TypedEntity<ENTITY_TYPE>> {
    val untypedIterable: EntityIterable
    val size: Long
    val isEmpty: Boolean
    val isNotEmpty: Boolean get() = !isEmpty
    operator fun contains(entity: TypedEntity<ENTITY_TYPE>): Boolean
    operator fun plus(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE>
    operator fun minus(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE>
    fun intersect(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE>
    fun deleteAll()
}

interface TypedEntity<ENTITY_TYPE : ErsType> : Comparable<TypedEntity<*>> {
    val untypedEntity: Entity
    val txn: Transaction
    val id: TypedEntityId<ENTITY_TYPE>
    operator fun <VALUE : Any> set(property: ErsProperty<ENTITY_TYPE, VALUE, *>, value: VALUE?)
    operator fun <VALUE : Any> get(property: ErsProperty<ENTITY_TYPE, VALUE, *>): VALUE?
    operator fun <TARGET : ErsType> get(link: ErsLink<ENTITY_TYPE, TARGET>): TypedEntityIterable<TARGET>
    fun <TARGET : ErsType> addLink(link: ErsLink<ENTITY_TYPE, TARGET>, target: TypedEntity<TARGET>): Boolean
    fun <TARGET : ErsType> deleteLink(link: ErsLink<ENTITY_TYPE, TARGET>, target: TypedEntity<TARGET>): Boolean
    fun delete()
}
