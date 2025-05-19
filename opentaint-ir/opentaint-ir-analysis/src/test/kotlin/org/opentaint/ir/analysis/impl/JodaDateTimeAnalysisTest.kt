package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.ifds.SingletonUnitResolver
import org.opentaint.ir.analysis.npe.NpeManager
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.analysis.unused.UnusedVariableManager
import org.opentaint.ir.analysis.util.JIRTraits
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.opentaint.ir.testing.allClasspath
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class JodaDateTimeAnalysisTest : BaseTest() {

    companion object : WithGlobalDB()

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

    private val graph: JIRApplicationGraph by lazy {
        runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
    }

    @Test
    fun `test taint analysis`() = with(JIRTraits) {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test NPE analysis`() = with(JIRTraits) {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = NpeManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test unused variables analysis`() = with(JIRTraits) {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = UnusedVariableManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Unused variables found: ${sinks.size}" }
    }
}
