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

import io.mockk.every
import io.mockk.mockk
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.dataflow.jvm.util.toPath
import org.opentaint.dataflow.taint.ForwardTaintFlowFunctions
import org.opentaint.dataflow.taint.TaintZeroFact
import org.opentaint.dataflow.taint.Tainted

@TestInstance(PER_CLASS)
open class TaintFlowFunctionsTest : BaseAnalysisTest(configFileName = "config_test.json") {

    override val graph: JIRApplicationGraph = mockk {
        every { cp } returns this@TaintFlowFunctionsTest.cp
        every { callees(any()) } answers {
            sequenceOf(arg<JIRInst>(0).callExpr!!.callee)
        }
        every { methodOf(any()) } answers {
            arg<JIRInst>(0).location.method
        }
    }

    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    private val getConfigForMethod: (JIRMethod) -> List<TaintConfigurationItem>? = { method ->
        taintConfigurationFeature?.getConfigForMethod(method)
    }

    private val stringType = cp.findTypeOrNull<String>() as JIRClassType

    private val testMethod = mockk<JIRMethod> {
        every { name } returns "test"
        every { enclosingClass } returns mockk(relaxed = true) {
            every { packageName } returns "com.example"
            every { simpleName } returns "Example"
            every { name } returns "com.example.Example"
            every { superClass } returns null
            every { interfaces } returns emptyList()
        }
        every { isConstructor } returns false
        every { returnType } returns mockk(relaxed = true)
        every { parameters } returns listOf(
            mockk(relaxed = true) {
                every { index } returns 0
                every { type } returns mockk {
                    every { typeName } returns "java.lang.String"
                }
            }
        )
    }

    @Test
    fun `test obtain start facts`() {
        with(JIRTraits(cp)) {
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val facts = flowSpace.obtainPossibleStartFacts(testMethod).toList()
            val arg0 = getArgument(testMethod.parameters[0])!!
            val arg0Taint = Tainted(arg0.toPath(), TaintMark("EXAMPLE"))
            Assertions.assertEquals(listOf(TaintZeroFact, arg0Taint), facts)
        }
    }

    @Test
    fun `test sequential flow function assign mark`() {
        with(JIRTraits(cp)) {
            // "x := y", where 'y' is tainted, should result in both 'x' and 'y' to be tainted
            val x: JIRLocal = JIRLocalVar(1, "x", stringType)
            val y: JIRLocal = JIRLocalVar(2, "y", stringType)
            val inst = JIRAssignInst(location = mockk(), lhv = x, rhv = y)
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val f = flowSpace.obtainSequentFlowFunction(inst, next = mockk())
            val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
            val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
            val facts = f.compute(yTaint).toList()
            Assertions.assertEquals(listOf(yTaint, xTaint), facts)
        }
    }

    @Test
    fun `test call flow function assign mark`() {
        with(JIRTraits(cp)) {
            // "x := test(...)", where 'test' is a source, should result in 'x' to be tainted
            val x: JIRLocal = JIRLocalVar(1, "x", stringType)
            val callStatement = JIRAssignInst(location = mockk(), lhv = x, rhv = mockk<JIRCallExpr> {
                every { callee } returns testMethod
            })
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val f = flowSpace.obtainCallToReturnSiteFlowFunction(callStatement, returnSite = mockk())
            val xTaint = Tainted(x.toPath(), TaintMark("EXAMPLE"))
            val facts = f.compute(TaintZeroFact).toList()
            Assertions.assertEquals(listOf(TaintZeroFact, xTaint), facts)
        }
    }

