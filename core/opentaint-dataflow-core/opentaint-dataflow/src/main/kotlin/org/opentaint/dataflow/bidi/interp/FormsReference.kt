package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** One per-path result with its premise set. */
typealias ReferenceSink = (Set<Pattern>, Conclusion) -> Unit

/** Reference transfer, filtering and standing source support for one method and run. */
class ReferenceAlgebra(
    private val mode: ApMode,
    request: (Set<Pattern>, ReferenceRequestKind) -> Unit = { _, _ -> },
    allowsSource: (CommonInst, MicroEdge) -> Boolean = { _, _ -> true },
    sourceHit: (CommonInst, MicroEdge) -> Unit = { _, _ -> },
    private val manager: ApManager,
    conjunction: (ConjunctiveEdge, Set<Pattern>, Conclusion, CommonInst, ReferenceSink) -> Unit = { _, _, _, _, _ -> },
    private val historicalRun1: Boolean = false,
) : FactAlgebra<Set<Pattern>, Conclusion> {
    private val onRequest = request
    private val seedAllows = allowsSource
    private val onHit = sourceHit
    private val join = conjunction
    private val sourcePredicates = ReferenceSourcePredicates(manager)

    override fun base(c: Conclusion) = c.fact.base

    override fun filter(c: Conclusion, filter: TypeFilter): Conclusion? = c.takeIf { passes(filter, it) }

    override fun applyEdge(me: MicroEdge, c: Conclusion, premise: Set<Pattern>, statementEdge: Boolean, out: ReferenceSink) {
        val identity = historicalRun1 && mode.run1 && statementEdge && isStaticIdentity(premise, c)
        when (val o = concat(c, me.edge, edgeDemand = me.may, staticIdentity = identity, restricted = mode.restricted,
            run1 = mode.run1 && !historicalRun1)) {
            is EdgeOutcome.Fact -> out(premise, o.conclusion)
            is EdgeOutcome.Request -> if (mode.run1) onRequest(premise, ReferenceRequestKind.Mark(o.mark))
            is EdgeOutcome.PositionRequest -> if (mode.run1) onRequest(premise, ReferenceRequestKind.Position(manager.path(o.path)!!))
            EdgeOutcome.None -> Unit
        }
    }

    override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Conclusion, node: CommonInst, out: ReferenceSink) =
        join(cj, premise, c, node, out)
    override fun sourcePredicate(source: SourceRuleOccurrence, premise: Set<Pattern>, c: Conclusion, node: CommonInst,
                                 statementEdge: Boolean, out: ReferenceSink) =
        sourcePredicates.apply(source, premise, c, node, mode, statementEdge, ::applyEdge, onRequest, out)
    override fun allowsSource(node: CommonInst, me: MicroEdge) = seedAllows(node, me)
    override fun sourceHit(node: CommonInst, me: MicroEdge) = onHit(node, me)

    fun passes(filter: TypeFilter, c: Conclusion): Boolean {
        var may: FactTypeChecker.FactApFilter = filter.may
        for (a in c.fact.path) when (val r = may.check(manager.accessors.accessor(a))) {
            FactTypeChecker.FilterResult.Accept -> break
            FactTypeChecker.FilterResult.Reject -> return false
            is FactTypeChecker.FilterResult.FilterNext -> may = r.filter
        }
        val mark = (c.fact.mark as? MarkSlot.Concrete)?.mark ?: return true
        val policy = filter.markPolicy ?: return true
        if (c.fact.path.any { it != ELEMENT_ACCESSOR_IDX }) return true
        return policy.keeps(mark, elements = c.fact.path.size)
    }

    private fun isStaticIdentity(premise: Set<Pattern>, c: Conclusion): Boolean {
        val i = premise.singleOrNull()?.fact ?: return false
        val f = c.fact
        return i.base == AccessPathBase.ClassStatic && f.base == AccessPathBase.ClassStatic &&
            i.tail == Tail.STAR && f.tail == Tail.STAR && i.mark == MarkSlot.STAR && f.mark is MarkSlot.Star &&
            f.path == i.path && !c.demand && rootOrClass(i.path)
    }
}

/** One plan item carries its premise, conclusion and nullable identity candidate. */
data class PlanItem(val premise: Set<Pattern>, val c: Conclusion, val identityCandidate: Boolean?)

/** Hooks supply guards, callees, cleaners, statement cuts and exit cuts. */
class PlanHooks(
    val guards: Boolean = false,
    val atBound: (List<PlanItem>) -> List<Pair<SinkRule, Layer>> = { emptyList() },
    val callees: (CallStage.Callees, PlanItem) -> List<PlanItem> = { _, _ -> emptyList() },
    val clean: (Cleaner, PlanItem) -> List<PlanItem> = { _, it -> listOf(it) },
    val statementCut: (PlanItem) -> PlanItem = { it },
    val exit: (PlanItem) -> PlanItem = { it },
    val trigger: (SinkRule) -> List<PlanItem> = { emptyList() },
)

