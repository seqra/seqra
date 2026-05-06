package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.FunctionLowering
import org.opentaint.ir.impl.python.proto.*

/**
 * Expression-level lowering. Extension functions on [CfgSession]; mirrors the
 * structure of mypy's `expression_visitor.py`.
 *
 * Each function returns a [FlatValue] and may emit instructions via the
 * receiver. Unlike the old `ExpressionLowering` class, there is no back-ref
 * to a CFG builder — the receiver *is* the session.
 */

internal val BIN_OP_MAP = mapOf(
    "+" to FlatBinaryOperator.ADD,
    "-" to FlatBinaryOperator.SUB,
    "*" to FlatBinaryOperator.MUL,
    "/" to FlatBinaryOperator.DIV,
    "//" to FlatBinaryOperator.FLOOR_DIV,
    "%" to FlatBinaryOperator.MOD,
    "**" to FlatBinaryOperator.POW,
    "@" to FlatBinaryOperator.MAT_MUL,
    "&" to FlatBinaryOperator.BIT_AND,
    "|" to FlatBinaryOperator.BIT_OR,
    "^" to FlatBinaryOperator.BIT_XOR,
    "<<" to FlatBinaryOperator.LSHIFT,
    ">>" to FlatBinaryOperator.RSHIFT,
)

private val UNARY_OP_MAP = mapOf(
    "-" to FlatUnaryOperator.NEG,
    "+" to FlatUnaryOperator.POS,
    "not" to FlatUnaryOperator.NOT,
    "~" to FlatUnaryOperator.INVERT,
)

private val COMPARE_OP_MAP = mapOf(
    "==" to FlatCompareOperator.EQ,
    "!=" to FlatCompareOperator.NE,
    "<" to FlatCompareOperator.LT,
    "<=" to FlatCompareOperator.LE,
    ">" to FlatCompareOperator.GT,
    ">=" to FlatCompareOperator.GE,
    "is" to FlatCompareOperator.IS,
    "is not" to FlatCompareOperator.IS_NOT,
    "in" to FlatCompareOperator.IN,
    "not in" to FlatCompareOperator.NOT_IN,
)

internal fun CfgSession.lowerExpr(expr: MypyExprProto): FlatValue = when (expr.kindCase) {
    MypyExprProto.KindCase.INT_EXPR -> intConst(expr.intExpr)
    MypyExprProto.KindCase.STR_EXPR -> FlatStrConst(expr.strExpr.value)
    MypyExprProto.KindCase.FLOAT_EXPR -> FlatFloatConst(expr.floatExpr.value)
    MypyExprProto.KindCase.BYTES_EXPR -> FlatBytesConst(expr.bytesExpr.value.toByteArray())
    MypyExprProto.KindCase.COMPLEX_EXPR -> FlatComplexConst(expr.complexExpr.real, expr.complexExpr.imag)
    MypyExprProto.KindCase.ELLIPSIS_EXPR -> FlatEllipsisConst
    MypyExprProto.KindCase.NAME_EXPR -> lowerName(expr.nameExpr)
    MypyExprProto.KindCase.MEMBER_EXPR -> lowerMember(expr.memberExpr, expr.line)
    MypyExprProto.KindCase.CALL_EXPR -> lowerCall(expr.callExpr, expr.line)
    MypyExprProto.KindCase.OP_EXPR -> lowerOp(expr.opExpr, expr.line)
    MypyExprProto.KindCase.UNARY_EXPR -> lowerUnary(expr.unaryExpr, expr.line)
    MypyExprProto.KindCase.COMPARISON_EXPR -> lowerComparison(expr.comparisonExpr, expr.line)
    MypyExprProto.KindCase.INDEX_EXPR -> lowerIndex(expr.indexExpr, expr.line)
    MypyExprProto.KindCase.SLICE_EXPR -> lowerSlice(expr.sliceExpr, expr.line)
    MypyExprProto.KindCase.LIST_EXPR -> lowerListExpr(expr.listExpr, expr.line)
    MypyExprProto.KindCase.TUPLE_EXPR -> lowerTupleExpr(expr.tupleExpr, expr.line)
    MypyExprProto.KindCase.SET_EXPR -> lowerSetExpr(expr.setExpr, expr.line)
    MypyExprProto.KindCase.DICT_EXPR -> lowerDictExpr(expr.dictExpr, expr.line)
    MypyExprProto.KindCase.CONDITIONAL_EXPR -> lowerConditional(expr.conditionalExpr, expr.line)
    MypyExprProto.KindCase.STAR_EXPR -> lowerExpr(expr.starExpr.expr)
    MypyExprProto.KindCase.YIELD_EXPR -> lowerYield(expr.yieldExpr, expr.line)
    MypyExprProto.KindCase.YIELD_FROM_EXPR -> lowerYieldFrom(expr.yieldFromExpr, expr.line)
    MypyExprProto.KindCase.AWAIT_EXPR -> lowerAwait(expr.awaitExpr, expr.line)
    MypyExprProto.KindCase.ASSIGNMENT_EXPR -> lowerWalrus(expr.assignmentExpr, expr.line)
    MypyExprProto.KindCase.LAMBDA_EXPR -> lowerLambda(expr.lambdaExpr)
    MypyExprProto.KindCase.SUPER_EXPR -> lowerSuper(expr.line)
    MypyExprProto.KindCase.LIST_COMPREHENSION -> lowerComprehension(expr.listComprehension.generator, CollectionKind.LIST, expr.line)
    MypyExprProto.KindCase.SET_COMPREHENSION -> lowerComprehension(expr.setComprehension.generator, CollectionKind.SET, expr.line)
    MypyExprProto.KindCase.DICT_COMPREHENSION -> lowerDictComprehension(expr.dictComprehension, expr.line)
    MypyExprProto.KindCase.GENERATOR_EXPR -> lowerComprehension(expr.generatorExpr, CollectionKind.LIST, expr.line)
    else -> FlatNoneConst
}

