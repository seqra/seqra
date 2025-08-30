package org.opentaint.machine.interpreter.transformers

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAddExpr
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRGeExpr
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.machine.interpreter.transformers.JIRSingleInstructionTransformer.BlockGenerationContext

object JIRMultiDimArrayAllocationTransformer : JIRInstExtFeature {
    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        val multiDimArrayAllocations = list.mapNotNull { inst ->
            val assignInst = inst as? JIRAssignInst ?: return@mapNotNull null
            val arrayAllocation = assignInst.rhv as? JIRNewArrayExpr ?: return@mapNotNull null
            if (arrayAllocation.dimensions.size == 1) return@mapNotNull null
            assignInst to arrayAllocation
        }

        if (multiDimArrayAllocations.isEmpty()) return list

        val transformer = JIRSingleInstructionTransformer(list)
        for ((assignInst, arrayAllocation) in multiDimArrayAllocations) {
            transformer.generateReplacementBlock(assignInst) {
                generateBlock(
                    method.enclosingClass.classpath,
                    assignInst.lhv, arrayAllocation
                )
            }
        }

        return transformer.buildInstList()
    }

    /**
     * original:
     * result = new T[d0][d1][d2]
     *
     * rewrited:
     * a0: T[][][] = new T[d0][][]
     * i0 = 0
     * INIT_0_START:
     *   if (i0 >= d0) goto INIT_0_END
     *
     *   a1: T[][] = new T[d1][]
     *   i1 = 0
     *
     *   INIT_1_START:
     *      if (i1 >= d1) goto INIT_1_END
     *
     *      a2: T[] = new T[d2]
     *
     *      a1[i1] = a2
     *      i1++
     *      goto INIT_1_START
     *
     *   INIT_1_END:
     *      a0[i0] = a1
     *      i0++
     *      goto INIT_0_START
     *
     * INIT_0_END:
     *   result = a0
     * */
    private fun BlockGenerationContext.generateBlock(
        cp: JIRClasspath,
        resultVariable: JIRValue,
        arrayAllocation: JIRNewArrayExpr
    ) {
        val type = arrayAllocation.type as? JIRArrayType
            ?: error("Incorrect array allocation: $arrayAllocation")

        val arrayVar = generateBlock(cp, type, arrayAllocation.dimensions, dimensionIdx = 0)
        addInstruction { loc ->
            JIRAssignInst(loc, resultVariable, arrayVar)
        }
    }

    private fun BlockGenerationContext.generateBlock(
        cp: JIRClasspath,
        type: JIRArrayType,
        dimensions: List<JIRValue>,
        dimensionIdx: Int
    ): JIRValue {
        val dimension = dimensions[dimensionIdx]
        val arrayVar = nextLocalVar("a_${originalLocation.index}_$dimensionIdx", type)

        addInstruction { loc ->
            JIRAssignInst(loc, arrayVar, JIRNewArrayExpr(type, listOf(dimension)))
        }

        if (dimensionIdx == dimensions.lastIndex) return arrayVar

        val initializerIdxVar = nextLocalVar("i_${originalLocation.index}_$dimensionIdx", cp.int)
        addInstruction { loc ->
            JIRAssignInst(loc, initializerIdxVar, JIRInt(0, cp.int))
        }

        val initStartLoc: JIRInstLocation
        addInstruction { loc ->
            initStartLoc = loc

            val cond = JIRGeExpr(cp.boolean, initializerIdxVar, dimension)
            val nextInst = JIRInstRef(loc.index + 1)
            JIRIfInst(loc, cond, END_LABEL_STUB, nextInst)
        }

        val nestedArrayType = type.elementType as? JIRArrayType
            ?: error("Incorrect array type: $type")

        val nestedArrayVar = generateBlock(cp, nestedArrayType, dimensions, dimensionIdx + 1)

        addInstruction { loc ->
            val arrayElement = JIRArrayAccess(arrayVar, initializerIdxVar, nestedArrayType)
            JIRAssignInst(loc, arrayElement, nestedArrayVar)
        }

        addInstruction { loc ->
            JIRAssignInst(loc, initializerIdxVar, JIRAddExpr(cp.int, initializerIdxVar, JIRInt(1, cp.int)))
        }

        val initEndLoc: JIRInstLocation
        addInstruction { loc ->
            initEndLoc = loc
            JIRGotoInst(loc, JIRInstRef(initStartLoc.index))
        }

        replaceInstructionAtLocation(initStartLoc) { blockStartInst ->
            val blockEnd = JIRInstRef(initEndLoc.index + 1)
            replaceEndLabelStub(blockStartInst as JIRIfInst, blockEnd)
        }

        return arrayVar
    }

    private val END_LABEL_STUB = JIRInstRef(-1)

    private fun replaceEndLabelStub(inst: JIRIfInst, replacement: JIRInstRef): JIRIfInst = with(inst) {
        JIRIfInst(
            location,
            condition,
            if (trueBranch == END_LABEL_STUB) replacement else trueBranch,
            if (falseBranch == END_LABEL_STUB) replacement else falseBranch,
        )
    }
}
