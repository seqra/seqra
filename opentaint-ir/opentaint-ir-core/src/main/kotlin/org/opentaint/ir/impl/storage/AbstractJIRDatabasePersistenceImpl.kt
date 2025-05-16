package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRInternalSignal
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.vfs.PersistentByteCodeLocation
import org.jooq.Condition
import org.jooq.DSLContext
import java.io.File

val defaultBatchSize: Int get() = System.getProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "100").toInt()

abstract class AbstractJIRDatabasePersistenceImpl(
    private val javaRuntime: JavaRuntime,
    private val featuresRegistry: FeaturesRegistry,
    private val clearOnStart: Boolean,
) : JIRDatabasePersistence {

    companion object {
        private const val cachesPrefix = "org.opentaint.opentaint-ir.persistence.caches"
        private val locationsCacheSize = Integer.getInteger("$cachesPrefix.locations", 1_000).toLong()
        private val byteCodeCacheSize = Integer.getInteger("$cachesPrefix.bytecode", 10_000).toLong()
        private val symbolsCacheSize = Integer.getInteger("$cachesPrefix.symbols", 100_000).toLong()
    }

    private val persistenceService = PersistenceService(this)

    abstract val jooq: DSLContext

    private val locationsCache = cacheOf<Long, RegisteredLocation>(locationsCacheSize)
    private val byteCodeCache = cacheOf<Long, ByteArray>(byteCodeCacheSize)
    private val symbolsCache = cacheOf<Long, String>(symbolsCacheSize)

    override val locations: List<JIRByteCodeLocation>
        get() {
            return jooq.selectFrom(BYTECODELOCATIONS).fetch().mapNotNull {
                try {
                    File(it.path!!).asByteCodeLocation(javaRuntime.version, isRuntime = it.runtime!!)
                } catch (e: Exception) {
                    null
                }
            }.toList()
        }

    override val symbolInterner by lazy {
        JIRDBSymbolsInternerImpl(jooq).also { it.setup() }
    }

    override fun setup() {
        write {
            featuresRegistry.broadcast(JIRInternalSignal.BeforeIndexing(clearOnStart))
        }
        persistenceService.setup()
    }

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            jooq.select(CLASSES.BYTECODE).from(CLASSES)
                .where(CLASSES.ID.eq(classId)).fetchAny()?.value1()
                ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
    }

    override fun <T> read(action: (DSLContext) -> T): T {
        return action(jooq)
    }

    override fun findSymbolId(symbol: String): Long? {
        return persistenceService.findSymbolId(symbol)
    }

    override fun findSymbolName(symbolId: Long): String {
        return symbolsCache.get(symbolId) {
            persistenceService.findSymbolName(symbolId)
        }
    }

    override fun findLocation(locationId: Long): RegisteredLocation {
        return locationsCache.get(locationId) {
            val record = jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(locationId))
                ?: throw IllegalArgumentException("location not found by id $locationId")
            PersistentByteCodeLocation(this, runtimeVersion = javaRuntime.version, locationId, record, null)
        }
    }

    override fun findClassSourceByName(cp: JIRClasspath, fullName: String): ClassSource? {
        val symbolId = findSymbolId(fullName) ?: return null
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause), single = true).firstOrNull()
    }

    override fun findClassSources(db: JIRDatabase, location: RegisteredLocation): List<ClassSource> {
        return db.classSources(CLASSES.LOCATION_ID.eq(location.id))
    }

    override fun findClassSources(cp: JIRClasspath, fullName: String): List<ClassSource> {
        val symbolId = findSymbolId(fullName) ?: return emptyList()
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause))
    }

    private val JIRClasspath.clause: Condition
        get() {
            val ids = registeredLocations.map { it.id }
            return CLASSES.LOCATION_ID.`in`(ids)
        }

    private fun JIRDatabase.classSources(clause: Condition, single: Boolean = false): List<ClassSource> {
        val classesQuery = jooq.select(CLASSES.LOCATION_ID, CLASSES.ID, CLASSES.BYTECODE, SYMBOLS.NAME).from(CLASSES)
            .join(SYMBOLS).on(CLASSES.NAME.eq(SYMBOLS.ID))
            .where(clause)
        val classes = when {
            single -> listOfNotNull(classesQuery.fetchAny())
            else -> classesQuery.fetch()
        }
        return classes.map { (locationId, classId, bytecode, name) ->
            PersistenceClassSource(
                db = this,
                className = name!!,
                classId = classId!!,
                locationId = locationId!!,
                cachedByteCode = bytecode
            )
        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        val allClasses = classes.map { it.info }
        persistenceService.persist(location, allClasses)
    }

    override fun close() {
        locationsCache.invalidateAll()
        symbolsCache.invalidateAll()
        byteCodeCache.invalidateAll()
        symbolInterner.setup()
    }
}
