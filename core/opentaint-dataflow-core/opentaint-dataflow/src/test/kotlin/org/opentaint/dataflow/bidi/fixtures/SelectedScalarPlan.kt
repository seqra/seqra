package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.CommonMethod

/** Deliberately flat finite-spec model. No production static profile or request store is read. */
internal class SelectedScalarPlan(private val program: FiniteProgramFixtures) {
    data class Needs(
        val fields: MutableSet<List<Int>> = linkedSetOf(),
        val marked: MutableMap<List<Int>, MutableSet<TaintMark>> = linkedMapOf(),
        val classes: MutableSet<Int> = linkedSetOf(),
        var allPoints: Boolean = false,
        var writes: Boolean = false,
    ) {
        fun merge(other: Needs): Boolean {
            var change = classes.addAll(other.classes)
            if (other.allPoints && !allPoints) { allPoints = true; change = true }
            if (other.writes && !writes) { writes = true; change = true }
            for ((path, marks) in other.marked)
                change = marked.getOrPut(path) { linkedSetOf() }.addAll(marks) || change
            return change
        }
    }
    private val values = program.methods.associate { it.method to Needs() }
    private val calls = program.methods.associate { it.method to linkedSetOf<CommonMethod>() }
    private val universe = linkedSetOf<List<Int>>().apply { addAll(program.declaredStaticFieldPaths) }
    private val classes = linkedSetOf<Int>().apply { addAll(program.declaredStaticClassIds) }

    init {
        for (row in program.statementInputs) summary(values.getValue(row.key.method), row.summary)
        for (row in program.entryInputs) rules(values.getValue(row.key.method), row.rules)
        for (row in program.exitInputs) rules(values.getValue(row.key.method), row.rules.rules)
        for (row in program.callInputs) {
            val n = values.getValue(row.key.method)
            row.plan.sinks.forEach { sink(n, it) }
            for (stage in row.plan.stages) when (stage) {
                is CallStage.Edges -> summary(n, stage.summary, reads = stage.kind != StageKind.ALIASES,
                    selfRead = stage.kind == StageKind.UNRESOLVED)
                is CallStage.Actions -> for (step in stage.steps) when (step) {
                    is CleanStep.Clean -> cleaner(n, step.cleaner)
                    is CleanStep.Kill -> summary(n, step.keepEdges, reads = false)
                    is CleanStep.Field -> {
                        summary(n, step.read, reads = false)
                        cleaner(n, step.cleaner)
                        summary(n, step.write, reads = false)
                    }
                }
                is CallStage.Callees -> for (callee in stage.callees) {
                    if (callee.method in values) calls.getValue(row.key.method) += callee.method
                    else n.writes = true
                }
            }
        }
        // The finite graph is closed by a plain whole-program least fixed point, not SCC plans.
        do {
            var changed = false
            for ((method, targets) in calls) for (callee in targets)
                changed = values.getValue(method).merge(values.getValue(callee)) || changed
        } while (changed)
    }

