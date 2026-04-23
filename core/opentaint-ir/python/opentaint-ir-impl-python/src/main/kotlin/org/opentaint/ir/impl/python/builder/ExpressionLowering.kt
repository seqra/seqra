package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.impl.python.proto.MypyExprProto
import org.opentaint.ir.impl.python.proto.MypyIntExprProto
import org.opentaint.ir.impl.python.proto.MypyNameExprProto
import org.opentaint.ir.impl.python.proto.MypyMemberExprProto
import org.opentaint.ir.impl.python.proto.MypyOpExprProto
import org.opentaint.ir.impl.python.proto.MypyUnaryExprProto
import org.opentaint.ir.impl.python.proto.MypyComparisonExprProto
import org.opentaint.ir.impl.python.proto.MypyCallExprProto
import org.opentaint.ir.impl.python.proto.MypyIndexExprProto
import org.opentaint.ir.impl.python.proto.MypySliceExprProto
import org.opentaint.ir.impl.python.proto.MypyListExprProto
import org.opentaint.ir.impl.python.proto.MypyTupleExprProto
import org.opentaint.ir.impl.python.proto.MypySetExprProto
import org.opentaint.ir.impl.python.proto.MypyDictExprProto
import org.opentaint.ir.impl.python.proto.MypyConditionalExprProto
import org.opentaint.ir.impl.python.proto.MypyYieldExprProto
import org.opentaint.ir.impl.python.proto.MypyYieldFromExprProto
import org.opentaint.ir.impl.python.proto.MypyAwaitExprProto
import org.opentaint.ir.impl.python.proto.MypyAssignmentExprProto
import org.opentaint.ir.impl.python.proto.MypyLambdaExprProto
import org.opentaint.ir.impl.python.proto.MypyListComprehensionProto
import org.opentaint.ir.impl.python.proto.MypySetComprehensionProto
import org.opentaint.ir.impl.python.proto.MypyDictComprehensionProto
import org.opentaint.ir.impl.python.proto.MypyGeneratorExprProto
import org.opentaint.ir.impl.python.proto.MypyCondListProto

/**
 * Expression lowering: converts raw mypy AST expressions into PIR instructions.
 *
 * This is the Kotlin port of Python's expression_visitor.py.
 * Each method returns a [FlatValue] and may emit instructions via [cfgBuilder].
 */
class ExpressionLowering(private val cfgBuilder: CfgBuilder) {

