package org.opentaint.ir.go.test

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Utility to compile and run Go source files as subprocesses.
 */
object GoRunner {

    /**
     * Compile and run a Go source file (must be in a directory with go.mod).
     * Returns stdout output.
     */
    fun compileAndRun(
        file: Path,
        timeout: Duration = Duration.ofSeconds(30),
    ): String {
        val dir = file.parent
        val tmpDir = Files.createTempDirectory("go-run")
        val binary = tmpDir.resolve("prog").toString()

        // Compile
        val compile = ProcessBuilder("go", "build", "-o", binary, file.fileName.toString())
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compile.inputStream.readAllBytes().decodeToString()
        val compiled = compile.waitFor(timeout.seconds, TimeUnit.SECONDS)
        check(compiled) { "Go compilation timed out after $timeout" }
        check(compile.exitValue() == 0) { "Go compilation failed:\n$compileOutput" }

        // Run
        val run = ProcessBuilder(binary)
            .redirectErrorStream(true)
            .start()
        val output = run.inputStream.readAllBytes().decodeToString()
        val exited = run.waitFor(timeout.seconds, TimeUnit.SECONDS)
        if (!exited) {
            run.destroyForcibly()
            throw IllegalStateException("Go process timed out after $timeout")
        }
        check(run.exitValue() == 0) { "Go execution failed (exit ${run.exitValue()}):\n$output" }

        // Cleanup
        tmpDir.toFile().deleteRecursively()

        return output
    }

    /**
     * Run `go run` on a Go source string. Writes to temp dir with go.mod.
     */
    fun runSource(
        source: String,
        timeout: Duration = Duration.ofSeconds(30),
    ): String {
        val tmpDir = Files.createTempDirectory("go-run-src")
        val goFile = tmpDir.resolve("main.go")
        goFile.toFile().writeText(source)
        tmpDir.resolve("go.mod").toFile().writeText("module gorun\ngo 1.22\n")

        val proc = ProcessBuilder("go", "run", "main.go")
            .directory(tmpDir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.readAllBytes().decodeToString()
        val exited = proc.waitFor(timeout.seconds, TimeUnit.SECONDS)
        if (!exited) {
            proc.destroyForcibly()
            throw IllegalStateException("Go run timed out after $timeout")
        }

        tmpDir.toFile().deleteRecursively()

        check(proc.exitValue() == 0) { "Go run failed (exit ${proc.exitValue()}):\n$output" }
        return output
    }

    /**
     * Check if Go is available on PATH.
     */
    fun isGoAvailable(): Boolean {
        return try {
            val proc = ProcessBuilder("go", "version")
                .redirectErrorStream(true)
                .start()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
