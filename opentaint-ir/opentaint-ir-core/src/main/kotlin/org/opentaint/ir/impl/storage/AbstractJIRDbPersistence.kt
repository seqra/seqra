package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.storage.ers.getEntityOrNull
import org.opentaint.ir.impl.JIRDBSymbolsInternerImpl
import org.opentaint.ir.impl.asSymbolId
import org.opentaint.ir.impl.caches.PluggableCache
import org.opentaint.ir.impl.caches.PluggableCacheProvider
import org.opentaint.ir.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.logger
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import java.io.File
import java.time.Duration

abstract class AbstractJIRDbPersistence(
    private val javaRuntime: JavaRuntime,
) : JIRDatabasePersistence {

    companion object {
        private const val CACHE_PREFIX = "org.opentaint.ir.persistence.caches"
        private val locationsCacheSize = Integer.getInteger("$CACHE_PREFIX.locations", 1_000)
        private val byteCodeCacheSize = Integer.getInteger("$CACHE_PREFIX.bytecode", 10_000)
        private val cacheProvider = PluggableCacheProvider.getProvider(
            System.getProperty("$CACHE_PREFIX.cacheProviderId", XODUS_CACHE_PROVIDER_ID)
        )

        fun <KEY : Any, VALUE : Any> cacheOf(size: Int): PluggableCache<KEY, VALUE> {
            return cacheProvider.newCache {
                maximumSize = size
                expirationDuration = Duration.ofSeconds(
                    Integer.getInteger("$CACHE_PREFIX.expirationDurationSec", 10).toLong()
                )
            }
        }
    }

    private val locationsCache = cacheOf<Long, RegisteredLocation>(locationsCacheSize)
    private val byteCodeCache = cacheOf<Long, ByteArray>(byteCodeCacheSize)

    override val locations: List<JIRByteCodeLocation>
        get() {
            return read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.selectFrom(BYTECODELOCATIONS).fetch().map {
                            PersistentByteCodeLocationData.fromSqlRecord(it)
                        }
                    },
                    noSqlAction = { txn ->
                        txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).map {
                            PersistentByteCodeLocationData.fromErsEntity(it)
                        }.toList()
                    }
                ).mapNotNull {
                    try {
                        File(it.path).asByteCodeLocation(javaRuntime.version, isRuntime = it.runtime)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

    abstract override val symbolInterner: JIRDBSymbolsInternerImpl

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.select(CLASSES.BYTECODE).from(CLASSES).where(CLASSES.ID.eq(classId)).fetchAny()?.value1()
                    },
                    noSqlAction = { txn ->
                        txn.getEntityOrNull("Class", classId)?.getRawBlob("bytecode")
                    }
                )
            } ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
    }

    override fun findSymbolId(symbol: String): Long {
        return symbol.asSymbolId(symbolInterner)
    }

    override fun findSymbolName(symbolId: Long): String {
        return symbolInterner.findSymbolName(symbolId)!!
    }

    override fun findLocation(locationId: Long): RegisteredLocation {
        return locationsCache.get(locationId) {
            val locationData = read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(locationId))
                            ?.let { PersistentByteCodeLocationData.fromSqlRecord(it) }
                    },
                    noSqlAction = { txn ->
                        txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, locationId)
                            ?.let { PersistentByteCodeLocationData.fromErsEntity(it) }
                    }
                ) ?: throw IllegalArgumentException("location not found by id $locationId")
            }
            PersistentByteCodeLocation(
                persistence = this,
                runtimeVersion = javaRuntime.version,
                id = locationId,
                cachedData = locationData,
                cachedLocation = null
            )
        }
    }

    override fun close() {
        try {
            symbolInterner.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    protected val runtimeProcessed: Boolean
        get() {
            try {
                return read { context ->
                    context.execute(
                        sqlAction = { jooq ->
                            val hasBytecodeLocations = jooq.meta().tables.any { it.name.equals(BYTECODELOCATIONS.name, true) }
                            if (!hasBytecodeLocations) {
                                return@execute false
                            }

                            val count = jooq.fetchCount(
                                BYTECODELOCATIONS,
                                BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal)
                                    .and(BYTECODELOCATIONS.RUNTIME.isTrue)
                            )
                            count == 0
                        },
                        noSqlAction = { txn ->
                            txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).none {
                                it.get<Boolean>(BytecodeLocationEntity.IS_RUNTIME) == true &&
                                        it.get<Int>(BytecodeLocationEntity.STATE) != LocationState.PROCESSED.ordinal
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                logger.warn("can't check that runtime libraries is processed with", e)
                return false
            }
        }
}
