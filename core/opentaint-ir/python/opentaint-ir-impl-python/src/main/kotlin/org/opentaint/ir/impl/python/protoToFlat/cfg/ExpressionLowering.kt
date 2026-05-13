package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.api.python.PIRPhysicalLocation
import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.FunctionLowering
import org.opentaint.ir.impl.python.protoToFlat.toPhysicalLocation
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

internal fun CfgSession.lowerExpr(expr: MypyExprProto): FlatValue {
    val loc = expr.toPhysicalLocation()
    return when (expr.kindCase) {
        MypyExprProto.KindCase.INT_EXPR -> intConst(expr.intExpr)
        MypyExprProto.KindCase.STR_EXPR -> FlatStrConst(expr.strExpr.value)
        MypyExprProto.KindCase.FLOAT_EXPR -> FlatFloatConst(expr.floatExpr.value)
        MypyExprProto.KindCase.BYTES_EXPR -> FlatBytesConst(expr.bytesExpr.value.toByteArray())
        MypyExprProto.KindCase.COMPLEX_EXPR -> FlatComplexConst(expr.complexExpr.real, expr.complexExpr.imag)
        MypyExprProto.KindCase.ELLIPSIS_EXPR -> FlatEllipsisConst
        MypyExprProto.KindCase.NAME_EXPR -> lowerName(expr.nameExpr)
        MypyExprProto.KindCase.MEMBER_EXPR -> lowerMember(expr.memberExpr, loc)
        MypyExprProto.KindCase.CALL_EXPR -> lowerCall(expr.callExpr, loc)
        MypyExprProto.KindCase.OP_EXPR -> lowerOp(expr.opExpr, loc)
        MypyExprProto.KindCase.UNARY_EXPR -> lowerUnary(expr.unaryExpr, loc)
        MypyExprProto.KindCase.COMPARISON_EXPR -> lowerComparison(expr.comparisonExpr, loc)
        MypyExprProto.KindCase.INDEX_EXPR -> lowerIndex(expr.indexExpr, loc)
        MypyExprProto.KindCase.SLICE_EXPR -> lowerSlice(expr.sliceExpr, loc)
        MypyExprProto.KindCase.LIST_EXPR -> lowerListExpr(expr.listExpr, loc)
        MypyExprProto.KindCase.TUPLE_EXPR -> lowerTupleExpr(expr.tupleExpr, loc)
        MypyExprProto.KindCase.SET_EXPR -> lowerSetExpr(expr.setExpr, loc)
        MypyExprProto.KindCase.DICT_EXPR -> lowerDictExpr(expr.dictExpr, loc)
        MypyExprProto.KindCase.CONDITIONAL_EXPR -> lowerConditional(expr.conditionalExpr, loc)
        MypyExprProto.KindCase.STAR_EXPR -> lowerExpr(expr.starExpr.expr)
        MypyExprProto.KindCase.YIELD_EXPR -> lowerYield(expr.yieldExpr, loc)
        MypyExprProto.KindCase.YIELD_FROM_EXPR -> lowerYieldFrom(expr.yieldFromExpr, loc)
        MypyExprProto.KindCase.AWAIT_EXPR -> lowerAwait(expr.awaitExpr, loc)
        MypyExprProto.KindCase.ASSIGNMENT_EXPR -> lowerWalrus(expr.assignmentExpr, loc)
        MypyExprProto.KindCase.LAMBDA_EXPR -> lowerLambda(expr.lambdaExpr)
        MypyExprProto.KindCase.SUPER_EXPR -> lowerSuper(loc)
        MypyExprProto.KindCase.LIST_COMPREHENSION -> lowerComprehension(expr.listComprehension.generator, CollectionKind.LIST, loc)
        MypyExprProto.KindCase.SET_COMPREHENSION -> lowerComprehension(expr.setComprehension.generator, CollectionKind.SET, loc)
        MypyExprProto.KindCase.DICT_COMPREHENSION -> lowerDictComprehension(expr.dictComprehension, loc)
        MypyExprProto.KindCase.GENERATOR_EXPR -> lowerComprehension(expr.generatorExpr, CollectionKind.LIST, loc)
        else -> FlatNoneConst
    }
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
        //
        // Exception: a module-scope `import missing_pkg` where mypy can't
        // resolve `missing_pkg` falls through to `add_unknown_imported_symbol`,
        // which creates a GDEF binding with a scope-prefixed fullname
        // (`__test__.missing_pkg`) — looks dotted, but the dot is the
        // enclosing module path, not the import target. We detect this by
        // consulting the import-scope maps populated from `Import` /
        // `ImportFrom` statements; for genuinely resolved GDEF bindings the
        // maps don't contain the bound name, so the override is a no-op.
        MypyNameKind.NAME_GLOBAL -> {
            imports.resolve(name)?.let { return it }
            val fullname = expr.fullname
            check('.' in fullname) {
                "NAME_GLOBAL fullname must be dotted; got '$fullname' for name '$name'"
            }
            FlatGlobalRef(fullname)
        }
        else -> {
            // Function-scope suppressed imports surface as LDEF with a
            // single-segment fullname (or, after a rebind, a bound `Var`
            // whose canonical target was discarded by mypy). The import-scope
            // chain recovers the original canonical name; if there's no
            // entry, this is a real local.
            imports.resolve(name)?.let { return it }
            FlatLocal(scope.resolveLocal(name))
        }
    }
}

