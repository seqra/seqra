package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.proto.*

/**
 * Analyzes a nested function's body to determine which variable names are
 * "free" — referenced but not defined as parameters or local assignments.
 *
 * Free variables are closure captures: names that come from the enclosing scope.
 * This replaces the Python-side `_collect_free_vars` analysis.
 */
internal object FreeVarAnalyzer {

    private val BUILTIN_NAMES = setOf(
        "True", "False", "None", "print", "len", "range", "int", "str",
        "float", "bool", "list", "dict", "set", "tuple", "type", "super",
        "isinstance", "issubclass", "hasattr", "getattr", "setattr",
        "delattr", "callable", "iter", "next", "enumerate", "zip", "map",
        "filter", "sorted", "reversed", "sum", "min", "max", "abs", "all",
        "any", "repr", "hash", "id", "input", "open", "chr", "ord",
        "hex", "oct", "bin", "format", "object", "property", "staticmethod",
        "classmethod", "ValueError", "TypeError", "RuntimeError",
        "KeyError", "IndexError", "AttributeError", "StopIteration",
        "Exception", "BaseException", "NotImplementedError", "AssertionError",
        "OSError", "IOError", "FileNotFoundError", "PermissionError",
    )

    /**
     * Collect free variable names from a function body, plus any names declared
     * `nonlocal` (which are always closure captures regardless of local writes).
     */
    fun collectFreeVars(body: MypyBlockProto, params: List<String>): List<String> {
        val state = ScanState(paramNames = params.toSet())
        scanBlock(body, state)

        val free = state.referenced - state.paramNames - state.localDefs - BUILTIN_NAMES
        return (free + state.nonlocalNames).sorted()
    }

    private class ScanState(
        val paramNames: Set<String>,
        val localDefs: MutableSet<String> = mutableSetOf(),
        val referenced: MutableSet<String> = mutableSetOf(),
        val nonlocalNames: MutableSet<String> = mutableSetOf(),
    )

    private fun scanBlock(block: MypyBlockProto, s: ScanState) {
        for (stmt in block.stmtsList) scanStmt(stmt, s)
    }

    private fun scanStmt(stmt: MypyStmtProto, s: ScanState) {
        when {
            stmt.hasAssignment() -> {
                for (lv in stmt.assignment.lvaluesList) collectAssignTargets(lv, s.localDefs)
                scanExpr(stmt.assignment.rvalue, s.referenced)
            }
            stmt.hasOpAssignment() -> {
                collectAssignTargets(stmt.opAssignment.lvalue, s.localDefs)
                scanExpr(stmt.opAssignment.rvalue, s.referenced)
            }
            stmt.hasReturnStmt() -> {
                if (stmt.returnStmt.hasExpr()) scanExpr(stmt.returnStmt.expr, s.referenced)
            }
            stmt.hasExpressionStmt() -> {
                if (stmt.expressionStmt.hasExpr()) scanExpr(stmt.expressionStmt.expr, s.referenced)
            }
            stmt.hasIfStmt() -> {
                for (cond in stmt.ifStmt.conditionsList) scanExpr(cond, s.referenced)
                for (body in stmt.ifStmt.bodiesList) scanBlock(body, s)
                if (stmt.ifStmt.hasElseBody()) scanBlock(stmt.ifStmt.elseBody, s)
            }
            stmt.hasWhileStmt() -> {
                scanExpr(stmt.whileStmt.condition, s.referenced)
                scanBlock(stmt.whileStmt.body, s)
                if (stmt.whileStmt.hasElseBody()) scanBlock(stmt.whileStmt.elseBody, s)
            }
            stmt.hasForStmt() -> {
                collectAssignTargets(stmt.forStmt.index, s.localDefs)
                scanExpr(stmt.forStmt.iterable, s.referenced)
                scanBlock(stmt.forStmt.body, s)
                if (stmt.forStmt.hasElseBody()) scanBlock(stmt.forStmt.elseBody, s)
            }
            stmt.hasWithStmt() -> {
                for (expr in stmt.withStmt.exprsList) scanExpr(expr, s.referenced)
                for (target in stmt.withStmt.targetsList) collectAssignTargets(target, s.localDefs)
                scanBlock(stmt.withStmt.body, s)
            }
            stmt.hasTryStmt() -> {
                scanBlock(stmt.tryStmt.body, s)
                for (handler in stmt.tryStmt.handlersList) scanBlock(handler, s)
                if (stmt.tryStmt.hasElseBody()) scanBlock(stmt.tryStmt.elseBody, s)
                if (stmt.tryStmt.hasFinallyBody()) scanBlock(stmt.tryStmt.finallyBody, s)
                for (v in stmt.tryStmt.varsList) {
                    if (v.hasNameExpr()) s.localDefs.add(v.nameExpr.name)
                }
            }
            stmt.hasRaiseStmt() -> {
                if (stmt.raiseStmt.hasExpr()) scanExpr(stmt.raiseStmt.expr, s.referenced)
                if (stmt.raiseStmt.hasFromExpr()) scanExpr(stmt.raiseStmt.fromExpr, s.referenced)
            }
            stmt.hasDelStmt() -> {
                if (stmt.delStmt.hasExpr()) scanExpr(stmt.delStmt.expr, s.referenced)
            }
            stmt.hasAssertStmt() -> {
                scanExpr(stmt.assertStmt.expr, s.referenced)
                if (stmt.assertStmt.hasMsg()) scanExpr(stmt.assertStmt.msg, s.referenced)
            }
            stmt.hasGlobalDecl() -> { /* global names are not free vars */ }
            stmt.hasNonlocalDecl() -> s.nonlocalNames.addAll(stmt.nonlocalDecl.namesList)
            stmt.hasFuncDef() -> { /* nested function — separate scope */ }
            stmt.hasClassDef() -> { /* nested class — separate scope */ }
            // PassStmt, BreakStmt, ContinueStmt — no-op
        }
    }

