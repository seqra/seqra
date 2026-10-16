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
    wideClasses: Collection<Accessor> = emptyList(), val allStatic: Boolean = false,
    classPoints: Collection<Accessor> = emptyList(),
) {
    val accesses: Set<List<Accessor>> = paths(accesses)
    val abstractions: Set<List<Accessor>> = paths(abstractions)
    val marks: Set<StaticMarkDescriptor> = Collections.unmodifiableSet(marks.mapTo(LinkedHashSet()) {
        it.copy(path = Collections.unmodifiableList(ArrayList(it.path)))
    })
    val cleanerMarks: Set<String> = Collections.unmodifiableSet(LinkedHashSet(cleanerMarks))
    val wideClasses: Set<Accessor> = Collections.unmodifiableSet(LinkedHashSet(wideClasses))
    val classPoints: Set<Accessor> = Collections.unmodifiableSet(LinkedHashSet(classPoints))

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
        }.toIntArray()), wideClasses.mapTo(LinkedHashSet(), ap.accessors::index), allStatic,
            classPoints.mapTo(LinkedHashSet(), ap.accessors::index))
    }

    fun union(other: JIRStaticMetadata) = JIRStaticMetadata(accesses + other.accesses,
        abstractions + other.abstractions, marks + other.marks, cleanerMarks + other.cleanerMarks,
        wideClasses + other.wideClasses, allStatic || other.allStatic, classPoints + other.classPoints)

    companion object {
        val EMPTY = JIRStaticMetadata(emptyList(), emptyList(), emptyList(), emptyList())
        private fun paths(xs: Collection<List<Accessor>>): Set<List<Accessor>> =
            Collections.unmodifiableSet(xs.mapTo(LinkedHashSet()) { Collections.unmodifiableList(ArrayList(it)) })
    }
}

/** Prescan completion alone does not certify the outcomes of later, independently timed AA.
 * A Known state requires that bridge; until then every enabled-AA runtime uses Unknown. */
internal enum class MetadataAliases {
    Disabled, Unknown;

    companion object {
        fun forRuntime(enabled: Boolean): MetadataAliases = if (enabled) Unknown else Disabled
    }
}

/** Reuses effective forward forms and immediately discards each compiled form after reading its needs.
 * No AA owner or completed payload is borrowed or initialized after prescan joins. */