private fun CfgSession.lowerMember(expr: MypyMemberExprProto, location: PIRPhysicalLocation?): FlatValue {
    val obj = lowerExpr(expr.expr)
    val target = newTempValue()
    emit(FlatLoadAttr(target, obj, expr.name, physicalLocation = location))
    return target
}

// ─── Operators ────────────────────────────────────────

private fun CfgSession.lowerOp(expr: MypyOpExprProto, location: PIRPhysicalLocation?): FlatValue {
    val op = expr.op
    return when {
        op in BIN_OP_MAP -> {
            val left = lowerExpr(expr.left)
            val right = lowerExpr(expr.right)
            val target = newTempValue()
            emit(FlatBinOp(target, left, right, BIN_OP_MAP.getValue(op), physicalLocation = location))
            target
        }
        op == "and" -> lowerShortCircuit(expr, isAnd = true, location)
        op == "or" -> lowerShortCircuit(expr, isAnd = false, location)
        else -> FlatNoneConst
    }
}

private fun CfgSession.lowerShortCircuit(expr: MypyOpExprProto, isAnd: Boolean, location: PIRPhysicalLocation?): FlatValue {
    val left = lowerExpr(expr.left)
    val target = newTempValue()
    emit(FlatAssign(target, left, physicalLocation = location))

    val evalRight = newBlock()
    val endBlock = newBlock()

    if (isAnd) emitBranch(target, evalRight, endBlock, location)
    else emitBranch(target, endBlock, evalRight, location)

    activate(evalRight)
    val right = lowerExpr(expr.right)
    emit(FlatAssign(target, right, physicalLocation = location))
    emitGoto(endBlock, location)
    activate(endBlock)
    return target
}

private fun CfgSession.lowerUnary(expr: MypyUnaryExprProto, location: PIRPhysicalLocation?): FlatValue {
    val operand = lowerExpr(expr.expr)
    val target = newTempValue()
    val op = UNARY_OP_MAP[expr.op] ?: FlatUnaryOperator.NEG
    emit(FlatUnaryOp(target, operand, op, physicalLocation = location))
    return target
}

private fun CfgSession.lowerComparison(expr: MypyComparisonExprProto, location: PIRPhysicalLocation?): FlatValue {
    if (expr.operatorsCount == 1) {
        val left = lowerExpr(expr.getOperands(0))
        val right = lowerExpr(expr.getOperands(1))
        val target = newTempValue()
        val op = COMPARE_OP_MAP[expr.getOperators(0)] ?: FlatCompareOperator.EQ
        emit(FlatCompare(target, left, right, op, physicalLocation = location))
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
        emit(FlatCompare(cmpTarget, prevRight, nextRight, op, physicalLocation = location))
        emit(FlatAssign(resultVar, cmpTarget, physicalLocation = location))

        if (i < expr.operatorsCount - 1) {
            val nextCmp = newBlock()
            emitBranch(cmpTarget, nextCmp, endBlock, location)
            activate(nextCmp)
        }
        prevRight = nextRight
    }
    emitGoto(endBlock, location)
    activate(endBlock)
    return resultVar
}

// ─── Call ─────────────────────────────────────────────