    private fun coordinate(n: Needs, f: PathFact, root: Boolean = true) {
        if (f.base != STATIC) return
        val path = f.path.take(2)
        when (path.size) {
            0 -> if (root) n.allPoints = true
            1 -> { classes += path[0]; n.classes += path[0] }
            else -> { classes += path[0]; universe += path }
        }
    }
    private fun literal(n: Needs, p: Pattern) {
        if (p.fact.base != STATIC) return
        coordinate(n, p.fact)
        val mark = (p.fact.mark as? MarkSlot.Concrete)?.mark ?: return
        // An exact class/root condition has no field cylinder.
        if (p.fact.path.size < 2 && p.fact.tail == Tail.EXACT) return
        n.marked.getOrPut(p.fact.path.take(2)) { linkedSetOf() } += mark
    }
    private fun summary(n: Needs, s: StatementSummary, reads: Boolean = true, selfRead: Boolean = false) {
        for (micro in s.edges) {
            val e = micro.edge
            val identity = e.from == e.to && e.from.mark is MarkSlot.Star
            if (!identity) { coordinate(n, e.from); coordinate(n, e.to) }
            if (reads && e.from.base == STATIC && e.from.path.size >= 2 && e.from.mark is MarkSlot.Star &&
                (!identity || (selfRead && e.exclusion == ExclusionSet.Empty))) {
                n.fields += e.from.path.take(2)
                coordinate(n, e.from)
            }
            if (e.from.mark is MarkSlot.Concrete) literal(n, Pattern(e.from, ExclusionSet.Empty))
            if (e.from.base == STATIC) for (id in (e.to.mark as? MarkSlot.Star)?.excluded?.ids ?: intArrayOf())
                literal(n, Pattern(e.from.copy(mark = MarkSlot.Concrete(TaintMark(id))), ExclusionSet.Empty))
            if (e.to.base == STATIC && (e.from != e.to || e.exclusion != ExclusionSet.Empty ||
                    e.fromExclusion != ExclusionSet.Empty || e.toExclusion != ExclusionSet.Empty)) n.writes = true
            // A strong keep can have no value edge. The finest exclusion names its destination.
            val excluded = (e.exclusion as? ExclusionSet.Concrete)?.ids ?: intArrayOf()
            if (e.from.base == STATIC) for (a in excluded) {
                val target = e.from.path + a
                if (s.edges.none { it !== micro && it.edge.from.base == STATIC &&
                        it.edge.from.path.startsWith(target) && it.edge.exclusion != ExclusionSet.Empty })
                    coordinate(n, e.from.copy(path = target))
            }
        }
        if (STATIC in s.touched && s.edges.none { it.edge.from.base == STATIC }) {
            n.writes = true; n.allPoints = true
        }
        for (source in s.sources) {
            source.predicate.leaves.forEach { literal(n, it) }
            for (action in source.actions) { coordinate(n, action.target); if (action.target.base == STATIC) n.writes = true }
        }
        for (cj in s.conjunctions) {
            cj.literals.forEach { literal(n, it) }; coordinate(n, cj.target)
            if (cj.target.base == STATIC) n.writes = true
        }
    }
    private fun sink(n: Needs, s: SinkRule) {
        (s.predicate?.leaves ?: s.patterns).forEach { literal(n, it) }
        s.endFacts.forEach { summary(n, StatementSummary(setOf(it.edge.from.base), listOf(it), emptyList(), emptyMap())) }
    }
    private fun rules(n: Needs, r: RuleStatement) {
        summary(n, r.summary); summary(n, r.endFacts); r.sinks.forEach { sink(n, it) }
    }
    private fun cleaner(n: Needs, c: Cleaner) {
        if (c.base != STATIC) return
        n.writes = true
        val path = c.path?.toList().orEmpty()
        coordinate(n, PathFact(STATIC, path, Tail.STAR, MarkSlot.STAR))
        c.mark?.let { mark ->
            literal(n, Pattern(PathFact(STATIC, path,
                if (path.size == 1 && c.reach == CleanReach.EXACT) Tail.EXACT else Tail.STAR,
                MarkSlot.Concrete(mark)), ExclusionSet.Empty))
        }
    }
    private fun needs(method: MethodKey) = values.getValue(method.method)
    fun fieldReads(method: MethodKey): Set<List<Int>> = needs(method).fields
    fun writes(method: MethodKey): Boolean = needs(method).writes
    fun points(method: MethodKey): Set<Int> = needs(method).let { if (it.allPoints) classes else it.classes }
    fun wide(method: MethodKey, field: List<Int>): Set<TaintMark> = needs(method).marked.entries
        .filter { (scope, _) -> scope.size < 2 && field.startsWith(scope) }.flatMapTo(linkedSetOf()) { it.value }
    fun base(method: MethodKey): Map<Pattern, Set<TaintMark>> = needs(method).let { n ->
        universe.associate { path -> slot(STATIC, path) to n.marked.entries
            .filter { (scope, _) -> path.startsWith(scope) }.flatMapTo(linkedSetOf()) { it.value } }
            .filterValues { it.isNotEmpty() }
    }
    fun suppliedFields(input: Pattern): Set<List<Int>> = universe.filterTo(linkedSetOf()) { path ->
        geometry(slot(STATIC, path), input)
    }
    companion object {
        fun slot(base: org.opentaint.dataflow.ap.ifds.AccessPathBase, path: List<Int> = emptyList()) =
            Pattern(PathFact(base, path, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
        /** Request geometry intentionally ignores E/mx, but rejects exact proper ancestors. */
        fun geometry(slot: Pattern, input: Pattern): Boolean = slot.fact.base == input.fact.base &&
            (input.fact.path.startsWith(slot.fact.path) ||
                (slot.fact.path.startsWith(input.fact.path) && input.fact.tail != Tail.EXACT))
    }
}
