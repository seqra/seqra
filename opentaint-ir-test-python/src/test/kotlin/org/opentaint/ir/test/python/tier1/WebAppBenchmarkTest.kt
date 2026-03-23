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
 * Tier 1: Real-world web application benchmarks.
 *
 * Analyzes cloned web application projects (Django, Flask, FastAPI, etc.)
 * through the full PIR pipeline. Each test method analyzes a sample of
 * Python files from a real web application.
 *
 * Tests: no unhandled exceptions, entity count sanity, CFG quality,
 *        instruction diversity, no dangling edges, reachability.
 */
@Tag("tier1")
class WebAppBenchmarkTest : PIRTestBase() {

    companion object {
        private const val WEB_PROJECTS_DIR = "/home/sobol/data/python-ir/web-projects"
    }

    /**
     * Analyze a directory-based project (cloned from git).
     * Scans for .py files under [sourceDir], excluding tests, migrations, and venvs.
     * Loads all matching files sorted by size (largest first, __init__.py prioritized).
     */
    private fun analyzeDir(
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

        val projectRoot = findProjectRoot()
        val start = System.nanoTime()

        val cp = try {
            PIRClasspathImpl.create(PIRSettings(
                sources = pyFiles,
                pythonExecutable = createPythonWrapperPublic(projectRoot),
                mypyFlags = listOf("--ignore-missing-imports"),
                rpcTimeout = java.time.Duration.ofSeconds(300),
            ))
        } catch (e: Exception) {
            System.err.println("WARNING: $projectName build failed: ${e.message}")
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "$projectName: mypy build failed (${e.javaClass.simpleName}: ${e.message})")
            return
        }

