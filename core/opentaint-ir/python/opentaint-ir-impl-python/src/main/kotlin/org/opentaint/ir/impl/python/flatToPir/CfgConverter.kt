package org.opentaint.ir.impl.python.flatToPir

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRCFGImpl
import org.opentaint.ir.impl.python.PIRLocationImpl
import org.opentaint.ir.impl.python.flat.*

/**
 * Output of [CfgConverter.convert]: the built CFG plus the locations of every
 * instruction in `cfg.instList` (position-aligned). Each [PIRLocationImpl] has
 * `index` already set; the caller must wire `method` once the owning function
 * is built.
 */
class CfgConversionResult(val cfg: PIRCFG, val locations: List<PIRLocationImpl>)

/**
 * Per-function Flat → PIR CFG converter. One instance per function: holds
 * the [LocalIndexer] that hands out parameter and body-local indices on the
 * fly, so a single instance can only convert one CFG. Construct via the
 * companion's [convert] factory.
 */
class CfgConverter private constructor(parameters: List<FlatParameter>, qualifiedName: String) {

    private val indexer = LocalIndexer(parameters, qualifiedName)

    companion object {
        fun convert(flat: FlatCFG, parameters: List<FlatParameter>, qualifiedName: String): CfgConversionResult =
            CfgConverter(parameters, qualifiedName).convert(flat)
    }

