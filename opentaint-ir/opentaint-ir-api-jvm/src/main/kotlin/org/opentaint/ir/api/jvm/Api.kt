package org.opentaint.ir.api.jvm

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.Transaction
import org.jooq.DSLContext
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

enum class LocationType {
    RUNTIME,
    APP
}

interface ClassSource {
    val className: String
    val byteCode: ByteArray
    val location: RegisteredLocation
}

/**
 * field usage mode
 */
enum class FieldUsageMode {

    /** search for reads */
    READ,

    /** search for writes */
    WRITE
}

interface JavaVersion {
    val majorVersion: Int
}

/**
 * Compilation database
 *
 * `close` method should be called when database is not needed anymore
 */
@JvmDefaultWithoutCompatibility
interface JIRDatabase : Closeable {

    /**
     * Unique id of the database. Databases built against same locations have the same id.
     * Id changes if locations change. Databases with the same id can be considered as equal.
     */
    val id: String
    val locations: List<RegisteredLocation>
    val persistence: JIRDatabasePersistence

    val runtimeVersion: JavaVersion

    /**
     * create classpath instance
     *
     * @param dirOrJars list of byte-code resources to be processed and included in classpath
     * @return new classpath instance associated with specified byte-code locations
     */
    suspend fun classpath(dirOrJars: List<File>, features: List<JIRClasspathFeature>?): JIRClasspath
    suspend fun classpath(dirOrJars: List<File>): JIRClasspath = classpath(dirOrJars, null)
    fun asyncClasspath(dirOrJars: List<File>) = GlobalScope.future { classpath(dirOrJars) }
    fun asyncClasspath(dirOrJars: List<File>, features: List<JIRClasspathFeature>?) =
        GlobalScope.future { classpath(dirOrJars, features) }

    /**
     * process and index single byte-code resource
     * @param dirOrJar build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJar: File): JIRDatabase
    fun asyncLoad(dirOrJar: File) = GlobalScope.future { load(dirOrJar) }

    /**
     * process and index byte-code resources
     * @param dirOrJars list of build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJars: List<File>): JIRDatabase
    fun asyncLoad(dirOrJars: List<File>) = GlobalScope.future { load(dirOrJars) }

    /**
     * load locations
     * @param locations locations to load
     * @return current database instance
     */
    suspend fun loadLocations(locations: List<JIRByteCodeLocation>): JIRDatabase
    fun asyncLocations(locations: List<JIRByteCodeLocation>) = GlobalScope.future { loadLocations(locations) }

    /**
     * explicitly refreshes the state of resources from file-system.
     * That means that any new classpath created after refresh is done will
     * reference fresh byte-code from file-system. While any classpath created
     * before `refresh` will still reference byte-code which is outdated
     * according to file-system
     */
    suspend fun refresh()
    fun asyncRefresh() = GlobalScope.future { refresh() }

    /**
     * rebuilds features data (indexes)
     */
    suspend fun rebuildFeatures()
    fun asyncRebuildFeatures() = GlobalScope.future { rebuildFeatures() }

    /**
     * watch file system for changes and refreshes the state of database in case loaded resources and resources from
     * file systems are different.
     *
     * @return current database instance
     */
    fun watchFileSystemChanges(): JIRDatabase

    /**
     * await background jobs
     */
    suspend fun awaitBackgroundJobs()
    suspend fun cancelBackgroundJobs()
    fun asyncAwaitBackgroundJobs() = GlobalScope.future { awaitBackgroundJobs() }

    /**
     * Sets this database's internal state to immutable if corresponding backend supports this operation.
     * If it does, any write operation is no longer possible.
     * The method can be used in order to "fix" current snapshot of the model.
     * Generally, there is no way to switch the database back to mutable.
     */
    suspend fun setImmutable()

    fun isInstalled(feature: JIRFeature<*, *>): Boolean = features.contains(feature)

    val features: List<JIRFeature<*, *>>
}

interface JIRDatabasePersistence : Closeable {

    val locations: List<JIRByteCodeLocation>

    val ers: EntityRelationshipStorage

    fun setup()

    /**
     * Try to load code model by database id
     */
    fun tryLoad(databaseId: String): Boolean = false

    fun <T> write(action: (JIRDBContext) -> T): T
    fun <T> read(action: (JIRDBContext) -> T): T

    fun persist(location: RegisteredLocation, classes: List<ClassSource>)
    fun findSymbolId(symbol: String): Long
    fun findSymbolName(symbolId: Long): String
    fun findLocation(locationId: Long): RegisteredLocation

    val symbolInterner: JIRDBSymbolsInterner
    fun findBytecode(classId: Long): ByteArray

    fun findClassSourceByName(cp: JIRClasspath, fullName: String): ClassSource?
    fun findClassSources(db: JIRDatabase, location: RegisteredLocation): List<ClassSource>
    fun findClassSources(cp: JIRClasspath, fullName: String): List<ClassSource>

    fun createIndexes() {}

    /**
     * Sets this persistence's internal state to immutable if corresponding backend supports this operation.
     * If it does, any write operation is no longer possible.
     * The method can be used in order to "fix" current snapshot of the model.
     * Generally, there is no way to switch the persistence back to mutable.
     */
    fun setImmutable(databaseId: String) {}
}

/**
 * Abstract database access context contains several named context objects.
 * Normally, there should be implemented specific context classes for each persistence
 * implementation with its specific context objects.
 *
 * For SQLite persistence, JIRDBContext should contain [DSLContext] object and may contain [Connection] object.
 *
 * For [EntityRelationshipStorage] persistence, JIRDBContext should contain [Transaction] object.
 */
@Suppress("UNCHECKED_CAST")
class JIRDBContext private constructor() {

    private val contextObjects = ConcurrentHashMap<ContextProperty<*>, Any>()

    fun <T : Any> setContextObject(contextKey: ContextProperty<T>, contextObject: T) = apply {
        contextObjects[contextKey] = contextObject
    }

    fun <T : Any> getContextObject(property: ContextProperty<T>): T =
        contextObjects[property] as? T?
            ?: throw NullPointerException("JIRDBContext doesn't contain context object $property")

    fun <T : Any> hasContextObject(property: ContextProperty<T>): Boolean = contextObjects.containsKey(property)

    companion object {
        fun <T : Any> of(contextKey: ContextProperty<T>, contextObject: T): JIRDBContext {
            return JIRDBContext().apply { this(contextKey, contextObject) }
        }

        fun empty() = JIRDBContext()
    }
}

interface ContextProperty<T : Any>

operator fun <T : Any> JIRDBContext.invoke(contextKey: ContextProperty<T>, contextObject: T) =
    setContextObject(contextKey, contextObject)

interface RegisteredLocation {
    val jIRLocation: JIRByteCodeLocation?
    val id: Long
    val path: String
    val isRuntime: Boolean
}

interface JIRDBSymbolsInterner {
    fun findOrNew(symbol: String): Long
    fun findSymbolName(symbolId: Long): String?
    fun flush(context: JIRDBContext)
}
