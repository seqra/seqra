package org.opentaint.go.config

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createTempDirectory
import kotlin.streams.asSequence

object GoDefaultModelLoader {
    private const val MODEL_ROOT = "/model/go/dataflow"

    private val unpackedModelPaths: List<Path> by lazy {
        loadModelPaths()
    }

    fun modelPaths(): List<Path> = unpackedModelPaths

    private fun loadModelPaths(): List<Path> {
        val resource = javaClass.getResource(MODEL_ROOT) ?: return emptyList()
        return when (resource.protocol) {
            "file" -> findModelModules(Path.of(resource.toURI()))
            "jar" -> unpackJarModels(resource.toURI())
            else -> error("Unsupported bundled Go model resource protocol: ${resource.protocol}")
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun unpackJarModels(resource: URI): List<Path> {
        val unpacked = createTempDirectory("opentaint-go-models")
        FileSystems.newFileSystem(resource, emptyMap<String, Any>()).use { fs ->
            fs.getPath(MODEL_ROOT).copyToRecursively(
                unpacked,
                followLinks = false,
                overwrite = false,
            )
        }
        return findModelModules(unpacked)
    }

    private fun findModelModules(root: Path): List<Path> = Files.list(root).use { paths ->
        paths.asSequence()
            .filter { Files.isDirectory(it) && Files.isRegularFile(it.resolve("go.mod")) }
            .sortedBy { it.fileName.toString() }
            .toList()
    }
}
