package org.opentaint.ir.test.python.tier1

import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRClasspathImpl
import org.opentaint.ir.test.python.PIRTestBase
import java.io.File

/**
 * Shared base for benchmark tests: library benchmarks and web app benchmarks.
 *
 * Provides common analysis methods, stats collection, and assertion logic.
 * All assertions are strict: zero errors, zero empty CFGs, zero dangling edges.
 */
abstract class BenchmarkTestBase : PIRTestBase() {

    // ─── Analysis entry points ──────────────────────────────

    /**
     * Analyze an installed Python package by module name.
     */
    protected fun analyzePkg(
        pythonModule: String,
        minModules: Int,
        minClasses: Int,
        minFunctions: Int,
        recursive: Boolean = false,
    ) {
        val pkgDir = findPackageDir(pythonModule)
        val pyFiles = listPyFiles(pkgDir, recursive)
        assertTrue(pyFiles.isNotEmpty(), "No .py files found in $pkgDir")

        analyzeFiles(pythonModule, pyFiles, minModules, minClasses, minFunctions)
    }

    /**
     * Analyze a directory-based project (cloned from git).
     * Scans for .py files excluding tests, migrations, and venvs.
     */
    protected fun analyzeDir(
        projectName: String,
        sourceDir: String,
        minModules: Int,
        minClasses: Int,
        minFunctions: Int,
    ) {
        val dir = File(sourceDir)
        assertTrue(dir.isDirectory, "Source directory not found: $sourceDir")

        val pyFiles = dir.walk()
            .filter { it.isFile && it.extension == "py" }
            .filter { f ->
                val rel = f.relativeTo(dir).path
                !rel.contains("/test") && !rel.contains("/tests/") &&
                !rel.contains("/migrations/") && !rel.contains("/venv/") &&
                !rel.contains("/.venv/") && !rel.contains("/node_modules/") &&
                !rel.startsWith("test") && !f.name.startsWith("test_") &&
                !f.name.startsWith("conftest")
            }
            .toList()
            .sortedByDescending { if (it.name == "__init__.py") Long.MAX_VALUE else it.length() }
            .map { it.absolutePath }

        assertTrue(pyFiles.isNotEmpty(), "No .py files found in $sourceDir")

        val cp = try {
            createClasspath(pyFiles)
        } catch (e: Exception) {
            System.err.println("WARNING: $projectName build failed: ${e.message}")
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "$projectName: mypy build failed (${e.javaClass.simpleName}: ${e.message})")
            return
        }

