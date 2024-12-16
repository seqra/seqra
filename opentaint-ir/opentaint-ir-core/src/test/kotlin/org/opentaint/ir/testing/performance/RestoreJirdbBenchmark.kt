package org.opentaint.ir.testing.performance

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.testing.allClasspath
import org.openjdk.jmh.annotations.*
import org.opentaint.opentaint-ir.api.JIRDatabase
import org.opentaint.opentaint-ir.impl.opentaint-ir
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class RestoreJirdbBenchmark {

    companion object {
        private val jdbcLocation = Files.createTempDirectory("jdbc-${UUID.randomUUID()}").toFile().absolutePath
    }

    var db: JIRDatabase? = null

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

    private fun newDB(): JIRDatabase {
        return runBlocking {
            opentaint-ir {
                persistent(jdbcLocation)
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }
    }

}