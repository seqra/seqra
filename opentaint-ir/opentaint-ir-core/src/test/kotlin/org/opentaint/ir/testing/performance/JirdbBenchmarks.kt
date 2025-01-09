package org.opentaint.ir.testing.performance

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.guavaLib
import org.openjdk.jmh.annotations.*
import java.io.File
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xmx12048m"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbBenchmarks  {

    private var db: JIRDatabase? = null

    @Benchmark
    fun jvmRuntime() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithUsages() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspath() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                loadByteCode(allClasspath)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspathWithUsages() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                loadByteCode(allClasspath)
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithGuava() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                loadByteCode(listOf(guavaLib))
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithGuavaWithUsages() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                loadByteCode(listOf(guavaLib))
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithIdeaCommunity() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                persistent(File.createTempFile("jIRdb-", "-db").absolutePath)
                loadByteCode(allIdeaJars)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeIdeaCommunityWithUsages() {
        db = runBlocking {
            opentaint-ir {
                useProcessJavaRuntime()
                loadByteCode(allIdeaJars)
                persistent(File.createTempFile("jIRdb-", "-db").absolutePath)
                installFeatures(Usages)
            }
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db?.close()
    }
}
