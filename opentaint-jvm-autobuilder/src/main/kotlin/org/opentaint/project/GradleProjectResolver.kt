package org.opentaint.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opentaint.project.ProjectResolver.Companion.logger
import org.opentaint.project.ProjectResolver.Companion.tryJavaToolchains
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.visitFileTree
import kotlin.io.path.walk
import kotlin.io.path.writeText

class GradleProjectResolver(
    private val resolverDir: Path,
    override val projectSourceRoot: Path
) : ProjectResolver {
    private val resolvedModules = mutableListOf<ProjectModuleClasses>()
    private val resolvedProjectDependencies = mutableListOf<Path>()

    private fun registerModule(moduleRoot: Path, snapshotLibs: (Path) -> List<Path>) {
        val snapshotDir = resolverDir.resolve("modules_${resolvedModules.size}").createDirectories()
        val libs = snapshotLibs(snapshotDir)
        resolvedModules += ProjectModuleClasses(moduleRoot, moduleClasses = libs)
    }

    private lateinit var javaToolchain: JavaToolchain

    override fun resolveProject(): Project? {
        logger.info { "Gradle build start for: $projectSourceRoot" }
        if (!buildProject()) {
            logger.error { "Gradle build failed for: $projectSourceRoot" }
            return null
        }

        logger.info { "Gradle dependency resolution start for: $projectSourceRoot" }
        if (!resolveDependencies()) {
            logger.error { "Gradle dependency resolution failed for: $projectSourceRoot" }
        }

        return Project(projectSourceRoot, javaToolchain.path(), resolvedModules, resolvedProjectDependencies)
    }

    private fun buildProject(): Boolean {
        val gradleExecutable = resolveGradleExecutable(projectSourceRoot)

        val buildTarget = "classes" // todo: maybe use testClasses (to include tests) or assemble task?
        val args = listOf(gradleExecutable) + gradleBuildFlags + listOf("clean", buildTarget)

        javaToolchain = tryJavaToolchains { ProjectResolver.runCommand(projectSourceRoot, args, it) } ?: return false

        projectSourceRoot.visitFileTree {
            onPreVisitDirectory { directory, _ ->
                if (isGradleProjectRoot(directory)) {
                    val classesDir = directory.resolve("build").resolve("classes")
                    if (classesDir.isDirectory()) {
                        val languages = classesDir.listDirectoryEntries().filter { it.isDirectory() }
                        val configurations = languages.flatMap { languageClasses ->
                            languageClasses.listDirectoryEntries().filter { it.isDirectory() }
                        }

                        if (configurations.isNotEmpty()) {
                            registerModule(directory) { snapshotDir ->
                                configurations.map { configurationClasses ->
                                    val configurationName = configurationClasses.relativeTo(classesDir)
                                    val snapshotDestination = snapshotDir.resolve(configurationName)

                                    snapshotDestination.createDirectories()
                                    configurationClasses.copyDirRecursivelyTo(snapshotDestination)

                                    snapshotDestination
                                }
                            }
                        }
                    }
                }
                FileVisitResult.CONTINUE
            }
        }

        return true
    }

    private val dependencyResolverInitScript: Path by lazy {
        resolverDir.resolve("dep-graph.gradle").apply {
            writeText(GRADLE_DEPENDENCY_INIT_SCRIPT)
        }
    }

    private fun resolveDependencies(): Boolean {
        val depGraphOutFolder = resolverDir.resolve("dg-out").createDirectories()

        val gradleExecutable = resolveGradleExecutable(projectSourceRoot)
        val args = listOf(gradleExecutable) + resolveGradleDependencyCmdArgs(
            projectSourceRoot, dependencyResolverInitScript, depGraphOutFolder
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

        val dependencyResolver = GradleDependencyResolver()

        graphLocation.walk().filter { it.extension == "json" }
            .forEach {
                val deps = json.decodeFromString<GradleDependencies>(it.readText())
                dependencyResolver.addDependencies(deps)
            }

        resolvedProjectDependencies += dependencyResolver.resolveDependenciesJars()
    }

    private class GradleDependencyResolver {
        private val dependenciesInfo = mutableMapOf<String, GradleDependencyInfo>()
        private val directDependencies = mutableSetOf<String>()

        fun addDependencies(dependencies: GradleDependencies) {
            for (manifest in dependencies.manifests.orEmpty().values) {
                for ((dependencyId, dependency) in manifest.resolved.orEmpty()) {
                    val dependencyInfo = dependency.info ?: continue
                    dependenciesInfo[dependencyId] = dependencyInfo

                    if (dependency.relationship == "direct") {
                        directDependencies.add(dependencyId)
                    }
                }
            }
        }

        fun resolveDependenciesJars(): List<Path> {
            val allDependenciesInfo = dependenciesInfo.entries.sortedBy { it.key }

            val resolvedDirectDependencies = allDependenciesInfo
                .filter { it.key in directDependencies }
                .mapNotNull { resolveJarPath(it.value) }

            val resolvedIndirectDependencies = allDependenciesInfo
                .filter { it.key !in directDependencies }
                .mapNotNull { resolveJarPath(it.value) }

            return resolvedDirectDependencies + resolvedIndirectDependencies
        }

        private fun resolveJarPath(dependency: GradleDependencyInfo): Path? {
            val gradlePath = gradleLocalRepoPath.resolve(dependency.gradleArtifactDir)
            if (gradlePath.isDirectory()) {
                gradlePath.walk().firstOrNull { it.name == dependency.artifactJarName }?.let { return it }
            }

            val mavenPath = mavenLocalRepoPath.resolve(dependency.mavenArtifactDir).resolve(dependency.artifactJarName)
            if (mavenPath.isRegularFile()) return mavenPath

            return null
        }
    }

    @Serializable
    data class GradleDependencies(
        val manifests: Map<String, GradleDependenciesManifest>? = null
    )

    @Serializable
    data class GradleDependenciesManifest(
        val resolved: Map<String, GradleDependenciesDependency>? = null
    )

    @Serializable
    data class GradleDependenciesDependency(
        @SerialName("package_url")
        val packageUrl: String,
        val relationship: String,
        val dependencies: List<String>? = null
    ) {
        val info: GradleDependencyInfo? by lazy { resolveDependencyInfo() }

        private fun resolveDependencyInfo(): GradleDependencyInfo? {
            if (!packageUrl.startsWith(MAVEN_PACKAGE_PREFIX)) return null

            val groupEnd = packageUrl.indexOf('/', MAVEN_PACKAGE_PREFIX.length)
            val artifactEnd = packageUrl.indexOf('@', groupEnd)
            var versionEnd = packageUrl.indexOf('?')
            if (versionEnd == -1) {
                versionEnd = packageUrl.length
            }

            val groupId = packageUrl.substring(MAVEN_PACKAGE_PREFIX.length, groupEnd)
            val artifactId = packageUrl.substring(groupEnd + 1, artifactEnd)
            val version = packageUrl.substring(artifactEnd + 1, versionEnd)

            return GradleDependencyInfo(groupId, artifactId, version)
        }

        companion object {
            private const val MAVEN_PACKAGE_PREFIX = "pkg:maven/"
        }
    }

    data class GradleDependencyInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
    ) {
        val artifactJarName: String by lazy { "${artifactId}-${version}.jar" }
        val mavenArtifactDir: List<String> by lazy { groupId.split(".") + listOf(artifactId, version) }
        val gradleArtifactDir: List<String> by lazy { listOf(groupId, artifactId, version) }
    }

    companion object {
        private val mavenLocalRepoPath by lazy {
            Path(System.getProperty("user.home")) / ".m2" / "repository"
        }

        private val gradleLocalRepoPath by lazy {
            Path(System.getProperty("user.home")) / ".gradle" / "caches" / "modules-2" / "files-2.1"
        }

        private const val GRADLE_SETTINGS_FILE = "settings.gradle"
        private const val GRADLE_SETTINGS_KTS_FILE = "$GRADLE_SETTINGS_FILE.kts"
        private const val GRADLE_BUILD_FILE = "build.gradle"
        private const val GRADLE_BUILD_KTS_FILE = "$GRADLE_BUILD_FILE.kts"

        private val gradleProjectFiles = arrayOf(
            GRADLE_SETTINGS_FILE, GRADLE_SETTINGS_KTS_FILE, GRADLE_BUILD_FILE, GRADLE_BUILD_KTS_FILE
        )

        fun isGradleProjectRoot(directory: Path): Boolean =
            gradleProjectFiles.any { directory.resolve(it).exists() }

        private const val GRADLE_SYSTEM_EXECUTABLE = "/usr/bin/gradle"
        private const val GRADLE_WRAPPER = "gradlew"

        private fun resolveGradleExecutable(directory: Path): String {
            val gradlew = directory.resolve(GRADLE_WRAPPER)
            if (!gradlew.isExecutable()) return GRADLE_SYSTEM_EXECUTABLE

            val wrapperDir = directory.resolve("gradle").resolve("wrapper")
            val wrapperJar = wrapperDir.resolve("gradle-wrapper.jar")
            val wrapperProperties = wrapperDir.resolve("gradle-wrapper.properties")

            if (!wrapperJar.exists() || !wrapperProperties.exists()) {
                return GRADLE_SYSTEM_EXECUTABLE
            }

            return gradlew.absolutePathString()
        }

        private val gradleBuildFlags = listOf(
            "--no-daemon",
            "-S",
            "-Dorg.gradle.dependency.verification=off",
            "-Dorg.gradle.warning.mode=none",
            "-Dorg.gradle.caching=false",
        )

        private val GRADLE_DEPENDENCY_INIT_SCRIPT = """
            import org.gradle.github.GitHubDependencyGraphPlugin
            initscript {
              repositories {
                maven {
                  url = uri("https://plugins.gradle.org/m2/")
                }
              }
              dependencies {
                classpath("org.gradle:github-dependency-graph-gradle-plugin:+")
              }
            }
            
            apply plugin: GitHubDependencyGraphPlugin
        """.trimIndent()

        private fun resolveGradleDependencyCmdArgs(workDir: Path, initScript: Path, reportDir: Path): List<String> =
            listOf(
                "-Dorg.gradle.configureondemand=false",
                "-Dorg.gradle.dependency.verification=off",
                "-Dorg.gradle.warning.mode=none",
                "--init-script",
                initScript.absolutePathString(),
                "ForceDependencyResolutionPlugin_resolveAllDependencies",
                "--stacktrace",
                "-DGITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR=dep-graph",
                "-DGITHUB_DEPENDENCY_GRAPH_JOB_ID=unknown",
                "-DGITHUB_DEPENDENCY_GRAPH_SHA=unknown",
                "-DGITHUB_DEPENDENCY_GRAPH_REF=unknown",
                "-DGITHUB_DEPENDENCY_GRAPH_WORKSPACE=${workDir.absolutePathString()}",
                "-DDEPENDENCY_GRAPH_REPORT_DIR=${reportDir.absolutePathString()}"
            )
    }
}