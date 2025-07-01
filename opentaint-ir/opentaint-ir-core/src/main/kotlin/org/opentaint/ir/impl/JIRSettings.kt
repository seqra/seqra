package org.opentaint.ir.impl

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.jvm.Hook
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRPersistenceImplSettings
import org.opentaint.ir.api.jvm.JIRPersistenceSettings
import org.opentaint.ir.api.jvm.storage.ers.EmptyErsSettings
import org.opentaint.ir.api.jvm.storage.ers.ErsSettings
import org.opentaint.ir.impl.storage.SQLITE_DATABASE_PERSISTENCE_SPI
import org.opentaint.ir.impl.storage.ers.ERS_DATABASE_PERSISTENCE_SPI
import org.opentaint.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.opentaint.ir.impl.storage.ers.ram.RAM_ERS_SPI
import org.opentaint.ir.impl.storage.ers.sql.SQL_ERS_SPI
import org.opentaint.ir.impl.storage.kv.lmdb.LMDB_KEY_VALUE_STORAGE_SPI
import org.opentaint.ir.impl.storage.kv.rocks.ROCKS_KEY_VALUE_STORAGE_SPI
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI
import java.io.File
import java.time.Duration

/**
 * Settings for database
 */
class JIRSettings {

    /** watch file system changes delay */
    var watchFileSystemDelay: Int? = null
        private set

    /** persisted  */
    val persistenceId: String?
        get() = persistenceSettings.persistenceId

    val persistenceLocation: String?
        get() = persistenceSettings.persistenceLocation

    val persistenceClearOnStart: Boolean?
        get() = persistenceSettings.persistenceClearOnStart

    var persistenceSettings: JIRPersistenceSettings = JIRPersistenceSettings()

    var keepLocalVariableNames: Boolean = false
        private set

    /** jar files which should be loaded right after database is created */
    var predefinedDirOrJars: List<File> = persistentListOf()
        private set

    var cacheSettings: JIRCacheSettings = JIRCacheSettings()
        private set

    var byteCodeSettings: JIRByteCodeCache = JIRByteCodeCache()
        private set

    var hooks: MutableList<(JIRDatabase) -> Hook> = arrayListOf()
        private set

    /** mandatory setting for java runtime location */
    lateinit var jre: File

    /** features to add */
    var features: List<JIRFeature<*, *>> = emptyList()
        private set

    init {
        useProcessJavaRuntime()
    }

    /**
     * builder for persistent settings
     * @param location - file for db location
     * @param clearOnStart -if true old data from this folder will be dropped
     */
    @JvmOverloads
    fun persistent(
        location: String,
        clearOnStart: Boolean = false,
        implSettings: JIRPersistenceImplSettings = JIRSQLitePersistenceSettings
    ) = apply {
        persistenceSettings.persistenceLocation = location
        persistenceSettings.persistenceClearOnStart = clearOnStart
        persistenceSettings.implSettings = implSettings
    }

    fun persistenceImpl(persistenceImplSettings: JIRPersistenceImplSettings) = apply {
        persistenceSettings.implSettings = persistenceImplSettings
    }

    fun caching(settings: JIRCacheSettings.() -> Unit) = apply {
        cacheSettings = JIRCacheSettings().also { it.settings() }
    }

    fun caching(settings: JIRCacheSettings) = apply {
        cacheSettings = settings
    }

    fun bytecodeCaching(byteCodeCache: JIRByteCodeCache) = apply {
        this.byteCodeSettings = byteCodeCache
    }

    fun loadByteCode(files: List<File>) = apply {
        predefinedDirOrJars = (predefinedDirOrJars + files).toPersistentList()
    }

    fun keepLocalVariableNames() {
        keepLocalVariableNames = true
    }

    /**
     * builder for watching file system changes
     * @param delay - delay between syncs
     */
    @JvmOverloads
    fun watchFileSystem(delay: Int = 10_000) = apply {
        watchFileSystemDelay = delay
    }

    /** builder for hooks */
    fun withHook(hook: (JIRDatabase) -> Hook) = apply {
        hooks += hook
    }

