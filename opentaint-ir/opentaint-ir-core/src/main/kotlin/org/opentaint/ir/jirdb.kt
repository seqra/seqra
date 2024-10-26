package org.opentaint.ir

import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl
import java.io.File

suspend fun jirdb(builder: JIRDBSettings.() -> Unit): JIRDB {
    val settings = JIRDBSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    val featureRegistry = FeaturesRegistry(settings.fullFeatures)
    val environment = SQLitePersistenceImpl(
        featureRegistry,
        location = File(persistentSettings?.location ?: ":memory:"),
        clearOnStart = persistentSettings?.clearOnStart ?: true
    )
    return JIRDBImpl(
        persistence = environment,
        featureRegistry = featureRegistry,
        settings = settings
    ).also {
        it.restore()
        it.afterStart()
    }
}