package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile
import kotlin.streams.asSequence


class JarFileLocationImpl(private val file: File) : ByteCodeLocation {

    private val jarFile = JarFile(file)

    override val currentVersion: String
        get() = file.absolutePath + file.lastModified()

    override val version = currentVersion

    override suspend fun classesByteCode(): Sequence<InputStream> {
        return jarFile.stream().filter { it.name.endsWith(".class") }.asSequence().map {
            jarFile.getInputStream(it)
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jarFile.getJarEntry(jarEntryName)
        return jarFile.getInputStream(jarEntry)
    }
}

class BuildFolderLocationImpl(private val folder: File) : ByteCodeLocation {

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

    override suspend fun classesByteCode(): Sequence<InputStream> {
        return Files.find(folder.toPath(), Int.MAX_VALUE, { path, _ -> path.endsWith(".class") })
            .asSequence()
            .map { Files.newInputStream(it) }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val filePath = Paths.get(folder.absolutePath, *classFullName.split(".").toTypedArray())
        if (!Files.exists(filePath)) {
            return null
        }
        return Files.newInputStream(filePath)
    }
}

val File.byteCodeLocation: ByteCodeLocation
    get() {
        if (!exists()) {
            throw IllegalArgumentException("file $absolutePath doesn't exist")
        }
        if (isFile && name.endsWith(".jar")) {
            return JarFileLocationImpl(this)
        } else if (!isFile) {
            return BuildFolderLocationImpl(this)
        }
        throw IllegalArgumentException("file $absolutePath is not jar-file nor build dir folder")
    }