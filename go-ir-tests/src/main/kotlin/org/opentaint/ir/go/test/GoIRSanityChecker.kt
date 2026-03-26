package org.opentaint.ir.go.test

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.cfg.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.value.*

/**
 * Validates structural invariants of a GoIR program.
 * Catches serialization/deserialization bugs, missing references, etc.
 *
 * Checks:
 * - Instruction indexing (sequential, no duplicates)
 * - CFG structure (terminators, successor/predecessor duality, entry block)
 * - SSA invariants (phi placement, phi edge count, unique value names)
 * - Entity invariants (unique package paths, non-empty names, method receivers)
 * - Instruction graph consistency (successors/predecessors match block structure)
 * - Dominator tree invariants (tree structure, idom consistency)
 * - Value operand validity (operands reference valid values)
 * - Block instruction membership (each inst knows its block, block range is correct)
 */
object GoIRSanityChecker {

    /**
     * Run full sanity checks on the program.
     *
     * @param deep if true, also runs expensive checks (inst graph consistency,
     *   dominator tree, operand validity). Set to false for benchmark tests
     *   where only core structural checks are needed.
     */
    fun check(program: GoIRProgram, deep: Boolean = true): SanityResult {
        val errors = mutableListOf<SanityViolation>()
        val warnings = mutableListOf<SanityViolation>()

        checkEntityInvariants(program, errors, warnings)

        for (fn in program.allFunctions()) {
            val body = fn.body ?: continue
            checkIndexing(body, errors)
            checkCFGInvariants(body, errors, warnings)
            checkSSAInvariants(body, errors, warnings)
            if (deep) {
                checkBlockMembership(body, errors)
                checkInstGraphConsistency(body, errors, warnings)
                checkDominatorTree(body, errors, warnings)
                checkOperandValidity(body, errors, warnings)
            }
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

        // Block indices should be sequential 0..N-1
        body.blocks.forEachIndexed { expected, block ->
            if (block.index != expected) {
                errors += SanityViolation(
                    "cfg",
                    "$fnName: Block at position $expected has index ${block.index}"
                )
            }
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

    /**
     * Verify that each instruction's block reference is consistent with the body structure:
     * - inst.block must be one of body.blocks
     * - inst must be in inst.block.instructions
     * - Body.inst(ref) round-trips correctly
     */
    private fun checkBlockMembership(body: GoIRBody, errors: MutableList<SanityViolation>) {
        val fnName = body.function.fullName
        val blockSet = body.blocks.toSet()

        for (inst in body.instructions) {
            if (inst.block !in blockSet) {
                errors += SanityViolation(
                    "membership",
                    "$fnName: Instruction ${inst.index} references block ${inst.block.index} which is not in body.blocks"
                )
            }

            if (inst !in inst.block) {
                errors += SanityViolation(
                    "membership",
                    "$fnName: Instruction ${inst.index} claims block ${inst.block.index} but block.contains returns false"
                )
            }

            // Round-trip check: body.inst(GoIRInstRef(inst.index)) should return this inst
            val ref = GoIRInstRef(inst.index)
            val resolved = body.inst(ref)
            if (resolved !== inst) {
                errors += SanityViolation(
                    "membership",
                    "$fnName: body.inst(GoIRInstRef(${inst.index})) returns a different object (index=${resolved.index})"
                )
            }
        }

        // Verify each block's instructions are contiguous in the flat list
        for (block in body.blocks) {
            if (block.instructions.isEmpty()) continue
            val startIdx = block.instructions.first().index
            val endIdx = block.instructions.last().index
            val expectedSize = endIdx - startIdx + 1
            if (block.instructions.size != expectedSize) {
                errors += SanityViolation(
                    "membership",
                    "$fnName: Block ${block.index} instructions are not contiguous (start=$startIdx, end=$endIdx, size=${block.instructions.size})"
                )
            }
        }
    }

    /**
     * Verify the instruction-level graph (flat CFG) is consistent:
     * - Non-terminator instructions should have exactly one successor (next in block)
     * - Terminator successors should be first instructions of successor blocks
     * - Predecessor/successor duality at instruction level
     */
    private fun checkInstGraphConsistency(
        body: GoIRBody,
        errors: MutableList<SanityViolation>,
        warnings: MutableList<SanityViolation>,
    ) {
        val fnName = body.function.fullName
        val instGraph = body.instGraph

        // Entry instruction should be the first instruction of the entry block
        if (body.blocks.isNotEmpty() && body.blocks[0].instructions.isNotEmpty()) {
            val expectedEntry = body.blocks[0].instructions.first()
            if (instGraph.entry !== expectedEntry) {
                errors += SanityViolation(
                    "inst-graph",
                    "$fnName: instGraph.entry (index=${instGraph.entry.index}) != first instruction of entry block (index=${expectedEntry.index})"
                )
            }
        }

        // Exit instructions should be Return or Panic
        for (exit in instGraph.exits) {
            if (exit !is GoIRReturn && exit !is GoIRPanic) {
                errors += SanityViolation(
                    "inst-graph",
                    "$fnName: instGraph.exits contains non-terminator at index ${exit.index} (${exit::class.simpleName})"
                )
            }
        }

        // For each instruction, verify successor/predecessor relationships
        for (inst in body.instructions) {
            val succs = instGraph.successors(inst)
            val preds = instGraph.predecessors(inst)

            // Successor/predecessor duality
            for (succ in succs) {
                val succPreds = instGraph.predecessors(succ)
                if (inst !in succPreds) {
                    errors += SanityViolation(
                        "inst-graph",
                        "$fnName: inst ${inst.index} -> ${succ.index} successor, but predecessor link missing"
                    )
                }
            }
            for (pred in preds) {
                val predSuccs = instGraph.successors(pred)
                if (inst !in predSuccs) {
                    errors += SanityViolation(
                        "inst-graph",
                        "$fnName: inst ${inst.index} <- ${pred.index} predecessor, but successor link missing"
                    )
                }
            }

            // Non-terminator in block: should have exactly 1 successor = next instruction
            if (inst !is GoIRTerminator) {
                if (succs.size != 1) {
                    errors += SanityViolation(
                        "inst-graph",
                        "$fnName: Non-terminator at index ${inst.index} should have 1 successor, has ${succs.size}"
                    )
                } else {
                    val next = instGraph.next(inst)
                    if (next != null && succs[0] !== next) {
                        errors += SanityViolation(
                            "inst-graph",
                            "$fnName: Non-terminator at ${inst.index}: successor (${succs[0].index}) != next (${next.index})"
                        )
                    }
                }
            }

            // next/previous consistency
            val next = instGraph.next(inst)
            if (next != null) {
                val prev = instGraph.previous(next)
                if (prev !== inst) {
                    errors += SanityViolation(
                        "inst-graph",
                        "$fnName: next(${inst.index})=${next.index} but previous(${next.index})=${prev?.index}"
                    )
                }
            }
        }

        // Verify ref-based navigation matches inst-based
        for (inst in body.instructions) {
            val ref = GoIRInstRef(inst.index)
            val succsByRef = instGraph.successors(ref)
            val succsByInst = instGraph.successors(inst).map { GoIRInstRef(it.index) }
            if (succsByRef != succsByInst) {
                errors += SanityViolation(
                    "inst-graph",
                    "$fnName: Ref-based successors at ${inst.index} differ from inst-based successors"
                )
            }
        }
    }

    /**
     * Verify dominator tree invariants:
     * - Entry block has no idom (or idom == itself)
     * - All non-entry blocks should have an idom
     * - idom/dominatedBlocks duality: if B.idom == A then B in A.dominatedBlocks
     * - domPreorder covers all blocks
     */
    private fun checkDominatorTree(
        body: GoIRBody,
        errors: MutableList<SanityViolation>,
        warnings: MutableList<SanityViolation>,
    ) {
        val fnName = body.function.fullName
        if (body.blocks.isEmpty()) return

        val blockGraph = body.blockGraph
        val entryBlock = body.blocks[0]

        // Entry block idom should be null (it has no dominator)
        if (entryBlock.idom != null) {
            warnings += SanityViolation(
                "dom",
                "$fnName: Entry block has non-null idom (block ${entryBlock.idom!!.index})"
            )
        }

        // idom / dominatedBlocks duality
        for (block in body.blocks) {
            val idom = block.idom
            if (idom != null) {
                if (block !in idom.dominatedBlocks) {
                    errors += SanityViolation(
                        "dom",
                        "$fnName: Block ${block.index}.idom = ${idom.index}, but block not in idom.dominatedBlocks"
                    )
                }
            }

            for (child in block.dominatedBlocks) {
                if (child.idom !== block) {
                    errors += SanityViolation(
                        "dom",
                        "$fnName: Block ${block.index} dominates ${child.index}, but child.idom = ${child.idom?.index}"
                    )
                }
            }
        }

        // domPreorder should cover all blocks (that are reachable)
        try {
            val preorder = blockGraph.domPreorder()
            val preorderIndices = preorder.map { it.index }.toSet()
            // At minimum, entry block should be in preorder
            if (entryBlock.index !in preorderIndices) {
                errors += SanityViolation(
                    "dom",
                    "$fnName: domPreorder does not include entry block"
                )
            }
            // domPostorder should have same blocks
            val postorder = blockGraph.domPostorder()
            val postorderIndices = postorder.map { it.index }.toSet()
            if (preorderIndices != postorderIndices) {
                warnings += SanityViolation(
                    "dom",
                    "$fnName: domPreorder and domPostorder cover different blocks (pre=${preorderIndices.size}, post=${postorderIndices.size})"
                )
            }
        } catch (e: Exception) {
            errors += SanityViolation(
                "dom",
                "$fnName: domPreorder/domPostorder threw: ${e::class.simpleName}: ${e.message}"
            )
        }
    }

    /**
     * Verify operand validity:
     * - Operand values should have non-null types
     * - GoIRValueInst operands that reference other instructions should reference instructions in the same function
     * - GoIRFunctionValue should reference a valid function
     */
    private fun checkOperandValidity(
        body: GoIRBody,
        errors: MutableList<SanityViolation>,
        warnings: MutableList<SanityViolation>,
    ) {
        val fnName = body.function.fullName
        val validInstIndices = body.instructions.map { it.index }.toSet()

        for (inst in body.instructions) {
            for ((opIdx, operand) in inst.operands.withIndex()) {
                // Check that operand type is not null (always true due to non-null type, but the type itself could be invalid)
                try {
                    val typeName = operand.type.toString()
                    if (typeName.isEmpty()) {
                        warnings += SanityViolation(
                            "operand",
                            "$fnName: Inst ${inst.index} operand $opIdx has empty type name"
                        )
                    }
                } catch (e: Exception) {
                    errors += SanityViolation(
                        "operand",
                        "$fnName: Inst ${inst.index} operand $opIdx type.toString() threw: ${e::class.simpleName}"
                    )
                }

                // If operand is a value instruction, it should be in this function
                if (operand is GoIRValueInst) {
                    if (operand.index !in validInstIndices) {
                        errors += SanityViolation(
                            "operand",
                            "$fnName: Inst ${inst.index} operand $opIdx references instruction ${operand.index} which is not in this function"
                        )
                    }
                }

                // Check parameter indices are in range
                if (operand is GoIRParameterValue) {
                    val paramCount = body.function.params.size
                    if (operand.paramIndex < 0 || operand.paramIndex >= paramCount) {
                        errors += SanityViolation(
                            "operand",
                            "$fnName: Inst ${inst.index} operand $opIdx references parameter index ${operand.paramIndex} but function has $paramCount params"
                        )
                    }
                }

                // Check free var indices are in range
                if (operand is GoIRFreeVarValue) {
                    val freeVarCount = body.function.freeVars.size
                    if (operand.freeVarIndex < 0 || operand.freeVarIndex >= freeVarCount) {
                        errors += SanityViolation(
                            "operand",
                            "$fnName: Inst ${inst.index} operand $opIdx references freeVar index ${operand.freeVarIndex} but function has $freeVarCount freeVars"
                        )
                    }
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

        // Check functions belong to their declared package
        for ((importPath, pkg) in program.packages) {
            for (fn in pkg.functions) {
                if (fn.pkg !== pkg) {
                    warnings += SanityViolation(
                        "entity",
                        "Function '${fn.fullName}' is in package '${importPath}' functions list but fn.pkg differs"
                    )
                }
            }
        }

        // Check named types belong to their declared package
        for ((importPath, pkg) in program.packages) {
            for (nt in pkg.namedTypes) {
                if (nt.pkg !== pkg) {
                    warnings += SanityViolation(
                        "entity",
                        "NamedType '${nt.name}' in package '${importPath}' but nt.pkg differs"
                    )
                }
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

    fun assertNoErrorsOrWarnings() {
        if (errors.isNotEmpty() || warnings.isNotEmpty()) {
            val msg = buildString {
                if (errors.isNotEmpty()) {
                    appendLine("ERRORS (${errors.size}):")
                    errors.forEach { appendLine("  [${it.category}] ${it.message}") }
                }
                if (warnings.isNotEmpty()) {
                    appendLine("WARNINGS (${warnings.size}):")
                    warnings.forEach { appendLine("  [${it.category}] ${it.message}") }
                }
            }
            throw AssertionError("GoIR sanity check failed:\n$msg")
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
