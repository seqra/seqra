package org.opentaint.semgrep.pattern

import org.opentaint.dataflow.configuration.python.serialized.PythonPosition
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionBase
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionModifier
import org.opentaint.dataflow.configuration.python.serialized.PythonTarget
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCleaner
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCondition
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonEntryPointSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonExitSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonRule
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSource
import org.opentaint.semgrep.pattern.conversion.PythonLanguageStrategy
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PythonRuleEmitTest {
    private fun emit(resource: String): List<SerializedPythonRule> {
        val yaml = javaClass.classLoader.getResource(resource)!!.readText()
        val loader = SemgrepRuleLoader(listOf(PythonLanguageStrategy()))
        loader.registerRuleSet(yaml, Path(resource), Path("."), SemgrepLoadTrace())
        val loaded = loader.loadRules()

        @Suppress("UNCHECKED_CAST")
        val rule = loaded.rulesWithMeta.first().first as TaintRuleFromSemgrep<SerializedPythonRule>
        return rule.taintRules.flatMap { it.rules }
    }

    private fun emitAll(resource: String): List<SerializedPythonRule> {
        val yaml = javaClass.classLoader.getResource(resource)!!.readText()
        val loader = SemgrepRuleLoader(listOf(PythonLanguageStrategy()))
        loader.registerRuleSet(yaml, Path(resource), Path("."), SemgrepLoadTrace())
        return loader.loadRules().rulesWithMeta.flatMap {
            @Suppress("UNCHECKED_CAST")
            (it.first as TaintRuleFromSemgrep<SerializedPythonRule>).taintRules.flatMap { tr -> tr.rules }
        }
    }

    private fun SerializedPythonRule.functionTarget(): String? = (target as? PythonTarget.Function)?.function

    private fun SerializedPythonRule.attributeTarget(): String? = (target as? PythonTarget.Attribute)?.attribute

    @Test fun `qualified source taints the call result`() {
        val source = emit("python-rules/source-sink.yaml")
            .filterIsInstance<SerializedPythonSource>()
            .single { it.functionTarget() == "os.environ.get" }

        assertTrue(source.taint.isNotEmpty(), "expected a taint action")
        assertTrue(source.taint.all { it.pos.base == PythonPositionBase.Result }, "source taints the result")
    }

    @Test fun `instance-call sink checks its tainted positions`() {
        val sinks = emit("python-rules/source-sink.yaml")
            .filterIsInstance<SerializedPythonSink>()
            .filter { it.functionTarget() == "execute" }

        assertTrue(sinks.isNotEmpty(), "expected execute sink(s)")
        assertTrue(sinks.all { it.condition != null }, "each sink carries a taint condition")

        val bases = sinks.flatMap { it.condition!!.markPositions() }.map { it.base }.toSet()
        assertTrue(PythonPositionBase.This in bases, "a sink checks the \$DB receiver (this)")
        assertTrue(bases.any { it is PythonPositionBase.Argument }, "a sink checks the \$Q argument")
    }

    @Test fun `sanitizer becomes a cleaner rule`() {
        val cleaner = emit("python-rules/sanitizer.yaml")
            .filterIsInstance<SerializedPythonCleaner>()
            .single { it.functionTarget() == "shlex.quote" }

        assertTrue(cleaner.cleans.isNotEmpty(), "cleaner removes taint from at least one position")
    }

    @Test fun `metavar method name becomes a qualified regex target`() {
        val source = emit("python-rules/metavar-method.yaml")
            .filterIsInstance<SerializedPythonSource>()
            .single()

        assertEquals("""requests\..*""", source.functionTarget())
    }

    @Test fun `function-def source becomes an entry-point source`() {
        val entryPoint = emit("python-rules/entrypoint-def.yaml")
            .filterIsInstance<SerializedPythonEntryPointSource>()
            .single()

        assertEquals(".*", entryPoint.functionTarget())
        assertTrue(entryPoint.taint.isNotEmpty(), "entry-point taints at least one parameter position")
    }

    @Test fun `undecorated function-def source has no decorator condition`() {
        val entryPoint = emit("python-rules/entrypoint-def.yaml")
            .filterIsInstance<SerializedPythonEntryPointSource>()
            .single()

        assertTrue(
            entryPoint.condition.decoratorNames().isEmpty(),
            "an undecorated `def` pattern must not gate on any decorator",
        )
    }

    @Test fun `decorated function-def source is gated on the decorator`() {
        val entryPoint = emit("python-rules/return-sink.yaml")
            .filterIsInstance<SerializedPythonEntryPointSource>()
            .single()

        assertEquals(".*", entryPoint.functionTarget(), "the decorator gate lives in the condition, not the target")
        assertTrue(entryPoint.taint.isNotEmpty(), "entry-point taints at least one position")
        assertEquals(
            listOf("entry_point"),
            entryPoint.condition.decoratorNames(),
            "the entry point is gated on @entry_point",
        )
    }

    @Test fun `subscript source taints the result element`() {
        val source = emit("python-rules/subscript-source.yaml")
            .filterIsInstance<SerializedPythonSource>()
            .single { it.functionTarget() == "source" }

        val positions = source.taint.map { it.pos }
        assertTrue(positions.isNotEmpty(), "expected a taint action")
        assertTrue(
            positions.all {
                it is PythonPosition.WithModifiers &&
                    it.base == PythonPositionBase.Result &&
                    it.modifiers == listOf(PythonPositionModifier.ArrayElement)
            },
            "subscript source taints Result[*], got $positions",
        )
    }

    @Test fun `subscript-assignment sink emits no sink (engine gap)`() {
        val all = emitAll("python-rules/subscript-assign-sink.yaml")
        assertTrue(all.isEmpty(), "expected zero emitted rules (subscript-store sink unsupported), got ${all.size}")
    }

    @Test fun `subscript-assignment source binds the metavar to the result element`() {
        val source = emit("python-rules/subscript-assign-source.yaml")
            .filterIsInstance<SerializedPythonSource>()
            .single { it.functionTarget() == "source" }

        val positions = source.taint.map { it.pos }
        assertTrue(positions.isNotEmpty(), "expected a taint action")
        assertTrue(
            positions.all {
                it is PythonPosition.WithModifiers &&
                    it.base == PythonPositionBase.Result &&
                    it.modifiers == listOf(PythonPositionModifier.ArrayElement)
            },
            "subscript-assignment source taints Result[*], got $positions",
        )
    }

    @Test fun `qualified attribute read becomes an attribute-fqn source`() {
        val source = emit("python-rules/attribute-source.yaml")
            .filterIsInstance<SerializedPythonSource>()
            .single { it.attributeTarget() == "flask.request" }

        assertTrue(source.taint.isNotEmpty(), "expected a taint action")
        assertTrue(source.taint.all { it.pos.base == PythonPositionBase.Result }, "attribute read taints the result")
    }

    @Test fun `constant keyword-arg condition serializes as a named position`() {
        val sink = emitAll("python-rules/kwarg-const.yaml")
            .filterIsInstance<SerializedPythonSink>()
            .first { it.functionTarget() == "run" }

        assertEquals(
            setOf(PythonPositionBase.KwArgument("shell")),
            sink.condition!!.constantCmpPositions().map { it.base }.toSet(),
            "shell=True lands on kwarg(shell), not arg(*)",
        )
    }

    @Test fun `each constant keyword-arg condition keeps its own name`() {
        val sink = emitAll("python-rules/kwarg-const.yaml")
            .filterIsInstance<SerializedPythonSink>()
            .first { it.functionTarget() == "mrun" }

        assertEquals(
            setOf(PythonPositionBase.KwArgument("shell"), PythonPositionBase.KwArgument("check")),
            sink.condition!!.constantCmpPositions().map { it.base }.toSet(),
            "each keyword condition keeps its name (guard exemption preserves multi-kwarg)",
        )
    }

    @Test fun `constant positional after ellipsis stays any-argument`() {
        val sink = emitAll("python-rules/kwarg-const.yaml")
            .filterIsInstance<SerializedPythonSink>()
            .first { it.functionTarget() == "prun" }

        assertEquals(
            setOf(PythonPositionBase.Argument(null)),
            sink.condition!!.constantCmpPositions().map { it.base }.toSet(),
            "a positional `*->i` classifier must not be mistaken for a kwarg name",
        )
    }

    @Test fun `structural rule keyword condition serializes as a named position`() {
        val sink = emitAll("python-rules/kwarg-structural.yaml")
            .filterIsInstance<SerializedPythonSink>()
            .single { it.functionTarget() == "sink" }

        assertEquals(
            setOf(PythonPositionBase.KwArgument("mode")),
            sink.condition!!.constantCmpPositions().map { it.base }.toSet(),
            "structural mode=\"constant\" lands on kwarg(mode), not arg(*)",
        )
    }

    @Test fun `structural not-pattern cleaner keyword condition serializes as a named position`() {
        val cleaners = emitAll("python-rules/kwarg-not-cleaner.yaml")
            .filterIsInstance<SerializedPythonCleaner>()
            .filter { it.functionTarget() == "transform" }

        assertTrue(cleaners.isNotEmpty(), "expected a transform cleaner from pattern-not")
        assertEquals(
            setOf(PythonPositionBase.KwArgument("mode")),
            cleaners.flatMap { it.condition!!.constantCmpPositions() }.map { it.base }.toSet(),
            "the cleaner guard checks kwarg(mode)==\"safe\", not arg(*)",
        )
    }

    @Test fun `focused keyword-arg source taints the named position`() {
        val source = emit("python-rules/kwarg-source.yaml")
            .filterIsInstance<SerializedPythonSource>()
            .single { it.functionTarget() == "handler" }

        assertEquals(
            listOf(PythonPositionBase.KwArgument("payload")),
            source.taint.map { it.pos.base },
            "a focused kwarg source marks kwarg(payload); without the fix this is arg(*) and crashes at runtime",
        )
    }

    @Test fun `decorated method-signature return pattern lowers to a method-exit sink`() {
        val rules = emit("python-rules/return-sink.yaml")

        val source = rules.filterIsInstance<SerializedPythonSource>().single { it.functionTarget() == "source" }
        assertTrue(source.taint.all { it.pos.base == PythonPositionBase.Result }, "the in-body source taints its result")

        assertTrue(
            rules.filterIsInstance<SerializedPythonEntryPointSource>().isNotEmpty(),
            "the decorated method signature yields an entry-point source",
        )

        val exitSink = rules.filterIsInstance<SerializedPythonExitSink>().single()
        assertEquals(".*", exitSink.functionTarget(), "a return sink fires at any function's exit")
        assertEquals(
            setOf(PythonPositionBase.Result),
            exitSink.condition!!.markPositions().map { it.base }.toSet(),
            "the return sink checks containsMark(result)",
        )
    }

    private fun SerializedPythonCondition?.atoms(): List<SerializedPythonCondition> = when (this) {
        null -> emptyList()
        is SerializedPythonCondition.And -> allOf.flatMap { it.atoms() }
        is SerializedPythonCondition.Or -> anyOf.flatMap { it.atoms() }
        is SerializedPythonCondition.Not -> not.atoms()
        else -> listOf(this)
    }

    private fun SerializedPythonCondition?.decoratorNames(): List<String> =
        atoms().filterIsInstance<SerializedPythonCondition.MethodDecorated>().map { it.decorator }

    private fun SerializedPythonCondition.constantCmpPositions(): List<PythonPosition> =
        atoms().filterIsInstance<SerializedPythonCondition.ConstantCmp>().map { it.pos }

    private fun SerializedPythonCondition.markPositions(): List<PythonPosition> = atoms().mapNotNull {
        when (it) {
            is SerializedPythonCondition.ContainsMark -> it.pos
            is SerializedPythonCondition.ContainsMarkOnAnyAccessor -> it.pos
            else -> null
        }
    }
}
