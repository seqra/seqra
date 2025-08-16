package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.storage.ConcurrentSymbolInterner
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS

class SqlSymbolInterner : ConcurrentSymbolInterner() {

    fun setup(persistence: JIRDatabasePersistence) = persistence.read { context ->
        context.execute {
            val jooq = context.dslContext
            jooq.selectFrom(SYMBOLS).fetch().forEach {
                val (id, name) = it
                if (name != null && id != null) {
                    symbolsCache[name] = id
                    idCache[id] = name
                }
            }
            symbolsIdGen.set(SYMBOLS.ID.maxId(jooq) ?: 0L)
        }
    }

    override fun flush(context: StorageContext, force: Boolean) {
        val entries = newElements.entries.toList()
        if (entries.isNotEmpty()) {
            context.execute {
                context.connection.insertElements(
                    SYMBOLS,
                    entries,
                    onConflict = "ON CONFLICT(id) DO NOTHING"
                ) { (value, id) ->
                    setLong(1, id)
                    setString(2, value)
                }
            }
            entries.forEach {
                newElements.remove(it.key)
            }
        }
    }

    private fun StorageContext.execute(action: () -> Unit) {
        execute(sqlAction = action, noSqlAction = { error("Can't execute NoSql action in SqlSymbolInterner") })
    }
}