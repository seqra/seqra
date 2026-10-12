package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.util.RefManager
import org.opentaint.dataflow.util.SoftReferenceManager
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

class FormStorageStatisticsTest {
    private val fixture = StoreFixtures()
    private val x = AccessPathBase.Argument(0)

    private fun summary(bases: Int, copies: Int = 1): StatementSummary {
        val touched = List(bases) { AccessPathBase.Argument(it) }.toSet()
        return StatementSummary(touched, touched.flatMap { base -> List(copies) { identityEdge(base) } },
            emptyList(), emptyMap())
    }

    private fun measured(form: StatementSummary): FormCacheStatistics.Snapshot {
        val statistics = FormCacheStatistics()
        FormsCache<StatementSummary>(1, statistics = statistics) { it.reversed() }
            .get(0, Direction.FORWARD) { form }
        return statistics.snapshot()
    }

    @Test fun `empty singleton and indexed summaries count their actual retained storage`(): Unit {
        val empty = measured(StatementSummary.EMPTY)
        assertEquals(1L, empty.forwardFormShapes)
        assertEquals(1L, empty.statementSummaries)
        assertEquals(0L, empty.ownedContainerEntries)
        assertEquals(0L, empty.primitiveIndexCells)
        assertEquals(0L, empty.summaryBaseDirectoryEntries)

        val single = measured(summary(1))
        // touched + edge + target + singleton directory key/value. Its value reuses edges.
        assertEquals(5L, single.ownedContainerEntries)
        assertEquals(0L, single.primitiveIndexCells)
        assertEquals(1L, single.summaryBaseDirectoryEntries)
        val oneGroup = measured(summary(1, copies = 2))
        assertEquals(6L, oneGroup.ownedContainerEntries)
        assertEquals(0L, oneGroup.primitiveIndexCells)

        val indexed = measured(summary(9, copies = 2))
        // Nine touched/target entries, 18 edges, and nine directory key/value pairs.
        assertEquals(54L, indexed.ownedContainerEntries)
        // Three 32-cell hash indexes plus one shared 18-cell group ordinal array.
        assertEquals(114L, indexed.primitiveIndexCells)
        assertEquals(9L, indexed.summaryBaseDirectoryEntries)
    }

    @Test fun `fixed call point directory counts retained slots and one shared group index`(): Unit {
        fun plan(points: List<CallPoint>) = CallPlan(emptySet(), points.map { point ->
            CallStage.Edges(point, CallPoint.AFTER, StageKind.SOURCES, StatementSummary.EMPTY)
        }, emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        fun measured(plan: CallPlan): FormCacheStatistics.Snapshot {
            val statistics = FormCacheStatistics()
            FormsCache<CallPlan>(1, statistics = statistics) { it.reversed() }
                .get(0, Direction.FORWARD) { plan }
            return statistics.snapshot()
        }
        val empty = measured(plan(emptyList()))
        assertEquals(1L, empty.callPlans)
        assertEquals(0L, empty.ownedContainerEntries)
        assertEquals(0L, empty.callPointDirectoryEntries)
        val single = measured(plan(listOf(CallPoint.BEFORE)))
        assertEquals(3L, single.ownedContainerEntries)
        assertEquals(0L, single.primitiveIndexCells)
        assertEquals(1L, single.callPointDirectoryEntries)
        val many = measured(plan(listOf(CallPoint.BEFORE, CallPoint.BOUND, CallPoint.BEFORE, CallPoint.BOUND)))
        assertEquals(10L, many.ownedContainerEntries, "Four stages and six retained point slots")
        assertEquals(6L, many.primitiveIndexCells, "Two point-order cells and four shared group ordinals")
        assertEquals(2L, many.callPointDirectoryEntries)
        assertEquals(1L, many.statementSummaries, "The shared EMPTY summary is sampled once")
    }

    @Test fun `rule and exit roots count shared nested summaries only once per sample`(): Unit {
        val rules = RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, emptyList())
        val exit = ExitRules(rules, false, setOf(fixture.mark("T"), fixture.mark("U")))
        val statistics = FormCacheStatistics()
        FormsCache<ExitRules>(1, statistics = statistics) { it }
            .get(0, Direction.FORWARD) { exit }
        val counts = statistics.snapshot()
        assertEquals(1L, counts.exitRules)
        assertEquals(1L, counts.ruleStatements)
        assertEquals(1L, counts.statementSummaries)
        assertEquals(2L, counts.ownedContainerEntries)
        assertEquals(0L, counts.primitiveIndexCells)
    }

