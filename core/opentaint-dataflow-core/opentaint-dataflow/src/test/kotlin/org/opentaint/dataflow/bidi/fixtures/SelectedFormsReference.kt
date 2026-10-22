package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** Scalar selected transfer: no packed AP kernel, emitter, request table or static-frame reader. */
internal class SelectedReferenceAlgebra(
    private val mode: ApMode,
    private val request: (Set<Pattern>, ReferenceRequestKind) -> Unit,
    private val allows: (CommonInst, MicroEdge) -> Boolean,
    private val hit: (CommonInst, MicroEdge) -> Unit,
    private val manager: ApManager,
    private val join: (ConjunctiveEdge, Set<Pattern>, Conclusion, CommonInst, ReferenceSink) -> Unit,
) : FactAlgebra<Set<Pattern>, Conclusion> {
    private val sourcePredicates = GroupedSourcePredicates(manager, mode.run1 && mode.markMode == MarkMode.GROUP)
    private val groups = if (mode.run1 && mode.markMode == MarkMode.GROUP) GroupedScalarMarks(manager.marks) else null
    private val filters = ReferenceAlgebra(mode, manager = manager)
    override fun base(c: Conclusion) = c.fact.base
    override fun filter(c: Conclusion, filter: TypeFilter) = c.takeIf { filters.passes(filter, c) }
    override fun allowsSource(node: CommonInst, me: MicroEdge) = allows(node, me)
    override fun sourceHit(node: CommonInst, me: MicroEdge) = hit(node, me)
    override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Conclusion,
                             node: CommonInst, out: ReferenceSink) = join(cj, premise, c, node, out)
    override fun sourcePredicate(source: SourceRuleOccurrence, premise: Set<Pattern>, c: Conclusion,
                                 node: CommonInst, statementEdge: Boolean, out: ReferenceSink) =
        sourcePredicates.apply(source, premise, c, node, mode, statementEdge, ::applyEdge, request, out,
            allows = { allows(node, it) }, hit = { hit(node, it) })
    override fun applyEdge(me: MicroEdge, c: Conclusion, premise: Set<Pattern>,
                           statementEdge: Boolean, out: ReferenceSink) {
        if (mode.run1 && c.fact.base == me.edge.from.base && c.fact.mark is MarkSlot.Star)
            for (id in (me.edge.to.mark as? MarkSlot.Star)?.excluded?.ids ?: intArrayOf())
                request(premise, ReferenceRequestKind.Mark(groups?.atom(TaintMark(id)) ?: TaintMark(id)))
        when (val o = groups?.apply(c, me.edge, me.may) ?: concat(c, me.edge, edgeDemand = me.may, restricted = mode.restricted, run1 = mode.run1)) {
            is EdgeOutcome.Fact -> out(premise, o.conclusion)
            is EdgeOutcome.Request -> if (mode.run1) request(premise, ReferenceRequestKind.Mark(o.mark))
            is EdgeOutcome.PositionRequest -> error("Selected statics have no Position protocol")
            EdgeOutcome.None -> Unit
        }
    }
}

/** Per-path form execution and the reference walk of a call plan. */
internal class SelectedFormsReference(
    private val ops: ApOps,
    algebra: FactAlgebra<Set<Pattern>, Conclusion>,
    private val run1AliasAdmission: Boolean = true,
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
                i.copy(c = i.c.copy(demand = true), identityCandidate = if (run1AliasAdmission) i.identityCandidate else false)
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
            val i = if (pendingDemotion && !run1AliasAdmission) input.copy(identityCandidate = false) else input
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
                        if (st.kind == StageKind.END_FACTS) continue
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
                                    if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(run1AliasAdmission, i.identityCandidate,
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
