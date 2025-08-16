package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRPersistenceImplSettings
import org.opentaint.ir.impl.JIRErsSettings
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.JIRSQLitePersistenceSettings
import org.opentaint.ir.impl.RamErsSettings
import org.opentaint.ir.impl.features.Builders
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClassMethodsAndFields
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.ir.impl.storage.ers.ram.RAM_ERS_SPI
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.reflect.full.companionObjectInstance

@Tag("lifecycle")
annotation class LifecycleTest

abstract class BaseTest(getAdditionalFeatures: (() -> List<JIRClasspathFeature>)? = null) {

    protected open val cp: JIRClasspath by lazy {
        runBlocking {
            val withDb = this@BaseTest.javaClass.withDb
            val additionalFeatures = getAdditionalFeatures?.invoke().orEmpty()
            withDb.db.classpath(allClasspath, additionalFeatures + withDb.classpathFeatures.toList())
        }
    }

    @AfterEach
    open fun close() {
        cp.close()
    }
}

val Class<*>.withDb: JIRDatabaseHolder
    get() {
        val comp = kotlin.companionObjectInstance
        if (comp is JIRDatabaseHolder) {
            return comp
        }
        val s = superclass
            ?: throw IllegalStateException("can't find WithDb companion object. Please check that test class has it.")
        return s.withDb
    }

interface JIRDatabaseHolder {

    val classpathFeatures: List<JIRClasspathFeature>
    val db: JIRDatabase
    fun cleanup()
}

open class WithDb(vararg features: Any) : JIRDatabaseHolder {

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

    internal open fun persistenceImpl(): JIRPersistenceImplSettings = JIRRamErsSettings
}

open class WithDbImmutable(vararg features: Any) : JIRDatabaseHolder {

    protected var allFeatures = features.toList().toTypedArray()

    val dbFeatures = allFeatures.mapNotNull { it as? JIRFeature<*, *> }
    override val classpathFeatures = allFeatures.mapNotNull { it as? JIRClasspathFeature }

    override var db = runBlocking {
        opentaint-ir {
            persistenceImpl(JIRErsSettings(RAM_ERS_SPI, RamErsSettings(immutableDumpsPath = tempDir)))
            loadByteCode(allClasspath)
            useProcessJavaRuntime()
            keepLocalVariableNames()
            installFeatures(*dbFeatures.toTypedArray())
        }.also {
            it.setImmutable()
        }
    }

    override fun cleanup() {
        db.close()
    }

    companion object {
        private val tempDir by lazy {
            Paths.get(System.getProperty("java.io.tmpdir"), "jIRdb-ers-immutable")
                .also {
                    if (!Files.exists(it)) {
                        Files.createDirectories(it)
                    }
                }
                .absolutePathString()
        }
    }
}

open class WithSQLiteDb(vararg features: Any) : WithDb(*features) {

    override fun persistenceImpl() = JIRSQLitePersistenceSettings
}

val globalDb by lazy {
    WithDb(Usages, Builders, InMemoryHierarchy).db
}

val globalDbImmutable by lazy {
    WithDbImmutable(Usages, Builders, InMemoryHierarchy).db
}

val globalSQLiteDb by lazy {
    WithSQLiteDb(Usages, Builders, InMemoryHierarchy).db
}

open class WithGlobalDb(vararg _classpathFeatures: JIRClasspathFeature) : JIRDatabaseHolder {

    init {
        System.setProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "500")
    }

    override val classpathFeatures: List<JIRClasspathFeature> = _classpathFeatures.toList()

    override val db: JIRDatabase get() = globalDb

    override fun cleanup() {
    }
}

open class WithGlobalDbImmutable(vararg _classpathFeatures: JIRClasspathFeature) : JIRDatabaseHolder {

    init {
        System.setProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "500")
    }

    override val classpathFeatures: List<JIRClasspathFeature> = _classpathFeatures.toList()

    override val db: JIRDatabase get() = globalDbImmutable

    override fun cleanup() {
    }
}

open class WithGlobalSQLiteDb(vararg _classpathFeatures: JIRClasspathFeature) : JIRDatabaseHolder {

    init {
        System.setProperty("org.opentaint.ir.impl.storage.defaultBatchSize", "500")
    }

    override val classpathFeatures: List<JIRClasspathFeature> = _classpathFeatures.toList()

    override val db: JIRDatabase get() = globalSQLiteDb

    override fun cleanup() {
    }
}

open class WithGlobalDbWithoutJRE(vararg _classpathFeatures: JIRClasspathFeature) :
    WithGlobalDb(*_classpathFeatures) {

    override val classpathFeatures: List<JIRClasspathFeature> =
        super.classpathFeatures + UnknownClasses + UnknownClassMethodsAndFields

    override val db: JIRDatabase = runBlocking {
        opentaint-ir {
            persistenceImpl(JIRRamErsSettings)
            loadByteCode(allClasspath)
            keepLocalVariableNames()
            buildModelForJRE(build = false)
            installFeatures(Usages, Builders, InMemoryHierarchy)
        }.also {
            it.awaitBackgroundJobs()
        }
    }
}

open class WithRestoredDb(vararg features: JIRFeature<*, *>) : WithDb(*features) {

    private val location by lazy {
        if (implSettings is JIRSQLitePersistenceSettings) {
            Files.createTempFile("jIRdb-", null).toFile().absolutePath
        } else {
            Files.createTempDirectory("jIRdb-").toFile().absolutePath
        }
    }

    var tempDb: JIRDatabase? = newDb()

    override var db: JIRDatabase = newDb {
        tempDb?.close()
        tempDb = null
    }

    open val implSettings: JIRPersistenceImplSettings get() = JIRSQLitePersistenceSettings

    private fun newDb(before: () -> Unit = {}): JIRDatabase {
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
