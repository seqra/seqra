package org.opentaint.ir.analysis.config

import org.opentaint.ir.analysis.ifds.Maybe
import org.opentaint.ir.analysis.ifds.onSome
import org.opentaint.ir.analysis.ifds.toPath
import org.opentaint.ir.analysis.taint.Tainted
import org.opentaint.ir.analysis.util.startsWith
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

open class BasicConditionEvaluator(
    internal val positionResolver: PositionResolver<Maybe<JIRValue>>,
) : ConditionVisitor<Boolean> {

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: Not): Boolean {
        return !condition.arg.accept(this)
    }

    override fun visit(condition: And): Boolean {
        return condition.args.all { it.accept(this) }
    }

    override fun visit(condition: Or): Boolean {
        return condition.args.any { it.accept(this) }
    }

    override fun visit(condition: IsConstant): Boolean {
        positionResolver.resolve(condition.position).onSome { return it is JIRConstant }
        return false
    }

    override fun visit(condition: IsType): Boolean {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding IsType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: AnnotationType): Boolean {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding AnnotationType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: ConstantEq): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
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
            }
        }
        return false
    }

    override fun visit(condition: ConstantLt): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantIntValue -> {
                    value is JIRInt && value.value < constant.value
                }

                else -> error("Unexpected constant: $constant")
            }
        }
        return false
    }

    override fun visit(condition: ConstantGt): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantIntValue -> {
                    value is JIRInt && value.value > constant.value
                }

                else -> error("Unexpected constant: $constant")
            }
        }
        return false
    }

    override fun visit(condition: ConstantMatches): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            val re = condition.pattern.toRegex()
            return re.matches(value.toString())
        }
        return false
    }

    override fun visit(condition: SourceFunctionMatches): Boolean {
        TODO("Not implemented yet")
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return value.type.isAssignable(condition.type)
        }
        return false
    }
}

class FactAwareConditionEvaluator(
    private val fact: Tainted,
    positionResolver: PositionResolver<Maybe<JIRValue>>,
) : BasicConditionEvaluator(positionResolver) {

    override fun visit(condition: ContainsMark): Boolean {
        if (fact.mark != condition.mark) return false
        positionResolver.resolve(condition.position).onSome { value ->
            val variable = value.toPath()
            return variable.startsWith(fact.variable)
        }
        return false
    }
}
