package org.opentaint.ir.impl

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.Hook
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.api.JIRDatabasePersistence
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.storage.PostgresPersistenceImpl
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl
import java.io.File

/**
 * Settings for database
 */
class JIRSettings {

    /** watch file system changes delay */
    var watchFileSystemDelay: Int? = null
        private set

    /** persisted  */
    var persistentType: JIRPersistenceType? = null
        private set

    var persistentLocation: String? = null
        private set

    var persistentClearOnStart: Boolean? = null

    /** jar files which should be loaded right after database is created */
    var predefinedDirOrJars: List<File> = persistentListOf()
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
    fun persistent(location: String, clearOnStart: Boolean = false, type: JIRPersistenceType = PredefinedPersistenceType.SQLITE) = apply {
        persistentLocation = location
        persistentClearOnStart = clearOnStart
        persistentType = type
    }

    fun loadByteCode(files: List<File>) = apply {
        predefinedDirOrJars = (predefinedDirOrJars + files).toPersistentList()
    }

    /**
     * builder for watching file system changes
     * @param delay - delay between syncs
     */
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

interface JIRPersistenceType {

    fun newPersistence(
        runtime: JavaRuntime,
        featuresRegistry: FeaturesRegistry,
        settings: JIRSettings
    ): JIRDatabasePersistence
}

enum class PredefinedPersistenceType : JIRPersistenceType {
    SQLITE {
        override fun newPersistence(
            runtime: JavaRuntime,
            featuresRegistry: FeaturesRegistry,
            settings: JIRSettings
        ): JIRDatabasePersistence {
            return SQLitePersistenceImpl(
                javaRuntime = runtime,
                featuresRegistry = featuresRegistry,
                location = settings.persistentLocation,
                clearOnStart = settings.persistentClearOnStart ?: false
            )
        }
    },
    POSTGRES {
        override fun newPersistence(
            runtime: JavaRuntime,
            featuresRegistry: FeaturesRegistry,
            settings: JIRSettings
        ): JIRDatabasePersistence {
            return PostgresPersistenceImpl(
                javaRuntime = runtime,
                featuresRegistry = featuresRegistry,
                jIRdbUrl = settings.persistentLocation,
                clearOnStart = settings.persistentClearOnStart ?: false
            )
        }
    };

}