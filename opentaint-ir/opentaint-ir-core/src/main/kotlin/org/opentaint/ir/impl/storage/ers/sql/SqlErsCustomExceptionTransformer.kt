package org.opentaint.ir.impl.storage.ers.sql

import org.opentaint.ir.api.jvm.storage.ers.ERSConflictingTransactionException
import org.jooq.ExecuteContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DefaultExecuteListener

class SqlErsExceptionTransformer : DefaultExecuteListener() {
    override fun exception(ctx: ExecuteContext) {
        val exception = ctx.exception()
        if (exception is DataAccessException && exception.message?.contains("SQLITE_LOCKED_SHAREDCACHE") == true) {
            val customException = ERSConflictingTransactionException(
                "Cannot perform an operation in a transaction since a parallel one has locked a shared resource",
                exception
            )
            ctx.exception(customException)
        }
    }
}