// ─── Constants ────────────────────────────────────────

private fun intConst(proto: MypyIntExprProto): FlatValue =
    if (proto.strValue.isNotEmpty()) FlatStrConst(proto.strValue) else FlatIntConst(proto.value)

// ─── Names & Attributes ──────────────────────────────

private fun CfgSession.lowerName(expr: MypyNameExprProto): FlatValue {
    val name = expr.name

    when (name) {
        "True" -> return FlatBoolConst(true)
        "False" -> return FlatBoolConst(false)
        "None" -> return FlatNoneConst
    }

    return when (expr.nameKind) {
        // Module imports — `import os`, `import os.path as p`, plain
        // `import os.path` (which binds `os`). mypy stores the canonical
        // module fullname in `fullname`; `name` may be an alias which we
        // discard (downstream consumers match against canonical paths).
        MypyNameKind.NAME_MODULE -> FlatModuleRef(expr.fullname.ifEmpty { name })
        // Module-level / imported / builtin values. mypy populates a dotted
        // canonical fullname for every GDEF binding (`pkg.x`, `os.getcwd`,
        // `builtins.print`); aliases like `from m import x as y` already
        // arrive with the canonical fullname `m.x`, so no extra alias
        // handling is needed.
        MypyNameKind.NAME_GLOBAL -> {
            val fullname = expr.fullname
            check('.' in fullname) {
                "NAME_GLOBAL fullname must be dotted; got '$fullname' for name '$name'"
            }
            FlatGlobalRef(fullname)
        }
        else -> FlatLocal(scope.resolveLocal(name))
    }
}

private fun CfgSession.lowerMember(expr: MypyMemberExprProto, line: Int): FlatValue {
    val obj = lowerExpr(expr.expr)
    val target = newTempValue()
    emit(FlatLoadAttr(target, obj, expr.name, line = line))
    return target
}

// ─── Operators ────────────────────────────────────────

private fun CfgSession.lowerOp(expr: MypyOpExprProto, line: Int): FlatValue {
    val op = expr.op
    return when {
        op in BIN_OP_MAP -> {
            val left = lowerExpr(expr.left)
            val right = lowerExpr(expr.right)
            val target = newTempValue()
            emit(FlatBinOp(target, left, right, BIN_OP_MAP.getValue(op), line = line))
            target
        }
        op == "and" -> lowerShortCircuit(expr, isAnd = true, line)
        op == "or" -> lowerShortCircuit(expr, isAnd = false, line)
        else -> FlatNoneConst
    }
}

