package org.opentaint.ir.test.python.tier3

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.transforms.closure.ClosureRuntime

/**
 * Converts a PIRFunction's CFG back into executable Python code.
 *
 * Uses a while-True + __state variable pattern to simulate basic-block jumps:
 *
 *     def reconstructed(args):
 *         __state = 0
 *         while True:
 *             if __state == 0:
 *                 ...
 *                 __state = 1
 *             elif __state == 1:
 *                 ...
 */
class PIRReconstructor {

    // Names of functions emitted as top-level defs (lambdas and nested functions).
    // When reconstructing an outer function, assignments like `inner = inner` (GlobalRef)
    // should be skipped since `inner` is already available from module scope.
    private var currentEmittedFuncNames: Set<String> = emptySet()

    /**
     * Global names of closure-bearing functions visible from the function
     * currently being reconstructed. At a `PIRBindFunctionExpr` whose function
     * is in this set, the bind site emits `target = name()` to instantiate a
     * fresh `_closure_class` wrapper (rather than `target = name`). Call sites
     * don't consult this set — `__call__` on the wrapper forwards `<self>`
     * automatically.
     */
    private var closureBearingNames: Set<String> = emptySet()

    fun reconstruct(func: PIRFunction): String {
        currentEmittedFuncNames = emptySet()
        closureBearingNames = if (isClosureBearing(func)) setOf(func.name) else emptySet()
        return MODULE_PRELUDE + reconstructSingle(func)
    }

