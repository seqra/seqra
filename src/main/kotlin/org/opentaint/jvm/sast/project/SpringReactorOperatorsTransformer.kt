package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRClassConstant
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.findType
import org.opentaint.ir.impl.cfg.JIRGraphImpl
import org.opentaint.dataflow.jvm.graph.transformers.JIRSingleInstructionTransformer
import org.opentaint.dataflow.jvm.graph.transformers.JIRSingleInstructionTransformer.BlockGenerationContext

object SpringReactorOperatorsTransformer : JIRInstExtFeature {
    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        val operatorSet = list.mapNotNull { inst -> findOperatorsSet(inst)?.let { inst to it } }
        if (operatorSet.isEmpty()) return list

        val flowGraph = JIRGraphImpl(method, list.instructions)
        val classInitializer = method.enclosingClass.declaredMethods
            .singleOrNull { it.isClassInitializer }
            ?: return list

        val fieldSetters = operatorSet.mapNotNull { (inst, call) ->
            findOperatorsSetField(flowGraph, inst, call, classInitializer)?.let { inst to it }
                ?: run {
                    logger.warn { "Failed to convert field setter: $inst" }
                    null
                }
        }

        if (fieldSetters.isEmpty()) return list

        val transformer = JIRSingleInstructionTransformer(list)
        val boolType = method.enclosingClass.classpath.findType(PredefinedPrimitives.Boolean)
        for ((setterCall, setter) in fieldSetters) {
            transformer.generateReplacementBlock(setterCall) {
                generateSetterReplacement(setter.first, setter.second, (setterCall as? JIRAssignInst)?.lhv, boolType)
            }
        }

        return transformer.buildInstList()
    }

    private fun BlockGenerationContext.generateSetterReplacement(
        fieldRef: JIRFieldRef,
        fieldValue: JIRValue,
        resultVariable: JIRValue?,
        boolType: JIRType
    ) {
        addInstruction { loc ->
            JIRAssignInst(loc, fieldRef, fieldValue)
        }

        if (resultVariable != null) {
            addInstruction { loc ->
                JIRAssignInst(loc, resultVariable, JIRBool(true, boolType))
            }
        }
    }

    private fun findOperatorsSetField(
        graph: JIRGraph,
        setInst: JIRInst,
        setCall: JIRCallExpr,
        classInitializer: JIRMethod
    ): Pair<JIRFieldRef, JIRValue>? {
        val (fieldRef, instance, fieldValue) = setCall.args

        val fieldUpdater = findSingleAssignedValue<JIRFieldRef>(graph, setInst, fieldRef)
            ?.field?.takeIf { it.isStatic }
            ?: return null

        val classInitializerGraph = classInitializer.flowGraph()
        val clinitReturn = classInitializerGraph.exits
            .filterIsInstance<JIRReturnInst>()
            .singleOrNull()
            ?: return null

        val fieldUpdaterRef = JIRFieldRef(instance = null, fieldUpdater)
        val fieldUpdaterCall = findSingleAssignedValue<JIRCallExpr>(
            classInitializerGraph, clinitReturn, fieldUpdaterRef
        )?.takeIf { it.method.method.isJavaAtomicRefFieldUpdater() }
            ?: return null

        val (typeClass, fieldValueClass, fieldName) = fieldUpdaterCall.args

        val typeClassConst = findSingleAssignedValue<JIRClassConstant>(
            classInitializerGraph, clinitReturn, typeClass
        ) ?: return null

        fieldValueClass.let {  }
//        val fieldValueClassConst = findSingleAssignedValue<JIRClassConstant>(
//            classInitializerGraph, clinitReturn, fieldValueClass
//        ) ?: return null

        val fieldNameConst = findSingleAssignedValue<JIRStringConstant>(
            classInitializerGraph, clinitReturn, fieldName
        ) ?: return null

        val cls = typeClassConst.klass as? JIRClassType ?: return null
        val field = cls.declaredFields.singleOrNull { it.name == fieldNameConst.value } ?: return null

        return JIRFieldRef(instance, field) to fieldValue
    }

    private inline fun <reified T> findSingleAssignedValue(
        graph: JIRGraph,
        initialInst: JIRInst,
        value: JIRValue
    ): T? {
        if (value is T) return value

        var assign = findSingleAssignment(graph, initialInst, value) ?: return null
        while (true) {
            val e = assign.rhv
            if (e is T) return e
            if (e !is JIRValue) return null
            assign = findSingleAssignment(graph, assign, e) ?: return null
        }
    }

    private fun findSingleAssignment(graph: JIRGraph, initialInst: JIRInst, value: JIRValue): JIRAssignInst? {
        val unprocessed = mutableListOf(initialInst)
        val results = hashSetOf<JIRAssignInst>()

        while (unprocessed.isNotEmpty()) {
            val inst = unprocessed.removeLast()
            if (inst is JIRAssignInst && inst.lhv == value) {
                results.add(inst)
            } else {
                unprocessed.addAll(graph.predecessors(inst))
            }
        }

        return results.singleOrNull()
    }

    private fun findOperatorsSet(inst: JIRInst): JIRCallExpr? {
        val call = inst.callExpr
        val method = call?.method ?: return null
        if (!method.isStatic) return null

        if (method.enclosingType.typeName != OPERATORS_CLASS) return null
        if (method.name !in operatorsFieldSetMethods) return null

        return call
    }

    private const val OPERATORS_CLASS = "reactor.core.publisher.Operators"
    private val operatorsFieldSetMethods = arrayOf("set", "setOnce", "replace")

    private fun JIRMethod.isJavaAtomicRefFieldUpdater(): Boolean {
        if (!isStatic) return false
        if (name != "newUpdater") return false
        if (enclosingClass.name != "java.util.concurrent.atomic.AtomicReferenceFieldUpdater") return false
        return true
    }

    private val logger = object : KLogging() {}.logger
}
