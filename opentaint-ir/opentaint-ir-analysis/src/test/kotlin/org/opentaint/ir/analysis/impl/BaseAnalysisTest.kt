package org.opentaint.ir.analysis.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import juliet.support.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.VulnerabilityInstance
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.methods
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.opentaint.ir.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.streams.asStream

private val logger = KotlinLogging.logger {}

abstract class BaseAnalysisTest : BaseTest() {
    companion object : WithGlobalDB(UnknownClasses) {
        fun getJulietClasses(
            cweNum: Int,
            cweSpecificBans: List<String> = emptyList(),
        ): Sequence<String> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            hierarchyExt.findSubClasses(baseClass, false)
                .map { it.name }
                .filter { it.contains("CWE${cweNum}_") }
                .filterNot { className -> (commonJulietBans + cweSpecificBans).any { className.contains(it) } }
                .sorted()
        }

        @JvmStatic
        fun provideClassesForJuliet(
            cweNum: Int,
            cweSpecificBans: List<String> = emptyList(),
        ): Stream<Arguments> =
            getJulietClasses(cweNum, cweSpecificBans)
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

    override val cp: JIRClasspath = runBlocking {
        val configPath = "config_small.json"
        val defaultConfigResource = this.javaClass.getResourceAsStream("/$configPath")
        if (defaultConfigResource != null) {
            logger.info { "Loading '$configPath'..." }
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + BaseAnalysisTest.classpathFeatures)
        } else {
            super.cp
        }
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
            val sinksRepresentations = sinks.map { it.traceGraph.sink.toString() }
            assertTrue(sinksRepresentations.any { it.contains(expected) }) {
                "$expected unmatched in:\n${sinksRepresentations.joinToString("\n")}"
            }
        }
    }

    protected fun testSingleJulietClass(vulnerabilityType: String, className: String) {
        println(className)

        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val goodMethod = clazz.methods.single { it.name == "good" }

        val badIssues = findSinks(badMethod, vulnerabilityType)
        logger.info { "badIssues: ${badIssues.size} total" }
        for (issue in badIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(badIssues.isNotEmpty()) { "Must find some sinks in 'bad' for $className" }

        val goodIssues = findSinks(goodMethod, vulnerabilityType)
        logger.info { "goodIssues: ${goodIssues.size} total" }
        for (issue in goodIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(goodIssues.isEmpty()) { "Must NOT find any sinks in 'good' for $className" }
    }

    protected fun findSinks(method: JIRMethod, vulnerabilityType: String): Set<VulnerabilityInstance> {
        val vulnerabilities = launchAnalysis(listOf(method))
        val sinks = vulnerabilities
            .filter { it.location.vulnerabilityDescription.ruleId == vulnerabilityType }
        // .map { it.traceGraph.sink.toString() }

        return sinks.toSet()
    }
}
