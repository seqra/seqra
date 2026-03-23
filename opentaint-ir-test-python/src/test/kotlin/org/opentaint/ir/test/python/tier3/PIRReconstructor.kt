package org.opentaint.ir.test.python.tier3

import org.opentaint.ir.api.python.*

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

    // Captured variables for the current function being reconstructed.
    // In inner functions: these vars are accessed via __env__['name']
    // In outer functions: these vars are stored into __env__ dict
    private var currentCapturedVars: Set<String> = emptySet()
    // Whether the current function is an inner (nested) function that receives __env__
    private var currentIsInnerWithEnv: Boolean = false
    // Mapping: nested function name -> its closureVars (for outer function to know what to pass)
    private var nestedFuncCaptureMap: Map<String, List<String>> = emptyMap()

    fun reconstruct(func: PIRFunction): String {
        currentEmittedFuncNames = emptySet()
        currentCapturedVars = emptySet()
        currentIsInnerWithEnv = false
        nestedFuncCaptureMap = emptyMap()
        return reconstructSingle(func)
    }

    /**
     * Reconstruct a function along with any lambda/nested functions it references.
     *
     * Nested functions with captured variables use an __env__ dict pattern:
     * - Inner function: receives __env__ as first parameter, reads/writes captured
     *   vars via __env__['name']
     * - Outer function: creates __env__ dict with captured vars, passes it to
     *   inner function calls
     */
    fun reconstructWithLambdas(func: PIRFunction, cp: PIRClasspath): String {
        val sb = StringBuilder()
        val lambdaRefs = collectLambdaRefs(func, func.module.name)
        val emittedFuncNames = mutableSetOf<String>()
        val captureMap = mutableMapOf<String, List<String>>()

        // Find referenced lambda/nested functions
        val resolvedFuncs = mutableListOf<PIRFunction>()
        for (ref in lambdaRefs) {
            var lambdaFunc = cp.findFunctionOrNull("${func.module.name}.$ref")
            if (lambdaFunc == null) {
                for (mod in cp.modules) {
                    lambdaFunc = mod.functions.find { it.name == ref }
                    if (lambdaFunc != null) break
                }
            }
            if (lambdaFunc != null) {
                resolvedFuncs.add(lambdaFunc)
                emittedFuncNames.add(ref)
                if (lambdaFunc.closureVars.isNotEmpty()) {
                    captureMap[ref] = lambdaFunc.closureVars
                }
            }
        }

        // Reconstruct each nested function
        for (lambdaFunc in resolvedFuncs) {
            val cvars = lambdaFunc.closureVars.toSet()
            sb.append(reconstructSingle(lambdaFunc, emittedFuncNames,
                capturedVars = cvars, isInner = cvars.isNotEmpty()))
            sb.appendLine()
        }

        // Collect all captured vars across all nested functions
        val allCaptured = captureMap.values.flatten().toSet()

        // Build local-var-name -> captures map by scanning the outer CFG for
        // assignments like: local_var = GlobalRef(nested_func_unique_name)
        val localVarCaptureMap = mutableMapOf<String, List<String>>()
        localVarCaptureMap.putAll(captureMap)  // funcName -> captures
        for (block in func.cfg.blocks) {
            for (inst in block.instructions) {
                if (inst is PIRAssign && inst.source is PIRGlobalRef && inst.target is PIRLocal) {
                    val refName = (inst.source as PIRGlobalRef).name
                    val localName = (inst.target as PIRLocal).name
                    if (refName in captureMap && localName != refName) {
                        localVarCaptureMap[localName] = captureMap[refName]!!
                    }
                }
            }
        }

        // Reconstruct the outer function
        nestedFuncCaptureMap = localVarCaptureMap
        sb.append(reconstructSingle(func, emittedFuncNames,
            capturedVars = allCaptured, isInner = false))
        nestedFuncCaptureMap = emptyMap()
        return sb.toString()
    }

    private fun reconstructSingle(
        func: PIRFunction,
        emittedFuncNames: Set<String> = emptySet(),
        capturedVars: Set<String> = emptySet(),
        isInner: Boolean = false,
    ): String {
        currentEmittedFuncNames = emittedFuncNames
        currentCapturedVars = capturedVars
        currentIsInnerWithEnv = isInner
        val sb = StringBuilder()

        val paramNames = func.parameters.map { it.name }.toSet()

        // Inner functions with captures get __env__ as first parameter
        val params = func.parameters.joinToString(", ") { p ->
            if (p.hasDefault && p.defaultValue != null) {
                "${p.name}=${val_(p.defaultValue!!)}"
            } else {
                p.name
            }
        }
        val fullParams = if (isInner) "__env__, $params" else params
        sb.appendLine("def ${sanitizeFuncName(func.name)}($fullParams):")

        val blocks = func.cfg.blocks
        if (blocks.isEmpty()) {
            sb.appendLine("    pass")
            return sb.toString()
        }

        // Outer function with captures: create __env__ dict and seed with params
        if (!isInner && capturedVars.isNotEmpty()) {
            sb.appendLine("    __env__ = {}")
            // Initialize __env__ with parameter values that are captured
            for (cv in capturedVars.sorted()) {
                if (cv in paramNames) {
                    sb.appendLine("    __env__['$cv'] = $cv")
                }
            }
        }

        // Collect all locals used (except parameters, emitted function names, and captured vars)
        val locals = mutableSetOf<String>()
        for (block in blocks) {
            for (inst in block.instructions) {
                collectLocals(inst, locals)
            }
        }
        locals.removeAll(paramNames)
        locals.removeAll(emittedFuncNames)
        if (isInner) {
            locals.removeAll(capturedVars)  // Inner: captured vars live in __env__, not as locals
        }
        locals.remove("")

        // Declare locals
        for (local in locals.sorted()) {
            if (!local.startsWith("\$")) {
                sb.appendLine("    $local = None")
            } else {
                sb.appendLine("    ${sanitize(local)} = None")
            }
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
        for (block in func.cfg.blocks) {
            for (inst in block.instructions) {
                collectLambdaRefsFromInstruction(inst, refs, moduleName)
            }
        }
        return refs
    }

    private fun collectLambdaRefsFromInstruction(inst: PIRInstruction, refs: MutableSet<String>, moduleName: String) {
        fun checkValue(v: PIRValue) {
            if (v is PIRGlobalRef) {
                if (v.name.startsWith("<lambda>")) {
                    refs.add(v.name)
                } else if (v.module == moduleName) {
                    // Same module GlobalRef — candidate nested function (verified during resolution)
                    refs.add(v.name)
                }
            }
        }
        when (inst) {
            is PIRAssign -> { checkValue(inst.target); checkValue(inst.source) }
            is PIRBinOp -> { checkValue(inst.target); checkValue(inst.left); checkValue(inst.right) }
            is PIRUnaryOp -> { checkValue(inst.target); checkValue(inst.operand) }
            is PIRCompare -> { checkValue(inst.target); checkValue(inst.left); checkValue(inst.right) }
            is PIRCall -> { inst.target?.let { checkValue(it) }; checkValue(inst.callee); inst.args.forEach { checkValue(it.value) } }
            is PIRLoadAttr -> { checkValue(inst.target); checkValue(inst.obj) }
            is PIRStoreAttr -> { checkValue(inst.obj); checkValue(inst.value) }
            is PIRLoadSubscript -> { checkValue(inst.target); checkValue(inst.obj); checkValue(inst.index) }
            is PIRStoreSubscript -> { checkValue(inst.obj); checkValue(inst.index); checkValue(inst.value) }
            is PIRLoadGlobal -> checkValue(inst.target)
            is PIRStoreGlobal -> checkValue(inst.value)
            is PIRBuildList -> { checkValue(inst.target); inst.elements.forEach { checkValue(it) } }
            is PIRBuildTuple -> { checkValue(inst.target); inst.elements.forEach { checkValue(it) } }
            is PIRBuildSet -> { checkValue(inst.target); inst.elements.forEach { checkValue(it) } }
            is PIRBuildDict -> { checkValue(inst.target); inst.keys.forEach { checkValue(it) }; inst.values.forEach { checkValue(it) } }
            is PIRBuildSlice -> { checkValue(inst.target); inst.lower?.let { checkValue(it) }; inst.upper?.let { checkValue(it) }; inst.step?.let { checkValue(it) } }
            is PIRBuildString -> { checkValue(inst.target); inst.parts.forEach { checkValue(it) } }
            is PIRGetIter -> { checkValue(inst.target); checkValue(inst.iterable) }
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
            is PIRLoadClosure -> checkValue(inst.target)
            is PIRStoreClosure -> checkValue(inst.value)
            is PIRGoto, is PIRDeleteGlobal, is PIRTypeCheck, is PIRUnreachable -> {}
        }
    }

    private fun reconstructInstruction(inst: PIRInstruction): List<String> {
        return when (inst) {
            is PIRAssign -> {
                // Skip self-assignments for nested function references (inner = GlobalRef("inner"))
                // but NOT lambda assignments (ident = GlobalRef("<lambda>$6")) where names differ
                val srcRef = inst.source as? PIRGlobalRef
                val tgtLocal = inst.target as? PIRLocal
                val isSelfAssign = srcRef != null && tgtLocal != null
                    && srcRef.name == tgtLocal.name && srcRef.name in currentEmittedFuncNames
                if (isSelfAssign) {
                    emptyList()
                } else {
                    val lines = mutableListOf("${val_(inst.target)} = ${val_(inst.source)}")
                    // Outer function: also store to __env__ if target is a captured var
                    if (!currentIsInnerWithEnv && inst.target is PIRLocal
                        && (inst.target as PIRLocal).name in currentCapturedVars) {
                        val name = (inst.target as PIRLocal).name
                        lines.add("__env__['$name'] = $name")
                    }
                    lines
                }
            }
            is PIRBinOp -> listOf("${val_(inst.target)} = ${val_(inst.left)} ${binOp(inst.op)} ${val_(inst.right)}")
            is PIRUnaryOp -> listOf("${val_(inst.target)} = ${unaryOp(inst.op)}${val_(inst.operand)}")
            is PIRCompare -> listOf("${val_(inst.target)} = ${val_(inst.left)} ${cmpOp(inst.op)} ${val_(inst.right)}")
            is PIRCall -> {
                val argsStr = inst.args.joinToString(", ") { callArg(it) }
                // Inject __env__ as first argument if callee is a nested function with captures
                val calleeName = when (inst.callee) {
                    is PIRLocal -> (inst.callee as PIRLocal).name
                    is PIRGlobalRef -> (inst.callee as PIRGlobalRef).name
                    else -> ""
                }
                val needsEnv = !currentIsInnerWithEnv && calleeName in nestedFuncCaptureMap
                val fullArgs = if (needsEnv) {
                    if (argsStr.isEmpty()) "__env__" else "__env__, $argsStr"
                } else argsStr
                val call = "${val_(inst.callee)}($fullArgs)"
                val t = inst.target
                val lines = mutableListOf<String>()
                if (t != null) lines.add("${val_(t)} = $call")
                else lines.add(call)
                // After calling a nested function, read back captured vars from __env__
                if (needsEnv) {
                    for (cv in nestedFuncCaptureMap[calleeName] ?: emptyList()) {
                        lines.add("$cv = __env__['$cv']")
                    }
                }
                lines
            }
            is PIRLoadAttr -> listOf("${val_(inst.target)} = ${val_(inst.obj)}.${inst.attribute}")
            is PIRStoreAttr -> listOf("${val_(inst.obj)}.${inst.attribute} = ${val_(inst.value)}")
            is PIRLoadSubscript -> listOf("${val_(inst.target)} = ${val_(inst.obj)}[${val_(inst.index)}]")
            is PIRStoreSubscript -> listOf("${val_(inst.obj)}[${val_(inst.index)}] = ${val_(inst.value)}")
            is PIRLoadGlobal -> listOf("${val_(inst.target)} = ${inst.name}")
            is PIRStoreGlobal -> listOf("${inst.name} = ${val_(inst.value)}")
            is PIRBuildList -> {
                val elems = inst.elements.joinToString(", ") { val_(it) }
                listOf("${val_(inst.target)} = [$elems]")
            }
            is PIRBuildTuple -> {
                val elems = inst.elements.joinToString(", ") { val_(it) }
                listOf("${val_(inst.target)} = ($elems,)")
            }
            is PIRBuildSet -> {
                val elems = inst.elements.joinToString(", ") { val_(it) }
                if (elems.isEmpty()) listOf("${val_(inst.target)} = set()")
                else listOf("${val_(inst.target)} = {$elems}")
            }
            is PIRBuildDict -> {
                val pairs = inst.keys.zip(inst.values).joinToString(", ") { (k, v) ->
                    "${val_(k)}: ${val_(v)}"
                }
                listOf("${val_(inst.target)} = {$pairs}")
            }
            is PIRBuildSlice -> {
                val lo = if (inst.lower != null) val_(inst.lower!!) else "None"
                val hi = if (inst.upper != null) val_(inst.upper!!) else "None"
                val st = if (inst.step != null) val_(inst.step!!) else "None"
                listOf("${val_(inst.target)} = slice($lo, $hi, $st)")
            }
            is PIRBuildString -> {
                val parts = inst.parts.joinToString(" + ") { "str(${val_(it)})" }
                listOf("${val_(inst.target)} = $parts")
            }
            is PIRGetIter -> listOf("${val_(inst.target)} = iter(${val_(inst.iterable)})")
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
            is PIRTypeCheck -> listOf("${val_(inst.target)} = isinstance(${val_(inst.value)}, object)")
            is PIRLoadClosure -> listOf("${val_(inst.target)} = ${inst.name}")
            is PIRStoreClosure -> listOf("${inst.name} = ${val_(inst.value)}")
            is PIRUnreachable -> listOf("raise RuntimeError('unreachable')")
        }
    }

    private fun val_(v: PIRValue): String {
        return when (v) {
            is PIRLocal -> {
                if (currentIsInnerWithEnv && v.name in currentCapturedVars) {
                    "__env__['${v.name}']"
                } else {
                    sanitize(v.name)
                }
            }
            is PIRParameterRef -> v.name
            is PIRIntConst -> v.value.toString()
            is PIRFloatConst -> v.value.toString()
            is PIRStrConst -> "\"${v.value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is PIRBoolConst -> if (v.value) "True" else "False"
            is PIRNoneConst -> "None"
            is PIREllipsisConst -> "..."
            is PIRBytesConst -> "b\"...\""
            is PIRComplexConst -> "complex(${v.real}, ${v.imag})"
            is PIRGlobalRef -> sanitizeFuncName(v.name)
        }
    }

    private fun sanitize(name: String): String {
        // Replace $ with __ for valid Python identifiers
        return name.replace("\$", "__")
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

    private fun binOp(op: PIRBinaryOperator): String = when (op) {
        PIRBinaryOperator.ADD -> "+"
        PIRBinaryOperator.SUB -> "-"
        PIRBinaryOperator.MUL -> "*"
        PIRBinaryOperator.DIV -> "/"
        PIRBinaryOperator.FLOOR_DIV -> "//"
        PIRBinaryOperator.MOD -> "%"
        PIRBinaryOperator.POW -> "**"
        PIRBinaryOperator.MAT_MUL -> "@"
        PIRBinaryOperator.BIT_AND -> "&"
        PIRBinaryOperator.BIT_OR -> "|"
        PIRBinaryOperator.BIT_XOR -> "^"
        PIRBinaryOperator.LSHIFT -> "<<"
        PIRBinaryOperator.RSHIFT -> ">>"
    }

    private fun unaryOp(op: PIRUnaryOperator): String = when (op) {
        PIRUnaryOperator.NEG -> "-"
        PIRUnaryOperator.POS -> "+"
        PIRUnaryOperator.NOT -> "not "
        PIRUnaryOperator.INVERT -> "~"
    }

    private fun cmpOp(op: PIRCompareOperator): String = when (op) {
        PIRCompareOperator.EQ -> "=="
        PIRCompareOperator.NE -> "!="
        PIRCompareOperator.LT -> "<"
        PIRCompareOperator.LE -> "<="
        PIRCompareOperator.GT -> ">"
        PIRCompareOperator.GE -> ">="
        PIRCompareOperator.IS -> "is"
        PIRCompareOperator.IS_NOT -> "is not"
        PIRCompareOperator.IN -> "in"
        PIRCompareOperator.NOT_IN -> "not in"
    }

    private fun callArg(arg: PIRCallArg): String = when (arg.kind) {
        PIRCallArgKind.POSITIONAL -> val_(arg.value)
        PIRCallArgKind.KEYWORD -> "${arg.keyword}=${val_(arg.value)}"
        PIRCallArgKind.STAR -> "*${val_(arg.value)}"
        PIRCallArgKind.DOUBLE_STAR -> "**${val_(arg.value)}"
    }

    private fun collectLocals(inst: PIRInstruction, locals: MutableSet<String>) {
        when (inst) {
            is PIRAssign -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.source, locals) }
            is PIRBinOp -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.left, locals); collectLocalFromValue(inst.right, locals) }
            is PIRUnaryOp -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.operand, locals) }
            is PIRCompare -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.left, locals); collectLocalFromValue(inst.right, locals) }
            is PIRCall -> { inst.target?.let { collectLocalFromValue(it, locals) }; collectLocalFromValue(inst.callee, locals) }
            is PIRLoadAttr -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.obj, locals) }
            is PIRStoreAttr -> { collectLocalFromValue(inst.obj, locals); collectLocalFromValue(inst.value, locals) }
            is PIRLoadSubscript -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.obj, locals); collectLocalFromValue(inst.index, locals) }
            is PIRStoreSubscript -> { collectLocalFromValue(inst.obj, locals); collectLocalFromValue(inst.index, locals); collectLocalFromValue(inst.value, locals) }
            is PIRLoadGlobal -> collectLocalFromValue(inst.target, locals)
            is PIRStoreGlobal -> collectLocalFromValue(inst.value, locals)
            is PIRBuildList -> { collectLocalFromValue(inst.target, locals); inst.elements.forEach { collectLocalFromValue(it, locals) } }
            is PIRBuildTuple -> { collectLocalFromValue(inst.target, locals); inst.elements.forEach { collectLocalFromValue(it, locals) } }
            is PIRBuildSet -> { collectLocalFromValue(inst.target, locals); inst.elements.forEach { collectLocalFromValue(it, locals) } }
            is PIRBuildDict -> { collectLocalFromValue(inst.target, locals); inst.keys.forEach { collectLocalFromValue(it, locals) }; inst.values.forEach { collectLocalFromValue(it, locals) } }
            is PIRBuildSlice -> { collectLocalFromValue(inst.target, locals); inst.lower?.let { collectLocalFromValue(it, locals) }; inst.upper?.let { collectLocalFromValue(it, locals) }; inst.step?.let { collectLocalFromValue(it, locals) } }
            is PIRBuildString -> { collectLocalFromValue(inst.target, locals); inst.parts.forEach { collectLocalFromValue(it, locals) } }
            is PIRGetIter -> { collectLocalFromValue(inst.target, locals); collectLocalFromValue(inst.iterable, locals) }
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
            is PIRLoadClosure -> collectLocalFromValue(inst.target, locals)
            is PIRStoreClosure -> collectLocalFromValue(inst.value, locals)
            is PIRGoto, is PIRDeleteGlobal, is PIRTypeCheck, is PIRUnreachable -> {}
        }
    }

    private fun collectLocalFromValue(v: PIRValue, locals: MutableSet<String>) {
        if (v is PIRLocal) locals.add(v.name)
    }
}
