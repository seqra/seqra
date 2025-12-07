package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JcClassOrInterface
import org.opentaint.ir.api.jvm.JcClassType
import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.JcClasspathFeature
import org.opentaint.ir.api.jvm.JcDatabase
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.impl.types.JcClassTypeImpl
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ApproximationPaths(
    val engineApiJarPath: String? = System.getenv("opentaint.jvm.api.jar.path"),
    val engineApproximationsJarPath: String? = System.getenv("opentaint.jvm.approximations.jar.path")
) {
    val namedPaths = mapOf(
        "Opentaint engine API" to engineApiJarPath,
        "Opentaint Approximations" to engineApproximationsJarPath
    )
    val presentPaths: Set<String> = namedPaths.values.filterNotNull().toSet()
    val allPathsArePresent = namedPaths.values.all { it != null }
}

private val classpathApproximations: MutableMap<JcClasspath, Set<String>> = ConcurrentHashMap()

// TODO: use another way to detect internal classes (e.g. special bytecode location type)
val JcClassOrInterface.isInternalClass: Boolean
    get() = classpathApproximations[classpath]?.contains(name) ?: false

val JcClassType.isInternalClass: Boolean
    get() = if (this is JcClassTypeImpl) {
        classpathApproximations[classpath]?.contains(name) ?: false
    } else {
        jcClass.isInternalClass
    }

suspend fun JcDatabase.classpathWithApproximations(
    dirOrJars: List<File>,
    features: List<JcClasspathFeature> = emptyList(),
    approximationPaths: ApproximationPaths = ApproximationPaths(),
): JcClasspath? {
    if (!approximationPaths.allPathsArePresent) {
        return null
    }

    val approximationsPath = approximationPaths.presentPaths.map { File(it) }

    val cpWithApproximations = dirOrJars + approximationsPath

    val approximations = this.features.filterIsInstance<Approximations>().singleOrNull()
        ?: error("Approximations feature not found in database features")

    val featuresWithApproximations = features + listOf(approximations)

    val cp = classpath(cpWithApproximations, featuresWithApproximations.distinct())

    val approximationsLocations = cp.locations.filter { it.jarOrFolder in approximationsPath }
    val approximationsClasses = approximationsLocations.flatMapTo(hashSetOf()) { it.classNames ?: emptySet() }
    classpathApproximations[cp] = approximationsClasses

    return cp
}
