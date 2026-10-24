package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCleanAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedGlobalSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.go.serialized.GoUserDefinedRuleInfo
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.configuration.selection.RuleSelectionSnapshot
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.GoGlobalFieldSignature
import org.opentaint.ir.go.type.GoIRUnsafePointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GoSelectedRuleViewTest {
    private val result = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    private val argument = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
    private val matcher = GoNameMatcher.Simple("test")
    private fun signature(name: String = "f") =
        GoFunctionSignature("test.$name", null, listOf(GoIRUnsafePointerType), GoIRUnsafePointerType)

    private fun snapshot(
        execution: Set<String> = setOf("selected"),
        metadata: Set<String> = setOf("selected", "metadata"),
        live: Set<String>? = null,
    ) = RuleSelectionSnapshot(1, execution, metadata, setOf("selected", "metadata", "discarded"),
        ReducedMarkPlan(live, setOf("T", "U")))

    private fun source(id: String, mark: String = "T", condition: GoSerializedCondition? = null,
                       info: GoUserDefinedRuleInfo? = null) = GoSerializedRule.Source(
        matcher, GoNameMatcher.Simple("f"), condition,
        listOf(GoSerializedAssignAction.Direct(mark, result)), info, id)

    @Test
    fun `true and false source caches cannot choose each others selected rows`() {
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(source = listOf(source("selected"), source("metadata"), source("discarded"))))
        }
        for (trueFirst in listOf(true, false)) {
            val view = config.selectedView(snapshot())
            if (trueFirst) view.sourceForFunction(signature(), true) else view.sourceForFunction(signature(), false)
            assertEquals(listOf("selected"), view.sourceForFunction(signature(), false).map { it.serializedId })
            assertEquals(listOf("selected", "metadata"), view.sourceForFunction(signature(), true).map { it.serializedId })
        }
        assertEquals(3, config.sourceForFunction(signature(), false).size)
    }

    @Test
    fun `unsafe selected out global source keeps original validation error`() {
        val invalid = GoSerializedGlobalSource(matcher, GoNameMatcher.Simple("global"), null,
            listOf(GoSerializedAssignAction.Direct("T", argument)), null, "discarded")
        val config = GoTaintConfiguration().apply { loadConfig(GoSerializedTaintConfig(globalSource = listOf(invalid))) }
        val sig = GoGlobalFieldSignature("test.global", GoIRUnsafePointerType)
        assertFailsWith<IllegalStateException> { config.selectedView(snapshot()).sourceForGlobal(sig) }
        assertFailsWith<IllegalStateException> { config.sourceForGlobal(sig) }
    }

    @Test
    fun `unsafe sink memo and counter are shared across views and selection changes`() {
        fun sink(id: String, function: String) = GoSerializedRule.Sink(
            matcher, GoNameMatcher.Simple(function), null, info = null, serializedId = id)
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(sink = listOf(sink("discarded", "f"), sink("selected", "f"), sink("selected", "g"))))
        }
        val before = config.sinkForFunction(signature())
        val view = config.selectedView(snapshot())
        val selected = view.sinkForFunction(signature()).single()
        assertSame(before[1], selected)
        assertEquals("go-generated-id-2", selected.id)
        assertEquals("go-generated-id-3", view.sinkForFunction(signature("g")).single().id)
        assertSame(selected, config.selectedView(snapshot()).sinkForFunction(signature()).single())
        assertSame(before[0], config.sinkForFunction(signature())[0])
    }

    @Test
    fun `metadata uses live reset targets without dropping unused mark carriers or rewriting guard`() {
        val info = object : GoUserDefinedRuleInfo { override val relevantTaintMarks = linkedSetOf("T", "U") }
        val guard = GoSerializedCondition.Not(GoSerializedCondition.ContainsMark("U", argument))
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(source = listOf(source("metadata", "U", guard, info))))
        }
        val row = config.selectedView(snapshot(live = setOf("T"))).sourceForFunction(signature(), true).single()
        assertSame(info, row.info)
        assertEquals("U", row.actionsAfter.single().mark)
        assertEquals(setOf("T"), row.effectiveRelevantTaintMarks)
        assertTrue(row.condition is CommonCondition.Not)
        assertTrue((row.condition as CommonCondition.Not).arg is CommonCondition.Atom)
    }

    @Test
    fun `executable CM any accessor and negative CM fold after binding without altering retained actions`() {
        val guard = GoSerializedCondition.Not(GoSerializedCondition.ContainsMarkOnAnyAccessor("U", argument))
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(source = listOf(source("selected", "T", guard))))
        }
        val broad = config.sourceForFunction(signature(), false).single()
        val row = config.selectedView(snapshot(live = setOf("T"))).sourceForFunction(signature(), false).single()
        assertTrue(row.condition.isTrue())
        assertEquals(broad.actionsAfter, row.actionsAfter)
        assertSame(broad.info, row.info)
        assertEquals(broad.serializedId, row.serializedId)
    }

    @Test
    fun `cleaner execution remains broad and structural cleaning survives empty live marks`() {
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(cleaner = listOf(GoSerializedRule.Cleaner(
                matcher, GoNameMatcher.Simple("f"), cleans = listOf(GoSerializedCleanAction(null, result)),
                info = null, serializedId = "discarded"))))
        }
        val view = config.selectedView(snapshot(live = emptySet()))
        assertTrue(view.cleanerForFunction(signature(), false).single().actionsAfter.single() is RemoveAllMarks)
        assertTrue(view.cleanerForFunction(signature(), true).isEmpty())
    }

    @Test
    fun `metadata cleaner preserves guard and positions while executable cleaner folds absence`() {
        val info = object : GoUserDefinedRuleInfo { override val relevantTaintMarks = linkedSetOf("T", "U") }
        val guard = GoSerializedCondition.Not(GoSerializedCondition.ContainsMark("U", argument))
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(cleaner = listOf(GoSerializedRule.Cleaner(
                matcher, GoNameMatcher.Simple("f"), guard,
                listOf(GoSerializedCleanAction("U", result)), info, "metadata"))))
        }
        for (trueFirst in listOf(true, false)) {
            val view = config.selectedView(snapshot(live = setOf("T")))
            if (trueFirst) view.cleanerForFunction(signature(), true) else view.cleanerForFunction(signature(), false)
            val metadata = view.cleanerForFunction(signature(), true).single()
            val executable = view.cleanerForFunction(signature(), false).single()
            assertTrue(metadata.condition is CommonCondition.Not)
            assertEquals(setOf("T"), metadata.effectiveRelevantTaintMarks)
            assertEquals(RemoveMark("U", Position.Result), metadata.actionsAfter.single())
            assertTrue(executable.condition.isTrue())
            assertEquals(metadata.actionsAfter, executable.actionsAfter)
            assertSame(info, metadata.info)
        }
    }

    @Test
    fun `unknown and null IDs follow metadata and executable policies independently`() {
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(source = listOf(
                source("unknown"), source("selected").copy(serializedId = null), source("discarded"))))
        }
        val view = config.selectedView(snapshot())
        assertEquals(listOf<String?>(null), view.sourceForFunction(signature(), false).map { it.serializedId })
        assertEquals(listOf("unknown", null), view.sourceForFunction(signature(), true).map { it.serializedId })
    }

    @Test
    fun `catalog reload invalidates old selected epoch without resetting fallback counter`() {
        val sink = GoSerializedRule.Sink(matcher, GoNameMatcher.Simple("f"), null,
            info = null, serializedId = "selected")
        val config = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(source = listOf(source("selected")), sink = listOf(sink)))
        }
        assertEquals("go-generated-id-1", config.sinkForFunction(signature()).single().id)
        val view = config.selectedView(snapshot())
        val original = view.sourceForFunction(signature(), false).single()
        config.loadConfig(GoSerializedTaintConfig(source = listOf(source("metadata"))))
        assertFailsWith<IllegalStateException> { view.sourceForFunction(signature(), false) }
        val replacement = config.selectedView(snapshot()).sourceForFunction(signature(), false).single()
        assertNotSame(original, replacement)
        assertEquals(2, config.sourceForFunction(signature(), false).size)
        assertEquals("go-generated-id-2", config.sinkForFunction(signature()).single().id)
    }

    @Test
    fun `expanded any argument complements fold without changing different binding or query kinds`() {
        val sig = signature().copy(paramTypes = List(2) { GoIRUnsafePointerType })
        val any = GoSerializedCondition.ContainsMark("T",
            PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(null)))
        val positive = GoSerializedCondition.ContainsMark("T", argument)
        val second = GoSerializedCondition.ContainsMark("T",
            PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(1)))
        val otherKind = GoSerializedCondition.ContainsMarkOnAnyAccessor("T", argument)
        fun condition(guard: GoSerializedCondition): List<TaintRule.Source> = GoTaintConfiguration().apply {
            loadConfig(GoSerializedTaintConfig(source = listOf(source("selected", "T", guard))))
        }.selectedView(snapshot(live = setOf("T"))).sourceForFunction(sig, false)
        assertTrue(condition(GoSerializedCondition.And(listOf(any, GoSerializedCondition.Not(any)))).isEmpty())
        assertTrue(condition(GoSerializedCondition.Or(listOf(any, GoSerializedCondition.Not(any)))).single().condition.isTrue())
        assertFalse(condition(GoSerializedCondition.Or(listOf(positive, GoSerializedCondition.Not(second)))).single().condition.isTrue())
        assertFalse(condition(GoSerializedCondition.Or(listOf(positive, GoSerializedCondition.Not(otherKind)))).single().condition.isTrue())
    }
}
