package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
import org.opentaint.ir.impl.python.flat.FlatAnyType
import org.opentaint.ir.impl.python.flat.FlatArgKind
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
import org.opentaint.ir.impl.python.flat.FlatCallArg
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatCompare
import org.opentaint.ir.impl.python.flat.FlatDeleteAttr
import org.opentaint.ir.impl.python.flat.FlatDeleteGlobal
import org.opentaint.ir.impl.python.flat.FlatDeleteLocal
import org.opentaint.ir.impl.python.flat.FlatDeleteSubscript
import org.opentaint.ir.impl.python.flat.FlatExceptHandler
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatFunctionKind
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
 *
 * **Callable-shim shape** (per `.agents/callable-shim/plan.md`): for every
 * capturing function (`closureVars` non-empty) the rewriter additionally
 * synthesizes an adapter `FlatClass` whose `__init__` stores `_closure_env_`
 * on `self` and whose `__call__` forwards to the renamed impl function. At
 * each `FlatBindFunction(target, child)` whose child captures, the bind is
 * replaced by a `FlatBuildDict + FlatCall` constructing the adapter class
 * with the env dict as its only argument.
 *
 * `FlatFunctionIR.closureVars` is populated from [ClosureInfo.closureVars]
 * so the PIR converter sees it.
 */
internal object ClosureRewriter {

    /**
     * Result of rewriting one capturing-or-cell-owning function: the (possibly
     * renamed) impl plus an optional adapter class. Non-capturing functions
     * with cellVars only return `impl` with `adapterClass = null`.
     */
    private data class RewriteOutput(
        val impl: FlatFunctionIR,
        val adapterClass: FlatClass? = null,
    )

