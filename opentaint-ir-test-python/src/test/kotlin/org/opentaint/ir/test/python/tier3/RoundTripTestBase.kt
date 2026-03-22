package org.opentaint.ir.test.python.tier3

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRClasspathImpl
import org.opentaint.ir.impl.python.proto.ExecuteFunctionRequest
import org.opentaint.ir.test.python.PIRTestBase
import java.io.File
import java.nio.file.Files

/**
 * Base class for round-trip tests.
 *
 * Subclasses provide [allSources] — a single Python module containing all test functions.
 * The base class handles classpath creation, function execution, and round-trip verification.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class RoundTripTestBase : PIRTestBase() {

    protected val reconstructor = PIRReconstructor()
    protected val gson = Gson()
    protected lateinit var cp: PIRClasspathImpl

    /** All Python source functions concatenated into one module. */
    abstract val allSources: String

    @BeforeAll
    fun setup() {
        val tmpDir = Files.createTempDirectory("pir-rt-test").toFile()
        tmpDir.deleteOnExit()
        val file = File(tmpDir, "__test__.py")
        file.writeText(allSources)
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

    protected fun executeFunction(
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

    /**
     * Core round-trip verification:
     * 1. Look up function in PIR classpath
     * 2. Reconstruct Python from CFG
     * 3. Execute both original and reconstructed with same inputs
     * 4. Assert outputs match
     */
    protected fun roundTrip(
        funcName: String,
        inputs: List<Pair<List<Any?>, Map<String, Any?>>>
    ) {
        val qualifiedName = "__test__.$funcName"
        val func = cp.findFunctionOrNull(qualifiedName)
        assertNotNull(func, "Function $qualifiedName not found")

        val reconstructed = reconstructor.reconstruct(func!!)

        val originalResults = executeFunction(allSources, funcName, inputs)
        val reconstructedResults = executeFunction(reconstructed, funcName, inputs)

        for ((i, input) in inputs.withIndex()) {
            val original = originalResults[i]
            val recon = reconstructedResults[i]
            assertEquals(
                original["value"], recon["value"],
                "Mismatch for $funcName input $input:\n" +
                    "  original: ${original["value"]}\n" +
                    "  reconstructed: ${recon["value"]}\n" +
                    "  reconstructed source:\n$reconstructed"
            )
            assertEquals(
                original["exception"], recon["exception"],
                "Exception mismatch for $funcName input $input"
            )
        }
    }

    /**
     * Round-trip verification for functions that contain lambda expressions.
     * Uses [PIRReconstructor.reconstructWithLambdas] to also emit reconstructed
     * lambda functions referenced from the main function's CFG.
     */
    protected fun roundTripWithLambdas(
        funcName: String,
        inputs: List<Pair<List<Any?>, Map<String, Any?>>>
    ) {
        val qualifiedName = "__test__.$funcName"
        val func = cp.findFunctionOrNull(qualifiedName)
        assertNotNull(func, "Function $qualifiedName not found")

        val reconstructed = reconstructor.reconstructWithLambdas(func!!, cp)

        val originalResults = executeFunction(allSources, funcName, inputs)
        // The reconstructed function name is sanitized the same way
        val reconstructedResults = executeFunction(reconstructed, funcName, inputs)

        for ((i, input) in inputs.withIndex()) {
            val original = originalResults[i]
            val recon = reconstructedResults[i]
            assertEquals(
                original["value"], recon["value"],
                "Mismatch for $funcName input $input:\n" +
                    "  original: ${original["value"]}\n" +
                    "  reconstructed: ${recon["value"]}\n" +
                    "  reconstructed source:\n$reconstructed"
            )
            assertEquals(
                original["exception"], recon["exception"],
                "Exception mismatch for $funcName input $input"
            )
        }
    }

    /** Helper to create positional-only argument sets. */
    protected fun posArgs(vararg argSets: List<Any?>): List<Pair<List<Any?>, Map<String, Any?>>> {
        return argSets.map { it to emptyMap() }
    }
}
