package org.opentaint.ir.impl.fs

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import kotlin.streams.asSequence

class JarFileLocationImpl(private val file: File, override val apiLevel: ApiLevel) : ByteCodeLocation {

    private val jarFile = JarFile(file)

    override val currentVersion: String
        get() = file.absolutePath + file.lastModified()

    override val version = currentVersion

    override suspend fun classesByteCode(): Sequence<Pair<String, InputStream>> {
        return jarFile.stream().filter { it.name.endsWith(".class") }.asSequence().map {
            it.name.removeSuffix(".class").replace("/", ".") to jarFile.getInputStream(it)
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jarFile.getJarEntry(jarEntryName)
        return jarFile.getInputStream(jarEntry)
    }

    override fun toString() = file.absolutePath
}