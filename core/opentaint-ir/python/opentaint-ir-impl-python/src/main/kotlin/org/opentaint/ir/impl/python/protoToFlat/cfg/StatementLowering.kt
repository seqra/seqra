package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.api.python.PIRPhysicalLocation
import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.DecoratorLowering
import org.opentaint.ir.impl.python.protoToFlat.FunctionLowering
import org.opentaint.ir.impl.python.protoToFlat.toPhysicalLocation
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
    val loc = stmt.toPhysicalLocation()
    when {
        stmt.hasAssignment() -> visitAssignment(stmt.assignment, loc)
        stmt.hasOpAssignment() -> visitOperatorAssignment(stmt.opAssignment, loc)
        stmt.hasExpressionStmt() -> visitExpressionStmt(stmt.expressionStmt)
        stmt.hasReturnStmt() -> visitReturn(stmt.returnStmt, loc)
        stmt.hasIfStmt() -> visitIf(stmt.ifStmt, loc)
        stmt.hasWhileStmt() -> visitWhile(stmt.whileStmt, loc)
        stmt.hasForStmt() -> visitFor(stmt.forStmt, loc)
        stmt.hasTryStmt() -> visitTry(stmt.tryStmt, loc)
        stmt.hasWithStmt() -> visitWith(stmt.withStmt, loc)
        stmt.hasRaiseStmt() -> visitRaise(stmt.raiseStmt, loc)
        stmt.hasBreakStmt() -> breakTarget?.let { emitGoto(it) }
        stmt.hasContinueStmt() -> continueTarget?.let { emitGoto(it) }
        stmt.hasDelStmt() -> visitDel(stmt.delStmt, loc)
        stmt.hasAssertStmt() -> visitAssert(stmt.assertStmt, loc)
        stmt.hasFuncDef() -> visitNestedFuncDef(stmt.funcDef, emptyList(), loc)
        stmt.hasDecorator() ->
            visitNestedFuncDef(stmt.decorator.func, stmt.decorator.originalDecoratorsList, loc)
        stmt.hasGlobalDecl() -> recordGlobal(stmt.globalDecl.namesList)
        stmt.hasNonlocalDecl() -> recordNonlocal(stmt.nonlocalDecl.namesList)
        // PassStmt, ClassDef inside body: no-op
    }
}

// ─── Assignment ────────────────────────────────────────

internal fun CfgSession.visitAssignment(stmt: MypyAssignmentStmtProto, location: PIRPhysicalLocation?) {
    val rhs = lowerExpr(stmt.rvalue)
    for (lvalue in stmt.lvaluesList) {
        assignTo(lvalue, rhs, location)
    }
}

internal fun CfgSession.assignTo(lvalue: MypyExprProto, rhs: FlatValue, location: PIRPhysicalLocation?) {
    when {
        lvalue.hasNameExpr() -> {
            val targetName = scope.resolveLocal(lvalue.nameExpr.name)
            emit(FlatAssign(FlatLocal(targetName), rhs, physicalLocation = location))
        }
        lvalue.hasMemberExpr() -> {
            val obj = lowerExpr(lvalue.memberExpr.expr)
            emit(FlatStoreAttr(obj, lvalue.memberExpr.name, rhs, physicalLocation = location))
        }
        lvalue.hasIndexExpr() -> {
            val obj = lowerExpr(lvalue.indexExpr.base)
            val index = lowerExpr(lvalue.indexExpr.index)
            emit(FlatStoreSubscript(obj, index, rhs, physicalLocation = location))
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
            emit(FlatUnpack(targets, rhs, starIndex, physicalLocation = location))
        }
        lvalue.hasStarExpr() -> assignTo(lvalue.starExpr.expr, rhs, location)
    }
}

private fun CfgSession.visitOperatorAssignment(stmt: MypyOperatorAssignmentStmtProto, location: PIRPhysicalLocation?) {
    val lhsVal = lowerExpr(stmt.lvalue)
    val rhsVal = lowerExpr(stmt.rvalue)
    val target = newTempValue()
    val op = BIN_OP_MAP[stmt.op] ?: FlatBinaryOperator.ADD
    emit(FlatBinOp(target, lhsVal, rhsVal, op, physicalLocation = location))
    assignTo(stmt.lvalue, target, location)
}

// ─── Expression statement ────────────────────────────

private fun CfgSession.visitExpressionStmt(stmt: MypyExpressionStmtProto) {
    lowerExpr(stmt.expr)
}

// ─── Return ────────────────────────────────────────────

private fun CfgSession.visitReturn(stmt: MypyReturnStmtProto, location: PIRPhysicalLocation?) {
    val value = if (stmt.hasExpr() && stmt.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(stmt.expr)
    } else null
    emitReturn(value, location)
}

// ─── If ────────────────────────────────────────────────

