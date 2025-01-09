package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.impl.opentaint-ir
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
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
        opentaint-ir {
//            persistent("D:\\work\\jIRdb\\jIRdb-index.db")
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

open class WithRestoredDB(vararg features: JIRFeature<*, *>) : WithDB(*features) {

    private val jdbcLocation = Files.createTempFile("jIRdb-", null).toFile().absolutePath

    var tempDb: JIRDatabase? = newDB()

    override var db: JIRDatabase = newDB {
        tempDb?.close()
        tempDb = null
    }

    private fun newDB(before: () -> Unit = {}): JIRDatabase {
        before()
        return runBlocking {
            opentaint-ir {
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
