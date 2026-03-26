package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.proto.BuildProgramRequest
import org.opentaint.ir.go.proto.GoSSAServiceGrpc
import java.nio.file.Path

/**
 * Timing breakdown for a single IR build.
 */
data class BuildTimings(
    /** Total wall-clock time including gRPC overhead */
    val totalMs: Long,
    /** Time spent on the Go server (SSA build + serialization), from the Summary message */
    val serverBuildMs: Long,
    /** Time spent deserializing the gRPC stream on the Kotlin side */
    val deserializeMs: Long,
)

/**
 * Result of an IR build, containing both the program and timing info.
 */
data class BuildResult(
    val program: GoIRProgram,
    val timings: BuildTimings,
)

/**
 * High-level API for loading Go IR from Go source code.
 */
class GoIRClient : AutoCloseable {
    private val serverProcess = GoSsaServerProcess()
    private val channel = serverProcess.start()
    private val stub = GoSSAServiceGrpc.newBlockingStub(channel)

    /**
     * Build IR from a directory with Go source files.
     */
    fun buildFromDir(
        dir: Path,
        vararg patterns: String,
        instantiateGenerics: Boolean = true,
        sanityCheck: Boolean = true,
        includeStdlib: Boolean = false,
    ): GoIRProgram = buildFromDirWithTimings(dir, *patterns,
        instantiateGenerics = instantiateGenerics,
        sanityCheck = sanityCheck,
        includeStdlib = includeStdlib,
    ).program

    /**
     * Build IR from a directory, returning both the program and detailed timings.
     */
    fun buildFromDirWithTimings(
        dir: Path,
        vararg patterns: String,
        instantiateGenerics: Boolean = true,
        sanityCheck: Boolean = true,
        includeStdlib: Boolean = false,
    ): BuildResult {
        val request = BuildProgramRequest.newBuilder()
            .addAllPatterns(patterns.toList())
            .setWorkingDir(dir.toAbsolutePath().toString())
            .setInstantiateGenerics(instantiateGenerics)
            .setSanityCheck(sanityCheck)
            .setIncludeStdlib(includeStdlib)
            .build()

        val totalStart = System.nanoTime()
        val responses = stub.buildProgram(request)
        val deserializer = GoIRDeserializer()
        val deserializeStart = System.nanoTime()
        val program = deserializer.deserialize(responses)
        val deserializeMs = (System.nanoTime() - deserializeStart) / 1_000_000
        val totalMs = (System.nanoTime() - totalStart) / 1_000_000
        val serverBuildMs = deserializer.serverBuildTimeMs

        return BuildResult(
            program = program,
            timings = BuildTimings(
                totalMs = totalMs,
                serverBuildMs = serverBuildMs,
                deserializeMs = deserializeMs,
            ),
        )
    }

    /**
     * Build IR from inline Go source code.
     * Creates a temp directory, writes source, and loads it.
     */
    fun buildFromSource(
        source: String,
        packageName: String = "p",
    ): GoIRProgram {
        val tmpDir = java.nio.file.Files.createTempDirectory("goir-test")
        try {
            val goFile = tmpDir.resolve("$packageName.go")
            goFile.toFile().writeText(source)
            tmpDir.resolve("go.mod").toFile().writeText("module test/$packageName\ngo 1.22\n")
            return buildFromDir(tmpDir, "./...")
        } finally {
            // Don't delete — may be useful for debugging
        }
    }

    override fun close() {
        serverProcess.close()
    }
}
