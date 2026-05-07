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
 * exception: [targets] returns `null` for it. Use [unpackTargets] to read
 * its slots; [mapTarget] handles writing uniformly across single-target
 * and multi-target instructions.
 */

/**
 * The single result-slot of [inst], or `null` if the instruction has none
 * (control-flow, side-effect-only stores/deletes, or [FlatUnpack] which
 * has multiple targets — use [unpackTargets] for it).
 */
val FlatInst.targets: List<FlatValue>
    get() = accept(TargetExtractor)

/**
 * Apply [f] to every target position of [inst] and return a copy. For
 * single-target instructions ([FlatAssign], [FlatBinOp], …) [f] is called
 * once on the target; for [FlatUnpack] it is called once per slot in
 * `targets`; for instructions with no target ([FlatGoto], [FlatStoreAttr],
 * …) it is not called.
 *
 * Identity-preserving: when every call to [f] returns its input
 * (referential identity), the original [inst] is returned unchanged.
 *
 * For instructions whose target is nullable ([FlatCall], [FlatYield], …)
 * [f] is invoked only when the target is present. The mapper itself does
 * not introduce or remove a target — it cannot turn a non-null target
 * into null or vice versa.
 */
fun FlatInst.mapTarget(f: (FlatValue) -> FlatValue): FlatInst = accept(TargetMapper(f))

/**
 * Apply [f] to every operand position of [inst] and return a copy with
 * the substituted operands. See the file KDoc for the operand definition;
 * `FlatBindFunction.function` is NOT considered an operand.
 *
 * Identity-preserving: when every call to [f] returns its input
 * (referential identity), the original [inst] is returned unchanged.
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

/* ------------------------------------------------------------------ */
/* Visitor implementations backing the extensions above.              */
/* ------------------------------------------------------------------ */

private object TargetExtractor : FlatInstVisitor<List<FlatValue>> {
    override fun visitAssign(inst: FlatAssign) = listOf(inst.target)
    override fun visitLoadAttr(inst: FlatLoadAttr) = listOf(inst.target)
    override fun visitStoreAttr(inst: FlatStoreAttr) = emptyList<FlatValue>()
    override fun visitLoadSubscript(inst: FlatLoadSubscript) = listOf(inst.target)
    override fun visitStoreSubscript(inst: FlatStoreSubscript) = emptyList<FlatValue>()
    override fun visitLoadGlobal(inst: FlatLoadGlobal) = listOf(inst.target)
    override fun visitStoreGlobal(inst: FlatStoreGlobal) = emptyList<FlatValue>()
    override fun visitBindFunction(inst: FlatBindFunction) = listOf(inst.target)
    override fun visitBinOp(inst: FlatBinOp) = listOf(inst.target)
    override fun visitUnaryOp(inst: FlatUnaryOp) = listOf(inst.target)
    override fun visitCompare(inst: FlatCompare) = listOf(inst.target)
    override fun visitCall(inst: FlatCall) = inst.target?.let { listOf(it) }.orEmpty()
    override fun visitBuildList(inst: FlatBuildList) = listOf(inst.target)
    override fun visitBuildTuple(inst: FlatBuildTuple) = listOf(inst.target)
    override fun visitBuildSet(inst: FlatBuildSet) = listOf(inst.target)
    override fun visitBuildDict(inst: FlatBuildDict) = listOf(inst.target)
    override fun visitBuildSlice(inst: FlatBuildSlice) = listOf(inst.target)
    override fun visitBuildString(inst: FlatBuildString) = listOf(inst.target)
    override fun visitGetIter(inst: FlatGetIter) = listOf(inst.target)
    override fun visitNextIter(inst: FlatNextIter) = listOf(inst.target)
    override fun visitUnpack(inst: FlatUnpack) = emptyList<FlatValue>()
    override fun visitGoto(inst: FlatGoto) = emptyList<FlatValue>()
    override fun visitBranch(inst: FlatBranch) = emptyList<FlatValue>()
    override fun visitReturn(inst: FlatReturn) = emptyList<FlatValue>()
    override fun visitRaise(inst: FlatRaise) = emptyList<FlatValue>()
    override fun visitExceptHandler(inst: FlatExceptHandler) = inst.target?.let { listOf(it) }.orEmpty()
    override fun visitYield(inst: FlatYield) = inst.target?.let { listOf(it) }.orEmpty()
    override fun visitYieldFrom(inst: FlatYieldFrom) = inst.target?.let { listOf(it) }.orEmpty()
    override fun visitAwait(inst: FlatAwait) = inst.target?.let { listOf(it) }.orEmpty()
    override fun visitDeleteLocal(inst: FlatDeleteLocal) = emptyList<FlatValue>()
    override fun visitDeleteAttr(inst: FlatDeleteAttr) = emptyList<FlatValue>()
    override fun visitDeleteSubscript(inst: FlatDeleteSubscript) = emptyList<FlatValue>()
    override fun visitDeleteGlobal(inst: FlatDeleteGlobal) = emptyList<FlatValue>()
    override fun visitTypeCheck(inst: FlatTypeCheck) = listOf(inst.target)
    override fun visitUnreachable(inst: FlatUnreachable) = emptyList<FlatValue>()
}

