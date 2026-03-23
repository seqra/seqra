import org.gradle.api.tasks.testing.Test
import java.io.File

plugins {
    id("kotlin-conventions")
}

val pirProject = project(":python")
val pirServerPython = pirProject.layout.projectDirectory.file(".venv/bin/python").asFile.absolutePath
val inheritedPythonPath = providers.environmentVariable("PYTHONPATH").orNull
val pirPythonPath = listOf(
    pirProject.projectDir.absolutePath,
    inheritedPythonPath,
).filterNotNull().filter { it.isNotBlank() }.joinToString(File.pathSeparator)

tasks.withType<Test>().configureEach {
    dependsOn(":python:setupPirServerVenv")
    dependsOn(":python:setupPirBenchmarkDeps")
    environment("PIR_SERVER_PYTHON", pirServerPython)
    environment("PYTHONPATH", pirPythonPath)
}

dependencies {
    testImplementation(project(":python:opentaint-ir-api-python"))
    testImplementation(project(":python:opentaint-ir-impl-python"))

    testImplementation("com.google.code.gson:gson:2.10.1")

    // Needed for Tier 3 round-trip tests (ExecuteFunctionRequest/Response)
    testImplementation("com.google.protobuf:protobuf-java:3.25.3")
    testImplementation("io.grpc:grpc-protobuf:1.62.2")
    testImplementation("io.grpc:grpc-stub:1.62.2")
}

tasks.test {
    if (project.hasProperty("allTiers")) {
        dependsOn(setupWebProjects)
    }

    useJUnitPlatform {
        if (!project.hasProperty("allTiers")) {
            excludeTags("tier1")
        }
    }
    // Each test class spawns a Python subprocess with gRPC server.
    // Single fork prevents port conflicts and resource exhaustion.
    maxParallelForks = 1
    maxHeapSize = "8g"

    // Test logging: show which tests start and pass/fail
    testLogging {
        events("started", "passed", "failed", "skipped")
        showStandardStreams = false
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ─── Web project setup for Tier-1 benchmarks ──────────────────────

val webProjectsDir = layout.buildDirectory.dir("web-projects")
val webProjectsManifest = layout.projectDirectory.file("web-projects.txt")

fun run(vararg args: String, dir: File? = null, ignoreExit: Boolean = false) {
    val pb = ProcessBuilder(*args).redirectErrorStream(true)
    if (dir != null) pb.directory(dir)
    val proc = pb.start()
    proc.inputStream.bufferedReader().forEachLine { println("  $it") }
    val rc = proc.waitFor()
    if (rc != 0 && !ignoreExit) {
        throw GradleException("Command failed (exit $rc): ${args.joinToString(" ")}")
    }
}

val setupWebProjects = tasks.register("setupWebProjects") {
    group = "benchmark"
    description = "Clone and checkout web projects listed in web-projects.txt at pinned commits."

    doLast {
        val entries = webProjectsManifest.asFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split("|")
                check(parts.size == 3) { "Bad line in web-projects.txt: $line" }
                Triple(parts[0], parts[1], parts[2])
            }

        println("Setting up ${entries.size} web projects")

        for ((name, commit, url) in entries) {
            val projectDir = webProjectsDir.get().dir(name).asFile
            if (projectDir.exists()) {
                println("[$name] Already exists, checking out $commit")
                run("git", "fetch", "--depth=1", "origin", commit, dir = projectDir, ignoreExit = true)
                run("git", "checkout", commit, dir = projectDir)
            } else {
                println("[$name] Cloning $url @ $commit")
                run("git", "clone", "--depth=1", url, projectDir.absolutePath)
                run("git", "fetch", "--depth=1", "origin", commit, dir = projectDir)
                run("git", "checkout", commit, dir = projectDir)
            }
        }

        println("Done: ${entries.size} web projects ready.")
    }
}
