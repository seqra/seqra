package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.analyzers.TaintAnalyzer
import org.opentaint.ir.analysis.engine.SingletonUnitResolver
import org.opentaint.ir.analysis.engine.runAnalysis
import org.opentaint.ir.analysis.graph.newApplicationGraph
import org.opentaint.ir.analysis.newSqlInjectionRunner
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SqlInjectionAnalysisTest : BaseAnalysisTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, emptyList())

        private const val vulnerabilityType = TaintAnalyzer.vulnerabilityType
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(vulnerabilityType, className)
    }

    override fun launchAnalysis(methods: List<JIRMethod>): List<VulnerabilityInstance> {
        val graph = runBlocking {
            cp.newApplicationGraph()
        }
        return runAnalysis(graph, SingletonUnitResolver, newSqlInjectionRunner(), methods)
    }
}