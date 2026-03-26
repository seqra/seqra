import java.time.Duration
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":go-ir-api"))
    implementation(project(":go-ir-client"))
    implementation(project(":go-ir-codegen"))
    // Test infra classes (in src/main) need JUnit API at compile time
    implementation(libs.junit.jupiter)
    implementation(libs.assertj.core)
    runtimeOnly(libs.junit.platform.launcher)
}

// Resolve Go server binary path
val goServerBinary = rootProject.projectDir.resolve("go-ssa-server/go-ssa-server").absolutePath

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("goir.server.binary", goServerBinary)
    // Parallel execution: run test classes concurrently
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2)
    // Show test output for debugging (started/passed/failed/skipped)
    testLogging {
        events("started", "passed", "skipped", "failed")
        showStandardStreams = false
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark", "roundtrip", "fuzz")
    }
}

val testSourceSet = sourceSets["test"]

tasks.register<Test>("benchmarkTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }
    timeout.set(Duration.ofMinutes(60))
    // Larger heap for real-world projects (some produce 100k+ functions)
    jvmArgs("-Xmx8g", "-Xms1g")
    // Run sequentially — one project at a time to avoid OOM
    maxParallelForks = 1
}

tasks.register<Test>("roundtripTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("roundtrip") }
    jvmArgs("-Xmx4g")
    timeout.set(Duration.ofMinutes(30))
}

tasks.register<Test>("fuzzTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("fuzz") }
    timeout.set(Duration.ofMinutes(30))
}

// ─── Benchmark project download task ────────────────────────────────
// Pre-downloads all benchmark projects so tests can run offline.
// Usage: gradle :go-ir-tests:downloadBenchmarks
// The cache dir defaults to build/benchmark-cache but can be overridden
// with -Pgoir.benchmark.cache=/custom/path

val benchmarkCacheDir = project.findProperty("goir.benchmark.cache")?.toString()
    ?: layout.buildDirectory.dir("benchmark-cache").get().asFile.absolutePath

tasks.register("downloadBenchmarks") {
    group = "verification"
    description = "Pre-download all benchmark Go projects at pinned commits for offline testing"

    // module -> commitHash (pinned for reproducibility)
    val projects = mapOf(
        "github.com/sirupsen/logrus" to "9f0600962f750e07df31280b76cfe3fcebda5fdf",
        "github.com/spf13/cobra" to "61968e893eee2f27696c2fbc8e34fa5c4afaf7c4",
        "github.com/gorilla/websocket" to "e064f32e3674d9d79a8fd417b5bc06fa5c6cad8f",
        "github.com/samber/lo" to "a17e3ac882581ddf4503a27762b355b7c7961eb2",
        "github.com/uber-go/zap" to "0ab0d5aae5986395e2ca497385d977ccd7cdfc5e",
        "github.com/stretchr/testify" to "5f80e4aef7bee125b7e9c0b620edf25f6fc93350",
        "github.com/spf13/viper" to "528f7416c4b56a4948673984b190bf8713f0c3c4",
        "github.com/go-playground/validator/v10" to "b9f1d79d745213827cf712628dfe29211507b011",
        "github.com/golang-migrate/migrate/v4" to "2bd822b3aad4e86f3028324a5b754fc6b4ea54a1",
        "github.com/gin-gonic/gin" to "d3ffc9985281dcf4d3bef604cce4e662b1a327a6",
        "github.com/gofiber/fiber/v2" to "fb4206c367d60cd68807cdcd57cd5b5d012779cb",
        "github.com/go-kit/kit" to "78fbbceece7bbcf073bee814a7772f4397ea756c",
        "github.com/redis/go-redis/v9" to "f37dbd0bc1c4756f6a45298c6c888920b5139593",
        "github.com/jackc/pgx/v5" to "4e4eaedb47b7b3cfba0a1b0a9e6a3f015764f046",
        "github.com/hashicorp/consul" to "09e1f7ca476842857d4b4f091506441b1e31ec68",
        "github.com/prometheus/prometheus" to "729cde895370e11428cb1cbf52f4a71406b7c530",
        "github.com/etcd-io/etcd" to "8dfd8288b27ec0d841ad0356f9fdc767bd86d2c2",
        "github.com/docker/cli" to "9637f1b3648f835005b71c9e11a10dc980f8e47b",
        "github.com/kubernetes/client-go" to "b5cc94ef3b2fa553a6b69d6c7c6fc8e5c90c02ce",
        "github.com/caddyserver/caddy/v2" to "e98ed6232d65790d27bacd13fb49fa5474b9ec93",
    )

    doLast {
        val cacheDir = file(benchmarkCacheDir)
        cacheDir.mkdirs()
        println("Downloading ${projects.size} benchmark projects to: $cacheDir")

        for ((module, commitHash) in projects) {
            val dirName = module.replace("/", "_")
            val targetDir = cacheDir.resolve(dirName)
            if (targetDir.exists()) {
                println("  CACHED: $module @ ${commitHash.take(8)} -> $targetDir")
                continue
            }
            // Strip Go version suffix (e.g. /v2, /v10) for git URL
            val repoPath = module.replace(Regex("/v\\d+$"), "")
            val cloneUrl = "https://$repoPath.git"
            println("  CLONE:  $module @ ${commitHash.take(8)} -> $targetDir")
            // Clone then checkout pinned commit
            val clone = ProcessBuilder("git", "clone", "--depth", "1", cloneUrl, targetDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val cloneOutput = clone.inputStream.readAllBytes().decodeToString()
            val cloneOk = clone.waitFor(180, TimeUnit.SECONDS)
            if (!cloneOk || clone.exitValue() != 0) {
                println("    CLONE FAILED: $cloneOutput")
                targetDir.deleteRecursively()
                continue
            }
            // Fetch and checkout the pinned commit
            if (commitHash.isNotEmpty()) {
                val fetch = ProcessBuilder("git", "fetch", "--depth", "1", "origin", commitHash)
                    .directory(targetDir)
                    .redirectErrorStream(true)
                    .start()
                fetch.inputStream.readAllBytes() // drain
                fetch.waitFor(60, TimeUnit.SECONDS)

                val checkout = ProcessBuilder("git", "checkout", commitHash)
                    .directory(targetDir)
                    .redirectErrorStream(true)
                    .start()
                checkout.inputStream.readAllBytes() // drain
                checkout.waitFor(30, TimeUnit.SECONDS)
            }
            println("    OK")
        }
        println("Done. Set GOIR_BENCHMARK_CACHE=$cacheDir to use in tests.")
    }
}

// benchmarkTest depends on downloadBenchmarks — projects must be pre-downloaded
tasks.named<Test>("benchmarkTest") {
    dependsOn("downloadBenchmarks")
    systemProperty("goir.benchmark.cache", benchmarkCacheDir)
    environment("GOIR_BENCHMARK_CACHE", benchmarkCacheDir)
}
