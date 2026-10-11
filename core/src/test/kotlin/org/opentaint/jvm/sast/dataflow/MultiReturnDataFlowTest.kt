package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

/**
 * Interprocedural flows through callees with several `return` statements, the shape
 * `JApplicationSingleExitGraph` normalizes to a single normal exit and a single exceptional one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class MultiReturnDataFlowTest : AnalysisTest() {

    companion object {
        private const val TEST_CLS = "test.samples.MultiReturnDataFlowSample"
        private const val TAINT_MARK = "tainted"
        private const val RULE_ID = "multi-return-rule"
    }

    override val sourceFileExtension: String = "java"
    override val useDefaultConfig: Boolean = true

    private val config = SerializedTaintConfig(
        source = listOf(sourceRule(TEST_CLS, "source", TAINT_MARK)),
        sink = listOf(sinkRule(TEST_CLS, "sink", RULE_ID, listOf(Argument(0) to TAINT_MARK)))
    )

    @Test
    fun `every return of the callee carries taint`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "allReturnsTaintedFlow",
        ruleId = RULE_ID,
        testName = "all returns tainted",
    )

    @Test
    fun `exactly one of three returns carries taint`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "oneReturnTaintedFlow",
        ruleId = RULE_ID,
        testName = "one return tainted",
    )

    @Test
    fun `taint reaches the callee but no return carries it`() = assertNotReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "noReturnTaintedFlow",
        testName = "no return tainted",
    )

    /**
     * The callee creates taint in a local and returns a constant on every path. The normal exit
     * must carry the return value, not whatever the callee still holds in a local when it exits.
     */
    @Test
    fun `taint created in the callee and never returned does not reach the caller`() = assertNotReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "taintNotReturnedFlow",
        testName = "taint not returned",
    )

    @Test
    fun `taint flows past an early-return guard clause`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "earlyReturnGuardFlow",
        ruleId = RULE_ID,
        testName = "early return guard",
    )

    @Test
    fun `taint crosses two frames that both have several returns`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "nestedMultiReturnFlow",
        ruleId = RULE_ID,
        testName = "nested multi-return",
    )

    @Test
    fun `taint crosses two chained multi-return calls`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "chainedMultiReturnFlow",
        ruleId = RULE_ID,
        testName = "chained multi-return",
    )

    @Test
    fun `taint returns from inside a loop`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "loopReturnFlow",
        ruleId = RULE_ID,
        testName = "loop return",
    )

    @Test
    fun `taint returns from a callee that throws on another path`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "returnOrThrowFlow",
        ruleId = RULE_ID,
        testName = "return or throw",
    )

    /** Control for the test below: the same side effect reaches the caller over a normal exit. */
    @Test
    fun `a side effect on an argument reaches the caller over the normal exit`() = assertReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "taintArgumentThenReturnFlow",
        ruleId = RULE_ID,
        testName = "argument side effect, normal exit",
    )

    /**
     * The callee taints an argument and then always throws, so its only exit is the exceptional
     * one. `JIRLanguageManager.producesExceptionalControlFlow` must report that exit as
     * exceptional, or `isApplicableExitToReturnEdge` emits a summary edge for it and the side
     * effect leaks across the `throw`. Exceptional flow is deliberately not propagated to a call
     * site (`JIRMethodCallFactMapper`: "Trow can't be propagated to method return site"), so the
     * caller sees no taint even though a real execution would.
     */
    @Test
    fun `a side effect on an argument does not leak over the exceptional exit`() = assertNotReachable(
        config = config,
        testCls = TEST_CLS,
        entryPointName = "taintArgumentThenThrowFlow",
        testName = "argument side effect, exceptional exit",
    )
}
