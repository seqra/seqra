package org.opentaint.ir.impl.fs

import com.google.common.hash.Hashing
import mu.KLogging
import org.opentaint.ir.api.jvm.LocationType
import org.opentaint.ir.util.io.mapReadonly
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import kotlin.text.Charsets.UTF_8

class BuildFolderLocation(folder: File) : AbstractByteCodeLocation(folder) {

    companion object : KLogging()

    @Suppress("UnstableApiUsage")
    override val currentHash: BigInteger
        get() {
            return Hashing.sha256().newHasher().let { h ->
                jarOrFolder.walk().filter { it.isFile }.sortedBy { it.name }.forEach {
                    h.putString(it.name, UTF_8)
                    h.putBytes(it.mapReadonly())
                }
                BigInteger(h.hash().asBytes())
            }
        }

    override val type: LocationType
        get() = LocationType.APP

    override fun createRefreshed() = BuildFolderLocation(jarOrFolder)

    override val classes: Map<String, ByteArray>
        get() {
            try {
                return dirClasses?.mapValues { (_, file) ->
                    Files.newInputStream(file.toPath()).use { it.readBytes() }
                } ?: return emptyMap()
            } catch (e: Exception) {
                logger.warn(e) { "error loading classes from build folder: ${jarOrFolder.absolutePath}. returning empty loader" }
                return emptyMap()
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
