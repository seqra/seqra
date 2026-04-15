package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRCFGImpl
import org.opentaint.ir.impl.python.proto.*

class InstructionConverter(
    private val typeConverter: TypeConverter,
    private val valueConverter: ValueConverter,
) {

    /** Source positions (line, col) for each instruction, to be read during location wiring. */
    private val sourcePositions = mutableMapOf<PIRInstruction, Pair<Int, Int>>()

    fun convertCFG(proto: PIRCFGProto): PIRCFG {
        val blocks = proto.blocksList.map { convertBlock(it) }
        return PIRCFGImpl(
            blocks = blocks,
            entryLabel = proto.entryBlock,
            exitLabels = proto.exitBlocksList.toSet(),
        )
    }

    fun getInstPosition(inst: PIRInstruction): Pair<Int, Int> =
        sourcePositions[inst] ?: error("Missing source position for instruction: $inst")

    private fun saveInstPosition(inst: PIRInstruction, line: Int, col: Int) {
        sourcePositions[inst] = line to col
    }

    private fun convertBlock(proto: PIRBasicBlockProto): PIRBasicBlock {
        return PIRBasicBlock(
            label = proto.label,
            instructions = proto.instructionsList.map { convertInstruction(it) },
            exceptionHandlers = proto.exceptionHandlersList.toList(),
        )
    }

    private fun v(proto: PIRValueProto) = valueConverter.convert(proto)
    private fun t(proto: PIRTypeProto) = typeConverter.convert(proto)

    /** Create a PIRAssign wrapping an expression. */
    private fun assign(target: PIRValueProto, expr: PIRExpr) =
        PIRAssign(v(target), expr)

    private fun convertInstruction(proto: PIRInstructionProto): PIRInstruction {
        val line = proto.lineNumber
        val col = proto.colOffset

        return when (proto.instCase) {
            PIRInstructionProto.InstCase.ASSIGN -> {
                val a = proto.assign
                PIRAssign(v(a.target), v(a.source))
            }
            PIRInstructionProto.InstCase.LOAD_ATTR -> {
                val la = proto.loadAttr
                assign(la.target, PIRAttrExpr(v(la.`object`), la.attribute, t(la.type)))
            }
            PIRInstructionProto.InstCase.STORE_ATTR -> {
                val sa = proto.storeAttr
                PIRStoreAttr(v(sa.`object`), sa.attribute, v(sa.value))
            }
            PIRInstructionProto.InstCase.LOAD_SUBSCRIPT -> {
                val ls = proto.loadSubscript
                assign(ls.target, PIRSubscriptExpr(v(ls.`object`), v(ls.index), t(ls.type)))
            }
            PIRInstructionProto.InstCase.STORE_SUBSCRIPT -> {
                val ss = proto.storeSubscript
                PIRStoreSubscript(v(ss.`object`), v(ss.index), v(ss.value))
            }
            PIRInstructionProto.InstCase.LOAD_GLOBAL -> {
                val lg = proto.loadGlobal
                // LoadGlobal → assign target = GlobalRef value
                assign(lg.target, PIRGlobalRef(lg.name, lg.module))
            }
            PIRInstructionProto.InstCase.STORE_GLOBAL -> {
                val sg = proto.storeGlobal
                PIRStoreGlobal(sg.name, sg.module, v(sg.value))
            }
            PIRInstructionProto.InstCase.LOAD_CLOSURE -> {
                val lc = proto.loadClosure
                // LoadClosure → assign target = GlobalRef-like reference (closure name)
                assign(lc.target, PIRGlobalRef(lc.name, ""))
            }
            PIRInstructionProto.InstCase.STORE_CLOSURE -> {
                val sc = proto.storeClosure
                PIRStoreClosure(sc.name, sc.depth, v(sc.value))
            }
            PIRInstructionProto.InstCase.BIN_OP -> {
                val bo = proto.binOp
                assign(bo.target, convertBinaryOp(bo.left, bo.right, bo.op))
            }
            PIRInstructionProto.InstCase.UNARY_OP -> {
                val uo = proto.unaryOp
                assign(uo.target, convertUnaryOp(uo.operand, uo.op))
            }
            PIRInstructionProto.InstCase.COMPARE -> {
                val c = proto.compare
                assign(c.target, convertCompareOp(c.left, c.right, c.op))
            }
            PIRInstructionProto.InstCase.CALL -> {
                val c = proto.call
                PIRCall(
                    target = if (c.hasTarget()) v(c.target) else null,
                    callee = v(c.callee),
                    args = c.argsList.map { convertCallArg(it) },
                    resolvedCallee = c.resolvedCallee.ifEmpty { null },
                )
            }
            PIRInstructionProto.InstCase.BUILD_LIST -> {
                val bl = proto.buildList
                assign(bl.target, PIRListExpr(bl.elementsList.map { v(it) }))
            }
            PIRInstructionProto.InstCase.BUILD_TUPLE -> {
                val bt = proto.buildTuple
                assign(bt.target, PIRTupleExpr(bt.elementsList.map { v(it) }))
            }
            PIRInstructionProto.InstCase.BUILD_SET -> {
                val bs = proto.buildSet
                assign(bs.target, PIRSetExpr(bs.elementsList.map { v(it) }))
            }
            PIRInstructionProto.InstCase.BUILD_DICT -> {
                val bd = proto.buildDict
                assign(bd.target, PIRDictExpr(bd.keysList.map { v(it) }, bd.valuesList.map { v(it) }))
            }
            PIRInstructionProto.InstCase.BUILD_SLICE -> {
                val bs = proto.buildSlice
                assign(bs.target, PIRSliceExpr(
                    if (bs.hasLower()) v(bs.lower) else null,
                    if (bs.hasUpper()) v(bs.upper) else null,
                    if (bs.hasStep()) v(bs.step) else null,
                ))
            }
            PIRInstructionProto.InstCase.BUILD_STRING -> {
                val bs = proto.buildString
                assign(bs.target, PIRStringExpr(bs.partsList.map { v(it) }))
            }
            PIRInstructionProto.InstCase.GET_ITER -> {
                val gi = proto.getIter
                assign(gi.target, PIRIterExpr(v(gi.iterable)))
            }
            PIRInstructionProto.InstCase.NEXT_ITER -> {
                val ni = proto.nextIter
                PIRNextIter(v(ni.target), v(ni.iterator), ni.bodyBlock, ni.exitBlock)
            }
            PIRInstructionProto.InstCase.UNPACK -> {
                val u = proto.unpack
                PIRUnpack(u.targetsList.map { v(it) }, v(u.source), u.starIndex)
            }
            PIRInstructionProto.InstCase.GOTO_INST -> {
                PIRGoto(proto.gotoInst.targetBlock)
            }
            PIRInstructionProto.InstCase.BRANCH -> {
                val b = proto.branch
                PIRBranch(v(b.condition), b.trueBlock, b.falseBlock)
            }
            PIRInstructionProto.InstCase.RETURN_INST -> {
                val r = proto.returnInst
                PIRReturn(if (r.hasValue()) v(r.value) else null)
            }
            PIRInstructionProto.InstCase.RAISE_INST -> {
                val r = proto.raiseInst
                PIRRaise(
                    if (r.hasException()) v(r.exception) else null,
                    if (r.hasCause()) v(r.cause) else null,
                )
            }
            PIRInstructionProto.InstCase.EXCEPT_HANDLER -> {
                val eh = proto.exceptHandler
                PIRExceptHandler(
                    if (eh.hasTarget()) v(eh.target) else null,
                    eh.exceptionTypesList.map { t(it) },
                )
            }
            PIRInstructionProto.InstCase.YIELD_INST -> {
                val y = proto.yieldInst
                PIRYield(
                    if (y.hasTarget()) v(y.target) else null,
                    if (y.hasValue()) v(y.value) else null,
                )
            }
            PIRInstructionProto.InstCase.YIELD_FROM -> {
                val yf = proto.yieldFrom
                PIRYieldFrom(
                    if (yf.hasTarget()) v(yf.target) else null,
                    v(yf.iterable),
                )
            }
            PIRInstructionProto.InstCase.AWAIT_INST -> {
                val a = proto.awaitInst
                PIRAwait(
                    if (a.hasTarget()) v(a.target) else null,
                    v(a.awaitable),
                )
            }
            PIRInstructionProto.InstCase.DELETE_LOCAL -> {
                PIRDeleteLocal(v(proto.deleteLocal.local))
            }
            PIRInstructionProto.InstCase.DELETE_ATTR -> {
                val da = proto.deleteAttr
                PIRDeleteAttr(v(da.`object`), da.attribute)
            }
            PIRInstructionProto.InstCase.DELETE_SUBSCRIPT -> {
                val ds = proto.deleteSubscript
                PIRDeleteSubscript(v(ds.`object`), v(ds.index))
            }
            PIRInstructionProto.InstCase.DELETE_GLOBAL -> {
                val dg = proto.deleteGlobal
                PIRDeleteGlobal(dg.name, dg.module)
            }
            PIRInstructionProto.InstCase.TYPE_CHECK -> {
                val tc = proto.typeCheck
                assign(tc.target, PIRTypeCheckExpr(v(tc.value), t(tc.type)))
            }
            PIRInstructionProto.InstCase.UNREACHABLE -> PIRUnreachable
            PIRInstructionProto.InstCase.INST_NOT_SET, null -> PIRUnreachable
        }.also {
            saveInstPosition(it, line, col)
        }
    }

    private fun convertCallArg(proto: PIRCallArgProto): PIRCallArg {
        return PIRCallArg(
            value = valueConverter.convert(proto.value),
            kind = when (proto.kind) {
                CallArgKind.POSITIONAL -> PIRCallArgKind.POSITIONAL
                CallArgKind.KEYWORD -> PIRCallArgKind.KEYWORD
                CallArgKind.STAR -> PIRCallArgKind.STAR
                CallArgKind.DOUBLE_STAR -> PIRCallArgKind.DOUBLE_STAR
                CallArgKind.UNRECOGNIZED, null -> PIRCallArgKind.POSITIONAL
            },
            keyword = proto.keyword.ifEmpty { null },
        )
    }

    private fun convertBinaryOp(left: PIRValueProto, right: PIRValueProto, op: BinaryOperator): PIRBinaryExpr {
        val l = v(left)
        val r = v(right)

        return when (op) {
            BinaryOperator.ADD -> PIRAddExpr(l, r)
            BinaryOperator.SUB -> PIRSubExpr(l, r)
            BinaryOperator.MUL -> PIRMulExpr(l, r)
            BinaryOperator.DIV -> PIRDivExpr(l, r)
            BinaryOperator.FLOOR_DIV -> PIRFloorDivExpr(l, r)
            BinaryOperator.MOD -> PIRModExpr(l, r)
            BinaryOperator.POW -> PIRPowExpr(l, r)
            BinaryOperator.MAT_MUL -> PIRMatMulExpr(l, r)
            BinaryOperator.BIT_AND -> PIRBitAndExpr(l, r)
            BinaryOperator.BIT_OR -> PIRBitOrExpr(l, r)
            BinaryOperator.BIT_XOR -> PIRBitXorExpr(l, r)
            BinaryOperator.LSHIFT -> PIRLShiftExpr(l, r)
            BinaryOperator.RSHIFT -> PIRRShiftExpr(l, r)
            BinaryOperator.UNRECOGNIZED -> PIRAddExpr(l, r)
        }
    }

    private fun convertUnaryOp(operandProto: PIRValueProto, op: UnaryOperator): PIRUnaryExpr {
        val operand = v(operandProto)

        return when (op) {
            UnaryOperator.NEG -> PIRNegExpr(operand)
            UnaryOperator.POS -> PIRPosExpr(operand)
            UnaryOperator.NOT -> PIRNotExpr(operand)
            UnaryOperator.INVERT -> PIRInvertExpr(operand)
            UnaryOperator.UNRECOGNIZED -> PIRNegExpr(operand)
        }
    }

    private fun convertCompareOp(leftProto: PIRValueProto, rightProto: PIRValueProto, op: CompareOperator): PIRCompareExpr {
        val l = v(leftProto)
        val r = v(rightProto)

        return when (op) {
            CompareOperator.EQ -> PIREqExpr(l, r)
            CompareOperator.NE -> PIRNeExpr(l, r)
            CompareOperator.LT -> PIRLtExpr(l, r)
            CompareOperator.LE -> PIRLeExpr(l, r)
            CompareOperator.GT -> PIRGtExpr(l, r)
            CompareOperator.GE -> PIRGeExpr(l, r)
            CompareOperator.IS -> PIRIsExpr(l, r)
            CompareOperator.IS_NOT -> PIRIsNotExpr(l, r)
            CompareOperator.IN -> PIRInExpr(l, r)
            CompareOperator.NOT_IN -> PIRNotInExpr(l, r)
            CompareOperator.UNRECOGNIZED -> PIREqExpr(l, r)
        }
    }

}
