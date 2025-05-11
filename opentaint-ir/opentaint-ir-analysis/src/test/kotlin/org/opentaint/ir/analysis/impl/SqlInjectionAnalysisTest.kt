package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.SingletonUnitResolver
import org.opentaint.ir.analysis.engine.VulnerabilityInstance
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.library.analyzers.SqlInjectionAnalyzer
import org.opentaint.ir.analysis.library.newSqlInjectionRunnerFactory
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.analysis.SqlInjectionExamples
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SqlInjectionAnalysisTest : BaseAnalysisTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, specificBansCwe89)

        private val vulnerabilityType = SqlInjectionAnalyzer.vulnerabilityDescription.ruleId
        private val specificBansCwe89: List<String> = listOf(
            // Not working yet (#156)
            "s03", "s04"
        )
    }

    @Test
    fun `simple SQL injection`() {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method, vulnerabilityType)
        assertTrue(sinks.isNotEmpty())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(vulnerabilityType, className)
    }

    @Test
    fun `test on specific Juliet's CWE 89`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_01"

        testSingleJulietClass(vulnerabilityType, className)
    }

    override fun launchAnalysis(methods: List<JIRMethod>): List<VulnerabilityInstance> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return runAnalysis(graph, SingletonUnitResolver, newSqlInjectionRunnerFactory(), methods)
    }
}
