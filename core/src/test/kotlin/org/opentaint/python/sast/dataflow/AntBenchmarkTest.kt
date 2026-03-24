package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the Ant Application Security Testing Benchmark for Python 3.
 *
 * The benchmark directory is expected at ~/data/ant-application-security-testing-benchmark/sast-python3/case/
 * If not present, all tests are skipped.
 *
 * All benchmark .py files are loaded into a shared PIRClasspath (built once in @BeforeAll).
 * Each parameterized test case analyzes a single function within that classpath.
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
        if (!BENCHMARK_ROOT.isDirectory) {
            println("Benchmark directory not found: $BENCHMARK_ROOT (skipping)")
            return
        }

        val cases = collectCases()
        if (cases.isEmpty()) {
            println("No benchmark cases found")
            return
        }

        // Copy benchmark files to a flat temp directory to avoid nested package issues
        val tmp = java.nio.file.Files.createTempDirectory("ant-benchmark").toFile()
        tempDir = tmp
        for ((file, _) in cases) {
            file.copyTo(File(tmp, file.name), overwrite = true)
        }

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
        private val BENCHMARK_ROOT = File(
            System.getProperty("user.home"),
            "data/ant-application-security-testing-benchmark/sast-python3/case"
        )

        /**
         * Optional: set BENCHMARK_SUBSET env var to limit to a specific subdirectory.
         * E.g., "accuracy/flow_sensitive" to only run flow-sensitive accuracy tests.
         * If not set, runs all single-file tests.
         */
        private val BENCHMARK_SUBSET: String? = System.getenv("BENCHMARK_SUBSET")

        private fun collectCases(): List<Pair<File, BenchmarkCase>> {
            if (!BENCHMARK_ROOT.isDirectory) return emptyList()

            val results = mutableListOf<Pair<File, BenchmarkCase>>()

            val searchDirs = if (BENCHMARK_SUBSET != null) {
                listOf(BENCHMARK_ROOT.resolve(BENCHMARK_SUBSET))
            } else {
                listOf(
                    BENCHMARK_ROOT.resolve("completeness/single_app_tracing"),
                    BENCHMARK_ROOT.resolve("accuracy"),
                )
            }

            for (searchDir in searchDirs) {
                if (!searchDir.isDirectory) continue
                searchDir.walkTopDown()
                    .filter { it.isFile && it.extension == "py" && it.name != "__init__.py" }
                    .filter { file ->
                        val baseName = file.nameWithoutExtension
                        baseName.endsWith("_T") || baseName.endsWith("_F")
                    }
                    .filter { file ->
                        // Exclude cross-file test cases (files in subdirectories named *_T or *_F)
                        val parentName = file.parentFile.name
                        !parentName.endsWith("_T") && !parentName.endsWith("_F")
                    }
                    .forEach { file ->
                        val baseName = file.nameWithoutExtension
                        val isTP = baseName.endsWith("_T")
                        val moduleName = baseName
                        val functionName = "$moduleName.$baseName"
                        val category = file.parentFile.relativeTo(BENCHMARK_ROOT).path

                        results.add(file to BenchmarkCase(
                            moduleName = moduleName,
                            functionName = functionName,
                            expectedVulnerable = isTP,
                            category = category,
                        ))
                    }
            }

            return results.sortedBy { it.second.category + "/" + it.second.moduleName }
        }

        @JvmStatic
        fun benchmarkCases(): List<BenchmarkCase> = collectCases().map { it.second }
    }
}
