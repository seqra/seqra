package org.opentaint.ir.analysis.impl

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.taint.ForwardTaintFlowFunctions
import org.opentaint.ir.analysis.taint.TaintZeroFact
import org.opentaint.ir.analysis.taint.Tainted
import org.opentaint.ir.analysis.util.JIRTraits
import org.opentaint.ir.analysis.util.getArgument
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
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
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TaintFlowFunctionsTest : BaseTest() {

    companion object : WithDB(Usages, InMemoryHierarchy)

    override val cp: JIRClasspath = runBlocking {
        val configFileName = "config_test.json"
        val configResource = this.javaClass.getResourceAsStream("/$configFileName")
        if (configResource != null) {
            val configJson = configResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    private val graph: JIRApplicationGraph = mockk {
        every { project } returns cp
        every { callees(any()) } answers {
            sequenceOf(arg<JIRInst>(0).callExpr!!.callee)
        }
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
    fun `test obtain start facts`() = with(JIRTraits) {
        val flowSpace = ForwardTaintFlowFunctions(graph)
        val facts = flowSpace.obtainPossibleStartFacts(testMethod).toList()
        val arg0 = cp.getArgument(testMethod.parameters[0])!!
        val arg0Taint = Tainted(arg0.toPath(), TaintMark("EXAMPLE"))
        Assertions.assertEquals(listOf(TaintZeroFact, arg0Taint), facts)
    }

    @Test
    fun `test sequential flow function assign mark`() = with(JIRTraits) {
        // "x := y", where 'y' is tainted, should result in both 'x' and 'y' to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val y: JIRLocal = JIRLocalVar(2, "y", stringType)
        val inst = JIRAssignInst(location = mockk(), lhv = x, rhv = y)
        val flowSpace = ForwardTaintFlowFunctions(graph)
        val f = flowSpace.obtainSequentFlowFunction(inst, next = mockk())
        val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val facts = f.compute(yTaint).toList()
        Assertions.assertEquals(listOf(yTaint, xTaint), facts)
    }

    @Test
    fun `test call flow function assign mark`() = with(JIRTraits) {
        // "x := test(...)", where 'test' is a source, should result in 'x' to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRAssignInst(location = mockk(), lhv = x, rhv = mockk<JIRCallExpr> {
            every { callee } returns testMethod
        })
        val flowSpace = ForwardTaintFlowFunctions(graph)
        val f = flowSpace.obtainCallToReturnSiteFlowFunction(callStatement, returnSite = mockk())
        val xTaint = Tainted(x.toPath(), TaintMark("EXAMPLE"))
        val facts = f.compute(TaintZeroFact).toList()
        Assertions.assertEquals(listOf(TaintZeroFact, xTaint), facts)
    }

    @Test
    fun `test call flow function remove mark`() = with(JIRTraits) {
        // "test(x)", where 'x' is tainted, should result in 'x' NOT to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRCallInst(location = mockk(), callExpr = mockk<JIRCallExpr> {
            every { callee } returns testMethod
            every { args } returns listOf(x)
        })
        val flowSpace = ForwardTaintFlowFunctions(graph)
        val f = flowSpace.obtainCallToReturnSiteFlowFunction(callStatement, returnSite = mockk())
        val xTaint = Tainted(x.toPath(), TaintMark("REMOVE"))
        val facts = f.compute(xTaint).toList()
        Assertions.assertTrue(facts.isEmpty())
    }

    @Test
    fun `test call flow function copy mark`() = with(JIRTraits) {
        // "y := test(x)" should result in 'y' to be tainted only when 'x' is tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val y: JIRLocal = JIRLocalVar(2, "y", stringType)
        val callStatement = JIRAssignInst(location = mockk(), lhv = y, rhv = mockk<JIRCallExpr> {
            every { callee } returns testMethod
            every { args } returns listOf(x)
        })
        val flowSpace = ForwardTaintFlowFunctions(graph)
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

    @Test
    fun `test call to start flow function`() = with(JIRTraits) {
        // "test(x)", where 'x' is tainted, should result in 'x' (formal argument of 'test') to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRCallInst(location = mockk(), callExpr = mockk<JIRCallExpr> {
            every { callee } returns testMethod
            every { args } returns listOf(x)
        })
        val flowSpace = ForwardTaintFlowFunctions(graph)
        val f = flowSpace.obtainCallToStartFlowFunction(callStatement, calleeStart = mockk {
            every { location } returns mockk {
                every { method } returns testMethod
            }
        })
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val arg0: JIRArgument = cp.getArgument(testMethod.parameters[0])!!
        val arg0Taint = Tainted(arg0.toPath(), TaintMark("TAINT"))
        val facts = f.compute(xTaint).toList()
        Assertions.assertEquals(listOf(arg0Taint), facts)
        val other: JIRLocal = JIRLocalVar(10, "other", stringType)
        val otherTaint = Tainted(other.toPath(), TaintMark("TAINT"))
        val facts2 = f.compute(otherTaint).toList()
        Assertions.assertTrue(facts2.isEmpty())
    }

    @Test
    fun `test exit flow function`() = with(JIRTraits) {
        // "x := test()" + "return y", where 'y' is tainted, should result in 'x' to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRAssignInst(location = mockk(), lhv = x, rhv = mockk<JIRCallExpr> {
            every { callee } returns testMethod
        })
        val y: JIRLocal = JIRLocalVar(1, "y", stringType)
        val exitStatement = JIRReturnInst(location = mockk {
            every { method } returns testMethod
        }, returnValue = y)
        val flowSpace = ForwardTaintFlowFunctions(graph)
        val f = flowSpace.obtainExitToReturnSiteFlowFunction(callStatement, returnSite = mockk(), exitStatement)
        val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val facts = f.compute(yTaint).toList()
        Assertions.assertEquals(listOf(xTaint), facts)
    }
}
