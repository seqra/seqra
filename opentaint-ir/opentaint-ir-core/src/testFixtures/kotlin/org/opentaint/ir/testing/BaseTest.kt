package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRPersistenceImplSettings
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.JIRSQLitePersistenceSettings
import org.opentaint.ir.impl.features.Builders
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.opentaint-ir
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import kotlin.reflect.full.companionObjectInstance

@Tag("lifecycle")
annotation class LifecycleTest

abstract class BaseTest {

    protected open val cp: JIRClasspath = runBlocking {
        val withDB = this@BaseTest.javaClass.withDB
        withDB.db.classpath(allClasspath, withDB.classpathFeatures.toList())
    }

    @AfterEach
    open fun close() {
        cp.close()
    }

}

val Class<*>.withDB: JIRDatabaseHolder
    get() {
        val comp = kotlin.companionObjectInstance
        if (comp is JIRDatabaseHolder) {
            return comp
        }
        val s = superclass
            ?: throw IllegalStateException("can't find WithDB companion object. Please check that test class has it.")
        return s.withDB
    }

interface JIRDatabaseHolder {

    val classpathFeatures: List<JIRClasspathFeature>
    val db: JIRDatabase
    fun cleanup()
}

open class WithDB(vararg features: Any) : JIRDatabaseHolder {

    protected var allFeatures = features.toList().toTypedArray()

    init {
        System.setProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "500")
    }

    val dbFeatures = allFeatures.mapNotNull { it as? JIRFeature<*, *> }
    override val classpathFeatures = allFeatures.mapNotNull { it as? JIRClasspathFeature }

    override var db = runBlocking {
        opentaint-ir {
            // persistent("D:\\work\\opentaint-ir\\jIRdb-index.db")
            persistenceImpl(persistenceImpl())
            loadByteCode(allClasspath)
            useProcessJavaRuntime()
            keepLocalVariableNames()
            installFeatures(*dbFeatures.toTypedArray())
        }.also {
            it.awaitBackgroundJobs()
        }
    }

    override fun cleanup() {
        db.close()
    }

    internal open fun persistenceImpl(): JIRPersistenceImplSettings = JIRSQLitePersistenceSettings
}

open class WithRAMDB(vararg features: Any) : WithDB(*features) {

    override fun persistenceImpl() = JIRRamErsSettings
}

val globalDb by lazy {
    WithDB(Usages, Builders, InMemoryHierarchy).db
}

val globalRAMDb by lazy {
    WithRAMDB(Usages, Builders, InMemoryHierarchy).db
}

open class WithGlobalDB(vararg _classpathFeatures: JIRClasspathFeature) : JIRDatabaseHolder {

    init {
        System.setProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "500")
    }

    override val classpathFeatures: List<JIRClasspathFeature> = _classpathFeatures.toList()

    override val db: JIRDatabase get() = globalDb

    override fun cleanup() {
    }
}

open class WithGlobalRAMDB(vararg _classpathFeatures: JIRClasspathFeature) : JIRDatabaseHolder {

    init {
        System.setProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "500")
    }

    override val classpathFeatures: List<JIRClasspathFeature> = _classpathFeatures.toList()

    override val db: JIRDatabase get() = globalRAMDb

    override fun cleanup() {
    }
}

open class WithRestoredDB(vararg features: JIRFeature<*, *>) : WithDB(*features) {

    private val location by lazy {
        if (implSettings is JIRSQLitePersistenceSettings) {
            Files.createTempFile("jIRdb-", null).toFile().absolutePath
        } else {
            Files.createTempDirectory("jIRdb-").toFile().absolutePath
        }
    }

    var tempDb: JIRDatabase? = newDB()

    override var db: JIRDatabase = newDB {
        tempDb?.close()
        tempDb = null
    }

    open val implSettings: JIRPersistenceImplSettings get() = JIRSQLitePersistenceSettings

    private fun newDB(before: () -> Unit = {}): JIRDatabase {
        before()
        return runBlocking {
            opentaint-ir {
                require(implSettings !is JIRRamErsSettings) { "cannot restore in-RAM database" }
                persistent(
                    location = location,
                    implSettings = implSettings
                )
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
                keepLocalVariableNames()
                installFeatures(*dbFeatures.toTypedArray())
            }.also {
                it.awaitBackgroundJobs()
            }
        }
    }

}
