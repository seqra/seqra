package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.dataflow.bidi.interp.FormCacheStatistics
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import java.util.IdentityHashMap

/** Bind positive NNF once. Array alternatives are local OR nodes, never a clause product. */
internal class JIRRulePredicates(private val manager: ApManager,
                                 private val statistics: FormCacheStatistics? = null,
                                 private val literal: (TaintMarkAwareConditionExpr.Literal) -> PathFact) {
    fun compile(condition: ExprOrConstant, mayBeArray: (Int) -> Boolean = { false }): RulePredicate {
        val builder = RulePredicate.Builder(manager.cancellation, manager.marks)
        fun built(root: Int): RulePredicate = builder.build(root).also {
            statistics?.compiledPredicate(it.nodeCount, it.leaves.size)
        }
        if (condition.isFalse) return built(builder.falseNode)
        if (condition.isTrue) return built(builder.trueNode)
        val done = IdentityHashMap<TaintMarkAwareConditionExpr, Int>()
        val todo = ArrayDeque<Pair<TaintMarkAwareConditionExpr, Boolean>>()
        todo.add(condition.expr to false)
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
                    check(!node.negated) { "Positive source/sink NNF expected" }
                    val fact = literal(node)
                    val root = builder.leaf(Pattern(fact, ExclusionSet.Empty))
                    val argument = fact.base as? AccessPathBase.Argument
                    if (argument == null || !mayBeArray(argument.idx)) root else {
                        val element = manager.accessors.index(ElementAccessor)
                        val prefixed = builder.leaf(Pattern(fact.copy(path = listOf(element) + fact.path), ExclusionSet.Empty))
                        builder.or(listOf(root, prefixed))
                    }
                }
                is TaintMarkAwareConditionExpr.And -> builder.and(children.map { checkNotNull(done[it]) })
                is TaintMarkAwareConditionExpr.Or -> builder.or(children.map { checkNotNull(done[it]) })
            }
        }
        return built(checkNotNull(done[condition.expr]))
    }
}
