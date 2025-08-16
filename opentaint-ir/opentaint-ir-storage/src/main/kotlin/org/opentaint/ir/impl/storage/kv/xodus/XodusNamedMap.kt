package org.opentaint.ir.impl.storage.kv.xodus

import jetbrains.exodus.env.Store
import org.opentaint.ir.api.storage.kv.NamedMap
import org.opentaint.ir.api.storage.kv.Transaction

internal class XodusNamedMap(val store: Store) : NamedMap {

    override val name: String get() = store.name

    override fun size(txn: Transaction): Long = store.count((txn as XodusTransaction).xodusTxn)
}