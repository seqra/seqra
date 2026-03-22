package org.opentaint.ir.impl.python

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.builder.MypyModuleBuilder
import org.opentaint.ir.impl.python.proto.PIRServiceGrpc
import org.opentaint.ir.impl.python.proto.BuildProjectRequest
import org.opentaint.ir.impl.python.proto.PingRequest
import org.opentaint.ir.impl.python.proto.ShutdownRequest
import org.opentaint.ir.impl.python.proto.ExecuteFunctionRequest
import org.opentaint.ir.impl.python.proto.ExecuteFunctionResponse
import java.util.concurrent.TimeUnit

class PIRClasspathImpl private constructor(
    private val processManager: PIRProcessManager,
    private val channel: ManagedChannel,
    internal val stub: PIRServiceGrpc.PIRServiceBlockingStub,
    private val settings: PIRSettings,
) : PIRClasspath {

    private val _modules = mutableListOf<PIRModule>()
    private val modulesByName = mutableMapOf<String, PIRModule>()
    private val classesByQName = mutableMapOf<String, PIRClass>()
    private val functionsByQName = mutableMapOf<String, PIRFunction>()

    override val modules: List<PIRModule> get() = _modules
    override var pythonVersion: String = ""
        private set
    override var mypyVersion: String = ""
        private set

    companion object {
        fun create(settings: PIRSettings): PIRClasspathImpl {
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

            val stub = PIRServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(settings.rpcTimeout.toMillis(), TimeUnit.MILLISECONDS)

            val classpath = PIRClasspathImpl(processManager, channel, stub, settings)

            // Ping with retries — the gRPC channel may need time to connect
            // after the server reports READY.
            val maxRetries = 5
            var lastException: Exception? = null
            for (attempt in 1..maxRetries) {
                if (!processManager.isRunning) {
                    processManager.close()
                    throw PIRServerStartupException(
                        "Python server died before ping (attempt $attempt/$maxRetries)"
                    )
                }
                try {
                    val pingStub = PIRServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(10, TimeUnit.SECONDS)
                    val pingResponse = pingStub.ping(PingRequest.getDefaultInstance())
                    classpath.pythonVersion = pingResponse.pythonVersion
                    classpath.mypyVersion = pingResponse.mypyVersion
                    lastException = null
                    break
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        Thread.sleep(1000L * attempt)
                    }
                }
            }
            if (lastException != null) {
                processManager.close()
                throw lastException
            }

            if (settings.sources.isNotEmpty()) {
                classpath.buildProject()
            }

            return classpath
        }
    }

    private fun buildProject() {
        val request = BuildProjectRequest.newBuilder()
            .addAllSources(settings.sources)
            .addAllMypyFlags(settings.mypyFlags)
            .setPythonVersion(settings.pythonVersion ?: "")
            .addAllSearchPaths(settings.searchPaths)
            .build()

        val iterator = stub.buildProject(request)
        while (iterator.hasNext()) {
            val astModuleProto = iterator.next()
            val builder = MypyModuleBuilder(astModuleProto, this)
            val module = builder.build()
            _modules.add(module)
            modulesByName[module.name] = module
            indexModule(module)
        }
    }

    private fun indexModule(module: PIRModule) {
        for (cls in module.classes) {
            indexClass(cls)
        }
        for (func in module.functions) {
            functionsByQName[func.qualifiedName] = func
        }
    }

    private fun indexClass(cls: PIRClass) {
        classesByQName[cls.qualifiedName] = cls
        for (method in cls.methods) {
            functionsByQName[method.qualifiedName] = method
        }
        for (nested in cls.nestedClasses) {
            indexClass(nested)
        }
    }

    override fun findModuleOrNull(name: String): PIRModule? = modulesByName[name]
    override fun findClassOrNull(qualifiedName: String): PIRClass? = classesByQName[qualifiedName]
    override fun findFunctionOrNull(qualifiedName: String): PIRFunction? = functionsByQName[qualifiedName]

    fun executeFunction(request: ExecuteFunctionRequest): ExecuteFunctionResponse {
        return stub.executeFunction(request)
    }

    override fun close() {
        try {
            try {
                stub.shutdown(ShutdownRequest.getDefaultInstance())
            } catch (_: Exception) {}
            channel.shutdown()
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow()
                channel.awaitTermination(3, TimeUnit.SECONDS)
            }
        } finally {
            processManager.close()
        }
    }
}