    /**
     * use java from JAVA_HOME env variable
     */
    fun useJavaHomeRuntime() = apply {
        val javaHome = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * use java from current system process
     */
    fun useProcessJavaRuntime() = apply {
        val javaHome = System.getProperty("java.home") ?: throw IllegalArgumentException("java.home is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * use java from current system process
     */
    fun useJavaRuntime(runtime: File) = apply {
        jre = runtime.absolutePath.asValidJRE()
    }

    /**
     * install additional indexes
     */
    fun installFeatures(vararg feature: JIRFeature<*, *>) = apply {
        features = features + feature.toList()
    }

    private fun String.asValidJRE(): File {
        val file = File(this)
        if (!file.exists()) {
            throw IllegalArgumentException("$this points to folder that do not exists")
        }
        return file
    }
}

class JIRByteCodeCache(val prefixes: List<String> = persistentListOf("java.", "javax.", "kotlinx.", "kotlin."))

data class JIRCacheSegmentSettings(
    val valueStoreType: ValueStoreType = ValueStoreType.STRONG,
    val maxSize: Long = 10_000,
    val expiration: Duration = Duration.ofMinutes(1)
)

enum class ValueStoreType { WEAK, SOFT, STRONG }

class JIRCacheSettings {
    var classes: JIRCacheSegmentSettings = JIRCacheSegmentSettings()
    var types: JIRCacheSegmentSettings = JIRCacheSegmentSettings()
    var rawInstLists: JIRCacheSegmentSettings = JIRCacheSegmentSettings()
    var instLists: JIRCacheSegmentSettings = JIRCacheSegmentSettings()
    var flowGraphs: JIRCacheSegmentSettings = JIRCacheSegmentSettings()

    @JvmOverloads
    fun classes(maxSize: Long, expiration: Duration, valueStoreType: ValueStoreType = ValueStoreType.STRONG) = apply {
        classes = JIRCacheSegmentSettings(maxSize = maxSize, expiration = expiration, valueStoreType = valueStoreType)
    }

    @JvmOverloads
    fun types(maxSize: Long, expiration: Duration, valueStoreType: ValueStoreType = ValueStoreType.STRONG) = apply {
        types = JIRCacheSegmentSettings(maxSize = maxSize, expiration = expiration, valueStoreType = valueStoreType)
    }

    @JvmOverloads
    fun rawInstLists(maxSize: Long, expiration: Duration, valueStoreType: ValueStoreType = ValueStoreType.STRONG) =
        apply {
            rawInstLists =
                JIRCacheSegmentSettings(maxSize = maxSize, expiration = expiration, valueStoreType = valueStoreType)
        }

    @JvmOverloads
    fun instLists(maxSize: Long, expiration: Duration, valueStoreType: ValueStoreType = ValueStoreType.STRONG) = apply {
        instLists = JIRCacheSegmentSettings(maxSize = maxSize, expiration = expiration, valueStoreType = valueStoreType)
    }

    @JvmOverloads
    fun flowGraphs(maxSize: Long, expiration: Duration, valueStoreType: ValueStoreType = ValueStoreType.STRONG) =
        apply {
            flowGraphs =
                JIRCacheSegmentSettings(maxSize = maxSize, expiration = expiration, valueStoreType = valueStoreType)
        }

}

object JIRSQLitePersistenceSettings : JIRPersistenceImplSettings {
    override val persistenceId: String
        get() = SQLITE_DATABASE_PERSISTENCE_SPI
}

open class JIRErsSettings(
    val ersId: String,
    val ersSettings: ErsSettings = EmptyErsSettings
) : JIRPersistenceImplSettings {

    override val persistenceId: String
        get() = ERS_DATABASE_PERSISTENCE_SPI
}

object JIRRamErsSettings : JIRErsSettings(RAM_ERS_SPI)

object JIRSqlErsSettings : JIRErsSettings(SQL_ERS_SPI)

/**
 * Id of pluggable K/V storage being passed for [org.opentaint.ir.impl.storage.ers.kv.KVEntityRelationshipStorageSPI].
 */
open class JIRKvErsSettings(val kvId: String) : ErsSettings

object JIRXodusKvErsSettings : JIRErsSettings(KV_ERS_SPI, JIRKvErsSettings(XODUS_KEY_VALUE_STORAGE_SPI))

object JIRRocksKvErsSettings : JIRErsSettings(KV_ERS_SPI, JIRKvErsSettings(ROCKS_KEY_VALUE_STORAGE_SPI))

// by default, mapSize is 1Gb
class JIRLmdbErsSettings(val mapSize: Long = 0x40_00_00_00) : JIRKvErsSettings(LMDB_KEY_VALUE_STORAGE_SPI)

object JIRLmdbKvErsSettings : JIRErsSettings(KV_ERS_SPI, JIRLmdbErsSettings()) {

    fun withMapSize(mapSize: Long): JIRErsSettings {
        return JIRErsSettings(KV_ERS_SPI, JIRLmdbErsSettings(mapSize))
    }
}