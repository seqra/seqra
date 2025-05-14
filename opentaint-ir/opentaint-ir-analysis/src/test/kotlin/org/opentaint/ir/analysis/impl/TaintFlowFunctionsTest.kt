package org.opentaint.ir.analysis.impl

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.ifds2.FlowFunctions
import org.opentaint.ir.analysis.ifds2.taint.ForwardTaintFlowFunctions
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.Zero
import org.opentaint.ir.analysis.library.analyzers.getArgument
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.api.ext.packageName
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private val logger = mu.KotlinLogging.logger {}

@ExtendWith(MockKExtension::class)
class TaintFlowFunctionsTest : BaseTest() {

    companion object : WithDB(Usages, InMemoryHierarchy)

    override val cp: JIRClasspath = runBlocking {
        val defaultConfigResource = this.javaClass.getResourceAsStream("/config_test.json")
        if (defaultConfigResource != null) {
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    private val stringType = cp.findTypeOrNull<String>() as JIRClassType

    @MockK
    private lateinit var graph: JIRApplicationGraph

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
        val flowSpace: FlowFunctions<TaintFact> = ForwardTaintFlowFunctions(cp, graph)
        val facts = flowSpace.obtainPossibleStartFacts(testMethod).toList()
        val arg0 = cp.getArgument(testMethod.parameters[0])!!
        Assertions.assertEquals(facts, listOf(Zero, Tainted(arg0.toPath(), TaintMark("EXAMPLE"))))
    }

    @Test
    fun `test sequential flow function`() {
        // "x := y", where 'y' is tainted, should result in both 'x' and 'y' to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val y: JIRLocal = JIRLocalVar(2, "y", stringType)
        val inst = JIRAssignInst(location = mockk(), lhv = x, rhv = y)
        val flowSpace: FlowFunctions<TaintFact> = ForwardTaintFlowFunctions(cp, graph)
        val f = flowSpace.obtainSequentFlowFunction(inst, next = mockk())
        val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val facts = f.compute(yTaint).toList()
        Assertions.assertEquals(facts, listOf(yTaint, xTaint))
    }

    @Test
    fun `test call flow function`() {
        // "x := test()", where 'test' is a source, should result in 'x' to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRAssignInst(location = mockk(), lhv = x, rhv = mockk<JIRCallExpr>() {
            every { method } returns mockk {
                every { method } returns testMethod
            }
        })
        val flowSpace: FlowFunctions<TaintFact> = ForwardTaintFlowFunctions(cp, graph)
        val f = flowSpace.obtainCallToReturnSiteFlowFunction(callStatement, returnSite = mockk())
        val xTaint = Tainted(x.toPath(), TaintMark("EXAMPLE"))
        val facts = f.compute(Zero).toList()
        Assertions.assertEquals(facts, listOf(Zero, xTaint))
    }

    @Test
    fun `test call to start flow function`() {
        // "test(x)", where 'x' is tainted, should result in 'x' (formal argument of 'test') to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRCallInst(location = mockk(), callExpr = mockk<JIRCallExpr>() {
            every { method } returns mockk {
                every { method } returns testMethod
            }
            every { args } returns listOf(x)
        })
        val flowSpace: FlowFunctions<TaintFact> = ForwardTaintFlowFunctions(cp, graph)
        val f = flowSpace.obtainCallToStartFlowFunction(callStatement, calleeStart = mockk() {
            every { location } returns mockk() {
                every { method } returns testMethod
            }
        })
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val arg0: JIRArgument = cp.getArgument(testMethod.parameters[0])!!
        val arg0Taint = Tainted(arg0.toPath(), TaintMark("TAINT"))
        val facts = f.compute(xTaint).toList()
        Assertions.assertEquals(facts, listOf(arg0Taint))
    }

    @Test
    fun `test exit flow function`() {
        // "x := test()" + "return y", where 'y' is tainted, should result in 'x' to be tainted
        val x: JIRLocal = JIRLocalVar(1, "x", stringType)
        val callStatement = JIRAssignInst(location = mockk(), lhv = x, rhv = mockk<JIRCallExpr>() {
            every { method } returns mockk {
                every { method } returns testMethod
            }
        })
        val y: JIRLocal = JIRLocalVar(1, "y", stringType)
        val exitStatement = JIRReturnInst(location = mockk {
            every { method } returns testMethod
        }, returnValue = y)
        val flowSpace: FlowFunctions<TaintFact> = ForwardTaintFlowFunctions(cp, graph)
        val f = flowSpace.obtainExitToReturnSiteFlowFunction(callStatement, returnSite = mockk(), exitStatement)
        val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val facts = f.compute(yTaint).toList()
        Assertions.assertEquals(facts, listOf(xTaint))
    }
}
