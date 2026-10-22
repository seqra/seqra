package org.opentaint.jvm.sast.dataflow

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.config.JavaDefaultConfigLoader
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApMode
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
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.DataFlowApproximationLoader.isApproximation
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AnalysisTest : BasicTestUtils() {
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

    fun methodExitSinkRule(
        fqn: String,
        methodName: String,
        ruleId: String,
        mark: String
    ): SerializedRule.MethodExitSink {
        return SerializedRule.MethodExitSink(
            condition = listOf(PositionBase.Result to mark).condition(),
            function = functionMatcher(fqn, methodName),
            id = ruleId
        )
    }

    open val useDefaultConfig = false

    open val apMode: ApMode = ApMode.Tree

    open val analysisUnrollStrategy: AnyAccessorUnrollStrategy = AnyAccessorUnrollStrategy.AnyAccessorDisabled

    private class SingleLocationUnit(val loc: RegisteredLocation) : JIRUnitResolver {
        override fun resolve(method: JIRMethod): UnitType {
            if (method.enclosingClass.declaration.location == loc || isApproximation(method)) {
                return SingletonUnit
            }

            return UnknownUnit
        }

        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != this.loc
    }

    private val defaultConfig by lazy {
        JavaDefaultConfigLoader.loadConfig()
    }

    fun runAnalysis(
        config: SerializedTaintConfig,
        entryPointClass: String,
        entryPointMethod: String,
        fieldLimit: Int? = null,
    ): List<VulnerabilityWithTrace> {
        val cls = cp.findClassOrNull(entryPointClass) ?: error("Class $entryPointClass not found in CP")
        val ep = cls.declaredMethods.singleOrNull { it.name == entryPointMethod }
            ?: error("No $entryPointMethod method in $entryPointClass")

        val taintConfig = TaintConfiguration(cp)
        taintConfig.loadConfig(config)

        if (useDefaultConfig) {
            val defaultPassRules = SerializedTaintConfig(passThrough = defaultConfig?.passThrough)
            taintConfig.loadConfig(defaultPassRules)
        }

        var rulesProvider: TaintRulesProvider = JIRTaintRulesProvider(taintConfig)
        rulesProvider = JIRMethodExitRuleProvider(rulesProvider)

        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        val ifdsGraph = JIRSafeApplicationGraph(JApplicationSingleExitGraph(mainGraph))

        val options = TaintAnalyzerOptions(
            ifdsTimeout = 1.minutes,
            ifdsApMode = apMode,
            fieldLimit = fieldLimit,
        )

        val analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(options) {
            override val unrollStrategy: AnyAccessorUnrollStrategy
                get() = analysisUnrollStrategy

            override fun analysisGraph(): ApplicationGraph<JIRMethod, JIRInst> = ifdsGraph
            override fun analysisManager() = JIRAnalysisManager(cp, refManager, rulesProvider)
            override fun unitResolver() = SingleLocationUnit(cls.declaration.location)
        }

        return analyzer.use {
            it.analyzeWithIfds(listOf(ep)).first
        }
    }

    fun assertReachable(
        config: SerializedTaintConfig,
        testCls: String,
        entryPointName: String,
        ruleId: String,
        testName: String,
        fieldLimit: Int? = null,
    ) {
        val traces = runAnalysis(config, testCls, entryPointName, fieldLimit)
        assertTrue(traces.isNotEmpty(), "$testName: expected taint to reach the sink, but no vulnerability was found")
        traces.forEach { vt ->
            assertEquals(
                ruleId, vt.vulnerability.rule.id,
                "$testName: unexpected rule id in vulnerability"
            )
        }
    }

    fun assertNotReachable(
        config: SerializedTaintConfig,
        testCls: String,
        entryPointName: String,
        testName: String,
        fieldLimit: Int? = null,
    ) {
        val traces = runAnalysis(config, testCls, entryPointName, fieldLimit)
        assertTrue(traces.isEmpty(), "$testName: expected no vulnerability, but found ${traces.size}")
    }
}
