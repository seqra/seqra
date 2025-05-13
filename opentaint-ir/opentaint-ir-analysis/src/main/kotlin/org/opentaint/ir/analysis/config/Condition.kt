package org.opentaint.ir.analysis.config

import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.api.cfg.JIRBool
import org.opentaint.ir.api.cfg.JIRConstant
import org.opentaint.ir.api.cfg.JIRInt
import org.opentaint.ir.api.cfg.JIRStringConstant
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.isAssignable
import org.opentaint.ir.taint.configuration.And
import org.opentaint.ir.taint.configuration.AnnotationType
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.ConstantBooleanValue
import org.opentaint.ir.taint.configuration.ConstantEq
import org.opentaint.ir.taint.configuration.ConstantGt
import org.opentaint.ir.taint.configuration.ConstantIntValue
import org.opentaint.ir.taint.configuration.ConstantLt
import org.opentaint.ir.taint.configuration.ConstantMatches
import org.opentaint.ir.taint.configuration.ConstantStringValue
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.ContainsMark
import org.opentaint.ir.taint.configuration.IsConstant
import org.opentaint.ir.taint.configuration.IsType
import org.opentaint.ir.taint.configuration.Not
import org.opentaint.ir.taint.configuration.Or
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.SourceFunctionMatches
import org.opentaint.ir.taint.configuration.TypeMatches

class BasicConditionEvaluator(
    internal val positionResolver: PositionResolver<JIRValue>,
) : ConditionVisitor<Boolean> {

    // Default condition handler:
    override fun visit(condition: Condition): Boolean {
        return false
    }

    override fun visit(condition: And): Boolean {
        return condition.args.all { it.accept(this) }
    }

    override fun visit(condition: Or): Boolean {
        return condition.args.any { it.accept(this) }
    }

    override fun visit(condition: Not): Boolean {
        return !condition.arg.accept(this)
    }

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: IsConstant): Boolean {
        val value = positionResolver.resolve(condition.position)
        return value is JIRConstant
    }

    override fun visit(condition: IsType): Boolean {
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: AnnotationType): Boolean {
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: ConstantEq): Boolean {
        val value = positionResolver.resolve(condition.position)
        return when (val constant = condition.value) {
            is ConstantBooleanValue -> {
                value is JIRBool && value.value == constant.value
            }

            is ConstantIntValue -> {
                value is JIRInt && value.value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                value is JIRStringConstant && value.value == constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun visit(condition: ConstantLt): Boolean {
        val value = positionResolver.resolve(condition.position)
        return when (val constant = condition.value) {
            is ConstantIntValue -> {
                value is JIRInt && value.value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun visit(condition: ConstantGt): Boolean {
        val value = positionResolver.resolve(condition.position)
        return when (val constant = condition.value) {
            is ConstantIntValue -> {
                value is JIRInt && value.value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun visit(condition: ConstantMatches): Boolean {
        val value = positionResolver.resolve(condition.position)
        val re = condition.pattern.toRegex()
        return re.matches(value.toString())
    }

    override fun visit(condition: SourceFunctionMatches): Boolean {
        TODO("Not implemented yet")
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean {
        val value = positionResolver.resolve(condition.position)
        return value.type.isAssignable(condition.type)
    }
}

class FactAwareConditionEvaluator(
    private val fact: Tainted,
    private val basicConditionEvaluator: BasicConditionEvaluator,
) : ConditionVisitor<Boolean> {

    constructor(
        fact: Tainted,
        positionResolver: PositionResolver<JIRValue>,
    ) : this(fact, BasicConditionEvaluator(positionResolver))

    override fun visit(condition: ContainsMark): Boolean {
        if (fact.mark == condition.mark) {
            val value = basicConditionEvaluator.positionResolver.resolve(condition.position)
            val variable = value.toPath()
            if (variable.startsWith(fact.variable)) {
                return true
            }
        }
        return false
    }

    override fun visit(condition: And): Boolean {
        return condition.args.all { it.accept(this) }
    }

    override fun visit(condition: Or): Boolean {
        return condition.args.any { it.accept(this) }
    }

    override fun visit(condition: Not): Boolean {
        return !condition.arg.accept(this)
    }

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: IsConstant): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: IsType): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: AnnotationType): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: ConstantEq): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: ConstantLt): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: ConstantGt): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: ConstantMatches): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: SourceFunctionMatches): Boolean = basicConditionEvaluator.visit(condition)
    override fun visit(condition: TypeMatches): Boolean = basicConditionEvaluator.visit(condition)

    override fun visit(condition: Condition): Boolean = basicConditionEvaluator.visit(condition)
}
