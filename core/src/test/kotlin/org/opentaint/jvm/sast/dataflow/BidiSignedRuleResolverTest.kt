package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSink
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSource
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.taint.SignedConditionNormalizer
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.rules.MethodTaintConfigurationResolver
import org.opentaint.jvm.sast.dataflow.rules.PatternManager
import org.opentaint.jvm.sast.dataflow.rules.TaintMarkManager
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.jvm.util.typename
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the shared resolver's real root/element specialization before bidi normalization. */
class BidiSignedRuleResolverTest : BasicTestUtils() {
    override val sourceFileExtension = "java"

    @Test fun `real serialized Object and array mark complements normalize to false for sources and sinks`(): Unit {
        for ((owner, name) in listOf("test.samples.bidi.BidiAcceptanceSample" to "mayPass",
            "test.samples.AnyFieldPrimitiveSample" to "elementFlow")) {
            val method = findMethod(owner, name)
            val arg = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
            val p = SerializedCondition.ContainsMark("T", arg)
            val condition = SerializedCondition.And(listOf(p, SerializedCondition.Not(p)))
            val function = SerializedFunctionNameMatcher.Simple(SerializedSimpleNameMatcher.Simple(owner.substringBeforeLast('.')),
                SerializedSimpleNameMatcher.Simple(owner.substringAfterLast('.')), SerializedSimpleNameMatcher.Simple(name))
            val resolver = MethodTaintConfigurationResolver(PatternManager(), TaintMarkManager(), cp, cp.objectClass.typename, method)
            val source = SerializedRule.Source(function, condition = condition,
                taint = listOf(SerializedTaintAssignAction("U", pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result))))
            val sink = SerializedRule.Sink(function, condition = condition, id = "signed-resolver")
            val resolved = with(resolver) { source.resolveRule() + sink.resolveRule() }
            assertEquals(2, resolved.size)
            for (rule in resolved) {
                val raw: Condition = when (rule) {
                    is TaintConfigurationSource -> rule.condition
                    is TaintConfigurationSink -> rule.condition
                    else -> error("unexpected rule")
                }
                assertFalse(raw.isFalse(), "The resolver produces a composite NNF, not a direct leaf complement")
                val positions = linkedSetOf<org.opentaint.dataflow.configuration.jvm.Position>()
                val todo = ArrayDeque<Condition>().also { it.add(raw) }
                while (todo.isNotEmpty()) when (val node = todo.removeLast()) {
                    is CommonCondition.Atom -> (node.atom as? ContainsMark)?.let { positions += it.position }
                    is CommonCondition.And -> todo.addAll(node.args)
                    is CommonCondition.Or -> todo.addAll(node.args)
                    is CommonCondition.Not -> todo.add(node.arg)
                    else -> Unit
                }
                assertEquals(2, positions.size, "$owner must resolve root and element")
                assertTrue(SignedConditionNormalizer.normalize(raw, negativeLiteralsAreTrue = true).isFalse(), owner)
            }
        }
    }
    @Test fun `new core resolves all sink roles with no end actions and leaves legacy unchanged`(): Unit {
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        val function = SerializedFunctionNameMatcher.Simple(
            SerializedSimpleNameMatcher.Simple("test.samples.bidi"),
            SerializedSimpleNameMatcher.Simple("BidiAcceptanceSample"),
            SerializedSimpleNameMatcher.Simple("mayPass"))
        val ends = listOf(SerializedTaintAssignAction("END_ONLY",
            pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)))
        val rules = listOf<SerializedRule>(
            SerializedRule.Sink(function, trackFactsReachAnalysisEnd = ends, id = "call"),
            SerializedRule.MethodEntrySink(function, trackFactsReachAnalysisEnd = ends, id = "entry"),
            SerializedRule.MethodExitSink(function, trackFactsReachAnalysisEnd = ends, id = "exit"),
        )
        fun resolve(ignore: Boolean): List<TaintConfigurationSink> {
            val resolver = MethodTaintConfigurationResolver(PatternManager(), TaintMarkManager(), cp,
                cp.objectClass.typename, method, ignoreSinkEndFacts = ignore)
            return with(resolver) { rules.flatMap { it.resolveRule() }.filterIsInstance<TaintConfigurationSink>() }
        }
        val legacy = resolve(false)
        val selected = resolve(true)
        assertEquals(3, selected.size)
        assertTrue(selected.all { it.trackFactsReachAnalysisEnd.isEmpty() })
        assertTrue(legacy.all { it.trackFactsReachAnalysisEnd.isNotEmpty() })
        assertTrue(rules.all { (it as SinkRule).trackFactsReachAnalysisEnd == ends }, "The serialized configuration stays full")
    }

}
