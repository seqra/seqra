package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
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
import org.opentaint.dataflow.configuration.jvm.IsNull
import org.opentaint.dataflow.configuration.jvm.IsStaticField
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionValue
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.isAssignable

class JIRBasicAtomEvaluator(
    private val negated: Boolean,
    private val positionResolver: PositionResolver<CallPositionValue>,
    private val analysisContext: JIRMethodAnalysisContext,
    private val statement: CommonInst,
) : ConditionVisitor<Boolean> {
    private val typeChecker get() = analysisContext.factTypeChecker

    override fun visit(condition: Not): Boolean = error("Non-atomic condition")
    override fun visit(condition: And): Boolean = error("Non-atomic condition")
    override fun visit(condition: Or): Boolean = error("Non-atomic condition")

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: IsConstant): Boolean =
        condition.position.eval(
            value = { isConstant(it) },
            callVarArgValue = { false }, // todo: vararg
        )

    override fun visit(condition: IsNull): Boolean =
        condition.position.eval(
            value = { isNull(it) },
            callVarArgValue = { false }, // todo: vararg
        )

    override fun visit(condition: ConstantEq): Boolean =
        condition.position.eval(
            value = { eqConstant(it, condition.value) },
            callVarArgValue = { false }, // todo: vararg
        )

    override fun visit(condition: ConstantLt): Boolean =
        condition.position.eval(
            value = { ltConstant(it, condition.value) },
            callVarArgValue = { false }, // todo: vararg
        )

    override fun visit(condition: ConstantGt): Boolean =
        condition.position.eval(
            value = { gtConstant(it, condition.value) },
            callVarArgValue = { false }, // todo: vararg
        )

    // note: ConstantMatches means StringConstantMatches
    override fun visit(condition: ConstantMatches): Boolean =
        condition.position.eval(
            value = { matches(it, condition.pattern, matchArrayValue = false) },
            callVarArgValue = { matches(it, condition.pattern, matchArrayValue = true) },
        )

    override fun visit(condition: TypeMatches): Boolean =
        condition.position.eval(
            value = { typeMatches(it, condition) },
            callVarArgValue = { typeMatches(it, condition) }, // todo: vararg
        )

    override fun visit(condition: IsStaticField): Boolean =
        condition.position.eval(
            value = { isStaticField(it, condition, matchArrayValue = false) },
            callVarArgValue = { isStaticField(it, condition, matchArrayValue = true) },
        )

    private val typeMatchesCache = hashMapOf<TypeMatchesPattern, Boolean>()

    override fun visit(condition: TypeMatchesPattern): Boolean =
        condition.position.eval(
            value = { condition.evalTypeMatches(it) },
            callVarArgValue = { condition.evalTypeMatches(it) }, // todo: vararg
        )

    private fun TypeMatchesPattern.evalTypeMatches(value: JIRValue): Boolean =
        typeMatchesCache.computeIfAbsent(this) {
            typeMatchesPattern(value, this)
        }

    private fun isConstant(value: JIRValue): Boolean {
        return value is JIRConstant
    }

    private fun isNull(value: JIRValue): Boolean {
        return value is JIRNullConstant
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

    private fun matches(value: JIRValue, pattern: Regex, matchArrayValue: Boolean): Boolean {
        if (value is JIRStringConstant) {
            return pattern.matches(value.value)
        }

        if (value is JIRLocalVar) {
            resolveLocalVarValue(value, matchArrayValue) { aliasInfo ->
                val constants = aliasInfo
                    .mapNotNull { ai -> ai.base.takeIf { ai.accessors.isEmpty() } }
                    .filterIsInstance<AccessPathBase.Constant>()

                if (constants.any { pattern.matches(it.value) }) {
                    return true
                }
            }
        }

        return false
    }

    private fun isStaticField(value: JIRValue, condition: IsStaticField, matchArrayValue: Boolean): Boolean {
        if (value is JIRFieldRef) {
            val field = value.field.field
            return staticFieldMatches(field.enclosingClass.name, field.name, condition)
        }

        if (value is JIRLocalVar) {
            resolveLocalVarValue(value, matchArrayValue) { aliasInfo ->
                val statics = aliasInfo
                    .filter { it.base is AccessPathBase.ClassStatic }
                    .mapNotNull { it.accessors.firstOrNull() }
                    .filterIsInstance<JIRLocalAliasAnalysis.AliasAccessor.Field>()

                if (statics.any { staticFieldMatches(it.className, it.fieldName, condition) }) {
                    return true
                }
            }
        }

        return false
    }

    private inline fun resolveLocalVarValue(lv: JIRLocalVar, matchArrayValue: Boolean, body: (List<AliasInfo>) -> Unit) {
        val aa = analysisContext.aliasAnalysis ?: return

        val base = AccessPathBase.LocalVar(lv.index)
        if (!matchArrayValue) {
            // todo: use must alias if negated
            val aliasInfo = aa.findAlias(base, statement)
            if (aliasInfo != null) {
                body(aliasInfo)
            }
        } else {
            val allAliases = aa.getAllAliasAtStatement(statement)
            for ((_, aliasSet) in allAliases) {
                for (info in aliasSet) {
                    if (info.base != base) continue
                    val singleAccessor = info.accessors.singleOrNull() ?: continue
                    if (singleAccessor is JIRLocalAliasAnalysis.AliasAccessor.Array) {
                        body(aliasSet)
                        return
                    }
                }
            }
        }
    }

    private fun staticFieldMatches(
        className: String,
        fieldName: String,
        condition: IsStaticField,
    ): Boolean = condition.className.match(className) && condition.fieldName.match(fieldName)

    private fun typeMatches(value: CommonValue, condition: TypeMatches): Boolean {
        check(value is JIRValue)
        return value.type.isAssignable(condition.type)
    }

    private fun typeMatchesPattern(value: JIRValue, condition: TypeMatchesPattern): Boolean {
        val type = value.type as? JIRRefType ?: return false

        val pattern = condition.pattern
        if (pattern.match(type.typeName)) return true

        if (pattern !is ConditionNameMatcher.Concrete) {
            // todo: check super classes?
            return false
        }

        if (negated) return false

        if (type.typeName == "java.lang.Object") {
            // todo: hack to avoid explosion
            return false
        }

        return typeChecker.typeMayHaveSubtypeOf(type.typeName, pattern.name)
    }

    private fun ConditionNameMatcher.match(name: String): Boolean = when (this) {
        is ConditionNameMatcher.PatternEndsWith -> name.endsWith(suffix)
        is ConditionNameMatcher.PatternStartsWith -> name.startsWith(prefix)
        is ConditionNameMatcher.Simple -> match(name)
    }

    private fun ConditionNameMatcher.Simple.match(name: String): Boolean = when (this) {
        is ConditionNameMatcher.Pattern -> pattern.containsMatchIn(name)
        is ConditionNameMatcher.Concrete -> this.name == name
        is ConditionNameMatcher.AnyName -> true
    }

    private fun Position.eval(
        none: Boolean = false,
        value: (value: JIRValue) -> Boolean,
        callVarArgValue: (value: JIRValue) -> Boolean,
    ): Boolean = when (val res = positionResolver.resolve(this)) {
        is CallPositionValue.None -> none
        is CallPositionValue.Value -> value(res.value)
        is CallPositionValue.VarArgValue -> callVarArgValue(res.value)
    }
}
