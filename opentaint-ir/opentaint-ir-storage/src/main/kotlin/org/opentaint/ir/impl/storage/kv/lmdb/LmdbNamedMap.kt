package org.opentaint.ir.impl.storage.kv.lmdb

import org.opentaint.ir.api.storage.kv.NamedMap
import org.opentaint.ir.api.storage.kv.Transaction
import org.opentaint.ir.api.storage.kv.TransactionDecorator
import org.lmdbjava.Dbi
import java.nio.ByteBuffer

internal class LmdbNamedMap(
    val db: Dbi<ByteBuffer>,
    val duplicates: Boolean,
    override val name: String
) : NamedMap {

    override fun size(txn: Transaction): Long {
        var t = txn
        while (t is TransactionDecorator) {
            t = t.decorated
        }
        return db.stat((t as LmdbTransaction).lmdbTxn).entries
    }
}