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
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.MarkMode
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.ActionId
import org.opentaint.dataflow.bidi.interp.ActionRole
import org.opentaint.dataflow.bidi.interp.FormOwner
import org.opentaint.dataflow.bidi.interp.FormKey
import org.opentaint.dataflow.bidi.interp.RuleKind
import org.opentaint.dataflow.bidi.interp.FormCacheStatistics
import org.opentaint.dataflow.bidi.interp.RuleOccurrenceId
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.dataflow.bidi.interp.RuleView
import org.opentaint.dataflow.bidi.interp.OperationId
import org.opentaint.dataflow.bidi.interp.StaticOperation
import org.opentaint.dataflow.bidi.interp.SourceAction
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.dataflow.bidi.interp.SourceUnaryTransfer
import org.opentaint.dataflow.bidi.interp.TemporaryKey
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
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.JirCondition
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.ruleOrdinal
import org.opentaint.dataflow.configuration.jvm.actionOrdinal
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSink
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSource
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.PositionTypeResolver
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.dataflow.taint.SignedConditionNormalizer
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral
import org.opentaint.dataflow.taint.accessors
import org.opentaint.dataflow.taint.base
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/** Rule forms use callee coordinates. A rejected rule contributes no partial form. */
internal class JIRRuleForms(
    val rules: TaintRulesProvider,
    private val manager: ApManager,
    val errors: RuleErrors,
    private val typeFilters: JIRTypeFilters,
    private val resultBase: AccessPathBase = AccessPathBase.Return,
    private val statistics: FormCacheStatistics? = null,
    private val view: RuleView = RuleView.FULL,
) {
    private val fallbackFormOwner = FormOwner()
    internal val isRun1GroupView: Boolean get() = view == RuleView.RUN1_GROUP

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
        if (base == AccessPathBase.ClassStatic && path.size > 1 && path[1] !is FieldAccessor) {
            errors.reject(rule, "a static class accessor must be followed by a named field (F78 §1.1)")
            return null
        }
        return RulePos(base, owned(path.map { manager.accessors.index(it) }), any)
    }

    fun node(path: List<AccessorIdx>): PathNode? = manager.path(path)
    private fun fullMark(m: RuleMark): TaintMark = manager.marks.mark(m.name)
    private fun fullMark(name: String): TaintMark = manager.marks.mark(name)
    fun mark(m: RuleMark): TaintMark = selectedMark(fullMark(m))
    fun mark(name: String): TaintMark = selectedMark(manager.marks.mark(name))
    private fun selectedMark(mark: TaintMark): TaintMark = manager.marks.runtimeAtom(mark,
        if (view == RuleView.RUN1_GROUP) MarkMode.GROUP else MarkMode.FULL)

    fun literal(rule: Any, l: TaintMarkAwareConditionExpr.Literal): PathFact? = when (l) {
        is ContainsMarkLiteral -> pos(rule, l.position)?.fact(fullMark(l.mark.mark))
        is ContainsMarkOnAnyAccessorLiteral -> pos(rule, l.position)?.copy(any = true)?.fact(fullMark(l.mark.mark))
    }

    /** Validate before folding; entry eligibility can inspect signed output before negative approximation. */
    fun rewriteSourceOrSink(rule: TaintConfigurationItem, rewriter: RuleConditionRewriter<JirCondition>,
                            projectNegatives: Boolean = true,
                            potentialMarkLiteral: ((PathFact) -> Unit)? = null): ExprOrConstant {
        check(rule is TaintConfigurationSource || rule is TaintConfigurationSink)
        if (!valid(rule)) return RuleConditionRewriter.falseExpr
        val signed = SignedConditionNormalizer.normalize(rule.rawCondition, manager.cancellation)
        val symbolic: ((Condition) -> Unit)? = potentialMarkLiteral?.let { visit ->
            { condition -> visitPotentialMarkLiterals(rule, condition, visit) }
        }
        val rewritten = rewriteStatic(signed, rewriter, symbolic)
        return normalizedSourceOrSink(rewritten, projectNegatives)
    }

    /** Fold signed mark complements before the source/sink approximation of remaining negatives. */
    fun normalizedSourceOrSink(cond: ExprOrConstant, projectNegatives: Boolean = true): ExprOrConstant {
        manager.cancellation.checkpoint()
        if (cond.isFalse || cond.isTrue) return cond
        val signed = signedMarkCondition(cond.expr)
        return markExpression(SignedConditionNormalizer.normalize(signed, manager.cancellation,
            negativeLiteralsAreTrue = projectNegatives))
    }

    private fun rewriteStatic(condition: Condition, rewriter: RuleConditionRewriter<JirCondition>,
                              symbolicResult: ((Condition) -> Unit)? = null): ExprOrConstant {
        val done = IdentityHashMap<Condition, ExprOrConstant>()
        val symbolic = symbolicResult?.let { IdentityHashMap<Condition, Condition>() }
        val todo = ArrayDeque<Pair<Condition, Boolean>>().also { it.add(condition to false) }
        while (todo.isNotEmpty()) {
            manager.cancellation.checkpoint()
            val (node, finish) = todo.removeLast()
            if (done.containsKey(node)) continue
            val children = when (node) {
                is CommonCondition.And -> node.args
                is CommonCondition.Or -> node.args
                else -> emptyList()
            }
            if (!finish && children.isNotEmpty()) {
                todo.add(node to true)
                children.asReversed().forEach { todo.add(it to false) }
                continue
            }
            done[node] = when (node) {
                is CommonCondition.True -> RuleConditionRewriter.trueExpr
                is CommonCondition.Atom -> rewriter.rewriteAtom(node.atom, negated = false)
                is CommonCondition.Not -> {
                    val atom = node.arg as? CommonCondition.Atom
                    if (atom == null) {
                        check(node.arg is CommonCondition.True) { "Signed NNF expected" }
                        RuleConditionRewriter.falseExpr
                    } else rewriter.rewriteAtom(atom.atom, negated = true)
                }
                is CommonCondition.And -> markJunction(children.map { checkNotNull(done[it]) }, and = true)
                is CommonCondition.Or -> markJunction(children.map { checkNotNull(done[it]) }, and = false)
            }
            if (symbolic != null) {
                fun atom(atom: JirCondition, negative: Boolean): Condition {
                    if (atom is ContainsMark || atom is ContainsMarkOnAnyField ||
                        (rewriter as? JIRMarkAwareConditionRewriter)?.isMetadataUnknown(atom) == true) {
                        val leaf: Condition = CommonCondition.Atom(atom)
                        return if (negative) CommonCondition.Not(leaf) else leaf
                    }
                    val result = checkNotNull(done[node])
                    check(result.isTrue || result.isFalse) { "A known basic atom must be constant" }
                    return if (result.isTrue) mkTrue() else mkFalse()
                }
                symbolic[node] = when (node) {
                    is CommonCondition.True -> mkTrue()
                    is CommonCondition.Atom -> atom(node.atom, negative = false)
                    is CommonCondition.Not -> (node.arg as? CommonCondition.Atom)?.let { atom(it.atom, negative = true) }
                        ?: mkFalse()
                    is CommonCondition.And -> CommonCondition.And(children.map { checkNotNull(symbolic[it]) })
                    is CommonCondition.Or -> CommonCondition.Or(children.map { checkNotNull(symbolic[it]) })
                }
            }
        }
        if (symbolic != null) checkNotNull(symbolicResult)(checkNotNull(symbolic[condition]))
        return checkNotNull(done[condition])
    }

    /** Source/sink metadata needs retain unknown signed basic atoms. Only negative MARK
     * literals receive the existing positive approximation, after signed falsity folding. */
    private fun visitPotentialMarkLiterals(rule: TaintConfigurationItem, symbolic: Condition,
                                           visit: (PathFact) -> Unit) {
        val signed = SignedConditionNormalizer.normalize(symbolic, manager.cancellation)
        val done = IdentityHashMap<Condition, Condition>()
        val todo = ArrayDeque<Pair<Condition, Boolean>>().also { it.add(signed to false) }
        while (todo.isNotEmpty()) {
            manager.cancellation.checkpoint()
            val (node, finish) = todo.removeLast()
            if (done.containsKey(node)) continue
            val children = when (node) {
                is CommonCondition.And -> node.args
                is CommonCondition.Or -> node.args
                else -> emptyList()
            }
            if (!finish && children.isNotEmpty()) {
                todo.add(node to true)
                children.asReversed().forEach { todo.add(it to false) }
                continue
            }
            done[node] = when (node) {
                is CommonCondition.Not -> {
                    val atom = (node.arg as? CommonCondition.Atom)?.atom
                    if (atom is ContainsMark || atom is ContainsMarkOnAnyField) mkTrue() else node
                }
                is CommonCondition.And -> CommonCondition.And(children.map { checkNotNull(done[it]) })
                is CommonCondition.Or -> CommonCondition.Or(children.map { checkNotNull(done[it]) })
                else -> node
            }
        }
        val projected = SignedConditionNormalizer.normalize(checkNotNull(done[signed]), manager.cancellation)
        val remaining = ArrayDeque<Condition>().also { it.add(projected) }
        while (remaining.isNotEmpty()) {
            manager.cancellation.checkpoint()
            when (val node = remaining.removeLast()) {
                is CommonCondition.And -> remaining.addAll(node.args.asReversed())
                is CommonCondition.Or -> remaining.addAll(node.args.asReversed())
                is CommonCondition.Atom -> when (val atom = node.atom) {
                    is ContainsMark -> visit(checkNotNull(pos(rule, atom.position)).fact(mark(atom.mark)))
                    is ContainsMarkOnAnyField -> visit(checkNotNull(pos(rule, atom.position)).copy(any = true).fact(mark(atom.mark)))
                    else -> Unit
                }
                else -> Unit
            }
        }
    }

    private fun markJunction(children: List<ExprOrConstant>, and: Boolean): ExprOrConstant {
        if (children.any { if (and) it.isFalse else it.isTrue })
            return if (and) RuleConditionRewriter.falseExpr else RuleConditionRewriter.trueExpr
        val exprs = children.filterNot { if (and) it.isTrue else it.isFalse }.map { it.expr }
        if (exprs.isEmpty()) return if (and) RuleConditionRewriter.trueExpr else RuleConditionRewriter.falseExpr
        if (exprs.size == 1) return ExprOrConstant(exprs.single())
        return ExprOrConstant(if (and) TaintMarkAwareConditionExpr.And(exprs.toTypedArray())
            else TaintMarkAwareConditionExpr.Or(exprs.toTypedArray()))
    }

    private fun signedMarkCondition(expr: TaintMarkAwareConditionExpr): CommonCondition<TaintMarkAwareConditionExpr.Literal> {
        val done = IdentityHashMap<TaintMarkAwareConditionExpr, CommonCondition<TaintMarkAwareConditionExpr.Literal>>()
        val todo = ArrayDeque<Pair<TaintMarkAwareConditionExpr, Boolean>>().also { it.add(expr to false) }
        while (todo.isNotEmpty()) {
            manager.cancellation.checkpoint()
            val (node, finish) = todo.removeLast()
            if (done.containsKey(node)) continue
            val children = when (node) {
                is TaintMarkAwareConditionExpr.And -> node.args.toList()
                is TaintMarkAwareConditionExpr.Or -> node.args.toList()
                is TaintMarkAwareConditionExpr.Literal -> emptyList()
            }
            if (!finish && children.isNotEmpty()) {
                todo.add(node to true)
                children.asReversed().forEach { todo.add(it to false) }
                continue
            }
            done[node] = when (node) {
                is TaintMarkAwareConditionExpr.Literal -> {
                    val positive = when (node) {
                        is ContainsMarkLiteral -> node.copy(negated = false)
                        is ContainsMarkOnAnyAccessorLiteral -> node.copy(negated = false)
                    }
                    val atom: CommonCondition<TaintMarkAwareConditionExpr.Literal> = CommonCondition.Atom(positive)
                    if (node.negated) CommonCondition.Not(atom) else atom
                }
                is TaintMarkAwareConditionExpr.And -> CommonCondition.And(children.map { checkNotNull(done[it]) })
                is TaintMarkAwareConditionExpr.Or -> CommonCondition.Or(children.map { checkNotNull(done[it]) })
            }
        }
        return checkNotNull(done[expr])
    }

    private fun markExpression(condition: CommonCondition<TaintMarkAwareConditionExpr.Literal>): ExprOrConstant {
        val done = IdentityHashMap<CommonCondition<TaintMarkAwareConditionExpr.Literal>, ExprOrConstant>()
        val todo = ArrayDeque<Pair<CommonCondition<TaintMarkAwareConditionExpr.Literal>, Boolean>>()
        todo.add(condition to false)
        while (todo.isNotEmpty()) {
            manager.cancellation.checkpoint()
            val (node, finish) = todo.removeLast()
            if (done.containsKey(node)) continue
            val children = when (node) {
                is CommonCondition.And -> node.args
                is CommonCondition.Or -> node.args
                else -> emptyList()
            }
            if (!finish && children.isNotEmpty()) {
                todo.add(node to true)
                children.asReversed().forEach { todo.add(it to false) }
                continue
            }
            done[node] = when (node) {
                is CommonCondition.True -> RuleConditionRewriter.trueExpr
                is CommonCondition.Atom -> ExprOrConstant(node.atom)
                is CommonCondition.Not -> {
                    val atom = node.arg as? CommonCondition.Atom
                    if (atom == null) {
                        check(node.arg is CommonCondition.True)
                        RuleConditionRewriter.falseExpr
                    } else ExprOrConstant(atom.atom.negate())
                }
                is CommonCondition.And -> markJunction(children.map { checkNotNull(done[it]) }, and = true)
                is CommonCondition.Or -> markJunction(children.map { checkNotNull(done[it]) }, and = false)
            }
        }
        return checkNotNull(done[condition])
    }

    private fun fullPredicate(rule: Any, cond: ExprOrConstant, mayBeArray: (Int) -> Boolean = { false }): RulePredicate =
        JIRRulePredicates(manager, if (view == RuleView.FULL) statistics else null) {
            checkNotNull(literal(rule, it)) { "A rejected rule reaches predicate compilation" }
        }
            .compile(normalizedSourceOrSink(cond), mayBeArray)

    private fun groupConditions(): GroupRuleConditions = (rules as? EffectiveBidiRulesProvider)?.groupConditions(manager.cancellation)
        ?: GroupRuleConditions(manager.marks, manager.cancellation)

    private fun selectedPredicate(full: RulePredicate): RulePredicate =
        if (view == RuleView.FULL) full else groupConditions().select(full).also {
            statistics?.compiledPredicate(it.nodeCount, it.leaves.size)
        }

    fun predicate(rule: Any, cond: ExprOrConstant, mayBeArray: (Int) -> Boolean = { false }): RulePredicate =
        selectedPredicate(fullPredicate(rule, cond, mayBeArray))

    // Providers can return transient bound copies. Validate each input without retaining those rules.
    fun valid(rule: TaintConfigurationItem): Boolean = validate(rule)

    private fun validate(rule: TaintConfigurationItem): Boolean {
        var ok = true
        fun reject(reason: String) { errors.reject(rule, reason); ok = false }
        fun at(p: Position): RulePos? = pos(rule, p).also { if (it == null) ok = false }

        // An ignored action still has a raw rule position, which must be checked before rewriting.
        val actions: List<Action> = when (rule) {
            is TaintConfigurationSource -> rule.actionsAfter
            is TaintConfigurationSink -> emptyList()
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
            is TaintConfigurationSink -> Unit
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
        val todo = ArrayDeque<Condition>().also { it.add(c) }
        while (todo.isNotEmpty()) {
            manager.cancellation.checkpoint()
            when (val x = todo.removeLast()) {
                is CommonCondition.Atom -> when (val a = x.atom) {
                    is ContainsMark -> add(a.position to false)
                    is ContainsMarkOnAnyField -> add(a.position to true)
                    else -> Unit
                }
                is CommonCondition.Not -> todo.add(x.arg)
                is CommonCondition.And -> todo.addAll(x.args.asReversed())
                is CommonCondition.Or -> todo.addAll(x.args.asReversed())
                else -> Unit
            }
        }
    }

    private val TaintConfigurationItem.rawCondition: Condition get() = when (this) {
        is TaintConfigurationSource -> condition
        is TaintConfigurationSink -> condition
        is TaintPassThrough -> condition
        is TaintCleaner -> condition
    }

    private fun edge(b: MicroEdgeBuilder, from: PathFact?, target: PathFact) =
        b.edge(PathEdge(from?.let(::ownFact) ?: ZERO_FACT, ownFact(target), ExclusionSet.Empty))

    fun markTarget(rule: Any, a: AssignMark): PathFact =
        checkNotNull(pos(rule, a.position)) { "a rejected rule reaches the forms (§1.3): $rule" }.sourceFact(mark(a.mark))

    /** Constant sources retain the original zero edge; conditions and actions are separate vectors. */
    fun source(b: MicroEdgeBuilder, rule: TaintConfigurationSource, id: RuleOccurrenceId, cond: ExprOrConstant): RulePredicate {
        val full = fullPredicate(rule, cond)
        val predicate = selectedPredicate(full)
        if (predicate.isFalse || rule.actionsAfter.isEmpty()) return predicate
        rule.actionsAfter.forEachIndexed { ordinal, action ->
            val target = markTarget(rule, action)
            if (target.base == AccessPathBase.ClassStatic) {
                val actionId = ActionId(id, ActionRole.SOURCE, rule.actionOrdinal(ordinal))
                b.staticOperation(StaticOperation.Write(OperationId(id.form, action = actionId),
                    Pattern(target, ExclusionSet.Empty), action = actionId))
            }
        }
        if (predicate.isTrue) {
            rule.actionsAfter.forEachIndexed { ordinal, action ->
                b.sourceEdge(PathEdge(ZERO_FACT, markTarget(rule, action), ExclusionSet.Empty),
                    ActionId(id, ActionRole.SOURCE, rule.actionOrdinal(ordinal)))
            }
            return predicate
        }
        val actions = rule.actionsAfter.mapIndexed { ordinal, action ->
            SourceAction(ActionId(id, ActionRole.SOURCE, rule.actionOrdinal(ordinal)), markTarget(rule, action))
        }
        if (view == RuleView.FULL) {
            markInputs(predicate, id).forEach(b::staticOperation)
            b.source(SourceRuleOccurrence(id, predicate, actions))
        } else {
            val groups = groupConditions()
            val anchors = full.leaves.withIndex().filter { full.isUnary(it.index) }
                .map { groups.project(it.value) }.distinct()
            val unary = anchors.map { anchor ->
                val builder = RulePredicate.Builder(manager.cancellation, manager.marks)
                SourceUnaryTransfer(builder.build(builder.leaf(anchor)), anchor)
            }
            val multi = groups.multiAdmission(full)
            // Absorption of the observation guard must not erase an input of
            // the existing source AP operation retained for compatibility.
            markInputs((predicate.leaves + anchors + multi?.leaves.orEmpty()).distinct(), id).forEach(b::staticOperation)
            b.source(SourceRuleOccurrence(id, predicate, actions, groupTransfers = true,
                unaryTransfers = unary, multiPredicate = multi))
        }
        return predicate
    }

    fun markInputs(predicate: RulePredicate, id: RuleOccurrenceId): List<StaticOperation.MarkInput> = markInputs(predicate.leaves, id)

    private fun markInputs(patterns: List<Pattern>, id: RuleOccurrenceId): List<StaticOperation.MarkInput> =
        patterns.mapIndexedNotNull { ordinal, pattern ->
            if (pattern.fact.base != AccessPathBase.ClassStatic) null else
                StaticOperation.MarkInput(OperationId(id.form,
                    action = ActionId(id, ActionRole.MARK_INPUT, ordinal)), pattern)
        }

    fun sinkInputs(sinks: List<SinkRule>): List<StaticOperation.MarkInput> = sinks.flatMap { sink ->
        val id = checkNotNull(sink.occurrenceId)
        sink.predicate?.let { markInputs(it, id) }.orEmpty()
    }

    /** The provider is reduced by prescan; rule selection never depends on a propagated fact. */
    fun readSources(b: MicroEdgeBuilder, s: JIRAssignInst,
                    form: FormKey = FormKey(fallbackFormOwner, s.location.method, null, s)) {
        val field = (s.rhv as? JIRFieldRef)?.field?.field?.takeIf { it.isStatic } ?: return
        val x = accessPathBase(s.lhv) ?: return
        for ((ruleOrdinal, rule) in rules.sourceRulesForStaticField(field, s, fact = null).withIndex()) {
            if (!valid(rule)) continue
            b.keepZero()
            for ((actionOrdinal, action) in rule.actionsAfter.withIndex()) {
                val p = checkNotNull(pos(rule, action.position))
                val id = ActionId(RuleOccurrenceId(form, RuleKind.FIELD_SOURCE, rule.ruleOrdinal(ruleOrdinal)),
                    ActionRole.SOURCE, rule.actionOrdinal(actionOrdinal))
                b.sourceEdge(PathEdge(ZERO_FACT, p.copy(base = x).sourceFact(mark(action.mark)),
                    ExclusionSet.Empty), id)
            }
        }
    }

    fun sinks(rule: TaintConfigurationSink, cond: ExprOrConstant, id: RuleOccurrenceId,
              mayBeArray: (Int) -> Boolean = { false }): List<SinkRule> {
        val predicate = predicate(rule, cond, mayBeArray)
        if (predicate.isFalse) return emptyList()
        return listOf(SinkRule(rule, 0, if (predicate.isTrue) listOf(ZERO_PATTERN) else predicate.leaves,
            endFacts(rule), id, predicate))
    }

    fun endFacts(rule: TaintConfigurationSink): List<MicroEdge> = MicroEdgeBuilder().apply {
        // The new core does not execute end-fact actions in any run.
    }.buildGen().edges

    fun pass(b: MicroEdgeBuilder, rule: TaintConfigurationItem, action: Action,
             operation: OperationId? = null): Pair<AccessPathBase, AccessPathBase>? {
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
        val transfer = when (action) {
            is CopyAllMarks -> PathEdge(from.star(), to.star(), ExclusionSet.Empty)
            is CopyMark -> mark(action.mark).let { t -> PathEdge(from.fact(t), to.fact(t), ExclusionSet.Empty) }
            else -> error("Not a copy action")
        }
        b.edge(transfer)
        if (operation != null) {
            if (from.base == AccessPathBase.ClassStatic && transfer.from != transfer.to) {
                if (action is CopyAllMarks) b.staticOperation(StaticOperation.Read(operation, transfer))
                else b.staticOperation(StaticOperation.MarkInput(operation, Pattern(transfer.from, ExclusionSet.Empty)))
            }
            if (to.base == AccessPathBase.ClassStatic && transfer.from != transfer.to)
                b.staticOperation(StaticOperation.Write(operation, Pattern(transfer.to, ExclusionSet.Empty), transfer))
        }
        return from.base to to.base
    }

    /** Only a rewritten true condition permits a cleaner; no remaining mark literal can request or clean. */
    fun cleanSteps(rule: TaintCleaner, cond: ExprOrConstant, types: PositionTypeResolver,
                   id: RuleOccurrenceId? = null): List<CleanStep> =
        if (cond.isTrue) rule.actionsAfter.flatMapIndexed { ordinal, action ->
            unconditional(rule, action, types, id?.let { ActionId(it, ActionRole.BOUND_CLEANER, rule.actionOrdinal(ordinal)) })
        } else emptyList()

    fun unconditional(rule: TaintConfigurationItem, a: Action, types: PositionTypeResolver,
                      actionId: ActionId? = null): List<CleanStep> = when (a) {
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
                bytes?.let { Cleaner(p.base, node(p.path + it), reach, t) }).mapIndexed { substep, cl ->
                lowerCleaner(cl, types, actionId?.let { TemporaryKey(it, substep) })
            }
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
                    if (p.any) CleanReach.BELOW else CleanReach.AT_AND_BELOW, mark = null), types,
                    actionId?.let { TemporaryKey(it) }))
            }
        }
        else -> emptyList()
    }

    /** Named nonstatic terminal fields use a typed field-value temporary. */
    fun lowerCleaner(cl: Cleaner, types: PositionTypeResolver, temporaryKey: TemporaryKey? = null): CleanStep {
        val path = cl.path?.toList().orEmpty()
        if (cl.base == AccessPathBase.ClassStatic || path.isEmpty() || !path.last().isFieldAccessor())
            return CleanStep.Clean(cl)
        val baseType = types.resolve(PositionAccess.Simple(cl.base)) as? JIRType
        val (baseFilter, valueFilter) = typeFilters.forField(baseType, path)
        return fieldCleanStep(cl.base, path, cl.reach, cl.mark, baseFilter = baseFilter, valueFilter = valueFilter,
            temporaryKey = temporaryKey)
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

/** Identified rules share diagnostics across bound copies without retaining the rule or condition. */
class RuleErrors {
    private data class Diagnostic(val ruleType: String, val ruleId: String, val kind: String, val reason: String)
    private val seen = ConcurrentHashMap.newKeySet<Diagnostic>()
    private fun ruleId(rule: Any): String? =
        (rule as? TaintConfigurationItem)?.serializedId ?: (rule as? TaintConfigurationSink)?.id
    private fun first(rule: Any, kind: String, reason: String): Boolean {
        // Without a declared ID, keep the warning rather than conflate distinct anonymous rules.
        val id = ruleId(rule) ?: return true
        return seen.add(Diagnostic(rule.javaClass.name, id, kind, reason))
    }
    private fun description(rule: Any): String =
        "${rule.javaClass.simpleName}[${ruleId(rule) ?: "unidentified"}]"
    fun reject(rule: Any, reason: String): Boolean {
        if (first(rule, "rejected", reason)) logger.warn { "Rule rejected ($reason): ${description(rule)}" }
        return false
    }
    fun applied(rule: Any, reason: String) {
        if (first(rule, "applied", reason)) logger.warn { "Rule applied in part ($reason): ${description(rule)}" }
    }
    private companion object { val logger = KotlinLogging.logger {} }
}
