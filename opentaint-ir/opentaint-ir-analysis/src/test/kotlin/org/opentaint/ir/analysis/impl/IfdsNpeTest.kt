package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.graph.JIRApplicationGraphImpl
import org.opentaint.ir.analysis.ifds.SingletonUnitResolver
import org.opentaint.ir.analysis.npe.NpeManager
import org.opentaint.ir.analysis.taint.Vulnerability
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.constructors
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.analysis.NpeExamples
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class IfdsNpeTest : BaseAnalysisTest() {

    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet476(): Stream<Arguments> =
            provideClassesForJuliet(476, listOf("null_check_after_deref"))

        @JvmStatic
        fun provideClassesForJuliet690(): Stream<Arguments> =
            provideClassesForJuliet(690)
    }

    override fun findSinks(methods: List<JIRMethod>): List<Vulnerability> {
        val unitResolver = SingletonUnitResolver
        val manager = NpeManager(graph, unitResolver)
        return manager.analyze(methods, timeout = 30.seconds)
    }

    @Test
    fun `fields resolving should work through interfaces`() = runBlocking {
        val graph = JIRApplicationGraphImpl(cp, cp.usagesExt())
        val callers = graph.callers(cp.findClass<StringTokenizer>().constructors[2])
        logger.debug { "callers: ${callers.toList().size}" }
    }

    @Test
    fun `analyze simple NPE`() {
        testOneMethod<NpeExamples>("npeOnLength", listOf("%3 = %0.length()"))
    }

    @Test
    fun `analyze no NPE`() {
        testOneMethod<NpeExamples>("noNPE", emptyList())
    }

    @Test
    fun `analyze NPE after fun with two exits`() {
        testOneMethod<NpeExamples>(
            "npeAfterTwoExits",
            listOf("%4 = %0.length()", "%5 = %1.length()")
        )
    }

    @Test
    fun `no NPE after checked access`() {
        testOneMethod<NpeExamples>("checkedAccess", emptyList())
    }

    @Disabled("Aliasing")
    @Test
    fun `no NPE after checked access with field`() {
        testOneMethod<NpeExamples>("checkedAccessWithField", emptyList())
    }

    @Test
    fun `consecutive NPEs handled properly`() {
        testOneMethod<NpeExamples>(
            "consecutiveNPEs",
            listOf("%2 = arg$0.length()", "%4 = arg$0.length()")
        )
    }

    @Test
    fun `npe on virtual call when possible`() {
        testOneMethod<NpeExamples>(
            "possibleNPEOnVirtualCall",
            listOf("%0 = arg$0.length()")
        )
    }

    @Test
    fun `no npe on virtual call when impossible`() {
        testOneMethod<NpeExamples>(
            "noNPEOnVirtualCall",
            emptyList()
        )
    }

    @Test
    fun `basic test for NPE on fields`() {
        testOneMethod<NpeExamples>("simpleNPEOnField", listOf("len2 = second.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `simple points-to analysis`() {
        testOneMethod<NpeExamples>("simplePoints2", listOf("%5 = %4.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `complex aliasing`() {
        testOneMethod<NpeExamples>("complexAliasing", listOf("%6 = %5.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `context injection in points-to`() {
        testOneMethod<NpeExamples>(
            "contextInjection",
            listOf("%6 = %5.length()", "%3 = %2.length()")
        )
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `activation points maintain flow sensitivity`() {
        testOneMethod<NpeExamples>("flowSensitive", listOf("%8 = %7.length()"))
    }

    @Test
    fun `overridden null assignment in callee don't affect next caller's instructions`() {
        testOneMethod<NpeExamples>("overriddenNullInCallee", emptyList())
    }

    @Test
    fun `recursive classes handled correctly`() {
        testOneMethod<NpeExamples>(
            "recursiveClass",
            listOf("%10 = %9.toString()", "%15 = %14.toString()")
        )
    }

    @Test
    fun `NPE on uninitialized array element dereferencing`() {
        testOneMethod<NpeExamples>("simpleArrayNPE", listOf("%5 = %4.length()"))
    }

    @Test
    fun `no NPE on array element dereferencing after initialization`() {
        testOneMethod<NpeExamples>("noNPEAfterArrayInit", emptyList())
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `array aliasing`() {
        testOneMethod<NpeExamples>("arrayAliasing", listOf("%5 = %4.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `mixed array and class aliasing`() {
        testOneMethod<NpeExamples>("mixedArrayClassAliasing", listOf("%13 = %12.length()"))
    }

    @Test
    fun `dereferencing field of null object`() {
        testOneMethod<NpeExamples>("npeOnFieldDeref", listOf("%1 = %0.field"))
    }

    @Test
    fun `dereferencing copy of value saved before null assignment produce no npe`() {
        testOneMethod<NpeExamples>("copyBeforeNullAssignment", emptyList())
    }

    @Test
    fun `assigning null to copy doesn't affect original value`() {
        testOneMethod<NpeExamples>("nullAssignmentToCopy", emptyList())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet476")
    fun `test on Juliet's CWE 476`(className: String) {
        testSingleJulietClass(className)
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet690")
    fun `test on Juliet's CWE 690`(className: String) {
        testSingleJulietClass(className)
    }

    @Test
    fun `test on specific Juliet's testcase`() {
        // val className = "juliet.testcases.CWE476_NULL_Pointer_Dereference.CWE476_NULL_Pointer_Dereference__Integer_01"
        // val className = "juliet.testcases.CWE690_NULL_Deref_From_Return.CWE690_NULL_Deref_From_Return__Class_StringBuilder_01"
        val className =
            "juliet.testcases.CWE690_NULL_Deref_From_Return.CWE690_NULL_Deref_From_Return__Properties_getProperty_equals_01"

        testSingleJulietClass(className)
    }

    @Test
    fun `analyse something`() {
        val testingMethod = cp.findClass<NpeExamples>().declaredMethods.single { it.name == "id" }
        val results = testingMethod.flowGraph()
        print(results)
    }

    private inline fun <reified T> testOneMethod(
        methodName: String,
        expectedLocations: Collection<String>,
    ) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(listOf(method))

        // TODO: think about better assertions here
        Assertions.assertEquals(expectedLocations.size, sinks.size)
        // expectedLocations.forEach { expected ->
        //     Assertions.assertTrue(sinks.map { it.traceGraph.sink.toString() }.any { it.contains(expected) })
        // }
    }
}
