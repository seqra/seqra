package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.ContainsMarkOnAnyField
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Literal
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicAtomEvaluator
import org.opentaint.ir.api.common.cfg.CommonInst

class JIRMarkAwareConditionRewriter(
    positionResolver: PositionResolver<CallPositionValue>,
    factTypeChecker: JIRFactTypeChecker,
    aliasAnalysis: JIRLocalAliasAnalysis?,
    statement: CommonInst,
) {
    private val positiveAtomEvaluator = JIRBasicAtomEvaluator(negated = false, positionResolver, factTypeChecker, aliasAnalysis, statement)
    private val negativeAtomEvaluator = JIRBasicAtomEvaluator(negated = true, positionResolver, factTypeChecker, aliasAnalysis, statement)

    constructor(
        positionResolver: PositionResolver<CallPositionValue>,
        context: JIRMethodAnalysisContext,
        statement: CommonInst
    ) : this(positionResolver, context.factTypeChecker, context.aliasAnalysis, statement)

    fun rewrite(condition: Condition): ExprOrConstant =
        rewriteCondition(condition)

    private fun rewriteCondition(condition: Condition): ExprOrConstant = when (condition) {
        is And -> rewriteAndCondition(condition)
        is Or -> rewriteOrCondition(condition)
        is Not -> rewriteNotCondition(condition)
        else -> rewriteAtom(condition, positiveAtomEvaluator)
    }

    private fun rewriteAndCondition(condition: And): ExprOrConstant {
        return rewriteList(condition.args, ExprOrConstant.trueExpr, JIRMarkAwareConditionExpr::And) {
            when {
                it.isTrue -> null
                it.isFalse -> return ExprOrConstant.falseExpr
                else -> it.expr
            }
        }
    }

    private fun rewriteOrCondition(condition: Or): ExprOrConstant {
        return rewriteList(condition.args, ExprOrConstant.falseExpr, JIRMarkAwareConditionExpr::Or) {
            when {
                it.isTrue -> return ExprOrConstant.trueExpr
                it.isFalse -> null
                else -> it.expr
            }
        }
    }

    private fun rewriteNotCondition(condition: Not): ExprOrConstant =
        rewriteAtom(condition.arg, negativeAtomEvaluator).negate()

    private fun rewriteAtom(atom: Condition, evaluator: JIRBasicAtomEvaluator): ExprOrConstant {
        if (atom is ContainsMark) {
            return ExprOrConstant(JIRMarkAwareConditionExpr.ContainsMarkLiteral(atom, negated = false))
        }

        if (atom is ContainsMarkOnAnyField) {
            return ExprOrConstant(JIRMarkAwareConditionExpr.ContainsMarkOnAnyFieldLiteral(atom, negated = false))
        }

        val result = atom.accept(evaluator)
        return if (result) ExprOrConstant.trueExpr else ExprOrConstant.falseExpr
    }

    private fun JIRMarkAwareConditionExpr.negate(): JIRMarkAwareConditionExpr = when (this) {
        is Literal -> negate()
        is JIRMarkAwareConditionExpr.And,
        is JIRMarkAwareConditionExpr.Or -> error("Unexpected formula structure")
    }

    private inline fun rewriteList(
        elements: List<Condition>,
        default: ExprOrConstant,
        create: (Array<JIRMarkAwareConditionExpr>) -> JIRMarkAwareConditionExpr,
        processElement: (ExprOrConstant) -> JIRMarkAwareConditionExpr?,
    ): ExprOrConstant {
        val result = arrayOfNulls<JIRMarkAwareConditionExpr>(elements.size)
        var size = 0
        for (i in elements.indices) {
            val elementResult = rewriteCondition(elements[i])
            val elementExpr = processElement(elementResult) ?: continue
            result[size++] = elementExpr
        }

        if (size == 0) {
            return default
        }

        if (size == 1) {
            return ExprOrConstant(result[0]!!)
        }

        val resultExprs = result.copyOf(size)

        @Suppress("UNCHECKED_CAST")
        resultExprs as Array<JIRMarkAwareConditionExpr>

        return ExprOrConstant(create(resultExprs))
    }

    private fun ExprOrConstant.negate(): ExprOrConstant = when {
        this.isFalse -> ExprOrConstant.trueExpr
        this.isTrue -> ExprOrConstant.falseExpr
        else -> ExprOrConstant(expr.negate())
    }
}

@JvmInline
value class ExprOrConstant(private val rawValue: Any?) {
    val isTrue: Boolean get() = rawValue === trueMarker
    val isFalse: Boolean get() = rawValue === falseMarker

    val expr: JIRMarkAwareConditionExpr get() = rawValue as JIRMarkAwareConditionExpr

    companion object {
        private val trueMarker = Any()
        private val falseMarker = Any()

        val trueExpr = ExprOrConstant(trueMarker)
        val falseExpr = ExprOrConstant(falseMarker)
    }
}
