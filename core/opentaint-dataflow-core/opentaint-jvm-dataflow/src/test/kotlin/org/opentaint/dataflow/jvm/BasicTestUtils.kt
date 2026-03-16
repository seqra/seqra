package org.opentaint.dataflow.jvm

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.opentaintIrDb
import java.nio.file.Path
import kotlin.io.path.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasicTestUtils {
    protected lateinit var samplesJar: Path
    protected lateinit var db: JIRDatabase
    protected lateinit var cp: JIRClasspath

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        samplesJar = Path(jarPath)

        setupCp()
    }

    protected fun setupCp() = runBlocking {
        db = opentaintIrDb {
            useProcessJavaRuntime()
            persistenceImpl(JIRRamErsSettings)
            installFeatures(InMemoryHierarchy())
            installFeatures(Usages)

            keepLocalVariableNames()

            loadByteCode(listOf(samplesJar.toFile()))
        }

        db.awaitBackgroundJobs()

        cp = db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses))
    }

    @AfterAll
    fun tearDown() {
        if (::cp.isInitialized) cp.close()
        if (::db.isInitialized) db.close()
    }

    protected fun findClass(name: String) = cp.findClassOrNull(name)
        ?: error("Class $name not found")

    protected fun findMethod(className: String, methodName: String) =
        findClass(className).declaredMethods.find { it.name == methodName }
            ?: error("Method $methodName not found in $className")
}
