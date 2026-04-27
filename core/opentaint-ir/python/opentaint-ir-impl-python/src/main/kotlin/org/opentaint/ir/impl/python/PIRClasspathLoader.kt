package org.opentaint.ir.impl.python

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.builder.ProtoToFlatBuilder
import org.opentaint.ir.impl.python.converter.FlatToPirConverter
import org.opentaint.ir.impl.python.proto.BuildProjectRequest
import org.opentaint.ir.impl.python.proto.PIRServiceGrpc
import org.opentaint.ir.impl.python.proto.PingRequest
import java.util.concurrent.TimeUnit

/**
 * Boots the Python `pir_server` subprocess, opens the gRPC channel, performs
 * the version handshake, streams the project build, and assembles the result
 * into an immutable [PIRClasspathImpl].
 *
 * On any failure during loading, the underlying process and channel are
 * released before the exception propagates.
 */
class PIRClasspathLoader(private val settings: PIRSettings) {

    fun load(): PIRClasspathImpl {
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

            val versions = handshake(processManager, channel)

            val modules = if (settings.sources.isNotEmpty()) {
                buildModules(stub)
            } else {
                emptyList()
            }

            val index = indexModules(modules)

            return PIRClasspathImpl(
                processManager = processManager,
                channel = channel,
                stub = stub,
                pythonVersion = versions.pythonVersion,
                mypyVersion = versions.mypyVersion,
                modules = modules,
                modulesByName = index.modulesByName,
                classesByQName = index.classesByQName,
                functionsByQName = index.functionsByQName,
            )
        } catch (e: Throwable) {
            channel.shutdownNow()
            processManager.close()
            throw e
        }
    }

    private data class Versions(val pythonVersion: String, val mypyVersion: String)

    /**
     * Pings the server with retries — the gRPC channel may need time to connect
     * after the server reports READY.
     */
    private fun handshake(processManager: PIRProcessManager, channel: ManagedChannel): Versions {
        val maxRetries = 5
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            if (!processManager.isRunning) {
                throw PIRServerStartupException(
                    "Python server died before ping (attempt $attempt/$maxRetries)"
                )
            }
            try {
                val pingStub = PIRServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                val pingResponse = pingStub.ping(PingRequest.getDefaultInstance())
                return Versions(pingResponse.pythonVersion, pingResponse.mypyVersion)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    Thread.sleep(1000L * attempt)
                }
            }
        }
        throw lastException ?: IllegalStateException("Ping failed without exception")
    }

    private fun buildModules(stub: PIRServiceGrpc.PIRServiceBlockingStub): List<PIRModule> {
        val request = BuildProjectRequest.newBuilder()
            .addAllSources(settings.sources)
            .addAllMypyFlags(settings.mypyFlags)
            .setPythonVersion(settings.pythonVersion ?: "")
            .addAllSearchPaths(settings.searchPaths)
            .build()

        val iterator = stub.buildProject(request)
        val result = mutableListOf<PIRModule>()
        var count = 0
        var unknownCount = 0
        var lastLog = System.nanoTime()

        while (iterator.hasNext()) {
            val astModuleProto = iterator.next()

            // Modules with build errors become PIRUnknownModule
            if (astModuleProto.errorsCount > 0) {
                val diagnostics = astModuleProto.errorsList.map {
                    PIRDiagnostic(
                        PIRDiagnosticSeverity.ERROR,
                        it,
                        astModuleProto.name,
                        "MypyBuildError",
                    )
                }
                result.add(PIRUnknownModule(astModuleProto.name, diagnostics))
                unknownCount++
                continue
            }

            val flat = ProtoToFlatBuilder(astModuleProto).build()
            result.add(FlatToPirConverter(flat).convert())
            count++

            // Progress logging every 10 seconds
            val now = System.nanoTime()
            if (now - lastLog >= 10_000_000_000L) {
                System.err.println("PIR: Built $count modules ($unknownCount unknown)...")
                lastLog = now
            }
        }
        System.err.println("PIR: Finished. $count modules built, $unknownCount unknown.")
        return result
    }
}

private data class ModuleIndex(
    val modulesByName: Map<String, PIRModule>,
    val classesByQName: Map<String, PIRClass>,
    val functionsByQName: Map<String, PIRFunction>,
)

private fun indexModules(modules: List<PIRModule>): ModuleIndex {
    val modulesByName = mutableMapOf<String, PIRModule>()
    val classesByQName = mutableMapOf<String, PIRClass>()
    val functionsByQName = mutableMapOf<String, PIRFunction>()

    fun indexClass(cls: PIRClass) {
        classesByQName[cls.qualifiedName] = cls
        for (method in cls.methods) functionsByQName[method.qualifiedName] = method
        for (nested in cls.nestedClasses) indexClass(nested)
    }

    for (module in modules) {
        modulesByName[module.name] = module
        for (cls in module.classes) indexClass(cls)
        for (func in module.functions) functionsByQName[func.qualifiedName] = func
    }
    return ModuleIndex(modulesByName, classesByQName, functionsByQName)
}
