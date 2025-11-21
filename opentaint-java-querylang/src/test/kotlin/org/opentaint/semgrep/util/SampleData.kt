package org.opentaint.semgrep.util

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.dataflow.configuration.jvm.JIRClassNameFeature
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

data class PositiveCase(val className: String)

data class NegativeCase(val className: String, val ignoreWithMessage: String?)

data class SampleData(
    val rulePath: String,
    val rule: String,
    val positiveClasses: List<PositiveCase>,
    val negativeClasses: List<NegativeCase>,
)

class SamplesDb(
    val db: JIRDatabase,
    val samplesJar: Path
) : AutoCloseable {
    override fun close() {
        db.close()
    }
}

private fun samplesJarPath(): Path {
    val path = System.getenv("TEST_SAMPLES_JAR") ?: error("Test JAR path required")
    return Path(path)
}

fun samplesDb(): SamplesDb = runBlocking {
    val path = samplesJarPath()

    val db = opentaint-ir {
        loadByteCode(listOf(path.toFile()))
        useProcessJavaRuntime()

        persistenceImpl(JIRRamErsSettings)

        installFeatures(InMemoryHierarchy)
        installFeatures(Usages)
        installFeatures(JIRClassNameFeature())
    }

    db.awaitBackgroundJobs()

    SamplesDb(db, path)
}

fun SamplesDb.loadSampleData(): Map<String, SampleData> =
    JarFile(samplesJar.absolutePathString()).use { jar ->
        val rules = jar.entries().asSequence().filterTo(mutableListOf()) { it.name.endsWith(".yaml") }

        runBlocking {
            db.classpath(listOf(samplesJar.toFile())).use { cp ->
                rules.map { loadSample(jar, cp, it) }.associateBy { it.rulePath }
            }
        }
    }

private fun loadSample(samplesJar: JarFile, cp: JIRClasspath, sample: JarEntry): SampleData {
    val rulePath = sample.name.removeSuffix(".yaml")
    val ruleText = samplesJar.getInputStream(sample).use {
        it.bufferedReader().readText()
    }

    val sampleClassName = rulePath.replace('/', '.')
    val sampleClass = cp.findClassOrNull(sampleClassName)
        ?: error("No sample class for rule $rulePath")

    val hierarchy = runBlocking { cp.hierarchyExt() }
    val allSamples = hierarchy
        .findSubClasses(sampleClass, entireHierarchy = true, includeOwn = false)
        .filterNotTo(mutableListOf()) { it.isAbstract }

    val positiveSamples = allSamples.filter { it.simpleName.contains("Positive") }.map { PositiveCase(it.name) }

    val negativeSamples = allSamples.filter { it.simpleName.contains("Negative") }.map { cls ->
        var ignoreMessage: String? = null

        for (annotation in cls.annotations) {
            when (annotation.jirClass?.simpleName) {
                "IFDSFalsePositive" -> ignoreMessage = ignoreMessage.plusAnnotationValue(annotation)
                "TaintRuleFalsePositive" -> ignoreMessage = ignoreMessage.plusAnnotationValue(annotation)
            }
        }

        NegativeCase(cls.name, ignoreMessage)
    }

    return SampleData(rulePath, ruleText, positiveSamples, negativeSamples)
}

private fun String?.plusAnnotationValue(annotation: JIRAnnotation): String {
    val name = annotation.jirClass?.simpleName ?: error("No annotation class")
    return this + "$name(${annotation.values["value"]})"
}

private operator fun String?.plus(other: String): String = this?.let { "$it | $other" } ?: other
