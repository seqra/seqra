package org.opentaint.ir.analysis.impl

import org.opentaint.ir.analysis.ifds.SingletonUnitResolver
import org.opentaint.ir.analysis.taint.TaintManager
import org.opentaint.ir.analysis.taint.Vulnerability
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.testing.WithGlobalDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class JodaDateTimeAnalysisTest : BaseAnalysisTest() {

    companion object : WithGlobalDB()

    @Test
    fun `test taint analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val sinks = findSinks(methods)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    override fun findSinks(methods: List<JIRMethod>): List<Vulnerability> {
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        return sinks
    }
}
