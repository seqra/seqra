package org.opentaint.ir.impl.performance

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.LibrariesMixin
import org.opentaint.ir.impl.index.ReversedUsages
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class DBLifeCycleBenchmarks : LibrariesMixin {

    private lateinit var db: CompilationDatabase

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            compilationDatabase {
                installFeatures(ReversedUsages)
                useProcessJavaRuntime()
            }
        }
    }

    @Benchmark
    fun loadAdditionalJars() {
        val jars = allJars
        runBlocking {
            db.load(jars)
        }
    }

    @Benchmark
    fun awaitIndexing() {
        runBlocking {
            db.awaitBackgroundJobs()
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        runBlocking {
            db.awaitBackgroundJobs()
            db.close()
        }
    }
}
