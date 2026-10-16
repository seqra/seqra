package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.RequestMarkSet

/** Test-only extraction from explicit finite forms, independent of the JVM metadata collector. */
internal class FiniteStaticProfileLocal {
    private val accesses = LinkedHashSet<List<Int>>()
    private val abstractions = LinkedHashSet<List<Int>>()
    private val marks = LinkedHashSet<StaticMarkInterest>()
    private val wideClasses = LinkedHashSet<Int>()
    private val classPoints = LinkedHashSet<Int>()
    private var allStatic = false
    private var staticMayWrite = false

    private fun position(fact: PathFact, observe: Boolean = true, includeRoot: Boolean = false) {
        if (fact.base != STATIC || (fact.path.isEmpty() && !includeRoot)) return
        val slot = fact.path.take(2)
        if (!observe) return
        accesses += slot
        when (slot.size) {
            0 -> allStatic = true
            1 -> {
                classPoints += slot[0]
                if (fact.tail != Tail.EXACT) wideClasses += slot[0]
            }
        }
    }

    private fun literal(pattern: Pattern, role: StaticMarkRole) {
        if (pattern.fact.base != STATIC) return
        position(pattern.fact, includeRoot = true)
        val mark = (pattern.fact.mark as? MarkSlot.Concrete)?.mark ?: return
        marks += StaticMarkInterest(pattern, role, RequestMarkSet.of(mark))
    }

    private fun edge(value: MicroEdge, valueReads: Boolean = true, identityFieldReads: Boolean = false) {
        val from = value.edge.from
        val to = value.edge.to
        val identity = from == to && from.mark is MarkSlot.Star
        position(from, observe = !identity)
        position(to, observe = !identity, includeRoot = !identity)
        if ((!identity || (identityFieldReads && value.edge.exclusion == ExclusionSet.Empty)) &&
            valueReads && from.base == STATIC && from.mark is MarkSlot.Star && from.path.size >= 2)
            abstractions += from.path.take(2)
        if (to.base == STATIC && (from != to || value.edge.exclusion != ExclusionSet.Empty ||
                value.edge.fromExclusion != ExclusionSet.Empty || value.edge.toExclusion != ExclusionSet.Empty))
            staticMayWrite = true
        if (from.mark is MarkSlot.Concrete)
            literal(Pattern(from, ExclusionSet.Empty), StaticMarkRole.INPUT_GATE)
        val excluded = (to.mark as? MarkSlot.Star)?.excluded
        if (from.base == STATIC && excluded != null) for (id in excluded.ids) {
            literal(Pattern(from.copy(mark = MarkSlot.Concrete(TaintMark(id))), ExclusionSet.Empty),
                StaticMarkRole.INPUT_GATE)
        }
    }

    fun summary(value: StatementSummary, valueReads: Boolean = true, identityFieldReads: Boolean = false) {
        value.edges.forEach { edge(it, valueReads, identityFieldReads) }
        // A lowered strong write can contain no generated value, only keep edges.
        // Consume the most specific exclusion instead of treating its coarse class
        // exclusion as a write of every sibling/class point.
        for (micro in value.edges) {
            val from = micro.edge.from
            if (from.base != STATIC) continue
            val excluded = (micro.edge.exclusion as? ExclusionSet.Concrete)?.ids ?: continue
            for (next in excluded) {
                val target = from.path + next
                val refined = value.edges.any { other ->
                    other !== micro && other.edge.from.base == STATIC &&
                        other.edge.from.path.size >= target.size &&
                        other.edge.from.path.take(target.size) == target &&
                        other.edge.exclusion != ExclusionSet.Empty
                }
                if (!refined) position(from.copy(path = target), includeRoot = true)
            }
        }
        if (STATIC in value.touched && value.edges.none { it.edge.from.base == STATIC }) {
            allStatic = true
            staticMayWrite = true
        }
        for (source in value.sources) {
            source.predicate.leaves.forEach { literal(it, StaticMarkRole.SOURCE) }
            source.actions.forEach {
                position(it.target, includeRoot = true)
                if (it.target.base == STATIC) staticMayWrite = true
            }
        }
        for (conjunction in value.conjunctions) {
            conjunction.literals.forEach { literal(it, StaticMarkRole.SOURCE) }
            position(conjunction.target, includeRoot = true)
            if (conjunction.target.base == STATIC) staticMayWrite = true
        }
    }

    private fun sink(value: SinkRule) {
        (value.predicate?.leaves ?: value.patterns).forEach { literal(it, StaticMarkRole.SINK) }
        value.endFacts.forEach { edge(it) }
    }

    fun rules(value: RuleStatement) {
        summary(value.summary)
        summary(value.endFacts)
        value.sinks.forEach(::sink)
    }

    private fun clean(value: CleanStep) {
        when (value) {
            is CleanStep.Clean -> if (value.cleaner.base == STATIC) {
                position(PathFact(STATIC, value.cleaner.path?.toList().orEmpty(), Tail.STAR, MarkSlot.STAR),
                    includeRoot = true)
                staticMayWrite = true
                value.cleaner.mark?.let { mark ->
                    val path = value.cleaner.path?.toList().orEmpty()
                    literal(Pattern(PathFact(STATIC, path,
                        if (path.size == 1 && value.cleaner.reach == CleanReach.EXACT) Tail.EXACT else Tail.STAR,
                        MarkSlot.Concrete(mark)), ExclusionSet.Empty), StaticMarkRole.INPUT_GATE)
                }
            }
            is CleanStep.Kill -> summary(value.keepEdges, valueReads = false)
            is CleanStep.Field -> { summary(value.read, valueReads = false); summary(value.write, valueReads = false) }
        }
    }

    fun plan(value: CallPlan, callee: (MethodKey) -> Unit) {
        value.sinks.forEach(::sink)
        for (stage in value.stages) when (stage) {
            is CallStage.Edges -> summary(stage.summary, valueReads = stage.kind != StageKind.ALIASES,
                identityFieldReads = stage.kind == StageKind.UNRESOLVED)
            is CallStage.Actions -> stage.steps.forEach(::clean)
            is CallStage.Callees -> stage.callees.forEach(callee)
        }
    }

    fun profile(): Run1StaticLocalProfile = Run1StaticLocalProfile(accesses, abstractions, marks,
        RequestMarkSet.EMPTY, wideClasses, allStatic, classPoints, staticMayWrite)
}
