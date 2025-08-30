package org.opentaint.machine.interpreter.transformers

import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class JIRSingleInstructionTransformer(originalInstructions: JIRInstList<JIRInst>) {
    val mutableInstructions = originalInstructions.instructions.toMutableList()
    private val maxLocalVarIndex = mutableInstructions.maxOfOrNull { LocalVarMaxIndexFinder.find(it.operands) } ?: -1

    var generatedLocalVarIndex = maxLocalVarIndex + 1
    val modifiedLocationIndices = hashMapOf<Int, List<Int>>()

    inline fun generateReplacementBlock(original: JIRInst, blockGen: BlockGenerationContext.() -> Unit) {
        val originalLocation = original.location
        val ctx = BlockGenerationContext(originalLocation, generatedLocalVarIndex)

        ctx.blockGen()

        // add back jump from generated block
        ctx.addInstruction { loc ->
            JIRGotoInst(loc, JIRInstRef(originalLocation.index + 1))
        }

        // replace original instruction with jump to the generated block
        val replacementBlockStart = JIRInstRef(ctx.generatedLocations.first().index)
        mutableInstructions[originalLocation.index] = JIRGotoInst(originalLocation, replacementBlockStart)

        generatedLocalVarIndex = ctx.localVarIndex

        val generatedLocations = ctx.generatedLocations
        modifiedLocationIndices[originalLocation.index] = generatedLocations.map { it.index }
    }

    fun buildInstList(): JIRInstList<JIRInst> {
        fixCatchBlockThrowers()
        return JIRInstListImpl(mutableInstructions)
    }

    /**
     * Since we generate multiple instructions instead of a single one,
     * we must ensure that all catchers of the original instruction will
     * catch exceptions of generated instructions.
     * */
    private fun fixCatchBlockThrowers() {
        for (i in mutableInstructions.indices) {
            val instruction = mutableInstructions[i]
            if (instruction !is JIRCatchInst) continue

            val throwers = instruction.throwers.toMutableList()
            for (throwerIdx in throwers.indices) {
                val thrower = throwers[throwerIdx]
                val generatedLocations = modifiedLocationIndices[thrower.index] ?: continue
                generatedLocations.mapTo(throwers) { JIRInstRef(it) }
            }

            mutableInstructions[i] = with(instruction) {
                JIRCatchInst(location, throwable, throwableTypes, throwers)
            }
        }
    }

    inner class BlockGenerationContext(
        val originalLocation: JIRInstLocation,
        initialLocalVarIndex: Int,
    ) {
        var localVarIndex: Int = initialLocalVarIndex
        val generatedLocations = mutableListOf<JIRInstLocation>()

        fun nextLocalVar(name: String, type: JIRType) = JIRLocalVar(localVarIndex++, name, type)

        @OptIn(ExperimentalContracts::class)
        inline fun addInstruction(body: (JIRInstLocation) -> JIRInst) {
            contract {
                callsInPlace(body, InvocationKind.EXACTLY_ONCE)
            }

            mutableInstructions.addInstruction(originalLocation) { loc ->
                generatedLocations += loc
                body(loc)
            }
        }

        fun replaceInstructionAtLocation(loc: JIRInstLocation, replacement: (JIRInst) -> JIRInst) {
            val currentInst = mutableInstructions[loc.index]
            mutableInstructions[loc.index] = replacement(currentInst)
        }
    }

    inline fun MutableList<JIRInst>.addInstruction(origin: JIRInstLocation, body: (JIRInstLocation) -> JIRInst) {
        val index = size
        val newLocation = JIRInstLocationImpl(origin.method, index, origin.lineNumber)
        val instruction = body(newLocation)
        check(size == index)
        add(instruction)
    }

    private object LocalVarMaxIndexFinder : JIRExprVisitor.Default<Int> {
        override fun defaultVisitJIRExpr(expr: JIRExpr) = find(expr.operands)
        override fun visitJIRLocalVar(value: JIRLocalVar) = value.index
        fun find(expressions: Iterable<JIRExpr>): Int = expressions.maxOfOrNull { it.accept(this) } ?: -1
    }
}