private fun CfgSession.lowerShortCircuit(expr: MypyOpExprProto, isAnd: Boolean, line: Int): FlatValue {
    val left = lowerExpr(expr.left)
    val target = newTempValue()
    emit(FlatAssign(target, left, line = line))

    val evalRight = newBlock()
    val endBlock = newBlock()

    if (isAnd) emitBranch(target, evalRight, endBlock, line)
    else emitBranch(target, endBlock, evalRight, line)

    activate(evalRight)
    val right = lowerExpr(expr.right)
    emit(FlatAssign(target, right, line = line))
    emitGoto(endBlock, line)
    activate(endBlock)
    return target
}

private fun CfgSession.lowerUnary(expr: MypyUnaryExprProto, line: Int): FlatValue {
    val operand = lowerExpr(expr.expr)
    val target = newTempValue()
    val op = UNARY_OP_MAP[expr.op] ?: FlatUnaryOperator.NEG
    emit(FlatUnaryOp(target, operand, op, line = line))
    return target
}

private fun CfgSession.lowerComparison(expr: MypyComparisonExprProto, line: Int): FlatValue {
    if (expr.operatorsCount == 1) {
        val left = lowerExpr(expr.getOperands(0))
        val right = lowerExpr(expr.getOperands(1))
        val target = newTempValue()
        val op = COMPARE_OP_MAP[expr.getOperators(0)] ?: FlatCompareOperator.EQ
        emit(FlatCompare(target, left, right, op, line = line))
        return target
    }

    // Chained comparison: `a < b < c` short-circuits as `a<b and b<c`.
    val resultVar = newTempValue()
    val endBlock = newBlock()

    var prevRight = lowerExpr(expr.getOperands(0))
    for (i in 0 until expr.operatorsCount) {
        val nextRight = lowerExpr(expr.getOperands(i + 1))
        val cmpTarget = newTempValue()
        val op = COMPARE_OP_MAP[expr.getOperators(i)] ?: FlatCompareOperator.EQ
        emit(FlatCompare(cmpTarget, prevRight, nextRight, op, line = line))
        emit(FlatAssign(resultVar, cmpTarget, line = line))

        if (i < expr.operatorsCount - 1) {
            val nextCmp = newBlock()
            emitBranch(cmpTarget, nextCmp, endBlock, line)
            activate(nextCmp)
        }
        prevRight = nextRight
    }
    emitGoto(endBlock, line)
    activate(endBlock)
    return resultVar
}

// ─── Call ─────────────────────────────────────────────

private fun CfgSession.lowerCall(expr: MypyCallExprProto, line: Int): FlatValue {
    val callee = lowerExpr(expr.callee)
    val args = expr.argsList.map { arg ->
        val argVal = lowerExpr(arg.expr)
        val kind = when (arg.kind) {
            2 -> FlatArgKind.STAR            // ARG_STAR
            4 -> FlatArgKind.DOUBLE_STAR     // ARG_STAR2
            3, 5 -> FlatArgKind.KEYWORD      // ARG_NAMED=3, ARG_NAMED_OPT=5
            else -> FlatArgKind.POSITIONAL   // ARG_POS, ARG_OPT
        }
        FlatCallArg(argVal, kind, arg.name.ifEmpty { null })
    }

    val resolvedCallee = resolveCallee(expr)

    val target = newTempValue()
    emit(FlatCall(target, callee, args, resolvedCallee, line = line))
    return target
}

/**
 * Resolve the qualified name of a call's target. Tries, in order:
 *   1. mypy's pre-resolved [MypyCallExprProto.resolvedCallee];
 *   2. the callee's [MypyMemberExprProto.fullname] (instance/class method calls
 *      where mypy doesn't set node.fullname on the CallExpr);
 *   3. composing from the receiver's static type, e.g. `data.upper()` on
 *      `data: str` → `builtins.str.upper`.
 */
private fun resolveCallee(expr: MypyCallExprProto): String? {
    expr.resolvedCallee.ifEmpty { null }?.let { return it }

    if (!expr.callee.hasMemberExpr()) return null
    val member = expr.callee.memberExpr
    member.fullname.ifEmpty { null }?.let { return it }

    val receiverType = member.expr.exprType
    if (receiverType.hasClassType()) {
        return "${receiverType.classType.qualifiedName}.${member.name}"
    }
    return null
}

// ─── Subscript & Slice ───────────────────────────────

