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

class GroupedStaticPipelineTest {
    private fun call(f: DriverFixtures, callee: MethodKey, lhs: AccessPathBase? = null): CallPlan {
        val back = if (lhs == null) arrayOf(ZERO_KEEP, keepEdge(STATIC)) else arrayOf(ZERO_KEEP, keepEdge(STATIC),
            starEdge(AccessPathBase.Return, emptyList(), lhs, emptyList()))
        return CallPlan(setOf(AccessPathBase.Zero, AccessPathBase.Return, STATIC) + listOfNotNull(lhs), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP, keepEdge(STATIC))),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(*back)),
        ), emptyList(), BEFORE, AFTER)
    }

    @Test fun `getter and dead-local wrappers keep a full backward path to the static source`() {
        DriverFixtures().use { f ->
            val p = f.program
            val main = p.method("main", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val setter = p.method("setter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val wrapper = p.method("wrapper", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val deep = p.method("deep", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val getter = p.method("getter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val local = AccessPathBase.LocalVar(0)
            val argument = AccessPathBase.Argument(0)
            val returned = AccessPathBase.Return
            val full = p.mark("source T")
            val mark = MarkSlot.Concrete(full)
            val path = listOf(p.ap.accessors.index(ClassStaticAccessor("C")), p.field("f"))
            val static = PathFact(STATIC, path, Tail.STAR, MarkSlot.STAR)
            val source = PathEdge(ZERO_FACT, PathFact(local, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty)
            p.entry(setter, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val write = starEdge(local, emptyList(), STATIC, path)
            val writeId = OperationId(FormKey(FormOwner(), setter.method, setter, p.inst(setter, 1)))
            p.statement(setter, 1, f.summary(write, keepEdge(local)).withStaticOperations(listOf(
                StaticOperation.Write(writeId, Pattern(write.to, ExclusionSet.Empty), write),
            )))
            val read = PathEdge(static, PathFact(returned, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            val readId = OperationId(FormKey(FormOwner(), getter.method, getter, p.inst(getter, 1)))
            p.statement(getter, 1, f.summary(read).withStaticOperations(listOf(StaticOperation.Read(readId, read))))
            p.call(main, 1, call(f, setter))
            p.call(main, 2, call(f, wrapper))
            p.call(wrapper, 1, call(f, deep))
            p.call(deep, 1, call(f, getter, local))
            val sink = SinkRule(f.rule("sink T"), 0, listOf(p.pattern(argument, mark = mark)), emptyList())
            p.call(deep, 2, CallPlan(setOf(AccessPathBase.Zero, local, argument), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN,
                    f.summary(ZERO_KEEP, starEdge(local, emptyList(), argument, emptyList()))),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, f.summary(ZERO_KEEP, keepEdge(argument))),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(ZERO_KEEP)),
            ), listOf(sink), BEFORE, AFTER))

            val driver = f.driver(FixedLimits(listOf(1, 1, 2)), budget = 20.seconds)
            val report = driver.analyze(listOf(main))
            assertEquals(listOf(1, 2, 3), f.results.map { it.runIndex }, report.end.toString())
            assertTrue(f.results.all { it.status == RunStatus.COMPLETE })
            val forward = f.results.first()
            assertTrue(f.processed.filter { it.run == 1 && it.delta.node == it.method.statement }
                .none { row -> (0 until row.delta.premise.size).any { row.delta.premise.member(it).base == STATIC } },
                "Static point descriptors must not start a method-entry analysis")
            assertTrue(forward.analyzers.single { it.key == getter }.summaries.all().any { (premise, value) ->
                premise.member(0).base == STATIC && value.base == returned && value.layer == Layer.DEMAND
            })
            val backward = f.results[1]
            val hits = backward.analyzers.flatMap { it.sourceHits?.occurrences()?.toList().orEmpty() }
            assertTrue(hits.any { it.method == setter && it.forward == source },
                "Run-2 must find the full-mark source through the real caller CFG and static write")
            assertTrue(report.entries.any { it.key.rule == sink.rule }, buildString {
                appendLine("Missing final sink. End: ${report.end}")
                for (run in f.results) {
                    appendLine("run-${run.runIndex}: witnesses=${run.witnessesByKey().keys}")
                    for (actor in run.analyzers) {
                        appendLine("  ${actor.key.method.name}: ${actor.sourceHits?.occurrences()?.toList()}")
                        appendLine("    ${actor.summaries.all().toList().map { (initial, value) -> initial to f.ops.leaves(value).toList() }}")
                    }
                }
            })
        }
    }
}
