
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
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.impl.allClasspath
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.guavaLib
import org.opentaint.ir.opentaint-ir
import java.io.File
import java.util.concurrent.TimeUnit


@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JirdbBenchmarks  {

    private var db: JIRDB? = null

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
                persistent(File.createTempFile("jirdb-", "-db").absolutePath)
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
                persistent(File.createTempFile("jirdb-", "-db").absolutePath)
                installFeatures(Usages)
            }
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db?.close()
    }
}
