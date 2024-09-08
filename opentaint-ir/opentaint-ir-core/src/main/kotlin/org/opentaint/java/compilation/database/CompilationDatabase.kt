package org.opentaint.java.compilation.database

import org.objectweb.asm.Opcodes
import org.opentaint.java.compilation.database.api.CompilationDatabase
import org.opentaint.java.compilation.database.impl.CompilationDatabaseImpl
import java.io.File

suspend fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
    val database = CompilationDatabaseImpl(settings)
    database.loadJavaLibraries()
    if (settings.predefinedJars.isNotEmpty()) {
        database.load(settings.predefinedJars)
    }
    if (settings.watchFileSystemChanges != null) {
        database.watchFileSystemChanges()
    }
    return database
}

class CompilationDatabaseSettings {
    var watchFileSystemChanges: CompilationDatabaseWatchFileSystemSettings? = null

    var persistentSettings: CompilationDatabasePersistentSettings? = null
    var predefinedJars: List<File> = emptyList()
    lateinit var jre: File
    var apiLevel = ApiLevel.ASM8
    fun persistent(settings: (CompilationDatabasePersistentSettings.() -> Unit) = {}) {
        persistentSettings = CompilationDatabasePersistentSettings().also(settings)
    }

    fun watchFileSystem(settings: (CompilationDatabaseWatchFileSystemSettings.() -> Unit) = {}) {
        watchFileSystemChanges = CompilationDatabaseWatchFileSystemSettings().also(settings)
    }

    fun useJavaHomeJRE() {
        val javaHome = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME is not set")
        jre = javaHome.asValidJRE()
    }

    fun useProcessJRE() {
        val javaHome = System.getProperty("java.home") ?: throw IllegalArgumentException("java.home is not set")
        jre = javaHome.asValidJRE()
    }

    private fun String.asValidJRE(): File {
        val file = File(this)
        if (!file.exists()) {
            throw IllegalArgumentException("$this points to folder that do not exists")
        }
        return file
    }
}

enum class ApiLevel(val code: Int) {
    ASM4(Opcodes.ASM4),
    ASM5(Opcodes.ASM5),
    ASM6(Opcodes.ASM6),
    ASM7(Opcodes.ASM7),
    ASM8(Opcodes.ASM8),
    ASM9(Opcodes.ASM9)
}

class CompilationDatabasePersistentSettings {
    var location: String? = null
    var clearOnStart: Boolean = false
}

class CompilationDatabaseWatchFileSystemSettings {
    var delay: Long? = 10_000 // 10 seconds
}