package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
import org.opentaint.ir.impl.python.flat.FlatAnyType
import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatAwait
import org.opentaint.ir.impl.python.flat.FlatBinOp
import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatBlock
import org.opentaint.ir.impl.python.flat.FlatBranch
import org.opentaint.ir.impl.python.flat.FlatBuildDict
import org.opentaint.ir.impl.python.flat.FlatBuildList
import org.opentaint.ir.impl.python.flat.FlatBuildSet
import org.opentaint.ir.impl.python.flat.FlatBuildSlice
import org.opentaint.ir.impl.python.flat.FlatBuildString
import org.opentaint.ir.impl.python.flat.FlatBuildTuple
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatCompare
import org.opentaint.ir.impl.python.flat.FlatDeleteAttr
import org.opentaint.ir.impl.python.flat.FlatDeleteGlobal
import org.opentaint.ir.impl.python.flat.FlatDeleteLocal
import org.opentaint.ir.impl.python.flat.FlatDeleteSubscript
import org.opentaint.ir.impl.python.flat.FlatExceptHandler
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatGetIter
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatGoto
import org.opentaint.ir.impl.python.flat.FlatInst
import org.opentaint.ir.impl.python.flat.FlatLoadAttr
import org.opentaint.ir.impl.python.flat.FlatLoadGlobal
import org.opentaint.ir.impl.python.flat.FlatLoadSubscript
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.flat.FlatNextIter
import org.opentaint.ir.impl.python.flat.FlatParamKind
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatRaise
import org.opentaint.ir.impl.python.flat.FlatReturn
import org.opentaint.ir.impl.python.flat.FlatStoreAttr
import org.opentaint.ir.impl.python.flat.FlatStoreGlobal
import org.opentaint.ir.impl.python.flat.FlatStoreSubscript
import org.opentaint.ir.impl.python.flat.FlatStrConst
import org.opentaint.ir.impl.python.flat.FlatTypeCheck
import org.opentaint.ir.impl.python.flat.FlatUnaryOp
import org.opentaint.ir.impl.python.flat.FlatUnpack
import org.opentaint.ir.impl.python.flat.FlatUnreachable
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.flat.FlatYield
import org.opentaint.ir.impl.python.flat.FlatYieldFrom

/**
 * Rewrites a [FlatModuleIR] using per-function [ClosureInfo] from
 * [ClosureAnalyzer]. Pure: input module is not modified.
 *
 * For every function with non-empty `cellVars ∪ closureVars`:
 *   - prepends `<self>` to `parameters` if `closureVars` is non-empty;
 *   - prepends a prologue allocating own cells (`__pir_cell__()`),
 *     seeding parameter cells, and extracting received cells from
 *     `<self>._closure_env_`;
 *   - rewires reads of cell-managed locals through `FlatLoadAttr` and
 *     writes through `FlatStoreAttr` against `$cell$name`;
 *   - at each `FlatBindFunction` whose child has non-empty `closureVars`,
 *     emits `FlatBuildDict($env, …)` + `FlatStoreAttr(target, "_closure_env_", $env)`.
 *
 * `FlatFunctionIR.closureVars` is populated from [ClosureInfo.closureVars]
 * so the PIR converter sees it.
 */
internal object ClosureRewriter {

    fun rewrite(module: FlatModuleIR, info: Map<String, ClosureInfo>): FlatModuleIR {
        val diagnostics = ArrayList<PIRDiagnostic>()

        // FlatBindFunction.function is a FlatGlobalRef carrying the synthetic
        // *unique* name (e.g. "reader$local1"), while ClosureInfo is keyed by
        // qualifiedName (e.g. "module.outer.reader"). Build an index so bind
        // sites can resolve their child's ClosureInfo.
        val nameToQualified = buildNameToQualifiedIndex(module)

        val newFunctions = module.functions.map { rewriteFunction(it, info, nameToQualified, diagnostics) }
        val newModuleInit = rewriteFunction(module.moduleInit, info, nameToQualified, diagnostics)
        val newClasses = module.classes.map { rewriteClass(it, info, nameToQualified, diagnostics) }

        return module.copy(
            functions = newFunctions,
            moduleInit = newModuleInit,
            classes = newClasses,
            diagnostics = module.diagnostics + diagnostics,
        )
    }

