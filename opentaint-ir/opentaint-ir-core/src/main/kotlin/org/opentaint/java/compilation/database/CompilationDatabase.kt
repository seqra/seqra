package org.opentaint.java.compilation.database

import org.opentaint.java.compilation.database.api.CompilationDatabase
import org.opentaint.java.compilation.database.api.IndexInstaller
import org.opentaint.java.compilation.database.impl.CompilationDatabaseImpl
import java.io.File

suspend fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
    val database = CompilationDatabaseImpl(settings)
    database.loadJavaLibraries()
    if (settings.predefinedDirOrJars.isNotEmpty()) {
        database.load(settings.predefinedDirOrJars)
    }
    if (settings.watchFileSystemChanges != null) {
        database.watchFileSystemChanges()
    }
    return database
}

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
    /** index installers */
    var additionalIndexes: List<IndexInstaller<*,*>> = emptyList()
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
    fun installIndexes(vararg indexInstaller: IndexInstaller<*,*>){
        additionalIndexes  = additionalIndexes + indexInstaller.toList()
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
    /** if true old data from this folder will be dropped */
    var clearOnStart: Boolean = false
}

class CompilationDatabaseWatchFileSystemSettings {
    /** delay between looking up for new changes */
    var delay: Long? = 10_000 // 10 seconds
}