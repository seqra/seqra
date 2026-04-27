package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.DecoratorLowering
import org.opentaint.ir.impl.python.protoToFlat.FunctionLowering
import org.opentaint.ir.impl.python.proto.*

/**
 * Statement-level lowering. Extension functions on [CfgSession] — no class
 * holds a reference to another, all state flows through the receiver.
 *
 * Mirrors the structure of mypy's `statement_visitor.py`.
 */

internal fun CfgSession.visitBlock(block: MypyBlockProto) {
    for (stmt in block.stmtsList) {
        if (currentBlockTerminated()) break
        visitStmt(stmt)
    }
}

private fun CfgSession.visitStmt(stmt: MypyStmtProto) {
    when {
        stmt.hasAssignment() -> visitAssignment(stmt.assignment, stmt.line)
        stmt.hasOpAssignment() -> visitOperatorAssignment(stmt.opAssignment, stmt.line)
        stmt.hasExpressionStmt() -> visitExpressionStmt(stmt.expressionStmt)
        stmt.hasReturnStmt() -> visitReturn(stmt.returnStmt, stmt.line)
        stmt.hasIfStmt() -> visitIf(stmt.ifStmt, stmt.line)
        stmt.hasWhileStmt() -> visitWhile(stmt.whileStmt, stmt.line)
        stmt.hasForStmt() -> visitFor(stmt.forStmt, stmt.line)
        stmt.hasTryStmt() -> visitTry(stmt.tryStmt, stmt.line)
        stmt.hasWithStmt() -> visitWith(stmt.withStmt, stmt.line)
        stmt.hasRaiseStmt() -> visitRaise(stmt.raiseStmt, stmt.line)
        stmt.hasBreakStmt() -> breakTarget?.let { emitGoto(it) }
        stmt.hasContinueStmt() -> continueTarget?.let { emitGoto(it) }
        stmt.hasDelStmt() -> visitDel(stmt.delStmt, stmt.line)
        stmt.hasAssertStmt() -> visitAssert(stmt.assertStmt, stmt.line)
        stmt.hasFuncDef() -> visitNestedFuncDef(stmt.funcDef, emptyList(), stmt.line)
        stmt.hasDecorator() ->
            visitNestedFuncDef(stmt.decorator.func, stmt.decorator.originalDecoratorsList, stmt.line)
        // PassStmt, GlobalDecl, NonlocalDecl, ClassDef inside body: no-op
    }
}

// ─── Assignment ────────────────────────────────────────

internal fun CfgSession.visitAssignment(stmt: MypyAssignmentStmtProto, line: Int) {
    val rhs = lowerExpr(stmt.rvalue)
    for (lvalue in stmt.lvaluesList) {
        assignTo(lvalue, rhs, line)
    }
}

internal fun CfgSession.assignTo(lvalue: MypyExprProto, rhs: FlatValue, line: Int) {
    when {
        lvalue.hasNameExpr() -> {
            val targetName = scope.resolveLocal(lvalue.nameExpr.name)
            emit(FlatAssign(FlatLocal(targetName), rhs, line = line))
        }
        lvalue.hasMemberExpr() -> {
            val obj = lowerExpr(lvalue.memberExpr.expr)
            emit(FlatStoreAttr(obj, lvalue.memberExpr.name, rhs, line = line))
        }
        lvalue.hasIndexExpr() -> {
            val obj = lowerExpr(lvalue.indexExpr.base)
            val index = lowerExpr(lvalue.indexExpr.index)
            emit(FlatStoreSubscript(obj, index, rhs, line = line))
        }
        lvalue.hasTupleExpr() -> {
            val targets = mutableListOf<FlatValue>()
            var starIndex = -1
            for ((i, item) in lvalue.tupleExpr.itemsList.withIndex()) {
                when {
                    item.hasNameExpr() -> targets.add(FlatLocal(scope.resolveLocal(item.nameExpr.name)))
                    item.hasStarExpr() && item.starExpr.expr.hasNameExpr() -> {
                        targets.add(FlatLocal(scope.resolveLocal(item.starExpr.expr.nameExpr.name)))
                        starIndex = i
                    }
                    else -> targets.add(newTempValue())
                }
                if (item.hasStarExpr() && starIndex == -1) starIndex = i
            }
            emit(FlatUnpack(targets, rhs, starIndex, line = line))
        }
        lvalue.hasStarExpr() -> assignTo(lvalue.starExpr.expr, rhs, line)
    }
}

