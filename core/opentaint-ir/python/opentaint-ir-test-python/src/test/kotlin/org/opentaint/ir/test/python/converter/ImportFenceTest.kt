package org.opentaint.ir.test.python.converter

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ImportFenceTest {

    private val implSrcRoot = File(System.getProperty("user.dir"))
        .resolve("../opentaint-ir-impl-python/src/main/kotlin/org/opentaint/ir/impl/python")
        .canonicalFile

    private val protoToFlatBuilderPath = implSrcRoot.resolve("builder/ProtoToFlatBuilder.kt")
    private val flatToPirConverterPath = implSrcRoot.resolve("converter/FlatToPirConverter.kt")

    private fun readImports(file: File): List<String> {
        assertTrue(file.exists(), "Source file not found: $file")
        return file.readLines().filter { it.trimStart().startsWith("import ") }.map { it.trim() }
    }

    @Test
    fun `ProtoToFlatBuilder does not import PIR impl or converter types`() {
        val imports = readImports(protoToFlatBuilderPath)
        val forbidden = imports.filter { line ->
            line.startsWith("import org.opentaint.ir.impl.python.PIR")
            || line.startsWith("import org.opentaint.ir.impl.python.converter")
            || (line.startsWith("import org.opentaint.ir.api.python")
                && "PIRDiagnostic" !in line)
        }
        assertTrue(
            forbidden.isEmpty(),
            "Forbidden imports in ProtoToFlatBuilder:\n${forbidden.joinToString("\n")}",
        )
    }

    @Test
    fun `FlatToPirConverter does not import proto or ProtoToFlatBuilder`() {
        val imports = readImports(flatToPirConverterPath)
        val forbidden = imports.filter { line ->
            line.contains("impl.python.proto")
            || line.contains("ProtoToFlatBuilder")
        }
        assertTrue(
            forbidden.isEmpty(),
            "Forbidden imports in FlatToPirConverter:\n${forbidden.joinToString("\n")}",
        )
    }
}
