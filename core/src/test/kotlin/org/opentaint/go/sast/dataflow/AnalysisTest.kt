package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.client.GoIRClient
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.bufferedReader
import kotlin.io.deleteRecursively
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.io.readText
import kotlin.use

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AnalysisTest {
    lateinit var sourcesDir: Path
    lateinit var cp: GoIRProgram
    lateinit var client: GoIRClient

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        client = GoIRClient()

        sourcesDir = createTempDirectory("go-sources")
        extractGoSourcesFromJar(Path(jarPath), sourcesDir)

        cp = createClasspath()
    }

    @AfterAll
    fun tearDown() {
        if (::client.isInitialized) client.close()
        if (::sourcesDir.isInitialized) {
            sourcesDir.toFile().deleteRecursively()
        }
    }

    private fun createClasspath(): GoIRProgram {
        return client.buildFromDir(sourcesDir, "./...")
    }

    private fun extractGoSourcesFromJar(jarPath: Path, targetDir: Path) {
        JarFile(jarPath.toFile()).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".go") }
                .forEach { entry ->
                    val targetFile = targetDir.resolve(entry.name)
                    targetFile.parent.createDirectories()
                    jar.getInputStream(entry).use { input ->
                        targetFile.writeText(input.bufferedReader().readText())
                    }
                }
        }
    }
}
