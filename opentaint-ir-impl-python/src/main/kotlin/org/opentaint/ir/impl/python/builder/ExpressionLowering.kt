package org.opentaint.ir.impl.python.builder

import org.opentaint.ir.impl.python.proto.*

/**
 * Expression lowering: converts raw mypy AST expressions into PIR instructions.
 *
 * This is the Kotlin port of Python's expression_visitor.py.
 * Each method returns a PIRValueProto and may emit instructions via [cfgBuilder].
 */
class ExpressionLowering(private val cfgBuilder: CfgBuilder) {

    companion object {
        val BIN_OP_MAP = mapOf(
            "+" to BinaryOperator.ADD,
            "-" to BinaryOperator.SUB,
            "*" to BinaryOperator.MUL,
            "/" to BinaryOperator.DIV,
            "//" to BinaryOperator.FLOOR_DIV,
            "%" to BinaryOperator.MOD,
            "**" to BinaryOperator.POW,
            "@" to BinaryOperator.MAT_MUL,
            "&" to BinaryOperator.BIT_AND,
            "|" to BinaryOperator.BIT_OR,
            "^" to BinaryOperator.BIT_XOR,
            "<<" to BinaryOperator.LSHIFT,
            ">>" to BinaryOperator.RSHIFT,
        )

        val UNARY_OP_MAP = mapOf(
            "-" to UnaryOperator.NEG,
            "+" to UnaryOperator.POS,
            "not" to UnaryOperator.NOT,
            "~" to UnaryOperator.INVERT,
        )

        val COMPARE_OP_MAP = mapOf(
            "==" to CompareOperator.EQ,
            "!=" to CompareOperator.NE,
            "<" to CompareOperator.LT,
            "<=" to CompareOperator.LE,
            ">" to CompareOperator.GT,
            ">=" to CompareOperator.GE,
            "is" to CompareOperator.IS,
            "is not" to CompareOperator.IS_NOT,
            "in" to CompareOperator.IN,
            "not in" to CompareOperator.NOT_IN,
        )
    }

    fun accept(expr: MypyExprProto): PIRValueProto {
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

    private fun constInt(proto: MypyIntExprProto): PIRValueProto {
        return if (proto.strValue.isNotEmpty()) {
            // Fall back to string for huge ints
            PIRValueProto.newBuilder()
                .setConstVal(PIRConstProto.newBuilder().setStringValue(proto.strValue))
                .build()
        } else {
            PIRValueProto.newBuilder()
                .setConstVal(PIRConstProto.newBuilder().setIntValue(proto.value))
                .build()
        }
    }

    private fun constFloat(value: Double): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setFloatValue(value))
            .build()

