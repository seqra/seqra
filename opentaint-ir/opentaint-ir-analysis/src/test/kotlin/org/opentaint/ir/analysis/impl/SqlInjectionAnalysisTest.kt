package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.VulnerabilityInstance
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.library.JIRSingletonUnitResolver
import org.opentaint.ir.analysis.library.analyzers.JIRSqlInjectionAnalyzer
import org.opentaint.ir.analysis.library.newJIRSqlInjectionRunnerFactory
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
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
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, listOf(
            // Not working yet (#156)
            "s03", "s04"
        ))

        private val vulnerabilityType = JIRSqlInjectionAnalyzer.vulnerabilityDescription.ruleId
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(vulnerabilityType, className)
    }

    override fun launchAnalysis(methods: List<JIRMethod>): List<VulnerabilityInstance<JIRMethod, JIRInstLocation, JIRInst>> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return runAnalysis(graph, JIRSingletonUnitResolver, newJIRSqlInjectionRunnerFactory(), methods)
    }
}