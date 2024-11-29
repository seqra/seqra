
@file:JvmName("Opentaint-IR")
package org.opentaint.ir

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRDBImpl
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl

suspend fun opentaint-ir(builder: JIRSettings.() -> Unit): JIRDB {
    return opentaint-ir(JIRSettings().also(builder))
}

suspend fun opentaint-ir(settings: JIRSettings): JIRDB {
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
fun async(settings: JIRSettings) = GlobalScope.future { opentaint-ir(settings) }