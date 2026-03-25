package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
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
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the Ant Application Security Testing Benchmark for Python 3.
 *
 * Benchmark .py files and metadata are loaded from the ant-benchmark-samples JAR
 * (built by the samples module). The JAR path is provided via the ANT_BENCHMARK_SAMPLES_JAR
 * environment variable (set automatically by Gradle).
 *
 * All benchmark .py files are extracted to a flat temp directory and loaded into a shared
 * PIRClasspath (built once in @BeforeAll). Each parameterized test case analyzes a single
 * function within that classpath.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AntBenchmarkTest {

    data class BenchmarkCase(
        val moduleName: String,
        val functionName: String,
        val expectedVulnerable: Boolean,
        val category: String,
    ) {
        override fun toString(): String {
            val label = if (expectedVulnerable) "T" else "F"
            return "$category/$moduleName ($label)"
        }
    }

    private lateinit var cp: PIRClasspath
    private var benchmarkAvailable = false

    private var tempDir: File? = null

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("ANT_BENCHMARK_SAMPLES_JAR")
        if (jarPath == null || !File(jarPath).isFile) {
            println("ANT_BENCHMARK_SAMPLES_JAR not set or file not found (skipping)")
            return
        }

        val cases = collectCases(jarPath)
        if (cases.isEmpty()) {
            println("No benchmark cases found in JAR")
            return
        }

        // Extract .py files from JAR to flat temp directory
        val tmp = java.nio.file.Files.createTempDirectory("ant-benchmark").toFile()
        tempDir = tmp
        extractPythonFiles(jarPath, tmp.toPath())

        val pyFiles = tmp.listFiles()!!
            .filter { it.extension == "py" }
            .map { it.absolutePath }

        println("Building PIR classpath for ${pyFiles.size} benchmark files in flat temp dir...")

        cp = PIRClasspathImpl.create(
            PIRSettings(
                sources = pyFiles,
                mypyFlags = listOf("--ignore-missing-imports"),
                rpcTimeout = java.time.Duration.ofSeconds(1200),
            )
        )
        benchmarkAvailable = true
        println("PIR classpath built. Ready to run ${cases.size} test cases.")
    }

    @AfterAll
    fun tearDown() {
        if (::cp.isInitialized) cp.close()
        tempDir?.deleteRecursively()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("benchmarkCases")
    fun testBenchmarkCase(case: BenchmarkCase) {
        assumeTrue(benchmarkAvailable, "Benchmark not available")

        val entryPoint = cp.findFunctionOrNull(case.functionName)
        assumeTrue(entryPoint != null, "Entry point not found: ${case.functionName}")
        entryPoint!!

        // Configure taint rules
        val entrySources = listOf(
            TaintRules.EntrySource(case.functionName, "taint", 0)
        )

        val sinks = listOf(
            TaintRules.Sink("os.system", "taint", PositionBase.Argument(0), "benchmark"),
            TaintRules.Sink("${case.moduleName}.taint_sink", "taint", PositionBase.Argument(0), "benchmark"),
        )

        val config = PIRTaintConfig(
            sources = emptyList(),
            sinks = sinks,
            propagators = PythonBuiltinPassRules.all,
            entrySources = entrySources,
        )

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

        val vulnerabilities = engine.use { eng ->
            eng.runAnalysis(listOf(entryPoint), timeout = 1.minutes, cancellationTimeout = 10.seconds)
            eng.getVulnerabilities()
        }

        if (case.expectedVulnerable) {
            assertTrue(vulnerabilities.isNotEmpty(),
                "Expected vulnerability in ${case.moduleName} but none found")
        } else {
            assertTrue(vulnerabilities.isEmpty(),
                "Expected no vulnerability in ${case.moduleName} but found ${vulnerabilities.size}")
        }
    }

    companion object {
        /**
         * Optional: set BENCHMARK_SUBSET env var to limit to a specific category prefix.
         * E.g., "accuracy" to only run accuracy tests, or "accuracy/flow_sensitive" for a sub-category.
         * If not set, runs all benchmark tests.
         */
        private val BENCHMARK_SUBSET: String? = System.getenv("BENCHMARK_SUBSET")

        /**
         * Reads the benchmark-metadata.csv from the JAR to build the filename→category map.
         */
        private fun loadMetadata(jarPath: String): Map<String, String> {
            val metadata = mutableMapOf<String, String>()
            JarFile(jarPath).use { jar ->
                val entry = jar.entries().asSequence()
                    .firstOrNull { it.name.endsWith("benchmark-metadata.csv") }
                    ?: return metadata
                jar.getInputStream(entry).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val parts = line.split(",", limit = 2)
                        if (parts.size == 2) {
                            metadata[parts[0]] = parts[1]
                        }
                    }
                }
            }
            return metadata
        }

        /**
         * Extracts .py files from the benchmark JAR into a flat target directory.
         * Files inside the JAR are stored under ant-benchmark/ prefix; they are extracted
         * directly into the target dir without the prefix.
         */
        private fun extractPythonFiles(jarPath: String, targetDir: Path) {
            JarFile(jarPath).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".py") }
                    .forEach { entry ->
                        val fileName = entry.name.substringAfterLast("/")
                        val targetFile = targetDir.resolve(fileName)
                        targetFile.parent.createDirectories()
                        jar.getInputStream(entry).use { input ->
                            targetFile.writeText(input.bufferedReader().readText())
                        }
                    }
            }
        }

        private fun collectCases(jarPath: String): List<BenchmarkCase> {
            val metadata = loadMetadata(jarPath)
            if (metadata.isEmpty()) return emptyList()

            val results = mutableListOf<BenchmarkCase>()

            for ((fileName, category) in metadata) {
                val baseName = fileName.removeSuffix(".py")
                if (!baseName.endsWith("_T") && !baseName.endsWith("_F")) continue

                // Apply subset filter on category
                if (BENCHMARK_SUBSET != null && !category.startsWith(BENCHMARK_SUBSET)) continue

                val isTP = baseName.endsWith("_T")
                val moduleName = baseName
                val functionName = "$moduleName.$baseName"

                results.add(BenchmarkCase(
                    moduleName = moduleName,
                    functionName = functionName,
                    expectedVulnerable = isTP,
                    category = category,
                ))
            }

            return results.sortedBy { it.category + "/" + it.moduleName }
        }

        @JvmStatic
        fun benchmarkCases(): List<BenchmarkCase> {
            val jarPath = System.getenv("ANT_BENCHMARK_SAMPLES_JAR") ?: return emptyList()
            if (!File(jarPath).isFile) return emptyList()
            return collectCases(jarPath)
        }
    }
}
