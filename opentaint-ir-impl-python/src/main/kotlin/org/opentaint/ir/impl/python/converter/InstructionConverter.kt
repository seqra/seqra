package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRCFGImpl
import org.opentaint.ir.impl.python.proto.*

class InstructionConverter(
    private val typeConverter: TypeConverter,
    private val valueConverter: ValueConverter,
) {

    fun convertCFG(proto: PIRCFGProto): PIRCFG {
        val blocks = proto.blocksList.map { convertBlock(it) }
        return PIRCFGImpl(
            blocks = blocks,
            entryLabel = proto.entryBlock,
            exitLabels = proto.exitBlocksList.toSet(),
        )
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
    private fun assign(target: PIRValueProto, expr: PIRExpr, line: Int, col: Int) =
        PIRAssign(v(target), expr, line, col)

    fun convertInstruction(proto: PIRInstructionProto): PIRInstruction {
        val line = proto.lineNumber
        val col = proto.colOffset

        return when (proto.instCase) {
            PIRInstructionProto.InstCase.ASSIGN -> {
                val a = proto.assign
                PIRAssign(v(a.target), v(a.source), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_ATTR -> {
                val la = proto.loadAttr
                assign(la.target, PIRAttrExpr(v(la.`object`), la.attribute, t(la.type)), line, col)
            }
            PIRInstructionProto.InstCase.STORE_ATTR -> {
                val sa = proto.storeAttr
                PIRStoreAttr(v(sa.`object`), sa.attribute, v(sa.value), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_SUBSCRIPT -> {
                val ls = proto.loadSubscript
                assign(ls.target, PIRSubscriptExpr(v(ls.`object`), v(ls.index), t(ls.type)), line, col)
            }
            PIRInstructionProto.InstCase.STORE_SUBSCRIPT -> {
                val ss = proto.storeSubscript
                PIRStoreSubscript(v(ss.`object`), v(ss.index), v(ss.value), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_GLOBAL -> {
                val lg = proto.loadGlobal
                // LoadGlobal → assign target = GlobalRef value
                assign(lg.target, PIRGlobalRef(lg.name, lg.module), line, col)
            }
            PIRInstructionProto.InstCase.STORE_GLOBAL -> {
                val sg = proto.storeGlobal
                PIRStoreGlobal(sg.name, sg.module, v(sg.value), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_CLOSURE -> {
                val lc = proto.loadClosure
                // LoadClosure → assign target = GlobalRef-like reference (closure name)
                assign(lc.target, PIRGlobalRef(lc.name, ""), line, col)
            }
            PIRInstructionProto.InstCase.STORE_CLOSURE -> {
                val sc = proto.storeClosure
                PIRStoreClosure(sc.name, sc.depth, v(sc.value), line, col)
            }
            PIRInstructionProto.InstCase.BIN_OP -> {
                val bo = proto.binOp
                assign(bo.target, PIRBinExpr(v(bo.left), v(bo.right), convertBinaryOp(bo.op)), line, col)
            }
            PIRInstructionProto.InstCase.UNARY_OP -> {
                val uo = proto.unaryOp
                assign(uo.target, PIRUnaryExpr(v(uo.operand), convertUnaryOp(uo.op)), line, col)
            }
            PIRInstructionProto.InstCase.COMPARE -> {
                val c = proto.compare
                assign(c.target, PIRCompareExpr(v(c.left), v(c.right), convertCompareOp(c.op)), line, col)
            }
            PIRInstructionProto.InstCase.CALL -> {
                val c = proto.call
                PIRCall(
                    target = if (c.hasTarget()) v(c.target) else null,
                    callee = v(c.callee),
                    args = c.argsList.map { convertCallArg(it) },
                    resolvedCallee = c.resolvedCallee.ifEmpty { null },
                    lineNumber = line,
                    colOffset = col,
                )
            }
            PIRInstructionProto.InstCase.BUILD_LIST -> {
                val bl = proto.buildList
                assign(bl.target, PIRListExpr(bl.elementsList.map { v(it) }), line, col)
            }
            PIRInstructionProto.InstCase.BUILD_TUPLE -> {
                val bt = proto.buildTuple
                assign(bt.target, PIRTupleExpr(bt.elementsList.map { v(it) }), line, col)
            }
            PIRInstructionProto.InstCase.BUILD_SET -> {
                val bs = proto.buildSet
                assign(bs.target, PIRSetExpr(bs.elementsList.map { v(it) }), line, col)
            }
            PIRInstructionProto.InstCase.BUILD_DICT -> {
                val bd = proto.buildDict
                assign(bd.target, PIRDictExpr(bd.keysList.map { v(it) }, bd.valuesList.map { v(it) }), line, col)
            }
            PIRInstructionProto.InstCase.BUILD_SLICE -> {
                val bs = proto.buildSlice
                assign(bs.target, PIRSliceExpr(
                    if (bs.hasLower()) v(bs.lower) else null,
                    if (bs.hasUpper()) v(bs.upper) else null,
                    if (bs.hasStep()) v(bs.step) else null,
                ), line, col)
            }
            PIRInstructionProto.InstCase.BUILD_STRING -> {
                val bs = proto.buildString
                assign(bs.target, PIRStringExpr(bs.partsList.map { v(it) }), line, col)
            }
            PIRInstructionProto.InstCase.GET_ITER -> {
                val gi = proto.getIter
                assign(gi.target, PIRIterExpr(v(gi.iterable)), line, col)
            }
            PIRInstructionProto.InstCase.NEXT_ITER -> {
                val ni = proto.nextIter
                PIRNextIter(v(ni.target), v(ni.iterator), ni.bodyBlock, ni.exitBlock, line, col)
            }
            PIRInstructionProto.InstCase.UNPACK -> {
                val u = proto.unpack
                PIRUnpack(u.targetsList.map { v(it) }, v(u.source), u.starIndex, line, col)
            }
            PIRInstructionProto.InstCase.GOTO_INST -> {
                PIRGoto(proto.gotoInst.targetBlock, line, col)
            }
            PIRInstructionProto.InstCase.BRANCH -> {
                val b = proto.branch
                PIRBranch(v(b.condition), b.trueBlock, b.falseBlock, line, col)
            }
            PIRInstructionProto.InstCase.RETURN_INST -> {
                val r = proto.returnInst
                PIRReturn(if (r.hasValue()) v(r.value) else null, line, col)
            }
            PIRInstructionProto.InstCase.RAISE_INST -> {
                val r = proto.raiseInst
                PIRRaise(
                    if (r.hasException()) v(r.exception) else null,
                    if (r.hasCause()) v(r.cause) else null,
                    line, col
                )
            }
            PIRInstructionProto.InstCase.EXCEPT_HANDLER -> {
                val eh = proto.exceptHandler
                PIRExceptHandler(
                    if (eh.hasTarget()) v(eh.target) else null,
                    eh.exceptionTypesList.map { t(it) },
                    line, col
                )
            }
            PIRInstructionProto.InstCase.YIELD_INST -> {
                val y = proto.yieldInst
                PIRYield(
                    if (y.hasTarget()) v(y.target) else null,
                    if (y.hasValue()) v(y.value) else null,
                    line, col
                )
            }
            PIRInstructionProto.InstCase.YIELD_FROM -> {
                val yf = proto.yieldFrom
                PIRYieldFrom(
                    if (yf.hasTarget()) v(yf.target) else null,
                    v(yf.iterable), line, col
                )
            }
            PIRInstructionProto.InstCase.AWAIT_INST -> {
                val a = proto.awaitInst
                PIRAwait(
                    if (a.hasTarget()) v(a.target) else null,
                    v(a.awaitable), line, col
                )
            }
            PIRInstructionProto.InstCase.DELETE_LOCAL -> {
                PIRDeleteLocal(v(proto.deleteLocal.local), line, col)
            }
            PIRInstructionProto.InstCase.DELETE_ATTR -> {
                val da = proto.deleteAttr
                PIRDeleteAttr(v(da.`object`), da.attribute, line, col)
            }
            PIRInstructionProto.InstCase.DELETE_SUBSCRIPT -> {
                val ds = proto.deleteSubscript
                PIRDeleteSubscript(v(ds.`object`), v(ds.index), line, col)
            }
            PIRInstructionProto.InstCase.DELETE_GLOBAL -> {
                val dg = proto.deleteGlobal
                PIRDeleteGlobal(dg.name, dg.module, line, col)
            }
            PIRInstructionProto.InstCase.TYPE_CHECK -> {
                val tc = proto.typeCheck
                assign(tc.target, PIRTypeCheckExpr(v(tc.value), t(tc.type)), line, col)
            }
            PIRInstructionProto.InstCase.UNREACHABLE -> PIRUnreachable
            PIRInstructionProto.InstCase.INST_NOT_SET, null -> PIRUnreachable
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

    private fun convertBinaryOp(op: BinaryOperator): PIRBinaryOperator = when (op) {
        BinaryOperator.ADD -> PIRBinaryOperator.ADD
        BinaryOperator.SUB -> PIRBinaryOperator.SUB
        BinaryOperator.MUL -> PIRBinaryOperator.MUL
        BinaryOperator.DIV -> PIRBinaryOperator.DIV
        BinaryOperator.FLOOR_DIV -> PIRBinaryOperator.FLOOR_DIV
        BinaryOperator.MOD -> PIRBinaryOperator.MOD
        BinaryOperator.POW -> PIRBinaryOperator.POW
        BinaryOperator.MAT_MUL -> PIRBinaryOperator.MAT_MUL
        BinaryOperator.BIT_AND -> PIRBinaryOperator.BIT_AND
        BinaryOperator.BIT_OR -> PIRBinaryOperator.BIT_OR
        BinaryOperator.BIT_XOR -> PIRBinaryOperator.BIT_XOR
        BinaryOperator.LSHIFT -> PIRBinaryOperator.LSHIFT
        BinaryOperator.RSHIFT -> PIRBinaryOperator.RSHIFT
        BinaryOperator.UNRECOGNIZED -> PIRBinaryOperator.ADD
    }

    private fun convertUnaryOp(op: UnaryOperator): PIRUnaryOperator = when (op) {
        UnaryOperator.NEG -> PIRUnaryOperator.NEG
        UnaryOperator.POS -> PIRUnaryOperator.POS
        UnaryOperator.NOT -> PIRUnaryOperator.NOT
        UnaryOperator.INVERT -> PIRUnaryOperator.INVERT
        UnaryOperator.UNRECOGNIZED -> PIRUnaryOperator.NEG
    }

    private fun convertCompareOp(op: CompareOperator): PIRCompareOperator = when (op) {
        CompareOperator.EQ -> PIRCompareOperator.EQ
        CompareOperator.NE -> PIRCompareOperator.NE
        CompareOperator.LT -> PIRCompareOperator.LT
        CompareOperator.LE -> PIRCompareOperator.LE
        CompareOperator.GT -> PIRCompareOperator.GT
        CompareOperator.GE -> PIRCompareOperator.GE
        CompareOperator.IS -> PIRCompareOperator.IS
        CompareOperator.IS_NOT -> PIRCompareOperator.IS_NOT
        CompareOperator.IN -> PIRCompareOperator.IN
        CompareOperator.NOT_IN -> PIRCompareOperator.NOT_IN
        CompareOperator.UNRECOGNIZED -> PIRCompareOperator.EQ
    }
}
