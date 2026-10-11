package org.opentaint.dataflow.jvm.bidi.interp

import mu.KotlinLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.CleanReach
import org.opentaint.dataflow.bidi.ap.Cleaner
import org.opentaint.dataflow.bidi.ap.ConjunctiveEdge
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.MicroEdge
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.bidi.interp.ZERO_PATTERN
import org.opentaint.dataflow.bidi.interp.fieldCleanStep
import org.opentaint.dataflow.bidi.interp.keepEdge
import org.opentaint.dataflow.bidi.interp.strongKeep
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSink
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSource
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.PositionTypeResolver
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral
import org.opentaint.dataflow.taint.accessors
import org.opentaint.dataflow.taint.base
import org.opentaint.dataflow.taint.explodeToDNF
import org.opentaint.dataflow.taint.removeTrueLiterals
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Rule forms use callee coordinates. A rejected rule contributes no partial form. */
internal class JIRRuleForms(
    val rules: TaintRulesProvider,
    private val manager: ApManager,
    val errors: RuleErrors,
    private val typeFilters: JIRTypeFilters,
    private val resultBase: AccessPathBase = AccessPathBase.Return,
) {
    /** A final AnyField selects a tail; it is never part of an indexed concrete path. */
    data class RulePos(val base: AccessPathBase, val path: List<AccessorIdx>, val any: Boolean) {
        val isClass: Boolean get() = base == AccessPathBase.ClassStatic && path.size == 1
        fun fact(t: TaintMark) = PathFact(base, owned(path), if (any) Tail.ANY else Tail.EXACT, MarkSlot.Concrete(t))
        fun sourceFact(t: TaintMark) = PathFact(base, owned(path), if (any) Tail.ANY_TAINT else Tail.EXACT, MarkSlot.Concrete(t))
        fun star() = PathFact(base, owned(path), if (any) Tail.ANY else Tail.STAR, MarkSlot.STAR)
    }

    fun pos(rule: Any, p: Position): RulePos? = pos(rule, p.resolveAp())

    fun pos(rule: Any, p: PositionAccess): RulePos? {
        val accessors = p.accessors()
        val anyCount = accessors.count { it == AnyAccessor }
        if (anyCount > 1) {
            errors.reject(rule, "two AnyField accessors in a position (§1.3)")
            return null
        }
        if (anyCount == 1 && accessors.last() != AnyAccessor) {
            errors.reject(rule, "an accessor after AnyField in a position (§1.3)")
            return null
        }
        val any = anyCount == 1
        val path = if (any) accessors.dropLast(1) else accessors
        val base = p.base().let { if (it == AccessPathBase.Return) resultBase else it }
        return RulePos(base, owned(path.map { manager.accessors.index(it) }), any)
    }

    fun node(path: List<AccessorIdx>): PathNode? = manager.path(path)
    fun mark(m: RuleMark): TaintMark = manager.marks.mark(m.name)
    fun mark(name: String): TaintMark = manager.marks.mark(name)

    fun literal(rule: Any, l: TaintMarkAwareConditionExpr.Literal): PathFact? = when (l) {
        is ContainsMarkLiteral -> pos(rule, l.position)?.fact(mark(l.mark.mark))
        is ContainsMarkOnAnyAccessorLiteral -> pos(rule, l.position)?.copy(any = true)?.fact(mark(l.mark.mark))
    }

    /** Negated mark literals count as true in source and sink conditions. */
    fun cubes(rule: Any, cond: ExprOrConstant): List<List<PathFact>> {
        if (cond.isFalse) return emptyList()
        if (cond.isTrue) return listOf(emptyList())
        val positive = cond.expr.removeTrueLiterals { it.negated } ?: return listOf(emptyList())
        return positive.explodeToDNF().map { cube ->
            cube.literals.map {
                checkNotNull(literal(rule, it)) { "a rejected rule reaches the forms (§1.3): $rule" }
            }.distinct()
        }.distinct()
    }

    fun valid(rule: TaintConfigurationItem): Boolean = validity.computeIfAbsent(rule, ::validate)
    private val validity = ConcurrentHashMap<TaintConfigurationItem, Boolean>()

    private fun validate(rule: TaintConfigurationItem): Boolean {
        var ok = true
        fun reject(reason: String) { errors.reject(rule, reason); ok = false }
        fun at(p: Position): RulePos? = pos(rule, p).also { if (it == null) ok = false }

        // An ignored action still has a raw rule position, which must be checked before rewriting.
        val actions: List<Action> = when (rule) {
            is TaintConfigurationSource -> rule.actionsAfter
            is TaintConfigurationSink -> rule.trackFactsReachAnalysisEnd
            is TaintPassThrough -> rule.actionsAfter
            is TaintCleaner -> rule.actionsAfter
        }
        for (a in actions) when (a) {
            is AssignMark -> at(a.position)
            is CopyAllMarks -> { at(a.from); at(a.to) }
            is CopyMark -> { at(a.from); at(a.to) }
            is RemoveMark -> at(a.position)
            is RemoveAllMarks -> at(a.position)
        }

        val literals = markLiterals(rule.rawCondition)
        // Read every raw literal, including negated and statically discarded alternatives.
        val anyLiteral = literals.map { (p, onAny) -> at(p)?.any == true || onAny }.any { it }
        fun target(a: AssignMark, premises: Boolean) {
            val to = at(a.position) ?: return
            if (to.isClass && to.any) reject("[any-taint] target on a class position (§1.4)")
            if (to.isClass && premises && anyLiteral)
                reject("ContainsMarkOnAnyField with a class target (§1.4, I12 (b))")
        }
        when (rule) {
            is TaintConfigurationSource -> rule.actionsAfter.forEach { target(it, premises = true) }
            is TaintConfigurationSink -> rule.trackFactsReachAnalysisEnd.forEach { target(it, premises = false) }
            is TaintPassThrough -> for (a in rule.actionsAfter) {
                val (from, to) = when (a) {
                    is CopyAllMarks -> a.from to a.to
                    is CopyMark -> a.from to a.to
                    else -> continue
                }
                val f = at(from)
                val t = at(to)
                if (f != null && t != null && (f.isClass || t.isClass)) reject("pass rule from or to a class position (§1.4)")
                if (f != null && f.any) reject("pass rule with an AnyField premise (§1.3, D33)")
            }
            is TaintCleaner -> for (a in rule.actionsAfter) when (a) {
                is RemoveMark -> at(a.position)
                is RemoveAllMarks -> at(a.position)?.let {
                    if (it.base == AccessPathBase.ClassStatic && it.any) reject("RemoveAllMarks(P.AnyField) on S (§1.4)")
                }
                else -> Unit
            }
        }
        if (rule is TaintStaticFieldSource) {
            if (!rule.condition.isTrue()) reject("read source with a condition (§4.1)")
            if (rule.actionsAfter.any { a -> pos(rule, a.position)?.let { it.base != resultBase } == true })
                reject("read source target is not the read value (§4.4)")
        }
        return ok
    }

    private fun markLiterals(c: Condition): List<Pair<Position, Boolean>> = buildList {
        fun walk(x: Condition) {
            when (x) {
                is CommonCondition.Atom -> when (val a = x.atom) {
                    is ContainsMark -> add(a.position to false)
                    is ContainsMarkOnAnyField -> add(a.position to true)
                    else -> Unit
                }
                is CommonCondition.Not -> walk(x.arg)
                is CommonCondition.And -> x.args.forEach(::walk)
                is CommonCondition.Or -> x.args.forEach(::walk)
                else -> Unit
            }
        }
        walk(c)
    }

    private val TaintConfigurationItem.rawCondition: Condition get() = when (this) {
        is TaintConfigurationSource -> condition
        is TaintConfigurationSink -> condition
        is TaintPassThrough -> condition
        is TaintCleaner -> condition
    }

    /** This producer owns side paths and conjunction literal containers. */
    fun ruleEdge(b: MicroEdgeBuilder, premises: List<PathFact>, target: PathFact) {
        val to = ownFact(target)
        when (premises.size) {
            0 -> b.edge(PathEdge(ZERO_FACT, to, ExclusionSet.Empty))
            1 -> b.edge(PathEdge(ownFact(premises[0]), to, ExclusionSet.Empty))
            else -> b.conjunction(ConjunctiveEdge(owned(premises.map { Pattern(ownFact(it), ExclusionSet.Empty) }), to))
        }
    }

    fun markTarget(rule: Any, a: AssignMark): PathFact =
        checkNotNull(pos(rule, a.position)) { "a rejected rule reaches the forms (§1.3): $rule" }.sourceFact(mark(a.mark))

    fun source(b: MicroEdgeBuilder, rule: TaintConfigurationItem, cube: List<PathFact>, a: AssignMark) {
        ruleEdge(b, cube, markTarget(rule, a))
    }

    /** The provider is reduced by prescan; rule selection never depends on a propagated fact. */
    fun readSources(b: MicroEdgeBuilder, s: JIRAssignInst) {
        val field = (s.rhv as? JIRFieldRef)?.field?.field?.takeIf { it.isStatic } ?: return
        val x = accessPathBase(s.lhv) ?: return
        val found = rules.sourceRulesForStaticField(field, s, fact = null).filter(::valid)
        if (found.isEmpty()) return
        b.keepZero()
        for (rule in found) for (a in rule.actionsAfter) {
            val p = checkNotNull(pos(rule, a.position))
            ruleEdge(b, emptyList(), p.copy(base = x).sourceFact(mark(a.mark)))
        }
    }

    fun sinks(rule: TaintConfigurationSink, cond: ExprOrConstant,
              mayBeArray: (Int) -> Boolean = { false }): List<SinkRule> {
        val ends = endFacts(rule)
        val alternatives = cubes(rule, cond).flatMap { cube ->
            if (cube.isEmpty()) return@flatMap listOf(listOf(ZERO_PATTERN))
            cube.map { elementChoices(it, mayBeArray) }.cartesianProductMapTo { choice ->
                choice.map { Pattern(it, ExclusionSet.Empty) }.distinct()
            }
        }.distinct()
        return alternatives.mapIndexed { i, patterns -> SinkRule(rule, i, patterns, ends) }
    }

    private fun elementChoices(lit: PathFact, mayBeArray: (Int) -> Boolean): List<PathFact> {
        val b = lit.base as? AccessPathBase.Argument ?: return listOf(lit)
        if (!mayBeArray(b.idx)) return listOf(lit)
        return listOf(lit, lit.copy(path = owned(listOf(elementIdx) + lit.path)))
    }
    private val elementIdx: AccessorIdx = manager.accessors.index(ElementAccessor)

    fun endFacts(rule: TaintConfigurationSink): List<MicroEdge> = MicroEdgeBuilder().apply {
        for (a in rule.trackFactsReachAnalysisEnd) ruleEdge(this, emptyList(), markTarget(rule, a))
    }.buildGen().edges

    fun pass(b: MicroEdgeBuilder, rule: TaintConfigurationItem, action: Action): Pair<AccessPathBase, AccessPathBase>? {
        val (fromPos, toPos) = when (action) {
            is CopyAllMarks -> action.from to action.to
            is CopyMark -> action.from to action.to
            else -> return null
        }
        val from = checkNotNull(pos(rule, fromPos)) { "a rejected rule reaches the forms (§1.3): $rule" }
        val to = checkNotNull(pos(rule, toPos)) { "a rejected rule reaches the forms (§1.3): $rule" }
        check(!from.isClass && !to.isClass)
        check(!from.any)
        b.edge(keepEdge(from.base))
        when (action) {
            is CopyAllMarks -> b.edge(PathEdge(from.star(), to.star(), ExclusionSet.Empty))
            is CopyMark -> mark(action.mark).let { t -> ruleEdge(b, listOf(from.fact(t)), to.fact(t)) }
            else -> Unit
        }
        return from.base to to.base
    }

    /** Only a rewritten true condition permits a cleaner; no remaining mark literal can request or clean. */
    fun cleanSteps(rule: TaintCleaner, cond: ExprOrConstant, types: PositionTypeResolver): List<CleanStep> =
        if (cond.isTrue) rule.actionsAfter.flatMap { unconditional(rule, it, types) } else emptyList()

    fun unconditional(rule: TaintConfigurationItem, a: Action, types: PositionTypeResolver): List<CleanStep> = when (a) {
        is RemoveMark -> {
            val p = checkNotNull(pos(rule, a.position)) { "a rejected rule reaches the forms (§1.3): $rule" }
            val t = mark(a.mark)
            val reach = when {
                p.any -> CleanReach.BELOW
                a.reach == TaintCleanReach.Exact -> CleanReach.EXACT
                else -> CleanReach.AT_AND_BELOW
            }
            val bytes = stringBytes(p, types)
            listOfNotNull(Cleaner(p.base, node(p.path), reach, t),
                bytes?.let { Cleaner(p.base, node(p.path + it), reach, t) }).map { lowerCleaner(it, types) }
        }
        is RemoveAllMarks -> {
            val p = checkNotNull(pos(rule, a.position)) { "a rejected rule reaches the forms (§1.3): $rule" }
            when {
                p.base == AccessPathBase.ClassStatic && p.any -> error("a rejected rule reaches the forms (§1.4): $rule")
                p.base == AccessPathBase.ClassStatic -> listOf(CleanStep.Kill(MicroEdgeBuilder().apply {
                    touch(AccessPathBase.ClassStatic)
                    strongKeep(AccessPathBase.ClassStatic, p.path).forEach { edge(it) }
                }.build()))
                else -> listOf(lowerCleaner(Cleaner(p.base, node(p.path),
                    if (p.any) CleanReach.BELOW else CleanReach.AT_AND_BELOW, mark = null), types))
            }
        }
        else -> emptyList()
    }

    /** Named nonstatic terminal fields use a typed field-value temporary. */
    fun lowerCleaner(cl: Cleaner, types: PositionTypeResolver): CleanStep {
        val path = cl.path?.toList().orEmpty()
        if (cl.base == AccessPathBase.ClassStatic || path.isEmpty() || !path.last().isFieldAccessor())
            return CleanStep.Clean(cl)
        val baseType = types.resolve(PositionAccess.Simple(cl.base)) as? JIRType
        val (baseFilter, valueFilter) = typeFilters.forField(baseType, path)
        return fieldCleanStep(cl.base, path, cl.reach, cl.mark, baseFilter = baseFilter, valueFilter = valueFilter)
    }

    private fun stringBytes(p: RulePos, types: PositionTypeResolver): AccessorIdx? {
        if (p.any) return null
        val rootType = types.resolve(PositionAccess.Simple(p.base)) as? JIRType
        if (typeFilters.valueType(rootType, p.path)?.typeName != "java.lang.String") return null
        return manager.accessors.index(FieldAccessor("java.lang.String", "<string-bytes>", "byte[]"))
    }
}

private fun <T> owned(xs: List<T>): List<T> = Collections.unmodifiableList(ArrayList(xs))
private fun ownFact(p: PathFact): PathFact = p.copy(path = owned(p.path))

/** Reject a complete rule, or report the pass-rule mark-condition exception, once per reason. */
class RuleErrors {
    private val seen = ConcurrentHashMap.newKeySet<Pair<Any, String>>()
    fun reject(rule: Any, reason: String): Boolean {
        if (seen.add(rule to reason)) logger.warn { "Rule rejected ($reason): $rule" }
        return false
    }
    fun applied(rule: Any, reason: String) {
        if (seen.add(rule to reason)) logger.warn { "Rule applied in part ($reason): $rule" }
    }
    private companion object { val logger = KotlinLogging.logger {} }
}
