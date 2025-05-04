package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.IfdsUnitRunnerFactory
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.library.UnusedVariableRunnerFactory
import org.opentaint.ir.analysis.library.getJIRClassUnitResolver
import org.opentaint.ir.analysis.library.methodUnitResolver
import org.opentaint.ir.analysis.library.newJIRNpeRunnerFactory
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.analysis.sarif.SarifReport
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithGlobalDB()

    private fun <UnitType> testOne(
        unitResolver: UnitResolver<UnitType, JIRMethod>,
        ifdsUnitRunnerFactory: IfdsUnitRunnerFactory<JIRMethod, JIRInstLocation, JIRInst>
    ) {
        val clazz = cp.findClass<DateTime>()
        val result = runAnalysis(graph, unitResolver, ifdsUnitRunnerFactory, clazz.declaredMethods, 60000L)

        println("Vulnerabilities found: ${result.size}")
        println("Generated report:")
        SarifReport.fromJIRVulnerabilities(result).encodeToStream(System.out)
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(getJIRClassUnitResolver(false), UnusedVariableRunnerFactory)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(methodUnitResolver(), newJIRNpeRunnerFactory())
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}