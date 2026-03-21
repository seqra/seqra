package org.opentaint.ir.test.python.tier3

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRClasspathImpl
import org.opentaint.ir.impl.python.proto.ExecuteFunctionRequest
import org.opentaint.ir.test.python.PIRTestBase
import java.io.File
import java.nio.file.Files

/**
 * Tier 3 round-trip tests.
 *
 * All test functions are placed into a SINGLE Python file and analyzed once.
 * For each function:
 *   1. Parse original source → PIR CFG
 *   2. Reconstruct Python source from CFG
 *   3. Execute both original and reconstructed with same inputs
 *   4. Assert outputs match
 */
@Tag("tier3")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundTripTest : PIRTestBase() {

    private val reconstructor = PIRReconstructor()
    private val gson = Gson()
    private lateinit var cp: PIRClasspathImpl

    companion object {
        /** All test function sources concatenated into one module. */
        val ALL_SOURCES = """
def rt_simple_return(x: int) -> int:
    return x + 1

def rt_arithmetic(a: int, b: int) -> int:
    x = a + b
    y = x * 2
    z = y - a
    return z

def rt_if_else(x: int) -> str:
    if x > 0:
        result = "positive"
    elif x < 0:
        result = "negative"
    else:
        result = "zero"
    return result

def rt_factorial(n: int) -> int:
    result = 1
    i = 1
    while i <= n:
        result = result * i
        i = i + 1
    return result

def rt_sum_list(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    return total

def rt_count_positive(items: list) -> int:
    count = 0
    for x in items:
        if x > 0:
            count = count + 1
    return count

def rt_check(a: int, b: int) -> list:
    results = []
    if a == b:
        results = results + [1]
    if a != b:
        results = results + [2]
    if a < b:
        results = results + [3]
    if a >= b:
        results = results + [4]
    return results

def rt_logic(a: int, b: int) -> str:
    if a > 0 and b > 0:
        return "both positive"
    if a > 0 or b > 0:
        return "one positive"
    return "none positive"

def rt_greet(name: str) -> str:
    greeting = "Hello, " + name + "!"
    return greeting

def rt_make_list(a: int, b: int, c: int) -> list:
    return [a, b, c]
        """.trimIndent()
    }

    @BeforeAll
    fun setup() {
        val tmpDir = Files.createTempDirectory("pir-rt-test").toFile()
        tmpDir.deleteOnExit()
        val file = File(tmpDir, "__test__.py")
        file.writeText(ALL_SOURCES)
        file.deleteOnExit()

        val projectRoot = findProjectRoot()

        cp = PIRClasspathImpl.create(PIRSettings(
            sources = listOf(file.absolutePath),
            pythonExecutable = createPythonWrapper(projectRoot),
            mypyFlags = listOf("--ignore-missing-imports"),
        ))
    }

    @AfterAll
    fun tearDown() {
        cp.close()
    }

    /**
     * Create a wrapper script that sets PYTHONPATH before invoking Python.
     */
    private fun createPythonWrapper(projectRoot: String): String {
        val wrapper = File.createTempFile("pir-python-", ".sh")
        wrapper.deleteOnExit()
        wrapper.writeText("""
            #!/bin/bash
            export PYTHONPATH="$projectRoot:${'$'}PYTHONPATH"
            exec python3 "${'$'}@"
        """.trimIndent())
        wrapper.setExecutable(true)
        return wrapper.absolutePath
    }

    private fun executeFunction(
        source: String,
        funcName: String,
        inputs: List<Pair<List<Any?>, Map<String, Any?>>>
    ): List<Map<String, Any?>> {
        val argsJson = gson.toJson(inputs.map { (args, kwargs) -> listOf(args, kwargs) })
        val request = ExecuteFunctionRequest.newBuilder()
            .setSourceCode(source)
            .setFunctionName(funcName)
            .setArgumentsJson(argsJson)
            .build()
        val response = cp.executeFunction(request)
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        return gson.fromJson(response.resultsJson, type)
    }

    private fun roundTrip(
        funcName: String,
        inputs: List<Pair<List<Any?>, Map<String, Any?>>>
    ) {
        val qualifiedName = "__test__.$funcName"
        val func = cp.findFunctionOrNull(qualifiedName)
        assertNotNull(func, "Function $qualifiedName not found")

        // Reconstruct Python from CFG
        val reconstructed = reconstructor.reconstruct(func!!)

        // Execute original
        val originalResults = executeFunction(ALL_SOURCES, funcName, inputs)

        // Execute reconstructed
        val reconstructedResults = executeFunction(reconstructed, funcName, inputs)

        // Compare
        for ((i, input) in inputs.withIndex()) {
            val original = originalResults[i]
            val recon = reconstructedResults[i]
            assertEquals(
                original["value"], recon["value"],
                "Mismatch for input $input:\n" +
                    "  original: ${original["value"]}\n" +
                    "  reconstructed: ${recon["value"]}\n" +
                    "  reconstructed source:\n$reconstructed"
            )
            assertEquals(
                original["exception"], recon["exception"],
                "Exception mismatch for input $input"
            )
        }
    }

    private fun posArgs(vararg argSets: List<Any?>): List<Pair<List<Any?>, Map<String, Any?>>> {
        return argSets.map { it to emptyMap() }
    }

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `round trip - simple return`() = roundTrip("rt_simple_return",
        posArgs(listOf(0), listOf(5), listOf(-1), listOf(100)))

    @Test fun `round trip - arithmetic`() = roundTrip("rt_arithmetic",
        posArgs(listOf(1, 2), listOf(0, 0), listOf(10, -5), listOf(3, 7)))

    @Test fun `round trip - if-else`() = roundTrip("rt_if_else",
        posArgs(listOf(5), listOf(-3), listOf(0), listOf(100)))

    @Test fun `round trip - while loop`() = roundTrip("rt_factorial",
        posArgs(listOf(0), listOf(1), listOf(5), listOf(7)))

    @Test fun `round trip - for loop sum`() = roundTrip("rt_sum_list",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>()), listOf(listOf(10, -5, 3))))

    @Test fun `round trip - nested if in loop`() = roundTrip("rt_count_positive",
        posArgs(listOf(listOf(1, -2, 3, -4, 5)), listOf(emptyList<Int>()), listOf(listOf(-1, -2, -3))))

    @Test fun `round trip - comparison operators`() = roundTrip("rt_check",
        posArgs(listOf(1, 1), listOf(1, 2), listOf(3, 1)))

    @Test fun `round trip - boolean logic`() = roundTrip("rt_logic",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `round trip - string operations`() = roundTrip("rt_greet",
        posArgs(listOf("World"), listOf("Alice"), listOf("")))

    @Test fun `round trip - list building`() = roundTrip("rt_make_list",
        posArgs(listOf(1, 2, 3), listOf(0, 0, 0), listOf(-1, 0, 1)))
}
