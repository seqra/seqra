package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.proto.BuildProgramRequest
import org.opentaint.ir.go.proto.GoSSAServiceGrpc
import java.nio.file.Path

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
    ): GoIRProgram {
        val request = BuildProgramRequest.newBuilder()
            .addAllPatterns(patterns.toList())
            .setWorkingDir(dir.toAbsolutePath().toString())
            .setInstantiateGenerics(instantiateGenerics)
            .setSanityCheck(sanityCheck)
            .setIncludeStdlib(includeStdlib)
            .build()

        val responses = stub.buildProgram(request)
        return GoIRDeserializer().deserialize(responses)
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
