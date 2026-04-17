package org.opentaint.jvm.sast.project

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.project.rules.analysisConfig
import org.opentaint.jvm.sast.project.rules.loadSemgrepRules
import org.opentaint.jvm.sast.project.rules.semgrepRulesWithDefaultConfig
import org.opentaint.jvm.sast.project.spring.springWebProjectEntryPoints
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.jvm.sast.sarif.SarifGenerator
import org.opentaint.jvm.sast.util.locationChecker
import org.opentaint.project.Project
import org.opentaint.semgrep.pattern.SemgrepRuleUtils
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo

class TestProjectAnalyzer(
    project: Project,
    private val resultDir: Path,
    providedOptions: ProjectAnalysisOptions,
) {
    private val options = providedOptions.copy(storeSummaries = false)
    private val projectAnalysisContexts = initializeProjectModulesAnalysisContexts(project, options)
    private val loadedRules = options.loadSemgrepRules()

    @Serializable
    data class RuleInfo(val rulePath: String, val ruleId: String?)

    @Serializable
    data class TestSampleInfo(
        val className: String,
        val methodName: String?,
        val rule: RuleInfo
    )

    @Serializable
    data class TestResult(
        val success: List<TestSampleInfo>,
        val falseNegative: List<TestSampleInfo>,
        val falsePositive: List<TestSampleInfo>,
        val skipped: List<TestSampleInfo>,
        val disabled: List<TestSampleInfo>,
    )

    private var status: ProjectAnalysisStatus = ProjectAnalysisStatus.OK

    fun analyze(): ProjectAnalysisStatus {
        val results = projectAnalysisContexts.map { (module, ctx) ->
            val testSetName = ctx.project.sourceRoot?.let { srcRoot ->
                module.moduleSourceRoot?.relativeTo(srcRoot)?.toString()
            }.orEmpty().replace('/', '-')

            val testSamples = ctx.allProjectTestSamples(testSetName)
            ctx.analyzeTestSamples(testSamples, testSetName)
        }

        writeTestResult(results.joinResults())

        return status
    }

    private fun ProjectAnalysisContext.allProjectTestSamples(testSetName: String): List<TestSample> {
        val samples = mutableListOf<TestSample>()

        val classes = projectClasses.allProjectClasses()
            .filterNotTo(mutableListOf()) { it.isAbstract || it.isInterface || it.isAnonymous }

        classes.mapNotNullTo(samples) { cls ->
            val sample = cls.findSampleAnnotation() ?: return@mapNotNullTo null
            ClassTestSample(cls, cls.declaredMethods, sample)
        }

        classes.flatMapTo(mutableListOf()) { it.declaredMethods }
            .mapNotNullTo(samples) {
                val sample = it.findSampleAnnotation() ?: return@mapNotNullTo null
                MethodTestSample(it, sample)
            }

        if (!testSetName.isSpringAppTestSet()) return samples

        logger.info { "Detect spring test set: $testSetName" }

        val springEp = springWebProjectContext?.springWebProjectEntryPoints()?.takeIf { it.isNotEmpty() }
        if (springEp == null) {
            logger.error { "No spring entry point found: $testSetName" }
            return samples
        }

        return samples.map { SpringTestSample(springEp, it) }
    }

    private fun ProjectAnalysisContext.analyzeTestSamples(testSamples: List<TestSample>, testSetName: String): TestResult {
        val skipped = mutableListOf<TestSample>()
        val disabled = mutableListOf<TestSample>()

        logger.info { "Select test analysis rules" }

        val testWithRule = mutableListOf<Pair<TestSample, List<TaintRuleFromSemgrep>>>()
        val testGroups = testSamples.groupBy { it.info.rule }
        for ((ruleInfo, testGroup) in testGroups) {
            val rules = when (val result = selectRules(ruleInfo)) {
                RuleSelectResult.MultipleRules,
                RuleSelectResult.NoRules -> {
                    skipped += testGroup
                    continue
                }
                RuleSelectResult.RuleDisabled -> {
                    disabled += testGroup
                    continue
                }
                is RuleSelectResult.Rules -> result.rules
            }

            testGroup.mapTo(testWithRule) { it to rules }
        }

        logger.info { "Start test analysis" }

        val results = mutableListOf<Pair<TestSample, List<VulnerabilityWithTrace>>>()
        for ((sample, rules) in testWithRule) {
            val (analysisResult, analysisStatus) = analyzeTestSample(rules, sample)

            status = maxOf(status, analysisStatus.toProjectStatus())
            results += sample to analysisResult
        }

        generateSarif(results.flatMap { it.second }, testSetName)

        return generateTestResult(skipped, disabled, results)
    }

    private sealed interface RuleSelectResult {
        data class Rules(val rules: List<TaintRuleFromSemgrep>): RuleSelectResult
        data object MultipleRules: RuleSelectResult
        data object NoRules: RuleSelectResult
        data object RuleDisabled: RuleSelectResult
    }

    private fun RuleInfo.ruleIdMatcher(): (String) -> Boolean {
        val ruleId = SemgrepRuleUtils.getRuleId(rulePath, ruleId ?: "")
        if (this.ruleId != null) {
            return { s: String -> s == ruleId }
        } else {
            return { s: String -> s.startsWith(ruleId) }
        }
    }

    private fun selectRules(info: RuleInfo): RuleSelectResult {
        val ruleIdMatcher = info.ruleIdMatcher()
        val relevantRules = loadedRules.rulesWithMeta.filter { ruleIdMatcher(it.first.ruleId) }

        return when (relevantRules.size) {
            1 -> RuleSelectResult.Rules(relevantRules.map { it.first })

            0 -> {
                if (loadedRules.disabledRules.any { ruleIdMatcher(it) }){
                    logger.info { "Rule $info disabled" }
                    return RuleSelectResult.RuleDisabled
                }

                logger.error { "No rules found for $info" }
                RuleSelectResult.NoRules
            }

            else -> {
                logger.error { "Multiple rules found for $info" }
                RuleSelectResult.MultipleRules
            }
        }
    }

    private fun ProjectAnalysisContext.analyzeTestSample(
        rules: List<TaintRuleFromSemgrep>,
        sample: TestSample
    ): Pair<List<VulnerabilityWithTrace>, JIRTaintAnalyzer.Status> {
        val loadedConfig = rules.semgrepRulesWithDefaultConfig(cp)
        val config = analysisConfig(loadedConfig)

        JIRTaintAnalyzer(
            cp, config,
            projectClasses = projectClasses.locationChecker(),
            options = options.taintAnalyzerOptions(),
        ).use { analyzer ->
            logger.info { "Start IFDS analysis for test: $sample" }
            val traces = analyzer.analyzeWithIfds(sample.methods)
            logger.info { "Finish IFDS analysis for test: $sample" }
            return traces
        }
    }

    private fun generateTestResult(
        skipped: List<TestSample>, disabled: List<TestSample>,
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
            disabled = disabled.map(TestSample::toTestInfo),
        )
    }

    private fun ProjectAnalysisContext.generateSarif(traces: List<VulnerabilityWithTrace>, testSetName: String) {
        val sourcesResolver = project.sourceResolver(projectClasses)
        val generator = SarifGenerator(
            options.sarifGenerationOptions, project.sourceRoot,
            sourcesResolver, JIRSarifTraits(cp)
        )
        (resultDir / (testSetName + options.sarifGenerationOptions.sarifFileName)).outputStream().use { out ->
            generator.generateSarif(out, traces.asSequence(), loadedRules.rulesWithMeta.map { it.second })
        }
    }

    private fun List<TestResult>.joinResults(): TestResult = TestResult(
        success = flatMap { it.success },
        falseNegative = flatMap { it.falseNegative },
        falsePositive = flatMap { it.falsePositive },
        skipped = flatMap { it.skipped },
        disabled = flatMap { it.disabled },
    )

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

    private sealed interface TestSample {
        val info: SampleInfo
        val methods: List<JIRMethod>

        fun toTestInfo(): TestSampleInfo
    }

    private data class MethodTestSample(val method: JIRMethod, override val info: SampleInfo) : TestSample {
        override val methods: List<JIRMethod> get() = listOf(method)

        override fun toTestInfo(): TestSampleInfo = TestSampleInfo(
            method.enclosingClass.name, method.name, info.rule
        )
    }

    private data class ClassTestSample(
        val cls: JIRClassOrInterface,
        override val methods: List<JIRMethod>,
        override val info: SampleInfo
    ) : TestSample {
        override fun toTestInfo(): TestSampleInfo = TestSampleInfo(
            cls.name, methodName = null, info.rule
        )
    }

    private data class SpringTestSample(
        override val methods: List<JIRMethod>,
        val original: TestSample,
    ) : TestSample {
        override val info: SampleInfo get() = original.info
        override fun toTestInfo(): TestSampleInfo = original.toTestInfo()
    }

    private fun JIRAnnotated.findSampleAnnotation(): SampleInfo? {
        val positive = annotations.filter { it.name == POSITIVE_SAMPLE_ANNOTATION_NAME }
        val negative = annotations.filter { it.name == NEGATIVE_SAMPLE_ANNOTATION_NAME }
        val sampleAnnotations = positive + negative
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

        private fun String.isSpringAppTestSet(): Boolean = startsWith("spring-app-tests")
    }
}
