package org.opentaint.ir.analysis.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.SingletonUnitResolver
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.ifds2.taint.Vulnerability
import org.opentaint.ir.analysis.ifds2.taint.runTaintAnalysis
import org.opentaint.ir.analysis.impl.BaseAnalysisTest.Companion.provideClassesForJuliet
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.methods
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.analysis.SqlInjectionExamples
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private val logger = KotlinLogging.logger {}

class Ifds2SqlTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, specificBansCwe89)

        private val specificBansCwe89: List<String> = listOf(
            // Not working yet (#156)
            "s03", "s04"
        )
    }

    override val cp: JIRClasspath = runBlocking {
        val taintConfigFileName = "config_small.json"
        val defaultConfigResource = this.javaClass.getResourceAsStream("/$taintConfigFileName")
        if (defaultConfigResource != null) {
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    @Test
    fun `simple SQL injection`() {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method)
        assertTrue(sinks.isNotEmpty())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(className)
    }

    @Test
    fun `test on Juliet's CWE 89 Environment executeBatch 01`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_01"
        testSingleJulietClass(className)
    }

    @Test
    fun `test on Juliet's CWE 89 database prepareStatement 01`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__database_prepareStatement_01"
        testSingleJulietClass(className)
    }

    @Test
    fun `test on specific Juliet instance`() {
        // val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_45"
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__connect_tcp_execute_01"

        testSingleJulietClass(className)
    }

    private fun testSingleJulietClass(className: String) {
        println(className)

        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val goodMethod = clazz.methods.single { it.name == "good" }

        logger.info { "Searching for sinks in BAD method: $badMethod" }
        val badIssues = findSinks(badMethod)
        logger.info { "Total ${badIssues.size} issues in BAD method" }
        for (issue in badIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(badIssues.isNotEmpty()) { "Must find some sinks in 'bad' for $className" }

        logger.info { "Searching for sinks in GOOD method: $goodMethod" }
        val goodIssues = findSinks(goodMethod)
        logger.info { "Total ${goodIssues.size} issues in GOOD method" }
        for (issue in goodIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(goodIssues.isEmpty()) { "Must NOT find any sinks in 'good' for $className" }
    }

    private fun findSinks(method: JIRMethod): List<Vulnerability> {
        val vulnerabilities = launchAnalysis(listOf(method))
        return vulnerabilities
    }

    private fun launchAnalysis(methods: List<JIRMethod>): List<Vulnerability> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        val unitResolver = SingletonUnitResolver
        return runTaintAnalysis(graph, unitResolver, methods)
    }
}
