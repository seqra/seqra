package org.opentaint.ir.impl.storage.ers.sql

import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Table

// TODO optimize implementation
class SqlErsEntityIterable(
    private val condition: Condition,
    private val fromTables: List<Table<*>>,
    private val entityIdField: Field<Long>,
    private val typeId: Int,
    private val txn: SqlErsTransaction
) : EntityIterable {
    private val jooq get() = txn.jooq

    override val size: Long
        get() = iterator().asSequence().count().toLong()

    override val isEmpty: Boolean get() = !iterator().hasNext()

    override fun contains(e: Entity): Boolean = iterator().asSequence().contains(e)

    override fun plus(other: EntityIterable): EntityIterable {
        return super.plus(other)
    }

    override fun times(other: EntityIterable): EntityIterable {
        return super.times(other)
    }

    override fun minus(other: EntityIterable): EntityIterable {
        return super.minus(other)
    }

    override fun deleteAll() {
        super.deleteAll()
    }

    override fun iterator(): Iterator<Entity> {
        return jooq.select(entityIdField)
            .from(fromTables)
            .where(condition)
            .fetch()
            .map {
                SqlErsEntity(EntityId(typeId, it.value1()), txn)
            }
            .iterator()
    }
}
