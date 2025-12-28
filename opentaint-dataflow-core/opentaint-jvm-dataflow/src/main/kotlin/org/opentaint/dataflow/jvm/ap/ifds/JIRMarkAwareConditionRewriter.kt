package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Literal
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicAtomEvaluator
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.util.Maybe

class JIRMarkAwareConditionRewriter(
    positionResolver: PositionResolver<Maybe<JIRValue>>,
    typeChecker: JIRFactTypeChecker,
) {
    private val positiveAtomEvaluator = JIRBasicAtomEvaluator(negated = false, positionResolver, typeChecker)
    private val negativeAtomEvaluator = JIRBasicAtomEvaluator(negated = true, positionResolver, typeChecker)

    fun rewrite(condition: Condition): ExprOrConstant =
        rewriteCondition(condition)

    private fun rewriteCondition(condition: Condition): ExprOrConstant = when (condition) {
        is And -> rewriteAndCondition(condition)
        is Or -> rewriteOrCondition(condition)
        is Not -> rewriteNotCondition(condition)
        else -> rewriteAtom(condition, positiveAtomEvaluator)
    }

    private fun rewriteAndCondition(condition: And): ExprOrConstant {
        return rewriteList(condition.args, trueExpr, JIRMarkAwareConditionExpr::And) {
            when {
                it.isTrue -> null
                it.isFalse -> return falseExpr
                else -> it.expr
            }
        }
    }

    private fun rewriteOrCondition(condition: Or): ExprOrConstant {
        return rewriteList(condition.args, falseExpr, JIRMarkAwareConditionExpr::Or) {
            when {
                it.isTrue -> return trueExpr
                it.isFalse -> null
                else -> it.expr
            }
        }
    }

    private fun rewriteNotCondition(condition: Not): ExprOrConstant =
        rewriteAtom(condition.arg, negativeAtomEvaluator).negate()

    private fun rewriteAtom(atom: Condition, evaluator: JIRBasicAtomEvaluator): ExprOrConstant {
        if (atom is ContainsMark) {
            return ExprOrConstant(Literal(atom, negated = false))
        }

        val result = atom.accept(evaluator)
        return if (result) trueExpr else falseExpr
    }

    private fun JIRMarkAwareConditionExpr.negate(): JIRMarkAwareConditionExpr = when (this) {
        is Literal -> Literal(condition, negated = !negated)
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

    @JvmInline
    value class ExprOrConstant(private val rawValue: Any?) {
        val isTrue: Boolean get() = rawValue === trueMarker
        val isFalse: Boolean get() = rawValue === falseMarker

        val expr: JIRMarkAwareConditionExpr get() = rawValue as JIRMarkAwareConditionExpr
    }

    private fun ExprOrConstant.negate(): ExprOrConstant = when {
        this.isFalse -> trueExpr
        this.isTrue -> falseExpr
        else -> ExprOrConstant(expr.negate())
    }

    companion object {
        private val trueMarker = Any()
        private val falseMarker = Any()

        private val trueExpr = ExprOrConstant(trueMarker)
        private val falseExpr = ExprOrConstant(falseMarker)
    }
}