/** Per-path form execution and the reference walk of a call plan. */
class FormsReference(
    private val ops: ApOps,
    algebra: ReferenceAlgebra = ReferenceAlgebra(ApMode(run1 = true, Direction.FORWARD, fieldLimit = Int.MAX_VALUE),
        manager = ops.manager),
) {
    val applier = FormApplier(algebra)
    private val zeroPremise = setOf(ZERO_PATTERN)

    fun conclusions(f: Facts): List<Conclusion> {
        val demand = f.layer == Layer.DEMAND
        return when (f) {
            is Reach -> listOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, demand))
            else -> ops.leaves(f).map { Conclusion(it.fact, it.exclusion, demand) }.toList()
        }
    }

    enum class Mode { STATEMENT, STAGE, GEN }
    fun apply(mode: Mode, s: StatementSummary, premise: Set<Pattern>, c: Conclusion, at: Place): List<Conclusion> = buildList {
        when (mode) {
            Mode.STATEMENT -> applier.statement(s, premise, c, at, { _, x -> add(x) })
            Mode.STAGE -> applier.stage(s, premise, c, at) { _, x, _ -> add(x) }
            Mode.GEN -> applier.gen(s.edges, premise, c) { _, x -> add(x) }
        }
    }

    /** Compare complete leaf identities before store merging or subsumption. */
    fun agrees(mode: Mode, s: StatementSummary, at: Place, premise: Set<Pattern>, input: Facts, core: List<Facts>): Boolean {
        val ref = conclusions(input).flatMap { apply(mode, s, premise, it, at) }.toSet()
        val got = core.flatMap(::conclusions).toSet()
        return got == ref
    }

    /** Keep pending record exclusions until exit demotion, then apply the call cut. */
    fun run(plan: CallPlan, inputs: List<PlanItem>, at: Place, hooks: PlanHooks = PlanHooks(),
            from: CallPoint = plan.entry, pendingDemotion: Boolean = false): List<Pair<CallPoint, PlanItem>> {
        check(!pendingDemotion || (from == CallPoint.RETURNED && plan.exit == CallPoint.AFTER))
        fun finish(i: PlanItem): PlanItem {
            val result = if (!pendingDemotion) i else {
                check(i.c.fact.mark is MarkSlot.Concrete && i.c.fact.tail != Tail.STAR)
                i.copy(c = i.c.copy(demand = true), identityCandidate = false)
            }
            return hooks.exit(result)
        }
        fun statement(s: StatementSummary, x: PlanItem): List<PlanItem> = buildList {
            applier.statement(s, x.premise, x.c, at.copy(statementEdge = true, sources = false),
                sink = { pr, y -> add(hooks.statementCut(PlanItem(pr, y, x.identityCandidate))) },
                untouched = { pr, y -> add(PlanItem(pr, y, x.identityCandidate)) })
        }
        fun cleanSteps(steps: List<CleanStep>, inputs: List<PlanItem>): List<PlanItem> =
            steps.fold(inputs) { fs, step -> fs.flatMap { x -> when (step) {
                is CleanStep.Clean -> hooks.clean(step.cleaner, x)
                is CleanStep.Kill -> buildList { applier.statement(step.keepEdges, x.premise, x.c,
                    at.copy(statementEdge = true, sources = false), { pr, y -> add(PlanItem(pr, y, x.identityCandidate)) }) }
                is CleanStep.Field -> statement(step.read, x).flatMap { hooks.clean(step.cleaner, it) }
                    .flatMap { statement(step.write, it) }.filter { it.c.fact.base != step.temporary }
            } } }
        val seen = LinkedHashSet<Pair<CallPoint, PlanItem>>()
        val work = ArrayDeque<Pair<CallPoint, List<PlanItem>>>()
        val start = ArrayList<PlanItem>()
        for (input in inputs) {
            val i = if (pendingDemotion) input.copy(identityCandidate = false) else input
            when {
                from != plan.entry -> start += i
                i.c.fact.base == AccessPathBase.Zero -> { seen += plan.exit to finish(i); start += i }
                i.c.fact.base !in plan.touched -> seen += plan.exit to finish(i)
                else -> start += i
            }
        }
        if (start.isNotEmpty()) work.addLast(from to start)
        while (work.isNotEmpty()) {
            val (p, batch) = work.removeFirst()
            val items = batch.filter { seen.add(p to it) }
            if (items.isEmpty() || p == plan.exit) continue
            val fired = if (hooks.guards && p == CallPoint.BOUND) hooks.atBound(items) else emptyList()
            for (st in plan.stagesFrom[p].orEmpty()) {
                val out = ArrayList<PlanItem>()
                when (st) {
                    is CallStage.Edges -> {
                        val place = at.copy(statementEdge = st.kind.statementEdges, sources = st.kind == StageKind.SOURCES)
                        val guard = if (hooks.guards) st.guard else null
                        when {
                            guard is Guard.SinkTriggered ->
                                for ((sink, layer) in fired) if (sink.joinKey == guard.sink.joinKey) {
                                    val zero = Conclusion(ZERO_FACT, ExclusionSet.Empty, demand = layer == Layer.DEMAND)
                                    applier.stage(st.summary, zeroPremise, zero, place) { pr, x, me ->
                                        out += PlanItem(pr, x, st.kind.identityCandidateOf(me, null)) }
                                }
                            else -> {
                                for (i in items) {
                                    if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(checkNotNull(i.identityCandidate),
                                        if (i.c.demand) Layer.DEMAND else Layer.NORMAL)) continue
                                    applier.stage(st.summary, i.premise, i.c, place) { pr, x, me ->
                                        out += PlanItem(pr, x, st.kind.identityCandidateOf(me, i.identityCandidate)) }
                                }
                                val sink = st.trigger
                                if (sink != null && out.isNotEmpty()) out += hooks.trigger(sink)
                            }
                        }
                    }
                    is CallStage.Actions -> out += cleanSteps(st.steps, items)
                    is CallStage.Callees -> for (i in items) out += hooks.callees(st, i)
                }
                val next = if (st.to == plan.exit) out.map(::finish) else out
                if (next.isNotEmpty()) work.addLast(st.to to next)
            }
        }
        return seen.toList()
    }
}
