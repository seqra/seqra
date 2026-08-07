package org.opentaint.semgrep.pattern.conversion.go

import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCleanAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedPassAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.go.rules.ActionPosition
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.rules.GoTaintConfiguration
import org.opentaint.dataflow.go.rules.Position
import org.opentaint.dataflow.go.rules.RemoveMark
import org.opentaint.ir.go.type.GoIRUnsafePointerType
import org.opentaint.semgrep.go.pattern.conversion.loadGoTaintConfiguration
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoTaintRuleEmitterTest {

    private fun baseOnly(pos: PositionBase) = PositionBaseWithModifiers.BaseOnly(pos)

    private fun rule(vararg items: GoSerializedItem): TaintRuleFromSemgrep<GoSerializedItem> =
        TaintRuleFromSemgrep(
            "r",
            TaintRuleFromSemgrep.Structure.Matching(listOf(TaintRuleFromSemgrep.TaintRuleGroup(items.toList())))
        )

    @Test
    fun `source taints the result of a named function`() {
        val rule = rule(
            GoSerializedRule.Source(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Source"),
                condition = null,
                taint = listOf(GoSerializedAssignAction("taint", baseOnly(PositionBase.Result))),
                info = null,
            ),
        )

        val cfg = GoTaintConfiguration().loadGoTaintConfiguration(rule)

        val src = cfg.sourceForFunction("util.Source".signature(0), allRelevant = false).single()
        assertEquals("util.Source", src.function)
        assertEquals("taint", src.actionsAfter.single().mark)
        assertEquals(ActionPosition.Exact(Position.Result), src.actionsAfter.single().pos)
    }

    @Test
    fun `source with no taint action emits no actions`() {
        val rule = rule(
            GoSerializedRule.Source(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Source"),
                condition = null,
                taint = emptyList(),
                info = null,
            ),
        )

        val cfg = GoTaintConfiguration().loadGoTaintConfiguration(rule)
        assertTrue(cfg.sourceForFunction("util.Source".signature(0), allRelevant = false).single().actionsAfter.isEmpty())
    }

    @Test
    fun `sink with explicit id is preserved independently from serialized id`() {
        val rule = rule(
            GoSerializedRule.Sink(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Sink"),
                condition = GoSerializedCondition.ContainsMark("taint", baseOnly(PositionBase.Argument(0))),
                id = "explicit-id",
                info = null,
                serializedId = "explicit-serialized-id",
            ),
        )

        val cfg = GoTaintConfiguration().loadGoTaintConfiguration(rule)
        val sink = cfg.sinkForFunction("util.Sink".signature(1)).single()
        assertEquals("util.Sink", sink.function)
        assertEquals("explicit-id", sink.id)
        assertEquals("explicit-serialized-id", sink.serializedId)
    }

    @Test
    fun `pass-through copy action is resolved`() {
        val rule = rule(
            GoSerializedRule.PassThrough(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Wrap"),
                copy = listOf(
                    GoSerializedPassAction(
                        from = baseOnly(PositionBase.Argument(0)),
                        to = baseOnly(PositionBase.Result),
                    ),
                ),
            ),
        )

        val cfg = GoTaintConfiguration().loadGoTaintConfiguration(rule)
        val pass = cfg.passThroughForFunction("util.Wrap".signature(1)).single()
        assertEquals("util.Wrap", pass.function)
        assertEquals(1, pass.actionsAfter.size)
    }

    @Test
    fun `pattern-matcher rules resolve on callee-name lookup`() {
        val rule = rule(
            GoSerializedRule.Source(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Pattern(".*"),
                condition = null,
                taint = listOf(GoSerializedAssignAction("taint", baseOnly(PositionBase.Result))),
                info = null
            ),
        )

        // The pattern matcher survives — querying a concrete callee name that matches the
        // pattern materializes the rule on demand.
        val cfg = GoTaintConfiguration().loadGoTaintConfiguration(rule)
        assertTrue(cfg.sourceForFunction("util.Foo".signature(0), allRelevant = false).isNotEmpty())
        assertTrue(cfg.sourceForFunction("util.Bar".signature(0), allRelevant = false).isNotEmpty())
        // A name that does NOT match the pattern returns no rules.
        assertTrue(cfg.sourceForFunction("other.Foo".signature(0), allRelevant = false).isEmpty())
    }

    @Test
    fun `cleaner rule is resolved`() {
        val rule = rule(
            GoSerializedRule.Cleaner(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Clean"),
                cleans = listOf(GoSerializedCleanAction("taint", baseOnly(PositionBase.Argument(0)))),
                info = null,
            ),
        )

        val cfg = GoTaintConfiguration().loadGoTaintConfiguration(rule)
        assertEquals(1, cfg.cleanerForFunction("util.Clean".signature(1), allRelevant = false).size)
        assertEquals("util.Clean", cfg.cleanerForFunction("util.Clean".signature(1), allRelevant = false).single().function)
    }

    @Test
    fun `any-accessor cleaner lowers to RemoveMark with specialized position while direct stays exact`() {
        val pos = baseOnly(PositionBase.Argument(0))
        val anyPos = PositionBaseWithModifiers.WithModifiers(
            PositionBase.Argument(0),
            listOf(PositionModifier.AnyField),
        )

        val anyRule = rule(
            GoSerializedRule.Cleaner(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Clean"),
                cleans = listOf(GoSerializedCleanAction("taint", anyPos)),
                info = null,
            ),
        )
        val anyCfg = GoTaintConfiguration().loadGoTaintConfiguration(anyRule)
        val anyAction = anyCfg.cleanerForFunction("util.Clean".signature(1), allRelevant = false)
            .single().actionsAfter.filterIsInstance<RemoveMark>().single()
        assertEquals("taint", anyAction.mark)
        assertEquals(ActionPosition.AnyAccessorAfter(Position.Argument(0)), anyAction.pos)

        // Direct position must remain exact.
        val directRule = rule(
            GoSerializedRule.Cleaner(
                pkg = GoNameMatcher.Simple("util"),
                function = GoNameMatcher.Simple("Clean"),
                cleans = listOf(GoSerializedCleanAction("taint", pos)),
                info = null,
            ),
        )
        val directCfg = GoTaintConfiguration().loadGoTaintConfiguration(directRule)
        val directAction = directCfg.cleanerForFunction("util.Clean".signature(1), allRelevant = false)
            .single().actionsAfter.filterIsInstance<RemoveMark>().single()
        assertEquals("taint", directAction.mark)
        assertEquals(ActionPosition.Exact(Position.Argument(0)), directAction.pos)
    }

    private val anyType = GoIRUnsafePointerType

    fun String.signature(args: Int): GoFunctionSignature =
        GoFunctionSignature(this, receiverType = null, paramTypes = List(args) { anyType }, resultType = anyType)
}
