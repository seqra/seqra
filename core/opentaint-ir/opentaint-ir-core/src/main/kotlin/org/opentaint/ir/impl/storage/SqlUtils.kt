package org.opentaint.ir.impl.storage

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.util.*

fun configuredSQLiteDataSource(location: String? = null): SQLiteDataSource {
    val config = SQLiteConfig().also {
        it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
        it.setJournalMode(SQLiteConfig.JournalMode.OFF)
        it.setPageSize(32_768)
        it.setCacheSize(-8_000)
        it.setSharedCache(true)
    }
    val props = listOfNotNull(
        ("mode" to "memory").takeIf { location == null },
        ("cache" to "shared").takeIf { location == null },
        "rewriteBatchedStatements" to "true",
        "useServerPrepStmts" to "false"
    ).joinToString("&") { "${it.first}=${it.second}" }
    return SQLiteDataSource(config).also {
        it.url = "jdbc:sqlite:file:${location ?: ("jIRdb-" + UUID.randomUUID())}?$props"
    }
}