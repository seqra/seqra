package org.opentaint.opentaint-ir.impl.storage

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.opentaint.opentaint-ir.impl.FeaturesRegistry
import org.opentaint.opentaint-ir.impl.fs.JavaRuntime
import java.sql.Connection
import java.util.*

class SQLitePersistenceImpl(
    javaRuntime: JavaRuntime,
    featuresRegistry: FeaturesRegistry,
    location: String? = null,
    clearOnStart: Boolean
) : AbstractJIRDatabasePersistenceImpl(javaRuntime, featuresRegistry, clearOnStart) {

    private var connection: Connection? = null
    override val jooq: DSLContext

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
            if (clearOnStart) {
                jooq.executeQueriesFrom("sqlite/drop-schema.sql")
            }
            jooq.executeQueriesFrom("sqlite/create-schema.sql")
        }
    }

    override fun close() {
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
}