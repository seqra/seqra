package org.opentaint.java.compilation.database.impl.fs

import mu.KLogging
import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.md5
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class BuildFolderLocationImpl(
    private val folder: File,
    override val apiLevel: ApiLevel,
    private val loadClassesOnlyFrom: List<String>?
) : ByteCodeLocation {

    companion object : KLogging()

    override val version = currentVersion

    override val currentVersion: String
        get() {
            var lastModifiedDate = folder.lastModified()
            folder.walk().onEnter {
                val lastModified = it.lastModified()
                if (lastModifiedDate < lastModified) {
                    lastModifiedDate = lastModified
                }
                true
            }
            return (folder.absolutePath + lastModifiedDate).md5()
        }

    override fun refreshed() = BuildFolderLocationImpl(folder, apiLevel, loadClassesOnlyFrom)

    override suspend fun loader(): ByteCodeLoader? {
        try {
            val classes = dirClasses()?.mapValues { (className, file) ->
                Files.newInputStream(file.toPath()).takeIf { className.matchesOneOf(loadClassesOnlyFrom) }
            } ?: return null

            return ByteCodeLoaderImpl(this, classes) {
                dirClasses()?.filterKeys { className ->
                    !className.matchesOneOf(loadClassesOnlyFrom)
                }.orEmpty().mapValues { (_, file) ->
                    Files.newInputStream(file.toPath())
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "error loading classes from build folder: ${folder.absolutePath}. returning empty loader" }
            return null
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val filePath = Paths.get(folder.absolutePath, *classFullName.split(".").toTypedArray())
        if (!Files.exists(filePath)) {
            return null
        }
        return Files.newInputStream(filePath)
    }

    private fun dirClasses(): Map<String, File>? {
        if (!folder.exists() || folder.isFile) {
            return null
        }
        val folderPath = folder.toPath().toAbsolutePath().toString()

        return Files.find(folder.toPath(), Int.MAX_VALUE, { path, _ -> path.toString().endsWith(".class") })
            .asSequence().map {
                val className = it.toAbsolutePath().toString()
                    .substringAfter(folderPath + File.separator)
                    .replace(File.separator, ".")
                    .removeSuffix(".class")
                className to it.toFile()
            }.toMap()

    }

    override fun toString() = folder.absolutePath
}