package org.opentaint.ir.go.client

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Manages the go-ssa-server subprocess lifecycle.
 */
class GoSsaServerProcess(
    private val serverBinaryPath: String = System.getProperty(
        "goir.server.binary",
        "go-ssa-server" // hope it's on PATH
    ),
) : AutoCloseable {
    private var process: Process? = null
    private var channel: ManagedChannel? = null

    fun start(): ManagedChannel {
        val pb = ProcessBuilder(serverBinaryPath, "-port=0")
            .redirectErrorStream(false)

        val proc = pb.start()
        process = proc

        // Read the port from stdout
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val line = reader.readLine()
            ?: throw IllegalStateException("Go server did not produce output")

        val port = if (line.startsWith("LISTENING:")) {
            line.substringAfter("LISTENING:").trim().toInt()
        } else {
            throw IllegalStateException("Unexpected server output: $line")
        }

        val ch = ManagedChannelBuilder.forAddress("localhost", port)
            .usePlaintext()
            .maxInboundMessageSize(256 * 1024 * 1024) // 256 MB
            .build()

        channel = ch
        return ch
    }

    override fun close() {
        channel?.let {
            it.shutdown()
            try {
                it.awaitTermination(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                it.shutdownNow()
            }
        }
        process?.let {
            it.destroy()
            try {
                it.waitFor(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                it.destroyForcibly()
            }
        }
        channel = null
        process = null
    }
}
