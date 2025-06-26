package org.opentaint.ir.analysis.impl

import org.opentaint.ir.analysis.ifds.SingletonUnitResolver
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.analysis.taint.TaintVulnerability
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.testing.analysis.TaintExamples
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class IfdsTaintTest : BaseAnalysisTest() {

    @Test
    fun `analyze simple taint on bad method`() {
        testOneMethod<TaintExamples>("bad")
    }

    private fun findSinks(method: JIRMethod): List<TaintVulnerability<JIRInst>> {
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        return manager.analyze(listOf(method), timeout = 3000.seconds)
    }

    private inline fun <reified T> testOneMethod(methodName: String) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method)
        logger.info { "Sinks: ${sinks.size}" }
        for ((i, sink) in sinks.withIndex()) {
            logger.info { "[${i + 1}/${sinks.size}]: ${sink.sink}" }
        }
        assertTrue(sinks.isNotEmpty())
    }
}
