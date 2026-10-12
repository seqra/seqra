package org.opentaint.project

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import mu.KLogging
import org.opentaint.util.CliWithLogger
import org.opentaint.util.directory
import org.opentaint.util.newDirectory
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class ProjectAutoBuilder : CliWithLogger() {
    private val buildDir by option(help = "Project resolver (builder) working directory")
        .newDirectory()

    private val projectRootDir: Path by option(help = "Project root dir")
        .directory()
        .required()

    private val buildType: ProjectBuildType by option().groupChoice(
        "build" to BuildProject(),
        "cp" to ProjectFromCP()
    ).defaultByName("build")

    private val build: ProjectBuildOptions by option().groupChoice(
        "portable" to PortableProjectBuild(),
        "simple" to SimpleProjectBuild(),
    ).defaultByName("simple")

    override fun main() {
        val projectRoot = projectRootDir.toAbsolutePath()
        logger.info { "Run auto build resolver at $projectRoot" }

        val resolverWorkDir = buildDir?.toAbsolutePath() ?: createTempDirectory("resolver")

        val resolvedProject = when (val b = buildType) {
            is BuildProject -> {
                ProjectResolver.resolveProject(projectRoot, resolverWorkDir)
            }

            is ProjectFromCP -> {
                ProjectFromCPResolver().resolveProject(projectRoot, resolverWorkDir, b)
            }
        }

        val javaProjects = resolvedProject?.let { Project.flattenJavaProject(it) }.orEmpty()
            .dropDependenciesShadowingProjectClasses()
        val goProjects = GoProjectResolver.resolveProject(projectRoot, resolverWorkDir)

        if (javaProjects.isEmpty() && goProjects.isEmpty()) {
            logger.error { "No projects resolved at $projectRoot" }
            return
        }

        // todo: switch to a new project model
        if (goProjects.isEmpty()) {
            val javaRootProject = javaProjects.first()
            val javaOther = javaProjects.drop(1)
            val javaTopLeveProject = javaRootProject.copy(subProjects = javaOther)

            when (val b = build) {
                is SimpleProjectBuild -> {
                    javaTopLeveProject.dump(b.result.createParentDirectories())
                }

                is PortableProjectBuild -> {
                    val portableProjectCreator = PortableProjectCreator(b.resultDir)
                    portableProjectCreator.create(javaTopLeveProject)
                }
            }

            return
        }

        val topLevelProject = Project(
            projectRoot = projectRoot,
            goProjects = goProjects,
            javaProjects = javaProjects,
        )

        when (val b = build) {
            is SimpleProjectBuild -> {
                topLevelProject.dump(b.result.createParentDirectories())
            }

            is PortableProjectBuild -> {
                val portableProjectCreator = PortableProjectCreator(b.resultDir)
                portableProjectCreator.create(topLevelProject)
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        @JvmStatic
        fun main(args: Array<String>) = ProjectAutoBuilder().main(args)
    }
}
