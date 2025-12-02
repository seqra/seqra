package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ApproximationPaths(
    val opentaintApiJarPath: String? = System.getenv("opentaint.jvm.api.jar.path"),
    val opentaintApproximationsJarPath: String? = System.getenv("opentaint.jvm.approximations.jar.path")
) {
    val namedPaths = mapOf(
        "Analyzer API" to opentaintApiJarPath,
        "Analyzer Approximations" to opentaintApproximationsJarPath
    )
    val presentPaths: Set<String> = namedPaths.values.filterNotNull().toSet()
    val allPathsArePresent = namedPaths.values.all { it != null }
}

private val classpathApproximations: MutableMap<JIRClasspath, Set<String>> = ConcurrentHashMap()

// TODO: use another way to detect internal classes (e.g. special bytecode location type)
val JIRClassOrInterface.isOpentaintInternalClass: Boolean
    get() = classpathApproximations[classpath]?.contains(name) ?: false

val JIRClassType.isOpentaintInternalClass: Boolean
    get() = if (this is JIRClassTypeImpl) {
        classpathApproximations[classpath]?.contains(name) ?: false
    } else {
        jirClass.isOpentaintInternalClass
    }

suspend fun JIRDatabase.classpathWithApproximations(
    dirOrJars: List<File>,
    features: List<JIRClasspathFeature> = emptyList(),
    approximationPaths: ApproximationPaths = ApproximationPaths(),
): JIRClasspath? {
    if (!approximationPaths.allPathsArePresent) {
        return null
    }

    val approximationsPath = approximationPaths.presentPaths.map { File(it) }

    val cpWithApproximations = dirOrJars + approximationsPath
    val featuresWithApproximations = features + listOf(Approximations)
    val cp = classpath(cpWithApproximations, featuresWithApproximations.distinct())

    val approximationsLocations = cp.locations.filter { it.jarOrFolder in approximationsPath }
    val approximationsClasses = approximationsLocations.flatMapTo(hashSetOf()) { it.classNames ?: emptySet() }
    classpathApproximations[cp] = approximationsClasses

    return cp
}
