package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.And
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Literal
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Or
import org.opentaint.dataflow.util.cartesianProductMapTo

sealed interface JIRMarkAwareConditionExpr {
    class And(val args: Array<JIRMarkAwareConditionExpr>) : JIRMarkAwareConditionExpr {
        override fun toString(): String = "And(${args.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is And) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int = args.contentHashCode()
    }

    class Or(val args: Array<JIRMarkAwareConditionExpr>) : JIRMarkAwareConditionExpr {
        override fun toString(): String = "Or(${args.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Or) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int = args.contentHashCode()
    }

    data class Literal(val condition: ContainsMark, val negated: Boolean) : JIRMarkAwareConditionExpr
}

fun JIRMarkAwareConditionExpr.removeTrueLiterals(
    evalLiteral: (Literal) -> Boolean
): JIRMarkAwareConditionExpr? = removeTrueLiteralsFromExpr(this, evalLiteral)

private fun removeTrueLiteralsFromExpr(
    expr: JIRMarkAwareConditionExpr,
    evalLiteral: (Literal) -> Boolean
) = when (expr) {
    is Literal -> if (evalLiteral(expr)) null else expr
    is And -> removeTrueLiteralsFromAndExpr(expr, evalLiteral)
    is Or -> removeTrueLiteralsFromOrExpr(expr, evalLiteral)
}

private fun removeTrueLiteralsFromAndExpr(expr: And, evalLiteral: (Literal) -> Boolean): JIRMarkAwareConditionExpr? {
    return removeTrueLiteralsFromArray(
        expr.args, { removeTrueLiteralsFromExpr(it, evalLiteral) }, JIRMarkAwareConditionExpr::And,
        createDefault = { return null },
        processElement = { it }
    )
}

private fun removeTrueLiteralsFromOrExpr(expr: Or, evalLiteral: (Literal) -> Boolean): JIRMarkAwareConditionExpr? {
    return removeTrueLiteralsFromArray(
        expr.args, { removeTrueLiteralsFromExpr(it, evalLiteral) }, JIRMarkAwareConditionExpr::Or,
        createDefault = { error("impossible") },
        processElement = { it ?: return null }
    )
}

private inline fun removeTrueLiteralsFromArray(
    elements: Array<JIRMarkAwareConditionExpr>,
    removeTrueLiteralsFromExpr: (JIRMarkAwareConditionExpr) -> JIRMarkAwareConditionExpr?,
    create: (Array<JIRMarkAwareConditionExpr>) -> JIRMarkAwareConditionExpr,
    createDefault: () -> JIRMarkAwareConditionExpr?,
    processElement: (JIRMarkAwareConditionExpr?) -> JIRMarkAwareConditionExpr?,
): JIRMarkAwareConditionExpr? {
    val result = arrayOfNulls<JIRMarkAwareConditionExpr>(elements.size)
    var size = 0
    for (i in elements.indices) {
        val elementResult = removeTrueLiteralsFromExpr(elements[i])
        val elementExpr = processElement(elementResult) ?: continue
        result[size++] = elementExpr
    }

    if (size == 0) {
        return createDefault()
    }

    if (size == 1) {
        return result[0]
    }

    val resultExprs = result.copyOf(size)

    @Suppress("UNCHECKED_CAST")
    resultExprs as Array<JIRMarkAwareConditionExpr>

    return create(resultExprs)
}

data class JIRMarkAwareCube(val literals: Set<Literal>)

fun JIRMarkAwareConditionExpr.explodeToDNF(): List<JIRMarkAwareCube> = when (this) {
    is Literal -> listOf(JIRMarkAwareCube(setOf(this)))
    is Or -> args.flatMap { it.explodeToDNF() }
    is And -> {
        val result = mutableListOf<JIRMarkAwareCube>()
        val cubeLists = args.map { it.explodeToDNF() }
        cubeLists.cartesianProductMapTo { cubes ->
            val literals = hashSetOf<Literal>()
            cubes.flatMapTo(literals) { it.literals }
            result += JIRMarkAwareCube(literals)
        }
        result
    }
}
