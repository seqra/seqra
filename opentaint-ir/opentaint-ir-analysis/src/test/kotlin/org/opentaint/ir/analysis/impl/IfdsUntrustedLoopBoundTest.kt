package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.opentaint.ir.analysis.ifds.SingletonUnitResolver
import org.opentaint.ir.analysis.taint.TaintAnalysisOptions
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.analysis.UntrustedLoopBound
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class Ifds2UpperBoundTest : BaseAnalysisTest() {

    companion object : WithDB(Usages, InMemoryHierarchy)

    override val cp: JIRClasspath = runBlocking {
        val defaultConfigResource = this.javaClass.getResourceAsStream("/config_untrusted_loop_bound.json")
        if (defaultConfigResource != null) {
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    @Test
    fun `analyze untrusted upper bound`() {
        TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true
        testOneMethod<UntrustedLoopBound>("handle")
    }

    private inline fun <reified T> testOneMethod(methodName: String) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        val sinks = manager.analyze(listOf(method), timeout = 60.seconds)
        logger.info { "Sinks: ${sinks.size}" }
        for (sink in sinks) {
            logger.info { sink }
        }
        assertTrue(sinks.isNotEmpty())
    }
}
