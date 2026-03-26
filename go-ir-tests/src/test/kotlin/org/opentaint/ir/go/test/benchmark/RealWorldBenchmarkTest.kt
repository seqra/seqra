package org.opentaint.ir.go.test.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentaint.ir.go.test.*
import java.util.concurrent.TimeUnit

/**
 * Real-world project benchmark (Strategy 1).
 * Loads pre-downloaded Go projects and verifies IR extraction succeeds without errors.
 *
 * Projects are downloaded by the Gradle `downloadBenchmarks` task, which runs
 * automatically before this test task.
 *
 * Each test spins up its own [GoIRTestBuilder] (and Go SSA server) so that a crash
 * processing one project does not affect subsequent projects.
 *
 * Run with: gradle :go-ir-tests:benchmarkTest
 */
@Tag("benchmark")
class RealWorldBenchmarkTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("projects")
    @Timeout(value = 5, unit = TimeUnit.MINUTES) // per-test hard timeout
    fun `extract IR without errors`(project: BenchmarkProject) {
        // 1. Resolve pre-downloaded project directory
        val projectDir = BenchmarkProjectCache.projectDir(project)

        // 2. Build IR with fresh server per project (isolates crashes)
        GoIRTestBuilder().use { builder ->
            val buildResult = try {
                builder.buildFromDirWithTimings(projectDir, *project.patterns.toTypedArray())
            } catch (e: OutOfMemoryError) {
                throw AssertionError("OutOfMemoryError for ${project.module}", e)
            } catch (e: Exception) {
                throw AssertionError("IR build failed for ${project.module}: ${e.message}", e)
            }
            val prog = buildResult.program
            val timings = buildResult.timings

            // 3. Run sanity checker (light mode — skip expensive checks)
            val sanityStart = System.nanoTime()
            val sanityResult = GoIRSanityChecker.check(prog, deep = false)
            val sanityMs = (System.nanoTime() - sanityStart) / 1_000_000

            // 4. Collect metrics
            val totalInstructions = prog.allFunctions().sumOf { it.body?.instructionCount ?: 0 }
            val totalBlocks = prog.allFunctions().sumOf { it.body?.blocks?.size ?: 0 }

            // 5. Print detailed timing and metrics
            println("=== BENCHMARK: ${project.module} @ ${project.commitHash.take(8)} ===")
            println("  Server (SSA):  ${timings.serverBuildMs}ms")
            println("  Deserialize:   ${timings.deserializeMs}ms")
            println("  Total gRPC:    ${timings.totalMs}ms")
            println("  Sanity check:  ${sanityMs}ms")
            println("  ──────────────────")
            println("  Packages:      ${prog.packages.size}")
            println("  Functions:     ${prog.allFunctions().size}")
            println("  Instructions:  $totalInstructions")
            println("  Blocks:        $totalBlocks")
            if (sanityResult.errors.isNotEmpty()) {
                println("  ERRORS:        ${sanityResult.errors.size}")
                sanityResult.errors.take(10).forEach { println("    - [${it.category}] ${it.message}") }
            }
            if (sanityResult.warnings.isNotEmpty()) {
                println("  Warnings:      ${sanityResult.warnings.size}")
            }
            println("===")

            // 6. Assert — time within limit
            assertThat(timings.totalMs)
                .withFailMessage("Extraction took ${timings.totalMs}ms, limit is ${project.maxTimeSeconds * 1000}ms")
                .isLessThan(project.maxTimeSeconds * 1000)

            // 7. Assert — no sanity errors
            sanityResult.assertNoErrors()
        }
    }

    companion object {
        @JvmStatic
        fun projects(): List<BenchmarkProject> = listOf(
            // --- Small libraries (< 10k LoC) ---
            BenchmarkProject(
                module = "github.com/sirupsen/logrus",
                commitHash = "9f0600962f750e07df31280b76cfe3fcebda5fdf",
                description = "Structured logging library",
                tags = listOf("interfaces", "concurrency"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/spf13/cobra",
                commitHash = "61968e893eee2f27696c2fbc8e34fa5c4afaf7c4",
                description = "CLI framework",
                tags = listOf("closures", "variadic"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/gorilla/websocket",
                commitHash = "e064f32e3674d9d79a8fd417b5bc06fa5c6cad8f",
                description = "WebSocket implementation",
                tags = listOf("goroutines", "channels"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/samber/lo",
                commitHash = "a17e3ac882581ddf4503a27762b355b7c7961eb2",
                description = "Generic utility library",
                tags = listOf("generics"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/uber-go/zap",
                commitHash = "0ab0d5aae5986395e2ca497385d977ccd7cdfc5e",
                description = "High-performance logging",
                tags = listOf("performance", "sync-pool"),
                maxTimeSeconds = 120,
            ),
            // --- Medium libraries (10-30k LoC) ---
            BenchmarkProject(
                module = "github.com/stretchr/testify",
                commitHash = "5f80e4aef7bee125b7e9c0b620edf25f6fc93350",
                description = "Testing utilities",
                tags = listOf("reflection", "type-assertions", "variadic"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/spf13/viper",
                commitHash = "528f7416c4b56a4948673984b190bf8713f0c3c4",
                description = "Configuration library",
                tags = listOf("reflection", "maps", "type-assertions"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/go-playground/validator/v10",
                commitHash = "b9f1d79d745213827cf712628dfe29211507b011",
                description = "Struct validation",
                tags = listOf("reflection", "struct-tags"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/golang-migrate/migrate/v4",
                commitHash = "2bd822b3aad4e86f3028324a5b754fc6b4ea54a1",
                description = "Database migrations",
                tags = listOf("interfaces", "multiple-backends"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/gin-gonic/gin",
                commitHash = "d3ffc9985281dcf4d3bef604cce4e662b1a327a6",
                description = "Web framework",
                tags = listOf("interfaces", "middleware", "reflection"),
                maxTimeSeconds = 180,
            ),
            // --- Larger libraries (20-50k LoC) ---
            BenchmarkProject(
                module = "github.com/gofiber/fiber/v2",
                commitHash = "fb4206c367d60cd68807cdcd57cd5b5d012779cb",
                description = "Fast web framework",
                tags = listOf("generics", "http"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/go-kit/kit",
                commitHash = "78fbbceece7bbcf073bee814a7772f4397ea756c",
                description = "Microservices toolkit",
                tags = listOf("interfaces", "composition"),
                maxTimeSeconds = 240,
            ),
            BenchmarkProject(
                module = "github.com/redis/go-redis/v9",
                commitHash = "f37dbd0bc1c4756f6a45298c6c888920b5139593",
                description = "Redis client",
                tags = listOf("channels", "goroutines"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/jackc/pgx/v5",
                commitHash = "4e4eaedb47b7b3cfba0a1b0a9e6a3f015764f046",
                description = "PostgreSQL driver",
                tags = listOf("complex-types", "binary-protocols"),
                maxTimeSeconds = 180,
            ),
            // --- Large projects (50k+ LoC) — subsets only ---
            BenchmarkProject(
                module = "github.com/hashicorp/consul",
                commitHash = "09e1f7ca476842857d4b4f091506441b1e31ec68",
                description = "Service mesh",
                tags = listOf("goroutines", "select", "rpc"),
                maxTimeSeconds = 240,
                patterns = listOf("./api/..."),
            ),
            BenchmarkProject(
                module = "github.com/prometheus/prometheus",
                commitHash = "729cde895370e11428cb1cbf52f4a71406b7c530",
                description = "Monitoring system",
                tags = listOf("math", "concurrency"),
                maxTimeSeconds = 240,
                patterns = listOf("./model/..."),
            ),
            BenchmarkProject(
                module = "github.com/etcd-io/etcd",
                commitHash = "8dfd8288b27ec0d841ad0356f9fdc767bd86d2c2",
                description = "Distributed KV store",
                tags = listOf("raft", "channels", "goroutines"),
                maxTimeSeconds = 240,
                patterns = listOf("./client/v3/..."),
            ),
            BenchmarkProject(
                module = "github.com/docker/cli",
                commitHash = "9637f1b3648f835005b71c9e11a10dc980f8e47b",
                description = "Docker CLI",
                tags = listOf("cross-platform", "complex-control-flow"),
                maxTimeSeconds = 240,
                patterns = listOf("./cli/command/..."),
            ),
            BenchmarkProject(
                module = "github.com/kubernetes/client-go",
                commitHash = "b5cc94ef3b2fa553a6b69d6c7c6fc8e5c90c02ce",
                description = "Kubernetes client",
                tags = listOf("generated-code", "deep-hierarchies", "interfaces"),
                maxTimeSeconds = 240,
                patterns = listOf("./tools/cache/..."),
            ),
            BenchmarkProject(
                module = "github.com/caddyserver/caddy/v2",
                commitHash = "e98ed6232d65790d27bacd13fb49fa5474b9ec93",
                description = "Web server",
                tags = listOf("plugins", "interfaces", "goroutines"),
                maxTimeSeconds = 240,
                patterns = listOf("./modules/caddyhttp/..."),
            ),
        )
    }
}
