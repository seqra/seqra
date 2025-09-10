/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.config

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.And
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.ConstantEq
import org.opentaint.ir.taint.configuration.ConstantGt
import org.opentaint.ir.taint.configuration.ConstantLt
import org.opentaint.ir.taint.configuration.ConstantMatches
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.ContainsMark
import org.opentaint.ir.taint.configuration.IsConstant
import org.opentaint.ir.taint.configuration.Not
import org.opentaint.ir.taint.configuration.Or
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.TypeMatches
import org.opentaint.dataflow.taint.Tainted
import org.opentaint.dataflow.util.Traits
import org.opentaint.dataflow.util.removeTrailingElementAccessors
import org.opentaint.util.Maybe
import org.opentaint.util.onSome

open class BasicConditionEvaluator(
    val traits: Traits<CommonMethod, CommonInst>,
    internal val positionResolver: PositionResolver<Maybe<CommonValue>>
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

    override fun visit(condition: IsConstant): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome {
            return isConstant(it)
        }
        return false
    }

    override fun visit(condition: ConstantEq): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return eqConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantLt): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return ltConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantGt): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return gtConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantMatches): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return matches(value, condition.pattern)
        }
        return false
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return typeMatches(value, condition)
        }
        return false
    }
}

class FactAwareConditionEvaluator(
    traits: Traits<CommonMethod, CommonInst>,
    private val fact: Tainted,
    positionResolver: PositionResolver<Maybe<CommonValue>>,
) : BasicConditionEvaluator(traits, positionResolver) {

    override fun visit(condition: ContainsMark): Boolean = with(traits) {
        if (fact.mark != condition.mark) return false
        positionResolver.resolve(condition.position).onSome { value ->
            val variable = convertToPath(value)

            // FIXME: Adhoc for arrays
            val variableWithoutStars = variable.removeTrailingElementAccessors()
            val factWithoutStars = fact.variable.removeTrailingElementAccessors()
            if (variableWithoutStars == factWithoutStars) return true

            return variable == fact.variable
        }
        return false
    }
}
