package org.opentaint.ir.impl.storage.kv.lmdb

import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.jvm.storage.kv.Transaction
import org.opentaint.ir.api.jvm.storage.kv.withFinishedState
import org.opentaint.ir.impl.JIRLmdbErsSettings
import org.lmdbjava.Dbi
import org.lmdbjava.Dbi.KeyNotFoundException
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import org.lmdbjava.EnvFlags
import java.io.File
import java.nio.ByteBuffer

internal class LmdbKeyValueStorage(location: String, settings: JIRLmdbErsSettings) : PluggableKeyValueStorage() {

    private val env = Env.create().apply {
        setMaxDbs(999999)
        setMaxReaders(9999999)
        setMapSize(settings.mapSize)
    }.open(File(location), EnvFlags.MDB_NOTLS)

    override fun beginTransaction(): Transaction {
        return LmdbTransaction(this, env.txnWrite()).withFinishedState()
    }

    override fun beginReadonlyTransaction(): Transaction {
        return LmdbTransaction(this, env.txnRead()).withFinishedState()
    }

    override fun close() {
        env.close()
    }

    internal fun getMap(lmdbTxn: org.lmdbjava.Txn<ByteBuffer>, map: String): Pair<Dbi<ByteBuffer>, Boolean>? {
        val duplicates = isMapWithKeyDuplicates?.invoke(map) == true
        return if (lmdbTxn.isReadOnly) {
            try {

                env.openDbi(lmdbTxn, map.toByteArray(), null, false) to duplicates
            } catch (_: KeyNotFoundException) {
                null
            }
        } else {
            if (duplicates) {
                env.openDbi(lmdbTxn, map.toByteArray(), null, false, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT)
            } else {
                env.openDbi(lmdbTxn, map.toByteArray(), null, false, DbiFlags.MDB_CREATE)
            } to duplicates
        }
    }
}