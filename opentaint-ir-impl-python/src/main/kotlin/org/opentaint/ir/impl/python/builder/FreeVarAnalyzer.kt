package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.impl.python.proto.*

/**
 * Analyzes a nested function's body to determine which variable names are
 * "free" — referenced but not defined as parameters or local assignments.
 *
 * Free variables are closure captures: names that come from the enclosing scope.
 * This replaces the Python-side `_collect_free_vars` analysis.
 */
object FreeVarAnalyzer {

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
     * Collect free variable names from a function body.
     *
     * @param body The function's body block
     * @param params The function's parameter names
     * @return Sorted list of free variable names (captured from enclosing scope)
     */
    fun collectFreeVars(body: MypyBlockProto, params: List<String>): List<String> {
        val paramNames = params.toSet()
        val localDefs = mutableSetOf<String>()
        val referenced = mutableSetOf<String>()
        val nonlocalNames = mutableSetOf<String>()

        scanBlock(body, localDefs, referenced, nonlocalNames)

        val free = referenced - paramNames - localDefs - BUILTIN_NAMES
        return (free + nonlocalNames).sorted()
    }

    private fun scanBlock(block: MypyBlockProto, localDefs: MutableSet<String>,
                          referenced: MutableSet<String>, nonlocalNames: MutableSet<String>) {
        for (stmt in block.stmtsList) {
            scanStmt(stmt, localDefs, referenced, nonlocalNames)
        }
    }

    private fun scanStmt(stmt: MypyStmtProto, localDefs: MutableSet<String>,
                         referenced: MutableSet<String>, nonlocalNames: MutableSet<String>) {
        when {
            stmt.hasAssignment() -> {
                for (lv in stmt.assignment.lvaluesList) {
                    collectAssignTargets(lv, localDefs)
                }
                scanExpr(stmt.assignment.rvalue, referenced)
            }
            stmt.hasOpAssignment() -> {
                collectAssignTargets(stmt.opAssignment.lvalue, localDefs)
                scanExpr(stmt.opAssignment.rvalue, referenced)
            }
            stmt.hasReturnStmt() -> {
                if (stmt.returnStmt.hasExpr()) scanExpr(stmt.returnStmt.expr, referenced)
            }
            stmt.hasExpressionStmt() -> {
                if (stmt.expressionStmt.hasExpr()) scanExpr(stmt.expressionStmt.expr, referenced)
            }
            stmt.hasIfStmt() -> {
                for (cond in stmt.ifStmt.conditionsList) scanExpr(cond, referenced)
                for (body in stmt.ifStmt.bodiesList) scanBlock(body, localDefs, referenced, nonlocalNames)
                if (stmt.ifStmt.hasElseBody()) scanBlock(stmt.ifStmt.elseBody, localDefs, referenced, nonlocalNames)
            }
            stmt.hasWhileStmt() -> {
                scanExpr(stmt.whileStmt.condition, referenced)
                scanBlock(stmt.whileStmt.body, localDefs, referenced, nonlocalNames)
                if (stmt.whileStmt.hasElseBody()) scanBlock(stmt.whileStmt.elseBody, localDefs, referenced, nonlocalNames)
            }
            stmt.hasForStmt() -> {
                collectAssignTargets(stmt.forStmt.index, localDefs)
                scanExpr(stmt.forStmt.iterable, referenced)
                scanBlock(stmt.forStmt.body, localDefs, referenced, nonlocalNames)
                if (stmt.forStmt.hasElseBody()) scanBlock(stmt.forStmt.elseBody, localDefs, referenced, nonlocalNames)
            }
            stmt.hasWithStmt() -> {
                for (expr in stmt.withStmt.exprsList) scanExpr(expr, referenced)
                for (target in stmt.withStmt.targetsList) collectAssignTargets(target, localDefs)
                scanBlock(stmt.withStmt.body, localDefs, referenced, nonlocalNames)
            }
            stmt.hasTryStmt() -> {
                scanBlock(stmt.tryStmt.body, localDefs, referenced, nonlocalNames)
                for (handler in stmt.tryStmt.handlersList) scanBlock(handler, localDefs, referenced, nonlocalNames)
                if (stmt.tryStmt.hasElseBody()) scanBlock(stmt.tryStmt.elseBody, localDefs, referenced, nonlocalNames)
                if (stmt.tryStmt.hasFinallyBody()) scanBlock(stmt.tryStmt.finallyBody, localDefs, referenced, nonlocalNames)
                for (v in stmt.tryStmt.varsList) {
                    if (v.hasNameExpr()) localDefs.add(v.nameExpr.name)
                }
            }
            stmt.hasRaiseStmt() -> {
                if (stmt.raiseStmt.hasExpr()) scanExpr(stmt.raiseStmt.expr, referenced)
                if (stmt.raiseStmt.hasFromExpr()) scanExpr(stmt.raiseStmt.fromExpr, referenced)
            }
            stmt.hasDelStmt() -> {
                if (stmt.delStmt.hasExpr()) scanExpr(stmt.delStmt.expr, referenced)
            }
            stmt.hasAssertStmt() -> {
                scanExpr(stmt.assertStmt.expr, referenced)
                if (stmt.assertStmt.hasMsg()) scanExpr(stmt.assertStmt.msg, referenced)
            }
            stmt.hasGlobalDecl() -> { /* global names are not free vars */ }
            stmt.hasNonlocalDecl() -> {
                nonlocalNames.addAll(stmt.nonlocalDecl.namesList)
            }
            stmt.hasFuncDef() -> { /* nested function — skip (handled separately) */ }
            stmt.hasClassDef() -> { /* nested class — skip */ }
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
            MypyExprProto.KindCase.LAMBDA_EXPR -> { /* lambda has own scope — skip */ }
            MypyExprProto.KindCase.LIST_COMPREHENSION,
            MypyExprProto.KindCase.SET_COMPREHENSION,
            MypyExprProto.KindCase.DICT_COMPREHENSION,
            MypyExprProto.KindCase.GENERATOR_EXPR -> { /* comprehensions have own scope — skip */ }
            MypyExprProto.KindCase.SUPER_EXPR -> { /* super() has no free vars */ }
            // Literals and constants have no references
            else -> {}
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
