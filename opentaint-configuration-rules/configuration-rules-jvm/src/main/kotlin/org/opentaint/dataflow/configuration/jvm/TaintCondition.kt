package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.JIRType
import java.util.Objects

interface ConditionVisitor<out R> {
    fun visit(condition: ConstantTrue): R
    fun visit(condition: Not): R
    fun visit(condition: And): R
    fun visit(condition: Or): R
    fun visit(condition: IsConstant): R
    fun visit(condition: IsNull): R
    fun visit(condition: ConstantEq): R
    fun visit(condition: ConstantLt): R
    fun visit(condition: ConstantGt): R
    fun visit(condition: ConstantMatches): R
    fun visit(condition: ContainsMark): R
    fun visit(condition: TypeMatches): R
    fun visit(condition: TypeMatchesPattern): R
    fun visit(condition: IsStaticField): R
}

interface Condition {
    fun <R> accept(conditionVisitor: ConditionVisitor<R>): R
}

data object ConstantTrue : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
    override fun toString(): String = javaClass.simpleName
}

data class Not(
    val arg: Condition,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class And(
    val args: List<Condition>,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class Or(
    val args: List<Condition>,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class IsConstant(
    val position: Position,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class IsNull(
    val position: Position,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantEq(
    val position: Position,
    val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantLt(
    val position: Position,
    val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantGt(
    val position: Position,
    val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantMatches(
    val position: Position,
    val pattern: Regex,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Suppress("EqualsOrHashCode")
data class ContainsMark(
    val position: Position,
    val mark: TaintMark,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)

    private val hash = Objects.hash(position, mark)
    override fun hashCode(): Int = hash
}

data class TypeMatches(
    val position: Position,
    val type: JIRType,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

sealed interface ConditionNameMatcher {
    sealed interface Simple : ConditionNameMatcher

    data object AnyName: Simple
    data class Concrete(val name: String) : Simple
    data class Pattern(val pattern: Regex) : Simple

    data class PatternEndsWith(val suffix: String) : ConditionNameMatcher
    data class PatternStartsWith(val prefix: String) : ConditionNameMatcher
}

data class TypeMatchesPattern(
    val position: Position,
    val pattern: ConditionNameMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

sealed interface ConstantValue

data class ConstantIntValue(val value: Int) : ConstantValue

data class ConstantBooleanValue(val value: Boolean) : ConstantValue

data class ConstantStringValue(val value: String) : ConstantValue

data class IsStaticField(
    val position: Position,
    val className: ConditionNameMatcher,
    val fieldName: ConditionNameMatcher.Simple,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}
