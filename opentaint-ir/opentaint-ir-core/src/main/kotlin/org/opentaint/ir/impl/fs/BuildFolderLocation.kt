package org.opentaint.ir.impl.fs

import mu.KLogging
import org.opentaint.ir.api.LocationType
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class BuildFolderLocation(folder: File) : AbstractByteCodeLocation(folder) {

    companion object : KLogging()

    override val type: LocationType
        get() = LocationType.APP

    override val fileSystemId by lazy { currentHash() }

    override fun currentHash(): String {
        var lastModifiedDate = jarOrFolder.lastModified()
        jarOrFolder.walk().onEnter {
            val lastModified = it.lastModified()
            if (lastModifiedDate < lastModified) {
                lastModifiedDate = lastModified
            }
            true
        }
        return jarOrFolder.absolutePath + lastModifiedDate
    }

    override fun createRefreshed() = BuildFolderLocation(jarOrFolder)

    override val classes: Map<String, ByteArray>?
        get() {
            try {
                return dirClasses?.mapValues { (_, file) ->
                    Files.newInputStream(file.toPath()).use { it.readBytes() }
                } ?: return null
            } catch (e: Exception) {
                logger.warn(e) { "error loading classes from build folder: ${jarOrFolder.absolutePath}. returning empty loader" }
                return null
            }
        }

    override val classNames: Set<String>
        get() = dirClasses?.keys.orEmpty()

    override fun resolve(classFullName: String): ByteArray? {
        val pathArray = classFullName.split(".").toTypedArray()
        pathArray[pathArray.size - 1] = pathArray[pathArray.size - 1] + ".class"
        val filePath = Paths.get(jarOrFolder.absolutePath, *pathArray)
        if (!Files.exists(filePath)) {
            return null
        }
        return Files.newInputStream(filePath).use { it.readBytes() }
    }

    private val dirClasses: Map<String, File>?
        get() {
            if (!jarOrFolder.exists() || jarOrFolder.isFile) {
                return null
            }
            val folderPath = jarOrFolder.toPath().toAbsolutePath().toString()

            return Files.find(jarOrFolder.toPath(), Int.MAX_VALUE, { path, _ -> path.toString().endsWith(".class") })
                .asSequence().map {
                    val className = it.toAbsolutePath().toString()
                        .substringAfter(folderPath + File.separator)
                        .replace(File.separator, ".")
                        .removeSuffix(".class")
                    className to it.toFile()
                }.toMap()

        }

    override fun toString() = jarOrFolder.absolutePath

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is BuildFolderLocation) {
            return false
        }
        return other.jarOrFolder == jarOrFolder
    }

    override fun hashCode(): Int {
        return jarOrFolder.hashCode()
    }
}