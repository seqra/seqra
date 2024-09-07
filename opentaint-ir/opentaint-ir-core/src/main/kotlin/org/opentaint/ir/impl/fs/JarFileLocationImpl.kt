package org.opentaint.ir.impl.fs

import org.opentaint.ir.ApiLevel
import org.opentaint.ir.api.ByteCodeLocation
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import kotlin.streams.asSequence

class JarFileLocationImpl(val file: File, override val apiLevel: ApiLevel, private val loadClassesOnlyFrom: List<String>?) :
    ByteCodeLocation {

    private val jarFile = JarFile(file)

    override val currentVersion: String
        get() = file.absolutePath + file.lastModified()

    override val version = currentVersion

    override suspend fun classesByteCode(): Sequence<Pair<String, InputStream?>> {
        return jarFile.stream().filter { it.name.endsWith(".class") }.asSequence().map {
            val className = it.name.removeSuffix(".class").replace("/", ".")
            val stream = when (className.matchesOneOf(loadClassesOnlyFrom)) {
                true -> jarFile.getInputStream(it)
                else -> null
            }
            className to stream
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jarFile.getJarEntry(jarEntryName)
        return jarFile.getInputStream(jarEntry)
    }

    override fun toString() = file.absolutePath
}