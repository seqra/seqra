package org.opentaint.ir.test.python.protoToFlat

import org.opentaint.ir.api.python.PIRSettings
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import java.io.File
import java.nio.file.Files

/**
 * Test base for asserting on raw `FlatModuleIR` produced by
 * `ProtoToFlat.lowerModule` (i.e. before any later transform). Mirrors
 * `PIRTestBase` but stops at Flat IR.
 */
abstract class RawFlatModuleTestBase {
    protected fun lowerSourceToFlat(
        source: String,
        moduleName: String = "__test__",
    ): FlatModuleIR {
        val tmpDir = Files.createTempDirectory("flat-test").toFile()
        tmpDir.deleteOnExit()
        val file = File(tmpDir, "$moduleName.py")
        file.writeText(source.trimIndent())
        file.deleteOnExit()

        val modules = PIRRawFlatLoader.loadRawFlatModules(
            PIRSettings(
                sources = listOf(file.absolutePath),
                mypyFlags = listOf("--ignore-missing-imports"),
            )
        )
        return modules.first { it.moduleName == moduleName }
    }
}
