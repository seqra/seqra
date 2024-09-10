package org.opentaint.java.compilation.database.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.opentaint.java.compilation.database.compilationDatabase
import org.opentaint.java.compilation.database.impl.fs.asByteCodeLocation
import org.opentaint.java.compilation.database.impl.fs.load
import org.opentaint.java.compilation.database.impl.tree.ClassTree


class ByteCodeReaderTest : LibrariesMixin {

    @Test
    fun `read byte-code benchmark`() {
        val lib = guavaLib
        benchmark(name = "read bytecode") {
            runBlocking {
                lib.asByteCodeLocation().loader()!!.load(ClassTree())
            }
        }
    }

    @Test
    fun `read all classpath byte-code benchmark`() {
        val jars = allJars
        benchmark(5, "reading libraries bytecode") {
            runBlocking {
                val db = compilationDatabase {
                    useJavaHomeJRE()
                }
                db.load(jars)
            }
        }
    }

    @Test
    fun `read jre libraries  benchmark`() {
        benchmark(5, "reading libraries bytecode") {
            runBlocking {
                compilationDatabase {
                    useJavaHomeJRE()
                }
            }
        }
    }

    private fun benchmark(repeats: Int = 10, name: String, action: () -> Unit) {
        // warmup
        repeat(repeats / 2) {
            println("warmup $it")
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
}
//
//val db = runBlocking {
//    compilationDatabase {
//        apiLevel = ApiLevel.ASM8
//        useJavaHomeJRE()
//    }
//}
//fun main() {
//    println(db)
//    Thread.sleep(Long.MAX_VALUE)
//}