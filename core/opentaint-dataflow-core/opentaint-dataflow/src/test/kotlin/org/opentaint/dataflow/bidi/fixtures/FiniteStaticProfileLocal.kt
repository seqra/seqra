package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.bidi.store.RequestMarkSetBuilder

/** Test-only extraction from explicit finite forms, independent of the JVM metadata collector. */
internal class FiniteStaticProfileLocal {
    private val accesses = LinkedHashSet<List<Int>>()
    private val abstractions = LinkedHashSet<List<Int>>()
    private val marks = LinkedHashSet<StaticMarkInterest>()
    private val cleaners = RequestMarkSetBuilder()
    private val wideClasses = LinkedHashSet<Int>()
    private val classPoints = LinkedHashSet<Int>()
    private var allStatic = false

    private fun position(fact: PathFact, observe: Boolean = true, includeRoot: Boolean = false) {
        if (fact.base != STATIC || (fact.path.isEmpty() && !includeRoot)) return
        val slot = fact.path.take(2)
        abstractions += slot
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

    private fun edge(value: MicroEdge) {
        val from = value.edge.from
        val to = value.edge.to
        val identity = from == to && from.mark is MarkSlot.Star
        position(from, observe = !identity)
        position(to, observe = !identity, includeRoot = !identity)
        if (from.mark is MarkSlot.Concrete)
            literal(Pattern(from, ExclusionSet.Empty), StaticMarkRole.INPUT_GATE)
        val excluded = (to.mark as? MarkSlot.Star)?.excluded
        if (from.base == STATIC && excluded != null) for (id in excluded.ids) {
            literal(Pattern(from.copy(mark = MarkSlot.Concrete(TaintMark(id))), ExclusionSet.Empty),
                StaticMarkRole.INPUT_GATE)
        }
    }

    fun summary(value: StatementSummary) {
        value.edges.forEach(::edge)
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
        if (STATIC in value.touched && value.edges.none { it.edge.from.base == STATIC }) allStatic = true
        for (source in value.sources) {
            source.predicate.leaves.forEach { literal(it, StaticMarkRole.SOURCE) }
            source.actions.forEach { position(it.target, includeRoot = true) }
        }
        for (conjunction in value.conjunctions) {
            conjunction.literals.forEach { literal(it, StaticMarkRole.SOURCE) }
            position(conjunction.target, includeRoot = true)
        }
    }

    private fun sink(value: SinkRule) {
        (value.predicate?.leaves ?: value.patterns).forEach { literal(it, StaticMarkRole.SINK) }
        value.endFacts.forEach(::edge)
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
                value.cleaner.mark?.let(cleaners::add)
            }
            is CleanStep.Kill -> summary(value.keepEdges)
            is CleanStep.Field -> { summary(value.read); summary(value.write) }
        }
    }

    fun plan(value: CallPlan, callee: (MethodKey) -> Unit) {
        value.sinks.forEach(::sink)
        for (stage in value.stages) when (stage) {
            is CallStage.Edges -> summary(stage.summary)
            is CallStage.Actions -> stage.steps.forEach(::clean)
            is CallStage.Callees -> stage.callees.forEach(callee)
        }
    }

    fun profile(): Run1StaticLocalProfile = Run1StaticLocalProfile(accesses, abstractions, marks,
        cleaners.takeSnapshot(), wideClasses, allStatic, classPoints)
}
