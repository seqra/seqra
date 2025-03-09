package org.opentaint.ir.analysis.impl

import juliet.support.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.VulnerabilityInstance
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.methods
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.streams.asStream

abstract class BaseAnalysisTest : BaseTest() {
    companion object : WithDB(UnknownClasses, Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet(cweNum: Int, cweSpecificBans: List<String> = emptyList()): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE${cweNum}_", cweSpecificBans)
        }

        private fun Sequence<JIRClassOrInterface>.toArguments(cwe: String, cweSpecificBans: List<String>): Stream<Arguments> = this
            .map { it.name }
            .filter { it.contains(cwe) }
            .filterNot { className -> (commonJulietBans + cweSpecificBans).any { className.contains(it) } }
//            .filter { it.contains("_68") }
            .sorted()
            .map { Arguments.of(it) }
            .asStream()

        private val commonJulietBans = listOf(
            // TODO: containers not supported
            "_72", "_73", "_74",

            // TODO/Won't fix(?): dead parts of switches shouldn't be analyzed
            "_15",

            // TODO/Won't fix(?): passing through channels not supported
            "_75",

            // TODO/Won't fix(?): constant private/static methods not analyzed
            "_11", "_08",

            // TODO/Won't fix(?): unmodified non-final private variables not analyzed
            "_05", "_07",

            // TODO/Won't fix(?): unmodified non-final static variables not analyzed
            "_10", "_14",
        )
    }

    protected abstract fun launchAnalysis(methods: List<JIRMethod>): List<VulnerabilityInstance>

    protected inline fun <reified T> testOneAnalysisOnOneMethod(
        vulnerabilityType: String,
        methodName: String,
        expectedLocations: Collection<String>,
    ) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method, vulnerabilityType)

        // TODO: think about better assertions here
        assertEquals(expectedLocations.size, sinks.size)
        expectedLocations.forEach { expected ->
            assertTrue(sinks.any { it.contains(expected) })
        }
    }

    protected fun testSingleJulietClass(vulnerabilityType: String, className: String) {
        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        val goodIssues = findSinks(goodMethod, vulnerabilityType)
        val badIssues = findSinks(badMethod, vulnerabilityType)

        assertTrue(goodIssues.isEmpty())
        assertTrue(badIssues.isNotEmpty())
    }

    protected fun findSinks(method: JIRMethod, vulnerabilityType: String): Set<String> {
        val sinks = launchAnalysis(listOf(method))
            .filter { it.vulnerabilityDescription.ruleId == vulnerabilityType }
            .map { it.traceGraph.sink.toString() }

        return sinks.toSet()
    }
}