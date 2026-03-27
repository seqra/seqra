package org.opentaint.ir.go.test

import java.nio.file.Files
import java.nio.file.Path

/**
 * Read-only cache for benchmark project checkouts.
 *
 * Projects must be pre-downloaded via the Gradle `downloadBenchmarks` task.
 * This class only resolves the local path — it never clones or fetches.
 */
object BenchmarkProjectCache {
    private val cacheDir: Path by lazy {
        val envPath = System.getenv("GOIR_BENCHMARK_CACHE")
        val propPath = System.getProperty("goir.benchmark.cache")
        Path.of(
            envPath ?: propPath
                ?: error("Benchmark cache dir not set. Run: gradle :go-ir-tests:downloadBenchmarks")
        )
    }

    /**
     * Returns the local directory for a benchmark project.
     *
     * @throws IllegalStateException if the project has not been downloaded
     */
    fun projectDir(project: BenchmarkProject): Path {
        val dirName = project.module.replace("/", "_")
        val dir = cacheDir.resolve(dirName)
        check(Files.isDirectory(dir)) {
            "Benchmark project '${project.module}' not found at $dir. " +
                "Run: gradle :go-ir-tests:downloadBenchmarks"
        }
        return dir
    }
}

data class BenchmarkProject(
    val module: String,
    val commitHash: String = "",
    val patterns: List<String> = listOf("./..."),
    val tags: List<String> = emptyList(),
    val maxTimeSeconds: Long = 120,
    val description: String = "",
)

data class BenchmarkResult(
    val project: String,
    val success: Boolean,
    val extractionTimeMs: Long,
    val packageCount: Int,
    val functionCount: Int,
    val instructionCount: Int,
    val blockCount: Int,
    val errorMessages: List<String> = emptyList(),
)
