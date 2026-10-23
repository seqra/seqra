package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Select a fixture mode only while the production driver captures its immutable setting. */
internal fun <T> withRun1MarkMode(mode: MarkMode, body: () -> T): T {
    val property = "opentaint.bidi.run1MarkMode"
    val previous = System.getProperty(property)
    try {
        System.setProperty(property, mode.name)
        return body()
    } finally {
        if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
    }
}

data class ProgramCase(val root: MethodKey, val rule: RuleId, val sink: CommonInst, val source: PathEdge)
data class DriverInput(val run: Int, val callee: MethodKey, val link: Link)
data class DriverProcessed(val run: Int, val method: MethodKey, val delta: EdgeDelta)
data class DemandLinkCase(val root: MethodKey, val sourceMethod: MethodKey, val reader: MethodKey,
                          val sinkMethod: MethodKey, val rule: RuleId, val sink: CommonInst,
                          val source: PathEdge, val q: Int, val r: Int, val g: Int, val h: Int,
                          val mark: TaintMark)

/** Joined engine results are retained here only as explicit test observations. */
class DriverFixtures(threads: Int = 1) : AutoCloseable {
    val program = FiniteProgramFixtures(threads = threads)
    val shared get() = program.shared
    val manager get() = program.manager
    val ops get() = program.ops
    val records get() = program.records
    val vulnerabilities get() = program.vulnerabilities
    val cancellation get() = program.cancellation
    val refManager get() = program.refManager
    val configs = ArrayList<RunConfig>()
    val results = ArrayList<RunResult<RunMethodAnalyzer>>()
    val timeouts = ArrayList<Duration>()
    private val processRows = ConcurrentLinkedQueue<DriverProcessed>()
    private val inputRows = ConcurrentLinkedQueue<DriverInput>()
    fun links(run: Int, method: MethodKey): List<Link> = inputRows.filter { it.run == run && it.callee == method }.map { it.link }.distinct()
    val processed: List<DriverProcessed> get() = processRows.toList()

