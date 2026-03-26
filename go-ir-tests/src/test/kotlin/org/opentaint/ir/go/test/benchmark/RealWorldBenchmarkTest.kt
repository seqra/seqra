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
            // ─── 30 new web application projects ───
            // --- Small REST APIs / web apps ---
            BenchmarkProject(
                module = "github.com/zhashkevych/todo-app",
                commitHash = "1789ed69bd5f449bd605e23a92a1e87a83cb9fda",
                description = "Todo REST API (Gin + PostgreSQL)",
                tags = listOf("gin", "rest-api", "crud"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/cheatsnake/shadify",
                commitHash = "a2ff8f46ac748c9fcfc43ad5661c8c56e4920a8e",
                description = "Puzzle/game API service",
                tags = listOf("fiber", "rest-api", "algorithms"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/mrusme/journalist",
                commitHash = "9b62d2b277a57e8088a6971b6c1565370b284428",
                description = "RSS/Atom news aggregator",
                tags = listOf("rest-api", "rss"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/bagashiz/go-pos",
                commitHash = "30833c4511e18ad9f02210e24f2a3b08dc84518e",
                description = "Point-of-sale REST API",
                tags = listOf("gin", "rest-api", "crud"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/CareyWang/MyUrls",
                commitHash = "f56a92f944559d6ec6dff35a047294fe68379d98",
                description = "Short URL service",
                tags = listOf("gin", "url-shortener"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/barats/ohUrlShortener",
                commitHash = "a738952f0a949fdfd3590c74257bcd313124dd8d",
                description = "URL shortener with admin panel",
                tags = listOf("gin", "url-shortener", "crud"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/koddr/tutorial-go-fiber-rest-api",
                commitHash = "205901ed3f955c7c5cfea9e2cc0021f50baf408f",
                description = "Fiber REST API tutorial",
                tags = listOf("fiber", "rest-api"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/wpcodevo/golang-fiber-jwt",
                commitHash = "6737e11749bbd01c6122078c77696e49fbeca540",
                description = "Fiber JWT auth example",
                tags = listOf("fiber", "jwt", "auth"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/snykk/go-rest-boilerplate",
                commitHash = "ef4bb28f8d9150681b84119b3bac5e1a97878b00",
                description = "Go REST API boilerplate (Gin)",
                tags = listOf("gin", "rest-api", "boilerplate"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/bitcav/nitr",
                commitHash = "fa3b1708352940a8af3cb7293cd915f5def4bbad",
                description = "System info server",
                tags = listOf("rest-api", "system-info"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/jwma/jump-jump",
                commitHash = "96e8c35f823a0fb607f5b6ae707afee0c8b34bd0",
                description = "URL shortener with analytics",
                tags = listOf("gin", "url-shortener", "redis"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/restuwahyu13/go-rest-api",
                commitHash = "0a0452090edca33eb2637ced0937e7c20565b4a4",
                description = "Go REST API example (Gin)",
                tags = listOf("gin", "rest-api"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/GolangLessons/url-shortener",
                commitHash = "c3987f66469a8d0769add18521adb9023520be95",
                description = "URL shortener tutorial",
                tags = listOf("fiber", "url-shortener"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/Mamin78/Parham-Food-BackEnd",
                commitHash = "c703033d634c4d7d3136b9ffb47a1eed0424069b",
                description = "Food ordering backend",
                tags = listOf("rest-api", "e-commerce"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/sirini/goapi",
                commitHash = "c3dd8b220607b66e73973010aa1ccae26c1471b8",
                description = "Community forum API server",
                tags = listOf("rest-api", "forum"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/rasadov/EcommerceAPI",
                commitHash = "ddc95abe518b77304b2cacdaf973c862e1285be3",
                description = "E-commerce REST API",
                tags = listOf("rest-api", "e-commerce"),
                maxTimeSeconds = 60,
            ),
            BenchmarkProject(
                module = "github.com/zacscoding/gin-rest-api-example",
                commitHash = "4d1c00da6ae1c77187dbb749f753307cdf784848",
                description = "Gin REST API example",
                tags = listOf("gin", "rest-api", "example"),
                maxTimeSeconds = 60,
            ),
            // --- Medium web apps ---
            BenchmarkProject(
                module = "github.com/wa8n/wblog",
                commitHash = "d96c11c1471f409520a989a99d22affc84a65c1a",
                description = "Blog engine (Gin + GORM)",
                tags = listOf("gin", "blog", "gorm"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/gbrayhan/microservices-go",
                commitHash = "b63ba3de1132c6461359ac70ebbbec64e99bf128",
                description = "Microservices architecture example",
                tags = listOf("microservices", "rest-api", "clean-architecture"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/netlify/gocommerce",
                commitHash = "fc6215a8af06b407e94e2f6680f6c2b5a82a4278",
                description = "E-commerce API (Netlify)",
                tags = listOf("e-commerce", "rest-api", "jwt"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/quangdangfit/goshop",
                commitHash = "75ded3a930b5d634ea81175393e2d337a28740fe",
                description = "E-commerce shop backend",
                tags = listOf("gin", "e-commerce", "mongodb"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/benbjohnson/wtf",
                commitHash = "05bc90c940d5f9e2490fc93cf467d9e8aa48ad63",
                description = "WTF Dial app (exemplary Go project)",
                tags = listOf("http", "sqlite", "clean-architecture"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/go-sonic/sonic",
                commitHash = "53db5562b4fb8da9b7a7be694233ff7bcff0b35b",
                description = "Blog platform (Go Sonic)",
                tags = listOf("gin", "blog", "cms"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/gotify/server",
                commitHash = "061053711ffae46b1cbd7c26037efec0a0c6487d",
                description = "Push notification server",
                tags = listOf("gin", "websocket", "push-notifications"),
                maxTimeSeconds = 120,
            ),
            BenchmarkProject(
                module = "github.com/shurco/litecart",
                commitHash = "c22424b62ff9426c79befa8eb94839b8e1ee3488",
                description = "Lightweight e-commerce platform",
                tags = listOf("e-commerce", "sqlite", "htmx"),
                maxTimeSeconds = 120,
            ),
            // --- Larger web apps / frameworks (may need subsets) ---
            BenchmarkProject(
                module = "github.com/ArtalkJS/Artalk",
                commitHash = "71a651153bc68267a8b86c4ee88c597bd4097071",
                description = "Comment system (self-hosted)",
                tags = listOf("rest-api", "comments", "fiber"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/go-kratos/kratos",
                commitHash = "1393e857d98dc346bffa449d8ca1e96c0b1f355c",
                description = "Microservice framework",
                tags = listOf("framework", "grpc", "microservices"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/miniflux/v2",
                commitHash = "f5bfd5a70c0fa518ab1acad5c4d2c30072138bfe",
                description = "Minimalist feed reader",
                tags = listOf("rss", "web-app", "postgresql"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/casdoor/casdoor",
                commitHash = "6f18f671382b598cd5282954137c2b9d7e6d595a",
                description = "Identity/access management platform",
                tags = listOf("auth", "oauth", "web-app"),
                maxTimeSeconds = 180,
            ),
            BenchmarkProject(
                module = "github.com/woodpecker-ci/woodpecker",
                commitHash = "b9ba31ebe863f6aff1f046c91ac20688b8c94778",
                description = "CI/CD engine",
                tags = listOf("ci-cd", "docker", "grpc"),
                maxTimeSeconds = 240,
                patterns = listOf("./server/..."),
            ),
        )
    }
}
