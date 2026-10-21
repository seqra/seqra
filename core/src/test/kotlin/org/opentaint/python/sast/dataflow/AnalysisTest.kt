package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.python.ContainsMark
import org.opentaint.dataflow.configuration.python.Position
import org.opentaint.dataflow.configuration.python.Result
import org.opentaint.dataflow.configuration.python.TaintAssignAction
import org.opentaint.dataflow.configuration.python.TaintCleanAction
import org.opentaint.dataflow.configuration.python.TaintCleaner
import org.opentaint.dataflow.configuration.python.TaintEntryPointSource
import org.opentaint.dataflow.configuration.python.TaintExitSink
import org.opentaint.dataflow.configuration.python.TaintMark
import org.opentaint.dataflow.configuration.python.TaintPassThrough
import org.opentaint.dataflow.configuration.python.TaintSink
import org.opentaint.dataflow.configuration.python.TaintSinkMeta
import org.opentaint.dataflow.configuration.python.TaintSource
import org.opentaint.dataflow.configuration.python.Target
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.python.rules.PIRCombinedTaintRulesProvider
import org.opentaint.dataflow.python.rules.PIRTaintRulesProvider
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRSettings
import org.opentaint.ir.impl.python.PIRClasspathLoader
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
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.io.readText
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.use

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AnalysisTest {
    lateinit var sourcesDir: Path
    lateinit var cp: PIRClasspath

    open val externalMethods: ExternalMethodTracker? = null

    @BeforeAll
    fun setup() {
        cp = initCp()
    }

    open fun initCp(): PIRClasspath {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        sourcesDir = createTempDirectory("python-sources")
        extractPythonSourcesFromJar(Path(jarPath), sourcesDir)

        val pyFiles = sourcesDir.walk()
            .filter { it.isRegularFile() && it.extension == "py" }
            .mapTo(mutableListOf()) { it.absolutePathString() }

        return createClasspath(pyFiles)
    }

    @AfterAll
    fun tearDown() {
        if (::sourcesDir.isInitialized) {
            sourcesDir.toFile().deleteRecursively()
        }
    }

    private fun createClasspath(pyFiles: List<String>): PIRClasspath {
        return PIRClasspathLoader(
            PIRSettings(
                sources = pyFiles,
                packageRoots = listOf(sourcesDir.absolutePathString()),
                mypyFlags = listOf("--ignore-missing-imports"),
            )
        ).load()
    }

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
        entryPointFunction: String
    ) {
        val vulnerabilities = runAnalysis(shippedRules(), entryPointFunction)
        assertTrue(vulnerabilities.isNotEmpty(), "Sink was not reached")
    }

    fun assertSinkNotReachable(
        entryPointFunction: String
    ) {
        val vulnerabilities = runAnalysis(shippedRules(), entryPointFunction)
        assertTrue(vulnerabilities.isEmpty(), "Sink should not be reached")
    }

    fun assertSinkReachable(
        source: TestSource,
        sink: TestSink,
        entryPointFunction: String,
        cleaners: List<TestCleaner> = emptyList(),
    ) {
        val vulnerabilities = runAnalysis(source, sink, entryPointFunction, cleaners)
        assertTrue(vulnerabilities.isNotEmpty(), "Sink was not reached")
    }

    fun assertSinkNotReachable(
        source: TestSource,
        sink: TestSink,
        entryPointFunction: String,
        cleaners: List<TestCleaner> = emptyList(),
    ) {
        val vulnerabilities = runAnalysis(source, sink, entryPointFunction, cleaners)
        assertTrue(vulnerabilities.isEmpty(), "Sink should not be reached")
    }

    private fun shippedRules(): PIRTaintRulesProvider = loadDefaultConfig()

    fun runAnalysis(
        source: TestSource,
        sink: TestSink,
        entryPointFunction: String,
        cleaners: List<TestCleaner> = emptyList(),
    ): List<VulnerabilityWithTrace> = runAnalysis(rulesWith(source, sink, cleaners), entryPointFunction)

    fun runAnalysis(
        taintConfig: PIRTaintRulesProvider,
        entryPointFunction: String,
    ): List<VulnerabilityWithTrace> {
        val entryPoint = cp.findFunctionOrNull(entryPointFunction)
            ?: error("Entry point not found")

        val options = TaintAnalyzerOptions(
            ifdsTimeout = 10.minutes,
            ifdsApMode = ApMode.Tree,
        )

        val analyzer = PIRTaintAnalyzer(
            cp, taintConfig, UnitResolver { SingletonUnit }, options, externalMethods,
        )

        return analyzer.use { it.analyzeWithIfds(listOf(entryPoint)).first }
    }

    // region Test-only rule builders
    protected fun source(function: String, mark: String, pos: Position): TestSource =
        TestSource.Method(function, mark, pos)

    protected fun attributeSource(attribute: String, mark: String): TestSource =
        TestSource.Attribute(attribute, mark)

    protected fun sink(function: String, mark: String, pos: Position, id: String): TestSink =
        TestSink(function, mark, pos, id)

    protected fun cleaner(function: String, mark: String, pos: Position): TestCleaner =
        TestCleaner(function, mark, pos)

    private fun rulesWith(source: TestSource, sink: TestSink, cleaners: List<TestCleaner>): PIRTaintRulesProvider =
        PIRCombinedTaintRulesProvider(
            loadDefaultConfig(),
            TestRulesProvider(listOf(source), listOf(sink), cleaners),
            PIRCombinedTaintRulesProvider.CombinationOptions(
                source = PIRCombinedTaintRulesProvider.CombinationMode.EXTEND,
                sink = PIRCombinedTaintRulesProvider.CombinationMode.EXTEND,
            ),
        )
    // endregion
}

