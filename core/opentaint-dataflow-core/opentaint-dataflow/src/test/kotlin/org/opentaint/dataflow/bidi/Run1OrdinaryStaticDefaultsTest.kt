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

/** Stored defaults use the actual subscription and continuation, with no root analysis. */
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

    @Test fun `read-free callee preserves actual field through its ordinary N or D default`() {
        for (writes in listOf(false, true)) DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = p.method("callee", listOf(listOf(1), emptyList()), 1).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = p.field("f"); val t = p.mark("T")
            val path = listOf(cls, field)
            val actual = PathFact(STATIC, path, Tail.EXACT, MarkSlot.Concrete(t))
            p.entry(root, RuleStatement(f.summary(ZERO_KEEP, PathEdge(ZERO_FACT, actual,
                ExclusionSet.Empty)), StatementSummary.EMPTY, emptyList()))
            p.call(root, 1, call(f, callee))
            if (writes) p.statement(callee, 0, MicroEdgeBuilder().apply {
                write(STATIC, path, weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            val sink = SinkRule(f.rule("after call"), 0,
                listOf(Pattern(actual, ExclusionSet.Empty)), emptyList())
            p.exit(root, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))
            val result = f.run(p.config(roots = listOf(root)))
            assertEquals(RunStatus.COMPLETE, result.status)
            val actor = result.analyzers.single { it.key == callee }
            assertFalse(actor.nonZeroInitial, "A write target and an unused field do not seed an initial")
            val (premise, value) = actor.summaries.all().single { (premise, _) ->
                premise is InitialAp && isSpecialStaticRoot(premise)
            }
            assertEquals(if (writes) Layer.DEMAND else Layer.NORMAL, value.layer)
            assertEquals(Tail.STAR, f.ops.leaves(value).single().fact.tail)
            assertTrue(actor.summaries.demandEdges().any { it.first === premise },
                "Both normal guidance and demand defaults survive the completed barrier")
            assertTrue(f.processed.none { row -> row.delta.premise is InitialAp &&
                isSpecialStaticRoot(row.delta.premise as InitialAp) })
            val returned = f.processed.filter { it.method == root && it.delta.node.location.index == 2 }
                .filter { row -> f.ops.leaves(row.delta.facts).any { it.fact == actual } }
            assertTrue(returned.isNotEmpty())
            assertTrue(returned.any { it.delta.facts.layer == value.layer })
            assertTrue(result.witnessesByKey().any { it.key.rule == sink.rule })
        }
    }

    @Test fun `normal-only run-1 candidate enters strong refinement and no run-1 record is executable`() {
        DriverFixtures().use { f ->
            val c = f.confirmed()
            val report = f.driver(FixedLimits(listOf(1, 2, 3))).analyze(listOf(c.root))
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
