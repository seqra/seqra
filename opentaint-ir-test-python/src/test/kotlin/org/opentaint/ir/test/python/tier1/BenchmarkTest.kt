package org.opentaint.ir.test.python.tier1

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
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
 *   4. CFG quality: at least 1 instruction per function on average
 */
@Tag("tier1")
class BenchmarkTest : PIRTestBase() {

    private fun analyzePkg(
        pythonModule: String,
        minModules: Int,
        minClasses: Int,
        minFunctions: Int,
        recursive: Boolean = false,
    ) {
        val pkgDir = findPackageDir(pythonModule)
        val pyFiles = listPyFiles(pkgDir, recursive)
        assertTrue(pyFiles.isNotEmpty(), "No .py files found in $pkgDir")

        val projectRoot = findProjectRoot()
        val start = System.nanoTime()

        val cp = PIRClasspathImpl.create(PIRSettings(
            sources = pyFiles,
            pythonExecutable = createPythonWrapperPublic(projectRoot),
            mypyFlags = listOf("--ignore-missing-imports"),
            rpcTimeout = java.time.Duration.ofSeconds(180),
        ))

        cp.use {
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val stats = collectStats(it)

            println("╔══════════════════════════════════════════════════════════════")
            println("║ $pythonModule (${pyFiles.size} files, recursive=$recursive)")
            println("║ Modules: ${stats.modules}, Classes: ${stats.classes}, " +
                    "Functions: ${stats.functions}, Blocks: ${stats.blocks}, " +
                    "Instructions: ${stats.instructions}")
            println("║ Instruction kinds: ${stats.instructionKinds.size} types")
            println("║ Dangling edges: ${stats.danglingEdges}, Unreachable blocks: ${stats.unreachableBlocks}")
            println("║ Blocks with exception handlers: ${stats.blocksWithHandlers}")
            println("║ Time: ${"%.1f".format(elapsed)}s")
            println("╚══════════════════════════════════════════════════════════════")

            // Verify no loading errors
            val errors = it.modules.flatMap { m -> m.diagnostics.filter { d -> d.severity == PIRDiagnosticSeverity.ERROR } }
            if (errors.isNotEmpty()) {
                println("║ ERRORS: ${errors.size}")
                errors.take(10).forEach { e -> println("║   ${e.functionName}: ${e.message}") }
            }
            assertEquals(0, errors.size,
                "$pythonModule: found ${errors.size} loading errors:\n" +
                    errors.take(10).joinToString("\n") { "  ${it.functionName}: ${it.message}" })

            // Basic assertions
            assertTrue(stats.modules >= minModules,
                "$pythonModule: expected >= $minModules modules, got ${stats.modules}")
            assertTrue(stats.classes >= minClasses,
                "$pythonModule: expected >= $minClasses classes, got ${stats.classes}")
            assertTrue(stats.functions >= minFunctions,
                "$pythonModule: expected >= $minFunctions functions, got ${stats.functions}")

            // CFG quality: track functions with empty CFG and those with 0 instructions
            val badFunctions = mutableListOf<String>()
            val zeroInstructionFunctions = mutableListOf<String>()
            for (module in it.modules) {
                for (func in allFunctions(module)) {
                    if (func.cfg.blocks.isEmpty()) {
                        badFunctions.add(func.qualifiedName)
                    } else if (func.cfg.blocks.sumOf { b -> b.instructions.size } == 0) {
                        zeroInstructionFunctions.add(func.qualifiedName)
                    }
                }
            }

            // Print summary of CFG build failures (0 instructions)
            val cfgFailures = badFunctions + zeroInstructionFunctions
            if (cfgFailures.isNotEmpty()) {
                println("║ ⚠ CFG quality: ${cfgFailures.size} of ${stats.functions} functions with 0 instructions:")
                cfgFailures.take(20).forEach { println("║   - $it") }
                if (cfgFailures.size > 20) {
                    println("║   ... and ${cfgFailures.size - 20} more")
                }
            }

            // Allow up to 5% + 1 functions without CFG blocks
            val allowedBad = (stats.functions * 0.05).toInt() + 1
            assertTrue(
                badFunctions.size <= allowedBad,
                "$pythonModule: ${badFunctions.size} of ${stats.functions} functions without CFG:\n  " +
                    badFunctions.take(20).joinToString("\n  ")
            )

            // CFG quality assertion: at least 1 instruction per function on average
            if (stats.functions > 0) {
                assertTrue(
                    stats.instructions >= stats.functions,
                    "$pythonModule: CFG quality too low — ${stats.instructions} total instructions " +
                        "across ${stats.functions} functions (expected >= 1 per function on average)"
                )
            }

            // No dangling edges (all goto/branch/nextiter targets must resolve)
            assertEquals(0, stats.danglingEdges,
                "$pythonModule: found ${stats.danglingEdges} dangling edges in CFGs")

            // Instruction diversity: real-world packages should use many instruction types.
            // At minimum: PIRReturn, PIRAssign, PIRCall — most use 10+ types
            assertTrue(stats.instructionKinds.size >= 3,
                "$pythonModule: instruction diversity too low — only ${stats.instructionKinds.size} types: " +
                    stats.instructionKinds)

            // Unreachable blocks should be rare (< 10% of total blocks).
            // Some dead merge blocks are expected from if/else where both branches return.
            if (stats.blocks > 0) {
                val unreachablePct = stats.unreachableBlocks.toDouble() / stats.blocks
                assertTrue(unreachablePct < 0.10,
                    "$pythonModule: too many unreachable blocks: ${stats.unreachableBlocks} of ${stats.blocks} " +
                        "(${"%.1f".format(unreachablePct * 100)}%)")
            }
        }
    }

