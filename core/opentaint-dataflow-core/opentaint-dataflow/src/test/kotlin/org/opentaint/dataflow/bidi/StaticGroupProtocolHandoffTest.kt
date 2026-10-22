package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** The real first check is inside the getter; caller checks cannot create its GROUP summary. */
class StaticGroupProtocolHandoffTest {
    private fun call(f: DriverFixtures, callee: MethodKey, lhs: AccessPathBase? = null): CallPlan {
        val back = listOf(ZERO_KEEP, keepEdge(STATIC)) + if (lhs == null) emptyList()
            else listOf(starEdge(AccessPathBase.Return, emptyList(), lhs, emptyList()))
        return CallPlan(setOf(AccessPathBase.Zero, AccessPathBase.Return, STATIC) + listOfNotNull(lhs), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP, keepEdge(STATIC))),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(*back.toTypedArray())),
        ), emptyList(), BEFORE, AFTER)
    }

    @Test fun `group-refined getter and returning wrapper keep all method pairs and the full run2 source path`() {
        DriverFixtures().use { f ->
            val p = f.program
            p.enableGroupRuleViews()
            val main = p.method("protocol main", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val setter = p.method("protocol setter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val getter = p.method("protocol getter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val wrapper = p.method("protocol wrapper", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val local = AccessPathBase.LocalVar(0)
            val returned = AccessPathBase.Return
            val path = listOf(p.ap.accessors.index(ClassStaticAccessor("C")), p.field("f"))
            val full = p.mark("source T")
            val mark = MarkSlot.Concrete(full)
            val source = PathEdge(ZERO_FACT, PathFact(STATIC, path, Tail.EXACT, mark), ExclusionSet.Empty)
            val writeId = OperationId(FormKey(FormOwner(), setter.method, setter, p.inst(setter, 1)))
            p.statement(setter, 1, f.summary(ZERO_KEEP, source).withStaticOperations(listOf(
                StaticOperation.Write(writeId, Pattern(source.to, ExclusionSet.Empty), source))))
            val read = starEdge(STATIC, path, returned, emptyList())
            val readId = OperationId(FormKey(FormOwner(), getter.method, getter, p.inst(getter, 1)))
            p.statement(getter, 1, f.summary(ZERO_KEEP, read).withStaticOperations(listOf(StaticOperation.Read(readId, read))))
            val getterCheck = SinkRule(f.rule("getter real group refinement"), 0,
                listOf(p.pattern(returned, mark = mark)), emptyList())
            p.exit(getter, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(getterCheck)), false, emptySet()))
            val sink = SinkRule(f.rule("caller after wrapper"), 0, listOf(p.pattern(local, mark = mark)), emptyList())
            p.exit(main, 3, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))
            p.call(main, 1, call(f, setter)); p.call(main, 2, call(f, wrapper, local))
            p.call(wrapper, 1, call(f, getter, returned))

            val report = f.driver(FixedLimits(listOf(1, 1, 2)), 20.seconds).analyze(listOf(main))
            assertEquals(RunStatus.COMPLETE, report.end.status)
            val run1 = f.results.first()
            assertEquals(RunStatus.COMPLETE, run1.status)
            val group = MarkSlot.Concrete(f.manager.marks.group(f.manager.marks.groupOf(full)))
            val original = p.pattern(STATIC, path, Tail.STAR, group)
            val output = p.pattern(returned, tail = Tail.ANY, mark = group)
            for (method in listOf(getter, wrapper)) {
                val actor = run1.analyzers.single { it.key == method }
                val selected = actor.summaries.demandEdges().flatMap { (premise, facts) ->
                    f.ops.leaves(facts).map { premise.toPatternForTest() to it }
                }.toSet()
                assertTrue(original to output in selected, "This exact method retains the concrete static original")
                assertTrue(DemandPattern(output, original) in checkNotNull(f.configs[1].demand).near(method, output),
                    "The next run receives the full ordered pair, not only a return entry")
            }
            val setterEntry = p.pattern(STATIC, path, Tail.EXACT, group)
            assertTrue(DemandPattern(setterEntry, f.manager.zero.toPattern()) in
                checkNotNull(f.configs[1].demand).near(setter, setterEntry), "ZERO is an explicit backward exit")
            val backward = f.results.single { it.runIndex == 2 }
            assertTrue(backward.analyzers.flatMap { it.sourceHits?.occurrences()?.toList().orEmpty() }
                .any { it.method == setter && it.forward == source })
            assertTrue(report.entries.any { it.key.rule == sink.rule })
            assertTrue(run1.witnessesByKey().keys.any { it.rule == getterCheck.rule })
        }
    }

    private fun PremiseKey.toPatternForTest(): Pattern {
        assertEquals(1, size, "Run-1 keeps atomic originals")
        return member(0).toPattern()
    }
}
