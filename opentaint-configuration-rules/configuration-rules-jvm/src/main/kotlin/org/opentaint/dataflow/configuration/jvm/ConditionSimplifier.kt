package org.opentaint.dataflow.configuration.jvm

object ConditionSimplifier : ConditionVisitor<Condition> {
    override fun visit(condition: And): Condition {
        val queue = ArrayDeque(condition.args)
        val args = mutableListOf<Condition>()
        while (queue.isNotEmpty()) {
            val it = queue.removeFirst().accept(this)
            if (it is ConstantTrue) {
                // skip
            } else if (it.isFalse()) {
                return mkFalse()
            } else if (it is And) {
                queue += it.args
            } else {
                args += it
            }
        }
        return mkAnd(args)
    }

    override fun visit(condition: Or): Condition {
        val queue = ArrayDeque(condition.args)
        val args = mutableListOf<Condition>()
        while (queue.isNotEmpty()) {
            val it = queue.removeFirst().accept(this)
            if (it is ConstantTrue) {
                return mkTrue()
            } else if (it.isFalse()) {
                // skip
            } else if (it is Or) {
                queue += it.args
            } else {
                args += it
            }
        }
        return mkOr(args)
    }

    override fun visit(condition: Not): Condition {
        val arg = condition.arg.accept(this)
        return if (arg is Not) {
            // Eliminate double negation:
            arg.arg
        } else {
            Not(arg)
        }
    }

    override fun visit(condition: IsConstant): Condition = condition
    override fun visit(condition: IsNull): Condition = condition
    override fun visit(condition: ConstantEq): Condition = condition
    override fun visit(condition: ConstantLt): Condition = condition
    override fun visit(condition: ConstantGt): Condition = condition
    override fun visit(condition: ConstantMatches): Condition = condition
    override fun visit(condition: ContainsMark): Condition = condition
    override fun visit(condition: ConstantTrue): Condition = condition
    override fun visit(condition: TypeMatches): Condition = condition
    override fun visit(condition: TypeMatchesPattern): Condition = condition
    override fun visit(condition: IsStaticField): Condition = condition
}

fun mkTrue(): Condition = ConstantTrue
fun mkFalse(): Condition = Not(ConstantTrue)

fun mkOr(conditions: List<Condition>) = when (conditions.size) {
    0 -> mkFalse()
    1 -> conditions.single()
    else -> Or(conditions)
}

fun mkAnd(conditions: List<Condition>) = when (conditions.size) {
    0 -> mkTrue()
    1 -> conditions.single()
    else -> And(conditions)
}

fun Condition.simplify(): Condition =
    accept(ConditionSimplifier).toNnf(negated = false)

fun Condition.toNnf(negated: Boolean): Condition = when (this) {
    is Not -> arg.toNnf(!negated)

    is And -> if (!negated) {
        mkAndCondition(args) { it.toNnf(negated = false) }
    } else {
        mkOrCondition(args) { it.toNnf(negated = true) }
    }

    is Or -> if (!negated) {
        mkOrCondition(args) { it.toNnf(negated = false) }
    } else {
        mkAndCondition(args) { it.toNnf(negated = true) }
    }

    else -> if (negated) Not(this) else this
}

inline fun mkOrCondition(
    args: List<Condition>,
    op: (Condition) -> Condition
): Or {
    val result = mutableListOf<Condition>()
    for (arg in args) {
        val mappedArg = op(arg)
        if (mappedArg is Or) {
            result.addAll(mappedArg.args)
        } else {
            result.add(mappedArg)
        }
    }
    return Or(result)
}

inline fun mkAndCondition(
    args: List<Condition>,
    op: (Condition) -> Condition
): And {
    val result = mutableListOf<Condition>()
    for (arg in args) {
        val mappedArg = op(arg)
        if (mappedArg is And) {
            result.addAll(mappedArg.args)
        } else {
            result.add(mappedArg)
        }
    }
    return And(result)
}

fun Condition.isFalse() = this is Not && this.arg is ConstantTrue
