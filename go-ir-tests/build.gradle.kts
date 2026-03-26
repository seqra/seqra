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
    description = "Pre-download all benchmark Go projects for offline testing"

    val modules = listOf(
        "github.com/sirupsen/logrus",
        "github.com/spf13/cobra",
        "github.com/gorilla/websocket",
        "github.com/samber/lo",
        "github.com/uber-go/zap",
        "github.com/stretchr/testify",
        "github.com/spf13/viper",
        "github.com/go-playground/validator/v10",
        "github.com/golang-migrate/migrate/v4",
        "github.com/gin-gonic/gin",
        "github.com/gofiber/fiber/v2",
        "github.com/go-kit/kit",
        "github.com/redis/go-redis/v9",
        "github.com/jackc/pgx/v5",
        "github.com/hashicorp/consul",
        "github.com/prometheus/prometheus",
        "github.com/etcd-io/etcd",
        "github.com/docker/cli",
        "github.com/kubernetes/client-go",
        "github.com/caddyserver/caddy/v2",
    )

    doLast {
        val cacheDir = file(benchmarkCacheDir)
        cacheDir.mkdirs()
        println("Downloading ${modules.size} benchmark projects to: $cacheDir")

        for (module in modules) {
            val dirName = module.replace("/", "_")
            val targetDir = cacheDir.resolve(dirName)
            if (targetDir.exists()) {
                println("  CACHED: $module -> $targetDir")
                continue
            }
            // Strip Go version suffix (e.g. /v2, /v10) for git URL
            val repoPath = module.replace(Regex("/v\\d+$"), "")
            val cloneUrl = "https://$repoPath.git"
            println("  CLONE:  $module ($cloneUrl) -> $targetDir")
            val proc = ProcessBuilder("git", "clone", "--depth", "1", cloneUrl, targetDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.readAllBytes().decodeToString()
            val ok = proc.waitFor(180, TimeUnit.SECONDS)
            if (!ok || proc.exitValue() != 0) {
                println("    FAILED: $output")
                targetDir.deleteRecursively()
            } else {
                println("    OK")
            }
        }
        println("Done. Set GOIR_BENCHMARK_CACHE=$cacheDir to use in tests.")
    }
}

// Pass cache dir to benchmark tests via system property + env
tasks.named<Test>("benchmarkTest") {
    systemProperty("goir.benchmark.cache", benchmarkCacheDir)
    environment("GOIR_BENCHMARK_CACHE", benchmarkCacheDir)
}
