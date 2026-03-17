import org.gradle.api.Task
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext

fun IncludedBuild.resolveIncludedProjectTask(taskPath: String): Task {
    val taskRef = task(taskPath) as TaskDependencyContainer
    val resolvedTasks = mutableListOf<Task>()
    val ctx = object : TaskDependencyResolveContext {
        override fun add(dependency: Any) {
            resolvedTasks.add(dependency as Task)
        }

        override fun visitFailure(failure: Throwable) {
            error("Unexpected operation")
        }

        override fun getTask(): Task? {
            error("Unexpected operation")
        }
    }
    taskRef.visitDependencies(ctx)
    return resolvedTasks.single()
}
