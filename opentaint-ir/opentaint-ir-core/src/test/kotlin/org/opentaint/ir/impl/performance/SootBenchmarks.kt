package org.opentaint.opentaint-ir.impl.performance

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.opentaint.opentaint-ir.impl.allClasspath
import org.opentaint.opentaint-ir.impl.guavaLib
import soot.G
import soot.PackManager
import soot.Scene
import soot.SootClass
import soot.jimple.JimpleBody
import soot.options.Options
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

val allIdeaJars: List<File>
    get() {
        return allIdeaJarsMain("idea-community", "unzip")
    }

val allIdeaJarsAbsolute: List<File>
    get() {
        return allIdeaJarsMain("jIRdb-core", "idea-community", "unzip")
    }

private fun allIdeaJarsMain(vararg paths: String): List<File> {
    val absolutePath = File(".").absolutePath
    val ideaClassPath = Paths.get(absolutePath, *paths)
    require(ideaClassPath.exists())
    val pluginsJars = ideaClassPath.resolve("plugins").toFile().walk().filter { it.extension == "jar" }
    val libsJars = ideaClassPath.resolve("lib").toFile().walk().filter { it.extension == "jar" }
    return (libsJars + pluginsJars).toList()
}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class SootBenchmarks {

    @Benchmark
    fun jvmRuntime() {
        initSoot(emptyList())
    }

    @Benchmark
    fun jvmRuntimeWithGuava() {
        initSoot(listOf(guavaLib))
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspath() {
        initSoot(allClasspath)
    }

    @Benchmark
    fun jvmRuntimeWithIdeaCommunity() {
        initSoot(allIdeaJars)
    }

    private fun initSoot(files: List<File>) {
        G.reset()
        val options = Options.v()
        val version = 11
        val location = System.getProperty("java.home")

        G.v().initJdk(G.JreInfo(location, version)) // init Soot with the right jdk
        options.apply {
            set_prepend_classpath(true)
            // set true to debug. Disabled because of a bug when two different variables
            // from the source code have the same name in the jimple body.
            setPhaseOption("jb", "use-original-names:false")
            set_soot_classpath(files.filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath })
            set_src_prec(Options.src_prec_only_class)
            set_keep_line_number(true)
            set_ignore_classpath_errors(true) // gradle/build/resources/main does not exists, but it's not a problem
            set_output_format(Options.output_format_jimple)
            /**
             * In case of Java8, set_full_resolver(true) fails with "soot.SootResolver$SootClassNotFoundException:
             * couldn't find class: javax.crypto.BadPaddingException (is your soot-class-path set properly?)".
             * To cover that, set_allow_phantom_refs(true) is required
             */
            set_allow_phantom_refs(true) // Java8 related
            set_full_resolver(true)
        }
        Scene.v().loadNecessaryClasses()
        PackManager.v().runPacks()
        val sootClass = Scene.v().getSootClass("java.lang.String")
        sootClass.getMethod("asd").retrieveActiveBody()
        sootClass.setResolvingLevel(SootClass.BODIES)
        sootClass.methods.first().retrieveActiveBody() as JimpleBody
    }

}