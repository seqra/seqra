package org.opentaint.ir

import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.Hook
import org.opentaint.ir.api.JIRDB
import java.io.File

/**
 * Settings for database
 */
class JIRDBSettings {
    /** watch file system changes setting */
    var watchFileSystemChanges: JIRDBWatchFileSystemSettings? = null

    /** persisted  */
    var persistentSettings: JIRDBPersistentSettings? = null

    /** jar files which should be loaded right after database is created */
    var predefinedDirOrJars: List<File> = emptyList()

    var hooks: MutableList<(JIRDB) -> Hook> = arrayListOf()

    /** mandatory setting for java location */
    lateinit var jre: File

    /** feature to add */
    var features: List<Feature<*, *>> = emptyList()

    val fullFeatures get() = features

    /** builder for persistent settings */
    fun persistent(settings: (JIRDBPersistentSettings.() -> Unit) = {}) {
        persistentSettings = JIRDBPersistentSettings().also(settings)
    }

    /** builder for watching file system changes */
    fun watchFileSystem(settings: (JIRDBWatchFileSystemSettings.() -> Unit) = {}) {
        watchFileSystemChanges = JIRDBWatchFileSystemSettings().also(settings)
    }

    /** builder for hooks */
    fun withHook(hook: (JIRDB) -> Hook) {
        hooks += hook
    }

    /**
     * use java from JAVA_HOME env variable
     */
    fun useJavaHomeJavaRuntime() {
        val javaHome = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * use java from current system process
     */
    fun useProcessJavaRuntime() {
        val javaHome = System.getProperty("java.home") ?: throw IllegalArgumentException("java.home is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * install additional indexes
     */
    fun installFeatures(vararg feature: Feature<*, *>) {
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


class JIRDBPersistentSettings {
    /** location folder for persisting data */
    var location: String? = null

    /** if true old data from this folder will be dropped */
    var clearOnStart: Boolean = false
}

class JIRDBWatchFileSystemSettings {
    /** delay between looking up for new changes */
    var delay: Long? = 10_000 // 10 seconds
}