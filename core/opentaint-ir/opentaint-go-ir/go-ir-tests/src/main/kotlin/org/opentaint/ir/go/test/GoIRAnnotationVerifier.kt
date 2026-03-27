package org.opentaint.ir.go.test

import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.type.GoIRBinaryOp
import org.opentaint.ir.go.type.GoIRChanType
import org.opentaint.ir.go.type.GoIRUnaryOp

/**
 * Verifies that `//@ ...` annotations in Go source files match the built GoIR program.
 *
 * For each annotation, finds the corresponding IR instruction at the annotated source line
 * and verifies that the instruction type and field values match.
 */
object GoIRAnnotationVerifier {

    data class VerificationResult(
        val passed: Int,
        val failures: List<VerificationFailure>,
    ) {
        val isOk: Boolean get() = failures.isEmpty()

        fun assertNoFailures() {
            if (failures.isNotEmpty()) {
                val msg = failures.joinToString("\n") { "  Line ${it.line}: ${it.message}" }
                throw AssertionError("Annotation verification failed with ${failures.size} failure(s):\n$msg")
            }
        }
    }

    data class VerificationFailure(
        val line: Int,
        val annotation: GoIRAnnotation,
        val message: String,
    )

    fun verify(prog: GoIRProgram, annotationFile: GoIRAnnotationFile): VerificationResult {
        val failures = mutableListOf<VerificationFailure>()
        var passed = 0

        for (ann in annotationFile.annotations) {
            try {
                verifyAnnotation(prog, ann, failures)
                passed++
            } catch (e: Exception) {
                failures.add(VerificationFailure(ann.line, ann, "Exception: ${e.message}"))
            }
        }

        return VerificationResult(passed, failures)
    }

    private fun verifyAnnotation(
        prog: GoIRProgram,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        when (ann.kind) {
            "inst" -> verifyInst(prog, ann, failures)
            "count" -> verifyCount(prog, ann, failures)
            "call" -> verifyCall(prog, ann, failures)
            "cfg" -> verifyCfg(prog, ann, failures)
            "entity" -> verifyEntity(prog, ann, failures)
            else -> failures.add(VerificationFailure(ann.line, ann, "Unknown annotation kind: ${ann.kind}"))
        }
    }

    /**
     * Verify that the instruction at [ann.line] matches the expected type and fields.
     * We find the function, then find the instruction whose source position matches the annotated line.
     */
    private fun verifyInst(
        prog: GoIRProgram,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        val fnName = ann.function
        if (fnName == null) {
            failures.add(VerificationFailure(ann.line, ann, "No //go:ir-test func=Name directive before annotation"))
            return
        }

        val fn = prog.findFunctionByName(fnName)
        if (fn == null) {
            failures.add(VerificationFailure(ann.line, ann, "Function '$fnName' not found"))
            return
        }

        val body = fn.body
        if (body == null) {
            failures.add(VerificationFailure(ann.line, ann, "Function '$fnName' has no body"))
            return
        }

        val expectedType = ann.instType ?: return

        // Find instructions at this line
        val instsAtLine = body.instructions.filter { inst ->
            inst.position?.line == ann.line
        }

        // Also check instructions matching by type regardless of line (fallback)
        val matchingInsts = instsAtLine.filter { inst ->
            instTypeName(inst) == expectedType
        }

        if (matchingInsts.isEmpty()) {
            // If no instructions match at this exact line, look nearby (+-1 line)
            val nearbyInsts = body.instructions.filter { inst ->
                inst.position != null &&
                    kotlin.math.abs(inst.position!!.line - ann.line) <= 1 &&
                    instTypeName(inst) == expectedType
            }
            if (nearbyInsts.isEmpty()) {
                val availableInsts = instsAtLine.map { instTypeName(it) }.distinct()
                failures.add(
                    VerificationFailure(
                        ann.line, ann,
                        "Expected $expectedType at line ${ann.line}, found: $availableInsts"
                    )
                )
                return
            }
            // Use nearby instructions
            verifyInstFields(nearbyInsts.first(), ann, failures)
            return
        }

        verifyInstFields(matchingInsts.first(), ann, failures)
    }

