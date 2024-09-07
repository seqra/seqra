package org.opentaint.ir

import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.CompilationDatabaseImpl
import org.objectweb.asm.Opcodes
import java.io.File

suspend fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
    val database = CompilationDatabaseImpl(settings.apiLevel, settings.jre)
    database.loadJavaLibraries()
    if (settings.predefinedJars.isNotEmpty()) {
        database.load(settings.predefinedJars)
    }
    if (settings.watchFileSystemChanges) {
        database.watchFileSystemChanges()
    }
    return database
}

class CompilationDatabaseSettings {
    var watchFileSystemChanges: Boolean = false
    var persistentSettings: (CompilationDatabasePersistentSettings.() -> Unit)? = null
    var predefinedJars: List<File> = emptyList()
    lateinit var jre: File
    var apiLevel = ApiLevel.ASM8
    fun persistent(settings: (CompilationDatabasePersistentSettings.() -> Unit) = {}) {
        persistentSettings = settings
    }

    fun useJavaHomeJRE() {
        val javaHome = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME is not set")
        jre = File(javaHome)
        if (!jre.exists()) {
            throw IllegalArgumentException("JAVA_HOME: $javaHome points to folder that do not exists")
        }
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