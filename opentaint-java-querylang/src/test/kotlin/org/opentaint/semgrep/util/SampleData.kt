package org.opentaint.semgrep.util

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.opentaint-ir
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
    val sampleName: String,
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
    }

    db.awaitBackgroundJobs()

    SamplesDb(db, path)
}

fun SamplesDb.loadSampleData(): Map<String, SampleData> =
    JarFile(samplesJar.absolutePathString()).use { jar ->
        val rules = jar.entries().asSequence()
            .filterTo(mutableListOf()) { it.name.endsWith(".yaml") }
            .associateBy { it.name }

        runBlocking {
            db.classpath(listOf(samplesJar.toFile())).use { cp ->
                loadSamples(cp, rules, jar).associateBy { it.sampleName }
            }
        }
    }

private fun loadSamples(cp: JIRClasspath, rules: Map<String, JarEntry>, samplesJar: JarFile): List<SampleData> {
    val sampleClass = cp.findClassOrNull("base.RuleSample")
        ?: error("No base class for samples")

    val hierarchy = runBlocking { cp.hierarchyExt() }
    val allSampleClasses = hierarchy
        .findSubClasses(sampleClass, entireHierarchy = true, includeOwn = false)
        .toList()

    val data = mutableListOf<SampleData>()
    for (sample in allSampleClasses) {
        val annotation = sample.annotations.singleOrNull { it.name == "base.RuleSet" } ?: continue
        val rulePath = annotation.values["value"]?.toString() ?: continue
        data += loadSample(cp, sample, rulePath, rules, samplesJar)
    }
    return data
}

private fun loadSample(
    cp: JIRClasspath,
    sample: JIRClassOrInterface,
    rulePath: String,
    rules: Map<String, JarEntry>,
    samplesJar: JarFile
): SampleData {
    val ruleEntry = rules[rulePath] ?: error("Rule $rulePath not found")
    val ruleText = samplesJar.getInputStream(ruleEntry).use {
        it.bufferedReader().readText()
    }

    val hierarchy = runBlocking { cp.hierarchyExt() }
    val allSamples = hierarchy
        .findSubClasses(sample, entireHierarchy = true, includeOwn = false)
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

    return SampleData(rulePath, ruleText, sample.name, positiveSamples, negativeSamples)
}

private fun String?.plusAnnotationValue(annotation: JIRAnnotation): String {
    val name = annotation.jirClass?.simpleName ?: error("No annotation class")
    return this + "$name(${annotation.values["value"]})"
}

private operator fun String?.plus(other: String): String = this?.let { "$it | $other" } ?: other