private fun CfgSession.lowerCall(expr: MypyCallExprProto, location: PIRPhysicalLocation?): FlatValue {
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
    emit(FlatCall(target, callee, args, resolvedCallee, physicalLocation = location))
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

private fun CfgSession.lowerIndex(expr: MypyIndexExprProto, location: PIRPhysicalLocation?): FlatValue {
    val obj = lowerExpr(expr.base)
    val index = lowerExpr(expr.index)
    val target = newTempValue()
    emit(FlatLoadSubscript(target, obj, index, physicalLocation = location))
    return target
}

private fun CfgSession.lowerSlice(expr: MypySliceExprProto, location: PIRPhysicalLocation?): FlatValue {
    val target = newTempValue()
    val lower = if (expr.hasBegin()) lowerExpr(expr.begin) else null
    val upper = if (expr.hasEnd()) lowerExpr(expr.end) else null
    val step = if (expr.hasStride()) lowerExpr(expr.stride) else null
    emit(FlatBuildSlice(target, lower, upper, step, physicalLocation = location))
    return target
}

// ─── Collection literals ─────────────────────────────

private fun CfgSession.lowerListExpr(expr: MypyListExprProto, location: PIRPhysicalLocation?): FlatValue {
    val elements = expr.itemsList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildList(target, elements, physicalLocation = location))
    return target
}

private fun CfgSession.lowerTupleExpr(expr: MypyTupleExprProto, location: PIRPhysicalLocation?): FlatValue {
    val elements = expr.itemsList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildTuple(target, elements, physicalLocation = location))
    return target
}

private fun CfgSession.lowerSetExpr(expr: MypySetExprProto, location: PIRPhysicalLocation?): FlatValue {
    val elements = expr.itemsList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildSet(target, elements, physicalLocation = location))
    return target
}

private fun CfgSession.lowerDictExpr(expr: MypyDictExprProto, location: PIRPhysicalLocation?): FlatValue {
    val keys = expr.keysList.map {
        if (it.kindCase == MypyExprProto.KindCase.KIND_NOT_SET) FlatNoneConst else lowerExpr(it)
    }
    val values = expr.valuesList.map { lowerExpr(it) }
    val target = newTempValue()
    emit(FlatBuildDict(target, keys, values, physicalLocation = location))
    return target
}

// ─── Conditional expression ─────────────────────────

