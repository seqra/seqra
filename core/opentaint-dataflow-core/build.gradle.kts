plugins {
    java
}

val aggregatedTaskName = "check"

gradle.projectsEvaluated {
    val subprojectTasks = subprojects
        .mapNotNull { subproject -> subproject.tasks.findByName(aggregatedTaskName) }

    tasks.named(aggregatedTaskName) {
        dependsOn(subprojectTasks)
    }
}
