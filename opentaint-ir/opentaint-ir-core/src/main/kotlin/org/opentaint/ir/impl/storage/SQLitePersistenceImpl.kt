package org.opentaint.ir.impl.storage

import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.logger
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SQLitePersistenceImpl(
    javaRuntime: JavaRuntime,
    featuresRegistry: FeaturesRegistry,
    location: String? = null,
    clearOnStart: Boolean
) : AbstractJIRDatabasePersistenceImpl(javaRuntime, featuresRegistry, clearOnStart) {

    private var connection: Connection? = null
    override val jooq: DSLContext

    private val lock = ReentrantLock()

    init {
        val config = SQLiteConfig().also {
            it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            it.setJournalMode(SQLiteConfig.JournalMode.OFF)
            it.setPageSize(32_768)
            it.setCacheSize(-8_000)
            it.setSharedCache(true)
        }
        val props = listOfNotNull(
            ("mode" to "memory").takeIf { location == null },
            "rewriteBatchedStatements" to "true",
            "useServerPrepStmts" to "false"
        ).joinToString("&") { "${it.first}=${it.second}" }
        val dataSource = SQLiteDataSource(config).also {
            it.url = "jdbc:sqlite:file:${location ?: ("jIRdb-" + UUID.randomUUID())}?$props"
        }
        connection = dataSource.connection
        jooq = DSL.using(connection, SQLDialect.SQLITE, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart || !runtimeProcessed) {
                jooq.executeQueriesFrom("sqlite/drop-schema.sql")
            }
            jooq.executeQueriesFrom("sqlite/create-schema.sql")
        }
    }

    private val runtimeProcessed: Boolean
        get() {
            try {
                val count = jooq.fetchCount(
                    BYTECODELOCATIONS,
                    BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal)
                        .and(BYTECODELOCATIONS.RUNTIME.isTrue)
                )
                return count == 0
            } catch (e: Exception) {
                logger.warn("can't check that runtime libraries is processed with", e)
                return false
            }
        }

    override fun <T> write(action: (DSLContext) -> T): T = lock.withLock {
        action(jooq)
    }

    override fun close() {
        super.close()
        try {
            connection?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun getScript(name: String): String {
        return javaClass.classLoader.getResourceAsStream("sqlite/$name")?.reader()?.readText()
            ?: throw IllegalStateException("no sql script for sqlite/$name found")
    }

    override fun createIndexes() {
        jooq.executeQueries(getScript("add-indexes.sql"))
    }
}