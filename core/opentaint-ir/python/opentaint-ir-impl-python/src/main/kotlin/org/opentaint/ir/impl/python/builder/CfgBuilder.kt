package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.impl.python.proto.*

/**
 * CFG builder: converts raw mypy AST statements into PIR basic blocks.
 *
 * This is the Kotlin port of Python's statement_visitor.py.
 * It manages basic blocks, control flow (if/while/for/try/with),
 * and emits PIR instructions.
 */
class CfgBuilder(
    val scope: ScopeStack,
    val moduleBuilder: ProtoToFlatBuilder? = null,
    var currentFunctionQualifiedName: String = "",
) {
    val exprLowering = ExpressionLowering(this)

    private val blocks = mutableListOf<FlatBlock>()
    private var currentInstructions = mutableListOf<FlatInst>()
    private var currentLabel = 0
    private var blockCounter = 0
    private var currentExceptionHandlers = mutableListOf<Int>()

    private var breakTarget: Int? = null
    private var continueTarget: Int? = null

    private fun reset() {
        blocks.clear()
        currentInstructions = mutableListOf()
        currentLabel = 0
        blockCounter = 0
        currentExceptionHandlers = mutableListOf()
        breakTarget = null
        continueTarget = null
    }

    // ─── Block management ──────────────────────────────────

    fun newBlock(): Int {
        blockCounter++
        return blockCounter
    }

    fun activate(label: Int) {
        finalizeCurrentBlock()
        currentLabel = label
        currentInstructions = mutableListOf()
    }

    private fun finalizeCurrentBlock() {
        if (currentInstructions.isNotEmpty() || currentLabel == 0) {
            blocks.add(
                FlatBlock(
                    label = currentLabel,
                    instructions = currentInstructions.toList(),
                    exceptionHandlers = currentExceptionHandlers.toList(),
                )
            )
        }
    }

    fun currentBlockTerminated(): Boolean {
        if (currentInstructions.isEmpty()) return false
        val last = currentInstructions.last()
        return last is FlatGoto || last is FlatBranch || last is FlatReturn ||
                last is FlatRaise || last is FlatUnreachable || last is FlatNextIter
    }

    // ─── Instruction emission ──────────────────────────────

    fun emit(inst: FlatInst) {
        currentInstructions.add(inst)
    }

    fun emitGoto(target: Int, line: Int = -1) {
        emit(FlatGoto(target, line = line))
    }

    fun emitBranch(condition: FlatValue, trueBlock: Int, falseBlock: Int, line: Int = -1) {
        emit(FlatBranch(condition, trueBlock, falseBlock, line = line))
    }

    fun emitReturn(value: FlatValue?, line: Int = -1) {
        emit(FlatReturn(value, line = line))
    }

    fun newTempValue(): FlatLocal {
        return FlatLocal(scope.newTemp())
    }

    // ─── CFG builders ────────────────────────────────────

    fun buildFunctionCfg(body: MypyBlockProto): FlatCFG {
        reset()
        currentLabel = 0
        currentInstructions = mutableListOf()

        visitBlock(body)

        if (!currentBlockTerminated()) {
            emitReturn(null)
        }

        return finalizeCfg()
    }

    fun buildModuleInitCfg(defs: List<MypyStmtProto>): FlatCFG {
        reset()
        currentLabel = 0
        currentInstructions = mutableListOf()

        for (stmt in defs) {
            when {
                stmt.hasExpressionStmt() || stmt.hasAssignment() || stmt.hasOpAssignment() -> {
                    visitStmt(stmt)
                }
                // Skip FuncDef, ClassDef — they're extracted separately
            }
        }

        if (!currentBlockTerminated()) {
            emitReturn(null)
        }

        return finalizeCfg()
    }

    private fun finalizeCfg(): FlatCFG {
        finalizeCurrentBlock()

        val exitLabels = mutableListOf<Int>()
        for (block in blocks) {
            if (block.instructions.isNotEmpty()) {
                val last = block.instructions.last()
                if (last is FlatReturn || last is FlatRaise || last is FlatUnreachable) {
                    exitLabels.add(block.label)
                }
            }
        }

        return FlatCFG(
            blocks = blocks.toList(),
            entryBlock = 0,
            exitBlocks = exitLabels,
        )
    }

    // ─── Statement dispatch ────────────────────────────────

    fun visitBlock(block: MypyBlockProto) {
        for (stmt in block.stmtsList) {
            if (currentBlockTerminated()) break
            visitStmt(stmt)
        }
    }

    private fun visitStmt(stmt: MypyStmtProto) {
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
            stmt.hasBreakStmt() -> visitBreak()
            stmt.hasContinueStmt() -> visitContinue()
            stmt.hasDelStmt() -> visitDel(stmt.delStmt, stmt.line)
            stmt.hasAssertStmt() -> visitAssert(stmt.assertStmt, stmt.line)
            stmt.hasPassStmt() -> { /* no-op */ }
            stmt.hasGlobalDecl() -> { /* no-op */ }
            stmt.hasNonlocalDecl() -> { /* no-op — nonlocal semantics handled via FreeVarAnalyzer */ }
            stmt.hasFuncDef() -> visitNestedFuncDef(stmt.funcDef, emptyList(), stmt.line)
            stmt.hasDecorator() ->
                visitNestedFuncDef(stmt.decorator.func, stmt.decorator.originalDecoratorsList, stmt.line)
            stmt.hasClassDef() -> { /* ClassDef inside function body — not supported */ }
        }
    }

    // ─── Assignment ────────────────────────────────────────

    private fun visitAssignment(stmt: MypyAssignmentStmtProto, line: Int) {
        val rhs = exprLowering.accept(stmt.rvalue)
        for (lvalue in stmt.lvaluesList) {
            assignTo(lvalue, rhs, line)
        }
    }

    fun assignTo(lvalue: MypyExprProto, rhs: FlatValue, line: Int) {
        when {
            lvalue.hasNameExpr() -> {
                val targetName = scope.resolveLocal(lvalue.nameExpr.name)
                emit(FlatAssign(FlatLocal(targetName), rhs, line = line))
            }
            lvalue.hasMemberExpr() -> {
                val obj = exprLowering.accept(lvalue.memberExpr.expr)
                emit(FlatStoreAttr(obj, lvalue.memberExpr.name, rhs, line = line))
            }
            lvalue.hasIndexExpr() -> {
                val obj = exprLowering.accept(lvalue.indexExpr.base)
                val index = exprLowering.accept(lvalue.indexExpr.index)
                emit(FlatStoreSubscript(obj, index, rhs, line = line))
            }
            lvalue.hasTupleExpr() -> {
                val targets = mutableListOf<FlatValue>()
                var starIndex = -1
                for ((i, item) in lvalue.tupleExpr.itemsList.withIndex()) {
                    when {
                        item.hasNameExpr() -> {
                            val name = scope.resolveLocal(item.nameExpr.name)
                            targets.add(FlatLocal(name))
                        }
                        item.hasStarExpr() && item.starExpr.expr.hasNameExpr() -> {
                            val name = scope.resolveLocal(item.starExpr.expr.nameExpr.name)
                            targets.add(FlatLocal(name))
                            starIndex = i
                        }
                        else -> {
                            targets.add(newTempValue())
                        }
                    }
                    if (item.hasStarExpr() && starIndex == -1) {
                        starIndex = i
                    }
                }
                emit(FlatUnpack(targets, rhs, starIndex, line = line))
            }
            lvalue.hasStarExpr() -> {
                assignTo(lvalue.starExpr.expr, rhs, line)
            }
        }
    }

    private fun visitOperatorAssignment(stmt: MypyOperatorAssignmentStmtProto, line: Int) {
        val lhsVal = exprLowering.accept(stmt.lvalue)
        val rhsVal = exprLowering.accept(stmt.rvalue)
        val target = newTempValue()
        val op = ExpressionLowering.BIN_OP_MAP[stmt.op] ?: FlatBinaryOperator.ADD
        emit(FlatBinOp(target, lhsVal, rhsVal, op, line = line))
        assignTo(stmt.lvalue, target, line)
    }

    // ─── Expression statement ────────────────────────────

    private fun visitExpressionStmt(stmt: MypyExpressionStmtProto) {
        exprLowering.accept(stmt.expr)
    }

    // ─── Return ────────────────────────────────────────────

    private fun visitReturn(stmt: MypyReturnStmtProto, line: Int) {
        val value = if (stmt.hasExpr() && stmt.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            exprLowering.accept(stmt.expr)
        } else null
        emitReturn(value, line)
    }

    // ─── If ────────────────────────────────────────────────

    private fun visitIf(stmt: MypyIfStmtProto, line: Int) {
        val endBlock = newBlock()

        for (i in stmt.conditionsList.indices) {
            val condVal = exprLowering.accept(stmt.getConditions(i))
            val trueBlock = newBlock()
            val falseBlock = if (i < stmt.conditionsCount - 1 || stmt.hasElseBody()) {
                newBlock()
            } else {
                endBlock
            }

            emitBranch(condVal, trueBlock, falseBlock, line)

            activate(trueBlock)
            visitBlock(stmt.getBodies(i))
            if (!currentBlockTerminated()) {
                emitGoto(endBlock)
            }

            if (falseBlock != endBlock) {
                activate(falseBlock)
            }
        }

        if (stmt.hasElseBody()) {
            visitBlock(stmt.elseBody)
            if (!currentBlockTerminated()) {
                emitGoto(endBlock)
            }
        }

        activate(endBlock)
    }

    // ─── While ─────────────────────────────────────────────

    private fun visitWhile(stmt: MypyWhileStmtProto, line: Int) {
        val headerBlock = newBlock()
        val bodyBlock = newBlock()

        val exitBlock: Int
        val breakBlock: Int
        val elseBlock: Int?

        if (stmt.hasElseBody() && stmt.elseBody.stmtsCount > 0) {
            elseBlock = newBlock()
            val afterBlock = newBlock()
            exitBlock = elseBlock
            breakBlock = afterBlock
        } else {
            elseBlock = null
            exitBlock = newBlock()
            breakBlock = exitBlock
        }

        emitGoto(headerBlock)
        activate(headerBlock)

        val cond = exprLowering.accept(stmt.condition)
        emitBranch(cond, bodyBlock, exitBlock, line)

        val oldBreak = breakTarget
        val oldContinue = continueTarget
        breakTarget = breakBlock
        continueTarget = headerBlock

        activate(bodyBlock)
        visitBlock(stmt.body)
        if (!currentBlockTerminated()) {
            emitGoto(headerBlock)
        }

        breakTarget = oldBreak
        continueTarget = oldContinue

        if (elseBlock != null) {
            activate(elseBlock)
            visitBlock(stmt.elseBody)
            if (!currentBlockTerminated()) {
                emitGoto(breakBlock)
            }
            activate(breakBlock)
        } else {
            activate(exitBlock)
        }
    }

    // ─── For ───────────────────────────────────────────────

    private fun visitFor(stmt: MypyForStmtProto, line: Int) {
        val iterVal = newTempValue()
        val iterableVal = exprLowering.accept(stmt.iterable)
        emit(FlatGetIter(iterVal, iterableVal, line = line))

        val headerBlock = newBlock()
        val bodyBlock = newBlock()

        val exitBlock: Int
        val breakBlock: Int
        val elseBlock: Int?

        if (stmt.hasElseBody() && stmt.elseBody.stmtsCount > 0) {
            elseBlock = newBlock()
            val afterBlock = newBlock()
            exitBlock = elseBlock
            breakBlock = afterBlock
        } else {
            elseBlock = null
            exitBlock = newBlock()
            breakBlock = exitBlock
        }

        emitGoto(headerBlock)
        activate(headerBlock)

        val targetVal = lowerForTarget(stmt.index)
        emit(FlatNextIter(targetVal, iterVal, bodyBlock, exitBlock, line = line))

        val oldBreak = breakTarget
        val oldContinue = continueTarget
        breakTarget = breakBlock
        continueTarget = headerBlock

        activate(bodyBlock)
        // Emit tuple unpack if target is a tuple expression
        if (stmt.index.hasTupleExpr()) {
            assignTo(stmt.index, targetVal, line)
        }
        visitBlock(stmt.body)
        if (!currentBlockTerminated()) {
            emitGoto(headerBlock)
        }

        breakTarget = oldBreak
        continueTarget = oldContinue

        if (elseBlock != null) {
            activate(elseBlock)
            visitBlock(stmt.elseBody)
            if (!currentBlockTerminated()) {
                emitGoto(breakBlock)
            }
            activate(breakBlock)
        } else {
            activate(exitBlock)
        }
    }

    private fun lowerForTarget(target: MypyExprProto): FlatLocal {
        return when {
            target.hasNameExpr() -> {
                val name = scope.resolveLocal(target.nameExpr.name)
                FlatLocal(name)
            }
            target.hasTupleExpr() -> {
                // For tuple unpacking, use a temp for next_iter result
                newTempValue()
            }
            else -> newTempValue()
        }
    }

    // ─── Try/Except ────────────────────────────────────────

    private fun visitTry(stmt: MypyTryStmtProto, line: Int) {
        val handlerBlocks = (0 until stmt.handlersCount).map { newBlock() }
        val finallyBlock = if (stmt.hasFinallyBody() && stmt.finallyBody.stmtsCount > 0) newBlock() else null
        val elseBlock = if (stmt.hasElseBody() && stmt.elseBody.stmtsCount > 0) newBlock() else null
        val endBlock = newBlock()

        // Activate a new block for the try body
        val tryBodyBlock = newBlock()
        emitGoto(tryBodyBlock)
        activate(tryBodyBlock)

        val oldHandlers = currentExceptionHandlers.toMutableList()
        currentExceptionHandlers = handlerBlocks.toMutableList()

        visitBlock(stmt.body)

        if (!currentBlockTerminated()) {
            when {
                elseBlock != null -> emitGoto(elseBlock)
                finallyBlock != null -> emitGoto(finallyBlock)
                else -> emitGoto(endBlock)
            }
        }

        // Explicitly finalize before restoring handlers (DC-9 fix)
        finalizeCurrentBlock()
        currentInstructions = mutableListOf()

        currentExceptionHandlers = oldHandlers

        // Handlers
        for (i in 0 until stmt.handlersCount) {
            activate(handlerBlocks[i])

            val excTypes = mutableListOf<FlatType>()
            if (i < stmt.typesCount) {
                val typeExpr = stmt.getTypes(i)
                if (typeExpr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
                    excTypes.addAll(resolveExceptTypes(typeExpr))
                }
            }

            var excTarget: FlatValue? = null
            if (i < stmt.varsCount) {
                val varExpr = stmt.getVars(i)
                if (varExpr.hasNameExpr()) {
                    val varName = scope.resolveLocal(varExpr.nameExpr.name)
                    excTarget = FlatLocal(varName)
                }
            }

            emit(FlatExceptHandler(excTarget, excTypes, line = line))

            visitBlock(stmt.getHandlers(i))
            if (!currentBlockTerminated()) {
                if (finallyBlock != null) emitGoto(finallyBlock) else emitGoto(endBlock)
            }
        }

        if (elseBlock != null) {
            activate(elseBlock)
            visitBlock(stmt.elseBody)
            if (!currentBlockTerminated()) {
                if (finallyBlock != null) emitGoto(finallyBlock) else emitGoto(endBlock)
            }
        }

        if (finallyBlock != null) {
            activate(finallyBlock)
            visitBlock(stmt.finallyBody)
            if (!currentBlockTerminated()) {
                emitGoto(endBlock)
            }
        }

        activate(endBlock)
    }

    // ─── With ──────────────────────────────────────────────

    private fun visitWith(stmt: MypyWithStmtProto, line: Int) {
        val ctxVals = mutableListOf<FlatValue>()
        for (i in stmt.exprsList.indices) {
            val ctxVal = exprLowering.accept(stmt.getExprs(i))
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

        // Skip __exit__ if body terminated (DC-12 fix)
        if (currentBlockTerminated()) return

        // Emit __exit__ calls in reverse order
        val noneArg = FlatCallArg(FlatNoneConst)
        for (ctxVal in ctxVals.reversed()) {
            val exitAttr = newTempValue()
            emit(FlatLoadAttr(exitAttr, ctxVal, "__exit__", line = line))
            val exitResult = newTempValue()
            emit(FlatCall(exitResult, exitAttr, listOf(noneArg, noneArg, noneArg), line = line))
        }
    }

    // ─── Raise ─────────────────────────────────────────────

    private fun visitRaise(stmt: MypyRaiseStmtProto, line: Int) {
        val exc = if (stmt.hasExpr() && stmt.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            exprLowering.accept(stmt.expr)
        } else null
        val cause = if (stmt.hasFromExpr() && stmt.fromExpr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            exprLowering.accept(stmt.fromExpr)
        } else null
        emit(FlatRaise(exc, cause, line = line))
    }

    // ─── Break / Continue ──────────────────────────────────

    private fun visitBreak() {
        breakTarget?.let { emitGoto(it) }
    }

    private fun visitContinue() {
        continueTarget?.let { emitGoto(it) }
    }

    // ─── Del ───────────────────────────────────────────────

    private fun visitDel(stmt: MypyDelStmtProto, line: Int) {
        visitDelExpr(stmt.expr, line)
    }

    private fun visitDelExpr(expr: MypyExprProto, line: Int) {
        when {
            expr.hasNameExpr() -> {
                emit(FlatDeleteLocal(FlatLocal(scope.resolveLocal(expr.nameExpr.name)), line = line))
            }
            expr.hasMemberExpr() -> {
                val obj = exprLowering.accept(expr.memberExpr.expr)
                emit(FlatDeleteAttr(obj, expr.memberExpr.name, line = line))
            }
            expr.hasIndexExpr() -> {
                val obj = exprLowering.accept(expr.indexExpr.base)
                val index = exprLowering.accept(expr.indexExpr.index)
                emit(FlatDeleteSubscript(obj, index, line = line))
            }
            expr.hasTupleExpr() -> {
                for (item in expr.tupleExpr.itemsList) {
                    visitDelExpr(item, line)
                }
            }
        }
    }

    // ─── Nested FuncDef ──────────────────────────────────

    private fun visitNestedFuncDef(
        funcDef: MypyFuncDefProto,
        decoratorExprs: List<MypyExprProto>,
        line: Int,
    ) {
        val mb = moduleBuilder ?: return

        // Extract the nested function as a module-level function (same pattern as lambdas)
        val (uniqueName, _) = mb.extractNestedFunction(funcDef, decoratorExprs, currentFunctionQualifiedName)

        // Emit assignment: funcname = GlobalRef(uniqueName)
        // Use uniqueName to avoid collisions between inner functions with same name
        val ref = FlatGlobalRef(uniqueName, mb.moduleName)
        val targetName = scope.resolveLocal(funcDef.name)
        emit(FlatAssign(FlatLocal(targetName), ref, line = line))
    }

    // ─── Assert ────────────────────────────────────────────

    private fun visitAssert(stmt: MypyAssertStmtProto, line: Int) {
        val cond = exprLowering.accept(stmt.expr)
        val passBlock = newBlock()
        val failBlock = newBlock()
        emitBranch(cond, passBlock, failBlock, line)

        activate(failBlock)
        var exc: FlatValue = FlatGlobalRef("AssertionError", "builtins")

        if (stmt.hasMsg() && stmt.msg.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            val msgVal = exprLowering.accept(stmt.msg)
            val callTarget = newTempValue()
            emit(FlatCall(callTarget, exc, listOf(FlatCallArg(msgVal)), line = line))
            exc = callTarget
        }
        emit(FlatRaise(exc, null, line = line))

        activate(passBlock)
    }

    // ─── Helpers ───────────────────────────────────────────

    private fun resolveExceptTypes(typeExpr: MypyExprProto): List<FlatType> {
        val result = mutableListOf<FlatType>()
        when {
            typeExpr.hasTupleExpr() -> {
                for (item in typeExpr.tupleExpr.itemsList) {
                    result.addAll(resolveExceptTypes(item))
                }
            }
            typeExpr.hasNameExpr() -> {
                var fullname = typeExpr.nameExpr.fullname
                if (fullname.isBlank()) {
                    fullname = "builtins.${typeExpr.nameExpr.name}"
                }
                result.add(FlatClassType(fullname))
            }
            typeExpr.hasMemberExpr() -> {
                var fullname = typeExpr.memberExpr.fullname
                if (fullname.isBlank()) {
                    fullname = typeExpr.memberExpr.name
                }
                result.add(FlatClassType(fullname))
            }
            else -> {
                result.add(FlatClassType("builtins.Exception"))
            }
        }
        return result
    }
}
