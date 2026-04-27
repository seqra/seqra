package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JavaDataFlowReachabilityTest : AnalysisTest() {

    companion object {
        private const val SAMPLE_PACKAGE = "test.samples"
        private const val TAINT_MARK = "tainted"
        private const val COLLECTION_RULE_ID = "collection-flow-rule"
        private const val STRING_RULE_ID = "string-flow-rule"
        private const val LAMBDA_RULE_ID = "lambda-flow-rule"
        private const val OPTIONAL_RULE_ID = "optional-flow-rule"
        private const val STREAM_RULE_ID = "stream-flow-rule"
        private const val ASYNC_RULE_ID = "async-flow-rule"
    }

    override val sourceFileExtension: String = "java"
    override val useDefaultConfig: Boolean = true

    @Test
    fun `simple flow - source to sink through single method`() {
        val testCls = "$SAMPLE_PACKAGE.SimpleDataFlowSample"
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
        val testCls = "$SAMPLE_PACKAGE.InterproceduralDataFlowSample"
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
        val testCls = "$SAMPLE_PACKAGE.BranchLoopDataFlowSample"
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
    fun `collection flow - taint propagates through list add and get`() {
        val testCls = "$SAMPLE_PACKAGE.CollectionDataFlowSample"
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
        val testCls = "$SAMPLE_PACKAGE.CollectionDataFlowSample"
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
        val testCls = "$SAMPLE_PACKAGE.CollectionDataFlowSample"
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
        val testCls = "$SAMPLE_PACKAGE.StringMethodDataFlowSample"
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
    fun `string flow - taint propagates through toLowerCase`() {
        val testCls = "$SAMPLE_PACKAGE.StringMethodDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "toLowerCaseFlow",
            ruleId = STRING_RULE_ID,
            testName = "toLowerCase flow"
        )
    }

    @Test
    fun `string flow - taint propagates through trim`() {
        val testCls = "$SAMPLE_PACKAGE.StringMethodDataFlowSample"
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
    fun `string flow - taint propagates through concat`() {
        val testCls = "$SAMPLE_PACKAGE.StringMethodDataFlowSample"
        val config = stringConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "concatFlow",
            ruleId = STRING_RULE_ID,
            testName = "concat flow"
        )
    }

    @Test
    fun `string flow - taint propagates through replace`() {
        val testCls = "$SAMPLE_PACKAGE.StringMethodDataFlowSample"
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
    fun `lambda flow - taint tracked through identity lambda virtual dispatch`() {
        val testCls = "$SAMPLE_PACKAGE.LambdaDataFlowSample"
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
    fun `lambda flow - taint tracked through transforming lambda virtual dispatch`() {
        val testCls = "$SAMPLE_PACKAGE.LambdaDataFlowSample"
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
        val testCls = "$SAMPLE_PACKAGE.LambdaDataFlowSample"
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
    fun `lambda closure - taint flows through Iterable forEach into action lambda`() {
        val testCls = "$SAMPLE_PACKAGE.LambdaClosureDataFlowSample"
        val config = lambdaConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "forEachIdentityLambdaTaintFlow",
            ruleId = LAMBDA_RULE_ID,
            testName = "forEach identity lambda taint flow"
        )
    }

    @Test
    fun `lambda closure - sanitizing inner lambda captured by forEach action blocks taint`() {
        val testCls = "$SAMPLE_PACKAGE.LambdaClosureDataFlowSample"
        val config = lambdaConfig(testCls)

        assertNotReachable(
            config = config,
            testCls = testCls,
            entryPointName = "forEachCapturedSanitizingLambdaNoTaintFlow",
            testName = "forEach captured sanitizing lambda no taint flow"
        )
    }

    @Test
    fun `optional flow - taint propagates through Optional of and get`() {
        val testCls = "$SAMPLE_PACKAGE.OptionalDataFlowSample"
        val config = optionalConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "optionalOfGetFlow",
            ruleId = OPTIONAL_RULE_ID,
            testName = "optional of/get flow"
        )
    }

    @Test
    fun `optional flow - taint propagates through Optional map`() {
        val testCls = "$SAMPLE_PACKAGE.OptionalDataFlowSample"
        val config = optionalConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "optionalMapFlow",
            ruleId = OPTIONAL_RULE_ID,
            testName = "optional map flow"
        )
    }

    @Test
    fun `optional flow - taint propagates through Optional orElse`() {
        val testCls = "$SAMPLE_PACKAGE.OptionalDataFlowSample"
        val config = optionalConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "optionalOrElseFlow",
            ruleId = OPTIONAL_RULE_ID,
            testName = "optional orElse flow"
        )
    }

    @Test
    fun `optional flow - taint propagates through Optional flatMap`() {
        val testCls = "$SAMPLE_PACKAGE.OptionalDataFlowSample"
        val config = optionalConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "optionalFlatMapFlow",
            ruleId = OPTIONAL_RULE_ID,
            testName = "optional flatMap flow"
        )
    }

    @Test
    fun `optional flow - taint propagates through Optional ifPresent`() {
        val testCls = "$SAMPLE_PACKAGE.OptionalDataFlowSample"
        val config = optionalConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "optionalIfPresentFlow",
            ruleId = OPTIONAL_RULE_ID,
            testName = "optional ifPresent flow"
        )
    }

    @Test
    fun `stream flow - taint propagates through stream map and collect`() {
        val testCls = "$SAMPLE_PACKAGE.StreamDataFlowSample"
        val config = streamConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "streamMapCollectFlow",
            ruleId = STREAM_RULE_ID,
            testName = "stream map/collect flow"
        )
    }

    @Test
    fun `stream flow - taint propagates through stream filter and collect`() {
        val testCls = "$SAMPLE_PACKAGE.StreamDataFlowSample"
        val config = streamConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "streamFilterCollectFlow",
            ruleId = STREAM_RULE_ID,
            testName = "stream filter/collect flow"
        )
    }

    @Test
    fun `stream flow - taint propagates through stream reduce`() {
        val testCls = "$SAMPLE_PACKAGE.StreamDataFlowSample"
        val config = streamConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "streamReduceFlow",
            ruleId = STREAM_RULE_ID,
            testName = "stream reduce flow"
        )
    }

    @Test
    fun `stream flow - taint propagates through stream forEach`() {
        val testCls = "$SAMPLE_PACKAGE.StreamDataFlowSample"
        val config = streamConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "streamForEachFlow",
            ruleId = STREAM_RULE_ID,
            testName = "stream forEach flow"
        )
    }

    @Test
    @Disabled // todo: List<List<Taint>> -- impossible to represent in Tree
    fun `stream flow - taint propagates through stream flatMap`() {
        val testCls = "$SAMPLE_PACKAGE.StreamDataFlowSample"
        val config = streamConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "streamFlatMapFlow",
            ruleId = STREAM_RULE_ID,
            testName = "stream flatMap flow"
        )
    }

    @Test
    fun `async flow - taint propagates through Thread with Runnable`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "threadRunnableFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "thread runnable flow"
        )
    }

    @Test
    fun `async flow - taint propagates through Thread with lambda`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "threadLambdaFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "thread lambda flow"
        )
    }

    @Test
    fun `async flow - taint propagates through Callable and Future`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "callableFutureFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "callable/future flow"
        )
    }

    @Test
    fun `async flow - taint propagates through CompletableFuture supplyAsync`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "completableFutureSupplyFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "completable future supply flow"
        )
    }

    @Test
    fun `async flow - taint propagates through CompletableFuture thenApply`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "completableFutureThenApplyFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "completable future thenApply flow"
        )
    }

    @Test
    fun `async flow - taint propagates through CompletableFuture thenAccept`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "completableFutureThenAcceptFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "completable future thenAccept flow"
        )
    }

    @Test
    fun `async flow - taint propagates through ExecutorService submit with lambda`() {
        val testCls = "$SAMPLE_PACKAGE.AsyncDataFlowSample"
        val config = asyncConfig(testCls)

        assertReachable(
            config = config,
            testCls = testCls,
            entryPointName = "executorSubmitRunnableFlow",
            ruleId = ASYNC_RULE_ID,
            testName = "executor submit runnable flow"
        )
    }

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

    private fun optionalConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", OPTIONAL_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun streamConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", STREAM_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    private fun asyncConfig(testCls: String) = SerializedTaintConfig(
        source = listOf(sourceRule(testCls, "source", TAINT_MARK)),
        sink = listOf(sinkRule(testCls, "sink", ASYNC_RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )
}
