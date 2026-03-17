package org.opentaint.ir.impl.storage.ers.sql

import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.impl.storage.ers.jooq.tables.references.TYPES
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.BLOB_VALUE_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.ENTITY_ID_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.LINK_SOURCE_ENTITY_ID_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.LINK_TARGET_ENTITY_ID_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.PROPERTY_VALUE_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.getBlobTableName
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.getEntityTableNameByTypeId
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.getLinkTableName
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.getPropertyTableName
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.parseLinkTableNameOrNull
import org.jooq.DSLContext
import org.jooq.Name
import org.jooq.Table
import org.jooq.impl.DSL
import java.sql.Connection

interface SqlErsContext {
    val connection: Connection
    val jooq: DSLContext
    val ers: EntityRelationshipStorage
    fun nextEntityId(): Long
    fun nextTypeId(): Int

    fun getTableOrNull(name: Name): Table<*>? {
        return jooq.meta().getTables(name).firstOrNull()
    }

    fun getPropertyTableOrNull(typeId: Int, propertyName: String): Table<*>? {
        return getTableOrNull(getPropertyTableName(typeId, propertyName))
    }

    fun getOrCreatePropertyTable(typeId: Int, propertyName: String): Table<*> {
        val tableName = getPropertyTableName(typeId, propertyName)
        getTableOrNull(tableName)?.let { return it }

        val entityTable = getOrCreateEntityTableByTypeId(typeId)

        jooq.createTable(tableName)
            .column(ENTITY_ID_FIELD)
            .column(PROPERTY_VALUE_FIELD)
            .constraints(
                DSL.primaryKey(ENTITY_ID_FIELD),
                DSL.foreignKey(ENTITY_ID_FIELD).references(entityTable, ENTITY_ID_FIELD).onDeleteCascade()
            )
            .execute()

        return getTableOrNull(tableName)!!
    }

    fun getBlobTableOrNull(typeId: Int, blobName: String): Table<*>? {
        return getTableOrNull(getBlobTableName(typeId, blobName))
    }

    fun getOrCreateBlobTable(typeId: Int, blobName: String): Table<*> {
        val tableName = getBlobTableName(typeId, blobName)
        getTableOrNull(tableName)?.let { return it }

        val entityTable = getOrCreateEntityTableByTypeId(typeId)

        jooq.createTable(tableName)
            .column(ENTITY_ID_FIELD)
            .column(BLOB_VALUE_FIELD)
            .constraints(
                DSL.primaryKey(ENTITY_ID_FIELD),
                DSL.foreignKey(ENTITY_ID_FIELD).references(entityTable, ENTITY_ID_FIELD).onDeleteCascade()
            )
            .execute()

        return getTableOrNull(tableName)!!
    }

    fun getLinkTables(sourceTypeId: Int, linkName: String): List<Pair<Table<*>, LinkTableMetaData>> {
        return jooq.meta().tables.mapNotNull { table ->
            parseLinkTableNameOrNull(table)?.let { table to it }
        }
    }

    fun getOrCreateLinkTable(linkTableMetaData: LinkTableMetaData): Table<*> {
        val tableName = getLinkTableName(linkTableMetaData)
        getTableOrNull(tableName)?.let { return it }

        val sourceEntityTable = getOrCreateEntityTableByTypeId(linkTableMetaData.sourceTypeId)
        val targetEntityTable = getOrCreateEntityTableByTypeId(linkTableMetaData.targetTypeId)

        jooq.createTable(tableName)
                .column(LINK_SOURCE_ENTITY_ID_FIELD)
                .column(LINK_TARGET_ENTITY_ID_FIELD)
                .constraints(
                        DSL.primaryKey(LINK_SOURCE_ENTITY_ID_FIELD, LINK_TARGET_ENTITY_ID_FIELD),
                        DSL.foreignKey(LINK_SOURCE_ENTITY_ID_FIELD)
                                .references(sourceEntityTable, ENTITY_ID_FIELD)
                                .onDeleteCascade(),
                        DSL.foreignKey(LINK_TARGET_ENTITY_ID_FIELD)
                                .references(targetEntityTable, ENTITY_ID_FIELD)
                                .onDeleteCascade(),
                )
                .execute()

        return getTableOrNull(tableName)!!
    }

    fun getEntityTableByTypeIdOrNull(typeId: Int): Table<*>? {
        return getTableOrNull(getEntityTableNameByTypeId(typeId))
    }

    fun getOrCreateEntityTableByTypeId(typeId: Int): Table<*> {
        val tableName = getEntityTableNameByTypeId(typeId)
        getTableOrNull(tableName)?.let { return it }

        jooq.createTable(tableName)
            .column(ENTITY_ID_FIELD)
            .constraints(DSL.primaryKey(ENTITY_ID_FIELD))
            .execute()

        return getTableOrNull(tableName)!!
    }

    fun getOrCreateTypeId(type: String): Int {
        getTypeIdOrNull(type)?.let { return it }

        val typeId = nextTypeId()
        jooq.insertInto(TYPES)
            .set(TYPES.ID, typeId)
            .set(TYPES.NAME, type)
            .execute()

        return typeId
    }

    fun getTypeIdOrNull(type: String): Int? =
        jooq.select(TYPES.ID)
            .from(TYPES)
            .where(TYPES.NAME.eq(type))
            .fetchOne()
            ?.value1()
}
