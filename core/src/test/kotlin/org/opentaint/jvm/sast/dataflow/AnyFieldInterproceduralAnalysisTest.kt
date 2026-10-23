package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier.AnyField
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import java.util.concurrent.TimeUnit

class AnyFieldInterproceduralAnalysisTest : AnalysisTest() {
    companion object {
        private const val TEST_CLASS = "test.samples.AnyFieldInterproceduralSample"
        private const val DEEP_TEST_CLASS = "test.samples.AnyFieldDeepInterproceduralSample"
        private const val TAINT_MARK = "tainted"
        private const val RULE_ID = "any-field-interprocedural"
        private const val DEEP_RULE_ID = "any-field-interprocedural-deep"
    }

    /**
     * The sink accepts the container, so the mark has to be found below an arbitrary field rather
     * than on argument 0 itself -- which is what raises the field-unfold request in the first place.
     */
    private fun anyFieldSink(testClass: String, ruleId: String) = SerializedRule.Sink(
        function = functionMatcher(testClass, "sink"),
        condition = SerializedCondition.ContainsMark(
            tainted = TAINT_MARK,
            pos = PositionBaseWithModifiers.WithModifiers(Argument(0), listOf(AnyField)),
        ),
        id = ruleId,
        meta = SinkMetaData(note = "Taint reaches a field of sink argument"),
    )

    override val sourceFileExtension: String = "java"

    override val analysisUnrollStrategy: AnyAccessorUnrollStrategy = object : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean = accessor is FieldAccessor
    }

    private val config = SerializedTaintConfig(
        source = listOf(sourceRule(TEST_CLASS, "source", TAINT_MARK)),
        sink = listOf(anyFieldSink(TEST_CLASS, RULE_ID)),
    )

    private val deepConfig = SerializedTaintConfig(
        source = listOf(sourceRule(DEEP_TEST_CLASS, "source", TAINT_MARK)),
        sink = listOf(anyFieldSink(DEEP_TEST_CLASS, DEEP_RULE_ID)),
    )

    @Test
    fun `any-field sink unfolds a field across two call frames`() = assertReachable(
        config = config,
        testCls = TEST_CLASS,
        entryPointName = "fieldFlow",
        ruleId = RULE_ID,
        testName = "interprocedural any-field sink",
    )

    /**
     * The same flow at a range of call depths. Each frame is entered with the container as a formal
     * parameter, so the request travels on fact-to-fact edges the whole way. These pin how far the
     * field-unfold request is allowed to climb: a bound that only answers un-refined requests stops
     * finding the flow once the request has picked up accessors on the way up, and the depth at
     * which that happens is exactly what these cases record.
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `any-field sink unfolds a field at depth 1`() = assertDeepReachable(1)

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `any-field sink unfolds a field at depth 2`() = assertDeepReachable(2)

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `any-field sink unfolds a field at depth 3`() = assertDeepReachable(3)

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `any-field sink unfolds a field at depth 5`() = assertDeepReachable(5)

    /**
     * Ten frames. This is the cost case: without a bound on the climb the analyzer exhausts its own
     * IFDS budget here rather than reporting the flow.
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `any-field sink unfolds a field at depth 10`() = assertDeepReachable(10)

    /**
     * A self-recursive descent through a repeated field. Each frame's refinement delta is the tail
     * by which its own initial fact extends, so this is the shape any "the delta is the edge fact's
     * tail, so skip it" bound has to be checked against. The depth ladder never produces it.
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `any-field sink unfolds a field through a recursive walk`() = assertReachable(
        config = deepConfig,
        testCls = DEEP_TEST_CLASS,
        entryPointName = "fieldFlowRecursive",
        ruleId = DEEP_RULE_ID,
        testName = "recursive interprocedural any-field sink",
    )

    private fun assertDeepReachable(depth: Int) = assertReachable(
        config = deepConfig,
        testCls = DEEP_TEST_CLASS,
        entryPointName = "fieldFlowDepth$depth",
        ruleId = DEEP_RULE_ID,
        testName = "interprocedural any-field sink at depth $depth",
    )
}
