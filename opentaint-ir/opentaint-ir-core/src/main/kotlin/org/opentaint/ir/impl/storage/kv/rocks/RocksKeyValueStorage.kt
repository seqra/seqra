package org.opentaint.ir.impl.storage.kv.rocks

import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.jvm.storage.kv.Transaction
import org.opentaint.ir.impl.storage.ers.BuiltInBindingProvider
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.DBOptions
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.rocksdb.TransactionDB
import org.rocksdb.TransactionDBOptions
import org.rocksdb.WriteOptions
import java.util.concurrent.ConcurrentHashMap

internal abstract class RocksKeyValueStorage : PluggableKeyValueStorage() {
    abstract val sizesColumnFamily: ColumnFamilyHandle

    abstract fun getNamedMapOrNull(name: String): RocksNamedMap?
    abstract fun getOrCreateNamedMap(name: String): RocksNamedMap
}

internal class RocksKeyValueStorageImpl(location: String) : RocksKeyValueStorage() {

    companion object {
        init {
            // a static method that loads the RocksDB C++ library.
            RocksDB.loadLibrary()
        }
    }

    @Suppress("JoinDeclarationAndAssignment")
    private val columnFamilyOptions: ColumnFamilyOptions
    private val dbOptions: DBOptions
    private val transactionDbOptions: TransactionDBOptions
    private val rocksDB: TransactionDB
    private val columnFamiliesMap = ConcurrentHashMap<List<Byte>, ColumnFamilyHandle>()

    override val sizesColumnFamily: ColumnFamilyHandle

    init {
        columnFamilyOptions = ColumnFamilyOptions()
        try {
            dbOptions = DBOptions().setCreateIfMissing(true)
            try {
                transactionDbOptions = TransactionDBOptions()
                try {
                    val columnFamilyDescriptors = getColumnFamilyDescriptors(location)
                    val columnFamilies = mutableListOf<ColumnFamilyHandle>()
                    rocksDB = TransactionDB.open(
                        dbOptions,
                        transactionDbOptions,
                        location,
                        columnFamilyDescriptors,
                        columnFamilies
                    ) ?: error("Failed to open rocksDB at location: $location")
                    try {
                        columnFamilyDescriptors.zip(columnFamilies).forEach { (descriptor, column) ->
                            columnFamiliesMap[descriptor.name.toList()] = column
                        }
                        sizesColumnFamily = getOrCreateColumnFamily(
                            "org.opentaint.ir.impl.storage.kv.rocks.RocksKeyValueStorage.##column##family##sizes##"
                        )
                    } catch(e: Throwable) {
                        columnFamilies.forEach { it.close() }
                        rocksDB.close()
                        throw e
                    }
                } catch (e: Throwable) {
                    transactionDbOptions.close()
                    throw e
                }
            } catch (e: Throwable) {
                dbOptions.close()
                throw e
            }
        } catch (e: Throwable) {
            columnFamilyOptions.close()
            throw e
        }
    }

    override fun close() {
        columnFamiliesMap.values.forEach { it.close() }
        rocksDB.close()
        transactionDbOptions.close()
        dbOptions.close()
        columnFamilyOptions.close()
    }

    private fun getColumnFamilyDescriptors(location: String): List<ColumnFamilyDescriptor> {
        return Options(dbOptions, columnFamilyOptions).use { options ->
            RocksDB.listColumnFamilies(options, location).map { name -> ColumnFamilyDescriptor(name) }
        }.takeIf { it.isNotEmpty() }
            ?: listOf(ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY))
    }

    override fun beginTransaction(): Transaction = beginTransaction(isReadonly = false)

    override fun beginReadonlyTransaction(): Transaction = beginTransaction(isReadonly = true)

    private fun beginTransaction(isReadonly: Boolean): Transaction {
        // TODO check documentation to see if RocksDB supports marking readonly transaction
        val writeOptions = WriteOptions()
        try {
            val rocksTxn = rocksDB.beginTransaction(writeOptions)
            try {
                return RocksTransactionImpl.create(
                    this,
                    rocksTxn,
                    isReadonly = isReadonly
                )
            } catch (e: Throwable) {
                rocksTxn.close()
                throw e
            }
        } catch (e: Throwable) {
            writeOptions.close()
            throw e
        }
    }

    override fun getNamedMapOrNull(name: String): RocksNamedMap? {
        val nameBytes = BuiltInBindingProvider.getBinding(String::class.java).getBytes(name)
        val columnFamilyHandle = columnFamiliesMap[nameBytes.toList()]
        return when {
            columnFamilyHandle == null -> null
            isMapWithKeyDuplicates?.invoke(name) == true -> DuplicateRocksNamedMap(columnFamilyHandle)
            else -> NoDuplicateRocksNamedMap(columnFamilyHandle)
        }
    }

    override fun getOrCreateNamedMap(name: String): RocksNamedMap {
        val columnFamilyHandle = getOrCreateColumnFamily(name)
        return when {
            isMapWithKeyDuplicates?.invoke(name) == true -> DuplicateRocksNamedMap(columnFamilyHandle)
            else -> NoDuplicateRocksNamedMap(columnFamilyHandle)
        }
    }

    private fun getOrCreateColumnFamily(name: ByteArray): ColumnFamilyHandle {
        return columnFamiliesMap[name.toList()] ?: synchronized(this) {
            columnFamiliesMap.getOrPut(name.toList()) {
                rocksDB.createColumnFamily(ColumnFamilyDescriptor(name, columnFamilyOptions))
            }
        }
    }

    private fun getOrCreateColumnFamily(name: String): ColumnFamilyHandle =
        getOrCreateColumnFamily(BuiltInBindingProvider.getBinding(String::class.java).getBytes(name))
}