    private fun constStr(value: String): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setStringValue(value))
            .build()

    private fun constBytes(value: ByteArray): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setBytesValue(com.google.protobuf.ByteString.copyFrom(value)))
            .build()

    private fun constComplex(real: Double, imag: Double): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setComplexReal(real).setComplexImag(imag))
            .build()

    private fun constBool(value: Boolean): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setBoolValue(value))
            .build()

    fun constNone(): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setNoneValue(true))
            .build()

    private fun constEllipsis(): PIRValueProto =
        PIRValueProto.newBuilder()
            .setConstVal(PIRConstProto.newBuilder().setEllipsisValue(true))
            .build()

    // ─── Names & Attributes ──────────────────────────────

    private fun visitName(expr: MypyNameExprProto): PIRValueProto {
        val name = expr.name

        // Builtins
        if (name == "True") return constBool(true)
        if (name == "False") return constBool(false)
        if (name == "None") return constNone()

        // Global/imported reference
        if (expr.fullname.isNotEmpty() && "." in expr.fullname) {
            val module = expr.fullname.substringBeforeLast(".")
            return PIRValueProto.newBuilder()
                .setGlobalRef(PIRGlobalRefProto.newBuilder().setName(name).setModule(module))
                .build()
        }

        // Local or parameter
        val resolved = cfgBuilder.scope.resolveLocal(name)
        return PIRValueProto.newBuilder()
            .setLocal(PIRLocalProto.newBuilder().setName(resolved))
            .build()
    }

    // Access the scope from CfgBuilder — we need it public
    private val scope get() = cfgBuilder.scope

    private fun visitMember(expr: MypyMemberExprProto, line: Int): PIRValueProto {
        val obj = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setLoadAttr(
                    PIRLoadAttrProto.newBuilder()
                        .setTarget(target)
                        .setObject(obj)
                        .setAttribute(expr.name)
                )
                .build()
        )
        return target
    }

    // ─── Operators ────────────────────────────────────────

    private fun visitOp(expr: MypyOpExprProto, line: Int): PIRValueProto {
        val op = expr.op
        return when {
            op in BIN_OP_MAP -> {
                val left = accept(expr.left)
                val right = accept(expr.right)
                val target = cfgBuilder.newTempValue()
                cfgBuilder.emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setBinOp(
                            PIRBinOpProto.newBuilder()
                                .setTarget(target)
                                .setLeft(left)
                                .setRight(right)
                                .setOp(BIN_OP_MAP[op]!!)
                        )
                        .build()
                )
                target
            }
            op == "and" -> visitShortCircuit(expr, isAnd = true, line)
            op == "or" -> visitShortCircuit(expr, isAnd = false, line)
            else -> constNone()
        }
    }

    private fun visitShortCircuit(expr: MypyOpExprProto, isAnd: Boolean, line: Int): PIRValueProto {
        val left = accept(expr.left)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(left))
                .build()
        )

        val evalRight = cfgBuilder.newBlock()
        val endBlock = cfgBuilder.newBlock()

        if (isAnd) {
            cfgBuilder.emitBranch(target, evalRight, endBlock, line)
        } else {
            cfgBuilder.emitBranch(target, endBlock, evalRight, line)
        }

        cfgBuilder.activate(evalRight)
        val right = accept(expr.right)
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(right))
                .build()
        )
        cfgBuilder.emitGoto(endBlock, line)
        cfgBuilder.activate(endBlock)
        return target
    }

    private fun visitUnary(expr: MypyUnaryExprProto, line: Int): PIRValueProto {
        val operand = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        val op = UNARY_OP_MAP[expr.op] ?: UnaryOperator.NEG
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setUnaryOp(
                    PIRUnaryOpProto.newBuilder()
                        .setTarget(target)
                        .setOperand(operand)
                        .setOp(op)
                )
                .build()
        )
        return target
    }

    private fun visitComparison(expr: MypyComparisonExprProto, line: Int): PIRValueProto {
        if (expr.operatorsCount == 1) {
            val left = accept(expr.getOperands(0))
            val right = accept(expr.getOperands(1))
            val target = cfgBuilder.newTempValue()
            val op = COMPARE_OP_MAP[expr.getOperators(0)] ?: CompareOperator.EQ
            cfgBuilder.emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setCompare(
                        PIRCompareProto.newBuilder()
                            .setTarget(target)
                            .setLeft(left)
                            .setRight(right)
                            .setOp(op)
                    )
                    .build()
            )
            return target
        }

        // Chained comparison: a < b < c → (a < b) AND (b < c)
        var result: PIRValueProto? = null
        var prevRight = accept(expr.getOperands(0))
        for (i in 0 until expr.operatorsCount) {
            val nextRight = accept(expr.getOperands(i + 1))
            val cmpTarget = cfgBuilder.newTempValue()
            val op = COMPARE_OP_MAP[expr.getOperators(i)] ?: CompareOperator.EQ
            cfgBuilder.emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setCompare(
                        PIRCompareProto.newBuilder()
                            .setTarget(cmpTarget)
                            .setLeft(prevRight)
                            .setRight(nextRight)
                            .setOp(op)
                    )
                    .build()
            )
            result = if (result == null) {
                cmpTarget
            } else {
                val andTarget = cfgBuilder.newTempValue()
                cfgBuilder.emit(
                    PIRInstructionProto.newBuilder()
                        .setLineNumber(line)
                        .setBinOp(
                            PIRBinOpProto.newBuilder()
                                .setTarget(andTarget)
                                .setLeft(result)
                                .setRight(cmpTarget)
                                .setOp(BinaryOperator.BIT_AND)
                        )
                        .build()
                )
                andTarget
            }
            prevRight = nextRight
        }
        return result!!
    }

    // ─── Call ─────────────────────────────────────────────

    private fun visitCall(expr: MypyCallExprProto, line: Int): PIRValueProto {
        val callee = accept(expr.callee)
        val args = mutableListOf<PIRCallArgProto>()
        for (arg in expr.argsList) {
            val argVal = accept(arg.expr)
            val protoKind = when (arg.kind) {
                2 -> CallArgKind.STAR            // ARG_STAR
                4 -> CallArgKind.DOUBLE_STAR     // ARG_STAR2
                3, 5 -> CallArgKind.KEYWORD      // ARG_NAMED=3, ARG_NAMED_OPT=5
                else -> CallArgKind.POSITIONAL   // ARG_POS, ARG_OPT
            }
            args.add(
                PIRCallArgProto.newBuilder()
                    .setValue(argVal)
                    .setKind(protoKind)
                    .setKeyword(arg.name)
                    .build()
            )
        }

        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setCall(
                    PIRCallProto.newBuilder()
                        .setTarget(target)
                        .setCallee(callee)
                        .addAllArgs(args)
                        .setResolvedCallee(expr.resolvedCallee)
                )
                .build()
        )
        return target
    }

    // ─── Subscript & Slice ───────────────────────────────

    private fun visitIndex(expr: MypyIndexExprProto, line: Int): PIRValueProto {
        val obj = accept(expr.base)
        val index = accept(expr.index)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setLoadSubscript(
                    PIRLoadSubscriptProto.newBuilder()
                        .setTarget(target)
                        .setObject(obj)
                        .setIndex(index)
                )
                .build()
        )
        return target
    }

    private fun visitSlice(expr: MypySliceExprProto, line: Int): PIRValueProto {
        val target = cfgBuilder.newTempValue()
        val builder = PIRBuildSliceProto.newBuilder().setTarget(target)
        if (expr.hasBegin()) builder.setLower(accept(expr.begin))
        if (expr.hasEnd()) builder.setUpper(accept(expr.end))
        if (expr.hasStride()) builder.setStep(accept(expr.stride))
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBuildSlice(builder)
                .build()
        )
        return target
    }

    // ─── Collection literals ─────────────────────────────

    private fun visitList(expr: MypyListExprProto, line: Int): PIRValueProto {
        val elements = expr.itemsList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBuildList(PIRBuildListProto.newBuilder().setTarget(target).addAllElements(elements))
                .build()
        )
        return target
    }

    private fun visitTuple(expr: MypyTupleExprProto, line: Int): PIRValueProto {
        val elements = expr.itemsList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBuildTuple(PIRBuildTupleProto.newBuilder().setTarget(target).addAllElements(elements))
                .build()
        )
        return target
    }

    private fun visitSet(expr: MypySetExprProto, line: Int): PIRValueProto {
        val elements = expr.itemsList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBuildSet(PIRBuildSetProto.newBuilder().setTarget(target).addAllElements(elements))
                .build()
        )
        return target
    }

    private fun visitDict(expr: MypyDictExprProto, line: Int): PIRValueProto {
        val keys = expr.keysList.map {
            if (it.kindCase == MypyExprProto.KindCase.KIND_NOT_SET) constNone() else accept(it)
        }
        val values = expr.valuesList.map { accept(it) }
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBuildDict(
                    PIRBuildDictProto.newBuilder()
                        .setTarget(target)
                        .addAllKeys(keys)
                        .addAllValues(values)
                )
                .build()
        )
        return target
    }

    // ─── Conditional expression ─────────────────────────

    private fun visitConditional(expr: MypyConditionalExprProto, line: Int): PIRValueProto {
        val cond = accept(expr.cond)
        val target = cfgBuilder.newTempValue()
        val trueBlock = cfgBuilder.newBlock()
        val falseBlock = cfgBuilder.newBlock()
        val endBlock = cfgBuilder.newBlock()

        cfgBuilder.emitBranch(cond, trueBlock, falseBlock, line)

        cfgBuilder.activate(trueBlock)
        val trueVal = accept(expr.ifExpr)
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(trueVal))
                .build()
        )
        cfgBuilder.emitGoto(endBlock, -1)

        cfgBuilder.activate(falseBlock)
        val falseVal = accept(expr.elseExpr)
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(falseVal))
                .build()
        )
        cfgBuilder.emitGoto(endBlock, -1)

        cfgBuilder.activate(endBlock)
        return target
    }

    // ─── Generators & Async ─────────────────────────────

    private fun visitYield(expr: MypyYieldExprProto, line: Int): PIRValueProto {
        val value = if (expr.hasExpr() && expr.expr.kindCase != MypyExprProto.KindCase.KIND_NOT_SET) {
            accept(expr.expr)
        } else constNone()
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setYieldInst(PIRYieldProto.newBuilder().setTarget(target).setValue(value))
                .build()
        )
        return target
    }

    private fun visitYieldFrom(expr: MypyYieldFromExprProto, line: Int): PIRValueProto {
        val iterable = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setYieldFrom(PIRYieldFromProto.newBuilder().setTarget(target).setIterable(iterable))
                .build()
        )
        return target
    }

    private fun visitAwait(expr: MypyAwaitExprProto, line: Int): PIRValueProto {
        val awaitable = accept(expr.expr)
        val target = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setAwaitInst(PIRAwaitProto.newBuilder().setTarget(target).setAwaitable(awaitable))
                .build()
        )
        return target
    }

    // ─── Walrus ─────────────────────────────────────────

    private fun visitWalrus(expr: MypyAssignmentExprProto, line: Int): PIRValueProto {
        val value = accept(expr.value)
        val targetName = if (expr.target.hasNameExpr()) {
            scope.resolveLocal(expr.target.nameExpr.name)
        } else {
            scope.newTemp()
        }
        val target = PIRValueProto.newBuilder()
            .setLocal(PIRLocalProto.newBuilder().setName(targetName))
            .build()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setAssign(PIRAssignProto.newBuilder().setTarget(target).setSource(value))
                .build()
        )
        return target
    }

    // ─── Lambda ─────────────────────────────────────────

    private fun visitLambda(expr: MypyLambdaExprProto, line: Int): PIRValueProto {
        val mb = cfgBuilder.moduleBuilder ?: return constNone()

        val idx = mb.lambdaCounter++
        val lambdaName = "<lambda>\$$idx"
        val qualifiedName = "${mb.moduleName}.$lambdaName"

        // Build the lambda's CFG using a fresh CfgBuilder
        val lambdaScope = ScopeStack()
        val lambdaCfg = CfgBuilder(lambdaScope, mb)
        val cfgProto = try {
            lambdaCfg.buildFunctionCfg(expr.body)
        } catch (e: Exception) {
            // Fallback: empty CFG
            PIRCFGProto.newBuilder()
                .addBlocks(
                    PIRBasicBlockProto.newBuilder()
                        .setLabel(0)
                        .addInstructions(
                            PIRInstructionProto.newBuilder()
                                .setReturnInst(PIRReturnProto.getDefaultInstance())
                        )
                )
                .setEntryBlock(0)
                .addExitBlocks(0)
                .build()
        }

        // Build the function proto
        val funcBuilder = PIRFunctionProto.newBuilder()
            .setName(lambdaName)
            .setQualifiedName(qualifiedName)
            .setCfg(cfgProto)

        // Parameters
        for (arg in expr.argumentsList) {
            val kind = when (arg.kind) {
                2 -> ParameterKind.VAR_POSITIONAL     // ARG_STAR
                4 -> ParameterKind.VAR_KEYWORD        // ARG_STAR2
                3, 5 -> ParameterKind.KEYWORD_ONLY    // ARG_NAMED=3, ARG_NAMED_OPT=5
                else -> ParameterKind.POSITIONAL_OR_KEYWORD
            }
            val paramBuilder = PIRParameterProto.newBuilder()
                .setName(arg.name)
                .setKind(kind)
                .setHasDefault(arg.hasDefault)
            if (arg.hasType()) paramBuilder.setType(arg.type)
            funcBuilder.addParameters(paramBuilder)
        }

        // Return type
        if (expr.hasReturnType()) {
            funcBuilder.setReturnType(expr.returnType)
        }

        mb.lambdaFunctions.add(funcBuilder.build())

        return PIRValueProto.newBuilder()
            .setGlobalRef(
                PIRGlobalRefProto.newBuilder()
                    .setName(lambdaName)
                    .setModule(mb.moduleName)
            )
            .build()
    }

    // ─── Comprehensions ─────────────────────────────────

    private fun visitListComprehension(expr: MypyListComprehensionProto, line: Int): PIRValueProto =
        visitComprehensionAsLoop(expr.generator, "list", line)

    private fun visitSetComprehension(expr: MypySetComprehensionProto, line: Int): PIRValueProto =
        visitComprehensionAsLoop(expr.generator, "set", line)

    private fun visitGeneratorExpr(expr: MypyGeneratorExprProto, line: Int): PIRValueProto =
        visitComprehensionAsLoop(expr, "list", line)

    private fun visitDictComprehension(expr: MypyDictComprehensionProto, line: Int): PIRValueProto {
        val result = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setBuildDict(PIRBuildDictProto.newBuilder().setTarget(result))
                .build()
        )

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
    ): PIRValueProto {
        val result = cfgBuilder.newTempValue()

        if (collectionType == "list") {
            cfgBuilder.emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setBuildList(PIRBuildListProto.newBuilder().setTarget(result))
                    .build()
            )
        } else {
            // set() call
            val setRef = PIRValueProto.newBuilder()
                .setGlobalRef(PIRGlobalRefProto.newBuilder().setName("set").setModule("builtins"))
                .build()
            cfgBuilder.emit(
                PIRInstructionProto.newBuilder()
                    .setLineNumber(line)
                    .setCall(PIRCallProto.newBuilder().setTarget(result).setCallee(setRef))
                    .build()
            )
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
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setGetIter(PIRGetIterProto.newBuilder().setTarget(iterVal).setIterable(iterableVal))
                .build()
        )

        val headerBlock = cfgBuilder.newBlock()
        val bodyBlock = cfgBuilder.newBlock()
        val exitBlock = cfgBuilder.newBlock()

        cfgBuilder.emitGoto(headerBlock)
        cfgBuilder.activate(headerBlock)

        // Loop variable
        val idxExpr = indices[loopIdx]
        val targetVal = if (idxExpr.hasNameExpr()) {
            PIRValueProto.newBuilder()
                .setLocal(PIRLocalProto.newBuilder().setName(scope.resolveLocal(idxExpr.nameExpr.name)))
                .build()
        } else {
            cfgBuilder.newTempValue()
        }

        cfgBuilder.emit(
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

    private fun emitCollectionAdd(collection: PIRValueProto, valueExpr: MypyExprProto, method: String, line: Int) {
        val value = accept(valueExpr)
        val methodRef = cfgBuilder.newTempValue()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setLoadAttr(
                    PIRLoadAttrProto.newBuilder()
                        .setTarget(methodRef)
                        .setObject(collection)
                        .setAttribute(method)
                )
                .build()
        )
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setCall(
                    PIRCallProto.newBuilder()
                        .setCallee(methodRef)
                        .addArgs(
                            PIRCallArgProto.newBuilder()
                                .setValue(value)
                                .setKind(CallArgKind.POSITIONAL)
                        )
                )
                .build()
        )
    }

    private fun emitDictStore(dictVal: PIRValueProto, keyExpr: MypyExprProto, valueExpr: MypyExprProto, line: Int) {
        val key = accept(keyExpr)
        val value = accept(valueExpr)
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setStoreSubscript(
                    PIRStoreSubscriptProto.newBuilder()
                        .setObject(dictVal)
                        .setIndex(key)
                        .setValue(value)
                )
                .build()
        )
    }

    // ─── Super ──────────────────────────────────────────

    private fun visitSuper(line: Int): PIRValueProto {
        val target = cfgBuilder.newTempValue()
        val callee = PIRValueProto.newBuilder()
            .setGlobalRef(PIRGlobalRefProto.newBuilder().setName("super").setModule("builtins"))
            .build()
        cfgBuilder.emit(
            PIRInstructionProto.newBuilder()
                .setLineNumber(line)
                .setCall(PIRCallProto.newBuilder().setTarget(target).setCallee(callee))
                .build()
        )
        return target
    }
}
