package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.engine.Run1StaticLocalProfile
import org.opentaint.dataflow.bidi.engine.StaticMarkInterest
import org.opentaint.dataflow.bidi.engine.StaticMarkRole
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.RuleStatement
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.graph.JMethodEnterInst
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst
import java.util.Collections

/** Descriptors contain no numeric AP IDs, compiled predicate, alias analysis or prescan context. */
internal data class StaticMarkDescriptor(val path: List<Accessor>, val tail: Tail,
                                         val mark: String, val role: StaticMarkRole)

internal class JIRStaticMetadata(
    accesses: Collection<List<Accessor>>, abstractions: Collection<List<Accessor>>,
    marks: Collection<StaticMarkDescriptor>, cleanerMarks: Collection<String>,
) {
    val accesses: Set<List<Accessor>> = paths(accesses)
    val abstractions: Set<List<Accessor>> = paths(abstractions)
    val marks: Set<StaticMarkDescriptor> = Collections.unmodifiableSet(marks.mapTo(LinkedHashSet()) {
        it.copy(path = Collections.unmodifiableList(ArrayList(it.path)))
    })
    val cleanerMarks: Set<String> = Collections.unmodifiableSet(LinkedHashSet(cleanerMarks))

    fun compile(ap: ApManager): Run1StaticLocalProfile {
        fun path(xs: List<Accessor>): List<Int> {
            ap.cancellation.checkpoint()
            return xs.map(ap.accessors::index)
        }
        return Run1StaticLocalProfile(accesses.map(::path), abstractions.map(::path), marks.map {
            ap.cancellation.checkpoint()
            val mark = ap.marks.mark(it.mark)
            StaticMarkInterest(Pattern(PathFact(AccessPathBase.ClassStatic, path(it.path), it.tail,
                MarkSlot.Concrete(mark)), ExclusionSet.Empty), it.role, RequestMarkSet.of(mark))
        }, RequestMarkSet.fromIds(cleanerMarks.map {
            ap.cancellation.checkpoint()
            ap.marks.mark(it).id
        }.toIntArray()))
    }

    fun union(other: JIRStaticMetadata) = JIRStaticMetadata(accesses + other.accesses,
        abstractions + other.abstractions, marks + other.marks, cleanerMarks + other.cleanerMarks)

    companion object {
        val EMPTY = JIRStaticMetadata(emptyList(), emptyList(), emptyList(), emptyList())
        private fun paths(xs: Collection<List<Accessor>>): Set<List<Accessor>> =
            Collections.unmodifiableSet(xs.mapTo(LinkedHashSet()) { Collections.unmodifiableList(ArrayList(it)) })
    }
}

