plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(project(":go:go-ir-api"))
    implementation(project(":go:go-ir-client"))
    implementation(project(":go:go-ir-codegen"))
    // Test infra classes (in src/main) need JUnit API at compile time
    implementation("org.junit.jupiter:junit-jupiter:5.11.4")
    implementation("org.assertj:assertj-core:3.27.3")
    runtimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

// Resolve Go server binary path
val goServerBinary = project.parent!!.projectDir.resolve("go-ssa-server/go-ssa-server").absolutePath

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("goir.server.binary", goServerBinary)

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
        excludeTags("benchmark", "roundtrip")
    }
}

val testSourceSet = sourceSets["test"]

tasks.register<Test>("benchmarkTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }

    // Larger heap for real-world projects (some produce 100k+ functions)
    jvmArgs("-Xmx8g", "-Xms1g")
}

tasks.register<Test>("roundtripTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("roundtrip") }
    jvmArgs("-Xmx4g")
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
        // --- Original 20 projects (libraries + infrastructure) ---
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
        // --- 30 new web application projects ---
        "github.com/zhashkevych/todo-app" to "1789ed69bd5f449bd605e23a92a1e87a83cb9fda",
        "github.com/go-sonic/sonic" to "53db5562b4fb8da9b7a7be694233ff7bcff0b35b",
        "github.com/cheatsnake/shadify" to "a2ff8f46ac748c9fcfc43ad5661c8c56e4920a8e",
        "github.com/mrusme/journalist" to "9b62d2b277a57e8088a6971b6c1565370b284428",
        "github.com/netlify/gocommerce" to "fc6215a8af06b407e94e2f6680f6c2b5a82a4278",
        "github.com/bagashiz/go-pos" to "30833c4511e18ad9f02210e24f2a3b08dc84518e",
        "github.com/gbrayhan/microservices-go" to "b63ba3de1132c6461359ac70ebbbec64e99bf128",
        "github.com/CareyWang/MyUrls" to "f56a92f944559d6ec6dff35a047294fe68379d98",
        "github.com/barats/ohUrlShortener" to "a738952f0a949fdfd3590c74257bcd313124dd8d",
        "github.com/koddr/tutorial-go-fiber-rest-api" to "205901ed3f955c7c5cfea9e2cc0021f50baf408f",
        "github.com/wpcodevo/golang-fiber-jwt" to "6737e11749bbd01c6122078c77696e49fbeca540",
        "github.com/snykk/go-rest-boilerplate" to "ef4bb28f8d9150681b84119b3bac5e1a97878b00",
        "github.com/bitcav/nitr" to "fa3b1708352940a8af3cb7293cd915f5def4bbad",
        "github.com/jwma/jump-jump" to "96e8c35f823a0fb607f5b6ae707afee0c8b34bd0",
        "github.com/restuwahyu13/go-rest-api" to "0a0452090edca33eb2637ced0937e7c20565b4a4",
        "github.com/GolangLessons/url-shortener" to "c3987f66469a8d0769add18521adb9023520be95",
        "github.com/Mamin78/Parham-Food-BackEnd" to "c703033d634c4d7d3136b9ffb47a1eed0424069b",
        "github.com/sirini/goapi" to "c3dd8b220607b66e73973010aa1ccae26c1471b8",
        "github.com/rasadov/EcommerceAPI" to "ddc95abe518b77304b2cacdaf973c862e1285be3",
        "github.com/wa8n/wblog" to "d96c11c1471f409520a989a99d22affc84a65c1a",
        "github.com/zacscoding/gin-rest-api-example" to "4d1c00da6ae1c77187dbb749f753307cdf784848",
        "github.com/quangdangfit/goshop" to "75ded3a930b5d634ea81175393e2d337a28740fe",
        "github.com/benbjohnson/wtf" to "05bc90c940d5f9e2490fc93cf467d9e8aa48ad63",
        "github.com/gotify/server" to "061053711ffae46b1cbd7c26037efec0a0c6487d",
        "github.com/ArtalkJS/Artalk" to "71a651153bc68267a8b86c4ee88c597bd4097071",
        "github.com/shurco/litecart" to "c22424b62ff9426c79befa8eb94839b8e1ee3488",
        "github.com/go-kratos/kratos" to "1393e857d98dc346bffa449d8ca1e96c0b1f355c",
        "github.com/miniflux/v2" to "f5bfd5a70c0fa518ab1acad5c4d2c30072138bfe",
        "github.com/woodpecker-ci/woodpecker" to "b9ba31ebe863f6aff1f046c91ac20688b8c94778",
        "github.com/casdoor/casdoor" to "6f18f671382b598cd5282954137c2b9d7e6d595a",
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
            // Strip Go version suffix (e.g. /v2, /v10) for git URL,
            // but only when there are 3+ path segments after host (github.com/org/repo/vN).
            // Don't strip when the repo itself is named vN (e.g. github.com/miniflux/v2).
            val segments = module.removePrefix("github.com/").split("/")
            val repoPath = if (segments.size >= 3 && segments.last().matches(Regex("v\\d+"))) {
                module.replace(Regex("/v\\d+$"), "")
            } else {
                module
            }
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
