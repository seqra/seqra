package org.opentaint.dataflow.python

import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext.RuleWithCondition
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.python.TaintConfigurationItem
import org.opentaint.dataflow.configuration.python.AnyArgument
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.ContainsMark
import org.opentaint.dataflow.configuration.python.ContainsMarkOnAnyAccessor
import org.opentaint.dataflow.configuration.python.Position
import org.opentaint.dataflow.configuration.python.PositionWithAccess
import org.opentaint.dataflow.configuration.python.PythonRuleCondition
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.dataflow.taint.RuleConditionRewriter.Companion.falseExpr
import org.opentaint.dataflow.taint.RuleConditionRewriter.Companion.trueExpr
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind

class PIRConditionRewriter(
    private val anyArgumentResolver: AnyArgumentResolver,
    private val atomEvaluator: PIRBasicAtomEvaluator,
    private val call: PIRCall? = null, // TODO ap resolver
) : RuleConditionRewriter<PythonRuleCondition> {

    override fun rewriteAtom(atom: PythonRuleCondition, negated: Boolean): ExprOrConstant {
        if (!negated) {
            return rewriteAtom(atom)
        }
        return rewriteAtom(atom).negate()
    }

    private fun rewriteAtom(atom: PythonRuleCondition): ExprOrConstant {
        if (atom is ContainsMark) {
            if (atom.pos.rootBase() is AnyArgument) {
                return expandAnyArgument(atom.pos) { ContainsMark(atom.mark, it) }
            }

            val pos = atom.pos.resolveAp(call) ?: return falseExpr
            val literal = ContainsMarkLiteral(pos, TaintMarkAccessor(atom.mark.name), negated = false)
            return ExprOrConstant(literal)
        }

        if (atom is ContainsMarkOnAnyAccessor) {
            if (atom.pos.rootBase() is AnyArgument) {
                return expandAnyArgument(atom.pos) { ContainsMarkOnAnyAccessor(atom.mark, it) }
            }

            val pos = atom.pos.resolveAp(call) ?: return falseExpr
            val literal = TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral(pos, TaintMarkAccessor(atom.mark.name), negated = false)
            return ExprOrConstant(literal)
        }

        val result = atom.accept(atomEvaluator)
        return if (result) trueExpr else falseExpr
    }

    private fun expandAnyArgument(
        pos: Position,
        build: (Position) -> PythonRuleCondition,
    ): ExprOrConstant {
        val expandedArgs = anyArgumentResolver.resolve(AnyArgument)
        val expandedAtom = expandedArgs.map {
            CommonCondition.Atom(build(pos.replaceRoot(it)))
        }

        return rewriteOrCondition(expandedAtom)
    }

    private fun Position.rootBase(): Position = when (this) {
        is PositionWithAccess -> base.rootBase()
        else -> this
    }

    private fun Position.replaceRoot(newRoot: Position): Position = when (this) {
        is PositionWithAccess -> PositionWithAccess(base.replaceRoot(newRoot), access)
        else -> newRoot
    }
}

fun <T : TaintConfigurationItem> PIRConditionRewriter.rulesWithConditions(
    rules: List<T>
): List<RuleWithCondition<T>> = rules.mapNotNull { rule ->
    val condition = rewrite(rule.condition)
    if (condition.isFalse) return@mapNotNull null
    RuleWithCondition(rule, condition)
}

interface AnyArgumentResolver {
    fun resolve(position: AnyArgument): List<Position>
}

class PIRCallAnyArgumentResolver(val call: PIRCall) : AnyArgumentResolver {
    override fun resolve(position: AnyArgument): List<Position> =
        call.args.indices.mapNotNull { argIdx ->
            when (call.args[argIdx].kind) {
                PIRCallArgKind.POSITIONAL, PIRCallArgKind.KEYWORD -> Argument(argIdx)
                PIRCallArgKind.STAR, PIRCallArgKind.DOUBLE_STAR -> null
            }
        }
}

object PIRAttrLoadAnyArgumentResolver : AnyArgumentResolver {
    override fun resolve(position: AnyArgument): List<Position> {
        error("Unexpected attribute load rule position: $position")
    }
}
