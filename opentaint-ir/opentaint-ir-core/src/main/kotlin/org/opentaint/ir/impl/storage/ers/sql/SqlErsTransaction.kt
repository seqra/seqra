package org.opentaint.ir.impl.storage.ers.sql

import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.Transaction
import org.opentaint.ir.api.storage.ers.probablyCompressed
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.ENTITY_ID_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.PROPERTY_VALUE_FIELD
import org.opentaint.ir.impl.storage.executeQueriesFrom
import org.jooq.impl.DSL

interface SqlErsTransaction : Transaction, SqlErsContext

class SqlErsTransactionImpl(
    ersContext: SqlErsContext
) : SqlErsTransaction, SqlErsContext by ersContext {
    init {
        connection.autoCommit = false
    }

    override val isReadonly = false

    override val isFinished: Boolean
        get() = connection.isClosed

    override fun newEntity(type: String): Entity {
        val entityId = nextEntityId()
        val typeId = getOrCreateTypeId(type)
        val entityTable = getOrCreateEntityTableByTypeId(typeId)
        jooq.insertInto(entityTable)
            .set(ENTITY_ID_FIELD, entityId)
            .execute()
        return SqlErsEntity(
            EntityId(
                typeId = typeId,
                instanceId = entityId
            ), txn = this
        )
    }

    override fun getEntityOrNull(id: EntityId): Entity? {
        val entityTable = getEntityTableByTypeIdOrNull(id.typeId) ?: return null
        return jooq.select()
            .from(entityTable)
            .where(ENTITY_ID_FIELD.eq(id.instanceId))
            .fetchOne()
            ?.let { SqlErsEntity(id, txn = this) }
    }

    override fun getEntityUnsafe(id: EntityId): Entity = SqlErsEntity(id, txn = this)

    override fun deleteEntity(id: EntityId) {
        val entityTable = getEntityTableByTypeIdOrNull(id.typeId) ?: return
        jooq.deleteFrom(entityTable)
            .where(ENTITY_ID_FIELD.eq(id.instanceId))
            .execute()
    }

    override fun getTypeId(type: String): Int {
        return getTypeIdOrNull(type) ?: return -1
    }

    override fun getPropertyNames(type: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getBlobNames(type: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getLinkNames(type: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun all(type: String): EntityIterable {
        val typeId = getTypeIdOrNull(type) ?: return EntityIterable.EMPTY
        val entityTable = getEntityTableByTypeIdOrNull(typeId) ?: return EntityIterable.EMPTY
        return SqlErsEntityIterable(
            condition = DSL.trueCondition(),
            fromTables = listOf(entityTable),
            entityIdField = entityTable.field(ENTITY_ID_FIELD)!!,
            typeId = typeId,
            txn = this
        )
    }

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable {
        val typeId = getTypeIdOrNull(type) ?: return EntityIterable.EMPTY
        val propertyTable = getPropertyTableOrNull(typeId, propertyName) ?: return EntityIterable.EMPTY
        return SqlErsEntityIterable(
            condition = propertyTable.field(PROPERTY_VALUE_FIELD)!!.eq(probablyCompressed(value)),
            fromTables = listOf(propertyTable),
            entityIdField = propertyTable.field(ENTITY_ID_FIELD)!!,
            typeId = typeId,
            txn = this
        )
    }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun dropAll() {
        val dynamicTables = jooq.meta().tables.filter { table ->
            SqlErsNames.DYNAMIC_TABLE_PREFIXES.any { prefix -> table.name.startsWith(prefix) }
        }
        dynamicTables.forEach { table ->
            jooq.execute(DSL.dropTable(DSL.name(table.name)).toString())
        }
        jooq.executeQueriesFrom("ers/sqlite/drop-schema.sql")
        jooq.executeQueriesFrom("ers/sqlite/create-schema.sql")
    }

    override fun commit() {
        connection.commit()
        connection.close()
    }

    override fun abort() {
        connection.rollback()
        connection.close()
    }
}
