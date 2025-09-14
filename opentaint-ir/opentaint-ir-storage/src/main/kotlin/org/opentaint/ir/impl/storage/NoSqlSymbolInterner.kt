package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.storage.ConcurrentSymbolInterner
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.compressed
import org.opentaint.ir.api.storage.ers.nonSearchable
import org.opentaint.ir.api.storage.kv.forEach
import org.opentaint.ir.impl.storage.ers.BuiltInBindingProvider
import org.opentaint.ir.impl.storage.ers.decorators.unwrap
import org.opentaint.ir.impl.storage.ers.kv.KVErsTransaction
import kotlin.math.max

private const val symbolsMapName = "org.opentaint.ir.impl.storage.Symbols"

class NoSqlSymbolInterner(var ers: EntityRelationshipStorage) : ConcurrentSymbolInterner() {

    fun setup() {
        symbolsCache.clear()
        idCache.clear()
        newElements.clear()
        ers.transactional(readonly = true) { txn ->
            var maxId = -1L
            val unwrapped = txn.unwrap
            if (unwrapped is KVErsTransaction) {
                val kvTxn = unwrapped.kvTxn
                val stringBinding = BuiltInBindingProvider.getBinding(String::class.java)
                val longBinding = BuiltInBindingProvider.getBinding(Long::class.java)
                kvTxn.navigateTo(symbolsMapName).forEach { idBytes, nameBytes ->
                    val id = longBinding.getObjectCompressed(idBytes)
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
    }

    override fun flush(context: StorageContext, force: Boolean) {
        if (!context.isErsContext) {
            error("Can't use non-ERS context in NoSqlSymbolInterner")
        }
        if (ers.isInRam && !force) return
        val entries = newElements.entries.toList()
        if (entries.isNotEmpty()) {
            context.txn.let { txn ->
                val unwrapped = txn.unwrap
                if (unwrapped is KVErsTransaction) {
                    val kvTxn = unwrapped.kvTxn
                    val symbolsMap = kvTxn.getNamedMap(symbolsMapName, create = true)!!
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
            entries.forEach {
                newElements.remove(it.key)
            }
        }
        entries.forEach {
            newElements.remove(it.key)
        }
    }
}