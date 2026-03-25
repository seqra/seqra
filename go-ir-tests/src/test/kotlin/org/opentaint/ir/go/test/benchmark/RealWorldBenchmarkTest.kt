package org.opentaint.ir.go.test.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentaint.ir.go.test.*

/**
 * Real-world project benchmark (Strategy 1).
 * Clones real Go projects and verifies IR extraction succeeds without errors.
 *
 * Run with: ./gradlew :go-ir-tests:benchmarkTest
 */
@Tag("benchmark")
@ExtendWith(GoIRTestExtension::class)
class RealWorldBenchmarkTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("projects")
    fun `extract IR without errors`(project: BenchmarkProject, builder: GoIRTestBuilder) {
        // 1. Checkout project
        val projectDir = BenchmarkProjectCache.checkout(project)

        // 2. Build IR and measure time
        val startTime = System.nanoTime()
        val prog = builder.buildFromDir(projectDir, *project.patterns.toTypedArray())
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        // 3. Run sanity checker
        val sanityResult = GoIRSanityChecker.check(prog)

        // 4. Collect metrics
        val totalInstructions = prog.allFunctions().sumOf { it.body?.instructionCount ?: 0 }
        val totalBlocks = prog.allFunctions().sumOf { it.body?.blocks?.size ?: 0 }

        val result = BenchmarkResult(
            project = project.module,
            success = sanityResult.isOk,
            extractionTimeMs = elapsedMs,
            packageCount = prog.packages.size,
            functionCount = prog.allFunctions().size,
            instructionCount = totalInstructions,
            blockCount = totalBlocks,
            errorMessages = sanityResult.errors.map { it.message },
        )

        // 5. Print metrics
        println("=== BENCHMARK: ${project.module} ===")
        println("  Time: ${result.extractionTimeMs}ms")
        println("  Packages: ${result.packageCount}")
        println("  Functions: ${result.functionCount}")
        println("  Instructions: ${result.instructionCount}")
        println("  Blocks: ${result.blockCount}")
        if (sanityResult.errors.isNotEmpty()) {
            println("  Errors: ${sanityResult.errors.size}")
            sanityResult.errors.take(5).forEach { println("    - ${it.message}") }
        }
        println("===")

        // 6. Assert
        assertThat(result.extractionTimeMs)
            .withFailMessage("Extraction took ${result.extractionTimeMs}ms, limit is ${project.maxTimeSeconds * 1000}ms")
            .isLessThan(project.maxTimeSeconds * 1000)

        sanityResult.assertNoErrors()
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
                maxTimeSeconds = 120,
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
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/go-redis/redis/v8",
                description = "Redis client",
                tags = listOf("channels", "goroutines", "generics"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/jackc/pgx/v5",
                description = "PostgreSQL driver",
                tags = listOf("complex-types", "binary-protocols"),
                maxTimeSeconds = 180,
            ),
            // --- Large projects (50k+ LoC) ---
            BenchmarkProject(
                module = "github.com/hashicorp/consul",
                description = "Service mesh",
                tags = listOf("goroutines", "select", "rpc"),
                maxTimeSeconds = 600,
                patterns = listOf("./agent/..."),  // subset to avoid timeout
            ),
            BenchmarkProject(
                module = "github.com/prometheus/prometheus",
                description = "Monitoring system",
                tags = listOf("math", "concurrency"),
                maxTimeSeconds = 600,
                patterns = listOf("./model/..."),  // subset
            ),
            BenchmarkProject(
                module = "github.com/etcd-io/etcd",
                description = "Distributed KV store",
                tags = listOf("raft", "channels", "goroutines"),
                maxTimeSeconds = 600,
                patterns = listOf("./client/v3/..."),  // subset
            ),
            BenchmarkProject(
                module = "github.com/docker/cli",
                description = "Docker CLI",
                tags = listOf("cross-platform", "complex-control-flow"),
                maxTimeSeconds = 600,
                patterns = listOf("./cli/..."),  // subset
            ),
            BenchmarkProject(
                module = "github.com/kubernetes/client-go",
                description = "Kubernetes client",
                tags = listOf("generated-code", "deep-hierarchies", "interfaces"),
                maxTimeSeconds = 600,
                patterns = listOf("./tools/..."),  // subset
            ),
            BenchmarkProject(
                module = "github.com/caddyserver/caddy/v2",
                description = "Web server",
                tags = listOf("plugins", "interfaces", "goroutines"),
                maxTimeSeconds = 600,
                patterns = listOf("./modules/caddyhttp/..."),  // subset
            ),
        )
    }
}
