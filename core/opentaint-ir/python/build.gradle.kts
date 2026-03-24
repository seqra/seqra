import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Exec

plugins {
    id("kotlin-conventions")
}

val pirEnvironmentExtraKey = "opentaint.pir.env"
val pirVenvDir = layout.projectDirectory.dir(".venv")
val pirPyprojectFile = layout.projectDirectory.file("pyproject.toml")
val pirVenvPython = pirVenvDir.file("bin/python")
val pirBootstrapPython = providers.environmentVariable("PYTHON").orElse("python3")
val pirInstallSpec = ".[dev]"
val pirBenchmarksInstallSpec = ".[benchmarks]"
val inheritedPythonPath = providers.environmentVariable("PYTHONPATH").orNull

fun pirEnvironment(): Map<String, String> {
    val pythonPath = listOf(
        projectDir.absolutePath,
        inheritedPythonPath,
    ).filterNotNull().filter { it.isNotBlank() }.joinToString(File.pathSeparator)

    return mapOf(
        "PIR_SERVER_PYTHON" to pirVenvPython.asFile.absolutePath,
        "PYTHONPATH" to pythonPath,
    )
}

val createPirServerVenv = tasks.register<Exec>("createPirServerVenv") {
    group = "python"
    description = "Creates the PIR server virtual environment."
    outputs.dir(pirVenvDir)
    commandLine(
        pirBootstrapPython.get(),
        "-m",
        "venv",
        pirVenvDir.asFile.absolutePath,
    )
}

val upgradePirServerPip = tasks.register<Exec>("upgradePirServerPip") {
    group = "python"
    description = "Upgrades pip inside the PIR server virtual environment."
    dependsOn(createPirServerVenv)
    inputs.file(pirVenvPython)
    outputs.dir(pirVenvDir)
    commandLine(
        pirVenvPython.asFile.absolutePath,
        "-m",
        "pip",
        "install",
        "--upgrade",
        "pip",
    )
}

tasks.register<Exec>("setupPirServerVenv") {
    group = "python"
    description = "Creates the PIR server virtual environment and installs pir-server with dev dependencies."
    dependsOn(upgradePirServerPip)
    inputs.file(pirPyprojectFile)
    outputs.dir(pirVenvDir)
    workingDir = projectDir
    commandLine(
        pirVenvPython.asFile.absolutePath,
        "-m",
        "pip",
        "install",
        "-e",
        pirInstallSpec,
    )
}

tasks.register<Exec>("setupPirBenchmarkDeps") {
    group = "python"
    description = "Installs benchmark-only Python dependencies into the PIR server virtual environment."
    dependsOn("setupPirServerVenv")
    inputs.file(pirPyprojectFile)
    outputs.dir(pirVenvDir)
    workingDir = projectDir
    commandLine(
        pirVenvPython.asFile.absolutePath,
        "-m",
        "pip",
        "install",
        "-e",
        pirBenchmarksInstallSpec,
    )
}

tasks.register<DefaultTask>("setupPirEnvironment") {
    group = "python"
    description = "Initializes the PIR server environment metadata."
    dependsOn("setupPirServerVenv")
    doFirst {
        extra.set(pirEnvironmentExtraKey, pirEnvironment())
    }
}

tasks.register<DefaultTask>("setupPirTestEnvironment") {
    group = "python"
    description = "Initializes the PIR test environment metadata, including benchmark dependencies."
    dependsOn("setupPirEnvironment")
    dependsOn("setupPirBenchmarkDeps")
    doFirst {
        extra.set(pirEnvironmentExtraKey, pirEnvironment())
    }
}
