package org.opentaint.go.sast.project

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.Serializable
import mu.KLogging
import org.opentaint.common.sast.ProjectAnalysisStatus
import org.opentaint.common.sast.rules.loadSemgrepRules
import org.opentaint.common.sast.test.ProjectAnalysisTestResults
import org.opentaint.common.sast.test.RuleTest
import org.opentaint.common.sast.test.RuleTests
import org.opentaint.common.sast.test.TestProjectAnalyzerBase
import org.opentaint.common.sast.test.TestResult
import org.opentaint.common.sast.test.TestSampleInfo
import org.opentaint.common.sast.toProjectStatus
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.go.serialized.GoConfigurationLoader
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.go.config.GoDefaultConfigLoader
import org.opentaint.go.sast.dataflow.GoTaintAnalyzer
import org.opentaint.go.sast.dataflow.GoUnitResolver
import org.opentaint.go.sast.project.GoTestProjectAnalyzer.GoTestSampleInfo
import org.opentaint.go.sast.project.GoTestProjectAnalyzer.RuleSelectResult.Rule
import org.opentaint.go.sast.sarif.GoDebugFactReachabilitySarifGenerator
import org.opentaint.go.sast.sarif.GoSarifGenerator
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.client.GoIRClient
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.util.io.inputStream
import org.opentaint.project.GoProject
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy
import org.opentaint.semgrep.pattern.RuleMetadata
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import kotlin.io.path.exists
import kotlin.reflect.KClass

