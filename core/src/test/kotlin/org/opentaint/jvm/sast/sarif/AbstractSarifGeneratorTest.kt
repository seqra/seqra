package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.ThreadFlowLocation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance
import org.opentaint.common.sast.sarif.SarifGenerationOptions
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.AnalysisTest
import java.nio.file.Path
import java.util.BitSet

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSarifGeneratorTest: AnalysisTest() {
    data class SarifData(
        val resultLocations: List<Location>,
        val threadFlowLocations: List<ThreadFlowLocation>
    )

    fun generateSarifReport(traces: List<VulnerabilityWithTrace>): SarifData {
        val locs = cp.registeredLocations.filter { !it.isRuntime }
        val sourceFileResolver = JIRSourceFileResolver(sourcesDir, locs.associateWith { sourcesDir })
        val options = SarifGenerationOptions()

        val generator = JirSarifGenerator(
            options = options,
            sourceRoot = sourcesDir,
            sourceFileResolver = sourceFileResolver,
            traits = traits
        )

        val sarif = generator.generateSarif(traces.asSequence(), emptyList())

        val results = sarif.results.toList()
        val resultLocations = results.flatMap { it.locations.orEmpty() }
        val threadFlowLocations = results
            .flatMap { it.codeFlows.orEmpty() }
            .flatMap { it.threadFlows }
            .flatMap { it.locations }

        return SarifData(resultLocations, threadFlowLocations)
    }

    fun parseExpectedLocation(sourcePath: Path, markerId: String): MarkedSpan =
        parseSpanMarker(sourcePath, markerId)

    fun Region.match(span: MarkedSpan): Boolean =
        startLine?.toInt() == span.startLine
                && endLine?.toInt() == span.endLine
                && startColumn?.toInt() == span.startColumn
                && (endColumn?.toInt() == span.endColumn || endColumn?.toInt() == span.endColumn.plus(1))

    fun MarkedSpan.printRegion(): String {
        return "L${startLine}:${startColumn}-L${endLine}:${endColumn + 1}"
    }

    fun Location.printRegion(): String {
        val r = physicalLocation?.region
        return "L${r?.startLine}:${r?.startColumn}-L${r?.endLine}:${r?.endColumn}"
    }

    fun Location.message(): String? = message?.text

    fun List<Location>.printLocations(): String =
        joinToString(separator = "\n") { "${it.printRegion()}:${it.message()}" }

    fun assertSarifTraceLocationsMatch(
        sarifData: SarifData,
        expectedLocations: List<MarkedSpan>,
        testName: String,
    ) {
        assertTrue(sarifData.resultLocations.isNotEmpty(), "$testName: Expected SARIF result to contain locations")

        val allLocations = sarifData.threadFlowLocations.mapNotNull { it.location }
        val coveredLocations = BitSet()

        println("All trace locations:\n${allLocations.printLocations()}")

        for (expected in expectedLocations) {
            val expectedMessage = expected.message ?: ""
            var matchingLocationIdx = -1
            for (i in allLocations.indices) {
                if (coveredLocations.get(i)) continue

                val loc = allLocations[i]
                if (loc.physicalLocation?.region?.match(expected) != true) continue
                if (loc.message() != expectedMessage) continue

                matchingLocationIdx = i
                break
            }

            assertTrue(
                matchingLocationIdx != -1,
                "$testName: Expected marker ${expected.markerId} ${expected.printRegion()} with message \"${expected.message}\" not found. " +
                        "Found regions:\n${allLocations.printLocations()}"
            )

            coveredLocations.set(matchingLocationIdx)
        }

        val uncoveredLocations = allLocations.filterIndexed { idx, _ -> !coveredLocations.get(idx) }
        assertTrue(
            uncoveredLocations.isEmpty(),
            "$testName: Uncovered locations found:\n${uncoveredLocations.printLocations()}"
        )
    }

    fun runTest(
        config: SerializedTaintConfig,
        expectedLocations: List<String>,
        testCls: String,
        entryPointName: String,
        testName: String,
    ) {
        val traces = runAnalysis(config, testCls, entryPointName)
        assertTrue(traces.isNotEmpty(), "Expected at least one vulnerability trace")

        val sarifData = generateSarifReport(traces)
        if (useBidiEntry) {
            val sinkMarker = expectedLocations.single { "sink" in it }
            val expectedSink = parseExpectedLocation(getSourcePath(testCls), sinkMarker)
            assertTrue(traces.all { it.trace === TracePathGenerationResult.Simple },
                "$testName: Expected phase-3 simple traces")
            assertTrue(sarifData.resultLocations.isNotEmpty(),
                "$testName: Expected SARIF sink locations")
            assertTrue(sarifData.resultLocations.all { it.physicalLocation?.region?.startLine != null },
                "$testName: Expected sink locations with source lines")
            assertTrue(sarifData.resultLocations.any { it.physicalLocation?.region?.match(expectedSink) == true },
                "$testName: Expected sink $sinkMarker ${expectedSink.printRegion()}. " +
                    "Found:\n${sarifData.resultLocations.printLocations()}")
            assertTrue(sarifData.threadFlowLocations.isEmpty(),
                "$testName: Simple traces must not contain code-flow locations")
            return
        }
        val sourcePath = getSourcePath(testCls)
        val expected = expectedLocations.map { parseExpectedLocation(sourcePath, it) }

        assertSarifTraceLocationsMatch(sarifData, expected, testName)
    }
}
