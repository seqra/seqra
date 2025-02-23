package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.ir.analysis.engine.IfdsUnitRunner
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.library.MethodUnitResolver
import org.opentaint.ir.analysis.library.UnusedVariableRunner
import org.opentaint.ir.analysis.library.getClassUnitResolver
import org.opentaint.ir.analysis.library.newNpeRunner
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

    private fun <UnitType> testOne(unitResolver: UnitResolver<UnitType>, ifdsUnitRunner: IfdsUnitRunner) {
        val clazz = cp.findClass<DateTime>()
        val result = runAnalysis(graph, unitResolver, ifdsUnitRunner, clazz.declaredMethods).toDumpable()

        println("Vulnerabilities found: ${result.foundVulnerabilities.size}")
        val json = Json { prettyPrint = true }
        json.encodeToStream(result, System.out)
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(getClassUnitResolver(false), UnusedVariableRunner)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(MethodUnitResolver, newNpeRunner())
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}