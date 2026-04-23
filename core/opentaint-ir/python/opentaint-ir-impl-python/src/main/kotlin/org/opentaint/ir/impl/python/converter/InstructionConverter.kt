package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRCFGImpl
import org.opentaint.ir.impl.python.builder.*

class InstructionConverter {

    private val sourcePositions = mutableMapOf<PIRInstruction, Pair<Int, Int>>()
    private val instructionIndices = mutableMapOf<PIRInstruction, Int>()

    fun convertCFG(flat: FlatCFG): PIRCFG {
        val sortedBlocks = flat.blocks.sortedBy { it.label }
        val blockStartIndex = calculateBlockToStartIdx(sortedBlocks)

        val instList = ArrayList<PIRInstruction>()
        val instToBlock = ArrayList<Int>()
        for (block in sortedBlocks) {
            convertBlockInstructions(block, blockStartIndex, instList)
            repeat(block.instructions.size) { instToBlock.add(block.label) }
        }

        val blocks = convertBlocks(sortedBlocks, blockStartIndex, instList)

        return PIRCFGImpl(
            blocks = blocks,
            instList = instList,
            entryLabel = flat.entryBlock,
            exitLabels = flat.exitBlocks.toSet(),
            instToBlock = instToBlock,
        )
    }

    fun getInstPosition(inst: PIRInstruction): Pair<Int, Int> =
        sourcePositions[inst] ?: error("Missing source position for instruction: $inst")

    fun getInstIndex(inst: PIRInstruction): Int =
        instructionIndices[inst] ?: error("Missing instruction index for: $inst")

    private fun convertBlockInstructions(block: FlatBlock, blockStartIndex: Map<Int, Int>, dst: MutableList<PIRInstruction>) {
        val startIdx = blockStartIndex.getValue(block.label)
        for ((i, flatInst) in block.instructions.withIndex()) {
            dst.add(convertInstruction(flatInst, blockStartIndex, startIdx + i))
        }
    }

    private fun convertBlocks(sortedBlocks: List<FlatBlock>, blockStartIndex: Map<Int, Int>, instList: List<PIRInstruction>): List<PIRBasicBlock> =
        sortedBlocks.map { block ->
            val startIdx = blockStartIndex.getValue(block.label)
            PIRBasicBlock(
                label = block.label,
                instructions = instList.subList(startIdx, startIdx + block.instructions.size),
                exceptionHandlers = block.exceptionHandlers,
            )
        }

    private fun calculateBlockToStartIdx(blocks: List<FlatBlock>): Map<Int, Int> = buildMap {
        var idx = 0
        for (block in blocks) {
            this[block.label] = idx
            idx += block.instructions.size
        }
    }

    // ─── Value conversion ─────────────────────────────────

    private fun v(flat: FlatValue): PIRValue = when (flat) {
        is FlatLocal -> PIRLocal(flat.name, convertType(flat.type))
        is FlatGlobalRef -> PIRGlobalRef(flat.name, flat.module, PIRAnyType)
        is FlatParameterRef -> PIRParameterRef(flat.name, PIRAnyType)
        is FlatConst -> convertConstValue(flat)
    }

    fun convertType(flat: FlatType): PIRType = when (flat) {
        is FlatAnyType -> PIRAnyType
        is FlatNeverType -> PIRNeverType
        is FlatNoneType -> PIRNoneType
        is FlatClassType -> PIRClassType(
            qualifiedName = flat.qualifiedName,
            typeArgs = flat.typeArgs.map { convertType(it) },
            isOptional = flat.isOptional,
        )
        is FlatFunctionType -> PIRFunctionType(
            paramTypes = flat.paramTypes.map { convertType(it) },
            returnType = convertType(flat.returnType),
        )
        is FlatUnionType -> PIRUnionType(members = flat.members.map { convertType(it) })
        is FlatTupleType -> PIRTupleType(
            elementTypes = flat.elementTypes.map { convertType(it) },
            isVarLength = flat.isVarLength,
        )
        is FlatLiteralType -> PIRLiteralType(
            value = flat.value,
            baseType = convertType(flat.baseType),
        )
        is FlatTypeVarType -> PIRTypeVarType(
            name = flat.name,
            bounds = flat.bounds.map { convertType(it) },
        )
    }

