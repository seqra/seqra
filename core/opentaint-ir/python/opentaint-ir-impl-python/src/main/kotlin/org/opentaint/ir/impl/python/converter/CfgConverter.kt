package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRCFGImpl
import org.opentaint.ir.impl.python.builder.*

/**
 * Output of [CfgConverter.convert]: the built CFG plus a parallel list of
 * source lines (one entry per instruction in `cfg.instList`, indexed by the
 * instruction's position in `instList`). Callers use the lines to build
 * `PIRLocation`s once they have a `PIRFunction` to bind to.
 */
class CfgConversionResult(val cfg: PIRCFG, val lines: List<Int>)

object CfgConverter {

    fun convert(flat: FlatCFG): CfgConversionResult {
        val sortedBlocks = flat.blocks.sortedBy { it.label }
        val blockStartIndex = calculateBlockToStartIdx(sortedBlocks)

        val instList = ArrayList<PIRInstruction>()
        val lines = ArrayList<Int>()
        val instToBlock = ArrayList<Int>()
        for (block in sortedBlocks) {
            for (flatInst in block.instructions) {
                instList.add(convertInstruction(flatInst, blockStartIndex))
                lines.add(flatInst.line)
                instToBlock.add(block.label)
            }
        }

        val blocks = sortedBlocks.map { block ->
            val startIdx = blockStartIndex.getValue(block.label)
            PIRBasicBlock(
                label = block.label,
                instructions = instList.subList(startIdx, startIdx + block.instructions.size),
                exceptionHandlers = block.exceptionHandlers,
            )
        }

        val cfg = PIRCFGImpl(
            blocks = blocks,
            instList = instList,
            entryLabel = flat.entryBlock,
            exitLabels = flat.exitBlocks.toSet(),
            instToBlock = instToBlock,
        )
        return CfgConversionResult(cfg, lines)
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
        is FlatLocal -> PIRLocal(flat.name, TypeConverter.convert(flat.type))
        is FlatGlobalRef -> PIRGlobalRef(flat.name, flat.module, PIRAnyType)
        is FlatParameterRef -> PIRParameterRef(flat.name, PIRAnyType)
        is FlatConst -> ConstConverter.convert(flat)
    }

    // ─── Instruction conversion ───────────────────────────

    private fun convertInstruction(flat: FlatInst, blockStartIndex: Map<Int, Int>): PIRInstruction =
        when (flat) {
            is FlatAssign -> PIRAssign(v(flat.target), v(flat.source))
            is FlatLoadAttr -> PIRLoadAttr(v(flat.target), v(flat.obj), flat.attribute, TypeConverter.convert(flat.type))
            is FlatStoreAttr -> PIRStoreAttr(v(flat.obj), flat.attribute, v(flat.value))
            is FlatLoadSubscript -> PIRAssign(v(flat.target), PIRSubscriptExpr(v(flat.obj), v(flat.index), TypeConverter.convert(flat.type)))
            is FlatStoreSubscript -> PIRStoreSubscript(v(flat.obj), v(flat.index), v(flat.value))
            is FlatLoadGlobal -> PIRAssign(v(flat.target), PIRGlobalRef(flat.name, flat.module))
            is FlatStoreGlobal -> PIRStoreGlobal(flat.name, flat.module, v(flat.value))
            is FlatLoadClosure -> PIRAssign(v(flat.target), PIRGlobalRef(flat.name, ""))
            is FlatStoreClosure -> PIRStoreClosure(flat.name, flat.depth, v(flat.value))

            is FlatBinOp -> PIRAssign(v(flat.target), flat.op.toPir(v(flat.left), v(flat.right)))
            is FlatUnaryOp -> PIRAssign(v(flat.target), flat.op.toPir(v(flat.operand)))
            is FlatCompare -> PIRAssign(v(flat.target), flat.op.toPir(v(flat.left), v(flat.right)))

            is FlatCall -> PIRCall(
                target = flat.target?.let { v(it) },
                callee = v(flat.callee),
                args = flat.args.map { PIRCallArg(v(it.value), it.kind.toPir(), it.keyword) },
                resolvedCallee = flat.resolvedCallee,
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
            is FlatExceptHandler -> PIRExceptHandler(flat.target?.let { v(it) }, flat.exceptionTypes.map { TypeConverter.convert(it) })

            is FlatYield -> PIRYield(flat.target?.let { v(it) }, flat.value?.let { v(it) })
            is FlatYieldFrom -> PIRYieldFrom(flat.target?.let { v(it) }, v(flat.iterable))
            is FlatAwait -> PIRAwait(flat.target?.let { v(it) }, v(flat.awaitable))

            is FlatDeleteLocal -> PIRDeleteLocal(v(flat.local))
            is FlatDeleteAttr -> PIRDeleteAttr(v(flat.obj), flat.attribute)
            is FlatDeleteSubscript -> PIRDeleteSubscript(v(flat.obj), v(flat.index))
            is FlatDeleteGlobal -> PIRDeleteGlobal(flat.name, flat.module)

            is FlatTypeCheck -> PIRAssign(v(flat.target), PIRTypeCheckExpr(v(flat.value), TypeConverter.convert(flat.type)))
            is FlatUnreachable -> PIRUnreachable
        }

}
