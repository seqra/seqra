package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class CurrentRequest(val run: Int, val method: MethodKey, val premise: Pattern, val kind: RequestKind)
data class CurrentLink(val run: Int, val callee: MethodKey, val caller: CallerRef, val layer: Layer, val added: Pattern)
data class CurrentSummary(val premise: Set<Pattern>, val conclusion: Conclusion)
data class CurrentCut(val run: Int, val point: Cut, val facts: Facts)
data class CurrentCase(val root: MethodKey, val callee: MethodKey, val rules: List<RuleId>,
                       val sinks: CommonInst, val inputs: List<AccessPathBase>, val outputs: List<AccessPathBase>,
                       val t: TaintMark, val u: TaintMark, val fields: List<Int>, val fieldAction: CleanStep.Field? = null)

/** These observers do not replace AP operations, joins, delivery, or the driver barrier. */
class CurrentModeFixtures : AutoCloseable {
    val actual = DriverFixtures()
    val program get() = actual.program
    val manager get() = actual.manager
    val ops get() = actual.ops
    private val processRows = ConcurrentLinkedQueue<DriverProcessed>()
    private val requestRows = ConcurrentLinkedQueue<CurrentRequest>()
    private val linkRows = ConcurrentLinkedQueue<CurrentLink>()
    private val cutRows = ConcurrentLinkedQueue<CurrentCut>()
    val processed get() = processRows.toList()
    val requests get() = requestRows.toSet()
    val links get() = linkRows.toSet()
    val cuts get() = cutRows.toList()

