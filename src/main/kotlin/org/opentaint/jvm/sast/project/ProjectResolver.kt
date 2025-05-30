package org.opentaint.jvm.sast.project

import org.opentaint.logger
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.visitFileTree

sealed interface ProjectResolver {
    val projectSourceRoot: Path

    fun resolveProject(): Project?

    data class ProjectModuleClasses(
        val projectModuleSourceRoot: Path,
        val projectModuleClasses: List<Path>
    )

    data class Project(
        val sourceRoot: Path,
        val modules: List<ProjectModuleClasses>,
        val dependencies: List<Path>
    )

    companion object {
        @OptIn(ExperimentalPathApi::class)
        fun resolveProjects(rootDir: Path, resolverWorkDir: Path): List<Project> {
            resolverWorkDir.createParentDirectories()

            val projectResolvers = mutableListOf<ProjectResolver>()
            rootDir.visitFileTree {
                onPreVisitDirectory { directory, _ ->
                    when {
                        GradleProjectResolver.isGradleProjectRoot(directory) -> {
                            logger.info { "Detect gradle project at $directory" }

                            projectResolvers += GradleProjectResolver(
                                resolverDir = createTempDirectory(resolverWorkDir, prefix = "gradle_project_"),
                                projectSourceRoot = directory
                            )

                            FileVisitResult.SKIP_SUBTREE
                        }

                        MavenProjectResolver.isMavenProjectRoot(directory) -> {
                            logger.info { "Detect maven project at $directory" }

                            projectResolvers += MavenProjectResolver(
                                resolverDir = createTempDirectory(resolverWorkDir, prefix = "maven_project_"),
                                projectSourceRoot = directory
                            )

                            FileVisitResult.SKIP_SUBTREE
                        }

                        else -> FileVisitResult.CONTINUE
                    }
                }
            }

            val resolvedProjects = mutableListOf<Project>()
            for (resolver in projectResolvers) {
                logger.info { "Start project resolution for: ${resolver.projectSourceRoot}" }
                try {
                    resolvedProjects += resolver.resolveProject() ?: continue
                } catch (ex: Throwable) {
                    logger.error(ex) { "Project resolution failed for: ${resolver.projectSourceRoot}" }
                }
            }

            return resolvedProjects
        }

        internal fun runCommand(workDir: Path, args: List<String>): Int {
            val builder = ProcessBuilder(args)
            builder.directory(workDir.toFile())
            builder.inheritIO()

            val process = builder.start()
            return process.waitFor()
        }
    }
}

fun Path.resolve(other: List<String>): Path = other.fold(this) { path, o -> path.resolve(o) }

fun Path.createParentDirectories() = also {
    parent?.createDirectories()
}


fun main() {
    val sourceRoot = Path("data/PublicCMS/")
    val resolverPath = Path("IdeaProjects/opentaint/resolver-tmp")

    val projects = ProjectResolver.resolveProjects(sourceRoot, resolverPath)
    println(projects)
}

