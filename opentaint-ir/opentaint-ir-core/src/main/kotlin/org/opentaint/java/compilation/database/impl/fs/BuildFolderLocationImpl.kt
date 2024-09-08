package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
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

    override suspend fun loader(): ByteCodeLoader {
        val folderPath = folder.toPath().toAbsolutePath().toString()

        val loadedSync = arrayListOf<Pair<String, InputStream>>()
        val loadedAsync = arrayListOf<Pair<String, () -> InputStream>>()

        Files.find(folder.toPath(), Int.MAX_VALUE, { path, _ -> path.toString().endsWith(".class") })
            .asSequence().forEach {
                val className = it.toAbsolutePath().toString()
                    .substringAfter(folderPath + File.separator)
                    .replace(File.separator, ".")
                    .removeSuffix(".class")
                when (className.matchesOneOf(loadClassesOnlyFrom)) {
                    true -> loadedSync.add(className to Files.newInputStream(it))
                    else -> loadedAsync.add(className to { Files.newInputStream(it) })
                }
            }
        return ByteCodeLoader(this, loadedSync, loadedAsync)
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