package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.analysis.impl.NullAssumptionAnalysis
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NullabilityAssumptionAnalysisTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy)

    @Test
    fun `null-assumption analysis should work`() {
        val clazz = cp.findClass<NullAssumptionAnalysisExample>()
        with(clazz.findMethod("test1").flowGraph()) {
            val analysis = NullAssumptionAnalysis(this).also {
                it.run()
            }
            val sout = (instructions[0] as JIRAssignInst).lhv as JIRLocal
            val a = ((instructions[3] as JIRAssignInst).rhv as JIRInstanceCallExpr).instance

            assertTrue(analysis.isAssumedNonNullBefore(instructions[2], a))
            assertTrue(analysis.isAssumedNonNullBefore(instructions[0], sout))
        }
    }

    @Test
    fun `null-assumption analysis should work 2`() {
        val clazz = cp.findClass<NullAssumptionAnalysisExample>()
        with(clazz.findMethod("test2").flowGraph()) {
            val analysis = NullAssumptionAnalysis(this).also {
                it.run()
            }
            val sout = (instructions[0] as JIRAssignInst).lhv as JIRLocal
            val a = ((instructions[3] as JIRAssignInst).rhv as JIRInstanceCallExpr).instance
            val x = (instructions[5] as JIRAssignInst).lhv as JIRLocal

            assertTrue(analysis.isAssumedNonNullBefore(instructions[2], a))
            assertTrue(analysis.isAssumedNonNullBefore(instructions[0], sout))
            analysis.isAssumedNonNullBefore(instructions[5], x)
        }
    }

    private fun JIRClassOrInterface.findMethod(name: String): JIRMethod = declaredMethods.first { it.name == name }

}