package org.opentaint.ir.analysis.impl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.ir.analysis.JIRNaivePoints2EngineFactory
import org.opentaint.ir.analysis.JIRSimplifiedGraphFactory
import org.opentaint.ir.analysis.analyzers.NpeAnalyzer
import org.opentaint.ir.analysis.analyzers.UnusedVariableAnalyzer
import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.BidiIFDSForTaintAnalysis
import org.opentaint.ir.analysis.engine.ClassUnitResolver
import org.opentaint.ir.analysis.engine.IFDSInstanceProvider
import org.opentaint.ir.analysis.engine.IFDSUnitInstance
import org.opentaint.ir.analysis.engine.IFDSUnitTraverser
import org.opentaint.ir.analysis.engine.MethodUnitResolver
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    private fun testOne(analyzer: Analyzer, unitResolver: UnitResolver<*>, ifdsInstanceProvider: IFDSInstanceProvider) {
        val clazz = cp.findClass<DateTime>()

        val graph = JIRSimplifiedGraphFactory().createGraph(cp)
        val points2Engine = JIRNaivePoints2EngineFactory.createPoints2Engine(graph)
        val engine = IFDSUnitTraverser(graph, analyzer, unitResolver, points2Engine.obtainDevirtualizer(), ifdsInstanceProvider)
        clazz.declaredMethods.forEach { engine.addStart(it) }
        val result = engine.analyze().toDumpable()

        println("Vulnerabilities found: ${result.foundVulnerabilities.size}")
        val json = Json { prettyPrint = true }
        json.encodeToStream(result, System.out)
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(UnusedVariableAnalyzer(JIRSimplifiedGraphFactory().createGraph(cp)), ClassUnitResolver(false), IFDSUnitInstance)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(NpeAnalyzer(JIRSimplifiedGraphFactory().createGraph(cp)), MethodUnitResolver, BidiIFDSForTaintAnalysis)
    }
}