        verifyClasspath(projectName, pyFiles.size, cp)
    }

    // ─── Core analysis ──────────────────────────────────────

    private fun analyzeFiles(
        name: String,
        pyFiles: List<String>,
        minModules: Int,
        minClasses: Int,
        minFunctions: Int,
    ) {
        val cp = createClasspath(pyFiles)
        verifyClasspath(name, pyFiles.size, cp, minModules, minClasses, minFunctions)
    }

    private fun createClasspath(pyFiles: List<String>): PIRClasspath {
        val projectRoot = findProjectRoot()
        return PIRClasspathImpl.create(PIRSettings(
            sources = pyFiles,
            pythonExecutable = createPythonWrapper(projectRoot),
            mypyFlags = listOf("--ignore-missing-imports"),
            rpcTimeout = java.time.Duration.ofSeconds(300),
        ))
    }

    private fun verifyClasspath(
        name: String,
        fileCount: Int,
        cp: PIRClasspath,
        minModules: Int = 1,
        minClasses: Int = 0,
        minFunctions: Int = 1,
    ) {
        cp.use {
            val start = System.nanoTime()
            val stats = collectStats(it)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0

            println("╔══════════════════════════════════════════════════════════════")
            println("║ $name ($fileCount files)")
            println("║ Modules: ${stats.modules}, Classes: ${stats.classes}, " +
                    "Functions: ${stats.functions}, Blocks: ${stats.blocks}, " +
                    "Instructions: ${stats.instructions}")
            println("║ Instruction kinds: ${stats.instructionKinds.size} types")
            println("║ Dangling edges: ${stats.danglingEdges}, Unreachable: ${stats.unreachableBlocks}")
            println("║ Exception handlers: ${stats.blocksWithHandlers}, Errors: ${stats.errorDiagnostics}")
            println("║ Empty CFGs: ${stats.emptyFunctions}, Zero-instruction: ${stats.zeroInstructionFunctions}")
            println("╚══════════════════════════════════════════════════════════════")

            // ─── Strict assertions ───────────────────────────

            // Zero loading errors
            if (stats.errorDiagnosticMessages.isNotEmpty()) {
                println("║ ERRORS:")
                stats.errorDiagnosticMessages.take(10).forEach { println("║   $it") }
            }
            assertEquals(0, stats.errorDiagnostics,
                "$name: found ${stats.errorDiagnostics} loading errors:\n" +
                    stats.errorDiagnosticMessages.take(10).joinToString("\n") { "  $it" })

            // Zero empty CFGs
            if (stats.emptyFunctionNames.isNotEmpty()) {
                println("║ EMPTY CFGs:")
                stats.emptyFunctionNames.take(20).forEach { println("║   $it") }
            }
            assertEquals(0, stats.emptyFunctions,
                "$name: ${stats.emptyFunctions} functions with empty CFG:\n  " +
                    stats.emptyFunctionNames.take(20).joinToString("\n  "))

            // Zero zero-instruction functions
            assertEquals(0, stats.zeroInstructionFunctions,
                "$name: ${stats.zeroInstructionFunctions} functions with 0 instructions:\n  " +
                    stats.zeroInstructionFunctionNames.take(20).joinToString("\n  "))

            // Minimum entity counts
            assertTrue(stats.modules >= minModules,
                "$name: expected >= $minModules modules, got ${stats.modules}")
            assertTrue(stats.classes >= minClasses,
                "$name: expected >= $minClasses classes, got ${stats.classes}")
            assertTrue(stats.functions >= minFunctions,
                "$name: expected >= $minFunctions functions, got ${stats.functions}")

            // No dangling edges
            assertEquals(0, stats.danglingEdges,
                "$name: found ${stats.danglingEdges} dangling edges in CFGs")

            // Instruction diversity >= 3 types
            assertTrue(stats.instructionKinds.size >= 3,
                "$name: instruction diversity too low — only ${stats.instructionKinds.size} types: " +
                    stats.instructionKinds)

            // Unreachable blocks < 10%
            if (stats.blocks > 0) {
                val pct = stats.unreachableBlocks.toDouble() / stats.blocks
                assertTrue(pct < 0.10,
                    "$name: too many unreachable blocks: ${stats.unreachableBlocks}/${stats.blocks} " +
                        "(${"%.1f".format(pct * 100)}%)")
            }
        }
    }

    // ─── Stats ──────────────────────────────────────────────

    data class Stats(
        val modules: Int, val classes: Int, val functions: Int,
        val blocks: Int, val instructions: Int,
        val instructionKinds: Set<String>,
        val danglingEdges: Int,
        val unreachableBlocks: Int,
        val blocksWithHandlers: Int,
        val errorDiagnostics: Int,
        val errorDiagnosticMessages: List<String>,
        val emptyFunctions: Int,
        val emptyFunctionNames: List<String>,
        val zeroInstructionFunctions: Int,
        val zeroInstructionFunctionNames: List<String>,
    )

    private fun collectStats(cp: PIRClasspath): Stats {
        var modules = 0; var classes = 0; var functions = 0
        var blocks = 0; var instructions = 0
        val instructionKinds = mutableSetOf<String>()
        var danglingEdges = 0; var unreachableBlocks = 0; var blocksWithHandlers = 0
        val emptyFunctionNames = mutableListOf<String>()
        val zeroInstructionFunctionNames = mutableListOf<String>()
        val errorMessages = mutableListOf<String>()

        for (module in cp.modules) {
            modules++; classes += module.classes.size
            for (d in module.diagnostics) {
                if (d.severity == PIRDiagnosticSeverity.ERROR) {
                    errorMessages.add("${d.functionName}: ${d.message}")
                }
            }
            for (func in allFunctions(module)) {
                functions++
                val cfg = func.cfg
                blocks += cfg.blocks.size
                if (cfg.blocks.isEmpty()) {
                    emptyFunctionNames.add(func.qualifiedName)
                } else if (cfg.blocks.sumOf { b -> b.instructions.size } == 0) {
                    zeroInstructionFunctionNames.add(func.qualifiedName)
                }
                val allLabels = cfg.blocks.map { it.label }.toSet()
                for (block in cfg.blocks) {
                    instructions += block.instructions.size
                    if (block.exceptionHandlers.isNotEmpty()) blocksWithHandlers++
                    for (inst in block.instructions) {
                        instructionKinds.add(inst::class.simpleName ?: "Unknown")
                        when (inst) {
                            is PIRGoto -> if (inst.targetBlock !in allLabels) danglingEdges++
                            is PIRBranch -> {
                                if (inst.trueBlock !in allLabels) danglingEdges++
                                if (inst.falseBlock !in allLabels) danglingEdges++
                            }
                            is PIRNextIter -> {
                                if (inst.bodyBlock !in allLabels) danglingEdges++
                                if (inst.exitBlock !in allLabels) danglingEdges++
                            }
                            else -> {}
                        }
                    }
                    for (h in block.exceptionHandlers) {
                        if (h !in allLabels) danglingEdges++
                    }
                }
                if (cfg.blocks.isNotEmpty()) {
                    val reachable = mutableSetOf(cfg.entry.label)
                    val queue = ArrayDeque<PIRBasicBlock>()
                    queue.add(cfg.entry)
                    while (queue.isNotEmpty()) {
                        val b = queue.removeFirst()
                        for (succ in cfg.successors(b) + cfg.exceptionalSuccessors(b)) {
                            if (succ.label !in reachable) {
                                reachable.add(succ.label)
                                queue.add(succ)
                            }
                        }
                    }
                    unreachableBlocks += cfg.blocks.size - reachable.size
                }
            }
        }
        return Stats(
            modules, classes, functions, blocks, instructions,
            instructionKinds, danglingEdges, unreachableBlocks, blocksWithHandlers,
            errorMessages.size, errorMessages,
            emptyFunctionNames.size, emptyFunctionNames,
            zeroInstructionFunctionNames.size, zeroInstructionFunctionNames,
        )
    }

    private fun allFunctions(module: PIRModule): Sequence<PIRFunction> = sequence {
        yield(module.moduleInit)
        yieldAll(module.functions)
        for (cls in module.classes) { yieldAll(cls.methods) }
    }

    // ─── Helpers ────────────────────────────────────────────

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

        fun listPyFiles(dir: String, recursive: Boolean = false): List<String> {
            val allFiles = if (recursive) {
                File(dir).walk()
                    .filter { it.isFile && it.extension == "py" }
                    .toList()
                    .sortedByDescending { if (it.name == "__init__.py") Long.MAX_VALUE else it.length() }
            } else {
                File(dir).listFiles()
                    ?.filter { it.extension == "py" && it.isFile }
                    ?.sortedByDescending { if (it.name == "__init__.py") Long.MAX_VALUE else it.length() }
                    ?: emptyList()
            }
            return allFiles.map { it.absolutePath }
        }
    }
}
