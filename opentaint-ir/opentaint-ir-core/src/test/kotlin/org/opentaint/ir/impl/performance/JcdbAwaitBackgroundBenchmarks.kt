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
import org.opentaint.ir.JIRDBSettings
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.allClasspath
import org.opentaint.ir.jirdb
import java.io.File
import java.util.concurrent.TimeUnit

abstract class JirdbAbstractAwaitBackgroundBenchmarks {

    private lateinit var db: JIRDB

    abstract fun JIRDBSettings.configure()

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            jirdb {
                useProcessJavaRuntime()
                configure()
            }
        }
    }

    @Benchmark
    fun awaitBackground() {
        runBlocking {
            db.awaitBackgroundJobs()
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db.close()
    }
}


@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbJvmBackgroundBenchmarks : JirdbAbstractAwaitBackgroundBenchmarks() {

    override fun JIRDBSettings.configure() {
    }

}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbAllClasspathBackgroundBenchmarks : JirdbAbstractAwaitBackgroundBenchmarks() {

    override fun JIRDBSettings.configure() {
        loadByteCode(allClasspath)
    }

}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbIdeaBackgroundBenchmarks : JirdbAbstractAwaitBackgroundBenchmarks() {

    override fun JIRDBSettings.configure() {
        loadByteCode(allIdeaJars)
        persistent(File.createTempFile("jirdb-", "-db").absolutePath)
    }

}
