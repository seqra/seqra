package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.caches.PluggableCache
import org.opentaint.ir.api.caches.PluggableCacheProvider
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.storage.ers.getEntityOrNull
import org.opentaint.ir.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.JavaRuntimeModuleLocation
import org.opentaint.ir.impl.fs.dirOrJarAsBytecodeLocation
import org.opentaint.ir.impl.storage.ers.bytecode
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
                    sqlAction = {
                        context.dslContext.selectFrom(BYTECODELOCATIONS).fetch().map {
                            PersistentByteCodeLocationData.fromSqlRecord(it)
                        }
                    },
                    noSqlAction = {
                        context.txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).map {
                            PersistentByteCodeLocationData.fromErsEntity(it)
                        }.toList()
                    }
                ).mapNotNull {
                    try {
                        if (it.runtime && JavaRuntimeModuleLocation.isModuleLocation(it.path)) {
                            return@mapNotNull listOf(JavaRuntimeModuleLocation.fromPath(it.path))
                        }

                        val file = File(it.path)
                        if (!file.exists()) return@mapNotNull null

                        file.dirOrJarAsBytecodeLocation(javaRuntime.version, it.runtime)
                    } catch (_: Exception) {
                        null
                    }
                }.flatten().distinct()
            }
        }

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            read { context ->
                context.execute(
                    sqlAction = {
                        context.dslContext.select(CLASSES.BYTECODE).from(CLASSES).where(CLASSES.ID.eq(classId))
                            .fetchAny()?.value1()
                    },
                    noSqlAction = {
                        context.txn.getEntityOrNull("Class", classId).bytecode()
                    }
                )
            } ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
    }

    override fun findSymbolId(symbol: String): Long {
        return symbol.asSymbolId()
    }

    override fun findSymbolName(symbolId: Long): String {
        return symbolInterner.findSymbolName(symbolId)!!
    }

    override fun findLocation(locationId: Long): RegisteredLocation {
        return locationsCache.get(locationId) {
            val locationData = read { context ->
                context.execute(
                    sqlAction = {
                        context.dslContext.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(locationId))
                            ?.let { PersistentByteCodeLocationData.fromSqlRecord(it) }
                    },
                    noSqlAction = {
                        context.txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, locationId)
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
        } catch (_: Exception) {
            // ignore
        }
    }

    protected val runtimeProcessed: Boolean
        get() {
            return read { context ->
                context.execute(
                    sqlAction = {
                        val jooq = context.dslContext
                        val hasBytecodeLocations =
                            jooq.meta().tables.any { it.name.equals(BYTECODELOCATIONS.name, true) }
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
                    noSqlAction = {
                        context.txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).none {
                            it.get<Boolean>(BytecodeLocationEntity.IS_RUNTIME) == true &&
                                    it.get<Int>(BytecodeLocationEntity.STATE) != LocationState.PROCESSED.ordinal
                        }
                    }
                )
            }
        }
}