/** Reuses effective forward forms and immediately discards each compiled form after reading its needs. */
internal class JIRStaticMetadataCollector(private val interp: JIRInterpreter) {
    fun collect(key: MethodKey, aliasAnalysis: JIRLocalAliasAnalysis? = null): JIRStaticMetadata {
        val ap = interp.manager
        val availableAlias = aliasAnalysis?.takeIf { it.computedAliasInfo() != null }
        val unknownAliases = aliasAnalysis != null && availableAlias == null
        val accesses = LinkedHashSet<List<Accessor>>()
        val abstractions = LinkedHashSet<List<Accessor>>()
        val marks = LinkedHashSet<StaticMarkDescriptor>()
        val cleaners = LinkedHashSet<String>()
        fun position(fact: PathFact, access: Boolean = true, empty: Boolean = false) {
            if (fact.base != AccessPathBase.ClassStatic || (fact.path.isEmpty() && !empty)) return
            val slot = fact.path.take(2).map(ap.accessors::accessor)
            abstractions.add(slot)
            if (access) accesses.add(slot)
        }
        fun literal(pattern: Pattern, role: StaticMarkRole) {
            val fact = pattern.fact
            if (fact.base != AccessPathBase.ClassStatic) return
            position(fact, empty = true)
            val mark = fact.mark as? MarkSlot.Concrete ?: return
            check(pattern.exclusion == ExclusionSet.Empty) { "A rule literal has no field exclusions" }
            marks.add(StaticMarkDescriptor(fact.path.map(ap.accessors::accessor), fact.tail,
                ap.marks.name(mark.mark), role))
        }
        fun summary(s: StatementSummary) {
            for (micro in s.edges) {
                ap.cancellation.checkpoint()
                val edge = micro.edge
                val keep = edge.from == edge.to && edge.from.mark is MarkSlot.Star
                position(edge.from, access = !keep)
                position(edge.to, access = !keep)
                if (edge.from.mark is MarkSlot.Concrete)
                    literal(Pattern(edge.from, ExclusionSet.Empty), StaticMarkRole.INPUT_GATE)
            }
            for (source in s.sources) {
                ap.cancellation.checkpoint()
                for (leaf in source.predicate.leaves) literal(leaf, StaticMarkRole.SOURCE)
                for (action in source.actions) position(action.target, empty = true)
            }
            for (conjunction in s.conjunctions) {
                for (leaf in conjunction.literals) literal(leaf, StaticMarkRole.SOURCE)
                position(conjunction.target, empty = true)
            }
        }
        fun sink(s: SinkRule) {
            for (leaf in s.predicate?.leaves ?: s.patterns) literal(leaf, StaticMarkRole.SINK)
            for (edge in s.endFacts) position(edge.edge.to, empty = true)
        }
        fun clean(step: CleanStep) {
            ap.cancellation.checkpoint()
            when (step) {
                is CleanStep.Clean -> if (step.cleaner.base == AccessPathBase.ClassStatic) {
                    val path = step.cleaner.path?.toList().orEmpty()
                    val descriptors = path.take(2).map(ap.accessors::accessor)
                    accesses.add(descriptors)
                    abstractions.add(descriptors)
                    step.cleaner.mark?.let { cleaners.add(ap.marks.name(it)) }
                }
                is CleanStep.Kill -> {
                    summary(step.keepEdges)
                    // A strong kill encodes the written position in its deepest keep's exclusion.
                    val last = step.keepEdges.edges.maxByOrNull { it.edge.from.path.size }?.edge
                    if (last != null) (last.exclusion as? ExclusionSet.Concrete)?.ids?.forEach { field ->
                        position(last.from.copy(path = last.from.path + field), empty = true)
                    }
                }
                is CleanStep.Field -> { summary(step.read); summary(step.write) }
            }
        }
        fun rules(r: RuleStatement) { summary(r.summary); summary(r.endFacts); r.sinks.forEach(::sink) }
        val method = key.method as JIRMethod
        if (method.instList.size == 0) return JIRStaticMetadata.EMPTY
        val entry = interp.entries[key]
        val entryRules = JIRBoundaryForms.entryRules(interp, entry, key, availableAlias, unknownAliases)
        rules(entryRules)
        val statements = JIRStatementForms(ap, interp.typeFilters, readSources = interp.ruleForms::readSources)
        for (node in method.instList.instructions) {
            ap.cancellation.checkpoint()
            if (node is JIRAssignInst) {
                fun field(ref: JIRFieldRef?, write: Boolean) {
                    val access = ref?.let(MethodFlowFunctionUtils::mkAccess) as? MethodFlowFunctionUtils.StaticRefAccess ?: return
                    val slot = listOf(access.classStaticAccessor, access.accessor)
                    accesses.add(slot)
                    abstractions.add(slot)
                    if (write) abstractions.add(listOf(access.classStaticAccessor))
                }
                field(node.rhv as? JIRFieldRef, write = false)
                field(node.lhv as? JIRFieldRef, write = true)
            }
            when (node) {
                is JMethodEnterInst -> Unit
                is JMethodExitNormalInst, is JMethodExitExceptionalInst ->
                    rules(JIRBoundaryForms.exitRules(interp, entry, key, node, entryRules, availableAlias, unknownAliases).rules)
                else -> {
                    val call = node.callExpr
                    if (call == null) summary(statements.build(node))
                    else JIRCallPlanBuilder(ap, interp.ruleForms, interp.checker, interp.typeFilters,
                        key, node, call, { _, _, _ -> error("Metadata must not resolve calls") },
                        interp.epResolver, emptyMap(), aliasAnalysis = availableAlias,
                        defaultGetModel = interp.defaultGetModel, formOwner = interp.entries.formOwner,
                        potentialUnknownAliases = unknownAliases)
                        .visitStaticMetadata(::summary, ::sink, ::clean)
                }
            }
        }
        return JIRStaticMetadata(accesses, abstractions, marks, cleaners)
    }
}
