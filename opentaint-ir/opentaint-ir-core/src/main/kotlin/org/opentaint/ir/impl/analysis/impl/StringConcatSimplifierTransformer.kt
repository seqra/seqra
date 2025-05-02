package org.opentaint.ir.impl.analysis.impl

import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.PredefinedJIRPrimitives
import org.opentaint.ir.api.core.cfg.InstList
import org.opentaint.ir.api.jvm.ext.autoboxIfNeeded
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.impl.cfg.InstListImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.cfg.methodRef
import org.opentaint.ir.api.jvm.cfg.BsmStringArg
import org.opentaint.ir.api.jvm.cfg.DefaultJIRInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRSwitchInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import kotlin.collections.set

class StringConcatSimplifierTransformer(classpath: JIRProject, private val list: InstList<JIRInst>) :
    DefaultJIRInstVisitor<JIRInst> {

    override val defaultInstHandler: (JIRInst) -> JIRInst
        get() = { it }

    private val instructionReplacements = mutableMapOf<JIRInst, JIRInst>()
    private val instructions = mutableListOf<JIRInst>()
    private val catchReplacements = mutableMapOf<JIRInst, MutableList<JIRInst>>()
    private val instructionIndices = mutableMapOf<JIRInst, Int>()

    private val stringType = classpath.findTypeOrNull<String>() as JIRClassType

    fun transform(): InstList<JIRInst> {
        var changed = false
        for (inst in list) {
            if (inst is JIRAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv

                if (rhv is JIRDynamicCallExpr && rhv.callSiteMethodName == "makeConcatWithConstants") {

                    val (first, second) = when {
                        rhv.callSiteArgs.size == 2 -> rhv.callSiteArgs
                        rhv.callSiteArgs.size == 1 && rhv.bsmArgs.size == 1 && rhv.bsmArgs[0] is BsmStringArg -> listOf(
                            rhv.callSiteArgs[0],
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

                    val concatMethod = stringType.declaredMethods.first {
                        it.name == "concat" && it.parameters.size == 1 && it.parameters.first().type == stringType
                    }
                    val methodRef = VirtualMethodRefImpl.of(stringType, concatMethod)
                    val newConcatExpr = JIRVirtualCallExpr(methodRef, firstStr, listOf(secondStr))
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

        if (!changed) return list

        /**
         * after we changed the instruction list, we need to examine new instruction list and
         * remap all the old JIRInstRef's to new ones
         */
        instructionIndices.putAll(instructions.indices.map { instructions[it] to it })
        return InstListImpl(instructions.map { it.accept(this) })
    }

    private fun stringify(inst: JIRInst, value: JIRValue, instList: MutableList<JIRInst>): JIRValue {
        return when {
            PredefinedJIRPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JIRClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JIRStaticCallExpr(method.methodRef(), listOf(value))
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
                val methodRef = VirtualMethodRefImpl.of(boxedType, method)
                val toStringExpr = JIRVirtualCallExpr(methodRef, value, emptyList())
                val assignment = JIRLocalVar("${value}String", stringType)
                instList += JIRAssignInst(inst.location, assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JIRInstRef) = JIRInstRef(
        instructionIndices[instructionReplacements.getOrDefault(list.get(instRef.index), list.get(instRef.index))] ?: -1
    )

    private fun indicesOf(instRef: JIRInstRef): List<JIRInstRef> {
        val index = list.get(instRef.index)
        return catchReplacements.getOrDefault(index, listOf(index)).map {
            JIRInstRef(instructions.indexOf(it))

        }
    }

    override fun visitJIRCatchInst(inst: JIRCatchInst): JIRInst = JIRCatchInst(
        inst.location,
        inst.throwable,
        inst.throwableTypes,
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
