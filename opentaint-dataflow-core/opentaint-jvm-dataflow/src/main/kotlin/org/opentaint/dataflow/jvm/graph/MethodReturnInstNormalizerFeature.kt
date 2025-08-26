package org.opentaint.dataflow.jvm.graph

import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl

object MethodReturnInstNormalizerFeature : JIRInstExtFeature {
    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        val returnInstructions = list.filterIsInstance<JIRReturnInst>()
        if (returnInstructions.size <= 1) {
            return list
        }

        val instructions = list.instructions.toMutableList()

        val firstReturn = returnInstructions.first()

        // Void method
        if (firstReturn.returnValue == null) {
            val location = JIRInstLocationImpl(method, instructions.size, firstReturn.lineNumber)
            instructions += JIRReturnInst(location, returnValue = null)

            for (returnInstruction in returnInstructions) {
                val retLoc = returnInstruction.location
                val retReplacement = JIRGotoInst(retLoc, JIRInstRef(location.index))
                instructions[retLoc.index] = retReplacement
            }

            return JIRInstListImpl(instructions)
        }

        val maxLocalVarIndex = instructions.maxOfOrNull { LocalVarMaxIndexFinder.find(it.operands) } ?: -1
        val returnValueLocalVarIndex = maxLocalVarIndex + 1
        val returnValueType = method.enclosingClass.classpath.findTypeOrNull(method.returnType)
            ?: return list

        val singleReturnValue = JIRLocalVar(returnValueLocalVarIndex, "ret", returnValueType)

        val singleReturnLocation = JIRInstLocationImpl(method, instructions.size, firstReturn.lineNumber)
        instructions += JIRReturnInst(singleReturnLocation, singleReturnValue)

        for (returnInstruction in returnInstructions) {
            val currentRetLoc = returnInstruction.location
            val returnValue = returnInstruction.returnValue ?: return list

            val assignLoc = JIRInstLocationImpl(method, instructions.size, currentRetLoc.lineNumber)
            instructions += JIRAssignInst(assignLoc, singleReturnValue, returnValue)

            val gotoRetLoc = JIRInstLocationImpl(method, instructions.size, currentRetLoc.lineNumber)
            instructions += JIRGotoInst(gotoRetLoc, JIRInstRef(singleReturnLocation.index))

            val retReplacement = JIRGotoInst(currentRetLoc, JIRInstRef(assignLoc.index))
            instructions[currentRetLoc.index] = retReplacement
        }

        return JIRInstListImpl(instructions)
    }

    private object LocalVarMaxIndexFinder : JIRExprVisitor.Default<Int> {
        override fun defaultVisitJIRExpr(expr: JIRExpr) = find(expr.operands)
        override fun visitJIRLocalVar(value: JIRLocalVar) = value.index
        fun find(expressions: Iterable<JIRExpr>): Int = expressions.maxOfOrNull { it.accept(this) } ?: -1
    }
}