private class TargetMapper(private val f: (FlatValue) -> FlatValue) : FlatInstVisitor<FlatInst> {
    override fun visitAssign(inst: FlatAssign) = transform(inst, inst.target, f) { inst.copy(target = it) }
    override fun visitLoadAttr(inst: FlatLoadAttr) = transform(inst, inst.target, f) { inst.copy(target = it) }
    override fun visitStoreAttr(inst: FlatStoreAttr): FlatInst = inst
    override fun visitLoadSubscript(inst: FlatLoadSubscript) = transform(inst, inst.target, f) { inst.copy(target = it) }
    override fun visitStoreSubscript(inst: FlatStoreSubscript): FlatInst = inst
    override fun visitLoadGlobal(inst: FlatLoadGlobal) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitStoreGlobal(inst: FlatStoreGlobal): FlatInst = inst
    override fun visitBindFunction(inst: FlatBindFunction) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBinOp(inst: FlatBinOp) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitUnaryOp(inst: FlatUnaryOp) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitCompare(inst: FlatCompare) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitCall(inst: FlatCall) = transformNullable(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBuildList(inst: FlatBuildList) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBuildTuple(inst: FlatBuildTuple) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBuildSet(inst: FlatBuildSet) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBuildDict(inst: FlatBuildDict) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBuildSlice(inst: FlatBuildSlice) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitBuildString(inst: FlatBuildString) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitGetIter(inst: FlatGetIter) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitNextIter(inst: FlatNextIter) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitUnpack(inst: FlatUnpack): FlatInst =
        transformList(inst, inst.targets, f) { inst.copy(targets = it) }
    override fun visitGoto(inst: FlatGoto): FlatInst = inst
    override fun visitBranch(inst: FlatBranch): FlatInst = inst
    override fun visitReturn(inst: FlatReturn): FlatInst = inst
    override fun visitRaise(inst: FlatRaise): FlatInst = inst
    override fun visitExceptHandler(inst: FlatExceptHandler) = transformNullable(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitYield(inst: FlatYield) = transformNullable(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitYieldFrom(inst: FlatYieldFrom) = transformNullable(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitAwait(inst: FlatAwait) = transformNullable(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitDeleteLocal(inst: FlatDeleteLocal): FlatInst = inst
    override fun visitDeleteAttr(inst: FlatDeleteAttr): FlatInst = inst
    override fun visitDeleteSubscript(inst: FlatDeleteSubscript): FlatInst = inst
    override fun visitDeleteGlobal(inst: FlatDeleteGlobal): FlatInst = inst
    override fun visitTypeCheck(inst: FlatTypeCheck) = transform(inst, inst.target, f)  { inst.copy(target = it) }
    override fun visitUnreachable(inst: FlatUnreachable): FlatInst = inst
}

private class OperandMapper(private val f: (FlatValue) -> FlatValue) : FlatInstVisitor<FlatInst> {
    override fun visitAssign(inst: FlatAssign) =
        transform(inst, inst.source, f) { inst.copy(source = it) }
    override fun visitLoadAttr(inst: FlatLoadAttr) =
        transform(inst, inst.obj, f) { inst.copy(obj = it) }
    override fun visitStoreAttr(inst: FlatStoreAttr) =
        transform(inst, inst.obj, inst.value, f) { o, v -> inst.copy(obj = o, value = v) }
    override fun visitLoadSubscript(inst: FlatLoadSubscript) =
        transform(inst, inst.obj, inst.index, f) { o, i -> inst.copy(obj = o, index = i) }
    override fun visitStoreSubscript(inst: FlatStoreSubscript) =
        transform(inst, inst.obj, inst.index, inst.value, f) { o, i, v ->
            inst.copy(obj = o, index = i, value = v)
        }
    override fun visitLoadGlobal(inst: FlatLoadGlobal): FlatInst = inst
    override fun visitStoreGlobal(inst: FlatStoreGlobal) =
        transform(inst, inst.value, f) { inst.copy(value = it) }
    override fun visitBindFunction(inst: FlatBindFunction): FlatInst = inst   // function ref is not an operand
    override fun visitBinOp(inst: FlatBinOp) =
        transform(inst, inst.left, inst.right, f) { l, r -> inst.copy(left = l, right = r) }
    override fun visitUnaryOp(inst: FlatUnaryOp) =
        transform(inst, inst.operand, f) { inst.copy(operand = it) }
    override fun visitCompare(inst: FlatCompare) =
        transform(inst, inst.left, inst.right, f) { l, r -> inst.copy(left = l, right = r) }

    override fun visitCall(inst: FlatCall): FlatInst {
        val newCallee = f(inst.callee)
        var argsChanged = false
        val newArgs = ArrayList<FlatCallArg>(inst.args.size)
        for (a in inst.args) {
            val nv = f(a.value)
            if (nv !== a.value) {
                argsChanged = true
                newArgs.add(a.copy(value = nv))
            } else {
                newArgs.add(a)
            }
        }
        return if (newCallee === inst.callee && !argsChanged) inst
        else inst.copy(callee = newCallee, args = if (argsChanged) newArgs else inst.args)
    }
    override fun visitBuildList(inst: FlatBuildList): FlatInst =
        transformList(inst, inst.elements, f) { inst.copy(elements = it) }
    override fun visitBuildTuple(inst: FlatBuildTuple): FlatInst =
        transformList(inst, inst.elements, f) { inst.copy(elements = it) }
    override fun visitBuildSet(inst: FlatBuildSet): FlatInst =
        transformList(inst, inst.elements, f) { inst.copy(elements = it) }
    override fun visitBuildDict(inst: FlatBuildDict): FlatInst {
        val nk = mappedList(inst.keys, f)
        val nv = mappedList(inst.values, f)
        return if (nk === inst.keys && nv === inst.values) inst
        else inst.copy(keys = nk, values = nv)
    }
    override fun visitBuildSlice(inst: FlatBuildSlice) =
        transformNullable(inst, inst.lower, inst.upper, inst.step, f) { l, u, s ->
            inst.copy(lower = l, upper = u, step = s)
        }
    override fun visitBuildString(inst: FlatBuildString): FlatInst =
        transformList(inst, inst.parts, f) { inst.copy(parts = it) }
    override fun visitGetIter(inst: FlatGetIter) =
        transform(inst, inst.iterable, f) { inst.copy(iterable = it) }
    override fun visitNextIter(inst: FlatNextIter) =
        transform(inst, inst.iterator, f) { inst.copy(iterator = it) }
    override fun visitUnpack(inst: FlatUnpack) =
        transform(inst, inst.source, f) { inst.copy(source = it) }
    override fun visitGoto(inst: FlatGoto): FlatInst = inst
    override fun visitBranch(inst: FlatBranch) =
        transform(inst, inst.condition, f) { inst.copy(condition = it) }
    override fun visitReturn(inst: FlatReturn) =
        transformNullable(inst, inst.value, f) { inst.copy(value = it) }
    override fun visitRaise(inst: FlatRaise) =
        transformNullable(inst, inst.exception, inst.cause, f) { e, c ->
            inst.copy(exception = e, cause = c)
        }
    override fun visitExceptHandler(inst: FlatExceptHandler): FlatInst = inst
    override fun visitYield(inst: FlatYield) =
        transformNullable(inst, inst.value, f) { inst.copy(value = it) }
    override fun visitYieldFrom(inst: FlatYieldFrom) =
        transform(inst, inst.iterable, f) { inst.copy(iterable = it) }
    override fun visitAwait(inst: FlatAwait) =
        transform(inst, inst.awaitable, f) { inst.copy(awaitable = it) }
    override fun visitDeleteLocal(inst: FlatDeleteLocal) =
        transform(inst, inst.local, f) { inst.copy(local = it) }
    override fun visitDeleteAttr(inst: FlatDeleteAttr) =
        transform(inst, inst.obj, f) { inst.copy(obj = it) }
    override fun visitDeleteSubscript(inst: FlatDeleteSubscript) =
        transform(inst, inst.obj, inst.index, f) { o, i -> inst.copy(obj = o, index = i) }
    override fun visitDeleteGlobal(inst: FlatDeleteGlobal): FlatInst = inst
    override fun visitTypeCheck(inst: FlatTypeCheck) =
        transform(inst, inst.value, f) { inst.copy(value = it) }
    override fun visitUnreachable(inst: FlatUnreachable): FlatInst = inst

    /**
     * Map [list] through [f]. Returns [list] itself when every element is
     * unchanged (referential identity); otherwise returns a fresh list.
     */
    private inline fun mappedList(
        list: List<FlatValue>,
        f: (FlatValue) -> FlatValue,
    ): List<FlatValue> {
        var changed = false
        val out = ArrayList<FlatValue>(list.size)
        for (v in list) {
            val nv = f(v)
            if (nv !== v) changed = true
            out.add(nv)
        }
        return if (changed) out else list
    }
}

/* ------------------------------------------------------------------- */
/* Identity-preserving helpers used by the mappers below.              */
/*                                                                     */
/* Each `transform` helper applies [f] to its input(s), checks whether */
/* anything actually changed (by referential identity, `===`), and     */
/* invokes [build] only when at least one input changed. Otherwise     */
/* returns the original [original]. This lets every `visitXxx` body    */
/* preserve the input instruction's identity when [f] is an identity   */
/* on every operand/target, so callers can detect "no rewrite" via     */
/* `result === original`.                                              */
/* ------------------------------------------------------------------- */

private inline fun transform(
    original: FlatInst,
    x: FlatValue,
    f: (FlatValue) -> FlatValue,
    build: (FlatValue) -> FlatInst,
): FlatInst {
    val nx = f(x)
    return if (nx === x) original else build(nx)
}

private inline fun transform(
    original: FlatInst,
    a: FlatValue,
    b: FlatValue,
    f: (FlatValue) -> FlatValue,
    build: (FlatValue, FlatValue) -> FlatInst,
): FlatInst {
    val na = f(a); val nb = f(b)
    return if (na === a && nb === b) original else build(na, nb)
}

private inline fun transform(
    original: FlatInst,
    a: FlatValue,
    b: FlatValue,
    c: FlatValue,
    f: (FlatValue) -> FlatValue,
    build: (FlatValue, FlatValue, FlatValue) -> FlatInst,
): FlatInst {
    val na = f(a); val nb = f(b); val nc = f(c)
    return if (na === a && nb === b && nc === c) original else build(na, nb, nc)
}

private inline fun transformNullable(
    original: FlatInst,
    x: FlatValue?,
    f: (FlatValue) -> FlatValue,
    build: (FlatValue?) -> FlatInst,
): FlatInst {
    val nx = x?.let { f(it) }
    return if (nx === x) original else build(nx)
}

private inline fun transformNullable(
    original: FlatInst,
    a: FlatValue?,
    b: FlatValue?,
    f: (FlatValue) -> FlatValue,
    build: (FlatValue?, FlatValue?) -> FlatInst,
): FlatInst {
    val na = a?.let { f(it) }
    val nb = b?.let { f(it) }
    return if (na === a && nb === b) original else build(na, nb)
}

private inline fun transformNullable(
    original: FlatInst,
    a: FlatValue?,
    b: FlatValue?,
    c: FlatValue?,
    f: (FlatValue) -> FlatValue,
    build: (FlatValue?, FlatValue?, FlatValue?) -> FlatInst,
): FlatInst {
    val na = a?.let { f(it) }
    val nb = b?.let { f(it) }
    val nc = c?.let { f(it) }
    return if (na === a && nb === b && nc === c) original else build(na, nb, nc)
}

private inline fun transformList(
    original: FlatInst,
    list: List<FlatValue>,
    f: (FlatValue) -> FlatValue,
    build: (List<FlatValue>) -> FlatInst,
): FlatInst {
    var changed = false
    val out = ArrayList<FlatValue>(list.size)
    for (v in list) {
        val nv = f(v)
        if (nv !== v) changed = true
        out.add(nv)
    }

    return if (changed) build(out) else original
}