private fun CfgSession.visitOperatorAssignment(stmt: MypyOperatorAssignmentStmtProto, line: Int) {
    val lhsVal = lowerExpr(stmt.lvalue)
    val rhsVal = lowerExpr(stmt.rvalue)
    val target = newTempValue()
    val op = BIN_OP_MAP[stmt.op] ?: FlatBinaryOperator.ADD
    emit(FlatBinOp(target, lhsVal, rhsVal, op, line = line))
    assignTo(stmt.lvalue, target, line)
}

// ─── Expression statement ────────────────────────────

private fun CfgSession.visitExpressionStmt(stmt: MypyExpressionStmtProto) {
    lowerExpr(stmt.expr)
}

// ─── Return ────────────────────────────────────────────

private fun CfgSession.visitReturn(stmt: MypyReturnStmtProto, line: Int) {
    val value = if (stmt.hasExpr() && stmt.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(stmt.expr)
    } else null
    emitReturn(value, line)
}

// ─── If ────────────────────────────────────────────────

private fun CfgSession.visitIf(stmt: MypyIfStmtProto, line: Int) {
    val endBlock = newBlock()

    for (i in stmt.conditionsList.indices) {
        val condVal = lowerExpr(stmt.getConditions(i))
        val trueBlock = newBlock()
        val falseBlock = if (i < stmt.conditionsCount - 1 || stmt.hasElseBody()) newBlock() else endBlock

        emitBranch(condVal, trueBlock, falseBlock, line)

        activate(trueBlock)
        visitBlock(stmt.getBodies(i))
        if (!currentBlockTerminated()) emitGoto(endBlock)

        if (falseBlock != endBlock) activate(falseBlock)
    }

    if (stmt.hasElseBody()) {
        visitBlock(stmt.elseBody)
        if (!currentBlockTerminated()) emitGoto(endBlock)
    }

    activate(endBlock)
}

// ─── While ─────────────────────────────────────────────

private fun CfgSession.visitWhile(stmt: MypyWhileStmtProto, line: Int) {
    val headerBlock = newBlock()
    val bodyBlock = newBlock()
    val hasElseBody = stmt.hasElseBody() && stmt.elseBody.stmtsCount > 0

    val elseBlock: Int?
    val exitBlock: Int
    val breakBlock: Int
    if (hasElseBody) {
        elseBlock = newBlock()
        breakBlock = newBlock()
        exitBlock = elseBlock
    } else {
        elseBlock = null
        exitBlock = newBlock()
        breakBlock = exitBlock
    }

    emitGoto(headerBlock)
    activate(headerBlock)

    val cond = lowerExpr(stmt.condition)
    emitBranch(cond, bodyBlock, exitBlock, line)

    activate(bodyBlock)
    withLoopTargets(breakBlock = breakBlock, continueBlock = headerBlock) {
        visitBlock(stmt.body)
    }
    if (!currentBlockTerminated()) emitGoto(headerBlock)

    if (elseBlock != null) {
        activate(elseBlock)
        visitBlock(stmt.elseBody)
        if (!currentBlockTerminated()) emitGoto(breakBlock)
        activate(breakBlock)
    } else {
        activate(exitBlock)
    }
}

// ─── For ───────────────────────────────────────────────

private fun CfgSession.visitFor(stmt: MypyForStmtProto, line: Int) {
    val iterVal = newTempValue()
    val iterableVal = lowerExpr(stmt.iterable)
    emit(FlatGetIter(iterVal, iterableVal, line = line))

    val headerBlock = newBlock()
    val bodyBlock = newBlock()
    val hasElseBody = stmt.hasElseBody() && stmt.elseBody.stmtsCount > 0

    val elseBlock: Int?
    val exitBlock: Int
    val breakBlock: Int
    if (hasElseBody) {
        elseBlock = newBlock()
        breakBlock = newBlock()
        exitBlock = elseBlock
    } else {
        elseBlock = null
        exitBlock = newBlock()
        breakBlock = exitBlock
    }

    emitGoto(headerBlock)
    activate(headerBlock)

    val targetVal = lowerForTarget(stmt.index)
    emit(FlatNextIter(targetVal, iterVal, bodyBlock, exitBlock, line = line))

    activate(bodyBlock)
    if (stmt.index.hasTupleExpr()) {
        // For tuple targets the temp holds the next iter value; unpack into named locals.
        assignTo(stmt.index, targetVal, line)
    }
    withLoopTargets(breakBlock = breakBlock, continueBlock = headerBlock) {
        visitBlock(stmt.body)
    }
    if (!currentBlockTerminated()) emitGoto(headerBlock)

    if (elseBlock != null) {
        activate(elseBlock)
        visitBlock(stmt.elseBody)
        if (!currentBlockTerminated()) emitGoto(breakBlock)
        activate(breakBlock)
    } else {
        activate(exitBlock)
    }
}