    private fun scanExpr(expr: MypyExprProto, referenced: MutableSet<String>) {
        if (expr.kindCase == MypyExprProto.KindCase.KIND_NOT_SET) return
        when (expr.kindCase) {
            MypyExprProto.KindCase.NAME_EXPR -> referenced.add(expr.nameExpr.name)
            MypyExprProto.KindCase.MEMBER_EXPR -> scanExpr(expr.memberExpr.expr, referenced)
            MypyExprProto.KindCase.OP_EXPR -> {
                scanExpr(expr.opExpr.left, referenced)
                scanExpr(expr.opExpr.right, referenced)
            }
            MypyExprProto.KindCase.COMPARISON_EXPR -> {
                for (op in expr.comparisonExpr.operandsList) scanExpr(op, referenced)
            }
            MypyExprProto.KindCase.UNARY_EXPR -> scanExpr(expr.unaryExpr.expr, referenced)
            MypyExprProto.KindCase.CALL_EXPR -> {
                scanExpr(expr.callExpr.callee, referenced)
                for (arg in expr.callExpr.argsList) scanExpr(arg.expr, referenced)
            }
            MypyExprProto.KindCase.INDEX_EXPR -> {
                scanExpr(expr.indexExpr.base, referenced)
                scanExpr(expr.indexExpr.index, referenced)
            }
            MypyExprProto.KindCase.LIST_EXPR -> expr.listExpr.itemsList.forEach { scanExpr(it, referenced) }
            MypyExprProto.KindCase.TUPLE_EXPR -> expr.tupleExpr.itemsList.forEach { scanExpr(it, referenced) }
            MypyExprProto.KindCase.SET_EXPR -> expr.setExpr.itemsList.forEach { scanExpr(it, referenced) }
            MypyExprProto.KindCase.DICT_EXPR -> {
                expr.dictExpr.keysList.forEach { scanExpr(it, referenced) }
                expr.dictExpr.valuesList.forEach { scanExpr(it, referenced) }
            }
            MypyExprProto.KindCase.CONDITIONAL_EXPR -> {
                scanExpr(expr.conditionalExpr.cond, referenced)
                scanExpr(expr.conditionalExpr.ifExpr, referenced)
                scanExpr(expr.conditionalExpr.elseExpr, referenced)
            }
            MypyExprProto.KindCase.STAR_EXPR -> scanExpr(expr.starExpr.expr, referenced)
            MypyExprProto.KindCase.ASSIGNMENT_EXPR -> {
                scanExpr(expr.assignmentExpr.target, referenced)
                scanExpr(expr.assignmentExpr.value, referenced)
            }
            MypyExprProto.KindCase.SLICE_EXPR -> {
                if (expr.sliceExpr.hasBegin()) scanExpr(expr.sliceExpr.begin, referenced)
                if (expr.sliceExpr.hasEnd()) scanExpr(expr.sliceExpr.end, referenced)
                if (expr.sliceExpr.hasStride()) scanExpr(expr.sliceExpr.stride, referenced)
            }
            MypyExprProto.KindCase.YIELD_EXPR -> if (expr.yieldExpr.hasExpr()) scanExpr(expr.yieldExpr.expr, referenced)
            MypyExprProto.KindCase.YIELD_FROM_EXPR -> scanExpr(expr.yieldFromExpr.expr, referenced)
            MypyExprProto.KindCase.AWAIT_EXPR -> scanExpr(expr.awaitExpr.expr, referenced)
            MypyExprProto.KindCase.LAMBDA_EXPR,
            MypyExprProto.KindCase.LIST_COMPREHENSION,
            MypyExprProto.KindCase.SET_COMPREHENSION,
            MypyExprProto.KindCase.DICT_COMPREHENSION,
            MypyExprProto.KindCase.GENERATOR_EXPR -> { /* introduce their own scope */ }
            MypyExprProto.KindCase.SUPER_EXPR -> { /* super() has no free vars */ }
            else -> {} // literals & constants
        }
    }

    private fun collectAssignTargets(expr: MypyExprProto, localDefs: MutableSet<String>) {
        when {
            expr.hasNameExpr() -> localDefs.add(expr.nameExpr.name)
            expr.hasTupleExpr() -> expr.tupleExpr.itemsList.forEach { collectAssignTargets(it, localDefs) }
            expr.hasListExpr() -> expr.listExpr.itemsList.forEach { collectAssignTargets(it, localDefs) }
            expr.hasStarExpr() -> collectAssignTargets(expr.starExpr.expr, localDefs)
            // MemberExpr, IndexExpr — not local variable definitions
        }
    }
}
