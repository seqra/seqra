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

package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.isAssignable
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.ConditionNameMatcher
import org.opentaint.dataflow.configuration.jvm.ConditionVisitor
import org.opentaint.dataflow.configuration.jvm.ConstantBooleanValue
import org.opentaint.dataflow.configuration.jvm.ConstantEq
import org.opentaint.dataflow.configuration.jvm.ConstantGt
import org.opentaint.dataflow.configuration.jvm.ConstantIntValue
import org.opentaint.dataflow.configuration.jvm.ConstantLt
import org.opentaint.dataflow.configuration.jvm.ConstantMatches
import org.opentaint.dataflow.configuration.jvm.ConstantStringValue
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ConstantValue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.IsConstant
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.util.Maybe
import org.opentaint.util.onSome

open class JIRBasicConditionEvaluator(
    private val positionResolver: PositionResolver<Maybe<JIRValue>>,
    private val typeChecker: JIRFactTypeChecker
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

    override fun visit(condition: IsConstant): Boolean{
        positionResolver.resolve(condition.position).onSome {
            return isConstant(it)
        }
        return false
    }

    override fun visit(condition: ConstantEq): Boolean   {
        positionResolver.resolve(condition.position).onSome { value ->
            return eqConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantLt): Boolean  {
        positionResolver.resolve(condition.position).onSome { value ->
            return ltConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantGt): Boolean  {
        positionResolver.resolve(condition.position).onSome { value ->
            return gtConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantMatches): Boolean   {
        positionResolver.resolve(condition.position).onSome { value ->
            return matches(value, condition.pattern)
        }
        return false
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean   {
        positionResolver.resolve(condition.position).onSome { value ->
            return typeMatches(value, condition)
        }
        return false
    }

    override fun visit(condition: TypeMatchesPattern): Boolean   {
        positionResolver.resolve(condition.position).onSome { value ->
            return typeMatchesPattern(value, condition.position, condition)
        }
        return false
    }

    private fun isConstant(value: JIRValue): Boolean {
        return value is JIRConstant
    }

    private fun eqConstant(value: JIRValue, constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantBooleanValue -> {
                when (value) {
                    is JIRBool -> value.value == constant.value
                    is JIRInt -> if (constant.value) value.value != 0 else value.value == 0
                    else -> false
                }
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

    private fun ltConstant(value: JIRValue, constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue -> {
                value is JIRInt && value.value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    private fun gtConstant(value: JIRValue, constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue -> {
                value is JIRInt && value.value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    private fun matches(value: JIRValue, pattern: Regex): Boolean {
        val s = value.toString()
        return pattern.matches(s)
    }

    private fun typeMatches(value: CommonValue, condition: TypeMatches): Boolean {
        check(value is JIRValue)
        return value.type.isAssignable(condition.type)
    }

    private fun typeMatchesPattern(value: JIRValue, pos: Position, condition: TypeMatchesPattern): Boolean {
        val type = value.type as? JIRRefType ?: return false
        val cls = type.jirClass

        when (val pattern = condition.pattern) {
            is ConditionNameMatcher.Pattern -> {
                if (pattern.matchName(cls.name)) return true

                // todo: check super classes?
                return false
            }

            is ConditionNameMatcher.Concrete -> {
                if (pattern.matchName(cls.name)) return true

                val patternCls = cls.classpath.findClassOrNull(pattern.name)
                    ?: return false // todo: maybe true, leads to more fp

                if (cls.isSubClassOf(patternCls)) return true

                // todo: try to avoid this hack
                if (pos !is This) return false
                if (cls == cls.classpath.objectClass) return false

                if (cls.isInterface) {
                    return typeChecker.interfaceMayHaveSubtypeOf(cls, patternCls)
                } else {
                    return patternCls.isSubClassOf(cls)
                }
            }
        }
    }

    private fun ConditionNameMatcher.matchName(name: String): Boolean = when (this) {
        is ConditionNameMatcher.Concrete -> this.name == name
        is ConditionNameMatcher.Pattern -> this.pattern.containsMatchIn(name)
    }
}
