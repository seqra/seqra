package org.opentaint.ir.go.test

import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.client.BuildResult
import org.opentaint.ir.go.client.GoIRClient
import java.nio.file.Path

/**
 * Helper for tests: builds GoIR from inline Go source or Go files.
 * Wraps [GoIRClient] with convenient test-oriented API.
 */
class GoIRTestBuilder : AutoCloseable {
    private val client = GoIRClient()

    /**
     * Build IR from inline Go source code.
     */
    fun buildFromSource(
        source: String,
        packageName: String = "p",
    ): GoIRProgram = client.buildFromSource(source, packageName)

    /**
     * Build IR from a directory with Go source files.
     */
    fun buildFromDir(dir: Path, vararg patterns: String): GoIRProgram =
        client.buildFromDir(dir, *patterns)

    /**
     * Build IR from a directory, returning both the program and detailed timings.
     */
    fun buildFromDirWithTimings(dir: Path, vararg patterns: String): BuildResult =
        client.buildFromDirWithTimings(dir, *patterns)

    override fun close() {
        client.close()
    }
}
