package org.opentaint.jvm.sast.project

import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIREqExpr
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.impl.cfg.TypedStaticMethodRefImpl
import org.opentaint.ir.impl.features.classpaths.JIRUnknownType
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.jvm.transformer.JSingleInstructionTransformer.BlockGenerationContext

object OpentaintNonDetermineUtil {
    fun BlockGenerationContext.addNonDetInstruction(body: (JIRInstLocation) -> JIRInst) {
        val cp = originalLocation.method.enclosingClass.classpath
        val condVar = nextLocalVar("%cond", cp.boolean)

        addInstruction { loc ->
            JIRAssignInst(loc, condVar, opentaintNonDet(cp))
        }

        addInstruction { loc ->
            JIRIfInst(
                loc,
                condition = JIREqExpr(cp.boolean, condVar, JIRBool(true, cp.boolean)),
                trueBranch = JIRInstRef(loc.index + 1),
                falseBranch = JIRInstRef(loc.index + 2)
            )
        }

        addInstruction(body)
    }

    private fun opentaintNonDet(cp: JIRClasspath): JIRCallExpr {
        val type = TypeNameImpl.fromTypeName(PredefinedPrimitives.Boolean)
        val methodRef = TypedStaticMethodRefImpl(opentaintNonDetCls(cp), "nextBool", argTypes = emptyList(), type)
        return JIRStaticCallExpr(methodRef, emptyList())
    }

    private fun opentaintNonDetCls(cp: JIRClasspath): JIRClassType =
        JIRUnknownType(cp, "opentaint.NonDetCls", virtualLoc, nullable = false)

    private val virtualLoc = VirtualLocation()
}
