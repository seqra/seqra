package org.opentaint.ir.go.test

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.inst.*

/**
 * Validates structural invariants of a GoIR program.
 * Catches serialization/deserialization bugs, missing references, etc.
 */
object GoIRSanityChecker {

    fun check(program: GoIRProgram): SanityResult {
        val errors = mutableListOf<SanityViolation>()
        val warnings = mutableListOf<SanityViolation>()

        checkEntityInvariants(program, errors, warnings)

        for (fn in program.allFunctions()) {
            val body = fn.body ?: continue
            checkIndexing(body, errors)
            checkCFGInvariants(body, errors, warnings)
            checkSSAInvariants(body, errors, warnings)
        }

        return SanityResult(errors, warnings)
    }

    private fun checkIndexing(body: GoIRBody, errors: MutableList<SanityViolation>) {
        // Verify instruction indices are sequential 0..N-1
        body.instructions.forEachIndexed { expected, inst ->
            if (inst.index != expected) {
                errors += SanityViolation(
                    "indexing",
                    "Function '${body.function.fullName}': Instruction at position $expected has index ${inst.index}"
                )
            }
        }

        // Verify no duplicate indices
        val indices = body.instructions.map { it.index }
        if (indices.distinct().size != indices.size) {
            errors += SanityViolation(
                "indexing",
                "Function '${body.function.fullName}': Duplicate instruction indices found"
            )
        }
    }

    private fun checkCFGInvariants(
        body: GoIRBody,
        errors: MutableList<SanityViolation>,
        warnings: MutableList<SanityViolation>,
    ) {
        val fnName = body.function.fullName

        // Every block must have at least one instruction
        for (block in body.blocks) {
            if (block.instructions.isEmpty()) {
                errors += SanityViolation("cfg", "$fnName: Block ${block.index} has no instructions")
                continue
            }

            // Last instruction must be a terminator
            val lastInst = block.instructions.last()
            if (lastInst !is GoIRTerminator) {
                errors += SanityViolation(
                    "cfg",
                    "$fnName: Block ${block.index} does not end with a terminator (ends with ${lastInst::class.simpleName})"
                )
            }

            // Non-last instructions must NOT be terminators
            for (inst in block.instructions.dropLast(1)) {
                if (inst is GoIRTerminator) {
                    errors += SanityViolation(
                        "cfg",
                        "$fnName: Block ${block.index} has non-final terminator at index ${inst.index}"
                    )
                }
            }

            // Check terminator successor count
            when (lastInst) {
                is GoIRJump -> {
                    if (block.successors.size != 1) {
                        errors += SanityViolation(
                            "cfg",
                            "$fnName: Block ${block.index} Jump should have 1 successor, has ${block.successors.size}"
                        )
                    }
                }
                is GoIRIf -> {
                    if (block.successors.size != 2) {
                        errors += SanityViolation(
                            "cfg",
                            "$fnName: Block ${block.index} If should have 2 successors, has ${block.successors.size}"
                        )
                    }
                }
                is GoIRReturn, is GoIRPanic -> {
                    if (block.successors.isNotEmpty()) {
                        errors += SanityViolation(
                            "cfg",
                            "$fnName: Block ${block.index} ${lastInst::class.simpleName} should have 0 successors, has ${block.successors.size}"
                        )
                    }
                }
                else -> {} // other terminators
            }
        }

        // Successor/predecessor duality
        for (block in body.blocks) {
            for (succ in block.successors) {
                if (block !in succ.predecessors) {
                    errors += SanityViolation(
                        "cfg",
                        "$fnName: Block ${block.index} -> ${succ.index} successor, but predecessor missing"
                    )
                }
            }
            for (pred in block.predecessors) {
                if (block !in pred.successors) {
                    errors += SanityViolation(
                        "cfg",
                        "$fnName: Block ${block.index} <- ${pred.index} predecessor, but successor missing"
                    )
                }
            }
        }

        // Entry block (block 0) should have no predecessors
        if (body.blocks.isNotEmpty() && body.blocks[0].predecessors.isNotEmpty()) {
            warnings += SanityViolation(
                "cfg",
                "$fnName: Entry block has predecessors (unusual)"
            )
        }
    }

    private fun checkSSAInvariants(
        body: GoIRBody,
        errors: MutableList<SanityViolation>,
        warnings: MutableList<SanityViolation>,
    ) {
        val fnName = body.function.fullName

        for (block in body.blocks) {
            var seenNonPhi = false
            for (inst in block.instructions) {
                if (inst is GoIRPhi) {
                    if (seenNonPhi) {
                        errors += SanityViolation(
                            "ssa",
                            "$fnName: Block ${block.index} has Phi after non-Phi at index ${inst.index}"
                        )
                    }
                } else {
                    seenNonPhi = true
                }
            }

            // Phi edge count must equal predecessor count
            for (phi in block.phis) {
                if (phi.edges.size != block.predecessors.size) {
                    errors += SanityViolation(
                        "ssa",
                        "$fnName: Block ${block.index} Phi '${phi.name}' has ${phi.edges.size} edges but ${block.predecessors.size} predecessors"
                    )
                }
            }
        }

        // Check unique value names within a function (SSA property)
        val valueNames = mutableMapOf<String, Int>()
        for (inst in body.instructions) {
            if (inst is GoIRValueInst && inst.name.isNotEmpty()) {
                val prev = valueNames.put(inst.name, inst.index)
                if (prev != null) {
                    warnings += SanityViolation(
                        "ssa",
                        "$fnName: Duplicate value name '${inst.name}' at indices $prev and ${inst.index}"
                    )
                }
            }
        }
    }

    private fun checkEntityInvariants(
        program: GoIRProgram,
        errors: MutableList<SanityViolation>,
        warnings: MutableList<SanityViolation>,
    ) {
        // Check unique import paths
        val importPaths = program.packages.keys.toList()
        if (importPaths.distinct().size != importPaths.size) {
            errors += SanityViolation("entity", "Duplicate package import paths found")
        }

        // Check that packages have names
        for (pkg in program.packages.values) {
            if (pkg.name.isEmpty()) {
                errors += SanityViolation("entity", "Package '${pkg.importPath}' has empty name")
            }
        }

        // Check that methods have receiver types
        for (fn in program.allFunctions()) {
            if (fn.isMethod && fn.receiverType == null) {
                warnings += SanityViolation(
                    "entity",
                    "Method '${fn.fullName}' has isMethod=true but receiverType=null"
                )
            }
        }
    }
}

data class SanityResult(
    val errors: List<SanityViolation>,
    val warnings: List<SanityViolation>,
) {
    val isOk: Boolean get() = errors.isEmpty()

    fun assertNoErrors() {
        if (errors.isNotEmpty()) {
            val msg = errors.joinToString("\n") { "  [${it.category}] ${it.message}" }
            throw AssertionError("GoIR sanity check failed with ${errors.size} error(s):\n$msg")
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        if (errors.isNotEmpty()) {
            sb.appendLine("ERRORS (${errors.size}):")
            errors.forEach { sb.appendLine("  [${it.category}] ${it.message}") }
        }
        if (warnings.isNotEmpty()) {
            sb.appendLine("WARNINGS (${warnings.size}):")
            warnings.forEach { sb.appendLine("  [${it.category}] ${it.message}") }
        }
        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("OK (no errors, no warnings)")
        }
        return sb.toString()
    }
}

data class SanityViolation(val category: String, val message: String)
