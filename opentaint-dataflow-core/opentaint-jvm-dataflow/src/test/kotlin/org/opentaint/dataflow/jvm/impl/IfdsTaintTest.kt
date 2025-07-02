/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.jvm.impl

import TaintExamples
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.dataflow.jvm.ifds.SingletonUnitResolver
import org.opentaint.dataflow.jvm.taint.jirTaintManager
import org.opentaint.dataflow.taint.TaintVulnerability
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

@TestInstance(PER_CLASS)
class IfdsTaintTest : BaseAnalysisTest() {

    @Test
    fun `analyze simple taint on bad method`() {
        testOneMethod<TaintExamples>("bad")
    }

    private fun findSinks(method: JIRMethod): List<TaintVulnerability<JIRInst>> {
        val unitResolver = SingletonUnitResolver
        val manager = jirTaintManager(graph, unitResolver)
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
