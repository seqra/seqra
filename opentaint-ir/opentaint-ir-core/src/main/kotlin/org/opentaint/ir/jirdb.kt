package org.opentaint.ir

import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.PersistentEnvironment
import java.io.File

suspend fun jirdb(builder: JIRDBSettings.() -> Unit): JIRDB {
    val settings = JIRDBSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    if (persistentSettings != null) {
        val environment = persistentSettings.toEnvironment()
        if (environment != null) {
            val restoredLocations = environment.allByteCodeLocations
            val byteCodeLocations = restoredLocations.map { it.second }.toList()
            val notLoaded = (
                    JavaRuntime(settings.jre).allLocations +
                            settings.predefinedDirOrJars
                                .filter { it.exists() }
                                .map { it.asByteCodeLocation(isRuntime = false) }
                    ).toSet() - byteCodeLocations.toSet()
            val database = JIRDBImpl(
                persistentEnvironment = environment,
                settings = settings
            )
            database.restoreDataFrom(restoredLocations.toMap())
            database.loadLocations(notLoaded.toList())
            database.afterStart()
            return database
        }
    }
    val database = JIRDBImpl(null, settings)
    database.loadJavaLibraries()
    if (settings.predefinedDirOrJars.isNotEmpty()) {
        database.load(settings.predefinedDirOrJars)
    }
    if (settings.watchFileSystemChanges != null) {
        database.watchFileSystemChanges()
    }
    database.afterStart()
    return database
}

private fun JIRDBPersistentSettings.toEnvironment(): PersistentEnvironment? {
    return this.location?.let {
        PersistentEnvironment(key, File(it), clearOnStart)
    }
}