    private fun verifyInstFields(
        inst: GoIRInst,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        // Verify additional fields from annotation args
        for ((key, expected) in ann.args) {
            if (key == "instType") continue // already checked

            val actual = getInstField(inst, key)
            if (actual == null) {
                failures.add(
                    VerificationFailure(
                        ann.line, ann,
                        "Field '$key' not applicable to ${instTypeName(inst)}"
                    )
                )
            } else if (actual != expected) {
                failures.add(
                    VerificationFailure(
                        ann.line, ann,
                        "Field '$key': expected '$expected', got '$actual'"
                    )
                )
            }
        }
    }

    private fun verifyCount(
        prog: GoIRProgram,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        val fnName = ann.function
        if (fnName == null) {
            failures.add(VerificationFailure(ann.line, ann, "No function directive before count annotation"))
            return
        }

        val fn = prog.findFunctionByName(fnName)
        if (fn == null) {
            failures.add(VerificationFailure(ann.line, ann, "Function '$fnName' not found"))
            return
        }

        val instType = ann.args["instType"] ?: return
        val expectedCount = ann.args["count"]?.toIntOrNull() ?: return

        val body = fn.body ?: return
        val actualCount = body.instructions.count { instTypeName(it) == instType }

        if (actualCount != expectedCount) {
            failures.add(
                VerificationFailure(
                    ann.line, ann,
                    "Expected $expectedCount ${instType}s in '$fnName', found $actualCount"
                )
            )
        }
    }

    private fun verifyCall(
        prog: GoIRProgram,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        val fnName = ann.function ?: return
        val fn = prog.findFunctionByName(fnName) ?: run {
            failures.add(VerificationFailure(ann.line, ann, "Function '$fnName' not found"))
            return
        }
        val body = fn.body ?: return

        val callsAtLine = body.instructions.filter { inst ->
            inst.position?.line == ann.line && (inst is GoIRCall || inst is GoIRGo || inst is GoIRDefer)
        }

        if (callsAtLine.isEmpty()) {
            failures.add(VerificationFailure(ann.line, ann, "No call instruction at line ${ann.line}"))
            return
        }

        val targetName = ann.args["target"]
        val expectedMode = ann.args["mode"]

        for (call in callsAtLine) {
            val callInfo = when (call) {
                is GoIRCall -> call.call
                is GoIRGo -> call.call
                is GoIRDefer -> call.call
                else -> continue
            }

            if (expectedMode != null) {
                val actualMode = callInfo.mode.name
                if (actualMode != expectedMode) {
                    failures.add(
                        VerificationFailure(
                            ann.line, ann,
                            "Call mode: expected $expectedMode, got $actualMode"
                        )
                    )
                }
            }

            if (targetName != null) {
                val actualTarget = callInfo.function?.name ?: callInfo.methodName ?: "<unknown>"
                if (!actualTarget.contains(targetName)) {
                    failures.add(
                        VerificationFailure(
                            ann.line, ann,
                            "Call target: expected '$targetName', got '$actualTarget'"
                        )
                    )
                }
            }
        }
    }

    private fun verifyCfg(
        prog: GoIRProgram,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        val fnName = ann.function ?: return
        val fn = prog.findFunctionByName(fnName) ?: run {
            failures.add(VerificationFailure(ann.line, ann, "Function '$fnName' not found"))
            return
        }
        val body = fn.body ?: return

        val expectedBlocks = ann.args["blocks"]?.toIntOrNull()
        if (expectedBlocks != null && body.blocks.size != expectedBlocks) {
            failures.add(
                VerificationFailure(
                    ann.line, ann,
                    "Block count: expected $expectedBlocks, got ${body.blocks.size}"
                )
            )
        }
    }

