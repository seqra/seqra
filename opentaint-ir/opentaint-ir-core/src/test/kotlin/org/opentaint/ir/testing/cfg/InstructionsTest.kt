package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InstructionsTest : BaseTest() {

    companion object : WithDB()

    @Test
    fun `assign inst`() {
        val clazz = cp.findClass<SimpleAlias1>()
        val method = clazz.declaredMethods.first { it.name == "main" }
        val bench = cp.findClass<Benchmark>()
        val use = bench.declaredMethods.first { it.name == "use" }
        val instructions = method.instList.instructions
        val firstUse = instructions.indexOfFirst { it.callExpr?.method?.method == use }
        val assign = instructions[firstUse + 1] as JIRAssignInst
        assertEquals("%4", (assign.lhv as JIRLocalVar).name)
        assertEquals("%1", (assign.rhv as JIRLocalVar).name)
    }

    @Test
    fun `inst index`() {
        val clazz = cp.findClass<JavaArrays>()
        val method = clazz.declaredMethods.first {
            it.name == "arrayObjectMonitors"
        }
        method.instList.forEachIndexed { index, inst ->
            assertEquals(index, inst.location.index)
        }

        method.flowGraph().instructions.forEachIndexed { index, inst ->
            assertEquals(index, inst.location.index)
        }
    }
}