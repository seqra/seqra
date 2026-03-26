package org.opentaint.ir.go.codegen

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRPackage
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.type.*
import org.opentaint.ir.go.value.*

/**
 * Converts a GoIR program back to compilable Go source code.
 *
 * This is NOT a decompiler — it produces mechanically correct code with gotos,
 * labeled blocks, and explicit variable declarations. The generated code computes
 * the same results as the original but is not idiomatic Go.
 *
 * The generated code strategy:
 * - Each SSA register becomes a Go variable declaration
 * - Each basic block becomes a labeled section
 * - Phi nodes are eliminated via temporary variables at predecessor block ends
 * - Terminators become gotos (Jump), conditional gotos (If), or return
 */
class GoIRToGoCodeGenerator {

    private val imports = mutableSetOf<String>()

    /**
     * Generate Go source code for the main package in the program.
     * Only generates the "main" package (or first package if no main).
     */
    fun generate(program: GoIRProgram): String {
        imports.clear()
        val sb = StringBuilder()
        val pkg = program.mainPackage() ?: program.packages.values.first()

        // Collect all functions we need to generate
        val allFunctions = collectAllFunctions(pkg)

        // Collect imports by scanning for function/global references to external packages
        scanImportsFromFunctions(allFunctions, pkg)

        // Package declaration
        sb.appendLine("package ${pkg.name}")
        sb.appendLine()

        // Imports
        if (imports.isNotEmpty()) {
            sb.appendLine("import (")
            for (imp in imports.sorted()) {
                sb.appendLine("\t\"$imp\"")
            }
            sb.appendLine(")")
            sb.appendLine()
        }

        // Generate type declarations first
        for (namedType in pkg.namedTypes) {
            generateTypeDecl(sb, namedType)
        }

        // Generate global variable declarations (skip init guard and external stubs)
        for (global in pkg.globals) {
            if (global.name.startsWith("init$") || global.name.startsWith("ext_")) continue
            sb.appendLine("var ${global.name} ${TypeFormatter.format(global.type)}")
        }
        if (pkg.globals.any { !it.name.startsWith("init$") && !it.name.startsWith("ext_") }) sb.appendLine()

        // Generate functions (excluding anonymous, init, and external stubs)
        for (fn in allFunctions) {
            if (fn.parent != null) continue // skip anonymous functions (generated inline)
            if (!fn.hasBody) continue
            if (fn.name == "init") continue // skip init for round-trip
            if (fn.isSynthetic) continue // skip synthetic wrappers
            if (fn.pkg != null && fn.pkg != pkg) continue // skip functions from other packages
            generateFunction(sb, fn)
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun collectAllFunctions(pkg: GoIRPackage): List<GoIRFunction> {
        val result = mutableListOf<GoIRFunction>()
        result.addAll(pkg.functions)
        result.addAll(pkg.allMethods())
        // Collect anonymous functions recursively
        fun collectAnon(fn: GoIRFunction) {
            for (anon in fn.anonymousFunctions) {
                result.add(anon)
                collectAnon(anon)
            }
        }
        for (fn in pkg.functions + pkg.allMethods()) {
            collectAnon(fn)
        }
        return result
    }

    /**
     * Scan user functions for direct calls to external packages.
     * Only scans functions we'll actually generate, not init or synthetics.
     */
    private fun scanImportsFromFunctions(functions: List<GoIRFunction>, userPkg: GoIRPackage) {
        for (fn in functions) {
            // Only scan functions that will be generated
            if (fn.pkg != null && fn.pkg != userPkg) continue
            if (fn.name == "init") continue
            if (fn.isSynthetic) continue
            if (fn.parent != null) continue
            val body = fn.body ?: continue
            for (inst in body.instructions) {
                when (inst) {
                    is GoIRCall -> scanCallForImport(inst.call, userPkg)
                    is GoIRGo -> scanCallForImport(inst.call, userPkg)
                    is GoIRDefer -> scanCallForImport(inst.call, userPkg)
                    else -> {}
                }
                // Also scan for global references from other packages
                for (operand in inst.operands) {
                    if (operand is GoIRGlobalValue) {
                        val gPkg = operand.global.pkg
                        if (gPkg != userPkg && !gPkg.importPath.startsWith("internal/") &&
                            !gPkg.importPath.startsWith("test/") && gPkg.importPath.isNotEmpty()
                        ) {
                            imports.add(gPkg.importPath)
                        }
                    }
                }
            }
        }
    }

    private fun scanCallForImport(call: GoIRCallInfo, userPkg: GoIRPackage) {
        val fn = call.function
        if (fn is GoIRFunctionValue) {
            val fnPkg = fn.function.pkg
            if (fnPkg != null && fnPkg != userPkg && !fnPkg.importPath.startsWith("internal/") &&
                !fnPkg.importPath.startsWith("test/") && fnPkg.importPath.isNotEmpty()
            ) {
                imports.add(fnPkg.importPath)
            }
        }
    }

    private fun generateTypeDecl(sb: StringBuilder, namedType: GoIRNamedType) {
        when (namedType.kind) {
            GoIRNamedTypeKind.STRUCT -> {
                sb.appendLine("type ${namedType.name} struct {")
                for (field in namedType.fields) {
                    val tag = if (field.tag.isNotEmpty()) " `${field.tag}`" else ""
                    if (field.isEmbedded) {
                        sb.appendLine("\t${TypeFormatter.format(field.type)}$tag")
                    } else {
                        sb.appendLine("\t${field.name} ${TypeFormatter.format(field.type)}$tag")
                    }
                }
                sb.appendLine("}")
                sb.appendLine()
            }
            GoIRNamedTypeKind.INTERFACE -> {
                sb.appendLine("type ${namedType.name} interface {")
                for (m in namedType.interfaceMethods) {
                    sb.appendLine("\t${m.name}${TypeFormatter.formatFuncSignature(m.signature)}")
                }
                sb.appendLine("}")
                sb.appendLine()
            }
            GoIRNamedTypeKind.ALIAS, GoIRNamedTypeKind.OTHER -> {
                sb.appendLine("type ${namedType.name} = ${TypeFormatter.format(namedType.underlying)}")
                sb.appendLine()
            }
        }
    }

    /**
     * Pre-compute Extract mapping for multi-return calls.
     *
     * For each Call whose register has a GoIRTupleType, we find all Extract
     * instructions that reference that register and build:
     *   tupleRegName -> sortedMap(extractIndex -> extractRegName)
     *
     * This lets visitCall emit `x, y = f(a, b)` and visitExtract return null.
     */
    private fun buildExtractMap(body: GoIRBody): Map<String, Map<Int, String>> {
        val result = mutableMapOf<String, MutableMap<Int, String>>()
        for (inst in body.instructions) {
            if (inst is GoIRAssignInst && inst.expr is GoIRExtractExpr) {
                val extract = inst.expr as GoIRExtractExpr
                val tupleName = extract.tuple.name
                result.getOrPut(tupleName) { mutableMapOf() }[extract.extractIndex] = inst.register.name
            }
        }
        return result
    }

    private fun generateFunction(sb: StringBuilder, fn: GoIRFunction) {
        val body = fn.body ?: return
        val phiResult = PhiEliminator.eliminate(body)
        val extractMap = buildExtractMap(body)

        // Function signature
        val recv = if (fn.isMethod && fn.receiverType != null) {
            val recvType = fn.receiverType!!.name
            val recvParam = fn.params.firstOrNull()
            val recvName = recvParam?.name ?: "recv"
            if (fn.isPointerReceiver) "($recvName *$recvType) "
            else "($recvName $recvType) "
        } else ""

        val params = if (fn.isMethod && fn.params.isNotEmpty()) {
            fn.params.drop(1) // skip receiver
        } else {
            fn.params
        }

        val paramStr = params.mapIndexed { i, p ->
            val paramType = fn.signature.params.let { allParams ->
                val idx = if (fn.isMethod) i + 1 else i
                if (idx < allParams.size) allParams[idx] else p.type
            }
            if (fn.signature.isVariadic && i == params.lastIndex) {
                val sliceType = paramType as? GoIRSliceType
                if (sliceType != null) "${p.name} ...${TypeFormatter.format(sliceType.elem)}"
                else "${p.name} ${TypeFormatter.format(paramType)}"
            } else {
                "${p.name} ${TypeFormatter.format(paramType)}"
            }
        }.joinToString(", ")

        val resultTypes = fn.signature.results
        val resultStr = when (resultTypes.size) {
            0 -> ""
            1 -> " ${TypeFormatter.format(resultTypes[0])}"
            else -> " (${resultTypes.joinToString(", ") { TypeFormatter.format(it) }})"
        }

        sb.appendLine("func ${recv}${fn.name}($paramStr)$resultStr {")

        // Declare all SSA register variables at the top.
        // All declarations MUST be before any labels/gotos to avoid "goto jumps over declaration".
        val seenNames = mutableSetOf<String>()        // all register names (including skipped tuples)
        val actuallyDeclared = mutableSetOf<String>()  // names that got a `var` declaration
        for (inst in body.instructions) {
            if (inst is GoIRDefInst) {
                val reg = inst.register
                if (reg.name.isNotEmpty() && reg.name !in seenNames) {
                    seenNames.add(reg.name)
                    // Skip tuple types — they're handled via Extract
                    if (reg.type is GoIRTupleType) continue
                    sb.appendLine("\tvar ${reg.name} ${TypeFormatter.format(reg.type)}")
                    actuallyDeclared.add(reg.name)
                }
                // Also pre-declare _alloc_ locals for stack Alloc expressions
                if (inst is GoIRAssignInst && inst.expr is GoIRAllocExpr) {
                    val allocExpr = inst.expr as GoIRAllocExpr
                    if (!allocExpr.isHeap) {
                        val allocName = "_alloc_${reg.name}"
                        if (allocName !in seenNames) {
                            sb.appendLine("\tvar $allocName ${TypeFormatter.format(allocExpr.allocType)}")
                            seenNames.add(allocName)
                            actuallyDeclared.add(allocName)
                        }
                    }
                }
            }
        }

        // Declare phi temp variables
        for ((_, reads) in phiResult.blockPhiReads) {
            for ((varName, tempName) in reads) {
                val phi = body.instructions.filterIsInstance<GoIRPhi>().find { it.register.name == varName }
                if (phi != null) {
                    if (tempName !in seenNames) {
                        sb.appendLine("\tvar $tempName ${TypeFormatter.format(phi.register.type)}")
                        seenNames.add(tempName)
                        actuallyDeclared.add(tempName)
                    }
                    if (varName !in seenNames) {
                        sb.appendLine("\tvar $varName ${TypeFormatter.format(phi.register.type)}")
                        seenNames.add(varName)
                        actuallyDeclared.add(varName)
                    }
                }
            }
        }

        // Suppress "declared and not used" errors for SSA registers that may not be
        // referenced in the generated code (e.g. unused phi results, dead code paths).
        for (v in actuallyDeclared) {
            sb.appendLine("\t_ = $v")
        }

        if (actuallyDeclared.isNotEmpty()) sb.appendLine()

        // Generate blocks
        // Collect which blocks are goto targets (need labels)
        val gotoTargets = mutableSetOf<Int>()
        for (block in body.blocks) {
            for (succ in block.successors) {
                gotoTargets.add(succ.index)
            }
        }

        for (block in body.blocks) {
            // Only emit label if some block jumps to this one
            if (block.index in gotoTargets) {
                sb.appendLine("\tblock${block.index}:")
            }

            // Phi reads at block start
            val phiReads = phiResult.blockPhiReads[block.index]
            if (phiReads != null) {
                for ((varName, tempName) in phiReads) {
                    sb.appendLine("\t\t$varName = $tempName")
                }
            }

            // Instructions (skip phis — they're handled above)
            for (inst in block.instructions) {
                if (inst is GoIRPhi) continue

                // Before terminators, emit phi assignments for successor blocks
                if (inst is GoIRTerminator) {
                    val assignments = phiResult.predecessorAssignments[block.index]
                    if (assignments != null) {
                        for (a in assignments) {
                            sb.appendLine("\t\t${a.phiTempName} = ${valueRef(a.sourceValue)}")
                        }
                    }
                }

                val code = generateInstruction(inst, block, extractMap)
                if (code != null) {
                    for (line in code.lines()) {
                        sb.appendLine("\t\t$line")
                    }
                }
            }
        }

        sb.appendLine("}")
    }

    private fun generateInstruction(inst: GoIRInst, block: GoIRBasicBlock, extractMap: Map<String, Map<Int, String>>): String? {
        return inst.accept(object : GoIRInstVisitor<String?> {
            override fun visitAssign(inst: GoIRAssignInst): String? {
                val name = inst.register.name
                val regType = inst.register.type
                return inst.expr.accept(object : GoIRExprVisitor<String?> {
                    override fun visitAlloc(expr: GoIRAllocExpr): String {
                        return if (expr.isHeap) {
                            "$name = new(${TypeFormatter.format(expr.allocType)})"
                        } else {
                            "$name = &_alloc_$name"
                        }
                    }

                    override fun visitBinOp(expr: GoIRBinOpExpr): String {
                        val opStr = binaryOpStr(expr.op)
                        return "$name = ${valueRef(expr.x)} $opStr ${valueRef(expr.y)}"
                    }

                    override fun visitUnOp(expr: GoIRUnOpExpr): String {
                        return when (expr.op) {
                            GoIRUnaryOp.DEREF -> "$name = *${valueRef(expr.x)}"
                            GoIRUnaryOp.NEG -> "$name = -${valueRef(expr.x)}"
                            GoIRUnaryOp.ARROW -> {
                                if (expr.commaOk) {
                                    "$name, _ok_$name = <-${valueRef(expr.x)}"
                                } else {
                                    "$name = <-${valueRef(expr.x)}"
                                }
                            }
                            GoIRUnaryOp.NOT -> "$name = !${valueRef(expr.x)}"
                            GoIRUnaryOp.XOR -> "$name = ^${valueRef(expr.x)}"
                        }
                    }

                    override fun visitChangeType(expr: GoIRChangeTypeExpr): String {
                        return "$name = ${TypeFormatter.format(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitConvert(expr: GoIRConvertExpr): String {
                        return "$name = ${TypeFormatter.format(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitMultiConvert(expr: GoIRMultiConvertExpr): String {
                        return "$name = ${TypeFormatter.format(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitChangeInterface(expr: GoIRChangeInterfaceExpr): String {
                        return "$name = ${TypeFormatter.format(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitSliceToArrayPointer(expr: GoIRSliceToArrayPointerExpr): String {
                        return "$name = ${TypeFormatter.format(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitMakeInterface(expr: GoIRMakeInterfaceExpr): String {
                        return "$name = ${TypeFormatter.format(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitTypeAssert(expr: GoIRTypeAssertExpr): String {
                        return "$name = ${valueRef(expr.x)}.(${TypeFormatter.format(expr.assertedType)})"
                    }

                    override fun visitMakeClosure(expr: GoIRMakeClosureExpr): String {
                        val closureFn = expr.fn
                        if (closureFn.body == null) {
                            return "$name = nil // closure with no body"
                        }
                        return "$name = ${valueRef(GoIRFunctionValue(closureFn.signature as? GoIRFuncType ?: GoIRFuncType(emptyList(), emptyList(), false, null), closureFn.fullName, closureFn))}"
                    }

                    override fun visitMakeMap(expr: GoIRMakeMapExpr): String {
                        val reserve = expr.reserve
                        return if (reserve != null) {
                            "$name = make(${TypeFormatter.format(regType)}, ${valueRef(reserve)})"
                        } else {
                            "$name = make(${TypeFormatter.format(regType)})"
                        }
                    }

                    override fun visitMakeChan(expr: GoIRMakeChanExpr): String {
                        return "$name = make(${TypeFormatter.format(regType)}, ${valueRef(expr.size)})"
                    }

                    override fun visitMakeSlice(expr: GoIRMakeSliceExpr): String {
                        return "$name = make(${TypeFormatter.format(regType)}, ${valueRef(expr.len)}, ${valueRef(expr.cap)})"
                    }

                    override fun visitFieldAddr(expr: GoIRFieldAddrExpr): String {
                        return "$name = &${valueRef(expr.x)}.${expr.fieldName}"
                    }

                    override fun visitField(expr: GoIRFieldExpr): String {
                        return "$name = ${valueRef(expr.x)}.${expr.fieldName}"
                    }

                    override fun visitIndexAddr(expr: GoIRIndexAddrExpr): String {
                        return "$name = &${valueRef(expr.x)}[${valueRef(expr.indexValue)}]"
                    }

                    override fun visitIndex(expr: GoIRIndexExpr): String {
                        return "$name = ${valueRef(expr.x)}[${valueRef(expr.indexValue)}]"
                    }

                    override fun visitSlice(expr: GoIRSliceExpr): String {
                        val lo = expr.low?.let { valueRef(it) } ?: ""
                        val hi = expr.high?.let { valueRef(it) } ?: ""
                        val max = expr.max?.let { ":${valueRef(it)}" } ?: ""
                        return "$name = ${valueRef(expr.x)}[$lo:$hi$max]"
                    }

                    override fun visitLookup(expr: GoIRLookupExpr): String {
                        return "$name = ${valueRef(expr.x)}[${valueRef(expr.indexValue)}]"
                    }

                    override fun visitRange(expr: GoIRRangeExpr): String {
                        return "$name = ${valueRef(expr.x)} // range"
                    }

                    override fun visitNext(expr: GoIRNextExpr): String {
                        return "$name = ${valueRef(expr.iter)} // next"
                    }

                    override fun visitSelect(expr: GoIRSelectExpr): String {
                        return "// select not supported in codegen"
                    }

                    override fun visitExtract(expr: GoIRExtractExpr): String? {
                        return null
                    }
                })
            }

            override fun visitPhi(inst: GoIRPhi): String? = null // handled by PhiEliminator

            override fun visitCall(inst: GoIRCall): String {
                // If the result is a tuple (multi-return), use extract targets as LHS
                if (inst.register.type is GoIRTupleType) {
                    val tupleType = inst.register.type as GoIRTupleType
                    val extracts = extractMap[inst.register.name]
                    val lhs = tupleType.elements.indices.joinToString(", ") { idx ->
                        extracts?.get(idx) ?: "_"
                    }
                    return "$lhs = ${generateCallStr(inst.call)}"
                }
                return generateCallExpr(inst.register.name, inst.call)
            }

            // ─── Terminators ───
            override fun visitJump(inst: GoIRJump): String {
                val target = block.successors[0].index
                return "goto block$target"
            }

            override fun visitIf(inst: GoIRIf): String {
                val tBlock = block.successors[0].index
                val fBlock = block.successors[1].index
                return "if ${valueRef(inst.cond)} {\n\tgoto block$tBlock\n} else {\n\tgoto block$fBlock\n}"
            }

            override fun visitReturn(inst: GoIRReturn): String {
                return if (inst.results.isEmpty()) "return"
                else "return ${inst.results.joinToString(", ") { valueRef(it) }}"
            }

            override fun visitPanic(inst: GoIRPanic): String {
                return "panic(${valueRef(inst.x)})"
            }

            // ─── Effect-only ───
            override fun visitStore(inst: GoIRStore): String {
                return "*${valueRef(inst.addr)} = ${valueRef(inst.value)}"
            }

            override fun visitMapUpdate(inst: GoIRMapUpdate): String {
                return "${valueRef(inst.map)}[${valueRef(inst.key)}] = ${valueRef(inst.value)}"
            }

            override fun visitSend(inst: GoIRSend): String {
                return "${valueRef(inst.chan)} <- ${valueRef(inst.x)}"
            }

            override fun visitGo(inst: GoIRGo): String {
                return "go ${generateCallStr(inst.call)}"
            }

            override fun visitDefer(inst: GoIRDefer): String {
                return "defer ${generateCallStr(inst.call)}"
            }

            override fun visitRunDefers(inst: GoIRRunDefers): String? = null // implicit in Go

            override fun visitDebugRef(inst: GoIRDebugRef): String? = null // not emitted
        })
    }

    private fun generateCallExpr(resultName: String, call: GoIRCallInfo): String {
        val callStr = generateCallStr(call)
        return if (resultName.isNotEmpty()) "$resultName = $callStr"
        else callStr
    }

    private fun generateCallStr(call: GoIRCallInfo): String {
        // Check if this is a variadic call where we need to spread the last arg
        val isVariadic = when {
            call.function is GoIRFunctionValue ->
                (call.function as GoIRFunctionValue).function.signature.isVariadic
            else -> false
        }

        val argStrs = call.args.mapIndexed { i, arg ->
            if (isVariadic && i == call.args.lastIndex && arg is GoIRRegister) {
                // Last arg to a variadic function — spread it
                "${valueRef(arg)}..."
            } else {
                valueRef(arg)
            }
        }
        val args = argStrs.joinToString(", ")

        return when (call.mode) {
            GoIRCallMode.DIRECT -> {
                val fn = call.function!!
                "${valueRef(fn)}($args)"
            }
            GoIRCallMode.DYNAMIC -> {
                val fn = call.function!!
                "${valueRef(fn)}($args)"
            }
            GoIRCallMode.INVOKE -> {
                val recv = call.receiver!!
                "${valueRef(recv)}.${call.methodName}($args)"
            }
        }
    }

    /**
     * Returns the Go expression that references the given SSA value.
     */
    private fun valueRef(value: GoIRValue): String = when (value) {
        is GoIRConstValue -> constValueStr(value)
        is GoIRParameterValue -> value.name
        is GoIRFreeVarValue -> value.name
        is GoIRGlobalValue -> value.name
        is GoIRFunctionValue -> functionRef(value)
        is GoIRBuiltinValue -> value.name
        is GoIRRegister -> value.name
        else -> value.name // Forward refs and other delegates resolve via name
    }

    private fun constValueStr(value: GoIRConstValue): String = when (val cv = value.value) {
        is GoIRConstantValue.IntConst -> cv.value.toString()
        is GoIRConstantValue.FloatConst -> {
            val s = cv.value.toString()
            if ('.' in s || 'e' in s || 'E' in s) s else "$s.0"
        }
        is GoIRConstantValue.ComplexConst -> "complex(${cv.real}, ${cv.imag})"
        is GoIRConstantValue.StringConst -> "\"${escapeGoString(cv.value)}\""
        is GoIRConstantValue.BoolConst -> cv.value.toString()
        is GoIRConstantValue.NilConst -> "nil"
    }

    private fun functionRef(value: GoIRFunctionValue): String {
        val fn = value.function
        val pkg = fn.pkg
        // If it's from a different package, use qualified name
        if (pkg != null && pkg.name != "main" && !pkg.importPath.startsWith("test/")) {
            return "${pkg.name}.${fn.name}"
        }
        return fn.name
    }

    private fun escapeGoString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun binaryOpStr(op: GoIRBinaryOp): String = when (op) {
        GoIRBinaryOp.ADD -> "+"
        GoIRBinaryOp.SUB -> "-"
        GoIRBinaryOp.MUL -> "*"
        GoIRBinaryOp.DIV -> "/"
        GoIRBinaryOp.REM -> "%"
        GoIRBinaryOp.AND -> "&"
        GoIRBinaryOp.OR -> "|"
        GoIRBinaryOp.XOR -> "^"
        GoIRBinaryOp.SHL -> "<<"
        GoIRBinaryOp.SHR -> ">>"
        GoIRBinaryOp.AND_NOT -> "&^"
        GoIRBinaryOp.EQ -> "=="
        GoIRBinaryOp.NEQ -> "!="
        GoIRBinaryOp.LT -> "<"
        GoIRBinaryOp.LEQ -> "<="
        GoIRBinaryOp.GT -> ">"
        GoIRBinaryOp.GEQ -> ">="
    }
}