    /**
     * Build a `synthetic-unique-name -> qualifiedName` map covering every
     * function-like that may appear as a `FlatBindFunction.function` target.
     * In practice this is `module.functions` (top-level + lifted nested defs +
     * lifted lambdas). Methods and module init are not bind targets.
     */
    private fun buildNameToQualifiedIndex(module: FlatModuleIR): Map<String, String> {
        val index = HashMap<String, String>()
        for (fn in module.functions) index[fn.name] = fn.qualifiedName
        return index
    }

    private fun rewriteClass(
        cls: FlatClass,
        info: Map<String, ClosureInfo>,
        nameToQualified: Map<String, String>,
        diagnostics: MutableList<PIRDiagnostic>,
    ): FlatClass = cls.copy(
        methods = cls.methods.map { rewriteFunction(it, info, nameToQualified, diagnostics) },
        nestedClasses = cls.nestedClasses.map { rewriteClass(it, info, nameToQualified, diagnostics) },
    )

    /**
     * Per-function rewrite. Returns the original function if there's nothing to do
     * (no own cells, no received cells, no capturing children to annotate).
     * On any thrown exception, appends a diagnostic and returns the original.
     */
    private fun rewriteFunction(
        fn: FlatFunctionIR,
        info: Map<String, ClosureInfo>,
        nameToQualified: Map<String, String>,
        diagnostics: MutableList<PIRDiagnostic>,
    ): FlatFunctionIR {
        val ci = info[fn.qualifiedName] ?: return fn

        val hasCapturingChildAtBindSite = functionHasCapturingChildBind(fn, info, nameToQualified)
        if (ci.cellVars.isEmpty() && ci.closureVars.isEmpty() && !hasCapturingChildAtBindSite) {
            return fn
        }

        return try {
            val ctx = RewriteCtx(fn, ci, info, nameToQualified)
            ctx.run()
        } catch (e: Exception) {
            diagnostics.add(
                PIRDiagnostic(
                    severity = PIRDiagnosticSeverity.ERROR,
                    message = "Closure rewrite failed for ${fn.qualifiedName}: ${e.message}",
                    functionName = fn.qualifiedName,
                    exceptionType = e::class.simpleName ?: "Exception",
                ),
            )
            fn
        }
    }