sealed interface TestSource {
    fun rulesForMethod(method: PIRFunction): List<TaintSource> = emptyList()
    fun rulesForAttribute(name: String): List<TaintSource> = emptyList()

    data class Method(val function: String, val mark: String, val pos: Position) : TestSource {
        override fun rulesForMethod(method: PIRFunction): List<TaintSource> {
            if (!method.matches(function)) return emptyList()
            return listOf(
                TaintSource(Target.Function(method), mkTrue(), listOf(TaintAssignAction(TaintMark(mark), pos)))
            )
        }
    }

    data class Attribute(val attribute: String, val mark: String) : TestSource {
        override fun rulesForAttribute(name: String): List<TaintSource> {
            if (name != attribute && !name.endsWith(".$attribute")) return emptyList()
            return listOf(
                TaintSource(Target.Attribute(name), mkTrue(), listOf(TaintAssignAction(TaintMark(mark), Result)))
            )
        }
    }
}

data class TestSink(val function: String, val mark: String, val pos: Position, val id: String)

data class TestCleaner(val function: String, val mark: String, val pos: Position)

private class TestRulesProvider(
    private val sources: List<TestSource>,
    private val sinks: List<TestSink>,
    private val cleaners: List<TestCleaner>,
) : PIRTaintRulesProvider {
    override fun sourcesForMethod(method: PIRFunction): List<TaintSource> =
        sources.flatMap { it.rulesForMethod(method) }

    override fun sinksForMethod(method: PIRFunction): List<TaintSink> =
        sinks.filter { method.matches(it.function) }.map {
            TaintSink(
                target = Target.Function(method),
                condition = CommonCondition.Atom(ContainsMark(TaintMark(it.mark), it.pos)),
                trackFactsReachAnalysisEnd = emptyList(),
                id = it.id,
                meta = TaintSinkMeta(it.id, CommonTaintConfigurationSinkMeta.Severity.Warning, cwe = null, note = it.id),
            )
        }

    override fun exitSinksForMethod(method: PIRFunction): List<TaintExitSink> = emptyList()

    override fun entryPointSourcesForMethod(method: PIRFunction): List<TaintEntryPointSource> = emptyList()
    override fun passThroughForMethod(method: PIRFunction, bySimpleName: Boolean): List<TaintPassThrough> = emptyList()
    override fun cleanersForMethod(method: PIRFunction): List<TaintCleaner> =
        cleaners.filter { method.matches(it.function) }.map {
            TaintCleaner(
                target = Target.Function(method),
                condition = mkTrue(),
                cleans = listOf(TaintCleanAction(TaintMark(it.mark), it.pos)),
                forCategory = null,
            )
        }
    override fun sourcesForAttribute(name: String): List<TaintSource> =
        sources.flatMap { it.rulesForAttribute(name) }
    override fun sinksForAttribute(name: String): List<TaintSink> = emptyList()
    override fun passThroughForAttribute(name: String): List<TaintPassThrough> = emptyList()
    override fun cleanersForAttribute(name: String): List<TaintCleaner> = emptyList()
}

private fun PIRFunction.matches(name: String): Boolean {
    val qn = qualifiedName
    val ctorQn = if (enclosingClass != null) qn.removeSuffix(".__init__") else qn
    return qn == name || ctorQn == name
}
