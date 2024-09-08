package org.opentaint.java.compilation.database.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.compilationDatabase
import org.opentaint.java.compilation.database.impl.fs.asByteCodeLocation
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import java.io.File
import java.net.URLClassLoader


class ByteCodeReaderTest {

    @Test
    fun `read byte-code benchmark`() {
        val lib = guavaLib
        benchmark(20, "read bytecode") {
            runBlocking {
                lib.asByteCodeLocation(ApiLevel.ASM8).loader().load(ClassTree())
            }
        }
    }

    @Test
    fun `read all classpath byte-code benchmark`() {
        val jars = allJars
        benchmark(10, "reading libraries bytecode") {
            runBlocking {
                val db = compilationDatabase {
                    apiLevel = ApiLevel.ASM8
                    useJavaHomeJRE()
                }
                db.load(jars)
            }
        }
    }

    @Test
    fun `read jre libraries  benchmark`() {
        benchmark(10, "reading libraries bytecode") {
            runBlocking {
                compilationDatabase {
                    apiLevel = ApiLevel.ASM8
                    useJavaHomeJRE()
                }
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
            Thread.sleep(1_000)
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
//            val jdks = File(System.getenv("JAVA_HOME") + "\\jre\\lib\\").listFiles { file -> file.name.endsWith(".jar") }
//                .orEmpty().toList()
            return jars.map { File(it.file) } //+ jdks
        }
}