package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KotlinDataFlowReachabilityTest : AnalysisTest() {

    companion object {
        private const val SAMPLE_PACKAGE = "test.samples"
        private const val TAINT_MARK = "tainted"
        private const val COROUTINE_RULE_ID = "coroutine-flow-rule"
        private const val COLLECTION_RULE_ID = "kt-collection-flow-rule"
        private const val STRING_RULE_ID = "kt-string-flow-rule"
        private const val LAMBDA_RULE_ID = "kt-lambda-flow-rule"
        private const val SCOPE_RULE_ID = "kt-scope-flow-rule"
        private const val NULL_SAFETY_RULE_ID = "kt-null-safety-flow-rule"
    }

    override val sourceFileExtension: String = "kt"
    override val useDefaultConfig: Boolean = true

    @Test
    fun `simple flow - source to sink through single method`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinSimpleDataFlowSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
            sink = listOf(sinkRule(testCls, "sink", "simple-flow-rule", listOf(Argument(0) to TAINT_MARK)))
        )

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "simpleDataFlow",
            ruleId = "simple-flow-rule",
            testName = "simple flow"
        )
    }

    @Test
    fun `interprocedural flow - source to sink through chained methods`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinInterproceduralDataFlowSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
            sink = listOf(sinkRule(testCls, "sink", "ip-flow-rule", listOf(Argument(0) to TAINT_MARK)))
        )

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "interproceduralDataFlow",
            ruleId = "ip-flow-rule",
            testName = "interprocedural flow"
        )
    }

    @Test
    fun `branch flow - source to sink through conditional branches`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinBranchLoopDataFlowSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
            sink = listOf(sinkRule(testCls, "sink", "branch-loop-rule", listOf(Argument(0) to TAINT_MARK)))
        )

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "branchLoopDataFlow",
            ruleId = "branch-loop-rule",
            testName = "branch flow"
        )
    }

    @Test
    fun `coroutine flow - taint propagates through runBlocking`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinCoroutineDataFlowSample"
        val config = coroutineConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "runBlockingFlow",
            ruleId = COROUTINE_RULE_ID,
            testName = "runBlocking flow"
        )
    }

    @Test
    fun `coroutine flow - taint propagates through launch`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinCoroutineDataFlowSample"
        val config = coroutineConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "launchFlow",
            ruleId = COROUTINE_RULE_ID,
            testName = "launch flow"
        )
    }

    @Test
    fun `coroutine flow - taint propagates through async await`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinCoroutineDataFlowSample"
        val config = coroutineConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "asyncAwaitFlow",
            ruleId = COROUTINE_RULE_ID,
            testName = "async/await flow"
        )
    }

    @Test
    fun `collection flow - taint propagates through list add and get`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinCollectionDataFlowSample"
        val config = collectionConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "listAddGetFlow",
            ruleId = COLLECTION_RULE_ID,
            testName = "list add/get flow"
        )
    }

    @Test
    fun `collection flow - taint propagates through map put and get`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinCollectionDataFlowSample"
        val config = collectionConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "mapPutGetFlow",
            ruleId = COLLECTION_RULE_ID,
            testName = "map put/get flow"
        )
    }

    @Test
    fun `collection flow - taint propagates through iterator`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinCollectionDataFlowSample"
        val config = collectionConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "iteratorFlow",
            ruleId = COLLECTION_RULE_ID,
            testName = "iterator flow"
        )
    }

    @Test
    fun `string flow - taint propagates through substring`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStringDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "substringFlow",
            ruleId = STRING_RULE_ID,
            testName = "substring flow"
        )
    }

    @Test
    fun `string flow - taint propagates through lowercase`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStringDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "lowercaseFlow",
            ruleId = STRING_RULE_ID,
            testName = "lowercase flow"
        )
    }

    @Test
    fun `string flow - taint propagates through trim`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStringDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "trimFlow",
            ruleId = STRING_RULE_ID,
            testName = "trim flow"
        )
    }

    @Test
    fun `string flow - taint propagates through plus`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStringDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "plusFlow",
            ruleId = STRING_RULE_ID,
            testName = "plus flow"
        )
    }

    @Test
    fun `string flow - taint propagates through replace`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStringDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "replaceFlow",
            ruleId = STRING_RULE_ID,
            testName = "replace flow"
        )
    }

    @Test
    fun `string flow - taint propagates through string template`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStringDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "stringTemplateFlow",
            ruleId = STRING_RULE_ID,
            testName = "string template flow"
        )
    }

    @Test
    fun `lambda flow - taint tracked through identity lambda`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinLambdaDataFlowSample"
        val config = lambdaConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "lambdaIdentityFlow",
            ruleId = LAMBDA_RULE_ID,
            testName = "lambda identity flow"
        )
    }

    @Test
    fun `lambda flow - taint tracked through transforming lambda`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinLambdaDataFlowSample"
        val config = lambdaConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "lambdaTransformFlow",
            ruleId = LAMBDA_RULE_ID,
            testName = "lambda transform flow"
        )
    }

    @Test
    fun `lambda flow - taint tracked through lambda passed to method`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinLambdaDataFlowSample"
        val config = lambdaConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "lambdaPassedToMethodFlow",
            ruleId = LAMBDA_RULE_ID,
            testName = "lambda passed to method flow"
        )
    }

    @Test
    fun `scope flow - taint propagates through let`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinScopeFunctionDataFlowSample"
        val config = scopeConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "letFlow",
            ruleId = SCOPE_RULE_ID,
            testName = "let flow"
        )
    }

    @Test
    fun `scope flow - taint propagates through run`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinScopeFunctionDataFlowSample"
        val config = scopeConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "runFlow",
            ruleId = SCOPE_RULE_ID,
            testName = "run flow"
        )
    }

    @Test
    fun `scope flow - taint propagates through with`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinScopeFunctionDataFlowSample"
        val config = scopeConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "withFlow",
            ruleId = SCOPE_RULE_ID,
            testName = "with flow"
        )
    }

    @Test
    fun `scope flow - taint propagates through also`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinScopeFunctionDataFlowSample"
        val config = scopeConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "alsoFlow",
            ruleId = SCOPE_RULE_ID,
            testName = "also flow"
        )
    }

    @Test
    fun `scope flow - taint propagates through apply`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinScopeFunctionDataFlowSample"
        val config = scopeConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "applyFlow",
            ruleId = SCOPE_RULE_ID,
            testName = "apply flow"
        )
    }

    @Test
    fun `null safety flow - taint propagates through elvis operator`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinNullSafetyDataFlowSample"
        val config = nullSafetyConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "elvisFlow",
            ruleId = NULL_SAFETY_RULE_ID,
            testName = "elvis flow"
        )
    }

    @Test
    fun `null safety flow - taint propagates through safe call`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinNullSafetyDataFlowSample"
        val config = nullSafetyConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "safeCallFlow",
            ruleId = NULL_SAFETY_RULE_ID,
            testName = "safe call flow"
        )
    }

    @Test
    fun `null safety flow - taint propagates through not-null assertion`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinNullSafetyDataFlowSample"
        val config = nullSafetyConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "notNullAssertionFlow",
            ruleId = NULL_SAFETY_RULE_ID,
            testName = "not-null assertion flow"
        )
    }

    @Test
    fun `null safety flow - taint propagates through safe call chain`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinNullSafetyDataFlowSample"
        val config = nullSafetyConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "safeCallChainFlow",
            ruleId = NULL_SAFETY_RULE_ID,
            testName = "safe call chain flow"
        )
    }

    private fun coroutineConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", COROUTINE_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun collectionConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", COLLECTION_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun stringConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", STRING_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun lambdaConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", LAMBDA_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun scopeConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", SCOPE_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun nullSafetyConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "nullableSource", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", NULL_SAFETY_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )
}
