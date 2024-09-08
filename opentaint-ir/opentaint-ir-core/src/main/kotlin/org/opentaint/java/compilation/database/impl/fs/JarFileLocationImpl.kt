package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile

class JarFileLocationImpl(
    val file: File,
    override val apiLevel: ApiLevel,
    private val loadClassesOnlyFrom: List<String>?
) : ByteCodeLocation {

    private val jarFile = JarFile(file)

    override val currentVersion: String
        get() = file.absolutePath + file.lastModified()

    override val version = currentVersion

    override suspend fun loader(): ByteCodeLoader {
        val loadedSync = arrayListOf<Pair<String, InputStream>>()
        val loadedAsync = arrayListOf<Pair<String, () -> InputStream>>()

        jarFile.stream().filter { it.name.endsWith(".class") }.forEach {
            val className = it.name.removeSuffix(".class").replace("/", ".")
            when (className.matchesOneOf(loadClassesOnlyFrom)) {
                true -> loadedSync.add(className to jarFile.getInputStream(it))
                else -> loadedAsync.add(className to { jarFile.getInputStream(it) })
            }
        }
        return ByteCodeLoader(this, loadedSync, loadedAsync)
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jarFile.getJarEntry(jarEntryName)
        return jarFile.getInputStream(jarEntry)
    }

    override fun toString() = file.absolutePath
}