    // ─── Existing benchmarks ─────────────────────────────────

    @Test
    @Timeout(600)
    fun `benchmark - click`() = analyzePkg("click", 5, 5, 20)

    @Test
    @Timeout(600)
    fun `benchmark - requests`() = analyzePkg("requests", 5, 5, 20)

    @Test
    @Timeout(600)
    fun `benchmark - attrs`() = analyzePkg("attr", 5, 3, 10)

    @Test
    @Timeout(600)
    fun `benchmark - typer`() = analyzePkg("typer", 3, 2, 5)

    // ─── New benchmarks ──────────────────────────────────────

    @Test
    @Timeout(600)
    fun `benchmark - rich`() = analyzePkg("rich", 8, 10, 30, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - pygments`() = analyzePkg("pygments", 7, 8, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - urllib3`() = analyzePkg("urllib3", 6, 5, 15, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - packaging`() = analyzePkg("packaging", 5, 3, 15)

    @Test
    @Timeout(600)
    fun `benchmark - cryptography`() = analyzePkg("cryptography", 6, 1, 10, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - more-itertools`() = analyzePkg("more_itertools", 2, 1, 5)

    @Test
    @Timeout(600)
    fun `benchmark - idna`() = analyzePkg("idna", 4, 2, 5)

    @Test
    @Timeout(600)
    fun `benchmark - charset-normalizer`() = analyzePkg("charset_normalizer", 5, 3, 10)

    @Test
    @Timeout(600)
    fun `benchmark - markdown-it`() = analyzePkg("markdown_it", 6, 3, 15, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - grpc`() = analyzePkg("grpc", 6, 5, 15, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - google-protobuf`() = analyzePkg("google.protobuf", 6, 5, 15, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - mypy`() = analyzePkg("mypy", 10, 10, 40, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - shellingham`() = analyzePkg("shellingham", 3, 1, 3)

    @Test
    @Timeout(600)
    fun `benchmark - mdurl`() = analyzePkg("mdurl", 3, 1, 5)

    @Test
    @Timeout(600)
    fun `benchmark - markupsafe`() = analyzePkg("markupsafe", 1, 1, 2)

    // certifi is a data-only package (no __file__ attribute), skip it

    // ─── Web framework libraries ─────────────────────────

    @Test
    @Timeout(600)
    fun `benchmark - flask`() = analyzePkg("flask", 10, 5, 30)

    @Test
    @Timeout(600)
    fun `benchmark - django`() = analyzePkg("django", 20, 1, 60, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - fastapi`() = analyzePkg("fastapi", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - starlette`() = analyzePkg("starlette", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - werkzeug`() = analyzePkg("werkzeug", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - jinja2`() = analyzePkg("jinja2", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - tornado`() = analyzePkg("tornado", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - falcon`() = analyzePkg("falcon", 10, 1, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - bottle`() = analyzePkg("bottle", 1, 1, 10)

    @Test
    @Timeout(600)
    fun `benchmark - pyramid`() = analyzePkg("pyramid", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - sanic`() = analyzePkg("sanic", 10, 1, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - aiohttp`() = analyzePkg("aiohttp", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - celery`() = analyzePkg("celery", 10, 5, 20, recursive = true)

    // ─── Data / Validation / ORM ─────────────────────────

    @Test
    @Timeout(600)
    fun `benchmark - pydantic`() = analyzePkg("pydantic", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - sqlalchemy`() = analyzePkg("sqlalchemy", 15, 20, 60, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - marshmallow`() = analyzePkg("marshmallow", 6, 3, 10, recursive = true)

    // ─── HTTP clients ────────────────────────────────────

    @Test
    @Timeout(600)
    fun `benchmark - httpx`() = analyzePkg("httpx", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - httpcore`() = analyzePkg("httpcore", 10, 3, 10, recursive = true)

    // ─── Utility libraries ───────────────────────────────

    @Test
    @Timeout(600)
    fun `benchmark - yaml`() = analyzePkg("yaml", 8, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - mako`() = analyzePkg("mako", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - markdown`() = analyzePkg("markdown", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - tqdm`() = analyzePkg("tqdm", 8, 3, 10, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - pycparser`() = analyzePkg("pycparser", 8, 5, 15, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - pyparsing`() = analyzePkg("pyparsing", 8, 5, 15, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - pathspec`() = analyzePkg("pathspec", 8, 3, 10, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - typeguard`() = analyzePkg("typeguard", 5, 3, 10, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - anyio`() = analyzePkg("anyio", 10, 5, 20, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - filelock`() = analyzePkg("filelock", 4, 2, 5, recursive = true)

    @Test
    @Timeout(600)
    fun `benchmark - lxml`() = analyzePkg("lxml", 8, 5, 15, recursive = true)

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
        val instructionKinds: Set<String>,
        val danglingEdges: Int,
        val unreachableBlocks: Int,
        val blocksWithHandlers: Int,
    )

    private fun collectStats(cp: PIRClasspath): Stats {
        var modules = 0; var classes = 0; var functions = 0
        var blocks = 0; var instructions = 0
        val instructionKinds = mutableSetOf<String>()
        var danglingEdges = 0
        var unreachableBlocks = 0
        var blocksWithHandlers = 0
        for (module in cp.modules) {
            modules++; classes += module.classes.size
            for (func in allFunctions(module)) {
                functions++
                val cfg = func.cfg
                blocks += cfg.blocks.size
                val allLabels = cfg.blocks.map { it.label }.toSet()
                for (block in cfg.blocks) {
                    instructions += block.instructions.size
                    if (block.exceptionHandlers.isNotEmpty()) blocksWithHandlers++
                    for (inst in block.instructions) {
                        instructionKinds.add(inst::class.simpleName ?: "Unknown")
                        // Check for dangling edges
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
                // Check reachability via BFS
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
        return Stats(modules, classes, functions, blocks, instructions,
            instructionKinds, danglingEdges, unreachableBlocks, blocksWithHandlers)
    }

    private fun allFunctions(module: PIRModule): Sequence<PIRFunction> = sequence {
        yield(module.moduleInit)
        yieldAll(module.functions)
        for (cls in module.classes) { yieldAll(cls.methods) }
    }
}
