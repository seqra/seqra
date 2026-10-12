package org.opentaint.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opentaint.project.ProjectResolver.Companion.logger
import org.opentaint.project.ProjectResolver.Companion.tryJavaToolchains
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
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

class GradleProjectResolver(
    private val resolverDir: Path,
    override val projectSourceRoot: Path
) : ProjectResolver {
    private val resolvedModules = mutableListOf<ProjectModuleClasses>()
    private val resolvedProjectDependencies = mutableListOf<Path>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun registerModule(moduleRoot: Path, snapshotLibs: (Path) -> List<Path>) {
        val snapshotDir = resolverDir.resolve("modules_${resolvedModules.size}").createDirectories()
        val libs = snapshotLibs(snapshotDir)
        resolvedModules += ProjectModuleClasses(moduleRoot, moduleClasses = libs)
    }

    private lateinit var javaToolchain: JavaToolchain

    override fun resolveProject(): JavaProject? {
        logger.info { "Gradle build start for: $projectSourceRoot" }
        if (!buildProject()) {
            logger.error { "Gradle build failed for: $projectSourceRoot" }
            return null
        }

        logger.info { "Gradle dependency resolution start for: $projectSourceRoot" }
        if (!resolveDependencies()) {
            logger.error { "Gradle dependency resolution failed for: $projectSourceRoot" }
        }

        return JavaProject(projectSourceRoot, javaToolchain.path(), resolvedModules, resolvedProjectDependencies)
    }

    private fun buildProject(): Boolean {
        val gradleExecutable = resolveGradleExecutable(projectSourceRoot)

        val classesReportDir = resolverDir.resolve("classes-out").createDirectories()

        val args = listOf(gradleExecutable) +
            gradleBuildFlags +
            resolveGradleClassesCmdArgs(classesResolverInitScript, classesReportDir) +
            listOf("clean", RESOLVE_CLASSES_TASK)

        javaToolchain = tryJavaToolchains { ProjectResolver.runCommand(projectSourceRoot, args, it) } ?: return false

        registerModulesFromReports(classesReportDir)

        if (resolvedModules.isEmpty()) {
            logger.warn { "No module classes resolved for: $projectSourceRoot" }
        }

        return true
    }

    private fun registerModulesFromReports(reportDir: Path) {
        reportDir.walk().filter { it.extension == "json" }.forEach { reportFile ->
            val report = json.decodeFromString<ClassesReport>(reportFile.readText())

            val classDirs = report.classDirs.map { Path(it) }.filter { it.isDirectory() }
            if (classDirs.isEmpty()) {
                logger.warn { "No class directories resolved for module: ${report.projectPath}" }
                return@forEach
            }

            val moduleRoot = Path(report.projectPath)
            registerModule(moduleRoot) { snapshotDir ->
                classDirs.mapIndexed { index, classDir ->
                    val snapshotDestination = snapshotDir.resolve("classes_$index")
                    snapshotDestination.createDirectories()
                    classDir.copyDirRecursivelyTo(snapshotDestination)
                    snapshotDestination
                }
            }
        }
    }

    private val dependencyResolverInitScript: Path by lazy {
        resolverDir.resolve("dep-graph.gradle").apply {
            writeText(GRADLE_DEPENDENCY_INIT_SCRIPT)
        }
    }

    private val classesResolverInitScript: Path by lazy {
        resolverDir.resolve("classes-graph.gradle").apply {
            writeText(GRADLE_CLASSES_INIT_SCRIPT)
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

            val directFirst = allDependenciesInfo.filter { it.key in directDependencies }.map { it.value } +
                allDependenciesInfo.filterNot { it.key in directDependencies }.map { it.value }

            // Dependencies are collected across every module of the build, so the same artifact shows
            // up at each version any module resolved. Resolve that conflict the way the build tool
            // does — one version per artifact — instead of handing the analyzer several copies of the
            // same classes and leaving the choice to classpath lookup order. Only versions that
            // resolve to a jar take part: the graph also carries versions nothing ever compiled
            // against, which have no artifact in the local caches.
            val conflictFree = directFirst.singleResolvedVersionPerArtifact(
                artifact = { it.groupId to it.artifactId },
                version = { it.version },
                resolve = { resolveJarPath(it) },
                onDropped = { kept, dropped ->
                    logger.debug {
                        "Dependency conflict on ${dropped.groupId}:${dropped.artifactId}: " +
                            "keeping ${kept.version}, dropping ${dropped.version}"
                    }
                }
            )

            val droppedCount = directFirst.size - conflictFree.size
            if (droppedCount > 0) {
                logger.info {
                    "Resolved dependencies: dropped $droppedCount entries " +
                        "(duplicate artifact versions and versions with no artifact in the local caches)"
                }
            }

            return conflictFree
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
    data class ClassesReport(
        val projectPath: String,
        val classDirs: List<String> = emptyList()
    )

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

        private val gradleWrapper by lazy {
            selectExecutableName(win = "gradlew.bat", other = "gradlew")
        }

        private fun resolveGradleExecutable(directory: Path): String {
            val gradlew = directory.resolve(gradleWrapper)
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
            "-Dorg.gradle.configuration-cache=false",
        )

        private const val RESOLVE_CLASSES_TASK = "opentaintResolveClasses"

        private const val CLASSES_REPORT_DIR_PROPERTY = "OPENTAINT_CLASSES_REPORT_DIR"

        private val GRADLE_CLASSES_INIT_SCRIPT = """
            import groovy.json.JsonOutput

            def reportDir = new File(System.getProperty("$CLASSES_REPORT_DIR_PROPERTY"))
            reportDir.mkdirs()

            gradle.rootProject { root ->
                root.tasks.register("$RESOLVE_CLASSES_TASK")
            }

            allprojects { p ->
                p.afterEvaluate {
                    def lifecycle = p.tasks.findByName("jvmMainClasses") ?: p.tasks.findByName("classes")
                    if (lifecycle == null) return

                    def reportTask = p.tasks.register("opentaintReportClasses") {
                        dependsOn lifecycle
                        doLast {
                            def dirs = [] as Set

                            // Compile tasks are not necessarily direct dependencies of the lifecycle
                            // task. In particular, Kotlin compilation can be wired behind another task,
                            // so traverse the complete dependency graph.
                            def pending = [lifecycle]
                            def visited = [] as Set
                            while (!pending.isEmpty()) {
                                def task = pending.remove(pending.size() - 1)
                                if (!visited.add(task)) continue

                                if (task.hasProperty("destinationDirectory")) {
                                    try {
                                        def f = task.destinationDirectory.get().asFile
                                        if (f.exists()) dirs.add(f.absolutePath)
                                    } catch (Exception e) {
                                        println "opentaint: failed to read destinationDirectory for " + task.path + ": " + e
                                    }
                                }

                                try {
                                    task.taskDependencies.getDependencies(task).each { dependency ->
                                        if (!visited.contains(dependency)) pending.add(dependency)
                                    }
                                } catch (Exception e) {
                                    println "opentaint: failed to traverse dependencies for " + task.path + ": " + e
                                }
                            }

                            if (dirs.isEmpty()) {
                                println "opentaint: no class directories resolved for " + p.path
                            }
                            def name = "classes-" + p.path.replace(":", "_") + ".json"
                            new File(reportDir, name).text = JsonOutput.toJson([projectPath: p.projectDir.absolutePath, classDirs: new ArrayList(dirs)])
                        }
                    }
                    p.rootProject.tasks.named("$RESOLVE_CLASSES_TASK").configure { dependsOn reportTask }
                }
            }
        """.trimIndent()

        private fun resolveGradleClassesCmdArgs(initScript: Path, reportDir: Path): List<String> =
            listOf(
                "--init-script",
                initScript.absolutePathString(),
                "-D$CLASSES_REPORT_DIR_PROPERTY=${reportDir.absolutePathString()}",
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
                "-Dorg.gradle.configuration-cache=false",
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