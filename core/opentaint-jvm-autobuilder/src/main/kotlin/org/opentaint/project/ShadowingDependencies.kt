package org.opentaint.project

import mu.KLogging
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

private val logger = object : KLogging() {}.logger

/**
 * Drops dependencies that ship the project's own classes.
 *
 * A project routinely depends on a published artifact that repackages its own modules (a client
 * bundle, a shaded jar, the previous release of the module itself). The project's compiled output
 * already carries those classes, so the dependency copy adds nothing — but it does make the class
 * name ambiguous on the classpath, and a lookup that lands on the dependency copy turns project code
 * into un-analyzable library code, silently killing every taint flow through it.
 */
fun List<JavaProject>.dropDependenciesShadowingProjectClasses(): List<JavaProject> {
    val projectClasses = flatMap { it.modules }
        .flatMap { it.moduleClasses }
        .flatMapTo(mutableSetOf()) { classNamesOf(it) }

    if (projectClasses.isEmpty()) return this

    val shadowingDependencies = flatMap { it.dependencies }
        .distinct()
        .mapNotNull { dependency ->
            val shadowed = classNamesOf(dependency).count { it in projectClasses }
            if (shadowed == 0) null else dependency to shadowed
        }
        .toMap()

    if (shadowingDependencies.isEmpty()) return this

    for ((dependency, shadowed) in shadowingDependencies) {
        logger.warn {
            "Dependency ${dependency.name} ships $shadowed classes the project itself compiles; " +
                "dropping it from the project model in favour of the project's own output"
        }
    }

    return map { project ->
        project.copy(dependencies = project.dependencies.filter { it !in shadowingDependencies })
    }
}

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
private fun classNamesOf(path: Path): Set<String> = when {
    path.isDirectory() -> path.walk()
        .filter { it.extension == CLASS_EXTENSION }
        .mapTo(mutableSetOf()) { it.relativeTo(path).toString().toClassName() }

    path.isRegularFile() -> runCatching {
        ZipFile(path.toFile()).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".$CLASS_EXTENSION") }
                .mapTo(mutableSetOf()) { it.name.toClassName() }
        }
    }.getOrElse {
        logger.warn { "Cannot read classes of $path: ${it.message}" }
        emptySet()
    }

    else -> emptySet()
}

private fun String.toClassName(): String =
    removeSuffix(".$CLASS_EXTENSION").replace('\\', '.').replace('/', '.')

private const val CLASS_EXTENSION = "class"
