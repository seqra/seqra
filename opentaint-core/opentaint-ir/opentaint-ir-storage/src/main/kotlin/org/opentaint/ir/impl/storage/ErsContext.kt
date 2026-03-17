package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.storage.ContextProperty
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.api.storage.ers.Transaction

private object ERSTransactionProperty : ContextProperty<Transaction> {
    override fun toString() = "transaction"
}

fun toStorageContext(txn: Transaction) = StorageContext.of(ERSTransactionProperty, txn)

val StorageContext.txn: Transaction get() = getContextObject(ERSTransactionProperty)

val StorageContext.isErsContext: Boolean get() = hasContextObject(ERSTransactionProperty)