private fun CfgSession.visitIf(stmt: MypyIfStmtProto, location: PIRPhysicalLocation?) {
    val endBlock = newBlock()

    for (i in stmt.conditionsList.indices) {
        val condVal = lowerExpr(stmt.getConditions(i))
        val trueBlock = newBlock()
        val falseBlock = if (i < stmt.conditionsCount - 1 || stmt.hasElseBody()) newBlock() else endBlock

        emitBranch(condVal, trueBlock, falseBlock, location)

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

private fun CfgSession.visitWhile(stmt: MypyWhileStmtProto, location: PIRPhysicalLocation?) {
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
    emitBranch(cond, bodyBlock, exitBlock, location)

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

private fun CfgSession.visitFor(stmt: MypyForStmtProto, location: PIRPhysicalLocation?) {
    val iterVal = newTempValue()
    val iterableVal = lowerExpr(stmt.iterable)
    emit(FlatGetIter(iterVal, iterableVal, physicalLocation = location))

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
    emit(FlatNextIter(targetVal, iterVal, bodyBlock, exitBlock, physicalLocation = location))

    activate(bodyBlock)
    if (stmt.index.hasTupleExpr()) {
        // For tuple targets the temp holds the next iter value; unpack into named locals.
        assignTo(stmt.index, targetVal, location)
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

private fun CfgSession.visitTry(stmt: MypyTryStmtProto, location: PIRPhysicalLocation?) {
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

        emit(FlatExceptHandler(excTarget, excTypes, physicalLocation = location))

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

private fun CfgSession.visitWith(stmt: MypyWithStmtProto, location: PIRPhysicalLocation?) {
    val ctxVals = mutableListOf<FlatValue>()
    for (i in stmt.exprsList.indices) {
        val ctxVal = lowerExpr(stmt.getExprs(i))
        ctxVals.add(ctxVal)

        val enterAttr = newTempValue()
        emit(FlatLoadAttr(enterAttr, ctxVal, "__enter__", physicalLocation = location))
        val enterResult = newTempValue()
        emit(FlatCall(enterResult, enterAttr, physicalLocation = location))
        if (i < stmt.targetsCount) {
            val target = stmt.getTargets(i)
            if (target.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
                assignTo(target, enterResult, location)
            }
        }
    }

    visitBlock(stmt.body)

    // Skip __exit__ if body terminated (early return / raise / break).
    if (currentBlockTerminated()) return

    val noneArg = FlatCallArg(FlatNoneConst)
    for (ctxVal in ctxVals.reversed()) {
        val exitAttr = newTempValue()
        emit(FlatLoadAttr(exitAttr, ctxVal, "__exit__", physicalLocation = location))
        val exitResult = newTempValue()
        emit(FlatCall(exitResult, exitAttr, listOf(noneArg, noneArg, noneArg), physicalLocation = location))
    }
}

// ─── Raise ─────────────────────────────────────────────

private fun CfgSession.visitRaise(stmt: MypyRaiseStmtProto, location: PIRPhysicalLocation?) {
    val exc = if (stmt.hasExpr() && stmt.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(stmt.expr)
    } else null
    val cause = if (stmt.hasFromExpr() && stmt.fromExpr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(stmt.fromExpr)
    } else null
    emit(FlatRaise(exc, cause, physicalLocation = location))
}

// ─── Del ───────────────────────────────────────────────

private fun CfgSession.visitDel(stmt: MypyDelStmtProto, location: PIRPhysicalLocation?) =
    visitDelExpr(stmt.expr, location)

private fun CfgSession.visitDelExpr(expr: MypyExprProto, location: PIRPhysicalLocation?) {
    when {
        expr.hasNameExpr() ->
            emit(FlatDeleteLocal(FlatLocal(scope.resolveLocal(expr.nameExpr.name)), physicalLocation = location))
        expr.hasMemberExpr() -> {
            val obj = lowerExpr(expr.memberExpr.expr)
            emit(FlatDeleteAttr(obj, expr.memberExpr.name, physicalLocation = location))
        }
        expr.hasIndexExpr() -> {
            val obj = lowerExpr(expr.indexExpr.base)
            val index = lowerExpr(expr.indexExpr.index)
            emit(FlatDeleteSubscript(obj, index, physicalLocation = location))
        }
        expr.hasTupleExpr() -> for (item in expr.tupleExpr.itemsList) visitDelExpr(item, location)
    }
}

// ─── Assert ────────────────────────────────────────────

private fun CfgSession.visitAssert(stmt: MypyAssertStmtProto, location: PIRPhysicalLocation?) {
    val cond = lowerExpr(stmt.expr)
    val passBlock = newBlock()
    val failBlock = newBlock()
    emitBranch(cond, passBlock, failBlock, location)

    activate(failBlock)
    var exc: FlatValue = FlatGlobalRef("builtins.AssertionError")
    if (stmt.hasMsg() && stmt.msg.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        val msgVal = lowerExpr(stmt.msg)
        val callTarget = newTempValue()
        emit(FlatCall(callTarget, exc, listOf(FlatCallArg(msgVal)), physicalLocation = location))
        exc = callTarget
    }
    emit(FlatRaise(exc, null, physicalLocation = location))

    activate(passBlock)
}

// ─── Nested function definitions ───────────────────────

private fun CfgSession.visitNestedFuncDef(
    funcDef: MypyFuncDefProto,
    decoratorExprs: List<MypyExprProto>,
    location: PIRPhysicalLocation?,
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
    val enclosingFnName = requireNotNull(currentFunctionName) {
        "visitNestedFuncDef invoked outside a function scope (no currentFunctionName)"
    }
    val nested = FunctionLowering.lowerNestedFunction(
        module = module,
        funcDef = funcDef,
        decorators = decorators,
        enclosingQualifiedName = enclosing,
        enclosingName = enclosingFnName,
    )
    module.register(nested)

    // Bind the local name to the synthetic global function.
    val ref = FlatGlobalRef(nested.qualifiedName)
    val targetName = scope.resolveLocal(funcDef.name)
    emit(FlatBindFunction(FlatLocal(targetName), ref, physicalLocation = location))
}