    companion object {
        val BIN_OP_MAP = mapOf(
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

        val UNARY_OP_MAP = mapOf(
            "-" to FlatUnaryOperator.NEG,
            "+" to FlatUnaryOperator.POS,
            "not" to FlatUnaryOperator.NOT,
            "~" to FlatUnaryOperator.INVERT,
        )

        val COMPARE_OP_MAP = mapOf(
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
    }

    fun accept(expr: MypyExprProto): FlatValue {
        return when (expr.kindCase) {
            MypyExprProto.KindCase.INT_EXPR -> constInt(expr.intExpr)
            MypyExprProto.KindCase.STR_EXPR -> constStr(expr.strExpr.value)
            MypyExprProto.KindCase.FLOAT_EXPR -> constFloat(expr.floatExpr.value)
            MypyExprProto.KindCase.BYTES_EXPR -> constBytes(expr.bytesExpr.value.toByteArray())
            MypyExprProto.KindCase.COMPLEX_EXPR -> constComplex(expr.complexExpr.real, expr.complexExpr.imag)
            MypyExprProto.KindCase.ELLIPSIS_EXPR -> constEllipsis()
            MypyExprProto.KindCase.NAME_EXPR -> visitName(expr.nameExpr)
            MypyExprProto.KindCase.MEMBER_EXPR -> visitMember(expr.memberExpr, expr.line)
            MypyExprProto.KindCase.CALL_EXPR -> visitCall(expr.callExpr, expr.line)
            MypyExprProto.KindCase.OP_EXPR -> visitOp(expr.opExpr, expr.line)
            MypyExprProto.KindCase.UNARY_EXPR -> visitUnary(expr.unaryExpr, expr.line)
            MypyExprProto.KindCase.COMPARISON_EXPR -> visitComparison(expr.comparisonExpr, expr.line)
            MypyExprProto.KindCase.INDEX_EXPR -> visitIndex(expr.indexExpr, expr.line)
            MypyExprProto.KindCase.SLICE_EXPR -> visitSlice(expr.sliceExpr, expr.line)
            MypyExprProto.KindCase.LIST_EXPR -> visitList(expr.listExpr, expr.line)
            MypyExprProto.KindCase.TUPLE_EXPR -> visitTuple(expr.tupleExpr, expr.line)
            MypyExprProto.KindCase.SET_EXPR -> visitSet(expr.setExpr, expr.line)
            MypyExprProto.KindCase.DICT_EXPR -> visitDict(expr.dictExpr, expr.line)
            MypyExprProto.KindCase.CONDITIONAL_EXPR -> visitConditional(expr.conditionalExpr, expr.line)
            MypyExprProto.KindCase.STAR_EXPR -> accept(expr.starExpr.expr)
            MypyExprProto.KindCase.YIELD_EXPR -> visitYield(expr.yieldExpr, expr.line)
            MypyExprProto.KindCase.YIELD_FROM_EXPR -> visitYieldFrom(expr.yieldFromExpr, expr.line)
            MypyExprProto.KindCase.AWAIT_EXPR -> visitAwait(expr.awaitExpr, expr.line)
            MypyExprProto.KindCase.ASSIGNMENT_EXPR -> visitWalrus(expr.assignmentExpr, expr.line)
            MypyExprProto.KindCase.LAMBDA_EXPR -> visitLambda(expr.lambdaExpr, expr.line)
            MypyExprProto.KindCase.SUPER_EXPR -> visitSuper(expr.line)
            MypyExprProto.KindCase.LIST_COMPREHENSION -> visitListComprehension(expr.listComprehension, expr.line)
            MypyExprProto.KindCase.SET_COMPREHENSION -> visitSetComprehension(expr.setComprehension, expr.line)
            MypyExprProto.KindCase.DICT_COMPREHENSION -> visitDictComprehension(expr.dictComprehension, expr.line)
            MypyExprProto.KindCase.GENERATOR_EXPR -> visitGeneratorExpr(expr.generatorExpr, expr.line)
            else -> constNone()
        }
    }

    // ─── Constants ────────────────────────────────────────

    private fun constInt(proto: MypyIntExprProto): FlatValue {
        return if (proto.strValue.isNotEmpty()) {
            // Fall back to string for huge ints
            FlatStrConst(proto.strValue)
        } else {
            FlatIntConst(proto.value)
        }
    }

    private fun constFloat(value: Double): FlatValue = FlatFloatConst(value)

    private fun constStr(value: String): FlatValue = FlatStrConst(value)

    private fun constBytes(value: ByteArray): FlatValue = FlatBytesConst(value)

    private fun constComplex(real: Double, imag: Double): FlatValue = FlatComplexConst(real, imag)

    private fun constBool(value: Boolean): FlatValue = FlatBoolConst(value)

    fun constNone(): FlatValue = FlatNoneConst

    private fun constEllipsis(): FlatValue = FlatEllipsisConst

    // ─── Names & Attributes ──────────────────────────────

    private fun visitName(expr: MypyNameExprProto): FlatValue {
        val name = expr.name

        // Builtins
        if (name == "True") return constBool(true)
        if (name == "False") return constBool(false)
        if (name == "None") return constNone()

        // Global/imported reference
        if (expr.fullname.isNotEmpty() && "." in expr.fullname) {
            val module = expr.fullname.substringBeforeLast(".")
            return FlatGlobalRef(name, module)
        }

        // Local or parameter
        val resolved = cfgBuilder.scope.resolveLocal(name)
        return FlatLocal(resolved)
    }

    // Access the scope from CfgBuilder — we need it public
    private val scope get() = cfgBuilder.scope

    private fun visitMember(expr: MypyMemberExprProto, line: Int): FlatValue {
        val obj = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatLoadAttr(target, obj, expr.name, line = line))
        return target
    }

    // ─── Operators ────────────────────────────────────────

    private fun visitOp(expr: MypyOpExprProto, line: Int): FlatValue {
        val op = expr.op
        return when {
            op in BIN_OP_MAP -> {
                val left = accept(expr.left)
                val right = accept(expr.right)
                val target = cfgBuilder.newTempValue()
                cfgBuilder.emit(FlatBinOp(target, left, right, BIN_OP_MAP[op]!!, line = line))
                target
            }
            op == "and" -> visitShortCircuit(expr, isAnd = true, line)
            op == "or" -> visitShortCircuit(expr, isAnd = false, line)
            else -> constNone()
        }
    }

    private fun visitShortCircuit(expr: MypyOpExprProto, isAnd: Boolean, line: Int): FlatValue {
        val left = accept(expr.left)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatAssign(target, left, line = line))

        val evalRight = cfgBuilder.newBlock()
        val endBlock = cfgBuilder.newBlock()

        if (isAnd) {
            cfgBuilder.emitBranch(target, evalRight, endBlock, line)
        } else {
            cfgBuilder.emitBranch(target, endBlock, evalRight, line)
        }

        cfgBuilder.activate(evalRight)
        val right = accept(expr.right)
        cfgBuilder.emit(FlatAssign(target, right, line = line))
        cfgBuilder.emitGoto(endBlock, line)
        cfgBuilder.activate(endBlock)
        return target
    }

