package org.opentaint.ir.impl.fs

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class BuildFolderLocationImpl(private val folder: File, override val apiLevel: ApiLevel, private val loadClassesOnlyFrom: List<String>?) : ByteCodeLocation {

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
            return folder.absolutePath + lastModifiedDate
        }

    override suspend fun classesByteCode(): Sequence<Pair<String, InputStream?>> {
        val folderPath = folder.toPath().toAbsolutePath().toString()
        return Files.find(folder.toPath(), Int.MAX_VALUE, { path, _ -> path.toString().endsWith(".class") })
            .asSequence()
            .map {
                val className = it.toAbsolutePath().toString()
                    .substringAfter(folderPath + File.separator)
                    .replace(File.separator, ".")
                    .removeSuffix(".class")
                val stream = when (className.matchesOneOf(loadClassesOnlyFrom)) {
                    true -> Files.newInputStream(it)
                    else -> null
                }
                className to stream
            }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val filePath = Paths.get(folder.absolutePath, *classFullName.split(".").toTypedArray())
        if (!Files.exists(filePath)) {
            return null
        }
        return Files.newInputStream(filePath)
    }

    override fun toString() = folder.absolutePath
}