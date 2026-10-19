package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*

/** Static handoff guidance does not create an executable run-1 default summary. */
class Run1OrdinaryStaticDefaultsTest {
    private fun call(f: DriverFixtures, callee: MethodKey): CallPlan {
        val identity = f.summary(keepEdge(STATIC), ZERO_KEEP)
        return CallPlan(setOf(STATIC), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, identity),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, identity),
        ), emptyList(), BEFORE, AFTER)
    }

    @Test fun `read-free calls need neither static input relay nor an executable root default`() {
        for (writes in listOf(false, true)) DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = p.method("callee", listOf(listOf(1), emptyList()), 1).key()
            val unseen = p.method("unseen", listOf(emptyList()), 0).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = p.field("f"); val t = p.mark("T")
            val path = listOf(cls, field)
            val actual = PathFact(STATIC, path, Tail.EXACT, MarkSlot.Concrete(t))
            val source = PathEdge(ZERO_FACT, actual, ExclusionSet.Empty)
            p.staticWrite(root, 0, Pattern(actual, ExclusionSet.Empty), source, f.summary(ZERO_KEEP, source))
            p.call(root, 1, call(f, callee))
            if (writes) p.statement(callee, 0, MicroEdgeBuilder().apply {
                write(STATIC, path, weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            val sink = SinkRule(f.rule("after call"), 0,
                listOf(Pattern(actual, ExclusionSet.Empty)), emptyList())
            val check = StatementSummary.EMPTY.withStaticOperations(listOf(StaticOperation.MarkInput(
                OperationId(FormKey(FormOwner(), root.method, root, p.inst(root, 2))), Pattern(actual, ExclusionSet.Empty))))
            p.exit(root, 2, ExitRules(RuleStatement(check, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))
            val config = p.config(roots = listOf(root))
            val result = f.run(config)
            assertEquals(RunStatus.COMPLETE, result.status)
            val actor = result.analyzers.single { it.key == callee }
            assertFalse(actor.nonZeroInitial, "A write target and an unused field do not seed an initial")
            assertTrue(actor.summaries.all().all { (premise, value) -> premise.isZero && value is Reach },
                "A clear with no tainted RHS does not create a new static value or root default")
            assertTrue(f.processed.none { row -> row.delta.facts.base == STATIC },
                "The global writer pool replaces static CFG relay through the call")
            assertTrue(result.witnessesByKey().any { it.key.rule == sink.rule })
            val report = ReportBuilder().also { it.add(config, result) }
            val handoff = HandOff.of(config, result, report, f.shared)
            val group = MarkSlot.Concrete(f.manager.marks.group(f.manager.marks.groupOf(t)))
            val expected = DemandPattern(p.pattern(STATIC, tail = Tail.ANY, mark = group),
                p.pattern(STATIC, tail = Tail.STAR, mark = group))
            for (method in listOf(root, callee, unseen)) {
                assertContains(handoff.demand.near(method, Pattern(actual, ExclusionSet.Empty)), expected)
            }
        }
    }

    @Test fun `group-valued run-1 candidate enters exact refinement without a normal taint record`() {
        DriverFixtures().use { f ->
            val c = f.confirmed()
            val report = f.driver(FixedLimits(listOf(1, 1, 2))).analyze(listOf(c.root))
            assertEquals(RunStatus.COMPLETE, report.end.status)
            assertTrue(f.configs.size >= 2, "Normal run-1 work is still a refinement candidate")
            val backwards = f.configs.single { it.index == 2 }
            val fact = Pattern(c.source.to, ExclusionSet.Empty)
            assertTrue(backwards.records.byEntry(c.root, fact).none())
            assertTrue(backwards.records.byExit(c.root, fact).none())
            assertTrue(report.entries.any { it.key.rule == c.rule })
        }
    }
}
