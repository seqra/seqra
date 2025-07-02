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

import UntrustedLoopBound
import mu.KotlinLogging
import org.opentaint.ir.api.jvm.ext.findClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.dataflow.jvm.ifds.SingletonUnitResolver
import org.opentaint.dataflow.jvm.taint.jirTaintManager
import org.opentaint.dataflow.taint.TaintAnalysisOptions
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@TestInstance(PER_CLASS)
class Ifds2UpperBoundTest : BaseAnalysisTest(configFileName = "config_untrusted_loop_bound.json") {
    @Test
    fun `analyze untrusted upper bound`() {
        TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true
        testOneMethod<UntrustedLoopBound>("handle")
    }

    private inline fun <reified T> testOneMethod(methodName: String) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val unitResolver = SingletonUnitResolver
        val manager = jirTaintManager(graph, unitResolver)
        val sinks = manager.analyze(listOf(method), timeout = 60.seconds)
        logger.info { "Sinks: ${sinks.size}" }
        for (sink in sinks) {
            logger.info { sink }
        }
        assertTrue(sinks.isNotEmpty())
    }
}
