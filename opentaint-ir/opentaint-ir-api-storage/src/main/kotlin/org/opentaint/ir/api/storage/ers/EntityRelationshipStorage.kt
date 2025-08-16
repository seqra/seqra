package org.opentaint.ir.api.storage.ers

import org.opentaint.ir.api.spi.CommonSPI
import org.opentaint.ir.api.spi.SPILoader
import java.io.Closeable
import java.util.*

interface EntityRelationshipStorage : Closeable, BindingProvider {

    val isInRam: Boolean get() = false

    @Throws(ERSConflictingTransactionException::class)
    fun beginTransaction(readonly: Boolean = false): Transaction

    @Throws(ERSConflictingTransactionException::class)
    fun <T> transactional(readonly: Boolean = false, action: (Transaction) -> T): T {
        beginTransaction(readonly = readonly).use { txn ->
            return action(txn)
        }
    }

    @Throws(ERSConflictingTransactionException::class)
    fun <T> transactionalOptimistic(attempts: Int = 5, action: (Transaction) -> T): T {
        repeat(attempts) {
            try {
                beginTransaction(readonly = false).use { txn ->
                    return action(txn)
                }
            } catch (_: ERSConflictingTransactionException) {
            }
        }
        throw ERSConflictingTransactionException("Failed to commit transaction after $attempts optimistic attempts")
    }

    /**
     * Returns an immutable storage holding the latest available snapshot of data.
     * `databaseId` can be used to dump the snapshot.
     */
    fun asImmutable(databaseId: String): EntityRelationshipStorage
}

interface EntityRelationshipStorageSPI : CommonSPI {

    fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): EntityRelationshipStorageSPI {
            return loadSPI(id) ?: throw ERSException("No EntityRelationshipStorageSPI implementation found by id = $id")
        }
    }
}

interface ErsSettings
object EmptyErsSettings : ErsSettings

open class ERSException(
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ERSConflictingTransactionException(
    message: String? = null,
    cause: Throwable? = null
) : ERSException(message, cause)

class ERSNonExistingEntityException(
    message: String? = "Cannot perform an operation, because entity doesn't exist",
    cause: Throwable? = null
) : ERSException(message, cause)

class ERSTransactionFinishedException(
    message: String? = "Cannot perform an operation, because transaction has been finished",
    cause: Throwable? = null
) : ERSException(message, cause)
