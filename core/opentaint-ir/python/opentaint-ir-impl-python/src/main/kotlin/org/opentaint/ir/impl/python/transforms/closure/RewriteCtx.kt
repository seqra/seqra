package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatAnyType
import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatBuildDict
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatCallArg
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatDeleteAttr
import org.opentaint.ir.impl.python.flat.FlatDeleteLocal
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatInst
import org.opentaint.ir.impl.python.flat.FlatLoadAttr
import org.opentaint.ir.impl.python.flat.FlatLoadSubscript
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatParamKind
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatStoreAttr
import org.opentaint.ir.impl.python.flat.FlatStrConst
import org.opentaint.ir.impl.python.flat.FlatUnpack
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.flat.mapOperand
import org.opentaint.ir.impl.python.flat.target
import org.opentaint.ir.impl.python.flat.withTarget

/**
 * Result of rewriting one capturing-or-cell-owning function: the (possibly
 * renamed) impl plus an optional adapter class. Non-capturing functions
 * with cellVars only return `impl` with `adapterClass = null`.
 */
internal data class RewriteOutput(
    val impl: FlatFunctionIR,
    val adapterClass: FlatClass? = null,
)

/**
 * Thrown when the closure transform encounters a documented but not-yet-
 * supported shape. Today the only producer is the METHOD pass-through case:
 * a closure-root method that binds a capturing child whose closure vars
 * the method doesn't own. The rewriter catches this exception, emits a
 * diagnostic, and leaves the function unchanged.
 *
 * Distinct from generic [IllegalStateException] / [IllegalArgumentException]
 * (which signal real invariant violations and propagate).
 */
internal class ClosureRewriteLimitation(message: String) : RuntimeException(message)

/**
 * Per-function rewrite state. Owns the cell map, fresh-temp allocator, and
 * the prologue / body rewrite primitives. One instance per function.
 */