    fun manager(config: RunConfig, owner: SharedObjects = shared): RunManager<RunMethodAnalyzer> {
        val actual = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
            MethodCallServicesFactory(::RunMethodCallServices))
        val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
            val observed = object : RunnerPort<RunMethodAnalyzer> by port {
                override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
                    ops.leaves(added).forEach { inputRows += DriverInput(config.index, callee, Link(it, linkLayer, ref)) }
                    port.link(callee, ref, linkLayer, added)
                }
                override val onProcess: (MethodKey, EdgeDelta) -> Unit = { key, delta ->
                    processRows += DriverProcessed(config.index, key, delta)
                    port.onProcess?.invoke(key, delta)
                }
            }
            actual.create(observed)
        }
        return RunManager(config, owner, factory, quantum = 1)
    }

    fun run(config: RunConfig, owner: SharedObjects = shared,
            timeout: Duration = 5.seconds): RunResult<RunMethodAnalyzer> {
        configs += config
        timeouts += timeout
        return manager(config, owner).run(timeout).also(results::add)
    }

    fun driver(policy: IterationPolicy, budget: Duration = 5.seconds,
               invokeRun: (RunConfig, SharedObjects, Duration) -> RunResult<RunMethodAnalyzer> = this::run,
               guardBarrier: (SharedObjects, () -> Next) -> Next = { _, body -> body() }): IterationDriver =
        IterationDriver(policy, shared, budget, invokeRun, guardBarrier)

    fun rule(id: String): RuleId = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun sourceRules(edge: PathEdge): RuleStatement =
        RuleStatement(summary(ZERO_KEEP, edge), StatementSummary.EMPTY, emptyList())

    private fun exitSink(sink: SinkRule): ExitRules =
        ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet())

    /** A normal root witness remains an unproved candidate under the minimal report policy. */
    fun confirmed(): ProgramCase {
        val root = program.method("confirmed", listOf(listOf(1), emptyList()), 1).key()
        val mark = MarkSlot.Concrete(program.mark("confirmed T"))
        val base = AccessPathBase.LocalVar(0)
        val source = PathEdge(ZERO_FACT, PathFact(base, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty)
        val sink = SinkRule(rule("confirmed sink"), 0, listOf(program.pattern(base, mark = mark)), emptyList())
        program.entry(root, sourceRules(source))
        program.exit(root, 1, exitSink(sink))
        return ProgramCase(root, sink.rule, program.inst(root, 1), source)
    }

    /** The explicit may pass keeps this finding unconfirmed at every field limit. */
    fun persistentDemand(): ProgramCase {
        val root = program.method("persistent demand", listOf(listOf(1), listOf(2), emptyList()), 2).key()
        val mark = MarkSlot.Concrete(program.mark("persistent T"))
        val base = AccessPathBase.LocalVar(0)
        val source = PathEdge(ZERO_FACT, PathFact(base, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty)
        val may = PathEdge(source.to, PathFact(AccessPathBase.Return, emptyList(), Tail.ANY, mark), ExclusionSet.Empty)
        val sink = SinkRule(rule("persistent sink"), 0,
            listOf(program.pattern(AccessPathBase.Return, tail = Tail.ANY, mark = mark)), emptyList())
        program.entry(root, sourceRules(source))
        program.statement(root, 1, summary(may))
        program.exit(root, 2, exitSink(sink))
        return ProgramCase(root, sink.rule, program.inst(root, 2), source)
    }

    /** A longer exact source is cut in run 1 and recovered by a real backward source hit. */
    fun refining(): ProgramCase {
        val root = program.method("refining", listOf(listOf(1), emptyList()), 1).key()
        val mark = MarkSlot.Concrete(program.mark("refining T"))
        val path = listOf(program.field("a"), program.field("b"))
        val source = PathEdge(ZERO_FACT, PathFact(AccessPathBase.Return, path, Tail.EXACT, mark), ExclusionSet.Empty)
        val sink = SinkRule(rule("refining sink"), 0, listOf(Pattern(source.to, ExclusionSet.Empty)), emptyList())
        program.entry(root, sourceRules(source))
        program.exit(root, 1, exitSink(sink))
        return ProgramCase(root, sink.rule, program.inst(root, 1), source)
    }

    /** Two different normal call sites cannot jointly support one callee conjunction. */
    fun unsupportedJoint(): ProgramCase {
        val root = program.method("separate sites", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val callee = program.method("joint sink", listOf(listOf(1), emptyList()), 1).key()
        val first = AccessPathBase.LocalVar(0)
        val second = AccessPathBase.LocalVar(1)
        val arg0 = AccessPathBase.Argument(0)
        val arg1 = AccessPathBase.Argument(1)
        val t = MarkSlot.Concrete(program.mark("joint T"))
        val u = MarkSlot.Concrete(program.mark("joint U"))
        val source = PathEdge(ZERO_FACT, PathFact(first, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
        val other = PathEdge(ZERO_FACT, PathFact(second, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
        val sink = SinkRule(rule("unsupported joint sink"), 0,
            listOf(program.pattern(arg0, mark = t), program.pattern(arg1, mark = u)), emptyList())
        program.entry(root, RuleStatement(summary(ZERO_KEEP, source, other), StatementSummary.EMPTY, emptyList()))
        fun call(value: AccessPathBase, argument: AccessPathBase) = CallPlan(setOf(value), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(starEdge(value, emptyList(), argument, emptyList()), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER)
        program.call(root, 1, call(first, arg0))
        program.call(root, 2, call(second, arg1))
        program.exit(callee, 1, exitSink(sink))
        return ProgramCase(root, sink.rule, program.inst(callee, 1), source)
    }

    /** DLINK: srcAny; c(x) reads q then r; m cleans its root EXACT before the g.h sink and throws. */
    fun demandLink(): DemandLinkCase {
        val root = program.method("dlink root", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val sourceMethod = program.method("dlink srcAny", listOf(listOf(1), emptyList()), 1).key()
        val reader = program.method("dlink c", listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4).key()
        // The normal exit is unreachable. Reached requirements and the sink flow through the exceptional exit.
        val sinkMethod = program.method("dlink m", listOf(listOf(1), listOf(2), emptyList(), emptyList()),
            normalExit = 3, exceptionalExit = 2).key()
        val dto = AccessPathBase.LocalVar(0)
        val temporary = AccessPathBase.LocalVar(1)
        val value = AccessPathBase.LocalVar(2)
        val argument = AccessPathBase.Argument(0)
        val returned = AccessPathBase.Return
        val q = program.field("q")
        val r = program.field("r")
        val g = program.field("g")
        val h = program.field("h")
        val taint = program.mark("DLINK T")
        val mark = MarkSlot.Concrete(taint)
        val source = PathEdge(ZERO_FACT, PathFact(returned, emptyList(), Tail.ANY_TAINT, mark), ExclusionSet.Empty)
        program.entry(sourceMethod, sourceRules(source))
        fun resolved(callee: MethodKey, bindIn: StatementSummary, bindBack: StatementSummary,
                     touched: Set<AccessPathBase>, cleaner: Cleaner? = null): CallPlan = CallPlan(touched, listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, bindIn),
            CallStage.Actions(BOUND, ADDED, cleaner?.let { listOf(CleanStep.Clean(it)) }.orEmpty()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, bindBack),
        ), emptyList(), BEFORE, AFTER)
        program.call(root, 1, resolved(sourceMethod, summary(ZERO_KEEP),
            summary(starEdge(returned, emptyList(), dto, emptyList()), ZERO_KEEP), setOf(dto)))
        program.call(root, 2, resolved(reader, summary(starEdge(dto, emptyList(), argument, emptyList()), ZERO_KEEP),
            summary(ZERO_KEEP), setOf(dto)))
        program.statement(reader, 1, StatementSummary(setOf(argument, temporary),
            listOf(MicroEdge.of(keepEdge(argument)), MicroEdge.of(starEdge(argument, listOf(q), temporary, emptyList()))),
            emptyList(), emptyMap()))
        program.statement(reader, 2, StatementSummary(setOf(temporary, value),
            listOf(MicroEdge.of(keepEdge(temporary)), MicroEdge.of(starEdge(temporary, listOf(r), value, emptyList()))),
            emptyList(), emptyMap()))
        program.call(reader, 3, resolved(sinkMethod, summary(starEdge(value, emptyList(), argument, emptyList()), ZERO_KEEP),
            summary(ZERO_KEEP), setOf(value), Cleaner(argument, null, CleanReach.EXACT, taint)))
        val sink = SinkRule(rule("DLINK sink"), 0, listOf(program.pattern(argument, listOf(g, h), mark = mark)), emptyList())
        program.call(sinkMethod, 1, CallPlan(setOf(argument), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(argument), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(argument), ZERO_KEEP)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(argument), ZERO_KEEP)),
        ), listOf(sink), BEFORE, AFTER))
        return DemandLinkCase(root, sourceMethod, reader, sinkMethod, sink.rule, program.inst(sinkMethod, 1),
            source, q, r, g, h, taint)
    }

    override fun close(): Unit = program.close()
}
