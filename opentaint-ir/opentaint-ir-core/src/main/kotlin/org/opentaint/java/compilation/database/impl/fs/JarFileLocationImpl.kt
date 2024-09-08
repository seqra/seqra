package org.opentaint.java.compilation.database.impl.fs

import mu.KLogging
import org.opentaint.java.compilation.database.ApiLevel
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.md5
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarFile

class JarFileLocationImpl(
    val file: File,
    override val apiLevel: ApiLevel,
    private val loadClassesOnlyFrom: List<String>?
) : ByteCodeLocation, Closeable {
    companion object : KLogging()

    private val jarFile = AtomicReference(jarFile())

    override val currentVersion: String
        get() = (file.absolutePath + file.lastModified()).md5()

    override val version = currentVersion
    override fun refreshed() = JarFileLocationImpl(file, apiLevel, loadClassesOnlyFrom)

    override suspend fun loader(): ByteCodeLoader? {
        val jar = jarFile.get() ?: return null
        val loadedSync = arrayListOf<Pair<String, InputStream>>()
        val loadedAsync = arrayListOf<Pair<String, () -> InputStream>>()

        try {
            jar.stream().filter { it.name.endsWith(".class") }.forEach {
                val className = it.name.removeSuffix(".class").replace("/", ".")
                when (className.matchesOneOf(loadClassesOnlyFrom)) {
                    true -> loadedSync.add(className to jar.getInputStream(it))
                    else -> loadedAsync.add(className to { jar.getInputStream(it) })
                }
            }
            return ByteCodeLoaderImpl(this, loadedSync, loadedAsync)
        } catch (e: Exception) {
            logger.warn(e) { "error loading classes from build folder: ${file.absolutePath}. returning empty loader" }
            return null
        }
    }

    override suspend fun resolve(classFullName: String): InputStream? {
        val jar = jarFile.get() ?: return null
        val jarEntryName = classFullName.replace(".", "/") + ".class"
        val jarEntry = jar.getJarEntry(jarEntryName)
        return jar.getInputStream(jarEntry)
    }

    override fun close() {
        val jar = jarFile.get()
        if (jar != null) {
            jar.close()
            jarFile.compareAndSet(jar, jarFile())
        }
    }

    private fun jarFile(): JarFile? {
        try {
            return JarFile(file)
        } catch (e: Exception) {
            logger.warn(e) { "error processing jar located ${file.absolutePath}" }
            return null
        }
    }

    override fun toString(): String = file.absolutePath
}