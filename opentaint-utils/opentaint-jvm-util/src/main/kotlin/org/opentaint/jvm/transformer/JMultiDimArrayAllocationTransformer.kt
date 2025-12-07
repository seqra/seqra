package org.opentaint.jvm.transformer

import org.opentaint.ir.api.jvm.JcArrayType
import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.JcInstExtFeature
import org.opentaint.ir.api.jvm.JcMethod
import org.opentaint.ir.api.jvm.cfg.JcAddExpr
import org.opentaint.ir.api.jvm.cfg.JcArrayAccess
import org.opentaint.ir.api.jvm.cfg.JcAssignInst
import org.opentaint.ir.api.jvm.cfg.JcGeExpr
import org.opentaint.ir.api.jvm.cfg.JcGotoInst
import org.opentaint.ir.api.jvm.cfg.JcIfInst
import org.opentaint.ir.api.jvm.cfg.JcInst
import org.opentaint.ir.api.jvm.cfg.JcInstList
import org.opentaint.ir.api.jvm.cfg.JcInstLocation
import org.opentaint.ir.api.jvm.cfg.JcInstRef
import org.opentaint.ir.api.jvm.cfg.JcInt
import org.opentaint.ir.api.jvm.cfg.JcNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JcValue
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.jvm.transformer.JSingleInstructionTransformer.BlockGenerationContext

object JMultiDimArrayAllocationTransformer : JcInstExtFeature {
    override fun transformInstList(method: JcMethod, list: JcInstList<JcInst>): JcInstList<JcInst> {
        val multiDimArrayAllocations = list.mapNotNull { inst ->
            val assignInst = inst as? JcAssignInst ?: return@mapNotNull null
            val arrayAllocation = assignInst.rhv as? JcNewArrayExpr ?: return@mapNotNull null
            if (arrayAllocation.dimensions.size == 1) return@mapNotNull null
            assignInst to arrayAllocation
        }

        if (multiDimArrayAllocations.isEmpty()) return list

        val transformer = JSingleInstructionTransformer(list)
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
        cp: JcClasspath,
        resultVariable: JcValue,
        arrayAllocation: JcNewArrayExpr
    ) {
        val type = arrayAllocation.type as? JcArrayType
            ?: error("Incorrect array allocation: $arrayAllocation")

        val arrayVar = generateBlock(cp, type, arrayAllocation.dimensions, dimensionIdx = 0)
        addInstruction { loc ->
            JcAssignInst(loc, resultVariable, arrayVar)
        }
    }

    private fun BlockGenerationContext.generateBlock(
        cp: JcClasspath,
        type: JcArrayType,
        dimensions: List<JcValue>,
        dimensionIdx: Int
    ): JcValue {
        val dimension = dimensions[dimensionIdx]
        val arrayVar = nextLocalVar("a_${originalLocation.index}_$dimensionIdx", type)

        addInstruction { loc ->
            JcAssignInst(loc, arrayVar, JcNewArrayExpr(type, listOf(dimension)))
        }

        if (dimensionIdx == dimensions.lastIndex) return arrayVar

        val initializerIdxVar = nextLocalVar("i_${originalLocation.index}_$dimensionIdx", cp.int)
        addInstruction { loc ->
            JcAssignInst(loc, initializerIdxVar, JcInt(0, cp.int))
        }

        val initStartLoc: JcInstLocation
        addInstruction { loc ->
            initStartLoc = loc

            val cond = JcGeExpr(cp.boolean, initializerIdxVar, dimension)
            val nextInst = JcInstRef(loc.index + 1)
            JcIfInst(loc, cond, END_LABEL_STUB, nextInst)
        }

        val nestedArrayType = type.elementType as? JcArrayType
            ?: error("Incorrect array type: $type")

        val nestedArrayVar = generateBlock(cp, nestedArrayType, dimensions, dimensionIdx + 1)

        addInstruction { loc ->
            val arrayElement = JcArrayAccess(arrayVar, initializerIdxVar, nestedArrayType)
            JcAssignInst(loc, arrayElement, nestedArrayVar)
        }

        addInstruction { loc ->
            JcAssignInst(loc, initializerIdxVar, JcAddExpr(cp.int, initializerIdxVar, JcInt(1, cp.int)))
        }

        val initEndLoc: JcInstLocation
        addInstruction { loc ->
            initEndLoc = loc
            JcGotoInst(loc, JcInstRef(initStartLoc.index))
        }

        replaceInstructionAtLocation(initStartLoc) { blockStartInst ->
            val blockEnd = JcInstRef(initEndLoc.index + 1)
            replaceEndLabelStub(blockStartInst as JcIfInst, blockEnd)
        }

        return arrayVar
    }

    private val END_LABEL_STUB = JcInstRef(-1)

    private fun replaceEndLabelStub(inst: JcIfInst, replacement: JcInstRef): JcIfInst = with(inst) {
        JcIfInst(
            location,
            condition,
            if (trueBranch == END_LABEL_STUB) replacement else trueBranch,
            if (falseBranch == END_LABEL_STUB) replacement else falseBranch,
        )
    }
}