private fun CfgSession.lowerIndex(expr: MypyIndexExprProto, line: Int): FlatValue {
    val obj = lowerExpr(expr.base)
    val index = lowerExpr(expr.index)
    val target = newTempValue()
    emit(FlatLoadSubscript(target, obj, index, line = line))
    return target
}

private fun CfgSession.lowerSlice(expr: MypySliceExprProto, line: Int): FlatValue {
    val target = newTempValue()
    val lower = if (expr.hasBegin()) lowerExpr(expr.begin) else null
    val upper = if (expr.hasEnd()) lowerExpr(expr.end) else null
    val step = if (expr.hasStride()) lowerExpr(expr.stride) else null
    emit(FlatBuildSlice(target, lower, upper, step, line = line))
    return target
}

// ─── Collection literals ─────────────────────────────

private fun CfgSession.lowerListExpr(expr: MypyListExprProto, line: Int): FlatValue {
    val elements = expr.itemsList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildList(target, elements, line = line))
    return target
}

private fun CfgSession.lowerTupleExpr(expr: MypyTupleExprProto, line: Int): FlatValue {
    val elements = expr.itemsList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildTuple(target, elements, line = line))
    return target
}

private fun CfgSession.lowerSetExpr(expr: MypySetExprProto, line: Int): FlatValue {
    val elements = expr.itemsList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildSet(target, elements, line = line))
    return target
}

private fun CfgSession.lowerDictExpr(expr: MypyDictExprProto, line: Int): FlatValue {
    val keys = expr.keysList.map {
        if (it.kindCase == MypyExprProto.KindCase.KIND_NOT_SET) FlatNoneConst else lowerExpr(it)
    }
    val values = expr.valuesList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildDict(target, keys, values, line = line))
    return target
}

// ─── Conditional expression ─────────────────────────

private fun CfgSession.lowerConditional(expr: MypyConditionalExprProto, line: Int): FlatValue {
    val cond = lowerExpr(expr.cond)
    val target = newTempValue()
    val trueBlock = newBlock()
    val falseBlock = newBlock()
    val endBlock = newBlock()

    emitBranch(cond, trueBlock, falseBlock, line)

    activate(trueBlock)
    val trueVal = lowerExpr(expr.ifExpr)
    emit(FlatAssign(target, trueVal))
    emitGoto(endBlock)

    activate(falseBlock)
    val falseVal = lowerExpr(expr.elseExpr)
    emit(FlatAssign(target, falseVal))
    emitGoto(endBlock)

    activate(endBlock)
    return target
}

// ─── Generators & Async ─────────────────────────────

private fun CfgSession.lowerYield(expr: MypyYieldExprProto, line: Int): FlatValue {
    val value = if (expr.hasExpr() && expr.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(expr.expr)
    } else FlatNoneConst
    val target = newTempValue()
    emit(FlatYield(target, value, line = line))
    return target
}

private fun CfgSession.lowerYieldFrom(expr: MypyYieldFromExprProto, line: Int): FlatValue {
    val iterable = lowerExpr(expr.expr)
    val target = newTempValue()
    emit(FlatYieldFrom(target, iterable, line = line))
    return target
}

private fun CfgSession.lowerAwait(expr: MypyAwaitExprProto, line: Int): FlatValue {
    val awaitable = lowerExpr(expr.expr)
    val target = newTempValue()
    emit(FlatAwait(target, awaitable, line = line))
    return target
}

// ─── Walrus ─────────────────────────────────────────

private fun CfgSession.lowerWalrus(expr: MypyAssignmentExprProto, line: Int): FlatValue {
    val value = lowerExpr(expr.value)
    val targetName = if (expr.target.hasNameExpr()) {
        scope.resolveLocal(expr.target.nameExpr.name)
    } else {
        scope.newTemp()
    }
    val target = FlatLocal(targetName)
    emit(FlatAssign(target, value, line = line))
    return target
}

// ─── Lambda ─────────────────────────────────────────

private fun CfgSession.lowerLambda(expr: MypyLambdaExprProto): FlatValue {
    val lambda = FunctionLowering.lowerLambda(
        module = module,
        expr = expr,
        parentQualifiedName = currentFunctionQualifiedName,
    )
    module.register(lambda)
    val ref = FlatGlobalRef(lambda.qualifiedName)
    val target = newTempValue()
    emit(FlatBindFunction(target, ref, line = -1))
    return target
}

// ─── Super ───────────────────────��──────────────────

