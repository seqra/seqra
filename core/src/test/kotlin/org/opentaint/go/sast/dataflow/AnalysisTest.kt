package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.go.analysis.GoAnalysisManager
import org.opentaint.dataflow.go.graph.GoApplicationGraph
import org.opentaint.dataflow.go.rules.GoTaintConfig
import org.opentaint.dataflow.go.rules.TaintRules
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.client.GoIRClient
import org.opentaint.ir.go.ext.findFunctionByFullName
import org.opentaint.jvm.sast.dataflow.DummySerializationContext
import org.opentaint.util.analysis.ApplicationGraph
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.bufferedReader
import kotlin.io.deleteRecursively
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.io.readText
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
        return client.buildFromDir(sourcesDir)
    }

    private fun extractGoSourcesFromJar(jarPath: Path, targetDir: Path) {
        JarFile(jarPath.toFile()).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".go") || it.name.endsWith(".mod") }
                .forEach { entry ->
                    val targetFile = targetDir.resolve(entry.name)
                    targetFile.parent.createDirectories()
                    jar.getInputStream(entry).use { input ->
                        targetFile.writeText(input.bufferedReader().readText())
                    }
                }
        }
    }

    val commonPathRules = listOf<TaintRules.Pass>(

    )

    fun assertSinkReachable(
        source: TaintRules.Source,
        sink: TaintRules.Sink,
        entryPointFunction: String
    ) {
        val vulnerabilities = runAnalysis(source, sink, entryPointFunction)
        assertTrue(vulnerabilities.isNotEmpty(), "Sink was not reached")
    }

    fun assertSinkNotReachable(
        source: TaintRules.Source,
        sink: TaintRules.Sink,
        entryPointFunction: String
    ) {
        val vulnerabilities = runAnalysis(source, sink, entryPointFunction)
        assertTrue(vulnerabilities.isEmpty(), "Sink should not be reached")
    }
    
    fun runAnalysis(
        source: TaintRules.Source,
        sink: TaintRules.Sink,
        entryPointFunction: String,
    ): List<TaintSinkTracker.TaintVulnerability> {
        val entryPoint = cp.findFunctionByFullName(entryPointFunction)
            ?: error("Entry point not found")

        val config = GoTaintConfig(listOf(source), listOf(sink), commonPathRules)

        val ifdsGraph = GoApplicationGraph(cp)

        @Suppress("UNCHECKED_CAST")
        val engine = TaintAnalysisUnitRunnerManager(
            GoAnalysisManager(cp),
            ifdsGraph as ApplicationGraph<CommonMethod, CommonInst>,
            unitResolver = { SingletonUnit },
            apManager = TreeApManager(anyAccessorUnrollStrategy = AnyAccessorUnrollStrategy.AnyAccessorDisabled),
            summarySerializationContext = DummySerializationContext,
            taintConfig = config,
            taintRulesStatsSamplingPeriod = null,
        )

        return engine.use { eng ->
            eng.runAnalysis(listOf(entryPoint), timeout = 1.minutes, cancellationTimeout = 10.seconds)
            eng.getVulnerabilities()
        }
    }
}
