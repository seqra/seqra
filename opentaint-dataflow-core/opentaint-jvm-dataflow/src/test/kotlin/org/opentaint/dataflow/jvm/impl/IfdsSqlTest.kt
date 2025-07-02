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

import SqlInjectionExamples
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.methods
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.opentaint.dataflow.jvm.ifds.ClassUnitResolver
import org.opentaint.dataflow.jvm.ifds.SingletonUnitResolver
import org.opentaint.dataflow.jvm.taint.jirTaintManager
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.sarif.sarifReportFromVulnerabilities
import org.opentaint.dataflow.taint.toSarif
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

@TestInstance(PER_CLASS)
class IfdsSqlTest : BaseAnalysisTest() {
    companion object: JIRTraits

    fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, specificBansCwe89)

    private val specificBansCwe89: List<String> = listOf(
        // Not working yet (#156)
        "s03", "s04"
    )

    @Test
    fun `simple SQL injection`() {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }
        val methods = listOf(method)
        val unitResolver = SingletonUnitResolver
        val manager = jirTaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 30.seconds)
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = manager.vulnerabilityTraceGraph(sink)
        val trace = graph.getAllTraces().first()
        assertTrue(trace.isNotEmpty())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(className) { method ->
            val unitResolver = SingletonUnitResolver
            val manager = jirTaintManager(graph, unitResolver)
            manager.analyze(listOf(method), timeout = 30.seconds)
        }
    }

    @Test
    fun `test on specific Juliet instance`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__connect_tcp_execute_01"
        testSingleJulietClass(className) { method ->
            val unitResolver = SingletonUnitResolver
            val manager = jirTaintManager(graph, unitResolver)
            manager.analyze(listOf(method), timeout = 30.seconds)
        }
    }

    @Test
    fun `test bidirectional runner and other stuff`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_51a"
        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val unitResolver = ClassUnitResolver(true)
        val manager = jirTaintManager(graph, unitResolver, useBidiRunner = true)
        val sinks = manager.analyze(listOf(badMethod), timeout = 30.seconds)
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = manager.vulnerabilityTraceGraph(sink)
        val trace = graph.getAllTraces().first()
        assertTrue(trace.isNotEmpty())
        val sarif = sarifReportFromVulnerabilities(listOf(sink.toSarif(graph)))

        val json = Json { prettyPrint = true }
        val sarifJson = json.encodeToString(sarif)
        logger.info { "SARIF:\n$sarifJson" }
    }
}
