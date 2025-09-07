/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.jvm.impl

import NullAssumptionAnalysisExample
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.ext.findClass
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.dataflow.jvm.flow.NullAssumptionAnalysis

@TestInstance(PER_CLASS)
class NullabilityAssumptionAnalysisTest : BaseAnalysisTest() {

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
