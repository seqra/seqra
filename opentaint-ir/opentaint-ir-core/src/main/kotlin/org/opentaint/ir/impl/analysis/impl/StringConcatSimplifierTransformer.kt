package org.opentaint.ir.impl.analysis.impl

import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.cfg.BsmStringArg
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIRGotoInst
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRInstRef
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.cfg.JIRStringConstant
import org.opentaint.ir.api.cfg.JIRSwitchInst
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.cfg.values
import org.opentaint.ir.api.ext.autoboxIfNeeded
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.cfg.methodRef
import kotlin.collections.set

class StringConcatSimplifierTransformer(
    classpath: JIRClasspath,
    private val list: JIRInstList<JIRInst>,
) : JIRInstVisitor.Default<JIRInst> {

    override fun defaultVisitJIRInst(inst: JIRInst): JIRInst {
        return inst
    }

    private val instructionReplacements: MutableMap<JIRInst, JIRInst> = mutableMapOf()
    private val instructions: MutableList<JIRInst> = mutableListOf()
    private val catchReplacements: MutableMap<JIRInst, MutableList<JIRInst>> = mutableMapOf()
    private val instructionIndices: MutableMap<JIRInst, Int> = mutableMapOf()

    private val stringType = classpath.findTypeOrNull<String>() as JIRClassType

    private var localCounter = list
        .flatMap { it.values.filterIsInstance<JIRLocalVar>() }
        .maxOfOrNull { it.index }?.plus(1) ?: 0

    fun transform(): JIRInstList<JIRInst> {
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
        return JIRInstListImpl(instructions.map { it.accept(this) })
    }

    private fun stringify(inst: JIRInst, value: JIRValue, instList: MutableList<JIRInst>): JIRValue {
        return when {
            PredefinedPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JIRClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JIRStaticCallExpr(method.methodRef(), listOf(value))
                val assignment = JIRLocalVar(localCounter++, "${value}String", stringType)
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
                val assignment = JIRLocalVar(localCounter++, "${value}String", stringType)
                instList += JIRAssignInst(inst.location, assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JIRInstRef) = JIRInstRef(
        instructionIndices[instructionReplacements.getOrDefault(list[instRef.index], list[instRef.index])] ?: -1
    )

    private fun indicesOf(instRef: JIRInstRef): List<JIRInstRef> {
        val index = list[instRef.index]
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
