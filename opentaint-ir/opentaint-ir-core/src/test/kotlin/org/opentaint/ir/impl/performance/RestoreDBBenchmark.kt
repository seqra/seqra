package org.opentaint.ir.impl.performance

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.allClasspath
import org.opentaint.ir.jirdb
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class RestoreDBBenchmark {

    companion object {
        private val jdbcLocation = Files.createTempDirectory("jdbc-${UUID.randomUUID()}").toFile().absolutePath
    }

    var db: JIRDB? = null

    @Setup
    fun setup() {
        val tempDb = newDB()
        tempDb.close()
    }

    @Benchmark
    fun restore() {
        db = newDB()
    }

    @TearDown(Level.Iteration)
    fun clean() {
        db?.close()
        db = null
    }

    private fun newDB(): JIRDB {
        return runBlocking {
            jirdb {
                persistent(jdbcLocation)
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }
    }

}

fun main() {
    val test = RestoreDBBenchmark()
    test.setup()
    repeat(3) {
        println("iteration $it")
        val start = System.currentTimeMillis()
        test.restore()
        println("took ${System.currentTimeMillis() - start}ms")
        test.clean()
    }
}