private fun CfgSession.lowerForTarget(target: MypyExprProto): FlatLocal = when {
    target.hasNameExpr() -> FlatLocal(scope.resolveLocal(target.nameExpr.name))
    // Tuple targets land in a temp that we then unpack inside the body block.
    else -> newTempValue()
}

// ─── Try / Except ──────────────────────────────────────

private fun CfgSession.visitTry(stmt: MypyTryStmtProto, line: Int) {
    val handlerBlocks = (0 until stmt.handlersCount).map { newBlock() }
    val finallyBlock = if (stmt.hasFinallyBody() && stmt.finallyBody.stmtsCount > 0) newBlock() else null
    val elseBlock = if (stmt.hasElseBody() && stmt.elseBody.stmtsCount > 0) newBlock() else null
    val endBlock = newBlock()

    val tryBodyBlock = newBlock()
    emitGoto(tryBodyBlock)
    activate(tryBodyBlock)

    withExceptionHandlers(handlerBlocks) {
        visitBlock(stmt.body)

        if (!currentBlockTerminated()) {
            when {
                elseBlock != null -> emitGoto(elseBlock)
                finallyBlock != null -> emitGoto(finallyBlock)
                else -> emitGoto(endBlock)
            }
        }

        // Commit the try-body block under the handler-stack frame. Handlers
        // themselves run *outside* the frame (a raise inside a handler is not
        // caught by the same try).
        closeCurrentBlock()
    }

    for (i in 0 until stmt.handlersCount) {
        activate(handlerBlocks[i])

        val excTypes = if (i < stmt.typesCount && stmt.getTypes(i).kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            resolveExceptTypes(stmt.getTypes(i))
        } else emptyList()

        val excTarget = if (i < stmt.varsCount && stmt.getVars(i).hasNameExpr()) {
            FlatLocal(scope.resolveLocal(stmt.getVars(i).nameExpr.name))
        } else null

        emit(FlatExceptHandler(excTarget, excTypes, line = line))

        visitBlock(stmt.getHandlers(i))
        if (!currentBlockTerminated()) emitGoto(finallyBlock ?: endBlock)
    }

    if (elseBlock != null) {
        activate(elseBlock)
        visitBlock(stmt.elseBody)
        if (!currentBlockTerminated()) emitGoto(finallyBlock ?: endBlock)
    }

    if (finallyBlock != null) {
        activate(finallyBlock)
        visitBlock(stmt.finallyBody)
        if (!currentBlockTerminated()) emitGoto(endBlock)
    }

    activate(endBlock)
}

private fun resolveExceptTypes(typeExpr: MypyExprProto): List<FlatType> {
    val result = mutableListOf<FlatType>()
    when {
        typeExpr.hasTupleExpr() -> {
            for (item in typeExpr.tupleExpr.itemsList) result.addAll(resolveExceptTypes(item))
        }
        typeExpr.hasNameExpr() -> {
            val fullname = typeExpr.nameExpr.fullname.ifBlank { "builtins.${typeExpr.nameExpr.name}" }
            result.add(FlatClassType(fullname))
        }
        typeExpr.hasMemberExpr() -> {
            val fullname = typeExpr.memberExpr.fullname.ifBlank { typeExpr.memberExpr.name }
            result.add(FlatClassType(fullname))
        }
        else -> result.add(FlatClassType("builtins.Exception"))
    }
    return result
}

// ─── With ──────────────────────────────────────────────