    fun rewrite(module: FlatModuleIR, info: Map<String, ClosureInfo>): FlatModuleIR {
        val diagnostics = ArrayList<PIRDiagnostic>()

        // Build name → qualifiedName index over original module functions only.
        // This is the view the analyzer sees and the bind-site rewrite consults.
        val nameToQualified = buildNameToQualifiedIndex(module)

        // First scan: pick adapter class names + impl renames for every capturing
        // function. Both decisions must be visible BEFORE we walk bind sites.
        val capturingPlan = buildCapturingPlan(module, info)

        val adapterClasses = ArrayList<FlatClass>()

        val rewriteCtx = RewriteRunner(info, nameToQualified, capturingPlan, diagnostics)

        val newFunctions = module.functions.map {
            val out = rewriteCtx.rewriteFunction(it)
            out.adapterClass?.let(adapterClasses::add)
            out.impl
        }
        val newModuleInit = rewriteCtx.rewriteFunction(module.moduleInit).also {
            // Module init is never capturing — its kind is MODULE_INIT, a closure root.
            it.adapterClass?.let(adapterClasses::add)
        }.impl
        val newClasses = module.classes.map { rewriteCtx.rewriteClass(it, adapterClasses) }

        return module.copy(
            functions = newFunctions,
            moduleInit = newModuleInit,
            classes = newClasses + adapterClasses,
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

    /**
     * Per-capturing-function plan: synthetic adapter class name + impl-rename target.
     *
     * Keys are the *original* qualifiedName of the capturing impl function.
     */
    private data class CapturingEntry(
        val originalQn: String,
        val originalName: String,
        val moduleName: String,
        val adapterClassName: String,           // bare name like "<closure_inner$local1>"
        val adapterClassQn: String,             // module.<closure_inner$local1>
        val implRenamedName: String,            // unique-name in module.functions
        val implRenamedQn: String,              // module.<closure_inner$local1_impl>
    )

    private fun buildCapturingPlan(
        module: FlatModuleIR,
        info: Map<String, ClosureInfo>,
    ): Map<String, CapturingEntry> {
        val taken = HashSet<String>()
        // Reserve every existing function name + every existing class name to avoid collisions.
        for (fn in module.functions) taken.add(fn.name)
        for (fn in module.functions) taken.add(fn.qualifiedName)
        for (cls in module.classes) {
            taken.add(cls.name)
            taken.add(cls.qualifiedName)
        }
        taken.add(module.moduleInit.name)
        taken.add(module.moduleInit.qualifiedName)

        val out = LinkedHashMap<String, CapturingEntry>()
        for (fn in module.functions) {
            val ci = info[fn.qualifiedName] ?: continue
            if (ci.closureVars.isEmpty()) continue
            val baseName = fn.name
            // Synthetic angle-bracketed names cannot collide with user identifiers.
            val adapterClassName = uniquify("<closure_$baseName>", taken)
            val implRenamedName = uniquify("<closure_${baseName}_impl>", taken)
            val adapterClassQn = "${module.moduleName}.$adapterClassName"
            val implRenamedQn = "${module.moduleName}.$implRenamedName"
            taken.add(adapterClassName)
            taken.add(implRenamedName)
            taken.add(adapterClassQn)
            taken.add(implRenamedQn)
            out[fn.qualifiedName] = CapturingEntry(
                originalQn = fn.qualifiedName,
                originalName = fn.name,
                moduleName = module.moduleName,
                adapterClassName = adapterClassName,
                adapterClassQn = adapterClassQn,
                implRenamedName = implRenamedName,
                implRenamedQn = implRenamedQn,
            )
        }
        return out
    }

    private fun uniquify(candidate: String, taken: Set<String>): String {
        if (candidate !in taken) return candidate
        var i = 2
        while (true) {
            val attempt = candidate.removeSuffix(">") + "_$i>"
            if (attempt !in taken) return attempt
            i++
        }
    }

    /* ------------------------------------------------------------------ */
    /* Rewrite runner                                                     */
    /* ------------------------------------------------------------------ */

    private class RewriteRunner(
        private val info: Map<String, ClosureInfo>,
        private val nameToQualified: Map<String, String>,
        private val capturingPlan: Map<String, CapturingEntry>,
        private val diagnostics: MutableList<PIRDiagnostic>,
    ) {

        fun rewriteClass(
            cls: FlatClass,
            adapterAccumulator: MutableList<FlatClass>,
        ): FlatClass = cls.copy(
            methods = cls.methods.map {
                val out = rewriteFunction(it)
                out.adapterClass?.let(adapterAccumulator::add)
                out.impl
            },
            nestedClasses = cls.nestedClasses.map { rewriteClass(it, adapterAccumulator) },
        )

        /**
         * Per-function rewrite. Returns a [RewriteOutput] holding the new impl
         * function and an optional adapter class. On any thrown exception during
         * rewrite, appends a diagnostic and returns the function unchanged.
         */
        fun rewriteFunction(fn: FlatFunctionIR): RewriteOutput {
            val ci = info[fn.qualifiedName] ?: return RewriteOutput(fn)

            val hasCapturingChildAtBindSite = functionHasCapturingChildBind(fn)
            if (ci.cellVars.isEmpty() && ci.closureVars.isEmpty() && !hasCapturingChildAtBindSite) {
                return RewriteOutput(fn)
            }

            return try {
                val ctx = RewriteCtx(fn, ci, info, nameToQualified, capturingPlan)
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
                RewriteOutput(fn)
            }
        }

        private fun functionHasCapturingChildBind(fn: FlatFunctionIR): Boolean {
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
    }

    /* ------------------------------------------------------------------ */
    /* Per-function rewrite context                                       */
    /* ------------------------------------------------------------------ */

    private class RewriteCtx(
        private val fn: FlatFunctionIR,
        private val ci: ClosureInfo,
        private val info: Map<String, ClosureInfo>,
        private val nameToQualified: Map<String, String>,
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
        /* Adapter class synthesis                                        */
        /* -------------------------------------------------------------- */

        /**
         * Build the user-visible adapter class for a capturing impl function.
         *
         * Shape:
         * ```
         * class <closure_$base>:
         *     def __init__(self, _closure_env_):
         *         self._closure_env_ = _closure_env_
         *     def __call__(self, ...impl-user-params...):
         *         return _impl(self, ...impl-user-params...)
         * ```
         *
         * The original impl's user-visible parameters (i.e. its parameters
         * minus the synthetic `<self>` we just prepended) are mirrored on
         * `__call__` exactly — same kinds, defaults, types. Forwarding uses
         * arg kinds matched to each parameter kind.
         */
        private fun buildAdapterClass(entry: CapturingEntry, originalImpl: FlatFunctionIR): FlatClass {
            val initFn = buildInitMethod(entry)
            val callFn = buildCallMethod(entry, originalImpl)
            return FlatClass(
                name = entry.adapterClassName,
                qualifiedName = entry.adapterClassQn,
                baseClasses = emptyList(),
                mro = emptyList(),
                methods = listOf(initFn, callFn),
                fields = emptyList(),
                nestedClasses = emptyList(),
                decorators = emptyList(),
                isAbstract = false,
                isDataclass = false,
                isEnum = false,
            )
        }

        private fun buildInitMethod(entry: CapturingEntry): FlatFunctionIR {
            val selfLocal = FlatLocal("self")
            val envParamLocal = FlatLocal(ClosureRuntime.CLOSURE_ATTR_NAME)
            val cfg = FlatCFG(
                blocks = listOf(
                    FlatBlock(
                        label = 0,
                        instructions = listOf(
                            FlatStoreAttr(
                                obj = selfLocal,
                                attribute = ClosureRuntime.CLOSURE_ATTR_NAME,
                                value = envParamLocal,
                            ),
                            FlatReturn(null),
                        ),
                        exceptionHandlers = emptyList(),
                    ),
                ),
                entryBlock = 0,
                exitBlocks = listOf(0),
            )
            return FlatFunctionIR(
                name = "__init__",
                qualifiedName = "${entry.adapterClassQn}.__init__",
                parentQualifiedName = null,
                kind = FlatFunctionKind.METHOD,
                cfg = cfg,
                parameters = listOf(
                    plainParameter("self"),
                    plainParameter(ClosureRuntime.CLOSURE_ATTR_NAME),
                ),
                returnType = FlatAnyType,
                isAsync = false,
                isGenerator = false,
                decorators = emptyList(),
                closureVars = emptySet(),
                nonlocalNames = emptySet(),
                globalNames = emptySet(),
            )
        }

        /**
         * `__call__(self, p1, p2, …, *args, **kwargs)` — mirrors the impl's
         * user-visible parameters and forwards them positionally/keyword/star
         * to `_impl(self, …)`.
         */
        private fun buildCallMethod(entry: CapturingEntry, originalImpl: FlatFunctionIR): FlatFunctionIR {
            // Adapter `__call__` mirrors the impl's user-visible parameters
            // (= original impl parameters; the impl's synthetic <self> is added
            // by the prologue inside the impl itself).
            val implUserParams = originalImpl.parameters
            val callParams = listOf(plainParameter("self")) + implUserParams

            // Forward args: first positional is `self` (impl's <self>), then one
            // FlatCallArg per impl user-visible param, kind matched.
            val tmpReturn = FlatLocal("\$ret")
            val forwardArgs = listOf(FlatCallArg(FlatLocal("self"), FlatArgKind.POSITIONAL)) +
                implUserParams.map { p -> forwardArgFor(p) }

            val implRef = FlatGlobalRef(entry.implRenamedName, entry.moduleName)
            val cfg = FlatCFG(
                blocks = listOf(
                    FlatBlock(
                        label = 0,
                        instructions = listOf(
                            FlatCall(
                                target = tmpReturn,
                                callee = implRef,
                                args = forwardArgs,
                            ),
                            FlatReturn(tmpReturn),
                        ),
                        exceptionHandlers = emptyList(),
                    ),
                ),
                entryBlock = 0,
                exitBlocks = listOf(0),
            )
            return FlatFunctionIR(
                name = "__call__",
                qualifiedName = "${entry.adapterClassQn}.__call__",
                parentQualifiedName = null,
                kind = FlatFunctionKind.METHOD,
                cfg = cfg,
                parameters = callParams,
                returnType = originalImpl.returnType,
                isAsync = originalImpl.isAsync,
                isGenerator = originalImpl.isGenerator,
                decorators = emptyList(),
                closureVars = emptySet(),
                nonlocalNames = emptySet(),
                globalNames = emptySet(),
            )
        }

        private fun forwardArgFor(p: FlatParameter): FlatCallArg {
            val v = FlatLocal(p.name)
            return when (p.kind) {
                FlatParamKind.POSITIONAL_OR_KEYWORD ->
                    FlatCallArg(v, FlatArgKind.POSITIONAL)
                FlatParamKind.KEYWORD_ONLY ->
                    FlatCallArg(v, FlatArgKind.KEYWORD, keyword = p.name)
                FlatParamKind.VAR_POSITIONAL ->
                    FlatCallArg(v, FlatArgKind.STAR)
                FlatParamKind.VAR_KEYWORD ->
                    FlatCallArg(v, FlatArgKind.DOUBLE_STAR)
            }
        }

        private fun plainParameter(name: String): FlatParameter = FlatParameter(
            name = name,
            type = FlatAnyType,
            kind = FlatParamKind.POSITIONAL_OR_KEYWORD,
            hasDefault = false,
            defaultValue = null,
        )

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
                    // Resolve child closure info via the (pre-rename) name index.
                    val childQn = nameToQualified[inst.function.name]
                    val childInfo = childQn?.let { info[it] }
                    val childClosureVars = childInfo?.closureVars.orEmpty()
                    val childCaptures = childClosureVars.isNotEmpty()

                    if (childCaptures) {
                        // Replace bind with adapter-class constructor call.
                        val childEntry = childQn?.let { capturingPlan[it] }
                            ?: error(
                                "Closure rewrite: child '${inst.function.name}' captures " +
                                    "but has no capturing-plan entry (childQn=$childQn)",
                            )
                        val originalTarget = inst.target

                        // Build env on parent's cells.
                        val (envBuildInst, envValueLocal) = buildEnvDict(childClosureVars, line)

                        // Constructor call: target = <closure_inner>(env)
                        if (originalTarget is FlatLocal && isCellManaged(originalTarget.name)) {
                            // Cell-managed bind target: emit constructor into a temp,
                            // then store the temp into the cell.
                            val tmp = freshTemp()
                            val ctorCall = FlatCall(
                                target = tmp,
                                callee = FlatGlobalRef(childEntry.adapterClassName, childEntry.moduleName),
                                args = listOf(FlatCallArg(envValueLocal)),
                                line = line,
                            )
                            // Sequence: env-build, ctor-call, then store tmp into cell.
                            return listOf(envBuildInst, ctorCall, storeCell(originalTarget.name, tmp, line))
                        }

                        val ctorCall = FlatCall(
                            target = originalTarget,
                            callee = FlatGlobalRef(childEntry.adapterClassName, childEntry.moduleName),
                            args = listOf(FlatCallArg(envValueLocal)),
                            line = line,
                        )
                        return listOf(envBuildInst, ctorCall)
                    } else {
                        // Non-capturing child: keep FlatBindFunction; only handle cell-managed target.
                        val redir = redirectTarget(inst.target)
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
         * Build the env dict on parent's cells. Returns the build-dict
         * instruction and a freshly-allocated env local that the constructor
         * call will receive.
         */
        private fun buildEnvDict(childClosureVars: Set<String>, line: Int): Pair<FlatInst, FlatLocal> {
            val sortedNames = childClosureVars.toSortedSet().toList()
            val keys: List<FlatValue> = sortedNames.map { FlatStrConst(it) }
            val values: List<FlatValue> = sortedNames.map { name ->
                cellLocals[name]
                    ?: error(
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
