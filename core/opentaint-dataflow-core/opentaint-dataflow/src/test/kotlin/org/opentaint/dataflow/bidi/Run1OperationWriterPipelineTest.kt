package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** Real caller subscriptions, Base request replay, static point replies and
 * fixed-group summary supply. No replacement request/supply solver is used. */
class Run1OperationWriterPipelineTest {
    private fun rule(name: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = name
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = name
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(callee: MethodKey, input: AccessPathBase? = null): CallPlan {
        val arg = AccessPathBase.Argument(0)
        val bind = listOf(ZERO_KEEP) + if (input == null) emptyList() else
            listOf(starEdge(input, emptyList(), arg, emptyList()))
        return CallPlan(setOf(AccessPathBase.Zero, STATIC, arg) + listOfNotNull(input), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(*bind.toTypedArray())),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER)
    }

    @Test fun `one lexical static copy has one writer across several caller summaries`() {
        for (policy in listOf(SchedulePolicy.FIFO, SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            FiniteProgramFixtures().use { f ->
                val main = f.method("copy main", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val middle = f.method("copy middle", listOf(listOf(1), emptyList()), 1).key()
                val leaf = f.method("copy leaf", listOf(listOf(1), emptyList()), 1).key()
                val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
                val from = listOf(cls, f.field("from")); val to = listOf(cls, f.field("to"))
                val full = f.mark("T")
                val source = PathEdge(ZERO_FACT, f.pattern(STATIC, from, mark = MarkSlot.Concrete(full)).fact, ExclusionSet.Empty)
                f.entry(main, RuleStatement(directStaticWrite(f, main, 0, source, summary(ZERO_KEEP, source)),
                    StatementSummary.EMPTY, emptyList()))
                f.call(main, 1, call(middle))
                f.call(middle, 0, call(leaf))
                val copy = starEdge(STATIC, from, STATIC, to)
                f.statement(leaf, 0, directStaticRead(f, leaf, 0, copy, summary(ZERO_KEEP, copy)))
                val sink = SinkRule(rule("copy sink"), 0,
                    listOf(f.pattern(STATIC, to, mark = MarkSlot.Concrete(full))), emptyList())
                val op = OperationId(FormKey(FormOwner(), main.method, main, f.inst(main, 2)))
                val checks = StatementSummary.EMPTY.withStaticOperations(listOf(StaticOperation.MarkInput(op, sink.patterns.single())))
                f.exit(main, 2, ExitRules(RuleStatement(checks, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
                val schedule = ProductionSchedule(f, f.config(roots = listOf(main)), policy = policy,
                    quantum = 1, duplicateDeliveries = true)
                val result = schedule.run()
                assertTrue(result.witnesses.any { it.rule == sink.rule })
                assertEquals(1, schedule.completedStaticStatistics!!.writerRows,
                    "The two callers retain summaries, not copies of the leaf writer")
                assertEquals(0, schedule.completedStaticStatistics!!.flowWriterRows)
                for (owner in listOf(main, middle, leaf)) {
                    val raw = result.rawSummaries.filter { row -> row.method == owner && row.conclusion.fact.path == to }
                    assertTrue(raw.any { row -> row.premise.singleOrNull()?.fact?.path == from &&
                        row.conclusion.fact.mark is MarkSlot.Star }, "Missing FLOW summary at $owner")
                    assertTrue(result.selectedDemand.containsAll(raw))
                }
            }
    }

    @Test fun `setter Base requests replay actual source and static-origin inputs through nested callers`() {
        for (staticInput in listOf(false, true)) for (lateGroup in listOf(false, true))
            for (policy in listOf(SchedulePolicy.FIFO, SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
                FiniteProgramFixtures().use { f ->
                    val main = f.method("setter main", listOf(listOf(1), listOf(2), listOf(3), listOf(4),
                        listOf(5), listOf(6), emptyList()), 6).key()
                    val middle = f.method("setter middle", listOf(listOf(1), emptyList()), 1).key()
                    val setter = f.method("setter leaf", listOf(listOf(1), emptyList()), 1).key()
                    val x = AccessPathBase.LocalVar(0); val arg = AccessPathBase.Argument(0)
                    val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
                    val from = listOf(cls, f.field("from")); val to = listOf(cls, f.field("to"))
                    val full = f.mark("T"); val other = f.mark("U")
                    fun source(mark: TaintMark): PathEdge = PathEdge(ZERO_FACT,
                        f.pattern(if (staticInput) STATIC else x, if (staticInput) from else emptyList(),
                            mark = MarkSlot.Concrete(mark)).fact, ExclusionSet.Empty)
                    val first = source(if (lateGroup) other else full)
                    val initial = if (staticInput) directStaticWrite(f, main, 0, first, summary(ZERO_KEEP, first))
                        else summary(ZERO_KEEP, first)
                    f.entry(main, RuleStatement(initial, StatementSummary.EMPTY, emptyList()))
                    if (staticInput) {
                        val read = starEdge(STATIC, from, x, emptyList())
                        f.statement(main, 1, directStaticRead(f, main, 1, read, summary(ZERO_KEEP, read)))
                    }
                    f.call(main, 2, call(middle, x))
                    f.call(middle, 0, call(setter, arg))
                    val write = starEdge(arg, emptyList(), STATIC, to)
                    f.statement(setter, 0, directStaticWrite(f, setter, 0, write, summary(ZERO_KEEP, write)))
                    val required = f.pattern(STATIC, to, mark = MarkSlot.Concrete(full))
                    val sink = SinkRule(rule("setter sink"), 0, listOf(required), emptyList())
                    val checkId = OperationId(FormKey(FormOwner(), main.method, main, f.inst(main, 3)))
                    val checks = StatementSummary.EMPTY.withStaticOperations(listOf(StaticOperation.MarkInput(checkId, required)))
                    f.call(main, 3, CallPlan(setOf(AccessPathBase.Zero, STATIC), listOf(
                        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(ZERO_KEEP)),
                        CallStage.Edges(BOUND, ADDED, SOURCES, checks),
                        CallStage.Edges(ADDED, AFTER, BIND_BACK, summary(ZERO_KEEP)),
                    ), listOf(sink), BEFORE, AFTER))
                    if (lateGroup) {
                        val later = source(full)
                        f.statement(main, 4, if (staticInput) directStaticWrite(f, main, 4, later, summary(ZERO_KEEP, later))
                            else summary(ZERO_KEEP, later, keepEdge(x)))
                        // New actual input for an old callee request. Static inputs
                        // can also answer the existing saved first-call point.
                        f.call(main, 5, call(middle, x))
                    }
                    val schedule = ProductionSchedule(f, f.config(roots = listOf(main)), policy = policy,
                        quantum = 1, duplicateDeliveries = true)
                    val result = schedule.run()
                    assertTrue(result.witnesses.any { it.rule == sink.rule },
                        "Missing computed supply: static=$staticInput late=$lateGroup policy=$policy")
                    val group = f.ap.marks.group(f.ap.marks.groupOf(full))
                    assertTrue(result.requests.any { it.method == setter &&
                        it.premise.fact.base == arg && it.kind == ReferenceRequestKind.Mark(group) })
                    assertTrue(result.links.any { it.callee == setter &&
                        (it.added.fact.mark as? MarkSlot.Concrete)?.mark == group })
                    assertEquals(0, schedule.completedStaticStatistics!!.writerRows,
                        "Call composition does not turn static-origin inputs into another FLOW writer")
                    assertEquals(1, schedule.completedStaticStatistics!!.flowWriterRows,
                        "The setter's local Base/FLOW relation is the only FLOW writer")
                    assertDirectStaticProtocol(result, f.ap.marks)
                }
    }
}
