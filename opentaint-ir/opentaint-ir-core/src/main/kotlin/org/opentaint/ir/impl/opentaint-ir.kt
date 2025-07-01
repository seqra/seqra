@file:JvmName("Opentaint-IR")

package org.opentaint.ir.impl

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.impl.fs.JavaRuntime

suspend fun opentaint-ir(builder: JIRSettings.() -> Unit): JIRDatabase {
    return opentaint-ir(JIRSettings().also(builder))
}

suspend fun opentaint-ir(settings: JIRSettings): JIRDatabase {
    val javaRuntime = JavaRuntime(settings.jre)
    return JIRDatabaseImpl(javaRuntime = javaRuntime, settings = settings).also {
        it.restore()
        it.afterStart()
    }
}

/** bridge for Java */
fun async(settings: JIRSettings) = GlobalScope.future { opentaint-ir(settings) }
