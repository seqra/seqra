package org.opentaint.jvm.sast.ast

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.opentaintIrDb
import org.opentaint.jvm.sast.sarif.InstructionInfo
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.LocationType
import org.opentaint.jvm.sast.sarif.TracePathNode
import org.opentaint.jvm.sast.sarif.TracePathNodeKind
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractAstSpanResolverTest {

    protected lateinit var db: JIRDatabase
    protected lateinit var cp: JIRClasspath
    protected lateinit var traits: JIRSarifTraits
    protected lateinit var sourcesDir: Path
    protected lateinit var samplesJar: Path

    protected abstract val sourceFileExtension: String

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        samplesJar = Path(jarPath)

        sourcesDir = createTempDirectory("span-resolver-sources")
        extractSourcesFromJar(samplesJar, sourcesDir)

        db = runBlocking {
            opentaintIrDb {
                loadByteCode(listOf(samplesJar.toFile()))
                useProcessJavaRuntime()
                persistenceImpl(JIRRamErsSettings)
                installFeatures(InMemoryHierarchy())
                installFeatures(Usages)
                keepLocalVariableNames()
            }.also { it.awaitBackgroundJobs() }
        }

        cp = runBlocking { db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses)) }
        traits = JIRSarifTraits(cp)
    }

    @AfterAll
    fun tearDown() {
        if (::cp.isInitialized) cp.close()
        if (::db.isInitialized) db.close()
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

    protected data class MarkedSpan(
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int
    )

    protected fun parseSpanMarker(sourcePath: Path, spanId: String): MarkedSpan {
        val startMarker = "/*$spanId:start*/"
        val endMarker = "/*$spanId:end*/"
        val lines = sourcePath.toFile().readLines()

        var startLine: Int? = null
        var startColumn: Int? = null
        var endLine: Int? = null
        var endColumn: Int? = null

        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1

            val startIdx = line.indexOf(startMarker)
            if (startIdx >= 0) {
                startLine = lineNumber
                startColumn = startIdx + startMarker.length + 1
            }

            val endIdx = line.indexOf(endMarker)
            if (endIdx >= 0) {
                endLine = lineNumber
                endColumn = endIdx
            }
        }

        check(startLine != null && startColumn != null && endLine != null && endColumn != null) {
            "Span marker '$spanId' not found in $sourcePath"
        }

        return MarkedSpan(startLine, startColumn, endLine, endColumn)
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
