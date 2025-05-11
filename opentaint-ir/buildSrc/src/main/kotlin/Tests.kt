import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

object Tests {
    val lifecycleTag = "lifecycle"

}

fun Test.setup(jacocoTestReport: TaskProvider<*>) {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
    finalizedBy(jacocoTestReport) // report is always generated after tests run
    jvmArgs = listOf("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=heapdump.hprof")
}
