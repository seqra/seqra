package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConditionVisitor
import org.opentaint.dataflow.configuration.jvm.ConstantEq
import org.opentaint.dataflow.configuration.jvm.ConstantGt
import org.opentaint.dataflow.configuration.jvm.ConstantLt
import org.opentaint.dataflow.configuration.jvm.ConstantMatches
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.IsConstant
import org.opentaint.dataflow.configuration.jvm.IsNull
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern

interface ConditionRewriter : ConditionVisitor<Condition> {
    override fun visit(condition: Not): Condition = Not(condition.arg.accept(this))
    override fun visit(condition: And): Condition = And(condition.args.map { it.accept(this) })
    override fun visit(condition: Or): Condition = Or(condition.args.map { it.accept(this) })

    override fun visit(condition: ConstantTrue): Condition = condition
    override fun visit(condition: IsConstant): Condition = condition
    override fun visit(condition: IsNull): Condition = condition
    override fun visit(condition: ConstantEq): Condition = condition
    override fun visit(condition: ConstantLt): Condition = condition
    override fun visit(condition: ConstantGt): Condition = condition
    override fun visit(condition: ConstantMatches): Condition = condition
    override fun visit(condition: ContainsMark): Condition = condition
    override fun visit(condition: TypeMatches): Condition = condition
    override fun visit(condition: TypeMatchesPattern): Condition = condition
}
