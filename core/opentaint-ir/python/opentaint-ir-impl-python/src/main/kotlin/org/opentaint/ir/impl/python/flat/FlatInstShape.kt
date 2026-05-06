package org.opentaint.ir.impl.python.flat

/**
 * Shape utilities for [FlatInst]: the result-target slot, the operand
 * positions, and substitution helpers. All implemented on top of
 * [FlatInstVisitor] so adding a new instruction kind is one method
 * per visitor.
 *
 * Conventions
 * -----------
 *
 * **Operands.** "Operand" means a [FlatValue] that the instruction *reads*
 * to produce its effect. Specifically:
 * - `FlatBindFunction.function` is treated as a name-binding target, NOT
 *   an operand. The lifted-function reference is part of the instruction's
 *   identity — closure-aware passes that need to see it should match
 *   directly on `FlatBindFunction`.
 *
 * **Targets.** "Target" means the [FlatValue] that the instruction
 * *writes* to (the result slot). Most instructions have exactly one
 * (sometimes nullable: `FlatCall`, `FlatYield`, `FlatYieldFrom`,
 * `FlatAwait`, `FlatExceptHandler`). [FlatUnpack] is the multi-target
 * exception: [target] returns `null` for it and [withTarget] is a
 * no-op. Use [unpackTargets] / [withUnpackTargets] for that case.
 */

/**
 * The single result-slot of [inst], or `null` if the instruction has none
 * (control-flow, side-effect-only stores/deletes, or [FlatUnpack] which
 * has multiple targets — use [unpackTargets] for it).
 */
val FlatInst.target: FlatValue?
    get() = accept(TargetExtractor)

/**
 * Return a copy of [inst] whose result slot is [target]. No-op when the
 * instruction has no result slot or has multiple targets ([FlatUnpack] —
 * use [withUnpackTargets]).
 *
 * The [target] argument's nullability must match the instruction's own
 * target nullability:
 * - For instructions whose `target` is non-nullable ([FlatAssign],
 *   [FlatBinOp], …), [target] must be non-null and a [FlatLocal].
 * - For instructions whose `target` is nullable ([FlatCall], [FlatYield],
 *   …), [target] may be null.
 *
 * Violations throw [IllegalArgumentException].
 */
fun FlatInst.withTarget(target: FlatValue?): FlatInst = accept(TargetReplacer(target))

/**
 * Apply [f] to every operand position of [inst] and return a copy with
 * the substituted operands. See the file KDoc for the operand definition;
 * `FlatBindFunction.function` is NOT considered an operand.
 */
fun FlatInst.mapOperand(f: (FlatValue) -> FlatValue): FlatInst = accept(OperandMapper(f))

/**
 * Every operand of [inst], in source-order. Convenience over [mapOperand]
 * for analyzers that only need to read.
 */
val FlatInst.operands: List<FlatValue>
    get() = buildList {
        mapOperand { v -> add(v); v }
    }

/** Multi-target view for [FlatUnpack]; empty for every other instruction. */
val FlatInst.unpackTargets: List<FlatValue>
    get() = if (this is FlatUnpack) targets else emptyList()

/** Replace [FlatUnpack.targets]; no-op for every other instruction. */
fun FlatInst.withUnpackTargets(targets: List<FlatValue>): FlatInst =
    if (this is FlatUnpack) copy(targets = targets) else this

/* ------------------------------------------------------------------ */
/* Visitor implementations backing the extensions above.              */
/* ------------------------------------------------------------------ */

