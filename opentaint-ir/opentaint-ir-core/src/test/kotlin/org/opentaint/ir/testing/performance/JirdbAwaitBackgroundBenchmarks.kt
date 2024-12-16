package org.opentaint.ir.testing.performance

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.testing.allClasspath
import org.openjdk.jmh.annotations.*
import org.opentaint.opentaint-ir.api.JIRDatabase
import org.opentaint.opentaint-ir.impl.JIRSettings
import org.opentaint.opentaint-ir.impl.features.InMemoryHierarchy
import org.opentaint.opentaint-ir.impl.features.Usages
import org.opentaint.opentaint-ir.impl.opentaint-ir
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.*
import java.io.File
import java.util.concurrent.TimeUnit

abstract class JirdbAbstractAwaitBackgroundBenchmarks {

    private lateinit var db: JIRDatabase

    abstract fun JIRSettings.configure()

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            opentaint-ir {
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

    override fun JIRSettings.configure() {
    }

}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbAllClasspathBackgroundBenchmarks : JirdbAbstractAwaitBackgroundBenchmarks() {

    override fun JIRSettings.configure() {
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

    override fun JIRSettings.configure() {
        loadByteCode(allIdeaJars)
        installFeatures(Usages, InMemoryHierarchy)
        persistent(File.createTempFile("jIRdb-", "-db").absolutePath)
    }

}