    private fun convert(flat: FlatCFG): CfgConversionResult {
        val sortedBlocks = flat.blocks.sortedBy { it.label }
        val blockStartIndex = calculateBlockToStartIdx(sortedBlocks)

        val instList = ArrayList<PIRInstruction>()
        val locations = ArrayList<PIRLocationImpl>()
        val instToBlock = ArrayList<Int>()
        for (block in sortedBlocks) {
            for (flatInst in block.instructions) {
                val loc = PIRLocationImpl(index = instList.size)
                instList.add(convertInstruction(flatInst, blockStartIndex, loc, flatInst.physicalLocation))
                locations.add(loc)
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
        return CfgConversionResult(cfg, locations)
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
        is FlatLocal -> PIRLocalVar(flat.name, TypeConverter.convert(flat.type), indexer.localIndex(flat.name))
        is FlatParameterRef -> PIRParameterRef(flat.name, TypeConverter.convert(flat.type), indexer.paramIndex(flat.name))
        is FlatGlobalRef -> PIRGlobalRef(flat.qualifiedName, PIRAnyType)
        is FlatModuleRef -> PIRModuleRef(flat.module, PIRAnyType)
        is FlatConst -> ConstConverter.convert(flat)
    }

    // ─── Instruction conversion ───────────────────────────

    private fun convertInstruction(
        flat: FlatInst,
        blockStartIndex: Map<Int, Int>,
        loc: PIRLocation,
        phys: PIRPhysicalLocation?,
    ): PIRInstruction = when (flat) {
        is FlatAssign -> PIRAssign(v(flat.target), v(flat.source), loc, phys)
        is FlatLoadAttr -> PIRLoadAttr(v(flat.target), v(flat.obj), flat.attribute, TypeConverter.convert(flat.type), loc, phys)
        is FlatStoreAttr -> PIRStoreAttr(v(flat.obj), flat.attribute, v(flat.value), loc, phys)
        is FlatLoadSubscript -> PIRAssign(v(flat.target), PIRSubscriptExpr(v(flat.obj), v(flat.index), TypeConverter.convert(flat.type)), loc, phys)
        is FlatStoreSubscript -> PIRStoreSubscript(v(flat.obj), v(flat.index), v(flat.value), loc, phys)
        is FlatLoadGlobal -> PIRAssign(v(flat.target), PIRGlobalRef("${flat.module}.${flat.name}"), loc, phys)
        is FlatStoreGlobal -> PIRStoreGlobal(flat.name, flat.module, v(flat.value), loc, phys)
        is FlatBindFunction -> PIRAssign(
            v(flat.target),
            PIRBindFunctionExpr(v(flat.function) as PIRGlobalRef),
            loc,
            phys,
        )

        is FlatBinOp -> PIRAssign(v(flat.target), flat.op.toPir(v(flat.left), v(flat.right)), loc, phys)
        is FlatUnaryOp -> PIRAssign(v(flat.target), flat.op.toPir(v(flat.operand)), loc, phys)
        is FlatCompare -> PIRAssign(v(flat.target), flat.op.toPir(v(flat.left), v(flat.right)), loc, phys)

        is FlatCall -> PIRCall(
            target = flat.target?.let { v(it) },
            callee = v(flat.callee),
            args = flat.args.map { PIRCallArg(v(it.value), it.kind.toPir(), it.keyword) },
            resolvedCallee = flat.resolvedCallee,
            location = loc,
            physicalLocation = phys,
        )

        is FlatBuildList -> PIRAssign(v(flat.target), PIRListExpr(flat.elements.map { v(it) }), loc, phys)
        is FlatBuildTuple -> PIRAssign(v(flat.target), PIRTupleExpr(flat.elements.map { v(it) }), loc, phys)
        is FlatBuildSet -> PIRAssign(v(flat.target), PIRSetExpr(flat.elements.map { v(it) }), loc, phys)
        is FlatBuildDict -> PIRAssign(v(flat.target), PIRDictExpr(flat.keys.map { v(it) }, flat.values.map { v(it) }), loc, phys)
        is FlatBuildSlice -> PIRAssign(v(flat.target), PIRSliceExpr(flat.lower?.let { v(it) }, flat.upper?.let { v(it) }, flat.step?.let { v(it) }), loc, phys)
        is FlatBuildString -> PIRAssign(v(flat.target), PIRStringExpr(flat.parts.map { v(it) }), loc, phys)

        is FlatGetIter -> PIRAssign(v(flat.target), PIRIterExpr(v(flat.iterable)), loc, phys)
        is FlatNextIter -> PIRNextIter(
            v(flat.target), v(flat.iterator), flat.bodyBlock, flat.exitBlock,
            blockStartIndex.getValue(flat.bodyBlock), blockStartIndex.getValue(flat.exitBlock),
            loc, phys,
        )
        is FlatUnpack -> PIRUnpack(flat.targets.map { v(it) }, v(flat.source), flat.starIndex, loc, phys)

        is FlatGoto -> PIRGoto(flat.targetBlock, blockStartIndex.getValue(flat.targetBlock), loc, phys)
        is FlatBranch -> PIRBranch(
            v(flat.condition), flat.trueBlock, flat.falseBlock,
            blockStartIndex.getValue(flat.trueBlock), blockStartIndex.getValue(flat.falseBlock),
            loc, phys,
        )
        is FlatReturn -> PIRReturn(flat.value?.let { v(it) }, loc, phys)
        is FlatRaise -> PIRRaise(flat.exception?.let { v(it) }, flat.cause?.let { v(it) }, loc, phys)
        is FlatExceptHandler -> PIRExceptHandler(flat.target?.let { v(it) }, flat.exceptionTypes.map { TypeConverter.convert(it) }, loc, phys)

        is FlatYield -> PIRYield(flat.target?.let { v(it) }, flat.value?.let { v(it) }, loc, phys)
        is FlatYieldFrom -> PIRYieldFrom(flat.target?.let { v(it) }, v(flat.iterable), loc, phys)
        is FlatAwait -> PIRAwait(flat.target?.let { v(it) }, v(flat.awaitable), loc, phys)

        is FlatDeleteLocal -> PIRDeleteLocal(v(flat.local), loc, phys)
        is FlatDeleteAttr -> PIRDeleteAttr(v(flat.obj), flat.attribute, loc, phys)
        is FlatDeleteSubscript -> PIRDeleteSubscript(v(flat.obj), v(flat.index), loc, phys)
        is FlatDeleteGlobal -> PIRDeleteGlobal(flat.name, flat.module, loc, phys)

        is FlatTypeCheck -> PIRAssign(v(flat.target), PIRTypeCheckExpr(v(flat.value), TypeConverter.convert(flat.type)), loc, phys)
        is FlatUnreachable -> PIRUnreachable
    }
}

/**
 * Assigns a unique [Int] index to each parameter and body-local of one
 * function. Parameters keep their signature order (0..N-1). Body locals
 * are indexed in first-appearance order starting at `parameters.size`,
 * so the param + local index space is disjoint within a function.
 */
private class LocalIndexer(parameters: List<FlatParameter>, private val qualifiedName: String) {
    private val paramIndices: Map<String, Int> =
        parameters.withIndex().associate { (i, p) -> p.name to i }
    private val localIndices: MutableMap<String, Int> = HashMap()
    private var nextLocalIndex: Int = parameters.size

    fun paramIndex(name: String): Int =
        paramIndices[name] ?: error("FlatParameterRef('$name') has no matching parameter in $qualifiedName")

    fun localIndex(name: String): Int =
        localIndices.getOrPut(name) { nextLocalIndex++ }
}

