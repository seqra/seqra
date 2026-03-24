package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.python.analysis.PIRAnalysisManager
import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.dataflow.python.rules.PIRTaintConfig
import org.opentaint.dataflow.python.rules.PythonBuiltinPassRules
import org.opentaint.dataflow.python.rules.TaintRules
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRSettings
import org.opentaint.ir.impl.python.PIRClasspathImpl
import org.opentaint.jvm.sast.dataflow.DummySerializationContext
import org.opentaint.util.analysis.ApplicationGraph
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.bufferedReader
import kotlin.io.deleteRecursively
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.io.readText
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.use

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AnalysisTest {
    lateinit var sourcesDir: Path
    lateinit var cp: PIRClasspath

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        sourcesDir = createTempDirectory("python-sources")
        extractPythonSourcesFromJar(Path(jarPath), sourcesDir)

        val pyFiles = sourcesDir.walk()
            .filter { it.isRegularFile() && it.extension == "py" }
            .mapTo(mutableListOf()) { it.absolutePathString() }

        cp = createClasspath(pyFiles)
    }

    @AfterAll
    fun tearDown() {
        if (::cp.isInitialized) cp.close()
        if (::sourcesDir.isInitialized) {
            sourcesDir.toFile().deleteRecursively()
        }
    }

    private fun createClasspath(pyFiles: List<String>): PIRClasspath {
        return PIRClasspathImpl.create(
            PIRSettings(
                sources = pyFiles,
                mypyFlags = listOf("--ignore-missing-imports"),
                rpcTimeout = java.time.Duration.ofSeconds(1200),
            )
        )
    }

    /**
     * Pass rules for Python builtin entities (list/set/tuple/dict/str methods)
     */
    val commonPathRules: List<TaintRules.Pass> = PythonBuiltinPassRules.all

    private fun extractPythonSourcesFromJar(jarPath: Path, targetDir: Path) {
        JarFile(jarPath.toFile()).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".py") }
                .forEach { entry ->
                    val targetFile = targetDir.resolve(entry.name)
                    targetFile.parent.createDirectories()
                    jar.getInputStream(entry).use { input ->
                        targetFile.writeText(input.bufferedReader().readText())
                    }
                }
        }
    }

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
        val entryPoint = cp.findFunctionOrNull(entryPointFunction)
            ?: error("Entry point not found")

        val config = PIRTaintConfig(listOf(source), listOf(sink), commonPathRules)

        val ifdsGraph = PIRApplicationGraph(cp)

        @Suppress("UNCHECKED_CAST")
        val engine = TaintAnalysisUnitRunnerManager(
            PIRAnalysisManager(cp),
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
