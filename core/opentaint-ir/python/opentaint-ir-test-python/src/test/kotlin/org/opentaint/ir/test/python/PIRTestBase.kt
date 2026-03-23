package org.opentaint.ir.test.python

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRClasspathImpl
import java.io.File
import java.nio.file.Files

/**
 * Base class for PIR tests. Provides utilities for building classpaths
 * from inline Python sources.
 */
abstract class PIRTestBase {

    companion object {
        /**
         * Find the project root (where pir_server/ directory lives).
         */
        fun findProjectRoot(): String {
            // Walk up from working directory looking for pir_server/
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                if (File(dir, "pir_server").isDirectory) {
                    return dir.absolutePath
                }
                dir = dir.parentFile
            }
            // Fallback: assume CWD is the project root
            return System.getProperty("user.dir")
        }
    }

    /**
     * Build a PIRClasspath from inline Python source code.
     * Writes the source to a temp file and analyzes it.
     */
    protected fun buildFromSource(
        source: String,
        moduleName: String = "__test__"
    ): PIRClasspath {
        val tmpDir = Files.createTempDirectory("pir-test").toFile()
        tmpDir.deleteOnExit()
        val file = File(tmpDir, "$moduleName.py")
        file.writeText(source.trimIndent())
        file.deleteOnExit()

        return PIRClasspathImpl.create(PIRSettings(
            sources = listOf(file.absolutePath),
            mypyFlags = listOf("--ignore-missing-imports"),
        ))
    }
}
