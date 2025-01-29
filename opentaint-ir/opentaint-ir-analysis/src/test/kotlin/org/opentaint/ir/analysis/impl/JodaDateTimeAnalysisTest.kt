package org.opentaint.ir.analysis.impl

import org.opentaint.ir.analysis.AnalysisEngineFactory
import org.opentaint.ir.analysis.JIRNaivePoints2EngineFactory
import org.opentaint.ir.analysis.JIRSimplifiedGraphFactory
import org.opentaint.ir.analysis.NPEAnalysisFactory
import org.opentaint.ir.analysis.UnusedVariableAnalysisFactory
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    fun testOneFactory(factory: AnalysisEngineFactory) {
        val clazz = cp.findClass<DateTime>()

        val graph = JIRSimplifiedGraphFactory().createGraph(cp)
        val points2Engine = JIRNaivePoints2EngineFactory().createPoints2Engine(graph)
        val ifds = factory.createAnalysisEngine(graph, points2Engine)
        clazz.declaredMethods
            .forEach { ifds.addStart(it) }
        val result = ifds.analyze().foundVulnerabilities

        result.forEachIndexed { ind, vulnerability ->
            println("VULNERABILITY $ind:")
            println(vulnerability)
        }
    }

    @Test
    fun `test Unused variable analysis`() {
        testOneFactory(UnusedVariableAnalysisFactory())
    }

    @Test
    fun `test NPE analysis`() {
        testOneFactory(NPEAnalysisFactory())
    }
}