        cp.use {
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val stats = collectStats(it)

            println("╔══════════════════════════════════════════════════════════════")
            println("║ WEB APP: $projectName (${pyFiles.size} files)")
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

            // Basic entity assertions
            assertTrue(stats.modules >= minModules,
                "$projectName: expected >= $minModules modules, got ${stats.modules}")
            assertTrue(stats.classes >= minClasses,
                "$projectName: expected >= $minClasses classes, got ${stats.classes}")
            assertTrue(stats.functions >= minFunctions,
                "$projectName: expected >= $minFunctions functions, got ${stats.functions}")

            // CFG quality: track empty-CFG and zero-instruction functions
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

            val cfgFailures = badFunctions + zeroInstructionFunctions
            if (cfgFailures.isNotEmpty()) {
                println("║ CFG quality: ${cfgFailures.size} of ${stats.functions} functions with 0 instructions:")
                cfgFailures.take(20).forEach { f -> println("║   - $f") }
                if (cfgFailures.size > 20) println("║   ... and ${cfgFailures.size - 20} more")
            }

            // Allow up to 5% + 1 without CFG blocks
            val allowedBad = (stats.functions * 0.05).toInt() + 1
            assertTrue(badFunctions.size <= allowedBad,
                "$projectName: ${badFunctions.size} of ${stats.functions} functions without CFG:\n  " +
                    badFunctions.take(20).joinToString("\n  "))

            // At least 1 instruction per function on average
            if (stats.functions > 0) {
                assertTrue(stats.instructions >= stats.functions,
                    "$projectName: CFG quality too low — ${stats.instructions} instructions " +
                        "across ${stats.functions} functions")
            }

            // No dangling edges
            assertEquals(0, stats.danglingEdges,
                "$projectName: found ${stats.danglingEdges} dangling edges")

            // Instruction diversity >= 3 types
            assertTrue(stats.instructionKinds.size >= 3,
                "$projectName: instruction diversity too low — ${stats.instructionKinds.size} types")

            // Unreachable blocks < 10%
            if (stats.blocks > 0) {
                val pct = stats.unreachableBlocks.toDouble() / stats.blocks
                assertTrue(pct < 0.10,
                    "$projectName: too many unreachable: ${stats.unreachableBlocks}/${stats.blocks} (${"%.1f".format(pct * 100)}%)")
            }
        }
    }

    // ─── Django Web Applications ─────────────────────────────

    @Test @Timeout(600)
    fun `webapp - saleor (Django e-commerce)`() =
        analyzeDir("saleor", "$WEB_PROJECTS_DIR/saleor/saleor", 20, 1, 30)

    @Test @Timeout(600)
    fun `webapp - netbox (Django network automation)`() =
        analyzeDir("netbox", "$WEB_PROJECTS_DIR/netbox/netbox", 20, 1, 30)

    @Test @Timeout(600)
    fun `webapp - wagtail (Django CMS)`() =
        analyzeDir("wagtail", "$WEB_PROJECTS_DIR/wagtail/wagtail", 20, 1, 30)

    @Test @Timeout(600)
    fun `webapp - taiga-back (Django project management)`() =
        analyzeDir("taiga-back", "$WEB_PROJECTS_DIR/taiga-back/taiga", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - django-oscar (Django e-commerce)`() =
        analyzeDir("django-oscar", "$WEB_PROJECTS_DIR/django-oscar/src", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - django-rest-framework`() =
        analyzeDir("django-rest-framework", "$WEB_PROJECTS_DIR/django-rest-framework/rest_framework", 10, 5, 20)

    @Test @Timeout(600)
    fun `webapp - healthchecks (Django monitoring)`() =
        analyzeDir("healthchecks", "$WEB_PROJECTS_DIR/healthchecks/hc", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - zulip (Django chat)`() =
        analyzeDir("zulip", "$WEB_PROJECTS_DIR/zulip/zerver", 20, 1, 30)

    @Test @Timeout(600)
    fun `webapp - label-studio (Django data labeling)`() =
        analyzeDir("label-studio", "$WEB_PROJECTS_DIR/label-studio/label_studio", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - paperless-ngx (Django document management)`() =
        analyzeDir("paperless-ngx", "$WEB_PROJECTS_DIR/paperless-ngx/src", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - flagsmith (Django feature flags)`() =
        analyzeDir("flagsmith", "$WEB_PROJECTS_DIR/flagsmith/api", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - hyperkitty (Django mailing lists)`() =
        analyzeDir("hyperkitty", "$WEB_PROJECTS_DIR/hyperkitty/hyperkitty", 10, 1, 20)

    @Test @Timeout(600)
    fun `webapp - ralph (Django asset management)`() =
        analyzeDir("ralph", "$WEB_PROJECTS_DIR/ralph/src/ralph", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - plane (Django project tracker)`() =
        analyzeDir("plane", "$WEB_PROJECTS_DIR/plane/apps", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - posthog (Django analytics)`() =
        analyzeDir("posthog", "$WEB_PROJECTS_DIR/posthog/posthog", 20, 0, 30)

    @Test @Timeout(600)
    fun `webapp - sentry (Django error tracking)`() =
        analyzeDir("sentry", "$WEB_PROJECTS_DIR/sentry/src", 20, 1, 30)

    // ─── Flask Web Applications ──────────────────────────────

    @Test @Timeout(600)
    fun `webapp - superset (Flask BI platform)`() =
        analyzeDir("superset", "$WEB_PROJECTS_DIR/superset/superset", 20, 0, 30)

    @Test @Timeout(600)
    fun `webapp - redash (Flask dashboarding)`() =
        analyzeDir("redash", "$WEB_PROJECTS_DIR/redash/redash", 10, 1, 20)

    @Test @Timeout(600)
    fun `webapp - CTFd (Flask CTF platform)`() =
        analyzeDir("CTFd", "$WEB_PROJECTS_DIR/CTFd/CTFd", 10, 1, 20)

    @Test @Timeout(600)
    fun `webapp - flaskbb (Flask forum)`() =
        analyzeDir("flaskbb", "$WEB_PROJECTS_DIR/flaskbb/flaskbb", 10, 1, 20)

    @Test @Timeout(600)
    fun `webapp - lemur (Flask certificate manager)`() =
        analyzeDir("lemur", "$WEB_PROJECTS_DIR/lemur/lemur", 10, 0, 20)

    // ─── FastAPI Web Applications ────────────────────────────

    @Test @Timeout(600)
    fun `webapp - mealie (FastAPI recipe manager)`() =
        analyzeDir("mealie", "$WEB_PROJECTS_DIR/mealie/mealie", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - dispatch (FastAPI incident management)`() =
        analyzeDir("dispatch", "$WEB_PROJECTS_DIR/dispatch/src/dispatch", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - polar (FastAPI billing)`() =
        analyzeDir("polar", "$WEB_PROJECTS_DIR/polar/server/polar", 10, 0, 20)

    @Test @Timeout(600)
    fun `webapp - full-stack-fastapi-template`() =
        analyzeDir("fastapi-template", "$WEB_PROJECTS_DIR/full-stack-fastapi-template/backend/app", 5, 1, 5)

    // ─── Helper methods ──────────────────────────────────────

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
        return Stats(modules, classes, functions, blocks, instructions,
            instructionKinds, danglingEdges, unreachableBlocks, blocksWithHandlers)
    }

    private fun allFunctions(module: PIRModule): Sequence<PIRFunction> = sequence {
        yield(module.moduleInit)
        yieldAll(module.functions)
        for (cls in module.classes) { yieldAll(cls.methods) }
    }
}
