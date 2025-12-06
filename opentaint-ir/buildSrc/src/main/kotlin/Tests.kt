import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.util.*

object Tests {
    val lifecycleTag = "lifecycle"
}

fun Test.setupTest() {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }

    val majorJavaVersion = Integer.parseInt(
        StringTokenizer(System.getProperty("java.specification.version"), ".").nextToken()
    )

    maxHeapSize = "5G"

    if (majorJavaVersion >= 16) {
        jvmArgs = listOf(
            "--add-opens", "java.base/java.nio=ALL-UNNAMED", // this is necessary for LMDB
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED" // this is necessary for LMDB
        )
    }
}

fun Any.runtimeJavaVersion(): String = System.getProperty("java.specification.version")