class GoTestProjectAnalyzer(
    project: GoProject,
    results: ProjectAnalysisTestResults,
    providedOptions: GoProjectAnalysisOptions,
) : TestProjectAnalyzerBase<GoTestSampleInfo, AnalysisCtx, GoProject, GoIRFunction, GoIRInst, GoSerializedItem, GoSerializedTaintConfig>(
    project, results, providedOptions.common.copy(storeSummaries = false),
) {
    private val modelPaths = providedOptions.modelPaths
    private val useDefaultModels = providedOptions.useDefaultModels
    private val loadedRules = options.loadSemgrepRules(GoLanguageStrategy())

    @Serializable
    data class GoTestSampleInfo(
        val function: String,
        val ruleId: String,
        override val language: String,
        override val testSetName: String,
    ) : TestSampleInfo

    override fun testInfoCls(): KClass<GoTestSampleInfo> = GoTestSampleInfo::class
    override fun testInfoSerializer() = GoTestSampleInfo.serializer()
    override fun defaultConfigLoader() = GoDefaultConfigLoader
    override fun configLoader() = GoConfigurationLoader()

    private data class RuleTestSample(
        val ruleId: String,
        val kind: SampleKind,
        val entryPoint: GoIRFunction,
    )

    private var status: ProjectAnalysisStatus = ProjectAnalysisStatus.OK

    override fun analyze(): ProjectAnalysisStatus {
        val ruleTestsFile = project.projectDir.resolve("rule-test.yaml")
        if (!ruleTestsFile.exists()) {
            logger.error("No test file in ${project.projectDir}")
            return ProjectAnalysisStatus.EXCEPTION
        }

        val ruleTests = ruleTestsFile.inputStream().use {
            Yaml().decodeFromStream<RuleTests>(it)
        }

        val ctx = AnalysisCtx(project, GoIRClient(), modelPaths, useDefaultModels)

        val results = ctx.analyzeTestSamples(ruleTests.tests)
            ?: return ProjectAnalysisStatus.EXCEPTION

        this.results.testResults += project to results

        return status
    }

    private fun AnalysisCtx.analyzeTestSamples(tests: List<RuleTest>): TestResult<GoTestSampleInfo>? {
        val skipped = mutableListOf<RuleTest>()
        val disabled = mutableListOf<RuleTest>()

        logger.info { "Select test analysis rules" }

        val testWithRule = mutableListOf<Pair<RuleTestSample, Rule>>()
        val testGroups = tests.groupBy { it.ruleId }
        for ((ruleInfo, testGroup) in testGroups) {
            val rules = when (val result = selectRules(ruleInfo)) {
                RuleSelectResult.MultipleRules, RuleSelectResult.NoRules -> {
                    skipped += testGroup
                    continue
                }

                RuleSelectResult.RuleDisabled -> {
                    disabled += testGroup
                    continue
                }

                is Rule -> result
            }

            val samples = mutableListOf<RuleTestSample>()
            testGroup.forEach { group ->
                group.positive.mapTo(samples) {
                    val ep = resolveEntryPoint(it.entrypoint) ?: return null
                    RuleTestSample(ruleInfo, SampleKind.POSITIVE, ep)
                }
                group.negative.mapTo(samples) {
                    val ep = resolveEntryPoint(it.entrypoint) ?: return null
                    RuleTestSample(ruleInfo, SampleKind.NEGATIVE, ep)
                }
            }

            samples.mapTo(testWithRule) { it to rules }
        }

        logger.info { "Start test analysis" }

        val results = mutableListOf<Triple<RuleTestSample, AnalysisResult, Rule>>()
        for ((sample, rule) in testWithRule) {
            val analysisResult = analyzeTestSample(listOf(rule.rule), sample)

            status = maxOf(status, analysisResult.status.toProjectStatus())
            results += Triple(sample, analysisResult, rule)
        }

        for ((_, result, rule) in results) {
            generateReportFromAnalysisResult(result, listOf(rule.meta))
        }

        return generateTestResult(skipped, disabled, results)
    }

    private fun AnalysisCtx.resolveEntryPoint(name: String): GoIRFunction? =
        cp.allFunctions().firstOrNull { it.fullName == name }

    private sealed interface RuleSelectResult {
        data class Rule(
            val rule: TaintRuleFromSemgrep<GoSerializedItem>,
            val meta: RuleMetadata,
        ) : RuleSelectResult

        data object MultipleRules : RuleSelectResult
        data object NoRules : RuleSelectResult
        data object RuleDisabled : RuleSelectResult
    }

    private fun selectRules(testRuleId: String): RuleSelectResult {
        val loadedRuleId = testRuleId.replace('#', ':')

        val relevantRules = loadedRules.rulesWithMeta.filter { it.first.ruleId == loadedRuleId }
        return when (relevantRules.size) {
            1 -> {
                val relevantRule = relevantRules.first()
                @Suppress("UNCHECKED_CAST")
                Rule(
                    relevantRule.first as TaintRuleFromSemgrep<GoSerializedItem>,
                    relevantRule.second
                )
            }

            0 -> {
                if (loadedRules.disabledRules.any { it == loadedRuleId }) {
                    logger.info { "Rule $testRuleId disabled" }
                    return RuleSelectResult.RuleDisabled
                }

                logger.error { "No rules found for $testRuleId" }
                RuleSelectResult.NoRules
            }

            else -> {
                logger.error { "Multiple rules found for $testRuleId" }
                RuleSelectResult.MultipleRules
            }
        }
    }

    private fun AnalysisCtx.analyzeTestSample(
        rules: List<TaintRuleFromSemgrep<GoSerializedItem>>, sample: RuleTestSample
    ): AnalysisResult {
        val rulesProvider = approximations.copy(rules = rules).loadRules()

        val analyzer = GoTaintAnalyzer(
            cp,
            rulesProvider,
            GoUnitResolver(),
            options.taintAnalyzerOptions(),
        )
        return runAnalyzerWithTraceResolver(analyzer, listOf(sample.entryPoint))
    }

    private enum class SampleKind {
        POSITIVE, NEGATIVE
    }

    private fun generateTestResult(
        skipped: List<RuleTest>, disabled: List<RuleTest>,
        results: List<Triple<RuleTestSample, AnalysisResult, Rule>>
    ): TestResult<GoTestSampleInfo> {
        val success = mutableListOf<RuleTestSample>()
        val falseNegative = mutableListOf<RuleTestSample>()
        val falsePositive = mutableListOf<RuleTestSample>()

        for ((test, testResult, _) in results) {
            when (test.kind) {
                SampleKind.POSITIVE -> if (testResult.traces.isEmpty()) {
                    falseNegative += test
                } else {
                    success += test
                }

                SampleKind.NEGATIVE -> if (testResult.traces.isNotEmpty()) {
                    falsePositive += test
                } else {
                    success += test
                }
            }
        }

        return TestResult(
            success = success.toRuleTest(),
            falseNegative = falseNegative.toRuleTest(),
            falsePositive = falsePositive.toRuleTest(),
            skipped = skipped.flatMap { it.toTestInfo() },
            disabled = disabled.flatMap { it.toTestInfo() },
        )
    }

    private fun List<RuleTestSample>.toRuleTest() = sortedBy { it.ruleId }.map {
        GoTestSampleInfo(it.entryPoint.fullName, it.ruleId, language = "go", testSetName = "default")
    }

    private fun RuleTest.toTestInfo(): List<GoTestSampleInfo> {
        val allSamples = positive + negative
        return allSamples.map { GoTestSampleInfo(it.entrypoint, ruleId, language = "go", testSetName = "default") }
    }

    override fun AnalysisCtx.sarifGenerator() =
        GoSarifGenerator(options.sarifGenerationOptions, project.sourceRoot())

    override fun AnalysisCtx.debugSarifGenerator() =
        GoDebugFactReachabilitySarifGenerator(options.sarifGenerationOptions, project.sourceRoot())

    override fun AnalysisCtx.runSeAnalyzer(
        engine: TaintAnalysisUnitRunnerManager,
        traces: List<VulnerabilityWithTrace>
    ): List<VulnerabilityWithTrace> {
        TODO("Not yet implemented")
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
