package org.opentaint.ir.api.storage

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

interface SymbolInterner : Closeable {
    fun findOrNew(symbol: String): Long
    fun findSymbolName(symbolId: Long): String?
    fun flush(context: StorageContext, force: Boolean = false)
}

abstract class ConcurrentSymbolInterner : SymbolInterner {

    protected val symbolsIdGen = AtomicLong()
    protected val symbolsCache = ConcurrentHashMap<String, Long>()
    protected val idCache = ConcurrentHashMap<Long, String>()
    protected val newElements = ConcurrentSkipListMap<String, Long>()

    override fun findOrNew(symbol: String): Long {
        return symbolsCache.computeIfAbsent(symbol) {
            symbolsIdGen.incrementAndGet().also {
                newElements[symbol] = it
                idCache[it] = symbol
            }
        }
    }

    override fun findSymbolName(symbolId: Long): String? = idCache[symbolId]

    override fun close() {
        symbolsCache.clear()
        newElements.clear()
    }
}

fun String.asSymbolId(symbolInterner: SymbolInterner): Long {
    return symbolInterner.findOrNew(this)
}