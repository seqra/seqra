package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldLimitAnalysisTest : AnalysisTest() {

    companion object {
        private const val TEST_CLS = "test.samples.FieldLimitSample"
        private const val TAINT_MARK = "tainted"
        private const val RULE_ID = "field-limit-rule"
    }

    override val sourceFileExtension: String = "java"

    private val config = SerializedTaintConfig(
        source = listOf(sourceRule(TEST_CLS, "source", TAINT_MARK)),
        sink = listOf(sinkRule(TEST_CLS, "sink", RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    @Test
    fun `depth three flow is found without field limit`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "depthThreeFlow",
        ruleId = RULE_ID,
        testName = "unlimited",
    )

    @Test
    fun `depth three flow is found with field limit three`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "depthThreeFlow",
        ruleId = RULE_ID,
        testName = "limit 3",
        fieldLimit = 3,
    )

    @Test
    fun `depth three flow is dropped with field limit two`() = assertNotReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "depthThreeFlow",
        testName = "limit 2",
        fieldLimit = 2,
    )

    @Test
    fun `depth one flow is found with field limit one`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "depthOneFlow",
        ruleId = RULE_ID,
        testName = "limit 1",
        fieldLimit = 1,
    )

    @Test
    fun `depth one flow is dropped with field limit zero`() = assertNotReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "depthOneFlow",
        testName = "limit 0",
        fieldLimit = 0,
    )
}