    fun convertConstValue(c: FlatConst): PIRValue = when (c) {
        is FlatIntConst -> PIRIntConst(c.value)
        is FlatFloatConst -> PIRFloatConst(c.value)
        is FlatStrConst -> PIRStrConst(c.value)
        is FlatBoolConst -> PIRBoolConst(c.value)
        is FlatNoneConst -> PIRNoneConst
        is FlatEllipsisConst -> PIREllipsisConst
        is FlatBytesConst -> PIRBytesConst(c.value)
        is FlatComplexConst -> PIRComplexConst(c.real, c.imag)
    }

    // ─── Instruction conversion ───────────────────────────

    private fun convertInstruction(flat: FlatInst, blockStartIndex: Map<Int, Int>, flatIndex: Int): PIRInstruction {
        return when (flat) {
            is FlatAssign -> PIRAssign(v(flat.target), v(flat.source))
            is FlatLoadAttr -> PIRLoadAttr(v(flat.target), v(flat.obj), flat.attribute, convertType(flat.type))
            is FlatStoreAttr -> PIRStoreAttr(v(flat.obj), flat.attribute, v(flat.value))
            is FlatLoadSubscript -> PIRAssign(v(flat.target), PIRSubscriptExpr(v(flat.obj), v(flat.index), convertType(flat.type)))
            is FlatStoreSubscript -> PIRStoreSubscript(v(flat.obj), v(flat.index), v(flat.value))
            is FlatLoadGlobal -> PIRAssign(v(flat.target), PIRGlobalRef(flat.name, flat.module))
            is FlatStoreGlobal -> PIRStoreGlobal(flat.name, flat.module, v(flat.value))
            is FlatLoadClosure -> PIRAssign(v(flat.target), PIRGlobalRef(flat.name, ""))
            is FlatStoreClosure -> PIRStoreClosure(flat.name, flat.depth, v(flat.value))

            is FlatBinOp -> PIRAssign(v(flat.target), convertBinaryOp(v(flat.left), v(flat.right), flat.op))
            is FlatUnaryOp -> PIRAssign(v(flat.target), convertUnaryOp(v(flat.operand), flat.op))
            is FlatCompare -> PIRAssign(v(flat.target), convertCompareOp(v(flat.left), v(flat.right), flat.op))

            is FlatCall -> PIRCall(
                target = flat.target?.let { v(it) },
                callee = v(flat.callee),
                args = flat.args.map { PIRCallArg(v(it.value), convertArgKind(it.kind), it.keyword.ifEmpty { null }) },
                resolvedCallee = flat.resolvedCallee.ifEmpty { null },
            )

            is FlatBuildList -> PIRAssign(v(flat.target), PIRListExpr(flat.elements.map { v(it) }))
            is FlatBuildTuple -> PIRAssign(v(flat.target), PIRTupleExpr(flat.elements.map { v(it) }))
            is FlatBuildSet -> PIRAssign(v(flat.target), PIRSetExpr(flat.elements.map { v(it) }))
            is FlatBuildDict -> PIRAssign(v(flat.target), PIRDictExpr(flat.keys.map { v(it) }, flat.values.map { v(it) }))
            is FlatBuildSlice -> PIRAssign(v(flat.target), PIRSliceExpr(flat.lower?.let { v(it) }, flat.upper?.let { v(it) }, flat.step?.let { v(it) }))
            is FlatBuildString -> PIRAssign(v(flat.target), PIRStringExpr(flat.parts.map { v(it) }))

            is FlatGetIter -> PIRAssign(v(flat.target), PIRIterExpr(v(flat.iterable)))
            is FlatNextIter -> PIRNextIter(
                v(flat.target), v(flat.iterator), flat.bodyBlock, flat.exitBlock,
                blockStartIndex.getValue(flat.bodyBlock), blockStartIndex.getValue(flat.exitBlock),
            )
            is FlatUnpack -> PIRUnpack(flat.targets.map { v(it) }, v(flat.source), flat.starIndex)

            is FlatGoto -> PIRGoto(flat.targetBlock, blockStartIndex.getValue(flat.targetBlock))
            is FlatBranch -> PIRBranch(
                v(flat.condition), flat.trueBlock, flat.falseBlock,
                blockStartIndex.getValue(flat.trueBlock), blockStartIndex.getValue(flat.falseBlock),
            )
            is FlatReturn -> PIRReturn(flat.value?.let { v(it) })
            is FlatRaise -> PIRRaise(flat.exception?.let { v(it) }, flat.cause?.let { v(it) })
            is FlatExceptHandler -> PIRExceptHandler(flat.target?.let { v(it) }, flat.exceptionTypes.map { convertType(it) })

            is FlatYield -> PIRYield(flat.target?.let { v(it) }, flat.value?.let { v(it) })
            is FlatYieldFrom -> PIRYieldFrom(flat.target?.let { v(it) }, v(flat.iterable))
            is FlatAwait -> PIRAwait(flat.target?.let { v(it) }, v(flat.awaitable))

            is FlatDeleteLocal -> PIRDeleteLocal(v(flat.local))
            is FlatDeleteAttr -> PIRDeleteAttr(v(flat.obj), flat.attribute)
            is FlatDeleteSubscript -> PIRDeleteSubscript(v(flat.obj), v(flat.index))
            is FlatDeleteGlobal -> PIRDeleteGlobal(flat.name, flat.module)

            is FlatTypeCheck -> PIRAssign(v(flat.target), PIRTypeCheckExpr(v(flat.value), convertType(flat.type)))
            is FlatUnreachable -> PIRUnreachable
        }.also {
            sourcePositions[it] = flat.line to 0
            instructionIndices[it] = flatIndex
        }
    }

