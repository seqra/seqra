package org.opentaint.jvm.sast.sarif

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KotlinSarifGeneratorTest : AbstractSarifGeneratorTest() {
    companion object {
        private const val SAMPLE_PACKAGE = "test.samples"
    }

    override val sourceFileExtension: String = "kt"

    @Test
    fun `interprocedural flow with tainted args`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinTaintedArgsSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "args-sink-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "args_source", "args_process_call", "args_process_entry",
                "args_transform_call", "args_transform_body", "args_transform_exit",
                "args_transform", "args_process_exit", "args_process_prop",
                "args_sink"
            ),
            testCls = testCls,
            entryPointName = "taintedArgsFlow",
            testName = "tainted args flow"
        )
    }

    @Test
    fun `flow with tainted object fields`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinTaintedFieldsSample"
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
        val testCls = "$SAMPLE_PACKAGE.KotlinComplexConditionalSample"
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
        val testCls = "$SAMPLE_PACKAGE.KotlinMethodExitSinkSample"
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
        val testCls = "$SAMPLE_PACKAGE.KotlinDeepNestedConditionSample"
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
                "deep_outer", "deep_outer_entry", "deep_middle_call",
                "deep_middle_entry", "deep_inner_call",
                "deep_inner_body", "deep_inner_exit",
                "deep_inner", "deep_middle_exit",
                "deep_middle", "deep_outer_exit",
                "deep_outer_prop", "deep_sink"
            ),
            testCls = testCls,
            entryPointName = "deepNestedConditionFlow",
            testName = "deep nested condition flow"
        )
    }

    @Test
    fun `flow with tainted static field`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinStaticFieldSample"
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

    @Test
    fun `double inlined body of lambda entered in trace`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinInlineLambdaSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "inline-lambda-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "i1", "i2", "i3",
                "inline_source",
                "inline_call",
                "inline_propagate",
                "inline_transform",
                "inline_pass",
                "inline_pass_propagate",
                "inline_pass_body",
                "inline_pass_exit",
                "inline_sink",
                "transform_exit",
            ),
            testCls = testCls,
            entryPointName = "inlineLambdaFlow",
            testName = "double inlined lambda entry",
        )
    }

    @Test
    fun `double inlined body of lambda entered in trace many lambda`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinInlineLambdaSample2"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "inline-lambda-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "inline_source",
                "i4", "i5", "i6",
                "d1_call",
                "inline_transform",
                "transform_exit",
                "d1_propagate",
                "d1_pass",
                "d1_pass_body",
                "d1_pass_exit",
                "d1_pass_propagate",
                "i1", "i2", "i3",
                "inline_call",
                "inline_transform",
                "transform_exit",
                "inline_propagate",
                "inline_pass",
                "inline_pass_body",
                "inline_pass_exit",
                "inline_pass_propagate",
                "inline_sink",
            ),
            testCls = testCls,
            entryPointName = "inlineLambdaFlow",
            testName = "double inlined lambda entry",
        )
    }

    @Test
    fun `double inlined body of lambda entered in trace deep`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinInlineLambdaSampleDeep"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "inline-lambda-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "i1", "i2", "i3",
                "inline_source",
                "inline_call",
                "inline_propagate",
                "inline_transform",
                "i4", "i5", "i6",
                "inline_pass",
                "inline_pass_propagate",
                "inline_pass_body",
                "inline_pass_exit",
                "inline_sink",
                "transform_exit",
            ),
            testCls = testCls,
            entryPointName = "inlineLambdaFlow",
            testName = "double inlined lambda entry deep",
        )
    }

    @Test
    fun `inlined lambda in reentrant method call`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinInlineLambdaSampleReenter"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "inline-lambda-reenter-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "inline_source",
                "f1_call",
                "f_enter",
                "i1", "i2", "i3",
                "inline_call",
                "inline_transform",
                "transform_exit",
                "inline_propagate",
                "inline_pass",
                "inline_pass_body",
                "inline_pass_exit",
                "inline_pass_propagate",
                "f_return",
                "f_exit",
                "f1_propagate",
                "f2_call",
                "f_enter",
                "i1", "i2", "i3",
                "inline_call",
                "inline_transform",
                "transform_exit",
                "inline_propagate",
                "inline_pass",
                "inline_pass_body",
                "inline_pass_exit",
                "inline_pass_propagate",
                "f_return",
                "f_exit",
                "f2_propagate",
                "inline_sink",
            ),
            testCls = testCls,
            entryPointName = "inlineLambdaFlow",
            testName = "inlined lambda in reentrant method call",
        )
    }

    @Test
    fun `messy trace in kotlin default methods`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinDefaultParamsSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "default-params-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "default_source",
                "default_calling",
                "default_call",
                "default_sink",
                "default_takes",
                "default_exit_process",
            ),
            testCls = testCls,
            entryPointName = "defaultParamsFlow",
            testName = "messy trace in default methods"
        )
    }

    @Test
    fun `messy trace in data class initializer`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinDataClassInitSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "data-class-init-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf("data_source", "data_sink"),
            testCls = testCls,
            entryPointName = "dataClassInitFlow",
            testName = "messy trace in data class initializer"
        )
    }

    @Test
    fun `messy trace in data class propagation with default params`() {
        val testCls = "$SAMPLE_PACKAGE.KotlinDataClassInitSample"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule(testCls, "source", "tainted")),
            sink = listOf(sinkRule(testCls, "sink", "data-class-propagation-rule", listOf(Argument(0) to "tainted")))
        )

        runTest(
            config = config,
            expectedLocations = listOf(
                "data_prop_source",
                "data_prop_calling",
                "data_prop_entering",
                "data_prop_id_assign",
                "data_prop_exiting",
                "data_prop_call",
                "data_prop_get_call",
                "data_prop_get_ret",
                "data_prop_get_exit",
                "data_prop_get_prop",
                "data_prop_sink",
            ),
            testCls = testCls,
            entryPointName = "dataClassPropagationFlow",
            testName = "messy trace in data class propagation with default params"
        )
    }
}
