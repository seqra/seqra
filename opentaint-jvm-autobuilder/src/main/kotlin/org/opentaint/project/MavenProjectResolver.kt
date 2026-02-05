package org.opentaint.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opentaint.project.ProjectResolver.Companion.logger
import org.opentaint.project.ProjectResolver.Companion.tryJavaToolchains
import org.zeroturnaround.exec.ProcessExecutor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.visitFileTree
import kotlin.io.path.walk

class MavenProjectResolver(
    private val resolverDir: Path,
    override val projectSourceRoot: Path
) : ProjectResolver {
    private val resolvedProjectDependencies = mutableListOf<Path>()
    private val resolvedModules = mutableListOf<ProjectModuleClasses>()

    private var executableFound = true
    private val projectMavenExecutable = getProjectMaven()

    private fun getProjectMaven(): String {
        val mwnwName = selectExecutableName(win = "mvnw.cmd", other = "mvnw")
        val mvnwPath = (projectSourceRoot / mwnwName).toAbsolutePath()
        if (mvnwPath.isExecutable())
            return mvnwPath.pathString
        if (MAVEN_EXECUTABLE_NAME != null)
            return MAVEN_EXECUTABLE_NAME!!
        executableFound = false
        // setting as default so possible execution errors are concise
        return DEFAULT_MAVEN_NAME
    }

    private lateinit var javaToolchain: JavaToolchain

    override fun resolveProject(): Project? {
        if (!executableFound) {
            logger.error { "Could not find Maven executable!" }
            return null
        }

        logger.info { "Maven builder in use: $projectMavenExecutable" }
        logger.info { "Maven build start for: $projectSourceRoot" }
        if (!buildProject()) {
            logger.error { "Maven build failed for: $projectSourceRoot" }
            return null
        }

        logger.info { "Maven dependency resolution start for: $projectSourceRoot" }
        if (!resolveDependencies()) {
            logger.error { "Maven dependency resolution failed for: $projectSourceRoot" }
        }

        return Project(projectSourceRoot, javaToolchain.path(), resolvedModules, resolvedProjectDependencies)
    }

    private fun registerModule(moduleRoot: Path, processModuleContent: (Path) -> Unit) {
        val classesDir = resolverDir.resolve("classes_${resolvedModules.size}")
        processModuleContent(classesDir.createParentDirectories())
        resolvedModules += ProjectModuleClasses(moduleRoot, moduleClasses = listOf(classesDir))
    }

    private fun buildProject(): Boolean {
        val args = listOf(projectMavenExecutable) + listOf("clean", "package") + mavenCommandFlags

        javaToolchain = tryJavaToolchains { ProjectResolver.runCommand(projectSourceRoot, args, it) } ?: return false

        projectSourceRoot.visitFileTree {
            onPreVisitDirectory { directory, _ ->
                if (isMavenProjectRoot(directory)) {
                    val classes = directory.resolve("target").resolve("classes")
                    if (classes.isDirectory()) {
                        registerModule(directory) { classesSnapshotDir ->
                            classes.copyDirRecursivelyTo(classesSnapshotDir)
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
        val args = listOf(projectMavenExecutable) + mavenCommandFlags + listOf(
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

        private fun checkExecutable(name: String): Boolean {
            return try {
                ProcessExecutor()
                    .command(listOf(name, "--version"))
                    .timeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .execute().exitValue == 0
            } catch (e: Exception) {
                false
            }
        }

        private fun getDefaultMaven(): String? {
            if (checkExecutable(DEFAULT_MAVEN_NAME))
                return DEFAULT_MAVEN_NAME
            return null
        }

        private fun findMavenExecutable(): String? {
            for (exe in MAVEN_LOOKUP_PATHS) {
                if (Path(exe).isExecutable())
                    return exe
            }
            return getDefaultMaven()
        }

        private val mavenLocalRepoPath by lazy {
            Path(System.getProperty("user.home")) / ".m2" / "repository"
        }

        private const val DEPGRAPH_PLUGIN_ID = "com.github.ferstl:depgraph-maven-plugin:4.0.2:graph"

        private val MAVEN_LOOKUP_PATHS = listOf(
            "/usr/bin/mvn",
            "/usr/local/bin/mvn",
        )

        private const val DEFAULT_MAVEN_NAME = "mvn"

        private val MAVEN_EXECUTABLE_NAME by lazy {
            findMavenExecutable()
        }

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
            "-Dspotless.check.skip=true",
            "-Dspotless.apply.skip=true",
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
