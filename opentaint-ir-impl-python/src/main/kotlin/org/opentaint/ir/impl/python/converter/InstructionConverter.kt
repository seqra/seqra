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

    fun convertInstruction(proto: PIRInstructionProto): PIRInstruction {
        val line = proto.lineNumber
        val col = proto.colOffset

        return when (proto.instCase) {
            PIRInstructionProto.InstCase.ASSIGN -> {
                val a = proto.assign
                PIRAssign(valueConverter.convert(a.target), valueConverter.convert(a.source), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_ATTR -> {
                val la = proto.loadAttr
                PIRLoadAttr(
                    valueConverter.convert(la.target), valueConverter.convert(la.`object`),
                    la.attribute, typeConverter.convert(la.type), line, col
                )
            }
            PIRInstructionProto.InstCase.STORE_ATTR -> {
                val sa = proto.storeAttr
                PIRStoreAttr(valueConverter.convert(sa.`object`), sa.attribute, valueConverter.convert(sa.value), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_SUBSCRIPT -> {
                val ls = proto.loadSubscript
                PIRLoadSubscript(valueConverter.convert(ls.target), valueConverter.convert(ls.`object`), valueConverter.convert(ls.index), typeConverter.convert(ls.type), line, col)
            }
            PIRInstructionProto.InstCase.STORE_SUBSCRIPT -> {
                val ss = proto.storeSubscript
                PIRStoreSubscript(valueConverter.convert(ss.`object`), valueConverter.convert(ss.index), valueConverter.convert(ss.value), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_GLOBAL -> {
                val lg = proto.loadGlobal
                PIRLoadGlobal(valueConverter.convert(lg.target), lg.name, lg.module, line, col)
            }
            PIRInstructionProto.InstCase.STORE_GLOBAL -> {
                val sg = proto.storeGlobal
                PIRStoreGlobal(sg.name, sg.module, valueConverter.convert(sg.value), line, col)
            }
            PIRInstructionProto.InstCase.LOAD_CLOSURE -> {
                val lc = proto.loadClosure
                PIRLoadClosure(valueConverter.convert(lc.target), lc.name, lc.depth, line, col)
            }
            PIRInstructionProto.InstCase.STORE_CLOSURE -> {
                val sc = proto.storeClosure
                PIRStoreClosure(sc.name, sc.depth, valueConverter.convert(sc.value), line, col)
            }
            PIRInstructionProto.InstCase.BIN_OP -> {
                val bo = proto.binOp
                PIRBinOp(
                    valueConverter.convert(bo.target), valueConverter.convert(bo.left),
                    valueConverter.convert(bo.right), convertBinaryOp(bo.op), line, col
                )
            }
            PIRInstructionProto.InstCase.UNARY_OP -> {
                val uo = proto.unaryOp
                PIRUnaryOp(valueConverter.convert(uo.target), valueConverter.convert(uo.operand), convertUnaryOp(uo.op), line, col)
            }
            PIRInstructionProto.InstCase.COMPARE -> {
                val c = proto.compare
                PIRCompare(
                    valueConverter.convert(c.target), valueConverter.convert(c.left),
                    valueConverter.convert(c.right), convertCompareOp(c.op), line, col
                )
            }
            PIRInstructionProto.InstCase.CALL -> {
                val c = proto.call
                PIRCall(
                    target = if (c.hasTarget()) valueConverter.convert(c.target) else null,
                    callee = valueConverter.convert(c.callee),
                    args = c.argsList.map { convertCallArg(it) },
                    resolvedCallee = c.resolvedCallee.ifEmpty { null },
                    lineNumber = line,
                    colOffset = col,
                )
            }
            PIRInstructionProto.InstCase.BUILD_LIST -> {
                val bl = proto.buildList
                PIRBuildList(valueConverter.convert(bl.target), bl.elementsList.map { valueConverter.convert(it) }, line, col)
            }
            PIRInstructionProto.InstCase.BUILD_TUPLE -> {
                val bt = proto.buildTuple
                PIRBuildTuple(valueConverter.convert(bt.target), bt.elementsList.map { valueConverter.convert(it) }, line, col)
            }
            PIRInstructionProto.InstCase.BUILD_SET -> {
                val bs = proto.buildSet
                PIRBuildSet(valueConverter.convert(bs.target), bs.elementsList.map { valueConverter.convert(it) }, line, col)
            }
            PIRInstructionProto.InstCase.BUILD_DICT -> {
                val bd = proto.buildDict
                PIRBuildDict(valueConverter.convert(bd.target), bd.keysList.map { valueConverter.convert(it) }, bd.valuesList.map { valueConverter.convert(it) }, line, col)
            }
            PIRInstructionProto.InstCase.BUILD_SLICE -> {
                val bs = proto.buildSlice
                PIRBuildSlice(
                    valueConverter.convert(bs.target),
                    if (bs.hasLower()) valueConverter.convert(bs.lower) else null,
                    if (bs.hasUpper()) valueConverter.convert(bs.upper) else null,
                    if (bs.hasStep()) valueConverter.convert(bs.step) else null,
                    line, col
                )
            }
            PIRInstructionProto.InstCase.BUILD_STRING -> {
                val bs = proto.buildString
                PIRBuildString(valueConverter.convert(bs.target), bs.partsList.map { valueConverter.convert(it) }, line, col)
            }
            PIRInstructionProto.InstCase.GET_ITER -> {
                val gi = proto.getIter
                PIRGetIter(valueConverter.convert(gi.target), valueConverter.convert(gi.iterable), line, col)
            }
            PIRInstructionProto.InstCase.NEXT_ITER -> {
                val ni = proto.nextIter
                PIRNextIter(valueConverter.convert(ni.target), valueConverter.convert(ni.iterator), ni.bodyBlock, ni.exitBlock, line, col)
            }
            PIRInstructionProto.InstCase.UNPACK -> {
                val u = proto.unpack
                PIRUnpack(u.targetsList.map { valueConverter.convert(it) }, valueConverter.convert(u.source), u.starIndex, line, col)
            }
            PIRInstructionProto.InstCase.GOTO_INST -> {
                PIRGoto(proto.gotoInst.targetBlock, line, col)
            }
            PIRInstructionProto.InstCase.BRANCH -> {
                val b = proto.branch
                PIRBranch(valueConverter.convert(b.condition), b.trueBlock, b.falseBlock, line, col)
            }
            PIRInstructionProto.InstCase.RETURN_INST -> {
                val r = proto.returnInst
                PIRReturn(if (r.hasValue()) valueConverter.convert(r.value) else null, line, col)
            }
            PIRInstructionProto.InstCase.RAISE_INST -> {
                val r = proto.raiseInst
                PIRRaise(
                    if (r.hasException()) valueConverter.convert(r.exception) else null,
                    if (r.hasCause()) valueConverter.convert(r.cause) else null,
                    line, col
                )
            }
            PIRInstructionProto.InstCase.EXCEPT_HANDLER -> {
                val eh = proto.exceptHandler
                PIRExceptHandler(
                    if (eh.hasTarget()) valueConverter.convert(eh.target) else null,
                    eh.exceptionTypesList.map { typeConverter.convert(it) },
                    line, col
                )
            }
            PIRInstructionProto.InstCase.YIELD_INST -> {
                val y = proto.yieldInst
                PIRYield(
                    if (y.hasTarget()) valueConverter.convert(y.target) else null,
                    if (y.hasValue()) valueConverter.convert(y.value) else null,
                    line, col
                )
            }
            PIRInstructionProto.InstCase.YIELD_FROM -> {
                val yf = proto.yieldFrom
                PIRYieldFrom(
                    if (yf.hasTarget()) valueConverter.convert(yf.target) else null,
                    valueConverter.convert(yf.iterable), line, col
                )
            }
            PIRInstructionProto.InstCase.AWAIT_INST -> {
                val a = proto.awaitInst
                PIRAwait(
                    if (a.hasTarget()) valueConverter.convert(a.target) else null,
                    valueConverter.convert(a.awaitable), line, col
                )
            }
            PIRInstructionProto.InstCase.DELETE_LOCAL -> {
                PIRDeleteLocal(valueConverter.convert(proto.deleteLocal.local), line, col)
            }
            PIRInstructionProto.InstCase.DELETE_ATTR -> {
                val da = proto.deleteAttr
                PIRDeleteAttr(valueConverter.convert(da.`object`), da.attribute, line, col)
            }
            PIRInstructionProto.InstCase.DELETE_SUBSCRIPT -> {
                val ds = proto.deleteSubscript
                PIRDeleteSubscript(valueConverter.convert(ds.`object`), valueConverter.convert(ds.index), line, col)
            }
            PIRInstructionProto.InstCase.DELETE_GLOBAL -> {
                val dg = proto.deleteGlobal
                PIRDeleteGlobal(dg.name, dg.module, line, col)
            }
            PIRInstructionProto.InstCase.TYPE_CHECK -> {
                val tc = proto.typeCheck
                PIRTypeCheck(valueConverter.convert(tc.target), valueConverter.convert(tc.value), typeConverter.convert(tc.type), line, col)
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
