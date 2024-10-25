package org.opentaint.ir

import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl
import java.io.File

suspend fun jirdb(builder: JIRDBSettings.() -> Unit): JIRDB {
    val settings = JIRDBSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    val featureRegistry = FeaturesRegistry(settings.fullFeatures)
    val environment = SQLitePersistenceImpl(featureRegistry,
        location = File(persistentSettings?.location ?: ":memory:"),
        clearOnStart = persistentSettings?.clearOnStart ?: true
    )
    val restoredLocations = environment.locations.toSet()
    val notLoaded = (
            JavaRuntime(settings.jre).allLocations +
                    settings.predefinedDirOrJars
                        .filter { it.exists() }
                        .map { it.asByteCodeLocation(isRuntime = false) }
            ).toSet() - restoredLocations
    val database = JIRDBImpl(
        persistence = environment,
        featureRegistry = featureRegistry,
        settings = settings
    )
    environment.setup()
    database.loadLocations(notLoaded.toList())
    database.afterStart()
    return database
}

private fun JIRDBPersistentSettings.toEnvironment(featuresRegistry: FeaturesRegistry): JIRDBPersistence {
    return location.let {
        SQLitePersistenceImpl(featuresRegistry, File(it), clearOnStart)
    }
}