    /**
     * Emit Python source for a synthetic adapter class produced by the
     * callable-shim closure refactor. Only handles the two known synthetic
     * shapes: `__init__(self, _closure_env_)` storing the env, and
     * `__call__(self, …)` forwarding to the impl.
     *
     * Real (user-defined) classes are not the concern of this reconstructor —
     * round-trip tests today are function-only.
     */
    private fun reconstructAdapterClass(cls: PIRClass): String {
        val sb = StringBuilder()
        val sanitizedClassName = sanitizeFuncName(cls.name)
        sb.appendLine("class $sanitizedClassName:")
        for (method in cls.methods) {
            val paramList = method.parameters.joinToString(", ") { p ->
                val pname = sanitizeLocal(p.name)
                when (p.kind) {
                    PIRParameterKind.VAR_POSITIONAL -> "*$pname"
                    PIRParameterKind.VAR_KEYWORD -> "**$pname"
                    PIRParameterKind.KEYWORD_ONLY -> pname  // Caller passes by kw; def is just `name=`
                    PIRParameterKind.POSITIONAL_OR_KEYWORD,
                    PIRParameterKind.POSITIONAL_ONLY -> pname
                }
            }
            sb.appendLine("    def ${method.name}($paramList):")
            // Each method has a tiny CFG built by the rewriter — emit its instructions
            // directly with 8-space indent.
            for (block in method.cfg.blocks) {
                for (inst in block.instructions) {
                    val lines = reconstructInstruction(inst)
                    for (line in lines) {
                        sb.appendLine("        $line")
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Reconstruct a function along with any lambda/nested functions it references.
     *
     * Closure semantics are encoded in the IR: capturing functions take `<self>`
     * and read captured vars via cell loads (`<self>._closure_env_['x'].value`).
     * The reconstructor only has to thread the function value into `<self>` at
     * call sites — the rest falls out of the IR shape.
     */
    fun reconstructWithLambdas(func: PIRFunction, cp: PIRClasspath): String {
        val sb = StringBuilder()
        sb.append(MODULE_PRELUDE)

        // Find all referenced lambda/nested functions, transitively. A nested
        // function may itself reference further nested functions (factory
        // pattern, three-level nesting), and all of them need to land at module
        // scope or the call chain breaks at runtime.
        val emittedFuncNames = mutableSetOf<String>()
        val closureBearingFuncs = mutableSetOf<String>()
        val resolvedFuncs = mutableListOf<PIRFunction>()
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque<PIRFunction>()
        queue.addAll(
            collectLambdaRefs(func, func.module.name).mapNotNull { resolveLambda(it, cp, func.module.name) }
        )
        while (queue.isNotEmpty()) {
            val nested = queue.removeFirst()
            if (!seen.add(nested.name)) continue
            resolvedFuncs.add(nested)
            emittedFuncNames.add(nested.name)
            if (isClosureBearing(nested)) {
                closureBearingFuncs.add(nested.name)
            }
            for (ref in collectLambdaRefs(nested, func.module.name)) {
                if (ref !in seen) {
                    resolveLambda(ref, cp, func.module.name)?.let { queue.add(it) }
                }
            }
        }

        // Synthetic adapter classes from the callable-shim refactor. The bind
        // sites for capturing nested defs are PIRCalls to these classes, so
        // they must be visible at module scope in the reconstructed source.
        // Adapter class names start with `<closure_` and are not user-defined.
        // Also pull in the matching `<closure_X_impl>` functions, which the
        // adapter's __call__ forwards to.
        val moduleClasses = func.module.classes.filter { it.name.startsWith("<closure_") }
        for (cls in moduleClasses) {
            // Walk the adapter's __call__ to find the impl function it forwards to.
            for (method in cls.methods) {
                for (inst in method.cfg.instList) {
                    if (inst is PIRCall) {
                        val callee = inst.callee
                        if (callee is PIRGlobalRef) {
                            val calleeShort = callee.qualifiedName.substringAfterLast('.')
                            if (calleeShort.startsWith("<closure_") && calleeShort.endsWith("_impl>")) {
                                if (calleeShort !in seen) {
                                    val implFn = cp.modules.flatMap { it.functions }.firstOrNull { it.name == calleeShort }
                                    if (implFn != null) {
                                        seen.add(implFn.name)
                                        resolvedFuncs.add(implFn)
                                        emittedFuncNames.add(implFn.name)
                                        if (isClosureBearing(implFn)) {
                                            closureBearingFuncs.add(implFn.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (cls in moduleClasses) {
            sb.append(reconstructAdapterClass(cls))
            sb.appendLine()
        }

        // Reconstruct each nested/lambda function
        closureBearingNames = closureBearingFuncs
        for (lambdaFunc in resolvedFuncs) {
            sb.append(reconstructSingle(lambdaFunc, emittedFuncNames))
            sb.appendLine()
        }

        // Reconstruct the outer function. Bind sites — `PIRAssign(local,
        // PIRBindFunctionExpr(closure_bearing))` — instantiate the wrapper
        // class so each bind owns its own `_closure_env_`.
        sb.append(reconstructSingle(func, emittedFuncNames))
        closureBearingNames = emptySet()
        return sb.toString()
    }

    /**
     * A function is closure-bearing iff it carries the synthetic `<self>`
     * parameter. With the callable-shim refactor, capturing impls keep
     * `<self>` but are invoked *through* an adapter class — the adapter's
     * `__call__` forwards `self` (the adapter instance) as the impl's
     * `<self>`. So impl functions named `<closure_X_impl>` are NOT wrapped
     * in `@_closure_class`; they're plain functions called directly by the
     * adapter.
     */
    private fun isClosureBearing(func: PIRFunction): Boolean {
        val hasSelf = func.parameters.firstOrNull()?.name == SELF_PARAM_RAW
        if (!hasSelf) return false
        val n = func.name
        // Callable-shim impls: invoked directly by the adapter, not wrapped.
        if (n.startsWith("<closure_") && n.endsWith("_impl>")) return false
        return true
    }

    /**
     * Resolve a `<lambda>$N` / nested-function name to its `PIRFunction`. First
     * tries the qualified name in the requesting module, then falls back to a
     * scan across all modules for a bare-name match.
     */
    private fun resolveLambda(name: String, cp: PIRClasspath, moduleName: String): PIRFunction? {
        cp.findFunctionOrNull("$moduleName.$name")?.let { return it }
        for (mod in cp.modules) {
            mod.functions.find { it.name == name }?.let { return it }
        }
        return null
    }

    companion object {
        /** Synthetic parameter name injected by closure lowering. Invalid Python identifier. */
        private const val SELF_PARAM_RAW = ClosureRuntime.SELF_PARAM_NAME

        /** Sanitised replacement for [SELF_PARAM_RAW] in reconstructed Python source. */
        private const val SELF_PARAM_SAFE = "__self__"

        /**
         * Module-level definitions every reconstructed module needs:
         *
         * - `__pir_cell__`: a plain class. The IR allocates cells via
         *   `PIRCall(callee = builtins.__pir_cell__)`; instances of this class
         *   carry the mutable `.value` attribute.
         *
         * - `_closure_class`: the closure-binding decorator. Applied to every
         *   closure-bearing function so the global name refers to a *class*,
         *   not the raw function. The bind site instantiates the class
         *   (`inc = inc_local0()`) — each bind gets its own wrapper, so binds
         *   that share a lifted function but have different `_closure_env_`s
         *   (e.g. two `make_adder` invocations) don't clobber each other.
         *   Calling the wrapper dispatches through `__call__`, which forwards
         *   the wrapper instance as `<self>` to the underlying function.
         */
        private const val MODULE_PRELUDE = """class __pir_cell__:
    pass

def _closure_class(f):
    class _Wrapper:
        def __call__(self, *args, **kwargs):
            return f(self, *args, **kwargs)
    return _Wrapper

"""
    }

    private fun reconstructSingle(
        func: PIRFunction,
        emittedFuncNames: Set<String> = emptySet(),
    ): String {
        currentEmittedFuncNames = emittedFuncNames
        val sb = StringBuilder()

        val paramNames = func.parameters.map { it.name }.toSet()

        val params = func.parameters.joinToString(", ") { p ->
            val pname = sanitizeLocal(p.name)
            if (p.hasDefault && p.defaultValue != null) {
                "$pname=${val_(p.defaultValue!!)}"
            } else {
                pname
            }
        }
        // Closure-bearing functions get wrapped in @_closure_class so their
        // global name resolves to a class. The bind site instantiates the class
        // per bind, giving each bind a fresh wrapper that owns its own
        // `_closure_env_`.
        if (isClosureBearing(func)) {
            sb.appendLine("@_closure_class")
        }
        sb.appendLine("def ${sanitizeFuncName(func.name)}($params):")

        val blocks = func.cfg.blocks
        if (blocks.isEmpty()) {
            sb.appendLine("    pass")
            return sb.toString()
        }

        // Collect all locals used (except parameters and emitted function names).
        val locals = mutableSetOf<String>()
        for (inst in func.instList) {
            collectLocals(inst, locals)
        }
        locals.removeAll(paramNames)
        locals.removeAll(emittedFuncNames)
        locals.remove("")

        // Declare locals
        for (local in locals.sorted()) {
            sb.appendLine("    ${sanitizeLocal(local)} = None")
        }

        // Build a label->block map for looking up handler blocks
        val blockByLabel = blocks.associateBy { it.label }

        sb.appendLine("    __state = ${blocks.first().label}")
        sb.appendLine("    while True:")

        for (block in blocks) {
            val hasHandlers = block.exceptionHandlers.isNotEmpty()
            // Find the first handler label (the one to jump to on exception)
            val handlerLabel = if (hasHandlers) block.exceptionHandlers.first() else -1

            sb.appendLine("        if __state == ${block.label}:")
            if (block.instructions.isEmpty()) {
                sb.appendLine("            pass")
                continue
            }

            if (hasHandlers) {
                sb.appendLine("            try:")
                for (inst in block.instructions) {
                    val lines = reconstructInstruction(inst)
                    for (line in lines) {
                        sb.appendLine("                $line")
                    }
                }
                sb.appendLine("            except:")
                sb.appendLine("                __state = $handlerLabel")
                sb.appendLine("                continue")
            } else {
                for (inst in block.instructions) {
                    val lines = reconstructInstruction(inst)
                    for (line in lines) {
                        sb.appendLine("            $line")
                    }
                }
            }
        }

        return sb.toString()
    }

    /**
     * Collect all PIRGlobalRef names that are lambda or nested function references.
     * Includes `<lambda>$N` patterns and any GlobalRef from the same module that
     * corresponds to an extracted nested function.
     */
    private fun collectLambdaRefs(func: PIRFunction, moduleName: String = ""): Set<String> {
        val refs = mutableSetOf<String>()
        for (inst in func.instList) {
            collectLambdaRefsFromInstruction(inst, refs, moduleName)
        }
        return refs
    }

    private fun collectLambdaRefsFromInstruction(inst: PIRInstruction, refs: MutableSet<String>, moduleName: String) {
        fun checkValue(v: PIRValue) {
            if (v is PIRGlobalRef) {
                val short = v.qualifiedName.substringAfterLast('.')
                val module = v.qualifiedName.substringBeforeLast('.', "")
                if (short.startsWith("<lambda>")) {
                    refs.add(short)
                } else if (module == moduleName) {
                    // Same module GlobalRef — candidate nested function (verified during resolution)
                    refs.add(short)
                }
            }
        }
        fun checkExpr(expr: PIRExpr) {
            when (expr) {
                is PIRBinaryExpr -> { checkValue(expr.left); checkValue(expr.right) }
                is PIRUnaryExpr -> checkValue(expr.operand)
                is PIRCompareExpr -> { checkValue(expr.left); checkValue(expr.right) }
                is PIRSubscriptExpr -> { checkValue(expr.obj); checkValue(expr.index) }
                is PIRListExpr -> expr.elements.forEach { checkValue(it) }
                is PIRTupleExpr -> expr.elements.forEach { checkValue(it) }
                is PIRSetExpr -> expr.elements.forEach { checkValue(it) }
                is PIRDictExpr -> { expr.keys.forEach { checkValue(it) }; expr.values.forEach { checkValue(it) } }
                is PIRSliceExpr -> { expr.lower?.let { checkValue(it) }; expr.upper?.let { checkValue(it) }; expr.step?.let { checkValue(it) } }
                is PIRStringExpr -> expr.parts.forEach { checkValue(it) }
                is PIRIterExpr -> checkValue(expr.iterable)
                is PIRTypeCheckExpr -> checkValue(expr.value)
                is PIRBindFunctionExpr -> checkValue(expr.function)
                is PIRValue -> checkValue(expr)
            }
        }
        when (inst) {
            is PIRAssign -> { checkValue(inst.target); checkExpr(inst.expr) }
            is PIRLoadAttr -> { checkValue(inst.target); checkValue(inst.obj) }
            is PIRCall -> { inst.target?.let { checkValue(it) }; checkValue(inst.callee); inst.args.forEach { checkValue(it.value) } }
            is PIRStoreAttr -> { checkValue(inst.obj); checkValue(inst.value) }
            is PIRStoreSubscript -> { checkValue(inst.obj); checkValue(inst.index); checkValue(inst.value) }
            is PIRStoreGlobal -> checkValue(inst.value)
            is PIRStoreClosure -> checkValue(inst.value)
            is PIRNextIter -> { checkValue(inst.target); checkValue(inst.iterator) }
            is PIRUnpack -> { inst.targets.forEach { checkValue(it) }; checkValue(inst.source) }
            is PIRBranch -> checkValue(inst.condition)
            is PIRReturn -> inst.value?.let { checkValue(it) }
            is PIRRaise -> inst.exception?.let { checkValue(it) }
            is PIRExceptHandler -> inst.target?.let { checkValue(it) }
            is PIRYield -> { inst.target?.let { checkValue(it) }; inst.value?.let { checkValue(it) } }
            is PIRYieldFrom -> { inst.target?.let { checkValue(it) }; checkValue(inst.iterable) }
            is PIRAwait -> { inst.target?.let { checkValue(it) }; checkValue(inst.awaitable) }
            is PIRDeleteLocal -> checkValue(inst.local)
            is PIRDeleteAttr -> checkValue(inst.obj)
            is PIRDeleteSubscript -> { checkValue(inst.obj); checkValue(inst.index) }
            is PIRGoto, is PIRDeleteGlobal, is PIRUnreachable -> {}
        }
    }

    private fun reconstructInstruction(inst: PIRInstruction): List<String> {
        return when (inst) {
            is PIRAssign -> reconstructAssign(inst)
            is PIRLoadAttr -> listOf("${val_(inst.target)} = ${val_(inst.obj)}.${inst.attribute}")
            is PIRCall -> {
                // Closure-bearing callees route through their `_closure_class`
                // wrapper's `__call__`, which forwards the wrapper as `<self>`
                // automatically. Plain functions call as written. Either way,
                // the call site emits user arguments only.
                val args = inst.args.joinToString(", ") { callArg(it) }
                val call = "${val_(inst.callee)}($args)"
                val t = inst.target
                if (t != null) listOf("${val_(t)} = $call")
                else listOf(call)
            }
            is PIRStoreAttr -> listOf("${val_(inst.obj)}.${inst.attribute} = ${val_(inst.value)}")
            is PIRStoreSubscript -> listOf("${val_(inst.obj)}[${val_(inst.index)}] = ${val_(inst.value)}")
            is PIRStoreGlobal -> listOf("${inst.name} = ${val_(inst.value)}")
            is PIRStoreClosure -> listOf("${inst.name} = ${val_(inst.value)}")
            is PIRNextIter -> listOf(
                "try:",
                "    ${val_(inst.target)} = next(${val_(inst.iterator)})",
                "    __state = ${inst.bodyBlock}",
                "except StopIteration:",
                "    __state = ${inst.exitBlock}",
                "continue",
            )
            is PIRUnpack -> {
                val targets = inst.targets.joinToString(", ") { val_(it) }
                listOf("$targets = ${val_(inst.source)}")
            }
            is PIRGoto -> listOf("__state = ${inst.targetBlock}", "continue")
            is PIRBranch -> listOf(
                "if ${val_(inst.condition)}:",
                "    __state = ${inst.trueBlock}",
                "else:",
                "    __state = ${inst.falseBlock}",
                "continue",
            )
            is PIRReturn -> {
                if (inst.value != null) listOf("return ${val_(inst.value!!)}")
                else listOf("return None")
            }
            is PIRRaise -> {
                if (inst.exception != null) listOf("raise ${val_(inst.exception!!)}")
                else listOf("raise")
            }
            is PIRExceptHandler -> {
                // This marks the start of a handler block — it's a declaration, not executable
                if (inst.target != null) listOf("# except handler -> ${val_(inst.target!!)}")
                else listOf("# except handler")
            }
            is PIRYield -> {
                if (inst.value != null) {
                    if (inst.target != null)
                        listOf("${val_(inst.target!!)} = (yield ${val_(inst.value!!)})")
                    else listOf("yield ${val_(inst.value!!)}")
                } else {
                    if (inst.target != null) listOf("${val_(inst.target!!)} = (yield)")
                    else listOf("yield")
                }
            }
            is PIRYieldFrom -> {
                if (inst.target != null)
                    listOf("${val_(inst.target!!)} = (yield from ${val_(inst.iterable)})")
                else listOf("yield from ${val_(inst.iterable)}")
            }
            is PIRAwait -> {
                if (inst.target != null)
                    listOf("${val_(inst.target!!)} = await ${val_(inst.awaitable)}")
                else listOf("await ${val_(inst.awaitable)}")
            }
            is PIRDeleteLocal -> listOf("del ${val_(inst.local)}")
            is PIRDeleteAttr -> listOf("del ${val_(inst.obj)}.${inst.attribute}")
            is PIRDeleteSubscript -> listOf("del ${val_(inst.obj)}[${val_(inst.index)}]")
            is PIRDeleteGlobal -> listOf("del ${inst.name}")
            is PIRUnreachable -> listOf("raise RuntimeError('unreachable')")
        }
    }

    /**
     * Reconstruct a PIRAssign instruction by dispatching on the expression type.
     */
    private fun reconstructAssign(inst: PIRAssign): List<String> {
        val target = val_(inst.target)
        val exprLines = when (val expr = inst.expr) {
            is PIRBinaryExpr -> listOf("$target = ${val_(expr.left)} ${binOp(expr)} ${val_(expr.right)}")
            is PIRUnaryExpr -> listOf("$target = ${unaryOp(expr)}${val_(expr.operand)}")
            is PIRCompareExpr -> listOf("$target = ${val_(expr.left)} ${cmpOp(expr)} ${val_(expr.right)}")
            is PIRSubscriptExpr -> listOf("$target = ${val_(expr.obj)}[${val_(expr.index)}]")
            is PIRListExpr -> {
                val elems = expr.elements.joinToString(", ") { val_(it) }
                listOf("$target = [$elems]")
            }
            is PIRTupleExpr -> {
                val elems = expr.elements.joinToString(", ") { val_(it) }
                listOf("$target = ($elems,)")
            }
            is PIRSetExpr -> {
                val elems = expr.elements.joinToString(", ") { val_(it) }
                if (elems.isEmpty()) listOf("$target = set()")
                else listOf("$target = {$elems}")
            }
            is PIRDictExpr -> {
                val pairs = expr.keys.zip(expr.values).joinToString(", ") { (k, v) ->
                    "${val_(k)}: ${val_(v)}"
                }
                listOf("$target = {$pairs}")
            }
            is PIRSliceExpr -> {
                val lo = if (expr.lower != null) val_(expr.lower!!) else "None"
                val hi = if (expr.upper != null) val_(expr.upper!!) else "None"
                val st = if (expr.step != null) val_(expr.step!!) else "None"
                listOf("$target = slice($lo, $hi, $st)")
            }
            is PIRStringExpr -> {
                val parts = expr.parts.joinToString(" + ") { "str(${val_(it)})" }
                listOf("$target = $parts")
            }
            is PIRIterExpr -> listOf("$target = iter(${val_(expr.iterable)})")
            is PIRTypeCheckExpr -> listOf("$target = isinstance(${val_(expr.value)}, object)")
            is PIRBindFunctionExpr -> {
                // Bind site for a nested function. Two cases:
                //
                //  - Closure-bearing: the global resolves to a
                //    `_closure_class`-decorated wrapper *class*, so instantiate
                //    it. Each bind gets a fresh wrapper that owns its own
                //    `_closure_env_`, so two binds of the same lifted function
                //    (e.g. two `make_adder(n)` invocations) don't share state.
                //    Note: a *capturing recursive inner* (a closure-bearing
                //    function whose body re-binds itself) would re-instantiate
                //    the wrapper inside its own body and lose its
                //    `_closure_env_` — currently unreachable because the
                //    only recursive-inner test (`rtlf_recursive_inner`)
                //    doesn't capture, but worth knowing if such a test is added.
                //
                //  - Plain: `target = name` is a regular function-value alias.
                //    Skip when the bound function's name equals the target
                //    local (`inner = inner` is implicit for nested defs).
                val fnName = expr.function.qualifiedName.substringAfterLast('.')
                if (fnName in closureBearingNames) {
                    return listOf("$target = ${sanitizeFuncName(fnName)}()")
                }
                val tgtLocal = inst.target as? PIRLocal
                val isSelfAssign = tgtLocal != null
                    && fnName == tgtLocal.name
                    && fnName in currentEmittedFuncNames
                if (isSelfAssign) return emptyList()
                listOf("$target = ${val_(expr.function)}")
            }
            is PIRValue -> listOf("$target = ${val_(expr)}")
        }
        return exprLines
    }

    private fun val_(v: PIRValue): String {
        return when (v) {
            is PIRLocal -> sanitizeLocal(v.name)
            is PIRParameterRef -> sanitizeLocal(v.name)
            is PIRIntConst -> v.value.toString()
            is PIRFloatConst -> v.value.toString()
            is PIRStrConst -> "\"${v.value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is PIRBoolConst -> if (v.value) "True" else "False"
            is PIRNoneConst -> "None"
            is PIREllipsisConst -> "..."
            is PIRBytesConst -> "b\"...\""
            is PIRComplexConst -> "complex(${v.real}, ${v.imag})"
            is PIRGlobalRef -> sanitizeFuncName(v.qualifiedName.substringAfterLast('.'))
            is PIRModuleRef -> v.module
        }
    }

    /**
     * Sanitize a local variable / parameter name to a valid Python identifier.
     * Handles synthetic closure-lowered names: `$cell$x` → `__cell__x`,
     * `<self>` → `__self__`.
     */
    private fun sanitizeLocal(name: String): String {
        if (name == SELF_PARAM_RAW) return SELF_PARAM_SAFE
        return name
            .replace("\$", "__")
            .replace("<", "__")
            .replace(">", "__")
    }

    /**
     * Sanitize function names that contain invalid Python characters.
     * `<lambda>$0` -> `__lambda___0`
     */
    private fun sanitizeFuncName(name: String): String {
        return name
            .replace("<", "__")
            .replace(">", "__")
            .replace("\$", "_")
    }

    private fun binOp(expr: PIRBinaryExpr): String = when (expr) {
        is PIRAddExpr -> "+"
        is PIRSubExpr -> "-"
        is PIRMulExpr -> "*"
        is PIRDivExpr -> "/"
        is PIRFloorDivExpr -> "//"
        is PIRModExpr -> "%"
        is PIRPowExpr -> "**"
        is PIRMatMulExpr -> "@"
        is PIRBitAndExpr -> "&"
        is PIRBitOrExpr -> "|"
        is PIRBitXorExpr -> "^"
        is PIRLShiftExpr -> "<<"
        is PIRRShiftExpr -> ">>"
    }

    private fun unaryOp(expr: PIRUnaryExpr): String = when (expr) {
        is PIRNegExpr -> "-"
        is PIRPosExpr -> "+"
        is PIRNotExpr -> "not "
        is PIRInvertExpr -> "~"
    }

    private fun cmpOp(expr: PIRCompareExpr): String = when (expr) {
        is PIREqExpr -> "=="
        is PIRNeExpr -> "!="
        is PIRLtExpr -> "<"
        is PIRLeExpr -> "<="
        is PIRGtExpr -> ">"
        is PIRGeExpr -> ">="
        is PIRIsExpr -> "is"
        is PIRIsNotExpr -> "is not"
        is PIRInExpr -> "in"
        is PIRNotInExpr -> "not in"
    }

    private fun callArg(arg: PIRCallArg): String = when (arg.kind) {
        PIRCallArgKind.POSITIONAL -> val_(arg.value)
        PIRCallArgKind.KEYWORD -> "${arg.keyword}=${val_(arg.value)}"
        PIRCallArgKind.STAR -> "*${val_(arg.value)}"
        PIRCallArgKind.DOUBLE_STAR -> "**${val_(arg.value)}"
    }

    private fun collectLocals(inst: PIRInstruction, locals: MutableSet<String>) {
        fun collectExprLocals(expr: PIRExpr) {
            when (expr) {
                is PIRBinaryExpr -> { collectLocalFromValue(expr.left, locals); collectLocalFromValue(expr.right, locals) }
                is PIRUnaryExpr -> collectLocalFromValue(expr.operand, locals)
                is PIRCompareExpr -> { collectLocalFromValue(expr.left, locals); collectLocalFromValue(expr.right, locals) }
                is PIRSubscriptExpr -> { collectLocalFromValue(expr.obj, locals); collectLocalFromValue(expr.index, locals) }
                is PIRListExpr -> expr.elements.forEach { collectLocalFromValue(it, locals) }
                is PIRTupleExpr -> expr.elements.forEach { collectLocalFromValue(it, locals) }
                is PIRSetExpr -> expr.elements.forEach { collectLocalFromValue(it, locals) }
                is PIRDictExpr -> { expr.keys.forEach { collectLocalFromValue(it, locals) }; expr.values.forEach { collectLocalFromValue(it, locals) } }
                is PIRSliceExpr -> { expr.lower?.let { collectLocalFromValue(it, locals) }; expr.upper?.let { collectLocalFromValue(it, locals) }; expr.step?.let { collectLocalFromValue(it, locals) } }
                is PIRStringExpr -> expr.parts.forEach { collectLocalFromValue(it, locals) }
                is PIRIterExpr -> collectLocalFromValue(expr.iterable, locals)
                is PIRTypeCheckExpr -> collectLocalFromValue(expr.value, locals)
                is PIRBindFunctionExpr -> {}
                is PIRValue -> collectLocalFromValue(expr, locals)
            }
        }
        when (inst) {
            is PIRAssign -> { collectLocalFromValue(inst.target, locals); collectExprLocals(inst.expr) }
            is PIRLoadAttr -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.obj, locals) }
            is PIRCall -> { inst.target?.let { collectLocalFromValue(it, locals) }; collectLocalFromValue(inst.callee, locals) }
            is PIRStoreAttr -> { collectLocalFromValue(inst.obj, locals); collectLocalFromValue(inst.value, locals) }
            is PIRStoreSubscript -> { collectLocalFromValue(inst.obj, locals); collectLocalFromValue(inst.index, locals); collectLocalFromValue(inst.value, locals) }
            is PIRStoreGlobal -> collectLocalFromValue(inst.value, locals)
            is PIRStoreClosure -> collectLocalFromValue(inst.value, locals)
            is PIRNextIter -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.iterator, locals) }
            is PIRUnpack -> { inst.targets.forEach { collectLocalFromValue(it, locals) }; collectLocalFromValue(inst.source, locals) }
            is PIRBranch -> collectLocalFromValue(inst.condition, locals)
            is PIRReturn -> inst.value?.let { collectLocalFromValue(it, locals) }
            is PIRRaise -> inst.exception?.let { collectLocalFromValue(it, locals) }
            is PIRExceptHandler -> inst.target?.let { collectLocalFromValue(it, locals) }
            is PIRYield -> { inst.target?.let { collectLocalFromValue(it, locals) }; inst.value?.let { collectLocalFromValue(it, locals) } }
            is PIRYieldFrom -> { inst.target?.let { collectLocalFromValue(it, locals) }; collectLocalFromValue(inst.iterable, locals) }
            is PIRAwait -> { inst.target?.let { collectLocalFromValue(it, locals) }; collectLocalFromValue(inst.awaitable, locals) }
            is PIRDeleteLocal -> collectLocalFromValue(inst.local, locals)
            is PIRDeleteAttr -> collectLocalFromValue(inst.obj, locals)
            is PIRDeleteSubscript -> { collectLocalFromValue(inst.obj, locals); collectLocalFromValue(inst.index, locals) }
            is PIRGoto, is PIRDeleteGlobal, is PIRUnreachable -> {}
        }
    }

    private fun collectLocalFromValue(v: PIRValue, locals: MutableSet<String>) {
        if (v is PIRLocal) locals.add(v.name)
    }
}
