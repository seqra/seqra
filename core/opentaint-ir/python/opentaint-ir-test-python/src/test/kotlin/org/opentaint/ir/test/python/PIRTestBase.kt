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
