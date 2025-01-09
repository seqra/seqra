package org.opentaint.ir.testing.performance

import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.guavaLib
import org.openjdk.jmh.annotations.*
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation
import sootup.java.core.JavaProject
import sootup.java.core.language.JavaLanguage
import sootup.java.core.views.JavaView
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xmx16000m"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class SootupBenchmarks {

//    @Benchmark
    fun jvmRuntime() {
        newView(emptyList())
    }

//    @Benchmark
    fun jvmRuntimeWithGuava() {
        newView(listOf(guavaLib))
    }

//    @Benchmark
    fun jvmRuntimeWithAllClasspath() {
        newView(allClasspath)
    }

    @Benchmark
    fun jvmRuntimeWithIdeaCommunity() {
        newView(allIdeaJars)
    }

    fun newView(files: List<File>): JavaView {
        val javaHome = System.getProperty("java.home")
        println(javaHome)

        val builder: JavaProject.JavaProjectBuilder = JavaProject.builder(JavaLanguage(8))

        allRuntimeLocations.forEach {
            println(it.absolutePath)
            builder.addInputLocation(JavaClassPathAnalysisInputLocation(it.absolutePath))
        }

        for (s in files) {
            if (s.exists()) {
                builder.addInputLocation(JavaClassPathAnalysisInputLocation(s.absolutePath))
            }
        }
        val javaProject: JavaProject = builder.build()
        return javaProject.createFullView()

    }

    val allRuntimeLocations: List<File>
        get() {
            val javaHome = File(System.getProperty("java.home"))
            return Paths.get(javaHome.toPath().toString(), "jmods").toFile()
                .listFiles { file -> file.name.endsWith(".jar") || file.name.endsWith(".jmod") }
                .orEmpty()
                .toList()
        }
}

//
//fun main() {
//    val javaHome = System.getProperty("java.home")
//    println(javaHome)
//
//    val classpath = System.getProperty("java.class.path")
//    val cp = classpath.split(File.pathSeparator.toRegex()).map { File(it) }
//    val builder: JavaProject.JavaProjectBuilder = JavaProject.builder(JavaLanguage(8))
//
//    allRuntimeLocations.forEach {
//        println(it.absolutePath)
//        builder.addInputLocation(JavaClassPathAnalysisInputLocation(it.absolutePath))
//    }
//
//    for (s in cp) {
//        if (s.exists()) {
//            builder.addInputLocation(JavaClassPathAnalysisInputLocation(s.absolutePath))
//        }
//    }
//    val javaProject: JavaProject = builder.build()
//    val view = javaProject.createFullView()
//
//    val identifierFactory = JavaIdentifierFactory.getInstance()
//    val type = identifierFactory.getClassType("java.lang.String")
//
//    val sootClass = view.getClass(type)
//    println(sootClass.get().methods.first().body)
//}