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
    val moduleBuilder: MypyModuleBuilder? = null,
    var currentFunctionQualifiedName: String = "",
) {
    val exprLowering = ExpressionLowering(this)

    private val blocks = mutableListOf<PIRBasicBlockProto>()
    private var currentInstructions = mutableListOf<PIRInstructionProto>()
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
            val block = PIRBasicBlockProto.newBuilder()
                .setLabel(currentLabel)
                .addAllInstructions(currentInstructions)
                .addAllExceptionHandlers(currentExceptionHandlers)
                .build()
            blocks.add(block)
        }
    }

    fun currentBlockTerminated(): Boolean {
        if (currentInstructions.isEmpty()) return false
        val last = currentInstructions.last()
        return last.hasGotoInst() || last.hasBranch() || last.hasReturnInst() ||
                last.hasRaiseInst() || last.hasUnreachable() || last.hasNextIter()
    }

    // ─── Instruction emission ──────────────────────────────

    fun emit(inst: PIRInstructionProto) {
        currentInstructions.add(inst)
    }

    fun emitGoto(target: Int, line: Int = -1) {
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setGotoInst(PIRGotoProto.newBuilder().setTargetBlock(target))
                .build()
        )
    }

    fun emitBranch(condition: PIRValueProto, trueBlock: Int, falseBlock: Int, line: Int = -1) {
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBranch(
                    PIRBranchProto.newBuilder()
                        .setCondition(condition)
                        .setTrueBlock(trueBlock)
                        .setFalseBlock(falseBlock)
                )
                .build()
        )
    }

    fun emitReturn(value: PIRValueProto?, line: Int = -1) {
        val ret = PIRReturnProto.newBuilder()
        if (value != null) ret.setValue(value)
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setReturnInst(ret)
                .build()
        )
    }

    fun newTempValue(): PIRValueProto {
        val name = scope.newTemp()
        return PIRValueProto.newBuilder()
            .setLocal(PIRLocalProto.newBuilder().setName(name))
            .build()
    }

    // ─── CFG builders ────────────────────────────────────

    fun buildFunctionCfg(body: MypyBlockProto): PIRCFGProto {
        reset()
        currentLabel = 0
        currentInstructions = mutableListOf()

        visitBlock(body)

        if (!currentBlockTerminated()) {
            emitReturn(null)
        }

        return finalizeCfg()
    }

    fun buildModuleInitCfg(defs: List<MypyStmtProto>): PIRCFGProto {
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

    private fun finalizeCfg(): PIRCFGProto {
        finalizeCurrentBlock()

        val exitLabels = mutableListOf<Int>()
        for (block in blocks) {
            if (block.instructionsCount > 0) {
                val last = block.getInstructions(block.instructionsCount - 1)
                if (last.hasReturnInst() || last.hasRaiseInst() || last.hasUnreachable()) {
                    exitLabels.add(block.label)
                }
            }
        }

        return PIRCFGProto.newBuilder()
            .addAllBlocks(blocks)
            .setEntryBlock(0)
            .addAllExitBlocks(exitLabels)
            .build()
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
            stmt.hasFuncDef() -> visitNestedFuncDef(stmt.funcDef, stmt.line)
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

    fun assignTo(lvalue: MypyExprProto, rhs: PIRValueProto, line: Int) {
        when {
            lvalue.hasNameExpr() -> {
                val targetName = scope.resolveLocal(lvalue.nameExpr.name)
                val target = PIRValueProto.newBuilder()
                    .setLocal(PIRLocalProto.newBuilder().setName(targetName))
                    .build()
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(rhs))
                        .build()
                )
            }
            lvalue.hasMemberExpr() -> {
                val obj = exprLowering.accept(lvalue.memberExpr.expr)
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setStoreAttr(
                            PIRStoreAttrProto.newBuilder()
                                .setObject(obj)
                                .setAttribute(lvalue.memberExpr.name)
                                .setValue(rhs)
                        )
                        .build()
                )
            }
            lvalue.hasIndexExpr() -> {
                val obj = exprLowering.accept(lvalue.indexExpr.base)
                val index = exprLowering.accept(lvalue.indexExpr.index)
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setStoreSubscript(
                            PIRStoreSubscriptProto.newBuilder()
                                .setObject(obj)
                                .setIndex(index)
                                .setValue(rhs)
                        )
                        .build()
                )
            }
            lvalue.hasTupleExpr() -> {
                val targets = mutableListOf<PIRValueProto>()
                var starIndex = -1
                for ((i, item) in lvalue.tupleExpr.itemsList.withIndex()) {
                    when {
                        item.hasNameExpr() -> {
                            val name = scope.resolveLocal(item.nameExpr.name)
                            targets.add(
                                PIRValueProto.newBuilder()
                                    .setLocal(PIRLocalProto.newBuilder().setName(name))
                                    .build()
                            )
                        }
                        item.hasStarExpr() && item.starExpr.expr.hasNameExpr() -> {
                            val name = scope.resolveLocal(item.starExpr.expr.nameExpr.name)
                            targets.add(
                                PIRValueProto.newBuilder()
                                    .setLocal(PIRLocalProto.newBuilder().setName(name))
                                    .build()
                            )
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
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setUnpack(
                            PIRUnpackProto.newBuilder()
                                .addAllTargets(targets)
                                .setSource(rhs)
                                .setStarIndex(starIndex)
                        )
                        .build()
                )
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
        val op = ExpressionLowering.BIN_OP_MAP[stmt.op] ?: BinaryOperator.ADD
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBinOp(
                    PIRBinOpProto.newBuilder()
                        .setTarget(target)
                        .setLeft(lhsVal)
                        .setRight(rhsVal)
                        .setOp(op)
                )
                .build()
        )
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
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setGetIter(
                    PIRGetIterProto.newBuilder()
                        .setTarget(iterVal)
                        .setIterable(iterableVal)
                )
                .build()
        )

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
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setNextIter(
                    PIRNextIterProto.newBuilder()
                        .setTarget(targetVal)
                        .setIterator(iterVal)
                        .setBodyBlock(bodyBlock)
                        .setExitBlock(exitBlock)
                )
                .build()
        )

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

    private fun lowerForTarget(target: MypyExprProto): PIRValueProto {
        return when {
            target.hasNameExpr() -> {
                val name = scope.resolveLocal(target.nameExpr.name)
                PIRValueProto.newBuilder()
                    .setLocal(PIRLocalProto.newBuilder().setName(name))
                    .build()
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

            val excTypeProtos = mutableListOf<PIRTypeProto>()
            if (i < stmt.typesCount) {
                val typeExpr = stmt.getTypes(i)
                if (typeExpr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
                    excTypeProtos.addAll(resolveExceptTypes(typeExpr))
                }
            }

            var excTarget: PIRValueProto? = null
            if (i < stmt.varsCount) {
                val varExpr = stmt.getVars(i)
                if (varExpr.hasNameExpr()) {
                    val varName = scope.resolveLocal(varExpr.nameExpr.name)
                    excTarget = PIRValueProto.newBuilder()
                        .setLocal(PIRLocalProto.newBuilder().setName(varName))
                        .build()
                }
            }

            val eh = PIRExceptHandlerProto.newBuilder()
                .addAllExceptionTypes(excTypeProtos)
            if (excTarget != null) eh.setTarget(excTarget)
            emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setExceptHandler(eh)
                    .build()
            )

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
        val ctxVals = mutableListOf<PIRValueProto>()
        for (i in stmt.exprsList.indices) {
            val ctxVal = exprLowering.accept(stmt.getExprs(i))
            ctxVals.add(ctxVal)

            val enterAttr = newTempValue()
            emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setLoadAttr(
                        PIRLoadAttrProto.newBuilder()
                            .setTarget(enterAttr)
                            .setObject(ctxVal)
                            .setAttribute("__enter__")
                    )
                    .build()
            )
            val enterResult = newTempValue()
            emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setCall(
                        PIRCallProto.newBuilder()
                            .setTarget(enterResult)
                            .setCallee(enterAttr)
                    )
                    .build()
            )
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
        val noneVal = PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setNoneValue(true))
            .build()
        for (ctxVal in ctxVals.reversed()) {
            val exitAttr = newTempValue()
            emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setLoadAttr(
                        PIRLoadAttrProto.newBuilder()
                            .setTarget(exitAttr)
                            .setObject(ctxVal)
                            .setAttribute("__exit__")
                    )
                    .build()
            )
            val exitResult = newTempValue()
            val noneArg = PIRCallArgProto.newBuilder()
                .setValue(noneVal)
                .setKind(CallArgKind.POSITIONAL)
                .build()
            emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setCall(
                        PIRCallProto.newBuilder()
                            .setTarget(exitResult)
                            .setCallee(exitAttr)
                            .addArgs(noneArg)
                            .addArgs(noneArg)
                            .addArgs(noneArg)
                    )
                    .build()
            )
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

        val raiseBuilder = PIRRaiseProto.newBuilder()
        if (exc != null) raiseBuilder.setException(exc)
        if (cause != null) raiseBuilder.setCause(cause)
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setRaiseInst(raiseBuilder)
                .build()
        )
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
                val local = PIRValueProto.newBuilder()
                    .setLocal(PIRLocalProto.newBuilder().setName(scope.resolveLocal(expr.nameExpr.name)))
                    .build()
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setDeleteLocal(PIRDeleteLocalProto.newBuilder().setLocal(local))
                        .build()
                )
            }
            expr.hasMemberExpr() -> {
                val obj = exprLowering.accept(expr.memberExpr.expr)
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setDeleteAttr(
                            PIRDeleteAttrProto.newBuilder()
                                .setObject(obj)
                                .setAttribute(expr.memberExpr.name)
                        )
                        .build()
                )
            }
            expr.hasIndexExpr() -> {
                val obj = exprLowering.accept(expr.indexExpr.base)
                val index = exprLowering.accept(expr.indexExpr.index)
                emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setDeleteSubscript(
                            PIRDeleteSubscriptProto.newBuilder()
                                .setObject(obj)
                                .setIndex(index)
                        )
                        .build()
                )
            }
            expr.hasTupleExpr() -> {
                for (item in expr.tupleExpr.itemsList) {
                    visitDelExpr(item, line)
                }
            }
        }
    }

    // ─── Nested FuncDef ──────────────────────────────────

    private fun visitNestedFuncDef(funcDef: MypyFuncDefProto, line: Int) {
        val mb = moduleBuilder ?: return

        // Extract the nested function as a module-level function (same pattern as lambdas)
        val (uniqueName, _) = mb.extractNestedFunction(funcDef, currentFunctionQualifiedName)

        // Emit assignment: funcname = GlobalRef(uniqueName)
        // Use uniqueName to avoid collisions between inner functions with same name
        val ref = PIRValueProto.newBuilder()
            .setGlobalRef(
                PIRGlobalRefProto.newBuilder()
                    .setName(uniqueName)
                    .setModule(mb.moduleName)
            )
            .build()
        val targetName = scope.resolveLocal(funcDef.name)
        val target = PIRValueProto.newBuilder()
            .setLocal(PIRLocalProto.newBuilder().setName(targetName))
            .build()
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(ref))
                .build()
        )
    }

    // ─── Assert ────────────────────────────────────────────

    private fun visitAssert(stmt: MypyAssertStmtProto, line: Int) {
        val cond = exprLowering.accept(stmt.expr)
        val passBlock = newBlock()
        val failBlock = newBlock()
        emitBranch(cond, passBlock, failBlock, line)

        activate(failBlock)
        var exc: PIRValueProto = PIRValueProto.newBuilder()
            .setGlobalRef(
                PIRGlobalRefProto.newBuilder()
                    .setName("AssertionError")
                    .setModule("builtins")
            )
            .build()

        if (stmt.hasMsg() && stmt.msg.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            val msgVal = exprLowering.accept(stmt.msg)
            val callTarget = newTempValue()
            emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setCall(
                        PIRCallProto.newBuilder()
                            .setTarget(callTarget)
                            .setCallee(exc)
                            .addArgs(
                                PIRCallArgProto.newBuilder()
                                    .setValue(msgVal)
                                    .setKind(CallArgKind.POSITIONAL)
                            )
                    )
                    .build()
            )
            exc = callTarget
        }
        emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setRaiseInst(PIRRaiseProto.newBuilder().setException(exc))
                .build()
        )

        activate(passBlock)
    }

    // ─── Helpers ───────────────────────────────────────────

    private fun resolveExceptTypes(typeExpr: MypyExprProto): List<PIRTypeProto> {
        val result = mutableListOf<PIRTypeProto>()
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
                result.add(
                    PIRTypeProto.newBuilder()
                        .setClassType(PIRClassTypeProto.newBuilder().setQualifiedName(fullname))
                        .build()
                )
            }
            typeExpr.hasMemberExpr() -> {
                var fullname = typeExpr.memberExpr.fullname
                if (fullname.isBlank()) {
                    fullname = typeExpr.memberExpr.name
                }
                result.add(
                    PIRTypeProto.newBuilder()
                        .setClassType(PIRClassTypeProto.newBuilder().setQualifiedName(fullname))
                        .build()
                )
            }
            else -> {
                result.add(
                    PIRTypeProto.newBuilder()
                        .setClassType(
                            PIRClassTypeProto.newBuilder()
                                .setQualifiedName("builtins.Exception")
                        )
                        .build()
                )
            }
        }
        return result
    }
}
