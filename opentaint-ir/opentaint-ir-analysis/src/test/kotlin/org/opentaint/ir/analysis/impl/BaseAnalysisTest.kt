package org.opentaint.ir.analysis.impl

import juliet.support.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.taint.Vulnerability
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.methods
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.opentaint.ir.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.streams.asStream

private val logger = mu.KotlinLogging.logger {}

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
        val configFileName = "config_small.json"
        val configResource = this.javaClass.getResourceAsStream("/$configFileName")
        if (configResource != null) {
            val configJson = configResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    protected val graph: JIRApplicationGraph by lazy {
        runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
    }

    protected abstract fun findSinks(methods: List<JIRMethod>): List<Vulnerability>

    protected fun testSingleJulietClass(className: String) {
        logger.info { className }

        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val goodMethod = clazz.methods.single { it.name == "good" }

        logger.info { "Searching for sinks in BAD method: $badMethod" }
        val badIssues = findSinks(listOf(badMethod))
        logger.info { "Total ${badIssues.size} issues in BAD method" }
        for (issue in badIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(badIssues.isNotEmpty()) { "Must find some sinks in 'bad' for $className" }

        logger.info { "Searching for sinks in GOOD method: $goodMethod" }
        val goodIssues = findSinks(listOf(goodMethod))
        logger.info { "Total ${goodIssues.size} issues in GOOD method" }
        for (issue in goodIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(goodIssues.isEmpty()) { "Must NOT find any sinks in 'good' for $className" }
    }
}
