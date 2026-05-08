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
import org.opentaint.ir.impl.python.flat.FlatParameterRef
import org.opentaint.ir.impl.python.flat.FlatStoreAttr
import org.opentaint.ir.impl.python.flat.FlatStrConst
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.flat.mapOperand
import org.opentaint.ir.impl.python.flat.mapTarget

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
 * supported shape. The producer is the closure-root pass-through case: a
 * parentless function (closure root) binds a capturing child whose closure
 * vars the parent doesn't own. [ClosureAnalyzer] emits a leak warning when
 * it detects this; the rewriter catches the exception, emits a follow-up
 * diagnostic on the bind-site walk, and leaves the parent function
 * unchanged.
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
    private val moduleName: String,
) {
    // [ClosureAnalyzer] hands us deterministic-iteration sets, so we don't
    // re-sort here. Both sets are kept on the receiver type `Set<String>`
    // for membership checks; iteration order is preserved.
    private val ownedCells: Set<String> = ci.cellVars
    private val receivedCells: Set<String> = ci.closureVars
    private val cellLocals: Map<String, FlatLocal> = buildMap {
        for (n in ownedCells) this[n] = FlatLocal(cellLocalName(n))
        for (n in receivedCells) this[n] = FlatLocal(cellLocalName(n))
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
        val isCapturing = ci.closureVars.isNotEmpty()
        // `<self>` is the synthetic env parameter prepended to capturing
        // children's signatures here. Unlike user parameters, it is NOT
        // bound to a same-named `FlatLocal("<self>")` by the function-entry
        // parameter-binding prologue (CfgBuild ran before this rewrite, on
        // the pre-prepend signature). Consumers must therefore read `<self>`
        // exclusively as `FlatParameterRef("<self>")` — see [buildPrologue]'s
        // env-load. No `FlatLocal("<self>")` exists at any point.
        val newParameters = if (isCapturing) {
            listOf(selfParameter()) + fn.parameters
        } else {
            fn.parameters
        }

        val prologue = buildPrologue()

        val newBlocks = fn.cfg.blocks.map { block ->
            val rewrittenBody = block.instructions.flatMap { rewriteInstruction(it) }
            val instructions = if (block.label == fn.cfg.entryBlock) {
                prologue + rewrittenBody
            } else {
                rewrittenBody
            }
            block.copy(instructions = instructions)
        }

        val newCfg = fn.cfg.copy(blocks = newBlocks)

        val rebuiltImpl = if (isCapturing) {
            val implQn = ClosureRuntime.implFunctionQn(moduleName, fn.name)
            fn.copy(
                name = implQn.substringAfterLast('.'),
                qualifiedName = implQn,
                parameters = newParameters,
                closureVars = ci.closureVars,
                cfg = newCfg,
            )
        } else {
            fn.copy(
                parameters = newParameters,
                closureVars = ci.closureVars,
                cfg = newCfg,
            )
        }

        val adapter = if (isCapturing) buildAdapterClass(fn, moduleName) else null

        return RewriteOutput(impl = rebuiltImpl, adapterClass = adapter)
    }

    /* -------------------------------------------------------------- */
    /* Prologue                                                       */
    /* -------------------------------------------------------------- */

    private fun buildPrologue(): List<FlatInst> = buildList {
        // Own cells: only the allocation. Seeding from a parameter falls out
        // of the body rewrite for free — when the captured name is also a
        // parameter, the function-entry parameter-binding prologue emits
        // `FlatAssign(FlatLocal(p), FlatParameterRef(p))`, and `defaultRewrite`
        // redirects the cell-managed `FlatLocal(p)` target into a fresh temp
        // plus a `FlatStoreAttr($cell$p, "value", $tempN)`. Adding an explicit
        // seed here would duplicate that store.
        for (name in ownedCells) {
            val cellLocal = cellLocals.getValue(name)
            add(
                FlatCall(
                    target = cellLocal,
                    callee = FlatGlobalRef("builtins.${ClosureRuntime.CELL_CTOR_NAME}"),
                    args = emptyList(),
                ),
            )
        }
        // Received cells via env extraction. Only emit when there are any —
        // [receivedCells] mirrors `ci.closureVars`, so emptiness of one
        // implies emptiness of the other.
        if (receivedCells.isNotEmpty()) {
            add(
                FlatLoadAttr(
                    target = envLocal,
                    obj = FlatParameterRef(ClosureRuntime.SELF_PARAM_NAME),
                    attribute = ClosureRuntime.CLOSURE_ATTR_NAME,
                ),
            )
            for (name in receivedCells) {
                add(
                    FlatLoadSubscript(
                        target = cellLocals.getValue(name),
                        obj = envLocal,
                        index = FlatStrConst(name),
                    ),
                )
            }
        }
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
     * temp; emit the load into [scope]. Returns the input unchanged (same
     * reference) when no substitution is needed; the caller can detect
     * "rewrite happened" via referential identity (`!==`).
     */
    private fun loadOperand(value: FlatValue, line: Int, scope: InstRewriterScope): FlatValue {
        if (value !is FlatLocal || !isCellManaged(value.name)) return value
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
     * If [target] is a cell-managed [FlatLocal], allocate a fresh temp,
     * emit the post-store into [scope], and return the temp. Otherwise
     * return [target] unchanged. The caller can detect "rewrite happened"
     * via referential identity (`!==`).
     */
    private fun redirectTarget(target: FlatValue, line: Int, scope: InstRewriterScope): FlatValue {
        if (target !is FlatLocal || !isCellManaged(target.name)) return target
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

    /**
     * Per-instruction rewrite. The dispatcher creates a fresh
     * [InstRewriterScope] and each handler stages its core, pre, and post
     * instructions on the scope. Two cases are genuinely shape-changing:
     *
     *  - `FlatBindFunction` whose child captures: replaced by an adapter
     *    constructor call (with optional cell-store wrapping if the bind
     *    target is itself cell-managed).
     *  - `FlatDeleteLocal` of a cell-managed name: lowered to
     *    `FlatDeleteAttr($cell$name, "value")`.
     *
     * Everything else flows through [defaultRewrite]: load every operand
     * from its cell (pre), redirect every target slot — single or
     * [FlatUnpack]'s multi-slot — to fresh temps and store them back
     * (post). [mapOperand] / [mapTarget] are identity-preserving, so when
     * nothing needs cell-handling the scope's core stays as the original
     * instruction reference.
     */
    private fun rewriteInstruction(inst: FlatInst): List<FlatInst> {
        val scope = InstRewriterScope(inst)
        when (inst) {
            is FlatBindFunction -> rewriteBind(inst, scope)
            is FlatDeleteLocal -> rewriteDeleteLocal(inst, scope)
            else -> defaultRewrite(inst, scope)
        }
        return scope.finish()
    }

    /**
     * Generic rewrite: load every operand from its cell (pre), redirect
     * every cell-managed target slot to a fresh temp and store it back
     * (post). [FlatUnpack]'s multi-target shape is handled by [mapTarget].
     */
    private fun defaultRewrite(inst: FlatInst, scope: InstRewriterScope) {
        val rewritten = inst
            .mapOperand { v -> loadOperand(v, inst.line, scope) }
            .mapTarget { t -> redirectTarget(t, inst.line, scope) }
        scope.replaceWith(rewritten)
    }

    /** [FlatDeleteLocal] of a cell-managed name lowers to `del cell.value`. */
    private fun rewriteDeleteLocal(inst: FlatDeleteLocal, scope: InstRewriterScope) {
        val l = inst.local as? FlatLocal ?: return
        if (!isCellManaged(l.name)) return

        scope.replaceWith(
            FlatDeleteAttr(
                obj = cellLocals.getValue(l.name),
                attribute = ClosureRuntime.CELL_VALUE_ATTR,
                line = inst.line,
            ),
        )
    }

    /**
     * [FlatBindFunction]: capturing child → replace with adapter-class
     * constructor call. Non-capturing → keep the bind and treat target
     * cell-management as a normal case.
     */
    private fun rewriteBind(inst: FlatBindFunction, scope: InstRewriterScope) {
        val line = inst.line
        // The bind target's FlatGlobalRef.qualifiedName IS the child's
        // FlatFunctionIR.qualifiedName — no name→qn bridge needed.
        val childQn = inst.function.qualifiedName
        val childClosureVars = info[childQn]?.closureVars.orEmpty()

        if (childClosureVars.isEmpty()) {
            // Non-capturing child: keep FlatBindFunction; only handle cell-managed target.
            defaultRewrite(inst, scope)
            return
        }

        // Capturing child: replace bind with adapter-class constructor call.
        // The child's bare name is the suffix of its qualified name and is
        // module-unique by construction (set by `freshNestedName` /
        // `freshLambdaName` during proto→Flat lifting).
        val childAdapterQn = ClosureRuntime.adapterClassQn(
            moduleName = moduleName,
            fnName = childQn.substringAfterLast('.'),
        )
        val originalTarget = inst.target

        // Build env on parent's cells.
        val (envBuildInst, envValueLocal) = buildEnvDict(childClosureVars, line)
        scope.emitBefore(envBuildInst)

        // Cell-managed bind target: redirect first (post-store goes through
        // the scope), then emit the constructor into the temp. For a
        // non-cell-managed target [redirectTarget] returns the input
        // unchanged.
        val callTarget = redirectTarget(originalTarget, line, scope)

        scope.replaceWith(
            FlatCall(
                target = callTarget,
                callee = FlatGlobalRef(childAdapterQn),
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
        // Iteration of [childClosureVars] is already deterministic (analyzer
        // hands us sorted-iterating sets), so no extra sort needed.
        val keys: List<FlatValue> = childClosureVars.map { FlatStrConst(it) }
        // `cellLocals[name]` resolves against THIS function's cell map.
        // The same captured user-name (e.g. `x`) maps to a `$cell$x` local
        // in every function that owns or receives it; that's correct because
        // each `$cell$x` is a function-scoped local. The env dict's job is
        // to bridge: keys are user-name strings, values are the parent's
        // local cell — read in the child's prologue into the child's own
        // local cell of the same name.
        val values: List<FlatValue> = childClosureVars.map { name ->
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