    private fun visitUnary(expr: MypyUnaryExprProto, line: Int): FlatValue {
        val operand = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        val op = UNARY_OP_MAP[expr.op] ?: FlatUnaryOperator.NEG
        cfgBuilder.emit(FlatUnaryOp(target, operand, op, line = line))
        return target
    }

    private fun visitComparison(expr: MypyComparisonExprProto, line: Int): FlatValue {
        if (expr.operatorsCount == 1) {
            val left = accept(expr.getOperands(0))
            val right = accept(expr.getOperands(1))
            val target = cfgBuilder.newTempValue()
            val op = COMPARE_OP_MAP[expr.getOperators(0)] ?: FlatCompareOperator.EQ
            cfgBuilder.emit(FlatCompare(target, left, right, op, line = line))
            return target
        }

        // Chained comparison: a < b < c → short-circuit AND
        // Evaluate each pair left-to-right; if any is false, skip remaining.
        val resultVar = cfgBuilder.newTempValue()
        val endBlock = cfgBuilder.newBlock()

        var prevRight = accept(expr.getOperands(0))
        for (i in 0 until expr.operatorsCount) {
            val nextRight = accept(expr.getOperands(i + 1))
            val cmpTarget = cfgBuilder.newTempValue()
            val op = COMPARE_OP_MAP[expr.getOperators(i)] ?: FlatCompareOperator.EQ
            cfgBuilder.emit(FlatCompare(cmpTarget, prevRight, nextRight, op, line = line))
            // Store into result
            cfgBuilder.emit(FlatAssign(resultVar, cmpTarget, line = line))

            if (i < expr.operatorsCount - 1) {
                // Short-circuit: if this comparison is false, skip remaining
                val nextCmp = cfgBuilder.newBlock()
                cfgBuilder.emitBranch(cmpTarget, nextCmp, endBlock, line)
                cfgBuilder.activate(nextCmp)
            }

            prevRight = nextRight
        }
        cfgBuilder.emitGoto(endBlock, line)
        cfgBuilder.activate(endBlock)
        return resultVar
    }

    // ─── Call ─────────────────────────────────────────────

