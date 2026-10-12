rootProject.name = "opentaint-jvm-sast-dataflow"

// Include each model directory that has a Gradle build file.
// Keep this prefix equal to ApproximationModules.PROJECT_PREFIX.
val modelRoot = file("../../model/java/dataflow")
val modelModules = modelRoot.listFiles()
    ?.filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
    ?.sortedBy { it.name }
    .orEmpty()

// The analyzer jar carries whatever is included here, so an empty list is a jar shipped
// with no built-in models at all. Refuse rather than build one.
require(modelModules.isNotEmpty()) {
    "No built-in dataflow model modules under $modelRoot: each one is a directory with a build.gradle.kts"
}

modelModules.forEach { moduleDir ->
    val projectName = "dataflow-approximations-${moduleDir.name}"
    include(projectName)
    project(":$projectName").projectDir = moduleDir
}
