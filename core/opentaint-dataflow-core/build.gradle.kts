plugins {
    java
}

val aggregatedTasks = listOf("check", "build", "test")
for (taskName in aggregatedTasks) {
    tasks.named(taskName) {
        dependsOn(subprojects.map { ":${it.name}:$taskName" })
    }
}