    private fun visitCall(expr: MypyCallExprProto, line: Int): FlatValue {
        val callee = accept(expr.callee)
        val args = mutableListOf<FlatCallArg>()
        for (arg in expr.argsList) {
            val argVal = accept(arg.expr)
            val kind = when (arg.kind) {
                2 -> FlatArgKind.STAR            // ARG_STAR
                4 -> FlatArgKind.DOUBLE_STAR     // ARG_STAR2
                3, 5 -> FlatArgKind.KEYWORD      // ARG_NAMED=3, ARG_NAMED_OPT=5
                else -> FlatArgKind.POSITIONAL   // ARG_POS, ARG_OPT
            }
            args.add(FlatCallArg(argVal, kind, arg.name))
        }

        // Resolve callee qualified name: prefer CallExpr.resolvedCallee,
        // fall back to MemberExpr.fullname for instance/class method calls
        // where mypy doesn't set node.fullname on the CallExpr.
        var resolvedCallee = expr.resolvedCallee.ifEmpty {
            if (expr.callee.hasMemberExpr()) {
                expr.callee.memberExpr.fullname
            } else ""
        }
        // Second fallback: resolve from receiver's expression type.
        // For data.upper() where data:str, the receiver expr_type is ClassType("builtins.str"),
        // so we construct "builtins.str.upper".
        if (resolvedCallee.isEmpty() && expr.callee.hasMemberExpr()) {
            val memberExpr = expr.callee.memberExpr
            val receiverType = memberExpr.expr.exprType
            if (receiverType.hasClassType()) {
                resolvedCallee = "${receiverType.classType.qualifiedName}.${memberExpr.name}"
            }
        }

        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatCall(target, callee, args, resolvedCallee, line = line))
        return target
    }

    // ─── Subscript & Slice ───────────────────────────────

    private fun visitIndex(expr: MypyIndexExprProto, line: Int): FlatValue {
        val obj = accept(expr.base)
        val index = accept(expr.index)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatLoadSubscript(target, obj, index, line = line))
        return target
    }

    private fun visitSlice(expr: MypySliceExprProto, line: Int): FlatValue {
        val target = cfgBuilder.newTempValue()
        val lower = if (expr.hasBegin()) accept(expr.begin) else null
        val upper = if (expr.hasEnd()) accept(expr.end) else null
        val step = if (expr.hasStride()) accept(expr.stride) else null
        cfgBuilder.emit(FlatBuildSlice(target, lower, upper, step, line = line))
        return target
    }

    // ─── Collection literals ─────────────────────────────

    private fun visitList(expr: MypyListExprProto, line: Int): FlatValue {
        val elements = expr.itemsList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatBuildList(target, elements, line = line))
        return target
    }

    private fun visitTuple(expr: MypyTupleExprProto, line: Int): FlatValue {
        val elements = expr.itemsList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatBuildTuple(target, elements, line = line))
        return target
    }

    private fun visitSet(expr: MypySetExprProto, line: Int): FlatValue {
        val elements = expr.itemsList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatBuildSet(target, elements, line = line))
        return target
    }

    private fun visitDict(expr: MypyDictExprProto, line: Int): FlatValue {
        val keys = expr.keysList.map {
            if (it.kindCase == MypyExprProto.KindCase.KIND_NOT_SET) constNone() else accept(it)
        }
        val values = expr.valuesList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatBuildDict(target, keys, values, line = line))
        return target
    }

    // ─── Conditional expression ─────────────────────────

    private fun visitConditional(expr: MypyConditionalExprProto, line: Int): FlatValue {
        val cond = accept(expr.cond)
        val target = cfgBuilder.newTempValue()
        val trueBlock = cfgBuilder.newBlock()
        val falseBlock = cfgBuilder.newBlock()
        val endBlock = cfgBuilder.newBlock()

        cfgBuilder.emitBranch(cond, trueBlock, falseBlock, line)

        cfgBuilder.activate(trueBlock)
        val trueVal = accept(expr.ifExpr)
        cfgBuilder.emit(FlatAssign(target, trueVal))
        cfgBuilder.emitGoto(endBlock, -1)

        cfgBuilder.activate(falseBlock)
        val falseVal = accept(expr.elseExpr)
        cfgBuilder.emit(FlatAssign(target, falseVal))
        cfgBuilder.emitGoto(endBlock, -1)

        cfgBuilder.activate(endBlock)
        return target
    }

    // ─── Generators & Async ─────────────────────────────

    private fun visitYield(expr: MypyYieldExprProto, line: Int): FlatValue {
        val value = if (expr.hasExpr() && expr.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            accept(expr.expr)
        } else constNone()
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatYield(target, value, line = line))
        return target
    }

    private fun visitYieldFrom(expr: MypyYieldFromExprProto, line: Int): FlatValue {
        val iterable = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatYieldFrom(target, iterable, line = line))
        return target
    }

    private fun visitAwait(expr: MypyAwaitExprProto, line: Int): FlatValue {
        val awaitable = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatAwait(target, awaitable, line = line))
        return target
    }

    // ─── Walrus ─────────────────────────────────────────

    private fun visitWalrus(expr: MypyAssignmentExprProto, line: Int): FlatValue {
        val value = accept(expr.value)
        val targetName = if (expr.target.hasNameExpr()) {
            scope.resolveLocal(expr.target.nameExpr.name)
        } else {
            scope.newTemp()
        }
        val target = FlatLocal(targetName)
        cfgBuilder.emit(FlatAssign(target, value, line = line))
        return target
    }

    // ─── Lambda ─────────────────────────────────────────

    private fun visitLambda(expr: MypyLambdaExprProto, line: Int): FlatValue {
        val mb = cfgBuilder.moduleBuilder ?: return constNone()

        val idx = mb.lambdaCounter++
        val lambdaName = "<lambda>\$$idx"
        val qualifiedName = "${mb.moduleName}.$lambdaName"

        // Build the lambda's CFG using a fresh CfgBuilder
        val lambdaScope = ScopeStack()
        val lambdaCfg = CfgBuilder(lambdaScope, mb)
        val flatCfg = try {
            lambdaCfg.buildFunctionCfg(expr.body)
        } catch (e: Exception) {
            // Fallback: empty CFG
            FlatCFG.EMPTY
        }

        mb.addLambda(
            lambdaName,
            qualifiedName,
            cfgBuilder.currentFunctionQualifiedName.ifEmpty { null },
            flatCfg,
            expr.argumentsList,
            if (expr.hasReturnType()) expr.returnType else null,
        )

        return FlatGlobalRef(lambdaName, mb.moduleName)
    }

    // ─── Comprehensions ─────────────────────────────────

    private fun visitListComprehension(expr: MypyListComprehensionProto, line: Int): FlatValue =
        visitComprehensionAsLoop(expr.generator, "list", line)

    private fun visitSetComprehension(expr: MypySetComprehensionProto, line: Int): FlatValue =
        visitComprehensionAsLoop(expr.generator, "set", line)

    private fun visitGeneratorExpr(expr: MypyGeneratorExprProto, line: Int): FlatValue =
        visitComprehensionAsLoop(expr, "list", line)

    private fun visitDictComprehension(expr: MypyDictComprehensionProto, line: Int): FlatValue {
        val result = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatBuildDict(result, line = line))

        emitComprehensionLoops(
            indices = expr.indicesList,
            sequences = expr.sequencesList,
            condlists = expr.condlistsList,
            bodyCallback = { emitDictStore(result, expr.key, expr.value, line) },
            line = line,
            loopIdx = 0,
        )

        return result
    }

    private fun visitComprehensionAsLoop(
        gen: MypyGeneratorExprProto,
        collectionType: String,
        line: Int,
    ): FlatValue {
        val result = cfgBuilder.newTempValue()

        if (collectionType == "list") {
            cfgBuilder.emit(FlatBuildList(result, line = line))
        } else {
            // set() call
            val setRef = FlatGlobalRef("set", "builtins")
            cfgBuilder.emit(FlatCall(result, setRef, line = line))
        }

        val addMethod = if (collectionType == "list") "append" else "add"

        emitComprehensionLoops(
            indices = gen.indicesList,
            sequences = gen.sequencesList,
            condlists = gen.condlistsList,
            bodyCallback = { emitCollectionAdd(result, gen.leftExpr, addMethod, line) },
            line = line,
            loopIdx = 0,
        )

        return result
    }

    private fun emitComprehensionLoops(
        indices: List<MypyExprProto>,
        sequences: List<MypyExprProto>,
        condlists: List<MypyCondListProto>,
        bodyCallback: () -> Unit,
        line: Int,
        loopIdx: Int,
    ) {
        if (loopIdx >= sequences.size) {
            bodyCallback()
            return
        }

        val iterableVal = accept(sequences[loopIdx])
        val iterVal = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatGetIter(iterVal, iterableVal, line = line))

        val headerBlock = cfgBuilder.newBlock()
        val bodyBlock = cfgBuilder.newBlock()
        val exitBlock = cfgBuilder.newBlock()

        cfgBuilder.emitGoto(headerBlock)
        cfgBuilder.activate(headerBlock)

        // Loop variable
        val idxExpr = indices[loopIdx]
        val targetVal = if (idxExpr.hasNameExpr()) {
            FlatLocal(scope.resolveLocal(idxExpr.nameExpr.name))
        } else {
            cfgBuilder.newTempValue()
        }

        cfgBuilder.emit(FlatNextIter(targetVal, iterVal, bodyBlock, exitBlock, line = line))

        cfgBuilder.activate(bodyBlock)

        // Emit tuple unpacking if needed
        if (idxExpr.hasTupleExpr()) {
            cfgBuilder.assignTo(idxExpr, targetVal, line)
        }

        // Apply conditions
        val conditions = if (loopIdx < condlists.size) condlists[loopIdx].conditionsList else emptyList()
        for (condExpr in conditions) {
            val condVal = accept(condExpr)
            val skipBlock = cfgBuilder.newBlock()
            val continueBlock = cfgBuilder.newBlock()
            cfgBuilder.emitBranch(condVal, continueBlock, skipBlock, line)
            cfgBuilder.activate(skipBlock)
            cfgBuilder.emitGoto(headerBlock)
            cfgBuilder.activate(continueBlock)
        }

        // Recurse for inner loops
        emitComprehensionLoops(indices, sequences, condlists, bodyCallback, line, loopIdx + 1)

        if (!cfgBuilder.currentBlockTerminated()) {
            cfgBuilder.emitGoto(headerBlock)
        }

        cfgBuilder.activate(exitBlock)
    }

    private fun emitCollectionAdd(collection: FlatValue, valueExpr: MypyExprProto, method: String, line: Int) {
        val value = accept(valueExpr)
        val methodRef = cfgBuilder.newTempValue()
        cfgBuilder.emit(FlatLoadAttr(methodRef, collection, method, line = line))
        cfgBuilder.emit(
            FlatCall(null, methodRef, listOf(FlatCallArg(value, FlatArgKind.POSITIONAL)), line = line),
        )
    }

    private fun emitDictStore(dictVal: FlatValue, keyExpr: MypyExprProto, valueExpr: MypyExprProto, line: Int) {
        val key = accept(keyExpr)
        val value = accept(valueExpr)
        cfgBuilder.emit(FlatStoreSubscript(dictVal, key, value, line = line))
    }

    // ─── Super ──────────────────────────────────────────

    private fun visitSuper(line: Int): FlatValue {
        val target = cfgBuilder.newTempValue()
        val callee = FlatGlobalRef("super", "builtins")
        cfgBuilder.emit(FlatCall(target, callee, line = line))
        return target
    }
}
