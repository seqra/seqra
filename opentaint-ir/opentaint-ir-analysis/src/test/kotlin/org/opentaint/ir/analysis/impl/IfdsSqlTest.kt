package org.opentaint.ir.analysis.impl

import org.opentaint.ir.analysis.ifds.ClassUnitResolver
import org.opentaint.ir.analysis.ifds.SingletonUnitResolver
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.analysis.taint.Vulnerability
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.methods
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
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class IfdsSqlTest : BaseAnalysisTest() {

    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, specificBansCwe89)

        private val specificBansCwe89: List<String> = listOf(
            // Not working yet (#156)
            "s03", "s04"
        )
    }

    override fun findSinks(methods: List<JIRMethod>): List<Vulnerability> {
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        return manager.analyze(methods, timeout = 30.seconds)
    }

    @Test
    fun `simple SQL injection`() {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(listOf(method))
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
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__connect_tcp_execute_01"

        testSingleJulietClass(className)
    }

    @Test
    fun `test bidirectional runner and other stuff`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_51a"
        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val unitResolver = ClassUnitResolver(true)
        val manager = TaintManager(graph, unitResolver, useBidiRunner = true)
        val sinks = manager.analyze(listOf(badMethod), timeout = 30.seconds)
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = manager.vulnerabilityTraceGraph(sink)
        val trace = graph.getAllTraces().first()
        logger.debug { "Some trace (of length ${trace.size}): $trace" }
    }
}