    private fun verifyEntity(
        prog: GoIRProgram,
        ann: GoIRAnnotation,
        failures: MutableList<VerificationFailure>,
    ) {
        val entityKind = ann.args["kind"] ?: return
        val entityName = ann.args["name"] ?: return

        when (entityKind) {
            "Function" -> {
                if (prog.findFunctionByName(entityName) == null) {
                    failures.add(
                        VerificationFailure(ann.line, ann, "Function '$entityName' not found")
                    )
                }
            }
            "Type" -> {
                val found = prog.allNamedTypes().any { it.name == entityName }
                if (!found) {
                    failures.add(
                        VerificationFailure(ann.line, ann, "Named type '$entityName' not found")
                    )
                }
            }
            else -> {
                failures.add(
                    VerificationFailure(ann.line, ann, "Unknown entity kind '$entityKind'")
                )
            }
        }
    }

    // ─── Helpers ───

    /**
     * Returns a logical name for the instruction (or the wrapped expression) for annotation matching.
     * For GoIRAssignInst, we return the expression class name (e.g., "GoIRBinOpExpr" -> "GoIRBinOp").
     * This maintains backward compatibility with existing annotations that use "GoIRBinOp", etc.
     */
    private fun instTypeName(inst: GoIRInst): String {
        if (inst is GoIRAssignInst) {
            // Map expr class names back to old instruction names for annotation compatibility
            val exprName = inst.expr::class.simpleName ?: "Unknown"
            return exprName.removeSuffix("Expr").let { "GoIR$it".removePrefix("GoIRGoIR") }
                .let { if (it.startsWith("GoIR")) it else "GoIR$it" }
        }
        return inst::class.simpleName ?: "Unknown"
    }

    /**
     * Get a field value from an instruction by name. Returns null if not applicable.
     */
    private fun getInstField(inst: GoIRInst, field: String): String? {
        // For assign instructions, delegate to the expression
        if (inst is GoIRAssignInst) {
            return getExprField(inst.expr, field)
        }
        return when (field) {
            "mode" -> when (inst) {
                is GoIRCall -> inst.call.mode.name
                is GoIRGo -> inst.call.mode.name
                is GoIRDefer -> inst.call.mode.name
                else -> null
            }
            else -> null
        }
    }

    private fun getExprField(expr: GoIRExpr, field: String): String? = when (field) {
        "op" -> when (expr) {
            is GoIRBinOpExpr -> expr.op.name
            is GoIRUnOpExpr -> expr.op.name
            else -> null
        }
        "commaOk" -> when (expr) {
            is GoIRTypeAssertExpr -> expr.commaOk.toString()
            is GoIRUnOpExpr -> expr.commaOk.toString()
            is GoIRLookupExpr -> expr.commaOk.toString()
            else -> null
        }
        "isHeap" -> when (expr) {
            is GoIRAllocExpr -> expr.isHeap.toString()
            else -> null
        }
        "fieldName" -> when (expr) {
            is GoIRFieldAddrExpr -> expr.fieldName
            is GoIRFieldExpr -> expr.fieldName
            else -> null
        }
        "fieldIndex" -> when (expr) {
            is GoIRFieldAddrExpr -> expr.fieldIndex.toString()
            is GoIRFieldExpr -> expr.fieldIndex.toString()
            else -> null
        }
        "extractIndex" -> when (expr) {
            is GoIRExtractExpr -> expr.extractIndex.toString()
            else -> null
        }
        "bindings" -> when (expr) {
            is GoIRMakeClosureExpr -> expr.bindings.size.toString()
            else -> null
        }
        "isBlocking" -> when (expr) {
            is GoIRSelectExpr -> expr.isBlocking.toString()
            else -> null
        }
        "assertedType" -> when (expr) {
            is GoIRTypeAssertExpr -> expr.assertedType.displayName
            else -> null
        }
        "direction" -> when (expr) {
            is GoIRMakeChanExpr -> {
                // The register type (not expr) holds the channel type info
                // This is trickier — we'd need access to the register's type
                // For now, return null (not commonly tested)
                null
            }
            else -> null
        }
        "isString" -> when (expr) {
            is GoIRNextExpr -> expr.isString.toString()
            else -> null
        }
        else -> null
    }
}
