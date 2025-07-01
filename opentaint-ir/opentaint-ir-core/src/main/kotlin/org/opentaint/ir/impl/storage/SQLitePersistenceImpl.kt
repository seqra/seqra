package org.opentaint.ir.impl.storage

import mu.KotlinLogging
import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRDBContext
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.impl.JIRDBSymbolsInternerImpl
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.storage.ers.BuiltInBindingProvider
import org.opentaint.ir.impl.storage.ers.sql.SqlEntityRelationshipStorage
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.Condition
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val defaultBatchSize: Int get() = System.getProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "100").toInt()

class SQLitePersistenceImpl(
    javaRuntime: JavaRuntime,
    clearOnStart: Boolean,
    val location: String?,
) : AbstractJIRDbPersistence(javaRuntime) {
    private val dataSource = configuredSQLiteDataSource(location)
    private val connection: Connection = dataSource.connection
    internal val jooq = DSL.using(connection, SQLDialect.SQLITE, Settings().withExecuteLogging(false))
    private val lock = ReentrantLock()
    private val persistenceService = SQLitePersistenceService(this)
    override val ers: EntityRelationshipStorage = SqlEntityRelationshipStorage(dataSource, BuiltInBindingProvider)

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        if (clearOnStart || !runtimeProcessed) {
            jooq.executeQueriesFrom("sqlite/drop-schema.sql")
        }
        jooq.executeQueriesFrom("sqlite/create-schema.sql")
    }

    override val symbolInterner = JIRDBSymbolsInternerImpl().apply { setup(this@SQLitePersistenceImpl) }

    override fun <T> read(action: (JIRDBContext) -> T): T {
        return action(toJIRDBContext(jooq))
    }

    override fun <T> write(action: (JIRDBContext) -> T): T = lock.withLock {
        action(toJIRDBContext(jooq))
    }

    override fun close() {
        try {
            ers.close()
            connection.close()
            super.close()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to close SQL persistence" }
        }
    }

    override fun createIndexes() {
        jooq.executeQueries("add-indexes.sql".sqlScript())
    }

    override fun setup() {
        persistenceService.setup()
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        val allClasses = classes.map { it.info }
        persistenceService.persist(location, allClasses)
    }

    override fun findClassSourceByName(cp: JIRClasspath, fullName: String): ClassSource? {
        val symbolId = findSymbolId(fullName)
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause), single = true).firstOrNull()
    }

    override fun findClassSources(db: JIRDatabase, location: RegisteredLocation): List<ClassSource> {
        return db.classSources(CLASSES.LOCATION_ID.eq(location.id))
    }

    override fun findClassSources(cp: JIRClasspath, fullName: String): List<ClassSource> {
        val symbolId = findSymbolId(fullName)
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause))
    }

    private val JIRClasspath.clause: Condition
        get() {
            val ids = registeredLocations.map { it.id }
            return CLASSES.LOCATION_ID.`in`(ids)
        }

    private fun JIRDatabase.classSources(clause: Condition, single: Boolean = false): List<ClassSource> = read { context ->
        val jooq = context.dslContext
        val classesQuery =
            jooq.select(CLASSES.LOCATION_ID, CLASSES.ID, CLASSES.BYTECODE, SYMBOLS.NAME).from(CLASSES).join(SYMBOLS)
                .on(CLASSES.NAME.eq(SYMBOLS.ID)).where(clause)
        val classes = when {
            single -> listOfNotNull(classesQuery.fetchAny())
            else -> classesQuery.fetch()
        }
        classes.map { (locationId, classId, bytecode, name) ->
            PersistenceClassSource(
                db = this, className = name!!, classId = classId!!, locationId = locationId!!, cachedByteCode = bytecode
            )
        }
    }
}

fun String.sqlScript(): String {
    return SQLitePersistenceImpl::class.java.classLoader.getResourceAsStream("sqlite/${this}")?.reader()?.readText()
        ?: throw IllegalStateException("no sql script for sqlite/${this} found")
}
