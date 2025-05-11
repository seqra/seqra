package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.ClassUnitResolver
import org.opentaint.ir.analysis.engine.IfdsUnitRunnerFactory
import org.opentaint.ir.analysis.engine.MethodUnitResolver
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.library.UnusedVariableRunnerFactory
import org.opentaint.ir.analysis.library.newNpeRunnerFactory
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.analysis.sarif.SarifReport
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithGlobalDB()

    private fun testOne(
        unitResolver: UnitResolver,
        ifdsUnitRunnerFactory: IfdsUnitRunnerFactory,
    ) {
        val clazz = cp.findClass<DateTime>()
        val result = runAnalysis(graph, unitResolver, ifdsUnitRunnerFactory, clazz.declaredMethods, 60000L)

        println("Vulnerabilities found: ${result.size}")
        println("Generated report:")
        SarifReport.fromVulnerabilities(result).encodeToStream(System.out)
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(ClassUnitResolver(false), UnusedVariableRunnerFactory)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(MethodUnitResolver, newNpeRunnerFactory())
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}