    @Test
    fun `test call flow function remove mark`() {
        with(JIRTraits(cp)) {
            // "test(x)", where 'x' is tainted, should result in 'x' NOT to be tainted
            val x: JIRLocal = JIRLocalVar(1, "x", stringType)
            val callStatement = JIRCallInst(location = mockk(), callExpr = mockk<JIRCallExpr> {
                every { callee } returns testMethod
                every { args } returns listOf(x)
            })
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val f = flowSpace.obtainCallToReturnSiteFlowFunction(callStatement, returnSite = mockk())
            val xTaint = Tainted(x.toPath(), TaintMark("REMOVE"))
            val facts = f.compute(xTaint).toList()
            Assertions.assertTrue(facts.isEmpty())
        }
    }

    @Test
    fun `test call flow function copy mark`() {
        with(JIRTraits(cp)) {
            // "y := test(x)" should result in 'y' to be tainted only when 'x' is tainted
            val x: JIRLocal = JIRLocalVar(1, "x", stringType)
            val y: JIRLocal = JIRLocalVar(2, "y", stringType)
            val callStatement = JIRAssignInst(location = mockk(), lhv = y, rhv = mockk<JIRCallExpr> {
                every { callee } returns testMethod
                every { args } returns listOf(x)
            })
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val f = flowSpace.obtainCallToReturnSiteFlowFunction(callStatement, returnSite = mockk())
            val xTaint = Tainted(x.toPath(), TaintMark("COPY"))
            val yTaint = Tainted(y.toPath(), TaintMark("COPY"))
            val facts = f.compute(xTaint).toList()
            Assertions.assertEquals(listOf(xTaint, yTaint), facts) // copy from x to y
            val other: JIRLocal = JIRLocalVar(10, "other", stringType)
            val otherTaint = Tainted(other.toPath(), TaintMark("OTHER"))
            val facts2 = f.compute(otherTaint).toList()
            Assertions.assertEquals(listOf(otherTaint), facts2) // pass-through
        }
    }

    @Test
    fun `test call to start flow function`() {
        with(JIRTraits(cp)) {
            // "test(x)", where 'x' is tainted, should result in 'x' (formal argument of 'test') to be tainted
            val x: JIRLocal = JIRLocalVar(1, "x", stringType)
            val callStatement = JIRCallInst(location = mockk(), callExpr = mockk<JIRCallExpr> {
                every { callee } returns testMethod
                every { args } returns listOf(x)
            })
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val f = flowSpace.obtainCallToStartFlowFunction(callStatement, calleeStart = mockk {
                every { location } returns mockk {
                    every { method } returns testMethod
                }
            })
            val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
            val arg0: JIRArgument = getArgument(testMethod.parameters[0])!!
            val arg0Taint = Tainted(arg0.toPath(), TaintMark("TAINT"))
            val facts = f.compute(xTaint).toList()
            Assertions.assertEquals(listOf(arg0Taint), facts)
            val other: JIRLocal = JIRLocalVar(10, "other", stringType)
            val otherTaint = Tainted(other.toPath(), TaintMark("TAINT"))
            val facts2 = f.compute(otherTaint).toList()
            Assertions.assertTrue(facts2.isEmpty())
        }
    }

    @Test
    fun `test exit flow function`() {
        with(JIRTraits(cp)) {
            // "x := test()" + "return y", where 'y' is tainted, should result in 'x' to be tainted
            val x: JIRLocal = JIRLocalVar(1, "x", stringType)
            val callStatement = JIRAssignInst(location = mockk(), lhv = x, rhv = mockk<JIRCallExpr> {
                every { callee } returns testMethod
            })
            val y: JIRLocal = JIRLocalVar(1, "y", stringType)
            val exitStatement = JIRReturnInst(location = mockk {
                every { method } returns testMethod
            }, returnValue = y)
            val flowSpace = ForwardTaintFlowFunctions(traits = this, graph, getConfigForMethod)
            val f = flowSpace.obtainExitToReturnSiteFlowFunction(callStatement, returnSite = mockk(), exitStatement)
            val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
            val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
            val facts = f.compute(yTaint).toList()
            Assertions.assertEquals(listOf(xTaint), facts)
        }
    }
}
