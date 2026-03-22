package org.opentaint.ir.impl.python

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Manages the Python pir_server subprocess lifecycle.
 */
class PIRProcessManager(
    private val pythonExecutable: String = "python3",
    private val serverModule: String = "pir_server",
    private val startupTimeout: Duration = Duration.ofSeconds(30),
) : Closeable {

    private var process: Process? = null
    private var port: Int = -1

    fun start(): Int {
        val pb = ProcessBuilder(pythonExecutable, "-m", serverModule, "--port", "0")
        pb.redirectErrorStream(false)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)

        val proc = pb.start()
        this.process = proc

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val deadline = System.currentTimeMillis() + startupTimeout.toMillis()

        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                throw PIRServerStartupException(
                    "Python server exited with code ${proc.exitValue()}"
                )
            }
            val line = reader.readLine() ?: continue
            if (line.startsWith("READY:")) {
                port = line.substringAfter("READY:").trim().toInt()
                return port
            }
        }
        proc.destroyForcibly()
        throw PIRServerStartupException(
            "Python server did not become ready within $startupTimeout"
        )
    }

    fun getPort(): Int {
        check(port > 0) { "Server not started" }
        return port
    }

    override fun close() {
        val proc = process ?: return
        try {
            // Close stdin pipe — triggers the Python watchdog thread to exit
            try { proc.outputStream.close() } catch (_: Exception) {}
            if (proc.isAlive) {
                proc.waitFor(5, TimeUnit.SECONDS)
            }
        } finally {
            if (proc.isAlive) {
                proc.destroyForcibly()
                proc.waitFor(3, TimeUnit.SECONDS)
            }
            process = null
            port = -1
        }
    }

    val isRunning: Boolean get() = process?.isAlive == true
}

class PIRServerStartupException(message: String) : RuntimeException(message)