internal class RewriteCtx(
    private val fn: FlatFunctionIR,
    private val ci: ClosureInfo,
    private val info: Map<String, ClosureInfo>,
    private val capturingPlan: Map<String, CapturingEntry>,
) {
    // Sorted for determinism.
    private val ownedCells: List<String> = ci.cellVars.toSortedSet().toList()
    private val receivedCells: List<String> = ci.closureVars.toSortedSet().toList()
    private val cellLocals: Map<String, FlatLocal> = run {
        val m = LinkedHashMap<String, FlatLocal>()
        for (n in ownedCells) m[n] = FlatLocal(cellLocalName(n))
        for (n in receivedCells) m[n] = FlatLocal(cellLocalName(n))
        m
    }
    private val originalParamNames: Set<String> = fn.parameters.map { it.name }.toSet()
    private var tempCounter: Int = 0
    private val envLocal: FlatLocal = FlatLocal(ENV_LOCAL_NAME)

    init {
        // Synthetic locals introduced by the prologue must not clash with
        // any name already in scope. Python identifier rules forbid `$`, so
        // user-written code cannot collide; a clash would mean an upstream
        // transform emitted a name in our reserved space.
        for (paramName in originalParamNames) {
            check(paramName != ENV_LOCAL_NAME) {
                "Closure rewrite reserved name '$ENV_LOCAL_NAME' collides with " +
                    "parameter of ${fn.qualifiedName}"
            }
            check(!paramName.startsWith(CELL_LOCAL_PREFIX)) {
                "Closure rewrite reserved prefix '$CELL_LOCAL_PREFIX' collides " +
                    "with parameter '$paramName' of ${fn.qualifiedName}"
            }
        }
    }

    fun run(): RewriteOutput {
        val newParameters = if (ci.closureVars.isNotEmpty()) {
            listOf(selfParameter()) + fn.parameters
        } else {
            fn.parameters
        }

        val prologue = buildPrologue()

        val newBlocks = fn.cfg.blocks.map { block ->
            val instructions = if (block.label == fn.cfg.entryBlock) {
                prologue + block.instructions.flatMap { rewriteInstruction(it) }
            } else {
                block.instructions.flatMap { rewriteInstruction(it) }
            }
            block.copy(instructions = instructions)
        }

        val newCfg = fn.cfg.copy(blocks = newBlocks)

        val capturingEntry = capturingPlan[fn.qualifiedName]
        val rebuiltImpl = if (capturingEntry != null) {
            fn.copy(
                name = capturingEntry.implRenamedName,
                qualifiedName = capturingEntry.implRenamedQn,
                parameters = newParameters,
                closureVars = ci.closureVars.toSortedSet().toSet(),
                cfg = newCfg,
            )
        } else {
            fn.copy(
                parameters = newParameters,
                closureVars = ci.closureVars.toSortedSet().toSet(),
                cfg = newCfg,
            )
        }

        val adapter = if (capturingEntry != null) {
            buildAdapterClass(capturingEntry, fn)
        } else {
            null
        }

        return RewriteOutput(impl = rebuiltImpl, adapterClass = adapter)
    }

    /* -------------------------------------------------------------- */
    /* Prologue                                                       */
    /* -------------------------------------------------------------- */

    private fun buildPrologue(): List<FlatInst> {
        val out = ArrayList<FlatInst>()
        // Own cells (alloc + seed-from-param if applicable).
        for (name in ownedCells) {
            val cellLocal = cellLocals.getValue(name)
            out.add(
                FlatCall(
                    target = cellLocal,
                    callee = FlatGlobalRef("builtins.${ClosureRuntime.CELL_CTOR_NAME}"),
                    args = emptyList(),
                ),
            )
            if (name in originalParamNames) {
                out.add(
                    FlatStoreAttr(
                        obj = cellLocal,
                        attribute = ClosureRuntime.CELL_VALUE_ATTR,
                        value = FlatLocal(name),
                    ),
                )
            }
        }
        // Received cells via env extraction.
        if (ci.closureVars.isNotEmpty()) {
            out.add(
                FlatLoadAttr(
                    target = envLocal,
                    obj = FlatLocal(ClosureRuntime.SELF_PARAM_NAME),
                    attribute = ClosureRuntime.CLOSURE_ATTR_NAME,
                ),
            )
            for (name in receivedCells) {
                val cellLocal = cellLocals.getValue(name)
                out.add(
                    FlatLoadSubscript(
                        target = cellLocal,
                        obj = envLocal,
                        index = FlatStrConst(name),
                    ),
                )
            }
        }
        return out
    }

    /* -------------------------------------------------------------- */
    /* Body rewrite                                                   */
    /* -------------------------------------------------------------- */

    private fun isCellManaged(name: String): Boolean = name in cellLocals

    /**
     * Allocate a fresh local with the closure-rewrite prefix `$tc$`. The
     * prefix is distinct from `protoToFlat`'s expression-lowering convention
     * (`$tN`), so collisions with existing temps in [fn] are impossible by
     * construction. Still starts with `$t` so existing tests asserting
     * `name.startsWith("$t")` continue to hold.
     */
    private fun freshTemp(): FlatLocal = FlatLocal("\$tc\$${tempCounter++}")

    /**
     * Substitute a cell-managed [FlatLocal] operand with a load into a fresh
     * temp; emit the load into [scope]. Non-cell-managed operands pass
     * through unchanged.
     */
    private fun loadOperand(value: FlatValue, line: Int, scope: InstRewriterScope): FlatValue {
        if (value !is FlatLocal) return value
        if (!isCellManaged(value.name)) return value
        val tmp = freshTemp()
        scope.emitBefore(
            FlatLoadAttr(
                target = tmp,
                obj = cellLocals.getValue(value.name),
                attribute = ClosureRuntime.CELL_VALUE_ATTR,
                line = line,
            ),
        )
        return tmp
    }

    /**
     * If [target] needs cell-redirection, allocate a fresh temp, emit the
     * post-store into [scope], and return the temp. Otherwise return `null`
     * — the caller must keep the original target.
     *
     * Cell-redirection applies iff [target] is a [FlatLocal] AND its name is
     * cell-managed in this function. Both conditions are necessary; targets
     * that are not [FlatLocal] are not name-bound to cells regardless of
     * what the cell map contains.
     */
    private fun redirectTarget(target: FlatValue, line: Int, scope: InstRewriterScope): FlatValue? {
        if (!needsCellRedirection(target)) return null
        target as FlatLocal
        val tmp = freshTemp()
        scope.emitAfter(
            FlatStoreAttr(
                obj = cellLocals.getValue(target.name),
                attribute = ClosureRuntime.CELL_VALUE_ATTR,
                value = tmp,
                line = line,
            ),
        )
        return tmp
    }

    /** A target needs cell-redirection iff it is a cell-managed [FlatLocal]. */
    private fun needsCellRedirection(target: FlatValue): Boolean =
        target is FlatLocal && isCellManaged(target.name)

    /**
     * Per-instruction rewrite. For most instructions this is just
     * `mapOperand(loadCell) + withTarget(redirectToCell)` ([defaultRewrite]);
     * three cases are genuinely shape-changing and handled explicitly:
     *
     *  - `FlatBindFunction` whose child captures: replaced by an adapter
     *    constructor call (with optional cell-store wrapping if the bind
     *    target is itself cell-managed).
     *  - `FlatDeleteLocal` of a cell-managed name: lowered to
     *    `FlatDeleteAttr($cell$name, "value")`.
     *  - `FlatUnpack`: multi-target — each cell-managed slot is
     *    redirected separately and followed by its own cell store.
     */
    private fun rewriteInstruction(inst: FlatInst): List<FlatInst> {
        return when (inst) {
            is FlatBindFunction -> rewriteBind(inst)
            is FlatDeleteLocal -> rewriteDeleteLocal(inst)
            is FlatUnpack -> rewriteUnpack(inst)
            else -> defaultRewrite(inst)
        }
    }

    /**
     * Generic rewrite: load every operand from its cell (pre), redirect a
     * cell-managed target to a fresh temp and store it back (post).
     */
    private fun defaultRewrite(inst: FlatInst): List<FlatInst> {
        val scope = InstRewriterScope()
        val withOps = inst.mapOperand { v -> loadOperand(v, inst.line, scope) }
        val rewritten = inst.target?.let { redirectTarget(it, inst.line, scope) }
            ?.let(withOps::withTarget)
            ?: withOps
        return scope.finish(rewritten)
    }

    /** [FlatDeleteLocal] of a cell-managed name lowers to `del cell.value`. */
    private fun rewriteDeleteLocal(inst: FlatDeleteLocal): List<FlatInst> {
        val l = inst.local
        if (l is FlatLocal && isCellManaged(l.name)) {
            return listOf(
                FlatDeleteAttr(
                    obj = cellLocals.getValue(l.name),
                    attribute = ClosureRuntime.CELL_VALUE_ATTR,
                    line = inst.line,
                ),
            )
        }
        return listOf(inst)
    }

    /** [FlatUnpack] has multiple targets; redirect each cell-managed slot. */
    private fun rewriteUnpack(inst: FlatUnpack): List<FlatInst> {
        val scope = InstRewriterScope()
        val source = loadOperand(inst.source, inst.line, scope)
        val newTargets = inst.targets.map { redirectTarget(it, inst.line, scope) ?: it }
        return scope.finish(inst.copy(targets = newTargets, source = source))
    }

    /**
     * [FlatBindFunction]: capturing child → replace with adapter-class
     * constructor call. Non-capturing → keep the bind and treat target
     * cell-management as a normal case.
     */
    private fun rewriteBind(inst: FlatBindFunction): List<FlatInst> {
        val line = inst.line
        // The bind target's FlatGlobalRef.qualifiedName IS the child's
        // FlatFunctionIR.qualifiedName — no name→qn bridge needed.
        val childQn = inst.function.qualifiedName
        val childInfo = info[childQn]
        val childClosureVars = childInfo?.closureVars.orEmpty()

        if (childClosureVars.isEmpty()) {
            // Non-capturing child: keep FlatBindFunction; only handle cell-managed target.
            return defaultRewrite(inst)
        }

        // Capturing child: replace bind with adapter-class constructor call.
        val childEntry = capturingPlan[childQn]
            ?: error(
                "Closure rewrite: child '$childQn' captures " +
                    "but has no capturing-plan entry",
            )
        val originalTarget = inst.target

        // Build env on parent's cells.
        val scope = InstRewriterScope()
        val (envBuildInst, envValueLocal) = buildEnvDict(childClosureVars, line)
        scope.emitBefore(envBuildInst)

        if (originalTarget is FlatLocal && isCellManaged(originalTarget.name)) {
            // Cell-managed bind target: redirect first (post-store goes
            // through the scope), then emit the constructor into the temp.
            val tmp = redirectTarget(originalTarget, line, scope)!!
            return scope.finish(
                FlatCall(
                    target = tmp,
                    callee = FlatGlobalRef(childEntry.adapterClassQn),
                    args = listOf(FlatCallArg(envValueLocal)),
                    line = line,
                ),
            )
        }

        return scope.finish(
            FlatCall(
                target = originalTarget,
                callee = FlatGlobalRef(childEntry.adapterClassQn),
                args = listOf(FlatCallArg(envValueLocal)),
                line = line,
            ),
        )
    }

    /**
     * Build the env dict on parent's cells. Returns the build-dict
     * instruction and a freshly-allocated env local that the constructor
     * call will receive.
     *
     * Throws [ClosureRewriteLimitation] if the parent doesn't own a cell
     * for some captured name. This is the documented METHOD-as-closure-root
     * pass-through limitation (a method binding a capturing child cannot
     * forward cells from a grand-parent it doesn't see).
     */
    private fun buildEnvDict(childClosureVars: Set<String>, line: Int): Pair<FlatInst, FlatLocal> {
        val sortedNames = childClosureVars.toSortedSet().toList()
        val keys: List<FlatValue> = sortedNames.map { FlatStrConst(it) }
        val values: List<FlatValue> = sortedNames.map { name ->
            cellLocals[name]
                ?: throw ClosureRewriteLimitation(
                    "Closure rewrite: child captures '$name' but parent " +
                        "${fn.qualifiedName} has no cell for it (cells: ${cellLocals.keys})",
                )
        }
        val envTarget = freshTemp()
        val envInst = FlatBuildDict(
            target = envTarget,
            keys = keys,
            values = values,
            line = line,
        )
        return envInst to envTarget
    }

    private companion object {
        private const val ENV_LOCAL_NAME = "\$env"
        private const val CELL_LOCAL_PREFIX = "\$cell\$"

        private fun cellLocalName(name: String): String = "$CELL_LOCAL_PREFIX$name"

        private fun selfParameter(): FlatParameter = FlatParameter(
            name = ClosureRuntime.SELF_PARAM_NAME,
            type = FlatAnyType,
            kind = FlatParamKind.POSITIONAL_OR_KEYWORD,
            hasDefault = false,
            defaultValue = null,
        )
    }
}