private fun CfgSession.lowerSuper(line: Int): FlatValue {
    val target = newTempValue()
    emit(FlatCall(target, FlatGlobalRef("builtins.super"), line = line))
    return target
}

// ─── Comprehensions ─────────────────────────────────

private enum class CollectionKind { LIST, SET }

private fun CfgSession.lowerComprehension(
    gen: MypyGeneratorExprProto,
    collectionKind: CollectionKind,
    line: Int,
): FlatValue {
    val result = newTempValue()
    val addMethod: String

    when (collectionKind) {
        CollectionKind.LIST -> {
            emit(FlatBuildList(result, line = line))
            addMethod = "append"
        }
        CollectionKind.SET -> {
            emit(FlatCall(result, FlatGlobalRef("builtins.set"), line = line))
            addMethod = "add"
        }
    }

    emitComprehensionLoops(
        indices = gen.indicesList,
        sequences = gen.sequencesList,
        condlists = gen.condlistsList,
        line = line,
        loopIdx = 0,
    ) { emitCollectionAdd(result, gen.leftExpr, addMethod, line) }

    return result
}

private fun CfgSession.lowerDictComprehension(expr: MypyDictComprehensionProto, line: Int): FlatValue {
    val result = newTempValue()
    emit(FlatBuildDict(result, line = line))

    emitComprehensionLoops(
        indices = expr.indicesList,
        sequences = expr.sequencesList,
        condlists = expr.condlistsList,
        line = line,
        loopIdx = 0,
    ) { emitDictStore(result, expr.key, expr.value, line) }

    return result
}

/**
 * Emit nested for-loops for a comprehension. Each loop level has its own
 * iterator + header/body/exit blocks; conditions inside a level act as
 * `if cond: continue` between header and recursive body.
 */
private fun CfgSession.emitComprehensionLoops(
    indices: List<MypyExprProto>,
    sequences: List<MypyExprProto>,
    condlists: List<MypyCondListProto>,
    line: Int,
    loopIdx: Int,
    bodyCallback: () -> Unit,
) {
    if (loopIdx >= sequences.size) {
        bodyCallback()
        return
    }

    val iterableVal = lowerExpr(sequences[loopIdx])
    val iterVal = newTempValue()
    emit(FlatGetIter(iterVal, iterableVal, line = line))

    val headerBlock = newBlock()
    val bodyBlock = newBlock()
    val exitBlock = newBlock()

    emitGoto(headerBlock)
    activate(headerBlock)

    val idxExpr = indices[loopIdx]
    val targetVal = if (idxExpr.hasNameExpr()) {
        FlatLocal(scope.resolveLocal(idxExpr.nameExpr.name))
    } else {
        newTempValue()
    }

    emit(FlatNextIter(targetVal, iterVal, bodyBlock, exitBlock, line = line))

    activate(bodyBlock)
    if (idxExpr.hasTupleExpr()) {
        assignTo(idxExpr, targetVal, line)
    }

    val conditions = if (loopIdx < condlists.size) condlists[loopIdx].conditionsList else emptyList()
    for (condExpr in conditions) {
        val condVal = lowerExpr(condExpr)
        val skipBlock = newBlock()
        val continueBlock = newBlock()
        emitBranch(condVal, continueBlock, skipBlock, line)
        activate(skipBlock)
        emitGoto(headerBlock)
        activate(continueBlock)
    }

    emitComprehensionLoops(indices, sequences, condlists, line, loopIdx + 1, bodyCallback)

    if (!currentBlockTerminated()) emitGoto(headerBlock)

    activate(exitBlock)
}

private fun CfgSession.emitCollectionAdd(collection: FlatValue, valueExpr: MypyExprProto, method: String, line: Int) {
    val value = lowerExpr(valueExpr)
    val methodRef = newTempValue()
    emit(FlatLoadAttr(methodRef, collection, method, line = line))
    emit(FlatCall(null, methodRef, listOf(FlatCallArg(value, FlatArgKind.POSITIONAL)), line = line))
}

private fun CfgSession.emitDictStore(dictVal: FlatValue, keyExpr: MypyExprProto, valueExpr: MypyExprProto, line: Int) {
    val key = lowerExpr(keyExpr)
    val value = lowerExpr(valueExpr)
    emit(FlatStoreSubscript(dictVal, key, value, line = line))
}
