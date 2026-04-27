package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.FlatDecorator
import org.opentaint.ir.impl.python.proto.MypyDecoratorDefProto
import org.opentaint.ir.impl.python.proto.MypyExprProto
import org.opentaint.ir.impl.python.proto.MypyFuncDefProto
import org.opentaint.ir.impl.python.proto.MypyMemberExprProto

/**
 * Stateless lowering of decorator nodes into [FlatDecorator]. Two source
 * shapes feed in:
 *   - Bare `MypyFuncDefProto.decoratorsList`  — already-summarized decorators.
 *   - `MypyDecoratorDefProto.originalDecoratorsList` — raw expression nodes
 *     that we re-summarize via [fromExpr].
 */
internal object DecoratorLowering {

    fun fromFuncDef(funcDef: MypyFuncDefProto): List<FlatDecorator> =
        funcDef.decoratorsList.map { FlatDecorator(it.name, it.qualifiedName, it.argumentsList) }

    fun fromDecoratorDef(decorator: MypyDecoratorDefProto): List<FlatDecorator> =
        decorator.originalDecoratorsList.map { fromExpr(it) }

    fun fromExpr(expr: MypyExprProto): FlatDecorator = when {
        expr.hasNameExpr() -> {
            val ne = expr.nameExpr
            FlatDecorator(
                name = ne.name,
                qualifiedName = ne.fullname.ifEmpty { ne.name },
                arguments = emptyList(),
            )
        }
        expr.hasMemberExpr() -> {
            val me = expr.memberExpr
            FlatDecorator(
                name = me.name,
                qualifiedName = me.fullname.ifEmpty { dottedPath(me) },
                arguments = emptyList(),
            )
        }
        expr.hasCallExpr() -> {
            val callee = fromExpr(expr.callExpr.callee)
            FlatDecorator(
                name = callee.name,
                qualifiedName = callee.qualifiedName,
                arguments = expr.callExpr.argsList.map { exprRepr(it.expr) },
            )
        }
        else -> FlatDecorator("<unknown>", "<unknown>", emptyList())
    }

    private fun dottedPath(me: MypyMemberExprProto): String {
        val prefix = when {
            me.expr.hasNameExpr() -> me.expr.nameExpr.fullname.ifEmpty { me.expr.nameExpr.name }
            me.expr.hasMemberExpr() -> dottedPath(me.expr.memberExpr)
            else -> "<expr>"
        }
        return "$prefix.${me.name}"
    }

    /**
     * Render a literal-ish argument expression as a printable string — mypy's
     * decorator metadata stores arguments as raw text, not values.
     */
    private fun exprRepr(expr: MypyExprProto): String = when {
        expr.hasIntExpr() -> expr.intExpr.value.toString()
        expr.hasStrExpr() -> "\"${escape(expr.strExpr.value)}\""
        expr.hasFloatExpr() -> expr.floatExpr.value.toString()
        expr.hasBytesExpr() -> "b\"${escape(expr.bytesExpr.value.toStringUtf8())}\""
        expr.hasComplexExpr() -> "${expr.complexExpr.real}+${expr.complexExpr.imag}j"
        expr.hasEllipsisExpr() -> "..."
        // True / False / None are NameExprs in mypy's AST; render verbatim.
        expr.hasNameExpr() -> expr.nameExpr.name
        expr.hasMemberExpr() -> dottedPath(expr.memberExpr)
        else -> "<expr>"
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
