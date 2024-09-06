package org.opentaint.ir

import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.CompilationDatabaseImpl
import kotlinx.coroutines.runBlocking
import org.objectweb.asm.Opcodes
import java.io.File

fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
    val database: CompilationDatabase = CompilationDatabaseImpl(settings.apiLevel)
    if (settings.watchFileSystemChanges) {
        database.watchFileSystemChanges()
    }
    if (settings.predefinedJars.isNotEmpty()) {
        runBlocking {
            database.load(settings.predefinedJars)
        }
    }
    return database
}

class CompilationDatabaseSettings {
    var watchFileSystemChanges: Boolean = false
    var persistentSettings: (CompilationDatabasePersistentSettings.() -> Unit)? = null
    var predefinedJars: List<File> = emptyList()
    var apiLevel = ApiLevel.ASM8
    fun persistent(settings: (CompilationDatabasePersistentSettings.() -> Unit) = {}) {
        persistentSettings = settings
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