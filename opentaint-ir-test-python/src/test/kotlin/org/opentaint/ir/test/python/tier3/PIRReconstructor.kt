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

    fun reconstruct(func: PIRFunction): String {
        return reconstructSingle(func)
    }

    /**
     * Reconstruct a function along with any lambda functions it references.
     *
     * Lambda expressions are lowered to synthetic `<lambda>$N` functions.
     * This method scans for PIRGlobalRef values matching that pattern,
     * looks up the corresponding PIRFunction in the classpath, and emits
     * the lambda as a regular `def` with a sanitized name before the main function.
     */
    fun reconstructWithLambdas(func: PIRFunction, cp: PIRClasspath): String {
        val sb = StringBuilder()
        val lambdaRefs = collectLambdaRefs(func)

        // Reconstruct each referenced lambda as a top-level def
        for (ref in lambdaRefs) {
            val qualifiedName = "${func.module.name}.$ref"
            val lambdaFunc = cp.findFunctionOrNull(qualifiedName)
            if (lambdaFunc != null) {
                sb.append(reconstructSingle(lambdaFunc))
                sb.appendLine()
            }
        }

        // Reconstruct the main function
        sb.append(reconstructSingle(func))
        return sb.toString()
    }

    private fun reconstructSingle(func: PIRFunction): String {
        val sb = StringBuilder()
        val params = func.parameters.joinToString(", ") { it.name }
        sb.appendLine("def ${sanitizeFuncName(func.name)}($params):")

        val blocks = func.cfg.blocks
        if (blocks.isEmpty()) {
            sb.appendLine("    pass")
            return sb.toString()
        }

        // Collect all locals used (except parameters)
        val paramNames = func.parameters.map { it.name }.toSet()
        val locals = mutableSetOf<String>()
        for (block in blocks) {
            for (inst in block.instructions) {
                collectLocals(inst, locals)
            }
        }
        locals.removeAll(paramNames)
        locals.remove("") // remove empty names

        // Declare locals
        for (local in locals.sorted()) {
            if (!local.startsWith("\$")) {
                sb.appendLine("    $local = None")
            } else {
                // Temp variables use a sanitized name
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
     * Collect all PIRGlobalRef names that look like lambda references (`<lambda>$N`).
     */
    private fun collectLambdaRefs(func: PIRFunction): Set<String> {
        val refs = mutableSetOf<String>()
        for (block in func.cfg.blocks) {
            for (inst in block.instructions) {
                collectLambdaRefsFromInstruction(inst, refs)
            }
        }
        return refs
    }

    private fun collectLambdaRefsFromInstruction(inst: PIRInstruction, refs: MutableSet<String>) {
        fun checkValue(v: PIRValue) {
            if (v is PIRGlobalRef && v.name.startsWith("<lambda>")) {
                refs.add(v.name)
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
            is PIRAssign -> listOf("${val_(inst.target)} = ${val_(inst.source)}")
            is PIRBinOp -> listOf("${val_(inst.target)} = ${val_(inst.left)} ${binOp(inst.op)} ${val_(inst.right)}")
            is PIRUnaryOp -> listOf("${val_(inst.target)} = ${unaryOp(inst.op)}${val_(inst.operand)}")
            is PIRCompare -> listOf("${val_(inst.target)} = ${val_(inst.left)} ${cmpOp(inst.op)} ${val_(inst.right)}")
            is PIRCall -> {
                val argsStr = inst.args.joinToString(", ") { callArg(it) }
                val call = "${val_(inst.callee)}($argsStr)"
                val t = inst.target
                if (t != null) listOf("${val_(t)} = $call")
                else listOf(call)
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
            is PIRLocal -> sanitize(v.name)
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
