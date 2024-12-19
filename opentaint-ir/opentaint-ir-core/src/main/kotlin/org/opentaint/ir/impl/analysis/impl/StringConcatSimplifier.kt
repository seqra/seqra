package org.opentaint.ir.impl.analysis.impl

import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.cfg.BsmStringArg
import org.opentaint.ir.api.cfg.DefaultJIRInstVisitor
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIRGotoInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstRef
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.cfg.JIRStringConstant
import org.opentaint.ir.api.cfg.JIRSwitchInst
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.ext.autoboxIfNeeded
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.impl.cfg.JIRGraphImpl
import kotlin.collections.set

class StringConcatSimplifier(val jIRGraph: JIRGraph) : DefaultJIRInstVisitor<JIRInst> {
    override val defaultInstHandler: (JIRInst) -> JIRInst
        get() = { it }
    private val instructionReplacements = mutableMapOf<JIRInst, JIRInst>()
    private val instructions = mutableListOf<JIRInst>()
    private val catchReplacements = mutableMapOf<JIRInst, MutableList<JIRInst>>()
    private val instructionIndices = mutableMapOf<JIRInst, Int>()

    private val stringType = jIRGraph.classpath.findTypeOrNull<String>() as JIRClassType

    fun build(): JIRGraph {
        var changed = false
        for (inst in jIRGraph) {
            if (inst is JIRAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv

                if (rhv is JIRDynamicCallExpr && rhv.callCiteMethodName == "makeConcatWithConstants") {

                    val (first, second) = when {
                        rhv.callCiteArgs.size == 2 -> rhv.callCiteArgs
                        rhv.callCiteArgs.size == 1 && rhv.bsmArgs.size == 1 && rhv.bsmArgs[0] is BsmStringArg -> listOf(
                            rhv.callCiteArgs[0],
                            JIRStringConstant((rhv.bsmArgs[0] as BsmStringArg).value, stringType)
                        )

                        else -> {
                            instructions += inst
                            continue
                        }
                    }
                    changed = true

                    val result = mutableListOf<JIRInst>()
                    val firstStr = stringify(inst, first, result)
                    val secondStr = stringify(inst, second, result)

                    val concatMethod = stringType.methods.first {
                        it.name == "concat" && it.parameters.size == 1 && it.parameters.first().type == stringType
                    }
                    val newConcatExpr = JIRVirtualCallExpr(concatMethod, firstStr, listOf(secondStr))
                    result += JIRAssignInst(inst.location, lhv, newConcatExpr)
                    instructionReplacements[inst] = result.first()
                    catchReplacements[inst] = result
                    instructions += result
                } else {
                    instructions += inst
                }
            } else {
                instructions += inst
            }
        }

        if (!changed) return jIRGraph

        /**
         * after we changed the instruction list, we need to examine new instruction list and
         * remap all the old JIRInstRef's to new ones
         */
        instructionIndices.putAll(instructions.indices.map { instructions[it] to it })
        val mappedInstructions = instructions.map { it.accept(this) }
        return JIRGraphImpl(jIRGraph.method, mappedInstructions)
    }

    private fun stringify(inst: JIRInst, value: JIRValue, instList: MutableList<JIRInst>): JIRValue {
        return when {
            PredefinedPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JIRClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JIRStaticCallExpr(method, listOf(value))
                val assignment = JIRLocalVar("${value}String", stringType)
                instList += JIRAssignInst(inst.location, assignment, toStringExpr)
                assignment
            }

            value.type == stringType -> value
            else -> {
                val boxedType = value.type.autoboxIfNeeded() as JIRClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.isEmpty()
                }
                val toStringExpr = JIRVirtualCallExpr(method, value, emptyList())
                val assignment = JIRLocalVar("${value}String", stringType)
                instList += JIRAssignInst(inst.location, assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JIRInstRef) = JIRInstRef(
        instructionIndices[instructionReplacements.getOrDefault(jIRGraph.inst(instRef), jIRGraph.inst(instRef))] ?: -1
    )

    private fun indicesOf(instRef: JIRInstRef) =
        catchReplacements.getOrDefault(jIRGraph.inst(instRef), listOf(jIRGraph.inst(instRef))).map {
            JIRInstRef(instructions.indexOf(it))
        }

    override fun visitJIRCatchInst(inst: JIRCatchInst): JIRInst = JIRCatchInst(
        inst.location,
        inst.throwable,
        inst.throwers.flatMap { indicesOf(it) }
    )

    override fun visitJIRGotoInst(inst: JIRGotoInst): JIRInst = JIRGotoInst(inst.location, indexOf(inst.target))

    override fun visitJIRIfInst(inst: JIRIfInst): JIRInst = JIRIfInst(
        inst.location,
        inst.condition,
        indexOf(inst.trueBranch),
        indexOf(inst.falseBranch)
    )

    override fun visitJIRSwitchInst(inst: JIRSwitchInst): JIRInst = JIRSwitchInst(
        inst.location,
        inst.key,
        inst.branches.mapValues { indexOf(it.value) },
        indexOf(inst.default)
    )
}
