package org.opentaint.ir.testing.performance

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.ir.testing.allJars
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbLifeCycleBenchmarks {

    private lateinit var db: JIRDatabase

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            opentaint-ir {
                installFeatures(Usages)
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
        db.close()
    }
}
