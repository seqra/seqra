package org.opentaint.ir.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opentaint.ir.api.JIRType

interface ConditionVisitor<R> {
    fun visit(condition: And): R
    fun visit(condition: Or): R
    fun visit(condition: Not): R
    fun visit(condition: IsConstant): R
    fun visit(condition: IsType): R
    fun visit(condition: AnnotationType): R
    fun visit(condition: ConstantEq): R
    fun visit(condition: ConstantLt): R
    fun visit(condition: ConstantGt): R
    fun visit(condition: ConstantMatches): R
    fun visit(condition: SourceFunctionMatches): R
    fun visit(condition: CallParameterContainsMark): R
    fun visit(condition: ConstantTrue): R

    // extra conditions
    fun visit(condition: TypeMatches): R
}

interface Condition {
    fun <R> accept(conditionVisitor: ConditionVisitor<R>): R
}

@Serializable
@SerialName("And")
data class And(@SerialName("args") val args: List<Condition>) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("Or")
data class Or(@SerialName("args") val args: List<Condition>) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("Not")
data class Not(val condition: Condition) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("IsConstant")
data class IsConstant(val position: Position) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("IsType")
data class IsType(
    @SerialName("position") val position: Position,
    @SerialName("type") val typeMatcher: TypeMatcher
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("AnnotationType")
data class AnnotationType(
    @SerialName("position") val position: Position,
    @SerialName("type") val typeMatcher: TypeMatcher
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantEq")
data class ConstantEq(
    @SerialName("position") val position: Position,
    @SerialName("constant") val value: ConstantValue
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantLt")
data class ConstantLt(
    @SerialName("position") val position: Position,
    @SerialName("constant") val value: ConstantValue
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantGt")
data class ConstantGt(
    @SerialName("position") val position: Position,
    @SerialName("constant") val value: ConstantValue
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantMatches")
data class ConstantMatches(
    @SerialName("position") val position: Position,
    @SerialName("pattern") val pattern: String
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("SourceFunctionMatches")
data class SourceFunctionMatches(
    @SerialName("position") val position: Position,
    @SerialName("sourceFunction") val functionMatcher: FunctionMatcher
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

// sink label
@Serializable
@SerialName("ContainsMark")
data class CallParameterContainsMark(
    @SerialName("position") val position: Position,
    @SerialName("mark") val mark: TaintMark
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantTrue")
object ConstantTrue : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

// Extra conditions
@Serializable
@SerialName("TypeMatches")
data class TypeMatches(val position: Position, @SerialName("type") val type: JIRType) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
sealed interface ConstantValue

@Serializable
@SerialName("IntValue")
data class ConstantIntValue(val value: Int) : ConstantValue

@Serializable
@SerialName("BoolValue")
data class ConstantBooleanValue(val value: Boolean) : ConstantValue

@Serializable
@SerialName("StringValue")
data class ConstantStringValue(val value: String) : ConstantValue

@Serializable
sealed interface NameMatcher

@Serializable
@SerialName("NameMatches")
data class NamePatternMatcher(val pattern: String) : NameMatcher

@Serializable
@SerialName("NameIsEqualTo")
data class NameExactMatcher(val name: String) : NameMatcher

@Serializable
@SerialName("AnyNameMatches")
object AnyNameMatcher : NameMatcher

@Serializable
sealed interface TypeMatcher

@Serializable
@SerialName("ClassMatcher")
data class ClassMatcher(
    @SerialName("packageMatcher") val pkg: NameMatcher,
    @SerialName("classNameMatcher") val classNameMatcher: NameMatcher
) : TypeMatcher

@Serializable
@SerialName("PrimitiveNameMatches")
data class PrimitiveNameMatcher(val name: String) : TypeMatcher

@Serializable
@SerialName("AnyTypeMatches")
object AnyTypeMatcher : TypeMatcher

@Serializable
@SerialName("FunctionMatches")
data class FunctionMatcher(
    val cls: ClassMatcher,
    val functionName: NameMatcher,
    val parametersMatchers: List<ParameterMatcher>,
    val returnTypeMatcher: TypeMatcher,
    val applyToOverrides: Boolean,
    val functionLabel: String?,
    val modifier: Int,
    val exclude: List<FunctionMatcher>
)

@Serializable
@SerialName("ParameterMatches")
data class ParameterMatcher(val index: Int, val typeMatcher: TypeMatcher)
