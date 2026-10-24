package org.opentaint.common.sast.rules

import org.opentaint.dataflow.configuration.selection.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** These tests exercise the shared compiler independently of method binding. */
class MarkCatalogNormalizationTest {
    private val yes = MarkExpression.Truth(true)
    private fun cm(mark: String, query: Any? = null) = MarkExpression.ContainsMark(mark, query)
    private fun source(mark: String, guard: MarkExpression = yes, id: String = "source-$mark") =
        MarkCatalogRow(id, RuleRole.SOURCE, guard, listOf(mark), 0)
    private fun sink(guard: MarkExpression, id: String = "sink") =
        MarkCatalogRow(id, RuleRole.SINK, guard, emptyList(), 0)

    @Test fun `assigned and checked intersection spans the complete executable catalog`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            source("T", id = "method-A-source"),
            source("unused", id = "method-C-source"),
            sink(MarkExpression.Or(listOf(cm("T"), cm("unassigned"))), "method-B-sink"),
        )))
        assertEquals(setOf("T"), plan.liveMarkNames)
        assertFalse(plan.isLive("unused"))
        assertFalse(plan.isLive("unassigned"))
        assertEquals(1, plan.removedAssignments)
        assertEquals(1, plan.removedRows)
    }

    @Test fun `dropping an actionless source removes its last input consumer in the next round`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            source("V", cm("U")),
            source("U"),
            source("T"),
            sink(cm("T")),
        )))
        assertEquals(setOf("T"), plan.liveMarkNames)
        assertEquals(2, plan.removedRows)
        assertEquals(2, plan.removedAssignments)
        assertTrue(plan.rounds >= 3, "At least two changing rounds and the fixed-point check are required")
    }

    @Test fun `a false last producer cannot keep a downstream mark check alive`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            source("T", MarkExpression.Truth(false)),
            sink(cm("T")),
        )))
        assertEquals(emptySet(), plan.liveMarkNames)
        assertEquals(2, plan.removedRows)
        assertFalse(plan.isLive("T"))
    }

    @Test fun `an empty true sink remains an observable rule`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(sink(yes))))
        assertEquals(emptySet(), plan.liveMarkNames)
        assertEquals(0, plan.removedRows)
    }

    @Test fun `only source and cleaner roles lose empty true rows`() {
        val plan = reduceMarkCatalog(MarkCatalog(RuleRole.entries.map { role ->
            MarkCatalogRow(role.name, role, yes, emptyList(), 0)
        }))
        assertEquals(2, plan.removedRows)
        assertEquals(0, plan.removedAssignments)
    }

    @Test fun `structural cleaner and pass actions remain even without a live mark`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            MarkCatalogRow("clean-all", RuleRole.CLEANER, yes, emptyList(), 1),
            MarkCatalogRow("copy", RuleRole.PASS, yes, emptyList(), 1),
        )))
        assertEquals(0, plan.removedRows)
    }

    @Test fun `metadata references do not contribute assignments or checks`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            source("assigned-only").copy(referencedMarks = setOf("metadata-only")),
            sink(cm("checked-only")).copy(referencedMarks = setOf("assigned-only", "metadata-only")),
        )))
        assertEquals(emptySet(), plan.liveMarkNames)
        assertEquals(setOf("assigned-only", "checked-only", "metadata-only"), plan.knownMarkNames)
        assertFalse(plan.isLive("metadata-only"))
    }

    @Test fun `protected roots and observers describe executable imports and outputs`() {
        val plan = reduceMarkCatalog(MarkCatalog(
            rows = listOf(sink(cm("imported")), source("observed"), source("unused")),
            protectedProducers = setOf("imported"),
            protectedConsumers = setOf("observed"),
        ))
        assertEquals(setOf("imported", "observed"), plan.liveMarkNames)
        assertFalse(plan.isLive("unused"))
    }

    @Test fun `selected out END rows cannot protect a mark in the executable inventory`() {
        val end = sink(yes, "discarded-end").copy(
            protectedProducers = setOf("END"), protectedConsumers = setOf("END"),
        )
        val plan = reduceMarkCatalog(MarkCatalog(listOf(end, source("unused"))),
            keepExecution = { it.itemId != "discarded-end" })
        assertEquals(emptySet(), plan.liveMarkNames)
        assertFalse(plan.isLive("END"))
    }

    @Test fun `a false END row cannot protect its actions for later rounds`() {
        val end = sink(MarkExpression.Truth(false)).copy(
            assignments = listOf("END"),
            protectedProducers = setOf("END"), protectedConsumers = setOf("END"),
        )
        val plan = reduceMarkCatalog(MarkCatalog(listOf(end, sink(cm("END"), "consumer"))))
        assertEquals(emptySet(), plan.liveMarkNames)
        assertEquals(2, plan.removedRows)
    }

    @Test fun `one time symbolic simplification rejects a complementary producer guard`() {
        val contradiction = MarkExpression.And(listOf(cm("T"), MarkExpression.Not(cm("T"))))
        val plan = reduceMarkCatalog(MarkCatalog(listOf(source("T", contradiction), sink(cm("T")))))
        assertEquals(emptySet(), plan.liveMarkNames)
        assertEquals(2, plan.removedRows)
        assertEquals(MarkExpression.Truth(false), contradiction.simplify())
    }

    @Test fun `unresolved executable scope disables absence rewrites`() {
        val plan = reduceMarkCatalog(MarkCatalog(
            listOf(source("T"), sink(cm("U"))), protectAll = true,
        ))
        assertTrue(plan.protectAll)
        assertTrue(plan.isLive("T"))
        assertTrue(plan.isLive("U"))
        assertTrue(plan.isLive("custom-input"))
        assertEquals(0, plan.removedRows)
    }

    @Test fun `a key outside the closed known catalog keeps its compatibility path`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(source("unused"))))
        assertFalse(plan.isLive("unused"))
        assertTrue(plan.isLive("foreign-generated-at-runtime"))
    }

    @Test fun `selection happens before executable producer and consumer inventory`() {
        val snapshot = RuleSelectionSnapshot(1, setOf("keep-source", "keep-sink"), emptySet(),
            setOf("keep-source", "keep-sink", "discarded-source"), ReducedMarkPlan.ALL)
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            source("T", id = "keep-source"),
            source("U", id = "discarded-source"),
            sink(MarkExpression.Or(listOf(cm("T"), cm("U"))), "keep-sink"),
        )), keepExecution = { snapshot.keepsRaw(it.itemId, it.role, allRelevant = false) })
        assertEquals(setOf("T"), plan.liveMarkNames)
        assertFalse(plan.isLive("U"))
    }

    @Test fun `complete full keys do not merge marks from different owners`() {
        val names = listOf("ownerA::T", "ownerB::T")
        val identities = mapOf(
            names[0] to RuleMarkIdentity("ownerA", false, names[0]),
            names[1] to RuleMarkIdentity("ownerB", false, names[1]),
        )
        val plan = reduceMarkCatalog(MarkCatalog(listOf(
            source(names[0]), sink(cm(names[1])),
        )), identities = identities)
        assertEquals(emptySet(), plan.liveMarkNames)
        assertFalse(plan.isLive(names[0]))
        assertFalse(plan.isLive(names[1]))
    }

    @Test fun `invalid identity correspondence keeps the original behavior`() {
        val plan = reduceMarkCatalog(MarkCatalog(listOf(source("T"))),
            identities = mapOf("T" to RuleMarkIdentity("owner", false, "different")))
        assertTrue(plan.protectAll)
        assertTrue(plan.isLive("T"))
    }

    @Test fun `live CM leaves keep their complete position and query kind`() {
        for (query in listOf("arg0", "arg1-any-field", "return-any-accessor")) {
            val original = cm("T", query)
            assertSame(original, original.foldAbsent { it == "T" })
            assertEquals(MarkExpression.Truth(false), original.foldAbsent { false })
        }
    }

    @Test fun `absent CM folding preserves Boolean meaning including negative guards`() {
        val guards = listOf(
            cm("dead"), MarkExpression.Not(cm("dead")),
            MarkExpression.And(listOf(cm("T"), MarkExpression.Not(cm("dead")))),
            MarkExpression.Or(listOf(cm("dead"), cm("U"))),
            MarkExpression.Not(MarkExpression.Or(listOf(cm("dead"), cm("U")))),
            MarkExpression.And(listOf(MarkExpression.Other("type"), cm("T"))),
            MarkExpression.Or(listOf(MarkExpression.Not(cm("T")), MarkExpression.Other("type"))),
            MarkExpression.And(emptyList()), MarkExpression.Or(emptyList()),
        )
        for (guard in guards) for (t in listOf(false, true)) for (u in listOf(false, true)) {
            for (typeMatches in listOf(false, true)) {
                val valuation = mapOf("T" to t, "U" to u, "dead" to false)
                val folded = guard.foldAbsent { it != "dead" }
                assertEquals(evaluate(guard, valuation, typeMatches), evaluate(folded, valuation, typeMatches),
                    "guard=$guard T=$t U=$u type=$typeMatches")
            }
        }
    }

    @Test fun `snapshot owns copies of the selected sets and mark plan`() {
        val ids = linkedSetOf("source")
        val live = linkedSetOf("T")
        val known = linkedSetOf("T", "unused")
        val plan = ReducedMarkPlan(live, known)
        val snapshot = RuleSelectionSnapshot(1, ids, ids, ids, plan)
        ids.clear(); live.clear(); known.clear()
        assertEquals(setOf("source"), snapshot.executionIds)
        assertEquals(setOf("source"), snapshot.metadataIds)
        assertEquals(setOf("source"), snapshot.knownIds)
        assertTrue(snapshot.markPlan.isLive("T"))
        assertFalse(snapshot.markPlan.isLive("unused"))
    }

    private fun evaluate(expression: MarkExpression, values: Map<String, Boolean>, other: Boolean): Boolean =
        when (expression) {
            is MarkExpression.Truth -> expression.value
            is MarkExpression.ContainsMark -> values.getValue(expression.name)
            is MarkExpression.Other -> other
            is MarkExpression.Not -> !evaluate(expression.argument, values, other)
            is MarkExpression.And -> expression.arguments.all { evaluate(it, values, other) }
            is MarkExpression.Or -> expression.arguments.any { evaluate(it, values, other) }
        }
}