    fun run(config: RunConfig, shared: SharedObjects, timeout: Duration): RunResult<RunMethodAnalyzer> {
        actual.configs += config
        actual.timeouts += timeout
        val local = MethodLocalServicesFactory { core ->
            val delegate = RunMethodLocalServices(core)
            val run = config.index
            val key = core.key
            object : MethodLocalServices by delegate {
                override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                    val kind = RequestKind.Mark(mark)
                    requestRows += CurrentRequest(run, key, premise.toPattern(), kind)
                    delegate.addMarkRequest(premise, mark)
                }
                override fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
                    marks.forEachMark { requestRows += CurrentRequest(run, key, premise.toPattern(), RequestKind.Mark(it)) }
                    delegate.addMarkRequests(premise, marks)
                }

            }
        }
        val handlers = RunMethodHandlers(local, MethodCallServicesFactory(::RunMethodCallServices))
        val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
            handlers.create(object : RunnerPort<RunMethodAnalyzer> by port {
                override fun subscribe(sub: Subscription) {
                    if (config.run1) for (leaf in ops.leaves(sub.added))
                        linkRows += CurrentLink(config.index, sub.callee, sub.ref, sub.linkLayer, leaf)
                    port.subscribe(sub)
                }
                override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
                    for (leaf in ops.leaves(added)) linkRows += CurrentLink(config.index, callee, ref, linkLayer, leaf)
                    port.link(callee, ref, linkLayer, added)
                }
                override val onProcess: (MethodKey, EdgeDelta) -> Unit = { key, delta ->
                    processRows += DriverProcessed(config.index, key, delta)
                    port.onProcess?.invoke(key, delta)
                }
                override val onCut: (Cut, Facts) -> Unit = { cut, facts ->
                    cutRows += CurrentCut(config.index, cut, facts)
                    port.onCut?.invoke(cut, facts)
                }
            })
        }
        return RunManager(config, shared, factory, quantum = 1).run(timeout).also(actual.results::add)
    }

    fun driver(): IterationDriver = actual.driver(FixedLimits(listOf(1, 2, 3)), 15.seconds, this::run)
    fun actor(run: Int, method: MethodKey): RunMethodAnalyzer =
        actual.results.single { it.runIndex == run }.analyzers.single { it.key == method }
    fun summaries(run: Int, method: MethodKey, selected: Boolean = false): Set<CurrentSummary> {
        val store = actor(run, method).summaries
        val values = if (selected) store.demandEdges() else store.all()
        return values.flatMap { (premise, facts) ->
            val members = premise.members.map { it.toPattern() }.toSet()
            ops.leaves(facts).map { CurrentSummary(members, Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND)) }
        }.toSet()
    }
    fun premises(run: Int, method: MethodKey): Set<Pattern> = processed.asSequence()
        .filter { it.run == run && it.method == method }
        .flatMap { it.delta.premise.members.asSequence() }.filterNot { it.isZero }.map { it.toPattern() }.toSet()
    fun patterns(run: Int, method: MethodKey, base: AccessPathBase): Set<DemandPattern> =
        actual.configs.single { it.index == run }.demand!!.near(method, base, null).toSet()
    fun p(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
          mark: MarkSlot = MarkSlot.STAR, exclusion: ExclusionSet = ExclusionSet.Empty): Pattern =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun summary(vararg edges: PathEdge): StatementSummary = actual.summary(*edges)
    private fun source(vararg edges: PathEdge): RuleStatement =
        RuleStatement(summary(ZERO_KEEP, *edges), StatementSummary.EMPTY, emptyList())
    private fun gen(base: AccessPathBase, mark: TaintMark, tail: Tail = Tail.EXACT): PathEdge =
        PathEdge(ZERO_FACT, PathFact(base, emptyList(), tail, MarkSlot.Concrete(mark)), ExclusionSet.Empty)
    private fun sink(rule: RuleId, base: AccessPathBase, mark: TaintMark, path: List<Int> = emptyList()): SinkRule =
        SinkRule(rule, 0, listOf(p(base, path, mark = MarkSlot.Concrete(mark))), emptyList())
    private fun sinks(key: MethodKey, index: Int, vararg sinks: SinkRule) {
        program.exit(key, index, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
            sinks.toList()), false, emptySet()))
    }
    private fun resolved(callee: MethodKey, input: AccessPathBase, output: AccessPathBase): CallPlan =
        CallPlan(setOf(input, output), listOf(
            CallStage.Edges(BEFORE, AFTER, PASS_OVER, summary(keepEdge(input))),
            CallStage.Edges(BEFORE, BOUND, BIND_IN,
                summary(starEdge(input, emptyList(), AccessPathBase.Argument(0), emptyList()), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                summary(starEdge(AccessPathBase.Return, emptyList(), output, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER)
    private fun unresolved(input: AccessPathBase, output: AccessPathBase, effect: StatementSummary,
                           cleaners: List<CleanStep>, returnBase: AccessPathBase = AccessPathBase.Return,
                           preserveInput: Boolean = true): CallPlan {
        val stages = ArrayList<CallStage>()
        if (preserveInput) stages += CallStage.Edges(BEFORE, AFTER, PASS_OVER, summary(keepEdge(input)))
        stages += CallStage.Edges(BEFORE, BOUND, BIND_IN,
            summary(starEdge(input, emptyList(), AccessPathBase.Argument(0), emptyList()), ZERO_KEEP))
        stages += CallStage.Actions(BOUND, ADDED, cleaners)
        stages += CallStage.Edges(ADDED, RETURNED, UNRESOLVED, effect)
        stages += CallStage.Actions(RETURNED, REWRITTEN, emptyList())
        stages += CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
            summary(starEdge(returnBase, emptyList(), output, emptyList()), ZERO_KEEP))
        return CallPlan(setOf(input, output), stages, emptyList(), BEFORE, AFTER)
    }
    private fun read(key: MethodKey, index: Int, from: AccessPathBase, field: Int, to: AccessPathBase) {
        program.statement(key, index, MicroEdgeBuilder().apply { read(to, from, listOf(field)) }.build())
    }
    private fun move(key: MethodKey, index: Int, from: AccessPathBase, to: AccessPathBase) {
        program.statement(key, index, MicroEdgeBuilder().apply { move(to, from) }.build())
    }
    private fun write(key: MethodKey, index: Int, base: AccessPathBase, field: Int, value: AccessPathBase) {
        program.statement(key, index, MicroEdgeBuilder().apply {
            write(base, listOf(field), weak = false, values = listOf(value), aliasPaths = emptyList())
        }.build())
    }

    fun sharedGetter(): CurrentCase {
        val root = program.method("current two marks root", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val get = program.method("current shared getter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
        val a = AccessPathBase.LocalVar(0); val b = AccessPathBase.LocalVar(1)
        val x = AccessPathBase.LocalVar(2); val y = AccessPathBase.LocalVar(3)
        val name = program.field("name"); val t = program.mark("shared T"); val u = program.mark("shared U")
        val rt = actual.rule("shared getter T"); val ru = actual.rule("shared getter U")
        program.entry(root, source(gen(a, t, Tail.ANY_TAINT), gen(b, u, Tail.ANY_TAINT)))
        program.call(root, 1, resolved(get, a, x)); program.call(root, 2, resolved(get, b, y))
        read(get, 1, AccessPathBase.Argument(0), name, AccessPathBase.Return)
        sinks(root, 3, sink(rt, x, t), sink(ru, y, u))
        return CurrentCase(root, get, listOf(rt, ru), program.inst(root, 3), listOf(a, b), listOf(x, y), t, u, listOf(name))
    }

    fun rootCleanerNormalization(): CurrentCase {
        val root = program.method("current normalization root", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val m = program.method("current normalized cleaner", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val a = AccessPathBase.LocalVar(0); val b = AccessPathBase.LocalVar(1)
        val x = AccessPathBase.LocalVar(2); val z = AccessPathBase.LocalVar(3); val y = AccessPathBase.LocalVar(4)
        val field = program.field("f"); val t = program.mark("normalization T"); val u = program.mark("normalization U")
        val rt = actual.rule("normalization blocked T"); val ru = actual.rule("normalization U")
        program.entry(root, source(gen(a, t, Tail.ANY_TAINT), gen(b, u, Tail.ANY_TAINT)))
        program.call(root, 1, resolved(m, a, x)); program.call(root, 2, resolved(m, b, z))
        read(m, 1, AccessPathBase.Argument(0), field, y)
        val cleaner = CleanStep.Clean(Cleaner(AccessPathBase.Argument(0), null, CleanReach.AT_AND_BELOW, t))
        program.call(m, 2, unresolved(y, y, summary(keepEdge(AccessPathBase.Argument(0)), ZERO_KEEP),
            listOf(cleaner), AccessPathBase.Argument(0), preserveInput = false))
        move(m, 3, y, AccessPathBase.Return)
        sinks(root, 3, sink(rt, x, t), sink(ru, z, u))
        return CurrentCase(root, m, listOf(rt, ru), program.inst(root, 3), listOf(a, b), listOf(x, z), t, u, listOf(field))
    }

    fun markChangingPass(): CurrentCase {
        val root = program.method("current pass root", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val c = program.method("current mark changing", listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4).key()
        val a = AccessPathBase.LocalVar(0); val value = AccessPathBase.LocalVar(1)
        val r = AccessPathBase.LocalVar(2); val y = AccessPathBase.LocalVar(3); val q = AccessPathBase.LocalVar(4)
        val h = program.field("h"); val t = program.mark("pass T"); val u = program.mark("pass U")
        val rule = actual.rule("changed mark U")
        program.entry(root, source(gen(value, t)))
        write(root, 1, a, h, value); program.call(root, 2, resolved(c, a, r))
        read(c, 1, AccessPathBase.Argument(0), h, y)
        val pass = PathEdge(PathFact(AccessPathBase.Argument(0), emptyList(), Tail.EXACT, MarkSlot.Concrete(t)),
            PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT, MarkSlot.Concrete(u)), ExclusionSet.Empty)
        program.call(c, 2, unresolved(y, q, summary(pass, ZERO_KEEP), emptyList()))
        move(c, 3, q, AccessPathBase.Return)
        sinks(root, 3, sink(rule, r, u))
        return CurrentCase(root, c, listOf(rule), program.inst(root, 3), listOf(a), listOf(r, y, q), t, u, listOf(h))
    }

    fun partialFieldCleaner(): CurrentCase {
        val root = program.method("current field cleaner root",
            listOf(listOf(1), listOf(2), listOf(3), listOf(4), listOf(5), listOf(6), emptyList()), 6).key()
        val c = program.method("current partial field cleaner",
            listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4).key()
        val a = AccessPathBase.LocalVar(0); val inner = AccessPathBase.LocalVar(1); val r = AccessPathBase.LocalVar(2)
        val y = AccessPathBase.LocalVar(3); val vt = AccessPathBase.LocalVar(10); val vg = AccessPathBase.LocalVar(11)
        val vu = AccessPathBase.LocalVar(12)
        val h = program.field("h"); val f = program.field("f"); val g = program.field("g"); val k = program.field("k")
        val t = program.mark("partial T"); val u = program.mark("partial U")
        val rf = actual.rule("cleaned f"); val rg = actual.rule("retained g"); val rk = actual.rule("retained k")
        program.entry(root, source(gen(vt, t), gen(vg, t), gen(vu, u)))
        write(root, 1, inner, f, vt); write(root, 2, inner, g, vg); write(root, 3, inner, k, vu)
        write(root, 4, a, h, inner); program.call(root, 5, resolved(c, a, r))
        read(c, 1, AccessPathBase.Argument(0), h, y)
        val action = fieldCleanStep(AccessPathBase.Argument(0), listOf(f), CleanReach.AT_AND_BELOW, t)
        program.call(c, 2, unresolved(y, y, summary(keepEdge(AccessPathBase.Argument(0)), ZERO_KEEP),
            listOf(action), AccessPathBase.Argument(0), preserveInput = false))
        move(c, 3, y, AccessPathBase.Return)
        sinks(root, 6, sink(rf, r, t, listOf(f)), sink(rg, r, t, listOf(g)), sink(rk, r, u, listOf(k)))
        return CurrentCase(root, c, listOf(rf, rg, rk), program.inst(root, 6), listOf(a), listOf(r, y),
            t, u, listOf(h, f, g, k), action)
    }

    override fun close(): Unit = actual.close()
}