private fun CfgSession.visitWith(stmt: MypyWithStmtProto, line: Int) {
    val ctxVals = mutableListOf<FlatValue>()
    for (i in stmt.exprsList.indices) {
        val ctxVal = lowerExpr(stmt.getExprs(i))
        ctxVals.add(ctxVal)

        val enterAttr = newTempValue()
        emit(FlatLoadAttr(enterAttr, ctxVal, "__enter__", line = line))
        val enterResult = newTempValue()
        emit(FlatCall(enterResult, enterAttr, line = line))
        if (i < stmt.targetsCount) {
            val target = stmt.getTargets(i)
            if (target.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
                assignTo(target, enterResult, line)
            }
        }
    }

    visitBlock(stmt.body)

    // Skip __exit__ if body terminated (early return / raise / break).
    if (currentBlockTerminated()) return

    val noneArg = FlatCallArg(FlatNoneConst)
    for (ctxVal in ctxVals.reversed()) {
        val exitAttr = newTempValue()
        emit(FlatLoadAttr(exitAttr, ctxVal, "__exit__", line = line))
        val exitResult = newTempValue()
        emit(FlatCall(exitResult, exitAttr, listOf(noneArg, noneArg, noneArg), line = line))
    }
}

// ─── Raise ─────────────────────────────────────────────

private fun CfgSession.visitRaise(stmt: MypyRaiseStmtProto, line: Int) {
    val exc = if (stmt.hasExpr() && stmt.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(stmt.expr)
    } else null
    val cause = if (stmt.hasFromExpr() && stmt.fromExpr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(stmt.fromExpr)
    } else null
    emit(FlatRaise(exc, cause, line = line))
}

// ─── Del ───────────────────────────────────────────────

private fun CfgSession.visitDel(stmt: MypyDelStmtProto, line: Int) = visitDelExpr(stmt.expr, line)

private fun CfgSession.visitDelExpr(expr: MypyExprProto, line: Int) {
    when {
        expr.hasNameExpr() ->
            emit(FlatDeleteLocal(FlatLocal(scope.resolveLocal(expr.nameExpr.name)), line = line))
        expr.hasMemberExpr() -> {
            val obj = lowerExpr(expr.memberExpr.expr)
            emit(FlatDeleteAttr(obj, expr.memberExpr.name, line = line))
        }
        expr.hasIndexExpr() -> {
            val obj = lowerExpr(expr.indexExpr.base)
            val index = lowerExpr(expr.indexExpr.index)
            emit(FlatDeleteSubscript(obj, index, line = line))
        }
        expr.hasTupleExpr() -> for (item in expr.tupleExpr.itemsList) visitDelExpr(item, line)
    }
}

// ─── Assert ────────────────────────────────────────────

private fun CfgSession.visitAssert(stmt: MypyAssertStmtProto, line: Int) {
    val cond = lowerExpr(stmt.expr)
    val passBlock = newBlock()
    val failBlock = newBlock()
    emitBranch(cond, passBlock, failBlock, line)

    activate(failBlock)
    var exc: FlatValue = FlatGlobalRef("AssertionError", "builtins")
    if (stmt.hasMsg() && stmt.msg.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        val msgVal = lowerExpr(stmt.msg)
        val callTarget = newTempValue()
        emit(FlatCall(callTarget, exc, listOf(FlatCallArg(msgVal)), line = line))
        exc = callTarget
    }
    emit(FlatRaise(exc, null, line = line))

    activate(passBlock)
}

// ─── Nested function definitions ───────────────────────

private fun CfgSession.visitNestedFuncDef(
    funcDef: MypyFuncDefProto,
    decoratorExprs: List<MypyExprProto>,
    line: Int,
) {
    // Decorators on nested defs reach us only via `MypyDecoratorDefProto.originalDecorators`.
    // For a bare nested `FuncDef` the list is empty (the serializer doesn't populate
    // `MypyFuncDefProto.decorators` for nested-def nodes).
    val decorators = decoratorExprs.map { DecoratorLowering.fromExpr(it) }

    // Nested-def statements only occur inside function bodies, where the
    // current function's qualified name is always set.
    val enclosing = requireNotNull(currentFunctionQualifiedName) {
        "visitNestedFuncDef invoked outside a function scope"
    }
    val nested = FunctionLowering.lowerNestedFunction(
        module = module,
        funcDef = funcDef,
        decorators = decorators,
        enclosingQualifiedName = enclosing,
    )
    module.register(nested)

    // Bind the local name to the synthetic global function.
    val ref = FlatGlobalRef(nested.name, module.moduleName)
    val targetName = scope.resolveLocal(funcDef.name)
    emit(FlatAssign(FlatLocal(targetName), ref, line = line))
}
