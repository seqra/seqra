package org.opentaint.ir

import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl

suspend fun jirdb(builder: JIRDBSettings.() -> Unit): JIRDB {
    return jirdb(JIRDBSettings().also(builder))
}

suspend fun jirdb(settings: JIRDBSettings): JIRDB {
    val featureRegistry = FeaturesRegistry(settings.features)
    val environment = SQLitePersistenceImpl(
        featureRegistry,
        location = settings.persistentLocation,
        clearOnStart = settings.persistentClearOnStart ?: true
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