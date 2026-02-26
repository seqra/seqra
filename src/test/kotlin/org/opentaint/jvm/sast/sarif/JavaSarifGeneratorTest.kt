package org.opentaint.jvm.sast.sarif

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JavaSarifGeneratorTest: AbstractSarifGeneratorTest() {
    companion object {
        private const val SAMPLE_PACKAGE = "test.samples"
    }

    override val sourceFileExtension: String = "java"

    @Test
    fun `flow with object constructor`() {
        val testCls = "$SAMPLE_PACKAGE.ConstructorFlowSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "ctor-sink-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "ctor_source", "ctor_init_call", "ctor_init_prop",
                "ctor_init_entry", "ctor_init_assign", "ctor_init_exit",
                "ctor_get_call", "ctor_get_prop", "ctor_get_return", "ctor_get_exit",
                "ctor_sink"
            ),
            testCls = testCls,
            entryPointName = "constructorFlow",
            testName = "constructor flow"
        )
    }

    @Test
    fun `interprocedural flow with tainted args`() {
        val testCls = "$SAMPLE_PACKAGE.TaintedArgsSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "args-sink-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "args_source", "args_process_call", "args_process_prop",
                "args_process_entry", "args_transform_call", "args_transform",
                "args_transform_body", "args_transform_exit", "args_process_exit",
                "args_sink"
            ),
            testCls = testCls,
            entryPointName = "taintedArgsFlow",
            testName = "tainted args flow"
        )
    }

    @Test
    fun `interprocedural flow with tainted method instance`() {
        val testCls = "$SAMPLE_PACKAGE.TaintedInstanceSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "instance-sink-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "inst_create_inside", "inst_source", "inst_set_call", "inst_set_prop",
                "inst_set_entry", "inst_set_assign", "inst_set_exit",
                "inst_return", "inst_create_exit", "inst_create",
                "inst_get_call", "inst_get_prop", "inst_get_return", "inst_get_exit",
                "inst_sink"
            ),
            testCls = testCls,
            entryPointName = "taintedInstanceFlow",
            testName = "tainted instance flow"
        )
    }

    @Test
    fun `flow with tainted object fields`() {
        val testCls = "$SAMPLE_PACKAGE.TaintedFieldsSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "field-sink-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf("field_source", "field_process_call", "field_process_entry", "field_read", "field_sink"),
            testCls = testCls,
            entryPointName = "taintedFieldFlow",
            testName = "tainted field flow"
        )
    }

    @Test
    fun `complex flow with conditional sources`() {
        val testCls = "$SAMPLE_PACKAGE.ComplexConditionalSample"
        val config = SerializedTaintConfig(
            source = listOf(
                sourceRule(testCls, "taintA", "taintA"),
                sourceRule(testCls, "taintB", "taintB"),
                sourceRule(testCls, "copy", "combined", listOf(Argument(0) to "taintA", Argument(1) to "taintB"))
            ),
            sink = listOf(sinkRule(testCls, "sink", "complex-cond-rule", listOf(Argument(0) to "combined")))
        )

        runTest(
            config = config,
            expectedLocations = listOf("cond_taintA", "cond_taintB", "cond_copy", "cond_sink"),
            testCls = testCls,
            entryPointName = "complexConditionalFlow",
            testName = "complex conditional flow"
        )
    }

    @Test
    fun `method exit sink`() {
        val testCls = "$SAMPLE_PACKAGE.MethodExitSinkSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "secret")),
            methodExitSink = listOf(methodExitSinkRule(testCls, "methodExitFlow", "method-exit-rule", "secret"))
        )

        runTest(
            config = config,
            expectedLocations = listOf("exit_source", "exit_return", "exit_sink_loc"),
            testCls = testCls,
            entryPointName = "methodExitFlow",
            testName = "method exit sink"
        )
    }

    @Test
    fun `complex source with deeply nested conditions`() {
        val testCls = "$SAMPLE_PACKAGE.DeepNestedConditionSample"
        val config = SerializedTaintConfig(
            source = listOf(
                sourceRule(testCls, "taintA", "taintA"),
                sourceRule(testCls, "taintB", "taintB"),
                sourceRule(testCls, "combineWithCondition", "combined", listOf(Argument(0) to "taintA", Argument(1) to "taintB"))
            ),
            sink = listOf(sinkRule(testCls, "sink", "deep-nested-rule", listOf(Argument(0) to "combined")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "deep_taintA", "deep_taintB", "deep_combine",
                "deep_outer", "deep_outer_prop",
                "deep_outer_entry", "deep_middle_call", "deep_middle",
                "deep_middle_entry", "deep_inner_call", "deep_inner",
                "deep_inner_body", "deep_inner_exit",
                "deep_middle_exit", "deep_outer_exit",
                "deep_sink"
            ),
            testCls = testCls,
            entryPointName = "deepNestedConditionFlow",
            testName = "deep nested condition flow"
        )
    }

    @Test
    fun `method entry point source`() {
        val testCls = "$SAMPLE_PACKAGE.EntryPointSourceSample"
        val config = SerializedTaintConfig(
            entryPoint = listOf(entryPointRule(testCls, "entryPointFlow", "userTaint", 0)),
            sink = listOf(sinkRule(testCls, "sink", "entry-point-rule", listOf(Argument(0) to "userTaint")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "entry_point_mark", "entry_process_call", "entry_process_prop",
                "entry_process_entry", "entry_transform_call", "entry_transform",
                "entry_transform_body", "entry_transform_exit", "entry_process_exit",
                "entry_sink"
            ),
            testCls = testCls,
            entryPointName = "entryPointFlow",
            testName = "entry point source"
        )
    }

    @Test
    fun `flow with tainted static field`() {
        val testCls = "$SAMPLE_PACKAGE.StaticFieldSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "static-field-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf("static_source", "static_process_call", "static_process_entry", "static_read", "static_sink"),
            testCls = testCls,
            entryPointName = "staticFieldFlow",
            testName = "static field flow"
        )
    }
}
