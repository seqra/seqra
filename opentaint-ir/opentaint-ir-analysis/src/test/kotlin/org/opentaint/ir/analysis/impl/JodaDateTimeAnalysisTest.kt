package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.ir.analysis.engine.IfdsUnitRunnerFactory
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.library.MethodUnitResolver
import org.opentaint.ir.analysis.library.UnusedVariableRunnerFactory
import org.opentaint.ir.analysis.library.getClassUnitResolver
import org.opentaint.ir.analysis.library.newNpeRunnerFactory
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.analysis.toDumpable
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    private fun <UnitType> testOne(unitResolver: UnitResolver<UnitType>, ifdsUnitRunnerFactory: IfdsUnitRunnerFactory) {
        val clazz = cp.findClass<DateTime>()
        val result = runAnalysis(graph, unitResolver, ifdsUnitRunnerFactory, clazz.declaredMethods, 60000L).toDumpable()

        println("Vulnerabilities found: ${result.foundVulnerabilities.size}")
        val json = Json { prettyPrint = true }
        json.encodeToStream(result, System.out)
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(getClassUnitResolver(false), UnusedVariableRunnerFactory)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(MethodUnitResolver, newNpeRunnerFactory())
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}