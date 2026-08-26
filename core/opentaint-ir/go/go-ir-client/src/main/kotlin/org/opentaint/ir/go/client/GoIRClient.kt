package org.opentaint.ir.go.client

import io.grpc.StatusRuntimeException
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.proto.BuildProgramRequest
import org.opentaint.ir.go.proto.GoSSAServiceGrpc
import org.opentaint.ir.go.proto.LoadMode
import java.nio.file.Path

data class BuildTimings(
    val totalMs: Long,
    val serverBuildMs: Long,
    val deserializeMs: Long,
)

data class BuildResult(
    val program: GoIRProgram,
    val timings: BuildTimings,
)

class GoIRClient : AutoCloseable {
    companion object {
        init {
            GoIRGrpcLogging.quietNoisyGrpcLogs()
        }
    }

    private val serverProcess = GoSsaServerProcess()
    private val channel = serverProcess.start()
    private val stub = GoSSAServiceGrpc.newBlockingStub(channel)

    fun buildFromDir(dir: Path, config: GoIRLoadConfig = GoIRLoadConfig()): BuildResult {
        val protoMode = when (config.mode) {
            GoIRLoadMode.FULL -> LoadMode.LOAD_MODE_FULL
            GoIRLoadMode.PROJECT -> LoadMode.LOAD_MODE_PROJECT
        }
        val request = BuildProgramRequest.newBuilder()
            .addAllPatterns(config.patterns)
            .setWorkingDir(dir.toAbsolutePath().toString())
            .setMode(protoMode)
            .addAllProjectModulePaths(config.projectModules)
            .setInstantiateGenerics(config.instantiateGenerics)
            .setSanityCheck(config.sanityCheck)
            .addAllModelDirs(config.modelDirs.map { it.toAbsolutePath().toString() })
            .build()

        val totalStart = System.nanoTime()
        val deserializer = GoIRDeserializer()
        val deserializeStart = System.nanoTime()
        val program = try {
            val responses = stub.buildProgram(request)
            deserializer.deserialize(responses)
        } catch (e: StatusRuntimeException) {
            // gRPC only says e.g. "UNAVAILABLE: Network closed" when the server
            // process dies mid-stream. Attach the server's exit state and stderr
            // tail so the actual crash (panic, fatal runtime error, OOM) is visible.
            throw GoIRServerException(
                "go-ssa-server RPC failed (${e.status.code}): ${e.status.description}\n" +
                    serverProcess.diagnostics(),
                e,
            )
        }
        val deserializeMs = (System.nanoTime() - deserializeStart) / 1_000_000
        val totalMs = (System.nanoTime() - totalStart) / 1_000_000
        return BuildResult(
            program = program,
            timings = BuildTimings(totalMs, deserializer.serverBuildTimeMs, deserializeMs),
        )
    }

    fun buildFromSource(
        source: String,
        packageName: String = "p",
        config: GoIRLoadConfig = GoIRLoadConfig(),
    ): GoIRProgram {
        val tmpDir = java.nio.file.Files.createTempDirectory("goir-test")
        val goFile = tmpDir.resolve("$packageName.go")
        goFile.toFile().writeText(source)
        tmpDir.resolve("go.mod").toFile().writeText("module test/$packageName\ngo 1.22\n")
        return buildFromDir(tmpDir, config).program
    }

    override fun close() {
        serverProcess.close()
    }
}
