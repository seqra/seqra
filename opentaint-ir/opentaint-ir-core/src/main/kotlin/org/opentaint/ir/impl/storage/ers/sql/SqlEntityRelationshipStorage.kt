package org.opentaint.ir.impl.storage.ers.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.opentaint.ir.api.jvm.storage.ers.BindingProvider
import org.opentaint.ir.api.jvm.storage.ers.ERSConflictingTransactionException
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.jvm.storage.ers.Transaction
import org.opentaint.ir.impl.storage.ers.decorators.withAllDecorators
import org.opentaint.ir.impl.storage.ers.jooq.tables.references.TYPES
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.ENTITY_ID_FIELD
import org.opentaint.ir.impl.storage.ers.sql.SqlErsNames.ENTITY_TABLE_PREFIX
import org.opentaint.ir.impl.storage.executeQueriesFrom
import org.opentaint.ir.impl.util.allCauses
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListenerProvider
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private fun Connection.createSQLiteDSLContext(): DSLContext {
    val settings = Settings().withExecuteLogging(false)
    val configuration = DSL.using(/*connection = */ this, SQLDialect.SQLITE, settings)
        .configuration()
        .derive(DefaultExecuteListenerProvider(SqlErsExceptionTransformer()))

    return DSL.using(configuration).also {
        // Fixes CASCADE DELETE
        it.execute("PRAGMA foreign_keys = ON;")
    }
}

class SqlEntityRelationshipStorage(
    dataSource: SQLiteDataSource,
    bindingProvider: BindingProvider
) : EntityRelationshipStorage, BindingProvider by bindingProvider {
    private val primaryConnection: Connection = dataSource.connection
    private val jooq: DSLContext = primaryConnection.createSQLiteDSLContext()
    private val connectionPool = run {
        val config = HikariConfig()
        config.dataSource = dataSource
        config.connectionTimeout = 15_000
        HikariDataSource(config)
    }

    init {
        jooq.executeQueriesFrom("ers/sqlite/create-schema.sql")
    }

    private val entityIdGen = AtomicLong(
        jooq.meta().tables
            .filter { it.name.startsWith(ENTITY_TABLE_PREFIX) }
            .maxOfOrNull { table -> jooq.select(DSL.max(ENTITY_ID_FIELD)).from(table).fetchAny()?.component1() ?: 0L }
            ?: 0L
    )

    private val typeIdGen = AtomicInteger(
        jooq.select(DSL.max(TYPES.ID)).from(TYPES).fetchAny()?.component1() ?: 0
    )

    override fun beginTransaction(readonly: Boolean): Transaction {
        return SqlErsTransactionImpl(object : SqlErsContext {
            override val connection: Connection = getConnectionFromPool()
            override val jooq: DSLContext = connection.createSQLiteDSLContext()
            override val ers: EntityRelationshipStorage = this@SqlEntityRelationshipStorage
            override fun nextEntityId(): Long = entityIdGen.incrementAndGet()
            override fun nextTypeId(): Int = typeIdGen.incrementAndGet()
        }).withAllDecorators()
    }

    private fun getConnectionFromPool(): Connection = try {
        connectionPool.connection
    } catch (e: SQLException) {
        if (e.allCauses().any { it.message?.contains("SQLITE_LOCKED_SHAREDCACHE") == true }) {
            throw ERSConflictingTransactionException(
                "Cannot begin a transaction since a parallel one has locked a shared resource", e
            )
        } else {
            throw e
        }
    }

    override fun close() {
        connectionPool.close()
        primaryConnection.close()
    }
}
