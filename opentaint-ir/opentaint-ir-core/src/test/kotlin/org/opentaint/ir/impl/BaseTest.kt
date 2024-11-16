package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.jirdb
import java.nio.file.Files
import kotlin.reflect.full.companionObjectInstance

@ExtendWith(CleanDB::class)
abstract class BaseTest {

    protected val cp: JIRClasspath = runBlocking {
        val withDB = this@BaseTest.javaClass.withDB
        withDB.db.classpath(allClasspath)
    }

    @AfterEach
    fun close() {
        cp.close()
    }

}

val Class<*>.withDB: WithDB
    get() {
        val comp = kotlin.companionObjectInstance
        if (comp is WithDB) {
            return comp
        }
        val s = superclass
        if (superclass == null) {
            throw IllegalStateException("can't find WithDB companion object. Please check that test class has it.")
        }
        return s.withDB
    }

open class WithDB(vararg features: JIRFeature<*, *>) {

    protected var allFeatures = features.toList().toTypedArray()

    open var db = runBlocking {
        jirdb {
            loadByteCode(allClasspath)
            useProcessJavaRuntime()
            installFeatures(*allFeatures)
        }.also {
            it.awaitBackgroundJobs()
        }
    }

    open fun cleanup() {
        db.close()
    }
}

open class WithRestoredDB(vararg features: JIRFeature<*, *>): WithDB(*features) {

    private val jdbcLocation = Files.createTempFile("jirdb-", null).toFile().absolutePath

    var tempDb: JIRDB? = newDB()

    override var db: JIRDB = newDB {
        tempDb?.close()
        tempDb = null
    }

    private fun newDB(before: () -> Unit = {}): JIRDB {
        before()
        return runBlocking {
            jirdb {
                persistent(jdbcLocation)
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
                installFeatures(*allFeatures)
            }.also {
                it.awaitBackgroundJobs()
            }
        }
    }

}



class CleanDB : AfterAllCallback {
    override fun afterAll(context: ExtensionContext) {
        val companion = context.requiredTestClass.kotlin.companionObjectInstance
        if (companion is WithDB) {
            companion.cleanup()
        }
    }
}
