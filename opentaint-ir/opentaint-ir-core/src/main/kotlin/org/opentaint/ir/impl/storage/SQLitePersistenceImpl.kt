
package org.opentaint.ir.impl.storage

import mu.KLogging
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRInternalSignal
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.vfs.PersistentByteCodeLocation
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock

class SQLitePersistenceImpl(
    private val javaRuntime: JavaRuntime,
    private val featuresRegistry: FeaturesRegistry,
    location: String? = null,
    private val clearOnStart: Boolean
) : JIRDBPersistence, Closeable {

    companion object : KLogging() {
        private const val cachesPrefix = "org.opentaint.ir.persistence.caches"

        private val locationsCacheSize = Integer.getInteger("$cachesPrefix.locations", 1_000).toLong()
        private val byteCodeCacheSize = Integer.getInteger("$cachesPrefix.bytecode", 10_000).toLong()
        private val symbolsCacheSize = Integer.getInteger("$cachesPrefix.symbols", 100_000).toLong()
    }

    private val lock = ReentrantLock()

    private val dataSource: DataSource
    private var keepAliveConnection: Connection? = null
    private val persistenceService = PersistenceService(this)
    val jooq: DSLContext

    private val locationsCache = cacheOf<Long, RegisteredLocation>(locationsCacheSize)
    private val byteCodeCache = cacheOf<Long, ByteArray>(byteCodeCacheSize)
    private val symbolsCache = cacheOf<Long, String>(symbolsCacheSize)

    init {
        val config = SQLiteConfig().also {
            it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            it.setJournalMode(SQLiteConfig.JournalMode.OFF)
            it.setPageSize(32_768)
            it.setCacheSize(-8_000)
        }
        if (location == null) {
            val url =
                "jdbc:sqlite:file:jirdb-${UUID.randomUUID()}?mode=memory&cache=shared&rewriteBatchedStatements=true"
            dataSource = SQLiteDataSource(config).also {
                it.url = url
            }
            keepAliveConnection = dataSource.connection //DriverManager.getConnection(url)
        } else {
            val url = "jdbc:sqlite:file:$location?rewriteBatchedStatements=true&useServerPrepStmts=false"
            dataSource = SQLiteDataSource(config).also {
                it.url = url
            }
        }
        jooq = DSL.using(dataSource, SQLDialect.SQLITE, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart) {
                jooq.executeQueriesFrom("jirdb-drop-schema.sql")
            }
            jooq.executeQueriesFrom("jirdb-create-schema.sql")
        }
    }

    override fun setup() {
        write {
            featuresRegistry.broadcast(JIRInternalSignal.BeforeIndexing(clearOnStart))
        }
        persistenceService.setup()
    }

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

    override fun newSymbolInterner() = persistenceService.newSymbolInterner()

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            jooq.select(CLASSES.BYTECODE).from(CLASSES)
                .where(CLASSES.ID.eq(classId)).fetchAny()?.value1()
                ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
    }

    override fun <T> write(action: (DSLContext) -> T): T {
        return lock.withLock {
            action(jooq)
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

    override fun findClassSourceByName(
        cp: JIRClasspath,
        locations: List<RegisteredLocation>,
        fullName: String
    ): ClassSource? {
        val ids = locations.map { it.id }
        val symbolId = findSymbolId(fullName) ?: return null
        val found = jooq.select(CLASSES.LOCATION_ID, CLASSES.BYTECODE).from(CLASSES)
            .where(CLASSES.NAME.eq(symbolId).and(CLASSES.LOCATION_ID.`in`(ids)))
            .fetchAny() ?: return null
        val locationId = found.component1()!!
        val byteCode = found.component2()!!
        return ClassSourceImpl(
            location = PersistentByteCodeLocation(cp, locationId),
            className = fullName,
            byteCode = byteCode
        )
    }

    override fun findClassSources(location: RegisteredLocation): List<ClassSource> {
        val classes = jooq.select(CLASSES.LOCATION_ID, CLASSES.BYTECODE, SYMBOLS.NAME).from(CLASSES)
            .join(SYMBOLS).on(CLASSES.NAME.eq(SYMBOLS.ID))
            .where(CLASSES.LOCATION_ID.eq(location.id))
            .fetch()
        return classes.map {
            ClassSourceImpl(
                location = location,
                className = it.component3()!!,
                byteCode = it.component2()!!
            )
        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        val allClasses = classes.map { it.info }
        persistenceService.persist(location, allClasses)
    }

    override fun close() {
        try {
            keepAliveConnection?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

}