private object TargetExtractor : FlatInstVisitor<FlatValue?> {
    override fun visitAssign(inst: FlatAssign) = inst.target
    override fun visitLoadAttr(inst: FlatLoadAttr) = inst.target
    override fun visitStoreAttr(inst: FlatStoreAttr): FlatValue? = null
    override fun visitLoadSubscript(inst: FlatLoadSubscript) = inst.target
    override fun visitStoreSubscript(inst: FlatStoreSubscript): FlatValue? = null
    override fun visitLoadGlobal(inst: FlatLoadGlobal) = inst.target
    override fun visitStoreGlobal(inst: FlatStoreGlobal): FlatValue? = null
    override fun visitBindFunction(inst: FlatBindFunction) = inst.target
    override fun visitBinOp(inst: FlatBinOp) = inst.target
    override fun visitUnaryOp(inst: FlatUnaryOp) = inst.target
    override fun visitCompare(inst: FlatCompare) = inst.target
    override fun visitCall(inst: FlatCall) = inst.target
    override fun visitBuildList(inst: FlatBuildList) = inst.target
    override fun visitBuildTuple(inst: FlatBuildTuple) = inst.target
    override fun visitBuildSet(inst: FlatBuildSet) = inst.target
    override fun visitBuildDict(inst: FlatBuildDict) = inst.target
    override fun visitBuildSlice(inst: FlatBuildSlice) = inst.target
    override fun visitBuildString(inst: FlatBuildString) = inst.target
    override fun visitGetIter(inst: FlatGetIter) = inst.target
    override fun visitNextIter(inst: FlatNextIter) = inst.target
    override fun visitUnpack(inst: FlatUnpack): FlatValue? = null
    override fun visitGoto(inst: FlatGoto): FlatValue? = null
    override fun visitBranch(inst: FlatBranch): FlatValue? = null
    override fun visitReturn(inst: FlatReturn): FlatValue? = null
    override fun visitRaise(inst: FlatRaise): FlatValue? = null
    override fun visitExceptHandler(inst: FlatExceptHandler) = inst.target
    override fun visitYield(inst: FlatYield) = inst.target
    override fun visitYieldFrom(inst: FlatYieldFrom) = inst.target
    override fun visitAwait(inst: FlatAwait) = inst.target
    override fun visitDeleteLocal(inst: FlatDeleteLocal): FlatValue? = null
    override fun visitDeleteAttr(inst: FlatDeleteAttr): FlatValue? = null
    override fun visitDeleteSubscript(inst: FlatDeleteSubscript): FlatValue? = null
    override fun visitDeleteGlobal(inst: FlatDeleteGlobal): FlatValue? = null
    override fun visitTypeCheck(inst: FlatTypeCheck) = inst.target
    override fun visitUnreachable(inst: FlatUnreachable): FlatValue? = null
}

private class TargetReplacer(private val newTarget: FlatValue?) : FlatInstVisitor<FlatInst> {
    private fun required(): FlatValue =
        newTarget ?: throw IllegalArgumentException("withTarget(null) on instruction with non-nullable target")

    override fun visitAssign(inst: FlatAssign) = inst.copy(target = required())
    override fun visitLoadAttr(inst: FlatLoadAttr) = inst.copy(target = required())
    override fun visitStoreAttr(inst: FlatStoreAttr): FlatInst = inst
    override fun visitLoadSubscript(inst: FlatLoadSubscript) = inst.copy(target = required())
    override fun visitStoreSubscript(inst: FlatStoreSubscript): FlatInst = inst
    override fun visitLoadGlobal(inst: FlatLoadGlobal) = inst.copy(target = required())
    override fun visitStoreGlobal(inst: FlatStoreGlobal): FlatInst = inst
    override fun visitBindFunction(inst: FlatBindFunction) = inst.copy(target = required())
    override fun visitBinOp(inst: FlatBinOp) = inst.copy(target = required())
    override fun visitUnaryOp(inst: FlatUnaryOp) = inst.copy(target = required())
    override fun visitCompare(inst: FlatCompare) = inst.copy(target = required())
    override fun visitCall(inst: FlatCall) = inst.copy(target = newTarget)
    override fun visitBuildList(inst: FlatBuildList) = inst.copy(target = required())
    override fun visitBuildTuple(inst: FlatBuildTuple) = inst.copy(target = required())
    override fun visitBuildSet(inst: FlatBuildSet) = inst.copy(target = required())
    override fun visitBuildDict(inst: FlatBuildDict) = inst.copy(target = required())
    override fun visitBuildSlice(inst: FlatBuildSlice) = inst.copy(target = required())
    override fun visitBuildString(inst: FlatBuildString) = inst.copy(target = required())
    override fun visitGetIter(inst: FlatGetIter) = inst.copy(target = required())
    override fun visitNextIter(inst: FlatNextIter) = inst.copy(target = required())
    override fun visitUnpack(inst: FlatUnpack): FlatInst = inst   // multi-target; use withUnpackTargets
    override fun visitGoto(inst: FlatGoto): FlatInst = inst
    override fun visitBranch(inst: FlatBranch): FlatInst = inst
    override fun visitReturn(inst: FlatReturn): FlatInst = inst
    override fun visitRaise(inst: FlatRaise): FlatInst = inst
    override fun visitExceptHandler(inst: FlatExceptHandler) = inst.copy(target = newTarget)
    override fun visitYield(inst: FlatYield) = inst.copy(target = newTarget)
    override fun visitYieldFrom(inst: FlatYieldFrom) = inst.copy(target = newTarget)
    override fun visitAwait(inst: FlatAwait) = inst.copy(target = newTarget)
    override fun visitDeleteLocal(inst: FlatDeleteLocal): FlatInst = inst
    override fun visitDeleteAttr(inst: FlatDeleteAttr): FlatInst = inst
    override fun visitDeleteSubscript(inst: FlatDeleteSubscript): FlatInst = inst
    override fun visitDeleteGlobal(inst: FlatDeleteGlobal): FlatInst = inst
    override fun visitTypeCheck(inst: FlatTypeCheck) = inst.copy(target = required())
    override fun visitUnreachable(inst: FlatUnreachable): FlatInst = inst
}

