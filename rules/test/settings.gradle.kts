rootProject.name = "seqra-builtin-rules-test"

includeBuild("../../core/opentaint-sast-test-util") {
    dependencySubstitution {
        substitute(module("org.opentaint.sast-test-util:opentaint-sast-test-util")).using(project(":"))
    }
}

// Auto-discover all spring-tests subprojects by walking the entire subtree
val springTestsRoot = "spring-app-tests"
val springTestsRootFile = file(springTestsRoot)
springTestsRootFile.walkTopDown()
    .filter { it.isFile && it.name == "build.gradle.kts" }
    .map { it.parentFile }
    .forEach { moduleDir ->
        val relPath = moduleDir.relativeTo(springTestsRootFile).invariantSeparatorsPath
        val pathSegments = relPath.split("/")

        val projectPath = (listOf(springTestsRoot) + pathSegments).joinToString(separator = ":", prefix = ":")
        include(projectPath)

        val projectName = projectPath.replace(":", "-").removePrefix("-")
        project(projectPath).name = projectName
    }
