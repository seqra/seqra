package org.opentaint.ir

import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.CompilationDatabaseImpl
import kotlinx.coroutines.runBlocking
import java.io.File

fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
    val database: CompilationDatabase = CompilationDatabaseImpl()
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
    val predefinedJars: List<File> = emptyList()

    fun persistent(settings: (CompilationDatabasePersistentSettings.() -> Unit) = {}) {
        persistentSettings = settings
    }
}

class CompilationDatabasePersistentSettings {
    var location: String? = null
    var clearOnStart: Boolean = false
}