package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.ContextProperty
import org.opentaint.ir.api.jvm.JIRDBContext
import org.opentaint.ir.api.jvm.invoke
import org.opentaint.ir.api.jvm.storage.ers.Transaction
import org.jooq.DSLContext
import java.sql.Connection

private object DSLContextProperty : ContextProperty<DSLContext> {
    override fun toString() = "dslContext"
}

private object ConnectionProperty : ContextProperty<Connection> {
    override fun toString() = "connection"
}

private object ERSTransactionProperty : ContextProperty<Transaction> {
    override fun toString() = "transaction"
}

fun toJIRDBContext(dslContext: DSLContext, connection: Connection): JIRDBContext =
    toJIRDBContext(dslContext)(ConnectionProperty, connection)

fun toJIRDBContext(dslContext: DSLContext): JIRDBContext = JIRDBContext.of(DSLContextProperty, dslContext)

val JIRDBContext.dslContext: DSLContext get() = getContextObject(DSLContextProperty)

val JIRDBContext.connection: Connection get() = getContextObject(ConnectionProperty)

val JIRDBContext.isSqlContext: Boolean get() = hasContextObject(DSLContextProperty)

fun toJIRDBContext(txn: Transaction) = JIRDBContext.of(ERSTransactionProperty, txn)

val JIRDBContext.txn: Transaction get() = getContextObject(ERSTransactionProperty)

val JIRDBContext.isErsContext: Boolean get() = hasContextObject(ERSTransactionProperty)

fun <T> JIRDBContext.execute(sqlAction: (DSLContext) -> T, noSqlAction: (Transaction) -> T): T {
    return if (isSqlContext) {
        sqlAction(dslContext)
    } else if (isErsContext) {
        noSqlAction(txn)
    } else {
        throw IllegalArgumentException("JIRDBContext should support SQL or NoSQL persistence")
    }
}