private class OperandMapper(private val f: (FlatValue) -> FlatValue) : FlatInstVisitor<FlatInst> {
    private fun n(v: FlatValue?): FlatValue? = if (v == null) null else f(v)

    override fun visitAssign(inst: FlatAssign) = inst.copy(source = f(inst.source))
    override fun visitLoadAttr(inst: FlatLoadAttr) = inst.copy(obj = f(inst.obj))
    override fun visitStoreAttr(inst: FlatStoreAttr) = inst.copy(obj = f(inst.obj), value = f(inst.value))
    override fun visitLoadSubscript(inst: FlatLoadSubscript) = inst.copy(obj = f(inst.obj), index = f(inst.index))
    override fun visitStoreSubscript(inst: FlatStoreSubscript) =
        inst.copy(obj = f(inst.obj), index = f(inst.index), value = f(inst.value))
    override fun visitLoadGlobal(inst: FlatLoadGlobal): FlatInst = inst
    override fun visitStoreGlobal(inst: FlatStoreGlobal) = inst.copy(value = f(inst.value))
    override fun visitBindFunction(inst: FlatBindFunction): FlatInst = inst   // function ref is not an operand
    override fun visitBinOp(inst: FlatBinOp) = inst.copy(left = f(inst.left), right = f(inst.right))
    override fun visitUnaryOp(inst: FlatUnaryOp) = inst.copy(operand = f(inst.operand))
    override fun visitCompare(inst: FlatCompare) = inst.copy(left = f(inst.left), right = f(inst.right))
    override fun visitCall(inst: FlatCall) = inst.copy(
        callee = f(inst.callee),
        args = inst.args.map { it.copy(value = f(it.value)) },
    )
    override fun visitBuildList(inst: FlatBuildList) = inst.copy(elements = inst.elements.map(f))
    override fun visitBuildTuple(inst: FlatBuildTuple) = inst.copy(elements = inst.elements.map(f))
    override fun visitBuildSet(inst: FlatBuildSet) = inst.copy(elements = inst.elements.map(f))
    override fun visitBuildDict(inst: FlatBuildDict) =
        inst.copy(keys = inst.keys.map(f), values = inst.values.map(f))
    override fun visitBuildSlice(inst: FlatBuildSlice) =
        inst.copy(lower = n(inst.lower), upper = n(inst.upper), step = n(inst.step))
    override fun visitBuildString(inst: FlatBuildString) = inst.copy(parts = inst.parts.map(f))
    override fun visitGetIter(inst: FlatGetIter) = inst.copy(iterable = f(inst.iterable))
    override fun visitNextIter(inst: FlatNextIter) = inst.copy(iterator = f(inst.iterator))
    override fun visitUnpack(inst: FlatUnpack) = inst.copy(source = f(inst.source))
    override fun visitGoto(inst: FlatGoto): FlatInst = inst
    override fun visitBranch(inst: FlatBranch) = inst.copy(condition = f(inst.condition))
    override fun visitReturn(inst: FlatReturn) = inst.copy(value = n(inst.value))
    override fun visitRaise(inst: FlatRaise) = inst.copy(exception = n(inst.exception), cause = n(inst.cause))
    override fun visitExceptHandler(inst: FlatExceptHandler): FlatInst = inst
    override fun visitYield(inst: FlatYield) = inst.copy(value = n(inst.value))
    override fun visitYieldFrom(inst: FlatYieldFrom) = inst.copy(iterable = f(inst.iterable))
    override fun visitAwait(inst: FlatAwait) = inst.copy(awaitable = f(inst.awaitable))
    override fun visitDeleteLocal(inst: FlatDeleteLocal) = inst.copy(local = f(inst.local))
    override fun visitDeleteAttr(inst: FlatDeleteAttr) = inst.copy(obj = f(inst.obj))
    override fun visitDeleteSubscript(inst: FlatDeleteSubscript) = inst.copy(obj = f(inst.obj), index = f(inst.index))
    override fun visitDeleteGlobal(inst: FlatDeleteGlobal): FlatInst = inst
    override fun visitTypeCheck(inst: FlatTypeCheck) = inst.copy(value = f(inst.value))
    override fun visitUnreachable(inst: FlatUnreachable): FlatInst = inst
}