    @Test fun `reversal rebuild and failures account only successful form shape samples`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val statistics = FormCacheStatistics()
        var rejectReverse = true
        val cache = FormsCache<StatementSummary>(1, refs, statistics) {
            if (rejectReverse) error("reverse") else it.reversed()
        }
        assertFailsWith<IllegalStateException> { cache.get(0, Direction.FORWARD) { error("build") } }
        assertEquals(0L, statistics.snapshot().forwardFormShapes)
        assertFailsWith<IllegalStateException> { cache.get(0, Direction.BACKWARD) { summary(1) } }
        assertEquals(1L, statistics.snapshot().forwardFormShapes)
        assertEquals(0L, statistics.snapshot().reversedFormShapes)
        rejectReverse = false
        cache.get(0, Direction.BACKWARD) { error("unexpected build") }
        repeat(3) { cache.get(0, Direction.BACKWARD) { error("cache hit rebuilt") } }
        assertEquals(1, refs.cleanup())
        refs.enable()
        cache.get(0, Direction.BACKWARD) { summary(1) }
        val counts = statistics.snapshot()
        assertEquals(2L, counts.forwardFormShapes)
        assertEquals(2L, counts.reversedFormShapes)
        assertEquals(4L, counts.statementSummaries)
        assertEquals(20L, counts.ownedContainerEntries)
        assertEquals(4L, counts.summaryBaseDirectoryEntries)
        assertEquals(0L, counts.primitiveIndexCells)
        assertEquals(1L, counts.buildFailures)
        assertEquals(1L, counts.reversalFailures)
    }

    @Test fun `shape readers keep compatibility views lazy and do not inspect AP payloads`(): Unit {
        val opaquePath = object : AbstractList<Int>() {
            override val size: Int get() = error("AP path inspected by diagnostics")
            override fun get(index: Int): Int = error("AP path inspected by diagnostics")
        }
        val side = PathFact(x, opaquePath, Tail.STAR, MarkSlot.STAR)
        val edge = MicroEdge.of(PathEdge(side, side, ExclusionSet.Empty))
        val opaque = StatementSummary(setOf(x), listOf(edge), emptyList(), emptyMap())
        assertEquals(5L, measured(opaque).ownedContainerEntries)

        val indexed = summary(9, copies = 2)
        measured(indexed)
        val directory = indexed.javaClass.getDeclaredField("byBase").apply { isAccessible = true }.get(indexed)
        assertNull(directory.javaClass.superclass.getDeclaredField("entryView").apply { isAccessible = true }.get(directory))
        val plan = CallPlan(emptySet(), listOf(
            CallStage.Edges(CallPoint.BEFORE, CallPoint.AFTER, StageKind.SOURCES, indexed),
            CallStage.Edges(CallPoint.BOUND, CallPoint.AFTER, StageKind.SOURCES, indexed)),
            emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        val statistics = FormCacheStatistics()
        FormsCache<CallPlan>(1, statistics = statistics) { it.reversed() }.get(0, Direction.FORWARD) { plan }
        assertNull(plan.stagesFrom.javaClass.getDeclaredField("entryView").apply { isAccessible = true }.get(plan.stagesFrom))
    }

    @Test fun `aggregate retains numbers only and unknown cache payloads have no form measurements`(): Unit {
        val statistics = FormCacheStatistics()
        FormsCache<StatementSummary>(1, statistics = statistics) { it.reversed() }
            .get(0, Direction.FORWARD) { summary(9, copies = 2) }
        val before = statistics.snapshot()
        FormsCache<Any>(1, statistics = statistics) { it }.get(0, Direction.FORWARD) { Any() }
        val after = statistics.snapshot()
        assertEquals(before.forwardFormShapes, after.forwardFormShapes)
        assertEquals(before.ownedContainerEntries, after.ownedContainerEntries)
        assertEquals(before.primitiveIndexCells, after.primitiveIndexCells)
        for (field in statistics.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) })
            assertEquals(AtomicLong::class.java, field.type, "Diagnostics retain no form or collector: ${field.name}")
        val plain = FormsCache<StatementSummary>(1) { it.reversed() }
        plain.get(0, Direction.FORWARD) { summary(1) }
        assertNull(plain.javaClass.getDeclaredField("statistics").apply { isAccessible = true }.get(plain))
    }
}
