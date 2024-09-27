package org.opentaint.ir

import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.impl.CompilationDatabaseImpl
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.PersistentEnvironment
import java.io.File

suspend fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    val features = settings.fullFeatures
    if (persistentSettings != null) {
        val environment = persistentSettings.toEnvironment()
        if (environment != null) {
            val restoredLocations = environment.allByteCodeLocations
            val restoredFeatures = environment.restore(features, restoredLocations.toList())
            val notLoaded = (JavaRuntime(settings.jre).allLocations + settings.predefinedDirOrJars.map {
                it.asByteCodeLocation(isRuntime = false)
            }).toSet() - restoredLocations.toSet()
            val database = CompilationDatabaseImpl(
                persistentEnvironment = environment,
                settings = settings,
                featureRegistry = restoredFeatures
            )

            database.loadLocations(notLoaded.toList())
            return database
        }
    }
    val database = CompilationDatabaseImpl(null, settings, FeaturesRegistry(null, settings.fullFeatures))
    database.loadJavaLibraries()
    if (settings.predefinedDirOrJars.isNotEmpty()) {
        database.load(settings.predefinedDirOrJars)
    }
    if (settings.watchFileSystemChanges != null) {
        database.watchFileSystemChanges()
    }
    return database
}

private fun CompilationDatabasePersistentSettings.toEnvironment(): PersistentEnvironment? {
    return this.location?.let {
        PersistentEnvironment(key, File(it))
    }
}
