package org.opentaint.ir.go.test.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentaint.ir.go.test.*
import java.util.concurrent.TimeUnit

/**
 * Real-world project benchmark (Strategy 1).
 * Clones real Go projects and verifies IR extraction succeeds without errors.
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
        // 1. Checkout project (skip test if clone fails)
        val cloneStart = System.nanoTime()
        val projectDir = try {
            BenchmarkProjectCache.checkout(project)
        } catch (e: Exception) {
            System.err.println("SKIP ${project.module}: checkout failed: ${e.message}")
            assumeTrue(false, "Could not checkout ${project.module}: ${e.message}")
            return
        }
        val cloneMs = (System.nanoTime() - cloneStart) / 1_000_000

        // 2. Build IR with fresh server per project (isolates crashes)
        GoIRTestBuilder().use { builder ->
            val buildResult = try {
                builder.buildFromDirWithTimings(projectDir, *project.patterns.toTypedArray())
            } catch (e: OutOfMemoryError) {
                System.err.println("SKIP ${project.module}: OutOfMemoryError during IR build")
                assumeTrue(false, "OutOfMemoryError for ${project.module}")
                return
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
            println("=== BENCHMARK: ${project.module} ===")
            println("  Clone:         ${cloneMs}ms")
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
                description = "Structured logging library",
                tags = listOf("interfaces", "concurrency"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/spf13/cobra",
                description = "CLI framework",
                tags = listOf("closures", "variadic"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/gorilla/websocket",
                description = "WebSocket implementation",
                tags = listOf("goroutines", "channels"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/samber/lo",
                description = "Generic utility library",
                tags = listOf("generics"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/uber-go/zap",
                description = "High-performance logging",
                tags = listOf("performance", "sync-pool"),
                maxTimeSeconds = 120,
            ),
            // --- Medium libraries (10-30k LoC) ---
            BenchmarkProject(
                module = "github.com/stretchr/testify",
                description = "Testing utilities",
                tags = listOf("reflection", "type-assertions", "variadic"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/spf13/viper",
                description = "Configuration library",
                tags = listOf("reflection", "maps", "type-assertions"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/go-playground/validator/v10",
                description = "Struct validation",
                tags = listOf("reflection", "struct-tags"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/golang-migrate/migrate/v4",
                description = "Database migrations",
                tags = listOf("interfaces", "multiple-backends"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/gin-gonic/gin",
                description = "Web framework",
                tags = listOf("interfaces", "middleware", "reflection"),
                maxTimeSeconds = 180,
            ),
            // --- Larger libraries (20-50k LoC) ---
            BenchmarkProject(
                module = "github.com/gofiber/fiber/v2",
                description = "Fast web framework",
                tags = listOf("generics", "http"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/go-kit/kit",
                description = "Microservices toolkit",
                tags = listOf("interfaces", "composition"),
                maxTimeSeconds = 240,
            ),
            BenchmarkProject(
                module = "github.com/redis/go-redis/v9",
                description = "Redis client",
                tags = listOf("channels", "goroutines"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/jackc/pgx/v5",
                description = "PostgreSQL driver",
                tags = listOf("complex-types", "binary-protocols"),
                maxTimeSeconds = 180,
            ),
            // --- Large projects (50k+ LoC) — subsets only ---
            BenchmarkProject(
                module = "github.com/hashicorp/consul",
                description = "Service mesh",
                tags = listOf("goroutines", "select", "rpc"),
                maxTimeSeconds = 240,
                patterns = listOf("./api/..."),
            ),
            BenchmarkProject(
                module = "github.com/prometheus/prometheus",
                description = "Monitoring system",
                tags = listOf("math", "concurrency"),
                maxTimeSeconds = 240,
                patterns = listOf("./model/..."),
            ),
            BenchmarkProject(
                module = "github.com/etcd-io/etcd",
                description = "Distributed KV store",
                tags = listOf("raft", "channels", "goroutines"),
                maxTimeSeconds = 240,
                patterns = listOf("./client/v3/..."),
            ),
            BenchmarkProject(
                module = "github.com/docker/cli",
                description = "Docker CLI",
                tags = listOf("cross-platform", "complex-control-flow"),
                maxTimeSeconds = 240,
                patterns = listOf("./cli/command/..."),
            ),
            BenchmarkProject(
                module = "github.com/kubernetes/client-go",
                description = "Kubernetes client",
                tags = listOf("generated-code", "deep-hierarchies", "interfaces"),
                maxTimeSeconds = 240,
                patterns = listOf("./tools/cache/..."),
            ),
            BenchmarkProject(
                module = "github.com/caddyserver/caddy/v2",
                description = "Web server",
                tags = listOf("plugins", "interfaces", "goroutines"),
                maxTimeSeconds = 240,
                patterns = listOf("./modules/caddyhttp/..."),
            ),
        )
    }
}
