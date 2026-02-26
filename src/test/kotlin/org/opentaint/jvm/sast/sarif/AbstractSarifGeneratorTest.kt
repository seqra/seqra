package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.ThreadFlowLocation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.AbstractAstSpanResolverTest
import org.opentaint.jvm.sast.dataflow.DummySerializationContext
import org.opentaint.jvm.sast.dataflow.JIRMethodExitRuleProvider
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.project.SarifGenerationOptions
import org.opentaint.util.analysis.ApplicationGraph
import java.nio.file.Path
import java.util.BitSet

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSarifGeneratorTest: AbstractAstSpanResolverTest() {
    fun functionMatcher(fqn: String, methodName: String) = SerializedFunctionNameMatcher.Simple(
        `package` = SerializedSimpleNameMatcher.Simple(fqn.substringBeforeLast('.')),
        `class` = SerializedSimpleNameMatcher.Simple(fqn.substringAfterLast('.')),
        name = SerializedSimpleNameMatcher.Simple(methodName)
    )

    fun List<Pair<PositionBase, String>>.condition(): SerializedCondition =
        SerializedCondition.and(map {
            SerializedCondition.ContainsMark(it.second, PositionBaseWithModifiers.BaseOnly(it.first))
        })

    fun sourceRule(
        fqn: String,
        methodName: String,
        taintMark: String,
        condition: List<Pair<PositionBase, String>> = emptyList()
    ): SerializedRule.Source = SerializedRule.Source(
        function = functionMatcher(fqn, methodName),
        condition = condition.condition(),
        taint = listOf(
            SerializedTaintAssignAction(
                kind = taintMark,
                pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
            )
        )
    )

    fun entryPointRule(fqn: String, methodName: String, taintMark: String, argIndex: Int) =
        SerializedRule.EntryPoint(
            function = functionMatcher(fqn, methodName),
            taint = listOf(
                SerializedTaintAssignAction(
                    kind = taintMark,
                    pos = PositionBaseWithModifiers.BaseOnly(Argument(argIndex))
                )
            )
        )

    fun sinkRule(
        fqn: String,
        methodName: String,
        ruleId: String,
        condition: List<Pair<PositionBase, String>>
    ): SerializedRule.Sink {
        return SerializedRule.Sink(
            condition = condition.condition(),
            function = functionMatcher(fqn, methodName),
            id = ruleId,
            meta = SinkMetaData(note = "Sink message: $ruleId")
        )
    }

    fun methodExitSinkRule(fqn: String, methodName: String, ruleId: String, mark: String): SerializedRule.MethodExitSink {
        return SerializedRule.MethodExitSink(
            condition = listOf(PositionBase.Result to mark).condition(),
            function = functionMatcher(fqn, methodName),
            id = ruleId
        )
    }

    fun runAnalysis(
        config: SerializedTaintConfig,
        entryPointClass: String,
        entryPointMethod: String
    ): List<VulnerabilityWithTrace> {
        val cls = cp.findClassOrNull(entryPointClass) ?: error("Class $entryPointClass not found in CP")
        val ep = cls.declaredMethods.singleOrNull { it.name == entryPointMethod }
            ?: error("No $entryPointMethod method in $entryPointClass")

        val taintConfig = TaintConfiguration(cp)
        taintConfig.loadConfig(config)

        var rulesProvider: TaintRulesProvider = JIRTaintRulesProvider(taintConfig)
        rulesProvider = JIRMethodExitRuleProvider(rulesProvider)

        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        val ifdsGraph = JIRSafeApplicationGraph(mainGraph)

        @Suppress("UNCHECKED_CAST")
        val engine = TaintAnalysisUnitRunnerManager(
            JIRAnalysisManager(cp),
            ifdsGraph as ApplicationGraph<CommonMethod, CommonInst>,
            unitResolver = JIRUnitResolver {
                if (it.enclosingClass.declaration.location == cls.declaration.location) {
                    return@JIRUnitResolver SingletonUnit
                }

                UnknownUnit
            } as UnitResolver<CommonMethod>,
            apManager = TreeApManager(anyAccessorUnrollStrategy = AnyAccessorUnrollStrategy.AnyAccessorDisabled),
            summarySerializationContext = DummySerializationContext,
            taintConfig = rulesProvider,
            taintRulesStatsSamplingPeriod = null,
        )

        return engine.use { eng ->
            eng.runAnalysis(listOf(ep), timeout = 1.minutes, cancellationTimeout = 10.seconds)

            val allVulnerabilities = eng.getVulnerabilities()
            val confirmed = eng.confirmVulnerabilities(
                setOf(ep), allVulnerabilities,
                timeout = 1.minutes, cancellationTimeout = 10.seconds
            )
            eng.resolveVulnerabilityTraces(
                setOf(ep), confirmed,
                resolverParams = TraceResolver.Params(),
                timeout = 1.minutes, cancellationTimeout = 10.seconds
            ).filter { it.trace?.sourceToSinkTrace?.startNodes?.isNotEmpty() ?: false }
        }
    }

    data class SarifData(
        val resultLocations: List<Location>,
        val threadFlowLocations: List<ThreadFlowLocation>
    )

    fun generateSarifReport(traces: List<VulnerabilityWithTrace>): SarifData {
        val locs = cp.registeredLocations.filter { !it.isRuntime }
        val sourceFileResolver = JIRSourceFileResolver(sourcesDir, locs.associateWith { sourcesDir })
        val options = SarifGenerationOptions()

        val generator = SarifGenerator(
            options = options,
            sourceRoot = sourcesDir,
            sourceFileResolver = sourceFileResolver,
            traits = traits
        )

        val sarif = generator.generateSarif(traces.asSequence(), emptyList())

        val results = sarif.runs.flatMap { it.results }
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
        val sourcePath = getSourcePath(testCls)
        val expected = expectedLocations.map { parseExpectedLocation(sourcePath, it) }

        assertSarifTraceLocationsMatch(sarifData, expected, testName)
    }
}
