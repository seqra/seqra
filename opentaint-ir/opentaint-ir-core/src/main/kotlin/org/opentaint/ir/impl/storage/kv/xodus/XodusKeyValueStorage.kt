package org.opentaint.ir.impl.storage.kv.xodus

import jetbrains.exodus.env.Environment
import jetbrains.exodus.env.Environments
import jetbrains.exodus.env.Store
import jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING
import jetbrains.exodus.env.StoreConfig.WITH_DUPLICATES_WITH_PREFIXING
import jetbrains.exodus.env.TransactionBase
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.jvm.storage.kv.Transaction

internal class XodusKeyValueStorage(location: String) : PluggableKeyValueStorage() {

    private val env: Environment = Environments.newInstance(location,
        environmentConfig {
            logFileSize = 32768
            logCachePageSize = 65536 * 4
            gcStartIn = 600_000
            useVersion1Format = false // use v2 data format, as we use stores with prefixing, i.e., patricia trees
        }
    )

    override fun beginTransaction(): Transaction =
        XodusTransaction(this, env.beginTransaction().withNoStoreGetCache())

    override fun beginReadonlyTransaction(): Transaction =
        XodusTransaction(this, env.beginReadonlyTransaction().withNoStoreGetCache())

    override fun close() {
        env.close()
    }

    internal fun getMap(
        xodusTxn: jetbrains.exodus.env.Transaction,
        map: String,
        create: Boolean
    ): Store? {
        return if (create || env.storeExists(map, xodusTxn)) {
            val duplicates = isMapWithKeyDuplicates?.invoke(map)
            env.openStore(
                map,
                if (duplicates == true) WITH_DUPLICATES_WITH_PREFIXING else WITHOUT_DUPLICATES_WITH_PREFIXING,
                xodusTxn
            )
        } else {
            null
        }
    }

    internal fun getMapNames(xodusTxn: jetbrains.exodus.env.Transaction): Set<String> =
        env.getAllStoreNames(xodusTxn).toSortedSet()

    private fun jetbrains.exodus.env.Transaction.withNoStoreGetCache(): jetbrains.exodus.env.Transaction {
        this as TransactionBase
        isDisableStoreGetCache = true
        return this
    }
}