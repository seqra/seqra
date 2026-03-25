package org.opentaint.ir.go.test

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Cache for benchmark project checkouts.
 * Clones repos at pinned commits for reproducible benchmarks.
 */
object BenchmarkProjectCache {
    private val cacheDir: Path by lazy {
        val dir = Path.of(System.getProperty("goir.benchmark.cache", "/tmp/goir-benchmark"))
        Files.createDirectories(dir)
        dir
    }

    /**
     * Ensure the project is checked out at the specified commit.
     * Returns the directory of the project.
     */
    fun checkout(project: BenchmarkProject): Path {
        val dirName = project.module.replace("/", "_")
        val dir = cacheDir.resolve(dirName)

        if (!Files.exists(dir)) {
            // Clone
            val clone = ProcessBuilder(
                "git", "clone", "--depth", "1", "https://${project.module}.git", dir.toString()
            )
                .redirectErrorStream(true)
                .start()
            val output = clone.inputStream.readAllBytes().decodeToString()
            val ok = clone.waitFor(120, TimeUnit.SECONDS)
            check(ok && clone.exitValue() == 0) {
                "Failed to clone ${project.module}:\n$output"
            }
        }

        // Checkout the pinned commit (if not shallow or already at that commit)
        if (project.commitHash.isNotEmpty()) {
            // Try fetch and checkout; if shallow clone, just use HEAD
            try {
                val fetch = ProcessBuilder("git", "fetch", "--depth", "1", "origin", project.commitHash)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start()
                fetch.waitFor(60, TimeUnit.SECONDS)

                val checkout = ProcessBuilder("git", "checkout", project.commitHash)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start()
                checkout.waitFor(30, TimeUnit.SECONDS)
            } catch (_: Exception) {
                // If fetch fails (shallow clone), just use what we have
            }
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