    private fun convertArgKind(kind: FlatArgKind): PIRCallArgKind = when (kind) {
        FlatArgKind.POSITIONAL -> PIRCallArgKind.POSITIONAL
        FlatArgKind.KEYWORD -> PIRCallArgKind.KEYWORD
        FlatArgKind.STAR -> PIRCallArgKind.STAR
        FlatArgKind.DOUBLE_STAR -> PIRCallArgKind.DOUBLE_STAR
    }

    private fun convertBinaryOp(l: PIRValue, r: PIRValue, op: FlatBinaryOperator): PIRBinaryExpr = when (op) {
        FlatBinaryOperator.ADD -> PIRAddExpr(l, r)
        FlatBinaryOperator.SUB -> PIRSubExpr(l, r)
        FlatBinaryOperator.MUL -> PIRMulExpr(l, r)
        FlatBinaryOperator.DIV -> PIRDivExpr(l, r)
        FlatBinaryOperator.FLOOR_DIV -> PIRFloorDivExpr(l, r)
        FlatBinaryOperator.MOD -> PIRModExpr(l, r)
        FlatBinaryOperator.POW -> PIRPowExpr(l, r)
        FlatBinaryOperator.MAT_MUL -> PIRMatMulExpr(l, r)
        FlatBinaryOperator.BIT_AND -> PIRBitAndExpr(l, r)
        FlatBinaryOperator.BIT_OR -> PIRBitOrExpr(l, r)
        FlatBinaryOperator.BIT_XOR -> PIRBitXorExpr(l, r)
        FlatBinaryOperator.LSHIFT -> PIRLShiftExpr(l, r)
        FlatBinaryOperator.RSHIFT -> PIRRShiftExpr(l, r)
    }

    private fun convertUnaryOp(operand: PIRValue, op: FlatUnaryOperator): PIRUnaryExpr = when (op) {
        FlatUnaryOperator.NEG -> PIRNegExpr(operand)
        FlatUnaryOperator.POS -> PIRPosExpr(operand)
        FlatUnaryOperator.NOT -> PIRNotExpr(operand)
        FlatUnaryOperator.INVERT -> PIRInvertExpr(operand)
    }

    private fun convertCompareOp(l: PIRValue, r: PIRValue, op: FlatCompareOperator): PIRCompareExpr = when (op) {
        FlatCompareOperator.EQ -> PIREqExpr(l, r)
        FlatCompareOperator.NE -> PIRNeExpr(l, r)
        FlatCompareOperator.LT -> PIRLtExpr(l, r)
        FlatCompareOperator.LE -> PIRLeExpr(l, r)
        FlatCompareOperator.GT -> PIRGtExpr(l, r)
        FlatCompareOperator.GE -> PIRGeExpr(l, r)
        FlatCompareOperator.IS -> PIRIsExpr(l, r)
        FlatCompareOperator.IS_NOT -> PIRIsNotExpr(l, r)
        FlatCompareOperator.IN -> PIRInExpr(l, r)
        FlatCompareOperator.NOT_IN -> PIRNotInExpr(l, r)
    }
}
