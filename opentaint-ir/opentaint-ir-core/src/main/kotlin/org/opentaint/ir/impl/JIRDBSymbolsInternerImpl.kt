package org.opentaint.ir.impl

import org.opentaint.ir.api.jvm.JIRDBContext
import org.opentaint.ir.api.jvm.JIRDBSymbolsInterner
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.storage.ers.compressed
import org.opentaint.ir.api.jvm.storage.ers.nonSearchable
import org.opentaint.ir.api.jvm.storage.kv.forEach
import org.opentaint.ir.impl.storage.connection
import org.opentaint.ir.impl.storage.ers.BuiltInBindingProvider
import org.opentaint.ir.impl.storage.ers.decorators.unwrap
import org.opentaint.ir.impl.storage.ers.kv.KVErsTransaction
import org.opentaint.ir.impl.storage.execute
import org.opentaint.ir.impl.storage.insertElements
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.storage.maxId
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

fun String.asSymbolId(symbolInterner: JIRDBSymbolsInterner): Long {
    return symbolInterner.findOrNew(this)
}

private const val symbolsMapName = "org.opentaint.ir.impl.Symbols"

class JIRDBSymbolsInternerImpl : JIRDBSymbolsInterner, Closeable {

    private val symbolsIdGen = AtomicLong()
    private val symbolsCache = ConcurrentHashMap<String, Long>()
    private val idCache = ConcurrentHashMap<Long, String>()
    private val newElements = ConcurrentSkipListMap<String, Long>()

    fun setup(persistence: JIRDatabasePersistence) = persistence.read { context ->
        context.execute(
            sqlAction = { jooq ->
                jooq.selectFrom(SYMBOLS).fetch().forEach {
                    val (id, name) = it
                    if (name != null && id != null) {
                        symbolsCache[name] = id
                        idCache[id] = name
                    }
                }
                symbolsIdGen.set(SYMBOLS.ID.maxId(jooq) ?: 0L)
            },
            noSqlAction = { txn ->
                var maxId = -1L
                val unwrapped = txn.unwrap
                if (unwrapped is KVErsTransaction) {
                    val kvTxn = unwrapped.kvTxn
                    val stringBinding = BuiltInBindingProvider.getBinding(String::class.java)
                    val longBinding = BuiltInBindingProvider.getBinding(Long::class.java)
                    kvTxn.navigateTo(symbolsMapName).forEach { idBytes, nameBytes ->
                        val id = longBinding.getObject(idBytes)
                        val name = stringBinding.getObject(nameBytes)
                        symbolsCache[name] = id
                        idCache[id] = name
                        maxId = max(maxId, id)
                    }
                } else {
                    val symbols = txn.all("Symbol").toList()
                    symbols.forEach { symbol ->
                        val name: String? = symbol.getBlob("name")
                        val id: Long? = symbol.getCompressedBlob("id")
                        if (name != null && id != null) {
                            symbolsCache[name] = id
                            idCache[id] = name
                            maxId = max(maxId, id)
                        }
                    }
                }
                symbolsIdGen.set(maxId)
            }
        )
    }

    override fun findOrNew(symbol: String): Long {
        return symbolsCache.computeIfAbsent(symbol) {
            symbolsIdGen.incrementAndGet().also {
                newElements[symbol] = it
                idCache[it] = symbol
            }
        }
    }

    override fun findSymbolName(symbolId: Long): String? = idCache[symbolId]

    override fun flush(context: JIRDBContext) {
        val entries = newElements.entries.toList()
        if (entries.isNotEmpty()) {
            context.execute(
                sqlAction = {
                    context.connection.insertElements(
                        SYMBOLS,
                        entries,
                        onConflict = "ON CONFLICT(id) DO NOTHING"
                    ) { (value, id) ->
                        setLong(1, id)
                        setString(2, value)
                    }
                },
                noSqlAction = { txn ->
                    val unwrapped = txn.unwrap
                    if (unwrapped is KVErsTransaction) {
                        val kvTxn = unwrapped.kvTxn
                        val symbolsMap = kvTxn.getNamedMap(symbolsMapName)
                        val stringBinding = BuiltInBindingProvider.getBinding(String::class.java)
                        val longBinding = BuiltInBindingProvider.getBinding(Long::class.java)
                        entries.forEach { (name, id) ->
                            kvTxn.put(symbolsMap, longBinding.getBytesCompressed(id), stringBinding.getBytes(name))
                        }
                    } else {
                        entries.forEach { (name, id) ->
                            txn.newEntity("Symbol").also { symbol ->
                                symbol["name"] = name.nonSearchable
                                symbol["id"] = id.compressed.nonSearchable
                            }
                        }
                    }
                }
            )
            entries.forEach {
                newElements.remove(it.key)
            }
        }
    }

    override fun close() {
        symbolsCache.clear()
        newElements.clear()
    }
}