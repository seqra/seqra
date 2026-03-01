package org.opentaint.jvm.sast.ast

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.jvm.sast.project.ProjectAnalysisContext
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.jvm.sast.project.initializeProjectAnalysisContext
import org.opentaint.jvm.sast.sarif.InstructionInfo
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.LocationType
import org.opentaint.jvm.sast.sarif.TracePathNode
import org.opentaint.jvm.sast.sarif.TracePathNodeKind
import org.opentaint.project.Project
import org.opentaint.project.ProjectModuleClasses
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasicTestUtils {

    protected lateinit var context: ProjectAnalysisContext
    protected lateinit var traits: JIRSarifTraits
    protected lateinit var sourcesDir: Path
    protected lateinit var samplesJar: Path
    protected lateinit var dependencyJars: List<Path>

    protected abstract val sourceFileExtension: String

    val cp: JIRClasspath get() = context.cp

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        val dependencyJarPath = System.getenv("TEST_DEPENDENCIES_JAR")
            ?: error("TEST_DEPENDENCIES_JAR environment variable not set. Run tests via Gradle.")

        samplesJar = Path(jarPath)
        dependencyJars = dependencyJarPath.split(File.pathSeparator).map { Path(it) }

        sourcesDir = createTempDirectory("span-resolver-sources")
        extractSourcesFromJar(samplesJar, sourcesDir)

        val project = Project(
            sourceRoot = sourcesDir,
            modules = listOf(
                ProjectModuleClasses(
                    moduleSourceRoot = sourcesDir,
                    moduleClasses = listOf(samplesJar)
                )
            ),
            dependencies = dependencyJars
        )

        val options = ProjectAnalysisOptions()
        context = initializeProjectAnalysisContext(project, options)

        traits = JIRSarifTraits(cp)
    }

    @AfterAll
    fun tearDown() {
        if (::context.isInitialized) context.close()
        if (::sourcesDir.isInitialized) {
            sourcesDir.toFile().deleteRecursively()
        }
    }

    private fun extractSourcesFromJar(jarPath: Path, targetDir: Path) {
        JarFile(jarPath.toFile()).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".java") || it.name.endsWith(".kt") }
                .forEach { entry ->
                    val targetFile = targetDir.resolve(entry.name)
                    targetFile.parent.createDirectories()
                    jar.getInputStream(entry).use { input ->
                        targetFile.writeText(input.bufferedReader().readText())
                    }
                }
        }
    }

    protected fun findClass(name: String) = cp.findClassOrNull(name)
        ?: error("Class $name not found")

    protected fun findMethod(className: String, methodName: String) =
        findClass(className).declaredMethods.find { it.name == methodName }
            ?: error("Method $methodName not found in $className")

    protected inline fun <reified T> findInstruction(instructions: Iterable<*>, predicate: (T) -> Boolean): T? =
        instructions.filterIsInstance<T>().find(predicate)

    protected inline fun <reified T> getInstructionsOfType(className: String, methodName: String): List<T> =
        findMethod(className, methodName).instList.filterIsInstance<T>()

    protected fun getSourcePath(className: String): Path {
        val packagePath = className.substringBeforeLast('.').replace('.', '/')
        val simpleName = className.substringAfterLast('.')
        return sourcesDir.resolve("$packagePath/$simpleName.$sourceFileExtension")
    }

    protected fun createIntermediateLocation(inst: JIRInst, type: LocationType = LocationType.Simple): IntermediateLocation {
        val lineNumber = inst.lineNumber
        val method = inst.location.method
        val info = InstructionInfo(
            fullyQualified = "${method.enclosingClass.name}.${method.name}",
            machineName = method.name,
            lineNumber = lineNumber
        )
        return IntermediateLocation(inst, info, "test", null, type, null, null)
    }

    protected fun createMethodExitLocation(inst: JIRReturnInst): IntermediateLocation {
        val method = inst.location.method
        val info = InstructionInfo(
            fullyQualified = "${method.enclosingClass.name}.${method.name}",
            machineName = method.name,
            lineNumber = inst.lineNumber
        )
        val finalEntry = MethodTraceResolver.TraceEntry.Final(
            edges = emptySet(),
            statement = inst
        )
        val traceNode = TracePathNode(
            statement = inst,
            kind = TracePathNodeKind.OTHER,
            entry = finalEntry
        )
        return IntermediateLocation(inst, info, "test", null, LocationType.Simple, null, traceNode)
    }

    data class MarkedSpan(
        val markerId: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val message: String?
    )

    private data class StartMarker(val id: String, val line: Int, val column: Int)
    private data class EndMarker(val id: String, val line: Int, val column: Int, val message: String?)

    private val markerCache = mutableMapOf<Path, Map<String, MarkedSpan>>()

    private val COMMENT_PATTERN = Regex("""/\*([^*]+)\*/""")
    private val ENTRY_SEPARATOR = "|<$>|"
    private val ENTRY_PATTERN = Regex("""([^:]+):(start|end)(?::(.*))?""")

    protected fun parseSpanMarker(sourcePath: Path, spanId: String): MarkedSpan {
        val spans = markerCache.getOrPut(sourcePath) { parseAllMarkers(sourcePath) }
        return spans[spanId]
            ?: error("Span marker '$spanId' not found in $sourcePath. Available markers: ${spans.keys}")
    }

    private fun parseAllMarkers(sourcePath: Path): Map<String, MarkedSpan> {
        val lines = sourcePath.toFile().readLines()
        val startMarkers = mutableMapOf<String, StartMarker>()
        val endMarkers = mutableMapOf<String, EndMarker>()

        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1

            for (commentMatch in COMMENT_PATTERN.findAll(line)) {
                val commentContent = commentMatch.groupValues[1]
                val commentStart = commentMatch.range.first
                val commentEnd = commentMatch.range.last + 1

                for (entry in commentContent.split(ENTRY_SEPARATOR)) {
                    val entryMatch = ENTRY_PATTERN.matchEntire(entry.trim()) ?: continue
                    val id = entryMatch.groupValues[1]
                    val type = entryMatch.groupValues[2]
                    val message = entryMatch.groupValues[3].takeIf { it.isNotEmpty() }

                    when (type) {
                        "start" -> startMarkers[id] = StartMarker(id, lineNumber, commentEnd + 1)
                        "end" -> endMarkers[id] = EndMarker(id, lineNumber, commentStart, message)
                    }
                }
            }
        }

        val result = mutableMapOf<String, MarkedSpan>()
        for ((id, start) in startMarkers) {
            val end = endMarkers[id]
                ?: error("Missing end marker for '$id' in $sourcePath")
            result[id] = MarkedSpan(
                markerId = id,
                startLine = start.line,
                startColumn = start.column,
                endLine = end.line,
                endColumn = end.column,
                message = end.message
            )
        }

        return result
    }

    protected fun assertSpanMatchesMarker(span: LocationSpan?, expectedSpan: MarkedSpan) {
        assertNotNull(span, "Span should not be null")
        span!!
        assertEquals(expectedSpan.startLine, span.startLine, "Start line mismatch")
        assertEquals(expectedSpan.startColumn, span.startColumn, "Start column mismatch")
        assertEquals(expectedSpan.endLine, span.endLine, "End line mismatch")
        assertEquals(expectedSpan.endColumn, span.endColumn, "End column mismatch")
    }
}
