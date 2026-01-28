package org.opentaint.jvm.sast.project

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.jvm.util.JIRSarifTraits
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.DummySerializationContext
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.project.rules.analysisConfig
import org.opentaint.jvm.sast.project.rules.loadSemgrepRules
import org.opentaint.jvm.sast.project.rules.semgrepRulesWithDefaultConfig
import org.opentaint.jvm.sast.sarif.SarifGenerator
import org.opentaint.project.Project
import org.opentaint.semgrep.pattern.SemgrepRuleUtils
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.outputStream

class TestProjectAnalyzer(
    project: Project,
    private val resultDir: Path,
    providedOptions: ProjectAnalysisOptions,
) {
    private val options = providedOptions.copy(storeSummaries = false)
    private val projectAnalysisContext = initializeProjectAnalysisContext(project, options)
    private val rulesWithMetadata = options.loadSemgrepRules()

    @Serializable
    data class RuleInfo(val rulePath: String, val ruleId: String?)

    @Serializable
    data class TestSampleInfo(
        val className: String,
        val methodName: String,
        val rule: RuleInfo
    )

    @Serializable
    data class TestResult(
        val success: List<TestSampleInfo>,
        val falseNegative: List<TestSampleInfo>,
        val falsePositive: List<TestSampleInfo>,
        val skipped: List<TestSampleInfo>,
    )

    fun analyze() {
        projectAnalysisContext.use {
            val testSamples = it.allProjectTestSamples()
            it.analyzeTestSamples(testSamples)
        }
    }

    private fun ProjectAnalysisContext.allProjectTestSamples(): List<TestSample> =
        projectClasses.allProjectClasses()
            .filterNot { it.isAbstract || it.isInterface || it.isAnonymous }
            .flatMapTo(mutableListOf()) { it.declaredMethods }
            .mapNotNull {
                val sample = it.findSampleAnnotation() ?: return@mapNotNull null
                TestSample(it, sample)
            }

    private fun ProjectAnalysisContext.analyzeTestSamples(testSamples: List<TestSample>) {
        val skipped = mutableListOf<TestSample>()

        logger.info { "Select test analysis rules" }

        val testWithRule = mutableListOf<Pair<TestSample, List<TaintRuleFromSemgrep>>>()
        val testGroups = testSamples.groupBy { it.info.rule }
        for ((ruleInfo, testGroup) in testGroups) {
            val rules = selectRules(ruleInfo)
            if (rules.isEmpty()) {
                skipped += testGroup
                continue
            }

            testGroup.mapTo(testWithRule) { it to rules }
        }

        logger.info { "Start test analysis" }

        val results = mutableListOf<Pair<TestSample, List<VulnerabilityWithTrace>>>()
        for ((sample, rules) in testWithRule) {
            val analysisResult = analyzeTestSample(rules, sample)
            results += sample to analysisResult
        }

        generateSarif(results.flatMap { it.second })

        val testResult = generateTestResult(skipped, results)
        writeTestResult(testResult)
    }

    private fun selectRules(info: RuleInfo): List<TaintRuleFromSemgrep> {
        val ruleId = SemgrepRuleUtils.getRuleId(info.rulePath, info.ruleId ?: "")
        val relevantRules = if (info.ruleId != null) {
            rulesWithMetadata.first.filter { it.ruleId == ruleId }
        } else {
            rulesWithMetadata.first.filter { it.ruleId.startsWith(ruleId) }
        }

        return when (relevantRules.size) {
            1 -> relevantRules

            0 -> {
                logger.error { "No rules found for $info" }
                emptyList()
            }

            else -> {
                logger.error { "Multiple rules found for $info" }
                emptyList()
            }
        }
    }

    private fun ProjectAnalysisContext.analyzeTestSample(
        rules: List<TaintRuleFromSemgrep>,
        sample: TestSample
    ): List<VulnerabilityWithTrace> {
        val loadedConfig = rules.semgrepRulesWithDefaultConfig(cp)
        val config = analysisConfig(loadedConfig)

        JIRTaintAnalyzer(
            cp, config,
            projectClasses = { projectClasses.isProjectClass(it) },
            options = options.taintAnalyzerOptions(),
            summarySerializationContext = DummySerializationContext,
        ).use { analyzer ->
            logger.info { "Start IFDS analysis for test: $sample" }
            val traces = analyzer.analyzeWithIfds(listOf(sample.method))
            logger.info { "Finish IFDS analysis for test: $sample" }
            return traces
        }
    }

    private fun generateTestResult(
        skipped: List<TestSample>,
        results: List<Pair<TestSample, List<VulnerabilityWithTrace>>>
    ): TestResult {
        val success = mutableListOf<TestSample>()
        val falseNegative = mutableListOf<TestSample>()
        val falsePositive = mutableListOf<TestSample>()

        for ((test, testResult) in results) {
            when (test.info.kind) {
                SampleKind.POSITIVE -> if (testResult.isEmpty()) {
                    falseNegative += test
                } else {
                    success += test
                }

                SampleKind.NEGATIVE -> if (testResult.isNotEmpty()) {
                    falsePositive += test
                } else {
                    success += test
                }
            }
        }

        return TestResult(
            success = success.map(TestSample::toTestInfo),
            falseNegative = falseNegative.map(TestSample::toTestInfo),
            falsePositive = falsePositive.map(TestSample::toTestInfo),
            skipped = skipped.map(TestSample::toTestInfo),
        )
    }

    private fun ProjectAnalysisContext.generateSarif(traces: List<VulnerabilityWithTrace>) {
        val sourcesResolver = JIRSourceFileResolver(
            project.sourceRoot,
            projectClasses.locationProjectModules.mapValues { (_, module) -> module.moduleSourceRoot }
        )
        val generator = SarifGenerator(sourcesResolver, JIRSarifTraits(cp))
        (resultDir / "report-ifds.sarif").outputStream().use { out ->
            generator.generateSarif(out, traces.asSequence(), rulesWithMetadata.second)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeTestResult(testResult: TestResult) {
        val json = Json { prettyPrint = true }
        (resultDir / "test-result.json").outputStream().use { out ->
            json.encodeToStream(testResult, out)
        }
    }

    private enum class SampleKind {
        POSITIVE, NEGATIVE
    }

    private data class SampleInfo(val kind: SampleKind, val rule: RuleInfo)

    private data class TestSample(val method: JIRMethod, val info: SampleInfo) {
        fun toTestInfo(): TestSampleInfo = TestSampleInfo(
            method.enclosingClass.name, method.name, info.rule
        )
    }

    private fun JIRMethod.findSampleAnnotation(): SampleInfo? {
        val positive = annotations.filter { it.name == POSITIVE_SAMPLE_ANNOTATION_NAME }
        val negative = annotations.filter { it.name == NEGATIVE_SAMPLE_ANNOTATION_NAME }
        val classPositive = enclosingClass.annotations.filter { it.name == POSITIVE_SAMPLE_ANNOTATION_NAME }
        val classNegative = enclosingClass.annotations.filter { it.name == NEGATIVE_SAMPLE_ANNOTATION_NAME }
        val sampleAnnotations = positive + negative + classPositive + classNegative
        if (sampleAnnotations.isEmpty()) return null
        if (sampleAnnotations.size > 1) {
            logger.error { "Multiple sample annotations: $this" }
            return null
        }
        return sampleAnnotations.first().toSampleInfo()
    }

    private fun JIRAnnotation.toSampleInfo(): SampleInfo? {
        val kind = when (name) {
            POSITIVE_SAMPLE_ANNOTATION_NAME -> SampleKind.POSITIVE
            NEGATIVE_SAMPLE_ANNOTATION_NAME -> SampleKind.NEGATIVE
            else -> return null
        }

        val rulePath = values["value"]?.let { it as? String }?.takeIf { it.isNotBlank() }
        val ruleId = values["id"]?.let { it as? String }?.takeIf { it.isNotBlank() }

        if (rulePath == null) {
            logger.error { "Annotation without rule path: $this" }
            return null
        }

        return SampleInfo(kind, RuleInfo(rulePath, ruleId))
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        private const val POSITIVE_SAMPLE_ANNOTATION_NAME = "org.opentaint.sast.test.util.PositiveRuleSample"
        private const val NEGATIVE_SAMPLE_ANNOTATION_NAME = "org.opentaint.sast.test.util.NegativeRuleSample"
    }
}
