package org.opentaint.ir.impl.python

import io.grpc.ManagedChannel
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.proto.PIRServiceGrpc
import org.opentaint.ir.impl.python.proto.ShutdownRequest
import org.opentaint.ir.impl.python.proto.ExecuteFunctionRequest
import org.opentaint.ir.impl.python.proto.ExecuteFunctionResponse
import java.util.concurrent.TimeUnit

class PIRClasspathImpl internal constructor(
    private val processManager: PIRProcessManager,
    private val channel: ManagedChannel,
    internal val stub: PIRServiceGrpc.PIRServiceBlockingStub,
    override val pythonVersion: String,
    override val mypyVersion: String,
    override val modules: List<PIRModule>,
    private val modulesByName: Map<String, PIRModule>,
    private val classesByQName: Map<String, PIRClass>,
    private val functionsByQName: Map<String, PIRFunction>,
) : PIRClasspath {

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