internal class JIRStaticMetadataCollector(
    private val interp: JIRInterpreter,
    private val aliases: MetadataAliases = MetadataAliases.forRuntime(interp.entries.aliasParams.useAliasAnalysis),
) {
    fun collect(key: MethodKey): JIRStaticMetadata {
        val ap = interp.manager
        val unknownAliases = aliases == MetadataAliases.Unknown
        val accesses = LinkedHashSet<List<Accessor>>()
        val abstractions = LinkedHashSet<List<Accessor>>()
        val marks = LinkedHashSet<StaticMarkDescriptor>()
        val cleaners = LinkedHashSet<String>()
        val wideClasses = LinkedHashSet<Accessor>()
        val classPoints = LinkedHashSet<Accessor>()
        var allStatic = false
        fun position(fact: PathFact, access: Boolean = true, empty: Boolean = false) {
            if (fact.base != AccessPathBase.ClassStatic || (fact.path.isEmpty() && !empty)) return
            val slot = fact.path.take(2).map(ap.accessors::accessor)
            abstractions.add(slot)
            if (access) accesses.add(slot)
            if (access && fact.path.isEmpty()) allStatic = true
            if (access && fact.path.size == 1) classPoints += ap.accessors.accessor(fact.path[0])
            if (access && fact.path.size == 1 && fact.tail != Tail.EXACT) wideClasses += ap.accessors.accessor(fact.path[0])
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
        fun excludedTarget(from: PathFact, target: PathFact) {
            if (from.base != AccessPathBase.ClassStatic) return
            val excluded = (target.mark as? MarkSlot.Star)?.excluded ?: return
            if (excluded.isEmpty) return
            position(from, empty = true)
            for (id in excluded.ids) {
                ap.cancellation.checkpoint()
                marks += StaticMarkDescriptor(from.path.map(ap.accessors::accessor), from.tail,
                    ap.marks.name(org.opentaint.dataflow.bidi.ap.TaintMark(id)), StaticMarkRole.INPUT_GATE)
            }
        }
        fun summary(s: StatementSummary) {
            for (micro in s.edges) {
                ap.cancellation.checkpoint()
                val edge = micro.edge
                val keep = edge.from == edge.to && edge.from.mark is MarkSlot.Star
                position(edge.from, access = !keep, empty = !keep)
                position(edge.to, access = !keep, empty = !keep)
                excludedTarget(edge.from, edge.to)
                if (edge.from.mark is MarkSlot.Concrete)
                    literal(Pattern(edge.from, ExclusionSet.Empty), StaticMarkRole.INPUT_GATE)
            }
            for (source in s.sources) {
                ap.cancellation.checkpoint()
                for (leaf in source.predicate.leaves) literal(leaf, StaticMarkRole.SOURCE)
                for (action in source.actions) {
                    position(action.target, empty = true)
                    for (leaf in source.predicate.leaves) excludedTarget(leaf.fact, action.target)
                }
            }
            for (conjunction in s.conjunctions) {
                for (leaf in conjunction.literals) literal(leaf, StaticMarkRole.SOURCE)
                position(conjunction.target, empty = true)
                for (leaf in conjunction.literals) excludedTarget(leaf.fact, conjunction.target)
            }
        }
        fun sink(s: SinkRule) {
            for (leaf in s.predicate?.leaves ?: s.patterns) literal(leaf, StaticMarkRole.SINK)
            for (edge in s.endFacts) {
                position(edge.edge.to, empty = true)
                excludedTarget(edge.edge.from, edge.edge.to)
            }
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
                    if (path.isEmpty()) allStatic = true
                    else if (path.size == 1 && step.cleaner.reach != org.opentaint.dataflow.bidi.ap.CleanReach.EXACT)
                        wideClasses += ap.accessors.accessor(path[0])
                    if (path.size == 1 && step.cleaner.reach != org.opentaint.dataflow.bidi.ap.CleanReach.BELOW)
                        classPoints += ap.accessors.accessor(path[0])
                    if (path.size <= 1 && step.cleaner.mark != null &&
                        step.cleaner.reach != org.opentaint.dataflow.bidi.ap.CleanReach.BELOW) {
                        marks += StaticMarkDescriptor(path.map(ap.accessors::accessor),
                            if (path.isEmpty()) Tail.ANY else Tail.EXACT,
                            ap.marks.name(step.cleaner.mark!!), StaticMarkRole.INPUT_GATE)
                    }
                }
                is CleanStep.Kill -> {
                    summary(step.keepEdges)
                    if (AccessPathBase.ClassStatic in step.keepEdges.touched &&
                        step.keepEdges.edges.none { it.edge.from.base == AccessPathBase.ClassStatic }) allStatic = true
                    // A strong kill encodes the written position in its deepest keep's exclusion.
                    val last = step.keepEdges.edges.maxByOrNull { it.edge.from.path.size }?.edge
                    if (last != null) (last.exclusion as? ExclusionSet.Concrete)?.ids?.forEach { field ->
                        position(last.from.copy(path = last.from.path + field), empty = true)
                    }
                }
                is CleanStep.Field -> {
                    summary(step.read); summary(step.write)
                    // F74's selected cleaner acts on a temporary, but its request belongs to
                    // the original static read, never to the temporary or the write target.
                    step.cleaner.mark?.let { mark ->
                        for (read in step.read.edges) if (read.edge.from.base == AccessPathBase.ClassStatic) {
                            val from = read.edge.from
                            marks += StaticMarkDescriptor(from.path.map(ap.accessors::accessor), from.tail,
                                ap.marks.name(mark), StaticMarkRole.INPUT_GATE)
                        }
                    }
                }
            }
        }
        fun rules(r: RuleStatement) { summary(r.summary); summary(r.endFacts); r.sinks.forEach(::sink) }
        val method = key.method as JIRMethod
        if (method.instList.size == 0) return JIRStaticMetadata.EMPTY
        val entry = interp.entries[key]
        val entryRules = JIRBoundaryForms.entryRules(interp, entry, key, aliasAnalysis = null,
            potentialUnknownAliases = unknownAliases)
        rules(entryRules)
        val statements = JIRStatementForms(ap, interp.typeFilters, readSources = interp.ruleForms::readSources)
        for (node in method.instList.instructions) {
            ap.cancellation.checkpoint()
            if (node is JIRAssignInst) {
                fun field(ref: JIRFieldRef?) {
                    val access = ref?.let(MethodFlowFunctionUtils::mkAccess) as? MethodFlowFunctionUtils.StaticRefAccess ?: return
                    val slot = listOf(access.classStaticAccessor, access.accessor)
                    accesses.add(slot)
                    abstractions.add(slot)
                }
                field(node.rhv as? JIRFieldRef)
                field(node.lhv as? JIRFieldRef)
            }
            when (node) {
                is JMethodEnterInst -> Unit
                is JMethodExitNormalInst, is JMethodExitExceptionalInst ->
                    rules(JIRBoundaryForms.exitRules(interp, entry, key, node, entryRules, aliasAnalysis = null,
                        potentialUnknownAliases = unknownAliases).rules)
                else -> {
                    val call = node.callExpr
                    if (call == null) summary(statements.build(node))
                    else JIRCallPlanBuilder(ap, interp.ruleForms, interp.checker, interp.typeFilters,
                        key, node, call, { _, _, _ -> error("Metadata must not resolve calls") },
                        interp.epResolver, emptyMap(), aliasAnalysis = null,
                        defaultGetModel = interp.defaultGetModel, formOwner = interp.entries.formOwner,
                        potentialUnknownAliases = unknownAliases,
                        metadataSourceLiteral = if (unknownAliases) ({ fact ->
                            literal(Pattern(fact, ExclusionSet.Empty), StaticMarkRole.SOURCE)
                        }) else null,
                        metadataSinkLiteral = if (unknownAliases) ({ fact ->
                            literal(Pattern(fact, ExclusionSet.Empty), StaticMarkRole.SINK)
                        }) else null)
                        .visitStaticMetadata(::summary, ::sink, ::clean)
                }
            }
        }
        return JIRStaticMetadata(accesses, abstractions, marks, cleaners, wideClasses, allStatic, classPoints)
    }
}
