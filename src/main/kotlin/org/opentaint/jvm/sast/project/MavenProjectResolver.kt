package org.opentaint.jvm.sast.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opentaint.jvm.sast.project.ProjectResolver.Companion.logger
import org.opentaint.jvm.sast.project.ProjectResolver.Companion.tryJavaToolchains
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.visitFileTree
import kotlin.io.path.walk

class MavenProjectResolver(
    private val resolverDir: Path,
    override val projectSourceRoot: Path
) : ProjectResolver {
    private val resolvedProjectDependencies = mutableListOf<Path>()
    private val resolvedModules = mutableListOf<ProjectResolver.ProjectModuleClasses>()

    private lateinit var javaToolchain: JavaToolchain

    override fun resolveProject(): ProjectResolver.Project? {
        logger.info { "Maven build start for: $projectSourceRoot" }
        if (!buildProject()) {
            logger.error { "Maven build failed for: $projectSourceRoot" }
            return null
        }

        logger.info { "Maven dependency resolution start for: $projectSourceRoot" }
        if (!resolveDependencies()) {
            logger.error { "Maven dependency resolution failed for: $projectSourceRoot" }
        }

        return ProjectResolver.Project(projectSourceRoot, javaToolchain, resolvedModules, resolvedProjectDependencies)
    }

    private fun registerModule(moduleRoot: Path, processModuleContent: (Path) -> Unit) {
        val classesDir = resolverDir.resolve("classes_${resolvedModules.size}")
        processModuleContent(classesDir.createParentDirectories())
        resolvedModules += ProjectResolver.ProjectModuleClasses(moduleRoot, listOf(classesDir))
    }

    @OptIn(ExperimentalPathApi::class)
    private fun buildProject(): Boolean {
        val args = listOf(MAVEN_EXECUTABLE_NAME) + listOf("clean", "package") + mavenCommandFlags

        javaToolchain = tryJavaToolchains { ProjectResolver.runCommand(projectSourceRoot, args, it) } ?: return false

        projectSourceRoot.visitFileTree {
            onPreVisitDirectory { directory, _ ->
                if (isMavenProjectRoot(directory)) {
                    val classes = directory.resolve("target").resolve("classes")
                    if (classes.isDirectory()) {
                        registerModule(directory) { classesSnapshotDir ->
                            classes.copyToRecursively(classesSnapshotDir, followLinks = false, overwrite = false)
                        }
                    }
                }
                FileVisitResult.CONTINUE
            }
        }

        return true
    }

    private fun resolveDependencies(): Boolean {
        val depGraphOutFolder = resolverDir.resolve("dg-out")
        val args = listOf(MAVEN_EXECUTABLE_NAME) + mavenCommandFlags + listOf(
            DEPGRAPH_PLUGIN_ID,
            "-DclasspathScopes=compile",
            "-DoutputDirectory=${depGraphOutFolder.absolutePathString()}",
            "-DgraphFormat=json",
            "-DshowAllAttributesForJson=true",
            "-DuseArtifactIdInFileName=true",
        )

        val status = ProjectResolver.runCommand(projectSourceRoot, args, javaToolchain)
        if (status != 0) {
            return false
        }

        resolveDependenciesFromGraph(depGraphOutFolder)

        return true
    }

    @OptIn(ExperimentalPathApi::class)
    private fun resolveDependenciesFromGraph(graphLocation: Path) {
        val json = Json {
            ignoreUnknownKeys = true
        }

        val dependencyResolver = MavenDependencyGraphResolver()

        graphLocation.walk().filter { it.extension == "json" }
            .forEach {
                val deps = json.decodeFromString<MavenDependencies>(it.readText())
                dependencyResolver.addDependencies(deps)
            }

        resolvedProjectDependencies += dependencyResolver.resolveDependenciesJars()
    }

    class MavenDependencyGraphResolver {
        private val artifacts = mutableMapOf<String, MavenArtifact>()
        private val artifactDependencies = mutableMapOf<String, MutableSet<String>>()
        private val buildArtifacts = mutableSetOf<String>()

        fun addDependencies(dependencies: MavenDependencies) {
            val usedArtifacts = mutableSetOf<String>()
            dependencies.dependencies?.forEach { dependency ->
                usedArtifacts.add(dependency.to)
                artifactDependencies.getOrPut(dependency.from) { mutableSetOf() }.add(dependency.to)
            }

            dependencies.artifacts?.forEach { artifact ->
                artifacts[artifact.id] = artifact

                if (artifact.id !in usedArtifacts) {
                    buildArtifacts.add(artifact.id)
                }
            }
        }

        fun resolveDependenciesJars(): List<Path> {
            val buildArtifactsNames = buildArtifacts.mapTo(mutableSetOf()) { artifacts.getValue(it).artifactName }
            return artifacts.values
                .filter { artifact -> artifact.artifactName !in buildArtifactsNames }
                .mapNotNull { artifact -> mavenLocalRepoPath.resolve(artifact.artifactJarPath).takeIf { it.exists() } }
        }
    }

    @Serializable
    data class MavenDependencies(
        val artifacts: List<MavenArtifact>? = null,
        val dependencies: List<MavenDependency>? = null
    )

    @Serializable
    data class MavenDependency(val from: String, val to: String)

    @Serializable
    data class MavenArtifact(
        val id: String,
        val groupId: String,
        val artifactId: String,
        val version: String,
        val classifiers: List<String>? = null,
        val types: List<String>? = null,
    ) {
        val artifactName: String by lazy { "${groupId}:${artifactId}:${version}" }

        val artifactDir: List<String> by lazy { groupId.split(".") + listOf(artifactId, version) }

        val snapshotVersion: String by lazy {
            resolveSnapshotVersion(this) ?: version
        }

        val artifactPomPath: List<String> by lazy {
            artifactDir + listOf("${artifactId}-${snapshotVersion}.pom")
        }

        val artifactJarPath: List<String> by lazy {
            artifactDir + listOf("${artifactId}-${snapshotVersion}.jar")
        }
    }

    companion object {
        private const val POM_FILE_NAME = "pom.xml"

        fun isMavenProjectRoot(directory: Path): Boolean =
            directory.resolve(POM_FILE_NAME).exists()


        private val mavenLocalRepoPath by lazy {
            Path(System.getProperty("user.home")) / ".m2" / "repository"
        }

        private const val DEPGRAPH_PLUGIN_ID = "com.github.ferstl:depgraph-maven-plugin:4.0.2:graph"

        private const val MAVEN_EXECUTABLE_NAME = "/usr/bin/mvn"

        private val mavenCommandFlags = listOf(
            "-f",
            POM_FILE_NAME,
            "-B",
            "-V",
            "-e",
            "-Dfindbugs.skip",
            "-Dcheckstyle.skip",
            "-Dpmd.skip=true",
            "-Dspotbugs.skip",
            "-Denforcer.skip",
            "-Dmaven.javadoc.skip",
            "-DskipTests",
            "-Dmaven.test.skip.exec",
            "-Dlicense.skip=true",
            "-Drat.skip=true",
            "-Dspotless.check.skip=true"
        )

        private fun resolveSnapshotVersion(artifact: MavenArtifact): String? {
            val remoteId = resolveRemoteId(artifact) ?: return null
            val metadataPath = mavenLocalRepoPath.resolve(artifact.artifactDir)
                .resolve("maven-metadata-${remoteId}.xml")

            if (!metadataPath.exists()) return null

            logger.warn { "TODO: Maven resolver snapshot: ${artifact.artifactName}" }

            return null
        }

        private fun resolveRemoteId(artifact: MavenArtifact): String? {
            val remotesPath = mavenLocalRepoPath.resolve(artifact.artifactDir).resolve("_remote.repositories")
            if (!remotesPath.exists()) return null
            return remotesPath.useLines { lines ->
                lines
                    .filterNot { it.isBlank() || it.startsWith("#") }
                    .map { it.split(">") }
                    .filter { it.size >= 2 }
                    .filter { it.first().endsWith(".pom") }
                    .map { it[1].trim() }
                    .map { it.substring(0, it.lastIndex) } // drop last symbol
                    .firstOrNull()
            }
        }
    }
}
