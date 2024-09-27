package org.opentaint.ir

import org.opentaint.ir.api.Feature
import org.opentaint.ir.impl.index.Hierarchy
import java.io.File

/**
 * Settings for database
 */
class CompilationDatabaseSettings {
    /** watch file system changes setting */
    var watchFileSystemChanges: CompilationDatabaseWatchFileSystemSettings? = null

    /** persisted  */
    var persistentSettings: CompilationDatabasePersistentSettings? = null

    /** jar files which should be loaded right after database is created */
    var predefinedDirOrJars: List<File> = emptyList()

    /** mandatory setting for java location */
    lateinit var jre: File

    /** feature to add */
    var features: List<Feature<*, *>> = emptyList()

    val fullFeatures get() = listOf(Hierarchy) + features

    /** builder for persistent settings */
    fun persistent(settings: (CompilationDatabasePersistentSettings.() -> Unit) = {}) {
        persistentSettings = CompilationDatabasePersistentSettings().also(settings)
    }

    /** builder for watching file system changes */
    fun watchFileSystem(settings: (CompilationDatabaseWatchFileSystemSettings.() -> Unit) = {}) {
        watchFileSystemChanges = CompilationDatabaseWatchFileSystemSettings().also(settings)
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


class CompilationDatabasePersistentSettings {
    /** location folder for persisting data */
    var location: String? = null

    /** key for data */
    var key: String = "jirdb"

    /** if true old data from this folder will be dropped */
    var clearOnStart: Boolean = false
}

class CompilationDatabaseWatchFileSystemSettings {
    /** delay between looking up for new changes */
    var delay: Long? = 10_000 // 10 seconds
}