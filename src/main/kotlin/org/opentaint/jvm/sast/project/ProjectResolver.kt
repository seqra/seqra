package org.opentaint.jvm.sast.project

import mu.KLogging
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
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
        val javaToolchain: JavaToolchain,
        val modules: List<ProjectModuleClasses>,
        val dependencies: List<Path>
    )

    companion object {
        val logger = object : KLogging() {}.logger

        private val JAVA_8_HOME: String? by lazy { System.getenv("JAVA_8_HOME") }
        private val JAVA_11_HOME: String? by lazy { System.getenv("JAVA_11_HOME") }
        private val JAVA_17_HOME: String? by lazy { System.getenv("JAVA_17_HOME") }
        private val JAVA_LATEST_HOME: String? by lazy { System.getenv("JAVA_LATEST_HOME") }

        private val availableJavaToolchains: List<JavaToolchain> by lazy {
            listOfNotNull(JAVA_8_HOME, JAVA_LATEST_HOME, JAVA_17_HOME, JAVA_11_HOME)
                .map { JavaToolchain.ConcreteJavaToolchain(it) }
                .ifEmpty { listOf(JavaToolchain.DefaultJavaToolchain) }
        }

        fun tryJavaToolchains(block: (JavaToolchain) -> Int): JavaToolchain? {
            val toolchains = availableJavaToolchains.iterator()
            while (toolchains.hasNext()) {
                val toolchain = toolchains.next()
                if (block(toolchain) == 0) return toolchain
            }
            return null
        }

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

        internal fun runCommand(workDir: Path, args: List<String>, javaToolchain: JavaToolchain): Int =
            ProcessExecutor()
                .command(args)
                .apply {
                    when (javaToolchain) {
                        JavaToolchain.DefaultJavaToolchain -> {}
                        is JavaToolchain.ConcreteJavaToolchain -> {
                            environment("JAVA_HOME", javaToolchain.javaHome)
                        }
                    }
                }
                .directory(workDir.toFile())
                .redirectOutput(Slf4jStream.of(logger.underlyingLogger as ch.qos.logback.classic.Logger).asDebug())
                .redirectError(Slf4jStream.of(logger.underlyingLogger as ch.qos.logback.classic.Logger).asDebug())
                .executeNoTimeout().exitValue
    }
}

fun Path.resolve(other: List<String>): Path = other.fold(this) { path, o -> path.resolve(o) }

fun Path.createParentDirectories() = also {
    parent?.createDirectories()
}
