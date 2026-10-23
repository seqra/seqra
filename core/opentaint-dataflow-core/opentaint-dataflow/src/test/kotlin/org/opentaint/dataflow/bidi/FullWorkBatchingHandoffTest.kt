package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.fixtures.withRun1MarkMode
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Compare real completed closures, including the next backward source hit. */
class FullWorkBatchingHandoffTest {
    private data class Observation(val run1: Set<String>, val reports: Set<String>, val sourceHit: Boolean,
                                   val runIndices: List<Int>)

    private fun execute(scheduled: Boolean, threads: Int): Observation = withRun1MarkMode(MarkMode.FULL) {
        DriverFixtures(threads).use { f ->
            val p = f.program
            val main = p.method("full batching main", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val setter = p.method("full batching setter", listOf(listOf(1), emptyList()), 1).key()
            val getter = p.method("full batching getter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val wrapper = p.method("full batching wrapper", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val ret = AccessPathBase.Return
            val local = AccessPathBase.LocalVar(0)
            val child = p.field("child")
            val path = listOf(p.ap.accessors.index(ClassStaticAccessor("C")), p.field("f"))
            val t = p.mark("T")
            val u = p.mark("U")
            val mark = MarkSlot.Concrete(t)
            val source = PathEdge(ZERO_FACT, PathFact(STATIC, path + child, Tail.EXACT, mark), ExclusionSet.Empty)
            fun id(method: MethodKey, index: Int) =
                OperationId(FormKey(FormOwner(), method.method, method, p.inst(method, index)))
            p.statement(setter, 0, f.summary(ZERO_KEEP, source).withStaticOperations(listOf(
                StaticOperation.Write(id(setter, 0), Pattern(source.to, ExclusionSet.Empty), source))))
            val read = starEdge(STATIC, path, ret, emptyList())
            p.statement(getter, 1, f.summary(ZERO_KEEP, read).withStaticOperations(listOf(
                StaticOperation.Read(id(getter, 1), read))))

            fun call(callee: MethodKey, lhs: AccessPathBase?, clean: Boolean = false): CallPlan {
                val raw = if (clean) listOf(t, u, t).map {
                    CleanStep.Clean(Cleaner(ret, null, CleanReach.EXACT, it))
                } else emptyList()
                val actions = if (scheduled && clean) CallStage.Actions.run1FullReturnRewriter(raw, f.manager.marks)
                    else CallStage.Actions(RETURNED, REWRITTEN, raw)
                val back = listOf(ZERO_KEEP, keepEdge(STATIC)) + if (lhs == null) emptyList()
                    else listOf(starEdge(ret, emptyList(), lhs, emptyList()))
                return CallPlan(setOf(AccessPathBase.Zero, STATIC, ret) + listOfNotNull(lhs), listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP, keepEdge(STATIC))),
                    CallStage.Actions(BOUND, ADDED, emptyList()),
                    CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                    actions,
                    CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(*back.toTypedArray())),
                ), emptyList(), BEFORE, AFTER)
            }
            p.call(main, 1, call(setter, null))
            p.call(main, 2, call(wrapper, local))
            p.call(wrapper, 1, call(getter, ret, clean = true))
            val sink = SinkRule(f.rule("full batching child sink"), 0,
                listOf(p.pattern(local, listOf(child), mark = mark)), emptyList())
            p.exit(main, 3, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))

            val report = f.driver(FixedLimits(listOf(1, 1, 2)), 20.seconds).analyze(listOf(main))
            assertEquals(RunStatus.COMPLETE, report.end.status)
            assertTrue(f.results.all { it.status == RunStatus.COMPLETE })
            assertEquals(listOf(1, 2, 3), f.results.map { it.runIndex },
                "The PART result must keep the next backward and forward iteration reachable")
            assertTrue(f.results.all { it.markMode == MarkMode.FULL })
            fun encode(pattern: Pattern): String {
                val fact = pattern.fact
                val marks = when (val m = fact.mark) {
                    is MarkSlot.Concrete -> f.manager.marks.name(m.mark)
                    is MarkSlot.Star -> "FLOW:" + m.excluded.ids.map { f.manager.marks.name(TaintMark(it)) }.sorted()
                }
                return "${fact.base}/${fact.path.map { f.manager.accessors.accessor(it) }}/${fact.tail}/${pattern.exclusion}/$marks"
            }
            val rows = f.results.first().analyzers.flatMap { actor ->
                actor.summaries.all().flatMap { (premise, facts) ->
                    val initial = premise.members.map { encode(it.toPattern()) }.sorted()
                    f.ops.leaves(facts).map { "${actor.key.method.name}:$initial -> ${encode(it)}/${facts.layer}" }
                }
            }.toSet()
            val sourceHit = f.results.single { it.runIndex == 2 }.analyzers.any { actor ->
                actor.sourceHits?.occurrences()?.any { it.method == setter && it.forward == source } == true
            }
            assertTrue(sourceHit, "Run-1 demand must guide run-2 to the marked static source")
            assertTrue(report.entries.any { it.key.rule == sink.rule }, "The child survives an EXACT root clean")
            Observation(rows, report.entries.map { it.key.rule.id }.toSet(), sourceHit, f.results.map { it.runIndex })
        }
    }

    @Test fun `FULL return blocks retain static demand and the run2 source path at both actor counts`() {
        val reference = execute(scheduled = false, threads = 1)
        for (threads in listOf(1, 2)) assertEquals(reference, execute(scheduled = true, threads))
    }
}
