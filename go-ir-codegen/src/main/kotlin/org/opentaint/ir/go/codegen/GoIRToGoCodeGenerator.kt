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
     * Pre-compute Extract mapping for tuple-producing instructions.
     *
     * Covers: multi-return calls, comma-ok Lookup, comma-ok TypeAssert,
     * comma-ok channel receive (UnOp ARROW), Range+Next iteration, and Select.
     *
     * For each instruction whose register has a GoIRTupleType, we find all Extract
     * instructions that reference that register and build:
     *   tupleRegName -> sortedMap(extractIndex -> extractRegName)
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

    /**
     * Generate an anonymous function (closure) as an inline Go func literal.
     * The closure's freeVars are bound to the provided [bindings].
     *
     * Key challenge: SSA registers inside the closure body may have names (t0, t1, ...)
     * that collide with the outer scope names used in freeVar bindings. To avoid this,
     * we rename colliding closure-internal registers with a `_c` prefix.
     */
    private fun generateInlineClosure(fn: GoIRFunction, bindings: List<GoIRValue>): String {
        val body = fn.body ?: return "nil /* closure body unavailable */"
        val phiResult = PhiEliminator.eliminate(body)
        val extractMap = buildExtractMap(body)

        // Build a mapping from freeVar name to the binding expression from the outer scope
        val freeVarBindings = mutableMapOf<String, String>()
        val bindingTargetNames = mutableSetOf<String>() // outer names used in bindings
        for ((i, fv) in fn.freeVars.withIndex()) {
            if (i < bindings.size) {
                val outerRef = valueRef(bindings[i])
                freeVarBindings[fv.name] = outerRef
                bindingTargetNames.add(outerRef)
            }
        }

        // Collect all register names inside the closure body
        val closureRegNames = mutableSetOf<String>()
        for (inst in body.instructions) {
            if (inst is GoIRDefInst) closureRegNames.add(inst.register.name)
        }

        // Build rename map: closure registers whose names collide with binding targets
        // E.g. if outer binding has "x -> t0" and closure has register t0, rename it to _ct0
        val closureRenameMap = mutableMapOf<String, String>()
        for (regName in closureRegNames) {
            if (regName in bindingTargetNames) {
                var newName = "_c$regName"
                while (newName in closureRegNames || newName in bindingTargetNames || newName in closureRenameMap.values) {
                    newName = "_$newName"
                }
                closureRenameMap[regName] = newName
            }
        }

        // Build the param list (closures don't have receivers)
        val params = fn.params
        val paramStr = params.mapIndexed { i, p ->
            val paramType = fn.signature.params.getOrElse(i) { p.type }
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

        val csb = StringBuilder()
        csb.append("func($paramStr)$resultStr {\n")

        // Collect param names for this closure (includes free vars — they're substituted, not declared)
        val closureParamNames = params.map { it.name }.toMutableSet()
        closureParamNames.addAll(fn.freeVars.map { it.name })

        // Declare SSA registers (using renamed names where needed)
        val seenNames = mutableSetOf<String>()
        val actuallyDeclared = mutableSetOf<String>()
        for (inst in body.instructions) {
            if (inst is GoIRDefInst) {
                val reg = inst.register
                val regName = closureRenameMap[reg.name] ?: reg.name
                if (regName.isNotEmpty() && regName !in seenNames && reg.name !in closureParamNames) {
                    seenNames.add(regName)
                    if (reg.type is GoIRTupleType) continue
                    csb.appendLine("\t\tvar $regName ${TypeFormatter.format(reg.type)}")
                    actuallyDeclared.add(regName)
                }
                if (inst is GoIRAssignInst && inst.expr is GoIRAllocExpr) {
                    val allocExpr = inst.expr as GoIRAllocExpr
                    if (!allocExpr.isHeap) {
                        val allocName = "_alloc_$regName"
                        if (allocName !in seenNames) {
                            csb.appendLine("\t\tvar $allocName ${TypeFormatter.format(allocExpr.allocType)}")
                            seenNames.add(allocName)
                            actuallyDeclared.add(allocName)
                        }
                    }
                }
            }
        }
        for ((_, reads) in phiResult.blockPhiReads) {
            for ((varName, tempName) in reads) {
                val renamedVar = closureRenameMap[varName] ?: varName
                val renamedTemp = closureRenameMap[tempName] ?: tempName
                val phi = body.instructions.filterIsInstance<GoIRPhi>().find { it.register.name == varName }
                if (phi != null) {
                    if (renamedTemp !in seenNames) { csb.appendLine("\t\tvar $renamedTemp ${TypeFormatter.format(phi.register.type)}"); seenNames.add(renamedTemp); actuallyDeclared.add(renamedTemp) }
                    if (renamedVar !in seenNames && varName !in closureParamNames) { csb.appendLine("\t\tvar $renamedVar ${TypeFormatter.format(phi.register.type)}"); seenNames.add(renamedVar); actuallyDeclared.add(renamedVar) }
                }
            }
        }
        for (v in actuallyDeclared) { csb.appendLine("\t\t_ = $v") }
        if (actuallyDeclared.isNotEmpty()) csb.appendLine()

        // Generate blocks — use a local valueRef that substitutes freeVar references
        val gotoTargets = mutableSetOf<Int>()
        for (block in body.blocks) { for (succ in block.successors) { gotoTargets.add(succ.index) } }

        // Store both freeVar bindings and register rename map for the closure generation
        closureFreeVarBindings = freeVarBindings
        closureRegisterRenames = closureRenameMap

        for (block in body.blocks) {
            if (block.index in gotoTargets) csb.appendLine("\t\tblock${block.index}:")
            val phiReads = phiResult.blockPhiReads[block.index]
            if (phiReads != null) {
                for ((varName, tempName) in phiReads) {
                    val renamedVar = closureRenameMap[varName] ?: varName
                    val renamedTemp = closureRenameMap[tempName] ?: tempName
                    csb.appendLine("\t\t\t$renamedVar = $renamedTemp")
                }
            }
            for (inst in block.instructions) {
                if (inst is GoIRPhi) continue
                if (inst is GoIRTerminator) {
                    val assignments = phiResult.predecessorAssignments[block.index]
                    if (assignments != null) { for (a in assignments) {
                        val renamedPhi = closureRenameMap[a.phiTempName] ?: a.phiTempName
                        csb.appendLine("\t\t\t$renamedPhi = ${valueRef(a.sourceValue)}")
                    } }
                }
                val code = generateInstruction(inst, block, extractMap, body)
                if (code != null) { for (line in code.lines()) { csb.appendLine("\t\t\t$line") } }
            }
        }

        closureFreeVarBindings = null
        closureRegisterRenames = null
        csb.append("\t}")
        return csb.toString()
    }

    // When generating closure bodies, freeVar references are substituted
    private var closureFreeVarBindings: Map<String, String>? = null
    // When generating closure bodies, some register names are renamed to avoid collisions
    private var closureRegisterRenames: Map<String, String>? = null
    // Deferred call strings, collected during function code generation.
    // At RunDefers, they are emitted in LIFO order as direct calls (not as `defer`).
    private val deferredCalls = mutableListOf<String>()

    private fun generateFunction(sb: StringBuilder, fn: GoIRFunction) {
        val body = fn.body ?: return
        deferredCalls.clear()
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
        // Collect parameter names so we don't redeclare them.
        val paramNames = fn.params.map { it.name }.toMutableSet()
        paramNames.addAll(fn.freeVars.map { it.name })

        val seenNames = mutableSetOf<String>()        // all register names (including skipped tuples)
        val actuallyDeclared = mutableSetOf<String>()  // names that got a `var` declaration
        for (inst in body.instructions) {
            if (inst is GoIRDefInst) {
                val reg = inst.register
                if (reg.name.isNotEmpty() && reg.name !in seenNames) {
                    seenNames.add(reg.name)
                    // Skip tuple types — they're handled via Extract
                    if (reg.type is GoIRTupleType) continue
                    // Skip names that are already function parameters
                    if (reg.name in paramNames) continue
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
                    if (varName !in seenNames && varName !in paramNames) {
                        sb.appendLine("\tvar $varName ${TypeFormatter.format(phi.register.type)}")
                        seenNames.add(varName)
                        actuallyDeclared.add(varName)
                    }
                }
            }
        }

        // Declare range iteration helper variables (_rcoll_, _ridx_, _rkeys_ for map iteration)
        for (inst in body.instructions) {
            if (inst is GoIRAssignInst && inst.expr is GoIRRangeExpr) {
                val rangeExpr = inst.expr as GoIRRangeExpr
                val iterName = inst.register.name
                val collType = rangeExpr.x.type
                sb.appendLine("\tvar _rcoll_$iterName ${TypeFormatter.format(collType)}")
                actuallyDeclared.add("_rcoll_$iterName")
                sb.appendLine("\tvar _ridx_$iterName int")
                actuallyDeclared.add("_ridx_$iterName")
                if (collType is GoIRMapType) {
                    sb.appendLine("\tvar _rkeys_$iterName []${TypeFormatter.format(collType.key)}")
                    actuallyDeclared.add("_rkeys_$iterName")
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

                val code = generateInstruction(inst, block, extractMap, body)
                if (code != null) {
                    for (line in code.lines()) {
                        sb.appendLine("\t\t$line")
                    }
                }
            }
        }

        sb.appendLine("}")
    }

    /** Apply closure register rename if active. */
    private fun renameReg(name: String): String = closureRegisterRenames?.get(name) ?: name

    /**
     * Format a type for use in a type conversion expression: Type(value).
     * Wraps in parentheses when the type string starts with `<-` (receive-only channel)
     * to avoid Go's parsing ambiguity: `<-chan int(x)` is parsed as `<-(chan int(x))`.
     */
    private fun typeForConversion(type: GoIRType): String {
        val formatted = TypeFormatter.format(type)
        return if (formatted.startsWith("<-")) "($formatted)" else formatted
    }

    /**
     * Find the collection type for a Range iterator.
     * Given a value reference to the Range result register, find the Range instruction
     * and return the type of its collection operand.
     */
    private fun findRangeCollectionType(body: GoIRBody, iterValue: GoIRValue): GoIRType? {
        val iterName = iterValue.name
        for (inst in body.instructions) {
            if (inst is GoIRAssignInst && inst.register.name == iterName && inst.expr is GoIRRangeExpr) {
                return (inst.expr as GoIRRangeExpr).x.type
            }
        }
        return null
    }

    private fun generateInstruction(inst: GoIRInst, block: GoIRBasicBlock, extractMap: Map<String, Map<Int, String>>, body: GoIRBody? = null): String? {
        return inst.accept(object : GoIRInstVisitor<String?> {
            override fun visitAssign(inst: GoIRAssignInst): String? {
                val rawName = inst.register.name
                val name = renameReg(rawName)
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
                                if (expr.commaOk && regType is GoIRTupleType) {
                                    val extracts = extractMap[rawName]
                                    val valName = extracts?.get(0)?.let { renameReg(it) } ?: "_"
                                    val okName = extracts?.get(1)?.let { renameReg(it) } ?: "_"
                                    "$valName, $okName = <-${valueRef(expr.x)}"
                                } else {
                                    "$name = <-${valueRef(expr.x)}"
                                }
                            }
                            GoIRUnaryOp.NOT -> "$name = !${valueRef(expr.x)}"
                            GoIRUnaryOp.XOR -> "$name = ^${valueRef(expr.x)}"
                        }
                    }

                    override fun visitChangeType(expr: GoIRChangeTypeExpr): String {
                        return "$name = ${typeForConversion(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitConvert(expr: GoIRConvertExpr): String {
                        return "$name = ${typeForConversion(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitMultiConvert(expr: GoIRMultiConvertExpr): String {
                        return "$name = ${typeForConversion(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitChangeInterface(expr: GoIRChangeInterfaceExpr): String {
                        return "$name = ${typeForConversion(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitSliceToArrayPointer(expr: GoIRSliceToArrayPointerExpr): String {
                        return "$name = ${typeForConversion(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitMakeInterface(expr: GoIRMakeInterfaceExpr): String {
                        // MakeInterface wraps a concrete value into an interface.
                        // For zero-size structs, Go SSA may represent the value as nil.
                        // We need to create an actual struct zero-value to get a non-nil interface.
                        val inner = expr.x
                        val innerType = inner.type
                        if (inner is GoIRConstValue && inner.value is GoIRConstantValue.NilConst) {
                            // Nil concrete value — generate zero-value literal for the concrete type
                            val zeroVal = zeroValueLiteral(innerType)
                            if (zeroVal != null) {
                                return "$name = ${typeForConversion(regType)}($zeroVal)"
                            }
                        }
                        return "$name = ${typeForConversion(regType)}(${valueRef(expr.x)})"
                    }

                    override fun visitTypeAssert(expr: GoIRTypeAssertExpr): String {
                        if (expr.commaOk && regType is GoIRTupleType) {
                            val extracts = extractMap[rawName]
                            val valName = extracts?.get(0)?.let { renameReg(it) } ?: "_"
                            val okName = extracts?.get(1)?.let { renameReg(it) } ?: "_"
                            return "$valName, $okName = ${valueRef(expr.x)}.(${TypeFormatter.format(expr.assertedType)})"
                        }
                        return "$name = ${valueRef(expr.x)}.(${TypeFormatter.format(expr.assertedType)})"
                    }

                    override fun visitMakeClosure(expr: GoIRMakeClosureExpr): String {
                        val closureFn = expr.fn
                        if (closureFn.body == null) {
                            return "$name = nil // closure with no body"
                        }
                        val inlineSrc = generateInlineClosure(closureFn, expr.bindings)
                        return "$name = $inlineSrc"
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
                        if (expr.commaOk && regType is GoIRTupleType) {
                            val extracts = extractMap[rawName]
                            val valName = extracts?.get(0)?.let { renameReg(it) } ?: "_"
                            val okName = extracts?.get(1)?.let { renameReg(it) } ?: "_"
                            return "$valName, $okName = ${valueRef(expr.x)}[${valueRef(expr.indexValue)}]"
                        }
                        return "$name = ${valueRef(expr.x)}[${valueRef(expr.indexValue)}]"
                    }

                    override fun visitRange(expr: GoIRRangeExpr): String {
                        // Range creates an iterator over a collection.
                        // We store the collection in _rcoll_NAME and prepare iteration state.
                        val collType = expr.x.type
                        val collName = "_rcoll_$name"
                        val sb = StringBuilder()
                        when (collType) {
                            is GoIRMapType -> {
                                // For maps: store the map, collect keys, init index
                                sb.appendLine("$collName = ${valueRef(expr.x)}")
                                sb.appendLine("_rkeys_$name = make([]${TypeFormatter.format(collType.key)}, 0, len($collName))")
                                sb.appendLine("for _rk_ := range $collName { _rkeys_$name = append(_rkeys_$name, _rk_) }")
                                sb.append("_ridx_$name = 0")
                            }
                            is GoIRSliceType, is GoIRArrayType -> {
                                sb.appendLine("$collName = ${valueRef(expr.x)}")
                                sb.append("_ridx_$name = 0")
                            }
                            is GoIRBasicType -> {
                                // String range
                                sb.appendLine("$collName = ${valueRef(expr.x)}")
                                sb.append("_ridx_$name = 0")
                            }
                            else -> {
                                sb.append("$collName = ${valueRef(expr.x)} // range (unknown collection type)")
                            }
                        }
                        return sb.toString()
                    }

                    override fun visitNext(expr: GoIRNextExpr): String {
                        // Next advances the iterator, producing (ok, key, value) tuple.
                        // The extract map tells us which registers receive ok/key/value.
                        val iterName = valueRef(expr.iter)
                        val collName = "_rcoll_$iterName"
                        val extracts = extractMap[rawName]
                        val okName = extracts?.get(0)?.let { renameReg(it) } ?: "_rok_$name"
                        val keyName = extracts?.get(1)?.let { renameReg(it) }
                        val valName = extracts?.get(2)?.let { renameReg(it) }

                        // Determine collection type from the Range expr's collection
                        val rangeCollType = body?.let { findRangeCollectionType(it, expr.iter) }

                        val sb = StringBuilder()
                        when (rangeCollType) {
                            is GoIRMapType -> {
                                sb.appendLine("$okName = _ridx_$iterName < len(_rkeys_$iterName)")
                                sb.appendLine("if $okName {")
                                if (keyName != null) sb.appendLine("\t$keyName = _rkeys_$iterName[_ridx_$iterName]")
                                if (valName != null) {
                                    val kRef = keyName ?: "_rkeys_${iterName}[_ridx_$iterName]"
                                    sb.appendLine("\t$valName = $collName[$kRef]")
                                }
                                sb.appendLine("\t_ridx_${iterName}++")
                                sb.append("}")
                            }
                            is GoIRSliceType, is GoIRArrayType -> {
                                sb.appendLine("$okName = _ridx_$iterName < len($collName)")
                                sb.appendLine("if $okName {")
                                if (keyName != null) sb.appendLine("\t$keyName = _ridx_$iterName")
                                if (valName != null) sb.appendLine("\t$valName = $collName[_ridx_$iterName]")
                                sb.appendLine("\t_ridx_${iterName}++")
                                sb.append("}")
                            }
                            is GoIRBasicType -> {
                                // String range — iterate by runes
                                sb.appendLine("$okName = _ridx_$iterName < len($collName)")
                                sb.appendLine("if $okName {")
                                if (keyName != null) sb.appendLine("\t$keyName = _ridx_$iterName")
                                if (valName != null) sb.appendLine("\t$valName = int32($collName[_ridx_$iterName])")
                                sb.appendLine("\t_ridx_${iterName}++")
                                sb.append("}")
                            }
                            else -> {
                                sb.append("// next: unsupported range collection type")
                            }
                        }
                        return sb.toString()
                    }

                    override fun visitSelect(expr: GoIRSelectExpr): String {
                        // Select produces (index, recvOk, recv...) tuple.
                        // Generate a Go select statement.
                        val sb = StringBuilder()
                        val extracts = extractMap[rawName]
                        sb.append("// select (codegen: simplified)")
                        return sb.toString()
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
                    // Void function: tuple with 0 elements
                    if (tupleType.elements.isEmpty()) {
                        return generateCallStr(inst.call)
                    }
                    val extracts = extractMap[inst.register.name]
                    val lhs = tupleType.elements.indices.joinToString(", ") { idx ->
                        extracts?.get(idx)?.let { renameReg(it) } ?: "_"
                    }
                    return "$lhs = ${generateCallStr(inst.call)}"
                }
                return generateCallExpr(renameReg(inst.register.name), inst.call)
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

            override fun visitDefer(inst: GoIRDefer): String? {
                // Don't emit `defer` — collect the call for later LIFO execution at RunDefers
                deferredCalls.add(generateCallStr(inst.call))
                return null
            }

            override fun visitRunDefers(inst: GoIRRunDefers): String? {
                if (deferredCalls.isEmpty()) return null
                // Execute deferred calls in LIFO order (last defer first)
                val sb = StringBuilder()
                for (i in deferredCalls.indices.reversed()) {
                    sb.appendLine(deferredCalls[i])
                }
                return sb.toString().trimEnd()
            }

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
            // Builtin append is always variadic
            call.function is GoIRBuiltinValue && (call.function as GoIRBuiltinValue).name == "append" ->
                true
            else -> false
        }

        val argStrs = call.args.mapIndexed { i, arg ->
            if (isVariadic && i == call.args.lastIndex) {
                // Last arg to a variadic function — spread it if it's a slice
                val argType = arg.type
                if (argType is GoIRSliceType) {
                    "${valueRef(arg)}..."
                } else {
                    valueRef(arg)
                }
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
    private fun valueRef(value: GoIRValue): String {
        // When generating closure bodies, substitute free variable references with their bindings
        val bindings = closureFreeVarBindings
        if (bindings != null && value is GoIRFreeVarValue) {
            return bindings[value.name] ?: value.name
        }
        // When generating closure bodies, apply register renames to avoid collisions
        val renames = closureRegisterRenames
        val baseName = when (value) {
            is GoIRConstValue -> return constValueStr(value)
            is GoIRParameterValue -> value.name
            is GoIRFreeVarValue -> value.name
            is GoIRGlobalValue -> return value.name
            is GoIRFunctionValue -> return functionRef(value)
            is GoIRBuiltinValue -> return value.name
            is GoIRRegister -> value.name
            else -> value.name
        }
        // Apply closure register rename if active
        if (renames != null) {
            return renames[baseName] ?: baseName
        }
        return baseName
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

    /**
     * Generate a Go zero-value literal for a type.
     * Used when MakeInterface receives a nil const for zero-size structs.
     * Returns null if we can't generate a safe literal.
     */
    private fun zeroValueLiteral(type: GoIRType): String? {
        return when (type) {
            is GoIRStructType -> {
                val named = type.namedType
                if (named != null) "${named.name}{}" else "struct{}{}"
            }
            is GoIRNamedTypeRef -> {
                val named = type.namedType
                if (named != null) "${named.name}{}" else null
            }
            is GoIRPointerType -> "new(${TypeFormatter.format(type.elem)})"
            is GoIRBasicType -> when (type.kind) {
                GoIRBasicTypeKind.INT, GoIRBasicTypeKind.INT8, GoIRBasicTypeKind.INT16,
                GoIRBasicTypeKind.INT32, GoIRBasicTypeKind.INT64,
                GoIRBasicTypeKind.UINT, GoIRBasicTypeKind.UINT8, GoIRBasicTypeKind.UINT16,
                GoIRBasicTypeKind.UINT32, GoIRBasicTypeKind.UINT64 -> "0"
                GoIRBasicTypeKind.FLOAT32, GoIRBasicTypeKind.FLOAT64 -> "0.0"
                GoIRBasicTypeKind.BOOL -> "false"
                GoIRBasicTypeKind.STRING -> "\"\""
                else -> null
            }
            else -> null
        }
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