private fun CfgSession.lowerConditional(expr: MypyConditionalExprProto, location: PIRPhysicalLocation?): FlatValue {
    val cond = lowerExpr(expr.cond)
    val target = newTempValue()
    val trueBlock = newBlock()
    val falseBlock = newBlock()
    val endBlock = newBlock()

    emitBranch(cond, trueBlock, falseBlock, location)

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

private fun CfgSession.lowerYield(expr: MypyYieldExprProto, location: PIRPhysicalLocation?): FlatValue {
    val value = if (expr.hasExpr() && expr.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
        lowerExpr(expr.expr)
    } else FlatNoneConst
    val target = newTempValue()
    emit(FlatYield(target, value, physicalLocation = location))
    return target
}

private fun CfgSession.lowerYieldFrom(expr: MypyYieldFromExprProto, location: PIRPhysicalLocation?): FlatValue {
    val iterable = lowerExpr(expr.expr)
    val target = newTempValue()
    emit(FlatYieldFrom(target, iterable, physicalLocation = location))
    return target
}

private fun CfgSession.lowerAwait(expr: MypyAwaitExprProto, location: PIRPhysicalLocation?): FlatValue {
    val awaitable = lowerExpr(expr.expr)
    val target = newTempValue()
    emit(FlatAwait(target, awaitable, physicalLocation = location))
    return target
}

// ─── Walrus ─────────────────────────────────────────

private fun CfgSession.lowerWalrus(expr: MypyAssignmentExprProto, location: PIRPhysicalLocation?): FlatValue {
    val value = lowerExpr(expr.value)
    val targetName = if (expr.target.hasNameExpr()) {
        scope.resolveLocal(expr.target.nameExpr.name)
    } else {
        scope.newTemp()
    }
    val target = FlatLocal(targetName)
    emit(FlatAssign(target, value, physicalLocation = location))
    return target
}

// ─── Lambda ─────────────────────────────────────────

private fun CfgSession.lowerLambda(expr: MypyLambdaExprProto): FlatValue {
    val lambda = FunctionLowering.lowerLambda(
        module = module,
        expr = expr,
        parentQualifiedName = currentFunctionQualifiedName,
        enclosingImports = imports,
    )
    module.register(lambda)
    val ref = FlatGlobalRef(lambda.qualifiedName)
    val target = newTempValue()
    emit(FlatBindFunction(target, ref, physicalLocation = null))
    return target
}

// ─── Super ──────────────────────────────────────────

private fun CfgSession.lowerSuper(location: PIRPhysicalLocation?): FlatValue {
    val target = newTempValue()
    emit(FlatCall(target, FlatGlobalRef("builtins.super"), physicalLocation = location))
    return target
}

// ─── Comprehensions ─────────────────────────────────

private enum class CollectionKind { LIST, SET }

private fun CfgSession.lowerComprehension(
    gen: MypyGeneratorExprProto,
    collectionKind: CollectionKind,
    location: PIRPhysicalLocation?,
): FlatValue {
    val result = newTempValue()
    val addMethod: String

    when (collectionKind) {
        CollectionKind.LIST -> {
            emit(FlatBuildList(result, physicalLocation = location))
            addMethod = "append"
        }
        CollectionKind.SET -> {
            emit(FlatCall(result, FlatGlobalRef("builtins.set"), physicalLocation = location))
            addMethod = "add"
        }
    }

    emitComprehensionLoops(
        indices = gen.indicesList,
        sequences = gen.sequencesList,
        condlists = gen.condlistsList,
        location = location,
        loopIdx = 0,
    ) { emitCollectionAdd(result, gen.leftExpr, addMethod, location) }

    return result
}

private fun CfgSession.lowerDictComprehension(expr: MypyDictComprehensionProto, location: PIRPhysicalLocation?): FlatValue {
    val result = newTempValue()
    emit(FlatBuildDict(result, physicalLocation = location))

    emitComprehensionLoops(
        indices = expr.indicesList,
        sequences = expr.sequencesList,
        condlists = expr.condlistsList,
        location = location,
        loopIdx = 0,
    ) { emitDictStore(result, expr.key, expr.value, location) }

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
    location: PIRPhysicalLocation?,
    loopIdx: Int,
    bodyCallback: () -> Unit,
) {
    if (loopIdx >= sequences.size) {
        bodyCallback()
        return
    }

    val iterableVal = lowerExpr(sequences[loopIdx])
    val iterVal = newTempValue()
    emit(FlatGetIter(iterVal, iterableVal, physicalLocation = location))

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

    emit(FlatNextIter(targetVal, iterVal, bodyBlock, exitBlock, physicalLocation = location))

    activate(bodyBlock)
    if (idxExpr.hasTupleExpr()) {
        assignTo(idxExpr, targetVal, location)
    }

    val conditions = if (loopIdx < condlists.size) condlists[loopIdx].conditionsList else emptyList()
    for (condExpr in conditions) {
        val condVal = lowerExpr(condExpr)
        val skipBlock = newBlock()
        val continueBlock = newBlock()
        emitBranch(condVal, continueBlock, skipBlock, location)
        activate(skipBlock)
        emitGoto(headerBlock)
        activate(continueBlock)
    }

    emitComprehensionLoops(indices, sequences, condlists, location, loopIdx + 1, bodyCallback)

    if (!currentBlockTerminated()) emitGoto(headerBlock)

    activate(exitBlock)
}

private fun CfgSession.emitCollectionAdd(collection: FlatValue, valueExpr: MypyExprProto, method: String, location: PIRPhysicalLocation?) {
    val value = lowerExpr(valueExpr)
    val methodRef = newTempValue()
    emit(FlatLoadAttr(methodRef, collection, method, physicalLocation = location))
    emit(FlatCall(null, methodRef, listOf(FlatCallArg(value, FlatArgKind.POSITIONAL)), physicalLocation = location))
}

private fun CfgSession.emitDictStore(dictVal: FlatValue, keyExpr: MypyExprProto, valueExpr: MypyExprProto, location: PIRPhysicalLocation?) {
    val key = lowerExpr(keyExpr)
    val value = lowerExpr(valueExpr)
    emit(FlatStoreSubscript(dictVal, key, value, physicalLocation = location))
}