    private fun functionHasCapturingChildBind(
        fn: FlatFunctionIR,
        info: Map<String, ClosureInfo>,
        nameToQualified: Map<String, String>,
    ): Boolean {
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                if (inst is FlatBindFunction) {
                    val childQn = nameToQualified[inst.function.name]
                    val childInfo = childQn?.let { info[it] }
                    if (childInfo != null && childInfo.closureVars.isNotEmpty()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /* ------------------------------------------------------------------ */
    /* Per-function rewrite context                                       */
    /* ------------------------------------------------------------------ */

    private class RewriteCtx(
        private val fn: FlatFunctionIR,
        private val ci: ClosureInfo,
        private val info: Map<String, ClosureInfo>,
        private val nameToQualified: Map<String, String>,
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

        fun run(): FlatFunctionIR {
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

            return fn.copy(
                parameters = newParameters,
                closureVars = ci.closureVars.toSortedSet().toSet(),
                cfg = newCfg,
            )
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
                        callee = FlatGlobalRef(ClosureRuntime.CELL_CTOR_NAME, "builtins"),
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

        private fun freshTemp(): FlatLocal = FlatLocal("\$t${tempCounter++}")

        /**
         * Produce loads for any cell-managed `FlatLocal` operand, returning the
         * substituted operand. Emits `FlatLoadAttr` instructions into [pre].
         */
        private fun rewriteOperand(value: FlatValue, line: Int, pre: MutableList<FlatInst>): FlatValue {
            if (value !is FlatLocal) return value
            if (!isCellManaged(value.name)) return value
            val tmp = freshTemp()
            pre.add(
                FlatLoadAttr(
                    target = tmp,
                    obj = cellLocals.getValue(value.name),
                    attribute = ClosureRuntime.CELL_VALUE_ATTR,
                    line = line,
                ),
            )
            return tmp
        }

        private fun rewriteOperandNullable(
            value: FlatValue?,
            line: Int,
            pre: MutableList<FlatInst>,
        ): FlatValue? = if (value == null) null else rewriteOperand(value, line, pre)

        /**
         * Allocate a fresh temp to substitute for a cell-managed target. Returns
         * `null` if the target is not cell-managed (caller should keep the original).
         */
        private fun redirectTarget(target: FlatValue): Pair<FlatValue, String>? {
            if (target !is FlatLocal) return null
            if (!isCellManaged(target.name)) return null
            val tmp = freshTemp()
            return tmp to target.name
        }

        /**
         * Emit a `FlatStoreAttr` finalising a cell write.
         */
        private fun storeCell(name: String, value: FlatValue, line: Int): FlatInst =
            FlatStoreAttr(
                obj = cellLocals.getValue(name),
                attribute = ClosureRuntime.CELL_VALUE_ATTR,
                value = value,
                line = line,
            )

        private fun rewriteInstruction(inst: FlatInst): List<FlatInst> {
            val pre = ArrayList<FlatInst>()
            val post = ArrayList<FlatInst>()
            val line = inst.line

            val rewritten: FlatInst? = when (inst) {
                is FlatAssign -> {
                    val src = rewriteOperand(inst.source, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, source = src)
                    } else {
                        inst.copy(source = src)
                    }
                }
                is FlatBinOp -> {
                    val l = rewriteOperand(inst.left, line, pre)
                    val r = rewriteOperand(inst.right, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, left = l, right = r)
                    } else {
                        inst.copy(left = l, right = r)
                    }
                }
                is FlatUnaryOp -> {
                    val op = rewriteOperand(inst.operand, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, operand = op)
                    } else {
                        inst.copy(operand = op)
                    }
                }
                is FlatCompare -> {
                    val l = rewriteOperand(inst.left, line, pre)
                    val r = rewriteOperand(inst.right, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, left = l, right = r)
                    } else {
                        inst.copy(left = l, right = r)
                    }
                }
                is FlatLoadAttr -> {
                    val obj = rewriteOperand(inst.obj, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, obj = obj)
                    } else {
                        inst.copy(obj = obj)
                    }
                }
                is FlatStoreAttr -> {
                    val obj = rewriteOperand(inst.obj, line, pre)
                    val value = rewriteOperand(inst.value, line, pre)
                    inst.copy(obj = obj, value = value)
                }
                is FlatLoadSubscript -> {
                    val obj = rewriteOperand(inst.obj, line, pre)
                    val index = rewriteOperand(inst.index, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, obj = obj, index = index)
                    } else {
                        inst.copy(obj = obj, index = index)
                    }
                }
                is FlatStoreSubscript -> {
                    val obj = rewriteOperand(inst.obj, line, pre)
                    val index = rewriteOperand(inst.index, line, pre)
                    val value = rewriteOperand(inst.value, line, pre)
                    inst.copy(obj = obj, index = index, value = value)
                }
                is FlatLoadGlobal -> {
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp)
                    } else {
                        inst
                    }
                }
                is FlatStoreGlobal -> {
                    val value = rewriteOperand(inst.value, line, pre)
                    inst.copy(value = value)
                }
                is FlatCall -> {
                    val callee = rewriteOperand(inst.callee, line, pre)
                    val args = inst.args.map { it.copy(value = rewriteOperand(it.value, line, pre)) }
                    if (inst.target != null) {
                        val redir = redirectTarget(inst.target)
                        if (redir != null) {
                            val (tmp, name) = redir
                            post.add(storeCell(name, tmp, line))
                            inst.copy(target = tmp, callee = callee, args = args)
                        } else {
                            inst.copy(callee = callee, args = args)
                        }
                    } else {
                        inst.copy(callee = callee, args = args)
                    }
                }
                is FlatBuildList -> {
                    val elements = inst.elements.map { rewriteOperand(it, line, pre) }
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, elements = elements)
                    } else {
                        inst.copy(elements = elements)
                    }
                }
                is FlatBuildTuple -> {
                    val elements = inst.elements.map { rewriteOperand(it, line, pre) }
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, elements = elements)
                    } else {
                        inst.copy(elements = elements)
                    }
                }
                is FlatBuildSet -> {
                    val elements = inst.elements.map { rewriteOperand(it, line, pre) }
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, elements = elements)
                    } else {
                        inst.copy(elements = elements)
                    }
                }
                is FlatBuildDict -> {
                    val keys = inst.keys.map { rewriteOperand(it, line, pre) }
                    val values = inst.values.map { rewriteOperand(it, line, pre) }
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, keys = keys, values = values)
                    } else {
                        inst.copy(keys = keys, values = values)
                    }
                }
                is FlatBuildSlice -> {
                    val lower = rewriteOperandNullable(inst.lower, line, pre)
                    val upper = rewriteOperandNullable(inst.upper, line, pre)
                    val step = rewriteOperandNullable(inst.step, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, lower = lower, upper = upper, step = step)
                    } else {
                        inst.copy(lower = lower, upper = upper, step = step)
                    }
                }
                is FlatBuildString -> {
                    val parts = inst.parts.map { rewriteOperand(it, line, pre) }
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, parts = parts)
                    } else {
                        inst.copy(parts = parts)
                    }
                }
                is FlatGetIter -> {
                    val iterable = rewriteOperand(inst.iterable, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, iterable = iterable)
                    } else {
                        inst.copy(iterable = iterable)
                    }
                }
                is FlatNextIter -> {
                    val iterator = rewriteOperand(inst.iterator, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, iterator = iterator)
                    } else {
                        inst.copy(iterator = iterator)
                    }
                }
                is FlatTypeCheck -> {
                    val value = rewriteOperand(inst.value, line, pre)
                    val redir = redirectTarget(inst.target)
                    if (redir != null) {
                        val (tmp, name) = redir
                        post.add(storeCell(name, tmp, line))
                        inst.copy(target = tmp, value = value)
                    } else {
                        inst.copy(value = value)
                    }
                }
                is FlatYield -> {
                    val value = rewriteOperandNullable(inst.value, line, pre)
                    if (inst.target != null) {
                        val redir = redirectTarget(inst.target)
                        if (redir != null) {
                            val (tmp, name) = redir
                            post.add(storeCell(name, tmp, line))
                            inst.copy(target = tmp, value = value)
                        } else {
                            inst.copy(value = value)
                        }
                    } else {
                        inst.copy(value = value)
                    }
                }
                is FlatYieldFrom -> {
                    val iterable = rewriteOperand(inst.iterable, line, pre)
                    if (inst.target != null) {
                        val redir = redirectTarget(inst.target)
                        if (redir != null) {
                            val (tmp, name) = redir
                            post.add(storeCell(name, tmp, line))
                            inst.copy(target = tmp, iterable = iterable)
                        } else {
                            inst.copy(iterable = iterable)
                        }
                    } else {
                        inst.copy(iterable = iterable)
                    }
                }
                is FlatAwait -> {
                    val awaitable = rewriteOperand(inst.awaitable, line, pre)
                    if (inst.target != null) {
                        val redir = redirectTarget(inst.target)
                        if (redir != null) {
                            val (tmp, name) = redir
                            post.add(storeCell(name, tmp, line))
                            inst.copy(target = tmp, awaitable = awaitable)
                        } else {
                            inst.copy(awaitable = awaitable)
                        }
                    } else {
                        inst.copy(awaitable = awaitable)
                    }
                }
                is FlatUnpack -> {
                    val source = rewriteOperand(inst.source, line, pre)
                    val newTargets = ArrayList<FlatValue>(inst.targets.size)
                    val storesAfter = ArrayList<FlatInst>()
                    for (target in inst.targets) {
                        val redir = redirectTarget(target)
                        if (redir != null) {
                            val (tmp, name) = redir
                            newTargets.add(tmp)
                            storesAfter.add(storeCell(name, tmp, line))
                        } else {
                            newTargets.add(target)
                        }
                    }
                    post.addAll(storesAfter)
                    inst.copy(targets = newTargets, source = source)
                }
                is FlatExceptHandler -> {
                    val target = inst.target
                    if (target != null) {
                        val redir = redirectTarget(target)
                        if (redir != null) {
                            val (tmp, name) = redir
                            post.add(storeCell(name, tmp, line))
                            inst.copy(target = tmp)
                        } else {
                            inst
                        }
                    } else {
                        inst
                    }
                }
                is FlatBranch -> {
                    val condition = rewriteOperand(inst.condition, line, pre)
                    inst.copy(condition = condition)
                }
                is FlatReturn -> {
                    val value = rewriteOperandNullable(inst.value, line, pre)
                    inst.copy(value = value)
                }
                is FlatRaise -> {
                    val exception = rewriteOperandNullable(inst.exception, line, pre)
                    val cause = rewriteOperandNullable(inst.cause, line, pre)
                    inst.copy(exception = exception, cause = cause)
                }
                is FlatDeleteLocal -> {
                    val l = inst.local
                    if (l is FlatLocal && isCellManaged(l.name)) {
                        // Lower to cell delete.
                        FlatDeleteAttr(
                            obj = cellLocals.getValue(l.name),
                            attribute = ClosureRuntime.CELL_VALUE_ATTR,
                            line = line,
                        )
                    } else {
                        inst
                    }
                }
                is FlatDeleteAttr -> {
                    val obj = rewriteOperand(inst.obj, line, pre)
                    inst.copy(obj = obj)
                }
                is FlatDeleteSubscript -> {
                    val obj = rewriteOperand(inst.obj, line, pre)
                    val index = rewriteOperand(inst.index, line, pre)
                    inst.copy(obj = obj, index = index)
                }
                is FlatBindFunction -> {
                    // Bind site: keep the bind, then if the child captures, attach env
                    // BEFORE redirecting through a cell (so the env attach hits the
                    // function value, not a cell). After env attach, perform the cell
                    // store (which will reload from the function value into the cell).
                    // FlatBindFunction.function carries the synthetic *unique* name
                    // (e.g. "reader$local1"); ClosureInfo is keyed by qualifiedName.
                    val childQn = nameToQualified[inst.function.name]
                    val childInfo = childQn?.let { info[it] }
                    val childClosureVars = childInfo?.closureVars.orEmpty()
                    val childCaptures = childClosureVars.isNotEmpty()

                    val originalTarget = inst.target

                    if (childCaptures && originalTarget is FlatLocal && isCellManaged(originalTarget.name)) {
                        // The bound function lives in a cell. Strategy: emit the bind
                        // with a temp target, attach the env to that temp (so the
                        // function value gets `_closure_env_`), then store the temp
                        // into the cell.
                        val tmp = freshTemp()
                        val newBind = inst.copy(target = tmp)
                        // Build env on parent's cells.
                        val (envInst, envAttachInst) = buildEnvAttach(
                            parentTargetForAttach = tmp,
                            childClosureVars = childClosureVars,
                            line = line,
                        )
                        post.add(envInst)
                        post.add(envAttachInst)
                        post.add(storeCell(originalTarget.name, tmp, line))
                        return listOf(newBind) + post
                    }

                    if (childCaptures) {
                        // Target not cell-managed (or not a FlatLocal). Attach env directly.
                        val (envInst, envAttachInst) = buildEnvAttach(
                            parentTargetForAttach = originalTarget,
                            childClosureVars = childClosureVars,
                            line = line,
                        )
                        post.add(envInst)
                        post.add(envAttachInst)
                        // Now handle a cell-managed target normally — but already
                        // returned above for that case. Keep instruction unchanged here.
                        inst
                    } else {
                        // No capture: handle target redirect like any other write.
                        val redir = redirectTarget(originalTarget)
                        if (redir != null) {
                            val (tmp, name) = redir
                            post.add(storeCell(name, tmp, line))
                            inst.copy(target = tmp)
                        } else {
                            inst
                        }
                    }
                }
                is FlatGoto -> inst
                is FlatDeleteGlobal -> inst
                FlatUnreachable -> inst
            }

            return if (rewritten == null) {
                pre + post
            } else {
                pre + rewritten + post
            }
        }

        /**
         * Returns (envBuildInst, envStoreAttrInst). The env dict's keys are the child's
         * closureVars (sorted) and values are the *parent*'s cell locals (this function's
         * `cellLocals`). The store attaches the env to [parentTargetForAttach].
         */
        private fun buildEnvAttach(
            parentTargetForAttach: FlatValue,
            childClosureVars: Set<String>,
            line: Int,
        ): Pair<FlatInst, FlatInst> {
            val sortedNames = childClosureVars.toSortedSet().toList()
            val keys: List<FlatValue> = sortedNames.map { FlatStrConst(it) }
            val values: List<FlatValue> = sortedNames.map { name ->
                // Parent must own a cell for every name the child captures —
                // either through `cellVars` (this fn defines it) or through
                // `closureVars` (this fn received it from above). The only way
                // this can fail in practice is the METHOD-pass-through gap
                // (see [ClosureAnalyzer.isClosureRoot]'s comment): if a method
                // bind-emits a capturing nested def that needs an outer-fn
                // local, the analyzer's closure-root override leaves the
                // method's `cellLocals` empty for that name. The enclosing
                // `try/catch` in [rewriteFunction] turns this into a
                // PIRDiagnostic and bails on the method. Unreachable from
                // real proto→Flat input today (FlatClass cannot represent
                // class-inside-function), but exercised by
                // FlatClosureTransformerTest's known-limitation case.
                cellLocals[name]
                    ?: error(
                        "Closure rewrite: child captures '$name' but parent " +
                            "${fn.qualifiedName} has no cell for it (cells: ${cellLocals.keys})",
                    )
            }
            val envInst = FlatBuildDict(
                target = envLocal,
                keys = keys,
                values = values,
                line = line,
            )
            val attachInst = FlatStoreAttr(
                obj = parentTargetForAttach,
                attribute = ClosureRuntime.CLOSURE_ATTR_NAME,
                value = envLocal,
                line = line,
            )
            return envInst to attachInst
        }
    }

    /* ------------------------------------------------------------------ */
    /* Constants                                                          */
    /* ------------------------------------------------------------------ */

    private const val ENV_LOCAL_NAME = "\$env"

    private fun cellLocalName(name: String): String = "\$cell\$$name"

    private fun selfParameter(): FlatParameter = FlatParameter(
        name = ClosureRuntime.SELF_PARAM_NAME,
        type = FlatAnyType,
        kind = FlatParamKind.POSITIONAL_OR_KEYWORD,
        hasDefault = false,
        defaultValue = null,
    )

}
