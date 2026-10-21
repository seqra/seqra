package org.opentaint.dataflow.configuration.python

import org.opentaint.dataflow.configuration.CommonCondition

typealias PIRCondition = CommonCondition<PythonRuleCondition>

sealed interface PythonRuleCondition {
    fun <R> accept(visitor: PIRConditionVisitor<R>): R
}

data class ContainsMark(val mark: TaintMark, val pos: Position) : PythonRuleCondition {
    override fun <R> accept(visitor: PIRConditionVisitor<R>): R = visitor.visit(this)
}

data class ContainsMarkOnAnyAccessor(val mark: TaintMark, val pos: Position) : PythonRuleCondition {
    override fun <R> accept(visitor: PIRConditionVisitor<R>): R = visitor.visit(this)
}

data class NumberOfArgs(val n: Int) : PythonRuleCondition {
    override fun <R> accept(visitor: PIRConditionVisitor<R>): R = visitor.visit(this)
}

data class ConstantCmp(val pos: Position, val value: ConstantValue, val cmp: ConstantCmpType) : PythonRuleCondition {
    override fun <R> accept(visitor: PIRConditionVisitor<R>): R = visitor.visit(this)
}

data class ConstantMatches(val pos: Position, val pattern: Regex) : PythonRuleCondition {
    override fun <R> accept(visitor: PIRConditionVisitor<R>): R = visitor.visit(this)
}

sealed interface ConstantValue
data class IntConstantValue(val value: Long) : ConstantValue
data class StrConstantValue(val value: String) : ConstantValue
data class BoolConstantValue(val value: Boolean) : ConstantValue

enum class ConstantCmpType { Eq, Lt, Gt }

interface PIRConditionVisitor<out R> {
    fun visit(c: ContainsMark): R
    fun visit(c: ContainsMarkOnAnyAccessor): R
    fun visit(c: NumberOfArgs): R
    fun visit(c: ConstantCmp): R
    fun visit(c: ConstantMatches): R
}
