package org.opentaint.ir.impl.python.protoToFlat.cfg

import org.opentaint.ir.api.python.PIRPhysicalLocation
import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.protoToFlat.ModuleContext
import org.opentaint.ir.impl.python.protoToFlat.Scope

/**
 * Per-function CFG construction state. Owned exclusively by [buildFunctionCfg]
 * / [buildModuleInitCfg]; never reused across functions.
 *
 * State that used to be split between `CfgBuilder` / `ExpressionLowering` /
 * `ProtoToFlatBuilder` lives here behind a small API:
 *
 *   - Block & instruction emission ([newBlock], [activate], [emit], goto/branch/return helpers)
 *   - Loop targets and exception-handler stacks (real ArrayDeques, no manual save/restore)
 *   - Scope (locals + temporaries) and the enclosing function's qualified name
 *
 * [StatementLowering] and [ExpressionLowering] are extension files on this class
 * — they share state because they're co-building one CFG. There are no
 * back-references from the lowerings into a parent builder.
 */
internal class CfgSession(
    val module: ModuleContext,
    val scope: Scope = Scope(),
    val currentFunctionQualifiedName: String? = null,
    /**
     * Module-flat short name of the enclosing function (its
     * [org.opentaint.ir.impl.python.flat.FlatFunctionIR.name] field).
     * Used by nested-def lowering to compose the lifted child's name as
     * `"$enclosingName$$childSourceName"`. `null` for module-init.
     */
    val currentFunctionName: String? = null,
) {
    // ─── CFG state (private) ───────────────────────────────

    private val blocks = mutableListOf<FlatBlock>()
    private var currentInstructions = mutableListOf<FlatInst>()
    private var currentLabel = 0
    private var blockCounter = 0

    /**
     * Stack of enclosing exception-handler block lists. The top of stack is
     * the set of handler labels that catch exceptions raised from the
     * currently-active block.
     */
    private val exceptionHandlerStack = ArrayDeque<List<Int>>()

    /** Stack of break / continue jump targets for nested loops. */
    private val loopStack = ArrayDeque<LoopTargets>()

    private data class LoopTargets(val breakBlock: Int, val continueBlock: Int)

    // ─── Nonlocal / global declarations ────────────────────

    private val _nonlocalNames = mutableSetOf<String>()
    private val _globalNames = mutableSetOf<String>()

    /** Record `nonlocal` declarations encountered while lowering this scope. */
    fun recordNonlocal(names: Iterable<String>) {
        _nonlocalNames.addAll(names)
    }

    /** Record `global` declarations encountered while lowering this scope. */
    fun recordGlobal(names: Iterable<String>) {
        _globalNames.addAll(names)
    }

    val nonlocalNames: Set<String> get() = _nonlocalNames
    val globalNames: Set<String> get() = _globalNames

    // ─── Block management ──────────────────────────────────

    fun newBlock(): Int = ++blockCounter

    fun activate(label: Int) {
        finalizeCurrentBlock()
        currentLabel = label
        currentInstructions = mutableListOf()
    }

    private fun finalizeCurrentBlock() {
        if (currentInstructions.isNotEmpty() || currentLabel == 0) {
            blocks.add(
                FlatBlock(
                    label = currentLabel,
                    instructions = currentInstructions.toList(),
                    exceptionHandlers = exceptionHandlerStack.lastOrNull().orEmpty(),
                )
            )
        }
    }

    /**
     * Force-finalize the current block without starting a new one. The only
     * legitimate caller is [StatementLowering.visitTry], which needs to commit
     * the try-body block under the active exception-handler stack frame
     * *before* popping handlers. Callers MUST follow up with [activate] before
     * emitting again. `currentLabel` is intentionally left in place: with the
     * just-closed label, a forgotten [activate] with no further emits is a
     * no-op, whereas resetting `currentLabel = 0` would trip the entry-block
     * special case in [finalizeCurrentBlock] and emit a phantom empty block 0.
     */
    fun closeCurrentBlock() {
        finalizeCurrentBlock()
        currentInstructions = mutableListOf()
    }

    fun currentBlockTerminated(): Boolean {
        val last = currentInstructions.lastOrNull() ?: return false
        return last is FlatGoto || last is FlatBranch || last is FlatReturn ||
                last is FlatRaise || last is FlatUnreachable || last is FlatNextIter
    }

    // ─── Instruction emission ──────────────────────────────

    fun emit(inst: FlatInst) {
        currentInstructions.add(inst)
    }

    fun emitGoto(target: Int, location: PIRPhysicalLocation? = null) =
        emit(FlatGoto(target, location))

    fun emitBranch(
        condition: FlatValue,
        trueBlock: Int,
        falseBlock: Int,
        location: PIRPhysicalLocation? = null,
    ) = emit(FlatBranch(condition, trueBlock, falseBlock, location))

    fun emitReturn(value: FlatValue?, location: PIRPhysicalLocation? = null) =
        emit(FlatReturn(value, location))

    fun newTempValue(): FlatLocal = FlatLocal(scope.newTemp())

    // ─── Scoped stacks ─────────────────────────────────────

    inline fun <R> withExceptionHandlers(handlers: List<Int>, block: () -> R): R {
        pushExceptionHandlers(handlers)
        try {
            return block()
        } finally {
            popExceptionHandlers()
        }
    }

    fun pushExceptionHandlers(handlers: List<Int>) {
        exceptionHandlerStack.addLast(handlers)
    }

    fun popExceptionHandlers() {
        exceptionHandlerStack.removeLast()
    }

    inline fun <R> withLoopTargets(breakBlock: Int, continueBlock: Int, block: () -> R): R {
        pushLoopTargets(breakBlock, continueBlock)
        try {
            return block()
        } finally {
            popLoopTargets()
        }
    }

    fun pushLoopTargets(breakBlock: Int, continueBlock: Int) {
        loopStack.addLast(LoopTargets(breakBlock, continueBlock))
    }

    fun popLoopTargets() {
        loopStack.removeLast()
    }

    val breakTarget: Int? get() = loopStack.lastOrNull()?.breakBlock
    val continueTarget: Int? get() = loopStack.lastOrNull()?.continueBlock

    // ─── CFG finalization ──────────────────────────────────

    fun finalizeCfg(): FlatCFG {
        finalizeCurrentBlock()

        val exitLabels = blocks.mapNotNull { block ->
            val last = block.instructions.lastOrNull()
            if (last is FlatReturn || last is FlatRaise || last is FlatUnreachable) block.label else null
        }

        return FlatCFG(
            blocks = blocks.toList(),
            entryBlock = 0,
            exitBlocks = exitLabels,
        )
    }
}
