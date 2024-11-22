
package org.opentaint.ir

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl

suspend fun jirdb(builder: JIRDBSettings.() -> Unit): JIRDB {
    return jirdb(JIRDBSettings().also(builder))
}

suspend fun jirdb(settings: JIRDBSettings): JIRDB {
    val featureRegistry = FeaturesRegistry(settings.features)
    val javaRuntime = JavaRuntime(settings.jre)
    val environment = SQLitePersistenceImpl(
        javaRuntime,
        featureRegistry,
        location = settings.persistentLocation,
        clearOnStart = settings.persistentClearOnStart ?: true
    )
    return JIRDBImpl(
        javaRuntime = javaRuntime,
        persistence = environment,
        featureRegistry = featureRegistry,
        settings = settings
    ).also {
        it.restore()
        it.afterStart()
    }
}

/** bridge for Java */
fun asyncJirdb(settings: JIRDBSettings) = GlobalScope.future { jirdb(settings) }