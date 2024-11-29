
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
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.impl.allJars
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.opentaint-ir
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
