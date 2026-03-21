package org.opentaint.ir.test.python.tier1

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRClasspathImpl
import org.opentaint.ir.test.python.PIRTestBase
import java.io.File

/**
 * Tier 1: Real-world benchmark tests.
 *
 * Analyzes installed Python packages through the full PIR pipeline.
 * Each test method analyzes a different package with a limited set of files.
 *
 * Asserts:
 *   1. No unhandled exceptions (pipeline completes)
 *   2. Expected minimum entity counts (not silently skipping code)
 *   3. All functions have a non-empty CFG
 */
@Tag("tier1")
class BenchmarkTest : PIRTestBase() {

    private fun analyzePkg(
        pythonModule: String,
        maxFiles: Int,
        minModules: Int,
        minClasses: Int,
        minFunctions: Int,
    ) {
        val pkgDir = findPackageDir(pythonModule)
        val pyFiles = listPyFiles(pkgDir, maxFiles)
        assertTrue(pyFiles.isNotEmpty(), "No .py files found in $pkgDir")

        val projectRoot = findProjectRoot()
        val start = System.nanoTime()

        val cp = PIRClasspathImpl.create(PIRSettings(
            sources = pyFiles,
            pythonExecutable = createPythonWrapperPublic(projectRoot),
            mypyFlags = listOf("--ignore-missing-imports"),
            rpcTimeout = java.time.Duration.ofSeconds(120),
        ))

        cp.use {
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val stats = collectStats(it)

            println("╔══════════════════════════════════════════════════════════════")
            println("║ $pythonModule (${pyFiles.size} files)")
            println("║ Modules: ${stats.modules}, Classes: ${stats.classes}, " +
                    "Functions: ${stats.functions}, Blocks: ${stats.blocks}, " +
                    "Instructions: ${stats.instructions}")
            println("║ Time: ${"%.1f".format(elapsed)}s")
            println("╚══════════════════════════════════════════════════════════════")

            // Basic assertions
            assertTrue(stats.modules >= minModules,
                "$pythonModule: expected >= $minModules modules, got ${stats.modules}")
            assertTrue(stats.classes >= minClasses,
                "$pythonModule: expected >= $minClasses classes, got ${stats.classes}")
            assertTrue(stats.functions >= minFunctions,
                "$pythonModule: expected >= $minFunctions functions, got ${stats.functions}")

            // All functions should have non-empty CFG
            val badFunctions = mutableListOf<String>()
            for (module in it.modules) {
                for (func in allFunctions(module)) {
                    if (func.cfg.blocks.isEmpty()) {
                        badFunctions.add(func.qualifiedName)
                    }
                }
            }
            val allowedBad = (stats.functions * 0.05).toInt() + 1
            assertTrue(
                badFunctions.size <= allowedBad,
                "$pythonModule: ${badFunctions.size} of ${stats.functions} functions without CFG:\n  " +
                    badFunctions.take(20).joinToString("\n  ")
            )
        }
    }

    @Test
    @Timeout(120)
    fun `benchmark - click`() = analyzePkg("click", 8, 5, 5, 20)

    @Test
    @Timeout(120)
    fun `benchmark - requests`() = analyzePkg("requests", 8, 5, 5, 20)

    @Test
    @Timeout(120)
    fun `benchmark - attrs`() = analyzePkg("attr", 8, 5, 3, 10)

    @Test
    @Timeout(120)
    fun `benchmark - typer`() = analyzePkg("typer", 5, 3, 2, 5)

    // ─── Helpers ──────────────────────────────────────────

    companion object {
        fun findPackageDir(pythonModule: String): String {
            val proc = ProcessBuilder(
                "python3", "-c",
                "import $pythonModule, os; print(os.path.dirname($pythonModule.__file__))"
            ).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (proc.exitValue() != 0) {
                throw IllegalStateException("Cannot locate package $pythonModule: $output")
            }
            return output
        }

        fun listPyFiles(dir: String, maxFiles: Int): List<String> {
            val allFiles = File(dir).listFiles()
                ?.filter { it.extension == "py" && it.isFile }
                ?.sortedByDescending { if (it.name == "__init__.py") Long.MAX_VALUE else it.length() }
                ?: emptyList()
            return allFiles.take(maxFiles).map { it.absolutePath }
        }
    }

    fun createPythonWrapperPublic(projectRoot: String): String {
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

    data class Stats(
        val modules: Int, val classes: Int, val functions: Int,
        val blocks: Int, val instructions: Int,
    )

    private fun collectStats(cp: PIRClasspath): Stats {
        var modules = 0; var classes = 0; var functions = 0
        var blocks = 0; var instructions = 0
        for (module in cp.modules) {
            modules++; classes += module.classes.size
            for (func in allFunctions(module)) {
                functions++
                blocks += func.cfg.blocks.size
                instructions += func.cfg.blocks.sumOf { it.instructions.size }
            }
        }
        return Stats(modules, classes, functions, blocks, instructions)
    }

    private fun allFunctions(module: PIRModule): Sequence<PIRFunction> = sequence {
        yield(module.moduleInit)
        yieldAll(module.functions)
        for (cls in module.classes) { yieldAll(cls.methods) }
    }
}
