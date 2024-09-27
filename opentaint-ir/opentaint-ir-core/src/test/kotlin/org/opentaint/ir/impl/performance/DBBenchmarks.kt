package org.opentaint.ir.impl.performance


import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.opentaint.ir.api.CompilationDatabase
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.LibrariesMixin
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.fs.load
import org.opentaint.ir.impl.index.ReversedUsages
import org.opentaint.ir.impl.tree.ClassTree
import java.util.concurrent.TimeUnit


@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class DBBenchmarks : LibrariesMixin {

    private var db: CompilationDatabase? = null

    @Benchmark
    fun readBytecode() {
        val lib = guavaLib
        runBlocking {
            lib.asByteCodeLocation().loader()!!.load(ClassTree())
        }
    }

    @Benchmark
    fun readingJVMbytecode() {
        db = runBlocking {
            compilationDatabase {
                useProcessJavaRuntime()

                installFeatures(ReversedUsages)
            }
        }
    }

    @Benchmark
    fun readingJVMbytecodeWithProjectClasspath() {
        db = runBlocking {
            compilationDatabase {
                useProcessJavaRuntime()
                predefinedDirOrJars = allJars
                installFeatures(ReversedUsages)
            }
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db?.let {
            runBlocking {
                it.awaitBackgroundJobs()
                it.close()
            }
        }
    }
}