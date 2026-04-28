package org.opentaint.ir.test.python.protoToFlat

import io.grpc.ManagedChannelBuilder
import org.opentaint.ir.api.python.PIRSettings
import org.opentaint.ir.impl.python.PIRProcessManager
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.protoToFlat.ProtoToFlat
import org.opentaint.ir.impl.python.proto.BuildProjectRequest
import org.opentaint.ir.impl.python.proto.PIRServiceGrpc
import org.opentaint.ir.impl.python.proto.PingRequest
import java.util.concurrent.TimeUnit

/**
 * Test-only helper. Boots the `pir_server` subprocess, performs the
 * ProtoToFlat lowering, and returns the raw `FlatModuleIR` list — without
 * passing the result through `FlatToPirConverter`. Used by tests that need
 * to assert on Flat IR directly.
 */
object PIRRawFlatLoader {
    fun loadRawFlatModules(settings: PIRSettings): List<FlatModuleIR> {
        val processManager = PIRProcessManager(
            pythonExecutable = settings.pythonExecutable,
            serverModule = settings.serverModule,
            startupTimeout = settings.serverStartupTimeout,
        )
        val port = processManager.start()

        val channel = ManagedChannelBuilder
            .forAddress("localhost", port)
            .usePlaintext()
            .maxInboundMessageSize(256 * 1024 * 1024)
            .build()

        try {
            val stub = PIRServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(settings.rpcTimeout.toMillis(), TimeUnit.MILLISECONDS)

            // Handshake — same retry pattern as PIRClasspathLoader.
            var lastException: Exception? = null
            for (attempt in 1..5) {
                if (!processManager.isRunning) error("pir_server died before ping")
                try {
                    PIRServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(10, TimeUnit.SECONDS)
                        .ping(PingRequest.getDefaultInstance())
                    lastException = null
                    break
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < 5) Thread.sleep(1000L * attempt)
                }
            }
            if (lastException != null) throw lastException

            val request = BuildProjectRequest.newBuilder()
                .addAllSources(settings.sources)
                .addAllMypyFlags(settings.mypyFlags)
                .setPythonVersion(settings.pythonVersion ?: "")
                .addAllSearchPaths(settings.searchPaths)
                .build()

            val iterator = stub.buildProject(request)
            val result = mutableListOf<FlatModuleIR>()
            while (iterator.hasNext()) {
                val astModuleProto = iterator.next()
                if (astModuleProto.errorsCount > 0) continue
                result.add(ProtoToFlat.lowerModule(astModuleProto))
            }
            return result
        } finally {
            channel.shutdownNow()
            channel.awaitTermination(5, TimeUnit.SECONDS)
            processManager.close()
        }
    }
}
