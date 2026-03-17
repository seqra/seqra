package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.storage.ContextProperty
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.api.storage.invoke
import org.jooq.DSLContext
import java.sql.Connection

private object DSLContextProperty : ContextProperty<DSLContext> {
    override fun toString() = "dslContext"
}

private object ConnectionProperty : ContextProperty<Connection> {
    override fun toString() = "connection"
}

fun toStorageContext(dslContext: DSLContext, connection: Connection): StorageContext =
    toStorageContext(dslContext)(ConnectionProperty, connection)

fun toStorageContext(dslContext: DSLContext): StorageContext = StorageContext.of(DSLContextProperty, dslContext)

val StorageContext.dslContext: DSLContext get() = getContextObject(DSLContextProperty)

val StorageContext.connection: Connection get() = getContextObject(ConnectionProperty)

val StorageContext.isSqlContext: Boolean get() = hasContextObject(DSLContextProperty)

fun <T> StorageContext.execute(sqlAction: () -> T, noSqlAction: () -> T): T {
    return if (isErsContext) {
        noSqlAction()
    } else if (isSqlContext) {
        sqlAction()
    } else {
        throw IllegalArgumentException("StorageContext should support SQL or NoSQL persistence")
    }
}