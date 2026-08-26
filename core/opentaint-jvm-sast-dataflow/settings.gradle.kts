rootProject.name = "opentaint-jvm-sast-dataflow"

// Include each model directory that has a Gradle build file.
// Keep this prefix equal to ApproximationModules.PROJECT_PREFIX.
val modelRoot = file("../../model/java/dataflow")
val modelModules = modelRoot.listFiles()
    ?.filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
    ?.sortedBy { it.name }
    .orEmpty()

if (modelModules.isEmpty() && modelRoot.resolve("build.gradle.kts").isFile) {
    // The core stack layer still uses the single model project.
    val projectName = "dataflow-approximations-core"
    include(projectName)
    project(":$projectName").projectDir = modelRoot
} else {
    modelModules.forEach { moduleDir ->
        val projectName = "dataflow-approximations-${moduleDir.name}"
        include(projectName)
        project(":$projectName").projectDir = moduleDir
    }
}
