package org.opentaint.ir.impl

import org.opentaint.ir.impl.reader.readClasses
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader


class ByteCodeReaderTest {

    @Test
    fun `read byte-code benchmark`() {
        val lib = guavaLib
        benchmark(20, "read bytecode") {
            runBlocking {
                lib.byteCodeLocation.readClasses().toList()
            }
        }
    }


    @Test
    fun `read all classpath byte-code benchmark`() {
        val jars = allJars
        benchmark(10, "reading libraries bytecode") {
            runBlocking {
                CompilationDatabaseImpl().load(jars)
            }
        }
    }

    private fun benchmark(repeats: Int = 10, name: String, action: () -> Unit) {
        // warmup
        repeat(repeats / 2) {
            action()
        }

        // let's count
        repeat(repeats) {
            val start = System.currentTimeMillis()
            action()
            val end = System.currentTimeMillis()
            println("$it: $name took: ${end - start}ms")
        }

    }

    private val guavaLib: File
        get() {
            val cl = ClassLoader.getSystemClassLoader()
            val guavaUrl = (cl as URLClassLoader).urLs.first { it.path.contains("guava-31.1-jre.jar") }
            return File(guavaUrl.file).also {
                assertTrue(it.isFile && it.exists())
            }
        }

    private val allJars: List<File>
        get() {
            val cl = ClassLoader.getSystemClassLoader()
            val jars = (cl as URLClassLoader).urLs.filter { it.path.endsWith(".jar") }
            return jars.map { File(it.file) }
        }
}