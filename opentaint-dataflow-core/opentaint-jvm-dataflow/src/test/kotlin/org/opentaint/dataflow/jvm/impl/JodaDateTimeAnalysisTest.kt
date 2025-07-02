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

import org.opentaint.ir.api.jvm.ext.findClass
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.dataflow.jvm.ifds.SingletonUnitResolver
import org.opentaint.dataflow.jvm.npe.jirNpeManager
import org.opentaint.dataflow.jvm.taint.jirTaintManager
import org.opentaint.dataflow.jvm.unused.UnusedVariableManager
import org.opentaint.dataflow.jvm.util.JIRTraits
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

@TestInstance(PER_CLASS)
class JodaDateTimeAnalysisTest : BaseAnalysisTest() {
    companion object : JIRTraits

    @Test
    fun `test taint analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = jirTaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test NPE analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = jirNpeManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test unused variables analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = UnusedVariableManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Unused variables found: ${sinks.size}" }
    }
}
